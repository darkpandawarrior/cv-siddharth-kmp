package com.siddharth.cv.shared

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The surfaces that ported: seven route kinds out of the React site's twenty-three addressable
 * routes (the `.tsx` files in `src/routes`, minus `__root` and the catch-all).
 *
 * The sixteen that are missing are NOT all blocked, and the README says which is which rather than
 * this comment guessing: two are blocked on the platform (`/blueprint`, `/pulse`), one is
 * deliberately dropped (`/playground`), and the remaining thirteen are simply not ported yet. They
 * are absent rather than stubbed because a `Route` entry with nothing behind it is a dead end, not
 * a roadmap.
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
 * Every route with a fixed path, in nav order. [Route.ProjectDetail] is absent because it is
 * parameterised: its pages come from `projects`.
 *
 * One list, read by the prerenderer's page list, the sitemap and the command palette, so a new
 * `Route` cannot ship a screen with no crawlable page and no way to reach it. The sealed interface
 * makes the `when`s exhaustive; this makes the enumerations single-sourced.
 */
val staticRoutes: List<Route> =
    listOf(Route.Home, Route.Resume, Route.Terminal, Route.Lab, Route.Forge, Route.Playground)

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

/**
 * The honest answer: null means "this build does not serve that path".
 *
 * Separate from [routeFromPath] because two callers need the difference. The homepage room wall and
 * the prerenderer both decide per link whether to navigate in-app or link out to the React site, and
 * a function that silently answers [Route.Home] cannot tell them apart, which is how three shipped
 * routes came to be labelled "web only" on their own home page.
 */
fun routeOrNull(path: String): Route? {
    val clean = path.substringBefore('?').substringBefore('#').trimEnd('/')
    return when {
        clean.isEmpty() -> Route.Home
        clean == "/resume" -> Route.Resume
        clean == "/terminal" -> Route.Terminal
        clean == "/lab" -> Route.Lab
        clean == "/forge" -> Route.Forge
        // "/compose" mirrors the React site's route name for this surface.
        clean == "/compose" -> Route.Playground
        clean.startsWith("/project/") -> Route.ProjectDetail(clean.removePrefix("/project/"))
        else -> null
    }
}

/**
 * Unknown path -> home rather than a 404 screen: the prerendered HTML shell is what a crawler sees,
 * and a human who mistypes is better served by the homepage. This is what `popstate` calls.
 */
fun routeFromPath(path: String): Route = routeOrNull(path) ?: Route.Home

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

    // routeOrNull is what the room wall and the prerenderer branch on: it must say "not here"
    // rather than "home", or every unported room silently claims to be a page on this build.
    check(routeOrNull("/nonsense") == null) { "unknown path is null, not home" }
    check(routeOrNull("/blueprint") == null) { "an unported React room is null" }
    check(routeOrNull("/compose") == Route.Playground) { "a shipped room is not null" }
    check(routeOrNull("") == Route.Home) { "the empty path is still home" }

    // staticRoutes feeds the prerenderer's page list, the sitemap and the palette. A duplicate path
    // collides three keys at once; a path that does not parse back is a page nothing can reach.
    val paths = staticRoutes.map { it.toPath() }
    check(paths.toSet().size == paths.size) { "duplicate path in staticRoutes: $paths" }
    staticRoutes.forEach { check(routeOrNull(it.toPath()) == it) { "staticRoutes round-trip ${it.toPath()}" } }
}
