package com.wifiaudit.app.domain.model

// Seuils internes — ne JAMAIS exposer les valeurs dBm dans l'UI
// Seuils pratiques Android : WifiManager retourne souvent -65 à -85 dBm en usage résidentiel
enum class SignalQuality { GOOD, FAIR, POOR }

fun rssiToQuality(rssi: Int): SignalQuality = when {
    rssi >= -55 -> SignalQuality.GOOD   // Excellent (proche du routeur)
    rssi >= -72 -> SignalQuality.FAIR   // Utilisable (pièce voisine)
    else        -> SignalQuality.POOR   // Faible (trop loin ou obstruction)
}

// Seule forme autorisée dans l'UI
fun SignalQuality.toUserLabel(): String = when (this) {
    SignalQuality.GOOD -> "Signal fort"
    SignalQuality.FAIR -> "Signal moyen"
    SignalQuality.POOR -> "Signal faible"
}
