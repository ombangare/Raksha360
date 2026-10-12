package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Minimal local child profile. Use an alias; do not store unnecessary identity data here. */
@Entity(tableName = "child_profiles")
data class ChildProfileEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val ageBand: String,
    val schoolName: String,
    val guardianLabel: String,
    val safePlaceName: String,
    val safePlaceLatitude: Double?,
    val safePlaceLongitude: Double?,
    val geofenceRadiusMeters: Int = 150,
    val locationSharingEnabled: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val locationConsentAt: Long? = null
)
