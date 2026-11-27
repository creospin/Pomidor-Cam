package com.example.data

import java.util.Locale

data class WorkdayReportSummary(
    val workdayDate: String,
    val windowLabel: String,
    val totalSessions: Int,
    val completedContinuousCount: Int,
    val interruptedAbsenceCount: Int,
    val manualStoppedCount: Int,
    val totalFocusSeconds: Int,
    val continuousFocusSeconds: Int,
    val interruptedFocusSeconds: Int,
    val totalBreakMinutes: Int,
    val longestContinuousSeconds: Int,
    val completionRatePercent: Int,
    val totalMissedChecks: Int,
    val totalAwaySeconds: Int
)

data class WorkdayReportPayload(
    val summary: WorkdayReportSummary,
    val sessionsAsc: List<WorkSessionEntity>,
    val jsonString: String,
    val markdownString: String
)

object WorkdayReportBuilder {

    fun summarize(
        workdayDate: String,
        sessions: List<WorkSessionEntity>,
        startHour: Int = 6,
        endHour: Int = 4
    ): WorkdayReportSummary {
        val completed = sessions.filter { it.status == SessionStatus.COMPLETED_CONTINUOUS }
        val interrupted = sessions.filter { it.status == SessionStatus.INTERRUPTED_ABSENCE }
        val manual = sessions.filter { it.status == SessionStatus.STOPPED_MANUAL }

        val continuousSec = completed.sumOf { it.actualFocusSeconds }
        val interruptedSec = (interrupted + manual).sumOf { it.actualFocusSeconds }
        val totalSec = continuousSec + interruptedSec
        val totalBreakMin = completed.filter { it.breakTriggered }.sumOf { it.breakDurationMinutes }
        val longestContinuous = completed.maxOfOrNull { it.actualFocusSeconds }
            ?: sessions.maxOfOrNull { it.actualFocusSeconds } ?: 0
        val completionPct = if (sessions.isEmpty()) 0 else ((completed.size * 100f) / sessions.size).toInt()
        val totalMisses = sessions.sumOf { it.missedChecksCount }
        val totalAway = sessions.sumOf { it.awaySecondsEstimate }

        return WorkdayReportSummary(
            workdayDate = workdayDate,
            windowLabel = WorkdayClock.formatWindowLabel(startHour, endHour),
            totalSessions = sessions.size,
            completedContinuousCount = completed.size,
            interruptedAbsenceCount = interrupted.size,
            manualStoppedCount = manual.size,
            totalFocusSeconds = totalSec,
            continuousFocusSeconds = continuousSec,
            interruptedFocusSeconds = interruptedSec,
            totalBreakMinutes = totalBreakMin,
            longestContinuousSeconds = longestContinuous,
            completionRatePercent = completionPct,
            totalMissedChecks = totalMisses,
            totalAwaySeconds = totalAway
        )
    }

    fun buildPayload(
        workdayDate: String,
        sessionsAsc: List<WorkSessionEntity>,
        startHour: Int = 6,
        endHour: Int = 4,
        isRu: Boolean = false
    ): WorkdayReportPayload {
        val summary = summarize(workdayDate, sessionsAsc, startHour, endHour)
        val markdown = buildMarkdown(summary, sessionsAsc, isRu)
        val json = buildJson(summary, sessionsAsc, markdown)
        return WorkdayReportPayload(
            summary = summary,
            sessionsAsc = sessionsAsc,
            jsonString = json,
            markdownString = markdown
        )
    }

