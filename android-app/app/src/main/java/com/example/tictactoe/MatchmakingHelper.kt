package com.example.tictactoe

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import com.example.tictactoe.network.ApiClient
import com.example.tictactoe.network.MatchmakingRequest
import com.example.tictactoe.network.MatchmakingResponse
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

object MatchmakingHelper {

    fun getGameTitle(gameType: String): String {
        return when (gameType.lowercase()) {
            "tictactoe", "tic_tac_toe" -> "Tic Tac Toe"
            "chess" -> "Shaxmat"
            "checkers" -> "Shashka"
            "connect4" -> "Connect 4"
            "gomoku" -> "Gomoku"
            "dots_and_boxes" -> "Dots & Boxes"
            "durak" -> "Durak"
            else -> gameType.replace("_", " ").replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
    }

    fun startQuickMatch(
        context: Context,
        gameType: String,
        boardSize: Int = 3,
        onMatched: (roomCode: String, isHost: Boolean, opponentName: String, isBot: Boolean) -> Unit
    ) {
        val activity = context as? Activity
        if (activity != null && (activity.isFinishing || activity.isDestroyed)) return

        val dialog = Dialog(context).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(R.layout.dialog_quick_match)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setCancelable(false)
        }

        val pbMatch = dialog.findViewById<ProgressBar>(R.id.pbMatch)
        val tvStatus = dialog.findViewById<TextView>(R.id.tvMatchStatus)
        val tvSub = dialog.findViewById<TextView>(R.id.tvMatchSub)
        val layoutNotFound = dialog.findViewById<View>(R.id.layoutNotFoundActions)
        val btnPlayWithBot = dialog.findViewById<TextView>(R.id.btnPlayWithBot)
        val btnRetryMatch = dialog.findViewById<TextView>(R.id.btnRetryMatch)
        val btnCancel = dialog.findViewById<Button>(R.id.btnCancelMatch)

        var isCancelled = false
        val handler = Handler(Looper.getMainLooper())
        val gameTitle = getGameTitle(gameType)

        fun dismissSafe() {
            val act = context as? Activity
            if (act == null || (!act.isFinishing && !act.isDestroyed)) {
                try { dialog.dismiss() } catch (_: Exception) {}
            }
        }

        btnCancel.setOnClickListener {
            isCancelled = true
            handler.removeCallbacksAndMessages(null)
            dismissSafe()
        }

        btnPlayWithBot.setOnClickListener {
            isCancelled = true
            handler.removeCallbacksAndMessages(null)
            dismissSafe()
            HapticHelper.performClick(context)
            onMatched("BOT_${(1000..9999).random()}", true, "AI Bot", true)
        }

        fun showNoOpponentState() {
            if (isCancelled) return
            pbMatch.visibility = View.GONE
            tvStatus.text = "🔍 Raqib topilmadi"
            tvSub.text = "Hozircha $gameTitle bo'yicha faol onlayn o'yinchi yo'q.\nSun'iy intellekt (Bot) bilan kuch sinashasizmi?"
            layoutNotFound.visibility = View.VISIBLE
            btnPlayWithBot.text = "🤖 $gameTitle BOT BILAN O'YNASH"
            btnCancel.text = "Bekor qilish"
        }

        fun executeSearch() {
            pbMatch.visibility = View.VISIBLE
            layoutNotFound.visibility = View.GONE
            tvStatus.text = "⚡ Raqib qidirilmoqda..."
            tvSub.text = "$gameTitle lobbisida o'yinchilar qidirilmoqda..."
            btnCancel.text = "Bekor qilish"

            val sharedPref = context.getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
            val userId = sharedPref.getInt("user_id", -1)

            var searchCompleted = false

            // Timeout: if no real opponent responds within 5.5 seconds, show "No Opponent"
            handler.postDelayed({
                if (!isCancelled && !searchCompleted) {
                    searchCompleted = true
                    showNoOpponentState()
                }
            }, 5500)

            ApiClient.instance.findMatch(MatchmakingRequest(userId, gameType))
                .enqueue(object : Callback<MatchmakingResponse> {
                    override fun onResponse(call: Call<MatchmakingResponse>, response: Response<MatchmakingResponse>) {
                        if (isCancelled || searchCompleted) return

                        val body = response.body()
                        val hasRealOpponent = response.isSuccessful &&
                                body != null &&
                                body.status == "success" &&
                                !body.room_code.isNullOrEmpty() &&
                                body.opponent != null

                        if (hasRealOpponent) {
                            searchCompleted = true
                            handler.removeCallbacksAndMessages(null)
                            val roomCode = body.room_code ?: ""
                            val isHost = body.is_host ?: true
                            val oppName = body.opponent?.username ?: "Player 2"

                            tvStatus.text = "🎉 Raqib topildi!"
                            tvSub.text = "Raqib: $oppName (Ulanmoqda...)"
                            HapticHelper.performHeavyImpact(context)
                            SoundHelper.playRewardSound(context)

                            handler.postDelayed({
                                if (!isCancelled) {
                                    dismissSafe()
                                    onMatched(roomCode, isHost, oppName, false)
                                }
                            }, 1200)
                        } else {
                            // No real opponent found immediately
                            searchCompleted = true
                            handler.removeCallbacksAndMessages(null)
                            showNoOpponentState()
                        }
                    }

                    override fun onFailure(call: Call<MatchmakingResponse>, t: Throwable) {
                        if (isCancelled || searchCompleted) return
                        searchCompleted = true
                        handler.removeCallbacksAndMessages(null)
                        showNoOpponentState()
                    }
                })
        }

        btnRetryMatch.setOnClickListener {
            HapticHelper.performClick(context)
            executeSearch()
        }

        dialog.show()
        HapticHelper.performClick(context)
        SoundHelper.playMoveSound(context)
        executeSearch()
    }
}
