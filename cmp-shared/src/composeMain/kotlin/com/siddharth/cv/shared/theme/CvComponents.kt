package com.siddharth.cv.shared.theme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** `max-w-5xl` — 1024px. The hero text column and the desktop particle mask both anchor to it. */
val CvContentMaxWidth: Dp = 1024.dp

/** `px-6` — the horizontal gutter on every section. */
val CvGutter: Dp = 24.dp

/**
 * `--space-section-y: clamp(3.5rem, …, 6rem)`, taken at its midpoint.
 * ponytail: a fixed value, not a fluid-spacing helper — 16dp of drift at the extremes is not worth
 * a second viewport-reading API alongside [fluidSp].
 */
val CvSectionGap: Dp = 72.dp

// ---------------------------------------------------------------------------------------------
// Ground
// ---------------------------------------------------------------------------------------------

private class Star(
    val xFrac: Float,
    val yFrac: Float,
    val radiusPx: Float,
    val color: Color,
    val phase: Float,
)

/**
 * The page ground: ink, `--gradient-glow-signal` (green, top-anchored), `--gradient-glow-depth`
 * (cyan, bottom-anchored) and the static starfield that the web build falls back to whenever WebGL
 * is unavailable — here it is the only implementation, since the ambient layer must never depend on
 * a 3D scene loading. Draw once in `App()`, behind the nav host.
 */
@Composable
fun AmbientBackground(modifier: Modifier = Modifier) {
    val colors = cvColors
    val time by rememberFrameTicker()
    val stars =
        remember(colors.accent, colors.accent2) {
            val rng = Random(7) // seeded: the sky is identical on every load and every platform
            List(120) {
                val roll = rng.nextFloat()
                val color =
                    when {
                        roll < 0.55f -> Color.White.copy(alpha = 0.35f + rng.nextFloat() * 0.20f)
                        roll < 0.85f -> colors.accent2.copy(alpha = 0.40f + rng.nextFloat() * 0.10f)
                        else -> colors.accent.copy(alpha = 0.45f)
                    }
                Star(
                    xFrac = rng.nextFloat(),
                    yFrac = rng.nextFloat(),
                    radiusPx = 0.5f + rng.nextFloat(),
                    color = color,
                    phase = rng.nextFloat() * (2f * PI.toFloat()),
                )
            }
        }

    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(colors.ink)
        val reach = maxOf(size.width, size.height) * 0.6f
        drawRect(
            brush =
                Brush.radialGradient(
                    colors = listOf(colors.accent.copy(alpha = 0.28f), Color.Transparent),
                    center = Offset(size.width / 2f, 0f),
                    radius = reach,
                ),
        )
        drawRect(
            brush =
                Brush.radialGradient(
                    colors = listOf(colors.accent2.copy(alpha = 0.22f), Color.Transparent),
                    center = Offset(size.width / 2f, size.height),
                    radius = reach,
                ),
        )
        stars.forEach { star ->
            // rememberFrameTicker is pinned at 0 under reduced motion, so twinkle == 1 and the
            // field is simply static. No second code path.
            val twinkle = 0.7f + 0.3f * sin(time * 1.4f + star.phase)
            drawCircle(
                color = star.color.copy(alpha = star.color.alpha * twinkle),
                radius = star.radiusPx,
                center = Offset(star.xFrac * size.width, star.yFrac * size.height),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Section chrome
// ---------------------------------------------------------------------------------------------

/**
 * `// featured work` — the glowing accent LED plus the label. Static by design (the CSS has no
 * animation on it), so there is no reduced-motion branch.
 */
@Composable
fun SectionEyebrow(text: String, modifier: Modifier = Modifier) {
    val colors = cvColors
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(6.dp)
                .glow(colors.accent, radius = 9.dp, alpha = 0.9f)
                .background(colors.accent, RoundedCornerShape(1.5.dp)),
        )
        Spacer(Modifier.width(10.dp))
        BasicText(text = text, style = cvType.eyebrow)
    }
}

@Composable
fun SectionHeading(text: String, modifier: Modifier = Modifier) {
    BasicText(text = text, modifier = modifier, style = cvType.h2)
}

/**
 * `.circuit-line` — the 1px seam between sections, with a 90px accent→accent2 sliver running along
 * it every 8s. The sliver is removed entirely under reduced motion (the CSS uses `display: none`,
 * not just `animation: none`, precisely so it doesn't park mid-track).
 */
@Composable
fun CircuitDivider(modifier: Modifier = Modifier) {
    val colors = cvColors
    val reduced = LocalReducedMotion.current
    val travel by rememberInfiniteFloat(durationMillis = 8000, easing = LinearEasing)
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .drawBehind {
                drawRect(
                    brush =
                        Brush.horizontalGradient(
                            colorStops =
                                arrayOf(
                                    0f to Color.Transparent,
                                    0.18f to colors.line,
                                    0.82f to colors.line,
                                    1f to Color.Transparent,
                                ),
                        ),
                )
                if (reduced) return@drawBehind
                val sliver = 90.dp.toPx()
                // left: -12% -> 112%
                val x = (-0.12f + travel * 1.24f) * size.width
                drawRect(
                    brush =
                        Brush.horizontalGradient(
                            colors = listOf(Color.Transparent, colors.accent, colors.accent2, Color.Transparent),
                            startX = x,
                            endX = x + sliver,
                        ),
                    topLeft = Offset(x, 0f),
                    size = Size(sliver, size.height),
                )
            },
    )
}

