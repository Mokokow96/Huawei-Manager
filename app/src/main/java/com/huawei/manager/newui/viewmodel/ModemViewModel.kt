package com.huawei.manager.newui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.huawei.manager.newui.model.BandOption
import com.huawei.manager.newui.model.BlockedDevice
import com.huawei.manager.newui.model.ConnectedDevice
import com.huawei.manager.newui.model.LinkState
import com.huawei.manager.newui.model.ConnectionState
import com.huawei.manager.newui.model.IpChangeProgress
import com.huawei.manager.newui.model.NetworkStatus
import com.huawei.manager.newui.model.SignalData
import com.huawei.manager.newui.model.TemperatureData
import com.huawei.manager.newui.model.TrafficData
import com.huawei.manager.newui.network.HilinkModemService
import com.huawei.manager.newui.network.WifiNetworkHelper
import com.huawei.manager.newui.util.AntennaBeeper
import com.huawei.manager.newui.util.PreferencesManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class AppScreen {
    LOGIN,
    DASHBOARD,
    SIGNAL_HUNTER
}

class ModemViewModel(application: Application) : AndroidViewModel(application) {

    private companion object {
        // Modem Huawei menghitung GB sebagai GiB (1024^3), sama dengan formatBytes() di Dashboard
        const val BYTES_PER_GB = 1024f * 1024f * 1024f

        // Auto-retry saat MiFi offline: 10 percobaan x 30 detik = 5 menit
        const val MAX_RETRY_ATTEMPTS = 10
        const val RETRY_INTERVAL_SECONDS = 30
        const val FAILED_ROUNDS_BEFORE_OFFLINE = 3
        const val DEVICE_REFRESH_EVERY_ROUNDS = 4
    }

    private val modemService = HilinkModemService()
    private val wifiHelper = WifiNetworkHelper(application.applicationContext)
    private val beeper = AntennaBeeper(application.applicationContext)
    private val prefs = PreferencesManager(application.applicationContext)

    // Navigation & Screen State
    private val _currentScreen = MutableStateFlow(AppScreen.LOGIN)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    // Config & Credentials
    private val _gatewayIp = MutableStateFlow(prefs.gatewayIp)
    val gatewayIp: StateFlow<String> = _gatewayIp.asStateFlow()

    private val _savedUsername = MutableStateFlow(prefs.username)
    val savedUsername: StateFlow<String> = _savedUsername.asStateFlow()

    private val _savedPassword = MutableStateFlow(prefs.password)
    val savedPassword: StateFlow<String> = _savedPassword.asStateFlow()

    private val _isDemoMode = MutableStateFlow(false)
    val isDemoMode: StateFlow<Boolean> = _isDemoMode.asStateFlow()

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    private val _connectionErrorMessage = MutableStateFlow<String?>(null)
    val connectionErrorMessage: StateFlow<String?> = _connectionErrorMessage.asStateFlow()

    // Real-time Data
    private val _networkStatus = MutableStateFlow(
        NetworkStatus(
            connectionState = ConnectionState.DISCONNECTED,
            isDemoMode = false
        )
    )
    val networkStatus: StateFlow<NetworkStatus> = _networkStatus.asStateFlow()

    private val _signalData = MutableStateFlow(SignalData())
    val signalData: StateFlow<SignalData> = _signalData.asStateFlow()

    private val _temperatureData = MutableStateFlow(TemperatureData())
    val temperatureData: StateFlow<TemperatureData> = _temperatureData.asStateFlow()
    private var lastTempFetchMs = 0L

    private val _trafficData = MutableStateFlow(TrafficData())
    val trafficData: StateFlow<TrafficData> = _trafficData.asStateFlow()

    // Band Selector & IP Change State
    private val _selectedBand = MutableStateFlow(BandOption.INDONESIA_BANDS[0])
    val selectedBand: StateFlow<BandOption> = _selectedBand.asStateFlow()

