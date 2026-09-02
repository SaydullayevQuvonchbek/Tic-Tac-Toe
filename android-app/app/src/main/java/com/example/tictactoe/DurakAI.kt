package com.example.tictactoe

object DurakAI {

    enum class ActionType { ATTACK, DEFEND, PASS, TAKE }

    data class Decision(
        val type: ActionType,
        val card: Card? = null,
        val targetAttackCard: Card? = null
    )

    fun getBestAction(
        logic: DurakLogic,
        isBotAttacker: Boolean,
        difficulty: BotDifficulty = BotDifficulty.MEDIUM
    ): Decision {
        val botHand = logic.opponentHand
        val trumpSuit = logic.trumpSuit
        val smart = difficulty != BotDifficulty.EASY
        val endgame = difficulty == BotDifficulty.HARD && logic.deck.size <= 4

        // Rough count of trumps still unknown to the bot (could be in the opponent's hand or the stock).
        val botTrumps = botHand.count { it.suit == trumpSuit }
        val seenTrumps = botTrumps +
            logic.beatenPile.count { it.suit == trumpSuit } +
            logic.tablePairs.sumOf { p ->
                (if (p.attackCard.suit == trumpSuit) 1 else 0) + (if (p.defendCard?.suit == trumpSuit) 1 else 0)
            } +
            (if (logic.trumpCard?.suit == trumpSuit) 1 else 0)
        val trumpsOutstanding = (9 - seenTrumps).coerceAtLeast(0)
        // If almost no trumps are left unseen and the bot holds a couple, it is trump-dominant.
        val trumpDominant = smart && botTrumps >= 2 && trumpsOutstanding - botTrumps <= 1

        if (logic.isDefenderTaking) {
            return if (isBotAttacker) {
                val playable = botHand.filter { logic.canAttackWith(it, isPlayer = false) }
                val dump = playable.filter { it.suit != trumpSuit }.minByOrNull { it.rank.value }
                    ?: if (endgame) null else playable.filter { it.suit == trumpSuit && it.rank.value <= 10 }.minByOrNull { it.rank.value }
                if (dump != null) Decision(ActionType.ATTACK, dump) else Decision(ActionType.PASS)
            } else {
                Decision(ActionType.PASS)
            }
        }

        if (isBotAttacker) {
            if (logic.tablePairs.isEmpty()) {
                val nonTrumps = botHand.filter { it.suit != trumpSuit }.sortedBy { it.rank.value }
                if (nonTrumps.isNotEmpty()) return Decision(ActionType.ATTACK, nonTrumps.first())
                val trumps = botHand.filter { it.suit == trumpSuit }.sortedBy { it.rank.value }
                if (trumps.isNotEmpty()) return Decision(ActionType.ATTACK, trumps.first())
                return Decision(ActionType.PASS)
            }

            val unbeat = logic.tablePairs.count { it.defendCard == null }
            if (unbeat > 0) return Decision(ActionType.PASS)

            val playable = botHand.filter { logic.canAttackWith(it, isPlayer = false) }
            if (playable.isNotEmpty()) {
                val nonTrumpAdd = playable.filter { it.suit != trumpSuit }.minByOrNull { it.rank.value }
                if (nonTrumpAdd != null) return Decision(ActionType.ATTACK, nonTrumpAdd)
                // Only feed a trump if the opponent is nearly out of cards.
                if (logic.playerHand.size <= 2 || endgame) {
                    val trumpAdd = playable.filter { it.suit == trumpSuit && it.rank.value <= 11 }.minByOrNull { it.rank.value }
                    if (trumpAdd != null) return Decision(ActionType.ATTACK, trumpAdd)
                }
            }
            return Decision(ActionType.PASS)
        }

        // ---- Bot is DEFENDING ----
        val unbeatPair = logic.tablePairs.firstOrNull { it.defendCard == null }
            ?: return Decision(ActionType.PASS)
        val attackCard = unbeatPair.attackCard
        val beatable = botHand.filter { logic.canBeat(attackCard, it) }
        if (beatable.isEmpty()) return Decision(ActionType.TAKE)

        val nonTrumpBeat = beatable.filter { it.suit != trumpSuit }.minByOrNull { it.rank.value }
        if (nonTrumpBeat != null) return Decision(ActionType.DEFEND, nonTrumpBeat, attackCard)

        val trumpBeat = beatable.filter { it.suit == trumpSuit }.minByOrNull { it.rank.value }
        if (trumpBeat != null) {
            // Don't burn a trump on a cheap non-trump attack while the stock is deep and the bot isn't
            // trump-dominant — take the card instead and keep the trump.
            val cheapAttack = attackCard.suit != trumpSuit && attackCard.rank.value <= 10
            val wasteful = smart && cheapAttack && !endgame && !trumpDominant &&
                logic.deck.size > 6 && botHand.size >= 4 &&
                (trumpBeat.rank.value >= 12 || logic.tablePairs.size == 1)
            if (wasteful) return Decision(ActionType.TAKE)
            return Decision(ActionType.DEFEND, trumpBeat, attackCard)
        }
        return Decision(ActionType.TAKE)
    }
}
