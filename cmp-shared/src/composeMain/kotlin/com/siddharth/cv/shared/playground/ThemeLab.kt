@file:OptIn(ExperimentalLayoutApi::class)

package com.siddharth.cv.shared.playground

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.siddharth.cv.shared.data.projects
import com.siddharth.cv.shared.theme.AnimatedCounter
import com.siddharth.cv.shared.theme.CvCard
import com.siddharth.cv.shared.theme.CvColors
import com.siddharth.cv.shared.theme.CvDarkColors
import com.siddharth.cv.shared.theme.CvMotion
import com.siddharth.cv.shared.theme.CvTheme
import com.siddharth.cv.shared.theme.GhostButton
import com.siddharth.cv.shared.theme.LocalReducedMotion
import com.siddharth.cv.shared.theme.MetricGauge
import com.siddharth.cv.shared.theme.MonoMeta
import com.siddharth.cv.shared.theme.PrimaryButton
import com.siddharth.cv.shared.theme.Reveal
import com.siddharth.cv.shared.theme.SectionEyebrow
import com.siddharth.cv.shared.theme.SectionHeading
import com.siddharth.cv.shared.theme.StatusDot
import com.siddharth.cv.shared.theme.TagChip
import com.siddharth.cv.shared.theme.cvColor
import com.siddharth.cv.shared.theme.cvColors
import com.siddharth.cv.shared.theme.cvType
import com.siddharth.cv.shared.theme.glow
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * The theme engine, running. Port of cv-siddharth/src/labs/ThemeLab.tsx, re-aimed at the claim it
 * actually proves here.
 *
 * The React lab paints its preview with inline `style={{ background: brand.color }}` — it *shows* a
 * reskin. This one cannot: the preview pane is wrapped in `CvTheme(colors = paletteFromSeed(seed))`
 * and every surface inside it is an ordinary [CvCard] / [TagChip] / [PrimaryButton] reading
 * `cvColors`. Nothing in the pane names a colour. So the demo is not a mock of the mechanism, it is
 * the mechanism — the same `LocalCvColors` shadowing that the production Android app does with the
 * tenant seed, and that ProjectDetail already does with `projectColors(project.theme)`.
 *
 * That is also why the surrounding page stays green while the pane changes: a CompositionLocal
 * stops at the subtree boundary, which is the entire reason this pattern is worth 60% of the UI
 * friction it removed.
 */

// ---------------------------------------------------------------------------------------------
// Seeds
// ---------------------------------------------------------------------------------------------

/** [fromData] separates the site's own audited accents from the three hues added for range. */
@Immutable
data class ThemeSeed(val label: String, val color: Color, val fromData: Boolean)

/**
 * The site default, then every project that declares a `theme` (5 of 6 — Deadlock through Kursi),
 * then three hues that exist only to prove the derivation isn't tuned per-brand. The project seeds
 * are read out of `data/CvProjectData.kt`, never re-typed: a colour edited there moves this row.
 */
val themeLabSeeds: List<ThemeSeed> =
    buildList {
        add(ThemeSeed("Site default", CvDarkColors.accent, fromData = true))
        projects.forEach { project ->
            project.theme?.let { add(ThemeSeed(project.name, cvColor(it.accent), fromData = true)) }
        }
        add(ThemeSeed("Amber", cvColor("#f59e0b"), fromData = false))
        add(ThemeSeed("Teal", cvColor("#14b8a6"), fromData = false))
        add(ThemeSeed("Iris", cvColor("#818cf8"), fromData = false))
    }

// ---------------------------------------------------------------------------------------------
// One seed -> a whole palette
// ---------------------------------------------------------------------------------------------

