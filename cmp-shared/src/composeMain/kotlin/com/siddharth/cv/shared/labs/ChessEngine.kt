package com.siddharth.cv.shared.labs

import com.siddharth.cv.shared.data.generated.ChessQuizPosition
import com.siddharth.cv.shared.data.generated.chessPositions
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * A legal-move chess generator and an alpha-beta search, written out so the Chess Search instrument
 * can draw a **real** tree rather than a simulation of one.
 *
 * The React lab (`src/labs/ChessSearchLab.tsx`) gets its legality from `chess.js` and runs the
 * search in a Web Worker. Neither is available here: a wasm target adds no npm package, and there is
 * no worker to offload to. The README's Degraded table used to explain the gap differently — it said
 * both chess instruments "read the generated chess corpus, which this repo does not vendor", which
 * was simply not true: `data/generated/CvChessData.kt` has been vendored the whole time, and Clock
 * Burn cost nothing but a chart. The real price was always the move generator, and a move generator
 * is a few hundred lines that a standard perft check proves correct.
 *
 * So: no dependency, and the one thing that could be silently, plausibly wrong is the one thing with
 * a published oracle. [chessEngineSelfCheck] runs perft over three standard test positions to depths
 * whose totals are documented to the last unit. Allow castling through check and 197,281 stops
 * matching; miss a pin and so does 97,862. That sharpness is the whole reason a chess engine is a
 * reasonable thing to hand-roll and a Leaflet map is not.
 *
 * Board representation is 0x88: a 16-wide array where a square index with any bit of `0x88` set is
 * off the board, so every "did I run off the edge" test is one mask instead of a pair of bounds
 * checks. Pieces are signed — a positive pawn is White's, a negative one is Black's — which makes
 * "is this mine" a sign comparison and [evaluate] a single signed sum.
 */

// -------------------------------------------------------------------------------------------
// Board
// -------------------------------------------------------------------------------------------

private const val PAWN = 1
private const val KNIGHT = 2
private const val BISHOP = 3
private const val ROOK = 4
private const val QUEEN = 5
private const val KING = 6

/** One rank up. The 0x88 board is 16 wide, so a rank step is 16 and a file step is 1. */
private const val RANK_STRIDE = 16

private const val BOARD_SIZE = 128
private const val OFF_BOARD_MASK = 0x88
private const val SQUARE_MASK = 0x7F
private const val PIECE_MASK = 0x7
private const val TO_SHIFT = 7
private const val PROMOTION_SHIFT = 14
private const val FLAG_SHIFT = 17

private val KNIGHT_OFFSETS = intArrayOf(33, 31, 18, 14, -33, -31, -18, -14)
private val KING_OFFSETS = intArrayOf(16, -16, 1, -1, 17, 15, -17, -15)
private val BISHOP_OFFSETS = intArrayOf(17, 15, -17, -15)
private val ROOK_OFFSETS = intArrayOf(16, -16, 1, -1)
private val PROMOTION_PIECES = intArrayOf(QUEEN, ROOK, BISHOP, KNIGHT)

// The squares castling touches, named rather than numbered: `board[6]` is unreadable, `board[G1]`
// is not, and every one of these would otherwise sit as a bare literal inside a rules check.
private const val A1 = 0
private const val B1 = 1
private const val C1 = 2
private const val D1 = 3
private const val E1 = 4
private const val F1 = 5
private const val G1 = 6
private const val H1 = 7
private const val A8 = 112
private const val B8 = 113
private const val C8 = 114
private const val D8 = 115
private const val E8 = 116
private const val F8 = 117
private const val G8 = 118
private const val H8 = 119

private const val CASTLE_WHITE_KING = 1
private const val CASTLE_WHITE_QUEEN = 2
private const val CASTLE_BLACK_KING = 4
private const val CASTLE_BLACK_QUEEN = 8
private const val CASTLE_ALL = 15

/** The squares that must be empty for each of the four castles. */
private val WHITE_KING_SIDE_EMPTY = intArrayOf(F1, G1)
private val WHITE_QUEEN_SIDE_EMPTY = intArrayOf(D1, C1, B1)
private val BLACK_KING_SIDE_EMPTY = intArrayOf(F8, G8)
private val BLACK_QUEEN_SIDE_EMPTY = intArrayOf(D8, C8, B8)

/**
 * Moving *from* or *to* a square clears whatever rights that square carried — one table covers the
 * king moving, the rook moving, and the rook being captured on its home square, which is the case
 * hand-written castling bookkeeping usually forgets.
 */
private val CASTLE_MASK =
    IntArray(BOARD_SIZE) { CASTLE_ALL }.also {
        it[A1] = CASTLE_ALL and CASTLE_WHITE_QUEEN.inv()
        it[E1] = CASTLE_ALL and (CASTLE_WHITE_KING or CASTLE_WHITE_QUEEN).inv()
        it[H1] = CASTLE_ALL and CASTLE_WHITE_KING.inv()
        it[A8] = CASTLE_ALL and CASTLE_BLACK_QUEEN.inv()
        it[E8] = CASTLE_ALL and (CASTLE_BLACK_KING or CASTLE_BLACK_QUEEN).inv()
        it[H8] = CASTLE_ALL and CASTLE_BLACK_KING.inv()
    }

