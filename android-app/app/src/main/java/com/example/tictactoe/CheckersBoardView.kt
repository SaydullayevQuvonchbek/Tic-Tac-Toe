package com.example.tictactoe

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.min

class CheckersBoardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var logic: CheckersLogic? = null
        set(value) {
            field = value
            selectedR = -1
            selectedC = -1
            validMoves = emptyList()
            invalidate()
        }

    var onMoveExecutedListener: ((fromR: Int, fromC: Int, toR: Int, toC: Int) -> Unit)? = null

    // Selection State
    var selectedR = -1
    var selectedC = -1
    var validMoves: List<CheckersLogic.Move> = emptyList()

    // Paints
    private val lightSquarePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E2E8F0")
    }

    private val darkSquarePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#334155")
    }

    private val selectedSquarePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#80F59E0B") // Translucent Amber
        style = Paint.Style.FILL
    }

    private val validMoveIndicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9910B981") // Translucent Emerald Green
        style = Paint.Style.FILL
    }

    private val p1PiecePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val p2PiecePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val crownPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FBBF24")
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private var cellSize = 0f
    private var startX = 0f
    private var startY = 0f
    private var pieceRadius = 0f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        calculateDimensions()
    }

    private fun calculateDimensions() {
        val padding = 24f
        val availableWidth = width - (padding * 2)
        val availableHeight = height - (padding * 2)

        cellSize = min(availableWidth / 8f, availableHeight / 8f)
        startX = (width - (cellSize * 8f)) / 2f
        startY = (height - (cellSize * 8f)) / 2f
        pieceRadius = cellSize * 0.40f
        crownPaint.textSize = cellSize * 0.45f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val l = logic ?: return
        if (cellSize == 0f) calculateDimensions()

        // 1. Draw 8x8 Board Squares
        for (r in 0..7) {
            for (c in 0..7) {
                val left = startX + c * cellSize
                val top = startY + r * cellSize
                val rect = RectF(left, top, left + cellSize, top + cellSize)

                val isDark = (r + c) % 2 != 0
                canvas.drawRect(rect, if (isDark) darkSquarePaint else lightSquarePaint)

                // Highlight selected square
                if (r == selectedR && c == selectedC) {
                    canvas.drawRect(rect, selectedSquarePaint)
                }
            }
        }

        // 2. Draw Valid Move Destinations
        for (move in validMoves) {
            val cx = startX + move.toC * cellSize + cellSize / 2f
            val cy = startY + move.toR * cellSize + cellSize / 2f
            canvas.drawCircle(cx, cy, pieceRadius * 0.42f, validMoveIndicatorPaint)
        }

        // 3. Draw Pieces
        for (r in 0..7) {
            for (c in 0..7) {
                val piece = l.board[r][c]
                if (piece != CheckersLogic.EMPTY) {
                    val cx = startX + c * cellSize + cellSize / 2f
                    val cy = startY + r * cellSize + cellSize / 2f

                    if (l.isP1(piece)) {
                        // P1 (Red Piece)
                        p1PiecePaint.shader = RadialGradient(
                            cx - pieceRadius * 0.3f, cy - pieceRadius * 0.3f,
                            pieceRadius * 1.2f,
                            intArrayOf(Color.parseColor("#EF4444"), Color.parseColor("#991B1B")),
                            null, Shader.TileMode.CLAMP
                        )
                        canvas.drawCircle(cx, cy, pieceRadius, p1PiecePaint)
                    } else {
                        // P2 (Black / Dark Piece)
                        p2PiecePaint.shader = RadialGradient(
                            cx - pieceRadius * 0.3f, cy - pieceRadius * 0.3f,
                            pieceRadius * 1.2f,
                            intArrayOf(Color.parseColor("#475569"), Color.parseColor("#0F172A")),
                            null, Shader.TileMode.CLAMP
                        )
                        canvas.drawCircle(cx, cy, pieceRadius, p2PiecePaint)
                    }

                    // Draw Crown for King (Dama)
                    if (l.isKing(piece)) {
                        val yPos = (cy - ((crownPaint.descent() + crownPaint.ascent()) / 2))
                        canvas.drawText("👑", cx, yPos, crownPaint)
                    }
                }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled || logic == null) return super.onTouchEvent(event)

        if (event.action == MotionEvent.ACTION_UP) {
            val touchX = event.x
            val touchY = event.y

            if (touchX in startX..(startX + cellSize * 8) && touchY in startY..(startY + cellSize * 8)) {
                val col = ((touchX - startX) / cellSize).toInt().coerceIn(0, 7)
                val row = ((touchY - startY) / cellSize).toInt().coerceIn(0, 7)

                handleSquareTap(row, col)
                return true
            }
        }
        return true
    }

    private fun handleSquareTap(r: Int, c: Int) {
        val l = logic ?: return

        // 1. If tapping a valid destination for selected piece
        val targetMove = validMoves.firstOrNull { it.toR == r && it.toC == c }
        if (targetMove != null) {
            val fromR = selectedR
            val fromC = selectedC
            selectedR = -1
            selectedC = -1
            validMoves = emptyList()
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            onMoveExecutedListener?.invoke(fromR, fromC, r, c)
            return
        }

        // 2. If selecting a piece of current player
        if (l.isCurrentPlayerPiece(l.board[r][c])) {
            val moves = l.getValidMovesForPiece(r, c)
            if (moves.isNotEmpty()) {
                selectedR = r
                selectedC = c
                validMoves = moves
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                invalidate()
            }
        } else {
            // Deselect
            selectedR = -1
            selectedC = -1
            validMoves = emptyList()
            invalidate()
        }
    }

    fun clearSelection() {
        selectedR = -1
        selectedC = -1
        validMoves = emptyList()
        invalidate()
    }
}
