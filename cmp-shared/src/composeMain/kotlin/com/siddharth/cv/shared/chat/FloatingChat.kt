package com.siddharth.cv.shared.chat

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.siddharth.cv.shared.LocalNav
import com.siddharth.cv.shared.Route
import com.siddharth.cv.shared.data.projectBySlug
import com.siddharth.cv.shared.data.projectOrder
import com.siddharth.cv.shared.theme.CvCard
import com.siddharth.cv.shared.theme.CvMotion
import com.siddharth.cv.shared.theme.GhostButton
import com.siddharth.cv.shared.theme.LocalReducedMotion
import com.siddharth.cv.shared.theme.PrimaryButton
import com.siddharth.cv.shared.theme.cvColors
import com.siddharth.cv.shared.theme.cvType
import com.siddharth.cv.shared.theme.rememberInfiniteFloat
import com.siddharth.cv.shared.toPath
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

/**
 * The console — Siddharth's AI assistant as a floating panel, ported from
 * `cv-siddharth/src/FloatingChat.tsx` against the same live endpoint.
 *
 * WHAT DELIBERATELY DIDN'T COME ACROSS, and why. The React panel carries four things this one
 * doesn't: generative UI (`[[rooms]]` directives rendering real components), slash commands, the
 * JD fit analyzer, and Web Speech voice I/O. Each is a subsystem, not a feature — the directive
 * parser alone is a file — and none of them is what "does the chat stream on wasm" is asking. What
 * IS here is the whole streaming contract: the transcript, live tokens, the route-aware quick
 * prompts, and every failure the endpoint can hand back, said out loud.
 * ponytail: add the directive renderer first if this grows — it's the one that changes what the
 * model is *for*, and the terminal already proves the widget vocabulary ports.
 *
 * ONE ENTRY POINT: [FloatingChat] owns its own open/closed state, so the integrator overlays it and
 * nothing else. It draws into a full-size transparent [Box]; Compose only intercepts pointer input
 * where a modifier asks for it, so the empty area stays click-through to the page underneath.
 */
