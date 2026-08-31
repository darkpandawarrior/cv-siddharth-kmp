package com.siddharth.cv.shared.anthology

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.siddharth.cv.shared.data.generated.CanonLaw
import com.siddharth.cv.shared.data.generated.CanonPoint
import com.siddharth.cv.shared.data.generated.SeasonCanon
import com.siddharth.cv.shared.data.generated.afterlivesNote
import com.siddharth.cv.shared.data.generated.anthology
import com.siddharth.cv.shared.data.generated.countLedger
import com.siddharth.cv.shared.data.generated.milgalaxalNote
import com.siddharth.cv.shared.data.generated.namedThirteen
import com.siddharth.cv.shared.data.generated.renderingDoctrine
import com.siddharth.cv.shared.data.generated.renderings
import com.siddharth.cv.shared.data.generated.rigConstraints
import com.siddharth.cv.shared.data.generated.rigConstraintsNote
import com.siddharth.cv.shared.data.generated.seasonCanon
import com.siddharth.cv.shared.data.generated.standardIntervals
import com.siddharth.cv.shared.data.generated.tether
import com.siddharth.cv.shared.data.generated.tetherDoctrine
import com.siddharth.cv.shared.theme.AnimatedCounter
import com.siddharth.cv.shared.theme.CircuitDivider
import com.siddharth.cv.shared.theme.CvCard
import com.siddharth.cv.shared.theme.CvContentMaxWidth
import com.siddharth.cv.shared.theme.CvGutter
import com.siddharth.cv.shared.theme.CvSectionGap
import com.siddharth.cv.shared.theme.CvTheme
import com.siddharth.cv.shared.theme.ExpanderSection
import com.siddharth.cv.shared.theme.GhostButton
import com.siddharth.cv.shared.theme.MonoMeta
import com.siddharth.cv.shared.theme.PrimaryButton
import com.siddharth.cv.shared.theme.Reveal
import com.siddharth.cv.shared.theme.SectionEyebrow
import com.siddharth.cv.shared.theme.SectionHeading
import com.siddharth.cv.shared.theme.cvColors
import com.siddharth.cv.shared.theme.cvType

/**
 * Port of cv-siddharth/src/routes/canon.tsx: the reference the fiction keeps about itself.
 *
 * THE SECTION ORDER IS AN ARGUMENT, NOT A LIST. The reader arrives knowing nothing, is handed the
 * one fact all forty-eight entries assume they already have, is then told the fact that reframes
 * every image on the site, and is only then offered the parts that spoil.
 *
 * DATA vs PRESENTATION. Every fact is in `data/generated/CvCanonData.kt`, generated from
 * canonLore.ts. This file holds layout only, and it never compares a season number to a literal:
 * the open/gated partition reads `spoils`, so a fourth season picks its own side of the line by
 * writing one field. See [CanonBody]; that is the one non-obvious block in the file and it is
 * load-bearing, because a law's own name can be the spoiler.
 *
 * LOST AGAINST THE WEB, all of it for the same reason, which is that this port carries no bitmaps:
 *  - The bestiary plate above The Count. It becomes the drawn fourteen-cell figure the anthology
 *    hub uses, which states the same census without pretending to be the painting.
 *  - The four rendering portraits. That section argues with plates rather than asserting, and here
 *    it can only assert; the captions were already carrying the argument in words, so they stay and
 *    the frames go, rather than an invented panel standing in for a drawing of a person.
 *  - Tveggi's scratch, the seam between sections. It is an SVG string the emitter left behind, so
 *    the site's own [CircuitDivider] is the seam instead.
 */
@Composable
fun CanonScreen(
    onOpenAnthology: (AnthologyLayer) -> Unit,
    modifier: Modifier = Modifier,
) {
    CvTheme(colors = InkColors) {
        CanonBody(onOpenAnthology, modifier)
    }
}

/** `max-w-5xl mx-auto px-6`. */
private fun Modifier.pageMeasure(): Modifier =
    this.widthIn(max = CvContentMaxWidth).fillMaxWidth().padding(horizontal = CvGutter)

