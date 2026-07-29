package com.siddharth.cv.shared.terminal

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.siddharth.cv.shared.LocalNav
import com.siddharth.cv.shared.Route
import com.siddharth.cv.shared.theme.CvMotion
import com.siddharth.cv.shared.theme.CvColors
import com.siddharth.cv.shared.theme.GhostButton
import com.siddharth.cv.shared.theme.LocalReducedMotion
import com.siddharth.cv.shared.theme.cvColors
import com.siddharth.cv.shared.theme.cvType
import com.siddharth.cv.shared.theme.rememberInfiniteFloat

/**
 * The CRT shell over [TerminalEngine] — the port of the presentation layer of
 * cv-siddharth/src/Terminal.tsx.
 *
 * It is the one surface here with no web dependency at all: the whole feature is a list of strings,
 * a text field and four key handlers, so what ships on wasm is the same thing that ships on Android
 * and desktop rather than an approximation of it.
 */
private const val PROMPT = "guest@sid.android:~$ "

@Composable
fun TerminalScreen(modifier: Modifier = Modifier) {
    val colors = cvColors
    val nav = LocalNav.current
    val reduced = LocalReducedMotion.current

    val lines = remember { mutableStateListOf<TermLine>().also { it += TerminalEngine.banner } }
    var field by remember { mutableStateOf(TextFieldValue("")) }
    val history = remember { mutableStateListOf<String>() }
    var historyIndex by remember { mutableStateOf(-1) }

    // Only lines at or after this index animate in. Without it a LazyColumn item re-entering
    // composition on scroll would replay its fade, which reads as the screen redrawing itself.
    var animateFrom by remember { mutableStateOf(lines.size) }

    val listState = rememberLazyListState()
    val focus = remember { FocusRequester() }
    val rootInteraction = remember { MutableInteractionSource() }

    fun setInput(text: String) {
        field = TextFieldValue(text, TextRange(text.length))
    }

    fun submit() {
        val raw = field.text
        val entered = raw.trim()
        animateFrom = lines.size
        lines += TermLine(PROMPT + raw, TermTone.DIM)
        val result = TerminalEngine.run(raw)
        if (result.clear) {
            lines.clear()
            lines += TerminalEngine.banner
            animateFrom = lines.size
        }
        lines += result.lines
        if (entered.isNotEmpty()) {
            history.remove(entered)
            history += entered
        }
        historyIndex = -1
        setInput("")
        // Navigation last: the echoed command stays on screen for the back journey.
        result.navigate?.let(nav::go)
    }

    fun walkHistory(back: Boolean) {
        if (history.isEmpty()) return
        if (back) {
            val next = if (historyIndex < 0) history.lastIndex else (historyIndex - 1).coerceAtLeast(0)
            historyIndex = next
            setInput(history[next])
        } else {
            if (historyIndex < 0) return
            val next = historyIndex + 1
            if (next > history.lastIndex) {
                historyIndex = -1
                setInput("")
            } else {
                historyIndex = next
                setInput(history[next])
            }
        }
    }

    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(lines.size) { listState.scrollToItem((lines.size - 1).coerceAtLeast(0)) }

    // ponytail: 3-px repeating gradient instead of a per-scanline draw loop, and NO BlendMode.Screen
    // — the blend forces an offscreen layer on Skia/WebGL for a difference you cannot see at 2%
    // alpha. Swap the two-line brush below if the screen ever needs to actually brighten content.
    val scanlines = remember {
        Brush.verticalGradient(
            0.0f to Color.White.copy(alpha = 0.02f),
            0.34f to Color.White.copy(alpha = 0.02f),
            0.35f to Color.Transparent,
            1.0f to Color.Transparent,
            startY = 0f,
            endY = 3f,
            tileMode = TileMode.Repeated,
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.deepVoid)
            .clickable(interactionSource = rootInteraction, indication = null) { focus.requestFocus() }
            .drawWithContent {
                drawContent()
                drawRect(brush = scanlines)
                // Vignette: the glass curvature of a CRT, cheaper than a shader.
                drawRect(
                    brush = Brush.radialGradient(
                        0.55f to Color.Transparent,
                        1.0f to Color.Black.copy(alpha = 0.55f),
                        center = Offset(size.width / 2f, size.height / 2f),
                        radius = maxOf(size.width, size.height) * 0.72f,
                    ),
                )
            },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TitleBar(colors = colors, onExit = { nav.go(Route.Home) })
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(lines.size) { index ->
                    OutputLine(
                        line = lines[index],
                        colors = colors,
                        animate = !reduced && index >= animateFrom,
                    )
                }
            }
            // Outside the LazyColumn on purpose: as an item it would be disposed the moment the
            // user scrolled the log, silently dropping keyboard focus mid-session.
            InputRow(
                colors = colors,
                value = field,
                onValueChange = { field = it },
                focusRequester = focus,
                onKey = { event ->
                    if (event.type != KeyEventType.KeyDown) {
                        false
                    } else {
                        when (event.key) {
                            Key.Enter, Key.NumPadEnter -> { submit(); true }
                            Key.Tab -> {
                                TerminalEngine.complete(field.text)?.let(::setInput)
                                true
                            }
                            Key.DirectionUp -> { walkHistory(back = true); true }
                            Key.DirectionDown -> { walkHistory(back = false); true }
                            else -> false
                        }
                    }
                },
            )
        }

        SignalSweep(accent = colors.accent)
    }
}

