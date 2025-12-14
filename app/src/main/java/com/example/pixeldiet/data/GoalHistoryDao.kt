package com.example.pixeldiet.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalHistoryDao {

    // -------------------------------
    // Insert / Update
    // -------------------------------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(entity: GoalHistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<GoalHistoryEntity>)

    // -------------------------------
    // Single day
    // -------------------------------

    @Query("""
        SELECT * FROM goal_history 
        WHERE uid = :uid AND date = :date
        LIMIT 1
    """)
    suspend fun getGoalsOnce(uid: String, date: String): GoalHistoryEntity?

    // -------------------------------
    // Range (캘린더 / 스트릭 계산용)
    // -------------------------------

    @Query("""
        SELECT * FROM goal_history
        WHERE uid = :uid
          AND date BETWEEN :fromDate AND :toDate
        ORDER BY date ASC
    """)
    fun getGoalsInRange(
        uid: String,
        fromDate: String,
        toDate: String
    ): Flow<List<GoalHistoryEntity>>

    // -------------------------------
    // Debug / Utility
    // -------------------------------

    @Query("DELETE FROM goal_history WHERE uid = :uid")
    suspend fun deleteAllForUser(uid: String)
}
