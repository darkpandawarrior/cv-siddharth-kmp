package com.siddharth.cv.shared.chess

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.siddharth.cv.shared.CvNavState
import com.siddharth.cv.shared.LocalNav
import com.siddharth.cv.shared.Route
import com.siddharth.cv.shared.anthology.grouped
import com.siddharth.cv.shared.data.generated.ChessActivityYear
import com.siddharth.cv.shared.data.generated.ChessOpeningShare
import com.siddharth.cv.shared.data.generated.ChessPlatform
import com.siddharth.cv.shared.data.generated.chess
import com.siddharth.cv.shared.data.generated.chessDeep
import com.siddharth.cv.shared.data.generated.chessHours
import com.siddharth.cv.shared.theme.CircuitDivider
import com.siddharth.cv.shared.theme.CvCard
import com.siddharth.cv.shared.theme.CvContentMaxWidth
import com.siddharth.cv.shared.theme.CvGutter
import com.siddharth.cv.shared.theme.CvSectionGap
import com.siddharth.cv.shared.theme.GhostButton
import com.siddharth.cv.shared.theme.MonoMeta
import com.siddharth.cv.shared.theme.Reveal
import com.siddharth.cv.shared.theme.SectionEyebrow
import com.siddharth.cv.shared.theme.SectionHeading
import com.siddharth.cv.shared.theme.cvColor
import com.siddharth.cv.shared.theme.cvColors
import com.siddharth.cv.shared.theme.cvType
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.round

/**
 * The Board. Port of cv-siddharth/src/ChessRoom.tsx and the two panes inside it that are 2D:
 * `chess/ChessFindings.tsx` and `chess/ChessVsCommits.tsx`.
 *
 * THE WEB ROOM HAS SEVEN TABS. Six of them are here, one whole and five at the fidelity the web
 * itself drops to without WebGL. Nothing is approximated: a flat drawing captioned with a 3D
 * scene's claim is the one thing this port is not allowed to ship.
 *  - **The Findings** and **Rhythm** are whole, and are this file.
 *  - **The Arc**, **The Graveyard** and **Repertoire** are in `ChessScenePanes.kt`
 *    ([ArcPane], [GraveyardPane], [RepertoirePane]). Their three.js scenes (`ChessArcScene`, `GraveyardScene`, `RepertoireTreeScene`) are out, behind the
 *    same WebGL wall `/blueprint` is. What is here is the OTHER branch of each pane's own
 *    `webgl ? scene : fallback` ternary, which the web renders under reduced motion or on a machine
 *    with no GPU: a flat SVG band chart, a ranked list of terminal squares, and a year scrubber
 *    over a written table. Each pane's copy names the scene it is standing next to rather than in
 *    for. The README called all three emitter work rather than renderer work and was right: the
 *    whole cost was four new `vals` in `gen-kotlin-data.mjs`.
 *  - **Guess the Move** is in `ChessGuessPane.kt` ([GuessThePositionPane]), whole apart from its
 *    playhtml-backed counter shared between visitors, which needs a backend this port does not
 *    have. Its board needs no engine: only the FEN's piece placement is drawn.
 *  - **Play the Bot** (`chess/ChessBoardPane.tsx`) and the captured daily puzzle are the only two
 *    still absent, and NOT because of the wall they used to be behind. Both lean on chess.js for
 *    legality, which used to mean writing a move generator; `labs/ChessEngine.kt` now carries a
 *    perft-checked one that is `internal` and therefore visible from here. What is actually left
 *    is written down in [chessBoardPaneCost]: an interactive board, the two calibration presets,
 *    and a decision about the clock model that engine deliberately drops.
 *
 * Nothing was tab-stripped in their place: the panes stack into one scroll, which is also what
 * ChessRoom's own default landing tab does before anyone clicks.
 *
 * NOT ONE FIGURE IS TYPED. Every number reads from `data/generated/CvChessData.kt`, generated from
 * `src/data/chess.ts`, `src/data/chessDeep.ts` and `public/chess/corpus.json`, which are themselves
 * generated from both platforms' public APIs. The owner is still playing: the corpus grew by three
 * games within an hour of first generation. A literal here is a number that goes stale on a hiring
 * surface with nobody watching, which is why even the year count in the heading is a division.
 *
 * THREE HONESTY CONSTRAINTS ARE LOAD-BEARING IN THE COPY, not polish:
 *  - `boardTime.combinedHours` ADDS two different measurements, lichess's self-reported playTime
 *    and a figure derived from chess.com PGN wall clock. Both halves are named wherever it prints.
 *  - The two platforms are a handoff, not parallel accounts, and their ratings are not comparable.
 *    Game counts establish when he was playing; rating-history dates do not.
 *  - A repertoire year's Scandinavian share is a floor, because it sums only the lines that made
 *    that year's top five.
 */
@Composable
fun ChessScreen(modifier: Modifier = Modifier) {
    val nav = LocalNav.current
    val uri = LocalUriHandler.current

    BoxWithConstraints(modifier.fillMaxSize()) {
        val threeUp = maxWidth >= WideBreakpoint
        val twoUp = maxWidth >= MediumBreakpoint

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(top = 32.dp, bottom = 120.dp),
        ) {
            item("header") { BoardHeader(nav) }
            item("thesis") { ThesisSection(if (threeUp) 3 else 1) }
            item("repertoire") { RepertoireSection() }
            item("profiles") { ProfilesSection(if (twoUp) 2 else 1, uri) }
            item("cast") { CastSection(nav, if (threeUp) 3 else 1) }
            item("provenance") { ProvenanceNote() }
            item("second-pass") { SecondPassSection(if (twoUp) 2 else 1) }
            item("arc") { ArcPane() }
            item("graveyard") { GraveyardPane() }
            item("repertoire-by-platform") { RepertoirePane() }
            item("guess") { GuessThePositionPane() }
            item("rhythm") { RhythmSection() }
        }
    }
}

// -------------------------------------------------------------------------------------------
// Measure and section frame, matching ShippedScreen and CanonScreen
// -------------------------------------------------------------------------------------------

private val WideBreakpoint: Dp = 900.dp
private val MediumBreakpoint: Dp = 620.dp

