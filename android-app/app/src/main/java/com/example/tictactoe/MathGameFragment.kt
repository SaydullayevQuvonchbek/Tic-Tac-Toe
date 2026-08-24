package com.example.tictactoe

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
    private var correctAnswer = 0
    private var timer: CountDownTimer? = null

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
        updateScore()
        generateQuestion()

        timer = object : CountDownTimer(30000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                binding.tvTimer.text = "${millisUntilFinished / 1000}s"
            }

            override fun onFinish() {
                binding.tvTimer.text = "0s"
                endGame()
            }
        }.start()
    }

    private fun generateQuestion() {
        val a = (10..50).random()
        val b = (10..50).random()
        val isAdd = (0..1).random() == 1

        if (isAdd) {
            correctAnswer = a + b
            binding.tvQuestion.text = "$a + $b = ?"
        } else {
            val max = maxOf(a, b)
            val min = minOf(a, b)
            correctAnswer = max - min
            binding.tvQuestion.text = "$max - $min = ?"
        }

        val answers = mutableListOf(correctAnswer)
        while (answers.size < 4) {
            val wrong = correctAnswer + (-10..10).random()
            if (wrong != correctAnswer && wrong >= 0 && !answers.contains(wrong)) {
                answers.add(wrong)
            }
        }
        answers.shuffle()

        binding.btnAns1.text = answers[0].toString()
        binding.btnAns2.text = answers[1].toString()
        binding.btnAns3.text = answers[2].toString()
        binding.btnAns4.text = answers[3].toString()
        
        binding.tvFeedback.text = ""
    }

    private fun checkAnswer(ans: Int) {
        if (ans == correctAnswer) {
            score += 10
            binding.tvFeedback.text = "Correct! +10"
            binding.tvFeedback.setTextColor(android.graphics.Color.parseColor("#34D399"))
        } else {
            score -= 5
            binding.tvFeedback.text = "Wrong! -5"
            binding.tvFeedback.setTextColor(android.graphics.Color.parseColor("#F43F5E"))
        }
        updateScore()
        binding.root.postDelayed({ generateQuestion() }, 500)
    }

    private fun updateScore() {
        binding.tvScore.text = "Score: $score"
    }

    private fun endGame() {
        binding.btnAns1.isEnabled = false
        binding.btnAns2.isEnabled = false
        binding.btnAns3.isEnabled = false
        binding.btnAns4.isEnabled = false

        if (score > 0) {
            submitScore()
        } else {
            showResultDialog(0, 0, false, 0)
        }
    }

    private fun submitScore() {
        val pd = android.app.ProgressDialog(context)
        pd.setMessage("Saving Score...")
        pd.setCancelable(false)
        pd.show()

        val sharedPref = requireActivity().getSharedPreferences("TicTacToePrefs", android.content.Context.MODE_PRIVATE)
        val userId = sharedPref.getInt("user_id", -1)

        ApiClient.instance.submitGameScore(GameScoreRequest(userId, "math", score))
            .enqueue(object : Callback<GameScoreResponse> {
                override fun onResponse(call: Call<GameScoreResponse>, response: Response<GameScoreResponse>) {
                    pd.dismiss()
                    if (response.isSuccessful && response.body()?.status == "success") {
                        val resp = response.body()!!
                        val prevBest = sharedPref.getInt("math_high_score", 0)
                        if (score > prevBest) {
                            sharedPref.edit().putInt("math_high_score", score).apply()
                        }
                        // Update local shared prefs
                        sharedPref.edit()
                            .putInt("level", resp.current_level)
                            .putInt("xp", resp.new_total_xp)
                            .apply()

                        showResultDialog(resp.xp_earned, resp.new_total_xp, resp.level_up, resp.current_level)
                    } else {
                        Toast.makeText(context, "Failed to submit score", Toast.LENGTH_SHORT).show()
                        findNavController().navigateUp()
                    }
                }

                override fun onFailure(call: Call<GameScoreResponse>, t: Throwable) {
                    pd.dismiss()
                    Toast.makeText(context, "Network error", Toast.LENGTH_SHORT).show()
                    findNavController().navigateUp()
                }
            })
    }

    private fun showResultDialog(xpEarned: Int, totalXp: Int, levelUp: Boolean, currentLevel: Int) {
        var msg = "You scored $score points!\n\nEarned: +$xpEarned XP\nTotal XP: $totalXp"
        if (levelUp) {
            msg += "\n\n🎉 LEVEL UP! You are now Level $currentLevel 🎉"
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Time's Up!")
            .setMessage(msg)
            .setPositiveButton("Back to Menu") { _, _ ->
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
