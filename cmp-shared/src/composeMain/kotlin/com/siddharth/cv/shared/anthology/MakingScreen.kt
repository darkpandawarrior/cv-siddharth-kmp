package com.siddharth.cv.shared.anthology

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.siddharth.cv.shared.LocalNav
import com.siddharth.cv.shared.data.generated.KillRecord
import com.siddharth.cv.shared.data.generated.Rendering
import com.siddharth.cv.shared.data.generated.anthology
import com.siddharth.cv.shared.data.generated.auditMethod
import com.siddharth.cv.shared.data.generated.pipelineStages
import com.siddharth.cv.shared.data.generated.portraitIterations
import com.siddharth.cv.shared.data.generated.receipts
import com.siddharth.cv.shared.data.generated.renderings
import com.siddharth.cv.shared.data.generated.retroactionStandard
import com.siddharth.cv.shared.data.generated.s2AuditKills
import com.siddharth.cv.shared.data.generated.s2MissingBeat
import com.siddharth.cv.shared.data.generated.s2NegativeControl
import com.siddharth.cv.shared.data.generated.s3FirstDesign
import com.siddharth.cv.shared.data.generated.s4Fence
import com.siddharth.cv.shared.data.generated.seasonCanon
import com.siddharth.cv.shared.data.generated.spend
import com.siddharth.cv.shared.data.generated.voiceConstraints
import com.siddharth.cv.shared.media.ProjectShot
import com.siddharth.cv.shared.theme.CircuitDivider
import com.siddharth.cv.shared.theme.CvCard
import com.siddharth.cv.shared.theme.CvContentMaxWidth
import com.siddharth.cv.shared.theme.CvGutter
import com.siddharth.cv.shared.theme.CvSectionGap
import com.siddharth.cv.shared.theme.ExpanderSection
import com.siddharth.cv.shared.theme.GhostButton
import com.siddharth.cv.shared.theme.MonoMeta
import com.siddharth.cv.shared.theme.PrimaryButton
import com.siddharth.cv.shared.theme.Reveal
import com.siddharth.cv.shared.theme.SectionEyebrow
import com.siddharth.cv.shared.theme.SectionHeading
import com.siddharth.cv.shared.theme.cvColor
import com.siddharth.cv.shared.theme.cvColors
import com.siddharth.cv.shared.theme.cvType
import kotlin.math.roundToInt

/**
 * Port of cv-siddharth/src/routes/making.tsx. "The Making" is the craft record for The Morkinstar
 * Journals. It is the one surface on the site allowed to have an author: the audits, what they
 * killed, the two portrait passes, the voice rules and the measured spend.
 *
 * Every fact comes from `data/generated/CvMakingData.kt`, `CvCanonData.kt` and `CvAnthologyData.kt`,
 * all three emitted from the React modules by `gen-kotlin-data.mjs`. Nothing here is transcribed,
 * so a fact that changes upstream changes here on the next `npm run gen:kotlin` and not before.
 *
 * THE SPOILER GATE is the same mechanism the web route uses and it is reused rather than rebuilt:
 * the two seasons that already declare a `spoils` string in [seasonCanon] print theirs, Season Four
 * states its own because the route does. [ExpanderSection] is closed on first paint, which is the
 * whole contract. The one thing it cannot do is carry a three-line summary, so the price is printed
 * in the card *above* the expander header rather than inside it: it is still named before it is
 * paid, and it stays put when the body opens.
 *
 * THE PORTRAITS ARE THE REAL ONES. This page's whole subject is two passes of artwork and what was
 * wrong with the first, so the redrawn set is streamed from the live origin by [plateUrl] rather
 * than stood in for. A generated panel here would have been the page arguing about pictures with no
 * picture in it.
 *
 * DEGRADED, deliberately:
 *   - The web page's `<GiantCTA>` becomes a [PrimaryButton]. Same destination, no slab.
 *   - There is no footer and no floating chat on this screen; both are App-level furniture.
 */
