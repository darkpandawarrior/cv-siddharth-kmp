package com.siddharth.cv.shared.detail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.siddharth.cv.shared.LocalNav
import com.siddharth.cv.shared.CvNavState
import com.siddharth.cv.shared.Route
import com.siddharth.cv.shared.data.Diagram
import com.siddharth.cv.shared.data.LabeledValue
import com.siddharth.cv.shared.data.NamedLink
import com.siddharth.cv.shared.data.Project
import com.siddharth.cv.shared.data.ProjectRole
import com.siddharth.cv.shared.data.ProjectTarget
import com.siddharth.cv.shared.data.SkillGroup
import com.siddharth.cv.shared.data.isInternalLink
import com.siddharth.cv.shared.data.nextProject
import com.siddharth.cv.shared.data.profile
import com.siddharth.cv.shared.data.projectBySlug
import com.siddharth.cv.shared.theme.AnimatedCounter
import com.siddharth.cv.shared.theme.CvCard
import com.siddharth.cv.shared.theme.CvContentMaxWidth
import com.siddharth.cv.shared.theme.CvGutter
import com.siddharth.cv.shared.theme.CvSectionGap
import com.siddharth.cv.shared.theme.CvTheme
import com.siddharth.cv.shared.theme.ExpanderSection
import com.siddharth.cv.shared.theme.GhostButton
import com.siddharth.cv.shared.theme.MetricGauge
import com.siddharth.cv.shared.theme.MonoMeta
import com.siddharth.cv.shared.theme.PrimaryButton
import com.siddharth.cv.shared.theme.Reveal
import com.siddharth.cv.shared.theme.SectionEyebrow
import com.siddharth.cv.shared.theme.SectionHeading
import com.siddharth.cv.shared.theme.Sparkline
import com.siddharth.cv.shared.theme.TagChip
import com.siddharth.cv.shared.theme.cvColor
import com.siddharth.cv.shared.theme.cvColors
import com.siddharth.cv.shared.theme.cvType
import com.siddharth.cv.shared.theme.projectColors
import com.siddharth.cv.shared.theme.rememberInfiniteFloat
import com.siddharth.cv.shared.theme.tiltOnHover
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Port of cv-siddharth/src/ProjectDetail.tsx.
 *
 * The whole body is wrapped in `CvTheme(projectColors(project.theme))` — that single nesting *is*
 * the per-project theming mechanism, the exact analogue of setting `--color-accent` &co. on
 * `<main class="project-detail">`. Nothing below hand-threads a colour; every `cvColors.accent` read
 * re-resolves through the shadowed CompositionLocal.
 *
 * Dropped from the web version and deliberately not stubbed: the screenshot gallery + lightbox, the
 * device wall, autoplay video, the showcase film, the share sheet, the Lab Bench deep-link and the
 * "Ask my AI" chat trigger. Each needs either bitmaps (a network round trip per image on wasmJs) or
 * a backend, and none is load-bearing for the page's argument.
 */
@Composable
fun ProjectDetailScreen(slug: String, modifier: Modifier = Modifier) {
    val project = projectBySlug(slug)
    if (project == null) {
        // Also the app's 404 surface — Route.ProjectDetail is the only route carrying free text.
        NoCarrier(slug, modifier)
        return
    }
    CvTheme(colors = projectColors(project.theme)) {
        ProjectBody(project, modifier)
    }
}

// -------------------------------------------------------------------------------------------
// 404
// -------------------------------------------------------------------------------------------

@Composable
private fun NoCarrier(slug: String, modifier: Modifier = Modifier) {
    val colors = cvColors
    val nav = LocalNav.current
    Box(
        modifier.fillMaxSize().background(colors.ink),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 520.dp).padding(CvGutter),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicText(
                text = "NO CARRIER",
                style = cvType.mono.copy(color = colors.accent, fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.height(12.dp))
            BasicText(text = "unknown project: $slug", style = cvType.mono.copy(color = colors.muted))
            Spacer(Modifier.height(8.dp))
            BasicText(
                text = "That route doesn't resolve to anything in the build.",
                style = cvType.bodySmall,
            )
            Spacer(Modifier.height(24.dp))
            GhostButton(text = "Back to all projects", onClick = { nav.goSection("projects") })
        }
    }
}

// -------------------------------------------------------------------------------------------
// Body
// -------------------------------------------------------------------------------------------

/** `max-w-5xl mx-auto px-6` — the measure every section on this page shares. */
private fun Modifier.pageMeasure(): Modifier =
    this.widthIn(max = CvContentMaxWidth).fillMaxWidth().padding(horizontal = CvGutter)

