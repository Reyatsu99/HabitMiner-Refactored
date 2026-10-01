package com.habitminer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BaselineDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(baseline: BaselineEntity)

    @Query("SELECT * FROM baseline WHERE timeBin = :timeBin")
    suspend fun getBaseline(timeBin: String): BaselineEntity?

    @Query("SELECT * FROM baseline ORDER BY timeBin ASC")
    fun getAllBaselines(): Flow<List<BaselineEntity>>

    @Query("SELECT COUNT(*) FROM baseline")
    suspend fun getBaselineCount(): Int

    @Query("DELETE FROM baseline")
    suspend fun deleteAll()

    @Query("DELETE FROM baseline WHERE updatedAt < :timestampMs")
    suspend fun deleteOlderThan(timestampMs: Long)
}
