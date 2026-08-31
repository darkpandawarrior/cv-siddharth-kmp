@file:OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

package com.siddharth.cv.shared.ops

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.siddharth.cv.shared.data.generated.fleetStats
import com.siddharth.cv.shared.data.generated.liveClients
import com.siddharth.cv.shared.data.generated.opsDrift
import com.siddharth.cv.shared.data.generated.opsGeneratedAt
import com.siddharth.cv.shared.data.generated.opsLeverage
import com.siddharth.cv.shared.data.generated.opsPerimeter
import com.siddharth.cv.shared.data.generated.storeGeneratedAt
import com.siddharth.cv.shared.theme.CvContentMaxWidth
import com.siddharth.cv.shared.theme.CvGutter
import com.siddharth.cv.shared.theme.CvMotion
import com.siddharth.cv.shared.theme.LocalReducedMotion
import com.siddharth.cv.shared.theme.MonoMeta
import com.siddharth.cv.shared.theme.SectionEyebrow
import com.siddharth.cv.shared.theme.SectionHeading
import com.siddharth.cv.shared.theme.cvColor
import com.siddharth.cv.shared.theme.cvColors
import com.siddharth.cv.shared.theme.cvType
import com.siddharth.cv.shared.theme.rememberInfiniteFloat
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * /ops, the port of cv-siddharth/src/OpsBoard.tsx. A control loop rendered as a page.
 *
 * Every other surface in this build argues the work was good. This one argues the work is STILL
 * TRUE, and shows the machinery that would notice if it stopped being.
 *
 * THE GRAMMAR. Four fields, in this order, and never a fifth:
 *
 *     LED   SUBJECT (+ its context)      STATE      VERIFIED
 *
 * The context is not a column. It is the rest of SUBJECT's line, the way a console line has always
 * been a fixed tag followed by a message, which is what let the web version stop truncating.
 *
 * WHY DEGRADED IS THE WHOLE IDEA. Green-or-red is what GitHub already gives you. DEGRADED, meaning
 * passing, succeeding daily, and quietly aging toward its deadline, is the state every failure this
 * board was built after actually lived in: a 5,150-line generated file 21 days old and invisible to
 * its own alarm, a chess dataset 29 days stale with 16 more days of legal silence still to run.
 *
 * MOTION. Only BROKEN moves, in the same two places as the web: the LED breathes and the rail
 * counts how long the worst row has been wrong. Nothing else animates, both stop under reduced
 * motion, and BROKEN is still carried by colour, a word and a static ring without them.
 *
 * WHAT THIS BUILD CANNOT READ, and why the board says so instead of showing a zero. The React page
 * has eight blocks; four of them are here. Control tower reads the GitHub Actions API at load,
 * Published and signed reads the F-Droid index, and Live surfaces fires a same-origin HEAD at every
 * embed from the reader's own browser. All three need a backend this binary does not have. The
 * incident ledger is a corpus (`src/data/incidents.ts`) that the Kotlin emitter does not carry yet.
 * A block with no source is absent from this board, never rendered empty and never counted as OK,
 * because a board about failure nobody noticed cannot itself report a missing feed as a clean one.
 * The same rule removes the REPAIR and RECORD stations from the loop rather than printing them 000.
 *
 * PROVENANCE. Nothing here claims current access to an employer's code. The fleet block is measured
 * history: public Play listings anyone can re-check. The fleet note says so in its own words.
 */

private const val repo = "https://github.com/darkpandawarrior/cv-siddharth"

/**
 * The two state tints the KMP palette does not have yet.
 *
 * `--color-signal` is already `cvColors.accent` (both are #3ddc84), so OK reads through the theme
 * and follows a reskin. Aging and failure have no token in [com.siddharth.cv.shared.theme.CvColors]
 * at all, and inventing them out of `accent2` would make DEGRADED and BROKEN indistinguishable from
 * a healthy row, which is the one thing this page must never do. These are `--color-warn` and
 * `--color-danger` from cv-siddharth/src/index.css, held here only until the palette carries them.
 */
