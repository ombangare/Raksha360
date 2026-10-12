package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.EmergencyEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EmergencyEventDao {
    @Query("SELECT * FROM emergency_events ORDER BY timestamp DESC")
    fun getAllEvents(): Flow<List<EmergencyEventEntity>>

    @Query("SELECT * FROM emergency_events WHERE status = 'ACTIVE' LIMIT 1")
    fun getActiveEmergencyFlow(): Flow<EmergencyEventEntity?>

    @Query("SELECT * FROM emergency_events WHERE status = 'ACTIVE' LIMIT 1")
    suspend fun getActiveEmergency(): EmergencyEventEntity?

    @Query("SELECT * FROM emergency_events WHERE id = :id")
    suspend fun getEventById(id: String): EmergencyEventEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: EmergencyEventEntity)

    @Update
    suspend fun updateEvent(event: EmergencyEventEntity)

    @Query("UPDATE emergency_events SET status = :status, resolvedAt = :resolvedAt WHERE id = :id")
    suspend fun updateEventStatus(id: String, status: String, resolvedAt: Long = System.currentTimeMillis())

    @Query("UPDATE emergency_events SET status = 'CANCELLED', resolvedAt = :resolvedAt WHERE status = 'ACTIVE'")
    suspend fun cancelAllActiveEvents(resolvedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM emergency_events")
    suspend fun deleteAllEvents()
}