@Composable
private fun ProjectBody(project: Project, modifier: Modifier = Modifier) {
    val colors = cvColors
    val nav = LocalNav.current
    val uri = LocalUriHandler.current
    // Flattened up front rather than smart-cast inside eight `item { }` lambdas — one null check,
    // and every lambda below closes over a plain non-null list.
    val detail = project.detail
    val overview = detail?.overview.orEmpty()
    val bandMetrics = detail?.metrics.orEmpty()
    val sections = detail?.sections.orEmpty()
    val techStack = detail?.techStack.orEmpty()
    val roles = detail?.roles.orEmpty()
    val diagrams = detail?.diagrams.orEmpty()
    val extraLinks = detail?.extraLinks.orEmpty()

    LazyColumn(
        modifier = modifier.fillMaxSize().background(colors.ink),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item { ProjectHero(project, nav, uri) }

        if (bandMetrics.isNotEmpty()) {
            item { MetricBand(bandMetrics) }
        }

        if (overview.isNotBlank()) {
            item {
                Reveal {
                    Column(Modifier.pageMeasure().padding(top = CvSectionGap / 2)) {
                        BasicText(text = overview, style = cvType.body)
                    }
                }
            }
        }

        if (sections.isNotEmpty()) {
            item {
                Section(eyebrow = "// deep dive", title = "How it works") {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        sections.forEach { s ->
                            CvCard(
                                modifier = Modifier.fillMaxWidth().tiltOnHover(4f, spotlight = colors.accent),
                            ) {
                                BasicText(
                                    text = s.heading,
                                    style = cvType.cardTitle.copy(color = colors.accent),
                                )
                                Spacer(Modifier.height(8.dp))
                                BasicText(text = s.body, style = cvType.bodySmall)
                            }
                        }
                    }
                }
            }
        }

        if (project.targets.isNotEmpty()) {
            item {
                Section(eyebrow = "// multiplatform", title = "One codebase, every surface") {
                    BasicText(
                        text = "The real surfaces per platform, from one shared source set — not a mockup.",
                        style = cvType.bodySmall,
                    )
                    Spacer(Modifier.height(20.dp))
                    TargetGrid(project.targets)
                }
            }
        }

        if (techStack.isNotEmpty()) {
            item {
                Section(eyebrow = "// under the hood", title = "Tech stack") {
                    TechStack(techStack)
                }
            }
        }

        if (roles.isNotEmpty()) {
            item {
                Section(eyebrow = "// cast", title = "The roster") {
                    RoleGrid(roles)
                }
            }
        }

        if (diagrams.isNotEmpty()) {
            item {
                Section(eyebrow = "// architecture", title = "How it's built") {
                    BasicText(
                        text =
                            "Mermaid source, unrendered — there is no Kotlin Mermaid renderer, so v1 " +
                                "shows the diagram exactly as it is authored rather than faking a picture of it.",
                        style = cvType.bodySmall,
                    )
                    Spacer(Modifier.height(16.dp))
                    DiagramList(diagrams)
                }
            }
        }

        if (project.highlights.isNotEmpty()) {
            item {
                Section(eyebrow = "// receipts", title = "Highlights") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        project.highlights.forEach { Bullet(it) }
                    }
                }
            }
        }

        if (extraLinks.isNotEmpty()) {
            item {
                Section(eyebrow = "// explore more", title = "Go deeper") {
                    ExtraLinks(extraLinks, nav, uri)
                }
            }
        }

        item { NextBuild(project.slug, nav) }

        item {
            Column(
                modifier = Modifier.pageMeasure().padding(vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                GhostButton(text = "Back to all projects", onClick = { nav.goSection("projects") })
            }
        }
    }
}

/** `SectionHeader` + `section-y mx-auto max-w-5xl px-6` in one place. */
@Composable
private fun Section(
    eyebrow: String,
    title: String,
    content: @Composable () -> Unit,
) {
    Reveal {
        Column(Modifier.pageMeasure().padding(top = CvSectionGap)) {
            SectionEyebrow(eyebrow)
            Spacer(Modifier.height(10.dp))
            SectionHeading(title)
            Spacer(Modifier.height(24.dp))
            content()
        }
    }
}

// -------------------------------------------------------------------------------------------
// 1 — Hero
// -------------------------------------------------------------------------------------------

