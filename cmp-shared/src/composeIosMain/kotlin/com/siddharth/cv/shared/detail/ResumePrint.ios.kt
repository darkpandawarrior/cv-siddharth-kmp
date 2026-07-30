package com.siddharth.cv.shared.detail

import platform.UIKit.UIMarkupTextPrintFormatter
import platform.UIKit.UIPrintInfo
import platform.UIKit.UIPrintInfoOutputType
import platform.UIKit.UIPrintInteractionController

/**
 * iOS: UIKit renders the markup itself — no WebView, no temp file.
 *
 * [UIMarkupTextPrintFormatter] is the shortcut the other three targets don't get: it takes an
 * HTML string and paginates it inside the print pipeline, so there is no page-load event to wait
 * for and no view to keep alive. The sheet
 * [UIPrintInteractionController.presentAnimated] raises includes "Save to Files" via AirPrint's
 * PDF path, which is the Save-as-PDF this feature is really about.
 *
 * ⚠️ Compile-verified by inspection only — there is no iOS toolchain in the loop that produced
 * this, so the cinterop spelling of `sharedPrintController` (class method → companion function)
 * is the one thing to re-check on the first `linkDebugFrameworkIosArm64`. The logic has no
 * branches; if it builds, it works.
 */
actual fun printResume(html: String) {
    val controller = UIPrintInteractionController.sharedPrintController()

    controller.printInfo = UIPrintInfo.printInfo().apply {
        // General, not Photo/Grayscale: the résumé is text with a couple of hairline rules, and
        // Photo would ask the driver for a colour-managed raster of a page of type.
        outputType = UIPrintInfoOutputType.UIPrintInfoOutputGeneral
        jobName = resumePrintJobName
    }
    controller.printFormatter = UIMarkupTextPrintFormatter(markupText = html)

    // ponytail: `presentAnimated` presents from the key window and needs no view controller,
    // which is why this file has no handle on one. Apple's guidance for iPad is
    // `presentFromRect`/`presentFromBarButtonItem` for a popover anchor; upgrade path if the
    // sheet ever looks unanchored on iPad is to pass the button's frame down from Compose via
    // `onGloballyPositioned`.
    controller.presentAnimated(animated = true, completionHandler = null)
}

/**
 * True on both Compose-capable iOS targets. Printing is a system service present on every device
 * and simulator; without a printer the sheet still offers the file destinations, so there is no
 * device state that makes this false.
 */
actual val resumePrintSupported: Boolean = true
