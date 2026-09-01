package com.example.tictactoe

import kotlin.math.abs

enum class PieceColor {
    WHITE,
    BLACK;

    fun opposite(): PieceColor = if (this == WHITE) BLACK else WHITE
}

enum class PieceType(val symbolWhite: String, val symbolBlack: String, val value: Int) {
    PAWN("♙", "♟", 100),
    KNIGHT("♘", "♞", 320),
    BISHOP("♗", "♝", 330),
    ROOK("♖", "♜", 500),
    QUEEN("♕", "♛", 900),
    KING("♔", "♚", 20000)
}

data class ChessPiece(
    val type: PieceType,
    val color: PieceColor,
    var hasMoved: Boolean = false
) {
    val symbol: String get() = if (color == PieceColor.WHITE) type.symbolWhite else type.symbolBlack
}

data class ChessMove(
    val fromR: Int,
    val fromC: Int,
    val toR: Int,
    val toC: Int,
    val piece: ChessPiece,
    val capturedPiece: ChessPiece? = null,
    val isCastling: Boolean = false,
    val isEnPassant: Boolean = false,
    val promotionType: PieceType? = null
)

class ChessLogic {

    var board = Array(8) { arrayOfNulls<ChessPiece>(8) }
    var currentTurn: PieceColor = PieceColor.WHITE

    var enPassantTarget: Pair<Int, Int>? = null // (row, col) square that can be captured via en passant
    var moveHistory = mutableListOf<ChessMove>()
    var capturedWhitePieces = mutableListOf<ChessPiece>()
    var capturedBlackPieces = mutableListOf<ChessPiece>()

    var isGameOver: Boolean = false
    var winner: PieceColor? = null // null = Draw or ongoing
    var isDraw: Boolean = false
    var isCheck: Boolean = false

    init {
        resetBoard()
    }

    fun resetBoard() {
        board = Array(8) { arrayOfNulls(8) }
        moveHistory.clear()
        capturedWhitePieces.clear()
        capturedBlackPieces.clear()
        enPassantTarget = null
        currentTurn = PieceColor.WHITE
        isGameOver = false
        winner = null
        isDraw = false
        isCheck = false

        // Black Pieces (Top rows 0, 1)
        board[0][0] = ChessPiece(PieceType.ROOK, PieceColor.BLACK)
        board[0][1] = ChessPiece(PieceType.KNIGHT, PieceColor.BLACK)
        board[0][2] = ChessPiece(PieceType.BISHOP, PieceColor.BLACK)
        board[0][3] = ChessPiece(PieceType.QUEEN, PieceColor.BLACK)
        board[0][4] = ChessPiece(PieceType.KING, PieceColor.BLACK)
        board[0][5] = ChessPiece(PieceType.BISHOP, PieceColor.BLACK)
        board[0][6] = ChessPiece(PieceType.KNIGHT, PieceColor.BLACK)
        board[0][7] = ChessPiece(PieceType.ROOK, PieceColor.BLACK)
        for (c in 0..7) {
            board[1][c] = ChessPiece(PieceType.PAWN, PieceColor.BLACK)
        }

        // White Pieces (Bottom rows 6, 7)
        for (c in 0..7) {
            board[6][c] = ChessPiece(PieceType.PAWN, PieceColor.WHITE)
        }
        board[7][0] = ChessPiece(PieceType.ROOK, PieceColor.WHITE)
        board[7][1] = ChessPiece(PieceType.KNIGHT, PieceColor.WHITE)
        board[7][2] = ChessPiece(PieceType.BISHOP, PieceColor.WHITE)
        board[7][3] = ChessPiece(PieceType.QUEEN, PieceColor.WHITE)
        board[7][4] = ChessPiece(PieceType.KING, PieceColor.WHITE)
        board[7][5] = ChessPiece(PieceType.BISHOP, PieceColor.WHITE)
        board[7][6] = ChessPiece(PieceType.KNIGHT, PieceColor.WHITE)
        board[7][7] = ChessPiece(PieceType.ROOK, PieceColor.WHITE)
    }

    fun findKing(color: PieceColor, currentBoard: Array<Array<ChessPiece?>> = board): Pair<Int, Int>? {
        for (r in 0..7) {
            for (c in 0..7) {
                val p = currentBoard[r][c]
                if (p != null && p.type == PieceType.KING && p.color == color) {
                    return Pair(r, c)
                }
            }
        }
        return null
    }

