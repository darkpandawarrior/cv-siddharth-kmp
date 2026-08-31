@file:OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

package com.siddharth.cv.shared.excelsior

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.siddharth.cv.shared.LocalNav
import com.siddharth.cv.shared.Route
import com.siddharth.cv.shared.anthology.InkColors
import com.siddharth.cv.shared.data.generated.ExcelsiorEdition
import com.siddharth.cv.shared.data.generated.ExcelsiorMark
import com.siddharth.cv.shared.data.generated.excelsiorEditions
import com.siddharth.cv.shared.data.generated.excelsiorMarks
import com.siddharth.cv.shared.data.profile
import com.siddharth.cv.shared.media.ProjectShot
import com.siddharth.cv.shared.theme.CvCard
import com.siddharth.cv.shared.theme.CvContentMaxWidth
import com.siddharth.cv.shared.theme.CvGutter
import com.siddharth.cv.shared.theme.CvSectionGap
import com.siddharth.cv.shared.theme.CvTheme
import com.siddharth.cv.shared.theme.GhostButton
import com.siddharth.cv.shared.theme.MonoMeta
import com.siddharth.cv.shared.theme.Reveal
import com.siddharth.cv.shared.theme.SectionEyebrow
import com.siddharth.cv.shared.theme.TagChip
import com.siddharth.cv.shared.theme.cvColors
import com.siddharth.cv.shared.theme.cvType

/**
 * Port of cv-siddharth/src/routes/excelsior.tsx and the Flipbook.tsx reader it hosts: MANIT
 * Bhopal's institute magazine, all 396 pages of it, read here rather than linked away.
 *
 * THE README WAS WRONG ABOUT THE COST, and this file is the correction. It said this route needed
 * "scanned page imagery, which this port ships no bitmaps for". It ships none: `coil-network-ktor3`
 * is already a dependency, [ProjectShot] already streams the live site's CDN with a graceful floor,
 * and the pages are already published beside the screenshots under `/excelsior/pages/<year>/`. The
 * whole imagery cost was one URL builder, [pageUrl]. What was actually true is the narrower claim
 * now in the doc: skiko has no AVIF decoder, so this asks for the `.webp` sibling.
 *
 * THE READER IS A SCROLL, NOT A PAGE TURN, and that is a deliberate trade rather than a shortfall.
 * The web reader animates a real leaf rotating about the spine, front and back faces painted, the
 * next spread already underneath. A canvas can do that; what it cannot do cheaply is the thing the
 * leaf exists to hide, which is that only the pages near the current spread are ever in the DOM. A
 * `LazyColumn` gets that for free and gives back what the flip cost: you can move through 128 pages
 * with a wheel instead of 64 clicks, and the sticky bar keeps the edition switch and the counter on
 * screen the whole way down, which the web bar scrolls away. Facing pages still land together on a
 * wide viewport ([spreads]), because that pairing is artwork, not chrome.
 *
 * WHAT WAS DROPPED, named rather than papered over:
 *  - The page-turn animation itself. See above.
 *  - The thumbnail sheet behind the grid button. It exists on the web because turning one spread at
 *    a time through 128 pages is unusable; here the scroll is the answer, and a sheet would fetch
 *    another 128 images to save a gesture that already costs nothing.
 *  - The prev/next arrows, for the same reason.
 *  - A generic "go to page N" jump. The ten marks are the pages worth landing on and everything
 *    else is a scroll. Add one when someone asks for page 73 on purpose.
 *  - The `title` tooltip on each jump chip. A tooltip is invisible on touch and needs a primitive
 *    this build does not have, so a mark's note is printed under the page it annotates instead,
 *    which is where it was always about.
 *  - The three glyphs (U+270E, U+275D, U+2726) that colour-code the chips on the web. Neither
 *    vendored font cut carries them and Skia paints a missing glyph as a tofu box, so the kind is
 *    spelled out in words under the page. Words were the better channel anyway: colour was doing
 *    that job alone on the web.
 *
 * @param year the `?year=` param. Unknown or absent falls back to the newest edition, which is what
 *   `validateSearch` in excelsior.tsx does with the same input.
 * @param page the `?page=` param, clamped into the edition. Null means "no page requested", and
 *   that distinction is load-bearing: an explicit page scrolls straight to it, while a bare
 *   `/excelsior` must land on the header rather than skipping it to show the cover.
 * @param onOpenRead opens `/read/<slug>` for one of the five pieces with a readable version. A
 *   callback rather than a `Route` because this file does not own Nav.kt.
 */
