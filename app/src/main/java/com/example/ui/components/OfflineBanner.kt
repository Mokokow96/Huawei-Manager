package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.LinkState
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningRed

/**
 * Banner status offline.
 * - OFFLINE_RETRYING: menampilkan nomor percobaan (x/10) dan hitung mundur.
 * - OFFLINE_GAVE_UP: menampilkan tombol refresh manual.
 */
@Composable
fun OfflineBanner(
    linkState: LinkState,
    attempt: Int,
    maxAttempts: Int,
    secondsToNext: Int,
    onManualRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (linkState == LinkState.ONLINE) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(WarningRed.copy(alpha = 0.12f))
            .border(1.dp, WarningRed.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            .padding(14.dp)
            .testTag("offline_banner")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.WifiOff, null, tint = WarningRed, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("MiFi tidak terhubung", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(
                    if (linkState == LinkState.OFFLINE_RETRYING) {
                        if (secondsToNext > 0) "Mencoba menyambung ulang ($attempt/$maxAttempts) dalam ${secondsToNext}d…"
                        else "Mencoba menyambung ulang ($attempt/$maxAttempts)…"
                    } else {
                        "Gagal tersambung setelah $maxAttempts percobaan. Pastikan HP terhubung ke Wi-Fi MiFi."
                    },
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
            if (linkState == LinkState.OFFLINE_RETRYING) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = WarningRed
                )
            }
        }

        if (linkState == LinkState.OFFLINE_GAVE_UP) {
            Spacer(Modifier.size(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(WarningRed)
                    .clickable { onManualRefresh() }
                    .padding(vertical = 11.dp)
                    .testTag("manual_refresh_button"),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Icon(Icons.Default.Refresh, null, tint = TextPrimary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Refresh", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
            }
        }
    }
}
