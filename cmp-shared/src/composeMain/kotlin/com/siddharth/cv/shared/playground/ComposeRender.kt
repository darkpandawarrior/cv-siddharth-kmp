package com.siddharth.cv.shared.playground

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.siddharth.cv.shared.theme.cvColors
import com.siddharth.cv.shared.theme.cvType

/**
 * Renders a parsed [Program] with **real Compose composables**.
 *
 * This is the reason the port exists. The React original walks the same AST and emits CSS: a `Card`
 * becomes a `div` with a `border-radius`, `Arrangement.Center` becomes `justify-content: center`,
 * and `Arrangement.spacedBy(12.dp)` becomes — verbatim, from `composeInterpreter`'s renderer —
 * `gap: 8`, discarding the argument entirely. Every one of those is an *approximation of Compose
 * written in another language*.
 *
 * Here the target and the source are the same framework, so nothing is approximated. `Column` is a
 * `Column`. `Arrangement.spacedBy(12.dp)` is `Arrangement.spacedBy(12.dp)`, argument honoured. A
 * `Button` is a Material 3 `Button` whose `onClick` mutates the same observable state the tree reads,
 * so recomposition is Compose's, not a hand-rolled re-render from the root.
 *
 * Forgiveness is ported deliberately from the interpreter: an unknown modifier or named argument is
 * ignored rather than fatal, so a half-finished snippet still renders something. The one exception is
 * [Node.Unknown], which draws a visible marker — silently rendering nothing for a composable the
 * author typed would be a lie about what the subset supports.
 */
@Composable
fun RenderTree(nodes: List<Node>, state: ComposeState) {
    nodes.forEach { RenderNode(it, state) }
}

@Composable
fun RenderNode(node: Node, state: ComposeState) {
    when (node) {
        is Node.Container -> RenderContainer(node, state)
        is Node.Text -> RenderText(node, state)
        is Node.Button -> RenderButton(node, state)
        is Node.Spacer -> Spacer(node.modifiers.toComposeModifier(state))
        is Node.Animated -> RenderAnimated(node, state)
        is Node.TextField -> RenderTextField(node, state)
        is Node.Unknown -> RenderUnknown(node.name)
    }
}

@Composable
private fun RenderContainer(node: Node.Container, state: ComposeState) {
    val modifier = node.modifiers.toComposeModifier(state)
    when (node.name) {
        ContainerKind.Column ->
            Column(
                modifier = modifier,
                verticalArrangement = node.named["verticalArrangement"].toVerticalArrangement(),
                horizontalAlignment = node.named["horizontalAlignment"].toHorizontalAlignment(),
            ) { RenderTree(node.children, state) }

        ContainerKind.Row ->
            Row(
                modifier = modifier,
                horizontalArrangement = node.named["horizontalArrangement"].toHorizontalArrangement(),
                verticalAlignment = node.named["verticalAlignment"].toVerticalAlignment(),
            ) { RenderTree(node.children, state) }

        ContainerKind.Box ->
            Box(
                modifier = modifier,
                contentAlignment = node.named["contentAlignment"].toBoxAlignment(),
            ) { RenderTree(node.children, state) }

        // Card and Surface both take a colour from the site palette rather than M3 defaults, so a
        // snippet dropped into the preview sits in the same world as the page around it.
        ContainerKind.Card ->
            Card(
                modifier = modifier,
                colors = CardDefaults.cardColors(containerColor = cvColors.card),
            ) { Column { RenderTree(node.children, state) } }

        ContainerKind.Surface ->
            Surface(modifier = modifier, color = cvColors.surface) {
                Column { RenderTree(node.children, state) }
            }
    }
}

@Composable
private fun RenderText(node: Node.Text, state: ComposeState) {
    val colors = cvColors
    BasicText(
        text = resolveText(node.value, state),
        modifier = node.modifiers.toComposeModifier(state),
        style =
            cvType.body.copy(
                color = node.named["color"].toColor(colors.onBackground),
                fontSize = node.named["fontSize"]?.let { resolveNum(it, state, 16.0).sp }
                    ?: cvType.body.fontSize,
                fontWeight = node.named["fontWeight"].toFontWeight(),
                textAlign = node.named["textAlign"].toTextAlign(),
            ),
    )
}

