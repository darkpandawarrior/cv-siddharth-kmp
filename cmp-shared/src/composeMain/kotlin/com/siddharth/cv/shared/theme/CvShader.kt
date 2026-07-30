package com.siddharth.cv.shared.theme

import androidx.compose.ui.graphics.Brush

/**
 * A GPU fragment shader as a Compose [Brush], or null where the platform has no Skia pipeline.
 *
 * Skiko exposes `RuntimeEffect` (SkSL) on wasm, JVM and iOS; Android renders through its own
 * pipeline and would need AGSL, so it returns null and callers fall back to a gradient. Null is
 * the contract, not a failure — every call site must already have a non-shader path.
 *
 * @param sksl a fragment shader whose only uniforms are `uniform float2 uSize;` and
 *   `uniform float uTime;`, in that order.
 * @param timeSeconds animation clock, driven by the caller so reduced-motion can freeze it.
 */
expect fun runtimeShaderBrush(sksl: String, widthPx: Float, heightPx: Float, timeSeconds: Float): Brush?
