package com.siddharth.cv.shared.forge

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.siddharth.cv.shared.theme.LocalReducedMotion
import com.siddharth.cv.shared.theme.cvColors
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The Particle Forge — the port of cv-siddharth/src/ParticleWordmark.tsx.
 *
 * A couple of thousand particles are spring-tied to points along a wordmark, pushed radially out of
 * the way wherever the pointer goes, and pulled back by the same springs the moment it leaves.
 * Physics on a canvas, which is the point the room is making.
 *
 * ## Why the wordmark is drawn geometry and not font outlines
 *
 * The obvious route is `TextLayoutResult.getPathForRange()` on a measured string. On Android that
 * returns real glyph outlines. On every skiko target — wasm included, which is the one that ships —
 * it does not: `SkiaParagraph.getPathForRange` calls `Paragraph.getRectsForRange(MAX, TIGHT)` and
 * stuffs those boxes into a `PathBuilder`, so what comes back is the *selection rectangle* of the
 * range, not its letterforms. Verified against the bytecode of ui-text 1.12.0-beta02, not against
 * the docs. Sampling it would spell "siddharth" as one filled slab.
 *
 * The other two routes are worse: rasterise-and-read-pixels (what the React original does with
 * `getImageData`) has no reliable wasm equivalent, and reaching into `org.jetbrains.skia` for
 * `Font.getPath` is both banned in this source set and single-platform.
 *
 * So the mark is authored here as a stroke skeleton in em units — nine glyphs, twenty-odd contours
 * of lines, cubics and ovals — and then walked with [PathMeasure] exactly as it would have been
 * walked had the outline come from a font. The letterforms are geometric rather than Space Grotesk,
 * which reads as deliberate at this size, and the whole mark is resolution-independent and testable.
 * ponytail: if CMP ever implements real glyph outlines on skiko, swap [wordmarkStrokes] for one
 * `getPathForRange` call and delete the glyph table — nothing else in this file changes.
 */

// ---------------------------------------------------------------------------------------------
// Physics constants
// ---------------------------------------------------------------------------------------------

/**
 * The React original integrates in per-frame units at whatever rate the display runs
 * (`v += dx * 0.045`, `v *= 0.86`, `v += dir * 4.2`). Those are the same numbers converted to
 * per-second units against its implicit 60Hz, so the feel matches on a 120Hz panel instead of
 * running twice as fast: k = 0.045 * 60^2, damping = -ln(0.86) * 60, repulsion = 4.2 * 60^2.
 */
internal const val ForgeStiffness: Float = 162f

internal const val ForgeDamping: Float = 9f

internal const val ForgeRepelAccel: Float = 15_120f

/**
 * The integrator's ceiling, enforced *inside* [forgeStep] rather than at its call site.
 *
 * A backgrounded browser tab delivers one frame with a multi-second delta the instant it comes
 * back; at k = 162 a 2s step multiplies every offset by 324 and the swarm is gone forever. Clamping
 * here means no caller — the frame loop, a self-check, anything added later — can blow the
 * simulation up by handing it a bad `dt`. One guard where every path already runs through.
 */
internal const val ForgeMaxStepSeconds: Float = 1f / 60f

/** Fixed-ish timestep: the loop runs whole [ForgeMaxStepSeconds] steps and drops the remainder. */
private const val ForgeMaxSubsteps: Int = 4

/** Cursor influence radius, from the original's 34 CSS px, nudged up for touch-sized pointers. */
private val ForgeRepelRadius: Dp = 38.dp

/** Pressing amplifies the same field instead of adding a second one — "give it a click". */
private const val ForgePressRadiusScale: Float = 3.4f

private const val ForgePressAccelScale: Float = 2.2f

/** Arc-length gap between particles. Fixed in dp, so the mark stays equally dense at any size. */
private val ForgeSpacing: Dp = 1.4.dp

private val ForgeDotSize: Dp = 1.8.dp

/**
 * The particle budget. The floor stops a phone-width canvas from reading as a dotted line, the
 * ceiling stops a 4K window from asking for 30k circles a frame; between them the count simply
 * follows the outline length, which follows the canvas area.
 */
