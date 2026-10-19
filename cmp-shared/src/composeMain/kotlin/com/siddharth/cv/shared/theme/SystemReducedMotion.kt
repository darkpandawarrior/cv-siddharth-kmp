package com.siddharth.cv.shared.theme

import androidx.compose.runtime.Composable

/**
 * The OS-level "reduce motion" preference, read once per composition. Feeds the root `CvTheme` so
 * every `LocalReducedMotion` guard (reveals, loops, pulses) is live in production; before this the
 * default `false` was never overridden outside previews.
 *
 * Android: animator duration scale 0. Web: `prefers-reduced-motion`. iOS: Reduce Motion. Desktop:
 * no portable signal, so false.
 */
@Composable
expect fun rememberSystemReducedMotion(): Boolean
