package com.cobfa.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cobfa.app.data.local.entity.PointsEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PointsDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(event: PointsEventEntity): Long

    @Query("SELECT IFNULL(SUM(delta), 0) FROM points_events")
    fun observePointsBalance(): Flow<Int>

    @Query("SELECT * FROM points_events ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecentPoints(limit: Int = 100): Flow<List<PointsEventEntity>>

    @Query("SELECT IFNULL(SUM(delta), 0) FROM points_events")
    suspend fun getPointsBalance(): Int

    @Query("SELECT COUNT(*) FROM points_events")
    suspend fun countAll(): Int

    @Query("SELECT COUNT(*) FROM points_events WHERE reason = :reason")
    suspend fun countByReason(reason: String): Int

}
