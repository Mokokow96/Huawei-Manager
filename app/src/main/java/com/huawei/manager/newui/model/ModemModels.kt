package com.huawei.manager.newui.model

import androidx.compose.ui.graphics.Color
import com.huawei.manager.newui.ui.theme.SignalGray
import com.huawei.manager.newui.ui.theme.SignalGreen
import com.huawei.manager.newui.ui.theme.SignalGreenNeon
import com.huawei.manager.newui.ui.theme.SignalOrange
import com.huawei.manager.newui.ui.theme.WarningRed

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    ERROR
}

/** Status koneksi HP -> MiFi saat dashboard sedang dibuka. */
enum class LinkState {
    /** Terhubung dan data realtime mengalir. */
    ONLINE,
    /** MiFi tidak terjangkau; aplikasi mencoba menyambung ulang otomatis (maks. 10x / 5 menit). */
    OFFLINE_RETRYING,
    /** 10 percobaan gagal; menunggu pengguna menekan tombol refresh manual. */
    OFFLINE_GAVE_UP
}

/** Perangkat yang sedang terhubung ke Wi-Fi MiFi. */
data class ConnectedDevice(
    val mac: String,
    val ip: String,
    val name: String,
    /** Perkiraan waktu (epoch ms) perangkat mulai terhubung. 0 jika tidak diketahui. */
    val connectedSinceMs: Long,
    /** Lama terhubung dalam detik. 0 jika tidak diketahui. */
    val connectedSeconds: Long,
    val isThisPhone: Boolean = false
)

/** Perangkat yang masuk daftar blokir (MAC filter) di modem. */
data class BlockedDevice(
    val mac: String,
    val name: String
)

data class SignalData(
    val sinr: Float = 0f,             // in dB
    val rssi: Int = -90,              // in dBm (-51 to -113)
    val rsrp: Int = -105,             // in dBm (-140 to -44)
    val rsrq: Int = -12,              // in dB (-20 to -3)
    val pci: Int = 0,                 // Physical Cell ID (Tower identification)
    val cellId: String = "0",         // Cell ID (ECI)
    val enbId: String = "",           // eNodeB ID (kosong = tidak tersedia)
    val tac: String = "",             // Tracking Area Code (kosong = tidak tersedia)
    val networkMode: String = "4G LTE",
    /** ID band yang SEDANG dipakai modem saat ini, mis. "B40" (kosong = belum terbaca). */
    val activeBandId: String = "",
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
    val monthQuotaLimitBytes: Long = 53_687_091_200L, // 50 GB (GiB) default quota
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
    val deviceModel: String = "",
    val firmwareVersion: String = "",
    val isDemoMode: Boolean = true
)

