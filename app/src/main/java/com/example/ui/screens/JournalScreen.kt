package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.ScheduleSend
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.PomidorSettings
import com.example.data.SessionStatus
import com.example.data.WorkSessionEntity
import com.example.data.WorkdayClock
import com.example.data.WorkdayReportPayload
import com.example.data.WorkdayReportSummary
import com.example.i18n.AppLocaleHelper.tr
import com.example.i18n.LocalIsRussian
import com.example.ui.components.BlueGradientButton
import com.example.ui.theme.AmberWarningSoft
import com.example.ui.theme.BlueGlowSoft
import com.example.ui.theme.CharcoalSurface
import com.example.ui.theme.DimText
import com.example.ui.theme.GoldenSeed
import com.example.ui.theme.IceText
import com.example.ui.theme.MintGreen
import com.example.ui.theme.MintGreenSoft
import com.example.ui.theme.MutedSlateText
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.RoyalBlue
import com.example.ui.theme.SkyBlue
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateCard
import kotlinx.coroutines.launch

@Composable
fun JournalScreen(
    selectedWorkday: String,
    availableWorkdays: List<String>,
    sessions: List<WorkSessionEntity>,
    summary: WorkdayReportSummary,
    settings: PomidorSettings,
    isBusy: Boolean,
    onSelectWorkday: (String) -> Unit,
    onExportToPc: () -> Unit,
    onToggleAutoMorningExport: (Boolean) -> Unit,
    onAutoMorningHourChange: (Int) -> Unit,
    onRunAutoExportYesterdayNow: () -> Unit,
    onBuildReportPayload: suspend () -> WorkdayReportPayload,
    onSaveTextToUri: (android.net.Uri, String, String) -> Unit,
    onShareFile: (String, String, String) -> android.content.Intent?,
    onDeleteSession: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isRu = LocalIsRussian.current

    val saveMarkdownLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val payload = onBuildReportPayload()
                onSaveTextToUri(uri, payload.markdownString, tr(isRu, "Report .md saved", "Отчёт .md сохранён"))
            }
        }
    }

    val saveJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val payload = onBuildReportPayload()
                onSaveTextToUri(uri, payload.jsonString, tr(isRu, "Report .json saved", "Отчёт .json сохранён"))
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                Text(
                    text = tr(isRu, "Journal & Reports", "Журнал и отчёты"),
                    style = MaterialTheme.typography.headlineMedium,
                    color = IceText
                )
                Text(
                    text = tr(
                        isRu,
                        "Workday: ${WorkdayClock.formatWindowLabel(settings.workdayStartHour, settings.workdayEndHour)} • All continuous & interrupted sessions",
                        "Рабочий день: ${WorkdayClock.formatWindowLabel(settings.workdayStartHour, settings.workdayEndHour)} • Все непрерывные и прерванные сессии"
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedSlateText
                )
            }
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(availableWorkdays) { day ->
                    val selected = day == selectedWorkday
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectWorkday(day) },
                        label = { Text(day, style = MaterialTheme.typography.labelMedium) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = RoyalBlue,
                            selectedLabelColor = IceText,
                            containerColor = SlateCard,
                            labelColor = MutedSlateText
                        )
                    )
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ReportKpiTile(
                        title = tr(isRu, "Continuous", "Непрерывных"),
                        value = "${summary.completedContinuousCount}",
                        subtitle = WorkdayClock.formatDurationHuman(summary.continuousFocusSeconds, isRu),
                        accent = SkyBlue,
                        modifier = Modifier.weight(1f)
                    )
                    ReportKpiTile(
                        title = tr(isRu, "Interrupted", "Прерванных"),
                        value = "${summary.interruptedAbsenceCount + summary.manualStoppedCount}",
                        subtitle = WorkdayClock.formatDurationHuman(summary.interruptedFocusSeconds, isRu),
                        accent = GoldenSeed,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ReportKpiTile(
                        title = tr(isRu, "Total Focus", "Всего фокуса"),
                        value = WorkdayClock.formatDurationHuman(summary.totalFocusSeconds, isRu),
                        subtitle = tr(
                            isRu,
                            "Rate: ${summary.completionRatePercent}% • Break: ${summary.totalBreakMinutes}m",
                            "Успех: ${summary.completionRatePercent}% • Отдых: ${summary.totalBreakMinutes}м"
                        ),
                        accent = MintGreen,
                        modifier = Modifier.weight(1f)
                    )
                    ReportKpiTile(
                        title = tr(isRu, "Longest Streak", "Макс. серия"),
                        value = WorkdayClock.formatDurationHuman(summary.longestContinuousSeconds, isRu),
                        subtitle = tr(
                            isRu,
                            "Misses: ${summary.totalMissedChecks}",
                            "Пропусков: ${summary.totalMissedChecks}"
                        ),
                        accent = IceText,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        item {
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                border = BorderStroke(1.dp, SlateBorder)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = tr(isRu, "On-Demand Full Report Export", "Выгрузка полного отчёта по запросу"),
                        style = MaterialTheme.typography.titleMedium,
                        color = IceText
                    )
                    Text(
                        text = tr(
                            isRu,
                            "Sends JSON and Markdown report for shift ${summary.workdayDate} (${summary.windowLabel}) to PC ${settings.pcHost}:${settings.pcPort}.",
                            "Отправляет JSON и Markdown-отчёт за смену ${summary.workdayDate} (${summary.windowLabel}) на ПК ${settings.pcHost}:${settings.pcPort}."
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedSlateText
                    )

                    BlueGradientButton(
                        onClick = onExportToPc,
                        enabled = !isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("export_report_pc_button")
                    ) {
                        Icon(Icons.Rounded.CloudUpload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (isBusy) {
                                tr(isRu, "Exporting...", "Выгрузка...")
                            } else {
                                tr(isRu, "Export Report to PC", "Выгрузить отчёт на компьютер")
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { saveMarkdownLauncher.launch("pomidor_report_${selectedWorkday}.md") },
                            modifier = Modifier.weight(1f).height(48.dp),
                            border = BorderStroke(1.dp, SlateBorder)
                        ) {
                            Icon(Icons.Rounded.SaveAlt, contentDescription = null, modifier = Modifier.size(16.dp), tint = IceText)
                            Spacer(Modifier.width(4.dp))
                            Text(".MD", color = IceText)
                        }
                        OutlinedButton(
                            onClick = { saveJsonLauncher.launch("pomidor_report_${selectedWorkday}.json") },
                            modifier = Modifier.weight(1f).height(48.dp),
                            border = BorderStroke(1.dp, SlateBorder)
                        ) {
                            Icon(Icons.Rounded.SaveAlt, contentDescription = null, modifier = Modifier.size(16.dp), tint = IceText)
                            Spacer(Modifier.width(4.dp))
                            Text(".JSON", color = IceText)
                        }
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val payload = onBuildReportPayload()
                                    val intent = onShareFile(
                                        "pomidor_report_${selectedWorkday}.md",
                                        payload.markdownString,
                                        "text/markdown"
                                    )
                                    if (intent != null) {
                                        context.startActivity(
                                            android.content.Intent.createChooser(
                                                intent,
                                                tr(isRu, "Share report", "Поделиться отчётом")
                                            )
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                            border = BorderStroke(1.dp, SlateBorder)
                        ) {
                            Icon(
                                Icons.Rounded.Share,
                                contentDescription = tr(isRu, "Share", "Поделиться"),
                                modifier = Modifier.size(16.dp),
                                tint = IceText
                            )
                        }
                    }
                }
            }
        }

        item {
            val previousWorkday = WorkdayClock.getPreviousWorkdayDate(
                timestampMs = System.currentTimeMillis(),
                startHour = settings.workdayStartHour
            )
            val hourLabel = String.format(java.util.Locale.US, "%02d:00", settings.autoMorningExportHour)
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                border = BorderStroke(1.dp, SlateBorder)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.ScheduleSend, contentDescription = null, tint = SkyBlue)
                            Column {
                                Text(
                                    text = tr(isRu, "Morning Auto-Export", "Автовыгрузка утром"),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = IceText
                                )
                                Text(
                                    text = tr(
                                        isRu,
                                        "Daily from $hourLabel for previous shift ($previousWorkday)",
                                        "Каждый день с $hourLabel за прошлую смену ($previousWorkday)"
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MutedSlateText
                                )
                            }
                        }
                        Switch(
                            checked = settings.autoMorningExportEnabled,
                            onCheckedChange = onToggleAutoMorningExport,
                            modifier = Modifier.testTag("auto_morning_export_switch")
                        )
                    }

                    if (settings.autoMorningExportEnabled) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = tr(isRu, "Morning export time", "Время утренней выгрузки"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = IceText
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilledTonalIconButton(
                                    onClick = { onAutoMorningHourChange((settings.autoMorningExportHour - 1).coerceIn(6, 12)) },
                                    modifier = Modifier.size(48.dp).testTag("auto_export_hour_minus"),
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = SlateCard,
                                        contentColor = IceText
                                    )
                                ) {
                                    Icon(Icons.Rounded.Remove, contentDescription = tr(isRu, "1 hour earlier", "Раньше на час"))
                                }
                                Text(
                                    text = hourLabel,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = SkyBlue
                                )
                                FilledTonalIconButton(
                                    onClick = { onAutoMorningHourChange((settings.autoMorningExportHour + 1).coerceIn(6, 12)) },
                                    modifier = Modifier.size(48.dp).testTag("auto_export_hour_plus"),
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = SlateCard,
                                        contentColor = IceText
                                    )
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = tr(isRu, "1 hour later", "Позже на час"))
                                }
                            }
                        }
                    }

                    val autoStatus = settings.lastAutoExportStatus.ifBlank {
                        tr(
                            isRu,
                            "Waiting for morning window (from $hourLabel)",
                            "Ожидает утреннего окна (с $hourLabel)"
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = SlateCard,
                        border = BorderStroke(1.dp, SlateBorder)
                    ) {
                        Text(
                            text = "${tr(isRu, "Status", "Статус")}: $autoStatus",
                            style = MaterialTheme.typography.labelSmall,
                            color = SkyBlue,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }

                    OutlinedButton(
                        onClick = onRunAutoExportYesterdayNow,
                        enabled = !isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("auto_export_yesterday_now_button"),
                        border = BorderStroke(1.dp, SlateBorder)
                    ) {
                        Icon(Icons.Rounded.CloudUpload, contentDescription = null, tint = SkyBlue, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = tr(
                                isRu,
                                "Export Previous Day ($previousWorkday)",
                                "Выгрузить вчерашний день ($previousWorkday)"
                            ),
                            color = IceText
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = tr(
                    isRu,
                    "Sessions for $selectedWorkday (${sessions.size})",
                    "Сессии за $selectedWorkday (${sessions.size})"
                ),
                style = MaterialTheme.typography.titleMedium,
                color = IceText
            )
        }

        if (sessions.isEmpty()) {
            item {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                    border = BorderStroke(1.dp, SlateBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PauseCircle,
                            contentDescription = null,
                            tint = DimText,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = tr(isRu, "No sessions recorded for this workday", "За этот рабочий день пока нет записей"),
                            style = MaterialTheme.typography.titleMedium,
                            color = MutedSlateText
                        )
                        Text(
                            text = tr(
                                isRu,
                                "Once the camera detects a person, all continuous and interrupted sessions will appear here.",
                                "Как только камера зафиксирует человека, здесь будут сохраняться все непрерывные и прерванные сессии."
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = DimText
                        )
                    }
                }
            }
        } else {
            items(sessions, key = { it.id }) { session ->
                SessionHistoryCard(
                    session = session,
                    isRu = isRu,
                    onDelete = { onDeleteSession(session.id) }
                )
            }
        }
    }
}

