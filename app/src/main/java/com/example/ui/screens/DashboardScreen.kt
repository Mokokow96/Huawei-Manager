package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.BandOption
import com.example.model.LinkState
import com.example.ui.components.BlockedDevicesCard
import com.example.ui.components.ConnectedDevicesCard
import com.example.ui.components.OfflineBanner
import com.example.ui.components.BandSelectorDialog
import com.example.ui.components.DataLimitDialog
import com.example.ui.components.IpChangeDialog
import com.example.ui.components.SignalBars
import com.example.ui.components.getSignalLevelInfo
import com.example.ui.theme.DarkNavyBase
import com.example.ui.theme.DarkNavySurface
import com.example.ui.theme.DarkNavySurfaceElevated
import com.example.ui.theme.DarkNavySurfaceVariant
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.SignalGreen
import com.example.ui.theme.SignalOrange
import com.example.ui.theme.SpeedDownloadColor
import com.example.ui.theme.SpeedUploadColor
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningRed
import com.example.viewmodel.ModemViewModel
import java.util.Locale

@Composable
fun DashboardScreen(
    viewModel: ModemViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val networkStatus by viewModel.networkStatus.collectAsState()
    val signalData by viewModel.signalData.collectAsState()
    val trafficData by viewModel.trafficData.collectAsState()
    val selectedBand by viewModel.selectedBand.collectAsState()
    val isDemoMode by viewModel.isDemoMode.collectAsState()

    val showBandDialog by viewModel.showBandDialog.collectAsState()
    val showIpChangeDialog by viewModel.showIpChangeDialog.collectAsState()
    val ipChangeProgress by viewModel.ipChangeProgress.collectAsState()
    val showDataLimitDialog by viewModel.showDataLimitDialog.collectAsState()
    val quotaLimitGb by viewModel.quotaLimitGb.collectAsState()
    val resetDayOfMonth by viewModel.resetDayOfMonth.collectAsState()

    val linkState by viewModel.linkState.collectAsState()
    val offlineAttempt by viewModel.offlineAttempt.collectAsState()
    val secondsToNextRetry by viewModel.secondsToNextRetry.collectAsState()
    val connectedDevices by viewModel.connectedDevices.collectAsState()
    val blockedDevices by viewModel.blockedDevices.collectAsState()
    val deviceBusyMac by viewModel.deviceBusyMac.collectAsState()
    val deviceMessage by viewModel.deviceMessage.collectAsState()
    val deviceListError by viewModel.deviceListError.collectAsState()

    val isOffline = !isDemoMode && linkState != LinkState.ONLINE
    val gaveUp = !isDemoMode && linkState == LinkState.OFFLINE_GAVE_UP
    val screenBackground = if (gaveUp) Color(0xFF3A3F47) else DarkNavyBase
    val modemPreview by viewModel.modemPreview.collectAsState()
    val modelTitle = networkStatus.deviceModel.ifBlank { modemPreview.first.ifBlank { "Huawei MiFi" } }

    LaunchedEffect(deviceMessage) {
        deviceMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearDeviceMessage()
        }
    }

    var showMenu by remember { mutableStateOf(false) }
    var showRebootConfirmDialog by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(screenBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 18.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top App Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_app_logo),
                        contentDescription = "Huawei Management",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = modelTitle,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )
                            if (isDemoMode) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(SignalOrange.copy(alpha = 0.2f))
                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "SIMULASI",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SignalOrange
                                    )
                                }
                            } else {
                                val badgeColor = if (isOffline) WarningRed else SignalGreen
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(badgeColor.copy(alpha = 0.2f))
                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                        .testTag("link_status_badge")
                                ) {
                                    Text(
                                        text = if (isOffline) "OFFLINE" else "TERHUBUNG",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = badgeColor
                                    )
                                }
                            }
                        }
                        Text(
                            text = networkStatus.firmwareVersion.ifBlank { modemPreview.second }
                                .let { if (it.isBlank()) "Firmware —" else "Firmware $it" },
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        )
                    }
                }

                // Top Right: Turn off / Restart Menu Button
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.testTag("turnoff_menu_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = "Menu Daya / Restart",
                            tint = TextSecondary
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier
                            .background(DarkNavySurface)
                            .border(1.dp, DarkNavySurfaceElevated, RoundedCornerShape(8.dp))
                    ) {
                        // Requested: "tambahkan menu restar modem pada bagian turnoff button di pojok kanan atas"
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.RestartAlt,
                                        contentDescription = null,
                                        tint = SignalOrange,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("Restart Modem", color = TextPrimary)
                                }
                            },
                            onClick = {
                                showMenu = false
                                showRebootConfirmDialog = true
                            }
                        )

                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.PowerSettingsNew,
                                        contentDescription = null,
                                        tint = WarningRed,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("Putuskan Koneksi", color = WarningRed)
                                }
                            },
                            onClick = {
                                showMenu = false
                                viewModel.disconnect()
                            }
                        )
                    }
                }
            }

            // Banner status offline + auto retry / tombol refresh manual
            OfflineBanner(
                linkState = if (isDemoMode) LinkState.ONLINE else linkState,
                attempt = offlineAttempt,
                maxAttempts = 10,
                secondsToNext = secondsToNextRetry,
                onManualRefresh = { viewModel.manualRefresh() }
            )

            // 1. Provider & Signal Strength (5 Bars) Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("provider_signal_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = DarkNavySurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkNavySurfaceElevated)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "OPERATOR SELULER",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = TextMuted,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = networkStatus.operator,
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = TextPrimary
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(ElectricBlue.copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "4G LTE",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ElectricBlue
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.BatteryFull,
                                    contentDescription = null,
                                    tint = SignalGreen,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${networkStatus.batteryPercent}%",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }

                            // Accurate Wi-Fi Users Count
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Wifi,
                                    contentDescription = null,
                                    tint = ElectricBlue,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${networkStatus.wifiUsers} User Terhubung",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextSecondary
                                )
                            }
                        }
                    }

                    // 5-bar Signal Indicator with strict colored label
                    val (signalLabel, signalColor) = getSignalLevelInfo(networkStatus.signalBars)
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.Center
                    ) {
                        SignalBars(
                            bars = networkStatus.signalBars,
                            barWidth = 6.dp,
                            maxHeight = 32.dp,
                            spacing = 4.dp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = signalLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = signalColor,
                                fontSize = 12.sp
                            )
                        )
                    }
                }
            }

            // 2. Real-time Speeds: Download & Upload
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Download Speed Card
                val (downValue, downUnit) = formatSpeed(trafficData.downloadRateBps)
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("download_speed_card"),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkNavySurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkNavySurfaceElevated)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(SpeedDownloadColor.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDownward,
                                        contentDescription = "Download",
                                        tint = SpeedDownloadColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Download",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = TextSecondary,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = downValue,
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = SpeedDownloadColor
                                )
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = downUnit,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = TextMuted,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                    }
                }

                // Upload Speed Card
                val (upValue, upUnit) = formatSpeed(trafficData.uploadRateBps)
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("upload_speed_card"),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkNavySurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkNavySurfaceElevated)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(SpeedUploadColor.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowUpward,
                                        contentDescription = "Upload",
                                        tint = SpeedUploadColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Upload",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = TextSecondary,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = upValue,
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = SpeedUploadColor
                                )
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = upUnit,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = TextMuted,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                    }
                }
            }

            // 3. Network Parameters: Alamat IP WAN & Bandwidth
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("network_params_card"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = DarkNavySurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkNavySurfaceElevated)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Alamat IP Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Dns,
                                contentDescription = null,
                                tint = ElectricBlue,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Alamat IP WAN (Internet)",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = TextMuted,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                                Text(
                                    text = networkStatus.wanIp.ifBlank { "Membaca IP..." },
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (networkStatus.wanIp.isNotBlank()) TextPrimary else TextMuted
                                    )
                                )
                            }
                        }

                        if (networkStatus.wanIp.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("WAN IP", networkStatus.wanIp)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "IP Disalin: ${networkStatus.wanIp}", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Salin IP",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(DarkNavySurfaceElevated.copy(alpha = 0.6f))
                    )

                    // Bandwidth & Active Band
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = SignalGreen,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Frekuensi Band Terkunci",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = TextMuted,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                                Text(
                                    text = selectedBand.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(DarkNavySurfaceElevated)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = selectedBand.id,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElectricBlue
                            )
                        }
                    }
                }
            }

            // 4. Action Buttons: "Pilihan Band" and "Ganti IP"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Button 1: Pilihan Band
                Button(
                    onClick = { viewModel.openBandDialog() },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("pilihan_band_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DarkNavySurfaceVariant,
                        contentColor = TextPrimary
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Pilihan Band",
                        tint = ElectricBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Pilihan Band",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                // Button 2: Ganti IP
                Button(
                    onClick = { viewModel.openIpChangeDialog() },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("ganti_ip_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DarkNavySurfaceVariant,
                        contentColor = TextPrimary
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SignalOrange.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapHoriz,
                        contentDescription = "Ganti IP",
                        tint = SignalOrange,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Ganti IP",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            // 5. Clean, Non-Overlapping UI: Pemakaian Data Bulan Ini dengan Batas Limit & Reset Tanggal
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("monthly_data_card"),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = DarkNavySurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkNavySurfaceElevated)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Header Row with Atur Limit Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(ElectricBlue.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DataUsage,
                                    contentDescription = null,
                                    tint = ElectricBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Pemakaian Data Bulan Ini",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )
                        }

                        // Button to edit data limit and reset day on MiFi
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(ElectricBlue.copy(alpha = 0.15f))
                                .border(1.dp, ElectricBlue.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .clickable { viewModel.openDataLimitDialog() }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("edit_data_limit_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Pembatasan Data",
                                tint = ElectricBlue,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Atur Limit",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElectricBlue
                            )
                        }
                    }

                    // Big Usage Counter
                    val totalBytes = trafficData.totalMonthBytes
                    val displayUsage = if (totalBytes > 0L) {
                        formatBytes(totalBytes)
                    } else {
                        "0.00 GB"
                    }

                    Column {
                        Text(
                            text = displayUsage,
                            style = MaterialTheme.typography.displaySmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = TextPrimary
                            )
                        )
                        Text(
                            text = "Total lalu lintas data tercatat di modem",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        )
                    }

                    // MiFi Limit & Reset Day Parameters
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(DarkNavySurfaceVariant)
                                .border(1.dp, DarkNavySurfaceElevated, RoundedCornerShape(12.dp))
                                .padding(10.dp)
                        ) {
                            Column {
                                Text(
                                    text = "BATAS LIMIT KUOTA",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        color = TextMuted,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = formatLimitGb(quotaLimitGb),
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = TextPrimary
                                    )
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(DarkNavySurfaceVariant)
                                .border(1.dp, DarkNavySurfaceElevated, RoundedCornerShape(12.dp))
                                .padding(10.dp)
                        ) {
                            Column {
                                Text(
                                    text = "TANGGAL RESET",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        color = TextMuted,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Tgl $resetDayOfMonth Tiap Bulan",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = SignalGreen
                                    )
                                )
                            }
                        }
                    }

                    // Quota Usage Progress Bar
                    val limitBytes = (quotaLimitGb * 1024f * 1024f * 1024f).toLong()
                    val usedFraction = if (limitBytes > 0) (totalBytes.toFloat() / limitBytes.toFloat()).coerceIn(0f, 1f) else 0f
                    val progressColor = when {
                        usedFraction >= 0.9f -> WarningRed
                        usedFraction >= 0.75f -> SignalOrange
                        else -> ElectricBlue
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${(usedFraction * 100).toInt()}% terpakai",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = progressColor
                            )
                            val remainingBytes = (limitBytes - totalBytes).coerceAtLeast(0L)
                            Text(
                                text = "Sisa: ${formatBytes(remainingBytes)}",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }

                        LinearProgressIndicator(
                            progress = { usedFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = progressColor,
                            trackColor = DarkNavySurfaceElevated
                        )
                    }

                    // Breakdown Download vs Upload
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(DarkNavySurfaceVariant)
                                .padding(12.dp)
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(SpeedDownloadColor)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Download",
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = formatBytes(trafficData.monthDownloadBytes),
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(DarkNavySurfaceVariant)
                                .padding(12.dp)
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(SpeedUploadColor)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Upload",
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = formatBytes(trafficData.monthUploadBytes),
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // 5b. Perangkat terhubung (nama, waktu terhubung, tombol blokir)
            ConnectedDevicesCard(
                devices = connectedDevices,
                busyMac = deviceBusyMac,
                onBlock = { viewModel.blockDevice(it) },
                errorMessage = deviceListError,
                reportedCount = networkStatus.wifiUsers
            )

            // 5c. Perangkat diblokir (dropdown buka/tutup, tombol unban)
            BlockedDevicesCard(
                devices = blockedDevices,
                busyMac = deviceBusyMac,
                onUnblock = { viewModel.unblockDevice(it) }
            )

            // 6. Prominent "Pencari Sinyal" CTA Button
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.openSignalHunter() }
                    .testTag("pencari_sinyal_button"),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF0F223D)
                ),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, ElectricBlue)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(ElectricBlue),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Radar,
                                contentDescription = "Pencari Sinyal",
                                tint = DarkNavyBase,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Pencari Sinyal",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = TextPrimary
                                    )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(SignalGreen.copy(alpha = 0.2f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "LIVE",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = SignalGreen
                                    )
                                }
                            }
                            Text(
                                text = "Buka indikator SINR, PCI tower & RSSI untuk penyesuaian antena",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "Buka",
                        tint = ElectricBlue,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Tanda tangan pengembang (hanya tampil di halaman utama setelah login)
            Text(
                text = "Dikembangkan oleh Moko96",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 10.dp)
                    .testTag("developer_signature"),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = TextMuted
            )
        }

        // Reboot confirmation dialog
        if (showRebootConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showRebootConfirmDialog = false },
                title = { Text("Restart $modelTitle?", color = TextPrimary) },
                text = {
                    Text(
                        "Modem akan mematikan dan menyalakan kembali sistem operasinya. Koneksi Wi-Fi akan terputus sementara selama 1-2 menit.",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showRebootConfirmDialog = false
                            Toast.makeText(context, "Mengirim perintah restart ke modem...", Toast.LENGTH_SHORT).show()
                            viewModel.rebootModem { msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SignalOrange)
                    ) {
                        Text("Restart Sekarang")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRebootConfirmDialog = false }) {
                        Text("Batal", color = TextSecondary)
                    }
                },
                containerColor = DarkNavySurface
            )
        }

        // Dialogs
        if (showBandDialog) {
            BandSelectorDialog(
                selectedBand = selectedBand,
                onSelectBand = { viewModel.selectBand(it) },
                onDismiss = { viewModel.closeBandDialog() }
            )
        }

        if (showIpChangeDialog) {
            IpChangeDialog(
                progress = ipChangeProgress,
                onRetry = { viewModel.executeChangeIp() },
                onDismiss = { viewModel.closeIpChangeDialog() }
            )
        }

        if (showDataLimitDialog) {
            DataLimitDialog(
                currentLimitGb = quotaLimitGb,
                currentResetDay = resetDayOfMonth,
                onSave = { limitGb, resetDay ->
                    viewModel.updateDataLimit(limitGb, resetDay)
                },
                onDismiss = { viewModel.closeDataLimitDialog() }
            )
        }
    }
}

private fun formatSpeed(bytesPerSec: Long): Pair<String, String> {
    return when {
        bytesPerSec >= 1_000_000 -> {
            val mbps = bytesPerSec / 1_000_000f
            Pair(String.format(Locale.US, "%.2f", mbps), "MB/s")
        }
        bytesPerSec >= 1_000 -> {
            val kbps = bytesPerSec / 1_000f
            Pair(String.format(Locale.US, "%.1f", kbps), "KB/s")
        }
        else -> {
            Pair(bytesPerSec.toString(), "B/s")
        }
    }
}

private fun formatLimitGb(gb: Float): String {
    return if (gb % 1f == 0f) "${gb.toInt()} GB" else String.format(Locale.US, "%.1f GB", gb)
}

private fun formatBytes(bytes: Long): String {
    val gb = bytes / (1024f * 1024f * 1024f)
    return if (gb >= 1f) {
        String.format(Locale.US, "%.2f GB", gb)
    } else {
        val mb = bytes / (1024f * 1024f)
        String.format(Locale.US, "%.1f MB", mb)
    }
}