data class BandOption(
    val id: String,
    val name: String,
    val frequency: String,
    val maskHex: String
) {
    companion object {
        /** Mask "Auto" (semua band) yang dikirim ke modem. */
        const val AUTO_MASK_HEX = "7FFFFFFFFFFFFFFF"

        val AUTO = BandOption(
            id = "AUTO",
            name = "Auto (Semua Band)",
            frequency = "Otomatis",
            maskHex = AUTO_MASK_HEX
        )

        /** Band yang bisa dihidup/matikan satu per satu (bit LTEBand di modem). */
        val SINGLE_BANDS = listOf(
            BandOption("B40", "B40 2300 MHz", "2300 MHz TDD", "8000000000"),
            BandOption("B3", "B3 1800 MHz", "1800 MHz FDD", "4"),
            BandOption("B1", "B1 2100 MHz", "2100 MHz FDD", "1"),
            BandOption("B8", "B8 900 MHz", "900 MHz FDD", "80"),
            BandOption("B5", "B5 850 MHz", "850 MHz FDD", "10")
        )

        /** Auto + semua band tunggal (dipertahankan agar kode/tes lama tetap berlaku). */
        val INDONESIA_BANDS = listOf(AUTO) + SINGLE_BANDS

        fun byId(id: String): BandOption? = SINGLE_BANDS.firstOrNull { it.id == id }

        /** "B40" + "B3" -> mask gabungan (OR bit) dalam hex. */
        fun maskFor(ids: Set<String>): String {
            var mask = java.math.BigInteger.ZERO
            for (id in ids) {
                byId(id)?.let { mask = mask.or(java.math.BigInteger(it.maskHex, 16)) }
            }
            return mask.toString(16).uppercase()
        }

        /**
         * Baca mask LTEBand dari modem -> pilihan band.
         * Mask berisi seluruh band tunggal PLUS band lain (mis. 7FFFFFFFFFFFFFFF) dianggap Auto.
         */
        fun selectionFromMask(maskHex: String): BandSelection {
            val mask = try {
                java.math.BigInteger(maskHex.trim().removePrefix("0x").removePrefix("0X"), 16)
            } catch (_: Exception) {
                return BandSelection.AUTO
            }
            val known = SINGLE_BANDS.filter { !mask.and(java.math.BigInteger(it.maskHex, 16)).equals(java.math.BigInteger.ZERO) }
                .map { it.id }.toSet()
            var knownMask = java.math.BigInteger.ZERO
            for (b in SINGLE_BANDS) knownMask = knownMask.or(java.math.BigInteger(b.maskHex, 16))
            val hasOthers = !mask.andNot(knownMask).equals(java.math.BigInteger.ZERO)
            return if (known.size == SINGLE_BANDS.size && hasOthers) BandSelection.AUTO
            else BandSelection(isAuto = false, ids = known)
        }

        /** Nomor band LTE dari EARFCN downlink (hanya band yang dipakai di Indonesia). */
        fun bandIdFromEarfcn(earfcn: Int): String = when (earfcn) {
            in 0..599 -> "B1"
            in 1200..1949 -> "B3"
            in 2400..2649 -> "B5"
            in 3450..3799 -> "B8"
            in 38650..39649 -> "B40"
            else -> ""
        }
    }
}

/**
 * Band yang diizinkan modem. isAuto = true -> modem bebas memilih (semua band tunggal tampil menyala).
 */
data class BandSelection(
    val isAuto: Boolean,
    val ids: Set<String>
) {
    /** Teks ringkas untuk kartu: "Auto (Semua Band)" atau "B40 2300 MHz + B3 1800 MHz". */
    val label: String
        get() = when {
            isAuto -> BandOption.AUTO.name
            ids.isEmpty() -> "Band lain"
            else -> BandOption.SINGLE_BANDS.filter { it.id in ids }.joinToString(" + ") { it.name }
        }

    /** Teks sangat ringkas: "AUTO" atau "B40+B3". */
    val shortLabel: String
        get() = when {
            isAuto -> "AUTO"
            ids.isEmpty() -> "-"
            else -> BandOption.SINGLE_BANDS.filter { it.id in ids }.joinToString("+") { it.id }
        }

    /** Mask yang dikirim ke modem untuk pilihan ini. */
    val maskHex: String
        get() = if (isAuto) BandOption.AUTO_MASK_HEX else BandOption.maskFor(ids)

    /** Band tunggal yang boleh dipakai modem (Auto = semuanya). */
    val allowedIds: Set<String>
        get() = if (isAuto) BandOption.SINGLE_BANDS.map { it.id }.toSet() else ids

    /** Saklar satu band. Dari Auto, semua band dianggap menyala lalu band yang ditekan dimatikan. */
    fun toggled(id: String): BandSelection {
        val next = if (id in allowedIds) allowedIds - id else allowedIds + id
        return BandSelection(isAuto = false, ids = next)
    }

    companion object {
        val AUTO = BandSelection(isAuto = true, ids = BandOption.SINGLE_BANDS.map { it.id }.toSet())
    }
}

sealed class IpChangeProgress {
    object Idle : IpChangeProgress()
    object Disconnecting : IpChangeProgress()
    object RequestingNewIp : IpChangeProgress()
    data class Success(val oldIp: String, val newIp: String) : IpChangeProgress()
    data class Failed(val reason: String) : IpChangeProgress()
}
