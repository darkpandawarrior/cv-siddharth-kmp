package com.siddharth.cv.shared.prerender

import com.siddharth.cv.shared.Route
import com.siddharth.cv.shared.chat.floatingChatSelfCheck
import com.siddharth.cv.shared.detail.mermaidLayoutSelfCheck
import com.siddharth.cv.shared.detail.mermaidParseSelfCheck
import com.siddharth.cv.shared.detail.resumeHtmlSelfCheck
import com.siddharth.cv.shared.forge.forgeSelfCheck
import com.siddharth.cv.shared.labs.LabGroup
import com.siddharth.cv.shared.labs.cvLabs
import com.siddharth.cv.shared.labs.labScreenSelfCheck
import com.siddharth.cv.shared.labs.labsSelfCheck
import com.siddharth.cv.shared.palette.paletteSelfCheck
import com.siddharth.cv.shared.playground.composeInterpreterSelfCheck
import com.siddharth.cv.shared.playground.composePresets
import com.siddharth.cv.shared.playground.composeRenderSelfCheck
import com.siddharth.cv.shared.playground.playgroundScreenSelfCheck
import com.siddharth.cv.shared.navSelfCheck
import com.siddharth.cv.shared.routeOrNull
import com.siddharth.cv.shared.staticRoutes
import com.siddharth.cv.shared.playground.themeLabSelfCheck
import com.siddharth.cv.shared.anthology.anthologySelfCheck
import com.siddharth.cv.shared.anthology.makingSelfCheck
import com.siddharth.cv.shared.data.CvGallery
import com.siddharth.cv.shared.data.Experience
import com.siddharth.cv.shared.data.Project
import com.siddharth.cv.shared.data.SkillGroup
import com.siddharth.cv.shared.data.caseStudies
import com.siddharth.cv.shared.data.competencies
import com.siddharth.cv.shared.data.education
import com.siddharth.cv.shared.data.experience
import com.siddharth.cv.shared.data.languages
import com.siddharth.cv.shared.data.metrics
import com.siddharth.cv.shared.data.nextProject
import com.siddharth.cv.shared.data.openSource
import com.siddharth.cv.shared.data.profile
import com.siddharth.cv.shared.data.projects
import com.siddharth.cv.shared.data.recentGrowth
import com.siddharth.cv.shared.data.resumeSkills
import com.siddharth.cv.shared.data.sharedFoundation
import com.siddharth.cv.shared.data.generated.anthology
import com.siddharth.cv.shared.data.generated.anthologyEntries
import com.siddharth.cv.shared.data.generated.auditMethod
import com.siddharth.cv.shared.data.generated.boardArc
import com.siddharth.cv.shared.data.generated.boardProfiles
import com.siddharth.cv.shared.data.generated.countLedger
import com.siddharth.cv.shared.data.generated.fleetStats
import com.siddharth.cv.shared.data.generated.liveClients
import com.siddharth.cv.shared.data.generated.namedThirteen
import com.siddharth.cv.shared.data.generated.opsDrift
import com.siddharth.cv.shared.data.generated.opsLeverage
import com.siddharth.cv.shared.data.generated.opsPerimeter
import com.siddharth.cv.shared.data.generated.pastClients
import com.siddharth.cv.shared.data.generated.pipelineStages
import com.siddharth.cv.shared.data.generated.receipts
import com.siddharth.cv.shared.data.generated.renderingDoctrine
import com.siddharth.cv.shared.data.generated.rigConstraints
import com.siddharth.cv.shared.data.generated.seasonCanon
import com.siddharth.cv.shared.data.generated.siblingSeries
import com.siddharth.cv.shared.data.generated.societies
import com.siddharth.cv.shared.data.generated.spend
import com.siddharth.cv.shared.data.generated.storeGeneratedAt
import com.siddharth.cv.shared.data.generated.unfiledPieces
import com.siddharth.cv.shared.data.generated.voiceConstraints
import com.siddharth.cv.shared.data.generated.weeb
import com.siddharth.cv.shared.data.generated.writingArchive
import com.siddharth.cv.shared.data.generated.writingCast
import com.siddharth.cv.shared.data.generated.writingLessons
import com.siddharth.cv.shared.data.generated.writingSeries
import com.siddharth.cv.shared.anthology.money
import com.siddharth.cv.shared.data.generated.loopdownOrigin
import com.siddharth.cv.shared.data.generated.opsGeneratedAt
import com.siddharth.cv.shared.data.generated.retroactionStandard
import com.siddharth.cv.shared.data.generated.rigConstraintsNote
import com.siddharth.cv.shared.data.generated.standardIntervals
import com.siddharth.cv.shared.data.generated.storeApps
import com.siddharth.cv.shared.data.generated.tether
import com.siddharth.cv.shared.data.generated.tetherDoctrine
import com.siddharth.cv.shared.data.generated.lastShipped
import com.siddharth.cv.shared.data.siteRooms
import com.siddharth.cv.shared.data.skills
import com.siddharth.cv.shared.shipped.shippedFormatSelfCheck
import com.siddharth.cv.shared.writing.titleize
import com.siddharth.cv.shared.terminal.TerminalEngine
import com.siddharth.cv.shared.toPath
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.File

/**
 * Build-time static HTML for every route, generated from the same Kotlin data the Compose app
 * renders.
 *
 * **Why this exists.** A Kotlin/Wasm canvas app is, to a crawler, one empty `<div>`. CMP 1.12 does
 * emit an `#cmp_a11y_root` mirror, but it lives in a shadow root that crawlers generally do not
 * enter — and it only exists *after* 2.6 MB of wasm downloads and paints. The hand-written `#seo`
 * block in `cmp-web/src/wasmJsMain/resources/index.html` was the floor: one page, four routes'
 * worth of prose, maintained by hand and already drifting from the data. This is the ceiling —
 * one file per route, every sentence read out of `data/`, so the static layer and the rendered app
 * cannot disagree without someone editing `data/` and getting both at once.
 *
 * **Why jvmMain and not a script.** The data classes are Kotlin. Any other generator (Node, a
 * shell heredoc, a `.md` template) would have to re-state the content in a second language, which
 * is the exact drift this replaces. `jvmMain` sees `composeMain` through `skikoMain`, so
 * `profile`, `projects`, `TerminalEngine` and `Route.toPath()` are all directly importable, and
 * `java.*` is legal here because this JAR never ships to wasm.
 *
 * **How the two layers cohabit.** Every emitted page carries the same `#compose` mount point,
 * `#boot` line and module script tag as the hand-written `index.html`, so the wasm app boots over
 * a prerendered page exactly as it does over the template. `Main.kt` hides `#boot` and `#seo` once
 * Compose has painted — hides, not removes, so `view-source:` and any crawler reading the served
 * bytes still find the text.
 *
 * Run it: `Prerender <outputDir> [originUrl]`. The integrator wires a Gradle `JavaExec` task; see
 * the note at the bottom of this file.
 */
object Prerender {

    @JvmStatic
    fun main(args: Array<String>) {
        selfCheck()

        val outDir = File(
            args.firstOrNull() ?: error("usage: Prerender <outputDir> [originUrl]"),
        )
        // The canonical origin cannot be inferred from the repo — nothing here records where the
        // wasm bundle is deployed. Passing it in keeps the generator honest instead of baking a
        // guess into every <link rel="canonical"> on the site.
        val origin = (args.getOrNull(1) ?: System.getenv("CV_SITE_ORIGIN") ?: DEFAULT_ORIGIN)
            .trimEnd('/')

        val routes = prerenderRoutes

        routes.forEach { route ->
            val file = route.outputFile(outDir)
            file.parentFile?.mkdirs()
            file.writeText(render(route, origin))
        }

        File(outDir, "sitemap.xml").writeText(sitemap(routes, origin))
        File(outDir, "robots.txt").writeText("User-agent: *\nAllow: /\n\nSitemap: $origin/sitemap.xml\n")

        println("prerender: ${routes.size} pages + sitemap.xml + robots.txt -> ${outDir.absolutePath} (origin $origin)")
    }
}