private const val ForgeMinParticles: Int = 700

private const val ForgeMaxParticles: Int = 4200

private val ForgeDefaultHeight: Dp = 280.dp

/** A Canvas exposes no text nodes at all, so this is the entire accessible surface of the room. */
private const val ForgeDescription: String =
    "An interactive particle field spelling the wordmark \"siddharth\". A couple of thousand dots " +
        "are spring-tied to points along the letters and tinted from Android green on the left to " +
        "cyan on the right. Moving a pointer through the mark pushes nearby dots radially out of " +
        "its way so the letters part around it, and pressing pushes them much further; the springs " +
        "pull every dot back into place as soon as the pointer moves on."

// ---------------------------------------------------------------------------------------------
// The step
// ---------------------------------------------------------------------------------------------

/**
 * One semi-implicit Euler step of the whole swarm, in place over parallel [FloatArray]s.
 *
 * Arrays rather than a `List<Particle>` because this runs over a few thousand elements sixty times
 * a second: six flat float buffers are six allocations for the lifetime of the mark, where boxed
 * particles would be a few thousand objects the collector has to keep walking. It is also what
 * makes the whole thing checkable — no Compose types in the signature, so [forgeSelfCheck] can run
 * it on the JVM.
 *
 * Pass [repelRadius] = 0 for "pointer not over the canvas"; there is no separate flag to keep in
 * sync with it.
 */
internal fun forgeStep(
    px: FloatArray,
    py: FloatArray,
    vx: FloatArray,
    vy: FloatArray,
    tx: FloatArray,
    ty: FloatArray,
    count: Int,
    dtSeconds: Float,
    pointerX: Float,
    pointerY: Float,
    repelRadius: Float,
    repelAccel: Float,
) {
    if (dtSeconds <= 0f || dtSeconds.isNaN()) return
    val h = min(dtSeconds, ForgeMaxStepSeconds)
    // Linear approximation of exp(-damping * h). Exact enough at h <= 1/60 and cheaper; the
    // coerce is what keeps it a decay rather than a sign flip if the constants are ever raised.
    val damp = (1f - ForgeDamping * h).coerceIn(0f, 1f)
    val radius = if (repelRadius > 0f) repelRadius else 0f
    val radius2 = radius * radius

    for (i in 0 until count) {
        // Hooke: the spring only ever knows where home is, which is why the mark reassembles
        // itself from any state at all — including the initial random scatter.
        var vxi = vx[i] + (tx[i] - px[i]) * ForgeStiffness * h
        var vyi = vy[i] + (ty[i] - py[i]) * ForgeStiffness * h

        if (radius2 > 0f) {
            val dx = px[i] - pointerX
            val dy = py[i] - pointerY
            val d2 = dx * dx + dy * dy
            if (d2 < radius2) {
                val d = sqrt(d2)
                // A particle exactly under the pointer has no direction to flee. Give it a fixed
                // diagonal instead of dividing by zero — one NaN here poisons the array forever,
                // because every later step reads its own previous output.
                val ux: Float
                val uy: Float
                if (d > 1e-4f) {
                    ux = dx / d
                    uy = dy / d
                } else {
                    ux = 0.70710678f
                    uy = 0.70710678f
                }
                val falloff = 1f - d / radius // 1 at the centre, 0 at the edge
                vxi += ux * falloff * repelAccel * h
                vyi += uy * falloff * repelAccel * h
            }
        }

        vxi *= damp
        vyi *= damp
        vx[i] = vxi
        vy[i] = vyi
        px[i] += vxi * h
        py[i] += vyi * h
    }
}

// ---------------------------------------------------------------------------------------------
// The wordmark
// ---------------------------------------------------------------------------------------------

private const val ForgeWord: String = "siddharth"

/** `--tracking`, in em. */
private const val ForgeLetterSpacing: Float = 0.06f

/**
 * Per-glyph advance in em. Only the nine letters of [ForgeWord] are real; anything else falls back
 * to a neutral width so a future word can't silently overlap itself.
 */
