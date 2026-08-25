package com.example.tictactoe

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object QuestManager {

    private const val PREFS_NAME = "TicTacToePrefs"

    data class Quest(
        val id: String,
        val title: String,
        val currentProgress: Int,
        val target: Int,
        val coinReward: Int,
        val xpReward: Int,
        val isClaimed: Boolean
    ) {
        val isCompleted: Boolean get() = currentProgress >= target
    }

    private fun getTodayDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    fun getDailyQuests(context: Context): List<Quest> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val today = getTodayDateString()
        val lastDate = prefs.getString("last_quest_date", "")

        if (today != lastDate) {
            // Reset daily quests for new day
            prefs.edit()
                .putString("last_quest_date", today)
                .putInt("quest_1_progress", 0)
                .putBoolean("quest_1_claimed", false)
                .putInt("quest_2_progress", 0)
                .putBoolean("quest_2_claimed", false)
                .putInt("quest_3_progress", 0)
                .putBoolean("quest_3_claimed", false)
                .apply()
        }

        val q1Progress = prefs.getInt("quest_1_progress", 0)
        val q1Claimed = prefs.getBoolean("quest_1_claimed", false)

        val q2Progress = prefs.getInt("quest_2_progress", 0)
        val q2Claimed = prefs.getBoolean("quest_2_claimed", false)

        val q3Progress = prefs.getInt("quest_3_progress", 0)
        val q3Claimed = prefs.getBoolean("quest_3_claimed", false)

        return listOf(
            Quest("q1", "🎯 Gomoku yoki Shashkada 2 ta o'yin o'ynash", q1Progress, 2, 60, 100, q1Claimed),
            Quest("q2", "💧 Water Sort da 2 ta yangi bosqichni yutish", q2Progress, 2, 50, 80, q2Claimed),
            Quest("q3", "🌐 Online xonada do'st bilan 1 marta g'alaba qozonish", q3Progress, 1, 100, 150, q3Claimed)
        )
    }

    fun recordGamePlayed(context: Context, gameType: String, isOnline: Boolean, isWin: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        getDailyQuests(context) // ensures date reset

        if (gameType == "gomoku" || gameType == "checkers") {
            val cur = prefs.getInt("quest_1_progress", 0)
            prefs.edit().putInt("quest_1_progress", (cur + 1).coerceAtMost(2)).apply()
        }

        if (gameType == "water_sort" && isWin) {
            val cur = prefs.getInt("quest_2_progress", 0)
            prefs.edit().putInt("quest_2_progress", (cur + 1).coerceAtMost(2)).apply()
        }

        if (isOnline && isWin) {
            val cur = prefs.getInt("quest_3_progress", 0)
            prefs.edit().putInt("quest_3_progress", (cur + 1).coerceAtMost(1)).apply()
        }
    }

    fun claimQuest(context: Context, questId: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val quests = getDailyQuests(context)
        val quest = quests.firstOrNull { it.id == questId } ?: return false

        if (!quest.isCompleted || quest.isClaimed) return false

        val keyClaimed = when (questId) {
            "q1" -> "quest_1_claimed"
            "q2" -> "quest_2_claimed"
            else -> "quest_3_claimed"
        }

        val curCoins = prefs.getInt("coins", 0)
        val curXp = prefs.getInt("xp", 0)

        prefs.edit()
            .putBoolean(keyClaimed, true)
            .putInt("coins", curCoins + quest.coinReward)
            .putInt("xp", curXp + quest.xpReward)
            .apply()

        return true
    }

    fun getLeagueTier(xp: Int): Pair<String, String> {
        return when {
            xp >= 3500 -> Pair("👑 Master League", "#F59E0B")
            xp >= 1800 -> Pair("💎 Diamond League", "#06B6D4")
            xp >= 800  -> Pair("🥇 Gold League", "#EAB308")
            xp >= 300  -> Pair("🥈 Silver League", "#94A3B8")
            else       -> Pair("🥉 Bronze League", "#CD7F32")
        }
    }
}