// ---------------------------------------------------------------------------------------------
// Surfaces
// ---------------------------------------------------------------------------------------------

private val CardShape = RoundedCornerShape(16.dp)

/**
 * `.card-elevated` — every interactive surface on the site (case studies, projects, experience)
 * lifts with the same accent-tinted "signal" depth, and because the tint reads `cvColors.accent` it
 * follows a project reskin for free.
 */
@Composable
fun CvCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    glowOnHover: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = cvColors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val lifted = glowOnHover && (hovered || pressed || focused)

    val glowColor = if (lifted) colors.accent else Color.Black
    val glowRadius by
        animateDpAsState(
            if (lifted) 44.dp else 16.dp,
            tween(CvMotion.DurBase, easing = CvMotion.EaseOutQuart),
            label = "cardGlowRadius",
        )
    val glowAlpha by
        animateFloatAsState(
            if (lifted) 0.45f else 0.5f,
            tween(CvMotion.DurBase, easing = CvMotion.EaseOutQuart),
            label = "cardGlowAlpha",
        )

    Column(
        modifier =
            modifier
                .glow(glowColor, radius = glowRadius, alpha = glowAlpha, offsetY = 4.dp)
                .then(
                    // One focus treatment sitewide: 2px accent outline at 3px offset (index.css
                    // :focus-visible). Not negotiable, not a place to be lazy.
                    if (focused) {
                        Modifier.drawBehind {
                            val off = 3.dp.toPx()
                            drawRoundRect(
                                color = colors.accent,
                                topLeft = Offset(-off, -off),
                                size = Size(size.width + off * 2f, size.height + off * 2f),
                                cornerRadius = CornerRadius(16.dp.toPx() + off),
                                style = Stroke(2.dp.toPx()),
                            )
                        }
                    } else {
                        Modifier
                    },
                )
                .background(colors.card, CardShape)
                .border(1.dp, if (lifted) colors.accent.copy(alpha = 0.35f) else colors.line, CardShape)
                .then(
                    if (onClick != null) {
                        Modifier.clickable(
                            interactionSource = interaction,
                            indication = null,
                            role = Role.Button,
                            onClick = onClick,
                        )
                    } else {
                        Modifier.hoverable(interaction)
                    },
                )
                .padding(24.dp),
        content = content,
    )
}

/**
 * `.tag-chip` — the skills tag cloud, project tech stacks, badges. [tint] overrides the accent
 * (Kursi's role chips, diagram series colours).
 */
