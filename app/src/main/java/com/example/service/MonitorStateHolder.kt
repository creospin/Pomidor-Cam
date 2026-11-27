package com.example.service

import android.graphics.Bitmap
import androidx.camera.core.Preview
import com.example.ml.DetectionBox
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class SessionPhase(val labelEn: String, val labelRu: String) {
    IDLE("Waiting for person", "Ожидание человека"),
    FOCUSING("Focus (silent)", "Фокус (без уведомлений)"),
    GRACE_PERIOD("Grace period", "Льготный пропуск"),
    BREAK("Break time", "Перерыв");

    fun label(isRu: Boolean): String = if (isRu) labelRu else labelEn
}

data class MonitorUiState(
    val isServiceRunning: Boolean = false,
    val phase: SessionPhase = SessionPhase.IDLE,
    val remainingSeconds: Int = 20 * 60,
    val totalPhaseSeconds: Int = 20 * 60,
    val secondsUntilNextCheck: Int = 10,
    val consecutiveMisses: Int = 0,
    val maxMissedChecks: Int = 2,
    val lastPersonDetected: Boolean? = null,
    val lastConfidence: Float = 0f,
    val lastInferenceMs: Long = 0L,
    val activeEngineLabel: String = "",
    val lastBoxes: List<DetectionBox> = emptyList(),
    val lastSnapshotBitmap: Bitmap? = null,
    val isDirectCamera2Active: Boolean = false,
    val lastLanStatus: String = "",
    val lastError: String? = null,
    val recentLogs: List<String> = emptyList()
)

object MonitorStateHolder {
    private val _state = MutableStateFlow(MonitorUiState())
    val state: StateFlow<MonitorUiState> = _state.asStateFlow()

    @Volatile
    var previewSurfaceProvider: Preview.SurfaceProvider? = null
        private set

    @Volatile
    var onPreviewSurfaceChanged: ((Preview.SurfaceProvider?) -> Unit)? = null

    @Volatile
    var onTorchTestFeedback: ((Result<String>) -> Unit)? = null

    fun setPreviewSurfaceProvider(provider: Preview.SurfaceProvider?) {
        previewSurfaceProvider = provider
        onPreviewSurfaceChanged?.invoke(provider)
    }

    fun update(transform: (MonitorUiState) -> MonitorUiState) {
        _state.update(transform)
    }

    fun appendLog(entry: String) {
        _state.update { current ->
            val updated = (listOf(entry) + current.recentLogs).take(30)
            current.copy(recentLogs = updated)
        }
    }
}
