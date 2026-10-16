package com.huawei.manager.newui.util

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class AntennaBeeper(private val context: Context) {

    private var toneGenerator: ToneGenerator? = null
    private var lastToneTime = 0L

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
        } catch (_: Exception) {
            toneGenerator = null
        }
    }

    fun playBeepForSinr(sinr: Float) {
        val now = System.currentTimeMillis()
        // Determine interval and tone type based on SINR quality
        val interval = when {
            sinr <= 3f -> 1200L
            sinr <= 6f -> 700L
            sinr <= 10f -> 400L
            else -> 200L
        }

        if (now - lastToneTime >= interval) {
            lastToneTime = now
            val toneType = when {
                sinr <= 3f -> ToneGenerator.TONE_PROP_BEEP
                sinr <= 6f -> ToneGenerator.TONE_PROP_BEEP2
                sinr <= 10f -> ToneGenerator.TONE_PROP_ACK
                else -> ToneGenerator.TONE_CDMA_HIGH_L
            }
            try {
                toneGenerator?.startTone(toneType, 80)
            } catch (_: Exception) {}
        }
    }

    fun vibratePeak() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(80)
                }
            }
        } catch (_: Exception) {}
    }

    fun release() {
        try {
            toneGenerator?.release()
        } catch (_: Exception) {}
        toneGenerator = null
    }
}
