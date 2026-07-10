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
    private var wasMultiFinger = false

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

        if (activeFingers.size >= 2) {
            wasMultiFinger = true
        }

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
        // Capture previous position before overwriting
        val prev = activeFingers[event.fingerId]
        activeFingers[event.fingerId] = event

        return if (activeFingers.size >= 2) {
            wasMultiFinger = true
            // Multi-finger scroll: delta of the moving finger
            // (only one finger triggers MOVE per event)
            val dx = event.x - (prev?.x ?: event.x)
            val dy = event.y - (prev?.y ?: event.y)
            listOf(Command.Scroll(dx, dy))
        } else if (wasMultiFinger) {
            // After a multi-finger gesture (e.g. scroll), a remaining finger
            // that hasn't lifted yet should not trigger cursor movement.
            // User must lift all fingers before single-finger mouse move resumes.
            emptyList()
        } else {
            // Single finger: mouse move
            listOf(Command.TouchMove(event.fingerId.toByte(), event.x, event.y))
        }
    }

    private fun handleUp(event: TouchEvent): List<Command> {
        activeFingers.remove(event.fingerId)

        if (wasMultiFinger && activeFingers.isEmpty()) {
            // Last finger lifted after a multi-finger gesture.
            // Send TouchCancel instead of TouchUp so the server
            // clears all touch state without triggering a click.
            wasMultiFinger = false
            return listOf(Command.TouchCancel)
        }

        if (activeFingers.isEmpty()) {
            wasMultiFinger = false
        }
        return listOf(Command.TouchUp(event.fingerId.toByte(), event.x, event.y))
    }

    /** Reset state (e.g., on connection loss). */
    fun reset() {
        activeFingers.clear()
        pendingDoubleTap = false
        wasMultiFinger = false
    }
}
