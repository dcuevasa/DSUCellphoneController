package com.example.a3dsinputredirection

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.roundToInt

class TouchPadView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    companion object {
        private const val INVALID_POINTER_ID = -1
        private const val TOUCH_MAX = 0xFFF
    }

    interface Listener {
        fun onTouchState(active: Boolean, x: Int, y: Int)
    }

    var listener: Listener? = null

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#AACCCCCC")
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }

    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2E7D32")
        style = Paint.Style.FILL
    }

    private var touchActive = false
    private var touchX = 0
    private var touchY = 0
    private var activePointerId = INVALID_POINTER_ID

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val desiredHeight = (width * 3f / 4f).roundToInt()
        val resolvedHeight = resolveSize(desiredHeight, heightMeasureSpec)
        setMeasuredDimension(width, resolvedHeight)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), borderPaint)

        for (i in 1 until 8) {
            val x = (width / 8f) * i
            canvas.drawLine(x, 0f, x, height.toFloat(), gridPaint)
        }
        for (i in 1 until 6) {
            val y = (height / 6f) * i
            canvas.drawLine(0f, y, width.toFloat(), y, gridPaint)
        }

        if (touchActive) {
            val cx = (touchX / TOUCH_MAX.toFloat()) * width
            val cy = (touchY / TOUCH_MAX.toFloat()) * height
            canvas.drawCircle(cx, cy, 14f, pointPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                performClick()
                activePointerId = event.getPointerId(event.actionIndex)
                touchActive = true
                updatePosition(event.getX(event.actionIndex), event.getY(event.actionIndex))
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                // Keep the first touch as the active contact for the single-touch protocol.
                if (activePointerId == INVALID_POINTER_ID) {
                    activePointerId = event.getPointerId(event.actionIndex)
                    touchActive = true
                    updatePosition(event.getX(event.actionIndex), event.getY(event.actionIndex))
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val pointerIndex = event.findPointerIndex(activePointerId)
                if (pointerIndex >= 0) {
                    touchActive = true
                    updatePosition(event.getX(pointerIndex), event.getY(pointerIndex))
                }
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val releasedPointerId = event.getPointerId(event.actionIndex)
                if (releasedPointerId == activePointerId) {
                    if (event.pointerCount > 1) {
                        val nextIndex = if (event.actionIndex == 0) 1 else 0
                        activePointerId = event.getPointerId(nextIndex)
                        updatePosition(event.getX(nextIndex), event.getY(nextIndex))
                    } else {
                        activePointerId = INVALID_POINTER_ID
                        touchActive = false
                        listener?.onTouchState(false, touchX, touchY)
                        invalidate()
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                activePointerId = INVALID_POINTER_ID
                touchActive = false
                listener?.onTouchState(false, touchX, touchY)
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    private fun updatePosition(rawX: Float, rawY: Float) {
        touchX = ((rawX / width.coerceAtLeast(1)) * TOUCH_MAX).roundToInt().coerceIn(0, TOUCH_MAX)
        touchY = ((rawY / height.coerceAtLeast(1)) * TOUCH_MAX).roundToInt().coerceIn(0, TOUCH_MAX)
        listener?.onTouchState(true, touchX, touchY)
        invalidate()
    }
}
