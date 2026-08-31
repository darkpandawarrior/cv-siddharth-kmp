@file:OptIn(ExperimentalLayoutApi::class)

package com.siddharth.cv.shared.map

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.siddharth.cv.shared.CvNavState
import com.siddharth.cv.shared.LocalNav
import com.siddharth.cv.shared.Route
import com.siddharth.cv.shared.data.generated.StoryMapEdge
import com.siddharth.cv.shared.data.generated.StoryMapNode
import com.siddharth.cv.shared.data.generated.storyMapEdges
import com.siddharth.cv.shared.data.generated.storyMapNodes
import com.siddharth.cv.shared.home.homeSections
import com.siddharth.cv.shared.routeOrNull
import com.siddharth.cv.shared.theme.CvCard
import com.siddharth.cv.shared.theme.CvColors
import com.siddharth.cv.shared.theme.CvContentMaxWidth
import com.siddharth.cv.shared.theme.CvGutter
import com.siddharth.cv.shared.theme.CvSectionGap
import com.siddharth.cv.shared.theme.GhostButton
import com.siddharth.cv.shared.theme.LocalReducedMotion
import com.siddharth.cv.shared.theme.MonoMeta
import com.siddharth.cv.shared.theme.PrimaryButton
import com.siddharth.cv.shared.theme.Reveal
import com.siddharth.cv.shared.theme.SectionEyebrow
import com.siddharth.cv.shared.theme.SectionHeading
import com.siddharth.cv.shared.theme.TagChip
import com.siddharth.cv.shared.theme.cvColor
import com.siddharth.cv.shared.theme.cvColors
import com.siddharth.cv.shared.theme.cvType
import kotlin.math.sqrt

/**
 * Port of cv-siddharth/src/StoryMap.tsx: the storyboard, every place on the site and the wiring
 * between them.
 *
 * THE FIDELITY LOSS, said plainly. The web version renders this as a three.js constellation on a
 * capable desktop, falling back to its own hand-placed 2D canvas everywhere else. There is no
 * three.js here and nothing pretends there is. What this draws is the 2D graph, and it is drawn
 * from a real Fruchterman-Reingold layout rather than from coordinates typed into the corpus. The
 * README's `/map` row already says a Compose port is a 2D force-directed graph and a fidelity loss;
 * this file is that, and it does not quietly redefine the target it fell short of.
 *
 * WHAT THE PORT GAINS BY LOSING IT. The layout is a pure function of the corpus plus a fixed seed
 * ([layoutStoryMap]), so it settles bit-identically on every target, the reduced-motion still frame
 * IS the settled frame at no extra cost, and every claim about it is asserted on the JVM by
 * [storyMapLayoutSelfCheck] before the page ships. A three.js scene can make none of those three
 * claims about itself.
 *
 * WHAT DRIVES THE MOTION. One float: seconds since mount, read ONLY inside the draw lambda, so the
 * settle and the signal pulses animate without recomposing the page around them. Every position on
 * screen is a function of that float and nothing else, which is the lab bench's rule and the reason
 * reduced motion is one branch rather than a second rendering path: the clock is parked at the end
 * of the settle and no pulse is drawn.
 *
 * WHAT IS NOT A DESTINATION. Two nodes name places this build does not serve. They are drawn as
 * hollow rings with the reason stated, rather than as live dots that go nowhere.
 * [classifyTarget] decides that once, off `routeOrNull`, which is the function that exists to
 * answer exactly this question.
 */
@Composable
fun MapScreen(modifier: Modifier = Modifier) {
    val nav = LocalNav.current
    val uri = LocalUriHandler.current
    var selected by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(top = 32.dp, bottom = 120.dp),
    ) {
        item("header") { StoryboardHeader(nav) }
        item("graph") { Constellation(selected = selected, onSelect = { selected = it }) }
        item("selection") { SelectionPanel(selected, nav, uri, onSelect = { selected = it }) }
        item("chips") { DestinationChips(nav, uri) }
        item("method") { HowItIsDrawn() }
    }
}

/** `max-w-5xl mx-auto px-6`, held to the sitewide measure so the page does not jump on arrival. */
private fun Modifier.pageMeasure(): Modifier =
    this.widthIn(max = CvContentMaxWidth).fillMaxWidth().padding(horizontal = CvGutter)

