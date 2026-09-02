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
            translationY = if (value) -35f else 0f
            invalidate()
        }

    var isTrump: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    // Paints
    private val trumpBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FBBF24")
        style = Paint.Style.FILL
    }

    private val trumpTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3B2508")
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val cardBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFDF7")
        style = Paint.Style.FILL
    }

    private val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#33000000")
        style = Paint.Style.STROKE
    }

    private val cardSelectedGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6EE7B7") // Neon Mint Glow (Design 3a)
        style = Paint.Style.STROKE
    }

    private val backBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val backPatternPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#38BDF8")
        style = Paint.Style.STROKE
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

        val radius = w * 0.12f
        val borderPad = w * 0.025f
        val rect = RectF(borderPad, borderPad, w - borderPad, h - borderPad)

        if (isFaceDown) {
            // Draw Card Back (Royal Blue Casino Gradient)
            backBackgroundPaint.shader = LinearGradient(
                0f, 0f, w, h,
                Color.parseColor("#1E3A8A"), Color.parseColor("#0F172A"),
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(rect, radius, radius, backBackgroundPaint)

            // Inner Pattern Border
            backPatternPaint.strokeWidth = w * 0.03f
            val innerPad = w * 0.07f
            val innerRect = RectF(innerPad, innerPad, w - innerPad, h - innerPad)
            canvas.drawRoundRect(innerRect, radius * 0.8f, radius * 0.8f, backPatternPaint)

            // Center Neutral Crown Logo
            centerSymbolPaint.color = Color.parseColor("#FBBF24")
            centerSymbolPaint.textSize = w * 0.45f
            val yPos = (h / 2f) - ((centerSymbolPaint.descent() + centerSymbolPaint.ascent()) / 2)
            canvas.drawText("👑", w / 2f, yPos, centerSymbolPaint)
            return
        }

        val c = card ?: return

        // 1. Draw Card Face
        canvas.drawRoundRect(rect, radius, radius, cardBackgroundPaint)

        // 2. Draw Selected Glow or Standard Border
        if (isSelectedCard) {
            cardSelectedGlowPaint.strokeWidth = w * 0.09f
            canvas.drawRoundRect(rect, radius, radius, cardSelectedGlowPaint)
        } else {
            cardBorderPaint.strokeWidth = w * 0.035f
            canvas.drawRoundRect(rect, radius, radius, cardBorderPaint)
        }

        val textPaint = if (c.suit.isRed) redTextPaint else blackTextPaint
        val cornerRankSize = w * 0.32f
        val cornerSuitSize = w * 0.28f

        // 3. Top-Left Rank and Suit
        val rankLabel = c.rank.label
        val suitSymbol = c.suit.symbol

        val leftMargin = w * 0.08f
        val topMargin = h * 0.06f

        textPaint.textSize = cornerRankSize
        canvas.drawText(rankLabel, leftMargin, topMargin + cornerRankSize, textPaint)

        textPaint.textSize = cornerSuitSize
        canvas.drawText(suitSymbol, leftMargin, topMargin + cornerRankSize + cornerSuitSize * 0.95f, textPaint)

        // 4. Center Big Suit Symbol
        centerSymbolPaint.color = c.suit.colorInt
        centerSymbolPaint.textSize = w * 0.58f
        val centerY = (h / 2f) - ((centerSymbolPaint.descent() + centerSymbolPaint.ascent()) / 2)
        canvas.drawText(suitSymbol, w / 2f, centerY, centerSymbolPaint)

        // 5. Bottom-Right Inverted Rank & Suit
        canvas.save()
        canvas.rotate(180f, w / 2f, h / 2f)
        textPaint.textSize = cornerRankSize
        canvas.drawText(rankLabel, leftMargin, topMargin + cornerRankSize, textPaint)
        textPaint.textSize = cornerSuitSize
        canvas.drawText(suitSymbol, leftMargin, topMargin + cornerRankSize + cornerSuitSize * 0.95f, textPaint)
        canvas.restore()

        // 6. ZOD Gold Badge (if Trump)
        if (isTrump) {
            val badgeW = w * 0.42f
            val badgeH = h * 0.16f
            val badgeRect = RectF(w - badgeW - borderPad * 2, h - badgeH - borderPad * 2, w - borderPad * 2, h - borderPad * 2)
            canvas.drawRoundRect(badgeRect, 6f, 6f, trumpBadgePaint)

            trumpTextPaint.textSize = badgeH * 0.65f
            val badgeY = badgeRect.centerY() - ((trumpTextPaint.descent() + trumpTextPaint.ascent()) / 2)
            canvas.drawText("ZOD", badgeRect.centerX(), badgeY, trumpTextPaint)
        }
    }
}