/** Move flags. A quiet move and an ordinary capture share flag 0 — the board says which it was. */
private const val FLAG_EN_PASSANT = 1
private const val FLAG_CASTLE = 2
private const val FLAG_DOUBLE_PUSH = 3

/**
 * A move packed into an `Int`: `from | to shl 7 | promotion shl 14 | flag shl 17`.
 *
 * Boxed move objects would be the readable choice everywhere except inside a search, which
 * allocates one per generated move per node. At ten thousand nodes and forty moves each that is
 * hundreds of thousands of short-lived objects for one button press; an Int is free and the four
 * accessors below cost nothing.
 */
internal fun chessMove(from: Int, to: Int, promotion: Int, flag: Int): Int =
    from or (to shl TO_SHIFT) or (promotion shl PROMOTION_SHIFT) or (flag shl FLAG_SHIFT)

internal fun moveFrom(move: Int): Int = move and SQUARE_MASK

internal fun moveTo(move: Int): Int = (move shr TO_SHIFT) and SQUARE_MASK

internal fun movePromotion(move: Int): Int = (move shr PROMOTION_SHIFT) and PIECE_MASK

private fun moveFlag(move: Int): Int = (move shr FLAG_SHIFT) and PIECE_MASK

private fun offBoard(square: Int): Boolean = (square and OFF_BOARD_MASK) != 0

/** `4 -> "e1"`. Coordinate notation, not SAN — see [ChessSearchResult.moveText]. */
internal fun chessSquareName(square: Int): String =
    "${('a' + (square and 7))}${(square shr 4) + 1}"

/** What [ChessPosition.make] has to put back. */
internal class ChessUndo(val captured: Int, val castle: Int, val enPassant: Int)

/**
 * A position, mutated in place by [make] / [unmake].
 *
 * King squares are tracked as fields rather than found by scanning: legality is decided by "is my
 * king attacked after this move", so the scan would otherwise run once per generated move per node.
 */
internal class ChessPosition(fen: String) {
    val board = IntArray(BOARD_SIZE)
    var side: Int = 1
        private set
    private var castle: Int = 0
    private var enPassant: Int = -1
    private var whiteKing: Int = E1
    private var blackKing: Int = E8

    init {
        val parts = fen.split(' ')
        var rank = 7
        var file = 0
        for (ch in parts[0]) {
            when {
                ch == '/' -> {
                    rank--
                    file = 0
                }
                ch in '1'..'8' -> file += ch - '0'
                else -> {
                    val square = rank * RANK_STRIDE + file
                    val type = pieceType(ch)
                    board[square] = if (ch.isLowerCase()) -type else type
                    if (type == KING) {
                        if (ch.isLowerCase()) blackKing = square else whiteKing = square
                    }
                    file++
                }
            }
        }
        side = if (parts[1] == "w") 1 else -1
        if (parts[2].contains('K')) castle = castle or CASTLE_WHITE_KING
        if (parts[2].contains('Q')) castle = castle or CASTLE_WHITE_QUEEN
        if (parts[2].contains('k')) castle = castle or CASTLE_BLACK_KING
        if (parts[2].contains('q')) castle = castle or CASTLE_BLACK_QUEEN
        enPassant =
            if (parts[3] == "-") -1 else (parts[3][1] - '1') * RANK_STRIDE + (parts[3][0] - 'a')
    }

    private fun pieceType(ch: Char): Int =
        when (ch.lowercaseChar()) {
            'p' -> PAWN
            'n' -> KNIGHT
            'b' -> BISHOP
            'r' -> ROOK
            'q' -> QUEEN
            else -> KING
        }

    fun kingSquare(of: Int): Int = if (of == 1) whiteKing else blackKing

    /** Is [square] attacked by side [by]? Radiates *out* from the square, so it costs no move list. */
    fun attacked(square: Int, by: Int): Boolean {
        // A pawn of `by` attacking this square sits one rank behind it from `by`'s point of view.
        val back = if (by == 1) -RANK_STRIDE else RANK_STRIDE
        val pawn = by * PAWN
        if (pieceAt(square + back + 1) == pawn || pieceAt(square + back - 1) == pawn) return true
        if (leaperHits(square, KNIGHT_OFFSETS, by * KNIGHT)) return true
        if (leaperHits(square, KING_OFFSETS, by * KING)) return true
        return rayHits(square, BISHOP_OFFSETS, by * BISHOP, by * QUEEN) ||
            rayHits(square, ROOK_OFFSETS, by * ROOK, by * QUEEN)
    }

    /** The piece on [square], or 0 for empty *and* for anything off the board. */
    private fun pieceAt(square: Int): Int = if (offBoard(square)) 0 else board[square]

