package com.wifiaudit.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "measurements",
    foreignKeys = [
        ForeignKey(
            entity = AuditEntity::class,
            parentColumns = ["id"],
            childColumns = ["auditId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("auditId")]  // index obligatoire sur la clé étrangère
)
data class MeasurementEntity(
    @PrimaryKey val id: String,
    val auditId: String,
    val roomId: String? = null,  // ID de la CanvasRoom contenant ce point
    val x: Float,
    val y: Float,
    val rssi: Int,
    val bssid: String,
    val channel: Int,
    val band: String,               // "2.4GHz" | "5GHz" | "6GHz"
    val pingGatewayMs: Int,
    val pingInternetMs: Int,
    val neighborsJson: String = "[]",     // List<NeighborNetwork> sérialisée
    val rssiPerBandJson: String = "{}",  // Map<String, Int> sérialisée
    val apReadingsJson: String = "[]",   // List<ApReading> sérialisée
    val deviceHint: String? = null,      // "gateway" | repeater ID | null
    val connectedBssid: String = ""      // AP auquel le téléphone était connecté (vide si masqué)
)