@Composable
fun ExcelsiorScreen(
    year: String?,
    page: Int?,
    onOpenRead: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The magazine belongs to the ink world, not the engineering control room, so it takes the same
    // scoped palette /ink and /canon take. Reused from the anthology rather than copied a third
    // time; InkScreen already keeps a private duplicate of these nine tokens.
    CvTheme(colors = InkColors) {
        ExcelsiorBody(year, page, onOpenRead, modifier)
    }
}

@Composable
private fun ExcelsiorBody(
    yearParam: String?,
    pageParam: Int?,
    onOpenRead: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = cvColors
    val nav = LocalNav.current
    val uri = LocalUriHandler.current
    val density = LocalDensity.current
    val list = rememberLazyListState()

    // The reading position lives here, not in the URL. The web writes every turn back to the
    // address bar with `replace: true`; CvNavState has no replace that preserves the stack, so the
    // honest options were "push a back-stack entry per page" or "the address is where you came in".
    // A pasted `?year=2021&page=5` still opens exactly that spread, which is the claim the route's
    // own doc comment makes; only the live write-back is gone.
    var edition by remember { mutableStateOf(editionOf(yearParam)) }
    var pending by remember { mutableStateOf<Int?>(null) }

    // A route change while already mounted (the jump chips on /ink point here) re-seeds both.
    LaunchedEffect(yearParam, pageParam) {
        val next = editionOf(yearParam)
        edition = next
        pending = pageParam?.coerceIn(1, next.pages)
    }

    BoxWithConstraints(modifier.fillMaxSize().background(colors.ink)) {
        val twoUp = maxWidth >= SpreadBreakpoint
        val rows = remember(edition, twoUp) { spreads(edition.pages, twoUp) }
        val marks = remember(edition) { excelsiorMarks.filter { it.year == edition.year }.associateBy { it.page } }

        // Page rows carry an Int key and every piece of chrome carries a String one, which is the
        // whole trick behind the live counter below: the first visible Int key IS the page on
        // screen, with no header-count constant to drift when a section is added.
        val visiblePage by remember(rows) {
            derivedStateOf { list.layoutInfo.visibleItemsInfo.firstNotNullOfOrNull { it.key as? Int } }
        }

        // One-shot, so clicking the same mark twice re-arms it: `pending` goes null -> 44 again and
        // the effect fires a second time. Keyed on `rows` as well so a year switch scrolls only
        // once the new edition's spreads exist.
        LaunchedEffect(rows, pending) {
            val target = pending ?: return@LaunchedEffect
            val row = rows.indexOfFirst { target in it }
            if (row >= 0) {
                // Negative offset, or the sticky bar covers the top of the page it just took you to.
                list.scrollToItem(row + pageRowsStart, -with(density) { ReaderBarHeight.roundToPx() })
            }
            pending = null
        }

        LazyColumn(
            // focusable(), so Tab reaches the reader and the arrow keys move it. The web reader
            // binds Arrow/Home/End by hand on `window`; here the scroll container does it once it
            // can hold focus, which is also what makes 396 pages keyboard-reachable at all.
            modifier = Modifier.fillMaxSize().focusable(),
            state = list,
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(top = 32.dp, bottom = 120.dp),
        ) {
            item("chrome") {
                Chrome(
                    onBack = { nav.go(Route.Ink) },
                    onOpenRead = onOpenRead,
                    onJump = { y, p ->
                        edition = editionOf(y)
                        pending = p
                    },
                )
            }
            stickyHeader(key = "bar") {
                ReaderBar(
                    edition = edition,
                    visiblePage = visiblePage,
                    onEdition = { e ->
                        edition = e
                        pending = 1
                    },
                    onSource = { uri.openUri(edition.source) },
                )
            }
            items(rows, key = { it.first() }) { row ->
                PageRow(edition.year, row, twoUp, marks)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Measure and constants
// ---------------------------------------------------------------------------------------------

/** `max-w-6xl mx-auto px-6`, held to the port's shared measure so the reader is not wider than /ink. */
private fun Modifier.pageMeasure(): Modifier =
    this.widthIn(max = CvContentMaxWidth).fillMaxWidth().padding(horizontal = CvGutter)

/** Below this a spread would give each page less than a phone's width, so pages stack singly. */
private val SpreadBreakpoint: Dp = 720.dp

/** Fixed, because the scroll offset that keeps the bar off the target page is measured from it. */
private val ReaderBarHeight: Dp = 56.dp

/** Chrome item, then the sticky bar. Page rows start after both. */
private const val pageRowsStart = 2

/**
 * The tallest trim in the archive: every page is 950px wide, and the heights run 1176-1343 (2019 is
 * uniformly 1343, 2021 uniformly 1237, 2020 mixed across five sizes). Framing at the tallest means
 * every page fits by width and fills the column; a shorter one leaves a little vertical slack
 * instead of being pillarboxed, which is the better of the two ways to be wrong.
 */
private const val pageAspect = 950f / 1343f

private val PageShape = RoundedCornerShape(4.dp)

// ---------------------------------------------------------------------------------------------
// The prose, the five, and the marks
// ---------------------------------------------------------------------------------------------

@Composable
private fun Chrome(
    onBack: () -> Unit,
    onOpenRead: (String) -> Unit,
    onJump: (String, Int) -> Unit,
) {
    val colors = cvColors
    // Derived, never typed: the sentence counts the corpus rather than repeating 396 at it.
    val totalPages = remember { excelsiorEditions.sumOf { it.pages } }
    val readable = remember { excelsiorMarks.filter { it.readSlug != null } }

    Reveal {
        Column(Modifier.pageMeasure()) {
            GhostButton(text = "The Ink", onClick = onBack)
            Spacer(Modifier.height(28.dp))
            SectionEyebrow("// print, 2019-21")
            Spacer(Modifier.height(12.dp))
            // Deliberately smaller than a landing-page hero on the web too: this is a reader, and
            // the furniture yields vertical space to the spread.
            BasicText(text = "Excelsior", style = cvType.h2)
            Spacer(Modifier.height(14.dp))
            BasicText(
                modifier = Modifier.widthIn(max = 680.dp),
                text =
                    "MANIT Bhopal's institute magazine, running since 1963. I was an English " +
                        "Editor on the 2019 and 2020 editions and Joint Chief Editor on 2021. The " +
                        "sign-off is on page 5 of '21. All $totalPages pages are hosted here; the " +
                        "original PDFs stay with MANIT.",
                style = cvType.body.copy(color = colors.muted),
            )

            // This card comes FIRST, before the scans, because the pages are the artefact and not
            // the reading: the text in them is unselectable, invisible to search, and unusable on a
            // phone. Read it here, then go and look at the page it ran on.
            Spacer(Modifier.height(24.dp))
            CvCard(Modifier.fillMaxWidth(), glowOnHover = false) {
                MonoMeta("RATHER READ IT? THE ${readable.size} PIECES I WROTE, IN FULL")
                Spacer(Modifier.height(14.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    readable.forEach { m ->
                        val slug = m.readSlug ?: return@forEach
                        TagChip(
                            text = "${m.label} '${m.year.takeLast(2)}",
                            selected = true,
                            onClick = { onOpenRead(slug) },
                        )
                    }
                }
            }

            // Jump into the scan itself. Colour still separates the three kinds, exactly as it does
            // on the web, but it is never the only channel: the page each chip lands on prints the
            // kind in words.
            Spacer(Modifier.height(20.dp))
            MonoMeta("WHAT IS MARKED IN THESE PAGES")
            Spacer(Modifier.height(12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                excelsiorMarks.forEach { m ->
                    TagChip(
                        text = "${m.label} '${m.year.takeLast(2)} p${m.page}",
                        tint = kindTint(m.kind),
                        onClick = { onJump(m.year, m.page) },
                    )
                }
            }
            Spacer(Modifier.height(CvSectionGap))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// The reader bar
// ---------------------------------------------------------------------------------------------

/**
 * The flipbook bar, pinned. Opaque ground on purpose: a translucent pinned header is what makes an
 * automated contrast check report *incomplete* rather than pass, the same reason the ops board's
 * banner is solid.
 *
 * It scrolls sideways rather than wrapping, so its height stays the constant the scroll offset above
 * is measured from and a 390px viewport never reflows it into two rows.
 */
@Composable
private fun ReaderBar(
    edition: ExcelsiorEdition,
    visiblePage: Int?,
    onEdition: (ExcelsiorEdition) -> Unit,
    onSource: () -> Unit,
) {
    val colors = cvColors
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(ReaderBarHeight)
                .background(colors.surface)
                .drawBehind {
                    val h = 1.dp.toPx()
                    drawRect(colors.line, topLeft = Offset(0f, size.height - h), size = Size(size.width, h))
                }
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = CvGutter),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        excelsiorEditions.forEach { e ->
            val current = e.year == edition.year
            TagChip(
                text = "'${e.year.takeLast(2)}",
                modifier =
                    Modifier.semantics {
                        // `aria-pressed` on the web. Without it the only signal that an edition is
                        // the open one is an ochre border, which is colour alone.
                        selected = current
                        contentDescription = "Excelsior ${e.year}, ${e.pages} pages"
                    },
                selected = current,
                onClick = { onEdition(e) },
            )
        }
        Spacer(Modifier.width(4.dp))
        BasicText(
            // `aria-live="polite"`: the counter is the only thing that tells a screen reader the
            // scroll moved, since the pages themselves are one long list of images.
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            text = if (visiblePage == null) "${edition.pages} pages" else "p.$visiblePage / ${edition.pages}",
            style = cvType.mono.copy(color = colors.onBackground),
        )
        Spacer(Modifier.width(4.dp))
        TagChip(
            text = "Original PDF",
            modifier =
                Modifier.semantics {
                    contentDescription = "Open the original Excelsior ${edition.year} PDF at MANIT"
                },
            onClick = onSource,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// The pages
// ---------------------------------------------------------------------------------------------

/**
 * One row of the reader: a single page, or the two that faced each other in print.
 *
 * The fallback under a page while it streams is [ProjectShot]'s generated gradient, which is a 16:9
 * panel sitting inside a portrait frame. It is left that way rather than forked: it reads as
 * "loading" for the half-second Coil needs, and a second image pipeline shaped for one screen would
 * cost more than the band does.
 */
@Composable
private fun PageRow(
    year: String,
    pages: List<Int>,
    twoUp: Boolean,
    marks: Map<Int, ExcelsiorMark>,
) {
    val colors = cvColors
    Column(Modifier.pageMeasure().padding(bottom = 24.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // The cover, and a final odd page, sit centred at half width rather than shoved against
            // the spine with a hole beside them.
            val lone = twoUp && pages.size == 1
            if (lone) Spacer(Modifier.weight(0.5f))
            pages.forEach { n ->
                Page(year, n, Modifier.weight(1f))
            }
            if (lone) Spacer(Modifier.weight(0.5f))
        }
        pages.mapNotNull { marks[it] }.forEach { m ->
            val tint = kindTintOf(m.kind, colors.accent, colors.accent2, colors.muted)
            Column(
                Modifier
                    .padding(top = 14.dp)
                    .drawBehind { drawRect(tint, size = Size(2.dp.toPx(), size.height)) }
                    .padding(start = 14.dp)
                    .widthIn(max = 640.dp),
            ) {
                BasicText(
                    text = "${kindLabel(m.kind)} · P${m.page} · ${m.label.uppercase()}",
                    style = cvType.metaMono.copy(color = tint),
                )
                Spacer(Modifier.height(6.dp))
                BasicText(text = m.note, style = cvType.bodySmall)
            }
        }
    }
}

@Composable
private fun Page(year: String, n: Int, modifier: Modifier) {
    ProjectShot(
        url = pageUrl(year, n),
        label = "Excelsior $year, page $n",
        modifier = modifier.aspectRatio(pageAspect).border(1.dp, cvColors.line, PageShape),
        // Fit, not Crop: cropping a magazine page cuts the text off the edge of the artefact.
        contentScale = ContentScale.Fit,
    )
}

// ---------------------------------------------------------------------------------------------
// Derivations. Read these, never a literal.
// ---------------------------------------------------------------------------------------------

/**
 * `/excelsior/pages/<year>/pNNN.webp`, the rule `src/data/excelsior.ts` builds and the generated
 * corpus records rather than emitting 396 strings. `.webp` and never `.avif`: skiko ships no AVIF
 * decoder, and an avif URL renders blank with nothing in the log.
 */
private fun pageUrl(year: String, n: Int): String =
    "${profile.portfolio.trimEnd('/')}/excelsior/pages/$year/p${n.toString().padStart(3, '0')}.webp"

/** Unknown or absent year is the newest edition, which is what `validateSearch` does with it. */
private fun editionOf(year: String?): ExcelsiorEdition =
    excelsiorEditions.firstOrNull { it.year == year } ?: excelsiorEditions.first()

/**
 * How a magazine actually opens: page 1 alone on the shelf, then true spreads (2|3, 4|5), so facing
 * pages designed as one artwork land together. Narrow viewports get one page per row, because half
 * of 390px is not a page.
 */
private fun spreads(total: Int, twoUp: Boolean): List<List<Int>> {
    if (!twoUp) return (1..total).map { listOf(it) }
    val out = mutableListOf(listOf(1))
    var p = 2
    while (p <= total) {
        out += if (p < total) listOf(p, p + 1) else listOf(p)
        p += 2
    }
    return out
}

/** `wrote | about | credit`, the three the generator validates. */
private fun kindLabel(kind: String): String = when (kind) {
    "wrote" -> "I WROTE THIS"
    "about" -> "ABOUT ME"
    else -> "THE CREDIT"
}

@Composable
private fun kindTint(kind: String): Color =
    kindTintOf(kind, cvColors.accent, cvColors.accent2, cvColors.muted)

private fun kindTintOf(kind: String, wrote: Color, about: Color, credit: Color): Color = when (kind) {
    "wrote" -> wrote
    "about" -> about
    else -> credit
}

// ---------------------------------------------------------------------------------------------
// Self-check
// ---------------------------------------------------------------------------------------------

/**
 * ponytail: one runnable check instead of a test module, the same shape `navSelfCheck` and
 * `canonSelfCheck` already have. Must be called from `selfCheck()` in jvmMain's Prerender.kt:
 * nothing here runs it, and that file belongs to the spine.
 *
 * It guards the two derivations a reader silently breaks on. [spreads] dropping or duplicating a
 * page shows up as a magazine that skips p.63, which nobody notices in a build log; a mark pointing
 * past the end of its edition is a chip that scrolls nowhere at all.
 */
internal fun excelsiorSelfCheck() {
    excelsiorEditions.forEach { e ->
        listOf(true, false).forEach { twoUp ->
            val rows = spreads(e.pages, twoUp)
            check(rows.flatten() == (1..e.pages).toList()) {
                "${e.year} twoUp=$twoUp must lay out every page once, in order"
            }
        }
        val wide = spreads(e.pages, twoUp = true)
        check(wide.first() == listOf(1)) { "${e.year}: the cover opens alone" }
        check(wide[1] == listOf(2, 3)) { "${e.year}: 2 and 3 face each other" }
    }

    check(editionOf(null) == excelsiorEditions.first()) { "no year is the newest edition" }
    check(editionOf("1863") == excelsiorEditions.first()) { "an unknown year is the newest edition" }

    excelsiorMarks.forEach { m ->
        val e = excelsiorEditions.firstOrNull { it.year == m.year }
        checkNotNull(e) { "mark '${m.label}' names edition ${m.year}, which is not in the corpus" }
        check(m.page in 1..e.pages) { "mark '${m.label}' points at p${m.page} of ${e.pages}" }
    }

    check(pageUrl("2021", 5).endsWith("/excelsior/pages/2021/p005.webp")) { "three-digit page names" }
}
