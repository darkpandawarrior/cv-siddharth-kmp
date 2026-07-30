package com.siddharth.cv.shared.detail

/**
 * No-op on desktop JVM.
 *
 * ponytail: the honest desktop path is not `java.awt.print` — Java2D would have to lay out the
 * HTML itself, which means shipping a rendering engine. Write [buildResumeHtml] to a temp
 * `.html` and hand it to the OS browser (`java.awt.Desktop.browse`), which already has the
 * print engine and the Save-as-PDF dialog. Two lines, but it needs a temp-file lifecycle and
 * the desktop target has no résumé button today, so it stays unbuilt until it does.
 */
actual fun printResume(html: String) {
    // Intentionally empty — see KDoc.
}
