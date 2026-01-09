package com.cobfa.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "nudge_events")
data class NudgeEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: String,      // "merchant_3x", "budget_100"
    val category: String,  // "Zomato", "Groceries"
    val action: String?,   // "dismiss", "adjust", null
    val timestamp: Long = System.currentTimeMillis()
)
