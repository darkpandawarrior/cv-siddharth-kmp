package com.siddharth.cv.shared.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeShader
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

/**
 * Compiles the SkSL once per (source) and rebinds uniforms per frame. `makeShader` is cheap;
 * `RuntimeEffect.makeForShader` is not, hence the cache — a shader recompiled every frame drops
 * the framerate more than the effect is worth.
 */
private val cache = mutableMapOf<String, RuntimeEffect>()

actual fun runtimeShaderBrush(
    sksl: String,
    widthPx: Float,
    heightPx: Float,
    timeSeconds: Float,
): Brush? = runCatching {
    val effect = cache.getOrPut(sksl) { RuntimeEffect.makeForShader(sksl) }
    val builder = RuntimeShaderBuilder(effect)
    builder.uniform("uSize", widthPx, heightPx)
    builder.uniform("uTime", timeSeconds)
    ShaderBrush(builder.makeShader().asComposeShader())
}.getOrNull() // a bad SkSL string must degrade to the gradient, never crash the page
