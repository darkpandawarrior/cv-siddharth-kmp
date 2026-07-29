package com.siddharth.cv.shared.terminal

import com.siddharth.cv.shared.Route
import com.siddharth.cv.shared.data.caseStudies
import com.siddharth.cv.shared.data.education
import com.siddharth.cv.shared.data.experience
import com.siddharth.cv.shared.data.metrics
import com.siddharth.cv.shared.data.openSource
import com.siddharth.cv.shared.data.profile
import com.siddharth.cv.shared.data.projectBySlug
import com.siddharth.cv.shared.data.projectOrder
import com.siddharth.cv.shared.data.projects
import com.siddharth.cv.shared.data.recentGrowth
import com.siddharth.cv.shared.data.resumeSkills
import com.siddharth.cv.shared.data.siteRooms
import com.siddharth.cv.shared.data.skills

/**
 * The command layer of cv-siddharth/src/Terminal.tsx, ported as pure Kotlin.
 *
 * No Compose imports on purpose: this is a `String -> TermResult` function and nothing more, so it
 * is unit-testable from any target and [demo] at the bottom of this file is the whole test suite.
 *
 * Colour travels as a [TermTone] per line rather than as markup inside the text, so the UI never
 * parses output — the one rule that keeps the renderer a `BasicText` per line.
 *
 * Everything reads the compiled-in data objects. There is no network in v1, which is why `ask`
 * (the AI console) answers with an error instead of pretending.
 */
enum class TermTone { OUT, DIM, ACCENT, ACCENT2, ERROR, HEAD }

data class TermLine(val text: String, val tone: TermTone = TermTone.OUT)

/**
 * What a command did. [clear] wipes the screen (the UI re-prints [TerminalEngine.banner]),
 * [navigate] hands a destination to the nav state, [lines] is everything printed.
 */
data class TermResult(
    val lines: List<TermLine> = emptyList(),
    val clear: Boolean = false,
    val navigate: Route? = null,
)

private const val PROMPT_USER = "guest"
private const val PROMPT_HOST = "sid.android"

/** ASCII only — no glyph here is outside the mono face the wasm build ships. */
private val WORDMARK = listOf(
    """   ___  _  ___  """,
    """  / __|| ||   \ """,
    """  \__ \| || |) |""",
    """  |___/|_||___/ """,
)

/** The neofetch mascot. Nine lines so it sits flush against the nine key/value rows. */
private val DROID = listOf(
    """    \         /    """,
    """     \_______/     """,
    """    /         \    """,
    """   |  o     o  |   """,
    """  =|           |=  """,
    """   |    ___    |   """,
    """   |   \___/   |   """,
    """    \_________/    """,
    """    |_|     |_|    """,
)

/** `cat` targets. [ls] prints exactly this list, so the two can never drift apart. */
private val FILES = listOf("resume.txt", "profile.txt", "skills.txt")

private val ROOMS = listOf("home/", "resume/", "terminal/", "projects/")

private class Cmd(
    val name: String,
    val usage: String,
    val help: String,
    val hidden: Boolean = false,
    val run: (List<String>) -> TermResult,
)

// Small builders. `lines(...)` keeps every command body a flat list of (text, tone) pairs.
private fun out(vararg l: TermLine) = TermResult(l.toList())

private fun out(l: List<TermLine>) = TermResult(l)

private fun err(text: String) = TermResult(listOf(TermLine(text, TermTone.ERROR)))

private fun head(text: String) = TermLine(text, TermTone.HEAD)

private fun dim(text: String) = TermLine(text, TermTone.DIM)

private fun hi(text: String) = TermLine(text, TermTone.ACCENT)

private fun hi2(text: String) = TermLine(text, TermTone.ACCENT2)

object TerminalEngine {
    private val table: List<Cmd> = buildTable()

    /** Every visible command name, in table order. [complete] and `help` both read this. */
    val commands: List<String> = table.filter { !it.hidden }.map { it.name }

    /** The boot screen: wordmark, strapline, and the one hint a first-time visitor needs. */
    val banner: List<TermLine> =
        WORDMARK.map(::hi) +
            listOf(
                hi2("  prototype  ->  platform"),
                dim(""),
                dim("${profile.name} — ${profile.title}"),
                dim("type `help` for commands · `projects` for the builds · `exit` to leave"),
                dim(""),
            )

    /**
     * Runs one input line. The verb is case-insensitive; args split on any run of whitespace.
     * An empty line prints nothing, exactly like a real shell.
     */
    fun run(input: String): TermResult {
        val line = input.trim()
        if (line.isEmpty()) return TermResult()
        val parts = line.split(' ', '\t').filter { it.isNotEmpty() }
        val verb = parts[0].lowercase()
        val args = parts.drop(1)
        val cmd = table.firstOrNull { it.name == verb }
            ?: return err("command not found: ${parts[0]} — try `help`")
        return cmd.run(args)
    }

