package com.siddharth.cv.shared.chat

import com.siddharth.kmp.result.AiFailure
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
 * Exercises [streamReply] now that it is a thin wrapper over kmp-toolkit's `HttpChatProvider`
 * (llm-chat#52 — `explicitNulls = false` — is what made this swap safe; see `ChatClient.kt`'s file
 * doc for the three things the swap gives up in exchange, and why each is a toolkit-side gap, not
 * a bug here).
 *
 * Runs on the jvm target only (see cmp-shared/build.gradle.kts's jvmTest block for why): the logic
 * under test has no platform branch, so one target is enough to catch a regression in it.
 */
class ChatClientTest {

    // Dispatchers.Unconfined — same rationale as kmp-toolkit's HttpChatProviderTest: keeps the mock
    // response on the calling thread so it resolves synchronously under runTest's virtual clock.
    private fun mockEngine(
        vararg sseLines: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ): MockEngine =
        MockEngine(
            MockEngineConfig().apply {
                dispatcher = Dispatchers.Unconfined
                addHandler {
                    respond(
                        content = sseLines.joinToString("\n"),
                        status = status,
                        headers = headersOf(HttpHeaders.ContentType, "text/event-stream"),
                    )
                }
            },
        )

    private fun capturingMockEngine(vararg sseLines: String): Pair<MockEngine, () -> HttpRequestData?> {
        var captured: HttpRequestData? = null
        val engine = MockEngine(
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
        )
        return engine to { captured }
    }

    private fun oneTurn(text: String = "hi") = listOf(ChatMessage(ChatRole.User, text))

    @Test
    fun streamReply_emitsTokensInOrder_andStopsOnDone() = runTest {
        val deltas = streamReply(
            oneTurn(),
            engine = mockEngine(
                """data: {"text":"Hel"}""",
                """data: {"text":"lo"}""",
                "data: [DONE]",
            ),
        ).toList()

        assertEquals(listOf("Hel", "lo"), deltas)
    }

    @Test
    fun streamReply_skipsBlankAndNonDataLines() = runTest {
        val deltas = streamReply(
            oneTurn(),
            engine = mockEngine(
                ": keepalive",
                "",
                """data: {"text":"Hi"}""",
                "data: [DONE]",
            ),
        ).toList()

        assertEquals(listOf("Hi"), deltas)
    }

    @Test
    fun streamReply_emptyStream_reportsUnavailable() = runTest {
        // HttpChatProvider's only "zero tokens" signal — a stream that produced no [DONE] but also
        // no text (unlike a mid-reply cutoff, which it can no longer distinguish from success, see
        // ChatClient.kt's file doc point 3).
        val error = assertFailsWithChatUnavailable {
            streamReply(oneTurn(), engine = mockEngine("")).toList()
        }

        assertEquals(AiFailure.EmptyReply, error.reason)
        assertEquals(CHAT_CONTACT_FALLBACK, error.message)
    }

    @Test
    fun streamReply_403_mapsToUnauthorized_withAllowlistMessage() = runTest {
        val error = assertFailsWithChatUnavailable {
            streamReply(
                oneTurn(),
                engine = mockEngine("""{"error":"origin not allowed"}""", status = HttpStatusCode.Forbidden),
            ).toList()
        }

        assertEquals(AiFailure.Unauthorized, error.reason)
        assertTrue("allowlist" in error.message, error.message)
        // status/retryAfterSeconds are no longer populated through this path — see the class doc.
        assertEquals(null, error.status)
    }

    @Test
    fun streamReply_429_mapsToRateLimited() = runTest {
        val error = assertFailsWithChatUnavailable {
            streamReply(
                oneTurn(),
                engine = mockEngine("""{"error":"slow down"}""", status = HttpStatusCode.TooManyRequests),
            ).toList()
        }

        assertEquals(AiFailure.RateLimited, error.reason)
    }

    @Test
    fun streamReply_400_mapsToNetworkBucket() = runTest {
        // Same bucket as before (AiFailure.Network) — only the message is coarser now, since
        // HttpChatProvider doesn't surface the server's own error text. See ChatClient.kt point 2.
        val error = assertFailsWithChatUnavailable {
            streamReply(
                oneTurn(),
                engine = mockEngine(
                    """{"error":"Expected { messages: [...] }"}""",
                    status = HttpStatusCode.BadRequest,
                ),
            ).toList()
        }

        assertEquals(AiFailure.Network, error.reason)
    }

    @Test
    fun streamReply_cancellation_propagates_ratherThanBecomingChatUnavailable() = runTest {
        val engine = MockEngine { throw CancellationException("visitor closed the panel") }

        var sawCancellation = false
        try {
            streamReply(oneTurn(), engine = engine).toList()
        } catch (_: CancellationException) {
            sawCancellation = true
        } catch (e: ChatUnavailable) {
            fail("a cancellation must propagate, not get wrapped as ChatUnavailable: ${e.message}")
        }
        assertTrue(sawCancellation)
    }

    @Test
    fun streamReply_ordinaryChat_omitsModeEntirely() = runTest {
        // The regression guard for the wire-incompatibility this file's top doc explains: the
        // production endpoint 400s an explicit `"mode":null`, which is exactly what routing this
        // client through kmp-toolkit's HttpChatProvider used to send on every request before
        // llm-chat#52 (`explicitNulls = false`).
        val (engine, lastRequest) = capturingMockEngine("""data: {"text":"hi"}""", "data: [DONE]")

        streamReply(oneTurn(), route = "/resume", engine = engine).toList()

        val body = (lastRequest()!!.body as TextContent).text
        assertFalse(""""mode"""" in body, body)
    }

    @Test
    fun streamReply_ordinaryChat_neverSendsRoute() = runTest {
        // `route` no longer reaches the wire at all — HttpChatConfig has no slot for it (see
        // ChatClient.kt's file doc point 1). This is the documented regression, not a silent one.
        val (engine, lastRequest) = capturingMockEngine("""data: {"text":"hi"}""", "data: [DONE]")

        streamReply(oneTurn(), route = "/resume", engine = engine).toList()

        val body = (lastRequest()!!.body as TextContent).text
        assertFalse(""""route"""" in body, body)
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
