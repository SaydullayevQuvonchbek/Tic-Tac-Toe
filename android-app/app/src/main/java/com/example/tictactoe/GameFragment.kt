package com.example.tictactoe

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.tictactoe.databinding.FragmentGameBinding

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
        val boardSize = arguments?.getInt("boardSize") ?: 3
        
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

        val gridLayout = view.findViewById<android.widget.GridLayout>(R.id.gridLayout)
        gridLayout.columnCount = boardSize
        gridLayout.rowCount = boardSize

        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels - (64 * displayMetrics.density) // 32dp margin on each side
        val sizePx = (screenWidth / boardSize).toInt() - (8 * displayMetrics.density).toInt() // subtract margin

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

        if (isOnlineMode) {
            binding.tvTurn.text = if (isHost) "Room: $roomCode. Waiting..." else "Game Started!"
            setButtonsEnabled(false, boardSize)
            setupPusher()
        } else {
            // If AI is X, it should make the first move.
            triggerAiMoveIfNeeded(boardSize)
        }
    }

    private fun setupPusher() {
        val pusherManager = com.example.tictactoe.network.PusherManager
        pusherManager.connect()
        pusherManager.subscribeToRoom(roomCode,
            onGameStarted = { data ->
                activity?.runOnUiThread {
                    binding.tvTurn.text = "Opponent joined! Your symbol: $myOnlineSymbol"
                    if (isMyTurnOnline) {
                        setButtonsEnabled(true, if (isInfinityMode) infinityGameLogic!!.size else gameLogic!!.size)
                    }
                }
            },
            onMoveMade = { data ->
                activity?.runOnUiThread {
                    try {
                        val json = org.json.JSONObject(data)
                        val senderId = json.optInt("player_id", json.optString("player_id", "-1").toIntOrNull() ?: -1)
                        if (senderId != -1 && senderId != playerId) {
                            val row = json.optInt("row", json.optString("row", "0").toIntOrNull() ?: 0)
                            val col = json.optInt("col", json.optString("col", "0").toIntOrNull() ?: 0)
                            // Opponent made a move
                            if (isInfinityMode) {
                                infinityGameLogic!!.makeMove(row, col)
                            } else {
                                gameLogic!!.makeMove(row, col)
                            }
                            isMyTurnOnline = true
                            setButtonsEnabled(true, if (isInfinityMode) infinityGameLogic!!.size else gameLogic!!.size)
                            val size = if (isInfinityMode) infinityGameLogic!!.size else gameLogic!!.size
                            renderBoard(size)
                            checkOnlineWinner()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            },
            onOpponentLeft = {
                activity?.runOnUiThread {
                    android.widget.Toast.makeText(context, "Opponent left! You win!", android.widget.Toast.LENGTH_LONG).show()
                    val winner = myOnlineSymbol
                    if (isInfinityMode) {
                        infinityGameLogic!!.winner = winner
                        infinityGameLogic!!.isGameOver = true
                    } else {
                        gameLogic!!.winner = winner
                        gameLogic!!.isGameOver = true
                    }
                    navigateToResult()
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
        if (!isAiMode) return
        val current = if (isInfinityMode) infinityGameLogic!!.currentPlayer else gameLogic!!.currentPlayer
        val gameOver = if (isInfinityMode) infinityGameLogic!!.isGameOver else gameLogic!!.isGameOver
        
        if (current == aiPlayer && !gameOver) {
            // Disable buttons temporarily
            setButtonsEnabled(false, boardSize)
            binding.root.postDelayed({
                val board = if (isInfinityMode) infinityGameLogic!!.board else gameLogic!!.board
                // Wait! Infinity mode AI is much harder because minimax needs to simulate queues.
                // Our MinimaxAI only works for classic mode!
                // For Infinity mode, we will just pick a random empty spot for now.
                if (isInfinityMode) {
                    val emptySpots = mutableListOf<Pair<Int, Int>>()
                    for (i in 0 until boardSize) {
                        for (j in 0 until boardSize) {
                            if (board[i][j] == "") emptySpots.add(Pair(i, j))
                        }
                    }
                    if (emptySpots.isNotEmpty()) {
                        val move = emptySpots.random()
                        onCellClicked(move.first, move.second, isAi = true)
                    }
                } else {
                    val bestMove = aiObj?.findBestMove(board, aiPlayer, boardSize)
                    if (bestMove != null) {
                        onCellClicked(bestMove.first, bestMove.second, isAi = true)
                    }
                }
                setButtonsEnabled(true, boardSize)
            }, 500)
        }
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
                    override fun onResponse(c: retrofit2.Call<com.example.tictactoe.network.MoveResponse>, r: retrofit2.Response<com.example.tictactoe.network.MoveResponse>) {}
                    override fun onFailure(c: retrofit2.Call<com.example.tictactoe.network.MoveResponse>, t: Throwable) {}
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
        if (isOnlineMode) {
            com.example.tictactoe.network.PusherManager.unsubscribeFromRoom(roomCode)
        }

        val winner = if (isInfinityMode) infinityGameLogic!!.winner else gameLogic!!.winner
        
        if (isOnlineMode) {
            var result = "loss"
            if (winner == "Draw") result = "draw"
            else if (winner == myOnlineSymbol) result = "win"

            // Optional: send match result to update XP
            com.example.tictactoe.network.ApiClient.instance.matchResult(
                com.example.tictactoe.network.MatchResultRequest(playerId, result)
            ).enqueue(object : retrofit2.Callback<com.example.tictactoe.network.MatchResultResponse> {
                override fun onResponse(c: retrofit2.Call<com.example.tictactoe.network.MatchResultResponse>, r: retrofit2.Response<com.example.tictactoe.network.MatchResultResponse>) {}
                override fun onFailure(c: retrofit2.Call<com.example.tictactoe.network.MatchResultResponse>, t: Throwable) {}
            })
        }

        val bundle = Bundle().apply {
            putString("gameType", "tic_tac_toe")
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
