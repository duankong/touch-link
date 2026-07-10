package com.touchlink.ui

import android.content.pm.PackageManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.touchlink.ConnectionState
import com.touchlink.transport.DiscoveredDevice

/**
 * Device discovery and connection screen.
 */
@Composable
fun ConnectionScreen(
    devices: List<DiscoveredDevice>,
    connectionState: ConnectionState,
    onDeviceSelected: (DiscoveredDevice) -> Unit,
    onScan: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val versionName = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    } catch (_: PackageManager.NameNotFoundException) {
        "0.0.0"
    }

    // Pulsing animation for scanning indicator
    val infiniteTransition = rememberInfiniteTransition(label = "scanning")
    val scanAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scanAlpha"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Header ────────────────────────────────────────────
        Text(
            text = "TouchLink",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = "手机触控控制电脑",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        // ── Status & tips ─────────────────────────────────────
        StatusSection(
            connectionState = connectionState,
            scanAlpha = scanAlpha,
            deviceCount = devices.size
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ── Scan button ───────────────────────────────────────
        Button(
            onClick = onScan,
            enabled = connectionState != ConnectionState.Connecting,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            if (connectionState == ConnectionState.Connecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text("扫描中...")
            } else {
                Text(
                    if (connectionState == ConnectionState.Disconnected) "扫描设备"
                    else "重新扫描",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── Tips when idle ────────────────────────────────────
        if (devices.isEmpty() && connectionState == ConnectionState.Disconnected) {
            TipsSection()
        }

        // ── Device list ───────────────────────────────────────
        if (devices.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "找到 ${devices.size} 台设备",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(devices, key = { "${it.host}:${it.port}" }) { device ->
                    DeviceCard(
                        device = device,
                        onSelect = { onDeviceSelected(device) }
                    )
                }
            }
        }

        // ── Version footer ────────────────────────────────────
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "v$versionName",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun StatusSection(
    connectionState: ConnectionState,
    scanAlpha: Float,
    deviceCount: Int
) {
    when (connectionState) {
        ConnectionState.Disconnected -> {
            if (deviceCount == 0) {
                Text(
                    text = "点按下方按钮扫描局域网中的电脑",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
        ConnectionState.Connecting -> {
            Text(
                text = "正在连接...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary.copy(alpha = scanAlpha)
            )
        }
        ConnectionState.Connected -> {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("✓", color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "已连接",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun TipsSection() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "使用提示",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            BulletText("① 确保电脑已运行 touchlink-server.exe")
            BulletText("② 手机和电脑连接同一个 WiFi")
            BulletText("③ 点击下方「扫描设备」开始搜索")
            BulletText("④ 点击电脑名称进行连接")
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "连接后：单指移动鼠标 · 点击选中 · 双指滚动",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BulletText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 2.dp)
    )
}

@Composable
private fun DeviceCard(
    device: DiscoveredDevice,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "点击连接 →",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${device.host}:${device.port}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
