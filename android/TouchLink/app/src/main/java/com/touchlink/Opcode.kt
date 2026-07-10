package com.touchlink

@JvmInline
value class Opcode(val value: UShort) {
    companion object {
        val TouchMove = Opcode(0x0001u)
        val TouchDown = Opcode(0x0002u)
        val TouchUp = Opcode(0x0003u)
        val Scroll = Opcode(0x0004u)
        val Pinch = Opcode(0x0005u)
        val KeyDown = Opcode(0x0010u)
        val KeyUp = Opcode(0x0011u)
        val TextType = Opcode(0x0012u)
        val MediaPlayPause = Opcode(0x0020u)
        val MediaNext = Opcode(0x0021u)
        val MediaPrev = Opcode(0x0022u)
        val VolumeUp = Opcode(0x0023u)
        val VolumeDown = Opcode(0x0024u)
        val PairRequest = Opcode(0x0080u)
        val PairResponse = Opcode(0x0081u)
        val Heartbeat = Opcode(0x00FFu)
    }
}
