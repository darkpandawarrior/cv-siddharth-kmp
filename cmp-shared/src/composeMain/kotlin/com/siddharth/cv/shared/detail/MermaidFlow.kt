package com.siddharth.cv.shared.detail

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.siddharth.cv.shared.theme.CvMotion
import com.siddharth.cv.shared.theme.LocalReducedMotion
import com.siddharth.cv.shared.theme.cvColors
import com.siddharth.cv.shared.theme.cvType
import kotlin.math.abs

/**
 * Draws a Mermaid flowchart natively instead of printing its source.
 *
 * The layout is Sugiyama's first three phases: [FlowGraph.ranks] assigns each node a layer by
 * longest path from a root and then orders each layer with a barycenter sweep, and this file spreads
 * the ordered layers along the cross axis and routes edges between them. Phase four proper —
 * coordinate assignment that pulls each node towards its neighbours' median — is not here; ranks are
 * centred as blocks against the widest one, which for 3-7 nodes a rank reads the same and needs no
 * priority/median pass. Edge labels then get their own pass: they all land on their band's midline
 * by default and the chip behind each is opaque, so overlapping ones are packed into rows across the
 * rank gap (see [packLabelRows]).
 *
 * **This composable owns its own card.** The call site should hand it the raw string and nothing
 * else — no surrounding background, padding or `horizontalScroll`, because it needs to measure the
 * available width itself (a parent `horizontalScroll` would hand it an infinite constraint) and it
 * scrolls internally when a wide diagram can't be scaled down honestly.
 *
 * If [source] isn't in the subset [parseMermaidFlow] handles, this degrades to the raw mono source
 * card that shipped in v1. A diagram we can't draw truthfully is worse than one we don't draw.
 */
@Composable
fun MermaidFlow(source: String, modifier: Modifier = Modifier) {
    val graph = remember(source) { parseMermaidFlow(source) }
    if (graph == null) {
        RawMermaidSource(source, modifier)
        return
    }

    val colors = cvColors
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()

    // Both styles are measured *and* drawn, so the colour has to live in the style rather than in
    // the drawText call — otherwise the cached TextLayoutResult and the pixels disagree.
    val nodeStyle =
        cvType.metaMono.copy(color = colors.onBackground, textAlign = TextAlign.Center)
    val edgeStyle = cvType.metaMono.copy(color = colors.muted, textAlign = TextAlign.Center)

    val layout =
        remember(graph, nodeStyle, edgeStyle, density) {
            buildFlowLayout(graph, measurer, nodeStyle, edgeStyle, density)
        }

    val reduced = LocalReducedMotion.current
    var armed by remember(source) { mutableStateOf(false) }
    LaunchedEffect(source) { armed = true }
    // Not `by`: read inside graphicsLayer so the fade runs in the draw phase instead of
    // recomposing the whole diagram sixty times a second.
    val appear =
        animateFloatAsState(
            targetValue = if (armed || reduced) 1f else 0f,
            animationSpec = tween(if (reduced) 0 else 520, easing = CvMotion.EaseOutExpo),
            label = "mermaid-appear",
        )

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(12.dp))
            .border(1.dp, colors.line, RoundedCornerShape(12.dp))
            .padding(DIAGRAM_PAD),
    ) {
        // `maxWidth` is already inside the 18dp padding: BoxWithConstraints reports the constraints
        // its *content* receives, not the ones the card was measured with. Subtracting the pad again
        // charged every oversized diagram a further 18dp it had, so the widest ones shrank ~5% more
        // than they needed to and hit the scroll floor sooner.
        val availablePx = with(density) { maxWidth.toPx() }
        // Shrink to fit, never grow past 1:1, and stop shrinking before the 11sp labels stop being
        // readable — below the floor we scroll instead, which is honest about the diagram's size.
        val fit = if (layout.width <= 0f) 1f else availablePx / layout.width
        val scale = fit.coerceIn(MIN_SCALE, 1f)
        val w = with(density) { (layout.width * scale).toDp() }
        val h = with(density) { (layout.height * scale).toDp() }

        Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            Canvas(
                Modifier
                    .size(w, h)
                    .graphicsLayer { alpha = appear.value }
                    // A canvas has no text nodes, so the graph describes itself in prose. Without
                    // this the whole "How it's built" section is silent to a screen reader.
                    .semantics { contentDescription = graph.describe() },
            ) {
                scale(scale, scale, pivot = Offset.Zero) { drawFlow(layout, colors.accent, colors.card, colors.line, colors.surface) }
            }
        }
    }
}

