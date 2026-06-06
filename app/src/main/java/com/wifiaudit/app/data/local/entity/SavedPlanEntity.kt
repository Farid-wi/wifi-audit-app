package com.wifiaudit.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_plans")
data class SavedPlanEntity(
    @PrimaryKey val id: String,
    val name: String,
    val planImagePath: String = "",
    val roomsJson: String = "[]",
    val gatewayX: Float? = null,
    val gatewayY: Float? = null,
    val repeaterPositionsJson: String = "[]",
    val createdAt: Long
)
