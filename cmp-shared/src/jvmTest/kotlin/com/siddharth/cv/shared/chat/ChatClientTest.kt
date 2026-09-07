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
 * (llm-chat#52 — `explicitNulls = false` — is what made this swap safe, and llm-chat#54 — `route`,
 * `AiChunk.Failed.detail`/`retryAfterSeconds`, `requireDoneSentinel` — is what restored the
 * fidelity the swap first gave up; see `ChatClient.kt`'s file doc).
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
        extraHeaders: List<Pair<String, String>> = emptyList(),
    ): MockEngine =
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
        // The zero-tokens signal — a stream that produced no [DONE] and no text either.
        val error = assertFailsWithChatUnavailable {
            streamReply(oneTurn(), engine = mockEngine("")).toList()
        }

        assertEquals(AiFailure.EmptyReply, error.reason)
        assertEquals(CHAT_CONTACT_FALLBACK, error.message)
    }

    @Test
    fun streamReply_noTerminator_withText_reportsCutOff_asNetworkFailure() = runTest {
        // llm-chat#54's requireDoneSentinel: at least one token arrived, but the stream closed
        // without ever sending [DONE] — the mid-reply cutoff the pre-toolkit client used to catch
        // and this port's HttpChatProvider swap first lost the ability to see.
        val error = assertFailsWithChatUnavailable {
            streamReply(oneTurn(), engine = mockEngine("""data: {"text":"Hel"}""")).toList() // no [DONE]
        }

        assertEquals(AiFailure.Network, error.reason)
        assertTrue("cut off" in error.message, error.message)
    }

    @Test
    fun streamReply_403_prefersServerText_andMapsToUnauthorized() = runTest {
        val error = assertFailsWithChatUnavailable {
            streamReply(
                oneTurn(),
                engine = mockEngine("""{"error":"origin not allowed"}""", status = HttpStatusCode.Forbidden),
            ).toList()
        }

        assertEquals(AiFailure.Unauthorized, error.reason)
        assertEquals("origin not allowed", error.message)
        // HttpChatProvider classifies into an AiFailure bucket, not a raw status code — status
        // stays null through this path; that part of the old client's fidelity isn't restorable
        // without HttpChatProvider itself exposing the status (see ChatClient.kt's ponytail note).
        assertEquals(null, error.status)
    }

    @Test
    fun streamReply_429_mapsToRateLimited_andKeepsRetryAfter() = runTest {
        val error = assertFailsWithChatUnavailable {
            streamReply(
                oneTurn(),
                engine = mockEngine(
                    """{"error":"slow down"}""",
                    status = HttpStatusCode.TooManyRequests,
                    extraHeaders = listOf(HttpHeaders.RetryAfter to "30"),
                ),
            ).toList()
        }

        assertEquals(AiFailure.RateLimited, error.reason)
        assertEquals("slow down", error.message)
        assertEquals(30, error.retryAfterSeconds)
    }

    @Test
    fun streamReply_400_mapsToNetworkBucket() = runTest {
        // Same bucket as before (AiFailure.Network). The server's own text now flows into the
        // message again (llm-chat#54's AiChunk.Failed.detail) — the one thing NOT restored is the
        // old curated "too long" translation for 400/413 specifically, which needed the raw status
        // code HttpChatProvider still doesn't expose (see ChatClient.kt's ponytail note).
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
        assertEquals("Expected { messages: [...] }", error.message)
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
    fun streamReply_sendsRoute_notMode_andOmitsModeEntirely() = runTest {
        // The regression guard for the wire-incompatibility this file's top doc explains: the
        // production endpoint 400s an explicit `"mode":null`, which is exactly what routing this
        // client through kmp-toolkit's HttpChatProvider used to send on every request before
        // llm-chat#52 (`explicitNulls = false`). `route` reaching the wire again is llm-chat#54's
        // HttpChatConfig.route — the regression this test used to only guard the loss of.
        val (engine, lastRequest) = capturingMockEngine("""data: {"text":"hi"}""", "data: [DONE]")

        streamReply(oneTurn(), route = "/resume", engine = engine).toList()

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
