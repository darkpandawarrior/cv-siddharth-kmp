package com.siddharth.cv.shared.palette

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.siddharth.cv.shared.data.projects
import com.siddharth.cv.shared.home.homeSections
import com.siddharth.cv.shared.theme.LocalReducedMotion
import com.siddharth.cv.shared.theme.MonoMeta
import com.siddharth.cv.shared.theme.Reveal
import com.siddharth.cv.shared.theme.cvColors
import com.siddharth.cv.shared.theme.cvType

/**
 * One row of the palette. Deliberately carries an [id] and NOT a lambda.
 *
 * A `run: () -> Unit` in the model would drag `CvNavState`, the clipboard and the URL opener into
 * this file, and every one of those is platform- or app-shell-owned. An id keeps the command list a
 * pure value — orderable, filterable, checkable, and (see [paletteSelfCheck]) provable — while the
 * caller owns the single `when (id)` that turns a pick into an effect.
 */
data class PaletteCommand(val label: String, val group: String, val id: String)

/**
 * The whole command list, generated from the same data the site renders.
 *
 * Nothing here is hand-typed twice: sections come from `homeSections`, project rows come from
 * `projects`, so a new section or a new project appears in the palette without anyone remembering to
 * add it. That is the entire reason this is a function over the real data instead of a literal list —
 * the React original hand-maintained both and had already drifted (its `projects-section` id, its
 * missing `explore`).
 *
 * Ids are stable strings in a `namespace:key` shape so the caller's dispatch reads as a routing
 * table. `section:` scrolls the homepage, `route:` navigates, `project:` opens a case study,
 * `action:` does something that isn't navigation.
 */
fun paletteCommands(): List<PaletteCommand> = buildList {
    homeSections.forEach { section ->
        add(PaletteCommand(label = section.label, group = "Jump", id = "section:${section.id}"))
    }

    add(PaletteCommand("Résumé", "Open", "route:resume"))
    add(PaletteCommand("The Terminal — a faux shell you can type in", "Open", "route:terminal"))
    add(PaletteCommand("The Lab Bench — the numbers, running live", "Open", "route:lab"))
    add(PaletteCommand("The Particle Forge — cursor-reactive swarm", "Open", "route:forge"))

    // Only projects with a detail page: a palette row that lands on a 404 is worse than no row.
    projects.filter { it.detail != null }.forEach { p ->
        add(PaletteCommand("Open project: ${p.name}", "Case study", "project:${p.slug}"))
    }

    add(PaletteCommand("Copy email address", "Action", "action:copy-email"))
    add(PaletteCommand("Open GitHub", "External", "action:github"))
    add(PaletteCommand("Open LinkedIn", "External", "action:linkedin"))
}

// ---------------------------------------------------------------------------------------------
// Matching
// ---------------------------------------------------------------------------------------------

// Tier floors. The gap between adjacent tiers (200) is wider than [BonusCap] (150), so a
// within-tier tightness bonus can never lift a match above the tier below it — the ranking is
// lexicographic (tier first, tightness second) without needing a comparator to say so.
private const val TierPrefix = 900
private const val TierWordStart = 700
private const val TierContains = 500
private const val TierSubsequence = 200
private const val BonusCap = 150

/** A match starts at a "word" if it starts the string or follows a non-alphanumeric character. */
private fun isWordStart(text: String, index: Int): Boolean =
    index == 0 || !text[index - 1].isLetterOrDigit()

/**
 * Lower-case and strip the diacritics, so "resume" finds "Résumé".
 *
 * Not cosmetic: "Résumé" is the label of the single most-searched row on the site and nobody types
 * the accents. `java.text.Normalizer` is the usual answer and is illegal in composeMain, so this is
 * an explicit table — which is also all the site's Latin-1 labels need.
 */
private fun fold(text: String): String =
    text.lowercase().map { c ->
        when (c) {
            'é', 'è', 'ê', 'ë' -> 'e'
            'á', 'à', 'â', 'ä', 'ã', 'å' -> 'a'
            'í', 'ì', 'î', 'ï' -> 'i'
            'ó', 'ò', 'ô', 'ö', 'õ' -> 'o'
            'ú', 'ù', 'û', 'ü' -> 'u'
            'ç' -> 'c'
            'ñ' -> 'n'
            else -> c
        }
    }.joinToString("")