/** The web page is `max-w-6xl`; every surface in this port holds to [CvContentMaxWidth]. */
internal fun Modifier.pageMeasure(): Modifier =
    this.widthIn(max = CvContentMaxWidth).fillMaxWidth().padding(horizontal = CvGutter)

@Composable
internal fun Section(eyebrow: String, title: String, content: @Composable ColumnScope.() -> Unit) {
    Reveal {
        Column(Modifier.pageMeasure().padding(top = CvSectionGap)) {
            SectionEyebrow(eyebrow)
            Spacer(Modifier.height(10.dp))
            SectionHeading(title)
            Spacer(Modifier.height(20.dp))
            content()
        }
    }
}

/**
 * A plain chunked grid. Deliberately not a LazyVerticalGrid: these live inside the screen's
 * LazyColumn, and nesting a lazy scroller of the same axis inside another is an infinite
 * constraint. Same shape as `HomeSections.GridRows`, which is private to its own file.
 */
@Composable
private fun <T> GridRows(items: List<T>, columns: Int, cell: @Composable (T) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        items.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { item -> Box(Modifier.weight(1f)) { cell(item) } }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

// -------------------------------------------------------------------------------------------
// Derived values. Read these, never a literal. Every one mirrors a `const` in ChessFindings.tsx.
// -------------------------------------------------------------------------------------------

/** One repertoire year, joined to the platform game counts the opening change is confounded with. */
private data class RepertoireRow(
    val year: String,
    val top: ChessOpeningShare,
    val scandinavian: Double,
    val activity: ChessActivityYear?,
)

private fun isScandinavian(name: String): Boolean = name.contains("scandinavian", ignoreCase = true)

/**
 * `scandinavian` sums only the Scandinavian lines that made the year's top five, so a zero means
 * "fell out of the top five", not "played zero times". The table says so in its caption.
 */
private val repertoire: List<RepertoireRow> =
    chess.repertoire.mapNotNull { year ->
        // A year the generator emitted with no openings drops out rather than throwing during class
        // initialisation, which on wasm is a blank page with nothing in the console worth reading.
        year.openings.firstOrNull()?.let { top ->
            RepertoireRow(
                year = year.year,
                top = top,
                scandinavian = year.openings.filter { isScandinavian(it.name) }.sumOf { it.share },
                activity = chess.activityByYear.firstOrNull { it.year == year.year },
            )
        }
    }

/** The handoff, derived: the first year chess.com carried more games than lichess. */
internal val handoff: ChessActivityYear? = chess.activityByYear.firstOrNull { it.chesscom > it.lichess }

private val displaced: RepertoireRow? = repertoire.firstOrNull { !isScandinavian(it.top.name) }

private val lastOnLichess: RepertoireRow? =
    handoff?.let { h -> repertoire.lastOrNull { it.year < h.year } } ?: repertoire.lastOrNull()

private val latest: RepertoireRow? = repertoire.lastOrNull()

/**
 * lichess's rating history runs years past the handoff purely because of a handful of games in its
 * final year. Printed so nobody reads the arc's right edge as an active account.
 */
private val lichessLastFlicker: ChessActivityYear? = chess.activityByYear.lastOrNull { it.lichess > 0 }

private val daysPlayed: Double =
    if (chess.discipline.spanDays == 0) 0.0 else {
        chess.discipline.distinctDays.toDouble() / chess.discipline.spanDays
    }

/**
 * The web spells this out (`routes/chess.tsx` runs `countWord` over the same division) because the
 * rest of the site counts in words. Derived, never typed: `spanDays` is refreshed from the first and
 * last game the APIs return, so the heading ages itself.
 */
private val spanYears: Int = (chess.discipline.spanDays / 365.25).toInt()

private val SpelledOnes: List<String> =
    listOf("Zero", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten")

private fun spelled(n: Int): String = SpelledOnes.getOrNull(n) ?: n.toString()

/** The bucket width is a function of how many buckets the generator emitted, so the axis can't drift. */
private val decileStep: Int = 100 / chess.thesis.deciles.size
private val maxGap: Double = chess.thesis.deciles.maxOf { it.gap }

// -------------------------------------------------------------------------------------------
// Formatters. No printf in common Kotlin, and no locale to get wrong.
// -------------------------------------------------------------------------------------------

/** `3.14` to `"3.1"`. `toString()` prints "3.0999999" for some doubles. No negatives in this corpus. */
internal fun oneDecimal(v: Double): String {
    val scaled = round(v * 10.0).toInt()
    return "${scaled / 10}.${scaled % 10}"
}

/** A fraction to a percentage: `0.416` to `"41.6%"`. What `chess.*` stores. */
internal fun pctOf(fraction: Double): String = "${oneDecimal(fraction * 100)}%"

/** An already-scaled percentage: `49.2` to `"49.2%"`. What `chessDeep.*` stores. */
private fun pct(percent: Double): String = "${oneDecimal(percent)}%"

private fun plural(n: Int, word: String): String = "${n.grouped()} $word${if (n == 1) "" else "s"}"

// -------------------------------------------------------------------------------------------
// 1. Header
// -------------------------------------------------------------------------------------------

@Composable
private fun BoardHeader(nav: CvNavState) {
    Reveal {
        Column(Modifier.pageMeasure()) {
            GhostButton(text = "Back to portfolio", onClick = { nav.go(Route.Home) })
            Spacer(Modifier.height(28.dp))
            SectionEyebrow("// the board")
            Spacer(Modifier.height(10.dp))
            BasicText(text = "${spelled(spanYears)} years of games, mined", style = cvType.hero)
            Spacer(Modifier.height(18.dp))
            BasicText(
                text =
                    "Every rated and casual game played on lichess and chess.com, pulled from both " +
                        "public APIs at build time and taken apart before a word of this page was " +
                        "written. Where the clock decides them, what the repertoire drifted into, " +
                        "and the four fields the first analysis never read.",
                modifier = Modifier.widthIn(max = 680.dp),
                style = cvType.body,
            )
            Spacer(Modifier.height(12.dp))
            BasicText(
                text =
                    "Nothing on this page is typed. Every figure reads from the generated corpus, " +
                        "so re-running the generator moves the numbers and nobody has to remember " +
                        "to move them by hand.",
                modifier = Modifier.widthIn(max = 680.dp),
                style = cvType.bodySmall,
            )
        }
    }
}

// -------------------------------------------------------------------------------------------
// 2. The thesis: the clock, the divergence table, and the three stat cards
// -------------------------------------------------------------------------------------------

@Composable
private fun ThesisSection(columns: Int) {
    val thesis = chess.thesis
    val totals = chess.totals
    Section(eyebrow = "// the findings", title = "I lose time, not positions") {
        BasicText(
            text =
                "${totals.games.grouped()} rated and casual games across two platforms, analysed " +
                    "before anything on this page was written. The finding was not flattering: " +
                    "${pctOf(thesis.decidedOnClock)} of my decided games ended on a clock, not on " +
                    "a board. ${pctOf(thesis.lossesOnTime)} of every loss was a timeout, against " +
                    "${pctOf(thesis.winsOnTime)} of wins won on the opponent's. I don't lose " +
                    "positions nearly as often as I lose time.",
            modifier = Modifier.widthIn(max = 680.dp),
            style = cvType.body,
        )
        Spacer(Modifier.height(24.dp))
        ClockDivergenceCard()
        Spacer(Modifier.height(16.dp))
        GridRows(listOf(0, 1, 2), columns) { StatCard(it) }
    }
}

@Composable
private fun ClockDivergenceCard() {
    val colors = cvColors
    val thesis = chess.thesis
    CvCard(Modifier.fillMaxWidth(), glowOnHover = false) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                text = "Clock remaining, by game progress",
                modifier = Modifier.weight(1f),
                style = cvType.bodySmall.copy(fontWeight = FontWeight.SemiBold, color = colors.accent),
            )
            MonoMeta("THE THESIS")
        }
        Spacer(Modifier.height(16.dp))
        ScrollingTable(
            label = "Clock remaining by game progress",
            columns =
                listOf(
                    "Progress" to 112.dp,
                    "Wins" to 76.dp,
                    "Losses" to 76.dp,
                    "Gap" to 120.dp,
                ),
        ) {
            thesis.deciles.forEach { d ->
                TableRow {
                    Cell("${d.bucket * decileStep}-${(d.bucket + 1) * decileStep}%", 112.dp)
                    Cell(pctOf(d.win), 76.dp, strong = true)
                    Cell(pctOf(d.loss), 76.dp, strong = true)
                    Row(
                        modifier = Modifier.width(120.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Decoration, not data: the number beside it is the reading. Sized off the
                        // largest gap in the corpus so the shape survives a regeneration.
                        Box(
                            Modifier
                                .width(44.dp * (d.gap / maxGap).toFloat())
                                .height(3.dp)
                                .background(colors.accent.copy(alpha = 0.6f), CircleShape),
                        )
                        Spacer(Modifier.width(8.dp))
                        BasicText(
                            text = "+${pctOf(d.gap)}",
                            style = cvType.mono.copy(color = colors.accent),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        BasicText(
            text =
                "Mean fraction of the starting clock still on my clock, from " +
                    "${thesis.sampleSize.grouped()} blitz games carrying per-move clock " +
                    "annotations. The gap opens in the early middlegame and never closes: losses " +
                    "are decided by time spent long before any late blunder.",
            style = cvType.metaMono,
        )
    }
}

/**
 * The three summary cards, indexed rather than modelled: three shapes that share nothing but a
 * frame, and a data class for three call sites would be scaffolding.
 */
@Composable
private fun StatCard(index: Int) {
    val colors = cvColors
    val totals = chess.totals
    val boardTime = chess.boardTime
    val discipline = chess.discipline
    val eyebrow: String
    val value: String
    val tint: Color
    val note: String
    when (index) {
        0 -> {
            eyebrow = "// corpus"
            value = totals.games.grouped()
            tint = colors.accent
            note =
                "games, ${chess.span.from} to ${chess.span.to}. ${totals.wins.grouped()}W / " +
                    "${totals.losses.grouped()}L / ${totals.draws.grouped()}D, a losing record by " +
                    "${(totals.losses - totals.wins).grouped()}."
        }
        1 -> {
            eyebrow = "// time at the board"
            value = "${boardTime.combinedHours.grouped()} h"
            tint = colors.accent2
            note =
                "${boardTime.lichessHours.grouped()} h self-reported by lichess, plus " +
                    "${boardTime.chesscomHours.grouped()} h derived from the wall clock in " +
                    "${boardTime.chesscomGames.grouped()} chess.com PGNs. chess.com publishes no " +
                    "play-time figure, so this is two measurements added together, not one metric."
        }
        else -> {
            eyebrow = "// showing up"
            value = pctOf(daysPlayed)
            tint = colors.accent
            note =
                "of days played: ${discipline.distinctDays.grouped()} of " +
                    "${discipline.spanDays.grouped()} days in the span, longest unbroken run " +
                    "${discipline.longestDayStreak} days. Longest loss streak " +
                    "${discipline.longestLoss} beats the longest win streak ${discipline.longestWin}."
        }
    }
    CvCard(Modifier.fillMaxWidth(), glowOnHover = false) {
        MonoMeta(eyebrow)
        Spacer(Modifier.height(8.dp))
        BasicText(text = value, style = cvType.metric.copy(color = tint))
        Spacer(Modifier.height(8.dp))
        BasicText(text = note, style = cvType.bodySmall)
    }
}

// -------------------------------------------------------------------------------------------
// 3. Repertoire as Black, joined to the handoff it is confounded with
// -------------------------------------------------------------------------------------------

@Composable
private fun RepertoireSection() {
    val colors = cvColors
    Section(eyebrow = "// repertoire", title = "Repertoire as Black") {
        BasicText(
            text = repertoireArcSentence(),
            modifier = Modifier.widthIn(max = 680.dp),
            style = cvType.bodySmall,
        )
        Spacer(Modifier.height(12.dp))
        BasicText(
            text = handoffCaveatSentence(),
            modifier = Modifier.widthIn(max = 680.dp),
            style = cvType.bodySmall,
        )
        Spacer(Modifier.height(20.dp))
        ScrollingTable(
            label = "Openings by year",
            columns =
                listOf(
                    "Year" to 108.dp,
                    "lichess" to 76.dp,
                    "chess.com" to 88.dp,
                    "Most-played reply" to 260.dp,
                    "Scandinavian (top 5)" to 132.dp,
                ),
        ) {
            repertoire.forEach { row ->
                TableRow {
                    Column(Modifier.width(108.dp).padding(end = 16.dp)) {
                        BasicText(
                            text = row.year,
                            style = cvType.mono.copy(color = colors.onBackground),
                        )
                        if (handoff?.year == row.year) {
                            BasicText(
                                text = "handoff",
                                style = cvType.metaMono.copy(color = colors.accent2),
                            )
                        }
                    }
                    Cell(row.activity?.lichess?.grouped() ?: "-", 76.dp)
                    Cell(row.activity?.chesscom?.grouped() ?: "-", 88.dp)
                    Cell("${row.top.name} ${pctOf(row.top.share)}", 260.dp, strong = true)
                    Cell(
                        text = if (row.scandinavian > 0) pctOf(row.scandinavian) else "-",
                        width = 132.dp,
                        tint = if (row.scandinavian > 0) colors.accent else null,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        BasicText(
            text =
                "The Scandinavian column sums only the Scandinavian lines that made that year's " +
                    "top five replies, so it is a floor rather than an exact share. A dash means " +
                    "none of them did, not that it was never played.",
            style = cvType.metaMono,
        )
    }
}

/** Each clause drops out if the corpus stops supporting it, exactly as the JSX conditionals do. */
private fun repertoireArcSentence(): String {
    val opened = repertoire.firstOrNull()
    val parts = mutableListOf<String>()
    if (opened != null) {
        parts +=
            "I opened ${opened.year} with the Scandinavian: at least " +
                "${pctOf(opened.scandinavian)} of my games as Black that year."
    }
    if (displaced != null) {
        val dropped =
            if (lastOnLichess != null && lastOnLichess.scandinavian == 0.0) {
                ", and by ${lastOnLichess.year} it had dropped out of the top five entirely."
            } else {
                "."
            }
        parts += "By ${displaced.year} it was displaced by the ${displaced.top.name} " +
            "(${pctOf(displaced.top.share)})$dropped"
    }
    if (latest != null && isScandinavian(latest.top.name)) {
        parts += "It is my most-played reply again today: ${pctOf(latest.scandinavian)} of Black " +
            "games in ${latest.year}."
    }
    return parts.joinToString(" ")
}

private fun handoffCaveatSentence(): String {
    val between = handoff?.let { ", with the ${it.year} handoff sitting between them," } ?: ","
    val flicker =
        lichessLastFlicker?.let {
            ": lichess's last flicker is ${plural(it.lichess, "game")} in ${it.year}, which is the " +
                "only reason its rating history reaches that far"
        } ?: ""
    return "Both halves are real, but they are two within-platform observations rather than one " +
        "continuous line. The abandonment happened on lichess and the return happened on " +
        "chess.com$between so \"I came back to my first opening\" cannot be cleanly separated " +
        "from \"I started fresh on a new site.\" It is a handoff, not two accounts running side " +
        "by side$flicker. Game counts say when someone was actually playing; rating-history " +
        "dates do not."
}

// -------------------------------------------------------------------------------------------
// 4. Both profiles
// -------------------------------------------------------------------------------------------

@Composable
private fun ProfilesSection(columns: Int, uri: UriHandler) {
    Section(eyebrow = "// the accounts", title = "Both profiles") {
        BasicText(
            text =
                "Each account keeps its own scale. The two sites rate against different pools, so " +
                    "a peak on one is not a peak on the other and nothing on this page joins them " +
                    "into a single line.",
            modifier = Modifier.widthIn(max = 680.dp),
            style = cvType.bodySmall,
        )
        Spacer(Modifier.height(20.dp))
        GridRows(chess.platforms, columns) { PlatformCard(it, uri) }
    }
}

@Composable
private fun PlatformCard(platform: ChessPlatform, uri: UriHandler) {
    val colors = cvColors
    CvCard(Modifier.fillMaxWidth(), onClick = { uri.openUri(platform.url) }) {
        BasicText(text = platform.id, style = cvType.cardTitle)
        Spacer(Modifier.height(8.dp))
        MonoMeta(
            "${platform.games.grouped()} games · joined ${platform.joined} · " +
                "last game ${platform.lastActive}",
        )
        Spacer(Modifier.height(12.dp))
        platform.peaks.forEach { peak ->
            BasicText(
                text = "${peak.format} peak ${peak.rating}${peak.at?.let { " · $it" } ?: ""}",
                style = cvType.metaMono.copy(color = colors.onBackground),
            )
        }
        platform.puzzles?.let { puzzles ->
            BasicText(
                text = "puzzles peak ${puzzles.peak} · ${puzzles.solved.grouped()} solved",
                style = cvType.metaMono.copy(color = colors.onBackground),
            )
        }
        if (platform.provisional) {
            Spacer(Modifier.height(12.dp))
            BasicText(
                text =
                    "Every format on this account reads provisional. Rating deviation grew while " +
                        "it sat idle after ${platform.lastActive}, so what it shows is a last " +
                        "rating, not current form.",
                style = cvType.bodySmall,
            )
        }
        Spacer(Modifier.height(12.dp))
        // The card is the link. Named in text rather than by an arrow glyph, because the vendored
        // font cuts are Latin only and Skia paints a missing glyph as a tofu box.
        MonoMeta("opens the profile on ${platform.id}")
    }
}

// -------------------------------------------------------------------------------------------
// 5. The cast
// -------------------------------------------------------------------------------------------

@Composable
private fun CastSection(nav: CvNavState, columns: Int) {
    val ninth = chess.sessionDecay.firstOrNull { it.position == CAST_NINTH_GAME }
    val first = chess.sessionDecay.firstOrNull { it.position == 1 }
    val latestLine = chess.repertoire.lastOrNull()
    val scandinavianLine = latestLine?.openings?.firstOrNull { isScandinavian(it.name) }

    Section(eyebrow = "// the cast", title = "The cast") {
        BasicText(
            text =
                "Over in the Loopdown I give recurring production bugs names and personalities, " +
                    "because a bug you can name is a bug you can hunt. The same three keep turning " +
                    "up over the board, and unlike the ones at work, these came with their own " +
                    "receipts.",
            modifier = Modifier.widthIn(max = 680.dp),
            style = cvType.bodySmall,
        )
        Spacer(Modifier.height(16.dp))
        GhostButton(text = "Read the Loopdown", onClick = { nav.go(Route.Loopdown) })
        Spacer(Modifier.height(24.dp))

        val cast =
            buildList {
                add(
                    CastMember(
                        name = "The Flagfall",
                        figure = pctOf(chess.thesis.lossesOnTime),
                        note =
                            "of every loss, decided by the clock rather than the board. Not an " +
                                "opponent, a deadline.",
                        footnote = null,
                    ),
                )
                if (ninth != null) {
                    add(
                        CastMember(
                            name = "The Ninth Game",
                            figure = pctOf(ninth.winRate),
                            note =
                                "win rate by game nine of one sitting, against " +
                                    "${pctOf(first?.winRate ?: 0.0)} on game one. He should have " +
                                    "stopped at eight.",
                            footnote = "${plural(ninth.n, "game")}, a thin tail, shown with its n",
                        ),
                    )
                }
                add(
                    CastMember(
                        name = "The Returner",
                        figure = scandinavianLine?.let { pctOf(it.share) } ?: "-",
                        note =
                            "of games as Black are the Scandinavian again in " +
                                "${latestLine?.year ?: chess.span.to.take(4)}, after it was " +
                                "displaced almost entirely on the other account. First loves are a " +
                                "repertoire choice.",
                        footnote = null,
                    ),
                )
            }
        GridRows(cast, columns) { CastCard(it) }
    }
}

/** Ninth is where the session-decay tail turns over. The corpus is what says so, not this number. */
private const val CAST_NINTH_GAME = 9

private data class CastMember(
    val name: String,
    val figure: String,
    val note: String,
    val footnote: String?,
)

@Composable
private fun CastCard(member: CastMember) {
    val colors = cvColors
    CvCard(Modifier.fillMaxWidth(), glowOnHover = false) {
        BasicText(text = member.name, style = cvType.cardTitle)
        Spacer(Modifier.height(8.dp))
        BasicText(
            text = member.figure,
            style = cvType.mono.copy(fontWeight = FontWeight.Bold, color = colors.accent2),
        )
        Spacer(Modifier.height(8.dp))
        BasicText(text = member.note, style = cvType.bodySmall)
        if (member.footnote != null) {
            Spacer(Modifier.height(8.dp))
            MonoMeta(member.footnote)
        }
    }
}

@Composable
private fun ProvenanceNote() {
    Reveal {
        Column(Modifier.pageMeasure().padding(top = 24.dp)) {
            BasicText(
                text =
                    "Generated ${chess.generatedAt.take(10)} from the lichess and chess.com public " +
                        "APIs by scripts/gen-chess-stats.mjs, then emitted into Kotlin by " +
                        "scripts/gen-kotlin-data.mjs. Every number on this page is build output, " +
                        "not prose. Re-run them and the figures move.",
                modifier = Modifier.widthIn(max = 680.dp),
                style = cvType.metaMono,
            )
        }
    }
}

// -------------------------------------------------------------------------------------------
// 6. The second pass
// -------------------------------------------------------------------------------------------

@Composable
private fun SecondPassSection(columns: Int) {
    Reveal {
        Column(Modifier.pageMeasure().padding(top = CvSectionGap)) {
            CircuitDivider()
            Spacer(Modifier.height(CvSectionGap / 2))
            SectionEyebrow("// second pass")
            Spacer(Modifier.height(10.dp))
            SectionHeading("Four fields the first analysis never read")
            Spacer(Modifier.height(20.dp))
            BasicText(
                text =
                    "The first analysis measured what happens inside a game and never read four " +
                        "fields that were in every record: how the game was found, which time " +
                        "control, how it ended, and when the opening book ran out. Those four " +
                        "carry the least flattering findings in the corpus, which is exactly why " +
                        "they are here.",
                modifier = Modifier.widthIn(max = 680.dp),
                style = cvType.bodySmall,
            )
            Spacer(Modifier.height(24.dp))
            GridRows(listOf(0, 1, 2, 3), columns) { SecondPassCard(it) }
            Spacer(Modifier.height(16.dp))
            MonoMeta("${chessDeep.sampleSize.grouped()} lichess games · scripts/gen-chess-deep.mjs")
        }
    }
}

@Composable
private fun SecondPassCard(index: Int) {
    when (index) {
        0 ->
            DefinitionCard(
                title = "More clock does not help me",
                rows = chessDeep.byTimeControl.map { "${it.tc} · ${it.n.grouped()} games" to pct(it.winRate) },
                note =
                    "Ten times the thinking time moves the win rate by half a point. Whatever " +
                        "decides these games, it is not how long I get to look at them.",
            )
        1 ->
            DefinitionCard(
                title = "I leave theory on move one",
                rows = listOf("median book exit" to "ply ${chessDeep.book.medianPly}"),
                note =
                    "In the ${chessDeep.book.deep.n.grouped()} games where I stayed in a named " +
                        "opening to ply 8 or deeper, I won ${pct(chessDeep.book.deep.winRate)}, " +
                        "against ${pct(chessDeep.book.shallow.winRate)} across the " +
                        "${chessDeep.book.shallow.n.grouped()} where I was out by ply 4. The " +
                        "sample is small and I have never acted on it.",
            )
        2 ->
            DefinitionCard(
                title = "Where the game came from",
                rows = chessDeep.bySource.map { "${it.source} · ${it.n.grouped()}" to pct(it.winRate) },
                note =
                    "Matchmaking is a coin flip. Arena is a bloodbath. ${poolArenaGapSentence()} " +
                        "and the arena sample is small enough to stay a hypothesis.",
            )
        else ->
            DefinitionCard(
                title = "How they actually end",
                rows = chessDeep.byEnding.take(ENDING_ROWS_SHOWN).map { it.status to pct(it.share) },
                note =
                    "Across every game, not just decided ones: the clock ends more of them than " +
                        "checkmate and resignation combined.",
            )
    }
}

private const val ENDING_ROWS_SHOWN = 4

/**
 * ChessFindings.tsx types "twenty-one points" here. It is a subtraction of two generated figures,
 * so it is a subtraction here. If either row leaves the corpus the sentence loses its number rather
 * than keeping a stale one.
 */
private fun poolArenaGapSentence(): String {
    val pool = chessDeep.bySource.firstOrNull { it.source == "pool" }
    val arena = chessDeep.bySource.firstOrNull { it.source == "arena" }
    if (pool == null || arena == null) return "The gap between them is wide"
    return "The gap is ${oneDecimal(pool.winRate - arena.winRate)} points"
}

@Composable
private fun DefinitionCard(title: String, rows: List<Pair<String, String>>, note: String) {
    val colors = cvColors
    CvCard(Modifier.fillMaxWidth(), glowOnHover = false) {
        BasicText(text = title, style = cvType.cardTitle)
        Spacer(Modifier.height(12.dp))
        rows.forEach { (term, value) ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                BasicText(text = term, modifier = Modifier.weight(1f), style = cvType.metaMono)
                Spacer(Modifier.width(12.dp))
                BasicText(text = value, style = cvType.mono.copy(color = colors.accent))
            }
        }
        Spacer(Modifier.height(12.dp))
        BasicText(text = note, style = cvType.bodySmall)
    }
}

// -------------------------------------------------------------------------------------------
// 7. Rhythm: games against commits, on one clock
// -------------------------------------------------------------------------------------------

private const val HOURS = 24

private val rhythmGames = chessHours.games
private val rhythmCommits = chessHours.commits
private val commitSample = chessHours.commitSample
private val gameMax: Int = rhythmGames.maxOf { it.n }
private val commitMax: Int = rhythmCommits.maxOf { it.n }
private val gameTotal: Int = rhythmGames.sumOf { it.n }
private val peakGames = rhythmGames.first { it.n == gameMax }
private val peakCommits = rhythmCommits.first { it.n == commitMax }

/**
 * Win rate gets its own axis: on a 0-100% scale the whole series is a flat line through the middle.
 * Rounded out to the nearest 5% so the bounds are readable rather than exactly the extremes.
 */
private val winLo: Double = floor(rhythmGames.minOf { it.winRate } * 20.0) / 20.0
private val winHi: Double = ceil(rhythmGames.maxOf { it.winRate } * 20.0) / 20.0

/**
 * An hour holding a twentieth of the busiest hour's games swings its win rate on almost nothing, so
 * those points are drawn hollow and the cutoff is stated. Derived from the data, not chosen.
 */
private val thinCut: Int = round(gameMax * 0.05).toInt()

private val ChartHeight: Dp = 264.dp
private val ChartPadLeft: Dp = 46.dp
private val ChartPadRight: Dp = 52.dp
private val ChartPadTop: Dp = 22.dp
private val ChartPadBottom: Dp = 28.dp

/**
 * Kursi's gold, the site's own third series colour. The two curves take `accent` and `accent2` so
 * they follow a reskin; the win-rate line needs a hue neither of those owns.
 */
private val WinRateTint: Color = cvColor("#E8C874")

private fun hh(hour: Int): String = "${hour.toString().padStart(2, '0')}:00"

/** Inverse of the chart's x mapping. Pointer x in pixels to the hour band under it. */
private fun rhythmHourAt(x: Float, width: Float, padLeft: Float, padRight: Float): Int {
    val plot = width - padLeft - padRight
    if (plot <= 0f) return 0
    return (((x - padLeft) / plot) * HOURS).toInt().coerceIn(0, HOURS - 1)
}

private fun rhythmReadout(hour: Int): String {
    val here = rhythmGames.first { it.hour == hour }
    val commits = rhythmCommits.firstOrNull { it.hour == hour }?.n ?: 0
    return "At ${hh(hour)} IST: ${here.n.grouped()} games (${pctOf(here.n.toDouble() / gameMax)} of " +
        "his busiest hour) and ${commits.grouped()} commits " +
        "(${pctOf(commits.toDouble() / commitMax)} of theirs), winning ${pctOf(here.winRate)} of them."
}

/**
 * The canvas carries no text a screen reader can walk, so the whole figure is announced as one
 * sentence that moves with the selection, and the two caveats that change what the picture is
 * allowed to mean are real text under it rather than glyphs painted inside it. On the web both
 * caveats are drawn into the SVG; here they are better off outside, because outside they are
 * selectable, translatable and reflowable.
 */
@Composable
private fun RhythmSection() {
    var hour by remember { mutableStateOf(peakGames.hour) }
    Reveal {
        Column(Modifier.pageMeasure().padding(top = CvSectionGap)) {
            CircuitDivider()
            Spacer(Modifier.height(CvSectionGap / 2))
            SectionEyebrow("// rhythm")
            Spacer(Modifier.height(10.dp))
            SectionHeading("Games against commits, on one clock")
            Spacer(Modifier.height(20.dp))
            BasicText(
                text =
                    "When the games happen against when the commits happen, over the same clock. " +
                        "Chess peaks at ${hh(peakGames.hour)} with ${peakGames.n.grouped()} games; " +
                        "the commits peak at ${hh(peakCommits.hour)}. Each curve is drawn as a " +
                        "share of its own busiest hour: ${gameTotal.grouped()} games against a " +
                        "${commitSample.n.grouped()}-commit sample on one raw scale would flatten " +
                        "the commit curve into the axis and the overlay would say nothing.",
                modifier = Modifier.widthIn(max = 680.dp),
                style = cvType.bodySmall,
            )
            Spacer(Modifier.height(20.dp))
            RhythmChart(hour) { hour = it }
            Spacer(Modifier.height(16.dp))
            RhythmLegend(hour)
            Spacer(Modifier.height(16.dp))
            BasicText(
                text =
                    "Hours are IST. Commit author offsets are not consistent across this history, " +
                        "so they are normalised. Hollow win-rate points are hours holding fewer " +
                        "than ${thinCut.grouped()} games, where the rate swings on almost nothing.",
                modifier = Modifier.widthIn(max = 680.dp),
                style = cvType.metaMono,
            )
            Spacer(Modifier.height(8.dp))
            BasicText(
                text =
                    "Scope: every one of the ${gameTotal.grouped()} games in the corpus against " +
                        "${commitSample.n.grouped()} of the ${commitSample.total.grouped()} commits " +
                        "matching the same search since ${commitSample.from}. GitHub's search API " +
                        "limits how many results one query can return, so the commit half is a " +
                        "capped sample and the two windows are not the same length: the shapes are " +
                        "comparable and the volumes are not.",
                modifier = Modifier.widthIn(max = 680.dp),
                style = cvType.metaMono,
            )
        }
    }
}

@Composable
private fun RhythmChart(hour: Int, onHour: (Int) -> Unit) {
    val colors = cvColors
    val measurer = rememberTextMeasurer(cacheSize = 32)
    val axisStyle = cvType.metaMono
    val winAxisStyle = cvType.metaMono.copy(color = WinRateTint)
    val summary =
        "Two 24-hour distributions on a shared axis, each normalised to its own busiest hour: " +
            "${gameTotal.grouped()} chess games peaking at ${hh(peakGames.hour)} IST, and a capped " +
            "sample of ${commitSample.n.grouped()} of ${commitSample.total.grouped()} matching " +
            "commits since ${commitSample.from}, peaking at ${hh(peakCommits.hour)}. Win rate per " +
            "hour runs on a second axis from ${pctOf(winLo)} to ${pctOf(winHi)}. " +
            "${rhythmReadout(hour)} Left and right arrow keys move the selected hour."

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(ChartHeight)
                .background(colors.deepVoid.copy(alpha = 0.7f), ChartShape)
                .border(1.dp, colors.line, ChartShape)
                .semantics { contentDescription = summary }
                // onKeyEvent sits above focusable so the focused node's chain sees the key first.
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (event.key) {
                        Key.DirectionLeft -> {
                            onHour((hour + HOURS - 1) % HOURS)
                            true
                        }
                        Key.DirectionRight -> {
                            onHour((hour + 1) % HOURS)
                            true
                        }
                        else -> false
                    }
                }
                .focusable(),
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { at ->
                        onHour(
                            rhythmHourAt(
                                at.x,
                                size.width.toFloat(),
                                ChartPadLeft.toPx(),
                                ChartPadRight.toPx(),
                            ),
                        )
                    }
                }
                .pointerInput(Unit) {
                    // Scrubbing, the interaction the web's range input gave. Horizontal only, so a
                    // vertical drag still scrolls the page under it.
                    detectHorizontalDragGestures { change, _ ->
                        onHour(
                            rhythmHourAt(
                                change.position.x,
                                size.width.toFloat(),
                                ChartPadLeft.toPx(),
                                ChartPadRight.toPx(),
                            ),
                        )
                    }
                },
        ) {
            val padL = ChartPadLeft.toPx()
            val padR = ChartPadRight.toPx()
            val padT = ChartPadTop.toPx()
            val padB = ChartPadBottom.toPx()
            val plotW = size.width - padL - padR
            val plotH = size.height - padT - padB
            if (plotW <= 0f || plotH <= 0f) return@Canvas

            // Each hour is plotted at the centre of its band, so 0 and 23 are not stuck to the
            // frame and the two series line up over the same tick.
            fun xAt(h: Int): Float = padL + ((h + 0.5f) / HOURS) * plotW
            fun yAt(fraction: Float): Float = padT + (1f - fraction) * plotH
            fun yWin(rate: Double): Float = yAt(((rate - winLo) / (winHi - winLo)).toFloat())

            listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { g ->
                val y = yAt(g)
                drawLine(colors.line, Offset(padL, y), Offset(size.width - padR, y), 1f)
                val layout = measurer.measure("${(g * 100).toInt()}%", axisStyle)
                drawText(layout, topLeft = Offset(padL - layout.size.width - 8f, y - layout.size.height / 2f))
            }

            listOf(winLo, (winLo + winHi) / 2.0, winHi).forEach { r ->
                val layout = measurer.measure(pctOf(r), winAxisStyle)
                drawText(layout, topLeft = Offset(size.width - padR + 8f, yWin(r) - layout.size.height / 2f))
            }

            val gamePath = Path()
            rhythmGames.forEachIndexed { i, h ->
                val x = xAt(h.hour)
                val y = yAt(h.n.toFloat() / gameMax)
                if (i == 0) gamePath.moveTo(x, y) else gamePath.lineTo(x, y)
            }
            val filled = Path().apply {
                addPath(gamePath)
                lineTo(xAt(HOURS - 1), yAt(0f))
                lineTo(xAt(0), yAt(0f))
                close()
            }
            drawPath(filled, colors.accent.copy(alpha = 0.12f))
            drawPath(gamePath, colors.accent, style = Stroke(2.dp.toPx()))

            val commitPath = Path()
            rhythmCommits.forEachIndexed { i, h ->
                val x = xAt(h.hour)
                val y = yAt(h.n.toFloat() / commitMax)
                if (i == 0) commitPath.moveTo(x, y) else commitPath.lineTo(x, y)
            }
            drawPath(
                path = commitPath,
                color = colors.accent2,
                style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))),
            )

            val winPath = Path()
            rhythmGames.forEachIndexed { i, h ->
                val x = xAt(h.hour)
                val y = yWin(h.winRate)
                if (i == 0) winPath.moveTo(x, y) else winPath.lineTo(x, y)
            }
            drawPath(winPath, WinRateTint.copy(alpha = 0.85f), style = Stroke(1.5.dp.toPx()))

            rhythmGames.forEach { h ->
                val centre = Offset(xAt(h.hour), yWin(h.winRate))
                val radius = if (h.hour == hour) 4.dp.toPx() else 2.6.dp.toPx()
                if (h.n >= thinCut) drawCircle(WinRateTint, radius, centre)
                drawCircle(WinRateTint, radius, centre, style = Stroke(1.2.dp.toPx()))
            }

            drawLine(
                color = colors.onBackground,
                start = Offset(xAt(hour), padT),
                end = Offset(xAt(hour), padT + plotH),
                strokeWidth = 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f)),
            )

            listOf(0, 6, 12, 18, HOURS - 1).forEach { t ->
                val layout = measurer.measure(hh(t), axisStyle)
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(xAt(t) - layout.size.width / 2f, padT + plotH + 6f),
                )
            }
        }
    }
}

