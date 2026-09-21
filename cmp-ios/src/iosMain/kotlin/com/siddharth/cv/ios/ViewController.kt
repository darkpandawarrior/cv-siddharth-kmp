package com.siddharth.cv.ios

import androidx.compose.ui.window.ComposeUIViewController
import com.siddharth.cv.shared.App
import platform.UIKit.UIViewController

/**
 * The iOS entry point — `cmp-ios/iosApp/ContentView.swift` calls `ViewControllerKt.viewController()`.
 *
 * Kotlin/Native names the Swift-visible facade after the FILE, not the package, so moving this out
 * of :cmp-shared and into the umbrella module left the Swift call site unchanged.
 *
 * [App] takes a nav state and an `onRouteChanged` callback, both defaulted. Only the web shell
 * passes the latter (it maps route changes onto `history.pushState`); iOS has no address bar, so
 * the no-op default is correct here.
 */
fun viewController(): UIViewController = ComposeUIViewController { App() }
