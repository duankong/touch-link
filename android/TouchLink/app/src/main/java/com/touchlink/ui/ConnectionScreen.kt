package com.touchlink.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.touchlink.ConnectionState
import com.touchlink.transport.DiscoveredDevice

/**
 * Device discovery and connection screen.
 * Shows a list of discovered TouchLink servers and allows pairing.
 */
@Composable
fun ConnectionScreen(
    devices: List<DiscoveredDevice>,
    connectionState: ConnectionState,
    onDeviceSelected: (DiscoveredDevice) -> Unit,
    onScan: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "TouchLink",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = when (connectionState) {
                ConnectionState.Connected -> "Connected"
                ConnectionState.Connecting -> "Connecting..."
                ConnectionState.Disconnected -> "Scan for devices"
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Scan button
        Button(
            onClick = onScan,
            enabled = connectionState != ConnectionState.Connecting
        ) {
            Text(
                if (connectionState == ConnectionState.Disconnected) "Scan"
                else "Scan Again"
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Device list
        if (devices.isEmpty() && connectionState == ConnectionState.Disconnected) {
            Text(
                text = "No devices found. Make sure the TouchLink server is running on your PC.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(devices, key = { "${it.host}:${it.port}" }) { device ->
                    DeviceCard(
                        device = device,
                        onSelect = { onDeviceSelected(device) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceCard(
    device: DiscoveredDevice,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = device.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${device.host}:${device.port}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