    private fun leaperHits(square: Int, offsets: IntArray, piece: Int): Boolean {
        for (offset in offsets) {
            if (pieceAt(square + offset) == piece) return true
        }
        return false
    }

    private fun rayHits(square: Int, offsets: IntArray, slider: Int, queen: Int): Boolean {
        for (offset in offsets) {
            var s = square + offset
            while (!offBoard(s) && board[s] == 0) s += offset
            if (!offBoard(s) && (board[s] == slider || board[s] == queen)) return true
        }
        return false
    }

    fun make(move: Int): ChessUndo {
        val from = moveFrom(move)
        val to = moveTo(move)
        val promotion = movePromotion(move)
        val flag = moveFlag(move)
        val pawnBack = if (side == 1) -RANK_STRIDE else RANK_STRIDE
        val captured = if (flag == FLAG_EN_PASSANT) board[to + pawnBack] else board[to]
        val undo = ChessUndo(captured, castle, enPassant)
        val piece = board[from]

        board[to] = if (promotion != 0) side * promotion else piece
        board[from] = 0
        if (flag == FLAG_EN_PASSANT) board[to + pawnBack] = 0
        if (flag == FLAG_CASTLE) moveCastlingRook(to, undoing = false)
        if (abs(piece) == KING) {
            if (side == 1) whiteKing = to else blackKing = to
        }

        castle = castle and CASTLE_MASK[from] and CASTLE_MASK[to]
        enPassant = if (flag == FLAG_DOUBLE_PUSH) (from + to) / 2 else -1
        side = -side
        return undo
    }

    fun unmake(move: Int, undo: ChessUndo) {
        side = -side
        val from = moveFrom(move)
        val to = moveTo(move)
        val flag = moveFlag(move)
        val piece = board[to]

        board[from] = if (movePromotion(move) != 0) side * PAWN else piece
        if (flag == FLAG_EN_PASSANT) {
            board[to] = 0
            board[to + if (side == 1) -RANK_STRIDE else RANK_STRIDE] = undo.captured
        } else {
            board[to] = undo.captured
        }
        if (flag == FLAG_CASTLE) moveCastlingRook(to, undoing = true)
        if (abs(piece) == KING) {
            if (side == 1) whiteKing = from else blackKing = from
        }
        castle = undo.castle
        enPassant = undo.enPassant
    }

    /** The rook half of a castle. The king's destination [to] identifies which of the four it is. */
    private fun moveCastlingRook(to: Int, undoing: Boolean) {
        val rookFrom: Int
        val rookTo: Int
        when (to) {
            G1 -> { rookFrom = H1; rookTo = F1 }
            C1 -> { rookFrom = A1; rookTo = D1 }
            G8 -> { rookFrom = H8; rookTo = F8 }
            else -> { rookFrom = A8; rookTo = D8 }
        }
        val source = if (undoing) rookTo else rookFrom
        val target = if (undoing) rookFrom else rookTo
        board[target] = board[source]
        board[source] = 0
    }

    /** Every legal move for the side to move. Pseudo-legal generation, then a king-safety filter. */
    fun legalMoves(): MutableList<Int> {
        val pseudo = ArrayList<Int>(MOVE_LIST_HINT)
        for (square in 0 until BOARD_SIZE) {
            val piece = board[square]
            if (offBoard(square) || piece == 0 || (piece > 0) != (side > 0)) continue
            when (abs(piece)) {
                PAWN -> pawnMoves(square, pseudo)
                KNIGHT -> stepMoves(square, KNIGHT_OFFSETS, pseudo)
                KING -> stepMoves(square, KING_OFFSETS, pseudo)
                BISHOP -> slideMoves(square, BISHOP_OFFSETS, pseudo)
                ROOK -> slideMoves(square, ROOK_OFFSETS, pseudo)
                else -> slideMoves(square, KING_OFFSETS, pseudo)
            }
        }
        castleMoves(pseudo)

        val legal = ArrayList<Int>(pseudo.size)
        for (move in pseudo) {
            val mover = side
            val undo = make(move)
            if (!attacked(kingSquare(mover), -mover)) legal += move
            unmake(move, undo)
        }
        return legal
    }

    private fun pawnMoves(square: Int, out: MutableList<Int>) {
        val forward = if (side == 1) RANK_STRIDE else -RANK_STRIDE
        val homeRank = if (side == 1) 1 else 6
        val lastRank = if (side == 1) 7 else 0
        val one = square + forward
        if (!offBoard(one) && board[one] == 0) {
            pushOrPromote(square, one, lastRank, out)
            val two = square + forward * 2
            if ((one shr 4) != lastRank && (square shr 4) == homeRank && board[two] == 0) {
                out += chessMove(square, two, 0, FLAG_DOUBLE_PUSH)
            }
        }
        for (diagonal in intArrayOf(forward + 1, forward - 1)) {
            val target = square + diagonal
            if (offBoard(target)) continue
            val occupant = board[target]
            when {
                occupant != 0 && (occupant > 0) != (side > 0) ->
                    pushOrPromote(square, target, lastRank, out)
                occupant == 0 && target == enPassant ->
                    out += chessMove(square, target, 0, FLAG_EN_PASSANT)
            }
        }
    }

