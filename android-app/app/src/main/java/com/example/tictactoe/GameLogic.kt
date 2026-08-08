package com.example.tictactoe

class GameLogic {
    val board = Array(3) { Array(3) { "" } }
    var currentPlayer = "X"
    var isGameOver = false
    var winner = ""

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
        for (i in 0..2) {
            if (board[i][0] == currentPlayer && board[i][1] == currentPlayer && board[i][2] == currentPlayer) {
                winner = currentPlayer
                isGameOver = true
                return
            }
            if (board[0][i] == currentPlayer && board[1][i] == currentPlayer && board[2][i] == currentPlayer) {
                winner = currentPlayer
                isGameOver = true
                return
            }
        }
        if (board[0][0] == currentPlayer && board[1][1] == currentPlayer && board[2][2] == currentPlayer) {
            winner = currentPlayer
            isGameOver = true
            return
        }
        if (board[0][2] == currentPlayer && board[1][1] == currentPlayer && board[2][0] == currentPlayer) {
            winner = currentPlayer
            isGameOver = true
            return
        }
        var isDraw = true
        for (i in 0..2) {
            for (j in 0..2) {
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
}
