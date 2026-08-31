package com.siddharth.cv.shared.map

import com.siddharth.cv.shared.data.generated.StoryMapEdge
import com.siddharth.cv.shared.data.generated.StoryMapNode
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * The Storyboard's layout engine: Fruchterman-Reingold over `storyMapNodes` / `storyMapEdges`,
 * with no Compose type anywhere in the file.
 *
 * WHY IT IS A SEPARATE FILE, and this is the whole point of it. Everything here is a pure function
 * of the node set, the edge set and an integer seed, so the settled constellation can be asserted
 * on the JVM by [storyMapLayoutSelfCheck] without composing anything. That is the pattern the lab
 * bench already sets: a simulation that is a pure function of its inputs gets its reduced-motion
 * still frame for free AND gets its arithmetic checked, where one that reads a frame clock inside a
 * Canvas gets neither.
 *
 * WHY DETERMINISM IS ARITHMETIC, NOT LUCK. Nothing below calls `hypot`, `pow`, or any trig: those
 * are permitted a unit of error by IEEE 754 and the error is free to differ between the JVM, V8 and
 * a wasm runtime, which would draw the same graph three ways. `sqrt` is exactly rounded on all
 * three, and `+ - * /` on [Double] are exact operations, so every target settles bit-for-bit
 * identically. The seeded LCG is the same generator the anthology's starmap uses, for the same
 * reason.
 *
 * WHY IT IS SEEDED FROM THE HAND LAYOUT. `StoryMapNode.x` / `.y` are a designer's placement, and
 * throwing them away for a random scatter would discard real information (the hub in the middle,
 * the projects on one side, the writing on the other) in order to re-derive something worse. They
 * are the initial condition instead, nudged by [SEED_JITTER] so that no two nodes can start on the
 * same point and stall the repulsion term.
 */

/** The LCG seed. Any Int settles; this one is fixed so every load and every target draws alike. */
const val STORY_LAYOUT_SEED: Int = 0x5109

/**
 * A settled constellation, plus the frames it settled through.
 *
 * A FRAME IS `2 * n` DOUBLES: the first `n` are x, the next `n` are y, both in the unit square.
 * One flat array rather than a list of points because [sampleInto] runs in the draw phase sixty
 * times a second and must not allocate; [xOf] / [yOf] are the readable way in.
 *
 * Not a `data class`: the frames are arrays, and array `equals` is identity, so a generated
 * `equals`/`hashCode` here would be a trap rather than a convenience.
 */
class StoryLayout internal constructor(
    /** Node ids in frame order. Index `i` of any frame is the node `ids[i]`. */
    val ids: List<String>,
    /** Captured every [FRAME_EVERY] iterations, plus the settled state last. */
    val frames: List<DoubleArray>,
    /**
     * The largest single-node displacement on the final iteration: the convergence witness. A
     * layout that is still moving when the iterations run out says so in this number rather than
     * looking settled because the loop stopped.
     */
    val settleStep: Double,
) {
    val size: Int get() = ids.size

    /** The state everything non-animated reads: hit testing, the reduced-motion still frame. */
    val settled: DoubleArray get() = frames[frames.lastIndex]

    fun xOf(frame: DoubleArray, i: Int): Double = frame[i]

    fun yOf(frame: DoubleArray, i: Int): Double = frame[size + i]

    /**
     * The settle, replayed as a pure function of [progress] in 0..1, into a caller-owned [out]
     * buffer of `2 * size` doubles.
     *
     * Linear blend between the two captured frames straddling [progress], because the frames
     * already carry the cooling curve: easing the playback on top of them would be a second,
     * invented deceleration over a real one.
     */
    fun sampleInto(progress: Double, out: DoubleArray) {
        require(out.size == size * 2) { "out must be ${size * 2} doubles, was ${out.size}" }
        val last = frames.lastIndex
        val at = (progress.coerceIn(0.0, 1.0) * last)
        val lo = at.toInt().coerceIn(0, last)
        val hi = (lo + 1).coerceAtMost(last)
        val t = at - lo
        val a = frames[lo]
        val b = frames[hi]
        for (i in out.indices) out[i] = a[i] + (b[i] - a[i]) * t
    }
}

// ---------------------------------------------------------------------------------------------
// Tuning. Every one of these is read by exactly one line below; none is a guess left in place.
// ---------------------------------------------------------------------------------------------

/** Enough for the cooling schedule to reach a standstill on a graph this size; see [settleStep]. */
private const val ITERATIONS: Int = 320

