package com.example.xemulify.input

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

/**
 * Dibuja marcadores sutiles (círculo semitransparente + nombre) en la posición de cada
 * botón táctil, como referencia visual encima del juego. Lo muestra el servicio de
 * accesibilidad mediante una ventana TYPE_ACCESSIBILITY_OVERLAY.
 */
class TouchOverlayView(context: Context) : View(context) {

    private var mappings: List<TouchMapping> = emptyList()
    private val density = resources.displayMetrics.density

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x387C5CFF   // violeta ~22% alpha
        style = Paint.Style.FILL
    }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x887C5CFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xCCFFFFFF.toInt()
        textSize = 13f * density
        textAlign = Paint.Align.CENTER
        setShadowLayer(4f, 0f, 1f, Color.BLACK)
    }

    fun setMappings(m: List<TouchMapping>) {
        mappings = m
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val r = 26f * density
        for (m in mappings) {
            val cx = m.x * w
            val cy = m.y * h
            canvas.drawCircle(cx, cy, r, fill)
            canvas.drawCircle(cx, cy, r, ring)
            canvas.drawText(m.name, cx, cy + r + 16f * density, label)
        }
    }
}