    /**
     * Tab completion. Returns the single command sharing [prefix], the longest common prefix when
     * several match, or null when nothing does.
     *
     * Once the verb is complete, completion moves to its argument: `open <slug>` over the real
     * project slugs and `cat <file>` over [FILES], so a slug never has to be typed from memory.
     */
    fun complete(prefix: String): String? {
        if (prefix.isBlank()) return null
        val spaced = prefix.indexOf(' ')
        if (spaced > 0) {
            val verb = prefix.substring(0, spaced).lowercase()
            val rest = prefix.substring(spaced + 1).trimStart()
            val pool = when (verb) {
                "open" -> projects.map { it.slug }
                "cat" -> FILES
                else -> return null
            }
            val hit = longestCommonPrefix(pool.filter { it.startsWith(rest) }) ?: return null
            return "$verb $hit"
        }
        val lower = prefix.lowercase()
        return longestCommonPrefix(commands.filter { it.startsWith(lower) })
    }

    private fun longestCommonPrefix(matches: List<String>): String? {
        if (matches.isEmpty()) return null
        if (matches.size == 1) return matches[0]
        val first = matches[0]
        var len = first.length
        for (m in matches) {
            var i = 0
            while (i < len && i < m.length && m[i] == first[i]) i++
            len = i
        }
        return first.substring(0, len)
    }

