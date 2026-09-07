package com.siddharth.cv.shared.fit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Kotlin counterpart of `cv-siddharth/src/lib/skillMatch.test.ts` (there is none — the TS
 * module ships with no test file of its own, so this is new coverage, not a port of existing
 * assertions) plus the one thing this port adds that the web build doesn't need: parsing the
 * `[[jdfit:{…}]]` directive back out of the model's streamed reply.
 *
 * Runs on the jvm target only, same as [com.siddharth.cv.shared.chat.ChatClientTest] and for the
 * same reason: the logic under test has no platform branch. [SkillMatch.kt] itself lives in
 * composeMain rather than commonMain for an unrelated toolchain reason (see its own file doc).
 */
class SkillMatchTest {

    // -----------------------------------------------------------------------------------------
    // mentions() — whole-word, case-insensitive containment
    // -----------------------------------------------------------------------------------------

    @Test
    fun `mentions finds a whole-word case-insensitive hit`() {
        assertTrue(mentions("Looking for a KOTLIN engineer", "kotlin"))
    }

    @Test
    fun `mentions rejects a substring glued to a letter on either side`() {
        // The classic false positive this hand-written edge check exists to avoid: a following
        // LETTER is not a word edge, so "java" must not match inside "javascript".
        assertFalse(mentions("Experience with JavaScript", "java"))
    }

    @Test
    fun `mentions treats a hyphen as a word edge, faithfully to the original matcher`() {
        // Ported bug-for-bug from skillMatch.ts's own edge() (`![a-z0-9+]`, which does not include
        // "-"): a hyphen counts as a boundary, so "go" DOES match inside "go-to-market" here. The
        // source file's own comment claims this is guarded against, but no entry in OFF_STACK_TERMS
        // is the bare word "go" (the real key is "golang"), so the gap is latent rather than a
        // real false positive — and scoring must match the web build exactly, so this port keeps
        // the same edge rule rather than "fixing" a case the live table never exercises.
        assertTrue(mentions("go-to-market strategy", "go"))
    }

    @Test
    fun `mentions treats plus as a word edge so C++ and Compose + Material behave`() {
        assertTrue(mentions("Compose + Material 3", "compose"))
    }

    // -----------------------------------------------------------------------------------------
    // matchJd() — scoring
    // -----------------------------------------------------------------------------------------

    @Test
    fun `matchJd reports asked zero for text naming nothing recognisable`() {
        val result = matchJd("Please water the plants twice a week.")
        assertEquals(0, result.asked)
        assertEquals(0, result.score)
    }

    @Test
    fun `matchJd matches a deep skill by an alias other than its canonical name`() {
        val result = matchJd("You will build UI with Jetpack Compose and JC widgets.")
        assertTrue(result.matched.any { it.skill.name == "Jetpack Compose" })
    }

    @Test
    fun `matchJd demotes an unpreferred familiar skill to partial rather than a strength`() {
        // "iOS" is a `familiar`-depth skill in the table; asked as a hard requirement it must
        // read as a qualified gap, not a strength — see toFitReport's own doc for why.
        val result = matchJd("Must have native iOS experience.")
        assertTrue(result.partial.any { it.skill.name.contains("iOS") })
        assertFalse(result.matched.any { it.skill.name.contains("iOS") })
    }

    @Test
    fun `matchJd keeps a preferred familiar skill as matched rather than partial`() {
        val result = matchJd("Nice to have: some React experience.")
        assertTrue(result.matched.any { it.skill.name == "Web frontend" })
        assertFalse(result.partial.any { it.skill.name == "Web frontend" })
    }

    @Test
    fun `matchJd names an off-stack term as a gap`() {
        val result = matchJd("Must be proficient in Kubernetes and Rust.")
        assertTrue(result.missing.any { it.term == "Kubernetes" })
        assertTrue(result.missing.any { it.term == "Rust" })
    }

    @Test
    fun `matchJd marks a nice-to-have off-stack term as preferred`() {
        val result = matchJd("Requirements: Kotlin.\nNice to have: Docker experience.")
        val docker = result.missing.first { it.term == "Docker" }
        assertTrue(docker.preferred)
    }

