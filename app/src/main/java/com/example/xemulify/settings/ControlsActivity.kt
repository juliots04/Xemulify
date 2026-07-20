package com.example.xemulify.settings

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.example.xemulify.R
import com.example.xemulify.input.KeyMappingStore
import com.example.xemulify.input.PsxButton
import com.example.xemulify.input.TouchMapping
import com.example.xemulify.input.TouchMappingStore

/**
 * Conf. controles (pantalla unificada):
 *  1) Botones de PS1 con tres columnas: Player 1, Player 2 y Mando (D-pad). El mando tradicional
 *     funciona sin configurar nada (la columna Mando trae los botones estándar por defecto).
 *  2) Atajos del emulador (avance rápido hasta x6, guardado/carga rápida).
 *  3) Mapeo táctil al final (botones tecla → toque para juegos táctiles).
 *
 * El título se desplaza; solo la cabecera de columnas queda fija.
 */
class ControlsActivity : FragmentActivity() {

    private enum class Hotkey { FAST_FORWARD, QUICK_SAVE, QUICK_LOAD }
    private enum class Col { P1, P2, GAMEPAD }

    private lateinit var store: KeyMappingStore
    private lateinit var settings: SettingsStore
    private lateinit var touchStore: TouchMappingStore

    private lateinit var scroll: ScrollView
    private lateinit var headerReal: View
    private lateinit var headerPinned: View
    private lateinit var ps1Container: LinearLayout
    private lateinit var hotkeysContainer: LinearLayout
    private lateinit var touchContainer: LinearLayout

    private val hotkeyButtons = LinkedHashMap<Hotkey, Button>()
    private var ffSpeedButton: Button? = null

