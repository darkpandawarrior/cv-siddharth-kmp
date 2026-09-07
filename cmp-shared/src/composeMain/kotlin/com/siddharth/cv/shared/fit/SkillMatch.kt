package com.siddharth.cv.shared.fit

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

/**
 * A job description scored against Siddharth's stack with no model involved.
 *
 * LIVES IN composeMain, NOT commonMain, despite being plain Kotlin with zero Compose imports —
 * same reason `ChatClient.kt` does: `iosX64`/watchOS are bare Kotlin/Native targets with no
 * Compose runtime on their classpath (see cmp-shared/build.gradle.kts's own comment), but the
 * Compose Compiler Gradle plugin still activates for every compilation in this module. A
 * commonMain file with real content is enough to trigger `compileKotlinIosX64` for the first
 * time and hit "Compose Compiler requires the Compose Runtime to be on the classpath, but none
 * could be found" — confirmed by moving this file here, which was the first thing to add any
 * `.kt` content to commonMain at all. FitCheckScreen (composeMain) is this file's only consumer
 * anyway, so composeMain costs nothing.
 *
 * Port of `cv-siddharth/src/lib/skillMatch.ts` — same table, same weights, same scoring formula.
 * See that file's own doc comment for the full "why this exists" (the LLM path can rate-limit,
 * 502, or stream back nothing at all; this module means the analyzer always has something true
 * to say) and the measured delta against the model (73 here vs 58 from the model on the JD this
 * was calibrated against — a keyword match, not a judgement, and [toFitReport]'s summary says so).
 *
 * This file also carries [parseJdFitDirective], which `skillMatch.ts` has no counterpart for: the
 * React client already had a general-purpose generative-UI directive parser (`chatBlocks.ts`) that
 * `[[jdfit:{…}]]` rides on for free. This port has no such parser (the floating chat panel strips
 * every directive rather than rendering one — see `FloatingChat.kt`'s `stripDirectives`), and
 * building the general one is a whole subsystem for a feature that emits exactly one directive
 * kind. So `FitCheckScreen` (composeMain/fit) owns its own request/response cycle end to end
 * rather than routing through the chat transcript, and this is the one-directive parser that
 * decision needs.
 * ponytail: if a second directive kind ever needs parsing, promote this to the general brace-
 * counting scanner `chatBlocks.ts` has (endOfJson over any `[[name:{…}]]`) — not before.
 */

/** How well established a skill is. Weighting by depth is what keeps the score honest. */
enum class Depth { Deep, Working, Familiar }

/** Deliberately harsh on [Depth.Familiar] — see skillMatch.ts's own doc for the measured reason. */
private val DEPTH_WEIGHT: Map<Depth, Double> = mapOf(Depth.Deep to 1.0, Depth.Working to 0.7, Depth.Familiar to 0.25)

/** A requirement stated as "nice to have" counts, but not like a hard one. */
private const val PREFERRED_WEIGHT = 0.35

data class Skill(
    /** Display name — what the card calls it. */
    val name: String,
    val depth: Depth,
    /** Why he has it, in the card's voice. Public site facts only. */
    val evidence: String,
    /** Every way a JD might name it. Matched whole-word, case-insensitively. */
    val aliases: List<String>,
)

/**
 * The stack, as the site already publishes it (`data/CvProfileData.kt`). `aliases` is the
 * load-bearing field — see skillMatch.ts's own comment on why a canonical-spelling-only matcher
 * under-reports the fit.
 */
