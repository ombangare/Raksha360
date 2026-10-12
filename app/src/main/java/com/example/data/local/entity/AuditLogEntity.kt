package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "audit_logs")
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val action: String,
    val category: String = "SYSTEM", // "VOICE_SECURITY", "AUTHENTICATION", "EMERGENCY_DISPATCH", "SYSTEM_STATE", "DATA_INTEGRITY"
    val detail: String,
    val severity: String = "INFO", // "INFO", "WARNING", "ALERT", "CRITICAL"
    val sessionId: String = "",
    val networkState: String = "ONLINE" // "ONLINE", "OFFLINE"
)
