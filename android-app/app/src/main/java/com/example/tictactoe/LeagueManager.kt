package com.example.tictactoe

import com.example.tictactoe.network.ApiClient
import com.example.tictactoe.network.LeagueDivisionResponse
import com.example.tictactoe.network.LeagueStatusResponse
import com.example.tictactoe.network.SeasonRewardClaimResponse
import com.example.tictactoe.repository.EconomyRepository
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

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
        val isPromotionZone: Boolean,
        val user_id: Int? = null
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

    fun fetchLeagueStatus(onResult: (LeagueStatusResponse?) -> Unit) {
        ApiClient.instance.getLeagueStatus().enqueue(object : Callback<LeagueStatusResponse> {
            override fun onResponse(call: Call<LeagueStatusResponse>, response: Response<LeagueStatusResponse>) {
                if (response.isSuccessful && response.body() != null && response.body()!!.success) {
                    onResult(response.body())
                } else {
                    onResult(null)
                }
            }

            override fun onFailure(call: Call<LeagueStatusResponse>, t: Throwable) {
                onResult(null)
            }
        })
    }

    fun fetchDivisionPlayers(
        fallbackLeague: LeagueInfo,
        userXp: Int,
        username: String,
        onResult: (List<LeaguePlayer>) -> Unit
    ) {
        ApiClient.instance.getLeagueDivision().enqueue(object : Callback<LeagueDivisionResponse> {
            override fun onResponse(call: Call<LeagueDivisionResponse>, response: Response<LeagueDivisionResponse>) {
                val body = response.body()
                if (response.isSuccessful && body != null && body.success && body.players.isNotEmpty()) {
                    val list = body.players.map { p ->
                        LeaguePlayer(
                            rank = p.rank,
                            username = p.username,
                            xp = p.weekly_xp,
                            wins = p.wins,
                            isPromotionZone = p.is_promotion_zone,
                            user_id = p.user_id
                        )
                    }
                    onResult(list)
                } else {
                    onResult(getDivisionPlayers(fallbackLeague, userXp, username))
                }
            }

            override fun onFailure(call: Call<LeagueDivisionResponse>, t: Throwable) {
                onResult(getDivisionPlayers(fallbackLeague, userXp, username))
            }
        })
    }

    fun claimSeasonReward(
        onResult: (success: Boolean, coins: Int, xp: Int, newBalance: Int, message: String?) -> Unit
    ) {
        ApiClient.instance.claimSeasonReward().enqueue(object : Callback<SeasonRewardClaimResponse> {
            override fun onResponse(call: Call<SeasonRewardClaimResponse>, response: Response<SeasonRewardClaimResponse>) {
                val body = response.body()
                if (response.isSuccessful && body != null && body.success) {
                    EconomyRepository.saveBalance(body.new_balance)
                    onResult(true, body.reward_coins, body.reward_xp, body.new_balance, body.message)
                } else {
                    onResult(false, 0, 0, EconomyRepository.getCachedBalance(), body?.message)
                }
            }

            override fun onFailure(call: Call<SeasonRewardClaimResponse>, t: Throwable) {
                onResult(false, 0, 0, EconomyRepository.getCachedBalance(), t.localizedMessage)
            }
        })
    }
}
