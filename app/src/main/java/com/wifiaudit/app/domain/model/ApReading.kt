package com.wifiaudit.app.domain.model

/** RSSI d'un AP (identifié par son BSSID) du réseau de l'utilisateur, mesuré lors d'un scan. */
data class ApReading(
    val bssid: String,
    val rssi: Int,
    val band: String   // "2.4GHz" | "5GHz" | "6GHz"
)