/** The v1 rendering, kept as the fallback: exactly what a reader would see on GitHub. */
@Composable
private fun RawMermaidSource(source: String, modifier: Modifier = Modifier) {
    val colors = cvColors
    Box(
        modifier
            .fillMaxWidth()
            .background(colors.card, RoundedCornerShape(12.dp))
            .padding(16.dp)
            .horizontalScroll(rememberScrollState()),
    ) {
        BasicText(text = source, style = cvType.mono, softWrap = false)
    }
}

// -------------------------------------------------------------------------------------------
// Layout
// -------------------------------------------------------------------------------------------

private val DIAGRAM_PAD = 18.dp

/** Room inside a node box around its label. */
private val NODE_PAD_X = 14.dp
private val NODE_PAD_Y = 9.dp

/** So a one-character id like Kursi's `s` doesn't render as a sliver. */
private val NODE_MIN_W = 56.dp

/** Wrap rather than let one long label set the width of the entire diagram. */
private val LABEL_MAX_W = 220.dp

/** Between siblings in the same rank. */
private val WITHIN_GAP = 22.dp

/** Between ranks — grown at build time if an edge label needs more room than this. */
private val RANK_GAP_LR = 68.dp
private val RANK_GAP_TD = 56.dp

/** Cross-axis room reserved for back edges to bow through. */
private val BACK_BOW = 36.dp

private val ARROW_LEN = 9.dp
private val EDGE_WIDTH = 1.5.dp
private val LABEL_CHIP_PAD = 5.dp

/** Between two rows of edge labels in the same rank gap. */
private val LABEL_ROW_GAP = 6.dp

/** Below this the 11sp mono labels stop being readable, so we scroll instead of scaling further. */
private const val MIN_SCALE = 0.62f

private class PlacedNode(
    val shape: NodeShape,
    val text: TextLayoutResult,
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
) {
    val centerX get() = x + w / 2f
    val centerY get() = y + h / 2f
}

private class PlacedEdge(
    val path: Path,
    val tip: Offset,
    /** Unit vector the arrowhead points along; `null` for the headless `---`/`===` connectors. */
    val tipDir: Offset?,
    val dashed: Boolean,
    val thick: Boolean,
    val label: TextLayoutResult?,
    val labelCenter: Offset,
)

private class FlowLayout(
    val nodes: List<PlacedNode>,
    val edges: List<PlacedEdge>,
    val width: Float,
    val height: Float,
)

/**
 * Everything that needs a [Density] or a [TextMeasurer] happens here, once, so the draw pass is
 * pure geometry. Text is measured rather than estimated — a mono font makes a per-character guess
 * tempting, but the labels carry `·`, `—` and `→`, and a guess that's 8% short clips them.
 */
