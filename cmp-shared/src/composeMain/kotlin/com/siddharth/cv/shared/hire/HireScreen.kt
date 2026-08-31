@file:OptIn(ExperimentalLayoutApi::class)

package com.siddharth.cv.shared.hire

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.siddharth.cv.shared.CvNavState
import com.siddharth.cv.shared.LocalNav
import com.siddharth.cv.shared.Route
import com.siddharth.cv.shared.data.CaseStudy
import com.siddharth.cv.shared.data.Metric
import com.siddharth.cv.shared.data.caseStudies
import com.siddharth.cv.shared.data.metrics
import com.siddharth.cv.shared.data.profile
import com.siddharth.cv.shared.data.projectBySlug
import com.siddharth.cv.shared.theme.CvCard
import com.siddharth.cv.shared.theme.CvContentMaxWidth
import com.siddharth.cv.shared.theme.CvGutter
import com.siddharth.cv.shared.theme.CvSectionGap
import com.siddharth.cv.shared.theme.GhostButton
import com.siddharth.cv.shared.theme.MonoMeta
import com.siddharth.cv.shared.theme.PrimaryButton
import com.siddharth.cv.shared.theme.Reveal
import com.siddharth.cv.shared.theme.SectionEyebrow
import com.siddharth.cv.shared.theme.StatusDot
import com.siddharth.cv.shared.theme.cvColors
import com.siddharth.cv.shared.theme.cvType

/**
 * Port of cv-siddharth/src/routes/hire.tsx, the ninety-second surface and the only page here
 * built for someone who does not want to explore.
 *
 * The React file holds itself to four rules and they survive the port intact: everything that
 * matters is above the fold, nothing paints on a canvas or waits on a scroll, one idea per row with
 * the biggest number first, and every claim links to the thing that proves it.
 *
 * NOTHING ON THIS PAGE IS TYPED TWICE. The lead paragraph is `profile.intro` rather than a second
 * hand-written copy of the same three numbers. hire.tsx types its own and says in a comment that
 * the real fix is one source interpolated everywhere. This file is downstream of exactly the drift
 * that comment predicts, so it reads the audited string instead of restating it. Same for the
 * metrics, the case studies and the availability line.
 *
 * Degraded on purpose, one line each:
 *  - The inline "That half is here too" link becomes a button under the sentence. There is no
 *    inline-link primitive in this port and inventing one for a single sentence is a fork.
 *  - /ink is not a route in this build, so that button opens the React site rather than dead-ending.
 */
@Composable
fun HireScreen(modifier: Modifier = Modifier) {
    val nav = LocalNav.current
    val uri = LocalUriHandler.current
    // The three that answer "can he own a platform", in the order a hiring manager asks them:
    // scale, then a hard technical win, then reliability. hire.tsx slices the same three.
    val headline = metrics.take(HEADLINE_COUNT)
    val featured = caseStudies.take(HEADLINE_COUNT)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item { HireHero(headline, nav, uri) }
        item { WorkBehindTheNumbers(featured, nav) }
        item { TheOtherHalf(uri) }
        item { HireFooter(nav, uri) }
    }
}

/** `mx-auto max-w-4xl px-6`, the measure every block on this page shares. */
private fun Modifier.pageMeasure(): Modifier =
    this.widthIn(max = CvContentMaxWidth).fillMaxWidth().padding(horizontal = CvGutter)

private const val HEADLINE_COUNT = 3

// -------------------------------------------------------------------------------------------
// 1. Name, numbers, and the two things to do about them
// -------------------------------------------------------------------------------------------

@Composable
private fun HireHero(
    headline: List<Metric>,
    nav: CvNavState,
    uri: UriHandler,
) {
    val colors = cvColors
    Reveal {
        Column(Modifier.pageMeasure().padding(top = 32.dp)) {
            // The role sits in the kicker, not in the heading. hire.tsx moved it here so the
            // largest line on the page holds one idea, and that decision ports as-is.
            MonoMeta("${profile.title} · ${profile.location} · open to remote")
            Spacer(Modifier.height(12.dp))
            BasicText(text = profile.name, style = cvType.hero)
            Spacer(Modifier.height(16.dp))
            BasicText(
                text = profile.intro,
                modifier = Modifier.widthIn(max = 680.dp),
                style = cvType.body,
            )

            Spacer(Modifier.height(40.dp))
            MetricRow(headline)

            Spacer(Modifier.height(32.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PrimaryButton(
                    text = profile.email,
                    onClick = { uri.openUri("mailto:${profile.email}") },
                )
                GhostButton(text = "Résumé", onClick = { nav.go(Route.Resume) })
            }

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot()
                Spacer(Modifier.width(10.dp))
                BasicText(
                    text = profile.availability,
                    style = cvType.bodySmall.copy(color = colors.muted),
                )
            }
        }
    }
}

