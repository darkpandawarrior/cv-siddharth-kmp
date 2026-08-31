@file:OptIn(ExperimentalLayoutApi::class)

package com.siddharth.cv.shared.labs

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.siddharth.cv.shared.anthology.grouped
import com.siddharth.cv.shared.data.generated.chess
import com.siddharth.cv.shared.data.projects
import com.siddharth.cv.shared.theme.CvContentMaxWidth
import com.siddharth.cv.shared.theme.CvDarkColors
import com.siddharth.cv.shared.theme.CvGutter
import com.siddharth.cv.shared.theme.LocalReducedMotion
import com.siddharth.cv.shared.theme.MonoMeta
import com.siddharth.cv.shared.theme.SectionEyebrow
import com.siddharth.cv.shared.theme.SectionHeading
import com.siddharth.cv.shared.theme.TagChip
import com.siddharth.cv.shared.theme.cvColor
import com.siddharth.cv.shared.theme.cvColors
import com.siddharth.cv.shared.theme.cvType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The Lab Bench — the UI over the engines in `LabModels.kt`, and the port of
 * cv-siddharth/src/LabBench.tsx.
 *
 * Three structural differences from the React bench, all of them consequences of the engines being
 * pure functions of elapsed seconds rather than `dt`-accumulating simulations:
 *
 * 1. **One ticker for the whole screen.** The React bench gives every lab its own
 *    `requestAnimationFrame` loop (`useCanvasLoop.ts`). Here [rememberElapsedSeconds] is the only
 *    clock; each instrument is `engine(t)` evaluated inside its own draw lambda.
 * 2. **The clock is read in the draw phase, never in composition.** Reading `seconds.value` inside
 *    `Canvas { }` invalidates the draw pass only, so nothing here recomposes at 60fps. The text
 *    readouts — which genuinely have to recompose — go through [rememberCoarseSeconds], which is a
 *    `derivedStateOf` quantiser standing in for the React version's `setState` throttles.
 * 3. **Reduced motion needs no second code path.** The clock is pinned at [LabStillSeconds] and the
 *    loop never starts; the engines were designed so that instant *is* the representative frame.
 *
 * Interaction that the React labs express by mutating live simulation state is expressed here as a
 * shift of a time *epoch* — "re-run the feed from here" — because a closed-form feed cannot
 * retroactively re-bin what already landed. Each site documents the choice.
 */

// ---------------------------------------------------------------------------------------------
// The one clock
// ---------------------------------------------------------------------------------------------

/**
 * Elapsed seconds since the bench opened, ticked once per frame — the single driver for every
 * instrument on the screen.
 *
 * `withInfiniteAnimationFrameNanos` rather than `withFrameNanos`: it is the frame source that
 * respects `InfiniteAnimationPolicy`, so a test clock or a prerender pass can cancel this loop
 * instead of hanging forever on an animation that never ends.
 *
 * Under reduced motion the value is the constant [LabStillSeconds] and the loop is never started at
 * all — no warm-up, no paused animation parked at an arbitrary phase.
 */
@Composable
private fun rememberElapsedSeconds(): State<Float> {
    val reduced = LocalReducedMotion.current
    // Keyed on `reduced` so the very first frame after a toggle already holds the right value;
    // seeding at 0f and correcting inside the effect would flash an empty canvas for one frame.
    val seconds = remember(reduced) { mutableStateOf(if (reduced) LabStillSeconds else 0f) }
    LaunchedEffect(reduced) {
        if (reduced) return@LaunchedEffect
        val start = withInfiniteAnimationFrameNanos { it }
        while (true) {
            withInfiniteAnimationFrameNanos { now -> seconds.value = (now - start) / 1_000_000_000f }
        }
    }
    return seconds
}

/**
 * The clock quantised to [stepSeconds], for values that are read as *text*.
 *
 * A `BasicText` that reads the raw clock recomposes sixty times a second to print the same digits.
 * `derivedStateOf` only notifies its readers when the quantised value actually changes, which is the
 * Compose-native equivalent of the `statsAcc > 400` throttles in `CrashLab.tsx` / `FanoutLab.tsx`.
 */
@Composable
private fun rememberCoarseSeconds(seconds: State<Float>, stepSeconds: Float): State<Float> =
    remember(seconds, stepSeconds) {
        derivedStateOf { (seconds.value / stepSeconds).toInt() * stepSeconds }
    }

// ---------------------------------------------------------------------------------------------
// Shared drawing helpers
// ---------------------------------------------------------------------------------------------

private val LabCanvasHeight = 360.dp

/** `rounded-2xl border border-line bg-void/70` — the frame every instrument draws inside. */
private val LabPanelShape = RoundedCornerShape(16.dp)

/**
 * A project's real accent, read out of the project data rather than re-typed as a hex literal — the
 * fan-out is HireSignal blue and the search tree is Kursi gold because those simulations are *of*
 * those products, and a colour edited in `CvProjectData.kt` should move this canvas with it.
 *
 * An unknown slug degrades to the site's cyan instead of throwing: a lab going the wrong colour is
 * a cosmetic bug, a blank route is not. [labScreenSelfCheck] is what catches the typo.
 */
internal fun labAccent(slug: String): Color =
    projects.firstOrNull { it.slug == slug }?.theme?.let { cvColor(it.accent) } ?: CvDarkColors.accent2

/**
 * Canvas text, measured rather than guessed.
 *
 * [anchorX] 0f left-aligns on [x], 0.5f centres, 1f right-aligns; [anchorY] does the same
 * vertically, so `anchorY = 1f` places text *above* [y] the way a canvas baseline call would.
 * The result is always clamped inside the canvas — on a 320px-wide phone a right-anchored provider
 * label would otherwise leave the bitmap entirely.
 */
private fun DrawScope.drawLabel(
    measurer: TextMeasurer,
    text: String,
    style: TextStyle,
    x: Float,
    y: Float,
    anchorX: Float = 0f,
    anchorY: Float = 0f,
) {
    val layout = measurer.measure(text, style)
    val w = layout.size.width.toFloat()
    val left = (x - w * anchorX).coerceIn(2f, maxOf(2f, size.width - w - 2f))
    drawText(layout, topLeft = Offset(left, y - layout.size.height * anchorY))
}

/** True when [text] fits in [slotWidth] at [style] — the honest alternative to drawing a smear. */
private fun TextMeasurer.fits(text: String, style: TextStyle, slotWidth: Float): Boolean =
    measure(text, style).size.width <= slotWidth

// ---------------------------------------------------------------------------------------------
// The screen
// ---------------------------------------------------------------------------------------------

/**
 * The `/lab` route. Owns the selected instrument and the clock; every experiment below is a leaf
 * that draws `engine(t)` and nothing else.
 */
