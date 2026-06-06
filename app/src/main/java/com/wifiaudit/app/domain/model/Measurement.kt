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
    val rssiPerBand: Map<String, Int> = emptyMap(), // ex: {"2.4GHz":-62, "5GHz":-55}
    val apReadings: List<ApReading> = emptyList(),  // RSSI brut du scan — un enregistrement par AP du même SSID
    /** "gateway" | repeater ID | null.  Non-null = mesure prise en phase guidée à côté de ce device. */
    val deviceHint: String? = null,
    /** BSSID de l'AP auquel le téléphone était connecté lors de cette mesure (vide si masqué). */
    val connectedBssid: String = "",
    val pingGatewayMs: Int,
    val pingInternetMs: Int,
    val neighbors: List<NeighborNetwork> = emptyList()
) {
    val quality: SignalQuality get() = rssiToQuality(rssi)

    /**
     * RSSI à utiliser pour l'IDW selon la bande sélectionnée.
     * Note : si la bande est absente d'un rssiPerBand non vide, la mesure
     * doit être exclue de l'IDW avant d'appeler cette fonction.
     */
    fun rssiForDisplay(band: String?): Int =
        if (band == null) rssi else rssiPerBand[band] ?: rssi

    /** Vrai si cette mesure dispose d'une valeur RSSI exploitable pour [band]. */
    fun hasBandData(band: String?): Boolean =
        band == null || rssiPerBand.isEmpty() || rssiPerBand.containsKey(band)

    /** RSSI du BSSID spécifique, ou RSSI connecté si ce BSSID n'était pas visible. */
    fun rssiForBssid(bssid: String): Int =
        apReadings.find { it.bssid == bssid }?.rssi ?: rssi

    /** Vrai si ce BSSID était visible lors de ce scan (ou si aucun apReading n'existe). */
    fun hasBssidData(bssid: String): Boolean =
        apReadings.isEmpty() || apReadings.any { it.bssid == bssid }
}

data class NeighborNetwork(
    val ssid: String?,
    val bssid: String,
    val rssi: Int,
    val channel: Int,
    val band: String
)
