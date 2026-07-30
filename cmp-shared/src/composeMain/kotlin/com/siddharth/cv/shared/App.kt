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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.siddharth.cv.shared.chat.FloatingChat
import com.siddharth.cv.shared.data.profile
import com.siddharth.cv.shared.forge.ParticleForge
import com.siddharth.cv.shared.labs.LabScreen
import com.siddharth.cv.shared.palette.CommandPalette
import com.siddharth.cv.shared.palette.PaletteCommand
import com.siddharth.cv.shared.playground.PlaygroundScreen
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
// LocalClipboardManager rather than LocalClipboard, for the reason already documented above
// ContactSection in home/HomeSections.kt: `Clipboard.setClipEntry` needs a ClipEntry and commonMain
// has no String -> ClipEntry factory, so `setText` is the only common write path. Same suppression
// here so the palette's copy-email does the same thing the contact button does.
@Suppress("DEPRECATION", "DEPRECATION_ERROR")
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

    var paletteOpen by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    val focus = remember { FocusRequester() }

    // The chord is caught on a focusable root wrapping the whole app. onPreviewKeyEvent sees the
    // event BEFORE any focused child, which is the point: the terminal and the chat composer are
    // both text fields that would otherwise swallow ⌘K as ordinary input.
    CvTheme {
        CompositionLocalProvider(LocalNav provides nav) {
            Box(
                Modifier.fillMaxSize()
                    .focusRequester(focus)
                    .focusable()
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when {
                            event.key == Key.K && (event.isMetaPressed || event.isCtrlPressed) -> {
                                paletteOpen = !paletteOpen
                                true
                            }
                            // Only consume Escape when the palette is actually open, or it would
                            // steal the key from every other surface that wants it.
                            event.key == Key.Escape && paletteOpen -> {
                                paletteOpen = false
                                true
                            }
                            else -> false
                        }
                    },
            ) {
                LaunchedEffect(Unit) { focus.requestFocus() }
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
                    Route.Lab -> LabScreen(content)
                    Route.Forge -> ParticleForge(content)
                    Route.Playground -> PlaygroundScreen(content)
                    is Route.ProjectDetail -> ProjectDetailScreen(route.slug, content)
                }

                TopBar(nav, homeList)

                // ⌘K / Ctrl-K. Handled here rather than inside the palette so that exactly one
                // place owns the chord, and so the palette itself stays a pure function of
                // `visible` — which is also what makes it testable without a key-event harness.
                CommandPalette(
                    visible = paletteOpen,
                    onDismiss = { paletteOpen = false },
                    onCommand = { command ->
                        paletteOpen = false
                        runPaletteCommand(command, nav, uriHandler, clipboard)
                    },
                )

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

/**
 * Palette id -> action. The ids are strings rather than lambdas so `palette/CommandPalette.kt` needs
 * no dependency on navigation, the clipboard or the URI handler — it just lists what exists and lets
 * this decide what any of it means. `paletteSelfCheck` already asserts the ids are unique.
 *
 * An unrecognised id is deliberately a no-op rather than a crash: a stale id can only come from
 * someone adding a command and forgetting this branch, and a palette entry that does nothing is a
 * far better failure than a white screen.
 */
@Suppress("DEPRECATION", "DEPRECATION_ERROR")
private fun runPaletteCommand(
    command: PaletteCommand,
    nav: CvNavState,
    uriHandler: UriHandler,
    clipboard: ClipboardManager,
) {
    val (kind, value) = command.id.split(':', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
    when (kind) {
        "section" -> nav.goSection(value)
        "project" -> nav.go(Route.ProjectDetail(value))
        "route" -> when (value) {
            "resume" -> nav.go(Route.Resume)
            "terminal" -> nav.go(Route.Terminal)
            "lab" -> nav.go(Route.Lab)
            "forge" -> nav.go(Route.Forge)
            "compose", "playground" -> nav.go(Route.Playground)
            "home" -> nav.go(Route.Home)
        }
        "action" -> when (value) {
            "copy-email" -> clipboard.setText(AnnotatedString(profile.email))
            "github" -> uriHandler.openUri(profile.github)
            "linkedin" -> uriHandler.openUri(profile.linkedin)
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
