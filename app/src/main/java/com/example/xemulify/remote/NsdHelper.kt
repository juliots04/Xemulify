package com.example.xemulify.remote

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import java.net.InetAddress
import java.net.NetworkInterface

/**
 * Gestiona el descubrimiento de red local (mDNS / Zeroconf) para vincular la TV
 * y el smartphone automáticamente sin necesidad de teclear direcciones IP.
 */
class NsdHelper(private val context: Context) {

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private var multicastLock: WifiManager.MulticastLock? = null

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    companion object {
        private const val TAG = "NsdHelper"
        const val SERVICE_TYPE = "_xemulify._udp."
    }

    fun acquireMulticastLock() {
        try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifi?.createMulticastLock("XemulifyMulticastLock")?.apply {
                setReferenceCounted(true)
                acquire()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error adquiriendo MulticastLock", e)
        }
    }

    fun releaseMulticastLock() {
        try {
            multicastLock?.let {
                if (it.isHeld) it.release()
            }
            multicastLock = null
        } catch (e: Exception) {
            Log.w(TAG, "Error liberando MulticastLock", e)
        }
    }

    /**
     * Publica el servicio en la TV / Consola.
     */
    fun registerService(port: Int, serviceName: String, onRegistered: (String) -> Unit) {
        val serviceInfo = NsdServiceInfo().apply {
            this.serviceName = serviceName
            this.serviceType = SERVICE_TYPE
            this.port = port
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                Log.d(TAG, "Servicio registrado: ${info.serviceName}")
                onRegistered(info.serviceName)
            }

            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Error de registro: $errorCode")
            }

            override fun onServiceUnregistered(info: NsdServiceInfo) {
                Log.d(TAG, "Servicio desregistrado: ${info.serviceName}")
            }

            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Error al desregistrar: $errorCode")
            }
        }

        try {
            nsdManager?.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {
            Log.e(TAG, "Excepción al registrar servicio NSD", e)
        }
    }

    fun unregisterService() {
        try {
            registrationListener?.let { nsdManager?.unregisterService(it) }
            registrationListener = null
        } catch (e: Exception) {
            Log.w(TAG, "Error al desregistrar", e)
        }
    }

    /**
     * Descubre servicios de Xemulify en la red local desde el celular.
     */
    fun startDiscovery(onFound: (name: String, host: InetAddress, port: Int) -> Unit) {
        acquireMulticastLock()

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Falló inicio de descubrimiento: $errorCode")
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Falló detención de descubrimiento: $errorCode")
            }

            override fun onDiscoveryStarted(serviceType: String) {
                Log.d(TAG, "Descubrimiento iniciado para: $serviceType")
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(TAG, "Descubrimiento detenido.")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Servicio encontrado: ${serviceInfo.serviceName}")
                if (serviceInfo.serviceType.contains("xemulify", ignoreCase = true)) {
                    resolveService(serviceInfo, onFound)
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Servicio perdido: ${serviceInfo.serviceName}")
            }
        }

        try {
            nsdManager?.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e(TAG, "Error al iniciar descubrimiento NSD", e)
        }
    }

    private fun resolveService(
        serviceInfo: NsdServiceInfo,
        onFound: (name: String, host: InetAddress, port: Int) -> Unit
    ) {
        try {
            nsdManager?.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                    Log.w(TAG, "Fallo al resolver servicio: $errorCode")
                }

                override fun onServiceResolved(info: NsdServiceInfo) {
                    val host = info.host ?: return
                    val port = info.port
                    Log.d(TAG, "Resuelto con éxito: ${info.serviceName} en $host:$port")
                    onFound(info.serviceName, host, port)
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "Error en resolveService", e)
        }
    }

    fun stopDiscovery() {
        try {
            discoveryListener?.let { nsdManager?.stopServiceDiscovery(it) }
            discoveryListener = null
        } catch (e: Exception) {
            Log.w(TAG, "Error al detener descubrimiento", e)
        } finally {
            releaseMulticastLock()
        }
    }

    /**
     * Obtiene la IP local del dispositivo en la interfaz Wi-Fi (IPv4).
     */
    fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                for (addr in intf.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error al obtener IP local", e)
        }
        return null
    }
}
