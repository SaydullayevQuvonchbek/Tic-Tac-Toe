package com.example.tictactoe

import android.animation.ObjectAnimator
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentWaterSortBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        showSetupDialog()

        binding.btnBack.setOnClickListener { showExitDialog() }
        binding.btnUndo.setOnClickListener { handleUndo() }
        binding.btnRestart.setOnClickListener { restartLevel() }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                showExitDialog()
            }
        })
    }

    private fun showSetupDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_water_sort_setup, null)
        val rg = dialogView.findViewById<RadioGroup>(R.id.rgDifficulty)
        val btnStart = dialogView.findViewById<View>(R.id.btnStart)

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

        dialog.show()
    }

    private fun startLevel(colorsCount: Int) {
        logic.initLevel(colorsCount)
        selectedTubeIndex = null
        binding.tvMoves.text = "Moves: 0"
        buildTubesUI()
    }

    private fun restartLevel() {
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
        val availableWidth = displayMetrics.widthPixels - (48 * displayMetrics.density).toInt()
        val tubeWidth = (availableWidth / maxCols).coerceIn((48 * displayMetrics.density).toInt(), (68 * displayMetrics.density).toInt())
        val tubeHeight = (tubeWidth * 2.6f).toInt()
        val marginH = (6 * displayMetrics.density).toInt()

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
        val selected = selectedTubeIndex

        if (selected == null) {
            // First tap: Select tube if not empty
            if (logic.tubes[index].isNotEmpty()) {
                selectedTubeIndex = index
                tubeViews[index].animate().translationY(-36f).setDuration(150).start()
            }
        } else {
            // Second tap
            if (selected == index) {
                // Tapped same tube: Unselect
                tubeViews[selected].animate().translationY(0f).setDuration(150).start()
                selectedTubeIndex = null
            } else {
                // Tapped target tube: Try to pour
                if (logic.canPour(selected, index)) {
                    val fromView = tubeViews[selected]
                    fromView.animate().translationY(0f).setDuration(150).start()

                    logic.pour(selected, index)
                    selectedTubeIndex = null

                    // Update views
                    tubeViews[selected].setWaterColors(logic.tubes[selected])
                    tubeViews[index].setWaterColors(logic.tubes[index])

                    // Haptic feedback
                    binding.root.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                    binding.tvMoves.text = "Moves: ${logic.movesCount}"

                    // Check Win
                    if (logic.isWin()) {
                        handleWin()
                    }
                } else {
                    // Invalid pour: Unselect source with little shake
                    val fromView = tubeViews[selected]
                    fromView.animate().translationY(0f).setDuration(150).start()
                    selectedTubeIndex = null
                    Toast.makeText(context, "Cannot pour here!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun handleUndo() {
        if (logic.undo()) {
            selectedTubeIndex?.let {
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
        }, 600)
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

    private fun showExitDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Quit Puzzle?")
            .setMessage("Are you sure you want to exit?")
            .setPositiveButton("Exit") { _, _ -> findNavController().navigateUp() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