    private fun pushOrPromote(from: Int, to: Int, lastRank: Int, out: MutableList<Int>) {
        if ((to shr 4) == lastRank) {
            for (promotion in PROMOTION_PIECES) out += chessMove(from, to, promotion, 0)
        } else {
            out += chessMove(from, to, 0, 0)
        }
    }

    private fun stepMoves(square: Int, offsets: IntArray, out: MutableList<Int>) {
        for (offset in offsets) {
            val target = square + offset
            if (offBoard(target)) continue
            val occupant = board[target]
            if (occupant == 0 || (occupant > 0) != (side > 0)) out += chessMove(square, target, 0, 0)
        }
    }

    private fun slideMoves(square: Int, offsets: IntArray, out: MutableList<Int>) {
        for (offset in offsets) {
            var target = square + offset
            while (!offBoard(target) && board[target] == 0) {
                out += chessMove(square, target, 0, 0)
                target += offset
            }
            if (!offBoard(target) && (board[target] > 0) != (side > 0)) {
                out += chessMove(square, target, 0, 0)
            }
        }
    }

    /**
     * Castling, with all three legality conditions: the right survives, the path is clear, and the
     * king neither starts in, passes through, nor lands in check. The landing square is covered by
     * the ordinary king-safety filter in [legalMoves]; the other two are only checkable here.
     */
    private fun castleMoves(out: MutableList<Int>) {
        if (side == 1) {
            if (canCastle(CASTLE_WHITE_KING, WHITE_KING_SIDE_EMPTY, E1, F1)) {
                out += chessMove(E1, G1, 0, FLAG_CASTLE)
            }
            if (canCastle(CASTLE_WHITE_QUEEN, WHITE_QUEEN_SIDE_EMPTY, E1, D1)) {
                out += chessMove(E1, C1, 0, FLAG_CASTLE)
            }
        } else {
            if (canCastle(CASTLE_BLACK_KING, BLACK_KING_SIDE_EMPTY, E8, F8)) {
                out += chessMove(E8, G8, 0, FLAG_CASTLE)
            }
            if (canCastle(CASTLE_BLACK_QUEEN, BLACK_QUEEN_SIDE_EMPTY, E8, D8)) {
                out += chessMove(E8, C8, 0, FLAG_CASTLE)
            }
        }
    }

    private fun canCastle(right: Int, empties: IntArray, king: Int, passesThrough: Int): Boolean {
        if (castle and right == 0) return false
        for (square in empties) {
            if (board[square] != 0) return false
        }
        val them = -side
        return !attacked(king, them) && !attacked(passesThrough, them)
    }

    private companion object {
        /** Enough room for a busy middlegame without a resize; a list that grows is still correct. */
        const val MOVE_LIST_HINT = 48
    }
}

/** Legal move counting to [depth]. The only honest way to say a move generator is correct. */
internal fun chessPerft(position: ChessPosition, depth: Int): Long {
    if (depth == 0) return 1L
    var total = 0L
    for (move in position.legalMoves()) {
        val undo = position.make(move)
        total += chessPerft(position, depth - 1)
        position.unmake(move, undo)
    }
    return total
}

// -------------------------------------------------------------------------------------------
// Evaluation and search
// -------------------------------------------------------------------------------------------

/** Centipawns, indexed by piece type. A king is priceless, so it is scored at zero. */
private val PIECE_VALUE = intArrayOf(0, 100, 320, 330, 500, 900, 0)

/** Centrality by file or rank offset, and how much each piece is pulled toward it. */
private val CENTRE = intArrayOf(0, 1, 2, 3, 3, 2, 1, 0)
private val CENTRE_WEIGHT = intArrayOf(0, 2, 4, 3, 0, 0, 0)

private const val MATE_SCORE = 100_000
private const val NOISE_SPAN_CENTIPAWNS = 140f

/**
 * How many edges the tree keeps. Beyond this the search still runs — the node count in the readout
 * stays honest — but the drawing stops growing, because a canvas cannot show a hundred thousand
 * lines and a truncated picture is better than a stalled frame.
 */
private const val MAX_TREE_EDGES = 3000

private const val CAPTURE_BONUS = 100
private const val PAWN_TAKES_BONUS = 10
private const val PROMOTION_BONUS = 90

/** Material plus centrality, from White's point of view. */
private fun evaluate(position: ChessPosition): Int {
    var score = 0
    for (square in 0 until BOARD_SIZE) {
        val piece = position.board[square]
        if (offBoard(square) || piece == 0) continue
        val type = abs(piece)
        val value =
            PIECE_VALUE[type] + CENTRE_WEIGHT[type] * (CENTRE[square and 7] + CENTRE[square shr 4])
        score += if (piece > 0) value else -value
    }
    return score
}

