package com.siddharth.cv.shared.prerender

import com.siddharth.cv.shared.Route
import com.siddharth.cv.shared.chat.floatingChatSelfCheck
import com.siddharth.cv.shared.detail.mermaidParseSelfCheck
import com.siddharth.cv.shared.detail.resumeHtmlSelfCheck
import com.siddharth.cv.shared.navSelfCheck
import com.siddharth.cv.shared.data.CvGallery
import com.siddharth.cv.shared.data.Experience
import com.siddharth.cv.shared.data.NamedLink
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
import com.siddharth.cv.shared.data.siteRooms
import com.siddharth.cv.shared.data.skills
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

        // Derived from Route, not hand-listed: a new route in Nav.kt shows up here or the `when`
        // in `render` stops compiling. That is the whole point of generating from Kotlin.
        val routes: List<Route> = listOf(Route.Home, Route.Resume, Route.Terminal) +
            projects.map { Route.ProjectDetail(it.slug) }

        routes.forEach { route ->
            val file = route.outputFile(outDir)
            file.parentFile?.mkdirs()
            file.writeText(render(route, origin))
        }

        File(outDir, "sitemap.xml").writeText(sitemap(routes, origin))
        File(outDir, "robots.txt").writeText(robots(origin))

        println("prerender: ${routes.size} pages + sitemap.xml + robots.txt -> ${outDir.absolutePath} (origin $origin)")
    }
}

/**
 * Fallback origin. Overridden by `args[1]` or `CV_SITE_ORIGIN`; only used so a bare local run
 * produces something inspectable rather than throwing.
 */
private const val DEFAULT_ORIGIN = "https://cv-siddharth-kmp.vercel.app"

/** The React original. `siteRooms.to` are its routes, not this build's — see [exploreSection]. */
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

private fun render(route: Route, origin: String): String = when (route) {
    Route.Home -> page(
        route = route,
        origin = origin,
        title = "${profile.name} — ${profile.title}",
        description = profile.intro,
        ogType = "profile",
        image = CvGallery.hero("mileway"),
        jsonLd = personLd(origin, profile.intro),
        body = homeBody(),
    )

    Route.Resume -> page(
        route = route,
        origin = origin,
        title = "Résumé — ${profile.name} · ${profile.title}",
        description = profile.summary,
        ogType = "profile",
        image = CvGallery.hero("mileway"),
        jsonLd = personLd(origin, profile.summary),
        body = resumeBody(),
    )

    Route.Terminal -> page(
        route = route,
        origin = origin,
        title = "Terminal — ${profile.name}",
        description = "A typable shell over this CV: ${TerminalEngine.commands.size} commands — " +
            "ls the site, cat the résumé, open any project case study by slug.",
        ogType = "website",
        image = CvGallery.hero("mileway"),
        jsonLd = personLd(origin, profile.intro),
        body = terminalBody(),
    )

    is Route.ProjectDetail -> {
        // Only slugs taken from `projects` reach here (main() builds the list), so this is a
        // programming error rather than a 404 path.
        val project = projects.first { it.slug == route.slug }
        page(
            route = route,
            origin = origin,
            title = "${project.name} — ${profile.name}",
            description = project.detail?.overview ?: project.description,
            ogType = "article",
            image = CvGallery.hero(project.slug),
            jsonLd = projectLd(project, origin),
            body = projectBody(project),
        )
    }
}

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
    ogType: String,
    image: String?,
    jsonLd: String,
    body: String,
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
                <meta name="twitter:card" content="summary_large_image">
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
 * eleven, because the app's own navigation is canvas clicks it cannot see.
 */
private fun siteNav(current: Route): String = buildString {
    append("    <nav aria-label=\"Site\">\n")
    val entries = listOf<Pair<Route, String>>(
        Route.Home to "Home",
        Route.Resume to "Résumé",
        Route.Terminal to "Terminal",
    ) + projects.map { Route.ProjectDetail(it.slug) to it.slug }

    entries.forEach { (route, label) ->
        if (route == current) {
            append("      <span class=\"muted\" aria-current=\"page\">${esc(label)}</span>\n")
        } else {
            append("      <a href=\"${esc(route.toPath())}\">${esc(label)}</a>\n")
        }
    }
    append("    </nav>\n")
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
            ul(detail.extraLinks.map { renderLink(it) }, raw = true)
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
 * `siteRooms.to` are routes on the React original, not on this build — only `/terminal` ported.
 * Linking them at this origin would manufacture six 404s, which is worse for indexing than
 * linking out, so everything except the terminal points at the site that actually serves it.
 */
private fun exploreSection(): String = buildString {
    p(
        "Interactive surfaces on the original site — this Compose Multiplatform port ships the " +
            "terminal; the WebGL, canvas and tldraw rooms stay on ${link("the React build", REACT_SITE)}.",
        raw = true,
    )
    ul(
        siteRooms.map { room ->
            val href = if (room.to == "/terminal") "/terminal" else "$REACT_SITE${room.to}"
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
            is Route.ProjectDetail -> "0.7"
            Route.Terminal -> "0.5"
        }
        append("  <url>\n")
        append("    <loc>${esc(loc)}</loc>\n")
        append("    <changefreq>weekly</changefreq>\n")
        append("    <priority>$priority</priority>\n")
        append("  </url>\n")
    }
    append("</urlset>\n")
}

private fun robots(origin: String): String =
    """
    User-agent: *
    Allow: /

    Sitemap: $origin/sitemap.xml
    """.trimIndent() + "\n"

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

private fun renderLink(l: NamedLink): String = link(l.label, l.url)

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
    resumeHtmlSelfCheck()

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

// ---------------------------------------------------------------------------------------------
// INTEGRATOR NOTE — a Gradle task is still needed; this file deliberately does not add one.
//
//   val prerender by tasks.registering(JavaExec::class) {
//       description = "Generates static HTML for every route from the Kotlin data."
//       mainClass.set("com.siddharth.cv.shared.prerender.Prerender")
//       val jvm = kotlin.targets.getByName("jvm").compilations.getByName("main")
//       classpath = files(jvm.output.allOutputs, jvm.runtimeDependencyFiles)
//       args("$rootDir/cmp-web/build/dist/wasmJs/productionExecutable", "https://<deploy-origin>")
//   }
//
// It must run AFTER wasmJsBrowserDistribution, because that task writes index.html into the same
// directory and would overwrite the generated root page. The second arg (or CV_SITE_ORIGIN) is
// required for correct canonical/sitemap URLs — the default is a guess.
// ---------------------------------------------------------------------------------------------