private val DegradedTint = cvColor("#f0883e")
private val BrokenTint = cvColor("#ff5c5c")

/** Declaration order is the severity rank, so `sortedBy { it.state.ordinal }` is worst-first. */
private enum class OpsState { BROKEN, DEGRADED, OK }

/**
 * One row of the board.
 *
 * `sinceDay` is set only where an elapsed time is meaningful, and it drives the rail's clock. The
 * lane a row belongs to is NOT carried here: it is the block's property, handed down when a row is
 * hoisted into the rail, which keeps this at eight fields and keeps one fact in one place.
 */
private data class OpsRow(
    val key: String,
    val state: OpsState,
    val subject: String,
    val subjectUrl: String,
    val detail: String,
    val verified: String,
    val verifiedUrl: String?,
    val sinceDay: Long?,
)

private data class OpsBlock(val lane: String, val title: String, val note: String, val rows: List<OpsRow>)

// ---------------------------------------------------------------------------------------------
// Dates
//
// `kotlin.time` is the only wall clock available here: there is no java.time on wasm, and the repo
// has taken no date dependency. Every stamp in the corpora is a `YYYY-MM-DD`, so an epoch DAY is
// the whole arithmetic, and Instant.parse does the parsing that would otherwise be a civil-date
// algorithm nobody wants to review.
// ---------------------------------------------------------------------------------------------

private const val secondsPerDay = 86_400L

@OptIn(ExperimentalTime::class)
private fun nowSeconds(): Long = Clock.System.now().epochSeconds

@OptIn(ExperimentalTime::class)
private fun isoSeconds(seconds: Long): String = Instant.fromEpochSeconds(seconds).toString()

/** `"2026-08-29"` to its epoch day, or null when the stamp is not a date this build can read. */
@OptIn(ExperimentalTime::class)
private fun epochDay(stamp: String): Long? =
    runCatching { Instant.parse(stamp + "T00:00:00Z").epochSeconds / secondsPerDay }.getOrNull()

private val ShortMonths =
    listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/**
 * Play's own listing format, `"Aug 5, 2026"`, to an epoch day.
 *
 * Sort key only. The row prints the string verbatim, because a date a reader can paste straight
 * back into a Play listing beats one this code has quietly reformatted.
 */
private fun playDay(updated: String): Long? {
    val parts = updated.replace(",", "").split(" ")
    val month = parts.getOrNull(0)?.let { ShortMonths.indexOf(it) + 1 } ?: 0
    val day = parts.getOrNull(1)?.toIntOrNull()
    val year = parts.getOrNull(2)?.toIntOrNull()
    if (month == 0 || day == null || year == null) return null
    return epochDay("$year-${pad(month)}-${pad(day)}")
}

private fun pad(n: Long): String = if (n < 10) "0$n" else "$n"

private fun pad(n: Int): String = pad(n.toLong())

/** Aging is DEGRADED from two-thirds of the way to the deadline. Same rule as freshnessSla.ts. */
private fun stateForAge(age: Long, sla: Int): OpsState = when {
    age > sla -> OpsState.BROKEN
    age >= sla * 2 / 3 -> OpsState.DEGRADED
    else -> OpsState.OK
}

/**
 * Time REMAINING, not elapsed.
 *
 * "21d / 45d" makes a reader do the subtraction. "24d left of 45d" is the same fact already reduced
 * to the part that matters, and it turns the perimeter from trivia into a countdown, which is what
 * it actually is.
 */
private fun budget(age: Long, sla: Int): String = when {
    age > sla -> "${age - sla}d OVER the ${sla}d SLA"
    age.toInt() == sla -> "due today · ${sla}d SLA"
    else -> "${sla - age}d left of ${sla}d"
}

/** `2922170` to `"2,922,170"`. Kotlin common has no `toLocaleString`. */
private fun num(n: Int): String = n.toString().reversed().chunked(3).joinToString(",").reversed()

