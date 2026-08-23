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
    private var aiPlayer = "O"
    private var aiObj: MinimaxAI? = null

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
        val startingPlayer = arguments?.getString("startingPlayer") ?: "X"
        val boardSize = arguments?.getInt("boardSize") ?: 3
        
        val userPlayer = startingPlayer
        aiPlayer = if (userPlayer == "X") "O" else "X"

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

        buttons = Array(boardSize) { r ->
            Array(boardSize) { c ->
                val button = Button(requireContext())
                val sizePx = (80 * resources.displayMetrics.density).toInt()
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

        // If AI is X, it should make the first move.
        triggerAiMoveIfNeeded(boardSize)
    }

    private fun triggerAiMoveIfNeeded(boardSize: Int) {
        if (!isAiMode) return
        val current = if (isInfinityMode) infinityGameLogic!!.currentPlayer else gameLogic!!.currentPlayer
        val gameOver = if (isInfinityMode) infinityGameLogic!!.isGameOver else gameLogic!!.isGameOver
        
        if (current == aiPlayer && !gameOver) {
            // Disable buttons temporarily
            setButtonsEnabled(false)
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

        val moveSuccess = if (isInfinityMode) {
            infinityGameLogic!!.makeMove(row, col)
        } else {
            gameLogic!!.makeMove(row, col)
        }

        if (moveSuccess) {
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
                if (!isAi) {
                    triggerAiMoveIfNeeded(boardSize)
                }
            }
        }
    }

    private fun renderBoard(boardSize: Int) {
        val currentBoard = if (isInfinityMode) infinityGameLogic!!.board else gameLogic!!.board
        val fadingMove = if (isInfinityMode) infinityGameLogic!!.getFadingMove() else null

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
        binding.tvTurn.text = "Player $cp's Turn"
    }

    private fun navigateToResult() {
        val winner = if (isInfinityMode) infinityGameLogic!!.winner else gameLogic!!.winner
        val bundle = Bundle().apply {
            if (winner == "Draw") {
                putString("resultMessage", "It's a Draw!")
                putBoolean("isDraw", true)
            } else {
                putString("resultMessage", "Player $winner Won")
                putBoolean("isDraw", false)
            }
        }
        findNavController().navigate(R.id.action_gameFragment_to_resultFragment, bundle)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
