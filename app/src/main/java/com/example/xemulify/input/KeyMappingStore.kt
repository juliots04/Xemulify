package com.example.xemulify.input

import android.content.Context

/**
 * Guarda y recupera la asignación tecla-de-teclado → botón de PS1.
 *
 * Se persiste en SharedPreferences: por cada [PsxButton] se almacena el keycode físico
 * (un `KeyEvent.KEYCODE_*`) que el usuario le asignó. Si no hay valor guardado, se usa
 * [PsxButton.defaultKeyCode].
 */
class KeyMappingStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Keycode de teclado asignado a [button]. */
    fun keyboardKeyCodeFor(button: PsxButton): Int =
        prefs.getInt(prefKeyboardKey(button), button.defaultKeyboardKeyCode)

    /** Keycode de mando/D-pad asignado a [button]. */
    fun gamepadKeyCodeFor(button: PsxButton): Int =
        prefs.getInt(prefGamepadKey(button), button.defaultGamepadKeyCode)

    /** Asigna [keyCode] al teclado para [button]. */
    fun assignKeyboard(button: PsxButton, keyCode: Int) {
        prefs.edit().putInt(prefKeyboardKey(button), keyCode).apply()
    }

    /** Asigna [keyCode] al mando para [button]. */
    fun assignGamepad(button: PsxButton, keyCode: Int) {
        prefs.edit().putInt(prefGamepadKey(button), keyCode).apply()
    }

    /** Restablece todas las teclas a sus valores por defecto. */
    fun resetToDefaults() {
        prefs.edit().clear().apply()
    }

    /**
     * Construye el mapa inverso (keycode físico → botón) que usa el emulador en tiempo real.
     * Mapea tanto el código de teclado como el de mando al mismo botón de PS1.
     */
    fun buildReverseMap(): Map<Int, PsxButton> {
        val map = HashMap<Int, PsxButton>()
        for (button in PsxButton.values()) {
            map[keyboardKeyCodeFor(button)] = button
            map[gamepadKeyCodeFor(button)] = button
        }
        return map
    }

    private fun prefKeyboardKey(button: PsxButton) = "key_kbd_${button.id}"
    private fun prefGamepadKey(button: PsxButton) = "key_gpd_${button.id}"

    companion object {
        private const val PREFS_NAME = "xemulify_input_map"
    }
}

