package com.example.data.repository

import com.example.data.local.dao.EvidenceDao
import com.example.data.local.entity.EvidenceEntity
import com.example.data.security.KeystoreCryptoManager
import kotlinx.coroutines.flow.Flow
import java.io.File

class EvidenceRepository(
    private val dao: EvidenceDao,
    private val cryptoManager: KeystoreCryptoManager
) {
    val allEvidence: Flow<List<EvidenceEntity>> = dao.getAllEvidence()

    suspend fun addEvidence(
        id: String,
        eventId: String,
        title: String,
        filePath: String,
        sha256Checksum: String,
        durationSecs: Int,
        fileSizeBytes: Long
    ) {
        val entity = EvidenceEntity(
            id = id,
            eventId = eventId,
            title = title,
            filePath = filePath,
            sha256Checksum = sha256Checksum,
            timestamp = System.currentTimeMillis(),
            durationSecs = durationSecs,
            fileSizeBytes = fileSizeBytes,
            isVerified = true
        )
        dao.insertEvidence(entity)
    }

    suspend fun verifyIntegrity(evidenceId: String): Boolean {
        val evidence = dao.getEvidenceById(evidenceId) ?: return false
        val file = File(evidence.filePath)
        if (!file.exists()) return false

        val calculatedHash = cryptoManager.computeSha256(file)
        val matches = calculatedHash.equals(evidence.sha256Checksum, ignoreCase = true)
        if (evidence.isVerified != matches) {
            dao.updateEvidence(evidence.copy(isVerified = matches))
        }
        return matches
    }

    suspend fun deleteEvidence(id: String) {
        val evidence = dao.getEvidenceById(id)
        if (evidence != null) {
            try {
                val f = File(evidence.filePath)
                if (f.exists()) f.delete()
            } catch (_: Exception) {}
        }
        dao.deleteEvidenceById(id)
    }
}
