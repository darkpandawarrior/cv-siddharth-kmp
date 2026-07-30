package com.siddharth.cv.shared.playground

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.siddharth.cv.shared.theme.CvContentMaxWidth
import com.siddharth.cv.shared.theme.CvGutter
import com.siddharth.cv.shared.theme.SectionEyebrow
import com.siddharth.cv.shared.theme.SectionHeading
import com.siddharth.cv.shared.theme.cvColors
import com.siddharth.cv.shared.theme.cvType

/**
 * The playground: an editor on the left, a live preview on the right.
 *
 * **There is no Run button, and that is the point.** A playground that genuinely compiles Kotlin
 * needs a server — a warm Gradle daemon, a prebuilt Compose base module, and a 5-15 second round
 * trip — because the editor and the compiled result are two separate programs. This one interprets a
 * curated subset of Compose and hands the result to the *same* Compose runtime that is drawing this
 * page, so the preview updates as you type, with no backend and no compile step.
 *
 * The trade is honest and stated on screen: no compiler means a curated slice of the language, not
 * all of Kotlin. What it buys is that everything you do see is real — a real `Column`, a real
 * Material 3 `Button`, real `AnimatedVisibility`, real recomposition driven by real observable state.
 */
@Composable
fun PlaygroundScreen(modifier: Modifier = Modifier) {
    val colors = cvColors
    var code by remember { mutableStateOf(composePresets.first().code) }
    var activePreset by remember { mutableStateOf(composePresets.first().label) }

    // Parsed on every edit. The subset is small enough that parsing per keystroke is cheaper than
    // the debounce machinery would be — measure before adding one.
    val parsed = remember(code) { runCatching { parseCompose(code) }.getOrNull() }
    val parseError = remember(code) { runCatching { parseCompose(code) }.exceptionOrNull() }

    // State is rebuilt only when the *declarations* change, not on every keystroke — otherwise
    // editing a colour would reset a counter you had just clicked up to 7. `stateSignature` exists
    // for exactly this: it changes when a var is added, removed, renamed or re-initialised.
    val signature = parsed?.let { stateSignature(it) } ?: ""
    val state = remember(signature) { ComposeState(parsed?.state ?: emptyList()) }

    val widthDp = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    val stacked = widthDp < 900.dp

    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = CvGutter, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = CvContentMaxWidth).fillMaxWidth()) {
            SectionEyebrow("// the playground")
            SectionHeading("Type Compose, see Compose")
            Spacer(Modifier.height(12.dp))
            BasicText(
                text =
                    "A curated slice of Compose, interpreted and handed to the same runtime that " +
                        "draws this page — so there is no Run button and no server round-trip. " +
                        "Everything rendered is the real composable, not a styled div: a real " +
                        "Column, a real Material 3 Button, real AnimatedVisibility, real state.",
                style = cvType.bodySmall,
            )

            Spacer(Modifier.height(20.dp))
            PresetRow(active = activePreset) { preset ->
                activePreset = preset.label
                code = preset.code
            }

            Spacer(Modifier.height(20.dp))
            if (stacked) {
                EditorPane(code = code, onCodeChange = { code = it }, error = parseError?.message)
                Spacer(Modifier.height(20.dp))
                PreviewPane(parsed = parsed, state = state, stacked = true)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Box(Modifier.weight(1f)) {
                        EditorPane(code = code, onCodeChange = { code = it }, error = parseError?.message)
                    }
                    PreviewPane(parsed = parsed, state = state, stacked = false)
                }
            }

            Spacer(Modifier.height(16.dp))
            BasicText(
                text =
                    "Supported: Column · Row · Box · Card · Surface · Text · Button · Spacer · " +
                        "AnimatedVisibility · TextField, a modifier chain, and " +
                        "remember { mutableStateOf(...) }. Anything outside that renders as an " +
                        "explicit marker rather than silently vanishing.",
                style = cvType.metaMono.copy(color = colors.muted),
            )
        }
    }
}

