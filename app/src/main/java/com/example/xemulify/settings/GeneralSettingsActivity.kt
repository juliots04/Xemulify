package com.example.xemulify.settings

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import com.example.xemulify.R
import com.example.xemulify.mode.ModeSelectActivity
import com.example.xemulify.mode.ModeStore
import com.example.xemulify.remote.RemoteControllerManager

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
        val rowStartupMode = findViewById<LinearLayout>(R.id.row_startup_mode)

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
        rowStartupMode.setOnClickListener {
            val modeStore = ModeStore(this)
            modeStore.clearPreference()
            val intent = Intent(this, ModeSelectActivity::class.java).apply {
                putExtra(ModeSelectActivity.EXTRA_FORCE_SELECTION, true)
            }
            startActivity(intent)
        }

        swAspect.isChecked = settings.aspectFill
        swHud.isChecked = settings.showControlsHud
        swAutosave.isChecked = settings.autosaveEnabled
        valSlot.text = getString(R.string.gen_slot_value, settings.autosaveSlot)

        rowAspect.requestFocus()
    }

    private var stickDpadX = 0
    private var stickDpadY = 0

    private val remoteInputSink = object : RemoteControllerManager.InputSink {
        override fun onRemoteButtonChanged(playerSlot: Int, button: Int, isPressed: Boolean) {
            if (!isPressed) return
            runOnUiThread {
                val keyCode = when (button) {
                    KeyEvent.KEYCODE_DPAD_UP -> KeyEvent.KEYCODE_DPAD_UP
                    KeyEvent.KEYCODE_DPAD_DOWN -> KeyEvent.KEYCODE_DPAD_DOWN
                    KeyEvent.KEYCODE_DPAD_LEFT -> KeyEvent.KEYCODE_DPAD_LEFT
                    KeyEvent.KEYCODE_DPAD_RIGHT -> KeyEvent.KEYCODE_DPAD_RIGHT
                    KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_START -> KeyEvent.KEYCODE_DPAD_CENTER
                    KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_SELECT -> KeyEvent.KEYCODE_BACK
                    else -> null
                }
                keyCode?.let {
                    dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, it))
                    dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, it))
                }
            }
        }

        override fun onRemoteMotionChanged(playerSlot: Int, axisSource: Int, x: Float, y: Float) {
            if (axisSource != 1) return
            runOnUiThread {
                val threshold = 0.50f
                val release = 0.30f
                if (x < -threshold && stickDpadX != -1) {
                    stickDpadX = -1
                    dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT))
                    dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_LEFT))
                } else if (x > threshold && stickDpadX != 1) {
                    stickDpadX = 1
                    dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
                    dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT))
                } else if (Math.abs(x) < release) {
                    stickDpadX = 0
                }

                if (y < -threshold && stickDpadY != -1) {
                    stickDpadY = -1
                    dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP))
                    dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_UP))
                } else if (y > threshold && stickDpadY != 1) {
                    stickDpadY = 1
                    dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN))
                    dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_DOWN))
                } else if (Math.abs(y) < release) {
                    stickDpadY = 0
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        RemoteControllerManager.inputSink = remoteInputSink
    }

    override fun onPause() {
        if (RemoteControllerManager.inputSink === remoteInputSink) {
            RemoteControllerManager.inputSink = null
        }
        super.onPause()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_UP && event.keyCode == KeyEvent.KEYCODE_ESCAPE) {
            finish()
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}
