package com.siddharth.cv.shared.labs

import androidx.compose.ui.graphics.Color
import com.siddharth.cv.shared.theme.cvColor
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The Lab Bench's data and simulation engines — the port of cv-siddharth/src/LabBench.tsx plus the
 * arithmetic out of the files under `src/labs/`.
 *
 * (That path is deliberately not written as a glob. Kotlin block comments NEST, so a `/` immediately
 * followed by a `*` inside a KDoc opens a nested comment which this block's own terminator then
 * closes — leaving the KDoc itself open and swallowing every declaration below it. The compiler
 * reports that as "Unclosed comment" on the file's last line, hundreds of lines from the cause.)
 *
 * **Nothing here is `@Composable`, and nothing here holds mutable frame state.** Every engine is a
 * pure function of *elapsed seconds*, which buys three things the React originals pay for
 * separately:
 *
 * 1. **Reduced motion is free.** The React bench fakes a still frame by running its `step(16)`
 *    loop 900 times and drawing once (`useCanvasLoop.ts:36-40`). Here, freezing the clock at
 *    [LabStillSeconds] *is* the still frame — no warm-up path, no second code path to keep honest.
 * 2. **One ticker, no per-experiment loop.** A `dt`-accumulating simulation needs its own driver;
 *    a function of `t` just needs `t`.
 * 3. **It is checkable.** [labsSelfCheck] runs the branchy parts on the JVM, which a canvas
 *    simulation wired to `requestAnimationFrame` never can be.
 *
 * The cost is that history can't be replayed — flipping a switch mid-run can't retroactively
 * re-bin what already landed. Where that matters (crash triage, scan restarts) the screen shifts a
 * time *epoch* instead, which reads as "re-run the feed from here". Documented at each call site.
 */

// -------------------------------------------------------------------------------------------
// The bench
// -------------------------------------------------------------------------------------------

internal enum class LabGroup(val label: String) {
    Production("Dice.tech — production"),
    Personal("Personal builds"),
}

/**
 * One instrument on the bench.
 *
 * [caption] is not decoration and not optional: a simulation nobody can read is a screensaver.
 * [description] is the same content for a screen reader, because a Compose Canvas exposes exactly
 * zero text nodes — whatever isn't said here is not said at all.
 */
internal class LabExperiment(
    val id: String,
    val label: String,
    val metric: String,
    val group: LabGroup,
    val caption: String,
    val description: String,
)

/**
 * Five of the React bench's nine instruments. The four that didn't come across:
 *
 * - **Signal Lab** — its visual is a Leaflet map with real tile imagery; the engine would port
 *   fine, the map would have to be rebuilt from scratch as a canvas projection. Skipped
 *   deliberately, not blocked.
 * - **White-label / Gateway / Deterministic Replay** — no obstacle, just not in this slice.
 */
internal val cvLabs: List<LabExperiment> = listOf(
    LabExperiment(
        id = "recompose",
        label = "Recomposition",
        metric = "~87% Compose",
        group = LabGroup.Production,
        caption = "Tap any cell. In rebuild-the-world mode one state change repaints the whole " +
            "screen — that is a legacy view tree, and at ~960k LOC it is molasses. Flip to stable " +
            "state and only the touched cell recomposes. That is what the ~87% migration bought.",
        description = "A grid of 40 cells standing in for recomposition scopes. Each tap flashes " +
            "the scopes that repaint: all forty in rebuild-the-world mode, one in stable-state " +
            "mode. The counters below tally wasted repaints against needed ones.",
    ),
    LabExperiment(
        id = "crashes",
        label = "Crash Triage",
        metric = "-80%",
        group = LabGroup.Production,
        caption = "A production crash feed doesn't arrive labelled — it arrives as noise. Turn " +
            "clustering on and the same feed sorts itself by root cause. The skew is the whole " +
            "point: fix the top two clusters and most of the noise disappears.",
        description = "Crash traces fall down the canvas as dots. Unclustered they pile into one " +
            "undifferentiated slab. Clustered, each trace steers into one of five root-cause bins " +
            "— main-thread I/O, coroutine race, lifecycle leak, bitmap OOM, OEM quirk — and the " +
            "first two bins together hold about 80% of the feed.",
    ),
    LabExperiment(
        id = "modules",
        label = "Module Graph",
        metric = "46 modules",
        group = LabGroup.Personal,
        caption = "Mileway's 46-module Gradle graph: thirteen feature modules — tracking, " +
            "logging, travel, approvals, payables, agent and seven more — that never depend on " +
            "each other, wired together only at the :app composition root. Turn isolation off " +
            "to see the alternative: every feature reaching into every other one.",
        description = "A radial dependency graph. Thirteen feature modules sit on an inner ring, " +
            "each joined by a single spoke to the :app composition root at the centre, with 33 " +
            "further shared and composed modules as an outer ring of dots. Turning isolation off " +
            "draws all 78 feature-to-feature edges the composition root replaces.",
    ),
    LabExperiment(
        id = "search",
        label = "Search Tree",
        metric = "10 personas",
        group = LabGroup.Personal,
        caption = "Kursi's bots never see your hand — they play with Information Set Monte Carlo " +
            "Tree Search, growing a tree of plausible futures over the hidden cards and picking " +
            "the branch that wins most often. Harder tiers search deeper: 1,500 iterations on " +
            "Easy, 16,000 on Grandmaster, still landing on one bot's actual move.",
        description = "An ISMCTS search tree growing upward from a single root at the bottom of " +
            "the canvas. Branches accumulate as the iteration counter climbs toward the selected " +
            "difficulty tier's target, and when the search completes one root-to-leaf line is " +
            "highlighted as the move chosen, labelled with the bot persona that played it.",
    ),
    LabExperiment(
        id = "fanout",
        label = "Provider Fan-out",
        metric = "62 providers",
        group = LabGroup.Personal,
        caption = "62 ATS and job-board providers, one query. Greenhouse, Ashby and Lever are hit " +
            "directly by structured APIs — zero LLM cost — while listings fan back in toward a " +
            "collection zone. The same posting often comes back from more than one board; SimHash " +
            "fingerprinting is what tells those apart from something actually new.",
        description = "A ring of 62 provider dots around a central query point, three of them " +
            "labelled Greenhouse, Ashby and Lever. Listings travel along curves from the ring " +
            "into a collection zone at the bottom. With SimHash de-duplication on, a listing " +
            "already seen from another board collapses into the existing one with a ring flash " +
            "instead of adding a new dot.",
    ),
)

/**
 * Where a frozen clock parks. Chosen so that *every* experiment reads well at this one instant:
 * the crash feed has ~58 traces binned and ~25 in flight, the fan-out is mid-flight through its
 * third scan, and the search tree has finished and drawn its chosen line.
 */
internal const val LabStillSeconds: Float = 11.5f

/** `0.025f -> "2.5%"`. There is no `String.format` on wasmJs, and no Locale to get wrong. */
internal fun pct1(fraction: Float): String {
    val tenths = (fraction * 1000f).roundToInt()
    return "${tenths / 10}.${tenths % 10}%"
}

/** The cubic smoothstep the React labs use as their easing (`FanoutLab.tsx:25`). */
internal fun smoothstep(t: Float): Float {
    val c = t.coerceIn(0f, 1f)
    return c * c * (3f - 2f * c)
}

// -------------------------------------------------------------------------------------------
// Recomposition
// -------------------------------------------------------------------------------------------

internal const val RecomposeGridW: Int = 8
internal const val RecomposeGridH: Int = 5
internal const val RecomposeCells: Int = RecomposeGridW * RecomposeGridH

/** How long a scope stays lit after it repaints. */
internal const val RecomposeFlashSeconds: Float = 0.55f

/**
 * Which cell a pointer at [x],[y] hit inside a [w] by [h] grid, or -1 outside it. Lives here
 * rather than in the draw code because hit-testing is the one place that experiment can be wrong
 * in a way nobody notices — an off-by-one column reads as "the tap didn't register".
 */
internal fun recomposeCellAt(x: Float, y: Float, w: Float, h: Float): Int {
    if (w <= 0f || h <= 0f || x < 0f || y < 0f || x >= w || y >= h) return -1
    val col = (x / (w / RecomposeGridW)).toInt().coerceIn(0, RecomposeGridW - 1)
    val row = (y / (h / RecomposeGridH)).toInt().coerceIn(0, RecomposeGridH - 1)
    return row * RecomposeGridW + col
}

// -------------------------------------------------------------------------------------------
// Crash triage
// -------------------------------------------------------------------------------------------

internal class CrashCause(val id: String, val color: Color)

internal val crashCauses: List<CrashCause> = listOf(
    CrashCause("main-thread I/O", cvColor("#f0883e")),
    CrashCause("coroutine race", cvColor("#ff5c5c")),
    CrashCause("lifecycle leak", cvColor("#db61ff")),
    CrashCause("bitmap OOM", cvColor("#5ee6ff")),
    CrashCause("OEM quirk", cvColor("#8ff0b4")),
)

/**
 * The crash feed, as a closed-form function of elapsed seconds.
 *
 * A fixed ring of [Ring] pre-rolled traces is generated once from a seed, and the feed simply reads
 * further into it as time passes — trace `i` spawns at [spawnSeconds] and lands [FallSeconds]
 * later, wrapping through the ring for a run longer than ~5 minutes. Because spawn times are
 * strictly increasing, "how many have landed by now" is a two-step scan around the obvious index
 * rather than a walk over the whole history, and "how many of those were cause `c`" is a prefix-sum
 * lookup. Both are O(1) at any `t`, which is what makes a per-frame recomputation cheaper than the
 * bookkeeping it replaces.
 */
internal class CrashFeed(seed: Int = 20260729) {
    private val cause = IntArray(Ring)
    private val xFrac = FloatArray(Ring)

    /** Sub-slot stagger, `0..1` of a spawn interval — without it every dot falls in lockstep. */
    private val slot = FloatArray(Ring)

    /** `prefix[c][n]` = how many of the first `n` traces had cause `c`. */
    private val prefix = Array(crashCauses.size) { IntArray(Ring + 1) }

    init {
        val rng = Random(seed)
        for (i in 0 until Ring) {
            xFrac[i] = rng.nextFloat()
            slot[i] = rng.nextFloat()
            cause[i] = pickCause(rng.nextFloat())
        }
        for (c in crashCauses.indices) {
            val row = prefix[c]
            for (i in 0 until Ring) row[i + 1] = row[i] + if (cause[i] == c) 1 else 0
        }
    }

    fun causeOf(index: Int): Int = cause[index.mod(Ring)]

    fun xFracOf(index: Int): Float = xFrac[index.mod(Ring)]

    /** Strictly increasing in [index], which is the property both counters below rely on. */
    fun spawnSeconds(index: Int): Float = (index + slot[index.mod(Ring)]) * SpawnInterval

    /** Traces that have already hit the floor — the ones in the pile or the bins. */
    fun landedCount(seconds: Float): Int = countSpawnedBy(seconds - FallSeconds)

    /** Traces that exist at all. `landedCount until this` is the in-flight window. */
    fun spawnedCount(seconds: Float): Int = countSpawnedBy(seconds)

    fun binCount(causeIndex: Int, landed: Int): Int {
        if (landed <= 0) return 0
        val row = prefix[causeIndex]
        return (landed / Ring) * row[Ring] + row[landed % Ring]
    }

    /** The headline: the two worst clusters as a percentage of everything binned. */
    fun topTwoSharePercent(landed: Int): Int {
        if (landed <= 0) return 0
        return ((binCount(0, landed) + binCount(1, landed)) * 100f / landed).roundToInt()
    }

    private fun countSpawnedBy(cutoff: Float): Int {
        if (cutoff < 0f) return 0
        var i = (cutoff / SpawnInterval).toInt() + 2
        if (i < 0) return 0 // guards a cutoff large enough to overflow the Int cast
        while (i > 0 && spawnSeconds(i - 1) > cutoff) i--
        while (spawnSeconds(i) <= cutoff) i++
        return i
    }

    companion object {
        /** One trace every 140ms, matching `CrashLab.tsx`'s spawn accumulator. */
        const val SpawnInterval: Float = 0.14f

        /**
         * Fall time is fixed rather than a fixed velocity, so a tall canvas drops faster instead of
         * taking longer. Keeps [landedCount] independent of layout — a resize must not retroactively
         * change how many crashes have been triaged.
         */
        const val FallSeconds: Float = 3.4f

        private const val Ring = 2048

        /**
         * Real feeds are two bugs and a long tail. The top two — main-thread I/O and coroutine race
         * — sum to exactly 80%, which is how -80% actually happened once those two clusters got
         * fixed. Do not smooth this out; the skew *is* the lesson.
         */
        fun pickCause(roll: Float): Int = when {
            roll < 0.50f -> 0
            roll < 0.80f -> 1
            roll < 0.92f -> 2
            roll < 0.98f -> 3
            else -> 4
        }
    }
}

// -------------------------------------------------------------------------------------------
// Provider fan-out
// -------------------------------------------------------------------------------------------

internal const val FanoutProviders: Int = 62

internal val fanoutNamed: List<String> = listOf("Greenhouse", "Ashby", "Lever")

/** Spread evenly around the 62-dot ring so the three named integrations don't clump. */
internal val fanoutNamedIndexes: List<Int> = listOf(0, 21, 41)

/** A scan runs, drains, and the next one starts. Comfortably longer than any generated span. */
internal const val FanoutScanPeriod: Float = 5f

internal class FanoutPulse(
    val provider: Int,
    val cluster: Int,
    /** Target inside the collection zone, as fractions of its box. */
    val tx: Float,
    val ty: Float,
    val waitSeconds: Float,
    val flightSeconds: Float,
    /** The arrival SimHash keeps. `false` means this copy gets absorbed into an existing listing. */
    val first: Boolean,
) {
    val landsAt: Float get() = waitSeconds + flightSeconds
}

/**
 * One scan of HireSignal's 62 providers, generated from [seed] so scan *n* is always scan *n*.
 *
 * Most postings come back from one board; a handful come back from two or three at once, which is
 * the near-duplicate case SimHash exists for. Which copy of a cluster is the keeper depends on
 * *landing* order rather than dispatch order, so it can only be decided after generation — hence
 * the sort.
 */
internal class FanoutScan(seed: Int) {
    val pulses: List<FanoutPulse>
    val clusterCount: Int

    /** When the last listing lands, i.e. when the scan is done. */
    val spanSeconds: Float

    init {
        val rng = Random(seed * 7919 + 13)
        clusterCount = 20 + rng.nextInt(6)
        val raw = ArrayList<FanoutPulse>()
        var order = 0
        for (c in 0 until clusterCount) {
            val roll = rng.nextFloat()
            val size = if (roll < 0.60f) 1 else if (roll < 0.86f) 2 else 3
            val bx = 0.05f + rng.nextFloat() * 0.90f
            for (k in 0 until size) {
                raw += FanoutPulse(
                    provider = rng.nextInt(FanoutProviders),
                    cluster = c,
                    tx = (bx + (rng.nextFloat() - 0.5f) * 0.03f).coerceIn(0.03f, 0.97f),
                    ty = 0.5f + (rng.nextFloat() - 0.5f) * 0.55f,
                    waitSeconds = order * (0.030f + rng.nextFloat() * 0.020f),
                    flightSeconds = 0.9f + rng.nextFloat() * 0.6f,
                    first = true,
                )
                order++
            }
        }
        val seen = HashSet<Int>()
        pulses = raw.sortedBy { it.landsAt }
            .map { p ->
                // `add` returns true only for a cluster's first arrival — that's the keeper.
                FanoutPulse(p.provider, p.cluster, p.tx, p.ty, p.waitSeconds, p.flightSeconds, seen.add(p.cluster))
            }
        spanSeconds = pulses.lastOrNull()?.landsAt ?: 0f
    }

    fun landedAt(seconds: Float): Int = pulses.count { it.landsAt <= seconds }

    fun uniqueAt(seconds: Float): Int = pulses.count { it.landsAt <= seconds && it.first }
}

// -------------------------------------------------------------------------------------------
// ISMCTS search tree
// -------------------------------------------------------------------------------------------

internal class SearchTier(val label: String, val iterations: Int)

/** The real tiers from the `kursi` profile entry: 1.5k on Easy to 16k on Grandmaster. */
internal val searchTiers: List<SearchTier> = listOf(
    SearchTier("Easy", 1500),
    SearchTier("Normal", 4000),
    SearchTier("Hard", 8000),
    SearchTier("Expert", 12000),
    SearchTier("Grandmaster", 16000),
)

/**
 * The six bot personas the source data actually names. The card says ten; the other four aren't
 * enumerated anywhere, and inventing names to fill a ring would be fabrication.
 */
internal val kursiRoles: List<String> = listOf(
    "Netaji Vachan",
    "Bhai Teja",
    "Babu Filewala",
    "Jugaadu Chhotu",
    "Vakil Loophole",
    "Patrakaar",
)

internal class SearchNode(
    val x: Float,
    val y: Float,
    val angle: Float,
    val depth: Int,
    /** -1 for the root. Always less than this node's own index, so a walk to the root terminates. */
    val parent: Int,
)

internal class SearchTreeRun(
    val nodes: List<SearchNode>,
    /** The deepest line the search explored — the move it settles on. */
    val chosen: Int,
    val durationSeconds: Float,
    val iterations: Int,
    val tierLabel: String,
    val role: String,
) {
    fun progressAt(seconds: Float): Float = (seconds / durationSeconds).coerceIn(0f, 1f)

    /** How much of the tree exists yet. Grows linearly with the iteration counter. */
    fun revealedAt(seconds: Float): Int {
        val ratio = progressAt(seconds)
        val want = (1f + (nodes.size - 1) * ratio).roundToInt()
        return want.coerceIn(1, nodes.size)
    }

    fun iterationsAt(seconds: Float): Int = (iterations * progressAt(seconds)).roundToInt()

    fun isFinished(seconds: Float): Boolean = progressAt(seconds) >= 1f

    /** [chosen] back to the root, leaf-first. */
    fun chain(): List<Int> {
        val out = ArrayList<Int>()
        var cur = chosen
        while (cur != -1) {
            out += cur
            cur = nodes[cur].parent
        }
        return out
    }
}

/**
 * Grows the whole tree up front from a seed, then [SearchTreeRun.revealedAt] animates the reveal.
 *
 * The React version grows it live inside the frame loop, which means the tree is a different tree
 * every time it's resized. Building once against a known canvas size is both more honest (a resize
 * re-lays-out the same search, it doesn't re-run it) and the only shape that could be checked.
 */
internal fun buildSearchTreeRun(
    tierIndex: Int,
    runIndex: Int,
    widthPx: Float,
    heightPx: Float,
): SearchTreeRun {
    val tier = searchTiers[tierIndex.coerceIn(searchTiers.indices)]
    val rng = Random(tierIndex * 977 + runIndex * 31 + 7)
    val margin = 16f
    val w = maxOf(widthPx, margin * 4f)
    val h = maxOf(heightPx, margin * 4f)

    val nodes = ArrayList<SearchNode>()
    nodes += SearchNode(w / 2f, h - 20f, -PI.toFloat() / 2f, 0, -1)
    val frontier = ArrayList<Int>()
    frontier += 0

    // Node count tracks sqrt(iterations): a 10x deeper search is not 10x more drawable branches.
    val cap = min(380, (3f * sqrt(tier.iterations.toFloat())).roundToInt())
    while (nodes.size < cap && frontier.isNotEmpty()) {
        val parentIdx = frontier[rng.nextInt(frontier.size)]
        val parent = nodes[parentIdx]
        var angle = parent.angle + (55f * PI.toFloat() / 180f) * (rng.nextFloat() - 0.5f)
        val len = maxOf(3f, 20f * 0.94f.pow(parent.depth))
        var nx = parent.x + kotlin.math.cos(angle) * len
        if (nx < margin || nx > w - margin) {
            // Reflect off the wall rather than clamp — a clamped branch stacks into a vertical bar.
            angle = PI.toFloat() - angle
            nx = parent.x + kotlin.math.cos(angle) * len
        }
        val ny = parent.y + kotlin.math.sin(angle) * len
        val reachedTop = ny < margin

        nodes += SearchNode(nx, maxOf(margin, ny), angle, parent.depth + 1, parentIdx)
        val childIdx = nodes.lastIndex
        if (!reachedTop) frontier += childIdx
        if (rng.nextFloat() < 0.35f) {
            // `remove(Int)` on a MutableList<Int> is the classic index-vs-element trap. Explicit.
            val pos = frontier.indexOf(parentIdx)
            if (pos != -1) frontier.removeAt(pos)
        }
        if (frontier.isEmpty()) frontier += childIdx
    }

    var chosen = 0
    var bestDepth = -1
    nodes.forEachIndexed { i, n ->
        if (n.depth > bestDepth || (n.depth == bestDepth && rng.nextFloat() < 0.4f)) {
            bestDepth = n.depth
            chosen = i
        }
    }

    return SearchTreeRun(
        nodes = nodes,
        chosen = chosen,
        durationSeconds = 1.6f + (tier.iterations - 1500f) / (16000f - 1500f) * 1.8f,
        iterations = tier.iterations,
        tierLabel = tier.label,
        role = kursiRoles[runIndex.mod(kursiRoles.size)],
    )
}

// -------------------------------------------------------------------------------------------
// Module graph
// -------------------------------------------------------------------------------------------

internal const val ModuleTotal: Int = 46
internal const val ModuleFeatureCount: Int = 13
internal const val ModuleOtherCount: Int = ModuleTotal - ModuleFeatureCount

/**
 * Interleaved so the six names confirmed in Mileway's architecture diagram don't clump on one side
 * of the circle. The other seven feature modules are real; their names are not in the source data,
 * so they stay generic rather than invented.
 */
private val moduleFeatureLabels = listOf(
    "tracking", "feature", "logging", "feature", "travel", "feature",
    "approvals", "feature", "payables", "feature", "agent", "feature", "feature",
)

internal class ModuleFeature(val label: String, val named: Boolean, val angle: Float)

internal val moduleFeatures: List<ModuleFeature> = moduleFeatureLabels.mapIndexed { i, label ->
    ModuleFeature(
        label = label,
        named = label != "feature",
        angle = 2f * PI.toFloat() * i / ModuleFeatureCount - PI.toFloat() / 2f,
    )
}

/** All 78 feature-to-feature pairs — the tangle the `:app` composition root replaces. */
internal val moduleCrossEdges: List<Pair<Int, Int>> = buildList {
    for (i in 0 until ModuleFeatureCount) {
        for (j in i + 1 until ModuleFeatureCount) add(i to j)
    }
}

// -------------------------------------------------------------------------------------------
// Self-check
// -------------------------------------------------------------------------------------------

/**
 * ponytail: one runnable check instead of a test module, matching `navSelfCheck` /
 * `mermaidParseSelfCheck`. Must be called from `selfCheck()` in jvmMain's Prerender.kt — nothing
 * under composeMain executes on its own, and a check that never runs reads as coverage it isn't.
 */
internal fun labsSelfCheck() {
    // The bench itself.
    check(cvLabs.size == 5) { "five instruments ported" }
    check(cvLabs.map { it.id }.toSet().size == cvLabs.size) { "lab ids are unique — the picker keys on them" }
    cvLabs.forEach {
        check(it.caption.length > 60) { "${it.id}: a simulation without an explanation is decoration" }
        check(it.description.length > 60) { "${it.id}: a canvas exposes no text; the description is the only a11y surface" }
    }

    check(pct1(1f / RecomposeCells) == "2.5%") { "one cell of forty" }
    check(pct1(1f) == "100.0%") { "whole" }
    check(smoothstep(0f) == 0f && smoothstep(1f) == 1f) { "easing spans its range" }

    // Recomposition hit-testing: the corners, the centre, and everything off-grid.
    check(recomposeCellAt(1f, 1f, 800f, 500f) == 0) { "top-left cell" }
    check(recomposeCellAt(799f, 499f, 800f, 500f) == RecomposeCells - 1) { "bottom-right cell" }
    check(recomposeCellAt(101f, 101f, 800f, 500f) == RecomposeGridW + 1) { "second row, second column" }
    listOf(-1f to 10f, 10f to -1f, 800f to 10f, 10f to 500f).forEach { (x, y) ->
        check(recomposeCellAt(x, y, 800f, 500f) == -1) { "off-grid tap at $x,$y must miss" }
    }
    check(recomposeCellAt(10f, 10f, 0f, 0f) == -1) { "a zero-size grid can't be hit" }

    // Crash feed: monotone, conserved, and skewed the way the story claims.
    val feed = CrashFeed()
    check(feed.landedCount(0f) == 0) { "nothing has landed before the first trace falls" }
    check(feed.landedCount(CrashFeed.FallSeconds - 0.01f) == 0) { "the first trace is still in flight" }
    var prevLanded = 0
    var prevSpawned = 0
    var t = 0f
    while (t < 400f) {
        val landed = feed.landedCount(t)
        val spawned = feed.spawnedCount(t)
        check(landed >= prevLanded) { "landed count can only grow (t=$t)" }
        check(spawned >= prevSpawned) { "spawn count can only grow (t=$t)" }
        check(landed <= spawned) { "a trace cannot land before it spawns (t=$t)" }
        check(crashCauses.indices.sumOf { feed.binCount(it, landed) } == landed) {
            "every landed trace is in exactly one bin (t=$t)"
        }
        prevLanded = landed
        prevSpawned = spawned
        t += 0.37f
    }
    check(prevSpawned - prevLanded in 15..40) { "the in-flight window stays a screenful, not a swarm" }
    // Beyond one ring's worth the prefix sums have to wrap correctly or the bins silently stall.
    val deep = feed.landedCount(20_000f)
    check(deep > 2048) { "a long run wraps the ring" }
    check(crashCauses.indices.sumOf { feed.binCount(it, deep) } == deep) { "wrapped bins still conserve" }
    val share = feed.topTwoSharePercent(deep)
    check(share in 76..84) { "top two clusters are ~80% of the feed, was $share%" }
    check(CrashFeed.pickCause(0f) == 0 && CrashFeed.pickCause(0.99f) == crashCauses.lastIndex) {
        "the skew covers both ends"
    }

    // Fan-out: de-dup keeps exactly one arrival per cluster, and only ever fewer than it saw.
    for (seed in 0 until 6) {
        val scan = FanoutScan(seed)
        check(scan.pulses.size >= scan.clusterCount) { "every cluster lands at least once" }
        check(scan.spanSeconds < FanoutScanPeriod) { "a scan finishes inside its period (seed $seed)" }
        var prevTotal = 0
        var prevUnique = 0
        var s = 0f
        while (s <= FanoutScanPeriod) {
            val total = scan.landedAt(s)
            val unique = scan.uniqueAt(s)
            check(total >= prevTotal && unique >= prevUnique) { "arrivals only accumulate" }
            check(unique <= total) { "de-dup can never invent a listing" }
            prevTotal = total
            prevUnique = unique
            s += 0.1f
        }
        check(prevTotal == scan.pulses.size) { "the whole scan lands within its period" }
        check(prevUnique == scan.clusterCount) { "exactly one survivor per cluster" }
        check(prevUnique < prevTotal) { "there were duplicates to collapse in the first place" }
    }

    // Search tree: a well-formed tree that reveals monotonically and can always walk to its root.
    searchTiers.indices.forEach { tier ->
        val run = buildSearchTreeRun(tier, tier, 640f, 340f)
        check(run.nodes.size > 20) { "${run.tierLabel}: the tree actually grew" }
        run.nodes.forEachIndexed { i, n ->
            check(n.parent < i) { "${run.tierLabel}: node $i's parent must precede it or the walk loops" }
        }
        check(run.nodes[0].parent == -1) { "the root has no parent" }
        val chain = run.chain()
        check(chain.last() == 0) { "${run.tierLabel}: the chosen line reaches the root" }
        check(chain.size == run.nodes[run.chosen].depth + 1) { "the chain is exactly one node per level" }
        check(run.revealedAt(0f) == 1) { "a search starts at the root alone" }
        check(run.revealedAt(run.durationSeconds) == run.nodes.size) { "and finishes whole" }
        check(run.iterationsAt(0f) == 0 && run.iterationsAt(run.durationSeconds) == run.iterations) {
            "${run.tierLabel}: the counter spans 0..target"
        }
        check(!run.isFinished(run.durationSeconds * 0.5f) && run.isFinished(LabStillSeconds)) {
            "${run.tierLabel}: frozen clocks land on a completed search"
        }
    }
    // Same seed, same tree — the whole point of building it from a seed rather than a live loop.
    val a = buildSearchTreeRun(2, 3, 640f, 340f)
    val b = buildSearchTreeRun(2, 3, 640f, 340f)
    check(a.nodes.size == b.nodes.size && a.chosen == b.chosen && a.role == b.role) { "runs reproduce" }

    // Module graph.
    check(moduleFeatures.size == ModuleFeatureCount) { "thirteen feature modules" }
    check(moduleFeatures.count { it.named } == 6) { "six confirmed names, seven honest placeholders" }
    check(moduleCrossEdges.size == 78) { "13 choose 2 — the tangle isolation removes" }
    check(moduleCrossEdges.toSet().size == 78) { "no duplicated pair" }
    check(ModuleOtherCount == 33) { "46 total minus 13 features" }
}
