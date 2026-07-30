@file:OptIn(ExperimentalLayoutApi::class)

package com.siddharth.cv.shared.home

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.siddharth.cv.shared.LocalNav
import com.siddharth.cv.shared.Route
import com.siddharth.cv.shared.data.metrics
import com.siddharth.cv.shared.data.profile
import com.siddharth.cv.shared.playground.ThemeLabSection
import com.siddharth.cv.shared.data.projectBySlug
import com.siddharth.cv.shared.data.projectOrder
import com.siddharth.cv.shared.theme.CircuitDivider
import com.siddharth.cv.shared.theme.CvContentMaxWidth
import com.siddharth.cv.shared.theme.CvGutter
import com.siddharth.cv.shared.theme.CvMotion
import com.siddharth.cv.shared.theme.CvSectionGap
import com.siddharth.cv.shared.theme.GhostButton
import com.siddharth.cv.shared.theme.HeroShimmerText
import com.siddharth.cv.shared.theme.MonoMeta
import com.siddharth.cv.shared.theme.PrimaryButton
import com.siddharth.cv.shared.theme.Reveal
import com.siddharth.cv.shared.theme.StatusDot
import com.siddharth.cv.shared.theme.cvColor
import com.siddharth.cv.shared.theme.cvColors
import com.siddharth.cv.shared.theme.cvType
import com.siddharth.cv.shared.theme.glow
import com.siddharth.cv.shared.theme.rememberInfiniteFloat
import com.siddharth.cv.shared.theme.tiltOnHover
import kotlinx.coroutines.delay

/**
 * The homepage — the port of cv-siddharth/src/App.tsx.
 *
 * The scroll-spy in `App()` and `CvNavState.goSection()` both index into [homeSections], so
 * "LazyColumn item N is homeSections[N]" is a hard invariant. It is held *structurally* here: the
 * LazyColumn is driven by `items(homeSections)` and dispatches on the section id, so the list and
 * the item order cannot drift apart. Adding a section means adding one row to [homeSections] and one
 * branch to the `when` — nothing else.
 */
data class HomeSection(val id: String, val label: String, val index: Int)

val homeSections: List<HomeSection> =
    listOf(
        HomeSection("top", "Home", 0),
        HomeSection("work", "Work", 1),
        HomeSection("projects", "Projects", 2),
        HomeSection("source", "The Source", 3),
        HomeSection("experience", "Experience", 4),
        HomeSection("skills", "Skills", 5),
        HomeSection("theme", "Theme Engine", 6),
        HomeSection("explore", "Explore", 7),
        HomeSection("contact", "Contact", 8),
    )

@Composable
fun HomeScreen(listState: LazyListState, modifier: Modifier = Modifier) {
    val nav = LocalNav.current

    // The `goToSection` / hash-scroll equivalent. `pendingSection` is a one-shot request the nav
    // state raises from anywhere (top bar, hero CTA, a card link) — this is the only consumer.
    LaunchedEffect(nav.pendingSection) {
        val id = nav.pendingSection ?: return@LaunchedEffect
        homeSections.firstOrNull { it.id == id }?.let { listState.animateScrollToItem(it.index) }
        nav.consumeSection()
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 32.dp, bottom = 120.dp),
    ) {
        items(homeSections, key = { it.id }) { section ->
            SectionSlot(last = section.index == homeSections.lastIndex) {
                when (section.id) {
                    "top" -> HeroSection()
                    "work" -> CaseStudiesSection()
                    "projects" -> ProjectsSection()
                    "source" -> SourceSection()
                    "experience" -> ExperienceSection()
                    "skills" -> SkillsSection()
                    // The theme engine demonstrated rather than asserted: the preview re-skins
                    // through a nested CvTheme, which is the same CompositionLocal mechanism the
                    // production app uses per tenant.
                    "theme" -> ThemeLabSection()
                    "explore" -> ExploreSection()
                    "contact" -> ContactSection()
                }
            }
        }
    }
}

/**
 * `mx-auto max-w-5xl px-6` plus the `<Circuit />` seam. The divider lives *inside* the item it
 * follows rather than in an item of its own — a divider item would shift every index by one and
 * break the scroll-spy contract above.
 */
@Composable
private fun SectionSlot(last: Boolean, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .widthIn(max = CvContentMaxWidth)
                .fillMaxWidth()
                .padding(horizontal = CvGutter),
        ) {
            content()
        }
        if (!last) {
            Spacer(Modifier.height(CvSectionGap))
            CircuitDivider(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .widthIn(max = CvContentMaxWidth)
                    .fillMaxWidth()
                    .padding(horizontal = CvGutter),
            )
            Spacer(Modifier.height(CvSectionGap))
        }
    }
}

// -------------------------------------------------------------------------------------------
// Hero
// -------------------------------------------------------------------------------------------

/**
 * App.tsx `<Hero />`. Two columns above ~900dp (text | device), stacked below — the CSS
 * `lg:grid-cols-[1fr_280px]`.
 *
 * ponytail: the cycling `<Typewriter />` identity line is dropped. The device screen already
 * cross-fades every 3s, and two independent tickers in one hero read as noise rather than life.
 */
