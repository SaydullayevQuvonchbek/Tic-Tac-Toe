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
            if (ensureProfile()) {
                findNavController().navigate(R.id.action_dashboardFragment_to_memoryGameFragment)
            }
        }

        binding.btnGlobalLeaderboard.setOnClickListener {
            // Reusing logic from WelcomeFragment or we can extract it.
            // For now, let's keep it simple and just show a Toast that we are fetching.
            fetchAndShowLeaderboard()
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

        if (username.isNotEmpty()) {
            binding.tvUsername.text = username
            binding.tvLevelInfo.text = "Level $level | $xp XP"
        } else {
            binding.tvUsername.text = "Guest Player"
            binding.tvLevelInfo.text = "Click edit to set username"
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
    
    private fun fetchAndShowLeaderboard() {
        val pd = android.app.ProgressDialog(context)
        pd.setMessage("Loading Leaderboard...")
        pd.show()

        ApiClient.instance.getLeaderboard().enqueue(object : retrofit2.Callback<com.example.tictactoe.network.LeaderboardResponse> {
            override fun onResponse(call: retrofit2.Call<com.example.tictactoe.network.LeaderboardResponse>, response: retrofit2.Response<com.example.tictactoe.network.LeaderboardResponse>) {
                pd.dismiss()
                if (response.isSuccessful && response.body()?.status == "success") {
                    val list = response.body()?.leaderboard
                    if (list.isNullOrEmpty()) {
                        Toast.makeText(context, "No players yet!", Toast.LENGTH_SHORT).show()
                    } else {
                        val names = list.map { "${it.rank}. ${it.username} (Level ${it.level} - ${it.xp} XP)" }.toTypedArray()
                        AlertDialog.Builder(requireContext())
                            .setTitle("Global Leaderboard")
                            .setItems(names, null)
                            .setPositiveButton("Close", null)
                            .show()
                    }
                } else {
                    Toast.makeText(context, "Failed to load", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onFailure(call: retrofit2.Call<com.example.tictactoe.network.LeaderboardResponse>, t: Throwable) {
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