@Composable
fun LabScreen(modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    val seconds = rememberElapsedSeconds()
    var selectedId by remember { mutableStateOf(cvLabs.first().id) }
    val selected = cvLabs.firstOrNull { it.id == selectedId } ?: cvLabs.first()

    Column(modifier.verticalScroll(rememberScrollState())) {
        Column(
            Modifier
                .align(Alignment.CenterHorizontally)
                .widthIn(max = CvContentMaxWidth)
                .fillMaxWidth()
                // Bottom room for the floating chat launcher, same as the homepage's list padding.
                .padding(start = CvGutter, end = CvGutter, top = 32.dp, bottom = 120.dp),
        ) {
            SectionEyebrow("// the lab bench")
            Spacer(Modifier.height(12.dp))
            SectionHeading("Don't take the numbers on faith")
            Spacer(Modifier.height(10.dp))
            BasicText(
                text = "$labCount instruments across Dice.tech's production case studies and the " +
                    "personal open-source builds — the actual idea behind each headline metric, " +
                    "running live. Flip a switch and watch the number happen. The white-label " +
                    "instrument has its own room on the homepage as the theme engine, where it " +
                    "re-skins a real subtree instead of a mock of one.",
                modifier = Modifier.widthIn(max = 680.dp),
                style = cvType.bodySmall,
            )

            if (reduced) {
                Spacer(Modifier.height(12.dp))
                MonoMeta(
                    "// reduced motion: every instrument is frozen at t = " +
                        "${LabStillSeconds}s, its representative still frame",
                )
            }

            Spacer(Modifier.height(24.dp))

            LabGroup.entries.forEach { group ->
                val members = cvLabs.filter { it.group == group }
                if (members.isEmpty()) return@forEach
                MonoMeta(group.label.uppercase())
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    members.forEach { experiment ->
                        LabTab(
                            experiment = experiment,
                            selected = experiment.id == selected.id,
                            onSelect = { selectedId = experiment.id },
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            Spacer(Modifier.height(8.dp))

            when (selected.id) {
                "recompose" -> RecomposeInstrument(selected, seconds)
                "crashes" -> CrashInstrument(selected, seconds)
                "modules" -> ModuleGraphInstrument(selected)
                "search" -> SearchTreeInstrument(selected, seconds)
                "fanout" -> FanoutInstrument(selected, seconds)
                "gateways" -> GatewayInstrument(selected, seconds)
                "replay" -> ReplayInstrument(selected, seconds)
                "chess-search" -> ChessSearchInstrument(selected, seconds)
                "chess-clock" -> ClockBurnInstrument(selected)
                // Unreachable while [labScreenSelfCheck] passes — it asserts that every id in
                // cvLabs is wired here. Says so out loud rather than rendering an empty panel.
                else -> MonoMeta("// ${selected.id}: no instrument wired for this experiment yet")
            }
        }
    }
}

/** Ids with a drawing below. Kept next to the `when` above so the two can't drift apart silently. */
private val labInstrumentIds =
    setOf(
        "recompose", "crashes", "modules", "search", "fanout",
        "gateways", "replay", "chess-search", "chess-clock",
    )

/**
 * One tab. [selectable] with [Role.RadioButton] rather than a plain clickable: five mutually
 * exclusive instruments are a radio group to a screen reader, and "selected" is the state that
 * matters — the same call the theme-engine swatches make.
 */
@Composable
private fun LabTab(experiment: LabExperiment, selected: Boolean, onSelect: () -> Unit) {
    val colors = cvColors
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(999.dp)
    Row(
        modifier =
            Modifier
                .background(if (selected) colors.accent.copy(alpha = 0.15f) else Color.Transparent, shape)
                .border(1.dp, if (selected) colors.accent else colors.line, shape)
                .selectable(
                    selected = selected,
                    interactionSource = interaction,
                    // indication = null sitewide: the site draws its own focus/hover treatment.
                    indication = null,
                    role = Role.RadioButton,
                    onClick = onSelect,
                )
                .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text = experiment.label,
            style =
                cvType.body.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) colors.accent else colors.muted,
                ),
        )
        Spacer(Modifier.width(8.dp))
        BasicText(
            text = experiment.metric,
            style =
                cvType.metaMono.copy(
                    fontSize = 10.sp,
                    color = if (selected) colors.accent.copy(alpha = 0.8f) else colors.muted,
                ),
        )
    }
}

/**
 * The instrument frame: caption, canvas, control strip.
 *
 * The canvas box carries [LabExperiment.description] as its `contentDescription`, because a Compose
 * Canvas exposes exactly zero text nodes to assistive tech — whatever the description doesn't say
 * about the simulation is not said at all.
 */
@Composable
private fun LabInstrument(
    experiment: LabExperiment,
    canvas: @Composable BoxWithConstraintsScope.() -> Unit,
    controls: @Composable () -> Unit,
) {
    val colors = cvColors
    Column(Modifier.fillMaxWidth()) {
        BasicText(
            text = experiment.caption,
            modifier = Modifier.widthIn(max = 680.dp),
            style = cvType.bodySmall,
        )
        Spacer(Modifier.height(20.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.deepVoid.copy(alpha = 0.70f), LabPanelShape)
                .border(1.dp, colors.line, LabPanelShape),
        ) {
            BoxWithConstraints(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(LabCanvasHeight)
                        .semantics { contentDescription = experiment.description },
                content = canvas,
            )
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                controls()
            }
        }
    }
}

/**
 * The `<input type="checkbox">` substitute. The tick is drawn, not the "✓" glyph — the vendored
 * families cover Latin text only and an unvendored glyph renders as tofu on the wasm canvas.
 */
@Composable
private fun LabToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = cvColors
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier =
            Modifier
                .toggleable(
                    value = checked,
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Checkbox,
                    onValueChange = onChange,
                )
                .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(13.dp)) {
            val corner = CornerRadius(3.dp.toPx())
            drawRoundRect(
                color = if (checked) colors.accent.copy(alpha = 0.18f) else Color.Transparent,
                cornerRadius = corner,
            )
            drawRoundRect(
                color = if (checked) colors.accent else colors.line,
                cornerRadius = corner,
                style = Stroke(width = 1.2.dp.toPx()),
            )
            if (!checked) return@Canvas
            val tick =
                Path().apply {
                    moveTo(size.width * 0.24f, size.height * 0.54f)
                    lineTo(size.width * 0.43f, size.height * 0.74f)
                    lineTo(size.width * 0.78f, size.height * 0.28f)
                }
            drawPath(tick, colors.accent, style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round))
        }
        Spacer(Modifier.width(8.dp))
        BasicText(text = label, style = cvType.metaMono.copy(color = colors.onBackground))
    }
}

/** Waste reads red across the whole bench: discarded repaints, un-triaged noise, tangled edges. */
private val LabWasteRed: Color = cvColor("#ff5c5c")

/** A control-strip readout. Mono, tinted, and never a source of layout surprise. */
@Composable
private fun LabReadout(text: String, tint: Color? = null) {
    BasicText(text = text, style = cvType.metaMono.copy(color = tint ?: cvColors.muted))
}

// ---------------------------------------------------------------------------------------------
// Recomposition
// ---------------------------------------------------------------------------------------------

/** The React lab's ambient `setInterval(…, 1400)` — here it is just another function of `t`. */
private const val RecomposeAmbientSeconds: Float = 1.4f

/**
 * Which scope the ambient tap `n` hits. A stride of 37 over 40 cells is coprime with 40, so the
 * demo visits every recomposition scope in the grid before repeating instead of favouring a corner.
 */
internal fun recomposeAmbientCell(tapIndex: Int): Int = (tapIndex * 37 + 11).mod(RecomposeCells)

/**
 * Rebuild-the-world versus one recomposing scope.
 *
 * Counters diverge from `RecomposeLab.tsx` on purpose: it charges the naive path 40 renders and the
 * stable path 1, which double-counts the one repaint that was actually needed. Here every tap needs
 * exactly one, and rebuild mode *wastes* the other 39 — which is the claim the ~87% migration is
 * allowed to make.
 */
@Composable
private fun RecomposeInstrument(experiment: LabExperiment, seconds: State<Float>) {
    val colors = cvColors
    var stable by remember { mutableStateOf(false) }
    var needed by remember { mutableStateOf(0) }
    var wasted by remember { mutableStateOf(0) }
    var manualCell by remember { mutableStateOf(-1) }
    var manualAt by remember { mutableStateOf(-1f) }

    // Changes once every 1.4s, so this read costs one recomposition per ambient tap — not one per
    // frame. The tally has to be an effect: a counter is history, and history isn't a function of t.
    val ambientIndex by remember(seconds) {
        derivedStateOf { (seconds.value / RecomposeAmbientSeconds).toInt() }
    }
    LaunchedEffect(ambientIndex) {
        needed++
        if (!stable) wasted += RecomposeCells - 1
    }

    LabInstrument(
        experiment = experiment,
        canvas = {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        val pad = 18.dp.toPx()
                        detectTapGestures { at ->
                            val cell =
                                recomposeCellAt(
                                    x = at.x - pad,
                                    y = at.y - pad,
                                    w = size.width.toFloat() - pad * 2f,
                                    h = size.height.toFloat() - pad * 2f,
                                )
                            if (cell < 0) return@detectTapGestures
                            manualCell = cell
                            manualAt = seconds.value
                            needed++
                            if (!stable) wasted += RecomposeCells - 1
                        }
                    },
            ) {
                val pad = 18.dp.toPx()
                val gridW = size.width - pad * 2f
                val gridH = size.height - pad * 2f
                if (gridW <= 0f || gridH <= 0f) return@Canvas
                val cellW = gridW / RecomposeGridW
                val cellH = gridH / RecomposeGridH
                val inset = 3.dp.toPx()
                val corner = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx())

                val t = seconds.value
                val ambientN = (t / RecomposeAmbientSeconds).toInt()
                val ambientAt = ambientN * RecomposeAmbientSeconds
                // Whichever tap is more recent owns the flash. Under a frozen clock a manual tap
                // wins forever and simply stays lit — that is the still frame, not a stuck timer.
                val manualWins = manualCell >= 0 && manualAt >= ambientAt
                val litCell = if (manualWins) manualCell else recomposeAmbientCell(ambientN)
                val age = t - if (manualWins) manualAt else ambientAt
                val flash = (1f - age / RecomposeFlashSeconds).coerceIn(0f, 1f)
                val flashColor = if (stable) colors.accent else LabWasteRed

                for (i in 0 until RecomposeCells) {
                    val col = i % RecomposeGridW
                    val row = i / RecomposeGridW
                    val topLeft = Offset(pad + col * cellW + inset, pad + row * cellH + inset)
                    val cellSize = Size(cellW - inset * 2f, cellH - inset * 2f)
                    drawRoundRect(colors.card, topLeft, cellSize, corner)
                    drawRoundRect(colors.line, topLeft, cellSize, corner, style = Stroke(1.dp.toPx()))
                    // Rebuild-the-world repaints every scope; stable state repaints the one tapped.
                    val lit = flash > 0f && (!stable || i == litCell)
                    if (!lit) continue
                    drawRoundRect(flashColor.copy(alpha = 0.55f * flash), topLeft, cellSize, corner)
                    if (i == litCell) {
                        drawRoundRect(
                            flashColor.copy(alpha = flash),
                            topLeft,
                            cellSize,
                            corner,
                            style = Stroke(1.5.dp.toPx()),
                        )
                    }
                }
            }
        },
        controls = {
            LabToggle("compose + stable UiState", stable) { stable = it }
            LabReadout("wasted repaints: $wasted", LabWasteRed)
            LabReadout("needed repaints: $needed", colors.accent)
            LabReadout(
                "scopes touched per tap: rebuild ${pct1(1f)} · stable ${pct1(1f / RecomposeCells)}",
            )
        },
    )
}

