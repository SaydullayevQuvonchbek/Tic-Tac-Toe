package com.example.tictactoe

class GameLogic(val size: Int) {
    val board = Array(size) { Array(size) { "" } }
    var currentPlayer = "X"
    var isGameOver = false
    var winner = ""
    private val winCondition = if (size == 5) 4 else size

    fun makeMove(row: Int, col: Int): Boolean {
        if (board[row][col] == "" && !isGameOver) {
            board[row][col] = currentPlayer
            checkWinner()
            if (!isGameOver) {
                currentPlayer = if (currentPlayer == "X") "O" else "X"
            }
            return true
        }
        return false
    }

    private fun checkWinner() {
        if (checkConsecutive(currentPlayer, winCondition)) {
            winner = currentPlayer
            isGameOver = true
            return
        }

        var isDraw = true
        for (i in 0 until size) {
            for (j in 0 until size) {
                if (board[i][j] == "") {
                    isDraw = false
                    break
                }
            }
        }
        if (isDraw && !isGameOver) {
            isGameOver = true
            winner = "Draw"
        }
    }

    private fun checkConsecutive(player: String, needed: Int): Boolean {
        // gorizontal
        for (r in 0 until size) {
            for (c in 0..size - needed) {
                var count = 0
                for (k in 0 until needed) {
                    if (board[r][c + k] == player) count++
                }
                if (count == needed) return true
            }
        }
        // vertikal
        for (c in 0 until size) {
            for (r in 0..size - needed) {
                var count = 0
                for (k in 0 until needed) {
                    if (board[r + k][c] == player) count++
                }
                if (count == needed) return true
            }
        }
        // diagonal (pastga o'ngga)
        for (r in 0..size - needed) {
            for (c in 0..size - needed) {
                var count = 0
                for (k in 0 until needed) {
                    if (board[r + k][c + k] == player) count++
                }
                if (count == needed) return true
            }
        }
        // diagonal (tepaga o'ngga)
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
}
