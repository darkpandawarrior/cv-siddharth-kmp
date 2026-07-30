package com.siddharth.cv.shared.detail

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.siddharth.cv.shared.LocalNav
import com.siddharth.cv.shared.data.Experience
import com.siddharth.cv.shared.data.competencies
import com.siddharth.cv.shared.data.education
import com.siddharth.cv.shared.data.experience
import com.siddharth.cv.shared.data.languages
import com.siddharth.cv.shared.data.metrics
import com.siddharth.cv.shared.data.openSource
import com.siddharth.cv.shared.data.profile
import com.siddharth.cv.shared.data.projects
import com.siddharth.cv.shared.data.resumeSkills
import com.siddharth.cv.shared.theme.CvResumeColors
import com.siddharth.cv.shared.theme.CvTheme
import com.siddharth.cv.shared.theme.GhostButton
import com.siddharth.cv.shared.theme.PrimaryButton
import com.siddharth.cv.shared.theme.TagChip
import com.siddharth.cv.shared.theme.cvColors
import com.siddharth.cv.shared.theme.cvType

/**
 * Port of cv-siddharth/src/ResumeView.tsx (+ the `/resume` route's `resume-mode` class).
 *
 * The dark-on-light flip is not a special case: it is the same nesting the project pages use, handed
 * [CvResumeColors] instead of a project palette. Everything below re-resolves through the shadowed
 * CompositionLocal — no `if (resumeMode)` branch exists anywhere.
 *
 * The print path does NOT print this canvas. `window.print()` against a single `<canvas>` gives the
 * browser's print engine nothing to paginate, so [printResume] hands it a real HTML document
 * instead — [buildResumeHtml] rebuilds the page below out of the same `data/` values, and the web
 * actual writes it into a hidden iframe with its own box tree. Selectable text, real page breaks,
 * Save-as-PDF.
 */
@Composable
fun ResumeScreen(modifier: Modifier = Modifier) {
    CvTheme(colors = CvResumeColors) {
        ResumeBody(modifier)
    }
}

/** A4-ish measure — `max-w-[210mm]`. */
private val PageWidth = 800.dp
private val PagePadding = 40.dp

@Composable
private fun ResumeBody(modifier: Modifier = Modifier) {
    val colors = cvColors
    val nav = LocalNav.current
    val uri = LocalUriHandler.current

    Box(
        // Opaque by design: AmbientBackground is still painted behind the nav host with the site's
        // dark palette, and a translucent résumé would let a green starfield through white paper.
        modifier = modifier.fillMaxSize().background(colors.ink).verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier.widthIn(max = PageWidth).fillMaxWidth().padding(vertical = 32.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GhostButton(text = "Back to portfolio", onClick = { nav.goSection("top") })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton(
                        text = "Open on the React site",
                        onClick = { uri.openUri(profile.portfolio.trimEnd('/') + "/resume") },
                    )
                    // Gated on the platform actually having a print path, rather than shown
                    // everywhere and silently doing nothing on three targets out of four. Android
                    // computes this at runtime (it needs an Application context to have been
                    // installed first), so this is a real read, not a compile-time constant.
                    // "Open on the React site" above is the always-works fallback either way.
                    if (resumePrintSupported) {
                        PrimaryButton(
                            text = "Print / Save as PDF",
                            onClick = { printResume(buildResumeHtml()) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Column(
                modifier = Modifier.fillMaxWidth().background(colors.card).padding(PagePadding),
            ) {
                Header()
                Section("PROFESSIONAL SUMMARY") {
                    BasicText(text = profile.summary, style = cvType.bodySmall.copy(color = colors.onBackground))
                }
                Section("CORE COMPETENCIES") { Competencies() }
                Section("KEY RESULTS") {
                    BasicText(
                        text = metrics.joinToString(" · ") { "${it.value} ${it.label}" },
                        style = cvType.bodySmall.copy(color = colors.onBackground),
                    )
                }
                Section("EXPERIENCE") {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        experience.forEach { Job(it) }
                    }
                }
                Section("PROJECTS & OPEN SOURCE") { ProjectsAndOpenSource() }
                Section("EDUCATION") {
                    LabeledRow(
                        left = "${education.degree} · ${education.school}",
                        right = education.period,
                    )
                }
                Section("TECHNICAL SKILLS") {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        resumeSkills.forEach { SkillLine(it.group, it.items.joinToString(", ")) }
                        SkillLine("Languages", languages.joinToString(", "))
                    }
                }

                Spacer(Modifier.height(28.dp))
                BasicText(
                    text =
                        "\"Print / Save as PDF\" does not print this canvas — a canvas has nothing " +
                            "for the print engine to paginate. It rebuilds the résumé above as an " +
                            "HTML document from the same data and prints that, so the PDF has " +
                            "selectable text and real page breaks.",
                    style = cvType.metaMono.copy(color = colors.muted),
                )
            }
        }
    }
}

// -------------------------------------------------------------------------------------------
// Header
// -------------------------------------------------------------------------------------------

@Composable
private fun Header() {
    val colors = cvColors
    Column(Modifier.fillMaxWidth()) {
        BasicText(text = profile.name, style = cvType.hero.copy(color = colors.onBackground))
        Spacer(Modifier.height(4.dp))
        BasicText(
            text = profile.resumeTitle,
            style = cvType.body.copy(color = colors.onBackground, fontWeight = FontWeight.Medium),
        )
        Spacer(Modifier.height(10.dp))
        BasicText(
            text =
                listOf(
                    profile.location,
                    profile.email,
                    profile.phone,
                    profile.github.removePrefix("https://"),
                    profile.linkedin.removePrefix("https://"),
                ).joinToString(" · "),
            style = cvType.metaMono.copy(color = colors.muted),
        )
        Spacer(Modifier.height(4.dp))
        BasicText(text = profile.availability, style = cvType.metaMono.copy(color = colors.muted))
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().height(2.dp).background(colors.onBackground))
    }
}

