package com.example.tictactoe

import android.os.SystemClock
import kotlin.math.max

class Connect4Logic {
    val rows = 6
    val cols = 7
    // 0 = empty, 1 = Player 1 (Red), 2 = Player 2 (Yellow)
    val board = Array(rows) { IntArray(cols) }
    
    var currentPlayer = 1
    var isGameOver = false
    var winner = 0
    var winningLine: List<Pair<Int, Int>>? = null

    // Tries to drop a token in the specified column
    // Returns the row it landed in, or -1 if full
    fun dropToken(col: Int): Int {
        if (isGameOver) return -1
        
        // Find the lowest empty spot in the column
        for (r in rows - 1 downTo 0) {
            if (board[r][col] == 0) {
                board[r][col] = currentPlayer
                checkWinner(r, col)
                if (!isGameOver) {
                    currentPlayer = if (currentPlayer == 1) 2 else 1
                }
                return r
            }
        }
        return -1
    }

    private fun checkWinner(r: Int, c: Int) {
        val player = board[r][c]
        val directions = listOf(
            Pair(0, 1),   // Horizontal
            Pair(1, 0),   // Vertical
            Pair(1, 1),   // Diagonal \
            Pair(1, -1)   // Diagonal /
        )

        for (dir in directions) {
            var count = 1
            val line = mutableListOf(Pair(r, c))

            // Check positive direction
            var i = 1
            while (true) {
                val nr = r + dir.first * i
                val nc = c + dir.second * i
                if (nr in 0 until rows && nc in 0 until cols && board[nr][nc] == player) {
                    count++
                    line.add(Pair(nr, nc))
                    i++
                } else break
            }

            // Check negative direction
            i = 1
            while (true) {
                val nr = r - dir.first * i
                val nc = c - dir.second * i
                if (nr in 0 until rows && nc in 0 until cols && board[nr][nc] == player) {
                    count++
                    line.add(Pair(nr, nc))
                    i++
                } else break
            }

            if (count >= 4) {
                isGameOver = true
                winner = player
                winningLine = line
                return
            }
        }

        // Check Draw
        var isDraw = true
        for (col in 0 until cols) {
            if (board[0][col] == 0) {
                isDraw = false
                break
            }
        }
        if (isDraw) {
            isGameOver = true
            winner = 0 // Draw
        }
    }

    // --- Bot (bot is player 2). Always called from a background thread via AiThinker. ---
    private val colOrder = intArrayOf(3, 2, 4, 1, 5, 0, 6)

    fun getBestMove(difficulty: BotDifficulty = BotDifficulty.MEDIUM): Int {
        // 1. Immediate win.
        for (c in 0 until cols) {
            val r = getDropRow(c)
            if (r != -1) { board[r][c] = 2; val w = wouldWin(r, c, 2); board[r][c] = 0; if (w) return c }
        }
        // 2. Immediate block.
        for (c in 0 until cols) {
            val r = getDropRow(c)
            if (r != -1) { board[r][c] = 1; val w = wouldWin(r, c, 1); board[r][c] = 0; if (w) return c }
        }

        val playable = colOrder.filter { getDropRow(it) != -1 }
        if (playable.isEmpty()) return 0

        // 3. Drop the trivial no-blunder rule for EASY, real search otherwise.
        if (difficulty == BotDifficulty.EASY) {
            val safe = playable.filter { c ->
                val r = getDropRow(c); board[r][c] = 2
                val gift = (0 until cols).any { oc ->
                    val or = getDropRow(oc)
                    or != -1 && run { board[or][oc] = 1; val w = wouldWin(or, oc, 1); board[or][oc] = 0; w }
                }
                board[r][c] = 0
                !gift
            }
            return safe.firstOrNull() ?: playable.first()
        }

        val depth = if (difficulty == BotDifficulty.HARD) 9 else 6
        val deadline = SystemClock.elapsedRealtime() + if (difficulty == BotDifficulty.HARD) 2600L else 1200L

        var bestCol = playable.first()
        var bestScore = Int.MIN_VALUE
        var alpha = Int.MIN_VALUE + 1
        val beta = Int.MAX_VALUE - 1
        for (c in playable) {
            val r = getDropRow(c)
            board[r][c] = 2
            val sc = -negamax(depth - 1, -beta, -alpha, 1, r, c, deadline)
            board[r][c] = 0
            if (sc > bestScore) { bestScore = sc; bestCol = c }
            alpha = max(alpha, sc)
            if (SystemClock.elapsedRealtime() >= deadline) break
        }
        return bestCol
    }

