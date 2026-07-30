package com.siddharth.cv.shared.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * The ambient ground as one GPU fragment shader.
 *
 * The web build paints the ambient layer with two stacked CSS radial gradients plus a starfield;
 * a gradient can only ever be an ellipse, so the web version reads as two clean lens flares. What
 * the site actually wants — and what [AmbientBackground] can only approximate on the CPU — is a
 * slow, organic *wash*: fbm noise put through a domain warp so the bloom edges wander instead of
 * being perfect ovals. That is one texture fetch-free fragment program here and ~120 Canvas draw
 * calls a frame there, so this is both the better-looking and the cheaper path.
 *
 * Deliberately NOT a raymarcher: this runs full-screen behind every route on every frame. The whole
 * program is 4 octaves of value noise plus a two-tap warp — a handful of ALU ops per pixel.
 */
private const val AmbientSkSl = """
uniform float2 uSize;
uniform float uTime;

float hash(float2 p) {
    return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453);
}

float vnoise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i),                    hash(i + float2(1.0, 0.0)), u.x),
               mix(hash(i + float2(0.0, 1.0)), hash(i + float2(1.0, 1.0)), u.x), u.y);
}

float fbm(float2 p) {
    float s = 0.0;
    float a = 0.5;
    for (int o = 0; o < 4; o++) {
        s += a * vnoise(p);
        p = p * 2.03 + float2(1.7, 9.2);
        a *= 0.5;
    }
    return s;
}

half4 main(float2 fragCoord) {
    const float3 GROUND = float3(0.020, 0.027, 0.039); // #05070a  --color-void
    const float3 SIGNAL = float3(0.239, 0.863, 0.518); // #3ddc84  --color-accent
    const float3 DEPTH  = float3(0.369, 0.902, 1.000); // #5ee6ff  --color-accent-2

    float h = max(uSize.y, 1.0);
    float2 uv = fragCoord / h;   // y spans 0..1, x keeps the aspect ratio so noise never stretches
    float cx = uSize.x / h * 0.5;
    float t = uTime * 0.05;      // ~20s to cross one noise cell: a drift, not an animation

    // Domain warp. Two fbm taps displace the sample point, which is what turns a radial falloff
    // into something that looks like it is breathing rather than pulsing.
    float2 warp = float2(fbm(uv * 1.6 + float2(t, -t)), fbm(uv * 1.6 + float2(5.2, 1.3) - t));
    float2 w = uv + 0.45 * warp;

    // Anchored exactly where the CSS gradients are: signal above the fold, depth below it.
    float bloomTop = 1.0 - smoothstep(0.0, 1.05, distance(uv, float2(cx, -0.12)));
    float bloomBot = 1.0 - smoothstep(0.0, 1.20, distance(uv, float2(cx,  1.12)));

    // Peak luminance is capped near 0.30 of each accent: body text is #e8efe9, and anything
    // brighter than this starts eating the contrast ratio the a11y suite depends on.
    float3 col = GROUND;
    col += SIGNAL * bloomTop * (0.09 + 0.21 * fbm(w * 1.25 + float2(0.0, t * 2.0)));
    col += DEPTH  * bloomBot * (0.06 + 0.15 * fbm(w * 1.05 + float2(3.7, -t * 1.6)));
    col *= 1.0 - 0.22 * smoothstep(0.55, 1.30, distance(uv, float2(cx, 0.5)));

    // The starfield, as a hash threshold over 4px cells instead of 120 drawCircle calls.
    float2 g = fragCoord / 4.0;
    float2 cell = floor(g);
    float star = step(0.9990, hash(cell + 31.7)) *
                 smoothstep(0.5, 0.05, distance(fract(g), float2(0.5)));
    col += float3(0.85, 0.94, 1.0) * star * (0.30 + 0.30 * sin(uTime * 1.4 + hash(cell) * 6.2832));

    // Dither. At this luminance an 8-bit framebuffer bands visibly across the falloff.
    col += (hash(fragCoord * 0.0137) - 0.5) * 0.005;
    return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
"""

