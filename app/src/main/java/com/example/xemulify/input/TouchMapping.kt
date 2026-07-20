package com.example.xemulify.input

import android.view.KeyEvent

/**
 * Un botón virtual del mapeador táctil: al pulsar la tecla asignada (de teclado o de mando)
 * se inyecta un toque en la posición [x],[y] de la pantalla del juego que esté en primer plano.
 *
 *  - [keyboardKeyCode] / [gamepadKeyCode]: teclas asignadas (KEYCODE_UNKNOWN = sin asignar).
 *  - [x],[y]: posición NORMALIZADA (0..1) respecto al ancho/alto de la pantalla, para que
 *    funcione a cualquier resolución.
 *  - [hold]: si es true, mantiene el toque mientras la tecla esté pulsada (modo nave de GD);
 *    si es false, es un toque rápido por cada pulsación.
 */
data class TouchMapping(
    val id: String,
    val name: String,
    val keyboardKeyCode: Int = KeyEvent.KEYCODE_UNKNOWN,
    val gamepadKeyCode: Int = KeyEvent.KEYCODE_UNKNOWN,
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val hold: Boolean = false,
)
