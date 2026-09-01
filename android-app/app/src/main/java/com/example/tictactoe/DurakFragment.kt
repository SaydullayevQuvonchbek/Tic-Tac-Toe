package com.example.tictactoe

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
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
                        Toast.makeText(context, "Please enter room code", Toast.LENGTH_SHORT).show()
                    }
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
                    startGame()
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
                    startGame()
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

    private fun startGame() {
        selectedCard = null
        logic.initNewGame()

        if (isOnlineMode) {
            subscribePusherEvents()
            // Request clean start from server
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

        // Emotes
        binding.layoutEmotesDurak.removeAllViews()
        val emoteBar = EmoteHelper.createEmoteBar(requireContext()) { emote ->
            EmoteHelper.showFloatingEmote(binding.root as ViewGroup, emote, isOpponent = false)
            if (isOnlineMode && roomCode.isNotEmpty()) {
                ApiClient.instance.sendEmote(EmoteRequest(roomCode, myPlayerId, emote)).enqueue(object : Callback<EmoteResponse> {
                    override fun onResponse(call: Call<EmoteResponse>, response: Response<EmoteResponse>) {}
                    override fun onFailure(call: Call<EmoteResponse>, t: Throwable) {}
                })
            }
        }
        binding.layoutEmotesDurak.addView(emoteBar)

        if (isAiMode && !logic.isPlayerAttacker) {
            triggerBotAction()
        }
    }

    private fun renderBoard() {
        if (_binding == null) return

        // 1. Prominent ZOD (Trump) Badge & Deck Pile
        val suit = logic.trumpSuit
        binding.tvTrumpHeaderSuit.text = suit.suitName
        binding.tvTrumpHeaderSuit.setTextColor(suit.colorInt)

        binding.viewTrumpCard.card = logic.trumpCard
        binding.viewTrumpCard.isFaceDown = false

        binding.viewDeckPile.isFaceDown = true
        binding.viewDeckPile.visibility = if (logic.deck.size > 1) View.VISIBLE else View.INVISIBLE
        binding.viewTrumpCard.visibility = if (logic.deck.isNotEmpty()) View.VISIBLE else View.INVISIBLE
        binding.tvDeckCount.text = "🂠 ${logic.deck.size}"

        // 2. Opponent Count & Backs
        binding.tvOpponentCardsCount.text = "🂠 ${logic.opponentHand.size} ta"
        binding.layoutOpponentCards.removeAllViews()
        val oppCount = logic.opponentHand.size.coerceAtMost(10)
        for (i in 0 until oppCount) {
            val miniCard = DurakCardView(requireContext()).apply {
                isFaceDown = true
                layoutParams = LinearLayout.LayoutParams(46, 64).apply {
                    setMargins(-12, 0, 0, 0)
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

    private fun renderTablePairs() {
        binding.layoutTablePairs.removeAllViews()
        for (pair in logic.tablePairs) {
            val pairContainer = FrameLayout(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(88, 126).apply {
                    setMargins(10, 0, 10, 0)
                }
            }

            // Attack Card (Bottom)
            val attackView = DurakCardView(requireContext()).apply {
                card = pair.attackCard
                isFaceDown = false
                layoutParams = FrameLayout.LayoutParams(76, 110)
            }
            pairContainer.addView(attackView)

            // Defend Card (Top with offset & rotation)
            if (pair.defendCard != null) {
                val defendView = DurakCardView(requireContext()).apply {
                    card = pair.defendCard
                    isFaceDown = false
                    rotation = 14f
                    translationX = 12f
                    translationY = 12f
                    layoutParams = FrameLayout.LayoutParams(76, 110)
                }
                pairContainer.addView(defendView)
            }

            binding.layoutTablePairs.addView(pairContainer)
        }
    }

    private fun renderPlayerHand() {
        binding.layoutPlayerCards.removeAllViews()
        for (card in logic.playerHand) {
            val cardView = DurakCardView(requireContext()).apply {
                this.card = card
                isFaceDown = false
                isSelectedCard = (selectedCard == card)
                layoutParams = LinearLayout.LayoutParams(82, 122).apply {
                    setMargins(6, 0, 6, 0)
                }
                setOnClickListener {
                    if (selectedCard == card) {
                        // Double tap to play immediately!
                        if (logic.isPlayerAttacker) {
                            handlePlayerAttack(card)
                        } else {
                            handlePlayerDefend(card)
                        }
                    } else {
                        HapticHelper.performClick(context)
                        SoundHelper.playMoveSound(context)
                        selectedCard = card
                        renderPlayerHand()
                        updateStatusAndButtons()
                    }
                }
            }
            binding.layoutPlayerCards.addView(cardView)
        }
    }

    private fun updateStatusAndButtons() {
        val isAttacker = logic.isPlayerAttacker
        val unbeatCount = logic.tablePairs.count { it.defendCard == null }

        if (isAttacker) {
            binding.tvGameStatus.text = "Sizning navbatingiz (Hujum qiling) ⚔️"
            binding.btnAttack.isEnabled = (selectedCard != null && logic.canAttackWith(selectedCard!!, true))
            binding.btnDefend.visibility = View.GONE
            binding.btnAttack.visibility = View.VISIBLE
            binding.btnTakeCards.visibility = View.GONE

            // Can Pass/Bita only if table has at least 1 pair and all are defended
            binding.btnPassBita.visibility = View.VISIBLE
            binding.btnPassBita.isEnabled = (logic.tablePairs.isNotEmpty() && unbeatCount == 0)
            binding.btnPassBita.text = "✋ Bita (Bo'ldi)"
        } else {
            binding.tvGameStatus.text = if (unbeatCount > 0) "Himoyalaning (Kartani yoping) 🛡️" else "Raqib yana karta qo'shishi mumkin... ⚔️"
            binding.btnAttack.visibility = View.GONE
            binding.btnDefend.visibility = View.VISIBLE
            binding.btnPassBita.visibility = View.GONE
            binding.btnTakeCards.visibility = View.VISIBLE

            val targetUnbeat = logic.tablePairs.firstOrNull { it.defendCard == null }
            binding.btnDefend.isEnabled = (selectedCard != null && targetUnbeat != null && logic.canBeat(targetUnbeat.attackCard, selectedCard!!))
            
            // Defender can ALWAYS choose to take table cards whenever there are cards on the table!
            binding.btnTakeCards.isEnabled = (logic.tablePairs.isNotEmpty())
            binding.btnTakeCards.text = "📥 Ko'tarib Olish"
        }

        binding.btnAttack.alpha = if (binding.btnAttack.isEnabled) 1.0f else 0.45f
        binding.btnDefend.alpha = if (binding.btnDefend.isEnabled) 1.0f else 0.45f
        binding.btnPassBita.alpha = if (binding.btnPassBita.isEnabled) 1.0f else 0.45f
        binding.btnTakeCards.alpha = if (binding.btnTakeCards.isEnabled) 1.0f else 0.45f
    }

    private fun handlePlayerAttack(card: Card) {
        if (!logic.canAttackWith(card, true)) {
            Toast.makeText(context, "Bu karta bilan hozir hujum qilib bo'lmaydi!", Toast.LENGTH_SHORT).show()
            return
        }

        HapticHelper.performClick(requireContext())
        SoundHelper.playMoveSound(requireContext())

        logic.attack(card, true)
        selectedCard = null
        renderBoard()

        if (isOnlineMode) {
            sendCardActionOnline("attack", card.code, null)
        } else if (isAiMode) {
            triggerBotAction()
        }

        checkGameOver()
    }

    private fun handlePlayerDefend(card: Card) {
        val target = logic.tablePairs.firstOrNull { it.defendCard == null }
        if (target == null || !logic.canBeat(target.attackCard, card)) {
            Toast.makeText(context, "Bu karta stoldagi kartani ura olmaydi!", Toast.LENGTH_SHORT).show()
            return
        }

        HapticHelper.performHeavyImpact(requireContext())
        SoundHelper.playCaptureSound(requireContext())

        logic.defend(target.attackCard, card, true)
        selectedCard = null
        renderBoard()

        if (isOnlineMode) {
            sendCardActionOnline("defend", card.code, target.attackCard.code)
        } else if (isAiMode) {
            triggerBotAction()
        }

        checkGameOver()
    }

    private fun handlePlayerPassBita() {
        HapticHelper.performClick(requireContext())
        SoundHelper.playMoveSound(requireContext())

        logic.clearTableToBita()
        selectedCard = null
        renderBoard()

        if (isOnlineMode) {
            sendCardActionOnline("pass", null, null)
        } else if (isAiMode) {
            triggerBotAction()
        }

        checkGameOver()
    }

    private fun handlePlayerTake() {
        HapticHelper.performHeavyImpact(requireContext())
        SoundHelper.playCaptureSound(requireContext())

        logic.takeTableCards(true)
        selectedCard = null
        renderBoard()

        if (isOnlineMode) {
            sendCardActionOnline("take", null, null)
        } else if (isAiMode) {
            triggerBotAction()
        }

        checkGameOver()
    }

    private fun triggerBotAction() {
        handler.postDelayed({
            if (!isAdded || logic.isGameOver) return@postDelayed

            val decision = DurakAI.getBestAction(logic, isBotAttacker = !logic.isPlayerAttacker)
            when (decision.type) {
                DurakAI.ActionType.ATTACK -> {
                    decision.card?.let {
                        logic.attack(it, false)
                        HapticHelper.performClick(requireContext())
                        SoundHelper.playMoveSound(requireContext())
                    }
                }
                DurakAI.ActionType.DEFEND -> {
                    if (decision.card != null && decision.targetAttackCard != null) {
                        logic.defend(decision.targetAttackCard, decision.card, false)
                        HapticHelper.performHeavyImpact(requireContext())
                        SoundHelper.playCaptureSound(requireContext())
                    }
                }
                DurakAI.ActionType.PASS -> {
                    logic.clearTableToBita()
                    HapticHelper.performClick(requireContext())
                }
                DurakAI.ActionType.TAKE -> {
                    logic.takeTableCards(false)
                    Toast.makeText(context, "🤖 Bot kartalarni oldi!", Toast.LENGTH_SHORT).show()
                    HapticHelper.performHeavyImpact(requireContext())
                }
            }

            renderBoard()
            checkGameOver()
        }, 800)
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
                "take" -> {
                    logic.takeTableCards(isPlayer = false)
                    Toast.makeText(context, "Raqib kartalarni oldi!", Toast.LENGTH_SHORT).show()
                    HapticHelper.performHeavyImpact(requireContext())
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
