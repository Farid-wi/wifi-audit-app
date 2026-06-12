package com.wifiaudit.app.domain.repository

import com.wifiaudit.app.domain.model.ApReading
import com.wifiaudit.app.domain.model.NeighborNetwork
import com.wifiaudit.app.domain.model.ScanMode

interface WifiRepository {

    /** Données brutes du réseau connecté + voisins. */
    data class ScanData(
        val rssi: Int,
        val bssid: String,
        val channel: Int,
        val band: String,
        val gatewayIp: String?,
        val neighbors: List<NeighborNetwork>,
        /** RSSI mesuré par bande pour le même SSID : {"2.4GHz":-62, "5GHz":-55}. */
        val rssiPerBand: Map<String, Int> = emptyMap(),
        /** Un enregistrement par AP visible du même SSID (BSSID + RSSI + bande), RSSI brut du scan. */
        val apReadings: List<ApReading> = emptyList(),
        /** BSSID de l'AP auquel le téléphone est actuellement connecté (vide si masqué ou mode passif). */
        val connectedBssid: String = ""
    )

    /** Réseau visible dans les environs. */
    data class VisibleNetwork(
        val ssid: String,
        val isConnected: Boolean
    )

    suspend fun scan(targetSsid: String? = null): Result<ScanData>

    /**
     * Millisecondes à attendre avant qu'un scan frais soit de nouveau possible (0 si dispo
     * immédiatement). Android limite à 4 scans / 2 min ; au-delà, une mesure renverrait des
     * données périmées. L'UI s'en sert pour désactiver le bouton de mesure avec un compte à rebours.
     */
    suspend fun scanCooldownRemainingMs(): Long

    /** Retourne tous les SSIDs visibles, dédupliqués, le connecté en premier. */
    suspend fun getVisibleNetworks(): List<VisibleNetwork>

    // ─── Mode de scan (cadence) ──────────────────────────────────────────────

    /** Mode de scan actuellement mémorisé (défaut : [ScanMode.STANDARD]). */
    fun getScanMode(): ScanMode

    /**
     * Mémorise le mode choisi par l'utilisateur et démarre une nouvelle session de diagnostic
     * (remise à zéro des compteurs réussis/échoués/périmés).
     */
    fun setScanMode(mode: ScanMode)

    /**
     * Détection EMPIRIQUE du throttling Android : l'état du réglage « Limitation de la recherche
     * Wi-Fi » n'est pas lisible par API. On lance 3 [android.net.wifi.WifiManager.startScan]
     * espacés de ~5 s et on compare la fraîcheur des résultats. Si les 3 scans sont frais →
     * throttling désactivé → le mode rapide est exploitable.
     *
     * @return true si le throttling semble désactivé (3 scans frais sur 3).
     * @throws SecurityException si la permission de localisation manque.
     * @throws IllegalStateException si la localisation système ou le Wi-Fi sont désactivés.
     */
    suspend fun detectThrottlingDisabled(): Boolean

    /** Écrit dans les logs le résumé de la session de scan (réussis / échoués / périmés). */
    fun logScanSessionSummary()
}
