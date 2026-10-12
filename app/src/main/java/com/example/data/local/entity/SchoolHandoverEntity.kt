package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "school_handovers")
data class SchoolHandoverEntity(
    @PrimaryKey val id: String,
    val childProfileId: String?,
    val childLabel: String,
    val schoolName: String,
    val authorizedGuardian: String,
    val scheduledAt: Long,
    /** SCHEDULED, AWAITING_PICKUP, VERIFIED, EXPIRED, REVIEW_REQUIRED, CANCELLED */
    val status: String,
    val verificationMethod: String = "ONE_TIME_PIN",
    /** Only a SHA-256 hash is persisted; the plain PIN is returned once to the creator. */
    val oneTimePinHash: String?,
    val pinExpiresAt: Long?,
    val verificationAttempts: Int = 0,
    val verifiedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)
