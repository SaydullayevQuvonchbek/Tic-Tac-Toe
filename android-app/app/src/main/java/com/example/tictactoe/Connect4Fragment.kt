package com.example.tictactoe

import android.animation.ObjectAnimator
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.tictactoe.databinding.FragmentConnect4Binding
import com.example.tictactoe.network.ApiClient
import com.example.tictactoe.network.EmoteRequest
import com.example.tictactoe.network.EmoteResponse
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

class Connect4Fragment : Fragment() {

    private var _binding: FragmentConnect4Binding? = null
    private val binding get() = _binding!!

    private lateinit var logic: Connect4Logic
    private val cellViews = Array(6) { arrayOfNulls<ImageView>(7) }

    private var isAiMode = true
    private var isOnlineMode = false
    private var isHost = false
    private var roomCode = ""
    private var myPlayerNumber = 1 // 1 = Red (Host/P1), 2 = Yellow (Joiner/P2)
    private var isMyTurnOnline = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentConnect4Binding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        PusherManager.connect()

        initSetupUI()

        // Handle Back Press
        binding.btnSetupBack.setOnClickListener { findNavController().navigateUp() }
        binding.btnGameplayBack.setOnClickListener { handleBackNavigation() }
        binding.btnCancelWaiting.setOnClickListener { cancelWaiting() }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : androidx.activity.OnBackPressedCallback(true) {
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
            myPlayerNumber = if (isHost) 1 else 2
            isMyTurnOnline = isHost

            startLocalGame()
            if (isOnlineMode && roomCode.isNotEmpty()) {
                subscribePusherEvents()
                val sharedPref = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
                val myUserId = sharedPref.getInt("user_id", -1)
                ApiClient.instance.makeMove(MoveRequest(roomCode, myUserId, -99, -99, -1)).enqueue(object : Callback<MoveResponse> {
                    override fun onResponse(call: Call<MoveResponse>, response: Response<MoveResponse>) {
                    if (!isAdded || _binding == null) return
                        if (!isAdded || _binding == null) return
                    }
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
                            override fun onResponse(call: Call<MoveResponse>, response: Response<MoveResponse>) {
                    if (!isAdded || _binding == null) return
                        if (!isAdded || _binding == null) return
                    }
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
        val curWins = sharedPref.getInt("connect4_wins", 0) + 1
        val curCoins = sharedPref.getInt("coins", 0) + 50
        val curXp = sharedPref.getInt("xp", 0) + 100
        sharedPref.edit()
            .putInt("connect4_wins", curWins)
            .putInt("coins", curCoins)
            .putInt("xp", curXp)
            .apply()

        val bundle = Bundle().apply {
            putString("gameType", "connect4")
            putString("resultMessage", getString(R.string.forfeit_opponent_won))
            putBoolean("isDraw", false)
            putBoolean("userWon", true)
            putBoolean("isUserWin", true)
            putBoolean("isOnlineMode", true)
            putString("roomCode", roomCode)
            putBoolean("isHost", isHost)
            putBoolean("isAiMode", false)
        }
        findNavController().navigate(R.id.action_connect4Fragment_to_resultFragment, bundle)
    }

    private fun initSetupUI() {
        showSetupScreen()

        DifficultySelector.bind(
            binding.diffSelector.segDiffEasy,
            binding.diffSelector.segDiffMedium,
            binding.diffSelector.segDiffHard,
            "connect4"
        )

        binding.rgSetupMode.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.rbJoinOnline) {
                binding.layoutRoomCode.visibility = View.VISIBLE
            } else {
                binding.layoutRoomCode.visibility = View.GONE
            }
        }

        binding.btnStartGame.setOnClickListener {
            val sharedPref = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
            val userId = sharedPref.getInt("user_id", -1)

            when (binding.rgSetupMode.checkedRadioButtonId) {
                R.id.rbQuickMatch -> {
                    MatchmakingHelper.startQuickMatch(requireContext(), "connect4", 6) { code, host, oppName, isBot ->
                        roomCode = code
                        isHost = host
                        isOnlineMode = !isBot
                        isAiMode = isBot
                        myPlayerNumber = if (isHost) 1 else 2
                        isMyTurnOnline = isHost

                        startLocalGame()
                        if (isOnlineMode && roomCode.isNotEmpty()) {
                            subscribePusherEvents()
                        }
                    }
                }
                R.id.rbVsAi -> {
                    isAiMode = true
                    isOnlineMode = false
                    startLocalGame()
                }
                R.id.rbPassPlay -> {
                    isAiMode = false
                    isOnlineMode = false
                    startLocalGame()
                }
                R.id.rbCreateOnline -> {
                    if (userId == -1) {
                        Toast.makeText(context, "Please set up your profile first!", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    createOnlineRoom(userId)
                }
                R.id.rbJoinOnline -> {
                    if (userId == -1) {
                        Toast.makeText(context, "Please set up your profile first!", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    val code = binding.etRoomCode.text.toString().trim().uppercase()
                    if (code.isEmpty()) {
                        Toast.makeText(context, "Please enter room code!", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    joinOnlineRoom(userId, code)
                }
            }
        }
    }

    private fun showSetupScreen() {
        binding.setupContainer.visibility = View.VISIBLE
        binding.waitingContainer.visibility = View.GONE
        binding.gameplayContainer.visibility = View.GONE
    }

    private fun showWaitingScreen(code: String) {
        binding.setupContainer.visibility = View.GONE
        binding.waitingContainer.visibility = View.VISIBLE
        binding.gameplayContainer.visibility = View.GONE
        binding.tvWaitingRoomCode.text = code
    }

    private fun showGameplayScreen() {
        binding.setupContainer.visibility = View.GONE
        binding.waitingContainer.visibility = View.GONE
        binding.gameplayContainer.visibility = View.VISIBLE
    }

    private fun startLocalGame() {
        logic = Connect4Logic()
        showGameplayScreen()
        setupBoard()
        updateTurnIndicator()

        binding.layoutEmotesConnect4.removeAllViews()
        val emoteBar = EmoteHelper.createEmoteBar(requireContext()) { emote ->
            EmoteHelper.showFloatingEmote(binding.root as ViewGroup, emote, isOpponent = false)
            if (isOnlineMode) {
                val emoteIndex = EmoteHelper.EMOTES.indexOf(emote)
                val sharedPref = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
                val userId = sharedPref.getInt("user_id", -1)
                ApiClient.instance.makeMove(MoveRequest(roomCode, userId, -888, emoteIndex, -1)).enqueue(object : Callback<MoveResponse> {
                    override fun onResponse(call: Call<MoveResponse>, response: Response<MoveResponse>) {
                    if (!isAdded || _binding == null) return
                        if (!isAdded || _binding == null) return
                    }
                    override fun onFailure(call: Call<MoveResponse>, t: Throwable) {
                    if (!isAdded || _binding == null) return
    t.printStackTrace()
    context?.let { android.widget.Toast.makeText(it, "Tarmoq xatosi!", android.widget.Toast.LENGTH_SHORT).show() }
}
                })
                ApiClient.instance.sendEmote(EmoteRequest(roomCode, userId, emote)).enqueue(object : Callback<EmoteResponse> {
                    override fun onResponse(call: Call<EmoteResponse>, response: Response<EmoteResponse>) {
                    if (!isAdded || _binding == null) return
                        if (!isAdded || _binding == null) return
                    }
                    override fun onFailure(call: Call<EmoteResponse>, t: Throwable) {
                    if (!isAdded || _binding == null) return
    t.printStackTrace()
    context?.let { android.widget.Toast.makeText(it, "Tarmoq xatosi!", android.widget.Toast.LENGTH_SHORT).show() }
}
                })
            }
        }
        binding.layoutEmotesConnect4.addView(emoteBar)
    }

    private fun cancelWaiting() {
        if (isOnlineMode && roomCode.isNotEmpty()) {
            PusherManager.unsubscribeFromRoom(roomCode)
            roomCode = ""
        }
    }

    private fun createOnlineRoom(userId: Int) {
        ApiClient.instance.createRoom(RoomCreateRequest(userId, 7, false))
            .enqueue(object : Callback<RoomCreateResponse> {
                override fun onResponse(call: Call<RoomCreateResponse>, response: Response<RoomCreateResponse>) {
                    if (!isAdded || _binding == null) return
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
                    startLocalGame()
                    Toast.makeText(context, "Friend joined! Match started!", Toast.LENGTH_SHORT).show()
                }
            },
            onMoveMade = { eventData ->
                activity?.runOnUiThread {
                    if (!isAdded || _binding == null) return@runOnUiThread
                    try {
                        val json = JSONObject(eventData)
                        val senderId = json.optInt("player_id", -1)
                        val sharedPref = (activity ?: return@runOnUiThread).getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
                        val myUserId = sharedPref.getInt("user_id", -1)

                        // Ignore our own move echoed back from Pusher
                        if (senderId != -1 && myUserId != -1 && senderId == myUserId) {
                            return@runOnUiThread
                        }

                        val row = json.optInt("row", -1)
                        val col = json.optInt("col", json.optString("col", "-1").toIntOrNull() ?: -1)

                        if (row == -888 || col == -888) {
                            val emoteIdx = if (col in 0..10) col else 0
                            val emote = EmoteHelper.EMOTES.getOrNull(emoteIdx) ?: "🔥"
                            EmoteHelper.showFloatingEmote(binding.root as ViewGroup, emote, isOpponent = true)
                            return@runOnUiThread
                        }

                        if (col == -999 || row == -999) {
                            handleOpponentForfeited()
                            return@runOnUiThread
                        }

                        if (col == -99 || row == -99) {
                            logic = Connect4Logic()
                            setupBoard()
                            isMyTurnOnline = isHost
                            updateTurnIndicator()
                            Toast.makeText(context, "Rematch started! 🔥", Toast.LENGTH_SHORT).show()
                            return@runOnUiThread
                        }

                        if (col in 0..6) {
                            val opponentPlayerNumber = if (myPlayerNumber == 1) 2 else 1
                            val row = logic.dropToken(col)
                            if (row != -1) {
                                animateTokenDrop(row, col, opponentPlayerNumber)
                                if (logic.isGameOver) {
                                    handleGameOver()
                                } else {
                                    isMyTurnOnline = true
                                    updateTurnIndicator()
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
                            HapticHelper.performClick(requireContext())
                        }
                    } catch (_: Exception) {}
                }
            }
        )
    }

    private fun setupBoard() {
        binding.gridLayout.removeAllViews()
        val displayMetrics = resources.displayMetrics
        val totalOuterMargin = (40 * displayMetrics.density).toInt()
        val availableWidth = displayMetrics.widthPixels - totalOuterMargin
        val cellSize = availableWidth / 7
        val marginPx = (2 * displayMetrics.density).toInt()
        val cellDimension = cellSize - (marginPx * 2)

        for (r in 0 until 6) {
            for (c in 0 until 7) {
                val img = ImageView(requireContext()).apply {
                    layoutParams = GridLayout.LayoutParams(
                        GridLayout.spec(r),
                        GridLayout.spec(c)
                    ).apply {
                        width = cellDimension
                        height = cellDimension
                        setMargins(marginPx, marginPx, marginPx, marginPx)
                    }
                    setBackgroundResource(R.drawable.bg_circle)
                    backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#1E293B"))

                    setOnClickListener { onColumnClicked(c) }
                }
                binding.gridLayout.addView(img)
                cellViews[r][c] = img
            }
        }
    }

    private fun onColumnClicked(col: Int) {
        if (logic.isGameOver) return

        if (isOnlineMode) {
            if (!isMyTurnOnline) {
                Toast.makeText(context, "Waiting for opponent's move!", Toast.LENGTH_SHORT).show()
                return
            }
            val sharedPref = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
            val userId = sharedPref.getInt("user_id", -1)

            val row = logic.dropToken(col)
            if (row != -1) {
                isMyTurnOnline = false
                animateTokenDrop(row, col, myPlayerNumber)

                ApiClient.instance.makeMove(MoveRequest(roomCode, userId, -1, col, -1))
                    .enqueue(object : Callback<MoveResponse> {
                        override fun onResponse(call: Call<MoveResponse>, response: Response<MoveResponse>) {
                    if (!isAdded || _binding == null) return
                        if (!isAdded || _binding == null) return
                    }
                        override fun onFailure(call: Call<MoveResponse>, t: Throwable) {
                    if (!isAdded || _binding == null) return
    t.printStackTrace()
    context?.let { android.widget.Toast.makeText(it, "Tarmoq xatosi!", android.widget.Toast.LENGTH_SHORT).show() }
}
                    })

                if (logic.isGameOver) {
                    handleGameOver()
                } else {
                    updateTurnIndicator()
                }
            }
            return
        }

        if (isAiMode && logic.currentPlayer == 2) return // Wait for AI

        makeMove(col)
    }

    private fun makeMove(col: Int) {
        val player = logic.currentPlayer
        val row = logic.dropToken(col)

        if (row != -1) {
            animateTokenDrop(row, col, player)

            if (logic.isGameOver) {
                handleGameOver()
            } else {
                updateTurnIndicator()
                if (isAiMode && logic.currentPlayer == 2) {
                    val difficulty = DifficultyStore.get(requireContext(), "connect4")
                    AiThinker.think(this, compute = {
                        if (logic.isGameOver || logic.currentPlayer != 2) -1 else logic.getBestMove(difficulty)
                    }, onResult = onResult@{ colResult ->
                        if (_binding == null || colResult == null || colResult < 0) return@onResult
                        if (logic.isGameOver || logic.currentPlayer != 2) return@onResult
                        makeMove(colResult)
                    })
                }
            }
        }
    }

    private fun animateTokenDrop(row: Int, col: Int, player: Int) {
        val img = cellViews[row][col] ?: return
        val color = if (player == 1) "#EF4444" else "#FBBF24"

        HapticHelper.performClick(requireContext())
        SoundHelper.playMoveSound(requireContext())

        img.translationY = -1000f
        img.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor(color))

        ObjectAnimator.ofFloat(img, "translationY", 0f).apply {
            duration = 400
            interpolator = android.view.animation.BounceInterpolator()
            start()
        }
    }

    private fun updateTurnIndicator() {
        val sharedPref = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
        val username = sharedPref.getString("username", "") ?: ""
        val displayName = if (username.isNotEmpty()) username else "Player 1"

        val color = if (logic.currentPlayer == 1) "#EF4444" else "#FBBF24"
        val playerText = if (isOnlineMode) {
            if (isMyTurnOnline) "Your Turn (${if (myPlayerNumber == 1) "🔴" else "🟡"})"
            else "Opponent's Turn (${if (myPlayerNumber == 1) "🟡" else "🔴"})"
        } else if (logic.currentPlayer == 1) {
            if (isAiMode) "$displayName's Turn (🔴)" else "Player 1's Turn (🔴)"
        } else {
            if (isAiMode) "Bot's Turn (🟡)" else "Player 2's Turn (🟡)"
        }
        binding.tvTurnIndicator.text = playerText
        binding.viewTurnColor.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor(color))
    }

    private fun handleGameOver() {
        val sharedPref = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
        val username = sharedPref.getString("username", "") ?: ""
        val displayName = if (username.isNotEmpty()) username else "You"

        if (logic.winner != 0) {
            logic.winningLine?.forEach { (r, c) ->
                cellViews[r][c]?.alpha = 0.5f
            }
            if (isOnlineMode) {
                binding.tvTurnIndicator.text = if (logic.winner == myPlayerNumber) "$displayName Wins! 🎉" else "Opponent Wins!"
            } else if (isAiMode) {
                binding.tvTurnIndicator.text = if (logic.winner == 1) "$displayName Wins! 🎉" else "Bot Wins!"
            } else {
                binding.tvTurnIndicator.text = "Player ${logic.winner} Wins!"
            }
        } else {
            binding.tvTurnIndicator.text = "It's a Draw!"
        }

        // Track record
        if (logic.winner == 1 || (isOnlineMode && logic.winner == myPlayerNumber)) {
            val wins = sharedPref.getInt("connect4_wins", 0) + 1
            sharedPref.edit().putInt("connect4_wins", wins).apply()
        }

        binding.root.postDelayed({
            if (!isAdded || _binding == null) return@postDelayed
            submitScoreAndExit()
        }, 1500)
    }

    private fun submitScoreAndExit() {
        val sharedPref = (activity ?: return).getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
        val userId = sharedPref.getInt("user_id", -1)
        val username = sharedPref.getString("username", "") ?: ""
        val displayName = if (username.isNotEmpty()) username else "You"
        val isWinner = if (isOnlineMode) logic.winner == myPlayerNumber else logic.winner == 1
        val score = if (isWinner) 50 else if (logic.winner == 0) 10 else 0

        if (userId != -1 && score > 0) {
            ApiClient.instance.submitGameScore(GameScoreRequest(userId, "connect4", score))
                .enqueue(object : Callback<GameScoreResponse> {
                    override fun onResponse(call: Call<GameScoreResponse>, response: Response<GameScoreResponse>) {
                        if (!isAdded || _binding == null) return
                    }
                    override fun onFailure(call: Call<GameScoreResponse>, t: Throwable) {
                        if (!isAdded || _binding == null) return
                        t.printStackTrace()
                        context?.let { android.widget.Toast.makeText(it, "Tarmoq xatosi!", android.widget.Toast.LENGTH_SHORT).show() }
                    }
                })
        }

        val bundle = Bundle().apply {
            putString("gameType", "connect4")
            val winMsg = if (isOnlineMode) {
                if (logic.winner == myPlayerNumber) "$displayName Won! 🎉" else if (logic.winner == 0) "It's a Draw!" else "You Lost!"
            } else if (isAiMode) {
                if (logic.winner == 1) "$displayName Won! 🎉" else if (logic.winner == 2) "Bot Won!" else "It's a Draw!"
            } else {
                if (logic.winner == 1) "Player 1 (Red) Wins!" else if (logic.winner == 2) "Player 2 (Yellow) Wins!" else "It's a Draw!"
            }
            putString("resultMessage", winMsg)
            putBoolean("isDraw", logic.winner == 0)
            putBoolean("userWon", isWinner)
            putBoolean("isUserWin", isWinner)
            putBoolean("isAiMode", isAiMode)
            putBoolean("isOnlineMode", isOnlineMode)
            putString("roomCode", roomCode)
            putBoolean("isHost", isHost)
        }
        findNavController().navigate(R.id.action_connect4Fragment_to_resultFragment, bundle)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        for (r in 0 until 6) {
            for (c in 0 until 7) {
                cellViews[r][c] = null
            }
        }
        if (isOnlineMode && roomCode.isNotEmpty()) {
            PusherManager.unsubscribeFromRoom(roomCode)
        }
        _binding = null
    }
}
