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
 * ROUTED THROUGH kmp-toolkit's [HttpChatProvider] (llm-chat#52 — `explicitNulls = false` on its
 * request `Json` — is what unblocked this: before it, an unset `mode` serialized as an explicit
 * `"mode": null`, which trips this endpoint's closed allowlist, `undefined | "compose" | "jd"`,
 * exactly the way an unrecognized `mode` string would). This file used to hand-roll the SSE
 * `data:`/`[DONE]` parse loop for that reason; it doesn't any more — [HttpChatProvider] parses the
 * same framing this endpoint speaks and is already proven against it by
 * [com.siddharth.cv.shared.fit.FitCheckScreen]'s `mode = "jd"` call.
 *
 * llm-chat#54 closed the three gaps that swap first opened: [HttpChatConfig.route] gives `route`
 * a wire slot again, [AiChunk.Failed.detail]/[AiChunk.Failed.retryAfterSeconds] carry the server's
 * own error text and `Retry-After` back out, and [HttpChatConfig.requireDoneSentinel] turns a
 * stream that closes after emitting tokens but never a `data: [DONE]` line into a reported
 * failure instead of a silently truncated success. Fidelity with the old hand-rolled client is
 * restored; see [statusMessageFor] for the failure→message mapping and [streamReply] for the
 * request wiring.
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
 * [status] stays null coming out of [streamReply] — [HttpChatProvider] classifies into an
 * [AiFailure] bucket rather than handing back the raw status code, so there is still no seam for
 * it here. [retryAfterSeconds] IS populated again (from [AiChunk.Failed.retryAfterSeconds]) — see
 * [statusMessageFor].
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

/** The prefix [HttpChatConfig.requireDoneSentinel]'s cutoff [AiChunk.Failed.detail] always starts with. */
private const val CUTOFF_DETAIL_PREFIX = "Stream closed before the completion signal"

/**
 * An [AiFailure] bucket, plus the server's own [detail] text when [HttpChatProvider] had one, →
 * the sentence the visitor sees. [detail] wins whenever the server sent one, same priority the old
 * hand-rolled parser gave it, with one exception: a [CUTOFF_DETAIL_PREFIX] detail is
 * [HttpChatProvider]'s own technical wording for a stream that died mid-reply (see
 * [HttpChatConfig.requireDoneSentinel]) and gets translated to the same "cut off mid-sentence"
 * sentence the old client showed, rather than surfaced verbatim.
 *
 * [AiChunk.Failed.retryAfterSeconds] isn't folded in here — it's carried on [ChatUnavailable]
 * itself instead, for a caller that wants to render a countdown rather than a sentence.
 *
 * // ponytail: the old client also gave 400/413 a curated "too long" translation instead of the
 * // server's raw schema text, keyed off the actual status code. HttpChatProvider only ever hands
 * // back the coarse Network bucket now, with no status code to key that distinction on, so a
 * // 400's detail renders as-is here — still better than the old code's generic fallback, but not
 * // that specific translation. Upgrade needs HttpChatProvider to expose the status too.
 */
private fun statusMessageFor(reason: AiFailure, detail: String?): String =
    when (reason) {
        AiFailure.Unauthorized ->
            detail ?: (
                "This chat endpoint only serves Siddharth's portfolio site, and this build isn't " +
                    "on its allowlist. $CHAT_CONTACT_FALLBACK"
            )
        AiFailure.RateLimited ->
            detail ?: (
                "This chat endpoint is getting hit hard right now — give it a moment and ask " +
                    "again. $CHAT_CONTACT_FALLBACK"
            )
        AiFailure.EmptyReply -> CHAT_CONTACT_FALLBACK
        AiFailure.Network ->
            if (detail?.startsWith(CUTOFF_DETAIL_PREFIX) == true) {
                "That reply got cut off mid-sentence — the connection dropped. Ask again and " +
                    "I'll finish it."
            } else {
                detail ?: transportMessage()
            }
        else -> transportMessage()
    }

/**
 * Streams one reply as incremental text deltas.
 *
 * @param history the whole transcript INCLUDING the just-typed user turn. Trimming to the server's
 *   ceilings happens here (`toWire`), at the one place every caller streams through, so a future
 *   second caller can't reintroduce the "sent the whole session, got a 400" bug.
 * @param route where the visitor is standing — forwarded to the backend via
 *   [HttpChatConfig.route], the same wire slot the old hand-rolled client used.
 * @param engine the [HttpClientEngine] [HttpChatProvider] sends the request on. Defaults to the
 *   real [httpClientEngine]; a test passes a `MockEngine` instead — this is [HttpChatProvider]'s
 *   own seam, not a bespoke one.
 *
 * The flow completes when the server sends `[DONE]`. It fails with [ChatUnavailable] for a
 * non-2xx response, a transport error, a stream that produced no tokens at all, or — thanks to
 * [HttpChatConfig.requireDoneSentinel] below, since `api/_lib/chat-handler.ts`'s
 * `normalizeStream.flush` guarantees `[DONE]` on every clean finish — one that closed without ever
 * sending it.
 */
fun streamReply(
    history: List<ChatMessage>,
    route: String? = null,
    engine: HttpClientEngine = httpClientEngine(),
): Flow<String> = channelFlow {
    val provider = HttpChatProvider(
        HttpChatConfig(endpoint = CHAT_ENDPOINT, route = route, requireDoneSentinel = true),
        engine,
    )

    try {
        provider.completeStream(history.toWire()).collect { chunk ->
            when (chunk) {
                is AiChunk.Token -> send(chunk.text)
                is AiChunk.Failed -> throw ChatUnavailable(
                    statusMessageFor(chunk.reason, chunk.detail),
                    chunk.reason,
                    retryAfterSeconds = chunk.retryAfterSeconds,
                )
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
    // what closes the channel, so awaiting that close from inside the block would deadlock. // claim-audit:allow -- ordinary concurrency term, not the game
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
