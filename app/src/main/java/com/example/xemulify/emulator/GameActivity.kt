package com.example.xemulify.emulator

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.example.xemulify.R
import com.example.xemulify.cheats.CheatConfigActivity
import com.example.xemulify.cheats.CheatStore
import com.example.xemulify.core.CoreManager
import com.example.xemulify.input.KeyMappingStore
import com.example.xemulify.input.PsxInputTranslator
import com.example.xemulify.settings.ControlsActivity
import com.example.xemulify.settings.GeneralSettingsActivity
import com.example.xemulify.settings.SettingsStore
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.GLRetroViewData
import com.swordfish.libretrodroid.ShaderConfig
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

/**
 * Pantalla de juego: monta el [GLRetroView] con el core de PS1, reparte la entrada
 * del teclado/mando y ofrece un menú en-juego (guardar/cargar estado, teclas, salir).
 */
class GameActivity : FragmentActivity() {

    private lateinit var retroView: GLRetroView
    private lateinit var translator: PsxInputTranslator
    private lateinit var mappingStore: KeyMappingStore
    private lateinit var cheatStore: CheatStore
    private lateinit var settings: SettingsStore

    private lateinit var gameFile: File
    private lateinit var gameId: String

    private var started = false
    private var menuOpen = false
    private var currentResizeMode = 3
    private var fastForwarding = false
    private var hudVisible = false
    private var coreReady = false
    private var lastAppliedCheatCount = 0

    private val handler = Handler(Looper.getMainLooper())
    private val autosaveRunnable = object : Runnable {
        override fun run() {
            autosaveNow()
            handler.postDelayed(this, AUTOSAVE_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterImmersiveMode()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_game)

        val path = intent.getStringExtra(EXTRA_GAME_PATH)
        if (path == null) {
            finishWithMessage(getString(R.string.error_no_game))
            return
        }
        gameFile = File(path)
        gameId = intent.getStringExtra(EXTRA_GAME_ID) ?: gameFile.parentFile?.name ?: gameFile.nameWithoutExtension

        if (!CoreManager.isCoreAvailable(this)) {
            finishWithMessage(getString(R.string.error_core_missing))
            return
        }
        if (!gameFile.exists()) {
            finishWithMessage(getString(R.string.error_no_game))
            return
        }

        mappingStore = KeyMappingStore(this)
        cheatStore = CheatStore(this)
        settings = SettingsStore(this)
        currentResizeMode = if (settings.aspectFill) 3 else 0

        // Se aplaza el montaje del emulador hasta que el contenedor esté maquetado y la
        // ventana lista. Crear el GLRetroView demasiado pronto provocaba una carrera del
        // hilo GL del core (SIGSEGV) que hacía que fallara al primer/segundo intento.
        val container = findViewById<FrameLayout>(R.id.game_container)
        container.post { setupEmulator(container) }
    }

    private fun setupEmulator(container: FrameLayout) {
        if (started || isFinishing) return

        val data = GLRetroViewData(this).apply {
            coreFilePath = CoreManager.coreFile(this@GameActivity).absolutePath
            gameFilePath = gameFile.absolutePath
            systemDirectory = CoreManager.systemDir(this@GameActivity).absolutePath
            savesDirectory = CoreManager.savesDir(this@GameActivity).absolutePath
            shader = ShaderConfig.Default
            val sram = sramFile()
            if (sram.exists()) saveRAMState = sram.readBytes()
        }

        retroView = GLRetroView(this, data)
        val layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
            android.view.Gravity.CENTER
        )
        // En el índice 0 para que el HUD de controles quede dibujado por encima.
        container.addView(retroView, 0, layoutParams)
        lifecycle.addObserver(retroView)
        retroView.post { runCatching { retroView.setResizeMode(currentResizeMode) } }

        translator = PsxInputTranslator(
            retroView,
            mappingStore.buildP1Map(),
            mappingStore.buildP2Map(),
            mappingStore.buildGamepadMap(),
        )
        started = true

        // Los cheats se aplican cuando el core ya está corriendo (primer frame dibujado);
        // aplicarlos antes devolvería ERROR_CHEAT porque el juego aún no está cargado.
        lifecycleScope.launch {
            retroView.getGLRetroEvents()
                .filterIsInstance<GLRetroView.GLRetroEvents.FrameRendered>()
                .first()
            coreReady = true
            applyCheats()
        }

        applyAspect()
        syncControlsHud()
        scheduleAutosave()

        if (!CoreManager.hasBios(this)) {
            Toast.makeText(this, R.string.warn_no_bios, Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        if (started) {
            translator.updateMapping(
                mappingStore.buildP1Map(),
                mappingStore.buildP2Map(),
                mappingStore.buildGamepadMap(),
            )
            applyAspect()
            syncControlsHud()
            scheduleAutosave()
            // Refleja cambios hechos en la pantalla de trucos sin reiniciar el juego.
            if (coreReady) applyCheats()
        }
    }

    override fun onPause() {
        if (started) {
            handler.removeCallbacks(autosaveRunnable)
            setFastForward(false)
            persistSram()
            if (settings.autosaveEnabled) autosaveNow()
        }
        super.onPause()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!started || menuOpen) return super.dispatchKeyEvent(event)

        // Avance rápido: mantener pulsada la tecla acelera; al soltarla vuelve a 1x.
        if (event.keyCode == settings.fastForwardKeyCode && !isMenuKey(event.keyCode)) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> setFastForward(true)
                KeyEvent.ACTION_UP -> setFastForward(false)
            }
            return true
        }

