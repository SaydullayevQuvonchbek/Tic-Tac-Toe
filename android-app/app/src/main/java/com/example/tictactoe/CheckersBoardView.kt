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
import android.widget.Toast
import kotlin.math.min

class CheckersBoardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var isFlipped: Boolean = false
    var onMoveExecutedListener: ((fromR: Int, fromC: Int, toR: Int, toC: Int) -> Unit)? = null

    var logic: CheckersLogic? = null
        set(value) {
            field = value
            selectedR = -1
            selectedC = -1
            validMoves = emptyList()
            calculateDimensions()
            invalidate()
        }

    var boardTheme: CheckersThemeManager.BoardTheme = CheckersThemeManager.BOARD_THEMES[0]
        set(value) {
            field = value
            updateThemePaints()
            invalidate()
        }

    var pieceSkin: CheckersThemeManager.PieceSkin = CheckersThemeManager.PIECE_SKINS[0]
        set(value) {
            field = value
            updateThemePaints()
            invalidate()
        }

    // Selection State
    var selectedR = -1
    var selectedC = -1
    var validMoves: List<CheckersLogic.Move> = emptyList()

    // Paints
    private val lightSquarePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val darkSquarePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val selectedRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#38BDF8") // Bright Cyan Glow
        style = Paint.Style.STROKE
        strokeWidth = 10f
    }

    private val selectedSquarePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6638BDF8")
        style = Paint.Style.FILL
    }

    private val validMoveIndicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CC10B981") // Translucent Emerald
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
        color = Color.parseColor("#F59E0B") // Golden Glow for Mandatory Jump Pieces
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }

    private val p1PiecePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val p2PiecePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val p1RingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FCA5A5")
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
    }

    private val p2RingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F1F5F9") // High-contrast Silver metallic ring for Black pieces
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
    }

    private val pieceShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#55000000") // 33% black drop shadow for 3D elevation
        style = Paint.Style.FILL
    }

    private val p1OuterRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4.5f
    }

    private val p2OuterRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5.5f
    }

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

    init {
        updateThemePaints()
    }

    private fun updateThemePaints() {
        lightSquarePaint.color = boardTheme.lightColor
        darkSquarePaint.color = boardTheme.darkColor
        p1RingPaint.color = pieceSkin.p1RingColor
        p2RingPaint.color = pieceSkin.p2RingColor
        p1OuterRimPaint.color = pieceSkin.p1RingColor
        p2OuterRimPaint.color = pieceSkin.p2RingColor
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        calculateDimensions()
    }

    private fun calculateDimensions() {
        val s = logic?.size ?: 8
        val padding = 12f
        val availableWidth = width - (padding * 2)
        val availableHeight = height - (padding * 2)

        cellSize = min(availableWidth / s.toFloat(), availableHeight / s.toFloat())
        startX = (width - (cellSize * s.toFloat())) / 2f
        startY = (height - (cellSize * s.toFloat())) / 2f
        pieceRadius = cellSize * 0.40f
        crownPaint.textSize = cellSize * 0.45f
        jumpTextPaint.textSize = cellSize * 0.32f
    }

    fun toScreenR(r: Int): Int {
        val s = logic?.size ?: 8
        return if (isFlipped) (s - 1) - r else r
    }

    fun toScreenC(c: Int): Int {
        val s = logic?.size ?: 8
        return if (isFlipped) (s - 1) - c else c
    }

    fun toBoardR(sr: Int): Int {
        val s = logic?.size ?: 8
        return if (isFlipped) (s - 1) - sr else sr
    }

    fun toBoardC(sc: Int): Int {
        val s = logic?.size ?: 8
        return if (isFlipped) (s - 1) - sc else sc
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val l = logic ?: return
        val s = l.size
        if (cellSize == 0f) calculateDimensions()

        // 1. Draw Board Squares
        for (sr in 0 until s) {
            for (sc in 0 until s) {
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

        // 2. Highlight Pieces with Mandatory Jumps
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

        // 3. Draw Jump Trail and Destination Move Indicators
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
                val path = Path().apply {
                    moveTo(startCx, startCy)
                    quadTo((startCx + destCx) / 2f, min(startCy, destCy) - 20f, destCx, destCy)
                }
                canvas.drawPath(path, jumpTrailPaint)

                canvas.drawCircle(destCx, destCy, pieceRadius * 0.48f, jumpMoveIndicatorPaint)
                val yPos = (destCy - ((jumpTextPaint.descent() + jumpTextPaint.ascent()) / 2))
                canvas.drawText("⚔️", destCx, yPos, jumpTextPaint)
            } else {
                canvas.drawCircle(destCx, destCy, pieceRadius * 0.42f, validMoveIndicatorPaint)
            }
        }

        // 4. Draw Pieces with 3D Gradients and Metallic Rings
        for (sr in 0 until s) {
            for (sc in 0 until s) {
                val r = toBoardR(sr)
                val c = toBoardC(sc)
                val piece = l.board[r][c]

                if (piece != CheckersLogic.EMPTY) {
                    val cx = startX + sc * cellSize + cellSize / 2f
                    val cy = startY + sr * cellSize + cellSize / 2f

                    // 1. Drop shadow for 3D elevation from dark squares
                    canvas.drawCircle(cx, cy + 4f, pieceRadius, pieceShadowPaint)

                    if (l.isP1(piece)) {
                        // P1 Player
                        p1PiecePaint.shader = RadialGradient(
                            cx - pieceRadius * 0.3f, cy - pieceRadius * 0.3f,
                            pieceRadius * 1.2f,
                            intArrayOf(pieceSkin.p1Color, Color.parseColor("#111827")),
                            null, Shader.TileMode.CLAMP
                        )
                        canvas.drawCircle(cx, cy, pieceRadius, p1PiecePaint)
                        canvas.drawCircle(cx, cy, pieceRadius - 2f, p1OuterRimPaint)
                        canvas.drawCircle(cx, cy, pieceRadius * 0.65f, p1RingPaint)
                        canvas.drawCircle(cx, cy, pieceRadius * 0.35f, p1RingPaint)
                    } else {
                        // P2 Player (Dark / Opponent)
                        p2PiecePaint.shader = RadialGradient(
                            cx - pieceRadius * 0.3f, cy - pieceRadius * 0.3f,
                            pieceRadius * 1.2f,
                            intArrayOf(pieceSkin.p2Color, Color.parseColor("#0F172A")),
                            null, Shader.TileMode.CLAMP
                        )
                        canvas.drawCircle(cx, cy, pieceRadius, p2PiecePaint)
                        // High-contrast outer rim right on the perimeter so dark piece clearly pops against dark squares!
                        canvas.drawCircle(cx, cy, pieceRadius - 2f, p2OuterRimPaint)
                        canvas.drawCircle(cx, cy, pieceRadius * 0.65f, p2RingPaint)
                        canvas.drawCircle(cx, cy, pieceRadius * 0.35f, p2RingPaint)
                    }

                    // Highlight selected piece with cyan glow
                    if (r == selectedR && c == selectedC) {
                        canvas.drawCircle(cx, cy, pieceRadius + 5f, selectedRingPaint)
                    }

                    // King Crown
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
        val s = logic!!.size

        if (event.action == MotionEvent.ACTION_UP) {
            val touchX = event.x
            val touchY = event.y

            if (touchX in startX..(startX + cellSize * s) && touchY in startY..(startY + cellSize * s)) {
                val sc = ((touchX - startX) / cellSize).toInt().coerceIn(0, s - 1)
                val sr = ((touchY - startY) / cellSize).toInt().coerceIn(0, s - 1)

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

        // Check if tapping a destination move square
        val targetMove = validMoves.firstOrNull { it.toR == r && it.toC == c }
        if (targetMove != null) {
            val fromR = selectedR
            val fromC = selectedC
            selectedR = -1
            selectedC = -1
            validMoves = emptyList()
            HapticHelper.performClick(context)
            onMoveExecutedListener?.invoke(fromR, fromC, r, c)
            invalidate()
            return
        }

        // Tapping a piece
        if (l.isCurrentPlayerPiece(l.board[r][c])) {
            val moves = l.getValidMovesForPiece(r, c)
            selectedR = r
            selectedC = c
            validMoves = moves
            HapticHelper.performClick(context)

            val allJumps = l.getAllValidMovesForPlayer(l.currentPlayer).filter { it.isJump }
            if (allJumps.isNotEmpty() && moves.isEmpty()) {
                Toast.makeText(context, "⚠️ Majburiy urish (Capture) mavjud! Oltin rangli donani tanlang.", Toast.LENGTH_SHORT).show()
            }
            invalidate()
        } else {
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
