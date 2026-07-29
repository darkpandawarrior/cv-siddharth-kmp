package com.siddharth.cv.shared.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * The motion system from index.css, ported one-for-one. Easings are the CSS cubic-beziers verbatim;
 * durations are the same three-step scale so nothing in the port is a bespoke number.
 */
object CvMotion {
    /** Reveals — long, decisive settle. */
    val EaseOutExpo: Easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

    /** Hover / press — snappy but soft. */
    val EaseOutQuart: Easing = CubicBezierEasing(0.25f, 1f, 0.5f, 1f)

    /** Playful overshoot — the primary CTA's hover-in only. */
    val EaseSpring: Easing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

    const val DurFast: Int = 180
    const val DurBase: Int = 300
    const val DurSlow: Int = 600
    const val DurReveal: Int = 600
}

/**
 * The `box-shadow: 0 14px 40px -10px <tint>` substitute, and the single largest fidelity item in
 * the port — the whole visual language is "signal glow".
 *
 * `Modifier.shadow` carries elevation only (no colour, no spread) and `Modifier.blur` is a no-op
 * below Android API 31, so neither can express this. Instead: a stack of concentric rounded-rects
 * drawn behind the content, each one further out and fainter, which accumulates into a soft tinted
 * bloom rather than a hard ring.
 *
 * @param radius how far past the content bounds the bloom reaches.
 * @param alpha peak alpha at the innermost ring.
 * @param offsetY pushes the bloom down, the CSS drop-shadow y-offset.
 */
fun Modifier.glow(
    color: Color,
    radius: Dp,
    alpha: Float = 0.35f,
    offsetY: Dp = 0.dp,
): Modifier =
    this.drawBehind {
        val reach = radius.toPx()
        if (reach <= 0f || alpha <= 0f) return@drawBehind
        val dy = offsetY.toPx()
        // Corner radius is unknowable from here, so track the site's card radius and degrade to a
        // circle on small square things (the 6dp eyebrow LED, the 8dp status dot).
        val baseCorner = minOf(16.dp.toPx(), size.minDimension / 2f)
        val steps = 5
        for (i in steps downTo 1) {
            val spread = reach * i / steps
            // Linear falloff outward; /steps keeps the accumulated centre alpha near `alpha`.
            val ringAlpha = alpha * (steps - i + 1f) / steps * (2f / steps)
            drawRoundRect(
                color = color.copy(alpha = color.alpha * ringAlpha.coerceIn(0f, 1f)),
                topLeft = Offset(-spread, -spread + dy),
                size = Size(size.width + spread * 2f, size.height + spread * 2f),
                cornerRadius = CornerRadius(baseCorner + spread),
            )
        }
    }

/**
 * The CSS `mask-image: linear-gradient(...)` substitute. [stops] are `position to alpha` pairs,
 * transferring verbatim: the hero band's
 * `transparent 0%, black 12%, black 38%, transparent 60%` becomes
 * `listOf(0f to 0f, 0.12f to 1f, 0.38f to 1f, 0.60f to 0f)`.
 *
 * `BlendMode.DstIn` over an offscreen layer is the exact analogue of a CSS alpha mask.
 */
fun Modifier.fadeMask(stops: List<Pair<Float, Float>>): Modifier =
    this
        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        .drawWithContent {
            drawContent()
            drawRect(
                brush =
                    Brush.verticalGradient(
                        colorStops = stops.map { it.first to Color.Black.copy(alpha = it.second) }.toTypedArray(),
                    ),
                blendMode = BlendMode.DstIn,
            )
        }

/**
 * The `.reveal -> .revealed` scroll-in: opacity 0 -> 1 and translateY(16px) -> 0 over
 * --dur-slow on --ease-out-quart.
 *
 * The web version keys off an IntersectionObserver; inside a LazyColumn a delay-based entrance
 * reads identically because a row only composes once it's near the viewport anyway — so there is
 * deliberately no visibility-tracking system here.
 */
@Composable
fun Reveal(delayMillis: Int = 0, content: @Composable () -> Unit) {
    val reduced = LocalReducedMotion.current
    if (reduced) {
        // The CSS forces `.reveal` straight to its revealed state under reduced motion.
        content()
        return
    }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (delayMillis > 0) delay(delayMillis.toLong())
        shown = true
    }
    val progress by
        animateFloatAsState(
            targetValue = if (shown) 1f else 0f,
            animationSpec = tween(CvMotion.DurReveal, easing = CvMotion.EaseOutQuart),
            label = "reveal",
        )
    Box(
        modifier =
            Modifier.graphicsLayer {
                this.alpha = progress
                translationY = (1f - progress) * 16.dp.toPx()
            },
    ) {
        content()
    }
}

