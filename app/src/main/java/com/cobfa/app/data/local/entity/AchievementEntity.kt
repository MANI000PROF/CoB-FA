package com.cobfa.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "achievements",
    indices = [Index(value = ["key"], unique = true)]
)
data class AchievementEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val key: String,              // "IMPULSE_SLAYER", ...
    val title: String,
    val description: String,
    val unlockedAt: Long = System.currentTimeMillis()
)
