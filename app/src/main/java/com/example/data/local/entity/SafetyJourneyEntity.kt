package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "safety_journeys")
data class SafetyJourneyEntity(
    @PrimaryKey val id: String,
    val childProfileId: String?,
    val childLabel: String,
    val originLabel: String,
    val destinationLabel: String,
    val startedAt: Long,
    val expectedArrivalAt: Long,
    val lastCheckInAt: Long?,
    val lastLatitude: Double?,
    val lastLongitude: Double?,
    /** ACTIVE, SAFE_ARRIVAL, OVERDUE, PAUSED, CANCELLED */
    val status: String = "ACTIVE",
    val createdAt: Long = System.currentTimeMillis()
)
