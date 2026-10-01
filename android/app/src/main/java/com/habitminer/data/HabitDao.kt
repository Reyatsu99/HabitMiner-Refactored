package com.habitminer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(habit: DiscoveredHabitEntity)

    @Query("SELECT * FROM discovered_habits ORDER BY confidence DESC")
    fun getAllHabits(): Flow<List<DiscoveredHabitEntity>>

    @Query("SELECT * FROM discovered_habits WHERE dayType IN (:dayType, 'ANY') AND timeSlot = :timeSlot ORDER BY confidence DESC")
    suspend fun getHabitsForTimeBin(
        dayType: String,
        timeSlot: String,
    ): List<DiscoveredHabitEntity>

    @Query("DELETE FROM discovered_habits")
    suspend fun deleteAll()

    @Query("DELETE FROM discovered_habits WHERE lastSeenAt < :timestampMs")
    suspend fun deleteOlderThan(timestampMs: Long)

    @Query("SELECT COUNT(*) FROM discovered_habits")
    fun getHabitCount(): Flow<Int>
}