// ---------------------------------------------------------------------------------------------
// Crash triage
// ---------------------------------------------------------------------------------------------

private fun crashBinCenterX(index: Int, width: Float): Float =
    width * ((index + 0.5f) / crashCauses.size)

/**
 * The crash feed, clustered or not.
 *
 * Flipping the toggle shifts the time epoch rather than re-binning in place: a closed-form feed
 * cannot retroactively re-sort traces that already landed, and pretending otherwise would be a lie
 * about what clustering does. It reads as "re-run the feed from here", which is the honest framing.
 *
 * Under reduced motion the epoch deliberately does **not** move — with a frozen clock a shift would
 * simply empty the canvas, whereas holding the instant re-steers the very same traces into bins,
 * which is the comparison the toggle exists to make.
 */
@Composable
private fun CrashInstrument(experiment: LabExperiment, seconds: State<Float>) {
    val colors = cvColors
    val reduced = LocalReducedMotion.current
    val feed = remember { CrashFeed() }
    // 5 bin names + 5 percentages + the pile caption: the default 8-entry cache would thrash.
    val measurer = rememberTextMeasurer(cacheSize = 16)
    var clustered by remember { mutableStateOf(false) }
    var epoch by remember { mutableStateOf(0f) }

    val coarse by rememberCoarseSeconds(seconds, stepSeconds = 0.5f)
    val coarseLocal = (coarse - epoch).coerceAtLeast(0f)
    val landedNow = feed.landedCount(coarseLocal)
    val seenNow = feed.spawnedCount(coarseLocal)

    val labelStyle = cvType.metaMono.copy(color = colors.onBackground.copy(alpha = 0.6f))

    LabInstrument(
        experiment = experiment,
        canvas = {
            Canvas(Modifier.fillMaxSize()) {
                val t = (seconds.value - epoch).coerceAtLeast(0f)
                val floorY = size.height - 46f
                val landed = feed.landedCount(t)
                val spawned = feed.spawnedCount(t)

                // In flight: `landed until spawned` is the window on screen, ~25 traces wide.
                for (i in landed until spawned) {
                    val progress =
                        ((t - feed.spawnSeconds(i)) / CrashFeed.FallSeconds).coerceIn(0f, 1f)
                    val cause = feed.causeOf(i)
                    val x0 = 30f + feed.xFracOf(i) * (size.width - 60f)
                    val x =
                        if (clustered) {
                            // Steering, eased — the React lab accelerates toward the bin, so most
                            // of the lateral move happens late in the fall either way.
                            x0 + (crashBinCenterX(cause, size.width) - x0) * smoothstep(progress)
                        } else {
                            x0
                        }
                    val y = -8f + progress * (floorY + 8f)
                    val color = if (clustered) crashCauses[cause].color else LabWasteRed
                    drawRect(color.copy(alpha = 0.25f), Offset(x - 0.5f, y - 14f), Size(1f, 12f))
                    drawCircle(color, radius = 2.4f, center = Offset(x, y))
                }

                if (!clustered) {
                    val h = min(34f, 6f + landed * 0.16f)
                    val top = floorY - h
                    drawRect(LabWasteRed.copy(alpha = 0.25f), Offset(20f, top), Size(size.width - 40f, h))
                    drawRect(
                        LabWasteRed.copy(alpha = 0.6f),
                        Offset(20f, top),
                        Size(size.width - 40f, h),
                        style = Stroke(1f),
                    )
                    drawLabel(
                        measurer = measurer,
                        text = "crash feed: $landed traces, zero answers",
                        style = labelStyle.copy(color = LabWasteRed),
                        x = 24f,
                        y = top - 6f,
                        anchorY = 1f,
                    )
                    return@Canvas
                }

                val total = maxOf(1, landed)
                val slot = size.width / crashCauses.size
                crashCauses.forEachIndexed { i, cause ->
                    val count = feed.binCount(i, landed)
                    val x = crashBinCenterX(i, size.width)
                    val w = maxOf(8f, slot - 18f)
                    val h = min(96f, 4f + count * 0.55f)
                    val top = floorY - h
                    drawRect(cause.color.copy(alpha = 0.20f), Offset(x - w / 2f, top), Size(w, h))
                    drawRect(
                        cause.color.copy(alpha = 0.67f),
                        Offset(x - w / 2f, top),
                        Size(w, h),
                        style = Stroke(1f),
                    )
                    drawLabel(
                        measurer = measurer,
                        text = "${(count * 100f / total).toInt()}%",
                        style = labelStyle.copy(color = cause.color),
                        x = x,
                        y = top - 6f,
                        anchorX = 0.5f,
                        anchorY = 1f,
                    )
                    // A cause name wider than its slot would smear into its neighbours. The five
                    // names are all in the experiment's description, so dropping them on a narrow
                    // canvas costs nothing a screen reader or the caption doesn't already carry.
                    if (measurer.fits(cause.id, labelStyle, slot - 4f)) {
                        drawLabel(
                            measurer = measurer,
                            text = cause.id,
                            style = labelStyle,
                            x = x,
                            y = size.height - 26f,
                            anchorX = 0.5f,
                        )
                    }
                }
            }
        },
        controls = {
            LabToggle("cluster by root cause", clustered) {
                clustered = it
                if (!reduced) epoch = seconds.value
            }
            LabReadout("$seenNow traces seen · $landedNow triaged")
            if (clustered) {
                LabReadout(
                    "top 2 clusters = ${feed.topTwoSharePercent(landedNow)}% of all crashes",
                    colors.accent,
                )
            }
        },
    )
}

// ---------------------------------------------------------------------------------------------
// Module graph
// ---------------------------------------------------------------------------------------------

/**
 * Mileway's 46 modules as a radial graph — the one instrument with no clock at all, because the
 * React original has none either (`ModuleGraphLab.tsx` is static SVG plus a toggle).
 *
 * ponytail: the original's hover/pin highlighting is dropped. On a canvas it would be pointer-only
 * — invisible to a keyboard, which the SVG version at least handled with focusable groups — and the
 * claim being made here is entirely in the toggle: 0 cross-feature edges versus 78.
 */
