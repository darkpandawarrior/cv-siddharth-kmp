package com.siddharth.cv.shared.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberSystemReducedMotion(): Boolean = remember { prefersReducedMotion() }

// ponytail: read once at start-up; a live matchMedia listener is the upgrade if the setting must
// follow a mid-session toggle.
@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun prefersReducedMotion(): Boolean = js("window.matchMedia('(prefers-reduced-motion: reduce)').matches")
