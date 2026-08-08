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
    private lateinit var gameLogic: GameLogic
    private lateinit var buttons: Array<Array<Button>>

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGameBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        gameLogic = GameLogic()
        val startingPlayer = arguments?.getString("startingPlayer") ?: "X"
        gameLogic.currentPlayer = startingPlayer

        updateTurnText()

        buttons = Array(3) { r ->
            Array(3) { c ->
                val btnId = resources.getIdentifier("btn$r$c", "id", requireActivity().packageName)
                val button = view.findViewById<Button>(btnId)
                button.setOnClickListener { onCellClicked(r, c) }
                button
            }
        }
    }

    private fun onCellClicked(row: Int, col: Int) {
        if (gameLogic.makeMove(row, col)) {
            val button = buttons[row][col]
            val player = gameLogic.board[row][col]
            button.text = player
            button.setTextColor(if (player == "X") 0xFF556B2F.toInt() else 0xFF8B0000.toInt())

            if (gameLogic.isGameOver) {
                navigateToResult()
            } else {
                updateTurnText()
            }
        }
    }

    private fun updateTurnText() {
        binding.tvTurn.text = "Player ${gameLogic.currentPlayer}'s Turn"
    }

    private fun navigateToResult() {
        val bundle = Bundle().apply {
            if (gameLogic.winner == "Draw") {
                putString("resultMessage", "It's a Draw!")
                putBoolean("isDraw", true)
            } else {
                putString("resultMessage", "Player ${gameLogic.winner} Won")
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