/**
 * Every page this build emits: Nav.kt's `staticRoutes`, never re-typed here, plus one page per
 * project that actually has a detail block. A route added to Nav.kt gets a page and a sitemap entry
 * without anyone remembering this file exists; a project without a detail block is skipped for the
 * same reason the command palette skips it, an indexable page with nothing on it being worse than
 * no page.
 *
 * A val rather than a line inside `main` so [readmeSelfCheck] counts the same list that ships.
 */
private val prerenderRoutes: List<Route> =
    staticRoutes + projects.filter { it.detail != null }.map { Route.ProjectDetail(it.slug) }

/**
 * Fallback origin. Overridden by `args[1]` or `CV_SITE_ORIGIN`; only used so a bare local run
 * produces something inspectable rather than throwing.
 */
private const val DEFAULT_ORIGIN = "https://cv-siddharth-kmp.vercel.app"

/** The React original, where the rooms this build does not serve still live. See [exploreSection]. */
private const val REACT_SITE = "https://cv-siddharth.vercel.app"

private val json = Json

// ---------------------------------------------------------------------------------------------
// Route -> file, route -> page
// ---------------------------------------------------------------------------------------------

/**
 * `/` -> `index.html`, `/resume` -> `resume/index.html`, `/project/x` -> `project/x/index.html`.
 *
 * Directory-per-route rather than `resume.html`, because the app's own URLs (from
 * [Route.toPath], which the web shell pushes into the address bar) have no extension — a static
 * host has to be able to serve the *exact* path the router produces or a refresh 404s.
 */
private fun Route.outputFile(root: File): File {
    val path = toPath().trim('/')
    return if (path.isEmpty()) File(root, "index.html") else File(root, "$path/index.html")
}

/**
 * Route -> page, and nothing else. Every branch is one call, so this stays a table a reader can
 * check against `Nav.kt` at a glance rather than a function they have to scroll. The head copy each
 * page needs sits beside the body it belongs to, further down.
 *
 * `page` defaults to the website type, no share image and the Person record, which is what most of
 * these are; only the pages that genuinely differ say so.
 */
private fun render(route: Route, origin: String): String = when (route) {
    Route.Home ->
        page(route, origin, "${profile.name} — ${profile.title}", profile.intro, homeBody(), "profile", HERO)
    Route.Resume ->
        page(route, origin, "Résumé — ${profile.name} · ${profile.title}", profile.summary, resumeBody(), "profile", HERO)
    Route.Hire ->
        page(route, origin, "Hire me — ${profile.name} · ${profile.title}", HIRE_DESCRIPTION, hireBody(), "profile", HERO)
    Route.Shipped ->
        page(route, origin, "Shipped — ${profile.name}", SHIPPED_DESCRIPTION, shippedBody(), image = HERO)
    Route.Terminal ->
        page(route, origin, "Terminal — ${profile.name}", TERMINAL_DESCRIPTION, terminalBody(), image = HERO)
    Route.Lab ->
        page(route, origin, "Lab bench — ${profile.name}", LAB_DESCRIPTION, labBody(), image = HERO)
    Route.Playground ->
        page(route, origin, "Compose playground — ${profile.name}", PLAYGROUND_DESCRIPTION, playgroundBody(), image = HERO)
    Route.Forge ->
        page(route, origin, "Particle forge — ${profile.name}", FORGE_DESCRIPTION, forgeBody(), image = HERO)
    Route.Ops ->
        page(route, origin, "The ops board — ${profile.name}", OPS_DESCRIPTION, opsBody())
    Route.Weeb ->
        page(route, origin, "Weeb Central — ${profile.name}", WEEB_DESCRIPTION, weebBody())
    Route.Loopdown ->
        page(route, origin, "Loopdown — ${profile.name}", LOOPDOWN_DESCRIPTION, loopdownBody())
    Route.Ink ->
        page(route, origin, "The Ink — ${profile.name}", boardArc, inkBody())
    is Route.Anthology ->
        page(route, origin, "${anthology.title} — ${profile.name}", anthology.tagline, anthologyBody())
    Route.Canon ->
        page(route, origin, "The Canon — ${anthology.title}", CANON_DESCRIPTION, canonBody())
    Route.Making ->
        page(route, origin, "The Making — ${anthology.title}", MAKING_DESCRIPTION, makingBody())

    is Route.ProjectDetail -> {
        // Only slugs taken from `projects` reach here (main() builds the list), so this is a
        // programming error rather than a 404 path.
        val project = projects.first { it.slug == route.slug }
        page(
            route = route,
            origin = origin,
            title = "${project.name} — ${profile.name}",
            description = project.detail?.overview ?: project.description,
            body = projectBody(project),
            ogType = "article",
            image = CvGallery.hero(project.slug),
            jsonLd = projectLd(project, origin),
        )
    }
}

/**
 * The one share image this build has for a page that is not a project: a real screenshot of a real
 * shipped app. The nine rooms added in the second pass deliberately take none. The React route
 * declares none either (`roomHead` in src/lib/routeHead.ts emits title, description and canonical
 * only), and the artwork those pages carry on the web is per-entry plates and scanned magazine
 * pages that this port does not ship. A project screenshot pasted onto /canon would be a card that
 * misdescribes the page, which is worse than a card with no picture in it.
 */
private val HERO: String? = CvGallery.hero("mileway")

// ---------------------------------------------------------------------------------------------
// The shell — must stay byte-compatible in structure with cmp-web/.../index.html
// ---------------------------------------------------------------------------------------------

/**
 * The page skeleton. The `#compose` div, the `#boot` line, the `noscript` override and the module
 * script tag are copied from `cmp-web/src/wasmJsMain/resources/index.html` and must stay in step
 * with it — `Main.kt` looks up all three ids by name.
 *
 * **One deliberate difference: the script `src` is `/cmpWeb.js`, absolute, not relative.** The
 * template is only ever served from `/`, so a bare `cmpWeb.js` resolves correctly there. A
 * prerendered page lives at `/resume/index.html`, where the same relative src would resolve to
 * `/resume/cmpWeb.js` and 404 — the wasm app would silently never boot and the visitor would be
 * left staring at the static layer. Webpack resolves the `.wasm` sibling from the script's own
 * URL, so making the script root-absolute fixes the chunk too.
 */