/**
 * The derivation, and the honest core of the demo: one [seed] in, a full [CvColors] out.
 *
 * The ground ladder is built from the seed's *hue direction* — the seed scaled so its brightest
 * channel is 1.0 — rather than from the seed itself, so a dark seed and a bright seed of the same
 * hue produce the same ink/surface/card/line. Multiplying that direction by a fixed ratio is enough
 * to land within a few points of the hand-tuned tokens in `CvProjectData.kt` (Kursi's gold seed
 * derives #1F1A0E / #33291C-ish against its authored #1E1008 / #33241c), which is the strongest
 * argument that the hand-tuning was never the value — the token layer was.
 *
 * `muted`, `accent2` and the glass pair deliberately fall through from [CvDarkColors], exactly as
 * `projectColors()` lets them: `muted` in particular is a WCAG-AA-audited value and re-deriving it
 * per seed would quietly move a contrast floor. Same mechanism, same omissions.
 *
 * ponytail: multiplicative shading, not an HSL/Oklch round trip. It is monotone in luminance and
 * hue-preserving, which is all the ladder needs. Move to Oklch if a seed ever needs a perceptually
 * even ramp rather than a proportional one.
 */
fun paletteFromSeed(seed: Color): CvColors {
    val base = seed.hueDirection()
    return CvDarkColors.copy(
        accent = seed,
        accentDim = seed.scaled(0.74f),
        ink = base.scaled(0.055f),
        surface = base.scaled(0.10f),
        card = base.scaled(0.145f),
        line = base.scaled(0.29f),
        onBackground = mixRgb(Color.White, base, 0.12f),
        deepVoid = base.scaled(0.03f),
    )
}

/**
 * The seed with its brightest channel normalised to 1.0 — its hue and saturation without its
 * brightness. A fully black seed has no direction to recover, so it falls back to a neutral slate;
 * without that guard the whole palette would collapse to black and the ladder would flatten.
 */
private fun Color.hueDirection(): Color {
    val peak = max(red, max(green, blue))
    if (peak <= 0.004f) return Color(0.55f, 0.58f, 0.62f, 1f)
    return Color(red / peak, green / peak, blue / peak, 1f)
}

private fun Color.scaled(k: Float): Color =
    Color(
        (red * k).coerceIn(0f, 1f),
        (green * k).coerceIn(0f, 1f),
        (blue * k).coerceIn(0f, 1f),
        1f,
    )

/**
 * Straight per-channel sRGB mix. Deliberately not `androidx.compose.ui.graphics.lerp`, which
 * interpolates through a linear/Oklab space — this file's numbers are asserted in
 * [themeLabSelfCheck] and they should be reproducible by hand from the hex, not from a colour
 * pipeline that can change under us on a beta toolchain.
 */
private fun mixRgb(a: Color, b: Color, t: Float): Color =
    Color(
        a.red + (b.red - a.red) * t,
        a.green + (b.green - a.green) * t,
        a.blue + (b.blue - a.blue) * t,
        1f,
    )

/** WCAG 2.1 relative luminance. The sRGB transfer curve, then the 709 weights. */
internal fun relativeLuminance(color: Color): Float {
    fun linear(c: Float): Float = if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
    return 0.2126f * linear(color.red) + 0.7152f * linear(color.green) + 0.0722f * linear(color.blue)
}

/** WCAG contrast ratio, 1.0 (identical) … 21.0 (black on white). Order-independent. */
internal fun contrastRatio(a: Color, b: Color): Float {
    val la = relativeLuminance(a)
    val lb = relativeLuminance(b)
    return (max(la, lb) + 0.05f) / (min(la, lb) + 0.05f)
}

/**
 * `#3DDC84` — for the readout. No `String.format` on wasm, so the two-digit pad is manual, and the
 * `+ 0.5f` matches how `Color(r, g, b)` itself rounds into its 8-bit sRGB packing: truncating here
 * instead would print #3CDC84 for a channel that came back as 60.9999.
 */
private fun hexOf(color: Color): String {
    fun byte(c: Float): String =
        ((c * 255f + 0.5f).toInt().coerceIn(0, 255)).toString(16).padStart(2, '0')
    return "#${byte(color.red)}${byte(color.green)}${byte(color.blue)}".uppercase()
}

// ---------------------------------------------------------------------------------------------
// Section
// ---------------------------------------------------------------------------------------------

