package com.example.xemulify.controller

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.FragmentActivity
import com.example.xemulify.R
import com.example.xemulify.mode.ModeSelectActivity
import com.example.xemulify.remote.NsdHelper
import com.example.xemulify.remote.RemoteClient
import com.example.xemulify.remote.RemoteProtocol
import com.example.xemulify.ui.PinBoxesView
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

class PairingActivity : FragmentActivity() {

    private lateinit var nsdHelper: NsdHelper
    private var discoveredHostIp: String? = null
    private var discoveredPort: Int = RemoteProtocol.DEFAULT_PORT
    private var discoveredTvName: String? = null

    private lateinit var pinBoxesView: PinBoxesView
    private lateinit var btnConnectPin: View
    private lateinit var txtBtnConnect: TextView
    private lateinit var layoutBtnLoading: View
    private lateinit var btnScanQr: Button

    private var client: RemoteClient? = null

    private val qrScanLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            val qrInfo = RemoteProtocol.parseQrPayload(result.contents)
            if (qrInfo != null) {
                pinBoxesView.setText(qrInfo.pin.uppercase())
                connectToHost(qrInfo.ip, qrInfo.port, qrInfo.pin.uppercase())
            } else {
                Toast.makeText(this, "Código QR no válido para Xemulify", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pairing)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                navigateBack()
            }
        })

        pinBoxesView = findViewById(R.id.pin_boxes_view)
        btnConnectPin = findViewById(R.id.btn_connect_pin)
        txtBtnConnect = findViewById(R.id.txt_btn_connect)
        layoutBtnLoading = findViewById(R.id.layout_btn_loading)
        btnScanQr = findViewById(R.id.btn_scan_qr)

        nsdHelper = NsdHelper(this)
        startDiscovery()

        // Al completar los 6 caracteres, si ya detectamos la IP de la TV, nos conectamos de inmediato
        pinBoxesView.onPinComplete = { pin ->
            val hostIp = discoveredHostIp
            if (!hostIp.isNullOrBlank()) {
                connectToHost(hostIp, discoveredPort, pin)
            }
        }

        pinBoxesView.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_GO) {
                btnConnectPin.performClick()
                true
            } else false
        }

        btnConnectPin.setOnClickListener {
            val pin = pinBoxesView.text.toString().trim().uppercase()
            if (pin.length != 6) {
                Toast.makeText(this, "Ingresa el código PIN completo de 6 caracteres", Toast.LENGTH_SHORT).show()
                showKeyboard()
                return@setOnClickListener
            }

            val targetIp = discoveredHostIp
            if (targetIp.isNullOrBlank()) {
                Toast.makeText(this, "Buscando TV en la red Wi-Fi... Espera un momento o escanea el QR", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            connectToHost(targetIp, discoveredPort, pin)
        }

        btnScanQr.setOnClickListener {
            val options = ScanOptions().apply {
                setPrompt("Apunta la cámara al código QR de la TV")
                setBeepEnabled(true)
                setOrientationLocked(false)
                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            }
            qrScanLauncher.launch(options)
        }
    }

    override fun onResume() {
        super.onResume()
        pinBoxesView.postDelayed({ showKeyboard() }, 300)
    }

    private fun setConnectingState(isConnecting: Boolean) {
        if (isConnecting) {
            txtBtnConnect.visibility = View.GONE
            layoutBtnLoading.visibility = View.VISIBLE
            btnConnectPin.isEnabled = false
            btnScanQr.isEnabled = false
        } else {
            txtBtnConnect.visibility = View.VISIBLE
            layoutBtnLoading.visibility = View.GONE
            btnConnectPin.isEnabled = true
            btnScanQr.isEnabled = true
        }
    }

    private fun showKeyboard() {
        pinBoxesView.requestFocus()
        pinBoxesView.post {
            try {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showSoftInput(pinBoxesView, InputMethodManager.SHOW_IMPLICIT)
                imm?.showSoftInput(pinBoxesView, InputMethodManager.SHOW_FORCED)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    window.decorView.windowInsetsController?.show(WindowInsets.Type.ime())
                }
            } catch (_: Exception) {}
        }
    }

    private fun startDiscovery() {
        nsdHelper.startDiscovery { name, host, port ->
            runOnUiThread {
                discoveredHostIp = host.hostAddress
                discoveredPort = port
                discoveredTvName = name

                val pin = pinBoxesView.text.toString().trim().uppercase()
                val addr = host.hostAddress
                if (pin.length == 6 && !addr.isNullOrBlank()) {
                    connectToHost(addr, port, pin)
                }
            }
        }
    }

    private fun connectToHost(ip: String, port: Int, pin: String) {
        setConnectingState(true)

        client?.disconnect()
        val c = RemoteClient()
        client = c

        val myDeviceName = "${Build.MANUFACTURER} ${Build.MODEL}".take(24)

        c.listener = object : RemoteClient.Listener {
            override fun onConnected(playerSlot: Int, slotColorIndex: Int) {
                runOnUiThread {
                    setConnectingState(false)
                    val intent = Intent(this@PairingActivity, ControllerActivity::class.java).apply {
                        putExtra(ControllerActivity.EXTRA_HOST_IP, ip)
                        putExtra(ControllerActivity.EXTRA_HOST_PORT, port)
                        putExtra(ControllerActivity.EXTRA_PIN, pin)
                        putExtra(ControllerActivity.EXTRA_PLAYER_SLOT, playerSlot)
                        putExtra(ControllerActivity.EXTRA_COLOR_INDEX, slotColorIndex)
                    }
                    startActivity(intent)
                    finish()
                }
            }

            override fun onConnectionFailed(reason: String) {
                runOnUiThread {
                    setConnectingState(false)
                    Toast.makeText(this@PairingActivity, "Error: $reason", Toast.LENGTH_LONG).show()
                }
            }

            override fun onDisconnected() {
                runOnUiThread {
                    setConnectingState(false)
                }
            }

            override fun onLatencyUpdated(pingMs: Long) {}
            override fun onRumbleReceived(durationMs: Long, strongMotor: Boolean) {}
        }

        c.connect(ip, port, pin, myDeviceName)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        navigateBack()
    }

    private fun navigateBack() {
        nsdHelper.stopDiscovery()
        client?.disconnect()
        val intent = Intent(this, ModeSelectActivity::class.java).apply {
            putExtra(ModeSelectActivity.EXTRA_FORCE_SELECTION, true)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        client?.disconnect()
        nsdHelper.stopDiscovery()
    }
}