private fun page(
    route: Route,
    origin: String,
    title: String,
    description: String,
    body: String,
    ogType: String = "website",
    image: String? = null,
    jsonLd: String = personLd(origin, profile.intro),
): String {
    val path = route.toPath()
    val canonical = if (path == "/") "$origin/" else "$origin$path"
    val desc = clamp(description)

    return buildString {
        append(
            """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <!-- REQUIRED on every nested route, not cosmetic. Compose Resources builds its
                     font URLs relative to the document ("composeResources/.../space_grotesk_
                     regular.ttf"), so on /project/mileway the browser asks for
                     /project/composeResources/... and 404s all five faces — the page then renders
                     in the system sans with none of the site's type. Root-absolute is not an
                     option there; the paths come from generated resource accessors. One <base>
                     fixes every relative URL at once. Every link this file emits is already
                     root-absolute or fully qualified, so nothing else moves.
                     Integrator-added during wiring; the bug was invisible until prerendering made
                     these routes servable at all. -->
                <base href="/">
                <title>${esc(title)}</title>
                <meta name="description" content="${esc(desc)}">
                <meta name="author" content="${esc(profile.name)}">
                <link rel="canonical" href="${esc(canonical)}">
                <meta property="og:site_name" content="${esc(profile.name)} — ${esc(profile.title)}">
                <meta property="og:title" content="${esc(title)}">
                <meta property="og:description" content="${esc(desc)}">
                <meta property="og:type" content="$ogType">
                <meta property="og:url" content="${esc(canonical)}">
                <meta name="twitter:card" content="${if (image == null) "summary" else "summary_large_image"}">
                <meta name="twitter:title" content="${esc(title)}">
                <meta name="twitter:description" content="${esc(desc)}">
            """.trimIndent(),
        )
        if (image != null) {
            append("\n    <meta property=\"og:image\" content=\"${esc(image)}\">")
            append("\n    <meta property=\"og:image:alt\" content=\"${esc(title)}\">")
            append("\n    <meta name=\"twitter:image\" content=\"${esc(image)}\">")
        }
        append(
            """

            <link rel="icon" href="data:image/svg+xml,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 16 16'><circle cx='8' cy='8' r='7' fill='%233ddc84'/></svg>">
            <script type="application/ld+json">$jsonLd</script>
            <style>
            """.trimIndent(),
        )
        // Appended outside the trimIndent block on purpose: trimIndent runs on the *interpolated*
        // string, so splicing an already-dedented multi-line constant into one would reset the
        // common indent to zero and leave the whole head raggedly indented.
        append('\n').append(PAGE_CSS).append('\n')
        append(
            """
            </style>
            </head>
            <body>
                <div id="compose"></div>
                <div id="boot">loading <b>compose multiplatform</b> · kotlin/wasm</div>

                <noscript><style>#boot{display:none}</style></noscript>

                <!-- GENERATED by cmp-shared jvmMain Prerender.kt from data/CvProfileData.kt and
                     data/CvProjectData.kt. Do not hand-edit: edit the Kotlin data and re-run, or
                     the static layer and the Compose app start telling different stories. -->
                <div id="seo">
            """.trimIndent(),
        )
        append('\n')
        append(siteNav(route))
        append(body)
        append(
            """
                </div>

                <!-- type="module" is REQUIRED once Ktor is on the classpath: kotlinx-io's wasm glue
                     emits `import.meta`, a syntax error in a classic script that kills the bundle
                     before main() runs. Root-absolute src so nested routes resolve it — see the
                     KDoc on Prerender.page. -->
                <script type="module" src="/cmpWeb.js"></script>
            </body>
            </html>
            """.trimIndent(),
        )
        append('\n')
    }
}

/**
 * Copied from the template, plus the handful of rules the richer generated content needs.
 * Deliberately inline: the static layer must be complete in one round-trip, before any stylesheet
 * or the wasm bundle lands.
 */
private val PAGE_CSS = """
    /* --color-void from the source site's @theme. Set on html so the skiko payload downloads
       over the site's own ground instead of a white flash. */
    html, body { margin: 0; padding: 0; height: 100%; background: #05070a; color: #e8efe9;
                 font-family: "Space Grotesk", system-ui, sans-serif; }
    #compose { position: fixed; inset: 0; }
    #boot {
        position: fixed; inset: 0; display: flex; align-items: center; justify-content: center;
        font: 400 13px/1.6 ui-monospace, monospace; color: #8b909a;
        letter-spacing: 0.08em; text-transform: uppercase; pointer-events: none;
    }
    #boot b { color: #3ddc84; font-weight: 400; }
    /* The crawlable layer. Visible until Compose paints (main() hides it), so a crawler, a reader
       with JS disabled, and any browser without wasm all get real text. It is a SIBLING of
       #compose, never a child — Compose clears its own container on mount. */
    #seo { max-width: 46rem; margin: 0 auto; padding: 4rem 1.5rem; line-height: 1.65; }
    #seo h1 { font-size: 2rem; margin: 0 0 .25rem; }
    #seo h2 { font-size: 1.1rem; margin: 2rem 0 .5rem; color: #3ddc84; }
    #seo h3 { font-size: .98rem; margin: 1.4rem 0 .3rem; }
    #seo p { margin: .5rem 0; }
    #seo ul { margin: .5rem 0; padding-left: 1.15rem; }
    #seo li { margin: .2rem 0; }
    #seo a { color: #3ddc84; }
    #seo .muted { color: #8b909a; }
    #seo .lead { font-size: 1.05rem; }
    #seo nav { font: 400 12px/2 ui-monospace, monospace; margin-bottom: 2rem; }
    #seo nav a { margin-right: .9rem; white-space: nowrap; }
    #seo pre { overflow-x: auto; font: 400 12px/1.55 ui-monospace, monospace; color: #b9c0b9; }
""".trimIndent()

/**
 * Every route linked from every page. This is the single highest-value thing on the page for
 * indexability: without it a crawler that lands on one project page has no path to the other
 * twenty, because the app's own navigation is canvas clicks it cannot see.
 *
 * Walks [prerenderRoutes] rather than a second hand-typed list. That list used to be typed here,
 * and it was already two kinds of wrong: it had never been given `/compose`'s neighbours, and it
 * linked every project rather than the ones with a detail block, so `/project/portfolio` was
 * advertised on twelve pages and served on none. Deriving it means a route added to Nav.kt is
 * linked from every page for free, and a page that is not emitted cannot be linked.
 */
private fun siteNav(current: Route): String = buildString {
    append("    <nav aria-label=\"Site\">\n")
    prerenderRoutes.forEach { route ->
        val label = route.navLabel()
        if (route == current) {
            append("      <span class=\"muted\" aria-current=\"page\">${esc(label)}</span>\n")
        } else {
            append("      <a href=\"${esc(route.toPath())}\">${esc(label)}</a>\n")
        }
    }
    append("    </nav>\n")
}

/**
 * The one-word name of a route in the site nav. Short on purpose: this strip carries twenty-one
 * links on every page and the descriptive wording belongs in the `<title>` and the palette, not in
 * a mono nav rule. Exhaustive, so a new route cannot ship without being named here.
 */
private fun Route.navLabel(): String = when (this) {
    Route.Home -> "Home"
    Route.Resume -> "Résumé"
    Route.Terminal -> "Terminal"
    Route.Lab -> "Lab"
    Route.Playground -> "Playground"
    Route.Forge -> "Forge"
    Route.Hire -> "Hire"
    Route.Shipped -> "Shipped"
    Route.Ops -> "Ops"
    Route.Weeb -> "Weeb"
    Route.Loopdown -> "Loopdown"
    Route.Ink -> "Ink"
    is Route.Anthology -> "Anthology"
    Route.Canon -> "Canon"
    Route.Making -> "Making"
    is Route.ProjectDetail -> slug
}

// ---------------------------------------------------------------------------------------------
// Route bodies
// ---------------------------------------------------------------------------------------------

