package com.example.ml

import android.graphics.Bitmap
import android.os.SystemClock
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class Yolo26TfliteDetector {

    private var interpreter: Interpreter? = null
    private var loadedPath: String = ""
    private var inputWidth: Int = 640
    private var inputHeight: Int = 640
    private var inputIsUint8: Boolean = false
    private var outputShape: IntArray = intArrayOf(1, 300, 6)

    @Synchronized
    fun ensureLoaded(modelPath: String): Result<Unit> {
        if (modelPath.isBlank()) {
            return Result.failure(IllegalArgumentException("Путь к TFLite-модели не задан"))
        }
        val file = File(modelPath)
        if (!file.exists()) {
            return Result.failure(IllegalStateException("Файл TFLite-модели не найден"))
        }
        if (interpreter != null && loadedPath == modelPath) {
            return Result.success(Unit)
        }
        close()
        return runCatching {
            val mapped = mapModelFile(file)
            val options = Interpreter.Options().apply {
                setNumThreads(2)
                setUseXNNPACK(true)
            }
            val tflite = Interpreter(mapped, options)
            val inTensor = tflite.getInputTensor(0)
            val inShape = inTensor.shape()
            if (inShape.size >= 4) {
                if (inShape[1] > 0 && inShape[2] > 0) {
                    inputHeight = inShape[1]
                    inputWidth = inShape[2]
                }
            }
            inputIsUint8 = inTensor.dataType() == DataType.UINT8
            outputShape = tflite.getOutputTensor(0).shape()

            interpreter = tflite
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
                engineLabel = "YOLO26 TFLite",
                errorMessage = loadResult.exceptionOrNull()?.message
            )
        }
        val tflite = interpreter ?: return DetectionResult(
            false, 0f, emptyList(), 0L, "YOLO26 TFLite", "Интерпретатор не готов"
        )

        val letterbox = BitmapUtils.letterbox(bitmap, inputWidth, inputHeight)
        val lbBitmap = letterbox.bitmap ?: return DetectionResult(
            false, 0f, emptyList(), 0L, "YOLO26 TFLite", "Ошибка letterbox"
        )
        return try {
            val inputBuffer = BitmapUtils.bitmapToNhwcByteBuffer(lbBitmap, inputIsUint8)
            val dim1 = outputShape.getOrElse(1) { 300 }
            val dim2 = outputShape.getOrElse(2) { 6 }

            val boxes = if (outputShape.size == 3) {
                val outBuffer = Array(1) { Array(dim1) { FloatArray(dim2) } }
                tflite.run(inputBuffer, outBuffer)
                val matrix = outBuffer[0]
                when {
                    dim2 == 6 -> Yolo26Decoder.decodeNmsFreeRows(matrix, threshold, letterbox)
                    dim1 < dim2 && dim1 >= 5 -> Yolo26Decoder.decodeClassicHead(
                        matrix, channelsFirst = true, threshold, letterbox
                    )
                    else -> Yolo26Decoder.decodeClassicHead(
                        matrix, channelsFirst = false, threshold, letterbox
                    )
                }
            } else if (outputShape.size == 2) {
                val d0 = outputShape[0]
                val d1 = outputShape[1]
                val outBuffer = Array(d0) { FloatArray(d1) }
                tflite.run(inputBuffer, outBuffer)
                Yolo26Decoder.decodeNmsFreeRows(outBuffer, threshold, letterbox)
            } else {
                emptyList()
            }

            val topConf = boxes.maxOfOrNull { it.confidence } ?: 0f
            DetectionResult(
                personDetected = boxes.isNotEmpty(),
                confidence = topConf,
                boxes = boxes,
                inferenceMs = SystemClock.elapsedRealtime() - start,
                engineLabel = "YOLO26 TFLite (${inputWidth}x${inputHeight})"
            )
        } catch (t: Throwable) {
            DetectionResult(
                personDetected = false,
                confidence = 0f,
                boxes = emptyList(),
                inferenceMs = SystemClock.elapsedRealtime() - start,
                engineLabel = "YOLO26 TFLite",
                errorMessage = t.message ?: "Ошибка TFLite инференса"
            )
        } finally {
            lbBitmap.recycle()
        }
    }

    private fun mapModelFile(file: File): MappedByteBuffer {
        FileInputStream(file).use { fis ->
            val channel = fis.channel
            return channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size())
        }
    }

    @Synchronized
    fun close() {
        runCatching { interpreter?.close() }
        interpreter = null
        loadedPath = ""
    }
}
