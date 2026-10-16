package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.BandOption
import com.example.model.ConnectionState
import com.example.model.IpChangeProgress
import com.example.model.NetworkStatus
import com.example.model.SignalData
import com.example.model.TrafficData
import com.example.network.HilinkModemService
import com.example.network.WifiNetworkHelper
import com.example.util.AntennaBeeper
import com.example.util.PreferencesManager
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

    private var pollingJob: Job? = null
    private var consecutiveErrors = 0

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
                _networkStatus.value = _networkStatus.value.copy(
                    gatewayIp = ip,
                    connectionState = ConnectionState.CONNECTED,
                    isDemoMode = true
                )
                _currentScreen.value = AppScreen.DASHBOARD
                startDataPolling()
                return@launch
            }

            // Real connection: Bind socket routing to Wi-Fi
            wifiHelper.bindToWifiNetwork()

            val checkResult = modemService.checkConnection(ip)
            _isConnecting.value = false

            checkResult.onSuccess {
                consecutiveErrors = 0
                // Save credentials for seamless auto-login next time
                prefs.saveCredentials(ip, user, pass)

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
                startDataPolling()
            }.onFailure { error ->
                _networkStatus.value = _networkStatus.value.copy(
                    connectionState = ConnectionState.ERROR
                )
                _connectionErrorMessage.value = error.message
                    ?: "Gagal terhubung ke modem di $ip. Pastikan HP terhubung ke Wi-Fi modem E5372."
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
            val (limitBytes, startDay) = modemService.fetchDataLimitSettings(ip)
            if (limitBytes > 0) {
                val gb = limitBytes / 1_000_000_000f
                _quotaLimitGb.value = gb
                _resetDayOfMonth.value = startDay
                prefs.quotaLimitGb = gb
                prefs.resetDayOfMonth = startDay
                _trafficData.value = _trafficData.value.copy(
                    monthQuotaLimitBytes = limitBytes,
                    resetDayOfMonth = startDay
                )
            }
        }
    }

    fun disconnect() {
        pollingJob?.cancel()
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
        _sessionMaxSinr.value = _signalData.value.sinr
        startDataPolling(fastMode = true)
    }

    fun closeSignalHunter() {
        _currentScreen.value = AppScreen.DASHBOARD
        startDataPolling(fastMode = false)
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

        val limitBytes = (limitGb * 1_000_000_000L).toLong()
        _trafficData.value = _trafficData.value.copy(
            monthQuotaLimitBytes = limitBytes,
            resetDayOfMonth = resetDay
        )

        viewModelScope.launch {
            modemService.updateDataLimitSettings(_gatewayIp.value, limitBytes, resetDay)
        }
    }

    private fun startDataPolling(fastMode: Boolean = false) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                val ip = _gatewayIp.value
                val demo = _isDemoMode.value

                // 1. Fetch signal parameters
                val signalResult = modemService.fetchSignalData(ip, demo)
                signalResult.onSuccess { sig ->
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
                }.onFailure {
                    if (!demo) {
                        consecutiveErrors++
                        if (consecutiveErrors >= 5) {
                            _networkStatus.value = _networkStatus.value.copy(
                                connectionState = ConnectionState.RECONNECTING
                            )
                        }
                    }
                }

                // 2. Fetch traffic & network status
                val trafficResult = modemService.fetchTrafficData(ip, demo)
                trafficResult.onSuccess { tf ->
                    _trafficData.value = tf
                }

                val netResult = modemService.fetchNetworkStatus(ip, demo)
                netResult.onSuccess { st ->
                    _networkStatus.value = st
                }

                // 500ms in signal hunter for ultra-fast antenna adjustments, 1200ms in dashboard
                val sleepTime = if (fastMode) 500L else 1200L
                delay(sleepTime)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
        beeper.release()
    }
}