@Composable
fun ThemeLabSection(modifier: Modifier = Modifier) {
    val colors = cvColors
    val reduced = LocalReducedMotion.current
    var picked by remember { mutableStateOf(themeLabSeeds.first()) }
    var reskins by remember { mutableStateOf(0) }

    // One animation source for the whole demo: the seed crossfades and every derived token follows,
    // because they are all recomputed from it. Reduced motion collapses the tween to 0ms — an
    // instant swap, never a slow one.
    val seed by
        animateColorAsState(
            targetValue = picked.color,
            animationSpec = tween(if (reduced) 0 else CvMotion.DurBase, easing = CvMotion.EaseOutQuart),
            label = "themeLabSeed",
        )
    val derived = paletteFromSeed(seed)

    fun pick(next: ThemeSeed) {
        if (next == picked) return
        picked = next
        reskins++
    }

    Column(modifier.fillMaxWidth()) {
        Reveal {
            Column(Modifier.fillMaxWidth()) {
                SectionEyebrow("// theme engine")
                Spacer(Modifier.height(12.dp))
                SectionHeading("One seed colour, one UI")
                Spacer(Modifier.height(10.dp))
                BasicText(
                    text = "Brand is a token, not a codebase. Every swatch below is a real accent " +
                        "from this site's own project data; pick one and the pane underneath " +
                        "re-skins through the same CompositionLocal the production app uses.",
                    modifier = Modifier.widthIn(max = 680.dp),
                    style = cvType.bodySmall,
                )
            }
        }

        Spacer(Modifier.height(22.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            themeLabSeeds.forEach { candidate ->
                SeedSwatch(
                    seed = candidate,
                    selected = candidate == picked,
                    onPick = { pick(candidate) },
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        MonoMeta(
            text = themeLabSeeds.count { it.fromData }.toString() +
                " real project tokens · " + themeLabSeeds.count { !it.fromData } + " added for range",
        )

        Spacer(Modifier.height(20.dp))

        // The boundary. Everything below this call reads `derived`; everything above still reads the
        // site palette, and nothing had to be told which.
        CvTheme(colors = derived) {
            ThemePreviewPane(
                onNextSeed = { pick(themeLabSeeds[(themeLabSeeds.indexOf(picked) + 1) % themeLabSeeds.size]) },
                onReset = { pick(themeLabSeeds.first()) },
            )
        }

        Spacer(Modifier.height(14.dp))

        if (reskins > 0) {
            MonoMeta(
                text = "$reskins " + (if (reskins == 1) "reskin" else "reskins") +
                    " · 1 CvTheme call · 0 forks",
                modifier = Modifier.padding(bottom = 10.dp),
            )
        }

        BasicText(
            text = "One seed colour drives the palette. The accent and its dim variant come " +
                "straight off the seed; the ink → surface → card → line ladder is derived from " +
                "the seed's hue. Nothing in the pane names a colour — every surface reads " +
                "cvColors, so a single CvTheme(colors = paletteFromSeed(seed)) re-skins all of " +
                "it. That is the mechanism that cut UI development friction ~60% on the Dice " +
                "platform: a new tenant is a token, not a screen pass.",
            modifier = Modifier.widthIn(max = 680.dp),
            style = cvType.bodySmall,
        )

        Spacer(Modifier.height(12.dp))

        // The claim this section is closest to overstating. Stated precisely, on purpose.
        Box(
            Modifier
                .widthIn(max = 680.dp)
                .background(colors.surface, RoundedCornerShape(12.dp))
                .border(1.dp, colors.line, RoundedCornerShape(12.dp))
                .padding(14.dp),
        ) {
            Column {
                MonoMeta("// the honest detail")
                Spacer(Modifier.height(8.dp))
                BasicText(
                    text = "The real engine is hybrid, not server-driven theming. The server " +
                        "supplies one thing — the tenant's seed colour, on " +
                        "UserConfigResponseV2.color. The client owns everything else: dark mode, " +
                        "the user's own chosen colour, the MaterialKolor palette style, Material " +
                        "You, and the theme variant. Calling it \"server-driven\" would be a " +
                        "bigger claim than the code makes.",
                    style = cvType.bodySmall.copy(color = colors.muted),
                )
            }
        }
    }
}

/**
 * A seed chip: the hue as a filled dot plus its label. [selectable] with [Role.RadioButton] rather
 * than a plain clickable — nine mutually exclusive options is a radio group to a screen reader, and
 * "selected" is the state that matters here, not "pressed".
 *
 * Reads the *outer* palette on purpose: the control row must not restyle itself when the preview
 * does, or the demo loses its reference point.
 */
@Composable
private fun SeedSwatch(seed: ThemeSeed, selected: Boolean, onPick: () -> Unit) {
    val colors = cvColors
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(999.dp)
    Row(
        modifier =
            Modifier
                .background(if (selected) seed.color.copy(alpha = 0.10f) else Color.Transparent, shape)
                .border(1.dp, if (selected) seed.color.copy(alpha = 0.7f) else colors.line, shape)
                .selectable(
                    selected = selected,
                    interactionSource = interaction,
                    // indication = null sitewide: the focus/hover treatment is drawn by hand in
                    // CvComponents, and the platform default would land a ripple on a green site.
                    indication = null,
                    role = Role.RadioButton,
                    onClick = onPick,
                )
                .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(10.dp)
                .then(if (selected) Modifier.glow(seed.color, radius = 10.dp, alpha = 0.8f) else Modifier)
                .background(seed.color, CircleShape),
        )
        Spacer(Modifier.width(8.dp))
        BasicText(
            text = seed.label,
            style = cvType.metaMono.copy(color = if (selected) seed.color else colors.muted),
        )
    }
}

/**
 * The miniature. Every composable in here is the real sitewide component, unmodified — that is the
 * whole point. If this pane reskins, the site reskins, because there is no third thing in between.
 *
 * The two buttons are wired to real seed changes rather than left inert: the site's rule is that a
 * dead control is worse than an honest one.
 */
@Composable
private fun ThemePreviewPane(onNextSeed: () -> Unit, onReset: () -> Unit) {
    val colors = cvColors
    val shape = RoundedCornerShape(18.dp)

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.surface, shape)
                .border(1.dp, colors.line, shape)
                .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            StatusDot()
            Spacer(Modifier.width(10.dp))
            BasicText(
                text = "Tenant preview",
                style = cvType.cardTitle.copy(fontSize = cvType.body.fontSize),
            )
            Spacer(Modifier.width(12.dp))
            MonoMeta("seed " + hexOf(colors.accent))
        }

        CvCard(glowOnHover = false) {
            BasicText(text = "Trip summary", style = cvType.cardTitle)
            Spacer(Modifier.height(8.dp))
            BasicText(
                text = "A card, a chip row, two buttons and a metric — the same components the " +
                    "rest of this page is built from, one CompositionLocal deeper.",
                style = cvType.bodySmall,
            )
            Spacer(Modifier.height(14.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TagChip("Compose", selected = true)
                TagChip("Room")
                TagChip("Hilt")
                TagChip("WorkManager")
            }
            Spacer(Modifier.height(16.dp))
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                // Under ~420dp the gauge and the numeral fight for the same row; stack them.
                if (maxWidth >= 420.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MetricGauge(0.60f)
                        Spacer(Modifier.width(18.dp))
                        MetricColumn()
                    }
                } else {
                    Column {
                        MetricGauge(0.60f)
                        Spacer(Modifier.height(12.dp))
                        MetricColumn()
                    }
                }
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PrimaryButton(text = "Next seed", onClick = onNextSeed)
            GhostButton(text = "Site default", onClick = onReset)
        }
    }
}