    private fun escapeJson(value: String): String {
        val sb = StringBuilder(value.length + 16)
        for (ch in value) {
            when (ch) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> {
                    if (ch.code < 0x20) {
                        sb.append(String.format(Locale.US, "\\u%04x", ch.code))
                    } else {
                        sb.append(ch)
                    }
                }
            }
        }
        return sb.toString()
    }

    private fun buildJson(
        summary: WorkdayReportSummary,
        sessionsAsc: List<WorkSessionEntity>,
        markdown: String
    ): String {
        val sb = StringBuilder()
        sb.appendLine("{")
        sb.appendLine("  \"app\": \"Pomidor Cam\",")
        sb.appendLine("  \"exported_at\": ${System.currentTimeMillis()},")
        sb.appendLine("  \"workday_date\": \"${escapeJson(summary.workdayDate)}\",")
        sb.appendLine("  \"workday_window\": \"${escapeJson(summary.windowLabel)}\",")
        sb.appendLine("  \"summary\": {")
        sb.appendLine("    \"total_sessions\": ${summary.totalSessions},")
        sb.appendLine("    \"completed_continuous_count\": ${summary.completedContinuousCount},")
        sb.appendLine("    \"interrupted_absence_count\": ${summary.interruptedAbsenceCount},")
        sb.appendLine("    \"manual_stopped_count\": ${summary.manualStoppedCount},")
        sb.appendLine("    \"total_focus_seconds\": ${summary.totalFocusSeconds},")
        sb.appendLine("    \"continuous_focus_seconds\": ${summary.continuousFocusSeconds},")
        sb.appendLine("    \"interrupted_focus_seconds\": ${summary.interruptedFocusSeconds},")
        sb.appendLine("    \"total_break_minutes\": ${summary.totalBreakMinutes},")
        sb.appendLine("    \"longest_continuous_seconds\": ${summary.longestContinuousSeconds},")
        sb.appendLine("    \"completion_rate_percent\": ${summary.completionRatePercent},")
        sb.appendLine("    \"total_missed_checks\": ${summary.totalMissedChecks},")
        sb.appendLine("    \"total_away_seconds\": ${summary.totalAwaySeconds}")
        sb.appendLine("  },")
        sb.appendLine("  \"sessions\": [")
        sessionsAsc.forEachIndexed { idx, s ->
            val comma = if (idx < sessionsAsc.lastIndex) "," else ""
            sb.appendLine("    {")
            sb.appendLine("      \"index\": ${idx + 1},")
            sb.appendLine("      \"id\": ${s.id},")
            sb.appendLine("      \"workday_date\": \"${escapeJson(s.workdayDate)}\",")
            sb.appendLine("      \"within_workday_window\": ${s.withinWorkdayWindow},")
            sb.appendLine("      \"start_time_ms\": ${s.startTimeMs},")
            sb.appendLine("      \"end_time_ms\": ${s.endTimeMs},")
            sb.appendLine("      \"start_time\": \"${escapeJson(WorkdayClock.formatTime(s.startTimeMs))}\",")
            sb.appendLine("      \"end_time\": \"${escapeJson(WorkdayClock.formatTime(s.endTimeMs))}\",")
            sb.appendLine("      \"planned_focus_seconds\": ${s.plannedFocusSeconds},")
            sb.appendLine("      \"actual_focus_seconds\": ${s.actualFocusSeconds},")
            sb.appendLine("      \"status\": \"${escapeJson(s.status)}\",")
            sb.appendLine("      \"missed_checks_count\": ${s.missedChecksCount},")
            sb.appendLine("      \"away_seconds_estimate\": ${s.awaySecondsEstimate},")
            sb.appendLine("      \"break_triggered\": ${s.breakTriggered},")
            sb.appendLine("      \"break_duration_minutes\": ${s.breakDurationMinutes},")
            sb.appendLine("      \"camera_facing\": \"${escapeJson(s.cameraFacing)}\",")
            sb.appendLine("      \"detector_engine\": \"${escapeJson(s.detectorEngine)}\",")
            sb.appendLine("      \"notes\": \"${escapeJson(s.notes)}\"")
            sb.appendLine("    }$comma")
        }
        sb.appendLine("  ],")
        sb.appendLine("  \"markdown\": \"${escapeJson(markdown)}\"")
        sb.append("}")
        return sb.toString()
    }

    private fun buildMarkdown(
        summary: WorkdayReportSummary,
        sessionsAsc: List<WorkSessionEntity>,
        isRu: Boolean = false
    ): String {
        val sb = StringBuilder()
        if (isRu) {
            sb.appendLine("# Pomidor Cam — Полный отчёт за рабочий день ${summary.workdayDate}")
            sb.appendLine()
            sb.appendLine("- **Окно рабочего дня:** `${summary.windowLabel}`")
            sb.appendLine("- **Всего сессий:** ${summary.totalSessions}")
            sb.appendLine("- **Завершённых непрерывных (помидоров):** ${summary.completedContinuousCount}")
            sb.appendLine("- **Прерванных из-за ухода:** ${summary.interruptedAbsenceCount}")
            sb.appendLine("- **Остановленных вручную:** ${summary.manualStoppedCount}")
            sb.appendLine("- **Общее время фокуса:** ${WorkdayClock.formatDurationHuman(summary.totalFocusSeconds, true)}")
            sb.appendLine("- **Непрерывный фокус:** ${WorkdayClock.formatDurationHuman(summary.continuousFocusSeconds, true)}")
            sb.appendLine("- **Неполный фокус:** ${WorkdayClock.formatDurationHuman(summary.interruptedFocusSeconds, true)}")
            sb.appendLine("- **Запланировано перерывов:** ${summary.totalBreakMinutes} мин")
            sb.appendLine("- **Самая длинная непрерывная сессия:** ${WorkdayClock.formatDurationHuman(summary.longestContinuousSeconds, true)}")
            sb.appendLine("- **Успешность непрерывных сессий:** ${summary.completionRatePercent}%")
            sb.appendLine("- **Суммарно пропусков кадров (10с):** ${summary.totalMissedChecks} (~${summary.totalAwaySeconds} с)")
            sb.appendLine()
            sb.appendLine("## Хронология всех сессий")
            sb.appendLine()
            if (sessionsAsc.isEmpty()) {
                sb.appendLine("_За этот рабочий день сессии пока не зафиксированы._")
            } else {
                sb.appendLine("| # | Начало | Конец | Длительность | Статус | Пропуски | Детектор | Перерыв |")
                sb.appendLine("|---:|:---:|:---:|:---:|---|---:|---|---|")
                sessionsAsc.forEachIndexed { idx, s ->
                    val statusRu = when (s.status) {
                        SessionStatus.COMPLETED_CONTINUOUS -> "Завершена (непрерывно)"
                        SessionStatus.INTERRUPTED_ABSENCE -> "Прервана (уход)"
                        SessionStatus.STOPPED_MANUAL -> "Остановлена вручную"
                        else -> s.status
                    }
                    val windowTag = if (s.withinWorkdayWindow) "" else " [вне окна]"
                    val breakStr = if (s.breakTriggered) "${s.breakDurationMinutes} мин" else "—"
                    sb.appendLine(
                        String.format(
                            Locale.US,
                            "| %d | %s | %s | %s / %s | %s%s | %d | %s (%s) | %s |",
                            idx + 1,
                            WorkdayClock.formatTime(s.startTimeMs),
                            WorkdayClock.formatTime(s.endTimeMs),
                            WorkdayClock.formatMmSs(s.actualFocusSeconds),
                            WorkdayClock.formatMmSs(s.plannedFocusSeconds),
                            statusRu,
                            windowTag,
                            s.missedChecksCount,
                            s.detectorEngine,
                            s.cameraFacing,
                            breakStr
                        )
                    )
                }
            }
        } else {
            sb.appendLine("# Pomidor Cam — Full Workday Report (${summary.workdayDate})")
            sb.appendLine()
            sb.appendLine("- **Workday window:** `${summary.windowLabel}`")
            sb.appendLine("- **Total sessions:** ${summary.totalSessions}")
            sb.appendLine("- **Completed continuous (Pomodoros):** ${summary.completedContinuousCount}")
            sb.appendLine("- **Interrupted (away):** ${summary.interruptedAbsenceCount}")
            sb.appendLine("- **Stopped manually:** ${summary.manualStoppedCount}")
            sb.appendLine("- **Total focus time:** ${WorkdayClock.formatDurationHuman(summary.totalFocusSeconds, false)}")
            sb.appendLine("- **Continuous focus:** ${WorkdayClock.formatDurationHuman(summary.continuousFocusSeconds, false)}")
            sb.appendLine("- **Partial focus:** ${WorkdayClock.formatDurationHuman(summary.interruptedFocusSeconds, false)}")
            sb.appendLine("- **Scheduled break time:** ${summary.totalBreakMinutes} min")
            sb.appendLine("- **Longest continuous session:** ${WorkdayClock.formatDurationHuman(summary.longestContinuousSeconds, false)}")
            sb.appendLine("- **Continuous completion rate:** ${summary.completionRatePercent}%")
            sb.appendLine("- **Total missed checks:** ${summary.totalMissedChecks} (~${summary.totalAwaySeconds} s)")
            sb.appendLine()
            sb.appendLine("## Session Timeline")
            sb.appendLine()
            if (sessionsAsc.isEmpty()) {
                sb.appendLine("_No sessions recorded for this workday yet._")
            } else {
                sb.appendLine("| # | Start | End | Duration | Status | Misses | Detector | Break |")
                sb.appendLine("|---:|:---:|:---:|:---:|---|---:|---|---|")
                sessionsAsc.forEachIndexed { idx, s ->
                    val statusEn = when (s.status) {
                        SessionStatus.COMPLETED_CONTINUOUS -> "Completed (continuous)"
                        SessionStatus.INTERRUPTED_ABSENCE -> "Interrupted (away)"
                        SessionStatus.STOPPED_MANUAL -> "Stopped manually"
                        else -> s.status
                    }
                    val windowTag = if (s.withinWorkdayWindow) "" else " [off-shift]"
                    val breakStr = if (s.breakTriggered) "${s.breakDurationMinutes} min" else "—"
                    sb.appendLine(
                        String.format(
                            Locale.US,
                            "| %d | %s | %s | %s / %s | %s%s | %d | %s (%s) | %s |",
                            idx + 1,
                            WorkdayClock.formatTime(s.startTimeMs),
                            WorkdayClock.formatTime(s.endTimeMs),
                            WorkdayClock.formatMmSs(s.actualFocusSeconds),
                            WorkdayClock.formatMmSs(s.plannedFocusSeconds),
                            statusEn,
                            windowTag,
                            s.missedChecksCount,
                            s.detectorEngine,
                            s.cameraFacing,
                            breakStr
                        )
                    )
                }
            }
        }
        return sb.toString()
    }
}
