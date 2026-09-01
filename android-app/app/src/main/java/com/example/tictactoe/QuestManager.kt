package com.example.tictactoe

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.Random

object QuestManager {

    private const val PREFS_NAME = "TicTacToePrefs"

    data class QuestTemplate(
        val templateId: String,
        val title: String,
        val target: Int,
        val coinReward: Int,
        val xpReward: Int,
        val filter: (gameType: String, isOnline: Boolean, isWin: Boolean) -> Boolean
    )

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

    private val QUEST_POOL = listOf(
        QuestTemplate("durak_win", "🃏 Durakda 2 marta g'alaba qozon", 2, 80, 120) { g, _, w -> g == "durak" && w },
        QuestTemplate("durak_play", "🃏 Durakda 2 ta o'yin o'yna", 2, 60, 90) { g, _, _ -> g == "durak" },
        QuestTemplate("checkers_play", "♟️ Shashkada 2 ta o'yin o'yna", 2, 60, 100) { g, _, _ -> g == "checkers" },
        QuestTemplate("checkers_win", "♟️ Shashkada 1 ta g'alaba qozon", 1, 80, 120) { g, _, w -> g == "checkers" && w },
        QuestTemplate("online_win", "🌐 Onlayn rejimda 1 ta g'alaba qozon", 1, 100, 150) { _, o, w -> o && w },
        QuestTemplate("online_play", "👥 Do'st yoki Onlayn 2 ta o'yin o'yna", 2, 80, 130) { _, o, _ -> o },
        QuestTemplate("tictactoe_win", "❌ Tic-Tac-Toe da 3 marta yut", 3, 70, 110) { g, _, w -> (g == "tictactoe" || g == "tic_tac_toe") && w },
        QuestTemplate("water_sort", "🧪 Suv saralashda 2 ta bosqichni yut", 2, 60, 90) { g, _, w -> g == "water_sort" && w },
        QuestTemplate("game_2048", "🔢 2048 o'yinida 1 marta o'yna", 1, 50, 80) { g, _, _ -> g == "2048" },
        QuestTemplate("gomoku_win", "⚪ Gomoku (5 qator) da 1 marta yut", 1, 80, 120) { g, _, w -> g == "gomoku" && w },
        QuestTemplate("dots_boxes", "📦 Nuqtalar va Kataklar o'yinini o'yna", 2, 60, 90) { g, _, _ -> g == "dots_and_boxes" },
        QuestTemplate("any_games", "🎮 Har qanday o'yinda 4 ta raund o'yna", 4, 80, 130) { _, _, _ -> true }
    )

    private fun getTodayDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    fun getTimeUntilMidnightString(): String {
        val now = Calendar.getInstance()
        val midnight = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 24)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val diffMillis = (midnight.timeInMillis - now.timeInMillis).coerceAtLeast(0)
        val hours = (diffMillis / (1000 * 60 * 60)) % 24
        val minutes = (diffMillis / (1000 * 60)) % 60
        val seconds = (diffMillis / 1000) % 60
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    }

    private fun getTodayQuestTemplates(dateStr: String): List<QuestTemplate> {
        val seed = dateStr.hashCode().toLong()
        val random = Random(seed)
        val shuffled = QUEST_POOL.shuffled(random)
        return shuffled.take(3)
    }

    fun getDailyQuests(context: Context): List<Quest> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val today = getTodayDateString()
        val lastDate = prefs.getString("last_quest_date", "")

        val templates = getTodayQuestTemplates(today)

        if (today != lastDate) {
            val editor = prefs.edit().putString("last_quest_date", today)
            for (i in 0 until 3) {
                editor.putString("quest_${i}_tpl", templates[i].templateId)
                editor.putInt("quest_${i}_progress", 0)
                editor.putBoolean("quest_${i}_claimed", false)
            }
            editor.apply()
        }

        return (0 until 3).map { i ->
            val tplId = prefs.getString("quest_${i}_tpl", templates[i].templateId) ?: templates[i].templateId
            val tpl = QUEST_POOL.firstOrNull { it.templateId == tplId } ?: templates[i]
            val progress = prefs.getInt("quest_${i}_progress", 0)
            val claimed = prefs.getBoolean("quest_${i}_claimed", false)
            Quest("q$i", tpl.title, progress, tpl.target, tpl.coinReward, tpl.xpReward, claimed)
        }
    }

    fun recordGamePlayed(context: Context, gameType: String, isOnline: Boolean, isWin: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val today = getTodayDateString()
        getDailyQuests(context) // ensures date reset

        val templates = getTodayQuestTemplates(today)

        for (i in 0 until 3) {
            val tplId = prefs.getString("quest_${i}_tpl", templates[i].templateId) ?: templates[i].templateId
            val tpl = QUEST_POOL.firstOrNull { it.templateId == tplId } ?: templates[i]
            if (tpl.filter(gameType.lowercase(), isOnline, isWin)) {
                val curProgress = prefs.getInt("quest_${i}_progress", 0)
                if (curProgress < tpl.target) {
                    prefs.edit().putInt("quest_${i}_progress", (curProgress + 1).coerceAtMost(tpl.target)).apply()
                }
            }
        }
    }

    fun claimQuest(context: Context, questId: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val quests = getDailyQuests(context)
        val questIndex = questId.removePrefix("q").toIntOrNull() ?: return false
        val quest = quests.getOrNull(questIndex) ?: return false

        if (!quest.isCompleted || quest.isClaimed) return false

        val keyClaimed = "quest_${questIndex}_claimed"
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
