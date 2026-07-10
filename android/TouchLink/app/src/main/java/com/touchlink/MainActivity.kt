package com.touchlink

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.touchlink.session.Session
import com.touchlink.transport.DiscoveredDevice
import com.touchlink.transport.UdpDiscovery
import com.touchlink.transport.UdpTransport
import com.touchlink.ui.ConnectionScreen
import com.touchlink.ui.TouchpadView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var udpDiscovery: UdpDiscovery? = null
    private var session: Session? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    /** Thread-safe set of discovered devices, deduplicated by host:port. */
    private val deviceSet = java.util.Collections.synchronizedSet(mutableSetOf<DiscoveredDevice>())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            var connectionState by remember { mutableStateOf(ConnectionState.Disconnected) }
            var devices by remember { mutableStateOf(listOf<DiscoveredDevice>()) }
            var selectedDevice by remember { mutableStateOf<DiscoveredDevice?>(null) }

            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (connectionState == ConnectionState.Connected && selectedDevice != null) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Top bar with device info and disconnect button
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                tonalElevation = 2.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = selectedDevice!!.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = selectedDevice!!.host,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    FilledTonalButton(onClick = {
                                        session?.close()
                                        connectionState = ConnectionState.Disconnected
                                        selectedDevice = null
                                    }) {
                                        Text("断开")
                                    }
                                }
                            }
                            // Touchpad
                            AndroidView(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .weight(1f),
                                factory = { ctx ->
                                    TouchpadView(ctx).apply {
                                        onCommand = { cmd -> session?.send(cmd) }
                                    }
                                }
                            )
                        }
                    } else {
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
                            onScan = { startScanning { updatedDevices -> devices = updatedDevices } }
                        )
                    }
                }
            }
        }
    }

    /**
     * Start UDP broadcast discovery.
     * Stops any previously running discovery before starting a new instance.
     */
    private fun startScanning(onDevicesChanged: (List<DiscoveredDevice>) -> Unit) {
        stopScanning()
        deviceSet.clear()
        onDevicesChanged(emptyList())

        val udp = UdpDiscovery { device ->
            deviceSet.add(device)
            onDevicesChanged(deviceSet.toList())
        }
        udpDiscovery = udp
        scope.launch {
            try {
                udp.start()
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "UDP discovery error", e)
            }
        }
    }

    private fun connectToDevice(device: DiscoveredDevice, onConnected: () -> Unit) {
        stopScanning()
        val transport = UdpTransport(device.host, device.port)
        session = Session(transport, scope)
        // For MVP, assume connection succeeds immediately
        // Future: send PairRequest and wait for PairResponse
        onConnected()
    }

    /** Stop all active discovery. */
    private fun stopScanning() {
        udpDiscovery?.stop()
        udpDiscovery = null
    }

    override fun onDestroy() {
        stopScanning()
        session = null
        super.onDestroy()
    }
}
