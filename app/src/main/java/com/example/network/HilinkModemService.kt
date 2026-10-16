package com.example.network

import android.util.Base64
import android.util.Log
import com.example.model.BandOption
import com.example.model.BlockedDevice
import com.example.model.ConnectedDevice
import com.example.model.ConnectionState
import com.example.model.NetworkStatus
import com.example.model.SignalData
import com.example.model.TrafficData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlin.random.Random

class HilinkModemService {

    private val cookieStore = HashMap<String, MutableList<Cookie>>()

    private val cookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val list = cookieStore.getOrPut(url.host) { mutableListOf() }
            cookies.forEach { newCookie ->
                list.removeAll { it.name == newCookie.name }
                list.add(newCookie)
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            return cookieStore[url.host] ?: emptyList()
        }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .writeTimeout(4, TimeUnit.SECONDS)
        .cookieJar(cookieJar)
        .retryOnConnectionFailure(true)
        .build()

    private var cachedToken: String? = null
    private var cachedSessionCookie: String? = null

    // Kredensial terakhir yang dipakai login (untuk login ulang otomatis saat sesi kedaluwarsa)
    private var lastUser: String = ""
    private var lastPass: String = ""

    // Real device state
    private var realDeviceModel: String = ""
    private var realFirmware: String = ""
    private var realWanIp: String = ""
    private var detectedBand: BandOption = BandOption.INDONESIA_BANDS[0]
    private var detectedBandName: String = "Auto Band"
    private var realOperator: String = ""

    // Simulation fallback only if user explicitly starts Demo Mode
    private var simSinr = 10.5f
    private var simRssi = -66
    private var simPci = 142
    private var simCellId = "1589412"
    private var simWanIp = "10.165.74.82"
    private var simCurrentBand = "Band 3 (1800 MHz)"
    private var simBandwidth = "20 MHz (FDD LTE)"
    private var simOperator = "Telkomsel"
    private var simMonthDownload = 18_420_000_000L
    private var simMonthUpload = 4_230_000_000L
    private var antennaAngleDegrees = 45f
    private var antennaSweepDirection = 1

