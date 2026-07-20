package com.example.xemulify.cheats

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.example.xemulify.R

/**
 * Pantalla de trucos (cheats) por juego, para Android TV.
 *
 * Lista los cheats guardados del juego con su estado y permite activarlos/desactivarlos,
 * editarlos, borrarlos o añadir uno nuevo escribiendo su código GameShark/Action Replay.
 * No trae códigos predefinidos: cada usuario introduce los suyos. Los cambios los aplica
 * [com.example.xemulify.emulator.GameActivity] al volver al juego.
 */
class CheatConfigActivity : FragmentActivity() {

    private lateinit var store: CheatStore
    private lateinit var gameId: String
    private lateinit var container: LinearLayout

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cheat_config)

        store = CheatStore(this)
        gameId = intent.getStringExtra(EXTRA_GAME_ID) ?: run { finish(); return }

        container = findViewById(R.id.cheat_list_container)
        findViewById<Button>(R.id.btn_add_cheat).setOnClickListener { showEditDialog(null) }

        rebuild()
        findViewById<Button>(R.id.btn_add_cheat).requestFocus()
    }

    // ---- Construcción de la lista -------------------------------------------

    private fun rebuild() {
        container.removeAllViews()
        val cheats = store.getCheats(gameId)

        if (cheats.isEmpty()) {
            val empty = TextView(this).apply {
                text = getString(R.string.cheats_empty)
                setTextColor(0xFF5E5E68.toInt())
                textSize = 14f
                setPadding(dp(4), dp(6), dp(4), dp(14))
            }
            container.addView(empty)
            return
        }

        cheats.forEachIndexed { i, cheat ->
            val row = layoutInflater.inflate(R.layout.item_cheat_row, container, false)
            row.findViewById<TextView>(R.id.txt_cheat_name).text = cheat.name
            row.findViewById<TextView>(R.id.txt_cheat_code).text = cheat.code.replace('\n', ' ')
            val state = row.findViewById<TextView>(R.id.txt_cheat_state)
            state.text = getString(if (cheat.enabled) R.string.cheat_state_on else R.string.cheat_state_off)
            state.setTextColor((if (cheat.enabled) 0xFF7C5CFF else 0xFF5E5E68).toInt())
            row.setOnClickListener { showOptions(i) }
            container.addView(row)
        }
    }

    // ---- Acciones ------------------------------------------------------------

    private fun showOptions(index: Int) {
        val cheat = store.getCheats(gameId).getOrNull(index) ?: return
        val toggle = getString(if (cheat.enabled) R.string.cheat_opt_disable else R.string.cheat_opt_enable)
        val options = arrayOf(
            toggle,
            getString(R.string.cheat_opt_edit),
            getString(R.string.cheat_opt_delete),
        )
        AlertDialog.Builder(this, R.style.Theme_Xemulify_Dialog)
            .setTitle(cheat.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> { store.setEnabled(gameId, index, !cheat.enabled); rebuild() }
                    1 -> showEditDialog(index)
                    2 -> { store.deleteCheat(gameId, index); rebuild() }
                }
            }
            .show()
    }

    private fun showEditDialog(index: Int?) {
        val existing = index?.let { store.getCheats(gameId).getOrNull(it) }

        val nameInput = EditText(this).apply {
            hint = getString(R.string.cheat_name_hint)
            setText(existing?.name ?: "")
            setSingleLine(true)
        }
        val codeInput = EditText(this).apply {
            hint = getString(R.string.cheat_code_hint)
            setText(existing?.code ?: "")
            typeface = Typeface.MONOSPACE
            setHorizontallyScrolling(false)
            maxLines = 6
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(12), dp(24), dp(4))
            addView(nameInput)
            addView(
                codeInput,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(10) }
            )
        }

        AlertDialog.Builder(this, R.style.Theme_Xemulify_Dialog)
            .setTitle(if (index == null) R.string.cheat_dialog_add_title else R.string.cheat_dialog_edit_title)
            .setView(layout)
            .setPositiveButton(R.string.cheat_save) { _, _ ->
                val name = nameInput.text.toString().trim().ifBlank { getString(R.string.cheat_default_name) }
                val code = codeInput.text.toString().trim()
                if (code.isBlank()) {
                    Toast.makeText(this, R.string.cheat_code_required, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (index == null) {
                    store.addCheat(gameId, Cheat(name, code, enabled = true))
                } else {
                    store.updateCheat(gameId, index, Cheat(name, code, existing?.enabled ?: true))
                }
                rebuild()
            }
            .setNegativeButton(R.string.cheat_cancel, null)
            .show()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_UP && event.keyCode == KeyEvent.KEYCODE_ESCAPE) {
            finish()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    companion object {
        const val EXTRA_GAME_ID = "extra_game_id"

        fun start(context: Context, gameId: String) {
            context.startActivity(
                Intent(context, CheatConfigActivity::class.java)
                    .putExtra(EXTRA_GAME_ID, gameId)
            )
        }
    }
}
