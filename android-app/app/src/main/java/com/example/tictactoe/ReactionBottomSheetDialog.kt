package com.example.tictactoe

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tictactoe.databinding.DialogReactionSheetBinding
import com.example.tictactoe.network.ApiClient
import com.example.tictactoe.network.StoreBuyRequest
import com.example.tictactoe.network.StoreBuyResponse
import com.google.android.material.bottomsheet.BottomSheetDialog
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ReactionBottomSheetDialog(
    context: Context,
    private val onEmoteSelected: (String) -> Unit
) : BottomSheetDialog(context) {

    private lateinit var binding: DialogReactionSheetBinding

    enum class Category { QUICK, FREE, PREMIUM, OWNED }
    private var currentCategory = Category.QUICK

    private val quickEmotes = listOf("🔥", "😂", "👏", "💀", "😎", "👑", "🏆", "💣")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DialogReactionSheetBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        setupListeners()
        renderCategory(Category.QUICK)
    }

    private fun setupUI() {
        val prefs = context.getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
        val coins = prefs.getInt("coins", 0)
        binding.tvReactionCoins.text = "🪙 $coins"

        val isMuted = prefs.getBoolean("mute_opponent_emotes", false)
        binding.switchMuteEmotes.isChecked = isMuted

        binding.switchMuteEmotes.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("mute_opponent_emotes", isChecked).apply()
            HapticHelper.performClick(context)
            val msg = if (isChecked) "🔇 Raqib reaksiyalari o'chirildi" else "🔊 Raqib reaksiyalari yoqildi"
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun col(id: Int): Int = ContextCompat.getColor(context, id)

    private fun setupListeners() {
        binding.btnTabQuick.setOnClickListener { renderCategory(Category.QUICK) }
        binding.btnTabFree.setOnClickListener { renderCategory(Category.FREE) }
        binding.btnTabPrem.setOnClickListener { renderCategory(Category.PREMIUM) }
        binding.btnTabOwned.setOnClickListener { renderCategory(Category.OWNED) }
    }

    private fun renderCategory(cat: Category) {
        currentCategory = cat

        val activeBg = col(R.color.accent_cyan)
        val inactiveBg = col(R.color.card_surface)
        val activeText = col(R.color.on_accent)
        val inactiveText = col(R.color.text_muted)

        binding.btnTabQuick.setBackgroundColor(if (cat == Category.QUICK) activeBg else inactiveBg)
        binding.btnTabQuick.setTextColor(if (cat == Category.QUICK) activeText else inactiveText)

        binding.btnTabFree.setBackgroundColor(if (cat == Category.FREE) activeBg else inactiveBg)
        binding.btnTabFree.setTextColor(if (cat == Category.FREE) activeText else inactiveText)

        binding.btnTabPrem.setBackgroundColor(if (cat == Category.PREMIUM) activeBg else inactiveBg)
        binding.btnTabPrem.setTextColor(if (cat == Category.PREMIUM) activeText else inactiveText)

        binding.btnTabOwned.setBackgroundColor(if (cat == Category.OWNED) activeBg else inactiveBg)
        binding.btnTabOwned.setTextColor(if (cat == Category.OWNED) activeText else inactiveText)

        val items = when (cat) {
            Category.QUICK -> EmoteHelper.ALL_EMOTES.filter { quickEmotes.contains(it.emoji) }
            Category.FREE -> EmoteHelper.ALL_EMOTES.filter { !it.isPremium }
            Category.PREMIUM -> EmoteHelper.ALL_EMOTES.filter { it.isPremium && it.cost < 999999 }
            Category.OWNED -> EmoteHelper.ALL_EMOTES.filter { EmoteHelper.isEmoteUnlocked(context, it) }
        }

        binding.rvEmotesGrid.adapter = EmoteGridAdapter(items)
    }

    private inner class EmoteGridAdapter(
        private val list: List<EmoteHelper.EmoteItem>
    ) : RecyclerView.Adapter<EmoteGridAdapter.Holder>() {

        inner class Holder(v: View) : RecyclerView.ViewHolder(v) {
            val tvEmoji: TextView = v.findViewById(R.id.tvEmoteEmoji)
            val tvTag: TextView = v.findViewById(R.id.tvEmoteTag)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_reaction_grid, parent, false)
            return Holder(v)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = list[position]
            holder.tvEmoji.text = item.emoji

            val isUnlocked = EmoteHelper.isEmoteUnlocked(context, item)
            when {
                !item.isPremium -> {
                    holder.tvTag.text = "BEPUL"
                    holder.tvTag.setTextColor(col(R.color.accent_cyan))
                }
                isUnlocked -> {
                    holder.tvTag.text = "OCHILGAN"
                    holder.tvTag.setTextColor(col(R.color.accent_green))
                }
                else -> {
                    holder.tvTag.text = "🪙 ${item.cost}"
                    holder.tvTag.setTextColor(col(R.color.accent_gold))
                }
            }

            holder.itemView.setOnClickListener {
                if (isUnlocked) {
                    HapticHelper.performClick(context)
                    onEmoteSelected(item.emoji)
                    dismiss()
                } else {
                    buyEmote(item)
                }
            }
        }

        override fun getItemCount() = list.size
    }

    private fun buyEmote(item: EmoteHelper.EmoteItem) {
        val prefs = context.getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
        val coins = prefs.getInt("coins", 0)
        val userId = prefs.getInt("user_id", -1)

        if (coins < item.cost) {
            Toast.makeText(context, "Mablag' yetarli emas! Sizda $coins 🪙 bor.", Toast.LENGTH_SHORT).show()
            return
        }

        val successAction = {
            val newCoins = coins - item.cost
            prefs.edit()
                .putInt("coins", newCoins)
                .putBoolean("unlocked_emote_${item.emoji}", true)
                .apply()
            HapticHelper.performVictory(context)
            Toast.makeText(context, "🎉 ${item.emoji} ochildi!", Toast.LENGTH_SHORT).show()
            binding.tvReactionCoins.text = "🪙 $newCoins"
            renderCategory(currentCategory)
        }

        if (userId != -1) {
            ApiClient.instance.buyItem(StoreBuyRequest(userId, "emote_${item.name.lowercase().replace(" ", "_")}", item.cost))
                .enqueue(object : Callback<StoreBuyResponse> {
                    override fun onResponse(call: Call<StoreBuyResponse>, response: Response<StoreBuyResponse>) {
                        if (response.isSuccessful) {
                            successAction()
                        } else {
                            context?.let { Toast.makeText(it, "Server xatosi: ${response.code()}", Toast.LENGTH_SHORT).show() }
                        }
                    }
                    override fun onFailure(call: Call<StoreBuyResponse>, t: Throwable) {
                        t.printStackTrace()
                        context?.let { Toast.makeText(it, "Tarmoq xatosi!", Toast.LENGTH_SHORT).show() }
                    }
                })
        } else {
            successAction()
        }
    }
}
