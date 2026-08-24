package com.example.tictactoe

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

class DotsAndBoxesView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var logic: DotsAndBoxesLogic? = null
        set(value) {
            field = value
            invalidate()
        }

    var onEdgeSelectedListener: ((isVertical: Boolean, r: Int, c: Int) -> Unit)? = null

    // Colors
    private val colorDot = Color.parseColor("#1E293B")
    private val colorUnselected = Color.parseColor("#CBD5E1")
    private val colorP1 = Color.parseColor("#06B6D4") // Cyan
    private val colorP1Fill = Color.parseColor("#3306B6D4")
    private val colorP2 = Color.parseColor("#F59E0B") // Amber
    private val colorP2Fill = Color.parseColor("#33F59E0B")

    // Paints
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorDot
        style = Paint.Style.FILL
    }

    private val unselectedEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorUnselected
        style = Paint.Style.STROKE
        strokeWidth = 6f
        pathEffect = DashPathEffect(floatArrayOf(12f, 12f), 0f)
        strokeCap = Paint.Cap.ROUND
    }

    private val selectedEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 14f
        strokeCap = Paint.Cap.ROUND
    }

    private val boxFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 48f
        isFakeBoldText = true
    }

    private var dotRadius = 14f
    private var cellSpacing = 0f
    private var startX = 0f
    private var startY = 0f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        calculateDimensions()
    }

    private fun calculateDimensions() {
        val l = logic ?: return
        val size = l.gridSize
        if (size <= 1) return

        val padding = 72f
        val availableWidth = width - (padding * 2)
        val availableHeight = height - (padding * 2)

        cellSpacing = min(availableWidth / (size - 1), availableHeight / (size - 1))
        startX = (width - (cellSpacing * (size - 1))) / 2f
        startY = (height - (cellSpacing * (size - 1))) / 2f
        dotRadius = cellSpacing * 0.10f
        selectedEdgePaint.strokeWidth = cellSpacing * 0.09f
        textPaint.textSize = cellSpacing * 0.40f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val l = logic ?: return
        val size = l.gridSize
        if (cellSpacing == 0f) calculateDimensions()

        // 1. Draw Completed Boxes (Fills and Owner Text)
        for (r in 0 until l.numBoxesRow) {
            for (c in 0 until l.numBoxesRow) {
                val owner = l.boxes[r][c]
                if (owner != 0) {
                    val left = startX + c * cellSpacing
                    val top = startY + r * cellSpacing
                    val rect = RectF(left + 8f, top + 8f, left + cellSpacing - 8f, top + cellSpacing - 8f)

                    boxFillPaint.color = if (owner == 1) colorP1Fill else colorP2Fill
                    canvas.drawRoundRect(rect, 16f, 16f, boxFillPaint)

                    textPaint.color = if (owner == 1) colorP1 else colorP2
                    val text = if (owner == 1) "1" else "2"
                    val yPos = (rect.centerY() - ((textPaint.descent() + textPaint.ascent()) / 2))
                    canvas.drawText(text, rect.centerX(), yPos, textPaint)
                }
            }
        }

        // 2. Draw Horizontal Edges
        for (r in 0 until size) {
            for (c in 0 until l.numBoxesRow) {
                val x1 = startX + c * cellSpacing
                val y1 = startY + r * cellSpacing
                val x2 = x1 + cellSpacing
                val y2 = y1

                if (l.horizontalEdges[r][c]) {
                    selectedEdgePaint.color = colorP1 // Accent selected color
                    canvas.drawLine(x1, y1, x2, y2, selectedEdgePaint)
                } else {
                    canvas.drawLine(x1, y1, x2, y2, unselectedEdgePaint)
                }
            }
        }

        // 3. Draw Vertical Edges
        for (r in 0 until l.numBoxesRow) {
            for (c in 0 until size) {
                val x1 = startX + c * cellSpacing
                val y1 = startY + r * cellSpacing
                val x2 = x1
                val y2 = y1 + cellSpacing

                if (l.verticalEdges[r][c]) {
                    selectedEdgePaint.color = colorP2 // Accent selected color
                    canvas.drawLine(x1, y1, x2, y2, selectedEdgePaint)
                } else {
                    canvas.drawLine(x1, y1, x2, y2, unselectedEdgePaint)
                }
            }
        }

        // 4. Draw Grid Dots
        for (r in 0 until size) {
            for (c in 0 until size) {
                val cx = startX + c * cellSpacing
                val cy = startY + r * cellSpacing
                canvas.drawCircle(cx, cy, dotRadius, dotPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled || logic == null) return super.onTouchEvent(event)

        if (event.action == MotionEvent.ACTION_UP) {
            val touchX = event.x
            val touchY = event.y

            val nearestEdge = findNearestEdge(touchX, touchY)
            if (nearestEdge != null) {
                val (isVert, r, c) = nearestEdge
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onEdgeSelectedListener?.invoke(isVert, r, c)
                return true
            }
        }
        return true
    }

    private fun findNearestEdge(tx: Float, ty: Float): Triple<Boolean, Int, Int>? {
        val l = logic ?: return null
        val size = l.gridSize
        val touchThreshold = cellSpacing * 0.45f

        var closestDistance = Float.MAX_VALUE
        var closestEdge: Triple<Boolean, Int, Int>? = null

        // Check Horizontal Edges
        for (r in 0 until size) {
            for (c in 0 until l.numBoxesRow) {
                if (l.horizontalEdges[r][c]) continue

                val x1 = startX + c * cellSpacing
                val y1 = startY + r * cellSpacing
                val x2 = x1 + cellSpacing

                // Midpoint
                val midX = (x1 + x2) / 2f
                val midY = y1

                val dist = hypot((tx - midX).toDouble(), (ty - midY).toDouble()).toFloat()
                if (dist < touchThreshold && dist < closestDistance) {
                    closestDistance = dist
                    closestEdge = Triple(false, r, c)
                }
            }
        }

        // Check Vertical Edges
        for (r in 0 until l.numBoxesRow) {
            for (c in 0 until size) {
                if (l.verticalEdges[r][c]) continue

                val x1 = startX + c * cellSpacing
                val y1 = startY + r * cellSpacing
                val y2 = y1 + cellSpacing

                // Midpoint
                val midX = x1
                val midY = (y1 + y2) / 2f

                val dist = hypot((tx - midX).toDouble(), (ty - midY).toDouble()).toFloat()
                if (dist < touchThreshold && dist < closestDistance) {
                    closestDistance = dist
                    closestEdge = Triple(true, r, c)
                }
            }
        }

        return closestEdge
    }
}
