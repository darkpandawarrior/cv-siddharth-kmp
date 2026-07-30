@file:OptIn(ExperimentalLayoutApi::class)

package com.siddharth.cv.shared.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.siddharth.cv.shared.LocalNav
import com.siddharth.cv.shared.Route
import com.siddharth.cv.shared.data.CaseStudy
import com.siddharth.cv.shared.data.Experience
import com.siddharth.cv.shared.data.Metric
import com.siddharth.cv.shared.data.Project
import com.siddharth.cv.shared.data.caseStudies
import com.siddharth.cv.shared.data.education
import com.siddharth.cv.shared.data.experience
import com.siddharth.cv.shared.data.metrics
import com.siddharth.cv.shared.data.openSource
import com.siddharth.cv.shared.data.profile
import com.siddharth.cv.shared.data.projectBySlug
import com.siddharth.cv.shared.data.projectOrder
import com.siddharth.cv.shared.data.projects
import com.siddharth.cv.shared.data.recentGrowth
import com.siddharth.cv.shared.data.sharedFoundation
import com.siddharth.cv.shared.data.siteRooms
import com.siddharth.cv.shared.data.skills
import com.siddharth.cv.shared.theme.AnimatedCounter
import com.siddharth.cv.shared.theme.CvCard
import com.siddharth.cv.shared.theme.ExpanderSection
import com.siddharth.cv.shared.theme.GhostButton
import com.siddharth.cv.shared.data.CvGallery
import com.siddharth.cv.shared.media.ProjectShot
import com.siddharth.cv.shared.theme.MetricGauge
import com.siddharth.cv.shared.theme.MonoMeta
import com.siddharth.cv.shared.theme.PrimaryButton
import com.siddharth.cv.shared.theme.Reveal
import com.siddharth.cv.shared.theme.SectionEyebrow
import com.siddharth.cv.shared.theme.SectionHeading
import com.siddharth.cv.shared.theme.Sparkline
import com.siddharth.cv.shared.theme.StatusDot
import com.siddharth.cv.shared.theme.TagChip
import com.siddharth.cv.shared.theme.cvColor
import com.siddharth.cv.shared.theme.cvColors
import com.siddharth.cv.shared.theme.cvType
import com.siddharth.cv.shared.theme.glow
import com.siddharth.cv.shared.theme.tiltOnHover
import kotlinx.coroutines.delay

/**
 * The seven non-hero homepage sections, ported from cv-siddharth/src/App.tsx.
 *
 * Split out of HomeScreen.kt on purpose: App.tsx is 1178 lines in one file, and a single composable
 * of that size is exactly the shape that trips a Compose-compiler ICE on a beta toolchain. Eight
 * top-level functions cost nothing and give the compiler eight small bodies instead of one huge one.
 *
 * Every surface below is built from CvComponents. Nothing here reads MaterialTheme — an M3 default
 * would drag purple into a green site.
 */

// ---------------------------------------------------------------------------------------------
// Shared layout helpers (private — these are not cross-file API)
// ---------------------------------------------------------------------------------------------

/** `// eyebrow` + `<h2>` + the lede paragraph, the header every section on the site opens with. */
@Composable
private fun SectionHeader(eyebrow: String, heading: String, lede: String? = null) {
    Reveal {
        Column(Modifier.fillMaxWidth()) {
            SectionEyebrow(eyebrow)
            Spacer(Modifier.height(12.dp))
            SectionHeading(heading)
            if (lede != null) {
                Spacer(Modifier.height(10.dp))
                BasicText(
                    text = lede,
                    modifier = Modifier.widthIn(max = 680.dp),
                    style = cvType.bodySmall,
                )
            }
        }
    }
}

/**
 * A plain chunked grid. Deliberately NOT LazyVerticalGrid: every one of these lives inside the
 * homepage LazyColumn, and nesting a lazy scroller of the same axis inside another is an infinite
 * constraint and a crash.
 */
@Composable
private fun <T> GridRows(
    items: List<T>,
    columns: Int,
    modifier: Modifier = Modifier,
    spacing: Dp = 20.dp,
    cell: @Composable (T) -> Unit,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing)) {
        items.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing)) {
                row.forEach { item -> Box(Modifier.weight(1f)) { cell(item) } }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** The `<li>` with its accent dot — used by case studies, projects and the experience timeline. */
@Composable
private fun Bullet(text: String, label: String? = null) {
    val colors = cvColors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .padding(top = 8.dp)
                .size(6.dp)
                .background(colors.accent.copy(alpha = 0.7f), CircleShape),
        )
        Spacer(Modifier.width(10.dp))
        Column {
            if (label != null) {
                BasicText(
                    text = label,
                    style = cvType.bodySmall.copy(color = colors.accent),
                )
            }
            BasicText(text = text, style = cvType.bodySmall)
        }
    }
}

