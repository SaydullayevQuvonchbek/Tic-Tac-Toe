package com.example.tictactoe

import android.animation.ObjectAnimator
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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

        showSetupDialog()

        binding.btnBack.setOnClickListener { showSetupDialog() }
        binding.btnUndo.setOnClickListener { handleUndo() }
        binding.btnRestart.setOnClickListener { restartLevel() }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                showSetupDialog()
            }
        })
    }

    private fun showSetupDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_water_sort_setup, null)
        val rg = dialogView.findViewById<RadioGroup>(R.id.rgDifficulty)
        val btnStart = dialogView.findViewById<Button>(R.id.btnStart)
        val btnMenu = dialogView.findViewById<Button>(R.id.btnMenu)

        // Select current difficulty radio button
        when (currentDifficultyColors) {
            3 -> rg.check(R.id.rbEasy)
            5 -> rg.check(R.id.rbMedium)
            7 -> rg.check(R.id.rbHard)
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnStart.setOnClickListener {
            currentDifficultyColors = when (rg.checkedRadioButtonId) {
                R.id.rbEasy -> 3
                R.id.rbMedium -> 5
                R.id.rbHard -> 7
                else -> 3
            }
            dialog.dismiss()
            startLevel(currentDifficultyColors)
        }

        btnMenu.setOnClickListener {
            dialog.dismiss()
            findNavController().navigateUp()
        }

        dialog.show()
    }

    private fun startLevel(colorsCount: Int) {
        logic.initLevel(colorsCount)
        selectedTubeIndex = null
        isAnimating = false
        binding.tvMoves.text = "Moves: 0"
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
        
        // Exact pixel math: tubeWidth takes exact column fraction minus horizontal margins
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
                // Tapped target tube: Try to pour with smooth animation
                if (logic.canPour(selected, index)) {
                    animatePour(selected, index)
                } else {
                    // Invalid pour: Unselect source with little shake
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

        val fromLoc = IntArray(2)
        val toLoc = IntArray(2)
        fromView.getLocationOnScreen(fromLoc)
        toView.getLocationOnScreen(toLoc)

        val dx = (toLoc[0] - fromLoc[0]).toFloat()
        val dy = (toLoc[1] - fromLoc[1] - (fromView.height * 0.45f)).toFloat()
        val tiltAngle = if (fromLoc[0] <= toLoc[0]) 55f else -55f

        // Step 1: Fly towards target tube and tilt
        fromView.bringToFront()
        fromView.animate()
            .translationX(dx)
            .translationY(dy)
            .rotation(tiltAngle)
            .setDuration(260)
            .withEndAction {
                // Step 2: Transfer liquid
                logic.pour(fromIndex, toIndex)
                fromView.setWaterColors(logic.tubes[fromIndex])
                toView.setWaterColors(logic.tubes[toIndex])
                binding.root.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                binding.tvMoves.text = "Moves: ${logic.movesCount}"

                // Step 3: Return to original spot
                fromView.postDelayed({
                    fromView.animate()
                        .translationX(0f)
                        .translationY(0f)
                        .rotation(0f)
                        .setDuration(220)
                        .withEndAction {
                            fromView.isSelectedTube = false
                            isAnimating = false
                            if (logic.isWin()) {
                                handleWin()
                            }
                        }
                        .start()
                }, 100)
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
