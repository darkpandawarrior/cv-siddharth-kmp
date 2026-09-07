package com.siddharth.cv.shared.chat

import com.siddharth.kmp.llmchat.AiChunk
import com.siddharth.kmp.llmchat.HttpChatConfig
import com.siddharth.kmp.llmchat.HttpChatProvider
import com.siddharth.kmp.network.httpClientEngine
import com.siddharth.kmp.result.AiFailure
import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow

/**
 * The one implementation of "talk to /api/chat" in the port — the Kotlin counterpart of
 * `cv-siddharth/src/lib/chatClient.ts`, speaking the framing `api/_lib/chat-handler.ts` actually
 * emits: `data: {"text":"…"}` lines terminated by `data: [DONE]`.
 *
 * NOW ROUTED THROUGH kmp-toolkit's [HttpChatProvider] (llm-chat#52 — `explicitNulls = false` on
 * its request `Json` — is what unblocked this: before it, an unset `mode` serialized as an
 * explicit `"mode": null`, which trips this endpoint's closed allowlist,
 * `undefined | "compose" | "jd"`, exactly the way an unrecognized `mode` string would). This file
 * used to hand-roll the SSE `data:`/`[DONE]` parse loop for that reason; it doesn't any more —
 * [HttpChatProvider] parses the same framing this endpoint speaks and is already proven against it
 * by [com.siddharth.cv.shared.fit.FitCheckScreen]'s `mode = "jd"` call.
 *
 * THREE THINGS THIS SWAP GENUINELY GIVES UP, because [HttpChatProvider]'s public surface has no
 * seam for them (not a bug in this file — see its own KDoc and `HttpChatConfig`):
 *  1. `route` (where the visitor is standing) never reaches the wire any more. `HttpChatConfig`
 *     has exactly `endpoint`/`mode`/`originHeader` — no slot for an app-defined field beyond
 *     `mode`, which this endpoint already spends on the mode allowlist. [streamReply] keeps the
 *     `route` parameter (nothing here to break FloatingChat.kt's call site) but the value is now
 *     inert. // ponytail: ceiling is kmp-toolkit's, not this file's — lift by adding an
 *     `extraFields: Map<String, String>` (or a dedicated `route`) to `HttpChatConfig`.
 *  2. Per-status messages get coarser. [AiChunk.Failed] carries only an [AiFailure] bucket, not
 *     the response body or `Retry-After` header — [HttpChatProvider] reads and discards both. A
 *     403's server-written allowlist sentence and a 429's `Retry-After` seconds no longer reach
 *     [ChatUnavailable]; [statusMessageFor] below gives the same bucket a good generic sentence
 *     instead. `status` and `retryAfterSeconds` stay on [ChatUnavailable] for source compat but are
 *     always null coming out of this path now.
 *  3. A stream that dies mid-reply (some tokens arrived, the connection then dropped with no
 *     `[DONE]`) is no longer distinguishable from one that finished cleanly: [HttpChatProvider]
 *     only fails when the stream produced ZERO tokens ([AiFailure.EmptyReply]); once one token has
 *     emitted, a channel closing early or a `[DONE]` ending the same way both read as success. A
 *     cut-off reply now renders as a (silently truncated) complete one rather than the old "that
 *     reply got cut off mid-sentence" message.
 * None of these three is a wire-shape mismatch left to fix in THIS repo — each needs
 * [HttpChatProvider] itself to grow the seam first.
 */

/** Production endpoint. Hardcoded: this client exists to talk to exactly one deployment. */
const val CHAT_ENDPOINT: String = "https://cv-siddharth.vercel.app/api/chat"

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
 * [status] and [retryAfterSeconds] are always null coming out of [streamReply] now — see this
 * file's top doc, point 2 — kept on the class only so a future caller that reads them doesn't need
 * a source change, not because this path populates them.
 *
 * [reason] is the same [AiFailure] vocabulary `:result`/`:llm-chat` use, so a caller that already
 * handles the on-device or cloud-vendor AI seams' failures can fold this endpoint's into the same
 * `when` instead of inventing a fourth taxonomy.
 */
class ChatUnavailable(
    override val message: String,
    val reason: AiFailure,
    val status: Int? = null,
    val retryAfterSeconds: Int? = null,
) : RuntimeException(message)

/**
 * An [AiFailure] bucket → the sentence the visitor sees. Coarser than the old per-status/per-body
 * messages (see this file's top doc, point 2): [HttpChatProvider] hands back the bucket only, not
 * the server's own text or a `Retry-After` value.
 */
private fun statusMessageFor(reason: AiFailure): String =
    when (reason) {
        AiFailure.Unauthorized ->
            "This chat endpoint only serves Siddharth's portfolio site, and this build isn't on " +
                "its allowlist. $CHAT_CONTACT_FALLBACK"
        AiFailure.RateLimited ->
            "This chat endpoint is getting hit hard right now — give it a moment and ask again. " +
                CHAT_CONTACT_FALLBACK
        AiFailure.EmptyReply -> CHAT_CONTACT_FALLBACK
        else -> transportMessage()
    }

/**
 * Streams one reply as incremental text deltas.
 *
 * @param history the whole transcript INCLUDING the just-typed user turn. Trimming to the server's
 *   ceilings happens here (`toWire`), at the one place every caller streams through, so a future
 *   second caller can't reintroduce the "sent the whole session, got a 400" bug.
 * @param route where the visitor is standing. No longer reaches the wire — see this file's top
 *   doc, point 1. Kept so FloatingChat.kt's call site doesn't need to change for a value that may
 *   matter again once `HttpChatConfig` grows a slot for it.
 * @param engine the [HttpClientEngine] [HttpChatProvider] sends the request on. Defaults to the
 *   real [httpClientEngine]; a test passes a `MockEngine` instead — this is [HttpChatProvider]'s
 *   own seam, not a bespoke one.
 *
 * The flow completes when the server closes the stream having emitted at least one token. It fails
 * with [ChatUnavailable] for a non-2xx response, a transport error, or a stream that produced no
 * tokens at all.
 */
@Suppress("UnusedParameter") // route: see this file's top doc, point 1 — no HttpChatConfig slot yet.
fun streamReply(
    history: List<ChatMessage>,
    route: String? = null,
    engine: HttpClientEngine = httpClientEngine(),
): Flow<String> = channelFlow {
    val provider = HttpChatProvider(HttpChatConfig(endpoint = CHAT_ENDPOINT), engine)

    try {
        provider.completeStream(history.toWire()).collect { chunk ->
            when (chunk) {
                is AiChunk.Token -> send(chunk.text)
                is AiChunk.Failed -> throw ChatUnavailable(statusMessageFor(chunk.reason), chunk.reason)
            }
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
 * A throw before any status arrived, or a bucket [HttpChatProvider] mapped to [AiFailure.Network].
 * Two real causes for the transport case, and the client genuinely cannot tell them apart, so the
 * message names the likelier one instead of inventing certainty:
 *  1. the browser blocked the response because the endpoint's allowlist rejected our origin (a
 *     denied 403 carries no CORS headers, so `fetch` rejects and the status is invisible to us),
 *  2. an actual network failure.
 *
 * Naming (1) first is the honest ordering: this port is served from origins the live endpoint has
 * never heard of, so it is the expected outcome, not the exotic one.
 */
private fun transportMessage(): String =
    "Couldn't reach the chat backend. It only answers its own site — a build served from " +
        "anywhere else is blocked by its origin allowlist, by design. $CHAT_CONTACT_FALLBACK"
