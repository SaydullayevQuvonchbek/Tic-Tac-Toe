package com.example.tictactoe

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

    /**
     * AI move calculation
     */
    fun getAiMove(isHard: Boolean): Pair<Boolean, Pair<Int, Int>>? {
        val allAvailableMoves = mutableListOf<Pair<Boolean, Pair<Int, Int>>>()

        // 1. Horizontal edges
        for (r in 0 until gridSize) {
            for (c in 0 until numBoxesRow) {
                if (!horizontalEdges[r][c]) {
                    allAvailableMoves.add(Pair(false, Pair(r, c)))
                }
            }
        }

        // 2. Vertical edges
        for (r in 0 until numBoxesRow) {
            for (c in 0 until gridSize) {
                if (!verticalEdges[r][c]) {
                    allAvailableMoves.add(Pair(true, Pair(r, c)))
                }
            }
        }

        if (allAvailableMoves.isEmpty()) return null

        // Priority 1: Capturing Moves (moves that complete a box)
        val capturingMoves = allAvailableMoves.filter { (isVert, pos) ->
            val (r, c) = pos
            if (isVert) {
                (c > 0 && countBoxSides(r, c - 1) == 3) || (c < numBoxesRow && countBoxSides(r, c) == 3)
            } else {
                (r > 0 && countBoxSides(r - 1, c) == 3) || (r < numBoxesRow && countBoxSides(r, c) == 3)
            }
        }

        if (capturingMoves.isNotEmpty()) {
            return capturingMoves.random()
        }

        // Priority 2: Safe Moves (moves that DO NOT create a 3rd side for opponent)
        if (isHard) {
            val safeMoves = allAvailableMoves.filter { (isVert, pos) ->
                val (r, c) = pos
                val safeLeftOrTop = if (isVert) {
                    c == 0 || countBoxSides(r, c - 1) < 2
                } else {
                    r == 0 || countBoxSides(r - 1, c) < 2
                }
                val safeRightOrBottom = if (isVert) {
                    c == numBoxesRow || countBoxSides(r, c) < 2
                } else {
                    r == numBoxesRow || countBoxSides(r, c) < 2
                }
                safeLeftOrTop && safeRightOrBottom
            }

            if (safeMoves.isNotEmpty()) {
                return safeMoves.random()
            }
        }

        // Priority 3: Fallback to any available move
        return allAvailableMoves.random()
    }
}
