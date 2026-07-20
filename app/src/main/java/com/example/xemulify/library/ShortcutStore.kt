package com.example.xemulify.library

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Persiste los accesos directos añadidos a la biblioteca (paquete + etiqueta), en JSON. */
class ShortcutStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getShortcuts(): MutableList<AppShortcut> {
        val json = prefs.getString(KEY, null) ?: return mutableListOf()
        return runCatching {
            val arr = JSONArray(json)
            MutableList(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                AppShortcut(o.getString("pkg"), o.getString("label"))
            }
        }.getOrDefault(mutableListOf())
    }

    fun add(shortcut: AppShortcut) {
        val list = getShortcuts()
        if (list.none { it.packageName == shortcut.packageName }) {
            list.add(shortcut)
            save(list)
        }
    }

    fun remove(packageName: String) {
        val list = getShortcuts()
        if (list.removeAll { it.packageName == packageName }) save(list)
    }

    private fun save(list: List<AppShortcut>) {
        val arr = JSONArray()
        for (s in list) arr.put(JSONObject().put("pkg", s.packageName).put("label", s.label))
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "xemulify_shortcuts"
        private const val KEY = "shortcuts"
    }
}
