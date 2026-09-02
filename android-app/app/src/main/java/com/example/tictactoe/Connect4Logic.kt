package com.example.tictactoe

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

    // --- Basic AI for Bot mode (Minimax could be heavy for 7x6, using heuristics) ---
    fun getBestMove(): Int {
        // Simple heuristic bot for Connect 4:
        // 1. Can I win this turn?
        for (c in 0 until cols) {
            val r = getDropRow(c)
            if (r != -1) {
                board[r][c] = 2 // Assume Bot is Player 2
                if (wouldWin(r, c, 2)) {
                    board[r][c] = 0
                    return c
                }
                board[r][c] = 0
            }
        }
        
        // 2. Can the opponent win next turn? Block them!
        for (c in 0 until cols) {
            val r = getDropRow(c)
            if (r != -1) {
                board[r][c] = 1
                if (wouldWin(r, c, 1)) {
                    board[r][c] = 0
                    return c
                }
                board[r][c] = 0
            }
        }
        
        // 3. Avoid moves that hand the opponent an immediate winning drop anywhere.
        val pref = listOf(3, 4, 2, 5, 1, 6, 0)
        val playable = pref.filter { getDropRow(it) != -1 }
        val safe = playable.filter { c ->
            val r = getDropRow(c)
            board[r][c] = 2                 // bot plays here
            val opponentCanWin = (0 until cols).any { oc ->
                val or = getDropRow(oc)
                if (or == -1) false else {
                    board[or][oc] = 1
                    val win = wouldWin(or, oc, 1)
                    board[or][oc] = 0
                    win
                }
            }
            board[r][c] = 0
            !opponentCanWin
        }

        // 4. Prefer a safe center-ish column, else any playable column.
        return safe.firstOrNull() ?: playable.firstOrNull() ?: 0
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
