package com.example.tictactoe

import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.tictactoe.databinding.FragmentConnect4Binding
import com.example.tictactoe.network.ApiClient
import com.example.tictactoe.network.GameScoreRequest
import com.example.tictactoe.network.GameScoreResponse
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class Connect4Fragment : Fragment() {

    private var _binding: FragmentConnect4Binding? = null
    private val binding get() = _binding!!

    private lateinit var logic: Connect4Logic
    private val cellViews = Array(6) { arrayOfNulls<ImageView>(7) }
    private var isAiMode = true

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentConnect4Binding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (arguments?.containsKey("isAiMode") == true) {
            isAiMode = arguments?.getBoolean("isAiMode", true) ?: true
            startGame()
        } else {
            showModeSelectionDialog()
        }

        binding.btnBack.setOnClickListener { showExitDialog() }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                showExitDialog()
            }
        })
    }

    private fun showModeSelectionDialog() {
        val options = arrayOf("🤖 Play vs AI (Bot)", "👥 2 Players (Pass & Play)")
        AlertDialog.Builder(requireContext())
            .setTitle("Select Game Mode")
            .setItems(options) { _, which ->
                isAiMode = (which == 0)
                startGame()
            }
            .setCancelable(false)
            .show()
    }

    private fun startGame() {
        logic = Connect4Logic()
        setupBoard()
        updateTurnIndicator()
    }

    private fun setupBoard() {
        binding.gridLayout.removeAllViews()
        val displayMetrics = resources.displayMetrics
        val boardWidth = displayMetrics.widthPixels - (32 * displayMetrics.density).toInt()
        val cellSize = boardWidth / 7
        val marginPx = (4 * displayMetrics.density).toInt()

        for (r in 0 until 6) {
            for (c in 0 until 7) {
                val img = ImageView(requireContext()).apply {
                    layoutParams = GridLayout.LayoutParams(
                        GridLayout.spec(r),
                        GridLayout.spec(c)
                    ).apply {
                        width = cellSize - (marginPx * 2)
                        height = cellSize - (marginPx * 2)
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
                    // AI Turn
                    binding.root.postDelayed({
                        makeMove(logic.getBestMove())
                    }, 500)
                }
            }
        }
    }

    private fun animateTokenDrop(row: Int, col: Int, player: Int) {
        val img = cellViews[row][col]!!
        val color = if (player == 1) "#EF4444" else "#FBBF24" // Red vs Yellow
        
        // Simulating drop animation
        img.translationY = -1000f
        img.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor(color))
        
        ObjectAnimator.ofFloat(img, "translationY", 0f).apply {
            duration = 400
            interpolator = android.view.animation.BounceInterpolator()
            start()
        }
    }

    private fun updateTurnIndicator() {
        val color = if (logic.currentPlayer == 1) "#EF4444" else "#FBBF24"
        val playerText = if (logic.currentPlayer == 1) {
            "Player 1's Turn (🔴)"
        } else {
            if (isAiMode) "Bot's Turn (🟡)" else "Player 2's Turn (🟡)"
        }
        binding.tvTurnIndicator.text = playerText
        binding.viewTurnColor.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor(color))
    }

    private fun handleGameOver() {
        if (logic.winner != 0) {
            // Highlight winning line
            logic.winningLine?.forEach { (r, c) ->
                cellViews[r][c]?.alpha = 0.5f // Dim slightly or add a border
            }
            binding.tvTurnIndicator.text = "Player ${logic.winner} Wins!"
        } else {
            binding.tvTurnIndicator.text = "It's a Draw!"
        }
        
        binding.root.postDelayed({
            submitScoreAndExit()
        }, 1500)
    }

    private fun submitScoreAndExit() {
        val sharedPref = requireActivity().getSharedPreferences("TicTacToePrefs", android.content.Context.MODE_PRIVATE)
        val userId = sharedPref.getInt("user_id", -1)
        val score = if (logic.winner == 1) 50 else if (logic.winner == 0) 10 else 0

        if (userId != -1 && score > 0) {
            ApiClient.instance.submitGameScore(GameScoreRequest(userId, "connect4", score))
                .enqueue(object : Callback<GameScoreResponse> {
                    override fun onResponse(call: Call<GameScoreResponse>, response: Response<GameScoreResponse>) {}
                    override fun onFailure(call: Call<GameScoreResponse>, t: Throwable) {}
                })
        }
        
        // Navigate to result
        val bundle = Bundle().apply {
            putString("gameType", "connect4")
            val winMsg = if (logic.winner == 1) "Player 1 (Red) Wins!" else if (logic.winner == 2) (if (isAiMode) "Bot (Yellow) Wins!" else "Player 2 (Yellow) Wins!") else "It's a Draw!"
            putString("resultMessage", winMsg)
            putBoolean("isDraw", logic.winner == 0)
            putBoolean("isAiMode", isAiMode)
        }
        findNavController().navigate(R.id.action_connect4Fragment_to_resultFragment, bundle)
    }

    private fun showExitDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Quit Game?")
            .setMessage("Are you sure you want to exit?")
            .setPositiveButton("Exit") { _, _ -> findNavController().navigateUp() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