    @Test
    fun `matchJd deduplicates off-stack aliases sharing one label`() {
        // "k8s" and "kubernetes" both label as "Kubernetes" — must not double-count.
        val result = matchJd("Experience with k8s and Kubernetes clusters.")
        assertEquals(1, result.missing.count { it.term == "Kubernetes" })
    }

    @Test
    fun `matchJd extracts a role title from an early line`() {
        val result = matchJd("Senior Android Engineer\n\nWe are looking for someone to join our team.")
        assertEquals("Senior Android Engineer", result.role)
    }

    @Test
    fun `matchJd finds no role when nothing on the first lines shapes like a title`() {
        val result = matchJd("We are a fast-growing startup building great products for everyone.")
        assertNull(result.role)
    }

    // -----------------------------------------------------------------------------------------
    // toFitReport() — shaping into the card
    // -----------------------------------------------------------------------------------------

    @Test
    fun `toFitReport caps strengths at four and gaps at three`() {
        val jd = "Senior Android Engineer needing Kotlin, Compose, Coroutines, Room, Hilt, " +
            "Retrofit, Rust, Kubernetes, Docker, AWS, Terraform, Kafka."
        val report = toFitReport(matchJd(jd))
        assertTrue(report.strengths.size <= 4)
        assertTrue(report.gaps.size <= 3)
    }

    @Test
    fun `toFitReport marks the summary provisional when the model has not yet answered`() {
        val report = toFitReport(matchJd("Kotlin and Android required."), final = false)
        assertTrue(report.summary.contains("the full read is on its way"))
    }

    @Test
    fun `toFitReport marks the summary as final when the model never arrived`() {
        val report = toFitReport(matchJd("Kotlin and Android required."), final = true)
        assertTrue(report.summary.contains("couldn't be reached"))
    }

    // -----------------------------------------------------------------------------------------
    // parseJdFitDirective() — the ONE thing this port needs that skillMatch.ts doesn't
    // -----------------------------------------------------------------------------------------

    private val validDirective =
        """He is a strong fit for this role.
          |[[jdfit:{"score":82,"role":"Senior Android Engineer","summary":"Strong platform match.","strengths":[{"need":"Kotlin","evidence":"5+ years"}],"gaps":[{"need":"Backend","note":"Not on his CV"}]}]]
        """.trimMargin()

    @Test
    fun `parseJdFitDirective reads a well-formed directive`() {
        val report = parseJdFitDirective(validDirective)
        assertNotNull(report)
        assertEquals(82, report.score)
        assertEquals("Senior Android Engineer", report.role)
        assertEquals("Strong platform match.", report.summary)
        assertEquals(1, report.strengths.size)
        assertEquals("Kotlin", report.strengths.first().need)
        assertEquals(1, report.gaps.size)
    }

    @Test
    fun `parseJdFitDirective returns null when no directive is present`() {
        assertNull(parseJdFitDirective("Just a plain sentence with no directive at all."))
    }

    @Test
    fun `parseJdFitDirective returns null for a directive cut off mid-stream`() {
        // The exact shape of a stream that stopped before [[DONE]] — an unterminated JSON object.
        assertNull(parseJdFitDirective("""Some prose. [[jdfit:{"score":70,"summary":"partial"""))
    }

    @Test
    fun `parseJdFitDirective clamps an out-of-range score`() {
        val report = parseJdFitDirective("""[[jdfit:{"score":140,"summary":"x"}]]""")
        assertNotNull(report)
        assertEquals(100, report.score)
    }

    @Test
    fun `parseJdFitDirective drops a row missing a required field rather than crashing`() {
        val report = parseJdFitDirective(
            """[[jdfit:{"score":50,"summary":"x","strengths":[{"need":"Kotlin"}],"gaps":[]}]]""",
        )
        assertNotNull(report)
        assertTrue(report.strengths.isEmpty(), "a strength with no evidence is dropped, not half-shown")
    }

    @Test
    fun `parseJdFitDirective returns null without a summary`() {
        assertNull(parseJdFitDirective("""[[jdfit:{"score":50}]]"""))
    }

    @Test
    fun `parseJdFitDirective caps a hostile field length`() {
        val long = "x".repeat(5000)
        val report = parseJdFitDirective("""[[jdfit:{"score":50,"summary":"$long"}]]""")
        assertNotNull(report)
        assertTrue(report.summary.length <= 240)
    }
}
