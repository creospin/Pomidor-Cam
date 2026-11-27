package com.example.network

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.SettingsRepository
import com.example.data.WorkdayClock
import com.example.i18n.AppLocaleHelper
import com.example.service.MonitorStateHolder
import kotlinx.coroutines.flow.first

class MorningReportWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return when (val outcome = MorningAutoExportManager.evaluateAndExportIfDue(applicationContext)) {
            is AutoExportOutcome.Exported -> {
                val lang = SettingsRepository(applicationContext).settingsFlow.first().appLanguage
                val isRu = AppLocaleHelper.isRussian(lang)
                MonitorStateHolder.update { it.copy(lastLanStatus = outcome.message) }
                val logText = if (isRu) {
                    "Автоотчёт за ${outcome.workdayDate} выгружен на ПК"
                } else {
                    "Morning report for ${outcome.workdayDate} exported to PC"
                }
                MonitorStateHolder.appendLog("${WorkdayClock.formatTime(System.currentTimeMillis())} • $logText")
                Result.success()
            }
            is AutoExportOutcome.RetryLater -> {
                MonitorStateHolder.update { it.copy(lastLanStatus = outcome.message) }
                Result.retry()
            }
            else -> Result.success()
        }
    }
}
