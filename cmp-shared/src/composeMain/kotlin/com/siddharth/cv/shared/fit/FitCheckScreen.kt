package com.siddharth.cv.shared.fit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.siddharth.cv.shared.chat.CHAT_CONTACT_FALLBACK
import com.siddharth.cv.shared.chat.CHAT_ENDPOINT
import com.siddharth.cv.shared.chat.chatCapability
import com.siddharth.cv.shared.theme.CvCard
import com.siddharth.cv.shared.theme.CvContentMaxWidth
import com.siddharth.cv.shared.theme.CvGutter
import com.siddharth.cv.shared.theme.PrimaryButton
import com.siddharth.cv.shared.theme.Reveal
import com.siddharth.cv.shared.theme.SectionEyebrow
import com.siddharth.cv.shared.theme.cvColors
import com.siddharth.cv.shared.theme.cvType
import com.siddharth.kmp.llmchat.AiChunk
import com.siddharth.kmp.llmchat.AiMessage
import com.siddharth.kmp.llmchat.HttpChatConfig
import com.siddharth.kmp.llmchat.HttpChatProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Port of `cv-siddharth/src/FitCheck.tsx` — "Hiring? Paste the job description." — plus the JD
 * path of `FloatingChat.tsx`'s `send()` that it hands off to (`openJdFit`).
 *
 * DELIBERATELY SELF-CONTAINED rather than routed through [com.siddharth.cv.shared.chat.FloatingChat]'s
 * transcript the way the React build does it. The React panel can render `[[jdfit:{…}]]` as a real
 * component because it already carries a general generative-UI directive parser (`chatBlocks.ts`)
 * for `[[rooms]]` and friends; this port's chat panel has none — it strips every directive instead
 * of rendering one (`FloatingChat.kt`'s `stripDirectives`, and its own doc comment lists the JD fit
 * analyzer as one of the things that didn't come across for exactly that reason). Building that
 * whole subsystem to carry one directive kind would be the wrong trade for what this screen needs,
 * so it owns its own request, its own directive parse ([parseJdFitDirective]) and its own card.
 * ponytail: if a widget renderer for `[[rooms]]` ever lands in the chat panel, folding this in
 * becomes cheap — not before.
 *
 * SAME OFFLINE-FIRST CONTRACT as the web build: [matchJd] renders a real scorecard in the same
 * frame as the paste, from a stack shipped in this bundle, no network and nothing to fail. The
 * model's reply — mode `"jd"` through kmp-toolkit's [HttpChatProvider], which is the ONE caller in
 * this app that provider is safe for (see settings.gradle.kts) — supersedes it if and when it
 * arrives; a rate limit, a 502 or a dead provider still leaves a recruiter with a real answer.
 */
@Composable
fun FitCheckScreen(modifier: Modifier = Modifier) {
    val unavailableReason = remember { chatCapability().unavailableReason }

    Column(modifier = modifier.fillMaxWidth()) {
        Reveal {
            Column(Modifier.widthIn(max = CvContentMaxWidth).fillMaxWidth().padding(horizontal = CvGutter)) {
                SectionEyebrow("// fit check")
                Spacer(Modifier.height(8.dp))
                BasicText("Hiring? Paste the job description.", style = cvType.cardTitle)
                Spacer(Modifier.height(8.dp))
                BasicText(
                    "My AI assistant reads it against what he's actually shipped and answers the " +
                        "only question that matters: where he fits, and where he doesn't. No score " +
                        "inflation — the gaps come with the strengths.",
                    modifier = Modifier.widthIn(max = 640.dp),
                    style = cvType.body.copy(color = cvColors.muted),
                )
                Spacer(Modifier.height(20.dp))
                if (unavailableReason != null) {
                    FitUnavailableBadge()
                } else {
                    FitCheckForm()
                }
            }
        }
    }
}

@Composable
private fun FitUnavailableBadge() {
    val red = Color(0xFFFF5C7A)
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier
            .widthIn(max = 420.dp)
            .background(red.copy(alpha = 0.08f), shape)
            .border(1.dp, red.copy(alpha = 0.35f), shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        BasicText(CHAT_CONTACT_FALLBACK, style = cvType.bodySmall.copy(color = red))
    }
}

@Composable
private fun FitCheckForm() {
    val colors = cvColors
    val scope = rememberCoroutineScope()
    var jd by remember { mutableStateOf("") }
    var report by remember { mutableStateOf<JdFitReport?>(null) }
    var final by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    val busy = job != null

    fun run() {
        val text = jd.trim()
        if (text.isEmpty() || busy) return

        // The offline pass, in the same frame as the click: matchJd is pure string work over a
        // stack shipped in this bundle, so there is a real scorecard on screen before any request
        // goes out. `asked == 0` means the text named nothing recognisable — a spinner beats a
        // scorecard full of zeroes.
        val offline = matchJd(text)
        report = if (offline.asked > 0) toFitReport(offline, final = false) else null
        final = false

        job = scope.launch {
            val provider = HttpChatProvider(HttpChatConfig(endpoint = CHAT_ENDPOINT, mode = "jd"))
            val reply = StringBuilder()
            try {
                provider.completeStream(listOf(AiMessage(AiMessage.Role.USER, text))).collect { chunk ->
                    if (chunk is AiChunk.Token) reply.append(chunk.text)
                    // AiChunk.Failed falls through to the offline-final card below, same as a
                    // stream that produced no directive at all — the reason doesn't change what
                    // a recruiter needs to see.
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Throwable) {
                // Transport-level failure never reaches AiChunk.Failed — same fallback either way.
            }
            // The model's answer SUPERSEDES the offline card rather than appending to it.
            val modelReport = parseJdFitDirective(reply.toString())
            if (modelReport != null) {
                report = modelReport
            } else if (offline.asked > 0) {
                report = toFitReport(offline, final = true)
                final = true
            }
            job = null
        }
    }

    Column(
        modifier = Modifier
            .widthIn(max = 720.dp)
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(16.dp))
            .border(1.dp, colors.line, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(colors.ink, RoundedCornerShape(12.dp))
                .border(1.dp, colors.line, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            if (jd.isEmpty()) {
                BasicText(
                    "Paste the whole thing — responsibilities, requirements, the years-of-" +
                        "experience line. The more of it I get, the less I have to guess.",
                    style = cvType.bodySmall.copy(color = colors.muted),
                )
            }
            BasicTextField(
                value = jd,
                onValueChange = { jd = it.take(JD_MAX_CHARS) },
                textStyle = cvType.bodySmall.copy(color = colors.onBackground),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
            )
        }

        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            BasicText(
                "${jd.length} / $JD_MAX_CHARS",
                style = cvType.metaMono.copy(color = if (isJdNearCap(jd.length)) colors.accent else colors.muted),
            )
            PrimaryButton(
                text = if (busy) "Analysing…" else "Analyse fit",
                onClick = { run() },
            )
        }

        report?.let { r ->
            Spacer(Modifier.height(16.dp))
            FitReportCard(r, final = final && !busy)
        }
    }
}

@Composable
private fun FitReportCard(report: JdFitReport, final: Boolean) {
    val colors = cvColors
    CvCard(modifier = Modifier.fillMaxWidth(), glowOnHover = false) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                report.role?.let { BasicText(it, style = cvType.metaMono.copy(color = colors.muted)) }
                BasicText(
                    "${report.score}/100",
                    style = cvType.metric.copy(color = fitColor(report.score, colors.accent)),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        BasicText(report.summary, style = cvType.bodySmall.copy(color = colors.onBackground))

        if (report.strengths.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            BasicText("STRENGTHS", style = cvType.eyebrow)
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                report.strengths.forEach { s ->
                    FitRow(need = s.need, note = s.evidence, tint = colors.accent)
                }
            }
        }

        if (report.gaps.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            BasicText("GAPS", style = cvType.eyebrow)
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                report.gaps.forEach { g -> FitRow(need = g.need, note = g.note, tint = Color(0xFFFF5C7A)) }
            }
        }

        if (!final) return@CvCard
        Spacer(Modifier.height(12.dp))
        BasicText(
            "This card is the offline keyword match; the model's full read didn't arrive in time.",
            style = cvType.metaMono.copy(color = colors.muted),
        )
    }
}

@Composable
private fun FitRow(need: String, note: String, tint: Color) {
    val colors = cvColors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        BasicText(
            need,
            modifier = Modifier.widthIn(min = 140.dp),
            style = cvType.bodySmall.copy(color = tint, fontWeight = FontWeight.SemiBold),
        )
        BasicText(note, style = cvType.bodySmall.copy(color = colors.muted))
    }
}

private const val FIT_SCORE_STRONG = 70
private const val FIT_SCORE_MODERATE = 45
private val FIT_COLOR_STRONG = Color(0xFF3DD68C)
private val FIT_COLOR_WEAK = Color(0xFFFF5C7A)

/** Green above a strong match, amber in the middle, red below — same bands hire.tsx's copy uses. */
private fun fitColor(score: Int, accent: Color): Color = when {
    score >= FIT_SCORE_STRONG -> FIT_COLOR_STRONG
    score >= FIT_SCORE_MODERATE -> accent
    else -> FIT_COLOR_WEAK
}
