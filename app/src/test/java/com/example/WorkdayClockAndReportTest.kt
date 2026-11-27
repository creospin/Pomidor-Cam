package com.example

import com.example.data.SessionStatus
import com.example.data.WorkSessionEntity
import com.example.data.WorkdayClock
import com.example.data.WorkdayReportBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class WorkdayClockAndReportTest {

    private fun timestamp(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        return Calendar.getInstance().apply {
            set(year, month - 1, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    @Test
    fun workday06To04MapsEarlyMorningToPreviousWorkday() {
        val evening = timestamp(2026, 11, 25, 22, 15)
        val afterMidnight = timestamp(2026, 11, 26, 2, 45)
        val offShift = timestamp(2026, 11, 26, 4, 30)

        assertEquals("2026-11-25", WorkdayClock.getWorkdayDate(evening, startHour = 6))
        assertTrue(WorkdayClock.isWithinWorkdayWindow(evening, startHour = 6, endHour = 4))

        assertEquals("2026-11-25", WorkdayClock.getWorkdayDate(afterMidnight, startHour = 6))
        assertTrue(WorkdayClock.isWithinWorkdayWindow(afterMidnight, startHour = 6, endHour = 4))

        assertEquals("2026-11-25", WorkdayClock.getWorkdayDate(offShift, startHour = 6))
        assertFalse(WorkdayClock.isWithinWorkdayWindow(offShift, startHour = 6, endHour = 4))
    }

    @Test
    fun reportBuilderIncludesBothContinuousAndInterruptedSessions() {
        val sessions = listOf(
            WorkSessionEntity(
                id = 1,
                workdayDate = "2026-11-25",
                withinWorkdayWindow = true,
                startTimeMs = timestamp(2026, 11, 25, 9, 0),
                endTimeMs = timestamp(2026, 11, 25, 9, 20),
                plannedFocusSeconds = 1200,
                actualFocusSeconds = 1200,
                status = SessionStatus.COMPLETED_CONTINUOUS,
                missedChecksCount = 0,
                awaySecondsEstimate = 0,
                breakTriggered = true,
                breakDurationMinutes = 5,
                cameraFacing = "Фронтальная",
                detectorEngine = "YOLO26 ONNX"
            ),
            WorkSessionEntity(
                id = 2,
                workdayDate = "2026-11-25",
                withinWorkdayWindow = true,
                startTimeMs = timestamp(2026, 11, 25, 10, 0),
                endTimeMs = timestamp(2026, 11, 25, 10, 8),
                plannedFocusSeconds = 1200,
                actualFocusSeconds = 480,
                status = SessionStatus.INTERRUPTED_ABSENCE,
                missedChecksCount = 2,
                awaySecondsEstimate = 20,
                breakTriggered = false,
                breakDurationMinutes = 0,
                cameraFacing = "Фронтальная",
                detectorEngine = "YOLO26 ONNX"
            )
        )

        val payloadEn = WorkdayReportBuilder.buildPayload("2026-11-25", sessions, 6, 4)
        assertEquals(2, payloadEn.summary.totalSessions)
        assertEquals(1, payloadEn.summary.completedContinuousCount)
        assertEquals(1, payloadEn.summary.interruptedAbsenceCount)
        assertEquals(1680, payloadEn.summary.totalFocusSeconds)
        assertEquals(5, payloadEn.summary.totalBreakMinutes)
        assertTrue(payloadEn.markdownString.contains("# Pomidor Cam — Full Workday Report"))
        assertTrue(payloadEn.markdownString.contains("Completed (continuous)"))
        assertTrue(payloadEn.markdownString.contains("Interrupted (away)"))
        assertTrue(payloadEn.jsonString.contains("\"app\": \"Pomidor Cam\""))
        assertTrue(payloadEn.jsonString.contains("\"workday_date\": \"2026-11-25\""))

        val payloadRu = WorkdayReportBuilder.buildPayload("2026-11-25", sessions, 6, 4, isRu = true)
        assertTrue(payloadRu.markdownString.contains("# Pomidor Cam — Полный отчёт за рабочий день"))
        assertTrue(payloadRu.markdownString.contains("Завершена (непрерывно)"))
        assertTrue(payloadRu.markdownString.contains("Прервана (уход)"))
    }
}
