package com.cobfa.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "points_events",
    indices = [Index(value = ["sourceNudgeId"], unique = true)]
)
data class PointsEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sourceNudgeId: Long, // non-null
    val delta: Int,
    val reason: String,
    val details: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
