package com.example.tictactoe

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object FriendsManager {

    private const val PREFS_NAME = "TicTacToePrefs"
    private const val KEY_FRIENDS = "saved_friends_list_json"

    data class Friend(
        val id: String,
        val username: String,
        val level: Int,
        val xp: Int,
        val wins: Int,
        val isOnline: Boolean,
        val lastSeen: String
    )

    private val DEFAULT_FRIENDS = listOf(
        Friend("f1", "Shohruh_Pro", 8, 2400, 62, true, "Hozir onlayn"),
        Friend("f2", "Malika_Queen", 6, 1750, 45, true, "Hozir onlayn"),
        Friend("f3", "Jasur_Master", 5, 1200, 31, false, "1 soat oldin"),
        Friend("f4", "Sardor_Gamer", 4, 850, 22, true, "Hozir onlayn"),
        Friend("f5", "Aziza_Chess", 3, 520, 14, false, "Kecha")
    )

    fun getFriends(context: Context): List<Friend> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_FRIENDS, null) ?: return DEFAULT_FRIENDS

        return try {
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<Friend>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    Friend(
                        id = obj.getString("id"),
                        username = obj.getString("username"),
                        level = obj.getInt("level"),
                        xp = obj.getInt("xp"),
                        wins = obj.getInt("wins"),
                        isOnline = obj.getBoolean("isOnline"),
                        lastSeen = obj.getString("lastSeen")
                    )
                )
            }
            if (list.isEmpty()) DEFAULT_FRIENDS else list
        } catch (e: Exception) {
            DEFAULT_FRIENDS
        }
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
            level = (1..6).random(),
            xp = (200..1800).random(),
            wins = (5..40).random(),
            isOnline = true,
            lastSeen = "Hozir onlayn"
        )
        friends.add(0, newFriend)
        saveFriends(context, friends)
        return true
    }

    private fun saveFriends(context: Context, friends: List<Friend>) {
        val arr = JSONArray()
        for (f in friends) {
            val obj = JSONObject().apply {
                put("id", f.id)
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
