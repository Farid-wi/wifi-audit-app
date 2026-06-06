package com.wifiaudit.app.data.repository

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import com.wifiaudit.app.domain.model.NeighborNetwork
import com.wifiaudit.app.domain.repository.WifiRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val TAG = "WIFI_AUDIT"

class WifiRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : WifiRepository {

    private val wifiManager: WifiManager by lazy {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }

    private val connectivityManager: ConnectivityManager by lazy {
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    @SuppressLint("MissingPermission")
    override suspend fun scan(targetSsid: String?): Result<WifiRepository.ScanData> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!wifiManager.isWifiEnabled) {
                    throw IllegalStateException("Activez le Wi-Fi de votre téléphone")
                }

                val wifiInfo = getConnectedWifiInfo()

                if (wifiInfo != null && wifiInfo.rssi > -127) {
                    // ── Mode connecté ─────────────────────────────────────────
                    val rssi      = wifiInfo.rssi
                    val bssid     = wifiInfo.bssid?.takeIf { it != "02:00:00:00:00:00" } ?: ""
                    val frequency = wifiInfo.frequency
                    val band      = frequencyToBand(frequency)
                    val channel   = frequencyToChannel(frequency)
                    val gatewayIp = intToIp(wifiManager.dhcpInfo.gateway)

                    Log.d(TAG, "Mode connecté — rssi=$rssi bssid=$bssid freq=$frequency ch=$channel gw=$gatewayIp")

                    @Suppress("DEPRECATION")
                    val neighbors = wifiManager.scanResults
                        .filter { it.BSSID != bssid }
                        .mapNotNull { r ->
                            val ssid = r.SSID?.trim()
                                ?.takeIf { it.isNotEmpty() && it != "<unknown ssid>" }
                            NeighborNetwork(ssid, r.BSSID, r.level,
                                frequencyToChannel(r.frequency), frequencyToBand(r.frequency))
                        }

                    WifiRepository.ScanData(rssi, bssid, channel, band, gatewayIp, neighbors)

                } else {
                    // ── Mode passif (non connecté) — lecture des scanResults ──
                    // Demande un scan frais; Android 9+ limite à 4/2min mais ça reste utile
                    @Suppress("DEPRECATION")
                    val started = wifiManager.startScan()
                    Log.d(TAG, "startScan demandé — accepté=$started")
                    if (started) delay(1500) // attendre la complétion

                    @Suppress("DEPRECATION")
                    val results = wifiManager.scanResults
                    if (results.isEmpty()) {
                        throw IllegalStateException(
                            "Aucun réseau Wi-Fi détecté. Rapprochez-vous de votre box."
                        )
                    }

                    // Sélectionne le meilleur signal pour le SSID cible, sinon le réseau le plus fort
                    val target = targetSsid?.let { ssid ->
                        results
                            .filter { it.SSID?.trim('"') == ssid.trim('"') }
                            .maxByOrNull { it.level }
                    } ?: results.maxByOrNull { it.level }!!

                    val bssid   = target.BSSID ?: ""
                    val band    = frequencyToBand(target.frequency)
                    val channel = frequencyToChannel(target.frequency)

                    Log.d(TAG, "Mode passif — ${target.SSID} rssi=${target.level} freq=${target.frequency} ch=$channel")

                    val neighbors = results
                        .filter { it.BSSID != bssid }
                        .mapNotNull { r ->
                            val ssid = r.SSID?.trim()
                                ?.takeIf { it.isNotEmpty() && it != "<unknown ssid>" }
                            NeighborNetwork(ssid, r.BSSID, r.level,
                                frequencyToChannel(r.frequency), frequencyToBand(r.frequency))
                        }

                    WifiRepository.ScanData(target.level, bssid, channel, band, null, neighbors)
                }
            }
        }

    @SuppressLint("MissingPermission")
    override suspend fun getVisibleNetworks(): List<WifiRepository.VisibleNetwork> =
        withContext(Dispatchers.IO) {
            val connectedBssid = getConnectedWifiInfo()?.bssid
            val connectedSsid  = getConnectedWifiInfo()?.ssid?.trim('"')
                ?.takeIf { it.isNotEmpty() && it != "<unknown ssid>" }

            Log.d(TAG, "getVisibleNetworks — connectedSsid=$connectedSsid")

            @Suppress("DEPRECATION")
            wifiManager.scanResults
                .mapNotNull { r ->
                    val ssid = r.SSID?.trim()?.takeIf { it.isNotEmpty() && it != "<unknown ssid>" }
                        ?: return@mapNotNull null
                    WifiRepository.VisibleNetwork(ssid, ssid == connectedSsid)
                }
                .distinctBy { it.ssid }
                .sortedByDescending { it.isConnected }
        }

    // ─── API moderne pour WifiInfo (Android 10+) ─────────────────────────────

    @SuppressLint("MissingPermission")
    private fun getConnectedWifiInfo(): WifiInfo? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            connectivityManager.allNetworks.forEach { network ->
                val caps = connectivityManager.getNetworkCapabilities(network) ?: return@forEach
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    val info = caps.transportInfo as? WifiInfo ?: return@forEach
                    Log.d(TAG, "Wi-Fi trouvé via allNetworks — rssi=${info.rssi} ssid=${info.ssid}")
                    return info
                }
            }
            Log.w(TAG, "allNetworks vide (${connectivityManager.allNetworks.size}) — fallback connectionInfo")
            // Fallback : certains ROMs/versions exposent encore connectionInfo correctement
            @Suppress("DEPRECATION")
            val legacy = wifiManager.connectionInfo
            if (legacy != null && legacy.networkId != -1 && legacy.rssi > -127) {
                Log.d(TAG, "Wi-Fi via fallback connectionInfo — rssi=${legacy.rssi} networkId=${legacy.networkId}")
                return legacy
            }
            return null
        }
        @Suppress("DEPRECATION")
        return wifiManager.connectionInfo
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun frequencyToBand(freq: Int): String = when {
        freq < 3000  -> "2.4GHz"
        freq < 6000  -> "5GHz"
        else         -> "6GHz"
    }

    private fun frequencyToChannel(freq: Int): Int = when {
        freq in 2412..2484 -> (freq - 2412) / 5 + 1
        freq in 5170..5825 -> (freq - 5170) / 5 + 34
        freq in 5955..7115 -> (freq - 5955) / 5 + 1
        else               -> 0
    }

    private fun intToIp(ip: Int): String? {
        if (ip == 0) return null
        return buildString {
            append(ip and 0xff).append('.')
            append(ip shr 8 and 0xff).append('.')
            append(ip shr 16 and 0xff).append('.')
            append(ip shr 24 and 0xff)
        }
    }
}
