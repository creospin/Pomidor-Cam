package com.example.network

import android.content.Context
import com.example.data.PomidorDatabase
import com.example.data.PomidorSettings
import com.example.data.SessionRepository
import com.example.data.SettingsRepository
import com.example.data.WorkSessionEntity
import com.example.data.WorkdayClock
import com.example.data.WorkdayReportBuilder
import com.example.i18n.AppLocaleHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed class AutoExportOutcome {
    data object Disabled : AutoExportOutcome()
    data class NotTimeYet(val targetHour: Int) : AutoExportOutcome()
    data class AlreadyExported(val workdayDate: String) : AutoExportOutcome()
    data class NoSessions(val workdayDate: String) : AutoExportOutcome()
    data class Exported(val workdayDate: String, val message: String) : AutoExportOutcome()
    data class RetryLater(val workdayDate: String, val message: String) : AutoExportOutcome()
    data class Failed(val message: String) : AutoExportOutcome()
}

object MorningAutoExportManager {

    private val exportMutex = Mutex()

    suspend fun evaluateAndExportIfDue(
        context: Context,
        forceNow: Boolean = false,
        nowMs: Long = System.currentTimeMillis()
    ): AutoExportOutcome {
        val appCtx = context.applicationContext
        val settingsRepo = SettingsRepository(appCtx)
        val sessionRepo = SessionRepository(PomidorDatabase.getInstance(appCtx).workSessionDao())
        val lanNotifier = LanNotifier()

        return exportMutex.withLock {
            val settings = settingsRepo.settingsFlow.first()
            evaluateWith(
                settings = settings,
                forceNow = forceNow,
                nowMs = nowMs,
                loadSessions = { day -> sessionRepo.getSessionsForWorkdayAsc(day) },
                sendReport = { s, day, json -> lanNotifier.sendWorkdayReport(s, day, json) },
                saveOutcome = { exportedDay, status ->
                    settingsRepo.recordAutoExportOutcome(exportedDay, status)
                }
            )
        }
    }

    internal suspend fun evaluateWith(
        settings: PomidorSettings,
        forceNow: Boolean,
        nowMs: Long,
        loadSessions: suspend (String) -> List<WorkSessionEntity>,
        sendReport: suspend (PomidorSettings, String, String) -> LanDispatchResult,
        saveOutcome: suspend (String?, String) -> Unit
    ): AutoExportOutcome {
        if (!forceNow && !settings.autoMorningExportEnabled) {
            return AutoExportOutcome.Disabled
        }

        val currentHour = WorkdayClock.getCurrentHour(nowMs)
        if (!forceNow && currentHour < settings.autoMorningExportHour) {
            return AutoExportOutcome.NotTimeYet(settings.autoMorningExportHour)
        }

        val targetWorkday = WorkdayClock.getPreviousWorkdayDate(nowMs, settings.workdayStartHour)
        if (!forceNow && settings.lastAutoExportedWorkday == targetWorkday) {
            return AutoExportOutcome.AlreadyExported(targetWorkday)
        }

        val isRu = AppLocaleHelper.isRussian(settings.appLanguage)
        val sessionsAsc = loadSessions(targetWorkday)
        if (sessionsAsc.isEmpty()) {
            val msg = if (isRu) {
                "За $targetWorkday сессий не было (пропуск)"
            } else {
                "No sessions recorded on $targetWorkday (skipped)"
            }
            saveOutcome(targetWorkday, msg)
            return AutoExportOutcome.NoSessions(targetWorkday)
        }

        if (settings.pcHost.isBlank()) {
            val msg = if (isRu) {
                "Укажите IP ПК для автовыгрузки за $targetWorkday"
            } else {
                "Set PC IP address to auto-export $targetWorkday"
            }
            saveOutcome(null, msg)
            return AutoExportOutcome.Failed(msg)
        }

        val payload = WorkdayReportBuilder.buildPayload(
            workdayDate = targetWorkday,
            sessionsAsc = sessionsAsc,
            startHour = settings.workdayStartHour,
            endHour = settings.workdayEndHour,
            isRu = isRu
        )
        val res = sendReport(settings, targetWorkday, payload.jsonString)
        return if (res.success) {
            val timeLabel = WorkdayClock.formatShortTime(nowMs)
            val status = if (isRu) {
                "Выгружено за $targetWorkday в $timeLabel"
            } else {
                "Exported $targetWorkday at $timeLabel"
            }
            saveOutcome(targetWorkday, status)
            AutoExportOutcome.Exported(targetWorkday, res.message)
        } else {
            val timeLabel = WorkdayClock.formatShortTime(nowMs)
            val status = if (isRu) {
                "Ожидает ПК в сети за $targetWorkday (попытка в $timeLabel)"
            } else {
                "Waiting for PC online for $targetWorkday (last try $timeLabel)"
            }
            saveOutcome(null, status)
            AutoExportOutcome.RetryLater(targetWorkday, res.message)
        }
    }
}
