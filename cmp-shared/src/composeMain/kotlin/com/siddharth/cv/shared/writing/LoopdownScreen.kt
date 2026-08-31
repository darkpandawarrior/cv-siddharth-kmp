@file:OptIn(ExperimentalLayoutApi::class)

package com.siddharth.cv.shared.writing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.unit.dp
import com.siddharth.cv.shared.CvNavState
import com.siddharth.cv.shared.LocalNav
import com.siddharth.cv.shared.Route
import com.siddharth.cv.shared.data.NamedLink
import com.siddharth.cv.shared.data.generated.PostLinks
import com.siddharth.cv.shared.data.generated.WritingLesson
import com.siddharth.cv.shared.data.generated.writingCast
import com.siddharth.cv.shared.data.generated.writingLessons
import com.siddharth.cv.shared.data.generated.writingSeries
import com.siddharth.cv.shared.data.isInternalLink
import com.siddharth.cv.shared.data.profile
import com.siddharth.cv.shared.theme.CvCard
import com.siddharth.cv.shared.theme.CvContentMaxWidth
import com.siddharth.cv.shared.theme.CvGutter
import com.siddharth.cv.shared.theme.CvSectionGap
import com.siddharth.cv.shared.theme.HeroShimmerText
import com.siddharth.cv.shared.theme.MonoMeta
import com.siddharth.cv.shared.theme.Reveal
import com.siddharth.cv.shared.theme.SectionEyebrow
import com.siddharth.cv.shared.theme.SectionHeading
import com.siddharth.cv.shared.theme.TagChip
import com.siddharth.cv.shared.theme.cvColor
import com.siddharth.cv.shared.theme.cvColors
import com.siddharth.cv.shared.theme.cvType

/**
 * The Loopdown, the port of cv-siddharth/src/WritingView.tsx, the route at /loopdown.
 *
 * Every count, episode number, tag and platform link on this page is read out of
 * `data/generated/CvWritingData.kt`, which gen-loopdown.mjs writes into the React repo and
 * gen-kotlin-data.mjs mirrors here. Nothing about the corpus is typed into this file.
 *
 * The hand-kept half of the React page is a different matter. src/data/writingMeta.ts says of
 * itself "hand-maintained metadata around the auto-generated writing registry": series accents,
 * the platform order, the back-links into the portfolio. So the Kotlin copy below is a second
 * hand-kept file, not a transcript of something a generator owns.
 *
 * Dropped from the web version: the sticky in-page nav (App's TopBar is the site nav on this
 * build) and the "Ask my AI" pill (FloatingChat is mounted once, app-wide, in App.kt, so a
 * second launcher would be a duplicate control rather than a ported one).
 */
