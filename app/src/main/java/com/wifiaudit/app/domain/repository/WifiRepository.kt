package com.wifiaudit.app.domain.repository

import com.wifiaudit.app.domain.model.NeighborNetwork

interface WifiRepository {

    /** Données brutes du réseau connecté + voisins. */
    data class ScanData(
        val rssi: Int,
        val bssid: String,
        val channel: Int,
        val band: String,
        val gatewayIp: String?,
        val neighbors: List<NeighborNetwork>
    )

    /** Réseau visible dans les environs. */
    data class VisibleNetwork(
        val ssid: String,
        val isConnected: Boolean
    )

    suspend fun scan(targetSsid: String? = null): Result<ScanData>

    /** Retourne tous les SSIDs visibles, dédupliqués, le connecté en premier. */
    suspend fun getVisibleNetworks(): List<VisibleNetwork>
}
