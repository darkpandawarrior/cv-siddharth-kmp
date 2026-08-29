package com.siddharth.cv.shared.detail

import com.siddharth.cv.shared.data.competencies
import com.siddharth.cv.shared.data.education
import com.siddharth.cv.shared.data.experience
import com.siddharth.cv.shared.data.languages
import com.siddharth.cv.shared.data.metrics
import com.siddharth.cv.shared.data.openSource
import com.siddharth.cv.shared.data.profile
import com.siddharth.cv.shared.data.projects
import com.siddharth.cv.shared.data.resumeSkills

/**
 * Save-as-PDF for the résumé, without asking Compose to do something it cannot.
 *
 * v1's footnote in [ResumeScreen] was right about the diagnosis and wrong about the cure. The
 * diagnosis: a Compose/Wasm build paints into one `<canvas>`, and a canvas is a single opaque
 * box to the browser's print engine — there is no text to reflow, no element to break a page
 * between, so `window.print()` on the host page yields one clipped bitmap. The cure it missed:
 * the print engine does not have to be pointed at *this* document. Hand it a second, throwaway
 * document that is real semantic HTML and it paginates that instead.
 *
 * So the split here is:
 *  - [buildResumeHtml] — a pure `() -> String` that renders the same Kotlin data the on-screen
 *    résumé renders into print-styled HTML. No Compose, no platform API, no I/O; identical on
 *    every target and the part actually worth owning.
 *  - [printResume] — the platform glue that puts that string in front of a print engine. Every
 *    target has one now; each reaches a *different* engine, because the only thing that renders
 *    HTML on a platform is that platform's own browser/WebView/UIKit text stack:
 *      - wasmJs — a hidden `<iframe>`, printed in place.
 *      - jvm    — a temp `.html` handed to the OS browser via `Desktop.browse`.
 *      - android — an offscreen `WebView` + `PrintManager`.
 *      - ios    — `UIMarkupTextPrintFormatter` + `UIPrintInteractionController`.
 *
 * The HTML is deliberately self-contained: no webfont, no stylesheet, no image. An `<iframe>`
 * written via `document.write` starts with an empty cache context, so any external asset would
 * be a race against `print()` — and a résumé that prints with a fallback font that arrived late
 * is worse than one that never asked for a font at all. That constraint pays off twice: it is
 * also what lets `UIMarkupTextPrintFormatter` and a `null`-base-URL `WebView` render the same
 * bytes without a network round-trip.
 */
expect fun printResume(html: String)

/**
 * Whether [printResume] will actually reach a print engine on this target.
 *
 * The point of the flag is that the résumé's Print button can be *absent* rather than dead — a
 * control that does nothing is a worse answer than no control. Callers should gate on this, not
 * on a platform check, because "supported" is not purely a compile-time property: Android needs
 * a host Activity installed first (see `installResumePrintHost`), so its actual is a getter.
 *
 * Read it from composition and it behaves: on every target the value is settled before the first
 * frame, so it never needs to be observable state.
 */
expect val resumePrintSupported: Boolean

/**
 * Names the print job, and with it the file the platform's Save-as-PDF dialog proposes.
 *
 * Shared rather than duplicated per target so the PDF has one name everywhere; the web gets the
 * same string through `<title>` in [buildResumeHtml].
 */
internal val resumePrintJobName: String
    get() = "${profile.name} — ${profile.resumeTitle}"

// -------------------------------------------------------------------------------------------
// The document
// -------------------------------------------------------------------------------------------

/**
 * Renders the résumé as a standalone print-styled HTML document.
 *
 * Mirrors cv-siddharth/src/ResumeView.tsx section for section and in its order — header,
 * summary, competencies, key results, experience, projects & open source, education, technical
 * skills — so the PDF this produces and the React site's PDF are the same document.
 *
 * Pure: same output for the same build, no ordering surprises, safe to call from anywhere.
 */
fun buildResumeHtml(): String = buildString {
    append("<!doctype html>\n<html lang=\"en\">\n<head>\n")
    append("<meta charset=\"utf-8\">\n")
    append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
    // Names the file in the browser's Save-as-PDF dialog — Chrome and Safari both seed the
    // filename from <title>, which is the only handle we get on it.
    append("<title>").append(profile.name.esc()).append(" — ").append(profile.resumeTitle.esc())
    append("</title>\n<style>\n").append(PrintCss).append("</style>\n</head>\n<body>\n")
    append("<article class=\"resume\">\n")

    header()
    section("Professional Summary", avoid = false) {
        append("<p class=\"lede\">").append(profile.summary.esc()).append("</p>\n")
    }
    section("Core Competencies", avoid = true) {
        append("<div class=\"chips\">")
        competencies.forEach { append("<span class=\"chip\">").append(it.esc()).append("</span>") }
        append("</div>\n")
    }
    section("Key Results", avoid = true) {
        // Same join as ResumeView.tsx: value + label, no detail — the detail strings are
        // hover copy on the site and would bloat the page here.
        append("<p class=\"lede\">")
        append(metrics.joinToString(" · ") { "${it.value} ${it.label}" }.esc())
        append("</p>\n")
    }
    section("Experience", avoid = false) { experienceEntries() }
    section("Projects &amp; Open Source", avoid = false) { projectEntries() }
    section("Education", avoid = true) {
        entryHead("${education.degree} · ${education.school}", education.period)
    }
    section("Technical Skills", avoid = true) { skillLines() }

    append("</article>\n</body>\n</html>")
}

