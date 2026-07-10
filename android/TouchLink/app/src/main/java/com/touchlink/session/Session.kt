package com.touchlink.session

import com.touchlink.Packet
import com.touchlink.command.Command
import com.touchlink.transport.Transport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Manages the session lifecycle for sending Commands to the remote server.
 * Handles sequence numbering and Command → Packet conversion.
 */
class Session(
    private val transport: Transport,
    private val scope: CoroutineScope
) {
    private var seq: UInt = 0u

    /** Send a single Command over the transport. */
    fun send(command: Command) {
        val (opcode, payload) = command.toOpcodeAndPayload()
        val pkt = Packet(
            opcode = opcode,
            seq = seq++,
            payload = payload
        )
        scope.launch {
            try {
                transport.send(Packet.encode(pkt))
            } catch (e: Exception) {
                // Log and drop — transport errors are surfaced via Session state changes
            }
        }
    }

    /** Reset the sequence counter (e.g., on reconnect). */
    fun resetSequence() {
        seq = 0u
    }

    /** Close the session and release the underlying transport. */
    fun close() {
        try {
            transport.close()
        } catch (_: Exception) {
            // Best-effort close
        }
    }
}
