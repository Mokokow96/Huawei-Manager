package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.DarkNavyBase
import com.example.ui.theme.DarkNavySurface
import com.example.ui.theme.DarkNavySurfaceElevated
import com.example.ui.theme.DarkNavySurfaceVariant
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.SignalGray
import com.example.ui.theme.SignalGreen
import com.example.ui.theme.SignalGreenNeon
import com.example.ui.theme.SignalOrange
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningRed
import com.example.viewmodel.ModemViewModel
import java.util.Locale

@Composable
fun SignalHunterScreen(
    viewModel: ModemViewModel,
    modifier: Modifier = Modifier
) {
    BackHandler {
        viewModel.closeSignalHunter()
    }

    val signalData by viewModel.signalData.collectAsState()
    val linkState by viewModel.linkState.collectAsState()
    val offlineAttempt by viewModel.offlineAttempt.collectAsState()
    val secondsToNextRetry by viewModel.secondsToNextRetry.collectAsState()
    val isDemoModeHunter by viewModel.isDemoMode.collectAsState()
    val sessionMaxSinr by viewModel.sessionMaxSinr.collectAsState()
    val audioBeepEnabled by viewModel.audioBeepEnabled.collectAsState()
    val sinrHistory by viewModel.sinrHistory.collectAsState()
    val selectedBand by viewModel.selectedBand.collectAsState()

    val scrollState = rememberScrollState()

    val animatedSinrColor by animateColorAsState(
        targetValue = signalData.sinrColor,
        animationSpec = tween(250),
        label = "sinrColor"
    )

    val animatedSinrValue by animateFloatAsState(
        targetValue = signalData.sinr.coerceIn(-5f, 25f),
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "sinrValue"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkNavyBase)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { viewModel.closeSignalHunter() },
                        modifier = Modifier.testTag("back_to_dashboard_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali ke Menu Utama",
                            tint = ElectricBlue
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Column {
                        Text(
                            text = "Pencari Sinyal",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = TextPrimary
                            )
                        )
                        Text(
                            text = "Penyesuaian Presisi Arah Antena",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        )
                    }
                }

                // Audio Beeper Quick Toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (audioBeepEnabled) ElectricBlue.copy(alpha = 0.2f) else DarkNavySurfaceVariant)
                        .border(
                            1.dp,
                            if (audioBeepEnabled) ElectricBlue else DarkNavySurfaceElevated,
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { viewModel.toggleAudioBeep(!audioBeepEnabled) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("audio_beeper_toggle")
                ) {
                    Icon(
                        imageVector = if (audioBeepEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                        contentDescription = "Beeper Suara",
                        tint = if (audioBeepEnabled) ElectricBlue else TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (audioBeepEnabled) "Beeper ON" else "Beeper",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (audioBeepEnabled) ElectricBlue else TextSecondary
                    )
                }
            }

            // Status offline + auto retry / refresh manual
            com.example.ui.components.OfflineBanner(
                linkState = if (isDemoModeHunter) com.example.model.LinkState.ONLINE else linkState,
                attempt = offlineAttempt,
                maxAttempts = 10,
                secondsToNext = secondsToNextRetry,
                onManualRefresh = { viewModel.manualRefresh() }
            )

            // 1. Tower Identification Card: PCI & Cell ID
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("pci_tower_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = DarkNavySurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkNavySurfaceElevated)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(ElectricBlue.copy(alpha = 0.25f), Color.Transparent)
                                    )
                                )
                                .border(1.dp, ElectricBlue.copy(alpha = 0.6f), RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CellTower,
                                contentDescription = "Tower BTS",
                                tint = ElectricBlue,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column {
                            Text(
                                text = "PCI (PHYSICAL CELL ID)",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = TextMuted,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${signalData.pci}",
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = TextPrimary
                                    )
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(DarkNavySurfaceElevated)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "TOWER TERKUNCI",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SignalGreen
                                    )
                                }
                            }
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Cell ID: ${signalData.cellId}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                        Text(
                            text = selectedBand.name.take(16),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ElectricBlue
                        )
                    }
                }
            }

            // 2. Main Animated SINR Gauge Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sinr_gauge_card"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = DarkNavySurface),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, animatedSinrColor.copy(alpha = 0.7f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Top Card Row: Title & Peak SINR
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "KUALITAS SINYAL (SINR)",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = TextMuted,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            )
                            Text(
                                text = signalData.sinrQualityLabel,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = animatedSinrColor
                                )
                            )
                        }

                        // Session Peak Chip
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(DarkNavySurfaceVariant)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Puncak: ",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                            Text(
                                text = String.format(Locale.US, "%.1f dB", sessionMaxSinr),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = SignalGreenNeon
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.RestartAlt,
                                contentDescription = "Reset Puncak",
                                tint = TextMuted,
                                modifier = Modifier
                                    .size(14.dp)
                                    .clickable { viewModel.resetSessionPeak() }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Circular Arc Gauge Canvas
                    Box(
                        modifier = Modifier
                            .size(230.dp)
                            .testTag("sinr_circular_gauge"),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val strokeWidth = 18.dp.toPx()
                            val diameter = size.minDimension - strokeWidth
                            val arcSize = Size(diameter, diameter)
                            val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)

                            val startAngle = 150f
                            val sweepMax = 240f

                            drawArc(
                                color = Color(0xFF1E293B),
                                startAngle = startAngle,
                                sweepAngle = sweepMax,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )

                            val progress = ((animatedSinrValue.coerceAtLeast(0f)) / 20f).coerceIn(0.02f, 1f)
                            val activeSweep = sweepMax * progress

                            drawArc(
                                color = animatedSinrColor,
                                startAngle = startAngle,
                                sweepAngle = activeSweep,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                        }

                        // Digital Readout Inside Gauge
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = String.format(Locale.US, "%.1f", signalData.sinr),
                                style = MaterialTheme.typography.displayMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = TextPrimary
                                )
                            )
                            Text(
                                text = "dB (SINR)",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = animatedSinrColor
                                )
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            // User requested: "pada grafik dB (SINR) tampilkan juga berapa respon dalam ms singal itu update"
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(DarkNavySurfaceVariant.copy(alpha = 0.85f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(SignalGreenNeon)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "${signalData.responseTimeMs} ms",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ElectricBlue
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Requested: "keterangan (Abu-abu) (Oranye) (Hijau) seharusnya diganti menjadi WEEK, Medium, Good"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkNavySurfaceVariant.copy(alpha = 0.6f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LegendItem(
                            color = SignalGray,
                            label = "WEAK (0-3 dB)",
                            isActive = signalData.sinr <= 3.99f
                        )
                        LegendItem(
                            color = SignalOrange,
                            label = "Medium (4-6 dB)",
                            isActive = signalData.sinr in 4f..6.99f
                        )
                        LegendItem(
                            color = SignalGreen,
                            label = "Good (7+ dB)",
                            isActive = signalData.sinr >= 7f
                        )
                    }
                }
            }

            // 3. Colored RSSI Bar Indicator
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("rssi_bar_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = DarkNavySurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkNavySurfaceElevated)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "KEKUATAN SINYAL (RSSI)",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = TextMuted,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${signalData.rssi} dBm",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = TextPrimary
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "(${signalData.rssiQualityLabel})",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = signalData.rssiColor
                                    )
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(signalData.rssiColor.copy(alpha = 0.2f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (signalData.rssi >= -70) "OPTIMAL" else "SEDANG",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = signalData.rssiColor
                            )
                        }
                    }

                    // RSSI Progress Bar
                    val rssiProgress = ((signalData.rssi - (-110f)) / 60f).coerceIn(0.05f, 1f)
                    val animatedRssiProgress by animateFloatAsState(
                        targetValue = rssiProgress,
                        animationSpec = tween(250),
                        label = "rssiProgress"
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(DarkNavySurfaceElevated)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(animatedRssiProgress)
                                .height(14.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            WarningRed,
                                            SignalOrange,
                                            signalData.rssiColor
                                        )
                                    )
                                )
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "-110 dBm (Lemah)", fontSize = 10.sp, color = TextMuted)
                        Text(text = "-80 dBm", fontSize = 10.sp, color = TextMuted)
                        Text(text = "-50 dBm (Kuat)", fontSize = 10.sp, color = TextMuted)
                    }
                }
            }

            // 4. Secondary Parameters: RSRP & RSRQ
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // RSRP Card
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkNavySurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkNavySurfaceElevated)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "RSRP (DAYA)",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = TextMuted,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${signalData.rsrp} dBm",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = TextPrimary
                            )
                        )
                        Text(
                            text = "Reference Signal Received",
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                    }
                }

                // RSRQ Card
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkNavySurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkNavySurfaceElevated)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "RSRQ (KUALITAS)",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = TextMuted,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${signalData.rsrq} dB",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = TextPrimary
                            )
                        )
                        Text(
                            text = "Signal Quality Ratio",
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                    }
                }
            }

            // 5. Requested: "pada bagian kecepatan refresh singal di ganti saja sebagai graphic history dari kekuatan SINR singal sebelumya"
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sinr_history_graph_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = DarkNavySurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkNavySurfaceElevated)
            ) {
                Column(
                    modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 14.dp)
                ) {
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
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(ElectricBlue.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ShowChart,
                                    contentDescription = null,
                                    tint = ElectricBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Grafik Riwayat SINR",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                )
                                Text(
                                    text = "Respon sinyal saat antena diputar",
                                    fontSize = 10.sp,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Real-time",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = SignalGreen,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Enlarged High-Resolution Oscilloscope Graph with Vertical Auto-Range on Left
                    SinrExpandedChart(
                        history = sinrHistory,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                    )
                }
            }

            // Practical Antenna Aiming Tip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(DarkNavySurfaceVariant.copy(alpha = 0.5f))
                    .border(1.dp, DarkNavySurfaceElevated.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = ElectricBlue,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Tips: Putar antena perlahan dan perhatikan gelombang grafik riwayat di atas. Cari titik puncak gelombang tertinggi dengan nilai PCI tower yang tetap stabil.",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun LegendItem(
    color: Color,
    label: String,
    isActive: Boolean
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(if (isActive) 10.dp else 8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            color = if (isActive) color else TextMuted
        )
    }
}

