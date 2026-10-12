package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.OfflineQueueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OfflineQueueDao {
    @Query("SELECT * FROM offline_emergency_queue WHERE syncStatus != 'SYNCED' ORDER BY createdAt ASC")
    fun getPendingQueue(): Flow<List<OfflineQueueEntity>>

    @Query("SELECT * FROM offline_emergency_queue WHERE syncStatus != 'SYNCED' ORDER BY createdAt ASC")
    suspend fun getPendingQueueList(): List<OfflineQueueEntity>

    @Query("SELECT COUNT(*) FROM offline_emergency_queue WHERE syncStatus != 'SYNCED'")
    fun getPendingCountFlow(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueueItem(item: OfflineQueueEntity): Long

    @Update
    suspend fun updateItem(item: OfflineQueueEntity)

    @Query("UPDATE offline_emergency_queue SET syncStatus = 'SYNCED' WHERE id = :id")
    suspend fun markSynced(id: Long)

    @Query("UPDATE offline_emergency_queue SET retryCount = retryCount + 1, lastAttemptAt = :time, syncStatus = :status WHERE id = :id")
    suspend fun updateRetry(id: Long, time: Long, status: String)

    @Query("DELETE FROM offline_emergency_queue WHERE syncStatus = 'SYNCED'")
    suspend fun clearSynced()
}
