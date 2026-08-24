package com.example.tictactoe

import android.animation.AnimatorInflater
import android.animation.AnimatorSet
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.tictactoe.databinding.FragmentMemoryGameBinding
import com.example.tictactoe.network.ApiClient
import com.example.tictactoe.network.GameScoreRequest
import com.example.tictactoe.network.GameScoreResponse
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MemoryGameFragment : Fragment() {

    private var _binding: FragmentMemoryGameBinding? = null
    private val binding get() = _binding!!

    private val emojis = listOf("🍎", "🍌", "🍉", "🍇", "🍓", "🍒", "🍍", "🥝")
    private lateinit var cards: List<String>
    
    private var firstCard: CardView? = null
    private var secondCard: CardView? = null
    private var isProcessing = false
    private var matches = 0
    private var timer: CountDownTimer? = null
    private var timeRemaining = 60

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMemoryGameBinding.inflate(inflater, container, false)
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

        showSetupDialog()
    }

    private fun showSetupDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_memory_setup, null)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .create()

        val rgTheme = dialogView.findViewById<android.widget.RadioGroup>(R.id.rgTheme)
        val rgDifficulty = dialogView.findViewById<android.widget.RadioGroup>(R.id.rgDifficulty)
        val btnStart = dialogView.findViewById<android.widget.Button>(R.id.btnStart)

        btnStart.setOnClickListener {
            val isAnimals = rgTheme.checkedRadioButtonId == R.id.rbAnimals
            val (rows, cols) = when (rgDifficulty.checkedRadioButtonId) {
                R.id.rbEasy -> 4 to 2 // 8 cards (4 pairs)
                R.id.rbMedium -> 4 to 3 // 12 cards (6 pairs)
                R.id.rbHard -> 4 to 4 // 16 cards (8 pairs)
                R.id.rbPro -> 5 to 4 // 20 cards (10 pairs)
                R.id.rbExpert -> 6 to 5 // 30 cards (15 pairs)
                else -> 4 to 4
            }
            dialog.dismiss()
            setupBoard(rows, cols, isAnimals)
            startTimer()
        }
        dialog.show()
    }

    private var totalPairs = 8

    private fun setupBoard(rows: Int, cols: Int, isAnimals: Boolean) {
        val themeEmojis = if (isAnimals) {
            listOf("🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼", "🐨", "🐯", "🦁", "🐮", "🐷", "🐸", "🐵", "🐧")
        } else {
            listOf("🍎", "🍌", "🍉", "🍇", "🍓", "🍒", "🍍", "🥝", "🍑", "🥭", "🍏", "🍐", "🍋", "🍊", "🍈", "🥥")
        }

        totalPairs = (rows * cols) / 2
        binding.tvMatches.text = "Matches: 0/$totalPairs"
        
        val selectedEmojis = themeEmojis.take(totalPairs)
        cards = (selectedEmojis + selectedEmojis).shuffled()
        
        binding.gridMemory.rowCount = rows
        binding.gridMemory.columnCount = cols

        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        
        // Top bar takes about 80dp. We leave some padding at bottom (32dp) and sides (32dp)
        val density = displayMetrics.density
        val availableWidth = screenWidth - (32 * density).toInt()
        val availableHeight = screenHeight - (120 * density).toInt()

        val maxCardWidth = availableWidth / cols
        val maxCardHeight = availableHeight / rows
        
        // Pick the smaller one so it's a square and fits both dimensions
        val cellSize = Math.min(maxCardWidth, maxCardHeight)
        
        // 4dp margin on each side = 8dp total per cell
        val marginPx = (4 * density).toInt()
        val cardSize = cellSize - (marginPx * 2)

        for (i in 0 until (rows * cols)) {
            val card = CardView(requireContext()).apply {
                val row = i / cols
                val col = i % cols
                val params = GridLayout.LayoutParams(
                    GridLayout.spec(row),
                    GridLayout.spec(col)
                )
                params.width = cardSize
                params.height = cardSize
                params.setMargins(marginPx, marginPx, marginPx, marginPx)
                layoutParams = params
                setCardBackgroundColor(android.graphics.Color.parseColor("#312E81"))
                radius = 16f
                cardElevation = 8f
                tag = cards[i]
            }

            val tv = TextView(requireContext()).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                gravity = android.view.Gravity.CENTER
                textSize = 32f
                text = ""
                tag = "tv"
            }
            card.addView(tv)
            
            card.setOnClickListener { onCardClicked(card) }
            binding.gridMemory.addView(card)
        }
    }

    private fun onCardClicked(card: CardView) {
        if (isProcessing || card.alpha < 1f || card == firstCard) return

        flipCard(card, true)

        if (firstCard == null) {
            firstCard = card
        } else {
            secondCard = card
            isProcessing = true
            
            val firstEmoji = firstCard?.tag as String
            val secondEmoji = secondCard?.tag as String

            if (firstEmoji == secondEmoji) {
                // Match
                binding.root.postDelayed({
                    firstCard?.animate()?.alpha(0f)?.setDuration(300)?.start()
                    secondCard?.animate()?.alpha(0f)?.setDuration(300)?.start()
                    
                    matches++
                    binding.tvMatches.text = "Matches: $matches/$totalPairs"
                    
                    firstCard = null
                    secondCard = null
                    isProcessing = false
                    
                    if (matches == totalPairs) {
                        endGame(true)
                    }
                }, 500)
            } else {
                // No Match
                binding.root.postDelayed({
                    flipCard(firstCard!!, false)
                    flipCard(secondCard!!, false)
                    
                    firstCard = null
                    secondCard = null
                    isProcessing = false
                }, 800)
            }
        }
    }

    private fun flipCard(card: CardView, faceUp: Boolean) {
        val tv = card.findViewWithTag<TextView>("tv")
        
        card.animate().rotationYBy(90f).setDuration(150).withEndAction {
            if (faceUp) {
                tv.text = card.tag as String
                card.setCardBackgroundColor(android.graphics.Color.parseColor("#ffffff"))
            } else {
                tv.text = ""
                card.setCardBackgroundColor(android.graphics.Color.parseColor("#312E81"))
            }
            card.animate().rotationYBy(90f).setDuration(150).start()
        }.start()
    }

    private fun startTimer() {
        timer = object : CountDownTimer(60000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeRemaining = (millisUntilFinished / 1000).toInt()
                binding.tvTimer.text = "${timeRemaining}s"
            }

            override fun onFinish() {
                binding.tvTimer.text = "0s"
                endGame(false)
            }
        }.start()
    }

    private fun endGame(win: Boolean) {
        timer?.cancel()
        for (i in 0 until binding.gridMemory.childCount) {
            binding.gridMemory.getChildAt(i).setOnClickListener(null)
        }

        if (win) {
            // Score based on remaining time
            val score = 100 + (timeRemaining * 2)
            submitScore(score)
        } else {
            showResultDialog(0, 0, false, 0, false)
        }
    }

    private fun submitScore(score: Int) {
        val pd = android.app.ProgressDialog(context)
        pd.setMessage("Saving Score...")
        pd.setCancelable(false)
        pd.show()

        val sharedPref = requireActivity().getSharedPreferences("TicTacToePrefs", android.content.Context.MODE_PRIVATE)
        val userId = sharedPref.getInt("user_id", -1)

        ApiClient.instance.submitGameScore(GameScoreRequest(userId, "memory", score))
            .enqueue(object : Callback<GameScoreResponse> {
                override fun onResponse(call: Call<GameScoreResponse>, response: Response<GameScoreResponse>) {
                    pd.dismiss()
                    if (response.isSuccessful && response.body()?.status == "success") {
                        val resp = response.body()!!
                        sharedPref.edit()
                            .putInt("level", resp.current_level)
                            .putInt("xp", resp.new_total_xp)
                            .apply()

                        showResultDialog(resp.xp_earned, resp.new_total_xp, resp.level_up, resp.current_level, true)
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

    private fun showResultDialog(xpEarned: Int, totalXp: Int, levelUp: Boolean, currentLevel: Int, win: Boolean) {
        var msg = if (win) "You cleared the board!\n\nEarned: +$xpEarned XP\nTotal XP: $totalXp" else "Time's up! Try again."
        if (levelUp) {
            msg += "\n\n🎉 LEVEL UP! You are now Level $currentLevel 🎉"
        }

        AlertDialog.Builder(requireContext())
            .setTitle(if (win) "Victory!" else "Game Over")
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
