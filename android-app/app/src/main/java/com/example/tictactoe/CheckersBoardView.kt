package com.example.tictactoe

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
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

    var isFlipped: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

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
        color = Color.parseColor("#9910B981") // Translucent Emerald
        style = Paint.Style.FILL
    }

    private val jumpMoveIndicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E6EF4444") // Vibrant Crimson for Jumps
        style = Paint.Style.FILL
    }

    private val jumpTrailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FBBF24") // Golden Trail
        style = Paint.Style.STROKE
        strokeWidth = 6f
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }

    private val mandatoryCaptureHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#EF4444")
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }

    private val p1PiecePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val p2PiecePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val crownPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FBBF24")
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val jumpTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 28f
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
        jumpTextPaint.textSize = cellSize * 0.32f
    }

    fun toScreenR(r: Int) = if (isFlipped) 7 - r else r
    fun toScreenC(c: Int) = if (isFlipped) 7 - c else c
    fun toBoardR(sr: Int) = if (isFlipped) 7 - sr else sr
    fun toBoardC(sc: Int) = if (isFlipped) 7 - sc else sc

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val l = logic ?: return
        if (cellSize == 0f) calculateDimensions()

        // 1. Draw 8x8 Board Squares
        for (sr in 0..7) {
            for (sc in 0..7) {
                val r = toBoardR(sr)
                val c = toBoardC(sc)

                val left = startX + sc * cellSize
                val top = startY + sr * cellSize
                val rect = RectF(left, top, left + cellSize, top + cellSize)

                val isDark = (r + c) % 2 != 0
                canvas.drawRect(rect, if (isDark) darkSquarePaint else lightSquarePaint)

                // Highlight selected square
                if (r == selectedR && c == selectedC) {
                    canvas.drawRect(rect, selectedSquarePaint)
                }
            }
        }

        // 2. Identify and Highlight Pieces with Mandatory Jumps
        val piecesWithMandatoryJumps = mutableSetOf<Pair<Int, Int>>()
        val allJumps = l.getAllValidMovesForPlayer(l.currentPlayer).filter { it.isJump }
        if (allJumps.isNotEmpty()) {
            for (jump in allJumps) {
                piecesWithMandatoryJumps.add(Pair(jump.fromR, jump.fromC))
            }
        }

        for (p in piecesWithMandatoryJumps) {
            val sr = toScreenR(p.first)
            val sc = toScreenC(p.second)
            val cx = startX + sc * cellSize + cellSize / 2f
            val cy = startY + sr * cellSize + cellSize / 2f
            canvas.drawCircle(cx, cy, pieceRadius + 6f, mandatoryCaptureHaloPaint)
        }

        // 3. Draw Jump Trail and Destination Squares
        for (move in validMoves) {
            val fromSr = toScreenR(move.fromR)
            val fromSc = toScreenC(move.fromC)
            val toSr = toScreenR(move.toR)
            val toSc = toScreenC(move.toC)

            val startCx = startX + fromSc * cellSize + cellSize / 2f
            val startCy = startY + fromSr * cellSize + cellSize / 2f
            val destCx = startX + toSc * cellSize + cellSize / 2f
            val destCy = startY + toSr * cellSize + cellSize / 2f

            if (move.isJump) {
                // Draw connecting dashed jump trail
                val path = Path().apply {
                    moveTo(startCx, startCy)
                    quadTo((startCx + destCx) / 2f, min(startCy, destCy) - 24f, destCx, destCy)
                }
                canvas.drawPath(path, jumpTrailPaint)

                // Draw Jump Target Circle
                canvas.drawCircle(destCx, destCy, pieceRadius * 0.48f, jumpMoveIndicatorPaint)
                val yPos = (destCy - ((jumpTextPaint.descent() + jumpTextPaint.ascent()) / 2))
                canvas.drawText("⚔️", destCx, yPos, jumpTextPaint)
            } else {
                // Simple move indicator
                canvas.drawCircle(destCx, destCy, pieceRadius * 0.42f, validMoveIndicatorPaint)
            }
        }

        // 4. Draw Pieces
        for (sr in 0..7) {
            for (sc in 0..7) {
                val r = toBoardR(sr)
                val c = toBoardC(sc)
                val piece = l.board[r][c]

                if (piece != CheckersLogic.EMPTY) {
                    val cx = startX + sc * cellSize + cellSize / 2f
                    val cy = startY + sr * cellSize + cellSize / 2f

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
                val sc = ((touchX - startX) / cellSize).toInt().coerceIn(0, 7)
                val sr = ((touchY - startY) / cellSize).toInt().coerceIn(0, 7)

                val r = toBoardR(sr)
                val c = toBoardC(sc)

                handleSquareTap(r, c)
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
