package com.example.tictactoe

import android.content.Context
import android.graphics.Color
import com.example.tictactoe.network.ApiClient
import com.example.tictactoe.network.StoreBuyRequest
import com.example.tictactoe.network.StoreBuyResponse
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

object ChessThemeManager {

    private const val PREFS_NAME = "TicTacToePrefs"

    data class BoardTheme(
        val id: String,
        val name: String,
        val cost: Int,
        val lightColor: Int,
        val darkColor: Int,
        val selectedColor: Int,
        val lastMoveColor: Int,
        val checkColor: Int = Color.parseColor("#EF4444")
    )

    data class PieceSkin(
        val id: String,
        val name: String,
        val cost: Int,
        val whiteColor: Int,
        val whiteStrokeColor: Int,
        val blackColor: Int,
        val blackStrokeColor: Int? = null
    )

    val BOARD_THEMES = listOf(
        BoardTheme(
            id = "classic",
            name = "🌿 Classic Tournament",
            cost = 0,
            lightColor = Color.parseColor("#EEEED2"),
            darkColor = Color.parseColor("#769656"),
            selectedColor = Color.parseColor("#B8D434"),
            lastMoveColor = Color.parseColor("#F7EC59")
        ),
        BoardTheme(
            id = "wood",
            name = "🪵 Walnut Wood",
            cost = 500,
            lightColor = Color.parseColor("#F0D9B5"),
            darkColor = Color.parseColor("#B58863"),
            selectedColor = Color.parseColor("#D4A373"),
            lastMoveColor = Color.parseColor("#E9C46A")
        ),
        BoardTheme(
            id = "midnight",
            name = "🌌 Midnight Navy",
            cost = 1000,
            lightColor = Color.parseColor("#DEE3E6"),
            darkColor = Color.parseColor("#8CA2AD"),
            selectedColor = Color.parseColor("#64748B"),
            lastMoveColor = Color.parseColor("#38BDF8")
        ),
        BoardTheme(
            id = "gold",
            name = "👑 Royal Gold",
            cost = 2500,
            lightColor = Color.parseColor("#FDF6E2"),
            darkColor = Color.parseColor("#B7950B"),
            selectedColor = Color.parseColor("#F59E0B"),
            lastMoveColor = Color.parseColor("#FDE047")
        ),
        BoardTheme(
            id = "cyberpunk",
            name = "⚡ Cyberpunk Neon",
            cost = 3500,
            lightColor = Color.parseColor("#22D3EE"),
            darkColor = Color.parseColor("#7C3AED"),
            selectedColor = Color.parseColor("#EC4899"),
            lastMoveColor = Color.parseColor("#A855F7")
        ),
        BoardTheme(
            id = "marble",
            name = "🏛️ Luxury Marble",
            cost = 5000,
            lightColor = Color.parseColor("#F8FAFC"),
            darkColor = Color.parseColor("#334155"),
            selectedColor = Color.parseColor("#06B6D4"),
            lastMoveColor = Color.parseColor("#94A3B8")
        )
    )

    val PIECE_SKINS = listOf(
        PieceSkin(
            id = "classic",
            name = "♟️ Classic Staunton",
            cost = 0,
            whiteColor = Color.parseColor("#FFFFFF"),
            whiteStrokeColor = Color.parseColor("#0F172A"),
            blackColor = Color.parseColor("#1E293B"),
            blackStrokeColor = Color.parseColor("#F1F5F9") // High-contrast silver stroke for dark squares
        ),
        PieceSkin(
            id = "carved_wood",
            name = "🪵 Carved Walnut & Maple",
            cost = 500,
            whiteColor = Color.parseColor("#FEF3C7"),
            whiteStrokeColor = Color.parseColor("#92400E"),
            blackColor = Color.parseColor("#451A03"),
            blackStrokeColor = Color.parseColor("#FDE68A")
        ),
        PieceSkin(
            id = "crystal_sapphire",
            name = "💎 Crystal Ice & Sapphire",
            cost = 1200,
            whiteColor = Color.parseColor("#38BDF8"),
            whiteStrokeColor = Color.parseColor("#0284C7"),
            blackColor = Color.parseColor("#4338CA"),
            blackStrokeColor = Color.parseColor("#C7D2FE")
        ),
        PieceSkin(
            id = "neon_cyber",
            name = "⚡ Cyberpunk Neon Glow",
            cost = 2500,
            whiteColor = Color.parseColor("#22D3EE"),
            whiteStrokeColor = Color.parseColor("#0891B2"),
            blackColor = Color.parseColor("#EC4899"),
            blackStrokeColor = Color.parseColor("#FBCFE8")
        ),
        PieceSkin(
            id = "luxury_marble",
            name = "🏛️ Luxury Marble & Jade",
            cost = 3500,
            whiteColor = Color.parseColor("#F8FAFC"),
            whiteStrokeColor = Color.parseColor("#64748B"),
            blackColor = Color.parseColor("#064E3B"),
            blackStrokeColor = Color.parseColor("#6EE7B7")
        ),
        PieceSkin(
            id = "gold_obsidian",
            name = "👑 Royal Gold & Obsidian",
            cost = 5000,
            whiteColor = Color.parseColor("#FBBF24"),
            whiteStrokeColor = Color.parseColor("#78350F"),
            blackColor = Color.parseColor("#09090B"),
            blackStrokeColor = Color.parseColor("#F59E0B")
        )
    )

