package com.example.tictactoe

import java.util.ArrayDeque

class InfinityGameLogic(val size: Int) {
    val board = Array(size) { Array(size) { "" } }
    var currentPlayer = "X"
    var isGameOver = false
    var winner = ""

    private val winCondition = if (size == 5) 4 else size
    // Limit is equal to size (3 for 3x3, 4 for 4x4, 5 for 5x5)
    private val limit = size

    private val xMoves = ArrayDeque<Pair<Int, Int>>()
    private val oMoves = ArrayDeque<Pair<Int, Int>>()

    fun makeMove(row: Int, col: Int): Boolean {
        if (board[row][col] == "" && !isGameOver) {
            board[row][col] = currentPlayer
            
            val currentQueue = if (currentPlayer == "X") xMoves else oMoves
            currentQueue.addLast(Pair(row, col))
            
            if (currentQueue.size > limit) {
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

    fun getFadingMove(): Pair<Int, Int>? {
        val currentQueue = if (currentPlayer == "X") xMoves else oMoves
        return if (currentQueue.size == limit) currentQueue.first() else null
    }

    private fun checkWinner() {
        if (checkConsecutive(currentPlayer, winCondition)) {
            winner = currentPlayer
            isGameOver = true
            return
        }
    }

    var winningLine: List<Pair<Int, Int>>? = null

    private fun checkConsecutive(player: String, needed: Int): Boolean {
        // gorizontal
        for (r in 0 until size) {
            for (c in 0..size - needed) {
                var count = 0
                val line = mutableListOf<Pair<Int, Int>>()
                for (k in 0 until needed) {
                    if (board[r][c + k] == player) {
                        count++
                        line.add(Pair(r, c + k))
                    }
                }
                if (count == needed) {
                    winningLine = line
                    return true
                }
            }
        }
        // vertikal
        for (c in 0 until size) {
            for (r in 0..size - needed) {
                var count = 0
                val line = mutableListOf<Pair<Int, Int>>()
                for (k in 0 until needed) {
                    if (board[r + k][c] == player) {
                        count++
                        line.add(Pair(r + k, c))
                    }
                }
                if (count == needed) {
                    winningLine = line
                    return true
                }
            }
        }
        // diagonal (pastga o'ngga)
        for (r in 0..size - needed) {
            for (c in 0..size - needed) {
                var count = 0
                val line = mutableListOf<Pair<Int, Int>>()
                for (k in 0 until needed) {
                    if (board[r + k][c + k] == player) {
                        count++
                        line.add(Pair(r + k, c + k))
                    }
                }
                if (count == needed) {
                    winningLine = line
                    return true
                }
            }
        }
        // diagonal (tepaga o'ngga)
        for (r in needed - 1 until size) {
            for (c in 0..size - needed) {
                var count = 0
                val line = mutableListOf<Pair<Int, Int>>()
                for (k in 0 until needed) {
                    if (board[r - k][c + k] == player) {
                        count++
                        line.add(Pair(r - k, c + k))
                    }
                }
                if (count == needed) {
                    winningLine = line
                    return true
                }
            }
        }
        return false
    }
}
