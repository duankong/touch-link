package com.touchlink

data class Packet(
    val opcode: Opcode,
    val seq: UInt,
    val payload: ByteArray
) {
    companion object {
        private const val MAGIC: Byte = 'T'.code.toByte()
        private const val MAGIC2: Byte = 'L'.code.toByte()
        private const val VERSION: Byte = 0x01
        const val HEADER_LEN: Int = 11

        fun decode(data: ByteArray): Packet {
            require(data.size >= HEADER_LEN) { "Packet too short: ${data.size}" }
            require(data[0] == MAGIC && data[1] == MAGIC2) { "Invalid magic" }
            require(data[2] == VERSION) { "Unsupported version: ${data[2]}" }
            val opcodeVal = ((data[3].toInt() shl 8) or (data[4].toInt())).toUShort()
            val opcode = Opcode(opcodeVal)
            val seq = ((data[5].toUByte().toUInt() shl 24) or
                       (data[6].toUByte().toUInt() shl 16) or
                       (data[7].toUByte().toUInt() shl 8) or
                       data[8].toUByte().toUInt())
            val payLen = ((data[9].toInt() shl 8) or data[10].toInt())
            val payload = data.copyOfRange(11, 11 + payLen)
            return Packet(opcode, seq, payload)
        }

        fun encode(pkt: Packet): ByteArray {
            val buf = ByteArray(HEADER_LEN + pkt.payload.size)
            buf[0] = MAGIC
            buf[1] = MAGIC2
            buf[2] = VERSION
            buf[3] = (pkt.opcode.value.toInt() shr 8).toByte()
            buf[4] = pkt.opcode.value.toInt().toByte()
            buf[5] = (pkt.seq.toInt() shr 24).toByte()
            buf[6] = (pkt.seq.toInt() shr 16).toByte()
            buf[7] = (pkt.seq.toInt() shr 8).toByte()
            buf[8] = pkt.seq.toInt().toByte()
            buf[9] = (pkt.payload.size shr 8).toByte()
            buf[10] = pkt.payload.size.toByte()
            if (pkt.payload.isNotEmpty()) {
                pkt.payload.copyInto(buf, 11)
            }
            return buf
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Packet) return false
        return opcode == other.opcode && seq == other.seq && payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = opcode.hashCode()
        result = 31 * result + seq.hashCode()
        result = 31 * result + payload.contentHashCode()
        return result
    }
}

object TouchPayload {
    fun encode(fingerId: Byte, x: Float, y: Float): ByteArray {
        val buf = ByteArray(9)
        buf[0] = fingerId
        x.toRawBits().also { v ->
            buf[1] = (v shr 24).toByte()
            buf[2] = (v shr 16).toByte()
            buf[3] = (v shr 8).toByte()
            buf[4] = v.toByte()
        }
        y.toRawBits().also { v ->
            buf[5] = (v shr 24).toByte()
            buf[6] = (v shr 16).toByte()
            buf[7] = (v shr 8).toByte()
            buf[8] = v.toByte()
        }
        return buf
    }

    fun decode(data: ByteArray): Triple<Byte, Float, Float> {
        require(data.size >= 9) { "TouchPayload too short: ${data.size}" }
        val fingerId = data[0]
        val xBits = ((data[1].toInt() shl 24) or
                     (data[2].toInt() shl 16) or
                     (data[3].toInt() shl 8) or
                     data[4].toInt())
        val yBits = ((data[5].toInt() shl 24) or
                     (data[6].toInt() shl 16) or
                     (data[7].toInt() shl 8) or
                     data[8].toInt())
        return Triple(fingerId, Float.fromBits(xBits), Float.fromBits(yBits))
    }
}

object ScrollPayload {
    fun encode(dx: Float, dy: Float): ByteArray {
        val buf = ByteArray(8)
        dx.toRawBits().also { v ->
            buf[0] = (v shr 24).toByte()
            buf[1] = (v shr 16).toByte()
            buf[2] = (v shr 8).toByte()
            buf[3] = v.toByte()
        }
        dy.toRawBits().also { v ->
            buf[4] = (v shr 24).toByte()
            buf[5] = (v shr 16).toByte()
            buf[6] = (v shr 8).toByte()
            buf[7] = v.toByte()
        }
        return buf
    }

    fun decode(data: ByteArray): Pair<Float, Float> {
        require(data.size >= 8) { "ScrollPayload too short: ${data.size}" }
        val dxBits = ((data[0].toInt() shl 24) or
                      (data[1].toInt() shl 16) or
                      (data[2].toInt() shl 8) or
                      data[3].toInt())
        val dyBits = ((data[4].toInt() shl 24) or
                      (data[5].toInt() shl 16) or
                      (data[6].toInt() shl 8) or
                      data[7].toInt())
        return Pair(Float.fromBits(dxBits), Float.fromBits(dyBits))
    }
}

object KeyPayload {
    fun encode(keyCode: UShort): ByteArray {
        return byteArrayOf((keyCode.toInt() shr 8).toByte(), keyCode.toInt().toByte())
    }

    fun decode(data: ByteArray): UShort {
        require(data.size >= 2) { "KeyPayload too short: ${data.size}" }
        return (((data[0].toInt() shl 8) or data[1].toInt())).toUShort()
    }
}
