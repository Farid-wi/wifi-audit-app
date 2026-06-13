package com.wifiaudit.app.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.wifiaudit.app.data.local.dao.SavedPlanDao
import com.wifiaudit.app.data.local.entity.SavedPlanEntity
import com.wifiaudit.app.domain.model.CanvasRoom
import com.wifiaudit.app.domain.model.Position
import com.wifiaudit.app.domain.model.RepeaterPosition
import com.wifiaudit.app.domain.model.SavedPlan
import com.wifiaudit.app.domain.repository.SavedPlanRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class SavedPlanRepositoryImpl @Inject constructor(
    private val dao: SavedPlanDao,
    private val gson: Gson
) : SavedPlanRepository {

    override fun observeAll(): Flow<List<SavedPlan>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun save(plan: SavedPlan) = dao.upsert(plan.toEntity())

    override suspend fun rename(planId: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isNotEmpty()) dao.rename(planId, trimmed)
    }

    override suspend fun duplicate(planId: String) {
        val original = dao.getById(planId) ?: return
        dao.upsert(
            original.copy(
                id        = java.util.UUID.randomUUID().toString(),
                name      = "${original.name} (copie)",
                createdAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun delete(planId: String) = dao.delete(planId)

    private fun SavedPlanEntity.toDomain(): SavedPlan {
        val roomType     = object : TypeToken<List<CanvasRoom>>() {}.type
        val repeaterType = object : TypeToken<List<RepeaterPosition>>() {}.type
        return SavedPlan(
            id                = id,
            name              = name,
            planImagePath     = planImagePath,
            rooms             = gson.fromJson(roomsJson, roomType) ?: emptyList(),
            gatewayPosition   = if (gatewayX != null && gatewayY != null) Position(gatewayX, gatewayY) else null,
            repeaterPositions = gson.fromJson(repeaterPositionsJson, repeaterType) ?: emptyList(),
            createdAt         = createdAt
        )
    }

    private fun SavedPlan.toEntity() = SavedPlanEntity(
        id                   = id,
        name                 = name,
        planImagePath        = planImagePath,
        roomsJson            = gson.toJson(rooms),
        gatewayX             = gatewayPosition?.x,
        gatewayY             = gatewayPosition?.y,
        repeaterPositionsJson = gson.toJson(repeaterPositions),
        createdAt            = createdAt
    )
}
