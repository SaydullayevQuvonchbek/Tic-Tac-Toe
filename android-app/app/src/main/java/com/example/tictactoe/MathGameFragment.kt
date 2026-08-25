package com.example.tictactoe

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.tictactoe.databinding.FragmentMathGameBinding
import com.example.tictactoe.network.ApiClient
import com.example.tictactoe.network.GameScoreRequest
import com.example.tictactoe.network.GameScoreResponse
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MathGameFragment : Fragment() {

    private var _binding: FragmentMathGameBinding? = null
    private val binding get() = _binding!!

    private var score = 0
    private var combo = 0
    private var correctAnswer = 0
    private var timer: CountDownTimer? = null
    private var timeRemainingMillis = 35000L

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMathGameBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                showExitDialog()
            }
        })

        binding.btnBack.setOnClickListener {
            showExitDialog()
        }

        val buttons = listOf(binding.btnAns1, binding.btnAns2, binding.btnAns3, binding.btnAns4)
        for (btn in buttons) {
            btn.setOnClickListener { checkAnswer((it as Button).text.toString().toInt()) }
        }

        startGame()
    }

    private fun startGame() {
        score = 0
        combo = 0
        timeRemainingMillis = 35000L
        updateScore()
        generateQuestion()
        startTimer()
    }

    private fun startTimer() {
        timer?.cancel()
        timer = object : CountDownTimer(timeRemainingMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeRemainingMillis = millisUntilFinished
                binding.tvTimer.text = "${millisUntilFinished / 1000}s"
            }

            override fun onFinish() {
                binding.tvTimer.text = "0s"
                endGame()
            }
        }.start()
    }

    private fun generateQuestion() {
        val op = (0..3).random()
        when (op) {
            0 -> {
                // Addition
                val a = (12..80).random()
                val b = (12..80).random()
                correctAnswer = a + b
                binding.tvQuestion.text = "$a + $b = ?"
            }
            1 -> {
                // Subtraction
                val a = (25..99).random()
                val b = (10..a).random()
                correctAnswer = a - b
                binding.tvQuestion.text = "$a - $b = ?"
            }
            2 -> {
                // Multiplication
                val a = (3..12).random()
                val b = (3..12).random()
                correctAnswer = a * b
                binding.tvQuestion.text = "$a × $b = ?"
            }
            else -> {
                // Division
                val b = (2..9).random()
                val ans = (3..12).random()
                val a = b * ans
                correctAnswer = ans
                binding.tvQuestion.text = "$a ÷ $b = ?"
            }
        }

        val answers = mutableListOf(correctAnswer)
        while (answers.size < 4) {
            val offset = (-12..12).random()
            val wrong = correctAnswer + offset
            if (wrong != correctAnswer && wrong >= 0 && !answers.contains(wrong)) {
                answers.add(wrong)
            }
        }
        answers.shuffle()

        binding.btnAns1.text = answers[0].toString()
        binding.btnAns2.text = answers[1].toString()
        binding.btnAns3.text = answers[2].toString()
        binding.btnAns4.text = answers[3].toString()
    }

    private fun checkAnswer(ans: Int) {
        if (ans == correctAnswer) {
            combo++
            val multiplier = when {
                combo >= 8 -> 5
                combo >= 5 -> 3
                combo >= 3 -> 2
                else -> 1
            }
            val ptsGained = 10 * multiplier
            score += ptsGained

            val comboStr = if (multiplier > 1) " (COMBO x$multiplier! 🔥)" else ""
            binding.tvFeedback.text = "Correct! +$ptsGained$comboStr"
            binding.tvFeedback.setTextColor(Color.parseColor("#34D399"))

            // Bonus 1.5s
            timeRemainingMillis = (timeRemainingMillis + 1500L).coerceAtMost(60000L)
            startTimer()
        } else {
            combo = 0
            score = (score - 5).coerceAtLeast(0)
            binding.tvFeedback.text = "Wrong! -5 ❌"
            binding.tvFeedback.setTextColor(Color.parseColor("#F43F5E"))
        }
        updateScore()
        binding.root.postDelayed({ generateQuestion() }, 350)
    }

    private fun updateScore() {
        val comboSuffix = if (combo >= 3) " | 🔥 x${if (combo >= 8) 5 else if (combo >= 5) 3 else 2}" else ""
        binding.tvScore.text = "Score: $score$comboSuffix"
    }

    private fun endGame() {
        timer?.cancel()
        binding.btnAns1.isEnabled = false
        binding.btnAns2.isEnabled = false
        binding.btnAns3.isEnabled = false
        binding.btnAns4.isEnabled = false

        val sharedPref = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
        val coinsEarned = (score / 30) * 5
        if (coinsEarned > 0) {
            val curCoins = sharedPref.getInt("coins", 0)
            sharedPref.edit().putInt("coins", curCoins + coinsEarned).apply()
        }

        if (score > 0) {
            submitScore(coinsEarned)
        } else {
            showResultDialog(0, 0, false, 0, 0)
        }
    }

    private fun submitScore(coinsEarned: Int) {
        val sharedPref = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
        val userId = sharedPref.getInt("user_id", -1)

        val prevBest = sharedPref.getInt("math_high_score", 0)
        if (score > prevBest) {
            sharedPref.edit().putInt("math_high_score", score).apply()
        }

        if (userId != -1) {
            val pd = android.app.ProgressDialog(context).apply {
                setMessage("Saving Score...")
                setCancelable(false)
                show()
            }

            ApiClient.instance.submitGameScore(GameScoreRequest(userId, "math", score))
                .enqueue(object : Callback<GameScoreResponse> {
                    override fun onResponse(call: Call<GameScoreResponse>, response: Response<GameScoreResponse>) {
                        pd.dismiss()
                        if (response.isSuccessful && response.body()?.status == "success") {
                            val resp = response.body()!!
                            sharedPref.edit()
                                .putInt("level", resp.current_level)
                                .putInt("xp", resp.new_total_xp)
                                .apply()

                            showResultDialog(resp.xp_earned, resp.new_total_xp, resp.level_up, resp.current_level, coinsEarned)
                        } else {
                            showResultDialog(score / 2, sharedPref.getInt("xp", 0) + score / 2, false, sharedPref.getInt("level", 1), coinsEarned)
                        }
                    }

                    override fun onFailure(call: Call<GameScoreResponse>, t: Throwable) {
                        pd.dismiss()
                        showResultDialog(score / 2, sharedPref.getInt("xp", 0) + score / 2, false, sharedPref.getInt("level", 1), coinsEarned)
                    }
                })
        } else {
            showResultDialog(score / 2, sharedPref.getInt("xp", 0) + score / 2, false, sharedPref.getInt("level", 1), coinsEarned)
        }
    }

    private fun showResultDialog(xpEarned: Int, totalXp: Int, levelUp: Boolean, currentLevel: Int, coinsEarned: Int) {
        var msg = "You scored $score points!\n\n🪙 Coins Earned: +$coinsEarned\n⚡ XP Earned: +$xpEarned\nTotal XP: $totalXp"
        if (levelUp) {
            msg += "\n\n🎉 LEVEL UP! You are now Level $currentLevel 🎉"
        }

        AlertDialog.Builder(requireContext())
            .setTitle("⏱️ Time's Up!")
            .setMessage(msg)
            .setPositiveButton("🔄 Play Again") { _, _ ->
                binding.btnAns1.isEnabled = true
                binding.btnAns2.isEnabled = true
                binding.btnAns3.isEnabled = true
                binding.btnAns4.isEnabled = true
                startGame()
            }
            .setNegativeButton("🚪 Back to Menu") { _, _ ->
                findNavController().navigateUp()
            }
            .setCancelable(false)
            .show()
    }

    private fun showExitDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Quit Game?")
            .setMessage("Are you sure you want to exit? Your current progress will be lost.")
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
