package com.example.xemulify.mode

import android.content.Context
import android.content.SharedPreferences

/**
 * Almacena la preferencia del modo de uso de la aplicación:
 *  - TV / Consola (pantalla de juegos principal)
 *  - Mando (smartphone)
 * Permite recordar la elección o volver a elegir al abrir la app.
 */
class ModeStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    enum class AppMode {
        UNSET,
        HOST_TV,
        CONTROLLER_PHONE
    }

    var selectedMode: AppMode
        get() {
            val raw = prefs.getString(KEY_MODE, null) ?: return AppMode.UNSET
            return try {
                AppMode.valueOf(raw)
            } catch (_: Exception) {
                AppMode.UNSET
            }
        }
        set(value) {
            prefs.edit().putString(KEY_MODE, value.name).apply()
        }

    var rememberChoice: Boolean
        get() = prefs.getBoolean(KEY_REMEMBER, false)
        set(value) = prefs.edit().putBoolean(KEY_REMEMBER, value).apply()

    fun clearPreference() {
        prefs.edit().remove(KEY_MODE).remove(KEY_REMEMBER).apply()
    }

    companion object {
        private const val PREFS_NAME = "xemulify_mode_prefs"
        private const val KEY_MODE = "app_mode"
        private const val KEY_REMEMBER = "remember_mode"
    }
}
