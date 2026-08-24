package com.example.tictactoe

import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.tictactoe.databinding.FragmentColorMatchBinding
import com.example.tictactoe.network.ApiClient
import com.example.tictactoe.network.GameScoreRequest
import com.example.tictactoe.network.GameScoreResponse
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ColorMatchFragment : Fragment() {

    private var _binding: FragmentColorMatchBinding? = null
    private val binding get() = _binding!!

    private var score = 0
    private var combo = 0
    private var isCorrectMatch = false
    private var timer: CountDownTimer? = null
    private var timeRemaining = 60

    private val colors = listOf(
        Pair("RED", Color.parseColor("#EF4444")),
        Pair("BLUE", Color.parseColor("#3B82F6")),
        Pair("GREEN", Color.parseColor("#10B981")),
        Pair("YELLOW", Color.parseColor("#F59E0B")),
        Pair("PURPLE", Color.parseColor("#8B5CF6")),
        Pair("ORANGE", Color.parseColor("#F97316"))
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentColorMatchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                showExitDialog()
            }
        })

        binding.btnBackColor.setOnClickListener {
            showExitDialog()
        }

        binding.btnWrong.setOnClickListener { checkAnswer(false) }
        binding.btnCorrect.setOnClickListener { checkAnswer(true) }

        startGame()
    }

    private fun startGame() {
        score = 0
        combo = 0
        timeRemaining = 30 // Make it faster!
        updateScore()
        nextWord()
        startTimer()
    }

    private fun startTimer() {
        timer?.cancel()
        timer = object : CountDownTimer(timeRemaining * 1000L, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeRemaining = (millisUntilFinished / 1000).toInt()
                binding.tvTimer.text = "${timeRemaining}s"
            }

            override fun onFinish() {
                endGame()
            }
        }.start()
    }

    private fun nextWord() {
        val wordPair = colors.random()
        
        // 50% chance to match
        isCorrectMatch = Math.random() > 0.5
        val colorPair = if (isCorrectMatch) wordPair else colors.filter { it.first != wordPair.first }.random()

        binding.tvColorWord.text = wordPair.first
        binding.tvColorWord.setTextColor(colorPair.second)
    }

    private fun checkAnswer(userSaysYes: Boolean) {
        if (userSaysYes == isCorrectMatch) {
            combo++
            score += (10 * combo)
            binding.tvInstruction.text = "Correct! Combo x$combo (+1s)"
            binding.tvInstruction.setTextColor(Color.parseColor("#34D399"))
            timeRemaining += 1
            startTimer()
        } else {
            combo = 0
            score -= 10
            binding.tvInstruction.text = "Wrong! Combo Lost"
            binding.tvInstruction.setTextColor(Color.parseColor("#EF4444"))
        }
        updateScore()
        nextWord()
    }

    private fun updateScore() {
        binding.tvScore.text = "Score: $score"
    }

    private fun endGame() {
        binding.btnWrong.isEnabled = false
        binding.btnCorrect.isEnabled = false
        binding.tvTimer.text = "0s"
        
        val sharedPref = requireActivity().getSharedPreferences("TicTacToePrefs", android.content.Context.MODE_PRIVATE)
        val userId = sharedPref.getInt("user_id", -1)

        val prevBest = sharedPref.getInt("color_match_high_score", 0)
        val isNewRecord = score > prevBest
        if (isNewRecord) {
            sharedPref.edit().putInt("color_match_high_score", score).apply()
        }

        if (userId != -1 && score > 0) {
            val pd = android.app.ProgressDialog(context).apply {
                setMessage("Saving Score...")
                setCancelable(false)
                show()
            }
            val req = GameScoreRequest(userId, "color_match", score)
            ApiClient.instance.submitGameScore(req).enqueue(object : Callback<GameScoreResponse> {
                override fun onResponse(call: Call<GameScoreResponse>, response: Response<GameScoreResponse>) {
                    pd.dismiss()
                    if (response.isSuccessful && response.body()?.status == "success") {
                        val resp = response.body()!!
                        sharedPref.edit()
                            .putInt("level", resp.current_level)
                            .putInt("xp", resp.new_total_xp)
                            .apply()
                        showResultDialog(isNewRecord, resp.xp_earned, resp.new_total_xp, resp.level_up, resp.current_level)
                    } else {
                        showResultDialog(isNewRecord, 0, 0, false, 0)
                    }
                }

                override fun onFailure(call: Call<GameScoreResponse>, t: Throwable) {
                    pd.dismiss()
                    showResultDialog(isNewRecord, 0, 0, false, 0)
                }
            })
        } else {
            showResultDialog(isNewRecord, 0, 0, false, 0)
        }
    }

    private fun showResultDialog(isNewRecord: Boolean, xpEarned: Int, totalXp: Int, levelUp: Boolean, currentLevel: Int) {
        var msg = "Final Score: $score"
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
            .setTitle("⏱️ Time's Up!")
            .setMessage(msg)
            .setPositiveButton("🔄 Play Again") { _, _ ->
                binding.btnWrong.isEnabled = true
                binding.btnCorrect.isEnabled = true
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
            .setMessage("Are you sure you want to exit? Your current score will be lost.")
            .setPositiveButton("Exit") { _, _ ->
                timer?.cancel()
                findNavController().navigateUp()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        timer?.cancel()
        _binding = null
    }
}
