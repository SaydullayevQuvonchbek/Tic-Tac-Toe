package com.example.tictactoe

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import com.example.tictactoe.network.ApiClient
import com.example.tictactoe.network.MatchmakingRequest
import com.example.tictactoe.network.MatchmakingResponse
import com.example.tictactoe.network.RoomCreateRequest
import com.example.tictactoe.network.RoomCreateResponse
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

object MatchmakingHelper {

    private val BOT_NAMES = listOf("Sardor_Pro", "Shohjahon_77", "Alex_King", "Dilshod_99", "Elena_Chess", "Bekzod_Gamer", "Ulugbek_UZ")

    fun startQuickMatch(
        context: Context,
        gameType: String,
        boardSize: Int = 3,
        onMatched: (roomCode: String, isHost: Boolean, opponentName: String, isBot: Boolean) -> Unit
    ) {
        val dialog = Dialog(context).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(R.layout.dialog_quick_match)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setCancelable(false)
        }

        val tvStatus = dialog.findViewById<TextView>(R.id.tvMatchStatus)
        val tvSub = dialog.findViewById<TextView>(R.id.tvMatchSub)
        val btnCancel = dialog.findViewById<Button>(R.id.btnCancelMatch)

        var isCancelled = false
        val handler = Handler(Looper.getMainLooper())

        btnCancel.setOnClickListener {
            isCancelled = true
            handler.removeCallbacksAndMessages(null)
            dialog.dismiss()
        }

        dialog.show()
        HapticHelper.performClick(context)
        SoundHelper.playMoveSound(context)

        val sharedPref = context.getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
        val userId = sharedPref.getInt("user_id", -1)

        tvStatus.text = "⚡ Searching for Opponent..."
        tvSub.text = "Scanning online lobby for $gameType..."

        // 1. Call server Matchmaking API
        ApiClient.instance.findMatch(MatchmakingRequest(userId, gameType))
            .enqueue(object : Callback<MatchmakingResponse> {
                override fun onResponse(call: Call<MatchmakingResponse>, response: Response<MatchmakingResponse>) {
                    if (isCancelled) return

                    if (response.isSuccessful && response.body() != null) {
                        val body = response.body()!!
                        val roomCode = body.room_code ?: ""
                        val isHost = body.is_host ?: true
                        val oppName = body.opponent?.username ?: BOT_NAMES.random()

                        tvStatus.text = "🎉 Match Found!"
                        tvSub.text = "Opponent: $oppName (Connecting...)"
                        HapticHelper.performHeavyImpact(context)
                        SoundHelper.playRewardSound(context)

                        handler.postDelayed({
                            if (!isCancelled) {
                                dialog.dismiss()
                                onMatched(roomCode, isHost, oppName, false)
                            }
                        }, 1200)
                    } else {
                        // Fallback to fast room match simulation
                        fallbackMatch(context, gameType, boardSize, userId, dialog, tvStatus, tvSub, handler, isCancelled, onMatched)
                    }
                }

                override fun onFailure(call: Call<MatchmakingResponse>, t: Throwable) {
                    if (isCancelled) return
                    fallbackMatch(context, gameType, boardSize, userId, dialog, tvStatus, tvSub, handler, isCancelled, onMatched)
                }
            })
    }

    private fun fallbackMatch(
        context: Context,
        gameType: String,
        boardSize: Int,
        userId: Int,
        dialog: Dialog,
        tvStatus: TextView,
        tvSub: TextView,
        handler: Handler,
        isCancelled: Boolean,
        onMatched: (roomCode: String, isHost: Boolean, opponentName: String, isBot: Boolean) -> Unit
    ) {
        if (isCancelled) return

        // Create standard room on server
        ApiClient.instance.createRoom(RoomCreateRequest(userId, boardSize, false, gameType))
            .enqueue(object : Callback<RoomCreateResponse> {
                override fun onResponse(call: Call<RoomCreateResponse>, response: Response<RoomCreateResponse>) {
                    if (isCancelled) return
                    val roomCode = if (response.isSuccessful) response.body()?.room_code ?: "QM${(1000..9999).random()}" else "QM${(1000..9999).random()}"
                    val oppName = BOT_NAMES.random()

                    tvStatus.text = "🎉 Match Found!"
                    tvSub.text = "Opponent: $oppName (Entering arena...)"
                    HapticHelper.performHeavyImpact(context)
                    SoundHelper.playRewardSound(context)

                    handler.postDelayed({
                        if (!isCancelled) {
                            dialog.dismiss()
                            onMatched(roomCode, true, oppName, true)
                        }
                    }, 1400)
                }

                override fun onFailure(call: Call<RoomCreateResponse>, t: Throwable) {
                    if (isCancelled) return
                    val roomCode = "QM${(1000..9999).random()}"
                    val oppName = BOT_NAMES.random()

                    tvStatus.text = "🎉 Match Found!"
                    tvSub.text = "Opponent: $oppName (Entering arena...)"
                    HapticHelper.performHeavyImpact(context)
                    SoundHelper.playRewardSound(context)

                    handler.postDelayed({
                        if (!isCancelled) {
                            dialog.dismiss()
                            onMatched(roomCode, true, oppName, true)
                        }
                    }, 1400)
                }
            })
    }
}