    // -----------------------------------------------------------------------------------------
    // The command table. `help` renders from it, so a new command documents itself.
    // -----------------------------------------------------------------------------------------
    private fun buildTable(): List<Cmd> {
        val cmds = mutableListOf<Cmd>()
        fun cmd(name: String, help: String, usage: String = name, hidden: Boolean = false, run: (List<String>) -> TermResult) {
            cmds += Cmd(name, usage, help, hidden, run)
        }

        cmd("help", "list everything you can type") { helpText() }

        cmd("whoami", "who is this") {
            out(
                head("${profile.name} · ${profile.title}"),
                dim("${profile.location} · ${education.school}"),
                TermLine(profile.intro),
                dim(""),
                dim("next: projects · skills · metrics · hire"),
            )
        }

        cmd("about", "the longer story") { out(TermLine(profile.summary)) }

        cmd("projects", "the builds — with slugs for `open`") {
            val body = projects.flatMap {
                listOf(
                    hi("${it.name}  (${it.slug})"),
                    TermLine("  ${it.tagline}"),
                    dim("  ${it.status}"),
                )
            }
            out(body + dim("") + dim("-> open <slug> for the full case study, e.g. `open mileway`"))
        }

        cmd("open", "open a project case study", usage = "open <slug>") { args ->
            val slug = args.firstOrNull()?.lowercase().orEmpty()
            val valid = projects.joinToString(", ") { it.slug }
            if (slug.isEmpty()) return@cmd out(dim("usage: open <slug> — $valid"))
            // projectBySlug is the single choke point for an untrusted slug: anything that is not
            // a real project — a typo, a path traversal, an injected directive — resolves to null
            // here rather than reaching the navigator.
            val p = projectBySlug(slug)
                ?: return@cmd err("open: no build \"$slug\". valid slugs: $valid")
            TermResult(
                lines = listOf(TermLine("opening ${p.name} …", TermTone.ACCENT)),
                navigate = Route.ProjectDetail(p.slug),
            )
        }

        cmd("skills", "the tech stack, grouped") {
            out(skills.flatMap { listOf(head(it.group), TermLine("  " + it.items.joinToString(" · "))) })
        }

        cmd("stack", "the granular résumé stack") {
            out(resumeSkills.flatMap { listOf(head(it.group), TermLine("  " + it.items.joinToString(" · "))) })
        }

        cmd("experience", "career timeline") {
            val body = experience.flatMap { job ->
                listOf(head("${job.role} @ ${job.company}"), dim("  ${job.period}")) +
                    job.points.take(3).map {
                        TermLine("  - " + (if (it.label != null) "${it.label}: " else "") + it.text)
                    }
            }
            out(body + dim("") + dim("${education.degree} @ ${education.school} · ${education.period}"))
        }

        cmd("metrics", "the headline numbers") {
            val body = metrics.flatMap { listOf(hi("${it.value.padEnd(6)}${it.label}"), dim("  ${it.detail}")) }
            val cases = caseStudies.map { dim("${it.metric.padEnd(24)}  ${it.title}") }
            out(body + dim("") + head("case studies") + cases)
        }

        cmd("education", "where the degree came from") {
            out(TermLine(education.degree), dim("${education.school} · ${education.period}"))
        }

        cmd("contact", "how to reach me") {
            out(
                TermLine("email     ${profile.email}"),
                TermLine("phone     ${profile.phone}"),
                TermLine("github    ${profile.github}"),
                TermLine("linkedin  ${profile.linkedin}"),
                TermLine("where     ${profile.location}"),
                dim(""),
                dim(profile.availability),
            )
        }

        cmd("resume", "open the full résumé") {
            TermResult(lines = listOf(hi("opening résumé …")), navigate = Route.Resume)
        }

        cmd("growth", "recently shipped, newest first") {
            out(recentGrowth.reversed().flatMap { listOf(hi("${it.date}  ${it.title}"), dim("  ${it.detail}")) })
        }

        cmd("oss", "merged open-source contributions") {
            out(
                openSource.flatMap {
                    listOf(hi("[${it.status}] ${it.title}"), dim("  ${it.repo} · ${it.date} · ${it.url}"))
                },
            )
        }

        cmd("rooms", "the interactive rooms on the web build") {
            val body = siteRooms.flatMap {
                listOf(hi("${it.to.padEnd(12)}${it.label}"), TermLine("  ${it.blurb}"), dim("  ${it.tag}"))
            }
            out(body + dim("") + dim("these live on the React build — this one ships home, resume, terminal & projects"))
        }

        cmd("hire", "the recruiter pitch") {
            out(
                head("Senior Android engineer · platform owner at 50k+ MAU"),
                TermLine("GPS 50% -> 95% · crashes -80% · ~87% of UI-layer code in Compose across ~960k LOC."),
                dim(profile.availability),
                dim(""),
                hi("mailto:${profile.email}"),
                dim("or type `resume` for the full thing"),
            )
        }

        cmd("neofetch", "the system readout") { out(neofetch()) }

        cmd("banner", "reprint the banner") { out(banner) }

        cmd("ls", "list files & rooms") {
            out(
                hi(FILES.joinToString("   ")),
                dim(ROOMS.joinToString("   ")),
                dim(""),
                dim("cat <file> to read · projects to list the builds"),
            )
        }

        cmd("cat", "read resume.txt / profile.txt / skills.txt", usage = "cat <file>") { args ->
            when (args.firstOrNull()?.lowercase()?.removeSuffix(".txt")) {
                "resume" -> out(
                    TermLine(profile.summary),
                    dim(""),
                    dim("-> `resume` opens the laid-out version"),
                )
                "profile" -> out(
                    head("${profile.name} — ${profile.resumeTitle}"),
                    dim("${profile.location} · ${profile.email} · ${profile.phone}"),
                    dim(""),
                    TermLine(profile.tagline),
                    TermLine(profile.intro),
                )
                "skills" -> out(skills.map { TermLine(it.group.padEnd(22) + it.items.joinToString(" · ")) })
                null, "" -> out(dim("usage: cat <${FILES.joinToString("|")}>"))
                else -> err("cat: ${args[0]}: No such file. Try `ls`.")
            }
        }

        cmd("ask", "ask the AI console (web build only)", usage = "ask <question>") {
            err("ask: the AI console is only on the web build (cv-siddharth) — it needs a server round trip this offline build deliberately does not ship.")
        }

        cmd("theme", "the palettes this build uses", usage = "theme") {
            out(
                dim("this shell doesn't recolour — theming here is structural, not a toggle:"),
                hi("  site      #3ddc84 / #5ee6ff   (the default CvTheme)"),
                hi2("  project   each build overrides accent + surface on its detail page"),
                dim("  resume    the same mechanism, inverted to dark-on-light"),
                dim(""),
                dim("open kursi and watch every accent below the header re-resolve."),
            )
        }

        cmd("echo", "print text", usage = "echo <text>", hidden = true) { args ->
            out(TermLine(args.joinToString(" ")))
        }

        cmd("date", "current date/time") { out(dim(nowText())) }

        cmd("clear", "clear the screen") { TermResult(clear = true) }

        cmd("exit", "back to the portfolio") {
            TermResult(lines = listOf(dim("logging out…")), navigate = Route.Home)
        }

        // ── easter eggs, hidden from `help` ─────────────────────────────────────────────────
        cmd("sudo", "", hidden = true) { args ->
            when {
                args.joinToString(" ").contains("hire") ->
                    TermResult(
                        lines = listOf(hi("access granted. routing you to the hiring channel -> ${profile.email}")),
                        navigate = Route.Home,
                    )
                args.firstOrNull() == "rm" ->
                    err("nice try. this shell is read-only — the code is all on ${profile.github} though.")
                else -> out(dim("$PROMPT_USER is not in the sudoers file. This incident will be reported. (try `sudo hire`)"))
            }
        }

        cmd("matrix", "", hidden = true) {
            out(hi("Wake up, Neo… the crashes are down 80%. There is no spoon, only structured concurrency."))
        }

        cmd("coffee", "", hidden = true) {
            out(TermLine("brewing…"), dim("HTTP 418: I'm a teapot. Ship anyway."))
        }

        cmd("uptime", "", hidden = true) {
            out(TermLine("up 5+ years, load average: ~960k LOC, 50k MAU, 0 dropped pagers"))
        }

        cmd("vim", "", hidden = true) { out(dim("you're already in the best editor — Android Studio. :q!")) }

        cmd("man", "", hidden = true) { args ->
            out(dim("man: no manual entry for ${args.firstOrNull() ?: "that"}. This is a portfolio, not GNU. Type `help`."))
        }

        return cmds
    }

