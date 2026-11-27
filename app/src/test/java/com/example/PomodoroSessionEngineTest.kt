package com.example

import com.example.data.PomidorSettings
import com.example.data.SessionStatus
import com.example.service.EngineAction
import com.example.service.PomodoroSessionEngine
import com.example.service.SessionPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PomodoroSessionEngineTest {

    @Test
    fun idleRemainsIdleWhenNoPersonDetected() {
        val engine = PomodoroSessionEngine()
        val settings = PomidorSettings(focusDurationMinutes = 20, maxMissedChecks = 2)
        val actions = engine.onDetectionCheck(
            personDetected = false,
            engineLabel = "YOLO26 ONNX",
            settings = settings,
            nowMs = 1_000_000L
        )
        assertTrue(actions.isEmpty())
        assertEquals(SessionPhase.IDLE, engine.phase)
    }

    @Test
    fun personAppearsStartsFocusAndSurvivesGracePeriodUpToConfiguredThreshold() {
        val engine = PomodoroSessionEngine()
        val settings = PomidorSettings(
            focusDurationMinutes = 20,
            checkIntervalSeconds = 10,
            maxMissedChecks = 3
        )

        engine.onDetectionCheck(true, "YOLO26 ONNX", settings, 10_000L)
        assertEquals(SessionPhase.FOCUSING, engine.phase)
        assertEquals(1200, engine.remainingSeconds)

        // 2 consecutive misses (< 3) enter GRACE_PERIOD without resetting
        engine.onDetectionCheck(false, "YOLO26 ONNX", settings, 20_000L)
        assertEquals(SessionPhase.GRACE_PERIOD, engine.phase)
        assertEquals(1, engine.consecutiveMisses)

        engine.onDetectionCheck(false, "YOLO26 ONNX", settings, 30_000L)
        assertEquals(SessionPhase.GRACE_PERIOD, engine.phase)
        assertEquals(2, engine.consecutiveMisses)

        // Person returns -> resets consecutiveMisses and resumes FOCUSING
        engine.onDetectionCheck(true, "YOLO26 ONNX", settings, 40_000L)
        assertEquals(SessionPhase.FOCUSING, engine.phase)
        assertEquals(0, engine.consecutiveMisses)

        // Now 3 misses in a row -> logs INTERRUPTED_ABSENCE and resets to IDLE
        engine.onDetectionCheck(false, "YOLO26 ONNX", settings, 50_000L)
        engine.onDetectionCheck(false, "YOLO26 ONNX", settings, 60_000L)
        val finalActions = engine.onDetectionCheck(false, "YOLO26 ONNX", settings, 70_000L)

        assertEquals(SessionPhase.IDLE, engine.phase)
        val persisted = finalActions.filterIsInstance<EngineAction.PersistSession>().single()
        assertEquals(SessionStatus.INTERRUPTED_ABSENCE, persisted.session.status)
        assertEquals(5, persisted.session.missedChecksCount)
    }

    @Test
    fun continuousFocusCompletesAndTriggersBreakSignal() {
        val engine = PomodoroSessionEngine()
        val settings = PomidorSettings(
            focusDurationMinutes = 1,
            breakDurationMinutes = 5,
            maxMissedChecks = 2
        )

        engine.onDetectionCheck(true, "YOLO26 TFLite", settings, 100_000L)
        assertEquals(SessionPhase.FOCUSING, engine.phase)

        var collected = emptyList<EngineAction>()
        repeat(60) { idx ->
            val actions = engine.onOneSecondTick(settings, 100_000L + (idx + 1) * 1000L)
            if (actions.isNotEmpty()) collected = actions
        }

        assertEquals(SessionPhase.BREAK, engine.phase)
        assertEquals(300, engine.remainingSeconds)
        val persisted = collected.filterIsInstance<EngineAction.PersistSession>().single()
        assertEquals(SessionStatus.COMPLETED_CONTINUOUS, persisted.session.status)
        assertTrue(persisted.session.breakTriggered)
        val trigger = collected.filterIsInstance<EngineAction.TriggerPomodoroComplete>().single()
        assertEquals(1, trigger.focusMinutes)
        assertEquals(5, trigger.breakMinutes)
    }
}