/** mulberry32, the same generator the React labs use — 32-bit, seedable, no dependency. */
internal class Mulberry32(seed: Int) {
    private var state = seed

    fun next(): Float {
        state += MULBERRY_STEP
        var t = state
        t = (t xor (t ushr XORSHIFT_A)) * (1 or t)
        t = t xor (t + (t xor (t ushr XORSHIFT_B)) * (ODD_MIX_MASK or t))
        return (t xor (t ushr XORSHIFT_C)).toUInt().toFloat() / TWO_POW_32
    }

    private companion object {
        const val MULBERRY_STEP = 0x6d2b79f5
        const val TWO_POW_32 = 4294967296f

        /**
         * The three xorshift distances of mulberry32's mixing stages, verbatim from the reference.
         * They are tuned as a set against the step and the mask below; change one and this is a
         * different generator, which moves every seeded thing on the bench at once.
         */
        const val XORSHIFT_A = 15
        const val XORSHIFT_B = 7
        const val XORSHIFT_C = 14

        /**
         * OR-ed into a multiplicand to force its low bits set. An odd multiplier is invertible
         * modulo 2^32, which is what stops the mix from collapsing information on the way through.
         */
        const val ODD_MIX_MASK = 61
    }
}

/** One parent-to-child link. Ids are handed out in visit order, so `from` is always less than `to`. */
internal class ChessTreeEdge(val from: Int, val to: Int, val move: Int, val depth: Int)

internal class ChessSearchResult(
    val move: Int,
    val score: Int,
    val nodes: Int,
    val edges: List<ChessTreeEdge>,
    val truncated: Boolean,
) {
    /**
     * Coordinate notation ("e2a6"), not SAN.
     *
     * ponytail: SAN needs check and mate suffixes plus full from-square disambiguation, which is a
     * second pass over the move list for every move named. The lab names exactly one move, and a
     * reader who can follow a search tree can read `e2a6`. Documented rather than half-built.
     */
    val moveText: String
        get() =
            chessSquareName(moveFrom(move)) + chessSquareName(moveTo(move)) +
                when (movePromotion(move)) {
                    QUEEN -> "q"
                    ROOK -> "r"
                    BISHOP -> "b"
                    KNIGHT -> "n"
                    else -> ""
                }

    val maxPly: Int get() = edges.maxOfOrNull { it.depth + 1 } ?: 0
}

private class SearchContext(val rng: Mulberry32, val noise: Float) {
    var nodes: Int = 0
    var lastId: Int = 0
    val edges = ArrayList<ChessTreeEdge>(MAX_TREE_EDGES)
    var truncated = false
}

/**
 * Captures first, promotions next. Ordering never changes which move is chosen — it only changes how
 * much alpha-beta can prune, which is why the cheapest useful ordering is the right one.
 *
 * ponytail: no MVV-LVA, no killer moves, no transposition table. Measured node counts run from a
 * couple of hundred at two ply to about twelve thousand at four; nothing is waiting on this.
 */
private fun rankMove(position: ChessPosition, move: Int): Int {
    var score = 0
    if (position.board[moveTo(move)] != 0) {
        val attacker = abs(position.board[moveFrom(move)])
        score += CAPTURE_BONUS + if (attacker == PAWN) PAWN_TAKES_BONUS else 0
    }
    if (movePromotion(move) != 0) score += PROMOTION_BONUS
    return score
}

private fun negamax(
    position: ChessPosition,
    depth: Int,
    ply: Int,
    alphaIn: Int,
    beta: Int,
    ctx: SearchContext,
    nodeId: Int,
): Int {
    ctx.nodes++
    val moves = position.legalMoves()
    // Terminal before the horizon: a mate found at depth 0 still has to score as a mate, and a
    // shallower mate has to beat a deeper one, which is what subtracting `ply` buys.
    if (moves.isEmpty()) {
        val inCheck = position.attacked(position.kingSquare(position.side), -position.side)
        return if (inCheck) -(MATE_SCORE - ply) else 0
    }
    if (depth == 0) return leafScore(position, ctx)

    moves.sortByDescending { rankMove(position, it) }
    var alpha = alphaIn
    var best = Int.MIN_VALUE
    for (move in moves) {
        val childId = ++ctx.lastId
        // Recorded *before* the recursion, so the edge list is in pre-order: truncation then drops
        // leaves rather than the parents that anchor them, and every edge's `from` already exists.
        if (ctx.edges.size < MAX_TREE_EDGES) {
            ctx.edges += ChessTreeEdge(nodeId, childId, move, ply)
        } else {
            ctx.truncated = true
        }
        val undo = position.make(move)
        val score = -negamax(position, depth - 1, ply + 1, -beta, -alpha, ctx, childId)
        position.unmake(move, undo)
        if (score > best) best = score
        if (best > alpha) alpha = best
        if (alpha >= beta) break
    }
    return best
}

