package com.example.tictactoe.network

import android.content.Context
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.UUID

object AuthManager {

    private const val PREFS_NAME = "TicTacToePrefs"
    private const val KEY_AUTH_TOKEN = "auth_token"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_USERNAME = "username"

    fun getOrGenerateDeviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var devId = prefs.getString(KEY_DEVICE_ID, null)
        if (devId.isNullOrBlank()) {
            devId = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, devId).apply()
        }
        return devId
    }

    fun getAuthToken(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_AUTH_TOKEN, null)
    }

    fun hasValidToken(context: Context): Boolean {
        val token = getAuthToken(context)
        return !token.isNullOrBlank()
    }

    fun clearToken(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_AUTH_TOKEN)
            .apply()
    }

    /**
     * Ensures the device is registered with the backend API and has a valid token.
     * Asynchronously authenticates with /api/users/auth if token is absent.
     */
    fun ensureAuthenticated(
        context: Context,
        onComplete: ((success: Boolean, token: String?) -> Unit)? = null
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existingToken = prefs.getString(KEY_AUTH_TOKEN, null)
        if (!existingToken.isNullOrBlank()) {
            onComplete?.invoke(true, existingToken)
            return
        }

        val deviceId = getOrGenerateDeviceId(context)
        val username = prefs.getString(KEY_USERNAME, null) ?: ("Player_" + deviceId.take(6))

        ApiClient.instance.auth(AuthRequest(deviceId, username)).enqueue(object : Callback<AuthResponse> {
            override fun onResponse(call: Call<AuthResponse>, response: Response<AuthResponse>) {
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    val token = body.token
                    if (!token.isNullOrBlank()) {
                        prefs.edit().putString(KEY_AUTH_TOKEN, token).apply()
                    }
                    val user = body.user
                    if (user != null) {
                        prefs.edit()
                            .putInt(KEY_USER_ID, user.id)
                            .apply()
                    }
                    onComplete?.invoke(true, token)
                } else {
                    onComplete?.invoke(false, null)
                }
            }

            override fun onFailure(call: Call<AuthResponse>, t: Throwable) {
                onComplete?.invoke(false, null)
            }
        })
    }
}
