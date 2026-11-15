package com.example.xemulify.remote

import android.content.Context
import android.os.Build
import android.util.Log

/**
 * Singleton que gestiona el ciclo de vida del servidor de mandos remotos
 * y despacha las entradas al emulador o a la interfaz de usuario de la consola.
 */
object RemoteControllerManager {

    private const val TAG = "RemoteControllerMgr"

    interface InputSink {
        fun onRemoteButtonChanged(playerSlot: Int, button: Int, isPressed: Boolean)
        fun onRemoteMotionChanged(playerSlot: Int, axisSource: Int, x: Float, y: Float)
    }

    interface ConnectionListener {
        fun onClientConnected(session: RemoteServer.ClientSession) {}
        fun onClientDisconnected(session: RemoteServer.ClientSession) {}
        fun onClientsChanged(clients: List<RemoteServer.ClientSession>) {}
    }

    private val connectionListeners = java.util.concurrent.CopyOnWriteArrayList<ConnectionListener>()

    fun addConnectionListener(listener: ConnectionListener) {
        connectionListeners.add(listener)
    }

    fun removeConnectionListener(listener: ConnectionListener) {
        connectionListeners.remove(listener)
    }

    var server: RemoteServer? = null
        private set

    var nsdHelper: NsdHelper? = null
        private set

    var inputSink: InputSink? = null

    // Estados previos de botones por jugador para detectar transiciones DOWN/UP
    private val previousButtons = ShortArray(4)

    fun startServer(context: Context): RemoteServer {
        val existing = server
        if (existing != null) return existing

        val srv = RemoteServer()
        server = srv

        srv.listener = object : RemoteServer.Listener {
            override fun onClientConnected(session: RemoteServer.ClientSession) {
                Log.i(TAG, "Mando conectado: ${session.deviceName} (Slot P${session.playerSlot + 1})")
                for (l in connectionListeners) {
                    try { l.onClientConnected(session) } catch (_: Exception) {}
                }
            }

            override fun onClientDisconnected(session: RemoteServer.ClientSession) {
                Log.i(TAG, "Mando desconectado: ${session.deviceName} (Slot P${session.playerSlot + 1})")
                // Liberar botones que hayan quedado pulsados
                if (session.playerSlot in previousButtons.indices) {
                    previousButtons[session.playerSlot] = 0
                }
                for (l in connectionListeners) {
                    try { l.onClientDisconnected(session) } catch (_: Exception) {}
                }
            }

            override fun onInputReceived(
                playerSlot: Int,
                buttonMask: Short,
                stickLX: Byte,
                stickLY: Byte,
                stickRX: Byte,
                stickRY: Byte
            ) {
                dispatchInputs(playerSlot, buttonMask, stickLX, stickLY, stickRX, stickRY)
            }

            override fun onClientsChanged(clients: List<RemoteServer.ClientSession>) {
                for (l in connectionListeners) {
                    try { l.onClientsChanged(clients) } catch (_: Exception) {}
                }
            }
        }

        srv.start()

        // Iniciar publicidad mDNS
        val nsd = NsdHelper(context.applicationContext)
        nsdHelper = nsd
        val tvName = "Xemulify-${Build.MODEL.take(12)}"
        nsd.registerService(srv.port, tvName) { registeredName ->
            Log.i(TAG, "Servicio de mandos anunciado en Wi-Fi como: $registeredName")
        }

        return srv
    }

    private fun dispatchInputs(
        playerSlot: Int,
        currentMask: Short,
        stickLX: Byte,
        stickLY: Byte,
        stickRX: Byte,
        stickRY: Byte
    ) {
        val sink = inputSink ?: return
        val prevMask = previousButtons.getOrElse(playerSlot) { 0 }
        previousButtons[playerSlot] = currentMask

        fun check(btnMask: Short, buttonCode: Int) {
            val wasPressed = (prevMask.toInt() and btnMask.toInt()) != 0
            val isPressed = (currentMask.toInt() and btnMask.toInt()) != 0
            if (wasPressed != isPressed) {
                sink.onRemoteButtonChanged(playerSlot, buttonCode, isPressed)
            }
        }

        check(RemoteProtocol.BTN_DPAD_UP, android.view.KeyEvent.KEYCODE_DPAD_UP)
        check(RemoteProtocol.BTN_DPAD_DOWN, android.view.KeyEvent.KEYCODE_DPAD_DOWN)
        check(RemoteProtocol.BTN_DPAD_LEFT, android.view.KeyEvent.KEYCODE_DPAD_LEFT)
        check(RemoteProtocol.BTN_DPAD_RIGHT, android.view.KeyEvent.KEYCODE_DPAD_RIGHT)

        check(RemoteProtocol.BTN_CROSS, android.view.KeyEvent.KEYCODE_BUTTON_A)
        check(RemoteProtocol.BTN_CIRCLE, android.view.KeyEvent.KEYCODE_BUTTON_B)
        check(RemoteProtocol.BTN_SQUARE, android.view.KeyEvent.KEYCODE_BUTTON_X)
        check(RemoteProtocol.BTN_TRIANGLE, android.view.KeyEvent.KEYCODE_BUTTON_Y)

        check(RemoteProtocol.BTN_L1, android.view.KeyEvent.KEYCODE_BUTTON_L1)
        check(RemoteProtocol.BTN_R1, android.view.KeyEvent.KEYCODE_BUTTON_R1)
        check(RemoteProtocol.BTN_L2, android.view.KeyEvent.KEYCODE_BUTTON_L2)
        check(RemoteProtocol.BTN_R2, android.view.KeyEvent.KEYCODE_BUTTON_R2)

        check(RemoteProtocol.BTN_START, android.view.KeyEvent.KEYCODE_BUTTON_START)
        check(RemoteProtocol.BTN_SELECT, android.view.KeyEvent.KEYCODE_BUTTON_SELECT)
        check(RemoteProtocol.BTN_L3, android.view.KeyEvent.KEYCODE_BUTTON_THUMBL)
        check(RemoteProtocol.BTN_R3, android.view.KeyEvent.KEYCODE_BUTTON_THUMBR)

        // Ejes de sticks analógicos normalizados de -1.0f a 1.0f
        val lx = (stickLX.toFloat() / 127.0f).coerceIn(-1.0f, 1.0f)
        val ly = (stickLY.toFloat() / 127.0f).coerceIn(-1.0f, 1.0f)
        sink.onRemoteMotionChanged(playerSlot, 1 /* MOTION_SOURCE_ANALOG_LEFT */, lx, ly)

        val rx = (stickRX.toFloat() / 127.0f).coerceIn(-1.0f, 1.0f)
        val ry = (stickRY.toFloat() / 127.0f).coerceIn(-1.0f, 1.0f)
        sink.onRemoteMotionChanged(playerSlot, 2 /* MOTION_SOURCE_ANALOG_RIGHT */, rx, ry)
    }

    fun stopServer() {
        nsdHelper?.unregisterService()
        nsdHelper = null
        server?.stop()
        server = null
        inputSink = null
    }
}
