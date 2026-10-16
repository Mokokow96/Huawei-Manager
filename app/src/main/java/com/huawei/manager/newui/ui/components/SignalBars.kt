package com.huawei.manager.newui.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.huawei.manager.newui.ui.theme.SignalGray
import com.huawei.manager.newui.ui.theme.SignalGreen
import com.huawei.manager.newui.ui.theme.SignalOrange
import com.huawei.manager.newui.ui.theme.WarningRed

/**
 * Helper to determine signal color and Indonesian label strictly matching user requirements:
 * 1 batang (<= 1) = Lemah (Merah)
 * 2-3 batang = Sedang (Oranye)
 * 4-5 batang = Bagus (Hijau)
 */
fun getSignalLevelInfo(bars: Int): Pair<String, Color> {
    return when {
        bars <= 1 -> Pair("Lemah", WarningRed)
        bars in 2..3 -> Pair("Sedang", SignalOrange)
        else -> Pair("Bagus", SignalGreen)
    }
}

/**
 * 5 Batang Indikator Kekuatan Sinyal Modem (0 - 5 Bar)
 */
@Composable
fun SignalBars(
    bars: Int,
    modifier: Modifier = Modifier,
    barWidth: Dp = 5.dp,
    maxHeight: Dp = 26.dp,
    spacing: Dp = 3.dp
) {
    val (_, activeColor) = getSignalLevelInfo(bars)

    val baseHeights = listOf(
        maxHeight * 0.28f,
        maxHeight * 0.46f,
        maxHeight * 0.64f,
        maxHeight * 0.82f,
        maxHeight * 1.0f
    )

    Row(
        modifier = modifier.height(maxHeight),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.Bottom
    ) {
        for (i in 0 until 5) {
            val isActive = i < bars
            val barColor by animateColorAsState(
                targetValue = if (isActive) activeColor else SignalGray.copy(alpha = 0.25f),
                animationSpec = tween(300),
                label = "barColor"
            )
            val barHeight by animateDpAsState(
                targetValue = baseHeights[i],
                animationSpec = tween(300),
                label = "barHeight"
            )

            Box(
                modifier = Modifier
                    .width(barWidth)
                    .height(barHeight)
                    .clip(RoundedCornerShape(2.dp))
                    .background(barColor)
            )
        }
    }
}