val SKILLS: List<Skill> = listOf(
    Skill(
        "Jetpack Compose", Depth.Deep,
        "~87% of a ~964k-line production app is Compose, including the View interop layer",
        listOf("jetpack compose", "compose", "compose ui", "material 3", "material3", "material design 3"),
    ),
    Skill(
        "Kotlin", Depth.Deep,
        "Primary language for 5+ years across every production app he's shipped",
        listOf("kotlin"),
    ),
    Skill(
        "Coroutines & Flow", Depth.Deep,
        "StateFlow/SharedFlow and structured concurrency across ~180 ViewModels",
        listOf(
            "coroutine", "coroutines", "kotlin coroutines", "flow", "stateflow", "sharedflow",
            "structured concurrency", "reactive programming", "rxjava",
        ),
    ),
    Skill(
        "MVVM & Clean Architecture", Depth.Deep,
        "Single-UiState MVI over a repository layer, ~180 ViewModels in production",
        listOf(
            "mvvm", "mvi", "clean architecture", "architecture patterns", "repository pattern",
            "design patterns", "solid",
        ),
    ),
    Skill(
        "Room / SQLite", Depth.Deep,
        "24 schema migrations across 2 production databases; 47 sequential non-destructive " +
            "migrations in Doori (schema v48)",
        listOf("room", "sqlite", "sqldelight", "local database", "persistence", "orm", "datastore"),
    ),
    Skill(
        "Dependency injection (Hilt/Dagger)", Depth.Deep,
        "Hilt across the whole graph — scoping, assisted injection, HiltWorker",
        listOf("hilt", "dagger", "dependency injection", "koin", "di framework"),
    ),
    Skill(
        "Android SDK & platform", Depth.Deep,
        "5+ years; foreground services, WorkManager, background execution limits, OEM quirks",
        listOf(
            "android", "android sdk", "android development", "native android", "workmanager",
            "foreground service", "foreground services", "background processing", "services",
            "broadcast receiver",
        ),
    ),
    Skill(
        "Networking (Retrofit/OkHttp)", Depth.Deep,
        "Retrofit + OkHttp with certificate pinning against a multi-tenant backend",
        listOf(
            "retrofit", "okhttp", "rest", "rest api", "restful", "api integration", "networking",
            "graphql", "ktor",
        ),
    ),
    Skill(
        "Mobile security", Depth.Deep,
        "Android Keystore field-level encryption (AES-256), SSL pinning across 9 domains (5 " +
            "SHA-256 pins), biometric access gate, EncryptedSharedPreferences/DataStore+Tink — " +
            "VAPT-cleared",
        listOf(
            "security", "encryption", "keystore", "ssl pinning", "certificate pinning", "biometric",
            "biometrics", "vapt", "penetration test", "owasp", "secure storage",
        ),
    ),
    Skill(
        "Location & motion engineering", Depth.Deep,
        "Predictive dead reckoning over GPS/IMU with Kalman smoothing and spike rejection — " +
            "accuracy 50% → 95%; MotionFusion gravity/linear-acceleration filtering in Doori",
        listOf(
            "location", "gps", "geolocation", "sensor", "sensors", "maps", "google maps",
            "geofencing", "location tracking", "imu", "accelerometer", "kalman",
        ),
    ),
    Skill(
        "CI/CD & release", Depth.Deep,
        "Fastlane build/sign/upload, Play Store release ownership, Gradle Kotlin DSL",
        listOf(
            "ci/cd", "ci cd", "continuous integration", "continuous delivery", "fastlane", "gradle",
            "jenkins", "github actions", "bitrise", "play store", "app store", "release management",
            "build system",
        ),
    ),
    Skill(
        "Crash & analytics tooling", Depth.Deep,
        "Crashlytics + Sentry + Mixpanel; drove an 80% crash reduction",
        listOf(
            "crashlytics", "sentry", "firebase", "analytics", "mixpanel", "observability",
            "monitoring", "logging", "instrumentation",
        ),
    ),
    Skill(
        "Kotlin Multiplatform", Depth.Working,
        "Four shipped KMP/CMP projects targeting Android, iOS, desktop and Wasm",
        listOf(
            "kmp", "kotlin multiplatform", "compose multiplatform", "cmp", "multiplatform",
            "cross-platform", "cross platform",
        ),
    ),
    Skill(
        "iOS (via Compose Multiplatform)", Depth.Familiar,
        "Ships iOS targets from shared KMP code — not a native UIKit/SwiftUI specialist",
        listOf("ios", "swift", "swiftui", "uikit", "xcode", "objective-c", "apple"),
    ),
    Skill(
        "Testing", Depth.Working,
        "Unit tests over ViewModels and repositories with fakes; gdUnit4 on the Godot build",
        listOf(
            "testing", "unit test", "unit testing", "unit tests", "junit", "espresso",
            "test automation", "tdd", "mockk", "mockito", "instrumentation test",
        ),
    ),
    Skill(
        "Performance engineering", Depth.Working,
        "Compose compiler metrics run during the migration push, recomposition/stability work; " +
            "80% crash reduction at 50k MAU",
        listOf(
            "performance", "optimization", "optimisation", "profiling", "memory management", "anr",
            "jank", "baseline profile", "app startup", "rendering",
        ),
    ),
    Skill(
        "Modular architecture", Depth.Working,
        "Multi-module production codebase at ~964k LOC with feature-level boundaries",
        listOf(
            "modular", "modularization", "modularisation", "multi-module", "multi module",
            "monorepo", "feature module",
        ),
    ),
    Skill(
        "Team leadership & ownership", Depth.Working,
        "Android platform owner at Dice.tech and product owner for the mobile surface",
        listOf(
            "mentor", "mentoring", "mentorship", "lead", "leadership", "tech lead", "code review",
            "code reviews", "ownership", "stakeholder", "cross-functional", "agile", "scrum",
        ),
    ),
    Skill(
        "AI-assisted development", Depth.Working,
        "Agentic workflows in daily production use — Claude Code, Firebender, MCP servers",
        listOf(
            "ai tools", "ai-assisted", "copilot", "github copilot", "cursor", "claude", "chatgpt",
            "llm", "genai", "generative ai", "mcp", "prompt engineering", "ai coding",
        ),
    ),
    Skill(
        "Java", Depth.Working,
        "Legacy Android modules and interop; Kotlin is the primary language",
        listOf("java"),
    ),
    Skill(
        "Flutter / Dart", Depth.Familiar,
        "Dart listed among his languages; Android-native is where the depth is",
        listOf("flutter", "dart"),
    ),
    Skill(
        "Web frontend", Depth.Familiar,
        "This site — React 19, TanStack Start, Vite, deployed on Vercel Edge",
        listOf(
            "react", "typescript", "javascript", "frontend", "front-end", "web development",
            "next.js", "nextjs", "vue", "angular", "tailwind", "html", "css",
        ),
    ),
)

