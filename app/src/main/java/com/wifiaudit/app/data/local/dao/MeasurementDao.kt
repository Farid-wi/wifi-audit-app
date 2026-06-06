package com.wifiaudit.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wifiaudit.app.data.local.entity.MeasurementEntity

@Dao
interface MeasurementDao {

    @Query("SELECT * FROM measurements WHERE auditId = :auditId")
    suspend fun getForAudit(auditId: String): List<MeasurementEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(measurement: MeasurementEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(measurements: List<MeasurementEntity>)

    @Query("DELETE FROM measurements WHERE auditId = :auditId")
    suspend fun deleteForAudit(auditId: String)
}
