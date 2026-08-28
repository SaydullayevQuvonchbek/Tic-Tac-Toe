package com.example.tictactoe

import kotlin.math.max
import kotlin.math.min

class MinimaxAI {

    fun findBestMove(board: Array<Array<String>>, aiPlayer: String, size: Int): Pair<Int, Int>? {
        if (size > 3) {
            val opponent = if (aiPlayer == "X") "O" else "X"
            val winNeeded = if (size == 5) 4 else size

            // 1. Check if AI can win in 1 move
            val winMove = findImmediateWinningMove(board, aiPlayer, size, winNeeded)
            if (winMove != null) return winMove

            // 2. Check if opponent can win in 1 move and block it
            val blockMove = findImmediateWinningMove(board, opponent, size, winNeeded)
            if (blockMove != null) return blockMove

            // 3. Prefer center cells if empty
            val center = size / 2
            if (board[center][center] == "") return Pair(center, center)
            if (board[center - 1][center] == "") return Pair(center - 1, center)

            // 4. Pick random empty spot
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

    private fun findImmediateWinningMove(board: Array<Array<String>>, player: String, size: Int, winNeeded: Int): Pair<Int, Int>? {
        for (r in 0 until size) {
            for (c in 0 until size) {
                if (board[r][c] == "") {
                    board[r][c] = player
                    val isWin = checkWinCondition(board, player, size, winNeeded)
                    board[r][c] = ""
                    if (isWin) return Pair(r, c)
                }
            }
        }
        return null
    }

    private fun checkWinCondition(board: Array<Array<String>>, player: String, size: Int, needed: Int): Boolean {
        // Horizontal
        for (r in 0 until size) {
            for (c in 0..size - needed) {
                var count = 0
                for (k in 0 until needed) {
                    if (board[r][c + k] == player) count++
                }
                if (count == needed) return true
            }
        }
        // Vertical
        for (c in 0 until size) {
            for (r in 0..size - needed) {
                var count = 0
                for (k in 0 until needed) {
                    if (board[r + k][c] == player) count++
                }
                if (count == needed) return true
            }
        }
        // Diagonal down-right
        for (r in 0..size - needed) {
            for (c in 0..size - needed) {
                var count = 0
                for (k in 0 until needed) {
                    if (board[r + k][c + k] == player) count++
                }
                if (count == needed) return true
            }
        }
        // Diagonal up-right
        for (r in needed - 1 until size) {
            for (c in 0..size - needed) {
                var count = 0
                for (k in 0 until needed) {
                    if (board[r - k][c + k] == player) count++
                }
                if (count == needed) return true
            }
        }
        return false
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
