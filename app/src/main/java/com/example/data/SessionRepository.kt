package com.example.data

import kotlinx.coroutines.flow.Flow

class SessionRepository(private val dao: WorkSessionDao) {

    fun observeSessionsForWorkday(workdayDate: String): Flow<List<WorkSessionEntity>> {
        return dao.observeSessionsForWorkday(workdayDate)
    }

    fun observeAllSessions(): Flow<List<WorkSessionEntity>> {
        return dao.observeAllSessions()
    }

    fun observeWorkdayDates(): Flow<List<String>> {
        return dao.observeWorkdayDates()
    }

    suspend fun getSessionsForWorkdayAsc(workdayDate: String): List<WorkSessionEntity> {
        return dao.getSessionsForWorkdayAsc(workdayDate)
    }

    suspend fun insertSession(session: WorkSessionEntity): Long {
        return dao.insertSession(session)
    }

    suspend fun deleteSessionById(sessionId: Long) {
        dao.deleteSessionById(sessionId)
    }

    suspend fun deleteWorkdaySessions(workdayDate: String) {
        dao.deleteWorkdaySessions(workdayDate)
    }
}
