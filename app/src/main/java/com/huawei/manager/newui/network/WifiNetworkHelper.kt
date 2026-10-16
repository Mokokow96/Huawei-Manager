package com.huawei.manager.newui.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log

class WifiNetworkHelper(private val context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /**
     * Binds HTTP socket routing directly to Wi-Fi.
     * Crucial for MiFi: prevents Android from routing 192.168.8.1 requests
     * over phone's 4G cellular data when Wi-Fi has no internet connection yet.
     */
    fun bindToWifiNetwork(): Boolean {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Find active Wi-Fi network
                for (network in connectivityManager.allNetworks) {
                    val caps = connectivityManager.getNetworkCapabilities(network)
                    if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        val bound = connectivityManager.bindProcessToNetwork(network)
                        Log.d("WifiNetworkHelper", "Bound process to Wi-Fi network: $bound")
                        return bound
                    }
                }
                // Tidak ada Wi-Fi aktif: lepas binding lama supaya tidak menempel ke jaringan yang sudah hilang
                connectivityManager.bindProcessToNetwork(null)
            }
        } catch (e: Exception) {
            Log.e("WifiNetworkHelper", "Failed to bind to Wi-Fi", e)
        }
        return false
    }

    fun isWifiConnected(): Boolean {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val activeNetwork = connectivityManager.activeNetwork ?: return false
                val caps = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
                return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            } else {
                @Suppress("DEPRECATION")
                val netInfo = connectivityManager.activeNetworkInfo
                @Suppress("DEPRECATION")
                return netInfo != null && netInfo.type == ConnectivityManager.TYPE_WIFI
            }
        } catch (e: Exception) {
            return false
        }
    }

    /** Alamat IPv4 HP pada jaringan Wi-Fi saat ini (untuk menandai "Perangkat ini" di daftar user). */
    fun getLocalWifiIp(): String? {
        try {
            for (network in connectivityManager.allNetworks) {
                val caps = connectivityManager.getNetworkCapabilities(network) ?: continue
                if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) continue
                val props = connectivityManager.getLinkProperties(network) ?: continue
                val v4 = props.linkAddresses.firstOrNull { it.address is java.net.Inet4Address }
                if (v4 != null) return v4.address.hostAddress
            }
        } catch (e: Exception) {
            Log.w("WifiNetworkHelper", "Gagal membaca IP Wi-Fi lokal", e)
        }
        return null
    }
}
