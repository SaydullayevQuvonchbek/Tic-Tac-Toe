package com.example.tictactoe

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.tictactoe.databinding.FragmentCheckersBinding
import com.example.tictactoe.network.ApiClient
import com.example.tictactoe.network.EmoteRequest
import com.example.tictactoe.network.EmoteResponse
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

    private val logic = CheckersLogic(8)
    private var isAiMode = false
    private var isOnlineMode = false
    private var roomCode = ""
    private var isHost = false
    private var myPlayerNumber = 1
    private var isMyTurnOnline = true
    private var currentBoardSize = 8

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCheckersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initSetupUI()

        binding.btnSetupBack.setOnClickListener { findNavController().navigateUp() }
        binding.btnGameplayBack.setOnClickListener { handleBackNavigation() }
        binding.btnCancelWaiting.setOnClickListener { cancelWaiting() }
        binding.btnInviteFriend.setOnClickListener {
            ShareInviteHelper.shareRoomCode(requireContext(), "Shashka (Checkers)", roomCode)
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackNavigation()
            }
        })

        val isRematch = arguments?.getBoolean("isRematch", false) ?: false
        if (isRematch) {
            isOnlineMode = arguments?.getBoolean("isOnlineMode", false) ?: false
            roomCode = arguments?.getString("roomCode", "") ?: ""
            isHost = arguments?.getBoolean("isHost", false) ?: false
            isAiMode = arguments?.getBoolean("isAiMode", false) ?: false
            currentBoardSize = arguments?.getInt("boardSize", 8) ?: 8
            myPlayerNumber = if (isHost) 1 else 2
            isMyTurnOnline = isHost

            startLocalGame(currentBoardSize)
            if (isOnlineMode && roomCode.isNotEmpty()) {
                subscribePusherEvents()
                val sharedPref = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
                val myUserId = sharedPref.getInt("user_id", -1)
                ApiClient.instance.makeMove(MoveRequest(roomCode, myUserId, -99, -99, -1)).enqueue(object : Callback<MoveResponse> {
                    override fun onResponse(call: Call<MoveResponse>, response: Response<MoveResponse>) { if (!isAdded || _binding == null) return }
                    override fun onFailure(call: Call<MoveResponse>, t: Throwable) {
                        if (!isAdded || _binding == null) return
                        t.printStackTrace()
                        context?.let { android.widget.Toast.makeText(it, "Tarmoq xatosi!", android.widget.Toast.LENGTH_SHORT).show() }
                    }
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
                            override fun onResponse(call: Call<MoveResponse>, response: Response<MoveResponse>) { if (!isAdded || _binding == null) return }
                            override fun onFailure(call: Call<MoveResponse>, t: Throwable) {
                                if (!isAdded || _binding == null) return
                                t.printStackTrace()
                                context?.let { android.widget.Toast.makeText(it, "Tarmoq xatosi!", android.widget.Toast.LENGTH_SHORT).show() }
                            }
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
        val curWins = sharedPref.getInt("checkers_wins", 0) + 1
        val curCoins = sharedPref.getInt("coins", 0) + 50
        val curXp = sharedPref.getInt("xp", 0) + 100
        sharedPref.edit()
            .putInt("checkers_wins", curWins)
            .putInt("coins", curCoins)
            .putInt("xp", curXp)
            .apply()

        QuestManager.recordGamePlayed(requireContext(), "checkers", isOnline = true, isWin = true)

        val resultBundle = Bundle().apply {
            putString("resultMessage", getString(R.string.forfeit_opponent_won))
            putBoolean("isDraw", false)
            putBoolean("isOnlineMode", true)
            putString("roomCode", roomCode)
            putBoolean("isHost", isHost)
            putString("gameType", "checkers")
            putBoolean("isAiMode", false)
            putInt("boardSize", currentBoardSize)
        }
        findNavController().navigate(R.id.action_checkersFragment_to_resultFragment, resultBundle)
    }

    private fun initSetupUI() {
        showSetupScreen()

        DifficultySelector.bind(
            binding.diffSelector.segDiffEasy,
            binding.diffSelector.segDiffMedium,
            binding.diffSelector.segDiffHard,
            "checkers"
        )

        binding.rgMode.setOnCheckedChangeListener { _, checkedId ->
            binding.onlineOptionsContainer.visibility = if (checkedId == R.id.rbOnline) View.VISIBLE else View.GONE
            binding.btnStartGame.visibility = if (checkedId == R.id.rbOnline) View.GONE else View.VISIBLE
        }

        binding.btnStartGame.setOnClickListener {
            currentBoardSize = if (binding.rb10x10.isChecked) 10 else 8

            if (binding.rbQuickMatch.isChecked) {
                MatchmakingHelper.startQuickMatch(requireContext(), "checkers", currentBoardSize) { code, host, oppName, isBot ->
                    roomCode = code
                    isHost = host
                    isOnlineMode = !isBot
                    isAiMode = isBot
                    myPlayerNumber = if (isHost) 1 else 2
                    isMyTurnOnline = isHost

                    startLocalGame(currentBoardSize)
                    if (isOnlineMode && roomCode.isNotEmpty()) {
                        subscribePusherEvents()
                    }
                }
                return@setOnClickListener
            }

            isAiMode = binding.rbAi.isChecked
            isOnlineMode = false
            startLocalGame(currentBoardSize)
        }

        binding.btnCreateRoom.setOnClickListener {
            currentBoardSize = if (binding.rb10x10.isChecked) 10 else 8
            createOnlineRoom(currentBoardSize)
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
        currentBoardSize = size
        applyEquippedCustomization()
        logic.resetBoard(currentBoardSize)
        binding.checkersBoardView.logic = logic
        binding.checkersBoardView.isFlipped = (isOnlineMode && myPlayerNumber == 2)

        val sharedPref = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
        val username = sharedPref.getString("username", "Player 1") ?: "Player 1"

        binding.tvP1Name.text = if (isOnlineMode) (if (isHost) username else "Opponent") else username
        binding.tvP2Name.text = if (isOnlineMode) (if (isHost) "Opponent" else username) else if (isAiMode) "🤖 AI Bot" else "Player 2"

        updateScoreboard()
        updateTurnIndicator()
        showGameplayScreen()

        binding.layoutEmotesCheckers.removeAllViews()
        val emoteBar = EmoteHelper.createEmoteBar(requireContext()) { emote ->
            EmoteHelper.showFloatingEmote(binding.root as ViewGroup, emote, isOpponent = false)
            if (isOnlineMode) {
                val emoteIndex = EmoteHelper.EMOTES.indexOf(emote)
                val sharedPref = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
                val userId = sharedPref.getInt("user_id", -1)
                ApiClient.instance.makeMove(MoveRequest(roomCode, userId, -888, emoteIndex, -1)).enqueue(object : Callback<MoveResponse> {
                    override fun onResponse(call: Call<MoveResponse>, response: Response<MoveResponse>) { if (!isAdded || _binding == null) return }
                    override fun onFailure(call: Call<MoveResponse>, t: Throwable) {
                        if (!isAdded || _binding == null) return
                        t.printStackTrace()
                        context?.let { android.widget.Toast.makeText(it, "Tarmoq xatosi!", android.widget.Toast.LENGTH_SHORT).show() }
                    }
                })
                ApiClient.instance.sendEmote(EmoteRequest(roomCode, userId, emote)).enqueue(object : Callback<EmoteResponse> {
                    override fun onResponse(call: Call<EmoteResponse>, response: Response<EmoteResponse>) { if (!isAdded || _binding == null) return }
                    override fun onFailure(call: Call<EmoteResponse>, t: Throwable) {
                        if (!isAdded || _binding == null) return
                        t.printStackTrace()
                        context?.let { android.widget.Toast.makeText(it, "Tarmoq xatosi!", android.widget.Toast.LENGTH_SHORT).show() }
                    }
                })
            }
        }
        binding.layoutEmotesCheckers.addView(emoteBar)

        binding.checkersBoardView.onMoveExecutedListener = { fromR, fromC, toR, toC ->
            handleMove(fromR, fromC, toR, toC)
        }
    }

    private fun applyEquippedCustomization() {
        binding.checkersBoardView.boardTheme = CheckersThemeManager.getEquippedBoardTheme(requireContext())
        binding.checkersBoardView.pieceSkin = CheckersThemeManager.getEquippedPieceSkin(requireContext())
    }

    /** After a jump that continues, re-select the landed piece so the player sees the next hop. */
    private fun autoSelectContinuedJump() {
        val jp = logic.activeJumpPiece ?: return
        binding.checkersBoardView.selectedR = jp.first
        binding.checkersBoardView.selectedC = jp.second
        binding.checkersBoardView.validMoves = logic.getValidMovesForPiece(jp.first, jp.second)
        binding.checkersBoardView.invalidate()
    }

    private fun handleMove(fromR: Int, fromC: Int, toR: Int, toC: Int) {
        if (logic.isGameOver) return

        HapticHelper.performHeavyImpact(requireContext())
        SoundHelper.playCaptureSound(requireContext())

        if (isOnlineMode) {
            if (!isMyTurnOnline) {
                Toast.makeText(context, "Waiting for opponent's move!", Toast.LENGTH_SHORT).show()
                return
            }

            val moved = logic.makeMove(fromR, fromC, toR, toC)
            if (moved) {
                binding.checkersBoardView.invalidate()
                updateScoreboard()

                val continuesJump = (logic.activeJumpPiece != null)
                if (!continuesJump) {
                    isMyTurnOnline = false
                } else {
                    autoSelectContinuedJump()
                }
                updateTurnIndicator()

                val sharedPref = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
                val userId = sharedPref.getInt("user_id", -1)
                val encodedFrom = fromR * 100 + fromC
                val encodedTo = toR * 100 + toC

                ApiClient.instance.makeMove(MoveRequest(roomCode, userId, encodedFrom, encodedTo, -1)).enqueue(object : Callback<MoveResponse> {
                    override fun onResponse(call: Call<MoveResponse>, response: Response<MoveResponse>) { if (!isAdded || _binding == null) return }
                    override fun onFailure(call: Call<MoveResponse>, t: Throwable) {
                        if (!isAdded || _binding == null) return
                        t.printStackTrace()
                        context?.let { android.widget.Toast.makeText(it, "Tarmoq xatosi!", android.widget.Toast.LENGTH_SHORT).show() }
                    }
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

            when {
                logic.isGameOver -> handleGameOver()
                logic.activeJumpPiece != null && logic.currentPlayer == 1 -> autoSelectContinuedJump()
                isAiMode && logic.currentPlayer == 2 -> triggerAiMove()
            }
        }
    }

    private fun triggerAiMove() {
        _binding ?: return
        binding.checkersBoardView.isEnabled = false
        val difficulty = DifficultyStore.get(requireContext(), "checkers")

        AiThinker.think(
            owner = this,
            compute = {
                if (logic.isGameOver || logic.currentPlayer != 2) null
                else logic.getAiMove(difficulty)
            },
            onResult = onResult@{ aiMove ->
                if (_binding == null) return@onResult
                if (aiMove == null || logic.isGameOver || logic.currentPlayer != 2) {
                    binding.checkersBoardView.isEnabled = true
                    return@onResult
                }
                val moved = logic.makeMove(aiMove.fromR, aiMove.fromC, aiMove.toR, aiMove.toC)
                if (!moved) {
                    binding.checkersBoardView.isEnabled = true
                    return@onResult
                }
                binding.checkersBoardView.invalidate()
                updateScoreboard()
                updateTurnIndicator()
                when {
                    logic.isGameOver -> handleGameOver()
                    logic.currentPlayer == 2 && logic.activeJumpPiece != null -> triggerAiMove()
                    else -> binding.checkersBoardView.isEnabled = true
                }
            }
        )
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
        val myUsername = sharedPref.getString("username", "Player 1") ?: "Player 1"

        val winnerName = when {
            logic.winner == 1 -> if (isOnlineMode) (if (isHost) myUsername else "Opponent") else (if (isAiMode) myUsername else "Player 1")
            logic.winner == 2 -> if (isOnlineMode) (if (isHost) "Opponent" else myUsername) else (if (isAiMode) "🤖 AI Bot" else "Player 2")
            else -> "Nobody (Draw)"
        }

        val isUserWin = (isOnlineMode && ((isHost && logic.winner == 1) || (!isHost && logic.winner == 2))) ||
                (!isOnlineMode && logic.winner == 1)

        if (isUserWin) {
            val currentWins = sharedPref.getInt("checkers_wins", 0)
            sharedPref.edit().putInt("checkers_wins", currentWins + 1).apply()
        }

        QuestManager.recordGamePlayed(requireContext(), "checkers", isOnlineMode, isUserWin)

        val resultBundle = Bundle().apply {
            putString("resultMessage", if (logic.winner == 0) "Draw!" else "$winnerName Won!")
            putBoolean("isDraw", logic.winner == 0)
            putBoolean("isOnlineMode", isOnlineMode)
            putString("roomCode", roomCode)
            putBoolean("isHost", isHost)
            putString("gameType", "checkers")
            putBoolean("isAiMode", isAiMode)
            putInt("boardSize", currentBoardSize)
        }

        findNavController().navigate(R.id.action_checkersFragment_to_resultFragment, resultBundle)
    }

    private fun createOnlineRoom(size: Int) {
        val sharedPref = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
        val userId = sharedPref.getInt("user_id", -1)

        ApiClient.instance.createRoom(RoomCreateRequest(userId, size, false, "checkers"))
            .enqueue(object : Callback<RoomCreateResponse> {
                override fun onResponse(call: Call<RoomCreateResponse>, response: Response<RoomCreateResponse>) {
                    if (!isAdded || _binding == null) return
                    if (response.isSuccessful && response.body()?.status == "success") {
                        roomCode = response.body()?.room_code ?: ""
                        isHost = true
                        myPlayerNumber = 1
                        isOnlineMode = true
                        isMyTurnOnline = true

                        showWaitingScreen(roomCode)
                        subscribePusherEvents()
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
        ApiClient.instance.joinRoom(RoomJoinRequest(userId, code))
            .enqueue(object : Callback<RoomJoinResponse> {
                override fun onResponse(call: Call<RoomJoinResponse>, response: Response<RoomJoinResponse>) {
                    if (!isAdded || _binding == null) return
                    if (response.isSuccessful && response.body()?.status == "success") {
                        roomCode = code
                        isHost = false
                        myPlayerNumber = 2
                        isOnlineMode = true
                        isMyTurnOnline = false
                        currentBoardSize = response.body()?.board_size ?: 8

                        startLocalGame(currentBoardSize)
                        subscribePusherEvents()
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

    private fun subscribePusherEvents() {
        PusherManager.subscribeToRoom(
            roomCode = roomCode,
            onGameStarted = {
                activity?.runOnUiThread {
                    if (!isAdded || _binding == null) return@runOnUiThread
                    startLocalGame(currentBoardSize)
                }
            },
            onMoveMade = { eventData ->
                activity?.runOnUiThread {
                    if (!isAdded || _binding == null) return@runOnUiThread
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

                        if (row == -888) {
                            val emote = EmoteHelper.EMOTES.getOrNull(col) ?: "🔥"
                            EmoteHelper.showFloatingEmote(binding.root as ViewGroup, emote, isOpponent = true)
                            HapticHelper.performClick(context ?: return@runOnUiThread)
                            return@runOnUiThread
                        }

                        if (row == -999 && col == -999) {
                            handleOpponentForfeited()
                            return@runOnUiThread
                        }

                        if (row == -99 && col == -99) {
                            logic.resetBoard(currentBoardSize)
                            binding.checkersBoardView.invalidate()
                            isMyTurnOnline = isHost
                            updateScoreboard()
                            updateTurnIndicator()
                            return@runOnUiThread
                        }

                        val fromR = if (row >= 100) row / 100 else row / 10
                        val fromC = if (row >= 100) row % 100 else row % 10
                        val toR = if (col >= 100) col / 100 else col / 10
                        val toC = if (col >= 100) col % 100 else col % 10

                        val moved = logic.applyNetworkMove(fromR, fromC, toR, toC)
                        if (moved) {
                            HapticHelper.performClick(context ?: return@runOnUiThread)
                            SoundHelper.playMoveSound(context ?: return@runOnUiThread)
                            binding.checkersBoardView.invalidate()
                            updateScoreboard()

                            val continuesJump = (logic.activeJumpPiece != null)
                            if (!continuesJump) {
                                isMyTurnOnline = true
                            }
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
                    if (!isAdded || _binding == null) return@runOnUiThread
                    handleOpponentForfeited()
                }
            },
            onEmoteReceived = { eventData ->
                activity?.runOnUiThread {
                    if (!isAdded || _binding == null) return@runOnUiThread
                    try {
                        var json = JSONObject(eventData)
                        if (json.has("data") && json.get("data") is String) {
                            json = JSONObject(json.getString("data"))
                        }
                        val senderId = json.optInt("player_id", json.optString("player_id", "-1").toIntOrNull() ?: -1)
                        val sharedPref = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
                        val myUserId = sharedPref.getInt("user_id", -1)
                        if (senderId != -1 && myUserId != -1 && senderId == myUserId) return@runOnUiThread

                        val emote = json.optString("emote", "🔥")
                        if (emote.isNotEmpty()) {
                            EmoteHelper.showFloatingEmote(binding.root as ViewGroup, emote, isOpponent = true)
                            HapticHelper.performClick(context ?: return@runOnUiThread)
                        }
                    } catch (_: Exception) {}
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
        _binding = null
    }
}
