package com.siddharth.cv.shared.labs

import androidx.compose.ui.graphics.Color
import com.siddharth.cv.shared.data.generated.chess
import com.siddharth.cv.shared.data.projects
import com.siddharth.cv.shared.theme.cvColor
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
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
/**
 * How many instruments this build actually ships. Public because the homepage room wall's lab blurb
 * reads it: the blurb used to state a count by hand and named neither this bench's size nor the
 * React one's. A number in prose that nothing computes is a number that goes stale.
 */
val labCount: Int get() = cvLabs.size

internal class LabExperiment(
    val id: String,
    val label: String,
    val metric: String,
    val group: LabGroup,
    val caption: String,
    val description: String,
)

/**
 * How many gateways PaymentsLab catalogs, read out of the project's own metrics rather than typed
 * here. The React lab gets a four-way split (native-SDK / hosted-webview / mobile-money / stub) from
 * `data/projectStats.ts`, which `gen-kotlin-data.mjs` does not emit into Kotlin — so this port draws
 * one shelf of every gateway instead of four bins. That is a real loss of texture and it is the
 * honest one: the alternative was hand-copying four numbers into this file, which is exactly the
 * drift the generator exists to prevent. [labsSelfCheck] cross-checks this against the project's
 * badge list, so the two can't part company silently.
 */
internal val gatewayCount: Int =
    projects
        .firstOrNull { it.slug == "paymentslab" }
        ?.detail
        ?.metrics
        ?.firstOrNull { it.label.contains("gateways") }
        ?.value
        ?.toIntOrNull()
        ?: 0

/** The measured curve, straight from the generated corpus. Ten buckets of game progress. */
internal val clockDeciles = chess.thesis.deciles

/** Where the win/loss curves are furthest apart — the marker's resting place. */
internal val clockPeakDecile = clockDeciles.maxByOrNull { it.gap } ?: clockDeciles.first()

/** The widest win/loss clock gap in the corpus, in points, for the tab metric. */
internal val clockPeakGapPoints: String get() = oneDecimal(clockPeakDecile.gap * 100)

/**
 * Nine of the React bench's eleven instruments. What happened to the other two, and to the three
 * rows of this list that used to be excuses:
 *
 * - **Signal Lab** — the one genuine hold-out. Its visual is a Leaflet map over real tile imagery;
 *   the engine would port in an afternoon, the map would have to be rebuilt from scratch as a
 *   canvas projection over a tile source this port does not carry. Skipped deliberately.
 * - **White-label** — ported, but not onto this bench: it is `playground/ThemeLab.kt`, a section of
 *   the homepage. That version goes *further* than the React lab it came from, which paints its
 *   preview with inline colours; ours re-skins a real subtree through a nested `CvTheme`, which is
 *   the production mechanism rather than a picture of it. A second copy here would be one demo
 *   maintained twice, so this list links to it instead. See [cvLabs]' bench blurb.
 * - **Gateway Lab / Deterministic Replay** — the note here used to read "no obstacle, just not in
 *   this slice", and that was accurate: neither needed anything the port did not already have.
 * - **Chess Search / Clock Burn** — this said both "read the generated chess corpus, which this
 *   repo does not vendor". That was simply wrong: `data/generated/CvChessData.kt` carries the clock
 *   thesis, deciles and all, and Clock Burn cost nothing but a chart. Chess Search *was* expensive,
 *   for a different reason nobody had written down — the React lab gets legal moves from `chess.js`
 *   — and the fix was a 0x88 move generator proved correct by perft. See `ChessEngine.kt`.
 */