@Composable
private fun HeroSection() {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 900.dp) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { HeroCopy() }
                Spacer(Modifier.width(40.dp))
                TiltPhone()
            }
        } else {
            Column(Modifier.fillMaxWidth()) {
                HeroCopy()
                Spacer(Modifier.height(56.dp))
                TiltPhone(Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}

/** The `rise-in` cascade: three [Reveal]s at 0 / 80 / 160 ms, matching `.rise-in-1..3`. */
@Composable
private fun HeroCopy() {
    val colors = cvColors
    val nav = LocalNav.current

    Column(Modifier.fillMaxWidth()) {
        Reveal {
            Column {
                MonoMeta("${profile.location} · ${profile.title}")
                Spacer(Modifier.height(16.dp))
                BasicText(text = profile.name, style = cvType.hero)
                HeroShimmerText(text = "Prototype to platform.")
            }
        }

        Spacer(Modifier.height(20.dp))

        Reveal(delayMillis = 80) {
            BasicText(
                text = profile.intro,
                modifier = Modifier.widthIn(max = 680.dp),
                style = cvType.body,
            )
        }

        Spacer(Modifier.height(28.dp))

        Reveal(delayMillis = 160) {
            Column {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PrimaryButton("See the work", onClick = { nav.goSection("work") })
                    GhostButton("Résumé", onClick = { nav.go(Route.Resume) })
                    GhostButton("Terminal", onClick = { nav.go(Route.Terminal) })
                }

                Spacer(Modifier.height(24.dp))

                // The headline numbers, as a mono receipt strip. Values are claim-audited strings —
                // rendered verbatim, never reformatted.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    metrics.forEach { m -> MonoMeta("${m.value} ${m.label}") }
                }

                Spacer(Modifier.height(20.dp))

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
}

private val PhoneBezel = cvColor("#0c100e")
private val PhoneShape = RoundedCornerShape(28.dp)
private val PhoneScreenShape = RoundedCornerShape(21.dp)

/**
 * The `<Phone3D />` stand-in: a real device body that floats and tilts, with a generated screen
 * cycling the project names. No three.js scene graph exists on CMP, but the *read* of that hero
 * element — "a phone, alive, showing my work" — is entirely reproducible with a graphicsLayer.
 */
@Composable
private fun TiltPhone(modifier: Modifier = Modifier) {
    val colors = cvColors
    val float by rememberInfiniteFloat(5000, from = -1f, to = 1f, easing = CvMotion.EaseOutQuart)
    val names = remember { projectOrder.mapNotNull { projectBySlug(it)?.name } }
    var shown by remember { mutableStateOf(0) }

    LaunchedEffect(names) {
        if (names.size < 2) return@LaunchedEffect
        while (true) {
            delay(3000)
            shown = (shown + 1) % names.size
        }
    }

    Box(
        modifier
            .graphicsLayer { translationY = float * 10.dp.toPx() }
            .tiltOnHover(maxDegrees = 8f, spotlight = colors.accent)
            .size(width = 260.dp, height = 540.dp)
            .glow(colors.accent, radius = 56.dp, alpha = 0.22f, offsetY = 22.dp)
            .background(PhoneBezel, PhoneShape)
            .padding(7.dp),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(colors.deepVoid, PhoneScreenShape)
                .background(
                    Brush.verticalGradient(
                        colors =
                            listOf(
                                colors.accent.copy(alpha = 0.20f),
                                Color.Transparent,
                                colors.accent2.copy(alpha = 0.14f),
                            ),
                    ),
                    PhoneScreenShape,
                )
                .border(1.dp, colors.line.copy(alpha = 0.6f), PhoneScreenShape)
                .padding(horizontal = 18.dp, vertical = 22.dp),
        ) {
            MonoMeta("sid.android")
            Spacer(Modifier.height(6.dp))
            BasicText(
                text = "now showing",
                style = cvType.metaMono.copy(color = colors.accent.copy(alpha = 0.8f)),
            )

            Spacer(Modifier.height(28.dp))

            Crossfade(targetState = names.getOrNull(shown).orEmpty(), label = "phoneProject") { name ->
                BasicText(text = name, style = cvType.cardTitle.copy(color = colors.accent))
            }

            Spacer(Modifier.height(28.dp))

            // Faux content rows — enough structure to read as an app, no bitmaps to download.
            repeat(5) { i ->
                Box(
                    Modifier
                        .fillMaxWidth(if (i % 2 == 0) 1f else 0.72f)
                        .height(if (i == 0) 54.dp else 34.dp)
                        .background(
                            colors.card.copy(alpha = 0.75f - i * 0.09f),
                            RoundedCornerShape(10.dp),
                        ),
                )
                Spacer(Modifier.height(12.dp))
            }

            Spacer(Modifier.weight(1f))

            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(96.dp)
                    .height(4.dp)
                    .background(colors.line, RoundedCornerShape(2.dp)),
            )
        }
    }
}