private fun StringBuilder.header() {
    append("<header class=\"rh\">\n")
    append("<h1>").append(profile.name.esc()).append("</h1>\n")
    append("<p class=\"role\">").append(profile.resumeTitle.esc()).append("</p>\n")
    // `https://` stripped exactly as ResumeView.tsx does: the scheme costs a third of the line
    // and tells a reader nothing.
    append("<p class=\"meta\">").append(
        listOf(
            profile.phone,
            profile.email,
            profile.linkedin.removePrefix("https://"),
            profile.github.removePrefix("https://"),
        ).joinToString(" · ").esc(),
    ).append("</p>\n")
    append("<p class=\"meta\">")
    append("${profile.location} · ${profile.availability}".esc())
    append("</p>\n</header>\n")
}

private fun StringBuilder.experienceEntries() {
    experience.forEach { job ->
        append("<div class=\"entry avoid\">\n")
        entryHead("${job.role} · ${job.company}", job.period)
        append("<ul>\n")
        job.points.forEach { point ->
            append("<li>")
            point.label?.let { append("<strong>").append(it.esc()).append(": </strong>") }
            append(point.text.esc()).append("</li>\n")
        }
        append("</ul>\n</div>\n")
    }
}

/** Strips the conventional-commit prefix, e.g. `feat(providers): ` — same regex as ResumeView.tsx. */
private val CommitPrefix = Regex("^(feat|fix)\\([^)]*\\): ")

private fun StringBuilder.projectEntries() {
    // break-inside:avoid lives on each entry, never on this section: the project list is taller
    // than a page, so avoiding a break on the whole thing would just push a page of white space.
    projects.forEach { p ->
        append("<div class=\"entry avoid\">\n")
        entryHead(p.name, p.stack.take(3).joinToString(" · "))
        append("<p class=\"tight\">")
        append("${p.tagline} ${p.highlights.firstOrNull().orEmpty()}".trim().esc())
        append("</p>\n</div>\n")
    }
    // Generated from the same `openSource` list the homepage renders, so the count and the
    // titles can never drift from the real merged-PR set.
    append("<p class=\"tight oss\"><strong>Upstream contributions:</strong> ")
    append(
        (
            "${openSource.size} merged PRs to career-ops (public OSS, 60k+ stars) — " +
                openSource.joinToString("; ") { it.title.replace(CommitPrefix, "") } + "."
            ).esc(),
    )
    append("</p>\n")
}

private fun StringBuilder.skillLines() {
    append("<div class=\"skills\">\n")
    skillLine("Languages", languages)
    resumeSkills.forEach { skillLine(it.group, it.items) }
    append("</div>\n")
}

private fun StringBuilder.skillLine(group: String, items: List<String>) {
    append("<p class=\"tight\"><strong>").append(group.esc()).append(":</strong> ")
    append(items.joinToString(", ").esc()).append("</p>\n")
}

/** `flex items-baseline justify-between` — title left, period right, period never wraps. */
private fun StringBuilder.entryHead(left: String, right: String) {
    append("<div class=\"head\"><h3>").append(left.esc()).append("</h3>")
    append("<span class=\"period\">").append(right.esc()).append("</span></div>\n")
}

/**
 * `avoid` opts a section into `break-inside: avoid`. It is off for Experience and Projects on
 * purpose — see the note in [projectEntries].
 */
private inline fun StringBuilder.section(title: String, avoid: Boolean, body: StringBuilder.() -> Unit) {
    append("<section").append(if (avoid) " class=\"avoid\"" else "").append(">\n")
    append("<h2>").append(title).append("</h2>\n")
    body()
    append("</section>\n")
}

/**
 * Minimal HTML text escape.
 *
 * `&` must go first or it would re-escape the ampersands the later replacements just wrote.
 * Quotes are not escaped because nothing here lands in an attribute value — every call site is
 * element text content. If that ever changes, escape `"` here rather than at the call site.
 */
private fun String.esc(): String = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

// -------------------------------------------------------------------------------------------
// Print stylesheet
// -------------------------------------------------------------------------------------------

/**
 * Physical units (pt/mm) throughout, because this document exists only to be paginated — `px`
 * would make the type size depend on the browser's print DPI guess.
 *
 * ponytail: a system font stack, not the site's display face. Embedding a webfont as a base64
 * data URI would cost ~80 KB in the wasm binary and buy a nicer `h1`; upgrade path is
 * Res.font + a data-URI @font-face if the typography ever matters more than the payload.
 */
