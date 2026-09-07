package com.siddharth.cv.shared.chat

import com.siddharth.kmp.network.httpClientEngine
import com.siddharth.kmp.result.AiFailure
import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readLine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.serialization.json.Json

/**
 * The one implementation of "talk to /api/chat" in the port — the Kotlin counterpart of
 * `cv-siddharth/src/lib/chatClient.ts`, speaking the framing `api/_lib/chat-handler.ts` actually
 * emits: `data: {"text":"…"}` lines terminated by `data: [DONE]`.
 *
 * WHY A HAND-ROLLED PARSER RATHER THAN KTOR'S SSE PLUGIN: the plugin turns a non-2xx into an
 * `SSEClientException` whose status and body are awkward to get back out, and this endpoint's
 * failures are the interesting part — a 403 from the origin allowlist and a 429 with a
 * `Retry-After` are both things the visitor needs told, precisely, not swallowed into "connection
 * failed". Reading the channel directly costs about fifteen lines and keeps `HttpResponse` in hand.
 *
 * WHY [io.ktor.client.statement.HttpStatement.execute] AND NOT `client.post(...)`: Ktor buffers the
 * whole body of an ordinary call before returning, which would make every token arrive at once and
 * defeat the entire point. `execute { }` hands over the live channel.
 *
 * WHY NOT kmp-toolkit's `HttpChatProvider` (llm-chat), given it exists to speak this exact framing:
 * its wire body always serializes a `mode` key (an app-defined string; null when unset, since
 * `HttpChatRequest.mode` has no Kotlin-level default for `encodeDefaults` to skip and its Json
 * config leaves `explicitNulls` at its true default). This endpoint's `validateRequest` 400s any
 * `mode` that isn't exactly undefined/`"compose"`/`"jd"` — an explicit `null` included — so routing
 * this client's normal-chat traffic through `HttpChatProvider` unmodified 400s every request. There
 * is also no slot in its request shape for this endpoint's separate `route` field (the ambient
 * page-location hint) — only one app-defined string, which this endpoint already spends on `mode`.
 * What DOES get reused: [httpClientEngine] (below — every target this module ships on now gets a
 * real engine, not just wasmJs) and `:result`'s [AiFailure] (the classification [ChatUnavailable]
 * carries), the two pieces that don't assume a wire shape this endpoint doesn't have.
 */

/** Production endpoint. Hardcoded: this client exists to talk to exactly one deployment. */
const val CHAT_ENDPOINT: String = "https://cv-siddharth.vercel.app/api/chat"

private const val DATA_PREFIX = "data: "
private const val DONE_PAYLOAD = "[DONE]"

/**
 * What a visitor should read when a reply fails, so they still leave with a way to reach a human.
 * Mirrors `CHAT_FALLBACK` in chatClient.ts — same words, same reason.
 */
const val CHAT_CONTACT_FALLBACK: String =
    "The chat backend isn't reachable from this build. You can reach Siddharth directly at " +
        "siddharthpandalai990@gmail.com."

/**
 * Everything the panel needs to render a failure honestly.
 *
 * [status] is null for a transport-level failure — which, in a browser, is also what a
 * CORS-rejected 403 looks like (a denied response carries no `access-control-allow-origin`, so
 * `fetch` rejects and the status never reaches us). That ambiguity is why [message] for the null
 * case names the allowlist as the likely cause rather than asserting a network outage.
 *
 * [reason] is the same [AiFailure] vocabulary `:result`/`:llm-chat` use, so a caller that already
 * handles the on-device or cloud-vendor AI seams' failures can fold this endpoint's into the same
 * `when` instead of inventing a fourth taxonomy. It is coarser than [message] on purpose — a 400
 * and a 413 both read as [AiFailure.Network] even though [message] tells them apart for the visitor
 * — see the enum's own doc for why it has no "too long" member.
 */
class ChatUnavailable(
    override val message: String,
    val reason: AiFailure,
    val status: Int? = null,
    val retryAfterSeconds: Int? = null,
) : RuntimeException(message)

/**
 * Maps a non-2xx status to the [AiFailure] bucket it belongs in. Mirrors
 * `HttpStatusCode.toAiFailureOrNull` in kmp-toolkit's `:llm-chat` — same buckets, same ordering,
 * and matched by the same named [HttpStatusCode] constants rather than raw status numbers — so
 * this endpoint's failures read the same way a cloud-vendor AI provider's would.
 */
