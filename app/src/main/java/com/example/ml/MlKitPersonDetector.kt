package com.example.ml

import android.graphics.Bitmap
import android.os.SystemClock
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.min

class MlKitPersonDetector {

    private val detector: PoseDetector by lazy {
        val options = PoseDetectorOptions.Builder()
            .setDetectorMode(PoseDetectorOptions.SINGLE_IMAGE_MODE)
            .build()
        PoseDetection.getClient(options)
    }

    suspend fun detect(bitmap: Bitmap, threshold: Float): DetectionResult {
        val start = SystemClock.elapsedRealtime()
        val image = InputImage.fromBitmap(bitmap, 0)
        return suspendCancellableCoroutine { cont ->
            detector.process(image)
                .addOnSuccessListener { pose ->
                    val elapsed = SystemClock.elapsedRealtime() - start
                    val confidentLandmarks = pose.allPoseLandmarks.filter {
                        it.inFrameLikelihood >= threshold
                    }
                    // Seated user at a desk typically has at least 4 confident upper-body/head landmarks
                    if (confidentLandmarks.size >= 4) {
                        val avgConf = confidentLandmarks.map { it.inFrameLikelihood }.average().toFloat()
                        val w = bitmap.width.toFloat().coerceAtLeast(1f)
                        val h = bitmap.height.toFloat().coerceAtLeast(1f)
                        var minX = 1f
                        var minY = 1f
                        var maxX = 0f
                        var maxY = 0f
                        for (lm in confidentLandmarks) {
                            val nx = (lm.position.x / w).coerceIn(0f, 1f)
                            val ny = (lm.position.y / h).coerceIn(0f, 1f)
                            minX = min(minX, nx)
                            minY = min(minY, ny)
                            maxX = max(maxX, nx)
                            maxY = max(maxY, ny)
                        }
                        val pad = 0.04f
                        val box = DetectionBox(
                            left = (minX - pad).coerceIn(0f, 1f),
                            top = (minY - pad).coerceIn(0f, 1f),
                            right = (maxX + pad).coerceIn(0f, 1f),
                            bottom = (maxY + pad).coerceIn(0f, 1f),
                            confidence = avgConf,
                            label = "person"
                        )
                        cont.resume(
                            DetectionResult(
                                personDetected = true,
                                confidence = avgConf,
                                boxes = listOf(box),
                                inferenceMs = elapsed,
                                engineLabel = "Встроенный детектор (ML Kit)"
                            )
                        )
                    } else {
                        cont.resume(
                            DetectionResult(
                                personDetected = false,
                                confidence = 0f,
                                boxes = emptyList(),
                                inferenceMs = elapsed,
                                engineLabel = "Встроенный детектор (ML Kit)"
                            )
                        )
                    }
                }
                .addOnFailureListener { e ->
                    val elapsed = SystemClock.elapsedRealtime() - start
                    cont.resume(
                        DetectionResult(
                            personDetected = false,
                            confidence = 0f,
                            boxes = emptyList(),
                            inferenceMs = elapsed,
                            engineLabel = "Встроенный детектор (ML Kit)",
                            errorMessage = e.message
                        )
                    )
                }
        }
    }

    fun close() {
        runCatching { detector.close() }
    }
}