@Composable
private fun CanonBody(onOpenAnthology: (AnthologyLayer) -> Unit, modifier: Modifier = Modifier) {
    val colors = cvColors
    val uri = LocalUriHandler.current

    // The whole of the season logic, and the whole of the spoiler design. A season with no doctrine
    // written yet drops out rather than rendering an empty shell; the rest sort themselves by what
    // they admit to giving away.
    val blocks = anthology.seasons.mapNotNull { s -> seasonCanon[s.n]?.let { s.n to it } }
    val open = blocks.filter { it.second.spoils == null }
    val gated = blocks.filter { it.second.spoils != null }

    // Laws from a season whose doctrine is gated stay inside that gated block. A season four that
    // adds a law AND declares what it spoils must not have that law leak into the open grid above
    // the divider, because a law's own name can be the spoiler.
    val openLaws = open.flatMap { it.second.laws }

    BoxWithConstraints(modifier.fillMaxSize().background(colors.ink)) {
        val twoUp = maxWidth >= 760.dp
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(top = 32.dp, bottom = 120.dp),
        ) {
            item("masthead") { Masthead(onOpenAnthology) }
            item("count") { TheCount(twoUp) }
            item("seam-1") { Seam() }
            lawsSection(openLaws, twoUp, uri)
            item("seam-2") { Seam() }
            renderingSection(twoUp, uri, onOpenAnthology)
            item("seam-3") { Seam() }
            item("constraints") { RigConstraintsSection() }
            item("tether") { TetherSection() }
            item("seam-4") { Seam() }
            item("intervals") { IntervalsSection() }

            // 7. Open season doctrine (spoils == null).
            open.forEach { (n, canon) ->
                item("season-$n") {
                    Reveal {
                        Column(Modifier.pageMeasure().padding(top = CvSectionGap)) {
                            CvCard(Modifier.fillMaxWidth(), glowOnHover = false) {
                                MonoMeta("SEASON $n")
                                Spacer(Modifier.height(6.dp))
                                BasicText(
                                    text = seasonTitle(n),
                                    style = cvType.cardTitle.copy(color = colors.onBackground),
                                )
                                SeasonBody(canon)
                            }
                        }
                    }
                }
            }

            if (gated.isNotEmpty()) {
                turnSection(gated)
            }

            item("exit") { Exit(uri) }
        }
    }
}

private fun seasonTitle(n: Int): String =
    anthology.seasons.firstOrNull { it.n == n }?.title ?: "Season $n"

/**
 * The seam between sections. On the web this is Tveggi's scratch from Entry #2250, the object that
 * made writing possible dividing the parts of a story, inlined as SVG. The emitter left `mark`
 * behind with every other SVG string, so the site's own circuit seam stands in its place.
 */
@Composable
private fun Seam() {
    Column(Modifier.pageMeasure().padding(vertical = CvSectionGap / 2)) {
        CircuitDivider()
    }
}

// ---------------------------------------------------------------------------------------------
// 0. Masthead
// ---------------------------------------------------------------------------------------------

