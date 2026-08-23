package com.example.tictactoe

import java.util.ArrayDeque

class InfinityGameLogic {
    val board = Array(3) { Array(3) { "" } }
    var currentPlayer = "X"
    var isGameOver = false
    var winner = ""

    // Har bir o'yinchining yurishlarini saqlaymiz (maksimal 3 ta)
    private val xMoves = ArrayDeque<Pair<Int, Int>>()
    private val oMoves = ArrayDeque<Pair<Int, Int>>()

    fun makeMove(row: Int, col: Int): Boolean {
        if (board[row][col] == "" && !isGameOver) {
            board[row][col] = currentPlayer
            
            val currentQueue = if (currentPlayer == "X") xMoves else oMoves
            currentQueue.addLast(Pair(row, col))
            
            // Agar toshlar soni 3 tadan oshib ketsa, eng eskisini o'chiramiz (limit 3)
            if (currentQueue.size > 3) {
                val oldestMove = currentQueue.removeFirst()
                board[oldestMove.first][oldestMove.second] = ""
            }

            checkWinner()
            if (!isGameOver) {
                currentPlayer = if (currentPlayer == "X") "O" else "X"
            }
            return true
        }
        return false
    }

    // Qaysi tosh keyingi safar o'chishini bilish uchun (Xiralashtirish animatsiyasi uchun)
    fun getFadingMove(): Pair<Int, Int>? {
        val currentQueue = if (currentPlayer == "X") xMoves else oMoves
        return if (currentQueue.size == 3) currentQueue.first() else null
    }

    private fun checkWinner() {
        for (i in 0..2) {
            if (board[i][0] != "" && board[i][0] == board[i][1] && board[i][1] == board[i][2]) {
                winner = board[i][0]
                isGameOver = true
                return
            }
            if (board[0][i] != "" && board[0][i] == board[1][i] && board[1][i] == board[2][i]) {
                winner = board[0][i]
                isGameOver = true
                return
            }
        }
        if (board[0][0] != "" && board[0][0] == board[1][1] && board[1][1] == board[2][2]) {
            winner = board[0][0]
            isGameOver = true
            return
        }
        if (board[0][2] != "" && board[0][2] == board[1][1] && board[1][1] == board[2][0]) {
            winner = board[0][2]
            isGameOver = true
            return
        }
        // Infinity rejimida durang bo'lmaydi, shuning uchun draw tekshiruvi kerak emas.
    }
}