    private val _showBandDialog = MutableStateFlow(false)
    val showBandDialog: StateFlow<Boolean> = _showBandDialog.asStateFlow()

    private val _showIpChangeDialog = MutableStateFlow(false)
    val showIpChangeDialog: StateFlow<Boolean> = _showIpChangeDialog.asStateFlow()

    private val _ipChangeProgress = MutableStateFlow<IpChangeProgress>(IpChangeProgress.Idle)
    val ipChangeProgress: StateFlow<IpChangeProgress> = _ipChangeProgress.asStateFlow()

    // Data Quota Limit state
    private val _showDataLimitDialog = MutableStateFlow(false)
    val showDataLimitDialog: StateFlow<Boolean> = _showDataLimitDialog.asStateFlow()

    private val _quotaLimitGb = MutableStateFlow(prefs.quotaLimitGb)
    val quotaLimitGb: StateFlow<Float> = _quotaLimitGb.asStateFlow()

    private val _resetDayOfMonth = MutableStateFlow(prefs.resetDayOfMonth)
    val resetDayOfMonth: StateFlow<Int> = _resetDayOfMonth.asStateFlow()

    // Signal Hunter Specifics
    private val _audioBeepEnabled = MutableStateFlow(false)
    val audioBeepEnabled: StateFlow<Boolean> = _audioBeepEnabled.asStateFlow()

    private val _sessionMaxSinr = MutableStateFlow(0f)
    val sessionMaxSinr: StateFlow<Float> = _sessionMaxSinr.asStateFlow()

    private val _sinrHistory = MutableStateFlow<List<Float>>(emptyList())
    val sinrHistory: StateFlow<List<Float>> = _sinrHistory.asStateFlow()

    // Status koneksi HP -> MiFi (Terhubung / Offline) + auto-retry
    private val _linkState = MutableStateFlow(LinkState.ONLINE)
    val linkState: StateFlow<LinkState> = _linkState.asStateFlow()

    private val _offlineAttempt = MutableStateFlow(0)
    val offlineAttempt: StateFlow<Int> = _offlineAttempt.asStateFlow()

    private val _secondsToNextRetry = MutableStateFlow(0)
    val secondsToNextRetry: StateFlow<Int> = _secondsToNextRetry.asStateFlow()

    // Model & firmware modem untuk halaman login (dibaca dari modem, atau cache login terakhir)
    private val _modemPreview = MutableStateFlow(Pair(prefs.lastModel, prefs.lastFirmware))
    val modemPreview: StateFlow<Pair<String, String>> = _modemPreview.asStateFlow()

    // Alasan daftar perangkat gagal dibaca (null = tidak ada masalah)
    private val _deviceListError = MutableStateFlow<String?>(null)
    val deviceListError: StateFlow<String?> = _deviceListError.asStateFlow()

    // Perangkat terhubung & diblokir
    private val _connectedDevices = MutableStateFlow<List<ConnectedDevice>>(emptyList())
    val connectedDevices: StateFlow<List<ConnectedDevice>> = _connectedDevices.asStateFlow()

    private val _blockedDevices = MutableStateFlow<List<BlockedDevice>>(emptyList())
    val blockedDevices: StateFlow<List<BlockedDevice>> = _blockedDevices.asStateFlow()

    private val _deviceBusyMac = MutableStateFlow<String?>(null)
    val deviceBusyMac: StateFlow<String?> = _deviceBusyMac.asStateFlow()

    private val _deviceMessage = MutableStateFlow<String?>(null)
    val deviceMessage: StateFlow<String?> = _deviceMessage.asStateFlow()

    // Data simulasi untuk Mode Demo
    private val demoConnected = mutableListOf(
        ConnectedDevice("AA:BB:CC:11:22:33", "192.168.8.101", "Galaxy-S23", 0L, 5_400L),
        ConnectedDevice("AA:BB:CC:44:55:66", "192.168.8.102", "Laptop-Kantor", 0L, 1_260L),
        ConnectedDevice("AA:BB:CC:77:88:99", "192.168.8.103", "Redmi-Note-12", 0L, 320L)
    )
    private val demoBlocked = mutableListOf<BlockedDevice>()