// ---------------------------------------------------------------------------------------------
// The corpus, resolved once
// ---------------------------------------------------------------------------------------------

/**
 * The settled constellation, computed once for the lifetime of the process rather than per
 * composition. It is a pure function of a corpus that cannot change at runtime, so a `remember`
 * would only be re-deriving the same answer on every navigation back to this page.
 */
private val storyLayout: StoryLayout = layoutStoryMap(storyMapNodes, storyMapEdges)

private val storyDescription: String = describeStoryMap(storyMapNodes, storyMapEdges)

private val nodesById: Map<String, StoryMapNode> = storyMapNodes.associateBy { it.id }

/** Node id to its index in a layout frame. */
private val nodeIndex: Map<String, Int> =
    storyMapNodes.withIndex().associate { (i, node) -> node.id to i }

/** Resolved once so the draw loop never classifies a target sixty times a second. */
private val absentIds: Set<String> =
    storyMapNodes.filter { classifyTarget(it.target) is MapTarget.Absent }.map { it.id }.toSet()

private const val HUB_ID: String = "sid"

// ---------------------------------------------------------------------------------------------
// 1. Header
// ---------------------------------------------------------------------------------------------

@Composable
private fun StoryboardHeader(nav: CvNavState) {
    Reveal {
        Column(Modifier.pageMeasure()) {
            GhostButton(text = "Back to portfolio", onClick = { nav.go(Route.Home) })
            Spacer(Modifier.height(28.dp))
            SectionEyebrow("// the storyboard")
            Spacer(Modifier.height(10.dp))
            BasicText(text = "Everything connects", style = cvType.hero)
            Spacer(Modifier.height(18.dp))
            BasicText(
                text =
                    "The projects share one foundation, the writing is field notes from the work, " +
                        "and the AI assistant has read all of it. Every dot is somewhere on this " +
                        "site and every line is a real dependency. Hover a dot to light its " +
                        "wiring, click it to read what it connects to.",
                modifier = Modifier.widthIn(max = 680.dp),
                style = cvType.body,
            )
            Spacer(Modifier.height(28.dp))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 2. The constellation
// ---------------------------------------------------------------------------------------------

/** How long the settle takes to play back. The frames carry the cooling; this only paces them. */
private const val SETTLE_SECONDS: Float = 1.4f

/** One lap of a signal pulse along a wire. */
private const val PULSE_SECONDS: Float = 2.4f

/** Offsets each wire's pulse so the graph does not blink in unison. */
private const val PULSE_STAGGER: Float = 0.137f

private const val NANOS_PER_SECOND: Float = 1_000_000_000f

private val CanvasMinHeight: Dp = 340.dp

private val CanvasMaxHeight: Dp = 520.dp

private const val CANVAS_ASPECT: Float = 0.58f

/** Extra px around a node that still counts as pointing at it. The React canvas uses the same 12. */
private val HitSlop: Dp = 12.dp

@Composable
private fun Constellation(selected: String?, onSelect: (String?) -> Unit) {
    val reduced = LocalReducedMotion.current
    val measurer = rememberTextMeasurer()
    val colors = cvColors
    val labelStyle = cvType.metaMono.copy(color = colors.onBackground, fontWeight = FontWeight.SemiBold)
    val subStyle = cvType.metaMono.copy(fontSize = SubFontSize)

    // One scratch buffer for the life of the screen: sampleInto writes into it every frame rather
    // than handing back a fresh array sixty times a second.
    val frame = remember { DoubleArray(storyLayout.size * 2) }

    // Seconds since mount, read ONLY inside the draw lambda. Bumping it invalidates this Canvas's
    // drawing and nothing else, so the page around it never recomposes for a frame of animation.
    val clock = remember { mutableFloatStateOf(if (reduced) SETTLE_SECONDS else 0f) }
    // Hover lives in the same draw-phase-only budget. Selection does not: the panel below reads it,
    // so it is ordinary state and a change there is an ordinary recomposition.
    val hover = remember { mutableStateOf<String?>(null) }

    LaunchedEffect(reduced) {
        if (reduced) {
            // Parked at the end of the settle: the still frame IS the settled frame, so there is
            // no second rendering path that has to be kept honest against the animated one.
            clock.floatValue = SETTLE_SECONDS
            return@LaunchedEffect
        }
        val start = withInfiniteAnimationFrameNanos { it }
        while (true) {
            withInfiniteAnimationFrameNanos { now ->
                clock.floatValue = (now - start) / NANOS_PER_SECOND
            }
        }
    }

    Reveal {
        BoxWithConstraints(Modifier.pageMeasure()) {
            val height = (maxWidth * CANVAS_ASPECT).coerceIn(CanvasMinHeight, CanvasMaxHeight)
            CvCard(modifier = Modifier.fillMaxWidth(), glowOnHover = false) {
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(height)
                        .semantics { contentDescription = storyDescription }
                        .pointerInput(Unit) { trackConstellation(hover, onSelect) },
                ) {
                    val t = clock.floatValue
                    storyLayout.sampleInto((t / SETTLE_SECONDS).toDouble(), frame)
                    val paint =
                        ConstellationPaint(
                            frame = frame,
                            geometry = canvasFrame(size.width, size.height),
                            focus = hover.value ?: selected,
                            colors = colors,
                            measurer = measurer,
                            labelStyle = labelStyle,
                            subStyle = subStyle,
                            seconds = if (reduced) null else t,
                        )
                    drawWires(paint)
                    drawStars(paint)
                }
                Spacer(Modifier.height(14.dp))
                CanvasLegend()
            }
        }
    }
}

private val LegendDot: Dp = 10.dp

private val RingStroke: Dp = 1.5.dp

@Composable
private fun CanvasLegend() {
    val colors = cvColors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(LegendDot).drawBehind {
                val stroke = RingStroke.toPx()
                drawCircle(
                    color = colors.muted,
                    radius = size.minDimension / 2f - stroke / 2f,
                    style = Stroke(width = stroke),
                )
            },
        )
        Spacer(Modifier.width(8.dp))
        MonoMeta("a hollow ring is a place this build does not serve")
    }
}

