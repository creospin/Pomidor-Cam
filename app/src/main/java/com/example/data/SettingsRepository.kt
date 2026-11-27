package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.i18n.AppLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.pomidorDataStore: DataStore<Preferences> by preferencesDataStore(name = "pomidor_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        val CAMERA_LENS = stringPreferencesKey("camera_lens")
        val SELECTED_CAMERA_ID = stringPreferencesKey("selected_camera_id")
        val SELECTED_CAMERA_LABEL = stringPreferencesKey("selected_camera_label")
        val USE_ULTRAWIDE_ZOOM = booleanPreferencesKey("use_ultrawide_zoom")
        val DEVICE_PROFILE = stringPreferencesKey("device_profile")
        val CHECK_INTERVAL_SEC = intPreferencesKey("check_interval_sec")
        val FOCUS_DURATION_MIN = intPreferencesKey("focus_duration_min")
        val BREAK_DURATION_MIN = intPreferencesKey("break_duration_min")
        val MAX_MISSED_CHECKS = intPreferencesKey("max_missed_checks")
        val DETECTOR_MODE = stringPreferencesKey("detector_mode")
        val CUSTOM_MODEL_PATH = stringPreferencesKey("custom_model_path")
        val CUSTOM_MODEL_NAME = stringPreferencesKey("custom_model_name")
        val CONFIDENCE_THRESHOLD = floatPreferencesKey("confidence_threshold")
        val TORCH_BLINK_ENABLED = booleanPreferencesKey("torch_blink_enabled")
        val TORCH_BLINK_COUNT = intPreferencesKey("torch_blink_count")
        val TORCH_BRIGHTNESS_PERCENT = intPreferencesKey("torch_brightness_percent")
        val PC_HOST = stringPreferencesKey("pc_host")
        val PC_PORT = intPreferencesKey("pc_port")
        val LAN_PROTOCOL = stringPreferencesKey("lan_protocol")
        val WORKDAY_START_HOUR = intPreferencesKey("workday_start_hour")
        val WORKDAY_END_HOUR = intPreferencesKey("workday_end_hour")
        val SHOW_LIVE_PREVIEW = booleanPreferencesKey("show_live_preview")
        val AUTO_MORNING_EXPORT_ENABLED = booleanPreferencesKey("auto_morning_export_enabled")
        val AUTO_MORNING_EXPORT_HOUR = intPreferencesKey("auto_morning_export_hour")
        val LAST_AUTO_EXPORTED_WORKDAY = stringPreferencesKey("last_auto_exported_workday")
        val LAST_AUTO_EXPORT_STATUS = stringPreferencesKey("last_auto_export_status")
    }

    val settingsFlow: Flow<PomidorSettings> = context.pomidorDataStore.data.map { prefs ->
        PomidorSettings(
            appLanguage = prefs[Keys.APP_LANGUAGE]?.let {
                runCatching { AppLanguage.valueOf(it) }.getOrNull()
            } ?: AppLanguage.EN,
            cameraLens = prefs[Keys.CAMERA_LENS]?.let {
                runCatching { CameraLensOption.valueOf(it) }.getOrNull()
            } ?: CameraLensOption.FRONT,
            selectedCameraId = prefs[Keys.SELECTED_CAMERA_ID] ?: "AUTO_FRONT",
            selectedCameraLabel = prefs[Keys.SELECTED_CAMERA_LABEL] ?: "",
            useUltrawideZoomFallback = prefs[Keys.USE_ULTRAWIDE_ZOOM] ?: false,
            deviceProfile = prefs[Keys.DEVICE_PROFILE]?.let {
                runCatching { DeviceProfile.valueOf(it) }.getOrNull()
            } ?: DeviceProfile.INFINIX_NOTE_10_PRO,
            checkIntervalSeconds = (prefs[Keys.CHECK_INTERVAL_SEC] ?: 10).coerceIn(5, 60),
            focusDurationMinutes = (prefs[Keys.FOCUS_DURATION_MIN] ?: 20).coerceIn(1, 120),
            breakDurationMinutes = (prefs[Keys.BREAK_DURATION_MIN] ?: 5).coerceIn(1, 60),
            maxMissedChecks = (prefs[Keys.MAX_MISSED_CHECKS] ?: 2).coerceIn(1, 5),
            detectorMode = prefs[Keys.DETECTOR_MODE]?.let {
                runCatching { DetectorMode.valueOf(it) }.getOrNull()
            } ?: DetectorMode.AUTO,
            customModelPath = prefs[Keys.CUSTOM_MODEL_PATH] ?: "",
            customModelName = prefs[Keys.CUSTOM_MODEL_NAME] ?: "",
            confidenceThreshold = (prefs[Keys.CONFIDENCE_THRESHOLD] ?: 0.45f).coerceIn(0.20f, 0.90f),
            torchBlinkEnabled = prefs[Keys.TORCH_BLINK_ENABLED] ?: true,
            torchBlinkCount = (prefs[Keys.TORCH_BLINK_COUNT] ?: 3).coerceIn(1, 5),
            torchBrightnessPercent = (prefs[Keys.TORCH_BRIGHTNESS_PERCENT] ?: 80).coerceIn(10, 100),
            pcHost = prefs[Keys.PC_HOST] ?: "192.168.1.100",
            pcPort = (prefs[Keys.PC_PORT] ?: 8765).coerceIn(1024, 65535),
            lanProtocol = prefs[Keys.LAN_PROTOCOL]?.let {
                runCatching { LanProtocol.valueOf(it) }.getOrNull()
            } ?: LanProtocol.HTTP_AND_UDP,
            workdayStartHour = (prefs[Keys.WORKDAY_START_HOUR] ?: 6).coerceIn(0, 23),
            workdayEndHour = (prefs[Keys.WORKDAY_END_HOUR] ?: 4).coerceIn(0, 23),
            showLivePreview = prefs[Keys.SHOW_LIVE_PREVIEW] ?: true,
            autoMorningExportEnabled = prefs[Keys.AUTO_MORNING_EXPORT_ENABLED] ?: true,
            autoMorningExportHour = (prefs[Keys.AUTO_MORNING_EXPORT_HOUR] ?: 8).coerceIn(6, 12),
            lastAutoExportedWorkday = prefs[Keys.LAST_AUTO_EXPORTED_WORKDAY] ?: "",
            lastAutoExportStatus = prefs[Keys.LAST_AUTO_EXPORT_STATUS] ?: ""
        )
    }

    suspend fun updateAppLanguage(language: AppLanguage) {
        context.pomidorDataStore.edit { it[Keys.APP_LANGUAGE] = language.name }
    }

    suspend fun updateCameraSelection(
        lens: CameraLensOption,
        cameraId: String,
        label: String,
        useUltrawideZoomFallback: Boolean
    ) {
        context.pomidorDataStore.edit {
            it[Keys.CAMERA_LENS] = lens.name
            it[Keys.SELECTED_CAMERA_ID] = cameraId
            it[Keys.SELECTED_CAMERA_LABEL] = label
            it[Keys.USE_ULTRAWIDE_ZOOM] = useUltrawideZoomFallback
        }
    }

    suspend fun updateDeviceProfile(profile: DeviceProfile) {
        context.pomidorDataStore.edit { it[Keys.DEVICE_PROFILE] = profile.name }
    }

    suspend fun updateCheckIntervalSeconds(seconds: Int) {
        context.pomidorDataStore.edit { it[Keys.CHECK_INTERVAL_SEC] = seconds.coerceIn(5, 60) }
    }

    suspend fun updateFocusDurationMinutes(minutes: Int) {
        context.pomidorDataStore.edit { it[Keys.FOCUS_DURATION_MIN] = minutes.coerceIn(1, 120) }
    }

    suspend fun updateBreakDurationMinutes(minutes: Int) {
        context.pomidorDataStore.edit { it[Keys.BREAK_DURATION_MIN] = minutes.coerceIn(1, 60) }
    }

    suspend fun updateMaxMissedChecks(misses: Int) {
        context.pomidorDataStore.edit { it[Keys.MAX_MISSED_CHECKS] = misses.coerceIn(1, 5) }
    }

    suspend fun updateDetectorMode(mode: DetectorMode) {
        context.pomidorDataStore.edit { it[Keys.DETECTOR_MODE] = mode.name }
    }

    suspend fun updateCustomModel(path: String, name: String) {
        context.pomidorDataStore.edit {
            it[Keys.CUSTOM_MODEL_PATH] = path
            it[Keys.CUSTOM_MODEL_NAME] = name
        }
    }

    suspend fun updateConfidenceThreshold(threshold: Float) {
        context.pomidorDataStore.edit {
            it[Keys.CONFIDENCE_THRESHOLD] = threshold.coerceIn(0.20f, 0.90f)
        }
    }

    suspend fun updateTorchBlinkEnabled(enabled: Boolean) {
        context.pomidorDataStore.edit { it[Keys.TORCH_BLINK_ENABLED] = enabled }
    }

    suspend fun updateTorchBlinkCount(count: Int) {
        context.pomidorDataStore.edit { it[Keys.TORCH_BLINK_COUNT] = count.coerceIn(1, 5) }
    }

    suspend fun updateTorchBrightnessPercent(percent: Int) {
        context.pomidorDataStore.edit { it[Keys.TORCH_BRIGHTNESS_PERCENT] = percent.coerceIn(10, 100) }
    }

    suspend fun updatePcEndpoint(host: String, port: Int, protocol: LanProtocol) {
        context.pomidorDataStore.edit {
            it[Keys.PC_HOST] = host.trim()
            it[Keys.PC_PORT] = port.coerceIn(1024, 65535)
            it[Keys.LAN_PROTOCOL] = protocol.name
        }
    }

    suspend fun updateWorkdayWindow(startHour: Int, endHour: Int) {
        context.pomidorDataStore.edit {
            it[Keys.WORKDAY_START_HOUR] = startHour.coerceIn(0, 23)
            it[Keys.WORKDAY_END_HOUR] = endHour.coerceIn(0, 23)
        }
    }

    suspend fun updateShowLivePreview(show: Boolean) {
        context.pomidorDataStore.edit { it[Keys.SHOW_LIVE_PREVIEW] = show }
    }

    suspend fun updateAutoMorningExport(enabled: Boolean, hour: Int) {
        context.pomidorDataStore.edit {
            it[Keys.AUTO_MORNING_EXPORT_ENABLED] = enabled
            it[Keys.AUTO_MORNING_EXPORT_HOUR] = hour.coerceIn(6, 12)
        }
    }

    suspend fun recordAutoExportOutcome(exportedWorkday: String?, status: String) {
        context.pomidorDataStore.edit {
            if (exportedWorkday != null) {
                it[Keys.LAST_AUTO_EXPORTED_WORKDAY] = exportedWorkday
            }
            it[Keys.LAST_AUTO_EXPORT_STATUS] = status
        }
    }
}