/**
 * How well [query] matches [label] — higher is better, `null` is no match at all.
 *
 * Subsequence matching, not `startsWith`: "mw" has to find Mileway and "pl" PaymentsLab, because
 * that is how anyone who already knows the site types. But a subsequence match alone ranks nonsense
 * alongside intent, so the tiers exist:
 *
 *  - [TierPrefix] — the label starts with the query ("mile" -> Mileway).
 *  - [TierWordStart] — the query appears whole, at a word boundary ("forge" -> The Particle Forge).
 *  - [TierContains] — the query appears whole, mid-word.
 *  - [TierSubsequence] — the characters appear in order, scattered.
 *
 * An empty query matches everything with the same score, which keeps the caller's sort stable and so
 * shows the list in its declared order.
 *
 * ponytail: the subsequence walk is greedy-leftmost, so its span is not always the tightest possible
 * one ("aa" in "a-aa"). Swap in a backwards second pass if a real query ever ranks visibly wrong;
 * with ~25 short labels it never has.
 */
internal fun paletteScore(query: String, label: String): Int? {
    val q = fold(query.trim())
    if (q.isEmpty()) return 0
    val l = fold(label)

    if (l.startsWith(q)) return TierPrefix

    val first = l.indexOf(q)
    if (first >= 0) {
        // Prefer a word-boundary occurrence even if a mid-word one comes earlier: "lab" should read
        // as "The *Lab* Bench", not as the "lab" inside a hypothetical "collaboration".
        var at = first
        var boundary = -1
        while (at >= 0) {
            if (isWordStart(l, at)) {
                boundary = at
                break
            }
            at = l.indexOf(q, at + 1)
        }
        return if (boundary >= 0) {
            TierWordStart + positionBonus(boundary)
        } else {
            TierContains + positionBonus(first)
        }
    }

    var qi = 0
    var start = -1
    var end = -1
    var boundaries = 0
    for (i in l.indices) {
        if (qi == q.length) break
        if (l[i] == q[qi]) {
            if (start < 0) start = i
            end = i
            if (isWordStart(l, i)) boundaries++
            qi++
        }
    }
    if (qi != q.length) return null

    val span = end - start + 1
    val tightness = (120 - span).coerceAtLeast(0) + boundaries * 6
    return TierSubsequence + tightness.coerceAtMost(BonusCap)
}

/** Earlier in the label is better, and the ramp is capped so it stays inside its tier. */
private fun positionBonus(index: Int): Int = (BonusCap - index).coerceIn(0, BonusCap)

/**
 * The filtered, ranked list the palette shows.
 *
 * The group name is searchable too but at a quarter weight, so typing "external" finds the GitHub
 * and LinkedIn rows without ever outranking a real label hit. `sortedByDescending` is a stable sort,
 * which is what makes an empty query render the declared order rather than an arbitrary one.
 */
internal fun paletteFilter(query: String, commands: List<PaletteCommand>): List<PaletteCommand> {
    if (query.isBlank()) return commands
    return commands
        .mapNotNull { cmd ->
            val score = paletteScore(query, cmd.label)
                ?: paletteScore(query, cmd.group)?.let { it / 4 }
            score?.let { cmd to it }
        }
        .sortedByDescending { it.second }
        .map { it.first }
}

// ---------------------------------------------------------------------------------------------
// UI
// ---------------------------------------------------------------------------------------------

private val PanelShape = RoundedCornerShape(18.dp)
private val RowShape = RoundedCornerShape(12.dp)
private val PillShape = RoundedCornerShape(999.dp)

/**
 * The ⌘K palette — a centred overlay over whatever screen is showing.
 *
 * [visible] is a parameter rather than internal state on purpose: the chord itself has to be caught
 * at the window level (the browser's keydown, the desktop window, an Android hardware keyboard), and
 * that is platform-shell work. This composable owns everything downstream of "it's open now".
 *
 * Closed means *not composed*, not composed-and-hidden: the overlay fills the window and eats
 * pointer input, so leaving it in the tree while invisible would silently kill the page underneath.
 * That also means the query and selection reset on every open for free, with no teardown branch.
 */
