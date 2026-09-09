package com.example.tictactoe.network

// ======================== FRIENDS DTOs ========================

data class FriendDto(
    val id: String?,
    val user_id: Int?,
    val username: String,
    val level: Int = 1,
    val xp: Int = 0,
    val wins: Int = 0,
    val is_online: Boolean = false,
    val last_seen: String = "Noma'lum"
)

data class FriendsListResponse(
    val success: Boolean,
    val friends: List<FriendDto> = emptyList(),
    val message: String? = null
)

data class UserSearchDto(
    val id: Int,
    val username: String,
    val level: Int = 1,
    val xp: Int = 0,
    val friendship_status: String = "none" // "none", "pending", "friends"
)

data class UserSearchResponse(
    val success: Boolean,
    val users: List<UserSearchDto> = emptyList(),
    val message: String? = null
)

data class FriendRequestSend(
    val friend_id: Int? = null,
    val username: String? = null
)

data class FriendRequestResponse(
    val success: Boolean,
    val message: String? = null
)

data class IncomingFriendRequestDto(
    val request_id: Long,
    val from_user_id: Int,
    val username: String,
    val level: Int = 1,
    val xp: Int = 0,
    val created_at: String? = null
)

data class FriendRequestsResponse(
    val success: Boolean,
    val incoming_requests: List<IncomingFriendRequestDto> = emptyList(),
    val message: String? = null
)

data class FriendChallengeRequest(
    val friend_id: Int,
    val game_type: String
)

data class FriendChallengeResponse(
    val success: Boolean,
    val room_code: String? = null,
    val message: String? = null
)

// ======================== LEAGUES DTOs ========================

data class LeagueTierDto(
    val tier: Int,
    val name: String,
    val badge: String = "🥉",
    val color_hex: String = "#CD7F32",
    val min_xp: Int = 0,
    val max_xp: Int = 299,
    val description: String = ""
)

data class NextLeagueDto(
    val tier: Int,
    val name: String,
    val min_xp: Int
)

data class LeagueSeasonDto(
    val id: Long,
    val season_number: Int,
    val ends_at: String? = null,
    val time_left_seconds: Long = 0
)

data class MyDivisionStandingDto(
    val rank: Int,
    val weekly_xp: Int = 0,
    val is_promotion_zone: Boolean = false,
    val is_demotion_zone: Boolean = false
)

data class PendingLeagueRewardDto(
    val coins: Int = 0,
    val xp: Int = 0,
    val previous_league_name: String = ""
)

data class LeagueStatusResponse(
    val success: Boolean,
    val current_league: LeagueTierDto? = null,
    val next_league: NextLeagueDto? = null,
    val season: LeagueSeasonDto? = null,
    val my_division_standing: MyDivisionStandingDto? = null,
    val pending_reward: PendingLeagueRewardDto? = null,
    val message: String? = null
)

data class LeaguePlayerDto(
    val rank: Int,
    val user_id: Int? = null,
    val username: String,
    val weekly_xp: Int = 0,
    val wins: Int = 0,
    val is_promotion_zone: Boolean = false,
    val is_demotion_zone: Boolean = false
)

data class LeagueDivisionResponse(
    val success: Boolean,
    val league_tier: Int = 1,
    val division_id: Int = 1,
    val players: List<LeaguePlayerDto> = emptyList(),
    val message: String? = null
)

data class SeasonRewardClaimResponse(
    val success: Boolean,
    val reward_coins: Int = 0,
    val reward_xp: Int = 0,
    val new_balance: Int = 0,
    val promoted: Boolean = false,
    val new_league_tier: Int = 1,
    val message: String? = null
)