/** Section ids mirror `homeSections` in home/HomeScreen.kt, so `/#work` means the same thing. */
private fun homeBody(): String = buildString {
    h1(profile.name)
    p("${profile.title} · ${profile.location}", cls = "muted")
    p(profile.tagline, cls = "lead")
    p(profile.intro)
    ul(metrics.map { "<b>${esc(it.value)}</b> — ${esc(it.label)}. ${esc(it.detail)}" }, raw = true)

    h2("Selected work", id = "work")
    caseStudies.forEach { cs ->
        h3(cs.title)
        p("${cs.metric} · ${cs.tags.joinToString(" · ")}", cls = "muted")
        p(cs.summary)
        p("<b>Problem.</b> ${esc(cs.problem)}", raw = true)
        ul(cs.approach)
        p("<b>Outcome.</b> ${esc(cs.outcome)}", raw = true)
    }

    h2("Projects", id = "projects")
    projects.forEach { pr ->
        h3("<a href=\"/project/${esc(pr.slug)}\">${esc(pr.name)}</a>", raw = true)
        p(pr.status, cls = "muted")
        p(pr.tagline)
        p(pr.description)
        p("Stack: ${pr.stack.joinToString(" · ")}", cls = "muted")
    }

    h2("The source", id = "source")
    p(sharedFoundation.blurb)
    ul(
        sharedFoundation.libs.map {
            "${link(it.name, it.url)} — ${esc(it.role)} Used by ${esc(it.usedBy.joinToString(", "))}."
        },
        raw = true,
    )
    h3("Merged open-source contributions")
    ul(
        openSource.map {
            "${link(it.title, it.url)} — ${esc(it.repo)}, ${esc(it.status)} ${esc(it.date)}"
        },
        raw = true,
    )
    h3("Recently shipped")
    ul(recentGrowth.map { "<b>${esc(it.date)} · ${esc(it.title)}</b> — ${esc(it.detail)}" }, raw = true)

    h2("Experience", id = "experience")
    experience.forEach { appendExperience(it) }
    p("${education.degree}, ${education.school} · ${education.period}")

    h2("Skills", id = "skills")
    skills.forEach { appendSkillGroup(it) }

    h2("Explore", id = "explore")
    append(exploreSection())

    h2("Contact", id = "contact")
    appendContact()
}

private fun resumeBody(): String = buildString {
    h1(profile.name)
    p(profile.resumeTitle, cls = "lead")
    p(
        listOf(
            esc(profile.location),
            link(profile.email, "mailto:${profile.email}"),
            esc(profile.phone),
            link("GitHub", profile.github),
            link("LinkedIn", profile.linkedin),
        ).joinToString(" · "),
        cls = "muted",
        raw = true,
    )

    h2("Summary")
    p(profile.summary)

    h2("Core competencies")
    ul(competencies)

    h2("Experience")
    experience.forEach { appendExperience(it) }

    h2("Skills")
    resumeSkills.forEach { appendSkillGroup(it) }

    h2("Education")
    p("${education.degree} — ${education.school} · ${education.period}")

    h2("Languages")
    p(languages.joinToString(" · "))

    h2("Open source")
    ul(
        openSource.map {
            "${link(it.title, it.url)} — ${esc(it.repo)}, ${esc(it.status)} ${esc(it.date)}"
        },
        raw = true,
    )

    h2("Selected projects")
    ul(
        projects.map {
            "<a href=\"/project/${esc(it.slug)}\">${esc(it.name)}</a> — ${esc(it.tagline)} " +
                "<span class=\"muted\">${esc(it.status)}</span>"
        },
        raw = true,
    )

    h2("Availability")
    p(profile.availability)
}

private val TERMINAL_DESCRIPTION =
    "A typable shell over this CV: ${TerminalEngine.commands.size} commands — ls the site, " +
        "cat the résumé, open any project case study by slug."

/**
 * The terminal's `help` output, printed by the engine itself rather than transcribed — a new
 * command documents itself here the same way it does in the shell.
 */
private fun terminalBody(): String = buildString {
    h1("Terminal")
    p(
        "A faux shell over this CV that you can actually type in — list the site, print the " +
            "résumé, or open any project case study by slug. Reachable from anywhere on the " +
            "site with the backtick key.",
    )
    h2("Commands")
    append("    <pre>")
    append(esc(TerminalEngine.run("help").lines.joinToString("\n") { it.text }))
    append("</pre>\n")

    h2("Case studies you can open")
    ul(
        projects.map {
            "<code>open ${esc(it.slug)}</code> — " +
                "<a href=\"/project/${esc(it.slug)}\">${esc(it.name)}</a>: ${esc(it.tagline)}"
        },
        raw = true,
    )
}

private const val LAB_DESCRIPTION =
    "Interactive simulations of real production problems: recomposition cost, request fan-out, " +
        "crash triage, search traversal and module-graph isolation."

/**
 * The bench's crawlable form. Each experiment's description already exists in [cvLabs] — it is
 * precisely what the canvas cannot expose to a crawler or a screen reader, and it is also the only
 * part that carries meaning without the animation. Emitting it here costs nothing and duplicates
 * nothing.
 */
private fun labBody(): String = buildString {
    h1("Lab bench")
    p(
        "Each instrument is a real production problem reduced to its arithmetic and drawn on a " +
            "canvas — recomposition cost, request fan-out, crash triage, search traversal, " +
            "module-graph isolation. Every simulation is a pure function of elapsed time, which " +
            "is what makes the reduced-motion still frame free and the maths checkable.",
    )
    LabGroup.entries.forEach { group ->
        h2(group.label)
        ul(
            cvLabs.filter { it.group == group }.map {
                "<b>${esc(it.label)}</b> (${esc(it.metric)}) — ${esc(it.description)}"
            },
            raw = true,
        )
    }
}

private const val PLAYGROUND_DESCRIPTION =
    "Type a Compose subset and see it rendered by real composables in the same runtime — no " +
        "server round-trip and no compile step."

/** The presets are the crawlable content — they are the only prose the canvas cannot expose. */
private fun playgroundBody(): String = buildString {
    h1("Compose playground")
    p(
        "A curated slice of Jetpack Compose, interpreted and handed to the same Compose runtime "
            + "that renders this site. There is no Run button because there is no compile step: a "
            + "real playground compiles Kotlin server-side, which needs a warm JVM and means "
            + "building untrusted code. Interpreting a subset trades generality for instant "
            + "feedback, and because the host is itself Compose, what renders is the real "
            + "composable rather than a CSS approximation of one.",
    )
    h2("Starting points")
    ul(composePresets.map { esc(it.label) })
    h2("Supported subset")
    p(
        "Column, Row, Box, Card, Surface, Text, Button, Spacer, AnimatedVisibility and TextField, "
            + "a modifier chain, and remember { mutableStateOf(...) }.",
    )
}

private const val FORGE_DESCRIPTION =
    "A few thousand particles spring-tied to the wordmark, parting around the cursor and snapping " +
        "back. Physics on a Compose canvas."

private fun forgeBody(): String = buildString {
    h1("Particle forge")
    p(
        "A few thousand particles, each spring-tied to a point on the wordmark, parting around " +
            "the cursor and snapping back. Hooke plus damping over flat float arrays on a Compose " +
            "canvas — no physics engine, no per-particle objects.",
    )
    p(
        "There is nothing to read here; it is a toy. " +
            "<a href=\"/\">The portfolio</a> is the point.",
        raw = true,
    )
}

private val HIRE_DESCRIPTION = "${profile.tagline} ${profile.availability}."

/**
 * The recruiter's page. Every sentence on it already exists in `data/` and has already been
 * through the claim audit, which is the argument hire.tsx makes in a comment and then breaks by
 * hand-typing its own numbers a paragraph later.
 */
private fun hireBody(): String = buildString {
    h1(profile.name)
    p("${profile.title} · ${profile.location}", cls = "muted")
    p(profile.intro, cls = "lead")

    h2("The numbers")
    ul(metrics.map { "<b>${esc(it.value)}</b> — ${esc(it.label)}. ${esc(it.detail)}" }, raw = true)

    h2("The proof")
    ul(
        caseStudies.map {
            "<b>${esc(it.title.substringBefore(": "))}</b> — ${esc(it.metric)}. ${esc(it.summary)}"
        },
        raw = true,
    )

    h2("Availability")
    p(profile.availability)
    appendContact()
}

// Read off the corpus, never typed: gen-store.mjs re-sweeps the store and both of these move on
// their own. A hand-written "89 live" here is precisely the drift this pipeline exists to stop.
private val SHIPPED_DESCRIPTION =
    "Every Play Store listing his commits reached: ${fleetStats.live} live and " +
        "${fleetStats.delisted} pulled across ${fleetStats.clients} client branches, swept from " +
        "the store rather than remembered."

