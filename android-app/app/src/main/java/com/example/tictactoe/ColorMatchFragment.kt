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
    private var isCorrectMatch = false
    private var timer: CountDownTimer? = null

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

        binding.btnWrong.setOnClickListener { checkAnswer(false) }
        binding.btnCorrect.setOnClickListener { checkAnswer(true) }

        startGame()
    }

    private fun startGame() {
        score = 0
        updateScore()
        nextWord()

        timer = object : CountDownTimer(60000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                binding.tvTimer.text = "${millisUntilFinished / 1000}s"
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
            score += 10
        } else {
            score -= 10
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

        if (userId != -1 && score > 0) {
            val req = GameScoreRequest(userId, score)
            ApiClient.instance.submitScore(req).enqueue(object : Callback<GameScoreResponse> {
                override fun onResponse(call: Call<GameScoreResponse>, response: Response<GameScoreResponse>) {
                    if (response.isSuccessful && response.body()?.status == "success") {
                        val xpEarned = response.body()?.xp_earned ?: 0
                        Toast.makeText(context, "Game Over! You earned $xpEarned XP", Toast.LENGTH_LONG).show()
                    }
                    findNavController().navigateUp()
                }

                override fun onFailure(call: Call<GameScoreResponse>, t: Throwable) {
                    Toast.makeText(context, "Game Over! Score: $score", Toast.LENGTH_LONG).show()
                    findNavController().navigateUp()
                }
            })
        } else {
            Toast.makeText(context, "Game Over! Score: $score", Toast.LENGTH_LONG).show()
            findNavController().navigateUp()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        timer?.cancel()
        _binding = null
    }
}