private const val ForgeFallbackAdvance: Float = 0.52f

private fun glyphAdvance(ch: Char): Float = when (ch) {
    'i' -> 0.26f
    'r', 't' -> 0.46f
    's', 'a' -> 0.60f
    'h' -> 0.64f
    'd' -> 0.66f
    else -> ForgeFallbackAdvance
}

/**
 * One glyph's centre-line strokes, appended to [out] as separate single-contour [Path]s.
 *
 * Separate paths, not one path with several `moveTo`s, because [PathMeasure] measures only the
 * *first* contour of whatever it is given — `length` on a nine-letter path would report the length
 * of the letter s and the other eight would never be sampled. (`Path.divide()` would split them
 * back apart, but authoring them apart is the same thing without the extra pass.)
 *
 * Coordinates are em-relative with y = 0 at the ascender top, 0.44 at x-height and 1.0 at the
 * baseline — the same axis a font uses, so the numbers are readable as letterforms.
 */
private fun appendGlyph(ch: Char, originX: Float, originY: Float, em: Float, out: MutableList<Path>) {
    fun x(u: Float): Float = originX + u * em
    fun y(v: Float): Float = originY + v * em
    fun stroke(block: Path.() -> Unit) {
        val p = Path()
        p.block()
        out += p
    }

    when (ch) {
        's' -> stroke {
            moveTo(x(0.52f), y(0.52f))
            cubicTo(x(0.46f), y(0.40f), x(0.08f), y(0.38f), x(0.08f), y(0.57f))
            cubicTo(x(0.08f), y(0.70f), x(0.50f), y(0.72f), x(0.50f), y(0.86f))
            cubicTo(x(0.50f), y(1.02f), x(0.14f), y(1.02f), x(0.06f), y(0.92f))
        }

        'i' -> {
            stroke {
                moveTo(x(0.13f), y(0.44f))
                lineTo(x(0.13f), y(1.00f))
            }
            // The tittle. An oval is one closed contour, so it samples like any other stroke.
            stroke { addOval(Rect(x(0.055f), y(0.20f), x(0.205f), y(0.35f))) }
        }

        'd' -> {
            stroke {
                moveTo(x(0.58f), y(0.06f))
                lineTo(x(0.58f), y(1.00f))
            }
            stroke { addOval(Rect(x(0.04f), y(0.44f), x(0.58f), y(1.00f))) }
        }

        'h' -> {
            stroke {
                moveTo(x(0.07f), y(0.06f))
                lineTo(x(0.07f), y(1.00f))
            }
            stroke {
                moveTo(x(0.07f), y(0.62f))
                cubicTo(x(0.12f), y(0.42f), x(0.56f), y(0.40f), x(0.56f), y(0.64f))
                lineTo(x(0.56f), y(1.00f))
            }
        }

        'a' -> {
            stroke { addOval(Rect(x(0.04f), y(0.44f), x(0.52f), y(1.00f))) }
            stroke {
                moveTo(x(0.52f), y(0.44f))
                lineTo(x(0.52f), y(1.00f))
            }
        }

        'r' -> {
            stroke {
                moveTo(x(0.09f), y(0.44f))
                lineTo(x(0.09f), y(1.00f))
            }
            stroke {
                moveTo(x(0.09f), y(0.62f))
                cubicTo(x(0.14f), y(0.44f), x(0.30f), y(0.40f), x(0.42f), y(0.46f))
            }
        }

        't' -> {
            stroke {
                moveTo(x(0.22f), y(0.16f))
                lineTo(x(0.22f), y(0.88f))
                cubicTo(x(0.22f), y(1.02f), x(0.34f), y(1.04f), x(0.42f), y(0.98f))
            }
            stroke {
                moveTo(x(0.02f), y(0.46f))
                lineTo(x(0.42f), y(0.46f))
            }
        }

        else -> Unit // no glyph, no strokes — the advance still moves the pen
    }
}

/** Total advance of [ForgeWord] in em, tracking included. */
private fun wordAdvance(word: String): Float {
    if (word.isEmpty()) return 0f
    var sum = ForgeLetterSpacing * (word.length - 1)
    for (ch in word) sum += glyphAdvance(ch)
    return sum
}

