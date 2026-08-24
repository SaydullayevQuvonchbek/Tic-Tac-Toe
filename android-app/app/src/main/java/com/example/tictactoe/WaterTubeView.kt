package com.example.tictactoe

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View

class WaterTubeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    val maxCapacity = 4
    val colors = mutableListOf<Int>() // Bottom to Top: index 0 is bottom, index (size-1) is top
    var isSelectedTube = false
        set(value) {
            field = value
            invalidate()
        }

    private val tubePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = Color.parseColor("#E2E8F0")
    }

    private val tubeSelectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
        color = Color.parseColor("#F59E0B") // Amber glow when selected
    }

    private val tubeFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#0F172A") // Deep dark glass inside
    }

    private val liquidPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val glarePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.argb(90, 255, 255, 255)
    }

    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.argb(60, 255, 255, 255)
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
        val stroke = tubeSelectedPaint.strokeWidth
        val padding = stroke / 2f
        tubeRect.set(padding + 2f, padding + 2f, w - padding - 2f, h - padding)

        val radius = (tubeRect.width()) / 2f
        tubePath.reset()
        // Top open (small radius), bottom fully rounded
        val radii = floatArrayOf(
            6f, 6f,         // Top-left
            6f, 6f,         // Top-right
            radius, radius, // Bottom-right
            radius, radius  // Bottom-left
        )
        tubePath.addRoundRect(tubeRect, radii, Path.Direction.CW)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1. Draw inner dark glass background
        canvas.drawPath(tubePath, tubeFillPaint)

        // 2. Clip canvas to tube shape
        canvas.save()
        canvas.clipPath(tubePath)

        val totalHeight = tubeRect.height()
        val segmentHeight = totalHeight / maxCapacity

        // 3. Draw water segments from bottom to top with gradient
        for (i in 0 until colors.size) {
            val baseColor = colors[i]
            val bottom = tubeRect.bottom - (i * segmentHeight)
            val top = bottom - segmentHeight

            // Slightly brighter at top, richer at bottom
            val shader = LinearGradient(
                tubeRect.left, top,
                tubeRect.right, bottom,
                adjustAlpha(baseColor, 0.85f),
                baseColor,
                Shader.TileMode.CLAMP
            )
            liquidPaint.shader = shader
            canvas.drawRect(tubeRect.left, top, tubeRect.right, bottom, liquidPaint)
            liquidPaint.shader = null

            // Subtle surface line
            val shinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(60, 255, 255, 255)
                strokeWidth = 2f
                style = Paint.Style.STROKE
            }
            canvas.drawLine(tubeRect.left, top, tubeRect.right, top, shinePaint)
        }

        // Draw measurement tick marks inside tube
        for (i in 1..3) {
            val tickY = tubeRect.bottom - (i * segmentHeight)
            canvas.drawLine(tubeRect.left + 4f, tickY, tubeRect.left + 12f, tickY, tickPaint)
        }

        // Draw vertical glass glare reflection line on left side
        canvas.drawLine(
            tubeRect.left + 6f, tubeRect.top + 8f,
            tubeRect.left + 6f, tubeRect.bottom - 16f,
            glarePaint
        )

        canvas.restore()

        // 4. Draw outer glass tube outline and top lip
        val paintToUse = if (isSelectedTube) tubeSelectedPaint else tubePaint
        canvas.drawPath(tubePath, paintToUse)

        // Top lip line
        canvas.drawLine(
            tubeRect.left - 4f, tubeRect.top,
            tubeRect.right + 4f, tubeRect.top,
            paintToUse
        )
    }

    private fun adjustAlpha(color: Int, factor: Float): Int {
        val alpha = Color.alpha(color)
        val red = Math.min(255, (Color.red(color) * 1.15f).toInt())
        val green = Math.min(255, (Color.green(color) * 1.15f).toInt())
        val blue = Math.min(255, (Color.blue(color) * 1.15f).toInt())
        return Color.argb(alpha, red, green, blue)
    }
}
