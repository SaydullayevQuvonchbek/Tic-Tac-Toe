package com.example.tictactoe

import kotlin.math.abs

class CheckersLogic {

    companion object {
        const val EMPTY = 0
        const val P1 = 1      // Red / White regular
        const val P2 = 2      // Black / Dark regular
        const val P1_KING = 3 // Red King (Dama)
        const val P2_KING = 4 // Black King (Dama)
    }

    // 8x8 Board
    val board = Array(8) { IntArray(8) }

    var currentPlayer = 1 // 1 = P1 (moves UP), 2 = P2 (moves DOWN)
    var isGameOver = false
    var winner = 0 // 0 = Draw/None, 1 = P1, 2 = P2

    var activeJumpPiece: Pair<Int, Int>? = null // When in a multi-jump sequence

    init {
        resetBoard()
    }

    fun resetBoard() {
        for (r in 0 until 8) {
            for (c in 0 until 8) {
                board[r][c] = EMPTY
            }
        }

        // P2 pieces (top 3 rows on dark squares)
        for (r in 0..2) {
            for (c in 0..7) {
                if ((r + c) % 2 != 0) {
                    board[r][c] = P2
                }
            }
        }

        // P1 pieces (bottom 3 rows on dark squares)
        for (r in 5..7) {
            for (c in 0..7) {
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

    /**
     * Returns all valid moves for a piece at (r, c).
     * Enforces mandatory jump if any jump exists on the board.
     */
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
        val directions = mutableListOf<Pair<Int, Int>>()

        if (piece == P1) {
            directions.add(Pair(-1, -1))
            directions.add(Pair(-1, 1))
        } else if (piece == P2) {
            directions.add(Pair(1, -1))
            directions.add(Pair(1, 1))
        } else if (isKing(piece)) {
            directions.add(Pair(-1, -1))
            directions.add(Pair(-1, 1))
            directions.add(Pair(1, -1))
            directions.add(Pair(1, 1))
        }

        for ((dr, dc) in directions) {
            val nr = r + dr
            val nc = c + dc
            if (nr in 0..7 && nc in 0..7 && board[nr][nc] == EMPTY) {
                moves.add(Move(r, c, nr, nc, false))
            }
        }
        return moves
    }

    private fun getJumpsForPiece(r: Int, c: Int, piece: Int): List<Move> {
        val jumps = mutableListOf<Move>()
        val directions = mutableListOf<Pair<Int, Int>>()

        if (piece == P1) {
            directions.add(Pair(-1, -1))
            directions.add(Pair(-1, 1))
            directions.add(Pair(1, -1))
            directions.add(Pair(1, 1))
        } else if (piece == P2) {
            directions.add(Pair(1, -1))
            directions.add(Pair(1, 1))
            directions.add(Pair(-1, -1))
            directions.add(Pair(-1, 1))
        } else if (isKing(piece)) {
            directions.add(Pair(-1, -1))
            directions.add(Pair(-1, 1))
            directions.add(Pair(1, -1))
            directions.add(Pair(1, 1))
        }

        val isEnemy = if (isP1(piece)) { p: Int -> isP2(p) } else { p: Int -> isP1(p) }

        for ((dr, dc) in directions) {
            val midR = r + dr
            val midC = c + dc
            val destR = r + dr * 2
            val destC = c + dc * 2

            if (destR in 0..7 && destC in 0..7) {
                if (isEnemy(board[midR][midC]) && board[destR][destC] == EMPTY) {
                    jumps.add(Move(r, c, destR, destC, true, midR, midC))
                }
            }
        }
        return jumps
    }

    private fun getAllJumpsForPlayer(player: Int): List<Move> {
        val jumps = mutableListOf<Move>()
        for (r in 0..7) {
            for (c in 0..7) {
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
        for (r in 0..7) {
            for (c in 0..7) {
                val piece = board[r][c]
                val match = if (player == 1) isP1(piece) else isP2(piece)
                if (match) {
                    simple.addAll(getSimpleMoves(r, c, piece))
                }
            }
        }
        return simple
    }

    /**
     * Executes a move on the board.
     * @return true if move was successful.
     */
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
        } else if (piece == P2 && toR == 7) {
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
        for (r in 0..7) {
            for (c in 0..7) {
                val piece = board[r][c]
                if (player == 1 && isP1(piece)) count++
                if (player == 2 && isP2(piece)) count++
            }
        }
        return count
    }

    /**
     * AI Bot evaluation (Minimax)
     */
    fun getAiMove(): Move? {
        val moves = getAllValidMovesForPlayer(2)
        if (moves.isEmpty()) return null

        // Priority 1: Jumps (mandatory captures)
        val jumps = moves.filter { it.isJump }
        if (jumps.isNotEmpty()) {
            return jumps.maxByOrNull {
                // Prefer jumping into safe positions
                var score = 10
                if (it.toR == 7) score += 15 // Promotion jump!
                score
            } ?: jumps.random()
        }

        // Priority 2: Best simple positional move
        return moves.maxByOrNull { move ->
            var score = 0
            val piece = board[move.fromR][move.fromC]
            if (piece == P2 && move.toR == 7) score += 20 // Promotion!
            if (move.toC in 2..5 && move.toR in 3..4) score += 5 // Center control
            if (move.fromR == 0) score -= 3 // Don't leave back rank easily
            score
        } ?: moves.random()
    }
}
