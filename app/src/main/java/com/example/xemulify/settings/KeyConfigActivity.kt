package com.example.xemulify.settings

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.example.xemulify.R
import com.example.xemulify.input.KeyMappingStore
import com.example.xemulify.input.PsxButton

/**
 * Pantalla de configuración de controles optimizada para Android TV.
 *  - Botones de PS1: dos columnas (teclado y mando).
 *  - Atajos del emulador (avance rápido, guardado/carga rápida): una tecla cada uno.
 */
class KeyConfigActivity : FragmentActivity() {

    private enum class Hotkey { FAST_FORWARD, QUICK_SAVE, QUICK_LOAD }

    private lateinit var store: KeyMappingStore
    private lateinit var settings: SettingsStore

    private val rowViewsMap = LinkedHashMap<PsxButton, RowViews>()
    private val hotkeyButtons = LinkedHashMap<Hotkey, Button>()
    private var ffSpeedButton: Button? = null

    private var waitingForButton: PsxButton? = null
    private var waitingForKeyboard: Boolean = true
    private var waitingForHotkey: Hotkey? = null

    private class RowViews(
        val keyboardButton: Button,
        val gamepadButton: Button
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_key_config)
        store = KeyMappingStore(this)
        settings = SettingsStore(this)

        val listContainer = findViewById<LinearLayout>(R.id.key_list_container)
        var firstButton: View? = null

        for (button in PsxButton.values()) {
            val rowView = layoutInflater.inflate(R.layout.item_key_config_row, listContainer, false)

            val iconView = rowView.findViewById<ImageView>(R.id.img_button_icon)
            val nameView = rowView.findViewById<TextView>(R.id.txt_button_name)
            val kbdButton = rowView.findViewById<Button>(R.id.btn_map_keyboard)
            val gpdButton = rowView.findViewById<Button>(R.id.btn_map_gamepad)

            iconView.setImageResource(iconFor(button))
            nameView.text = button.displayName

            kbdButton.setOnClickListener { startWaiting(button, isKeyboard = true) }
            gpdButton.setOnClickListener { startWaiting(button, isKeyboard = false) }

            if (firstButton == null) firstButton = kbdButton

            rowViewsMap[button] = RowViews(kbdButton, gpdButton)
            listContainer.addView(rowView)
            refreshRow(button)
        }

        addHotkeysSection(listContainer)

        findViewById<Button>(R.id.btn_reset).setOnClickListener {
            store.resetToDefaults()
            settings.fastForwardKeyCode = SettingsStore.DEFAULT_FF_KEY
            settings.fastForwardSpeed = SettingsStore.DEFAULT_FF_SPEED
            settings.quickSaveKeyCode = SettingsStore.DEFAULT_QUICK_SAVE
            settings.quickLoadKeyCode = SettingsStore.DEFAULT_QUICK_LOAD
            cancelWaiting()
            rowViewsMap.keys.forEach { refreshRow(it) }
            Hotkey.values().forEach { refreshHotkey(it) }
            Toast.makeText(this, R.string.keys_reset_done, Toast.LENGTH_SHORT).show()
        }

