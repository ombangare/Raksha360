package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.ChildProfileEntity
import com.example.data.local.entity.ChildSafetyAlertEntity
import com.example.data.local.entity.SafetyJourneyEntity
import com.example.data.local.entity.SchoolHandoverEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChildSafetyDao {
    @Query("SELECT * FROM child_profiles ORDER BY createdAt DESC")
    fun observeChildren(): Flow<List<ChildProfileEntity>>

    @Query("SELECT * FROM child_profiles WHERE id = :id LIMIT 1")
    suspend fun getChild(id: String): ChildProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveChild(profile: ChildProfileEntity)

    @Update
    suspend fun updateChild(profile: ChildProfileEntity)

    @Query("SELECT * FROM safety_journeys ORDER BY createdAt DESC")
    fun observeJourneys(): Flow<List<SafetyJourneyEntity>>

    @Query("SELECT * FROM safety_journeys WHERE id = :id LIMIT 1")
    suspend fun getJourney(id: String): SafetyJourneyEntity?

    @Query("SELECT * FROM safety_journeys WHERE childProfileId = :childId AND status = 'ACTIVE' ORDER BY startedAt DESC LIMIT 1")
    suspend fun getActiveJourneyForChild(childId: String): SafetyJourneyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveJourney(journey: SafetyJourneyEntity)

    @Update
    suspend fun updateJourney(journey: SafetyJourneyEntity)

    @Query("SELECT * FROM school_handovers ORDER BY createdAt DESC")
    fun observeHandovers(): Flow<List<SchoolHandoverEntity>>

    @Query("SELECT * FROM school_handovers WHERE id = :id LIMIT 1")
    suspend fun getHandover(id: String): SchoolHandoverEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveHandover(handover: SchoolHandoverEntity)

    @Update
    suspend fun updateHandover(handover: SchoolHandoverEntity)

    @Query("SELECT * FROM child_safety_alerts ORDER BY createdAt DESC LIMIT 100")
    fun observeAlerts(): Flow<List<ChildSafetyAlertEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAlert(alert: ChildSafetyAlertEntity)

    @Query("UPDATE child_safety_alerts SET isRead = 1 WHERE id = :id")
    suspend fun markAlertRead(id: String)
}