private fun buildFlowLayout(
    graph: FlowGraph,
    measurer: TextMeasurer,
    nodeStyle: TextStyle,
    edgeStyle: TextStyle,
    density: Density,
): FlowLayout {
    fun px(d: Dp) = with(density) { d.toPx() }

    val lr = graph.direction == FlowDirection.LeftRight
    val ranks = graph.ranks()
    val rankOf = HashMap<String, Int>()
    ranks.forEachIndexed { r, group -> group.forEach { rankOf[it.id] = r } }

    val labelCap = Constraints(maxWidth = px(LABEL_MAX_W).toInt())
    val texts =
        graph.nodes.associate { node ->
            node.id to measurer.measure(node.label, nodeStyle, constraints = labelCap)
        }

    // Node boxes.
    val padX = px(NODE_PAD_X)
    val padY = px(NODE_PAD_Y)
    val minW = px(NODE_MIN_W)
    val sizes =
        graph.nodes.associate { node ->
            val t = texts.getValue(node.id)
            val tw = t.size.width.toFloat()
            val th = t.size.height.toFloat()
            // ponytail: a rhombus that truly circumscribes a tw x th box needs 2*tw by 2*th, which
            // looks absurd next to the boxes. 1.45/1.7 clips the very corners of a wide diamond
            // label. No real diagram uses `{}` yet; widen the factors if one ever does.
            val (w, h) =
                if (node.shape == NodeShape.Diamond) {
                    (tw * 1.45f + padX) to (th * 1.7f + padY)
                } else {
                    (tw + padX * 2f) to (th + padY * 2f)
                }
            node.id to Size(maxOf(w, minW), h)
        }

    // Edge labels, measured before placement because they set the gap between ranks. Only forward
    // edges count: a back edge's label rides the bow, out past the last rank, where it costs
    // nothing.
    val edgeTexts =
        graph.edges.associateWith { e ->
            e.label?.let { measurer.measure(it, edgeStyle, constraints = labelCap) }
        }
    val forwardLabelExtent =
        graph.edges
            .filter { (rankOf[it.to] ?: 0) > (rankOf[it.from] ?: 0) }
            .mapNotNull { edgeTexts[it] }
            .maxOfOrNull { if (lr) it.size.width.toFloat() else it.size.height.toFloat() }
            ?: 0f
    val withinGap = px(WITHIN_GAP)

    // Rank axis: band thickness is the deepest node in the band; cross axis: bands are centred
    // against the widest band so the diagram reads as symmetric rather than left-ragged.
    val bandThickness =
        ranks.map { group ->
            group.maxOfOrNull { if (lr) sizes.getValue(it.id).width else sizes.getValue(it.id).height } ?: 0f
        }
    val bandExtent =
        ranks.map { group ->
            val each = group.sumOf { (if (lr) sizes.getValue(it.id).height else sizes.getValue(it.id).width).toDouble() }
            (each + withinGap * (group.size - 1).coerceAtLeast(0)).toFloat()
        }
    val crossSpan = bandExtent.maxOrNull() ?: 0f

    // Cross-axis offsets don't depend on the rank gap, and the label packer below needs them before
    // that gap can be sized — so they're resolved here and the placement loop reads them back.
    val crossOf = HashMap<String, Float>()
    ranks.forEachIndexed { r, group ->
        var alongCross = (crossSpan - bandExtent[r]) / 2f
        group.forEach { node ->
            val s = sizes.getValue(node.id)
            crossOf[node.id] = alongCross
            alongCross += (if (lr) s.height else s.width) + withinGap
        }
    }
    fun crossCentre(id: String): Float {
        val s = sizes.getValue(id)
        return crossOf.getValue(id) + (if (lr) s.height else s.width) / 2f
    }

    // Every label between the same pair of ranks lands on that band's midline, and the chip behind
    // it is opaque — so two labels whose cross-axis footprints overlap don't merely touch, the later
    // one erases the earlier. Kursi's "redact per viewer" is three copies of a 21-character label
    // whose centres sit ~85dp apart behind ~167dp chips: it drew as one smear, with two of the three
    // half-painted over. Pack each band's labels into rows and give every label a `t` that puts it on
    // its own row, still on its own curve. A band that fits in one row writes nothing, keeps t = 0.5,
    // and lays out exactly as it did before.
    val labelT = HashMap<FlowEdge, Float>()
    var labelRows = 1
    graph.edges
        .mapNotNull { e ->
            val text = edgeTexts[e] ?: return@mapNotNull null
            if ((rankOf[e.to] ?: 0) - (rankOf[e.from] ?: 0) != 1) return@mapNotNull null
            Triple(e, rankOf.getValue(e.from), text)
        }
        .groupBy { it.second }
        .forEach { (_, band) ->
            val spans =
                band.map { (e, _, text) ->
                    val extent = (if (lr) text.size.height else text.size.width) + px(LABEL_CHIP_PAD) * 2f
                    val centre = (crossCentre(e.from) + crossCentre(e.to)) / 2f
                    centre - extent / 2f to centre + extent / 2f
                }
            val rows = packLabelRows(spans)
            val used = (rows.maxOrNull() ?: 0) + 1
            if (used > 1) {
                band.forEachIndexed { i, (e, _, _) -> labelT[e] = tForRankFraction((rows[i] + 0.5f) / used) }
                labelRows = maxOf(labelRows, used)
            }
        }

    // One row reduces to the original `widest label + breathing room`; N rows have to stack.
    val rankGap =
        maxOf(
            px(if (lr) RANK_GAP_LR else RANK_GAP_TD),
            labelRows * forwardLabelExtent + (labelRows - 1) * px(LABEL_ROW_GAP) + px(20.dp),
        )
    val rankSpan = bandThickness.sum() + rankGap * (ranks.size - 1).coerceAtLeast(0)

    val placed = HashMap<String, PlacedNode>()
    var alongRank = 0f
    ranks.forEachIndexed { r, group ->
        group.forEach { node ->
            val s = sizes.getValue(node.id)
            val cross = crossOf.getValue(node.id)
            placed[node.id] =
                PlacedNode(
                    shape = node.shape,
                    text = texts.getValue(node.id),
                    x = if (lr) alongRank + (bandThickness[r] - s.width) / 2f else cross,
                    y = if (lr) cross else alongRank + (bandThickness[r] - s.height) / 2f,
                    w = s.width,
                    h = s.height,
                )
        }
        alongRank += bandThickness[r] + rankGap
    }

    val hasBackEdge =
        graph.edges.any { it.from != it.to && (rankOf[it.to] ?: 0) <= (rankOf[it.from] ?: 0) }
    val bow = if (hasBackEdge) px(BACK_BOW) else 0f
    val contentW = (if (lr) rankSpan else crossSpan) + (if (lr) 0f else bow)
    val contentH = (if (lr) crossSpan else rankSpan) + (if (lr) bow else 0f)
    val bowLine = if (lr) contentH - bow / 2f else contentW - bow / 2f

    val edges =
        graph.edges.mapNotNull { e ->
            if (e.from == e.to) return@mapNotNull null // Mermaid allows it; nothing here uses it.
            val a = placed[e.from] ?: return@mapNotNull null
            val b = placed[e.to] ?: return@mapNotNull null
            val forward = (rankOf[e.to] ?: 0) > (rankOf[e.from] ?: 0)

            val p0: Offset
            val c1: Offset
            val c2: Offset
            val p3: Offset
            val dir: Offset
            when {
                forward && lr -> {
                    p0 = Offset(a.x + a.w, a.centerY)
                    p3 = Offset(b.x, b.centerY)
                    val mid = (p0.x + p3.x) / 2f
                    c1 = Offset(mid, p0.y)
                    c2 = Offset(mid, p3.y)
                    dir = Offset(1f, 0f)
                }
                forward -> {
                    p0 = Offset(a.centerX, a.y + a.h)
                    p3 = Offset(b.centerX, b.y)
                    val mid = (p0.y + p3.y) / 2f
                    c1 = Offset(p0.x, mid)
                    c2 = Offset(p3.x, mid)
                    dir = Offset(0f, 1f)
                }
                // Back edge (Kursi's replay loop) — bow out past the far side of the content rather
                // than draw a straight line back through every box in between.
                lr -> {
                    p0 = Offset(a.centerX, a.y + a.h)
                    p3 = Offset(b.centerX, b.y + b.h)
                    c1 = Offset(p0.x, bowLine)
                    c2 = Offset(p3.x, bowLine)
                    dir = Offset(0f, -1f)
                }
                else -> {
                    p0 = Offset(a.x + a.w, a.centerY)
                    p3 = Offset(b.x + b.w, b.centerY)
                    c1 = Offset(bowLine, p0.y)
                    c2 = Offset(bowLine, p3.y)
                    dir = Offset(-1f, 0f)
                }
            }

            val path =
                Path().apply {
                    moveTo(p0.x, p0.y)
                    cubicTo(c1.x, c1.y, c2.x, c2.y, p3.x, p3.y)
                }
            PlacedEdge(
                path = path,
                tip = p3,
                tipDir = if (e.arrow) dir else null,
                dashed = e.dashed,
                thick = e.thick,
                label = edgeTexts[e],
                labelCenter = cubicAt(p0, c1, c2, p3, labelT[e] ?: 0.5f),
            )
        }

    return FlowLayout(
        nodes = graph.nodes.mapNotNull { placed[it.id] },
        edges = edges,
        width = contentW,
        height = contentH,
    )
}