// -------------------------------------------------------------------------------------------
// Section chrome — deliberately NOT SectionEyebrow: the résumé's rule is a plain uppercase
// mono label, no accent LED and no glow. Same tokens, different rhythm.
// -------------------------------------------------------------------------------------------

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    val colors = cvColors
    Column(Modifier.fillMaxWidth().padding(top = 20.dp)) {
        BasicText(
            text = title,
            style = cvType.metaMono.copy(color = colors.muted, fontWeight = FontWeight.Bold),
        )
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Competencies() {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        competencies.forEach { TagChip(text = it) }
    }
}

// -------------------------------------------------------------------------------------------
// Experience
// -------------------------------------------------------------------------------------------

@Composable
private fun Job(job: Experience) {
    Column(Modifier.fillMaxWidth()) {
        LabeledRow(left = "${job.role} · ${job.company}", right = job.period)
        Spacer(Modifier.height(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            job.points.forEach { point ->
                Bullet(
                    buildAnnotatedString {
                        val label = point.label
                        if (label != null) {
                            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append("$label: ") }
                        }
                        append(point.text)
                    },
                )
            }
        }
    }
}

/** `flex items-baseline justify-between` — title left, period right, period never wraps. */
@Composable
private fun LabeledRow(left: String, right: String) {
    val colors = cvColors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        BasicText(
            text = left,
            modifier = Modifier.weight(1f),
            style = cvType.bodySmall.copy(color = colors.onBackground, fontWeight = FontWeight.Bold),
        )
        Spacer(Modifier.width(16.dp))
        BasicText(text = right, style = cvType.metaMono.copy(color = colors.muted))
    }
}

@Composable
private fun Bullet(text: AnnotatedString) {
    val colors = cvColors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .padding(top = 8.dp, start = 4.dp)
                .size(4.dp)
                .background(colors.muted, CircleShape),
        )
        Spacer(Modifier.width(10.dp))
        BasicText(text = text, style = cvType.bodySmall.copy(color = colors.onBackground))
    }
}

// -------------------------------------------------------------------------------------------
// Projects & open source
// -------------------------------------------------------------------------------------------

/** Strips the conventional-commit prefix, e.g. `feat(providers): ` — same regex as ResumeView.tsx. */
private val CommitPrefix = Regex("^(feat|fix)\\([^)]*\\): ")

@Composable
private fun ProjectsAndOpenSource() {
    val colors = cvColors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        projects.forEach { p ->
            Column(Modifier.fillMaxWidth()) {
                LabeledRow(left = p.name, right = p.stack.take(3).joinToString(" · "))
                BasicText(
                    text = "${p.tagline} ${p.highlights.firstOrNull().orEmpty()}".trim(),
                    style = cvType.bodySmall.copy(color = colors.onBackground),
                )
            }
        }
        // Rendered from the same openSource list as the homepage so this line can never drift
        // from the real merged-PR set.
        BasicText(
            text =
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                        append("Upstream contributions: ")
                    }
                    append(
                        "${openSource.size} merged PRs to career-ops (public OSS, 60k+ stars) — " +
                            openSource.joinToString("; ") { it.title.replace(CommitPrefix, "") } +
                            ".",
                    )
                },
            style = cvType.bodySmall.copy(color = colors.onBackground),
        )
    }
}

// -------------------------------------------------------------------------------------------
// Skills
// -------------------------------------------------------------------------------------------

@Composable
private fun SkillLine(group: String, items: String) {
    val colors = cvColors
    BasicText(
        text =
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append("$group: ") }
                append(items)
            },
        style = cvType.bodySmall.copy(color = colors.onBackground),
    )
}
