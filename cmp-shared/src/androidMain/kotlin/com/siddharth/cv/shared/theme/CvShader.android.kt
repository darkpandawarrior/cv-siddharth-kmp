package com.siddharth.cv.shared.theme

import androidx.compose.ui.graphics.Brush

// Android renders through its own pipeline, not Skiko: `org.jetbrains.skia` is absent from the
// classpath entirely. AGSL via RuntimeShader (API 33+) is the equivalent and the upgrade path.
// ponytail: null until an Android build actually wants the effect.
actual fun runtimeShaderBrush(
    sksl: String,
    widthPx: Float,
    heightPx: Float,
    timeSeconds: Float,
): Brush? = null