/**
 * Terms a JD may ask for that the stack doesn't cover. Naming them is what lets the offline path
 * report an honest gap instead of a suspiciously clean sheet — see skillMatch.ts's own note that
 * this is a vocabulary of general industry technologies, not a record of anyone's weaknesses.
 */
val OFF_STACK_TERMS: Map<String, String> = linkedMapOf(
    "rust" to "Rust",
    "golang" to "Go",
    "kubernetes" to "Kubernetes",
    "k8s" to "Kubernetes",
    "docker" to "Docker",
    "terraform" to "Terraform",
    "aws" to "AWS",
    "azure" to "Azure",
    "gcp" to "GCP",
    "react native" to "React Native",
    "unity" to "Unity",
    "machine learning" to "Machine learning",
    "deep learning" to "Deep learning",
    "pytorch" to "PyTorch",
    "tensorflow" to "TensorFlow",
    "kafka" to "Kafka",
    "microservices" to "Microservices",
    "postgresql" to "PostgreSQL",
    "postgres" to "PostgreSQL",
    "mongodb" to "MongoDB",
    "redis" to "Redis",
    "elasticsearch" to "Elasticsearch",
    "python" to "Python",
    "ruby" to "Ruby",
    "php" to "PHP",
    "c#" to "C#",
    ".net" to ".NET",
    "scala" to "Scala",
    "elixir" to "Elixir",
    "spring boot" to "Spring Boot",
    "django" to "Django",
    "embedded c" to "Embedded C",
    "webrtc" to "WebRTC",
    "blockchain" to "Blockchain",
    "solidity" to "Solidity",
)

