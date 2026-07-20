package com.example.xemulify.cheats

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Guarda y recupera la lista de cheats por juego, persistida en SharedPreferences
 * como JSON (una entrada por [gameId]).
 *
 * El core (PCSX-ReARMed vía LibretroDroid) recibe cada cheat con
 * `GLRetroView.setCheat(index, enabled, code)`; el [code] que espera es el conjunto
 * de líneas GameShark unidas por '+'. [normalizeCode] hace esa conversión.
 */
class CheatStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getCheats(gameId: String): MutableList<Cheat> {
        val json = prefs.getString(key(gameId), null) ?: return mutableListOf()
        return runCatching {
            val arr = JSONArray(json)
            MutableList(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                Cheat(
                    name = o.getString("name"),
                    code = o.getString("code"),
                    enabled = o.optBoolean("enabled", false),
                )
            }
        }.getOrDefault(mutableListOf())
    }

    fun saveCheats(gameId: String, cheats: List<Cheat>) {
        val arr = JSONArray()
        for (c in cheats) {
            arr.put(
                JSONObject()
                    .put("name", c.name)
                    .put("code", c.code)
                    .put("enabled", c.enabled)
            )
        }
        prefs.edit().putString(key(gameId), arr.toString()).apply()
    }

    fun addCheat(gameId: String, cheat: Cheat) {
        val list = getCheats(gameId)
        list.add(cheat)
        saveCheats(gameId, list)
    }

    fun updateCheat(gameId: String, index: Int, cheat: Cheat) {
        val list = getCheats(gameId)
        if (index in list.indices) {
            list[index] = cheat
            saveCheats(gameId, list)
        }
    }

    fun deleteCheat(gameId: String, index: Int) {
        val list = getCheats(gameId)
        if (index in list.indices) {
            list.removeAt(index)
            saveCheats(gameId, list)
        }
    }

    fun setEnabled(gameId: String, index: Int, enabled: Boolean) {
        val list = getCheats(gameId)
        if (index in list.indices) {
            list[index] = list[index].copy(enabled = enabled)
            saveCheats(gameId, list)
        }
    }

    private fun key(gameId: String) = "cheats_$gameId"

    companion object {
        private const val PREFS_NAME = "xemulify_cheats"

        /**
         * Normaliza un código GameShark multilínea al formato que espera el core:
         * líneas no vacías, sin comentarios, unidas por '+'.
         * Ej.: "30037930 0001\n800989DC 0280" → "30037930 0001+800989DC 0280".
         */
        fun normalizeCode(raw: String): String =
            raw.split('\n', '+')
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("//") && !it.startsWith("#") }
                .joinToString("+")
    }
}
