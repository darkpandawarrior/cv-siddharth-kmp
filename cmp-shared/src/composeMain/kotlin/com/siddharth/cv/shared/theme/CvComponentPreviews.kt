// ─────────────────────────────────────────────────────────────────────────────
// CvComponentPreviews.kt — the whole of CvComponents.kt, one preview per component.
//
// WHY THIS FILE EXISTS AND WHY IT COVERS EXACTLY ONE FILE
//   This module holds ~348 composables. Almost all of them are private rows and sections inside
//   one screen, reachable only with a loaded CvProfile/CvProject and meaningful only in place.
//   CvComponents.kt is the exception: sixteen public, self-contained, data-free design-system
//   parts that every screen is assembled from. Those are the ones worth looking at in isolation,
//   so those are the ones previewed. Chasing a coverage percentage across the other 332 would buy
//   previews nobody opens.
//
// WHICH @Preview ANNOTATION
//   androidx.compose.ui.tooling.preview.Preview — from org.jetbrains.compose.ui:ui-tooling-preview.
//   Since Compose Multiplatform 1.10 the AndroidX annotation IS the multiplatform one; the
//   org.jetbrains.compose.ui.tooling.preview.Preview / components-ui-tooling-preview pair is the
//   deprecated path, and every pre-2026 answer on the subject has it backwards. Verified against
//   the published artifact: ui-tooling-preview-1.13.0-alpha01-sources.jar carries
//   commonMain/androidx/compose/ui/tooling/preview/Preview.kt. No expect/actual shim, no
//   androidMain-only preview file — this compiles for android, jvm, iosArm64, iosSimulatorArm64
//   and wasmJs, the exact target set composeMain declares.
//
// WHAT A PREVIEW HERE DOES AND DOES NOT PROVE
//   The IDE draws common previews with the ANDROID renderer, so these show Android metrics for
//   shared code. Layout, spacing, state permutations and colour are platform-independent and this
//   catches all of them; pixel-level iOS/wasm fidelity it cannot. For that, run the app.
// ─────────────────────────────────────────────────────────────────────────────
package com.siddharth.cv.shared.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Every preview below renders through the real [CvTheme], because the components read
 * `cvColors`/`cvType` from it and [LocalCvType] throws without it.
 *
 * `reducedMotion = true` is the load-bearing argument, not a nicety. It is what makes a preview a
 * still image of the FINAL state instead of frame zero: [rememberInfiniteFloat] returns its `from`
 * value, and [AnimatedCounter], [MetricGauge] and [Sparkline] tween over 0ms straight to target.
 * Drop it and half this file previews as an empty gauge and a counter reading 0.
 */