@Composable
fun TagChip(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    tint: Color? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = cvColors
    val accent = tint ?: colors.accent
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val reduced = LocalReducedMotion.current
    val scale by
        animateFloatAsState(
            if (hovered && !reduced) 1.04f else 1f,
            tween(CvMotion.DurFast, easing = CvMotion.EaseOutQuart),
            label = "chipScale",
        )
    val lift by
        animateFloatAsState(
            if (hovered && !reduced) -2f else 0f,
            tween(CvMotion.DurFast, easing = CvMotion.EaseOutQuart),
            label = "chipLift",
        )

    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier =
            modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationY = lift * density
                }
                .background(if (selected) accent.copy(alpha = 0.08f) else Color.Transparent, shape)
                .border(1.dp, if (selected) accent.copy(alpha = 0.55f) else colors.line, shape)
                .then(
                    if (onClick != null) {
                        Modifier.clickable(
                            interactionSource = interaction,
                            indication = null,
                            role = Role.Button,
                            onClick = onClick,
                        )
                    } else {
                        Modifier.hoverable(interaction)
                    },
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        BasicText(
            text = text,
            style = cvType.metaMono.copy(color = if (selected) accent else colors.muted),
        )
    }
}

/** Muted 11px mono — stack lists, receipt labels, the hero stat row. */
@Composable
fun MonoMeta(text: String, modifier: Modifier = Modifier) {
    BasicText(text = text, modifier = modifier, style = cvType.metaMono)
}

// ---------------------------------------------------------------------------------------------
// Buttons
// ---------------------------------------------------------------------------------------------

/**
 * `.btn-primary` — accent fill, ink text, the 3.2s `cta-breathe` idle glow, and a crisp spring
 * press. The breathing loop stops on hover/press exactly as `animation-play-state: paused` does.
 */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = cvColors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val reduced = LocalReducedMotion.current
    val breathe by rememberInfiniteFloat(3200, from = 0.35f, to = 0.5f, easing = CvMotion.EaseOutQuart)

    val glowAlpha = if (hovered || pressed) 0.55f else breathe
    val scale by
        animateFloatAsState(
            when {
                reduced -> 1f
                pressed -> 0.97f
                hovered -> 1.02f
                else -> 1f
            },
            tween(if (pressed) CvMotion.DurFast / 2 else CvMotion.DurFast, easing = CvMotion.EaseSpring),
            label = "btnScale",
        )
    val lift by
        animateFloatAsState(
            when {
                reduced -> 0f
                pressed -> -1f
                hovered -> -2f
                else -> 0f
            },
            tween(CvMotion.DurFast, easing = CvMotion.EaseSpring),
            label = "btnLift",
        )

    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier =
            modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationY = lift * density
                }
                .glow(colors.accent, radius = 26.dp, alpha = glowAlpha, offsetY = 6.dp)
                .background(colors.accent, shape)
                .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = text,
            style = cvType.body.copy(color = colors.ink, fontWeight = FontWeight.SemiBold),
        )
    }
}

/** The secondary CTA — transparent fill, hairline border, body text. */
@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = cvColors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier =
            modifier
                .background(Color.Transparent, shape)
                .border(1.dp, if (hovered) colors.accent.copy(alpha = 0.6f) else colors.line, shape)
                .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = text,
            style = cvType.body.copy(color = if (hovered) colors.accent else colors.onBackground),
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Metrics
// ---------------------------------------------------------------------------------------------

/** Counts 0 → [target] on `--ease-out-expo`; jumps straight to [target] under reduced motion. */
@Composable
fun AnimatedCounter(
    target: Int,
    modifier: Modifier = Modifier,
    prefix: String = "",
    suffix: String = "",
    style: TextStyle? = null,
) {
    val reduced = LocalReducedMotion.current
    var armed by remember(target) { mutableStateOf(false) }
    LaunchedEffect(target) { armed = true }
    val value by
        animateIntAsState(
            targetValue = if (armed || reduced) target else 0,
            animationSpec = tween(if (reduced) 0 else 1200, easing = CvMotion.EaseOutExpo),
            label = "counter",
        )
    BasicText(text = "$prefix$value$suffix", modifier = modifier, style = style ?: cvType.metric)
}

