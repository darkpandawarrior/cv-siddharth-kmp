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

/**
 * Draws a Mermaid flowchart natively instead of printing its source.
 *
 * The layout is Sugiyama's first two phases and nothing more: [FlowGraph.ranks] assigns each node a
 * layer by longest path from a root, and this file spreads each layer along the cross axis and
 * routes edges between them. Phase three — the barycenter sweep that minimises crossings — is
 * deliberately absent; see the ponytail note on [FlowGraph.ranks]. Every one of the twelve real
 * diagrams declares its siblings in the order it wants them drawn, so the sweep would be work with
 * no visible output.
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
        val availablePx = with(density) { maxWidth.toPx() } - with(density) { DIAGRAM_PAD.toPx() }
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
    val rankGap =
        maxOf(px(if (lr) RANK_GAP_LR else RANK_GAP_TD), forwardLabelExtent + px(20.dp))
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
    val rankSpan = bandThickness.sum() + rankGap * (ranks.size - 1).coerceAtLeast(0)

    val placed = HashMap<String, PlacedNode>()
    var alongRank = 0f
    ranks.forEachIndexed { r, group ->
        var alongCross = (crossSpan - bandExtent[r]) / 2f
        group.forEach { node ->
            val s = sizes.getValue(node.id)
            val x: Float
            val y: Float
            if (lr) {
                x = alongRank + (bandThickness[r] - s.width) / 2f
                y = alongCross
                alongCross += s.height + withinGap
            } else {
                x = alongCross
                y = alongRank + (bandThickness[r] - s.height) / 2f
                alongCross += s.width + withinGap
            }
            placed[node.id] = PlacedNode(node.shape, texts.getValue(node.id), x, y, s.width, s.height)
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
                labelCenter = cubicAt(p0, c1, c2, p3, 0.5f),
            )
        }

    return FlowLayout(
        nodes = graph.nodes.mapNotNull { placed[it.id] },
        edges = edges,
        width = contentW,
        height = contentH,
    )
}

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
