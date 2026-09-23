package com.siddharth.cv.shared.data

import com.siddharth.cv.shared.data.generated.projectGalleries

/** Project media uses exact URLs from the web portfolio's generated gallery registry. */
object CvGallery {
    private val canonicalSlugs =
        mapOf(
            "mileway" to "doori", // claim-audit:allow -- stable route slug
            "paymentslab" to "paymentslab-kmp", // claim-audit:allow -- stable route slug
            "kursi" to "gaddi", // claim-audit:allow -- stable route slug
            "hiresignal" to "candidai", // claim-audit:allow -- stable route slug
            "deadlock" to "stutter",
        )
    private val galleriesBySlug = projectGalleries.associate { it.slug to it.urls }

    /** Empty when the canonical registry has no media for this project. */
    fun urls(slug: String): List<String> = galleriesBySlug[canonicalSlugs[slug] ?: slug].orEmpty()

    fun hero(slug: String): String? = urls(slug).firstOrNull()
}
