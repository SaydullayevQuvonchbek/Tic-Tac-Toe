package com.example.tictactoe

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.min
import kotlin.random.Random

class LuckyWheelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class WheelItem(
        val label: String,
        val sublabel: String,
        val coinAmount: Int,
        val xpAmount: Int,
        val bgColor: Int
    )

    val items = listOf(
        WheelItem("+50 🪙", "Coins", 50, 0, Color.parseColor("#4F46E5")),
        WheelItem("+100 ⚡", "XP", 0, 100, Color.parseColor("#059669")),
        WheelItem("+100 🪙", "Coins", 100, 0, Color.parseColor("#D97706")),
        WheelItem("💎 +250 ⚡", "Big XP", 0, 250, Color.parseColor("#0891B2")),
        WheelItem("+200 🪙", "Mega Coin", 200, 0, Color.parseColor("#DB2777")),
        WheelItem("🎁 +150", "Coins+XP", 75, 75, Color.parseColor("#7C3AED")),
        WheelItem("👑 +500 🪙", "JACKPOT", 500, 200, Color.parseColor("#DC2626")),
        WheelItem("+150 ⚡", "Bonus XP", 0, 150, Color.parseColor("#16A34A"))
    )

    private val sectorPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 34f
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    private val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FEF08A")
        textSize = 22f
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F59E0B")
        style = Paint.Style.STROKE
        strokeWidth = 14f
    }
    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1E1B4B")
    }

    private var currentRotation = 0f
    private var isSpinning = false

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val size = min(width, height)
        val cx = width / 2f
        val cy = height / 2f
        val radius = size / 2f - 20f
        val rect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        val sweepAngle = 360f / items.size

        canvas.save()
        canvas.rotate(currentRotation, cx, cy)

        for (i in items.indices) {
            val item = items[i]
            sectorPaint.color = item.bgColor
            val startAngle = i * sweepAngle

            canvas.drawArc(rect, startAngle, sweepAngle, true, sectorPaint)

            // Draw text inside sector
            canvas.save()
            val textAngle = startAngle + sweepAngle / 2f
            canvas.rotate(textAngle, cx, cy)

            canvas.drawText(item.label, cx + radius * 0.62f, cy - 6f, textPaint)
            canvas.drawText(item.sublabel, cx + radius * 0.62f, cy + 24f, subTextPaint)

            canvas.restore()
        }

        // Outer Golden Border
        canvas.drawCircle(cx, cy, radius, borderPaint)

        // Center Pin Cap
        canvas.drawCircle(cx, cy, radius * 0.18f, centerPaint)
        borderPaint.strokeWidth = 6f
        canvas.drawCircle(cx, cy, radius * 0.18f, borderPaint)

        canvas.restore()
    }

    fun startSpin(onFinished: (WheelItem) -> Unit) {
        if (isSpinning) return
        isSpinning = true

        val targetIndex = Random.nextInt(items.size)
        val sweepAngle = 360f / items.size
        // Land in middle of chosen sector (indicator is at top: 270 degrees)
        val targetSectorAngle = targetIndex * sweepAngle + (sweepAngle / 2f)
        val targetAngle = 270f - targetSectorAngle

        val fullRotations = (5 + Random.nextInt(3)) * 360f
        val finalRotation = currentRotation + fullRotations + ((targetAngle - (currentRotation % 360f) + 360f) % 360f)

        ValueAnimator.ofFloat(currentRotation, finalRotation).apply {
            duration = 4200
            interpolator = DecelerateInterpolator(1.8f)
            addUpdateListener { anim ->
                currentRotation = anim.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    isSpinning = false
                    val winningItem = items[targetIndex]
                    onFinished(winningItem)
                }
            })
            start()
        }
    }
}
