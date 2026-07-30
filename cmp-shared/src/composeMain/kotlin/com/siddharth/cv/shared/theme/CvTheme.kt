package com.siddharth.cv.shared.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.siddharth.cv.shared.data.ProjectTheme
import com.siddharth.cv.shared.resources.Res
import com.siddharth.cv.shared.resources.dm_mono_medium
import com.siddharth.cv.shared.resources.space_grotesk_bold
import com.siddharth.cv.shared.resources.space_grotesk_medium
import com.siddharth.cv.shared.resources.space_grotesk_regular
import com.siddharth.cv.shared.resources.space_grotesk_semibold
import org.jetbrains.compose.resources.Font

/**
 * Kotlin translation of the `@theme` token block in cv-siddharth/src/index.css.
 *
 * Deliberately NOT MaterialTheme.ColorScheme: the token names (ink / surface / card / line /
 * accent / accent2 / void / glass / muted) don't map onto M3 roles cleanly enough to be worth
 * forcing, and every M3 default would drag purple into a green site. Nothing below reads
 * `MaterialTheme.colorScheme` — treat any such read elsewhere in the port as a bug.
 */
@Immutable
data class CvColors(
    val accent: Color,
    val accentDim: Color,
    val accent2: Color,
    val accent2Dim: Color,
    val ink: Color,
    val surface: Color,
    val card: Color,
    val line: Color,
    val onBackground: Color,
    val muted: Color,
    val deepVoid: Color,
    val glass: Color,
    val glassBorder: Color,
)

@Immutable
data class CvTypography(
    val hero: TextStyle,
    val h2: TextStyle,
    val metric: TextStyle,
    val cardTitle: TextStyle,
    val body: TextStyle,
    val bodySmall: TextStyle,
    val mono: TextStyle,
    val metaMono: TextStyle,
    val eyebrow: TextStyle,
    val ghostNumeral: TextStyle,
)

/** Site defaults — the `@theme` block, verbatim. */
val CvDarkColors: CvColors =
    CvColors(
        accent = cvColor("#3ddc84"), // Android green
        accentDim = cvColor("#2bb86c"),
        accent2 = cvColor("#5ee6ff"),
        accent2Dim = cvColor("#2fb8d6"),
        ink = cvColor("#0b0f0d"),
        surface = cvColor("#111613"),
        card = cvColor("#171e1a"),
        line = cvColor("#243029"),
        onBackground = cvColor("#e8efe9"),
        // WCAG-AA floor: 4.65:1 against Kursi's #33241c card, the lightest themed ground on the
        // site. Do NOT lighten or darken — this exact value is what let the a11y suite drop its
        // color-contrast allowlist.
        muted = cvColor("#8b909a"),
        deepVoid = cvColor("#05070a"),
        glass = Color(0xFF111619).copy(alpha = 0.66f),
        glassBorder = Color(0xFF5DE6FF).copy(alpha = 0.14f),
    )

/**
 * The résumé route is dark-on-light. On the web that's an `html.resume-mode` media-ish override;
 * here it's just a different [CvColors] instance handed to [CvTheme] — same mechanism as a project
 * theme, which is the whole point of not hardcoding anything.
 */
val CvResumeColors: CvColors =
    CvDarkColors.copy(
        ink = cvColor("#e4e4e7"),
        surface = cvColor("#ffffff"),
        card = cvColor("#ffffff"),
        onBackground = cvColor("#111111"),
        muted = cvColor("#55585f"),
        line = cvColor("#c8c8cc"),
    )

val LocalCvColors: ProvidableCompositionLocal<CvColors> = staticCompositionLocalOf { CvDarkColors }

/**
 * Typography is derived from the colours + the viewport, so it changes when a project theme or a
 * window resize does — a *dynamic* local, unlike the other two.
 */
val LocalCvType: ProvidableCompositionLocal<CvTypography> =
    compositionLocalOf { error("CvTypography not provided — wrap the tree in CvTheme { }") }

val LocalReducedMotion: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { false }

val cvColors: CvColors
    @Composable @ReadOnlyComposable get() = LocalCvColors.current

val cvType: CvTypography
    @Composable @ReadOnlyComposable get() = LocalCvType.current

/** `"#3ddc84"` -> opaque [Color]. Accepts a leading `#` or not. */
fun cvColor(hex: String): Color = Color(hex.removePrefix("#").toLong(16) or 0xFF000000)

/**
 * The CSS-cascade analogue of ProjectDetail.tsx:270-284 — a project's optional `theme` becomes an
 * override of a handful of tokens; everything it doesn't name falls through to the site defaults.
 * `accent`/`accentDim` are required, the rest are optional, exactly as the TS type declares.
 */
fun projectColors(theme: ProjectTheme?): CvColors {
    if (theme == null) return CvDarkColors
    return CvDarkColors.copy(
        accent = cvColor(theme.accent),
        accentDim = cvColor(theme.accentDim),
        ink = theme.ink?.let(::cvColor) ?: CvDarkColors.ink,
        surface = theme.surface?.let(::cvColor) ?: CvDarkColors.surface,
        card = theme.card?.let(::cvColor) ?: CvDarkColors.card,
        line = theme.line?.let(::cvColor) ?: CvDarkColors.line,
    )
}

