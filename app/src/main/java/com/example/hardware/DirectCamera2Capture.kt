package com.example.hardware

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lightweight Camera2 capture session used when an ultrawide/auxiliary lens
 * is exposed only as a physical sub-camera or hidden vendor ID outside CameraX's catalog.
 */
class DirectCamera2Capture(
    private val context: Context,
    private val cameraId: String,
    private val physicalCameraId: String?,
    private val getIntervalSeconds: () -> Int,
    private val onBitmapCaptured: (Bitmap, Boolean) -> Unit,
    private val onError: (String) -> Unit
) {
    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null

    private val isBusyProcessing = AtomicBoolean(false)
    private val isClosed = AtomicBoolean(false)

    @Volatile
    private var nextDetectionElapsedMs: Long = 0L

    @Volatile
    private var lastPreviewElapsedMs: Long = 0L

    @Volatile
    private var lastCapturedDisplayRotation: Int = -1

    private var sensorOrientationDeg: Int = 90
    private var isFrontFacing: Boolean = false
    private var hasFlash: Boolean = false

    @SuppressLint("MissingPermission")
    fun start() {
        isClosed.set(false)
        val mgr = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        if (mgr == null) {
            onError("CameraManager недоступен")
            return
        }

        val charsId = physicalCameraId ?: cameraId
        runCatching {
            val chars = mgr.getCameraCharacteristics(charsId)
            sensorOrientationDeg = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
            isFrontFacing = chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
            hasFlash = mgr.getCameraCharacteristics(cameraId)
                .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }

        val thread = HandlerThread("PomidorCam2-$cameraId").apply { start() }
        handlerThread = thread
        val bgHandler = Handler(thread.looper)
        handler = bgHandler

        val reader = ImageReader.newInstance(640, 480, ImageFormat.YUV_420_888, 2)
        imageReader = reader
        nextDetectionElapsedMs = SystemClock.elapsedRealtime() + 800L
        lastPreviewElapsedMs = 0L

        reader.setOnImageAvailableListener({ r ->
            val image = runCatching { r.acquireLatestImage() }.getOrNull() ?: return@setOnImageAvailableListener
            if (isClosed.get()) {
                image.close()
                return@setOnImageAvailableListener
            }

            val now = SystemClock.elapsedRealtime()
            val displayRotation = CameraRotationHelper.getDisplayRotation(context)
            val rotationChanged = displayRotation != lastCapturedDisplayRotation
            val dueForDetection = now >= nextDetectionElapsedMs
            val dueForPreview = !dueForDetection && (rotationChanged || (now - lastPreviewElapsedMs >= 1500L))

            if ((!dueForDetection && !dueForPreview) || !isBusyProcessing.compareAndSet(false, true)) {
                image.close()
                return@setOnImageAvailableListener
            }

            if (dueForDetection) {
                nextDetectionElapsedMs = now + getIntervalSeconds().coerceIn(5, 60) * 1000L
            } else {
                lastPreviewElapsedMs = now
            }
            lastCapturedDisplayRotation = displayRotation

            val bitmap = runCatching { imageToUprightBitmap(image, displayRotation) }.getOrNull()
            image.close()

            if (bitmap == null) {
                isBusyProcessing.set(false)
                return@setOnImageAvailableListener
            }

            try {
                onBitmapCaptured(bitmap, dueForDetection)
            } finally {
                isBusyProcessing.set(false)
            }
        }, bgHandler)

        runCatching {
            mgr.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) {
                    if (isClosed.get()) {
                        device.close()
                        return
                    }
                    cameraDevice = device
                    createSession(device, reader, bgHandler)
                }

                override fun onDisconnected(device: CameraDevice) {
                    device.close()
                    cameraDevice = null
                }

                override fun onError(device: CameraDevice, error: Int) {
                    device.close()
                    cameraDevice = null
                    onError("Ошибка Camera2 ($cameraId): код $error")
                }
            }, bgHandler)
        }.onFailure { e ->
            onError("Не удалось открыть камеру $cameraId: ${e.message}")
        }
    }

    private fun createSession(
        device: CameraDevice,
        reader: ImageReader,
        bgHandler: Handler
    ) {
        val surface = reader.surface
        val stateCallback = object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                if (isClosed.get()) {
                    session.close()
                    return
                }
                captureSession = session
                applyRepeatingRequest(session, device, surface, torchOn = false, bgHandler = bgHandler)
            }

            override fun onConfigureFailed(session: CameraCaptureSession) {
                onError("Не удалось настроить поток камеры $cameraId" +
                    (physicalCameraId?.let { " (модуль $it)" } ?: ""))
            }
        }

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val outputConfig = OutputConfiguration(surface).apply {
                    if (!physicalCameraId.isNullOrBlank()) {
                        setPhysicalCameraId(physicalCameraId)
                    }
                }
                val executor = Executor { cmd -> bgHandler.post(cmd) }
                val config = SessionConfiguration(
                    SessionConfiguration.SESSION_REGULAR,
                    listOf(outputConfig),
                    executor,
                    stateCallback
                )
                device.createCaptureSession(config)
            } else {
                @Suppress("DEPRECATION")
                device.createCaptureSession(listOf(surface), stateCallback, bgHandler)
            }
        }.onFailure { e ->
            onError("Ошибка сессии камеры $cameraId: ${e.message}")
        }
    }

    fun setTorch(on: Boolean): Boolean {
        val session = captureSession ?: return false
        val device = cameraDevice ?: return false
        val surface = imageReader?.surface ?: return false
        val bgHandler = handler ?: return false
        if (!hasFlash) return false
        return runCatching {
            applyRepeatingRequest(session, device, surface, torchOn = on, bgHandler = bgHandler)
            true
        }.getOrDefault(false)
    }

    private fun applyRepeatingRequest(
        session: CameraCaptureSession,
        device: CameraDevice,
        surface: android.view.Surface,
        torchOn: Boolean,
        bgHandler: Handler
    ) {
        val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            addTarget(surface)
            set(
                CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
            )
            set(
                CaptureRequest.FLASH_MODE,
                if (torchOn) CaptureRequest.FLASH_MODE_TORCH else CaptureRequest.FLASH_MODE_OFF
            )
        }
        session.setRepeatingRequest(builder.build(), null, bgHandler)
    }

    fun hasFlashUnit(): Boolean = hasFlash

    fun close() {
        if (!isClosed.compareAndSet(false, true)) return
        runCatching { captureSession?.close() }
        captureSession = null
        runCatching { cameraDevice?.close() }
        cameraDevice = null
        runCatching { imageReader?.close() }
        imageReader = null
        runCatching { handlerThread?.quitSafely() }
        handlerThread = null
        handler = null
    }

    private fun imageToUprightBitmap(image: Image, displayRotation: Int): Bitmap {
        val nv21 = yuv420ToNv21(image)
        val yuv = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val baos = ByteArrayOutputStream()
        yuv.compressToJpeg(Rect(0, 0, image.width, image.height), 85, baos)
        val jpeg = baos.toByteArray()
        val raw = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
            ?: error("JPEG decode failed")

        val rotation = CameraRotationHelper.computeUprightRotationDegrees(
            sensorOrientationDeg = sensorOrientationDeg,
            isFrontFacing = isFrontFacing,
            surfaceRotation = displayRotation
        )
        if (rotation == 0 && !isFrontFacing) return raw
        val matrix = Matrix().apply {
            if (rotation != 0) postRotate(rotation.toFloat())
            if (isFrontFacing) postScale(-1f, 1f)
        }
        val rotated = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
        if (rotated !== raw) raw.recycle()
        return rotated
    }

    private fun yuv420ToNv21(image: Image): ByteArray {
        val width = image.width
        val height = image.height
        val ySize = width * height
        val uvSize = width * height / 2
        val nv21 = ByteArray(ySize + uvSize)

        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride
        var pos = 0
        for (row in 0 until height) {
            val rowStart = row * yRowStride
            if (yPixelStride == 1) {
                yBuffer.position(rowStart)
                yBuffer.get(nv21, pos, width)
                pos += width
            } else {
                for (col in 0 until width) {
                    nv21[pos++] = yBuffer.get(rowStart + col * yPixelStride)
                }
            }
        }

        val uvHeight = height / 2
        val uvWidth = width / 2
        val uRowStride = uPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        val vRowStride = vPlane.rowStride
        val vPixelStride = vPlane.pixelStride

        for (row in 0 until uvHeight) {
            val uRowStart = row * uRowStride
            val vRowStart = row * vRowStride
            for (col in 0 until uvWidth) {
                nv21[pos++] = vBuffer.get(vRowStart + col * vPixelStride)
                nv21[pos++] = uBuffer.get(uRowStart + col * uPixelStride)
            }
        }
        return nv21
    }
}
