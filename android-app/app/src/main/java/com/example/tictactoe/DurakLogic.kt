package com.example.tictactoe

import android.graphics.Color

enum class CardSuit(val code: String, val symbol: String, val isRed: Boolean, val colorInt: Int, val suitName: String) {
    HEARTS("H", "♥️", true, Color.parseColor("#EF4444"), "Yurak (Qizil) ♥️"),
    DIAMONDS("D", "♦️", true, Color.parseColor("#F97316"), "G'isht (Bubi) ♦️"),
    CLUBS("C", "♣️", false, Color.parseColor("#1E293B"), "Chillik (Kresti) ♣️"),
    SPADES("S", "♠️", false, Color.parseColor("#0F172A"), "Qarg'a (Piki) ♠️");

    companion object {
        fun fromCode(code: String): CardSuit {
            return values().firstOrNull { it.code == code } ?: HEARTS
        }
    }
}

enum class CardRank(val value: Int, val label: String) {
    SIX(6, "6"),
    SEVEN(7, "7"),
    EIGHT(8, "8"),
    NINE(9, "9"),
    TEN(10, "10"),
    JACK(11, "J"),
    QUEEN(12, "Q"),
    KING(13, "K"),
    ACE(14, "A");

    companion object {
        fun fromLabel(label: String): CardRank {
            return values().firstOrNull { it.label == label } ?: SIX
        }
    }
}

data class Card(val suit: CardSuit, val rank: CardRank) {
    val code: String = "${suit.code}${rank.label}"

    companion object {
        fun fromCode(code: String): Card {
            if (code.length < 2) return Card(CardSuit.HEARTS, CardRank.SIX)
            val suitCode = code.substring(0, 1)
            val rankLabel = code.substring(1)
            return Card(CardSuit.fromCode(suitCode), CardRank.fromLabel(rankLabel))
        }
    }
}

data class TablePair(
    val attackCard: Card,
    var defendCard: Card? = null
)

class DurakLogic {

    var deck = mutableListOf<Card>()
    var trumpCard: Card? = null
    val trumpSuit: CardSuit get() = trumpCard?.suit ?: CardSuit.HEARTS

    var playerHand = mutableListOf<Card>()
    var opponentHand = mutableListOf<Card>()
    var tablePairs = mutableListOf<TablePair>()

    var isPlayerAttacker: Boolean = true
    var isDefenderTaking: Boolean = false // When defender declares taking, attacker can add more cards (Qo'shib berish)
    var isGameOver: Boolean = false
    var winner: Int = 0 // 0 = Ongoing, 1 = Player, 2 = Opponent/Bot

    init {
        initNewGame()
    }

    fun initNewGame(customTrumpCard: Card? = null, p1Cards: List<String>? = null, p2Cards: List<String>? = null) {
        tablePairs.clear()
        isGameOver = false
        isDefenderTaking = false
        winner = 0

        if (p1Cards != null && p2Cards != null && customTrumpCard != null) {
            trumpCard = customTrumpCard
            playerHand = p1Cards.map { Card.fromCode(it) }.toMutableList()
            opponentHand = p2Cards.map { Card.fromCode(it) }.toMutableList()
            deck.clear()
            for (s in CardSuit.values()) {
                for (r in CardRank.values()) {
                    val c = Card(s, r)
                    if (!playerHand.contains(c) && !opponentHand.contains(c) && c != trumpCard) {
                        deck.add(c)
                    }
                }
            }
            deck.shuffle()
            deck.add(trumpCard!!)
        } else {
            deck.clear()
            for (s in CardSuit.values()) {
                for (r in CardRank.values()) {
                    deck.add(Card(s, r))
                }
            }
            deck.shuffle()

            playerHand.clear()
            opponentHand.clear()

            for (i in 0 until 6) {
                if (deck.isNotEmpty()) playerHand.add(deck.removeAt(0))
                if (deck.isNotEmpty()) opponentHand.add(deck.removeAt(0))
            }

            trumpCard = if (deck.isNotEmpty()) deck.last() else Card(CardSuit.HEARTS, CardRank.ACE)

            val pLowestTrump = playerHand.filter { it.suit == trumpSuit }.minByOrNull { it.rank.value }
            val oLowestTrump = opponentHand.filter { it.suit == trumpSuit }.minByOrNull { it.rank.value }

            isPlayerAttacker = when {
                pLowestTrump != null && oLowestTrump != null -> pLowestTrump.rank.value <= oLowestTrump.rank.value
                pLowestTrump != null -> true
                oLowestTrump != null -> false
                else -> true
            }
        }

        sortHand(playerHand)
        sortHand(opponentHand)
    }

