package com.example.xemulify.input

import android.view.KeyEvent

/**
 * Botones del mando de PlayStation 1 (DualShock digital).
 *
 * Cada botón conoce:
 *  - [displayName]: etiqueta visible en la pantalla de configuración de teclas.
 *  - [defaultKeyboardKeyCode]: tecla de teclado asignada por defecto (p. ej. letras, enter).
 *  - [defaultGamepadKeyCode]: tecla de mando físico/TV por defecto (p. ej. D-pad, botones del mando).
 *  - [virtualKeyCode]: el keycode de mando Android que se reenvía al core de emulación.
 *  - [isDpad]: indica si pertenece a la cruceta de movimiento.
 */
enum class PsxButton(
    val id: String,
    val displayName: String,
    val defaultKeyboardKeyCode: Int,
    val defaultGamepadKeyCode: Int,
    val virtualKeyCode: Int,
    val isDpad: Boolean = false,
) {
    DPAD_UP("dpad_up", "Cruceta ↑", KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_UP, isDpad = true),
    DPAD_DOWN("dpad_down", "Cruceta ↓", KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_DOWN, isDpad = true),
    DPAD_LEFT("dpad_left", "Cruceta ←", KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_LEFT, isDpad = true),
    DPAD_RIGHT("dpad_right", "Cruceta →", KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_RIGHT, isDpad = true),

    CROSS("cross", "Cruz", KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_A),
    CIRCLE("circle", "Círculo", KeyEvent.KEYCODE_C, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_B),
    SQUARE("square", "Cuadrado", KeyEvent.KEYCODE_Z, KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_X),
    TRIANGLE("triangle", "Triángulo", KeyEvent.KEYCODE_X, KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_BUTTON_Y),

    L1("l1", "L1", KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_L1),
    R1("r1", "R1", KeyEvent.KEYCODE_Q, KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_BUTTON_R1),
    L2("l2", "L2", KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_BUTTON_L2),
    R2("r2", "R2", KeyEvent.KEYCODE_P, KeyEvent.KEYCODE_BUTTON_R2, KeyEvent.KEYCODE_BUTTON_R2),

    START("start", "Start", KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_START),
    SELECT("select", "Select", KeyEvent.KEYCODE_SHIFT_RIGHT, KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BUTTON_SELECT);
}

