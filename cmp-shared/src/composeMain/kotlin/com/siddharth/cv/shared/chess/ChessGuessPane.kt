package com.siddharth.cv.shared.chess

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.siddharth.cv.shared.anthology.grouped
import com.siddharth.cv.shared.data.generated.chessPositions
import com.siddharth.cv.shared.theme.GhostButton
import com.siddharth.cv.shared.theme.MonoMeta
import com.siddharth.cv.shared.theme.cvColor
import com.siddharth.cv.shared.theme.cvType

/**
 * Guess the Move, ported from `chess/GuessTheMove.tsx`.
 *
 * This is the one pane of ChessRoom's board-shaped half that ports, and it ports because it needs
 * no chess engine. The quiz draws the FINAL position of a real game and asks whether he won it; the
 * only thing read out of the FEN is the piece placement and the side to move. No legality, no move
 * generation, no chess.js. `ChessBoardPane` and `DailyPuzzle` are the ones that do, and they are
 * absent for that reason rather than this one. See [chessBoardPaneCost] for what they would cost
 * now that `labs/ChessEngine.kt` has taken the move generator off that bill.
 *
 * WHAT THE BOARD LOSES, said plainly rather than drawn over. The web's board is `react-chessboard`
 * with real piece artwork. This port ships no chess piece artwork and its two bundled font families
 * carry no chess glyphs, so a Unicode king would render as a missing-glyph box on at least one
 * target. So pieces are lettered discs in the standard algebraic letters, light disc for White and
 * dark for Black, and the copy under the board says that is what they are. A diagram of a position
 * is honest; a board with holes in it is not.
 *
 * The board carries no semantics, which is deliberate and is also what the web does: 64 squares of
 * a position you cannot act on is noise to a screen reader, so the question and the answer text
 * carry everything the quiz is actually about.
 *
 * The web also bumps a playhtml-backed counter shared between visitors on every guess. There is no
 * backend here, so there is no shared counter, and nothing in this pane pretends there is one.
 */

private val DarkSquare: Color = cvColor("#2A3B33")
private val LightSquare: Color = cvColor("#C9D6CD")
private val BoardMaxWidth: Dp = 320.dp

/** Standard algebraic letters. `n` for knight, because `k` is the king. */
private val PieceLetters: Map<Char, String> =
    mapOf('p' to "P", 'n' to "N", 'b' to "B", 'r' to "R", 'q' to "Q", 'k' to "K")

/**
 * The FEN's piece-placement field as 64 slots, index 0 = a1 and 63 = h8, the same convention the
 * generator's square matrix and [squareName] use. Uppercase is White, lowercase is Black, and a
 * digit is that many empty squares. The corpus's FENs from chess.com carry four fields rather than
 * six because the export drops the move counters, which costs this nothing: only fields 0 and 1
 * are read.
 */
internal fun fenPieces(fen: String): List<Char?> {
    val squares = arrayOfNulls<Char>(64)
    var rank = 7
    var file = 0
    for (c in fen.substringBefore(' ')) {
        when {
            c == '/' -> {
                rank--
                file = 0
            }
            c.isDigit() -> file += c - '0'
            else -> {
                if (rank in 0..7 && file in 0..7) squares[rank * 8 + file] = c
                file++
            }
        }
    }
    return squares.toList()
}

internal fun sideToMove(fen: String): String = if (fen.split(" ").getOrNull(1) == "b") "Black" else "White"

/**
 * ponytail: a fixed coprime stride instead of the web's `Math.random` step. The twin prerenders to
 * static HTML, and a random first position would churn that output on every build for no reader's
 * benefit. A stride coprime with the corpus size still visits every position before repeating, and
 * the walk down from 7 always terminates because 1 is coprime with everything.
 */
private val quizStride: Int = (7 downTo 1).first { stride -> gcd(stride, chessPositions.size) == 1 }

