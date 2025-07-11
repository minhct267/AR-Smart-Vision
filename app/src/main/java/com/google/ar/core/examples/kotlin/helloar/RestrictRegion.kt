package com.google.ar.core.examples.kotlin.helloar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class RestrictRegion @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private val borderPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }

    var restrictRegion: RectF = RectF(0.35f, 0.35f, 0.65f, 0.65f)
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val left = restrictRegion.left * width
        val top = restrictRegion.top * height
        val right = restrictRegion.right * width
        val bottom = restrictRegion.bottom * height
        val rect = RectF(left, top, right, bottom)
        canvas.drawRect(rect, borderPaint)
    }
}