private val ChartShape = RoundedCornerShape(16.dp)

/**
 * The readout under the chart. It is the same three figures the canvas already draws for the
 * selected hour, in text, which is what makes the selection mean anything to a reader who is not
 * looking at the shapes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RhythmLegend(hour: Int) {
    val colors = cvColors
    val here = rhythmGames.first { it.hour == hour }
    val commits = rhythmCommits.firstOrNull { it.hour == hour }?.n ?: 0
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LegendItem(colors.accent, "games ${here.n.grouped()}")
            LegendItem(colors.accent2, "commits (dashed) ${commits.grouped()}")
            LegendItem(WinRateTint, "win rate ${pctOf(here.winRate)}")
        }
        MonoMeta("${hh(hour)} IST. Tap or drag the chart, or focus it and use the arrow keys.")
    }
}

@Composable
private fun LegendItem(tint: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(tint, CircleShape))
        Spacer(Modifier.width(8.dp))
        BasicText(text = label, style = cvType.metaMono.copy(color = tint))
    }
}


// -------------------------------------------------------------------------------------------
// Tables
// -------------------------------------------------------------------------------------------

/**
 * A table that scrolls sideways inside its own container rather than pushing the page wide, with a
 * stated width per column so the overflow actually has somewhere to go on a phone.
 *
 * [label] and `focusable()` are the pair that makes it reachable: ChessFindings.tsx needed
 * `tabIndex={0}`, `role="region"` and an `aria-label` for the same reason, because without them a
 * keyboard-only visitor on a narrow viewport cannot reach the columns past the fold.
 */
