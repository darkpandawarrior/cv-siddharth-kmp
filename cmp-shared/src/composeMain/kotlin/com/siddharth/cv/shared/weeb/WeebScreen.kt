@file:OptIn(ExperimentalLayoutApi::class)

package com.siddharth.cv.shared.weeb

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.siddharth.cv.shared.data.generated.WeebBucket
import com.siddharth.cv.shared.data.generated.WeebStale
import com.siddharth.cv.shared.data.generated.weeb
import com.siddharth.cv.shared.theme.CvCard
import com.siddharth.cv.shared.theme.CvContentMaxWidth
import com.siddharth.cv.shared.theme.CvGutter
import com.siddharth.cv.shared.theme.CvSectionGap
import com.siddharth.cv.shared.theme.GhostButton
import com.siddharth.cv.shared.theme.MonoMeta
import com.siddharth.cv.shared.theme.Reveal
import com.siddharth.cv.shared.theme.SectionEyebrow
import com.siddharth.cv.shared.theme.SectionHeading
import com.siddharth.cv.shared.theme.cvColors
import com.siddharth.cv.shared.theme.cvType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Weeb Central, the port of cv-siddharth/src/WeebRoom.tsx.
 *
 * Worth saying up front, because the shape of this file follows from it: /weeb is not a browsable
 * watchlist. What ships in `weeb.*` is the *aggregate* (`byWatch`, `scoreDist`, `caughtUp`) plus
 * exactly one enumerated list: the titles marked caught-up that AniList says have a sequel out. A
 * watchlist is a list and nobody needs another one. What earns the page is that the schema
 * confesses three things its rows never say:
 *
 *  1. There is no "Dropped" status, so titles pile up in "Paused" instead, and almost none of them
 *     are caught up. "Paused" is what quitting looks like when the schema has no word for it.
 *  2. The bottom of the score scale has never once been spent.
 *  3. A hand-kept list cannot see the present, which is the whole reason finding three exists:
 *     the gap only becomes visible when something outside the list is asked.
 *
 * No count appears anywhere in this file, comments included. Every figure is read out of `weeb.*`
 * at render time, which gen-weeb.mjs writes from the Notion export and gen-kotlin-data.mjs
 * re-emits into Kotlin, because a number typed into Kotlin by hand is a second copy no generator
 * refreshes. The first draft of this docstring named a stale-row count that was already off by
 * one, which is the drift this rule exists to prevent, performed in a comment.
 *
 * Dropped from the web version and deliberately not stubbed: `ReactionRow`, the per-row playhtml
 * reaction bar, a third-party multiplayer channel with no offline meaning; and `DeferredPlayRoom`,
 * a bundle-splitting wrapper with no analogue in a single compiled binary.
 */

// ---------------------------------------------------------------------------------------------
// Derived once, at module scope, the direct analogue of WeebRoom.tsx's top-of-file consts. Every
// one of these is a derivation and never an assertion: if a future export adds a "Dropped" status
// or spends a 1, the copy below changes with it instead of going quietly wrong.
// ---------------------------------------------------------------------------------------------

private val statuses = weeb.anime.byWatch.entries.sortedByDescending { it.value }
private val hasDropped = statuses.any { it.key.contains("drop", ignoreCase = true) }
private val maxStatus = statuses.maxOfOrNull { it.value } ?: 1
private val scores = weeb.anime.scoreDist.entries.sortedBy { it.key }
private val lowestUsed = scores.firstOrNull()?.key ?: 0
private val topShare =
    scores.lastOrNull()?.let { it.value.toDouble() / weeb.anime.scored * 100 } ?: 0.0
private val biggestGap = weeb.anime.deepestGaps.firstOrNull()

/** How many stale rows are on screen before the reader has to ask for the rest. */
private const val staleVisible = 8

/** The full width of the score scale. Not `scores.size`: the point is which end is missing. */
private const val scoreScale = 5

/** `1924` to `"1,924"`. Kotlin common has no `toLocaleString`, and this corpus has no negatives. */
private fun num(n: Int): String = n.toString().reversed().chunked(3).joinToString(",").reversed()

/** `toFixed(1)`, which common Kotlin also lacks. `97.55` becomes `"97.6%"`. */
private fun pct(x: Double): String {
    val tenths = (x * 10).roundToInt()
    return "${tenths / 10}.${tenths % 10}%"
}

