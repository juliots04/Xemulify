package com.example.xemulify.input

import android.content.Context
import android.view.KeyEvent
import org.json.JSONArray
import org.json.JSONObject

/**
 * Guarda la lista de botones táctiles y sus opciones, en SharedPreferences (JSON).
 * La comparte la pantalla de configuración y el servicio de accesibilidad
 * ([TouchMapperService]), que corren en el mismo proceso.
 */
class TouchMappingStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getMappings(): MutableList<TouchMapping> {
        val json = prefs.getString(KEY_MAPPINGS, null) ?: return mutableListOf()
        return runCatching {
            val arr = JSONArray(json)
            MutableList(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                TouchMapping(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    keyboardKeyCode = o.optInt("kbd", KeyEvent.KEYCODE_UNKNOWN),
                    gamepadKeyCode = o.optInt("gpd", KeyEvent.KEYCODE_UNKNOWN),
                    x = o.optDouble("x", 0.5).toFloat(),
                    y = o.optDouble("y", 0.5).toFloat(),
                    hold = o.optBoolean("hold", false),
                )
            }
        }.getOrDefault(mutableListOf())
    }

    fun saveMappings(list: List<TouchMapping>) {
        val arr = JSONArray()
        for (m in list) {
            arr.put(
                JSONObject()
                    .put("id", m.id)
                    .put("name", m.name)
                    .put("kbd", m.keyboardKeyCode)
                    .put("gpd", m.gamepadKeyCode)
                    .put("x", m.x.toDouble())
                    .put("y", m.y.toDouble())
                    .put("hold", m.hold)
            )
        }
        prefs.edit().putString(KEY_MAPPINGS, arr.toString()).apply()
        TouchMapperService.mappingsDirty = true
    }

    fun add(mapping: TouchMapping) {
        val list = getMappings(); list.add(mapping); saveMappings(list)
    }

    fun update(id: String, transform: (TouchMapping) -> TouchMapping) {
        val list = getMappings()
        val i = list.indexOfFirst { it.id == id }
        if (i >= 0) { list[i] = transform(list[i]); saveMappings(list) }
    }

    fun delete(id: String) {
        val list = getMappings()
        if (list.removeAll { it.id == id }) saveMappings(list)
    }

    var showOverlay: Boolean
        get() = prefs.getBoolean(KEY_SHOW_OVERLAY, true)
        set(value) {
            prefs.edit().putBoolean(KEY_SHOW_OVERLAY, value).apply()
            TouchMapperService.mappingsDirty = true
        }

    companion object {
        private const val PREFS_NAME = "xemulify_touch"
        private const val KEY_MAPPINGS = "mappings"
        private const val KEY_SHOW_OVERLAY = "show_overlay"

        /** Genera un id estable sin usar Math.random (no disponible aquí): timestamp + tamaño. */
        fun newId(seed: Int): String = "m${seed}_${seed * 31 + 7}"
    }
}