/**
 * The case-study gauge. `stroke-dashoffset` transitioned over 1.1s on `--ease-out-expo` becomes an
 * animated `drawArc` sweep over the same 270° track.
 */
@Composable
fun MetricGauge(progress: Float, modifier: Modifier = Modifier) {
    val colors = cvColors
    val reduced = LocalReducedMotion.current
    var armed by remember(progress) { mutableStateOf(false) }
    LaunchedEffect(progress) { armed = true }
    val swept by
        animateFloatAsState(
            targetValue = if (armed || reduced) progress.coerceIn(0f, 1f) else 0f,
            animationSpec = tween(if (reduced) 0 else 1100, easing = CvMotion.EaseOutExpo),
            label = "gauge",
        )
    Box(modifier.size(88.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 6.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = colors.line,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = colors.accent.copy(alpha = 0.28f),
                startAngle = 135f,
                sweepAngle = 270f * swept,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke * 2.2f, cap = StrokeCap.Round),
            )
            drawArc(
                color = colors.accent,
                startAngle = 135f,
                sweepAngle = 270f * swept,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
    }
}

/**
 * The metric sparkline. The web sets `stroke-dasharray`/`-dashoffset` from `getTotalLength()` and
 * transitions the offset over 1s; the Compose analogue is a [PathMeasure] segment reveal.
 */
