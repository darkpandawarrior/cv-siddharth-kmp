package com.siddharth.cv.shared.chat

import com.siddharth.kmp.result.AiFailure
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The first tests this client has ever had (no `*Test.kt`/`*.test.ts` counterpart existed for it
 * before — the TS twin has `chatClient.test.ts`, this one had nothing). Exercises the pieces that
 * changed when [chatClient] moved onto `:network`'s [com.siddharth.kmp.network.httpClientEngine]
 * and adopted `:result`'s [AiFailure]: the SSE parse loop, the status→message→reason mapping, the
 * request's wire shape (see the "route, not mode" test — this is the regression guard for the
 * incompatibility [streamReply]'s own file doc explains), and that a cancellation is never
 * reported as a [ChatUnavailable].
 *
 * Runs on the jvm target only (see cmp-shared/build.gradle.kts's jvmTest block for why): the logic
 * under test has no platform branch, so one target is enough to catch a regression in it.
 */
class ChatClientTest {

    // Dispatchers.Unconfined — same rationale as kmp-toolkit's HttpChatProviderTest: keeps the mock
    // response on the calling thread so it resolves synchronously under runTest's virtual clock.
    private fun mockClient(
        vararg sseLines: String,
        status: HttpStatusCode = HttpStatusCode.OK,
        extraHeaders: List<Pair<String, String>> = emptyList(),
    ): HttpClient =
        HttpClient(
            MockEngine(
                MockEngineConfig().apply {
                    dispatcher = Dispatchers.Unconfined
                    addHandler {
                        respond(
                            content = sseLines.joinToString("\n"),
                            status = status,
                            headers = headersOf(
                                HttpHeaders.ContentType to listOf("text/event-stream"),
                                *extraHeaders.map { (k, v) -> k to listOf(v) }.toTypedArray(),
                            ),
                        )
                    }
                },
            ),
        )

    private fun capturingMockClient(vararg sseLines: String): Pair<HttpClient, () -> HttpRequestData?> {
        var captured: HttpRequestData? = null
        val client = HttpClient(
            MockEngine(
                MockEngineConfig().apply {
                    dispatcher = Dispatchers.Unconfined
                    addHandler { request ->
                        captured = request
                        respond(
                            content = sseLines.joinToString("\n"),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "text/event-stream"),
                        )
                    }
                },
            ),
        )
        return client to { captured }
    }

    private fun oneTurn(text: String = "hi") = listOf(ChatMessage(ChatRole.User, text))

    @Test
    fun streamReply_emitsTokensInOrder_andStopsOnDone() = runTest {
        val client = mockClient(
            """data: {"text":"Hel"}""",
            """data: {"text":"lo"}""",
            "data: [DONE]",
        )

        val deltas = streamReply(oneTurn(), client = client).toList()

        assertEquals(listOf("Hel", "lo"), deltas)
    }

    @Test
    fun streamReply_skipsBlankAndNonDataLines() = runTest {
        val client = mockClient(
            ": keepalive",
            "",
            """data: {"text":"Hi"}""",
            "data: [DONE]",
        )

        assertEquals(listOf("Hi"), streamReply(oneTurn(), client = client).toList())
    }

    @Test
    fun streamReply_noTerminator_withText_reportsCutOff_asNetworkFailure() = runTest {
        val client = mockClient("""data: {"text":"Hel"}""") // no [DONE]

        val error = assertFailsWithChatUnavailable { streamReply(oneTurn(), client = client).toList() }

        assertEquals(AiFailure.Network, error.reason)
        assertTrue("cut off" in error.message, error.message)
    }

    @Test
    fun streamReply_noTerminator_withoutText_fallsBackToContactMessage() = runTest {
        val client = mockClient("") // no [DONE], no token ever emitted

        val error = assertFailsWithChatUnavailable { streamReply(oneTurn(), client = client).toList() }

        assertEquals(AiFailure.Network, error.reason)
        assertEquals(CHAT_CONTACT_FALLBACK, error.message)
    }

    @Test
    fun streamReply_403_prefersServerText_andMapsToUnauthorized() = runTest {
        val client = mockClient("""{"error":"origin not allowed"}""", status = HttpStatusCode.Forbidden)

        val error = assertFailsWithChatUnavailable { streamReply(oneTurn(), client = client).toList() }

        assertEquals(AiFailure.Unauthorized, error.reason)
        assertEquals("origin not allowed", error.message)
        assertEquals(403, error.status)
    }

    @Test
    fun streamReply_429_mapsToRateLimited_andKeepsRetryAfter() = runTest {
        val client = mockClient(
            """{"error":"slow down"}""",
            status = HttpStatusCode.TooManyRequests,
            extraHeaders = listOf(HttpHeaders.RetryAfter to "30"),
        )

        val error = assertFailsWithChatUnavailable { streamReply(oneTurn(), client = client).toList() }

        assertEquals(AiFailure.RateLimited, error.reason)
        assertEquals(30, error.retryAfterSeconds)
    }

    @Test
    fun streamReply_400_getsTheHonestTooLongTranslation_notTheServerSchemaText() = runTest {
        val client = mockClient(
            """{"error":"Expected { messages: [...] }"}""",
            status = HttpStatusCode.BadRequest,
        )

        val error = assertFailsWithChatUnavailable { streamReply(oneTurn(), client = client).toList() }

        // Same bucket as every other non-2xx that isn't 401/403/429 — see AiFailure's own doc for
        // why there is no dedicated "too long" member; the human-facing message still tells them apart.
        assertEquals(AiFailure.Network, error.reason)
        assertTrue("too long" in error.message, error.message)
        assertFalse("Expected" in error.message, error.message)
    }

    @Test
    fun streamReply_cancellation_propagates_ratherThanBecomingChatUnavailable() = runTest {
        val client = HttpClient(MockEngine { throw CancellationException("visitor closed the panel") })

        var sawCancellation = false
        try {
            streamReply(oneTurn(), client = client).toList()
        } catch (_: CancellationException) {
            sawCancellation = true
        } catch (e: ChatUnavailable) {
            fail("a cancellation must propagate, not get wrapped as ChatUnavailable: ${e.message}")
        }
        assertTrue(sawCancellation)
    }

    @Test
    fun streamReply_sendsRoute_notMode_andOmitsModeEntirely() = runTest {
        // The regression guard for the wire-incompatibility this file's top doc explains: the
        // production endpoint 400s an explicit `"mode":null`, which is exactly what routing this
        // client through kmp-toolkit's HttpChatProvider unmodified would send on every request.
        val (client, lastRequest) = capturingMockClient("""data: {"text":"hi"}""", "data: [DONE]")

        streamReply(oneTurn(), route = "/resume", client = client).toList()

        val body = (lastRequest()!!.body as TextContent).text
        assertTrue(""""route":"/resume"""" in body, body)
        assertFalse(""""mode"""" in body, body)
    }

    private suspend fun assertFailsWithChatUnavailable(block: suspend () -> Unit): ChatUnavailable {
        try {
            block()
        } catch (e: ChatUnavailable) {
            return e
        }
        fail("expected a ChatUnavailable to be thrown")
    }
}
