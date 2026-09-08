package com.example.tictactoe

import android.content.Context
import android.content.SharedPreferences
import com.example.tictactoe.repository.EconomyRepository

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
        return EconomyRepository.getCachedBalance()
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

    fun addCoins(context: Context, amount: Int) {
        val cur = EconomyRepository.getCachedBalance()
        EconomyRepository.saveBalance(cur + amount)
    }

    /**
     * Refresh the authoritative balance from the server
     */
    fun refreshBalance(onComplete: ((balance: Int) -> Unit)? = null) {
        EconomyRepository.refreshProfile { success, balance, _, _ ->
            onComplete?.invoke(balance)
        }
    }

    /**
     * Records local game statistics and synchronizes with server
     */
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

        // Estimated display values while server confirms
        val (coins, xp) = when {
            customCoins >= 0 && customXp >= 0 -> Pair(customCoins, customXp)
            result == GameResult.WIN -> if (isOnline) Pair(50, 100) else Pair(25, 50)
            result == GameResult.DRAW -> if (isOnline) Pair(20, 40) else Pair(10, 20)
            result == GameResult.LOSS -> if (isOnline) Pair(10, 20) else Pair(5, 10)
            result == GameResult.SOLO_COMPLETE -> Pair(25, 45)
            else -> Pair(15, 30)
        }

        val curCoins = EconomyRepository.getCachedBalance()
        val curXp = p.getInt("xp", 0)
        val curLevel = p.getInt("level", 1)
        val curWins = p.getInt("wins", 0)
        val curLosses = p.getInt("losses", 0)
        val curDraws = p.getInt("draws", 0)

        val newCoins = curCoins + coins
        val newXp = curXp + xp
        val calculatedLevel = LevelHelper.levelForXp(newXp)
        val leveledUp = calculatedLevel > curLevel

        val editor = p.edit()
            .putInt("xp", newXp)
            .putInt("level", calculatedLevel)

        when (result) {
            GameResult.WIN -> editor.putInt("wins", curWins + 1)
            GameResult.LOSS -> editor.putInt("losses", curLosses + 1)
            GameResult.DRAW -> editor.putInt("draws", curDraws + 1)
            GameResult.SOLO_COMPLETE -> editor.putInt("wins", curWins + 1)
        }
        editor.apply()

        // Sync local cache
        EconomyRepository.saveBalance(newCoins)

        // Sync with QuestManager
        val isWinBool = (result == GameResult.WIN || result == GameResult.SOLO_COMPLETE)
        QuestManager.recordGamePlayed(context, gameKey, isOnline, isWinBool)

        return RewardResult(
            coinsEarned = coins,
            xpEarned = xp,
            newTotalCoins = newCoins,
            newTotalXp = newXp,
            newLevel = calculatedLevel,
            leveledUp = leveledUp
        )
    }
}
