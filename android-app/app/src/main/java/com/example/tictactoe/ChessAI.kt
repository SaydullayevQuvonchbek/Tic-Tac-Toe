package com.example.tictactoe

import kotlin.math.max
import kotlin.math.min

object ChessAI {

    private val PAWN_TABLE = arrayOf(
        intArrayOf(0,  0,  0,  0,  0,  0,  0,  0),
        intArrayOf(50, 50, 50, 50, 50, 50, 50, 50),
        intArrayOf(10, 10, 20, 30, 30, 20, 10, 10),
        intArrayOf(5,  5, 10, 25, 25, 10,  5,  5),
        intArrayOf(0,  0,  0, 20, 20,  0,  0,  0),
        intArrayOf(5, -5,-10,  0,  0,-10, -5,  5),
        intArrayOf(5, 10, 10,-20,-20, 10, 10,  5),
        intArrayOf(0,  0,  0,  0,  0,  0,  0,  0)
    )

    private val KNIGHT_TABLE = arrayOf(
        intArrayOf(-50,-40,-30,-30,-30,-30,-40,-50),
        intArrayOf(-40,-20,  0,  0,  0,  0,-20,-40),
        intArrayOf(-30,  0, 10, 15, 15, 10,  0,-30),
        intArrayOf(-30,  5, 15, 20, 20, 15,  5,-30),
        intArrayOf(-30,  0, 15, 20, 20, 15,  0,-30),
        intArrayOf(-30,  5, 10, 15, 15, 10,  5,-30),
        intArrayOf(-40,-20,  0,  5,  5,  0,-20,-40),
        intArrayOf(-50,-40,-30,-30,-30,-30,-40,-50)
    )

    private val BISHOP_TABLE = arrayOf(
        intArrayOf(-20,-10,-10,-10,-10,-10,-10,-20),
        intArrayOf(-10,  0,  0,  0,  0,  0,  0,-10),
        intArrayOf(-10,  0,  5, 10, 10,  5,  0,-10),
        intArrayOf(-10,  5,  5, 10, 10,  5,  5,-10),
        intArrayOf(-10,  0, 10, 10, 10, 10,  0,-10),
        intArrayOf(-10, 10, 10, 10, 10, 10, 10,-10),
        intArrayOf(-10,  5,  0,  0,  0,  0,  5,-10),
        intArrayOf(-20,-10,-10,-10,-10,-10,-10,-20)
    )

    private val ROOK_TABLE = arrayOf(
        intArrayOf(0,  0,  0,  0,  0,  0,  0,  0),
        intArrayOf(5, 10, 10, 10, 10, 10, 10,  5),
        intArrayOf(-5,  0,  0,  0,  0,  0,  0, -5),
        intArrayOf(-5,  0,  0,  0,  0,  0,  0, -5),
        intArrayOf(-5,  0,  0,  0,  0,  0,  0, -5),
        intArrayOf(-5,  0,  0,  0,  0,  0,  0, -5),
        intArrayOf(-5,  0,  0,  0,  0,  0,  0, -5),
        intArrayOf(0,  0,  0,  5,  5,  0,  0,  0)
    )

    private val QUEEN_TABLE = arrayOf(
        intArrayOf(-20,-10,-10, -5, -5,-10,-10,-20),
        intArrayOf(-10,  0,  0,  0,  0,  0,  0,-10),
        intArrayOf(-10,  0,  5,  5,  5,  5,  0,-10),
        intArrayOf(-5,  0,  5,  5,  5,  5,  0, -5),
        intArrayOf(0,  0,  5,  5,  5,  5,  0, -5),
        intArrayOf(-10,  5,  5,  5,  5,  5,  0,-10),
        intArrayOf(-10,  0,  5,  0,  0,  0,  0,-10),
        intArrayOf(-20,-10,-10, -5, -5,-10,-10,-20)
    )

    private val KING_MID_TABLE = arrayOf(
        intArrayOf(-30,-40,-40,-50,-50,-40,-40,-30),
        intArrayOf(-30,-40,-40,-50,-50,-40,-40,-30),
        intArrayOf(-30,-40,-40,-50,-50,-40,-40,-30),
        intArrayOf(-30,-40,-40,-50,-50,-40,-40,-30),
        intArrayOf(-20,-30,-30,-40,-40,-30,-30,-20),
        intArrayOf(-10,-20,-20,-20,-20,-20,-20,-10),
        intArrayOf(20, 20,  0,  0,  0,  0, 20, 20),
        intArrayOf(20, 30, 10,  0,  0, 10, 30, 20)
    )

