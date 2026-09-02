package com.example.tictactoe

import android.content.Context
import android.content.Intent
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

        val lower = resultMessage.lowercase()
        val userWon = !isDraw && (lower.contains("won") || lower.contains("win"))
        val userLost = !isDraw && (lower.contains("lost") || lower.contains("lose"))
        binding.tvResultMessage.text = when {
            isDraw -> "DURRANG!"
            userWon -> "G'ALABA!"
            userLost -> "MAG'LUBIYAT"
            else -> resultMessage
        }

        populateStats(isDraw, userWon)

        binding.btnShare.setOnClickListener { shareResult() }

        // Safe Back Navigation
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                exitToMenu()
            }
        })

        if (isDraw) {
            binding.tvHeader.text = "DURRANG"
            binding.ivResult.setImageResource(R.drawable.board)
            binding.tvSubMessage.text = "Kuchlar teng keldi — revansh vaqti!"
            binding.btnAction.text = if (isOnlineMode) "↻ REVANSH" else "↻ QAYTA O'YIN"
        } else {
            binding.tvHeader.text = if (userLost) "YAKUN" else "G'OLIB"
            binding.ivResult.setImageResource(R.drawable.trophy)
            binding.tvSubMessage.text = if (userLost) "Keyingi safar albatta! 💪" else "Zo'r o'yin bo'ldi — chempion!"
            binding.btnAction.text = if (isOnlineMode) "↻ REVANSH" else "↻ QAYTA O'YIN"

            if (!userLost) {
                // Trigger Confetti & Victory Audio/Haptics
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
                ConfettiView.show(binding.root as ViewGroup)
                HapticHelper.performVictory(requireContext())
                SoundHelper.playVictorySound(requireContext())
            }
        }

        binding.btnSecondary.visibility = View.VISIBLE
        binding.btnSecondary.setOnClickListener { exitToMenu() }

        if (isOnlineMode && roomCode.isNotEmpty()) {
            listenToOnlineRematch()
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
                        var json = JSONObject(eventData)
                        if (json.has("data")) {
                            val d = json.get("data")
                            if (d is String) json = JSONObject(d)
                            else if (d is JSONObject) json = d
                        }

                        val senderId = json.optInt("player_id", json.optString("player_id", "-1").toIntOrNull() ?: -1)
                        val sharedPref = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
                        val myUserId = sharedPref.getInt("user_id", -1)

                        if (senderId != -1 && myUserId != -1 && senderId == myUserId) {
                            return@runOnUiThread
                        }

                        val row = json.optInt("row", json.optString("row", "-1").toIntOrNull() ?: -1)
                        val col = json.optInt("col", json.optString("col", "-1").toIntOrNull() ?: -1)

                        if (row == -98 && col == -98) {
                            // Opponent requested rematch!
                            opponentRequestedRematch = true
                            if (myRematchRequested) {
                                // Both accepted! Send -99 and start
                                sendRematchSignal(-99, -99)
                                launchRematchGame()
                            } else {
                                binding.tvRematchStatus.visibility = View.VISIBLE
                                binding.tvRematchStatus.text = "⚡ Raqibingiz revansh so'radi!"
                                binding.btnAction.text = "REVANSGNI QABUL QILISH 🔄"
                                binding.btnAction.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#059669"))
                            }
                        } else if (row == -99 && col == -99) {
                            // Opponent accepted rematch!
                            Toast.makeText(context, "Revansh qabul qilindi! O'yin boshlanmoqda... 🔥", Toast.LENGTH_SHORT).show()
                            launchRematchGame()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            },
            onOpponentLeft = {
                activity?.runOnUiThread {
                    Toast.makeText(context, "Raqibingiz xonadan chiqib ketdi", Toast.LENGTH_SHORT).show()
                    binding.tvRematchStatus.visibility = View.VISIBLE
                    binding.tvRematchStatus.text = "🚪 Raqib xonadan chiqdi"
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

    private fun populateStats(isDraw: Boolean, userWon: Boolean) {
        val prefs = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
        val xp = prefs.getInt("xp", 0)
        val level = prefs.getInt("level", 1)
        val streak = prefs.getInt("streak_count", 0)

        val xpEarned = arguments?.getInt("xpEarned", Int.MIN_VALUE) ?: Int.MIN_VALUE
        val coinsEarned = arguments?.getInt("coinsEarned", Int.MIN_VALUE) ?: Int.MIN_VALUE

        val xpVal = if (xpEarned != Int.MIN_VALUE) xpEarned else when {
            isDraw -> 10
            userWon -> if (isOnlineMode) 100 else 50
            else -> -5
        }
        val coinVal = if (coinsEarned != Int.MIN_VALUE) coinsEarned else when {
            isDraw -> 5
            userWon -> if (isOnlineMode) 50 else 20
            else -> 0
        }

        binding.tvStatXp.text = if (xpVal >= 0) "+$xpVal" else "$xpVal"
        binding.tvStatCoins.text = "+$coinVal"
        binding.tvStatStreak.text = "🔥 $streak"

        binding.tvLevelRange.text = "LEVEL $level → ${level + 1}"
        binding.tvLevelXp.text = "${LevelHelper.xpIntoLevel(xp, level)}/${LevelHelper.XP_PER_LEVEL} XP"
        binding.pbLevel.progress = LevelHelper.levelProgressPercent(xp, level)
    }

    private fun shareResult() {
        val prefs = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
        val name = prefs.getString("username", "Player") ?: "Player"
        val level = prefs.getInt("level", 1)
        val xp = prefs.getInt("xp", 0)
        val msg = "🏆 $name — Mini Game Arena\n${binding.tvResultMessage.text} · LEVEL $level · $xp XP\n\nSen ham sinab ko'r! 🎮"
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, msg)
        }
        runCatching { startActivity(Intent.createChooser(send, "Ulashish")) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