@Composable
fun CommandPalette(
    visible: Boolean,
    onDismiss: () -> Unit,
    onCommand: (PaletteCommand) -> Unit,
) {
    if (!visible) return

    val colors = cvColors
    val reduced = LocalReducedMotion.current
    val commands = remember { paletteCommands() }
    var query by remember { mutableStateOf("") }
    var active by remember { mutableStateOf(0) }
    val focus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val scrim = remember { MutableInteractionSource() }

    val results = remember(query, commands) { paletteFilter(query, commands) }
    // Clamp rather than reset-on-change: the list can shrink under the cursor while the user is
    // still typing, and a stale `active` would run the wrong command on Enter.
    val activeIndex = active.coerceIn(0, results.lastIndex.coerceAtLeast(0))

    LaunchedEffect(query) { active = 0 }

    // Focus after a frame, the same reason the web version waits for a requestAnimationFrame: on
    // wasm/iOS the field's platform text input isn't attached in the first composition pass and the
    // focus request is dropped on the floor.
    LaunchedEffect(visible) {
        withFrameNanos { }
        focus.requestFocus()
    }

    // Keep the highlighted row on screen. Only scrolls when the row is actually clipped — the CSS
    // original's `block: "nearest"` — because scrolling on every arrow press makes the list feel
    // like it's fighting the keyboard.
    LaunchedEffect(activeIndex, results.size) {
        val info = listState.layoutInfo
        val row = info.visibleItemsInfo.firstOrNull { it.index == activeIndex }
        val onScreen = row != null &&
            row.offset >= info.viewportStartOffset &&
            row.offset + row.size <= info.viewportEndOffset
        if (!onScreen) {
            // Keyboard navigation under reduced motion jumps: a smooth scroll fired by a keypress is
            // exactly the involuntary movement the preference asks us to drop.
            if (reduced) listState.scrollToItem(activeIndex) else listState.animateScrollToItem(activeIndex)
        }
    }

    val run: (PaletteCommand) -> Unit = { cmd ->
        onCommand(cmd)
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.ink.copy(alpha = 0.82f))
            // Click-outside closes. `indication = null` because a ripple across the whole window is
            // not a thing; the role stays Button so it is at least announced as dismissable.
            .clickable(
                interactionSource = scrim,
                indication = null,
                role = Role.Button,
                onClick = onDismiss,
            )
            .semantics { contentDescription = "Command palette. Press Escape to close." }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Reveal {
            Column(
                modifier = Modifier
                    .padding(top = 88.dp)
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    // Swallow taps so a click on the panel doesn't reach the scrim's dismiss.
                    // A no-op `clickable` would do it too, but would also publish a second phantom
                    // button to assistive tech.
                    .pointerInput(Unit) { detectTapGestures { } }
                    .background(colors.surface, PanelShape)
                    .border(1.dp, colors.line, PanelShape),
            ) {
                QueryRow(
                    query = query,
                    onQueryChange = { query = it },
                    focusRequester = focus,
                    onKey = handler@{ event ->
                        if (event.type != KeyEventType.KeyDown) return@handler false
                        when (event.key) {
                            Key.DirectionDown -> {
                                active = (activeIndex + 1).coerceAtMost(results.lastIndex.coerceAtLeast(0))
                                true
                            }
                            Key.DirectionUp -> {
                                active = (activeIndex - 1).coerceAtLeast(0)
                                true
                            }
                            Key.Enter, Key.NumPadEnter -> {
                                results.getOrNull(activeIndex)?.let(run)
                                true
                            }
                            Key.Escape -> {
                                onDismiss()
                                true
                            }
                            // Focus trap: the rows are focusable, so an untrapped Tab would walk the
                            // highlight and the focus ring out of step with each other.
                            Key.Tab -> true
                            else -> false
                        }
                    },
                )

                if (results.isEmpty()) {
                    BasicText(
                        text = "No matches.",
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        style = cvType.bodySmall,
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp),
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        itemsIndexed(results, key = { _, cmd -> cmd.id }) { index, cmd ->
                            CommandRow(
                                command = cmd,
                                active = index == activeIndex,
                                onHover = { active = index },
                                onSelect = { run(cmd) },
                            )
                        }
                    }
                }

                FooterRow(shown = results.size, total = commands.size)
            }
        }
    }
}

@Composable
private fun QueryRow(
    query: String,
    onQueryChange: (String) -> Unit,
    focusRequester: FocusRequester,
    onKey: (KeyEvent) -> Boolean,
) {
    val colors = cvColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText("⌘K", style = cvType.metaMono.copy(color = colors.accent))
        Spacer(Modifier.width(12.dp))

        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                BasicText(
                    text = "Jump to a section, open a project…",
                    style = cvType.bodySmall.copy(color = colors.muted),
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = cvType.bodySmall.copy(color = colors.onBackground),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    // The whole keymap hangs off the field because the field always holds focus while
                    // the palette is open, so this is the one node every key event passes through.
                    .onPreviewKeyEvent(onKey)
                    .semantics { contentDescription = "Command palette search" },
            )
        }

        Spacer(Modifier.width(12.dp))
        Box(
            Modifier
                .border(1.dp, colors.line, PillShape)
                .padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            MonoMeta("esc")
        }
    }
}

/**
 * One command. The active row is marked three ways — accent wash, accent label, a ↵ hint — because
 * "highlighted" has to survive both a colourblind reader and a screen reader, hence `selected` in
 * semantics too.
 */