/**
 * The Play Store fleet. Not one number here is typed: `fleetStats` is what gen-store.mjs derived
 * from the sweep, and the two client walls are its own grouping, so a re-sweep moves the prose and
 * the lists together or moves neither.
 */
private fun shippedBody(): String = buildString {
    h1("Shipped")
    p(
        "Every Play Store listing his commits reached, read off the store rather than remembered. " +
            "${fleetStats.live} live and ${fleetStats.delisted} pulled, over ${fleetStats.clients} " +
            "client branches and ${fleetStats.developers} developer accounts, at least " +
            "${fleetStats.installFloor} installs. Swept $storeGeneratedAt.",
        cls = "lead",
    )

    h2("Named apps")
    ul(
        storeApps.map {
            "${link(it.name, it.url)} — ${esc(it.role)}, ${esc(it.employer)}. " +
                "${esc(it.installs)} installs, rated ${it.rating}."
        },
        raw = true,
    )

    h2("By year")
    ul(lastShipped.map { "<b>${it.year}</b> — ${it.live} still live, ${it.gone} pulled" }, raw = true)

    h2("Live clients")
    ul(
        liveClients.map { c ->
            "<b>${esc(c.name)}</b> — ${c.apps.size} listing${plural(c.apps.size)}, ${esc(c.developer)}"
        },
        raw = true,
    )

    h2("Pulled clients")
    ul(
        pastClients.map { c ->
            "<b>${esc(c.name)}</b> — ${c.apps.size} listing${plural(c.apps.size)}, " +
                "last seen ${esc(c.lastSeen)}"
        },
        raw = true,
    )
}

/** The two client walls say "listing" 110 times between them. */
private fun plural(n: Int): String = if (n == 1) "" else "s"

private val WEEB_DESCRIPTION =
    "${weeb.anime.total} anime and ${weeb.manga.total} manga kept by hand and read as evidence: a " +
        "status column with no word for quitting, a score scale whose bottom half is unused, and " +
        "the seasons that aired while the list was not looking."

/** The anime and manga ledger. Every count reads out of `weeb`; the page states no total of its own. */
private fun weebBody(): String = buildString {
    h1("Weeb Central")
    p(
        "${weeb.anime.total} anime and ${weeb.manga.total} manga kept by hand, " +
            "${weeb.manga.chaptersRead} chapters of it read, matched against AniList on " +
            "${weeb.generatedAt}. Read as evidence rather than as a list.",
        cls = "lead",
    )

    h2("Status")
    ul(weeb.anime.byWatch.entries.map { "<b>${esc(it.key)}</b> — ${it.value}" }, raw = true)

    h2("Scores used")
    ul(
        weeb.anime.scoreDist.entries.sortedBy { it.key }.map { "<b>${it.key}</b> — ${it.value}" },
        raw = true,
    )

    h2("Furthest behind")
    ul(weeb.anime.deepestGaps.map { "<b>${esc(it.name)}</b> — ${it.gap} unwatched" }, raw = true)

    h2("Sequels out, unstarted")
    ul(
        weeb.stale.map {
            "<b>${esc(it.title)}</b> — ${esc(it.sequel)}, ${esc(it.status)}" +
                (it.year?.let { y -> ", $y" } ?: "")
        },
        raw = true,
    )

    h2("Where his score parts from the crowd")
    ul(
        (weeb.divergence.top + weeb.divergence.bottom).map {
            "<b>${esc(it.name)}</b> — his ${it.mine}, the crowd's ${it.crowd}"
        },
        raw = true,
    )
}

private val OPS_DESCRIPTION =
    "What reports, what has gone stale and what is broken across ${opsPerimeter.size} generated " +
        "corpora and ${opsDrift.size} vendored pins. A board about failure nobody noticed cannot " +
        "report a missing feed as a clean one."

/**
 * The ops board. Four blocks, because four is what this build has a source for: the other four on
 * the React board need a backend this port does not have, and a board about failure nobody noticed
 * is the last place to render a missing feed as a clean one.
 */
private fun opsBody(): String = buildString {
    h1("The ops board")
    p(
        "What reports, what has gone stale, and what is broken across the generated corpora behind " +
            "this site. Stamped $opsGeneratedAt.",
        cls = "lead",
    )

    h2("Freshness perimeter")
    ul(
        opsPerimeter.map {
            "<b>${esc(it.file)}</b> — generated ${esc(it.generatedAt)} by ${esc(it.generator)}, " +
                "SLA ${it.slaDays} days"
        },
        raw = true,
    )

    h2("Vendored drift")
    ul(
        opsDrift.map {
            "<b>${esc(it.repo)}</b> — pinned at ${esc(it.pin)} of ${esc(it.upstream)}" +
                (it.behind?.let { b -> ", $b behind" } ?: "")
        },
        raw = true,
    )

    h2("Leverage")
    ul(
        opsLeverage.map {
            "<b>${esc(it.id)}</b> — ${it.modules} modules across ${esc(it.repos.joinToString(", "))}"
        },
        raw = true,
    )
}

private val LOOPDOWN_DESCRIPTION =
    "Engineering field notes: ${writingLessons.size} lessons across ${writingSeries.size} series, " +
        "and the personified-bug cast that keeps turning up in them."

/** The field-notes index. Each lesson links to wherever it actually published, or to nowhere. */
private fun loopdownBody(): String = buildString {
    h1("Loopdown")
    p(
        "${writingLessons.size} engineering field notes across ${writingSeries.size} series, and " +
            "the personified-bug cast that keeps turning up in them.",
        cls = "lead",
    )

    h2("Lessons")
    ul(
        writingLessons.map { lesson ->
            val where = lesson.links.devto ?: lesson.links.medium
                ?: lesson.links.hashnode ?: lesson.links.linkedin
            val title = if (where != null) link(lesson.title, where) else esc(lesson.title)
            title + (lesson.pillar?.let { " <span class=\"muted\">${esc(it)}</span>" } ?: "")
        },
        raw = true,
    )

    h2("Series")
    ul(writingSeries.map { "<b>${esc(it.title)}</b> — ${it.episodes} episodes" }, raw = true)

    h2("The cast")
    ul(
        writingCast.map { "<b>${esc(titleize(it.id))}</b> — ${it.appearances} appearances" },
        raw = true,
    )
}

/** The doorway room, and the writing that predates the code. */
private fun inkBody(): String = buildString {
    h1("The Ink")
    p(boardArc, cls = "lead")

    h2("The archive")
    ul(
        writingArchive.map {
            "<b>${esc(it.title)}</b>" + (it.form?.let { f -> " <span class=\"muted\">${esc(f)}</span>" } ?: "") +
                (it.blurb?.let { b -> " — ${esc(b)}" } ?: "")
        },
        raw = true,
    )

    h2("Excelsior")
    ul(
        boardProfiles.map {
            "<b>${esc(it.year)}, page ${it.page}: ${esc(it.title)}</b> — ${esc(it.role)}. " +
                "${esc(it.quote)}"
        },
        raw = true,
    )
    p("${loopdownOrigin.year}, page ${loopdownOrigin.page}. ${loopdownOrigin.story}")

    h2("Societies")
    ul(
        societies.map {
            "<b>${esc(it.name)}</b> — ${esc(it.role)}, ${esc(it.years)}. ${esc(it.blurb)}"
        },
        raw = true,
    )
}

