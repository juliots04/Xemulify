package com.example.xemulify.library

import java.io.File

/**
 * Un juego de la biblioteca.
 *
 * @param title  nombre visible (el de la carpeta del juego).
 * @param file   fichero que se pasa al emulador (.cue / .m3u / .pbp / .chd / .bin ...).
 */
data class Game(val title: String, val file: File) {
    /** Identificador estable para carpetas de estados y SRAM. */
    val id: String get() = file.parentFile?.name ?: file.nameWithoutExtension
}