@Composable
private fun Masthead(onOpenAnthology: (AnthologyLayer) -> Unit) {
    Reveal {
        Column(Modifier.pageMeasure()) {
            GhostButton(
                text = anthology.title,
                onClick = { onOpenAnthology(AnthologyLayer.Form) },
            )
            Spacer(Modifier.height(28.dp))
            SectionEyebrow("// the reference the fiction keeps about itself")
            Spacer(Modifier.height(14.dp))
            BasicText(text = "The Canon", style = cvType.hero)
            Spacer(Modifier.height(18.dp))
            BasicText(
                modifier = Modifier.widthIn(max = 680.dp),
                text =
                    "The rules ${anthology.title} holds itself to, the units it measures time in, and " +
                        "what the instrument that produced every picture on this site does to the " +
                        "people it renders.",
                style = cvType.body.copy(fontSize = cvType.body.fontSize * 1.1f),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 1. The Count
// ---------------------------------------------------------------------------------------------

/**
 * First, because without it the rest is trivia: this is the fact every one of the forty-eight
 * entries assumes the reader already has.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TheCount(twoUp: Boolean) {
    val colors = cvColors
    Reveal {
        Column(Modifier.pageMeasure().padding(top = CvSectionGap)) {
            SectionHeading("The Count")
            Spacer(Modifier.height(24.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalArrangement = Arrangement.spacedBy(32.dp),
            ) {
                Box(Modifier.widthIn(max = if (twoUp) 340.dp else CvContentMaxWidth)) {
                    TheFourteenFigure()
                }
                Column(Modifier.widthIn(min = 280.dp, max = 620.dp)) {
                    BasicText(
                        text =
                            "Every world he has ever surveyed independently reports the same census: " +
                                "fourteen gods, fourteen monsters. Ask anyone to list the fourteen " +
                                "monsters and you get thirteen names and a pause. The resolution comes " +
                                "from one storyteller's account: thirteen of the fourteen split into a " +
                                "god-face and a monster-face when observed from both sides at once, and " +
                                "the one that never split keeps its single name on the god list, out of " +
                                "gratitude, and holds an unnamed line on the monster list, because it " +
                                "only ever had the one face to give. Twenty-eight lines. " +
                                "Twenty-seven names.",
                        style = cvType.body.copy(color = colors.muted),
                    )

                    // The arithmetic as a ledger, not as prose. A reader who does the subtraction
                    // unaided gets 27 against a stated 28 and concludes the book is broken.
                    Spacer(Modifier.height(24.dp))
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .drawBehind {
                                val h = 1.dp.toPx()
                                drawRect(colors.line, size = Size(size.width, h))
                                drawRect(
                                    colors.line,
                                    topLeft = Offset(0f, size.height - h),
                                    size = Size(size.width, h),
                                )
                            }
                            .padding(vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        countLedger.forEach { row ->
                            Row(Modifier.fillMaxWidth()) {
                                BasicText(
                                    text = row.line,
                                    modifier = Modifier.weight(1f),
                                    style = cvType.mono.copy(color = colors.muted),
                                )
                                BasicText(
                                    text = row.value,
                                    style = cvType.mono.copy(color = colors.accent),
                                )
                            }
                        }
                    }

                    // The thirteen as real text, because the plate's names are baked pixels on the
                    // web and a raster cannot be the only channel. Here there is no raster at all,
                    // which makes this the only channel.
                    Spacer(Modifier.height(20.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        namedThirteen.forEach {
                            BasicText(text = it, style = cvType.metaMono)
                        }
                        // The fourteenth slot is a real element, not a gap. The blank is the
                        // subject. Colour is measured rather than eyeballed: --color-danger lands
                        // at 4.49:1 on the ink ground and fails AA for normal text by a hundredth,
                        // so it stays as the dashed border, where the 3:1 non-text floor applies.
                        Box(
                            Modifier
                                .border(
                                    width = 1.dp,
                                    color = InkDanger,
                                    shape = RoundedCornerShape(4.dp),
                                )
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            BasicText(
                                text = "no name · XIV",
                                style = cvType.metaMono.copy(color = colors.accent2),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 2. The Laws
// ---------------------------------------------------------------------------------------------

/**
 * Doorways, not glosses. Each card links to every entry whose text actually fires the law, which is
 * the thing a reference panel could never do.
 *
 * ONE DIRECTION ONLY. The canon cites its subjects and the subjects do not cite the institution
 * back: no entry anywhere carries "this demonstrates law five". That asymmetry is the argument.
 */
private fun LazyListScope.lawsSection(laws: List<CanonLaw>, twoUp: Boolean, uri: UriHandler) {
    item("laws-head") {
        Reveal {
            Column(Modifier.pageMeasure()) {
                SectionHeading("The ${if (laws.size == lawsAtFull) "Seven" else laws.size} Laws")
                Spacer(Modifier.height(14.dp))
                BasicText(
                    modifier = Modifier.widthIn(max = 680.dp),
                    text =
                        "No law here was pressed onto the record. Each was read out of it, at the " +
                            "entries named on the card.",
                    style = cvType.body.copy(color = cvColors.muted),
                )
            }
        }
    }

    val columns = if (twoUp) 2 else 1
    laws.chunked(columns).forEach { row ->
        item("law-${row.first().n}") {
            Row(
                modifier = Modifier.pageMeasure().padding(top = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                row.forEachIndexed { i, law ->
                    Box(Modifier.weight(1f)) {
                        Reveal(delayMillis = i * 80) { LawCard(law, uri) }
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Seven is what the corpus holds today; the heading spells it only while that is true. */
private const val lawsAtFull = 7

private val Roman = listOf("", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X")

@Composable
private fun LawCard(law: CanonLaw, uri: UriHandler) {
    val colors = cvColors
    CvCard(Modifier.fillMaxWidth(), glowOnHover = false) {
        Box(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth(0.82f)) {
                BasicText(
                    text = law.name,
                    style = cvType.cardTitle.copy(color = colors.onBackground),
                )
                Spacer(Modifier.height(8.dp))
                BasicText(text = law.gloss, style = cvType.bodySmall)
                // bible.md is explicit that law six is one storyteller's position rather than
                // settled canon, and the seams in this anthology belong at the level of who is
                // telling you.
                val contested = law.contested
                if (contested != null) {
                    Spacer(Modifier.height(12.dp))
                    MonoMeta(contested)
                }
            }
            GhostNumeral(law.n, Modifier.align(Alignment.TopEnd))
        }

        // Every place the law fires, not just the first, and the list is however long the corpus
        // made it. No count, no "and two more", no ordering by anything but the order the entries
        // were written, because the variance between one card and the next is the only thing here
        // that says a human read the stories.
        Spacer(Modifier.height(16.dp))
        Column {
            (listOf(law.seenAt) + law.alsoAt).forEach { at ->
                RefLink(at.label) { openRead(at.slug, uri) }
            }
        }
    }
}

/**
 * The ghost ordinal behind a law card, drawn rather than typeset, and that is the same reasoning
 * the web arrives at from the other side. There, a giant near-ink word set as DOM text is flagged
 * serious colour-contrast by axe "and it deserves to be, because an automated check can't tell
 * decorative type from content", so it ships as an SVG graphic. Here a [Canvas] emits no semantics
 * node at all, so the numeral is decorative by construction and never reaches the a11y tree.
 */
@Composable
private fun GhostNumeral(n: Int, modifier: Modifier = Modifier) {
    val colors = cvColors
    val measurer = rememberTextMeasurer(cacheSize = 8)
    val label = Roman.getOrNull(n) ?: n.toString()
    // `cvType.ghostNumeral` is the site's own watermark token: W900, fixed at 36sp. The hero
    // style is fluid to 60sp and would paint outside this box at a wide viewport.
    val style = cvType.ghostNumeral.copy(color = colors.accent.copy(alpha = 0.13f))
    Canvas(modifier.width(GhostNumeralBox).height(GhostNumeralBox)) {
        val layout = measurer.measure(label, style)
        drawText(
            textLayoutResult = layout,
            topLeft =
                Offset(
                    size.width - layout.size.width,
                    0f,
                ),
        )
    }
}

private val GhostNumeralBox: Dp = 72.dp

/** An accent link in a body of prose. Underlined, because colour is never the only channel. */
@Composable
private fun RefLink(label: String, onClick: () -> Unit) {
    val colors = cvColors
    BasicText(
        text = label,
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick).padding(vertical = 10.dp),
        style =
            cvType.bodySmall.copy(
                color = colors.accent,
                textDecoration = TextDecoration.Underline,
            ),
    )
}

// ---------------------------------------------------------------------------------------------
// 3. The Rendering
// ---------------------------------------------------------------------------------------------

/**
 * The strongest material in the project, and it reframes every image on the site, so it gets the
 * most room.
 *
 * On the web it argues with plates: four portraits at ~460px each, two across, captions outside the
 * frame. This port has no bitmaps, so the four states of the rig are argued in words alone. That is
 * a real loss and it is named rather than papered over with a generated panel. A drawn stand-in
 * for a portrait would be the instrument inventing a body, which is the one thing this doctrine
 * says it does not get to do.
 */
private fun LazyListScope.renderingSection(
    twoUp: Boolean,
    uri: UriHandler,
    onOpenAnthology: (AnthologyLayer) -> Unit,
) {
    item("rendering-head") {
        Reveal {
            Column(Modifier.pageMeasure()) {
                SectionEyebrow("// series-level doctrine")
                Spacer(Modifier.height(14.dp))
                SectionHeading("The Rendering")
                Spacer(Modifier.height(24.dp))
                BasicText(
                    modifier = Modifier.widthIn(max = 760.dp),
                    text = renderingDoctrine.claim,
                    style = cvType.h2,
                )
                renderingDoctrine.mechanism.forEach { para ->
                    Spacer(Modifier.height(20.dp))
                    BasicText(
                        modifier = Modifier.widthIn(max = 760.dp),
                        text = para,
                        style = cvType.body.copy(color = cvColors.muted),
                    )
                }
                Spacer(Modifier.height(24.dp))
                PullQuote(renderingDoctrine.pull, accentText = true)
            }
        }
    }

    val columns = if (twoUp) 2 else 1
    renderings.chunked(columns).forEach { row ->
        item("rendering-${row.first().state}") {
            Row(
                modifier = Modifier.pageMeasure().padding(top = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                row.forEachIndexed { i, r ->
                    Box(Modifier.weight(1f)) {
                        Reveal(delayMillis = i * 80) {
                            val witness = anthology.witnesses.firstOrNull { it.id == r.witnessId }
                            CvCard(Modifier.fillMaxWidth(), glowOnHover = false) {
                                SectionEyebrow("the rig ${r.state}")
                                Spacer(Modifier.height(12.dp))
                                BasicText(
                                    text = witness?.name ?: r.witnessId,
                                    style = cvType.cardTitle.copy(color = cvColors.onBackground),
                                )
                                Spacer(Modifier.height(10.dp))
                                BasicText(text = r.note, style = cvType.bodySmall)
                                Spacer(Modifier.height(14.dp))
                                RefLink("read the entry") { openRead(r.slug, uri) }
                            }
                        }
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }

    item("rendering-consequences") {
        Reveal {
            Column(Modifier.pageMeasure().padding(top = 40.dp)) {
                Consequences(renderingDoctrine.consequences, twoUp)
                Spacer(Modifier.height(32.dp))
                // The next sentence in the bible is "Season Three is built on that, and it is why
                // burning the case is not an escape". That is the ending, so it lives below the
                // line inside season three's gated block. This is the cut.
                BasicText(
                    text = "All ten renderings survive.",
                    style = cvType.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                // The roll, not the hub: the anthology's layers are addressable, so this lands on
                // the tellers with the switch already set instead of dropping the reader on season
                // one to go looking. The layer is the enum the switch row itself reads, so a typo
                // here is a compile error rather than a link that silently opens the wrong panel.
                RefLink("The tellers are on the roll") { onOpenAnthology(AnthologyLayer.Tellers) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Consequences(points: List<CanonPoint>, twoUp: Boolean) {
    val colors = cvColors
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        points.forEach { c ->
            Column(
                Modifier
                    .widthIn(min = if (twoUp) 260.dp else 200.dp, max = 320.dp)
                    .drawBehind { drawRect(colors.line, size = Size(size.width, 1.dp.toPx())) }
                    .padding(top = 16.dp),
            ) {
                BasicText(
                    text = c.term,
                    style = cvType.body.copy(
                        color = colors.onBackground,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Spacer(Modifier.height(8.dp))
                BasicText(text = c.gloss, style = cvType.bodySmall)
            }
        }
    }
}

/** `border-l-2 pl-5`: the display pull quote, in accent or in the body colour. */
@Composable
private fun PullQuote(text: String, accentText: Boolean) {
    val colors = cvColors
    Column(
        Modifier
            .widthIn(max = 760.dp)
            .drawBehind { drawRect(colors.accent, size = Size(2.dp.toPx(), size.height)) }
            .padding(start = 20.dp),
    ) {
        BasicText(
            text = text,
            style =
                cvType.cardTitle.copy(
                    color = if (accentText) colors.accent else colors.onBackground,
                ),
        )
    }
}

// ---------------------------------------------------------------------------------------------
// 4. What the rig could not render away
// ---------------------------------------------------------------------------------------------

/** The counterweight. Without it the section above reads as licence to draw anything at all. */
@Composable
private fun RigConstraintsSection() {
    Reveal {
        Column(Modifier.pageMeasure()) {
            SectionHeading("What the rig could not render away")
            Spacer(Modifier.height(24.dp))
            ScrollingTable(
                headers = listOf("Species" to 140.dp, "World" to 160.dp, "The constraint" to 360.dp),
            ) {
                rigConstraints.forEach { row ->
                    TableRow(
                        cells =
                            listOf(
                                TableCell(row.species, 140.dp, strong = true),
                                TableCell(row.world, 160.dp),
                                TableCell(row.constraint, 360.dp),
                            ),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            BasicText(
                modifier = Modifier.widthIn(max = 680.dp),
                text = rigConstraintsNote,
                style = cvType.bodySmall,
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 5. The tether
// ---------------------------------------------------------------------------------------------

/**
 * Three real figures, the big one first. No sparkline: the site's metric spark draws a fixed
 * ascending line, which would be a trend for a thing that has no trend.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TetherSection() {
    Reveal {
        Column(Modifier.pageMeasure().padding(top = CvSectionGap)) {
            SectionHeading("The tether")
            Spacer(Modifier.height(24.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                tether.forEach { t ->
                    Column(Modifier.widthIn(min = 200.dp, max = 300.dp)) {
                        AnimatedCounter(target = t.value)
                        Spacer(Modifier.height(8.dp))
                        MonoMeta(t.label)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            BasicText(
                modifier = Modifier.widthIn(max = 760.dp),
                text = tetherDoctrine,
                style = cvType.body.copy(color = cvColors.muted),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 6. Standard Intervals
// ---------------------------------------------------------------------------------------------

@Composable
private fun IntervalsSection() {
    val colors = cvColors
    Reveal {
        Column(Modifier.pageMeasure()) {
            SectionHeading("Standard Intervals")
            Spacer(Modifier.height(14.dp))
            BasicText(
                modifier = Modifier.widthIn(max = 680.dp),
                text = "Section 3 of the Founding Charter. Eight named at founding, six in use.",
                style = cvType.body.copy(color = colors.muted),
            )
            Spacer(Modifier.height(24.dp))
            ScrollingTable(
                headers = listOf("Interval" to 140.dp, "Realm" to 140.dp, "Length" to 260.dp),
            ) {
                standardIntervals.forEach { row ->
                    TableRow(
                        cells =
                            listOf(
                                TableCell(row.interval, 140.dp, strong = true),
                                TableCell(row.realm, 140.dp),
                                // The two founding blanks read as blanks rather than as data. The
                                // italic is what carries the difference, so the distinction never
                                // depends on colour alone.
                                TableCell(row.length, 260.dp, italic = row.blank),
                            ),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            BasicText(
                modifier = Modifier.widthIn(max = 680.dp),
                text = milgalaxalNote,
                style = cvType.bodySmall,
            )
            // The joke buried in an appendix, and the site has never printed it anywhere else. Safe
            // above the line: it gives away what the units are named after, not what happens.
            Spacer(Modifier.height(24.dp))
            PullQuote(afterlivesNote, accentText = false)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// The turn
// ---------------------------------------------------------------------------------------------

/**
 * THE DISCLOSURE DESIGN, and why it is this and not something else.
 *
 * A global spoiler toggle controls content four screens away from the switch: a reader flips it at
 * the top, forgets by the bottom, and is ambushed anyway. It also turns the unspoiled page into a
 * page of holes, which is worse than a page with a door in it. A hole says "there is something here
 * you are not allowed". A closed door says "there is something here, and here is what it costs".
 *
 * So: one unmistakable divider, everything above it open, and every season that declares what it
 * gives away sitting below it behind an [ExpanderSection] that is shut on first paint, with the
 * damage named on the outside of it.
 *
 * The summary sits above the expander rather than inside its title, because ExpanderSection takes a
 * title String and the warning has to be readable while the block is shut. A `summary` slot on that
 * primitive would collapse these four calls into one; it does not have one, and forking it for this
 * page would be worse than the extra MonoMeta.
 */
private fun LazyListScope.turnSection(gated: List<Pair<Int, SeasonCanon>>) {
    item("turn") {
        Column(
            modifier = Modifier.pageMeasure().padding(top = CvSectionGap),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircuitDivider()
            Spacer(Modifier.height(32.dp))
            BasicText(text = "THE TURN", style = cvType.hero.copy(color = cvColors.accent))
            Spacer(Modifier.height(32.dp))
            CircuitDivider()
            Spacer(Modifier.height(28.dp))
            SectionEyebrow(
                "Below this line the seasons explain themselves, and each one says what it gives " +
                    "away before it does.",
            )
        }
    }

    gated.forEach { (n, canon) ->
        item("gated-$n") {
            Reveal {
                Column(Modifier.pageMeasure().padding(top = 24.dp)) {
                    CvCard(Modifier.fillMaxWidth(), glowOnHover = false) {
                        MonoMeta("SEASON $n")
                        Spacer(Modifier.height(6.dp))
                        BasicText(
                            text = seasonTitle(n),
                            style = cvType.cardTitle.copy(color = cvColors.onBackground),
                        )
                        Spacer(Modifier.height(6.dp))
                        MonoMeta("THIS GIVES AWAY ${canon.spoils.orEmpty()}")
                        ExpanderSection(
                            title = "Open season $n's doctrine",
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            SeasonBody(canon)
                        }
                    }
                }
            }
        }
    }
}

/** One season's doctrine body, shared by the open blocks and the gated ones. */
@Composable
private fun SeasonBody(canon: SeasonCanon) {
    val colors = cvColors
    Spacer(Modifier.height(14.dp))
    BasicText(
        modifier = Modifier.widthIn(max = 760.dp),
        text = canon.thesis,
        style = cvType.body.copy(color = colors.onBackground),
    )
    canon.points.forEach { p ->
        Spacer(Modifier.height(16.dp))
        BasicText(
            text = p.term,
            style = cvType.mono.copy(color = colors.accent, fontWeight = FontWeight.SemiBold),
        )
        Spacer(Modifier.height(4.dp))
        BasicText(
            modifier = Modifier.widthIn(max = 760.dp),
            text = p.gloss,
            style = cvType.bodySmall,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// The exit
// ---------------------------------------------------------------------------------------------

/**
 * The page's exit is also its point: the canon only means anything inside a story.
 *
 * There is no Sources section here and no "outside the fiction" note, and both are barred from this
 * page rather than merely missing. The craft record is real and worth keeping; it is not worth
 * keeping here, on a surface a reader falls into halfway through the lore.
 */
@Composable
private fun Exit(uri: UriHandler) {
    Column(
        modifier = Modifier.pageMeasure().padding(top = CvSectionGap, bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PrimaryButton(
            text = "Read the first entry",
            onClick = { openRead("legend-of-koaeluae-scales", uri) },
        )
        Spacer(Modifier.height(14.dp))
        MonoMeta("Journal Entry #2245. Exxobar. Snow.")
    }
}

// ---------------------------------------------------------------------------------------------
// Tables
// ---------------------------------------------------------------------------------------------

private data class TableCell(
    val text: String,
    val width: Dp,
    val strong: Boolean = false,
    val italic: Boolean = false,
)

/**
 * A table that scrolls sideways inside its own container rather than pushing the page wide, the
 * same containment `e2e/overflow.spec.ts` asserts at 390px on the web, arrived at here by giving
 * every column a stated width and putting a `horizontalScroll` around the lot.
 */
@Composable
private fun ScrollingTable(headers: List<Pair<String, Dp>>, rows: @Composable () -> Unit) {
    val colors = cvColors
    Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
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
            headers.forEach { (label, width) ->
                BasicText(
                    text = label.uppercase(),
                    modifier = Modifier.width(width).padding(end = 16.dp),
                    style = cvType.metaMono.copy(fontWeight = FontWeight.SemiBold),
                )
            }
        }
        rows()
    }
}

@Composable
private fun TableRow(cells: List<TableCell>) {
    val colors = cvColors
    Row(
        Modifier
            .drawBehind {
                drawRect(
                    colors.line.copy(alpha = 0.5f),
                    topLeft = Offset(0f, size.height - 1.dp.toPx()),
                    size = Size(size.width, 1.dp.toPx()),
                )
            }
            .padding(vertical = 10.dp),
    ) {
        cells.forEach { c ->
            BasicText(
                text = c.text,
                modifier = Modifier.width(c.width).padding(end = 16.dp),
                style =
                    cvType.mono.copy(
                        color = if (c.strong) colors.onBackground else colors.muted,
                        fontStyle = if (c.italic) FontStyle.Italic else FontStyle.Normal,
                    ),
            )
        }
    }
}