@Composable
private fun ProjectHero(project: Project, nav: CvNavState, uri: UriHandler) {
    val colors = cvColors
    // `.aurora` — two slow radial washes orbiting the hero. One loop drives both so they never
    // drift out of phase, and rememberInfiniteFloat pins it under reduced motion for free.
    val drift by rememberInfiniteFloat(16000)

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .drawBehind {
                    val t = drift * 2f * PI.toFloat()
                    val reach = maxOf(size.width, size.height) * 0.9f
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(colors.accent.copy(alpha = 0.20f), Color.Transparent),
                            center = Offset(
                                size.width * (0.18f + 0.08f * cos(t)),
                                size.height * (0.08f + 0.10f * sin(t)),
                            ),
                            radius = reach,
                        ),
                    )
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(colors.accentDim.copy(alpha = 0.16f), Color.Transparent),
                            center = Offset(
                                size.width * (0.84f - 0.08f * sin(t)),
                                size.height * (0.16f + 0.08f * cos(t)),
                            ),
                            radius = reach,
                        ),
                    )
                    // `bg-gradient-to-b from-transparent to-ink` — the wash dissolves into the page.
                    drawRect(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, colors.ink),
                        ),
                    )
                },
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(Modifier.pageMeasure().padding(top = 32.dp, bottom = 48.dp)) {
            GhostButton(text = "All projects", onClick = { nav.goSection("projects") })
            Spacer(Modifier.height(28.dp))
            SectionEyebrow("// project")
            Spacer(Modifier.height(10.dp))
            BasicText(text = project.name, style = cvType.hero)
            Spacer(Modifier.height(12.dp))
            // `.sheen` — the 3px accent rule under the title.
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
            BasicText(
                text = project.tagline,
                style = cvType.body.copy(color = colors.accent, fontSize = cvType.body.fontSize * 1.1f),
            )
            Spacer(Modifier.height(16.dp))
            BasicText(
                text = project.detail?.overview?.takeIf { it.isNotBlank() } ?: project.description,
                style = cvType.body,
            )

            if (project.badges.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                ChipRow(project.badges, selected = true)
            }
            if (project.stack.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                ChipRow(project.stack, selected = false)
            }

            if (project.links.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                LinkRow(project, nav, uri)
            }

            Spacer(Modifier.height(18.dp))
            MonoMeta(project.status)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(items: List<String>, selected: Boolean) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { TagChip(text = it, selected = selected) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LinkRow(project: Project, nav: CvNavState, uri: UriHandler) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        project.links.forEach { link ->
            PrimaryButton(text = link.label, onClick = { openLink(link.url, nav, uri) })
        }
    }
}

/**
 * The one place a URL turns into an action. Every hash the data carries is an in-app destination —
 * handing one to a URL opener is the bug [isInternalLink] exists to prevent — and the handful of
 * site routes this port doesn't ship (/lab, /map, /compose …) resolve against the live React site
 * rather than silently doing nothing.
 */
private fun openLink(url: String, nav: CvNavState, uri: UriHandler) {
    when {
        url.startsWith("#project/") -> nav.go(Route.ProjectDetail(url.removePrefix("#project/")))
        url == "#resume" || url == "/resume" -> nav.go(Route.Resume)
        url == "#terminal" || url == "/terminal" -> nav.go(Route.Terminal)
        url.startsWith("#") -> nav.goSection(url.removePrefix("#"))
        isInternalLink(url) -> uri.openUri(profile.portfolio.trimEnd('/') + url)
        else -> uri.openUri(url)
    }
}

// -------------------------------------------------------------------------------------------
// 2 — Metrics band
// -------------------------------------------------------------------------------------------

/** `"45k"` -> `45 to "k"`, `"2,026"` -> `2026 to ""`. Null when the value isn't numeric-led. */
private fun parseMetric(raw: String): Pair<Int, String>? {
    val trimmed = raw.trim().replace(",", "")
    val digits = trimmed.takeWhile { it.isDigit() }
    if (digits.isEmpty()) return null
    return digits.toInt() to trimmed.drop(digits.length)
}

