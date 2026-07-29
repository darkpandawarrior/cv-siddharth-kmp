package com.siddharth.cv.shared

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The four surfaces that ported. The React site has thirteen routes; nine of them are WebGL, tldraw,
 * Leaflet or the chat backend and are documented as dropped/deferred in the README rather than
 * stubbed here — a `Route` entry with nothing behind it is a dead end, not a roadmap.
 */
sealed interface Route {
    data object Home : Route

    /** The only route carrying free text. An unknown slug resolves to ProjectDetailScreen's 404. */
    data class ProjectDetail(val slug: String) : Route

    data object Resume : Route

    data object Terminal : Route
}

/**
 * The whole router. No navigation-compose: a back stack is a list, and `mutableStateListOf` already
 * makes a list observable, so the dependency would buy deep links and saved state that a canvas app
 * with no URL bar and no process death (on web/desktop) cannot use anyway.
 *
 * [Route.Home] is the floor of the stack and never appears twice — navigating home pops back to it,
 * which is what both the top-bar wordmark and the terminal's `exit` mean by "home".
 */
class CvNavState {
    private val stack = mutableStateListOf<Route>(Route.Home)

    val current: Route get() = stack[stack.lastIndex]

    val canGoBack: Boolean get() = stack.size > 1

    /**
     * A one-shot "scroll the homepage to this section" request, raised from anywhere (top bar,
     * footer sitemap, a project page's back link). `HomeScreen` is the only consumer; it scrolls and
     * calls [consumeSection]. Section ids are `homeSections` in HomeScreen.kt.
     */
    var pendingSection: String? by mutableStateOf(null)
        private set

    fun go(route: Route) {
        if (route == current) return
        if (route == Route.Home) {
            popToHome()
            return
        }
        stack.add(route)
    }

    fun goSection(id: String) {
        popToHome()
        pendingSection = id
    }

    fun consumeSection() {
        pendingSection = null
    }

    fun back() {
        if (canGoBack) stack.removeAt(stack.lastIndex)
    }

    private fun popToHome() {
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
    }
}

val LocalNav: ProvidableCompositionLocal<CvNavState> =
    staticCompositionLocalOf { error("CvNavState not provided — the tree must be inside App()") }

// ponytail: one runnable check instead of a test module — the two non-obvious behaviours are
// "home is a floor, not a second entry" and "goSection pops back to home". Call from any target's
// main() while poking at nav.
internal fun navSelfCheck() {
    val nav = CvNavState()
    check(nav.current == Route.Home) { "starts home" }
    check(!nav.canGoBack) { "nothing to go back to at the floor" }

    nav.go(Route.ProjectDetail("mileway"))
    check(nav.current == Route.ProjectDetail("mileway")) { "push" }
    check(nav.canGoBack) { "can go back off a pushed route" }

    nav.go(Route.ProjectDetail("mileway"))
    nav.back()
    check(nav.current == Route.Home) { "re-navigating to the current route must not stack a duplicate" }

    nav.go(Route.Resume)
    nav.go(Route.Terminal)
    nav.go(Route.Home)
    check(nav.current == Route.Home && !nav.canGoBack) { "home pops the stack rather than pushing" }

    nav.go(Route.Terminal)
    nav.goSection("skills")
    check(nav.current == Route.Home) { "goSection returns to the homepage" }
    check(nav.pendingSection == "skills") { "goSection raises the request" }
    nav.consumeSection()
    check(nav.pendingSection == null) { "one-shot" }
}