private fun HttpStatusCode.toAiFailure(): AiFailure = when (this) {
    HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden -> AiFailure.Unauthorized
    HttpStatusCode.TooManyRequests -> AiFailure.RateLimited
    else -> AiFailure.Network
}

/**
 * `encodeDefaults = false` is load-bearing, not tidiness: `mode` is a CLOSED allowlist server-side
 * (`undefined | "compose" | "jd"`), so serializing it as an explicit `null` would 400 every
 * request. `ignoreUnknownKeys` covers the endpoint growing a field.
 */
private val chatJson = Json {
    encodeDefaults = false
    explicitNulls = false
    ignoreUnknownKeys = true
    isLenient = true
}

/**
 * Created once and kept — an [HttpClient] owns a connection pool and a coroutine scope, and one per
 * question would leak both.
 *
 * `by lazy` rather than a top-level `val` for the same reason as before this reused
 * `:network`'s [httpClientEngine] rather than the classpath-scanning bare `HttpClient()`: an engine
 * is now real and present on every target composeMain ships on (android/jvm/iosArm64/
 * iosSimulatorArm64/wasmJs — exactly `:network`'s own target set), so construction can no longer
 * fail for a missing engine. It stays lazy anyway — no reason to spend the connection pool before
 * the first question.
 */
private val chatClient: HttpClient by lazy { HttpClient(httpClientEngine()) }

/**
 * Streams one reply as incremental text deltas.
 *
 * @param history the whole transcript INCLUDING the just-typed user turn. Trimming to the server's
 *   ceilings happens here (`toWire`), at the one place every caller streams through, so a future
 *   second caller can't reintroduce the "sent the whole session, got a 400" bug.
 * @param route where the visitor is standing — a hint the server re-validates against its own
 *   allowlist and drops if unknown. Never a turn, so it can't read as something the visitor said.
 * @param client the [HttpClient] to send the request on. Defaults to the shared, lazily-built
 *   [chatClient]; a test passes its own client built on a [io.ktor.client.engine.mock.MockEngine]
 *   instead, since [chatClient] has no other seam for one.
 *
 * The flow completes when the server sends `[DONE]`. It fails with [ChatUnavailable] for every
 * other ending, including a stream that stops mid-reply.
 *
 * WHY `channelFlow`: the deltas are produced inside Ktor's `execute { }` block. A plain `flow { }`
 * requires every `emit` to happen in the collector's own context, and nothing in this file's
 * contract guarantees Ktor won't switch it. `send` carries no such restriction, so the correct
 * version costs one extra line.
 */
fun streamReply(
    history: List<ChatMessage>,
    route: String? = null,
    client: HttpClient = chatClient,
): Flow<String> = channelFlow {
    val payload = chatJson.encodeToString(
        ChatRequestBody(messages = history.toWire(), route = route),
    )

    try {
        client.preparePost(CHAT_ENDPOINT) {
            contentType(ContentType.Application.Json)
            accept(ContentType.Text.EventStream)
            // Deliberately NOT setting an Origin header. The endpoint spends the owner's API key
            // and allowlists its own origins; a native build forging one would be defeating that
            // guard on purpose. In a browser the header is forbidden to scripts anyway — the
            // engine supplies the real one, which is what makes the wasm build work when it is
            // served from the site (or localhost) and 403 when it isn't. That 403 is correct
            // behaviour, and the panel says so.
            setBody(payload)
        }.execute { response ->
            if (!response.status.isSuccess()) throw response.toChatFailure()
            readSseInto(response) { delta -> send(delta) }
        }
    } catch (cancel: CancellationException) {
        // The visitor closed the panel or asked something else. Not a failure — let it propagate
        // untouched so the collector is cancelled rather than shown an error.
        throw cancel
    } catch (chat: ChatUnavailable) {
        throw chat
    } catch (_: Throwable) {
        throw ChatUnavailable(transportMessage(), reason = AiFailure.Network)
    }
    // No `awaitClose` here on purpose: this producer is not callback-based. The block returning IS
    // what closes the channel, so awaiting that close from inside the block would deadlock.
}