    private var pollingJob: Job? = null
    private var reconnectJob: Job? = null
    private var consecutiveErrors = 0
    private var pollingFast = false


    init {
        // Auto-login if user previously connected successfully
        if (prefs.hasLoggedInBefore) {
            connectToModem(
                ip = prefs.gatewayIp,
                user = prefs.username,
                pass = prefs.password,
                isDemo = false
            )
        }
    }

    /** Coba baca model & firmware modem langsung dari modem (tanpa login) untuk halaman login. */
    fun refreshModemPreview() {
        if (_isDemoMode.value) return
        val ip = _gatewayIp.value
        viewModelScope.launch {
            wifiHelper.bindToWifiNetwork()
            modemService.probeDeviceInfo(ip)?.let { (model, firmware) ->
                val newModel = model.ifBlank { _modemPreview.value.first }
                val newFw = firmware.ifBlank { _modemPreview.value.second }
                _modemPreview.value = Pair(newModel, newFw)
                if (newModel.isNotBlank()) prefs.lastModel = newModel
                if (newFw.isNotBlank()) prefs.lastFirmware = newFw
            }
        }
    }

    fun updateGatewayIp(ip: String) {
        _gatewayIp.value = ip
        prefs.gatewayIp = ip
        _connectionErrorMessage.value = null
    }

    fun connectToModem(
        ip: String = _gatewayIp.value,
        user: String = _savedUsername.value,
        pass: String = _savedPassword.value,
        isDemo: Boolean = false
    ) {
        _isConnecting.value = true
        _isDemoMode.value = isDemo
        _connectionErrorMessage.value = null

        _gatewayIp.value = ip
        _savedUsername.value = user
        _savedPassword.value = pass

        viewModelScope.launch {
            if (isDemo) {
                delay(500)
                _isConnecting.value = false
                _linkState.value = LinkState.ONLINE
                _networkStatus.value = _networkStatus.value.copy(
                    gatewayIp = ip,
                    connectionState = ConnectionState.CONNECTED,
                    deviceModel = "Huawei E5372",
                    firmwareVersion = "Demo",
                    isDemoMode = true
                )
                _currentScreen.value = AppScreen.DASHBOARD
                startDataPolling()
                return@launch
            }

            // Real connection: Bind socket routing to Wi-Fi
            wifiHelper.bindToWifiNetwork()

            val checkResult = modemService.checkConnection(ip, user, pass)
            _isConnecting.value = false

            checkResult.onSuccess {
                consecutiveErrors = 0
                _linkState.value = LinkState.ONLINE
                // Save credentials for seamless auto-login next time
                prefs.saveCredentials(ip, user, pass)
                // Simpan model & firmware asli hasil deteksi modem
                modemService.getDeviceModel().takeIf { it.isNotBlank() }?.let { prefs.lastModel = it }
                modemService.getFirmware().takeIf { it.isNotBlank() }?.let { prefs.lastFirmware = it }
                _modemPreview.value = Pair(prefs.lastModel, prefs.lastFirmware)

                // Read detected active band from modem
                val activeBand = modemService.getCurrentlyDetectedBand()
                _selectedBand.value = activeBand

                _networkStatus.value = _networkStatus.value.copy(
                    gatewayIp = ip,
                    connectionState = ConnectionState.CONNECTED,
                    isDemoMode = false
                )
                _currentScreen.value = AppScreen.DASHBOARD

                fetchDataOnce()
                refreshDevices()
                startDataPolling()
            }.onFailure { error ->
                _networkStatus.value = _networkStatus.value.copy(
                    connectionState = ConnectionState.ERROR
                )
                _connectionErrorMessage.value = error.message
                    ?: "Gagal terhubung ke modem di $ip. Pastikan HP terhubung ke Wi-Fi modem Huawei."
            }
        }
    }