@Composable
private fun ModuleGraphInstrument(experiment: LabExperiment) {
    val colors = cvColors
    val cyan = labAccent("mileway")
    // 13 feature labels plus the two captions, all static: cache them all rather than re-shaping.
    val measurer = rememberTextMeasurer(cacheSize = 24)
    var isolate by remember { mutableStateOf(true) }

    // Styles are built here, not in the draw lambda: `cvType` is a @Composable getter, and a
    // TextStyle that is measured *and* drawn has to carry its own colour anyway.
    val nodeStyle = cvType.metaMono.copy(color = cyan.copy(alpha = 0.95f), fontSize = 10.sp)
    val ghostStyle = cvType.metaMono.copy(color = colors.muted, fontSize = 10.sp)
    val rootStyle = cvType.metaMono.copy(color = colors.deepVoid, fontSize = 10.sp)

    LabInstrument(
        experiment = experiment,
        canvas = {
            Canvas(Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height * 0.46f
                val outerR = min(size.width, size.height) * 0.5f * 0.78f
                val featureR = outerR * 0.74f
                val labelGap = 14.dp.toPx()

                fun featureAt(i: Int): Offset {
                    val a = moduleFeatures[i].angle
                    return Offset(cx + cos(a) * featureR, cy + sin(a) * featureR)
                }

                if (!isolate) {
                    // The tangle the composition root replaces: all 78 feature-to-feature pairs.
                    moduleCrossEdges.forEach { (a, b) ->
                        drawLine(cyan.copy(alpha = 0.14f), featureAt(a), featureAt(b), strokeWidth = 1f)
                    }
                }

                repeat(ModuleFeatureCount) { i ->
                    drawLine(cyan.copy(alpha = 0.55f), Offset(cx, cy), featureAt(i), strokeWidth = 1.6f)
                }

                repeat(ModuleOtherCount) { i ->
                    val a = 2f * PI.toFloat() * i / ModuleOtherCount
                    drawCircle(
                        color = cyan.copy(alpha = 0.18f),
                        radius = 2f,
                        center = Offset(cx + cos(a) * outerR, cy + sin(a) * outerR),
                    )
                }
                drawLabel(
                    measurer = measurer,
                    text = "+$ModuleOtherCount shared & composed modules",
                    style = ghostStyle,
                    x = cx,
                    y = cy + outerR + 18f,
                    anchorX = 0.5f,
                )

                moduleFeatures.forEachIndexed { i, feature ->
                    val p = featureAt(i)
                    drawCircle(colors.deepVoid, radius = 7f, center = p)
                    drawCircle(cyan, radius = 7f, center = p, style = Stroke(1.5f))
                    val c = cos(feature.angle)
                    val s = sin(feature.angle)
                    drawLabel(
                        measurer = measurer,
                        text = feature.label,
                        style = if (feature.named) nodeStyle else ghostStyle,
                        x = p.x + c * labelGap,
                        y = p.y + s * labelGap,
                        // Labels flow outward: away from the centre horizontally, and clear of the
                        // node vertically so a spoke never runs through the glyphs.
                        anchorX = if (c > 0.3f) 0f else if (c < -0.3f) 1f else 0.5f,
                        anchorY = if (s < -0.35f) 1f else if (s > 0.35f) 0f else 0.5f,
                    )
                }

                drawCircle(cyan, radius = 16f, center = Offset(cx, cy))
                drawLabel(
                    measurer = measurer,
                    text = ":app",
                    style = rootStyle,
                    x = cx,
                    y = cy,
                    anchorX = 0.5f,
                    anchorY = 0.5f,
                )
            }
        },
        controls = {
            LabToggle("isolate features", isolate) { isolate = it }
            LabReadout(
                text = "cross-feature dependencies: ${if (isolate) 0 else moduleCrossEdges.size}",
                tint = if (isolate) colors.accent else LabWasteRed,
            )
            LabReadout("$ModuleTotal modules · $ModuleFeatureCount features + $ModuleOtherCount shared")
        },
    )
}

// ---------------------------------------------------------------------------------------------
// ISMCTS search tree
// ---------------------------------------------------------------------------------------------

/**
 * Kursi's ISMCTS search, revealed rather than grown.
 *
 * The tree is built once per (tier, run, canvas size) by [buildSearchTreeRun] and the clock only
 * reveals it, so a window resize re-lays-out the same search instead of quietly running a different
 * one — which is also why the result is reproducible enough to assert in `labsSelfCheck`.
 */
@Composable
private fun SearchTreeInstrument(experiment: LabExperiment, seconds: State<Float>) {
    val reduced = LocalReducedMotion.current
    val gold = labAccent("kursi")
    val measurer = rememberTextMeasurer(cacheSize = 8)
    var tierIndex by remember { mutableStateOf(0) }
    var runIndex by remember { mutableStateOf(0) }
    var epoch by remember { mutableStateOf(0f) }

    // A new run is a new tree plus a clock reset. Under reduced motion only the tree changes: the
    // epoch stays at 0 so the frozen instant still lands on a *finished* search, as designed.
    fun rerun(nextTier: Int) {
        tierIndex = nextTier
        runIndex++
        if (!reduced) epoch = seconds.value
    }

    // 120ms, matching the React lab's iteration-counter throttle — fast enough to read as counting.
    val coarse by rememberCoarseSeconds(seconds, stepSeconds = 0.12f)
    val readoutSeconds = (coarse - epoch).coerceAtLeast(0f)

    // A second, nominally-sized run purely so the control strip can read `iterationsAt` and `role`
    // off the engine instead of re-deriving the tier's duration formula by hand. Everything the text
    // needs is geometry-independent; the canvas builds the same search against its own real size.
    val meta = remember(tierIndex, runIndex) { buildSearchTreeRun(tierIndex, runIndex, 640f, 340f) }
    val chosenStyle = cvType.metaMono.copy(color = gold)
    val titleStyle = cvType.metaMono.copy(color = gold.copy(alpha = 0.5f))

    LabInstrument(
        experiment = experiment,
        canvas = {
            val widthPx = constraints.maxWidth.toFloat()
            val heightPx = constraints.maxHeight.toFloat()
            val tree =
                remember(tierIndex, runIndex, widthPx, heightPx) {
                    buildSearchTreeRun(tierIndex, runIndex, widthPx, heightPx)
                }
            Canvas(Modifier.fillMaxSize()) {
                val t = (seconds.value - epoch).coerceAtLeast(0f)
                val revealed = tree.revealedAt(t)
                val finished = tree.isFinished(t)

                for (i in 1 until revealed) {
                    val node = tree.nodes[i]
                    val parent = tree.nodes[node.parent]
                    val recency = i.toFloat() / tree.nodes.size
                    drawLine(
                        color = gold.copy(alpha = 0.16f + 0.5f * recency),
                        start = Offset(parent.x, parent.y),
                        end = Offset(node.x, node.y),
                        strokeWidth = maxOf(0.6f, 2.2f - node.depth * 0.05f),
                    )
                }

                drawCircle(gold, radius = 4f, center = Offset(tree.nodes[0].x, tree.nodes[0].y))

                if (!finished) {
                    // ponytail: the growing edge stands in for the React lab's frontier list, which
                    // the closed-form engine doesn't keep. The newest nodes *are* where it is
                    // expanding, so the read is the same without exposing search internals.
                    for (i in maxOf(1, revealed - 12) until revealed) {
                        drawCircle(
                            gold.copy(alpha = 0.85f),
                            radius = 2f,
                            center = Offset(tree.nodes[i].x, tree.nodes[i].y),
                        )
                    }
                } else {
                    val chain = tree.chain()
                    val line =
                        Path().apply {
                            chain.asReversed().forEachIndexed { i, idx ->
                                val n = tree.nodes[idx]
                                if (i == 0) moveTo(n.x, n.y) else lineTo(n.x, n.y)
                            }
                        }
                    // Two passes: a wide translucent stroke for the glow the canvas API gets from
                    // shadowBlur, then the crisp line on top.
                    drawPath(line, gold.copy(alpha = 0.28f), style = Stroke(width = 7f, cap = StrokeCap.Round))
                    drawPath(line, gold, style = Stroke(width = 2.6f, cap = StrokeCap.Round))
                    val tip = tree.nodes[tree.chosen]
                    drawCircle(gold, radius = 5f, center = Offset(tip.x, tip.y))
                    val onRight = tip.x > size.width / 2f
                    drawLabel(
                        measurer = measurer,
                        text = "${tree.role} — chosen",
                        style = chosenStyle,
                        x = tip.x + if (onRight) -10f else 10f,
                        y = tip.y - 10f,
                        anchorX = if (onRight) 1f else 0f,
                        anchorY = 1f,
                    )
                }

                drawLabel(
                    measurer = measurer,
                    text = "ISMCTS Search Tree",
                    style = titleStyle,
                    x = 14f,
                    y = 12f,
                )
            }
        },
        controls = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LabReadout("difficulty:")
                Spacer(Modifier.width(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    searchTiers.forEachIndexed { i, tier ->
                        TagChip(
                            text = tier.label,
                            selected = i == tierIndex,
                            tint = gold,
                            onClick = { rerun(i) },
                        )
                    }
                }
            }
            TagChip(text = "run search", tint = gold, onClick = { rerun(tierIndex) })
            LabReadout(
                text = "iterations: ${meta.iterationsAt(readoutSeconds)} / ${meta.iterations} · " +
                    "difficulty: ${meta.tierLabel}",
            )
            LabReadout("persona: ${meta.role}", gold)
        },
    )
}