    fun isBoardUnlocked(context: Context, themeId: String): Boolean {
        if (themeId == "classic") return true
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean("unlocked_chess_board_$themeId", false)
    }

    fun isPieceSkinUnlocked(context: Context, skinId: String): Boolean {
        if (skinId == "classic") return true
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean("unlocked_chess_piece_$skinId", false)
    }

    fun getEquippedBoardTheme(context: Context): BoardTheme {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val equippedId = prefs.getString("equipped_chess_board", "classic") ?: "classic"
        return BOARD_THEMES.firstOrNull { it.id == equippedId } ?: BOARD_THEMES[0]
    }

    fun getEquippedPieceSkin(context: Context): PieceSkin {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val equippedId = prefs.getString("equipped_chess_piece", "classic") ?: "classic"
        return PIECE_SKINS.firstOrNull { it.id == equippedId } ?: PIECE_SKINS[0]
    }

    fun equipBoardTheme(context: Context, themeId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString("equipped_chess_board", themeId).apply()
    }

    fun equipPieceSkin(context: Context, skinId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString("equipped_chess_piece", skinId).apply()
    }

    fun buyBoardTheme(context: Context, theme: BoardTheme, onResult: (success: Boolean, msg: String) -> Unit) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentCoins = prefs.getInt("coins", 0)
        val userId = prefs.getInt("user_id", -1)

        if (currentCoins < theme.cost) {
            onResult(false, "Mablag' yetarli emas! Sizda $currentCoins 🪙 bor.")
            return
        }

        val newCoins = currentCoins - theme.cost
        prefs.edit()
            .putInt("coins", newCoins)
            .putBoolean("unlocked_chess_board_${theme.id}", true)
            .putString("equipped_chess_board", theme.id)
            .apply()

        if (userId != -1) {
            ApiClient.instance.buyItem(StoreBuyRequest(userId, "chess_board_${theme.id}", theme.cost))
                .enqueue(object : Callback<StoreBuyResponse> {
                    override fun onResponse(call: Call<StoreBuyResponse>, response: Response<StoreBuyResponse>) {}
                    override fun onFailure(call: Call<StoreBuyResponse>, t: Throwable) {
    t.printStackTrace()
}
                })
        }

        onResult(true, "🎉 ${theme.name} muvaffaqiyatli sotib olindi va o'rnatildi!")
    }

    fun buyPieceSkin(context: Context, skin: PieceSkin, onResult: (success: Boolean, msg: String) -> Unit) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentCoins = prefs.getInt("coins", 0)
        val userId = prefs.getInt("user_id", -1)

        if (currentCoins < skin.cost) {
            onResult(false, "Mablag' yetarli emas! Sizda $currentCoins 🪙 bor.")
            return
        }

        val newCoins = currentCoins - skin.cost
        prefs.edit()
            .putInt("coins", newCoins)
            .putBoolean("unlocked_chess_piece_${skin.id}", true)
            .putString("equipped_chess_piece", skin.id)
            .apply()

        if (userId != -1) {
            ApiClient.instance.buyItem(StoreBuyRequest(userId, "chess_piece_${skin.id}", skin.cost))
                .enqueue(object : Callback<StoreBuyResponse> {
                    override fun onResponse(call: Call<StoreBuyResponse>, response: Response<StoreBuyResponse>) {}
                    override fun onFailure(call: Call<StoreBuyResponse>, t: Throwable) {
    t.printStackTrace()
}
                })
        }

        onResult(true, "🎉 ${skin.name} muvaffaqiyatli sotib olindi va o'rnatildi!")
    }
}
