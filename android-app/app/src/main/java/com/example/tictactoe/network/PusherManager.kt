package com.example.tictactoe.network

import com.pusher.client.Pusher
import com.pusher.client.PusherOptions
import com.pusher.client.channel.Channel
import com.pusher.client.channel.PusherEvent
import com.pusher.client.connection.ConnectionEventListener
import com.pusher.client.connection.ConnectionState
import com.pusher.client.connection.ConnectionStateChange

object PusherManager {
    private const val APP_KEY = "e0643eb32b8f5c11a0a3"
    private const val CLUSTER = "eu"
    
    private var pusher: Pusher? = null
    private var channel: Channel? = null

    fun connect() {
        if (pusher == null) {
            val options = PusherOptions().setCluster(CLUSTER)
            pusher = Pusher(APP_KEY, options)
            
            pusher?.connect(object : ConnectionEventListener {
                override fun onConnectionStateChange(change: ConnectionStateChange) {
                    println("Pusher Connection State: ${change.currentState}")
                }
                override fun onError(message: String, code: String?, e: Exception?) {
                    println("Pusher Connection Error: $message")
                }
            }, ConnectionState.ALL)
        }
    }

    fun subscribeToRoom(roomCode: String, onGameStarted: (String) -> Unit, onMoveMade: (String) -> Unit, onOpponentLeft: () -> Unit) {
        val channelName = "game.$roomCode"
        channel = pusher?.subscribe(channelName)
        
        channel?.bind("game_started") { event: PusherEvent ->
            onGameStarted(event.data)
        }
        
        channel?.bind("move_made") { event: PusherEvent ->
            onMoveMade(event.data)
        }
        
        channel?.bind("opponent_disconnected") { event: PusherEvent ->
            onOpponentLeft()
        }
    }

    fun unsubscribeFromRoom(roomCode: String) {
        val channelName = "game.$roomCode"
        pusher?.unsubscribe(channelName)
        channel = null
    }
}
