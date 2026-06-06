package com.wifiaudit.app.data.repository

import android.content.Context
import android.os.Build
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.wifiaudit.app.BuildConfig
import com.wifiaudit.app.data.local.dao.AuditDao
import com.wifiaudit.app.data.local.dao.MeasurementDao
import com.wifiaudit.app.data.local.entity.AuditEntity
import com.wifiaudit.app.data.local.entity.MeasurementEntity
import com.wifiaudit.app.data.remote.api.AuditApiService
import com.wifiaudit.app.data.remote.dto.AuditPayload
import com.wifiaudit.app.data.remote.dto.DeviceInfoDto
import com.wifiaudit.app.data.remote.dto.MeasurementDto
import com.wifiaudit.app.data.remote.dto.NeighborNetworkDto
import com.wifiaudit.app.data.remote.dto.PositionDto
import com.wifiaudit.app.data.remote.dto.RepeaterPositionDto
import com.wifiaudit.app.domain.model.ApReading
import com.wifiaudit.app.domain.model.Audit
import com.wifiaudit.app.domain.model.AuditStatus
import com.wifiaudit.app.domain.model.AuditSummary
import com.wifiaudit.app.domain.model.CanvasRoom
import com.wifiaudit.app.domain.model.Measurement
import com.wifiaudit.app.domain.model.NeighborNetwork
import com.wifiaudit.app.domain.model.Position
import com.wifiaudit.app.domain.model.RepeaterPosition
import com.wifiaudit.app.domain.repository.AuditRepository
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.time.Instant
import java.time.format.DateTimeFormatter
import javax.inject.Inject

