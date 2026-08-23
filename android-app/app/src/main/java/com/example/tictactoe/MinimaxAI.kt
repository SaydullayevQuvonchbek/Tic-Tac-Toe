package com.example.tictactoe

import kotlin.math.max
import kotlin.math.min

class MinimaxAI {

    fun findBestMove(board: Array<Array<String>>, aiPlayer: String, size: Int): Pair<Int, Int>? {
        if (size > 3) {
            // Minimax is too slow for 4x4 or 5x5 without alpha-beta and depth limit.
            // Pick a random empty spot.
            val emptySpots = mutableListOf<Pair<Int, Int>>()
            for (i in 0 until size) {
                for (j in 0 until size) {
                    if (board[i][j] == "") emptySpots.add(Pair(i, j))
                }
            }
            if (emptySpots.isNotEmpty()) {
                return emptySpots.random()
            }
            return null
        }

        var bestVal = -1000
        var bestMove: Pair<Int, Int>? = null
        
        val opponent = if (aiPlayer == "X") "O" else "X"

        for (i in 0 until size) {
            for (j in 0 until size) {
                if (board[i][j] == "") {
                    board[i][j] = aiPlayer
                    val moveVal = minimax(board, 0, false, aiPlayer, opponent, size)
                    board[i][j] = ""

                    if (moveVal > bestVal) {
                        bestMove = Pair(i, j)
                        bestVal = moveVal
                    }
                }
            }
        }
        return bestMove
    }

    private fun minimax(board: Array<Array<String>>, depth: Int, isMax: Boolean, aiPlayer: String, opponent: String, size: Int): Int {
        val score = evaluate(board, aiPlayer, opponent, size)

        if (score == 10) return score - depth
        if (score == -10) return score + depth
        if (!isMovesLeft(board, size)) return 0

        if (isMax) {
            var best = -1000
            for (i in 0 until size) {
                for (j in 0 until size) {
                    if (board[i][j] == "") {
                        board[i][j] = aiPlayer
                        best = max(best, minimax(board, depth + 1, !isMax, aiPlayer, opponent, size))
                        board[i][j] = ""
                    }
                }
            }
            return best
        } else {
            var best = 1000
            for (i in 0 until size) {
                for (j in 0 until size) {
                    if (board[i][j] == "") {
                        board[i][j] = opponent
                        best = min(best, minimax(board, depth + 1, !isMax, aiPlayer, opponent, size))
                        board[i][j] = ""
                    }
                }
            }
            return best
        }
    }

    private fun evaluate(board: Array<Array<String>>, aiPlayer: String, opponent: String, size: Int): Int {
        for (row in 0 until size) {
            if (board[row][0] != "" && board[row][0] == board[row][1] && board[row][1] == board[row][2]) {
                if (board[row][0] == aiPlayer) return +10
                else if (board[row][0] == opponent) return -10
            }
        }
        for (col in 0 until size) {
            if (board[0][col] != "" && board[0][col] == board[1][col] && board[1][col] == board[2][col]) {
                if (board[0][col] == aiPlayer) return +10
                else if (board[0][col] == opponent) return -10
            }
        }
        if (board[0][0] != "" && board[0][0] == board[1][1] && board[1][1] == board[2][2]) {
            if (board[0][0] == aiPlayer) return +10
            else if (board[0][0] == opponent) return -10
        }
        if (board[0][2] != "" && board[0][2] == board[1][1] && board[1][1] == board[2][0]) {
            if (board[0][2] == aiPlayer) return +10
            else if (board[0][2] == opponent) return -10
        }
        return 0
    }

    private fun isMovesLeft(board: Array<Array<String>>, size: Int): Boolean {
        for (i in 0 until size) {
            for (j in 0 until size) {
                if (board[i][j] == "") return true
            }
        }
        return false
    }
}
