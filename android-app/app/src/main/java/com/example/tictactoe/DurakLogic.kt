package com.example.tictactoe

import android.graphics.Color

enum class CardSuit(val code: String, val symbol: String, val isRed: Boolean, val colorInt: Int) {
    HEARTS("H", "♥️", true, Color.parseColor("#EF4444")),
    DIAMONDS("D", "♦️", true, Color.parseColor("#F97316")),
    CLUBS("C", "♣️", false, Color.parseColor("#1E293B")),
    SPADES("S", "♠️", false, Color.parseColor("#0F172A"));

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
    var isGameOver: Boolean = false
    var winner: Int = 0 // 0 = Ongoing, 1 = Player, 2 = Opponent/Bot

    init {
        initNewGame()
    }

    fun initNewGame(customTrumpCard: Card? = null, p1Cards: List<String>? = null, p2Cards: List<String>? = null) {
        tablePairs.clear()
        isGameOver = false
        winner = 0

        if (p1Cards != null && p2Cards != null && customTrumpCard != null) {
            trumpCard = customTrumpCard
            playerHand = p1Cards.map { Card.fromCode(it) }.toMutableList()
            opponentHand = p2Cards.map { Card.fromCode(it) }.toMutableList()
            deck.clear()
            // Create full deck minus dealt cards
            for (s in CardSuit.values()) {
                for (r in CardRank.values()) {
                    val c = Card(s, r)
                    if (!playerHand.contains(c) && !opponentHand.contains(c) && c != trumpCard) {
                        deck.add(c)
                    }
                }
            }
            deck.shuffle()
            deck.add(trumpCard!!) // Trump card is last in deck
        } else {
            // Local offline deck generation
            deck.clear()
            for (s in CardSuit.values()) {
                for (r in CardRank.values()) {
                    deck.add(Card(s, r))
                }
            }
            deck.shuffle()

            playerHand.clear()
            opponentHand.clear()

            // Deal 6 cards each
            for (i in 0 until 6) {
                if (deck.isNotEmpty()) playerHand.add(deck.removeAt(0))
                if (deck.isNotEmpty()) opponentHand.add(deck.removeAt(0))
            }

            // Trump card is the last card in the deck
            trumpCard = if (deck.isNotEmpty()) deck.last() else Card(CardSuit.HEARTS, CardRank.ACE)

            // Lowest trump starts first
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
        val defenderHandSize = if (isPlayer) opponentHand.size else playerHand.size
        val unbeatCount = tablePairs.count { it.defendCard == null }

        // Max 6 attacks and cannot exceed defender's cards
        if (tablePairs.size >= 6 || (unbeatCount >= defenderHandSize)) return false

        // If table is empty, can attack with anything
        if (tablePairs.isEmpty()) return true

        // Otherwise rank must match ANY card already on the table
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
        if (!hand.remove(card)) return false

        tablePairs.add(TablePair(card))
        return true
    }

    fun defend(attackCard: Card, defendCard: Card, isPlayer: Boolean): Boolean {
        val pair = tablePairs.firstOrNull { it.attackCard == attackCard && it.defendCard == null } ?: return false
        if (!canBeat(attackCard, defendCard)) return false

        val hand = if (isPlayer) playerHand else opponentHand
        if (!hand.remove(defendCard)) return false

        pair.defendCard = defendCard
        return true
    }

    fun takeTableCards(isPlayer: Boolean) {
        val hand = if (isPlayer) playerHand else opponentHand
        for (pair in tablePairs) {
            hand.add(pair.attackCard)
            pair.defendCard?.let { hand.add(it) }
        }
        tablePairs.clear()
        sortHand(hand)

        // Defender took -> Attacker remains the same
        refillHands()
        checkGameOver()
    }

    fun clearTableToBita() {
        tablePairs.clear()

        // Turn switches to previous defender (now new attacker)
        isPlayerAttacker = !isPlayerAttacker
        refillHands()
        checkGameOver()
    }

    fun refillHands() {
        val attackerHand = if (isPlayerAttacker) playerHand else opponentHand
        val defenderHand = if (isPlayerAttacker) opponentHand else playerHand

        // Attacker draws first up to 6
        while (attackerHand.size < 6 && deck.isNotEmpty()) {
            attackerHand.add(deck.removeAt(0))
        }

        // Defender draws next up to 6
        while (defenderHand.size < 6 && deck.isNotEmpty()) {
            defenderHand.add(deck.removeAt(0))
        }

        sortHand(playerHand)
        sortHand(opponentHand)
    }

    fun checkGameOver() {
        if (deck.isNotEmpty() || tablePairs.isNotEmpty()) return

        val pEmpty = playerHand.isEmpty()
        val oEmpty = opponentHand.isEmpty()

        if (pEmpty && oEmpty) {
            isGameOver = true
            winner = 0 // Draw
        } else if (pEmpty) {
            isGameOver = true
            winner = 1 // Player Win
        } else if (oEmpty) {
            isGameOver = true
            winner = 2 // Opponent/Bot Win
        }
    }
}
