package com.touchlink

import com.touchlink.transport.DiscoveredDevice

data class AppState(
    val discoveredDevices: List<DiscoveredDevice> = emptyList(),
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val isTouchpadActive: Boolean = false,
    val deviceName: String = "TouchLink-Android"
)

enum class ConnectionState {
    Disconnected,
    Connecting,
    Connected
}

data class DeviceInfo(
    val name: String,
    val host: String,
    val port: Int
)
