package com.example.tictactoe

import android.content.Context
import com.example.tictactoe.network.*
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

object FriendsManager {

    private const val PREFS_NAME = "TicTacToePrefs"
    private const val KEY_FRIENDS = "saved_friends_list_json"

    data class Friend(
        val id: String,
        val user_id: Int? = null,
        val username: String,
        val level: Int,
        val xp: Int,
        val wins: Int,
        val isOnline: Boolean,
        val lastSeen: String
    )

    fun getFriends(context: Context): List<Friend> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_FRIENDS, null) ?: return emptyList()

        // Clean up legacy test friends if they were stored in SharedPreferences
        if (jsonStr.contains("Shohruh_Pro") || jsonStr.contains("Malika_Queen") || jsonStr.contains("Jasur_Master")) {
            prefs.edit().remove(KEY_FRIENDS).apply()
            return emptyList()
        }

        return try {
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<Friend>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    Friend(
                        id = obj.getString("id"),
                        user_id = if (obj.has("user_id")) obj.getInt("user_id") else null,
                        username = obj.getString("username"),
                        level = obj.optInt("level", 1),
                        xp = obj.optInt("xp", 0),
                        wins = obj.optInt("wins", 0),
                        isOnline = obj.optBoolean("isOnline", false),
                        lastSeen = obj.optString("lastSeen", "Yaqinda")
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun fetchFriendsFromServer(context: Context, onResult: (List<Friend>) -> Unit) {
        ApiClient.instance.getFriends().enqueue(object : Callback<FriendsListResponse> {
            override fun onResponse(call: Call<FriendsListResponse>, response: Response<FriendsListResponse>) {
                val body = response.body()
                if (response.isSuccessful && body != null && body.success) {
                    val serverFriends = body.friends.map { f ->
                        Friend(
                            id = f.id ?: "f_${f.user_id}",
                            user_id = f.user_id,
                            username = f.username,
                            level = f.level,
                            xp = f.xp,
                            wins = f.wins,
                            isOnline = f.is_online,
                            lastSeen = f.last_seen
                        )
                    }
                    saveFriends(context, serverFriends)
                    onResult(serverFriends)
                } else {
                    onResult(getFriends(context))
                }
            }

            override fun onFailure(call: Call<FriendsListResponse>, t: Throwable) {
                onResult(getFriends(context))
            }
        })
    }

    fun searchUsers(query: String, onResult: (List<UserSearchDto>) -> Unit) {
        ApiClient.instance.searchUsers(query).enqueue(object : Callback<UserSearchResponse> {
            override fun onResponse(call: Call<UserSearchResponse>, response: Response<UserSearchResponse>) {
                val body = response.body()
                if (response.isSuccessful && body != null && body.success) {
                    onResult(body.users)
                } else {
                    onResult(emptyList())
                }
            }

            override fun onFailure(call: Call<UserSearchResponse>, t: Throwable) {
                onResult(emptyList())
            }
        })
    }

    fun sendFriendRequest(
        friendId: Int? = null,
        username: String? = null,
        onResult: (success: Boolean, message: String?) -> Unit
    ) {
        val req = FriendRequestSend(friend_id = friendId, username = username)
        ApiClient.instance.sendFriendRequest(req).enqueue(object : Callback<FriendRequestResponse> {
            override fun onResponse(call: Call<FriendRequestResponse>, response: Response<FriendRequestResponse>) {
                val body = response.body()
                if (response.isSuccessful && body != null && body.success) {
                    onResult(true, body.message)
                } else {
                    onResult(false, body?.message ?: "Xatolik: ${response.code()}")
                }
            }

            override fun onFailure(call: Call<FriendRequestResponse>, t: Throwable) {
                onResult(false, t.localizedMessage)
            }
        })
    }

    fun addFriend(context: Context, username: String): Boolean {
        val trimmed = username.trim()
        if (trimmed.isEmpty()) return false

        val friends = getFriends(context).toMutableList()
        if (friends.any { it.username.equals(trimmed, ignoreCase = true) }) {
            return false // Already exists
        }

        val newFriend = Friend(
            id = "f_${System.currentTimeMillis()}",
            username = trimmed,
            level = 1,
            xp = 0,
            wins = 0,
            isOnline = false,
            lastSeen = "Kutilmoqda"
        )
        friends.add(0, newFriend)
        saveFriends(context, friends)

        // Also try sending request to server asynchronously
        sendFriendRequest(username = trimmed) { _, _ -> }
        return true
    }

    fun challengeFriend(
        friendId: Int,
        gameType: String,
        onResult: (success: Boolean, roomCode: String?, message: String?) -> Unit
    ) {
        val req = FriendChallengeRequest(friendId, gameType)
        ApiClient.instance.challengeFriend(req).enqueue(object : Callback<FriendChallengeResponse> {
            override fun onResponse(call: Call<FriendChallengeResponse>, response: Response<FriendChallengeResponse>) {
                val body = response.body()
                if (response.isSuccessful && body != null && body.success && !body.room_code.isNullOrEmpty()) {
                    onResult(true, body.room_code, null)
                } else {
                    onResult(false, null, body?.message ?: "Server xatosi: ${response.code()}")
                }
            }

            override fun onFailure(call: Call<FriendChallengeResponse>, t: Throwable) {
                onResult(false, null, t.localizedMessage)
            }
        })
    }

    fun saveFriends(context: Context, friends: List<Friend>) {
        val arr = JSONArray()
        for (f in friends) {
            val obj = JSONObject().apply {
                put("id", f.id)
                if (f.user_id != null) put("user_id", f.user_id)
                put("username", f.username)
                put("level", f.level)
                put("xp", f.xp)
                put("wins", f.wins)
                put("isOnline", f.isOnline)
                put("lastSeen", f.lastSeen)
            }
            arr.put(obj)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_FRIENDS, arr.toString())
            .apply()
    }
}
