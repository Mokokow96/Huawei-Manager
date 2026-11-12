package com.huawei.manager.newui.ui.components

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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.huawei.manager.newui.model.BandOption
import com.huawei.manager.newui.model.BandSelection
import com.huawei.manager.newui.ui.theme.DarkNavySurface
import com.huawei.manager.newui.ui.theme.DarkNavySurfaceElevated
import com.huawei.manager.newui.ui.theme.DarkNavySurfaceVariant
import com.huawei.manager.newui.ui.theme.ElectricBlue
import com.huawei.manager.newui.ui.theme.TextMuted
import com.huawei.manager.newui.ui.theme.TextPrimary
import com.huawei.manager.newui.ui.theme.TextSecondary

@Composable
fun BandSelectorDialog(
    selection: BandSelection,
    onSelectAuto: () -> Unit,
    onToggleBand: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .border(1.dp, DarkNavySurfaceElevated, RoundedCornerShape(24.dp))
                .testTag("band_selector_dialog"),
            color = DarkNavySurface
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(ElectricBlue.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Pilihan Band",
                                tint = ElectricBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Pilihan Band LTE",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )
                            Text(
                                text = "Hidupkan / matikan band sesukamu",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_band_dialog")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Tutup",
                            tint = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Auto: semua band diizinkan
                    BandRow(
                        title = BandOption.AUTO.name,
                        on = selection.isAuto,
                        tag = "band_item_AUTO",
                        isSwitch = false,
                        onClick = onSelectAuto
                    )
                    // Saklar per band
                    BandOption.SINGLE_BANDS.forEach { band ->
                        BandRow(
                            title = band.name,
                            on = !selection.isAuto && band.id in selection.ids,
                            tag = "band_item_${band.id}",
                            isSwitch = true,
                            onClick = { onToggleBand(band.id) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "*Minimal satu band harus tetap menyala. Perubahan langsung diterapkan ke modem.",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = TextMuted,
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun BandRow(
    title: String,
    on: Boolean,
    tag: String,
    isSwitch: Boolean,
    onClick: () -> Unit
) {
    val bg = if (on) ElectricBlue.copy(alpha = 0.15f) else DarkNavySurfaceVariant
    val border = if (on) ElectricBlue else DarkNavySurfaceElevated.copy(alpha = 0.5f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(if (on) ElectricBlue else DarkNavySurfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (on) Icons.Default.Check else Icons.Default.CellTower,
                    contentDescription = null,
                    tint = if (on) DarkNavySurface else TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    color = if (on) ElectricBlue else TextPrimary,
                    fontSize = 14.sp
                )
            )
        }
        if (isSwitch) {
            Switch(
                checked = on,
                onCheckedChange = { onClick() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = DarkNavySurface,
                    checkedTrackColor = ElectricBlue
                )
            )
        }
    }
}
