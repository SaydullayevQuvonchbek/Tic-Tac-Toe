package com.example.tictactoe

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
     * Intelligent Gomoku AI using directional heuristic evaluation
     */
    fun getAiMove(): Pair<Int, Int>? {
        if (moveCount == 0) {
            return Pair(boardSize / 2, boardSize / 2)
        }

        var bestScore = -1
        var bestMove: Pair<Int, Int>? = null

        // Consider candidates with neighboring stones within distance 2
        val candidates = mutableListOf<Pair<Int, Int>>()
        for (r in 0 until boardSize) {
            for (c in 0 until boardSize) {
                if (board[r][c] == 0 && hasNeighbor(r, c, 2)) {
                    candidates.add(Pair(r, c))
                }
            }
        }

        if (candidates.isEmpty()) {
            return Pair(boardSize / 2, boardSize / 2)
        }

        for ((r, c) in candidates) {
            // Offensive evaluation (AI = 2)
            val attackScore = evaluatePosition(r, c, 2)
            // Defensive evaluation (Block Player = 1)
            val defenseScore = evaluatePosition(r, c, 1)

            val totalScore = attackScore + (defenseScore * 1.15).toInt()
            if (totalScore > bestScore) {
                bestScore = totalScore
                bestMove = Pair(r, c)
            }
        }

        return bestMove ?: candidates.random()
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