// ---------------------------------------------------------------------------------------------
// The drawing
// ---------------------------------------------------------------------------------------------

/**
 * Where the unit square lands on the canvas.
 *
 * THE INSETS ARE A DRAWING FACT, NOT A LAYOUT ONE, which is why they live here rather than in
 * [layoutStoryMap]. A label is drawn above its node and a subtitle below, and both are a fixed
 * number of dp tall no matter how big the canvas is, so reserving space for them as a FRACTION of
 * the frame works at one size and clips at another: at the 340dp floor, the topmost node's label
 * would have started 13dp above the top edge. The layout stays a pure function of the graph; the
 * band the type needs is measured in dp, once, here, and hit testing reads the same mapping so a
 * dot is never half a label off from where it can be clicked.
 */
private class CanvasFrame(
    private val width: Float,
    private val height: Float,
    private val insetX: Float,
    private val insetTop: Float,
    private val insetBottom: Float,
) {
    fun x(u: Double): Float = insetX + (u * (width - insetX * 2f)).toFloat()

    fun y(v: Double): Float = insetTop + (v * (height - insetTop - insetBottom)).toFloat()
}

private val CanvasInsetX: Dp = 10.dp

/** Room for a label above the highest node. */
private val CanvasInsetTop: Dp = 34.dp

/** Room for a subtitle below the lowest. */
private val CanvasInsetBottom: Dp = 28.dp

private fun Density.canvasFrame(width: Float, height: Float): CanvasFrame =
    CanvasFrame(
        width = width,
        height = height,
        insetX = CanvasInsetX.toPx(),
        insetTop = CanvasInsetTop.toPx(),
        insetBottom = CanvasInsetBottom.toPx(),
    )

/**
 * Everything one frame of the figure needs, in one object, so [drawWires] and [drawStars] take one
 * parameter instead of eight.
 *
 * [seconds] is null under reduced motion, and that is the only thing the draw code has to know
 * about it: no pulses, and the frame it was handed is already the settled one.
 */
