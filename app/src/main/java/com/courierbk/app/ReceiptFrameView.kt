package com.courierbk.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class ReceiptFrameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val dimPaint = Paint()
    private val borderPaint = Paint()
    private val cornerPaint = Paint()
    private val textPaint = Paint()

    private val frameRect = RectF()

    init {

        dimPaint.color =
            0x99000000.toInt()

        borderPaint.color =
            0xFFFFFFFF.toInt()

        borderPaint.style =
            Paint.Style.STROKE

        borderPaint.strokeWidth =
            3f

        cornerPaint.color =
            0xFFFFAA00.toInt()

        cornerPaint.style =
            Paint.Style.STROKE

        cornerPaint.strokeWidth =
            8f

        textPaint.color =
            0xFFFFFFFF.toInt()

        textPaint.textSize =
            18f

        textPaint.textAlign =
            Paint.Align.CENTER
    }

    override fun onDraw(canvas: Canvas) {

        super.onDraw(canvas)

        val width =
            width.toFloat()

        val height =
            height.toFloat()

        /*
         * Вертикальная область для чека.
         *
         * Ширина примерно 72% экрана.
         * Высота примерно 76%.
         */
        val frameWidth =
            width * 0.72f

        val frameHeight =
            height * 0.76f

        val left =
            (width - frameWidth) / 2f

        val top =
            height * 0.10f

        val right =
            left + frameWidth

        val bottom =
            top + frameHeight

        frameRect.set(
            left,
            top,
            right,
            bottom
        )

        /*
         * Затемняем всё вокруг рамки.
         */
        canvas.drawRect(
            0f,
            0f,
            width,
            top,
            dimPaint
        )

        canvas.drawRect(
            0f,
            top,
            left,
            bottom,
            dimPaint
        )

        canvas.drawRect(
            right,
            top,
            width,
            bottom,
            dimPaint
        )

        canvas.drawRect(
            0f,
            bottom,
            width,
            height,
            dimPaint
        )

        /*
         * Основная тонкая рамка.
         */
        canvas.drawRect(
            frameRect,
            borderPaint
        )

        /*
         * Яркие углы рамки.
         */
        val corner =
            38f

        canvas.drawLine(
            left,
            top,
            left + corner,
            top,
            cornerPaint
        )

        canvas.drawLine(
            left,
            top,
            left,
            top + corner,
            cornerPaint
        )

        canvas.drawLine(
            right,
            top,
            right - corner,
            top,
            cornerPaint
        )

        canvas.drawLine(
            right,
            top,
            right,
            top + corner,
            cornerPaint
        )

        canvas.drawLine(
            left,
            bottom,
            left + corner,
            bottom,
            cornerPaint
        )

        canvas.drawLine(
            left,
            bottom,
            left,
            bottom - corner,
            cornerPaint
        )

        canvas.drawLine(
            right,
            bottom,
            right - corner,
            bottom,
            cornerPaint
        )

        canvas.drawLine(
            right,
            bottom,
            right,
            bottom - corner,
            cornerPaint
        )

        /*
         * Подсказка.
         */
        canvas.drawText(
            "Поместите весь чек в рамку",
            width / 2f,
            top - 22f,
            textPaint
        )

        canvas.drawText(
            "Держите телефон ровно",
            width / 2f,
            bottom + 32f,
            textPaint
        )
    }

    fun getFrameRect(): RectF {
        return RectF(frameRect)
    }
}