package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "evidence_vault")
data class EvidenceEntity(
    @PrimaryKey
    val id: String,
    val eventId: String,
    val title: String,
    val filePath: String,
    val sha256Checksum: String,
    val timestamp: Long = System.currentTimeMillis(),
    val durationSecs: Int = 0,
    val fileSizeBytes: Long = 0,
    val isVerified: Boolean = true
)
