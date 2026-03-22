package com.example.cemuhookcellphonecontroller

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.min
import kotlin.math.sqrt

class JoystickView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    interface Listener {
        fun onStickMoved(view: JoystickView, active: Boolean, x: Float, y: Float)
    }

    var listener: Listener? = null

    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#263238")
        style = Paint.Style.FILL
    }

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#90A4AE")
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#ECEFF1")
        style = Paint.Style.FILL
    }

    private var stickActive = false
    private var normX = 0f
    private var normY = 0f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) / 2f * 0.9f
        val knobRadius = radius * 0.35f
        val knobTravel = radius - knobRadius - 6f

        canvas.drawCircle(cx, cy, radius, basePaint)
        canvas.drawCircle(cx, cy, radius - 3f, ringPaint)

        val knobCx = cx + normX * knobTravel
        val knobCy = cy + normY * knobTravel
        canvas.drawCircle(knobCx, knobCy, knobRadius, knobPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_MOVE -> {
                stickActive = true
                updateStick(event.x, event.y)
                return true
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                stickActive = false
                normX = 0f
                normY = 0f
                listener?.onStickMoved(this, false, 0f, 0f)
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updateStick(rawX: Float, rawY: Float) {
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) / 2f * 0.9f
        val dx = (rawX - cx) / radius
        val dy = (rawY - cy) / radius
        val mag = sqrt(dx * dx + dy * dy)

        if (mag > 1f) {
            normX = dx / mag
            normY = dy / mag
        } else {
            normX = dx
            normY = dy
        }

        listener?.onStickMoved(this, true, normX, normY)
        invalidate()
    }
}