// ---------------------------------------------------------------------------------------------
// Provider fan-out
// ---------------------------------------------------------------------------------------------

/**
 * HireSignal's 62-provider fan-out and its SimHash de-dup.
 *
 * Scans are a function of `t` too: scan *n* is `FanoutScan(seedBase + n)` and runs in its own
 * [FanoutScanPeriod] slot, so the bench cycles forever without any spawn bookkeeping. "run scan"
 * moves the epoch *and* bumps the seed base, which is what makes it work under a frozen clock as
 * well — the still frame gets a genuinely different scan rather than nothing at all.
 */
@Composable
private fun FanoutInstrument(experiment: LabExperiment, seconds: State<Float>) {
    val colors = cvColors
    val reduced = LocalReducedMotion.current
    val blue = labAccent("hiresignal")
    val measurer = rememberTextMeasurer(cacheSize = 8)
    var dedup by remember { mutableStateOf(true) }
    var seedBase by remember { mutableStateOf(0) }
    var epoch by remember { mutableStateOf(0f) }

    val scanIndex by remember(seconds) {
        derivedStateOf { ((seconds.value - epoch) / FanoutScanPeriod).toInt().coerceAtLeast(0) }
    }
    // Built once per scan, in composition: generating 60-odd pulses and sorting them inside the
    // draw lambda would allocate that list sixty times a second for no new information.
    val scan = remember(seedBase, scanIndex) { FanoutScan(seedBase + scanIndex) }

    val coarse by rememberCoarseSeconds(seconds, stepSeconds = 0.4f)
    val coarseLocal = (coarse - epoch - scanIndex * FanoutScanPeriod).coerceAtLeast(0f)
    val landedNow = scan.landedAt(coarseLocal)
    val uniqueNow = scan.uniqueAt(coarseLocal)
    val labelStyle = cvType.metaMono.copy(color = colors.onBackground.copy(alpha = 0.75f))
    val zoneStyle = cvType.metaMono.copy(color = blue.copy(alpha = 0.7f))

    LabInstrument(
        experiment = experiment,
        canvas = {
            Canvas(Modifier.fillMaxSize()) {
                // One frame after a period boundary `scanIndex` can still be the previous scan's;
                // that draws a completed scan for a frame, which is invisible.
                val t = (seconds.value - epoch - scanIndex * FanoutScanPeriod).coerceAtLeast(0f)
                val cx = size.width / 2f
                val cy = size.height * 0.33f
                val collTop = size.height - 78f
                val collBottom = size.height - 24f
                val collLeft = 20f
                val collRight = size.width - 20f
                val rx = size.width * 0.42f
                val ry = min(size.height * 0.26f, min(cy - 14f, collTop - cy - 24f))

                fun ringPoint(i: Int): Offset {
                    val a = (i.toFloat() / FanoutProviders) * 2f * PI.toFloat() - PI.toFloat() / 2f
                    return Offset(cx + cos(a) * rx, cy + sin(a) * ry)
                }

                fun targetOf(pulse: FanoutPulse): Offset =
                    Offset(
                        collLeft + pulse.tx * (collRight - collLeft),
                        collTop + pulse.ty * (collBottom - collTop),
                    )

                // The collection zone.
                drawRect(
                    blue.copy(alpha = 0.05f),
                    Offset(collLeft, collTop),
                    Size(collRight - collLeft, collBottom - collTop),
                )
                drawRect(
                    blue.copy(alpha = 0.18f),
                    Offset(collLeft, collTop),
                    Size(collRight - collLeft, collBottom - collTop),
                    style = Stroke(1f),
                )
                drawLabel(
                    measurer = measurer,
                    text = "collected listings",
                    style = zoneStyle,
                    x = collLeft + 6f,
                    y = collTop - 6f,
                    anchorY = 1f,
                )

                // The 62-provider ring, three of them named integrations.
                repeat(FanoutProviders) { i ->
                    val p = ringPoint(i)
                    val namedIdx = fanoutNamedIndexes.indexOf(i)
                    if (namedIdx == -1) {
                        drawCircle(colors.muted.copy(alpha = 0.4f), radius = 2.4f, center = p)
                        return@repeat
                    }
                    drawCircle(blue, radius = 4.5f, center = p)
                    drawCircle(colors.onBackground.copy(alpha = 0.7f), radius = 4.5f, center = p, style = Stroke(1f))
                    val left = p.x < cx
                    drawLabel(
                        measurer = measurer,
                        text = fanoutNamed[namedIdx],
                        style = labelStyle,
                        x = p.x + if (left) -7f else 7f,
                        y = p.y + if (p.y < cy) -6f else 6f,
                        anchorX = if (left) 1f else 0f,
                        anchorY = if (p.y < cy) 1f else 0f,
                    )
                }

                // The query point, breathing on a 1.6s cycle (frozen with the clock).
                val breath = sin(t / 1.6f * 2f * PI.toFloat()) * 1.2f
                drawCircle(blue.copy(alpha = 0.35f), radius = 9f + breath, center = Offset(cx, cy))
                drawCircle(blue, radius = 4f + breath, center = Offset(cx, cy))
                drawLabel(
                    measurer = measurer,
                    text = "query",
                    style = labelStyle,
                    x = cx,
                    y = cy - 12f,
                    anchorX = 0.5f,
                    anchorY = 1f,
                )

                scan.pulses.forEach { pulse ->
                    val target = targetOf(pulse)
                    if (t < pulse.waitSeconds) return@forEach
                    if (t < pulse.landsAt) {
                        // In flight: quadratic Bézier bowing through the query point.
                        val p = smoothstep((t - pulse.waitSeconds) / pulse.flightSeconds)
                        val from = ringPoint(pulse.provider)
                        val mt = 1f - p
                        val pos =
                            Offset(
                                mt * mt * from.x + 2f * mt * p * cx + p * p * target.x,
                                mt * mt * from.y + 2f * mt * p * cy + p * p * target.y,
                            )
                        drawCircle(blue.copy(alpha = 0.75f), radius = 2.2f, center = pos)
                        return@forEach
                    }
                    val age = t - pulse.landsAt
                    if (dedup && !pulse.first) {
                        // SimHash matched a listing already collected: a ring flash where it
                        // merged, and no second dot. With de-dup off it piles up like any other.
                        val ring = (age / 0.5f)
                        if (ring <= 1f) {
                            drawCircle(
                                color = blue.copy(alpha = 1f - ring),
                                radius = 4f + ring * 14f,
                                center = target,
                                style = Stroke(1.5f),
                            )
                        }
                        return@forEach
                    }
                    drawCircle(blue.copy(alpha = min(1f, age / 0.15f)), radius = 3.4f, center = target)
                }
            }
        },
        controls = {
            TagChip(
                text = "run scan",
                tint = blue,
                onClick = {
                    seedBase += 101
                    if (!reduced) epoch = seconds.value
                },
            )
            LabToggle("SimHash de-dup", dedup) { dedup = it }
            LabReadout(
                if (dedup) {
                    "$FanoutProviders providers queried · $landedNow listings → $uniqueNow unique"
                } else {
                    "$FanoutProviders providers queried · $landedNow listings, 0 de-duped"
                },
            )
            LabReadout("0 LLM tokens spent", colors.accent)
        },
    )
}


// ---------------------------------------------------------------------------------------------
// Payment gateways
// ---------------------------------------------------------------------------------------------

/**
 * PaymentsLab's gateway catalog behind one contract.
 *
 * Toggling the contract shifts the time epoch rather than re-routing calls already in flight, for
 * the same reason the crash feed does: a closed-form feed cannot retroactively re-decide what
 * already landed, and pretending otherwise would misrepresent what an abstraction does. It reads as
 * "re-run checkout from here".
 *
 * ponytail: the React lab bins arrivals into four category columns (native-SDK, hosted-webview,
 * mobile-money, catalog-only) sized by each category's real share. Those four counts live in
 * `data/projectStats.ts`, which the Kotlin generator does not emit, so this draws one shelf of every
 * cataloged gateway instead. The lost texture is real and it is named here rather than faked with
 * four plausible numbers.
 */
