package com.example.tictactoe

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
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
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator

class WaterTubeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    val maxCapacity = 4
    val colors = mutableListOf<Int>() // Bottom to Top
    var isSelectedTube = false
        set(value) {
            field = value
            animateSelection(value)
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
        color = Color.parseColor("#F59E0B") // Amber glow
    }

    private val tubeFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#0F172A") // Deep dark glass interior
    }

    private val liquidPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val glarePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.argb(80, 255, 255, 255)
    }

    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.argb(40, 255, 255, 255)
    }

    private val surfaceShinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(70, 255, 255, 255)
        strokeWidth = 2.5f
        style = Paint.Style.STROKE
    }

    private val tubePath = Path()
    private val tubeRect = RectF()

    fun setWaterColors(newColors: List<Int>) {
        colors.clear()
        colors.addAll(newColors)
        invalidate()
    }

    private fun animateSelection(selected: Boolean) {
        val targetY = if (selected) -30f else 0f
        val anim = ObjectAnimator.ofFloat(this, "translationY", translationY, targetY)
        anim.duration = 180
        anim.interpolator = if (selected) OvershootInterpolator(1.5f) else AccelerateDecelerateInterpolator()
        anim.start()
    }

    fun animatePour(isTiltRight: Boolean, onHalfWay: () -> Unit, onComplete: () -> Unit) {
        val tiltAngle = if (isTiltRight) 42f else -42f
        val liftY = -45f

        // Set pivot to top mouth
        pivotX = if (isTiltRight) width.toFloat() else 0f
        pivotY = 10f

        val liftAnim = ObjectAnimator.ofFloat(this, "translationY", translationY, liftY).apply { duration = 160 }
        val tiltAnim = ObjectAnimator.ofFloat(this, "rotation", 0f, tiltAngle).apply { duration = 200 }

        val set1 = AnimatorSet()
        set1.playTogether(liftAnim, tiltAnim)
        set1.interpolator = AccelerateDecelerateInterpolator()

        set1.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                onHalfWay()
                postDelayed({
                    val returnTilt = ObjectAnimator.ofFloat(this@WaterTubeView, "rotation", tiltAngle, 0f).apply { duration = 220 }
                    val returnY = ObjectAnimator.ofFloat(this@WaterTubeView, "translationY", liftY, 0f).apply { duration = 220 }
                    val set2 = AnimatorSet()
                    set2.playTogether(returnTilt, returnY)
                    set2.interpolator = OvershootInterpolator(1.2f)
                    set2.addListener(object : android.animation.AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: android.animation.Animator) {
                            onComplete()
                        }
                    })
                    set2.start()
                }, 180)
            }
        })
        set1.start()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val stroke = tubeSelectedPaint.strokeWidth
        val padding = stroke / 2f
        tubeRect.set(padding + 2f, padding + 2f, w - padding - 2f, h - padding)

        val radius = (tubeRect.width()) / 2f
        tubePath.reset()
        val radii = floatArrayOf(
            8f, 8f,         // Top-left
            8f, 8f,         // Top-right
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

        // Group consecutive identical colors for seamless liquid rendering
        data class ColorBlock(val color: Int, val startIndex: Int, val count: Int)
        val blocks = mutableListOf<ColorBlock>()

        if (colors.isNotEmpty()) {
            var currentColor = colors[0]
            var startIndex = 0
            var count = 1

            for (i in 1 until colors.size) {
                if (colors[i] == currentColor) {
                    count++
                } else {
                    blocks.add(ColorBlock(currentColor, startIndex, count))
                    currentColor = colors[i]
                    startIndex = i
                    count = 1
                }
            }
            blocks.add(ColorBlock(currentColor, startIndex, count))
        }

        // 3. Draw merged seamless liquid blocks
        for (block in blocks) {
            val baseColor = block.color
            val bottom = tubeRect.bottom - (block.startIndex * segmentHeight)
            val top = bottom - (block.count * segmentHeight)

            val shader = LinearGradient(
                tubeRect.left, top,
                tubeRect.right, bottom,
                adjustAlpha(baseColor, 0.88f),
                baseColor,
                Shader.TileMode.CLAMP
            )
            liquidPaint.shader = shader
            canvas.drawRect(tubeRect.left, top, tubeRect.right, bottom, liquidPaint)
            liquidPaint.shader = null

            // Draw meniscus surface line only at the top of the color block
            canvas.drawLine(tubeRect.left, top, tubeRect.right, top, surfaceShinePaint)
        }

        // Draw measurement tick marks
        for (i in 1..3) {
            val tickY = tubeRect.bottom - (i * segmentHeight)
            canvas.drawLine(tubeRect.left + 4f, tickY, tubeRect.left + 10f, tickY, tickPaint)
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
            tubeRect.left - 3f, tubeRect.top,
            tubeRect.right + 3f, tubeRect.top,
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
