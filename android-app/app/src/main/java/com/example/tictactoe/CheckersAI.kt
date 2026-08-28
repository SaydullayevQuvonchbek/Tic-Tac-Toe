package com.example.tictactoe

import kotlin.math.max
import kotlin.math.min

object CheckersAI {

    private const val PAWN_VALUE = 100
    private const val KING_VALUE = 350

    fun findBestMove(logic: CheckersLogic, aiPlayer: Int = 2, maxDepth: Int = 5): CheckersLogic.Move? {
        val validMoves = logic.getAllValidMovesForPlayer(aiPlayer)
        if (validMoves.isEmpty()) return null
        if (validMoves.size == 1) return validMoves[0]

        // Sort moves: jumps first, then promotion moves
        val sortedMoves = validMoves.sortedByDescending { move ->
            var priority = 0
            if (move.isJump) priority += 1000
            val piece = logic.board[move.fromR][move.fromC]
            if (logic.isKing(piece)) priority += 50
            if (!logic.isKing(piece) && ((aiPlayer == 2 && move.toR == logic.size - 1) || (aiPlayer == 1 && move.toR == 0))) {
                priority += 500 // Promotion
            }
            priority
        }

        var bestMove = sortedMoves[0]
        var alpha = -100000
        val beta = 100000

        for (move in sortedMoves) {
            val clonedLogic = cloneLogic(logic)
            clonedLogic.makeMove(move.fromR, move.fromC, move.toR, move.toC)

            // If multi-jump is active, continue same player's search
            val score = if (clonedLogic.activeJumpPiece != null && clonedLogic.currentPlayer == aiPlayer) {
                minimax(clonedLogic, maxDepth, alpha, beta, true, aiPlayer)
            } else {
                minimax(clonedLogic, maxDepth - 1, alpha, beta, false, aiPlayer)
            }

            if (score > alpha) {
                alpha = score
                bestMove = move
            }
        }

        return bestMove
    }

    private fun minimax(logic: CheckersLogic, depth: Int, alphaInput: Int, betaInput: Int, isMaximizing: Boolean, aiPlayer: Int): Int {
        if (depth <= 0 || logic.isGameOver) {
            return evaluateBoard(logic, aiPlayer)
        }

        var alpha = alphaInput
        var beta = betaInput
        val opponent = if (aiPlayer == 1) 2 else 1
        val currentPlayer = if (isMaximizing) aiPlayer else opponent
        val validMoves = logic.getAllValidMovesForPlayer(currentPlayer)

        if (validMoves.isEmpty()) {
            return if (isMaximizing) -50000 else 50000
        }

        if (isMaximizing) {
            var maxEval = -100000
            for (move in validMoves) {
                val cloned = cloneLogic(logic)
                cloned.makeMove(move.fromR, move.fromC, move.toR, move.toC)

                val eval = if (cloned.activeJumpPiece != null && cloned.currentPlayer == aiPlayer) {
                    minimax(cloned, depth, alpha, beta, true, aiPlayer)
                } else {
                    minimax(cloned, depth - 1, alpha, beta, false, aiPlayer)
                }

                maxEval = max(maxEval, eval)
                alpha = max(alpha, eval)
                if (beta <= alpha) break
            }
            return maxEval
        } else {
            var minEval = 100000
            for (move in validMoves) {
                val cloned = cloneLogic(logic)
                cloned.makeMove(move.fromR, move.fromC, move.toR, move.toC)

                val eval = if (cloned.activeJumpPiece != null && cloned.currentPlayer == opponent) {
                    minimax(cloned, depth, alpha, beta, false, aiPlayer)
                } else {
                    minimax(cloned, depth - 1, alpha, beta, true, aiPlayer)
                }

                minEval = min(minEval, eval)
                beta = min(beta, eval)
                if (beta <= alpha) break
            }
            return minEval
        }
    }

    private fun evaluateBoard(logic: CheckersLogic, aiPlayer: Int): Int {
        val s = logic.size
        var aiScore = 0
        var oppScore = 0

        val oppPlayer = if (aiPlayer == 1) 2 else 1

        for (r in 0 until s) {
            for (c in 0 until s) {
                val piece = logic.board[r][c]
                if (piece == CheckersLogic.EMPTY) continue

                val isAiPiece = (aiPlayer == 1 && logic.isP1(piece)) || (aiPlayer == 2 && logic.isP2(piece))
                val isKing = logic.isKing(piece)

                var pieceVal = if (isKing) KING_VALUE else PAWN_VALUE

                // Positional bonuses:
                if (!isKing) {
                    // Advancement bonus
                    val advance = if (logic.isP1(piece)) (s - 1 - r) else r
                    pieceVal += advance * 5

                    // Back rank protection
                    if (logic.isP1(piece) && r == s - 1) pieceVal += 20
                    if (logic.isP2(piece) && r == 0) pieceVal += 20
                }

                // Center control (middle squares are stronger)
                if (r in 2..(s - 3) && c in 2..(s - 3)) {
                    pieceVal += 12
                }

                // Edge safety for normal pieces (pieces on board edges cannot be jumped)
                if (c == 0 || c == s - 1) {
                    pieceVal += 6
                }

                if (isAiPiece) {
                    aiScore += pieceVal
                } else {
                    oppScore += pieceVal
                }
            }
        }

        // Mobility bonus
        val aiMoves = logic.getAllValidMovesForPlayer(aiPlayer).size
        val oppMoves = logic.getAllValidMovesForPlayer(oppPlayer).size
        aiScore += aiMoves * 4
        oppScore += oppMoves * 4

        return aiScore - oppScore
    }

    private fun cloneLogic(original: CheckersLogic): CheckersLogic {
        val clone = CheckersLogic(original.size)
        for (r in 0 until original.size) {
            for (c in 0 until original.size) {
                clone.board[r][c] = original.board[r][c]
            }
        }
        clone.currentPlayer = original.currentPlayer
        clone.isGameOver = original.isGameOver
        clone.winner = original.winner
        clone.activeJumpPiece = original.activeJumpPiece
        return clone
    }
}
