package com.touchlink

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.touchlink.session.Session
import com.touchlink.transport.DiscoveredDevice
import com.touchlink.transport.Discovery
import com.touchlink.transport.UdpTransport
import com.touchlink.ui.ConnectionScreen
import com.touchlink.ui.TouchpadView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class MainActivity : ComponentActivity() {

    private var discovery: Discovery? = null
    private var session: Session? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        discovery = Discovery(this)

        setContent {
            var connectionState by remember { mutableStateOf(ConnectionState.Disconnected) }
            var devices by remember { mutableStateOf(listOf<DiscoveredDevice>()) }
            var selectedDevice by remember { mutableStateOf<DiscoveredDevice?>(null) }

            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (connectionState == ConnectionState.Connected && selectedDevice != null) {
                        // Show touchpad once connected
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                TouchpadView(ctx).apply {
                                    onCommand = { cmd -> session?.send(cmd) }
                                }
                            }
                        )
                    } else {
                        // Show discovery screen
                        ConnectionScreen(
                            devices = devices,
                            connectionState = connectionState,
                            onDeviceSelected = { device ->
                                selectedDevice = device
                                connectionState = ConnectionState.Connecting
                                connectToDevice(device) {
                                    connectionState = ConnectionState.Connected
                                }
                            },
                            onScan = {
                                devices = emptyList()
                                discovery?.startDiscovery { device ->
                                    devices = devices + device
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    private fun connectToDevice(device: DiscoveredDevice, onConnected: () -> Unit) {
        val transport = UdpTransport(device.host, device.port)
        val scope = CoroutineScope(Dispatchers.IO)
        session = Session(transport, scope)
        // For MVP, assume connection succeeds immediately
        // Future: send PairRequest and wait for PairResponse
        onConnected()
    }

    override fun onDestroy() {
        discovery?.stopDiscovery()
        session = null
        super.onDestroy()
    }
}
