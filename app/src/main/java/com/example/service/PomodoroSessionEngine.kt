package com.example.service

import com.example.data.PomidorSettings
import com.example.data.SessionStatus
import com.example.data.WorkSessionEntity
import com.example.data.WorkdayClock
import com.example.i18n.AppLocaleHelper

sealed class EngineAction {
    data class PersistSession(val session: WorkSessionEntity) : EngineAction()
    data class TriggerPomodoroComplete(val focusMinutes: Int, val breakMinutes: Int) : EngineAction()
    data object BreakFinished : EngineAction()
}

data class SessionEngineSnapshot(
    val phase: SessionPhase,
    val remainingSeconds: Int,
    val totalPhaseSeconds: Int,
    val consecutiveMisses: Int,
    val maxMissedChecks: Int
)

class PomodoroSessionEngine {

    var phase: SessionPhase = SessionPhase.IDLE
        private set

    var remainingSeconds: Int = 20 * 60
        private set

    var totalPhaseSeconds: Int = 20 * 60
        private set

    var consecutiveMisses: Int = 0
        private set

    private var sessionStartTimeMs: Long = 0L
    private var plannedFocusSeconds: Int = 20 * 60
    private var elapsedActiveSeconds: Int = 0
    private var totalMissedChecksInSession: Int = 0
    private var totalAwaySecondsInSession: Int = 0
    private var activeEngineUsed: String = ""
    private var activeCameraUsed: String = ""

    fun syncIdleDefaults(settings: PomidorSettings) {
        if (phase == SessionPhase.IDLE) {
            totalPhaseSeconds = settings.focusDurationMinutes * 60
            remainingSeconds = totalPhaseSeconds
        }
    }

    fun onDetectionCheck(
        personDetected: Boolean,
        engineLabel: String,
        settings: PomidorSettings,
        nowMs: Long
    ): List<EngineAction> {
        val actions = mutableListOf<EngineAction>()
        when (phase) {
            SessionPhase.IDLE -> {
                if (personDetected) {
                    val focusSec = settings.focusDurationMinutes * 60
                    phase = SessionPhase.FOCUSING
                    plannedFocusSeconds = focusSec
                    totalPhaseSeconds = focusSec
                    remainingSeconds = focusSec
                    sessionStartTimeMs = nowMs
                    elapsedActiveSeconds = 0
                    consecutiveMisses = 0
                    totalMissedChecksInSession = 0
                    totalAwaySecondsInSession = 0
                    activeEngineUsed = engineLabel
                    val isRu = AppLocaleHelper.isRussian(settings.appLanguage)
                    activeCameraUsed = settings.selectedCameraLabel.ifBlank { settings.cameraLens.label(isRu) }
                }
            }
            SessionPhase.FOCUSING, SessionPhase.GRACE_PERIOD -> {
                activeEngineUsed = engineLabel
                val isRu = AppLocaleHelper.isRussian(settings.appLanguage)
                if (personDetected) {
                    consecutiveMisses = 0
                    phase = SessionPhase.FOCUSING
                } else {
                    consecutiveMisses += 1
                    totalMissedChecksInSession += 1
                    totalAwaySecondsInSession += settings.checkIntervalSeconds
                    if (consecutiveMisses >= settings.maxMissedChecks) {
                        val note = if (isRu) {
                            "Сброс: $consecutiveMisses/${settings.maxMissedChecks} пропусков подряд"
                        } else {
                            "Reset: $consecutiveMisses/${settings.maxMissedChecks} consecutive misses"
                        }
                        val interrupted = buildSessionEntity(
                            endTimeMs = nowMs,
                            status = SessionStatus.INTERRUPTED_ABSENCE,
                            breakTriggered = false,
                            settings = settings,
                            notes = note
                        )
                        actions.add(EngineAction.PersistSession(interrupted))
                        resetToIdle(settings)
                    } else {
                        phase = SessionPhase.GRACE_PERIOD
                    }
                }
            }
            SessionPhase.BREAK -> {
                // During break, presence checks do not interrupt the rest countdown
            }
        }
        return actions
    }

