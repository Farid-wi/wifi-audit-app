package com.wifiaudit.app.domain.repository

import com.wifiaudit.app.domain.model.ApReading
import com.wifiaudit.app.domain.model.NeighborNetwork

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
}