@Composable
fun LoopdownScreen(onOpenInk: () -> Unit, modifier: Modifier = Modifier) {
    val colors = cvColors
    val nav = LocalNav.current
    val uri = LocalUriHandler.current

    // WritingView.tsx: published first, then newest-created first. Sorted once per composition
    // rather than per scroll, since the list itself never changes at runtime.
    val lessons =
        remember {
            writingLessons.sortedWith(
                compareBy<WritingLesson> { if (it.status == "published") 0 else 1 }
                    .thenByDescending { it.created.orEmpty() },
            )
        }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(colors.ink),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        item { LoopdownHero(nav, uri) }

        item {
            Reveal {
                Column(Modifier.writingMeasure().padding(top = CvSectionGap)) {
                    WritingSectionHead("// field notes", "Lessons")
                }
            }
        }

        items(lessons, key = { it.slug }) { lesson ->
            Column(Modifier.writingMeasure().padding(bottom = 16.dp)) {
                LessonCard(lesson, nav, uri)
            }
        }

        item {
            Reveal {
                Column(Modifier.writingMeasure().padding(top = CvSectionGap)) {
                    WritingSectionHead("// the shelf", "Series")
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        writingSeries.forEach { s ->
                            // Episode count derived from the registry, so a new post moves the
                            // number without anyone editing a screen.
                            TagChip(
                                text = "${s.title} · ${s.episodes}",
                                selected = true,
                                tint = accentOf(s.id),
                            )
                        }
                    }
                }
            }
        }

        if (writingCast.isNotEmpty()) {
            item {
                Reveal {
                    Column(Modifier.writingMeasure().padding(top = CvSectionGap)) {
                        WritingSectionHead("// cast", "The bugs, personified")
                        BasicText(
                            text =
                                "Every lesson stars a recurring character, the bug itself, given a " +
                                    "face and a motive. Appearances so far:",
                            modifier = Modifier.widthIn(max = 640.dp),
                            style = cvType.bodySmall,
                        )
                        Spacer(Modifier.height(16.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            writingCast.forEachIndexed { i, c ->
                                TagChip(
                                    text = "${titleize(c.id)} ×${c.appearances}",
                                    selected = true,
                                    tint = CastColors[i % CastColors.size],
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Column(Modifier.writingMeasure().padding(top = CvSectionGap)) {
                CrossWorldNote(
                    body =
                        "Everything I wrote before I wrote code, the magazine years, the societies " +
                            "and the archive they produced, lives in The Ink.",
                    linkLabel = "The Ink",
                    onClick = onOpenInk,
                )
            }
        }
    }
}

@Composable
private fun LoopdownHero(nav: CvNavState, uri: UriHandler) {
    Reveal {
        Column(Modifier.writingMeasure().padding(top = 40.dp)) {
            TagChip(text = "The Loopdown", selected = true)
            Spacer(Modifier.height(20.dp))
            // Deliberately h2 and not the hero size, the same demotion the React page documents:
            // this is a writing hub, not a doorway page.
            SectionHeading("Field notes from an engineer who")
            HeroShimmerText(text = "writes.", style = cvType.h2)
            Spacer(Modifier.height(16.dp))
            BasicText(
                text =
                    "Short, sharp lessons pulled from real Android and KMP work, each with a " +
                        "recurring cast of personified bugs. One idea, written once, adapted to " +
                        "dev.to, Medium, Hashnode, and LinkedIn.",
                modifier = Modifier.widthIn(max = 640.dp),
                style = cvType.body,
            )
            Spacer(Modifier.height(20.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TagChip(text = "the-loopdown", onClick = { openWritingLink(LOOPDOWN_REPO, nav, uri) })
                // The feed is served by the React site, so it resolves there like every other
                // path this build does not itself serve.
                TagChip(text = "RSS", onClick = { openWritingLink("/feed.xml", nav, uri) })
            }
        }
    }
}

/**
 * One field note.
 *
 * The React card carries a 3px left rule in the series accent. [CvCard] has no edge slot and the
 * house rule is to use the primitive rather than fork it, so the accent lands on the series label
 * instead, which the web card also tints, so the colour still says the same thing.
 */
@Composable
private fun LessonCard(lesson: WritingLesson, nav: CvNavState, uri: UriHandler) {
    val accent = accentOf(lesson.series)
    val live = lesson.status == "published"
    val links = lesson.links.published()

    CvCard(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                text = titleize(lesson.series).ifBlank { lesson.pillar.orEmpty() },
                modifier = Modifier.weight(1f),
                style = cvType.metaMono.copy(color = accent),
            )
            Spacer(Modifier.width(12.dp))
            TagChip(text = if (live) "LIVE" else "SOON", selected = live)
        }
        Spacer(Modifier.height(10.dp))
        BasicText(text = lesson.title, style = cvType.cardTitle)

        if (lesson.tags.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                lesson.tags.take(3).forEach { TagChip(text = it) }
            }
        }

        if (links.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                MonoMeta("READ ON")
                Spacer(Modifier.width(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    links.forEach { (label, url) ->
                        TagChip(text = label, tint = accent, onClick = { uri.openUri(url) })
                    }
                }
            }
        }

        val home = lesson.series?.let(SeriesProject::get)
        if (home != null) {
            Spacer(Modifier.height(12.dp))
            TagChip(text = home.label, onClick = { openWritingLink(home.url, nav, uri) })
        }
    }
}

// ---------------------------------------------------------------------------------------------
// writingMeta.ts: the hand-kept half, transcribed
// ---------------------------------------------------------------------------------------------

private const val LOOPDOWN_REPO = "https://github.com/darkpandawarrior/the-loopdown"

/**
 * Deliberate accents, pinned to a series id because they mirror the branded post cards the
 * generator already publishes for those five. An OVERRIDE list, not the registry: a new series
 * does not need an entry here, it already has a colour.
 */
private val SeriesColor: Map<String, Color> =
    mapOf(
        "sensors-who-lie" to cvColor("#8f74ff"),
        "the-coroutine-court" to cvColor("#4ec9b0"),
        "the-night-shift" to cvColor("#f0883e"),
        "ghosts-in-the-recomposition" to cvColor("#db61ff"),
        "one-brain-two-bodies" to cvColor("#38bdf8"),
    )

/**
 * The colour for a series nothing knows about. Grey rather than one of the accents above: the
 * React fallback used to be sensors-who-lie's violet, so an unrecognised series rendered as a
 * convincing chip that was lying about which series it belonged to.
 */
private val NeutralSeries: Color = cvColor("#8a8f98")

/** Hues for series with no pinned accent. None of these appears in [SeriesColor]. */
private val AutoPalette: List<Color> =
    listOf("#e5c07b", "#7ee787", "#ff7b72", "#79c0ff", "#f778ba", "#a5d6ff").map(::cvColor)

/**
 * Auto accents assigned by position among the series that have NO pinned colour, never by position
 * in [writingSeries]. The distinction is the whole point: the registry arrives sorted by id, so
 * indexing over all of it means one new series sorting early silently repaints every series after
 * it. Indexing the unpinned subset can at worst reshuffle the auto hues, which is cosmetic, and can
 * never hand two series the same colour, which is not.
 */
private val AutoSeriesColor: Map<String, Color> =
    writingSeries
        .map { it.id }
        .filter { it !in SeriesColor }
        .mapIndexed { i, id -> id to AutoPalette[i % AutoPalette.size] }
        .toMap()

private fun accentOf(id: String?): Color =
    id?.let { SeriesColor[it] ?: AutoSeriesColor[it] } ?: NeutralSeries

/** Cast accents cycle through the series palette: the characters roam between series. */
private val CastColors: List<Color> =
    listOf("#8f74ff", "#4ec9b0", "#f0883e", "#db61ff", "#38bdf8").map(::cvColor)

/**
 * The four syndication targets, in the order writingMeta.ts lists them, filtered to the ones this
 * lesson actually has. A `listOfNotNull` over the four named fields rather than a table of
 * accessors: [PostLinks] is a fixed four-field record, so there is nothing to iterate over.
 */
private fun PostLinks.published(): List<Pair<String, String>> =
    listOfNotNull(
        devto?.let { "dev.to" to it },
        linkedin?.let { "LinkedIn" to it },
        medium?.let { "Medium" to it },
        hashnode?.let { "Hashnode" to it },
    )

/**
 * Each series is field notes from a real build, so the card links straight to it. Genuinely
 * hand-kept: a missing entry costs a back-link, and unlike a missing accent it cannot make two
 * series look alike.
 */
private val SeriesProject: Map<String, NamedLink> =
    mapOf(
        "sensors-who-lie" to NamedLink("Built in: Mileway's location engine", "#project/mileway"),
        "the-coroutine-court" to NamedLink("From: the -80% crashes work", "#work"),
        "the-night-shift" to NamedLink("From: the 50%→95% GPS work", "#work"),
        "ghosts-in-the-recomposition" to NamedLink("From: the ~87% Compose migration", "#work"),
        "one-brain-two-bodies" to
            NamedLink("Built in: PaymentsLab's expect/actual split", "#project/paymentslab"),
        "chain-of-custody" to NamedLink("Built in: Mileway's trip data model", "#project/mileway"),
        "crossing-the-schema" to NamedLink("Built in: Mileway's Room migrations", "#project/mileway"),
        "notes-from-the-loop" to NamedLink("Built in: The Loopdown itself", "#project/the-loopdown"),
    )

/** `"sensors-who-lie"` -> `"Sensors Who Lie"`. Locale-independent, like the JS it ports. */
internal fun titleize(id: String?): String =
    id.orEmpty().split("-").joinToString(" ") { it.replaceFirstChar(Char::uppercaseChar) }

// ---------------------------------------------------------------------------------------------
// Shared with InkScreen
//
// Both writing routes are the same page furniture in two skins, so the furniture lives once, here,
// and InkScreen calls it. Internal rather than private because Kotlin's `private` is file-scoped.
// ---------------------------------------------------------------------------------------------

/** `mx-auto max-w-5xl px-6`, the measure both writing routes share. */
internal fun Modifier.writingMeasure(): Modifier =
    this.widthIn(max = CvContentMaxWidth).fillMaxWidth().padding(horizontal = CvGutter)

@Composable
internal fun WritingSectionHead(eyebrow: String, title: String) {
    SectionEyebrow(eyebrow)
    Spacer(Modifier.height(10.dp))
    SectionHeading(title)
    Spacer(Modifier.height(20.dp))
}

/**
 * The one sentence each writing world spends on the other, and the button that goes there.
 *
 * The web renders it as an inline underlined link inside the paragraph. Compose can annotate a
 * span, but a hit-testable inline link inside BasicText is a different (and worse) target on
 * touch, so the link is lifted out to a real control under the sentence. The words are unchanged.
 */
@Composable
internal fun CrossWorldNote(body: String, linkLabel: String, onClick: () -> Unit) {
    Column {
        BasicText(
            text = body,
            modifier = Modifier.widthIn(max = 640.dp),
            style = cvType.body.copy(color = cvColors.muted),
        )
        Spacer(Modifier.height(14.dp))
        TagChip(text = linkLabel, selected = true, onClick = onClick)
    }
}

/**
 * The one place a URL turns into an action on these two pages.
 *
 * Same contract as ProjectDetailScreen's `openLink`, which is private to that file: a hash is an
 * in-app destination, and a site path this build does not serve (`/excelsior`, `/feed.xml`)
 * resolves against the live React site rather than silently doing nothing.
 */
internal fun openWritingLink(url: String, nav: CvNavState, uri: UriHandler) {
    when {
        url.startsWith("#project/") -> nav.go(Route.ProjectDetail(url.removePrefix("#project/")))
        url.startsWith("#") -> nav.goSection(url.removePrefix("#"))
        isInternalLink(url) -> uri.openUri(profile.portfolio.trimEnd('/') + url)
        else -> uri.openUri(url)
    }
}