    fun isSquareAttacked(targetR: Int, targetC: Int, attackerColor: PieceColor, currentBoard: Array<Array<ChessPiece?>> = board): Boolean {
        // 1. Pawn Attacks
        val pawnPushesDir = if (attackerColor == PieceColor.WHITE) 1 else -1
        val pawnAttackR = targetR + pawnPushesDir
        for (pawnAttackC in listOf(targetC - 1, targetC + 1)) {
            if (pawnAttackR in 0..7 && pawnAttackC in 0..7) {
                val p = currentBoard[pawnAttackR][pawnAttackC]
                if (p != null && p.color == attackerColor && p.type == PieceType.PAWN) {
                    return true
                }
            }
        }

        // 2. Knight Attacks
        val knightOffsets = listOf(
            Pair(-2, -1), Pair(-2, 1), Pair(-1, -2), Pair(-1, 2),
            Pair(1, -2), Pair(1, 2), Pair(2, -1), Pair(2, 1)
        )
        for ((dr, dc) in knightOffsets) {
            val nr = targetR + dr
            val nc = targetC + dc
            if (nr in 0..7 && nc in 0..7) {
                val p = currentBoard[nr][nc]
                if (p != null && p.color == attackerColor && p.type == PieceType.KNIGHT) {
                    return true
                }
            }
        }

        // 3. Bishop / Queen Diagonal Rays
        val diagDirs = listOf(Pair(-1, -1), Pair(-1, 1), Pair(1, -1), Pair(1, 1))
        for ((dr, dc) in diagDirs) {
            var step = 1
            while (true) {
                val nr = targetR + dr * step
                val nc = targetC + dc * step
                if (nr !in 0..7 || nc !in 0..7) break
                val p = currentBoard[nr][nc]
                if (p != null) {
                    if (p.color == attackerColor && (p.type == PieceType.BISHOP || p.type == PieceType.QUEEN)) {
                        return true
                    }
                    break
                }
                step++
            }
        }

        // 4. Rook / Queen Orthogonal Rays
        val orthoDirs = listOf(Pair(-1, 0), Pair(1, 0), Pair(0, -1), Pair(0, 1))
        for ((dr, dc) in orthoDirs) {
            var step = 1
            while (true) {
                val nr = targetR + dr * step
                val nc = targetC + dc * step
                if (nr !in 0..7 || nc !in 0..7) break
                val p = currentBoard[nr][nc]
                if (p != null) {
                    if (p.color == attackerColor && (p.type == PieceType.ROOK || p.type == PieceType.QUEEN)) {
                        return true
                    }
                    break
                }
                step++
            }
        }

        // 5. King Attacks (Adjacent squares)
        val allDirs = diagDirs + orthoDirs
        for ((dr, dc) in allDirs) {
            val nr = targetR + dr
            val nc = targetC + dc
            if (nr in 0..7 && nc in 0..7) {
                val p = currentBoard[nr][nc]
                if (p != null && p.color == attackerColor && p.type == PieceType.KING) {
                    return true
                }
            }
        }

        return false
    }

    fun isKingInCheck(color: PieceColor, currentBoard: Array<Array<ChessPiece?>> = board): Boolean {
        val kingPos = findKing(color, currentBoard) ?: return false
        return isSquareAttacked(kingPos.first, kingPos.second, color.opposite(), currentBoard)
    }

    fun getPseudoLegalMovesForPiece(r: Int, c: Int): List<ChessMove> {
        val piece = board[r][c] ?: return emptyList()
        val moves = mutableListOf<ChessMove>()
        val color = piece.color
        val oppColor = color.opposite()

        when (piece.type) {
            PieceType.PAWN -> {
                val forward = if (color == PieceColor.WHITE) -1 else 1
                val startRow = if (color == PieceColor.WHITE) 6 else 1
                val promoRow = if (color == PieceColor.WHITE) 0 else 7

                // 1 Square Forward
                val f1R = r + forward
                if (f1R in 0..7 && board[f1R][c] == null) {
                    if (f1R == promoRow) {
                        for (promo in listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT)) {
                            moves.add(ChessMove(r, c, f1R, c, piece, null, promotionType = promo))
                        }
                    } else {
                        moves.add(ChessMove(r, c, f1R, c, piece))
                    }

                    // 2 Squares Forward from start row
                    val f2R = r + forward * 2
                    if (r == startRow && board[f2R][c] == null) {
                        moves.add(ChessMove(r, c, f2R, c, piece))
                    }
                }

                // Diagonal Captures
                for (dc in listOf(-1, 1)) {
                    val capR = r + forward
                    val capC = c + dc
                    if (capR in 0..7 && capC in 0..7) {
                        val target = board[capR][capC]
                        if (target != null && target.color == oppColor) {
                            if (capR == promoRow) {
                                for (promo in listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT)) {
                                    moves.add(ChessMove(r, c, capR, capC, piece, target, promotionType = promo))
                                }
                            } else {
                                moves.add(ChessMove(r, c, capR, capC, piece, target))
                            }
                        } else if (enPassantTarget != null && enPassantTarget!!.first == capR && enPassantTarget!!.second == capC) {
                            // En Passant Capture
                            val epCapturedPiece = board[r][capC]
                            if (epCapturedPiece != null && epCapturedPiece.color == oppColor && epCapturedPiece.type == PieceType.PAWN) {
                                moves.add(ChessMove(r, c, capR, capC, piece, epCapturedPiece, isEnPassant = true))
                            }
                        }
                    }
                }
            }

