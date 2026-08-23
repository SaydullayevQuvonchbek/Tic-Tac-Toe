package com.example.tictactoe.network

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

data class AuthRequest(val device_id: String, val username: String)
data class AuthResponse(val status: String, val user: User?)
data class User(val id: Int, val username: String, val level: Int, val xp: Int, val wins: Int, val losses: Int)

data class RoomCreateRequest(val player_id: Int, val board_size: Int, val infinity_mode: Boolean)
data class RoomCreateResponse(val status: String, val room_code: String?)

data class RoomJoinRequest(val player_id: Int, val room_code: String)
data class RoomJoinResponse(val status: String, val room_code: String?, val message: String?)

data class MoveRequest(val room_code: String, val player_id: Int, val row: Int, val col: Int, val next_turn: Int)
data class MoveResponse(val status: String)

data class MatchResultRequest(val player_id: Int, val result: String)
data class MatchResultResponse(val status: String, val xp_earned: Int, val new_total_xp: Int, val level_up: Boolean, val current_level: Int)

interface ApiService {
    @POST("users/auth")
    fun auth(@Body req: AuthRequest): Call<AuthResponse>

    @POST("room/create")
    fun createRoom(@Body req: RoomCreateRequest): Call<RoomCreateResponse>

    @POST("room/join")
    fun joinRoom(@Body req: RoomJoinRequest): Call<RoomJoinResponse>

    @POST("room/move")
    fun makeMove(@Body req: MoveRequest): Call<MoveResponse>

    @POST("match/result")
    fun matchResult(@Body req: MatchResultRequest): Call<MatchResultResponse>
}
