package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

object SessionStatus {
    const val COMPLETED_CONTINUOUS = "COMPLETED_CONTINUOUS"
    const val INTERRUPTED_ABSENCE = "INTERRUPTED_ABSENCE"
    const val STOPPED_MANUAL = "STOPPED_MANUAL"
}

@Entity(tableName = "work_sessions")
data class WorkSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workdayDate: String,
    val withinWorkdayWindow: Boolean,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val plannedFocusSeconds: Int,
    val actualFocusSeconds: Int,
    val status: String,
    val missedChecksCount: Int,
    val awaySecondsEstimate: Int,
    val breakTriggered: Boolean,
    val breakDurationMinutes: Int,
    val cameraFacing: String,
    val detectorEngine: String,
    val notes: String = ""
)
