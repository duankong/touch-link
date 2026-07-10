package com.touchlink

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.touchlink.session.Session
import com.touchlink.transport.DiscoveredDevice
import com.touchlink.transport.Discovery
import com.touchlink.transport.UdpDiscovery
import com.touchlink.transport.UdpTransport
import com.touchlink.ui.ConnectionScreen
import com.touchlink.ui.TouchpadView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var nsdDiscovery: Discovery? = null
    private var udpDiscovery: UdpDiscovery? = null
    private var session: Session? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    /** Set of discovered devices, deduplicated by host:port. */
    private val deviceSet = mutableSetOf<DiscoveredDevice>()

    /** Runtime permission launcher for location (required by NSD on Android 11+). */
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Permission result handled inline — NSD will be tried on next scan
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        nsdDiscovery = Discovery(this)

        setContent {
            var connectionState by remember { mutableStateOf(ConnectionState.Disconnected) }
            var devices by remember { mutableStateOf(listOf<DiscoveredDevice>()) }
            var selectedDevice by remember { mutableStateOf<DiscoveredDevice?>(null) }

            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (connectionState == ConnectionState.Connected && selectedDevice != null) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                TouchpadView(ctx).apply {
                                    onCommand = { cmd -> session?.send(cmd) }
                                }
                            }
                        )
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
     * Start both NSD and UDP broadcast discovery in parallel.
     * On Android 11+, NSD requires location permission; if denied, only UDP broadcast is used.
     */
    private fun startScanning(onDevicesChanged: (List<DiscoveredDevice>) -> Unit) {
        deviceSet.clear()
        onDevicesChanged(emptyList())

        // 1. Start UDP broadcast discovery (always works, no permission needed)
        val udp = UdpDiscovery { device ->
            deviceSet.add(device)
            onDevicesChanged(deviceSet.toList())
        }
        udpDiscovery = udp
        scope.launch { udp.start() }

        // 2. Start NSD discovery if location permission is granted (Android 11+)
        if (hasLocationPermission()) {
            nsdDiscovery?.startDiscovery { device ->
                deviceSet.add(device)
                onDevicesChanged(deviceSet.toList())
            }
        } else {
            requestLocationPermission()
        }
    }

    private fun hasLocationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            locationPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
            )
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
        nsdDiscovery?.stopDiscovery()
        udpDiscovery?.stop()
        udpDiscovery = null
    }

    override fun onDestroy() {
        stopScanning()
        session = null
        super.onDestroy()
    }
}
