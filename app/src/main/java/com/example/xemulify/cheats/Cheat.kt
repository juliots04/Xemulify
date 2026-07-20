package com.example.xemulify.cheats

/**
 * Un truco (cheat) para un juego: un código GameShark/Action Replay que el core
 * de PS1 aplica en tiempo real mientras se juega.
 *
 *  - [name]: etiqueta visible (p. ej. "60 FPS").
 *  - [code]: el código en crudo, tal cual lo entrega la comunidad. Puede tener
 *    varias líneas ("AAAAAAAA VVVV"), una por cada dirección a parchear.
 *  - [enabled]: si está activo; los desactivados se conservan pero no se aplican.
 */
data class Cheat(
    val name: String,
    val code: String,
    val enabled: Boolean = false,
)
