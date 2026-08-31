package com.siddharth.cv.shared.chess

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.siddharth.cv.shared.anthology.grouped
import com.siddharth.cv.shared.data.generated.ChessArcPoint
import com.siddharth.cv.shared.data.generated.chessArc
import com.siddharth.cv.shared.data.generated.chessGraveyard
import com.siddharth.cv.shared.data.generated.chessRepertoireByPlatform
import com.siddharth.cv.shared.data.generated.chess
import com.siddharth.cv.shared.theme.MonoMeta
import com.siddharth.cv.shared.theme.cvColor
import com.siddharth.cv.shared.theme.cvColors
import com.siddharth.cv.shared.theme.cvType

/**
 * The three panes ChessRoom.tsx draws with three.js, at the fidelity ChessRoom.tsx itself drops to
 * when `supportsWebGL()` says no.
 *
 * READ THIS BEFORE CHANGING ANYTHING HERE. Nothing below is an approximation of a 3D scene. Each of
 * the three is the OTHER branch of a `webgl ? scene : fallback` ternary that already ships on the
 * web, rendered from the same numbers the scene would have been built from:
 *
 *  - **The Arc.** `ChessArcScene.tsx` is out. What is here is `src/ChessArc.tsx`, the flat SVG band
 *    chart the web renders under `prefers-reduced-motion` or without a GPU, line for line: one band
 *    per platform, each on its own vertical scale, only the time axis shared.
 *  - **The Graveyard.** `GraveyardScene.tsx` is out. What is here is the ranked list its
 *    `ScenePane` carries as the canvas's text alternative, plus the losses/wins toggle and the
 *    sample caption, which are outside the WebGL branch on the web too.
 *  - **Repertoire.** `RepertoireTreeScene.tsx` is out. What is here is the year scrubber and the
 *    written table `RepertoirePane` falls back to, over the same `repertoireByPlatform` slices.
 *
 * The three scenes are behind the same wall `/blueprint` is: this port ships no WebGL surface, and
 * a flat drawing wearing a 3D scene's caption is the one thing it is not allowed to ship. So each
 * pane says in its own copy what is missing rather than quietly standing in for it.
 *
 * The README said all three were blocked on the emitter rather than the renderer, and that was
 * right. It was also cheaper than it read: `chess.arc` is the weekly downsample already sitting in
 * the bundled summary (190 points), and the graveyard, the repertoire slices and the quiz
 * positions are 13 KB of `corpus.json` between them. The whole cost was four `vals` in
 * `gen-kotlin-data.mjs`.
 */

// -------------------------------------------------------------------------------------------
// 1. The Arc: one band per platform, each on its own scale
// -------------------------------------------------------------------------------------------

/**
 * Format style by first-appearance order, so a format keeps the same colour AND the same dash in
 * every band. The dash is the point: meaning must not rest on colour alone, and each range is
 * written out as text beside the swatch as well. Third tint is the site's own already-checked
 * series colour, the one `writingMeta.ts` uses.
 */
private val ArcThirdTint: Color = cvColor("#F0883E")
private val ArcDashMedium: List<Float> = listOf(8f, 4f)
private val ArcDashFine: List<Float> = listOf(2f, 4f)

private val ArcBandHeight: Dp = 110.dp
private val ArcStroke: Dp = 1.75.dp

/** The `INSET / W` fraction ChessArc.tsx works in: 6 units either side of a 1000-unit viewBox. */
private const val ARC_INSET_FRACTION = 0.006f

private data class ArcLine(
    val format: String,
    val styleIndex: Int,
    val min: Int,
    val max: Int,
    val points: List<ChessArcPoint>,
)

private data class ArcBand(val platform: String, val ratingMin: Int, val ratingMax: Int, val lines: List<ArcLine>)

private val arcFormatOrder: List<String> = chessArc.series.map { it.format }.distinct()

private val arcBands: List<ArcBand> =
    chessArc.series.map { it.platform }.distinct().map { platform ->
        val own = chessArc.series.filter { it.platform == platform }
        val ratings = own.flatMap { series -> series.points.map { it.rating } }
        ArcBand(
            platform = platform,
            ratingMin = ratings.min(),
            ratingMax = ratings.max(),
            lines =
                own.map { series ->
                    ArcLine(
                        format = series.format,
                        styleIndex = arcFormatOrder.indexOf(series.format),
                        min = series.points.minOf { it.rating },
                        max = series.points.maxOf { it.rating },
                        points = series.points,
                    )
                },
        )
    }

