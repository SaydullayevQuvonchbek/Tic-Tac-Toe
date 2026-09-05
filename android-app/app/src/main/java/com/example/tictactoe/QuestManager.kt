package com.example.tictactoe

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.random.Random

object QuestManager {

    private const val PREFS_NAME = "TicTacToePrefs"

    data class QuestTemplate(
        val templateId: String,
        val gameKey: String,
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

    private val ALL_GAME_KEYS = listOf(
        "water_sort",
        "chess",
        "checkers",
        "connect4",
        "durak",
        "gomoku",
        "dots_and_boxes",
        "2048",
        "drop_number",
        "math_game",
        "color_match",
        "memory_game",
        "tictactoe"
    )

    private val GAME_QUEST_TEMPLATES = listOf(
        // Water Sort
        QuestTemplate("water_sort_play", "water_sort", "🧪 Yangi sinov: Water Sortda 2 bosqich yuting", 2, 75, 110) { g, _, w ->
            g.contains("water") && w
        },
        // Chess
        QuestTemplate("chess_play", "chess", "👑 Grossmeyster: Shaxmatda 1 ta o'yin o'ynang", 1, 80, 120) { g, _, _ ->
            g.contains("chess")
        },
        QuestTemplate("chess_win", "chess", "👑 Shaxmat: 1 marta mot qiling va yuting", 1, 100, 160) { g, _, w ->
            g.contains("chess") && w
        },
        // Checkers
        QuestTemplate("checkers_play", "checkers", "♟️ Shashka: 2 ta partiya o'ynab ko'ring", 2, 70, 100) { g, _, _ ->
            g.contains("checkers")
        },
        QuestTemplate("checkers_win", "checkers", "♟️ Shashka: 1 marta g'alaba qozoning", 1, 85, 130) { g, _, w ->
            g.contains("checkers") && w
        },
        // Connect 4
        QuestTemplate("connect4_play", "connect4", "🔴 Connect 4: 2 ta o'yinda ishtirok eting", 2, 70, 100) { g, _, _ ->
            g.contains("connect4")
        },
        QuestTemplate("connect4_win", "connect4", "🔴 Connect 4: 4 ta toshni qator terib yuting", 1, 85, 130) { g, _, w ->
            g.contains("connect4") && w
        },
        // Gomoku
        QuestTemplate("gomoku_play", "gomoku", "⚪ Gomoku: 5 ta tosh terishda 1 o'yin o'ynang", 1, 70, 100) { g, _, _ ->
            g.contains("gomoku")
        },
        QuestTemplate("gomoku_win", "gomoku", "⚪ Gomoku: Qatorda 5 ta tosh yig'ib yuting", 1, 90, 140) { g, _, w ->
            g.contains("gomoku") && w
        },
        // Dots & Boxes
        QuestTemplate("dots_play", "dots_and_boxes", "📦 Dots & Boxes: 2 ta o'yin o'ynang", 2, 70, 100) { g, _, _ ->
            g.contains("dots")
        },
        QuestTemplate("dots_win", "dots_and_boxes", "📦 Dots & Boxes: Katakchalarni egallab yuting", 1, 85, 130) { g, _, w ->
            g.contains("dots") && w
        },
        // Durak
        QuestTemplate("durak_play", "durak", "🃏 Durak: 2 ta karta jangi o'ynang", 2, 70, 100) { g, _, _ ->
            g.contains("durak")
        },
        QuestTemplate("durak_win", "durak", "🃏 Durak: Barcha kartalardan qutulib yuting", 1, 90, 140) { g, _, w ->
            g.contains("durak") && w
        },
        // 2048
        QuestTemplate("2048_play", "2048", "🔢 2048 Classic: Bloklarni birlashtiring", 1, 65, 90) { g, _, _ ->
            g.contains("2048")
        },
        // Drop Number
        QuestTemplate("drop_number_play", "drop_number", "🎯 Drop 2048: Raqamlarni otib birlashtiring", 1, 65, 90) { g, _, _ ->
            g.contains("drop")
        },
        // Math Game
        QuestTemplate("math_play", "math_game", "⚡ Math Master: Tezkor arifmetikada 1 bosqich yuting", 1, 65, 90) { g, _, w ->
            (g.contains("math")) && w
        },
        // Color Match
        QuestTemplate("color_play", "color_match", "🎨 Color Match: Reflekslarni sinab ko'ring", 1, 65, 90) { g, _, _ ->
            g.contains("color")
        },
        // Memory Game
        QuestTemplate("memory_play", "memory_game", "🧠 Memory Game: Xotira testida 1 bosqich yuting", 1, 65, 90) { g, _, w ->
            (g.contains("memory")) && w
        },
        // Tic Tac Toe
        QuestTemplate("tictactoe_win", "tictactoe", "❌ Tic-Tac-Toe: 2 marta g'alaba qozoning", 2, 70, 100) { g, _, w ->
            (g.contains("tictactoe") || g.contains("tic_tac_toe")) && w
        }
    )

    private val UNIVERSAL_TEMPLATES = listOf(
        QuestTemplate("univ_online_win", "all", "🌐 Onlayn 1v1: Haqiqiy raqib ustidan 1 g'alaba", 1, 100, 160) { _, o, w -> o && w },
        QuestTemplate("univ_online_play", "all", "👥 Onlayn yoki Do'st bilan 2 ta o'yin o'ynang", 2, 85, 130) { _, o, _ -> o },
        QuestTemplate("univ_any_3", "all", "🎮 Mini Arena: Har qanday 3 ta o'yinda qatnashing", 3, 80, 120) { _, _, _ -> true }
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

    private fun generateSmartQuests(context: Context, todayDate: String): List<QuestTemplate> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Rank games by play count ascending (least played first)
        val sortedGames = ALL_GAME_KEYS.sortedBy { key ->
            prefs.getInt("play_count_${key.lowercase()}", 0)
        }

        val seed = todayDate.hashCode().toLong()
        val rng = Random(seed)

        // 1. First Quest: Pick from user's least played game!
        val leastPlayedGame = sortedGames.firstOrNull() ?: "water_sort"
        val q1Templates = GAME_QUEST_TEMPLATES.filter { it.gameKey == leastPlayedGame }
        val q1 = if (q1Templates.isNotEmpty()) q1Templates.random(rng) else GAME_QUEST_TEMPLATES.first()

        // 2. Second Quest: Pick from second or third least played game (distinct from Q1)
        val secondGame = sortedGames.filter { it != q1.gameKey }.firstOrNull() ?: "chess"
        val q2Templates = GAME_QUEST_TEMPLATES.filter { it.gameKey == secondGame }
        val q2 = if (q2Templates.isNotEmpty()) q2Templates.random(rng) else GAME_QUEST_TEMPLATES.last()

        // 3. Third Quest: Pick an Arena / Universal quest
        val q3 = UNIVERSAL_TEMPLATES.random(rng)

        return listOf(q1, q2, q3)
    }

    fun getDailyQuests(context: Context): List<Quest> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val today = getTodayDateString()
        val lastDate = prefs.getString("last_quest_date", "")

        val templates = generateSmartQuests(context, today)

        // If day changed, reset and write new quests
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
            val tpl = (GAME_QUEST_TEMPLATES + UNIVERSAL_TEMPLATES).firstOrNull { it.templateId == tplId } ?: templates[i]
            val progress = prefs.getInt("quest_${i}_progress", 0)
            val claimed = prefs.getBoolean("quest_${i}_claimed", false)
            Quest("q${i + 1}", tpl.title, progress, tpl.target, tpl.coinReward, tpl.xpReward, claimed)
        }
    }

    fun recordGamePlayed(context: Context, gameType: String, isOnline: Boolean, isWin: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val today = getTodayDateString()
        getDailyQuests(context) // Ensure rollover check

        val templates = generateSmartQuests(context, today)

        for (i in 0 until 3) {
            val tplId = prefs.getString("quest_${i}_tpl", templates[i].templateId) ?: templates[i].templateId
            val tpl = (GAME_QUEST_TEMPLATES + UNIVERSAL_TEMPLATES).firstOrNull { it.templateId == tplId } ?: templates[i]
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
        val rawNum = questId.removePrefix("q").toIntOrNull() ?: return false
        val questIndex = if (rawNum in 1..3) rawNum - 1 else rawNum
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
        val league = LeagueManager.getCurrentLeague(xp)
        return Pair(league.badge + " " + league.name, league.colorHex)
    }
}
