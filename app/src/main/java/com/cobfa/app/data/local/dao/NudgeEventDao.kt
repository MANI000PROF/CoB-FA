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

    @Query("SELECT * FROM nudge_events ORDER BY timestamp DESC LIMIT 200")
    suspend fun getRecentEventsSnapshot(): List<NudgeEventEntity>

    @Query("SELECT * FROM nudge_events WHERE timestamp >= :since ORDER BY timestamp DESC")
    suspend fun getEventsSince(since: Long): List<NudgeEventEntity>

    @Query("SELECT * FROM nudge_events ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentEventsOnce(limit: Int = 200): List<NudgeEventEntity>

    @Query("""
        SELECT COUNT(*) FROM nudge_events
        WHERE type = :type AND category = :category AND action = 'dismiss' AND timestamp >= :since
        """)
    suspend fun countDismissedSince(type: String, category: String, since: Long): Int

}
