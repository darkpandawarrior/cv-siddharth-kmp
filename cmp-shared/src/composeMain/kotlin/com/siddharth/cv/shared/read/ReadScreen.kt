package com.siddharth.cv.shared.read

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.siddharth.cv.shared.LocalNav
import com.siddharth.cv.shared.Route
import com.siddharth.cv.shared.anthology.InkColors
import com.siddharth.cv.shared.anthology.grouped
import com.siddharth.cv.shared.data.generated.PrintedPiece
import com.siddharth.cv.shared.data.generated.printedPieces
import com.siddharth.cv.shared.data.profile
import com.siddharth.cv.shared.theme.CvGutter
import com.siddharth.cv.shared.theme.CvTheme
import com.siddharth.cv.shared.theme.GhostButton
import com.siddharth.cv.shared.theme.Reveal
import com.siddharth.cv.shared.theme.cvColor
import com.siddharth.cv.shared.theme.cvColors
import com.siddharth.cv.shared.theme.cvType
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Port of cv-siddharth/src/routes/read.$slug.tsx: the prose, not a photograph of the prose.
 *
 * /excelsior hosts the magazine as scanned pages, which is a faithful artefact and a bad way to
 * read. This is the reading, and every piece that ran on paper carries a link to the exact page it
 * ran on, so a reader gets the text properly AND the evidence that it was really printed.
 *
 * THE README FILED THIS ROUTE UNDER "needs a markdown renderer, a subsystem, not an afternoon".
 * That is wrong, and the correction is worth stating rather than working around. The React route
 * mounts react-markdown + remark-gfm because it serves FOUR corpora, and the anthology half of them
 * carries GFM tables that are load-bearing on three pages. The nine PRINTED pieces are two
 * constructs wide: a `> ` epigraph and `*italic*`. InlineMarkdown.kt is the whole renderer,
 * [readParseSelfCheck] is what keeps that true as the corpus grows, and the subsystem was never
 * needed for this half.
 *
 * WHAT THIS BUILD'S READER SERVES, and it is one corpus of the React route's four. The emitter
 * carries the printed bodies and deliberately leaves the anthology bodies behind, so an anthology,
 * unfiled or sibling slug reaches [OutOfPrint] and is handed the live site. That gap is stated on
 * the page itself rather than papered over.
 *
 * A READER, NOT A CARD GRID. Everything sits inside one 672dp measure, prose is set at 17sp on 1.75
 * leading, and the body blocks are NOT wrapped in [Reveal]. Forty-five paragraphs each fading in as
 * they cross the fold is a page that fights a reader who is scrolling to read rather than scrolling
 * to look, so the motion is spent on the header and the foot and nowhere in between.
 *
 * LOST AGAINST THE WEB, and neither is faked:
 *  - The reading serif. `.ink-world` swaps `--font-display` to Rozha One and only Space Grotesk and
 *    DM Mono are vendored under composeResources/font/, so the room is the right colour in the
 *    wrong face. The same loss InkScreen.kt and CanonScreen.kt already record.
 *  - No italic cut is vendored either, so `FontStyle.Italic` is whatever Skia synthesises from the
 *    regular. The colour half of the emphasis is the half guaranteed to land: index.css lifts `em`
 *    to `--color-text`, a step brighter than the prose around it, and that is applied here as well
 *    as the slant rather than instead of it.
 */