@Composable
fun MakingScreen(onReadAnthology: () -> Unit, modifier: Modifier = Modifier) {
    val colors = cvColors
    val nav = LocalNav.current
    val uri = LocalUriHandler.current

    LazyColumn(
        modifier = modifier.fillMaxSize().background(colors.ink),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item { Masthead { nav.goSection("top") } }
        item { AuditSection() }
        item { PortraitsSection() }
        item { VoiceSection() }
        item { PipelineSection() }
        item { RetroactionSection() }
        item { SpendSection() }
        item { TheTurn() }
        item { SeasonTwoGate() }
        item { SeasonThreeGate() }
        item { SeasonFourGate() }
        item { Receipts(uri) }
        item { ReadTheAnthology(onReadAnthology) }
    }
}

/** `max-w-5xl mx-auto px-6`: the measure every section on this page shares. */
private fun Modifier.pageMeasure(): Modifier =
    this.widthIn(max = CvContentMaxWidth).fillMaxWidth().padding(horizontal = CvGutter)

/** `SectionHeader` + `section-y mx-auto max-w-5xl px-6`, matching ProjectDetailScreen's own. */
@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Reveal {
        Column(Modifier.pageMeasure().padding(top = CvSectionGap)) {
            SectionHeading(title, Modifier.semantics { heading() })
            Spacer(Modifier.height(20.dp))
            content()
        }
    }
}

/** A paragraph at the page's reading measure. `max-w-2xl` on the web. */
@Composable
private fun Prose(text: String, dim: Boolean = true) {
    BasicText(
        text = text,
        modifier = Modifier.widthIn(max = ProseMeasure),
        style = if (dim) cvType.bodySmall else cvType.body,
    )
}

/** `border-l-2 pl-5`: the accent-ruled pull quote, used three times on this page. */
@Composable
private fun PullQuote(text: String, accentText: Boolean) {
    val colors = cvColors
    Row(Modifier.widthIn(max = ProseMeasure).height(IntrinsicSize.Min)) {
        Box(Modifier.width(2.dp).fillMaxHeight().background(colors.accent))
        Spacer(Modifier.width(20.dp))
        BasicText(
            text = text,
            style = cvType.cardTitle.copy(color = if (accentText) colors.accent else colors.onBackground),
        )
    }
}

// -------------------------------------------------------------------------------------------
// 0. Masthead
// -------------------------------------------------------------------------------------------

@Composable
private fun Masthead(onHome: () -> Unit) {
    val colors = cvColors
    Column(Modifier.pageMeasure().padding(top = 32.dp)) {
        GhostButton(text = "Portfolio", onClick = onHome)
        Spacer(Modifier.height(28.dp))
        SectionEyebrow("// the craft record, not the fiction")
        Spacer(Modifier.height(10.dp))
        BasicText(text = "The Making", style = cvType.hero, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(12.dp))
        // `.sheen`: the 3px accent rule under the title, same as the project pages.
        Box(
            Modifier
                .width(112.dp)
                .height(3.dp)
                .background(
                    Brush.horizontalGradient(listOf(colors.accent, colors.accent2)),
                    RoundedCornerShape(2.dp),
                ),
        )
        Spacer(Modifier.height(18.dp))
        Prose(
            "The Morkinstar Journals reads as a correspondent with no author. This page is where " +
                "the author is. Everything below used to be scattered across the fiction itself, a " +
                "line here, a link there, and it read as production apparatus left inside the lore. " +
                "It has been moved, not deleted: the audits, what they killed, the two portrait " +
                "passes, the voice rules and what the whole thing cost.",
            dim = false,
        )
    }
}

// -------------------------------------------------------------------------------------------
// 1. The ownership audit
// -------------------------------------------------------------------------------------------

@Composable
private fun AuditSection() {
    val colors = cvColors
    Section("The ownership audit") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Prose("${auditMethod.send} ${auditMethod.gate}")
            Prose(auditMethod.whyNotSelfAssessed)
            Spacer(Modifier.height(12.dp))
            PullQuote(auditMethod.summary, accentText = true)
            Spacer(Modifier.height(4.dp))
            BasicText(
                text =
                    "Negative control, so the gate is known to detect real borrowing rather than " +
                        "pattern match everything: $s2NegativeControl",
                modifier = Modifier.widthIn(max = ProseMeasure),
                style = cvType.bodySmall.copy(color = colors.muted),
            )
        }
    }
}

