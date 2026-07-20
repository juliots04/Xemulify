package com.example.xemulify.input

import android.content.Context
import android.view.KeyEvent

/**
 * Guarda la asignación tecla → botón de PS1, ahora por jugador (Player 1 / Player 2).
 *
 * Cada columna de la pantalla de controles guarda un keycode por [PsxButton]:
 *  - P1 (por defecto teclas de teclado: W, C, flechas…).
 *  - P2 (por defecto sin asignar).
 *
 * Además, [buildGamepadMap] expone el mapa de botones estándar de mando (BUTTON_A, DPAD_*…),
 * que el traductor deja SIEMPRE activo para que un mando tradicional funcione sin configurar
 * nada; un 2º mando físico se rutea a P2 por su número de control.
 */
class KeyMappingStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun p1KeyCodeFor(button: PsxButton): Int =
        prefs.getInt(pref("p1", button), button.defaultKeyboardKeyCode)

    fun p2KeyCodeFor(button: PsxButton): Int =
        prefs.getInt(pref("p2", button), KeyEvent.KEYCODE_UNKNOWN)

    /** Botón de mando/D-pad asignado (por defecto el estándar → mando tradicional plug-and-play). */
    fun gamepadKeyCodeFor(button: PsxButton): Int =
        prefs.getInt(pref("gp", button), button.defaultGamepadKeyCode)

    fun assignP1(button: PsxButton, keyCode: Int) =
        prefs.edit().putInt(pref("p1", button), keyCode).apply()

    fun assignP2(button: PsxButton, keyCode: Int) =
        prefs.edit().putInt(pref("p2", button), keyCode).apply()

    fun assignGamepad(button: PsxButton, keyCode: Int) =
        prefs.edit().putInt(pref("gp", button), keyCode).apply()

    fun resetToDefaults() = prefs.edit().clear().apply()

    /** Teclas de P1 → botón (para el puerto 0). Ignora las no asignadas. */
    fun buildP1Map(): Map<Int, PsxButton> {
        val map = HashMap<Int, PsxButton>()
        for (button in PsxButton.values()) {
            val code = p1KeyCodeFor(button)
            if (code != KeyEvent.KEYCODE_UNKNOWN) map[code] = button
        }
        return map
    }

    /** Teclas de P2 → botón (para el puerto 1). Ignora las no asignadas. */
    fun buildP2Map(): Map<Int, PsxButton> {
        val map = HashMap<Int, PsxButton>()
        for (button in PsxButton.values()) {
            val code = p2KeyCodeFor(button)
            if (code != KeyEvent.KEYCODE_UNKNOWN) map[code] = button
        }
        return map
    }

    /** Botones de mando → botón (columna Mando, editable; default = estándar plug-and-play). */
    fun buildGamepadMap(): Map<Int, PsxButton> {
        val map = HashMap<Int, PsxButton>()
        for (button in PsxButton.values()) {
            val code = gamepadKeyCodeFor(button)
            if (code != KeyEvent.KEYCODE_UNKNOWN) map[code] = button
        }
        return map
    }

    private fun pref(player: String, button: PsxButton) = "key_${player}_${button.id}"

    companion object {
        private const val PREFS_NAME = "xemulify_input_map"
    }
}
