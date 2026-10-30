package com.cleandroid.app.ui.common

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.google.android.material.R as MaterialR

/**
 * Lightweight storage ring. Pure Canvas - no chart library, no bitmap cache,
 * trivially cheap to redraw on low-end devices.
 */
class StorageRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var progress = 0f

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = resources.displayMetrics.density * 16f
    }
    private val valuePaint = Paint(trackPaint)
    private val rect = RectF()

    init {
        trackPaint.color = resolveColor(MaterialR.attr.colorSurfaceVariant)
        valuePaint.color = resolveColor(MaterialR.attr.colorPrimary)
    }

    private fun resolveColor(attr: Int): Int {
        val tv = TypedValue()
        return if (context.theme.resolveAttribute(attr, tv, true)) {
            tv.data
        } else {
            0xFF14B8A6.toInt()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val inset = trackPaint.strokeWidth / 2f + resources.displayMetrics.density * 2f
        rect.set(inset, inset, width - inset, height - inset)
        canvas.drawArc(rect, 0f, 360f, false, trackPaint)
        if (progress > 0f) {
            canvas.drawArc(rect, -90f, 360f * progress, false, valuePaint)
        }
    }

    fun setProgressAnimated(target: Float) {
        val clamped = target.coerceIn(0f, 1f)
        ValueAnimator.ofFloat(progress, clamped).apply {
            duration = 700
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                progress = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
    }
}
