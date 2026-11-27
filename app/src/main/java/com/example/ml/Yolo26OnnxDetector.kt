package com.example.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.graphics.Bitmap
import android.os.SystemClock
import java.io.File

class Yolo26OnnxDetector {

    private var env: OrtEnvironment? = null
    private var session: OrtSession? = null
    private var loadedPath: String = ""
    private var inputWidth: Int = 640
    private var inputHeight: Int = 640
    private var inputName: String = "images"

    @Synchronized
    fun ensureLoaded(modelPath: String): Result<Unit> {
        if (modelPath.isBlank()) {
            return Result.failure(IllegalArgumentException("Путь к ONNX-модели не задан"))
        }
        val file = File(modelPath)
        if (!file.exists()) {
            return Result.failure(IllegalStateException("Файл модели не найден"))
        }
        if (session != null && loadedPath == modelPath) {
            return Result.success(Unit)
        }
        close()
        return runCatching {
            val ortEnv = OrtEnvironment.getEnvironment()
            val opts = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(2)
                setInterOpNumThreads(1)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                runCatching { addNnapi() }
            }
            val createdSession = try {
                ortEnv.createSession(file.absolutePath, opts)
            } catch (_: Throwable) {
                // Fallback without NNAPI on devices where NNAPI EP fails for specific ops
                opts.close()
                val cpuOpts = OrtSession.SessionOptions().apply {
                    setIntraOpNumThreads(2)
                    setInterOpNumThreads(1)
                    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
                }
                ortEnv.createSession(file.absolutePath, cpuOpts)
            }
            val firstInput = createdSession.inputInfo.entries.firstOrNull()
            if (firstInput != null) {
                inputName = firstInput.key
                val tensorInfo = firstInput.value.info as? TensorInfo
                val shape = tensorInfo?.shape
                if (shape != null && shape.size >= 4) {
                    val h = shape[2].toInt()
                    val w = shape[3].toInt()
                    if (h > 0 && w > 0) {
                        inputHeight = h
                        inputWidth = w
                    }
                }
            }
            env = ortEnv
            session = createdSession
            loadedPath = modelPath
        }
    }

    @Synchronized
    fun detect(bitmap: Bitmap, modelPath: String, threshold: Float): DetectionResult {
        val start = SystemClock.elapsedRealtime()
        val loadResult = ensureLoaded(modelPath)
        if (loadResult.isFailure) {
            return DetectionResult(
                personDetected = false,
                confidence = 0f,
                boxes = emptyList(),
                inferenceMs = SystemClock.elapsedRealtime() - start,
                engineLabel = "YOLO26 ONNX",
                errorMessage = loadResult.exceptionOrNull()?.message
            )
        }
        val ortEnv = env ?: return DetectionResult(false, 0f, emptyList(), 0L, "YOLO26 ONNX", "ORT не готов")
        val ortSession = session ?: return DetectionResult(false, 0f, emptyList(), 0L, "YOLO26 ONNX", "Сессия не готова")

        val letterbox = BitmapUtils.letterbox(bitmap, inputWidth, inputHeight)
        val lbBitmap = letterbox.bitmap ?: return DetectionResult(
            false, 0f, emptyList(), 0L, "YOLO26 ONNX", "Ошибка letterbox"
        )
        return try {
            val floatBuffer = BitmapUtils.bitmapToNchwFloatBuffer(lbBitmap)
            val shape = longArrayOf(1L, 3L, inputHeight.toLong(), inputWidth.toLong())
            OnnxTensor.createTensor(ortEnv, floatBuffer, shape).use { inputTensor ->
                ortSession.run(mapOf(inputName to inputTensor)).use { outputs ->
                    val rawValue = outputs[0].value
                    val boxes = parseOutput(rawValue, threshold, letterbox)
                    val topConf = boxes.maxOfOrNull { it.confidence } ?: 0f
                    DetectionResult(
                        personDetected = boxes.isNotEmpty(),
                        confidence = topConf,
                        boxes = boxes,
                        inferenceMs = SystemClock.elapsedRealtime() - start,
                        engineLabel = "YOLO26 ONNX (${inputWidth}x${inputHeight})"
                    )
                }
            }
        } catch (t: Throwable) {
            DetectionResult(
                personDetected = false,
                confidence = 0f,
                boxes = emptyList(),
                inferenceMs = SystemClock.elapsedRealtime() - start,
                engineLabel = "YOLO26 ONNX",
                errorMessage = t.message ?: "Ошибка ONNX инференса"
            )
        } finally {
            lbBitmap.recycle()
        }
    }

    private fun parseOutput(
        rawValue: Any?,
        threshold: Float,
        letterbox: LetterboxInput
    ): List<DetectionBox> {
        val matrix: Array<FloatArray>? = when (rawValue) {
            is Array<*> -> {
                val first = rawValue.firstOrNull()
                when (first) {
                    is Array<*> -> @Suppress("UNCHECKED_CAST") (first as? Array<FloatArray>)
                    is FloatArray -> @Suppress("UNCHECKED_CAST") (rawValue as? Array<FloatArray>)
                    else -> null
                }
            }
            else -> null
        }
        if (matrix == null || matrix.isEmpty()) return emptyList()

        val rows = matrix.size
        val cols = matrix[0].size
        return when {
            cols == 6 -> Yolo26Decoder.decodeNmsFreeRows(matrix, threshold, letterbox)
            rows < cols && rows >= 5 -> Yolo26Decoder.decodeClassicHead(matrix, channelsFirst = true, threshold, letterbox)
            else -> Yolo26Decoder.decodeClassicHead(matrix, channelsFirst = false, threshold, letterbox)
        }
    }

    @Synchronized
    fun close() {
        runCatching { session?.close() }
        session = null
        loadedPath = ""
    }
}
