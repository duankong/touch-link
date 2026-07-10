package com.touchlink.gesture

import com.touchlink.command.Command

/**
 * Converts raw touch events into gesture-aware Commands.
 *
 * Current MVP recognition rules:
 * - Single finger: move/click (left mouse)
 * - Two fingers: vertical/horizontal scroll
 * - Future: pinch, three-finger, four-finger gestures
 */
class GestureRecognizer {
    private val activeFingers = mutableMapOf<Int, TouchEvent>()
    private var lastTapTime = 0L
    private var lastTapFinger = -1
    private var pendingDoubleTap = false

    /** Process a single TouchEvent and return zero or more Commands to send. */
    fun process(event: TouchEvent): List<Command> {
        return when (event.action) {
            TouchEvent.Action.Down -> handleDown(event)
            TouchEvent.Action.Move -> handleMove(event)
            TouchEvent.Action.Up -> handleUp(event)
        }
    }

    private fun handleDown(event: TouchEvent): List<Command> {
        activeFingers[event.fingerId] = event

        val commands = mutableListOf<Command>(
            Command.TouchDown(event.fingerId.toByte(), event.x, event.y)
        )

        // Double-tap detection (single finger, within 400ms)
        val now = System.currentTimeMillis()
        if (activeFingers.size == 1) {
            if (pendingDoubleTap) {
                commands.add(Command.TouchDown(event.fingerId.toByte(), event.x, event.y))
                commands.add(Command.TouchUp(event.fingerId.toByte(), event.x, event.y))
                pendingDoubleTap = false
            } else if (event.fingerId == lastTapFinger && (now - lastTapTime) < 400) {
                // Second tap detected — will be sent on Up
                pendingDoubleTap = true
            }
        }
        lastTapTime = now
        lastTapFinger = event.fingerId
        return commands
    }

    private fun handleMove(event: TouchEvent): List<Command> {
        activeFingers[event.fingerId] = event

        return if (activeFingers.size >= 2) {
            // Multi-finger: produce scroll from the average movement
            val values = activeFingers.values.toList()
            val avgX = values.map { it.x }.average().toFloat()
            val avgY = values.map { it.y }.average().toFloat()
            val prevSelf = values.firstOrNull { it.fingerId == event.fingerId } ?: event
            listOf(Command.Scroll(event.x - prevSelf.x, event.y - prevSelf.y))
        } else {
            // Single finger: mouse move
            listOf(Command.TouchMove(event.fingerId.toByte(), event.x, event.y))
        }
    }

    private fun handleUp(event: TouchEvent): List<Command> {
        activeFingers.remove(event.fingerId)
        return listOf(Command.TouchUp(event.fingerId.toByte(), event.x, event.y))
    }

    /** Reset state (e.g., on connection loss). */
    fun reset() {
        activeFingers.clear()
        pendingDoubleTap = false
    }
}