/** The whole mark, laid out centred in a [width] by [height] box at [em] px per em. */
private fun wordmarkStrokes(word: String, width: Float, height: Float, em: Float): List<Path> {
    val out = ArrayList<Path>(word.length * 3)
    var penX = (width - wordAdvance(word) * em) / 2f
    // Visible ink spans y = 0.06em (ascender) to 1.0em (baseline); centre that band, not the em box.
    val originY = height / 2f - 0.53f * em
    for (ch in word) {
        appendGlyph(ch, penX, originY, em, out)
        penX += (glyphAdvance(ch) + ForgeLetterSpacing) * em
    }
    return out
}

// ---------------------------------------------------------------------------------------------
// The swarm
// ---------------------------------------------------------------------------------------------

/** Six flat buffers plus the mark's horizontal extent, which is what the tint gradient spans. */
private class Forge(
    val count: Int,
    val tx: FloatArray,
    val ty: FloatArray,
    val px: FloatArray,
    val py: FloatArray,
    val vx: FloatArray,
    val vy: FloatArray,
    val markLeft: Float,
    val markRight: Float,
)

/**
 * Samples the wordmark into a [Forge], or returns null when the canvas is too small to hold a
 * legible mark (first layout pass reports 0 on wasm, and a 20px-tall forge is noise, not a mark).
 *
 * [settled] parks every particle on its target instead of scattering it: that is both the
 * reduced-motion still frame and what a resize wants — a resize re-lays-out the same mark, it does
 * not re-run the assembly reveal.
 */
private fun buildForge(
    width: Float,
    height: Float,
    spacingPx: Float,
    settled: Boolean,
): Forge? {
    val advance = wordAdvance(ForgeWord)
    if (advance <= 0f || width < 80f || height < 60f || spacingPx <= 0f) return null
    // Width-limited on a wide canvas, height-limited on a squat one.
    val em = min(width * 0.92f / advance, height / 1.30f)
    if (em < 24f) return null

    val strokes = wordmarkStrokes(ForgeWord, width, height, em)
    val measure = PathMeasure()

    val lengths = FloatArray(strokes.size)
    var total = 0f
    strokes.forEachIndexed { i, path ->
        measure.setPath(path, false)
        lengths[i] = measure.length
        total += lengths[i]
    }
    if (total <= 0f) return null

    val wanted = (total / spacingPx).roundToInt().coerceIn(ForgeMinParticles, ForgeMaxParticles)
    val spacing = total / wanted

    // Exact per-stroke counts first, so the buffers are allocated once at the right size instead of
    // growing an ArrayList<Float> of a few thousand boxed floats.
    val perStroke = IntArray(strokes.size)
    var count = 0
    for (i in strokes.indices) {
        val n = if (lengths[i] <= 0f) 0 else (lengths[i] / spacing).roundToInt().coerceAtLeast(1)
        perStroke[i] = n
        count += n
    }
    if (count == 0) return null

    val tx = FloatArray(count)
    val ty = FloatArray(count)
    var w = 0
    strokes.forEachIndexed { s, path ->
        val n = perStroke[s]
        if (n > 0) {
            measure.setPath(path, false)
            val step = lengths[s] / n
            for (k in 0 until n) {
                val at = measure.getPosition(k * step)
                tx[w] = at.x
                ty[w] = at.y
                w++
            }
        }
    }

    // Seeded: the assembly reveal is identical on every load and every platform, exactly as the
    // starfield in CvComponents is.
    val rng = Random(0x5EED)
    val px = FloatArray(count)
    val py = FloatArray(count)
    for (i in 0 until count) {
        px[i] = if (settled) tx[i] else rng.nextFloat() * width
        py[i] = if (settled) ty[i] else rng.nextFloat() * height
    }

    var left = tx[0]
    var right = tx[0]
    for (i in 1 until count) {
        if (tx[i] < left) left = tx[i]
        if (tx[i] > right) right = tx[i]
    }

    return Forge(
        count = count,
        tx = tx,
        ty = ty,
        px = px,
        py = py,
        vx = FloatArray(count),
        vy = FloatArray(count),
        markLeft = left,
        markRight = right,
    )
}

