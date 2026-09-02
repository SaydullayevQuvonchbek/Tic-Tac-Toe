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

        // Action Buttons
        binding.btnAttack.setOnClickListener {
            selectedCard?.let { handlePlayerAttack(it) }
        }

        binding.btnDefend.setOnClickListener {
            selectedCard?.let { handlePlayerDefend(it) }
        }

        binding.btnPassBita.setOnClickListener {
            handlePlayerPassBita()
        }

        binding.btnTakeCards.setOnClickListener {
            handlePlayerTake()
        }

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
                Toast.makeText(context, "Network Error: ${t.message}", Toast.LENGTH_SHORT).show()
                showSetupScreen()
            }
        })
    }

    private fun joinOnlineRoom(code: String) {
        val pd = android.app.ProgressDialog(context).apply {
            setMessage("Joining Durak room...")
            show()
        }

        ApiClient.instance.joinRoom(RoomJoinRequest(myPlayerId, code)).enqueue(object : Callback<RoomJoinResponse> {
            override fun onResponse(call: Call<RoomJoinResponse>, response: Response<RoomJoinResponse>) {
                pd.dismiss()
                if (response.isSuccessful && response.body()?.status == "success") {
                    roomCode = code
                    isHost = false
                    isOnlineMode = true
                    isAiMode = false
                    showGameplayScreen()
                    subscribePusherEvents()
                    sendCardActionOnline("guest_joined", "", "")
                } else {
                    Toast.makeText(context, response.body()?.message ?: "Room not found", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<RoomJoinResponse>, t: Throwable) {
                pd.dismiss()
                Toast.makeText(context, "Network Error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun subscribePusherEvents() {
        PusherManager.connect()
        PusherManager.subscribeToRoom(roomCode,
            onGameStarted = { data ->
                activity?.runOnUiThread {
                    binding.waitingContainer.visibility = View.GONE
                    if (isHost) {
                        hostBroadcastGameInit()
                    }
                }
            },
            onMoveMade = { eventData ->
                activity?.runOnUiThread {
                    handlePusherCardAction(eventData)
                }
            },
            onOpponentLeft = {
                activity?.runOnUiThread {
                    handleOpponentForfeited()
                }
            },
            onEmoteReceived = { eventData ->
                activity?.runOnUiThread {
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

        sendCardActionOnline("sync_init", trump.code, payload)
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
            override fun onFailure(call: Call<CardStartResponse>, t: Throwable) {}
        })
    }

    private fun startGame() {
        selectedCard = null
        logic.initNewGame()

        if (isOnlineMode) {
            subscribePusherEvents()
            ApiClient.instance.startCardGame(CardStartRequest(roomCode, myPlayerId)).enqueue(object : Callback<CardStartResponse> {
                override fun onResponse(call: Call<CardStartResponse>, response: Response<CardStartResponse>) {
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
                override fun onFailure(call: Call<CardStartResponse>, t: Throwable) {}
            })
        }

        showGameplayScreen()
        renderBoard()

        // Setup Quick Emotes Bar (Design 3c)
        setupEmotes()

        if (isAiMode && !logic.isPlayerAttacker) {
            triggerBotAction()
        }
    }

    private fun setupEmotes() {
        binding.layoutEmotesDurak.removeAllViews()
        val emoteBar = EmoteHelper.createQuickEmoteBar(
            context = requireContext(),
            onEmoteClick = { emote -> sendEmote(emote) },
            onMoreClick = {
                ReactionBottomSheetDialog(requireContext()) { emote ->
                    sendEmote(emote)
                }.show()
            }
        )
        binding.layoutEmotesDurak.addView(emoteBar)
    }

    private fun sendEmote(emote: String) {
        val sharedPref = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
        val myUsername = sharedPref.getString("username", "Siz") ?: "Siz"
        EmoteHelper.showFloatingEmote(binding.root as ViewGroup, emote, isOpponent = false, senderName = myUsername)
        if (isOnlineMode && roomCode.isNotEmpty()) {
            ApiClient.instance.sendEmote(EmoteRequest(roomCode, myPlayerId, emote)).enqueue(object : Callback<EmoteResponse> {
                override fun onResponse(call: Call<EmoteResponse>, response: Response<EmoteResponse>) {}
                override fun onFailure(call: Call<EmoteResponse>, t: Throwable) {}
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
                    setMargins(dp(6), 0, dp(6), 0)
                }
            }

            val isTargeted = (selectedTargetAttackCard == pair.attackCard)

            // Attack Card (Bottom)
            val attackView = DurakCardView(requireContext()).apply {
                card = pair.attackCard
                isFaceDown = false
                isSelectedCard = isTargeted
                layoutParams = FrameLayout.LayoutParams(cardW, cardH)
                setOnClickListener {
                    if (pair.defendCard != null) return@setOnClickListener
                    if (selectedCard != null && logic.canBeat(pair.attackCard, selectedCard!!)) {
                        val c = selectedCard!!
                        selectedCard = null
                        selectedTargetAttackCard = null
                        handlePlayerDefend(c, pair.attackCard)
                    } else {
                        selectedTargetAttackCard = if (selectedTargetAttackCard == pair.attackCard) null else pair.attackCard
                        renderTablePairs()
                        updateStatusAndButtons()
                    }
                }
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

            if (pair.defendCard == null && !logic.isPlayerAttacker) {
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

    private fun renderPlayerHand() {
        if (_binding == null) return
        binding.layoutPlayerCards.removeAllViews()
        val count = logic.playerHand.size
        if (count == 0) return

        // 1. Calculate usable width on screen
        val screenWidthPx = resources.displayMetrics.widthPixels
        val paddingPx = dp(16)
        val usableWidthPx = (screenWidthPx - paddingPx).coerceAtLeast(dp(280))

        // 2. Dynamic Card Dimensions in DP matching Design 3a
        val cardW = dp(60)
        val cardH = dp(88)

        // 3. Exact Screen-Fit Fan Overlap: All cards fit neatly on screen with zero scrolling!
        val overlapMarginPx = if (count > 1) {
            val neededStep = (usableWidthPx - cardW) / (count - 1)
            val computedMargin = neededStep - cardW
            computedMargin.coerceAtMost(dp(4))
        } else {
            0
        }

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
                    translationY = -dp(18).toFloat()
                    bringToFront()
                }
            }

            // Fluid Drag-to-Throw / Drag-and-Drop Support (Surib Tashlash)
            var downRawX = 0f
            var downRawY = 0f
            var isDragging = false

            cardView.setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        downRawX = event.rawX
                        downRawY = event.rawY
                        isDragging = false
                        v.bringToFront()
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                        true
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        val deltaX = event.rawX - downRawX
                        val deltaY = event.rawY - downRawY
                        if (Math.abs(deltaY) > 15 || Math.abs(deltaX) > 15) {
                            isDragging = true
                            v.translationX = deltaX
                            v.translationY = deltaY
                        }
                        true
                    }
                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                        v.parent?.requestDisallowInterceptTouchEvent(false)
                        val currTranslationY = v.translationY
                        val dropX = event.rawX
                        v.translationX = 0f
                        v.translationY = 0f

                        if (isDragging && currTranslationY < -60f) {
                            // Dragged upwards to table -> safely execute on next loop tick
                            v.post {
                                if (logic.isPlayerAttacker) {
                                    handlePlayerAttack(card)
                                } else {
                                    // Check if dropped directly over a specific table card (Tanlab urish)
                                    var targetedAttackCard: Card? = null
                                    for (i in 0 until binding.layoutTablePairs.childCount) {
                                        val child = binding.layoutTablePairs.getChildAt(i)
                                        val loc = IntArray(2)
                                        child.getLocationOnScreen(loc)
                                        val left = loc[0]
                                        val right = left + child.width
                                        if (dropX >= (left - 15) && dropX <= (right + 15)) {
                                            val p = logic.tablePairs.getOrNull(i)
                                            if (p != null && p.defendCard == null && logic.canBeat(p.attackCard, card)) {
                                                targetedAttackCard = p.attackCard
                                                break
                                            }
                                        }
                                    }

                                    val curTarget = selectedTargetAttackCard
                                    if (targetedAttackCard == null && curTarget != null && logic.canBeat(curTarget, card)) {
                                        targetedAttackCard = curTarget
                                    }

                                    handlePlayerDefend(card, targetedAttackCard)
                                }
                            }
                        } else {
                            // Tap to select or double-tap to play
                            v.post {
                                if (selectedCard == card) {
                                    if (logic.isPlayerAttacker) {
                                        handlePlayerAttack(card)
                                    } else {
                                        handlePlayerDefend(card, selectedTargetAttackCard)
                                    }
                                } else {
                                    HapticHelper.performClick(v.context)
                                    SoundHelper.playMoveSound(v.context)
                                    selectedCard = card
                                    renderPlayerHand()
                                    updateStatusAndButtons()
                                }
                            }
                        }
                        true
                    }
                    else -> false
                }
            }

            binding.layoutPlayerCards.addView(cardView)
        }
    }

    private fun updateRoomCodeTiles(code: String) {
        val padded = code.padEnd(4, ' ')
        binding.tvTile1.text = padded.getOrNull(0)?.toString() ?: ""
        binding.tvTile2.text = padded.getOrNull(1)?.toString() ?: ""
        binding.tvTile3.text = padded.getOrNull(2)?.toString() ?: ""
        binding.tvTile4.text = padded.getOrNull(3)?.toString() ?: ""
    }

    private fun updateStatusAndButtons() {
        val isAttacker = logic.isPlayerAttacker
        val unbeatCount = logic.tablePairs.count { it.defendCard == null }

        if (logic.isDefenderTaking) {
            if (isAttacker) {
                binding.tvGameStatus.text = "RAQIB KO'TARMOQDA — KARTA QO'SHING"
                binding.btnAttack.visibility = View.VISIBLE
                binding.btnAttack.isEnabled = (selectedCard != null && logic.canAttackWith(selectedCard!!, true))
                binding.btnAttack.text = "⚔️ QO'SHISH"
                binding.btnPassBita.visibility = View.VISIBLE
                binding.btnPassBita.isEnabled = true
                binding.btnPassBita.text = "✅ BERDIM"
                binding.btnDefend.visibility = View.GONE
                binding.btnTakeCards.visibility = View.GONE
            } else {
                binding.tvGameStatus.text = "KO'TARISHNI TANLADINGIZ — KUTING..."
                binding.btnAttack.visibility = View.GONE
                binding.btnDefend.visibility = View.GONE
                binding.btnPassBita.visibility = View.GONE
                binding.btnTakeCards.visibility = View.GONE
            }
        } else if (isAttacker) {
            if (logic.tablePairs.isEmpty()) {
                binding.tvGameStatus.text = "SIZ HUJUMDASIZ — KARTA TASHLANG"
            } else if (unbeatCount == 0) {
                binding.tvGameStatus.text = "HAMMASI URILDI — BITA DEYING"
            } else {
                binding.tvGameStatus.text = "RAQIB HIMOYALANMOQDA..."
            }

            binding.btnAttack.visibility = View.VISIBLE
            binding.btnDefend.visibility = View.GONE
            binding.btnPassBita.visibility = View.VISIBLE
            binding.btnTakeCards.visibility = View.GONE

            binding.btnAttack.isEnabled = (selectedCard != null && logic.canAttackWith(selectedCard!!, logic.tablePairs.isNotEmpty()))
            binding.btnAttack.text = if (logic.tablePairs.isEmpty()) "⚔️ TASHLASH" else "⚔️ QO'SHISH"
            binding.btnPassBita.isEnabled = (logic.tablePairs.isNotEmpty() && unbeatCount == 0)
            binding.btnPassBita.text = "✋ BITA"
        } else {
            val targetUnbeat = selectedTargetAttackCard ?: logic.tablePairs.firstOrNull { it.defendCard == null }?.attackCard
            if (targetUnbeat != null) {
                binding.tvGameStatus.text = "SIZ HIMOYADASIZ — URING"
            } else if (logic.tablePairs.isNotEmpty()) {
                binding.tvGameStatus.text = "HAMMASI URILDI — BITA DEYING"
            } else {
                binding.tvGameStatus.text = "RAQIB HUJUM QILMOQDA..."
            }

            binding.btnAttack.visibility = View.GONE
            binding.btnDefend.visibility = View.VISIBLE
            binding.btnPassBita.visibility = View.GONE
            binding.btnTakeCards.visibility = View.VISIBLE

            binding.btnDefend.isEnabled = (selectedCard != null && targetUnbeat != null && logic.canBeat(targetUnbeat, selectedCard!!))
            binding.btnTakeCards.isEnabled = (logic.tablePairs.isNotEmpty())
            binding.btnTakeCards.text = "📥 OLISH"
        }

        binding.btnAttack.alpha = if (binding.btnAttack.isEnabled) 1.0f else 0.45f
        binding.btnDefend.alpha = if (binding.btnDefend.isEnabled) 1.0f else 0.45f
        binding.btnPassBita.alpha = if (binding.btnPassBita.isEnabled) 1.0f else 0.45f
        binding.btnTakeCards.alpha = if (binding.btnTakeCards.isEnabled) 1.0f else 0.45f
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
            val decision = DurakAI.getBestAction(logic, isBotAttacker = isBotAttacker)

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

    private fun sendCardActionOnline(action: String, card: String?, targetCard: String?) {
        ApiClient.instance.sendCardAction(CardActionRequest(roomCode, myPlayerId, action, card, targetCard)).enqueue(object : Callback<CardActionResponse> {
            override fun onResponse(call: Call<CardActionResponse>, response: Response<CardActionResponse>) {}
            override fun onFailure(call: Call<CardActionResponse>, t: Throwable) {}
        })
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

            val action = json.optString("action", "")
            val cardCode = json.optString("card", "")
            val targetCardCode = json.optString("target_card", "")

            when (action) {
                "guest_joined" -> {
                    if (isHost) {
                        hostBroadcastGameInit()
                    }
                }
                "sync_init" -> {
                    if (!isHost && targetCardCode.isNotEmpty()) {
                        try {
                            val initObj = JSONObject(targetCardCode)
                            val trump = Card.fromCode(initObj.getString("trump"))
                            val p1Arr = initObj.getJSONArray("p1")
                            val p2Arr = initObj.getJSONArray("p2")
                            val deckArr = initObj.getJSONArray("deck")
                            val hostAttacks = initObj.getBoolean("host_attacks")

                            val myCards = (0 until p2Arr.length()).map { p2Arr.getString(it) }
                            val oppCards = (0 until p1Arr.length()).map { p1Arr.getString(it) }
                            val deckList = (0 until deckArr.length()).map { deckArr.getString(it) }

                            logic.initNewGameWithSyncedDeck(trump, myCards, oppCards, deckList)
                            logic.isPlayerAttacker = !hostAttacks
                            showGameplayScreen()
                            renderBoard()
                            return
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
                }
                "take" -> {
                    logic.finalizeTakeByDefender(isDefenderPlayer = true)
                    HapticHelper.performHeavyImpact(requireContext())
                    SoundHelper.playCaptureSound(requireContext())
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
        if (isOnlineMode && roomCode.isNotEmpty()) {
            PusherManager.unsubscribeFromRoom(roomCode)
        }
        _binding = null
    }
}
