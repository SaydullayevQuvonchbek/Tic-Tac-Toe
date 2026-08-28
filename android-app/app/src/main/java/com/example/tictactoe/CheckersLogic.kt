package com.example.tictactoe

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class CheckersLogic(var size: Int = 8) {

    companion object {
        const val EMPTY = 0
        const val P1 = 1      // Red / White regular
        const val P2 = 2      // Black / Dark regular
        const val P1_KING = 3 // Red King (Dama)
        const val P2_KING = 4 // Black King (Dama)
    }

    var board = Array(size) { IntArray(size) }

    var currentPlayer = 1 // 1 = P1 (moves UP), 2 = P2 (moves DOWN)
    var isGameOver = false
    var winner = 0 // 0 = Draw/None, 1 = P1, 2 = P2

    var activeJumpPiece: Pair<Int, Int>? = null // When in a multi-jump sequence

    init {
        resetBoard(size)
    }

    fun resetBoard(newSize: Int = size) {
        size = newSize
        board = Array(size) { IntArray(size) }

        val rowsOfPieces = if (size == 10) 4 else 3

        // P2 pieces (top rows on dark squares)
        for (r in 0 until rowsOfPieces) {
            for (c in 0 until size) {
                if ((r + c) % 2 != 0) {
                    board[r][c] = P2
                }
            }
        }

        // P1 pieces (bottom rows on dark squares)
        for (r in (size - rowsOfPieces) until size) {
            for (c in 0 until size) {
                if ((r + c) % 2 != 0) {
                    board[r][c] = P1
                }
            }
        }

        currentPlayer = 1
        isGameOver = false
        winner = 0
        activeJumpPiece = null
    }

    fun isP1(piece: Int): Boolean = piece == P1 || piece == P1_KING
    fun isP2(piece: Int): Boolean = piece == P2 || piece == P2_KING
    fun isKing(piece: Int): Boolean = piece == P1_KING || piece == P2_KING
    fun isCurrentPlayerPiece(piece: Int): Boolean = if (currentPlayer == 1) isP1(piece) else isP2(piece)

    data class Move(val fromR: Int, val fromC: Int, val toR: Int, val toC: Int, val isJump: Boolean, val jumpedR: Int = -1, val jumpedC: Int = -1)

    fun getValidMovesForPiece(r: Int, c: Int): List<Move> {
        if (isGameOver) return emptyList()
        val piece = board[r][c]
        if (!isCurrentPlayerPiece(piece)) return emptyList()

        if (activeJumpPiece != null && (activeJumpPiece!!.first != r || activeJumpPiece!!.second != c)) {
            return emptyList()
        }

        val allJumps = getAllJumpsForPlayer(currentPlayer)
        if (allJumps.isNotEmpty()) {
            return allJumps.filter { it.fromR == r && it.fromC == c }
        }

        return getSimpleMoves(r, c, piece)
    }

    private fun getSimpleMoves(r: Int, c: Int, piece: Int): List<Move> {
        val moves = mutableListOf<Move>()
        val directions = listOf(Pair(-1, -1), Pair(-1, 1), Pair(1, -1), Pair(1, 1))

        if (isKing(piece)) {
            // Flying King (Damka) in both 8x8 and 10x10: Raycast along diagonals
            for ((dr, dc) in directions) {
                var step = 1
                while (true) {
                    val nr = r + dr * step
                    val nc = c + dc * step
                    if (nr in 0 until size && nc in 0 until size && board[nr][nc] == EMPTY) {
                        moves.add(Move(r, c, nr, nc, false))
                        step++
                    } else {
                        break
                    }
                }
            }
        } else {
            // Regular piece: moves forward only (P1 moves UP, P2 moves DOWN)
            val forwardDirs = if (piece == P1) listOf(Pair(-1, -1), Pair(-1, 1)) else listOf(Pair(1, -1), Pair(1, 1))
            for ((dr, dc) in forwardDirs) {
                val nr = r + dr
                val nc = c + dc
                if (nr in 0 until size && nc in 0 until size && board[nr][nc] == EMPTY) {
                    moves.add(Move(r, c, nr, nc, false))
                }
            }
        }
        return moves
    }

    private fun getJumpsForPiece(r: Int, c: Int, piece: Int): List<Move> {
        val jumps = mutableListOf<Move>()
        val directions = listOf(Pair(-1, -1), Pair(-1, 1), Pair(1, -1), Pair(1, 1))
        val isEnemy = if (isP1(piece)) { p: Int -> isP2(p) } else { p: Int -> isP1(p) }

        if (isKing(piece)) {
            // Flying King (Damka) Capture Raycast across diagonals
            for ((dr, dc) in directions) {
                var step = 1
                var enemyFoundR = -1
                var enemyFoundC = -1

                while (true) {
                    val cr = r + dr * step
                    val cc = c + dc * step
                    if (cr !in 0 until size || cc !in 0 until size) break

                    val curPiece = board[cr][cc]
                    if (enemyFoundR == -1) {
                        if (curPiece == EMPTY) {
                            step++
                            continue
                        } else if (isEnemy(curPiece)) {
                            enemyFoundR = cr
                            enemyFoundC = cc
                            step++
                        } else {
                            // Friendly piece blocks ray
                            break
                        }
                    } else {
                        // After jumping over enemy piece, any empty square is a valid landing spot!
                        if (curPiece == EMPTY) {
                            jumps.add(Move(r, c, cr, cc, true, enemyFoundR, enemyFoundC))
                            step++
                        } else {
                            break
                        }
                    }
                }
            }
        } else {
            // Regular piece can jump over adjacent enemy piece in all 4 diagonal directions
            for ((dr, dc) in directions) {
                val midR = r + dr
                val midC = c + dc
                val destR = r + dr * 2
                val destC = c + dc * 2

                if (destR in 0 until size && destC in 0 until size) {
                    if (isEnemy(board[midR][midC]) && board[destR][destC] == EMPTY) {
                        jumps.add(Move(r, c, destR, destC, true, midR, midC))
                    }
                }
            }
        }
        return jumps
    }

    private fun getAllJumpsForPlayer(player: Int): List<Move> {
        val jumps = mutableListOf<Move>()
        for (r in 0 until size) {
            for (c in 0 until size) {
                val piece = board[r][c]
                val match = if (player == 1) isP1(piece) else isP2(piece)
                if (match) {
                    jumps.addAll(getJumpsForPiece(r, c, piece))
                }
            }
        }
        return jumps
    }

    fun getAllValidMovesForPlayer(player: Int): List<Move> {
        val jumps = getAllJumpsForPlayer(player)
        if (jumps.isNotEmpty()) return jumps

        val simple = mutableListOf<Move>()
        for (r in 0 until size) {
            for (c in 0 until size) {
                val piece = board[r][c]
                val match = if (player == 1) isP1(piece) else isP2(piece)
                if (match) {
                    simple.addAll(getSimpleMoves(r, c, piece))
                }
            }
        }
        return simple
    }

    fun makeMove(fromR: Int, fromC: Int, toR: Int, toC: Int): Boolean {
        val validMoves = getValidMovesForPiece(fromR, fromC)
        val move = validMoves.firstOrNull { it.toR == toR && it.toC == toC } ?: return false

        val piece = board[fromR][fromC]
        board[fromR][fromC] = EMPTY
        board[toR][toC] = piece

        // Check King promotion
        var promoted = false
        if (piece == P1 && toR == 0) {
            board[toR][toC] = P1_KING
            promoted = true
        } else if (piece == P2 && toR == size - 1) {
            board[toR][toC] = P2_KING
            promoted = true
        }

        // Handle capture
        if (move.isJump) {
            board[move.jumpedR][move.jumpedC] = EMPTY

            // Check multi-jump chain
            val furtherJumps = getJumpsForPiece(toR, toC, board[toR][toC])
            if (furtherJumps.isNotEmpty() && !promoted) {
                activeJumpPiece = Pair(toR, toC)
                return true // Same player continues jumping!
            }
        }

        activeJumpPiece = null
        currentPlayer = if (currentPlayer == 1) 2 else 1
        checkGameOver()
        return true
    }

    fun checkGameOver() {
        val p1Pieces = countPieces(1)
        val p2Pieces = countPieces(2)

        if (p1Pieces == 0) {
            isGameOver = true
            winner = 2
            return
        }
        if (p2Pieces == 0) {
            isGameOver = true
            winner = 1
            return
        }

        val currentMoves = getAllValidMovesForPlayer(currentPlayer)
        if (currentMoves.isEmpty()) {
            isGameOver = true
            winner = if (currentPlayer == 1) 2 else 1
        }
    }

    fun countPieces(player: Int): Int {
        var count = 0
        for (r in 0 until size) {
            for (c in 0 until size) {
                val piece = board[r][c]
                if (player == 1 && isP1(piece)) count++
                if (player == 2 && isP2(piece)) count++
            }
        }
        return count
    }

    fun getAiMove(): Move? {
        return CheckersAI.findBestMove(this, 2, maxDepth = 5)
    }
}