/**
 * The `.tilt-card` pointer-tracked 3D tilt plus its `--mx/--my` spotlight.
 *
 * Returns the receiver untouched under reduced motion. Touch platforms never emit hover events, so
 * this is inert on Android/iOS by construction — which is correct, the effect is desktop/web only.
 */
@Composable
fun Modifier.tiltOnHover(maxDegrees: Float = 6f, spotlight: Color? = null): Modifier {
    if (LocalReducedMotion.current) return this
    var pointer by remember { mutableStateOf<Offset?>(null) }
    var box by remember { mutableStateOf(Size.Zero) }

    val targetX = pointer?.let { p -> if (box.height > 0f) (0.5f - p.y / box.height) * 2f * maxDegrees else 0f } ?: 0f
    val targetY = pointer?.let { p -> if (box.width > 0f) (p.x / box.width - 0.5f) * 2f * maxDegrees else 0f } ?: 0f
    val rotX by animateFloatAsState(targetX, tween(CvMotion.DurBase, easing = CvMotion.EaseOutQuart), label = "tiltX")
    val rotY by animateFloatAsState(targetY, tween(CvMotion.DurBase, easing = CvMotion.EaseOutQuart), label = "tiltY")

    return this
        .onSizeChanged { box = Size(it.width.toFloat(), it.height.toFloat()) }
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    when (event.type) {
                        PointerEventType.Enter, PointerEventType.Move ->
                            pointer = event.changes.lastOrNull()?.position
                        PointerEventType.Exit -> pointer = null
                        else -> Unit
                    }
                }
            }
        }
        .graphicsLayer {
            rotationX = rotX
            rotationY = rotY
            cameraDistance = 12f * density
        }
        .then(
            if (spotlight == null) {
                Modifier
            } else {
                Modifier.drawWithContent {
                    drawContent()
                    val p = pointer ?: return@drawWithContent
                    val r = 220.dp.toPx()
                    drawCircle(
                        brush =
                            Brush.radialGradient(
                                colors = listOf(spotlight.copy(alpha = 0.16f), Color.Transparent),
                                center = p,
                                radius = r,
                            ),
                        radius = r,
                        center = p,
                    )
                }
            },
        )
}

/**
 * Elapsed seconds since first composition, ticked once per frame. Backs the starfield twinkle and
 * any hand-rolled canvas animation. Frozen at 0f under reduced motion — one place to enforce it,
 * exactly as the CSS does globally.
 */
@Composable
fun rememberFrameTicker(): State<Float> {
    val reduced = LocalReducedMotion.current
    val seconds = remember { mutableStateOf(0f) }
    LaunchedEffect(reduced) {
        if (reduced) {
            seconds.value = 0f
            return@LaunchedEffect
        }
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { now -> seconds.value = (now - start) / 1_000_000_000f }
        }
    }
    return seconds
}

/**
 * Every ambient loop in the port goes through this so reduced motion is enforced at one place.
 * Returns a constant [from] when motion is off rather than a paused animation, matching the CSS
 * `animation: none` (which resets to the 0% keyframe, not to wherever it happened to be).
 */
@Composable
fun rememberInfiniteFloat(
    durationMillis: Int,
    from: Float = 0f,
    to: Float = 1f,
    easing: Easing = LinearEasing,
): State<Float> {
    if (LocalReducedMotion.current) return rememberUpdatedState(from)
    val transition = rememberInfiniteTransition(label = "infinite")
    return transition.animateFloat(
        initialValue = from,
        targetValue = to,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis, easing = easing),
                // Linear loops restart (`hero-shimmer`, `circuit-run` — a sawtooth by design);
                // eased loops ping-pong, which is what a CSS `ease-in-out infinite` with a single
                // 50% keyframe (`cta-breathe`, `glow-pulse`, `float-soft`) actually does.
                repeatMode = if (easing == LinearEasing) RepeatMode.Restart else RepeatMode.Reverse,
            ),
        label = "infiniteFloat",
    )
}
