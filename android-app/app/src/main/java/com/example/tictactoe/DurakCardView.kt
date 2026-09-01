package com.example.tictactoe

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
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
            translationY = if (value) -40f else 0f
            invalidate()
        }

    // Paints
    private val cardBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFFFF")
        style = Paint.Style.FILL
    }

    private val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CBD5E1")
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
    }

    private val cardSelectedGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F59E0B") // Golden Glow
        style = Paint.Style.STROKE
        strokeWidth = 10f
    }

    private val backBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val backPatternPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#38BDF8")
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }

    private val redTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#DC2626")
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val blackTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0F172A")
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val centerSymbolPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val radius = 18f
        val rect = RectF(4f, 4f, w - 4f, h - 4f)

        if (isFaceDown) {
            // Draw Card Back (Royal Blue Casino Gradient)
            backBackgroundPaint.shader = LinearGradient(
                0f, 0f, w, h,
                Color.parseColor("#1E3A8A"), Color.parseColor("#0F172A"),
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(rect, radius, radius, backBackgroundPaint)

            // Inner Pattern Border
            val innerRect = RectF(8f, 8f, w - 8f, h - 8f)
            canvas.drawRoundRect(innerRect, radius - 4f, radius - 4f, backPatternPaint)

            // Diamond Mesh Lines
            val meshRect = RectF(14f, 14f, w - 14f, h - 14f)
            canvas.drawRoundRect(meshRect, radius - 6f, radius - 6f, backPatternPaint)

            // Center Neutral Crown Logo (No Spade / Qarg'a confusion!)
            centerSymbolPaint.color = Color.parseColor("#FBBF24")
            centerSymbolPaint.textSize = w * 0.40f
            val yPos = (h / 2f) - ((centerSymbolPaint.descent() + centerSymbolPaint.ascent()) / 2)
            canvas.drawText("👑", w / 2f, yPos, centerSymbolPaint)
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
        val cornerRankSize = w * 0.30f
        val cornerSuitSize = w * 0.25f

        // 3. Top-Left Rank and Suit
        val rankLabel = c.rank.label
        val suitSymbol = c.suit.symbol

        textPaint.textSize = cornerRankSize
        canvas.drawText(rankLabel, 8f, 10f + cornerRankSize, textPaint)

        textPaint.textSize = cornerSuitSize
        canvas.drawText(suitSymbol, 8f, 14f + cornerRankSize + cornerSuitSize, textPaint)

        // 4. Center Big Suit Symbol
        centerSymbolPaint.color = c.suit.colorInt
        centerSymbolPaint.textSize = w * 0.54f
        val centerY = (h / 2f) - ((centerSymbolPaint.descent() + centerSymbolPaint.ascent()) / 2)
        canvas.drawText(suitSymbol, w / 2f, centerY, centerSymbolPaint)

        // 5. Bottom-Right Inverted Rank & Suit
        canvas.save()
        canvas.rotate(180f, w / 2f, h / 2f)
        textPaint.textSize = cornerRankSize
        canvas.drawText(rankLabel, 8f, 10f + cornerRankSize, textPaint)
        textPaint.textSize = cornerSuitSize
        canvas.drawText(suitSymbol, 8f, 14f + cornerRankSize + cornerSuitSize, textPaint)
        canvas.restore()
    }
}