private val arcStampMin: Double = chessArc.series.minOf { series -> series.points.minOf { it.t } }
private val arcStampMax: Double = chessArc.series.maxOf { series -> series.points.maxOf { it.t } }

/** The whole run in one line per band, which is what the canvas has instead of readable text. */
private fun arcBandSentence(band: ArcBand): String =
    "${band.platform}, own scale ${band.ratingMin} to ${band.ratingMax}: " +
        band.lines.joinToString("; ") { "${it.format} ${it.min} to ${it.max}" }

@Composable
private fun arcTintFor(styleIndex: Int): Color {
    val colors = cvColors
    return when (styleIndex % 3) {
        0 -> colors.accent
        1 -> colors.accent2
        else -> ArcThirdTint
    }
}

private fun arcDashFor(styleIndex: Int): List<Float>? =
    when (styleIndex % 3) {
        0 -> null
        1 -> ArcDashMedium
        else -> ArcDashFine
    }

@Composable
internal fun ArcPane() {
    Section("// the arc", "Where the rating went, one band per platform") {
        BasicText(
            text =
                "Each platform sits in its own band on its own vertical scale. The two rating pools " +
                    "are not comparable, so a shared axis would draw a decline the games do not " +
                    "support; only the time axis is shared. The points are the weekly sample, and " +
                    "the true per-format peaks are on the profile cards above.",
            modifier = Modifier.widthIn(max = 680.dp),
            style = cvType.bodySmall,
        )
        Spacer(Modifier.height(20.dp))
        arcBands.forEach { band ->
            ArcBandFigure(band)
            Spacer(Modifier.height(18.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MonoMeta(chessArc.fromDay)
            MonoMeta(chessArc.toDay)
        }
        Spacer(Modifier.height(16.dp))
        BasicText(
            text = arcCoverageSentence(),
            modifier = Modifier.widthIn(max = 680.dp),
            style = cvType.metaMono,
        )
        Spacer(Modifier.height(8.dp))
        BasicText(
            text =
                "The web draws this as twin ribbons in three.js. That scene is not in this port. " +
                    "This is ChessArc.tsx, the flat fallback the site itself renders under reduced " +
                    "motion or on a machine with no WebGL, and it carries the same numbers.",
            modifier = Modifier.widthIn(max = 680.dp),
            style = cvType.metaMono,
        )
        handoff?.let { year ->
            Spacer(Modifier.height(16.dp))
            BasicText(
                text =
                    "Where the arc changes hands: in ${year.year} chess.com carried " +
                        "${year.chesscom.grouped()} games against lichess's ${year.lichess.grouped()}. " +
                        "That is the year the games change hands. Ratings earned either side of it " +
                        "come out of different pools and are never joined into one line.",
                modifier = Modifier.widthIn(max = 680.dp),
                style = cvType.bodySmall,
            )
        }
    }
}

/**
 * Which platforms are in the chart, and why the other one is not. lichess's rating-history endpoint
 * returns nothing since the account went quiet, so there is no lichess series to band. Its peaks
 * survive on the profile cards because the generator pins them rather than letting an empty fetch
 * write them away. Derived, so it stops saying this the day lichess answers again.
 */
private fun arcCoverageSentence(): String {
    val drawn = arcBands.map { it.platform }.toSet()
    val missing = chess.platforms.map { it.id }.filter { it !in drawn }
    val head = "${drawn.joinToString(" and ")} in the chart, ${chessArc.fromDay} to ${chessArc.toDay}."
    if (missing.isEmpty()) return head
    return "$head No band for ${missing.joinToString(" or ")}: its rating history is no longer " +
        "served by its API, so there is no series to draw. The peaks it did reach are on its " +
        "profile card above, pinned so an empty fetch cannot write them away."
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ArcBandFigure(band: ArcBand) {
    val colors = cvColors
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            BasicText(
                text = band.platform.uppercase(),
                style = cvType.metaMono.copy(fontWeight = FontWeight.SemiBold, color = colors.onBackground),
            )
            MonoMeta("own scale ${band.ratingMin}-${band.ratingMax}")
        }
        Spacer(Modifier.height(6.dp))
        ArcBandCanvas(band)
        Spacer(Modifier.height(8.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            band.lines.forEach { line ->
                val tint = arcTintFor(line.styleIndex)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ArcSwatch(tint, arcDashFor(line.styleIndex))
                    Spacer(Modifier.width(8.dp))
                    BasicText(
                        text = "${line.format} ${line.min}-${line.max}",
                        style = cvType.metaMono.copy(color = tint),
                    )
                }
            }
        }
    }
}

@Composable
private fun ArcSwatch(tint: Color, dash: List<Float>?) {
    Canvas(Modifier.size(width = 20.dp, height = 6.dp)) {
        drawLine(
            color = tint,
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = 2.dp.toPx(),
            pathEffect = dash?.let { PathEffect.dashPathEffect(it.map { u -> u * density }.toFloatArray()) },
        )
    }
}

@Composable
private fun ArcBandCanvas(band: ArcBand) {
    val colors = cvColors
    val shape = RoundedCornerShape(12.dp)
    val tints = band.lines.map { arcTintFor(it.styleIndex) }
    Box(
        Modifier
            .fillMaxWidth()
            .height(ArcBandHeight)
            .background(colors.deepVoid.copy(alpha = 0.7f), shape)
            .border(1.dp, colors.line, shape)
            .semantics { contentDescription = arcBandSentence(band) },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val inset = size.width * ARC_INSET_FRACTION
            val plotW = size.width - 2f * inset
            val span = (arcStampMax - arcStampMin).takeIf { it > 0.0 } ?: 1.0
            if (plotW <= 0f || size.height <= 0f) return@Canvas
            fun xAt(t: Double): Float = inset + (((t - arcStampMin) / span) * plotW).toFloat()

            chessArc.yearTicks.forEach { tick ->
                val x = xAt(tick)
                drawLine(colors.line, Offset(x, 0f), Offset(x, size.height), 1f)
            }

            val ratingSpan = (band.ratingMax - band.ratingMin).takeIf { it > 0 } ?: 1
            band.lines.forEachIndexed { index, line ->
                val path = Path()
                line.points.forEachIndexed { i, point ->
                    val x = xAt(point.t)
                    val y = size.height - ((point.rating - band.ratingMin).toFloat() / ratingSpan) * size.height
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(
                    path = path,
                    color = tints[index],
                    style =
                        Stroke(
                            width = ArcStroke.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                            pathEffect =
                                arcDashFor(line.styleIndex)?.let { dash ->
                                    PathEffect.dashPathEffect(dash.map { it * density }.toFloatArray())
                                },
                        ),
                )
            }
        }
    }
}

// -------------------------------------------------------------------------------------------
// 2. The Graveyard: where the games actually end
// -------------------------------------------------------------------------------------------

private const val GRAVEYARD_TOP = 8

/** Index 0 is a1 and 63 is h8, the convention the generator's square matrix fixed. */
internal fun squareName(index: Int): String = "${"abcdefgh"[index % 8]}${index / 8 + 1}"

private val graveyardViews: List<String> = listOf("losses", "wins")

private fun graveyardCounts(view: String): List<Int> =
    if (view == "wins") chessGraveyard.wins else chessGraveyard.losses

private fun graveyardTop(view: String): List<Pair<String, Int>> =
    graveyardCounts(view)
        .mapIndexed { index, n -> squareName(index) to n }
        .sortedByDescending { it.second }
        .take(GRAVEYARD_TOP)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GraveyardPane() {
    var view by remember { mutableStateOf(graveyardViews.first()) }
    val chesscom = chess.platforms.firstOrNull { it.id == "chess.com" }
    val lichess = chess.platforms.firstOrNull { it.id == "lichess" }

    Section("// the graveyard", "Where the games actually end") {
        BasicText(
            text =
                "Each square counts the $view whose FINAL position still had a piece, either " +
                    "side's, standing on it." +
                    if (chesscom != null && lichess != null) {
                        " This matrix is chess.com's ${chesscom.games.grouped()} games only. " +
                            "lichess's ${lichess.games.grouped()} are not in it: its export ships " +
                            "no FEN, so their final positions were never recorded."
                    } else {
                        ""
                    },
            modifier = Modifier.widthIn(max = 680.dp),
            style = cvType.bodySmall,
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            graveyardViews.forEach { option ->
                ChessTogglePill(
                    label = option,
                    selected = view == option,
                    onSelect = { view = option },
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        BasicText(
            text = "Busiest squares at the end of a game, $view:",
            style = cvType.bodySmall,
        )
        Spacer(Modifier.height(10.dp))
        FlowRow(
            // ponytail: no contentDescription here. The ranked list IS the text alternative the
            // scene needed, so a summary on the container would only make a screen reader say it
            // twice. The arc's canvas gets one because a canvas has no text at all.
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            graveyardTop(view).forEachIndexed { rank, (square, n) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicText(
                        text = "${rank + 1}. $square",
                        style = cvType.metaMono.copy(color = cvColors.onBackground, fontWeight = FontWeight.SemiBold),
                    )
                    Spacer(Modifier.width(6.dp))
                    MonoMeta(n.grouped())
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        BasicText(
            text =
                "The web draws these 64 counts as a height map in three.js, one column per square. " +
                    "That scene is not in this port, and no flat board stands in for it. This is the " +
                    "ranked list the site's own non-WebGL branch shows instead, from the same matrix.",
            modifier = Modifier.widthIn(max = 680.dp),
            style = cvType.metaMono,
        )
    }
}

// -------------------------------------------------------------------------------------------
// 3. Repertoire: the two lines that moved, tracked within each platform
// -------------------------------------------------------------------------------------------

private const val FOCUS_LINES = 2

/** A run needs two quotable years to be a run at all; one point is not a direction. */
private const val MIN_RUN = 2
private val repertoirePlatforms: List<String> = listOf("lichess", "chesscom")

internal fun platformLabel(key: String): String = if (key == "lichess") "lichess" else "chess.com"

/** `0.4107` to `"41.1%"`; a thin slice has no percentage to quote and says so instead. */
internal fun sharePct(share: Double?): String = if (share == null) "thin" else "${oneDecimal(share * 100)}%"

private data class SharePoint(val year: String, val key: String, val share: Double?, val thin: Boolean)

/** One opening's full WITHIN-platform history, year by year. Absence is a genuine zero: the
 *  generator tracks the union of every platform-year's top five, so a missing line means no games. */
private fun shareSeries(name: String): List<SharePoint> =
    chessRepertoireByPlatform.flatMap { year ->
        year.platforms.map { slice ->
            val found = slice.openings.firstOrNull { it.name == name }
            SharePoint(
                year = year.year,
                key = slice.key,
                share = if (slice.thin) null else (found?.share ?: 0.0),
                thin = slice.thin,
            )
        }
    }

/**
 * The lines whose share moved most, summed within each platform and then added, never across the
 * handoff. Summing across it would manufacture a swing out of the platform change itself. Thin
 * platform-years are dropped rather than quoted.
 */
private val focusLines: List<String> =
    buildMap<String, Double> {
        repertoirePlatforms.forEach { key ->
            val slices =
                chessRepertoireByPlatform.flatMap { year ->
                    year.platforms.filter { it.key == key && !it.thin }
                }
            if (slices.size < MIN_RUN) return@forEach
            val names = slices.flatMap { slice -> slice.openings.map { it.name } }.toSet()
            names.forEach { name ->
                val shares = slices.map { slice -> slice.openings.firstOrNull { it.name == name }?.share ?: 0.0 }
                put(name, (this[name] ?: 0.0) + (shares.max() - shares.min()))
            }
        }
    }
        .entries
        .sortedWith(compareByDescending<Map.Entry<String, Double>> { it.value }.thenBy { it.key })
        .take(FOCUS_LINES)
        .map { it.key }

/** "lichess 41.1% (2019) to 3.6% (2023)", per platform, never spliced across the handoff. */
private fun focusRuns(name: String): List<String> {
    val series = shareSeries(name)
    return repertoirePlatforms.mapNotNull { key ->
        val run = series.filter { it.key == key && !it.thin }
        if (run.size < MIN_RUN) {
            null
        } else {
            val from = run.first()
            val to = run.last()
            "${platformLabel(key)} ${sharePct(from.share)} (${from.year}) to ${sharePct(to.share)} (${to.year})"
        }
    }
}

/** Empty when the handoff has not happened yet in the data, which is the only honest default. */
private val handoffWallSentence: String =
    handoff?.let {
        " The wall in the web's scene is the ${it.year} handoff: the fall on lichess and the return " +
            "on chess.com are two separate observations on two different sites, and neither is " +
            "drawn as one line."
    } ?: ""

private val YearColumn: Dp = 64.dp
private val PlatformColumn: Dp = 96.dp
private val BlackGamesColumn: Dp = 120.dp
private val ShareColumn: Dp = 150.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RepertoirePane() {
    var year by remember { mutableStateOf(chessRepertoireByPlatform.first().year) }
    val selected = chessRepertoireByPlatform.first { it.year == year }

    Section("// repertoire", "The two lines that moved, within each platform") {
        BasicText(
            text =
                "The two lines whose share of his games as Black moved most, tracked WITHIN each " +
                    "platform.$handoffWallSentence",
            modifier = Modifier.widthIn(max = 680.dp),
            style = cvType.bodySmall,
        )
        Spacer(Modifier.height(20.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            chessRepertoireByPlatform.forEach { row ->
                ChessTogglePill(label = row.year, selected = row.year == year, onSelect = { year = row.year })
            }
        }
        Spacer(Modifier.height(12.dp))
        MonoMeta(
            selected.platforms.joinToString(" · ") {
                "${platformLabel(it.key)} ${it.blackGames.grouped()} as Black" +
                    if (it.thin) " (thin)" else ""
            },
        )
        Spacer(Modifier.height(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            focusLines.forEach { name ->
                BasicText(
                    text = "$name: ${focusRuns(name).joinToString(" · ")}",
                    modifier = Modifier.widthIn(max = 680.dp),
                    style = cvType.bodySmall,
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        RepertoireTable(year)
        Spacer(Modifier.height(12.dp))
        BasicText(
            text =
                "Share of that platform-year's games as Black. A platform only appears in a year it " +
                    "was actually played, and a sample the generator marked thin carries no " +
                    "percentage at all.",
            modifier = Modifier.widthIn(max = 680.dp),
            style = cvType.metaMono,
        )
        Spacer(Modifier.height(8.dp))
        BasicText(
            text =
                "The web draws this as an opening tree in three.js with the same year scrubber. That " +
                    "scene is not in this port. The table is what the site falls back to without " +
                    "WebGL, and it was always where the actual argument lived.",
            modifier = Modifier.widthIn(max = 680.dp),
            style = cvType.metaMono,
        )
    }
}

/**
 * The scrubber has to select something a reader can see. The web's `<input type=range>` drives a
 * 3D scene that is not here, so here it tints the year's rows in the table instead of moving a
 * camera. Compose common has no range input without pulling Material in, which this port does not
 * depend on, so eight years are eight pills.
 */
@Composable
private fun RepertoireTable(year: String) {
    val colors = cvColors
    val columns =
        listOf("year" to YearColumn, "platform" to PlatformColumn, "games as black" to BlackGamesColumn) +
            focusLines.map { it to ShareColumn }
    ScrollingTable(
        label =
            "Share of each platform-year's games as Black held by ${focusLines.joinToString(" and ")}. " +
                "Scrolls sideways.",
        columns = columns,
    ) {
        chessRepertoireByPlatform.forEach { row ->
            val tint = if (row.year == year) colors.accent else null
            row.platforms.forEach { slice ->
                TableRow {
                    Cell(row.year, YearColumn, strong = true, tint = tint)
                    Cell(platformLabel(slice.key), PlatformColumn, tint = tint)
                    Cell(slice.blackGames.grouped(), BlackGamesColumn, tint = tint)
                    focusLines.forEach { name ->
                        val opening = slice.openings.firstOrNull { it.name == name }
                        Cell(
                            text =
                                if (slice.thin) {
                                    "thin (n=${opening?.count ?: 0})"
                                } else {
                                    sharePct(opening?.share ?: 0.0)
                                },
                            width = ShareColumn,
                            tint = tint,
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------------
// Shared control
// -------------------------------------------------------------------------------------------

/**
 * One pill in a mutually exclusive strip. [selectable] with [Role.RadioButton] rather than a plain
 * clickable, the same call `LabTab` makes: the web's `aria-pressed` toggles are a radio group to a
 * screen reader, and "selected" is the state that matters.
 */
@Composable
internal fun ChessTogglePill(label: String, selected: Boolean, onSelect: () -> Unit) {
    val colors = cvColors
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier =
            Modifier
                .background(if (selected) colors.accent.copy(alpha = 0.15f) else Color.Transparent, shape)
                .border(1.dp, if (selected) colors.accent else colors.line, shape)
                .selectable(
                    selected = selected,
                    interactionSource = interaction,
                    // indication = null sitewide: the site draws its own focus and hover treatment.
                    indication = null,
                    role = Role.RadioButton,
                    onClick = onSelect,
                )
                .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        BasicText(
            text = label,
            style =
                cvType.metaMono.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) colors.accent else colors.muted,
                ),
        )
    }
}

// -------------------------------------------------------------------------------------------
// Self-check
// -------------------------------------------------------------------------------------------

/**
 * ponytail: one runnable check, called from [chessScreenSelfCheck]. It guards the four things here
 * that are derivation rather than layout, every one of which fails quietly: a band whose own scale
 * has collapsed to a point, a square index that no longer maps a1 to h8, a swing ranking that
 * quietly quotes a thin platform-year, and a run sentence spliced across the handoff.
 */
@Suppress("MagicNumber")
internal fun chessScenePanesSelfCheck() {
    check(arcBands.isNotEmpty()) { "the arc has no bands to draw" }
    check(arcBands.all { it.ratingMax > it.ratingMin }) { "an arc band has no vertical range" }
    check(arcStampMax > arcStampMin) { "the arc has no time range" }
    check(chessArc.yearTicks.all { it in arcStampMin..arcStampMax }) { "a year gridline falls outside the arc" }
    // The chart and the profile card have to agree. gen-chess-stats.mjs asserts the weekly
    // downsample never clips a peak, and this is the same assertion on the Kotlin side of the
    // emitter: a legend that quotes a lower ceiling than the card above it is a visible lie.
    arcBands.forEach { band ->
        val card = chess.platforms.firstOrNull { it.id == band.platform } ?: return@forEach
        band.lines.forEach { line ->
            val peak = card.peaks.firstOrNull { it.format == line.format } ?: return@forEach
            check(line.max == peak.rating) {
                "${band.platform} ${line.format} plots a maximum of ${line.max} against a card peak of ${peak.rating}"
            }
        }
    }

    check(squareName(0) == "a1") { "square 0 is not a1: ${squareName(0)}" }
    check(squareName(63) == "h8") { "square 63 is not h8: ${squareName(63)}" }
    check(chessGraveyard.losses.size == 64 && chessGraveyard.wins.size == 64) { "the graveyard is not 64 squares" }
    graveyardViews.forEach { view ->
        val top = graveyardTop(view)
        check(top.size == GRAVEYARD_TOP) { "$view has fewer than $GRAVEYARD_TOP ranked squares" }
        check(top.first().second >= top.last().second) { "$view is not ranked" }
    }

    check(focusLines.size == FOCUS_LINES) { "the repertoire focus is ${focusLines.size} lines" }
    check(focusLines.distinct().size == focusLines.size) { "the repertoire focus repeats a line" }
    // A thin platform-year prints "thin" and never a percentage, on either side of the handoff.
    chessRepertoireByPlatform.forEach { year ->
        year.platforms.filter { it.thin }.forEach { slice ->
            check(shareSeries(focusLines.first()).any { it.year == year.year && it.key == slice.key && it.share == null }) {
                "thin slice ${year.year}/${slice.key} still quotes a share"
            }
        }
    }
    check(sharePct(null) == "thin") { "a null share must not print as a number" }
    check(sharePct(0.4107) == "41.1%") { "sharePct: ${sharePct(0.4107)}" }
    // Each run stays inside one platform: a sentence naming both sites would be spliced across it.
    focusLines.forEach { name ->
        focusRuns(name).forEach { run ->
            check(run.startsWith("lichess ") || run.startsWith("chess.com ")) { "run is not platform-scoped: $run" }
        }
    }
}
