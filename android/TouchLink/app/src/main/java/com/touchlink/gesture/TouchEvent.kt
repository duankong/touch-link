package com.touchlink.gesture

/**
 * Immutable value object representing a single raw touch event
 * in device-independent normalized coordinates (0.0–1.0).
 */
data class TouchEvent(
    val fingerId: Int,
    val action: Action,
    val x: Float,
    val y: Float,
    val pressure: Float = 0f
) {
    enum class Action {
        Down,
        Move,
        Up
    }
}