        firstButton?.requestFocus()
    }

    // ---- Sección de atajos ---------------------------------------------------

    private fun addHotkeysSection(container: LinearLayout) {
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val divider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)
            ).apply {
                topMargin = dp(8)
                bottomMargin = dp(14)
            }
            setBackgroundColor(0xFF1C1C22.toInt())
        }
        container.addView(divider)

        val header = TextView(this).apply {
            text = getString(R.string.keys_hotkeys_header)
            setTextColor(0xFF5E5E68.toInt())
            textSize = 12f
            isAllCaps = true
            letterSpacing = 0.08f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = dp(4)
                bottomMargin = dp(10)
            }
        }
        container.addView(header)

        data class HotkeyDef(val hotkey: Hotkey, val icon: Int, val name: String, val hasSpeed: Boolean)
        val defs = listOf(
            HotkeyDef(Hotkey.FAST_FORWARD, R.drawable.ic_hotkey_ff, getString(R.string.hotkey_fast_forward), true),
            HotkeyDef(Hotkey.QUICK_SAVE, R.drawable.ic_hotkey_save, getString(R.string.hotkey_quick_save), false),
            HotkeyDef(Hotkey.QUICK_LOAD, R.drawable.ic_hotkey_load, getString(R.string.hotkey_quick_load), false),
        )

        for (def in defs) {
            val row = layoutInflater.inflate(R.layout.item_hotkey_row, container, false)
            row.findViewById<ImageView>(R.id.img_hotkey_icon).setImageResource(def.icon)
            row.findViewById<TextView>(R.id.txt_hotkey_name).text = def.name

            val primary = row.findViewById<Button>(R.id.btn_hotkey_primary)
            primary.setOnClickListener { startWaitingHotkey(def.hotkey) }
            hotkeyButtons[def.hotkey] = primary

            val secondary = row.findViewById<Button>(R.id.btn_hotkey_secondary)
            if (def.hasSpeed) {
                ffSpeedButton = secondary
                secondary.setOnClickListener {
                    settings.fastForwardSpeed = when (settings.fastForwardSpeed) {
                        2 -> 3
                        3 -> 4
                        else -> 2
                    }
                    refreshHotkey(Hotkey.FAST_FORWARD)
                }
            } else {
                secondary.visibility = View.INVISIBLE
                secondary.isFocusable = false
            }

            container.addView(row)
            refreshHotkey(def.hotkey)
        }
    }

    // ---- Entrada de teclas ---------------------------------------------------

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Asignación de un atajo del emulador (permite Supr, Intro, etc.).
        val hotkey = waitingForHotkey
        if (hotkey != null && event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            if (event.keyCode == KeyEvent.KEYCODE_BACK || event.keyCode == KeyEvent.KEYCODE_ESCAPE) {
                cancelWaiting()
                return true
            }
            setHotkeyKeyCode(hotkey, event.keyCode)
            waitingForHotkey = null
            refreshHotkey(hotkey)
            Toast.makeText(this, R.string.keys_assigned, Toast.LENGTH_SHORT).show()
            return true
        }

        val target = waitingForButton
        if (target != null && event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            val keyCode = event.keyCode
            if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
                cancelWaiting()
                return true
            }
            if (waitingForKeyboard) store.assignKeyboard(target, keyCode)
            else store.assignGamepad(target, keyCode)
            waitingForButton = null
            refreshRow(target)
            Toast.makeText(this, R.string.keys_assigned, Toast.LENGTH_SHORT).show()
            return true
        }

        // ESC del teclado equivale a ATRÁS: salir de la pantalla.
        if (target == null && waitingForHotkey == null &&
            event.action == KeyEvent.ACTION_UP && event.keyCode == KeyEvent.KEYCODE_ESCAPE
        ) {
            finish()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun startWaiting(button: PsxButton, isKeyboard: Boolean) {
        cancelWaiting()
        waitingForButton = button
        waitingForKeyboard = isKeyboard
        val views = rowViewsMap[button] ?: return
        val targetButton = if (isKeyboard) views.keyboardButton else views.gamepadButton
        targetButton.isActivated = true
        targetButton.text = "..."
    }

    private fun startWaitingHotkey(hotkey: Hotkey) {
        cancelWaiting()
        waitingForHotkey = hotkey
        hotkeyButtons[hotkey]?.apply {
            isActivated = true
            text = "..."
        }
    }

    private fun cancelWaiting() {
        waitingForButton?.let {
            waitingForButton = null
            refreshRow(it)
        }
        waitingForHotkey?.let {
            waitingForHotkey = null
            refreshHotkey(it)
        }
    }

    private fun refreshRow(button: PsxButton) {
        val views = rowViewsMap[button] ?: return
        views.keyboardButton.isActivated = false
        views.keyboardButton.text = keyLabel(store.keyboardKeyCodeFor(button))
        views.gamepadButton.isActivated = false
        views.gamepadButton.text = keyLabel(store.gamepadKeyCodeFor(button))
    }

    private fun refreshHotkey(hotkey: Hotkey) {
        hotkeyButtons[hotkey]?.apply {
            isActivated = false
            text = keyLabel(hotkeyKeyCode(hotkey))
        }
        if (hotkey == Hotkey.FAST_FORWARD) {
            ffSpeedButton?.text = "${settings.fastForwardSpeed}x"
        }
    }

    private fun hotkeyKeyCode(hotkey: Hotkey): Int = when (hotkey) {
        Hotkey.FAST_FORWARD -> settings.fastForwardKeyCode
        Hotkey.QUICK_SAVE -> settings.quickSaveKeyCode
        Hotkey.QUICK_LOAD -> settings.quickLoadKeyCode
    }

    private fun setHotkeyKeyCode(hotkey: Hotkey, keyCode: Int) {
        when (hotkey) {
            Hotkey.FAST_FORWARD -> settings.fastForwardKeyCode = keyCode
            Hotkey.QUICK_SAVE -> settings.quickSaveKeyCode = keyCode
            Hotkey.QUICK_LOAD -> settings.quickLoadKeyCode = keyCode
        }
    }

    private fun keyLabel(keyCode: Int): String =
        KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_")

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