/** The evaluation at the horizon, from the moving side's point of view, plus the preset's jitter. */
private fun leafScore(position: ChessPosition, ctx: SearchContext): Int {
    val own = if (position.side == 1) evaluate(position) else -evaluate(position)
    if (ctx.noise <= 0f) return own
    return own + ((ctx.rng.next() * 2f - 1f) * ctx.noise * (NOISE_SPAN_CENTIPAWNS / 2f)).toInt()
}

/**
 * A fixed-depth alpha-beta search. Pure in (fen, depth, noise, seed) — same arguments, same tree,
 * every time, which is what lets the instrument treat the reveal as a function of elapsed seconds
 * and what lets [chessEngineSelfCheck] assert on the result at all.
 *
 * The React original also takes a wall-clock budget and stops iterative deepening when it runs out,
 * so a hurried bot searches shallower. That is dropped here, deliberately: a search whose shape
 * depends on how fast the machine is cannot be a function of elapsed seconds, cannot be asserted,
 * and would redraw a different tree on every resize. The clock-pressure argument it was making is
 * the one the Clock Burn instrument makes directly, from measured games rather than from a model.
 */
internal fun chessSearch(fen: String, depth: Int, noise: Float, seed: Int): ChessSearchResult {
    val position = ChessPosition(fen)
    val roots = position.legalMoves()
    if (roots.isEmpty()) return ChessSearchResult(0, 0, 0, emptyList(), false)
    roots.sortByDescending { rankMove(position, it) }

    val ctx = SearchContext(Mulberry32(seed), noise)
    var bestScore = Int.MIN_VALUE
    var bestMove = roots[0]
    var alpha = Int.MIN_VALUE + 1
    for (move in roots) {
        val childId = ++ctx.lastId
        ctx.edges += ChessTreeEdge(0, childId, move, 0)
        val undo = position.make(move)
        val value = -negamax(position, depth - 1, 1, Int.MIN_VALUE + 1, -alpha, ctx, childId)
        position.unmake(move, undo)
        if (value > bestScore) {
            bestScore = value
            bestMove = move
        }
        if (bestScore > alpha) alpha = bestScore
    }
    return ChessSearchResult(bestMove, bestScore, ctx.nodes, ctx.edges, ctx.truncated)
}

// -------------------------------------------------------------------------------------------
// Laying the tree out
// -------------------------------------------------------------------------------------------

/** A placed node. Parallel arrays rather than objects: the layout is read once per drawn edge. */
internal class ChessTreeLayout(val x: FloatArray, val y: FloatArray, val inChosenLine: BooleanArray)

private const val TREE_MARGIN = 14f
private const val TREE_ROOT_LIFT = 22f
private const val TREE_FAN = 0.95f
private const val TREE_FAN_DECAY = 0.9f
private const val TREE_BRANCH = 0.34f
private const val TREE_BRANCH_DECAY = 0.62f

/**
 * Bottom-up radial layout over the pre-order edge list.
 *
 * Pre-order is what makes one forward pass enough: a parent is always numbered, and therefore
 * already placed, before any of its children. The React version needs two hash maps for the same
 * job because it does not lean on that ordering; here the node id *is* the array index.
 */
internal fun layoutChessTree(
    result: ChessSearchResult,
    widthPx: Float,
    heightPx: Float,
): ChessTreeLayout {
    val count = (result.edges.maxOfOrNull { it.to } ?: 0) + 1
    val x = FloatArray(count)
    val y = FloatArray(count)
    val angle = FloatArray(count)
    val depth = IntArray(count)
    val chosen = BooleanArray(count)

    val width = maxOf(widthPx, TREE_MARGIN * 4f)
    val height = maxOf(heightPx, TREE_MARGIN * 4f)
    x[0] = width / 2f
    y[0] = height - TREE_ROOT_LIFT
    angle[0] = -PI.toFloat() / 2f

    // How many children each parent has, so a fan can be centred without a second traversal.
    val siblings = IntArray(count)
    for (edge in result.edges) siblings[edge.from]++
    val placed = IntArray(count)

    val usable = height - TREE_MARGIN * 2f
    for (edge in result.edges) {
        val parent = edge.from
        val index = placed[parent]
        placed[parent] = index + 1
        // The fan narrows with depth, or a deep subtree wraps back over its own parent.
        val spread = (PI.toFloat() * TREE_FAN) / (1f + depth[parent] * TREE_FAN_DECAY)
        val a = angle[parent] + spread * ((index + 0.5f) / siblings[parent] - 0.5f)
        val length = usable * TREE_BRANCH * TREE_BRANCH_DECAY.pow(depth[parent])
        val child = edge.to
        angle[child] = a
        depth[child] = depth[parent] + 1
        x[child] = (x[parent] + cos(a) * length).coerceIn(TREE_MARGIN, width - TREE_MARGIN)
        y[child] = (y[parent] + sin(a) * length).coerceIn(TREE_MARGIN, height - TREE_MARGIN)
        // The played move's whole subtree, again in one pass and again because of pre-order.
        chosen[child] = (parent == 0 && edge.move == result.move) || chosen[parent]
    }
    return ChessTreeLayout(x, y, chosen)
}

