package com.example.tictactoe

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import kotlin.random.Random

class ConfettiView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val colors = intArrayOf(
        Color.parseColor("#F59E0B"), // Gold
        Color.parseColor("#EF4444"), // Red
        Color.parseColor("#06B6D4"), // Cyan
        Color.parseColor("#10B981"), // Emerald
        Color.parseColor("#8B5CF6"), // Purple
        Color.parseColor("#EC4899"), // Pink
        Color.parseColor("#FCD34D"), // Light Gold
        Color.parseColor("#38BDF8")  // Sky Blue
    )

    private data class Particle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        val color: Int,
        val width: Float,
        val height: Float,
        var rotation: Float,
        var rotationSpeed: Float,
        var alpha: Int = 255,
        val isCircle: Boolean = Random.nextBoolean()
    )

    private val particles = mutableListOf<Particle>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var animator: ValueAnimator? = null

    fun burst(particleCount: Int = 90) {
        post {
            particles.clear()
            val cx = width / 2f
            val cy = height / 3f

            for (i in 0 until particleCount) {
                val angle = Random.nextDouble(0.0, Math.PI * 2)
                val speed = Random.nextDouble(15.0, 45.0)
                val vx = (Math.cos(angle) * speed).toFloat()
                val vy = (Math.sin(angle) * speed - 15).toFloat() // upward initial burst bias
                val pWidth = Random.nextFloat() * 16f + 10f
                val pHeight = Random.nextFloat() * 20f + 12f

                particles.add(
                    Particle(
                        x = cx,
                        y = cy,
                        vx = vx,
                        vy = vy,
                        color = colors[Random.nextInt(colors.size)],
                        width = pWidth,
                        height = pHeight,
                        rotation = Random.nextFloat() * 360f,
                        rotationSpeed = Random.nextFloat() * 20f - 10f
                    )
                )
            }

            animator?.cancel()
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 2400
                interpolator = DecelerateInterpolator(0.8f)
                addUpdateListener { anim ->
                    val progress = anim.animatedFraction
                    val gravity = 1.4f

                    for (p in particles) {
                        p.x += p.vx
                        p.y += p.vy
                        p.vy += gravity
                        p.vx *= 0.98f
                        p.rotation += p.rotationSpeed

                        if (progress > 0.6f) {
                            p.alpha = ((1f - (progress - 0.6f) / 0.4f) * 255).toInt().coerceIn(0, 255)
                        }
                    }
                    invalidate()
                }
                start()
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (p in particles) {
            paint.color = p.color
            paint.alpha = p.alpha

            canvas.save()
            canvas.translate(p.x, p.y)
            canvas.rotate(p.rotation)

            if (p.isCircle) {
                canvas.drawCircle(0f, 0f, p.width / 2, paint)
            } else {
                val rect = RectF(-p.width / 2, -p.height / 2, p.width / 2, p.height / 2)
                canvas.drawRoundRect(rect, 4f, 4f, paint)
            }
            canvas.restore()
        }
    }

    companion object {
        fun show(parent: ViewGroup) {
            val context = parent.context
            val confetti = ConfettiView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            parent.addView(confetti)
            confetti.burst()

            HapticHelper.performVictory(context)
            SoundHelper.playVictorySound(context)

            parent.postDelayed({
                try {
                    parent.removeView(confetti)
                } catch (_: Exception) {}
            }, 2600)
        }
    }
}
