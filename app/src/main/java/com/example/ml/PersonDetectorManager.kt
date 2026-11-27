package com.example.ml

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.DetectorMode
import com.example.data.PomidorSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class ImportedModelInfo(
    val path: String,
    val displayName: String,
    val detectedMode: DetectorMode
)

class PersonDetectorManager(private val context: Context) {

    private val onnxDetector = Yolo26OnnxDetector()
    private val tfliteDetector = Yolo26TfliteDetector()
    private val mlKitDetector = MlKitPersonDetector()

    suspend fun detect(bitmap: Bitmap, settings: PomidorSettings): DetectionResult {
        val modelPath = settings.customModelPath
        val hasModelFile = modelPath.isNotBlank() && File(modelPath).exists()
        val ext = modelPath.substringAfterLast('.', "").lowercase()

        return when (settings.detectorMode) {
            DetectorMode.YOLO26_ONNX -> {
                if (hasModelFile && ext == "onnx") {
                    withContext(Dispatchers.Default) {
                        onnxDetector.detect(bitmap, modelPath, settings.confidenceThreshold)
                    }
                } else {
                    mlKitDetector.detect(bitmap, settings.confidenceThreshold).copy(
                        errorMessage = "ONNX файл не выбран — активен резервный детектор"
                    )
                }
            }
            DetectorMode.YOLO26_TFLITE -> {
                if (hasModelFile && (ext == "tflite" || ext == "litert")) {
                    withContext(Dispatchers.Default) {
                        tfliteDetector.detect(bitmap, modelPath, settings.confidenceThreshold)
                    }
                } else {
                    mlKitDetector.detect(bitmap, settings.confidenceThreshold).copy(
                        errorMessage = "TFLite файл не выбран — активен резервный детектор"
                    )
                }
            }
            DetectorMode.AUTO -> {
                when {
                    hasModelFile && ext == "onnx" -> {
                        val res = withContext(Dispatchers.Default) {
                            onnxDetector.detect(bitmap, modelPath, settings.confidenceThreshold)
                        }
                        if (res.errorMessage == null) res else mlKitDetector.detect(bitmap, settings.confidenceThreshold)
                    }
                    hasModelFile && (ext == "tflite" || ext == "litert") -> {
                        val res = withContext(Dispatchers.Default) {
                            tfliteDetector.detect(bitmap, modelPath, settings.confidenceThreshold)
                        }
                        if (res.errorMessage == null) res else mlKitDetector.detect(bitmap, settings.confidenceThreshold)
                    }
                    else -> mlKitDetector.detect(bitmap, settings.confidenceThreshold)
                }
            }
            DetectorMode.BUILTIN_MLKIT -> mlKitDetector.detect(bitmap, settings.confidenceThreshold)
        }
    }

    suspend fun importModelFromUri(uri: Uri): Result<ImportedModelInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val displayName = resolveDisplayName(uri) ?: "yolo26_model.onnx"
            val ext = displayName.substringAfterLast('.', "").lowercase()
            require(ext == "onnx" || ext == "tflite" || ext == "litert") {
                "Поддерживаются только файлы .onnx, .tflite или .litert"
            }

            val modelsDir = File(context.filesDir, "models").apply { mkdirs() }
            // Clean previous imported models to save flash space on 8GB device
            modelsDir.listFiles()?.forEach { it.delete() }

            val targetFile = File(modelsDir, displayName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output, bufferSize = 32 * 1024)
                }
            } ?: error("Не удалось открыть выбранный файл")

            val mode = if (ext == "onnx") {
                onnxDetector.ensureLoaded(targetFile.absolutePath).getOrThrow()
                DetectorMode.YOLO26_ONNX
            } else {
                tfliteDetector.ensureLoaded(targetFile.absolutePath).getOrThrow()
                DetectorMode.YOLO26_TFLITE
            }

            ImportedModelInfo(
                path = targetFile.absolutePath,
                displayName = displayName,
                detectedMode = mode
            )
        }
    }

    fun clearCustomModel() {
        onnxDetector.close()
        tfliteDetector.close()
        File(context.filesDir, "models").listFiles()?.forEach { it.delete() }
    }

    private fun resolveDisplayName(uri: Uri): String? {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIdx >= 0 && cursor.moveToFirst()) {
                return cursor.getString(nameIdx)
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }

    fun close() {
        onnxDetector.close()
        tfliteDetector.close()
        mlKitDetector.close()
    }
}