/**
 * First-fit interval colouring: every span gets the lowest row index whose spans it doesn't overlap.
 * Returns one row per input, in input order.
 *
 * Assigning in left-to-right order is what makes this exact rather than a heuristic — greedy
 * left-to-right colouring of an interval graph uses the minimum number of rows — so the rank gap
 * never grows by a row the labels didn't need. Two labels that merely abut (`a.end == b.start`)
 * share a row: the chip padding is already inside the span.
 */
internal fun packLabelRows(spans: List<Pair<Float, Float>>): List<Int> {
    val rows = mutableListOf<MutableList<Pair<Float, Float>>>()
    val out = IntArray(spans.size)
    for (i in spans.indices.sortedBy { spans[it].first }) {
        val span = spans[i]
        var row = rows.indexOfFirst { taken -> taken.none { it.first < span.second && span.first < it.second } }
        if (row < 0) {
            rows += mutableListOf<Pair<Float, Float>>()
            row = rows.lastIndex
        }
        rows[row] += span
        out[i] = row
    }
    return out.toList()
}

/**
 * The `t` at which a forward edge's curve has travelled [frac] of the way along the rank axis.
 *
 * Both control points share the midpoint's rank coordinate, so that axis reduces to
 * `f(t) = 1.5t - 1.5t² + t³` regardless of how far the edge fans across the other axis. `f` is
 * strictly increasing (`f'` has no real roots), so bisection inverts it — and `f(0.5) = 0.5`, which
 * is why a single-row band lands exactly where it always did.
 *
 * ponytail: 16 steps is ~1e-5 of a rank gap, far under a pixel. Closed form exists (it's a depressed
 * cubic); it is not more readable and this runs once per labelled edge at layout time.
 */
