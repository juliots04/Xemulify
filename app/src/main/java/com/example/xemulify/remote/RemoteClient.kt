package com.example.xemulify.remote

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Cliente UDP que corre en el smartphone (Modo Mando).
 * Envía paquetes de entrada compactos y mide la latencia de red en tiempo real.
 */
class RemoteClient {

    interface Listener {
        fun onConnected(playerSlot: Int, slotColorIndex: Int)
        fun onConnectionFailed(reason: String)
        fun onDisconnected()
        fun onLatencyUpdated(pingMs: Long)
        fun onRumbleReceived(durationMs: Long, strongMotor: Boolean)
    }

    var listener: Listener? = null

    private var socket: DatagramSocket? = null
    private var hostAddress: InetAddress? = null
    private var hostPort: Int = RemoteProtocol.DEFAULT_PORT

    private val isConnected = AtomicBoolean(false)
    private val isRunning = AtomicBoolean(false)
    private var sequenceNumber: Byte = 0
    private var pingSequence: Byte = 0
    private var lastPingSendTime: Long = 0
    private var lastPongReceivedTime: Long = 0

    private val mainHandler = Handler(Looper.getMainLooper())
    private var workerThread: Thread? = null
    private var keepAliveThread: Thread? = null
    private val sendExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "Xemulify-InputSender").apply { isDaemon = true }
    }

    var assignedSlot: Int = 0
        private set

    companion object {
        private const val TAG = "RemoteClient"
    }

    fun connect(hostIp: String, port: Int, pin6: String, deviceName: String) {
        disconnect()
        isRunning.set(true)

        workerThread = Thread({
            try {
                hostAddress = InetAddress.getByName(hostIp)
                hostPort = port
                socket = DatagramSocket()
                socket?.soTimeout = 4000

                // Enviar CONNECT_REQ
                val nameBytes = deviceName.toByteArray(Charsets.UTF_8).take(32).toByteArray()
                val pinBytes = pin6.uppercase().take(6).padEnd(6, 'X').toByteArray(Charsets.US_ASCII)
                val buffer = ByteBuffer.allocate(2 + 6 + 1 + nameBytes.size)
                buffer.put(RemoteProtocol.MAGIC_BYTE)
                buffer.put(RemoteProtocol.TYPE_CONNECT_REQ)
                buffer.put(pinBytes)
                buffer.put(nameBytes.size.toByte())
                buffer.put(nameBytes)

                val reqData = buffer.array()
                val reqPacket = DatagramPacket(reqData, reqData.size, hostAddress, hostPort)
                socket?.send(reqPacket)

                // Esperar ACK o NAK
                val respBuf = ByteArray(64)
                val respPacket = DatagramPacket(respBuf, respBuf.size)
                socket?.receive(respPacket)

                if (respPacket.length >= 2 && respBuf[0] == RemoteProtocol.MAGIC_BYTE) {
                    when (respBuf[1]) {
                        RemoteProtocol.TYPE_CONNECT_ACK -> {
                            assignedSlot = respBuf[2].toInt()
                            val colorIdx = if (respPacket.length >= 4) respBuf[3].toInt() else assignedSlot
                            isConnected.set(true)
                            lastPongReceivedTime = System.currentTimeMillis()
                            socket?.soTimeout = 2000 // Socket con timeout de recepción para detectar desconexión

                            mainHandler.post {
                                listener?.onConnected(assignedSlot, colorIdx)
                            }
                            startKeepAlive()
                            listenLoop()
                        }
                        RemoteProtocol.TYPE_CONNECT_NAK -> {
                            val code = if (respPacket.length >= 3) respBuf[2].toInt() else 0
                            val reason = when (code) {
                                1 -> "PIN incorrecto"
                                2 -> "La sala de juego está llena (máx 4 mandos)"
                                else -> "Conexión rechazada"
                            }
                            mainHandler.post { listener?.onConnectionFailed(reason) }
                            disconnect()
                        }
                    }
                } else {
                    mainHandler.post { listener?.onConnectionFailed("Respuesta inválida del anfitrión") }
                    disconnect()
                }

            } catch (e: Exception) {
                Log.w(TAG, "Error conectando al anfitrión", e)
                mainHandler.post { listener?.onConnectionFailed("No se pudo conectar a la TV: ${e.message}") }
                disconnect()
            }
        }, "Xemulify-RemoteClient").apply { start() }
    }

    private fun listenLoop() {
        val buf = ByteArray(64)
        val packet = DatagramPacket(buf, buf.size)
        while (isRunning.get() && isConnected.get()) {
            try {
                socket?.receive(packet)
                if (packet.length >= 2 && buf[0] == RemoteProtocol.MAGIC_BYTE) {
                    when (buf[1]) {
                        RemoteProtocol.TYPE_PONG -> {
                            val now = System.currentTimeMillis()
                            lastPongReceivedTime = now
                            val rtt = now - lastPingSendTime
                            mainHandler.post { listener?.onLatencyUpdated(rtt) }
                        }
                        RemoteProtocol.TYPE_RUMBLE -> {
                            lastPongReceivedTime = System.currentTimeMillis()
                            val dur = if (packet.length >= 4) {
                                ByteBuffer.wrap(buf, 2, 2).short.toLong()
                            } else 100L
                            val strong = if (packet.length >= 5) buf[4] == 1.toByte() else true
                            mainHandler.post { listener?.onRumbleReceived(dur, strong) }
                        }
                        RemoteProtocol.TYPE_DISCONNECT -> {
                            Log.i(TAG, "Recibido paquete DISCONNECT de la TV/Consola")
                            mainHandler.post { listener?.onDisconnected() }
                            disconnect()
                            break
                        }
                    }
                }
            } catch (_: java.net.SocketTimeoutException) {
                // Si la consola cerró la app sin avisar, el heartbeat detecta la ausencia de PONGs
                if (System.currentTimeMillis() - lastPongReceivedTime > 4000L) {
                    Log.w(TAG, "Timeout de red (>4s sin respuesta de la consola). Desconectando...")
                    mainHandler.post { listener?.onDisconnected() }
                    disconnect()
                    break
                }
            } catch (e: Exception) {
                if (!isRunning.get()) break
            }
        }
    }

    /**
     * Envía la pulsación de botones y ejes de los sticks en un paquete de ~10 bytes.
     * Se despacha en el hilo de fondo sendExecutor para evitar NetworkOnMainThreadException.
     */
    fun sendInput(
        buttonMask: Short,
        stickLX: Byte = 0,
        stickLY: Byte = 0,
        stickRX: Byte = 0,
        stickRY: Byte = 0
    ) {
        if (!isConnected.get()) return
        val host = hostAddress ?: return
        val sock = socket ?: return

        sendExecutor.execute {
            try {
                val buf = ByteBuffer.allocate(10)
                buf.put(RemoteProtocol.MAGIC_BYTE)
                buf.put(RemoteProtocol.TYPE_INPUT)
                buf.put(sequenceNumber++)
                buf.putShort(buttonMask)
                buf.put(stickLX)
                buf.put(stickLY)
                buf.put(stickRX)
                buf.put(stickRY)

                val data = buf.array()
                val packet = DatagramPacket(data, data.size, host, hostPort)
                sock.send(packet)
            } catch (e: Exception) {
                // Silenciar excepciones al desconectar
            }
        }
    }

    private fun startKeepAlive() {
        keepAliveThread = Thread({
            while (isRunning.get() && isConnected.get()) {
                try {
                    Thread.sleep(1500)
                    if (!isRunning.get() || !isConnected.get()) break
                    val host = hostAddress ?: break
                    val now = System.currentTimeMillis()
                    if (now - lastPongReceivedTime > 4000L) {
                        Log.w(TAG, "Heartbeat watchdog: sin PONG en >4s. Desconectando...")
                        mainHandler.post { listener?.onDisconnected() }
                        disconnect()
                        break
                    }
                    lastPingSendTime = now
                    val ping = byteArrayOf(RemoteProtocol.MAGIC_BYTE, RemoteProtocol.TYPE_PING, pingSequence++)
                    val packet = DatagramPacket(ping, ping.size, host, hostPort)
                    socket?.send(packet)
                } catch (_: InterruptedException) {
                    break
                } catch (_: Exception) {}
            }
        }, "Xemulify-KeepAlive").apply { isDaemon = true; start() }
    }

    fun disconnect() {
        isRunning.set(false)
        try {
            keepAliveThread?.interrupt()
        } catch (_: Exception) {}
        if (isConnected.getAndSet(false)) {
            val host = hostAddress
            val sock = socket
            if (host != null && sock != null) {
                sendExecutor.execute {
                    try {
                        val disc = byteArrayOf(RemoteProtocol.MAGIC_BYTE, RemoteProtocol.TYPE_DISCONNECT)
                        val packet = DatagramPacket(disc, disc.size, host, hostPort)
                        sock.send(packet)
                    } catch (_: Exception) {}
                    try {
                        sock.close()
                    } catch (_: Exception) {}
                }
            } else {
                try {
                    socket?.close()
                } catch (_: Exception) {}
            }
        } else {
            try {
                socket?.close()
            } catch (_: Exception) {}
        }
        socket = null
        hostAddress = null
    }
}