private class ConstellationPaint(
    val frame: DoubleArray,
    val geometry: CanvasFrame,
    val focus: String?,
    val colors: CvColors,
    val measurer: TextMeasurer,
    val labelStyle: TextStyle,
    val subStyle: TextStyle,
    val seconds: Float?,
)

private const val COLD_EDGE_ALPHA: Float = 0.14f

private const val HOT_EDGE_ALPHA: Float = 0.8f

/** A wire not attached to whatever is focused. Present, but out of the way. */
private const val DIM_EDGE_ALPHA: Float = 0.06f

private const val COLD_PULSE_ALPHA: Float = 0.35f

private const val GLOW_ALPHA: Float = 0.18f

private const val HOT_GLOW_ALPHA: Float = 0.34f

private const val GLOW_SCALE: Float = 2f

private const val HOT_GLOW_SCALE: Float = 2.6f

private const val EDGE_BOW_FACTOR: Float = 0.12f

private const val EDGE_WIDTH: Float = 1f

private const val HOT_EDGE_WIDTH: Float = 1.7f

private const val PULSE_RADIUS: Float = 1.4f

private const val HOT_PULSE_RADIUS: Float = 2.4f

private const val CORE_RADIUS_PX: Float = 4f

private const val HOT_CORE_RADIUS_PX: Float = 5.5f

private const val RING_WIDTH_PX: Float = 1.6f

private const val LABEL_ALPHA: Float = 0.72f

/** The subtitle sits in the node's own colour, a shade back from the dot itself. */
private const val SUB_ALPHA: Float = 0.8f

private val LabelGap: Dp = 6.dp

private val SubGap: Dp = 12.dp

private val SubFontSize = 9.5.sp

private const val COLD_WIRE_PASS: Int = 0

private const val HOT_WIRE_PASS: Int = 1

/** Node x in px. The layout is unit-square; the canvas stretches it, exactly as the React one does. */
private fun nodeX(p: ConstellationPaint, i: Int): Float = p.geometry.x(storyLayout.xOf(p.frame, i))

private fun nodeY(p: ConstellationPaint, i: Int): Float = p.geometry.y(storyLayout.yOf(p.frame, i))

/**
 * The wires, cold first and hot last, so a highlighted dependency is never painted under a dim one.
 *
 * Two passes over twenty edges rather than a sort: sorting would allocate a list per frame for an
 * ordering that is one boolean deep.
 *
 * The bow is the React canvas's own: the control point is the midpoint pushed along the segment's
 * perpendicular by [EDGE_BOW_FACTOR] of its length, which is what stops the ten edges leaving the hub
 * from collapsing into a starburst of straight lines.
 */
private fun DrawScope.drawWires(p: ConstellationPaint) {
    val hasFocus = p.focus != null
    for (pass in COLD_WIRE_PASS..HOT_WIRE_PASS) {
        storyMapEdges.forEachIndexed { ordinal, edge ->
            val hot = hasFocus && (edge.from == p.focus || edge.to == p.focus)
            if (hot != (pass == HOT_WIRE_PASS)) return@forEachIndexed
            val a = nodeIndex[edge.from] ?: return@forEachIndexed
            val b = nodeIndex[edge.to] ?: return@forEachIndexed
            drawWire(p, edge, ordinal, a, b, hot, hasFocus)
        }
    }
}

