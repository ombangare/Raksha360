package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.EvidenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EvidenceDao {
    @Query("SELECT * FROM evidence_vault ORDER BY timestamp DESC")
    fun getAllEvidence(): Flow<List<EvidenceEntity>>

    @Query("SELECT * FROM evidence_vault WHERE id = :id")
    suspend fun getEvidenceById(id: String): EvidenceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvidence(evidence: EvidenceEntity)

    @Update
    suspend fun updateEvidence(evidence: EvidenceEntity)

    @Query("DELETE FROM evidence_vault WHERE id = :id")
    suspend fun deleteEvidenceById(id: String)

    @Query("DELETE FROM evidence_vault")
    suspend fun deleteAllEvidence()
}
