package com.wifiaudit.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

// Les champs complexes (listes) sont sérialisés en JSON —
// pas de TypeConverter nécessaire pour le MVP.
@Entity(tableName = "audits")
data class AuditEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val name: String = "",
    val ssid: String,
    val planImagePath: String,
    val gatewayX: Float,
    val gatewayY: Float,
    val repeaterPositionsJson: String = "[]",  // List<RepeaterPosition> sérialisée
    val roomsJson: String = "[]",              // List<Room> sérialisée
    val summaryJson: String? = null,           // AuditSummary sérialisée
    val status: String = "DRAFT"              // AuditStatus.name : DRAFT | PENDING | SYNCED
)