    private fun helpText(): TermResult {
        val visible = table.filter { !it.hidden }
        val width = visible.maxOf { it.usage.length } + 3
        val rows = visible.map { TermLine("  ${it.usage.padEnd(width)}${it.help}") }
        return out(
            listOf(head("commands")) + rows +
                listOf(
                    dim(""),
                    dim("up/down walks history · Tab completes · `open <slug>` and `cat <file>` complete too"),
                    dim("try: open mileway · metrics · neofetch · hire"),
                ),
        )
    }

    /** The mascot on the left, key/value rows on the right, zipped into one column of text. */
    private fun neofetch(): List<TermLine> {
        val rows = buildList {
            add("role" to profile.title)
            add("host" to "$PROMPT_USER@$PROMPT_HOST")
            add("where" to profile.location)
            add("kernel" to "Kotlin · Compose Multiplatform · KMP")
            add("shell" to "cv-siddharth-kmp (wasm · one canvas)")
            // Value only, never value + detail: a row long enough to wrap would shear the mascot
            // off the left column. `metrics` is the command that prints the detail.
            metrics.forEach { add(it.label to it.value) }
            add("builds" to projectOrder.joinToString(" · "))
        }
        val artWidth = DROID.maxOf { it.length }
        val keyWidth = rows.maxOf { it.first.length } + 2
        val height = maxOf(DROID.size, rows.size)
        return (0 until height).map { i ->
            val art = DROID.getOrNull(i)?.padEnd(artWidth) ?: " ".repeat(artWidth)
            val row = rows.getOrNull(i)
            if (row == null) hi(art) else TermLine("$art  ${row.first.padEnd(keyWidth)}${row.second}", TermTone.ACCENT2)
        }
    }

    /**
     * `kotlin.time.Clock` is the only wall clock available here — there is no `java.time` on wasm
     * and this module deliberately has no kotlinx-datetime dependency. `Instant.toString()` is
     * ISO-8601 UTC, which is the honest thing for a shell to print anyway.
     */
    @OptIn(kotlin.time.ExperimentalTime::class)
    private fun nowText(): String = kotlin.time.Clock.System.now().toString()
}

/**
 * ponytail: the whole test suite. No framework — call it from a scratch main() or a test target.
 * Covers the four things that actually break: slug validation (both directions), completion, and
 * the clear signal.
 */
fun demo() {
    check(TerminalEngine.run("open mileway").navigate == Route.ProjectDetail("mileway")) { "open <slug> must navigate" }
    check(TerminalEngine.run("open ../etc").lines.first().tone == TermTone.ERROR) { "bad slug must error" }
    check(TerminalEngine.complete("pro") == "projects") { "complete(pro) must be projects" }
    check(TerminalEngine.run("clear").clear) { "clear must set the clear flag" }

    // A few more that cost nothing and would catch a table refactor.
    check(TerminalEngine.run("").lines.isEmpty()) { "empty input prints nothing" }
    check(TerminalEngine.run("HELP").lines.isNotEmpty()) { "verb is case-insensitive" }
    check(TerminalEngine.run("nope").lines.first().tone == TermTone.ERROR) { "unknown command errors" }
    check(TerminalEngine.run("exit").navigate == Route.Home) { "exit goes home" }
    check(TerminalEngine.run("resume").navigate == Route.Resume) { "resume opens the résumé" }
    check(TerminalEngine.complete("zzz") == null) { "no match completes to null" }
    check(TerminalEngine.complete("open mile") == "open mileway") { "arg completion" }
    check(TerminalEngine.run("ask anything").lines.first().tone == TermTone.ERROR) { "ask is unavailable offline" }
}
