package com.example.tictactoe

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
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

    companion object {
        const val MAX_LEVELS = 30
    }

    private var currentPlayingLevel = 1
    private var unlockedLevel = 1
    private var correctCount = 0
    private var targetCount = 5
    private var combo = 0
    private var correctAnswer = ""
    private var timer: CountDownTimer? = null
    private var timeRemainingSeconds = 20
    private var totalTimeSeconds = 20

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMathGameBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadProgress()
        initSetupUI()

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackNavigation()
            }
        })

        binding.btnSetupBack.setOnClickListener { findNavController().navigateUp() }
        binding.btnGameplayBack.setOnClickListener { handleBackNavigation() }

        val buttons = listOf(binding.btnAns1, binding.btnAns2, binding.btnAns3, binding.btnAns4)
        for (btn in buttons) {
            btn.setOnClickListener { checkAnswer((it as Button).text.toString()) }
        }

        // Power-Up 1: 50/50 (20 Coins)
        binding.btn5050.setOnClickListener {
            use5050Powerup()
        }

        // Power-Up 2: +15s Time (30 Coins)
        binding.btnAddTime.setOnClickListener {
            useAddTimePowerup()
        }
    }

    private fun handleBackNavigation() {
        if (binding.gameplayContainer.visibility == View.VISIBLE) {
            AlertDialog.Builder(requireContext())
                .setTitle("Exit Level?")
                .setMessage("Do you want to return to the level map?")
                .setPositiveButton("Yes, Exit") { _, _ ->
                    timer?.cancel()
                    showSetupScreen()
                }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            findNavController().navigateUp()
        }
    }

    private fun loadProgress() {
        val prefs = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
        unlockedLevel = prefs.getInt("math_unlocked_level", 1).coerceIn(1, MAX_LEVELS)
    }

    private fun saveLevelWin(level: Int, stars: Int) {
        val prefs = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
        val oldStars = prefs.getInt("math_level_${level}_stars", 0)
        if (stars > oldStars) {
            prefs.edit().putInt("math_level_${level}_stars", stars).apply()
        }

        if (level >= unlockedLevel && unlockedLevel < MAX_LEVELS) {
            unlockedLevel = level + 1
            prefs.edit().putInt("math_unlocked_level", unlockedLevel).apply()
        }

        val coinsEarned = when (stars) { 3 -> 50; 2 -> 35; else -> 20 }
        val curCoins = prefs.getInt("coins", 0)
        val curXp = prefs.getInt("xp", 0)
        prefs.edit()
            .putInt("coins", curCoins + coinsEarned)
            .putInt("xp", curXp + coinsEarned * 2)
            .apply()

        QuestManager.recordGamePlayed(requireContext(), "math", false, true)
    }

    private fun initSetupUI() {
        showSetupScreen()
        updateTotalStarsBadge()

        binding.rvLevels.layoutManager = GridLayoutManager(requireContext(), 5)
        binding.rvLevels.adapter = LevelsAdapter()

        binding.btnContinueLevel.text = "CONTINUE LEVEL $unlockedLevel ▶"
        binding.btnContinueLevel.setOnClickListener {
            startLevel(unlockedLevel)
        }
    }

    private fun updateTotalStarsBadge() {
        val prefs = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
        var totalStars = 0
        for (i in 1..MAX_LEVELS) {
            totalStars += prefs.getInt("math_level_${i}_stars", 0)
        }
        binding.tvTotalStars.text = "⭐ $totalStars / ${MAX_LEVELS * 3}"
    }

    private fun startLevel(level: Int) {
        currentPlayingLevel = level
        correctCount = 0
        targetCount = (5 + (level / 5)).coerceAtMost(12)
        totalTimeSeconds = (18 + (level % 3) * 2).coerceIn(15, 25)
        timeRemainingSeconds = totalTimeSeconds
        combo = 0

        showGameplayScreen()
        binding.tvLevelTitle.text = "✖️ Level $currentPlayingLevel"
        binding.tvTarget.text = "🎯 0/$targetCount Correct"
        binding.tvFeedback.text = "Choose correct answer"
        binding.tvFeedback.setTextColor(Color.parseColor("#94A3B8"))

        resetAnswerButtons()
        generateQuestion()
        startTimer()
    }

    private fun startTimer() {
        timer?.cancel()
        binding.tvTimer.text = "⏳ ${timeRemainingSeconds}s"

        timer = object : CountDownTimer((timeRemainingSeconds * 1000).toLong(), 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeRemainingSeconds = (millisUntilFinished / 1000).toInt()
                binding.tvTimer.text = "⏳ ${timeRemainingSeconds}s"
            }

            override fun onFinish() {
                timeRemainingSeconds = 0
                binding.tvTimer.text = "⏳ 0s"
                handleTimeOut()
            }
        }.start()
    }

    private fun generateQuestion() {
        resetAnswerButtons()

        when {
            currentPlayingLevel <= 5 -> {
                // Tier 1: Addition & Subtraction
                val isAdd = (0..1).random() == 0
                if (isAdd) {
                    val a = (10..40).random()
                    val b = (5..35).random()
                    correctAnswer = (a + b).toString()
                    binding.tvQuestion.text = "$a + $b = ?"
                } else {
                    val a = (20..50).random()
                    val b = (5..a).random()
                    correctAnswer = (a - b).toString()
                    binding.tvQuestion.text = "$a - $b = ?"
                }
                setNumberChoices(correctAnswer.toInt())
            }
            currentPlayingLevel <= 10 -> {
                // Tier 2: Multiplication & Quick Doubles
                val a = (3..11).random()
                val b = (3..10).random()
                correctAnswer = (a * b).toString()
                binding.tvQuestion.text = "$a × $b = ?"
                setNumberChoices(correctAnswer.toInt())
            }
            currentPlayingLevel <= 15 -> {
                // Tier 3: Division
                val b = (2..9).random()
                val ans = (3..12).random()
                val a = b * ans
                correctAnswer = ans.toString()
                binding.tvQuestion.text = "$a ÷ $b = ?"
                setNumberChoices(ans)
            }
            currentPlayingLevel <= 20 -> {
                // Tier 4: Missing Operator ?
                val ops = listOf("+", "-", "×", "÷")
                val chosenOp = ops.random()
                when (chosenOp) {
                    "+" -> {
                        val a = (12..45).random()
                        val b = (8..30).random()
                        val res = a + b
                        binding.tvQuestion.text = "$a [?] $b = $res"
                    }
                    "-" -> {
                        val a = (25..60).random()
                        val b = (5..20).random()
                        val res = a - b
                        binding.tvQuestion.text = "$a [?] $b = $res"
                    }
                    "×" -> {
                        val a = (3..9).random()
                        val b = (3..9).random()
                        val res = a * b
                        binding.tvQuestion.text = "$a [?] $b = $res"
                    }
                    else -> {
                        val b = (2..8).random()
                        val res = (2..9).random()
                        val a = b * res
                        binding.tvQuestion.text = "$a [?] $b = $res"
                    }
                }
                correctAnswer = chosenOp
                setOperatorChoices()
            }
            else -> {
                // Tier 5 & Boss: Grandmaster Mix & Mixed Operations
                val a = (4..12).random()
                val b = (2..6).random()
                val c = (5..20).random()
                val res = (a * b) + c
                correctAnswer = res.toString()
                binding.tvQuestion.text = "($a × $b) + $c = ?"
                setNumberChoices(res)
            }
        }
    }

    private fun setNumberChoices(correct: Int) {
        val choices = mutableListOf(correct)
        while (choices.size < 4) {
            val offset = (-15..15).random()
            val wrong = correct + offset
            if (wrong != correct && wrong >= 0 && !choices.contains(wrong)) {
                choices.add(wrong)
            }
        }
        choices.shuffle()

        binding.btnAns1.text = choices[0].toString()
        binding.btnAns2.text = choices[1].toString()
        binding.btnAns3.text = choices[2].toString()
        binding.btnAns4.text = choices[3].toString()
    }

    private fun setOperatorChoices() {
        val ops = listOf("+", "-", "×", "÷").shuffled()
        binding.btnAns1.text = ops[0]
        binding.btnAns2.text = ops[1]
        binding.btnAns3.text = ops[2]
        binding.btnAns4.text = ops[3]
    }

    private fun resetAnswerButtons() {
        val buttons = listOf(binding.btnAns1, binding.btnAns2, binding.btnAns3, binding.btnAns4)
        for (btn in buttons) {
            btn.visibility = View.VISIBLE
            btn.isEnabled = true
            btn.alpha = 1.0f
        }
    }

    private fun checkAnswer(ans: String) {
        if (ans == correctAnswer) {
            combo++
            correctCount++
            binding.tvTarget.text = "🎯 $correctCount/$targetCount Correct"

            HapticHelper.performClick(requireContext())
            SoundHelper.playMoveSound(requireContext())

            val comboStr = if (combo >= 3) " | 🔥 Combo x$combo!" else ""
            binding.tvFeedback.text = "Correct! 🎯$comboStr"
            binding.tvFeedback.setTextColor(Color.parseColor("#34D399"))

            if (correctCount >= targetCount) {
                handleLevelWin()
            } else {
                binding.root.postDelayed({ generateQuestion() }, 250)
            }
        } else {
            combo = 0
            HapticHelper.performHeavyImpact(requireContext())
            binding.tvFeedback.text = "Wrong! ❌ Try next"
            binding.tvFeedback.setTextColor(Color.parseColor("#EF4444"))
            binding.root.postDelayed({ generateQuestion() }, 300)
        }
    }

    private fun handleLevelWin() {
        timer?.cancel()

        val ratio = timeRemainingSeconds.toFloat() / totalTimeSeconds.toFloat()
        val stars = when {
            ratio >= 0.40f -> 3
            ratio >= 0.15f -> 2
            else -> 1
        }

        saveLevelWin(currentPlayingLevel, stars)
        updateTotalStarsBadge()

        ConfettiView.show(binding.root as ViewGroup)
        HapticHelper.performVictory(requireContext())
        SoundHelper.playVictorySound(requireContext())

        val starsStr = "⭐".repeat(stars) + "☆".repeat(3 - stars)
        val coinsEarned = when (stars) { 3 -> 50; 2 -> 35; else -> 20 }

        AlertDialog.Builder(requireContext())
            .setTitle("🎉 Level $currentPlayingLevel Completed!")
            .setMessage("Rating: $starsStr\nReward: +$coinsEarned Coins 🪙 | +${coinsEarned * 2} XP ⚡")
            .setCancelable(false)
            .setPositiveButton(if (currentPlayingLevel < MAX_LEVELS) "NEXT LEVEL ▶" else "CONTINUE") { _, _ ->
                if (currentPlayingLevel < MAX_LEVELS) {
                    startLevel(currentPlayingLevel + 1)
                } else {
                    showSetupScreen()
                }
            }
            .setNegativeButton("LEVEL MAP 🗺️") { _, _ ->
                showSetupScreen()
            }
            .show()
    }

    private fun handleTimeOut() {
        val prefs = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
        val curCoins = prefs.getInt("coins", 0)

        AlertDialog.Builder(requireContext())
            .setTitle("⏱️ Time's Up!")
            .setMessage("You were so close ($correctCount/$targetCount)!\n\nBuy +15s Extra Time for 30 🪙 to keep going?")
            .setCancelable(false)
            .setPositiveButton("⏱️ +15s (30 🪙)") { _, _ ->
                if (curCoins >= 30) {
                    prefs.edit().putInt("coins", curCoins - 30).apply()
                    timeRemainingSeconds += 15
                    startTimer()
                    Toast.makeText(context, "+15s Extra Time Added! ⏳", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Not enough coins! (Needs 30 🪙)", Toast.LENGTH_SHORT).show()
                    showSetupScreen()
                }
            }
            .setNegativeButton("RETRY 🔄") { _, _ ->
                startLevel(currentPlayingLevel)
            }
            .setNeutralButton("EXIT MAP", { _, _ ->
                showSetupScreen()
            })
            .show()
    }

    private fun use5050Powerup() {
        val prefs = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
        val coins = prefs.getInt("coins", 0)
        if (coins < 20) {
            Toast.makeText(context, "Not enough coins! (Needs 20 🪙)", Toast.LENGTH_SHORT).show()
            return
        }

        prefs.edit().putInt("coins", coins - 20).apply()
        HapticHelper.performHeavyImpact(requireContext())
        SoundHelper.playCaptureSound(requireContext())

        val buttons = listOf(binding.btnAns1, binding.btnAns2, binding.btnAns3, binding.btnAns4)
        val wrongButtons = buttons.filter { it.text != correctAnswer }
        wrongButtons.shuffled().take(2).forEach {
            it.visibility = View.INVISIBLE
        }
        Toast.makeText(context, "💣 50/50 Bomb Used (-20 🪙)!", Toast.LENGTH_SHORT).show()
    }

    private fun useAddTimePowerup() {
        val prefs = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
        val coins = prefs.getInt("coins", 0)
        if (coins < 30) {
            Toast.makeText(context, "Not enough coins! (Needs 30 🪙)", Toast.LENGTH_SHORT).show()
            return
        }

        prefs.edit().putInt("coins", coins - 30).apply()
        HapticHelper.performClick(requireContext())
        SoundHelper.playRewardSound(requireContext())

        timeRemainingSeconds += 15
        binding.tvTimer.text = "⏳ ${timeRemainingSeconds}s"
        Toast.makeText(context, "⏱️ +15s Added (-30 🪙)!", Toast.LENGTH_SHORT).show()
    }

    private fun showSetupScreen() {
        binding.setupContainer.visibility = View.VISIBLE
        binding.gameplayContainer.visibility = View.GONE
        loadProgress()
        updateTotalStarsBadge()
        binding.btnContinueLevel.text = "CONTINUE LEVEL $unlockedLevel ▶"
        binding.rvLevels.adapter?.notifyDataSetChanged()
    }

    private fun showGameplayScreen() {
        binding.setupContainer.visibility = View.GONE
        binding.gameplayContainer.visibility = View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        timer?.cancel()
        _binding = null
    }

    // ==========================================
    // RECYCLERVIEW ADAPTER FOR 30-LEVEL MAP
    // ==========================================
    private inner class LevelsAdapter : RecyclerView.Adapter<LevelsAdapter.LevelViewHolder>() {

        inner class LevelViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvLevelNum: TextView = itemView.findViewById(R.id.tvLevelNumber)
            val tvStars: TextView = itemView.findViewById(R.id.tvLevelStars)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LevelViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_water_sort_level, parent, false)
            return LevelViewHolder(view)
        }

        override fun onBindViewHolder(holder: LevelViewHolder, position: Int) {
            val level = position + 1
            val isUnlocked = level <= unlockedLevel
            val prefs = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
            val stars = prefs.getInt("math_level_${level}_stars", 0)

            holder.tvLevelNum.text = level.toString()

            if (isUnlocked) {
                holder.itemView.alpha = 1.0f
                val starsStr = "⭐".repeat(stars) + "☆".repeat(3 - stars)
                holder.tvStars.text = starsStr
                holder.tvStars.setTextColor(Color.parseColor("#F59E0B"))

                holder.itemView.setOnClickListener {
                    HapticHelper.performClick(requireContext())
                    startLevel(level)
                }
            } else {
                holder.itemView.alpha = 0.45f
                holder.tvStars.text = "🔒"
                holder.tvStars.setTextColor(Color.parseColor("#94A3B8"))
                holder.itemView.setOnClickListener {
                    Toast.makeText(context, "Complete Level ${level - 1} to unlock! 🔒", Toast.LENGTH_SHORT).show()
                }
            }
        }

        override fun getItemCount(): Int = MAX_LEVELS
    }
}