// ---------------------------------------------------------------------------------------------
// The blocks
//
// Every builder takes `today` rather than reading a clock, so the whole board is aged against one
// instant. That is also the literal claim the banner makes: these ages ARE the moment you loaded
// the page, not the moment some generator last ran.
// ---------------------------------------------------------------------------------------------

private fun opsBlocks(today: Long): List<OpsBlock> = listOf(
    OpsBlock(
        lane = "perimeter",
        title = "Freshness perimeter",
        note = "generated data against the SLA its own test enforces · worst first · " +
            "aged as you loaded this page",
        rows = perimeterRows(today),
    ),
    OpsBlock(
        lane = "drift",
        title = "Vendored drift",
        note = "how far each app is behind the shared foundation it pins as a git submodule",
        rows = driftRows(),
    ),
    OpsBlock(
        lane = "fleet",
        title = "Fleet heartbeat",
        note = "${fleetStats.live} apps re-verified against their live Play listings on the last " +
            "sweep · ${num(fleetStats.installFloor)} installs floor · ${fleetStats.delisted} since " +
            "delisted. No SLA on a row here: a quiet app is not a broken one, and the sweep has " +
            "its own perimeter row above. These shipped from employer work. The listings are " +
            "public and anyone can re-check them; the source was never his and is not tracked on " +
            "this board.",
        rows = fleetRows(),
    ),
    OpsBlock(
        lane = "leverage",
        title = "Leverage",
        note = "convention plugins by the modules that apply them · a plugin nothing applies is " +
            "DEGRADED, not absent",
        rows = leverageRows(),
    ),
)

private fun perimeterRows(today: Long): List<OpsRow> {
    val files = opsPerimeter.map { p ->
        perimeterRow(
            subject = p.file,
            subjectUrl = "$repo/blob/main/src/data/${p.file}",
            stamp = p.generatedAt,
            sla = p.slaDays,
            trailer = p.generator.removePrefix("npm run "),
            today = today,
        )
    }
    // The sweep is not in ops.ts because it stamps store.ts instead, but it ages like everything
    // else and is the one generator on the list nobody has put on a cron.
    val sweep = perimeterRow(
        subject = "Play Store fleet sweep",
        subjectUrl = "$repo/blob/main/scripts/gen-store.mjs",
        stamp = storeGeneratedAt,
        sla = sweepSlaDays,
        trailer = "gen:store · run by hand, not on a cron",
        today = today,
    )
    return (files + sweep).sortedBy { it.state.ordinal }
}

/** The blanket SLA from freshnessSla.ts, for anything the per-file table does not name. */
private const val sweepSlaDays = 45

private fun perimeterRow(
    subject: String,
    subjectUrl: String,
    stamp: String,
    sla: Int,
    trailer: String,
    today: Long,
): OpsRow {
    val day = epochDay(stamp)
    val age = day?.let { today - it }
    return OpsRow(
        key = "perimeter:$subject",
        // An unreadable stamp is BROKEN, never OK. Ageing to zero would let a generator that
        // dropped its stamp report as freshly built, which is the exact failure this perimeter
        // exists to catch.
        state = if (age == null) OpsState.BROKEN else stateForAge(age, sla),
        subject = subject,
        subjectUrl = subjectUrl,
        detail = if (age == null) {
            "stamp \"$stamp\" is not a date this build can read, so its age is unknown rather " +
                "than fresh · $trailer"
        } else {
            "${budget(age, sla)} · $trailer"
        },
        verified = stamp,
        verifiedUrl = "$repo/actions/workflows/refresh-media.yml",
        sinceDay = day,
    )
}

/**
 * How far each app is behind the shared foundation it vendors.
 *
 * No BROKEN here, and deliberately no threshold. "8 commits behind" as a failure line would be a
 * number invented to make a row red, the exact thing this board refuses elsewhere. Drift has no
 * declared SLA, so it reports two honest states: level, or behind by a measured amount.
 */
