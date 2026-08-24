package com.example.tictactoe

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.tictactoe.databinding.FragmentDashboardBinding
import com.example.tictactoe.network.ApiClient
import com.example.tictactoe.network.AuthRequest
import com.example.tictactoe.network.AuthResponse
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

        loadProfile()

        binding.btnEditProfile.setOnClickListener {
            showEditProfileDialog()
        }

        binding.cardTicTacToe.setOnClickListener {
            if (ensureProfile()) {
                findNavController().navigate(R.id.action_dashboardFragment_to_welcomeFragment)
            }
        }

        binding.cardMathGame.setOnClickListener {
            if (ensureProfile()) {
                findNavController().navigate(R.id.action_dashboardFragment_to_mathGameFragment)
            }
        }

        binding.cardMemoryGame.setOnClickListener {
            handleGameClick("memory_game", 2, 50, binding.tvMemoryGame, "🧠 Memory Game", R.id.action_dashboardFragment_to_memoryGameFragment)
        }
        
        binding.cardColorMatch.setOnClickListener {
            handleGameClick("color_match", 3, 100, binding.tvColorMatch, "🎨 Color Match", R.id.action_dashboardFragment_to_colorMatchFragment)
        }

        binding.card2048.setOnClickListener {
            handleGameClick("game_2048", 5, 250, binding.tv2048, "🔢 2048 Puzzle", R.id.action_dashboardFragment_to_game2048Fragment)
        }
        
        binding.cardDailyReward.setOnClickListener {
            if (ensureProfile()) claimDailyReward()
        }
    }
    
    private fun updateGameLocks() {
        val sharedPref = requireActivity().getSharedPreferences("TicTacToePrefs", android.content.Context.MODE_PRIVATE)
        val level = sharedPref.getInt("level", 1)
        
        fun updateLock(key: String, reqLevel: Int, tv: android.widget.TextView, title: String) {
            val isUnlocked = sharedPref.getBoolean("unlocked_$key", false) || level >= reqLevel
            if (isUnlocked) {
                tv.text = title
            } else {
                tv.text = "🔒 $title"
            }
        }
        
        updateLock("memory_game", 2, binding.tvMemoryGame, "🧠 Memory Game")
        updateLock("color_match", 3, binding.tvColorMatch, "🎨 Color Match")
        updateLock("game_2048", 5, binding.tv2048, "🔢 2048 Puzzle")
    }

    private fun handleGameClick(key: String, reqLevel: Int, cost: Int, tv: android.widget.TextView, title: String, actionId: Int) {
        if (!ensureProfile()) return
        
        val sharedPref = requireActivity().getSharedPreferences("TicTacToePrefs", android.content.Context.MODE_PRIVATE)
        val level = sharedPref.getInt("level", 1)
        val coins = sharedPref.getInt("coins", 0)
        val isUnlocked = sharedPref.getBoolean("unlocked_$key", false) || level >= reqLevel
        
        if (isUnlocked) {
            findNavController().navigate(actionId)
        } else {
            AlertDialog.Builder(requireContext())
                .setTitle("Game Locked 🔒")
                .setMessage("Reach Level $reqLevel to unlock for free, or buy it now for $cost 🪙.")
                .setPositiveButton("Buy ($cost 🪙)") { _, _ ->
                    if (coins >= cost) {
                        sharedPref.edit()
                            .putInt("coins", coins - cost)
                            .putBoolean("unlocked_$key", true)
                            .apply()
                        loadProfile()
                        updateGameLocks()
                        Toast.makeText(context, "Game Unlocked!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Not enough coins!", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun ensureProfile(): Boolean {
        val sharedPref = requireActivity().getSharedPreferences("TicTacToePrefs", android.content.Context.MODE_PRIVATE)
        val username = sharedPref.getString("username", "") ?: ""
        if (username.isEmpty()) {
            showEditProfileDialog()
            return false
        }
        return true
    }

    private fun loadProfile() {
        val sharedPref = requireActivity().getSharedPreferences("TicTacToePrefs", android.content.Context.MODE_PRIVATE)
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
            updateGameLocks()
        } else {
            binding.tvUsername.text = "Guest Player"
            binding.tvLevelInfo.text = "Click edit to set username"
            binding.tvStreak.text = "🔥 0"
            binding.tvCoins.text = "🪙 0"
            showEditProfileDialog()
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
            .setNegativeButton("Cancel", null)
            .setCancelable(false)
            .show()
    }

    private fun updateProfileOnServer(newUsername: String) {
        val pd = android.app.ProgressDialog(context)
        pd.setMessage("Updating Profile...")
        pd.show()

        val sharedPref = requireActivity().getSharedPreferences("TicTacToePrefs", android.content.Context.MODE_PRIVATE)
        var deviceId = sharedPref.getString("device_id", "") ?: ""
        if (deviceId.isEmpty()) {
            deviceId = java.util.UUID.randomUUID().toString()
            sharedPref.edit().putString("device_id", deviceId).apply()
        }

        ApiClient.instance.auth(AuthRequest(deviceId, newUsername))
            .enqueue(object : Callback<AuthResponse> {
                override fun onResponse(call: Call<AuthResponse>, response: Response<AuthResponse>) {
                    pd.dismiss()
                    if (response.isSuccessful && response.body()?.status == "success") {
                        val user = response.body()?.user
                        if (user != null) {
                            sharedPref.edit()
                                .putString("username", user.username)
                                .putInt("user_id", user.id)
                                .putInt("level", user.level)
                                .putInt("xp", user.xp)
                                .putInt("coins", user.coins)
                                .putInt("streak_count", user.streak_count)
                                .apply()
                            loadProfile()
                            Toast.makeText(context, "Profile Updated!", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        var errorMsg = "Update failed"
                        val rawError = response.errorBody()?.string()
                        if (rawError != null && rawError.contains("message")) {
                            try {
                                errorMsg = org.json.JSONObject(rawError).optString("message", "Username is already taken")
                            } catch (e: Exception) {}
                        }
                        Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                        showEditProfileDialog() // Re-prompt
                    }
                }

                override fun onFailure(call: Call<AuthResponse>, t: Throwable) {
                    pd.dismiss()
                    Toast.makeText(context, "Network Error", Toast.LENGTH_SHORT).show()
                }
            })
    }
    
    private fun claimDailyReward() {
        val pd = android.app.ProgressDialog(context)
        pd.setMessage("Checking reward...")
        pd.show()
        
        val sharedPref = requireActivity().getSharedPreferences("TicTacToePrefs", android.content.Context.MODE_PRIVATE)
        val userId = sharedPref.getInt("user_id", -1)

        ApiClient.instance.claimDailyReward(com.example.tictactoe.network.DailyRewardRequest(userId)).enqueue(object : retrofit2.Callback<com.example.tictactoe.network.DailyRewardResponse> {
            override fun onResponse(call: retrofit2.Call<com.example.tictactoe.network.DailyRewardResponse>, response: retrofit2.Response<com.example.tictactoe.network.DailyRewardResponse>) {
                pd.dismiss()
                if (response.isSuccessful && response.body()?.status == "success") {
                    val reward = response.body()?.reward_coins ?: 0
                    val total = response.body()?.new_total_coins ?: 0
                    
                    sharedPref.edit().putInt("coins", total).apply()
                    loadProfile()
                    
                    AlertDialog.Builder(requireContext())
                        .setTitle("🎁 Reward Claimed!")
                        .setMessage("You received $reward coins!\nTotal Coins: $total")
                        .setPositiveButton("Awesome!", null)
                        .show()
                } else {
                    var errorMsg = "Could not claim"
                    val rawError = response.errorBody()?.string()
                    if (rawError != null && rawError.contains("message")) {
                        try {
                            errorMsg = org.json.JSONObject(rawError).optString("message", "Already claimed today.")
                        } catch (e: Exception) {}
                    } else {
                        errorMsg = response.body()?.message ?: "Already claimed today."
                    }
                    Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                }
            }
            override fun onFailure(call: retrofit2.Call<com.example.tictactoe.network.DailyRewardResponse>, t: Throwable) {
                pd.dismiss()
                Toast.makeText(context, "Network Error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