/** One captured frame per this many iterations. 320/8 + 1 = 41 frames for a ~1.4s playback. */
private const val FRAME_EVERY: Int = 8

/** Fruchterman-Reingold's `C` in `k = C * sqrt(area / n)`. Area is 1: the layout is unit-square. */
private const val SPACING_CONSTANT: Double = 0.92

/** The starting temperature, i.e. the furthest a node may move in one iteration. */
private const val INITIAL_TEMPERATURE: Double = 0.13

/** Margin kept clear on every side of the fitted frame, for glow and labels. */
private const val FRAME_PADDING: Double = 0.06

/** How far a node is nudged off its hand-placed seed, so no two can start coincident. */
private const val SEED_JITTER: Double = 0.02

/** Guards the divisions by distance. Below this, two nodes are treated as coincident. */
private const val COINCIDENT: Double = 1e-4

private const val LCG_MULTIPLIER: Int = 1664525

private const val LCG_INCREMENT: Int = 1013904223

private const val TWO_POW_32: Double = 4294967296.0

/** Reads the wrapping Int back as the unsigned 32-bit value the generator is defined over. */
private const val UINT_MASK: Long = 0xFFFF_FFFFL

private const val HALF: Double = 0.5

// ---------------------------------------------------------------------------------------------
// The layout
// ---------------------------------------------------------------------------------------------

/**
 * Runs the graph to a standstill and returns every frame of it.
 *
 * ponytail: all-pairs repulsion, O(n^2) per iteration. The constellation is thirteen nodes, so a
 * Barnes-Hut quadtree would be more code than this whole file buys back. Upgrade path if it ever
 * passes a few hundred nodes; there is no other reason to.
 *
 * An edge naming a node that is not in [nodes] is dropped rather than thrown on. The emitter
 * already asserts both endpoints resolve (`gen-kotlin-data.mjs` fails the generation), so this can
 * only fire on a corpus that is already broken, and a page that draws a slightly wrong graph beats
 * a page that draws nothing.
 */
fun layoutStoryMap(
    nodes: List<StoryMapNode>,
    edges: List<StoryMapEdge>,
    seed: Int = STORY_LAYOUT_SEED,
): StoryLayout {
    require(nodes.isNotEmpty()) { "the constellation cannot be empty" }
    val n = nodes.size
    val index = nodes.withIndex().associate { (i, node) -> node.id to i }
    val links =
        edges.mapNotNull { e ->
            val a = index[e.from] ?: return@mapNotNull null
            val b = index[e.to] ?: return@mapNotNull null
            if (a == b) null else intArrayOf(a, b)
        }

    val pos = DoubleArray(n * 2)
    val rng = Lcg(seed)
    for (i in 0 until n) {
        pos[i] = nodes[i].x + (rng.nextUnit() - HALF) * SEED_JITTER
        pos[n + i] = nodes[i].y + (rng.nextUnit() - HALF) * SEED_JITTER
    }

    val k = SPACING_CONSTANT * sqrt(1.0 / n)
    val force = DoubleArray(n * 2)
    val frames = ArrayList<DoubleArray>(ITERATIONS / FRAME_EVERY + 2)
    var settleStep = 0.0

    for (iteration in 0 until ITERATIONS) {
        force.fill(0.0)
        repel(pos, force, n, k)
        attract(pos, force, links, n, k)
        // Classic linear cooling: the temperature is the per-node step cap, so the graph makes big
        // rearrangements early and only polishes late. It is also why the last frame is the
        // settled one rather than one of an endless jitter.
        val temperature = INITIAL_TEMPERATURE * (1.0 - iteration.toDouble() / ITERATIONS)
        settleStep = displace(pos, force, n, temperature)
        if (iteration % FRAME_EVERY == 0) frames += pos.copyOf()
    }

    // ONE transform, taken from the settled state and applied to every captured frame, so the
    // playback settles inside a fixed frame instead of appearing to zoom while it does it.
    fitToFrame(pos, frames, n)
    frames += pos.copyOf()

    return StoryLayout(ids = nodes.map { it.id }, frames = frames, settleStep = settleStep)
}

