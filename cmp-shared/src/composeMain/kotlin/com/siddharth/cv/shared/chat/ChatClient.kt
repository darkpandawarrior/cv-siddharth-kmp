package com.siddharth.cv.shared.chat

import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
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
 */
class ChatUnavailable(
    override val message: String,
    val status: Int? = null,
    val retryAfterSeconds: Int? = null,
) : RuntimeException(message)

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
 * `by lazy` rather than a top-level `val` because construction can legitimately fail: `HttpClient()`
 * resolves its engine from the classpath, and only `wasmJs` has one wired (`ktor-client-js`). On
 * jvm/android/ios this throws, and it must throw where [streamReply] can catch it and say so,
 * not at class-init time where it would take the whole screen down.
 */
private val chatClient: HttpClient by lazy { HttpClient() }

/**
 * Streams one reply as incremental text deltas.
 *
 * @param history the whole transcript INCLUDING the just-typed user turn. Trimming to the server's
 *   ceilings happens here (`toWire`), at the one place every caller streams through, so a future
 *   second caller can't reintroduce the "sent the whole session, got a 400" bug.
 * @param route where the visitor is standing — a hint the server re-validates against its own
 *   allowlist and drops if unknown. Never a turn, so it can't read as something the visitor said.
 *
 * The flow completes when the server sends `[DONE]`. It fails with [ChatUnavailable] for every
 * other ending, including a stream that stops mid-reply.
 *
 * WHY `channelFlow`: the deltas are produced inside Ktor's `execute { }` block. A plain `flow { }`
 * requires every `emit` to happen in the collector's own context, and nothing in this file's
 * contract guarantees Ktor won't switch it. `send` carries no such restriction, so the correct
 * version costs one extra line.
 */
fun streamReply(history: List<ChatMessage>, route: String? = null): Flow<String> = channelFlow {
    val payload = chatJson.encodeToString(
        ChatRequestBody(messages = history.toWire(), route = route),
    )

    try {
        chatClient.preparePost(CHAT_ENDPOINT) {
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
    } catch (transport: Throwable) {
        throw ChatUnavailable(transportMessage(transport))
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
        if (sawText) {
            "That reply got cut off mid-sentence — the connection dropped. Ask again and I'll finish it."
        } else {
            CHAT_CONTACT_FALLBACK
        },
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
    return ChatUnavailable(message, status = code, retryAfterSeconds = retryAfter)
}

/**
 * A throw before any status arrived. Three real causes, and the client genuinely cannot tell them
 * apart, so the message names the likeliest one instead of inventing certainty:
 *  1. the browser blocked the response because the endpoint's allowlist rejected our origin (a
 *     denied 403 carries no CORS headers, so `fetch` rejects and the status is invisible to us),
 *  2. no HTTP engine on the classpath — every target but `wasmJs` in this module,
 *  3. an actual network failure.
 *
 * Naming (1) first is the honest ordering: this port is served from origins the live endpoint has
 * never heard of, so it is the expected outcome, not the exotic one.
 */
private fun transportMessage(cause: Throwable): String {
    val detail = cause.message.orEmpty()
    if ("engine" in detail.lowercase() || "HttpClientEngineContainer" in detail) {
        return "Chat needs an HTTP engine, and only the web build has one wired. $CHAT_CONTACT_FALLBACK"
    }
    return "Couldn't reach the chat backend. It only answers its own site — a build served from " +
        "anywhere else is blocked by its origin allowlist, by design. $CHAT_CONTACT_FALLBACK"
}
