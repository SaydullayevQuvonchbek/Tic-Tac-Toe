package com.example.tictactoe

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.tictactoe.databinding.FragmentDotsAndBoxesBinding
import com.example.tictactoe.network.ApiClient
import com.example.tictactoe.network.GameScoreRequest
import com.example.tictactoe.network.GameScoreResponse
import com.example.tictactoe.network.MoveRequest
import com.example.tictactoe.network.MoveResponse
import com.example.tictactoe.network.PusherManager
import com.example.tictactoe.network.RoomCreateRequest
import com.example.tictactoe.network.RoomCreateResponse
import com.example.tictactoe.network.RoomJoinRequest
import com.example.tictactoe.network.RoomJoinResponse
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class DotsAndBoxesFragment : Fragment() {

    private var _binding: FragmentDotsAndBoxesBinding? = null
    private val binding get() = _binding!!

    private var logic = DotsAndBoxesLogic(4)
    private var isAiMode = true
    private var isOnlineMode = false
    private var isHost = false
    private var roomCode = ""
    private var myPlayerNumber = 1 // 1 = P1, 2 = P2
    private var isMyTurnOnline = false

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDotsAndBoxesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        PusherManager.connect()
        initSetupUI()

        binding.btnSetupBack.setOnClickListener { findNavController().navigateUp() }
        binding.btnGameplayBack.setOnClickListener { handleBackNavigation() }
        binding.btnCancelWaiting.setOnClickListener { cancelWaiting() }
        binding.btnInviteFriend.setOnClickListener {
            ShareInviteHelper.shareRoomCode(requireContext(), "Dots & Boxes", roomCode)
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackNavigation()
            }
        })

        // Check if arriving from Rematch
        val isRematch = arguments?.getBoolean("isRematch", false) ?: false
        if (isRematch) {
            isOnlineMode = arguments?.getBoolean("isOnlineMode", false) ?: false
            roomCode = arguments?.getString("roomCode", "") ?: ""
            isHost = arguments?.getBoolean("isHost", false) ?: false
            isAiMode = arguments?.getBoolean("isAiMode", false) ?: false
            val size = arguments?.getInt("boardSize", 4) ?: 4
            myPlayerNumber = if (isHost) 1 else 2
            isMyTurnOnline = isHost

            startLocalGame(size)
            if (isOnlineMode && roomCode.isNotEmpty()) {
                subscribePusherEvents()
                val sharedPref = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
                val myUserId = sharedPref.getInt("user_id", -1)
                ApiClient.instance.makeMove(MoveRequest(roomCode, myUserId, -99, -99, -1)).enqueue(object : Callback<MoveResponse> {
                    override fun onResponse(call: Call<MoveResponse>, response: Response<MoveResponse>) {}
                    override fun onFailure(call: Call<MoveResponse>, t: Throwable) {}
                })
            }
        }
    }

    private fun handleBackNavigation() {
        if (binding.gameplayContainer.visibility == View.VISIBLE) {
            android.app.AlertDialog.Builder(requireContext())
                .setTitle("Exit Game?")
                .setMessage(if (isOnlineMode) "If you exit, the match will be forfeited." else "Do you want to exit to game setup?")
                .setPositiveButton("Yes, Exit") { _, _ ->
                    if (isOnlineMode && roomCode.isNotEmpty()) {
                        val sharedPref = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
                        val myUserId = sharedPref.getInt("user_id", -1)
                        ApiClient.instance.makeMove(MoveRequest(roomCode, myUserId, -999, -999, -1)).enqueue(object : Callback<MoveResponse> {
                            override fun onResponse(call: Call<MoveResponse>, response: Response<MoveResponse>) {}
                            override fun onFailure(call: Call<MoveResponse>, t: Throwable) {}
                        })
                        PusherManager.unsubscribeFromRoom(roomCode)
                        Toast.makeText(context, getString(R.string.forfeit_you_lost), Toast.LENGTH_SHORT).show()
                    }
                    showSetupScreen()
                }
                .setNegativeButton("Cancel", null)
                .show()
        } else if (binding.waitingContainer.visibility == View.VISIBLE) {
            cancelWaiting()
            showSetupScreen()
        } else {
            findNavController().navigateUp()
        }
    }

    private fun handleOpponentForfeited() {
        val sharedPref = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
        val curWins = sharedPref.getInt("dots_and_boxes_wins", 0) + 1
        val curCoins = sharedPref.getInt("coins", 0) + 50
        val curXp = sharedPref.getInt("xp", 0) + 100
        sharedPref.edit()
            .putInt("dots_and_boxes_wins", curWins)
            .putInt("coins", curCoins)
            .putInt("xp", curXp)
            .apply()

        val resultBundle = Bundle().apply {
            putString("resultMessage", getString(R.string.forfeit_opponent_won))
            putBoolean("isDraw", false)
            putBoolean("isOnlineMode", true)
            putString("roomCode", roomCode)
            putBoolean("isHost", isHost)
            putString("gameType", "dots_and_boxes")
            putBoolean("isAiMode", false)
            putInt("gridSize", logic.gridSize)
        }
        findNavController().navigate(R.id.action_dotsAndBoxesFragment_to_resultFragment, resultBundle)
    }

    private fun initSetupUI() {
        showSetupScreen()

        DifficultySelector.bind(
            binding.diffSelector.segDiffEasy,
            binding.diffSelector.segDiffMedium,
            binding.diffSelector.segDiffHard,
            "dots_and_boxes"
        )

        binding.rgMode.setOnCheckedChangeListener { _, checkedId ->
            binding.onlineOptionsContainer.visibility = if (checkedId == R.id.rbOnline) View.VISIBLE else View.GONE
            binding.btnStartGame.visibility = if (checkedId == R.id.rbOnline) View.GONE else View.VISIBLE
        }

        binding.btnStartGame.setOnClickListener {
            val size = when (binding.rgSize.checkedRadioButtonId) {
                R.id.rbSize3 -> 3
                R.id.rbSize5 -> 5
                else -> 4
            }
            isAiMode = binding.rbAi.isChecked
            isOnlineMode = false
            startLocalGame(size)
        }

        binding.btnCreateRoom.setOnClickListener {
            val size = when (binding.rgSize.checkedRadioButtonId) {
                R.id.rbSize3 -> 3
                R.id.rbSize5 -> 5
                else -> 4
            }
            createOnlineRoom(size)
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

    private fun startLocalGame(size: Int) {
        logic = DotsAndBoxesLogic(size)
        binding.dotsAndBoxesView.logic = logic

        val sharedPref = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
        val username = sharedPref.getString("username", "Player 1") ?: "Player 1"

        binding.tvP1Name.text = if (isOnlineMode) (if (isHost) username else "Opponent") else username
        binding.tvP2Name.text = if (isOnlineMode) (if (isHost) "Opponent" else username) else if (isAiMode) "🤖 AI Bot" else "Player 2"

        updateScoreboard()
        updateTurnIndicator()
        showGameplayScreen()

        binding.dotsAndBoxesView.onEdgeSelectedListener = { isVert, r, c ->
            handleEdgeClick(isVert, r, c)
        }
    }

    private fun handleEdgeClick(isVert: Boolean, r: Int, c: Int) {
        if (logic.isGameOver) return

        if (isOnlineMode) {
            if (!isMyTurnOnline) {
                Toast.makeText(context, "Waiting for opponent's move!", Toast.LENGTH_SHORT).show()
                return
            }
            val prevPlayer = logic.currentPlayer
            val moved = logic.makeMove(isVert, r, c)
            if (moved) {
                binding.dotsAndBoxesView.invalidate()
                updateScoreboard()

                val sharedPref = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
                val userId = sharedPref.getInt("user_id", -1)
                val nextTurnFlag = if (isVert) 1 else 0

                // If player scored, they keep turn. Otherwise, turn passes to opponent
                isMyTurnOnline = (logic.currentPlayer == myPlayerNumber)
                updateTurnIndicator()

                ApiClient.instance.makeMove(MoveRequest(roomCode, userId, r, c, nextTurnFlag)).enqueue(object : Callback<MoveResponse> {
                    override fun onResponse(call: Call<MoveResponse>, response: Response<MoveResponse>) {}
                    override fun onFailure(call: Call<MoveResponse>, t: Throwable) {}
                })

                if (logic.isGameOver) {
                    handleGameOver()
                }
            }
            return
        }

        // Local or AI Mode
        val prevPlayer = logic.currentPlayer
        val moved = logic.makeMove(isVert, r, c)
        if (moved) {
            binding.dotsAndBoxesView.invalidate()
            updateScoreboard()
            updateTurnIndicator()

            if (logic.isGameOver) {
                handleGameOver()
            } else if (isAiMode && logic.currentPlayer == 2) {
                triggerAiMove()
            }
        }
    }

    private fun triggerAiMove() {
        if (_binding == null) return
        binding.dotsAndBoxesView.isEnabled = false
        val difficulty = DifficultyStore.get(requireContext(), "dots_and_boxes")
        AiThinker.think(this, compute = {
            if (logic.isGameOver || logic.currentPlayer != 2) null else logic.getAiMove(difficulty)
        }, onResult = onResult@{ aiMove ->
            if (_binding == null) return@onResult
            if (aiMove == null || logic.isGameOver || logic.currentPlayer != 2) {
                binding.dotsAndBoxesView.isEnabled = true
                return@onResult
            }
            val (isVert, pos) = aiMove
            val (r, c) = pos
            if (!logic.makeMove(isVert, r, c)) {
                binding.dotsAndBoxesView.isEnabled = true
                return@onResult
            }
            binding.dotsAndBoxesView.invalidate()
            updateScoreboard()
            updateTurnIndicator()
            when {
                logic.isGameOver -> handleGameOver()
                logic.currentPlayer == 2 -> triggerAiMove() // completed a box → another turn
                else -> binding.dotsAndBoxesView.isEnabled = true
            }
        })
    }

    private fun updateScoreboard() {
        binding.tvP1Score.text = logic.scoreP1.toString()
        binding.tvP2Score.text = logic.scoreP2.toString()

        // Highlight active player
        binding.cardP1.alpha = if (logic.currentPlayer == 1) 1.0f else 0.45f
        binding.cardP2.alpha = if (logic.currentPlayer == 2) 1.0f else 0.45f
    }

    private fun updateTurnIndicator() {
        if (isOnlineMode) {
            binding.tvTurnIndicator.text = if (isMyTurnOnline) "Your Turn! ⚡" else "Opponent's Turn..."
        } else if (isAiMode) {
            binding.tvTurnIndicator.text = if (logic.currentPlayer == 1) "Your Turn! ⚡" else "🤖 Bot Thinking..."
        } else {
            binding.tvTurnIndicator.text = "Player ${logic.currentPlayer}'s Turn! ⚡"
        }
    }

    private fun handleGameOver() {
        val sharedPref = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
        val username = sharedPref.getString("username", "You") ?: "You"

        if (logic.winner != 0) {
            val isUserWinner = if (isOnlineMode) logic.winner == myPlayerNumber else logic.winner == 1
            binding.tvTurnIndicator.text = if (isUserWinner) "$username Won! 🎉" else "Game Over!"
        } else {
            binding.tvTurnIndicator.text = "It's a Draw! 🤝"
        }

        // Track record
        if (logic.winner == 1 || (isOnlineMode && logic.winner == myPlayerNumber)) {
            val wins = sharedPref.getInt("dots_and_boxes_wins", 0) + 1
            sharedPref.edit().putInt("dots_and_boxes_wins", wins).apply()
        }

        handler.postDelayed({
            submitScoreAndExit()
        }, 1500)
    }

    private fun submitScoreAndExit() {
        val sharedPref = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
        val userId = sharedPref.getInt("user_id", -1)
        val isUserWinner = if (isOnlineMode) logic.winner == myPlayerNumber else logic.winner == 1
        val score = if (isUserWinner) 50 else if (logic.winner == 0) 10 else 0

        if (userId != -1 && score > 0) {
            ApiClient.instance.submitGameScore(GameScoreRequest(userId, "dots_and_boxes", score))
                .enqueue(object : Callback<GameScoreResponse> {
                    override fun onResponse(call: Call<GameScoreResponse>, response: Response<GameScoreResponse>) {}
                    override fun onFailure(call: Call<GameScoreResponse>, t: Throwable) {}
                })
        }

        val bundle = Bundle().apply {
            putString("gameType", "dots_and_boxes")
            val winMsg = if (isOnlineMode) {
                if (logic.winner == myPlayerNumber) "You Won! 🎉" else if (logic.winner == 0) "It's a Draw!" else "You Lost!"
            } else if (isAiMode) {
                if (logic.winner == 1) "You Won! 🎉" else if (logic.winner == 2) "Bot Won!" else "It's a Draw!"
            } else {
                if (logic.winner == 1) "Player 1 Wins!" else if (logic.winner == 2) "Player 2 Wins!" else "It's a Draw!"
            }
            putString("resultMessage", winMsg)
            putBoolean("isDraw", logic.winner == 0)
            putBoolean("isAiMode", isAiMode)
            putBoolean("isOnlineMode", isOnlineMode)
            putString("roomCode", roomCode)
            putBoolean("isHost", isHost)
            putInt("boardSize", logic.gridSize)
        }
        findNavController().navigate(R.id.action_dotsAndBoxesFragment_to_resultFragment, bundle)
    }

    // Online Multiplayer Sockets & Rooms
    private fun createOnlineRoom(size: Int) {
        val sharedPref = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
        val userId = sharedPref.getInt("user_id", -1)

        ApiClient.instance.createRoom(RoomCreateRequest(userId, size, false, "dots_and_boxes"))
            .enqueue(object : Callback<RoomCreateResponse> {
                override fun onResponse(call: Call<RoomCreateResponse>, response: Response<RoomCreateResponse>) {
                    if (response.isSuccessful && response.body()?.status == "success") {
                        roomCode = response.body()?.room_code ?: ""
                        isOnlineMode = true
                        isHost = true
                        myPlayerNumber = 1
                        isMyTurnOnline = true

                        subscribePusherEvents()
                        showWaitingScreen(roomCode)
                    } else {
                        Toast.makeText(context, "Failed to create room", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<RoomCreateResponse>, t: Throwable) {
                    Toast.makeText(context, "Network Error: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun joinOnlineRoom(userId: Int, code: String) {
        ApiClient.instance.joinRoom(RoomJoinRequest(userId, code))
            .enqueue(object : Callback<RoomJoinResponse> {
                override fun onResponse(call: Call<RoomJoinResponse>, response: Response<RoomJoinResponse>) {
                    if (response.isSuccessful && response.body()?.status == "success") {
                        roomCode = code
                        isOnlineMode = true
                        isHost = false
                        myPlayerNumber = 2
                        isMyTurnOnline = false
                        val size = response.body()?.board_size ?: 4

                        subscribePusherEvents()
                        startLocalGame(size)
                        Toast.makeText(context, "Connected! Match started!", Toast.LENGTH_SHORT).show()
                    } else {
                        val msg = response.body()?.message ?: "Room not found or full"
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }
                }

                override fun onFailure(call: Call<RoomJoinResponse>, t: Throwable) {
                    Toast.makeText(context, "Network Error: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun subscribePusherEvents() {
        PusherManager.subscribeToRoom(
            roomCode = roomCode,
            onGameStarted = {
                activity?.runOnUiThread {
                    startLocalGame(logic.gridSize)
                    Toast.makeText(context, "Match started! 🔥", Toast.LENGTH_SHORT).show()
                }
            },
            onMoveMade = { eventData ->
                activity?.runOnUiThread {
                    try {
                        val json = JSONObject(eventData)
                        val senderId = json.optInt("player_id", -1)
                        val sharedPref = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
                        val myUserId = sharedPref.getInt("user_id", -1)

                        // Ignore our own move echoed back
                        if (senderId != -1 && myUserId != -1 && senderId == myUserId) {
                            return@runOnUiThread
                        }

                        val row = json.optInt("row", -1)
                        val col = json.optInt("col", -1)
                        val nextTurn = json.optInt("next_turn", 0)

                        if (row == -999 && col == -999) {
                            handleOpponentForfeited()
                            return@runOnUiThread
                        }

                        if (row == -99 && col == -99) {
                            // Rematch trigger!
                            logic = DotsAndBoxesLogic(logic.gridSize)
                            binding.dotsAndBoxesView.logic = logic
                            isMyTurnOnline = isHost
                            updateScoreboard()
                            updateTurnIndicator()
                            Toast.makeText(context, "Rematch started! 🔥", Toast.LENGTH_SHORT).show()
                            return@runOnUiThread
                        }

                        val isVert = (nextTurn == 1)
                        val moved = logic.makeMove(isVert, row, col)
                        if (moved) {
                            binding.dotsAndBoxesView.invalidate()
                            updateScoreboard()
                            isMyTurnOnline = (logic.currentPlayer == myPlayerNumber)
                            updateTurnIndicator()

                            if (logic.isGameOver) {
                                handleGameOver()
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            },
            onOpponentLeft = {
                activity?.runOnUiThread {
                    handleOpponentForfeited()
                }
            }
        )
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
        binding.gameplayContainer.visibility = View.GONE
    }

    private fun showWaitingScreen(code: String) {
        binding.tvWaitingRoomCode.text = "ROOM: $code"
        binding.setupContainer.visibility = View.GONE
        binding.waitingContainer.visibility = View.VISIBLE
        binding.gameplayContainer.visibility = View.GONE
    }

    private fun showGameplayScreen() {
        binding.setupContainer.visibility = View.GONE
        binding.waitingContainer.visibility = View.GONE
        binding.gameplayContainer.visibility = View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacksAndMessages(null)
        _binding = null
    }
}
