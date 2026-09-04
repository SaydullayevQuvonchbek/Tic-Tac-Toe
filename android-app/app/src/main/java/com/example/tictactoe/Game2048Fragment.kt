package com.example.tictactoe

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.tictactoe.databinding.Fragment2048Binding
import com.example.tictactoe.network.ApiClient
import com.example.tictactoe.network.GameScoreRequest
import com.example.tictactoe.network.GameScoreResponse
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import kotlin.math.abs

class Game2048Fragment : Fragment() {

    private var _binding: Fragment2048Binding? = null
    private val binding get() = _binding!!

    private val grid = Array(4) { IntArray(4) }
    private val cells = Array(4) { arrayOfNulls<TextView>(4) }
    private var score = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = Fragment2048Binding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupGrid()
        startGame()
        
        binding.btnRestart2048.setOnClickListener {
            startGame()
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                showExitDialog()
            }
        })

        binding.btnBack2048.setOnClickListener {
            showExitDialog()
        }

        val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            private val SWIPE_THRESHOLD = 50
            private val SWIPE_VELOCITY_THRESHOLD = 50

            override fun onDown(e: MotionEvent): Boolean {
                return true
            }

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null) return false
                val diffY = e2.y - e1.y
                val diffX = e2.x - e1.x
                if (abs(diffX) > abs(diffY)) {
                    if (abs(diffX) > SWIPE_THRESHOLD && abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX > 0) move(Direction.RIGHT) else move(Direction.LEFT)
                        return true
                    }
                } else {
                    if (abs(diffY) > SWIPE_THRESHOLD && abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffY > 0) move(Direction.DOWN) else move(Direction.UP)
                        return true
                    }
                }
                return false
            }
        })

        binding.rootLayout.setOnTouchListener { _, event -> gestureDetector.onTouchEvent(event) }
        binding.boardCard.setOnTouchListener { _, event -> gestureDetector.onTouchEvent(event) }
        binding.gridLayout.setOnTouchListener { _, event -> gestureDetector.onTouchEvent(event) }
    }

    private fun setupGrid() {
        // Board has 24dp margin on left/right -> 48dp total
        // GridLayout has 8dp padding on left/right -> 16dp total
        // Each cell has 4dp margin on left/right -> 8dp * 4 = 32dp total
        // Total fixed horizontal space = 48 + 16 + 32 = 96dp
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val fixedSpacePx = (96 * displayMetrics.density).toInt()
        val cellSize = (screenWidth - fixedSpacePx) / 4

        val marginPx = (4 * displayMetrics.density).toInt()

        for (i in 0..3) {
            for (j in 0..3) {
                val card = CardView(requireContext())
                val params = GridLayout.LayoutParams(
                    GridLayout.spec(i),
                    GridLayout.spec(j)
                )
                params.width = cellSize
                params.height = cellSize
                params.setMargins(marginPx, marginPx, marginPx, marginPx)
                card.layoutParams = params
                card.radius = 8f * displayMetrics.density
                card.setCardBackgroundColor(Color.parseColor("#CDC1B4"))
                card.cardElevation = 0f

                val tv = TextView(requireContext())
                tv.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                tv.gravity = android.view.Gravity.CENTER
                tv.textSize = 28f
                tv.setTypeface(null, android.graphics.Typeface.BOLD)
                
                card.addView(tv)
                binding.gridLayout.addView(card)
                cells[i][j] = tv
            }
        }
    }

    private fun startGame() {
        score = 0
        updateScore()
        for (i in 0..3) {
            for (j in 0..3) {
                grid[i][j] = 0
            }
        }
        addRandomTile()
        addRandomTile()
        updateUI()
    }

    private fun addRandomTile() {
        val emptyCells = mutableListOf<Pair<Int, Int>>()
        for (i in 0..3) {
            for (j in 0..3) {
                if (grid[i][j] == 0) emptyCells.add(Pair(i, j))
            }
        }
        if (emptyCells.isNotEmpty()) {
            val cell = emptyCells.random()
            grid[cell.first][cell.second] = if (Math.random() < 0.9) 2 else 4
        }
    }

    private fun updateUI() {
        for (i in 0..3) {
            for (j in 0..3) {
                val value = grid[i][j]
                val tv = cells[i][j]!!
                val parentCard = tv.parent as CardView
                if (value == 0) {
                    tv.text = ""
                    parentCard.setCardBackgroundColor(Color.parseColor("#CDC1B4"))
                } else {
                    tv.text = value.toString()
                    tv.setTextColor(if (value <= 4) Color.parseColor("#776E65") else Color.WHITE)
                    parentCard.setCardBackgroundColor(getTileColor(value))
                }
            }
        }
    }

    private fun getTileColor(value: Int): Int {
        return when (value) {
            2 -> Color.parseColor("#EEE4DA")
            4 -> Color.parseColor("#EDE0C8")
            8 -> Color.parseColor("#F2B179")
            16 -> Color.parseColor("#F59563")
            32 -> Color.parseColor("#F67C5F")
            64 -> Color.parseColor("#F65E3B")
            128 -> Color.parseColor("#EDCF72")
            256 -> Color.parseColor("#EDCC61")
            512 -> Color.parseColor("#EDC850")
            1024 -> Color.parseColor("#EDC53F")
            2048 -> Color.parseColor("#EDC22E")
            else -> Color.parseColor("#3C3A32")
        }
    }

    enum class Direction { UP, DOWN, LEFT, RIGHT }

    private fun move(dir: Direction) {
        var moved = false
        val newGrid = Array(4) { IntArray(4) }
        
        for (i in 0..3) {
            for (j in 0..3) {
                newGrid[i][j] = grid[i][j]
            }
        }

        when (dir) {
            Direction.LEFT -> {
                for (i in 0..3) {
                    val row = grid[i].filter { it != 0 }.toMutableList()
                    val mergedRow = merge(row)
                    for (j in 0..3) {
                        newGrid[i][j] = if (j < mergedRow.size) mergedRow[j] else 0
                    }
                }
            }
            Direction.RIGHT -> {
                for (i in 0..3) {
                    val row = grid[i].filter { it != 0 }.reversed().toMutableList()
                    val mergedRow = merge(row)
                    for (j in 0..3) {
                        newGrid[i][3 - j] = if (j < mergedRow.size) mergedRow[j] else 0
                    }
                }
            }
            Direction.UP -> {
                for (j in 0..3) {
                    val col = mutableListOf<Int>()
                    for (i in 0..3) if (grid[i][j] != 0) col.add(grid[i][j])
                    val mergedCol = merge(col)
                    for (i in 0..3) {
                        newGrid[i][j] = if (i < mergedCol.size) mergedCol[i] else 0
                    }
                }
            }
            Direction.DOWN -> {
                for (j in 0..3) {
                    val col = mutableListOf<Int>()
                    for (i in 3 downTo 0) if (grid[i][j] != 0) col.add(grid[i][j])
                    val mergedCol = merge(col)
                    for (i in 0..3) {
                        newGrid[3 - i][j] = if (i < mergedCol.size) mergedCol[i] else 0
                    }
                }
            }
        }

        for (i in 0..3) {
            for (j in 0..3) {
                if (grid[i][j] != newGrid[i][j]) moved = true
                grid[i][j] = newGrid[i][j]
            }
        }

        if (moved) {
            addRandomTile()
            updateUI()
            checkGameOver()
        }
    }

    private fun merge(list: MutableList<Int>): List<Int> {
        val result = mutableListOf<Int>()
        var i = 0
        while (i < list.size) {
            if (i + 1 < list.size && list[i] == list[i + 1]) {
                val mergedValue = list[i] * 2
                result.add(mergedValue)
                score += mergedValue
                updateScore()
                i += 2
            } else {
                result.add(list[i])
                i++
            }
        }
        return result
    }
    
    private fun updateScore() {
        binding.tvScore2048.text = score.toString()
    }

    private fun checkGameOver() {
        var hasEmpty = false
        var canMerge = false
        
        for (i in 0..3) {
            for (j in 0..3) {
                if (grid[i][j] == 0) hasEmpty = true
                if (i < 3 && grid[i][j] == grid[i + 1][j]) canMerge = true
                if (j < 3 && grid[i][j] == grid[i][j + 1]) canMerge = true
            }
        }

        if (!hasEmpty && !canMerge) {
            handleGameOver()
        }
    }
    
    private fun handleGameOver() {
        val sharedPref = requireActivity().getSharedPreferences("TicTacToePrefs", android.content.Context.MODE_PRIVATE)
        val userId = sharedPref.getInt("user_id", -1)

        var maxTile = 0
        for (i in 0..3) {
            for (j in 0..3) {
                if (grid[i][j] > maxTile) maxTile = grid[i][j]
            }
        }

        val prevBest = sharedPref.getInt("game_2048_high_score", 0)
        val isNewRecord = score > prevBest
        if (isNewRecord) {
            sharedPref.edit().putInt("game_2048_high_score", score).apply()
        }

        if (userId != -1 && score > 0) {
            val safeContext = context
            val pd = safeContext?.let {
                try {
                    android.app.ProgressDialog(it).apply {
                        setMessage("Saving Score...")
                        setCancelable(false)
                        show()
                    }
                } catch (_: Exception) { null }
            }
            val req = GameScoreRequest(userId, "game_2048", score)
            ApiClient.instance.submitGameScore(req).enqueue(object : Callback<GameScoreResponse> {
                override fun onResponse(call: Call<GameScoreResponse>, response: Response<GameScoreResponse>) {
                    if (!isAdded || _binding == null) return
                    try { pd?.dismiss() } catch (_: Exception) {}
                    if (response.isSuccessful && response.body()?.status == "success") {
                        val resp = response.body()!!
                        sharedPref.edit()
                            .putInt("level", resp.current_level)
                            .putInt("xp", resp.new_total_xp)
                            .apply()
                        showResultDialog(maxTile, isNewRecord, resp.xp_earned, resp.new_total_xp, resp.level_up, resp.current_level)
                    } else {
                        showResultDialog(maxTile, isNewRecord, 0, 0, false, 0)
                    }
                }

                override fun onFailure(call: Call<GameScoreResponse>, t: Throwable) {
                    if (!isAdded || _binding == null) return
                    try { pd?.dismiss() } catch (_: Exception) {}
                    showResultDialog(maxTile, isNewRecord, 0, 0, false, 0)
                }
            })
        } else {
            showResultDialog(maxTile, isNewRecord, 0, 0, false, 0)
        }
    }

    private fun showResultDialog(maxTile: Int, isNewRecord: Boolean, xpEarned: Int, totalXp: Int, levelUp: Boolean, currentLevel: Int) {
        var msg = "Final Score: $score\nHighest Tile Reached: $maxTile"
        if (isNewRecord) {
            msg += "\n🎉 NEW HIGH SCORE! 🎉"
        }
        if (xpEarned > 0) {
            msg += "\n\nEarned: +$xpEarned XP\nTotal XP: $totalXp"
        }
        if (levelUp) {
            msg += "\n\n🚀 LEVEL UP! You are now Level $currentLevel 🚀"
        }

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("🎮 Game Over!")
            .setMessage(msg)
            .setPositiveButton("🔄 Play Again") { _, _ ->
                startGame()
            }
            .setNegativeButton("🚪 Back to Menu") { _, _ ->
                findNavController().navigateUp()
            }
            .setCancelable(false)
            .show()
    }

    private fun showExitDialog() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Quit Game?")
            .setMessage("Are you sure you want to exit? Your current score will be submitted if > 0.")
            .setPositiveButton("Exit") { _, _ ->
                if (score > 0) {
                    handleGameOver()
                } else {
                    findNavController().navigateUp()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
