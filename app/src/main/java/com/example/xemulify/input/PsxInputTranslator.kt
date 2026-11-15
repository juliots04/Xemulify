package com.example.xemulify.input

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.swordfish.libretrodroid.GLRetroView

/**
 * Traduce teclas físicas (teclado o mando) en entradas para el core de PS1, con soporte de
 * dos jugadores (puertos 0 y 1).
 *
 * Enrutado:
 *  - Si el evento viene de un MANDO, se usa el mapa de botones estándar ([gamepadMap]) y el
 *    puerto se decide por el número de control del dispositivo (2º mando → P2). Esto hace que
 *    un mando tradicional funcione sin configurar nada.
 *  - Si no, se buscan las teclas asignadas de P1 ([p1Map], puerto 0) y P2 ([p2Map], puerto 1).
 *
 * La cruceta se envía como movimiento (MOTION_SOURCE_DPAD) con estado independiente por puerto.
 */
class PsxInputTranslator(
    private val retroView: GLRetroView,
    @Volatile private var p1Map: Map<Int, PsxButton>,
    @Volatile private var p2Map: Map<Int, PsxButton>,
    @Volatile private var gamepadMap: Map<Int, PsxButton>,
) {
    private val dpad = arrayOf(DpadState(), DpadState(), DpadState(), DpadState())

    fun updateMapping(p1: Map<Int, PsxButton>, p2: Map<Int, PsxButton>, gamepad: Map<Int, PsxButton>) {
        p1Map = p1; p2Map = p2; gamepadMap = gamepad
    }

    /** Entrada directa desde un mando de smartphone remoto (Wi-Fi). */
    fun handleRemoteButton(port: Int, keyCode: Int, pressed: Boolean) {
        val safePort = port.coerceIn(0, dpad.size - 1)
        val button = gamepadMap[keyCode] ?: PsxButton.values().firstOrNull { it.defaultGamepadKeyCode == keyCode }
        button?.let { emit(it, safePort, pressed) }
    }

    /** Movimiento analógico desde un mando de smartphone remoto (Wi-Fi). */
    fun handleRemoteMotion(port: Int, axisSource: Int, x: Float, y: Float) {
        val safePort = port.coerceIn(0, dpad.size - 1)
        val source = if (axisSource == 1) GLRetroView.MOTION_SOURCE_ANALOG_LEFT else GLRetroView.MOTION_SOURCE_ANALOG_RIGHT
        runCatching { retroView.sendMotionEvent(source, x, y, safePort) }
    }

    fun handleKeyEvent(event: KeyEvent): Boolean {
        val pressed = event.action == KeyEvent.ACTION_DOWN

        if (isGamepad(event.device)) {
            gamepadMap[event.keyCode]?.let {
                emit(it, portOf(event.device), pressed); return true
            }
        }
        p1Map[event.keyCode]?.let { emit(it, 0, pressed); return true }
        p2Map[event.keyCode]?.let { emit(it, 1, pressed); return true }
        return false
    }

    /** Cruceta como "hat" analógico y stick izquierdo, para mandos que los envían como movimiento. */
    fun handleMotionEvent(event: MotionEvent): Boolean {
        val device = event.device ?: return false
        if (!isGamepad(device)) return false
        val port = portOf(device)

        val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        val d = dpad[port]
        d.left = hatX < -0.5f; d.right = hatX > 0.5f
        d.up = hatY < -0.5f; d.down = hatY > 0.5f
        emitDpad(port)

        val x = event.getAxisValue(MotionEvent.AXIS_X)
        val y = event.getAxisValue(MotionEvent.AXIS_Y)
        runCatching { retroView.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_LEFT, x, y, port) }
        return true
    }

    fun releaseAll() {
        for (port in 0 until dpad.size) {
            dpad[port].reset()
            emitDpad(port)
            for (button in PsxButton.values()) {
                if (!button.isDpad) retroView.sendKeyEvent(KeyEvent.ACTION_UP, button.virtualKeyCode, port)
            }
        }
    }

    private fun emit(button: PsxButton, port: Int, pressed: Boolean) {
        if (button.isDpad) {
            val d = dpad[port]
            when (button) {
                PsxButton.DPAD_UP -> d.up = pressed
                PsxButton.DPAD_DOWN -> d.down = pressed
                PsxButton.DPAD_LEFT -> d.left = pressed
                PsxButton.DPAD_RIGHT -> d.right = pressed
                else -> Unit
            }
            emitDpad(port)
        } else {
            val action = if (pressed) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP
            retroView.sendKeyEvent(action, button.virtualKeyCode, port)
        }
    }

    private fun emitDpad(port: Int) {
        val d = dpad[port]
        val x = (if (d.right) 1f else 0f) - (if (d.left) 1f else 0f)
        val y = (if (d.down) 1f else 0f) - (if (d.up) 1f else 0f)
        retroView.sendMotionEvent(GLRetroView.MOTION_SOURCE_DPAD, x, y, port)
    }

    private fun isGamepad(device: InputDevice?): Boolean {
        val sources = device?.sources ?: return false
        return sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
    }

    /** Puerto según el número de control del mando: 1er mando → 0 (P1), 2º → 1 (P2). */
    private fun portOf(device: InputDevice?): Int =
        if ((device?.controllerNumber ?: 1) >= 2) 1 else 0

    private class DpadState {
        var up = false; var down = false; var left = false; var right = false
        fun reset() { up = false; down = false; left = false; right = false }
    }
}
