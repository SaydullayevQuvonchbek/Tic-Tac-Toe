package com.example.tictactoe

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.tictactoe.databinding.FragmentWelcomeBinding
import com.example.tictactoe.network.ApiClient
import com.example.tictactoe.network.PusherManager
import com.example.tictactoe.network.RoomCreateRequest
import com.example.tictactoe.network.RoomCreateResponse
import com.example.tictactoe.network.RoomJoinRequest
import com.example.tictactoe.network.RoomJoinResponse
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class WelcomeFragment : Fragment() {

    private var _binding: FragmentWelcomeBinding? = null
    private val binding get() = _binding!!

    private var roomCode = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWelcomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        PusherManager.connect()
        initUI()
    }

    private fun initUI() {
        showSetupScreen()

        DifficultySelector.bind(
            binding.diffSelector.segDiffEasy,
            binding.diffSelector.segDiffMedium,
            binding.diffSelector.segDiffHard,
            "tictactoe"
        )

        binding.btnSetupBack.setOnClickListener { findNavController().navigateUp() }
        binding.btnCancelWaiting.setOnClickListener { cancelWaiting() }
        binding.btnInviteFriend.setOnClickListener {
            ShareInviteHelper.shareRoomCode(requireContext(), "Tic Tac Toe", roomCode)
        }

        binding.rgMode.setOnCheckedChangeListener { _, checkedId ->
            binding.onlineOptionsContainer.visibility = if (checkedId == R.id.rbOnline) View.VISIBLE else View.GONE
            binding.btnStartGame.visibility = if (checkedId == R.id.rbOnline) View.GONE else View.VISIBLE
        }

        binding.btnStartGame.setOnClickListener {
            val size = when (binding.rgSize.checkedRadioButtonId) {
                R.id.rb4x4 -> 4
                R.id.rb5x5 -> 5
                else -> 3
            }
            val isInfinityMode = binding.switchInfinityMode.isChecked
            val isArcadeMode = binding.switchArcadeMode.isChecked

            if (binding.rbQuickMatch.isChecked) {
                val sharedPref = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
                val userId = sharedPref.getInt("user_id", -1)
                val username = sharedPref.getString("username", "Player 1") ?: "Player 1"

                MatchmakingHelper.startQuickMatch(requireContext(), "tictactoe", size) { code, host, oppName, isBot ->
                    val bundle = Bundle().apply {
                        putBoolean("isOnlineMode", !isBot)
                        putBoolean("isAiMode", isBot)
                        putInt("playerId", userId)
                        putString("roomCode", code)
                        putBoolean("isHost", host)
                        putString("username", username)
                        putInt("boardSize", size)
                        putBoolean("isInfinityMode", isInfinityMode)
                        putBoolean("isArcadeMode", isArcadeMode)
                        putString("startingPlayer", "X")
                    }
                    findNavController().navigate(R.id.action_welcomeFragment_to_gameFragment, bundle)
                }
                return@setOnClickListener
            }

            val isAiMode = binding.rbAi.isChecked
            launchLocalGame(size, isAiMode, isInfinityMode, isArcadeMode)
        }

        binding.btnCreateRoom.setOnClickListener {
            val size = when (binding.rgSize.checkedRadioButtonId) {
                R.id.rb4x4 -> 4
                R.id.rb5x5 -> 5
                else -> 3
            }
            val isInfinity = binding.switchInfinityMode.isChecked
            createOnlineRoom(size, isInfinity)
        }

        binding.btnJoinRoom.setOnClickListener {
            val code = binding.etRoomCode.text.toString().trim().uppercase()
            if (code.length == 6 || code.length == 4) {
                val sharedPref = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
                val userId = sharedPref.getInt("user_id", -1)
                joinOnlineRoom(userId, code)
            } else {
                Toast.makeText(context, "Please enter a valid room code", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun launchLocalGame(size: Int, isAi: Boolean, isInfinity: Boolean, isArcade: Boolean) {
        val sharedPref = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
        val username = sharedPref.getString("username", "Player 1") ?: "Player 1"

        val bundle = Bundle().apply {
            putString("startingPlayer", "X")
            putBoolean("isAiMode", isAi)
            putBoolean("isInfinityMode", isInfinity)
            putBoolean("isArcadeMode", isArcade)
            putInt("boardSize", size)
            putString("username", username)
            putBoolean("isOnlineMode", false)
        }
        findNavController().navigate(R.id.action_welcomeFragment_to_gameFragment, bundle)
    }

    private fun createOnlineRoom(size: Int, isInfinity: Boolean) {
        val sharedPref = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
        val userId = sharedPref.getInt("user_id", -1)
        val username = sharedPref.getString("username", "Player 1") ?: "Player 1"

        ApiClient.instance.createRoom(RoomCreateRequest(userId, size, isInfinity, "tictactoe"))
            .enqueue(object : Callback<RoomCreateResponse> {
                override fun onResponse(call: Call<RoomCreateResponse>, response: Response<RoomCreateResponse>) {
                    if (!isAdded || _binding == null) return
                    if (response.isSuccessful && response.body()?.status == "success") {
                        roomCode = response.body()?.room_code ?: ""
                        showWaitingScreen(roomCode)

                        PusherManager.subscribeToRoom(
                            roomCode = roomCode,
                            onGameStarted = {
                                activity?.runOnUiThread {
                                    if (!isAdded || _binding == null) return@runOnUiThread
                                    val bundle = Bundle().apply {
                                        putBoolean("isOnlineMode", true)
                                        putInt("playerId", userId)
                                        putString("roomCode", roomCode)
                                        putBoolean("isHost", true)
                                        putString("username", username)
                                        putInt("boardSize", size)
                                        putBoolean("isInfinityMode", isInfinity)
                                    }
                                    findNavController().navigate(R.id.action_welcomeFragment_to_gameFragment, bundle)
                                }
                            }
                        )
                    } else {
                        Toast.makeText(context, "Failed to create room", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<RoomCreateResponse>, t: Throwable) {
                    if (!isAdded || _binding == null) return
                    Toast.makeText(context, "Network Error: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun joinOnlineRoom(userId: Int, code: String) {
        val sharedPref = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
        val username = sharedPref.getString("username", "Player 2") ?: "Player 2"

        ApiClient.instance.joinRoom(RoomJoinRequest(userId, code))
            .enqueue(object : Callback<RoomJoinResponse> {
                override fun onResponse(call: Call<RoomJoinResponse>, response: Response<RoomJoinResponse>) {
                    if (!isAdded || _binding == null) return
                    if (response.isSuccessful && response.body()?.status == "success") {
                        val size = response.body()?.board_size ?: 3
                        val isInfinity = response.body()?.infinity_mode ?: false

                        val bundle = Bundle().apply {
                            putBoolean("isOnlineMode", true)
                            putInt("playerId", userId)
                            putString("roomCode", code)
                            putBoolean("isHost", false)
                            putString("username", username)
                            putInt("boardSize", size)
                            putBoolean("isInfinityMode", isInfinity)
                        }
                        findNavController().navigate(R.id.action_welcomeFragment_to_gameFragment, bundle)
                    } else {
                        val msg = response.body()?.message ?: "Room not found or full"
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }
                }

                override fun onFailure(call: Call<RoomJoinResponse>, t: Throwable) {
                    if (!isAdded || _binding == null) return
                    Toast.makeText(context, "Network Error: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun cancelWaiting() {
        if (roomCode.isNotEmpty()) {
            PusherManager.unsubscribeFromRoom(roomCode)
        }
        showSetupScreen()
    }

    private fun showSetupScreen() {
        binding.setupContainer.visibility = View.VISIBLE
        binding.waitingContainer.visibility = View.GONE
    }

    private fun showWaitingScreen(code: String) {
        binding.tvWaitingRoomCode.text = "ROOM: $code"
        binding.setupContainer.visibility = View.GONE
        binding.waitingContainer.visibility = View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
