package com.huawei.manager.newui.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = ElectricBlue,
    onPrimary = Color(0xFF001E2F),
    primaryContainer = ElectricBlueContainer,
    onPrimaryContainer = Color(0xFFCBE6FF),
    secondary = CyberIndigo,
    onSecondary = Color(0xFF1E1A4D),
    secondaryContainer = CyberIndigoContainer,
    onSecondaryContainer = Color(0xFFE0E0FF),
    tertiary = SignalGreen,
    onTertiary = Color(0xFF003822),
    tertiaryContainer = SignalGreenBg,
    onTertiaryContainer = Color(0xFF6FF7B5),
    background = DarkNavyBase,
    onBackground = TextPrimary,
    surface = DarkNavySurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkNavySurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = DarkNavySurfaceElevated,
    outlineVariant = Color(0xFF334155),
    error = WarningRed,
    onError = Color.White
)

@Composable
fun HuaweiManagerTheme(
    darkTheme: Boolean = true, // Default to comfortable dark mode as requested
    dynamicColor: Boolean = false, // Keep consistent dark blue branding
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
