@file:OptIn(ExperimentalLayoutApi::class)

package com.siddharth.cv.shared.writing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.unit.dp
import com.siddharth.cv.shared.CvNavState
import com.siddharth.cv.shared.LocalNav
import com.siddharth.cv.shared.data.generated.BoardProfile
import com.siddharth.cv.shared.data.generated.Society
import com.siddharth.cv.shared.data.generated.WritingArchivePiece
import com.siddharth.cv.shared.data.generated.anthology
import com.siddharth.cv.shared.data.generated.anthologyEntries
import com.siddharth.cv.shared.data.generated.boardArc
import com.siddharth.cv.shared.data.generated.boardProfiles
import com.siddharth.cv.shared.data.generated.coverStory2021
import com.siddharth.cv.shared.data.generated.loopdownOrigin
import com.siddharth.cv.shared.data.generated.societies
import com.siddharth.cv.shared.data.generated.writingArchive
import com.siddharth.cv.shared.theme.CvCard
import com.siddharth.cv.shared.theme.CvColors
import com.siddharth.cv.shared.theme.CvDarkColors
import com.siddharth.cv.shared.theme.CvSectionGap
import com.siddharth.cv.shared.theme.CvTheme
import com.siddharth.cv.shared.theme.MonoMeta
import com.siddharth.cv.shared.theme.Reveal
import com.siddharth.cv.shared.theme.SectionEyebrow
import com.siddharth.cv.shared.theme.TagChip
import com.siddharth.cv.shared.theme.cvColor
import com.siddharth.cv.shared.theme.cvColors
import com.siddharth.cv.shared.theme.cvType
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The Ink, the writing years given their own world: the port of cv-siddharth/src/routes/ink.tsx
 * plus the section it mounts, src/WritingSection.tsx.
 *
 * The palette swap is the whole point of the route and it is done the idiomatic way here. One
 * nested [CvTheme] shadows `LocalCvColors` for the subtree, and every surface below is an ordinary
 * [CvCard] / [TagChip] / [MonoMeta] reading `cvColors`. Nothing inside names a colour, which is
 * exactly what `.ink-world` does on the web by re-declaring the same custom properties on a wrapper
 * element. Same mechanism as `projectColors(project.theme)` on a project page and as the theme lab
 * on the homepage.
 *
 * Two deliberate losses, neither faked:
 *
 * 1. The web world also swaps `--font-display` to Rozha One, a high-contrast display serif. No
 *    serif is vendored in `composeResources/font/` and Skia never consults the browser font stack,
 *    so the type here stays Space Grotesk. The colour half of the swap is complete; the type half
 *    is not.
 * 2. `.ink-world::before` lays two offset dot fields over the ground as paper fibre. On the canvas
 *    that is thousands of `drawCircle` calls repainted on every scroll frame, which is the kind of
 *    stutter this port exists to disprove, so the grain is dropped rather than shipped slow.
 */
@Composable
fun InkScreen(
    onOpenLoopdown: () -> Unit,
    onOpenAnthology: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CvTheme(colors = CvInkColors) {
        InkBody(onOpenLoopdown, onOpenAnthology, modifier)
    }
}

/**
 * index.css `.ink-world`, token for token: warm sepia ground, cream text, ochre accent, terracotta
 * replacing the telemetry cyan. Still dark on purpose. Going to cream paper mid-scroll is a
 * flashbang, and the contrast floors are tuned against dark grounds, so this reads as a different
 * room in the same building rather than a different building.
 *
 * The contrast numbers the CSS records with these values: ochre 8.42:1 on the ink ground, muted
 * 6.65:1. Do not nudge them without recomputing.
 */
private val CvInkColors: CvColors =
    CvDarkColors.copy(
        accent = cvColor("#d9a441"),
        accentDim = cvColor("#b8842c"),
        accent2 = cvColor("#cf8f63"),
        ink = cvColor("#14100c"),
        surface = cvColor("#1b1611"),
        card = cvColor("#221b15"),
        line = cvColor("#3a2f24"),
        onBackground = cvColor("#efe7d8"),
        muted = cvColor("#a4978a"),
    )

