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
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.cardview.widget.CardView
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

    // Flat list of strings for quick lookup
    val EMOTES: List<String> by lazy { ALL_EMOTES.map { it.emoji } }

    fun isEmoteUnlocked(context: Context, item: EmoteItem): Boolean {
        if (!item.isPremium || item.cost == 0) return true
        val prefs = context.getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("unlocked_emote_${item.emoji}", false)
    }

    /**
     * Builds and attaches a scrollable horizontal Emote Bar with Free + Premium locked emojis
     */
    fun createEmoteBar(context: Context, onEmoteClick: (String) -> Unit): View {
        val scrollView = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setMargins(8, 6, 8, 6)
            }
        }

        val card = CardView(context).apply {
            radius = 32f
            cardElevation = 6f
            setCardBackgroundColor(Color.parseColor("#0F172A"))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(10, 4, 10, 4)
        }

        fun populateEmotes() {
            container.removeAllViews()
            for (item in ALL_EMOTES) {
                val isUnlocked = isEmoteUnlocked(context, item)

                val itemBox = FrameLayout(context).apply {
                    setPadding(8, 4, 8, 4)
                    background = android.util.TypedValue().let {
                        context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, it, true)
                        context.getDrawable(it.resourceId)
                    }
                }

                val tvEmoji = TextView(context).apply {
                    text = item.emoji
                    textSize = 22f
                    gravity = Gravity.CENTER
                    alpha = if (isUnlocked) 1.0f else 0.45f
                }
                itemBox.addView(tvEmoji)

                if (!isUnlocked) {
                    val tvBadge = TextView(context).apply {
                        text = "🔒"
                        textSize = 10f
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply {
                            gravity = Gravity.BOTTOM or Gravity.END
                        }
                    }
                    itemBox.addView(tvBadge)
                }

                itemBox.setOnClickListener {
                    if (isUnlocked) {
                        HapticHelper.performClick(context)
                        SoundHelper.playMoveSound(context)
                        onEmoteClick(item.emoji)
                    } else {
                        showUnlockDialog(context, item) {
                            populateEmotes()
                            onEmoteClick(item.emoji)
                        }
                    }
                }

                container.addView(itemBox)
            }
        }

        populateEmotes()
        card.addView(container)
        scrollView.addView(card)
        return scrollView
    }

    private fun showUnlockDialog(context: Context, item: EmoteItem, onUnlocked: () -> Unit) {
        val prefs = context.getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
        val coins = prefs.getInt("coins", 0)
        val userId = prefs.getInt("user_id", -1)

        if (item.cost >= 999999) {
            // Hilarious Impossible Troll Easter Egg
            AlertDialog.Builder(context)
                .setTitle("🚫 O'zingcha ayyormisan? 😜")
                .setMessage("Bu maxfiy 'Fake You' belgisi faqat afsonaviy o'yinchilarning tushiga kiradi!\n\nNarxi: 999,999,999 🪙\n\nSotib olishning umuman iloji yo'q, behuda urinma! :xd")
                .setPositiveButton("Baribir Urinish 💸") { _, _ ->
                    HapticHelper.performHeavyImpact(context)
                    SoundHelper.playCaptureSound(context)
                    Toast.makeText(context, "❌ Xatolik 404: Pul ham, asab ham yetmaydi! 🤣", Toast.LENGTH_LONG).show()
                }
                .setNegativeButton("Tushundim 😂", null)
                .show()
            return
        }

        AlertDialog.Builder(context)
            .setTitle("✨ Premium Emote: ${item.emoji} ${item.name}")
            .setMessage("Unlock this exclusive animated reaction for ${item.cost} 🪙?\n\nYour Balance: $coins 🪙")
            .setPositiveButton("Buy (${item.cost} 🪙)") { _, _ ->
                if (coins < item.cost) {
                    Toast.makeText(context, "Not enough coins! You have $coins 🪙 (Needs ${item.cost} 🪙)", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val newCoins = coins - item.cost
                prefs.edit()
                    .putInt("coins", newCoins)
                    .putBoolean("unlocked_emote_${item.emoji}", true)
                    .apply()

                HapticHelper.performVictory(context)
                SoundHelper.playRewardSound(context)
                Toast.makeText(context, "🎉 ${item.emoji} Unlocked permanently!", Toast.LENGTH_SHORT).show()

                // Sync purchase to server
                if (userId != -1) {
                    ApiClient.instance.buyItem(StoreBuyRequest(userId, "emote_${item.name.lowercase().replace(" ", "_")}", item.cost)).enqueue(object : Callback<StoreBuyResponse> {
                        override fun onResponse(call: Call<StoreBuyResponse>, response: Response<StoreBuyResponse>) {}
                        override fun onFailure(call: Call<StoreBuyResponse>, t: Throwable) {}
                    })
                }

                onUnlocked()
            }
            .setNegativeButton("Cancel", null)
            .show()
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