private fun driftRows(): List<OpsRow> = opsDrift.map { d ->
    OpsRow(
        key = "drift:${d.repo}:${d.upstream}",
        state = if (d.behind == 0) OpsState.OK else OpsState.DEGRADED,
        subject = "${d.repo} > ${d.upstream}",
        subjectUrl = "https://github.com/darkpandawarrior/${d.upstream}",
        detail = when (d.behind) {
            null -> "pinned at ${d.pin}, a commit this clone has never fetched, so the distance " +
                "is unmeasured rather than assumed zero"
            0 -> "pinned at ${d.pin} · level with upstream"
            1 -> "pinned at ${d.pin} · 1 commit behind upstream"
            else -> "pinned at ${d.pin} · ${d.behind} commits behind upstream"
        },
        verified = d.pinnedAt ?: "unknown",
        verifiedUrl = null,
        sinceDay = d.pinnedAt?.let(::epochDay),
    )
}

/**
 * All of them, oldest release first. Eight rows with a dot is a status badge; the whole fleet is
 * somebody noticing what nobody else did.
 *
 * STATE is "confirmed listed on the last sweep", never "shipped recently". gen-store.mjs drops
 * anything whose listing does not resolve, so every row present IS live. An app quiet since 2023 is
 * a quiet app, not a broken one, and the staleness that CAN go wrong here (the sweep's) has its own
 * row on the perimeter above.
 *
 * Rebuilt from `liveClients[].apps` rather than the web's flat `fleet` export, which the Kotlin
 * emitter dropped as an exact duplicate of exactly this flattening.
 */
private fun fleetRows(): List<OpsRow> = liveClients
    .flatMap { client -> client.apps.map { client to it } }
    .filter { (_, app) -> app.updated.isNotBlank() }
    .sortedBy { (_, app) -> playDay(app.updated) ?: Long.MAX_VALUE }
    .map { (client, app) ->
        OpsRow(
            key = "fleet:${app.id}",
            state = OpsState.OK,
            subject = app.name,
            subjectUrl = app.url,
            detail = "${client.developer} · ${app.installs} installs · last shipped ${app.updated}",
            verified = storeGeneratedAt,
            verifiedUrl = app.url,
            sinceDay = null,
        )
    }

/**
 * Convention plugins by the modules that apply them.
 *
 * A plugin applied by nothing is DEGRADED, and ten of the seventeen are. That is a real finding
 * about his own toolchain rather than a rendering accident: the generator used to walk each
 * consumer's vendored `external/` submodules, which counted upstream modules once per consumer and
 * counted every plugin's own declaration file as a consumer of itself. It reported
 * shared.android.library at 63 against a true 24, and painted all ten of the zeros green.
 */
private fun leverageRows(): List<OpsRow> = opsLeverage.map { l ->
    OpsRow(
        key = "leverage:${l.id}",
        state = if (l.modules > 0) OpsState.OK else OpsState.DEGRADED,
        subject = l.id,
        subjectUrl = "https://github.com/darkpandawarrior/kmp-build-logic",
        detail = if (l.modules > 0) {
            l.repos.joinToString(" · ")
        } else {
            "authored, applied by no consumer module. The id appears in no build file outside its " +
                "own declaration."
        },
        verified = "${l.modules} modules",
        verifiedUrl = null,
        sinceDay = null,
    )
}.sortedBy { it.state.ordinal }

// ---------------------------------------------------------------------------------------------
// The page
// ---------------------------------------------------------------------------------------------

/** Below this the row's four fields cannot share a line, and VERIFIED drops under the subject. */
private val NarrowBoard = 640.dp

/** `max-height: min(34vh, 15rem)`. The rail is capped so it can never eat the phone it is pinned to. */
private val RailMaxHeight = 240.dp

