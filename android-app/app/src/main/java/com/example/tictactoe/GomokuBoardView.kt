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
import kotlin.math.hypot
import kotlin.math.min

class GomokuBoardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var logic: GomokuLogic? = null
        set(value) {
            field = value
            invalidate()
        }

    var onIntersectionSelectedListener: ((row: Int, col: Int) -> Unit)? = null

    // Paints
    private val boardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1E293B") // Modern Slate Board
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#475569")
        strokeWidth = 3f
    }

    private val starPointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#94A3B8")
        style = Paint.Style.FILL
    }

    private val blackStonePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val whiteStonePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val lastMoveMarkerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#EF4444")
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }

    private val winLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FBBF24") // Gold
        style = Paint.Style.STROKE
        strokeWidth = 12f
        strokeCap = Paint.Cap.ROUND
    }

    private var cellSize = 0f
    private var startX = 0f
    private var startY = 0f
    private var stoneRadius = 0f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        calculateDimensions()
    }

    private fun calculateDimensions() {
        val l = logic ?: return
        val size = l.boardSize
        if (size <= 1) return

        val padding = 48f
        val availableWidth = width - (padding * 2)
        val availableHeight = height - (padding * 2)

        cellSize = min(availableWidth / (size - 1), availableHeight / (size - 1))
        startX = (width - (cellSize * (size - 1))) / 2f
        startY = (height - (cellSize * (size - 1))) / 2f
        stoneRadius = cellSize * 0.44f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val l = logic ?: return
        val size = l.boardSize
        if (cellSize == 0f) calculateDimensions()

        // 1. Draw Rounded Board Background
        val boardRect = RectF(
            startX - stoneRadius - 16f,
            startY - stoneRadius - 16f,
            startX + (size - 1) * cellSize + stoneRadius + 16f,
            startY + (size - 1) * cellSize + stoneRadius + 16f
        )
        canvas.drawRoundRect(boardRect, 24f, 24f, boardPaint)

        // 2. Draw Grid Lines
        for (i in 0 until size) {
            // Horizontal lines
            val y = startY + i * cellSize
            canvas.drawLine(startX, y, startX + (size - 1) * cellSize, y, gridPaint)

            // Vertical lines
            val x = startX + i * cellSize
            canvas.drawLine(x, startY, x, startY + (size - 1) * cellSize, gridPaint)
        }

        // 3. Draw Star Points (Tengen / Hoshi)
        if (size == 15) {
            drawStarPoint(canvas, 3, 3)
            drawStarPoint(canvas, 3, 11)
            drawStarPoint(canvas, 7, 7)
            drawStarPoint(canvas, 11, 3)
            drawStarPoint(canvas, 11, 11)
        } else if (size == 10) {
            drawStarPoint(canvas, 2, 2)
            drawStarPoint(canvas, 2, 7)
            drawStarPoint(canvas, 7, 2)
            drawStarPoint(canvas, 7, 7)
        }

        // 4. Draw Stones
        for (r in 0 until size) {
            for (c in 0 until size) {
                val stone = l.board[r][c]
                if (stone != 0) {
                    val cx = startX + c * cellSize
                    val cy = startY + r * cellSize

                    if (stone == 1) {
                        // Black Stone with light gradient
                        blackStonePaint.shader = RadialGradient(
                            cx - stoneRadius * 0.3f, cy - stoneRadius * 0.3f,
                            stoneRadius * 1.2f,
                            intArrayOf(Color.parseColor("#475569"), Color.parseColor("#0F172A")),
                            null, Shader.TileMode.CLAMP
                        )
                        canvas.drawCircle(cx, cy, stoneRadius, blackStonePaint)
                    } else {
                        // White Stone with pearl gradient
                        whiteStonePaint.shader = RadialGradient(
                            cx - stoneRadius * 0.3f, cy - stoneRadius * 0.3f,
                            stoneRadius * 1.2f,
                            intArrayOf(Color.parseColor("#FFFFFF"), Color.parseColor("#CBD5E1")),
                            null, Shader.TileMode.CLAMP
                        )
                        canvas.drawCircle(cx, cy, stoneRadius, whiteStonePaint)
                    }

                    // Last Move Highlight
                    if (r == l.lastMoveRow && c == l.lastMoveCol) {
                        lastMoveMarkerPaint.color = if (stone == 1) Color.parseColor("#38BDF8") else Color.parseColor("#EF4444")
                        canvas.drawCircle(cx, cy, stoneRadius * 0.35f, lastMoveMarkerPaint)
                    }
                }
            }
        }

        // 5. Draw Winning Strike-through Line
        l.winningLine?.let { line ->
            if (line.size >= 5) {
                val start = line.first()
                val end = line.last()
                val x1 = startX + start.second * cellSize
                val y1 = startY + start.first * cellSize
                val x2 = startX + end.second * cellSize
                val y2 = startY + end.first * cellSize
                canvas.drawLine(x1, y1, x2, y2, winLinePaint)
            }
        }
    }

    private fun drawStarPoint(canvas: Canvas, r: Int, c: Int) {
        val cx = startX + c * cellSize
        val cy = startY + r * cellSize
        canvas.drawCircle(cx, cy, 6f, starPointPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled || logic == null) return super.onTouchEvent(event)

        if (event.action == MotionEvent.ACTION_UP) {
            val touchX = event.x
            val touchY = event.y

            val nearest = findNearestIntersection(touchX, touchY)
            if (nearest != null) {
                val (r, c) = nearest
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onIntersectionSelectedListener?.invoke(r, c)
                return true
            }
        }
        return true
    }

    private fun findNearestIntersection(tx: Float, ty: Float): Pair<Int, Int>? {
        val l = logic ?: return null
        val size = l.boardSize
        val touchThreshold = cellSize * 0.5f

        var closestDistance = Float.MAX_VALUE
        var closestCoord: Pair<Int, Int>? = null

        for (r in 0 until size) {
            for (c in 0 until size) {
                val cx = startX + c * cellSize
                val cy = startY + r * cellSize
                val dist = hypot((tx - cx).toDouble(), (ty - cy).toDouble()).toFloat()

                if (dist < touchThreshold && dist < closestDistance) {
                    closestDistance = dist
                    closestCoord = Pair(r, c)
                }
            }
        }
        return closestCoord
    }
}