/** The anthology index. Bodies and plates are not in the Kotlin corpus, so neither are they here. */
private fun anthologyBody(): String = buildString {
    h1(anthology.title)
    p(anthology.tagline, cls = "lead")
    p(anthology.fourteen)

    h2("Seasons")
    ul(anthology.seasons.map { "<b>${it.n}. ${esc(it.title)}</b> — ${esc(it.blurb)}" }, raw = true)

    h2("Entries")
    ul(
        anthologyEntries.map {
            "<b>${esc(it.title)}</b> — entry ${it.entry}, ${esc(it.planet)} in ${esc(it.system)}. " +
                "${esc(it.blurb)}"
        },
        raw = true,
    )

    h2("The tellers")
    ul(anthology.witnesses.map { "<b>${esc(it.name)}</b> — ${esc(it.did)}" }, raw = true)

    h2("Unfiled")
    ul(unfiledPieces.map { "<b>${esc(it.title)}</b> — ${esc(it.blurb)}" }, raw = true)

    siblingSeries.forEach { series ->
        h2(series.title)
        p("${series.tagline} (${series.medium})", cls = "muted")
        ul(series.entries.map { "<b>${esc(it.title)}</b> — ${esc(it.blurb)}" }, raw = true)
    }
}

private val CANON_DESCRIPTION =
    "The laws, the count and the doctrine behind ${anthology.title}. The gated half is not in " +
        "this page, on purpose."

/**
 * The canon, open half only.
 *
 * The screen partitions on `spoils`: a season that declares one sits behind a gate that names its
 * price before it opens. That partition is honoured here rather than flattened, because a search
 * result is not a place to put something a reader explicitly asked not to be told, and a crawler
 * that indexes the gated text would put it there for good.
 */
private fun canonBody(): String = buildString {
    h1("The Canon")
    p("The laws, the count and the doctrine behind ${anthology.title}.", cls = "lead")

    h2("The count")
    ul(countLedger.map { "<b>${esc(it.line)}</b> — ${esc(it.value)}" }, raw = true)
    p("The named: ${namedThirteen.joinToString(", ")}.")

    val open = seasonCanon.entries.filter { it.value.spoils == null }.sortedBy { it.key }
    open.forEach { (n, canon) ->
        h2("Season $n")
        p(canon.thesis)
        ul(canon.laws.map { "<b>${it.n}. ${esc(it.name)}</b> — ${esc(it.gloss)}" }, raw = true)
        ul(canon.points.map { "<b>${esc(it.term)}</b> — ${esc(it.gloss)}" }, raw = true)
    }

    h2("Rendering")
    p(renderingDoctrine.claim)
    ul(renderingDoctrine.mechanism)
    p(renderingDoctrine.pull)
    ul(renderingDoctrine.consequences.map { "<b>${esc(it.term)}</b> — ${esc(it.gloss)}" }, raw = true)

    h2("Rig constraints")
    ul(
        rigConstraints.map { "<b>${esc(it.species)}</b>, ${esc(it.world)} — ${esc(it.constraint)}" },
        raw = true,
    )
    p(rigConstraintsNote, cls = "muted")

    h2("The tether")
    ul(tether.map { "<b>${it.value}</b> — ${esc(it.label)}" }, raw = true)
    p(tetherDoctrine)

    h2("Standard intervals")
    ul(
        standardIntervals.filterNot { it.blank }.map {
            "<b>${esc(it.interval)}</b> — ${esc(it.realm)}, ${esc(it.length)}"
        },
        raw = true,
    )

    p(
        "The gated half of this page is deliberately not in the static layer. It sits behind a " +
            "spoiler expander that states its price before it opens, and a page in a search result " +
            "cannot ask first.",
        cls = "muted",
    )
}

private val MAKING_DESCRIPTION =
    "How ${anthology.title} was built, blind-audited and paid for: the audit gate, the " +
        "${pipelineStages.size} pipeline stages, and every dollar of it."

/** How the anthology was built and paid for. The season-specific findings are gated; see [canonBody]. */
private fun makingBody(): String = buildString {
    h1("The Making")
    p("How ${anthology.title} was built, blind-audited and paid for.", cls = "lead")

    h2("The audit")
    p(auditMethod.send)
    p(auditMethod.gate)
    p(auditMethod.whyNotSelfAssessed)
    p(auditMethod.summary)

    h2("Voice")
    ul(voiceConstraints)

    h2("The pipeline")
    ul(pipelineStages.map { "<b>${esc(it.step)}</b> — ${esc(it.detail)}" }, raw = true)

    h2("Retroaction")
    p(retroactionStandard)

    h2("Spend")
    ul(
        listOf(
            "<b>${money(spend.totalUsd)}</b> — total",
            "${money(spend.firstBuildUsd)} — the first build",
            "${money(spend.secondBuildUsd)} — the second",
            "${money(spend.auditsUsd)} — audits",
            "${money(spend.artUsd)} — art",
        ),
        raw = true,
    )
    p(spend.note, cls = "muted")

    h2("Receipts")
    // Some hrefs in this corpus are local paths rather than URLs; a bare label beats a dead link.
    ul(
        receipts.map {
            if (it.href.startsWith("http")) link(it.label, it.href) else esc("${it.label} (${it.href})")
        },
        raw = true,
    )

    p(
        "The three season-specific findings on this page are behind the same spoiler gates the " +
            "canon uses, so they are not in the static layer either.",
        cls = "muted",
    )
}

private fun projectBody(project: Project): String = buildString {
    h1(project.name)
    p(project.status, cls = "muted")
    p(project.tagline, cls = "lead")
    p(project.description)
    if (project.badges.isNotEmpty()) p(project.badges.joinToString(" · "), cls = "muted")

    if (project.links.isNotEmpty()) {
        p(project.links.joinToString(" · ") { link(it.label, it.url) }, raw = true)
    }

    h2("Stack")
    ul(project.stack)

    if (project.highlights.isNotEmpty()) {
        h2("Highlights")
        ul(project.highlights)
    }

    if (project.targets.isNotEmpty()) {
        h2("Platforms")
        ul(
            project.targets.map { t ->
                val screens = "${t.screenCount} screen" + if (t.screenCount == 1) "" else "s"
                "<b>${esc(t.platform)}</b> — $screens${t.note?.let { ". " + esc(it) } ?: ""}"
            },
            raw = true,
        )
    }

    project.detail?.let { detail ->
        h2("Overview")
        p(detail.overview)

        if (detail.metrics.isNotEmpty()) {
            h2("By the numbers")
            ul(detail.metrics.map { "<b>${esc(it.value)}</b> — ${esc(it.label)}" }, raw = true)
        }

        detail.sections.forEach { section ->
            h3(section.heading)
            p(section.body)
        }

        if (detail.roles.isNotEmpty()) {
            h2("Roles")
            ul(detail.roles.map { "<b>${esc(it.name)}</b> — ${esc(it.power)}" }, raw = true)
        }

        if (detail.techStack.isNotEmpty()) {
            h2("Tech stack")
            detail.techStack.forEach { appendSkillGroup(it) }
        }

        if (detail.extraLinks.isNotEmpty()) {
            h2("Links")
            ul(detail.extraLinks.map { link(it.label, it.url) }, raw = true)
        }

        // Diagrams are raw Mermaid. Nothing renders them yet (see the KDoc on `Diagram`), but the
        // source is real text a crawler and a reader can both use, so it ships as a <pre>.
        detail.diagrams.forEach { diagram ->
            h3(diagram.title)
            append("    <pre>")
            append(esc(diagram.code))
            append("</pre>\n")
        }
    }

    nextProject(project.slug)?.let { next ->
        h2("Next")
        p(
            "<a href=\"/project/${esc(next.slug)}\">${esc(next.name)}</a> — ${esc(next.tagline)}",
            raw = true,
        )
    }
}

/**
 * A room links here when this build serves its path and out to the React original when it does not.
 * The router answers that question ([routeOrNull]), so a room that ports later starts linking
 * inward on the next prerender with no edit here. Linking an unported room at this origin would
 * manufacture a 404, which is worse for indexing than linking out.
 */