@Composable
private fun GatewayInstrument(experiment: LabExperiment, seconds: State<Float>) {
    val colors = cvColors
    val reduced = LocalReducedMotion.current
    val violet = labAccent("paymentslab")
    val feed = remember { GatewayFeed() }
    val measurer = rememberTextMeasurer(cacheSize = 8)
    var routed by remember { mutableStateOf(false) }
    var epoch by remember { mutableStateOf(0f) }

    val coarse by rememberCoarseSeconds(seconds, stepSeconds = 0.5f)
    val coarseLocal = (coarse - epoch).coerceAtLeast(0f)
    val routedNow = feed.landedCount(coarseLocal)
    val blockedNow = feed.blockedCount(coarseLocal)

    val labelStyle = cvType.metaMono.copy(color = colors.onBackground.copy(alpha = 0.6f))

    LabInstrument(
        experiment = experiment,
        canvas = {
            Canvas(Modifier.fillMaxSize()) {
                val t = (seconds.value - epoch).coerceAtLeast(0f)
                val cx = size.width / 2f
                val shelfY = size.height - 34f
                val barrierY = size.height * 0.42f
                val hubY = size.height * 0.22f
                val landed = feed.landedCount(t)
                val blocked = feed.blockedCount(t)
                val spawned = feed.spawnedCount(t)

                drawLabel(measurer, "checkout", labelStyle, cx, 8f, anchorX = 0.5f)

                // Where gateway `g` sits on the shelf. One tick per cataloged gateway.
                fun shelfX(gateway: Int): Float =
                    26f + (gateway + 0.5f) / gatewayCount * (size.width - 52f)

                if (!routed) {
                    for (i in blocked until spawned) {
                        val progress =
                            ((t - feed.spawnSeconds(i)) / GatewayFeed.BarrierSeconds).coerceIn(0f, 1f)
                        val x = cx + (feed.xFracOf(i) - 0.5f) * 24f
                        val y = -8f + progress * (barrierY + 8f)
                        drawRect(LabWasteRed.copy(alpha = 0.25f), Offset(x - 0.5f, y - 14f), Size(1f, 12f))
                        drawCircle(LabWasteRed, radius = 2.4f, center = Offset(x, y))
                    }
                    val h = min(34f, 6f + blocked * 0.16f)
                    drawLine(
                        LabWasteRed.copy(alpha = 0.55f),
                        Offset(16f, barrierY),
                        Offset(size.width - 16f, barrierY),
                        strokeWidth = 1f,
                    )
                    drawRect(LabWasteRed.copy(alpha = 0.25f), Offset(20f, barrierY), Size(size.width - 40f, h))
                    drawRect(
                        LabWasteRed.copy(alpha = 0.6f),
                        Offset(20f, barrierY),
                        Size(size.width - 40f, h),
                        style = Stroke(1f),
                    )
                    drawLabel(
                        measurer = measurer,
                        text = "every gateway needs its own SDK integration first",
                        style = labelStyle.copy(color = LabWasteRed),
                        x = cx,
                        y = barrierY + h + 8f,
                        anchorX = 0.5f,
                    )
                    return@Canvas
                }

                // The shelf: one tick per gateway, the recently used ones still lit.
                repeat(gatewayCount) { g ->
                    val x = shelfX(g)
                    drawLine(
                        color = violet.copy(alpha = 0.22f),
                        start = Offset(x, shelfY),
                        end = Offset(x, shelfY + 8f),
                        strokeWidth = 1.4f,
                    )
                }
                // ponytail: "which gateway was last hit" is not closed-form, so the last 40 arrivals
                // are replayed instead. That window is exactly what a fade would have shown anyway.
                for (i in maxOf(0, landed - GatewayFeed.GlowWindow) until landed) {
                    val age = t - (feed.spawnSeconds(i) + GatewayFeed.FallSeconds)
                    val glow = (1f - age / GatewayFeed.GlowSeconds).coerceIn(0f, 1f)
                    if (glow <= 0f) continue
                    val x = shelfX(feed.gatewayOf(i))
                    drawLine(
                        color = violet.copy(alpha = glow),
                        start = Offset(x, shelfY - 4f),
                        end = Offset(x, shelfY + 10f),
                        strokeWidth = 2.2f,
                    )
                }

                for (i in landed until spawned) {
                    val progress =
                        ((t - feed.spawnSeconds(i)) / GatewayFeed.FallSeconds).coerceIn(0f, 1f)
                    val x0 = cx + (feed.xFracOf(i) - 0.5f) * 24f
                    // Nothing steers above the contract node: that is the point of drawing it.
                    val hub = GatewayFeed.HubFraction
                    val steer = ((progress - hub) / (1f - hub)).coerceIn(0f, 1f)
                    val x = x0 + (shelfX(feed.gatewayOf(i)) - x0) * smoothstep(steer)
                    val y = -8f + progress * (shelfY + 8f)
                    drawRect(violet.copy(alpha = 0.27f), Offset(x - 0.5f, y - 14f), Size(1f, 12f))
                    drawCircle(violet, radius = 2.4f, center = Offset(x, y))
                }

                val hubW = 112f
                drawRoundRect(
                    color = violet.copy(alpha = 0.18f),
                    topLeft = Offset(cx - hubW / 2f, hubY - 12f),
                    size = Size(hubW, 24f),
                    cornerRadius = CornerRadius(6f),
                )
                drawRoundRect(
                    color = violet,
                    topLeft = Offset(cx - hubW / 2f, hubY - 12f),
                    size = Size(hubW, 24f),
                    cornerRadius = CornerRadius(6f),
                    style = Stroke(1f),
                )
                drawLabel(
                    measurer = measurer,
                    text = "PaymentGateway",
                    style = labelStyle.copy(color = violet),
                    x = cx,
                    y = hubY,
                    anchorX = 0.5f,
                    anchorY = 0.5f,
                )
                drawLabel(
                    measurer = measurer,
                    text = "$gatewayCount cataloged gateways",
                    style = labelStyle,
                    x = cx,
                    y = shelfY + 14f,
                    anchorX = 0.5f,
                )
            }
        },
        controls = {
            LabToggle("route through the PaymentGateway contract", routed) {
                routed = it
                if (!reduced) epoch = seconds.value
            }
            if (routed) {
                LabReadout(
                    "$routedNow calls routed · $gatewayCount gateways reachable · " +
                        "0 gateway-specific lines touched",
                    colors.accent,
                )
            } else {
                LabReadout("$blockedNow calls blocked · one bespoke integration owed per gateway", LabWasteRed)
            }
        },
    )
}

// ---------------------------------------------------------------------------------------------
// Deterministic replay
// ---------------------------------------------------------------------------------------------

/**
 * Deadlock's determinism gate.
 *
 * This is the one instrument whose state genuinely is not a function of `t` — whether the tape has
 * been perturbed is a decision, not a phase — and that is fine, because the *simulation* still is:
 * both paths are recomputed from the tape every draw, and only the playhead reads the clock. Which
 * is also why the toggle needs no epoch shift: unlike the feeds, this really can re-run history,
 * because history here is a pure function of a 160-frame tape.
 */
