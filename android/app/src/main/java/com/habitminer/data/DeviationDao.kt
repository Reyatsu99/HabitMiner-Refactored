package com.habitminer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(deviation: DeviationEntity)

    @Query("SELECT * FROM deviations ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentDeviations(limit: Int = 20): Flow<List<DeviationEntity>>

    @Query("SELECT * FROM deviations WHERE timestamp >= :startOfDayMs ORDER BY timestamp DESC")
    fun getTodayDeviations(startOfDayMs: Long): Flow<List<DeviationEntity>>

    @Query("SELECT MAX(normalizedScore) FROM deviations WHERE timestamp >= :startOfDayMs")
    suspend fun getMaxScoreToday(startOfDayMs: Long): Float?

    @Query("DELETE FROM deviations WHERE timestamp < :timestampMs")
    suspend fun deleteOlderThan(timestampMs: Long)

    @Query("DELETE FROM deviations WHERE timestamp >= :startOfDayMs")
    suspend fun deleteSince(startOfDayMs: Long)

    @Query("DELETE FROM deviations")
    suspend fun deleteAll()

    @Query("DELETE FROM deviations WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM deviations ORDER BY timestamp ASC")
    fun getAllDeviations(): Flow<List<DeviationEntity>>
}
