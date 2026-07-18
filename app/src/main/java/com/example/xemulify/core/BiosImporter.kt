package com.example.xemulify.core

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

/**
 * Importa el BIOS de PS1 al directorio de sistema del emulador.
 *
 * PCSX-ReARMed detecta el BIOS automáticamente si el nombre está en minúsculas y acaba
 * en `.bin` (p. ej. `scph5501.bin`, `scph1001.bin`, `scph7001.bin`, `scph101.bin`).
 * Aquí normalizamos el nombre a minúsculas para maximizar la compatibilidad.
 */
object BiosImporter {

    fun import(context: Context, uri: Uri): File {
        val raw = displayName(context, uri)
        val lower = raw.lowercase()
        val fileName = if (lower.endsWith(".bin")) lower else "$lower.bin"

        val dest = File(CoreManager.systemDir(context), fileName)
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "No se pudo abrir el fichero de BIOS." }
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        return dest
    }

    private fun displayName(context: Context, uri: Uri): String {
        var name: String? = null
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) name = cursor.getString(0) }
        return name ?: uri.lastPathSegment?.substringAfterLast('/') ?: "scph5501.bin"
    }
}
