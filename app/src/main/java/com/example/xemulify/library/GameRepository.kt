package com.example.xemulify.library

import android.content.Context
import com.example.xemulify.core.CoreManager
import java.io.File

/** Escanea la carpeta de juegos importados y devuelve la lista para la biblioteca. */
object GameRepository {

    fun listGames(context: Context): List<Game> {
        val dir = CoreManager.gamesDir(context)
        return dir.listFiles()
            ?.filter { it.isDirectory }
            ?.sortedBy { it.name.lowercase() }
            ?.mapNotNull { gameDir ->
                pickLoadFile(gameDir)?.let { Game(gameDir.name, it) }
            }
            ?: emptyList()
    }

    /** Elige el fichero a cargar dentro de la carpeta de un juego, por prioridad de formato. */
    private fun pickLoadFile(dir: File): File? {
        val files = dir.listFiles()?.filter { it.isFile } ?: return null
        files.firstOrNull { it.extension.equals("m3u", true) }?.let { return it }
        files.firstOrNull { it.extension.equals("cue", true) }?.let { return it }
        files.firstOrNull { it.extension.lowercase() in setOf("pbp", "chd", "iso") }?.let { return it }
        files.firstOrNull { it.extension.lowercase() in setOf("bin", "img") }?.let { return it }
        return null
    }
}