/** A text row that opens an external URL. One place so the focus/hover treatment stays identical. */
@Composable
private fun LinkRow(modifier: Modifier = Modifier, url: String, content: @Composable () -> Unit) {
    val uriHandler = LocalUriHandler.current
    Box(
        modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { uriHandler.openUri(url) }
            .padding(vertical = 10.dp),
    ) {
        content()
    }
}

// ---------------------------------------------------------------------------------------------
// Metrics band
// ---------------------------------------------------------------------------------------------

/**
 * Splits a claim-audited metric string into `prefix + digits + suffix` so [AnimatedCounter] can
 * count the number while the *original* string is what ends up on screen: "~87%" counts 0 -> 87 and
 * renders "~87%", never "87%".
 */
private fun splitMetric(value: String): Triple<String, Int, String> {
    val start = value.indexOfFirst { it.isDigit() }
    if (start < 0) return Triple(value, 0, "")
    var end = start
    while (end < value.length && value[end].isDigit()) end++
    return Triple(value.substring(0, start), value.substring(start, end).toInt(), value.substring(end))
}

@Composable
fun MetricsBand(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val columns =
            when {
                maxWidth >= 860.dp -> 4
                maxWidth >= 520.dp -> 2
                else -> 1
            }
        GridRows(metrics, columns) { MetricTile(it) }
    }
}

@Composable
private fun MetricTile(metric: Metric) {
    val colors = cvColors
    val (prefix, number, suffix) = splitMetric(metric.value)
    // The round-trip guard: if the split can't rebuild the audited string exactly, don't animate it
    // — render the string raw. A counter is never worth silently altering a claim.
    val exact = prefix + number.toString() + suffix == metric.value

    CvCard(glowOnHover = false) {
        if (metric.value.endsWith("%")) {
            MetricGauge(number / 100f, Modifier.align(Alignment.CenterHorizontally))
        } else {
            Sparkline(listOf(0.10f, 0.24f, 0.33f, 0.58f, 0.79f, 1f))
        }
        Spacer(Modifier.height(14.dp))
        if (exact) {
            AnimatedCounter(number, prefix = prefix, suffix = suffix)
        } else {
            BasicText(text = metric.value, style = cvType.metric)
        }
        Spacer(Modifier.height(6.dp))
        BasicText(text = metric.label, style = cvType.body)
        Spacer(Modifier.height(4.dp))
        BasicText(text = metric.detail, style = cvType.bodySmall.copy(color = colors.muted))
    }
}

// ---------------------------------------------------------------------------------------------
// Case studies (#work)
// ---------------------------------------------------------------------------------------------

@Composable
fun CaseStudiesSection(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        // The numbers land first, then the work behind them — App.tsx's <Metrics /> sits directly
        // above <CaseStudies />, and there is no separate scroll target for it.
        MetricsBand()

        Spacer(Modifier.height(56.dp))

        SectionHeader(
            eyebrow = "// featured work",
            heading = "Case studies",
            lede = "The work behind the numbers — problem, approach, and what actually moved.",
        )

        Spacer(Modifier.height(28.dp))

        val featured = caseStudies.firstOrNull()
        if (featured != null) {
            Reveal { CaseStudyCard(featured, index = 0, featured = true) }
            Spacer(Modifier.height(20.dp))
        }

        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = if (maxWidth >= 760.dp) 2 else 1
            GridRows(caseStudies.drop(1), columns) { cs ->
                Reveal(delayMillis = (caseStudies.indexOf(cs) % 2) * 120) {
                    CaseStudyCard(cs, index = caseStudies.indexOf(cs))
                }
            }
        }
    }
}

