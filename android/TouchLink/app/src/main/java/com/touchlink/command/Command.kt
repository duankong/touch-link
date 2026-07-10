package com.touchlink.command

import com.touchlink.Opcode
import com.touchlink.KeyPayload
import com.touchlink.ScrollPayload
import com.touchlink.TouchPayload

/**
 * Intermediate value object between the UI/Gesture layer and the Transport layer.
 * GestureMapper produces Commands; Session sends them via Transport.
 */
sealed class Command {
    data class TouchDown(val fingerId: Byte, val x: Float, val y: Float) : Command()
    data class TouchMove(val fingerId: Byte, val x: Float, val y: Float) : Command()
    data class TouchUp(val fingerId: Byte, val x: Float, val y: Float) : Command()
    data class Scroll(val dx: Float, val dy: Float) : Command()
    data class Pinch(val scale: Float) : Command()
    data class Key(val vk: UShort, val down: Boolean) : Command()
    data object TouchCancel : Command()
    data object Heartbeat : Command()

    /** Convert this Command into an (Opcode, ByteArray) pair ready for Packet encoding. */
    fun toOpcodeAndPayload(): Pair<Opcode, ByteArray> {
        return when (this) {
            is TouchDown -> Opcode.TouchDown to TouchPayload.encode(fingerId, x, y)
            is TouchMove -> Opcode.TouchMove to TouchPayload.encode(fingerId, x, y)
            is TouchUp -> Opcode.TouchUp to TouchPayload.encode(fingerId, x, y)
            is Scroll -> Opcode.Scroll to ScrollPayload.encode(dx, dy)
            is Pinch -> Opcode.Pinch to ScrollPayload.encode(scale, 0f) // reuse scroll payload for pinch
            is Key -> {
                val opcode = if (down) Opcode.KeyDown else Opcode.KeyUp
                opcode to KeyPayload.encode(vk)
            }
            TouchCancel -> Opcode.TouchCancel to ByteArray(0)
            Heartbeat -> Opcode.Heartbeat to ByteArray(0)
        }
    }
}
