package com.example.tictactoe

import android.os.SystemClock
import kotlin.math.max
import kotlin.math.min

class GomokuLogic(val boardSize: Int = 10) {

    // 0 = Empty, 1 = Black (P1), 2 = White (P2)
    val board = Array(boardSize) { IntArray(boardSize) }

    var currentPlayer = 1 // 1 = Black starts
    var isGameOver = false
    var winner = 0 // 0 = Draw/None, 1 = Black, 2 = White
    var moveCount = 0

    var lastMoveRow = -1
    var lastMoveCol = -1
    var winningLine: List<Pair<Int, Int>>? = null

    fun makeMove(row: Int, col: Int): Boolean {
        if (isGameOver) return false
        if (row !in 0 until boardSize || col !in 0 until boardSize) return false
        if (board[row][col] != 0) return false

        board[row][col] = currentPlayer
        lastMoveRow = row
        lastMoveCol = col
        moveCount++

        if (checkWin(row, col, currentPlayer)) {
            isGameOver = true
            winner = currentPlayer
        } else if (moveCount >= boardSize * boardSize) {
            isGameOver = true
            winner = 0 // Draw
        } else {
            currentPlayer = if (currentPlayer == 1) 2 else 1
        }
        return true
    }

    private fun checkWin(r: Int, c: Int, player: Int): Boolean {
        val directions = arrayOf(
            Pair(0, 1),  // Horizontal
            Pair(1, 0),  // Vertical
            Pair(1, 1),  // Diagonal \
            Pair(1, -1)  // Diagonal /
        )

        for ((dr, dc) in directions) {
            val line = mutableListOf<Pair<Int, Int>>()
            line.add(Pair(r, c))

            // Forward
            var step = 1
            while (true) {
                val nr = r + dr * step
                val nc = c + dc * step
                if (nr in 0 until boardSize && nc in 0 until boardSize && board[nr][nc] == player) {
                    line.add(Pair(nr, nc))
                    step++
                } else break
            }

            // Backward
            step = 1
            while (true) {
                val nr = r - dr * step
                val nc = c - dc * step
                if (nr in 0 until boardSize && nc in 0 until boardSize && board[nr][nc] == player) {
                    line.add(Pair(nr, nc))
                    step++
                } else break
            }

            if (line.size >= 5) {
                winningLine = line
                return true
            }
        }
        return false
    }

    /**
     * Gomoku AI (bot = player 2). Always called from a background thread via AiThinker.
     * EASY   = 1-ply heuristic (historical behaviour).
     * MEDIUM = 2-ply (my move → opponent's best reply) over a narrowed candidate set.
     * HARD   = threat check (win / block open-four / block open-three) then 4-ply search.
     */
    fun getAiMove(difficulty: BotDifficulty = BotDifficulty.MEDIUM): Pair<Int, Int>? {
        if (moveCount == 0) return Pair(boardSize / 2, boardSize / 2)

        val candidates = candidateCells()
        if (candidates.isEmpty()) return Pair(boardSize / 2, boardSize / 2)

        // Immediate win.
        candidates.firstOrNull { (r, c) ->
            board[r][c] = 2; val w = wins5(r, c, 2); board[r][c] = 0; w
        }?.let { return it }
        // Immediate block.
        candidates.firstOrNull { (r, c) ->
            board[r][c] = 1; val w = wins5(r, c, 1); board[r][c] = 0; w
        }?.let { return it }

        if (difficulty == BotDifficulty.EASY) return greedy1Ply(candidates)

        // Rank candidates by a fast 1-ply score, keep the strongest handful for the deeper search.
        val ranked = candidates.sortedByDescending { (r, c) ->
            evaluatePosition(r, c, 2) + (evaluatePosition(r, c, 1) * 1.2).toInt()
        }
        val topN = ranked.take(if (difficulty == BotDifficulty.HARD) 12 else 8)

        val depth = if (difficulty == BotDifficulty.HARD) 4 else 2
        val deadline = SystemClock.elapsedRealtime() + if (difficulty == BotDifficulty.HARD) 2600L else 1000L

        var best = topN.first()
        var bestVal = Int.MIN_VALUE
        var alpha = Int.MIN_VALUE + 1
        val beta = Int.MAX_VALUE - 1
        for ((r, c) in topN) {
            board[r][c] = 2
            val v = searchG(depth - 1, alpha, beta, false, deadline)
            board[r][c] = 0
            if (v > bestVal) { bestVal = v; best = Pair(r, c) }
            alpha = max(alpha, v)
            if (SystemClock.elapsedRealtime() >= deadline) break
        }
        return best
    }

    private fun greedy1Ply(candidates: List<Pair<Int, Int>>): Pair<Int, Int> {
        var bestScore = Int.MIN_VALUE
        var best = candidates.first()
        for ((r, c) in candidates) {
            val s = evaluatePosition(r, c, 2) + (evaluatePosition(r, c, 1) * 1.15).toInt()
            if (s > bestScore) { bestScore = s; best = Pair(r, c) }
        }
        return best
    }

    private fun candidateCells(): List<Pair<Int, Int>> {
        val out = ArrayList<Pair<Int, Int>>()
        for (r in 0 until boardSize) for (c in 0 until boardSize) {
            if (board[r][c] == 0 && hasNeighbor(r, c, 2)) out.add(Pair(r, c))
        }
        return out
    }

