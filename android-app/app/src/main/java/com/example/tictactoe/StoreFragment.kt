package com.example.tictactoe

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.tictactoe.databinding.FragmentStoreBinding
import com.example.tictactoe.network.ApiClient
import com.example.tictactoe.network.StoreBuyRequest
import com.example.tictactoe.network.StoreBuyResponse
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class StoreFragment : Fragment() {

    private var _binding: FragmentStoreBinding? = null
    private val binding get() = _binding!!

    enum class StoreCategory {
        CHESS,
        CHECKERS,
        EMOTES,
        THEMES
    }

    private var currentCategory = StoreCategory.CHESS
    private var subTabIndex = 0 // 0 = Boards, 1 = Pieces

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStoreBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupListeners()
        updateCoinBalance()
        renderCategory(StoreCategory.CHESS)

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                findNavController().navigateUp()
            }
        })
    }

    override fun onResume() {
        super.onResume()
        updateCoinBalance()
        renderCurrentContent()
    }

    private fun updateCoinBalance() {
        val prefs = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
        val coins = prefs.getInt("coins", 0)
        binding.tvStoreCoins.text = "🪙 $coins"
    }

    private fun setupListeners() {
        binding.btnBackStore.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnCatChess.setOnClickListener {
            renderCategory(StoreCategory.CHESS)
        }

        binding.btnCatCheckers.setOnClickListener {
            renderCategory(StoreCategory.CHECKERS)
        }

        binding.btnCatEmotes.setOnClickListener {
            renderCategory(StoreCategory.EMOTES)
        }

        binding.btnCatThemes.setOnClickListener {
            renderCategory(StoreCategory.THEMES)
        }

        binding.btnSubTab1.setOnClickListener {
            if (subTabIndex != 0) {
                subTabIndex = 0
                updateSubTabButtons()
                renderCurrentContent()
            }
        }

        binding.btnSubTab2.setOnClickListener {
            if (subTabIndex != 1) {
                subTabIndex = 1
                updateSubTabButtons()
                renderCurrentContent()
            }
        }
    }

    private fun renderCategory(category: StoreCategory) {
        currentCategory = category
        subTabIndex = 0

        // Highlight Active Category Tab
        val activeBg = col(R.color.accent_cyan)
        val inactiveBg = col(R.color.card_surface)
        val activeText = col(R.color.on_accent)
        val inactiveText = col(R.color.text_muted)

        binding.btnCatChess.setBackgroundColor(if (category == StoreCategory.CHESS) activeBg else inactiveBg)
        binding.btnCatChess.setTextColor(if (category == StoreCategory.CHESS) activeText else inactiveText)

        binding.btnCatCheckers.setBackgroundColor(if (category == StoreCategory.CHECKERS) activeBg else inactiveBg)
        binding.btnCatCheckers.setTextColor(if (category == StoreCategory.CHECKERS) activeText else inactiveText)

        binding.btnCatEmotes.setBackgroundColor(if (category == StoreCategory.EMOTES) activeBg else inactiveBg)
        binding.btnCatEmotes.setTextColor(if (category == StoreCategory.EMOTES) activeText else inactiveText)

        binding.btnCatThemes.setBackgroundColor(if (category == StoreCategory.THEMES) activeBg else inactiveBg)
        binding.btnCatThemes.setTextColor(if (category == StoreCategory.THEMES) activeText else inactiveText)

        // Subtabs Visibility and Labels
        when (category) {
            StoreCategory.CHESS -> {
                binding.layoutSubTabs.visibility = View.VISIBLE
                binding.btnSubTab1.text = "🏁 Doska Ranglari"
                binding.btnSubTab2.text = "♟️ Toshlar Stillari"
            }
            StoreCategory.CHECKERS -> {
                binding.layoutSubTabs.visibility = View.VISIBLE
                binding.btnSubTab1.text = "🏁 Shashka Doskalari"
                binding.btnSubTab2.text = "🔴 Shashka Toshlari"
            }
            StoreCategory.EMOTES, StoreCategory.THEMES -> {
                binding.layoutSubTabs.visibility = View.GONE
            }
        }

        updateSubTabButtons()
        renderCurrentContent()
    }

    private fun col(id: Int): Int = ContextCompat.getColor(requireContext(), id)

    private fun updateSubTabButtons() {
        val activeBg = col(R.color.tab_active_bg)
        val inactiveBg = Color.TRANSPARENT
        val activeText = col(R.color.text_color)
        val inactiveText = col(R.color.text_muted)

        binding.btnSubTab1.setBackgroundColor(if (subTabIndex == 0) activeBg else inactiveBg)
        binding.btnSubTab1.setTextColor(if (subTabIndex == 0) activeText else inactiveText)

        binding.btnSubTab2.setBackgroundColor(if (subTabIndex == 1) activeBg else inactiveBg)
        binding.btnSubTab2.setTextColor(if (subTabIndex == 1) activeText else inactiveText)
    }

    private fun renderCurrentContent() {
        binding.layoutStoreItems.removeAllViews()

        when (currentCategory) {
            StoreCategory.CHESS -> {
                if (subTabIndex == 0) renderChessBoards() else renderChessPieces()
            }
            StoreCategory.CHECKERS -> {
                if (subTabIndex == 0) renderCheckersBoards() else renderCheckersPieces()
            }
            StoreCategory.EMOTES -> {
                renderEmotePacks()
            }
            StoreCategory.THEMES -> {
                renderGameThemes()
            }
        }
    }

    // 1. Chess Boards
    private fun renderChessBoards() {
        val currentEquipped = ChessThemeManager.getEquippedBoardTheme(requireContext())
        for (theme in ChessThemeManager.BOARD_THEMES) {
            val isUnlocked = ChessThemeManager.isBoardUnlocked(requireContext(), theme.id)
            val isEquipped = (theme.id == currentEquipped.id)

            val card = createItemCard(
                title = theme.name,
                cost = theme.cost,
                isUnlocked = isUnlocked,
                isEquipped = isEquipped,
                previewView = createCheckerboardPreview(theme.lightColor, theme.darkColor),
                onEquip = {
                    ChessThemeManager.equipBoardTheme(requireContext(), theme.id)
                    renderCurrentContent()
                },
                onBuy = {
                    ChessThemeManager.buyBoardTheme(requireContext(), theme) { success, msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        if (success) {
                            HapticHelper.performVictory(requireContext())
                            updateCoinBalance()
                            renderCurrentContent()
                        }
                    }
                }
            )
            binding.layoutStoreItems.addView(card)
        }
    }

    // 2. Chess Pieces
    private fun renderChessPieces() {
        val currentEquipped = ChessThemeManager.getEquippedPieceSkin(requireContext())
        for (skin in ChessThemeManager.PIECE_SKINS) {
            val isUnlocked = ChessThemeManager.isPieceSkinUnlocked(requireContext(), skin.id)
            val isEquipped = (skin.id == currentEquipped.id)

            val preview = TextView(requireContext()).apply {
                text = "♚ ♞"
                textSize = 22f
                setTextColor(skin.whiteColor)
                setShadowLayer(4f, 0f, 0f, skin.whiteStrokeColor)
                layoutParams = LinearLayout.LayoutParams(40.dpToPx(), LinearLayout.LayoutParams.WRAP_CONTENT)
            }

            val card = createItemCard(
                title = skin.name,
                cost = skin.cost,
                isUnlocked = isUnlocked,
                isEquipped = isEquipped,
                previewView = preview,
                onEquip = {
                    ChessThemeManager.equipPieceSkin(requireContext(), skin.id)
                    renderCurrentContent()
                },
                onBuy = {
                    ChessThemeManager.buyPieceSkin(requireContext(), skin) { success, msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        if (success) {
                            HapticHelper.performVictory(requireContext())
                            updateCoinBalance()
                            renderCurrentContent()
                        }
                    }
                }
            )
            binding.layoutStoreItems.addView(card)
        }
    }

    // 3. Checkers Boards
    private fun renderCheckersBoards() {
        val currentEquipped = CheckersThemeManager.getEquippedBoardTheme(requireContext())
        for (theme in CheckersThemeManager.BOARD_THEMES) {
            val isUnlocked = CheckersThemeManager.isBoardUnlocked(requireContext(), theme.id)
            val isEquipped = (theme.id == currentEquipped.id)

            val card = createItemCard(
                title = theme.name,
                cost = theme.cost,
                isUnlocked = isUnlocked,
                isEquipped = isEquipped,
                previewView = createCheckerboardPreview(theme.lightColor, theme.darkColor),
                onEquip = {
                    CheckersThemeManager.equipBoardTheme(requireContext(), theme.id)
                    renderCurrentContent()
                },
                onBuy = {
                    CheckersThemeManager.buyBoardTheme(requireContext(), theme) { success, msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        if (success) {
                            HapticHelper.performVictory(requireContext())
                            updateCoinBalance()
                            renderCurrentContent()
                        }
                    }
                }
            )
            binding.layoutStoreItems.addView(card)
        }
    }

    // 4. Checkers Pieces
    private fun renderCheckersPieces() {
        val currentEquipped = CheckersThemeManager.getEquippedPieceSkin(requireContext())
        for (skin in CheckersThemeManager.PIECE_SKINS) {
            val isUnlocked = CheckersThemeManager.isPieceSkinUnlocked(requireContext(), skin.id)
            val isEquipped = (skin.id == currentEquipped.id)

            val preview = TextView(requireContext()).apply {
                text = "🔴 ⚫"
                textSize = 20f
                layoutParams = LinearLayout.LayoutParams(40.dpToPx(), LinearLayout.LayoutParams.WRAP_CONTENT)
            }

            val card = createItemCard(
                title = skin.name,
                cost = skin.cost,
                isUnlocked = isUnlocked,
                isEquipped = isEquipped,
                previewView = preview,
                onEquip = {
                    CheckersThemeManager.equipPieceSkin(requireContext(), skin.id)
                    renderCurrentContent()
                },
                onBuy = {
                    CheckersThemeManager.buyPieceSkin(requireContext(), skin) { success, msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        if (success) {
                            HapticHelper.performVictory(requireContext())
                            updateCoinBalance()
                            renderCurrentContent()
                        }
                    }
                }
            )
            binding.layoutStoreItems.addView(card)
        }
    }

    // 5. Emote Packs
    private fun renderEmotePacks() {
        val packs = listOf(
            Triple("emote_royal", "👑 Qirollik To'plami (👑 💎 🏆 ⚡)", 150),
            Triple("emote_fire", "🔥 Olovli To'plam (🔥 💥 🚀 💣)", 150),
            Triple("emote_funny", "🎭 Hazil & Reaksiya (😂 😎 🤔 😱)", 150),
            Triple("emote_victory", "💀 G'alaba & Provokatsiya (💀 👻 🤡 🫡)", 150)
        )

        val prefs = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)

        for (pack in packs) {
            val isUnlocked = prefs.getBoolean("unlocked_${pack.first}", false)

            val preview = TextView(requireContext()).apply {
                text = pack.second.substringAfter("(").substringBefore(")")
                textSize = 18f
                layoutParams = LinearLayout.LayoutParams(60.dpToPx(), LinearLayout.LayoutParams.WRAP_CONTENT)
            }

            val card = createItemCard(
                title = pack.second.substringBefore("(").trim(),
                cost = pack.third,
                isUnlocked = isUnlocked,
                isEquipped = isUnlocked,
                previewView = preview,
                onEquip = {},
                onBuy = {
                    buyGenericItem(pack.first, pack.second, pack.third)
                }
            )
            binding.layoutStoreItems.addView(card)
        }
    }

    // 6. Game Themes
    private fun renderGameThemes() {
        val themes = listOf(
            Triple("theme_tictactoe_neon", "❌⭕ Tic-Tac-Toe Neon Glow", 120),
            Triple("theme_dots_cyber", "📦 Dots & Boxes Dark Cyber", 120),
            Triple("theme_2048_vintage", "🔢 2048 Classic Vintage", 120)
        )

        val prefs = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)

        for (item in themes) {
            val isUnlocked = prefs.getBoolean("unlocked_${item.first}", false)

            val preview = TextView(requireContext()).apply {
                text = "🎨"
                textSize = 24f
                layoutParams = LinearLayout.LayoutParams(40.dpToPx(), LinearLayout.LayoutParams.WRAP_CONTENT)
            }

            val card = createItemCard(
                title = item.second,
                cost = item.third,
                isUnlocked = isUnlocked,
                isEquipped = isUnlocked,
                previewView = preview,
                onEquip = {},
                onBuy = {
                    buyGenericItem(item.first, item.second, item.third)
                }
            )
            binding.layoutStoreItems.addView(card)
        }
    }

    private fun buyGenericItem(key: String, name: String, cost: Int) {
        val prefs = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
        val currentCoins = prefs.getInt("coins", 0)
        val userId = prefs.getInt("user_id", -1)

        if (currentCoins < cost) {
            Toast.makeText(context, "Mablag' yetarli emas! Sizda $currentCoins 🪙 bor.", Toast.LENGTH_SHORT).show()
            return
        }

        prefs.edit()
            .putInt("coins", currentCoins - cost)
            .putBoolean("unlocked_$key", true)
            .apply()

        if (userId != -1) {
            ApiClient.instance.buyItem(StoreBuyRequest(userId, key, cost)).enqueue(object : Callback<StoreBuyResponse> {
                override fun onResponse(call: Call<StoreBuyResponse>, response: Response<StoreBuyResponse>) {
                    if (!isAdded || _binding == null) return
                }
                override fun onFailure(call: Call<StoreBuyResponse>, t: Throwable) {
                    if (!isAdded || _binding == null) return
                    t.printStackTrace()
                    context?.let { android.widget.Toast.makeText(it, "Tarmoq xatosi!", android.widget.Toast.LENGTH_SHORT).show() }
                }
            })
        }

        HapticHelper.performVictory(requireContext())
        Toast.makeText(context, "🎉 $name muvaffaqiyatli sotib olindi!", Toast.LENGTH_SHORT).show()
        updateCoinBalance()
        renderCurrentContent()
    }

    private fun createCheckerboardPreview(lightColor: Int, darkColor: Int): LinearLayout {
        val swatch = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(38.dpToPx(), 38.dpToPx())
        }
        val row1 = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 19.dpToPx())
        }
        val s1 = FrameLayout(requireContext()).apply {
            setBackgroundColor(lightColor)
            layoutParams = LinearLayout.LayoutParams(19.dpToPx(), 19.dpToPx())
        }
        val s2 = FrameLayout(requireContext()).apply {
            setBackgroundColor(darkColor)
            layoutParams = LinearLayout.LayoutParams(19.dpToPx(), 19.dpToPx())
        }
        row1.addView(s1); row1.addView(s2)

        val row2 = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 19.dpToPx())
        }
        val s3 = FrameLayout(requireContext()).apply {
            setBackgroundColor(darkColor)
            layoutParams = LinearLayout.LayoutParams(19.dpToPx(), 19.dpToPx())
        }
        val s4 = FrameLayout(requireContext()).apply {
            setBackgroundColor(lightColor)
            layoutParams = LinearLayout.LayoutParams(19.dpToPx(), 19.dpToPx())
        }
        row2.addView(s3); row2.addView(s4)

        swatch.addView(row1)
        swatch.addView(row2)
        return swatch
    }

    private fun createItemCard(
        title: String,
        cost: Int,
        isUnlocked: Boolean,
        isEquipped: Boolean,
        previewView: View,
        onEquip: () -> Unit,
        onBuy: () -> Unit
    ): CardView {
        val card = CardView(requireContext()).apply {
            radius = 16.dpToPx().toFloat()
            cardElevation = 0f
            setCardBackgroundColor(col(R.color.card_background))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 10.dpToPx()
            }
        }

        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(14.dpToPx(), 14.dpToPx(), 14.dpToPx(), 14.dpToPx())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        layout.addView(previewView)

        // Info Text
        val infoLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = 12.dpToPx()
            }
        }
        val nameTv = TextView(requireContext()).apply {
            text = title
            textSize = 13f
            setTextColor(col(R.color.text_color))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val costTv = TextView(requireContext()).apply {
            text = if (cost == 0) "BEPUL" else "🪙 $cost"
            textSize = 11f
            setTextColor(if (cost == 0) col(R.color.accent_green) else col(R.color.accent_gold))
        }
        infoLayout.addView(nameTv)
        infoLayout.addView(costTv)
        layout.addView(infoLayout)

        // Action Button
        val actionBtn = Button(requireContext()).apply {
            textSize = 11f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, 38.dpToPx())

            when {
                isEquipped -> {
                    text = "✓ O'RNATILGAN"
                    setTextColor(col(R.color.accent_cyan))
                    setBackgroundColor(col(R.color.pill_cyan_bg))
                    isEnabled = false
                }
                isUnlocked -> {
                    text = "O'RNATISH"
                    setTextColor(col(R.color.on_accent))
                    setBackgroundColor(col(R.color.accent_cyan))
                    setThrottleClickListener {
                        HapticHelper.performClick(requireContext())
                        onEquip()
                        Toast.makeText(context, "$title o'rnatildi!", Toast.LENGTH_SHORT).show()
                    }
                }
                else -> {
                    text = "SOTIB OLISH"
                    setTextColor(col(R.color.on_accent))
                    setBackgroundColor(col(R.color.accent_gold))
                    setThrottleClickListener {
                        onBuy()
                    }
                }
            }
        }
        layout.addView(actionBtn)

        card.addView(layout)
        return card
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