/** `k^2 / d` along the separating axis, for every pair. The term that spreads the graph out. */
private fun repel(pos: DoubleArray, force: DoubleArray, n: Int, k: Double) {
    val kk = k * k
    for (i in 0 until n) {
        for (j in i + 1 until n) {
            var ax = pos[i] - pos[j]
            var ay = pos[n + i] - pos[n + j]
            var d = sqrt(ax * ax + ay * ay)
            if (d < COINCIDENT) {
                // Two nodes on the same point have no direction to separate along. Hand them a
                // fixed one rather than dividing by zero: one NaN poisons every later iteration,
                // because each reads its own previous output.
                ax = COINCIDENT
                ay = 0.0
                d = COINCIDENT
            }
            // The vector is already `d` long, so scaling it by k^2/d^2 gives a k^2/d push.
            val f = kk / (d * d)
            force[i] += ax * f
            force[n + i] += ay * f
            force[j] -= ax * f
            force[n + j] -= ay * f
        }
    }
}

/** `d^2 / k` along each edge. The term that makes a real dependency read as a short wire. */
private fun attract(pos: DoubleArray, force: DoubleArray, links: List<IntArray>, n: Int, k: Double) {
    for (link in links) {
        val i = link[0]
        val j = link[1]
        val ax = pos[i] - pos[j]
        val ay = pos[n + i] - pos[n + j]
        val d = sqrt(ax * ax + ay * ay).coerceAtLeast(COINCIDENT)
        val f = d / k
        force[i] -= ax * f
        force[n + i] -= ay * f
        force[j] += ax * f
        force[n + j] += ay * f
    }
}

/** Applies [force], capped at [temperature], and reports the largest step any node took. */
private fun displace(pos: DoubleArray, force: DoubleArray, n: Int, temperature: Double): Double {
    var largest = 0.0
    for (i in 0 until n) {
        val fx = force[i]
        val fy = force[n + i]
        val d = sqrt(fx * fx + fy * fy)
        if (d < COINCIDENT) continue
        val step = if (d < temperature) d else temperature
        pos[i] += fx / d * step
        pos[n + i] += fy / d * step
        if (step > largest) largest = step
    }
    return largest
}

/**
 * Rescales [settled] and every frame in [frames] so the settled bounding box fills the unit square
 * inset by [FRAME_PADDING].
 *
 * The axes are scaled independently, which stretches the force layout to the frame. That is a
 * deliberate distortion and it is the same one the React canvas applies, since it multiplies the
 * normalised x by the canvas width and y by its height. The alternative, a uniform scale, leaves a
 * band of dead canvas on a wide surface and makes the graph read as smaller than the card it sits
 * in. What is preserved either way is the topology, which is what the figure is claiming.
 */
private fun fitToFrame(settled: DoubleArray, frames: List<DoubleArray>, n: Int) {
    var minX = Double.MAX_VALUE
    var maxX = -Double.MAX_VALUE
    var minY = Double.MAX_VALUE
    var maxY = -Double.MAX_VALUE
    for (i in 0 until n) {
        val x = settled[i]
        val y = settled[n + i]
        if (x < minX) minX = x
        if (x > maxX) maxX = x
        if (y < minY) minY = y
        if (y > maxY) maxY = y
    }
    val span = 1.0 - FRAME_PADDING * 2.0
    val sx = span / (maxX - minX).coerceAtLeast(COINCIDENT)
    val sy = span / (maxY - minY).coerceAtLeast(COINCIDENT)
    val ox = HALF - (minX + maxX) * HALF * sx
    val oy = HALF - (minY + maxY) * HALF * sy
    for (frame in frames) applyFit(frame, n, sx, sy, ox, oy)
    applyFit(settled, n, sx, sy, ox, oy)
}

private fun applyFit(frame: DoubleArray, n: Int, sx: Double, sy: Double, ox: Double, oy: Double) {
    for (i in 0 until n) {
        frame[i] = frame[i] * sx + ox
        frame[n + i] = frame[n + i] * sy + oy
    }
}

/**
 * Numerical Recipes' LCG, on a wrapping [Int].
 *
 * Kotlin's `Int` multiply wraps silently on every target rather than promoting or throwing, which
 * is exactly the modulo-2^32 the generator is defined over, so this is the same stream everywhere.
 */
private class Lcg(seed: Int) {
    private var state: Int = seed

    /** The next value in 0..1. */
    fun nextUnit(): Double {
        state = state * LCG_MULTIPLIER + LCG_INCREMENT
        return (state.toLong() and UINT_MASK).toDouble() / TWO_POW_32
    }
}

// ---------------------------------------------------------------------------------------------
// The prose the canvas cannot say
// ---------------------------------------------------------------------------------------------

