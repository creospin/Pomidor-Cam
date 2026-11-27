package com.example.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.DeviceProfile
import com.example.data.DetectorMode
import com.example.data.LanProtocol
import com.example.data.PomidorDatabase
import com.example.data.PomidorSettings
import com.example.data.SessionRepository
import com.example.data.SettingsRepository
import com.example.data.WorkSessionEntity
import com.example.data.WorkdayClock
import com.example.data.WorkdayReportBuilder
import com.example.data.WorkdayReportPayload
import com.example.data.WorkdayReportSummary
import com.example.hardware.CameraCatalog
import com.example.hardware.DeviceCameraOption
import com.example.hardware.TorchCapabilityInfo
import com.example.hardware.TorchController
import com.example.i18n.AppLanguage
import com.example.i18n.AppLocaleHelper
import com.example.ml.PersonDetectorManager
import com.example.network.AutoExportOutcome
import com.example.network.LanNotifier
import com.example.network.MorningAutoExportManager
import com.example.network.MorningExportScheduler
import com.example.network.PcScriptProvider
import com.example.service.MonitorStateHolder
import com.example.service.MonitorUiState
import com.example.service.PomidorMonitorService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class ActionBanner(
    val message: String,
    val isError: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class PomidorViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application)
    private val sessionRepository = SessionRepository(
        PomidorDatabase.getInstance(application).workSessionDao()
    )
    private val detectorManager = PersonDetectorManager(application)
    private val torchController = TorchController(application)
    private val lanNotifier = LanNotifier()

    val torchCapability: TorchCapabilityInfo = torchController.getCapabilityInfo()
    val supportsNativeTorchStrength: Boolean = torchCapability.supportsHardwareStrength

    private val _availableCameras = MutableStateFlow(
        CameraCatalog.discoverCameras(application, AppLocaleHelper.isRussian())
    )
    val availableCameras: StateFlow<List<DeviceCameraOption>> = _availableCameras.asStateFlow()

    val settings: StateFlow<PomidorSettings> = settingsRepository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PomidorSettings())

    val monitorState: StateFlow<MonitorUiState> = MonitorStateHolder.state

    private val _selectedWorkday = MutableStateFlow(WorkdayClock.getWorkdayDate(System.currentTimeMillis(), 6))
    val selectedWorkday: StateFlow<String> = _selectedWorkday.asStateFlow()

    private val _actionBanner = MutableStateFlow<ActionBanner?>(null)
    val actionBanner: StateFlow<ActionBanner?> = _actionBanner.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    val availableWorkdays: StateFlow<List<String>> = combine(
        sessionRepository.observeWorkdayDates(),
        settings
    ) { dbDates, currentSettings ->
        val today = WorkdayClock.getWorkdayDate(System.currentTimeMillis(), currentSettings.workdayStartHour)
        (listOf(today) + dbDates).distinct().sortedDescending()
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        listOf(WorkdayClock.getWorkdayDate(System.currentTimeMillis(), 6))
    )

    val workdaySessions: StateFlow<List<WorkSessionEntity>> = _selectedWorkday
        .flatMapLatest { date -> sessionRepository.observeSessionsForWorkday(date) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val workdaySummary: StateFlow<WorkdayReportSummary> = combine(
        _selectedWorkday,
        workdaySessions,
        settings
    ) { date, sessions, s ->
        WorkdayReportBuilder.summarize(
            workdayDate = date,
            sessions = sessions,
            startHour = s.workdayStartHour,
            endHour = s.workdayEndHour
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        WorkdayReportBuilder.summarize(_selectedWorkday.value, emptyList())
    )

    init {
        viewModelScope.launch {
            settingsRepository.settingsFlow
                .distinctUntilChangedBy { it.appLanguage }
                .collect { s ->
                    _availableCameras.value = CameraCatalog.discoverCameras(
                        getApplication(),
                        AppLocaleHelper.isRussian(s.appLanguage)
                    )
                }
        }
        viewModelScope.launch {
            settingsRepository.settingsFlow
                .distinctUntilChangedBy { it.autoMorningExportEnabled to it.autoMorningExportHour }
                .collect { s ->
                    MorningExportScheduler.syncSchedule(getApplication(), s)
                }
        }
        viewModelScope.launch(Dispatchers.IO) {
            val outcome = MorningAutoExportManager.evaluateAndExportIfDue(getApplication())
            if (outcome is AutoExportOutcome.Exported) {
                val lang = settingsRepository.settingsFlow.first().appLanguage
                val isRu = AppLocaleHelper.isRussian(lang)
                MonitorStateHolder.update { it.copy(lastLanStatus = outcome.message) }
                val msg = if (isRu) {
                    "Утренний автоотчёт за ${outcome.workdayDate} выгружен на ПК"
                } else {
                    "Morning report for ${outcome.workdayDate} exported to PC"
                }
                _actionBanner.value = ActionBanner(msg)
            }
        }
    }

    private fun isRu(): Boolean = AppLocaleHelper.isRussian(settings.value.appLanguage)

    fun setAppLanguage(language: AppLanguage) {
        viewModelScope.launch {
            settingsRepository.updateAppLanguage(language)
            val ru = AppLocaleHelper.isRussian(language)
            _availableCameras.value = CameraCatalog.discoverCameras(getApplication(), ru)
        }
    }

    fun refreshAvailableCameras() {
        _availableCameras.value = CameraCatalog.discoverCameras(getApplication(), isRu())
    }

    fun dismissBanner() {
        _actionBanner.value = null
    }

    fun selectWorkday(workdayDate: String) {
        _selectedWorkday.value = workdayDate
    }

    fun startMonitoring() {
        refreshAvailableCameras()
        PomidorMonitorService.start(getApplication())
    }

    fun stopMonitoring() {
        PomidorMonitorService.stop(getApplication())
    }

    fun selectCameraOption(option: DeviceCameraOption) {
        viewModelScope.launch {
            settingsRepository.updateCameraSelection(
                lens = option.lensOption,
                cameraId = option.optionKey,
                label = option.label,
                useUltrawideZoomFallback = option.useMinZoomRatio
            )
            val prefix = if (isRu()) "Выбрана камера" else "Selected camera"
            _actionBanner.value = ActionBanner("$prefix: ${option.label} (${option.subtitle})")
        }
    }

    fun cycleCameraOption() {
        val list = _availableCameras.value
        if (list.isEmpty()) return
        val s = settings.value
        val matched = CameraCatalog.findMatch(list, s.selectedCameraId, s.cameraLens)
        val currentIdx = if (matched != null) list.indexOf(matched).coerceAtLeast(0) else 0
        val next = list[(currentIdx + 1) % list.size]
        selectCameraOption(next)
    }

    fun setDeviceProfile(profile: DeviceProfile) {
        viewModelScope.launch {
            settingsRepository.updateDeviceProfile(profile)
            if (profile == DeviceProfile.INFINIX_NOTE_10_PRO) {
                refreshAvailableCameras()
                val msg = if (isRu()) {
                    "Активирован профиль Infinix Note 10 Pro (Helio G95 / 8 ГБ)"
                } else {
                    "Infinix Note 10 Pro profile enabled (Helio G95 / 8 GB)"
                }
                _actionBanner.value = ActionBanner(msg)
            }
        }
    }

    fun setShowLivePreview(show: Boolean) {
        viewModelScope.launch { settingsRepository.updateShowLivePreview(show) }
    }

    fun setCheckIntervalSeconds(seconds: Int) {
        viewModelScope.launch { settingsRepository.updateCheckIntervalSeconds(seconds) }
    }

    fun setFocusDurationMinutes(minutes: Int) {
        viewModelScope.launch { settingsRepository.updateFocusDurationMinutes(minutes) }
    }

    fun setBreakDurationMinutes(minutes: Int) {
        viewModelScope.launch { settingsRepository.updateBreakDurationMinutes(minutes) }
    }

    fun setMaxMissedChecks(misses: Int) {
        viewModelScope.launch { settingsRepository.updateMaxMissedChecks(misses) }
    }

    fun setDetectorMode(mode: DetectorMode) {
        viewModelScope.launch { settingsRepository.updateDetectorMode(mode) }
    }

    fun setConfidenceThreshold(threshold: Float) {
        viewModelScope.launch { settingsRepository.updateConfidenceThreshold(threshold) }
    }

    fun setTorchBlinkEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateTorchBlinkEnabled(enabled) }
    }

    fun setTorchBlinkCount(count: Int) {
        viewModelScope.launch { settingsRepository.updateTorchBlinkCount(count) }
    }

    fun setTorchBrightnessPercent(percent: Int) {
        viewModelScope.launch { settingsRepository.updateTorchBrightnessPercent(percent) }
    }

    fun setWorkdayWindow(startHour: Int, endHour: Int) {
        viewModelScope.launch {
            settingsRepository.updateWorkdayWindow(startHour, endHour)
            _selectedWorkday.value = WorkdayClock.getWorkdayDate(System.currentTimeMillis(), startHour)
        }
    }

    fun savePcEndpoint(host: String, portText: String, protocol: LanProtocol) {
        val port = portText.toIntOrNull()?.coerceIn(1024, 65535) ?: 8765
        viewModelScope.launch {
            settingsRepository.updatePcEndpoint(host, port, protocol)
            val prefix = if (isRu()) "Настройки ПК сохранены" else "PC settings saved"
            _actionBanner.value = ActionBanner("$prefix: ${host.trim()}:$port")
        }
    }

    fun computeTorchPulseMs(percent: Int): Long = torchController.computePulseDurationMs(percent)

    fun testTorchBlink() {
        val s = settings.value
        val ru = isRu()
        if (MonitorStateHolder.state.value.isServiceRunning) {
            MonitorStateHolder.onTorchTestFeedback = { res ->
                val torchWord = if (ru) "Фонарик" else "Torch"
                val errWord = if (ru) "Ошибка вспышки" else "Flash error"
                val unavail = if (ru) "недоступна" else "unavailable"
                _actionBanner.value = res.fold(
                    onSuccess = { detail -> ActionBanner("$torchWord ${s.torchBlinkCount}× • $detail") },
                    onFailure = { e -> ActionBanner("$errWord: ${e.message ?: unavail}", isError = true) }
                )
                MonitorStateHolder.onTorchTestFeedback = null
            }
            PomidorMonitorService.testTorch(getApplication())
            return
        }

        viewModelScope.launch {
            val res = torchController.blink(
                times = s.torchBlinkCount,
                brightnessPercent = s.torchBrightnessPercent,
                isRu = ru
            )
            val torchWord = if (ru) "Фонарик" else "Torch"
            val errWord = if (ru) "Ошибка вспышки" else "Flash error"
            val unavail = if (ru) "недоступна" else "unavailable"
            _actionBanner.value = res.fold(
                onSuccess = { detail -> ActionBanner("$torchWord ${s.torchBlinkCount}× • $detail") },
                onFailure = { e -> ActionBanner("$errWord: ${e.message ?: unavail}", isError = true) }
            )
        }
    }

    fun pingPc() {
        viewModelScope.launch {
            _isBusy.value = true
            val res = lanNotifier.pingPc(settings.value)
            _isBusy.value = false
            MonitorStateHolder.update { it.copy(lastLanStatus = res.message) }
            _actionBanner.value = ActionBanner(res.message, isError = !res.success)
        }
    }

    fun testLanBreakSignal() {
        viewModelScope.launch {
            _isBusy.value = true
            val res = lanNotifier.sendBreakSignal(
                settings = settings.value,
                focusMinutes = settings.value.focusDurationMinutes,
                isTest = true
            )
            _isBusy.value = false
            MonitorStateHolder.update { it.copy(lastLanStatus = res.message) }
            _actionBanner.value = ActionBanner(res.message, isError = !res.success)
        }
    }

    fun importYolo26Model(uri: Uri) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = detectorManager.importModelFromUri(uri)
            _isBusy.value = false
            val ru = isRu()
            res.onSuccess { info ->
                settingsRepository.updateCustomModel(info.path, info.displayName)
                settingsRepository.updateDetectorMode(info.detectedMode)
                val msg = if (ru) {
                    "Модель ${info.displayName} загружена (${info.detectedMode.label(true)})"
                } else {
                    "Model ${info.displayName} loaded (${info.detectedMode.label(false)})"
                }
                _actionBanner.value = ActionBanner(msg)
            }.onFailure { e ->
                val prefix = if (ru) "Не удалось загрузить модель" else "Failed to load model"
                _actionBanner.value = ActionBanner("$prefix: ${e.message}", isError = true)
            }
        }
    }

    fun clearCustomModel() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { detectorManager.clearCustomModel() }
            settingsRepository.updateCustomModel("", "")
            settingsRepository.updateDetectorMode(DetectorMode.AUTO)
            val msg = if (isRu()) {
                "Пользовательская модель удалена, включён встроенный детектор"
            } else {
                "Custom model removed, built-in detector active"
            }
            _actionBanner.value = ActionBanner(msg)
        }
    }

    fun exportSelectedWorkdayToPc() {
        viewModelScope.launch {
            _isBusy.value = true
            val payload = buildCurrentReportPayload()
            val res = lanNotifier.sendWorkdayReport(
                settings = settings.value,
                workdayDate = payload.summary.workdayDate,
                reportJson = payload.jsonString
            )
            _isBusy.value = false
            MonitorStateHolder.update { it.copy(lastLanStatus = res.message) }
            _actionBanner.value = ActionBanner(res.message, isError = !res.success)
        }
    }

    fun setAutoMorningExport(enabled: Boolean, hour: Int) {
        viewModelScope.launch {
            val safeHour = hour.coerceIn(6, 12)
            settingsRepository.updateAutoMorningExport(enabled, safeHour)
            val ru = isRu()
            _actionBanner.value = if (enabled) {
                val fmt = if (ru) "Автовыгрузка включена на %02d:00" else "Morning auto-export enabled for %02d:00"
                ActionBanner(String.format(java.util.Locale.US, fmt, safeHour))
            } else {
                ActionBanner(if (ru) "Автовыгрузка утром отключена" else "Morning auto-export disabled")
            }
        }
    }

    fun triggerYesterdayAutoExportNow() {
        viewModelScope.launch {
            _isBusy.value = true
            val outcome = withContext(Dispatchers.IO) {
                MorningAutoExportManager.evaluateAndExportIfDue(
                    context = getApplication(),
                    forceNow = true
                )
            }
            _isBusy.value = false
            val ru = isRu()
            _actionBanner.value = when (outcome) {
                is AutoExportOutcome.Exported -> {
                    MonitorStateHolder.update { it.copy(lastLanStatus = outcome.message) }
                    ActionBanner(outcome.message)
                }
                is AutoExportOutcome.NoSessions -> ActionBanner(
                    if (ru) "За ${outcome.workdayDate} нет записанных сессий" else "No sessions recorded on ${outcome.workdayDate}"
                )
                is AutoExportOutcome.RetryLater -> {
                    MonitorStateHolder.update { it.copy(lastLanStatus = outcome.message) }
                    ActionBanner(outcome.message, isError = true)
                }
                is AutoExportOutcome.Failed -> ActionBanner(outcome.message, isError = true)
                else -> ActionBanner(if (ru) "Автовыгрузка выполнена" else "Auto-export complete")
            }
        }
    }

    suspend fun buildCurrentReportPayload(): WorkdayReportPayload {
        val date = _selectedWorkday.value
        val s = settings.value
        val asc = withContext(Dispatchers.IO) {
            sessionRepository.getSessionsForWorkdayAsc(date)
        }
        return WorkdayReportBuilder.buildPayload(
            workdayDate = date,
            sessionsAsc = asc,
            startHour = s.workdayStartHour,
            endHour = s.workdayEndHour,
            isRu = AppLocaleHelper.isRussian(s.appLanguage)
        )
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            sessionRepository.deleteSessionById(sessionId)
        }
    }

    fun writeTextToUri(uri: Uri, content: String, successMsg: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val ru = isRu()
            val res = runCatching {
                getApplication<Application>().contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(content.toByteArray(Charsets.UTF_8))
                } ?: error(if (ru) "Не удалось записать файл" else "Failed to write file")
            }
            _actionBanner.value = res.fold(
                onSuccess = { ActionBanner(successMsg) },
                onFailure = { e ->
                    val prefix = if (ru) "Ошибка сохранения" else "Save error"
                    ActionBanner("$prefix: ${e.message}", isError = true)
                }
            )
        }
    }

    fun createShareIntent(fileName: String, content: String, mimeType: String): Intent? {
        return runCatching {
            val app = getApplication<Application>()
            val exportDir = File(app.cacheDir, "exports").apply { mkdirs() }
            val file = File(exportDir, fileName)
            file.writeText(content, Charsets.UTF_8)
            val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
            Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, fileName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }.getOrNull()
    }

    fun getPythonScript(isRu: Boolean = isRu()): String {
        return PcScriptProvider.generatePythonScript(settings.value.pcPort, isRu)
    }

    override fun onCleared() {
        MonitorStateHolder.onTorchTestFeedback = null
        detectorManager.close()
        super.onCleared()
    }
}