private val PrintCss = """
:root { color-scheme: light; }
* { box-sizing: border-box; }
body {
  margin: 0;
  background: #e4e4e7;
  color: #18181b;
  font-family: ui-sans-serif, -apple-system, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
  font-size: 10pt;
  line-height: 1.42;
  -webkit-print-color-adjust: exact;
  print-color-adjust: exact;
}
.resume { max-width: 210mm; margin: 0 auto; background: #fff; padding: 14mm 13mm; }

.rh { border-bottom: 2px solid #18181b; padding-bottom: 9pt; }
h1 { margin: 0; font-size: 21pt; font-weight: 700; letter-spacing: -0.015em; }
.role { margin: 2pt 0 0; font-size: 12pt; font-weight: 500; color: #3f3f46; }
.meta { margin: 4pt 0 0; font-size: 8.5pt; color: #52525b; }

section { margin-top: 11pt; }
h2 {
  margin: 0 0 5pt;
  font-size: 8pt;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.16em;
  color: #71717a;
}
h3 { margin: 0; font-size: 10pt; font-weight: 700; }

.lede { margin: 0; font-size: 10pt; color: #3f3f46; }
.tight { margin: 1pt 0 0; font-size: 9.5pt; line-height: 1.35; color: #3f3f46; }
.oss { margin-top: 6pt; }
strong { color: #18181b; font-weight: 600; }

.head { display: flex; align-items: baseline; justify-content: space-between; gap: 12pt; }
.period { flex-shrink: 0; white-space: nowrap; font-size: 8.5pt; color: #71717a; }

.entry { margin-top: 8pt; }
.entry ul { margin: 3pt 0 0; padding-left: 13pt; }
.entry li { font-size: 9.5pt; line-height: 1.35; color: #3f3f46; margin-top: 1.5pt; }

.chips { display: flex; flex-wrap: wrap; gap: 4pt; }
.chip {
  border: 0.75pt solid #d4d4d8;
  border-radius: 3pt;
  padding: 1.5pt 6pt;
  font-size: 8.5pt;
  font-weight: 500;
  color: #3f3f46;
}
.skills p:first-child { margin-top: 0; }

@page { size: A4; margin: 12mm; }
@media print {
  body { background: #fff; }
  .resume { max-width: none; margin: 0; padding: 0; }
  /* Only on entries and the short sections. Experience and Projects are taller than a page,
     so an avoid on them would emit a blank page instead of a clean break. */
  .avoid { break-inside: avoid; page-break-inside: avoid; }
  h2, h3 { break-after: avoid; page-break-after: avoid; }
}
""".trimIndent()

// -------------------------------------------------------------------------------------------
// Self-check
// -------------------------------------------------------------------------------------------

/**
 * The check for a function whose whole job is string assembly: that every section actually made
 * it in, that the tags balance, and that escaping did not eat the markup.
 *
 * Same shape and reasoning as `navSelfCheck()` — this module has no test source set, and a
 * `check`-based function that any target can call is cheaper than adding one.
 */
internal fun resumeHtmlSelfCheck() {
    val html = buildResumeHtml()

    check(html.startsWith("<!doctype html>")) { "must be a standalone document, not a fragment" }
    check(html.trimEnd().endsWith("</html>")) { "document must be closed" }
    check(html.count { it == '<' } == html.count { it == '>' }) { "unbalanced angle brackets" }
    check("<article" in html && "</article>" in html) { "article must open and close" }

    // Every section the React résumé has, by heading.
    listOf(
        "Professional Summary", "Core Competencies", "Key Results",
        "Experience", "Projects &amp; Open Source", "Education", "Technical Skills",
    ).forEach { check("<h2>$it</h2>" in html) { "missing section: $it" } }

    // Content actually rendered, not just chrome.
    check(profile.name in html) { "name" }
    check(profile.email in html) { "email" }
    check(experience.all { it.company.replace("&", "&amp;") in html }) { "every job" }
    check(projects.all { it.name in html }) { "every project" }
    check(competencies.size == html.split("<span class=\"chip\">").size - 1) { "every competency chip" }
    // `.replace("&", "&amp;")` on the needle, not the haystack: "Data & Networking" is a group
    // name and it must arrive escaped, so the raw string would (correctly) not be found.
    check(resumeSkills.all { "<strong>${it.group.replace("&", "&amp;")}:</strong>" in html }) {
        "every skill group"
    }

    // Escaping: "Jugnoo / Tookan / Jungleworks" has no entity, but a `&` anywhere must arrive
    // as `&amp;` and never as a bare `&` that would swallow the following text in a parser.
    check(!Regex("&(?!(amp|lt|gt);)").containsMatchIn(html)) { "bare ampersand escaped the escaper" }

    // Print rules are the entire point — a document without them prints as a web page.
    check("@page" in html && "break-inside: avoid" in html) { "print rules" }
    check("http" !in html.substringBefore("<body")) { "no external asset may be referenced" }
}
