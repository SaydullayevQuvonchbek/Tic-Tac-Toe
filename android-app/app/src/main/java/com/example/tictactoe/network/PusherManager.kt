package com.example.tictactoe.network

import android.util.Log
import com.pusher.client.Pusher
import com.pusher.client.PusherOptions
import com.pusher.client.channel.Channel
import com.pusher.client.channel.PusherEvent
import com.pusher.client.connection.ConnectionEventListener
import com.pusher.client.connection.ConnectionState
import com.pusher.client.connection.ConnectionStateChange

object PusherManager {
    private const val TAG = "PusherManager"
    private const val APP_KEY = "e0643eb32b8f5c11a0a3"
    private const val CLUSTER = "eu"
    
    private var pusher: Pusher? = null
    private var activeChannel: Channel? = null
    private var currentRoomCode: String? = null

    fun connect() {
        if (pusher == null) {
            val options = PusherOptions().setCluster(CLUSTER)
            pusher = Pusher(APP_KEY, options)
        }

        val state = pusher?.connection?.state
        if (state == null || state == ConnectionState.DISCONNECTED || state == ConnectionState.DISCONNECTING) {
            Log.d(TAG, "Initiating Pusher connection... Current state: $state")
            pusher?.connect(object : ConnectionEventListener {
                override fun onConnectionStateChange(change: ConnectionStateChange) {
                    Log.d(TAG, "Pusher state changed: ${change.previousState} -> ${change.currentState}")
                }
                override fun onError(message: String, code: String?, e: Exception?) {
                    Log.e(TAG, "Pusher error: $message (code: $code)", e)
                }
            }, ConnectionState.ALL)
        }
    }

    fun subscribeToRoom(
        roomCode: String,
        onGameStarted: (String) -> Unit = {},
        onMoveMade: (String) -> Unit = {},
        onOpponentLeft: () -> Unit = {},
        onEmoteReceived: (String) -> Unit = {}
    ) {
        connect()

        val channelName = "game.$roomCode"
        Log.d(TAG, "Subscribing to channel: $channelName")

        // If switching rooms, unsubscribe previous channel
        if (currentRoomCode != null && currentRoomCode != roomCode) {
            unsubscribeFromRoom(currentRoomCode!!)
        }
        currentRoomCode = roomCode

        try {
            // Check if already subscribed
            val existing = pusher?.getChannel(channelName)
            if (existing != null && existing.isSubscribed) {
                Log.d(TAG, "Channel $channelName already subscribed. Unsubscribing first.")
                pusher?.unsubscribe(channelName)
            }

            activeChannel = pusher?.subscribe(channelName)

            // Global listener to capture ANY event regardless of Laravel class name or naming convention
            activeChannel?.bindGlobal { event: PusherEvent ->
                val eventName = event.eventName
                val data = event.data ?: "{}"
                Log.d(TAG, "Received Pusher Event [$eventName] on channel [$channelName]: $data")

                val normalizedName = eventName.lowercase()
                when {
                    normalizedName.contains("start") || normalizedName.contains("rematch") || normalizedName.contains("restart") || normalizedName.contains("game_started") -> {
                        Log.d(TAG, "Dispatched -> onGameStarted")
                        onGameStarted(data)
                    }
                    normalizedName.contains("emote") -> {
                        Log.d(TAG, "Dispatched -> onEmoteReceived")
                        onEmoteReceived(data)
                    }
                    normalizedName.contains("move") || normalizedName.contains("card") || normalizedName.contains("action") || normalizedName.contains("refill") -> {
                        Log.d(TAG, "Dispatched -> onMoveMade")
                        onMoveMade(data)
                    }
                    normalizedName.contains("disconnect") || normalizedName.contains("leave") || normalizedName.contains("left") -> {
                        Log.d(TAG, "Dispatched -> onOpponentLeft")
                        onOpponentLeft()
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to subscribe to $channelName", e)
        }
    }

    fun unsubscribeFromRoom(roomCode: String) {
        val channelName = "game.$roomCode"
        try {
            Log.d(TAG, "Unsubscribing from channel: $channelName")
            pusher?.unsubscribe(channelName)
            if (currentRoomCode == roomCode) {
                activeChannel = null
                currentRoomCode = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error unsubscribing from $channelName", e)
        }
    }
}