private tailrec fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GuessThePositionPane() {
    val measurer = rememberTextMeasurer(cacheSize = 16)
    var index by remember { mutableStateOf(0) }
    var guess by remember { mutableStateOf<String?>(null) }
    var right by remember { mutableStateOf(0) }
    var asked by remember { mutableStateOf(0) }

    val position = chessPositions[index]
    val correct = guess == position.result

    Section("// guess the move", "Won it or lost it?") {
        BasicText(
            text =
                "The last position of one of the ${chessPositions.size.grouped()} finished games the " +
                    "generator kept a board for, chess.com's only, because lichess's export ships no " +
                    "FEN. ${sideToMove(position.fen)} is to move. Call it.",
            modifier = Modifier.widthIn(max = 680.dp),
            style = cvType.bodySmall,
        )
        Spacer(Modifier.height(20.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            PositionBoard(position.fen, measurer)
            Column(Modifier.widthIn(min = 240.dp, max = 420.dp)) {
                if (guess == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf("win" to "he won it", "loss" to "he lost it").forEach { (value, label) ->
                            GhostButton(
                                text = label,
                                onClick = {
                                    guess = value
                                    asked += 1
                                    if (value == position.result) right += 1
                                },
                            )
                        }
                    }
                } else {
                    GhostButton(
                        text = "next position",
                        onClick = {
                            guess = null
                            index = (index + quizStride) % chessPositions.size
                        },
                    )
                }
                Spacer(Modifier.height(14.dp))
                BasicText(
                    text =
                        if (guess == null) {
                            "Pick one."
                        } else {
                            "${if (correct) "Right" else "Wrong"}. He " +
                                "${if (position.result == "win") "won" else "lost"} it. " +
                                "${position.speed} on ${position.at}, rated " +
                                "${position.myRating.grouped()} at the time."
                        },
                    modifier =
                        Modifier
                            .widthIn(max = 420.dp)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                    style = cvType.bodySmall,
                )
                if (asked > 0) {
                    Spacer(Modifier.height(10.dp))
                    MonoMeta("$right of $asked called correctly")
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        BasicText(
            text =
                "Pieces are lettered discs in standard algebraic notation, light for White and dark " +
                    "for Black. The web draws real piece artwork through react-chessboard; this port " +
                    "ships none and its fonts carry no chess glyphs, so the position is diagrammed " +
                    "rather than illustrated.",
            modifier = Modifier.widthIn(max = 680.dp),
            style = cvType.metaMono,
        )
    }
}

@Composable
private fun PositionBoard(fen: String, measurer: TextMeasurer) {
    val pieces = fenPieces(fen)
    val glyph = cvType.mono.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold)
    Box(Modifier.widthIn(max = BoardMaxWidth).fillMaxWidth().aspectRatio(1f)) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
            val cell = size.width / 8f
            // Screen row 0 is rank 8, so a white-perspective board reads a8 at the top left.
            for (row in 0 until 8) {
                for (file in 0 until 8) {
                    val rank = 7 - row
                    val light = (rank + file) % 2 == 1
                    val topLeft = Offset(file * cell, row * cell)
                    drawRect(if (light) LightSquare else DarkSquare, topLeft, Size(cell, cell))
                    val piece = pieces[rank * 8 + file]
                    val letter = piece?.let { PieceLetters[it.lowercaseChar()] }
                    if (letter != null) {
                        val white = piece.isUpperCase()
                        val radius = cell * 0.36f
                        val centre = Offset(topLeft.x + cell / 2f, topLeft.y + cell / 2f)
                        drawCircle(if (white) LightSquare else DarkSquare, radius, centre)
                        drawCircle(
                            color = if (white) DarkSquare else LightSquare,
                            radius = radius,
                            center = centre,
                            style = Stroke(width = 1.dp.toPx()),
                        )
                        val layout = measurer.measure(letter, glyph)
                        drawText(
                            textLayoutResult = layout,
                            color = if (white) DarkSquare else LightSquare,
                            topLeft =
                                Offset(
                                    centre.x - layout.size.width / 2f,
                                    centre.y - layout.size.height / 2f,
                                ),
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------------
// The two panes that are out, and what they would actually cost
// -------------------------------------------------------------------------------------------

/**
 * WHY `ChessBoardPane` AND `DailyPuzzle` ARE ABSENT, and what they would now actually cost.
 *
 * The first answer this file gave was that a chess engine is not the expensive part but a legal
 * move generator is: `chess/search.ts` is 201 lines of alpha-beta that would port in an afternoon,
 * while every line of it that touches `chess.js` (`moves()`, `move(san)`, `undo()`, `isCheck()`,
 * `board()`, `turn()`) leans on pins, en passant, castling through check, promotion and SAN
 * disambiguation. That was the right shape of the cost. It is no longer the state of this repo.
 *
 * `labs/ChessEngine.kt` now carries a 0x88 legal move generator, make/unmake, an alpha-beta search
 * and a perft harness over the standard test positions, and it is `internal`, so it is visible from
 * here. The move-generator wall is gone. What is left for a playable board is not nothing, and it
 * is worth writing down rather than rediscovering:
 *
 *  - An interactive board: tap to select, legal targets highlighted, a promotion choice. This pane
 *    draws a position but never accepts a move, which is most of the difference.
 *  - The two calibration presets from `chess/calibration.ts`, which are the whole point of the
 *    feature: the bot is named after two of his own ratings, not after difficulty tiers.
 *  - A decision about the clock. `clockBudget` is what makes the bot share his flaw as well as his
 *    rating, and `labs/ChessEngine.kt` drops the wall-clock budget on purpose so its tree stays a
 *    function of its inputs. Reproducing the flaw means putting the budget back on the caller's
 *    side; not reproducing it means the copy may not claim the bot hurries the finish.
 *  - A place to run it. There is no Web Worker here and wasmJs has no background thread, so a
 *    depth-4 reply is a visible stall on the frame that asks for it.
 *
 * That is a day of work resting on another file's engine, not an afternoon, and it is the honest
 * reason it is not in this pass rather than a wall. `DailyPuzzle` is the same board plus a captured
 * lichess puzzle and its solution line, both of which sit unread in `chess.ts`.
 *
 * `GuessThePositionPane` is the part that never needed any of it, and it is above. It deliberately
 * does NOT borrow `labs.ChessPosition` to hold its board: coupling a quiz that only needs piece
 * placement to a 789-line engine would trade 18 lines of [fenPieces] for a dependency on every
 * future change to a move generator it has no use for.
 */
/** Referenced from the KDoc above so the shape of the remaining cost cannot drift out of the file. */
internal fun chessBoardPaneCost(): String =
    "an interactive board, the two calibration presets, and a clock decision, over the move " +
        "generator labs/ChessEngine.kt already carries"

// -------------------------------------------------------------------------------------------
// Self-check
// -------------------------------------------------------------------------------------------

/** ponytail: one runnable check, called from [chessScreenSelfCheck]. */
@Suppress("MagicNumber")
internal fun chessGuessPaneSelfCheck() {
    check(chessPositions.isNotEmpty()) { "no quiz positions" }
    check(chessPositions.all { it.result == "win" || it.result == "loss" }) { "a quiz position has no outcome" }

    // The starting position, which is the one FEN whose square-by-square answer is known by heart.
    val start = fenPieces("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
    check(start.size == 64) { "fenPieces did not fill 64 squares: ${start.size}" }
    check(start[0] == 'R' && start[4] == 'K') { "a1 and e1 are not the white rook and king" }
    check(start[60] == 'k' && start[63] == 'r') { "e8 and h8 are not the black king and rook" }
    check(start.count { it != null } == 32) { "the starting position is not 32 pieces" }
    check(start.slice(16..47).all { it == null }) { "ranks 3 to 6 are not empty" }

    // Every corpus FEN has to parse, and none may put a piece on a square that does not exist.
    chessPositions.forEach { position ->
        val board = fenPieces(position.fen)
        check(board.size == 64) { "FEN did not fill 64 squares: ${position.fen}" }
        check(board.any { it == 'k' } && board.any { it == 'K' }) { "a FEN lost a king: ${position.fen}" }
        check(board.all { it == null || it.lowercaseChar() in PieceLetters }) { "unknown piece: ${position.fen}" }
    }
    check(sideToMove("8/8/8/8/8/8/8/8 b - -") == "Black") { "side to move is misread" }
    check(sideToMove("8/8/8/8/8/8/8/8 w - -") == "White") { "side to move is misread" }

    // The walk visits every position before it repeats, which is what makes "next" mean next.
    check(gcd(quizStride, chessPositions.size) == 1) { "the quiz stride does not cover the corpus" }
    val seen = mutableSetOf<Int>()
    var at = 0
    repeat(chessPositions.size) {
        seen.add(at)
        at = (at + quizStride) % chessPositions.size
    }
    check(seen.size == chessPositions.size) { "the quiz walk repeats before covering the corpus" }
    check(chessBoardPaneCost().contains("labs/ChessEngine.kt")) { "the board-pane cost lost its dependency" }
}
