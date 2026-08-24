package com.example.tictactoe

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.tictactoe.databinding.FragmentWaterSortBinding
import com.example.tictactoe.network.ApiClient
import com.example.tictactoe.network.GameScoreRequest
import com.example.tictactoe.network.GameScoreResponse
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class WaterSortFragment : Fragment() {

    private var _binding: FragmentWaterSortBinding? = null
    private val binding get() = _binding!!

    private val logic = WaterSortLogic()
    private val tubeViews = mutableListOf<WaterTubeView>()
    private var selectedTubeIndex: Int? = null
    private var currentDifficultyColors = 3
    private var isAnimating = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentWaterSortBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initSetupUI()

        binding.btnSetupBack.setOnClickListener { findNavController().navigateUp() }
        binding.btnGameplayBack.setOnClickListener { handleBackNavigation() }
        binding.btnUndo.setOnClickListener { handleUndo() }
        binding.btnRestart.setOnClickListener { restartLevel() }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackNavigation()
            }
        })
    }

    private fun handleBackNavigation() {
        if (binding.gameplayContainer.visibility == View.VISIBLE) {
            showSetupScreen()
        } else {
            findNavController().navigateUp()
        }
    }

    private fun initSetupUI() {
        showSetupScreen()

        when (currentDifficultyColors) {
            3 -> binding.rgDifficulty.check(R.id.rbEasy)
            5 -> binding.rgDifficulty.check(R.id.rbMedium)
            7 -> binding.rgDifficulty.check(R.id.rbHard)
        }

        binding.btnStartPuzzle.setOnClickListener {
            currentDifficultyColors = when (binding.rgDifficulty.checkedRadioButtonId) {
                R.id.rbEasy -> 3
                R.id.rbMedium -> 5
                R.id.rbHard -> 7
                else -> 3
            }
            startLevel(currentDifficultyColors)
        }
    }

    private fun showSetupScreen() {
        binding.setupContainer.visibility = View.VISIBLE
        binding.gameplayContainer.visibility = View.GONE
    }

    private fun showGameplayScreen() {
        binding.setupContainer.visibility = View.GONE
        binding.gameplayContainer.visibility = View.VISIBLE
    }

    private fun startLevel(colorsCount: Int) {
        logic.initLevel(colorsCount)
        selectedTubeIndex = null
        isAnimating = false
        binding.tvMoves.text = "Moves: 0"
        showGameplayScreen()
        buildTubesUI()
    }

    private fun restartLevel() {
        if (isAnimating) return
        startLevel(currentDifficultyColors)
        Toast.makeText(context, "Level Reset", Toast.LENGTH_SHORT).show()
    }

    private fun buildTubesUI() {
        binding.rowTop.removeAllViews()
        binding.rowBottom.removeAllViews()
        tubeViews.clear()

        val totalTubes = logic.tubes.size
        val topCount = (totalTubes + 1) / 2
        val bottomCount = totalTubes - topCount
        val maxCols = Math.max(topCount, bottomCount)

        val displayMetrics = resources.displayMetrics
        val totalOuterMargin = (24 * displayMetrics.density).toInt()
        val availableWidth = displayMetrics.widthPixels - totalOuterMargin
        val marginH = (3 * displayMetrics.density).toInt()

        val calculatedWidth = (availableWidth / maxCols) - (marginH * 2)
        val tubeWidth = calculatedWidth.coerceIn((42 * displayMetrics.density).toInt(), (64 * displayMetrics.density).toInt())
        val tubeHeight = (tubeWidth * 2.4f).toInt()

        for (i in 0 until totalTubes) {
            val tubeView = WaterTubeView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(tubeWidth, tubeHeight).apply {
                    setMargins(marginH, 0, marginH, 0)
                }
                setWaterColors(logic.tubes[i])
                setOnClickListener { onTubeClicked(i) }
            }

            tubeViews.add(tubeView)

            if (i < topCount) {
                binding.rowTop.addView(tubeView)
            } else {
                binding.rowBottom.addView(tubeView)
            }
        }
    }

    private fun onTubeClicked(index: Int) {
        if (isAnimating) return

        val selected = selectedTubeIndex

        if (selected == null) {
            // First tap: Select tube if not empty
            if (logic.tubes[index].isNotEmpty()) {
                selectedTubeIndex = index
                tubeViews[index].isSelectedTube = true
                tubeViews[index].animate().translationY(-28f).setDuration(150).start()
            }
        } else {
            // Second tap
            if (selected == index) {
                // Tapped same tube: Unselect
                tubeViews[selected].isSelectedTube = false
                tubeViews[selected].animate().translationY(0f).setDuration(150).start()
                selectedTubeIndex = null
            } else {
                // Tapped target tube: Try to pour
                if (logic.canPour(selected, index)) {
                    animatePour(selected, index)
                } else {
                    // Invalid pour: Unselect source
                    val fromView = tubeViews[selected]
                    fromView.isSelectedTube = false
                    fromView.animate().translationY(0f).setDuration(150).start()
                    selectedTubeIndex = null
                    Toast.makeText(context, "Cannot pour here!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun animatePour(fromIndex: Int, toIndex: Int) {
        isAnimating = true
        selectedTubeIndex = null

        val fromView = tubeViews[fromIndex]
        val toView = tubeViews[toIndex]

        val tiltAngle = if (fromIndex < toIndex) 35f else -35f

        // Smooth in-place tilt & pour animation
        fromView.animate()
            .rotation(tiltAngle)
            .setDuration(180)
            .withEndAction {
                logic.pour(fromIndex, toIndex)
                fromView.setWaterColors(logic.tubes[fromIndex])
                toView.setWaterColors(logic.tubes[toIndex])
                binding.root.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                binding.tvMoves.text = "Moves: ${logic.movesCount}"

                fromView.postDelayed({
                    fromView.animate()
                        .translationY(0f)
                        .rotation(0f)
                        .setDuration(160)
                        .withEndAction {
                            fromView.isSelectedTube = false
                            isAnimating = false
                            if (logic.isWin()) {
                                handleWin()
                            }
                        }
                        .start()
                }, 80)
            }
            .start()
    }

    private fun handleUndo() {
        if (isAnimating) return
        if (logic.undo()) {
            selectedTubeIndex?.let {
                tubeViews[it].isSelectedTube = false
                tubeViews[it].animate().translationY(0f).setDuration(100).start()
                selectedTubeIndex = null
            }
            for (i in 0 until logic.tubes.size) {
                tubeViews[i].setWaterColors(logic.tubes[i])
            }
            binding.tvMoves.text = "Moves: ${logic.movesCount}"
        } else {
            Toast.makeText(context, "No moves to undo", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleWin() {
        // Record best moves
        val sharedPref = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
        val prevBest = sharedPref.getInt("water_sort_best_moves", 0)
        if (prevBest == 0 || logic.movesCount < prevBest) {
            sharedPref.edit().putInt("water_sort_best_moves", logic.movesCount).apply()
        }

        binding.root.postDelayed({
            submitScoreAndNavigate()
        }, 500)
    }

    private fun submitScoreAndNavigate() {
        val sharedPref = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
        val userId = sharedPref.getInt("user_id", -1)

        if (userId != -1) {
            ApiClient.instance.submitGameScore(GameScoreRequest(userId, "water_sort", 50))
                .enqueue(object : Callback<GameScoreResponse> {
                    override fun onResponse(call: Call<GameScoreResponse>, response: Response<GameScoreResponse>) {}
                    override fun onFailure(call: Call<GameScoreResponse>, t: Throwable) {}
                })
        }

        val bundle = Bundle().apply {
            putString("gameType", "water_sort")
            putString("resultMessage", "Puzzle Solved in ${logic.movesCount} Moves! 🧪🎉")
            putBoolean("isDraw", false)
        }
        findNavController().navigate(R.id.action_waterSortFragment_to_resultFragment, bundle)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
