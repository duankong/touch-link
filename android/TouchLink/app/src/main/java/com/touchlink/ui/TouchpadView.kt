package com.touchlink.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.touchlink.command.Command
import com.touchlink.gesture.CoordinateMapper
import com.touchlink.gesture.GestureRecognizer
import com.touchlink.gesture.TouchEvent

/**
 * Custom View that captures raw multi-touch events and converts them
 * into gesture-aware Commands via the GestureRecognizer pipeline.
 *
 * Shows a subtle hint overlay on first touch and supports disconnection.
 */
class TouchpadView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val recognizer = GestureRecognizer()
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x60FFFFFF.toInt()
        textSize = 48f
        textAlign = Paint.Align.CENTER
    }
    private val hintSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x40FFFFFF.toInt()
        textSize = 28f
        textAlign = Paint.Align.CENTER
    }
    private var hasInteracted = false

    /** Callback invoked for each recognized Command. */
    var onCommand: ((Command) -> Unit)? = null

    /** Callback to disconnect and return to the device list. */
    var onDisconnect: (() -> Unit)? = null

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!hasInteracted) {
            // Draw hint overlay
            val cx = width / 2f
            val cy = height / 2f - 80f
            // Semi-transparent background
            val bgPaint = Paint().apply {
                color = 0x80000000.toInt()
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

            canvas.drawText("触控板已连接", cx, cy, hintPaint)
            canvas.drawText("单指移动 · 点击选择 · 双指滚动", cx, cy + 60f, hintSubPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        hasInteracted = true
        invalidate()
        val pointerIndex = event.actionIndex
        val fingerId = event.getPointerId(pointerIndex)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN -> {
                val (nx, ny) = normalize(event, pointerIndex)
                dispatch(recognizer.process(
                    TouchEvent(fingerId, TouchEvent.Action.Down, nx, ny)
                ))
            }

            MotionEvent.ACTION_MOVE -> {
                // Process all active pointers for smooth multi-touch tracking
                for (i in 0 until event.pointerCount) {
                    val fid = event.getPointerId(i)
                    val (nx, ny) = normalizeIndex(event, i)
                    dispatch(recognizer.process(
                        TouchEvent(fid, TouchEvent.Action.Move, nx, ny)
                    ))
                }
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_POINTER_UP -> {
                val (nx, ny) = normalize(event, pointerIndex)
                dispatch(recognizer.process(
                    TouchEvent(fingerId, TouchEvent.Action.Up, nx, ny)
                ))
            }

            MotionEvent.ACTION_CANCEL -> {
                recognizer.reset()
            }
        }
        return true
    }

    private fun normalize(event: MotionEvent, pointerIndex: Int): Pair<Float, Float> {
        return CoordinateMapper.normalize(
            event.getX(pointerIndex), event.getY(pointerIndex), width, height
        )
    }

    private fun normalizeIndex(event: MotionEvent, index: Int): Pair<Float, Float> {
        return CoordinateMapper.normalize(
            event.getX(index), event.getY(index), width, height
        )
    }

    private fun dispatch(commands: List<Command>) {
        commands.forEach { onCommand?.invoke(it) }
    }
}