/**
 * Whole-word, case-insensitive containment.
 *
 * Written by hand rather than with a `\b` regex — same reason as skillMatch.ts's own: the terms
 * include `c#`, `.net` and `ci/cd`, whose edges are not word characters, and this checks that the
 * characters adjacent to the hit are not letters/digits, which is what stops "Java" matching
 * JavaScript and "Go" matching every "go-to" in the document.
 */
fun mentions(haystack: String, term: String): Boolean {
    val h = haystack.lowercase()
    val t = term.lowercase()
    if (t.isEmpty()) return false
    var from = 0
    while (true) {
        val i = h.indexOf(t, from)
        if (i == -1) return false
        val before = if (i == 0) null else h[i - 1]
        val after = if (i + t.length >= h.length) null else h[i + t.length]
        if (isEdge(before) && isEdge(after)) return true
        from = i + t.length
    }
}

/** "+" is an edge character so "C++" and "Compose + Material" behave. */
private fun isEdge(c: Char?): Boolean = c == null || !(c in 'a'..'z' || c in '0'..'9' || c == '+')

/** A line that marks its content as optional rather than required. */
private val PREFERRED_LINE = Regex(
    "\\b(prefer(?:red|able|ably)?|nice[ -]to[ -]have|desirable|bonus|plus|ideally|good to have|" +
        "advantage|a plus|would be great)\\b",
    RegexOption.IGNORE_CASE,
)

/** A heading that puts the whole section that follows into "optional". */
private val PREFERRED_HEADING = Regex(
    "^[\\s#*_>\\-•\\d.)]*(nice[ -]to[ -]have|preferred|desirable|bonus|good to have|pluses)",
    RegexOption.IGNORE_CASE,
)

/** A heading that ends a "nice to have" section and returns to hard requirements. */
private val REQUIREMENT_HEADING = Regex(
    "^[\\s#*_>\\-•\\d.)]*(requirement|must[ -]have|qualification|responsibilit|what you)",
    RegexOption.IGNORE_CASE,
)

/**
 * Whether a term is asked for as a hard requirement or a nice-to-have. Reads the line the term is
 * on, plus the section it sits under — "Nice to have" is usually a heading with plain bullets
 * beneath it, so line-level checking alone would score every one of those bullets as mandatory.
 */
private fun preferenceByLine(text: String): List<Boolean> {
    val lines = text.split("\n")
    val out = ArrayList<Boolean>(lines.size)
    var sectionPreferred = false
    for (line in lines) {
        val t = line.trim()
        val isHeading = t.isNotEmpty() && t.length <= 80 && t.firstOrNull() !in BULLET_CHARS
        if (isHeading) {
            if (PREFERRED_HEADING.containsMatchIn(t)) {
                sectionPreferred = true
            } else if (REQUIREMENT_HEADING.containsMatchIn(t)) {
                sectionPreferred = false
            }
        }
        out.add(sectionPreferred || PREFERRED_LINE.containsMatchIn(line))
    }
    return out
}

private val BULLET_CHARS = charArrayOf('-', '*', '•').toList()

/** Is this term preferred-only everywhere it appears? */
private fun isPreferred(text: String, term: String): Boolean {
    val lines = text.split("\n")
    val prefs = preferenceByLine(text)
    var seen = false
    var allPreferred = true
    for (i in lines.indices) {
        if (mentions(lines[i], term)) {
            seen = true
            if (!prefs[i]) allPreferred = false
        }
    }
    return seen && allPreferred
}

data class MatchedSkill(
    val skill: Skill,
    /** The alias the JD actually used — worth echoing back verbatim. */
    val askedAs: String,
    val preferred: Boolean,
)

data class MissingTerm(val term: String, val preferred: Boolean)

