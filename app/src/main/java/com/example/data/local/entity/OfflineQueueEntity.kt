package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "offline_emergency_queue")
data class OfflineQueueEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val eventId: String,
    val payloadType: String, // "EVENT_TRIGGER", "LOCATION_BREADCRUMB", "CONTACT_ALERT", "POLICE_DISPATCH"
    val payloadJsonEncrypted: String,
    val createdAt: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val lastAttemptAt: Long = 0,
    val syncStatus: String = "PENDING" // "PENDING", "RETRYING", "SYNCED", "FAILED"
)
