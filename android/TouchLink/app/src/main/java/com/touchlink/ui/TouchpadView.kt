package com.touchlink.ui

import android.content.Context
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
 * Usage:
 *   touchpadView.onCommand = { command -> session.send(command) }
 */
class TouchpadView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val recognizer = GestureRecognizer()

    /** Callback invoked for each recognized Command. */
    var onCommand: ((Command) -> Unit)? = null

    override fun onTouchEvent(event: MotionEvent): Boolean {
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
