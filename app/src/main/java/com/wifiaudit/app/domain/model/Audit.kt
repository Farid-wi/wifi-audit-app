package com.wifiaudit.app.domain.model

import java.util.UUID

enum class AuditStatus { DRAFT, PENDING, SYNCED }

data class Audit(
    val id: String                          = UUID.randomUUID().toString(),
    val createdAt: Long                     = System.currentTimeMillis(),
    val name: String                        = "",
    val ssid: String,
    val planImagePath: String,
    val gatewayPosition: Position,
    val repeaterPositions: List<RepeaterPosition> = emptyList(),
    val rooms: List<CanvasRoom>              = emptyList(),
    val measurements: List<Measurement>    = emptyList(),
    val summary: AuditSummary?             = null,
    val status: AuditStatus                = AuditStatus.DRAFT
)
