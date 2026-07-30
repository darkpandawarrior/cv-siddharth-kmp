package com.siddharth.cv.shared

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The surfaces that ported. The React site has thirteen routes; the ones missing here are WebGL,
 * tldraw or Leaflet surfaces documented as dropped/deferred in the README rather than stubbed — a
 * `Route` entry with nothing behind it is a dead end, not a roadmap.
 *
 * Being a sealed interface is load-bearing: every `when` over a route is exhaustive, so adding an
 * entry here fails the build in each place that has to handle it (the nav host, the chat's
 * route-aware greeting, the prerenderer's page list, the sitemap priorities). A string-based router
 * would have silently shipped a blank screen and a wrong greeting instead.
 */
sealed interface Route {
    data object Home : Route

    /** The only route carrying free text. An unknown slug resolves to ProjectDetailScreen's 404. */
    data class ProjectDetail(val slug: String) : Route

    data object Resume : Route

    data object Terminal : Route

    /** The lab bench — the ported experiments from `src/labs/`. */
    data object Lab : Route

    /** The particle forge. */
    data object Forge : Route

    /** The Compose playground — interpreted subset, rendered with real composables. */
    data object Playground : Route
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

    /**
     * Replace the whole stack — the browser Back button's counterpart.
     *
     * Distinct from [go] because a `popstate` is the browser telling us where we now *are*, not a
     * request to navigate. Routing it through [go] would push a second entry and fight the history
     * API for control of the stack.
     */
    fun reset(route: Route) {
        popToHome()
        if (route != Route.Home) stack.add(route)
    }

    private fun popToHome() {
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
    }
}

/**
 * Route <-> URL path, kept here rather than in the web module so the mapping is testable on every
 * target and can't drift from [Route] itself. Pure string work — no browser API — so this stays
 * legal in composeMain. The web shell owns the actual `history.pushState` call.
 *
 * Paths mirror the React site exactly, so a link that works on cv-siddharth.vercel.app works here.
 */
fun Route.toPath(): String = when (this) {
    Route.Home -> "/"
    Route.Resume -> "/resume"
    Route.Terminal -> "/terminal"
    Route.Lab -> "/lab"
    Route.Forge -> "/forge"
    Route.Playground -> "/compose"
    is Route.ProjectDetail -> "/project/$slug"
}

fun routeFromPath(path: String): Route {
    val clean = path.substringBefore('?').substringBefore('#').trimEnd('/')
    return when {
        clean.isEmpty() -> Route.Home
        clean == "/resume" || clean == "/resume/" -> Route.Resume
        clean == "/terminal" -> Route.Terminal
        clean == "/lab" -> Route.Lab
        clean == "/forge" -> Route.Forge
        // "/compose" mirrors the React site's route name for this surface.
        clean == "/compose" -> Route.Playground
        clean.startsWith("/project/") -> Route.ProjectDetail(clean.removePrefix("/project/"))
        // Unknown path -> home rather than a 404 screen: the prerendered HTML shell is what a
        // crawler sees, and a human who mistypes is better served by the homepage.
        else -> Route.Home
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

    // reset() is popstate's path: it must land on the route without growing the stack past it.
    nav.reset(Route.ProjectDetail("kursi"))
    check(nav.current == Route.ProjectDetail("kursi")) { "reset lands on the route" }
    check(nav.canGoBack) { "reset keeps home underneath so back still means home" }
    nav.reset(Route.Home)
    check(nav.current == Route.Home && !nav.canGoBack) { "reset home collapses to the floor" }

    // Path mapping must round-trip, or the URL bar and the router disagree after a refresh.
    listOf(
        Route.Home, Route.Resume, Route.Terminal, Route.Lab, Route.Forge, Route.Playground,
        Route.ProjectDetail("mileway"),
    ).forEach {
        check(routeFromPath(it.toPath()) == it) { "round-trip ${it.toPath()}" }
    }
    check(routeFromPath("/project/mileway/") == Route.ProjectDetail("mileway")) { "trailing slash" }
    check(routeFromPath("/project/mileway?utm=x") == Route.ProjectDetail("mileway")) { "query stripped" }
    check(routeFromPath("/nonsense") == Route.Home) { "unknown path falls back home" }
}