@Composable
private fun MetricColumn() {
    Column {
        AnimatedCounter(60, suffix = "%")
        Spacer(Modifier.height(4.dp))
        BasicText(text = "less UI friction", style = cvType.body)
        Spacer(Modifier.height(2.dp))
        MonoMeta("one token, every screen")
    }
}

// ---------------------------------------------------------------------------------------------
// ponytail: one runnable check instead of a test module — same shape as navSelfCheck() in Nav.kt.
// Call it from selfCheck() in jvmMain/prerender/Prerender.kt; nothing in composeMain executes.
//
// It guards the two things a colour derivation fails at silently: a ladder that stops being a
// ladder (two rungs collapsing into the same shade, so cards vanish into their own ground), and
// text that stops being readable on the surface it was derived alongside. Both are invisible on
// the one seed you happened to look at and wrong on the seventh.
// ---------------------------------------------------------------------------------------------
internal fun themeLabSelfCheck() {
    check(themeLabSeeds.size >= 8) { "the swatch row needs enough hues to be a demonstration" }
    check(themeLabSeeds.count { it.fromData } >= 6) { "site default + the 5 themed projects" }
    check(themeLabSeeds.distinctBy { it.label }.size == themeLabSeeds.size) { "duplicate seed label" }

    // The seed must survive as the accent bit-for-bit — the swatches claim to be the real tokens
    // from CvProjectData, and a derivation that "improves" the input would make that a lie.
    val android = cvColor("#3ddc84")
    check(paletteFromSeed(android).accent == android) { "seed is the accent, unmodified" }

    // Every real swatch, plus the three seeds that break a naive derivation: pure black (no hue to
    // recover — the fallback in `hueDirection` is what stops the ladder flattening), pure white (the
    // brightest possible ground, so the worst case for every contrast floor below), and a fully
    // saturated primary (two channels at zero).
    val probes = themeLabSeeds.map { it.color } + listOf(Color.Black, Color.White, cvColor("#ff0000"))

    probes.forEach { seed ->
        val p = paletteFromSeed(seed)
        val hex = hexOf(seed)

        // A ladder, not a set of similar darks: strictly brightening ink -> surface -> card -> line.
        val ladder = listOf(p.ink, p.surface, p.card, p.line).map(::relativeLuminance)
        check(ladder.zipWithNext().all { (lo, hi) -> hi > lo + 0.0005f }) {
            "$hex: ink/surface/card/line must strictly brighten, got $ladder"
        }
        check(listOf(p.ink, p.surface, p.card, p.line, p.onBackground).distinct().size == 5) {
            "$hex: two ground tokens derived to the same colour"
        }

        // Readability floors, on the two grounds body text actually lands on.
        check(contrastRatio(p.onBackground, p.card) >= 7f) {
            "$hex: body text on card is ${contrastRatio(p.onBackground, p.card)}:1, want AAA"
        }
        check(contrastRatio(p.onBackground, p.surface) >= 7f) {
            "$hex: body text on surface is ${contrastRatio(p.onBackground, p.surface)}:1, want AAA"
        }
        // `muted` is inherited, not derived — this asserts the inherited value still clears AA
        // against every card the derivation can produce, which is the reason it is inherited.
        check(contrastRatio(p.muted, p.card) >= 4.5f) {
            "$hex: muted on card is ${contrastRatio(p.muted, p.card)}:1, below the AA floor"
        }
    }

    // The accent pair is asserted over the real swatches only. `accentDim` is a scale of the seed,
    // and a black seed has nothing darker to scale to — the degenerate case is correct behaviour of
    // the derivation, not something to assert against, and no swatch can reach it.
    themeLabSeeds.forEach { candidate ->
        val p = paletteFromSeed(candidate.color)
        check(p.accentDim != p.accent) { "${candidate.label}: accentDim collapsed onto accent" }
        check(relativeLuminance(p.accentDim) < relativeLuminance(p.accent)) {
            "${candidate.label}: accentDim must be the darker of the pair"
        }
    }

    // Anchors for the luminance maths itself, so a broken transfer curve fails here and not as a
    // mysteriously passing contrast assertion above.
    check(relativeLuminance(Color.White) > 0.99f) { "white luminance" }
    check(relativeLuminance(Color.Black) < 0.001f) { "black luminance" }
    check(contrastRatio(Color.White, Color.Black) > 20.9f) { "white-on-black is the 21:1 ceiling" }
    check(contrastRatio(android, android) == 1f) { "a colour has no contrast with itself" }

    check(hexOf(cvColor("#3ddc84")) == "#3DDC84") { "hex round-trip" }
    check(hexOf(Color.Black) == "#000000") { "hex pads both digits" }
}
