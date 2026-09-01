package com.example.tictactoe

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View

class DurakCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var card: Card? = null
        set(value) {
            field = value
            invalidate()
        }

    var isFaceDown: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var isSelectedCard: Boolean = false
        set(value) {
            field = value
            translationY = if (value) -30f else 0f
            invalidate()
        }

    // Paints
    private val cardBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFFFF")
        style = Paint.Style.FILL
    }

    private val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E2E8F0")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val cardSelectedGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F59E0B") // Golden Glow
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }

    private val backBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val backPatternPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#38BDF8")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val redTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#DC2626")
        isFakeBoldText = true
    }

    private val blackTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0F172A")
        isFakeBoldText = true
    }

    private val centerSymbolPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val radius = 16f
        val rect = RectF(4f, 4f, w - 4f, h - 4f)

        if (isFaceDown) {
            // Draw Card Back (Royal Blue Pattern)
            backBackgroundPaint.shader = LinearGradient(
                0f, 0f, w, h,
                Color.parseColor("#1E3A8A"), Color.parseColor("#0F172A"),
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(rect, radius, radius, backBackgroundPaint)

            // Inner Pattern Border
            val innerRect = RectF(12f, 12f, w - 12f, h - 12f)
            canvas.drawRoundRect(innerRect, radius - 4f, radius - 4f, backPatternPaint)

            // Center Crown / Logo
            centerSymbolPaint.color = Color.parseColor("#FBBF24")
            centerSymbolPaint.textSize = w * 0.35f
            val yPos = (h / 2f) - ((centerSymbolPaint.descent() + centerSymbolPaint.ascent()) / 2)
            canvas.drawText("♠️", w / 2f, yPos, centerSymbolPaint)
            return
        }

        val c = card ?: return

        // 1. Draw Card Face
        canvas.drawRoundRect(rect, radius, radius, cardBackgroundPaint)

        // 2. Draw Selected Glow or Standard Border
        if (isSelectedCard) {
            canvas.drawRoundRect(rect, radius, radius, cardSelectedGlowPaint)
        } else {
            canvas.drawRoundRect(rect, radius, radius, cardBorderPaint)
        }

        val textPaint = if (c.suit.isRed) redTextPaint else blackTextPaint
        val cornerTextSize = w * 0.22f
        textPaint.textSize = cornerTextSize

        // 3. Top-Left Rank and Suit
        val rankLabel = c.rank.label
        val suitSymbol = c.suit.symbol
        canvas.drawText(rankLabel, 12f, 22f + cornerTextSize * 0.8f, textPaint)

        textPaint.textSize = cornerTextSize * 0.85f
        canvas.drawText(suitSymbol, 12f, 26f + cornerTextSize * 1.7f, textPaint)

        // 4. Center Big Symbol
        centerSymbolPaint.color = c.suit.colorInt
        centerSymbolPaint.textSize = w * 0.45f
        val centerY = (h / 2f) - ((centerSymbolPaint.descent() + centerSymbolPaint.ascent()) / 2)
        canvas.drawText(suitSymbol, w / 2f, centerY, centerSymbolPaint)

        // 5. Bottom-Right Inverted Rank & Suit
        canvas.save()
        canvas.rotate(180f, w / 2f, h / 2f)
        textPaint.textSize = cornerTextSize
        canvas.drawText(rankLabel, 12f, 22f + cornerTextSize * 0.8f, textPaint)
        textPaint.textSize = cornerTextSize * 0.85f
        canvas.drawText(suitSymbol, 12f, 26f + cornerTextSize * 1.7f, textPaint)
        canvas.restore()
    }
}
