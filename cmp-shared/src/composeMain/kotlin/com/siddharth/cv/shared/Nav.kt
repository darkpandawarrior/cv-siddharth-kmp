package com.siddharth.cv.shared

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.siddharth.cv.shared.anthology.AnthologyLayer

/**
 * The surfaces that ported: twenty route kinds out of the React site's twenty-three addressable
 * routes (the `.tsx` files in `src/routes`, minus `__root` and the catch-all).
 *
 * The three that are missing are the three that cannot be built here rather than the three nobody
 * got to, and the README says why for each: `/blueprint` and `/pulse` are blocked on the platform
 * (a DOM/WebGL widget cannot live inside a Compose canvas; a playhtml channel has no Kotlin
 * client), and `/playground` is deliberately dropped. Nothing is absent for want of effort any
 * more. They are absent rather than stubbed because a `Route` entry with nothing behind it is a
 * dead end, not a roadmap.
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

    /** The ninety-second page, for a reader who wants the claim and the proof and nothing else. */
    data object Hire : Route

    /** The Play Store fleet: every listing his commits reached, live and pulled. */
    data object Shipped : Route

    /** Weeb Central: the anime and manga ledger read as evidence rather than as a list. */
    data object Weeb : Route

    /** The ops board: what reports, what has gone stale, what is broken. */
    data object Ops : Route

    /** Loopdown: the field notes index, its series and its personified-bug cast. */
    data object Loopdown : Route

    /** The Ink: the doorway room, and the writing that predates the code. */
    data object Ink : Route

    /**
     * The anthology, carrying which layer it opens on.
     *
     * The only fixed-path route with a field, and it earns it: `/canon` links straight at a named
     * layer, and the React route encodes that in the URL the same way (`?layer=tellers`), with the
     * default normalised away. A parameterless entry would have made every one of those links land
     * on the first layer and quietly lose the destination.
     */
    data class Anthology(val layer: AnthologyLayer = AnthologyLayer.Form) : Route

    /** The canon: the count, the laws, the doctrine, and the line the spoilers sit below. */
    data object Canon : Route

    /** How the anthology was made: the blind audit, the pipeline, the spend, the receipts. */
    data object Making : Route

    /**
     * One printed piece, read as prose rather than as a photograph of a page.
     *
     * The second route carrying free text, and it is parameterised for the same reason
     * [ProjectDetail] is: its pages come from `printedPieces`, so it is not in [staticRoutes]. An
     * unknown slug resolves here rather than to [Home] on purpose. Every slug the React site serves
     * exists somewhere; this build carries one of that route's four corpora, so the honest answer
     * to an anthology slug is ReadScreen's own 404 naming what it has and offering the live site,
     * not a silent bounce to the front page.
     */
    data class Read(val slug: String) : Route

    /**
     * The magazine reader, carrying which edition and which page it opens on.
     *
     * Both fields are nullable and both defaults are dropped from the URL, the same normalisation
     * [Anthology] applies to its layer. `page == null` means "no page requested", which is the
     * difference between landing on the header and skipping it to show the cover. Neither field is
     * validated here: the screen normalises an unknown year to the newest edition and clamps the
     * page into range, so a stale pasted link still round-trips through this router unchanged.
     */
    data class Excelsior(val year: String? = null, val page: Int? = null) : Route

    /** The board: seven years of games across two platforms, mined. */
    data object Chess : Route

    /** The story map: the constellation, as a 2D force-directed graph. */
    data object Map : Route
}

/**
 * Every route with a fixed path, in nav order. [Route.ProjectDetail] and [Route.Read] are absent
 * because they are parameterised: their pages come from `projects` and `printedPieces`.
 * [Route.Excelsior] IS here, at its own default, because `/excelsior` is a real address on its own
 * and the query state only ever deep-links into a page of it.
 *
 * One list, read by the prerenderer's page list, the sitemap and the command palette, so a new
 * `Route` cannot ship a screen with no crawlable page and no way to reach it. The sealed interface
 * makes the `when`s exhaustive; this makes the enumerations single-sourced.
 */
