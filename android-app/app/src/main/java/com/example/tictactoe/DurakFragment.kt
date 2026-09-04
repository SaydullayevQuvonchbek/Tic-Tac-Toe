package com.example.tictactoe

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.tictactoe.databinding.FragmentDurakBinding
import com.example.tictactoe.network.ApiClient
import com.example.tictactoe.network.CardActionRequest
import com.example.tictactoe.network.CardActionResponse
import com.example.tictactoe.network.CardRefillRequest
import com.example.tictactoe.network.CardRefillResponse
import com.example.tictactoe.network.CardStartRequest
import com.example.tictactoe.network.CardStartResponse
import com.example.tictactoe.network.EmoteRequest
import com.example.tictactoe.network.EmoteResponse
import com.example.tictactoe.network.PusherManager
import com.example.tictactoe.network.RoomCreateRequest
import com.example.tictactoe.network.RoomCreateResponse
import com.example.tictactoe.network.RoomJoinRequest
import com.example.tictactoe.network.RoomJoinResponse
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class DurakFragment : Fragment() {

    private var _binding: FragmentDurakBinding? = null
    private val binding get() = _binding!!

    private val logic = DurakLogic()
    private var isAiMode = false
    private var isOnlineMode = false
    private var roomCode = ""
    private var isHost = false
    private var myPlayerId = -1

    private var selectedCard: Card? = null
    private val handler = Handler(Looper.getMainLooper())

    // ---- Online resilience ----
    // Per-sender action counters. Durak lets the attacker throw several cards in a row, so a single
    // shared counter would collide (both players reuse the same number for different moves). Instead
    // each client numbers its OWN outbound actions; the receiver tracks the last seq it applied from
    // the peer and asks for a full resync the moment it sees a gap.
    private var mySeq = 0     // my own outbound action counter
    private var peerSeq = 0   // last action seq I have applied from the opponent
    private var connectedNow = true
    private var awaitingResync = false
    private val stallHandler = Handler(Looper.getMainLooper())
    private val stallRunnable = Runnable {
        if (_binding != null && isOnlineMode && !logic.isGameOver) {
            binding.tvStallBanner.visibility = View.VISIBLE
        }
    }

    private val gameActions = setOf("attack", "defend", "pass", "take_declared", "finalize_take", "take")

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDurakBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initSetupUI()

        binding.btnSetupBack.setOnClickListener { findNavController().navigateUp() }
        binding.btnGameplayBack.setOnClickListener { handleBackNavigation() }
        binding.btnCancelWaiting.setOnClickListener { cancelWaiting() }
        binding.btnInviteFriend.setOnClickListener {
            ShareInviteHelper.shareRoomCode(requireContext(), "Durak (Karta)", roomCode)
        }
        binding.btnCopyRoomCode.setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Durak Room Code", roomCode)
            clipboard.setPrimaryClip(clip)
            HapticHelper.performClick(requireContext())
            Toast.makeText(context, "Xona kodi nusxalandi: $roomCode", Toast.LENGTH_SHORT).show()
        }
        binding.btnOpenReaction.setOnClickListener {
            ReactionBottomSheetDialog(requireContext()) { emote ->
                sendEmote(emote)
            }.show()
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackNavigation()
            }
        })

        // Single context action button: attacker → BITA/BERDIM, defender → OLISH.
        binding.btnPrimaryAction.setOnClickListener { handlePrimaryAction() }

        binding.tvStallBanner.setOnClickListener {
            binding.tvStallBanner.visibility = View.GONE
            handleOpponentForfeited()
        }
        binding.viewReconnectOverlay.setOnClickListener { /* eat touches while reconnecting */ }

        // Quick matchmaking from arguments if provided
        val isRematch = arguments?.getBoolean("isRematch", false) ?: false
        if (isRematch) {
            isOnlineMode = arguments?.getBoolean("isOnlineMode", false) ?: false
            roomCode = arguments?.getString("roomCode", "") ?: ""
            isHost = arguments?.getBoolean("isHost", false) ?: false
            isAiMode = arguments?.getBoolean("isAiMode", false) ?: false
            startGame()
        }
    }

    private fun initSetupUI() {
        val sharedPref = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
        myPlayerId = sharedPref.getInt("user_id", -1)

        DifficultySelector.bind(
            binding.diffSelector.segDiffEasy,
            binding.diffSelector.segDiffMedium,
            binding.diffSelector.segDiffHard,
            "durak"
        )

        binding.rgMode.setOnCheckedChangeListener { _, checkedId ->
            binding.tilRoomCode.visibility = if (checkedId == R.id.rbJoinRoom) View.VISIBLE else View.GONE
        }

        binding.btnStartGame.setOnClickListener {
            when (binding.rgMode.checkedRadioButtonId) {
                R.id.rbQuickMatch -> {
                    MatchmakingHelper.startQuickMatch(requireContext(), "durak", 36) { code, host, oppName, isBot ->
                        roomCode = code
                        isHost = host
                        isOnlineMode = !isBot
                        isAiMode = isBot
                        binding.tvOpponentName.text = oppName
                        startGame()
                    }
                }
                R.id.rbAi -> {
                    isAiMode = true
                    isOnlineMode = false
                    roomCode = ""
                    binding.tvOpponentName.text = "🤖 AI Bot"
                    startGame()
                }
                R.id.rbCreateRoom -> {
                    createOnlineRoom()
                }
                R.id.rbJoinRoom -> {
                    val code = binding.etRoomCode.text.toString().trim().uppercase()
                    if (code.isNotEmpty()) {
                        joinOnlineRoom(code)
                    } else {
                        Toast.makeText(context, "Iltimos xona kodini kiriting", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        binding.tilRoomCode.setEndIconOnClickListener {
            val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
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
    }

    private fun createOnlineRoom() {
        binding.setupContainer.visibility = View.GONE
        binding.waitingContainer.visibility = View.VISIBLE
        binding.gameplayContainer.visibility = View.GONE

        ApiClient.instance.createRoom(RoomCreateRequest(myPlayerId, 36, false, "durak")).enqueue(object : Callback<RoomCreateResponse> {
            override fun onResponse(call: Call<RoomCreateResponse>, response: Response<RoomCreateResponse>) {
                    if (!isAdded || _binding == null) return
                val code = response.body()?.room_code
                if (response.isSuccessful && !code.isNullOrEmpty()) {
                    roomCode = code
                    isHost = true
                    isOnlineMode = true
                    isAiMode = false
                    binding.tvCreatedRoomCode.text = code
                    updateRoomCodeTiles(code)

                    subscribePusherEvents()
                } else {
                    Toast.makeText(context, "Failed to create room", Toast.LENGTH_SHORT).show()
                    showSetupScreen()
                }
            }

            override fun onFailure(call: Call<RoomCreateResponse>, t: Throwable) {
                    if (!isAdded || _binding == null) return
                Toast.makeText(context, "Network Error: ${t.message}", Toast.LENGTH_SHORT).show()
                showSetupScreen()
            }
        })
    }

    private fun joinOnlineRoom(code: String) {
        val safeContext = context
        val pd = safeContext?.let {
            try {
                android.app.ProgressDialog(it).apply {
                    setMessage("Joining Durak room...")
                    setCancelable(false)
                    show()
                }
            } catch (_: Exception) { null }
        }

        ApiClient.instance.joinRoom(RoomJoinRequest(myPlayerId, code)).enqueue(object : Callback<RoomJoinResponse> {
            override fun onResponse(call: Call<RoomJoinResponse>, response: Response<RoomJoinResponse>) {
                if (!isAdded || _binding == null) return
                try { pd?.dismiss() } catch (_: Exception) {}
                if (response.isSuccessful && response.body()?.status == "success") {
                    roomCode = code
                    isHost = false
                    isOnlineMode = true
                    isAiMode = false
                    showGameplayScreen()
                    subscribePusherEvents()
                    sendCardActionOnline("guest_joined", "", "", seqTagged = false)
                } else {
                    Toast.makeText(context, response.body()?.message ?: "Room not found", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<RoomJoinResponse>, t: Throwable) {
                if (!isAdded || _binding == null) return
                try { pd?.dismiss() } catch (_: Exception) {}
                Toast.makeText(context, "Network Error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun subscribePusherEvents() {
        PusherManager.connect()
        PusherManager.connectionListener = { connected ->
            activity?.runOnUiThread {
                    if (!isAdded || _binding == null) return@runOnUiThread onPusherConnectionChanged(connected) }
        }
        connectedNow = PusherManager.isConnected
        PusherManager.subscribeToRoom(roomCode,
            onGameStarted = { data ->
                activity?.runOnUiThread {
                    if (!isAdded || _binding == null) return@runOnUiThread
                    binding.waitingContainer.visibility = View.GONE
                    if (isHost) {
                        hostBroadcastGameInit()
                    }
                }
            },
            onMoveMade = { eventData ->
                activity?.runOnUiThread {
                    if (!isAdded || _binding == null) return@runOnUiThread
                    handlePusherCardAction(eventData)
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
                        if (json.has("data")) {
                            val d = json.get("data")
                            if (d is String) json = JSONObject(d)
                            else if (d is JSONObject) json = d
                        }
                        val senderId = json.optInt("player_id", json.optString("player_id", "-1").toIntOrNull() ?: -1)
                        if (senderId != -1 && myPlayerId != -1 && senderId == myPlayerId) return@runOnUiThread
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

    private fun hostBroadcastGameInit() {
        resetSeq()
        logic.initNewGame()
        val trump = logic.trumpCard ?: Card(CardSuit.HEARTS, CardRank.ACE)
        val p1Cards = org.json.JSONArray(logic.playerHand.map { it.code })
        val p2Cards = org.json.JSONArray(logic.opponentHand.map { it.code })
        val deckCards = org.json.JSONArray(logic.deck.map { it.code })
        val hostAttacks = logic.isPlayerAttacker

        val payload = JSONObject().apply {
            put("trump", trump.code)
            put("p1", p1Cards)
            put("p2", p2Cards)
            put("deck", deckCards)
            put("host_attacks", hostAttacks)
        }.toString()

        sendCardActionOnline("sync_init", trump.code, payload, seqTagged = false)
        showGameplayScreen()
        renderBoard()
    }

    private fun syncOnlineGameStart(data: String) {
        showGameplayScreen()
        try {
            var json = JSONObject(data)
            if (json.has("data")) {
                val d = json.get("data")
                if (d is String) json = JSONObject(d)
                else if (d is JSONObject) json = d
            }

            if (json.has("trump_card")) {
                val trumpStr = json.getString("trump_card")
                val trump = Card.fromCode(trumpStr)
                val p1Array = json.optJSONArray("player1_cards")
                val p2Array = json.optJSONArray("player2_cards")
                val p1 = if (p1Array != null) (0 until p1Array.length()).map { p1Array.getString(it) } else null
                val p2 = if (p2Array != null) (0 until p2Array.length()).map { p2Array.getString(it) } else null
                val attackerId = json.optInt("attacker_id", -1)

                if (p1 != null && p2 != null) {
                    val myHand = if (isHost) p1 else p2
                    val oppHand = if (isHost) p2 else p1
                    logic.initNewGame(trump, myHand, oppHand)
                    if (attackerId != -1) {
                        logic.isPlayerAttacker = (attackerId == myPlayerId)
                    }
                    renderBoard()
                    return
                }
            }
        } catch (_: Exception) {}

        // Fallback: Fetch directly from server API
        ApiClient.instance.startCardGame(CardStartRequest(roomCode, myPlayerId)).enqueue(object : Callback<CardStartResponse> {
            override fun onResponse(call: Call<CardStartResponse>, response: Response<CardStartResponse>) {
                    if (!isAdded || _binding == null) return
                val body = response.body()
                if (response.isSuccessful && body != null && body.trump_card != null) {
                    val trump = Card.fromCode(body.trump_card)
                    val p1 = if (isHost) body.player1_cards else body.player2_cards
                    val p2 = if (isHost) body.player2_cards else body.player1_cards
                    logic.initNewGame(trump, p1, p2)
                    logic.isPlayerAttacker = (body.attacker_id == myPlayerId)
                    renderBoard()
                }
            }
            override fun onFailure(call: Call<CardStartResponse>, t: Throwable) {
                    if (!isAdded || _binding == null) return
    t.printStackTrace()
    context?.let { android.widget.Toast.makeText(it, "Tarmoq xatosi!", android.widget.Toast.LENGTH_SHORT).show() }
}
        })
    }

    private fun resetSeq() {
        mySeq = 0
        peerSeq = 0
        awaitingResync = false
    }

    private fun startGame() {
        selectedCard = null
        selectedTargetAttackCard = null
        resetSeq()

        if (isOnlineMode) {
            subscribePusherEvents()
            showGameplayScreen()
            if (isHost) {
                hostBroadcastGameInit()
            } else {
                sendCardActionOnline("guest_joined", "", "", seqTagged = false)
                handler.postDelayed({
                    if (_binding != null && isOnlineMode && !isHost && logic.deck.isEmpty() && logic.playerHand.isEmpty()) {
                        sendCardActionOnline("guest_joined", "", "", seqTagged = false)
                    }
                }, 1500L)
            }
            return
        }

        logic.initNewGame()
        showGameplayScreen()
        renderBoard()

        if (isAiMode && !logic.isPlayerAttacker) {
            triggerBotAction()
        }
    }

    private fun sendEmote(emote: String) {
        val sharedPref = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
        val myUsername = sharedPref.getString("username", "Siz") ?: "Siz"
        EmoteHelper.showFloatingEmote(binding.root as ViewGroup, emote, isOpponent = false, senderName = myUsername)
        if (isOnlineMode && roomCode.isNotEmpty()) {
            ApiClient.instance.sendEmote(EmoteRequest(roomCode, myPlayerId, emote)).enqueue(object : Callback<EmoteResponse> {
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

    private fun initialsOf(name: String): String {
        val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return when {
            parts.size >= 2 -> (parts[0].take(1) + parts[1].take(1)).uppercase()
            parts.size == 1 -> parts[0].take(2).uppercase()
            else -> "?"
        }
    }

    private fun renderBoard() {
        if (_binding == null) return

        // 0. Game & Room Headers
        binding.tvRoomCodeHeader.text = if (isOnlineMode) "DURAK · ONLAYN" else if (isAiMode) "DURAK · BOT" else "DURAK · 2 O'YINCHI"
        binding.tvRoomSubHeader.text = if (isOnlineMode) "XONA #$roomCode" else if (isAiMode) "BOT BLITZ" else "OFFLINE"

        // 1. Prominent ZOD (Trump) Badge & Deck Pile (Design 3a)
        val suit = logic.trumpSuit
        binding.tvTrumpHeaderSuit.text = suit.symbol
        binding.tvTrumpHeaderSuit.setTextColor(suit.colorInt)

        binding.viewTrumpCard.card = logic.trumpCard
        binding.viewTrumpCard.isFaceDown = false
        binding.viewTrumpCard.isTrump = true

        binding.viewDeckPile.isFaceDown = true
        binding.viewDeckPile.visibility = if (logic.deck.size > 1) View.VISIBLE else View.INVISIBLE
        binding.viewTrumpCard.visibility = if (logic.deck.isNotEmpty()) View.VISIBLE else View.INVISIBLE
        binding.tvDeckCount.text = "${logic.deck.size}"

        // 2. Opponent Count & Initials Avatar & Fan Backs (Design 3a)
        val oppName = binding.tvOpponentName.text.toString()
        binding.tvOpponentAvatar.text = initialsOf(oppName)
        binding.tvOpponentCardsCount.text = "${logic.opponentHand.size} karta"
        binding.layoutOpponentCards.removeAllViews()

        val oppCount = logic.opponentHand.size.coerceAtMost(8)
        val fanAngles = listOf(-10f, -7f, -4f, -1f, 2f, 5f, 8f, 11f)
        for (i in 0 until oppCount) {
            val angle = fanAngles.getOrElse(i) { 0f }
            val miniCard = DurakCardView(requireContext()).apply {
                isFaceDown = true
                rotation = angle
                layoutParams = LinearLayout.LayoutParams(dp(44), dp(62)).apply {
                    setMargins(if (i == 0) 0 else dp(-16), 0, 0, 0)
                }
            }
            binding.layoutOpponentCards.addView(miniCard)
        }

        // 3. Table Pairs
        renderTablePairs()

        // 4. Player Hand
        renderPlayerHand()

        // 5. Status Text & Action Buttons
        updateStatusAndButtons()
    }

    private fun dp(dpVal: Int): Int {
        val density = resources.displayMetrics.density
        return (dpVal * density).toInt()
    }

    private var selectedTargetAttackCard: Card? = null

    private fun renderTablePairs() {
        binding.layoutTablePairs.removeAllViews()
        val count = logic.tablePairs.size

        // Dynamic Pair and Card scaling based on number of active battle pairs (DP based)
        val pairW = when {
            count <= 2 -> dp(98)
            count == 3 -> dp(86)
            count == 4 -> dp(76)
            else -> dp(68)
        }
        val pairH = when {
            count <= 2 -> dp(142)
            count == 3 -> dp(126)
            count == 4 -> dp(112)
            else -> dp(100)
        }
        val cardW = when {
            count <= 2 -> dp(86)
            count == 3 -> dp(76)
            count == 4 -> dp(66)
            else -> dp(58)
        }
        val cardH = when {
            count <= 2 -> dp(126)
            count == 3 -> dp(112)
            count == 4 -> dp(98)
            else -> dp(88)
        }

        for (pair in logic.tablePairs) {
            val pairContainer = FrameLayout(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(pairW, pairH).apply {
                    setMargins(dp(4), 0, dp(4), 0)
                }
                setOnClickListener {
                    if (pair.defendCard != null || logic.isPlayerAttacker) return@setOnClickListener
                    val chosen = selectedCard
                    if (chosen != null && logic.canBeat(pair.attackCard, chosen)) {
                        selectedCard = null
                        selectedTargetAttackCard = null
                        handlePlayerDefend(chosen, pair.attackCard)
                    } else {
                        selectedTargetAttackCard = if (selectedTargetAttackCard == pair.attackCard) null else pair.attackCard
                        renderTablePairs()
                        updateStatusAndButtons()
                    }
                }
            }

            val isTargeted = (selectedTargetAttackCard == pair.attackCard)

            // Attack Card (Bottom)
            val attackView = DurakCardView(requireContext()).apply {
                card = pair.attackCard
                isFaceDown = false
                isSelectedCard = isTargeted
                layoutParams = FrameLayout.LayoutParams(cardW, cardH)
            }
            pairContainer.addView(attackView)

            // Defend Card (Top with offset & rotation)
            if (pair.defendCard != null) {
                val defendView = DurakCardView(requireContext()).apply {
                    card = pair.defendCard
                    isFaceDown = false
                    rotation = 12f
                    translationX = dp(10).toFloat()
                    translationY = dp(10).toFloat()
                    layoutParams = FrameLayout.LayoutParams(cardW, cardH)
                }
                pairContainer.addView(defendView)
            }

            if (pair.defendCard == null && !logic.isPlayerAttacker && !logic.isGameOver) {
                val tvUrish = TextView(requireContext()).apply {
                    text = "URISH KERAK"
                    textSize = 8.5f
                    setTextColor(Color.parseColor("#FDE047"))
                    typeface = Typeface.DEFAULT_BOLD
                    letterSpacing = 0.08f
                    gravity = Gravity.CENTER
                    layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                        gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                    }
                }
                pairContainer.addView(tvUrish)
            }

            binding.layoutTablePairs.addView(pairContainer)
        }

        // Auto-scroll to the newest thrown attack pair
        binding.scrollTablePairs.post {
            binding.scrollTablePairs.fullScroll(View.FOCUS_RIGHT)
        }
    }

    private var draggingCard: DurakCardView? = null
    private var draggingModel: Card? = null
    private var dragIndex: Int = -1
    private var downRawX = 0f
    private var downRawY = 0f
    private var didDrag = false

    private fun renderPlayerHand() {
        if (_binding == null) return
        binding.layoutPlayerCards.removeAllViews()
        val count = logic.playerHand.size
        if (count == 0) return

        val screenWidthPx = resources.displayMetrics.widthPixels
        val usableWidthPx = (screenWidthPx - dp(20)).coerceAtLeast(dp(280))

        val cardW = dp(60)
        val cardH = dp(88)

        val overlapMarginPx = if (count > 1) {
            val step = (usableWidthPx - cardW) / (count - 1)
            (step - cardW).coerceAtMost(dp(4))
        } else 0

        val maxAngle = 13f.coerceAtMost(count * 2.8f)
        val angleStep = if (count > 1) (maxAngle * 2) / (count - 1) else 0f

        for ((index, card) in logic.playerHand.withIndex()) {
            val isSelected = (selectedCard == card)
            val angle = if (count > 1) -maxAngle + (index * angleStep) else 0f

            val cardView = DurakCardView(requireContext()).apply {
                this.card = card
                this.isFaceDown = false
                this.isSelectedCard = isSelected
                this.isTrump = (card.suit == logic.trumpSuit)
                this.rotation = angle
                layoutParams = LinearLayout.LayoutParams(cardW, cardH).apply {
                    setMargins(if (index == 0) 0 else overlapMarginPx, 0, 0, 0)
                }
                if (isSelected) {
                    translationY = -dp(22).toFloat()
                    elevation = dp(8).toFloat()
                }
            }
            binding.layoutPlayerCards.addView(cardView)
        }

        attachHandTouchListener()
    }

    /** Single robust touch listener for tap-to-select, drag-to-reorder, and drag-to-play. */
    private fun attachHandTouchListener() {
        val slop = android.view.ViewConfiguration.get(requireContext()).scaledTouchSlop

        binding.scrollPlayerHand.setOnTouchListener { host, event ->
            val cards = binding.layoutPlayerCards
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    val hit = cardAt(cards, event.x - cards.left, event.y - cards.top)
                    if (hit == null) {
                        draggingCard = null
                        draggingModel = null
                        dragIndex = -1
                        false
                    } else {
                        val (idx, view) = hit
                        dragIndex = idx
                        draggingCard = view
                        draggingModel = view.card
                        downRawX = event.rawX
                        downRawY = event.rawY
                        didDrag = false
                        view.elevation = dp(16).toFloat()
                        view.rotation = 0f
                        host.parent?.requestDisallowInterceptTouchEvent(true)
                        true
                    }
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val view = draggingCard ?: return@setOnTouchListener false
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!didDrag && kotlin.math.hypot(dx, dy) > slop) didDrag = true
                    if (didDrag) {
                        view.translationX = dx
                        view.translationY = dy

                        // If dragging horizontally within the hand, handle reordering!
                        if (dy > -dp(35).toFloat() && logic.playerHand.size > 1 && dragIndex != -1) {
                            val count = logic.playerHand.size
                            val cardsLoc = IntArray(2)
                            cards.getLocationOnScreen(cardsLoc)
                            val relativeX = event.rawX - cardsLoc[0]
                            val step = if (count > 0) (cards.width.toFloat() / count.toFloat()).coerceAtLeast(1f) else 1f
                            val targetIdx = (relativeX / step).toInt().coerceIn(0, count - 1)

                            if (targetIdx != dragIndex && targetIdx in logic.playerHand.indices) {
                                logic.movePlayerCard(dragIndex, targetIdx)
                                dragIndex = targetIdx
                                HapticHelper.performClick(requireContext())
                                downRawX = event.rawX
                                view.translationX = 0f
                                renderPlayerHand()
                                val newView = cards.getChildAt(dragIndex) as? DurakCardView
                                if (newView != null) {
                                    draggingCard = newView
                                    newView.elevation = dp(16).toFloat()
                                    newView.rotation = 0f
                                }
                            }
                        }
                    }
                    true
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    host.parent?.requestDisallowInterceptTouchEvent(false)
                    val view = draggingCard
                    val model = draggingModel
                    val totalDy = event.rawY - downRawY
                    val rawX = event.rawX
                    draggingCard = null
                    draggingModel = null
                    dragIndex = -1
                    view?.translationX = 0f
                    view?.translationY = 0f

                    if (view != null && model != null && event.actionMasked == android.view.MotionEvent.ACTION_UP) {
                        val playedByDrag = didDrag && totalDy < -dp(45).toFloat()
                        if (playedByDrag) {
                            host.post { onHandCardGesture(model, playedByDrag = true, rawX = rawX) }
                        } else if (didDrag) {
                            // Finished horizontal reorder
                            HapticHelper.performClick(requireContext())
                            renderPlayerHand()
                        } else {
                            // Simple tap
                            host.post { onHandCardGesture(model, playedByDrag = false, rawX = rawX) }
                        }
                    } else {
                        renderPlayerHand()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun onHandCardGesture(card: Card, playedByDrag: Boolean, rawX: Float) {
        if (_binding == null || logic.isGameOver) return
        val play = playedByDrag || selectedCard == card
        if (play) {
            if (logic.isPlayerAttacker) {
                handlePlayerAttack(card)
            } else {
                var target: Card? = null
                for (i in 0 until binding.layoutTablePairs.childCount) {
                    val child = binding.layoutTablePairs.getChildAt(i)
                    val loc = IntArray(2)
                    child.getLocationOnScreen(loc)
                    if (rawX in (loc[0] - dp(20)).toFloat()..(loc[0] + child.width + dp(20)).toFloat()) {
                        val p = logic.tablePairs.getOrNull(i)
                        if (p != null && p.defendCard == null && logic.canBeat(p.attackCard, card)) {
                            target = p.attackCard
                            break
                        }
                    }
                }
                if (target == null) {
                    val cur = selectedTargetAttackCard
                    if (cur != null && logic.canBeat(cur, card)) target = cur
                    else target = logic.tablePairs.firstOrNull { it.defendCard == null && logic.canBeat(it.attackCard, card) }?.attackCard
                }
                handlePlayerDefend(card, target)
            }
        } else {
            HapticHelper.performClick(requireContext())
            SoundHelper.playMoveSound(requireContext())
            selectedCard = card
            renderPlayerHand()
            updateStatusAndButtons()
        }
    }

    /** Top-most [DurakCardView] under (x,y) given in [container]'s coordinate space. */
    private fun cardAt(container: android.view.ViewGroup, x: Float, y: Float): Pair<Int, DurakCardView>? {
        val inv = android.graphics.Matrix()
        for (i in container.childCount - 1 downTo 0) {
            val child = container.getChildAt(i) as? DurakCardView ?: continue
            val pt = floatArrayOf(x - child.left, y - child.top)
            if (!child.matrix.isIdentity) {
                if (!child.matrix.invert(inv)) continue
                inv.mapPoints(pt)
            }
            if (pt[0] in 0f..child.width.toFloat() && pt[1] in 0f..child.height.toFloat()) return i to child
        }
        return null
    }

    private fun updateRoomCodeTiles(code: String) {
        val padded = code.padEnd(4, ' ')
        binding.tvTile1.text = padded.getOrNull(0)?.toString() ?: ""
        binding.tvTile2.text = padded.getOrNull(1)?.toString() ?: ""
        binding.tvTile3.text = padded.getOrNull(2)?.toString() ?: ""
        binding.tvTile4.text = padded.getOrNull(3)?.toString() ?: ""
    }

    /** Called by the one context button. Attacker → BITA / BERDIM, defender → OLISH. */
    private fun handlePrimaryAction() {
        if (logic.isGameOver) return
        if (logic.isPlayerAttacker) handlePlayerPassBita() else handlePlayerTake()
    }

    private fun updateStatusAndButtons() {
        if (_binding == null) return
        val isAttacker = logic.isPlayerAttacker
        val unbeatCount = logic.tablePairs.count { it.defendCard == null }
        val btn = binding.btnPrimaryAction

        fun show(text: String, bg: Int) {
            btn.visibility = View.VISIBLE
            btn.text = text
            btn.isEnabled = true
            btn.alpha = 1f
            btn.setBackgroundResource(bg)
        }
        fun hide() {
            btn.visibility = View.INVISIBLE
        }

        if (logic.isGameOver) {
            binding.tvGameStatus.text = if (logic.winner == 1) "🏆 G'ALABA! (Durak emassiz)" else if (logic.winner == 0) "🤝 DURRANG!" else "💀 RAQIB YUTDI!"
            hide()
            return
        }

        when {
            logic.isDefenderTaking && isAttacker -> {
                binding.tvGameStatus.text = "RAQIB KO'TARMOQDA — KARTA QO'SHISHINGIZ MUMKIN"
                show("✅ BERDIM", R.drawable.bg_durak_btn_pass)
            }
            logic.isDefenderTaking && !isAttacker -> {
                binding.tvGameStatus.text = "KO'TARDINGIZ — KUTING…"
                hide()
            }
            isAttacker && logic.tablePairs.isEmpty() -> {
                binding.tvGameStatus.text = "SIZ HUJUMDASIZ — KARTANI YUQORIGA SURING"
                hide()
            }
            isAttacker && unbeatCount > 0 -> {
                binding.tvGameStatus.text = "RAQIB HIMOYALANMOQDA…"
                hide()
            }
            isAttacker -> {
                binding.tvGameStatus.text = "HAMMASI URILDI"
                show("✋ BITA", R.drawable.bg_durak_btn_pass)
            }
            !isAttacker && unbeatCount > 0 -> {
                binding.tvGameStatus.text = "SIZ HIMOYADASIZ — KARTANI TANLANG"
                show("📥 OLISH", R.drawable.bg_durak_btn_take)
            }
            else -> {
                binding.tvGameStatus.text = "RAQIB HUJUM QILMOQDA…"
                hide()
            }
        }
    }

    private fun handlePlayerAttack(card: Card) {
        if (!logic.canAttackWith(card, true)) {
            binding.tvGameStatus.text = "❌ Bu karta bilan hozir hujum qilib bo'lmaydi!"
            HapticHelper.performClick(requireContext())
            return
        }

        HapticHelper.performClick(requireContext())
        SoundHelper.playMoveSound(requireContext())

        logic.attack(card, true)
        selectedCard = null
        selectedTargetAttackCard = null
        renderBoard()

        if (isOnlineMode) {
            sendCardActionOnline("attack", card.code, null)
        } else if (isAiMode) {
            if (!logic.isDefenderTaking) {
                triggerBotAction(700L)
            }
        }

        checkGameOver()
    }

    private fun handlePlayerDefend(card: Card, explicitTarget: Card? = null) {
        val target = explicitTarget ?: selectedTargetAttackCard ?: logic.tablePairs.firstOrNull { it.defendCard == null && logic.canBeat(it.attackCard, card) }?.attackCard
        if (target == null || !logic.canBeat(target, card)) {
            binding.tvGameStatus.text = "❌ Bu karta stoldagi kartani ura olmaydi!"
            HapticHelper.performClick(requireContext())
            return
        }

        HapticHelper.performHeavyImpact(requireContext())
        SoundHelper.playCaptureSound(requireContext())

        logic.defend(target, card, true)
        selectedCard = null
        selectedTargetAttackCard = null
        renderBoard()

        if (isOnlineMode) {
            sendCardActionOnline("defend", card.code, target.code)
        } else if (isAiMode) {
            triggerBotAction(700L)
        }

        checkGameOver()
    }

    private fun handlePlayerPassBita() {
        HapticHelper.performClick(requireContext())
        SoundHelper.playMoveSound(requireContext())

        if (logic.isDefenderTaking) {
            // Human attacker finished adding cards -> Bot takes all table cards!
            logic.finalizeTakeByDefender(isDefenderPlayer = false)
            selectedCard = null
            selectedTargetAttackCard = null
            renderBoard()

            if (isOnlineMode) {
                sendCardActionOnline("finalize_take", null, null)
                if (isHost) broadcastFullState()
            }
            // In Bot mode: Human remains attacker, human throws next card. Bot doesn't act yet.
            checkGameOver()
            return
        }

        // Standard Bita: Table cleared, turn swaps
        logic.clearTableToBita()
        selectedCard = null
        selectedTargetAttackCard = null
        renderBoard()

        if (isOnlineMode) {
            sendCardActionOnline("pass", null, null)
            if (isHost) broadcastFullState()
        } else if (isAiMode) {
            // If it's now Bot's turn to attack, trigger bot action
            if (!logic.isPlayerAttacker && !logic.isGameOver) {
                triggerBotAction(800L)
            }
        }

        checkGameOver()
    }

    private fun handlePlayerTake() {
        if (logic.tablePairs.isEmpty()) return

        HapticHelper.performHeavyImpact(requireContext())
        SoundHelper.playCaptureSound(requireContext())

        logic.declareTakeByDefender()
        selectedCard = null
        selectedTargetAttackCard = null
        renderBoard()

        if (isOnlineMode) {
            sendCardActionOnline("take_declared", null, null)
        } else if (isAiMode) {
            // Bot is attacker: Trigger bot to dump extra cards or finalize take
            triggerBotAction(650L)
        }
    }

    private fun triggerBotAction(delayMs: Long = 750L) {
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            if (!isAdded || logic.isGameOver) return@postDelayed

            val isBotAttacker = !logic.isPlayerAttacker
            val difficulty = DifficultyStore.get(requireContext(), "durak")
            val decision = DurakAI.getBestAction(logic, isBotAttacker = isBotAttacker, difficulty = difficulty)

            when (decision.type) {
                DurakAI.ActionType.ATTACK -> {
                    decision.card?.let {
                        logic.attack(it, false)
                        HapticHelper.performClick(requireContext())
                        SoundHelper.playMoveSound(requireContext())
                    }
                    renderBoard()
                    checkGameOver()

                    // If human is taking, bot should continue dumping or finalize
                    if (logic.isDefenderTaking && isBotAttacker && !logic.isGameOver) {
                        triggerBotAction(650L)
                    }
                }
                DurakAI.ActionType.DEFEND -> {
                    if (decision.card != null && decision.targetAttackCard != null) {
                        logic.defend(decision.targetAttackCard, decision.card, false)
                        HapticHelper.performHeavyImpact(requireContext())
                        SoundHelper.playCaptureSound(requireContext())
                    }
                    renderBoard()
                    checkGameOver()
                }
                DurakAI.ActionType.PASS -> {
                    if (logic.isDefenderTaking) {
                        // Human is taking cards -> Bot finishes dumping and finalizes take
                        if (!logic.isPlayerAttacker) {
                            logic.finalizeTakeByDefender(isDefenderPlayer = true)
                            renderBoard()
                            checkGameOver()

                            // Bot remains attacker -> Trigger Bot's next attack round!
                            if (!logic.isGameOver && !logic.isPlayerAttacker) {
                                triggerBotAction(800L)
                            }
                        }
                    } else {
                        // Table beaten -> Bita!
                        logic.clearTableToBita()
                        renderBoard()
                        checkGameOver()

                        // If turn swapped to bot, trigger bot's attack!
                        if (!logic.isGameOver && !logic.isPlayerAttacker) {
                            triggerBotAction(800L)
                        }
                    }
                    HapticHelper.performClick(requireContext())
                }
                DurakAI.ActionType.TAKE -> {
                    logic.declareTakeByDefender()
                    HapticHelper.performHeavyImpact(requireContext())
                    renderBoard()
                    checkGameOver()
                }
            }
        }, delayMs)
    }

    /** Sends a game action tagged with a game-level sequence number, retrying transient failures. */
    private fun sendCardActionOnline(action: String, card: String?, targetCard: String?, seqTagged: Boolean = true) {
        val taggedAction = if (seqTagged) {
            mySeq += 1
            "$action|$mySeq"
        } else action
        postCardAction(taggedAction, card, targetCard, attempt = 0)
        scheduleStallCheck()
    }

    private fun postCardAction(action: String, card: String?, targetCard: String?, attempt: Int) {
        ApiClient.instance.sendCardAction(CardActionRequest(roomCode, myPlayerId, action, card, targetCard))
            .enqueue(object : Callback<CardActionResponse> {
                override fun onResponse(call: Call<CardActionResponse>, response: Response<CardActionResponse>) {
                    if (!isAdded || _binding == null) return
                    if (!response.isSuccessful && attempt < 2) retry(action, card, targetCard, attempt)
                }
                override fun onFailure(call: Call<CardActionResponse>, t: Throwable) {
                    if (!isAdded || _binding == null) return
                    if (attempt < 2) retry(action, card, targetCard, attempt)
                }
            })
    }

    private fun retry(action: String, card: String?, targetCard: String?, attempt: Int) {
        handler.postDelayed({
            if (_binding != null && isOnlineMode) postCardAction(action, card, targetCard, attempt + 1)
        }, 900L * (attempt + 1))
    }

    private fun scheduleStallCheck() {
        if (!isOnlineMode) return
        stallHandler.removeCallbacks(stallRunnable)
        _binding?.tvStallBanner?.visibility = View.GONE
        stallHandler.postDelayed(stallRunnable, 42_000L)
    }

    private fun cancelStallCheck() {
        stallHandler.removeCallbacks(stallRunnable)
        _binding?.tvStallBanner?.visibility = View.GONE
    }

    private fun onPusherConnectionChanged(connected: Boolean) {
        if (_binding == null) return
        val wasConnected = connectedNow
        connectedNow = connected
        val inGame = binding.gameplayContainer.visibility == View.VISIBLE && isOnlineMode
        binding.viewReconnectOverlay.visibility = if (!connected && inGame) View.VISIBLE else View.GONE
        if (connected && !wasConnected && inGame && !logic.isGameOver) {
            // Reconnected mid-game — pull the authoritative state.
            requestResync()
        }
    }

    private fun requestResync() {
        if (!isOnlineMode) return
        if (isHost) {
            broadcastFullState()
        } else {
            awaitingResync = true
            postCardAction("resync_req", null, null, attempt = 0)
        }
    }

    /** Host only: serialise the full authoritative game state and broadcast it. */
    private fun broadcastFullState() {
        if (!isHost) return
        val trump = logic.trumpCard ?: return
        val hostHand = logic.playerHand   // host's own hand
        val guestHand = logic.opponentHand
        val tableArr = org.json.JSONArray()
        for (p in logic.tablePairs) {
            tableArr.put(org.json.JSONObject().apply {
                put("a", p.attackCard.code)
                p.defendCard?.let { put("d", it.code) }
            })
        }
        val payload = JSONObject().apply {
            put("trump", trump.code)
            put("host", org.json.JSONArray(hostHand.map { it.code }))
            put("guest", org.json.JSONArray(guestHand.map { it.code }))
            put("deck", org.json.JSONArray(logic.deck.map { it.code }))
            put("table", tableArr)
            put("hostAttacks", logic.isPlayerAttacker)
            put("taking", logic.isDefenderTaking)
            put("hostOut", mySeq)     // host's own outbound counter
            put("guestOut", peerSeq)  // last guest action the host applied
        }.toString()
        postCardAction("sync_state", null, payload, attempt = 0)
    }

    private fun applyFullState(payloadJson: String) {
        try {
            val o = JSONObject(payloadJson)
            val trump = Card.fromCode(o.getString("trump"))
            fun arr(k: String) = o.getJSONArray(k).let { a -> (0 until a.length()).map { a.getString(it) } }
            val hostCards = arr("host")
            val guestCards = arr("guest")
            val deckCards = arr("deck")
            val tArr = o.getJSONArray("table")
            val table = (0 until tArr.length()).map {
                val e = tArr.getJSONObject(it)
                e.getString("a") to (if (e.has("d")) e.getString("d") else null)
            }
            val hostAttacks = o.getBoolean("hostAttacks")
            val taking = o.optBoolean("taking", false)
            val hostOut = o.optInt("hostOut", 0)
            val guestOut = o.optInt("guestOut", 0)

            val myCards = if (isHost) hostCards else guestCards
            val oppCards = if (isHost) guestCards else hostCards
            logic.loadFullState(
                trump, myCards, oppCards, deckCards, table,
                playerIsAttacker = if (isHost) hostAttacks else !hostAttacks,
                defenderTaking = taking
            )
            // Re-anchor both counters to the host's authoritative view. Any of my own moves the host
            // never applied are rolled back by loadFullState, so my outbound counter rewinds too.
            if (isHost) {
                mySeq = hostOut; peerSeq = guestOut
            } else {
                mySeq = guestOut; peerSeq = hostOut
            }
            awaitingResync = false
            selectedCard = null
            selectedTargetAttackCard = null
            binding.viewReconnectOverlay.visibility = View.GONE
            renderBoard()
            checkGameOver()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun handlePusherCardAction(eventData: String) {
        try {
            var json = JSONObject(eventData)
            if (json.has("data")) {
                val d = json.get("data")
                if (d is String) json = JSONObject(d)
                else if (d is JSONObject) json = d
            }
            val senderId = json.optInt("player_id", json.optString("player_id", "-1").toIntOrNull() ?: -1)
            if (senderId != -1 && myPlayerId != -1 && senderId == myPlayerId) return

            // A genuine message from the opponent -> they are alive; clear any pending stall banner.
            cancelStallCheck()

            val rawAction = json.optString("action", "")
            val action = rawAction.substringBefore('|')
            val incomingSeq = rawAction.substringAfter('|', "").toIntOrNull() ?: -1
            val cardCode = json.optString("card", "")
            val targetCardCode = json.optString("target_card", "")

            // Control messages bypass the sequence gate.
            when (action) {
                "resync_req" -> { if (isHost) broadcastFullState(); return }
                "sync_state" -> {
                    val raw = if (targetCardCode.startsWith("{")) targetCardCode else if (cardCode.startsWith("{")) cardCode else targetCardCode
                    applyFullState(raw)
                    return
                }
            }

            // Game actions: enforce per-sender ordering, request a resync if we missed one.
            if (action in gameActions && incomingSeq >= 0) {
                if (incomingSeq <= peerSeq) return // duplicate / already applied
                if (incomingSeq > peerSeq + 1) {   // gap -> a move from the peer was lost
                    if (!awaitingResync) requestResync()
                    return
                }
                peerSeq = incomingSeq
            }

            when (action) {
                "guest_joined" -> {
                    if (isHost) {
                        if (mySeq > 0 || peerSeq > 0 || logic.tablePairs.isNotEmpty() || (logic.deck.isNotEmpty() && logic.deck.size < 24)) {
                            broadcastFullState()
                        } else {
                            hostBroadcastGameInit()
                        }
                    }
                }
                "sync_init" -> {
                    if (!isHost) {
                        try {
                            val rawPayload = when {
                                targetCardCode.startsWith("{") -> targetCardCode
                                cardCode.startsWith("{") -> cardCode
                                else -> targetCardCode
                            }
                            if (rawPayload.isNotEmpty()) {
                                val initObj = JSONObject(rawPayload)
                                val trump = Card.fromCode(initObj.getString("trump"))
                                val p1Arr = initObj.getJSONArray("p1")
                                val p2Arr = initObj.getJSONArray("p2")
                                val deckArr = initObj.getJSONArray("deck")
                                val hostAttacks = initObj.getBoolean("host_attacks")

                                val myCards = (0 until p2Arr.length()).map { p2Arr.getString(it) }
                                val oppCards = (0 until p1Arr.length()).map { p1Arr.getString(it) }
                                val deckList = (0 until deckArr.length()).map { deckArr.getString(it) }

                                resetSeq()
                                logic.initNewGameWithSyncedDeck(trump, myCards, oppCards, deckList)
                                logic.isPlayerAttacker = !hostAttacks
                                showGameplayScreen()
                                renderBoard()
                                return
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
                "attack" -> {
                    if (cardCode.isNotEmpty()) {
                        val card = Card.fromCode(cardCode)
                        logic.attack(card, isPlayer = false)
                        HapticHelper.performClick(requireContext())
                        SoundHelper.playMoveSound(requireContext())
                    }
                }
                "defend" -> {
                    if (cardCode.isNotEmpty() && targetCardCode.isNotEmpty()) {
                        val card = Card.fromCode(cardCode)
                        val target = Card.fromCode(targetCardCode)
                        logic.defend(target, card, isPlayer = false)
                        HapticHelper.performHeavyImpact(requireContext())
                        SoundHelper.playCaptureSound(requireContext())
                    }
                }
                "pass" -> {
                    logic.clearTableToBita()
                    HapticHelper.performClick(requireContext())
                    SoundHelper.playMoveSound(requireContext())
                    if (isHost) broadcastFullState()
                }
                "take_declared" -> {
                    logic.declareTakeByDefender()
                    HapticHelper.performHeavyImpact(requireContext())
                    SoundHelper.playCaptureSound(requireContext())
                }
                "finalize_take" -> {
                    logic.finalizeTakeByDefender(isDefenderPlayer = true)
                    HapticHelper.performHeavyImpact(requireContext())
                    SoundHelper.playCaptureSound(requireContext())
                    if (isHost) broadcastFullState()
                }
                "take" -> {
                    logic.finalizeTakeByDefender(isDefenderPlayer = true)
                    HapticHelper.performHeavyImpact(requireContext())
                    SoundHelper.playCaptureSound(requireContext())
                    if (isHost) broadcastFullState()
                }
            }

            renderBoard()
            checkGameOver()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun checkGameOver() {
        logic.checkGameOver()
        if (logic.isGameOver) {
            handler.removeCallbacksAndMessages(null)
            renderBoard()
            handleGameOver()
        }
    }

    private fun handleGameOver() {
        val isUserWin = (logic.winner == 1)
        val isDraw = (logic.winner == 0)

        if (isUserWin) {
            ConfettiView.show(binding.root as ViewGroup)
            HapticHelper.performVictory(requireContext())
            SoundHelper.playVictorySound(requireContext())

            val sharedPref = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
            val curWins = sharedPref.getInt("durak_wins", 0) + 1
            val curCoins = sharedPref.getInt("coins", 0) + 75
            val curXp = sharedPref.getInt("xp", 0) + 150
            sharedPref.edit()
                .putInt("durak_wins", curWins)
                .putInt("coins", curCoins)
                .putInt("xp", curXp)
                .apply()
        }

        QuestManager.recordGamePlayed(requireContext(), "durak", isOnlineMode, isUserWin)

        AlertDialog.Builder(requireContext())
            .setTitle(if (isDraw) "🤝 Durrang!" else if (isUserWin) "🏆 G'ALABA! (Durak emassiz)" else "💀 DURAK BO'LDINGIZ!")
            .setMessage(if (isUserWin) "Tabriklaymiz! Barcha kartalardan qutulib yutdingiz!\n\nMukofot: +75 🪙 | +150 XP ⚡" else "Afsuski, qo'lingizda karta qolib ketdi!")
            .setPositiveButton("QAYTA O'YNASH 🔄") { _, _ ->
                startGame()
            }
            .setNegativeButton("MENU 🏠") { _, _ ->
                showSetupScreen()
            }
            .setCancelable(false)
            .show()
    }

    private fun handleBackNavigation() {
        if (binding.gameplayContainer.visibility == View.VISIBLE) {
            AlertDialog.Builder(requireContext())
                .setTitle("O'yindan chiqish?")
                .setMessage(if (isOnlineMode) "Chiqib ketsangiz, durak hisoblanasiz." else "O'yinni yakunlaysizmi?")
                .setPositiveButton("Ha, Chiqish") { _, _ ->
                    if (isOnlineMode && roomCode.isNotEmpty()) {
                        PusherManager.unsubscribeFromRoom(roomCode)
                    }
                    showSetupScreen()
                }
                .setNegativeButton("Yo'q", null)
                .show()
        } else if (binding.waitingContainer.visibility == View.VISIBLE) {
            cancelWaiting()
        } else {
            findNavController().navigateUp()
        }
    }

    private fun cancelWaiting() {
        if (roomCode.isNotEmpty()) {
            PusherManager.unsubscribeFromRoom(roomCode)
        }
        showSetupScreen()
    }

    private fun handleOpponentForfeited() {
        val sharedPref = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
        val curWins = sharedPref.getInt("durak_wins", 0) + 1
        val curCoins = sharedPref.getInt("coins", 0) + 75
        val curXp = sharedPref.getInt("xp", 0) + 150
        sharedPref.edit()
            .putInt("durak_wins", curWins)
            .putInt("coins", curCoins)
            .putInt("xp", curXp)
            .apply()

        QuestManager.recordGamePlayed(requireContext(), "durak", isOnline = true, isWin = true)

        AlertDialog.Builder(requireContext())
            .setTitle("🏆 Raqib chiqib ketdi!")
            .setMessage("Raqibingiz o'yindan chiqib ketdi va sizga g'alaba yozildi!\n\nMukofot: +75 🪙 | +150 XP ⚡")
            .setPositiveButton("OK") { _, _ -> showSetupScreen() }
            .setCancelable(false)
            .show()
    }

    private fun showSetupScreen() {
        binding.setupContainer.visibility = View.VISIBLE
        binding.waitingContainer.visibility = View.GONE
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
        stallHandler.removeCallbacks(stallRunnable)
        PusherManager.connectionListener = null
        if (isOnlineMode && roomCode.isNotEmpty()) {
            PusherManager.unsubscribeFromRoom(roomCode)
        }
        _binding = null
    }
}
