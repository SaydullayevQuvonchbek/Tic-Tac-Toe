package com.example.tictactoe

import android.content.Context
import android.widget.TextView

/**
 * Bot strength for the strategy games. Chosen on each game's setup screen and
 * persisted per game in SharedPreferences.
 *
 * EASY   ≈ the historical behaviour (shallow / heuristic).
 * MEDIUM = deeper search, still fast.
 * HARD   = deep search on a background thread (see [AiThinker]).
 */
enum class BotDifficulty(val key: String, val labelUz: String) {
    EASY("easy", "OSON"),
    MEDIUM("medium", "O'RTA"),
    HARD("hard", "QIYIN");

    companion object {
        fun fromKey(k: String?): BotDifficulty =
            values().firstOrNull { it.key == k } ?: MEDIUM
    }
}

object DifficultyStore {

    private const val PREFS_NAME = "TicTacToePrefs"
    private fun prefKey(gameKey: String) = "bot_difficulty_$gameKey"

    /** [gameKey] matches the dashboard keys: chess, checkers, connect4, gomoku, tictactoe, dots_and_boxes, durak. */
    fun get(context: Context, gameKey: String): BotDifficulty {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return BotDifficulty.fromKey(prefs.getString(prefKey(gameKey), null))
    }

    fun set(context: Context, gameKey: String, difficulty: BotDifficulty) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(prefKey(gameKey), difficulty.key)
            .apply()
    }
}

/** Wires the 3-chip `view_difficulty_selector` include to [DifficultyStore]. */
object DifficultySelector {

    fun bind(easy: TextView, medium: TextView, hard: TextView, gameKey: String) {
        val context = easy.context
        val chips = listOf(easy to BotDifficulty.EASY, medium to BotDifficulty.MEDIUM, hard to BotDifficulty.HARD)

        fun render(d: BotDifficulty) = chips.forEach { (v, dd) -> v.isActivated = (dd == d) }
        render(DifficultyStore.get(context, gameKey))

        chips.forEach { (v, d) ->
            v.setOnClickListener {
                DifficultyStore.set(context, gameKey, d)
                render(d)
                HapticHelper.performClick(context)
            }
        }
    }
}