@Composable
internal fun ScrollingTable(
    label: String,
    columns: List<Pair<String, Dp>>,
    rows: @Composable ColumnScope.() -> Unit,
) {
    val colors = cvColors
    Column(
        Modifier
            .fillMaxWidth()
            .semantics { contentDescription = label }
            .horizontalScroll(rememberScrollState())
            .focusable(),
    ) {
        Row(
            Modifier
                .drawBehind {
                    drawRect(
                        colors.line,
                        topLeft = Offset(0f, size.height - 1.dp.toPx()),
                        size = Size(size.width, 1.dp.toPx()),
                    )
                }
                .padding(bottom = 8.dp),
        ) {
            columns.forEach { (title, width) ->
                BasicText(
                    text = title.uppercase(),
                    modifier = Modifier.width(width).padding(end = 16.dp),
                    style = cvType.metaMono.copy(fontWeight = FontWeight.SemiBold),
                )
            }
        }
        rows()
    }
}

@Composable
internal fun TableRow(cells: @Composable RowScope.() -> Unit) {
    val colors = cvColors
    Row(
        modifier =
            Modifier
                .drawBehind {
                    drawRect(
                        colors.line.copy(alpha = 0.5f),
                        topLeft = Offset(0f, size.height - 1.dp.toPx()),
                        size = Size(size.width, 1.dp.toPx()),
                    )
                }
                .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = cells,
    )
}

