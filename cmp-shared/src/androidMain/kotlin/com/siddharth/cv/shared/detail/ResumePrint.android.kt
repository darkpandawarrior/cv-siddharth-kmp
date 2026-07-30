package com.siddharth.cv.shared.detail

/**
 * No-op on Android. [buildResumeHtml] is still the useful half here — it just has nowhere to go.
 *
 * ponytail: the real path is `PrintManager.print(jobName, PrintDocumentAdapter, null)` fed by
 * `WebView.createPrintDocumentAdapter()` — load [buildResumeHtml] into an offscreen WebView via
 * `loadDataWithBaseURL(null, html, "text/html", "utf-8", null)`, wait for `onPageFinished`, then
 * hand its adapter to the system print dialog, which offers Save-as-PDF. That needs a `Context`,
 * so `printResume` would grow a parameter or the call site would route through a
 * CompositionLocal — a signature change for a target that has no résumé button today. Wire it
 * when Android actually ships this screen.
 */
actual fun printResume(html: String) {
    // Intentionally empty — see KDoc.
}