private fun DrawScope.drawWire(
    p: ConstellationPaint,
    edge: StoryMapEdge,
    ordinal: Int,
    a: Int,
    b: Int,
    hot: Boolean,
    hasFocus: Boolean,
) {
    val ax = nodeX(p, a)
    val ay = nodeY(p, a)
    val bx = nodeX(p, b)
    val by = nodeY(p, b)
    val mx = (ax + bx) / 2f + (ay - by) * EDGE_BOW_FACTOR
    val my = (ay + by) / 2f + (bx - ax) * EDGE_BOW_FACTOR

    // A hot wire takes the colour of the node it runs to, the way the React canvas does. The
    // lookup can only miss on a corpus the emitter would already have rejected; if it ever does,
    // the wire falls back to the cold tint rather than handing a node id to a hex parser.
    val hotTint = if (hot) nodesById[edge.to]?.color?.let { cvColor(it) } else null
    val tint = hotTint ?: p.colors.accent2
    val alpha = when {
        hot -> HOT_EDGE_ALPHA
        hasFocus -> DIM_EDGE_ALPHA
        else -> COLD_EDGE_ALPHA
    }
    val path =
        Path().apply {
            moveTo(ax, ay)
            quadraticTo(mx, my, bx, by)
        }
    drawPath(
        path = path,
        color = tint.copy(alpha = alpha),
        style = Stroke(width = (if (hot) HOT_EDGE_WIDTH else EDGE_WIDTH) * density),
    )

    // The signal travelling the wire. The quadratic Bezier is evaluated directly rather than
    // measured off the Path: a PathMeasure would be a per-frame allocation for a point that three
    // multiplies give.
    val seconds = p.seconds ?: return
    val k = ((seconds / PULSE_SECONDS) + ordinal * PULSE_STAGGER).mod(1f)
    val q = 1f - k
    val sx = q * q * ax + 2f * q * k * mx + k * k * bx
    val sy = q * q * ay + 2f * q * k * my + k * k * by
    drawCircle(
        color = tint.copy(alpha = if (hot) 1f else COLD_PULSE_ALPHA),
        radius = (if (hot) HOT_PULSE_RADIUS else PULSE_RADIUS) * density,
        center = Offset(sx, sy),
    )
}

/**
 * The stars: a soft halo, the core, the label, and the subtitle for the hub and whatever is focused.
 *
 * A node this build cannot serve is drawn as a RING rather than a disc. It stays in the graph
 * because the wiring it carries is true either way, and it stops looking like a destination because
 * going there is the one thing you cannot do from here.
 *
 * `StoryMapNode.r` is a prominence, not a hitbox: the React canvas draws every core at the same few
 * pixels and spends `r` on the halo and the label offset. This does the same, so the hub reads as
 * the hub without becoming a blob.
 */
private fun DrawScope.drawStars(p: ConstellationPaint) {
    storyMapNodes.forEachIndexed { i, node ->
        val hot = node.id == p.focus
        val x = nodeX(p, i)
        val y = nodeY(p, i)
        val tint = cvColor(node.color)
        val prominence = node.r.toFloat() * density
        val reach = prominence * (if (hot) HOT_GLOW_SCALE else GLOW_SCALE)
        drawCircle(
            brush =
                Brush.radialGradient(
                    colors =
                        listOf(
                            tint.copy(alpha = if (hot) HOT_GLOW_ALPHA else GLOW_ALPHA),
                            Color.Transparent,
                        ),
                    center = Offset(x, y),
                    radius = reach,
                ),
            radius = reach,
            center = Offset(x, y),
        )
        val core = (if (hot) HOT_CORE_RADIUS_PX else CORE_RADIUS_PX) * density
        if (node.id in absentIds) {
            drawCircle(tint, radius = core, center = Offset(x, y), style = Stroke(RING_WIDTH_PX * density))
        } else {
            drawCircle(tint, radius = core, center = Offset(x, y))
        }

        val label = p.measurer.measure(node.label, p.labelStyle)
        val labelColor = if (hot) tint else p.colors.onBackground.copy(alpha = LABEL_ALPHA)
        drawClamped(label, labelColor, x, y - prominence - LabelGap.toPx() - label.size.height)

        val sub = node.sub
        if (sub != null && (hot || node.id == HUB_ID)) {
            val measured = p.measurer.measure(plainText(sub), p.subStyle)
            drawClamped(measured, tint.copy(alpha = SUB_ALPHA), x, y + prominence + SubGap.toPx())
        }
    }
}

/**
 * Draws [text] centred on [centerX], pulled back inside the canvas if that would clip it.
 *
 * Without the clamp the widest labels leave the card on a narrow window: the layout's padding is a
 * fraction of the frame, and a label's width is not.
 */
private fun DrawScope.drawClamped(text: TextLayoutResult, color: Color, centerX: Float, top: Float) {
    val w = text.size.width.toFloat()
    val left = (centerX - w / 2f).coerceIn(0f, maxOf(0f, size.width - w))
    drawText(text, color = color, topLeft = Offset(left, top))
}

