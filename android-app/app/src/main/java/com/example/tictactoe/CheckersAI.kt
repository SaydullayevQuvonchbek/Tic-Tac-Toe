package com.example.tictactoe

import android.os.SystemClock
import kotlin.math.max
import kotlin.math.min

object CheckersAI {

    private const val PAWN_VALUE = 100
    private const val KING_VALUE = 340

    /**
     * NOTE: this is always called from a background thread ([AiThinker]).
     * EASY  = shallow (depth 3).
     * MED   = depth 6.
     * HARD  = iterative deepening up to 12 with a ~1.6s time budget.
     */
    fun findBestMove(
        logic: CheckersLogic,
        aiPlayer: Int = 2,
        difficulty: BotDifficulty = BotDifficulty.MEDIUM
    ): CheckersLogic.Move? {
        val rootMoves = logic.getAllValidMovesForPlayer(aiPlayer)
        if (rootMoves.isEmpty()) return null
        if (rootMoves.size == 1) return rootMoves[0]

        val ordered = orderMoves(logic, rootMoves, aiPlayer)

        return when (difficulty) {
            BotDifficulty.EASY -> searchAtDepth(logic, ordered, aiPlayer, 3, Long.MAX_VALUE) ?: ordered.first()
            BotDifficulty.MEDIUM -> searchAtDepth(logic, ordered, aiPlayer, 6, Long.MAX_VALUE) ?: ordered.first()
            BotDifficulty.HARD -> {
                val deadline = SystemClock.elapsedRealtime() + 1600L
                var best: CheckersLogic.Move? = ordered.first()
                var depth = 2
                while (depth <= 12 && SystemClock.elapsedRealtime() < deadline) {
                    val m = searchAtDepth(logic, ordered, aiPlayer, depth, deadline) ?: break
                    best = m
                    depth++
                }
                best
            }
        }
    }

    private fun searchAtDepth(
        logic: CheckersLogic,
        moves: List<CheckersLogic.Move>,
        aiPlayer: Int,
        depth: Int,
        deadline: Long
    ): CheckersLogic.Move? {
        var bestMove = moves.first()
        var alpha = -1_000_000
        val beta = 1_000_000

        for (move in moves) {
            if (SystemClock.elapsedRealtime() >= deadline) return null
            val cloned = logic.copy()
            cloned.makeMove(move.fromR, move.fromC, move.toR, move.toC)

            val score = if (cloned.activeJumpPiece != null && cloned.currentPlayer == aiPlayer) {
                minimax(cloned, depth, alpha, beta, true, aiPlayer, deadline)
            } else {
                minimax(cloned, depth - 1, alpha, beta, false, aiPlayer, deadline)
            }

            if (score > alpha) {
                alpha = score
                bestMove = move
            }
        }
        return bestMove
    }

    private fun minimax(
        logic: CheckersLogic,
        depth: Int,
        alphaIn: Int,
        betaIn: Int,
        isMaximizing: Boolean,
        aiPlayer: Int,
        deadline: Long
    ): Int {
        if (depth <= 0 || logic.isGameOver || SystemClock.elapsedRealtime() >= deadline) {
            return evaluateBoard(logic, aiPlayer)
        }

        var alpha = alphaIn
        var beta = betaIn
        val opponent = if (aiPlayer == 1) 2 else 1
        val currentPlayer = if (isMaximizing) aiPlayer else opponent
        val moves = logic.getAllValidMovesForPlayer(currentPlayer)
        if (moves.isEmpty()) return if (isMaximizing) -500_000 + depth else 500_000 - depth

        val ordered = orderMoves(logic, moves, currentPlayer)

        if (isMaximizing) {
            var best = -1_000_000
            for (move in ordered) {
                val c = logic.copy()
                c.makeMove(move.fromR, move.fromC, move.toR, move.toC)
                val eval = if (c.activeJumpPiece != null && c.currentPlayer == aiPlayer)
                    minimax(c, depth, alpha, beta, true, aiPlayer, deadline)
                else minimax(c, depth - 1, alpha, beta, false, aiPlayer, deadline)
                best = max(best, eval)
                alpha = max(alpha, eval)
                if (beta <= alpha) break
            }
            return best
        } else {
            var best = 1_000_000
            for (move in ordered) {
                val c = logic.copy()
                c.makeMove(move.fromR, move.fromC, move.toR, move.toC)
                val eval = if (c.activeJumpPiece != null && c.currentPlayer == opponent)
                    minimax(c, depth, alpha, beta, false, aiPlayer, deadline)
                else minimax(c, depth - 1, alpha, beta, true, aiPlayer, deadline)
                best = min(best, eval)
                beta = min(beta, eval)
                if (beta <= alpha) break
            }
            return best
        }
    }

    private fun orderMoves(
        logic: CheckersLogic,
        moves: List<CheckersLogic.Move>,
        player: Int
    ): List<CheckersLogic.Move> = moves.sortedByDescending { move ->
        var p = 0
        if (move.isJump) p += 1000
        val piece = logic.board[move.fromR][move.fromC]
        if (logic.isKing(piece)) p += 40
        val promoRow = if (player == 2) logic.size - 1 else 0
        if (!logic.isKing(piece) && move.toR == promoRow) p += 500
        p
    }

    private fun evaluateBoard(logic: CheckersLogic, aiPlayer: Int): Int {
        val s = logic.size
        var aiScore = 0
        var oppScore = 0

        for (r in 0 until s) {
            for (c in 0 until s) {
                val piece = logic.board[r][c]
                if (piece == CheckersLogic.EMPTY) continue

                val isAiPiece = (aiPlayer == 1 && logic.isP1(piece)) || (aiPlayer == 2 && logic.isP2(piece))
                val isKing = logic.isKing(piece)
                var v = if (isKing) KING_VALUE else PAWN_VALUE

                if (!isKing) {
                    val advance = if (logic.isP1(piece)) (s - 1 - r) else r
                    v += advance * 5
                    if (logic.isP1(piece) && r == s - 1) v += 18
                    if (logic.isP2(piece) && r == 0) v += 18
                }
                if (r in 2..(s - 3) && c in 2..(s - 3)) v += 10
                if (c == 0 || c == s - 1) v += 5

                if (isAiPiece) aiScore += v else oppScore += v
            }
        }

        val oppPlayer = if (aiPlayer == 1) 2 else 1
        aiScore += logic.getAllValidMovesForPlayer(aiPlayer).size * 3
        oppScore += logic.getAllValidMovesForPlayer(oppPlayer).size * 3

        return aiScore - oppScore
    }
}
