package com.cobfa.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.cobfa.app.data.local.entity.NudgeEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NudgeEventDao {
    @Insert
    suspend fun insert(event: NudgeEventEntity)
    @Query("SELECT * FROM nudge_events ORDER BY timestamp DESC LIMIT 100")
    fun getRecentEvents(): Flow<List<NudgeEventEntity>>
}
