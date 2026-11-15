package com.example.xemulify

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.input.InputManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.xemulify.core.CoreManager
import com.example.xemulify.core.GameImporter
import com.example.xemulify.emulator.GameActivity
import com.example.xemulify.library.AppShortcut
import com.example.xemulify.library.Game
import com.example.xemulify.library.GameRepository
import com.example.xemulify.library.LibraryItem
import com.example.xemulify.library.ShortcutStore
import com.example.xemulify.remote.QrHelper
import com.example.xemulify.remote.RemoteControllerManager
import com.example.xemulify.remote.RemoteProtocol
import com.example.xemulify.remote.RemoteServer
import com.example.xemulify.settings.ControlsActivity
import com.example.xemulify.settings.GeneralSettingsActivity
import com.example.xemulify.store.GameDownloader
import com.example.xemulify.store.StoreAdapter
import com.example.xemulify.store.StoreDetailsDialog
import com.example.xemulify.store.StoreGame
import com.example.xemulify.store.StoreRepository
import com.bumptech.glide.Glide
import java.io.File
import java.util.concurrent.Executors

/**
 * Home: Secciones apiladas (Tienda, Biblioteca, Accesos Directos y Ajustes).
 * La sección enfocada se muestra brillante y las demás atenuadas ("fantasma").
 */
class MainActivity : FragmentActivity() {

    private enum class Section { STORE, GAMES, SHORTCUTS, SETTINGS }

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private lateinit var navStore: TextView
    private lateinit var navGames: TextView
    private lateinit var navShortcuts: TextView
    private lateinit var navSettings: TextView
    private lateinit var layoutPlayerBadges: LinearLayout
    private lateinit var scroll: NestedScrollView
    private lateinit var secStore: View
    private lateinit var secGames: View
    private lateinit var secShortcuts: View
    private lateinit var secSettings: View
    private lateinit var gridStore: RecyclerView
    private lateinit var gridGames: RecyclerView
    private lateinit var gridShortcuts: RecyclerView
    private lateinit var gridSettings: RecyclerView

    private val storeAdapter = StoreAdapter()
    private val gamesAdapter = HomeAdapter(navLeftId = R.id.nav_games, jumpToHubOnTopRow = true)
    private val shortcutsAdapter = HomeAdapter(navLeftId = R.id.nav_shortcuts, jumpToHubOnTopRow = false)
    private val settingsAdapter = HomeAdapter(navLeftId = R.id.nav_settings, jumpToHubOnTopRow = false)

    private var activeSection = Section.GAMES
    private var firstLoad = true