// ---------------------------------------------------------------------------------------------
// Pointer
// ---------------------------------------------------------------------------------------------

/**
 * Hover, and click-to-select, in one raw-event loop.
 *
 * HIT TESTING READS THE SETTLED FRAME, not the animating one. A node you can only hit while it is
 * still flying past is not an affordance, and for the second and a bit the settle lasts the two
 * disagree. After that they are the same array.
 */
private suspend fun PointerInputScope.trackConstellation(
    hover: MutableState<String?>,
    onSelect: (String?) -> Unit,
) {
    val slop = HitSlop.toPx()
    val reach = density
    awaitPointerEventScope {
        // The pointer area is this Canvas, so its size is the canvas size and the mapping is the
        // one the draw phase uses. Rebuilt per event because a window resize changes it.
        val geometryOf = { canvasFrame(size.width.toFloat(), size.height.toFloat()) }
        while (true) {
            val event = awaitPointerEvent()
            val at = event.changes.lastOrNull()?.position
            when (event.type) {
                PointerEventType.Enter, PointerEventType.Move ->
                    hover.value = at?.let { hitTest(geometryOf(), it.x, it.y, slop, reach) }

                // Clicking empty sky clears the selection, which is the way back to "nothing
                // chosen" without a button whose only job is undoing a click.
                PointerEventType.Release ->
                    onSelect(at?.let { hitTest(geometryOf(), it.x, it.y, slop, reach) })

                PointerEventType.Exit -> hover.value = null
                else -> Unit
            }
        }
    }
}

/** The id under (x, y), or null. Nearest wins, so overlapping halos cannot make a node unclickable. */
private fun hitTest(geometry: CanvasFrame, x: Float, y: Float, slop: Float, density: Float): String? {
    var best: String? = null
    var bestDistance = Float.MAX_VALUE
    val settled = storyLayout.settled
    storyMapNodes.forEachIndexed { i, node ->
        val dx = geometry.x(storyLayout.xOf(settled, i)) - x
        val dy = geometry.y(storyLayout.yOf(settled, i)) - y
        val d = sqrt(dx * dx + dy * dy)
        if (d <= node.r.toFloat() * density + slop && d < bestDistance) {
            best = node.id
            bestDistance = d
        }
    }
    return best
}

// ---------------------------------------------------------------------------------------------
// 3. What the selected node wires into
// ---------------------------------------------------------------------------------------------