@Composable
private fun PresetRow(active: String, onPick: (ComposePreset) -> Unit) {
    val colors = cvColors
    Row(
        Modifier.fillMaxWidth().horizontalScrollIfNarrow(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        composePresets.forEach { preset ->
            val on = preset.label == active
            BasicText(
                text = preset.label,
                modifier =
                    Modifier.clip(RoundedCornerShape(999.dp))
                        .background(if (on) colors.accent.copy(alpha = 0.14f) else colors.card)
                        .border(
                            1.dp,
                            if (on) colors.accent else colors.line,
                            RoundedCornerShape(999.dp),
                        )
                        .clickable(role = Role.Tab) { onPick(preset) }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                style = cvType.metaMono.copy(color = if (on) colors.accent else colors.muted),
            )
        }
    }
}

@Composable
private fun EditorPane(code: String, onCodeChange: (String) -> Unit, error: String?) {
    val colors = cvColors
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.card)
            .border(1.dp, colors.line, RoundedCornerShape(12.dp)),
    ) {
        // Window chrome, matching the React original's "Playground.kt" title bar.
        Row(
            Modifier.fillMaxWidth().background(colors.surface).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf(Color(0xFFFF5F57), Color(0xFFFEBC2E), Color(0xFF28C840)).forEach {
                Box(Modifier.size(10.dp).clip(CircleShape).background(it))
            }
            Spacer(Modifier.width(8.dp))
            BasicText("Playground.kt", style = cvType.metaMono.copy(color = colors.muted))
            Spacer(Modifier.weight(1f))
            BasicText(
                "live · no compile step",
                style = cvType.metaMono.copy(color = colors.accent.copy(alpha = 0.8f)),
            )
        }

        Row(Modifier.padding(12.dp)) {
            // Line numbers as their own column, derived from the text so they cannot drift.
            Column(horizontalAlignment = Alignment.End) {
                code.lines().forEachIndexed { i, _ ->
                    BasicText(
                        text = "${i + 1}",
                        style = cvType.mono.copy(color = colors.muted.copy(alpha = 0.5f)),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            BasicTextField(
                value = code,
                onValueChange = onCodeChange,
                modifier = Modifier.fillMaxWidth().heightIn(min = 260.dp),
                textStyle = cvType.mono.copy(color = colors.onBackground),
                cursorBrush = SolidColor(colors.accent),
            )
        }

        // A parse failure is shown, never swallowed — an editor that goes quiet on bad input is
        // worse than one that says what it could not read.
        if (error != null) {
            BasicText(
                text = "parse: $error",
                modifier =
                    Modifier.fillMaxWidth()
                        .background(Color(0xFFFF5C5C).copy(alpha = 0.10f))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                style = cvType.metaMono.copy(color = Color(0xFFFF8A8A)),
            )
        }
    }
}

@Composable
private fun PreviewPane(parsed: Program?, state: ComposeState, stacked: Boolean) {
    val colors = cvColors
    val frame =
        if (stacked) Modifier.fillMaxWidth().height(420.dp)
        else Modifier.width(300.dp).height(560.dp)

    Column(
        frame
            .clip(RoundedCornerShape(28.dp))
            .background(colors.surface)
            .border(2.dp, colors.line, RoundedCornerShape(28.dp))
            .padding(8.dp),
    ) {
        // The speaker notch, so the pane reads as a device rather than a panel.
        Box(Modifier.fillMaxWidth().padding(bottom = 8.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.width(56.dp).height(5.dp).clip(RoundedCornerShape(999.dp)).background(colors.line))
        }
        Box(
            Modifier.fillMaxSize()
                .clip(RoundedCornerShape(20.dp))
                .background(colors.ink)
                .semantics {
                    contentDescription =
                        parsed?.let { describeTree(it) } ?: "Live preview: nothing to render yet."
                },
        ) {
            if (parsed == null || parsed.tree.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    BasicText(
                        "nothing to render yet",
                        style = cvType.metaMono.copy(color = colors.muted),
                    )
                }
            } else {
                Column(Modifier.fillMaxSize()) { RenderTree(parsed.tree, state) }
            }
        }
    }
}

/**
 * The preset row overflows on a phone. `horizontalScroll` needs a ScrollState hoisted into
 * composition, so this keeps that noise out of the layout above.
 */
@Composable
private fun Modifier.horizontalScrollIfNarrow(): Modifier = horizontalScroll(rememberScrollState())

/** The preset table is data the screen depends on; a duplicate label would break the active-tab read. */
internal fun playgroundScreenSelfCheck() {
    check(composePresets.isNotEmpty()) { "there must be at least one preset" }
    check(composePresets.size == 7) { "all seven React presets are expected, got ${composePresets.size}" }
    check(composePresets.map { it.label }.toSet().size == composePresets.size) {
        "preset labels must be unique — the active-tab highlight compares by label"
    }
    composePresets.forEach { preset ->
        check(preset.code.isNotBlank()) { "${preset.label} has no code" }
        val program = parseCompose(preset.code)
        check(program.tree.isNotEmpty()) { "${preset.label} parses to an empty tree" }
    }
}