data class SkillMatchResult(
    val matched: List<MatchedSkill>,
    /**
     * Skills the JD requires outright that he only has shallowly. Split out from [matched] — see
     * skillMatch.ts's own doc on why calling these strengths is how a fit tool loses a recruiter's
     * trust. A *preferred* shallow skill stays in [matched]: having some of a nice-to-have
     * genuinely is a plus.
     */
    val partial: List<MatchedSkill>,
    val missing: List<MissingTerm>,
    /** 0-100. Only meaningful when the JD named something recognisable. */
    val score: Int,
    /** How many distinct things the JD asked for that were recognised at all. */
    val asked: Int,
    val role: String? = null,
)

private val TITLE_PREFIXES = listOf("job title", "role", "position")

/** Strips a leading "Job Title:" / "Role -" style label, if the line opens with one. */
private fun stripTitlePrefix(line: String): String {
    val lower = line.lowercase()
    for (prefix in TITLE_PREFIXES) {
        if (!lower.startsWith(prefix)) continue
        val rest = line.substring(prefix.length).trimStart()
        if (rest.startsWith(":") || rest.startsWith("-")) return rest.drop(1).trimStart()
    }
    return line
}

// "app" is separate from "application" on purpose — "Senior Mobile App Developer" is a real title
// this must match, and "application" doesn't match it.
private val TITLE_SHAPE = Regex(
    "^((?:senior|staff|principal|lead|sr\\.?|junior|jr\\.?|mid[- ]level)\\s+)?" +
        "((?:mobile|android|ios|software|full[ -]?stack|front[- ]?end|back[- ]?end|web|app|" +
        "application|native)\\s+)*(engineer|developer|architect|programmer)\\b",
    RegexOption.IGNORE_CASE,
)

private val WHITESPACE_RUN = Regex("\\s+")

/**
 * The role title, if the JD leads with one. Only the first few lines are considered — a JD
 * mentions job titles throughout ("reporting to the Engineering Manager"), and scanning the whole
 * document reliably picks up the wrong one.
 */
fun extractRole(text: String): String? {
    for (raw in text.split("\n").take(6)) {
        val t = stripTitlePrefix(raw.trim()).trim()
        val looksLikeATitle = t.isNotEmpty() && t.length <= 100 && TITLE_SHAPE.containsMatchIn(t)
        if (!looksLikeATitle) continue
        // Return the line rather than just the matched span, so a trailing qualifier like
        // "(ModalX)" or "— Payments" survives into the card.
        return t.replace(WHITESPACE_RUN, " ")
    }
    return null
}

/**
 * Score a JD against the stack. Pure, synchronous, no network.
 *
 * The score is share-of-weighted-demand: everything the JD asked for and got, over everything it
 * asked for, with hard requirements counting for roughly three times a nice-to-have and shallower
 * skills contributing less than deep ones. A JD naming nothing recognisable scores 0 with
 * `asked == 0`, which is the caller's signal that there is nothing worth showing.
 */
fun matchJd(text: String): SkillMatchResult {
    val all = SKILLS.mapNotNull { skill ->
        val hit = skill.aliases.firstOrNull { mentions(text, it) } ?: return@mapNotNull null
        MatchedSkill(skill, hit, isPreferred(text, hit))
    }
    // Shallow + demanded outright = a qualified gap, not a strength (see SkillMatchResult.partial).
    val partial = all.filter { it.skill.depth == Depth.Familiar && !it.preferred }
    val matched = all.filterNot { it in partial }

    val missing = ArrayList<MissingTerm>()
    val seenLabels = HashSet<String>()
    for ((term, label) in OFF_STACK_TERMS) {
        if (label in seenLabels || !mentions(text, term)) continue
        seenLabels.add(label)
        missing.add(MissingTerm(label, isPreferred(text, term)))
    }

    fun weigh(preferred: Boolean, depth: Depth? = null): Double =
        (if (preferred) PREFERRED_WEIGHT else 1.0) * (depth?.let { DEPTH_WEIGHT.getValue(it) } ?: 1.0)

    // Credit earned: full weight for what he has, partial credit for the shallow ones.
    val earned = (matched + partial).sumOf { weigh(it.preferred, it.skill.depth) }
    // Demand: everything asked for at its full asking weight — the shortfall on a shallow skill,
    // and the whole of an unmet term, both land here, which is what pulls the score below raw
    // keyword coverage.
    val demanded = (matched + partial).sumOf { weigh(it.preferred) } + missing.sumOf { weigh(it.preferred) }

    val asked = matched.size + partial.size + missing.size
    val score = if (demanded > 0) (earned / demanded * 100).roundToInt() else 0
    return SkillMatchResult(matched, partial, missing, score, asked, extractRole(text))
}

