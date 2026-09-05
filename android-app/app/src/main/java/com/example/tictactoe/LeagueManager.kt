package com.example.tictactoe

object LeagueManager {

    data class LeagueInfo(
        val tier: Int,
        val name: String,
        val badge: String,
        val minXp: Int,
        val maxXp: Int,
        val colorHex: String,
        val rewardCoins: Int,
        val description: String
    )

    data class LeaguePlayer(
        val rank: Int,
        val username: String,
        val xp: Int,
        val wins: Int,
        val isPromotionZone: Boolean
    )

    val LEAGUES = listOf(
        LeagueInfo(1, "Bronza Ligasi", "🥉", 0, 299, "#CD7F32", 100, "Boshlang'ich jangchilar maydoni"),
        LeagueInfo(2, "Kumush Ligasi", "🥈", 300, 799, "#94A3B8", 250, "Tajribali o'yinchilar ligasi"),
        LeagueInfo(3, "Oltin Ligasi", "🥇", 800, 1799, "#EAB308", 500, "Yuqori mahoratli chempionlar"),
        LeagueInfo(4, "Olmos Ligasi", "💎", 1800, 3499, "#06B6D4", 1000, "Elita ustalari bellashuvi"),
        LeagueInfo(5, "Master Ligasi", "👑", 3500, Int.MAX_VALUE, "#F59E0B", 2500, "Afsonaviy grossmeysterlar")
    )

    fun getCurrentLeague(xp: Int): LeagueInfo {
        return LEAGUES.lastOrNull { xp >= it.minXp } ?: LEAGUES.first()
    }

    fun getNextLeague(xp: Int): LeagueInfo? {
        val cur = getCurrentLeague(xp)
        val idx = LEAGUES.indexOf(cur)
        return if (idx < LEAGUES.size - 1) LEAGUES[idx + 1] else null
    }

    fun getXpNeededForNextLeague(xp: Int): Int {
        val next = getNextLeague(xp) ?: return 0
        return (next.minXp - xp).coerceAtLeast(0)
    }

    fun getDivisionPlayers(currentLeague: LeagueInfo, userXp: Int, username: String): List<LeaguePlayer> {
        val players = mutableListOf<LeaguePlayer>()
        val botNames = listOf("Rustam_UZ", "Shahzod_99", "Farrux_King", "Dilnoza_Star", "Bobur_Pro", "Nodir_Chess", "Akmal_Champion")

        val baseMin = currentLeague.minXp
        val baseMax = if (currentLeague.maxXp == Int.MAX_VALUE) currentLeague.minXp + 2000 else currentLeague.maxXp

        // Generate bracket of 8 players in this league
        val generatedXps = mutableListOf<Int>()
        for (i in 0 until 7) {
            generatedXps.add((baseMin + (baseMax - baseMin) * Math.random()).toInt())
        }
        generatedXps.add(userXp)
        generatedXps.sortDescending()

        for (i in generatedXps.indices) {
            val pXp = generatedXps[i]
            val isMe = (pXp == userXp)
            val name = if (isMe) username else botNames.getOrElse(i) { "Player_${i + 1}" }
            val wins = (pXp / 35).coerceAtLeast(1)
            players.add(LeaguePlayer(i + 1, name, pXp, wins, i < 3))
        }

        return players
    }
}