val staticRoutes: List<Route> =
    listOf(
        Route.Home,
        Route.Resume,
        Route.Hire,
        Route.Shipped,
        Route.Ops,
        Route.Terminal,
        Route.Lab,
        Route.Forge,
        Route.Playground,
        Route.Weeb,
        Route.Chess,
        Route.Map,
        Route.Loopdown,
        Route.Ink,
        Route.Excelsior(),
        Route.Anthology(),
        Route.Canon,
        Route.Making,
    )

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

    /**
     * True when [current] arrived through [replace] rather than through a push, a pop or a reset.
     *
     * The web shell is the only reader, and it needs this because it cannot tell otherwise: it is
     * handed a route and nothing else, so without this flag a page turn inside `/excelsior` becomes
     * a `history.pushState` and the visitor's Back button has to be pressed 64 times to leave the
     * magazine. Set by exactly one operation and cleared by every other, so a stale `true` cannot
     * survive a real navigation.
     */
    var replacedInPlace: Boolean by mutableStateOf(false)
        private set

    fun go(route: Route) {
        replacedInPlace = false
        if (route == current) return
        if (route == Route.Home) {
            popToHome()
            return
        }
        stack.add(route)
    }

    /**
     * Same page, new address — the counterpart of the web's `navigate({ …, replace: true })`.
     *
     * The third operation, and the one the two above cannot express between them. A magazine page
     * turn is not somewhere you went, it is the same place showing you a different spread, so the
     * address has to follow it (or a shared link opens the wrong page) while the back stack must
     * not (or Back stops meaning "leave the magazine" and starts meaning "one page earlier",
     * 64 times). [go] gets the second wrong and [reset] destroys whatever you were reading before
     * you opened the room.
     *
     * A replace at the floor is deliberately a push. [Route.Home] is the floor of this stack and
     * never appears twice, so overwriting it would leave a stack whose bottom entry is not Home and
     * break the invariant every other operation here holds.
     */
    fun replace(route: Route) {
        if (route == current) return
        if (current == Route.Home) {
            go(route)
            return
        }
        replacedInPlace = true
        stack[stack.lastIndex] = route
    }

    fun goSection(id: String) {
        replacedInPlace = false
        popToHome()
        pendingSection = id
    }

    fun consumeSection() {
        pendingSection = null
    }

    fun back() {
        replacedInPlace = false
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
        replacedInPlace = false
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
// Detekt counts one point of cyclomatic complexity per `when` branch, so every exhaustive route
// table in this build now scores above its threshold of 20 purely for having twenty routes. The
// complexity is the exhaustiveness, which is the property the sealed interface exists to buy: the
// alternative it is asking for is a smaller `when` with an `else`, which is the exact defect this
// pass removed from FloatingChat.kt. Suppressed at the four route tables rather than raised in
// config/detekt/detekt.yml, so the exemption stays attached to its reason.
@Suppress("CyclomaticComplexMethod")
fun Route.toPath(): String = when (this) {
    Route.Home -> "/"
    Route.Resume -> "/resume"
    Route.Terminal -> "/terminal"
    Route.Lab -> "/lab"
    Route.Forge -> "/forge"
    Route.Playground -> "/compose"
    Route.Hire -> "/hire"
    Route.Shipped -> "/shipped"
    Route.Weeb -> "/weeb"
    Route.Ops -> "/ops"
    Route.Loopdown -> "/loopdown"
    Route.Ink -> "/ink"
    Route.Canon -> "/canon"
    Route.Making -> "/making"
    Route.Chess -> "/chess"
    Route.Map -> "/map"
    // The default layer is dropped from the URL, exactly as anthology.tsx drops it: one page,
    // one canonical address, and `?layer=form` normalising to `/anthology` on both builds.
    is Route.Anthology ->
        if (layer == AnthologyLayer.Form) "/anthology" else "/anthology?layer=${layer.key}"
    is Route.ProjectDetail -> "/project/$slug"
    is Route.Read -> "/read/$slug"
    // Same normalisation the anthology gets, one field wider: an absent field is absent from the
    // address, so `/excelsior` stays the one canonical page and `?year=&page=` is only ever the
    // deep link into a spread. Written in a fixed order so the URL is stable to compare.
    is Route.Excelsior ->
        listOfNotNull(year?.let { "year=$it" }, page?.let { "page=$it" })
            .joinToString("&")
            .let { if (it.isEmpty()) "/excelsior" else "/excelsior?$it" }
}

/**
 * The honest answer: null means "this build does not serve that path".
 *
 * Separate from [routeFromPath] because two callers need the difference. The homepage room wall and
 * the prerenderer both decide per link whether to navigate in-app or link out to the React site, and
 * a function that silently answers [Route.Home] cannot tell them apart, which is how three shipped
 * routes came to be labelled "web only" on their own home page.
 */
@Suppress("CyclomaticComplexMethod") // A route table. See the note on Route.toPath().
fun routeOrNull(path: String): Route? {
    val fragment = path.substringAfter('#', "")
    val noHash = path.substringBefore('#')
    val query = noHash.substringAfter('?', "")
    val clean = noHash.substringBefore('?').trimEnd('/')
    return when {
        clean.isEmpty() -> legacyInbound(fragment, query) ?: Route.Home
        clean == "/resume" -> Route.Resume
        clean == "/terminal" -> Route.Terminal
        clean == "/lab" -> Route.Lab
        clean == "/forge" -> Route.Forge
        // "/compose" mirrors the React site's route name for this surface.
        clean == "/compose" -> Route.Playground
        clean == "/hire" -> Route.Hire
        clean == "/shipped" -> Route.Shipped
        clean == "/weeb" -> Route.Weeb
        clean == "/ops" -> Route.Ops
        clean == "/loopdown" -> Route.Loopdown
        clean == "/ink" -> Route.Ink
        clean == "/anthology" -> Route.Anthology(anthologyLayer(query))
        clean == "/canon" -> Route.Canon
        clean == "/making" -> Route.Making
        clean == "/chess" -> Route.Chess
        clean == "/map" -> Route.Map
        // Neither field is checked against the corpus. `year` is a string because the corpus keys
        // editions by one, and a garbage `page` becomes "no page requested" rather than a throw:
        // the screen clamps into 1..pages itself, so the router never needs the data to answer.
        clean == "/excelsior" -> Route.Excelsior(query.param("year"), query.param("page")?.toIntOrNull())
        clean.startsWith("/project/") -> Route.ProjectDetail(clean.removePrefix("/project/"))
        // A bare `/read` is not a page on either build, so the trailing slash trim above is what
        // makes it null rather than a piece with an empty slug.
        clean.startsWith("/read/") -> Route.Read(clean.removePrefix("/read/"))
        else -> null
    }
}

/**
 * The addresses that predate this site's paths and are still out in the world.
 *
 * `HashCompat` in `src/routes/__root.tsx` is the React counterpart, and the shapes are its, not
 * invented here: `/#project/<slug>` and `/#terminal` are how every link shared before the router
 * migration is written, and `/?project=<slug>` exists because LinkedIn's Featured card strips the
 * fragment off a URL and keeps the query. The React repo has Playwright coverage for all three.
 * Without this they land on the homepage, which is the wrong page and looks like a broken link.
 *
 * Only consulted for the root path, which is the only shape any of them actually take. A hash on a
 * real page is a section anchor, and this router does not own scrolling — [CvNavState.goSection]
 * does, and the shell would have to raise it.
 *
 * NOT YET REACHABLE FROM THE WEB SHELL: `cmp-web`'s `main()` passes `window.location.pathname`,
 * which carries neither the query nor the fragment. That also costs the `?year=`/`?layer=` deep
 * links this router has always parsed, so the fix is one line there and it turns on all of it at
 * once. Written here because this is where the answer belongs, and it is self-checked below.
 */
private fun legacyInbound(fragment: String, query: String): Route? {
    if (fragment.startsWith("project/")) {
        return fragment.removePrefix("project/").ifEmpty { null }?.let { Route.ProjectDetail(it) }
    }
    query.param("project")?.let { return Route.ProjectDetail(it) }
    // `#terminal`, `#lab`, `#compose` — the old hash-router addresses. Asked of the real table
    // rather than of a second list of slugs, so a room that ports is reachable by both spellings
    // and a section anchor like `#projects` stays null because `/projects` is not a page.
    return if (fragment.isEmpty()) null else routeOrNull("/$fragment")
}

/**
 * One value out of a raw query string, or null. The whole of this router's query handling.
 *
 * No decoding, no repeated keys, no arrays: the three parameters this site uses (`layer`, `year`,
 * `page`) are a short enum key, a four-digit year and an integer, none of which can contain a
 * character that needs escaping. A `URLDecoder` would be a dependency (and a platform one) bought
 * for input that cannot occur. An empty value counts as absent, so `?year=` opens the default
 * edition rather than looking for an edition named "".
 */
private fun String.param(key: String): String? =
    split('&').firstOrNull { it.startsWith("$key=") }?.removePrefix("$key=")?.ifEmpty { null }

/**
 * `?layer=tellers` -> [AnthologyLayer.Tellers].
 *
 * Absent, unrecognised or misspelled is the default layer rather than a throw, which is what
 * anthology.tsx does with the same input: a shared link with a stale layer key still opens the
 * page it names instead of 404ing on a detail nobody typed on purpose.
 */
private fun anthologyLayer(query: String): AnthologyLayer =
    AnthologyLayer.entries.firstOrNull { it.key == query.param("layer") } ?: AnthologyLayer.Form

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
// LongMethod: a self-check is a list of assertions, and splitting it into four functions to satisfy
// a line count would hide which of them the build failed in. MagicNumber: it is all literals by
// construction, which is what an assertion is. Same accommodation shippedFormatSelfCheck already has.
@Suppress("LongMethod", "MagicNumber")
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

    // replace() is the page turn: the address moves, the stack does not. Walked at the depth
    // /excelsior actually runs at — one entry above home — because that is where the 64-entry
    // back stack this exists to prevent would have been built.
    nav.go(Route.Excelsior())
    repeat(4) { i -> nav.replace(Route.Excelsior("2021", i + 1)) }
    check(nav.current == Route.Excelsior("2021", 4)) { "replace lands on the route" }
    check(nav.replacedInPlace) { "and tells the web shell to replaceState rather than push" }
    nav.back()
    check(nav.current == Route.Home) { "four page turns left one entry, not four" }
    check(!nav.replacedInPlace) { "back clears the flag, or the shell replaces a real navigation" }

    nav.go(Route.Excelsior())
    nav.replace(Route.Excelsior("2021", 7))
    nav.go(Route.Resume)
    check(!nav.replacedInPlace) { "a push after a replace is still a push" }
    check(nav.canGoBack) { "the replaced entry is underneath it" }
    nav.back()
    check(nav.current == Route.Excelsior("2021", 7)) { "back off the push returns to the replaced spread" }

    // The floor is the one place a replace must not overwrite: Home is the bottom of every stack,
    // and a stack whose bottom is /excelsior has no way back to the front page.
    nav.reset(Route.Home)
    nav.replace(Route.Excelsior("2019", 3))
    check(nav.current == Route.Excelsior("2019", 3)) { "a replace at the floor still lands" }
    check(nav.canGoBack) { "but pushes, so home stays underneath it" }
    check(!nav.replacedInPlace) { "and is reported as the push it was" }

    nav.reset(Route.Excelsior("2021", 2))
    nav.replace(Route.Excelsior("2021", 2))
    check(!nav.replacedInPlace) { "replacing the current route is a no-op, not a history write" }
    nav.reset(Route.Home)

    // Path mapping must round-trip, or the URL bar and the router disagree after a refresh.
    listOf(
        Route.Home, Route.Resume, Route.Terminal, Route.Lab, Route.Forge, Route.Playground,
        Route.Hire, Route.Shipped, Route.Weeb, Route.Ops, Route.Loopdown, Route.Ink,
        Route.Anthology(), Route.Anthology(AnthologyLayer.Tellers), Route.Canon, Route.Making,
        Route.Chess, Route.Map, Route.ProjectDetail("mileway"), Route.Read("deadline"),
        // Every combination of the two optional fields, because each one is written into the URL
        // independently and a joiner that drops the wrong half round-trips as a different spread.
        Route.Excelsior(), Route.Excelsior("2021"), Route.Excelsior(page = 5),
        Route.Excelsior("2021", 5),
    ).forEach {
        check(routeFromPath(it.toPath()) == it) { "round-trip ${it.toPath()}" }
    }

    // The one query string this router reads. Each of these is a link somebody can paste.
    check(Route.Anthology().toPath() == "/anthology") { "the default layer is not written into the URL" }
    check(Route.Anthology(AnthologyLayer.Map).toPath() == "/anthology?layer=map") { "a named layer is" }
    check(routeOrNull("/anthology?layer=nonsense") == Route.Anthology()) {
        "an unrecognised layer opens the page rather than failing it"
    }
    check(routeOrNull("/anthology?utm=x&layer=tellers") == Route.Anthology(AnthologyLayer.Tellers)) {
        "the layer is found beside other query parameters"
    }
    check(routeOrNull("/anthology?layer=map#worlds") == Route.Anthology(AnthologyLayer.Map)) {
        "a fragment is not part of the query"
    }
    check(routeFromPath("/project/mileway/") == Route.ProjectDetail("mileway")) { "trailing slash" }
    check(routeFromPath("/project/mileway?utm=x") == Route.ProjectDetail("mileway")) { "query stripped" }
    check(routeFromPath("/nonsense") == Route.Home) { "unknown path falls back home" }

    // The second free-text route. Same trailing-slash and query handling as /project, and one
    // difference worth pinning: an unknown slug is a Read, not a Home. ReadScreen's own 404 names
    // the corpus this build carries and offers the live site, which is a better answer for a slug
    // that really does exist on cv-siddharth.vercel.app than a silent bounce to the front page.
    check(routeFromPath("/read/deadline/") == Route.Read("deadline")) { "trailing slash" }
    check(routeFromPath("/read/deadline?utm=x") == Route.Read("deadline")) { "query stripped" }
    check(routeOrNull("/read/nonsense") == Route.Read("nonsense")) { "an unknown slug reaches the reader's 404" }
    check(routeOrNull("/read") == null) { "a bare /read is not a page on either build" }
    check(routeOrNull("/read/") == null) { "nor is it with a trailing slash, which is the same address" }

    // The reader's query state. Each of these is an address that gets pasted into an application.
    check(Route.Excelsior().toPath() == "/excelsior") { "an absent field is absent from the URL" }
    check(Route.Excelsior("2021", 5).toPath() == "/excelsior?year=2021&page=5") { "both fields, in order" }
    check(Route.Excelsior(page = 5).toPath() == "/excelsior?page=5") { "page alone does not invent a year" }
    check(routeOrNull("/excelsior?page=5&year=2021") == Route.Excelsior("2021", 5)) {
        "the fields are found in either written order"
    }
    check(routeOrNull("/excelsior?utm=x&year=2019") == Route.Excelsior("2019")) {
        "a field is found beside a parameter this router does not read"
    }
    check(routeOrNull("/excelsior?page=nonsense") == Route.Excelsior()) {
        "a page that is not a number is no page requested, not a crash"
    }
    check(routeOrNull("/excelsior?year=1999&page=9999") == Route.Excelsior("1999", 9999)) {
        "the router does not validate against the corpus; the screen clamps"
    }
    check(routeOrNull("/excelsior?year=") == Route.Excelsior()) { "an empty value is an absent one" }

    // routeOrNull is what the room wall and the prerenderer branch on: it must say "not here"
    // rather than "home", or every unported room silently claims to be a page on this build.
    check(routeOrNull("/nonsense") == null) { "unknown path is null, not home" }
    check(routeOrNull("/blueprint") == null) { "a blocked React room is null" }
    check(routeOrNull("/pulse") == null) { "so is the other one" }
    check(routeOrNull("/playground") == null) { "and so is the one that was dropped on purpose" }
    check(routeOrNull("/map") == Route.Map) { "the last four rooms ported; /map is no longer null" }
    check(routeOrNull("/hire") == Route.Hire) { "a room ported in this pass is not null" }
    check(routeOrNull("/compose") == Route.Playground) { "a shipped room is not null" }
    check(routeOrNull("") == Route.Home) { "the empty path is still home" }

    // The legacy inbound addresses. Every one of these is a link that already exists somewhere he
    // does not control — a LinkedIn Featured card, an old share, a pasted hash URL.
    check(routeOrNull("/#project/mileway") == Route.ProjectDetail("mileway")) { "the old hash project link" }
    check(routeOrNull("/?project=kursi") == Route.ProjectDetail("kursi")) { "LinkedIn Featured keeps the query" }
    check(routeOrNull("/#terminal") == Route.Terminal) { "an old hash route resolves to its page" }
    check(routeOrNull("/#compose") == Route.Playground) { "including one whose slug is not its name" }
    check(routeOrNull("/#projects") == Route.Home) { "a homepage section anchor is the homepage, not a route" }
    check(routeOrNull("/#pulse") == Route.Home) { "an unported room's hash falls home rather than 404ing" }
    check(routeOrNull("/#project/") == Route.Home) { "an empty slug is not a project page" }
    check(routeOrNull("/?project=") == Route.Home) { "nor is an empty query value" }
    check(routeOrNull("/lab#top") == Route.Lab) { "a fragment on a real page does not redirect off it" }

    // staticRoutes feeds the prerenderer's page list, the sitemap and the palette. A duplicate path
    // collides three keys at once; a path that does not parse back is a page nothing can reach.
    val paths = staticRoutes.map { it.toPath() }
    check(paths.toSet().size == paths.size) { "duplicate path in staticRoutes: $paths" }
    staticRoutes.forEach { check(routeOrNull(it.toPath()) == it) { "staticRoutes round-trip ${it.toPath()}" } }
}
