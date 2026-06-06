package com.wifiaudit.app.domain.model

data class Measurement(
    val id: String,
    val auditId: String,
    val roomId: String?  = null,               // ID de la CanvasRoom contenant ce point
    val x: Float,                              // position normalisée 0..1 sur le plan
    val y: Float,
    val rssi: Int,                             // dBm bande connectée — usage interne uniquement
    val bssid: String,
    val channel: Int,
    val band: String,                          // "2.4GHz" | "5GHz" | "6GHz"
    val rssiPerBand: Map<String, Int> = emptyMap(), // ex: {"2.4GHz":-62, "5GHz":-55, "6GHz":-70}
    val pingGatewayMs: Int,
    val pingInternetMs: Int,
    val neighbors: List<NeighborNetwork> = emptyList()
) {
    val quality: SignalQuality get() = rssiToQuality(rssi)

    /** RSSI de la bande demandée, ou RSSI principal si absent. */
    fun rssiForBand(b: String): Int = rssiPerBand[b] ?: rssi
}

data class NeighborNetwork(
    val ssid: String?,
    val bssid: String,
    val rssi: Int,
    val channel: Int,
    val band: String
)