@Composable
fun OpsScreen(modifier: Modifier = Modifier) {
    // Stamped once, to the second. It is literally true: it IS the instant every age on this page
    // was computed, and it costs zero motion.
    val loadedAtSeconds = remember { nowSeconds() }
    val today = remember(loadedAtSeconds) { loadedAtSeconds / secondsPerDay }
    val blocks = remember(today) { opsBlocks(today) }

    // Every non-OK row, worst first, carrying the lane it was hoisted out of. The rows stay in
    // their own blocks below and the census there still counts them, because hoisting a row out
    // silently would make that block read clean when it is not.
    val escalated = remember(blocks) {
        blocks.flatMap { b -> b.rows.filter { it.state != OpsState.OK }.map { b.lane to it } }
            .sortedBy { it.second.state.ordinal }
    }
    val total = remember(blocks) { blocks.sumOf { it.rows.size } }
    val broken = escalated.count { it.second.state == OpsState.BROKEN }
    val worst = escalated.firstOrNull()?.second

    BoxWithConstraints(modifier.fillMaxSize()) {
        val narrow = maxWidth < NarrowBoard
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(top = 32.dp, bottom = 120.dp),
        ) {
            item { BoardHeader() }

            // Sticky, opaque, and never a translucent ground: a semi-transparent pinned header is
            // what makes an automated contrast check report *incomplete* instead of pass.
            stickyHeader {
                Banner(
                    loadedAt = isoSeconds(loadedAtSeconds),
                    total = total,
                    escalated = escalated,
                    broken = broken,
                    worst = worst,
                    narrow = narrow,
                )
            }

            blocks.forEach { block ->
                item(key = "rule:${block.lane}") { BlockRule(block) }
                items(block.rows, key = { it.key }) { row -> OpsRowView(row, narrow) }
            }

            item { BoardFooter() }
        }
    }
}

@Composable
private fun BoardHeader() {
    Column(Modifier.pageMeasure().padding(bottom = 20.dp)) {
        SectionEyebrow("// the control loop")
        Spacer(Modifier.height(12.dp))
        SectionHeading("Still true, or only once true")
        Spacer(Modifier.height(14.dp))
        BasicText(
            text = "Every other page here argues the work was good. This one argues it is still " +
                "true, and shows the machinery that would notice if it stopped being. Three " +
                "states: OK is a check that ran and passed, BROKEN is a check that failed or an " +
                "SLA that is blown, and DEGRADED, meaning passing, succeeding daily, and quietly " +
                "aging toward its deadline, is the state every failure this board was built after " +
                "actually lived in.",
            modifier = Modifier.widthIn(max = 760.dp),
            style = cvType.bodySmall,
        )
    }
}

@Composable
private fun BoardFooter() {
    Column(Modifier.pageMeasure().padding(top = 24.dp)) {
        MonoMeta(
            "PERIMETER, LEVERAGE AND DRIFT GENERATED $opsGeneratedAt FROM REPOS ON THE BUILD " +
                "MACHINE · PLAY LISTINGS SWEPT $storeGeneratedAt · AGES COMPUTED AS YOU LOADED " +
                "THIS PAGE · EMPLOYMENT-ERA FIGURES ARE MEASURED HISTORY, NOT A LIVE FEED",
        )
    }
}

/** `mx-auto max-w-[92rem] px-6`. Wider than the rest of the site: this page is a console. */
private fun Modifier.pageMeasure(): Modifier =
    this.widthIn(max = CvContentMaxWidth).fillMaxWidth().padding(horizontal = CvGutter)

// ---------------------------------------------------------------------------------------------
// Banner + rail
// ---------------------------------------------------------------------------------------------