/**
 * The `clamp(min, …vw, max)` substitute. Linear ramp between the same 375dp and 1920dp anchors the
 * CSS uses, so `--text-hero` reproduces exactly: 36sp at 375dp, 60sp at 1920dp.
 *
 * `widthDp` of 0 is treated as 375 — on wasmJs `LocalWindowInfo.containerSize` legitimately reports
 * 0 during the first composition and every fluid size would otherwise collapse to its minimum.
 */
fun fluidSp(minSp: Float, maxSp: Float, widthDp: Float): TextUnit {
    val w = if (widthDp <= 0f) 375f else widthDp
    val t = ((w - 375f) / (1920f - 375f)).coerceIn(0f, 1f)
    return (minSp + (maxSp - minSp) * t).sp
}

/**
 * The site's real typefaces, vendored under `composeResources/font/`.
 *
 * These must be built inside composition rather than as top-level vals: `Font(FontResource)` is
 * itself `@Composable` (it suspends on the resource load), so it cannot be folded into a constant.
 * That is also why [rememberCvTypography] takes the two families as parameters.
 *
 * Compose paints through Skia and never consults the browser's font stack, so a CSS `@font-face`
 * or a `font-family` on `<body>` is invisible here — the bytes have to be in the bundle. That is
 * the whole reason `compose.components.resources` is a dependency.
 */
@Composable
private fun rememberDisplayFamily(): FontFamily = FontFamily(
    Font(Res.font.space_grotesk_regular, FontWeight.Normal),
    Font(Res.font.space_grotesk_medium, FontWeight.Medium),
    Font(Res.font.space_grotesk_semibold, FontWeight.SemiBold),
    Font(Res.font.space_grotesk_bold, FontWeight.Bold),
    // No Black cut is vendored; ghostNumeral asks for W900 and Skia synthesises it from Bold.
    // ponytail: add space_grotesk_black.ttf if the synthesised weight ever reads wrong.
    Font(Res.font.space_grotesk_bold, FontWeight.Black),
)

/** DM Mono stands in for JetBrains Mono — same monospace feel, one weight, far fewer bytes. */
@Composable
private fun rememberMonoFamily(): FontFamily = FontFamily(
    Font(Res.font.dm_mono_medium, FontWeight.Normal),
    Font(Res.font.dm_mono_medium, FontWeight.Medium),
)

@Composable
private fun rememberCvTypography(
    colors: CvColors,
    widthDp: Float,
    displayFamily: FontFamily,
    monoFamily: FontFamily,
): CvTypography =
    remember(colors, widthDp, displayFamily, monoFamily) {
        val DisplayFamily = displayFamily
        val MonoFamily = monoFamily
        val hero = fluidSp(36f, 60f, widthDp)
        val h2 = fluidSp(28f, 36f, widthDp)
        val metric = fluidSp(30f, 40f, widthDp)
        CvTypography(
            hero =
                TextStyle(
                    fontFamily = DisplayFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = hero,
                    lineHeight = hero * 1.05f,
                    letterSpacing = (-0.02).em,
                    color = colors.onBackground,
                ),
            h2 =
                TextStyle(
                    fontFamily = DisplayFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = h2,
                    lineHeight = h2 * 1.15f,
                    letterSpacing = (-0.015).em,
                    color = colors.onBackground,
                ),
            metric =
                TextStyle(
                    fontFamily = DisplayFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = metric,
                    lineHeight = metric * 1.1f,
                    letterSpacing = (-0.02).em,
                    color = colors.accent,
                ),
            cardTitle =
                TextStyle(
                    fontFamily = DisplayFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    lineHeight = 26.sp,
                    letterSpacing = (-0.01).em,
                    color = colors.onBackground,
                ),
            body =
                TextStyle(
                    fontFamily = DisplayFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 16.sp,
                    lineHeight = 25.sp,
                    color = colors.onBackground,
                ),
            bodySmall =
                TextStyle(
                    fontFamily = DisplayFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    color = colors.muted,
                ),
            mono =
                TextStyle(
                    fontFamily = MonoFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = colors.onBackground,
                ),
            metaMono =
                TextStyle(
                    fontFamily = MonoFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    letterSpacing = 0.08.em,
                    color = colors.muted,
                ),
            eyebrow =
                TextStyle(
                    fontFamily = DisplayFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    letterSpacing = 0.14.em,
                    color = colors.accent.copy(alpha = 0.7f),
                ),
            ghostNumeral =
                TextStyle(
                    fontFamily = DisplayFamily,
                    fontWeight = FontWeight.Black,
                    fontSize = 36.sp,
                    lineHeight = 36.sp,
                    color = colors.accent.copy(alpha = 0.10f),
                ),
        )
    }

/**
 * Provides the three locals and paints nothing — the ground is [AmbientBackground]'s job.
 *
 * Nest it to re-theme a subtree: `CvTheme(colors = projectColors(project.theme)) { … }` shadows
 * `LocalCvColors` for everything below, which is exactly what setting `--color-accent` on
 * `<main class="project-detail">` does on the web. [reducedMotion] defaults to the *inherited*
 * value so a nested CvTheme can never silently re-enable motion.
 */
@Composable
fun CvTheme(
    colors: CvColors = CvDarkColors,
    reducedMotion: Boolean = LocalReducedMotion.current,
    content: @Composable () -> Unit,
) {
    val widthDp = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp().value }
    val typography =
        rememberCvTypography(colors, widthDp, rememberDisplayFamily(), rememberMonoFamily())
    CompositionLocalProvider(
        LocalCvColors provides colors,
        LocalCvType provides typography,
        LocalReducedMotion provides reducedMotion,
        content = content,
    )
}