    fun onOneSecondTick(settings: PomidorSettings, nowMs: Long): List<EngineAction> {
        val actions = mutableListOf<EngineAction>()
        when (phase) {
            SessionPhase.FOCUSING, SessionPhase.GRACE_PERIOD -> {
                elapsedActiveSeconds += 1
                remainingSeconds = (remainingSeconds - 1).coerceAtLeast(0)
                if (remainingSeconds == 0) {
                    val isRu = AppLocaleHelper.isRussian(settings.appLanguage)
                    val completed = buildSessionEntity(
                        endTimeMs = nowMs,
                        status = SessionStatus.COMPLETED_CONTINUOUS,
                        breakTriggered = true,
                        settings = settings,
                        notes = if (totalMissedChecksInSession == 0) {
                            if (isRu) "Непрерывная сессия без пропусков" else "Continuous session without misses"
                        } else {
                            if (isRu) {
                                "Завершена с допусками (пропусков: $totalMissedChecksInSession)"
                            } else {
                                "Completed with tolerance (misses: $totalMissedChecksInSession)"
                            }
                        }
                    )
                    actions.add(EngineAction.PersistSession(completed))
                    actions.add(
                        EngineAction.TriggerPomodoroComplete(
                            focusMinutes = settings.focusDurationMinutes,
                            breakMinutes = settings.breakDurationMinutes
                        )
                    )
                    val breakSec = settings.breakDurationMinutes * 60
                    phase = SessionPhase.BREAK
                    totalPhaseSeconds = breakSec
                    remainingSeconds = breakSec
                    consecutiveMisses = 0
                }
            }
            SessionPhase.BREAK -> {
                remainingSeconds = (remainingSeconds - 1).coerceAtLeast(0)
                if (remainingSeconds == 0) {
                    actions.add(EngineAction.BreakFinished)
                    resetToIdle(settings)
                }
            }
            SessionPhase.IDLE -> Unit
        }
        return actions
    }

    fun onManualStop(settings: PomidorSettings, nowMs: Long): List<EngineAction> {
        val actions = mutableListOf<EngineAction>()
        if ((phase == SessionPhase.FOCUSING || phase == SessionPhase.GRACE_PERIOD) && elapsedActiveSeconds > 0) {
            val isRu = AppLocaleHelper.isRussian(settings.appLanguage)
            val stopped = buildSessionEntity(
                endTimeMs = nowMs,
                status = SessionStatus.STOPPED_MANUAL,
                breakTriggered = false,
                settings = settings,
                notes = if (isRu) "Остановлено пользователем" else "Stopped manually"
            )
            actions.add(EngineAction.PersistSession(stopped))
        }
        resetToIdle(settings)
        return actions
    }

    fun snapshot(settings: PomidorSettings): SessionEngineSnapshot {
        return SessionEngineSnapshot(
            phase = phase,
            remainingSeconds = remainingSeconds,
            totalPhaseSeconds = totalPhaseSeconds,
            consecutiveMisses = consecutiveMisses,
            maxMissedChecks = settings.maxMissedChecks
        )
    }

    private fun resetToIdle(settings: PomidorSettings) {
        phase = SessionPhase.IDLE
        consecutiveMisses = 0
        elapsedActiveSeconds = 0
        totalMissedChecksInSession = 0
        totalAwaySecondsInSession = 0
        totalPhaseSeconds = settings.focusDurationMinutes * 60
        remainingSeconds = totalPhaseSeconds
    }

    private fun buildSessionEntity(
        endTimeMs: Long,
        status: String,
        breakTriggered: Boolean,
        settings: PomidorSettings,
        notes: String
    ): WorkSessionEntity {
        val start = if (sessionStartTimeMs > 0L) sessionStartTimeMs else endTimeMs
        val workdayDate = WorkdayClock.getWorkdayDate(start, settings.workdayStartHour)
        val withinWindow = WorkdayClock.isWithinWorkdayWindow(
            timestampMs = start,
            startHour = settings.workdayStartHour,
            endHour = settings.workdayEndHour
        )
        val isRu = AppLocaleHelper.isRussian(settings.appLanguage)
        return WorkSessionEntity(
            workdayDate = workdayDate,
            withinWorkdayWindow = withinWindow,
            startTimeMs = start,
            endTimeMs = endTimeMs,
            plannedFocusSeconds = plannedFocusSeconds,
            actualFocusSeconds = elapsedActiveSeconds.coerceAtLeast(1),
            status = status,
            missedChecksCount = totalMissedChecksInSession,
            awaySecondsEstimate = totalAwaySecondsInSession,
            breakTriggered = breakTriggered,
            breakDurationMinutes = if (breakTriggered) settings.breakDurationMinutes else 0,
            cameraFacing = activeCameraUsed.ifBlank { settings.cameraLens.label(isRu) },
            detectorEngine = activeEngineUsed.ifBlank { settings.detectorMode.label(isRu) },
            notes = notes
        )
    }
}
