package com.siddharth.cv.shared.data

/**
 * Pure data model, ported from cv-siddharth/src/data/profile.ts.
 *
 * Compiled Kotlin, not a resource: wasmJs fetches resources at runtime through a
 * suspend API, so the whole CV would need a loading state and a failure path for
 * data that never changes between builds.
 *
 * Colours stay #RRGGBB strings here — converting to Compose Color is the theme
 * layer's job, so this file has zero imports.
 */

data class Profile(
    val name: String,
    val title: String,
    val resumeTitle: String,
    val tagline: String,
    val location: String,
    val email: String,
    val phone: String,
    val github: String,
    val linkedin: String,
    val portfolio: String,
    val availability: String,
    /** Casual blurb shown on the homepage hero. */
    val intro: String,
    /** ATS-friendly, keyword-dense summary shown on the résumé view. */
    val summary: String,
)

data class Education(
    val school: String,
    val degree: String,
    val period: String,
)

data class Metric(
    val value: String,
    val label: String,
    val detail: String,
)

data class ExperiencePoint(
    val text: String,
    val label: String? = null,
)

data class Experience(
    val company: String,
    val role: String,
    val period: String,
    val points: List<ExperiencePoint>,
)

data class CaseStudy(
    val slug: String,
    val title: String,
    val metric: String,
    val summary: String,
    val problem: String,
    val approach: List<String>,
    val outcome: String,
    val tags: List<String>,
)

/** Serves both the 4-group homepage layout and the 7-group résumé layout. */
data class SkillGroup(
    val group: String,
    val items: List<String>,
)

/** `url` is not always external — see [isInternalLink]. */
data class NamedLink(
    val label: String,
    val url: String,
)

data class LabeledValue(
    val value: String,
    val label: String,
)

data class ProjectDetailSection(
    val heading: String,
    val body: String,
)

/** Raw Mermaid source. Nothing renders it in v1; kept so a future renderer can. */
data class Diagram(
    val title: String,
    val code: String,
)

data class ProjectRole(
    val name: String,
    val power: String,
    val color: String,
)

/**
 * ponytail: collapsed from the web shape — `deviceFrame`, `screens` and `liveUrl`
 * are dropped because v1 ships no images. A target renders as a chip:
 * "Android · 6 screens". `platform` stays the display string ("Wear OS", "watchOS").
 */
data class ProjectTarget(
    val platform: String,
    val screenCount: Int,
    val note: String? = null,
)

/** Per-project palette override. `displayFont` dropped — it was a CSS font stack. */
data class ProjectTheme(
    val accent: String,
    val accentDim: String,
    val ink: String? = null,
    val surface: String? = null,
    val card: String? = null,
    val line: String? = null,
)

data class ProjectDetailData(
    val overview: String,
    val sections: List<ProjectDetailSection> = emptyList(),
    val metrics: List<LabeledValue> = emptyList(),
    val techStack: List<SkillGroup> = emptyList(),
    val extraLinks: List<NamedLink> = emptyList(),
    val diagrams: List<Diagram> = emptyList(),
    val roles: List<ProjectRole> = emptyList(),
)

data class Project(
    val slug: String,
    val name: String,
    val tagline: String,
    val description: String,
    val stack: List<String>,
    val highlights: List<String>,
    val links: List<NamedLink> = emptyList(),
    val status: String,
    val badges: List<String> = emptyList(),
    val detail: ProjectDetailData? = null,
    val targets: List<ProjectTarget> = emptyList(),
    val theme: ProjectTheme? = null,
)

data class SharedLib(
    val name: String,
    val url: String,
    val role: String,
    val usedBy: List<String>,
)

data class SharedFoundation(
    val blurb: String,
    val libs: List<SharedLib>,
)

/** `status` is "merged" | "open" | "closed"; only "merged" occurs today. */
data class Contribution(
    val repo: String,
    val title: String,
    val url: String,
    val status: String,
    val date: String,
)

data class GrowthItem(
    val date: String,
    val title: String,
    val detail: String,
)

data class SiteRoom(
    val to: String,
    val label: String,
    val blurb: String,
    val tag: String,
)

/**
 * True for in-app destinations — "#work", "#project/paymentslab", "/compose".
 * Callers navigate on true and open a browser on false; handing an in-app hash
 * to a URL opener is the bug this exists to prevent.
 */
fun isInternalLink(url: String): Boolean = url.startsWith("#") || url.startsWith("/")
