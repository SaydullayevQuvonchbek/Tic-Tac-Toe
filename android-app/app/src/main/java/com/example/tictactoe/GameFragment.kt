package com.example.tictactoe

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.tictactoe.databinding.FragmentGameBinding
import com.example.tictactoe.network.ApiClient
import com.example.tictactoe.network.EmoteRequest
import com.example.tictactoe.network.EmoteResponse
import com.example.tictactoe.network.MoveRequest
import com.example.tictactoe.network.MoveResponse
import com.example.tictactoe.network.PusherManager
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class GameFragment : Fragment() {

    private var _binding: FragmentGameBinding? = null
    private val binding get() = _binding!!
    private var gameLogic: GameLogic? = null
    private var infinityGameLogic: InfinityGameLogic? = null
    private lateinit var buttons: Array<Array<Button>>
    private var isInfinityMode = false
    private var isAiMode = false
    private var isArcadeMode = false
    private var username = ""
    private var aiPlayer = "O"
    private var aiObj: MinimaxAI? = null
    
    // Online Multiplayer
    private var isOnlineMode = false
    private var roomCode = ""
    private var playerId = -1
    private var isHost = false
    private var myOnlineSymbol = "X"
    private var isMyTurnOnline = false
    private var boardSize = 3
    
    private var countDownTimer: android.os.CountDownTimer? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGameBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        isInfinityMode = arguments?.getBoolean("isInfinityMode") ?: false
        isAiMode = arguments?.getBoolean("isAiMode") ?: false
        isArcadeMode = arguments?.getBoolean("isArcadeMode") ?: false
        isOnlineMode = arguments?.getBoolean("isOnlineMode") ?: false
        
        if (isOnlineMode) {
            roomCode = arguments?.getString("roomCode") ?: ""
            playerId = arguments?.getInt("playerId") ?: -1
            isHost = arguments?.getBoolean("isHost") ?: false
            myOnlineSymbol = if (isHost) "X" else "O"
            isMyTurnOnline = isHost // X always starts
        }
        
        username = arguments?.getString("username") ?: ""
        val startingPlayer = arguments?.getString("startingPlayer") ?: "X"
        boardSize = arguments?.getInt("boardSize") ?: 3
        
        val userPlayer = startingPlayer
        aiPlayer = if (userPlayer == "X") "O" else "X"

        if (isArcadeMode) {
            binding.tvTimer.visibility = View.VISIBLE
        }

        if (isAiMode) {
            aiObj = MinimaxAI() // AI logic will be updated next
        }

        if (isInfinityMode) {
            infinityGameLogic = InfinityGameLogic(boardSize)
            infinityGameLogic?.currentPlayer = "X"
        } else {
            gameLogic = GameLogic(boardSize)
            gameLogic?.currentPlayer = "X"
        }

        updateTurnText()

        binding.btnGameBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        val gridLayout = binding.gridLayout
        gridLayout.columnCount = boardSize
        gridLayout.rowCount = boardSize

        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels - (48 * displayMetrics.density)
        val sizePx = (screenWidth / boardSize).toInt() - (8 * displayMetrics.density).toInt()

        buttons = Array(boardSize) { r ->
            Array(boardSize) { c ->
                val button = Button(requireContext())
                val marginPx = (4 * resources.displayMetrics.density).toInt()

                val params = android.widget.GridLayout.LayoutParams()
                params.width = sizePx
                params.height = sizePx
                params.setMargins(marginPx, marginPx, marginPx, marginPx)
                button.layoutParams = params

                button.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFFF5F5F5.toInt())
                button.textSize = if (boardSize == 5) 24f else 36f
                button.setTextColor(0xFF333333.toInt()) // Default text color

                button.setOnClickListener { onCellClicked(r, c) }
                gridLayout.addView(button)
                button
            }
        }
        renderBoard(boardSize)

        val emoteBar = EmoteHelper.createEmoteBar(requireContext()) { emote ->
            EmoteHelper.showFloatingEmote(binding.root as ViewGroup, emote, isOpponent = false)
            if (isOnlineMode) {
                val emoteIndex = EmoteHelper.EMOTES.indexOf(emote)
                ApiClient.instance.makeMove(MoveRequest(roomCode, playerId, -888, emoteIndex, -1)).enqueue(object : Callback<MoveResponse> {
                    override fun onResponse(call: Call<MoveResponse>, response: Response<MoveResponse>) { if (!isAdded || _binding == null) return }
                    override fun onFailure(call: Call<MoveResponse>, t: Throwable) { if (!isAdded || _binding == null) return }
                })
                ApiClient.instance.sendEmote(EmoteRequest(roomCode, playerId, emote)).enqueue(object : Callback<EmoteResponse> {
                    override fun onResponse(call: Call<EmoteResponse>, response: Response<EmoteResponse>) { if (!isAdded || _binding == null) return }
                    override fun onFailure(call: Call<EmoteResponse>, t: Throwable) { if (!isAdded || _binding == null) return }
                })
            }
        }
        binding.layoutEmotes.addView(emoteBar)

        val isRematch = arguments?.getBoolean("isRematch", false) ?: false
        if (isOnlineMode) {
            setupPusher()
            if (isRematch) {
                if (isInfinityMode) {
                    infinityGameLogic = com.example.tictactoe.InfinityGameLogic(boardSize)
                    infinityGameLogic?.currentPlayer = "X"
                } else {
                    gameLogic = com.example.tictactoe.GameLogic(boardSize)
                    gameLogic?.currentPlayer = "X"
                }
                renderBoard(boardSize)
            }
            isMyTurnOnline = isHost
            setButtonsEnabled(isMyTurnOnline, boardSize)
            updateTurnText()
        } else {
            // If AI is X, it should make the first move.
            triggerAiMoveIfNeeded(boardSize)
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                android.app.AlertDialog.Builder(requireContext())
                    .setTitle("Exit Game?")
                    .setMessage(if (isOnlineMode) "If you exit, the match will be forfeited." else "Do you want to return to the menu?")
                    .setPositiveButton("Yes, Exit") { _, _ ->
                        if (isOnlineMode && roomCode.isNotEmpty()) {
                            ApiClient.instance.makeMove(
                                MoveRequest(roomCode, playerId, -999, -999, -1)
                            ).enqueue(object : Callback<MoveResponse> {
                                override fun onResponse(c: Call<MoveResponse>, r: Response<MoveResponse>) { if (!isAdded || _binding == null) return }
                                override fun onFailure(c: Call<MoveResponse>, t: Throwable) { if (!isAdded || _binding == null) return }
                            })
                            PusherManager.unsubscribeFromRoom(roomCode)
                        }
                        Toast.makeText(context, getString(R.string.forfeit_you_lost), Toast.LENGTH_SHORT).show()
                        findNavController().navigateUp()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        })
    }

    private fun handleOpponentForfeited() {
        val sharedPref = requireActivity().getSharedPreferences("TicTacToePrefs", android.content.Context.MODE_PRIVATE)
        val curWins = sharedPref.getInt("wins", 0) + 1
        val curCoins = sharedPref.getInt("coins", 0) + 50
        val curXp = sharedPref.getInt("xp", 0) + 100
        sharedPref.edit()
            .putInt("wins", curWins)
            .putInt("coins", curCoins)
            .putInt("xp", curXp)
            .apply()

        val bundle = Bundle().apply {
            putString("gameType", "tic_tac_toe")
            putString("resultMessage", getString(R.string.forfeit_opponent_won))
            putBoolean("isDraw", false)
            putBoolean("isOnlineMode", true)
            putString("roomCode", roomCode)
            putInt("playerId", playerId)
            putBoolean("isHost", isHost)
            putString("username", username)
            putInt("boardSize", boardSize)
        }
        findNavController().navigate(R.id.action_gameFragment_to_resultFragment, bundle)
    }

    private fun setupPusher() {
        val pusherManager = PusherManager
        pusherManager.connect()
        pusherManager.subscribeToRoom(roomCode,
            onGameStarted = { data: String ->
                activity?.runOnUiThread {
                    if (!isAdded || _binding == null) return@runOnUiThread
                    isMyTurnOnline = isHost
                    val size = if (isInfinityMode) infinityGameLogic!!.size else gameLogic!!.size
                    setButtonsEnabled(isMyTurnOnline, size)
                    updateTurnText()
                }
            },
            onMoveMade = { data: String ->
                activity?.runOnUiThread {
                    if (!isAdded || _binding == null) return@runOnUiThread
                    try {
                        val json = JSONObject(data)
                        val senderId = json.optInt("player_id", json.optString("player_id", "-1").toIntOrNull() ?: -1)
                        if (senderId != -1 && senderId != playerId) {
                            val row = json.optInt("row", json.optString("row", "0").toIntOrNull() ?: 0)
                            val col = json.optInt("col", json.optString("col", "0").toIntOrNull() ?: 0)

                            if (row == -888 || col == -888) {
                                val emoteIdx = if (col in 0..10) col else 0
                                val emote = EmoteHelper.EMOTES.getOrNull(emoteIdx) ?: "🔥"
                                EmoteHelper.showFloatingEmote(binding.root as ViewGroup, emote, isOpponent = true)
                                return@runOnUiThread
                            }

                            if (row == -999 && col == -999) {
                                handleOpponentForfeited()
                                return@runOnUiThread
                            }

                            if (row == -99 && col == -99) {
                                // Rematch signal!
                                if (isInfinityMode) {
                                    infinityGameLogic = InfinityGameLogic(boardSize)
                                    infinityGameLogic?.currentPlayer = "X"
                                } else {
                                    gameLogic = GameLogic(boardSize)
                                    gameLogic?.currentPlayer = "X"
                                }
                                renderBoard(boardSize)
                                isMyTurnOnline = isHost
                                setButtonsEnabled(isMyTurnOnline, boardSize)
                                updateTurnText()
                                return@runOnUiThread
                            }

                            // Opponent made a move
                            HapticHelper.performClick(context ?: return@runOnUiThread)
                            SoundHelper.playMoveSound(context ?: return@runOnUiThread)

                            if (isInfinityMode) {
                                infinityGameLogic!!.makeMove(row, col)
                            } else {
                                gameLogic!!.makeMove(row, col)
                            }
                            isMyTurnOnline = true
                            val size = if (isInfinityMode) infinityGameLogic!!.size else gameLogic!!.size
                            setButtonsEnabled(true, size)
                            renderBoard(size)
                            updateTurnText()
                            checkOnlineWinner()
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
            onEmoteReceived = { eventData: String ->
                activity?.runOnUiThread {
                    if (!isAdded || _binding == null) return@runOnUiThread
                    try {
                        var json = JSONObject(eventData)
                        if (json.has("data") && json.get("data") is String) {
                            json = JSONObject(json.getString("data"))
                        }
                        val senderId = json.optInt("player_id", json.optString("player_id", "-1").toIntOrNull() ?: -1)
                        if (senderId != -1 && senderId == playerId) return@runOnUiThread
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

    private fun checkOnlineWinner() {
        val isGameOver = if (isInfinityMode) infinityGameLogic!!.isGameOver else gameLogic!!.isGameOver
        if (isGameOver) {
            navigateToResult()
        } else {
            updateTurnText()
        }
    }

    private fun triggerAiMoveIfNeeded(boardSize: Int) {
        if (!isAiMode || _binding == null) return
        val current = if (isInfinityMode) infinityGameLogic!!.currentPlayer else gameLogic!!.currentPlayer
        val gameOver = if (isInfinityMode) infinityGameLogic!!.isGameOver else gameLogic!!.isGameOver
        if (current != aiPlayer || gameOver) return

        setButtonsEnabled(false, boardSize)
        val liveBoard = if (isInfinityMode) infinityGameLogic!!.board else gameLogic!!.board
        val snapshot = Array(boardSize) { r -> liveBoard[r].copyOf() }
        val infinity = isInfinityMode
        val difficulty = DifficultyStore.get(requireContext(), "tictactoe")
        val ai = aiObj

        AiThinker.think(this, compute = {
            if (infinity) {
                // MinimaxAI can't model the fade queue — pick a random empty cell.
                val empties = ArrayList<Pair<Int, Int>>()
                for (i in 0 until boardSize) for (j in 0 until boardSize) if (snapshot[i][j] == "") empties.add(i to j)
                empties.randomOrNull()
            } else {
                ai?.findBestMove(snapshot, aiPlayer, boardSize, difficulty)
            }
        }, onResult = onResult@{ move ->
            if (_binding == null) return@onResult
            setButtonsEnabled(true, boardSize)
            val stillOver = if (isInfinityMode) infinityGameLogic!!.isGameOver else gameLogic!!.isGameOver
            val stillCurrent = if (isInfinityMode) infinityGameLogic!!.currentPlayer else gameLogic!!.currentPlayer
            if (move == null || stillOver || stillCurrent != aiPlayer) return@onResult
            onCellClicked(move.first, move.second, isAi = true)
        })
    }

    private fun setButtonsEnabled(enabled: Boolean, boardSize: Int) {
        for (r in 0 until boardSize) {
            for (c in 0 until boardSize) {
                buttons[r][c].isEnabled = enabled
            }
        }
    }

    private fun onCellClicked(row: Int, col: Int, isAi: Boolean = false) {
        val current = if (isInfinityMode) infinityGameLogic!!.currentPlayer else gameLogic!!.currentPlayer
        if (isAiMode && current == aiPlayer && !isAi) return // Prevent user from tapping on AI's turn
        if (isOnlineMode) {
            if (!isMyTurnOnline || current != myOnlineSymbol) return
        }

        val moveSuccess = if (isInfinityMode) {
            infinityGameLogic!!.makeMove(row, col)
        } else {
            gameLogic!!.makeMove(row, col)
        }

        if (moveSuccess) {
            if (isOnlineMode) {
                isMyTurnOnline = false
                setButtonsEnabled(false, if (isInfinityMode) infinityGameLogic!!.size else gameLogic!!.size)
                // Send move to API
                com.example.tictactoe.network.ApiClient.instance.makeMove(
                    com.example.tictactoe.network.MoveRequest(roomCode, playerId, row, col, -1)
                ).enqueue(object : retrofit2.Callback<com.example.tictactoe.network.MoveResponse> {
                    override fun onResponse(c: retrofit2.Call<com.example.tictactoe.network.MoveResponse>, r: retrofit2.Response<com.example.tictactoe.network.MoveResponse>) { if (!isAdded || _binding == null) return }
                    override fun onFailure(c: retrofit2.Call<com.example.tictactoe.network.MoveResponse>, t: Throwable) { if (!isAdded || _binding == null) return
    t.printStackTrace()
    context?.let { android.widget.Toast.makeText(it, "Tarmoq xatosi!", android.widget.Toast.LENGTH_SHORT).show() }
}
                })
            }

            // Haptic feedback for user (not AI)
            if (!isAi) {
                binding.root.performHapticFeedback(
                    android.view.HapticFeedbackConstants.VIRTUAL_KEY,
                    android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                )
            }

            val boardSize = if (isInfinityMode) infinityGameLogic!!.size else gameLogic!!.size
            renderBoard(boardSize)
            
            val isGameOver = if (isInfinityMode) infinityGameLogic!!.isGameOver else gameLogic!!.isGameOver
            if (isGameOver) {
                navigateToResult()
            } else {
                updateTurnText()
                if (!isAi && !isOnlineMode) {
                    triggerAiMoveIfNeeded(boardSize)
                }
            }
        }
    }

    private fun renderBoard(boardSize: Int) {
        val currentBoard = if (isInfinityMode) infinityGameLogic!!.board else gameLogic!!.board
        val fadingMove = if (isInfinityMode) infinityGameLogic!!.getFadingMove() else null
        val winningLine = if (isInfinityMode) infinityGameLogic!!.winningLine else gameLogic!!.winningLine

        for (r in 0 until boardSize) {
            for (c in 0 until boardSize) {
                val player = currentBoard[r][c]
                val button = buttons[r][c]
                button.text = player
                if (player == "X") {
                    button.setTextColor(0xFF556B2F.toInt())
                } else if (player == "O") {
                    button.setTextColor(0xFF8B0000.toInt())
                }
                
                // Highlight winning line
                if (winningLine != null && winningLine.contains(Pair(r, c))) {
                    button.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFFC8E6C9.toInt()) // Light green
                } else {
                    button.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFFF5F5F5.toInt())
                }
                
                // Animatsiya: O'chib ketuvchi toshni xiralashtirish
                if (isInfinityMode && fadingMove != null && fadingMove.first == r && fadingMove.second == c) {
                    button.alpha = 0.3f
                } else {
                    button.alpha = 1.0f
                }
            }
        }
    }

    private fun updateTurnText() {
        if (isOnlineMode) {
            val symbol = myOnlineSymbol
            val oppSymbol = if (symbol == "X") "O" else "X"
            binding.tvTurn.text = if (isMyTurnOnline) "Your Turn! ($symbol ⚡)" else "Opponent's Turn... ($oppSymbol)"
            return
        }
        val cp = if (isInfinityMode) infinityGameLogic!!.currentPlayer else gameLogic!!.currentPlayer
        binding.tvTurn.text = if (username.isNotEmpty() && cp == arguments?.getString("startingPlayer")) "$username's Turn" else "Player $cp's Turn"
        startTimer()
    }

    private fun startTimer() {
        if (!isArcadeMode) return
        countDownTimer?.cancel()
        countDownTimer = object : android.os.CountDownTimer(6000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                binding.tvTimer.text = "${millisUntilFinished / 1000}s"
            }
            override fun onFinish() {
                // Time's up! Current player loses.
                val cp = if (isInfinityMode) infinityGameLogic!!.currentPlayer else gameLogic!!.currentPlayer
                val winner = if (cp == "X") "O" else "X"
                
                if (isInfinityMode) {
                    infinityGameLogic!!.winner = winner
                    infinityGameLogic!!.isGameOver = true
                } else {
                    gameLogic!!.winner = winner
                    gameLogic!!.isGameOver = true
                }
                navigateToResult()
            }
        }.start()
    }

    private var isNavigating = false

    private fun navigateToResult() {
        if (isNavigating) return
        isNavigating = true
        
        countDownTimer?.cancel()

        val winner = if (isInfinityMode) infinityGameLogic!!.winner else gameLogic!!.winner
        
        if (isOnlineMode) {
            var result = "loss"
            if (winner == "Draw") result = "draw"
            else if (winner == myOnlineSymbol) result = "win"

            // Optional: send match result to update XP
            com.example.tictactoe.network.ApiClient.instance.matchResult(
                com.example.tictactoe.network.MatchResultRequest(playerId, result)
            ).enqueue(object : retrofit2.Callback<com.example.tictactoe.network.MatchResultResponse> {
                override fun onResponse(c: retrofit2.Call<com.example.tictactoe.network.MatchResultResponse>, r: retrofit2.Response<com.example.tictactoe.network.MatchResultResponse>) { if (!isAdded || _binding == null) return }
                override fun onFailure(c: retrofit2.Call<com.example.tictactoe.network.MatchResultResponse>, t: Throwable) { if (!isAdded || _binding == null) return }
            })
        }

        val sharedPref = requireActivity().getSharedPreferences("TicTacToePrefs", android.content.Context.MODE_PRIVATE)
        val isUserWinner = if (isOnlineMode) winner == myOnlineSymbol else (username.isNotEmpty() && winner == arguments?.getString("startingPlayer")) || (!isAiMode && winner == "X") || (isAiMode && winner == "X")
        var earnedXp = 0
        var earnedCoins = 0
        if (winner != "Draw" && isUserWinner) {
            val currentWins = sharedPref.getInt("wins", 0) + 1
            val addCoins = if (isOnlineMode) 50 else 20
            val addXp = if (isOnlineMode) 100 else 50
            earnedXp = addXp
            earnedCoins = addCoins
            val currentCoins = sharedPref.getInt("coins", 0) + addCoins
            val currentXp = sharedPref.getInt("xp", 0) + addXp
            sharedPref.edit()
                .putInt("wins", currentWins)
                .putInt("coins", currentCoins)
                .putInt("xp", currentXp)
                .apply()

            QuestManager.recordGamePlayed(requireContext(), "tic_tac_toe", isOnlineMode, true)
        } else {
            QuestManager.recordGamePlayed(requireContext(), "tic_tac_toe", isOnlineMode, false)
        }

        val bundle = Bundle().apply {
            putString("gameType", "tic_tac_toe")
            putInt("xpEarned", earnedXp)
            putInt("coinsEarned", earnedCoins)
            if (winner == "Draw") {
                putString("resultMessage", "It's a Draw!")
                putBoolean("isDraw", true)
            } else {
                val winnerName = if (username.isNotEmpty() && winner == arguments?.getString("startingPlayer")) username else "Player $winner"
                val display = if (isOnlineMode) {
                    if (winner == myOnlineSymbol) "You Won!" else "You Lost!"
                } else {
                    "$winnerName Wins!"
                }
                putString("resultMessage", display)
                putBoolean("isDraw", false)
            }
            
            // Pass the exact same configuration so ResultFragment can replay
            putBoolean("isInfinityMode", isInfinityMode)
            putBoolean("isAiMode", isAiMode)
            putBoolean("isArcadeMode", isArcadeMode)
            putBoolean("isOnlineMode", isOnlineMode)
            putString("roomCode", roomCode)
            putInt("playerId", playerId)
            putBoolean("isHost", isHost)
            putString("username", username)
            putString("startingPlayer", arguments?.getString("startingPlayer") ?: "X")
            putInt("boardSize", arguments?.getInt("boardSize") ?: 3)
        }
        
        // Disable buttons so no further clicks occur
        val size = if (isInfinityMode) infinityGameLogic!!.size else gameLogic!!.size
        setButtonsEnabled(false, size)
        
        // Show result after a longer delay so user can clearly see the final move
        binding.root.postDelayed({
            if (!isAdded || _binding == null) return@postDelayed
            try {
                findNavController().navigate(R.id.action_gameFragment_to_resultFragment, bundle)
            } catch (e: Exception) {}
        }, 2500)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        countDownTimer?.cancel()
        if (isOnlineMode) {
            com.example.tictactoe.network.PusherManager.unsubscribeFromRoom(roomCode)
        }
        _binding = null
    }
}
