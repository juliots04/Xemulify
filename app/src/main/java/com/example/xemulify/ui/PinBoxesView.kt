package com.example.xemulify.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.InputFilter
import android.text.InputType
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import android.widget.EditText

/**
 * Componente visual nativo para el ingreso de PIN de 6 caracteres.
 * Extiende [EditText] directamente para garantizar compatibilidad total con teclados
 * (Xiaomi MIUI, Samsung, Gboard), sin trucos de vistas invisibles.
 * Dibuja las 6 casillas estilizadas y centra cada dígito automáticamente en mayúscula.
 */
class PinBoxesView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.editTextStyle
) : EditText(context, attrs, defStyleAttr) {

    private val boxCount = 6
    private val boxWidth: Float
    private val boxHeight: Float
    private val boxSpacing: Float
    private val cornerRadius: Float

    private val paintBoxBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val paintBoxStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val paintText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val rectF = RectF()

    var onPinChanged: ((String) -> Unit)? = null
    var onPinComplete: ((String) -> Unit)? = null

    init {
        val density = resources.displayMetrics.density
        boxWidth = 46f * density
        boxHeight = 58f * density
        boxSpacing = 10f * density
        cornerRadius = 12f * density

        paintBoxStroke.strokeWidth = 2f * density
        paintText.textSize = 24f * density

        // Configuración nativa del EditText
        background = null
        isCursorVisible = false
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        imeOptions = EditorInfo.IME_ACTION_DONE

        // Filtro estricto: mayúsculas forzadas y longitud fija de 6
        filters = arrayOf(
            InputFilter.AllCaps(),
            InputFilter.LengthFilter(boxCount)
        )
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = (boxCount * boxWidth + (boxCount - 1) * boxSpacing).toInt()
        val desiredHeight = boxHeight.toInt()

        val width = resolveSize(desiredWidth + paddingLeft + paddingRight, widthMeasureSpec)
        val height = resolveSize(desiredHeight + paddingTop + paddingBottom, heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val result = super.onTouchEvent(event)
        setSelection(text?.length ?: 0)
        return result
    }

    override fun onFocusChanged(focused: Boolean, direction: Int, previouslyFocusedRect: android.graphics.Rect?) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect)
        invalidate()
    }

    override fun onTextChanged(text: CharSequence?, start: Int, lengthBefore: Int, lengthAfter: Int) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter)
        invalidate()
        val str = text?.toString()?.uppercase() ?: ""
        onPinChanged?.invoke(str)
        if (str.length == boxCount) {
            onPinComplete?.invoke(str)
        }
    }

    override fun onDraw(canvas: Canvas) {
        // No llamamos a super.onDraw para no dibujar el texto plano sin casillas.
        // Dibujamos las 6 casillas estilizadas idénticas a la referencia visual.
        val density = resources.displayMetrics.density
        val totalBoxesWidth = boxCount * boxWidth + (boxCount - 1) * boxSpacing
        val startX = (width - totalBoxesWidth) / 2f
        val startY = (height - boxHeight) / 2f

        val currentText = text?.toString()?.uppercase() ?: ""
        val activeIndex = if (currentText.length < boxCount) currentText.length else boxCount - 1

        val fontMetrics = paintText.fontMetrics
        val textBaselineY = startY + (boxHeight / 2f) - ((fontMetrics.ascent + fontMetrics.descent) / 2f)

        for (i in 0 until boxCount) {
            val left = startX + i * (boxWidth + boxSpacing)
            val top = startY
            val right = left + boxWidth
            val bottom = top + boxHeight
            rectF.set(left, top, right, bottom)

            val isActive = (i == activeIndex && isFocused) || (currentText.length == boxCount && i == boxCount - 1)
            val isFilled = i < currentText.length

            if (isActive) {
                // Casilla activa: fondo iluminado con borde resaltado violeta acento (#7C5CFF)
                paintBoxBg.color = Color.parseColor("#252838")
                paintBoxStroke.color = Color.parseColor("#7C5CFF")
                paintBoxStroke.strokeWidth = 2.2f * density
            } else if (isFilled) {
                // Casilla rellena: fondo oscuro con borde visible
                paintBoxBg.color = Color.parseColor("#20222D")
                paintBoxStroke.color = Color.parseColor("#4A4F68")
                paintBoxStroke.strokeWidth = 1.4f * density
            } else {
                // Casilla vacía normal: fondo oscuro con borde sutil
                paintBoxBg.color = Color.parseColor("#1B1C25")
                paintBoxStroke.color = Color.parseColor("#323545")
                paintBoxStroke.strokeWidth = 1.2f * density
            }

            // 1. Fondo redondeado de la casilla
            canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, paintBoxBg)
            // 2. Borde de la casilla
            canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, paintBoxStroke)

            // 3. Carácter centrado
            if (isFilled) {
                val charStr = currentText[i].toString()
                val charCenterX = left + boxWidth / 2f
                canvas.drawText(charStr, charCenterX, textBaselineY, paintText)
            }
        }
    }
}