private fun tForRankFraction(frac: Float): Float {
    var lo = 0f
    var hi = 1f
    repeat(BISECTION_STEPS) {
        val m = (lo + hi) / 2f
        // 1.5t - 1.5t² + t³, factored so the coefficient appears once.
        val progress = 1.5f * m * (1f - m) + m * m * m
        if (progress < frac) lo = m else hi = m
    }
    return (lo + hi) / 2f
}

/** ~1e-5 of a rank gap, which is far under a pixel at any scale this draws at. */
private const val BISECTION_STEPS = 16

/** de Casteljau at a single `t` — cheaper and clearer than dragging a PathMeasure in for one point. */
private fun cubicAt(p0: Offset, c1: Offset, c2: Offset, p3: Offset, t: Float): Offset {
    val u = 1f - t
    val a = u * u * u
    val b = 3f * u * u * t
    val c = 3f * u * t * t
    val d = t * t * t
    return Offset(
        a * p0.x + b * c1.x + c * c2.x + d * p3.x,
        a * p0.y + b * c1.y + c * c2.y + d * p3.y,
    )
}

// -------------------------------------------------------------------------------------------
// Draw
// -------------------------------------------------------------------------------------------

/**
 * Edges first, then nodes: a box painted over the line it terminates on hides the last pixel of
 * stroke, which is exactly the join Mermaid draws. Labels come last so nothing paints over them.
 */
private fun DrawScope.drawFlow(
    layout: FlowLayout,
    accent: Color,
    fill: Color,
    line: Color,
    ground: Color,
) {
    val stroke = EDGE_WIDTH.toPx()
    val arrow = ARROW_LEN.toPx()
    val dash = PathEffect.dashPathEffect(floatArrayOf(6f * density, 5f * density))

    layout.edges.forEach { e ->
        drawPath(
            path = e.path,
            color = accent,
            style = Stroke(width = if (e.thick) stroke * 1.9f else stroke, pathEffect = if (e.dashed) dash else null),
        )
        e.tipDir?.let { drawArrowHead(e.tip, it, accent, arrow) }
    }

    layout.nodes.forEach { n ->
        when (n.shape) {
            NodeShape.Diamond -> {
                val p = diamondPath(n)
                drawPath(p, fill)
                drawPath(p, line, style = Stroke(width = 1f * density))
            }
            else -> {
                val radius =
                    when (n.shape) {
                        NodeShape.Round, NodeShape.Stadium -> n.h / 2f
                        NodeShape.Subroutine -> 4.dp.toPx()
                        else -> 10.dp.toPx()
                    }
                drawRoundRect(fill, Offset(n.x, n.y), Size(n.w, n.h), CornerRadius(radius))
                drawRoundRect(
                    line,
                    Offset(n.x, n.y),
                    Size(n.w, n.h),
                    CornerRadius(radius),
                    style = Stroke(width = 1f * density),
                )
                if (n.shape == NodeShape.Subroutine) {
                    val inset = 7.dp.toPx()
                    listOf(n.x + inset, n.x + n.w - inset).forEach { x ->
                        drawLine(line, Offset(x, n.y), Offset(x, n.y + n.h), strokeWidth = 1f * density)
                    }
                }
            }
        }
        drawText(
            n.text,
            topLeft = Offset(n.centerX - n.text.size.width / 2f, n.centerY - n.text.size.height / 2f),
        )
    }

    // Edge labels sit on top of their own line, so they need the ground painted back in behind them
    // or the stroke reads straight through the glyphs.
    val chip = LABEL_CHIP_PAD.toPx()
    layout.edges.forEach { e ->
        val t = e.label ?: return@forEach
        val w = t.size.width.toFloat()
        val h = t.size.height.toFloat()
        drawRoundRect(
            ground,
            Offset(e.labelCenter.x - w / 2f - chip, e.labelCenter.y - h / 2f - chip * 0.5f),
            Size(w + chip * 2f, h + chip),
            CornerRadius(4.dp.toPx()),
        )
        drawText(t, topLeft = Offset(e.labelCenter.x - w / 2f, e.labelCenter.y - h / 2f))
    }
}

