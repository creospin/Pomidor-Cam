package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.util.Size
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.example.MainActivity
import com.example.R
import com.example.data.CameraLensOption
import com.example.data.PomidorDatabase
import com.example.data.PomidorSettings
import com.example.data.SessionRepository
import com.example.data.SettingsRepository
import com.example.data.WorkdayClock
import com.example.hardware.CameraCatalog
import com.example.hardware.DeviceCameraOption
import com.example.hardware.DirectCamera2Capture
import com.example.hardware.TorchController
import com.example.i18n.AppLocaleHelper
import com.example.ml.BitmapUtils
import com.example.ml.PersonDetectorManager
import com.example.network.AutoExportOutcome
import com.example.network.LanNotifier
import com.example.network.MorningAutoExportManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class PomidorMonitorService : LifecycleService() {

    companion object {
        const val ACTION_START = "com.example.pomidor.ACTION_START"
        const val ACTION_STOP = "com.example.pomidor.ACTION_STOP"
        const val ACTION_TEST_TORCH = "com.example.pomidor.ACTION_TEST_TORCH"
        private const val CHANNEL_ID = "pomidor_monitor_silent"
        private const val NOTIFICATION_ID = 4026

        fun start(context: Context) {
            val intent = Intent(context, PomidorMonitorService::class.java).apply {
                action = ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, PomidorMonitorService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun testTorch(context: Context) {
            val intent = Intent(context, PomidorMonitorService::class.java).apply {
                action = ACTION_TEST_TORCH
            }
            context.startService(intent)
        }
    }

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var sessionRepository: SessionRepository
    private lateinit var detectorManager: PersonDetectorManager
    private lateinit var torchController: TorchController
    private lateinit var lanNotifier: LanNotifier

    private val sessionEngine = PomodoroSessionEngine()
    private val isProcessingFrame = AtomicBoolean(false)

    private var cameraProvider: ProcessCameraProvider? = null
    private var boundCamera: Camera? = null
    private var directCamera2: DirectCamera2Capture? = null
    private var analysisExecutor: ExecutorService? = null
    private var tickerJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    @Volatile
    private var currentSettings: PomidorSettings = PomidorSettings()

    @Volatile
    private var nextCheckElapsedMs: Long = 0L

    @Volatile
    private var boundCameraKey: String? = null

    @Volatile
    private var boundPreviewAttached: Boolean = false

    private var lastNotificationPhase: SessionPhase? = null
    private var lastAutoExportCheckElapsedMs: Long = 0L

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(applicationContext)
        sessionRepository = SessionRepository(PomidorDatabase.getInstance(applicationContext).workSessionDao())
        detectorManager = PersonDetectorManager(applicationContext)
        torchController = TorchController(applicationContext)
        lanNotifier = LanNotifier()
        analysisExecutor = Executors.newSingleThreadExecutor()

        createSilentNotificationChannel()
        acquireWakeLock()

        MonitorStateHolder.onPreviewSurfaceChanged = { provider ->
            lifecycleScope.launch(Dispatchers.Main) {
                if (MonitorStateHolder.state.value.isServiceRunning && directCamera2 == null) {
                    bindCameraUseCases(forceRebind = provider != null || boundPreviewAttached)
                }
            }
        }

        lifecycleScope.launch {
            settingsRepository.settingsFlow.collectLatest { settings ->
                val targetCamKey = cameraKey(settings)
                val lensChanged = boundCameraKey != targetCamKey
                val previewSettingChanged =
                    boundPreviewAttached != (settings.showLivePreview && MonitorStateHolder.previewSurfaceProvider != null)
                currentSettings = settings
                sessionEngine.syncIdleDefaults(settings)
                publishEngineSnapshot(settings)
                if ((lensChanged || previewSettingChanged) && MonitorStateHolder.state.value.isServiceRunning) {
                    withContext(Dispatchers.Main) {
                        bindCameraUseCases(forceRebind = true)
                    }
                }
            }
        }
    }

    private fun cameraKey(settings: PomidorSettings): String {
        return "${settings.cameraLens.name}:${settings.selectedCameraId}:${settings.useUltrawideZoomFallback}"
    }

    private fun publishEngineSnapshot(settings: PomidorSettings) {
        val snap = sessionEngine.snapshot(settings)
        MonitorStateHolder.update {
            it.copy(
                phase = snap.phase,
                remainingSeconds = snap.remainingSeconds,
                totalPhaseSeconds = snap.totalPhaseSeconds,
                consecutiveMisses = snap.consecutiveMisses,
                maxMissedChecks = snap.maxMissedChecks
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> {
                stopMonitoringAndSelf()
                return START_NOT_STICKY
            }
            ACTION_TEST_TORCH -> {
                lifecycleScope.launch {
                    val res = blinkWithActiveCamera(currentSettings)
                    MonitorStateHolder.onTorchTestFeedback?.invoke(res)
                }
                return START_STICKY
            }
        }

        startInForeground()
        if (!MonitorStateHolder.state.value.isServiceRunning) {
            val isRu = AppLocaleHelper.isRussian(currentSettings.appLanguage)
            MonitorStateHolder.update { it.copy(isServiceRunning = true, lastError = null) }
            MonitorStateHolder.appendLog(
                "${WorkdayClock.formatTime(System.currentTimeMillis())} • ${if (isRu) "Мониторинг запущен" else "Monitoring started"}"
            )
            nextCheckElapsedMs = SystemClock.elapsedRealtime() + 1200L
            bindCameraUseCases(forceRebind = true)
            startTicker()
        }
        return START_STICKY
    }

    private fun startInForeground() {
        val notification = buildSilentNotification(SessionPhase.IDLE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createSilentNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildSilentNotification(phase: SessionPhase): Notification {
        val locCtx = AppLocaleHelper.localizedContext(this, currentSettings.appLanguage)
        val isRu = AppLocaleHelper.isRussian(currentSettings.appLanguage)

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentPending = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, PomidorMonitorService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = when (phase) {
            SessionPhase.IDLE -> locCtx.getString(R.string.notification_title_idle)
            SessionPhase.FOCUSING, SessionPhase.GRACE_PERIOD -> locCtx.getString(R.string.notification_title_focus)
            SessionPhase.BREAK -> locCtx.getString(R.string.notification_title_break)
        }
        val camName = currentSettings.selectedCameraLabel.ifBlank { currentSettings.cameraLens.label(isRu) }
        val text = if (isRu) {
            "$camName • каждые ${currentSettings.checkIntervalSeconds}с"
        } else {
            "$camName • every ${currentSettings.checkIntervalSeconds}s"
        }
        val stopLabel = if (isRu) "Стоп" else "Stop"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_tomato)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentPending)
            .addAction(0, stopLabel, stopPending)
            .build()
    }

    private fun updateSilentNotificationIfPhaseChanged(phase: SessionPhase) {
        if (phase != lastNotificationPhase) {
            lastNotificationPhase = phase
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTIFICATION_ID, buildSilentNotification(phase))
        }
    }

    private fun resolveTargetOption(settings: PomidorSettings): DeviceCameraOption? {
        val isRu = AppLocaleHelper.isRussian(settings.appLanguage)
        val catalog = CameraCatalog.discoverCameras(this, isRu)
        return CameraCatalog.findMatch(catalog, settings.selectedCameraId, settings.cameraLens)
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun buildCameraSelector(
        provider: ProcessCameraProvider,
        matched: DeviceCameraOption?,
        settings: PomidorSettings
    ): Triple<CameraSelector, Float?, Boolean> {
        val targetId = matched?.cameraId
        val zoomTarget = if (matched?.useMinZoomRatio == true || settings.useUltrawideZoomFallback) {
            matched?.minZoomRatio ?: 0.6f
        } else {
            null
        }

        if (matched?.isDirectCamera2Only == true || matched?.physicalCameraId != null) {
            return Triple(CameraSelector.DEFAULT_BACK_CAMERA, zoomTarget, true)
        }

        if (targetId != null && !targetId.startsWith("AUTO_")) {
            val hasMatch = provider.availableCameraInfos.any {
                Camera2CameraInfo.from(it).cameraId == targetId
            }
            if (hasMatch) {
                val selector = CameraSelector.Builder()
                    .addCameraFilter { infos ->
                        infos.filter { Camera2CameraInfo.from(it).cameraId == targetId }
                    }
                    .build()
                return Triple(selector, zoomTarget, false)
            } else {
                // Camera ID exists in Camera2 catalog but CameraX excluded it -> open via Camera2 directly
                return Triple(CameraSelector.DEFAULT_BACK_CAMERA, zoomTarget, true)
            }
        }

        val fallbackSelector = if (settings.cameraLens == CameraLensOption.FRONT) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
        return Triple(fallbackSelector, zoomTarget, false)
    }

    private fun releaseAllCameras() {
        runCatching { directCamera2?.close() }
        directCamera2 = null
        runCatching { cameraProvider?.unbindAll() }
        boundCamera = null
    }

    private fun bindCameraUseCases(forceRebind: Boolean = false) {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = runCatching { future.get() }.getOrElse { e ->
                val isRu = AppLocaleHelper.isRussian(currentSettings.appLanguage)
                MonitorStateHolder.update {
                    it.copy(lastError = "${if (isRu) "Камера" else "Camera"}: ${e.message}")
                }
                return@addListener
            }
            cameraProvider = provider
            val settingsSnapshot = currentSettings
            val targetKey = cameraKey(settingsSnapshot)
            val surfaceProvider = if (settingsSnapshot.showLivePreview) {
                MonitorStateHolder.previewSurfaceProvider
            } else null
            val wantPreview = surfaceProvider != null

            if (!forceRebind &&
                boundCameraKey == targetKey &&
                boundPreviewAttached == wantPreview &&
                (boundCamera != null || directCamera2 != null)
            ) {
                return@addListener
            }

            val matched = resolveTargetOption(settingsSnapshot)
            val (selector, targetZoomRatio, useDirectCamera2) = buildCameraSelector(provider, matched, settingsSnapshot)

            if (useDirectCamera2 && matched != null && !matched.cameraId.startsWith("AUTO_")) {
                releaseAllCameras()
                startDirectCamera2(matched, targetKey, wantPreview)
                return@addListener
            }

            runCatching { directCamera2?.close() }
            directCamera2 = null
            MonitorStateHolder.update { it.copy(isDirectCamera2Active = false) }

            val resolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(
                        Size(640, 480),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                    )
                )
                .build()

            val imageAnalysis = ImageAnalysis.Builder()
                .setResolutionSelector(resolutionSelector)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            val executor = analysisExecutor ?: Executors.newSingleThreadExecutor().also {
                analysisExecutor = it
            }

            imageAnalysis.setAnalyzer(executor) { imageProxy ->
                val nowElapsed = SystemClock.elapsedRealtime()
                if (nowElapsed < nextCheckElapsedMs || !isProcessingFrame.compareAndSet(false, true)) {
                    imageProxy.close()
                    return@setAnalyzer
                }

                val intervalMs = currentSettings.checkIntervalSeconds * 1000L
                nextCheckElapsedMs = nowElapsed + intervalMs

                val bitmap: Bitmap? = runCatching {
                    BitmapUtils.imageProxyToUprightBitmap(imageProxy)
                }.getOrNull()
                imageProxy.close()

                if (bitmap == null) {
                    isProcessingFrame.set(false)
                    return@setAnalyzer
                }

                processCapturedBitmap(bitmap, runDetection = true)
            }

            runCatching {
                provider.unbindAll()
                val cam = if (surfaceProvider != null) {
                    val preview = Preview.Builder()
                        .setResolutionSelector(resolutionSelector)
                        .build()
                        .apply { setSurfaceProvider(surfaceProvider) }
                    provider.bindToLifecycle(this, selector, preview, imageAnalysis)
                } else {
                    provider.bindToLifecycle(this, selector, imageAnalysis)
                }
                if (targetZoomRatio != null) {
                    val minSupported = cam.cameraInfo.zoomState.value?.minZoomRatio ?: 1.0f
                    cam.cameraControl.setZoomRatio(maxOf(targetZoomRatio, minSupported))
                }
                boundCamera = cam
                boundCameraKey = targetKey
                boundPreviewAttached = wantPreview
                MonitorStateHolder.update { it.copy(lastError = null) }
            }.onFailure { e ->
                val isRu = AppLocaleHelper.isRussian(currentSettings.appLanguage)
                val prefix = if (isRu) "Не удалось запустить камеру" else "Failed to start camera"
                MonitorStateHolder.update {
                    it.copy(lastError = "$prefix: ${e.message}")
                }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun startDirectCamera2(
        option: DeviceCameraOption,
        targetKey: String,
        wantPreview: Boolean
    ) {
        val cam2 = DirectCamera2Capture(
            context = applicationContext,
            cameraId = option.cameraId,
            physicalCameraId = option.physicalCameraId,
            getIntervalSeconds = { currentSettings.checkIntervalSeconds },
            onBitmapCaptured = { bitmap, dueForDetection ->
                if (!isProcessingFrame.compareAndSet(false, true)) {
                    if (!bitmap.isRecycled) bitmap.recycle()
                    return@DirectCamera2Capture
                }
                if (dueForDetection) {
                    nextCheckElapsedMs = SystemClock.elapsedRealtime() + currentSettings.checkIntervalSeconds * 1000L
                }
                processCapturedBitmap(bitmap, runDetection = dueForDetection)
            },
            onError = { errMsg ->
                MonitorStateHolder.update { it.copy(lastError = errMsg) }
                MonitorStateHolder.appendLog("${WorkdayClock.formatTime(System.currentTimeMillis())} • $errMsg")
            }
        )
        directCamera2 = cam2
        boundCamera = null
        boundCameraKey = targetKey
        boundPreviewAttached = wantPreview
        MonitorStateHolder.update { it.copy(isDirectCamera2Active = true, lastError = null) }
        cam2.start()
    }

    private fun processCapturedBitmap(bitmap: Bitmap, runDetection: Boolean) {
        lifecycleScope.launch {
            try {
                if (!runDetection) {
                    val previewCopy = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                    val oldBitmap = MonitorStateHolder.state.value.lastSnapshotBitmap
                    MonitorStateHolder.update { it.copy(lastSnapshotBitmap = previewCopy) }
                    if (oldBitmap != null && !oldBitmap.isRecycled && oldBitmap !== previewCopy) {
                        oldBitmap.recycle()
                    }
                    return@launch
                }

                val settings = currentSettings
                val result = detectorManager.detect(bitmap, settings)
                val actions = sessionEngine.onDetectionCheck(
                    personDetected = result.personDetected,
                    engineLabel = result.engineLabel,
                    settings = settings,
                    nowMs = System.currentTimeMillis()
                )
                handleEngineActions(actions)

                val previewCopy = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                val oldBitmap = MonitorStateHolder.state.value.lastSnapshotBitmap

                MonitorStateHolder.update { st ->
                    val snap = sessionEngine.snapshot(settings)
                    st.copy(
                        phase = snap.phase,
                        remainingSeconds = snap.remainingSeconds,
                        totalPhaseSeconds = snap.totalPhaseSeconds,
                        consecutiveMisses = snap.consecutiveMisses,
                        maxMissedChecks = snap.maxMissedChecks,
                        lastPersonDetected = result.personDetected,
                        lastConfidence = result.confidence,
                        lastInferenceMs = result.inferenceMs,
                        activeEngineLabel = result.engineLabel,
                        lastBoxes = result.boxes,
                        lastSnapshotBitmap = previewCopy,
                        lastError = result.errorMessage
                    )
                }
                if (oldBitmap != null && !oldBitmap.isRecycled && oldBitmap !== previewCopy) {
                    oldBitmap.recycle()
                }
                updateSilentNotificationIfPhaseChanged(sessionEngine.phase)

                val timeStr = WorkdayClock.formatTime(System.currentTimeMillis())
                val isRu = AppLocaleHelper.isRussian(settings.appLanguage)
                val statusTag = if (result.personDetected) {
                    if (isRu) {
                        "Человек (${(result.confidence * 100).toInt()}%, ${result.inferenceMs}мс)"
                    } else {
                        "Person (${(result.confidence * 100).toInt()}%, ${result.inferenceMs}ms)"
                    }
                } else {
                    if (isRu) "Пусто (${result.inferenceMs}мс)" else "Empty (${result.inferenceMs}ms)"
                }
                MonitorStateHolder.appendLog("$timeStr • $statusTag • ${sessionEngine.phase.label(isRu)}")
            } finally {
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
                isProcessingFrame.set(false)
            }
        }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = lifecycleScope.launch {
            while (isActive) {
                delay(1000L)
                val settings = currentSettings
                val nowMs = System.currentTimeMillis()
                val actions = sessionEngine.onOneSecondTick(settings, nowMs)
                handleEngineActions(actions)

                val secLeft = ((nextCheckElapsedMs - SystemClock.elapsedRealtime()) / 1000L)
                    .toInt()
                    .coerceIn(0, settings.checkIntervalSeconds)

                val snap = sessionEngine.snapshot(settings)
                MonitorStateHolder.update {
                    it.copy(
                        phase = snap.phase,
                        remainingSeconds = snap.remainingSeconds,
                        totalPhaseSeconds = snap.totalPhaseSeconds,
                        secondsUntilNextCheck = secLeft,
                        consecutiveMisses = snap.consecutiveMisses,
                        maxMissedChecks = snap.maxMissedChecks
                    )
                }
                updateSilentNotificationIfPhaseChanged(snap.phase)

                val elapsed = SystemClock.elapsedRealtime()
                if (settings.autoMorningExportEnabled && elapsed - lastAutoExportCheckElapsedMs >= 60_000L) {
                    lastAutoExportCheckElapsedMs = elapsed
                    lifecycleScope.launch(Dispatchers.IO) {
                        val outcome = MorningAutoExportManager.evaluateAndExportIfDue(
                            context = applicationContext,
                            forceNow = false,
                            nowMs = nowMs
                        )
                        if (outcome is AutoExportOutcome.Exported) {
                            val isRu = AppLocaleHelper.isRussian(settings.appLanguage)
                            MonitorStateHolder.update { it.copy(lastLanStatus = outcome.message) }
                            val logText = if (isRu) {
                                "Автоотчёт за ${outcome.workdayDate} выгружен на ПК"
                            } else {
                                "Morning report for ${outcome.workdayDate} exported to PC"
                            }
                            MonitorStateHolder.appendLog("${WorkdayClock.formatTime(System.currentTimeMillis())} • $logText")
                        }
                    }
                }
            }
        }
    }

    private suspend fun blinkWithActiveCamera(settings: PomidorSettings): Result<String> {
        val isRear = settings.cameraLens != CameraLensOption.FRONT
        val rearCamX = if (isRear) boundCamera else null
        val rearCam2 = if (isRear) directCamera2 else null
        val isRu = AppLocaleHelper.isRussian(settings.appLanguage)
        return torchController.blink(
            times = settings.torchBlinkCount,
            brightnessPercent = settings.torchBrightnessPercent,
            activeRearCamera = rearCamX,
            activeDirectCamera2 = rearCam2,
            pauseCameraForHardwareStrength = { block ->
                withContext(Dispatchers.Main) {
                    releaseAllCameras()
                    delay(180L)
                    try {
                        block()
                    } finally {
                        if (MonitorStateHolder.state.value.isServiceRunning) {
                            bindCameraUseCases(forceRebind = true)
                        }
                    }
                }
            },
            isRu = isRu
        )
    }

    private suspend fun handleEngineActions(actions: List<EngineAction>) {
        val isRu = AppLocaleHelper.isRussian(currentSettings.appLanguage)
        for (action in actions) {
            when (action) {
                is EngineAction.PersistSession -> {
                    withContext(Dispatchers.IO) {
                        sessionRepository.insertSession(action.session)
                    }
                    val timeStr = WorkdayClock.formatTime(action.session.endTimeMs)
                    val prefix = if (isRu) "Сессия записана" else "Session saved"
                    MonitorStateHolder.appendLog(
                        "$timeStr • $prefix (${action.session.status}, ${WorkdayClock.formatMmSs(action.session.actualFocusSeconds)})"
                    )
                }
                is EngineAction.TriggerPomodoroComplete -> {
                    val settings = currentSettings
                    val doneMsg = if (isRu) {
                        "Помидор завершён! Отдых ${action.breakMinutes} мин"
                    } else {
                        "Pomodoro completed! Break ${action.breakMinutes} min"
                    }
                    MonitorStateHolder.appendLog("${WorkdayClock.formatTime(System.currentTimeMillis())} • $doneMsg")
                    if (settings.torchBlinkEnabled) {
                        lifecycleScope.launch {
                            val res = blinkWithActiveCamera(settings)
                            val torchTag = if (isRu) "Фонарик" else "Torch"
                            res.onSuccess { modeDesc ->
                                MonitorStateHolder.appendLog("$torchTag: $modeDesc")
                            }.onFailure { err ->
                                MonitorStateHolder.appendLog("$torchTag: ${err.message}")
                            }
                        }
                    }
                    lifecycleScope.launch {
                        val lanRes = lanNotifier.sendBreakSignal(
                            settings = settings,
                            focusMinutes = action.focusMinutes,
                            isTest = false
                        )
                        MonitorStateHolder.update { it.copy(lastLanStatus = lanRes.message) }
                        MonitorStateHolder.appendLog("LAN: ${lanRes.message}")
                    }
                }
                is EngineAction.BreakFinished -> {
                    val finishedMsg = if (isRu) {
                        "Перерыв окончен, ожидание человека"
                    } else {
                        "Break finished, waiting for person"
                    }
                    MonitorStateHolder.appendLog("${WorkdayClock.formatTime(System.currentTimeMillis())} • $finishedMsg")
                }
            }
        }
    }

    private fun stopMonitoringAndSelf() {
        lifecycleScope.launch {
            val isRu = AppLocaleHelper.isRussian(currentSettings.appLanguage)
            val actions = sessionEngine.onManualStop(currentSettings, System.currentTimeMillis())
            handleEngineActions(actions)
            MonitorStateHolder.update {
                it.copy(
                    isServiceRunning = false,
                    isDirectCamera2Active = false,
                    phase = SessionPhase.IDLE,
                    remainingSeconds = currentSettings.focusDurationMinutes * 60,
                    totalPhaseSeconds = currentSettings.focusDurationMinutes * 60,
                    consecutiveMisses = 0
                )
            }
            val stoppedMsg = if (isRu) "Мониторинг остановлен" else "Monitoring stopped"
            MonitorStateHolder.appendLog("${WorkdayClock.formatTime(System.currentTimeMillis())} • $stoppedMsg")
            stopSelf()
        }
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PomidorCam::MonitorWakeLock").apply {
            setReferenceCounted(false)
            acquire(12 * 60 * 60 * 1000L)
        }
    }

    override fun onDestroy() {
        MonitorStateHolder.onPreviewSurfaceChanged = null
        tickerJob?.cancel()
        releaseAllCameras()
        analysisExecutor?.shutdown()
        detectorManager.close()
        runCatching {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        }
        MonitorStateHolder.update { it.copy(isServiceRunning = false, isDirectCamera2Active = false) }
        super.onDestroy()
    }
}