@Composable
private fun ReplayInstrument(experiment: LabExperiment, seconds: State<Float>) {
    val colors = cvColors
    val red = labAccent("deadlock")
    val log = remember { ReplayLog() }
    val measurer = rememberTextMeasurer(cacheSize = 8)
    var perturbed by remember { mutableStateOf(false) }

    // `cvType` is a @Composable getter, so the style is built here rather than in the draw lambda.
    val markStyle = cvType.metaMono.copy(color = red.copy(alpha = 0.75f))

    // Drift is geometry-dependent, so the readout measures the same nominal canvas the self-check
    // does rather than whatever this viewport happens to be — otherwise the number moves on resize.
    val drift =
        remember(perturbed) {
            if (!perturbed) {
                0f
            } else {
                log.driftFrom(
                    log.replay(log.frames, ReplayLog.NominalWidth, ReplayLog.NominalHeight),
                    log.replay(log.perturbed(), ReplayLog.NominalWidth, ReplayLog.NominalHeight),
                )
            }
        }

    LabInstrument(
        experiment = experiment,
        canvas = {
            Canvas(Modifier.fillMaxSize()) {
                val clean = log.replay(log.frames, size.width, size.height)
                val head = log.playheadAt(seconds.value)

                fun pathOf(points: Pair<FloatArray, FloatArray>, from: Int): Path =
                    Path().apply {
                        for (i in from until ReplayLog.PathLength) {
                            if (i == from) moveTo(points.first[i], points.second[i])
                            else lineTo(points.first[i], points.second[i])
                        }
                    }

                // Two passes for the glow the canvas API gets from shadowBlur, as the tree does.
                val cleanPath = pathOf(clean, 0)
                drawPath(cleanPath, colors.accent.copy(alpha = 0.22f), style = Stroke(6f, cap = StrokeCap.Round))
                drawPath(cleanPath, colors.accent, style = Stroke(2f, cap = StrokeCap.Round))

                if (perturbed) {
                    val edited = log.replay(log.perturbed(), size.width, size.height)
                    val editedPath = pathOf(edited, ReplayLog.DivergeAt)
                    drawPath(editedPath, red.copy(alpha = 0.22f), style = Stroke(6f, cap = StrokeCap.Round))
                    drawPath(editedPath, red, style = Stroke(2f, cap = StrokeCap.Round))

                    val at = Offset(clean.first[ReplayLog.PerturbIndex], clean.second[ReplayLog.PerturbIndex])
                    drawCircle(red, radius = 4f, center = at, style = Stroke(1.5f))
                    drawLabel(
                        measurer = measurer,
                        text = "frame ${ReplayLog.PerturbIndex} perturbed",
                        style = markStyle,
                        x = at.x + 8f,
                        y = at.y - 8f,
                        anchorY = 1f,
                    )
                    if (head >= ReplayLog.DivergeAt) {
                        drawCircle(red, radius = 3.5f, center = Offset(edited.first[head], edited.second[head]))
                    }
                }

                drawCircle(
                    color = colors.onBackground,
                    radius = 3.5f,
                    center = Offset(clean.first[head], clean.second[head]),
                )
            }
        },
        controls = {
            TagChip(
                text = "perturb frame #${ReplayLog.PerturbIndex}",
                selected = perturbed,
                tint = red,
                onClick = { perturbed = true },
            )
            TagChip(text = "reset", selected = !perturbed, onClick = { perturbed = false })
            LabReadout(
                text =
                    if (perturbed) {
                        "drift: ${driftText(drift)} · gate: BLOCKED, the change is rejected"
                    } else {
                        "drift: ${driftText(drift)} · gate: PASS, the replays are identical"
                    },
                tint = if (perturbed) red else colors.accent,
            )
        },
    )
}

/**
 * Six places when the gate passes, three when it does not.
 *
 * The extra precision is the point: a gate that claims zero tolerance has to show enough digits that
 * "0.000000" is a statement rather than a rounding. `toString()` on a Float would print `0.0` and
 * `0.1483...`, neither of which reads as a measurement.
 */
private fun driftText(drift: Float): String {
    val places = if (drift == 0f) 6 else 3
    var scale = 1f
    repeat(places) { scale *= 10f }
    val scaled = (drift * scale).toLong()
    val whole = scaled / scale.toLong()
    val fraction = (scaled % scale.toLong()).toString().padStart(places, '0')
    return "$whole.$fraction"
}

// ---------------------------------------------------------------------------------------------
// Chess: alpha-beta search
// ---------------------------------------------------------------------------------------------

/**
 * A real alpha-beta search over a position out of his own games, drawn edge by edge.
 *
 * Nothing here is a simulation and nothing is live progress: [chessSearch] is synchronous and hands
 * back the whole tree, so the growth is a replay of a search that already finished — which is what
 * lets it be a function of elapsed seconds like everything else on the bench. The React version
 * needs a Web Worker for the same search because it also runs a wall-clock budget; this one is
 * fixed-depth and costs a few hundred microseconds to twelve thousand nodes, so it runs inline.
 */
@Composable
private fun ChessSearchInstrument(experiment: LabExperiment, seconds: State<Float>) {
    val colors = cvColors
    val reduced = LocalReducedMotion.current
    val measurer = rememberTextMeasurer(cacheSize = 8)
    var presetIndex by remember { mutableStateOf(chessSearchPresets.lastIndex) }
    var positionIndex by remember { mutableStateOf(0) }
    var epoch by remember { mutableStateOf(0f) }

    val preset = chessSearchPresets[presetIndex]
    val entry = chessLabPositions[positionIndex.mod(chessLabPositions.size)]
    val result =
        remember(presetIndex, positionIndex) {
            chessSearch(entry.fen, preset.depth, preset.noise, CHESS_SEARCH_SEED + positionIndex)
        }

    fun rerun(nextPreset: Int, nextPosition: Int) {
        presetIndex = nextPreset
        positionIndex = nextPosition
        // Under reduced motion the epoch stays put, so the frozen instant still lands on a finished
        // search rather than on an empty canvas — the same rule the ISMCTS tree follows.
        if (!reduced) epoch = seconds.value
    }

    val titleStyle = cvType.metaMono.copy(color = colors.accent.copy(alpha = 0.55f))
    val noteStyle = cvType.metaMono.copy(color = colors.muted)

    LabInstrument(
        experiment = experiment,
        canvas = {
            val widthPx = constraints.maxWidth.toFloat()
            val heightPx = constraints.maxHeight.toFloat()
            val tree = remember(result, widthPx, heightPx) { layoutChessTree(result, widthPx, heightPx) }
            Canvas(Modifier.fillMaxSize()) {
                val t = (seconds.value - epoch).coerceAtLeast(0f)
                val revealed = chessRevealedAt(result, t)

                for (i in 0 until revealed) {
                    val edge = result.edges[i]
                    val chosen = tree.inChosenLine[edge.to]
                    // A few thousand edges overlap heavily at depth, so everything but the played
                    // line stays translucent: density then reads as where the search spent its
                    // budget rather than as a solid blob.
                    val color =
                        when {
                            chosen && edge.depth == 0 -> colors.accent
                            chosen -> colors.accent.copy(alpha = 0.34f)
                            else -> colors.muted.copy(alpha = 0.18f)
                        }
                    drawLine(
                        color = color,
                        start = Offset(tree.x[edge.from], tree.y[edge.from]),
                        end = Offset(tree.x[edge.to], tree.y[edge.to]),
                        strokeWidth = if (chosen && edge.depth == 0) 2.2f else if (chosen) 1f else 0.6f,
                    )
                }

                drawCircle(colors.accent, radius = 4f, center = Offset(tree.x[0], tree.y[0]))
                drawLabel(measurer, "alpha-beta search tree", titleStyle, 14f, 12f)
                if (revealed < result.edges.size) {
                    drawLabel(measurer, "replaying the finished search", noteStyle, 14f, 30f)
                }
            }
        },
        controls = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LabReadout("depth:")
                Spacer(Modifier.width(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    chessSearchPresets.forEachIndexed { i, option ->
                        TagChip(
                            text = option.label,
                            selected = i == presetIndex,
                            tint = colors.accent,
                            onClick = { rerun(i, positionIndex) },
                        )
                    }
                }
            }
            TagChip(
                text = "next position",
                tint = colors.accent,
                onClick = { rerun(presetIndex, positionIndex + 1) },
            )
            LabReadout(
                "${result.nodes.grouped()} nodes visited · ${result.edges.size.grouped()} edges drawn" +
                    if (result.truncated) " (capped)" else "",
            )
            LabReadout("plays ${result.moveText} · ${preset.note}", colors.accent)
            // Provenance, because the position is his, not a textbook diagram.
            LabReadout(
                "his ${entry.speed} game, ${entry.at}, rated ${entry.myRating} — a ${entry.result}",
            )
        },
    )
}

// ---------------------------------------------------------------------------------------------
// Chess: clock burn
// ---------------------------------------------------------------------------------------------

/**
 * The clock thesis as one chart: mean share of the starting clock still left, by decile of game
 * progress, split by how the game ended.
 *
 * The chess room already tabulates these ten rows. A table answers "what was the gap at 60%"; only
 * the chart answers "when does it open", which is the entire finding — the curves are on top of each
 * other through the opening and come apart in the early middlegame. Two readings of the same corpus,
 * neither redundant.
 *
 * No clock: like the module graph, the React original is static SVG. Selection is chips rather than
 * a drag on the canvas, because a canvas scrubber is pointer-only and a keyboard would lose it.
 */
