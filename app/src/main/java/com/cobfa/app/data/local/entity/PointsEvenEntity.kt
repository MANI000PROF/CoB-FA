package com.cobfa.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "points_events",
    indices = [
        Index(value = ["sourceNudgeId"], unique = true)
    ]
)
data class PointsEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // To prevent double-awarding points for the same nudge event.
    val sourceNudgeId: Long?,

    val delta: Int,               // +10, +5, -5
    val reason: String,           // "UNDER_BUDGET_DAY", "IMPULSE_SKIPPED", "BUDGET_EXCEEDED"
    val details: String? = null,  // merchant/category
    val timestamp: Long = System.currentTimeMillis()
)