@Composable
fun Sparkline(points: List<Float>, modifier: Modifier = Modifier) {
    val colors = cvColors
    val reduced = LocalReducedMotion.current
    var armed by remember(points) { mutableStateOf(false) }
    LaunchedEffect(points) { armed = true }
    val drawn by
        animateFloatAsState(
            targetValue = if (armed || reduced) 1f else 0f,
            animationSpec = tween(if (reduced) 0 else 1000, easing = CvMotion.EaseOutExpo),
            label = "spark",
        )
    val measure = remember { PathMeasure() }
    val source = remember { Path() }
    val segment = remember { Path() }

    Canvas(modifier.fillMaxWidth().height(48.dp)) {
        if (points.size < 2) return@Canvas
        val min = points.min()
        val max = points.max()
        val span = if (abs(max - min) < 0.0001f) 1f else max - min
        val stepX = size.width / (points.size - 1)
        source.reset()
        points.forEachIndexed { i, v ->
            val x = i * stepX
            val y = size.height - ((v - min) / span) * size.height
            if (i == 0) source.moveTo(x, y) else source.lineTo(x, y)
        }
        measure.setPath(source, false)
        segment.reset()
        measure.getSegment(0f, measure.length * drawn, segment, true)
        drawPath(
            path = segment,
            color = colors.accent,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Hero + media
// ---------------------------------------------------------------------------------------------

/**
 * `.hero-shimmer` — `background-clip: text` with a 9s living gradient sweep. Compose does this
 * natively and more cleanly via `TextStyle(brush = …)`.
 *
 * Deliberately carries NO text shadow: index.css:166 excludes the shimmer span from the mobile
 * legibility shadow because a shadow muddies the clipped gradient to olive. Keep that exclusion.
 */
@Composable
fun HeroShimmerText(text: String, modifier: Modifier = Modifier, style: TextStyle? = null) {
    val colors = cvColors
    var widthPx by remember { mutableStateOf(0f) }
    val t by rememberInfiniteFloat(9000, easing = LinearEasing)
    val base = style ?: cvType.hero
    val span = if (widthPx > 0f) widthPx * 3f else 1200f
    val start = -span * t
    val brush =
        Brush.linearGradient(
            colorStops =
                arrayOf(
                    0.20f to colors.accent,
                    0.40f to Color(0xFFA7F3C8),
                    0.60f to colors.accent2,
                    0.80f to colors.accent,
                ),
            start = Offset(start, 0f),
            end = Offset(start + span, 0f),
            tileMode = TileMode.Repeated,
        )
    BasicText(
        text = text,
        modifier = modifier.onSizeChanged { widthPx = it.width.toFloat() },
        style = base.copy(brush = brush),
    )
}

/**
 * The stand-in for project card art. There are no bitmaps in this port (every image on wasmJs is a
 * network round trip), so a project's tile is a deterministic generated panel: a diagonal accent →
 * card → accent2 wash whose angle is derived from [seed], a faint 28px dot lattice, and the label
 * as a ghost numeral. The same project therefore always looks the same, on every platform.
 */
@Composable
fun MediaPanel(seed: String, label: String, modifier: Modifier = Modifier) {
    val colors = cvColors
    val angleRad = remember(seed) { (seed.hashCode().mod(360)) * PI.toFloat() / 180f }
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .drawBehind {
                val dx = cos(angleRad) * size.width
                val dy = sin(angleRad) * size.height
                drawRoundRect(
                    brush =
                        Brush.linearGradient(
                            colors =
                                listOf(
                                    colors.accent.copy(alpha = 0.18f),
                                    colors.card,
                                    colors.accent2.copy(alpha = 0.10f),
                                ),
                            start = Offset(0f, 0f),
                            end = Offset(dx, dy),
                        ),
                    cornerRadius = CornerRadius(16.dp.toPx()),
                )
                val pitch = 28.dp.toPx()
                val dot = colors.accent.copy(alpha = 0.07f)
                var y = pitch / 2f
                while (y < size.height) {
                    var x = pitch / 2f
                    while (x < size.width) {
                        drawCircle(color = dot, radius = 1f, center = Offset(x, y))
                        x += pitch
                    }
                    y += pitch
                }
            }
            .border(1.dp, colors.line, CardShape)
            .padding(16.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        BasicText(text = label, style = cvType.ghostNumeral)
    }
}

// ---------------------------------------------------------------------------------------------
// Small parts
// ---------------------------------------------------------------------------------------------

/** `.status-pulse` — the contact-section availability dot and its expanding ring. */
@Composable
fun StatusDot(modifier: Modifier = Modifier) {
    val colors = cvColors
    val pulse by rememberInfiniteFloat(2200, easing = CvMotion.EaseOutQuart)
    Box(
        modifier
            .size(8.dp)
            .drawBehind {
                val ring = 6.dp.toPx() * pulse
                if (ring > 0f) {
                    drawCircle(
                        color = colors.accent.copy(alpha = 0.5f * (1f - pulse)),
                        radius = size.minDimension / 2f + ring,
                    )
                }
            }
            .background(colors.accent, CircleShape),
    )
}

/**
 * The native `<details>` equivalent — a clickable header whose chevron rotates 90° at --dur-fast,
 * with the body expanding underneath.
 *
 * ponytail: the chevron is drawn, not the "▸" glyph — there is no system font on the wasm canvas
 * and an unvendored glyph renders as tofu.
 */
@Composable
fun ExpanderSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = cvColors
    var open by remember { mutableStateOf(false) }
    val rotation by
        animateFloatAsState(
            if (open) 90f else 0f,
            tween(CvMotion.DurFast, easing = CvMotion.EaseOutQuart),
            label = "chevron",
        )
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    Column(modifier) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .then(
                        if (focused) {
                            Modifier.border(2.dp, colors.accent, RoundedCornerShape(6.dp))
                        } else {
                            Modifier
                        },
                    )
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        role = Role.Button,
                        onClick = { open = !open },
                    )
                    .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Canvas(Modifier.size(10.dp).graphicsLayer { rotationZ = rotation }) {
                val p = Path()
                p.moveTo(size.width * 0.2f, 0f)
                p.lineTo(size.width * 0.85f, size.height / 2f)
                p.lineTo(size.width * 0.2f, size.height)
                p.close()
                drawPath(p, colors.accent)
            }
            Spacer(Modifier.width(10.dp))
            BasicText(text = title, style = cvType.cardTitle.copy(fontSize = cvType.body.fontSize))
        }
        AnimatedVisibility(
            visible = open,
            enter = expandVertically(tween(CvMotion.DurBase, easing = CvMotion.EaseOutQuart)) + fadeIn(),
            exit = shrinkVertically(tween(CvMotion.DurFast, easing = CvMotion.EaseOutQuart)) + fadeOut(),
        ) {
            Column(
                modifier = Modifier.padding(start = 20.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content,
            )
        }
    }
}