@Composable
internal fun Cell(text: String, width: Dp, strong: Boolean = false, tint: Color? = null) {
    val colors = cvColors
    BasicText(
        text = text,
        modifier = Modifier.width(width).padding(end = 16.dp),
        style = cvType.mono.copy(color = tint ?: if (strong) colors.onBackground else colors.muted),
    )
}

// -------------------------------------------------------------------------------------------
// Self-check
// -------------------------------------------------------------------------------------------

/**
 * ponytail: one runnable check instead of a test module, the same shape `navSelfCheck` and
 * `labScreenSelfCheck` already have. Must be called from `selfCheck()` in jvmMain's Prerender.kt:
 * nothing under composeMain runs on its own, and that file belongs to the spine.
 *
 * It guards the four things in this file that are logic rather than layout, and every one of them
 * fails quietly rather than loudly: a formatter that prints "48.50000001%", a decile axis whose
 * step stops dividing 100 when the generator changes resolution, a repertoire row with no openings
 * in it, and the chart's pointer mapping drifting away from the chart's own x mapping so that a tap
 * selects a different hour from the one under the cursor.
 */
@Suppress("MagicNumber")
internal fun chessScreenSelfCheck() {
    check(oneDecimal(48.94) == "48.9") { "oneDecimal: ${oneDecimal(48.94)}" }
    check(oneDecimal(1.0) == "1.0") { "oneDecimal keeps the tenth: ${oneDecimal(1.0)}" }
    check(pctOf(0.416) == "41.6%") { "pctOf: ${pctOf(0.416)}" }
    check(pct(49.2) == "49.2%") { "pct passes an already-scaled figure through: ${pct(49.2)}" }
    check(plural(1, "game") == "1 game") { "plural singular: ${plural(1, "game")}" }
    check(plural(69, "game") == "69 games") { "plural: ${plural(69, "game")}" }

    check(decileStep * chess.thesis.deciles.size == 100) { "the decile axis no longer covers 100%" }
    check(maxGap > 0.0) { "every clock gap is zero, so the bar column draws nothing" }
    check(repertoire.isNotEmpty()) { "no repertoire years" }
    check(repertoire.all { it.scandinavian >= 0.0 }) { "a negative Scandinavian share" }
    check(handoff != null && handoff.chesscom > handoff.lichess) { "the handoff year is not a handoff" }

    check(rhythmGames.size == HOURS && rhythmCommits.size == HOURS) { "the rhythm chart is not 24 hours" }
    check(winHi > winLo) { "the win-rate axis has no range" }
    check(thinCut in 1..gameMax) { "the thin cutoff is outside the sample: $thinCut" }

    // The pointer mapping is the inverse of the chart's own `xAt`, so a round trip through both has
    // to land back on the hour it started from, at both ends of the axis and in the middle.
    val width = 800f
    val padL = 46f
    val padR = 52f
    val plot = width - padL - padR
    listOf(0, 1, 12, HOURS - 1).forEach { h ->
        val x = padL + ((h + 0.5f) / HOURS) * plot
        val back = rhythmHourAt(x, width, padL, padR)
        check(back == h) { "rhythmHourAt round trip: $h became $back" }
    }
    check(rhythmHourAt(-40f, width, padL, padR) == 0) { "a tap left of the plot must clamp to 00:00" }
    check(rhythmHourAt(width + 40f, width, padL, padR) == HOURS - 1) { "a tap right of the plot must clamp to 23:00" }

    chessScenePanesSelfCheck()
    chessGuessPaneSelfCheck()
}