/**
 * The line loop. Split out so the framing is readable on its own and testable without a socket.
 *
 * Reads LINE at a time rather than buffering the body: that is the difference between tokens
 * appearing as they are generated and a paragraph landing all at once three seconds later.
 * Blank lines (the SSE event separator) and any non-`data:` line are skipped, matching what the
 * React client does and what a keepalive comment would look like.
 */
private suspend fun readSseInto(response: HttpResponse, emit: suspend (String) -> Unit) {
    val channel = response.bodyAsChannel()
    var sawText = false
    var sawDone = false

    while (true) {
        // `readLine`, not the deprecated `readUTF8Line`, and not `readLineStrict` — strict throws
        // at EOF, which is the normal end of an SSE body, not an error.
        val line = channel.readLine() ?: break
        if (!line.startsWith(DATA_PREFIX)) continue
        val body = line.substring(DATA_PREFIX.length).trim()
        if (body == DONE_PAYLOAD) {
            sawDone = true
            break
        }
        // A partial or non-JSON event is skipped, not fatal — the same tolerance normalizeStream
        // has on the other side of the wire.
        val text = runCatching { chatJson.decodeFromString<ChatDelta>(body).text }.getOrNull()
        if (!text.isNullOrEmpty()) {
            sawText = true
            emit(text)
        }
    }

    if (sawDone) return
    // No terminator. The endpoint GUARANTEES one (normalizeStream.flush always writes it, and
    // emits an EMPTY_STREAM_FALLBACK first if the model produced nothing), so reaching here means
    // the connection died mid-flight — an upstream drop, a closed laptop lid, an Edge timeout.
    throw ChatUnavailable(
        message = if (sawText) {
            "That reply got cut off mid-sentence — the connection dropped. Ask again and I'll finish it."
        } else {
            CHAT_CONTACT_FALLBACK
        },
        reason = AiFailure.Network,
    )
}

/**
 * A non-2xx → the sentence the visitor sees.
 *
 * WHOSE WORDS WIN, and why it matters: 403 / 429 / 502 / 503 all carry `{"error": "…"}` text the
 * endpoint wrote FOR a visitor, and it is better than anything guessable from a status code — the
 * 429 in particular is the one failure where waiting genuinely fixes it, so its "give it a moment"
 * has to survive. 400 and 413 are the exception: their server text is a schema description aimed at
 * a developer ("Expected { messages: [...] }"), and showing that to a recruiter is worse than
 * useless. They get the honest translation instead — "that was too long" — which is actionable.
 */
private suspend fun HttpResponse.toChatFailure(): ChatUnavailable {
    val code = status.value
    // Read first, decode second: `runCatching` around a suspend call would also swallow a
    // CancellationException, which must never be treated as "the server said something odd".
    val raw = bodyAsText()
    val serverText = runCatching {
        chatJson.decodeFromString<ChatErrorBody>(raw).error
    }.getOrNull()?.takeIf { it.isNotBlank() }

    val retryAfter = headers[HttpHeaders.RetryAfter]?.trim()?.toIntOrNull()

    val message = when (code) {
        400, 413 ->
            "That was too long for me to take in one go — try a shorter message, or clear the " +
                "conversation and start fresh."
        403 ->
            serverText
                ?: "This chat endpoint only serves Siddharth's portfolio site, and this build " +
                "isn't on its allowlist. $CHAT_CONTACT_FALLBACK"
        else -> serverText ?: CHAT_CONTACT_FALLBACK
    }
    return ChatUnavailable(message, reason = status.toAiFailure(), status = code, retryAfterSeconds = retryAfter)
}

/**
 * A throw before any status arrived. Two real causes, and the client genuinely cannot tell them
 * apart, so the message names the likelier one instead of inventing certainty:
 *  1. the browser blocked the response because the endpoint's allowlist rejected our origin (a
 *     denied 403 carries no CORS headers, so `fetch` rejects and the status is invisible to us),
 *  2. an actual network failure.
 *
 * Naming (1) first is the honest ordering: this port is served from origins the live endpoint has
 * never heard of, so it is the expected outcome, not the exotic one. A missing HTTP engine used to
 * be a third cause here (every target but `wasmJs`) — [httpClientEngine] retired it, every target
 * composeMain ships on now gets a real one.
 */
private fun transportMessage(): String =
    "Couldn't reach the chat backend. It only answers its own site — a build served from " +
        "anywhere else is blocked by its origin allowlist, by design. $CHAT_CONTACT_FALLBACK"
