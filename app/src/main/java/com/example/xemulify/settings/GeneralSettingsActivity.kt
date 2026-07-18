package com.example.xemulify.settings

import android.os.Bundle
import android.view.KeyEvent
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import com.example.xemulify.R

/**
 * Ajustes generales del emulador, presentados como filas con interruptores:
 * pantalla 16:9, HUD de controles y autoguardado (+ ranura).
 * Accesible desde Ajustes y desde el menú en-juego.
 */
class GeneralSettingsActivity : FragmentActivity() {

    private lateinit var settings: SettingsStore

    private lateinit var swAspect: Switch
    private lateinit var swHud: Switch
    private lateinit var swAutosave: Switch
    private lateinit var valSlot: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_general)
        settings = SettingsStore(this)

        swAspect = findViewById(R.id.sw_aspect)
        swHud = findViewById(R.id.sw_hud)
        swAutosave = findViewById(R.id.sw_autosave)
        valSlot = findViewById(R.id.val_slot)

        val rowAspect = findViewById<LinearLayout>(R.id.row_aspect)
        val rowHud = findViewById<LinearLayout>(R.id.row_hud)
        val rowAutosave = findViewById<LinearLayout>(R.id.row_autosave)
        val rowSlot = findViewById<LinearLayout>(R.id.row_autosave_slot)

        rowAspect.setOnClickListener {
            settings.aspectFill = !settings.aspectFill
            swAspect.isChecked = settings.aspectFill
        }
        rowHud.setOnClickListener {
            settings.showControlsHud = !settings.showControlsHud
            swHud.isChecked = settings.showControlsHud
        }
        rowAutosave.setOnClickListener {
            settings.autosaveEnabled = !settings.autosaveEnabled
            swAutosave.isChecked = settings.autosaveEnabled
        }
        rowSlot.setOnClickListener {
            settings.autosaveSlot = if (settings.autosaveSlot >= SettingsStore.SLOT_COUNT) 1
            else settings.autosaveSlot + 1
            valSlot.text = getString(R.string.gen_slot_value, settings.autosaveSlot)
        }

        swAspect.isChecked = settings.aspectFill
        swHud.isChecked = settings.showControlsHud
        swAutosave.isChecked = settings.autosaveEnabled
        valSlot.text = getString(R.string.gen_slot_value, settings.autosaveSlot)

        rowAspect.requestFocus()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_UP && event.keyCode == KeyEvent.KEYCODE_ESCAPE) {
            finish()
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}
