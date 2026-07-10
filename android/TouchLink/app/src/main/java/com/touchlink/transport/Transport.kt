package com.touchlink.transport

interface Transport {
    suspend fun send(data: ByteArray)
    fun close()
}