            PieceType.KNIGHT -> {
                val offsets = listOf(
                    Pair(-2, -1), Pair(-2, 1), Pair(-1, -2), Pair(-1, 2),
                    Pair(1, -2), Pair(1, 2), Pair(2, -1), Pair(2, 1)
                )
                for ((dr, dc) in offsets) {
                    val nr = r + dr
                    val nc = c + dc
                    if (nr in 0..7 && nc in 0..7) {
                        val target = board[nr][nc]
                        if (target == null || target.color == oppColor) {
                            moves.add(ChessMove(r, c, nr, nc, piece, target))
                        }
                    }
                }
            }

            PieceType.BISHOP -> {
                val diagDirs = listOf(Pair(-1, -1), Pair(-1, 1), Pair(1, -1), Pair(1, 1))
                for ((dr, dc) in diagDirs) {
                    var step = 1
                    while (true) {
                        val nr = r + dr * step
                        val nc = c + dc * step
                        if (nr !in 0..7 || nc !in 0..7) break
                        val target = board[nr][nc]
                        if (target == null) {
                            moves.add(ChessMove(r, c, nr, nc, piece))
                        } else {
                            if (target.color == oppColor) {
                                moves.add(ChessMove(r, c, nr, nc, piece, target))
                            }
                            break
                        }
                        step++
                    }
                }
            }

            PieceType.ROOK -> {
                val orthoDirs = listOf(Pair(-1, 0), Pair(1, 0), Pair(0, -1), Pair(0, 1))
                for ((dr, dc) in orthoDirs) {
                    var step = 1
                    while (true) {
                        val nr = r + dr * step
                        val nc = c + dc * step
                        if (nr !in 0..7 || nc !in 0..7) break
                        val target = board[nr][nc]
                        if (target == null) {
                            moves.add(ChessMove(r, c, nr, nc, piece))
                        } else {
                            if (target.color == oppColor) {
                                moves.add(ChessMove(r, c, nr, nc, piece, target))
                            }
                            break
                        }
                        step++
                    }
                }
            }

            PieceType.QUEEN -> {
                val allDirs = listOf(
                    Pair(-1, -1), Pair(-1, 1), Pair(1, -1), Pair(1, 1),
                    Pair(-1, 0), Pair(1, 0), Pair(0, -1), Pair(0, 1)
                )
                for ((dr, dc) in allDirs) {
                    var step = 1
                    while (true) {
                        val nr = r + dr * step
                        val nc = c + dc * step
                        if (nr !in 0..7 || nc !in 0..7) break
                        val target = board[nr][nc]
                        if (target == null) {
                            moves.add(ChessMove(r, c, nr, nc, piece))
                        } else {
                            if (target.color == oppColor) {
                                moves.add(ChessMove(r, c, nr, nc, piece, target))
                            }
                            break
                        }
                        step++
                    }
                }
            }

