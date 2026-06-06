package com.wifiaudit.app.data.remote.dto

import com.google.gson.annotations.SerializedName

// ─── Requête POST /audits ────────────────────────────────────────────────────

data class AuditPayload(
    @SerializedName("audit_id")           val auditId: String,
    @SerializedName("created_at")         val createdAt: String,
    val ssid: String,
    @SerializedName("device_info")        val deviceInfo: DeviceInfoDto,
    @SerializedName("plan_image_base64")  val planImageBase64: String?,
    @SerializedName("gateway_position")   val gatewayPosition: PositionDto,
    @SerializedName("repeater_positions") val repeaterPositions: List<RepeaterPositionDto>,
    val measurements: List<MeasurementDto>
)

data class DeviceInfoDto(
    val model: String,
    @SerializedName("android_version") val androidVersion: String,
    @SerializedName("app_version")     val appVersion: String
)

data class PositionDto(val x: Float, val y: Float)

data class RepeaterPositionDto(val id: String, val x: Float, val y: Float)

data class MeasurementDto(
    val x: Float,
    val y: Float,
    val rssi: Int,
    val bssid: String,
    val channel: Int,
    val band: String,
    @SerializedName("ping_gateway_ms")      val pingGatewayMs: Int,
    @SerializedName("ping_internet_ms")     val pingInternetMs: Int,
    @SerializedName("neighboring_networks") val neighboringNetworks: List<NeighborNetworkDto> = emptyList()
)

data class NeighborNetworkDto(
    val ssid: String?,
    val bssid: String,
    val rssi: Int,
    val channel: Int,
    val band: String
)

// ─── Réponses du backend ─────────────────────────────────────────────────────

data class SubmitResponse(
    val id: String,
    @SerializedName("measurement_count") val measurementCount: Int
)

data class AuditSummaryDto(
    val id: String,
    @SerializedName("created_at")        val createdAt: String,
    val ssid: String,
    @SerializedName("measurement_count") val measurementCount: Int,
    val status: String
)