@Composable
private fun PreviewShell(
    colors: CvColors = CvDarkColors,
    content: @Composable ColumnScope.() -> Unit,
) {
    CvTheme(colors = colors, reducedMotion = true) {
        Column(
            modifier = Modifier.fillMaxWidth().background(colors.ink).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

// ── Ground ───────────────────────────────────────────────────────────────────

/** The starfield is seeded (Random(7)), so this preview is byte-identical on every render. */
@Preview(widthDp = 320, heightDp = 200)
@Composable
private fun AmbientBackgroundPreview() {
    CvTheme(reducedMotion = true) { Box(Modifier.fillMaxSize()) { AmbientBackground() } }
}

// ── Section chrome ───────────────────────────────────────────────────────────

@Preview(widthDp = 360)
@Composable
private fun SectionChromePreview() {
    PreviewShell {
        SectionEyebrow("// featured work")
        SectionHeading("Selected work")
        CircuitDivider()
        MonoMeta("Kotlin · Compose Multiplatform · Ktor")
    }
}

// ── Surfaces ─────────────────────────────────────────────────────────────────

/**
 * Two cards, because the difference is the point: the elevated one lifts on hover/focus, the
 * `glowOnHover = false` one is the static variant used behind non-interactive content.
 */
@Preview(widthDp = 360)
@Composable
private fun CvCardPreview() {
    PreviewShell {
        CvCard(onClick = {}) {
            SectionHeading("Gaddi")
            MonoMeta("A deterministic bluffing engine with an AI narrator.")
        }
        CvCard(glowOnHover = false) {
            MonoMeta("Static surface — no hover lift.")
        }
    }
}

/** The resume palette is a real second theme (CvResumeColors), so it gets its own render. */
@Preview(widthDp = 360)
@Composable
private fun CvCardResumePalettePreview() {
    PreviewShell(colors = CvResumeColors) {
        CvCard(glowOnHover = false) {
            SectionHeading("Experience")
            MonoMeta("Print-leaning palette — same component, reskinned by CvTheme.")
        }
    }
}

@Preview(widthDp = 360)
@Composable
private fun MediaPanelPreview() {
    PreviewShell {
        // The wash angle is derived from the seed's hashCode, so two seeds must look different or
        // every project tile on the site would read the same.
        MediaPanel(seed = "gaddi", label = "01")
        MediaPanel(seed = "kmp-toolkit", label = "02")
    }
}

// ── Chips and text ───────────────────────────────────────────────────────────

@Preview(widthDp = 360)
@Composable
private fun TagChipPreview() {
    PreviewShell {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TagChip("Kotlin")
            TagChip("Compose", selected = true)
            TagChip("Ktor", onClick = {})
        }
    }
}

@Preview(widthDp = 360)
@Composable
private fun HeroShimmerTextPreview() {
    // Under reduced motion the sweep is pinned at t = 0, which is the frame the gradient was
    // designed against — the accent → mint → cyan stops read left to right.
    PreviewShell { HeroShimmerText("Siddharth Pandalai") }
}

// ── Buttons ──────────────────────────────────────────────────────────────────

@Preview(widthDp = 360)
@Composable
private fun ButtonsPreview() {
    PreviewShell {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PrimaryButton("Hire me", onClick = {})
            GhostButton("Read the CV", onClick = {})
        }
    }
}

// ── Metrics ──────────────────────────────────────────────────────────────────

@Preview(widthDp = 360)
@Composable
private fun MetricsPreview() {
    PreviewShell {
        AnimatedCounter(target = 42, suffix = "%")
        MetricGauge(progress = 0.72f)
        Sparkline(points = listOf(3f, 5f, 4f, 8f, 7f, 11f, 9f, 14f))
    }
}

/** The clamp at both ends is worth seeing: 0f is an empty track, 1f a full 270° sweep. */
@Preview(widthDp = 360)
@Composable
private fun MetricGaugeRangePreview() {
    PreviewShell {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricGauge(progress = 0f)
            MetricGauge(progress = 0.5f)
            MetricGauge(progress = 1f)
        }
    }
}

/** A flat series is the edge case: every point equal means span == 0 and a divide-by-zero line. */
@Preview(widthDp = 360)
@Composable
private fun SparklineFlatPreview() {
    PreviewShell {
        Sparkline(points = listOf(5f, 5f, 5f, 5f))
        Box(Modifier.height(4.dp))
        Sparkline(points = listOf(1f, 9f))
    }
}

// ── Small parts ──────────────────────────────────────────────────────────────

@Preview(widthDp = 360)
@Composable
private fun StatusDotPreview() {
    PreviewShell {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatusDot()
            MonoMeta("Open to Lead / Principal Android roles")
        }
    }
}

/**
 * The expander previews CLOSED: `open` is remembered internal state with no parameter, so this is
 * the only state a preview can show. Its open state is an app-level interaction, not a component
 * variant — worth knowing before someone "fixes" this preview.
 */
@Preview(widthDp = 360)
@Composable
private fun ExpanderSectionPreview() {
    PreviewShell {
        ExpanderSection(title = "What this port cost") {
            MonoMeta("Body content renders once the header is tapped.")
        }
    }
}

// ── Typography ───────────────────────────────────────────────────────────────

/**
 * The type ramp on one sheet. Fonts come from Compose Resources (Space Grotesk, DM Mono); if the
 * preview panel cannot resolve them it falls back to the platform default and only the WEIGHTS and
 * sizes are trustworthy here — the letterforms are not.
 */
@Preview(widthDp = 360, heightDp = 420)
@Composable
private fun TypeRampPreview() {
    PreviewShell {
        SectionEyebrow("// type")
        HeroShimmerText("Hero")
        SectionHeading("Heading")
        AnimatedCounter(target = 128, style = cvType.metric)
        MonoMeta("MONO META — 11px, muted")
        CvCard(glowOnHover = false) {
            SectionHeading("Card title")
            MonoMeta("Body copy sits under it at the same measure.")
        }
        PrimaryButton("SemiBold label", onClick = {})
    }
}
