package com.example.hardware

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import androidx.camera.core.Camera
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.roundToInt

data class TorchCapabilityInfo(
    val hasFlash: Boolean,
    val flashCameraId: String?,
    val supportsHardwareStrength: Boolean,
    val maxStrengthLevel: Int,
    val defaultStrengthLevel: Int
)

class TorchController(private val context: Context) {

    private val cameraManager: CameraManager?
        get() = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager

    fun getCapabilityInfo(): TorchCapabilityInfo {
        val mgr = cameraManager ?: return TorchCapabilityInfo(
            hasFlash = false,
            flashCameraId = null,
            supportsHardwareStrength = false,
            maxStrengthLevel = 1,
            defaultStrengthLevel = 1
        )

        val candidates = runCatching {
            mgr.cameraIdList.mapNotNull { id ->
                val chars = mgr.getCameraCharacteristics(id)
                val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                if (!hasFlash) return@mapNotNull null
                val facing = chars.get(CameraCharacteristics.LENS_FACING) ?: CameraCharacteristics.LENS_FACING_BACK
                val maxLevel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    chars.get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 1
                } else 1
                val defLevel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    chars.get(CameraCharacteristics.FLASH_INFO_STRENGTH_DEFAULT_LEVEL) ?: 1
                } else 1
                Triple(id, facing == CameraCharacteristics.LENS_FACING_BACK, maxLevel to defLevel)
            }
        }.getOrElse { emptyList() }

        val best = candidates.sortedWith(
            compareByDescending<Triple<String, Boolean, Pair<Int, Int>>> { it.second }
                .thenByDescending { it.third.first }
        ).firstOrNull()

        return if (best == null) {
            TorchCapabilityInfo(
                hasFlash = false,
                flashCameraId = null,
                supportsHardwareStrength = false,
                maxStrengthLevel = 1,
                defaultStrengthLevel = 1
            )
        } else {
            val maxLevel = best.third.first.coerceAtLeast(1)
            val defLevel = best.third.second.coerceIn(1, maxLevel)
            TorchCapabilityInfo(
                hasFlash = true,
                flashCameraId = best.first,
                supportsHardwareStrength = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && maxLevel > 1,
                maxStrengthLevel = maxLevel,
                defaultStrengthLevel = defLevel
            )
        }
    }

    fun supportsNativeTorchStrength(): Boolean = getCapabilityInfo().supportsHardwareStrength

    fun computeHardwareLevel(brightnessPercent: Int, maxLevel: Int): Int {
        if (maxLevel <= 1) return 1
        val pct = brightnessPercent.coerceIn(10, 100)
        val normalized = (pct - 10) / 90.0
        return (1.0 + normalized * (maxLevel - 1)).roundToInt().coerceIn(1, maxLevel)
    }

    fun computePulseDurationMs(brightnessPercent: Int): Long {
        val pct = brightnessPercent.coerceIn(10, 100)
        return (55L + ((pct - 10) * 445L) / 90L).coerceIn(55L, 500L)
    }

    suspend fun blink(
        times: Int = 3,
        brightnessPercent: Int = 80,
        activeRearCamera: Camera? = null,
        activeDirectCamera2: DirectCamera2Capture? = null,
        pauseCameraForHardwareStrength: (suspend (suspend () -> Unit) -> Unit)? = null,
        isRu: Boolean = false
    ): Result<String> = withContext(Dispatchers.Main) {
        val count = times.coerceIn(1, 5)
        val brightness = brightnessPercent.coerceIn(10, 100)
        val cap = getCapabilityInfo()
        val pulseMs = computePulseDurationMs(brightness)
        val offDurationMs = 200L

        // Path 1: Hardware brightness level on Android 13+ when supported
        if (cap.supportsHardwareStrength && cap.flashCameraId != null) {
            val mgr = cameraManager ?: return@withContext Result.failure(
                IllegalStateException(if (isRu) "CameraManager недоступен" else "CameraManager unavailable")
            )
            val flashId = cap.flashCameraId
            val level = computeHardwareLevel(brightness, cap.maxStrengthLevel)
            val needsCameraRelease = activeRearCamera != null || activeDirectCamera2 != null

            val hwResult = runCatching {
                val runNativeBlink: suspend () -> Unit = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        repeat(count) { idx ->
                            mgr.turnOnTorchWithStrengthLevel(flashId, level)
                            delay(260L)
                            mgr.setTorchMode(flashId, false)
                            if (idx < count - 1) delay(offDurationMs)
                        }
                    }
                }
                if (needsCameraRelease && pauseCameraForHardwareStrength != null) {
                    pauseCameraForHardwareStrength(runNativeBlink)
                } else if (!needsCameraRelease) {
                    runNativeBlink()
                } else {
                    error(if (isRu) "Задняя камера занята" else "Rear camera is busy")
                }
                if (isRu) {
                    "Аппаратный уровень $level/${cap.maxStrengthLevel} ($brightness%)"
                } else {
                    "Hardware level $level/${cap.maxStrengthLevel} ($brightness%)"
                }
            }
            if (hwResult.isSuccess) return@withContext hwResult
        }

        // Path 2: Active Direct Camera2 session holding the rear camera
        if (activeDirectCamera2 != null && activeDirectCamera2.hasFlashUnit()) {
            return@withContext runCatching {
                repeat(count) { idx ->
                    activeDirectCamera2.setTorch(true)
                    delay(pulseMs)
                    activeDirectCamera2.setTorch(false)
                    if (idx < count - 1) delay(offDurationMs)
                }
                if (isRu) {
                    "Импульс $pulseMs мс ($brightness%, Camera2)"
                } else {
                    "Pulse ${pulseMs}ms ($brightness%, Camera2)"
                }
            }
        }

        // Path 3: Active CameraX rear camera session
        if (activeRearCamera != null && activeRearCamera.cameraInfo.hasFlashUnit()) {
            return@withContext runCatching {
                repeat(count) { idx ->
                    awaitFuture(activeRearCamera.cameraControl.enableTorch(true), 600L)
                    delay(pulseMs)
                    awaitFuture(activeRearCamera.cameraControl.enableTorch(false), 600L)
                    if (idx < count - 1) delay(offDurationMs)
                }
                if (isRu) {
                    "Импульс $pulseMs мс ($brightness%, Android ${Build.VERSION.RELEASE})"
                } else {
                    "Pulse ${pulseMs}ms ($brightness%, Android ${Build.VERSION.RELEASE})"
                }
            }
        }

        // Path 4: CameraManager torch mode (camera idle or front camera active)
        val mgr = cameraManager ?: return@withContext Result.failure(
            IllegalStateException(if (isRu) "CameraManager недоступен" else "CameraManager unavailable")
        )
        val flashId = cap.flashCameraId ?: return@withContext Result.failure(
            IllegalStateException(if (isRu) "Вспышка не найдена на устройстве" else "Flash unit not found on device")
        )

        return@withContext runCatching {
            repeat(count) { idx ->
                mgr.setTorchMode(flashId, true)
                delay(pulseMs)
                mgr.setTorchMode(flashId, false)
                if (idx < count - 1) delay(offDurationMs)
            }
            if (cap.supportsHardwareStrength) {
                if (isRu) "Импульс $pulseMs мс ($brightness%)" else "Pulse ${pulseMs}ms ($brightness%)"
            } else {
                if (isRu) {
                    "Импульс $pulseMs мс ($brightness% — на Android ${Build.VERSION.RELEASE} без LED-диммера)"
                } else {
                    "Pulse ${pulseMs}ms ($brightness% — no LED dimmer on Android ${Build.VERSION.RELEASE})"
                }
            }
        }
    }

    private suspend fun <T> awaitFuture(future: ListenableFuture<T>, timeoutMs: Long): T? {
        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                future.addListener({
                    if (!cont.isCompleted) {
                        val value = runCatching { future.get() }.getOrNull()
                        cont.resume(value)
                    }
                }, ContextCompat.getMainExecutor(context))
            }
        }
    }
}
