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
            tvResult.text = "🎁 1 FREE SPIN AVAILABLE TODAY!"
            tvResult.setTextColor(Color.parseColor("#10B981"))
            btnSpin.text = "SPIN FREE NOW! 🎰"
        } else {
            val userCoins = prefs.getInt("coins", 0)
            tvResult.text = "✨ Extra Spin: 50 Coins (You have: $userCoins 🪙)"
            tvResult.setTextColor(Color.parseColor("#F59E0B"))
            btnSpin.text = "SPIN (50 🪙)"
        }

        btnClose.setOnClickListener { dismiss() }

        btnSpin.setOnClickListener {
            val curFree = today != prefs.getString("last_lucky_wheel_date", "")
            val curCoins = prefs.getInt("coins", 0)

            if (!curFree && curCoins < 50) {
                Toast.makeText(context, "Not enough coins for extra spin! (Needs 50 🪙)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!curFree) {
                // Deduct 50 coins for extra spin
                prefs.edit().putInt("coins", curCoins - 50).apply()
            }

            btnSpin.isEnabled = false
            btnClose.isEnabled = false
            tvResult.text = "🎡 Good Luck! Spinning..."

            HapticHelper.performClick(context)
            SoundHelper.playRewardSound(context)

            wheelView.startSpin { item ->
                // Record free spin date
                if (curFree) {
                    prefs.edit().putString("last_lucky_wheel_date", today).apply()
                }

                // Award prize
                val updatedCoins = prefs.getInt("coins", 0) + item.coinAmount
                val updatedXp = prefs.getInt("xp", 0) + item.xpAmount
                prefs.edit()
                    .putInt("coins", updatedCoins)
                    .putInt("xp", updatedXp)
                    .apply()

                tvResult.text = "🎉 YOU WON: ${item.label} (${item.sublabel})!"
                tvResult.setTextColor(Color.parseColor("#10B981"))

                // Confetti Explosion
                ConfettiView.show(rootLayout)

                btnSpin.text = "CLAIM REWARD! 🎁"
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
