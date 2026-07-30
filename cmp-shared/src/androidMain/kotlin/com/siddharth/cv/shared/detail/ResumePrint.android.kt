package com.siddharth.cv.shared.detail

import android.app.Activity
import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import java.lang.ref.WeakReference

/**
 * Android: an offscreen WebView renders the document, `PrintManager` paginates it.
 *
 * Android's print stack takes a [android.print.PrintDocumentAdapter], not HTML — and the only
 * thing in the platform that can turn HTML into one is `WebView.createPrintDocumentAdapter()`.
 * The WebView never reaches the window; it exists purely as a layout engine. The system print
 * dialog it feeds offers "Save as PDF" as a destination, which is the actual feature.
 *
 * Two non-obvious constraints shape everything below:
 *  1. The adapter is only valid once layout has finished, so the print call has to happen in
 *     `onPageFinished` — not on the line after `loadDataWithBaseURL`.
 *  2. Nothing holds the WebView between those two moments (a WebView owns its client, not the
 *     reverse), so without [printHost] below it can be collected mid-load and the print silently
 *     never happens.
 */

// -------------------------------------------------------------------------------------------
// Host handle
// -------------------------------------------------------------------------------------------

/**
 * The Activity to launch the print dialog from, and the reason this file needs wiring at all.
 *
 * `PrintManager.print()` ends in `context.startIntentSender(...)` for the spooler's dialog. Give
 * it an application Context and that either warns or throws for want of `FLAG_ACTIVITY_NEW_TASK`
 * — the dialog is a task-less Activity launch. So it must be an Activity, and an Activity in a
 * `static` is a leak, hence the [WeakReference]: the framework holds the real strong reference
 * for as long as the résumé screen can exist, and this goes null the moment it doesn't.
 */
private var host: WeakReference<Activity>? = null

/**
 * Call once from the Activity hosting `App()`, before `setContent`.
 *
 * Before-not-after matters for [resumePrintSupported]: it is read during composition and is not
 * observable state, so installing after the first frame would compose the Print button away and
 * never bring it back.
 *
 * ponytail: a single global host, because this app has exactly one Activity. If it ever grows a
 * second one that shows the résumé, the upgrade path is a `LocalActivity`-style CompositionLocal
 * and a `printResume(host, html)` signature rather than a registry of Activities.
 */
fun installResumePrintHost(activity: Activity) {
    host = WeakReference(activity)
}

// -------------------------------------------------------------------------------------------
// Print
// -------------------------------------------------------------------------------------------

/**
 * Strong reference that keeps the in-flight WebView alive from `loadDataWithBaseURL` until
 * `PrintManager` has taken ownership of its adapter. See constraint 2 in the file KDoc.
 *
 * Overwritten rather than cleared on the next print: releasing it inside `onPageFinished` would
 * drop the WebView while the adapter is still writing pages, and holding one dead WebView between
 * prints is cheaper than reasoning about when the spooler is finally done with it.
 */
private var printWebView: WebView? = null

actual fun printResume(html: String) {
    val activity = host?.get() ?: return
    val printManager = activity.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return

    // Must be constructed on the main thread, which every Compose onClick already is.
    val webView = WebView(activity)
    printWebView = webView

    // JS off on purpose: the document is static markup with no script, so enabling it would only
    // widen the attack surface of a WebView that renders app-generated HTML.
    webView.settings.javaScriptEnabled = false
    webView.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView, url: String?) {
            val name = resumePrintJobName
            printManager.print(
                name,
                view.createPrintDocumentAdapter(name),
                // A4 to match the document's own `@page { size: A4 }`. Left unset, the dialog
                // defaults to the locale's paper size and silently rescales the layout.
                PrintAttributes.Builder()
                    .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                    .build(),
            )
        }
    }

    // `null` base URL is what makes this safe and offline: the document references no external
    // asset (`resumeHtmlSelfCheck` asserts it), so there is nothing to resolve and no network
    // permission involved.
    webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
}

/**
 * A getter, not a constant: on Android "supported" depends on [installResumePrintHost] having run.
 * Reporting `true` without a host would put back exactly the dead button this flag exists to
 * remove — and would do it only in whichever integration forgot the wiring, which is the worst
 * possible place for it to be invisible.
 */
actual val resumePrintSupported: Boolean
    get() = host?.get() != null