private fun diamondPath(n: PlacedNode): Path =
    Path().apply {
        moveTo(n.centerX, n.y)
        lineTo(n.x + n.w, n.centerY)
        lineTo(n.centerX, n.y + n.h)
        lineTo(n.x, n.centerY)
        close()
    }

private fun DrawScope.drawArrowHead(tip: Offset, dir: Offset, color: Color, len: Float) {
    val normal = Offset(-dir.y, dir.x)
    val base = tip - dir * len
    val half = len * 0.42f
    drawPath(
        Path().apply {
            moveTo(tip.x, tip.y)
            lineTo(base.x + normal.x * half, base.y + normal.y * half)
            lineTo(base.x - normal.x * half, base.y - normal.y * half)
            close()
        },
        color,
    )
}

// -------------------------------------------------------------------------------------------
// Self-check
// -------------------------------------------------------------------------------------------

/**
 * ponytail: the rest of the layout needs a live TextMeasurer, so only the pure geometry can be
 * checked without a Compose frame. That is also the part that was wrong. Called from
 * `mermaidLayoutSelfCheck()`, so it needs no extra wiring in `Prerender.kt`.
 */
internal fun mermaidLabelSelfCheck() {
    // Kursi's "redact per viewer" to scale: three 160-wide chips whose centres are 85 apart, so each
    // one covers half of its neighbour. The middle label steps out; the outer two never met.
    val redact = listOf(93f to 253f, 178f to 338f, 263f to 423f)
    check(packLabelRows(redact) == listOf(0, 1, 0)) { "overlapping labels must not share a row" }

    // Labels that clear each other keep the single row — this is what leaves 11 of the 13 untouched.
    val clear = listOf(0f to 10f, 20f to 30f, 40f to 50f)
    check(packLabelRows(clear) == listOf(0, 0, 0)) { "no rows bought for free" }
    val abutting = listOf(0f to 10f, 10f to 20f)
    check(packLabelRows(abutting) == listOf(0, 0)) { "abutting spans share a row" }
    // Rows are assigned left to right but handed back in input order.
    val outOfOrder = listOf(40f to 90f, 0f to 45f)
    check(packLabelRows(outOfOrder) == listOf(1, 0)) { "rows come back in input order" }
    check(packLabelRows(emptyList()).isEmpty()) { "no labels, no rows" }

    val tolerance = 0.001f
    val quarter = 0.25f
    val half = 0.5f
    val threeQuarters = 0.75f
    check(abs(tForRankFraction(half) - half) < tolerance) { "a single row still sits at the midpoint" }
    check(tForRankFraction(quarter) < half) { "row 0 of 2 sits above the midpoint" }
    check(tForRankFraction(threeQuarters) > half) { "row 1 of 2 sits below it" }

    // A fanned-out edge: 100 along the rank axis, 20 across it. Row 0 of 2 must land a quarter of the
    // way down the *rank* axis, not a quarter of the way along the arc.
    val start = Offset(0f, 0f)
    val bendOut = Offset(0f, 50f)
    val bendIn = Offset(20f, 50f)
    val finish = Offset(20f, 100f)
    val expectedY = 25f
    val row0 = cubicAt(start, bendOut, bendIn, finish, tForRankFraction(quarter))
    check(abs(row0.y - expectedY) < half) { "row 0 of 2 sits a quarter of the way down the band" }
}
