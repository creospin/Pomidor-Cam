package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Monitor
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonOff
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.data.CameraLensOption
import com.example.data.PomidorSettings
import com.example.data.WorkdayClock
import com.example.hardware.CameraCatalog
import com.example.hardware.DeviceCameraOption
import com.example.i18n.AppLocaleHelper.tr
import com.example.i18n.LocalIsRussian
import com.example.ml.DetectionBox
import com.example.service.MonitorStateHolder
import com.example.service.MonitorUiState
import com.example.service.SessionPhase
import com.example.ui.components.BlueGradientButton
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.AmberWarningSoft
import com.example.ui.theme.BlueGlowSoft
import com.example.ui.theme.CharcoalSurface
import com.example.ui.theme.CobaltBlue
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

@Composable
fun MonitorScreen(
    state: MonitorUiState,
    settings: PomidorSettings,
    availableCameras: List<DeviceCameraOption>,
    onStartMonitoring: () -> Unit,
    onStopMonitoring: () -> Unit,
    onSelectCameraOption: (DeviceCameraOption) -> Unit,
    onCycleCamera: () -> Unit,
    onRefreshCameras: () -> Unit,
    onTogglePreview: (Boolean) -> Unit,
    onTestTorch: () -> Unit,
    onTestLanBreak: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isRu = LocalIsRussian.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasCameraPermission = result[Manifest.permission.CAMERA] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            result[Manifest.permission.POST_NOTIFICATIONS] == true ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else true

        if (hasCameraPermission) {
            onRefreshCameras()
            onStartMonitoring()
        }
    }

    val phaseColor by animateColorAsState(
        targetValue = when (state.phase) {
            SessionPhase.IDLE -> MutedSlateText
            SessionPhase.FOCUSING -> SkyBlue
            SessionPhase.GRACE_PERIOD -> GoldenSeed
            SessionPhase.BREAK -> MintGreen
        },
        label = "phaseColor"
    )

    val phaseBgColor by animateColorAsState(
        targetValue = when (state.phase) {
            SessionPhase.IDLE -> SlateCard
            SessionPhase.FOCUSING -> BlueGlowSoft
            SessionPhase.GRACE_PERIOD -> AmberWarningSoft
            SessionPhase.BREAK -> MintGreenSoft
        },
        label = "phaseBgColor"
    )

    val matchedCamera = remember(availableCameras, settings.selectedCameraId, settings.cameraLens) {
        CameraCatalog.findMatch(availableCameras, settings.selectedCameraId, settings.cameraLens)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.pomidor_logo),
                        contentDescription = tr(isRu, "Pomidor Cam logo", "Логотип Pomidor Cam"),
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                    )
                    Column {
                        Text(
                            text = "Pomidor Cam",
                            style = MaterialTheme.typography.headlineMedium,
                            color = IceText
                        )
                        val engineText = state.activeEngineLabel.ifBlank {
                            tr(isRu, "YOLO26 / Built-in", "YOLO26 / Встроенный")
                        }
                        Text(
                            text = "${settings.deviceProfile.label(isRu)} • $engineText",
                            style = MaterialTheme.typography.labelSmall,
                            color = SkyBlue,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SlateCard,
                    border = BorderStroke(1.dp, SlateBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Videocam,
                            contentDescription = tr(isRu, "Camera", "Камера"),
                            tint = SkyBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        val cameraLabel = matchedCamera?.label
                            ?: settings.selectedCameraLabel.ifBlank { settings.cameraLens.label(isRu) }
                        Text(
                            text = cameraLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = IceText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        if (!hasCameraPermission || !hasNotificationPermission) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = AmberWarningSoft),
                    border = BorderStroke(1.dp, GoldenSeed.copy(alpha = 0.5f)),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = tr(isRu, "System permissions required", "Требуются системные разрешения"),
                            style = MaterialTheme.typography.titleMedium,
                            color = GoldenSeed
                        )
                        Text(
                            text = tr(
                                isRu,
                                "Camera access is needed to check presence every ${settings.checkIntervalSeconds}s, and silent notifications keep the monitor running with the screen off.",
                                "Камера нужна для проверки присутствия каждые ${settings.checkIntervalSeconds} с, а тихое уведомление — для работы при выключенном экране."
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = IceText
                        )
                        BlueGradientButton(
                            onClick = {
                                val perms = buildList {
                                    add(Manifest.permission.CAMERA)
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        add(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                }.toTypedArray()
                                permissionLauncher.launch(perms)
                            },
                            modifier = Modifier.testTag("grant_permissions_button")
                        ) {
                            Text(
                                text = tr(isRu, "Grant permissions", "Выдать разрешения"),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        item {
            PomodoroDialCard(
                state = state,
                settings = settings,
                phaseColor = phaseColor,
                phaseBgColor = phaseBgColor
            )
        }

        if (availableCameras.isNotEmpty()) {
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(availableCameras, key = { it.optionKey }) { cam ->
                        val isSelected = matchedCamera?.optionKey == cam.optionKey
                        FilterChip(
                            selected = isSelected,
                            onClick = { onSelectCameraOption(cam) },
                            label = {
                                Text(
                                    text = cam.label,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            modifier = Modifier.testTag("camera_chip_${cam.optionKey}"),
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
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (state.isServiceRunning) {
                    Button(
                        onClick = onStopMonitoring,
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                            .testTag("toggle_monitor_button"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SlateCard,
                            contentColor = IceText
                        ),
                        border = BorderStroke(1.5.dp, SkyBlue)
                    ) {
                        Icon(imageVector = Icons.Rounded.Stop, contentDescription = null, tint = SkyBlue)
                        Spacer(Modifier.width(8.dp))
                        Text(tr(isRu, "Stop", "Остановить"), style = MaterialTheme.typography.titleMedium)
                    }
                } else {
                    BlueGradientButton(
                        onClick = {
                            if (!hasCameraPermission) {
                                val perms = buildList {
                                    add(Manifest.permission.CAMERA)
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        add(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                }.toTypedArray()
                                permissionLauncher.launch(perms)
                            } else {
                                onStartMonitoring()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                            .testTag("toggle_monitor_button"),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(imageVector = Icons.Rounded.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = tr(isRu, "Start Monitoring", "Запустить мониторинг"),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }

                FilledTonalButton(
                    onClick = onCycleCamera,
                    modifier = Modifier
                        .height(54.dp)
                        .testTag("switch_camera_button"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = SlateCard,
                        contentColor = SkyBlue
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Cameraswitch,
                        contentDescription = tr(isRu, "Switch camera", "Переключить камеру")
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onTestTorch,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("test_torch_button"),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, SlateBorder)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FlashlightOn,
                        contentDescription = null,
                        tint = GoldenSeed,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = tr(
                            isRu,
                            "Torch ${settings.torchBrightnessPercent}%",
                            "Фонарик ${settings.torchBrightnessPercent}%"
                        ),
                        color = IceText
                    )
                }

                OutlinedButton(
                    onClick = onTestLanBreak,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("test_lan_break_button"),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, SlateBorder)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Monitor,
                        contentDescription = null,
                        tint = SkyBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(tr(isRu, "Test PC Overlay", "Тест ПК-заставки"), color = IceText)
                }
            }
        }

        item {
            CameraTelemetryCard(
                state = state,
                settings = settings,
                isRu = isRu,
                onTogglePreview = onTogglePreview
            )
        }

        if (state.recentLogs.isNotEmpty()) {
            item {
                Text(
                    text = tr(isRu, "Recent checks", "Последние проверки"),
                    style = MaterialTheme.typography.titleMedium,
                    color = MutedSlateText
                )
            }
            items(state.recentLogs.take(8)) { logLine ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SlateCard.copy(alpha = 0.7f),
                    border = BorderStroke(1.dp, SlateBorder.copy(alpha = 0.6f))
                ) {
                    Text(
                        text = logLine,
                        style = MaterialTheme.typography.labelSmall,
                        color = IceText,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PomodoroDialCard(
    state: MonitorUiState,
    settings: PomidorSettings,
    phaseColor: Color,
    phaseBgColor: Color
) {
    val isRu = LocalIsRussian.current
    val total = state.totalPhaseSeconds.coerceAtLeast(1)
    val rawProgress = if (state.phase == SessionPhase.IDLE) {
        0f
    } else {
        1f - (state.remainingSeconds.toFloat() / total.toFloat())
    }
    val progress by animateFloatAsState(
        targetValue = rawProgress.coerceIn(0f, 1f),
        animationSpec = spring(stiffness = 280f),
        label = "dialProgress"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
        border = BorderStroke(1.dp, SlateBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = phaseBgColor,
                    border = BorderStroke(1.dp, phaseColor.copy(alpha = 0.45f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(phaseColor)
                        )
                        Text(
                            text = state.phase.label(isRu),
                            style = MaterialTheme.typography.labelMedium,
                            color = phaseColor
                        )
                    }
                }

                if (state.isServiceRunning) {
                    Text(
                        text = tr(
                            isRu,
                            "Next check in ${state.secondsUntilNextCheck}s",
                            "Кадр через ${state.secondsUntilNextCheck}с"
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MutedSlateText
                    )
                } else {
                    Text(
                        text = tr(
                            isRu,
                            "${settings.focusDurationMinutes}m / ${settings.breakDurationMinutes}m",
                            "${settings.focusDurationMinutes}м / ${settings.breakDurationMinutes}м"
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = DimText
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(220.dp)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.pomidor_logo),
                    contentDescription = null,
                    modifier = Modifier
                        .size(164.dp)
                        .alpha(0.14f)
                )

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 14.dp.toPx()
                    val diameter = minOf(size.width, size.height) - strokeWidth
                    val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
                    val arcSize = Size(diameter, diameter)

                    drawArc(
                        color = SlateCard,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )

                    if (progress > 0f) {
                        val arcBrush = if (state.phase == SessionPhase.FOCUSING) {
                            Brush.sweepGradient(listOf(CobaltBlue, SkyBlue, RoyalBlue, CobaltBlue))
                        } else {
                            Brush.linearGradient(listOf(phaseColor, phaseColor))
                        }
                        drawArc(
                            brush = arcBrush,
                            startAngle = -90f,
                            sweepAngle = 360f * progress,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = WorkdayClock.formatMmSs(state.remainingSeconds),
                        style = MaterialTheme.typography.displayLarge,
                        color = IceText
                    )
                    Spacer(Modifier.height(4.dp))
                    val subInfo = when (state.phase) {
                        SessionPhase.IDLE -> if (state.isServiceRunning) {
                            tr(isRu, "Scanning for person", "Ожидаю человека в кадре")
                        } else {
                            tr(isRu, "Press Start to monitor", "Нажмите старт для отслеживания")
                        }
                        SessionPhase.FOCUSING -> tr(isRu, "Silent mode • no alerts", "Тихий режим • без оповещений")
                        SessionPhase.GRACE_PERIOD -> tr(
                            isRu,
                            "Misses: ${state.consecutiveMisses}/${state.maxMissedChecks}",
                            "Пропуски: ${state.consecutiveMisses}/${state.maxMissedChecks}"
                        )
                        SessionPhase.BREAK -> tr(isRu, "Break active on PC & phone", "Отдых на ПК и телефоне")
                    }
                    Text(
                        text = subInfo,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (state.phase == SessionPhase.GRACE_PERIOD) GoldenSeed else MutedSlateText
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                PresenceMiniStat(
                    label = tr(isRu, "Presence", "Присутствие"),
                    value = when (state.lastPersonDetected) {
                        true -> tr(
                            isRu,
                            "Detected (${(state.lastConfidence * 100).toInt()}%)",
                            "В кадре (${(state.lastConfidence * 100).toInt()}%)"
                        )
                        false -> tr(isRu, "Empty", "Нет в кадре")
                        null -> "—"
                    },
                    valueColor = when (state.lastPersonDetected) {
                        true -> SkyBlue
                        false -> GoldenSeed
                        null -> DimText
                    }
                )
                PresenceMiniStat(
                    label = tr(isRu, "Inference", "Инференс"),
                    value = if (state.lastInferenceMs > 0) {
                        tr(isRu, "${state.lastInferenceMs} ms", "${state.lastInferenceMs} мс")
                    } else "—",
                    valueColor = IceText
                )
                PresenceMiniStat(
                    label = tr(isRu, "Tolerance", "Допуск"),
                    value = "${state.consecutiveMisses}/${settings.maxMissedChecks}",
                    valueColor = if (state.consecutiveMisses > 0) GoldenSeed else IceText
                )
            }
        }
    }
}

@Composable
private fun PresenceMiniStat(label: String, value: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = DimText)
        Text(text = value, style = MaterialTheme.typography.labelMedium, color = valueColor)
    }
}

@Composable
private fun CameraTelemetryCard(
    state: MonitorUiState,
    settings: PomidorSettings,
    isRu: Boolean,
    onTogglePreview: (Boolean) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    DisposableEffect(state.isServiceRunning, settings.showLivePreview, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START && state.isServiceRunning && settings.showLivePreview) {
                MonitorStateHolder.setPreviewSurfaceProvider(previewView.surfaceProvider)
            } else if (event == Lifecycle.Event.ON_STOP) {
                MonitorStateHolder.setPreviewSurfaceProvider(null)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (state.isServiceRunning && settings.showLivePreview) {
            MonitorStateHolder.setPreviewSurfaceProvider(previewView.surfaceProvider)
        } else {
            MonitorStateHolder.setPreviewSurfaceProvider(null)
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            MonitorStateHolder.setPreviewSurfaceProvider(null)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
        border = BorderStroke(1.dp, SlateBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (state.lastPersonDetected == true) {
                            Icons.Rounded.Person
                        } else {
                            Icons.Rounded.PersonOff
                        },
                        contentDescription = null,
                        tint = if (state.lastPersonDetected == true) SkyBlue else MutedSlateText
                    )
                    Column {
                        Text(
                            text = tr(isRu, "Presence Sensor", "Сенсор присутствия"),
                            style = MaterialTheme.typography.titleMedium,
                            color = IceText
                        )
                        Text(
                            text = tr(
                                isRu,
                                "Interval: ${settings.checkIntervalSeconds}s • Works with screen off",
                                "Интервал: ${settings.checkIntervalSeconds} с • Работает при выключенном экране"
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MutedSlateText
                        )
                    }
                }

                IconButton(
                    onClick = { onTogglePreview(!settings.showLivePreview) },
                    modifier = Modifier.testTag("toggle_preview_button")
                ) {
                    Icon(
                        imageVector = if (settings.showLivePreview) {
                            Icons.Rounded.Visibility
                        } else {
                            Icons.Rounded.VisibilityOff
                        },
                        contentDescription = tr(isRu, "Camera preview", "Превью камеры"),
                        tint = IceText
                    )
                }
            }

            if (settings.showLivePreview) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(ObsidianBg)
                        .border(1.dp, SlateBorder, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (state.isServiceRunning && !state.isDirectCamera2Active) {
                        AndroidView(
                            factory = { previewView },
                            modifier = Modifier.fillMaxSize()
                        )
                        DetectionOverlay(
                            boxes = state.lastBoxes,
                            mirrorHorizontal = settings.cameraLens == CameraLensOption.FRONT,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (state.lastSnapshotBitmap != null && !state.lastSnapshotBitmap.isRecycled) {
                        Image(
                            bitmap = state.lastSnapshotBitmap.asImageBitmap(),
                            contentDescription = tr(isRu, "Last frame", "Последний кадр"),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        if (state.isServiceRunning) {
                            DetectionOverlay(
                                boxes = state.lastBoxes,
                                mirrorHorizontal = settings.cameraLens == CameraLensOption.FRONT,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Videocam,
                                contentDescription = null,
                                tint = DimText,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = tr(
                                    isRu,
                                    "Place your phone on a stand. You can lock the screen after starting — the camera keeps checking every ${settings.checkIntervalSeconds}s.",
                                    "Поставьте телефон на подставку. Экран можно заблокировать после запуска — камера продолжит проверку каждые ${settings.checkIntervalSeconds} с."
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MutedSlateText
                            )
                        }
                    }
                }
            }

            if (state.lastError != null) {
                Text(
                    text = state.lastError,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AmberWarning
                )
            }

            val lanText = state.lastLanStatus.ifBlank { tr(isRu, "Waiting for signal", "Ожидание сигнала") }
            Text(
                text = "LAN: $lanText",
                style = MaterialTheme.typography.labelSmall,
                color = MutedSlateText
            )
        }
    }
}

@Composable
private fun DetectionOverlay(
    boxes: List<DetectionBox>,
    mirrorHorizontal: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        for (box in boxes) {
            val l = if (mirrorHorizontal) (1f - box.right) else box.left
            val r = if (mirrorHorizontal) (1f - box.left) else box.right
            val leftPx = l * size.width
            val topPx = box.top * size.height
            val widthPx = (r - l) * size.width
            val heightPx = (box.bottom - box.top) * size.height
            drawRect(
                color = SkyBlue,
                topLeft = Offset(leftPx, topPx),
                size = Size(widthPx, heightPx),
                style = Stroke(width = 3.dp.toPx())
            )
        }
    }
}