@Composable
fun ReadScreen(
    slug: String,
    onOpenPiece: (String) -> Unit,
    onSeeInPrint: (year: Int, page: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val piece = printedPieces.firstOrNull { it.slug == slug }
    // `.ink-world` is a scoped token override on the web and a nested theme here. Reused from the
    // anthology rather than declared a third time: this page is in the same room.
    CvTheme(colors = InkColors) {
        if (piece == null) {
            OutOfPrint(slug, modifier)
        } else {
            Piece(piece, onOpenPiece, onSeeInPrint, modifier)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Measure and register
// ---------------------------------------------------------------------------------------------

/**
 * `max-w-2xl`. The one measure on this site narrower than `CvContentMaxWidth`, and the reason the
 * page is legible: ten thousand words at a thousand pixels is a wall, and the eye loses the start
 * of the next line on the way back.
 */
private val ReadMeasure: Dp = 672.dp

private fun Modifier.readMeasure(): Modifier =
    this.widthIn(max = ReadMeasure).fillMaxWidth().padding(horizontal = CvGutter)

/** `.piece-body`: `1.0625rem` on `1.75`. */
private val ProseSize = 17.sp
private const val PROSE_LEADING = 1.75f

/**
 * `--color-prose`'s fallback. A step below `onBackground` (#efe7d8) on purpose: long prose set at
 * full heading brightness glares, and the gap is what gives `em` somewhere to go.
 */
private val Prose: Color = cvColor("#ded3c2")

/** `.piece-body blockquote`: the mono register at `0.92em`, quieter than the prose. */
private val QuoteSize = 15.sp
private val QuoteLeading = 26.sp

/** `margin-bottom: 1.35em` under a paragraph, `1.75em` around a quote. */
private val ParagraphGap = 23.dp
private val QuoteGap = 30.dp

/** `border-accent/40` on the two callouts. The epigraph's rule is the full accent. */
private const val CALLOUT_RULE_ALPHA = 0.4f

/** `words / 220`, the reading rate the React page states. */
private const val WORDS_PER_MINUTE = 220.0

private const val FIRST_PUBLISHED_HERE = "First published here"
private const val PERCENT = 100

// ---------------------------------------------------------------------------------------------
// The piece
// ---------------------------------------------------------------------------------------------

@Composable
private fun Piece(
    piece: PrintedPiece,
    onOpenPiece: (String) -> Unit,
    onSeeInPrint: (year: Int, page: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = cvColors
    // Parsed once per piece, not once per frame. The longest body is 17kB and parses to 41 blocks;
    // doing that inside composition would redo it on every scroll-driven recomposition.
    val blocks = remember(piece.slug) { parsePiece(piece.body) }
    val prose = cvType.body.copy(fontSize = ProseSize, lineHeight = ProseSize * PROSE_LEADING, color = Prose)
    val emphasis = SpanStyle(fontStyle = FontStyle.Italic, color = colors.onBackground)

    LazyColumn(
        // focusable(), because the body of this page holds no controls at all: without it Tab goes
        // from the print link straight to the archive list at the foot, past ten thousand words of
        // unreachable scroll. Focused, the list takes the arrow and page keys.
        modifier = modifier.fillMaxSize().background(colors.ink).focusable(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(top = 24.dp, bottom = 120.dp),
    ) {
        item("head") { Head(piece, onSeeInPrint) }

        itemsIndexed(blocks, key = { i, _ -> "block-$i" }) { _, block ->
            if (block.quote) Epigraph(block, emphasis) else Paragraph(block, prose, emphasis)
        }

        item("more") { MoreFromTheArchive(piece, onOpenPiece) }
    }
}

@Composable
private fun Head(piece: PrintedPiece, onSeeInPrint: (year: Int, page: Int) -> Unit) {
    val colors = cvColors
    val nav = LocalNav.current
    val uri = LocalUriHandler.current
    val minutes = max(1, (piece.words / WORDS_PER_MINUTE).roundToInt())
    val printYear = piece.year.toIntOrNull()

    Reveal {
        Column(Modifier.readMeasure()) {
            GhostButton(text = "The Ink", onClick = { nav.go(Route.Ink) })
            Spacer(Modifier.height(32.dp))

            // Four of the nine carry no magazine page, so there is no edition to name and the
            // kicker drops that part rather than inventing one. Three of those four say "First
            // published here", which is the better line anyway and the more interesting claim.
            BasicText(text = kickerOf(piece), style = cvType.metaMono.copy(color = colors.accent))
            Spacer(Modifier.height(12.dp))
            BasicText(text = piece.title, style = cvType.hero)
            Spacer(Modifier.height(14.dp))
            BasicText(
                text = "${piece.words.grouped()} words · about $minutes min",
                style = cvType.bodySmall.copy(color = colors.muted),
            )

            // Two things a reader cannot otherwise know, and both are the interesting part of the
            // artefact.
            if (piece.printWords > 0) {
                Spacer(Modifier.height(20.dp))
                Callout(
                    "This is the draft. Roughly ${piece.printWords.grouped()} words of it ran in " +
                        "the magazine. About ${survivedPercent(piece)}% survived the page count, " +
                        "so most of what follows has never been read by anyone. (The print figure " +
                        "is approximate: counted from OCR of the scan.)",
                )
            }
            if (piece.note == FIRST_PUBLISHED_HERE) {
                Spacer(Modifier.height(20.dp))
                Callout(
                    "Never submitted anywhere, so it was never cut to fit a page, and never had " +
                        "an editor either. This is the length it wanted to be.",
                )
            }

            // The provenance. For a printed piece that is the exact page it ran on, which is the
            // whole reason the magazine was not rebuilt as a scrolling microsite.
            Spacer(Modifier.height(24.dp))
            if (piece.page > 0 && printYear != null) {
                GhostButton(
                    text = "See it in print: page ${piece.page}",
                    onClick = { onSeeInPrint(printYear, piece.page) },
                )
            } else if (piece.url.isNotEmpty()) {
                // Ran on the Editorial Board's blog rather than in the magazine. That blog bylines
                // every post to the society account, so the link is the publication record, not an
                // authorship claim.
                GhostButton(
                    text = "Read it where it ran" + if (piece.published.isEmpty()) "" else ": ${piece.published}",
                    onClick = { uri.openUri(piece.url) },
                )
            }
            Spacer(Modifier.height(36.dp))
        }
    }
}

/** `[form, edition, note]`, whichever of the three this piece actually carries. */
private fun kickerOf(piece: PrintedPiece): String =
    listOf(
        piece.form,
        when {
            piece.page > 0 -> "Excelsior '${piece.year.takeLast(2)}"
            piece.year.isNotEmpty() -> "'${piece.year.takeLast(2)}"
            else -> ""
        },
        piece.note,
    ).filter { it.isNotEmpty() }.joinToString(" · ")

private fun survivedPercent(piece: PrintedPiece): Int =
    (piece.printWords.toDouble() / piece.words * PERCENT).roundToInt()

// ---------------------------------------------------------------------------------------------
// The prose
// ---------------------------------------------------------------------------------------------

@Composable
private fun Paragraph(block: ProseBlock, style: TextStyle, emphasis: SpanStyle) {
    Column(Modifier.readMeasure().padding(bottom = ParagraphGap)) {
        BasicText(text = annotate(block, emphasis), style = style)
    }
}

/**
 * The blurb, set as the epigraph it is. All nine open on it, which is why the parse asserts that
 * rather than hoping for it.
 *
 * Mono rather than the reading face, for the reason index.css gives: a blockquote in this corpus is
 * always quoted or filed text and never the narrator's own voice, so it gets the other typeface at
 * the same weight of attention. Quiet, not neon.
 */
@Composable
private fun Epigraph(block: ProseBlock, emphasis: SpanStyle) {
    val colors = cvColors
    Column(Modifier.readMeasure().padding(bottom = QuoteGap)) {
        Ruled(colors.accent) {
            BasicText(
                text = annotate(block, emphasis),
                style = cvType.mono.copy(fontSize = QuoteSize, lineHeight = QuoteLeading, color = colors.muted),
            )
        }
    }
}

@Composable
private fun Callout(text: String) {
    Ruled(cvColors.accent.copy(alpha = CALLOUT_RULE_ALPHA)) {
        BasicText(text = text, style = cvType.bodySmall.copy(color = Prose))
    }
}

/** The left rule shared by the epigraph and the two callouts: `border-left: 2px` and its gutter. */
@Composable
private fun Ruled(rule: Color, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(Modifier.width(2.dp).fillMaxHeight().background(rule))
        Spacer(Modifier.width(18.dp))
        content()
    }
}

/**
 * The parse's index ranges become real spans here and nowhere else. [ProseBlock.italics] are
 * inclusive at both ends; `addStyle` wants an exclusive end.
 */
private fun annotate(block: ProseBlock, emphasis: SpanStyle): AnnotatedString =
    if (block.italics.isEmpty()) {
        AnnotatedString(block.text)
    } else {
        buildAnnotatedString {
            append(block.text)
            block.italics.forEach { addStyle(emphasis, it.first, it.last + 1) }
        }
    }

// ---------------------------------------------------------------------------------------------
// The register at the foot
// ---------------------------------------------------------------------------------------------

@Composable
private fun MoreFromTheArchive(piece: PrintedPiece, onOpenPiece: (String) -> Unit) {
    val colors = cvColors
    Reveal {
        Column(Modifier.readMeasure().padding(top = 40.dp)) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
            Spacer(Modifier.height(28.dp))
            BasicText(text = "More from the archive", style = cvType.metaMono)
            Spacer(Modifier.height(10.dp))
            printedPieces.filter { it.slug != piece.slug }.forEach { other ->
                ArchiveRow(other, onOpenPiece)
            }
        }
    }
}

@Composable
private fun ArchiveRow(piece: PrintedPiece, onOpenPiece: (String) -> Unit) {
    val colors = cvColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { onOpenPiece(piece.slug) }
            // `divide-y`: the hairline is drawn on top of each row, so the last one has none under
            // it and the block ends on prose rather than on a line.
            .drawBehind { drawRect(colors.line, size = Size(size.width, 1.dp.toPx())) }
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        BasicText(
            text = piece.title,
            modifier = Modifier.weight(1f),
            style = cvType.body.copy(color = colors.onBackground, fontWeight = FontWeight.Bold),
        )
        BasicText(text = rowMeta(piece), style = cvType.metaMono)
    }
}

private fun rowMeta(piece: PrintedPiece): String =
    listOf(
        if (piece.year.isEmpty()) "" else "'${piece.year.takeLast(2)}",
        "${piece.words.grouped()}w",
    ).filter { it.isNotEmpty() }.joinToString(" · ")

// ---------------------------------------------------------------------------------------------
// Not in this build's archive
// ---------------------------------------------------------------------------------------------

/**
 * The 404, and a real statement about this port rather than a dead end.
 *
 * The React /read resolves a slug against four corpora: the printed archive, the anthology's forty
 * eight entries, one unfiled piece and a sibling series. This build carries the printed bodies and
 * not the others, because the emitter excludes them on purpose, so those slugs land here and are
 * handed the live site instead of a blank page or an invented one.
 */
@Composable
private fun OutOfPrint(slug: String, modifier: Modifier = Modifier) {
    val colors = cvColors
    val nav = LocalNav.current
    val uri = LocalUriHandler.current
    Box(modifier.fillMaxSize().background(colors.ink), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.widthIn(max = ReadMeasure).padding(CvGutter),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicText(
                text = "NOT IN THIS ARCHIVE",
                style = cvType.mono.copy(color = colors.accent, fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.height(12.dp))
            BasicText(text = "unknown piece: $slug", style = cvType.mono.copy(color = colors.muted))
            Spacer(Modifier.height(12.dp))
            BasicText(
                text = "This reader carries the ${printedPieces.size} pieces of the printed " +
                    "archive. The anthology entries have bodies too, and they are on the live site " +
                    "rather than in this build.",
                style = cvType.bodySmall,
            )
            Spacer(Modifier.height(24.dp))
            GhostButton(
                text = "Read it on the live site",
                // Not the anthology's openRead(): that helper belongs to the spine and starts
                // routing in-app the moment /read is wired, which would send this button back here.
                onClick = { uri.openUri(profile.portfolio.trimEnd('/') + "/read/" + slug) },
            )
            Spacer(Modifier.height(12.dp))
            GhostButton(text = "The Ink", onClick = { nav.go(Route.Ink) })
        }
    }
}
