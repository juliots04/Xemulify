package com.example.xemulify.input

import android.view.KeyEvent
import com.swordfish.libretrodroid.GLRetroView

/**
 * Traduce las pulsaciones físicas (teclado o mando) en entradas para el core de PS1.
 *
 *  - Los botones normales se envían con `sendKeyEvent(...)` usando su keycode de mando virtual.
 *  - La cruceta (D-pad) se envía con `sendMotionEvent(MOTION_SOURCE_DPAD, x, y)`, combinando
 *    el estado de las cuatro direcciones para permitir diagonales.
 */
class PsxInputTranslator(
    private val retroView: GLRetroView,
    @Volatile private var mapping: Map<Int, PsxButton>,
) {
    private var up = false
    private var down = false
    private var left = false
    private var right = false

    /** Actualiza el mapeo (p. ej. al volver de la pantalla de configuración de teclas). */
    fun updateMapping(newMapping: Map<Int, PsxButton>) {
        mapping = newMapping
    }

    /**
     * Procesa un [KeyEvent]. Devuelve `true` si la tecla estaba asignada a un botón de PS1
     * (y por tanto debe consumirse), o `false` si no nos interesa.
     */
    fun handleKeyEvent(event: KeyEvent): Boolean {
        val button = mapping[event.keyCode] ?: return false
        val pressed = event.action == KeyEvent.ACTION_DOWN

        if (button.isDpad) {
            when (button) {
                PsxButton.DPAD_UP -> up = pressed
                PsxButton.DPAD_DOWN -> down = pressed
                PsxButton.DPAD_LEFT -> left = pressed
                PsxButton.DPAD_RIGHT -> right = pressed
                else -> Unit
            }
            emitDpad()
        } else {
            val action = if (pressed) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP
            retroView.sendKeyEvent(action, button.virtualKeyCode)
        }
        return true
    }

    /** Suelta todas las entradas (cruceta a neutro y todos los botones a "arriba"). */
    fun releaseAll() {
        up = false; down = false; left = false; right = false
        emitDpad()
        for (button in PsxButton.values()) {
            if (!button.isDpad) {
                retroView.sendKeyEvent(KeyEvent.ACTION_UP, button.virtualKeyCode)
            }
        }
    }

    private fun emitDpad() {
        // En convención Android/libretro: arriba = -1 en el eje Y.
        val x = (if (right) 1f else 0f) - (if (left) 1f else 0f)
        val y = (if (down) 1f else 0f) - (if (up) 1f else 0f)
        retroView.sendMotionEvent(GLRetroView.MOTION_SOURCE_DPAD, x, y)
    }
}
