package com.frogdesign.akart.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.util.AttributeSet
import android.view.View
import com.frogdesign.akart.util.dpToPx

class AimView(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : View(context, attrs, defStyleAttr, defStyleRes) {
    companion object {
        val SIZE = 80.0f //dp
        val RADIUS = 30.0f //dp
        val STROKE = 3.0f //dp
    }

    private val points: MutableMap<String, PointF> = hashMapOf()
    private var size = AimView.SIZE.toInt()
    private var radius = AimView.RADIUS
    private var stroke = AimView.STROKE
    private val paint: Paint

    init {
        if (context != null) {
            size = dpToPx(context, AimView.SIZE)
            radius = dpToPx(context, AimView.RADIUS).toFloat()
            stroke = dpToPx(context, AimView.STROKE).toFloat()
        }
        paint = Paint()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = stroke
        paint.color = Color.RED
        paint.alpha = 128;
    }

    constructor(ctx: Context, attrs: AttributeSet, defStyleAttr: Int) : this(ctx, attrs, 0, 0) {
    }

    constructor(ctx: Context, attrs: AttributeSet) : this(ctx, attrs, 0, 0) {
    }

    constructor(ctx: Context) : this(ctx, null, 0, 0) {
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = resolveSize(size, widthMeasureSpec)
        val h = resolveSize(size, heightMeasureSpec)
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas?) {
        var cx = (width / 2).toFloat()
        var cy = (height / 2).toFloat()
        canvas?.drawCircle(cx, cy, radius, paint)

        for (b in points.values) {
            if (b.x > Float.MIN_VALUE) canvas?.drawCircle(b.x, b.y, 10f, paint);
        }
    }

    fun nullify() {
        for (b in points.values) {
            b.x = Float.MIN_VALUE
            b.x = Float.MIN_VALUE
        }
        invalidate()
    }

    fun setTarget(id: String, x: Float, y: Float) {
        var pair = points.get(id) ?: PointF()
        pair.x = x
        pair.y = y
        points.put(id, pair)
        invalidate()
    }
}
