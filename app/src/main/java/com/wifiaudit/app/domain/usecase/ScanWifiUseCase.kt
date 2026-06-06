package com.wifiaudit.app.domain.usecase

import android.util.Log
import com.wifiaudit.app.domain.model.Measurement
import com.wifiaudit.app.domain.repository.WifiRepository
import java.util.UUID
import javax.inject.Inject

private const val TAG = "WIFI_AUDIT"

class ScanWifiUseCase @Inject constructor(
    private val wifiRepository: WifiRepository,
    private val runPingUseCase: RunPingUseCase
) {
    /**
     * Scanne le Wi-Fi, ping la gateway + internet, et retourne une [Measurement] complète.
     * @param x position normalisée 0..1 sur le plan (axe horizontal)
     * @param y position normalisée 0..1 sur le plan (axe vertical)
     * @param auditId identifiant de l'audit en cours
     */
    suspend operator fun invoke(
        x: Float,
        y: Float,
        auditId: String,
        targetSsid: String? = null,
        deviceHint: String? = null
    ): Result<Measurement> =
        wifiRepository.scan(targetSsid)
            .onFailure { Log.e(TAG, "scan() failed: ${it.message}") }
            .map { data ->
                val pingGateway  = data.gatewayIp?.let { runPingUseCase(it) } ?: -1
                val pingInternet = runPingUseCase("8.8.8.8") ?: -1

                val m = Measurement(
                    id             = UUID.randomUUID().toString(),
                    auditId        = auditId,
                    x              = x,
                    y              = y,
                    rssi           = data.rssi,
                    bssid          = data.bssid,
                    channel        = data.channel,
                    band           = data.band,
                    rssiPerBand    = data.rssiPerBand,
                    apReadings     = data.apReadings,
                    deviceHint     = deviceHint,
                    connectedBssid = data.connectedBssid,
                    pingGatewayMs  = pingGateway,
                    pingInternetMs = pingInternet,
                    neighbors      = data.neighbors
                )

                Log.d(TAG, buildString {
                    appendLine("=== MESURE ===")
                    appendLine("  Position plan  : x=%.3f  y=%.3f".format(x, y))
                    appendLine("  RSSI lien      : ${data.rssi} dBm (${data.band}) → ${m.quality}")
                    val connStr = data.connectedBssid.ifEmpty { "inconnu (masqué Android)" }
                    appendLine("  AP connecté    : $connStr")
                    deviceHint?.let { appendLine("  Device hint    : $it  ← mesure guidée") }
                    appendLine("  Ping gateway   : ${if (pingGateway < 0) "unreachable" else "${pingGateway}ms"}  (${data.gatewayIp ?: "N/A"})")
                    appendLine("  Ping internet  : ${if (pingInternet < 0) "unreachable" else "${pingInternet}ms"}")
                    if (data.apReadings.isNotEmpty()) {
                        appendLine("  APs (même SSID) : ${data.apReadings.size} détectés  [RSSI brut scan]")
                        data.apReadings.forEach { ap ->
                            val marker = when {
                                ap.bssid == data.connectedBssid -> " ← connecté (lien=${data.rssi} dBm)"
                                else -> ""
                            }
                            appendLine("    ${ap.bssid}  ${ap.band.padEnd(6)}  ${ap.rssi} dBm$marker")
                        }
                    } else {
                        appendLine("  APs (même SSID) : aucun")
                    }
                    appendLine("  rssiPerBand    : ${data.rssiPerBand}  [wifiInfo pour bande connectée]")
                    append  ("  Voisins        : ${data.neighbors.size} réseaux")
                })

                m
            }
}
