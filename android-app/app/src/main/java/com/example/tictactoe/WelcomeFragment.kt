package com.example.tictactoe

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.tictactoe.databinding.FragmentWelcomeBinding

class WelcomeFragment : Fragment() {

    private var _binding: FragmentWelcomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWelcomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val sharedPref = requireActivity().getSharedPreferences("TicTacToePrefs", android.content.Context.MODE_PRIVATE)
        val savedUsername = sharedPref.getString("username", "")
        binding.etUsername.setText(savedUsername)

        // Generate a random device ID once and save it
        if (sharedPref.getString("device_id", "") == "") {
            sharedPref.edit().putString("device_id", java.util.UUID.randomUUID().toString()).apply()
        }

        binding.btnPlayerX.setOnClickListener {
            startGame("X")
        }

        binding.btnPlayerO.setOnClickListener {
            startGame("O")
        }

        binding.btnPlayOnline.setOnClickListener {
            val username = binding.etUsername.text.toString()
            if (username.isEmpty()) {
                android.widget.Toast.makeText(context, "Enter Username first!", android.widget.Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            sharedPref.edit().putString("username", username).apply()
            val deviceId = sharedPref.getString("device_id", "") ?: ""
            authenticateAndPlayOnline(deviceId, username)
        }
    }

    private fun authenticateAndPlayOnline(deviceId: String, username: String) {
        val pd = android.app.ProgressDialog(context)
        pd.setMessage("Connecting to server...")
        pd.setCancelable(false)
        pd.show()

        com.example.tictactoe.network.ApiClient.instance.auth(
            com.example.tictactoe.network.AuthRequest(deviceId, username)
        ).enqueue(object : retrofit2.Callback<com.example.tictactoe.network.AuthResponse> {
            override fun onResponse(
                call: retrofit2.Call<com.example.tictactoe.network.AuthResponse>,
                response: retrofit2.Response<com.example.tictactoe.network.AuthResponse>
            ) {
                pd.dismiss()
                if (response.isSuccessful && response.body()?.status == "success") {
                    val user = response.body()?.user
                    if (user != null) {
                        showOnlineMenu(user)
                    } else {
                        android.widget.Toast.makeText(context, "Failed to get user data", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } else {
                    android.widget.Toast.makeText(context, "Auth failed!", android.widget.Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: retrofit2.Call<com.example.tictactoe.network.AuthResponse>, t: Throwable) {
                pd.dismiss()
                android.widget.Toast.makeText(context, "Network Error: ${t.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun showOnlineMenu(user: com.example.tictactoe.network.User) {
        val options = arrayOf("Create Room", "Join Room")
        android.app.AlertDialog.Builder(context)
            .setTitle("Online Menu (Level ${user.level})")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> createRoom(user)
                    1 -> joinRoomPrompt(user)
                }
            }
            .show()
    }

    private fun createRoom(user: com.example.tictactoe.network.User) {
        val size = when (binding.rgSize.checkedRadioButtonId) {
            R.id.rb4x4 -> 4
            R.id.rb5x5 -> 5
            else -> 3
        }
        val isInfinity = binding.switchInfinityMode.isChecked

        val pd = android.app.ProgressDialog(context)
        pd.setMessage("Creating Room...")
        pd.show()

        com.example.tictactoe.network.ApiClient.instance.createRoom(
            com.example.tictactoe.network.RoomCreateRequest(user.id, size, isInfinity)
        ).enqueue(object : retrofit2.Callback<com.example.tictactoe.network.RoomCreateResponse> {
            override fun onResponse(
                call: retrofit2.Call<com.example.tictactoe.network.RoomCreateResponse>,
                response: retrofit2.Response<com.example.tictactoe.network.RoomCreateResponse>
            ) {
                pd.dismiss()
                if (response.isSuccessful && response.body()?.status == "success") {
                    val roomCode = response.body()?.room_code ?: ""
                    launchOnlineGame(user, roomCode, isHost = true, size, isInfinity)
                } else {
                    android.widget.Toast.makeText(context, "Failed to create room", android.widget.Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: retrofit2.Call<com.example.tictactoe.network.RoomCreateResponse>, t: Throwable) {
                pd.dismiss()
                android.widget.Toast.makeText(context, "Error: ${t.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun joinRoomPrompt(user: com.example.tictactoe.network.User) {
        val input = android.widget.EditText(context)
        android.app.AlertDialog.Builder(context)
            .setTitle("Join Room")
            .setMessage("Enter Room Code:")
            .setView(input)
            .setPositiveButton("Join") { _, _ ->
                val code = input.text.toString()
                if (code.isNotEmpty()) {
                    joinRoom(user, code)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun joinRoom(user: com.example.tictactoe.network.User, roomCode: String) {
        val pd = android.app.ProgressDialog(context)
        pd.setMessage("Joining Room...")
        pd.show()

        com.example.tictactoe.network.ApiClient.instance.joinRoom(
            com.example.tictactoe.network.RoomJoinRequest(user.id, roomCode)
        ).enqueue(object : retrofit2.Callback<com.example.tictactoe.network.RoomJoinResponse> {
            override fun onResponse(
                call: retrofit2.Call<com.example.tictactoe.network.RoomJoinResponse>,
                response: retrofit2.Response<com.example.tictactoe.network.RoomJoinResponse>
            ) {
                pd.dismiss()
                if (response.isSuccessful && response.body()?.status == "success") {
                    // Host sets the rules, we just join. Defaulting to 3x3 normal if backend doesn't send rules.
                    // Ideal: backend sends board_size and infinity_mode in JoinResponse.
                    launchOnlineGame(user, roomCode, isHost = false, 3, false)
                } else {
                    android.widget.Toast.makeText(context, "Failed to join room: ${response.body()?.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: retrofit2.Call<com.example.tictactoe.network.RoomJoinResponse>, t: Throwable) {
                pd.dismiss()
                android.widget.Toast.makeText(context, "Error: ${t.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun launchOnlineGame(user: com.example.tictactoe.network.User, roomCode: String, isHost: Boolean, size: Int, isInfinity: Boolean) {
        val bundle = Bundle().apply {
            putBoolean("isOnlineMode", true)
            putInt("playerId", user.id)
            putString("roomCode", roomCode)
            putBoolean("isHost", isHost)
            putString("username", user.username)
            putInt("boardSize", size)
            putBoolean("isInfinityMode", isInfinity)
        }
        findNavController().navigate(R.id.action_welcomeFragment_to_gameFragment, bundle)
    }

    private fun startGame(startingPlayer: String) {
        val username = binding.etUsername.text.toString()
        if (username.isNotEmpty()) {
            val sharedPref = requireActivity().getSharedPreferences("TicTacToePrefs", android.content.Context.MODE_PRIVATE)
            sharedPref.edit().putString("username", username).apply()
        }

        val size = when (binding.rgSize.checkedRadioButtonId) {
            R.id.rb4x4 -> 4
            R.id.rb5x5 -> 5
            else -> 3
        }

        val bundle = Bundle().apply {
            putString("startingPlayer", startingPlayer)
            putBoolean("isInfinityMode", binding.switchInfinityMode.isChecked)
            putBoolean("isAiMode", binding.switchAiMode.isChecked)
            putBoolean("isArcadeMode", binding.switchArcadeMode.isChecked)
            putInt("boardSize", size)
            putString("username", username)
        }
        findNavController().navigate(R.id.action_welcomeFragment_to_gameFragment, bundle)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