// -------------------------------------------------------------------------------------------
// 2. The portraits, twice
// -------------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PortraitsSection() {
    val colors = cvColors
    Section("The portraits, twice") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Prose("First set: ${portraitIterations.firstSet}")
            Prose(portraitIterations.firstFix)
            Spacer(Modifier.height(12.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                renderings.forEach { r -> RenderingPlate(r) }
            }
            Spacer(Modifier.height(12.dp))
            Prose(portraitIterations.secondDefect)
            Prose(portraitIterations.theFix)
            Spacer(Modifier.height(4.dp))
            CvCard(modifier = Modifier.widthIn(max = ProseMeasure), glowOnHover = false) {
                BasicText(text = portraitIterations.trap, style = cvType.mono)
            }
            BasicText(
                text = "Redrawn set: the one that shipped.",
                style = cvType.bodySmall.copy(color = colors.muted),
            )
        }
    }
}

/**
 * One `<figure>`: the plate, then the caption.
 *
 * The plate is the shipped redrawn portrait, streamed. Its `contentDescription` is the rendering's
 * own note, which is what the page is arguing with in the first place, so a reader who cannot see
 * the picture gets the argument rather than a filename. If the fetch fails,
 * [com.siddharth.cv.shared.media.ProjectShot] falls back to the generated gradient, which reads as
 * a loading panel and never as a claim to be a drawing of a person.
 */
@Composable
private fun RenderingPlate(rendering: Rendering) {
    val colors = cvColors
    val witness = anthology.witnesses.firstOrNull { it.id == rendering.witnessId } ?: return
    Column(Modifier.widthIn(min = 260.dp, max = 460.dp)) {
        ProjectShot(
            url = plateUrl(witness.art),
            label = "${witness.name}, rendered. The rig ${rendering.state}. ${rendering.note}",
            modifier = Modifier.fillMaxWidth().aspectRatio(portraitAspect).clip(PlateShape),
        )
        Spacer(Modifier.height(10.dp))
        SectionEyebrow("the rig ${rendering.state}")
        Spacer(Modifier.height(6.dp))
        BasicText(
            text = witness.name,
            style = cvType.bodySmall.copy(color = colors.onBackground, fontWeight = FontWeight.Bold),
        )
    }
}

// -------------------------------------------------------------------------------------------
// 3. The voice
// -------------------------------------------------------------------------------------------