/** `sid.android — /bin/sh` chrome: the three dots, the host label, and the way out. */
@Composable
private fun TitleBar(colors: CvColors, onExit: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.ink.copy(alpha = 0.85f))
            .drawBehind {
                drawRect(
                    color = colors.line,
                    topLeft = Offset(0f, size.height - 1f),
                    size = Size(size.width, 1f),
                )
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        BasicText(
            text = "guest@sid.android — /bin/sh",
            style = cvType.metaMono.copy(color = colors.muted),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            repeat(2) {
                Box(Modifier.size(10.dp).background(colors.line, CircleShape))
                Spacer(Modifier.width(6.dp))
            }
            Box(Modifier.size(10.dp).background(colors.accent, CircleShape))
            Spacer(Modifier.width(16.dp))
            GhostButton(text = "exit", onClick = onExit)
        }
    }
}

/** One printed line. Tone picks the colour; the UI never reads the text. */
@Composable
private fun OutputLine(line: TermLine, colors: CvColors, animate: Boolean) {
    var shown by remember { mutableStateOf(!animate) }
    LaunchedEffect(Unit) { shown = true }
    val progress by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(durationMillis = 140, easing = CvMotion.EaseOutQuart),
        label = "termLineIn",
    )
    BasicText(
        text = line.text,
        modifier = Modifier.graphicsLayer {
            alpha = progress
            translationY = (1f - progress) * 3.dp.toPx()
        },
        style = cvType.mono.copy(
            color = toneColor(line.tone, colors),
            fontWeight = if (line.tone == TermTone.HEAD) FontWeight.Bold else null,
        ),
    )
}

private fun toneColor(tone: TermTone, colors: CvColors): Color = when (tone) {
    TermTone.OUT -> colors.onBackground
    TermTone.DIM -> colors.muted
    TermTone.ACCENT -> colors.accent
    TermTone.ACCENT2 -> colors.accent2
    TermTone.ERROR -> Color(0xFFFF5C7A)
    TermTone.HEAD -> colors.accent
}

/** The live prompt: accent caret, borderless field, and a block cursor that blinks. */
@Composable
private fun InputRow(
    colors: CvColors,
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    focusRequester: FocusRequester,
    onKey: (KeyEvent) -> Boolean,
) {
    val blink by rememberInfiniteFloat(durationMillis = 1100)
    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicText(text = PROMPT, style = cvType.mono.copy(color = colors.accent))
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = cvType.mono.copy(color = colors.onBackground),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onPreviewKeyEvent(onKey)
                    .semantics { contentDescription = "terminal input — type help" },
            )
            if (value.text.isEmpty() && blink < 0.5f) {
                // Block cursor only while the field is empty — past that the real caret takes over.
                Box(Modifier.size(width = 8.dp, height = 16.dp).background(colors.accent.copy(alpha = 0.55f)))
            }
        }
    }
}

/**
 * A slow band of accent crossing the tube — the one ambient motion on this route. Under reduced
 * motion [rememberInfiniteFloat] pins the progress at 0, which parks the band entirely above the
 * viewport: static, and nothing to special-case.
 */
@Composable
private fun SignalSweep(accent: Color) {
    val progress by rememberInfiniteFloat(durationMillis = 7000)
    Box(
        modifier = Modifier.fillMaxSize().drawBehind {
            val band = size.height * 0.4f
            val top = progress * (size.height + band) - band
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.5f to accent.copy(alpha = 0.05f),
                    1f to Color.Transparent,
                    startY = top,
                    endY = top + band,
                ),
                topLeft = Offset(0f, top),
                size = Size(size.width, band),
            )
        },
    )
}
