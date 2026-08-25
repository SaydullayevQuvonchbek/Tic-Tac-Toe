package com.example.tictactoe

import android.content.Context
import android.content.Intent

object ShareInviteHelper {

    fun shareRoomCode(context: Context, gameTitle: String, roomCode: String) {
        val shareMessage = """
            🎮 Kel, men bilan "$gameTitle" o'yinida kuch sinashamiz!
            
            🔑 Xona Kodi: $roomCode
            
            Ilovani oching, "$gameTitle" -> Online bo'limiga kiring va kodni yozing! 🚀
        """.trimIndent()

        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, shareMessage)
            type = "text/plain"
        }

        val shareIntent = Intent.createChooser(sendIntent, "Do'stlarga xona kodini ulashish:")
        context.startActivity(shareIntent)
    }
}
