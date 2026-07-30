package com.siddharth.cv.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.siddharth.cv.shared.App
import com.siddharth.cv.shared.CvNavState
import com.siddharth.cv.shared.Route
import com.siddharth.cv.shared.routeFromPath
import com.siddharth.cv.shared.toPath
import kotlinx.browser.document
import kotlinx.browser.window

/**
 * The web shell. Three jobs beyond calling [App]: pick the mount point, keep the URL bar honest,
 * and get the no-JS content out of the way once Compose has painted.
 *
 * **Why a dedicated `#compose` div and not `document.body`.** Compose clears whatever container it
 * mounts into. Mounting on `body` therefore deletes every sibling — including the `#seo` block that
 * is the only crawlable, selectable, findable text this build ships. Mounting into its own div means
 * Compose owns that subtree and nothing else, so `#seo` survives boot and a crawler (or a reader
 * with JS off, or a browser too old for wasm) still gets real HTML.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val nav = CvNavState()

    // Deep links work: /resume, /terminal and /project/<slug> all boot straight into that route
    // rather than landing home and making the visitor navigate again.
    nav.reset(routeFromPath(window.location.pathname))

    // The browser Back/Forward buttons. reset() rather than go() — popstate reports where we now
    // are, so pushing another entry here would fight the history stack.
    window.addEventListener("popstate") {
        nav.reset(routeFromPath(window.location.pathname))
    }

    val mount = document.getElementById("compose") ?: document.body!!
    ComposeViewport(mount) {
        App(
            nav = nav,
            // Called on every in-app navigation. pushState keeps the address bar and the Back
            // button in sync with a router that the browser otherwise cannot see at all.
            onRouteChanged = { route -> syncUrl(route) },
        )
    }

    // Compose has mounted; the boot text and the SEO block have done their jobs for a JS-capable
    // visitor. Hide rather than remove, so `view-source` and any crawler that reads the served
    // HTML still find the content.
    document.getElementById("boot")?.setAttribute("style", "display:none")
    document.getElementById("seo")?.setAttribute("style", "display:none")
}

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun syncUrl(route: Route) {
    val path = route.toPath()
    if (window.location.pathname == path) return
    window.history.pushState(null, "", path)
}