    private suspend fun fetchDataOnce() {
        val ip = _gatewayIp.value
        val demo = _isDemoMode.value

        modemService.fetchSignalData(ip, demo).getOrNull()?.let { _signalData.value = it }
        modemService.fetchTrafficData(ip, demo).getOrNull()?.let { _trafficData.value = it }
        modemService.fetchNetworkStatus(ip, demo).getOrNull()?.let { _networkStatus.value = it }
        val activeBand = modemService.getCurrentlyDetectedBand()
        _selectedBand.value = activeBand

        if (!demo) {
            syncDataLimitFromModem(ip)
        }
    }

    /** Baca batas kuota & tanggal reset yang tersimpan di modem lalu tampilkan di UI. */
    private suspend fun syncDataLimitFromModem(ip: String) {
        val (limitBytes, startDay) = modemService.fetchDataLimitSettings(ip)
        if (limitBytes > 0) {
            val gb = limitBytes / BYTES_PER_GB
            _quotaLimitGb.value = gb
            prefs.quotaLimitGb = gb
            _trafficData.value = _trafficData.value.copy(monthQuotaLimitBytes = limitBytes)
        }
        if (startDay in 1..31) {
            _resetDayOfMonth.value = startDay
            prefs.resetDayOfMonth = startDay
            _trafficData.value = _trafficData.value.copy(resetDayOfMonth = startDay)
        }
    }

    fun disconnect() {
        pollingJob?.cancel()
        reconnectJob?.cancel()
        _linkState.value = LinkState.ONLINE
        _connectedDevices.value = emptyList()
        _blockedDevices.value = emptyList()
        prefs.clearAutoLogin()
        _currentScreen.value = AppScreen.LOGIN
        _networkStatus.value = _networkStatus.value.copy(
            connectionState = ConnectionState.DISCONNECTED
        )
    }