private fun exploreSection(): String = buildString {
    val ported = siteRooms.count { routeOrNull(it.to) != null }
    p(
        "Interactive surfaces on this site. ${ported} of ${siteRooms.size} rooms are built into " +
            "this Compose Multiplatform port; the WebGL, canvas-3D and corpus rooms stay on " +
            "${link("the React build", REACT_SITE)}.",
        raw = true,
    )
    ul(
        siteRooms.map { room ->
            val href = if (routeOrNull(room.to) != null) room.to else "$REACT_SITE${room.to}"
            "${link(room.label, href)} <span class=\"muted\">${esc(room.tag)}</span> — ${esc(room.blurb)}"
        },
        raw = true,
    )
}

private fun StringBuilder.appendContact() {
    ul(
        listOf(
            link(profile.email, "mailto:${profile.email}"),
            esc(profile.phone),
            link("GitHub — ${profile.github.substringAfterLast('/')}", profile.github),
            link("LinkedIn", profile.linkedin),
            link("Portfolio", profile.portfolio),
        ),
        raw = true,
    )
    p(profile.availability, cls = "muted")
}

private fun StringBuilder.appendExperience(exp: Experience) {
    h3("${exp.role} — ${exp.company}")
    p(exp.period, cls = "muted")
    ul(
        exp.points.map { point ->
            point.label?.let { "<b>${esc(it)}.</b> ${esc(point.text)}" } ?: esc(point.text)
        },
        raw = true,
    )
}

private fun StringBuilder.appendSkillGroup(group: SkillGroup) {
    h3(group.group)
    ul(group.items)
}

// ---------------------------------------------------------------------------------------------
// JSON-LD
// ---------------------------------------------------------------------------------------------

/**
 * schema.org `Person` for the home and résumé pages — the structured record a knowledge panel or
 * a résumé parser reads. `sameAs` is what ties this origin to the GitHub and LinkedIn identities.
 */
private fun personLd(origin: String, description: String): String = ld {
    put("@context", "https://schema.org")
    put("@type", "Person")
    put("name", profile.name)
    put("jobTitle", profile.title)
    put("description", clamp(description, 300))
    put("email", "mailto:${profile.email}")
    put("telephone", profile.phone)
    put("url", "$origin/")
    putJsonObject("address") {
        put("@type", "PostalAddress")
        put("addressLocality", profile.location.substringBefore(',').trim())
        put("addressCountry", "IN")
    }
    putJsonObject("alumniOf") {
        put("@type", "CollegeOrUniversity")
        put("name", education.school)
    }
    putJsonObject("hasOccupation") {
        put("@type", "Occupation")
        put("name", profile.title)
    }
    putJsonArray("sameAs") {
        listOf(profile.github, profile.linkedin, profile.portfolio, REACT_SITE).forEach { add(it) }
    }
    putJsonArray("knowsAbout") { competencies.forEach { add(it) } }
}

/**
 * `SoftwareSourceCode` when there is a repository to point at, `CreativeWork` otherwise — DEADLOCK
 * is a public case study over a private repo, and claiming source code that nobody can fetch is
 * the kind of structured-data lie that gets rich results pulled.
 */
private fun projectLd(project: Project, origin: String): String {
    val repo = project.links.firstOrNull { it.label.equals("GitHub", ignoreCase = true) }?.url
    return ld {
        put("@context", "https://schema.org")
        put("@type", if (repo != null) "SoftwareSourceCode" else "CreativeWork")
        put("name", project.name)
        put("headline", project.tagline)
        put("description", clamp(project.detail?.overview ?: project.description, 300))
        put("url", "$origin/project/${project.slug}")
        if (repo != null) put("codeRepository", repo)
        CvGallery.hero(project.slug)?.let { put("image", it) }
        putJsonObject("author") {
            put("@type", "Person")
            put("name", profile.name)
            put("url", "$origin/")
        }
        putJsonArray("programmingLanguage") { project.stack.forEach { add(it) } }
        put("keywords", (project.badges + project.stack).distinct().joinToString(", "))
    }
}

/**
 * `</` is escaped because a literal `</script>` inside a JSON string would close the surrounding
 * `<script type="application/ld+json">` tag — JSON-legal, HTML-fatal. Nothing in the data does
 * that today; this is one line so it never can.
 */
private fun ld(build: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): String =
    json.encodeToString(JsonObject.serializer(), buildJsonObject(build)).replace("</", "<\\/")

// ---------------------------------------------------------------------------------------------
// sitemap.xml / robots.txt
// ---------------------------------------------------------------------------------------------

/**
 * No `<lastmod>` on purpose. The generator has no honest source for it — stamping "today" on every
 * run makes every page claim it changed on every deploy, which search engines learn to ignore and
 * which would make the output non-reproducible for build caching. Priority is the only hint given.
 */
private fun sitemap(routes: List<Route>, origin: String): String = buildString {
    append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
    append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n")
    routes.forEach { route ->
        val path = route.toPath()
        val loc = if (path == "/") "$origin/" else "$origin$path"
        val priority = when (route) {
            Route.Home -> "1.0"
            Route.Resume -> "0.9"
            // The page written for the reader who is hiring. Same weight as the résumé, because it
            // is the same errand with less scrolling.
            Route.Hire -> "0.9"
            is Route.ProjectDetail -> "0.7"
            // Evidence about the work, one rung under a case study.
            Route.Shipped -> "0.7"
            Route.Playground -> "0.6"
            Route.Loopdown -> "0.6"
            Route.Terminal -> "0.5"
            Route.Ink -> "0.5"
            is Route.Anthology -> "0.5"
            // Demos: worth indexing, but they should never outrank a case study in results.
            Route.Lab -> "0.4"
            Route.Ops -> "0.4"
            Route.Weeb -> "0.4"
            // Rooms off a room. Indexable, never a landing page.
            Route.Canon -> "0.3"
            Route.Making -> "0.3"
            Route.Forge -> "0.3"
        }
        append("  <url>\n")
        append("    <loc>${esc(loc)}</loc>\n")
        append("    <changefreq>weekly</changefreq>\n")
        append("    <priority>$priority</priority>\n")
        append("  </url>\n")
    }
    append("</urlset>\n")
}

// ---------------------------------------------------------------------------------------------
// Tiny HTML helpers
// ---------------------------------------------------------------------------------------------

/**
 * The five characters that change meaning inside HTML text or a double-quoted attribute. One
 * function for both positions, so no caller has to remember which context it is in — the content
 * is full of `&`, `<`, apostrophes and typographic quotes, and a single unescaped `&` in a meta
 * description is a validator error.
 */
private fun esc(s: String): String = buildString(s.length + 16) {
    s.forEach { c ->
        when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&#39;")
            else -> append(c)
        }
    }
}

/**
 * Meta descriptions get truncated by search engines around 155-160 characters; the source prose is
 * paragraphs. Collapse whitespace (the data uses triple-quoted multi-line strings), cut on a word
 * boundary, and trim dangling punctuation so the ellipsis does not read as "word — …".
 */
private fun clamp(s: String, max: Int = 155): String {
    val flat = s.replace(WHITESPACE, " ").trim()
    if (flat.length <= max) return flat
    val cut = flat.take(max)
    val space = cut.lastIndexOf(' ')
    val body = if (space > max / 2) cut.take(space) else cut
    return body.trimEnd(' ', ',', '.', ';', ':', '—', '·', '-') + "…"
}

private val WHITESPACE = Regex("\\s+")

private fun link(label: String, url: String): String =
    "<a href=\"${esc(url)}\">${esc(label)}</a>"

private fun StringBuilder.h1(text: String) {
    append("    <h1>${esc(text)}</h1>\n")
}

