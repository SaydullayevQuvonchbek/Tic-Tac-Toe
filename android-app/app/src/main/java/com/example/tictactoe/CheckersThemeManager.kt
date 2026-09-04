package com.example.tictactoe

import android.content.Context
import android.graphics.Color
import com.example.tictactoe.network.ApiClient
import com.example.tictactoe.network.StoreBuyRequest
import com.example.tictactoe.network.StoreBuyResponse
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

object CheckersThemeManager {

    private const val PREFS_NAME = "TicTacToePrefs"

    data class BoardTheme(
        val id: String,
        val name: String,
        val cost: Int,
        val lightColor: Int,
        val darkColor: Int,
        val selectedColor: Int = Color.parseColor("#38BDF8"),
        val validColor: Int = Color.parseColor("#10B981")
    )

    data class PieceSkin(
        val id: String,
        val name: String,
        val cost: Int,
        val p1Color: Int,
        val p1RingColor: Int,
        val p2Color: Int,
        val p2RingColor: Int
    )

    val BOARD_THEMES = listOf(
        BoardTheme(
            id = "classic_wood",
            name = "🪵 Classic Walnut Wood",
            cost = 0,
            lightColor = Color.parseColor("#F0D9B5"),
            darkColor = Color.parseColor("#B58863")
        ),
        BoardTheme(
            id = "emerald_forest",
            name = "🌿 Emerald Forest",
            cost = 150,
            lightColor = Color.parseColor("#E2E8F0"),
            darkColor = Color.parseColor("#065F46")
        ),
        BoardTheme(
            id = "midnight_obsidian",
            name = "🌌 Midnight Obsidian",
            cost = 200,
            lightColor = Color.parseColor("#94A3B8"),
            darkColor = Color.parseColor("#0F172A")
        ),
        BoardTheme(
            id = "lava_crimson",
            name = "🔥 Lava Crimson",
            cost = 250,
            lightColor = Color.parseColor("#FED7AA"),
            darkColor = Color.parseColor("#991B1B")
        ),
        BoardTheme(
            id = "royal_gold",
            name = "👑 Royal Gold",
            cost = 300,
            lightColor = Color.parseColor("#FEF3C7"),
            darkColor = Color.parseColor("#B45309")
        )
    )

    val PIECE_SKINS = listOf(
        PieceSkin(
            id = "classic",
            name = "🔴 Classic Crimson & Dark",
            cost = 0,
            p1Color = Color.parseColor("#EF4444"),
            p1RingColor = Color.parseColor("#FCA5A5"),
            p2Color = Color.parseColor("#1E293B"),
            p2RingColor = Color.parseColor("#E2E8F0")
        ),
        PieceSkin(
            id = "gold_platinum",
            name = "👑 Gold & Platinum",
            cost = 200,
            p1Color = Color.parseColor("#F59E0B"),
            p1RingColor = Color.parseColor("#FEF3C7"),
            p2Color = Color.parseColor("#334155"),
            p2RingColor = Color.parseColor("#CBD5E1")
        ),
        PieceSkin(
            id = "neon_cyber",
            name = "⚡ Neon Cyan & Magenta",
            cost = 250,
            p1Color = Color.parseColor("#EC4899"),
            p1RingColor = Color.parseColor("#FBCFE8"),
            p2Color = Color.parseColor("#06B6D4"),
            p2RingColor = Color.parseColor("#A5F3FC")
        ),
        PieceSkin(
            id = "carved_mahogany",
            name = "🪵 Carved Mahogany Wood",
            cost = 200,
            p1Color = Color.parseColor("#B45309"),
            p1RingColor = Color.parseColor("#FDE68A"),
            p2Color = Color.parseColor("#451A03"),
            p2RingColor = Color.parseColor("#D97706")
        )
    )

    fun isBoardUnlocked(context: Context, themeId: String): Boolean {
        if (themeId == "classic_wood") return true
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean("unlocked_checkers_board_$themeId", false)
    }

    fun isPieceSkinUnlocked(context: Context, skinId: String): Boolean {
        if (skinId == "classic") return true
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean("unlocked_checkers_piece_$skinId", false)
    }

    fun getEquippedBoardTheme(context: Context): BoardTheme {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val equippedId = prefs.getString("equipped_checkers_board", "classic_wood") ?: "classic_wood"
        return BOARD_THEMES.firstOrNull { it.id == equippedId } ?: BOARD_THEMES[0]
    }

    fun getEquippedPieceSkin(context: Context): PieceSkin {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val equippedId = prefs.getString("equipped_checkers_piece", "classic") ?: "classic"
        return PIECE_SKINS.firstOrNull { it.id == equippedId } ?: PIECE_SKINS[0]
    }

    fun equipBoardTheme(context: Context, themeId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString("equipped_checkers_board", themeId).apply()
    }

    fun equipPieceSkin(context: Context, skinId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString("equipped_checkers_piece", skinId).apply()
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
            .putBoolean("unlocked_checkers_board_${theme.id}", true)
            .putString("equipped_checkers_board", theme.id)
            .apply()

        if (userId != -1) {
            ApiClient.instance.buyItem(StoreBuyRequest(userId, "checkers_board_${theme.id}", theme.cost))
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
            .putBoolean("unlocked_checkers_piece_${skin.id}", true)
            .putString("equipped_checkers_piece", skin.id)
            .apply()

        if (userId != -1) {
            ApiClient.instance.buyItem(StoreBuyRequest(userId, "checkers_piece_${skin.id}", skin.cost))
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
