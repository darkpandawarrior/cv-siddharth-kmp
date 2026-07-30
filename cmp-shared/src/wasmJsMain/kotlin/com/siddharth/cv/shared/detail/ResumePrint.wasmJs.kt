package com.siddharth.cv.shared.detail

/**
 * The one target where Save-as-PDF is real.
 *
 * A hidden `<iframe>` is the whole trick. The host page is a single `<canvas>` — nothing the
 * print engine can paginate — but an iframe gets its own `Document`, and a document written
 * with `document.write` is laid out by the browser exactly like any other page. Calling
 * `print()` on *that* window hands Chrome/Safari/Firefox a real box tree, so page breaks,
 * text selection in the resulting PDF, and the `@page` size all work.
 *
 * Written with `js(...)` rather than `kotlinx.browser`: this module declares no browser-DOM
 * dependency of its own, and adding one to touch four DOM properties would be a build change
 * for nothing. The block is plain JS running in the page's global scope.
 */
actual fun printResume(html: String) {
    printInHiddenIframe(html)
}

/**
 * Unconditionally true: `window.print()` is in every browser that can run a wasm Compose build at
 * all, and a browser that blocked it would still leave the iframe's document open to Ctrl-P.
 */
actual val resumePrintSupported: Boolean = true

/**
 * Note the `readyState` fork. `doc.close()` normally settles the document synchronously, but
 * Safari finishes it on the load event instead, and calling `print()` before that prints a
 * blank page. Handling both is two lines; guessing is a bug that only reproduces on one
 * browser.
 *
 * ponytail: the iframe is torn down on a 1s timer. `print()` blocks in Chrome and Firefox but
 * not in Safari, so removing it immediately cancels the Safari print; `onafterprint` would be
 * exact but does not fire consistently on a same-origin child frame. Upgrade path if a slow
 * machine ever loses the dialog: listen for `afterprint` on the child window and keep the
 * timer only as the fallback.
 */
@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun printInHiddenIframe(html: String): Unit =
    js(
        """{
    const frame = document.createElement('iframe');
    frame.setAttribute('aria-hidden', 'true');
    frame.setAttribute('title', 'resume-print');
    frame.style.position = 'fixed';
    frame.style.right = '0';
    frame.style.bottom = '0';
    frame.style.width = '0';
    frame.style.height = '0';
    frame.style.border = '0';
    frame.style.visibility = 'hidden';
    document.body.appendChild(frame);

    const win = frame.contentWindow;
    const doc = win.document;
    doc.open();
    doc.write(html);
    doc.close();

    const run = function () {
        try {
            win.focus();
            win.print();
        } finally {
            setTimeout(function () { frame.remove(); }, 1000);
        }
    };

    if (doc.readyState === 'complete') {
        setTimeout(run, 0);
    } else {
        frame.onload = run;
    }
}""",
    )
