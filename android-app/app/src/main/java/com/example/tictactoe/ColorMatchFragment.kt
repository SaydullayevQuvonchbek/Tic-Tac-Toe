package com.example.tictactoe

import android.content.Context
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
    private var timeRemainingMillis = 30000L

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
        timeRemainingMillis = 30000L
        updateScore()
        nextWord()
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

    private fun nextWord() {
        val wordPair = colors.random()
        isCorrectMatch = Math.random() > 0.45
        val colorPair = if (isCorrectMatch) wordPair else colors.filter { it.first != wordPair.first }.random()

        binding.tvColorWord.text = wordPair.first
        binding.tvColorWord.setTextColor(colorPair.second)
    }

    private fun checkAnswer(userSaysYes: Boolean) {
        if (userSaysYes == isCorrectMatch) {
            combo++
            val multiplier = when {
                combo >= 10 -> 5
                combo >= 6  -> 3
                combo >= 3  -> 2
                else        -> 1
            }
            val ptsGained = 10 * multiplier
            score += ptsGained

            val comboStr = if (multiplier > 1) " (COMBO x$multiplier! 🔥)" else ""
            binding.tvInstruction.text = "Correct! +$ptsGained$comboStr (+1s)"
            binding.tvInstruction.setTextColor(Color.parseColor("#34D399"))

            timeRemainingMillis = (timeRemainingMillis + 1000L).coerceAtMost(45000L)
            startTimer()
        } else {
            combo = 0
            score = (score - 5).coerceAtLeast(0)
            binding.tvInstruction.text = "Wrong! Combo Lost ❌"
            binding.tvInstruction.setTextColor(Color.parseColor("#EF4444"))
        }
        updateScore()
        nextWord()
    }

    private fun updateScore() {
        val comboSuffix = if (combo >= 3) " | 🔥 x${if (combo >= 10) 5 else if (combo >= 6) 3 else 2}" else ""
        binding.tvScore.text = "Score: $score$comboSuffix"
    }

    private fun endGame() {
        timer?.cancel()
        binding.btnWrong.isEnabled = false
        binding.btnCorrect.isEnabled = false
        binding.tvTimer.text = "0s"

        val sharedPref = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
        val userId = sharedPref.getInt("user_id", -1)

        val prevBest = sharedPref.getInt("color_match_high_score", 0)
        val isNewRecord = score > prevBest
        if (isNewRecord) {
            sharedPref.edit().putInt("color_match_high_score", score).apply()
        }

        val coinsEarned = (score / 35) * 5
        if (coinsEarned > 0) {
            val curCoins = sharedPref.getInt("coins", 0)
            sharedPref.edit().putInt("coins", curCoins + coinsEarned).apply()
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
            val req = GameScoreRequest(userId, "color_match", score)
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
                        showResultDialog(isNewRecord, resp.xp_earned, resp.new_total_xp, resp.level_up, resp.current_level, coinsEarned)
                    } else {
                        showResultDialog(isNewRecord, score / 2, sharedPref.getInt("xp", 0) + score / 2, false, sharedPref.getInt("level", 1), coinsEarned)
                    }
                }

                override fun onFailure(call: Call<GameScoreResponse>, t: Throwable) {
                    if (!isAdded || _binding == null) return
                    try { pd?.dismiss() } catch (_: Exception) {}
                    t.printStackTrace()
                    showResultDialog(isNewRecord, score / 2, sharedPref.getInt("xp", 0) + score / 2, false, sharedPref.getInt("level", 1), coinsEarned)
                }
            })
        } else {
            showResultDialog(isNewRecord, score / 2, sharedPref.getInt("xp", 0) + score / 2, false, sharedPref.getInt("level", 1), coinsEarned)
        }
    }

    private fun showResultDialog(isNewRecord: Boolean, xpEarned: Int, totalXp: Int, levelUp: Boolean, currentLevel: Int, coinsEarned: Int) {
        var msg = "Final Score: $score"
        if (isNewRecord) {
            msg += "\n🎉 NEW HIGH SCORE! 🎉"
        }
        msg += "\n\n🪙 Coins Earned: +$coinsEarned\n⚡ XP Earned: +$xpEarned\nTotal XP: $totalXp"
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