    /**
     * Negamax from [toMove]'s perspective. [lastR]/[lastC] is the move [other] just played — only
     * that cell can have completed a line, so we check it instead of rescanning the whole board.
     */
    private fun negamax(depth: Int, alphaIn: Int, betaIn: Int, toMove: Int, lastR: Int, lastC: Int, deadline: Long): Int {
        val other = if (toMove == 1) 2 else 1
        if (wouldWin(lastR, lastC, other)) return -1_000_000 + (42 - depth)  // already lost; lose slower is better
        if (isFull()) return 0
        if (depth <= 0 || SystemClock.elapsedRealtime() >= deadline) {
            return evalFor(toMove) - evalFor(other)
        }
        var alpha = alphaIn
        val beta = betaIn
        var best = Int.MIN_VALUE
        for (c in colOrder) {
            val r = getDropRow(c)
            if (r == -1) continue
            board[r][c] = toMove
            val sc = -negamax(depth - 1, -beta, -alpha, other, r, c, deadline)
            board[r][c] = 0
            best = max(best, sc)
            alpha = max(alpha, best)
            if (alpha >= beta) break
            if (SystemClock.elapsedRealtime() >= deadline) break
        }
        return best
    }

    private fun isFull(): Boolean {
        for (c in 0 until cols) if (board[0][c] == 0) return false
        return true
    }

    /** Sum over every 4-window: window with only [player] marks scores by how filled it is. */
    private fun evalFor(player: Int): Int {
        var s = 0
        val dirs = arrayOf(intArrayOf(0, 1), intArrayOf(1, 0), intArrayOf(1, 1), intArrayOf(1, -1))
        for (r in 0 until rows) for (c in 0 until cols) for (d in dirs) {
            val er = r + d[0] * 3
            val ec = c + d[1] * 3
            if (er !in 0 until rows || ec !in 0 until cols) continue
            var mine = 0; var opp = 0
            for (k in 0 until 4) {
                when (board[r + d[0] * k][c + d[1] * k]) {
                    player -> mine++
                    0 -> {}
                    else -> opp++
                }
            }
            if (opp == 0 && mine > 0) s += when (mine) { 1 -> 1; 2 -> 5; 3 -> 40; else -> 5000 }
        }
        // small centre bias
        for (r in 0 until rows) if (board[r][3] == player) s += 3
        return s
    }


    private fun getDropRow(col: Int): Int {
        for (r in rows - 1 downTo 0) {
            if (board[r][col] == 0) return r
        }
        return -1
    }

    private fun wouldWin(r: Int, c: Int, player: Int): Boolean {
        val directions = listOf(Pair(0, 1), Pair(1, 0), Pair(1, 1), Pair(1, -1))
        for (dir in directions) {
            var count = 1
            // forward
            var i = 1
            while (true) {
                val nr = r + dir.first * i
                val nc = c + dir.second * i
                if (nr in 0 until rows && nc in 0 until cols && board[nr][nc] == player) {
                    count++; i++
                } else break
            }
            // backward
            i = 1
            while (true) {
                val nr = r - dir.first * i
                val nc = c - dir.second * i
                if (nr in 0 until rows && nc in 0 until cols && board[nr][nc] == player) {
                    count++; i++
                } else break
            }
            if (count >= 4) return true
        }
        return false
    }
}
