package com.siddharth.cv.shared.detail

/**
 * No-op on iOS.
 *
 * ponytail: the real path is `UIMarkupTextPrintFormatter(markupText = html)` handed to
 * `UIPrintInteractionController.sharedPrintController`, whose sheet includes "Save to Files" as
 * a PDF — UIKit renders the markup itself, so no WebView is needed. It must be presented from a
 * live `UIViewController`, which `printResume(html)` has no handle on; wire it when iOS ships
 * this screen and the entry point can pass one down.
 */
actual fun printResume(html: String) {
    // Intentionally empty — see KDoc.
}
