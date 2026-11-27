package com.example.network

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.data.PomidorSettings
import com.example.data.WorkdayClock
import java.util.concurrent.TimeUnit

object MorningExportScheduler {

    private const val PERIODIC_WORK_NAME = "pomidor_morning_export_periodic"
    private const val NEXT_MORNING_WORK_NAME = "pomidor_morning_export_next"

    fun syncSchedule(context: Context, settings: PomidorSettings) {
        val wm = WorkManager.getInstance(context.applicationContext)
        if (!settings.autoMorningExportEnabled) {
            wm.cancelUniqueWork(PERIODIC_WORK_NAME)
            wm.cancelUniqueWork(NEXT_MORNING_WORK_NAME)
            return
        }

        val networkConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val initialDelayMs = WorkdayClock.computeNextMorningTriggerDelayMs(
            nowMs = System.currentTimeMillis(),
            targetHour = settings.autoMorningExportHour,
            targetMinute = 1
        )

        val nextMorningWork = OneTimeWorkRequestBuilder<MorningReportWorker>()
            .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
            .setConstraints(networkConstraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()

        wm.enqueueUniqueWork(
            NEXT_MORNING_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            nextMorningWork
        )

        val periodicWork = PeriodicWorkRequestBuilder<MorningReportWorker>(
            2,
            TimeUnit.HOURS,
            30,
            TimeUnit.MINUTES
        )
            .setConstraints(networkConstraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()

        wm.enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicWork
        )
    }
}
