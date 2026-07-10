package com.touchlink.transport

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress

class UdpTransport(
    private val host: String,
    private val port: Int
) : Transport {

    private val socket = DatagramSocket()
    private val addr = InetSocketAddress(host, port)

    override suspend fun send(data: ByteArray) {
        withContext(Dispatchers.IO) {
            val packet = DatagramPacket(data, data.size, addr)
            socket.send(packet)
        }
    }

    override fun close() {
        socket.close()
    }
}