/**
 * The frame the wash freezes on under reduced motion.
 *
 * [rememberFrameTicker] pins to 0f when motion is off, and t=0 is the one frame where every fbm
 * tap lands on an unwarped lattice point — the degenerate, flattest frame in the loop. Offsetting
 * by a constant means reduced-motion users get a *representative* still, not the worst one, and it
 * costs nothing on the animated path since the shader is periodic in nothing.
 */
private const val FrozenPhaseSeconds = 9.5f

/**
 * Fills [modifier]'s bounds with [AmbientSkSl], or with a gradient approximation of it wherever
 * `runtimeShaderBrush` returns null (Android, which has no SkSL pipeline, and any platform where
 * the source fails to compile).
 *
 * Two things are deliberate here:
 *
 * 1. Everything happens inside the [Canvas] draw lambda. `ticker.value` is read in the draw scope,
 *    so a new frame invalidates draw only — never composition or layout. `DrawScope.size` is
 *    already the real pixel size, which is why there is no `onSizeChanged` mirror state: adding one
 *    would introduce a frame of 0x0 (a shader fed 0x0 renders nothing) for no benefit.
 * 2. Reduced motion is not handled here at all. [rememberFrameTicker] freezes at 0f, so the
 *    shader's clock stops dead and the field stops twinkling with it — one enforcement point,
 *    same as every other ambient loop in the port.
 */
@Composable
fun ShaderWash(modifier: Modifier = Modifier) {
    val ticker = rememberFrameTicker()
    val colors = cvColors
    Canvas(modifier = modifier.fillMaxSize()) {
        if (size.minDimension <= 0f) return@Canvas
        val t = ticker.value + FrozenPhaseSeconds
        val shader = runtimeShaderBrush(AmbientSkSl, size.width, size.height, t)
        if (shader != null) {
            drawRect(brush = shader)
            return@Canvas
        }
        // Gradient fallback — the same two blooms over the same ground, minus the noise. It is the
        // shader's silhouette, so a device that loses the shader loses texture, not identity.
        // ponytail: the fallback reads cvColors while the shader hard-codes the site palette, so a
        // project-themed subtree would tint only here. runtimeShaderBrush's contract is exactly two
        // uniforms, so colours cannot be passed in — widen that contract if per-project ambient
        // tinting ever ships.
        drawRect(color = colors.deepVoid)
        val reach = maxOf(size.width, size.height) * 0.85f
        drawRect(
            brush =
                Brush.radialGradient(
                    colors = listOf(colors.accent.copy(alpha = 0.26f), Color.Transparent),
                    center = Offset(size.width / 2f, -size.height * 0.12f),
                    radius = reach,
                ),
        )
        drawRect(
            brush =
                Brush.radialGradient(
                    colors = listOf(colors.accent2.copy(alpha = 0.18f), Color.Transparent),
                    center = Offset(size.width / 2f, size.height * 1.12f),
                    radius = reach,
                ),
        )
    }
}

/**
 * Drop-in replacement for [AmbientBackground]: the shader wash where Skia can run it, the existing
 * CPU starfield everywhere else.
 *
 * The probe compiles the source once at first composition (and warms `runtimeShaderBrush`'s effect
 * cache doing it) rather than per frame. Falling back to [AmbientBackground] instead of to
 * [ShaderWash]'s internal gradient is the point of this wrapper — Android then gets the full
 * shipped ground, twinkling stars and all, instead of a bare two-gradient reduction of it.
 * [ShaderWash] keeps its own fallback regardless, for callers that want the wash specifically.
 */
@Composable
fun ShaderOrGradientBackground(modifier: Modifier = Modifier) {
    val shaderAvailable = remember { runtimeShaderBrush(AmbientSkSl, 1f, 1f, 0f) != null }
    if (shaderAvailable) ShaderWash(modifier) else AmbientBackground(modifier)
}
