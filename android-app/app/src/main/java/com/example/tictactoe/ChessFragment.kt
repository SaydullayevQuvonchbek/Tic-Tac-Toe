package com.example.tictactoe

import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.tictactoe.databinding.FragmentChessBinding
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

class ChessFragment : Fragment() {

    private var _binding: FragmentChessBinding? = null
    private val binding get() = _binding!!

    private val logic = ChessLogic()
    private val handler = Handler(Looper.getMainLooper())

    private var isAiMode = true
    private var isPassAndPlay = false
    private var isOnlineMode = false
    private var isHost = true

    private var myUserId = -1
    private var roomCode = ""
    private var myColor: PieceColor = PieceColor.WHITE

    private var selectedPiecePos: Pair<Int, Int>? = null
    private var currentValidMoves: List<ChessMove> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChessBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
        myUserId = prefs.getInt("user_id", -1)

        setupUI()
        setupListeners()

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackNavigation()
            }
        })
    }

    private fun setupUI() {
        binding.chessBoardView.logic = logic
        binding.chessBoardView.onSquareTapped = { r, c ->
            handleSquareTapped(r, c)
        }

        // Paste Room Code Icon
        binding.tilRoomCode.setEndIconOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).text?.toString()?.trim()?.uppercase() ?: ""
                if (text.isNotEmpty()) {
                    binding.etRoomCode.setText(text)
                    HapticHelper.performClick(requireContext())
                    Toast.makeText(context, "Xona kodi qo'yildi: $text", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Quick Emotes Setup
        val emotes = listOf("👑", "🔥", "😎", "🤔", "👏", "💀")
        binding.layoutEmotes.removeAllViews()
        for (emote in emotes) {
            val btn = Button(requireContext()).apply {
                text = emote
                textSize = 16f
                layoutParams = LinearLayout.LayoutParams(42.dpToPx(), 42.dpToPx()).apply {
                    setMargins(3.dpToPx(), 0, 3.dpToPx(), 0)
                }
                setBackgroundResource(R.drawable.badge_pill)
                setPadding(0, 0, 0, 0)
                setOnClickListener {
                    sendEmote(emote)
                }
            }
            binding.layoutEmotes.addView(btn)
        }
    }

    private fun setupListeners() {
        binding.btnBackSetup.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnGameplayBack.setOnClickListener {
            handleBackNavigation()
        }

        binding.rgGameMode.setOnCheckedChangeListener { _, checkedId ->
            binding.cardOnlineOptions.visibility = if (checkedId == R.id.rbOnlineMode) View.VISIBLE else View.GONE
        }

        binding.rgOnlineType.setOnCheckedChangeListener { _, checkedId ->
            binding.tilRoomCode.visibility = if (checkedId == R.id.rbJoinRoom) View.VISIBLE else View.GONE
        }

        binding.btnStartGame.setOnClickListener {
            when (binding.rgGameMode.checkedRadioButtonId) {
                R.id.rbAiMode -> {
                    isAiMode = true
                    isPassAndPlay = false
                    isOnlineMode = false
                    myColor = PieceColor.WHITE
                    startLocalGame()
                }
                R.id.rbPassAndPlay -> {
                    isAiMode = false
                    isPassAndPlay = true
                    isOnlineMode = false
                    myColor = PieceColor.WHITE
                    startLocalGame()
                }
                R.id.rbOnlineMode -> {
                    if (binding.rbCreateRoom.isChecked) {
                        createOnlineRoom()
                    } else {
                        val code = binding.etRoomCode.text.toString().trim().uppercase()
                        if (code.isNotEmpty()) {
                            joinOnlineRoom(code)
                        } else {
                            Toast.makeText(context, "Iltimos xona kodini kiriting", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        binding.btnCancelWaiting.setOnClickListener {
            if (roomCode.isNotEmpty()) {
                PusherManager.unsubscribeFromRoom(roomCode)
            }
            showSetupScreen()
        }

        binding.btnInviteFriend.setOnClickListener {
            ShareInviteHelper.shareRoomCode(requireActivity(), roomCode, "Chess")
        }

        binding.btnResign.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Taslim bo'lish?")
                .setMessage("Haqiqatdan ham taslim bo'lmoqchimisiz?")
                .setPositiveButton("Ha, Taslim bo'lish") { _, _ ->
                    handleResign()
                }
                .setNegativeButton("Yo'q", null)
                .show()
        }
    }

    private fun startLocalGame() {
        logic.resetBoard()
        selectedPiecePos = null
        currentValidMoves = emptyList()
        binding.chessBoardView.lastMove = null
        binding.chessBoardView.isFlipped = false

        binding.setupContainer.visibility = View.GONE
        binding.waitingContainer.visibility = View.GONE
        binding.gameplayContainer.visibility = View.VISIBLE

        binding.tvRoomCodeHeader.text = if (isAiMode) "🤖 Shaxmat (Bot)" else "📱 Shaxmat (2 O'yinchi)"
        binding.tvPlayerName.text = if (isPassAndPlay) "Oqlar ⚪" else "Siz ⚪"
        binding.tvOpponentName.text = if (isAiMode) "Bot 🤖 (Qora)" else if (isPassAndPlay) "Qoralar ⚫" else "Raqib ⚫"

        updateBoardUI()
    }

    private fun createOnlineRoom() {
        binding.setupContainer.visibility = View.GONE
        binding.waitingContainer.visibility = View.VISIBLE
        binding.gameplayContainer.visibility = View.GONE

        ApiClient.instance.createRoom(RoomCreateRequest(myUserId, 8, false, "chess")).enqueue(object : Callback<RoomCreateResponse> {
            override fun onResponse(call: Call<RoomCreateResponse>, response: Response<RoomCreateResponse>) {
                val code = response.body()?.room_code
                if (response.isSuccessful && !code.isNullOrEmpty()) {
                    roomCode = code
                    isHost = true
                    isOnlineMode = true
                    isAiMode = false
                    isPassAndPlay = false
                    myColor = PieceColor.WHITE
                    binding.tvCreatedRoomCode.text = code

                    subscribePusherEvents()
                } else {
                    Toast.makeText(context, "Xona yaratib bo'lmadi", Toast.LENGTH_SHORT).show()
                    showSetupScreen()
                }
            }

            override fun onFailure(call: Call<RoomCreateResponse>, t: Throwable) {
                Toast.makeText(context, "Tarmoq xatosi: ${t.message}", Toast.LENGTH_SHORT).show()
                showSetupScreen()
            }
        })
    }

    private fun joinOnlineRoom(code: String) {
        val pd = android.app.ProgressDialog(context).apply {
            setMessage("Shaxmat xonasiga ulanmoqda...")
            show()
        }

        ApiClient.instance.joinRoom(RoomJoinRequest(myUserId, code)).enqueue(object : Callback<RoomJoinResponse> {
            override fun onResponse(call: Call<RoomJoinResponse>, response: Response<RoomJoinResponse>) {
                try { pd.dismiss() } catch (_: Exception) {}
                if (response.isSuccessful && response.body()?.status == "success") {
                    roomCode = code
                    isHost = false
                    isOnlineMode = true
                    isAiMode = false
                    isPassAndPlay = false
                    myColor = PieceColor.BLACK // Guest plays Black

                    subscribePusherEvents()
                    startOnlineGame()
                } else {
                    val msg = response.body()?.message ?: "Xonaga ulanib bo'lmadi"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<RoomJoinResponse>, t: Throwable) {
                try { pd.dismiss() } catch (_: Exception) {}
                Toast.makeText(context, "Tarmoq xatosi: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun subscribePusherEvents() {
        PusherManager.connect()
        PusherManager.subscribeToRoom(roomCode,
            onGameStarted = {
                activity?.runOnUiThread {
                    startOnlineGame()
                }
            },
            onMoveMade = { data ->
                activity?.runOnUiThread {
                    handlePusherMove(data)
                }
            },
            onOpponentLeft = {
                activity?.runOnUiThread {
                    handleOpponentLeft()
                }
            },
            onEmoteReceived = { data ->
                activity?.runOnUiThread {
                    handlePusherEmote(data)
                }
            }
        )
    }

    private fun startOnlineGame() {
        logic.resetBoard()
        selectedPiecePos = null
        currentValidMoves = emptyList()
        binding.chessBoardView.lastMove = null
        binding.chessBoardView.isFlipped = (myColor == PieceColor.BLACK)

        binding.setupContainer.visibility = View.GONE
        binding.waitingContainer.visibility = View.GONE
        binding.gameplayContainer.visibility = View.VISIBLE

        binding.tvRoomCodeHeader.text = "Xona: $roomCode"
        binding.tvPlayerName.text = "Siz (${if (myColor == PieceColor.WHITE) "⚪ Oqlar" else "⚫ Qoralar"})"
        binding.tvOpponentName.text = "Raqib (${if (myColor == PieceColor.WHITE) "⚫ Qoralar" else "⚪ Oqlar"})"

        updateBoardUI()
    }

    private fun handleSquareTapped(r: Int, c: Int) {
        if (logic.isGameOver) return

        // In online mode, player can only move on their turn
        if (isOnlineMode && logic.currentTurn != myColor) {
            binding.tvGameStatus.text = "⏳ Raqibingiz o'ylamoqda..."
            return
        }

        // In AI mode, player can only move on White turn
        if (isAiMode && logic.currentTurn != PieceColor.WHITE) {
            return
        }

        val clickedPiece = logic.board[r][c]
        val currentSelected = selectedPiecePos

        if (currentSelected == null) {
            // Select piece
            if (clickedPiece != null && clickedPiece.color == logic.currentTurn) {
                selectedPiecePos = Pair(r, c)
                currentValidMoves = logic.getLegalMovesForPiece(r, c)
                HapticHelper.performClick(requireContext())
                updateSelectionUI()
            }
        } else {
            // Already selected a piece: Check if target square is a valid move
            val matchingMove = currentValidMoves.firstOrNull { it.toR == r && it.toC == c }

            if (matchingMove != null) {
                // Check if move is a Pawn Promotion
                if (matchingMove.piece.type == PieceType.PAWN && (matchingMove.toR == 0 || matchingMove.toR == 7)) {
                    showPromotionDialog(matchingMove)
                } else {
                    executePlayerMove(matchingMove)
                }
            } else if (clickedPiece != null && clickedPiece.color == logic.currentTurn) {
                // Switch selection to another friendly piece
                selectedPiecePos = Pair(r, c)
                currentValidMoves = logic.getLegalMovesForPiece(r, c)
                HapticHelper.performClick(requireContext())
                updateSelectionUI()
            } else {
                // Deselect
                selectedPiecePos = null
                currentValidMoves = emptyList()
                updateSelectionUI()
            }
        }
    }

    private fun showPromotionDialog(baseMove: ChessMove) {
        val options = arrayOf("Farzin (Queen) ♛", "Ruh (Rook) ♜", "Fil (Bishop) ♝", "Ot (Knight) ♞")
        val types = listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT)

        AlertDialog.Builder(requireContext())
            .setTitle("Piyodani almashtiring 👑")
            .setItems(options) { _, which ->
                val selectedType = types[which]
                val promoMove = baseMove.copy(promotionType = selectedType)
                executePlayerMove(promoMove)
            }
            .setCancelable(false)
            .show()
    }

    private fun executePlayerMove(move: ChessMove) {
        val success = logic.makeMove(move)
        if (!success) return

        HapticHelper.performHeavyImpact(requireContext())
        if (move.capturedPiece != null) {
            SoundHelper.playCaptureSound(requireContext())
        } else {
            SoundHelper.playMoveSound(requireContext())
        }

        binding.chessBoardView.lastMove = move
        selectedPiecePos = null
        currentValidMoves = emptyList()
        updateBoardUI()

        if (isOnlineMode) {
            sendOnlineMove(move)
        } else if (isAiMode && !logic.isGameOver && logic.currentTurn == PieceColor.BLACK) {
            triggerBotMove()
        }

        checkGameOver()
    }

    private fun triggerBotMove() {
        binding.tvGameStatus.text = "🤖 Bot o'ylamoqda..."
        handler.postDelayed({
            if (!isAdded || logic.isGameOver) return@postDelayed

            val botMove = ChessAI.getBestMove(logic, depth = 3)
            if (botMove != null) {
                logic.makeMove(botMove)
                binding.chessBoardView.lastMove = botMove
                HapticHelper.performHeavyImpact(requireContext())
                if (botMove.capturedPiece != null) {
                    SoundHelper.playCaptureSound(requireContext())
                } else {
                    SoundHelper.playMoveSound(requireContext())
                }
                updateBoardUI()
                checkGameOver()
            }
        }, 400)
    }

    private fun sendOnlineMove(move: ChessMove) {
        val encodedFrom = move.fromR * 10 + move.fromC
        val encodedTo = move.toR * 10 + move.toC
        val promoCode = when (move.promotionType) {
            PieceType.QUEEN -> 1
            PieceType.ROOK -> 2
            PieceType.BISHOP -> 3
            PieceType.KNIGHT -> 4
            else -> 0
        }

        ApiClient.instance.makeMove(MoveRequest(roomCode, myUserId, encodedFrom, encodedTo, promoCode)).enqueue(object : Callback<MoveResponse> {
            override fun onResponse(call: Call<MoveResponse>, response: Response<MoveResponse>) {}
            override fun onFailure(call: Call<MoveResponse>, t: Throwable) {}
        })
    }

    private fun handlePusherMove(data: String) {
        try {
            var json = JSONObject(data)
            if (json.has("data")) {
                val d = json.get("data")
                if (d is String) json = JSONObject(d)
                else if (d is JSONObject) json = d
            }
            val senderId = json.optInt("player_id", json.optString("player_id", "-1").toIntOrNull() ?: -1)
            if (senderId != -1 && myUserId != -1 && senderId == myUserId) return

            val encodedFrom = json.optInt("row", -1)
            val encodedTo = json.optInt("col", -1)
            val promoCode = json.optInt("next_turn", 0)

            if (encodedFrom == -999 && encodedTo == -999) {
                // Opponent Resigned
                handleOpponentResigned()
                return
            }

            if (encodedFrom in 0..77 && encodedTo in 0..77) {
                val fromR = encodedFrom / 10
                val fromC = encodedFrom % 10
                val toR = encodedTo / 10
                val toC = encodedTo % 10

                val promoType = when (promoCode) {
                    1 -> PieceType.QUEEN
                    2 -> PieceType.ROOK
                    3 -> PieceType.BISHOP
                    4 -> PieceType.KNIGHT
                    else -> null
                }

                val legalMoves = logic.getLegalMovesForPiece(fromR, fromC)
                val targetMove = legalMoves.firstOrNull { it.toR == toR && it.toC == toC }

                if (targetMove != null) {
                    val finalMove = targetMove.copy(promotionType = promoType ?: targetMove.promotionType)
                    logic.makeMove(finalMove)
                    binding.chessBoardView.lastMove = finalMove
                    HapticHelper.performHeavyImpact(requireContext())
                    if (finalMove.capturedPiece != null) {
                        SoundHelper.playCaptureSound(requireContext())
                    } else {
                        SoundHelper.playMoveSound(requireContext())
                    }
                    updateBoardUI()
                    checkGameOver()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun sendEmote(emote: String) {
        EmoteHelper.showFloatingEmote(binding.root as ViewGroup, emote, isOpponent = false)
        HapticHelper.performClick(requireContext())

        if (isOnlineMode && roomCode.isNotEmpty()) {
            ApiClient.instance.sendEmote(EmoteRequest(roomCode, myUserId, emote)).enqueue(object : Callback<EmoteResponse> {
                override fun onResponse(call: Call<EmoteResponse>, response: Response<EmoteResponse>) {}
                override fun onFailure(call: Call<EmoteResponse>, t: Throwable) {}
            })
        }
    }

    private fun handlePusherEmote(data: String) {
        try {
            var json = JSONObject(data)
            if (json.has("data")) {
                val d = json.get("data")
                if (d is String) json = JSONObject(d)
                else if (d is JSONObject) json = d
            }
            val senderId = json.optInt("player_id", json.optString("player_id", "-1").toIntOrNull() ?: -1)
            if (senderId != -1 && myUserId != -1 && senderId == myUserId) return

            val emote = json.optString("emote", "👑")
            if (emote.isNotEmpty()) {
                EmoteHelper.showFloatingEmote(binding.root as ViewGroup, emote, isOpponent = true)
                HapticHelper.performClick(requireContext())
            }
        } catch (_: Exception) {}
    }

    private fun updateSelectionUI() {
        binding.chessBoardView.selectedSquare = selectedPiecePos
        binding.chessBoardView.validMoves = currentValidMoves
    }

    private fun updateBoardUI() {
        updateSelectionUI()
        binding.chessBoardView.invalidate()

        // Turn indicator
        val isWhite = (logic.currentTurn == PieceColor.WHITE)
        binding.tvTurnBadge.text = if (isWhite) "⚪ Oqlar navbati" else "⚫ Qoralar navbati"
        binding.tvTurnBadge.setTextColor(if (isWhite) resources.getColor(R.color.white, null) else resources.getColor(R.color.text_secondary, null))

        // Status text
        val isMyTurn = (!isOnlineMode) || (logic.currentTurn == myColor)
        binding.tvGameStatus.text = when {
            logic.isGameOver -> if (logic.winner != null) "🏆 ${if (logic.winner == PieceColor.WHITE) "Oqlar" else "Qoralar"} G'alaba qozondi (MOT)!" else "🤝 Durrang (PAT)!"
            logic.isCheck -> "⚠️ SHOH! ${if (isWhite) "Oqlar" else "Qoralar"} Shohi xavf ostida!"
            isMyTurn -> "Sizning navbatingiz ⚔️"
            else -> "Raqib o'ylamoqda... ⏳"
        }

        // Captured Pieces Display
        val whiteCapturedStr = logic.capturedWhitePieces.joinToString(" ") { it.symbol }
        val blackCapturedStr = logic.capturedBlackPieces.joinToString(" ") { it.symbol }

        if (myColor == PieceColor.WHITE) {
            binding.tvPlayerCaptured.text = blackCapturedStr
            binding.tvOpponentCaptured.text = whiteCapturedStr
        } else {
            binding.tvPlayerCaptured.text = whiteCapturedStr
            binding.tvOpponentCaptured.text = blackCapturedStr
        }
    }

    private fun checkGameOver() {
        if (logic.isGameOver) {
            handleGameOver()
        }
    }

    private fun handleGameOver() {
        val userWon = (logic.winner == myColor)
        val isDraw = logic.isDraw

        if (userWon) {
            ConfettiView.show(binding.root as ViewGroup)
            HapticHelper.performVictory(requireContext())
            SoundHelper.playVictorySound(requireContext())

            val sharedPref = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
            val curWins = sharedPref.getInt("chess_wins", 0) + 1
            val curCoins = sharedPref.getInt("coins", 0) + 100
            val curXp = sharedPref.getInt("xp", 0) + 200
            sharedPref.edit()
                .putInt("chess_wins", curWins)
                .putInt("coins", curCoins)
                .putInt("xp", curXp)
                .apply()
        }

        QuestManager.recordGamePlayed(requireContext(), "chess", isOnlineMode, userWon)

        val title = when {
            isDraw -> "🤝 Durrang (Pat)!"
            userWon -> "🏆 G'ALABA! (Shaxmat Ustasi)"
            else -> "💀 MAG'LUBIYAT (Mot bo'ldingiz)!"
        }
        val msg = when {
            userWon -> "Tabriklaymiz! Raqib shohini mot qildingiz!\n\nMukofot: +100 🪙 | +200 XP ⚡"
            isDraw -> "O'yin durrang bilan yakunlandi!"
            else -> "Afsuski, shohingiz mot qilindi!"
        }

        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMessage(msg)
            .setPositiveButton("QAYTA O'YNASH 🔄") { _, _ ->
                if (isOnlineMode) {
                    startOnlineGame()
                } else {
                    startLocalGame()
                }
            }
            .setNegativeButton("MENU 🏠") { _, _ ->
                showSetupScreen()
            }
            .setCancelable(false)
            .show()
    }

    private fun handleResign() {
        if (isOnlineMode && roomCode.isNotEmpty()) {
            ApiClient.instance.makeMove(MoveRequest(roomCode, myUserId, -999, -999, -1)).enqueue(object : Callback<MoveResponse> {
                override fun onResponse(call: Call<MoveResponse>, response: Response<MoveResponse>) {}
                override fun onFailure(call: Call<MoveResponse>, t: Throwable) {}
            })
        }
        logic.isGameOver = true
        logic.winner = myColor.opposite()
        handleGameOver()
    }

    private fun handleOpponentResigned() {
        logic.isGameOver = true
        logic.winner = myColor
        Toast.makeText(context, "🏳️ Raqibingiz taslim bo'ldi!", Toast.LENGTH_LONG).show()
        handleGameOver()
    }

    private fun handleOpponentLeft() {
        Toast.makeText(context, "🚪 Raqibingiz o'yinni tark etdi!", Toast.LENGTH_LONG).show()
        showSetupScreen()
    }

    private fun showSetupScreen() {
        binding.setupContainer.visibility = View.VISIBLE
        binding.waitingContainer.visibility = View.GONE
        binding.gameplayContainer.visibility = View.GONE
    }

    private fun handleBackNavigation() {
        if (binding.gameplayContainer.visibility == View.VISIBLE) {
            AlertDialog.Builder(requireContext())
                .setTitle("O'yindan chiqish?")
                .setMessage("Chiqsangiz, o'yin yakunlanadi.")
                .setPositiveButton("Ha, Chiqish") { _, _ ->
                    if (isOnlineMode && roomCode.isNotEmpty()) {
                        PusherManager.unsubscribeFromRoom(roomCode)
                    }
                    showSetupScreen()
                }
                .setNegativeButton("Yo'q", null)
                .show()
        } else if (binding.waitingContainer.visibility == View.VISIBLE) {
            if (roomCode.isNotEmpty()) {
                PusherManager.unsubscribeFromRoom(roomCode)
            }
            showSetupScreen()
        } else {
            findNavController().navigateUp()
        }
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        if (roomCode.isNotEmpty()) {
            PusherManager.unsubscribeFromRoom(roomCode)
        }
        _binding = null
    }
}
