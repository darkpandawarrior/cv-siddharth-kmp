package com.siddharth.cv.shared.detail

import java.awt.Desktop
import java.io.File

/**
 * Desktop JVM: borrow the OS browser's print engine instead of shipping one.
 *
 * The tempting-looking API here is `java.awt.print`, and it is the wrong one. Java2D would have
 * to lay the HTML out itself — pagination, flexbox baselines, `@page` — which means embedding a
 * rendering engine (~30 MB of Flying Saucer/JavaFX WebView) to print one page of text. Every
 * desktop this app runs on already has an HTML renderer with a Save-as-PDF dialog attached to it.
 * So: write the document to disk, hand the OS the URI, let Cmd-P/Ctrl-P finish the job.
 *
 * That is one hop more than the web path (the user presses print in the browser, not in the app),
 * and it is the honest ceiling of the approach — an in-app dialog would cost the renderer.
 */
actual fun printResume(html: String) {
    val file = writeTempHtml(html)

    // Both guards are load-bearing and fail on different machines: `isDesktopSupported()` is
    // false on a headless JVM, and BROWSE is unsupported on a Linux box with no xdg-open /
    // registered handler even when Desktop itself exists. Neither throws until you call browse().
    val desktop = if (Desktop.isDesktopSupported()) Desktop.getDesktop() else null
    if (desktop != null && desktop.isSupported(Desktop.Action.BROWSE)) {
        // browse() throws IOException when the handler exists but refuses (sandboxed launcher,
        // no default browser). The file is already on disk at that point, so the fallback below
        // is still a usable outcome rather than a dead end.
        runCatching { desktop.browse(file.toURI()) }.onSuccess { return }
    }

    // ponytail: stdout is the whole fallback UI. It is reachable only on a desktop with no
    // browser handler at all; upgrade path is a Compose snackbar with a copyable path, which
    // needs a UI channel this function deliberately does not have.
    println("[cv] Could not open a browser. Résumé written to: ${file.absolutePath}")
}

/**
 * `.html` suffix, not `.tmp` — the extension is the only thing telling the browser this is a
 * document and not a download, and a `.tmp` URI gets saved to disk instead of rendered.
 *
 * `deleteOnExit()` rather than an immediate delete: the browser reads the file asynchronously
 * after `browse()` returns, so deleting eagerly races the load and prints a 404. Tying the
 * lifetime to the JVM is the shortest correct scope — the app outlives the page load, and a
 * handful of kilobytes in the temp dir until quit is not a leak worth machinery.
 */
private fun writeTempHtml(html: String): File =
    File.createTempFile("resume-", ".html").apply {
        deleteOnExit()
        // Defaults to UTF-8, matching the document's own <meta charset>. An explicit charset
        // here would be the same value written twice.
        writeText(html)
    }

/**
 * True even on a machine where BROWSE turns out to be unsupported.
 *
 * The flag answers "will pressing this do something", and it will: the fallback writes the file
 * and reports the path, which is a real result. Gating the button on `isDesktopSupported()` would
 * hide it on exactly the machines where the printed path is the only way through.
 */
actual val resumePrintSupported: Boolean = true
