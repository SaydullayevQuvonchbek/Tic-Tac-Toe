package com.example.tictactoe

/**
 * Shared XP / level math for the Premium Arena UI.
 * The backend awards XP (Win +50, Draw +10, Loss -5) and returns the authoritative
 * level; the client only needs a consistent "XP into current level" for progress bars.
 * Design reference: header "LVL 1 · 170/500 XP", result "LEVEL 1 → 2 · 290/500 XP".
 */
object LevelHelper {

    const val XP_PER_LEVEL = 500

    /** Total XP required to have reached [level] (level 1 starts at 0). */
    fun xpForLevel(level: Int): Int = (level.coerceAtLeast(1) - 1) * XP_PER_LEVEL

    /** XP accumulated inside the current level, clamped to [0, XP_PER_LEVEL]. */
    fun xpIntoLevel(xp: Int, level: Int): Int =
        (xp - xpForLevel(level)).coerceIn(0, XP_PER_LEVEL)

    /** Progress through the current level as a 0..100 percentage. */
    fun levelProgressPercent(xp: Int, level: Int): Int =
        (xpIntoLevel(xp, level) * 100 / XP_PER_LEVEL).coerceIn(0, 100)
}