/** What the model's `[[jdfit:{…}]]` directive carries, and what the offline path renders into. */
data class JdFitReport(
    /** 0-100, clamped so the renderer can trust it. */
    val score: Int,
    /** The role title, if the JD stated one. */
    val role: String? = null,
    val summary: String,
    val strengths: List<Strength>,
    val gaps: List<Gap>,
) {
    data class Strength(val need: String, val evidence: String, val project: String? = null)
    data class Gap(val need: String, val note: String)
}

/**
 * A match rendered as the same [JdFitReport] the model produces, so [FitCheckScreen] needs no
 * second card layout for the offline path.
 *
 * Capped at the card's comfortable size (4 strengths, 3 gaps) and ordered deep-first, so the
 * strongest true things are the ones that survive the cut.
 */
fun toFitReport(m: SkillMatchResult, final: Boolean = false): JdFitReport {
    val rank = mapOf(Depth.Deep to 0, Depth.Working to 1, Depth.Familiar to 2)
    val strengths = m.matched
        .sortedWith(compareBy({ rank.getValue(it.skill.depth) }, { if (it.preferred) 1 else 0 }))
        .take(4)
        .map { JdFitReport.Strength(need = it.skill.name, evidence = it.skill.evidence) }

    // Qualified gaps lead: "has some of this" is more useful to a recruiter than "has none of
    // this", and it carries the evidence that makes it checkable.
    val gaps = (
        m.partial.map { JdFitReport.Gap(need = it.skill.name, note = it.skill.evidence) } +
            m.missing.sortedBy { if (it.preferred) 1 else 0 }.map { g ->
                JdFitReport.Gap(
                    need = g.term,
                    note = if (g.preferred) {
                        "Listed as a nice-to-have and not part of his stack."
                    } else {
                        "Asked for here, and not something he's worked in."
                    },
                )
            }
        ).take(3)

    val unmet = m.partial.size + m.missing.size
    val summary = buildString {
        append("Matched offline against ${m.matched.size} skill${if (m.matched.size == 1) "" else "s"} in his stack")
        if (unmet > 0) append(", with $unmet this JD asks for that he doesn't fully cover")
        // `final` means the model never arrived, so promising it would be a lie.
        append(
            if (final) {
                ". This is a keyword match rather than a full read — the detailed analysis " +
                    "couldn't be reached just now."
            } else {
                ". This is a keyword match, not a judgement — the full read is on its way."
            },
        )
    }
    return JdFitReport(score = m.score, role = m.role, summary = summary, strengths = strengths, gaps = gaps)
}

// -------------------------------------------------------------------------------------------
// The model's directive, parsed back out of its streamed reply
// -------------------------------------------------------------------------------------------

/** The pasted-JD cap. Mirrors `MAX_JD_CHARS` in `cv-siddharth/api/_lib/chat-handler.ts`. */
const val JD_MAX_CHARS: Int = 12_000

private const val JD_NEAR_CAP_FRACTION = 0.9

/** Whether the composer should flag the paste as close to the server's ceiling. */
fun isJdNearCap(length: Int): Boolean = length > JD_MAX_CHARS * JD_NEAR_CAP_FRACTION

private const val DIRECTIVE_PREFIX = "[[jdfit:"
private const val DIRECTIVE_SUFFIX = "]]"

/** Bounds on what a payload can put on screen — the model's output about text a stranger pasted,
 *  so it is attacker-influenced. Mirrors `chatBlocks.ts`'s `MAX_FIELD_CHARS`/`MAX_ROWS`. */