@Composable
fun FloatingChat(modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
        Box(Modifier.padding(24.dp)) {
            if (open) ChatPanel(onClose = { open = false }) else ChatLauncher(onOpen = { open = true })
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Launcher
// ---------------------------------------------------------------------------------------------

/**
 * The bubble. Accent fill, ink glyph, 56dp — the same affordance as the web build's
 * `bottom-6 right-6 h-14 w-14 rounded-full bg-accent`.
 *
 * The glyph is drawn rather than imported: the port has no icon dependency (the web build's
 * `lucide-react` has no Compose equivalent on the classpath), and a speech bubble is a rounded
 * rect, a tail and three dots.
 */
@Composable
private fun ChatLauncher(onOpen: () -> Unit) {
    val colors = cvColors
    val reduced = LocalReducedMotion.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val scale by animateFloatAsState(
        if (hovered && !reduced) 1.05f else 1f,
        tween(CvMotion.DurFast, easing = CvMotion.EaseSpring),
        label = "launcherScale",
    )

    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .size(56.dp)
            .background(colors.accent, CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onOpen,
            )
            .semantics { contentDescription = "Open chat with Panda, Siddharth's AI assistant" }
            .drawBehind {
                val ink = colors.ink
                val w = size.width
                val h = size.height
                // Bubble body: a rounded rect inset from the circle, plus a tail notch.
                drawRoundRect(
                    color = ink,
                    topLeft = Offset(w * 0.24f, h * 0.28f),
                    size = Size(w * 0.52f, h * 0.34f),
                    cornerRadius = CornerRadius(w * 0.10f),
                    style = Stroke(width = w * 0.045f),
                )
                drawLine(
                    color = ink,
                    start = Offset(w * 0.36f, h * 0.62f),
                    end = Offset(w * 0.32f, h * 0.74f),
                    strokeWidth = w * 0.045f,
                )
                repeat(3) { i ->
                    drawCircle(
                        color = ink,
                        radius = w * 0.030f,
                        center = Offset(w * (0.36f + i * 0.14f), h * 0.45f),
                    )
                }
            },
    )
}

// ---------------------------------------------------------------------------------------------
// Panel
// ---------------------------------------------------------------------------------------------

private val PanelShape = RoundedCornerShape(16.dp)

@Composable
private fun ChatPanel(onClose: () -> Unit) {
    val colors = cvColors
    val scope = rememberCoroutineScope()
    val nav = LocalNav.current
    val route = nav.current

    // The transcript, minus the greeting. The greeting is RENDERED from the current route and never
    // stored: it must not become a turn the model is asked to account for, and the server's history
    // is short enough that spending one on "hi" is a real cost.
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var inFlight by remember { mutableStateOf<Job?>(null) }
    val busy = inFlight != null

    val listState = rememberLazyListState()
    val reduced = LocalReducedMotion.current
    // Follow the stream. Keyed on the last message's LENGTH as well as the list size: the list
    // identity never changes while tokens land on the same element, so `messages.size` alone would
    // pin the view at the top of a reply and let it grow off-screen.
    LaunchedEffect(messages.size, messages.lastOrNull()?.text?.length, error) {
        val target = (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
        // A smooth scroll IS motion, and a transcript that slides on every token is exactly what
        // prefers-reduced-motion is asking us not to do. Jump instead — the content still follows.
        if (reduced) listState.scrollToItem(target) else listState.animateScrollToItem(target)
    }

    val send: (String) -> Unit = handler@{ text ->
        val trimmed = text.trim()
        if (trimmed.isEmpty() || busy) return@handler
        error = null
        input = ""
        messages.add(ChatMessage(ChatRole.User, trimmed.take(CHAT_MAX_USER_CHARS)))
        val placeholder = messages.size
        messages.add(ChatMessage(ChatRole.Assistant, "", streaming = true))
        inFlight = scope.launch {
            try {
                streamReply(messages.toList(), route.toPath()).catch { e ->
                    error = (e as? ChatUnavailable)?.message ?: CHAT_CONTACT_FALLBACK
                }.collect { delta ->
                    messages.appendDelta(placeholder, delta)
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } finally {
                messages.settle(placeholder)
                inFlight = null
            }
        }
    }

    Column(
        modifier = Modifier
            .widthIn(max = 400.dp)
            .fillMaxWidth()
            .heightIn(max = 560.dp)
            .background(colors.card, PanelShape)
            .border(1.dp, colors.line, PanelShape),
    ) {
        PanelHeader(onClose = onClose)

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
                .semantics {
                    contentDescription = "Conversation transcript"
                    liveRegion = LiveRegionMode.Polite
                },
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item("greeting") { AssistantBubble(greetingFor(route), streaming = false) }

            itemsIndexed(messages) { _, m ->
                when (m.role) {
                    ChatRole.User -> UserBubble(m.text)
                    ChatRole.Assistant -> AssistantBubble(m.text, streaming = m.streaming)
                }
            }

            error?.let { text ->
                item("error") { ErrorLine(text) }
            }

            // Quick prompts stay out of the way while a reply is landing — an "ask next" list under
            // a half-written answer is noise, and tapping one mid-stream would be dropped anyway.
            if (!busy) {
                item("prompts") {
                    QuickPrompts(
                        prompts = quickPromptsFor(route, messages),
                        heading = if (messages.isEmpty()) null else "Ask next",
                        onPick = send,
                    )
                }
            }
        }

        Composer(
            value = input,
            onValueChange = { input = it.take(CHAT_MAX_USER_CHARS) },
            onSubmit = { send(input) },
            busy = busy,
            onStop = { inFlight?.cancel() },
        )
    }
}

@Composable
private fun PanelHeader(onClose: () -> Unit) {
    val colors = cvColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText("sid@android:~$", style = cvType.metaMono.copy(color = colors.accent))
                Spacer(Modifier.width(6.dp))
                BasicText(
                    "Panda",
                    style = cvType.cardTitle.copy(color = colors.onBackground, fontWeight = FontWeight.Bold),
                )
            }
            BasicText(
                "Answers as Siddharth · streams from the live endpoint",
                style = cvType.metaMono,
            )
        }
        GhostButton(text = "Close", onClick = onClose)
    }
}

// ---------------------------------------------------------------------------------------------
// Bubbles
// ---------------------------------------------------------------------------------------------

@Composable
private fun UserBubble(text: String) {
    val colors = cvColors
    val shape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box(
            Modifier
                .padding(start = 32.dp)
                .background(colors.accent.copy(alpha = 0.15f), shape)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            BasicText(text, style = cvType.bodySmall.copy(color = colors.onBackground))
        }
    }
}

