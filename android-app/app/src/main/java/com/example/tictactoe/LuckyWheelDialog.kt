package com.example.tictactoe

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.tictactoe.GameEconomyManager
import com.example.tictactoe.network.AuthManager
import com.example.tictactoe.repository.EconomyRepository

class LuckyWheelDialog(
    context: Context,
    private val onRewardClaimed: (coinsEarned: Int, xpEarned: Int) -> Unit
) : Dialog(context) {

    private fun getTodayDate(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_lucky_wheel)
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        val prefs = context.getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
        val today = getTodayDate()
        val lastSpinDate = prefs.getString("last_lucky_wheel_date", "")
        val isFreeAvailable = today != lastSpinDate

        val wheelView = findViewById<LuckyWheelView>(R.id.wheelView)
        val tvResult = findViewById<TextView>(R.id.tvWheelResult)
        val btnSpin = findViewById<Button>(R.id.btnSpin)
        val btnClose = findViewById<Button>(R.id.btnCloseWheel)
        val rootLayout = findViewById<ViewGroup>(R.id.dialogWheelRoot)

        if (isFreeAvailable) {
            tvResult.text = "🎁 Bugungi 100% BEPUL sovgangiz tayyor!"
            tvResult.setTextColor(Color.parseColor("#10B981"))
            btnSpin.text = "AYLANTIRISH (BEPUL) 🎰"
            btnSpin.isEnabled = true
        } else {
            tvResult.text = "✅ Bugungi bepul sovg'angizni oldingiz! Ertaga qaytib keling 🎁"
            tvResult.setTextColor(Color.parseColor("#94A3B8"))
            btnSpin.text = "ERTAGA OCHILADI 🔒"
            btnSpin.isEnabled = false
            btnSpin.alpha = 0.6f
        }

        btnClose.setOnClickListener { dismiss() }

        btnSpin.setOnClickListener {
            val curFree = today != prefs.getString("last_lucky_wheel_date", "")
            if (!curFree) {
                Toast.makeText(context, "Sovg'a kuniga faqat 1 marta beriladi! Ertaga yana kiring 🎁", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnSpin.isEnabled = false
            btnClose.isEnabled = false
            tvResult.text = "🎡 Server bilan bog'lanilmoqda..."
            tvResult.setTextColor(Color.parseColor("#38BDF8"))

            fun performServerSpin() {
                tvResult.text = "🎡 Omad tilaymiz! Sovg'a aniqlanmoqda..."
                HapticHelper.performClick(context)
                SoundHelper.playRewardSound(context)

                EconomyRepository.spinWheel { success, segment, label, coins, xp, newBalance, nextSpinAt, message ->
                    if (!success) {
                        btnSpin.isEnabled = true
                        btnClose.isEnabled = true

                        when {
                            message == "401_NO_TOKEN" || message == "401_UNAUTHORIZED" || message?.contains("401") == true || message?.contains("Unauthenticated") == true -> {
                                AuthManager.clearToken(context)
                                // Automatically retry auth + spin
                                tvResult.text = "🔄 Qayta ulanilmoqda..."
                                tvResult.setTextColor(Color.parseColor("#38BDF8"))
                                AuthManager.ensureAuthenticated(context) { retrySuccess, retryToken ->
                                    if (retrySuccess && !retryToken.isNullOrBlank()) {
                                        performServerSpin()
                                    } else {
                                        tvResult.text = "⚠️ Serverga ulanib bo'lmadi. Qayta urinib ko'ring."
                                        tvResult.setTextColor(Color.parseColor("#EF4444"))
                                    }
                                }
                            }
                            message != null && (message.contains("429") || message.contains("kelmadi") || message.contains("allaqachon")) -> {
                                tvResult.text = message
                                tvResult.setTextColor(Color.parseColor("#F59E0B"))
                            }
                            else -> {
                                tvResult.text = message ?: "Server xatosi yuz berdi. Qayta urinib ko'ring."
                                tvResult.setTextColor(Color.parseColor("#EF4444"))
                            }
                        }
                        // CHEAT PREVENTION: DO NOT spin, DO NOT add coins, DO NOT add XP!
                        return@spinWheel
                    }

                    // STRICTLY SERVER-AUTHORITATIVE: Spin ONLY after server confirmed success and reward!
                    val targetIndex = when (segment) {
                        "COIN_50" -> 0
                        "XP_100" -> 1
                        "COIN_100" -> 2
                        "XP_250" -> 3
                        "MEGA_COIN_200" -> 4
                        "PACKAGE_150" -> 5
                        "JACKPOT_500" -> 6
                        "BONUS_XP_150" -> 7
                        else -> 0
                    }

                    wheelView.startSpin(targetIndex) { item ->
                        // Record spin date only after successful server spin
                        prefs.edit().putString("last_lucky_wheel_date", today).apply()

                        tvResult.text = "🎉 TABRIKLAYMIZ: ${label ?: item.label}!"
                        tvResult.setTextColor(Color.parseColor("#10B981"))

                        // Confetti Explosion
                        ConfettiView.show(rootLayout)

                        btnSpin.text = "QABUL QILISH! 🎁"
                        btnSpin.isEnabled = true
                        btnClose.isEnabled = true

                        btnSpin.setOnClickListener {
                            onRewardClaimed(coins, xp)
                            dismiss()
                        }
                    }
                }
            }

            // Always attempt authentication first, then spin
            fun trySpinWithAuth(isRetry: Boolean = false) {
                if (!AuthManager.hasValidToken(context)) {
                    tvResult.text = "🔄 Server bilan ulanilmoqda..."
                    tvResult.setTextColor(Color.parseColor("#38BDF8"))
                    AuthManager.ensureAuthenticated(context) { authSuccess, token ->
                        if (!authSuccess || token.isNullOrBlank()) {
                            tvResult.text = "⚠️ Serverga ulanib bo'lmadi. Internet aloqangizni tekshiring va qayta urinib ko'ring."
                            tvResult.setTextColor(Color.parseColor("#EF4444"))
                            btnSpin.isEnabled = true
                            btnClose.isEnabled = true
                            return@ensureAuthenticated
                        }
                        performServerSpin()
                    }
                } else {
                    performServerSpin()
                }
            }

            trySpinWithAuth()
        }
    }
}