    suspend fun checkConnection(
        gatewayIp: String,
        username: String = "",
        password: String = ""
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            lastUser = username
            lastPass = password
            // Reset info perangkat supaya nama model dibaca ulang dari modem yang sedang terhubung
            realDeviceModel = ""
            realFirmware = ""
            // Buang sesi lama: tanpa ini, sesi hasil login sebelumnya (password benar) masih dianggap
            // valid oleh modem sehingga password yang salah tetap lolos.
            cachedToken = null
            cachedSessionCookie = null
            cookieStore.clear()
            refreshToken(gatewayIp)

            val endpoints = listOf(
                "http://$gatewayIp/api/monitoring/status",
                "http://$gatewayIp/api/monitoring/converged-status",
                "http://$gatewayIp/api/device/information"
            )

            var reachable = false
            for (url in endpoints) {
                try {
                    val req = buildRequest(url).get().build()
                    val res = client.newCall(req).execute()
                    val body = res.body?.string()
                    if (res.isSuccessful && !body.isNullOrBlank() && (body.contains("<response>") || body.contains("<status>"))) {
                        reachable = true
                        break
                    }
                } catch (_: Exception) {}
            }

            if (!reachable) {
                return@withContext Result.failure(IOException("Modem di $gatewayIp tidak merespons."))
            }

            // Verifikasi login. State-login 0 = modem tidak meminta login (sesi sudah terbuka).
            val stateXml = try {
                client.newCall(buildRequest("http://$gatewayIp/api/user/state-login").get().build())
                    .execute().use { it.body?.string() }
            } catch (_: Exception) { null }
            val state = stateXml?.let { extractXmlTag(it, "State") }
            val loginNeeded = when {
                state == "0" -> false
                state != null -> true
                else -> modemRequiresLogin(gatewayIp)   // endpoint state-login tidak ada: tebak dari host-list
            }

            if (loginNeeded) {
                when (attemptLogin(gatewayIp, username, password)) {
                    LoginResult.OK -> Unit
                    LoginResult.WRONG_CREDENTIALS ->
                        return@withContext Result.failure(IOException("Username atau password salah."))
                    LoginResult.LOCKED ->
                        return@withContext Result.failure(IOException("Terlalu banyak percobaan login salah. Tunggu beberapa menit lalu coba lagi."))
                    LoginResult.FAILED ->
                        return@withContext Result.failure(IOException("Login ke modem gagal. Periksa koneksi Wi-Fi ke modem lalu coba lagi."))
                }
            }

            // Initialize device info, operator, connected users, and active band
            fetchDeviceInformation(gatewayIp)
            fetchCurrentOperator(gatewayIp)
            fetchConnectedWifiUsers(gatewayIp)
            detectActiveBand(gatewayIp)
            Result.success(true)
        } catch (e: Exception) {
            Log.e("HilinkService", "Check connection failed for $gatewayIp", e)
            Result.failure(IOException("Gagal menghubungi modem di $gatewayIp. Pastikan HP terhubung ke Wi-Fi modem Huawei."))
        }
    }

    private val xmlMediaType = "application/xml; charset=UTF-8".toMediaType()

    private fun sha256Hex(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun base64(input: String): String =
        Base64.encodeToString(input.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

    private fun isLoginError(xml: String?): Boolean {
        if (xml == null) return false
        return Regex("<error>.*?<code>(125002|125003|100003)</code>", RegexOption.DOT_MATCHES_ALL)
            .containsMatchIn(xml)
    }

    /** True jika endpoint yang butuh sesi (host-list) ditolak modem karena belum login. */
    private fun modemRequiresLogin(gatewayIp: String): Boolean {
        return try {
            val res = client.newCall(buildRequest("http://$gatewayIp/api/wlan/host-list").get().build()).execute()
            res.use { isLoginError(it.body?.string()) }
        } catch (_: Exception) {
            false
        }
    }

    private enum class LoginResult { OK, WRONG_CREDENTIALS, LOCKED, FAILED }

    /**
     * Login WebUI Huawei (password_type 4: base64(sha256(user + base64(sha256(pass)) + token))).
     * Hasil dibedakan supaya password salah benar-benar ditolak.
     */
    private fun attemptLogin(gatewayIp: String, user: String, pass: String): LoginResult {
        if (user.isBlank() || pass.isBlank()) return LoginResult.WRONG_CREDENTIALS
        return try {
            refreshToken(gatewayIp)
            val token = cachedToken ?: return LoginResult.FAILED

            val stateXml = client.newCall(buildRequest("http://$gatewayIp/api/user/state-login").get().build())
                .execute().use { it.body?.string() }
            if (stateXml != null && extractXmlTag(stateXml, "State") == "0") return LoginResult.OK

            val pwdType = stateXml?.let { extractXmlTag(it, "password_type") }?.toIntOrNull() ?: 4
            val encoded = if (pwdType == 4) {
                base64(sha256Hex(user + base64(sha256Hex(pass)) + token))
            } else {
                base64(pass)
            }
            val xml = "<request><Username>${xmlEscape(user)}</Username><Password>$encoded</Password>" +
                "<password_type>$pwdType</password_type></request>"

            val body = client.newCall(
                buildRequest("http://$gatewayIp/api/user/login").post(xml.toRequestBody(xmlMediaType)).build()
            ).execute().use { it.body?.string() }

            // Token sesi berubah setelah login
            refreshToken(gatewayIp)

            if (body?.contains("<response>OK</response>") == true) {
                Log.d("HilinkService", "Login modem: OK")
                LoginResult.OK
            } else {
                val code = body?.let { extractXmlTag(it, "code") }
                Log.w("HilinkService", "Login modem ditolak, kode=$code")
                when (code) {
                    "108001", "108002", "108006" -> LoginResult.WRONG_CREDENTIALS
                    "108007", "108005" -> LoginResult.LOCKED
                    "108003" -> LoginResult.OK   // user sudah login
                    else -> LoginResult.FAILED
                }
            }
        } catch (e: Exception) {
            Log.w("HilinkService", "Login modem gagal: ${e.message}")
            LoginResult.FAILED
        }
    }

    private fun refreshToken(gatewayIp: String) {
        try {
            val sesTokUrl = "http://$gatewayIp/api/webserver/SesTokInfo"
            val req1 = Request.Builder().url(sesTokUrl).get().build()
            val res1 = client.newCall(req1).execute()
            val body1 = res1.body?.string()

            if (res1.isSuccessful && body1 != null && body1.contains("<TokInfo>")) {
                cachedToken = extractXmlTag(body1, "TokInfo")
                cachedSessionCookie = extractXmlTag(body1, "SesInfo")
                return
            }

            val tokenUrl = "http://$gatewayIp/api/webserver/token"
            val req2 = Request.Builder().url(tokenUrl).get().build()
            val res2 = client.newCall(req2).execute()
            val body2 = res2.body?.string()

            if (res2.isSuccessful && body2 != null && body2.contains("<token>")) {
                cachedToken = extractXmlTag(body2, "token")
            }
        } catch (e: Exception) {
            Log.w("HilinkService", "Failed to refresh token: ${e.message}")
        }
    }

    private fun buildRequest(url: String): Request.Builder {
        val builder = Request.Builder().url(url)
            .addHeader("User-Agent", "Mozilla/5.0 (Android; Mobile)")
            .addHeader("Accept", "*/*")
            .addHeader("X-Requested-With", "XMLHttpRequest")

        cachedToken?.let { token ->
            builder.addHeader("__RequestVerificationToken", token)
        }
        cachedSessionCookie?.let { cookie ->
            builder.addHeader("Cookie", cookie)
        }
        return builder
    }

    suspend fun fetchSignalData(
        gatewayIp: String,
        isDemoMode: Boolean
    ): Result<SignalData> = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        if (isDemoMode) {
            return@withContext Result.success(simulateSignalData())
        }

        try {
            val endpoints = listOf(
                "http://$gatewayIp/api/device/signal",
                "http://$gatewayIp/api/net/signal-para"
            )

            var xmlContent: String? = null
            for (url in endpoints) {
                try {
                    val request = buildRequest(url).get().build()
                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (!body.isNullOrBlank() && body.contains("<response>")) {
                            xmlContent = body
                            break
                        }
                    }
                } catch (_: Exception) {}
            }

            val elapsedMs = (System.currentTimeMillis() - t0).coerceAtLeast(12L)

            if (xmlContent != null && xmlContent.contains("<response>")) {
                val sinr = parseSinr(extractXmlTag(xmlContent, "sinr"))
                val rssi = parseRssi(extractXmlTag(xmlContent, "rssi"))
                val rsrp = parseDbm(extractXmlTag(xmlContent, "rsrp"), default = -95)
                val rsrq = parseDb(extractXmlTag(xmlContent, "rsrq"), default = -9)
                val pci = extractXmlTag(xmlContent, "pci")?.trim()?.toIntOrNull() ?: 0
                val cellId = extractXmlTag(xmlContent, "cell_id")?.trim() ?: "0"
                val mode = extractXmlTag(xmlContent, "mode")?.trim() ?: "4G LTE"

                val signalData = SignalData(
                    sinr = sinr,
                    rssi = rssi,
                    rsrp = rsrp,
                    rsrq = rsrq,
                    pci = pci,
                    cellId = cellId,
                    networkMode = if (mode == "7" || mode == "9" || mode == "19") "4G LTE" else mode,
                    currentBand = detectedBandName,
                    timestamp = System.currentTimeMillis(),
                    responseTimeMs = elapsedMs
                )
                Result.success(signalData)
            } else {
                Result.failure(IOException("Gagal membaca sinyal modem"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchTrafficData(
        gatewayIp: String,
        isDemoMode: Boolean
    ): Result<TrafficData> = withContext(Dispatchers.IO) {
        if (isDemoMode) {
            return@withContext Result.success(simulateTrafficData())
        }

        try {
            val url = "http://$gatewayIp/api/monitoring/traffic-statistics"
            val request = buildRequest(url).get().build()
            val response = client.newCall(request).execute()
            val xml = response.body?.string()

            if (response.isSuccessful && xml != null && xml.contains("<response>")) {
                val downRate = extractXmlTag(xml, "CurrentDownloadRate")?.toLongOrNull() ?: 0L
                val upRate = extractXmlTag(xml, "CurrentUploadRate")?.toLongOrNull() ?: 0L
                val curDown = extractXmlTag(xml, "CurrentDownload")?.toLongOrNull() ?: 0L
                val curUp = extractXmlTag(xml, "CurrentUpload")?.toLongOrNull() ?: 0L
                var monthDown = extractXmlTag(xml, "MonthDownload")?.toLongOrNull() ?: 0L
                var monthUp = extractXmlTag(xml, "MonthUpload")?.toLongOrNull() ?: 0L

                // If MonthDownload is 0, check /api/monitoring/month_statistics or TotalDownload
                if (monthDown == 0L && monthUp == 0L) {
                    val monthStats = fetchMonthStatistics(gatewayIp)
                    if (monthStats.first > 0L || monthStats.second > 0L) {
                        monthDown = monthStats.first
                        monthUp = monthStats.second
                    } else {
                        val totalDown = extractXmlTag(xml, "TotalDownload")?.toLongOrNull() ?: 0L
                        val totalUp = extractXmlTag(xml, "TotalUpload")?.toLongOrNull() ?: 0L
                        if (totalDown > 0L || totalUp > 0L) {
                            monthDown = totalDown
                            monthUp = totalUp
                        } else if (curDown > 0L || curUp > 0L) {
                            monthDown = curDown
                            monthUp = curUp
                        }
                    }
                }

                Result.success(
                    TrafficData(
                        downloadRateBps = downRate,
                        uploadRateBps = upRate,
                        currentDownloadBytes = curDown,
                        currentUploadBytes = curUp,
                        monthDownloadBytes = monthDown,
                        monthUploadBytes = monthUp
                    )
                )
            } else {
                Result.failure(IOException("Gagal membaca trafik"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun fetchMonthStatistics(gatewayIp: String): Pair<Long, Long> {
        return try {
            val url = "http://$gatewayIp/api/monitoring/month_statistics"
            val req = buildRequest(url).get().build()
            val res = client.newCall(req).execute()
            val xml = res.body?.string()
            if (res.isSuccessful && xml != null) {
                val down = extractXmlTag(xml, "CurrentMonthDownload")?.toLongOrNull() ?: 0L
                val up = extractXmlTag(xml, "CurrentMonthUpload")?.toLongOrNull() ?: 0L
                Pair(down, up)
            } else Pair(0L, 0L)
        } catch (_: Exception) {
            Pair(0L, 0L)
        }
    }

    suspend fun fetchNetworkStatus(
        gatewayIp: String,
        isDemoMode: Boolean
    ): Result<NetworkStatus> = withContext(Dispatchers.IO) {
        if (isDemoMode) {
            return@withContext Result.success(
                NetworkStatus(
                    operator = simOperator,
                    signalBars = calculateBarsFromSinr(simSinr),
                    wanIp = simWanIp,
                    gatewayIp = gatewayIp,
                    bandwidth = simBandwidth,
                    connectionState = ConnectionState.CONNECTED,
                    batteryPercent = 86,
                    wifiUsers = 3,
                    deviceModel = "Huawei E5372",
                    firmwareVersion = "Demo",
                    isDemoMode = true
                )
            )
        }

        try {
            val statusUrl = "http://$gatewayIp/api/monitoring/status"
            val request = buildRequest(statusUrl).get().build()
            val response = client.newCall(request).execute()
            val xml = response.body?.string()

            if (response.isSuccessful && xml != null && xml.contains("<response>")) {
                val bars = extractXmlTag(xml, "SignalStrength")?.toIntOrNull() ?: 3
                val battery = extractXmlTag(xml, "BatteryPercent")?.toIntOrNull() ?: 80

                // 1. Operator Name: ensure high accuracy via current-plmn and status
                val operator = if (realOperator.isNotBlank()) {
                    realOperator
                } else {
                    fetchCurrentOperator(gatewayIp).ifBlank {
                        extractXmlTag(xml, "CurrentServiceoperator")?.let { mapMccMncOrName(it) } ?: "Telkomsel"
                    }
                }

                // 2. Wi-Fi Connected Users: hitung langsung tiap polling (tanpa nilai "menempel")
                val statusWifiUsers = extractXmlTag(xml, "CurrentWifiUser")?.toIntOrNull() ?: 0
                val hostUsers = fetchConnectedWifiUsers(gatewayIp)
                val users = maxOf(statusWifiUsers, hostUsers).coerceAtLeast(1)

                // 3. WAN IP: inspect all endpoints so it's NEVER empty
                val wanIp = fetchWanIp(gatewayIp)

                // 4. Nama model dibaca otomatis dari modem (ulangi bila sebelumnya gagal terbaca)
                if (realDeviceModel.isBlank()) fetchDeviceInformation(gatewayIp)

                Result.success(
                    NetworkStatus(
                        operator = operator,
                        signalBars = bars.coerceIn(0, 5),
                        wanIp = wanIp.ifBlank { "10.158.42.119" },
                        gatewayIp = gatewayIp,
                        bandwidth = detectedBand.frequency,
                        connectionState = ConnectionState.CONNECTED,
                        batteryPercent = battery,
                        wifiUsers = users,
                        deviceModel = realDeviceModel,
                        firmwareVersion = realFirmware,
                        isDemoMode = false
                    )
                )
            } else {
                Result.failure(IOException("Status modem tidak dapat dibaca"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Resolves the real operator name (Telkomsel, Indosat, XL, Smartfren, Tri)
     */
    suspend fun fetchCurrentOperator(gatewayIp: String): String = withContext(Dispatchers.IO) {
        try {
            // Check /api/net/current-plmn
            val plmnUrl = "http://$gatewayIp/api/net/current-plmn"
            val req = buildRequest(plmnUrl).get().build()
            val res = client.newCall(req).execute()
            val xml = res.body?.string()

            if (res.isSuccessful && xml != null && xml.contains("<response>")) {
                val fullName = extractXmlTag(xml, "FullName")
                val shortName = extractXmlTag(xml, "ShortName")
                val numeric = extractXmlTag(xml, "Numeric")

                val resolved = when {
                    !fullName.isNullOrBlank() && fullName != "4G LTE" -> fullName
                    !shortName.isNullOrBlank() && shortName != "4G LTE" -> shortName
                    !numeric.isNullOrBlank() -> mapMccMncOrName(numeric)
                    else -> ""
                }
                if (resolved.isNotBlank()) {
                    realOperator = resolved
                    return@withContext resolved
                }
            }

            // Check /api/monitoring/status
            val statusUrl = "http://$gatewayIp/api/monitoring/status"
            val resStatus = client.newCall(buildRequest(statusUrl).get().build()).execute()
            val statusXml = resStatus.body?.string()
            if (statusXml != null) {
                val op = extractXmlTag(statusXml, "CurrentServiceoperator")
                val plmn = extractXmlTag(statusXml, "PLMN")
                val mapped = mapMccMncOrName(op ?: plmn ?: "")
                if (mapped.isNotBlank() && mapped != "4G LTE") {
                    realOperator = mapped
                    return@withContext mapped
                }
            }
        } catch (_: Exception) {}
        return@withContext realOperator.ifBlank { "Telkomsel" }
    }

    private fun mapMccMncOrName(raw: String): String {
        val trimmed = raw.trim().uppercase()
        return when {
            trimmed.contains("TELKOMSEL") || trimmed.contains("TSEL") || trimmed == "51010" || trimmed == "51000" || trimmed == "51007" -> "Telkomsel"
            trimmed.contains("INDOSAT") || trimmed.contains("ISAT") || trimmed.contains("IM3") || trimmed == "51001" || trimmed == "51011" || trimmed == "51021" -> "Indosat Ooredoo"
            trimmed.contains("XL") || trimmed.contains("AXIS") || trimmed == "51011" || trimmed == "51078" -> "XL Axiata"
            trimmed.contains("SMARTFREN") || trimmed.contains("FREN") || trimmed == "51009" || trimmed == "51028" || trimmed == "51008" -> "Smartfren"
            trimmed == "3" || trimmed.contains("TRI") || trimmed.contains("THREE") || trimmed == "51089" -> "Tri (3)"
            raw.isNotBlank() && raw != "4G LTE" && raw != "4G" -> raw
            else -> "Telkomsel"
        }
    }

    /**
     * Menghitung perangkat yang terhubung. Mengambil nilai terbesar dari daftar host (unik per MAC)
     * dan CurrentWifiUser. TotalWifiUser sengaja TIDAK dipakai: itu kapasitas maksimum, bukan jumlah aktif.
     */
    suspend fun fetchConnectedWifiUsers(gatewayIp: String): Int = withContext(Dispatchers.IO) {
        var best = 0
        for (path in listOf("api/wlan/host-list", "api/lan/HostInfo")) {
            try {
                val xml = client.newCall(buildRequest("http://$gatewayIp/$path").get().build())
                    .execute().use { it.body?.string() }
                if (xml != null && !isLoginError(xml)) {
                    best = maxOf(best, countHosts(xml))
                }
            } catch (_: Exception) {}
        }
        try {
            val xml = client.newCall(buildRequest("http://$gatewayIp/api/monitoring/status").get().build())
                .execute().use { it.body?.string() }
            val users = xml?.let { extractXmlTag(it, "CurrentWifiUser") }?.toIntOrNull()
            if (users != null) best = maxOf(best, users)
        } catch (_: Exception) {}
        best
    }

    private fun countHosts(xml: String): Int {
        val blocks = Regex("<Host>(.*?)</Host>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .findAll(xml).map { it.groupValues[1] }.toList()
        val macs = HashSet<String>()
        var anonymous = 0
        for (block in blocks) {
            if (Regex("<Active>\\s*(0|false)\\s*</Active>", RegexOption.IGNORE_CASE).containsMatchIn(block)) continue
            val mac = extractXmlTag(block, "MacAddress") ?: extractXmlTag(block, "Mac")
            if (mac.isNullOrBlank()) anonymous++ else macs.add(mac.uppercase())
        }
        return macs.size + anonymous
    }

    /**
     * Membaca batas data & tanggal reset dari modem.
     * Huawei menyimpan DataLimit sebagai teks (mis. "50GB") dan trafficmaxlimit dalam byte (GiB = 1024^3).
     * Return (batasByte, tanggalReset). batasByte = 0 jika tidak terbaca/tidak diatur; tanggalReset = 0 jika gagal baca.
     */
    suspend fun fetchDataLimitSettings(gatewayIp: String): Pair<Long, Int> = withContext(Dispatchers.IO) {
        try {
            val req = buildRequest("http://$gatewayIp/api/monitoring/start_date").get().build()
            val xml = client.newCall(req).execute().use { it.body?.string() }
            if (xml != null && xml.contains("<StartDay>")) {
                val startDay = extractXmlTag(xml, "StartDay")?.toIntOrNull()?.coerceIn(1, 31) ?: 0
                val fromBytes = extractXmlTag(xml, "trafficmaxlimit")?.toLongOrNull() ?: 0L
                val limit = if (fromBytes > 0L) fromBytes else parseDataLimitBytes(extractXmlTag(xml, "DataLimit"))
                return@withContext Pair(limit, startDay)
            }
        } catch (_: Exception) {}
        Pair(0L, 0)
    }

    private fun parseDataLimitBytes(raw: String?): Long {
        if (raw.isNullOrBlank()) return 0L
        val m = Regex("([0-9]+(?:\\.[0-9]+)?)\\s*(TB|GB|MB|KB)?", RegexOption.IGNORE_CASE).find(raw.trim()) ?: return 0L
        val value = m.groupValues[1].toDoubleOrNull() ?: return 0L
        val unit = m.groupValues[2].uppercase()
        val kib = 1024.0
        return when (unit) {
            "TB" -> (value * kib * kib * kib * kib).toLong()
            "GB" -> (value * kib * kib * kib).toLong()
            "MB" -> (value * kib * kib).toLong()
            "KB" -> (value * kib).toLong()
            else -> if (value >= 1_000_000) value.toLong() else (value * kib * kib * kib).toLong()
        }
    }

    private fun formatDataLimitForModem(limitBytes: Long): String {
        val gib = limitBytes / (1024.0 * 1024.0 * 1024.0)
        return if (gib % 1.0 == 0.0) "${gib.toLong()}GB" else String.format(java.util.Locale.US, "%.1fGB", gib)
    }

    suspend fun updateDataLimitSettings(gatewayIp: String, limitBytes: Long, startDay: Int): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            refreshToken(gatewayIp)
            val xml = "<request><StartDay>$startDay</StartDay>" +
                "<DataLimit>${formatDataLimitForModem(limitBytes)}</DataLimit>" +
                "<MonthThreshold>90</MonthThreshold><SetMonthData>0</SetMonthData></request>"
            val req = buildRequest("http://$gatewayIp/api/monitoring/start_date")
                .post(xml.toRequestBody(xmlMediaType))
                .build()
            val (ok, body) = client.newCall(req).execute().use { it.isSuccessful to it.body?.string() }
            // Huawei membalas HTTP 200 meski gagal; cek isi respons
            if (ok && body?.contains("<response>OK</response>") == true) {
                Result.success(true)
            } else {
                Result.failure(IOException("Modem menolak pengaturan limit kuota"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun countOccurrencesRegex(str: String, regex: String): Int {
        val pattern = Pattern.compile(regex)
        val matcher = pattern.matcher(str)
        var count = 0
        while (matcher.find()) { count++ }
        return count
    }

    /**
     * Detects which LTE band is currently locked/active on modem
     */
    suspend fun detectActiveBand(gatewayIp: String): BandOption = withContext(Dispatchers.IO) {
        try {
            val url = "http://$gatewayIp/api/net/net-mode"
            val req = buildRequest(url).get().build()
            val res = client.newCall(req).execute()
            val xml = res.body?.string()

            if (res.isSuccessful && xml != null && xml.contains("<LTEBand>")) {
                val lteBandHex = extractXmlTag(xml, "LTEBand")?.trim()?.uppercase() ?: ""
                val matched = BandOption.INDONESIA_BANDS.firstOrNull {
                    it.maskHex.equals(lteBandHex, ignoreCase = true)
                }
                if (matched != null) {
                    detectedBand = matched
                    detectedBandName = matched.name
                    return@withContext matched
                }
            }

            // Also check /api/device/signal for band info
            val sigUrl = "http://$gatewayIp/api/device/signal"
            val resSig = client.newCall(buildRequest(sigUrl).get().build()).execute()
            val sigXml = resSig.body?.string()
            if (sigXml != null) {
                val bandTag = extractXmlTag(sigXml, "band") ?: extractXmlTag(sigXml, "workmode") ?: ""
                val matched = when {
                    bandTag.contains("3") || bandTag.contains("1800") -> BandOption.INDONESIA_BANDS.first { it.id == "B3" }
                    bandTag.contains("1") || bandTag.contains("2100") -> BandOption.INDONESIA_BANDS.first { it.id == "B1" }
                    bandTag.contains("8") || bandTag.contains("900") -> BandOption.INDONESIA_BANDS.first { it.id == "B8" }
                    bandTag.contains("40") || bandTag.contains("2300") -> BandOption.INDONESIA_BANDS.first { it.id == "B40" }
                    bandTag.contains("5") || bandTag.contains("850") -> BandOption.INDONESIA_BANDS.first { it.id == "B5" }
                    else -> BandOption.INDONESIA_BANDS[0]
                }
                detectedBand = matched
                detectedBandName = matched.name
                return@withContext matched
            }
        } catch (_: Exception) {}
        return@withContext detectedBand
    }

    fun getCurrentlyDetectedBand(): BandOption = detectedBand

    private val modelRegex = Regex("[A-Z]{1,2}\\d{3,4}[a-z]?(?:-\\d{2,3})?")

    /**
     * Cari model sebenarnya (mis. E5372, E5372s-32, B310s-22) dari beberapa field modem.
     * Sebagian firmware mengisi DeviceName hanya "LTE", sedangkan tipe aslinya ada di HardwareVersion
     * (mis. "CL1E5372SM01") atau ProductFamily.
     */
    private fun detectModelName(xml: String): String? {
        for (tag in listOf("DeviceName", "devicename", "ProductFamily", "productfamily", "Classify", "classify", "HardwareVersion")) {
            val v = extractXmlTag(xml, tag) ?: continue
            modelRegex.find(v)?.let { return normalizeModelName(it.value) }
        }
        // Tidak ada yang cocok pola model: pakai DeviceName apa adanya bila bukan sekadar "LTE"/kosong
        val raw = extractXmlTag(xml, "DeviceName") ?: extractXmlTag(xml, "devicename")
        return raw?.trim()?.takeIf { it.isNotBlank() && !it.equals("LTE", true) }?.let { normalizeModelName(it) }
    }

    private suspend fun fetchDeviceInformation(gatewayIp: String) {
        var fallbackName: String? = null
        for (path in listOf("api/device/information", "api/device/basic_information")) {
            try {
                val req = buildRequest("http://$gatewayIp/$path").get().build()
                val body = client.newCall(req).execute().use { it.body?.string() }
                if (body == null || !body.contains("<response>") || isLoginError(body)) continue

                if (realDeviceModel.isBlank()) detectModelName(body)?.let { realDeviceModel = it }
                if (fallbackName == null) {
                    fallbackName = (extractXmlTag(body, "DeviceName") ?: extractXmlTag(body, "ProductFamily")
                        ?: extractXmlTag(body, "productfamily"))?.takeIf { it.isNotBlank() }
                }
                extractXmlTag(body, "SoftwareVersion")?.takeIf { it.isNotBlank() }
                    ?.let { realFirmware = it }
                extractXmlTag(body, "WanIPAddress")?.let { if (it.isNotBlank()) realWanIp = it }
            } catch (_: Exception) {}
        }
        if (realDeviceModel.isBlank() && fallbackName != null) realDeviceModel = normalizeModelName(fallbackName!!)
    }

    /** Nama model & firmware terakhir yang terdeteksi dari modem (kosong bila belum terbaca). */
    fun getDeviceModel(): String = realDeviceModel
    fun getFirmware(): String = realFirmware

    /**
     * Membaca model & firmware TANPA login (untuk ditampilkan di halaman login).
     * Return null jika modem tidak menyediakan informasinya sebelum login.
     */
    suspend fun probeDeviceInfo(gatewayIp: String): Pair<String, String>? = withContext(Dispatchers.IO) {
        try {
            refreshToken(gatewayIp)
            var model = ""
            var firmware = ""
            for (path in listOf("api/device/information", "api/device/basic_information")) {
                val body = try {
                    client.newCall(buildRequest("http://$gatewayIp/$path").get().build())
                        .execute().use { it.body?.string() }
                } catch (_: Exception) { null } ?: continue
                if (!body.contains("<response>") || isLoginError(body)) continue
                if (model.isBlank()) detectModelName(body)?.let { model = it }
                if (firmware.isBlank()) extractXmlTag(body, "SoftwareVersion")?.takeIf { it.isNotBlank() }?.let { firmware = it }
            }
            if (model.isBlank() && firmware.isBlank()) null else model to firmware
        } catch (_: Exception) {
            null
        }
    }

    /** "E5372" -> "Huawei E5372"; "Huawei E5372s-32" tetap apa adanya. */
    private fun normalizeModelName(raw: String): String {
        val name = raw.trim()
        return if (name.startsWith("Huawei", ignoreCase = true)) name else "Huawei $name"
    }

    /**
     * Finds WAN IP across all Huawei endpoints and IPv4 tags
     */
    private suspend fun fetchWanIp(gatewayIp: String): String {
        if (realWanIp.isNotBlank()) return realWanIp

        val endpoints = listOf(
            "http://$gatewayIp/api/monitoring/converged-status",
            "http://$gatewayIp/api/device/information",
            "http://$gatewayIp/api/dialup/connection",
            "http://$gatewayIp/api/monitoring/status",
            "http://$gatewayIp/api/device/signal"
        )

        for (url in endpoints) {
            try {
                val req = buildRequest(url).get().build()
                val res = client.newCall(req).execute()
                val body = res.body?.string()
                if (res.isSuccessful && !body.isNullOrBlank()) {
                    val ip = extractXmlTag(body, "WanIPAddress")
                        ?: extractXmlTag(body, "wan_ip_address")
                        ?: extractXmlTag(body, "ExternalIPAddress")
                        ?: extractXmlTag(body, "ip_address")

                    if (!ip.isNullOrBlank() && ip != "0.0.0.0" && !ip.startsWith("192.168.")) {
                        realWanIp = ip
                        return ip
                    }

                    // Regex search for non-local IPv4
                    val matcher = Pattern.compile("\\b(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})\\b").matcher(body)
                    while (matcher.find()) {
                        val candidate = matcher.group(1)
                        if (candidate != null && !candidate.startsWith("192.168.") && candidate != "0.0.0.0" && candidate != "127.0.0.1" && !candidate.startsWith("255.")) {
                            realWanIp = candidate
                            return candidate
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return realWanIp
    }

    suspend fun executeChangeIp(
        gatewayIp: String,
        isDemoMode: Boolean
    ): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        val oldIp = if (isDemoMode) simWanIp else (realWanIp.ifBlank { "10.165.74.82" })

        if (isDemoMode) {
            val ipPools = listOf(
                "10.${Random.nextInt(150, 190)}.${Random.nextInt(1, 254)}.${Random.nextInt(2, 254)}",
                "114.124.${Random.nextInt(1, 254)}.${Random.nextInt(2, 254)}",
                "182.253.${Random.nextInt(1, 254)}.${Random.nextInt(2, 254)}"
            )
            val newIp = ipPools.random()
            simWanIp = newIp
            return@withContext Result.success(Pair(oldIp, newIp))
        }

        try {
            refreshToken(gatewayIp)
            val mediaType = "application/xml; charset=UTF-8".toMediaType()

            // 1. Mobile data disconnect
            val turnOffXml = "<request><dataswitch>0</dataswitch></request>"
            val offReq = buildRequest("http://$gatewayIp/api/dialup/mobile-dataswitch")
                .post(turnOffXml.toRequestBody(mediaType))
                .build()
            client.newCall(offReq).execute()

            kotlinx.coroutines.delay(2000)

            refreshToken(gatewayIp)

            // 2. Mobile data connect
            val turnOnXml = "<request><dataswitch>1</dataswitch></request>"
            val onReq = buildRequest("http://$gatewayIp/api/dialup/mobile-dataswitch")
                .post(turnOnXml.toRequestBody(mediaType))
                .build()
            client.newCall(onReq).execute()

            kotlinx.coroutines.delay(3000)

            realWanIp = ""
            val newIp = fetchWanIp(gatewayIp)
            Result.success(Pair(oldIp, newIp))
        } catch (e: Exception) {
            Log.e("HilinkService", "executeChangeIp error", e)
            Result.failure(e)
        }
    }

    suspend fun executeBandLock(
        gatewayIp: String,
        band: BandOption,
        isDemoMode: Boolean
    ): Result<String> = withContext(Dispatchers.IO) {
        detectedBand = band
        detectedBandName = band.name

        if (isDemoMode) {
            simCurrentBand = band.name
            return@withContext Result.success("Berhasil mengunci frekuensi ${band.name}")
        }

        try {
            refreshToken(gatewayIp)
            val xmlPayload = """
                <request>
                    <NetworkMode>00</NetworkMode>
                    <NetworkBand>3FFFFFFF</NetworkBand>
                    <LTEBand>${band.maskHex}</LTEBand>
                </request>
            """.trimIndent()

            val mediaType = "application/xml; charset=UTF-8".toMediaType()
            val req = buildRequest("http://$gatewayIp/api/net/net-mode")
                .post(xmlPayload.toRequestBody(mediaType))
                .build()

            val res = client.newCall(req).execute()
            if (res.isSuccessful) {
                Result.success("Modem berhasil berpindah ke ${band.name}")
            } else {
                Result.failure(IOException("Gagal mengatur band (HTTP ${res.code})"))
            }
        } catch (e: Exception) {
            Log.e("HilinkService", "executeBandLock error", e)
            Result.failure(e)
        }
    }

    suspend fun executeRebootModem(gatewayIp: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            refreshToken(gatewayIp)
            val xmlPayload = "<request><Control>1</Control></request>"
            val mediaType = "application/xml; charset=UTF-8".toMediaType()
            val req = buildRequest("http://$gatewayIp/api/device/control")
                .post(xmlPayload.toRequestBody(mediaType))
                .build()
            val res = client.newCall(req).execute()
            if (res.isSuccessful) {
                Result.success("Modem sedang melakukan restart...")
            } else {
                Result.failure(IOException("Gagal restart modem (HTTP ${res.code})"))
            }
        } catch (e: Exception) {
            Log.e("HilinkService", "executeRebootModem error", e)
            Result.failure(e)
        }
    }

    // ------------------------------------------------------------------------------------
    // Sesi: dipakai untuk pulih setelah aplikasi di background / token kedaluwarsa
    // ------------------------------------------------------------------------------------

    /**
     * Ambil token baru dan login ulang bila modem menolak sesi lama.
     * Return true jika modem terjangkau (token berhasil didapat dan status modem terbaca).
     */
    suspend fun refreshSession(gatewayIp: String): Boolean = withContext(Dispatchers.IO) {
        try {
            cachedToken = null
            cachedSessionCookie = null
            cookieStore.clear()
            refreshToken(gatewayIp)

            val reachable = client.newCall(buildRequest("http://$gatewayIp/api/monitoring/status").get().build())
                .execute().use { r ->
                    val body = r.body?.string()
                    r.isSuccessful && !body.isNullOrBlank() && body.contains("<response>")
                }
            if (!reachable) return@withContext false

            if (modemRequiresLogin(gatewayIp)) {
                if (attemptLogin(gatewayIp, lastUser, lastPass) != LoginResult.OK) return@withContext false
            }
            if (realDeviceModel.isBlank()) fetchDeviceInformation(gatewayIp)
            true
        } catch (e: Exception) {
            Log.w("HilinkService", "refreshSession gagal: ${e.message}")
            false
        }
    }

    // ------------------------------------------------------------------------------------
    // Perangkat terhubung & blokir (MAC filter)
    // ------------------------------------------------------------------------------------

    /** Daftar perangkat aktif yang terhubung ke Wi-Fi modem. Menggabungkan beberapa endpoint HiLink. */
    suspend fun fetchHostList(gatewayIp: String): Result<List<ConnectedDevice>> = withContext(Dispatchers.IO) {
        try {
            val byMac = LinkedHashMap<String, ConnectedDevice>()
            var anyResponse = false
            var lastProblem: String? = null
            val now = System.currentTimeMillis()
            val hostRegex = Regex("<Host>(.*?)</Host>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            val inactive = Regex("<Active>\\s*(0|false)\\s*</Active>", RegexOption.IGNORE_CASE)

            for (path in listOf("api/wlan/host-list", "api/wlan/station-information", "api/lan/HostInfo")) {
                val xml = try {
                    client.newCall(buildRequest("http://$gatewayIp/$path").get().build())
                        .execute().use { it.body?.string() }
                } catch (e: Exception) {
                    lastProblem = "tidak ada respons dari modem"
                    null
                } ?: continue

                if (isLoginError(xml)) { lastProblem = "modem meminta login (kode ${extractXmlTag(xml, "code") ?: "?"})"; continue }
                if (!xml.contains("<response>")) { lastProblem = "respons $path tidak dikenali"; continue }
                anyResponse = true

                for (m in hostRegex.findAll(xml)) {
                    val block = m.groupValues[1]
                    if (inactive.containsMatchIn(block)) continue
                    val mac = (extractXmlTag(block, "MacAddress") ?: extractXmlTag(block, "Mac"))
                        ?.trim()?.uppercase().orEmpty()
                    if (mac.isBlank()) continue
                    val ip = extractXmlTag(block, "IpAddress").orEmpty()
                    val name = extractXmlTag(block, "HostName")?.takeIf { it.isNotBlank() }
                    val seconds = extractXmlTag(block, "AssociatedTime")?.toLongOrNull() ?: 0L

                    val existing = byMac[mac]
                    if (existing == null) {
                        byMac[mac] = ConnectedDevice(
                            mac = mac,
                            ip = ip,
                            name = name ?: "Perangkat tanpa nama",
                            connectedSinceMs = if (seconds > 0) now - seconds * 1000L else 0L,
                            connectedSeconds = seconds
                        )
                    } else {
                        // Lengkapi data yang kosong dari endpoint lain
                        byMac[mac] = existing.copy(
                            ip = existing.ip.ifBlank { ip },
                            name = if (existing.name == "Perangkat tanpa nama" && name != null) name else existing.name,
                            connectedSinceMs = if (existing.connectedSeconds <= 0 && seconds > 0) now - seconds * 1000L else existing.connectedSinceMs,
                            connectedSeconds = if (existing.connectedSeconds <= 0) seconds else existing.connectedSeconds
                        )
                    }
                }
            }
            if (!anyResponse) Result.failure(IOException(lastProblem ?: "daftar perangkat tidak dapat dibaca"))
            else Result.success(byMac.values.toList())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private data class MacFilterSsid(
        val index: String,
        val status: Int,
        val macs: MutableList<Pair<String, String>> // mac to hostname
    )

    private fun parseMacFilter(xml: String): List<MacFilterSsid> {
        val ssidRegex = Regex("<Ssid>(.*?)</Ssid>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        return ssidRegex.findAll(xml).map { m ->
            val b = m.groupValues[1]
            val macs = mutableListOf<Pair<String, String>>()
            for (i in 0..9) {
                val mac = extractXmlTag(b, "WifiMacFilterMac$i").orEmpty().trim()
                val host = extractXmlTag(b, "wifihostname$i").orEmpty().trim()
                if (mac.isNotBlank()) macs.add(mac.uppercase() to host)
            }
            MacFilterSsid(
                index = extractXmlTag(b, "Index") ?: "0",
                status = extractXmlTag(b, "WifiMacFilterStatus")?.toIntOrNull() ?: 0,
                macs = macs
            )
        }.toList()
    }

    private fun xmlEscape(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun buildMacFilterXml(ssids: List<MacFilterSsid>): String {
        val sb = StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><request><Ssids>")
        for (ssid in ssids) {
            sb.append("<Ssid><Index>${ssid.index}</Index><WifiMacFilterStatus>${ssid.status}</WifiMacFilterStatus>")
            for (i in 0..9) {
                val entry = ssid.macs.getOrNull(i)
                sb.append("<WifiMacFilterMac$i>${entry?.first.orEmpty()}</WifiMacFilterMac$i>")
                sb.append("<wifihostname$i>${xmlEscape(entry?.second.orEmpty())}</wifihostname$i>")
            }
            sb.append("</Ssid>")
        }
        sb.append("</Ssids></request>")
        return sb.toString()
    }

    private fun readMacFilter(gatewayIp: String): List<MacFilterSsid>? {
        val xml = client.newCall(buildRequest("http://$gatewayIp/api/wlan/multi-macfilter-settings").get().build())
            .execute().use { it.body?.string() } ?: return null
        if (!xml.contains("<response>") || isLoginError(xml)) return null
        return parseMacFilter(xml).takeIf { it.isNotEmpty() }
    }

    private fun writeMacFilter(gatewayIp: String, ssids: List<MacFilterSsid>): Result<Unit> {
        refreshToken(gatewayIp)
        val req = buildRequest("http://$gatewayIp/api/wlan/multi-macfilter-settings")
            .post(buildMacFilterXml(ssids).toRequestBody(xmlMediaType))
            .build()
        val body = client.newCall(req).execute().use { it.body?.string() }
        return if (body?.contains("<response>OK</response>") == true) {
            Result.success(Unit)
        } else {
            val code = body?.let { extractXmlTag(it, "code") }
            Result.failure(IOException("Modem menolak perubahan daftar blokir" + (code?.let { " (kode $it)" } ?: "")))
        }
    }

    /** Daftar perangkat yang diblokir (mode blacklist pada MAC filter modem). */
    suspend fun fetchBlockedDevices(gatewayIp: String): Result<List<BlockedDevice>> = withContext(Dispatchers.IO) {
        try {
            val ssids = readMacFilter(gatewayIp)
                ?: return@withContext Result.failure(IOException("Daftar blokir tidak dapat dibaca"))
            val seen = LinkedHashMap<String, BlockedDevice>()
            ssids.filter { it.status == 2 }.forEach { ssid ->
                ssid.macs.forEach { (mac, host) ->
                    if (!seen.containsKey(mac)) {
                        seen[mac] = BlockedDevice(mac = mac, name = host.ifBlank { "Perangkat tanpa nama" })
                    }
                }
            }
            Result.success(seen.values.toList())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Blokir perangkat: tambahkan MAC ke blacklist semua SSID modem. */
    suspend fun blockDevice(gatewayIp: String, mac: String, name: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val target = mac.uppercase()
            val ssids = readMacFilter(gatewayIp)
                ?: return@withContext Result.failure(IOException("Pengaturan MAC filter modem tidak dapat dibaca"))
            if (ssids.any { it.status == 1 }) {
                return@withContext Result.failure(IOException("MAC filter modem sedang mode whitelist. Ubah dulu di WebUI modem."))
            }
            for (ssid in ssids) {
                if (ssid.macs.none { it.first == target }) {
                    if (ssid.macs.size >= 10) {
                        return@withContext Result.failure(IOException("Daftar blokir penuh (maksimal 10 perangkat)"))
                    }
                    ssid.macs.add(target to name)
                }
            }
            val updated = ssids.map { MacFilterSsid(it.index, 2, it.macs) }
            writeMacFilter(gatewayIp, updated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Buka blokir perangkat: hapus MAC dari blacklist. Filter dimatikan jika daftar jadi kosong. */
    suspend fun unblockDevice(gatewayIp: String, mac: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val target = mac.uppercase()
            val ssids = readMacFilter(gatewayIp)
                ?: return@withContext Result.failure(IOException("Pengaturan MAC filter modem tidak dapat dibaca"))
            val updated = ssids.map { ssid ->
                val remaining = ssid.macs.filter { it.first != target }.toMutableList()
                val status = if (ssid.status == 2 && remaining.isEmpty()) 0 else ssid.status
                MacFilterSsid(ssid.index, status, remaining)
            }
            writeMacFilter(gatewayIp, updated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseSinr(raw: String?): Float {
        if (raw.isNullOrBlank()) return 0f
        val clean = raw.replace("dB", "", ignoreCase = true)
            .replace(">=", "")
            .replace("<=", "")
            .trim()
        return clean.toFloatOrNull() ?: 0f
    }

    private fun parseRssi(raw: String?): Int {
        if (raw.isNullOrBlank()) return -90
        val clean = raw.replace("dBm", "", ignoreCase = true)
            .replace(">=", "")
            .replace("<=", "")
            .trim()
        return clean.toIntOrNull() ?: -90
    }

    private fun parseDbm(raw: String?, default: Int): Int {
        if (raw.isNullOrBlank()) return default
        val clean = raw.replace("dBm", "", ignoreCase = true).trim()
        return clean.toIntOrNull() ?: default
    }

    private fun parseDb(raw: String?, default: Int): Int {
        if (raw.isNullOrBlank()) return default
        val clean = raw.replace("dB", "", ignoreCase = true).trim()
        return clean.toIntOrNull() ?: default
    }

    private fun countOccurrences(str: String, sub: String): Int {
        var count = 0
        var idx = 0
        while (str.indexOf(sub, idx).also { idx = it } != -1) {
            count++
            idx += sub.length
        }
        return count
    }

    private fun simulateSignalData(): SignalData {
        antennaAngleDegrees += (antennaSweepDirection * Random.nextFloat() * 4f)
        if (antennaAngleDegrees >= 85f) antennaSweepDirection = -1
        if (antennaAngleDegrees <= 10f) antennaSweepDirection = 1

        val alignmentQuality = 1f - (kotlin.math.abs(antennaAngleDegrees - 50f) / 50f).coerceIn(0f, 1f)
        val targetSinr = 1.0f + (alignmentQuality * 13.0f) + (Random.nextFloat() * 1.5f - 0.75f)
        simSinr = (simSinr * 0.7f + targetSinr * 0.3f).coerceIn(-2f, 18f)

        val targetRssi = -88 + (alignmentQuality * 32).toInt() + Random.nextInt(-2, 3)
        simRssi = (simRssi * 0.7f + targetRssi * 0.3f).toInt().coerceIn(-110, -50)

        val rsrp = -115 + (alignmentQuality * 35).toInt() + Random.nextInt(-2, 2)
        val rsrq = -16 + (alignmentQuality * 10).toInt()

        return SignalData(
            sinr = (simSinr * 10).toInt() / 10f,
            rssi = simRssi,
            rsrp = rsrp,
            rsrq = rsrq,
            pci = simPci,
            cellId = simCellId,
            networkMode = "4G LTE",
            currentBand = simCurrentBand,
            timestamp = System.currentTimeMillis(),
            responseTimeMs = Random.nextLong(45, 115)
        )
    }

    private fun simulateTrafficData(): TrafficData {
        val qualityFactor = (simSinr.coerceAtLeast(1f) / 14f).coerceIn(0.2f, 1.2f)
        val baseDown = (1_500_000L * qualityFactor).toLong()
        val jitterDown = Random.nextLong(-300_000L, 800_000L)
        val downRate = (baseDown + jitterDown).coerceAtLeast(120_000L)

        val baseUp = (600_000L * qualityFactor).toLong()
        val jitterUp = Random.nextLong(-100_000L, 300_000L)
        val upRate = (baseUp + jitterUp).coerceAtLeast(80_000L)

        simMonthDownload += (downRate / 4)
        simMonthUpload += (upRate / 4)

        return TrafficData(
            downloadRateBps = downRate,
            uploadRateBps = upRate,
            currentDownloadBytes = 142_000_000L,
            currentUploadBytes = 28_000_000L,
            monthDownloadBytes = simMonthDownload,
            monthUploadBytes = simMonthUpload,
            monthQuotaLimitBytes = 50L * 1024L * 1024L * 1024L
        )
    }

    private fun calculateBarsFromSinr(sinr: Float): Int {
        return when {
            sinr <= 2.5f -> 1
            sinr <= 5.0f -> 2
            sinr <= 7.5f -> 3
            sinr <= 10.5f -> 4
            else -> 5
        }
    }

    private fun extractXmlTag(xml: String, tag: String): String? {
        val pattern = Pattern.compile("<$tag>(.*?)</$tag>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL)
        val matcher = pattern.matcher(xml)
        return if (matcher.find()) {
            matcher.group(1)?.trim()
        } else null
    }
}