    fun rebootModem(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val result = modemService.executeRebootModem(_gatewayIp.value)
            result.onSuccess { msg ->
                onResult(msg)
                delay(2000)
                disconnect()
            }.onFailure { err ->
                onResult(err.message ?: "Gagal restart modem")
            }
        }
    }

    fun openSignalHunter() {
        _currentScreen.value = AppScreen.SIGNAL_HUNTER
        lastTempFetchMs = 0L
        _sessionMaxSinr.value = _signalData.value.sinr
        pollingFast = true
        // Saat offline, loop reconnect yang mengatur polling; jangan dimulai ganda
        if (_linkState.value == LinkState.ONLINE) startDataPolling(fastMode = true)
    }

    fun closeSignalHunter() {
        _currentScreen.value = AppScreen.DASHBOARD
        pollingFast = false
        if (_linkState.value == LinkState.ONLINE) startDataPolling(fastMode = false)
    }

    fun toggleAudioBeep(enabled: Boolean) {
        _audioBeepEnabled.value = enabled
    }

    fun resetSessionPeak() {
        _sessionMaxSinr.value = _signalData.value.sinr
        _sinrHistory.value = emptyList()
    }

    fun openBandDialog() {
        _showBandDialog.value = true
    }

    fun closeBandDialog() {
        _showBandDialog.value = false
    }

    fun selectBand(band: BandOption) {
        _selectedBand.value = band
        _showBandDialog.value = false

        viewModelScope.launch {
            modemService.executeBandLock(_gatewayIp.value, band, _isDemoMode.value)
            delay(1000)
            fetchDataOnce()
        }
    }

    fun openIpChangeDialog() {
        _showIpChangeDialog.value = true
        executeChangeIp()
    }

    fun closeIpChangeDialog() {
        _showIpChangeDialog.value = false
        _ipChangeProgress.value = IpChangeProgress.Idle
    }

    fun executeChangeIp() {
        viewModelScope.launch {
            _ipChangeProgress.value = IpChangeProgress.Disconnecting
            delay(1500)
            _ipChangeProgress.value = IpChangeProgress.RequestingNewIp
            val result = modemService.executeChangeIp(_gatewayIp.value, _isDemoMode.value)
            delay(1500)
            result.onSuccess { (oldIp, newIp) ->
                _ipChangeProgress.value = IpChangeProgress.Success(oldIp, newIp)
                _networkStatus.value = _networkStatus.value.copy(wanIp = newIp)
            }.onFailure { err ->
                _ipChangeProgress.value = IpChangeProgress.Failed(err.message ?: "Gagal memperbarui IP")
            }
        }
    }

    fun openDataLimitDialog() {
        _showDataLimitDialog.value = true
    }

    fun closeDataLimitDialog() {
        _showDataLimitDialog.value = false
    }

    fun updateDataLimit(limitGb: Float, resetDay: Int) {
        _quotaLimitGb.value = limitGb
        _resetDayOfMonth.value = resetDay
        prefs.quotaLimitGb = limitGb
        prefs.resetDayOfMonth = resetDay
        _showDataLimitDialog.value = false

        val limitBytes = (limitGb * BYTES_PER_GB).toLong()
        _trafficData.value = _trafficData.value.copy(
            monthQuotaLimitBytes = limitBytes,
            resetDayOfMonth = resetDay
        )

        if (_isDemoMode.value) return
        viewModelScope.launch {
            modemService.updateDataLimitSettings(_gatewayIp.value, limitBytes, resetDay)
            // Selalu sinkron ulang dari modem supaya UI menampilkan nilai yang benar-benar tersimpan
            // (jika modem menolak, tampilan kembali ke pengaturan asli modem).
            delay(500)
            syncDataLimitFromModem(_gatewayIp.value)
        }
    }

    private fun startDataPolling(fastMode: Boolean = false) {
        pollingFast = fastMode
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            var failedRounds = 0
            var round = 0
            while (isActive) {
                val ip = _gatewayIp.value
                val demo = _isDemoMode.value
                var roundOk = false

                // 1. Fetch signal parameters
                val signalResult = modemService.fetchSignalData(ip, demo)
                signalResult.onSuccess { sig ->
                    roundOk = true
                    consecutiveErrors = 0
                    _signalData.value = sig

                    if (sig.sinr > _sessionMaxSinr.value) {
                        _sessionMaxSinr.value = sig.sinr
                        beeper.vibratePeak()
                    }

                    val currentList = _sinrHistory.value.toMutableList()
                    if (currentList.size >= 40) {
                        currentList.removeAt(0)
                    }
                    currentList.add(sig.sinr)
                    _sinrHistory.value = currentList

                    if (_audioBeepEnabled.value && _currentScreen.value == AppScreen.SIGNAL_HUNTER) {
                        beeper.playBeepForSinr(sig.sinr)
                    }
                }

                // 1b. Suhu chipset: hanya di halaman Pencari Sinyal, tiap 3 detik, gagal pun tidak memengaruhi koneksi
                if (_currentScreen.value == AppScreen.SIGNAL_HUNTER) {
                    val nowMs = System.currentTimeMillis()
                    if (nowMs - lastTempFetchMs >= 3000L) {
                        lastTempFetchMs = nowMs
                        _temperatureData.value = modemService.fetchTemperature(ip, demo)
                    }
                }

                // 2. Fetch traffic & network status
                val trafficResult = modemService.fetchTrafficData(ip, demo)
                trafficResult.onSuccess { tf ->
                    roundOk = true
                    _trafficData.value = tf.copy(
                        monthQuotaLimitBytes = (_quotaLimitGb.value * BYTES_PER_GB).toLong(),
                        resetDayOfMonth = _resetDayOfMonth.value
                    )
                }

                val netResult = modemService.fetchNetworkStatus(ip, demo)
                netResult.onSuccess { st ->
                    roundOk = true
                    _networkStatus.value = st
                }

                if (demo || roundOk) {
                    failedRounds = 0
                    if (_linkState.value != LinkState.ONLINE) _linkState.value = LinkState.ONLINE
                } else {
                    failedRounds++
                    // Penyebab paling umum "angka berhenti bergerak": sesi/token modem kedaluwarsa.
                    // Coba pulihkan sesi dulu sebelum menyatakan modem offline.
                    if (modemService.refreshSession(ip)) {
                        wifiHelper.bindToWifiNetwork()
                        failedRounds = 0
                    } else if (failedRounds >= FAILED_ROUNDS_BEFORE_OFFLINE) {
                        goOffline()
                        return@launch
                    }
                }

                // 3. Perangkat terhubung/diblokir: lebih jarang daripada sinyal
                round++
                if (round % DEVICE_REFRESH_EVERY_ROUNDS == 1 && _currentScreen.value == AppScreen.DASHBOARD) {
                    refreshDevices()
                }

                // 500ms in signal hunter for ultra-fast antenna adjustments, 1200ms in dashboard
                val sleepTime = if (fastMode) 500L else 1200L
                delay(sleepTime)
            }
        }
    }

    // ------------------------------------------------------------------------------------
    // Offline + reconnect otomatis (10x percobaan, tiap 30 detik = 5 menit)
    // ------------------------------------------------------------------------------------

    private fun goOffline() {
        pollingJob?.cancel()
        _linkState.value = LinkState.OFFLINE_RETRYING
        _networkStatus.value = _networkStatus.value.copy(connectionState = ConnectionState.RECONNECTING)
        startReconnectLoop()
    }

    private fun startReconnectLoop() {
        reconnectJob?.cancel()
        reconnectJob = viewModelScope.launch {
            _linkState.value = LinkState.OFFLINE_RETRYING
            for (attempt in 1..MAX_RETRY_ATTEMPTS) {
                _offlineAttempt.value = attempt
                for (sec in RETRY_INTERVAL_SECONDS downTo 1) {
                    _secondsToNextRetry.value = sec
                    delay(1000)
                }
                _secondsToNextRetry.value = 0
                if (tryReconnectOnce()) return@launch
            }
            // Semua percobaan gagal: tampilkan tombol refresh manual + background abu-abu
            _linkState.value = LinkState.OFFLINE_GAVE_UP
            _networkStatus.value = _networkStatus.value.copy(connectionState = ConnectionState.ERROR)
        }
    }

    /** Satu kali percobaan menyambung ulang. True jika berhasil dan polling sudah dimulai lagi. */
    private suspend fun tryReconnectOnce(): Boolean {
        val ip = _gatewayIp.value
        wifiHelper.bindToWifiNetwork()
        val ok = modemService.refreshSession(ip)
        if (!ok) return false
        consecutiveErrors = 0
        _linkState.value = LinkState.ONLINE
        _offlineAttempt.value = 0
        _networkStatus.value = _networkStatus.value.copy(connectionState = ConnectionState.CONNECTED)
        fetchDataOnce()
        refreshDevices()
        startDataPolling(pollingFast)
        return true
    }

    /** Tombol refresh manual (muncul setelah 10 percobaan gagal). */
    fun manualRefresh() {
        reconnectJob?.cancel()
        _offlineAttempt.value = 0
        _secondsToNextRetry.value = 0
        _linkState.value = LinkState.OFFLINE_RETRYING
        reconnectJob = viewModelScope.launch {
            if (!tryReconnectOnce()) {
                _linkState.value = LinkState.OFFLINE_GAVE_UP
            }
        }
    }

    // ------------------------------------------------------------------------------------
    // Siklus hidup aplikasi (background / foreground)
    // ------------------------------------------------------------------------------------

    /** Dipanggil saat aplikasi tidak lagi terlihat. Polling dihentikan agar hemat baterai. */
    fun onAppBackgrounded() {
        if (_currentScreen.value == AppScreen.LOGIN) return
        pollingJob?.cancel()
        reconnectJob?.cancel()
    }

    /**
     * Dipanggil saat aplikasi dibuka kembali. Sesi modem biasanya sudah kedaluwarsa dan binding Wi-Fi
     * bisa basi, jadi pulihkan keduanya lalu mulai polling dari awal supaya angka realtime bergerak lagi.
     */
    fun onAppForegrounded() {
        if (_currentScreen.value == AppScreen.LOGIN) return

        if (_isDemoMode.value) {
            startDataPolling(pollingFast)
            return
        }

        pollingJob?.cancel()
        reconnectJob?.cancel()
        viewModelScope.launch {
            // Tampilkan angka lama dulu, lalu segarkan segera
            if (tryReconnectOnce()) return@launch
            // Belum terjangkau: masuk mode offline + retry otomatis
            _linkState.value = LinkState.OFFLINE_RETRYING
            _networkStatus.value = _networkStatus.value.copy(connectionState = ConnectionState.RECONNECTING)
            startReconnectLoop()
        }
    }

    // ------------------------------------------------------------------------------------
    // Perangkat terhubung & blokir
    // ------------------------------------------------------------------------------------

    private suspend fun refreshDevices() {
        val ip = _gatewayIp.value
        if (_isDemoMode.value) {
            val now = System.currentTimeMillis()
            for (i in demoConnected.indices) {
                val d = demoConnected[i]
                val secs = d.connectedSeconds + 5
                demoConnected[i] = d.copy(connectedSeconds = secs, connectedSinceMs = now - secs * 1000L)
            }
            _deviceListError.value = null
            _connectedDevices.value = demoConnected.toList()
            _blockedDevices.value = demoBlocked.toList()
            return
        }
        val localIp = wifiHelper.getLocalWifiIp()
        modemService.fetchHostList(ip).onSuccess { list ->
            _deviceListError.value = null
            _connectedDevices.value = list.map { it.copy(isThisPhone = localIp != null && it.ip == localIp) }
        }.onFailure { _deviceListError.value = it.message ?: "daftar perangkat tidak dapat dibaca" }
        modemService.fetchBlockedDevices(ip).onSuccess { _blockedDevices.value = it }
    }

    fun blockDevice(device: ConnectedDevice) {
        if (device.isThisPhone || _deviceBusyMac.value != null) return
        _deviceBusyMac.value = device.mac
        viewModelScope.launch {
            val result = if (_isDemoMode.value) {
                demoConnected.removeAll { it.mac == device.mac }
                demoBlocked.add(BlockedDevice(device.mac, device.name))
                Result.success(Unit)
            } else {
                modemService.blockDevice(_gatewayIp.value, device.mac, device.name)
            }
            result.onSuccess {
                _connectedDevices.value = _connectedDevices.value.filterNot { it.mac == device.mac }
                _deviceMessage.value = "${device.name} diblokir"
            }.onFailure { _deviceMessage.value = it.message ?: "Gagal memblokir perangkat" }
            refreshDevices()
            _deviceBusyMac.value = null
        }
    }

    fun unblockDevice(device: BlockedDevice) {
        if (_deviceBusyMac.value != null) return
        _deviceBusyMac.value = device.mac
        viewModelScope.launch {
            val result = if (_isDemoMode.value) {
                demoBlocked.removeAll { it.mac == device.mac }
                Result.success(Unit)
            } else {
                modemService.unblockDevice(_gatewayIp.value, device.mac)
            }
            result.onSuccess {
                _blockedDevices.value = _blockedDevices.value.filterNot { it.mac == device.mac }
                _deviceMessage.value = "${device.name} dibuka blokirnya"
            }.onFailure { _deviceMessage.value = it.message ?: "Gagal membuka blokir" }
            refreshDevices()
            _deviceBusyMac.value = null
        }
    }

    fun clearDeviceMessage() {
        _deviceMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
        reconnectJob?.cancel()
        beeper.release()
    }
}
