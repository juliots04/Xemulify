package com.example.xemulify.remote

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Gestor universal de respuesta háptica y vibración con compatibilidad para cualquier
 * modelo y versión de Android (API 21 a API 36+).
 */
class HapticManager(private val context: Context) {

    enum class HapticStrength(val displayName: String, val level: Int) {
        OFF("Desactivada", 0),
        LIGHT("Suave", 1),
        MEDIUM("Media / Realista", 2),
        STRONG("Fuerte", 3)
    }

    var strength: HapticStrength = HapticStrength.MEDIUM

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    /**
     * Clic táctil ultrarrápido al presionar o soltar un botón físico virtual (✖, ⭕, D-pad, etc.).
     */
    fun performButtonClick() {
        if (strength == HapticStrength.OFF) return
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return

        val durationMs = when (strength) {
            HapticStrength.LIGHT -> 8L
            HapticStrength.MEDIUM -> 15L
            HapticStrength.STRONG -> 28L
            else -> 0L
        }

        val amplitude = when (strength) {
            HapticStrength.LIGHT -> 80
            HapticStrength.MEDIUM -> 180
            HapticStrength.STRONG -> 255
            else -> 0
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ soporta composición háptica y efectos predefinidos
                val effect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                vib.vibrate(effect)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Android 8.0 - 9.0 con amplitud personalizada
                val effect = if (vib.hasAmplitudeControl()) {
                    VibrationEffect.createOneShot(durationMs, amplitude)
                } else {
                    VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                }
                vib.vibrate(effect)
            } else {
                // Android 5.0 - 7.1 legacy
                @Suppress("DEPRECATION")
                vib.vibrate(durationMs)
            }
        } catch (_: Exception) {
            // Fallback silencioso seguro
            try {
                @Suppress("DEPRECATION")
                vib.vibrate(durationMs)
            } catch (_: Exception) {}
        }
    }

    /**
     * Vibración al activar el modo Analógico o cambiar configuraciones.
     */
    fun performToggleFeedback() {
        if (strength == HapticStrength.OFF) return
        val vib = vibrator ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val timings = longArrayOf(0, 15, 40, 20)
                val amplitudes = intArrayOf(0, 160, 0, 220)
                vib.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(longArrayOf(0, 20, 40, 25), -1)
            }
        } catch (_: Exception) {
            performButtonClick()
        }
    }

    /**
     * Rumble recibido desde el juego de PS1 (choques, disparos, explosiones).
     */
    fun performGameRumble(durationMs: Long, strongMotor: Boolean) {
        if (strength == HapticStrength.OFF) return
        val vib = vibrator ?: return
        val amp = if (strongMotor) 255 else 120
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(VibrationEffect.createOneShot(durationMs, amp))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(durationMs)
            }
        } catch (_: Exception) {}
    }
}
