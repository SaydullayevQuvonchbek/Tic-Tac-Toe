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
            tvResult.text = "🎡 Omad tilaymiz! Sovg'a aniqlanmoqda..."

            HapticHelper.performClick(context)
            SoundHelper.playRewardSound(context)

            wheelView.startSpin { item ->
                // Record free spin date
                prefs.edit().putString("last_lucky_wheel_date", today).apply()

                // Award prize
                val updatedCoins = prefs.getInt("coins", 0) + item.coinAmount
                val updatedXp = prefs.getInt("xp", 0) + item.xpAmount
                prefs.edit()
                    .putInt("coins", updatedCoins)
                    .putInt("xp", updatedXp)
                    .apply()

                tvResult.text = "🎉 TABRIKLAYMIZ: ${item.label} (${item.sublabel})!"
                tvResult.setTextColor(Color.parseColor("#10B981"))

                // Confetti Explosion
                ConfettiView.show(rootLayout)

                btnSpin.text = "QABUL QILISH! 🎁"
                btnSpin.isEnabled = true
                btnClose.isEnabled = true

                btnSpin.setOnClickListener {
                    onRewardClaimed(item.coinAmount, item.xpAmount)
                    dismiss()
                }
            }
        }
    }
}