@Composable
private fun ReportKpiTile(
    title: String,
    value: String,
    subtitle: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
        border = BorderStroke(1.dp, SlateBorder)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.labelSmall, color = MutedSlateText)
            Text(text = value, style = MaterialTheme.typography.titleLarge, color = accent)
            Text(text = subtitle, style = MaterialTheme.typography.labelSmall, color = DimText)
        }
    }
}

@Composable
private fun SessionHistoryCard(
    session: WorkSessionEntity,
    isRu: Boolean,
    onDelete: () -> Unit
) {
    val (statusLabel, statusColor, statusBg, icon) = when (session.status) {
        SessionStatus.COMPLETED_CONTINUOUS -> Quad(
            tr(isRu, "Completed continuous", "Завершена непрерывно"),
            MintGreen,
            MintGreenSoft,
            Icons.Rounded.CheckCircle
        )
        SessionStatus.INTERRUPTED_ABSENCE -> Quad(
            tr(isRu, "Interrupted (away)", "Прервана (уход)"),
            GoldenSeed,
            AmberWarningSoft,
            Icons.Rounded.WarningAmber
        )
        else -> Quad(
            tr(isRu, "Stopped manually", "Остановлена вручную"),
            SkyBlue,
            BlueGlowSoft,
            Icons.Rounded.PauseCircle
        )
    }

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
        border = BorderStroke(1.dp, SlateBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = statusBg
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(icon, contentDescription = null, tint = statusColor, modifier = Modifier.size(14.dp))
                        Text(statusLabel, style = MaterialTheme.typography.labelSmall, color = statusColor)
                    }
                }

                IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = tr(isRu, "Delete session", "Удалить запись"),
                        tint = DimText,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${WorkdayClock.formatTime(session.startTimeMs)} → ${WorkdayClock.formatTime(session.endTimeMs)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = IceText
                )
                Text(
                    text = "${WorkdayClock.formatMmSs(session.actualFocusSeconds)} / ${WorkdayClock.formatMmSs(session.plannedFocusSeconds)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = statusColor
                )
            }

            val windowNote = if (session.withinWorkdayWindow) "" else tr(isRu, " • Off-shift", " • Вне окна смены")
            Text(
                text = "${session.detectorEngine} (${session.cameraFacing}) • ${tr(isRu, "Misses", "Пропусков")}: ${session.missedChecksCount}$windowNote",
                style = MaterialTheme.typography.bodyMedium,
                color = MutedSlateText
            )
            if (session.notes.isNotBlank()) {
                Text(
                    text = session.notes,
                    style = MaterialTheme.typography.labelSmall,
                    color = DimText
                )
            }
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
