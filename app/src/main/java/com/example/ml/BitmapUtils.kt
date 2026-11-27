package com.example.ml

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

data class LetterboxInput(
    val bitmap: Bitmap? = null,
    val srcWidth: Int,
    val srcHeight: Int,
    val inputWidth: Int,
    val inputHeight: Int,
    val scale: Float,
    val padX: Float,
    val padY: Float
)

object BitmapUtils {

    fun imageProxyToUprightBitmap(imageProxy: ImageProxy): Bitmap {
        val raw = imageProxy.toBitmap()
        val rotation = imageProxy.imageInfo.rotationDegrees
        if (rotation == 0) return raw
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        val rotated = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
        if (rotated !== raw) {
            raw.recycle()
        }
        return rotated
    }

    fun letterbox(source: Bitmap, targetWidth: Int, targetHeight: Int): LetterboxInput {
        val srcW = source.width
        val srcH = source.height
        val scale = minOf(targetWidth.toFloat() / srcW, targetHeight.toFloat() / srcH)
        val scaledW = (srcW * scale).toInt().coerceAtLeast(1)
        val scaledH = (srcH * scale).toInt().coerceAtLeast(1)
        val padX = (targetWidth - scaledW) / 2f
        val padY = (targetHeight - scaledH) / 2f

        val output = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.rgb(114, 114, 114))
        val scaled = Bitmap.createScaledBitmap(source, scaledW, scaledH, true)
        canvas.drawBitmap(scaled, padX, padY, Paint(Paint.FILTER_BITMAP_FLAG))
        if (scaled !== source) {
            scaled.recycle()
        }
        return LetterboxInput(
            bitmap = output,
            srcWidth = srcW,
            srcHeight = srcH,
            inputWidth = targetWidth,
            inputHeight = targetHeight,
            scale = scale,
            padX = padX,
            padY = padY
        )
    }

    /**
     * Converts an RGB Bitmap into NCHW FloatBuffer [1, 3, H, W] normalized to [0f, 1f] for ONNX Runtime.
     */
    fun bitmapToNchwFloatBuffer(bitmap: Bitmap): FloatBuffer {
        val width = bitmap.width
        val height = bitmap.height
        val totalPixels = width * height
        val pixels = IntArray(totalPixels)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val buffer = ByteBuffer.allocateDirect(4 * 3 * totalPixels)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()

        val rOffset = 0
        val gOffset = totalPixels
        val bOffset = totalPixels * 2

        for (i in 0 until totalPixels) {
            val px = pixels[i]
            buffer.put(rOffset + i, ((px ushr 16) and 0xFF) / 255.0f)
            buffer.put(gOffset + i, ((px ushr 8) and 0xFF) / 255.0f)
            buffer.put(bOffset + i, (px and 0xFF) / 255.0f)
        }
        buffer.rewind()
        return buffer
    }

    /**
     * Converts an RGB Bitmap into NHWC ByteBuffer [1, H, W, 3] for TFLite (FLOAT32 or UINT8).
     */
    fun bitmapToNhwcByteBuffer(bitmap: Bitmap, isQuantizedUint8: Boolean): ByteBuffer {
        val width = bitmap.width
        val height = bitmap.height
        val totalPixels = width * height
        val pixels = IntArray(totalPixels)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val bytesPerChannel = if (isQuantizedUint8) 1 else 4
        val buffer = ByteBuffer.allocateDirect(totalPixels * 3 * bytesPerChannel)
            .order(ByteOrder.nativeOrder())

        for (i in 0 until totalPixels) {
            val px = pixels[i]
            val r = (px ushr 16) and 0xFF
            val g = (px ushr 8) and 0xFF
            val b = px and 0xFF
            if (isQuantizedUint8) {
                buffer.put(r.toByte())
                buffer.put(g.toByte())
                buffer.put(b.toByte())
            } else {
                buffer.putFloat(r / 255.0f)
                buffer.putFloat(g / 255.0f)
                buffer.putFloat(b / 255.0f)
            }
        }
        buffer.rewind()
        return buffer
    }
}
