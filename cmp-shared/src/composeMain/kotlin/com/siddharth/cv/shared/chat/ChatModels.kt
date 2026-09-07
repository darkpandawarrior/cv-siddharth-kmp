package com.siddharth.cv.shared.chat

import com.siddharth.kmp.llmchat.AiMessage

/**
 * The wire contract of `POST /api/chat`, transcribed from the endpoint rather than guessed:
 * `cv-siddharth/api/_lib/chat-handler.ts` (validateRequest / normalizeStream / jsonError) and the
 * React client that already speaks it, `cv-siddharth/src/lib/chatClient.ts`.
 *
 * Every constant below mirrors a server-side ceiling. They are duplicated here on purpose — the
 * server 400s a request that breaks one, and a 400 in the middle of a conversation reads to a
 * visitor as "the site is broken" rather than "that message was too long". Trimming client-side
 * loses nothing the server wouldn't have dropped anyway (selectHistory keeps the last 20 turns).
 *
 * The actual wire body ([com.siddharth.kmp.llmchat.HttpChatProvider]'s `HttpChatRequest`) is now
 * assembled by kmp-toolkit, not this file — see `ChatClient.kt`'s file doc for why only `mode` is
 * safe to hand off there and `route` still isn't.
 */

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
 * The transcript → what actually goes up the wire, as [AiMessage]s for
 * [com.siddharth.kmp.llmchat.HttpChatProvider] to send.
 *
 * Two jobs, both mirroring `trimHistory` in chatClient.ts: keep the last [CHAT_MAX_SENT_TURNS]
 * turns, and truncate any single turn over its role's ceiling. A still-streaming turn is dropped —
 * it is the placeholder for the reply being requested, not context for it.
 */
fun List<ChatMessage>.toWire(): List<AiMessage> =
    asSequence()
        .filterNot { it.streaming }
        .filter { it.text.isNotBlank() }
        .toList()
        .takeLast(CHAT_MAX_SENT_TURNS)
        .map { m ->
            val cap = if (m.role == ChatRole.Assistant) CHAT_MAX_ASSISTANT_CHARS else CHAT_MAX_USER_CHARS
            val content = if (m.text.length > cap) m.text.take(cap - 1) + "…" else m.text
            val role = if (m.role == ChatRole.Assistant) AiMessage.Role.ASSISTANT else AiMessage.Role.USER
            AiMessage(role = role, content = content)
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
    check(wire.last().role == AiMessage.Role.ASSISTANT) { "roles must map onto AiMessage.Role" }
    check(wire.first().role == AiMessage.Role.USER)
}