@Composable
fun WeebScreen(modifier: Modifier = Modifier) {
    val anime = weeb.anime
    val manga = weeb.manga
    val uri = LocalUriHandler.current
    var showAllStale by remember { mutableStateOf(false) }
    val shown = if (showAllStale) weeb.stale else weeb.stale.take(staleVisible)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        // Bottom room for the floating chat launcher, same as the homepage's list padding.
        contentPadding = PaddingValues(top = 32.dp, bottom = 120.dp),
    ) {
        item {
            Reveal {
                Column(Modifier.pageMeasure()) {
                    SectionEyebrow("// weeb central")
                    Spacer(Modifier.height(12.dp))
                    SectionHeading("A hand-kept list, read as evidence")
                    Spacer(Modifier.height(14.dp))
                    BasicText(
                        text = "${num(anime.total)} anime and ${num(manga.total)} manga, kept by " +
                            "hand in Notion for years before anyone asked to see them. The " +
                            "interesting part isn't the titles. It's that the table admits three " +
                            "things its rows never say out loud.",
                        modifier = Modifier.widthIn(max = ProseMeasure),
                        style = cvType.body,
                    )
                }
            }
        }

        // ----------------------------------------------------------------------------------- 1
        item {
            Finding(
                eyebrow = "// finding 01",
                title = if (hasDropped) "Quitting is recorded" else "There is no word for quitting",
            ) {
                BasicText(
                    text = buildString {
                        if (hasDropped) {
                            append("A \"dropped\" status exists in this export, so the schema does ")
                            append("let him admit it.")
                        } else {
                            append("The status column has ${statuses.size} values and not one of ")
                            append("them is \"dropped\". ${num(anime.byWatch["Paused"] ?: 0)} ")
                            append("titles sit in \"Paused\" instead.")
                        }
                        append(" Paused is supposed to mean later. Set against how many are ")
                        append("actually caught up, it mostly means no.")
                    },
                    modifier = Modifier.widthIn(max = ProseMeasure),
                    style = cvType.bodySmall,
                )

                Spacer(Modifier.height(20.dp))
                StatusChart()

                Spacer(Modifier.height(28.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CaughtUpCard("Completed", anime.caughtUp.completed)
                    CaughtUpCard("Paused", anime.caughtUp.paused)
                }

                Spacer(Modifier.height(22.dp))
                BasicText(
                    text = buildString {
                        append("${num(anime.unwatchedSeasons)} seasons sit unwatched across ")
                        append("${num(anime.behindCount)} shows.")
                        if (biggestGap != null) {
                            append(" The deepest single hole is ${biggestGap.name}, ")
                            append("${biggestGap.gap} seasons behind.")
                        }
                    },
                    modifier = Modifier.widthIn(max = ProseMeasure),
                    style = cvType.bodySmall,
                )
            }
        }

        // ----------------------------------------------------------------------------------- 2
        item {
            Finding(
                eyebrow = "// finding 02",
                title = "The bottom of the scale has never been used",
            ) {
                BasicText(
                    text = "${num(anime.scored)} of ${num(anime.total)} titles carry a score, and " +
                        "every one of them is a $lowestUsed or higher out of $scoreScale. The " +
                        "lower ${lowestUsed - 1} points of his own scale have never once been " +
                        "spent. The shows that would have earned them are the ones sitting in " +
                        "\"Paused\", unscored.",
                    modifier = Modifier.widthIn(max = ProseMeasure),
                    style = cvType.bodySmall,
                )

                Spacer(Modifier.height(20.dp))
                ScoreLadder()

                Spacer(Modifier.height(14.dp))
                MonoMeta("${topShare.roundToInt()}% OF EVERYTHING HE SCORED GOT FULL MARKS")

                if (weeb.divergence.n > 0) {
                    Spacer(Modifier.height(28.dp))
                    Divergence()
                }
            }
        }

        // ----------------------------------------------------------------------------------- 3
        item {
            Finding(
                eyebrow = "// finding 03",
                title = "A hand-kept list cannot see the present",
            ) {
                BasicText(
                    text = "Every title here was matched against AniList when the corpus was " +
                        "generated. ${num(weeb.stale.size)} rows say \"caught up\" while a sequel " +
                        "has already aired. That gap is not carelessness. It is what a snapshot " +
                        "does the moment it is written, and the only fix is to ask something " +
                        "outside the list.",
                    modifier = Modifier.widthIn(max = ProseMeasure),
                    style = cvType.bodySmall,
                )
                Spacer(Modifier.height(20.dp))
            }
        }

        // The one enumerated corpus on the page, and the reason this is a LazyColumn rather than a
        // scrolling Column: the rows the finding is ABOUT are the tail, so the tail has to be
        // reachable, and laying the whole list out eagerly on a wasm canvas is a visible hitch.
        items(shown, key = { it.name }) { StaleRow(it) }

        item {
            Column(Modifier.pageMeasure().padding(top = 18.dp)) {
                if (weeb.stale.size > staleVisible) {
                    GhostButton(
                        text = if (showAllStale) {
                            "Show the first $staleVisible"
                        } else {
                            "Show all ${weeb.stale.size}"
                        },
                        onClick = { showAllStale = !showAllStale },
                    )
                    Spacer(Modifier.height(18.dp))
                }
                MonoMeta(
                    "${anime.matched} OF ${anime.total} TITLES MATCHED A PUBLIC RECORD · " +
                        "CORPUS LAST READ ${weeb.generatedAt}",
                )
            }
        }

        // Manga is a much smaller corpus. One honest paragraph, not a fake third act.
        item {
            Reveal {
                Column(Modifier.pageMeasure().padding(top = CvSectionGap)) {
                    Rule()
                    Spacer(Modifier.height(24.dp))
                    MonoMeta("THE MANGA HALF")
                    Spacer(Modifier.height(12.dp))
                    BasicText(
                        text = "${num(manga.total)} titles and ${num(manga.chaptersRead)} chapters " +
                            "logged. Too small a corpus to carry a finding, and saying so is " +
                            "better than dressing it up. ${manga.byRead["Reading"] ?: 0} of them " +
                            "are still open.",
                        modifier = Modifier.widthIn(max = ProseMeasure),
                        style = cvType.bodySmall,
                    )
                    Spacer(Modifier.height(20.dp))
                    GhostButton(
                        text = "Enrichment source: AniList",
                        onClick = { uri.openUri("https://anilist.co") },
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Shell
// ---------------------------------------------------------------------------------------------

/** `mx-auto max-w-4xl px-6`, the measure every section on this page shares. */
private fun Modifier.pageMeasure(): Modifier =
    this.widthIn(max = CvContentMaxWidth).fillMaxWidth().padding(horizontal = CvGutter)

/** `max-w-2xl`. Prose stays narrower than the page so a paragraph never runs the full measure. */
private val ProseMeasure = 680.dp

@Composable
private fun Finding(eyebrow: String, title: String, content: @Composable () -> Unit) {
    Reveal {
        Column(Modifier.pageMeasure().padding(top = CvSectionGap)) {
            SectionEyebrow(eyebrow)
            Spacer(Modifier.height(10.dp))
            SectionHeading(title)
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
private fun Rule() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(cvColors.line))
}

// ---------------------------------------------------------------------------------------------
// 1 - the status taxonomy
// ---------------------------------------------------------------------------------------------

/**
 * The status histogram.
 *
 * One departure from the web: the count sits in a fixed right-hand column rather than trailing the
 * bar. On the web the bar is a percentage of its flex line and the count rides along behind it.
 * Pinning the count instead makes the five values directly comparable, which is what the finding is
 * asking the reader to do.
 */
@Composable
private fun StatusChart() {
    val colors = cvColors
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        statuses.forEach { (label, n) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BasicText(text = label, modifier = Modifier.width(92.dp), style = cvType.metaMono)
                Box(
                    Modifier
                        .weight(1f)
                        // The bar carries no text, so the a11y tree gets the pair spelled out.
                        .semantics { contentDescription = "$label, $n titles" },
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(n.toFloat() / maxStatus)
                            .height(8.dp)
                            .background(colors.accent.copy(alpha = 0.7f), RoundedCornerShape(4.dp)),
                    )
                }
                Spacer(Modifier.width(12.dp))
                BasicText(text = num(n), modifier = Modifier.width(44.dp), style = cvType.metaMono)
            }
        }
    }
}

@Composable
private fun CaughtUpCard(label: String, bucket: WeebBucket) {
    CvCard(modifier = Modifier.widthIn(min = 240.dp, max = 340.dp)) {
        MonoMeta(label.uppercase())
        Spacer(Modifier.height(10.dp))
        BasicText(text = pct(bucket.pct), style = cvType.metric)
        Spacer(Modifier.height(8.dp))
        BasicText(
            text = "of the ${bucket.n} \"$label\" shows are actually caught up with every season out.",
            style = cvType.bodySmall,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// 2 - the unused half of the scale
// ---------------------------------------------------------------------------------------------

@Composable
private fun ScoreLadder() {
    val colors = cvColors
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        for (s in 1..scoreScale) {
            val row = scores.firstOrNull { it.key == s }
            val lit = row != null
            Column(
                modifier = Modifier
                    .widthIn(min = 96.dp)
                    .background(colors.card, RoundedCornerShape(10.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                StarRow(count = s, lit = lit)
                Spacer(Modifier.height(8.dp))
                BasicText(
                    // "never" rather than the web's placeholder glyph. The unused rungs are the
                    // finding, so the word for them should be a word.
                    text = row?.let { num(it.value) } ?: "never",
                    style = cvType.cardTitle.copy(
                        color = if (lit) colors.onBackground else colors.muted,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
        }
    }
}

private const val starInnerRatio = 0.45f

/**
 * ponytail: the stars are drawn, not typed. Neither vendored face carries U+2605, and an
 * unvendored glyph is tofu on the wasm canvas, the same reason ExpanderSection draws its chevron.
 */
@Composable
private fun StarRow(count: Int, lit: Boolean) {
    val colors = cvColors
    val tint = if (lit) colors.accent else colors.muted.copy(alpha = 0.45f)
    Row(
        modifier = Modifier.semantics { contentDescription = "$count out of $scoreScale" },
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        repeat(count) {
            Canvas(Modifier.size(11.dp)) { drawPath(starPath(size), tint) }
        }
    }
}

private fun starPath(size: Size): Path {
    val path = Path()
    val cx = size.width / 2f
    val cy = size.height / 2f
    val outer = min(cx, cy)
    val inner = outer * starInnerRatio
    // Ten vertices, alternating outer and inner, starting at twelve o'clock.
    repeat(10) { i ->
        val radius = if (i % 2 == 0) outer else inner
        val angle = (-PI / 2 + i * PI / scoreScale).toFloat()
        val x = cx + radius * cos(angle)
        val y = cy + radius * sin(angle)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

@Composable
private fun Divergence() {
    val colors = cvColors
    Column(Modifier.fillMaxWidth()) {
        MonoMeta("AGAINST THE CROWD · ${weeb.divergence.n} TITLES WHERE BOTH SCORES EXIST")
        Spacer(Modifier.height(12.dp))
        weeb.divergence.top.take(3).forEach { d ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(text = d.name, modifier = Modifier.weight(1f), style = cvType.bodySmall)
                Spacer(Modifier.width(16.dp))
                // His 1-5 rescaled onto AniList's 0-100, so the two numbers are comparable.
                BasicText(text = "${d.mine * 20} vs ${d.crowd} crowd", style = cvType.metaMono)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
        }
        Spacer(Modifier.height(12.dp))
        BasicText(
            text = "He is most generous exactly where the crowd is harshest.",
            style = cvType.bodySmall,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// 3 - the rows the list cannot see
// ---------------------------------------------------------------------------------------------

/**
 * One stale row.
 *
 * A [Column], not a two-column [Row]: both halves are generated strings whose length nobody here
 * controls, and the longest of them ("I Was Reincarnated as the 7th Prince ... Season 2") is wider
 * than a phone on its own. The web version learned this by scrolling 439px sideways.
 *
 * The title shown is AniList's canonical name, not the CSV spelling. His rows were typed over years
 * and mix English with romaji, so the raw list read as two naming conventions with no rule. The
 * romaji goes underneath where it says something different, rather than replacing anything.
 */
@Composable
private fun StaleRow(s: WeebStale) {
    val colors = cvColors
    Column(Modifier.pageMeasure()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            BasicText(text = s.title, style = cvType.bodySmall.copy(color = colors.onBackground))
            if (s.romaji.isNotBlank() && s.romaji != s.title) {
                Spacer(Modifier.height(4.dp))
                MonoMeta(s.romaji)
            }
            Spacer(Modifier.height(6.dp))
            MonoMeta(s.sequel + if (s.year != null) " · ${s.year}" else "")
        }
    }
}