    /** Minimax over the narrowed candidate set. AI = 2 is the maximizer. */
    private fun searchG(depth: Int, alphaIn: Int, betaIn: Int, isMax: Boolean, deadline: Long): Int {
        if (depth <= 0 || SystemClock.elapsedRealtime() >= deadline) return staticEval()

        val me = if (isMax) 2 else 1
        val cand = candidateCells()
            .sortedByDescending { (r, c) -> evaluatePosition(r, c, me) + evaluatePosition(r, c, if (me == 1) 2 else 1) }
            .take(8)
        if (cand.isEmpty()) return staticEval()

        var alpha = alphaIn
        var beta = betaIn
        if (isMax) {
            var bv = Int.MIN_VALUE
            for ((r, c) in cand) {
                board[r][c] = 2
                val win = wins5(r, c, 2)
                val v = if (win) 900_000 - (100 - depth) else searchG(depth - 1, alpha, beta, false, deadline)
                board[r][c] = 0
                bv = max(bv, v)
                alpha = max(alpha, bv)
                if (beta <= alpha) break
            }
            return bv
        } else {
            var bv = Int.MAX_VALUE
            for ((r, c) in cand) {
                board[r][c] = 1
                val win = wins5(r, c, 1)
                val v = if (win) -900_000 + (100 - depth) else searchG(depth - 1, alpha, beta, true, deadline)
                board[r][c] = 0
                bv = min(bv, v)
                beta = min(beta, bv)
                if (beta <= alpha) break
            }
            return bv
        }
    }

    /** Full-board static evaluation from the bot's (player 2) perspective. */
    private fun staticEval(): Int {
        val dirs = arrayOf(intArrayOf(0, 1), intArrayOf(1, 0), intArrayOf(1, 1), intArrayOf(1, -1))
        var score = 0
        for (r in 0 until boardSize) for (c in 0 until boardSize) for (d in dirs) {
            val er = r + d[0] * 4
            val ec = c + d[1] * 4
            if (er !in 0 until boardSize || ec !in 0 until boardSize) continue
            var mine = 0; var opp = 0
            for (k in 0 until 5) {
                when (board[r + d[0] * k][c + d[1] * k]) {
                    2 -> mine++
                    1 -> opp++
                }
            }
            if (opp == 0) score += windowValue(mine)
            if (mine == 0) score -= (windowValue(opp) * 1.15).toInt()
        }
        return score
    }

    private fun windowValue(n: Int): Int = when (n) {
        0 -> 0; 1 -> 1; 2 -> 12; 3 -> 120; 4 -> 1400; else -> 200_000
    }

    /** Pure 5-in-a-row test after a hypothetical stone at (r,c); does not touch [winningLine]. */
    private fun wins5(r: Int, c: Int, player: Int): Boolean {
        val dirs = arrayOf(intArrayOf(0, 1), intArrayOf(1, 0), intArrayOf(1, 1), intArrayOf(1, -1))
        for (d in dirs) {
            var count = 1
            var step = 1
            while (true) {
                val nr = r + d[0] * step; val nc = c + d[1] * step
                if (nr in 0 until boardSize && nc in 0 until boardSize && board[nr][nc] == player) { count++; step++ } else break
            }
            step = 1
            while (true) {
                val nr = r - d[0] * step; val nc = c - d[1] * step
                if (nr in 0 until boardSize && nc in 0 until boardSize && board[nr][nc] == player) { count++; step++ } else break
            }
            if (count >= 5) return true
        }
        return false
    }

    private fun hasNeighbor(r: Int, c: Int, dist: Int): Boolean {
        for (dr in -dist..dist) {
            for (dc in -dist..dist) {
                val nr = r + dr
                val nc = c + dc
                if (nr in 0 until boardSize && nc in 0 until boardSize && board[nr][nc] != 0) {
                    return true
                }
            }
        }
        return false
    }

    private fun evaluatePosition(r: Int, c: Int, player: Int): Int {
        var score = 0
        val directions = arrayOf(Pair(0, 1), Pair(1, 0), Pair(1, 1), Pair(1, -1))

        for ((dr, dc) in directions) {
            var consecutive = 1
            var openEnds = 0

            // Forward
            var step = 1
            while (true) {
                val nr = r + dr * step
                val nc = c + dc * step
                if (nr in 0 until boardSize && nc in 0 until boardSize) {
                    if (board[nr][nc] == player) {
                        consecutive++
                        step++
                    } else {
                        if (board[nr][nc] == 0) openEnds++
                        break
                    }
                } else break
            }

            // Backward
            step = 1
            while (true) {
                val nr = r - dr * step
                val nc = c - dc * step
                if (nr in 0 until boardSize && nc in 0 until boardSize) {
                    if (board[nr][nc] == player) {
                        consecutive++
                        step++
                    } else {
                        if (board[nr][nc] == 0) openEnds++
                        break
                    }
                } else break
            }

            score += when {
                consecutive >= 5 -> 100000
                consecutive == 4 && openEnds == 2 -> 10000
                consecutive == 4 && openEnds == 1 -> 4000
                consecutive == 3 && openEnds == 2 -> 1500
                consecutive == 3 && openEnds == 1 -> 400
                consecutive == 2 && openEnds == 2 -> 100
                consecutive == 2 && openEnds == 1 -> 20
                else -> 5
            }
        }
        return score
    }
}
