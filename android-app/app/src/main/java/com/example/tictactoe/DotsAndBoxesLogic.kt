package com.example.tictactoe

import android.os.SystemClock
import kotlin.math.max
import kotlin.math.min

class DotsAndBoxesLogic(val gridSize: Int = 4) {

    val numBoxesRow = gridSize - 1
    val totalBoxes = numBoxesRow * numBoxesRow

    // horizontalEdges[r][c] connects dot (r, c) to (r, c+1) -> dimensions: [gridSize][gridSize - 1]
    val horizontalEdges = Array(gridSize) { BooleanArray(gridSize - 1) }

    // verticalEdges[r][c] connects dot (r, c) to (r+1, c) -> dimensions: [gridSize - 1][gridSize]
    val verticalEdges = Array(gridSize - 1) { BooleanArray(gridSize) }

    // boxes[r][c] represents box at (r, c) -> 0 = unoccupied, 1 = Player 1, 2 = Player 2
    val boxes = Array(numBoxesRow) { IntArray(numBoxesRow) }

    var currentPlayer = 1 // 1 = P1 (Cyan/Red), 2 = P2 (Amber/Yellow)
    var scoreP1 = 0
    var scoreP2 = 0
    var isGameOver = false
    var winner = 0 // 0 = Draw/None, 1 = P1, 2 = P2

    fun isEdgePlaced(isVertical: Boolean, r: Int, c: Int): Boolean {
        return if (isVertical) {
            if (r in 0 until numBoxesRow && c in 0 until gridSize) verticalEdges[r][c] else true
        } else {
            if (r in 0 until gridSize && c in 0 until numBoxesRow) horizontalEdges[r][c] else true
        }
    }

    /**
     * Attempts to place an edge.
     * @return true if move was placed, false if already taken or invalid.
     */
    fun makeMove(isVertical: Boolean, r: Int, c: Int): Boolean {
        if (isGameOver) return false
        if (isEdgePlaced(isVertical, r, c)) return false

        if (isVertical) {
            if (r !in 0 until numBoxesRow || c !in 0 until gridSize) return false
            verticalEdges[r][c] = true
        } else {
            if (r !in 0 until gridSize || c !in 0 until numBoxesRow) return false
            horizontalEdges[r][c] = true
        }

        // Check for completed boxes
        val completedBoxes = checkCompletedBoxes(isVertical, r, c)

        if (completedBoxes.isNotEmpty()) {
            for ((br, bc) in completedBoxes) {
                boxes[br][bc] = currentPlayer
                if (currentPlayer == 1) scoreP1++ else scoreP2++
            }
            // Player gets another turn when completing a box!
        } else {
            // Switch turn
            currentPlayer = if (currentPlayer == 1) 2 else 1
        }

        checkGameOver()
        return true
    }

    private fun checkCompletedBoxes(isVertical: Boolean, r: Int, c: Int): List<Pair<Int, Int>> {
        val completed = mutableListOf<Pair<Int, Int>>()

        if (isVertical) {
            // Vertical edge at (r, c) can complete box to its left (r, c-1) and right (r, c)
            if (c > 0 && isBoxComplete(r, c - 1)) {
                completed.add(Pair(r, c - 1))
            }
            if (c < numBoxesRow && isBoxComplete(r, c)) {
                completed.add(Pair(r, c))
            }
        } else {
            // Horizontal edge at (r, c) can complete box above (r-1, c) and below (r, c)
            if (r > 0 && isBoxComplete(r - 1, c)) {
                completed.add(Pair(r - 1, c))
            }
            if (r < numBoxesRow && isBoxComplete(r, c)) {
                completed.add(Pair(r, c))
            }
        }
        return completed
    }

    private fun isBoxComplete(r: Int, c: Int): Boolean {
        if (r !in 0 until numBoxesRow || c !in 0 until numBoxesRow) return false
        if (boxes[r][c] != 0) return false // already captured

        val top = horizontalEdges[r][c]
        val bottom = horizontalEdges[r + 1][c]
        val left = verticalEdges[r][c]
        val right = verticalEdges[r][c + 1]

        return top && bottom && left && right
    }

    fun countBoxSides(r: Int, c: Int): Int {
        if (r !in 0 until numBoxesRow || c !in 0 until numBoxesRow) return 0
        var count = 0
        if (horizontalEdges[r][c]) count++
        if (horizontalEdges[r + 1][c]) count++
        if (verticalEdges[r][c]) count++
        if (verticalEdges[r][c + 1]) count++
        return count
    }

    private fun checkGameOver() {
        if (scoreP1 + scoreP2 >= totalBoxes) {
            isGameOver = true
            winner = when {
                scoreP1 > scoreP2 -> 1
                scoreP2 > scoreP1 -> 2
                else -> 0
            }
        }
    }

    // ===================== AI =====================
    // Move = (isVertical, r, c)
    private fun availableMoves(): List<Triple<Boolean, Int, Int>> {
        val out = ArrayList<Triple<Boolean, Int, Int>>()
        for (r in 0 until gridSize) for (c in 0 until numBoxesRow) if (!horizontalEdges[r][c]) out.add(Triple(false, r, c))
        for (r in 0 until numBoxesRow) for (c in 0 until gridSize) if (!verticalEdges[r][c]) out.add(Triple(true, r, c))
        return out
    }

    private fun isCapturing(m: Triple<Boolean, Int, Int>): Boolean {
        val (isVert, r, c) = m
        return if (isVert) (c > 0 && countBoxSides(r, c - 1) == 3) || (c < numBoxesRow && countBoxSides(r, c) == 3)
        else (r > 0 && countBoxSides(r - 1, c) == 3) || (r < numBoxesRow && countBoxSides(r, c) == 3)
    }