@Composable
private fun Banner(
    loadedAt: String,
    total: Int,
    escalated: List<Pair<String, OpsRow>>,
    broken: Int,
    worst: OpsRow?,
    narrow: Boolean,
) {
    val colors = cvColors
    val worstState = worst?.state ?: OpsState.OK
    Box(Modifier.fillMaxWidth().background(colors.ink), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .pageMeasure()
                .padding(top = 10.dp),
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                BasicText(
                    text = "SID//OS",
                    style = cvType.metaMono.copy(
                        color = colors.onBackground,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                BasicText(
                    text = "ops console",
                    style = cvType.metaMono.copy(color = colors.onBackground),
                )
                MonoMeta("darkpandawarrior/cv-siddharth")
                MonoMeta("aged at load $loadedAt")
            }

            Spacer(Modifier.height(8.dp))

            // Three stations, not five. REPAIR and RECORD read the incident ledger, which this
            // build has no copy of; printing them as 000 would claim an empty ledger rather than a
            // missing one.
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                LoopStation("DETECT", total, hot = false)
                LoopStation("ANNOUNCE", escalated.size, hot = escalated.isNotEmpty())
                LoopStation("ESCALATE", broken, hot = broken > 0)
            }

            Spacer(Modifier.height(10.dp))
            Rail(escalated = escalated, broken = broken, total = total, worst = worst, narrow = narrow)
            Spacer(Modifier.height(10.dp))
            // An all-green day has to LOOK different from a day with something on fire, so the
            // banner's bottom edge is the worst state in the whole system.
            Box(Modifier.fillMaxWidth().height(2.dp).background(stateTint(worstState)))
        }
    }
}

@Composable
private fun LoopStation(label: String, count: Int, hot: Boolean) {
    val colors = cvColors
    Row(verticalAlignment = Alignment.CenterVertically) {
        MonoMeta(label)
        Spacer(Modifier.width(7.dp))
        BasicText(
            text = count.toString().padStart(3, '0'),
            style = cvType.metaMono.copy(
                color = if (hot) DegradedTint else colors.onBackground,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

/**
 * THE RAIL. The actual non-OK rows, not a summary of them, so the worst thing in the system is on
 * screen at every scroll position. A board about failure nobody noticed cannot make you scroll to
 * find the failure.
 */
@Composable
private fun Rail(
    escalated: List<Pair<String, OpsRow>>,
    broken: Int,
    total: Int,
    worst: OpsRow?,
    narrow: Boolean,
) {
    val colors = cvColors
    Column(Modifier.fillMaxWidth()) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BasicText(
                text = if (escalated.isEmpty()) "ALL CLEAR" else "ESCALATED",
                style = cvType.metaMono.copy(
                    color = colors.onBackground,
                    fontWeight = FontWeight.Bold,
                ),
            )
            MonoMeta("$broken broken")
            MonoMeta("${escalated.size - broken} degraded")
            MonoMeta("${total - escalated.size} steady")
            MonoMeta(
                if (escalated.isEmpty()) {
                    "nothing is escalating; every row is inside the SLA it declares"
                } else {
                    "pinned here and still counted in their own blocks below"
                },
            )
            val since = worst?.sinceDay
            if (worst?.state == OpsState.BROKEN && since != null) BrokenClock(since)
        }

        if (escalated.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = RailMaxHeight)
                    .verticalScroll(rememberScrollState()),
            ) {
                escalated.forEach { (lane, row) -> OpsRowView(row, narrow, lane) }
            }
        }
    }
}

/**
 * The one ticking readout on the page, and the only thing that says "right now" rather than "as of".
 *
 * Its own composable so the 1Hz state write invalidates one text node instead of recomposing every
 * row on the board every second. Mounted only when a BROKEN row exists, and it never starts under
 * reduced motion.
 */
@Composable
private fun BrokenClock(sinceDay: Long) {
    val reduced = LocalReducedMotion.current
    var now by remember { mutableStateOf(nowSeconds()) }
    LaunchedEffect(reduced, sinceDay) {
        if (reduced) return@LaunchedEffect
        while (true) {
            delay(1000)
            now = nowSeconds()
        }
    }
    val elapsed = (now - sinceDay * secondsPerDay).coerceAtLeast(0)
    val rest = elapsed % secondsPerDay
    BasicText(
        text = "worst unchanged for ${elapsed / secondsPerDay}d " +
            "${pad(rest / 3600)}:${pad(rest / 60 % 60)}:${pad(rest % 60)}",
        style = cvType.metaMono.copy(color = BrokenTint),
    )
}

// ---------------------------------------------------------------------------------------------
// Blocks + rows
// ---------------------------------------------------------------------------------------------

