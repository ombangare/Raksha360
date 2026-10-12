package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "child_safety_alerts")
data class ChildSafetyAlertEntity(
    @PrimaryKey val id: String,
    val childProfileId: String?,
    val relatedRecordId: String?,
    val type: String,
    val severity: String,
    val title: String,
    val message: String,
    val createdAt: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)