private const val MAX_FIELD_CHARS = 240
private const val MAX_ROWS = 6

private val directiveJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

@Serializable
private data class JdFitWireStrength(
    val need: String? = null,
    val evidence: String? = null,
    val project: String? = null,
)

@Serializable
private data class JdFitWireGap(val need: String? = null, val note: String? = null)

@Serializable
private data class JdFitWire(
    val score: Int? = null,
    val role: String? = null,
    val summary: String? = null,
    val strengths: List<JdFitWireStrength> = emptyList(),
    val gaps: List<JdFitWireGap> = emptyList(),
)

/**
 * Index just past the `}` that closes the JSON object starting at `src[start]`, or -1 if it never
 * closes — the normal state for most of a stream. Brace counting rather than a regex because the
 * payload carries free text: a `}` inside a string must not end the object early, and a
 * half-arrived object must read as "not yet", never as malformed. Ported from `chatBlocks.ts`'s
 * `endOfJson`.
 */
private fun endOfJson(src: String, start: Int): Int {
    var depth = 0
    var inString = false
    var i = start
    while (i < src.length) {
        val c = src[i]
        when {
            inString -> if (c == '\\') i++ else if (c == '"') inString = false
            c == '"' -> inString = true
            c == '{' -> depth++
            c == '}' -> {
                depth--
                if (depth == 0) return i + 1
            }
        }
        i++
    }
    return -1
}

private fun boundedField(value: String?, max: Int = MAX_FIELD_CHARS): String? {
    val trimmed = value?.trim() ?: return null
    return trimmed.ifEmpty { null }?.take(max)
}

private fun JdFitWire.toReport(): JdFitReport? {
    val boundedSummary = boundedField(summary) ?: return null // no honest card without one
    val strengthRows = strengths.take(MAX_ROWS).mapNotNull { s ->
        val need = boundedField(s.need) ?: return@mapNotNull null
        val evidence = boundedField(s.evidence) ?: return@mapNotNull null
        JdFitReport.Strength(need, evidence, boundedField(s.project, max = 40))
    }
    val gapRows = gaps.take(MAX_ROWS).mapNotNull { g ->
        val need = boundedField(g.need) ?: return@mapNotNull null
        val note = boundedField(g.note) ?: return@mapNotNull null
        JdFitReport.Gap(need, note)
    }
    return JdFitReport(
        score = (score ?: 0).coerceIn(0, 100),
        role = boundedField(role, max = 80),
        summary = boundedSummary,
        strengths = strengthRows,
        gaps = gapRows,
    )
}

/**
 * Finds and decodes the `[[jdfit:{…}]]` directive in a (possibly still-streaming) reply, or null
 * when there isn't one — no directive present, the JD wasn't recognisable as one (the model's own
 * fallback), the payload was cut off mid-stream, or it failed to decode. Every one of those is
 * "nothing to supersede the offline card with yet", never a crash.
 */
/**
 * The `{…}` object's span, or null when there is nothing to decode yet — no directive, an
 * unterminated payload (the stream hasn't finished it), or one not immediately closed by `]]`.
 */
private fun directiveObjectRange(text: String): IntRange? {
    val start = text.indexOf(DIRECTIVE_PREFIX)
    if (start == -1) return null
    val objStart = start + DIRECTIVE_PREFIX.length
    val objEnd = if (objStart < text.length && text[objStart] == '{') endOfJson(text, objStart) else -1
    if (objEnd == -1 || !text.startsWith(DIRECTIVE_SUFFIX, objEnd)) return null
    return objStart until objEnd
}

fun parseJdFitDirective(text: String): JdFitReport? {
    val range = directiveObjectRange(text) ?: return null
    val wire = runCatching {
        directiveJson.decodeFromString(JdFitWire.serializer(), text.substring(range.first, range.last + 1))
    }.getOrNull() ?: return null
    return wire.toReport()
}