/** A block heading is a rule line carrying its own census, not a card. */
@Composable
private fun BlockRule(block: OpsBlock) {
    val colors = cvColors
    fun census(state: OpsState) = block.rows.count { it.state == state }
    Column(Modifier.pageMeasure().padding(top = 26.dp)) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BasicText(
                text = block.title,
                style = cvType.cardTitle.copy(fontSize = cvType.body.fontSize),
            )
            Row {
                Census(census(OpsState.BROKEN), "broken", BrokenTint)
                Spacer(Modifier.width(10.dp))
                Census(census(OpsState.DEGRADED), "degraded", DegradedTint)
                Spacer(Modifier.width(10.dp))
                Census(census(OpsState.OK), "ok", colors.accent)
            }
        }
        Spacer(Modifier.height(6.dp))
        BasicText(text = block.note, style = cvType.metaMono)
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
    }
}

@Composable
private fun Census(n: Int, label: String, tint: Color) {
    Row {
        BasicText(
            text = n.toString(),
            style = cvType.metaMono.copy(color = tint, fontWeight = FontWeight.Bold),
        )
        Spacer(Modifier.width(4.dp))
        MonoMeta(label)
    }
}

private val StateColumn = 84.dp
private val VerifiedColumn = 104.dp

/**
 * One row. Four fields, always.
 *
 * SUBJECT names the thing and VERIFIED names the evidence, and both open the URL that proves it
 * where there is one. Under [narrow] the two right-hand fields drop onto their own line beneath the
 * subject instead of being squeezed, which is what the web's ≤640px grid does; nothing is dropped,
 * only re-flowed.
 */
@Composable
private fun OpsRowView(row: OpsRow, narrow: Boolean, lane: String? = null) {
    val colors = cvColors
    Column(Modifier.pageMeasure()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
            Led(row.state)
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                if (lane != null) MonoMeta(lane.uppercase())
                BasicText(
                    text = row.subject,
                    style = cvType.mono.copy(color = colors.onBackground),
                )
                BasicText(text = row.detail, style = cvType.mono.copy(color = colors.muted))
                if (narrow) {
                    Spacer(Modifier.height(4.dp))
                    Row {
                        StateWord(row.state)
                        Spacer(Modifier.width(12.dp))
                        MonoMeta(row.verified)
                    }
                }
            }
            if (!narrow) {
                Spacer(Modifier.width(12.dp))
                Box(Modifier.width(StateColumn)) { StateWord(row.state) }
                BasicText(
                    text = row.verified,
                    modifier = Modifier.width(VerifiedColumn),
                    style = cvType.metaMono.copy(textAlign = TextAlign.End),
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line.copy(alpha = 0.55f)))
    }
}

@Composable
private fun StateWord(state: OpsState) {
    BasicText(
        text = state.name,
        style = cvType.metaMono.copy(color = stateTint(state), fontWeight = FontWeight.Bold),
    )
}

/**
 * The LED. The only thing on the page allowed to move, and only when BROKEN.
 *
 * It carries no text of its own and needs none: STATE spells the same word out one column over, so
 * the a11y tree already has the fact. Under reduced motion the breath is replaced by a static ring,
 * exactly as the CSS does, so BROKEN never rests on colour alone.
 */
@Composable
private fun Led(state: OpsState) {
    val tint = stateTint(state)
    val reduced = LocalReducedMotion.current
    val breath by rememberInfiniteFloat(1600, from = 0.35f, to = 1f, easing = CvMotion.EaseOutQuart)
    val broken = state == OpsState.BROKEN
    Box(
        modifier = Modifier
            .padding(top = 4.dp)
            .size(14.dp)
            .then(if (broken && reduced) Modifier.border(2.dp, tint, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .background(tint.copy(alpha = if (broken && !reduced) breath else 1f), CircleShape),
        )
    }
}

@Composable
private fun stateTint(state: OpsState): Color = when (state) {
    OpsState.OK -> cvColors.accent
    OpsState.DEGRADED -> DegradedTint
    OpsState.BROKEN -> BrokenTint
}
