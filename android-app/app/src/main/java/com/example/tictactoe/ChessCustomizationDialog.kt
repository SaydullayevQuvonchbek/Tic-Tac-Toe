package com.example.tictactoe

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView

class ChessCustomizationDialog(
    private val context: Context,
    private val onThemeChanged: () -> Unit
) {

    private val dialog = Dialog(context)
    private var isViewingBoards = true // true = Boards, false = Pieces

    fun show() {
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0F172A"))
                cornerRadius = 24.dpToPx().toFloat()
                setStroke(2.dpToPx(), Color.parseColor("#334155"))
            }
            setPadding(18.dpToPx(), 20.dpToPx(), 18.dpToPx(), 18.dpToPx())
            layoutParams = ViewGroup.LayoutParams(
                (context.resources.displayMetrics.widthPixels * 0.92).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        renderDialogContent(root)
        dialog.setContentView(root)
        dialog.show()
    }

    private fun renderDialogContent(root: LinearLayout) {
        root.removeAllViews()

        val prefs = context.getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
        val userCoins = prefs.getInt("coins", 0)

        // 1. Header & Coin Balance
        val headerLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val titleTv = TextView(context).apply {
            text = "🎨 SHAXMAT DO'KONI"
            textSize = 17f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val coinPill = TextView(context).apply {
            text = "🪙 $userCoins"
            textSize = 14f
            setTextColor(Color.parseColor("#FBBF24"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            background = context.getDrawable(R.drawable.badge_pill)
            setPadding(12.dpToPx(), 4.dpToPx(), 12.dpToPx(), 4.dpToPx())
        }

        headerLayout.addView(titleTv)
        headerLayout.addView(coinPill)
        root.addView(headerLayout)

        // 2. Tab Switcher (Boards vs Pieces)
        val tabLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 46.dpToPx()).apply {
                topMargin = 14.dpToPx()
                bottomMargin = 12.dpToPx()
            }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B"))
                cornerRadius = 14.dpToPx().toFloat()
            }
            setPadding(4.dpToPx(), 4.dpToPx(), 4.dpToPx(), 4.dpToPx())
        }

        val btnTabBoards = Button(context).apply {
            text = "🏁 Doskalar"
            textSize = 13f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            setBackgroundResource(if (isViewingBoards) R.drawable.badge_pill else android.R.color.transparent)
            setTextColor(if (isViewingBoards) Color.WHITE else Color.parseColor("#94A3B8"))
            setOnClickListener {
                if (!isViewingBoards) {
                    isViewingBoards = true
                    renderDialogContent(root)
                }
            }
        }

        val btnTabPieces = Button(context).apply {
            text = "♟️ Toshlar"
            textSize = 13f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            setBackgroundResource(if (!isViewingBoards) R.drawable.badge_pill else android.R.color.transparent)
            setTextColor(if (!isViewingBoards) Color.WHITE else Color.parseColor("#94A3B8"))
            setOnClickListener {
                if (isViewingBoards) {
                    isViewingBoards = false
                    renderDialogContent(root)
                }
            }
        }

        tabLayout.addView(btnTabBoards)
        tabLayout.addView(btnTabPieces)
        root.addView(tabLayout)

        // 3. Scrollable List of Items
        val scrollView = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (context.resources.displayMetrics.heightPixels * 0.45).toInt()
            )
        }

        val itemsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        if (isViewingBoards) {
            val currentEquipped = ChessThemeManager.getEquippedBoardTheme(context)
            for (theme in ChessThemeManager.BOARD_THEMES) {
                val isUnlocked = ChessThemeManager.isBoardUnlocked(context, theme.id)
                val isEquipped = (theme.id == currentEquipped.id)
                val card = createBoardThemeCard(theme, isUnlocked, isEquipped) {
                    renderDialogContent(root)
                    onThemeChanged()
                }
                itemsContainer.addView(card)
            }
        } else {
            val currentEquipped = ChessThemeManager.getEquippedPieceSkin(context)
            for (skin in ChessThemeManager.PIECE_SKINS) {
                val isUnlocked = ChessThemeManager.isPieceSkinUnlocked(context, skin.id)
                val isEquipped = (skin.id == currentEquipped.id)
                val card = createPieceSkinCard(skin, isUnlocked, isEquipped) {
                    renderDialogContent(root)
                    onThemeChanged()
                }
                itemsContainer.addView(card)
            }
        }

        scrollView.addView(itemsContainer)
        root.addView(scrollView)

        // 4. Close Button
        val btnClose = Button(context).apply {
            text = "YOPISH"
            textSize = 14f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#334155"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 48.dpToPx()).apply {
                topMargin = 12.dpToPx()
            }
            setOnClickListener {
                dialog.dismiss()
            }
        }
        root.addView(btnClose)
    }

    private fun createBoardThemeCard(
        theme: ChessThemeManager.BoardTheme,
        isUnlocked: Boolean,
        isEquipped: Boolean,
        onRefresh: () -> Unit
    ): CardView {
        val card = CardView(context).apply {
            radius = 14.dpToPx().toFloat()
            cardElevation = 3.dpToPx().toFloat()
            setCardBackgroundColor(Color.parseColor("#1E293B"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 10.dpToPx()
            }
        }

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(12.dpToPx(), 12.dpToPx(), 12.dpToPx(), 12.dpToPx())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        // Preview Swatch: 2x2 checkerboard square
        val swatch = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(38.dpToPx(), 38.dpToPx())
        }
        val row1 = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 19.dpToPx())
        }
        val s1 = FrameLayout(context).apply {
            setBackgroundColor(theme.lightColor)
            layoutParams = LinearLayout.LayoutParams(19.dpToPx(), 19.dpToPx())
        }
        val s2 = FrameLayout(context).apply {
            setBackgroundColor(theme.darkColor)
            layoutParams = LinearLayout.LayoutParams(19.dpToPx(), 19.dpToPx())
        }
        row1.addView(s1); row1.addView(s2)

        val row2 = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 19.dpToPx())
        }
        val s3 = FrameLayout(context).apply {
            setBackgroundColor(theme.darkColor)
            layoutParams = LinearLayout.LayoutParams(19.dpToPx(), 19.dpToPx())
        }
        val s4 = FrameLayout(context).apply {
            setBackgroundColor(theme.lightColor)
            layoutParams = LinearLayout.LayoutParams(19.dpToPx(), 19.dpToPx())
        }
        row2.addView(s3); row2.addView(s4)

        swatch.addView(row1)
        swatch.addView(row2)
        layout.addView(swatch)

        // Info Text
        val infoLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = 12.dpToPx()
            }
        }
        val nameTv = TextView(context).apply {
            text = theme.name
            textSize = 14f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val costTv = TextView(context).apply {
            text = if (theme.cost == 0) "BEPUL" else "${theme.cost} 🪙"
            textSize = 12f
            setTextColor(if (theme.cost == 0) Color.parseColor("#34D399") else Color.parseColor("#FBBF24"))
        }
        infoLayout.addView(nameTv)
        infoLayout.addView(costTv)
        layout.addView(infoLayout)

        // Action Button
        val actionBtn = Button(context).apply {
            textSize = 11f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, 36.dpToPx())

            when {
                isEquipped -> {
                    text = "✅ TANLANGAN"
                    setTextColor(Color.WHITE)
                    setBackgroundColor(Color.parseColor("#059669"))
                    isEnabled = false
                }
                isUnlocked -> {
                    text = "O'RNATISH"
                    setTextColor(Color.WHITE)
                    setBackgroundColor(Color.parseColor("#2563EB"))
                    setOnClickListener {
                        ChessThemeManager.equipBoardTheme(context, theme.id)
                        HapticHelper.performClick(context)
                        Toast.makeText(context, "${theme.name} o'rnatildi!", Toast.LENGTH_SHORT).show()
                        onRefresh()
                    }
                }
                else -> {
                    text = "SOTIB OLISH"
                    setTextColor(Color.WHITE)
                    setBackgroundColor(Color.parseColor("#D97706"))
                    setOnClickListener {
                        ChessThemeManager.buyBoardTheme(context, theme) { success, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            if (success) {
                                HapticHelper.performVictory(context)
                                onRefresh()
                            }
                        }
                    }
                }
            }
        }
        layout.addView(actionBtn)

        card.addView(layout)
        return card
    }

    private fun createPieceSkinCard(
        skin: ChessThemeManager.PieceSkin,
        isUnlocked: Boolean,
        isEquipped: Boolean,
        onRefresh: () -> Unit
    ): CardView {
        val card = CardView(context).apply {
            radius = 14.dpToPx().toFloat()
            cardElevation = 3.dpToPx().toFloat()
            setCardBackgroundColor(Color.parseColor("#1E293B"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 10.dpToPx()
            }
        }

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(12.dpToPx(), 12.dpToPx(), 12.dpToPx(), 12.dpToPx())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        // Preview Glyph: 👑 ♞
        val previewTv = TextView(context).apply {
            text = "♚ ♞"
            textSize = 22f
            setTextColor(skin.whiteColor)
            setShadowLayer(4f, 0f, 0f, skin.whiteStrokeColor)
            layoutParams = LinearLayout.LayoutParams(40.dpToPx(), LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        layout.addView(previewTv)

        // Info Text
        val infoLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = 10.dpToPx()
            }
        }
        val nameTv = TextView(context).apply {
            text = skin.name
            textSize = 14f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val costTv = TextView(context).apply {
            text = if (skin.cost == 0) "BEPUL" else "${skin.cost} 🪙"
            textSize = 12f
            setTextColor(if (skin.cost == 0) Color.parseColor("#34D399") else Color.parseColor("#FBBF24"))
        }
        infoLayout.addView(nameTv)
        infoLayout.addView(costTv)
        layout.addView(infoLayout)

        // Action Button
        val actionBtn = Button(context).apply {
            textSize = 11f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, 36.dpToPx())

            when {
                isEquipped -> {
                    text = "✅ TANLANGAN"
                    setTextColor(Color.WHITE)
                    setBackgroundColor(Color.parseColor("#059669"))
                    isEnabled = false
                }
                isUnlocked -> {
                    text = "O'RNATISH"
                    setTextColor(Color.WHITE)
                    setBackgroundColor(Color.parseColor("#2563EB"))
                    setOnClickListener {
                        ChessThemeManager.equipPieceSkin(context, skin.id)
                        HapticHelper.performClick(context)
                        Toast.makeText(context, "${skin.name} toshlari o'rnatildi!", Toast.LENGTH_SHORT).show()
                        onRefresh()
                    }
                }
                else -> {
                    text = "SOTIB OLISH"
                    setTextColor(Color.WHITE)
                    setBackgroundColor(Color.parseColor("#D97706"))
                    setOnClickListener {
                        ChessThemeManager.buyPieceSkin(context, skin) { success, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            if (success) {
                                HapticHelper.performVictory(context)
                                onRefresh()
                            }
                        }
                    }
                }
            }
        }
        layout.addView(actionBtn)

        card.addView(layout)
        return card
    }

    private fun Int.dpToPx(): Int = (this * context.resources.displayMetrics.density).toInt()
}