private fun StringBuilder.h2(text: String, id: String? = null) {
    val attr = id?.let { " id=\"${esc(it)}\"" } ?: ""
    append("    <h2$attr>${esc(text)}</h2>\n")
}

private fun StringBuilder.h3(text: String, raw: Boolean = false) {
    append("    <h3>${if (raw) text else esc(text)}</h3>\n")
}

private fun StringBuilder.p(text: String, cls: String? = null, raw: Boolean = false) {
    val attr = cls?.let { " class=\"${esc(it)}\"" } ?: ""
    append("    <p$attr>${if (raw) text else esc(text)}</p>\n")
}

/** [raw] items are pre-escaped HTML (they carry `<a>`/`<b>`); plain items are escaped here. */
private fun StringBuilder.ul(items: List<String>, raw: Boolean = false) {
    if (items.isEmpty()) return
    append("    <ul>\n")
    items.forEach { append("      <li>${if (raw) it else esc(it)}</li>\n") }
    append("    </ul>\n")
}

// ---------------------------------------------------------------------------------------------
// ponytail: one runnable check instead of a test module — same shape as navSelfCheck() in Nav.kt.
// It guards the three things that would fail silently in generated HTML: escaping, the clamp, and
// the route -> file mapping (a wrong path here means a 404 that only shows up in production).
// ---------------------------------------------------------------------------------------------
/**
 * Floor for a page's crawlable body. Nothing here is near it: the thinnest page this build emits is
 * the forge at about four times this. It exists to catch a route wired into the table with a body
 * function that returns nothing, which renders as a complete, valid, empty page.
 */
private const val MIN_CRAWLABLE_CHARS = 400

internal fun selfCheck() {
    // The shared self-checks piggyback here because this is the ONLY entry point in the project
    // that actually executes on the JVM. Everything under composeMain is `internal` and called
    // from nowhere, so wasm DCE deletes it and the checks silently never run — a check that never
    // runs is worse than no check, because it reads as coverage. `prerenderSite` is on the deploy
    // path, so wiring them here makes them a real gate.
    // ponytail: move all four into a proper commonTest module the day this project has one.
    navSelfCheck()
    floatingChatSelfCheck()
    mermaidParseSelfCheck()
    mermaidLayoutSelfCheck()
    resumeHtmlSelfCheck()
    labsSelfCheck()
    labScreenSelfCheck()
    themeLabSelfCheck()
    composeInterpreterSelfCheck()
    composeRenderSelfCheck()
    playgroundScreenSelfCheck()
    paletteSelfCheck()
    forgeSelfCheck()
    // The three the ported screens left behind. Same reason as the rest: composeMain is `internal`
    // and called from nowhere, so without this line wasm DCE deletes them and they read as coverage
    // while never executing.
    shippedFormatSelfCheck()
    anthologySelfCheck()
    makingSelfCheck()
    readmeSelfCheck()

    check(esc("a & b <c> \"d\" 'e'") == "a &amp; b &lt;c&gt; &quot;d&quot; &#39;e&#39;") { "escaping" }
    check(esc("Kursi — “Panda”") == "Kursi — “Panda”") { "non-ASCII passes through; the file is UTF-8" }

    check(clamp("short line") == "short line") { "short strings are untouched" }
    check(clamp("a\n  b   c") == "a b c") { "multi-line data collapses to one line" }
    val long = clamp("word ".repeat(80))
    check(long.length <= 156 && long.endsWith("…")) { "long strings clamp and mark the cut" }
    check(!long.contains("  ")) { "no double spaces survive the collapse" }

    val root = File("/out")
    check(Route.Home.outputFile(root).path == "/out/index.html") { "home is the directory index" }
    check(Route.Resume.outputFile(root).path == "/out/resume/index.html") { "extensionless route -> dir" }
    check(
        Route.ProjectDetail("mileway").outputFile(root).path == "/out/project/mileway/index.html",
    ) { "project pages nest two deep" }

    // Every emitted page has to be a real page: the wasm mount point, a root-absolute bundle src,
    // a canonical pointing at its own route, and a body with something in it. Walking
    // `prerenderRoutes` rather than a list typed here means a route added to Nav.kt is checked the
    // moment it exists, which is what caught /anthology rendering its `?layer=` into a filename.
    prerenderRoutes.forEach { route ->
        val html = render(route, "https://example.test")
        val path = route.toPath()
        val canonical = if (path == "/") "https://example.test/" else "https://example.test$path"
        check(html.contains("<div id=\"compose\"></div>")) { "$path: wasm mount point" }
        check(html.contains("src=\"/cmpWeb.js\"")) { "$path: root-absolute bundle src" }
        check(html.contains("rel=\"canonical\" href=\"${esc(canonical)}\"")) {
            "$path: canonical points at its own route"
        }
        check(!html.contains("</script>\",")) { "$path: JSON-LD cannot break out of its tag" }
        // The `#seo` block is the only thing a crawler reads. An empty one is a page that exists
        // and says nothing, which is worse than no page at all.
        val seo = html.substringAfter("<div id=\"seo\">").substringBefore("</div>")
        check(seo.length > MIN_CRAWLABLE_CHARS) {
            "$path: the crawlable body is ${seo.length} chars, effectively empty"
        }
        check(seo.contains("<h1>")) { "$path: no heading in the crawlable body" }
        // A route the emitter forgot writes its query string into a directory name.
        check(!route.outputFile(File("/out")).path.contains('?')) { "$path: query leaked into the filename" }
    }

    // The whole point of the file: every project in the data gets a page, and every page's <title>
    // and body actually contain that project's own content.
    check(projects.isNotEmpty()) { "no projects means nothing to prerender" }
    projects.forEach { project ->
        val html = render(Route.ProjectDetail(project.slug), "https://example.test")
        check(html.contains("<div id=\"compose\"></div>")) { "${project.slug}: wasm mount point" }
        check(html.contains("src=\"/cmpWeb.js\"")) { "${project.slug}: root-absolute bundle src" }
        check(html.contains("rel=\"canonical\" href=\"https://example.test/project/${project.slug}\"")) {
            "${project.slug}: canonical points at its own route"
        }
        check(html.contains(esc(project.tagline))) { "${project.slug}: real content, not a placeholder" }
        check(!html.contains("</script>\",")) { "${project.slug}: JSON-LD cannot break out of its tag" }
    }
}

/**
 * The README is the only surface in this repo that states counts in prose, and prose does not
 * recompile. Both halves of this check exist because both already drifted: the page count was three
 * different numbers in one document, and the no-dash house rule was applied by hand and missed lines.
 *
 * Walks up from the working directory because Gradle runs `prerenderSite` with `cmp-shared` as cwd.
 */
private fun readmeSelfCheck() {
    val readme = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "README.md") }
        .firstOrNull { it.isFile }
        ?: error("README.md not found above ${File("").absolutePath}")
    val text = readme.readText()

    val pages = prerenderRoutes.size
    listOf("all $pages routes", "$pages pages", "$pages URLs").forEach { phrase ->
        check(text.contains(phrase)) { "README must say \"$phrase\": the prerenderer emits $pages pages" }
    }

    // Every path this build serves has to be findable in the README, or the parity tables claim a
    // surface the reader cannot check. /compose shipped unmentioned once already.
    staticRoutes.filter { it != Route.Home }.forEach { route ->
        check(text.contains(route.toPath())) { "README never names the shipped route ${route.toPath()}" }
    }

    val dashLine = text.lineSequence().withIndex()
        .firstOrNull { (_, line) -> line.any { it == '\u2014' || it == '\u2013' } }
    if (dashLine != null) error("em/en dash in README line ${dashLine.index + 1}: ${dashLine.value.trim()}")
}