    private fun evaluatePiecePos(type: PieceType, color: PieceColor, r: Int, c: Int): Int {
        val row = if (color == PieceColor.WHITE) r else 7 - r
        return when (type) {
            PieceType.PAWN -> PAWN_TABLE[row][c]
            PieceType.KNIGHT -> KNIGHT_TABLE[row][c]
            PieceType.BISHOP -> BISHOP_TABLE[row][c]
            PieceType.ROOK -> ROOK_TABLE[row][c]
            PieceType.QUEEN -> QUEEN_TABLE[row][c]
            PieceType.KING -> KING_MID_TABLE[row][c]
        }
    }

    private fun evaluateBoard(logic: ChessLogic, botColor: PieceColor): Int {
        var score = 0
        for (r in 0..7) {
            for (c in 0..7) {
                val piece = logic.board[r][c] ?: continue
                val material = piece.type.value
                val pos = evaluatePiecePos(piece.type, piece.color, r, c)
                val totalVal = material + pos
                if (piece.color == botColor) {
                    score += totalVal
                } else {
                    score -= totalVal
                }
            }
        }
        return score
    }

    fun getBestMove(logic: ChessLogic, depth: Int = 3): ChessMove? {
        val botColor = logic.currentTurn
        val legalMoves = logic.getAllLegalMovesForColor(botColor)
        if (legalMoves.isEmpty()) return null

        var bestMove: ChessMove? = null
        var bestScore = Int.MIN_VALUE

        // Sort moves: Captures first for alpha-beta efficiency
        val sortedMoves = legalMoves.sortedByDescending { it.capturedPiece?.type?.value ?: 0 }

        for (move in sortedMoves) {
            val logicCopy = cloneLogic(logic)
            logicCopy.makeMove(move)

            val score = minimax(logicCopy, depth - 1, Int.MIN_VALUE, Int.MAX_VALUE, false, botColor)
            if (score > bestScore) {
                bestScore = score
                bestMove = move
            }
        }

        return bestMove ?: legalMoves.random()
    }

    private fun minimax(
        logic: ChessLogic,
        depth: Int,
        alpha: Int,
        beta: Int,
        isMaximizing: Boolean,
        botColor: PieceColor
    ): Int {
        if (depth == 0 || logic.isGameOver) {
            if (logic.isGameOver) {
                if (logic.winner == botColor) return 100000 + depth
                if (logic.winner == botColor.opposite()) return -100000 - depth
                return 0 // Draw
            }
            return evaluateBoard(logic, botColor)
        }

        var currentAlpha = alpha
        var currentBeta = beta
        val currentTurn = logic.currentTurn
        val legalMoves = logic.getAllLegalMovesForColor(currentTurn)

        if (legalMoves.isEmpty()) {
            if (logic.isCheck) {
                return if (isMaximizing) -100000 - depth else 100000 + depth
            }
            return 0
        }

        val sortedMoves = legalMoves.sortedByDescending { it.capturedPiece?.type?.value ?: 0 }

        if (isMaximizing) {
            var maxEval = Int.MIN_VALUE
            for (move in sortedMoves) {
                val copy = cloneLogic(logic)
                copy.makeMove(move)
                val eval = minimax(copy, depth - 1, currentAlpha, currentBeta, false, botColor)
                maxEval = max(maxEval, eval)
                currentAlpha = max(currentAlpha, eval)
                if (currentBeta <= currentAlpha) break
            }
            return maxEval
        } else {
            var minEval = Int.MAX_VALUE
            for (move in sortedMoves) {
                val copy = cloneLogic(logic)
                copy.makeMove(move)
                val eval = minimax(copy, depth - 1, currentAlpha, currentBeta, true, botColor)
                minEval = min(minEval, eval)
                currentBeta = min(currentBeta, eval)
                if (currentBeta <= currentAlpha) break
            }
            return minEval
        }
    }

    private fun cloneLogic(original: ChessLogic): ChessLogic {
        val clone = ChessLogic()
        for (r in 0..7) {
            for (c in 0..7) {
                clone.board[r][c] = original.board[r][c]?.copy()
            }
        }
        clone.currentTurn = original.currentTurn
        clone.enPassantTarget = original.enPassantTarget
        clone.isGameOver = original.isGameOver
        clone.winner = original.winner
        clone.isDraw = original.isDraw
        clone.isCheck = original.isCheck
        return clone
    }
}
