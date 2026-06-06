package com.wifiaudit.app.domain.model

import java.util.UUID

data class SavedPlan(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val planImagePath: String = "",
    val rooms: List<CanvasRoom> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)
