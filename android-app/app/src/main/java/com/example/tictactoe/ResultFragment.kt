package com.example.tictactoe

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.tictactoe.databinding.FragmentResultBinding
import com.example.tictactoe.network.ApiClient
import com.example.tictactoe.network.MoveRequest
import com.example.tictactoe.network.MoveResponse
import com.example.tictactoe.network.PusherManager
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.emitter.Emitter
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.concurrent.TimeUnit

class ResultFragment : Fragment() {

    private var _binding: FragmentResultBinding? = null
    private val binding get() = _binding!!

    private var isOnlineMode = false
    private var roomCode = ""
    private var gameType = "tic_tac_toe"
    private var myRematchRequested = false
    private var opponentRequestedRematch = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentResultBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val resultMessage = arguments?.getString("resultMessage") ?: "Unknown"
        val isDraw = arguments?.getBoolean("isDraw") ?: false
        isOnlineMode = arguments?.getBoolean("isOnlineMode") ?: false
        roomCode = arguments?.getString("roomCode") ?: ""
        gameType = arguments?.getString("gameType") ?: "tic_tac_toe"

        binding.tvResultMessage.text = resultMessage

        // Safe Back Navigation
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                exitToMenu()
            }
        })

        if (isDraw) {
            binding.tvHeader.text = "Draw"
            binding.ivResult.setImageResource(R.drawable.board)
            binding.tvSubMessage.text = "Congrats to both of you for equally excelling in the art of not winning."
            binding.btnAction.text = if (isOnlineMode) "REMATCH 🔄" else "REPLAY"
        } else {
            binding.tvHeader.text = "Winner"
            binding.ivResult.setImageResource(R.drawable.trophy)
            binding.tvSubMessage.text = "Congrats on being the undisputed champion!"
            binding.btnAction.text = if (isOnlineMode) "REMATCH 🔄" else "RESTART"
            
            // Trigger Confetti
            val party = Party(
                speed = 0f,
                maxSpeed = 30f,
                damping = 0.9f,
                spread = 360,
                colors = listOf(0xfce18a, 0xff726d, 0xf4306d, 0xb48def),
                emitter = Emitter(duration = 100, TimeUnit.MILLISECONDS).max(100),
                position = Position.Relative(0.5, 0.3)
            )
            binding.konfettiView.start(party)
        }

        if (isOnlineMode && roomCode.isNotEmpty()) {
            binding.btnSecondary.visibility = View.VISIBLE
            binding.btnSecondary.setOnClickListener {
                exitToMenu()
            }

            listenToOnlineRematch()
        } else {
            binding.btnSecondary.visibility = View.GONE
        }

        binding.btnAction.setOnClickListener {
            if (isOnlineMode && roomCode.isNotEmpty()) {
                handleOnlineRematchClick()
            } else {
                launchRematchGame()
            }
        }
    }

    private fun listenToOnlineRematch() {
        PusherManager.subscribeToRoom(
            roomCode = roomCode,
            onGameStarted = {
                activity?.runOnUiThread {
                    launchRematchGame()
                }
            },
            onMoveMade = { eventData ->
                activity?.runOnUiThread {
                    try {
                        val json = JSONObject(eventData)
                        val senderId = json.optInt("player_id", -1)
                        val sharedPref = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
                        val myUserId = sharedPref.getInt("user_id", -1)

                        if (senderId != -1 && myUserId != -1 && senderId == myUserId) {
                            return@runOnUiThread
                        }

                        val row = json.optInt("row", -1)
                        val col = json.optInt("col", -1)

                        if (row == -98 && col == -98) {
                            // Opponent requested rematch!
                            opponentRequestedRematch = true
                            if (myRematchRequested) {
                                // Both accepted! Send -99 and start
                                sendRematchSignal(-99, -99)
                                launchRematchGame()
                            } else {
                                binding.tvRematchStatus.visibility = View.VISIBLE
                                binding.tvRematchStatus.text = "⚡ Opponent requested a rematch!"
                                binding.btnAction.text = "ACCEPT REMATCH 🔄"
                                binding.btnAction.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#059669"))
                            }
                        } else if (row == -99 && col == -99) {
                            // Opponent accepted rematch!
                            Toast.makeText(context, "Rematch Accepted! Starting match... 🔥", Toast.LENGTH_SHORT).show()
                            launchRematchGame()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            },
            onOpponentLeft = {
                activity?.runOnUiThread {
                    Toast.makeText(context, "Opponent left the room", Toast.LENGTH_SHORT).show()
                    binding.tvRematchStatus.visibility = View.VISIBLE
                    binding.tvRematchStatus.text = "🚪 Opponent left the room"
                    binding.btnAction.isEnabled = false
                    binding.btnAction.alpha = 0.5f
                }
            }
        )
    }

    private fun handleOnlineRematchClick() {
        if (opponentRequestedRematch) {
            // Accept opponent's request!
            sendRematchSignal(-99, -99)
            launchRematchGame()
        } else {
            // Send our request!
            myRematchRequested = true
            sendRematchSignal(-98, -98)
            binding.btnAction.text = "Waiting for opponent... ⏳"
            binding.btnAction.isEnabled = false
            binding.tvRematchStatus.visibility = View.VISIBLE
            binding.tvRematchStatus.text = "⏳ Waiting for opponent to accept..."
        }
    }

    private fun sendRematchSignal(row: Int, col: Int) {
        val sharedPref = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
        val myUserId = sharedPref.getInt("user_id", -1)

        ApiClient.instance.makeMove(MoveRequest(roomCode, myUserId, row, col, -1))
            .enqueue(object : Callback<MoveResponse> {
                override fun onResponse(call: Call<MoveResponse>, response: Response<MoveResponse>) {}
                override fun onFailure(call: Call<MoveResponse>, t: Throwable) {}
            })
    }

    private fun launchRematchGame() {
        val rematchBundle = Bundle(arguments).apply {
            putBoolean("isRematch", true)
        }

        when (gameType) {
            "connect4" -> findNavController().navigate(R.id.action_resultFragment_to_connect4Fragment, rematchBundle)
            "water_sort" -> findNavController().navigate(R.id.action_resultFragment_to_waterSortFragment, rematchBundle)
            "dots_and_boxes" -> findNavController().navigate(R.id.action_resultFragment_to_dotsAndBoxesFragment, rematchBundle)
            "gomoku" -> findNavController().navigate(R.id.action_resultFragment_to_gomokuFragment, rematchBundle)
            "checkers" -> findNavController().navigate(R.id.action_resultFragment_to_checkersFragment, rematchBundle)
            else -> findNavController().navigate(R.id.action_resultFragment_to_gameFragment, rematchBundle)
        }
    }

    private fun exitToMenu() {
        if (roomCode.isNotEmpty()) {
            PusherManager.unsubscribeFromRoom(roomCode)
        }
        findNavController().navigate(R.id.action_resultFragment_to_dashboardFragment)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
