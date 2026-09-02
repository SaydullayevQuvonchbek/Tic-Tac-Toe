package com.example.tictactoe

import android.os.SystemClock
import kotlin.math.max
import kotlin.math.min

class MinimaxAI {

    fun findBestMove(
        board: Array<Array<String>>,
        aiPlayer: String,
        size: Int,
        difficulty: BotDifficulty = BotDifficulty.MEDIUM
    ): Pair<Int, Int>? {
        val opponent = if (aiPlayer == "X") "O" else "X"
        val winNeeded = if (size >= 4) 4 else 3

        val emptySpots = emptyCells(board, size)
        if (emptySpots.isEmpty()) return null

        // Always take a win / block a loss — cheap and never wrong.
        findImmediateWinningMove(board, aiPlayer, size, winNeeded)?.let { return it }
        findImmediateWinningMove(board, opponent, size, winNeeded)?.let { return it }

        // ---- 3x3: perfect minimax ----
        if (size <= 3) {
            if (difficulty == BotDifficulty.EASY && Math.random() < 0.40) return emptySpots.random()
            var bestVal = -1000
            var bestMove = emptySpots.first()
            for ((i, j) in emptySpots) {
                board[i][j] = aiPlayer
                val moveVal = minimax(board, 0, false, aiPlayer, opponent, size)
                board[i][j] = ""
                if (moveVal > bestVal) { bestVal = moveVal; bestMove = Pair(i, j) }
            }
            return bestMove
        }

        // ---- 4x4 / 5x5 ----
        if (difficulty == BotDifficulty.EASY) {
            val center = size / 2
            if (board[center][center] == "") return Pair(center, center)
            return emptySpots.random()
        }

        val depthCap = when {
            difficulty == BotDifficulty.HARD && size == 4 -> 9
            difficulty == BotDifficulty.HARD -> 5
            size == 4 -> 5
            else -> 4 // MEDIUM 5x5
        }
        val deadline = SystemClock.elapsedRealtime() + if (difficulty == BotDifficulty.HARD) 2200L else 1100L

        var bestVal = Int.MIN_VALUE
        var bestMove = orderedCells(emptySpots, size).first()
        for ((i, j) in orderedCells(emptySpots, size)) {
            board[i][j] = aiPlayer
            val v = abSearch(board, depthCap - 1, Int.MIN_VALUE + 1, Int.MAX_VALUE - 1, false,
                aiPlayer, opponent, size, winNeeded, deadline)
            board[i][j] = ""
            if (v > bestVal) { bestVal = v; bestMove = Pair(i, j) }
            if (SystemClock.elapsedRealtime() >= deadline) break
        }
        return bestMove
    }

    private fun emptyCells(board: Array<Array<String>>, size: Int): List<Pair<Int, Int>> {
        val out = ArrayList<Pair<Int, Int>>()
        for (i in 0 until size) for (j in 0 until size) if (board[i][j] == "") out.add(Pair(i, j))
        return out
    }

    private fun orderedCells(cells: List<Pair<Int, Int>>, size: Int): List<Pair<Int, Int>> {
        val c = (size - 1) / 2.0
        return cells.sortedBy { (i, j) -> kotlin.math.abs(i - c) + kotlin.math.abs(j - c) }
    }

    private fun abSearch(
        board: Array<Array<String>>, depth: Int, alphaIn: Int, betaIn: Int, isMax: Boolean,
        aiPlayer: String, opponent: String, size: Int, winNeeded: Int, deadline: Long
    ): Int {
        if (checkWinCondition(board, aiPlayer, size, winNeeded)) return 100_000 + depth
        if (checkWinCondition(board, opponent, size, winNeeded)) return -100_000 - depth

        val cells = emptyCells(board, size)
        if (cells.isEmpty()) return 0
        if (depth <= 0 || SystemClock.elapsedRealtime() >= deadline) {
            return lineHeuristic(board, aiPlayer, opponent, size, winNeeded)
        }

        var alpha = alphaIn
        var beta = betaIn
        val ordered = orderedCells(cells, size)
        if (isMax) {
            var best = Int.MIN_VALUE
            for ((i, j) in ordered) {
                board[i][j] = aiPlayer
                best = max(best, abSearch(board, depth - 1, alpha, beta, false, aiPlayer, opponent, size, winNeeded, deadline))
                board[i][j] = ""
                alpha = max(alpha, best)
                if (beta <= alpha) break
            }
            return best
        } else {
            var best = Int.MAX_VALUE
            for ((i, j) in ordered) {
                board[i][j] = opponent
                best = min(best, abSearch(board, depth - 1, alpha, beta, true, aiPlayer, opponent, size, winNeeded, deadline))
                board[i][j] = ""
                beta = min(beta, best)
                if (beta <= alpha) break
            }
            return best
        }
    }

    /** Score every winNeeded-length window; a window with only one side's marks counts count^2. */
    private fun lineHeuristic(
        board: Array<Array<String>>, aiPlayer: String, opponent: String, size: Int, needed: Int
    ): Int {
        var score = 0
        val dirs = arrayOf(intArrayOf(0, 1), intArrayOf(1, 0), intArrayOf(1, 1), intArrayOf(1, -1))
        for (r in 0 until size) for (c in 0 until size) for (d in dirs) {
            val er = r + d[0] * (needed - 1)
            val ec = c + d[1] * (needed - 1)
            if (er !in 0 until size || ec !in 0 until size) continue
            var ai = 0; var op = 0
            for (k in 0 until needed) {
                when (board[r + d[0] * k][c + d[1] * k]) {
                    aiPlayer -> ai++
                    opponent -> op++
                }
            }
            if (ai > 0 && op == 0) score += ai * ai
            else if (op > 0 && ai == 0) score -= op * op * 2 // weight defense a bit higher
        }
        return score
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
