package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: WorkSessionEntity): Long

    @Query("SELECT * FROM work_sessions WHERE workdayDate = :workdayDate ORDER BY startTimeMs DESC")
    fun observeSessionsForWorkday(workdayDate: String): Flow<List<WorkSessionEntity>>

    @Query("SELECT * FROM work_sessions WHERE workdayDate = :workdayDate ORDER BY startTimeMs ASC")
    suspend fun getSessionsForWorkdayAsc(workdayDate: String): List<WorkSessionEntity>

    @Query("SELECT * FROM work_sessions ORDER BY startTimeMs DESC")
    fun observeAllSessions(): Flow<List<WorkSessionEntity>>

    @Query("SELECT DISTINCT workdayDate FROM work_sessions ORDER BY workdayDate DESC")
    fun observeWorkdayDates(): Flow<List<String>>

    @Query("DELETE FROM work_sessions WHERE workdayDate = :workdayDate")
    suspend fun deleteWorkdaySessions(workdayDate: String)

    @Query("DELETE FROM work_sessions WHERE id = :sessionId")
    suspend fun deleteSessionById(sessionId: Long)
}
