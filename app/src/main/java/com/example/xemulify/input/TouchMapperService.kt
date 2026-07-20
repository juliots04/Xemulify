package com.example.xemulify.input

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Build
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.RequiresApi

/**
 * Servicio de accesibilidad que convierte teclas físicas en toques de pantalla, para jugar
 * títulos táctiles (Geometry Dash) con teclado/mando. Funciona en segundo plano, sobre el
 * juego que esté en primer plano.
 *
 *  - Detecta la app en primer plano y solo actúa fuera de la propia Xemulify.
 *  - Al pulsar una tecla mapeada inyecta un toque en la posición guardada; si el botón es de
 *    tipo "hold", mantiene el toque mientras la tecla siga pulsada (encadenando segmentos de
 *    gesto con continueStroke, API 26+).
 *  - Dibuja un overlay sutil con la posición de cada botón (opcional).
 *
 * LÍMITE (Android): solo ve teclas (KEYCODE_*). El stick analógico y el D-pad reportado como
 * "hat" analógico no llegan aquí y no se pueden mapear.
 */
class TouchMapperService : AccessibilityService() {

    private var keyboardMap = HashMap<Int, TouchMapping>()
    private var gamepadMap = HashMap<Int, TouchMapping>()
    private var mappings: List<TouchMapping> = emptyList()
    private var showOverlay = true

    @Volatile private var currentPkg: String? = null

    private val holdingIds = HashSet<String>()
    private val holdPos = HashMap<String, FloatArray>()

    private var overlayView: TouchOverlayView? = null
    private var overlayShown = false

    override fun onServiceConnected() {
        reload()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString()
            if (pkg != null && pkg != currentPkg) {
                currentPkg = pkg
                refreshOverlay()
            }
        }
        if (mappingsDirty) reload()
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        removeOverlay()
        super.onDestroy()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (mappingsDirty) reload()
        if (!isActiveForCurrentApp()) return super.onKeyEvent(event)

        val mapping = keyboardMap[event.keyCode] ?: gamepadMap[event.keyCode]
        ?: return super.onKeyEvent(event)

        when (event.action) {
            KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) press(mapping)
            KeyEvent.ACTION_UP -> release(mapping)
        }
        return true // consumir: el juego no recibe la tecla
    }

    // ---- Estado -------------------------------------------------------------

    private fun reload() {
        mappingsDirty = false
        val store = TouchMappingStore(this)
        mappings = store.getMappings()
        showOverlay = store.showOverlay
        keyboardMap = HashMap()
        gamepadMap = HashMap()
        for (m in mappings) {
            if (m.keyboardKeyCode != KeyEvent.KEYCODE_UNKNOWN) keyboardMap[m.keyboardKeyCode] = m
            if (m.gamepadKeyCode != KeyEvent.KEYCODE_UNKNOWN) gamepadMap[m.gamepadKeyCode] = m
        }
        overlayView?.setMappings(mappings)
        refreshOverlay()
    }

    private fun isActiveForCurrentApp(): Boolean {
        val pkg = currentPkg ?: return false
        return pkg != packageName && mappings.isNotEmpty()
    }

    // ---- Inyección de toques ------------------------------------------------

    private fun press(mapping: TouchMapping) {
        val (x, y) = pixelPos(mapping)
        if (mapping.hold && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            holdPos[mapping.id] = floatArrayOf(x, y)
            if (holdingIds.add(mapping.id)) holdSegment(mapping.id, null, willContinue = true)
        } else {
            tap(x, y)
        }
    }

    private fun release(mapping: TouchMapping) {
        holdingIds.remove(mapping.id)
    }

    private fun tap(x: Float, y: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, TAP_MS)
        runCatching {
            dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun holdSegment(id: String, prev: GestureDescription.StrokeDescription?, willContinue: Boolean) {
        val pos = holdPos[id] ?: return
        val path = Path().apply { moveTo(pos[0], pos[1]) }
        val stroke = if (prev == null) {
            GestureDescription.StrokeDescription(path, 0, SEG_MS, willContinue)
        } else {
            prev.continueStroke(path, 0, SEG_MS, willContinue)
        }
        val callback = if (willContinue) object : GestureResultCallback() {
            override fun onCompleted(g: GestureDescription?) {
                // Sigue mientras la tecla siga pulsada; si se soltó, un último segmento
                // (willContinue=false) levanta el dedo limpiamente.
                holdSegment(id, stroke, willContinue = holdingIds.contains(id))
            }
            override fun onCancelled(g: GestureDescription?) {
                holdingIds.remove(id)
            }
        } else null

        runCatching {
            dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), callback, null)
        }
    }

    private fun pixelPos(mapping: TouchMapping): Pair<Float, Float> {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
        return mapping.x * metrics.widthPixels to mapping.y * metrics.heightPixels
    }

    // ---- Overlay ------------------------------------------------------------

    private fun refreshOverlay() {
        val shouldShow = showOverlay && isActiveForCurrentApp()
        if (shouldShow && !overlayShown) addOverlay()
        else if (!shouldShow && overlayShown) removeOverlay()
    }

    private fun addOverlay() {
        runCatching {
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            val view = TouchOverlayView(this).apply { setMappings(mappings) }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.TOP or Gravity.START }
            wm.addView(view, params)
            overlayView = view
            overlayShown = true
        }
    }

    private fun removeOverlay() {
        runCatching {
            overlayView?.let { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it) }
        }
        overlayView = null
        overlayShown = false
    }

    companion object {
        /** La marca la pantalla de config al guardar; el servicio recarga en el próximo evento. */
        @Volatile
        var mappingsDirty: Boolean = true

        private const val TAP_MS = 40L
        private const val SEG_MS = 240L
    }
}
