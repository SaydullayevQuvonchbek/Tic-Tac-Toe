package com.example.tictactoe

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.tictactoe.databinding.FragmentDashboardBinding
import com.example.tictactoe.network.ApiClient
import com.example.tictactoe.network.AuthRequest
import com.example.tictactoe.network.AuthResponse
import com.example.tictactoe.network.DailyRewardRequest
import com.example.tictactoe.network.DailyRewardResponse
import com.example.tictactoe.network.StoreBuyRequest
import com.example.tictactoe.network.StoreBuyResponse
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnEditProfile.setOnClickListener {
            showEditProfileDialog()
        }

        binding.btnQuickSound.setOnClickListener {
            val nextState = !SoundHelper.isSoundEnabled(requireContext())
            SoundHelper.setSoundEnabled(requireContext(), nextState)
            updateQuickTogglesUI()
            if (nextState) SoundHelper.playMoveSound(requireContext())
            Toast.makeText(context, if (nextState) "🔊 Sound ON" else "🔇 Sound OFF", Toast.LENGTH_SHORT).show()
        }

        binding.btnQuickVibration.setOnClickListener {
            val nextState = !HapticHelper.isVibrationEnabled(requireContext())
            HapticHelper.setVibrationEnabled(requireContext(), nextState)
            updateQuickTogglesUI()
            if (nextState) HapticHelper.performClick(requireContext())
            Toast.makeText(context, if (nextState) "📳 Vibration ON" else "📴 Vibration OFF", Toast.LENGTH_SHORT).show()
        }

        updateQuickTogglesUI()

        binding.cardTicTacToe.setOnClickListener {
            if (ensureProfile()) findNavController().navigate(R.id.action_dashboardFragment_to_welcomeFragment)
        }

        binding.cardMathGame.setOnClickListener {
            if (ensureProfile()) findNavController().navigate(R.id.action_dashboardFragment_to_mathGameFragment)
        }

        binding.cardMemoryGame.setOnClickListener {
            handleGameClick("memory_game", 2, 50, binding.tvMemoryGame, "🧠 Memory Game", R.id.action_dashboardFragment_to_memoryGameFragment)
        }

        binding.cardColorMatch.setOnClickListener {
            handleGameClick("color_match", 3, 100, binding.tvColorMatch, "🎨 Color Match", R.id.action_dashboardFragment_to_colorMatchFragment)
        }

        binding.cardWaterSort.setOnClickListener {
            handleGameClick("water_sort", 3, 120, binding.tvWaterSort, "🧪 Water Sort 💧", R.id.action_dashboardFragment_to_waterSortFragment)
        }

        binding.cardConnect4.setOnClickListener {
            handleGameClick("connect4", 4, 150, binding.tvConnect4, "🔴 Connect 4 🟡", R.id.action_dashboardFragment_to_connect4Fragment)
        }

        binding.card2048.setOnClickListener {
            handleGameClick("game_2048", 5, 200, binding.tv2048, "🔢 2048 Classic", R.id.action_dashboardFragment_to_game2048Fragment)
        }

        binding.cardDropNumber.setOnClickListener {
            handleGameClick("drop_number", 3, 120, binding.tvDropNumber, "🎯 Drop & Merge 2048", R.id.action_dashboardFragment_to_dropNumberFragment)
        }

        binding.cardDotsAndBoxes.setOnClickListener {
            handleGameClick("dots_and_boxes", 2, 100, binding.tvDotsAndBoxes, "🟥 Dots & Boxes", R.id.action_dashboardFragment_to_dotsAndBoxesFragment)
        }

        binding.cardGomoku.setOnClickListener {
            handleGameClick("gomoku", 3, 150, binding.tvGomoku, "⚪⚫ Gomoku (5 in a Row)", R.id.action_dashboardFragment_to_gomokuFragment)
        }

        binding.cardCheckers.setOnClickListener {
            handleGameClick("checkers", 4, 200, binding.tvCheckers, "👑 Shashka (Checkers)", R.id.action_dashboardFragment_to_checkersFragment)
        }

        binding.cardDurak.setOnClickListener {
            handleGameClick("durak", 2, 100, binding.tvDurak, "🃏 Durak (Karta)", R.id.action_dashboardFragment_to_durakFragment)
        }
        
        binding.cardDailyReward.setOnClickListener {
            if (ensureProfile()) claimDailyReward()
        }

        binding.cardLuckyWheel.setOnClickListener {
            if (ensureProfile()) {
                LuckyWheelDialog(requireContext()) { coins, xp ->
                    loadProfile()
                    Toast.makeText(context, "🎁 Claimed +$coins 🪙 and +$xp ⚡!", Toast.LENGTH_SHORT).show()
                }.show()
            }
        }

        initQuestsClickListeners()
    }

    private fun initQuestsClickListeners() {
        binding.btnClaimQuest1.setOnClickListener {
            if (QuestManager.claimQuest(requireContext(), "q1")) {
                Toast.makeText(context, "🎁 +60 Coins & +100 XP Claimed!", Toast.LENGTH_SHORT).show()
                loadProfile()
            }
        }

        binding.btnClaimQuest2.setOnClickListener {
            if (QuestManager.claimQuest(requireContext(), "q2")) {
                Toast.makeText(context, "🎁 +50 Coins & +80 XP Claimed!", Toast.LENGTH_SHORT).show()
                loadProfile()
            }
        }

        binding.btnClaimQuest3.setOnClickListener {
            if (QuestManager.claimQuest(requireContext(), "q3")) {
                Toast.makeText(context, "🎁 +100 Coins & +150 XP Claimed!", Toast.LENGTH_SHORT).show()
                loadProfile()
            }
        }
    }

    private val countdownHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val countdownRunnable = object : Runnable {
        override fun run() {
            if (_binding != null) {
                val timeLeft = QuestManager.getTimeUntilMidnightString()
                binding.tvQuestsCountdown.text = "⏳ $timeLeft"
                countdownHandler.postDelayed(this, 1000)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadProfile()
        countdownHandler.post(countdownRunnable)
    }

    override fun onPause() {
        super.onPause()
        countdownHandler.removeCallbacks(countdownRunnable)
    }
    
    private fun updateGameLocks() {
        val sharedPref = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
        val level = sharedPref.getInt("level", 1)
        
        fun updateLock(key: String, reqLevel: Int, tv: android.widget.TextView, title: String) {
            val isUnlocked = sharedPref.getBoolean("unlocked_$key", false) || level >= reqLevel
            if (isUnlocked) {
                tv.text = title
            } else {
                tv.text = "🔒 $title"
            }
        }
        
        updateLock("dots_and_boxes", 2, binding.tvDotsAndBoxes, "🟥 Dots & Boxes")
        updateLock("gomoku", 3, binding.tvGomoku, "⚪⚫ Gomoku (5 in a Row)")
        updateLock("checkers", 4, binding.tvCheckers, "👑 Shashka (Checkers)")
        updateLock("memory_game", 2, binding.tvMemoryGame, "🧠 Memory Game")
        updateLock("color_match", 3, binding.tvColorMatch, "🎨 Color Match")
        updateLock("water_sort", 3, binding.tvWaterSort, "🧪 Water Sort 💧")
        updateLock("connect4", 4, binding.tvConnect4, "🔴 Connect 4 🟡")
        updateLock("game_2048", 5, binding.tv2048, "🔢 2048 Classic")
        updateLock("drop_number", 3, binding.tvDropNumber, "🎯 Drop 2048")
        updateLock("durak", 2, binding.tvDurak, "🃏 Durak (Karta)")
    }

    private fun handleGameClick(key: String, reqLevel: Int, cost: Int, tv: android.widget.TextView, title: String, actionId: Int) {
        if (!ensureProfile()) return
        
        val sharedPref = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
        val level = sharedPref.getInt("level", 1)
        val coins = sharedPref.getInt("coins", 0)
        val userId = sharedPref.getInt("user_id", -1)
        val isUnlocked = sharedPref.getBoolean("unlocked_$key", false) || level >= reqLevel
        
        if (isUnlocked) {
            findNavController().navigate(actionId)
        } else {
            AlertDialog.Builder(requireContext())
                .setTitle("Game Locked 🔒")
                .setMessage("Reach Level $reqLevel to unlock for free, or buy it now for $cost 🪙.")
                .setPositiveButton("Buy ($cost 🪙)") { _, _ ->
                    if (coins >= cost && userId != -1) {
                        val pd = android.app.ProgressDialog(context)
                        pd.setMessage("Unlocking game...")
                        pd.show()
                        
                        ApiClient.instance.buyItem(StoreBuyRequest(userId, key, cost)).enqueue(object : Callback<StoreBuyResponse> {
                            override fun onResponse(call: Call<StoreBuyResponse>, response: Response<StoreBuyResponse>) {
                                pd.dismiss()
                                if (response.isSuccessful && response.body()?.status == "success") {
                                    val newCoins = response.body()?.new_coin_balance ?: (coins - cost)
                                    sharedPref.edit().apply {
                                        putInt("coins", newCoins)
                                        putBoolean("unlocked_$key", true)
                                        apply()
                                    }
                                    loadProfile()
                                    Toast.makeText(context, "$title Unlocked! 🎉", Toast.LENGTH_SHORT).show()
                                    findNavController().navigate(actionId)
                                } else {
                                    Toast.makeText(context, response.body()?.message ?: "Failed to buy item", Toast.LENGTH_SHORT).show()
                                }
                            }

                            override fun onFailure(call: Call<StoreBuyResponse>, t: Throwable) {
                                pd.dismiss()
                                Toast.makeText(context, "Network Error: ${t.message}", Toast.LENGTH_SHORT).show()
                            }
                        })
                    } else {
                        Toast.makeText(context, "Not enough coins! You have $coins 🪙", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun updateQuickTogglesUI() {
        if (_binding == null) return
        val soundOn = SoundHelper.isSoundEnabled(requireContext())
        val vibOn = HapticHelper.isVibrationEnabled(requireContext())

        binding.btnQuickSound.text = if (soundOn) "🔊" else "🔇"
        binding.btnQuickSound.alpha = if (soundOn) 1.0f else 0.45f

        binding.btnQuickVibration.text = if (vibOn) "📳" else "📴"
        binding.btnQuickVibration.alpha = if (vibOn) 1.0f else 0.45f
    }

    private fun ensureProfile(): Boolean {
        val sharedPref = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
        val username = sharedPref.getString("username", "") ?: ""
        if (username.isEmpty()) {
            showEditProfileDialog()
            return false
        }
        return true
    }

    private fun loadProfile() {
        val sharedPref = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
        val username = sharedPref.getString("username", "") ?: ""
        val level = sharedPref.getInt("level", 1)
        val xp = sharedPref.getInt("xp", 0)
        val coins = sharedPref.getInt("coins", 0)
        val streak = sharedPref.getInt("streak_count", 0)

        if (username.isNotEmpty()) {
            binding.tvUsername.text = username
            binding.tvLevelInfo.text = "Level $level | $xp XP"
            binding.tvStreak.text = "🔥 $streak"
            binding.tvCoins.text = "🪙 $coins"

            val league = QuestManager.getLeagueTier(xp)
            binding.tvLeagueBadge.text = league.first
            binding.tvLeagueBadge.setTextColor(Color.parseColor(league.second))

            updateGameLocks()
            updateDailyQuestsUI()
            updateQuickTogglesUI()

            // Update Best Records
            val wins = sharedPref.getInt("wins", 0)
            val mathScore = sharedPref.getInt("math_high_score", 0)
            val memoryScore = sharedPref.getInt("memory_game_best", 0)
            val colorScore = sharedPref.getInt("color_match_high_score", 0)
            val score2048 = sharedPref.getInt("game_2048_high_score", 0)
            val connect4Wins = sharedPref.getInt("connect4_wins", 0)
            val waterUnlocked = sharedPref.getInt("water_sort_unlocked_level", 1)
            val dotsWins = sharedPref.getInt("dots_and_boxes_wins", 0)
            val gomokuWins = sharedPref.getInt("gomoku_wins", 0)
            val checkersWins = sharedPref.getInt("checkers_wins", 0)
            val durakWins = sharedPref.getInt("durak_wins", 0)

            val mathUnlocked = sharedPref.getInt("math_unlocked_level", 1)
            binding.tvRecordTicTacToe.text = "🏆 Wins: $wins"
            binding.tvRecordMath.text = "⭐ Level $mathUnlocked / 30"
            binding.tvRecordMemory.text = if (memoryScore > 0) "⭐ Best: $memoryScore pts" else "⭐ 30 Levels"
            binding.tvRecordColorMatch.text = if (colorScore > 0) "⭐ High Score: $colorScore" else "⭐ Speed & Focus"
            binding.tvRecord2048.text = if (score2048 > 0) "⭐ Best: $score2048" else "⭐ Reach 2048 Tile"
            binding.tvRecordConnect4.text = "🏆 Wins: $connect4Wins"
            binding.tvRecordWaterSort.text = "⭐ Level $waterUnlocked / 50"
            binding.tvRecordDotsAndBoxes.text = "🏆 Wins: $dotsWins"
            binding.tvRecordGomoku.text = "🏆 Wins: $gomokuWins"
            binding.tvRecordCheckers.text = "🏆 Wins: $checkersWins"
            binding.tvRecordDurak.text = "🏆 Wins: $durakWins"
        } else {
            binding.tvUsername.text = "Guest Player"
            binding.tvLevelInfo.text = "Click edit to set username"
            binding.tvStreak.text = "🔥 0"
            binding.tvCoins.text = "🪙 0"
            showEditProfileDialog()
        }
    }

    private fun updateDailyQuestsUI() {
        val quests = QuestManager.getDailyQuests(requireContext())
        if (quests.size >= 3) {
            val q1 = quests[0]
            binding.tvQuest1Title.text = "${q1.title} (${q1.currentProgress}/${q1.target})"
            binding.pbQuest1.max = q1.target
            binding.pbQuest1.progress = q1.currentProgress
            val claimedText = getString(R.string.btn_claimed)
            val claimText = getString(R.string.btn_claim)

            if (q1.isClaimed) {
                binding.btnClaimQuest1.text = claimedText
                binding.btnClaimQuest1.isEnabled = false
                binding.btnClaimQuest1.alpha = 0.5f
            } else if (q1.isCompleted) {
                binding.btnClaimQuest1.text = claimText
                binding.btnClaimQuest1.isEnabled = true
                binding.btnClaimQuest1.alpha = 1.0f
            } else {
                binding.btnClaimQuest1.text = "+${q1.coinReward} 🪙"
                binding.btnClaimQuest1.isEnabled = false
                binding.btnClaimQuest1.alpha = 0.7f
            }

            val q2 = quests[1]
            binding.tvQuest2Title.text = "${q2.title} (${q2.currentProgress}/${q2.target})"
            binding.pbQuest2.max = q2.target
            binding.pbQuest2.progress = q2.currentProgress
            if (q2.isClaimed) {
                binding.btnClaimQuest2.text = claimedText
                binding.btnClaimQuest2.isEnabled = false
                binding.btnClaimQuest2.alpha = 0.5f
            } else if (q2.isCompleted) {
                binding.btnClaimQuest2.text = claimText
                binding.btnClaimQuest2.isEnabled = true
                binding.btnClaimQuest2.alpha = 1.0f
            } else {
                binding.btnClaimQuest2.text = "+${q2.coinReward} 🪙"
                binding.btnClaimQuest2.isEnabled = false
                binding.btnClaimQuest2.alpha = 0.7f
            }

            val q3 = quests[2]
            binding.tvQuest3Title.text = "${q3.title} (${q3.currentProgress}/${q3.target})"
            binding.pbQuest3.max = q3.target
            binding.pbQuest3.progress = q3.currentProgress
            if (q3.isClaimed) {
                binding.btnClaimQuest3.text = claimedText
                binding.btnClaimQuest3.isEnabled = false
                binding.btnClaimQuest3.alpha = 0.5f
            } else if (q3.isCompleted) {
                binding.btnClaimQuest3.text = claimText
                binding.btnClaimQuest3.isEnabled = true
                binding.btnClaimQuest3.alpha = 1.0f
            } else {
                binding.btnClaimQuest3.text = "+${q3.coinReward} 🪙"
                binding.btnClaimQuest3.isEnabled = false
                binding.btnClaimQuest3.alpha = 0.7f
            }
        }
    }

    private fun showEditProfileDialog() {
        val input = EditText(context)
        input.hint = "Enter Unique Username"
        AlertDialog.Builder(requireContext())
            .setTitle("Profile Setup")
            .setMessage("Set your username. It must be unique!")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    updateProfileOnServer(newName)
                } else {
                    Toast.makeText(context, "Username cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
            .setCancelable(false)
            .show()
    }

    private fun updateProfileOnServer(newUsername: String) {
        val sharedPref = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
        val deviceId = sharedPref.getString("device_id", "") ?: java.util.UUID.randomUUID().toString()
        sharedPref.edit().putString("device_id", deviceId).apply()

        val pd = android.app.ProgressDialog(context)
        pd.setMessage("Updating profile...")
        pd.show()

        ApiClient.instance.auth(AuthRequest(deviceId, newUsername))
            .enqueue(object : Callback<AuthResponse> {
                override fun onResponse(call: Call<AuthResponse>, response: Response<AuthResponse>) {
                    pd.dismiss()
                    if (response.isSuccessful && response.body()?.status == "success") {
                        val user = response.body()?.user
                        if (user != null) {
                            sharedPref.edit().apply {
                                putInt("user_id", user.id)
                                putString("username", user.username)
                                putInt("level", user.level)
                                putInt("xp", user.xp)
                                putInt("coins", user.coins)
                                putInt("streak_count", user.streak_count)
                                if (user.unlocked_games != null) {
                                    for (item in user.unlocked_games) {
                                        putBoolean("unlocked_$item", true)
                                    }
                                }
                                apply()
                            }
                            loadProfile()
                            Toast.makeText(context, "Welcome, ${user.username}!", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "Failed to update profile", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<AuthResponse>, t: Throwable) {
                    pd.dismiss()
                    Toast.makeText(context, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun claimDailyReward() {
        val sharedPref = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
        val userId = sharedPref.getInt("user_id", -1)
        if (userId == -1) return

        val pd = android.app.ProgressDialog(context)
        pd.setMessage("Claiming daily reward...")
        pd.show()

        ApiClient.instance.claimDailyReward(DailyRewardRequest(userId))
            .enqueue(object : Callback<DailyRewardResponse> {
                override fun onResponse(call: Call<DailyRewardResponse>, response: Response<DailyRewardResponse>) {
                    pd.dismiss()
                    if (response.isSuccessful && response.body()?.status == "success") {
                        val coinsGained = response.body()?.reward_coins ?: 0
                        val totalCoins = response.body()?.new_total_coins ?: 0

                        val curStreak = sharedPref.getInt("streak_count", 0) + 1
                        sharedPref.edit().apply {
                            putInt("coins", totalCoins)
                            putInt("streak_count", curStreak)
                            apply()
                        }
                        loadProfile()
                        Toast.makeText(context, "Claimed +$coinsGained Coins! 🔥 Streak: $curStreak", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, response.body()?.message ?: "Reward already claimed today!", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<DailyRewardResponse>, t: Throwable) {
                    pd.dismiss()
                    Toast.makeText(context, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
