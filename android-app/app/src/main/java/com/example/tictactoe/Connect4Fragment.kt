package com.example.tictactoe

import android.animation.ObjectAnimator
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.tictactoe.databinding.FragmentConnect4Binding
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
    private var waitingDialog: AlertDialog? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentConnect4Binding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        PusherManager.connect()

        if (arguments?.containsKey("isAiMode") == true && arguments?.getBoolean("isOnlineMode", false) == false) {
            isAiMode = arguments?.getBoolean("isAiMode", true) ?: true
            isOnlineMode = false
            startGame()
        } else {
            showCustomSetupDialog()
        }

        binding.btnBack.setOnClickListener { showExitDialog() }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                showExitDialog()
            }
        })
    }

    private fun showCustomSetupDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_connect4_setup, null)
        val rgMode = dialogView.findViewById<RadioGroup>(R.id.rgMode)
        val layoutRoomCode = dialogView.findViewById<LinearLayout>(R.id.layoutRoomCode)
        val etRoomCode = dialogView.findViewById<EditText>(R.id.etRoomCode)
        val btnProceed = dialogView.findViewById<View>(R.id.btnProceed)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        rgMode.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.rbJoinOnline) {
                layoutRoomCode.visibility = View.VISIBLE
            } else {
                layoutRoomCode.visibility = View.GONE
            }
        }

        btnProceed.setOnClickListener {
            val sharedPref = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
            val userId = sharedPref.getInt("user_id", -1)

            when (rgMode.checkedRadioButtonId) {
                R.id.rbVsAi -> {
                    isAiMode = true
                    isOnlineMode = false
                    dialog.dismiss()
                    startGame()
                }
                R.id.rbPassPlay -> {
                    isAiMode = false
                    isOnlineMode = false
                    dialog.dismiss()
                    startGame()
                }
                R.id.rbCreateOnline -> {
                    if (userId == -1) {
                        Toast.makeText(context, "Please set up your profile first!", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    dialog.dismiss()
                    createOnlineRoom(userId)
                }
                R.id.rbJoinOnline -> {
                    if (userId == -1) {
                        Toast.makeText(context, "Please set up your profile first!", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    val code = etRoomCode.text.toString().trim().uppercase()
                    if (code.isEmpty()) {
                        Toast.makeText(context, "Please enter room code!", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    dialog.dismiss()
                    joinOnlineRoom(userId, code)
                }
            }
        }

        dialog.show()
    }

    private fun createOnlineRoom(userId: Int) {
        val pd = android.app.ProgressDialog(context)
        pd.setMessage("Creating Online Room...")
        pd.setCancelable(false)
        pd.show()

        ApiClient.instance.createRoom(RoomCreateRequest(userId, 7, false))
            .enqueue(object : Callback<RoomCreateResponse> {
                override fun onResponse(call: Call<RoomCreateResponse>, response: Response<RoomCreateResponse>) {
                    pd.dismiss()
                    if (response.isSuccessful && response.body()?.status == "success") {
                        roomCode = response.body()?.room_code ?: ""
                        isOnlineMode = true
                        isHost = true
                        myPlayerNumber = 1
                        isMyTurnOnline = true
                        
                        subscribePusherEvents()
                        showWaitingDialog(roomCode)
                    } else {
                        Toast.makeText(context, "Failed to create room", Toast.LENGTH_SHORT).show()
                        showCustomSetupDialog()
                    }
                }

                override fun onFailure(call: Call<RoomCreateResponse>, t: Throwable) {
                    pd.dismiss()
                    Toast.makeText(context, "Network Error: ${t.message}", Toast.LENGTH_SHORT).show()
                    showCustomSetupDialog()
                }
            })
    }

    private fun showWaitingDialog(code: String) {
        val waitingView = layoutInflater.inflate(R.layout.dialog_connect4_waiting, null)
        val tvCode = waitingView.findViewById<TextView>(R.id.tvWaitingRoomCode)
        val btnCancel = waitingView.findViewById<View>(R.id.btnCancelWaiting)

        tvCode.text = code

        waitingDialog = AlertDialog.Builder(requireContext())
            .setView(waitingView)
            .setCancelable(false)
            .create()

        waitingDialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnCancel.setOnClickListener {
            PusherManager.unsubscribeFromRoom(code)
            waitingDialog?.dismiss()
            showCustomSetupDialog()
        }

        waitingDialog?.show()
    }

    private fun joinOnlineRoom(userId: Int, code: String) {
        val pd = android.app.ProgressDialog(context)
        pd.setMessage("Joining Room $code...")
        pd.setCancelable(false)
        pd.show()

        ApiClient.instance.joinRoom(RoomJoinRequest(userId, code))
            .enqueue(object : Callback<RoomJoinResponse> {
                override fun onResponse(call: Call<RoomJoinResponse>, response: Response<RoomJoinResponse>) {
                    pd.dismiss()
                    if (response.isSuccessful && response.body()?.status == "success") {
                        roomCode = code
                        isOnlineMode = true
                        isHost = false
                        myPlayerNumber = 2
                        isMyTurnOnline = false

                        subscribePusherEvents()
                        startGame()
                        Toast.makeText(context, "Connected! Match started!", Toast.LENGTH_SHORT).show()
                    } else {
                        val msg = response.body()?.message ?: "Room not found or full"
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        showCustomSetupDialog()
                    }
                }

                override fun onFailure(call: Call<RoomJoinResponse>, t: Throwable) {
                    pd.dismiss()
                    Toast.makeText(context, "Network Error: ${t.message}", Toast.LENGTH_SHORT).show()
                    showCustomSetupDialog()
                }
            })
    }

    private fun subscribePusherEvents() {
        PusherManager.subscribeToRoom(
            roomCode = roomCode,
            onGameStarted = {
                activity?.runOnUiThread {
                    waitingDialog?.dismiss()
                    startGame()
                    Toast.makeText(context, "Friend joined! Match started!", Toast.LENGTH_SHORT).show()
                }
            },
            onMoveMade = { eventData ->
                activity?.runOnUiThread {
                    try {
                        val json = JSONObject(eventData)
                        val col = json.getInt("col")
                        val player = if (myPlayerNumber == 1) 2 else 1
                        
                        val row = logic.dropToken(col)
                        if (row != -1) {
                            animateTokenDrop(row, col, player)
                            if (logic.isGameOver) {
                                handleGameOver()
                            } else {
                                isMyTurnOnline = true
                                updateTurnIndicator()
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

    private fun startGame() {
        logic = Connect4Logic()
        setupBoard()
        updateTurnIndicator()
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

                // Send move over network
                ApiClient.instance.makeMove(MoveRequest(roomCode, userId, -1, col, -1))
                    .enqueue(object : Callback<MoveResponse> {
                        override fun onResponse(call: Call<MoveResponse>, response: Response<MoveResponse>) {}
                        override fun onFailure(call: Call<MoveResponse>, t: Throwable) {}
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
                    // AI Turn
                    binding.root.postDelayed({
                        makeMove(logic.getBestMove())
                    }, 500)
                }
            }
        }
    }

    private fun animateTokenDrop(row: Int, col: Int, player: Int) {
        val img = cellViews[row][col] ?: return
        val color = if (player == 1) "#EF4444" else "#FBBF24" // Red vs Yellow
        
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
        val playerText = if (isOnlineMode) {
            if (isMyTurnOnline) "Your Turn (${if (myPlayerNumber == 1) "🔴" else "🟡"})" 
            else "Opponent's Turn (${if (myPlayerNumber == 1) "🟡" else "🔴"})"
        } else if (logic.currentPlayer == 1) {
            "Player 1's Turn (🔴)"
        } else {
            if (isAiMode) "Bot's Turn (🟡)" else "Player 2's Turn (🟡)"
        }
        binding.tvTurnIndicator.text = playerText
        binding.viewTurnColor.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor(color))
    }

    private fun handleGameOver() {
        if (logic.winner != 0) {
            logic.winningLine?.forEach { (r, c) ->
                cellViews[r][c]?.alpha = 0.5f
            }
            if (isOnlineMode) {
                binding.tvTurnIndicator.text = if (logic.winner == myPlayerNumber) "You Win! 🎉" else "Opponent Wins!"
            } else {
                binding.tvTurnIndicator.text = "Player ${logic.winner} Wins!"
            }
        } else {
            binding.tvTurnIndicator.text = "It's a Draw!"
        }
        
        binding.root.postDelayed({
            submitScoreAndExit()
        }, 1500)
    }

    private fun submitScoreAndExit() {
        val sharedPref = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
        val userId = sharedPref.getInt("user_id", -1)
        val isWinner = if (isOnlineMode) logic.winner == myPlayerNumber else logic.winner == 1
        val score = if (isWinner) 50 else if (logic.winner == 0) 10 else 0

        if (userId != -1 && score > 0) {
            ApiClient.instance.submitGameScore(GameScoreRequest(userId, "connect4", score))
                .enqueue(object : Callback<GameScoreResponse> {
                    override fun onResponse(call: Call<GameScoreResponse>, response: Response<GameScoreResponse>) {}
                    override fun onFailure(call: Call<GameScoreResponse>, t: Throwable) {}
                })
        }

        if (isOnlineMode && roomCode.isNotEmpty()) {
            PusherManager.unsubscribeFromRoom(roomCode)
        }
        
        // Navigate to result
        val bundle = Bundle().apply {
            putString("gameType", "connect4")
            val winMsg = if (isOnlineMode) {
                if (logic.winner == myPlayerNumber) "You Won! 🎉" else if (logic.winner == 0) "It's a Draw!" else "You Lost!"
            } else {
                if (logic.winner == 1) "Player 1 (Red) Wins!" else if (logic.winner == 2) (if (isAiMode) "Bot (Yellow) Wins!" else "Player 2 (Yellow) Wins!") else "It's a Draw!"
            }
            putString("resultMessage", winMsg)
            putBoolean("isDraw", logic.winner == 0)
            putBoolean("isAiMode", isAiMode)
            putBoolean("isOnlineMode", isOnlineMode)
        }
        findNavController().navigate(R.id.action_connect4Fragment_to_resultFragment, bundle)
    }

    private fun showExitDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Quit Game?")
            .setMessage("Are you sure you want to exit?")
            .setPositiveButton("Exit") { _, _ ->
                if (isOnlineMode && roomCode.isNotEmpty()) {
                    PusherManager.unsubscribeFromRoom(roomCode)
                }
                findNavController().navigateUp()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (isOnlineMode && roomCode.isNotEmpty()) {
            PusherManager.unsubscribeFromRoom(roomCode)
        }
        waitingDialog?.dismiss()
        _binding = null
    }
}