// -------------------------------------------------------------------------------------------
// What the instrument searches
// -------------------------------------------------------------------------------------------

/**
 * The positions the instrument searches: real positions out of his own games, straight from the
 * generated corpus, each carrying how that game ended, at what time control, and the rating he held
 * that day. The search runs over his board, not a textbook diagram.
 *
 * The React lab searches one position instead — the lichess daily puzzle in `chess.puzzle`, which
 * `gen-kotlin-data.mjs` does not emit into Kotlin. `chessPositions` does come across, and thirty of
 * his own positions is a better subject than one of anybody's.
 *
 * The filter is not tidying. Exactly half the corpus entries are *final* positions: checkmates the
 * games actually ended on, which the chess room shows in order to ask how a game finished. A search
 * has no answer where there are no legal moves, so those are skipped rather than drawn as an empty
 * canvas. That split was found by [chessEngineSelfCheck] failing on position 9 — a real mate from a
 * game he lost on 2023-04-24 — and then confirmed against `chess.js` across every entry, which is
 * the argument for a check that runs the whole corpus instead of a sample of it.
 */
internal val chessLabPositions: List<ChessQuizPosition> =
    chessPositions.filter { ChessPosition(it.fen).legalMoves().isNotEmpty() }

/** Corpus entries that are already over: checkmate or stalemate, nothing left to search. */
internal val chessTerminalPositions: Int = chessPositions.size - chessLabPositions.size

/** One seed for the whole instrument: a search that reproduces is a search that can be checked. */
internal const val CHESS_SEARCH_SEED: Int = 20260724

/**
 * How long the finished search takes to draw itself in, in seconds.
 *
 * The growth is a *replay*: [chessSearch] is synchronous and returns the whole edge list at once, so
 * nothing here is live progress and no progress bar is drawn from it. Making the reveal a function
 * of elapsed seconds keeps this instrument on the same clock as every other one, and makes the
 * reduced-motion still frame a completed tree with no second code path.
 */
internal const val CHESS_REPLAY_SECONDS: Float = 1.6f

/** How much of [ChessSearchResult.edges] exists yet at [seconds]. */
internal fun chessRevealedAt(result: ChessSearchResult, seconds: Float): Int {
    val ratio = (seconds / CHESS_REPLAY_SECONDS).coerceIn(0f, 1f)
    return (result.edges.size * ratio).toInt().coerceIn(0, result.edges.size)
}

/**
 * The two depth settings.
 *
 * The React lab names its presets after two ratings the owner actually held (1078 in 2019, 1425 in
 * 2026) and pairs each with a search depth and a move-selection noise term. Only the later rating is
 * carried into this port's generated corpus (`chess.platforms`, the chess.com blitz peak), so the
 * tabs are labelled by what they mechanically are — search depth — rather than by a number this
 * build cannot show its working for. The noise term survives: it is what makes the shallow setting
 * settle for the second-best move often enough to feel like a person.
 */
internal class ChessPreset(val label: String, val depth: Int, val noise: Float, val note: String)

internal val chessSearchPresets: List<ChessPreset> =
    listOf(
        ChessPreset(
            label = "2 ply",
            depth = 2,
            noise = 0.62f,
            note = "one move ahead and one reply, and it takes the second-best line often",
        ),
        ChessPreset(
            label = "4 ply",
            depth = 4,
            noise = 0.16f,
            note = "a full move deeper and it second-guesses itself far less",
        ),
    )

// -------------------------------------------------------------------------------------------
// Self-check
// -------------------------------------------------------------------------------------------

/** The initial position. Not corpus data — it is the rules. */
internal const val CHESS_START_FEN: String = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

/**
 * "Kiwipete", the standard move-generator test position: castling available to both sides, a pinned
 * knight, an en-passant possibility and several hanging pieces, chosen decades ago precisely because
 * a generator that is wrong anywhere is usually wrong here. It is never drawn — it exists so the
 * generator has an oracle with published totals.
 */
internal const val CHESS_KIWIPETE_FEN: String =
    "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1"

/** A rook-and-pawn endgame that catches en-passant and promotion bugs the other two miss. */
private const val CHESS_ENDGAME_FEN: String = "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1"

/**
 * Legal move sequences of length four from [CHESS_KIWIPETE_FEN] — the published perft(4) total, and so
 * the size of the tree a plain minimax has to walk to search four ply. Alpha-beta over the same
 * position visits about twelve thousand nodes of it. That ratio is the claim the instrument makes,
 * measured rather than asserted, and [chessEngineSelfCheck] holds it to it.
 */
internal const val CHESS_KIWIPETE_FULL_TREE: Long = 4_085_603L

/** Published perft totals, by position. Wrong rules produce wrong totals, by a specific amount. */
private val PERFT_CASES =
    listOf(
        Triple("start", CHESS_START_FEN, longArrayOf(20, 400, 8902, 197281)),
        Triple("kiwipete", CHESS_KIWIPETE_FEN, longArrayOf(48, 2039, 97862)),
        Triple("endgame", CHESS_ENDGAME_FEN, longArrayOf(14, 191, 2812, 43238)),
    )