class AuditRepositoryImpl @Inject constructor(
    private val auditDao: AuditDao,
    private val measurementDao: MeasurementDao,
    private val apiService: AuditApiService,
    private val gson: Gson,
    @ApplicationContext private val context: Context
) : AuditRepository {

    // ─── Lecture ─────────────────────────────────────────────────────────────

    override fun observeAudits(): Flow<List<Audit>> =
        auditDao.observeAll().map { entities ->
            entities.map { entity ->
                val measurements = measurementDao.getForAudit(entity.id)
                entity.toDomain(measurements)
            }
        }

    override suspend fun getPendingAudits(): List<Audit> =
        auditDao.getPending().map { entity ->
            val measurements = measurementDao.getForAudit(entity.id)
            entity.toDomain(measurements)
        }

    // ─── Écriture locale ─────────────────────────────────────────────────────

    override suspend fun saveAudit(audit: Audit) {
        auditDao.upsert(audit.toEntity())
        if (audit.measurements.isNotEmpty()) {
            measurementDao.insertAll(audit.measurements.map { it.toEntity(audit.id) })
        }
    }

    // ─── Envoi réseau ────────────────────────────────────────────────────────

    override suspend fun submitAudit(audit: Audit): Result<Unit> {
        auditDao.updateStatus(audit.id, AuditStatus.PENDING.name)
        return try {
            val payload = audit.toPayload()
            Log.d("WIFI_AUDIT", "HTTP POST → ${apiService.javaClass.simpleName} audit=${audit.id}")
            val response = apiService.submitAudit(payload)
            if (response.isSuccessful) {
                Log.d("WIFI_AUDIT", "HTTP ${response.code()} — SYNCED")
                auditDao.updateStatus(audit.id, AuditStatus.SYNCED.name)
                Result.success(Unit)
            } else {
                Log.e("WIFI_AUDIT", "HTTP ${response.code()} ${response.message()} — body: ${response.errorBody()?.string()}")
                Result.failure(Exception("Serveur injoignable (HTTP ${response.code()})"))
            }
        } catch (e: Exception) {
            Log.e("WIFI_AUDIT", "Connexion impossible: ${e.javaClass.simpleName} — ${e.message}")
            Result.failure(Exception("Impossible de joindre le serveur : ${e.message}"))
        }
    }

    // ─── Mappers Entity → Domain ──────────────────────────────────────────────

    private fun AuditEntity.toDomain(measurementEntities: List<MeasurementEntity>): Audit {
        val repeaterType = object : TypeToken<List<RepeaterPosition>>() {}.type
        val roomType     = object : TypeToken<List<CanvasRoom>>() {}.type
        return Audit(
            id                 = id,
            createdAt          = createdAt,
            ssid               = ssid,
            planImagePath      = planImagePath,
            gatewayPosition    = Position(gatewayX, gatewayY),
            repeaterPositions  = gson.fromJson(repeaterPositionsJson, repeaterType) ?: emptyList(),
            rooms              = gson.fromJson(roomsJson, roomType) ?: emptyList(),
            measurements       = measurementEntities.map { it.toDomain() },
            summary            = summaryJson?.let { gson.fromJson(it, AuditSummary::class.java) },
            status             = runCatching { AuditStatus.valueOf(status) }.getOrDefault(AuditStatus.DRAFT)
        )
    }

    private fun MeasurementEntity.toDomain(): Measurement {
        val neighborType   = object : TypeToken<List<NeighborNetwork>>() {}.type
        val perBandType    = object : TypeToken<Map<String, Int>>() {}.type
        val apReadingType  = object : TypeToken<List<ApReading>>() {}.type
        return Measurement(
            id             = id,
            auditId        = auditId,
            roomId         = roomId,
            x              = x,
            y              = y,
            rssi           = rssi,
            bssid          = bssid,
            channel        = channel,
            band           = band,
            rssiPerBand    = gson.fromJson(rssiPerBandJson, perBandType) ?: emptyMap(),
            apReadings     = gson.fromJson(apReadingsJson, apReadingType) ?: emptyList(),
            deviceHint     = deviceHint,
            connectedBssid = connectedBssid,
            pingGatewayMs  = pingGatewayMs,
            pingInternetMs = pingInternetMs,
            neighbors      = gson.fromJson(neighborsJson, neighborType) ?: emptyList()
        )
    }

    // ─── Mappers Domain → Entity ──────────────────────────────────────────────

    private fun Audit.toEntity() = AuditEntity(
        id                    = id,
        createdAt             = createdAt,
        ssid                  = ssid,
        planImagePath         = planImagePath,
        gatewayX              = gatewayPosition.x,
        gatewayY              = gatewayPosition.y,
        repeaterPositionsJson = gson.toJson(repeaterPositions),
        roomsJson             = gson.toJson(rooms),
        summaryJson           = summary?.let { gson.toJson(it) },
        status                = status.name
    )

    private fun Measurement.toEntity(auditId: String) = MeasurementEntity(
        id               = id,
        auditId          = auditId,
        roomId           = roomId,
        x                = x,
        y                = y,
        rssi             = rssi,
        bssid            = bssid,
        channel          = channel,
        band             = band,
        pingGatewayMs    = pingGatewayMs,
        pingInternetMs   = pingInternetMs,
        neighborsJson    = gson.toJson(neighbors),
        rssiPerBandJson  = gson.toJson(rssiPerBand),
        apReadingsJson   = gson.toJson(apReadings),
        deviceHint       = deviceHint,
        connectedBssid   = connectedBssid
    )

    // ─── Mapper Domain → Payload réseau ──────────────────────────────────────

    private fun Audit.toPayload(): AuditPayload {
        val imageBase64 = runCatching {
            val bytes = File(planImagePath).readBytes()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        }.getOrNull()

        return AuditPayload(
            auditId         = id,
            createdAt       = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(createdAt)),
            ssid            = ssid,
            deviceInfo      = DeviceInfoDto(
                model          = Build.MODEL,
                androidVersion = Build.VERSION.RELEASE,
                appVersion     = BuildConfig.VERSION_NAME
            ),
            planImageBase64 = imageBase64,
            gatewayPosition = PositionDto(gatewayPosition.x, gatewayPosition.y),
            repeaterPositions = repeaterPositions.map {
                RepeaterPositionDto(it.id, it.position.x, it.position.y)
            },
            measurements = measurements.map { m ->
                MeasurementDto(
                    x                  = m.x,
                    y                  = m.y,
                    rssi               = m.rssi,
                    bssid              = m.bssid,
                    channel            = m.channel,
                    band               = m.band,
                    pingGatewayMs      = m.pingGatewayMs,
                    pingInternetMs     = m.pingInternetMs,
                    neighboringNetworks = m.neighbors.map { n ->
                        NeighborNetworkDto(n.ssid, n.bssid, n.rssi, n.channel, n.band)
                    }
                )
            }
        )
    }
}