@Composable
private fun SinrExpandedChart(
    history: List<Float>,
    modifier: Modifier = Modifier
) {
    // User requested: "Grafik Riwayat SINR seharusnya kekuatan singal SINR berada di sebelah kiri
    // secara vertikal dari paling lemah sampai paling tinggi yang otomatis menyesuaikan rentangnya sendiri"
    val rawMin = if (history.isNotEmpty()) history.minOrNull() ?: 0f else 0f
    val rawMax = if (history.isNotEmpty()) history.maxOrNull() ?: 15f else 15f
    // Rentang minimal 5 dB, dipusatkan di sekitar data supaya gelombang tidak menempel di dasar
    // dengan ruang kosong besar di atasnya.
    val center = (rawMin + rawMax) / 2f
    val halfSpan = kotlin.math.max((rawMax - rawMin) / 2f + 1f, 2.5f)
    val minVal = kotlin.math.floor(center - halfSpan)
    val maxVal = kotlin.math.ceil(center + halfSpan)
    val midVal = (minVal + maxVal) / 2f

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Vertical scale on the left: from lowest (minVal) at bottom to highest (maxVal) at top
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(end = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = String.format(Locale.US, "%.0f dB", maxVal),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = SignalGreen
            )
            Text(
                text = String.format(Locale.US, "%.0f dB", midVal),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = TextMuted
            )
            Text(
                text = String.format(Locale.US, "%.0f dB", minVal),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = WarningRed
            )
        }

        // Oscilloscope waveform canvas
        Canvas(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            val h = size.height
            val w = size.width

            // Horizontal grid lines corresponding to top, middle, bottom
            drawLine(
                color = Color(0xFF1E293B),
                start = Offset(0f, 0f),
                end = Offset(w, 0f),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = Color(0xFF1E293B),
                start = Offset(0f, h / 2f),
                end = Offset(w, h / 2f),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = Color(0xFF1E293B),
                start = Offset(0f, h),
                end = Offset(w, h),
                strokeWidth = 1.dp.toPx()
            )

            if (history.size < 2) return@Canvas

            val stepX = w / (history.size - 1)
            val points = history.mapIndexed { index, sinr ->
                val clamped = sinr.coerceIn(minVal, maxVal)
                val normalizedY = 1f - ((clamped - minVal) / (maxVal - minVal))
                Offset(index * stepX, normalizedY * h)
            }

            // Draw filled gradient area under curve
            val fillPath = Path().apply {
                moveTo(points.first().x, h)
                points.forEach { lineTo(it.x, it.y) }
                lineTo(points.last().x, h)
                close()
            }

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        ElectricBlue.copy(alpha = 0.25f),
                        Color.Transparent
                    )
                )
            )

            // Draw line segments
            for (i in 0 until points.size - 1) {
                val p1 = points[i]
                val p2 = points[i + 1]
                val valAtPoint = history[i]
                val lineColor = when {
                    valAtPoint <= 3.99f -> SignalGray
                    valAtPoint <= 6.99f -> SignalOrange
                    else -> SignalGreen
                }

                drawLine(
                    color = lineColor,
                    start = p1,
                    end = p2,
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Glowing cursor circle on latest value
            val lastPoint = points.last()
            val lastVal = history.last()
            val cursorColor = when {
                lastVal <= 3.99f -> SignalGray
                lastVal <= 6.99f -> SignalOrange
                else -> SignalGreenNeon
            }

            drawCircle(
                color = cursorColor.copy(alpha = 0.3f),
                radius = 8.dp.toPx(),
                center = lastPoint
            )
            drawCircle(
                color = cursorColor,
                radius = 4.5.dp.toPx(),
                center = lastPoint
            )
        }
    }
}
