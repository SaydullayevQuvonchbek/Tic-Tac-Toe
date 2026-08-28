package com.example.tictactoe

import kotlin.random.Random

class DropNumberLogic(val cols: Int = 5, val rows: Int = 7) {

    // grid[r][c] where r=0 is bottom, r=rows-1 is top. 0 means empty.
    val grid = Array(rows) { IntArray(cols) { 0 } }

    var currentTile: Int = 2
    var nextTile: Int = 4
    var score: Int = 0
    var highestTile: Int = 2
    var targetTile: Int = 512
    var isGameOver: Boolean = false
    var isWin: Boolean = false

    init {
        initGame(512)
    }

    fun initGame(target: Int = 512) {
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                grid[r][c] = 0
            }
        }
        score = 0
        highestTile = 2
        targetTile = target
        isGameOver = false
        isWin = false

        // Seed 2 initial rows at the bottom
        val starterNumbers = listOf(2, 4, 8, 16)
        for (c in 0 until cols) {
            grid[0][c] = starterNumbers.random()
            if (Random.nextBoolean()) {
                grid[1][c] = starterNumbers.random()
            }
        }
        updateHighestTile()
        generateNextTiles()
    }

    private fun generateNextTiles() {
        val maxPower = (Math.log(highestTile.toDouble()) / Math.log(2.0)).toInt().coerceIn(2, 10)
        val availablePowers = (1..maxPower.coerceAtMost(6)).toList()

        val p1 = availablePowers.random()
        val p2 = availablePowers.random()

        currentTile = 1 shl p1
        nextTile = 1 shl p2
    }

    private fun updateHighestTile() {
        var maxVal = 2
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if (grid[r][c] > maxVal) {
                    maxVal = grid[r][c]
                }
            }
        }
        highestTile = maxVal
        if (highestTile >= targetTile) {
            isWin = true
        }
    }

    /**
     * Drops the current tile into the specified column (0 until cols).
     * Returns true if successfully dropped, false if column is full.
     */
    fun dropTile(col: Int): Boolean {
        if (col !in 0 until cols || isGameOver) return false

        // Find the lowest empty row in this column
        var dropRow = -1
        for (r in 0 until rows) {
            if (grid[r][col] == 0) {
                dropRow = r
                break
            }
        }

        if (dropRow == -1) {
            // Column full!
            isGameOver = true
            return false
        }

        grid[dropRow][col] = currentTile
        score += currentTile

        // Process Merges & Cascades
        processMerges(dropRow, col)

        // Generate next tiles
        currentTile = nextTile
        val maxPower = (Math.log(highestTile.toDouble()) / Math.log(2.0)).toInt().coerceIn(2, 10)
        val availablePowers = (1..maxPower.coerceAtMost(6)).toList()
        nextTile = 1 shl availablePowers.random()

        // Check if any column is completely filled up to top without merges
        checkGameOver()

        return true
    }

    private fun processMerges(startRow: Int, startCol: Int) {
        var changed = true
        while (changed) {
            changed = false
            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    val value = grid[r][c]
                    if (value == 0) continue

                    // Check below
                    if (r > 0 && grid[r - 1][c] == value) {
                        grid[r - 1][c] = value * 2
                        grid[r][c] = 0
                        score += value * 2
                        changed = true
                        gravity()
                        updateHighestTile()
                        break
                    }

                    // Check left
                    if (c > 0 && grid[r][c - 1] == value) {
                        grid[r][c - 1] = value * 2
                        grid[r][c] = 0
                        score += value * 2
                        changed = true
                        gravity()
                        updateHighestTile()
                        break
                    }

                    // Check right
                    if (c < cols - 1 && grid[r][c + 1] == value) {
                        grid[r][c + 1] = value * 2
                        grid[r][c] = 0
                        score += value * 2
                        changed = true
                        gravity()
                        updateHighestTile()
                        break
                    }
                }
                if (changed) break
            }
        }
    }

    private fun gravity() {
        for (c in 0 until cols) {
            val list = mutableListOf<Int>()
            for (r in 0 until rows) {
                if (grid[r][c] != 0) {
                    list.add(grid[r][c])
                }
            }
            for (r in 0 until rows) {
                grid[r][c] = if (r < list.size) list[r] else 0
            }
        }
    }

    private fun checkGameOver() {
        for (c in 0 until cols) {
            if (grid[rows - 1][c] != 0) {
                isGameOver = true
                return
            }
        }
    }

    fun bombBottomRow() {
        for (c in 0 until cols) {
            grid[0][c] = 0
        }
        gravity()
        isGameOver = false
    }
}
