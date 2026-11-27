package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.DetectorMode
import com.example.data.DeviceProfile
import com.example.data.PomidorSettings
import com.example.data.WorkdayClock
import com.example.hardware.CameraCatalog
import com.example.hardware.DeviceCameraOption
import com.example.hardware.TorchCapabilityInfo
import com.example.i18n.AppLanguage
import com.example.i18n.AppLocaleHelper.tr
import com.example.i18n.LocalIsRussian
import com.example.ui.components.BlueGradientButton
import com.example.ui.theme.CharcoalSurface
import com.example.ui.theme.DimText
import com.example.ui.theme.GoldenSeed
import com.example.ui.theme.IceText
import com.example.ui.theme.MintGreen
import com.example.ui.theme.MutedSlateText
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.RoyalBlue
import com.example.ui.theme.SkyBlue
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateCard
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: PomidorSettings,
    availableCameras: List<DeviceCameraOption>,
    torchCapability: TorchCapabilityInfo,
    computeTorchPulseMs: (Int) -> Long,
    isBusy: Boolean,
    onLanguageChange: (AppLanguage) -> Unit,
    onImportModel: (android.net.Uri) -> Unit,
    onClearModel: () -> Unit,
    onDetectorModeChange: (DetectorMode) -> Unit,
    onConfidenceChange: (Float) -> Unit,
    onFocusMinutesChange: (Int) -> Unit,
    onBreakMinutesChange: (Int) -> Unit,
    onCheckIntervalChange: (Int) -> Unit,
    onMaxMissedChecksChange: (Int) -> Unit,
    onWorkdayWindowChange: (Int, Int) -> Unit,
    onDeviceProfileChange: (DeviceProfile) -> Unit,
    onSelectCameraOption: (DeviceCameraOption) -> Unit,
    onRefreshCameras: () -> Unit,
    onTorchEnabledChange: (Boolean) -> Unit,
    onTorchCountChange: (Int) -> Unit,
    onTorchBrightnessChange: (Int) -> Unit,
    onTestTorch: () -> Unit,
    onCopiedNotice: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isRu = LocalIsRussian.current
    val modelPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onImportModel(uri)
        }
    }

    val matchedCamera = remember(availableCameras, settings.selectedCameraId, settings.cameraLens) {
        CameraCatalog.findMatch(availableCameras, settings.selectedCameraId, settings.cameraLens)
    }
    var draftBrightness by remember(settings.torchBrightnessPercent) {
        mutableFloatStateOf(settings.torchBrightnessPercent.toFloat())
    }
    val displayBrightness = draftBrightness.roundToInt().coerceIn(10, 100)

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
                    text = tr(isRu, "Settings & YOLO26", "Настройки и YOLO26"),
                    style = MaterialTheme.typography.headlineMedium,
                    color = IceText
                )
                Text(
                    text = tr(
                        isRu,
                        "Language, Infinix Note 10 Pro profile, cameras, torch, and AI model",
                        "Язык, профиль Infinix Note 10 Pro, камеры, фонарик и модель"
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedSlateText
                )
            }
        }

        // Language Selection Card
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.Language, contentDescription = null, tint = SkyBlue)
                        Text(
                            text = tr(isRu, "App Language / Язык", "Язык приложения / Language"),
                            style = MaterialTheme.typography.titleMedium,
                            color = IceText
                        )
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AppLanguage.entries.forEach { lang ->
                            FilterChip(
                                selected = settings.appLanguage == lang,
                                onClick = { onLanguageChange(lang) },
                                label = { Text(lang.label(isRu), style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.testTag("language_chip_${lang.name}"),
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
        }

        // 0. Device Profile (Infinix Note 10 Pro) & Multi-Camera
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.PhoneAndroid, contentDescription = null, tint = SkyBlue)
                            Text(
                                text = tr(isRu, "Device Profile & Cameras", "Профиль устройства и камеры"),
                                style = MaterialTheme.typography.titleMedium,
                                color = IceText
                            )
                        }
                        IconButton(onClick = onRefreshCameras) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = tr(isRu, "Refresh cameras", "Обновить список камер"),
                                tint = SkyBlue
                            )
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DeviceProfile.entries.forEach { profile ->
                            FilterChip(
                                selected = settings.deviceProfile == profile,
                                onClick = { onDeviceProfileChange(profile) },
                                label = { Text(profile.label(isRu), style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = RoyalBlue,
                                    selectedLabelColor = IceText,
                                    containerColor = SlateCard,
                                    labelColor = MutedSlateText
                                )
                            )
                        }
                    }

                    Text(
                        text = settings.deviceProfile.subtitle(isRu),
                        style = MaterialTheme.typography.bodyMedium,
                        color = SkyBlue
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Cameraswitch,
                            contentDescription = null,
                            tint = MutedSlateText,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = tr(
                                isRu,
                                "Available camera lenses (${availableCameras.size}):",
                                "Доступные модули камеры (${availableCameras.size}):"
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MutedSlateText
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        availableCameras.forEach { cam ->
                            val selected = matchedCamera?.optionKey == cam.optionKey
                            Surface(
                                onClick = { onSelectCameraOption(cam) },
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) RoyalBlue.copy(alpha = 0.25f) else SlateCard,
                                border = BorderStroke(1.dp, if (selected) SkyBlue else SlateBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = cam.label,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = if (selected) SkyBlue else IceText
                                        )
                                        Text(
                                            text = cam.subtitle,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MutedSlateText
                                        )
                                    }
                                    if (selected) {
                                        Text(
                                            text = tr(isRu, "Active", "Активна"),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MintGreen
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 1. Flashlight & Brightness Control
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
                            Icon(Icons.Rounded.FlashlightOn, contentDescription = null, tint = GoldenSeed)
                            Column {
                                Text(tr(isRu, "Flashlight & Brightness", "Фонарик и яркость"), style = MaterialTheme.typography.titleMedium, color = IceText)
                                Text(
                                    text = tr(
                                        isRu,
                                        "${settings.torchBlinkCount}× blinks • Brightness $displayBrightness%",
                                        "${settings.torchBlinkCount}× миганий • Яркость $displayBrightness%"
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MutedSlateText
                                )
                            }
                        }
                        Switch(
                            checked = settings.torchBlinkEnabled,
                            onCheckedChange = onTorchEnabledChange
                        )
                    }

                    if (settings.torchBlinkEnabled) {
                        Text(
                            text = tr(isRu, "Blink count:", "Количество миганий:"),
                            style = MaterialTheme.typography.labelMedium,
                            color = MutedSlateText
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            (1..5).forEach { cnt ->
                                FilterChip(
                                    selected = settings.torchBlinkCount == cnt,
                                    onClick = { onTorchCountChange(cnt) },
                                    label = { Text("${cnt}×") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = GoldenSeed,
                                        selectedLabelColor = ObsidianBg,
                                        containerColor = SlateCard,
                                        labelColor = IceText
                                    )
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            OutlinedButton(
                                onClick = onTestTorch,
                                border = BorderStroke(1.dp, SlateBorder)
                            ) {
                                Text(tr(isRu, "Test", "Тест"), color = IceText)
                            }
                        }

                        Text(
                            text = tr(
                                isRu,
                                "Flashlight brightness / power: $displayBrightness%",
                                "Яркость / мощность вспышки: $displayBrightness%"
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = IceText
                        )
                        Slider(
                            value = draftBrightness,
                            onValueChange = { draftBrightness = it },
                            onValueChangeFinished = {
                                onTorchBrightnessChange(draftBrightness.roundToInt().coerceIn(10, 100))
                            },
                            valueRange = 10f..100f,
                            modifier = Modifier.testTag("torch_brightness_slider"),
                            colors = SliderDefaults.colors(
                                thumbColor = SkyBlue,
                                activeTrackColor = RoyalBlue
                            )
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                10 to tr(isRu, "Min 10%", "Мин 10%"),
                                25 to tr(isRu, "Soft 25%", "Мягко 25%"),
                                60 to tr(isRu, "Med 60%", "Средне 60%"),
                                100 to tr(isRu, "Max 100%", "Макс 100%")
                            ).forEach { (pct, lbl) ->
                                FilterChip(
                                    selected = displayBrightness == pct,
                                    onClick = {
                                        draftBrightness = pct.toFloat()
                                        onTorchBrightnessChange(pct)
                                    },
                                    label = { Text(lbl, style = MaterialTheme.typography.labelSmall) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = RoyalBlue,
                                        selectedLabelColor = IceText,
                                        containerColor = SlateCard,
                                        labelColor = MutedSlateText
                                    )
                                )
                            }
                        }
                        Text(
                            text = if (torchCapability.supportsHardwareStrength) {
                                val lvl = (1.0 + ((displayBrightness - 10) / 90.0) * (torchCapability.maxStrengthLevel - 1))
                                    .roundToInt().coerceIn(1, torchCapability.maxStrengthLevel)
                                tr(
                                    isRu,
                                    "Android 13+ hardware torch dimming active (level $lvl of ${torchCapability.maxStrengthLevel}).",
                                    "Аппаратная регулировка Android 13+ активна (уровень $lvl из ${torchCapability.maxStrengthLevel})."
                                )
                            } else {
                                tr(
                                    isRu,
                                    "Android ${Build.VERSION.RELEASE} does not support hardware LED dimming — slider controls pulse duration (${computeTorchPulseMs(displayBrightness)} ms).",
                                    "На Android ${Build.VERSION.RELEASE} система не поддерживает снижение тока LED — слайдер регулирует длительность светового импульса (${computeTorchPulseMs(displayBrightness)} мс)."
                                )
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = DimText
                        )
                    }
                }
            }
        }

        // 2. YOLO26 Model Card
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.Memory, contentDescription = null, tint = SkyBlue)
                        Text(
                            text = tr(isRu, "YOLO26 Detection Model", "Модель распознавания YOLO26"),
                            style = MaterialTheme.typography.titleMedium,
                            color = IceText
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SlateCard,
                        border = BorderStroke(1.dp, SlateBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = if (settings.customModelName.isNotBlank()) {
                                    tr(isRu, "Loaded: ${settings.customModelName}", "Загружена: ${settings.customModelName}")
                                } else {
                                    tr(
                                        isRu,
                                        "No YOLO26 file imported — built-in detector active",
                                        "Файл YOLO26 не выбран — активен встроенный детектор"
                                    )
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = if (settings.customModelName.isNotBlank()) MintGreen else IceText
                            )
                            Text(
                                text = tr(
                                    isRu,
                                    "Supports NMS-free YOLO26 (.onnx with NNAPI or .tflite) and classic output for class 'person' (ID 0).",
                                    "Поддерживается NMS-free YOLO26 (.onnx с NNAPI или .tflite) и классический выход по классу person (ID 0)."
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MutedSlateText
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BlueGradientButton(
                            onClick = { modelPickerLauncher.launch(arrayOf("*/*")) },
                            enabled = !isBusy,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("import_yolo26_button")
                        ) {
                            Icon(Icons.Rounded.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(tr(isRu, "Import .onnx / .tflite", "Выбрать .onnx / .tflite"), fontWeight = FontWeight.SemiBold)
                        }

                        if (settings.customModelPath.isNotBlank()) {
                            OutlinedButton(
                                onClick = onClearModel,
                                modifier = Modifier.height(48.dp),
                                border = BorderStroke(1.dp, SlateBorder)
                            ) {
                                Icon(
                                    Icons.Rounded.DeleteOutline,
                                    contentDescription = tr(isRu, "Remove model", "Удалить модель"),
                                    tint = GoldenSeed
                                )
                            }
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DetectorMode.entries.forEach { mode ->
                            FilterChip(
                                selected = settings.detectorMode == mode,
                                onClick = { onDetectorModeChange(mode) },
                                label = { Text(mode.label(isRu), style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = RoyalBlue,
                                    selectedLabelColor = IceText,
                                    containerColor = SlateCard,
                                    labelColor = MutedSlateText
                                )
                            )
                        }
                    }

                    Text(
                        text = tr(
                            isRu,
                            "Confidence threshold: ${(settings.confidenceThreshold * 100).roundToInt()}%",
                            "Порог уверенности: ${(settings.confidenceThreshold * 100).roundToInt()}%"
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = IceText
                    )
                    Slider(
                        value = settings.confidenceThreshold,
                        onValueChange = onConfidenceChange,
                        valueRange = 0.25f..0.85f,
                        colors = SliderDefaults.colors(
                            thumbColor = SkyBlue,
                            activeTrackColor = RoyalBlue
                        )
                    )

                    val exportCmd = "yolo export model=yolo26n.pt format=onnx nms=False imgsz=640"
                    Surface(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("yolo26_export", exportCmd))
                            onCopiedNotice(tr(isRu, "YOLO26 export command copied", "Команда экспорта YOLO26 скопирована"))
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = ObsidianBg,
                        border = BorderStroke(1.dp, SlateBorder)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                            Text(
                                text = tr(isRu, "Export on PC (tap to copy):", "Экспорт с ПК (нажмите чтобы скопировать):"),
                                style = MaterialTheme.typography.labelSmall,
                                color = DimText
                            )
                            Text(
                                text = exportCmd,
                                style = MaterialTheme.typography.labelSmall,
                                color = SkyBlue
                            )
                        }
                    }
                }
            }
        }

        // 3. Configurable Timers & 1..5 Miss Tolerance + Workday Hours
        item {
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                border = BorderStroke(1.dp, SlateBorder)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.Schedule, contentDescription = null, tint = SkyBlue)
                        Text(
                            text = tr(isRu, "Timers & Workday Schedule", "Настраиваемый таймер и смена"),
                            style = MaterialTheme.typography.titleMedium,
                            color = IceText
                        )
                    }

                    ConfigurableStepperRow(
                        title = tr(isRu, "Focus duration", "Длительность фокуса"),
                        valueLabel = tr(isRu, "${settings.focusDurationMinutes} min", "${settings.focusDurationMinutes} мин"),
                        presets = listOf(10, 15, 20, 25, 45, 60),
                        currentValue = settings.focusDurationMinutes,
                        minValue = 1,
                        maxValue = 120,
                        isRu = isRu,
                        onValueChange = onFocusMinutesChange
                    )

                    ConfigurableStepperRow(
                        title = tr(isRu, "Break duration", "Длительность отдыха"),
                        valueLabel = tr(isRu, "${settings.breakDurationMinutes} min", "${settings.breakDurationMinutes} мин"),
                        presets = listOf(3, 5, 10, 15),
                        currentValue = settings.breakDurationMinutes,
                        minValue = 1,
                        maxValue = 60,
                        isRu = isRu,
                        onValueChange = onBreakMinutesChange
                    )

                    ConfigurableStepperRow(
                        title = tr(isRu, "Camera check interval", "Интервал кадра камеры"),
                        valueLabel = tr(isRu, "${settings.checkIntervalSeconds} sec", "${settings.checkIntervalSeconds} сек"),
                        presets = listOf(5, 10, 15, 30),
                        currentValue = settings.checkIntervalSeconds,
                        minValue = 5,
                        maxValue = 60,
                        isRu = isRu,
                        onValueChange = onCheckIntervalChange
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = tr(
                                isRu,
                                "Allowed consecutive misses (1–5): ${settings.maxMissedChecks}",
                                "Допустимых пропусков подряд (1–5): ${settings.maxMissedChecks}"
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            color = IceText
                        )
                        Text(
                            text = tr(
                                isRu,
                                "Session resets after ${settings.maxMissedChecks * settings.checkIntervalSeconds}s away from frame.",
                                "Сброс сессии через ${settings.maxMissedChecks * settings.checkIntervalSeconds} с отсутствия в кадре."
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MutedSlateText
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            (1..5).forEach { count ->
                                FilterChip(
                                    selected = settings.maxMissedChecks == count,
                                    onClick = { onMaxMissedChecksChange(count) },
                                    label = { Text("$count") },
                                    modifier = Modifier.weight(1f).testTag("misses_chip_$count"),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = GoldenSeed,
                                        selectedLabelColor = ObsidianBg,
                                        containerColor = SlateCard,
                                        labelColor = IceText
                                    )
                                )
                            }
                        }
                    }

                    Text(
                        text = tr(
                            isRu,
                            "Workday window: ${WorkdayClock.formatWindowLabel(settings.workdayStartHour, settings.workdayEndHour)}",
                            "Рабочий день: ${WorkdayClock.formatWindowLabel(settings.workdayStartHour, settings.workdayEndHour)}"
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = SkyBlue
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        HourStepper(
                            label = tr(isRu, "Shift Start", "Начало смены"),
                            hour = settings.workdayStartHour,
                            isRu = isRu,
                            onHourChange = { onWorkdayWindowChange(it, settings.workdayEndHour) },
                            modifier = Modifier.weight(1f)
                        )
                        HourStepper(
                            label = tr(isRu, "Shift End", "Конец смены"),
                            hour = settings.workdayEndHour,
                            isRu = isRu,
                            onHourChange = { onWorkdayWindowChange(settings.workdayStartHour, it) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConfigurableStepperRow(
    title: String,
    valueLabel: String,
    presets: List<Int>,
    currentValue: Int,
    minValue: Int,
    maxValue: Int,
    isRu: Boolean,
    onValueChange: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = IceText)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilledTonalIconButton(
                    onClick = { onValueChange((currentValue - 1).coerceIn(minValue, maxValue)) },
                    modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = SlateCard, contentColor = IceText)
                ) {
                    Icon(Icons.Rounded.Remove, contentDescription = tr(isRu, "Decrease", "Уменьшить"))
                }
                Text(
                    text = valueLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = SkyBlue
                )
                FilledTonalIconButton(
                    onClick = { onValueChange((currentValue + 1).coerceIn(minValue, maxValue)) },
                    modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = SlateCard, contentColor = IceText)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = tr(isRu, "Increase", "Увеличить"))
                }
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            presets.forEach { preset ->
                FilterChip(
                    selected = currentValue == preset,
                    onClick = { onValueChange(preset) },
                    label = { Text("$preset", style = MaterialTheme.typography.labelSmall) },
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

@Composable
private fun HourStepper(
    label: String,
    hour: Int,
    isRu: Boolean,
    onHourChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = SlateCard,
        border = BorderStroke(1.dp, SlateBorder)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = MutedSlateText)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalIconButton(
                    onClick = { onHourChange((hour + 23) % 24) },
                    modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = CharcoalSurface, contentColor = IceText)
                ) {
                    Icon(Icons.Rounded.Remove, contentDescription = tr(isRu, "Minus 1 hour", "Минус час"), modifier = Modifier.size(16.dp))
                }
                Text(
                    text = String.format(Locale.US, "%02d:00", hour),
                    style = MaterialTheme.typography.labelMedium,
                    color = IceText
                )
                FilledTonalIconButton(
                    onClick = { onHourChange((hour + 1) % 24) },
                    modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = CharcoalSurface, contentColor = IceText)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = tr(isRu, "Plus 1 hour", "Плюс час"), modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