    private var inputManager: InputManager? = null
    private val inputDeviceListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) { main.post { updatePlayerBadges() } }
        override fun onInputDeviceRemoved(deviceId: Int) { main.post { updatePlayerBadges() } }
        override fun onInputDeviceChanged(deviceId: Int) { main.post { updatePlayerBadges() } }
    }

    private val pickGamesLauncher =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (!uris.isNullOrEmpty()) importGames(uris)
        }

    private lateinit var btnControllersHub: View
    private lateinit var txtControllersLabel: TextView
    private var activeCustomDialog: Dialog? = null

    // Estados para traducir el stick analógico izquierdo a D-pad (con histéresis)
    private var stickDpadX = 0 // -1: LEFT, 0: CENTER, 1: RIGHT
    private var stickDpadY = 0 // -1: UP, 0: CENTER, 1: DOWN

    private fun ensureFocus() {
        val cf = currentFocus
        if (cf != null && cf.isAttachedToWindow && cf.isShown) return

        val targetGrid = when (activeSection) {
            Section.STORE -> gridStore
            Section.GAMES -> gridGames
            Section.SHORTCUTS -> gridShortcuts
            Section.SETTINGS -> gridSettings
        }
        val targetNav = when (activeSection) {
            Section.STORE -> navStore
            Section.GAMES -> navGames
            Section.SHORTCUTS -> navShortcuts
            Section.SETTINGS -> navSettings
        }
        val firstChild = targetGrid.getChildAt(0)
        if (firstChild != null && firstChild.isFocusable) {
            firstChild.requestFocus()
        } else {
            targetNav.requestFocus()
        }
    }

    private fun sendKeyPulse(keyCode: Int) {
        val activeDialog = activeCustomDialog
        if (activeDialog != null && activeDialog.isShowing) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                activeDialog.dismiss()
                return
            }
            activeDialog.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            activeDialog.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
            return
        }

        ensureFocus()
        dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    private val homeRemoteInputSink = object : RemoteControllerManager.InputSink {
        override fun onRemoteButtonChanged(playerSlot: Int, button: Int, isPressed: Boolean) {
            if (!isPressed) return
            main.post {
                val keyEventCode = when (button) {
                    KeyEvent.KEYCODE_DPAD_UP -> KeyEvent.KEYCODE_DPAD_UP
                    KeyEvent.KEYCODE_DPAD_DOWN -> KeyEvent.KEYCODE_DPAD_DOWN
                    KeyEvent.KEYCODE_DPAD_LEFT -> KeyEvent.KEYCODE_DPAD_LEFT
                    KeyEvent.KEYCODE_DPAD_RIGHT -> KeyEvent.KEYCODE_DPAD_RIGHT
                    KeyEvent.KEYCODE_BUTTON_A -> KeyEvent.KEYCODE_DPAD_CENTER
                    KeyEvent.KEYCODE_BUTTON_B -> KeyEvent.KEYCODE_BACK
                    KeyEvent.KEYCODE_BUTTON_X -> KeyEvent.KEYCODE_DPAD_CENTER
                    KeyEvent.KEYCODE_BUTTON_START -> KeyEvent.KEYCODE_DPAD_CENTER
                    KeyEvent.KEYCODE_BUTTON_SELECT -> KeyEvent.KEYCODE_BACK
                    else -> null
                }
                keyEventCode?.let { sendKeyPulse(it) }
            }
        }

        override fun onRemoteMotionChanged(playerSlot: Int, axisSource: Int, x: Float, y: Float) {
            if (axisSource != 1) return
            main.post {
                val threshold = 0.50f
                val release = 0.30f

                // Eje horizontal (X)
                if (x < -threshold && stickDpadX != -1) {
                    stickDpadX = -1
                    sendKeyPulse(KeyEvent.KEYCODE_DPAD_LEFT)
                } else if (x > threshold && stickDpadX != 1) {
                    stickDpadX = 1
                    sendKeyPulse(KeyEvent.KEYCODE_DPAD_RIGHT)
                } else if (Math.abs(x) < release) {
                    stickDpadX = 0
                }

                // Eje vertical (Y)
                if (y < -threshold && stickDpadY != -1) {
                    stickDpadY = -1
                    sendKeyPulse(KeyEvent.KEYCODE_DPAD_UP)
                } else if (y > threshold && stickDpadY != 1) {
                    stickDpadY = 1
                    sendKeyPulse(KeyEvent.KEYCODE_DPAD_DOWN)
                } else if (Math.abs(y) < release) {
                    stickDpadY = 0
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        navStore = findViewById(R.id.nav_store)
        navGames = findViewById(R.id.nav_games)
        navShortcuts = findViewById(R.id.nav_shortcuts)
        navSettings = findViewById(R.id.nav_settings)
        layoutPlayerBadges = findViewById(R.id.layout_player_badges)
        btnControllersHub = findViewById(R.id.btn_controllers_hub)
        txtControllersLabel = findViewById(R.id.txt_controllers_label)
        scroll = findViewById(R.id.scroll)
        secStore = findViewById(R.id.sec_store)
        secGames = findViewById(R.id.sec_games)
        secShortcuts = findViewById(R.id.sec_shortcuts)
        secSettings = findViewById(R.id.sec_settings)
        gridStore = findViewById(R.id.grid_store)
        gridGames = findViewById(R.id.grid_games)
        gridShortcuts = findViewById(R.id.grid_shortcuts)
        gridSettings = findViewById(R.id.grid_settings)

        btnControllersHub.setOnClickListener { showPairingDialog() }
        btnControllersHub.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                val grid = when (activeSection) {
                    Section.STORE -> gridStore
                    Section.GAMES -> gridGames
                    Section.SHORTCUTS -> gridShortcuts
                    Section.SETTINGS -> gridSettings
                }
                val child = grid.getChildAt(0)
                if (child != null) {
                    child.requestFocus()
                    return@setOnKeyListener true
                }
            }
            false
        }

        inputManager = getSystemService(Context.INPUT_SERVICE) as? InputManager
        inputManager?.registerInputDeviceListener(inputDeviceListener, main)

        // Iniciar el servidor de mandos remotos para que la TV reciba conexiones
        RemoteControllerManager.startServer(this)
        RemoteControllerManager.addConnectionListener(object : RemoteControllerManager.ConnectionListener {
            override fun onClientConnected(session: RemoteServer.ClientSession) {
                main.post { updateControllersIndicator() }
            }
            override fun onClientDisconnected(session: RemoteServer.ClientSession) {
                main.post { updateControllersIndicator() }
            }
            override fun onClientsChanged(clients: List<RemoteServer.ClientSession>) {
                main.post { updateControllersIndicator() }
            }
        })

        gridStore.layoutManager = GridLayoutManager(this, 4)
        gridGames.layoutManager = GridLayoutManager(this, 4)
        gridShortcuts.layoutManager = GridLayoutManager(this, 4)
        gridSettings.layoutManager = GridLayoutManager(this, 4)
        gridStore.adapter = storeAdapter
        gridGames.adapter = gamesAdapter
        gridShortcuts.adapter = shortcutsAdapter
        gridSettings.adapter = settingsAdapter
        gridStore.isNestedScrollingEnabled = false
        gridGames.isNestedScrollingEnabled = false
        gridShortcuts.isNestedScrollingEnabled = false
        gridSettings.isNestedScrollingEnabled = false

        storeAdapter.onGameClick = { storeGame -> showStoreGameDetails(storeGame) }
        storeAdapter.onFocus = { setActiveSection(Section.STORE) }

        val onClick: (LibraryItem) -> Unit = { item ->
            when (item) {
                is LibraryItem.GameItem -> launchGame(item.game)
                is LibraryItem.ShortcutItem -> launchApp(item.shortcut)
                is LibraryItem.ActionItem -> onAction(item.id)
            }
        }
        gamesAdapter.onClick = onClick
        shortcutsAdapter.onClick = onClick
        settingsAdapter.onClick = onClick

        gamesAdapter.onFocus = { setActiveSection(Section.GAMES) }
        shortcutsAdapter.onFocus = { setActiveSection(Section.SHORTCUTS) }
        settingsAdapter.onFocus = { setActiveSection(Section.SETTINGS) }

        // OK mantenido sobre un acceso directo: ofrecer quitarlo.
        shortcutsAdapter.onLongClick = { item ->
            if (item is LibraryItem.ShortcutItem) { confirmRemoveShortcut(item.shortcut); true } else false
        }

        setupNav(navStore, Section.STORE)
        setupNav(navGames, Section.GAMES)
        setupNav(navShortcuts, Section.SHORTCUTS)
        setupNav(navSettings, Section.SETTINGS)

        // Estado inicial: Biblioteca activa a pantalla completa
        setActiveSection(Section.GAMES, force = true)
    }

    private val periodicControllerCheck = object : Runnable {
        override fun run() {
            updatePlayerBadges()
            main.postDelayed(this, 1500L)
        }
    }

    override fun onResume() {
        super.onResume()
        reloadAll()
        updateControllersIndicator()
        RemoteControllerManager.inputSink = homeRemoteInputSink
        main.post(periodicControllerCheck)
    }

    override fun onPause() {
        main.removeCallbacks(periodicControllerCheck)
        if (RemoteControllerManager.inputSink === homeRemoteInputSink) {
            RemoteControllerManager.inputSink = null
        }
        super.onPause()
    }

    private fun updateControllersIndicator() {
        txtControllersLabel.text = getString(R.string.btn_add_player)
        updatePlayerBadges()
    }

    private class ActivePlayer(
        val slot: Int,
        val pingText: String
    )

    private fun isRealPhysicalGamepad(device: InputDevice): Boolean {
        if (device.isVirtual) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !device.isExternal) return false

        val name = device.name.lowercase()
        val ignoredWords = listOf("remote", "cec", "touch", "keyboard", "keypad", "mouse", "stylus", "sensor", "ir", "control", "voice", "front", "back", "power", "volume", "gpio")
        if (ignoredWords.any { name.contains(it) }) return false

        val sources = device.sources
        val isGamepad = (sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
        val isJoystick = (sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
        if (!isGamepad && !isJoystick) return false

        val hasFaceKeys = device.hasKeys(
            KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_BUTTON_B,
            KeyEvent.KEYCODE_BUTTON_X,
            KeyEvent.KEYCODE_BUTTON_Y
        )
        val hasAllFaceKeys = hasFaceKeys.size == 4 && hasFaceKeys.all { it }
        if (!hasAllFaceKeys) return false

        val hasShoulders = device.hasKeys(KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1).any { it }
        val hasAnalog = device.getMotionRange(MotionEvent.AXIS_X) != null && device.getMotionRange(MotionEvent.AXIS_Y) != null

        return hasShoulders || hasAnalog
    }

    private fun updatePlayerBadges() {
        val container = layoutPlayerBadges
        val activePlayers = mutableListOf<ActivePlayer>()

        // 1. Mandos remotos conectados (celulares vía Wi-Fi)
        val clients = RemoteControllerManager.server?.getConnectedClients() ?: emptyList()
        clients.forEach { client ->
            val simulatedPing = (8..15).random()
            activePlayers.add(ActivePlayer(client.playerSlot, "${simulatedPing}ms"))
        }

        // 2. Mandos físicos reales (Bluetooth / USB)
        val deviceIds = InputDevice.getDeviceIds()
        var physicalCount = 0
        for (id in deviceIds) {
            val dev = InputDevice.getDevice(id) ?: continue
            if (isRealPhysicalGamepad(dev)) physicalCount++
        }
        var nextSlot = 0
        val usedSlots = activePlayers.map { it.slot }.toSet()
        for (i in 0 until physicalCount) {
            while (usedSlots.contains(nextSlot)) {
                nextSlot++
            }
            activePlayers.add(ActivePlayer(nextSlot, "<1ms"))
            nextSlot++
        }

        activePlayers.sortBy { it.slot }

        // Si no hay jugadores conectados, ocultar completamente el contenedor
        if (activePlayers.isEmpty()) {
            container.removeAllViews()
            container.visibility = View.GONE
            return
        }

        container.visibility = View.VISIBLE

        val currentSlots = (0 until container.childCount).mapNotNull {
            container.getChildAt(it).tag as? Int
        }
        val targetSlots = activePlayers.map { it.slot }
        if (currentSlots == targetSlots) return

        container.removeAllViews()
        for (player in activePlayers) {
            val badge = buildModernPlayerBadge(player.slot, player.pingText)
            badge.tag = player.slot
            container.addView(badge)
        }
    }

    private fun buildModernPlayerBadge(slot: Int, pingText: String): View {
        val padH = (12 * resources.displayMetrics.density).toInt()
        val padV = (5 * resources.displayMetrics.density).toInt()

        val badge = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(padH, padV, padH, padV)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = (8 * resources.displayMetrics.density).toInt()
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 100 * resources.displayMetrics.density
                setColor(0xFF22242F.toInt())
                setStroke((1 * resources.displayMetrics.density).toInt().coerceAtLeast(1), 0xFF35394A.toInt())
            }
        }

        // LED verde moderno de conexión activa
        val dotView = TextView(this).apply {
            text = "● "
            setTextColor(0xFF22C55E.toInt())
            textSize = 10f
            includeFontPadding = false
        }
        badge.addView(dotView)

        // Nombre del jugador (Player 1, Player 2...)
        val titleView = TextView(this).apply {
            text = "Player ${slot + 1}"
            setTextColor(0xFFE6E6EA.toInt())
            textSize = 12f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            includeFontPadding = false
        }
        badge.addView(titleView)

        // Ping moderno
        val pingView = TextView(this).apply {
            text = "  ·  $pingText"
            setTextColor(0xFF10B981.toInt())
            textSize = 11f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            includeFontPadding = false
        }
        badge.addView(pingView)

        badge.alpha = 0f
        badge.scaleX = 0.9f
        badge.scaleY = 0.9f
        badge.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(180)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()

        return badge
    }

    private fun showPairingDialog() {
        val server = RemoteControllerManager.server ?: RemoteControllerManager.startServer(this)
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_pair_host, null)

        val txtPinCode = dialogView.findViewById<TextView>(R.id.txt_pin_code)
        val imgQrCode = dialogView.findViewById<ImageView>(R.id.img_qr_code)

        val localIp = RemoteControllerManager.nsdHelper?.getLocalIpAddress() ?: "127.0.0.1"

        fun refreshDialogUI() {
            txtPinCode.text = server.currentPin
            val qrPayload = RemoteProtocol.makeQrPayload(localIp, server.port, server.currentPin, Build.MODEL)
            val qrBitmap = QrHelper.generateQrBitmap(qrPayload, 300, 300)
            imgQrCode.setImageBitmap(qrBitmap)
        }

        refreshDialogUI()

        val dialog = Dialog(this, R.style.Theme_Xemulify_FullscreenDialog)
        activeCustomDialog = dialog
        dialog.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(0xFF15151C.toInt()))
            clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            statusBarColor = 0xFF15151C.toInt()
            navigationBarColor = 0xFF15151C.toInt()
        }
        dialog.setContentView(dialogView)

        // Auto-regeneración del PIN y QR cada 30 segundos
        val autoRefreshHandler = Handler(Looper.getMainLooper())
        val autoRefreshRunnable = object : Runnable {
            override fun run() {
                if (dialog.isShowing) {
                    server.regeneratePin()
                    refreshDialogUI()
                    autoRefreshHandler.postDelayed(this, 30_000L)
                }
            }
        }
        autoRefreshHandler.postDelayed(autoRefreshRunnable, 30_000L)

        // Escucha si un celular se conecta: se cierra automáticamente la pantalla
        val connectionListener = object : RemoteControllerManager.ConnectionListener {
            override fun onClientConnected(session: RemoteServer.ClientSession) {
                runOnUiThread {
                    updateControllersIndicator()
                    Toast.makeText(
                        this@MainActivity,
                        "¡Mando conectado! ${session.deviceName}",
                        Toast.LENGTH_SHORT
                    ).show()
                    if (dialog.isShowing) {
                        dialog.dismiss()
                    }
                }
            }
        }
        RemoteControllerManager.addConnectionListener(connectionListener)

        dialog.setOnDismissListener {
            autoRefreshHandler.removeCallbacks(autoRefreshRunnable)
            RemoteControllerManager.removeConnectionListener(connectionListener)
            if (activeCustomDialog === dialog) {
                activeCustomDialog = null
            }
            main.post { ensureFocus() }
        }

        dialog.show()

        // En Android y MIUI, setLayout debe aplicarse después de show() para ocupar el 100% de la pantalla sin bordes
        dialog.window?.let { window ->
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            window.decorView.setPadding(0, 0, 0, 0)
            window.decorView.setBackgroundColor(0xFF15151C.toInt())
        }

        dialog.setOnShowListener {
            dialog.window?.let { window ->
                window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                window.decorView.setPadding(0, 0, 0, 0)
            }
        }
    }

    override fun onDestroy() {
        inputManager?.unregisterInputDeviceListener(inputDeviceListener)
        io.shutdown()
        RemoteControllerManager.stopServer()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (activeSection != Section.GAMES) {
            setActiveSection(Section.GAMES)
            navGames.requestFocus()
        } else {
            super.onBackPressed()
        }
    }

    private fun showStoreGameDetails(storeGame: StoreGame) {
        val details = StoreDetailsDialog(
            activity = this,
            game = storeGame,
            onPlayRequested = { game ->
                val localGame = GameRepository.listGames(this).find {
                    it.title.equals(game.title, true) ||
                    it.title.equals(GameDownloader.sanitizeFileName(game.title), true) ||
                    it.title.startsWith(game.title.take(10), true)
                }
                if (localGame != null) {
                    launchGame(localGame)
                } else {
                    Toast.makeText(this, "Cargando juego...", Toast.LENGTH_SHORT).show()
                    reloadAll()
                }
            },
            onGameDownloaded = { _ ->
                runOnUiThread {
                    reloadAll()
                    storeAdapter.notifyDataSetChanged()
                }
            }
        )
        activeCustomDialog = details.innerDialog
        details.onDismiss = {
            if (activeCustomDialog === details.innerDialog) {
                activeCustomDialog = null
            }
            main.post { ensureFocus() }
        }
        details.show()
    }

    // ---- Secciones con cambio instantáneo por foco en el sidebar ------------

    private fun setActiveSection(s: Section, force: Boolean = false) {
        if (!force && activeSection == s) return
        activeSection = s

        secStore.visibility = if (s == Section.STORE) View.VISIBLE else View.GONE
        secGames.visibility = if (s == Section.GAMES) View.VISIBLE else View.GONE
        secShortcuts.visibility = if (s == Section.SHORTCUTS) View.VISIBLE else View.GONE
        secSettings.visibility = if (s == Section.SETTINGS) View.VISIBLE else View.GONE

        val activeSecView = when (s) {
            Section.STORE -> secStore
            Section.GAMES -> secGames
            Section.SHORTCUTS -> secShortcuts
            Section.SETTINGS -> secSettings
        }
        activeSecView.alpha = 0f
        activeSecView.animate().alpha(1f).setDuration(130).start()

        navStore.isActivated = s == Section.STORE
        navGames.isActivated = s == Section.GAMES
        navShortcuts.isActivated = s == Section.SHORTCUTS
        navSettings.isActivated = s == Section.SETTINGS

        styleNav(navStore, navStore.isActivated, navStore.hasFocus())
        styleNav(navGames, navGames.isActivated, navGames.hasFocus())
        styleNav(navShortcuts, navShortcuts.isActivated, navShortcuts.hasFocus())
        styleNav(navSettings, navSettings.isActivated, navSettings.hasFocus())

        btnControllersHub.nextFocusDownId = when (s) {
            Section.STORE -> R.id.grid_store
            Section.GAMES -> R.id.grid_games
            Section.SHORTCUTS -> R.id.grid_shortcuts
            Section.SETTINGS -> R.id.grid_settings
        }

        scroll.post { scroll.scrollTo(0, 0) }
    }

    private fun setupNav(view: TextView, target: Section) {
        view.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                setActiveSection(target)
            }
            styleNav(view, view.isActivated, hasFocus)
        }
        view.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                val grid = when (target) {
                    Section.STORE -> gridStore
                    Section.GAMES -> gridGames
                    Section.SHORTCUTS -> gridShortcuts
                    Section.SETTINGS -> gridSettings
                }
                val child = grid.getChildAt(0)
                if (child != null) {
                    child.requestFocus()
                    return@setOnKeyListener true
                }
            }
            false
        }
        view.setOnClickListener {
            setActiveSection(target)
            val grid = when (target) {
                Section.STORE -> gridStore
                Section.GAMES -> gridGames
                Section.SHORTCUTS -> gridShortcuts
                Section.SETTINGS -> gridSettings
            }
            grid.post { grid.getChildAt(0)?.requestFocus() }
        }
    }

    private fun styleNav(view: TextView, activated: Boolean, focused: Boolean) {
        val color = when {
            focused -> 0xFFFFFFFF.toInt()
            activated -> 0xFF9B7BFF.toInt()
            else -> 0xFF9A9AA5.toInt()
        }
        view.setTextColor(color)
    }

    // ---- Contenido -----------------------------------------------------------

    private fun reloadAll() {
        storeAdapter.submit(StoreRepository.getCatalog())
        submit(gridGames, gamesAdapter, buildGames())
        submit(gridShortcuts, shortcutsAdapter, buildShortcuts())
        submit(gridSettings, settingsAdapter, buildSettings())
        if (firstLoad) {
            firstLoad = false
            gridGames.post { gridGames.getChildAt(0)?.requestFocus() }
        }
    }

    /** Diferir el submit evita el crash "RecyclerView is computing a layout" al volver de otra pantalla. */
    private fun submit(grid: RecyclerView, adapter: HomeAdapter, items: List<LibraryItem>) {
        if (grid.isComputingLayout) grid.post { adapter.submit(items) } else adapter.submit(items)
    }

    /** Biblioteca: solo juegos de PS1 + acción para añadir juego. */
    private fun buildGames(): List<LibraryItem> {
        val list = ArrayList<LibraryItem>()
        GameRepository.listGames(this).forEach { list.add(LibraryItem.GameItem(it)) }
        list.add(
            LibraryItem.ActionItem(
                LibraryItem.ActionItem.ADD,
                getString(R.string.action_add_game),
                getString(R.string.action_add_game_sub),
                R.drawable.ic_action_add,
            )
        )
        return list
    }

    /** Accesos directos: solo apps añadidas + acción para añadir nueva app. */
    private fun buildShortcuts(): List<LibraryItem> {
        val list = ArrayList<LibraryItem>()
        ShortcutStore(this).getShortcuts().forEach { list.add(LibraryItem.ShortcutItem(it)) }
        list.add(
            LibraryItem.ActionItem(
                LibraryItem.ActionItem.ADD_APP,
                getString(R.string.action_add_app),
                getString(R.string.action_add_app_sub),
                R.drawable.ic_action_add,
            )
        )
        return list
    }

    private fun buildSettings(): List<LibraryItem> = listOf(
        LibraryItem.ActionItem(
            LibraryItem.ActionItem.CONTROLS,
            getString(R.string.action_controls),
            getString(R.string.action_controls_sub),
            R.drawable.ic_action_keys,
        ),
        LibraryItem.ActionItem(
            LibraryItem.ActionItem.GENERAL,
            getString(R.string.action_general),
            getString(R.string.action_general_sub),
            R.drawable.ic_action_general,
        ),
    )

    // ---- Acciones ------------------------------------------------------------

    private fun onAction(id: String) {
        when (id) {
            LibraryItem.ActionItem.ADD -> pickGamesLauncher.launch(arrayOf("*/*"))
            LibraryItem.ActionItem.ADD_APP -> showAppPicker()
            LibraryItem.ActionItem.CONTROLS -> startActivity(Intent(this, ControlsActivity::class.java))
            LibraryItem.ActionItem.GENERAL -> startActivity(Intent(this, GeneralSettingsActivity::class.java))
        }
    }

    private fun showAppPicker() {
        val pm = packageManager
        val seen = HashSet<String>()
        val apps = ArrayList<AppShortcut>()
        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
            for (ri in pm.queryIntentActivities(intent, 0)) {
                val pkg = ri.activityInfo.packageName
                if (pkg == packageName || !seen.add(pkg)) continue
                apps.add(AppShortcut(pkg, ri.loadLabel(pm).toString()))
            }
        }
        apps.sortBy { it.label.lowercase() }

        if (apps.isEmpty()) {
            Toast.makeText(this, R.string.apps_none, Toast.LENGTH_LONG).show()
            return
        }
        val labels = apps.map { it.label }.toTypedArray()
        AlertDialog.Builder(this, R.style.Theme_Xemulify_Dialog)
            .setTitle(R.string.action_add_app)
            .setItems(labels) { _, which ->
                ShortcutStore(this).add(apps[which])
                submit(gridShortcuts, shortcutsAdapter, buildShortcuts())
            }
            .show()
    }

    /** Confirma y quita un acceso directo de la biblioteca (OK mantenido sobre la tarjeta). */
    private fun confirmRemoveShortcut(shortcut: AppShortcut) {
        AlertDialog.Builder(this, R.style.Theme_Xemulify_Dialog)
            .setTitle(R.string.shortcut_remove_title)
            .setMessage(getString(R.string.shortcut_remove_msg, shortcut.label))
            .setPositiveButton(R.string.shortcut_remove) { _, _ ->
                ShortcutStore(this).remove(shortcut.packageName)
                submit(gridShortcuts, shortcutsAdapter, buildShortcuts())
            }
            .setNegativeButton(R.string.cheat_cancel, null)
            .show()
    }

    private fun launchApp(shortcut: AppShortcut) {
        val pm = packageManager
        val intent = pm.getLeanbackLaunchIntentForPackage(shortcut.packageName)
            ?: pm.getLaunchIntentForPackage(shortcut.packageName)
        if (intent == null) {
            Toast.makeText(this, getString(R.string.app_not_found, shortcut.label), Toast.LENGTH_LONG).show()
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun launchGame(game: Game) {
        if (!CoreManager.isCoreAvailable(this)) {
            Toast.makeText(this, R.string.error_core_missing, Toast.LENGTH_LONG).show()
            return
        }
        if (!CoreManager.hasBios(this)) {
            Toast.makeText(this, R.string.warn_no_bios, Toast.LENGTH_LONG).show()
        }
        GameActivity.start(this, game.file.absolutePath, game.id)
    }

    private fun importGames(uris: List<Uri>) {
        val context = applicationContext
        Toast.makeText(context, R.string.importing, Toast.LENGTH_SHORT).show()
        io.execute {
            try {
                val imported = GameImporter.import(context, uris)
                main.post {
                    Toast.makeText(context, getString(R.string.imported_ok, imported.name), Toast.LENGTH_LONG).show()
                    submit(gridGames, gamesAdapter, buildGames())
                }
            } catch (e: Exception) {
                main.post {
                    Toast.makeText(context, getString(R.string.import_error, e.message ?: ""), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // ---- Adapter -------------------------------------------------------------

    private class HomeAdapter(
        var navLeftId: Int = View.NO_ID,
        var jumpToHubOnTopRow: Boolean = false
    ) : RecyclerView.Adapter<HomeAdapter.CardVH>() {

        private val items = ArrayList<LibraryItem>()
        var onClick: ((LibraryItem) -> Unit)? = null
        var onFocus: (() -> Unit)? = null
        var onLongClick: ((LibraryItem) -> Boolean)? = null

        fun submit(newItems: List<LibraryItem>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardVH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_home_card, parent, false)
            return CardVH(view)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: CardVH, position: Int) {
            val item = items[position]
            val context = holder.itemView.context
            when (item) {
                is LibraryItem.GameItem -> {
                    holder.title.text = item.game.title
                    holder.subtitle.text = context.getString(R.string.card_playstation)
                    val coverFile = File(item.game.file.parentFile, "cover.png")
                    if (coverFile.exists()) {
                        Glide.with(context)
                            .load(coverFile)
                            .placeholder(R.drawable.ic_game_disc)
                            .error(R.drawable.ic_game_disc)
                            .into(holder.icon)
                    } else {
                        holder.icon.setImageResource(R.drawable.ic_game_disc)
                    }
                }
                is LibraryItem.ShortcutItem -> {
                    holder.title.text = item.shortcut.label
                    holder.subtitle.text = context.getString(R.string.card_app)
                    val icon = runCatching {
                        context.packageManager.getApplicationIcon(item.shortcut.packageName)
                    }.getOrNull()
                    if (icon != null) holder.icon.setImageDrawable(icon)
                    else holder.icon.setImageResource(R.drawable.ic_action_play)
                }
                is LibraryItem.ActionItem -> {
                    holder.title.text = item.title
                    holder.subtitle.text = item.subtitle
                    holder.icon.setImageResource(item.iconRes)
                }
            }
            // Al pulsar D-pad arriba en la fila superior, salta directamente a "Vincular nuevo mando" si aplica
            if (position < 4 && jumpToHubOnTopRow) {
                holder.itemView.nextFocusUpId = R.id.btn_controllers_hub
            } else {
                holder.itemView.nextFocusUpId = View.NO_ID
            }

            // Al pulsar D-pad izquierda en la primera columna, salta directamente al nav rail
            if (position % 4 == 0 && navLeftId != View.NO_ID) {
                holder.itemView.nextFocusLeftId = navLeftId
            } else {
                holder.itemView.nextFocusLeftId = View.NO_ID
            }

            holder.itemView.setOnClickListener { onClick?.invoke(item) }
            holder.itemView.setOnLongClickListener { onLongClick?.invoke(item) ?: false }
            holder.itemView.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) onFocus?.invoke()
                val scale = if (hasFocus) 1.05f else 1f
                v.animate().scaleX(scale).scaleY(scale).setDuration(140).start()
            }
        }

        class CardVH(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.img_thumbnail)
            val title: TextView = view.findViewById(R.id.txt_title)
            val subtitle: TextView = view.findViewById(R.id.txt_subtitle)
        }
    }

    companion object {
        private const val GHOST = 0.32f
    }
}
