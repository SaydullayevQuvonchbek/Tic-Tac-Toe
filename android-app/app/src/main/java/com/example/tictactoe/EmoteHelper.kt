package com.example.tictactoe

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView

object EmoteHelper {

    val EMOTES = listOf("😂", "🔥", "🤫", "😡", "👏", "😱", "😎", "💀")

    /**
     * Builds and attaches a sleek horizontal Emote Bar to any game screen
     */
    fun createEmoteBar(context: Context, onEmoteClick: (String) -> Unit): View {
        val card = CardView(context).apply {
            radius = 30f
            cardElevation = 8f
            setCardBackgroundColor(Color.parseColor("#1E293B"))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setMargins(16, 8, 16, 8)
            }
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(12, 6, 12, 6)
        }

        for (emote in EMOTES) {
            val tv = TextView(context).apply {
                text = emote
                textSize = 22f
                gravity = Gravity.CENTER
                setPadding(12, 6, 12, 6)
                background = android.util.TypedValue().let {
                    context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, it, true)
                    context.getDrawable(it.resourceId)
                }
                setOnClickListener {
                    HapticHelper.performClick(context)
                    SoundHelper.playMoveSound(context)
                    onEmoteClick(emote)
                }
            }
            container.addView(tv)
        }

        card.addView(container)
        return card
    }

    /**
     * Displays an animated floating bubble with the emote on the game screen
     */
    fun showFloatingEmote(parent: ViewGroup, emote: String, isOpponent: Boolean = false) {
        val context = parent.context
        val tv = TextView(context).apply {
            text = emote
            textSize = 54f
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = if (isOpponent) Gravity.TOP or Gravity.CENTER_HORIZONTAL else Gravity.CENTER
                topMargin = if (isOpponent) 120 else 0
            }
            scaleX = 0f
            scaleY = 0f
            alpha = 1f
        }

        parent.addView(tv)
        HapticHelper.performHeavyImpact(context)
        SoundHelper.playCaptureSound(context)

        val scaleX = ObjectAnimator.ofFloat(tv, View.SCALE_X, 0f, 1.4f, 1f).apply { duration = 400; interpolator = OvershootInterpolator() }
        val scaleY = ObjectAnimator.ofFloat(tv, View.SCALE_Y, 0f, 1.4f, 1f).apply { duration = 400; interpolator = OvershootInterpolator() }
        val translateY = ObjectAnimator.ofFloat(tv, View.TRANSLATION_Y, 0f, -160f).apply { duration = 1600; startDelay = 400 }
        val alpha = ObjectAnimator.ofFloat(tv, View.ALPHA, 1f, 0f).apply { duration = 500; startDelay = 1500 }

        AnimatorSet().apply {
            playTogether(scaleX, scaleY, translateY, alpha)
            start()
        }

        parent.postDelayed({
            try { parent.removeView(tv) } catch (_: Exception) {}
        }, 2100)
    }
}