@Composable
private fun CaseStudyCard(cs: CaseStudy, index: Int, featured: Boolean = false) {
    val colors = cvColors
    CvCard(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth(0.82f)) {
                if (featured) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot()
                        Spacer(Modifier.width(8.dp))
                        BasicText(text = "FLAGSHIP BUILD", style = cvType.eyebrow)
                    }
                    Spacer(Modifier.height(10.dp))
                }
                BasicText(text = cs.metric, style = cvType.metric)
            }
            // The ghost numeral — `text-accent/10` behind the metric, top-right.
            BasicText(
                text = (index + 1).toString().padStart(2, '0'),
                modifier = Modifier.align(Alignment.TopEnd),
                style = cvType.ghostNumeral,
            )
        }

        Spacer(Modifier.height(12.dp))
        BasicText(text = cs.title, style = cvType.cardTitle)
        Spacer(Modifier.height(10.dp))
        BasicText(text = cs.summary, style = cvType.bodySmall)

        ExpanderSection("How I did it", Modifier.padding(top = 8.dp)) {
            BasicText(
                text = cs.problem,
                style = cvType.bodySmall.copy(color = colors.muted),
            )
            cs.approach.forEach { Bullet(it) }
            BasicText(
                text = cs.outcome,
                style = cvType.bodySmall.copy(color = colors.onBackground),
            )
        }

        Spacer(Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            cs.tags.forEach { TagChip(it) }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Projects (#projects)
// ---------------------------------------------------------------------------------------------

/** `projectOrder` first (the pager order the detail route walks), then everything else. */
private val orderedProjects: List<Project> =
    projectOrder.mapNotNull(::projectBySlug) + projects.filter { it.slug !in projectOrder }

@Composable
fun ProjectsSection(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        SectionHeader(
            eyebrow = "// projects & open source",
            heading = "Things I've built",
            lede = "Open-source projects and tooling outside employer work — shipped end-to-end.",
        )

        Spacer(Modifier.height(28.dp))

        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = if (maxWidth >= 760.dp) 2 else 1
            GridRows(orderedProjects, columns) { project ->
                Reveal(delayMillis = (orderedProjects.indexOf(project) % 2) * 120) {
                    ProjectCard(project)
                }
            }
        }

        Spacer(Modifier.height(48.dp))

        Reveal {
            Column(Modifier.fillMaxWidth()) {
                SectionEyebrow("// recently shipped")
                Spacer(Modifier.height(16.dp))
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val columns = if (maxWidth >= 860.dp) 4 else if (maxWidth >= 520.dp) 2 else 1
                    GridRows(recentGrowth.takeLast(4).reversed(), columns, spacing = 16.dp) { g ->
                        CvCard(glowOnHover = false) {
                            MonoMeta(g.date)
                            Spacer(Modifier.height(6.dp))
                            BasicText(text = g.title, style = cvType.body)
                            Spacer(Modifier.height(6.dp))
                            BasicText(text = g.detail, style = cvType.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectCard(project: Project) {
    val nav = LocalNav.current
    val uriHandler = LocalUriHandler.current
    val colors = cvColors
    // The project's own accent, read straight off its theme — the same value ProjectDetailScreen
    // hands to CvTheme when you open it. The card is a preview of the reskin.
    val accent = project.theme?.let { cvColor(it.accent) } ?: colors.accent

    CvCard(
        modifier = Modifier.fillMaxWidth().tiltOnHover(spotlight = accent),
        onClick = {
            if (project.detail != null) {
                nav.go(Route.ProjectDetail(project.slug))
            } else {
                project.links.firstOrNull()?.let { uriHandler.openUri(it.url) }
            }
        },
    ) {
        // Real screenshot from the live site's CDN, falling back to the generated gradient while
        // it loads or if the fetch fails — see ProjectShot.
        ProjectShot(
            url = CvGallery.hero(project.slug),
            label = project.name,
            modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(10.dp)),
        )

        Spacer(Modifier.height(16.dp))
        BasicText(text = project.name, style = cvType.cardTitle)
        Spacer(Modifier.height(6.dp))
        BasicText(text = project.tagline, style = cvType.bodySmall.copy(color = accent))
        Spacer(Modifier.height(10.dp))
        MonoMeta(project.status)

        Spacer(Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            project.stack.forEach { TagChip(it, tint = accent) }
        }

        Spacer(Modifier.height(14.dp))
        BasicText(text = project.description, style = cvType.bodySmall)

        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            project.highlights.take(2).forEach { Bullet(it) }
        }

        if (project.badges.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                project.badges.forEach { TagChip(it) }
            }
        }

        Spacer(Modifier.height(16.dp))
        BasicText(
            text = if (project.detail != null) "View case study" else "Open repository",
            style = cvType.bodySmall.copy(color = accent),
        )
    }
}

// ---------------------------------------------------------------------------------------------
// The Source (#source)
// ---------------------------------------------------------------------------------------------

/**
 * The flattened stand-in for the WebGL `<FoundationGraph />`. The 3D dependency constellation is
 * dropped, not faked — the blurb plus two lib cards carries the same claim ("these two apps sit on
 * one foundation I maintain separately") without a scene graph CMP does not have.
 */
@Composable
fun SourceSection(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        SectionHeader(
            eyebrow = "// the source",
            heading = "Shared foundation",
            lede = null,
        )

        Spacer(Modifier.height(20.dp))

        Reveal {
            BasicText(
                text = sharedFoundation.blurb,
                modifier = Modifier.widthIn(max = 760.dp),
                style = cvType.body,
            )
        }

        Spacer(Modifier.height(28.dp))

        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = if (maxWidth >= 760.dp) 2 else 1
            GridRows(sharedFoundation.libs, columns) { lib ->
                val uriHandler = LocalUriHandler.current
                CvCard {
                    BasicText(text = lib.name, style = cvType.cardTitle)
                    Spacer(Modifier.height(10.dp))
                    BasicText(text = lib.role, style = cvType.bodySmall)
                    Spacer(Modifier.height(14.dp))
                    MonoMeta("used by")
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        lib.usedBy.forEach { TagChip(it) }
                    }
                    Spacer(Modifier.height(16.dp))
                    GhostButton("View on GitHub", onClick = { uriHandler.openUri(lib.url) })
                }
            }
        }

        Spacer(Modifier.height(44.dp))

        Reveal {
            Column(Modifier.fillMaxWidth()) {
                SectionEyebrow("// merged upstream")
                Spacer(Modifier.height(16.dp))
                CvCard(glowOnHover = false) {
                    openSource.forEach { contribution ->
                        LinkRow(url = contribution.url) {
                            Column(Modifier.fillMaxWidth()) {
                                MonoMeta("${contribution.repo} · ${contribution.date} · ${contribution.status}")
                                Spacer(Modifier.height(4.dp))
                                BasicText(text = contribution.title, style = cvType.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Experience (#experience)
// ---------------------------------------------------------------------------------------------

private val DotColumn = 10.dp
private val DotGutter = 22.dp

@Composable
fun ExperienceSection(modifier: Modifier = Modifier) {
    val colors = cvColors

    Column(modifier.fillMaxWidth()) {
        SectionHeader(eyebrow = "// background", heading = "Experience")

        Spacer(Modifier.height(28.dp))

        Column(
            Modifier
                .fillMaxWidth()
                // The spine: one gradient rail behind every dot, drawn once on the container so it
                // is continuous regardless of how the cards measure.
                .drawBehind {
                    val centre = (DotColumn / 2).toPx()
                    val width = 2.dp.toPx()
                    drawRect(
                        brush =
                            Brush.verticalGradient(
                                colors = listOf(colors.line, colors.accent),
                            ),
                        topLeft = Offset(centre - width / 2f, 0f),
                        size = Size(width, size.height),
                    )
                },
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            experience.forEach { job ->
                Reveal { TimelineRow(dotColor = colors.accent) { ExperienceCard(job) } }
            }
            Reveal {
                TimelineRow(dotColor = colors.accent2) {
                    CvCard(Modifier.fillMaxWidth(), glowOnHover = false) {
                        BasicText(text = education.degree, style = cvType.cardTitle)
                        Spacer(Modifier.height(6.dp))
                        BasicText(
                            text = education.school,
                            style = cvType.bodySmall.copy(color = colors.accent2),
                        )
                        Spacer(Modifier.height(8.dp))
                        MonoMeta(education.period)
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineRow(dotColor: Color, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .padding(top = 26.dp)
                .size(DotColumn)
                .glow(dotColor, radius = 14.dp, alpha = 0.8f)
                .background(dotColor, CircleShape),
        )
        Spacer(Modifier.width(DotGutter))
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun ExperienceCard(job: Experience) {
    val colors = cvColors
    CvCard(Modifier.fillMaxWidth()) {
        BasicText(text = job.role, style = cvType.cardTitle)
        Spacer(Modifier.height(4.dp))
        BasicText(
            text = job.company,
            style = cvType.cardTitle.copy(color = colors.accent, fontSize = cvType.body.fontSize),
        )
        Spacer(Modifier.height(8.dp))
        MonoMeta(job.period)

        Spacer(Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            job.points.take(4).forEach { Bullet(it.text, label = it.label) }
        }

        if (job.points.size > 4) {
            ExpanderSection("+ ${job.points.size - 4} more", Modifier.padding(top = 6.dp)) {
                job.points.drop(4).forEach { Bullet(it.text, label = it.label) }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Skills (#skills)
// ---------------------------------------------------------------------------------------------

/**
 * The WebGL `<SkillsOrbit />` is dropped. The flat cloud below it was already the accessible primary
 * on the source site, and the whole interaction is one nullable String: tap a group, everything that
 * isn't in it dims to 32%.
 */
@Composable
fun SkillsSection(modifier: Modifier = Modifier) {
    var selected by remember { mutableStateOf<String?>(null) }

    Column(modifier.fillMaxWidth()) {
        SectionHeader(
            eyebrow = "// tech stack",
            heading = "Skills",
            lede = "Filter by area, or read the whole cloud.",
        )

        Spacer(Modifier.height(24.dp))

        Reveal {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                skills.forEach { group ->
                    TagChip(
                        text = group.group,
                        selected = selected == group.group,
                        onClick = { selected = if (selected == group.group) null else group.group },
                    )
                }
            }
        }

        Spacer(Modifier.height(28.dp))

        Reveal(delayMillis = 100) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                skills.forEach { group ->
                    val dimmed = selected != null && selected != group.group
                    Column(Modifier.fillMaxWidth()) {
                        MonoMeta(group.group)
                        Spacer(Modifier.height(10.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            group.items.forEach { item ->
                                TagChip(
                                    text = item,
                                    modifier =
                                        Modifier.graphicsLayer { alpha = if (dimmed) 0.32f else 1f },
                                    selected = selected == group.group,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Explore (#explore)
// ---------------------------------------------------------------------------------------------

/**
 * The Playground teaser. Five of the six rooms are WebGL / Leaflet / tldraw surfaces with no CMP
 * equivalent, so they are labelled "web only" rather than wired to a button that does nothing —
 * a dead control is worse than an honest one.
 */
@Composable
fun ExploreSection(modifier: Modifier = Modifier) {
    val nav = LocalNav.current
    val colors = cvColors

    Column(modifier.fillMaxWidth()) {
        SectionHeader(
            eyebrow = "// the playground",
            heading = "This site is a live demo",
            lede = "Not a PDF with a pulse — a running program. The rooms below are the interactive " +
                "proofs of the engineering above.",
        )

        Spacer(Modifier.height(28.dp))

        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = if (maxWidth >= 760.dp) 2 else 1
            GridRows(siteRooms, columns) { room ->
                val wired = room.to == "/terminal"
                val onOpen: (() -> Unit)? = if (wired) ({ nav.go(Route.Terminal) }) else null
                CvCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onOpen,
                    glowOnHover = wired,
                ) {
                    BasicText(text = room.label, style = cvType.cardTitle)
                    Spacer(Modifier.height(10.dp))
                    BasicText(text = room.blurb, style = cvType.bodySmall)
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TagChip(room.tag)
                        Spacer(Modifier.width(10.dp))
                        BasicText(
                            text = if (wired) "open it" else "web only",
                            style =
                                cvType.metaMono.copy(
                                    color = if (wired) colors.accent else colors.muted,
                                ),
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Contact (#contact)
// ---------------------------------------------------------------------------------------------

// ponytail: LocalClipboardManager, not LocalClipboard. `Clipboard.setClipEntry` needs a ClipEntry,
// and there is no way to build one from a String in commonMain — every actual takes a platform type.
// `setText(AnnotatedString)` is the only common text-write path, so the deprecation is suppressed
// rather than worked around. Revisit when commonMain grows a String -> ClipEntry factory.
@Suppress("DEPRECATION", "DEPRECATION_ERROR")
@Composable
fun ContactSection(modifier: Modifier = Modifier) {
    val colors = cvColors
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current

    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (!copied) return@LaunchedEffect
        delay(2000)
        copied = false
    }

    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Reveal {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot()
                Spacer(Modifier.width(10.dp))
                BasicText(
                    text = profile.availability,
                    style = cvType.bodySmall.copy(color = colors.muted),
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Reveal(delayMillis = 80) {
            SectionHeading("Hiring for a senior Android role?")
        }

        Spacer(Modifier.height(14.dp))

        Reveal(delayMillis = 120) {
            BasicText(
                text = "Reach out directly — I reply fast.",
                modifier = Modifier.widthIn(max = 520.dp),
                style = cvType.body,
            )
        }

        Spacer(Modifier.height(28.dp))

        Reveal(delayMillis = 160) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AnimatedContent(targetState = copied, label = "copyEmail") { done ->
                    PrimaryButton(
                        text = if (done) "Copied" else "Copy email",
                        onClick = {
                            clipboard.setText(AnnotatedString(profile.email))
                            copied = true
                        },
                    )
                }
                GhostButton("GitHub", onClick = { uriHandler.openUri(profile.github) })
                GhostButton("LinkedIn", onClick = { uriHandler.openUri(profile.linkedin) })
            }
        }

        Spacer(Modifier.height(20.dp))

        MonoMeta(profile.email)
    }
}