@Composable
private fun VoiceSection() {
    val colors = cvColors
    Section("The voice, held to its own rule") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            voiceConstraints.forEach { rule ->
                Row(Modifier.widthIn(max = ProseMeasure).height(IntrinsicSize.Min)) {
                    Box(Modifier.width(2.dp).fillMaxHeight().background(colors.line))
                    Spacer(Modifier.width(16.dp))
                    BasicText(text = rule, style = cvType.bodySmall)
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------------
// 4. The pipeline
// -------------------------------------------------------------------------------------------

@Composable
private fun PipelineSection() {
    val colors = cvColors
    Section("The pipeline") {
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            pipelineStages.forEachIndexed { i, stage ->
                Row(Modifier.widthIn(max = ProseMeasure)) {
                    BasicText(
                        text = "${i + 1}",
                        style = cvType.cardTitle.copy(color = colors.accent),
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        BasicText(
                            text = stage.step,
                            style =
                                cvType.bodySmall.copy(
                                    color = colors.onBackground,
                                    fontWeight = FontWeight.Bold,
                                ),
                        )
                        Spacer(Modifier.height(4.dp))
                        BasicText(text = stage.detail, style = cvType.bodySmall)
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------------
// 5. The retroaction standard
// -------------------------------------------------------------------------------------------

@Composable
private fun RetroactionSection() {
    Section("What is allowed to count as new") { Prose(retroactionStandard) }
}

// -------------------------------------------------------------------------------------------
// 6. The spend
// -------------------------------------------------------------------------------------------

/**
 * `$4.68`. Written out rather than reached for: kotlin-common has no `String.format`, and this is
 * the one page on the site whose entire argument is that its dollar figures are exact. Rounds to
 * the nearest cent and never drops a trailing zero, which is what `toFixed(2)` gives the web page.
 */
internal fun money(usd: Double): String {
    val cents = (usd * 100.0).roundToInt()
    return "\$${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"
}

@Composable
private fun SpendSection() {
    Section("What it cost") {
        Column {
            SpendRow(
                listOf(
                    money(spend.totalUsd) to "total, measured",
                    money(spend.auditsUsd) to "on the cross lab audits",
                    money(spend.artUsd) to "on art",
                ),
            )
            Spacer(Modifier.height(24.dp))
            Prose(spend.note)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpendRow(figures: List<Pair<String, String>>) {
    val colors = cvColors
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        figures.forEach { (amount, label) ->
            Column(Modifier.widthIn(min = 180.dp, max = 300.dp)) {
                BasicText(text = amount, style = cvType.metric.copy(color = colors.accent))
                Spacer(Modifier.height(8.dp))
                MonoMeta(label)
            }
        }
    }
}

// -------------------------------------------------------------------------------------------
// THE TURN. Everything below is behind a gate that names its price first
// -------------------------------------------------------------------------------------------

@Composable
private fun TheTurn() {
    Column(Modifier.pageMeasure().padding(top = CvSectionGap)) {
        CircuitDivider()
        Spacer(Modifier.height(28.dp))
        SectionEyebrow("// the turn")
        Spacer(Modifier.height(12.dp))
        Prose(
            "Below this line: what the audits actually named, and the season that has not shipped " +
                "an entry yet.",
        )
    }
}

/**
 * One gated block. [price] is the `spoils` string, printed above the closed expander so the reader
 * knows the cost before the click; the body only exists once opened.
 */
@Composable
private fun Gate(
    eyebrow: String,
    title: String,
    price: String,
    body: @Composable () -> Unit,
) {
    Reveal {
        Column(Modifier.pageMeasure().padding(top = 24.dp)) {
            CvCard(modifier = Modifier.fillMaxWidth(), glowOnHover = false) {
                SectionEyebrow(eyebrow)
                Spacer(Modifier.height(10.dp))
                MonoMeta("this gives away $price")
                ExpanderSection(title = title) { body() }
            }
        }
    }
}

@Composable
private fun SeasonTwoGate() {
    Gate(
        eyebrow = "// season two, before the audit",
        title = "The Ninety-One Pages",
        // The route reads SEASON_CANON[2].spoils rather than restating it, and so does this. The
        // orEmpty() is the Kotlin-side cost of a nullable field: season one legitimately has none.
        price = seasonCanon[2]?.spoils.orEmpty(),
    ) {
        Column {
            s2AuditKills.forEach { KillRow(it) }
            Spacer(Modifier.height(20.dp))
            PullQuote(s2MissingBeat, accentText = false)
        }
    }
}

/**
 * A kill-table row.
 *
 * Degraded: the web version tints "killed" with `--color-danger`, a token this palette does not
 * carry, so the hex is vendored here the way LabScreen vendors its own waste red. Its own comment
 * on the React side is the reason the colour is safe to reproduce rather than drop: the word
 * carries the meaning and the colour only reinforces it.
 */
@Composable
private fun KillRow(kill: KillRecord) {
    val colors = cvColors
    Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            BasicText(
                text = kill.premise,
                modifier = Modifier.weight(1f),
                style =
                    cvType.bodySmall.copy(color = colors.onBackground, fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.width(12.dp))
            BasicText(
                text = kill.fate,
                style =
                    cvType.metaMono.copy(
                        color = if (kill.fate == "killed") MakingDanger else colors.muted,
                    ),
            )
        }
        Spacer(Modifier.height(4.dp))
        BasicText(text = kill.namedAs, style = cvType.bodySmall)
    }
}

@Composable
private fun SeasonThreeGate() {
    val colors = cvColors
    Gate(
        eyebrow = "// season three, the design that never shipped",
        title = "The Kindling, v1",
        price = seasonCanon[3]?.spoils.orEmpty(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Prose(s3FirstDesign.premise)
            s3FirstDesign.findings.forEach { finding ->
                Column(Modifier.widthIn(max = ProseMeasure)) {
                    BasicText(
                        text = finding.title,
                        style = cvType.mono.copy(color = colors.accent, fontWeight = FontWeight.SemiBold),
                    )
                    Spacer(Modifier.height(4.dp))
                    BasicText(text = finding.note, style = cvType.bodySmall)
                }
            }
            Prose(s3FirstDesign.replacement)
        }
    }
}

@Composable
private fun SeasonFourGate() {
    Gate(
        eyebrow = "// season four, no entries shipped yet",
        title = "The frame the audit fenced",
        // Authored, not borrowed: the route states this one itself rather than reading a `spoils`
        // string, and reading seasonCanon[4] instead would print a different promise than the web.
        price = "the premise of a season nobody has read",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Prose("Named as ${s4Fence.named}. ${s4Fence.finding}")
            PullQuote(s4Fence.quote, accentText = false)
        }
    }
}

// -------------------------------------------------------------------------------------------
// Receipts + the door out
// -------------------------------------------------------------------------------------------

@Composable
private fun Receipts(uri: UriHandler) {
    val colors = cvColors
    Column(Modifier.pageMeasure().padding(top = 40.dp)) {
        SectionEyebrow("// receipts")
        Spacer(Modifier.height(12.dp))
        receipts.forEach { receipt ->
            BasicText(
                text = receipt.label,
                modifier =
                    Modifier
                        .clickable(role = Role.Button) { uri.openUri(receipt.href) }
                        .semantics { contentDescription = "${receipt.label}, opens on GitHub" }
                        .padding(vertical = 6.dp),
                style =
                    cvType.bodySmall.copy(
                        color = colors.accent,
                        textDecoration = TextDecoration.Underline,
                    ),
            )
        }
    }
}

@Composable
private fun ReadTheAnthology(onReadAnthology: () -> Unit) {
    Column(
        modifier = Modifier.pageMeasure().padding(top = CvSectionGap, bottom = 96.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PrimaryButton(text = "Read the anthology", onClick = onReadAnthology)
        Spacer(Modifier.height(14.dp))
        MonoMeta("Everything above is standing on the other side of this door.")
    }
}

// -------------------------------------------------------------------------------------------

/** `max-w-2xl`: the reading measure every paragraph on this page is held to. */
private val ProseMeasure = 672.dp

/**
 * index.css's `--color-danger`, #ff5c5c. Vendored rather than themed because [com.siddharth.cv.shared.theme.CvColors]
 * has no danger token; LabScreen already vendors the same hex for the same reason.
 */
private val MakingDanger: Color = cvColor("#ff5c5c")

/**
 * The one cent value no real figure on this page exercises: a single-digit cent count, which is the
 * only path through [money] where the pad actually does something.
 */
private const val SINGLE_DIGIT_CENTS = 4.05

// ponytail: one runnable check instead of a test module. money() is the only arithmetic on this
// page and it prints the figures the whole surface is arguing about. Driven off `spend` rather than
// off copies of its numbers, so a regenerated corpus moves the check with it. Call it from any
// target's main() while poking at the screen.
internal fun makingSelfCheck() {
    check(money(spend.totalUsd) == "\$4.68") { "the measured total" }
    check(money(spend.artUsd) == "\$4.20") { "a trailing zero is not dropped" }
    check(money(spend.auditsUsd) == "\$0.32") { "zero dollars still prints the leading zero" }
    check(money(SINGLE_DIGIT_CENTS) == "\$4.05") { "a single-digit cent count is padded" }
}
