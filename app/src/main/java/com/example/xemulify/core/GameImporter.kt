package com.example.xemulify.core

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

/** Resultado de importar un juego: nombre legible y fichero que cargará el emulador. */
data class ImportedGame(val name: String, val loadFile: File)

/**
 * Importa juegos de PS1 desde el selector de ficheros del sistema (SAF).
 *
 * Admite seleccionar varios ficheros a la vez (p. ej. `.cue` + `.bin`, o solo `.bin`).
 * Copia todo a `games/<carpeta>/` y decide qué fichero cargar:
 *   1. Si hay `.m3u`/`.cue` → se carga ese (y se corrige la línea FILE del .cue).
 *   2. Si hay un formato autocontenido (`.pbp`/`.chd`/`.iso`) → se carga ese.
 *   3. Si solo hay `.bin`/`.img` → se genera un `.cue` de una pista apuntando a él.
 */
object GameImporter {

    fun import(context: Context, uris: List<Uri>): ImportedGame {
        require(uris.isNotEmpty()) { "No se seleccionó ningún fichero." }

        val firstName = displayName(context, uris.first())
        val baseName = firstName.substringBeforeLast('.').ifBlank { "Juego" }
        val gameDir = uniqueDir(File(CoreManager.gamesDir(context), sanitize(baseName)))
        gameDir.mkdirs()

        val copied = ArrayList<File>()
        for (uri in uris) {
            val fileName = sanitize(displayName(context, uri))
            val dest = File(gameDir, fileName)
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "No se pudo abrir $fileName." }
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            copied.add(dest)
        }

        val loadFile = resolveLoadFile(gameDir, copied)
        return ImportedGame(baseName, loadFile)
    }

    private fun resolveLoadFile(gameDir: File, copied: List<File>): File {
        copied.firstOrNull { it.extension.equals("m3u", true) }?.let { return it }

        val cue = copied.firstOrNull { it.extension.equals("cue", true) }
        if (cue != null) {
            fixCueReference(cue, copied)
            return cue
        }

        copied.firstOrNull { it.extension.lowercase() in SELF_CONTAINED }?.let { return it }

        val bin = copied.firstOrNull { it.extension.lowercase() in BIN_LIKE }
        if (bin != null) return generateCue(gameDir, bin)

        return copied.first()
    }

    /** Genera un .cue de una sola pista MODE2/2352 para un .bin suelto. */
    private fun generateCue(gameDir: File, bin: File): File {
        val cue = File(gameDir, bin.nameWithoutExtension + ".cue")
        cue.writeText(
            "FILE \"${bin.name}\" BINARY\n" +
                "  TRACK 01 MODE2/2352\n" +
                "    INDEX 01 00:00:00\n"
        )
        return cue
    }

    /** Reescribe la primera línea FILE del .cue para que apunte al .bin ya copiado localmente. */
    private fun fixCueReference(cue: File, copied: List<File>) {
        val bin = copied.firstOrNull { it.extension.lowercase() in BIN_LIKE } ?: return
        val text = cue.readText()
        val fixed = Regex("FILE\\s+\"[^\"]*\"").replaceFirst(text, "FILE \"${bin.name}\"")
        if (fixed != text) cue.writeText(fixed)
    }

    private fun displayName(context: Context, uri: Uri): String {
        var name: String? = null
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) name = cursor.getString(0) }
        return name ?: uri.lastPathSegment?.substringAfterLast('/') ?: "juego.bin"
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("[^A-Za-z0-9._ ()\\-]"), "_").trim().ifBlank { "archivo" }

    private fun uniqueDir(dir: File): File {
        if (!dir.exists()) return dir
        var i = 2
        while (true) {
            val candidate = File(dir.parentFile, "${dir.name} ($i)")
            if (!candidate.exists()) return candidate
            i++
        }
    }

    private val SELF_CONTAINED = setOf("pbp", "chd", "iso")
    private val BIN_LIKE = setOf("bin", "img")
}
