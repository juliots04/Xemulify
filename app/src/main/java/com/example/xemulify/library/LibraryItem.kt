package com.example.xemulify.library

/** Elemento mostrado en una tarjeta de la biblioteca: un juego o una acción del menú. */
sealed class LibraryItem {

    /** Un juego que se puede lanzar. */
    data class GameItem(val game: Game) : LibraryItem()

    /** Una acción del menú (añadir juego, configurar teclas, importar BIOS, ayuda). */
    data class ActionItem(
        val id: String,
        val title: String,
        val subtitle: String,
        val iconRes: Int,
    ) : LibraryItem() {
        companion object {
            const val ADD = "add"
            const val KEYS = "keys"
            const val BIOS = "bios"
            const val HELP = "help"
            const val GENERAL = "general"
        }
    }
}
