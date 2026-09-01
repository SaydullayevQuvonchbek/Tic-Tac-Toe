package com.example.tictactoe

object DurakAI {

    enum class ActionType {
        ATTACK,
        DEFEND,
        PASS,
        TAKE
    }

    data class Decision(
        val type: ActionType,
        val card: Card? = null,
        val targetAttackCard: Card? = null
    )

    fun getBestAction(logic: DurakLogic, isBotAttacker: Boolean): Decision {
        val botHand = logic.opponentHand
        val trumpSuit = logic.trumpSuit

        if (logic.isDefenderTaking) {
            if (isBotAttacker) {
                // Human is taking cards! Bot can dump more non-trumps onto human (Qo'shib berish)
                val playable = botHand.filter { logic.canAttackWith(it, isPlayer = false) }
                val nonTrumpDump = playable.filter { it.suit != trumpSuit }.minByOrNull { it.rank.value }
                if (nonTrumpDump != null) {
                    return Decision(ActionType.ATTACK, nonTrumpDump)
                }
                // No more cards to dump -> Finalize take
                return Decision(ActionType.PASS)
            } else {
                // Bot is taking, waiting for human attacker to finish adding cards
                return Decision(ActionType.PASS)
            }
        }

        if (isBotAttacker) {
            // Bot is ATTACKING
            if (logic.tablePairs.isEmpty()) {
                // Table is empty: Throw lowest non-trump, else lowest trump
                val nonTrumps = botHand.filter { it.suit != trumpSuit }.sortedBy { it.rank.value }
                if (nonTrumps.isNotEmpty()) {
                    return Decision(ActionType.ATTACK, nonTrumps.first())
                }
                val trumps = botHand.filter { it.suit == trumpSuit }.sortedBy { it.rank.value }
                if (trumps.isNotEmpty()) {
                    return Decision(ActionType.ATTACK, trumps.first())
                }
                return Decision(ActionType.PASS)
            } else {
                // Follow-up attack: Check if any unbeat cards remain
                val unbeat = logic.tablePairs.count { it.defendCard == null }
                if (unbeat > 0) {
                    return Decision(ActionType.PASS)
                }

                // Table cards are all defended, can bot add another matching card?
                val playableCards = botHand.filter { logic.canAttackWith(it, isPlayer = false) }
                if (playableCards.isNotEmpty()) {
                    val nonTrumpAdd = playableCards.filter { it.suit != trumpSuit }.minByOrNull { it.rank.value }
                    if (nonTrumpAdd != null) {
                        return Decision(ActionType.ATTACK, nonTrumpAdd)
                    }

                    if (logic.playerHand.size <= 2) {
                        val trumpAdd = playableCards.filter { it.suit == trumpSuit && it.rank.value <= 10 }.minByOrNull { it.rank.value }
                        if (trumpAdd != null) {
                            return Decision(ActionType.ATTACK, trumpAdd)
                        }
                    }
                }

                return Decision(ActionType.PASS)
            }
        } else {
            // Bot is DEFENDING
            val unbeatPair = logic.tablePairs.firstOrNull { it.defendCard == null }
                ?: return Decision(ActionType.PASS)

            val attackCard = unbeatPair.attackCard
            val beatableCards = botHand.filter { logic.canBeat(attackCard, it) }

            if (beatableCards.isEmpty()) {
                return Decision(ActionType.TAKE)
            }

            val nonTrumpBeat = beatableCards.filter { it.suit != trumpSuit }.minByOrNull { it.rank.value }
            if (nonTrumpBeat != null) {
                return Decision(ActionType.DEFEND, nonTrumpBeat, attackCard)
            }

            val trumpBeat = beatableCards.filter { it.suit == trumpSuit }.minByOrNull { it.rank.value }
            if (trumpBeat != null) {
                if (trumpBeat.rank.value >= 13 && attackCard.rank.value <= 8 && logic.deck.size > 10 && botHand.size > 4) {
                    return Decision(ActionType.TAKE)
                }
                return Decision(ActionType.DEFEND, trumpBeat, attackCard)
            }

            return Decision(ActionType.TAKE)
        }
    }
}