            PieceType.KING -> {
                val allDirs = listOf(
                    Pair(-1, -1), Pair(-1, 1), Pair(1, -1), Pair(1, 1),
                    Pair(-1, 0), Pair(1, 0), Pair(0, -1), Pair(0, 1)
                )
                for ((dr, dc) in allDirs) {
                    val nr = r + dr
                    val nc = c + dc
                    if (nr in 0..7 && nc in 0..7) {
                        val target = board[nr][nc]
                        if (target == null || target.color == oppColor) {
                            moves.add(ChessMove(r, c, nr, nc, piece, target))
                        }
                    }
                }

                // Castling (Rokirovka)
                if (!piece.hasMoved && !isKingInCheck(color)) {
                    val kingRow = if (color == PieceColor.WHITE) 7 else 0

                    // Kingside Castling (O-O)
                    val rRook = board[kingRow][7]
                    if (rRook != null && rRook.type == PieceType.ROOK && !rRook.hasMoved) {
                        if (board[kingRow][5] == null && board[kingRow][6] == null) {
                            if (!isSquareAttacked(kingRow, 5, oppColor) && !isSquareAttacked(kingRow, 6, oppColor)) {
                                moves.add(ChessMove(r, c, kingRow, 6, piece, isCastling = true))
                            }
                        }
                    }

                    // Queenside Castling (O-O-O)
                    val lRook = board[kingRow][0]
                    if (lRook != null && lRook.type == PieceType.ROOK && !lRook.hasMoved) {
                        if (board[kingRow][1] == null && board[kingRow][2] == null && board[kingRow][3] == null) {
                            if (!isSquareAttacked(kingRow, 2, oppColor) && !isSquareAttacked(kingRow, 3, oppColor)) {
                                moves.add(ChessMove(r, c, kingRow, 2, piece, isCastling = true))
                            }
                        }
                    }
                }
            }
        }

        return moves
    }

    fun getLegalMovesForPiece(r: Int, c: Int): List<ChessMove> {
        val piece = board[r][c] ?: return emptyList()
        if (piece.color != currentTurn) return emptyList()

        val pseudo = getPseudoLegalMovesForPiece(r, c)
        return pseudo.filter { move ->
            isMoveLegal(move)
        }
    }

    private fun isMoveLegal(move: ChessMove): Boolean {
        // Clone board to simulate move
        val tempBoard = Array(8) { r -> Array(8) { c -> board[r][c]?.copy() } }

        // Execute simulated move
        tempBoard[move.toR][move.toC] = move.piece.copy(hasMoved = true)
        tempBoard[move.fromR][move.fromC] = null

        if (move.isEnPassant) {
            tempBoard[move.fromR][move.toC] = null
        }

        if (move.isCastling) {
            val kingRow = move.fromR
            if (move.toC == 6) { // Kingside
                tempBoard[kingRow][5] = tempBoard[kingRow][7]?.copy(hasMoved = true)
                tempBoard[kingRow][7] = null
            } else if (move.toC == 2) { // Queenside
                tempBoard[kingRow][3] = tempBoard[kingRow][0]?.copy(hasMoved = true)
                tempBoard[kingRow][0] = null
            }
        }

        // Verify friendly King is NOT in check
        return !isKingInCheck(move.piece.color, tempBoard)
    }

    fun getAllLegalMovesForColor(color: PieceColor): List<ChessMove> {
        val all = mutableListOf<ChessMove>()
        for (r in 0..7) {
            for (c in 0..7) {
                val p = board[r][c]
                if (p != null && p.color == color) {
                    val pseudo = getPseudoLegalMovesForPiece(r, c)
                    for (m in pseudo) {
                        if (isMoveLegal(m)) {
                            all.add(m)
                        }
                    }
                }
            }
        }
        return all
    }

    fun makeMove(move: ChessMove): Boolean {
        if (isGameOver) return false

        val legalMoves = getLegalMovesForPiece(move.fromR, move.fromC)
        val validMove = legalMoves.firstOrNull {
            it.toR == move.toR && it.toC == move.toC &&
            (it.promotionType == move.promotionType || move.promotionType == null)
        } ?: return false

        val actualPromo = move.promotionType ?: validMove.promotionType
        val finalMove = validMove.copy(promotionType = actualPromo)

        // 1. Capture handling
        if (finalMove.capturedPiece != null) {
            if (finalMove.capturedPiece.color == PieceColor.WHITE) {
                capturedWhitePieces.add(finalMove.capturedPiece)
            } else {
                capturedBlackPieces.add(finalMove.capturedPiece)
            }
        }

        // 2. Move execution
        board[finalMove.fromR][finalMove.fromC] = null
        val placedPiece = if (finalMove.promotionType != null) {
            ChessPiece(finalMove.promotionType, finalMove.piece.color, hasMoved = true)
        } else {
            finalMove.piece.copy(hasMoved = true)
        }
        board[finalMove.toR][finalMove.toC] = placedPiece

        // 3. En Passant Pawn removal
        if (finalMove.isEnPassant) {
            board[finalMove.fromR][finalMove.toC] = null
        }

        // 4. Castling Rook displacement
        if (finalMove.isCastling) {
            val kingRow = finalMove.fromR
            if (finalMove.toC == 6) { // Kingside
                val rook = board[kingRow][7]?.copy(hasMoved = true)
                board[kingRow][7] = null
                board[kingRow][5] = rook
            } else if (finalMove.toC == 2) { // Queenside
                val rook = board[kingRow][0]?.copy(hasMoved = true)
                board[kingRow][0] = null
                board[kingRow][3] = rook
            }
        }

        // 5. Update En Passant target
        if (finalMove.piece.type == PieceType.PAWN && abs(finalMove.toR - finalMove.fromR) == 2) {
            val epRow = (finalMove.fromR + finalMove.toR) / 2
            enPassantTarget = Pair(epRow, finalMove.fromC)
        } else {
            enPassantTarget = null
        }

        moveHistory.add(finalMove)

        // 6. Switch Turn
        currentTurn = currentTurn.opposite()
        isCheck = isKingInCheck(currentTurn)

        // 7. Check Game Over (Checkmate or Stalemate)
        val nextLegalMoves = getAllLegalMovesForColor(currentTurn)
        if (nextLegalMoves.isEmpty()) {
            isGameOver = true
            if (isCheck) {
                winner = currentTurn.opposite() // Checkmate!
            } else {
                isDraw = true // Stalemate (Pat)!
            }
        }

        return true
    }
}