/**
 * Pointer state as a plain mutable holder, deliberately *not* snapshot state: the frame loop and the
 * draw scope both read it every frame, and making it observable would recompose the whole subtree on
 * every mouse move for no benefit.
 */
private class ForgePointer {
    var x: Float = 0f
    var y: Float = 0f
    var active: Boolean = false
    var pressed: Boolean = false
}

// ---------------------------------------------------------------------------------------------
// The composable
// ---------------------------------------------------------------------------------------------

/**
 * The forge. Sizes itself to a sensible default; any size in [modifier] wins, since [modifier] is
 * applied after the defaults.
 *
 * Under reduced motion there is no frame loop and no pointer handler at all — the settled wordmark
 * is drawn once and stays put. A slowed-down swarm would still be a swarm.
 */
@Composable
fun ParticleForge(modifier: Modifier = Modifier) {
    val colors = cvColors
    val reduced = LocalReducedMotion.current
    val density = LocalDensity.current

    var box by remember { mutableStateOf(IntSize.Zero) }
    val spacingPx = with(density) { ForgeSpacing.toPx() }
    val dotPx = with(density) { ForgeDotSize.toPx() }
    val repelPx = with(density) { ForgeRepelRadius.toPx() }

    // Survives the rebuilds a resize causes, so the reveal plays exactly once per mount.
    val revealed = remember { BooleanArray(1) }
    val forge =
        remember(box, reduced, spacingPx) {
            buildForge(
                width = box.width.toFloat(),
                height = box.height.toFloat(),
                spacingPx = spacingPx,
                settled = reduced || revealed[0],
            ).also { if (it != null) revealed[0] = true }
        }

    val pointer = remember { ForgePointer() }
    // A frame token read in the *draw* phase: bumping it invalidates this Canvas's drawing and
    // nothing else, which is how the swarm animates without recomposing sixty times a second.
    val tick = remember { mutableFloatStateOf(0f) }
    // Reused across frames — three batched drawPoints calls beat a few thousand drawCircles, and
    // clear() keeps the backing capacity so only the Offsets themselves are re-boxed.
    // ponytail: a FloatArray-based points API would remove even those, but the only one is
    // platform-specific (nativeCanvas), which this source set cannot see.
    val batches = remember { List(3) { ArrayList<Offset>(1024) } }

    LaunchedEffect(forge, reduced, repelPx) {
        if (reduced || forge == null) return@LaunchedEffect
        var last = withInfiniteAnimationFrameNanos { it }
        var carry = 0f
        while (true) {
            withInfiniteAnimationFrameNanos { now ->
                val raw = (now - last) / 1_000_000_000f
                last = now
                // Clamp the frame delta *and* cap the substep count: a tab returning from the
                // background must not be simulated forward by the ten seconds it was away.
                carry += raw.coerceIn(0f, 0.25f)
                val radius =
                    when {
                        !pointer.active -> 0f
                        pointer.pressed -> repelPx * ForgePressRadiusScale
                        else -> repelPx
                    }
                val accel = if (pointer.pressed) ForgeRepelAccel * ForgePressAccelScale else ForgeRepelAccel
                var steps = 0
                while (carry >= ForgeMaxStepSeconds && steps < ForgeMaxSubsteps) {
                    forgeStep(
                        px = forge.px,
                        py = forge.py,
                        vx = forge.vx,
                        vy = forge.vy,
                        tx = forge.tx,
                        ty = forge.ty,
                        count = forge.count,
                        dtSeconds = ForgeMaxStepSeconds,
                        pointerX = pointer.x,
                        pointerY = pointer.y,
                        repelRadius = radius,
                        repelAccel = accel,
                    )
                    carry -= ForgeMaxStepSeconds
                    steps++
                }
                if (steps == ForgeMaxSubsteps) carry = 0f // drop the backlog, never chase it
                tick.floatValue += 1f
            }
        }
    }

    val brush =
        remember(colors.accent, colors.accent2, forge) {
            if (forge == null) {
                Brush.horizontalGradient(listOf(colors.accent, colors.accent2))
            } else {
                Brush.horizontalGradient(
                    colors = listOf(colors.accent, colors.accent2),
                    startX = forge.markLeft,
                    endX = forge.markRight,
                )
            }
        }

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(ForgeDefaultHeight)
            .then(modifier)
            .onSizeChanged { box = it }
            .semantics { contentDescription = ForgeDescription }
            .then(if (reduced) Modifier else Modifier.pointerInput(Unit) { trackForgePointer(pointer) }),
    ) {
        tick.floatValue // the draw-phase read; see above
        val f = forge ?: return@Canvas
        batches.forEach { it.clear() }
        for (i in 0 until f.count) {
            val speed = abs(f.vx[i]) + abs(f.vy[i])
            // Fast particles read brighter: the motion is the highlight, as in the original.
            val bucket = if (speed < 30f) 0 else if (speed < 220f) 1 else 2
            batches[bucket] += Offset(f.px[i], f.py[i])
        }
        drawBatch(batches[0], brush, dotPx, 0.45f)
        drawBatch(batches[1], brush, dotPx, 0.72f)
        drawBatch(batches[2], brush, dotPx * 1.15f, 1f)
    }
}

