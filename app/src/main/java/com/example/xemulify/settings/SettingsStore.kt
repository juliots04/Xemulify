package com.example.xemulify.settings

import android.content.Context
import android.view.KeyEvent

/**
 * Ajustes globales de la app persistidos en SharedPreferences.
 *
 *  - Autoguardado: si está activado, la partida se guarda automáticamente en
 *    [autosaveSlot] al pausar/salir y de forma periódica.
 *  - Avance rápido: [fastForwardKeyCode] mantenido acelera la emulación a
 *    [fastForwardSpeed] veces la velocidad normal.
 */
class SettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var autosaveEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTOSAVE_ENABLED, false)
        set(value) { prefs.edit().putBoolean(KEY_AUTOSAVE_ENABLED, value).apply() }

    var autosaveSlot: Int
        get() = prefs.getInt(KEY_AUTOSAVE_SLOT, 1)
        set(value) { prefs.edit().putInt(KEY_AUTOSAVE_SLOT, value).apply() }

    /** Pantalla estirada a 16:9 (true) o 4:3 centrado (false). */
    var aspectFill: Boolean
        get() = prefs.getBoolean(KEY_ASPECT_FILL, true)
        set(value) { prefs.edit().putBoolean(KEY_ASPECT_FILL, value).apply() }

    /** Muestra el HUD compacto de controles encima del juego. */
    var showControlsHud: Boolean
        get() = prefs.getBoolean(KEY_SHOW_HUD, false)
        set(value) { prefs.edit().putBoolean(KEY_SHOW_HUD, value).apply() }

    /** Keycode que, mantenido pulsado durante el juego, activa el avance rápido. */
    var fastForwardKeyCode: Int
        get() = prefs.getInt(KEY_FF_KEY, DEFAULT_FF_KEY)
        set(value) { prefs.edit().putInt(KEY_FF_KEY, value).apply() }

    /** Multiplicador de velocidad del avance rápido (2x, 3x, …). */
    var fastForwardSpeed: Int
        get() = prefs.getInt(KEY_FF_SPEED, DEFAULT_FF_SPEED)
        set(value) { prefs.edit().putInt(KEY_FF_SPEED, value).apply() }

    /** Tecla para guardado rápido (en la ranura de autoguardado). */
    var quickSaveKeyCode: Int
        get() = prefs.getInt(KEY_QUICK_SAVE, DEFAULT_QUICK_SAVE)
        set(value) { prefs.edit().putInt(KEY_QUICK_SAVE, value).apply() }

    /** Tecla para carga rápida (desde la ranura de autoguardado). */
    var quickLoadKeyCode: Int
        get() = prefs.getInt(KEY_QUICK_LOAD, DEFAULT_QUICK_LOAD)
        set(value) { prefs.edit().putInt(KEY_QUICK_LOAD, value).apply() }

    companion object {
        private const val PREFS_NAME = "xemulify_settings"
        private const val KEY_AUTOSAVE_ENABLED = "autosave_enabled"
        private const val KEY_AUTOSAVE_SLOT = "autosave_slot"
        private const val KEY_ASPECT_FILL = "aspect_fill"
        private const val KEY_SHOW_HUD = "show_controls_hud"
        private const val KEY_FF_KEY = "fast_forward_key"
        private const val KEY_FF_SPEED = "fast_forward_speed"
        private const val KEY_QUICK_SAVE = "quick_save_key"
        private const val KEY_QUICK_LOAD = "quick_load_key"

        const val SLOT_COUNT = 4
        val DEFAULT_FF_KEY = KeyEvent.KEYCODE_TAB
        const val DEFAULT_FF_SPEED = 2
        val DEFAULT_QUICK_SAVE = KeyEvent.KEYCODE_F5
        val DEFAULT_QUICK_LOAD = KeyEvent.KEYCODE_F7
    }
}
