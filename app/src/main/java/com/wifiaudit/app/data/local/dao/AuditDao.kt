package com.wifiaudit.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wifiaudit.app.data.local.entity.AuditEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AuditDao {

    @Query("SELECT * FROM audits ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<AuditEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(audit: AuditEntity)

    @Query("SELECT * FROM audits WHERE id = :id")
    suspend fun getById(id: String): AuditEntity?

    @Query("SELECT * FROM audits WHERE status = 'PENDING'")
    suspend fun getPending(): List<AuditEntity>

    @Query("UPDATE audits SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    @Delete
    suspend fun delete(audit: AuditEntity)
}
