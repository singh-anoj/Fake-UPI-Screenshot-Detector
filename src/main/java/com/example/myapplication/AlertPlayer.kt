package com.example.myapplication

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Short audible + vibration warning for a suspicious result.
 * Uses ToneGenerator on the alarm stream so it does not depend on a custom sound file.
 */
object AlertPlayer {

    @Volatile
    private var activeTone: ToneGenerator? = null

    fun play(context: Context) {
        val app = context.applicationContext
        Handler(Looper.getMainLooper()).post {
            playTone()
            vibrate(app)
        }
    }

    private fun playTone() {
        try {
            activeTone?.release()
            val generator = ToneGenerator(AudioManager.STREAM_ALARM, 90)
            activeTone = generator
            val started = generator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 2200)
            if (!started) {
                generator.startTone(ToneGenerator.TONE_PROP_BEEP, 2200)
            }
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    generator.stopTone()
                    generator.release()
                    if (activeTone === generator) activeTone = null
                } catch (_: Exception) {
                }
            }, 2500)
        } catch (_: Exception) {
            try {
                val fallback = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
                fallback.startTone(ToneGenerator.TONE_PROP_BEEP, 1800)
                Handler(Looper.getMainLooper()).postDelayed({
                    try { fallback.release() } catch (_: Exception) {}
                }, 2000)
            } catch (_: Exception) {
                // The device may have alarm/notification sounds muted.
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun vibrate(context: Context) {
        try {
            val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            if (vibrator == null || !vibrator.hasVibrator()) return
            val pattern = longArrayOf(0, 350, 130, 350, 130, 500)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                vibrator.vibrate(pattern, -1)
            }
        } catch (_: Exception) {
            // Sound alert remains available if vibration is unavailable.
        }
    }
}