    fun initNewGameWithSyncedDeck(trump: Card, myCards: List<String>, oppCards: List<String>, deckCodes: List<String>) {
        tablePairs.clear()
        isGameOver = false
        isDefenderTaking = false
        winner = 0

        trumpCard = trump
        playerHand = myCards.map { Card.fromCode(it) }.toMutableList()
        opponentHand = oppCards.map { Card.fromCode(it) }.toMutableList()
        deck = deckCodes.map { Card.fromCode(it) }.toMutableList()

        sortHand(playerHand)
        sortHand(opponentHand)
    }

    fun sortHand(hand: MutableList<Card>) {
        hand.sortWith(compareBy<Card> { it.suit == trumpSuit }.thenBy { it.suit.ordinal }.thenBy { it.rank.value })
    }

    fun canBeat(attackCard: Card, defendCard: Card): Boolean {
        if (defendCard.suit == attackCard.suit) {
            return defendCard.rank.value > attackCard.rank.value
        }
        if (defendCard.suit == trumpSuit && attackCard.suit != trumpSuit) {
            return true
        }
        return false
    }

    fun canAttackWith(card: Card, isPlayer: Boolean): Boolean {
        if (isGameOver) return false
        val isAttacker = (isPlayer == isPlayerAttacker)
        if (!isAttacker) return false

        val defenderHandSize = if (isPlayer) opponentHand.size else playerHand.size
        val unbeatCount = tablePairs.count { it.defendCard == null }

        // While not in taking mode, cannot throw more unbeat cards than defender's hand size
        if (!isDefenderTaking && unbeatCount >= defenderHandSize) return false
        if (tablePairs.isEmpty()) return true

        val ranksOnTable = mutableSetOf<CardRank>()
        for (pair in tablePairs) {
            ranksOnTable.add(pair.attackCard.rank)
            pair.defendCard?.let { ranksOnTable.add(it.rank) }
        }

        return ranksOnTable.contains(card.rank)
    }

    fun attack(card: Card, isPlayer: Boolean): Boolean {
        if (!canAttackWith(card, isPlayer)) return false

        val hand = if (isPlayer) playerHand else opponentHand
        hand.remove(card)
        tablePairs.add(TablePair(attackCard = card))
        return true
    }

    fun defend(attackCard: Card, defendCard: Card, isPlayer: Boolean): Boolean {
        val target = tablePairs.firstOrNull { it.attackCard == attackCard && it.defendCard == null } ?: return false
        if (!canBeat(attackCard, defendCard)) return false

        val hand = if (isPlayer) playerHand else opponentHand
        hand.remove(defendCard)
        target.defendCard = defendCard
        return true
    }

    fun clearTableToBita() {
        tablePairs.clear()
        isDefenderTaking = false
        refillHands()
        isPlayerAttacker = !isPlayerAttacker
    }

    fun declareTakeByDefender() {
        isDefenderTaking = true
    }

    fun finalizeTakeByDefender(isDefenderPlayer: Boolean) {
        val defenderHand = if (isDefenderPlayer) playerHand else opponentHand
        for (pair in tablePairs) {
            defenderHand.add(pair.attackCard)
            pair.defendCard?.let { defenderHand.add(it) }
        }
        tablePairs.clear()
        sortHand(defenderHand)
        isDefenderTaking = false
        // Attacker refills up to 6, defender keeps cards and skips refill
        refillHandsAfterTake(isDefenderPlayer)
        // Attacker remains attacker since defender took
    }

    fun refillHands() {
        val (firstHand, secondHand) = if (isPlayerAttacker) {
            Pair(playerHand, opponentHand)
        } else {
            Pair(opponentHand, playerHand)
        }

        while (firstHand.size < 6 && deck.isNotEmpty()) {
            firstHand.add(deck.removeAt(0))
        }
        while (secondHand.size < 6 && deck.isNotEmpty()) {
            secondHand.add(deck.removeAt(0))
        }

        sortHand(playerHand)
        sortHand(opponentHand)
    }

    private fun refillHandsAfterTake(isDefenderPlayer: Boolean) {
        val attackerHand = if (isDefenderPlayer) opponentHand else playerHand
        while (attackerHand.size < 6 && deck.isNotEmpty()) {
            attackerHand.add(deck.removeAt(0))
        }
        val defenderHand = if (isDefenderPlayer) playerHand else opponentHand
        while (defenderHand.size < 6 && deck.isNotEmpty()) {
            defenderHand.add(deck.removeAt(0))
        }
        sortHand(playerHand)
        sortHand(opponentHand)
    }

    fun checkGameOver() {
        if (deck.isEmpty()) {
            if (playerHand.isEmpty() && opponentHand.isEmpty()) {
                isGameOver = true
                winner = 0 // Draw
            } else if (playerHand.isEmpty()) {
                isGameOver = true
                winner = 1 // Player Won
            } else if (opponentHand.isEmpty()) {
                isGameOver = true
                winner = 2 // Opponent Won
            }
        }
    }
}