/**
 * An assistant turn. Three states, and conflating any two of them is what made the web build's
 * earlier versions read as broken: still streaming with text (render it), still streaming with
 * nothing yet (a thinking indicator), and settled (render it, even if the server's
 * EMPTY_STREAM_FALLBACK is all that arrived).
 */
@Composable
private fun AssistantBubble(text: String, streaming: Boolean) {
    val colors = cvColors
    val shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
    Row(Modifier.fillMaxWidth().padding(end = 32.dp)) {
        Box(
            Modifier
                .background(colors.surface, shape)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            if (streaming && text.isEmpty()) {
                ThinkingIndicator()
            } else {
                BasicText(
                    text = chatMarkdown(stripDirectives(text), colors.accent),
                    style = cvType.bodySmall.copy(color = colors.onBackground),
                )
            }
        }
    }
}

/** Pulsing "thinking…". [rememberInfiniteFloat] parks at `from` under reduced motion, so this
 *  becomes a static label rather than a paused animation — no special case needed here. */
@Composable
private fun ThinkingIndicator() {
    val alpha by rememberInfiniteFloat(900, from = 1f, to = 0.35f, easing = CvMotion.EaseOutQuart)
    BasicText(
        text = "thinking…",
        modifier = Modifier.graphicsLayer { this.alpha = alpha },
        style = cvType.metaMono,
    )
}

/**
 * Failures are TEXT, not a toast and not a silent stop. Every message reaching here was written to
 * tell a visitor what to do next — see `toChatFailure` for whose words win and why.
 */
@Composable
private fun ErrorLine(text: String) {
    val shape = RoundedCornerShape(12.dp)
    val red = Color(0xFFFF5C7A)
    Box(
        Modifier
            .fillMaxWidth()
            .background(red.copy(alpha = 0.08f), shape)
            .border(1.dp, red.copy(alpha = 0.35f), shape)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics { liveRegion = LiveRegionMode.Assertive },
    ) {
        BasicText(text, style = cvType.bodySmall.copy(color = red))
    }
}

// ---------------------------------------------------------------------------------------------
// Quick prompts
// ---------------------------------------------------------------------------------------------