/** The fixed ascending spark from AnimatedMetric.tsx (`M2,20 L14,16 … L62,4`), y-flipped. */
private val SparkPoints = listOf(4f, 8f, 6f, 14f, 12f, 20f)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetricBand(metrics: List<LabeledValue>) {
    val colors = cvColors
    Box(
        Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        FlowRow(
            modifier = Modifier.pageMeasure(),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            metrics.forEach { m ->
                Row(
                    modifier = Modifier.widthIn(min = 200.dp, max = 320.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        val parsed = parseMetric(m.value)
                        if (parsed == null) {
                            BasicText(text = m.value, style = cvType.metric)
                        } else {
                            AnimatedCounter(target = parsed.first, suffix = parsed.second)
                        }
                        Spacer(Modifier.height(6.dp))
                        BasicText(text = m.label, style = cvType.bodySmall)
                    }
                    Spacer(Modifier.width(12.dp))
                    // Same rule as the web: a percentage gets the gauge, everything else the spark.
                    val pct = parseMetric(m.value)?.takeIf { it.second.trimStart().startsWith("%") }
                    if (pct != null) {
                        MetricGauge(progress = (pct.first.coerceAtMost(100)) / 100f, modifier = Modifier.size(56.dp))
                    } else {
                        Sparkline(points = SparkPoints, modifier = Modifier.width(64.dp))
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------------
// 5 — Targets
// -------------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TargetGrid(targets: List<ProjectTarget>) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        targets.forEach { t ->
            Column(Modifier.widthIn(min = 220.dp, max = 300.dp)) {
                TagChip(
                    text = "${t.platform} · ${t.screenCount} screens",
                    selected = true,
                )
                val note = t.note
                if (note != null) {
                    Spacer(Modifier.height(8.dp))
                    BasicText(text = note, style = cvType.bodySmall)
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------------
// 6 — Tech stack
// -------------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TechStack(groups: List<SkillGroup>) {
    val colors = cvColors
    CvCard(modifier = Modifier.fillMaxWidth(), glowOnHover = false) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            groups.forEach { g ->
                Column(Modifier.widthIn(min = 240.dp, max = 320.dp)) {
                    BasicText(
                        text = g.group,
                        style = cvType.bodySmall.copy(color = colors.accent, fontWeight = FontWeight.SemiBold),
                    )
                    Spacer(Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        g.items.forEach { TagChip(text = it) }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------------
// 7 — Roles (Kursi only)
// -------------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoleGrid(roles: List<ProjectRole>) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        roles.forEach { r ->
            CvCard(modifier = Modifier.widthIn(min = 260.dp, max = 320.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        Modifier
                            .padding(top = 4.dp)
                            .size(14.dp)
                            .background(cvColor(r.color), CircleShape),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        BasicText(
                            text = r.name,
                            style = cvType.cardTitle.copy(fontSize = cvType.body.fontSize),
                        )
                        Spacer(Modifier.height(4.dp))
                        BasicText(text = r.power, style = cvType.bodySmall)
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------------
// 8 — Diagrams (raw Mermaid source)
// -------------------------------------------------------------------------------------------

@Composable
private fun DiagramList(diagrams: List<Diagram>) {
    val colors = cvColors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        diagrams.forEach { d ->
            ExpanderSection(title = d.title, modifier = Modifier.fillMaxWidth()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(colors.card, RoundedCornerShape(12.dp))
                        .padding(16.dp)
                        .horizontalScroll(rememberScrollState()),
                ) {
                    BasicText(text = d.code, style = cvType.mono, softWrap = false)
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------------
// 9 — Highlights
// -------------------------------------------------------------------------------------------

@Composable
private fun Bullet(text: String) {
    val colors = cvColors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .padding(top = 9.dp)
                .size(5.dp)
                .background(colors.accent.copy(alpha = 0.7f), CircleShape),
        )
        Spacer(Modifier.width(12.dp))
        BasicText(text = text, style = cvType.body)
    }
}

// -------------------------------------------------------------------------------------------
// 10 — Extra links
// -------------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExtraLinks(links: List<NamedLink>, nav: CvNavState, uri: UriHandler) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        links.forEach { l ->
            GhostButton(text = l.label, onClick = { openLink(l.url, nav, uri) })
        }
        GhostButton(text = "Résumé", onClick = { nav.go(Route.Resume) })
    }
}

// -------------------------------------------------------------------------------------------
// 11 — Pager
// -------------------------------------------------------------------------------------------

@Composable
private fun NextBuild(slug: String, nav: CvNavState) {
    val next = nextProject(slug) ?: return
    Reveal {
        Column(Modifier.pageMeasure().padding(top = CvSectionGap)) {
            CvCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { nav.go(Route.ProjectDetail(next.slug)) },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        MonoMeta("NEXT BUILD")
                        Spacer(Modifier.height(6.dp))
                        BasicText(text = next.name, style = cvType.cardTitle)
                        Spacer(Modifier.height(4.dp))
                        BasicText(text = next.tagline, style = cvType.bodySmall)
                    }
                    Spacer(Modifier.width(16.dp))
                    Arrow(Modifier.size(18.dp))
                }
            }
        }
    }
}

/**
 * ponytail: the "→" is drawn, not typed. There is no system font on the wasm canvas and CvTheme
 * currently resolves to a fallback face, so an unvendored arrow glyph is a tofu risk — the same
 * reason ExpanderSection draws its chevron.
 */
@Composable
private fun Arrow(modifier: Modifier = Modifier) {
    val colors = cvColors
    Canvas(modifier) {
        val midY = size.height / 2f
        val head = size.height * 0.32f
        drawLine(
            color = colors.accent,
            start = Offset(0f, midY),
            end = Offset(size.width, midY),
            strokeWidth = 2f,
        )
        val p = Path()
        p.moveTo(size.width - head, midY - head)
        p.lineTo(size.width, midY)
        p.lineTo(size.width - head, midY + head)
        p.close()
        drawPath(p, colors.accent)
    }
}