private fun DrawScope.drawBatch(points: List<Offset>, brush: Brush, dot: Float, alpha: Float) {
    if (points.isEmpty()) return
    drawPoints(
        points = points,
        pointMode = PointMode.Points,
        brush = brush,
        strokeWidth = dot,
        cap = StrokeCap.Round,
        alpha = alpha,
    )
}

/**
 * Hover, press and leave, in the one raw-event loop.
 *
 * `Release` only parks the pointer for touch: a mouse that clicks and holds still emits no further
 * `Move`, so clearing [ForgePointer.active] there would snap the mark shut under a stationary
 * cursor. A finger, which has no hover state at all, has to be cleared on release or the mark stays
 * parted forever after a tap.
 */
private suspend fun PointerInputScope.trackForgePointer(pointer: ForgePointer) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.lastOrNull()
            when (event.type) {
                PointerEventType.Enter, PointerEventType.Move -> {
                    change?.let {
                        pointer.x = it.position.x
                        pointer.y = it.position.y
                        pointer.active = true
                    }
                }

                PointerEventType.Press -> {
                    change?.let {
                        pointer.x = it.position.x
                        pointer.y = it.position.y
                        pointer.active = true
                        pointer.pressed = true
                    }
                }

                PointerEventType.Release -> {
                    pointer.pressed = false
                    if (change?.type == PointerType.Touch) pointer.active = false
                }

                PointerEventType.Exit -> {
                    pointer.pressed = false
                    pointer.active = false
                }

                else -> Unit
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Self-check
// ---------------------------------------------------------------------------------------------

/**
 * ponytail: one runnable check instead of a test module, same shape as `labsSelfCheck`. Must be
 * called from `selfCheck()` in jvmMain's Prerender.kt — nothing under composeMain executes on its
 * own, so a check that isn't wired there reads as coverage it isn't.
 *
 * Only [forgeStep] and the glyph table are covered: those are the parts that can be silently wrong.
 * The sampling pass needs a real [Path] and is therefore Compose's problem, not arithmetic's.
 */
internal fun forgeSelfCheck() {
    fun arrays(x: Float, y: Float, tx: Float, ty: Float): Array<FloatArray> =
        arrayOf(
            floatArrayOf(x), floatArrayOf(y),
            floatArrayOf(0f), floatArrayOf(0f),
            floatArrayOf(tx), floatArrayOf(ty),
        )

    fun step(a: Array<FloatArray>, dt: Float, pxr: Float = 0f, pyr: Float = 0f, radius: Float = 0f) {
        forgeStep(a[0], a[1], a[2], a[3], a[4], a[5], 1, dt, pxr, pyr, radius, ForgeRepelAccel)
    }

    fun dist(a: Array<FloatArray>): Float {
        val dx = a[4][0] - a[0][0]
        val dy = a[5][0] - a[1][0]
        return sqrt(dx * dx + dy * dy)
    }

    // A particle released far from home converges on it, and gets closer along the way.
    val a = arrays(0f, 0f, 240f, 90f)
    val start = dist(a)
    repeat(30) { step(a, ForgeMaxStepSeconds) }
    check(dist(a) < start) { "the spring pulls in: was $start, now ${dist(a)}" }
    repeat(600) { step(a, ForgeMaxStepSeconds) }
    check(dist(a) < 0.5f) { "a released particle settles on its target, off by ${dist(a)}" }
    check(abs(a[2][0]) < 1f && abs(a[3][0]) < 1f) { "and stops moving when it gets there" }

    // Stability: a pathological dt (a tab returning from the background) is clamped inside the
    // step, so thousands of them neither NaN nor fling the particle off to infinity.
    val b = arrays(-4000f, 5000f, 100f, 100f)
    repeat(4000) { step(b, 5f, 100f, 100f, 60f) }
    check(b[0][0].isFinite() && b[1][0].isFinite()) { "no NaN or infinity under a 5s dt" }
    check(b[2][0].isFinite() && b[3][0].isFinite()) { "velocity stays finite too" }
    // It ends up orbiting the pointer at the radius where the spring balances the repulsion
    // (k*d = accel*(1 - d/r), about 36px here), not thrown off to infinity.
    check(dist(b) < 100f) { "no runaway: ${dist(b)}px from home at ${b[0][0]}, ${b[1][0]}" }

    // Zero-distance guard: a particle exactly under the pointer must not divide by zero.
    val c = arrays(50f, 50f, 50f, 50f)
    repeat(10) { step(c, ForgeMaxStepSeconds, 50f, 50f, 40f) }
    check(c[0][0].isFinite() && c[1][0].isFinite()) { "a direct hit produces a direction, not a NaN" }
    check(dist(c) > 0f) { "and actually pushes the particle off its target" }

    // Repulsion pushes away from the pointer, and only inside the radius.
    val d = arrays(100f, 100f, 100f, 100f)
    step(d, ForgeMaxStepSeconds, 90f, 100f, 40f)
    check(d[2][0] > 0f) { "a pointer to the left pushes right, not left" }
    val e = arrays(100f, 100f, 100f, 100f)
    step(e, ForgeMaxStepSeconds, 300f, 100f, 40f)
    check(e[2][0] == 0f && e[3][0] == 0f) { "a pointer outside the radius does nothing" }
    val f = arrays(100f, 100f, 100f, 100f)
    step(f, ForgeMaxStepSeconds, 90f, 100f, 0f)
    check(f[2][0] == 0f) { "radius 0 means no pointer at all" }

    // A non-positive or NaN dt is a no-op rather than a corruption.
    val g = arrays(10f, 20f, 300f, 400f)
    step(g, 0f)
    step(g, -1f)
    step(g, Float.NaN)
    check(g[0][0] == 10f && g[1][0] == 20f && g[2][0] == 0f) { "a bad dt leaves the swarm alone" }

    // The glyph table. Deliberately checked through [glyphAdvance] and not by calling
    // [appendGlyph]: constructing a Path here would make the JVM prerender load skiko's native
    // library, which it otherwise never needs — a self-check must not be able to break the build it
    // guards. The two `when`s cover the same letter set, so a letter missing from one is a letter
    // falling through to the fallback advance in the other.
    ForgeWord.toSet().forEach { ch ->
        check(glyphAdvance(ch) != ForgeFallbackAdvance) {
            "'$ch' is not in the glyph table — it would be a hole in the wordmark"
        }
    }
    check(glyphAdvance('z') == ForgeFallbackAdvance) { "an unknown letter still advances the pen" }
    check(wordAdvance(ForgeWord) > 5f) { "nine letters is a wide mark, not a narrow one" }
    check(wordAdvance("") == 0f) { "an empty word has no advance" }
    check(wordAdvance("i") == glyphAdvance('i')) { "one letter carries no tracking" }
}
