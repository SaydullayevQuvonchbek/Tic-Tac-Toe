package com.example.tictactoe

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.example.tictactoe.network.ApiClient
import com.example.tictactoe.network.StoreBuyRequest
import com.example.tictactoe.network.StoreBuyResponse
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

object EmoteHelper {

    data class EmoteItem(
        val emoji: String,
        val name: String,
        val cost: Int = 0,
        val isPremium: Boolean = false
    )

    val ALL_EMOTES = listOf(
        // Free Standard Emotes
        EmoteItem("😂", "Laugh", 0, false),
        EmoteItem("🔥", "Fire", 0, false),
        EmoteItem("🤫", "Shh", 0, false),
        EmoteItem("😡", "Angry", 0, false),
        EmoteItem("👏", "Clap", 0, false),
        EmoteItem("😱", "Shock", 0, false),
        EmoteItem("😎", "Cool", 0, false),
        EmoteItem("💀", "Dead", 0, false),

        // Premium Store Emotes
        EmoteItem("👑", "King Crown", 50, true),
        EmoteItem("💎", "Diamond", 75, true),
        EmoteItem("🚀", "Rocket Boom", 100, true),
        EmoteItem("🦄", "Mythic Unicorn", 120, true),
        EmoteItem("🐉", "Dragon Flame", 150, true),
        EmoteItem("🗿", "Sigma Chad", 100, true),
        EmoteItem("🥶", "Ice Cold", 60, true),
        EmoteItem("🤡", "Clown Flex", 50, true),
        EmoteItem("🦁", "Lion Roar", 80, true),
        EmoteItem("🏆", "Gold Trophy", 100, true),
        EmoteItem("💣", "Time Bomb", 75, true),
        EmoteItem("🧙", "Wizard Magic", 120, true),

        // Secret Impossible Troll Emote
        EmoteItem("🖕", "Fake You :xd", 999999999, true)
    )

    val EMOTES: List<String> by lazy { ALL_EMOTES.map { it.emoji } }

    fun isEmoteUnlocked(context: Context, item: EmoteItem): Boolean {
        if (!item.isPremium || item.cost == 0) return true
        val prefs = context.getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("unlocked_emote_${item.emoji}", false)
    }

    /**
     * Builds a clean, compact Quick Reaction Bar (4 quick buttons + ⋯ more button)
     */
    fun createQuickEmoteBar(
        context: Context,
        onEmoteClick: (String) -> Unit,
        onMoreClick: () -> Unit
    ): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val quickList = listOf("🔥", "😂", "👏", "💀")
        for (emote in quickList) {
            val btn = TextView(context).apply {
                text = emote
                textSize = 20f
                gravity = Gravity.CENTER
                background = ContextCompat.getDrawable(context, R.drawable.bg_arena_card_sm)
                layoutParams = LinearLayout.LayoutParams(42.dpToPx(context), 42.dpToPx(context)).apply {
                    marginEnd = 6.dpToPx(context)
                }
                setOnClickListener {
                    HapticHelper.performClick(context)
                    onEmoteClick(emote)
                }
            }
            container.addView(btn)
        }

        // ⋯ More button to open ReactionBottomSheetDialog
        val btnMore = TextView(context).apply {
            text = "⋯"
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(ContextCompat.getColor(context, R.color.accent_violet))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            background = ContextCompat.getDrawable(context, R.drawable.bg_arena_card_sm)
            layoutParams = LinearLayout.LayoutParams(42.dpToPx(context), 42.dpToPx(context))
            setOnClickListener {
                HapticHelper.performClick(context)
                onMoreClick()
            }
        }
        container.addView(btnMore)

        return container
    }

    /**
     * Backward-compatible helper for other fragments
     */
    fun createEmoteBar(context: Context, onEmoteClick: (String) -> Unit): View {
        return createQuickEmoteBar(
            context = context,
            onEmoteClick = onEmoteClick,
            onMoreClick = {
                ReactionBottomSheetDialog(context, onEmoteClick).show()
            }
        )
    }

    /**
     * Displays an animated floating bubble with the emote and sender name on the screen
     */
    fun showFloatingEmote(parent: ViewGroup, emote: String, isOpponent: Boolean = false, senderName: String = "") {
        val context = parent.context
        val prefs = context.getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)

        // If opponent emotes are muted, ignore opponent reactions
        if (isOpponent && prefs.getBoolean("mute_opponent_emotes", false)) {
            return
        }

        val name = if (senderName.isNotEmpty()) senderName else if (isOpponent) "Raqib" else "Siz"

        // Floating pill bubble: [ 😎 Jahongir ]
        val bubble = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(12.dpToPx(context), 8.dpToPx(context), 14.dpToPx(context), 8.dpToPx(context))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0B1424"))
                cornerRadius = 20.dpToPx(context).toFloat()
                setStroke(1.dpToPx(context), Color.parseColor("#38FFFFFF"))
            }
            elevation = 12f
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = if (isOpponent) Gravity.TOP or Gravity.CENTER_HORIZONTAL else Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                topMargin = if (isOpponent) 90.dpToPx(context) else 0
                bottomMargin = if (!isOpponent) 130.dpToPx(context) else 0
            }
            scaleX = 0f
            scaleY = 0f
            alpha = 1f
        }

        val tvEmoji = TextView(context).apply {
            text = emote
            textSize = 24f
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val tvName = TextView(context).apply {
            text = name
            textSize = 12f
            setTextColor(Color.parseColor("#CBD5E1"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = 8.dpToPx(context)
            }
        }

        bubble.addView(tvEmoji)
        bubble.addView(tvName)
        parent.addView(bubble)

        HapticHelper.performClick(context)
        SoundHelper.playCaptureSound(context)

        val scaleX = ObjectAnimator.ofFloat(bubble, View.SCALE_X, 0f, 1.15f, 1f).apply { duration = 320; interpolator = OvershootInterpolator() }
        val scaleY = ObjectAnimator.ofFloat(bubble, View.SCALE_Y, 0f, 1.15f, 1f).apply { duration = 320; interpolator = OvershootInterpolator() }
        val translateY = ObjectAnimator.ofFloat(bubble, View.TRANSLATION_Y, 0f, if (isOpponent) 40f else -60f).apply { duration = 1600; startDelay = 300 }
        val alpha = ObjectAnimator.ofFloat(bubble, View.ALPHA, 1f, 0f).apply { duration = 400; startDelay = 1800 }

        AnimatorSet().apply {
            playTogether(scaleX, scaleY, translateY, alpha)
            start()
        }

        parent.postDelayed({
            try { parent.removeView(bubble) } catch (_: Exception) {}
        }, 2300)
    }

    private fun Int.dpToPx(context: Context): Int = (this * context.resources.displayMetrics.density).toInt()
}