@Composable
private fun ClockBurnInstrument(experiment: LabExperiment) {
    val colors = cvColors
    val measurer = rememberTextMeasurer(cacheSize = 16)
    var bucket by remember { mutableStateOf(clockPeakDecile.bucket) }
    val here = clockDeciles[bucket]

    val axisStyle = cvType.metaMono.copy(color = colors.muted, fontSize = 10.sp)
    val winColor = colors.accent
    val lossColor = colors.accent2

    LabInstrument(
        experiment = experiment,
        canvas = {
            Canvas(Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 10.dp)) {
                val padLeft = 44f
                val padRight = 18f
                val padTop = 18f
                val padBottom = 34f
                val plotW = size.width - padLeft - padRight
                val plotH = size.height - padTop - padBottom
                if (plotW <= 0f || plotH <= 0f) return@Canvas

                fun xAt(b: Int): Float = padLeft + ((b + 0.5f) / clockDeciles.size) * plotW
                fun yAt(fraction: Double): Float = padTop + (1f - fraction.toFloat()) * plotH

                listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { g ->
                    val y = padTop + (1f - g) * plotH
                    drawLine(colors.line, Offset(padLeft, y), Offset(size.width - padRight, y), strokeWidth = 1f)
                    drawLabel(
                        measurer = measurer,
                        text = "${(g * 100).toInt()}%",
                        style = axisStyle,
                        x = padLeft - 8f,
                        y = y,
                        anchorX = 1f,
                        anchorY = 0.5f,
                    )
                }

                // The divergence itself: down the win curve, back along the loss curve.
                val band =
                    Path().apply {
                        clockDeciles.forEachIndexed { i, d ->
                            if (i == 0) moveTo(xAt(d.bucket), yAt(d.win)) else lineTo(xAt(d.bucket), yAt(d.win))
                        }
                        clockDeciles.reversed().forEach { d -> lineTo(xAt(d.bucket), yAt(d.loss)) }
                        close()
                    }
                drawPath(band, lossColor.copy(alpha = 0.10f))

                fun curve(pick: (Int) -> Double): Path =
                    Path().apply {
                        clockDeciles.forEachIndexed { i, d ->
                            val y = yAt(pick(i))
                            if (i == 0) moveTo(xAt(d.bucket), y) else lineTo(xAt(d.bucket), y)
                        }
                    }
                drawPath(
                    path = curve { clockDeciles[it].loss },
                    color = lossColor,
                    style = Stroke(2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))),
                )
                drawPath(curve { clockDeciles[it].win }, winColor, style = Stroke(2f))

                clockDeciles.forEach { d ->
                    val selected = d.bucket == bucket
                    val r = if (selected) 4f else 2.5f
                    drawCircle(winColor, radius = r, center = Offset(xAt(d.bucket), yAt(d.win)))
                    drawCircle(lossColor, radius = r, center = Offset(xAt(d.bucket), yAt(d.loss)))
                }

                val markerX = xAt(bucket)
                drawLine(
                    color = colors.onBackground.copy(alpha = 0.6f),
                    start = Offset(markerX, padTop),
                    end = Offset(markerX, padTop + plotH),
                    strokeWidth = 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f)),
                )

                drawLabel(measurer, "start of game", axisStyle, padLeft, size.height - 12f)
                drawLabel(
                    measurer = measurer,
                    text = "last move",
                    style = axisStyle,
                    x = size.width - padRight,
                    y = size.height - 12f,
                    anchorX = 1f,
                )
                // The sample size lives on the chart, not only in the prose beside it.
                drawLabel(
                    measurer = measurer,
                    text = "n = ${chess.thesis.sampleSize.grouped()} games with per-move clocks",
                    style = axisStyle,
                    x = padLeft + 6f,
                    y = padTop + plotH - 8f,
                )
            }
        },
        controls = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LabReadout("game progress:")
                Spacer(Modifier.width(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    clockDeciles.forEach { d ->
                        TagChip(
                            text = clockBandLabel(d.bucket),
                            selected = d.bucket == bucket,
                            tint = colors.accent,
                            onClick = { bucket = d.bucket },
                        )
                    }
                }
            }
            LabReadout("won — ${pctOf(here.win)} of the clock left", winColor)
            LabReadout("lost (dashed) — ${pctOf(here.loss)} left", lossColor)
            LabReadout("gap ${oneDecimal(here.gap * 100)} pts")
            LabReadout(
                "${pctOf(chess.thesis.decidedOnClock)} of decided games ended on a clock, " +
                    "not on a board",
            )
        },
    )
}

// ---------------------------------------------------------------------------------------------
// Self-check
// ---------------------------------------------------------------------------------------------

/**
 * ponytail: one runnable check instead of a test module, wired into `selfCheck()` in jvmMain's
 * Prerender.kt beside [labsSelfCheck]. Covers the three things in this file that are logic rather
 * than paint: the ambient-tap sequence, the data-driven accents, and the two "the frozen frame
 * lands somewhere worth looking at" claims that the drawing code relies on.
 */
internal fun labScreenSelfCheck() {
    // Every experiment on the bench must have a drawing wired in LabScreen's `when`.
    cvLabs.forEach { check(it.id in labInstrumentIds) { "${it.id}: no instrument wired" } }
    check(labInstrumentIds.size == cvLabs.size) { "a wired instrument no longer has an experiment" }

    // Ambient taps must visit every recomposition scope, not orbit a few of them.
    val visited = (0 until RecomposeCells).map { recomposeAmbientCell(it) }.toSet()
    check(visited.size == RecomposeCells) { "ambient taps only reach ${visited.size} of $RecomposeCells scopes" }
    check(visited.all { it in 0 until RecomposeCells }) { "an ambient tap fell outside the grid" }

    // The frozen clock has to catch a lit scope, or reduced motion shows a dead grid.
    val stillTap = (LabStillSeconds / RecomposeAmbientSeconds).toInt()
    val stillAge = LabStillSeconds - stillTap * RecomposeAmbientSeconds
    check(stillAge < RecomposeFlashSeconds) { "the still frame misses the flash by ${stillAge}s" }

    // Accents come from project data. A typo in a slug must be loud, not a quietly wrong hue.
    check(labAccent("kursi") == cvColor("#E8C874")) { "Kursi's gold moved or the slug is wrong" }
    check(labAccent("hiresignal") == cvColor("#3B82F6")) { "HireSignal's blue moved or the slug is wrong" }
    check(labAccent("mileway") == cvColor("#5ee6ff")) { "Mileway's cyan moved or the slug is wrong" }
    check(labAccent("no-such-project") == CvDarkColors.accent2) { "unknown slugs must fall back" }

    // The fan-out's still frame is claimed to be mid-flight through its third scan.
    val stillScan = (LabStillSeconds / FanoutScanPeriod).toInt()
    check(stillScan == 2) { "the still frame is in scan ${stillScan + 1}, not the third" }
    val stillInScan = LabStillSeconds - stillScan * FanoutScanPeriod
    val scan = FanoutScan(stillScan)
    check(stillInScan in 0.2f..scan.spanSeconds) {
        "the still frame is not mid-flight: ${stillInScan}s into a ${scan.spanSeconds}s scan"
    }
    check(scan.landedAt(stillInScan) > 0) { "the still frame should already show collected listings" }
    check(scan.landedAt(stillInScan) < scan.pulses.size) { "the still frame should still have listings in flight" }

    // The gateway shelf must not run off either end of the canvas at the extremes of the catalog.
    check(gatewayCount > 1) { "a one-tick shelf is not a fan-out" }

    // Drift formatting: the zero case is the claim, so it has to print as a measurement.
    check(driftText(0f) == "0.000000") { "a passing gate prints six places, was ${driftText(0f)}" }
    val blockedDrift = 0.148318f
    check(driftText(blockedDrift) == "0.148") { "a blocked gate prints three places, was ${driftText(blockedDrift)}" }
    val wholeUnits = 12.5f
    check(driftText(wholeUnits) == "12.500") { "whole units survive the split, was ${driftText(wholeUnits)}" }

    // The clock chart's marker defaults to the widest gap, which the caption calls the finding.
    check(clockPeakDecile.bucket in clockDeciles.indices) { "the peak decile is off the chart" }
}
