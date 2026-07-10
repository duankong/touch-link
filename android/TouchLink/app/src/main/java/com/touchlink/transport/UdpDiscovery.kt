package com.touchlink.transport

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.SocketTimeoutException

/**
 * UDP broadcast discovery listener.
 * Listens for broadcast announcements from TouchLink servers.
 * This is a reliable fallback when mDNS/NSD is blocked by firewalls.
 *
 * Broadcast format: "TOUCHLINK v1 <name> <port>\n"
 * Server broadcasts every 3 seconds to 255.255.255.255:[DISCOVERY_PORT].
 */
class UdpDiscovery(
    private val onDeviceFound: (DiscoveredDevice) -> Unit
) {
    companion object {
        /** Must match server's BROADCAST_PORT */
        const val DISCOVERY_PORT = 42070
        /** Timeout per receive cycle (ms) */
        private const val RECEIVE_TIMEOUT_MS = 3000
    }

    private var socket: DatagramSocket? = null
    private var running = false

    /**
     * Start listening for UDP broadcast announcements.
     * This is a suspending function that runs until [stop] is called.
     * Launch it in a coroutine scope (e.g. via [kotlinx.coroutines.CoroutineScope.launch]).
     */
    suspend fun start() {
        withContext(Dispatchers.IO) {
            running = true
            val sock = DatagramSocket(DISCOVERY_PORT).also { s ->
                s.soTimeout = RECEIVE_TIMEOUT_MS
                socket = s
            }

            val buf = ByteArray(256)
            while (running) {
                try {
                    val packet = DatagramPacket(buf, buf.size)
                    sock.receive(packet)
                    val msg = String(packet.data, packet.offset, packet.length, Charsets.UTF_8).trim()
                    val host = packet.address?.hostAddress ?: continue

                    parseDiscovery(msg, host)?.let { device ->
                        onDeviceFound(device)
                    }
                } catch (_: SocketTimeoutException) {
                    // Normal timeout — loop checks running flag
                } catch (e: Exception) {
                    if (running) {
                        android.util.Log.w("UdpDiscovery", "Receive error", e)
                    }
                }
            }
            sock.close()
        }
    }

    /** Stop listening and release the socket. */
    fun stop() {
        running = false
        socket?.close()
        socket = null
    }

    /**
     * Parse a broadcast message into a DiscoveredDevice.
     * Message format: "TOUCHLINK v1 <name> <port>"
     */
    internal fun parseDiscovery(msg: String, host: String): DiscoveredDevice? {
        val parts = msg.split(' ')
        if (parts.size < 4) return null
        if (parts[0] != "TOUCHLINK" || parts[1] != "v1") return null
        val name = parts[2]
        val port = parts[3].toIntOrNull() ?: return null
        return DiscoveredDevice(name = name, host = host, port = port)
    }
}
