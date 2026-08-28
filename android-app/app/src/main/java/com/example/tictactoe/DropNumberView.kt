package com.example.tictactoe

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class DropNumberView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var logic: DropNumberLogic? = null
    var onColumnTapped: ((col: Int) -> Unit)? = null

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1E1B4B")
    }

    private val colLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#312E81")
        strokeWidth = 2f
    }

    private val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.WHITE
        isFakeBoldText = true
    }

    fun setLogic(dropLogic: DropNumberLogic) {
        this.logic = dropLogic
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val l = logic ?: return

        val w = width.toFloat()
        val h = height.toFloat()
        val cols = l.cols
        val rows = l.rows

        val colWidth = w / cols
        val rowHeight = h / rows

        // Draw background
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // Draw column divider lines
        for (c in 1 until cols) {
            val x = c * colWidth
            canvas.drawLine(x, 0f, x, h, colLinePaint)
        }

        // Draw Tiles
        // logic.grid[r][c]: r=0 is bottom (y = h - rowHeight), r=rows-1 is top (y = 0)
        val margin = 4f
        val radius = 14f

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val value = l.grid[r][c]
                val left = c * colWidth + margin
                val right = (c + 1) * colWidth - margin
                val bottom = h - (r * rowHeight) - margin
                val top = h - ((r + 1) * rowHeight) + margin

                val rect = RectF(left, top, right, bottom)

                if (value != 0) {
                    tilePaint.color = getTileColor(value)
                    canvas.drawRoundRect(rect, radius, radius, tilePaint)

                    textPaint.color = if (value in 2..4) Color.parseColor("#1E293B") else Color.WHITE
                    textPaint.textSize = if (value >= 1024) colWidth * 0.32f else colWidth * 0.42f

                    val text = if (value >= 1000) "${value / 1000}K" else value.toString()
                    val textY = top + (rowHeight / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)
                    canvas.drawText(text, left + (colWidth / 2f) - margin, textY, textPaint)
                } else {
                    // Empty cell subtle placeholder
                    tilePaint.color = Color.parseColor("#1E293B")
                    tilePaint.alpha = 50
                    canvas.drawRoundRect(rect, radius, radius, tilePaint)
                }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val l = logic ?: return false
            val colWidth = width.toFloat() / l.cols
            val col = (event.x / colWidth).toInt().coerceIn(0, l.cols - 1)
            onColumnTapped?.invoke(col)
            return true
        }
        return super.onTouchEvent(event)
    }

    companion object {
        fun getTileColor(value: Int): Int {
            return when (value) {
                2 -> Color.parseColor("#CBD5E1")
                4 -> Color.parseColor("#FDE68A")
                8 -> Color.parseColor("#FB923C")
                16 -> Color.parseColor("#F97316")
                32 -> Color.parseColor("#F87171")
                64 -> Color.parseColor("#EF4444")
                128 -> Color.parseColor("#FBBF24")
                256 -> Color.parseColor("#F59E0B")
                512 -> Color.parseColor("#D97706")
                1024 -> Color.parseColor("#38BDF8")
                2048 -> Color.parseColor("#6366F1")
                4096 -> Color.parseColor("#A855F7")
                8192 -> Color.parseColor("#EC4899")
                else -> Color.parseColor("#10B981")
            }
        }
    }
}