/** The nominal canvas the layout is checked against. */
private const val CHECK_WIDTH = 640f
private const val CHECK_HEIGHT = 340f

/** Alpha-beta should visit well under a hundredth of the legal tree at the same depth. */
private const val PRUNING_FACTOR = 100

/**
 * ponytail: one runnable check instead of a test module, called from [labsSelfCheck].
 *
 * Perft is not a smoke test, it is an oracle: these totals are published for all three positions and
 * every one of them is wrong by a specific amount if a specific rule is wrong. Nothing else in this
 * port has a check this sharp.
 */
internal fun chessEngineSelfCheck() {
    PERFT_CASES.forEach { (name, fen, counts) ->
        counts.forEachIndexed { i, want ->
            val got = chessPerft(ChessPosition(fen), i + 1)
            check(got == want) { "perft($name, ${i + 1}) = $got, published total is $want" }
        }
    }

    // The search is a pure function of its arguments, which every claim below depends on.
    val a = chessSearch(CHESS_KIWIPETE_FEN, depth = 2, noise = 0.62f, seed = CHESS_SEARCH_SEED)
    val b = chessSearch(CHESS_KIWIPETE_FEN, depth = 2, noise = 0.62f, seed = CHESS_SEARCH_SEED)
    check(a.nodes == b.nodes && a.move == b.move && a.edges.size == b.edges.size) {
        "same arguments must reproduce the same search"
    }

    // Half the corpus is final positions, and the searchable half has to stay a real bench. If the
    // generator ever stops finding moves this collapses to zero, which is the failure worth catching.
    check(chessLabPositions.size >= chessPositions.size / 3) {
        "only ${chessLabPositions.size} of ${chessPositions.size} corpus positions are searchable — " +
            "either the corpus changed shape or the move generator stopped finding moves"
    }
    check(chessTerminalPositions > 0) { "the corpus is expected to carry games that ended in mate" }
    chessSearchPresets.forEach { preset ->
        chessLabPositions.forEachIndexed { index, entry -> checkOnePosition(preset, index, entry) }
    }

    // The whole point of the depth control: four ply is a different search, not a bigger picture.
    val shallow = chessSearch(CHESS_KIWIPETE_FEN, depth = 2, noise = 0f, seed = CHESS_SEARCH_SEED)
    val deep = chessSearch(CHESS_KIWIPETE_FEN, depth = 4, noise = 0f, seed = CHESS_SEARCH_SEED)
    check(deep.nodes > shallow.nodes * PRUNING_FACTOR / 10) {
        "four ply should cost far more than two, was ${deep.nodes} against ${shallow.nodes}"
    }
    // And the reason the deep search is affordable at all: it visits a fraction of the legal tree.
    check(deep.nodes < CHESS_KIWIPETE_FULL_TREE / PRUNING_FACTOR) {
        "alpha-beta should prune away most of the tree, visited ${deep.nodes}"
    }
}

/** One corpus position, searched, laid out and revealed. Split out to keep the check readable. */
private fun checkOnePosition(preset: ChessPreset, index: Int, entry: ChessQuizPosition) {
    val where = "${preset.label} / position $index (${entry.at})"
    val run = chessSearch(entry.fen, preset.depth, preset.noise, CHESS_SEARCH_SEED + index)
    check(run.edges.isNotEmpty()) { "$where: the search recorded no tree" }
    check(run.nodes > 0) { "$where: the search visited no nodes" }
    check(run.maxPly in 1..preset.depth) { "$where: tree is ${run.maxPly} ply, over ${preset.depth}" }
    // Pre-order is the property the layout and the chosen-line walk both rely on.
    run.edges.forEach { check(it.from < it.to) { "$where: an edge points backwards" } }
    check(run.edges.any { it.from == 0 && it.move == run.move }) {
        "$where: the played move is not in the tree"
    }

    val layout = layoutChessTree(run, CHECK_WIDTH, CHECK_HEIGHT)
    check(layout.x.size > run.edges.count { it.from == 0 }) { "$where: a node got no position" }
    check(layout.inChosenLine.count { it } >= 1) { "$where: the played line must be highlighted" }
    check(!layout.inChosenLine[0]) { "$where: the root belongs to no one line" }
    layout.x.forEachIndexed { i, px ->
        check(px in 0f..CHECK_WIDTH && layout.y[i] in 0f..CHECK_HEIGHT) { "$where: node $i is off-canvas" }
    }
    // The reveal spans the whole tree and starts empty, like every other instrument's clock.
    check(chessRevealedAt(run, 0f) == 0) { "$where: the replay starts before the first edge" }
    check(chessRevealedAt(run, CHESS_REPLAY_SECONDS) == run.edges.size) { "$where: the replay finishes whole" }
    check(chessRevealedAt(run, LabStillSeconds) == run.edges.size) {
        "$where: the still frame should be a finished search"
    }
}
