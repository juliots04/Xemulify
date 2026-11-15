package com.example.xemulify.controller

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.example.xemulify.R
import com.example.xemulify.remote.HapticManager
import com.example.xemulify.remote.RemoteClient

class ControllerActivity : FragmentActivity() {

    private lateinit var gamepadView: VirtualGamepadView
    private lateinit var txtPlayerBadge: TextView
    private lateinit var txtHostName: TextView
    private lateinit var txtLatency: TextView
    private lateinit var btnThemeDropdown: TextView

    private val client = RemoteClient()
    private lateinit var hapticManager: HapticManager

    private var hostIp: String = "127.0.0.1"
    private var hostPort: Int = 8888
    private var pin: String = ""
    private var playerSlot: Int = 0
    private var isExiting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_controller)
        enterImmersiveMode()

        hostIp = intent.getStringExtra(EXTRA_HOST_IP) ?: "127.0.0.1"
        hostPort = intent.getIntExtra(EXTRA_HOST_PORT, 8888)
        pin = intent.getStringExtra(EXTRA_PIN) ?: ""
        playerSlot = intent.getIntExtra(EXTRA_PLAYER_SLOT, 0)
        val colorIndex = intent.getIntExtra(EXTRA_COLOR_INDEX, playerSlot)

        hapticManager = HapticManager(this)

        gamepadView = findViewById(R.id.virtual_gamepad)
        txtPlayerBadge = findViewById(R.id.txt_player_badge)
        txtHostName = findViewById(R.id.txt_host_name)
        txtLatency = findViewById(R.id.txt_latency)
        btnThemeDropdown = findViewById(R.id.btn_theme_dropdown)

        gamepadView.hapticManager = hapticManager

        // Cargar preferencias de tema y vibración
        loadPreferences()

        // Configurar selector desglosable ultra sutil de tema
        setupThemeSelector()

        // Estilizar badge de jugador (P1 Azul, P2 Rojo, P3 Verde, P4 Amarillo)
        updatePlayerBadge(playerSlot, colorIndex)
        txtHostName.text = "Xemulify"

        // Callback de entradas
        gamepadView.listener = object : VirtualGamepadView.Listener {
            override fun onInputStateChanged(
                buttonMask: Short,
                stickLX: Byte,
                stickLY: Byte,
                stickRX: Byte,
                stickRY: Byte
            ) {
                client.sendInput(buttonMask, stickLX, stickLY, stickRX, stickRY)
            }

            override fun onOpenSettingsRequested() {
                // Configuración integrada en pantalla
            }
        }

        // Listener del cliente de red
        client.listener = object : RemoteClient.Listener {
            override fun onConnected(playerSlot: Int, slotColorIndex: Int) {
                this@ControllerActivity.playerSlot = playerSlot
                updatePlayerBadge(playerSlot, slotColorIndex)
            }

            override fun onConnectionFailed(reason: String) {
                if (isFinishing || isExiting) return
                isExiting = true
                Toast.makeText(this@ControllerActivity, "Desconectado: $reason", Toast.LENGTH_SHORT).show()
                val intent = Intent(this@ControllerActivity, PairingActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(intent)
                finish()
            }

            override fun onDisconnected() {
                if (isFinishing || isExiting) return
                isExiting = true
                Toast.makeText(this@ControllerActivity, "Consola desconectada", Toast.LENGTH_SHORT).show()
                val intent = Intent(this@ControllerActivity, PairingActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(intent)
                finish()
            }

            override fun onLatencyUpdated(pingMs: Long) {
                txtLatency.text = "$pingMs ms"
                val color = when {
                    pingMs < 8 -> Color.parseColor("#4ADE80") // Verde
                    pingMs < 20 -> Color.parseColor("#FBBF24") // Amarillo
                    else -> Color.parseColor("#F87171")       // Rojo
                }
                txtLatency.setTextColor(color)
                findViewById<android.widget.ImageView>(R.id.img_wifi_icon)?.setColorFilter(color)
            }

            override fun onRumbleReceived(durationMs: Long, strongMotor: Boolean) {
                hapticManager.performGameRumble(durationMs, strongMotor)
            }
        }

        val myDeviceName = "${Build.MANUFACTURER} ${Build.MODEL}".take(24)
        client.connect(hostIp, hostPort, pin, myDeviceName)
    }

    private fun updatePlayerBadge(slot: Int, colorIndex: Int) {
        val slotNum = slot + 1
        txtPlayerBadge.text = "P$slotNum"
        val bgColor = when (colorIndex % 4) {
            0 -> Color.parseColor("#2563EB") // P1 Azul
            1 -> Color.parseColor("#DC2626") // P2 Rojo
            2 -> Color.parseColor("#16A34A") // P3 Verde
            else -> Color.parseColor("#D97706") // P4 Amarillo
        }
        val bg = txtPlayerBadge.background
        if (bg is android.graphics.drawable.GradientDrawable) {
            bg.setColor(bgColor)
        } else {
            txtPlayerBadge.setBackgroundColor(bgColor)
        }
    }

    private fun setupThemeSelector() {
        val themes = arrayOf(
            ControllerTheme.CLASSIC_DARK_GRAY,
            ControllerTheme.STEALTH_BLACK,
            ControllerTheme.CARBON_GRAY
        )

        applyTheme(gamepadView.theme, save = false)

        btnThemeDropdown.setOnClickListener { view ->
            val popup = PopupMenu(this, view)
            themes.forEachIndexed { index, theme ->
                popup.menu.add(0, index, index, theme.displayName)
            }
            popup.setOnMenuItemClickListener { menuItem ->
                val chosen = themes.getOrElse(menuItem.itemId) { ControllerTheme.CLASSIC_DARK_GRAY }
                applyTheme(chosen, save = true)
                true
            }
            popup.show()
        }

        // Posicionar el selector de forma sutil justo debajo de la marca de agua central "XEMULIFY"
        gamepadView.post {
            val h = gamepadView.height.toFloat()
            if (h > 0f) {
                btnThemeDropdown.y = h * 0.40f
            }
        }
    }

    private fun applyTheme(theme: ControllerTheme, save: Boolean = true) {
        gamepadView.theme = theme
        if (save) {
            savePreferences()
        }

        // Texto minimalista y sutil en minúsculas con flecha
        btnThemeDropdown.text = "tema: ${theme.shortName.lowercase()} ▾"
        btnThemeDropdown.setTextColor(Color.parseColor("#80FFFFFF"))
        btnThemeDropdown.setBackgroundResource(R.drawable.bg_subtle_pill)

        // Fondo de pantalla completa dinámico
        findViewById<View>(R.id.controller_root)?.setBackgroundColor(theme.bodyBgColor)
    }

    private fun loadPreferences() {
        val prefs = getSharedPreferences(PREFS_CONTROLLER, Context.MODE_PRIVATE)
        val themeName = prefs.getString(KEY_THEME, ControllerTheme.CLASSIC_DARK_GRAY.name)

        gamepadView.theme = ControllerTheme.safeValueOf(themeName)
        // La vibración realista se establece por defecto y no es configurable
        hapticManager.strength = HapticManager.HapticStrength.MEDIUM
    }

    private fun savePreferences() {
        getSharedPreferences(PREFS_CONTROLLER, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME, gamepadView.theme.name)
            .putString(KEY_HAPTIC, HapticManager.HapticStrength.MEDIUM.name)
            .apply()
    }

    override fun onResume() {
        super.onResume()
        enterImmersiveMode()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enterImmersiveMode()
        }
    }

    private fun enterImmersiveMode() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.decorView.post {
                    try {
                        window.decorView.windowInsetsController?.let { controller ->
                            controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                            controller.systemBarsBehavior =
                                WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        }
                    } catch (_: Exception) {}
                }
            }
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
            )
        } catch (_: Exception) {}
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        AlertDialog.Builder(this, R.style.Theme_Xemulify)
            .setTitle("¿Desconectar mando?")
            .setMessage("¿Deseas salir del modo mando y desconectarte de la TV?")
            .setPositiveButton("Desconectar") { _, _ ->
                isExiting = true
                client.disconnect()
                val intent = Intent(this, PairingActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(intent)
                finish()
            }
            .setNegativeButton("Continuar jugando", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        client.disconnect()
    }

    companion object {
        const val EXTRA_HOST_IP = "host_ip"
        const val EXTRA_HOST_PORT = "host_port"
        const val EXTRA_PIN = "pin"
        const val EXTRA_PLAYER_SLOT = "player_slot"
        const val EXTRA_COLOR_INDEX = "color_index"

        private const val PREFS_CONTROLLER = "xemulify_controller_prefs"
        private const val KEY_THEME = "controller_theme"
        private const val KEY_HAPTIC = "controller_haptic"
    }
}
