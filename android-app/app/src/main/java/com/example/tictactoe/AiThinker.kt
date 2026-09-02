package com.example.tictactoe

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import java.util.concurrent.Executors

/**
 * Runs a (potentially expensive) bot search off the UI thread and delivers the
 * result back on the main thread — but only while the owning fragment's view is
 * still alive. This replaces the old `handler.postDelayed { logic.getAiMove() }`
 * pattern that ran deep minimax searches directly on the main looper (the cause
 * of the Checkers freeze).
 *
 * A small minimum delay is enforced so the bot still visibly "thinks".
 */
object AiThinker {

    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "ai-thinker").apply { priority = Thread.NORM_PRIORITY - 1 }
    }
    private val main = Handler(Looper.getMainLooper())

    fun <T> think(
        owner: Fragment,
        minVisibleMs: Long = 350L,
        compute: () -> T?,
        onResult: (T?) -> Unit
    ) {
        val start = SystemClock.elapsedRealtime()
        executor.execute {
            val result: T? = try {
                compute()
            } catch (t: Throwable) {
                t.printStackTrace()
                null
            }
            val elapsed = SystemClock.elapsedRealtime() - start
            val wait = (minVisibleMs - elapsed).coerceIn(0L, minVisibleMs)
            main.postDelayed({
                if (isAlive(owner)) {
                    try {
                        onResult(result)
                    } catch (t: Throwable) {
                        t.printStackTrace()
                    }
                }
            }, wait)
        }
    }

    private fun isAlive(owner: Fragment): Boolean = try {
        owner.isAdded && owner.view != null &&
            owner.viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    } catch (_: Exception) {
        false
    }
}
