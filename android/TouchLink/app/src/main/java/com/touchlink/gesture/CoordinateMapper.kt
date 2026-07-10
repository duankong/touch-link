package com.touchlink.gesture

/**
 * Pure function: converts raw pixel coordinates to normalized 0.0–1.0 range.
 * No side effects, fully deterministic.
 */
object CoordinateMapper {
    /**
     * Normalize pixel coordinates to [0.0, 1.0] range clipped.
     */
    fun normalize(x: Float, y: Float, viewWidth: Int, viewHeight: Int): Pair<Float, Float> {
        val nx = (x / viewWidth).coerceIn(0f, 1f)
        val ny = (y / viewHeight).coerceIn(0f, 1f)
        return nx to ny
    }
}