@Composable
private fun CommandRow(
    command: PaletteCommand,
    active: Boolean,
    onHover: () -> Unit,
    onSelect: () -> Unit,
) {
    val colors = cvColors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    // ponytail: no phantom-hover guard. The web version needs one because a stationary cursor over a
    // freshly-opened overlay fires a synthetic mouseenter; if that ever shows up here, gate this on a
    // first real pointer move the way CommandPalette.tsx does.
    LaunchedEffect(hovered) { if (hovered) onHover() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (active) colors.accent.copy(alpha = 0.15f) else Color.Transparent, RowShape)
            .hoverable(interaction)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onSelect,
            )
            .semantics { selected = active }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text = command.label,
            modifier = Modifier.weight(1f),
            style = cvType.bodySmall.copy(
                color = if (active) colors.onBackground else colors.muted,
            ),
        )
        Spacer(Modifier.width(12.dp))
        if (active) {
            BasicText("↵", style = cvType.metaMono.copy(color = colors.accent))
            Spacer(Modifier.width(8.dp))
        }
        BasicText(
            text = command.group,
            style = cvType.metaMono.copy(color = if (active) colors.accent else colors.muted),
        )
    }
}

/** Visible text, not a tooltip — the match count is the only feedback that filtering happened. */
@Composable
private fun FooterRow(shown: Int, total: Int) {
    val colors = cvColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.card, RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MonoMeta("↑↓ move · ↵ open · esc close")
        Spacer(Modifier.weight(1f))
        MonoMeta("$shown of $total")
    }
}

// ---------------------------------------------------------------------------------------------
// Self-check
// ---------------------------------------------------------------------------------------------

/**
 * ponytail: one runnable check instead of a test module. Everything asserted here is a property the
 * UI cannot show is broken — a ranking regression looks like "the palette feels wrong", and a
 * duplicated id looks like a LazyColumn crash three screens away.
 */
internal fun paletteSelfCheck() {
    val commands = paletteCommands()

    // A duplicate id is two bugs at once: the LazyColumn key collides and the caller's dispatch
    // becomes ambiguous. Generated ids make this cheap to get wrong, so it is checked.
    val ids = commands.map { it.id }
    check(ids.size == ids.toSet().size) { "duplicate palette command id: ${ids.groupBy { it }.filterValues { it.size > 1 }.keys}" }
    check(commands.isNotEmpty()) { "palette generated no commands" }
    check(commands.any { it.id == "route:lab" } && commands.any { it.id == "route:forge" }) { "lab and forge rows must exist" }
    check(commands.count { it.id.startsWith("section:") } == homeSections.size) { "one row per homepage section" }

    // Case-insensitive, both directions.
    check(paletteScore("MILE", "Mileway") == paletteScore("mile", "mileway")) { "case-insensitive" }
    check(paletteScore("FORGE", "The Particle Forge — cursor-reactive swarm") != null) { "upper-case query matches" }
    check(paletteScore("resume", "Résumé") == TierPrefix) { "unaccented query is an exact prefix of the accented label" }

    // Exact prefix outranks a word-boundary hit outranks a scattered subsequence.
    val prefix = paletteScore("mile", "Mileway")!!
    val wordStart = paletteScore("way", "Mile way")!!
    val scattered = paletteScore("mw", "Mileway")!!
    check(prefix > wordStart) { "exact prefix must outrank a word-boundary match ($prefix vs $wordStart)" }
    check(wordStart > scattered) { "word boundary must outrank a scattered subsequence ($wordStart vs $scattered)" }
    check(paletteScore("mile", "Mileway")!! > paletteScore("mw", "Mileway")!!) { "prefix beats subsequence on the same label" }

    // No match is null, not zero — zero is a legitimate score (the empty query).
    check(paletteScore("zzz", "Mileway") == null) { "non-matching query is null" }
    check(paletteScore("yawelim", "Mileway") == null) { "out-of-order characters do not match" }
    check(paletteScore("mileways", "Mileway") == null) { "query longer than the label does not match" }

    // Empty (and whitespace-only) query matches everything and preserves declared order.
    check(commands.all { paletteScore("", it.label) != null }) { "empty query matches every label" }
    check(paletteFilter("", commands) == commands) { "empty query keeps the declared order" }
    check(paletteFilter("   ", commands) == commands) { "whitespace-only query is an empty query" }

    // The filter is what the UI actually calls, so rank order is checked end-to-end too.
    val ranked = paletteFilter("resume", commands)
    check(ranked.firstOrNull()?.id == "route:resume") { "typing 'resume' must put the résumé first, got ${ranked.firstOrNull()?.id}" }
    check(paletteFilter("zzzz", commands).isEmpty()) { "a nonsense query filters everything out" }
    check(paletteFilter("mileway", commands).firstOrNull()?.id == "project:mileway") { "project rows are reachable by name" }
}
