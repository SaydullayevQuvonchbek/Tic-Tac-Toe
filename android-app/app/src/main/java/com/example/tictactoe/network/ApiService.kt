package com.example.tictactoe.network

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

data class AuthRequest(val device_id: String, val username: String)
data class AuthResponse(val status: String, val user: User?)
data class User(val id: Int, val username: String, val level: Int, val xp: Int, val wins: Int, val losses: Int, val coins: Int, val streak_count: Int, val unlocked_games: List<String>?)

data class StoreBuyRequest(val player_id: Int, val item_id: String, val cost: Int)
data class StoreBuyResponse(val status: String, val new_coin_balance: Int?, val message: String?)

data class DailyRewardRequest(val player_id: Int)
data class DailyRewardResponse(val status: String, val reward_coins: Int?, val new_total_coins: Int?, val message: String?)

data class RoomCreateRequest(val player_id: Int, val board_size: Int, val infinity_mode: Boolean, val game_type: String? = null)
data class RoomCreateResponse(val status: String, val room_code: String?)

data class RoomJoinRequest(val player_id: Int, val room_code: String)
data class RoomJoinResponse(val status: String, val room_code: String?, val message: String?, val board_size: Int?, val infinity_mode: Boolean?, val game_type: String? = null)

data class MoveRequest(val room_code: String, val player_id: Int, val row: Int, val col: Int, val next_turn: Int)
data class MoveResponse(val status: String)

data class MatchResultRequest(val player_id: Int, val result: String)
data class MatchResultResponse(val status: String, val xp_earned: Int, val new_total_xp: Int, val level_up: Boolean, val current_level: Int)

data class GameScoreRequest(val player_id: Int, val game_type: String, val score: Int)
data class GameScoreResponse(val status: String, val xp_earned: Int, val new_total_xp: Int, val level_up: Boolean, val current_level: Int)

data class LeaderboardPlayer(val rank: Int, val username: String, val level: Int, val xp: Int, val wins: Int)
data class LeaderboardResponse(val status: String, val leaderboard: List<LeaderboardPlayer>?)

data class EmoteRequest(val room_code: String, val player_id: Int, val emote: String)
data class EmoteResponse(val status: String)

data class MatchmakingRequest(val player_id: Int, val game_type: String)
data class MatchmakingOpponent(val id: Int, val username: String, val level: Int, val avatar: String?)
data class MatchmakingResponse(val status: String, val room_code: String?, val is_host: Boolean?, val opponent: MatchmakingOpponent?)

interface ApiService {
    @GET("leaderboard")
    fun getLeaderboard(): Call<LeaderboardResponse>
    @POST("users/auth")
    fun auth(@Body req: AuthRequest): Call<AuthResponse>

    @POST("room/create")
    fun createRoom(@Body req: RoomCreateRequest): Call<RoomCreateResponse>

    @POST("room/join")
    fun joinRoom(@Body req: RoomJoinRequest): Call<RoomJoinResponse>

    @POST("room/move")
    fun makeMove(@Body req: MoveRequest): Call<MoveResponse>

    @POST("room/emote")
    fun sendEmote(@Body req: EmoteRequest): Call<EmoteResponse>

    @POST("matchmaking/find")
    fun findMatch(@Body req: MatchmakingRequest): Call<MatchmakingResponse>

    @POST("match/result")
    fun matchResult(@Body req: MatchResultRequest): Call<MatchResultResponse>

    @POST("game/score")
    fun submitGameScore(@Body req: GameScoreRequest): Call<GameScoreResponse>

    @POST("users/daily-reward")
    fun claimDailyReward(@Body req: DailyRewardRequest): Call<DailyRewardResponse>

    @POST("store/buy")
    fun buyItem(@Body req: StoreBuyRequest): Call<StoreBuyResponse>
}
