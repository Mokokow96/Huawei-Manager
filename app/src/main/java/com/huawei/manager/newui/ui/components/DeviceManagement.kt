package com.huawei.manager.newui.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huawei.manager.newui.model.BlockedDevice
import com.huawei.manager.newui.model.ConnectedDevice
import com.huawei.manager.newui.ui.theme.DarkNavySurface
import com.huawei.manager.newui.ui.theme.DarkNavySurfaceElevated
import com.huawei.manager.newui.ui.theme.ElectricBlue
import com.huawei.manager.newui.ui.theme.SignalGreen
import com.huawei.manager.newui.ui.theme.SignalOrange
import com.huawei.manager.newui.ui.theme.TextMuted
import com.huawei.manager.newui.ui.theme.TextPrimary
import com.huawei.manager.newui.ui.theme.TextSecondary
import com.huawei.manager.newui.ui.theme.WarningRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Daftar perangkat yang sedang terhubung ke MiFi: nama, waktu terhubung, dan tombol blokir. */
@Composable
fun ConnectedDevicesCard(
    devices: List<ConnectedDevice>,
    busyMac: String?,
    onBlock: (ConnectedDevice) -> Unit,
    modifier: Modifier = Modifier,
    errorMessage: String? = null,
    reportedCount: Int = 0
) {
    var pendingBlock by remember { mutableStateOf<ConnectedDevice?>(null) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("connected_devices_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = DarkNavySurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkNavySurfaceElevated)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(ElectricBlue.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Devices, null, tint = ElectricBlue, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Perangkat Terhubung",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold, color = TextPrimary
                        )
                    )
                    Text("Pengguna Wi-Fi MiFi saat ini", fontSize = 10.sp, color = TextSecondary)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(SignalGreen.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text("${devices.size}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SignalGreen)
                }
            }

            Spacer(Modifier.height(10.dp))

            if (devices.isEmpty()) {
                Text(
                    when {
                        errorMessage != null -> "Daftar perangkat belum terbaca: $errorMessage."
                        reportedCount > 0 -> "Modem melaporkan $reportedCount perangkat terhubung, sedang memuat daftarnya…"
                        else -> "Belum ada perangkat terdeteksi."
                    },
                    fontSize = 12.sp,
                    color = if (errorMessage != null) WarningRed else TextMuted,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                devices.forEachIndexed { index, device ->
                    if (index > 0) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(DarkNavySurfaceElevated.copy(alpha = 0.6f))
                        )
                    }
                    ConnectedDeviceRow(
                        device = device,
                        busy = busyMac == device.mac,
                        enabled = busyMac == null,
                        onBlockClick = { pendingBlock = device }
                    )
                }
            }
        }
    }

    pendingBlock?.let { device ->
        AlertDialog(
            onDismissRequest = { pendingBlock = null },
            title = { Text("Blokir ${device.name}?", color = TextPrimary) },
            text = {
                Text(
                    "Perangkat dengan MAC ${device.mac} tidak akan bisa terhubung ke Wi-Fi modem sampai kamu membuka blokirnya.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onBlock(device)
                    pendingBlock = null
                }) { Text("Blokir", color = WarningRed, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { pendingBlock = null }) { Text("Batal", color = TextSecondary) }
            },
            containerColor = DarkNavySurface
        )
    }
}

@Composable
private fun ConnectedDeviceRow(
    device: ConnectedDevice,
    busy: Boolean,
    enabled: Boolean,
    onBlockClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(ElectricBlue.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.PhoneAndroid, null, tint = ElectricBlue, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    device.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (device.isThisPhone) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(ElectricBlue.copy(alpha = 0.2f))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text("PERANGKAT INI", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = ElectricBlue)
                    }
                }
            }
            Text(
                formatConnectedTime(device),
                fontSize = 11.sp,
                color = TextSecondary
            )
            Text(
                listOf(device.ip, device.mac).filter { it.isNotBlank() }.joinToString(" • "),
                fontSize = 10.sp,
                color = TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        when {
            device.isThisPhone -> Unit
            busy -> CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = WarningRed
            )
            else -> Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, WarningRed.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                    .clickable(enabled = enabled) { onBlockClick() }
                    .padding(horizontal = 10.dp, vertical = 7.dp)
                    .testTag("block_${device.mac}")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Block, null, tint = WarningRed, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Blokir", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = WarningRed)
                }
            }
        }
    }
}

/** Daftar perangkat yang diblokir; bisa dibuka/ditutup, tiap item punya tombol Unban. */
@Composable
fun BlockedDevicesCard(
    devices: List<BlockedDevice>,
    busyMac: String?,
    onUnblock: (BlockedDevice) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("blocked_devices_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = DarkNavySurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkNavySurfaceElevated)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(18.dp)
                    .testTag("blocked_devices_header"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(WarningRed.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Block, null, tint = WarningRed, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Perangkat Diblokir",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold, color = TextPrimary
                        )
                    )
                    Text("Ketuk untuk membuka blokir perangkat", fontSize = 10.sp, color = TextSecondary)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            (if (devices.isEmpty()) TextMuted else WarningRed).copy(alpha = 0.15f)
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        "${devices.size}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (devices.isEmpty()) TextMuted else WarningRed
                    )
                }
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Tutup" else "Buka",
                    tint = TextSecondary
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 14.dp)) {
                    if (devices.isEmpty()) {
                        Text(
                            "Tidak ada perangkat yang diblokir.",
                            fontSize = 12.sp,
                            color = TextMuted,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    } else {
                        devices.forEachIndexed { index, device ->
                            if (index > 0) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(DarkNavySurfaceElevated.copy(alpha = 0.6f))
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        device.name,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(device.mac, fontSize = 10.sp, color = TextMuted)
                                }
                                Spacer(Modifier.width(8.dp))
                                if (busyMac == device.mac) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        strokeWidth = 2.dp,
                                        color = SignalOrange
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .border(1.dp, SignalGreen.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                                            .clickable(enabled = busyMac == null) { onUnblock(device) }
                                            .padding(horizontal = 10.dp, vertical = 7.dp)
                                            .testTag("unblock_${device.mac}")
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.LockOpen, null, tint = SignalGreen, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Unban", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SignalGreen)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatConnectedTime(d: ConnectedDevice): String {
    if (d.connectedSeconds <= 0L) return "Waktu terhubung tidak diketahui"
    val since = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(d.connectedSinceMs))
    val h = d.connectedSeconds / 3600
    val m = (d.connectedSeconds % 3600) / 60
    val duration = when {
        h > 0 -> "${h}j ${m}m"
        m > 0 -> "${m} menit"
        else -> "baru saja"
    }
    return "Terhubung sejak $since • $duration"
}
