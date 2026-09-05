package com.example.tictactoe

import android.content.Context
import android.content.SharedPreferences
import com.example.tictactoe.network.ApiClient
import com.example.tictactoe.network.MatchResultRequest
import com.example.tictactoe.network.MatchResultResponse
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

object GameEconomyManager {

    private const val PREFS_NAME = "TicTacToePrefs"

    enum class GameResult {
        WIN,
        DRAW,
        LOSS,
        SOLO_COMPLETE
    }

    data class RewardResult(
        val coinsEarned: Int,
        val xpEarned: Int,
        val newTotalCoins: Int,
        val newTotalXp: Int,
        val newLevel: Int,
        val leveledUp: Boolean
    )

    private fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getCoins(context: Context): Int {
        return prefs(context).getInt("coins", 0)
    }

    fun getXp(context: Context): Int {
        return prefs(context).getInt("xp", 0)
    }

    fun getLevel(context: Context): Int {
        return prefs(context).getInt("level", 1)
    }

    fun recordGamePlay(context: Context, gameKey: String) {
        val p = prefs(context)
        val cur = p.getInt("play_count_${gameKey.lowercase()}", 0)
        p.edit()
            .putInt("play_count_${gameKey.lowercase()}", cur + 1)
            .putLong("last_played_${gameKey.lowercase()}", System.currentTimeMillis())
            .apply()
    }

    fun getGamePlayCount(context: Context, gameKey: String): Int {
        return prefs(context).getInt("play_count_${gameKey.lowercase()}", 0)
    }

    fun rewardMatchResult(
        context: Context,
        gameKey: String,
        isOnline: Boolean,
        result: GameResult,
        customCoins: Int = -1,
        customXp: Int = -1
    ): RewardResult {
        val p = prefs(context)
        recordGamePlay(context, gameKey)

        // Calculate coins and XP
        val (coins, xp) = when {
            customCoins >= 0 && customXp >= 0 -> Pair(customCoins, customXp)
            result == GameResult.WIN -> {
                if (isOnline) Pair(50, 100) else Pair(25, 50)
            }
            result == GameResult.DRAW -> {
                if (isOnline) Pair(20, 40) else Pair(10, 20)
            }
            result == GameResult.LOSS -> {
                if (isOnline) Pair(10, 20) else Pair(5, 10)
            }
            result == GameResult.SOLO_COMPLETE -> {
                Pair(25, 45)
            }
            else -> Pair(15, 30)
        }

        val curCoins = p.getInt("coins", 0)
        val curXp = p.getInt("xp", 0)
        val curLevel = p.getInt("level", 1)
        val curWins = p.getInt("wins", 0)
        val curLosses = p.getInt("losses", 0)
        val curDraws = p.getInt("draws", 0)

        val newCoins = (curCoins + coins).coerceAtLeast(0)
        val newXp = (curXp + xp).coerceAtLeast(0)
        val calculatedLevel = LevelHelper.levelForXp(newXp)
        val leveledUp = calculatedLevel > curLevel

        val editor = p.edit()
            .putInt("coins", newCoins)
            .putInt("xp", newXp)
            .putInt("level", calculatedLevel)

        when (result) {
            GameResult.WIN -> editor.putInt("wins", curWins + 1)
            GameResult.LOSS -> editor.putInt("losses", curLosses + 1)
            GameResult.DRAW -> editor.putInt("draws", curDraws + 1)
            GameResult.SOLO_COMPLETE -> editor.putInt("wins", curWins + 1)
        }
        editor.apply()

        // Sync with QuestManager
        val isWinBool = (result == GameResult.WIN || result == GameResult.SOLO_COMPLETE)
        QuestManager.recordGamePlayed(context, gameKey, isOnline, isWinBool)

        // Sync with server in background
        val userId = p.getInt("user_id", -1)
        if (userId != -1) {
            val serverResultStr = when (result) {
                GameResult.WIN, GameResult.SOLO_COMPLETE -> "win"
                GameResult.DRAW -> "draw"
                GameResult.LOSS -> "loss"
            }
            ApiClient.instance.matchResult(MatchResultRequest(userId, serverResultStr)).enqueue(object : Callback<MatchResultResponse> {
                override fun onResponse(call: Call<MatchResultResponse>, response: Response<MatchResultResponse>) {}
                override fun onFailure(call: Call<MatchResultResponse>, t: Throwable) {}
            })
        }

        return RewardResult(
            coinsEarned = coins,
            xpEarned = xp,
            newTotalCoins = newCoins,
            newTotalXp = newXp,
            newLevel = calculatedLevel,
            leveledUp = leveledUp
        )
    }

    fun spendCoins(context: Context, amount: Int, reason: String = ""): Boolean {
        val p = prefs(context)
        val curCoins = p.getInt("coins", 0)
        if (curCoins < amount) return false

        p.edit().putInt("coins", curCoins - amount).apply()
        return true
    }

    fun addCoins(context: Context, amount: Int) {
        val p = prefs(context)
        val curCoins = p.getInt("coins", 0)
        p.edit().putInt("coins", curCoins + amount).apply()
    }
}
