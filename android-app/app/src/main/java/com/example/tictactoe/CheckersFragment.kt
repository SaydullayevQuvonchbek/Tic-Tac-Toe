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
import com.example.tictactoe.databinding.FragmentCheckersBinding
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

class CheckersFragment : Fragment() {

    private var _binding: FragmentCheckersBinding? = null
    private val binding get() = _binding!!

    private var logic = CheckersLogic()
    private var isAiMode = true
    private var isOnlineMode = false
    private var isHost = false
    private var roomCode = ""
    private var myPlayerNumber = 1 // 1 = Red (Host/P1), 2 = Black (Joiner/P2)
    private var isMyTurnOnline = false

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCheckersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        PusherManager.connect()
        initSetupUI()

        binding.btnSetupBack.setOnClickListener { findNavController().navigateUp() }
        binding.btnGameplayBack.setOnClickListener { handleBackNavigation() }
        binding.btnCancelWaiting.setOnClickListener { cancelWaiting() }

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
            myPlayerNumber = if (isHost) 1 else 2
            isMyTurnOnline = isHost

            startLocalGame()
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
        if (binding.gameplayContainer.visibility == View.VISIBLE || binding.waitingContainer.visibility == View.VISIBLE) {
            cancelWaiting()
            showSetupScreen()
        } else {
            findNavController().navigateUp()
        }
    }

    private fun initSetupUI() {
        showSetupScreen()

        binding.rgMode.setOnCheckedChangeListener { _, checkedId ->
            binding.onlineOptionsContainer.visibility = if (checkedId == R.id.rbOnline) View.VISIBLE else View.GONE
            binding.btnStartGame.visibility = if (checkedId == R.id.rbOnline) View.GONE else View.VISIBLE
        }

        binding.btnStartGame.setOnClickListener {
            isAiMode = binding.rbAi.isChecked
            isOnlineMode = false
            startLocalGame()
        }

        binding.btnCreateRoom.setOnClickListener {
            createOnlineRoom()
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

    private fun startLocalGame() {
        logic.resetBoard()
        binding.checkersBoardView.logic = logic

        val sharedPref = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
        val username = sharedPref.getString("username", "Player 1") ?: "Player 1"

        binding.tvP1Name.text = if (isOnlineMode) (if (isHost) username else "Opponent") else username
        binding.tvP2Name.text = if (isOnlineMode) (if (isHost) "Opponent" else username) else if (isAiMode) "🤖 AI Bot" else "Player 2"

        updateScoreboard()
        updateTurnIndicator()
        showGameplayScreen()

        binding.checkersBoardView.onMoveExecutedListener = { fromR, fromC, toR, toC ->
            handleMove(fromR, fromC, toR, toC)
        }
    }

    private fun handleMove(fromR: Int, fromC: Int, toR: Int, toC: Int) {
        if (logic.isGameOver) return

        if (isOnlineMode) {
            if (!isMyTurnOnline) {
                Toast.makeText(context, "Waiting for opponent's move!", Toast.LENGTH_SHORT).show()
                return
            }

            val moved = logic.makeMove(fromR, fromC, toR, toC)
            if (moved) {
                binding.checkersBoardView.invalidate()
                updateScoreboard()

                // Check if continuing multi-jump
                val keepsTurn = (logic.activeJumpPiece != null && logic.currentPlayer == myPlayerNumber)
                isMyTurnOnline = keepsTurn
                updateTurnIndicator()

                val sharedPref = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
                val userId = sharedPref.getInt("user_id", -1)
                val encodedFrom = fromR * 10 + fromC
                val encodedTo = toR * 10 + toC

                ApiClient.instance.makeMove(MoveRequest(roomCode, userId, encodedFrom, encodedTo, -1)).enqueue(object : Callback<MoveResponse> {
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
        val moved = logic.makeMove(fromR, fromC, toR, toC)
        if (moved) {
            binding.checkersBoardView.invalidate()
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
        binding.checkersBoardView.isEnabled = false
        handler.postDelayed({
            if (!isAdded || logic.isGameOver || logic.currentPlayer != 2) {
                binding.checkersBoardView.isEnabled = true
                return@postDelayed
            }

            val aiMove = logic.getAiMove()
            if (aiMove != null) {
                val moved = logic.makeMove(aiMove.fromR, aiMove.fromC, aiMove.toR, aiMove.toC)
                if (moved) {
                    binding.checkersBoardView.invalidate()
                    updateScoreboard()
                    updateTurnIndicator()

                    if (logic.isGameOver) {
                        handleGameOver()
                    } else if (logic.currentPlayer == 2 && logic.activeJumpPiece != null) {
                        // AI continues multi-jump!
                        triggerAiMove()
                    } else {
                        binding.checkersBoardView.isEnabled = true
                    }
                } else {
                    binding.checkersBoardView.isEnabled = true
                }
            } else {
                binding.checkersBoardView.isEnabled = true
            }
        }, 500)
    }

    private fun updateScoreboard() {
        binding.tvP1Count.text = logic.countPieces(1).toString()
        binding.tvP2Count.text = logic.countPieces(2).toString()

        binding.cardP1.alpha = if (logic.currentPlayer == 1) 1.0f else 0.45f
        binding.cardP2.alpha = if (logic.currentPlayer == 2) 1.0f else 0.45f
    }

    private fun updateTurnIndicator() {
        if (isOnlineMode) {
            val color = if (myPlayerNumber == 1) "Red 🔴" else "Black ⚫"
            binding.tvTurnIndicator.text = if (isMyTurnOnline) "Your Turn! ($color)" else "Opponent's Turn..."
        } else if (isAiMode) {
            binding.tvTurnIndicator.text = if (logic.currentPlayer == 1) "Your Turn (Red 🔴)" else "🤖 Bot Thinking (Black ⚫)..."
        } else {
            val color = if (logic.currentPlayer == 1) "Red 🔴" else "Black ⚫"
            binding.tvTurnIndicator.text = "Player ${logic.currentPlayer}'s Turn ($color)"
        }
    }

    private fun handleGameOver() {
        val sharedPref = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
        val username = sharedPref.getString("username", "You") ?: "You"

        if (logic.winner != 0) {
            val isUserWinner = if (isOnlineMode) logic.winner == myPlayerNumber else logic.winner == 1
            binding.tvTurnIndicator.text = if (isUserWinner) "$username Won (Shashka)! 👑🎉" else "Game Over!"
        } else {
            binding.tvTurnIndicator.text = "It's a Draw! 🤝"
        }

        // Track record
        if (logic.winner == 1 || (isOnlineMode && logic.winner == myPlayerNumber)) {
            val wins = sharedPref.getInt("checkers_wins", 0) + 1
            sharedPref.edit().putInt("checkers_wins", wins).apply()
        }

        handler.postDelayed({
            submitScoreAndExit()
        }, 1800)
    }

    private fun submitScoreAndExit() {
        val sharedPref = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
        val userId = sharedPref.getInt("user_id", -1)
        val isUserWinner = if (isOnlineMode) logic.winner == myPlayerNumber else logic.winner == 1
        val score = if (isUserWinner) 50 else if (logic.winner == 0) 10 else 0

        if (userId != -1 && score > 0) {
            ApiClient.instance.submitGameScore(GameScoreRequest(userId, "checkers", score))
                .enqueue(object : Callback<GameScoreResponse> {
                    override fun onResponse(call: Call<GameScoreResponse>, response: Response<GameScoreResponse>) {}
                    override fun onFailure(call: Call<GameScoreResponse>, t: Throwable) {}
                })
        }

        val bundle = Bundle().apply {
            putString("gameType", "checkers")
            val winMsg = if (isOnlineMode) {
                if (logic.winner == myPlayerNumber) "You Won! 👑🎉" else if (logic.winner == 0) "It's a Draw!" else "You Lost!"
            } else if (isAiMode) {
                if (logic.winner == 1) "You Won! 👑🎉" else if (logic.winner == 2) "Bot Won!" else "It's a Draw!"
            } else {
                if (logic.winner == 1) "Player 1 (Red) Wins! 👑" else if (logic.winner == 2) "Player 2 (Black) Wins! 👑" else "It's a Draw!"
            }
            putString("resultMessage", winMsg)
            putBoolean("isDraw", logic.winner == 0)
            putBoolean("isAiMode", isAiMode)
            putBoolean("isOnlineMode", isOnlineMode)
            putString("roomCode", roomCode)
            putBoolean("isHost", isHost)
        }
        findNavController().navigate(R.id.action_checkersFragment_to_resultFragment, bundle)
    }

    // Online Multiplayer Sockets & Rooms
    private fun createOnlineRoom() {
        val sharedPref = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
        val userId = sharedPref.getInt("user_id", -1)

        ApiClient.instance.createRoom(RoomCreateRequest(userId, 8, false, "checkers"))
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

                        subscribePusherEvents()
                        startLocalGame()
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
                    startLocalGame()
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

                        if (row == -99 && col == -99) {
                            // Rematch trigger!
                            logic.resetBoard()
                            binding.checkersBoardView.logic = logic
                            isMyTurnOnline = isHost
                            updateScoreboard()
                            updateTurnIndicator()
                            Toast.makeText(context, "Rematch started! 🔥", Toast.LENGTH_SHORT).show()
                            return@runOnUiThread
                        }

                        val fromR = row / 10
                        val fromC = row % 10
                        val toR = col / 10
                        val toC = col % 10

                        if (fromR in 0..7 && fromC in 0..7 && toR in 0..7 && toC in 0..7) {
                            val moved = logic.makeMove(fromR, fromC, toR, toC)
                            if (moved) {
                                binding.checkersBoardView.invalidate()
                                updateScoreboard()
                                isMyTurnOnline = (logic.currentPlayer == myPlayerNumber)
                                updateTurnIndicator()

                                if (logic.isGameOver) {
                                    handleGameOver()
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            },
            onOpponentLeft = {
                activity?.runOnUiThread {
                    Toast.makeText(context, "Opponent left! You win! 🎉", Toast.LENGTH_LONG).show()
                    logic.isGameOver = true
                    logic.winner = myPlayerNumber
                    handleGameOver()
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