@Composable
private fun InkBody(
    onOpenLoopdown: () -> Unit,
    onOpenAnthology: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = cvColors
    val nav = LocalNav.current
    val uri = LocalUriHandler.current

    LazyColumn(
        modifier = modifier.fillMaxSize().background(colors.ink),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        item { InkHero(onOpenAnthology) }
        item { ArchiveIntro(onOpenLoopdown, nav, uri) }

        items(writingArchive, key = { it.slug }) { piece ->
            Column(Modifier.writingMeasure().padding(bottom = 12.dp)) {
                ArchiveCard(piece)
            }
        }

        item { ExcelsiorSection(nav, uri) }
        item { BoardSection(nav, uri) }
        item { SocietiesSection(nav, uri) }
    }
}

// ---------------------------------------------------------------------------------------------
// The doorway
// ---------------------------------------------------------------------------------------------

@Composable
private fun InkHero(onOpenAnthology: () -> Unit) {
    val colors = cvColors
    Reveal {
        Column(Modifier.writingMeasure().padding(top = 40.dp)) {
            // The one hero-sized title in the writing world. /ink is a doorway page, which is the
            // difference the React pair encodes too: this is an h1 at text-hero, and The Loopdown
            // deliberately opens one step down at h2.
            SectionEyebrow("// before the code")
            Spacer(Modifier.height(14.dp))
            BasicText(text = "The Ink", style = cvType.hero)
            Spacer(Modifier.height(24.dp))

            // The epigraph, quoted from beforeTheCode.ts rather than retyped. It is the throughline
            // from a college magazine to a production codebase, and it ends on the only line here
            // that is also a claim about the engineer, so it opens the world.
            Row(Modifier.height(IntrinsicSize.Min).widthIn(max = 680.dp)) {
                Box(Modifier.width(2.dp).fillMaxHeight().background(colors.accent.copy(alpha = 0.5f)))
                Spacer(Modifier.width(18.dp))
                BasicText(
                    text = boardArc,
                    style = cvType.body.copy(fontSize = cvType.body.fontSize * 1.1f),
                )
            }

            Spacer(Modifier.height(28.dp))

            // Derived, never typed: the sentence used to say "Twenty" and had been wrong since
            // season three shipped. A count typed beside the data that decides it always drifts.
            CvCard(modifier = Modifier.widthIn(max = 640.dp), onClick = onOpenAnthology) {
                BasicText(text = "${anthology.title}.", style = cvType.cardTitle)
                Spacer(Modifier.height(8.dp))
                BasicText(
                    text =
                        "${anthologyEntries.size} pieces of framed short fiction across " +
                            "${anthology.seasons.size} seasons, a galactic field reporter filing " +
                            "what he finds until he stops filing.",
                    style = cvType.bodySmall,
                )
                Spacer(Modifier.height(12.dp))
                MonoMeta(anthology.tagline)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// The archive
// ---------------------------------------------------------------------------------------------

@Composable
private fun ArchiveIntro(onOpenLoopdown: () -> Unit, nav: CvNavState, uri: UriHandler) {
    Reveal {
        Column(Modifier.writingMeasure().padding(top = CvSectionGap)) {
            WritingSectionHead("// the archive", "Writing")

            // Derived, like the anthology count above it: the number and the grid it counts are
            // finally on the same screen.
            BasicText(
                text =
                    "${writingArchive.size} pieces of short fiction, campus lore, satire and " +
                        "essays. Everything I wrote before I wrote code, first in a college " +
                        "magazine and then on a blog.",
                modifier = Modifier.widthIn(max = 640.dp),
                style = cvType.body,
            )

            Spacer(Modifier.height(20.dp))

            // The name is inherited, not invented, which is worth saying up front since it is the
            // whole reason an Android engineer has a writing section at all.
            BasicText(
                text =
                    "\"The Loopdown\" isn't a brand I made up. It's a short story I wrote for " +
                        "Excelsior '${loopdownOrigin.year.takeLast(2)}, ${loopdownOrigin.story}. " +
                        "The hub, the repo and the series all still carry its name.",
                modifier = Modifier.widthIn(max = 640.dp),
                style = cvType.bodySmall,
            )
            Spacer(Modifier.height(12.dp))
            ScanChip(loopdownOrigin.year, loopdownOrigin.page, nav, uri)

            Spacer(Modifier.height(28.dp))

            CrossWorldNote(
                body =
                    "That hub is still running. The field notes, the series and the cast of " +
                        "personified bugs they star live in The Loopdown.",
                linkLabel = "The Loopdown",
                onClick = onOpenLoopdown,
            )

            Spacer(Modifier.height(28.dp))

            // Books Before Bros heads the grid rather than sitting in it: most of the pieces below
            // were published there first.
            CvCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { uri.openUri(BOOKS_BEFORE_BROS_URL) },
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    BasicText(
                        text = "Books Before Bros",
                        modifier = Modifier.weight(1f),
                        style = cvType.cardTitle,
                    )
                    Spacer(Modifier.width(12.dp))
                    MonoMeta("THE ORIGIN BLOG")
                }
                Spacer(Modifier.height(8.dp))
                BasicText(
                    text =
                        "The original blog. Essays, campus lore and short fiction from before the " +
                            "code. Most of the pieces below were first published there, at " +
                            "booksbeforebros.wordpress.com.",
                    style = cvType.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun ArchiveCard(piece: WritingArchivePiece) {
    CvCard(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BasicText(text = piece.title, modifier = Modifier.weight(1f), style = cvType.cardTitle)
            val form = piece.form
            if (form != null) {
                Spacer(Modifier.width(12.dp))
                MonoMeta(form)
            }
        }
        val blurb = piece.blurb
        if (blurb != null) {
            Spacer(Modifier.height(8.dp))
            BasicText(text = blurb, style = cvType.bodySmall)
        }
        val meta = archiveMeta(piece)
        if (meta.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            MonoMeta(meta.joinToString("  ·  "))
        }
    }
}

/**
 * `era · 1,966 words · ~9 min read`, the same three facts the web card shows.
 *
 * `words` is typed `String?` because it is a string in every row upstream today and the generator
 * hard-fails the day that stops being true. A row whose words do not parse simply loses the two
 * derived facts rather than rendering a zero.
 */
private fun archiveMeta(piece: WritingArchivePiece): List<String> {
    val words = piece.words?.toIntOrNull()
    return listOfNotNull(
        piece.era,
        words?.let { "${it.grouped()} words" },
        words?.let { "~${max(1, (it / WORDS_PER_MINUTE).roundToInt())} min read" },
    )
}

/** The reading-speed divisor the web card uses. */
private const val WORDS_PER_MINUTE = 220f

/** `1966` -> `"1,966"`. No locale formatting anywhere in this port, on purpose. */
private fun Int.grouped(): String = toString().reversed().chunked(3).joinToString(",").reversed()

// ---------------------------------------------------------------------------------------------
// The print lineage
// ---------------------------------------------------------------------------------------------

/**
 * The web wraps the magazine, the board profiles and the societies in one `panel`, with cards
 * inside it. Nested two deep, a Compose card inside a card inside a scroll reads as noise rather
 * than as hierarchy, so the panel is flattened into three sections and the cards keep their own
 * frame. Nothing is lost but a border.
 */
@Composable
private fun ExcelsiorSection(nav: CvNavState, uri: UriHandler) {
    Reveal {
        Column(Modifier.writingMeasure().padding(top = CvSectionGap)) {
            WritingSectionHead("// print", "Excelsior, MANIT's institute magazine")
            MonoMeta("PRINT · 2019-21")
            Spacer(Modifier.height(16.dp))
            BasicText(
                text =
                    "Three years on the Editorial Board at NIT Bhopal. English Editor on the 2019 " +
                        "and 2020 editions, Joint Chief Editor on 2021. That last one was 128 " +
                        "pages shipped entirely remotely through the pandemic, and its cover " +
                        "story was the whole magazine: one frame story branching into three paths " +
                        "a reader chooses between.",
                modifier = Modifier.widthIn(max = 640.dp),
                style = cvType.body,
            )
            Spacer(Modifier.height(18.dp))

            // The hover-to-open cover shelf is dropped: it is three scanned covers, and this port
            // ships no bitmaps. The branching cover story it was there to show is data, so it is
            // rendered as data instead, one chip per path, each opening the page it starts on.
            MonoMeta("THE COVER STORY · PAGE ${coverStory2021.page}")
            Spacer(Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                coverStory2021.paths.forEach { path ->
                    TagChip(
                        text = "${path.name} · p${path.page}",
                        selected = true,
                        onClick = { openScan(EXCELSIOR_COVER_YEAR, path.page, nav, uri) },
                    )
                }
            }
        }
    }
}

@Composable
private fun BoardSection(nav: CvNavState, uri: UriHandler) {
    Reveal {
        Column(Modifier.writingMeasure().padding(top = CvSectionGap)) {
            WritingSectionHead("// eb profiles", "How the board wrote me")
            BasicText(
                text =
                    "Each year every board member gets one question, answered by a teammate " +
                        "impersonating them. Affectionate, unsparing, and not written by me, " +
                        "which is the only reason they're worth reading. Trimmed here to keep " +
                        "other people's names out of it; … marks every cut, and each card " +
                        "opens the scanned page it came from.",
                modifier = Modifier.widthIn(max = 640.dp),
                style = cvType.bodySmall,
            )
            Spacer(Modifier.height(20.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                boardProfiles.forEach { BoardCard(it, nav, uri) }
            }
        }
    }
}

/**
 * A trimmed quote is only honest if the untrimmed one is reachable, and reachable means someone
 * can tell it is there. The scan viewer at /excelsior is not in this build, so the card resolves
 * against the live React site rather than dropping the affordance, and the citation is printed on
 * the card either way.
 */
@Composable
private fun BoardCard(p: BoardProfile, nav: CvNavState, uri: UriHandler) {
    val colors = cvColors
    CvCard(
        modifier = Modifier.widthIn(min = 260.dp, max = 320.dp),
        onClick = { openScan(p.year, p.page, nav, uri) },
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                text = p.title,
                modifier = Modifier.weight(1f),
                style = cvType.cardTitle.copy(color = colors.accent2, fontSize = cvType.body.fontSize),
            )
            Spacer(Modifier.width(10.dp))
            MonoMeta("'${p.year.takeLast(2)}")
        }
        Spacer(Modifier.height(6.dp))
        MonoMeta(p.role)
        Spacer(Modifier.height(14.dp))
        BasicText(text = "Q: ${p.question}", style = cvType.bodySmall)
        Spacer(Modifier.height(8.dp))
        BasicText(text = "\"${p.quote}\"", style = cvType.body)
        Spacer(Modifier.height(14.dp))
        // The web sets the stage direction in CJK corner brackets. Neither vendored face carries
        // them, and an unvendored glyph renders as tofu on the canvas, so it is set in plain marks.
        MonoMeta("~ ${p.direction} ~" + (p.gloss?.let { " · $it" } ?: ""))
        Spacer(Modifier.height(12.dp))
        MonoMeta("EXCELSIOR '${p.year.takeLast(2)} · PAGE ${p.page}")
    }
}

@Composable
private fun SocietiesSection(nav: CvNavState, uri: UriHandler) {
    Reveal {
        Column(Modifier.writingMeasure().padding(top = CvSectionGap)) {
            WritingSectionHead("// the rooms", "The societies")
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                societies.forEach { SocietyCard(it, nav, uri) }
            }
        }
    }
}

@Composable
private fun SocietyCard(s: Society, nav: CvNavState, uri: UriHandler) {
    val colors = cvColors
    CvCard(modifier = Modifier.widthIn(min = 280.dp, max = 400.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                text = s.name,
                modifier = Modifier.weight(1f),
                style = cvType.cardTitle.copy(fontSize = cvType.body.fontSize),
            )
            Spacer(Modifier.width(10.dp))
            MonoMeta(s.years)
        }
        Spacer(Modifier.height(10.dp))
        BasicText(text = s.role, style = cvType.metaMono.copy(color = colors.accent))
        Spacer(Modifier.height(8.dp))
        BasicText(text = s.blurb, style = cvType.bodySmall)
        Spacer(Modifier.height(14.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            s.links.forEach { l ->
                TagChip(text = l.label, onClick = { openWritingLink(l.url, nav, uri) })
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Hand-kept metadata (writingMeta.ts)
// ---------------------------------------------------------------------------------------------

private const val BOOKS_BEFORE_BROS_URL = "https://booksbeforebros.wordpress.com/"

/** The 2021 edition is the one whose cover story branches, per beforeTheCode.ts. */
private const val EXCELSIOR_COVER_YEAR = "2021"

/** One scanned page of one edition, on the live site, in the shape /excelsior expects. */
private fun openScan(year: String, page: Int, nav: CvNavState, uri: UriHandler) {
    openWritingLink("/excelsior?year=$year&page=$page", nav, uri)
}

@Composable
private fun ScanChip(year: String, page: Int, nav: CvNavState, uri: UriHandler) {
    TagChip(
        text = "Excelsior '${year.takeLast(2)} · page $page",
        onClick = { openScan(year, page, nav, uri) },
    )
}
