package com.example.xemulify.remote

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Servidor UDP de ultra-baja latencia que se ejecuta en la TV / Consola.
 * Recibe entradas binarias de hasta 4 smartphones en paralelo.
 */
class RemoteServer(
    val port: Int = RemoteProtocol.DEFAULT_PORT,
    var currentPin: String = RemoteProtocol.generate6CharPin()
) {
    data class ClientSession(
        val playerSlot: Int, // 0 = P1, 1 = P2, etc.
        val address: InetAddress,
        val port: Int,
        val deviceName: String,
        var lastSeenMs: Long = System.currentTimeMillis()
    )

    interface Listener {
        fun onClientConnected(session: ClientSession)
        fun onClientDisconnected(session: ClientSession)
        fun onInputReceived(
            playerSlot: Int,
            buttonMask: Short,
            stickLX: Byte,
            stickLY: Byte,
            stickRX: Byte,
            stickRY: Byte
        )
        fun onClientsChanged(clients: List<ClientSession>)
    }

    var listener: Listener? = null

    private val isRunning = AtomicBoolean(false)
    private var socket: DatagramSocket? = null
    private var serverThread: Thread? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val activeClients = ConcurrentHashMap<String, ClientSession>()
    // Ranuras para 4 jugadores (true = ocupada)
    private val slotOccupied = BooleanArray(4)

    companion object {
        private const val TAG = "RemoteServer"
        private const val CLIENT_TIMEOUT_MS = 3500L
    }

    fun start() {
        if (isRunning.get()) return
        isRunning.set(true)

        serverThread = Thread({
            try {
                socket = DatagramSocket(port)
                val buffer = ByteArray(64)
                val packet = DatagramPacket(buffer, buffer.size)
                Log.i(TAG, "Servidor UDP iniciado en puerto $port con PIN: $currentPin")

                while (isRunning.get()) {
                    try {
                        socket?.receive(packet)
                        handlePacket(packet)
                    } catch (e: Exception) {
                        if (!isRunning.get()) break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error en socket del servidor UDP", e)
            } finally {
                stop()
            }
        }, "Xemulify-UdpServer").apply { start() }

        startCleanerThread()
    }

    private fun handlePacket(packet: DatagramPacket) {
        val len = packet.length
        if (len < 2) return
        val data = packet.data
        if (data[0] != RemoteProtocol.MAGIC_BYTE) return

        val type = data[1]
        val senderKey = "${packet.address.hostAddress}:${packet.port}"

        when (type) {
            RemoteProtocol.TYPE_CONNECT_REQ -> {
                handleConnectReq(packet, senderKey)
            }
            RemoteProtocol.TYPE_INPUT -> {
                val session = activeClients[senderKey]
                if (session == null) {
                    // Cliente residual de una sesión anterior: notificarle desconexión de inmediato
                    val disc = byteArrayOf(RemoteProtocol.MAGIC_BYTE, RemoteProtocol.TYPE_DISCONNECT)
                    sendPacket(packet.address, packet.port, disc)
                    return
                }
                session.lastSeenMs = System.currentTimeMillis()
                if (len >= 8) {
                    val bb = ByteBuffer.wrap(data, 2, len - 2)
                    bb.get() // sequence
                    val buttonMask = bb.short
                    val stickLX = bb.get()
                    val stickLY = bb.get()
                    val stickRX = if (len >= 9) bb.get() else 0.toByte()
                    val stickRY = if (len >= 10) bb.get() else 0.toByte()

                    listener?.onInputReceived(session.playerSlot, buttonMask, stickLX, stickLY, stickRX, stickRY)
                }
            }
            RemoteProtocol.TYPE_PING -> {
                val session = activeClients[senderKey]
                if (session == null) {
                    // Cliente no registrado haciendo ping: forzar desconexión en el smartphone
                    val disc = byteArrayOf(RemoteProtocol.MAGIC_BYTE, RemoteProtocol.TYPE_DISCONNECT)
                    sendPacket(packet.address, packet.port, disc)
                    return
                }
                session.lastSeenMs = System.currentTimeMillis()
                // Responder PONG con el mismo timestamp
                val pong = ByteArray(3)
                pong[0] = RemoteProtocol.MAGIC_BYTE
                pong[1] = RemoteProtocol.TYPE_PONG
                pong[2] = data.getOrElse(2) { 0 }
                sendPacket(packet.address, packet.port, pong)
            }
            RemoteProtocol.TYPE_DISCONNECT -> {
                removeClient(senderKey)
            }
        }
    }

    private fun handleConnectReq(packet: DatagramPacket, senderKey: String) {
        val len = packet.length
        if (len < 8) return

        val bb = ByteBuffer.wrap(packet.data, 2, len - 2)
        val pinBytes = ByteArray(6)
        bb.get(pinBytes)
        val incomingPin = String(pinBytes, Charsets.US_ASCII).uppercase()

        val nameLen = if (bb.hasRemaining()) bb.get().toInt() and 0xFF else 0
        val deviceName = if (nameLen > 0 && bb.remaining() >= nameLen) {
            val nameBytes = ByteArray(nameLen)
            bb.get(nameBytes)
            String(nameBytes, Charsets.UTF_8)
        } else {
            "Smartphone"
        }

        if (incomingPin == currentPin.uppercase()) {
            // Asignar primer slot libre
            var slot = -1
            synchronized(slotOccupied) {
                // Si ya existía un cliente registrado desde la misma IP (por reconexión con nuevo puerto UDP efímero),
                // liberar su slot anterior para reasignarlo de inmediato como P1
                val staleEntry = activeClients.entries.find { it.value.address == packet.address }
                if (staleEntry != null) {
                    slotOccupied[staleEntry.value.playerSlot] = false
                    activeClients.remove(staleEntry.key)
                }

                // Si el cliente ya estaba registrado, reasignar su slot
                val existing = activeClients[senderKey]
                if (existing != null) {
                    slot = existing.playerSlot
                } else {
                    for (i in slotOccupied.indices) {
                        if (!slotOccupied[i]) {
                            slotOccupied[i] = true
                            slot = i
                            break
                        }
                    }
                }
            }

            if (slot != -1) {
                val session = ClientSession(slot, packet.address, packet.port, deviceName)
                activeClients[senderKey] = session

                // Enviar ACK con slot
                val ack = ByteArray(4)
                ack[0] = RemoteProtocol.MAGIC_BYTE
                ack[1] = RemoteProtocol.TYPE_CONNECT_ACK
                ack[2] = slot.toByte()
                ack[3] = slot.toByte() // Color index
                sendPacket(packet.address, packet.port, ack)

                mainHandler.post {
                    listener?.onClientConnected(session)
                    listener?.onClientsChanged(activeClients.values.toList())
                }
                Log.i(TAG, "Mando conectado: $deviceName como Jugador ${slot + 1}")
            } else {
                // Sala llena
                val nak = byteArrayOf(RemoteProtocol.MAGIC_BYTE, RemoteProtocol.TYPE_CONNECT_NAK, 0x02)
                sendPacket(packet.address, packet.port, nak)
            }
        } else {
            // PIN erróneo
            val nak = byteArrayOf(RemoteProtocol.MAGIC_BYTE, RemoteProtocol.TYPE_CONNECT_NAK, 0x01)
            sendPacket(packet.address, packet.port, nak)
            Log.w(TAG, "Intento de conexión con PIN incorrecto: $incomingPin (esperado: $currentPin)")
        }
    }

    private fun removeClient(senderKey: String) {
        val removed = activeClients.remove(senderKey) ?: return
        synchronized(slotOccupied) {
            if (removed.playerSlot in slotOccupied.indices) {
                slotOccupied[removed.playerSlot] = false
            }
        }
        mainHandler.post {
            listener?.onClientDisconnected(removed)
            listener?.onClientsChanged(activeClients.values.toList())
        }
        Log.i(TAG, "Mando desconectado: ${removed.deviceName} (P${removed.playerSlot + 1})")
    }

    private fun startCleanerThread() {
        Thread({
            while (isRunning.get()) {
                try {
                    Thread.sleep(1000)
                    val now = System.currentTimeMillis()
                    for ((key, session) in activeClients) {
                        if (now - session.lastSeenMs > CLIENT_TIMEOUT_MS) {
                            Log.w(TAG, "Cliente timed out: ${session.deviceName}")
                            removeClient(key)
                        }
                    }
                } catch (_: Exception) {}
            }
        }, "Xemulify-TimeoutCleaner").apply { isDaemon = true; start() }
    }

    private fun sendPacket(address: InetAddress, port: Int, data: ByteArray) {
        try {
            val p = DatagramPacket(data, data.size, address, port)
            socket?.send(p)
        } catch (e: Exception) {
            Log.w(TAG, "Error enviando paquete a $address:$port", e)
        }
    }

    fun getConnectedClients(): List<ClientSession> {
        val now = System.currentTimeMillis()
        return activeClients.values.filter { now - it.lastSeenMs <= CLIENT_TIMEOUT_MS }
    }

    fun regeneratePin(): String {
        currentPin = RemoteProtocol.generate6CharPin()
        return currentPin
    }

    fun stop() {
        if (!isRunning.getAndSet(false)) return
        val clientsToNotify = activeClients.values.toList()
        activeClients.clear()
        synchronized(slotOccupied) {
            for (i in slotOccupied.indices) slotOccupied[i] = false
        }
        val currentSock = socket
        socket = null

        if (currentSock != null && clientsToNotify.isNotEmpty()) {
            val disc = byteArrayOf(RemoteProtocol.MAGIC_BYTE, RemoteProtocol.TYPE_DISCONNECT)
            for (client in clientsToNotify) {
                try {
                    val p = DatagramPacket(disc, disc.size, client.address, client.port)
                    currentSock.send(p)
                    currentSock.send(p)
                } catch (_: Exception) {}
            }
        }
        try {
            currentSock?.close()
        } catch (_: Exception) {}
    }
}