    private sealed class Waiting {
        data class Ps1(val button: PsxButton, val col: Col) : Waiting()
        data class Hot(val hotkey: Hotkey) : Waiting()
        data class Touch(val id: String, val keyboard: Boolean) : Waiting()
    }
    private var waiting: Waiting? = null

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_controls)
        store = KeyMappingStore(this)
        settings = SettingsStore(this)
        touchStore = TouchMappingStore(this)

        scroll = findViewById(R.id.scroll)
        headerReal = findViewById(R.id.header_real)
        headerPinned = findViewById(R.id.header_pinned)
        ps1Container = findViewById(R.id.ps1_container)
        hotkeysContainer = findViewById(R.id.hotkeys_container)
        touchContainer = findViewById(R.id.touch_container)

        scroll.viewTreeObserver.addOnScrollChangedListener {
            val y = scroll.scrollY
            // Fija la cabecera solo mientras la tabla de botones sigue debajo de ella;
            // al pasar el final de la tabla (hotkeys/táctil), desaparece.
            val pastHeader = y >= headerReal.top
            val tableStillVisible = (ps1Container.bottom - y) > headerPinned.height
            headerPinned.visibility = if (pastHeader && tableStillVisible) View.VISIBLE else View.GONE
        }

        findViewById<Button>(R.id.btn_reset).setOnClickListener { resetKeys() }
        findViewById<Button>(R.id.btn_touch_add).setOnClickListener { addTouchMapping() }
        findViewById<Button>(R.id.btn_touch_positions).setOnClickListener {
            startActivity(Intent(this, TouchEditorActivity::class.java))
        }
        findViewById<Button>(R.id.btn_touch_overlay).setOnClickListener {
            touchStore.showOverlay = !touchStore.showOverlay
            refreshOverlayButton()
        }

        refreshOverlayButton()
        buildPs1()
        buildHotkeys()
        buildTouch()
    }

    override fun onResume() {
        super.onResume()
        buildTouch() // refresca posiciones tras volver del editor
    }

    // ---- Botones de PS1 (P1 / P2 / Mando) -----------------------------------

    private fun buildPs1() {
        ps1Container.removeAllViews()
        for (button in PsxButton.values()) {
            val row = layoutInflater.inflate(R.layout.item_controls_row, ps1Container, false)
            row.findViewById<ImageView>(R.id.img_button_icon).setImageResource(iconFor(button))
            row.findViewById<TextView>(R.id.txt_button_name).text = button.displayName
            val p1 = row.findViewById<Button>(R.id.btn_p1)
            val p2 = row.findViewById<Button>(R.id.btn_p2)
            val gp = row.findViewById<Button>(R.id.btn_gamepad)
            p1.text = keyLabel(store.p1KeyCodeFor(button))
            p2.text = keyLabel(store.p2KeyCodeFor(button))
            gp.text = keyLabel(store.gamepadKeyCodeFor(button))
            p1.setOnClickListener { startWaiting(Waiting.Ps1(button, Col.P1), p1) }
            p2.setOnClickListener { startWaiting(Waiting.Ps1(button, Col.P2), p2) }
            gp.setOnClickListener { startWaiting(Waiting.Ps1(button, Col.GAMEPAD), gp) }
            ps1Container.addView(row)
        }
    }

    // ---- Atajos del emulador -------------------------------------------------

    private fun buildHotkeys() {
        hotkeysContainer.removeAllViews()
        hotkeyButtons.clear()
        data class Def(val hotkey: Hotkey, val icon: Int, val name: String, val hasSpeed: Boolean)
        val defs = listOf(
            Def(Hotkey.FAST_FORWARD, R.drawable.ic_hotkey_ff, getString(R.string.hotkey_fast_forward), true),
            Def(Hotkey.QUICK_SAVE, R.drawable.ic_hotkey_save, getString(R.string.hotkey_quick_save), false),
            Def(Hotkey.QUICK_LOAD, R.drawable.ic_hotkey_load, getString(R.string.hotkey_quick_load), false),
        )
        for (def in defs) {
            val row = layoutInflater.inflate(R.layout.item_hotkey_row, hotkeysContainer, false)
            row.findViewById<ImageView>(R.id.img_hotkey_icon).setImageResource(def.icon)
            row.findViewById<TextView>(R.id.txt_hotkey_name).text = def.name
            val primary = row.findViewById<Button>(R.id.btn_hotkey_primary)
            primary.setOnClickListener { startWaiting(Waiting.Hot(def.hotkey), primary) }
            hotkeyButtons[def.hotkey] = primary
            val secondary = row.findViewById<Button>(R.id.btn_hotkey_secondary)
            if (def.hasSpeed) {
                ffSpeedButton = secondary
                secondary.setOnClickListener {
                    settings.fastForwardSpeed = if (settings.fastForwardSpeed >= 6) 2 else settings.fastForwardSpeed + 1
                    refreshHotkey(Hotkey.FAST_FORWARD)
                }
            } else {
                secondary.visibility = View.INVISIBLE
                secondary.isFocusable = false
            }
            hotkeysContainer.addView(row)
            refreshHotkey(def.hotkey)
        }
    }

    private fun refreshHotkey(hotkey: Hotkey) {
        hotkeyButtons[hotkey]?.apply {
            isActivated = false
            text = keyLabel(hotkeyKeyCode(hotkey))
        }
        if (hotkey == Hotkey.FAST_FORWARD) ffSpeedButton?.text = "${settings.fastForwardSpeed}x"
    }

    private fun hotkeyKeyCode(hotkey: Hotkey): Int = when (hotkey) {
        Hotkey.FAST_FORWARD -> settings.fastForwardKeyCode
        Hotkey.QUICK_SAVE -> settings.quickSaveKeyCode
        Hotkey.QUICK_LOAD -> settings.quickLoadKeyCode
    }

    private fun setHotkeyKeyCode(hotkey: Hotkey, keyCode: Int) = when (hotkey) {
        Hotkey.FAST_FORWARD -> settings.fastForwardKeyCode = keyCode
        Hotkey.QUICK_SAVE -> settings.quickSaveKeyCode = keyCode
        Hotkey.QUICK_LOAD -> settings.quickLoadKeyCode = keyCode
    }

    private fun resetKeys() {
        store.resetToDefaults()
        settings.fastForwardKeyCode = SettingsStore.DEFAULT_FF_KEY
        settings.fastForwardSpeed = SettingsStore.DEFAULT_FF_SPEED
        settings.quickSaveKeyCode = SettingsStore.DEFAULT_QUICK_SAVE
        settings.quickLoadKeyCode = SettingsStore.DEFAULT_QUICK_LOAD
        waiting = null
        buildPs1()
        buildHotkeys()
        Toast.makeText(this, R.string.keys_reset_done, Toast.LENGTH_SHORT).show()
    }

    // ---- Mapeo táctil --------------------------------------------------------

    private fun buildTouch() {
        touchContainer.removeAllViews()
        val mappings = touchStore.getMappings()
        if (mappings.isEmpty()) {
            touchContainer.addView(TextView(this).apply {
                text = getString(R.string.touch_empty)
                setTextColor(0xFF5E5E68.toInt())
                textSize = 14f
                setPadding(dp(4), dp(6), dp(4), dp(10))
            })
            return
        }
        for (m in mappings) {
            val row = layoutInflater.inflate(R.layout.item_touch_mapping_row, touchContainer, false)
            row.findViewById<TextView>(R.id.txt_map_name).text = m.name
            row.findViewById<TextView>(R.id.txt_map_meta).text = touchMeta(m)
            val kbd = row.findViewById<Button>(R.id.btn_map_keyboard)
            val gpd = row.findViewById<Button>(R.id.btn_map_gamepad)
            kbd.text = keyLabel(m.keyboardKeyCode)
            gpd.text = keyLabel(m.gamepadKeyCode)
            kbd.setOnClickListener { startWaiting(Waiting.Touch(m.id, keyboard = true), kbd) }
            gpd.setOnClickListener { startWaiting(Waiting.Touch(m.id, keyboard = false), gpd) }
            row.findViewById<View>(R.id.box_name).setOnClickListener { showTouchOptions(m.id) }
            touchContainer.addView(row)
        }
    }

    private fun touchMeta(m: TouchMapping): String {
        val type = getString(if (m.hold) R.string.touch_type_hold else R.string.touch_type_tap)
        return getString(R.string.touch_meta, type, (m.x * 100).toInt(), (m.y * 100).toInt())
    }

    private fun addTouchMapping() {
        val n = touchStore.getMappings().size + 1
        touchStore.add(TouchMapping(id = "m" + System.currentTimeMillis(), name = getString(R.string.touch_default_name, n)))
        buildTouch()
    }

    private fun showTouchOptions(id: String) {
        val m = touchStore.getMappings().firstOrNull { it.id == id } ?: return
        val toggle = getString(if (m.hold) R.string.touch_make_tap else R.string.touch_make_hold)
        val options = arrayOf(
            getString(R.string.touch_edit_position),
            getString(R.string.touch_rename),
            toggle,
            getString(R.string.touch_delete),
        )
        AlertDialog.Builder(this, R.style.Theme_Xemulify_Dialog)
            .setTitle(m.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> startActivity(TouchEditorActivity.intent(this, id))
                    1 -> showRenameDialog(id)
                    2 -> { touchStore.update(id) { it.copy(hold = !it.hold) }; buildTouch() }
                    3 -> { touchStore.delete(id); buildTouch() }
                }
            }
            .show()
    }

    private fun showRenameDialog(id: String) {
        val m = touchStore.getMappings().firstOrNull { it.id == id } ?: return
        val input = EditText(this).apply { setText(m.name); setSingleLine(true) }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(12), dp(24), dp(4))
            addView(input)
        }
        AlertDialog.Builder(this, R.style.Theme_Xemulify_Dialog)
            .setTitle(R.string.touch_rename)
            .setView(box)
            .setPositiveButton(R.string.cheat_save) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) { touchStore.update(id) { it.copy(name = name) }; buildTouch() }
            }
            .setNegativeButton(R.string.cheat_cancel, null)
            .show()
    }

    private fun refreshOverlayButton() {
        findViewById<Button>(R.id.btn_touch_overlay).text =
            getString(if (touchStore.showOverlay) R.string.touch_overlay_on else R.string.touch_overlay_off)
    }

    // ---- Espera de tecla (común) --------------------------------------------

    private fun startWaiting(target: Waiting, button: Button) {
        cancelWaiting()
        waiting = target
        button.isActivated = true
        button.text = "..."
    }

    private fun cancelWaiting() {
        waiting = null
        buildPs1(); buildHotkeys(); buildTouch()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val w = waiting
        if (w != null && event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            if (event.keyCode == KeyEvent.KEYCODE_BACK || event.keyCode == KeyEvent.KEYCODE_ESCAPE) {
                cancelWaiting(); return true
            }
            when (w) {
                is Waiting.Ps1 -> {
                    when (w.col) {
                        Col.P1 -> store.assignP1(w.button, event.keyCode)
                        Col.P2 -> store.assignP2(w.button, event.keyCode)
                        Col.GAMEPAD -> store.assignGamepad(w.button, event.keyCode)
                    }
                    waiting = null; buildPs1()
                }
                is Waiting.Hot -> { setHotkeyKeyCode(w.hotkey, event.keyCode); waiting = null; buildHotkeys() }
                is Waiting.Touch -> {
                    touchStore.update(w.id) {
                        if (w.keyboard) it.copy(keyboardKeyCode = event.keyCode) else it.copy(gamepadKeyCode = event.keyCode)
                    }
                    waiting = null; buildTouch()
                }
            }
            Toast.makeText(this, R.string.keys_assigned, Toast.LENGTH_SHORT).show()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    // ---- Utilidades ----------------------------------------------------------

    private fun keyLabel(keyCode: Int): String =
        if (keyCode == KeyEvent.KEYCODE_UNKNOWN) "—"
        else KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_")

    private fun iconFor(button: PsxButton): Int = when (button) {
        PsxButton.DPAD_UP -> R.drawable.ic_dpad_up
        PsxButton.DPAD_DOWN -> R.drawable.ic_dpad_down
        PsxButton.DPAD_LEFT -> R.drawable.ic_dpad_left
        PsxButton.DPAD_RIGHT -> R.drawable.ic_dpad_right
        PsxButton.CROSS -> R.drawable.ic_ps_cross
        PsxButton.CIRCLE -> R.drawable.ic_ps_circle
        PsxButton.SQUARE -> R.drawable.ic_ps_square
        PsxButton.TRIANGLE -> R.drawable.ic_ps_triangle
        PsxButton.L1 -> R.drawable.ic_ps_l1
        PsxButton.R1 -> R.drawable.ic_ps_r1
        PsxButton.L2 -> R.drawable.ic_ps_l2
        PsxButton.R2 -> R.drawable.ic_ps_r2
        PsxButton.START -> R.drawable.ic_ps_start
        PsxButton.SELECT -> R.drawable.ic_ps_select
    }
}
