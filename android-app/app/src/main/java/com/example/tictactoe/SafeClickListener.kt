package com.example.tictactoe

import android.os.SystemClock
import android.view.View

class SafeClickListener(
    private val interval: Long = 600L,
    private val onSafeClick: (View) -> Unit
) : View.OnClickListener {
    private var lastClickTime = 0L
    override fun onClick(v: View) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastClickTime >= interval) {
            lastClickTime = now
            onSafeClick(v)
        }
    }
}

fun View.setThrottleClickListener(interval: Long = 600L, action: (View) -> Unit) {
    setOnClickListener(SafeClickListener(interval, action))
}
