package com.example.tictactoe

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class WaterTubeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    val maxCapacity = 4
    val colors = mutableListOf<Int>() // Bottom to Top: index 0 is bottom, index (size-1) is top

    private val tubePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
        color = Color.parseColor("#E2E8F0")
    }

    private val tubeFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#1E293B")
    }

    private val liquidPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val tubePath = Path()
    private val tubeRect = RectF()

    fun setWaterColors(newColors: List<Int>) {
        colors.clear()
        colors.addAll(newColors)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val stroke = tubePaint.strokeWidth
        val padding = stroke / 2f
        tubeRect.set(padding + 4f, padding, w - padding - 4f, h - padding)

        val radius = (w - padding * 2) / 2f
        tubePath.reset()
        // Top open, bottom rounded
        val radii = floatArrayOf(
            4f, 4f,       // Top-left
            4f, 4f,       // Top-right
            radius, radius, // Bottom-right
            radius, radius  // Bottom-left
        )
        tubePath.addRoundRect(tubeRect, radii, Path.Direction.CW)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1. Draw inner dark glass background
        canvas.drawPath(tubePath, tubeFillPaint)

        // 2. Clip canvas to tube shape so water stays inside rounded corners
        canvas.save()
        canvas.clipPath(tubePath)

        val totalHeight = tubeRect.height()
        val segmentHeight = totalHeight / maxCapacity

        // 3. Draw water segments from bottom (index 0) to top
        for (i in 0 until colors.size) {
            liquidPaint.color = colors[i]
            val bottom = tubeRect.bottom - (i * segmentHeight)
            val top = bottom - segmentHeight
            canvas.drawRect(tubeRect.left, top, tubeRect.right, bottom, liquidPaint)

            // Draw a subtle surface shine line at the top of each liquid layer
            val shinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(40, 255, 255, 255)
                strokeWidth = 2f
                style = Paint.Style.STROKE
            }
            canvas.drawLine(tubeRect.left, top, tubeRect.right, top, shinePaint)
        }

        canvas.restore()

        // 4. Draw outer glass tube outline and rim
        canvas.drawPath(tubePath, tubePaint)

        // Draw top tube lip/rim
        canvas.drawLine(
            tubeRect.left - 4f, tubeRect.top,
            tubeRect.right + 4f, tubeRect.top,
            tubePaint
        )
    }
}
