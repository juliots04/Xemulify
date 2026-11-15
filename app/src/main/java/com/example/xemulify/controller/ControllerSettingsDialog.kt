package com.example.xemulify.controller

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import com.example.xemulify.R
import com.example.xemulify.remote.HapticManager

class ControllerSettingsDialog(
    private val context: Context,
    private val currentTheme: ControllerTheme,
    private val currentHaptic: HapticManager.HapticStrength,
    private val onThemeChanged: (ControllerTheme) -> Unit,
    private val onHapticChanged: (HapticManager.HapticStrength) -> Unit,
    private val onDisconnect: () -> Unit
) {
    fun show() {
        val themes = ControllerTheme.values()
        val themeNames = themes.map { it.displayName }

        val haptics = HapticManager.HapticStrength.values()
        val hapticNames = haptics.map { it.displayName }

        val layout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(50, 40, 50, 30)
        }

        val txtTheme = android.widget.TextView(context).apply {
            text = "Tema Visual del Mando:"
            textSize = 16f
            setTextColor(android.graphics.Color.WHITE)
        }
        val spinnerTheme = Spinner(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, themeNames)
            setSelection(themes.indexOf(currentTheme))
        }

        val txtHaptic = android.widget.TextView(context).apply {
            text = "\nVibración Háptica:"
            textSize = 16f
            setTextColor(android.graphics.Color.WHITE)
        }
        val spinnerHaptic = Spinner(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, hapticNames)
            setSelection(haptics.indexOf(currentHaptic))
        }

        val btnDisconnect = Button(context).apply {
            text = "Desconectar Mando"
            setBackgroundColor(android.graphics.Color.parseColor("#C62828"))
            setTextColor(android.graphics.Color.WHITE)
            val params = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 40 }
            layoutParams = params
        }

        layout.addView(txtTheme)
        layout.addView(spinnerTheme)
        layout.addView(txtHaptic)
        layout.addView(spinnerHaptic)
        layout.addView(btnDisconnect)

        val dialog = AlertDialog.Builder(context, R.style.Theme_Xemulify)
            .setTitle("Configuración del Mando")
            .setView(layout)
            .setPositiveButton("Guardar") { d, _ ->
                val selectedTheme = themes[spinnerTheme.selectedItemPosition]
                val selectedHaptic = haptics[spinnerHaptic.selectedItemPosition]
                onThemeChanged(selectedTheme)
                onHapticChanged(selectedHaptic)
                d.dismiss()
            }
            .setNegativeButton("Cancelar", null)
            .create()

        btnDisconnect.setOnClickListener {
            dialog.dismiss()
            onDisconnect()
        }

        dialog.show()
    }
}