/**
 * The whole accessible content of the figure, as one sentence per part.
 *
 * A Canvas exposes no text nodes at all, so without this the constellation is silent: the same
 * reason `MermaidFlow` describes its graph and the forge describes its swarm. Every edge is named,
 * because the edges are the claim the surface makes.
 */
fun describeStoryMap(nodes: List<StoryMapNode>, edges: List<StoryMapEdge>): String {
    val label = nodes.associate { it.id to it.label }
    val wires =
        edges.joinToString("; ") { e -> "${label[e.from] ?: e.from} and ${label[e.to] ?: e.to}" }
    return "A constellation of ${nodes.size} places on this site, laid out as a force-directed " +
        "graph with ${edges.size} links between them. Every link is a real dependency: the " +
        "projects share one foundation, the writing is field notes from the work, and the AI " +
        "assistant has read all of it. Links: $wires. The same destinations are buttons below " +
        "this figure."
}

// ---------------------------------------------------------------------------------------------
// The runnable check
// ---------------------------------------------------------------------------------------------

/**
 * The floor two settled nodes must keep between them, in unit-frame terms.
 *
 * It exists to catch a layout that has COLLAPSED, not to police aesthetics: the widest node draws a
 * glow about 0.05 of the frame across, so anything clearing this is visibly two objects. Today's
 * corpus settles at about 0.147, so the margin is real and a corpus change that halves it is a
 * genuine regression rather than a tuning quibble.
 */
private const val COLLISION_FLOOR: Double = 0.08

/** A settled graph moves less than this on its last iteration. Measured: about 0.0004. */
private const val CONVERGENCE_FLOOR: Double = 0.002

/** How far the settle has to have travelled to count as having run at all. */
private const val MINIMUM_TRAVEL: Double = 0.05

/**
 * ponytail: one runnable check instead of a test module, in the same shape as `navSelfCheck` and
 * `forgeSelfCheck`. Wire it into `selfCheck()` in jvmMain's Prerender.kt — nothing calls it
 * otherwise, and the four properties below are the entire contract the screen leans on.
 */
internal fun storyMapLayoutSelfCheck(
    nodes: List<StoryMapNode>,
    edges: List<StoryMapEdge>,
) {
    val a = layoutStoryMap(nodes, edges)
    val b = layoutStoryMap(nodes, edges)

    // 1. Deterministic. Same seed, same graph, bit-identical settled state — this is what lets the
    //    prerendered page and the live page agree, and what makes the other checks mean anything.
    check(a.settled.contentEquals(b.settled)) { "two runs at the same seed must settle identically" }
    check(a.ids == nodes.map { it.id }) { "frame order must follow the corpus order" }

    // 2. Converged. Not "the loop ended" — the last iteration barely moved anything.
    check(a.settleStep < CONVERGENCE_FLOOR) {
        "layout still moving at the last iteration: ${a.settleStep} >= $CONVERGENCE_FLOOR"
    }

    // 3. It actually ran. A layout that converges by never moving would pass check 2 happily.
    val first = a.frames[0]
    var travelled = 0.0
    for (i in first.indices) travelled += abs(a.settled[i] - first[i])
    check(travelled > MINIMUM_TRAVEL) { "the settle moved nothing: $travelled" }

    // 4. No two nodes on top of each other, and everything inside the frame.
    for (i in 0 until a.size) {
        val x = a.xOf(a.settled, i)
        val y = a.yOf(a.settled, i)
        check(x in 0.0..1.0 && y in 0.0..1.0) { "${a.ids[i]} settled outside the frame at $x,$y" }
        for (j in i + 1 until a.size) {
            val dx = x - a.xOf(a.settled, j)
            val dy = y - a.yOf(a.settled, j)
            val d = sqrt(dx * dx + dy * dy)
            check(d >= COLLISION_FLOOR) { "${a.ids[i]} and ${a.ids[j]} settled $d apart" }
        }
    }

    // 5. The playback is a real interpolation of the frames, with both ends pinned.
    val out = DoubleArray(a.size * 2)
    a.sampleInto(1.0, out)
    check(out.contentEquals(a.settled)) { "progress 1 must be the settled frame" }
    a.sampleInto(0.0, out)
    check(out.contentEquals(a.frames[0])) { "progress 0 must be the first captured frame" }

    // 6. The figure says something, and it names every wire.
    val prose = describeStoryMap(nodes, edges)
    check(prose.contains("${edges.size} links")) { "the description must count the edges" }
    check(nodes.all { prose.contains(it.label) }) { "every node must be named in the description" }
}
