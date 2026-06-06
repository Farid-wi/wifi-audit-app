package com.wifiaudit.app.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.wifiaudit.app.data.local.dao.SavedPlanDao
import com.wifiaudit.app.data.local.entity.SavedPlanEntity
import com.wifiaudit.app.domain.model.CanvasRoom
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

    override suspend fun delete(planId: String) = dao.delete(planId)

    private fun SavedPlanEntity.toDomain(): SavedPlan {
        val type = object : TypeToken<List<CanvasRoom>>() {}.type
        return SavedPlan(
            id            = id,
            name          = name,
            planImagePath = planImagePath,
            rooms         = gson.fromJson(roomsJson, type) ?: emptyList(),
            createdAt     = createdAt
        )
    }

    private fun SavedPlan.toEntity() = SavedPlanEntity(
        id            = id,
        name          = name,
        planImagePath = planImagePath,
        roomsJson     = gson.toJson(rooms),
        createdAt     = createdAt
    )
}