/**
 * The numbers, before anything else asks for attention.
 *
 * `metrics` values are audited claim strings, rendered verbatim, never reformatted and never
 * counted up from a parsed integer, which is the one way a number on this page could drift.
 */
@Composable
private fun MetricRow(headline: List<Metric>) {
    val colors = cvColors
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        headline.forEach { m ->
            CvCard(
                modifier = Modifier.widthIn(min = 220.dp, max = 320.dp),
                glowOnHover = false,
            ) {
                BasicText(text = m.value, style = cvType.metric.copy(color = colors.accent))
                Spacer(Modifier.height(6.dp))
                BasicText(
                    text = m.label,
                    style = cvType.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                )
                Spacer(Modifier.height(6.dp))
                BasicText(text = m.detail, style = cvType.metaMono)
            }
        }
    }
}

// -------------------------------------------------------------------------------------------
// 2. Three case studies, one line each
// -------------------------------------------------------------------------------------------

@Composable
private fun WorkBehindTheNumbers(featured: List<CaseStudy>, nav: CvNavState) {
    Reveal {
        Column(Modifier.pageMeasure().padding(top = CvSectionGap)) {
            SectionEyebrow("// the work behind the numbers")
            Spacer(Modifier.height(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                featured.forEach { study -> CaseStudyRow(study, nav) }
            }
        }
    }
}

@Composable
private fun CaseStudyRow(study: CaseStudy, nav: CvNavState) {
    val colors = cvColors
    // A case study is not always a project. `mileway` is both and has a detail page; `gps-accuracy`
    // and `crash-reduction` exist only in `caseStudies`, and routing them to ProjectDetail would
    // send two of the three links on the page a recruiter is handed to the 404 screen. The
    // homepage's Work section renders every case study, so that fallback can never miss.
    val hasDetail = projectBySlug(study.slug) != null
    CvCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            if (hasDetail) nav.go(Route.ProjectDetail(study.slug)) else nav.goSection("work")
        },
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // CaseStudy.title is the full descriptive line ("Mileway: offline-first mileage tracker
            // (Android, iOS, ...)"). On a scan-in-ninety-seconds page that is a paragraph, so the
            // name leads and the metric carries the proof.
            BasicText(
                text = study.title.substringBefore(": "),
                modifier = Modifier.weight(1f),
                style = cvType.cardTitle.copy(fontSize = cvType.body.fontSize),
            )
            Spacer(Modifier.width(16.dp))
            BasicText(text = study.metric, style = cvType.metaMono.copy(color = colors.accent))
        }
    }
}

// -------------------------------------------------------------------------------------------
// 3. The fact, and only the fact
// -------------------------------------------------------------------------------------------

/**
 * The closing paragraph states the fact and stops. It used to continue "I do the same thing to
 * sensor data now", which draws the line between the editing years and the engineering for the
 * reader, the one thing this site must never do in copy. The link does that work instead.
 */
@Composable
private fun TheOtherHalf(uri: UriHandler) {
    val colors = cvColors
    Reveal {
        Column(Modifier.pageMeasure().padding(top = CvSectionGap)) {
            Row(Modifier.height(IntrinsicSize.Min)) {
                Box(
                    Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(colors.accent.copy(alpha = 0.4f), RoundedCornerShape(1.dp)),
                )
                Spacer(Modifier.width(16.dp))
                BasicText(
                    text =
                        "Before I wrote software I spent three years editing a college magazine, " +
                            "finding what was wrong in other people's drafts.",
                    modifier = Modifier.widthIn(max = 640.dp),
                    style = cvType.body,
                )
            }
            Spacer(Modifier.height(16.dp))
            GhostButton(
                text = "That half is here too",
                onClick = { uri.openUri(siteUrl("/ink")) },
            )
        }
    }
}

// -------------------------------------------------------------------------------------------
// 4. Everything else
// -------------------------------------------------------------------------------------------

@Composable
private fun HireFooter(nav: CvNavState, uri: UriHandler) {
    Column(
        modifier = Modifier.pageMeasure().padding(top = CvSectionGap, bottom = 120.dp),
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GhostButton(text = "Full portfolio", onClick = { nav.go(Route.Home) })
            GhostButton(text = "GitHub", onClick = { uri.openUri(profile.github) })
            GhostButton(text = "LinkedIn", onClick = { uri.openUri(profile.linkedin) })
        }
    }
}

/** A path on the React site. Used only for surfaces this build does not ship. */
private fun siteUrl(path: String): String = profile.portfolio.trimEnd('/') + path
