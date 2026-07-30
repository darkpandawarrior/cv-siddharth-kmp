package com.siddharth.cv.shared.chat

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The wire contract of `POST /api/chat`, transcribed from the endpoint rather than guessed:
 * `cv-siddharth/api/_lib/chat-handler.ts` (validateRequest / normalizeStream / jsonError) and the
 * React client that already speaks it, `cv-siddharth/src/lib/chatClient.ts`.
 *
 * Every constant below mirrors a server-side ceiling. They are duplicated here on purpose — the
 * server 400s a request that breaks one, and a 400 in the middle of a conversation reads to a
 * visitor as "the site is broken" rather than "that message was too long". Trimming client-side
 * loses nothing the server wouldn't have dropped anyway (selectHistory keeps the last 20 turns).
 */

// ---------------------------------------------------------------------------------------------
// Wire DTOs
// ---------------------------------------------------------------------------------------------

/**
 * One turn as the endpoint wants it. `role` is the string `"user"` or `"assistant"` — anything
 * else is a 400 (isValidMessage), which is why this is a plain String rather than the UI's enum:
 * the mapping lives in exactly one place ([ChatRole.wire]) instead of in a serializer.
 */
@Serializable
data class ChatWireMessage(
    val role: String,
    val content: String,
)

/**
 * The request body. `mode` and `route` are omitted rather than sent as null:
 *  - `mode` is a CLOSED allowlist server-side (`undefined | "compose" | "jd"`), so an explicit
 *    `null` is a 400. The port only ever sends normal chat, so it is never populated — the field
 *    exists to document that "compose"/"jd" are the endpoint's, not ours.
 *  - `route` is where the visitor is standing. It is a hint the server re-validates against its
 *    own build-time allowlist (validateRoute) and turns into a server-written sentence, so an
 *    unknown path (this port's `/terminal`) is silently dropped rather than 400'd.
 *
 * `encodeDefaults = false` on the Json instance is what keeps the nulls off the wire.
 */
@Serializable
data class ChatRequestBody(
    val messages: List<ChatWireMessage>,
    val mode: String? = null,
    val route: String? = null,
)

/**
 * One normalized SSE event: `data: {"text":"…"}`.
 *
 * The endpoint re-frames every provider's stream into this single shape (normalizeStream), which
 * is the whole reason this client doesn't need to know whether Groq, Gemini, Cerebras or Anthropic
 * served the reply.
 */
@Serializable
data class ChatDelta(val text: String? = null)

/** The body of every non-2xx: `{"error": "…"}` (jsonError). */
@Serializable
data class ChatErrorBody(@SerialName("error") val error: String? = null)

// ---------------------------------------------------------------------------------------------
// Server ceilings, mirrored
// ---------------------------------------------------------------------------------------------

/** `MAX_HISTORY` — how many turns the server keeps. Sending more is pure waste. */
const val CHAT_MAX_SENT_TURNS: Int = 20

/** `MAX_MESSAGE_CHARS` — one user turn. Also the composer's character cap. */
const val CHAT_MAX_USER_CHARS: Int = 2000

/**
 * `MAX_ASSISTANT_CHARS`. Higher than the user cap because a 1024-token reply routinely runs past
 * 2000 chars, and the client replays its own history verbatim: capping both at 2000 made a long
 * reply 400 the *next* question and brick the conversation.
 */
const val CHAT_MAX_ASSISTANT_CHARS: Int = 6000

// ---------------------------------------------------------------------------------------------
// UI model
// ---------------------------------------------------------------------------------------------

enum class ChatRole {
    User,
    Assistant,
    ;

    /** The exact strings isValidMessage accepts. */
    val wire: String get() = if (this == User) "user" else "assistant"
}

/**
 * A turn as the panel holds it.
 *
 * [streaming] is not derivable from "is this the last message": a settled empty reply and a reply
 * still on the wire look identical otherwise, and the first must render the empty-stream fallback
 * while the second renders a thinking indicator.
 */
data class ChatMessage(
    val role: ChatRole,
    val text: String,
    val streaming: Boolean = false,
)

/**
 * The transcript → what actually goes up the wire.
 *
 * Two jobs, both mirroring `trimHistory` in chatClient.ts: keep the last [CHAT_MAX_SENT_TURNS]
 * turns, and truncate any single turn over its role's ceiling. A still-streaming turn is dropped —
 * it is the placeholder for the reply being requested, not context for it.
 */
fun List<ChatMessage>.toWire(): List<ChatWireMessage> =
    asSequence()
        .filterNot { it.streaming }
        .filter { it.text.isNotBlank() }
        .toList()
        .takeLast(CHAT_MAX_SENT_TURNS)
        .map { m ->
            val cap = if (m.role == ChatRole.Assistant) CHAT_MAX_ASSISTANT_CHARS else CHAT_MAX_USER_CHARS
            val content = if (m.text.length > cap) m.text.take(cap - 1) + "…" else m.text
            ChatWireMessage(role = m.role.wire, content = content)
        }

// ponytail: one runnable check rather than a test module — the trimming is the only logic here,
// and the two ways it has historically broken are "sent the whole session" and "replayed a long
// assistant turn verbatim". Call from any target's main() while poking at chat.
internal fun chatModelsSelfCheck() {
    val long = "x".repeat(CHAT_MAX_ASSISTANT_CHARS + 500)
    val turns = List(30) { ChatMessage(ChatRole.User, "q$it") } +
        ChatMessage(ChatRole.Assistant, long) +
        ChatMessage(ChatRole.Assistant, "", streaming = true)

    val wire = turns.toWire()
    check(wire.size == CHAT_MAX_SENT_TURNS) { "history must be capped at the server's MAX_HISTORY" }
    check(wire.none { it.content.isEmpty() }) { "an empty turn is a 400 (content.length > 0)" }
    check(wire.last().content.length == CHAT_MAX_ASSISTANT_CHARS) { "a long assistant turn is truncated, not dropped" }
    check(wire.last().role == "assistant") { "roles must be the literal wire strings" }
    check(wire.first().role == "user")
}