@Composable
private fun SelectionPanel(
    selected: String?,
    nav: CvNavState,
    uri: UriHandler,
    onSelect: (String?) -> Unit,
) {
    val node = selected?.let { nodesById[it] }
    Column(Modifier.pageMeasure().padding(top = 20.dp)) {
        if (node == null) {
            MonoMeta("click a dot to read what it wires into")
            return@Column
        }
        val neighbours = neighboursOf(node.id).mapNotNull { nodesById[it] }
        CvCard(modifier = Modifier.fillMaxWidth(), glowOnHover = false) {
            SectionEyebrow("// ${node.id}")
            Spacer(Modifier.height(8.dp))
            BasicText(text = node.label, style = cvType.cardTitle)
            val sub = node.sub
            if (sub != null) {
                Spacer(Modifier.height(4.dp))
                MonoMeta(plainText(sub))
            }
            Spacer(Modifier.height(16.dp))
            BasicText(
                text =
                    if (neighbours.isEmpty()) {
                        "Nothing wires into this yet."
                    } else {
                        "Wires into ${neighbours.size}: ${neighbours.joinToString(", ") { it.label }}."
                    },
                style = cvType.bodySmall,
            )
            Spacer(Modifier.height(16.dp))
            val target = classifyTarget(node.target)
            if (target is MapTarget.Absent) {
                MonoMeta(target.why)
            } else {
                PrimaryButton(text = "Go to ${node.label}", onClick = { travel(target, nav, uri) })
            }
            if (neighbours.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    neighbours.forEach { n ->
                        TagChip(text = n.label, tint = cvColor(n.color), onClick = { onSelect(n.id) })
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 4. The same destinations as real buttons
// ---------------------------------------------------------------------------------------------

/**
 * The keyboard and touch path, kept for the reason the React version keeps it: a canvas is one
 * opaque element to a screen reader and to a tab key, so the figure above must not be the only way
 * to reach anything on it. The hub is left out, because it is the page you are already on.
 */
@Composable
private fun DestinationChips(nav: CvNavState, uri: UriHandler) {
    val colors = cvColors
    Column(Modifier.pageMeasure().padding(top = CvSectionGap)) {
        SectionEyebrow("// every destination")
        Spacer(Modifier.height(10.dp))
        SectionHeading("The same places, as buttons")
        Spacer(Modifier.height(20.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            storyMapNodes.filter { it.id != HUB_ID }.forEach { node ->
                val target = classifyTarget(node.target)
                if (target is MapTarget.Absent) {
                    TagChip(text = node.label, tint = colors.muted)
                } else {
                    TagChip(
                        text = node.label,
                        tint = cvColor(node.color),
                        onClick = { travel(target, nav, uri) },
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        // The two rings, named in words rather than left to the legend's colour cue alone.
        storyMapNodes.forEach { node ->
            val target = classifyTarget(node.target)
            if (target is MapTarget.Absent) {
                MonoMeta("${node.label}: ${target.why}")
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 5. How the figure is made
// ---------------------------------------------------------------------------------------------

@Composable
private fun HowItIsDrawn() {
    Column(Modifier.pageMeasure().padding(top = CvSectionGap)) {
        SectionEyebrow("// the layout")
        Spacer(Modifier.height(10.dp))
        SectionHeading("Where the dots come from")
        Spacer(Modifier.height(20.dp))
        BasicText(
            text =
                "The web version of this page draws the constellation in three.js on a capable " +
                    "desktop. This one does not, and does not pretend to. It is a " +
                    "Fruchterman-Reingold layout on a Compose canvas, which is a smaller thing " +
                    "than a 3D scene and an honest one.",
            style = cvType.bodySmall,
        )
        Spacer(Modifier.height(12.dp))
        BasicText(
            text =
                "The graph pushes every pair of nodes apart and pulls every wired pair together, " +
                    "cooling the step it is allowed to take until it stops moving. It is seeded " +
                    "from the hand-placed coordinates in the corpus rather than from noise, so the " +
                    "arrangement the site was designed around survives as the starting condition. " +
                    "Nothing in it reads a clock or a random number while it runs, so the same " +
                    "graph settles the same way on every load and on every platform, and the " +
                    "settled state is asserted on the JVM before the page ships.",
            style = cvType.bodySmall,
        )
        Spacer(Modifier.height(12.dp))
        MonoMeta(
            "${storyMapNodes.size} nodes · ${storyMapEdges.size} edges · " +
                "${storyLayout.frames.size} captured frames",
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Targets
// ---------------------------------------------------------------------------------------------

/**
 * What a node's `target` string actually means, decided once for the canvas, the panel and the chip
 * row, exactly as `classifyNodeTarget` in StoryMap.tsx decides it once for its own three consumers.
 */
private sealed interface MapTarget {
    data class Section(val id: String) : MapTarget

    data class Go(val route: Route) : MapTarget

    data class External(val url: String) : MapTarget

    /** No destination on this build, and the reason a reader is owed. */
    data class Absent(val why: String) : MapTarget
}

private const val CHAT_TARGET: String = "chat"

private const val PROJECT_PREFIX: String = "project/"

private const val BLUEPRINT_ID: String = "blueprint"

private const val CHAT_REASON: String =
    "not a place to go: here the assistant is the bubble in the corner of every page"

/**
 * A SECTION ID BEATS A ROUTE OF THE SAME NAME, deliberately, and that ordering is taken from
 * `classifyHash` rather than reinvented: a bare `#work` must scroll the home page, never navigate
 * away from it. The route stays reachable by its own path.
 *
 * The fall-through asks `routeOrNull`, whose whole job is answering "does this build serve that
 * path", so an unported route surfaces as a stated absence instead of a button that silently lands
 * on the home page. That is the failure mode `routeOrNull`'s own doc comment records: three shipped
 * routes were once labelled "web only" because a function that answers Home for everything cannot
 * tell "unported" from "the home page".
 */
private fun classifyTarget(target: String): MapTarget {
    val id = target.removePrefix("#")
    return when {
        target == CHAT_TARGET -> MapTarget.Absent(CHAT_REASON)
        !target.startsWith("#") -> MapTarget.External(target)
        id.startsWith(PROJECT_PREFIX) ->
            MapTarget.Go(Route.ProjectDetail(id.removePrefix(PROJECT_PREFIX)))

        homeSections.any { it.id == id } -> MapTarget.Section(id)
        else -> routeOrNull("/$id")?.let { MapTarget.Go(it) } ?: MapTarget.Absent(absentReason(id))
    }
}

private fun absentReason(id: String): String = when (id) {
    BLUEPRINT_ID ->
        "not on this build: it is a tldraw and three.js canvas, and a DOM widget cannot be laid " +
            "out inside a Compose one"

    else -> "not on this build: nothing here serves /$id"
}

private fun travel(target: MapTarget, nav: CvNavState, uri: UriHandler) {
    when (target) {
        is MapTarget.Section -> nav.goSection(target.id)
        is MapTarget.Go -> nav.go(target.route)
        is MapTarget.External -> uri.openUri(target.url)
        is MapTarget.Absent -> Unit
    }
}

/** Both directions: the corpus's edges are undirected, and the panel reads them as neighbours. */
private fun neighboursOf(id: String): List<String> =
    storyMapEdges.mapNotNull { e ->
        when (id) {
            e.from -> e.to
            e.to -> e.from
            else -> null
        }
    }

/**
 * ponytail: U+2192 is in neither vendored font cut, and Skia paints a missing glyph as a tofu box
 * rather than falling back to a system face, so the one arrow in the corpus is spelled out. Same
 * rule as the star in ShippedScreen and the chevron ExpanderSection draws for itself.
 */
private fun plainText(text: String): String = text.replace("→", "to")

// ---------------------------------------------------------------------------------------------
// The runnable check
// ---------------------------------------------------------------------------------------------

/**
 * ponytail: one runnable check instead of a test module. The layout's own contract is
 * [storyMapLayoutSelfCheck]; this adds the screen's half, which is the classifier — the one place
 * where a wrong answer ships either a button that goes nowhere or a stated absence for a page that
 * exists.
 *
 * Call it from `selfCheck()` in jvmMain's Prerender.kt; nothing calls it otherwise.
 */
internal fun mapScreenSelfCheck() {
    storyMapLayoutSelfCheck(storyMapNodes, storyMapEdges)

    check(classifyTarget("chat") is MapTarget.Absent) { "the chat bubble is not a destination" }
    check(classifyTarget("https://example.com") is MapTarget.External) { "a bare url is external" }
    check(classifyTarget("#project/mileway") == MapTarget.Go(Route.ProjectDetail("mileway"))) {
        "a project hash carries its slug"
    }
    check(classifyTarget("#work") == MapTarget.Section("work")) { "a home section stays on the page" }
    check(classifyTarget("#loopdown") == MapTarget.Go(Route.Loopdown)) { "a ported route navigates" }
    check(classifyTarget("#blueprint") is MapTarget.Absent) { "an unported route says so" }
    check(classifyTarget("#nonsense") is MapTarget.Absent) { "an unknown hash says so" }

    // Every node resolves to something a reader can either act on or be told about, and every
    // neighbour lookup lands on a real node. A corpus edit that breaks either fails here rather
    // than shipping a dot with no behaviour.
    storyMapNodes.forEach { node ->
        val target = classifyTarget(node.target)
        if (target is MapTarget.Absent) {
            check(target.why.isNotBlank()) { "${node.id} is absent with no reason given" }
        }
        neighboursOf(node.id).forEach { other ->
            check(nodesById[other] != null) { "${node.id} wires into an unknown node $other" }
        }
    }
    check(nodesById[HUB_ID] != null) { "the hub node $HUB_ID must exist" }
    check(absentIds.size < storyMapNodes.size) { "every node absent means nothing is a destination" }
    check(plainText("prototype → platform") == "prototype to platform") { "the arrow is spelled out" }
}
