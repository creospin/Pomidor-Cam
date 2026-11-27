package com.example

import com.example.data.PomidorSettings
import com.example.data.SessionStatus
import com.example.data.WorkSessionEntity
import com.example.data.WorkdayClock
import com.example.network.AutoExportOutcome
import com.example.network.LanDispatchResult
import com.example.network.MorningAutoExportManager
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class MorningAutoExportTest {

    private fun calMs(year: Int, monthZeroBased: Int, day: Int, hour: Int, minute: Int = 0): Long {
        return Calendar.getInstance().apply {
            set(year, monthZeroBased, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun sampleSession(workdayDate: String, startMs: Long): WorkSessionEntity {
        return WorkSessionEntity(
            id = 1L,
            workdayDate = workdayDate,
            withinWorkdayWindow = true,
            startTimeMs = startMs,
            endTimeMs = startMs + 20 * 60_000L,
            plannedFocusSeconds = 1200,
            actualFocusSeconds = 1200,
            status = SessionStatus.COMPLETED_CONTINUOUS,
            missedChecksCount = 0,
            awaySecondsEstimate = 0,
            breakTriggered = true,
            breakDurationMinutes = 5,
            cameraFacing = "Фронтальная",
            detectorEngine = "YOLO26 ONNX"
        )
    }

    @Test
    fun getPreviousWorkdayDate_handlesMorningAndLateNightShiftBoundaries() {
        val morning = calMs(2026, Calendar.NOVEMBER, 27, 8, 15)
        assertEquals("2026-11-26", WorkdayClock.getPreviousWorkdayDate(morning, startHour = 6))

        // At 02:30 on Nov 27 (before 06:00 start), current workday is still Nov 26 -> previous is Nov 25
        val lateNight = calMs(2026, Calendar.NOVEMBER, 27, 2, 30)
        assertEquals("2026-11-25", WorkdayClock.getPreviousWorkdayDate(lateNight, startHour = 6))

        // Year boundary: morning of Jan 1 2026 -> previous workday is Dec 31 2025
        val newYearMorning = calMs(2026, Calendar.JANUARY, 1, 9, 0)
        assertEquals("2025-12-31", WorkdayClock.getPreviousWorkdayDate(newYearMorning, startHour = 6))
    }

    @Test
    fun evaluateWith_waitsUntilMorningHourAndThenExportsPreviousWorkdayOnce() = runTest {
        val settingsBeforeHour = PomidorSettings(
            autoMorningExportEnabled = true,
            autoMorningExportHour = 8,
            workdayStartHour = 6,
            pcHost = "192.168.1.50"
        )
        val earlyMorning = calMs(2026, Calendar.NOVEMBER, 27, 7, 45)
        val earlyOutcome = MorningAutoExportManager.evaluateWith(
            settings = settingsBeforeHour,
            forceNow = false,
            nowMs = earlyMorning,
            loadSessions = { emptyList() },
            sendReport = { _, _, _ -> LanDispatchResult(true, "ok") },
            saveOutcome = { _, _ -> }
        )
        assertTrue(earlyOutcome is AutoExportOutcome.NotTimeYet)

        // Now at 08:10 -> exports 2026-11-26
        val afterEight = calMs(2026, Calendar.NOVEMBER, 27, 8, 10)
        var sentDay: String? = null
        var savedDay: String? = null
        val exportedOutcome = MorningAutoExportManager.evaluateWith(
            settings = settingsBeforeHour,
            forceNow = false,
            nowMs = afterEight,
            loadSessions = { day ->
                if (day == "2026-11-26") listOf(sampleSession(day, afterEight - 86_400_000L)) else emptyList()
            },
            sendReport = { _, day, _ ->
                sentDay = day
                LanDispatchResult(true, "Отчёт за $day выгружен")
            },
            saveOutcome = { day, _ -> savedDay = day }
        )
        assertTrue(exportedOutcome is AutoExportOutcome.Exported)
        assertEquals("2026-11-26", sentDay)
        assertEquals("2026-11-26", savedDay)

        // Calling again same day with lastAutoExportedWorkday == "2026-11-26" skips duplicate upload
        val duplicateOutcome = MorningAutoExportManager.evaluateWith(
            settings = settingsBeforeHour.copy(lastAutoExportedWorkday = "2026-11-26"),
            forceNow = false,
            nowMs = afterEight + 3_600_000L,
            loadSessions = { error("Should not load sessions when already exported") },
            sendReport = { _, _, _ -> error("Should not re-send") },
            saveOutcome = { _, _ -> }
        )
        assertEquals(AutoExportOutcome.AlreadyExported("2026-11-26"), duplicateOutcome)
    }

    @Test
    fun evaluateWith_keepsRetryPendingWhenPcIsOffline() = runTest {
        val afterEight = calMs(2026, Calendar.NOVEMBER, 27, 8, 30)
        var savedDay: String? = "SENTINEL"
        var savedStatus = ""

        val outcome = MorningAutoExportManager.evaluateWith(
            settings = PomidorSettings(
                autoMorningExportEnabled = true,
                autoMorningExportHour = 8,
                pcHost = "192.168.1.50"
            ),
            forceNow = false,
            nowMs = afterEight,
            loadSessions = { day -> listOf(sampleSession(day, afterEight - 86_400_000L)) },
            sendReport = { _, _, _ -> LanDispatchResult(false, "Connection refused") },
            saveOutcome = { day, status ->
                savedDay = day
                savedStatus = status
            }
        )

        assertTrue(outcome is AutoExportOutcome.RetryLater)
        assertNull(savedDay)
        assertTrue(savedStatus.contains("2026-11-26"))
    }
}