@Composable
private fun QuickPrompts(prompts: List<String>, heading: String?, onPick: (String) -> Unit) {
    if (prompts.isEmpty()) return
    val colors = cvColors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        heading?.let {
            BasicText(it.uppercase(), style = cvType.eyebrow)
        }
        prompts.forEach { q ->
            val interaction = remember(q) { MutableInteractionSource() }
            val hovered by interaction.collectIsHoveredAsState()
            val shape = RoundedCornerShape(12.dp)
            Box(
                Modifier
                    .fillMaxWidth()
                    .border(1.dp, if (hovered) colors.accent else colors.line, shape)
                    .clickable(interactionSource = interaction, indication = null) { onPick(q) }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
            ) {
                BasicText(
                    q,
                    style = cvType.bodySmall.copy(color = if (hovered) colors.accent else colors.muted),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Composer
// ---------------------------------------------------------------------------------------------

@Composable
private fun Composer(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    busy: Boolean,
    onStop: () -> Unit,
) {
    val colors = cvColors
    val shape = RoundedCornerShape(999.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .weight(1f)
                .background(colors.ink, shape)
                .border(1.dp, colors.line, shape)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            if (value.isEmpty()) {
                BasicText("Ask about my work…", style = cvType.bodySmall.copy(color = colors.muted))
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = cvType.bodySmall.copy(color = colors.onBackground),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier
                    .fillMaxWidth()
                    // Enter submits, Shift+Enter doesn't — the field is single-line so Shift+Enter
                    // is inert rather than a newline, but swallowing it here would make the two
                    // keys behave identically and hide that.
                    .onPreviewKeyEvent { e ->
                        val enter = e.key == Key.Enter || e.key == Key.NumPadEnter
                        if (e.type == KeyEventType.KeyDown && enter && !e.isShiftPressed) {
                            onSubmit()
                            true
                        } else {
                            false
                        }
                    }
                    .semantics { contentDescription = "Ask Panda a question" },
            )
        }
        if (busy) {
            GhostButton(text = "Stop", onClick = onStop)
        } else {
            PrimaryButton(text = "Send", onClick = onSubmit)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Content helpers
// ---------------------------------------------------------------------------------------------

/**
 * The greeting, rendered from the CURRENT route rather than stored — that is what lets it
 * acknowledge where the visitor is standing without ever becoming a second turn or resetting the
 * conversation. Mirrors `greetingFor` in `cv-siddharth/src/lib/chatContext.ts`.
 */
private fun greetingFor(route: Route): String = when (route) {
    is Route.ProjectDetail -> {
        val label = projectBySlug(route.slug)?.name?.substringBefore(" + ")?.trim()
        if (label != null) {
            "You're looking at **$label** — ask me anything about it, or ask me to show you " +
                "somewhere else on the site."
        } else {
            HOME_GREETING
        }
    }
    Route.Resume -> "You're on **the résumé** — ask me to walk you through any of it, or where the numbers come from."
    Route.Terminal -> "You're in **the terminal** — `help` lists what it does. Or just ask me here."
    Route.Lab -> "You're at **the lab bench** — each experiment is a real production problem, " +
        "simulated. Ask me what any of them is actually demonstrating."
    Route.Playground -> "You're in **the playground** — type Compose on the left, watch it render " +
        "with real composables on the right. Ask me what the subset covers, or why there is no " +
        "compile step."
    Route.Forge -> "You're in **the forge** — a few thousand particles spring-tied to the wordmark. " +
        "Ask me anything about the rest of the site while you play with it."
    Route.Home -> HOME_GREETING
}

private const val HOME_GREETING =
    "Hi, I'm **Panda** — Siddharth's AI assistant. Ask me about his Android work: GPS engineering, " +
        "the Compose migration, crash hunts, or any of the projects on this site."

/**
 * Route-aware suggestions, and every one of them is answerable from the real profile data the
 * server's system prompt is built from — a chip that produces "I don't know" is worse than no chip.
 *
 * Templated off the project's own name rather than per-slug copy, exactly as `chipsFor` does: hand
 * -written questions drift the moment a project is renamed or added.
 */
private fun quickPromptsFor(route: Route, asked: List<ChatMessage>): List<String> {
    val alreadyAsked = asked.filter { it.role == ChatRole.User }.map { it.text }.toSet()
    val all = when (route) {
        is Route.ProjectDetail -> {
            val label = projectBySlug(route.slug)?.name?.substringBefore(" + ")?.trim()
            if (label == null) HOME_PROMPTS else listOf(
                "How did you build $label?",
                "What was the hardest part of $label?",
                "What's the stack behind $label?",
            )
        }
        Route.Resume -> listOf(
            "Walk me through your experience",
            "What are you strongest at?",
            "Are you open to new roles?",
        )
        else -> HOME_PROMPTS
    }
    // Fewer follow-ups than opening prompts: the opening list is the menu, a follow-up list is a
    // nudge, and five nudges under a finished answer reads as a form.
    val room = if (asked.isEmpty()) 5 else 3
    return all.filterNot { it in alreadyAsked }.take(room)
}

/**
 * The home set, mirroring `QUICK_PROMPTS` in chatContext.ts. The metric questions are the audited
 * numbers from `data/CvProfileData.kt` — "50% → 95%" and "~87% of UI-layer code" are claim-audit
 * claims, so the questions are phrased to ask about them rather than to assert them.
 */
private val HOME_PROMPTS = listOf(
    "What can I do on this site?",
    "How did you get GPS accuracy to 95%?",
    "Which project should I look at first?",
    "Tell me about the Compose migration",
    "How did you cut crashes by 80%?",
    "What are you building in Kotlin Multiplatform?",
)

/**
 * Removes the endpoint's generative-UI directives so they never reach the transcript as literal text.
 *
 * The shared system prompt tells the model it may emit `[[rooms]]`, `[[projects]]` and friends on
 * their own line; the React client swaps each for a real component. This port doesn't render them
 * (that's a whole widget subsystem), and the same prompt serves both clients — so the directives
 * arrive here regardless and MUST be dropped. Leaving them visible was a real defect caught on
 * screen: a reply about the site ended with a bare `[[rooms]]`.
 *
 * Only whole-line directives are stripped. Inline `[[…]]` inside a sentence is left alone — it is
 * far more likely to be prose about the syntax than an instruction to the client.
 *
 * ponytail: when the widget renderer lands, this becomes the tokenizer that feeds it instead of a
 * delete. Until then, dropping is strictly better than showing.
 */
internal fun stripDirectives(text: String): String =
    text.lineSequence()
        .filterNot { line ->
            val t = line.trim()
            t.length > 4 && t.startsWith("[[") && t.endsWith("]]") && !t.drop(2).dropLast(2).contains("[[")
        }
        .joinToString("\n")
        .trim('\n')

/**
 * The smallest markdown that keeps the model's replies readable: `**bold**` and `` `code` ``.
 *
 * The system prompt asks for those two and for bullet lists; bullets already render acceptably as
 * plain text lines, and headings/links/tables never appear. A real parser here would be a file.
 * ponytail: replace with a proper block parser the day the widget directives (`[[rooms]]`) port —
 * they need one anyway, and doing it twice is the waste.
 */
private fun chatMarkdown(text: String, accent: Color): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        val bold = text.indexOf("**", i)
        val code = text.indexOf('`', i)
        val next = listOf(bold, code).filter { it >= 0 }.minOrNull() ?: -1
        if (next < 0) {
            append(text.substring(i))
            return@buildAnnotatedString
        }
        append(text.substring(i, next))

        if (next == bold) {
            val end = text.indexOf("**", next + 2)
            if (end < 0) {
                append(text.substring(next))
                return@buildAnnotatedString
            }
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = accent)) {
                append(text.substring(next + 2, end))
            }
            i = end + 2
        } else {
            val end = text.indexOf('`', next + 1)
            if (end < 0) {
                append(text.substring(next))
                return@buildAnnotatedString
            }
            withStyle(SpanStyle(color = accent)) { append(text.substring(next + 1, end)) }
            i = end + 1
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Transcript mutation
// ---------------------------------------------------------------------------------------------

/**
 * Append a token to the in-flight assistant turn.
 *
 * Guarded on the index still holding a streaming assistant message: the collect runs in a coroutine
 * that outlives nothing in particular, and a transcript reset while a reply is on the wire would
 * otherwise write a token into whatever now occupies that slot.
 */
private fun SnapshotStateList<ChatMessage>.appendDelta(index: Int, delta: String) {
    val current = getOrNull(index) ?: return
    if (!current.streaming) return
    this[index] = current.copy(text = current.text + delta)
}

/** Clear the streaming flag once the stream ends, however it ended. */
private fun SnapshotStateList<ChatMessage>.settle(index: Int) {
    val current = getOrNull(index) ?: return
    if (!current.streaming) return
    // A stream that failed before its first token leaves an empty bubble; the error line below it
    // carries the explanation, so the placeholder is removed rather than left as a blank turn.
    if (current.text.isEmpty()) removeAt(index) else this[index] = current.copy(streaming = false)
}

// ponytail: one runnable check instead of a test module — the markdown scanner is the only branchy
// pure function in this file, and every way it can break is "index arithmetic ate a character".
internal fun floatingChatSelfCheck() {
    fun flat(s: String) = chatMarkdown(s, Color.Red).text

    check(flat("plain") == "plain")
    check(flat("a **b** c") == "a b c") { "bold markers are consumed, text is not" }
    check(flat("`code` tail") == "code tail")
    check(flat("**unclosed") == "**unclosed") { "an unterminated marker must render literally, not vanish" }
    check(flat("`unclosed") == "`unclosed")
    check(flat("**a** and `b`") == "a and b") { "multiple spans in one string" }
    check(flat("") == "")

    // Directives must never reach the transcript — this shipped visibly broken once.
    check(stripDirectives("Here you go.\n[[rooms]]") == "Here you go.") { "whole-line directive dropped" }
    check(stripDirectives("[[rooms]]") == "") { "a reply that is only a directive collapses to empty" }
    check(stripDirectives("a\n  [[projects]]  \nb") == "a\nb") { "indented/padded directive dropped" }
    check(stripDirectives("see [[rooms]] inline") == "see [[rooms]] inline") { "inline is prose, keep it" }
    check(stripDirectives("[[]]") == "[[]]") { "too short to be a directive" }
    check(stripDirectives("no directives here") == "no directives here")

    // The prompt list must never re-offer a question already asked, or the panel nags.
    val asked = listOf(ChatMessage(ChatRole.User, HOME_PROMPTS.first()))
    check(HOME_PROMPTS.first() !in quickPromptsFor(Route.Home, asked))
    // Every project in the pager must produce templated chips rather than falling back home.
    projectOrder.forEach { slug ->
        val chips = quickPromptsFor(Route.ProjectDetail(slug), emptyList())
        check(chips.any { it.startsWith("How did you build ") }) { "no templated chip for $slug" }
    }
}
