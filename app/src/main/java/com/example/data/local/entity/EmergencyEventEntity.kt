package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "emergency_events")
data class EmergencyEventEntity(
    @PrimaryKey
    val id: String, // UUID string
    val timestamp: Long = System.currentTimeMillis(),
    val triggerType: String, // "VOICE_SOS", "PANIC_BUTTON", "JOURNEY_TIMEOUT", "DEMO_SIMULATION"
    val phraseDetected: String = "",
    val speakerScore: Float = 0f,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accuracyMeters: Float = 0f,
    val locationDescription: String = "",
    val status: String = "ACTIVE", // "ACTIVE", "RESOLVED", "CANCELLED"
    val trustedContactsNotifiedCount: Int = 0,
    val policeNotified: Boolean = false,
    val policeTrackingId: String? = null,
    val evidenceAudioPath: String? = null,
    val evidenceSha256: String? = null,
    val resolvedAt: Long? = null,
    val isDemoMode: Boolean = false
)
