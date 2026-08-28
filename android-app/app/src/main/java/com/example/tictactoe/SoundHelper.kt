package com.example.tictactoe

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator

object SoundHelper {

    private var toneGen: ToneGenerator? = null

    init {
        try {
            toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 70)
        } catch (_: Exception) {}
    }

    fun isSoundEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("sound_enabled", true) && prefs.getBoolean("sound_effects_enabled", true)
    }

    fun setSoundEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("sound_enabled", enabled)
            .putBoolean("sound_effects_enabled", enabled)
            .apply()
    }

    /**
     * Crisp high-frequency pop for moves / taps
     */
    fun playMoveSound(context: Context) {
        if (!isSoundEnabled(context)) return
        try {
            toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP, 35)
        } catch (_: Exception) {}
    }

    /**
     * Satisfying mid-frequency snap for piece capture or big move
     */
    fun playCaptureSound(context: Context) {
        if (!isSoundEnabled(context)) return
        try {
            toneGen?.startTone(ToneGenerator.TONE_PROP_ACK, 70)
        } catch (_: Exception) {}
    }

    /**
     * Joyful victory fanfare sequence using valid DTMF tones
     */
    fun playVictorySound(context: Context) {
        if (!isSoundEnabled(context)) return
        Thread {
            try {
                toneGen?.startTone(ToneGenerator.TONE_DTMF_1, 100)
                Thread.sleep(120)
                toneGen?.startTone(ToneGenerator.TONE_DTMF_5, 120)
                Thread.sleep(140)
                toneGen?.startTone(ToneGenerator.TONE_DTMF_9, 220)
            } catch (_: Exception) {}
        }.start()
    }

    /**
     * Coin / reward clinking sound
     */
    fun playRewardSound(context: Context) {
        if (!isSoundEnabled(context)) return
        Thread {
            try {
                toneGen?.startTone(ToneGenerator.TONE_DTMF_8, 80)
                Thread.sleep(90)
                toneGen?.startTone(ToneGenerator.TONE_DTMF_A, 150)
            } catch (_: Exception) {}
        }.start()
    }
}