    private fun isSafe(m: Triple<Boolean, Int, Int>): Boolean {
        val (isVert, r, c) = m
        val a = if (isVert) (c == 0 || countBoxSides(r, c - 1) < 2) else (r == 0 || countBoxSides(r - 1, c) < 2)
        val b = if (isVert) (c == numBoxesRow || countBoxSides(r, c) < 2) else (r == numBoxesRow || countBoxSides(r, c) < 2)
        return a && b
    }

    fun copy(): DotsAndBoxesLogic {
        val c = DotsAndBoxesLogic(gridSize)
        for (r in 0 until gridSize) horizontalEdges[r].copyInto(c.horizontalEdges[r])
        for (r in 0 until numBoxesRow) verticalEdges[r].copyInto(c.verticalEdges[r])
        for (r in 0 until numBoxesRow) boxes[r].copyInto(c.boxes[r])
        c.currentPlayer = currentPlayer
        c.scoreP1 = scoreP1
        c.scoreP2 = scoreP2
        c.isGameOver = isGameOver
        c.winner = winner
        return c
    }

    fun getAiMove(difficulty: BotDifficulty = BotDifficulty.MEDIUM): Pair<Boolean, Pair<Int, Int>>? {
        val moves = availableMoves()
        if (moves.isEmpty()) return null

        val caps = moves.filter { isCapturing(it) }
        if (caps.isNotEmpty()) return caps.random().let { (v, r, c) -> v to (r to c) }

        val safe = moves.filter { isSafe(it) }
        if (safe.isNotEmpty()) {
            return when (difficulty) {
                BotDifficulty.EASY -> safe.random()
                else -> safe.minByOrNull { m -> chainRiskAround(m) }
            }?.let { (v, r, c) -> v to (r to c) }
        }

        // No safe move — every move opens a chain. EASY: random. Else: give away the least, with search on HARD.
        if (difficulty == BotDifficulty.EASY) return moves.random().let { (v, r, c) -> v to (r to c) }

        val botPlayer = currentPlayer
        val deadline = SystemClock.elapsedRealtime() + if (difficulty == BotDifficulty.HARD) 1800L else 700L
        val maxDepth = if (difficulty == BotDifficulty.HARD) 14 else 6

        var best = moves.first()
        var bestVal = Int.MIN_VALUE
        for (m in moves.sortedBy { sacrificeSize(it) }) {
            val sim = copy()
            sim.applyMoveInternal(m)
            val v = sim.dbMinimax(maxDepth - 1, Int.MIN_VALUE + 1, Int.MAX_VALUE - 1, botPlayer, deadline)
            if (v > bestVal) { bestVal = v; best = m }
            if (SystemClock.elapsedRealtime() >= deadline) break
        }
        return best.let { (v, r, c) -> v to (r to c) }
    }

    private fun applyMoveInternal(m: Triple<Boolean, Int, Int>) = makeMove(m.first, m.second, m.third)

    private fun dbMinimax(depth: Int, alphaIn: Int, betaIn: Int, botPlayer: Int, deadline: Long): Int {
        if (isGameOver || depth <= 0 || SystemClock.elapsedRealtime() >= deadline) {
            val botScore = if (botPlayer == 1) scoreP1 else scoreP2
            val oppScore = if (botPlayer == 1) scoreP2 else scoreP1
            return botScore - oppScore
        }
        val moves = availableMoves()
        if (moves.isEmpty()) {
            val botScore = if (botPlayer == 1) scoreP1 else scoreP2
            val oppScore = if (botPlayer == 1) scoreP2 else scoreP1
            return botScore - oppScore
        }
        val maximizing = currentPlayer == botPlayer
        var alpha = alphaIn
        var beta = betaIn
        val ordered = moves.sortedByDescending { if (isCapturing(it)) 2 else if (isSafe(it)) 1 else 0 }

        if (maximizing) {
            var bv = Int.MIN_VALUE
            for (m in ordered) {
                val s = copy(); s.applyMoveInternal(m)
                bv = max(bv, s.dbMinimax(depth - 1, alpha, beta, botPlayer, deadline))
                alpha = max(alpha, bv)
                if (beta <= alpha || SystemClock.elapsedRealtime() >= deadline) break
            }
            return bv
        } else {
            var bv = Int.MAX_VALUE
            for (m in ordered) {
                val s = copy(); s.applyMoveInternal(m)
                bv = min(bv, s.dbMinimax(depth - 1, alpha, beta, botPlayer, deadline))
                beta = min(beta, bv)
                if (beta <= alpha || SystemClock.elapsedRealtime() >= deadline) break
            }
            return bv
        }
    }

    /** How many boxes the opponent could immediately run after this (unsafe) move — smaller is better. */
    private fun sacrificeSize(m: Triple<Boolean, Int, Int>): Int {
        val s = copy()
        s.applyMoveInternal(m)
        var run = 0
        while (true) {
            val cap = s.availableMoves().firstOrNull { s.isCapturing(it) } ?: break
            s.applyMoveInternal(cap)
            run++
            if (run > s.totalBoxes) break
        }
        return run
    }

    private fun chainRiskAround(m: Triple<Boolean, Int, Int>): Int {
        val (isVert, r, c) = m
        var risk = 0
        if (isVert) {
            if (c > 0) risk += countBoxSides(r, c - 1)
            if (c < numBoxesRow) risk += countBoxSides(r, c)
        } else {
            if (r > 0) risk += countBoxSides(r - 1, c)
            if (r < numBoxesRow) risk += countBoxSides(r, c)
        }
        return risk
    }
}
