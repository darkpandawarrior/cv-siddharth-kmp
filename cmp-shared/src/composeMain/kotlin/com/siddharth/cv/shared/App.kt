package com.siddharth.cv.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.siddharth.cv.shared.chat.FloatingChat
import com.siddharth.cv.shared.detail.ProjectDetailScreen
import com.siddharth.cv.shared.detail.ResumeScreen
import com.siddharth.cv.shared.home.HomeScreen
import com.siddharth.cv.shared.home.homeSections
import com.siddharth.cv.shared.media.InstallCvImageLoader
import com.siddharth.cv.shared.terminal.TerminalScreen
import com.siddharth.cv.shared.theme.CvTheme
import com.siddharth.cv.shared.theme.ShaderOrGradientBackground
import com.siddharth.cv.shared.theme.cvColors
import com.siddharth.cv.shared.theme.cvType

/**
 * The single Compose entry point every platform renders. Android's MainActivity, the desktop `main`,
 * the iOS UIViewController and the wasm `main` all just call [App] — platform code stays a shell.
 *
 * This is the nav host the rest of the port is written against: it owns the one [CvNavState], hands
 * it down through [LocalNav], paints [ShaderOrGradientBackground] once behind everything, overlays
 * [FloatingChat] once in front of everything, and holds the homepage's [LazyListState] so the top
 * bar's scroll-spy and `CvNavState.goSection()` both drive the same scroll position.
 */
@Composable
fun App(
    nav: CvNavState = remember { CvNavState() },
    /**
     * Fired whenever the route changes. Only the web shell passes anything — it turns each change
     * into a `history.pushState` so the address bar, Back button and shareable links all work.
     * Every other platform ignores it, which is why the default is a no-op rather than an expect.
     */
    onRouteChanged: (Route) -> Unit = {},
) {
    // Hoisted rather than owned by HomeScreen: the top bar reads it to highlight the active section,
    // and it must survive a trip to a project page so that returning home lands where you left.
    val homeList = rememberLazyListState()

    LaunchedEffect(nav.current) { onRouteChanged(nav.current) }

    // Must run before anything calls ProjectShot — without the Ktor fetcher registered, every
    // remote image resolves to blank with no error.
    InstallCvImageLoader()

    CvTheme {
        CompositionLocalProvider(LocalNav provides nav) {
            Box(Modifier.fillMaxSize()) {
                // The GPU wash where Skia can run SkSL, the CPU starfield everywhere else — the
                // wrapper decides by compiling the shader once, so there is no platform branch here.
                ShaderOrGradientBackground(Modifier.fillMaxSize())

                // Screens that re-theme (résumé paper, per-project accent) nest their own CvTheme,
                // so the ambient layer behind them keeps the site's dark palette by construction.
                val content = Modifier.fillMaxSize().padding(top = TopBarHeight)
                when (val route = nav.current) {
                    Route.Home -> HomeScreen(homeList, content)
                    Route.Resume -> ResumeScreen(content)
                    Route.Terminal -> TerminalScreen(content)
                    is Route.ProjectDetail -> ProjectDetailScreen(route.slug, content)
                }

                TopBar(nav, homeList)

                // Last child, so the open panel is never clipped by the top bar or by a screen that
                // paints its own opaque ground (the résumé does). It fills the window but only
                // consumes pointer input where the launcher/panel actually sit, so the page stays
                // clickable underneath. Inside the LocalNav provider: the greeting and quick prompts
                // are rendered from the current route, which is the whole reason it lives here
                // rather than inside each screen.
                FloatingChat()
            }
        }
    }
}

private val TopBarHeight = 64.dp

/**
 * Below this the section links are dropped rather than wrapped — the wordmark, back, and the two
 * route links are the floor of what has to stay reachable.
 */
private val WideBreakpoint = 900.dp

@Composable
private fun TopBar(nav: CvNavState, homeList: LazyListState) {
    val colors = cvColors
    val widthDp = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    val onHome = nav.current == Route.Home

    // The scroll-spy. `homeSections[i]` is LazyColumn item `i` by construction (see HomeScreen), so
    // the first visible index *is* the active section — no offset table to keep in sync.
    val active by
        remember(homeList) {
            derivedStateOf { homeSections.getOrNull(homeList.firstVisibleItemIndex)?.id }
        }

    Row(
        modifier =
            Modifier.fillMaxWidth()
                .height(TopBarHeight)
                .background(colors.glass)
                .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavLink("siddharth.", accent = true) { nav.go(Route.Home) }

        if (onHome && widthDp >= WideBreakpoint) {
            Spacer(Modifier.width(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                homeSections.forEach { section ->
                    NavLink(section.label, accent = section.id == active) {
                        nav.goSection(section.id)
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        if (nav.canGoBack) {
            NavLink("← back") { nav.back() }
            Spacer(Modifier.width(18.dp))
        }
        NavLink("résumé", accent = nav.current == Route.Resume) { nav.go(Route.Resume) }
        Spacer(Modifier.width(18.dp))
        NavLink("terminal", accent = nav.current == Route.Terminal) { nav.go(Route.Terminal) }
    }
}

@Composable
private fun NavLink(text: String, accent: Boolean = false, onClick: () -> Unit) {
    val colors = cvColors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    BasicText(
        text = text,
        modifier =
            Modifier.hoverable(interaction)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                )
                .padding(vertical = 8.dp),
        style = cvType.metaMono.copy(color = if (accent || hovered) colors.accent else colors.muted),
    )
}
