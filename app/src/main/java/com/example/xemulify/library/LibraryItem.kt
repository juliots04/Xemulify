package com.example.xemulify.library

/** Elemento mostrado en una tarjeta de la biblioteca: un juego, un acceso directo o una acción. */
sealed class LibraryItem {

    /** Un juego que se puede lanzar. */
    data class GameItem(val game: Game) : LibraryItem()

    /** Un acceso directo a una app instalada. */
    data class ShortcutItem(val shortcut: AppShortcut) : LibraryItem()

    /** Una acción del menú (añadir juego/app, configurar teclas, importar BIOS, ayuda…). */
    data class ActionItem(
        val id: String,
        val title: String,
        val subtitle: String,
        val iconRes: Int,
    ) : LibraryItem() {
        companion object {
            const val ADD = "add"
            const val ADD_APP = "add_app"
            const val CONTROLS = "controls"
            const val BIOS = "bios"
            const val HELP = "help"
            const val GENERAL = "general"
        }
    }
}
