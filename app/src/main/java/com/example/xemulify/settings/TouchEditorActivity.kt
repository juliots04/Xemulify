package com.example.xemulify.settings

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.example.xemulify.R
import com.example.xemulify.input.TouchMapping
import com.example.xemulify.input.TouchMappingStore

/**
 * Editor visual de posiciones de botones táctiles, para TV/teclado (sin táctil):
 *  - Flechas: mueven el botón seleccionado.
 *  - R1 / L1 / Tab: cambian de botón (solo al editar todos).
 *  - OK / Enter / ATRÁS: guardan y salen.
 * Con EXTRA_MAPPING_ID edita un solo botón (desde su fila); si no, muestra todos.
 * Posiciones normalizadas (0..1).
 */
class TouchEditorActivity : FragmentActivity() {

    private lateinit var store: TouchMappingStore
    private lateinit var editor: EditorView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = TouchMappingStore(this)
        val mappings = store.getMappings()
        if (mappings.isEmpty()) {
            Toast.makeText(this, R.string.touch_editor_empty, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val targetId = intent.getStringExtra(EXTRA_MAPPING_ID)
        val single = targetId != null
        val start = mappings.indexOfFirst { it.id == targetId }.coerceAtLeast(0)
        val hint = getString(if (single) R.string.touch_editor_hint_single else R.string.touch_editor_hint)

        editor = EditorView(this, mappings, hint, start, single)
        setContentView(editor)
        editor.isFocusable = true
        editor.requestFocus()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!::editor.isInitialized) return super.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> { editor.move(-STEP, 0f); return true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> { editor.move(STEP, 0f); return true }
                KeyEvent.KEYCODE_DPAD_UP -> { editor.move(0f, -STEP); return true }
                KeyEvent.KEYCODE_DPAD_DOWN -> { editor.move(0f, STEP); return true }
                KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_TAB -> { editor.cycle(1); return true }
                KeyEvent.KEYCODE_BUTTON_L1 -> { editor.cycle(-1); return true }
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_BUTTON_A,
                KeyEvent.KEYCODE_BACK,
                KeyEvent.KEYCODE_ESCAPE -> { saveAndExit(); return true }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() = saveAndExit()

    private fun saveAndExit() {
        if (::editor.isInitialized) store.saveMappings(editor.result())
        finish()
    }

    // ---- Vista del editor ----------------------------------------------------

    private class EditorView(
        context: Context,
        source: List<TouchMapping>,
        private val hint: String,
        start: Int,
        private val single: Boolean,
    ) : View(context) {

        private val items = source.toMutableList()
        private var selected = start.coerceIn(0, items.size - 1)
        private val density = resources.displayMetrics.density

        private val grid = Paint().apply { color = 0x14FFFFFF; strokeWidth = 1f }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33FFFFFF; style = Paint.Style.FILL }
        private val fillSel = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x557C5CFF; style = Paint.Style.FILL }
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66FFFFFF; style = Paint.Style.STROKE; strokeWidth = 2f * density }
        private val ringSel = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF7C5CFF.toInt(); style = Paint.Style.STROKE; strokeWidth = 3f * density }
        private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xE0FFFFFF.toInt(); textSize = 13f * density; textAlign = Paint.Align.CENTER
            setShadowLayer(4f, 0f, 1f, Color.BLACK)
        }
        private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xCCFFFFFF.toInt(); textSize = 14f * density
            setShadowLayer(4f, 0f, 1f, Color.BLACK)
        }

        init { setBackgroundColor(0xFF08080C.toInt()) }

        fun move(dx: Float, dy: Float) {
            val m = items[selected]
            items[selected] = m.copy(
                x = (m.x + dx).coerceIn(0f, 1f),
                y = (m.y + dy).coerceIn(0f, 1f),
            )
            invalidate()
        }

        fun cycle(dir: Int) {
            if (single || items.size <= 1) return
            selected = (selected + dir + items.size) % items.size
            invalidate()
        }

        fun result(): List<TouchMapping> = items

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()

            for (i in 1..2) {
                canvas.drawLine(w * i / 3, 0f, w * i / 3, h, grid)
                canvas.drawLine(0f, h * i / 3, w, h * i / 3, grid)
            }

            val r = 26f * density
            items.forEachIndexed { i, m ->
                // En modo "un solo botón" solo se dibuja el seleccionado.
                if (single && i != selected) return@forEachIndexed
                val cx = m.x * w
                val cy = m.y * h
                val isSel = i == selected
                canvas.drawCircle(cx, cy, r, if (isSel) fillSel else fill)
                canvas.drawCircle(cx, cy, r, if (isSel) ringSel else ring)
                canvas.drawText(m.name, cx, cy + r + 16f * density, label)
            }

            val sel = items[selected]
            canvas.drawText(hint, 24f * density, 34f * density, hintPaint)
            canvas.drawText(
                "▶ ${sel.name}   ${(sel.x * 100).toInt()}%, ${(sel.y * 100).toInt()}%",
                24f * density, 58f * density, hintPaint
            )
        }
    }

    companion object {
        private const val STEP = 0.01f
        const val EXTRA_MAPPING_ID = "extra_mapping_id"

        fun intent(context: Context, mappingId: String): Intent =
            Intent(context, TouchEditorActivity::class.java).putExtra(EXTRA_MAPPING_ID, mappingId)
    }
}
