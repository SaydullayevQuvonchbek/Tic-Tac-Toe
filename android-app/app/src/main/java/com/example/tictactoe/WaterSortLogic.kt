package com.example.tictactoe

import android.graphics.Color

class WaterSortLogic {

    companion object {
        val PALETTE = listOf(
            Color.parseColor("#EF4444"), // Red
            Color.parseColor("#3B82F6"), // Blue
            Color.parseColor("#10B981"), // Green
            Color.parseColor("#F59E0B"), // Orange
            Color.parseColor("#8B5CF6"), // Purple
            Color.parseColor("#EC4899"), // Pink
            Color.parseColor("#06B6D4"), // Cyan
            Color.parseColor("#84CC16")  // Lime
        )
    }

    val tubes = mutableListOf<MutableList<Int>>()
    private val history = mutableListOf<List<List<Int>>>()
    var movesCount = 0
        private set

    fun initLevel(numColors: Int) {
        tubes.clear()
        history.clear()
        movesCount = 0

        val colorsToUse = PALETTE.shuffled().take(numColors)
        val allSegments = mutableListOf<Int>()
        for (color in colorsToUse) {
            repeat(4) { allSegments.add(color) }
        }

        // Shuffle until we get a non-completed state
        do {
            allSegments.shuffle()
            tubes.clear()
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
        } while (isWin()) // ensure it's not already solved
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

        // Save snapshot for Undo
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
}