internal val cvLabs: List<LabExperiment> = listOf(
    LabExperiment(
        id = "recompose",
        label = "Recomposition",
        metric = "~87% Compose",
        group = LabGroup.Production,
        caption = "Tap any cell. In rebuild-the-world mode one state change repaints the whole " +
            "screen — that is a legacy view tree, and at ~964k LOC it is molasses. Flip to stable " +
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
        id = "gateways",
        label = "Gateway Lab",
        metric = "$gatewayCount gateways",
        group = LabGroup.Personal,
        caption = "Every payment provider ships its own SDK, and most of them are still " +
            "Activity-callback era. Turn the contract off and each checkout call needs a bespoke " +
            "integration written before it can go anywhere. Turn it on and the same call reaches " +
            "any of $gatewayCount cataloged gateways without one line of gateway-specific code.",
        description = "Checkout calls fall from the top of the canvas. Without the " +
            "PaymentGateway contract they stop dead at a barrier and pile up as blocked work. " +
            "With it they pass through a single contract node and fan out to a shelf of " +
            "$gatewayCount gateway ticks along the bottom, lighting whichever one each call " +
            "routed to.",
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
    LabExperiment(
        id = "replay",
        label = "Deterministic Replay",
        metric = "0-tolerance",
        group = LabGroup.Personal,
        caption = "Deadlock's determinism contract in one line: an input frame records intent — a " +
            "move vector, a jump, a dash — and never a position. Replay the same log and you get " +
            "the same path, to the float. Perturb one frame of the second log and the paths split " +
            "from exactly that frame on, which is the whole reason the gate can be zero-tolerance.",
        description = "Two replays of one recorded input log trace a wandering path across the " +
            "canvas. Identical logs draw one line, because the second is exactly under the first. " +
            "Perturbing a single frame of the second log splits it away in red from that frame " +
            "onward, and the readout turns from PASS to a blocked gate with the measured drift.",
    ),
    LabExperiment(
        id = "chess-search",
        label = "Chess Search",
        metric = "alpha-beta",
        group = LabGroup.Personal,
        caption = "The same picture as Kursi's tree, a different algorithm — and this one is not a " +
            "simulation either. Every line is a real edge from an alpha-beta search over a " +
            "position from one of his own games, run here on a legal-move generator this port " +
            "carries and a perft check proves correct. Two more ply is thousands more nodes, and " +
            "still a small fraction of the legal tree: the pruning is the whole trick, and the " +
            "readout counts what it cost.",
        description = "An alpha-beta search tree fanning upward from a single root at the bottom " +
            "of the canvas, replayed edge by edge after the search has already finished. The " +
            "subtree under the move the search played is drawn brighter, and its root edge " +
            "brightest of all. Switching between two and four ply runs a genuinely different " +
            "search, and the readout reports the nodes visited, the move chosen, and which of " +
            "his games the position came from.",
    ),
    LabExperiment(
        id = "chess-clock",
        label = "Clock Burn",
        metric = "+$clockPeakGapPoints pts",
        group = LabGroup.Personal,
        caption = "Two curves, mean share of the starting clock still left, by decile of game " +
            "progress. They sit on top of each other through the opening and come apart in the " +
            "early middlegame. That gap is where the games go: the time is spent long before any " +
            "late blunder, which is why the fix was never studying more endgames.",
        description = "A line chart of mean clock remaining against game progress, in ten " +
            "buckets. The won-games curve stays above the lost-games curve, the shaded band " +
            "between them is the divergence, and a movable marker reads out both values and " +
            "their gap at any bucket.",
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

/**
 * How many events have spawned by [cutoff], given one every [interval] seconds with a per-event
 * sub-slot stagger that [spawnAt] applies.
 *
 * Shared by [CrashFeed] and [GatewayFeed] — both are closed-form feeds over a pre-rolled ring, and
 * this is the trick that makes either O(1) at any `t`: because [spawnAt] is strictly increasing, the
 * answer is within a slot or two of `cutoff / interval`, so a two-step scan from there beats walking
 * the history. Extracted the second time it was needed, not the first.
 */
internal inline fun countSpawnedBy(cutoff: Float, interval: Float, spawnAt: (Int) -> Float): Int {
    if (cutoff < 0f) return 0
    var i = (cutoff / interval).toInt() + 2
    if (i < 0) return 0 // guards a cutoff large enough to overflow the Int cast
    while (i > 0 && spawnAt(i - 1) > cutoff) i--
    while (spawnAt(i) <= cutoff) i++
    return i
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

    private fun countSpawnedBy(cutoff: Float): Int =
        countSpawnedBy(cutoff, SpawnInterval, ::spawnSeconds)

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
// Payment gateways
// -------------------------------------------------------------------------------------------

/**
 * Checkout calls, as a closed-form function of elapsed seconds — the same shape as [CrashFeed],
 * because the argument is the same shape: a feed arriving at a fixed rate that either gets routed or
 * does not.
 *
 * The React lab (`GatewayLab.tsx`) keeps a live array, splices landed calls out of it and increments
 * four bins. Here call `i` spawns at [spawnSeconds] and lands [FallSeconds] later, and "how many
 * have landed" is the same two-step scan. What that buys is the same thing it buys everywhere else
 * on this bench: the reduced-motion still frame is the frozen clock, and the arithmetic is checkable
 * off-screen.
 */
internal class GatewayFeed(seed: Int = 20260812) {
    private val gateway = IntArray(Ring)
    private val xFrac = FloatArray(Ring)
    private val slot = FloatArray(Ring)

    init {
        val rng = Random(seed)
        for (i in 0 until Ring) {
            xFrac[i] = rng.nextFloat()
            slot[i] = rng.nextFloat()
            gateway[i] = if (gatewayCount > 0) rng.nextInt(gatewayCount) else 0
        }
    }

    /** Which of the cataloged gateways call [index] ends up on. */
    fun gatewayOf(index: Int): Int = gateway[index.mod(Ring)]

    fun xFracOf(index: Int): Float = xFrac[index.mod(Ring)]

    fun spawnSeconds(index: Int): Float = (index + slot[index.mod(Ring)]) * SpawnInterval

    fun landedCount(seconds: Float): Int =
        countSpawnedBy(seconds - FallSeconds, SpawnInterval, ::spawnSeconds)

    fun spawnedCount(seconds: Float): Int = countSpawnedBy(seconds, SpawnInterval, ::spawnSeconds)

    /** Calls stopped at the barrier. Same feed, shorter fall — they never reach the shelf. */
    fun blockedCount(seconds: Float): Int =
        countSpawnedBy(seconds - BarrierSeconds, SpawnInterval, ::spawnSeconds)

    companion object {
        /** One checkout every 160ms, matching `GatewayLab.tsx`'s spawn accumulator. */
        const val SpawnInterval: Float = 0.16f

        /** Fall time to the gateway shelf, fixed so a taller canvas drops faster, not for longer. */
        const val FallSeconds: Float = 2.6f

        /** The barrier sits partway down, so a blocked call dies visibly earlier than a routed one. */
        const val BarrierSeconds: Float = FallSeconds * 0.42f

        /** How many recent arrivals stay lit on the shelf, and for how long. */
        const val GlowWindow: Int = 40
        const val GlowSeconds: Float = 1.4f

        /** Where down the fall the contract node sits, as a fraction. Nothing steers above it. */
        const val HubFraction: Float = 0.22f

        private const val Ring = 2048
    }
}

// -------------------------------------------------------------------------------------------
// Deterministic replay
// -------------------------------------------------------------------------------------------

/** One recorded frame of *intent*. Never a position — that is the entire contract. */
internal class InputFrame(val mx: Float, val my: Float, val jump: Boolean, val dash: Boolean)

/**
 * Deadlock's determinism contract, ported whole.
 *
 * The tape is recorded once from a seed and replayed as often as asked. [step] is the fixed-timestep
 * physics tick — `state' = step(state, frame)` — and it is a pure function of its two arguments,
 * which is the property the zero-tolerance gate is checking. [replay] therefore reproduces exactly,
 * and [driftFrom] is the number the gate rejects on.
 *
 * ponytail: the path is recomputed per draw rather than cached against canvas size. It is 160
 * multiply-adds; a cache invalidated on resize would be more code than the work it saves.
 */
internal class ReplayLog(seed: Int = 20260724) {
    val frames: List<InputFrame>

    init {
        val rng = Mulberry32(seed)
        var angle = rng.next() * 2f * PI.toFloat()
        frames =
            List(FrameCount) {
                angle += (rng.next() - RollCentre) * WanderSpread
                InputFrame(cos(angle), sin(angle), rng.next() < JumpChance, rng.next() < DashChance)
            }
    }

    /** The tape with exactly one frame's intent inverted. One frame, nothing else. */
    fun perturbed(): List<InputFrame> =
        frames.mapIndexed { i, f ->
            if (i == PerturbIndex) InputFrame(-f.mx, -f.my, f.jump, f.dash) else f
        }

    /** `x[i]`, `y[i]` is the state after `i` frames. Index 0 is the starting state. */
    fun replay(tape: List<InputFrame>, widthPx: Float, heightPx: Float): Pair<FloatArray, FloatArray> {
        val cx = widthPx * 0.5f
        val cy = heightPx * 0.5f
        val x = FloatArray(PathLength)
        val y = FloatArray(PathLength)
        x[0] = cx
        y[0] = cy
        tape.forEachIndexed { i, frame ->
            val multiplier = if (frame.dash) DashMultiplier else 1f
            val vx = frame.mx * Speed * multiplier + (cx - x[i]) * Pull
            val vy = frame.my * Speed * multiplier + (cy - y[i]) * Pull
            x[i + 1] = x[i] + vx * Dt
            y[i + 1] = y[i] + vy * Dt - if (frame.jump) JumpKick else 0f
        }
        return x to y
    }

    /**
     * Mean divergence between the two replays, from the first frame the edit can reach, expressed in
     * thousands of pixels the way the React readout does. Zero to the float when the tapes match —
     * which is the assertion, not a tolerance.
     */
    fun driftFrom(a: Pair<FloatArray, FloatArray>, b: Pair<FloatArray, FloatArray>): Float {
        var sum = 0f
        var n = 0
        for (i in DivergeAt until PathLength) {
            sum += hypot(a.first[i] - b.first[i], a.second[i] - b.second[i])
            n++
        }
        return if (n == 0) 0f else sum / n / 1000f
    }

    /** Which path point the playhead is on at [seconds]. Loops, like the React lab's. */
    fun playheadAt(seconds: Float): Int =
        (seconds / FrameSeconds).toInt().mod(PathLength)

    /**
     * The tape's shape and the physics constants, in the companion rather than at file scope: this
     * file's top-level names are PascalCase, and detekt's top-level *constant* pattern here is
     * SCREAMING_SNAKE or camelCase — the older PascalCase constants above only pass on the baseline.
     * A companion is checked by the object-property rule instead, which PascalCase satisfies, so the
     * naming stays uniform without a suppression.
     */
    companion object {
        const val FrameCount: Int = 160

        /** The path includes the initial state, so it is one longer than the tape. */
        const val PathLength: Int = FrameCount + 1

        /** Which frame "perturb" edits, and the first path point that edit can possibly reach. */
        const val PerturbIndex: Int = 64
        const val DivergeAt: Int = PerturbIndex + 1

        /** Replay pace: one full loop of the tape in about 6.7 seconds. */
        const val FrameSeconds: Float = 0.042f

        /** The canvas drift is measured against, so a resize cannot move a published number. */
        const val NominalWidth: Float = 640f
        const val NominalHeight: Float = 340f

        /** An unsigned 0..1 roll re-centred, so a recorded turn is as likely left as right. */
        private const val RollCentre: Float = 0.5f

        /** How far the recorded turn can swing per frame, and how often it jumps or dashes. */
        private const val WanderSpread: Float = 0.85f
        private const val JumpChance: Float = 0.05f
        private const val DashChance: Float = 0.06f

        private const val Dt: Float = 1f / 60f
        private const val Speed: Float = 70f

        /** Gentle centering so a wandering tape stays on the canvas instead of leaving it. */
        private const val Pull: Float = 0.6f
        private const val DashMultiplier: Float = 2.1f
        private const val JumpKick: Float = 5f
    }
}

// -------------------------------------------------------------------------------------------
// Clock burn
// -------------------------------------------------------------------------------------------

/** `0-10%`. The axis step is derived, not typed, so a re-generated corpus can change the bucketing. */
internal fun clockBandLabel(bucket: Int): String {
    val step = 100 / clockDeciles.size
    return "${bucket * step}-${(bucket + 1) * step}%"
}

/** `0.0835 -> "8.4"`. There is no `String.format` on wasmJs. */
internal fun oneDecimal(value: Double): String {
    val tenths = (value * 10).roundToInt()
    return "${tenths / 10}.${(if (tenths < 0) -tenths else tenths) % 10}"
}

/** `0.083 -> "8.3%"`, for a corpus figure that arrives as a Double rather than a Float. */
internal fun pctOf(fraction: Double): String = "${oneDecimal(fraction * 100)}%"

// -------------------------------------------------------------------------------------------
// Self-check
// -------------------------------------------------------------------------------------------

/**
 * ponytail: one runnable check instead of a test module, matching `navSelfCheck` /
 * `mermaidParseSelfCheck`. Must be called from `selfCheck()` in jvmMain's Prerender.kt — nothing
 * under composeMain executes on its own, and a check that never runs reads as coverage it isn't.
 *
 * One function per instrument rather than one long one: a bench of nine simulations that fails as
 * a single `labsSelfCheck` tells you only that the bench is broken, and the stack frame that
 * actually names the instrument is worth more than the lines it costs.
 */
internal fun labsSelfCheck() {
    checkBench()
    checkRecomposeHitTesting()
    checkCrashFeed()
    checkFanout()
    checkSearchTree()
    checkModuleGraph()
    checkGatewayFeed()
    checkReplayLog()
    checkClockBurn()
    chessEngineSelfCheck()
}

/** The bench itself, plus the two formatting helpers every readout on it goes through. */
private fun checkBench() {
    check(cvLabs.size == 9) { "nine of the React bench's eleven instruments, was ${cvLabs.size}" }
    check(cvLabs.map { it.id }.toSet().size == cvLabs.size) { "lab ids are unique — the picker keys on them" }
    cvLabs.forEach {
        check(it.caption.length > 60) { "${it.id}: a simulation without an explanation is decoration" }
        check(it.description.length > 60) { "${it.id}: a canvas exposes no text; the description is the only a11y surface" }
    }

    check(pct1(1f / RecomposeCells) == "2.5%") { "one cell of forty" }
    check(pct1(1f) == "100.0%") { "whole" }
    check(smoothstep(0f) == 0f && smoothstep(1f) == 1f) { "easing spans its range" }

}

/** Recomposition hit-testing: the corners, the centre, and everything off-grid. */
private fun checkRecomposeHitTesting() {
    check(recomposeCellAt(1f, 1f, 800f, 500f) == 0) { "top-left cell" }
    check(recomposeCellAt(799f, 499f, 800f, 500f) == RecomposeCells - 1) { "bottom-right cell" }
    check(recomposeCellAt(101f, 101f, 800f, 500f) == RecomposeGridW + 1) { "second row, second column" }
    listOf(-1f to 10f, 10f to -1f, 800f to 10f, 10f to 500f).forEach { (x, y) ->
        check(recomposeCellAt(x, y, 800f, 500f) == -1) { "off-grid tap at $x,$y must miss" }
    }
    check(recomposeCellAt(10f, 10f, 0f, 0f) == -1) { "a zero-size grid can't be hit" }

}

/** Crash feed: monotone, conserved, and skewed the way the story claims. */
private fun checkCrashFeed() {
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

}

/** Fan-out: de-dup keeps exactly one arrival per cluster, and only ever fewer than it saw. */
private fun checkFanout() {
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

}

/** Search tree: a well-formed tree that reveals monotonically and can always walk to its root. */
private fun checkSearchTree() {
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

}

/** Module graph. */
private fun checkModuleGraph() {
    check(moduleFeatures.size == ModuleFeatureCount) { "thirteen feature modules" }
    check(moduleFeatures.count { it.named } == 6) { "six confirmed names, seven honest placeholders" }
    check(moduleCrossEdges.size == 78) { "13 choose 2 — the tangle isolation removes" }
    check(moduleCrossEdges.toSet().size == 78) { "no duplicated pair" }
    check(ModuleOtherCount == 33) { "46 total minus 13 features" }

}

/**
 * Gateways: the count is read out of project data, so the failure mode is a silent 0 — and a
 * renamed metric label. Cross-checked against the badge list, which states it independently.
 */
private fun checkGatewayFeed() {
    check(gatewayCount > 0) { "the paymentslab gateway metric no longer parses as a number" }
    val paymentsLab = projects.first { it.slug == "paymentslab" }
    check(paymentsLab.badges.any { it == "$gatewayCount gateways" }) {
        "the metric says $gatewayCount gateways but the badges disagree: ${paymentsLab.badges}"
    }
    val gateways = GatewayFeed()
    check(gateways.landedCount(0f) == 0) { "no call has routed before the first one falls" }
    check(gateways.blockedCount(0f) == 0) { "no call is blocked before the first one falls" }
    var prevRouted = 0
    var prevBlocked = 0
    var gt = 0f
    val sweepSeconds = 300f
    // Deliberately not a multiple of the 0.16s spawn interval, so the samples land off-beat.
    val sweepStep = 0.31f
    while (gt < sweepSeconds) {
        val routed = gateways.landedCount(gt)
        val blocked = gateways.blockedCount(gt)
        val spawned = gateways.spawnedCount(gt)
        check(routed >= prevRouted && blocked >= prevBlocked) { "gateway counters only grow (t=$gt)" }
        // The barrier is nearer than the shelf, so the blocked run is always ahead of the routed one.
        check(blocked >= routed) { "a call cannot route before it could have been blocked (t=$gt)" }
        check(spawned >= blocked) { "a call cannot be blocked before it spawns (t=$gt)" }
        check(gateways.gatewayOf(routed) in 0 until gatewayCount) { "a call routed off the shelf" }
        prevRouted = routed
        prevBlocked = blocked
        gt += sweepStep
    }
    val wrapped = gateways.landedCount(20_000f)
    check(wrapped > 2048) { "a long run wraps the gateway ring" }

}

/**
 * Replay: the whole claim is that identical tapes reproduce to the float, and that one edited
 * frame changes the output from exactly that frame on and never before it.
 */
private fun checkReplayLog() {
    val log = ReplayLog()
    check(log.frames.size == ReplayLog.FrameCount) { "the tape is ${log.frames.size} frames, not ${ReplayLog.FrameCount}" }
    val clean = log.replay(log.frames, 640f, 340f)
    val again = log.replay(log.frames, 640f, 340f)
    check(clean.first.contentEquals(again.first) && clean.second.contentEquals(again.second)) {
        "two replays of one tape must be bit-identical, or the gate has nothing to check"
    }
    check(log.driftFrom(clean, again) == 0f) { "identical tapes drift by zero, not by epsilon" }
    val edited = log.replay(log.perturbed(), 640f, 340f)
    for (i in 0..ReplayLog.PerturbIndex) {
        check(clean.first[i] == edited.first[i] && clean.second[i] == edited.second[i]) {
            "the edit reached backwards to frame $i, which a fixed-timestep replay cannot do"
        }
    }
    check(clean.first[ReplayLog.DivergeAt] != edited.first[ReplayLog.DivergeAt]) {
        "editing a frame must change the very next state, or the gate would pass a real change"
    }
    check(log.driftFrom(clean, edited) > 0f) { "a perturbed tape has to register drift" }
    check(log.perturbed().count { f -> log.frames.none { it.mx == f.mx && it.my == f.my } } <= 1) {
        "perturb edits one frame, not a range"
    }
    check(log.playheadAt(0f) == 0) { "the playhead starts at the recorded initial state" }
    check(log.playheadAt(ReplayLog.FrameSeconds * ReplayLog.PathLength) == 0) { "and wraps at the end of the tape" }
    check(log.playheadAt(LabStillSeconds) > ReplayLog.DivergeAt) {
        "the still frame should sit past the split, where both paths are visible"
    }

}

/** Clock burn: the corpus decides the shape, so the checks are on properties, not on values. */
private fun checkClockBurn() {
    check(clockDeciles.size == 10) { "the clock thesis is bucketed by decile" }
    clockDeciles.forEachIndexed { i, d ->
        check(d.bucket == i) { "decile $i is labelled ${d.bucket} — the chart plots by position" }
        check(d.win in 0.0..1.0 && d.loss in 0.0..1.0) { "decile $i is not a fraction of a clock" }
        check(oneDecimal(d.gap * 100).isNotEmpty()) { "decile $i's gap does not format" }
    }
    // The thesis itself: more clock left in wins than in losses, everywhere, and the gap opens
    // rather than being there from move one. If a regenerated corpus breaks this, the caption lies.
    check(clockDeciles.all { it.win >= it.loss }) { "a decile where losses kept more clock than wins" }
    check(clockDeciles.first().gap < clockPeakDecile.gap / 2) { "the curves have to start together" }
    check(clockPeakDecile.bucket in 4..8) { "the gap should peak in the middlegame, not the opening" }
    val roundsUp = 8.35
    val negativeGap = -1.24
    check(oneDecimal(0.0) == "0.0" && oneDecimal(roundsUp) == "8.4" && oneDecimal(negativeGap) == "-1.2") {
        "one-decimal formatting, including the negative case a subtraction can produce"
    }
    val clockShare = 0.083
    check(pctOf(clockShare) == "8.3%") { "percentage formatting" }
    check(clockBandLabel(0) == "0-10%" && clockBandLabel(9) == "90-100%") { "band labels span the game" }
}