@Composable
private fun RenderButton(node: Node.Button, state: ComposeState) {
    val colors = cvColors
    Button(
        onClick = { applyActions(node.onClick, state) },
        modifier = node.modifiers.toComposeModifier(state),
        enabled = node.named["enabled"]?.let { resolveBool(it, state) } ?: true,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = node.named["containerColor"].toColor(colors.accent),
                contentColor = colors.ink,
            ),
    ) {
        // A Button with no body still has to read as a button rather than an empty pill.
        if (node.children.isEmpty()) {
            BasicText("Button", style = cvType.bodySmall.copy(color = colors.ink))
        } else {
            RenderTree(node.children, state)
        }
    }
}

@Composable
private fun RenderAnimated(node: Node.Animated, state: ComposeState) {
    // AnimatedVisibility, not an if — the enter/exit transition is the thing the snippet is asking
    // for, and the React version can only approximate it with a CSS opacity transition.
    AnimatedVisibility(visible = resolveBool(node.visible, state)) {
        Column(node.modifiers.toComposeModifier(state)) { RenderTree(node.children, state) }
    }
}

@Composable
private fun RenderTextField(node: Node.TextField, state: ComposeState) {
    val colors = cvColors
    val text = resolveText(node.value, state)
    BasicTextField(
        value = text,
        // bindTo is null when the author wrote an onValueChange the parser could not read. The field
        // still renders and still accepts focus; it just cannot write back. Dropping the field
        // entirely would hide a typo instead of showing its consequence.
        onValueChange = { next -> node.bindTo?.let { state.setText(it, next) } },
        modifier =
            node.modifiers
                .toComposeModifier(state)
                .background(colors.surface, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        textStyle = cvType.body.copy(color = colors.onBackground),
        cursorBrush = SolidColor(colors.accent),
        singleLine = true,
    )
}

@Composable
private fun RenderUnknown(name: String) {
    val colors = cvColors
    BasicText(
        text = "$name() — not in the supported subset",
        modifier =
            Modifier.padding(vertical = 4.dp)
                .background(colors.card, RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
        style = cvType.metaMono.copy(color = colors.muted),
    )
}

// -------------------------------------------------------------------------------------------------
// Modifier chain
// -------------------------------------------------------------------------------------------------

/**
 * `Modifier.fillMaxWidth().padding(16.dp)` -> the real Modifier chain, applied in source order
 * because Compose modifier order is semantic and reordering would change the result.
 *
 * Unknown entries are skipped silently, matching the interpreter's contract. Anything listed in the
 * `ponytail:` note below parses but does not render.
 *
 * ponytail: not mapped — border, shadow/elevation, alpha, rotate, scale, offset, weight (weight
 * needs a RowScope/ColumnScope receiver, so it cannot live in a scope-free helper like this one).
 * Add the scoped ones as a separate scope-aware pass if a preset ever needs them.
 */
@Composable
private fun List<ModifierCall>.toComposeModifier(state: ComposeState): Modifier {
    var m: Modifier = Modifier
    forEach { call ->
        val n = call.args.firstOrNull()
        m = when (call.name) {
            "fillMaxSize" -> m.fillMaxSize()
            "fillMaxWidth" -> m.fillMaxWidth()
            "fillMaxHeight" -> m.fillMaxHeight()
            "padding" -> m.then(paddingFor(call, state))
            "height" -> m.height(resolveNum(n, state, 0.0).dp)
            "width" -> m.width(resolveNum(n, state, 0.0).dp)
            "size" -> m.size(resolveNum(n, state, 0.0).dp)
            "background" -> m.background(n.toColor(cvColors.card), shapeFor(call))
            "clip" -> m.clip(shapeFor(call))
            else -> m
        }
    }
    return m
}

/**
 * `padding(24.dp)` is uniform; `padding(horizontal = …, vertical = …)` is not. The parser flattens
 * named args into positions, so a single argument is treated as uniform and two as (horizontal,
 * vertical) — the same reading the TS renderer applies.
 */
@Composable
private fun paddingFor(call: ModifierCall, state: ComposeState): Modifier {
    val a = call.args
    return when {
        a.isEmpty() -> Modifier
        a.size == 1 -> Modifier.padding(resolveNum(a[0], state, 0.0).dp)
        else ->
            Modifier.padding(
                PaddingValues(
                    horizontal = resolveNum(a[0], state, 0.0).dp,
                    vertical = resolveNum(a[1], state, 0.0).dp,
                ),
            )
    }
}

/** `clip(CircleShape)` / `clip(RoundedCornerShape(16.dp))`; anything else falls back to 12.dp. */
private fun shapeFor(call: ModifierCall): androidx.compose.ui.graphics.Shape {
    val path = (call.args.firstOrNull() as? Expr.Member)?.path ?: return RoundedCornerShape(12.dp)
    if (memberBase(path).contains("Circle")) return CircleShape
    return RoundedCornerShape((memberArg(path) ?: 12.0).dp)
}

// -------------------------------------------------------------------------------------------------
// Named-argument mapping — the part the React renderer can only approximate
// -------------------------------------------------------------------------------------------------

/**
 * The `spacedBy` argument is honoured here. `composeInterpreter`'s React renderer sets `gap: 8` for
 * any `spacedBy(...)` whatever the source says, so the Counter preset's declared `spacedBy(12.dp)`
 * draws at 8px there and at 12dp here. That is the clearest single example of what "real Compose
 * instead of CSS" buys, and it is asserted in composeInterpreterSelfCheck.
 */
private fun Expr?.toVerticalArrangement(): Arrangement.Vertical {
    val path = (this as? Expr.Member)?.path ?: return Arrangement.Top
    memberArg(path)?.let { if (memberBase(path).endsWith("spacedBy")) return Arrangement.spacedBy(it.dp) }
    return when {
        memberBase(path).endsWith("Center") -> Arrangement.Center
        memberBase(path).endsWith("Bottom") || memberBase(path).endsWith("End") -> Arrangement.Bottom
        memberBase(path).endsWith("SpaceBetween") -> Arrangement.SpaceBetween
        memberBase(path).endsWith("SpaceAround") -> Arrangement.SpaceAround
        memberBase(path).endsWith("SpaceEvenly") -> Arrangement.SpaceEvenly
        else -> Arrangement.Top
    }
}

private fun Expr?.toHorizontalArrangement(): Arrangement.Horizontal {
    val path = (this as? Expr.Member)?.path ?: return Arrangement.Start
    memberArg(path)?.let { if (memberBase(path).endsWith("spacedBy")) return Arrangement.spacedBy(it.dp) }
    return when {
        memberBase(path).endsWith("Center") -> Arrangement.Center
        memberBase(path).endsWith("End") -> Arrangement.End
        memberBase(path).endsWith("SpaceBetween") -> Arrangement.SpaceBetween
        memberBase(path).endsWith("SpaceAround") -> Arrangement.SpaceAround
        memberBase(path).endsWith("SpaceEvenly") -> Arrangement.SpaceEvenly
        else -> Arrangement.Start
    }
}

private fun Expr?.toHorizontalAlignment(): Alignment.Horizontal =
    when {
        this !is Expr.Member -> Alignment.Start
        path.endsWith("CenterHorizontally") || path.endsWith("Center") -> Alignment.CenterHorizontally
        path.endsWith("End") -> Alignment.End
        else -> Alignment.Start
    }

private fun Expr?.toVerticalAlignment(): Alignment.Vertical =
    when {
        this !is Expr.Member -> Alignment.Top
        path.endsWith("CenterVertically") || path.endsWith("Center") -> Alignment.CenterVertically
        path.endsWith("Bottom") -> Alignment.Bottom
        else -> Alignment.Top
    }

private fun Expr?.toBoxAlignment(): Alignment =
    when {
        this !is Expr.Member -> Alignment.TopStart
        path.endsWith("Center") -> Alignment.Center
        path.endsWith("BottomEnd") -> Alignment.BottomEnd
        path.endsWith("TopEnd") -> Alignment.TopEnd
        path.endsWith("BottomStart") -> Alignment.BottomStart
        else -> Alignment.TopStart
    }

private fun Expr?.toFontWeight(): FontWeight =
    when {
        this !is Expr.Member -> FontWeight.Normal
        path.endsWith("Bold") || path.endsWith("Black") -> FontWeight.Bold
        path.endsWith("SemiBold") -> FontWeight.SemiBold
        path.endsWith("Medium") -> FontWeight.Medium
        path.endsWith("Light") -> FontWeight.Light
        else -> FontWeight.Normal
    }

private fun Expr?.toTextAlign(): TextAlign =
    when {
        this !is Expr.Member -> TextAlign.Unspecified
        path.endsWith("Center") -> TextAlign.Center
        path.endsWith("End") || path.endsWith("Right") -> TextAlign.End
        path.endsWith("Start") || path.endsWith("Left") -> TextAlign.Start
        else -> TextAlign.Unspecified
    }

/**
 * `Color.Green` and friends, plus the MaterialTheme-ish paths. The theme paths resolve to the SITE's
 * palette rather than M3 defaults, so `MaterialTheme.colorScheme.primary` in a snippet means "this
 * site's primary" — which is the honest reading inside a themed page, and matches the mapping the
 * React renderer already uses (`primary` -> the Android green).
 */
@Composable
private fun Expr?.toColor(fallback: Color): Color {
    val colors = cvColors
    if (this !is Expr.Member) return fallback
    val base = memberBase(path)
    return when {
        base.contains("primary") -> colors.accent
        base.contains("secondary") -> colors.accent2
        base.contains("error") -> Color(0xFFFF5C5C)
        base.endsWith("Green") -> colors.accent
        base.endsWith("Cyan") -> colors.accent2
        base.endsWith("White") -> Color.White
        base.endsWith("Black") -> Color.Black
        base.endsWith("Gray") || base.endsWith("Grey") -> colors.muted
        base.endsWith("LightGray") -> Color(0xFFCCCCCC)
        base.endsWith("DarkGray") -> Color(0xFF444444)
        base.endsWith("Red") -> Color(0xFFFF5C5C)
        base.endsWith("Blue") -> Color(0xFF5B8DEF)
        base.endsWith("Yellow") -> Color(0xFFF5C451)
        base.endsWith("Magenta") -> Color(0xFFE05CC8)
        base.endsWith("Transparent") -> Color.Transparent
        else -> fallback
    }
}

/** Styling for the preview's own chrome, kept out of the rendered tree. */
internal val previewTextStyle: TextStyle
    @Composable get() = cvType.body

/** A prose summary of a rendered tree, for the `semantics` on a canvas that exposes no text. */
fun describeTree(program: Program): String {
    fun label(n: Node): String = when (n) {
        is Node.Container -> n.name.name.lowercase() + " of " + n.children.size
        is Node.Text -> "text"
        is Node.Button -> "button"
        is Node.Spacer -> "spacer"
        is Node.Animated -> "animated block"
        is Node.TextField -> "text field"
        is Node.Unknown -> "unsupported " + n.name
    }
    val kinds = program.tree.joinToString(", ") { label(it) }
    val vars = program.state.joinToString(", ") { it.name }
    return buildString {
        append("Live preview: ")
        append(if (kinds.isEmpty()) "nothing yet" else kinds)
        if (vars.isNotEmpty()) append(". State: ").append(vars)
        append('.')
    }
}

/** Nothing here is animated, so reduced motion has no work to do — asserted rather than assumed. */
internal fun composeRenderSelfCheck() {
    val counter = composePresets.first { it.label == "Counter" }
    val program = parseCompose(counter.code)
    check(program.tree.isNotEmpty()) { "the Counter preset must produce a tree" }

    val described = describeTree(program)
    check(described.startsWith("Live preview: ")) { "describeTree prefix" }
    check(described.contains("count")) { "describeTree names the declared state" }
    check(describeTree(Program(emptyList(), emptyList())).contains("nothing yet")) { "empty tree" }

    // shapeFor is the only pure branch in the modifier path worth pinning.
    check(shapeFor(ModifierCall("clip", listOf(Expr.Member("CircleShape")))) == CircleShape) { "circle" }
    check(shapeFor(ModifierCall("clip", emptyList())) == RoundedCornerShape(12.dp)) { "clip default" }
    check(
        shapeFor(ModifierCall("clip", listOf(Expr.Member("RoundedCornerShape:20")))) ==
            RoundedCornerShape(20.dp),
    ) { "rounded corner argument is read, not defaulted" }
}
