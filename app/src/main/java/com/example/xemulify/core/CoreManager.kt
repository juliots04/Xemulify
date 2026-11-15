package com.example.xemulify.core

import android.content.Context
import java.io.File

/**
 * Localiza el core de emulación de PS1 (PCSX-ReARMed) y gestiona los directorios de trabajo.
 *
 * El core se empaqueta en `app/src/main/jniLibs/<abi>/libpcsx_rearmed_libretro.so`.
 * Con `useLegacyPackaging = true` (ver build.gradle.kts) Android lo extrae a
 * `applicationInfo.nativeLibraryDir` al instalar, de modo que podemos pasarle al motor
 * su ruta absoluta.
 *
 * Directorios (todos en el almacenamiento privado de la app):
 *  - system/  → BIOS de PS1 (scph*.bin). PCSX-ReARMed lo detecta automáticamente.
 *  - saves/   → tarjetas de memoria / SRAM.
 *  - games/   → juegos importados, una subcarpeta por juego.
 *  - states/  → estados guardados (ranuras), una subcarpeta por juego.
 */
object CoreManager {

    /** Debe empezar por "lib" y acabar en ".so" para que Android lo trate como librería nativa. */
    const val CORE_LIB_NAME = "libpcsx_rearmed_libretro.so"

    fun coreFile(context: Context): File =
        File(context.applicationInfo.nativeLibraryDir, CORE_LIB_NAME)

    /** ¿Está el core presente? Si es false, hay que colocar el .so en jniLibs y reinstalar. */
    fun isCoreAvailable(context: Context): Boolean = coreFile(context).exists()

    fun systemDir(context: Context): File = ensure(File(context.filesDir, "system"))
    fun savesDir(context: Context): File = ensure(File(context.filesDir, "saves"))
    fun gamesDir(context: Context): File = ensure(File(context.filesDir, "games"))
    fun statesDir(context: Context): File = ensure(File(context.filesDir, "states"))

    /** Carpeta de estados guardados de un juego concreto. */
    fun statesDirFor(context: Context, gameId: String): File =
        ensure(File(statesDir(context), gameId))

    /**
     * Copia cualquier BIOS empaquetada en `assets/bios/` al directorio de sistema
     * del emulador si todavía no está presente.
     */
    fun extractBundledBios(context: Context) {
        val destDir = systemDir(context)
        try {
            val assetFiles = context.assets.list("bios") ?: return
            for (fileName in assetFiles) {
                if (!fileName.lowercase().endsWith(".bin")) continue
                val lowerName = fileName.lowercase()
                val targetFile = File(destDir, lowerName)
                if (!targetFile.exists() || targetFile.length() == 0L) {
                    context.assets.open("bios/$fileName").use { input ->
                        targetFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                // Si la BIOS empaquetada es openbios.bin, PCSX-ReARMed espera por defecto
                // nombres de BIOS oficiales (scph*.bin). Creamos una réplica scph5501.bin para
                // garantizar detección automática directa por el core nativo.
                if (lowerName == "openbios.bin") {
                    val compatTarget = File(destDir, "scph5501.bin")
                    if (!compatTarget.exists() || compatTarget.length() == 0L) {
                        targetFile.copyTo(compatTarget, overwrite = true)
                    }
                }
            }
        } catch (_: Exception) {
            // Ignorar si no existe la carpeta assets/bios o hay error de lectura
        }
    }

    /** ¿Hay algún BIOS válido instalado en el directorio de sistema? */
    fun hasBios(context: Context): Boolean {
        extractBundledBios(context)
        return systemDir(context).listFiles()?.any { f ->
            f.isFile && f.name.lowercase().let {
                (it.startsWith("scph") || it.startsWith("openbios") || it.startsWith("psxonpsp660")) && it.endsWith(".bin")
            }
        } ?: false
    }

    private fun ensure(dir: File): File {
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
}
