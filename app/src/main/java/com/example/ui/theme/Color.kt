package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Dark Blue & Midnight Palette (Nyaman & Modern)
val DarkNavyBase = Color(0xFF090D1A)        // Deepest dark navy background
val DarkNavySurface = Color(0xFF0F172A)     // Slate dark blue surface
val DarkNavySurfaceVariant = Color(0xFF1E293B) // Card & container background
val DarkNavySurfaceElevated = Color(0xFF27354F) // Border / highlight container

// Primary & Accent Colors
val ElectricBlue = Color(0xFF38BDF8)       // Vibrant sky blue
val ElectricBlueContainer = Color(0xFF075985)
val CyberIndigo = Color(0xFF818CF8)        // Soft violet/indigo
val CyberIndigoContainer = Color(0xFF312E81)

// Signal Quality Colors (Strictly matching user spec)
// 0-3 dB: Abu-abu
val SignalGray = Color(0xFF64748B)         // Slate Gray (0-3 dB Poor)
val SignalGrayLight = Color(0xFF94A3B8)
val SignalGrayBg = Color(0xFF1E293B)

// 4-6 dB: Oranye
val SignalOrange = Color(0xFFF59E0B)       // Vivid Amber Orange (4-6 dB Fair)
val SignalOrangeLight = Color(0xFFFBBF24)
val SignalOrangeBg = Color(0xFF451A03)

// 7+ dB: Hijau
val SignalGreen = Color(0xFF10B981)        // Emerald Green (7+ dB Good)
val SignalGreenNeon = Color(0xFF00E676)    // Neon Green (12+ dB Peak)
val SignalGreenBg = Color(0xFF064E3B)

// Speed & Metric Accents
val SpeedDownloadColor = Color(0xFF06B6D4) // Cyan
val SpeedUploadColor = Color(0xFFA855F7)   // Purple
val WarningRed = Color(0xFFEF4444)
val TextPrimary = Color(0xFFF1F5F9)
val TextSecondary = Color(0xFF94A3B8)
val TextMuted = Color(0xFF64748B)
