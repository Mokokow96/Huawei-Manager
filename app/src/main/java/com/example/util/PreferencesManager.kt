package com.example.util

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("huawei_mifi_prefs", Context.MODE_PRIVATE)

    var gatewayIp: String
        get() = prefs.getString(KEY_GATEWAY_IP, "192.168.8.1") ?: "192.168.8.1"
        set(value) = prefs.edit().putString(KEY_GATEWAY_IP, value).apply()

    var username: String
        get() = prefs.getString(KEY_USERNAME, "admin") ?: "admin"
        set(value) = prefs.edit().putString(KEY_USERNAME, value).apply()

    var password: String
        get() = prefs.getString(KEY_PASSWORD, "admin") ?: "admin"
        set(value) = prefs.edit().putString(KEY_PASSWORD, value).apply()

    var hasLoggedInBefore: Boolean
        get() = prefs.getBoolean(KEY_HAS_LOGGED_IN, false)
        set(value) = prefs.edit().putBoolean(KEY_HAS_LOGGED_IN, value).apply()

    var quotaLimitGb: Float
        get() = prefs.getFloat(KEY_QUOTA_LIMIT_GB, 50f)
        set(value) = prefs.edit().putFloat(KEY_QUOTA_LIMIT_GB, value).apply()

    var resetDayOfMonth: Int
        get() = prefs.getInt(KEY_RESET_DAY, 1)
        set(value) = prefs.edit().putInt(KEY_RESET_DAY, value).apply()

    var lastModel: String
        get() = prefs.getString(KEY_LAST_MODEL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_MODEL, value).apply()

    var lastFirmware: String
        get() = prefs.getString(KEY_LAST_FIRMWARE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_FIRMWARE, value).apply()

    fun saveCredentials(ip: String, user: String, pass: String) {
        prefs.edit()
            .putString(KEY_GATEWAY_IP, ip)
            .putString(KEY_USERNAME, user)
            .putString(KEY_PASSWORD, pass)
            .putBoolean(KEY_HAS_LOGGED_IN, true)
            .apply()
    }

    fun clearAutoLogin() {
        prefs.edit().putBoolean(KEY_HAS_LOGGED_IN, false).apply()
    }

    companion object {
        private const val KEY_LAST_MODEL = "last_model"
        private const val KEY_LAST_FIRMWARE = "last_firmware"
        private const val KEY_GATEWAY_IP = "gateway_ip"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_HAS_LOGGED_IN = "has_logged_in_before"
        private const val KEY_QUOTA_LIMIT_GB = "quota_limit_gb"
        private const val KEY_RESET_DAY = "reset_day_of_month"
    }
}