        // Atajos de estado: guardado/carga rápida en la ranura de autoguardado.
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && !isMenuKey(event.keyCode)) {
            when (event.keyCode) {
                settings.quickSaveKeyCode -> { saveState(settings.autosaveSlot); return true }
                settings.quickLoadKeyCode -> { loadState(settings.autosaveSlot); return true }
            }
        }

        // El menú se abre al SOLTAR la tecla (ACTION_UP), no al pulsarla. Si se abriera en
        // ACTION_DOWN, el ACTION_UP de esa misma pulsación cerraría el diálogo al instante
        // (Escape/ATRÁS usan dismiss en ACTION_UP), por lo que solo parecía abrirse mientras
        // se mantenía la tecla pulsada. Consumimos ambas acciones para que la tecla no llegue
        // ni al traductor ni al sistema.
        if (isMenuKey(event.keyCode)) {
            if (event.action == KeyEvent.ACTION_UP) showInGameMenu()
            return true
        }
        if (translator.handleKeyEvent(event)) return true
        return super.dispatchKeyEvent(event)
    }

    /** Movimiento del mando (cruceta analógica / stick izquierdo) hacia el core. */
    override fun onGenericMotionEvent(event: android.view.MotionEvent): Boolean {
        if (started && !menuOpen && translator.handleMotionEvent(event)) return true
        return super.onGenericMotionEvent(event)
    }

    // ---- Cheats --------------------------------------------------------------

    /**
     * Vuelca todos los cheats del juego al core: cada uno en su ranura, con su estado
     * (activo/inactivo). Las ranuras que quedaron libres tras borrar un truco se apagan.
     */
    private fun applyCheats() {
        if (!started) return
        val cheats = cheatStore.getCheats(gameId)
        cheats.forEachIndexed { index, cheat ->
            runCatching {
                retroView.setCheat(index, cheat.enabled, CheatStore.normalizeCode(cheat.code))
            }
        }
        for (index in cheats.size until lastAppliedCheatCount) {
            runCatching { retroView.setCheat(index, false, "00000000 0000") }
        }
        lastAppliedCheatCount = cheats.size
    }

    // ---- Avance rápido -------------------------------------------------------

    private fun setFastForward(on: Boolean) {
        if (fastForwarding == on) return
        fastForwarding = on
        runCatching { retroView.frameSpeed = if (on) settings.fastForwardSpeed else 1 }
    }

    // ---- Autoguardado --------------------------------------------------------

    private fun scheduleAutosave() {
        handler.removeCallbacks(autosaveRunnable)
        if (settings.autosaveEnabled) {
            handler.postDelayed(autosaveRunnable, AUTOSAVE_INTERVAL_MS)
        }
    }

    private fun autosaveNow() {
        if (!started) return
        try {
            val bytes = retroView.serializeState()
            stateFile(settings.autosaveSlot).writeBytes(bytes)
        } catch (e: Exception) {
            // El core puede no estar listo; se reintenta en el siguiente ciclo.
        }
    }

    // ---- Menú en-juego -------------------------------------------------------

    private fun showInGameMenu() {
        translator.releaseAll()
        setFastForward(false)
        menuOpen = true
        val options = arrayOf(
            getString(R.string.menu_save_state),
            getString(R.string.menu_load_state),
            getString(R.string.menu_settings),
            getString(R.string.menu_exit),
        )
        val dialog = AlertDialog.Builder(this, R.style.Theme_Xemulify_Dialog)
            .setTitle(R.string.menu_title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showSlotDialog(saving = true)
                    1 -> showSlotDialog(saving = false)
                    2 -> showSettingsSubmenu()
                    3 -> finish()
                }
            }
            .setOnDismissListener { menuOpen = false }
            .create()
        dialog.dismissOnEscape()
        dialog.show()
    }

    /** Submenú "Ajustes": controles (primero), ajustes generales y cheats. */
    private fun showSettingsSubmenu() {
        menuOpen = true
        val options = arrayOf(
            getString(R.string.menu_configure_keys),
            getString(R.string.menu_general),
            getString(R.string.menu_cheats),
        )
        val dialog = AlertDialog.Builder(this, R.style.Theme_Xemulify_Dialog)
            .setTitle(R.string.menu_settings)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> startActivity(Intent(this, ControlsActivity::class.java))
                    1 -> startActivity(Intent(this, GeneralSettingsActivity::class.java))
                    2 -> CheatConfigActivity.start(this, gameId)
                }
            }
            .setOnDismissListener { menuOpen = false }
            .create()
        dialog.dismissOnEscape()
        dialog.show()
    }

    // ---- Estado visual desde Ajustes -----------------------------------------

    private fun applyAspect() {
        currentResizeMode = if (settings.aspectFill) 3 else 0
        runCatching { retroView.setResizeMode(currentResizeMode) }
    }

    private fun syncControlsHud() {
        hudVisible = settings.showControlsHud
        val hud = findViewById<View>(R.id.controls_hud)
        if (hudVisible) {
            buildControlsHud()
            hud.visibility = View.VISIBLE
        } else {
            hud.visibility = View.GONE
        }
    }

    private fun buildControlsHud() {
        val grid = findViewById<android.widget.GridLayout>(R.id.hud_grid)
        grid.removeAllViews()
        val map = mappingStore
        for (button in com.example.xemulify.input.PsxButton.values()) {
            val chip = layoutInflater.inflate(R.layout.item_hud_chip, grid, false)
            val icon = chip.findViewById<android.widget.ImageView>(R.id.hud_icon)
            val key = chip.findViewById<android.widget.TextView>(R.id.hud_key)
            icon.setImageResource(hudIconFor(button))
            key.text = KeyEvent.keyCodeToString(map.p1KeyCodeFor(button)).removePrefix("KEYCODE_")
            grid.addView(chip)
        }
    }

    private fun hudIconFor(button: com.example.xemulify.input.PsxButton): Int = when (button) {
        com.example.xemulify.input.PsxButton.DPAD_UP -> R.drawable.ic_dpad_up
        com.example.xemulify.input.PsxButton.DPAD_DOWN -> R.drawable.ic_dpad_down
        com.example.xemulify.input.PsxButton.DPAD_LEFT -> R.drawable.ic_dpad_left
        com.example.xemulify.input.PsxButton.DPAD_RIGHT -> R.drawable.ic_dpad_right
        com.example.xemulify.input.PsxButton.CROSS -> R.drawable.ic_ps_cross
        com.example.xemulify.input.PsxButton.CIRCLE -> R.drawable.ic_ps_circle
        com.example.xemulify.input.PsxButton.SQUARE -> R.drawable.ic_ps_square
        com.example.xemulify.input.PsxButton.TRIANGLE -> R.drawable.ic_ps_triangle
        com.example.xemulify.input.PsxButton.L1 -> R.drawable.ic_ps_l1
        com.example.xemulify.input.PsxButton.R1 -> R.drawable.ic_ps_r1
        com.example.xemulify.input.PsxButton.L2 -> R.drawable.ic_ps_l2
        com.example.xemulify.input.PsxButton.R2 -> R.drawable.ic_ps_r2
        com.example.xemulify.input.PsxButton.START -> R.drawable.ic_ps_start
        com.example.xemulify.input.PsxButton.SELECT -> R.drawable.ic_ps_select
    }

    private fun showSlotDialog(saving: Boolean) {
        menuOpen = true
        val labels = Array(SLOT_COUNT) { i ->
            val slot = i + 1
            val f = stateFile(slot)
            if (f.exists()) getString(R.string.slot_used, slot) else getString(R.string.slot_empty, slot)
        }
        val dialog = AlertDialog.Builder(this, R.style.Theme_Xemulify_Dialog)
            .setTitle(if (saving) R.string.menu_save_state else R.string.menu_load_state)
            .setItems(labels) { _, which ->
                val slot = which + 1
                if (saving) saveState(slot) else loadState(slot)
            }
            .setOnDismissListener { menuOpen = false }
            .create()
        dialog.dismissOnEscape()
        dialog.show()
    }

    /** Permite cerrar el diálogo con ESC del teclado (además de ATRÁS). */
    private fun AlertDialog.dismissOnEscape() {
        setOnKeyListener { d, keyCode, event ->
            if (event.action == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_ESCAPE) {
                d.dismiss()
                true
            } else {
                false
            }
        }
    }

    private fun saveState(slot: Int) {
        try {
            val bytes = retroView.serializeState()
            stateFile(slot).writeBytes(bytes)
            Toast.makeText(this, getString(R.string.state_saved, slot), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, R.string.state_error, Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadState(slot: Int) {
        val f = stateFile(slot)
        if (!f.exists() || f.length() == 0L) {
            Toast.makeText(this, R.string.slot_is_empty, Toast.LENGTH_SHORT).show()
            return
        }
        tryLoadState(f.readBytes(), slot, attempts = 6)
    }

    /**
     * Carga un estado comprobando el resultado de [GLRetroView.unserializeState]. Si el core
     * aún no está listo (devuelve false), reintenta un momento — antes se ignoraba el booleano
     * y parecía que "no cargaba".
     */
    private fun tryLoadState(bytes: ByteArray, slot: Int, attempts: Int) {
        val ok = runCatching { retroView.unserializeState(bytes) }.getOrDefault(false)
        when {
            ok -> Toast.makeText(this, getString(R.string.state_loaded, slot), Toast.LENGTH_SHORT).show()
            attempts > 0 -> handler.postDelayed({ tryLoadState(bytes, slot, attempts - 1) }, 300)
            else -> Toast.makeText(this, R.string.state_error, Toast.LENGTH_SHORT).show()
        }
    }

    // ---- Persistencia --------------------------------------------------------

    private fun persistSram() {
        try {
            val sram = retroView.serializeSRAM()
            if (sram.isNotEmpty()) sramFile().writeBytes(sram)
        } catch (e: Exception) {
            // La tarjeta de memoria puede no estar lista todavía; se ignora.
        }
    }

    private fun sramFile() = File(CoreManager.savesDir(this), "$gameId.srm")

    private fun stateFile(slot: Int) =
        File(CoreManager.statesDirFor(this, gameId), "slot$slot.state")

    // ---- Utilidades ----------------------------------------------------------

    private fun isMenuKey(keyCode: Int): Boolean = keyCode in MENU_KEYS

    private fun enterImmersiveMode() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
    }

    private fun finishWithMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }

    companion object {
        const val EXTRA_GAME_PATH = "extra_game_path"
        const val EXTRA_GAME_ID = "extra_game_id"
        private const val SLOT_COUNT = 4
        private const val AUTOSAVE_INTERVAL_MS = 60_000L

        /** Teclas reservadas que abren el menú (no son asignables a botones de PS1). */
        private val MENU_KEYS = setOf(
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_BUTTON_MODE,
            KeyEvent.KEYCODE_ESCAPE,
        )

        fun start(context: Context, gameFilePath: String, gameId: String) {
            val intent = Intent(context, GameActivity::class.java)
                .putExtra(EXTRA_GAME_PATH, gameFilePath)
                .putExtra(EXTRA_GAME_ID, gameId)
            context.startActivity(intent)
        }
    }
}
