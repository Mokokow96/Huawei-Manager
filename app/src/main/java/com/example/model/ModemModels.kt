package com.example.model

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.SignalGray
import com.example.ui.theme.SignalGreen
import com.example.ui.theme.SignalGreenNeon
import com.example.ui.theme.SignalOrange
import com.example.ui.theme.WarningRed

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    ERROR
}

data class SignalData(
    val sinr: Float = 0f,             // in dB
    val rssi: Int = -90,              // in dBm (-51 to -113)
    val rsrp: Int = -105,             // in dBm (-140 to -44)
    val rsrq: Int = -12,              // in dB (-20 to -3)
    val pci: Int = 0,                 // Physical Cell ID (Tower identification)
    val cellId: String = "0",         // eNodeB Cell ID
    val networkMode: String = "4G LTE",
    val currentBand: String = "Band 3 (1800 MHz)",
    val timestamp: Long = System.currentTimeMillis(),
    val responseTimeMs: Long = 120L
) {
    /**
     * Sesuai request user:
     * 0-3 itu berwarna abu-abu
     * 4-6 itu oranye
     * 7-seterusnya itu hijau (dengan neon untuk 13+)
     */
    val sinrColor: Color
        get() = when {
            sinr < 0f -> SignalGray
            sinr <= 3.99f -> SignalGray
            sinr <= 6.99f -> SignalOrange
            sinr <= 12.99f -> SignalGreen
            else -> SignalGreenNeon
        }

    val sinrQualityLabel: String
        get() = when {
            sinr < 0f -> "Sangat Buruk"
            sinr <= 3.99f -> "Buruk (0-3 dB)"
            sinr <= 6.99f -> "Sedang (4-6 dB)"
            sinr <= 12.99f -> "Bagus (7-12 dB)"
            else -> "Istimewa (>12 dB)"
        }

    val rssiColor: Color
        get() = when {
            rssi >= -65 -> SignalGreen
            rssi >= -79 -> SignalOrange
            else -> WarningRed
        }

    val rssiQualityLabel: String
        get() = when {
            rssi >= -65 -> "Sangat Kuat"
            rssi >= -75 -> "Kuat"
            rssi >= -85 -> "Cukup"
            else -> "Lemah"
        }
}

data class SignalBarQuality(
    val label: String,
    val color: Color
)

fun getSignalBarQuality(bars: Int): SignalBarQuality {
    return when {
        bars <= 1 -> SignalBarQuality("Lemah", WarningRed)
        bars <= 3 -> SignalBarQuality("Sedang", SignalOrange)
        else -> SignalBarQuality("Bagus", SignalGreen)
    }
}

data class TrafficData(
    val downloadRateBps: Long = 0L,       // Bytes per second
    val uploadRateBps: Long = 0L,         // Bytes per second
    val currentDownloadBytes: Long = 0L,
    val currentUploadBytes: Long = 0L,
    val monthDownloadBytes: Long = 0L,
    val monthUploadBytes: Long = 0L,
    val monthQuotaLimitBytes: Long = 50_000_000_000L, // 50 GB default quota
    val resetDayOfMonth: Int = 1
) {
    val totalMonthBytes: Long
        get() = monthDownloadBytes + monthUploadBytes

    val quotaUsagePercent: Float
        get() = if (monthQuotaLimitBytes > 0) {
            (totalMonthBytes.toFloat() / monthQuotaLimitBytes.toFloat()).coerceIn(0f, 1f)
        } else 0f
}

data class NetworkStatus(
    val operator: String = "Telkomsel",
    val signalBars: Int = 4,              // 0 to 5 bars
    val wanIp: String = "10.165.74.82",
    val gatewayIp: String = "192.168.8.1",
    val bandwidth: String = "20 MHz (FDD LTE)",
    val connectionState: ConnectionState = ConnectionState.CONNECTED,
    val batteryPercent: Int = 88,
    val wifiUsers: Int = 3,
    val deviceModel: String = "Huawei E5372",
    val firmwareVersion: String = "21.290.23.00.00 (Mod)",
    val isDemoMode: Boolean = true
)

data class BandOption(
    val id: String,
    val name: String,
    val frequency: String,
    val maskHex: String
) {
    companion object {
        val INDONESIA_BANDS = listOf(
            BandOption(
                id = "AUTO",
                name = "Auto (Semua Band)",
                frequency = "Otomatis",
                maskHex = "7FFFFFFFFFFFFFFF"
            ),
            BandOption(
                id = "B40",
                name = "B40 2300 MHz",
                frequency = "2300 MHz TDD",
                maskHex = "8000000000"
            ),
            BandOption(
                id = "B3",
                name = "B3 1800 MHz",
                frequency = "1800 MHz FDD",
                maskHex = "4"
            ),
            BandOption(
                id = "B1",
                name = "B1 2100 MHz",
                frequency = "2100 MHz FDD",
                maskHex = "1"
            ),
            BandOption(
                id = "B8",
                name = "B8 900 MHz",
                frequency = "900 MHz FDD",
                maskHex = "80"
            ),
            BandOption(
                id = "B5",
                name = "B5 850 MHz",
                frequency = "850 MHz FDD",
                maskHex = "10"
            ),
            BandOption(
                id = "B1_B3",
                name = "B1 + B3 (2100 + 1800 MHz)",
                frequency = "Dual Band CA",
                maskHex = "5"
            ),
            BandOption(
                id = "B3_B8",
                name = "B3 + B8 (1800 + 900 MHz)",
                frequency = "Dual Band CA",
                maskHex = "84"
            ),
            BandOption(
                id = "B3_B40",
                name = "B3 + B40 (1800 + 2300 MHz)",
                frequency = "FDD + TDD CA",
                maskHex = "8000000004"
            )
        )
    }
}

sealed class IpChangeProgress {
    object Idle : IpChangeProgress()
    object Disconnecting : IpChangeProgress()
    object RequestingNewIp : IpChangeProgress()
    data class Success(val oldIp: String, val newIp: String) : IpChangeProgress()
    data class Failed(val reason: String) : IpChangeProgress()
}
