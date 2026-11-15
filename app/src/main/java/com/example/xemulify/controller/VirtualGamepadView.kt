package com.example.xemulify.controller

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import com.example.xemulify.remote.HapticManager
import com.example.xemulify.remote.RemoteProtocol
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Mando táctil virtual para smartphone rediseñado con estética Dark AMOLED de alta gama.
 * Proporciona controles ergonómicos nativos edge-to-edge con respuesta háptica instantánea,
 * sticks analógicos DualShock con física de retorno elástico, y símbolos vectoriales
 * auténticos de PlayStation sin depender de fuentes del sistema.
 */
class VirtualGamepadView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface Listener {
        fun onInputStateChanged(
            buttonMask: Short,
            stickLX: Byte,
            stickLY: Byte,
            stickRX: Byte,
            stickRY: Byte
        )
        fun onOpenSettingsRequested()
    }

    var listener: Listener? = null
    var hapticManager: HapticManager? = null
    var theme: ControllerTheme = ControllerTheme.STEALTH_BLACK
        set(value) {
            field = value
            invalidate()
        }

    // Modo Analógico (DualShock)
    var isAnalogMode = true
        private set

    // Estado actual de los botones (Bitmask de 16 bits)
    private var currentButtonMask: Short = 0

    // Sticks analógicos
    private var stickLXVal: Byte = 0
    private var stickLYVal: Byte = 0
    private var stickRXVal: Byte = 0
    private var stickRYVal: Byte = 0

    // Animación de retorno elástico del stick
    private var stickLVisualX = 0f
    private var stickLVisualY = 0f
    private var stickLPointerId = -1

    private var stickRVisualX = 0f
    private var stickRVisualY = 0f
    private var stickRPointerId = -1

    // Herramientas de dibujo
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val symbolPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }

    // Posiciones y dimensiones
    private val dpadCenter = PointF()
    private var dpadRadius = 0f
    private val actionCenter = PointF()
    private var actionRadius = 0f

    private val stickLCenter = PointF()
    private var stickRadius = 0f
    private val stickRCenter = PointF()

    private val rectL1 = RectF()
    private val rectL2 = RectF()
    private val rectR1 = RectF()
    private val rectR2 = RectF()

    private val rectSelect = RectF()
    private val rectStart = RectF()
    private val rectAnalog = RectF()
    private val rectSettings = RectF()

    // Estados individuales de presión
    private val pressedButtons = HashSet<Short>()
    private val pointerToButton = HashMap<Int, Short>()

    init {
        isFocusable = true
        isClickable = true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        calculateLayout(w.toFloat(), h.toFloat())
    }

    private fun calculateLayout(width: Float, height: Float) {
        val padH = height
        val isWide = width / height > 1.8f
        val sideMargin = if (isWide) width * 0.045f else width * 0.03f

        // Gatillos superiores (L1, L2, R1, R2) un poco más compactos y equilibrados
        val shoulderW = (width * 0.12f).coerceIn(85f, 260f)
        val shoulderH = (padH * 0.135f).coerceIn(48f, 130f)
        val shoulderGap = 8f
        val topY = padH * 0.035f

        rectL2.set(sideMargin, topY, sideMargin + shoulderW, topY + shoulderH)
        rectL1.set(rectL2.right + shoulderGap, topY, rectL2.right + shoulderGap + shoulderW, topY + shoulderH)

        rectR1.set(width - sideMargin - shoulderW * 2 - shoulderGap, topY, width - sideMargin - shoulderW - shoulderGap, topY + shoulderH)
        rectR2.set(rectR1.right + shoulderGap, topY, width - sideMargin, topY + shoulderH)

        // Zona del Pulgar Izquierdo (Cruceta y Stick L)
        dpadRadius = padH * 0.20f
        dpadCenter.set(sideMargin + dpadRadius + width * 0.02f, padH * 0.44f)

        stickRadius = padH * 0.16f
        stickLCenter.set(dpadCenter.x + dpadRadius * 0.95f, padH * 0.77f)

        // Zona del Pulgar Derecho (Botones de Acción y Stick R)
        actionRadius = padH * 0.20f
        actionCenter.set(width - sideMargin - actionRadius - width * 0.02f, padH * 0.44f)
        stickRCenter.set(actionCenter.x - actionRadius * 0.95f, padH * 0.77f)

        // Botones centrales: SELECT, START y ANALOG con exactamente el MISMO TAMAÑO
        val btnW = (width * 0.095f).coerceIn(80f, 220f)
        val btnH = (padH * 0.085f).coerceIn(40f, 90f)
        val centerMidX = width * 0.5f
        val centerMidY = padH * 0.73f

        // SELECT y START más juntos (espaciado reducido)
        val gap = btnW * 0.35f
        rectSelect.set(centerMidX - btnW - gap * 0.5f, centerMidY, centerMidX - gap * 0.5f, centerMidY + btnH)
        rectStart.set(centerMidX + gap * 0.5f, centerMidY, centerMidX + btnW + gap * 0.5f, centerMidY + btnH)

        // Botón ANALOG con el mismo tamaño que SELECT/START, posicionado centrado un poco más abajo
        val analogY = centerMidY - btnH * 1.30f
        rectAnalog.set(centerMidX - btnW * 0.5f, analogY, centerMidX + btnW * 0.5f, analogY + btnH)

        // Zona Settings táctil auxiliar
        rectSettings.set(centerMidX - btnW * 0.5f, topY, centerMidX + btnW * 0.5f, topY + shoulderH)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1. Fondo Dark AMOLED y Pods Ergonómicos Traslúcidos
        drawBackgroundAndPods(canvas)

        // 2. Gatillos L1, L2, R1, R2 en esquinas superiores
        drawShoulderButton(canvas, rectL2, "L2", RemoteProtocol.BTN_L2)
        drawShoulderButton(canvas, rectL1, "L1", RemoteProtocol.BTN_L1)
        drawShoulderButton(canvas, rectR1, "R1", RemoteProtocol.BTN_R1)
        drawShoulderButton(canvas, rectR2, "R2", RemoteProtocol.BTN_R2)

        // 3. Cruceta direccional (D-Pad)
        drawDpad(canvas)

        // 4. Botones de acción auténticos de PlayStation (▲, ⭕, ✖, ⬛)
        drawActionButtons(canvas)

        // 5. Sticks analógicos DualShock
        if (isAnalogMode) {
            drawAnalogStick(canvas, stickLCenter, stickLVisualX, stickLVisualY, "L3", RemoteProtocol.BTN_L3)
            drawAnalogStick(canvas, stickRCenter, stickRVisualX, stickRVisualY, "R3", RemoteProtocol.BTN_R3)
        }

        // 6. Botones centrales (SELECT, START, ANALOG con LED)
        drawCenterPill(canvas, rectSelect, "SELECT", RemoteProtocol.BTN_SELECT)
        drawCenterPill(canvas, rectStart, "START", RemoteProtocol.BTN_START)
        drawAnalogButton(canvas)
    }

    private fun drawBackgroundAndPods(canvas: Canvas) {
        // Fondo base AMOLED oscuro
        canvas.drawColor(theme.bodyBgColor)

        // Pod ergonómico izquierdo (traslúcido y biselado)
        val leftPodRect = RectF(
            rectL2.left - 6f,
            height * 0.22f,
            stickLCenter.x + stickRadius * 1.35f,
            height * 0.96f
        )
        paint.style = Paint.Style.FILL
        paint.color = theme.panelInsetBg
        canvas.drawRoundRect(leftPodRect, 32f, 32f, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        paint.color = theme.podStrokeColor
        canvas.drawRoundRect(leftPodRect, 32f, 32f, paint)

        // Pod ergonómico derecho (traslúcido y biselado)
        val rightPodRect = RectF(
            stickRCenter.x - stickRadius * 1.35f,
            height * 0.22f,
            rectR2.right + 6f,
            height * 0.96f
        )
        paint.style = Paint.Style.FILL
        paint.color = theme.panelInsetBg
        canvas.drawRoundRect(rightPodRect, 32f, 32f, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        paint.color = theme.podStrokeColor
        canvas.drawRoundRect(rightPodRect, 32f, 32f, paint)
        paint.style = Paint.Style.FILL

        // Marca de agua central sutil y elegante
        paint.color = Color.WHITE
        paint.alpha = 20
        paint.textSize = height * 0.045f
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("X E M U L I F Y", width * 0.5f, height * 0.35f, paint)
        paint.alpha = 255
    }

    private fun drawShoulderButton(canvas: Canvas, rect: RectF, label: String, mask: Short) {
        val isPressed = pressedButtons.contains(mask)
        val shadowOffsetY = if (isPressed) 2f else 5f
        val pressOffset = if (isPressed) 3f else 0f

        // Sombra de profundidad
        paint.color = Color.BLACK
        paint.alpha = 90
        val shadowRect = RectF(rect.left, rect.top + shadowOffsetY, rect.right, rect.bottom + shadowOffsetY)
        canvas.drawRoundRect(shadowRect, 14f, 14f, paint)

        // Superficie del gatillo
        paint.color = if (isPressed) theme.dpadPressedColor else theme.shoulderColor
        paint.alpha = 255
        val faceRect = RectF(rect.left, rect.top + pressOffset, rect.right, rect.bottom + pressOffset)
        canvas.drawRoundRect(faceRect, 14f, 14f, paint)

        // Borde biselado
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        paint.color = if (isPressed) Color.WHITE else theme.buttonBezelColor
        paint.alpha = if (isPressed) 180 else 255
        canvas.drawRoundRect(faceRect, 14f, 14f, paint)
        paint.style = Paint.Style.FILL

        // Etiqueta tipográfica nítida
        textPaint.color = if (isPressed) Color.WHITE else theme.textColor
        textPaint.textSize = rect.height() * 0.44f
        canvas.drawText(label, faceRect.centerX(), faceRect.centerY() + textPaint.textSize * 0.35f, textPaint)
    }

    private fun drawDpad(canvas: Canvas) {
        val armW = dpadRadius * 0.58f
        val armL = dpadRadius * 0.95f
        val cx = dpadCenter.x
        val cy = dpadCenter.y

        // Pozo cóncavo de la cruceta (100% gris neutro PS1, cero azulados)
        paint.color = theme.wellBgColor
        paint.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, dpadRadius * 1.15f, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        paint.color = theme.wellStrokeColor
        canvas.drawCircle(cx, cy, dpadRadius * 1.15f, paint)
        paint.style = Paint.Style.FILL

        // 4 Brazos de la cruceta
        drawDpadWing(canvas, RectF(cx - armW / 2, cy - armL, cx + armW / 2, cy - armW / 3), "▲", RemoteProtocol.BTN_DPAD_UP)
        drawDpadWing(canvas, RectF(cx - armW / 2, cy + armW / 3, cx + armW / 2, cy + armL), "▼", RemoteProtocol.BTN_DPAD_DOWN)
        drawDpadWing(canvas, RectF(cx - armL, cy - armW / 2, cx - armW / 3, cy + armW / 2), "◀", RemoteProtocol.BTN_DPAD_LEFT)
        drawDpadWing(canvas, RectF(cx + armW / 3, cy - armW / 2, cx + armL, cy + armW / 2), "▶", RemoteProtocol.BTN_DPAD_RIGHT)

        // Pivote central cóncavo
        paint.color = theme.dpadColor
        canvas.drawCircle(cx, cy, armW * 0.45f, paint)
        paint.color = Color.BLACK
        paint.alpha = 70
        canvas.drawCircle(cx, cy, armW * 0.32f, paint)
        paint.alpha = 255
    }

    private fun drawDpadWing(canvas: Canvas, rect: RectF, arrow: String, mask: Short) {
        val isPressed = pressedButtons.contains(mask)
        val offset = if (isPressed) 2f else 0f

        // Sombra de cada brazo
        if (!isPressed) {
            paint.color = Color.BLACK
            paint.alpha = 90
            canvas.drawRoundRect(RectF(rect.left, rect.top + 3f, rect.right, rect.bottom + 3f), 10f, 10f, paint)
        }

        // Superficie del brazo
        paint.color = if (isPressed) theme.dpadPressedColor else theme.dpadColor
        paint.alpha = 255
        val face = RectF(rect.left, rect.top + offset, rect.right, rect.bottom + offset)
        canvas.drawRoundRect(face, 10f, 10f, paint)

        // Borde sutil
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        paint.color = if (isPressed) Color.WHITE else theme.buttonBezelColor
        paint.alpha = if (isPressed) 150 else 255
        canvas.drawRoundRect(face, 10f, 10f, paint)
        paint.style = Paint.Style.FILL

        // Flecha direccional grabada
        textPaint.color = if (isPressed) Color.WHITE else theme.textColor
        textPaint.textSize = rect.width().coerceAtMost(rect.height()) * 0.48f
        canvas.drawText(arrow, face.centerX(), face.centerY() + textPaint.textSize * 0.35f, textPaint)
    }

    private fun drawActionButtons(canvas: Canvas) {
        val dist = actionRadius * 0.65f
        val btnRadius = actionRadius * 0.33f
        val cx = actionCenter.x
        val cy = actionCenter.y

        // Pozo cóncavo de los botones (100% gris neutro PS1, cero azulados)
        paint.color = theme.wellBgColor
        paint.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, actionRadius * 1.15f, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        paint.color = theme.wellStrokeColor
        canvas.drawCircle(cx, cy, actionRadius * 1.15f, paint)
        paint.style = Paint.Style.FILL

        // ▲ Triángulo (Arriba) - Verde Neón
        drawPsxActionButton(canvas, cx, cy - dist, btnRadius, PsxSymbol.TRIANGLE, theme.triangleColor, RemoteProtocol.BTN_TRIANGLE)
        // ⭕ Círculo (Derecha) - Rojo Coral
        drawPsxActionButton(canvas, cx + dist, cy, btnRadius, PsxSymbol.CIRCLE, theme.circleColor, RemoteProtocol.BTN_CIRCLE)
        // ✖ Cruz (Abajo) - Azul Eléctrico
        drawPsxActionButton(canvas, cx, cy + dist, btnRadius, PsxSymbol.CROSS, theme.crossColor, RemoteProtocol.BTN_CROSS)
        // ⬛ Cuadrado (Izquierda) - Rosa Magenta
        drawPsxActionButton(canvas, cx - dist, cy, btnRadius, PsxSymbol.SQUARE, theme.squareColor, RemoteProtocol.BTN_SQUARE)
    }

    private enum class PsxSymbol { TRIANGLE, CIRCLE, CROSS, SQUARE }

    private fun drawPsxActionButton(
        canvas: Canvas,
        x: Float,
        y: Float,
        radius: Float,
        symbol: PsxSymbol,
        symbolColor: Int,
        mask: Short
    ) {
        val isPressed = pressedButtons.contains(mask)
        val shadowY = if (isPressed) 2f else 5f
        val currentY = y + (if (isPressed) 3f else 0f)

        // Sombra de profundidad 3D
        paint.color = Color.BLACK
        paint.alpha = 95
        canvas.drawCircle(x, y + shadowY, radius, paint)

        // Bisel exterior metálico
        paint.color = if (isPressed) symbolColor else theme.buttonBezelColor
        paint.alpha = if (isPressed) 180 else 255
        canvas.drawCircle(x, currentY, radius, paint)

        // Superficie del botón táctil
        paint.color = if (isPressed) theme.buttonPressedColor else theme.buttonFaceColor
        paint.alpha = 255
        canvas.drawCircle(x, currentY, radius * 0.88f, paint)

        // Dibujo vectorial nítido del símbolo PlayStation
        symbolPaint.color = symbolColor
        val symR = radius * 0.44f
        symbolPaint.strokeWidth = (radius * 0.16f).coerceAtLeast(3f)

        when (symbol) {
            PsxSymbol.TRIANGLE -> {
                val path = Path().apply {
                    moveTo(x, currentY - symR * 1.05f)
                    lineTo(x + symR * 0.95f, currentY + symR * 0.70f)
                    lineTo(x - symR * 0.95f, currentY + symR * 0.70f)
                    close()
                }
                canvas.drawPath(path, symbolPaint)
            }
            PsxSymbol.CIRCLE -> {
                canvas.drawCircle(x, currentY, symR * 0.82f, symbolPaint)
            }
            PsxSymbol.CROSS -> {
                val d = symR * 0.72f
                canvas.drawLine(x - d, currentY - d, x + d, currentY + d, symbolPaint)
                canvas.drawLine(x + d, currentY - d, x - d, currentY + d, symbolPaint)
            }
            PsxSymbol.SQUARE -> {
                val s = symR * 0.70f
                canvas.drawRoundRect(RectF(x - s, currentY - s, x + s, currentY + s), 4f, 4f, symbolPaint)
            }
        }
    }

    private fun drawAnalogStick(
        canvas: Canvas,
        center: PointF,
        offsetX: Float,
        offsetY: Float,
        label: String,
        mask: Short
    ) {
        val isPressed = pressedButtons.contains(mask)

        // Pozo cóncavo del stick (100% neutro, cero azulados)
        paint.color = theme.wellBgColor
        paint.style = Paint.Style.FILL
        canvas.drawCircle(center.x, center.y, stickRadius, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = theme.wellStrokeColor
        canvas.drawCircle(center.x, center.y, stickRadius, paint)
        paint.style = Paint.Style.FILL

        // Cabeza del stick (Thumb Cap)
        val capX = center.x + offsetX
        val capY = center.y + offsetY
        val capR = stickRadius * 0.62f

        // Sombra proyectada por la inclinación
        paint.color = Color.BLACK
        paint.alpha = 110
        canvas.drawCircle(capX - offsetX * 0.2f, capY - offsetY * 0.2f + 4f, capR, paint)

        // Superficie de goma texturizada exterior
        paint.color = if (isPressed) theme.dpadPressedColor else theme.dpadColor
        paint.alpha = 255
        canvas.drawCircle(capX, capY, capR, paint)

        // Anillos estriados de agarre antideslizante
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = theme.podStrokeColor
        canvas.drawCircle(capX, capY, capR * 0.82f, paint)
        canvas.drawCircle(capX, capY, capR * 0.64f, paint)

        // Depresión cóncava central
        paint.style = Paint.Style.FILL
        paint.color = if (isPressed) Color.BLACK else theme.wellBgColor
        canvas.drawCircle(capX, capY, capR * 0.44f, paint)

        // Reflejo de luz sutil
        paint.color = Color.WHITE
        paint.alpha = 45
        canvas.drawCircle(capX - capR * 0.15f, capY - capR * 0.15f, capR * 0.10f, paint)
        paint.alpha = 255
    }

    private fun drawCenterPill(canvas: Canvas, rect: RectF, label: String, mask: Short) {
        val isPressed = pressedButtons.contains(mask)
        val offset = if (isPressed) 2f else 0f
        val cornerRadius = rect.height() * 0.28f

        // Sombra
        paint.color = Color.BLACK
        paint.alpha = 80
        canvas.drawRoundRect(RectF(rect.left, rect.top + 3f, rect.right, rect.bottom + 3f), cornerRadius, cornerRadius, paint)

        // Cuerpo de la píldora
        paint.color = if (isPressed) theme.dpadPressedColor else theme.centerPillColor
        paint.alpha = 255
        val face = RectF(rect.left, rect.top + offset, rect.right, rect.bottom + offset)
        canvas.drawRoundRect(face, cornerRadius, cornerRadius, paint)

        // Borde biselado
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.0f
        paint.color = theme.buttonBezelColor
        canvas.drawRoundRect(face, cornerRadius, cornerRadius, paint)
        paint.style = Paint.Style.FILL

        // Texto
        textPaint.color = theme.textColor
        textPaint.textSize = (rect.height() * 0.40f).coerceAtLeast(14f)
        canvas.drawText(label, face.centerX(), face.centerY() + textPaint.textSize * 0.35f, textPaint)
    }

    private fun drawAnalogButton(canvas: Canvas) {
        val cornerRadius = rectAnalog.height() * 0.28f

        // Sombra
        paint.color = Color.BLACK
        paint.alpha = 80
        canvas.drawRoundRect(RectF(rectAnalog.left, rectAnalog.top + 3f, rectAnalog.right, rectAnalog.bottom + 3f), cornerRadius, cornerRadius, paint)

        // Botón conmutable ANALOG (mismo tamaño y estilo que SELECT/START)
        paint.color = theme.centerPillColor
        paint.alpha = 255
        canvas.drawRoundRect(rectAnalog, cornerRadius, cornerRadius, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.0f
        paint.color = theme.buttonBezelColor
        canvas.drawRoundRect(rectAnalog, cornerRadius, cornerRadius, paint)
        paint.style = Paint.Style.FILL

        // LED Indicador (Rojo encendido / apagado) pequeño y sutil
        val ledRadius = (rectAnalog.height() * 0.11f).coerceIn(4f, 8f)
        val ledX = rectAnalog.centerX()
        val ledY = rectAnalog.top - ledRadius * 1.5f

        if (isAnalogMode) {
            // Halo exterior sutil y discreto
            paint.color = theme.ledOnColor
            paint.alpha = 50
            canvas.drawCircle(ledX, ledY, ledRadius * 1.7f, paint)

            // Núcleo brillante rojo
            paint.alpha = 255
            canvas.drawCircle(ledX, ledY, ledRadius, paint)

            // Reflejo especular blanco
            paint.color = Color.WHITE
            canvas.drawCircle(ledX, ledY, ledRadius * 0.35f, paint)
        } else {
            // LED apagado (acrílico oscuro)
            paint.color = Color.parseColor("#2B1215")
            canvas.drawCircle(ledX, ledY, ledRadius, paint)
        }

        // Texto "ANALOG" nítido con tipografía unificada
        textPaint.color = theme.textColor
        textPaint.textSize = (rectAnalog.height() * 0.40f).coerceAtLeast(14f)
        canvas.drawText("ANALOG", rectAnalog.centerX(), rectAnalog.centerY() + textPaint.textSize * 0.35f, textPaint)
    }

    // ================= Multi-Touch & Input Handling =================

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        val pointerIndex = event.actionIndex
        val pointerId = event.getPointerId(pointerIndex)

        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                handlePointerDown(pointerId, event.getX(pointerIndex), event.getY(pointerIndex))
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val pid = event.getPointerId(i)
                    handlePointerMove(pid, event.getX(i), event.getY(i))
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                handlePointerUp(pointerId)
            }
            MotionEvent.ACTION_CANCEL -> {
                resetAllInputs()
            }
        }
        return true
    }

    private fun handlePointerDown(pointerId: Int, x: Float, y: Float) {
        // Verificar botón ANALOG (toggle) - incluye área táctil del botón y su LED superior
        val analogTouch = RectF(rectAnalog.left - 15f, rectAnalog.top - 35f, rectAnalog.right + 15f, rectAnalog.bottom + 15f)
        if (analogTouch.contains(x, y)) {
            isAnalogMode = !isAnalogMode
            hapticManager?.performToggleFeedback()
            if (!isAnalogMode) {
                stickLXVal = 0
                stickLYVal = 0
                stickRXVal = 0
                stickRYVal = 0
                stickLVisualX = 0f
                stickLVisualY = 0f
                stickRVisualX = 0f
                stickRVisualY = 0f
            }
            invalidate()
            notifyInputs()
            return
        }

        // Stick Izquierdo
        if (isAnalogMode && hypot(x - stickLCenter.x, y - stickLCenter.y) <= stickRadius * 1.4f) {
            stickLPointerId = pointerId
            updateStickL(x, y)
            return
        }

        // Stick Derecho
        if (isAnalogMode && hypot(x - stickRCenter.x, y - stickRCenter.y) <= stickRadius * 1.4f) {
            stickRPointerId = pointerId
            updateStickR(x, y)
            return
        }

        // Gatillos (con hitboxes expandidas hacia arriba y los bordes para facilitar pulsación)
        val expandedL2 = RectF(rectL2.left - 20f, 0f, rectL2.right + 4f, rectL2.bottom + 15f)
        val expandedL1 = RectF(rectL1.left - 4f, 0f, rectL1.right + 15f, rectL1.bottom + 15f)
        val expandedR1 = RectF(rectR1.left - 15f, 0f, rectR1.right + 4f, rectR1.bottom + 15f)
        val expandedR2 = RectF(rectR2.left - 4f, 0f, rectR2.right + 20f, rectR2.bottom + 15f)

        if (expandedL2.contains(x, y)) { pressButton(pointerId, RemoteProtocol.BTN_L2); return }
        if (expandedL1.contains(x, y)) { pressButton(pointerId, RemoteProtocol.BTN_L1); return }
        if (expandedR1.contains(x, y)) { pressButton(pointerId, RemoteProtocol.BTN_R1); return }
        if (expandedR2.contains(x, y)) { pressButton(pointerId, RemoteProtocol.BTN_R2); return }

        // Select / Start
        if (rectSelect.contains(x, y)) { pressButton(pointerId, RemoteProtocol.BTN_SELECT); return }
        if (rectStart.contains(x, y)) { pressButton(pointerId, RemoteProtocol.BTN_START); return }

        // D-Pad con zona táctil extendida
        val distDpad = hypot(x - dpadCenter.x, y - dpadCenter.y)
        if (distDpad <= dpadRadius * 1.35f) {
            val angle = Math.toDegrees(atan2((y - dpadCenter.y).toDouble(), (x - dpadCenter.x).toDouble()))
            val btn = getDpadButtonForAngle(angle)
            if (btn != 0.toShort()) {
                pressButton(pointerId, btn)
            }
            return
        }

        // Action Buttons con zona táctil extendida
        val distAction = hypot(x - actionCenter.x, y - actionCenter.y)
        if (distAction <= actionRadius * 1.35f) {
            val angle = Math.toDegrees(atan2((y - actionCenter.y).toDouble(), (x - actionCenter.x).toDouble()))
            val btn = getActionButtonForAngle(angle)
            if (btn != 0.toShort()) {
                pressButton(pointerId, btn)
            }
            return
        }
    }

    private fun handlePointerMove(pointerId: Int, x: Float, y: Float) {
        if (pointerId == stickLPointerId) {
            updateStickL(x, y)
            return
        }
        if (pointerId == stickRPointerId) {
            updateStickR(x, y)
            return
        }

        // Si el puntero estaba en D-Pad, permitir deslizar entre direcciones
        val prevBtn = pointerToButton[pointerId]
        if (prevBtn != null && isDpadButton(prevBtn)) {
            val dist = hypot(x - dpadCenter.x, y - dpadCenter.y)
            if (dist <= dpadRadius * 1.4f) {
                val angle = Math.toDegrees(atan2((y - dpadCenter.y).toDouble(), (x - dpadCenter.x).toDouble()))
                val newBtn = getDpadButtonForAngle(angle)
                if (newBtn != prevBtn && newBtn != 0.toShort()) {
                    releaseButton(pointerId)
                    pressButton(pointerId, newBtn)
                }
            } else {
                releaseButton(pointerId)
            }
        }
    }

    private fun handlePointerUp(pointerId: Int) {
        if (pointerId == stickLPointerId) {
            stickLPointerId = -1
            animateStickLReturn()
        }
        if (pointerId == stickRPointerId) {
            stickRPointerId = -1
            animateStickRReturn()
        }
        releaseButton(pointerId)
    }

    private fun updateStickL(x: Float, y: Float) {
        val dx = x - stickLCenter.x
        val dy = y - stickLCenter.y
        val maxDist = stickRadius * 0.75f
        val dist = hypot(dx, dy)

        if (dist <= maxDist) {
            stickLVisualX = dx
            stickLVisualY = dy
        } else {
            stickLVisualX = (dx / dist) * maxDist
            stickLVisualY = (dy / dist) * maxDist
        }

        stickLXVal = ((stickLVisualX / maxDist) * 127f).toInt().coerceIn(-128, 127).toByte()
        stickLYVal = ((stickLVisualY / maxDist) * 127f).toInt().coerceIn(-128, 127).toByte()

        invalidate()
        notifyInputs()
    }

    private fun updateStickR(x: Float, y: Float) {
        val dx = x - stickRCenter.x
        val dy = y - stickRCenter.y
        val maxDist = stickRadius * 0.75f
        val dist = hypot(dx, dy)

        if (dist <= maxDist) {
            stickRVisualX = dx
            stickRVisualY = dy
        } else {
            stickRVisualX = (dx / dist) * maxDist
            stickRVisualY = (dy / dist) * maxDist
        }

        stickRXVal = ((stickRVisualX / maxDist) * 127f).toInt().coerceIn(-128, 127).toByte()
        stickRYVal = ((stickRVisualY / maxDist) * 127f).toInt().coerceIn(-128, 127).toByte()

        invalidate()
        notifyInputs()
    }

    private fun animateStickLReturn() {
        val startX = stickLVisualX
        val startY = stickLVisualY
        ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 140
            interpolator = OvershootInterpolator(1.5f)
            addUpdateListener { anim ->
                val f = anim.animatedValue as Float
                stickLVisualX = startX * f
                stickLVisualY = startY * f
                stickLXVal = ((stickLVisualX / (stickRadius * 0.75f)) * 127f).toInt().coerceIn(-128, 127).toByte()
                stickLYVal = ((stickLVisualY / (stickRadius * 0.75f)) * 127f).toInt().coerceIn(-128, 127).toByte()
                invalidate()
                notifyInputs()
            }
            start()
        }
    }

    private fun animateStickRReturn() {
        val startX = stickRVisualX
        val startY = stickRVisualY
        ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 140
            interpolator = OvershootInterpolator(1.5f)
            addUpdateListener { anim ->
                val f = anim.animatedValue as Float
                stickRVisualX = startX * f
                stickRVisualY = startY * f
                stickRXVal = ((stickRVisualX / (stickRadius * 0.75f)) * 127f).toInt().coerceIn(-128, 127).toByte()
                stickRYVal = ((stickRVisualY / (stickRadius * 0.75f)) * 127f).toInt().coerceIn(-128, 127).toByte()
                invalidate()
                notifyInputs()
            }
            start()
        }
    }

    private fun pressButton(pointerId: Int, mask: Short) {
        pointerToButton[pointerId] = mask
        if (!pressedButtons.contains(mask)) {
            pressedButtons.add(mask)
            currentButtonMask = (currentButtonMask.toInt() or mask.toInt()).toShort()
            hapticManager?.performButtonClick()
            invalidate()
            notifyInputs()
        }
    }

    private fun releaseButton(pointerId: Int) {
        val mask = pointerToButton.remove(pointerId) ?: return
        if (!pointerToButton.values.contains(mask)) {
            pressedButtons.remove(mask)
            currentButtonMask = (currentButtonMask.toInt() and mask.toInt().inv()).toShort()
            invalidate()
            notifyInputs()
        }
    }

    private fun resetAllInputs() {
        pointerToButton.clear()
        pressedButtons.clear()
        currentButtonMask = 0
        stickLPointerId = -1
        stickRPointerId = -1
        stickLVisualX = 0f
        stickLVisualY = 0f
        stickRVisualX = 0f
        stickRVisualY = 0f
        stickLXVal = 0
        stickLYVal = 0
        stickRXVal = 0
        stickRYVal = 0
        invalidate()
        notifyInputs()
    }

    private fun notifyInputs() {
        listener?.onInputStateChanged(
            currentButtonMask,
            stickLXVal,
            stickLYVal,
            stickRXVal,
            stickRYVal
        )
    }

    private fun getDpadButtonForAngle(angle: Double): Short {
        return when (angle) {
            in -135.0..-45.0 -> RemoteProtocol.BTN_DPAD_UP
            in -45.0..45.0 -> RemoteProtocol.BTN_DPAD_RIGHT
            in 45.0..135.0 -> RemoteProtocol.BTN_DPAD_DOWN
            else -> RemoteProtocol.BTN_DPAD_LEFT
        }
    }

    private fun getActionButtonForAngle(angle: Double): Short {
        return when (angle) {
            in -135.0..-45.0 -> RemoteProtocol.BTN_TRIANGLE
            in -45.0..45.0 -> RemoteProtocol.BTN_CIRCLE
            in 45.0..135.0 -> RemoteProtocol.BTN_CROSS
            else -> RemoteProtocol.BTN_SQUARE
        }
    }

    private fun isDpadButton(mask: Short): Boolean {
        return mask == RemoteProtocol.BTN_DPAD_UP ||
                mask == RemoteProtocol.BTN_DPAD_DOWN ||
                mask == RemoteProtocol.BTN_DPAD_LEFT ||
                mask == RemoteProtocol.BTN_DPAD_RIGHT
    }
}
