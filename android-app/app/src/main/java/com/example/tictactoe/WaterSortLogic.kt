package com.example.tictactoe

import android.graphics.Color
import java.util.Random

class WaterSortLogic {

    companion object {
        const val MAX_LEVELS = 50

        val PALETTE = listOf(
            Color.parseColor("#EF4444"), // 1. Crimson Red
            Color.parseColor("#3B82F6"), // 2. Royal Blue
            Color.parseColor("#10B981"), // 3. Emerald Green
            Color.parseColor("#F59E0B"), // 4. Amber Orange
            Color.parseColor("#8B5CF6"), // 5. Vivid Purple
            Color.parseColor("#EC4899"), // 6. Hot Pink
            Color.parseColor("#06B6D4"), // 7. Electric Cyan
            Color.parseColor("#84CC16"), // 8. Neon Lime
            Color.parseColor("#EAB308"), // 9. Golden Yellow
            Color.parseColor("#6366F1")  // 10. Deep Indigo
        )
    }

    val tubes = mutableListOf<MutableList<Int>>()
    private val history = mutableListOf<List<List<Int>>>()
    var currentLevelNumber = 1
        private set
    var movesCount = 0
        private set

    fun getNumColorsForLevel(level: Int): Int {
        return when {
            level <= 5 -> 3
            level <= 15 -> 4
            level <= 25 -> 5
            level <= 35 -> 6
            level <= 45 -> 7
            else -> 8
        }
    }

    fun initLevelByNumber(levelNumber: Int) {
        currentLevelNumber = levelNumber.coerceIn(1, MAX_LEVELS)
        val numColors = getNumColorsForLevel(currentLevelNumber)
        val seed = (currentLevelNumber * 9973L) + 12345L
        val rng = Random(seed)

        tubes.clear()
        history.clear()
        movesCount = 0

        val colorsToUse = PALETTE.take(numColors)
        val allSegments = mutableListOf<Int>()
        for (color in colorsToUse) {
            repeat(4) { allSegments.add(color) }
        }

        // Shuffle with seed
        allSegments.shuffle(rng)

        // Populate tubes
        for (i in 0 until numColors) {
            val tube = mutableListOf<Int>()
            for (j in 0 until 4) {
                tube.add(allSegments[i * 4 + j])
            }
            tubes.add(tube)
        }

        // Add 2 empty tubes
        tubes.add(mutableListOf())
        tubes.add(mutableListOf())
    }

    fun canPour(from: Int, to: Int): Boolean {
        if (from == to) return false
        if (from !in tubes.indices || to !in tubes.indices) return false
        val source = tubes[from]
        val dest = tubes[to]

        if (source.isEmpty()) return false
        if (dest.size >= 4) return false

        // If dest is empty, we can pour
        if (dest.isEmpty()) return true

        // Otherwise top colors must match
        return source.last() == dest.last()
    }

    fun pour(from: Int, to: Int): Int {
        if (!canPour(from, to)) return 0

        saveHistory()

        val source = tubes[from]
        val dest = tubes[to]
        val colorToMove = source.last()
        var pouredCount = 0

        while (source.isNotEmpty() && source.last() == colorToMove && dest.size < 4) {
            dest.add(source.removeAt(source.size - 1))
            pouredCount++
        }

        movesCount++
        return pouredCount
    }

    private fun saveHistory() {
        val snapshot = tubes.map { it.toList() }
        history.add(snapshot)
    }

    fun undo(): Boolean {
        if (history.isEmpty()) return false
        val lastState = history.removeAt(history.size - 1)
        tubes.clear()
        for (tube in lastState) {
            tubes.add(tube.toMutableList())
        }
        if (movesCount > 0) movesCount--
        return true
    }

    fun isWin(): Boolean {
        for (tube in tubes) {
            if (tube.isEmpty()) continue
            if (tube.size != 4) return false
            val first = tube[0]
            if (!tube.all { it == first }) return false
        }
        return true
    }

    fun calculateStars(moves: Int, level: Int): Int {
        val numColors = getNumColorsForLevel(level)
        val optimalThreshold = numColors * 4
        return when {
            moves <= optimalThreshold + 3 -> 3
            moves <= optimalThreshold + 8 -> 2
            else -> 1
        }
    }
}
