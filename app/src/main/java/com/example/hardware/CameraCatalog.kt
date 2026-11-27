package com.example.hardware

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import com.example.data.CameraLensOption
import java.util.Locale
import kotlin.math.atan

data class DeviceCameraOption(
    val optionKey: String,
    val cameraId: String,
    val physicalCameraId: String? = null,
    val isDirectCamera2Only: Boolean = false,
    val label: String,
    val subtitle: String,
    val lensOption: CameraLensOption,
    val focalLengthMm: Float?,
    val horizontalFovDeg: Int?,
    val useMinZoomRatio: Boolean = false,
    val minZoomRatio: Float = 1.0f
)

object CameraCatalog {

    private val HIDDEN_PROBE_IDS = listOf("2", "3", "4", "5", "20", "21", "22")

    fun findMatch(
        options: List<DeviceCameraOption>,
        selectedId: String,
        fallbackLens: CameraLensOption
    ): DeviceCameraOption? {
        return options.firstOrNull { it.optionKey == selectedId }
            ?: options.firstOrNull { it.cameraId == selectedId }
            ?: options.firstOrNull { it.lensOption == fallbackLens }
    }

    fun discoverCameras(context: Context, isRu: Boolean = false): List<DeviceCameraOption> {
        val mgr = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return fallbackOptions(isRu)

        val listedIds = runCatching { mgr.cameraIdList.toList() }.getOrElse { emptyList() }
        if (listedIds.isEmpty()) return fallbackOptions(isRu)

        data class RawCam(
            val id: String,
            val parentLogicalId: String?,
            val isListed: Boolean,
            val facing: Int,
            val focal: Float?,
            val fovDeg: Int?,
            val minZoom: Float,
            val pixelCount: Long
        )

        fun inspectCamera(id: String, parentLogicalId: String?, isListed: Boolean): RawCam? {
            return runCatching {
                val chars = mgr.getCameraCharacteristics(id)
                val caps = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                val backwardCompat = caps?.contains(
                    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE
                ) ?: true

                // Physical sub-cameras of a logical multi-camera may omit BACKWARD_COMPATIBLE in sub-characteristics
                // while still streaming YUV_420_888 through OutputConfiguration.setPhysicalCameraId().
                if (parentLogicalId == null && !backwardCompat) {
                    return@runCatching null
                }

                val streamMap = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                if (parentLogicalId == null && streamMap != null && !streamMap.isOutputSupportedFor(ImageFormat.YUV_420_888)) {
                    return@runCatching null
                }

                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                    ?: CameraCharacteristics.LENS_FACING_BACK
                val focal = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                    ?.minOrNull()
                val sensorSize = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
                val fovDeg = if (focal != null && focal > 0f && sensorSize != null && sensorSize.width > 0f) {
                    Math.toDegrees(2.0 * atan((sensorSize.width / (2f * focal)).toDouble())).toInt()
                } else null

                val pixelSize = chars.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
                val pixelCount = if (pixelSize != null) {
                    pixelSize.width.toLong() * pixelSize.height.toLong()
                } else 0L

                val minZoom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    chars.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)?.lower ?: 1.0f
                } else {
                    1.0f
                }

                RawCam(
                    id = id,
                    parentLogicalId = parentLogicalId,
                    isListed = isListed,
                    facing = facing,
                    focal = focal,
                    fovDeg = fovDeg,
                    minZoom = minZoom,
                    pixelCount = pixelCount
                )
            }.getOrNull()
        }

        val discoveredByKey = linkedMapOf<String, RawCam>()

        // 1. Standard cameras from cameraIdList (directly usable via CameraX)
        for (id in listedIds) {
            inspectCamera(id, parentLogicalId = null, isListed = true)?.let { cam ->
                discoveredByKey["ID_$id"] = cam
            }
        }

        // 2. Physical sub-cameras inside logical multi-cameras (API 28+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            for (logicalId in listedIds) {
                val chars = runCatching { mgr.getCameraCharacteristics(logicalId) }.getOrNull() ?: continue
                val physIds = runCatching { chars.physicalCameraIds }.getOrNull().orEmpty()
                for (physId in physIds) {
                    if (physId !in listedIds) {
                        inspectCamera(physId, parentLogicalId = logicalId, isListed = false)?.let { cam ->
                            discoveredByKey.putIfAbsent("PHYS_${logicalId}_$physId", cam)
                        }
                    }
                }
            }
        }

        // 3. Probe hidden vendor IDs (common on MediaTek / Infinix / Xiaomi Android 11+)
        val knownPhysicalOrListed = discoveredByKey.values.map { it.id }.toSet()
        for (probeId in HIDDEN_PROBE_IDS) {
            if (probeId !in knownPhysicalOrListed) {
                inspectCamera(probeId, parentLogicalId = null, isListed = false)?.let { cam ->
                    discoveredByKey["PROBE_$probeId"] = cam
                }
            }
        }

        val allCams = discoveredByKey.values.toList()
        if (allCams.isEmpty()) return fallbackOptions(isRu)

        val result = mutableListOf<DeviceCameraOption>()
        val listedBack = allCams.filter { it.facing == CameraCharacteristics.LENS_FACING_BACK && it.isListed }
        val allBack = allCams.filter { it.facing == CameraCharacteristics.LENS_FACING_BACK }
        val maxBackFocal = allBack.mapNotNull { it.focal }.maxOrNull()

        // Front cameras first (only listed/CameraX or direct if needed)
        allCams.filter { it.facing == CameraCharacteristics.LENS_FACING_FRONT && it.isListed }
            .forEachIndexed { idx, cam ->
                val suffix = if (idx == 0) "" else " #${idx + 1}"
                val meta = formatMeta("ID ${cam.id}", cam.focal, cam.fovDeg, isRu)
                result.add(
                    DeviceCameraOption(
                        optionKey = "ID_${cam.id}",
                        cameraId = cam.id,
                        label = (if (isRu) "Фронтальная" else "Front") + suffix,
                        subtitle = meta,
                        lensOption = CameraLensOption.FRONT,
                        focalLengthMm = cam.focal,
                        horizontalFovDeg = cam.fovDeg
                    )
                )
            }

        var hasCameraXUltrawide = false
        var backNormalIdx = 0

        // Filter out obvious 2MP depth/macro helper sensors from extra hidden/physical cams
        val filteredBack = allBack.filter { cam ->
            val looksLikeTinyMacroOrDepth = !cam.isListed &&
                cam.pixelCount in 1L..2_400_000L &&
                (cam.fovDeg == null || cam.fovDeg < 85) &&
                (cam.focal == null || cam.focal >= 2.5f)
            !looksLikeTinyMacroOrDepth
        }

        filteredBack.forEach { cam ->
            val hasWideFov = cam.fovDeg != null && cam.fovDeg >= 85
            val hasShortWideFocal = cam.focal != null && cam.focal > 0f &&
                (cam.focal < 2.6f || (maxBackFocal != null && cam.focal < maxBackFocal * 0.74f)) &&
                (cam.pixelCount == 0L || cam.pixelCount >= 2_500_000L)
            val isUltrawide = hasWideFov || hasShortWideFocal

            // Skip hidden/physical non-ultrawide duplicates of the main rear camera
            if (!cam.isListed && !isUltrawide) return@forEach

            if (isUltrawide && cam.isListed) {
                hasCameraXUltrawide = true
            }

            val label = when {
                isUltrawide && cam.parentLogicalId != null ->
                    if (isRu) "Широкоугольная (модуль ${cam.id})" else "Ultrawide (Module ${cam.id})"
                isUltrawide && !cam.isListed ->
                    if (isRu) "Широкоугольная (Camera2)" else "Ultrawide (Camera2)"
                isUltrawide ->
                    if (isRu) "Широкоугольная" else "Ultrawide"
                backNormalIdx == 0 -> {
                    backNormalIdx++
                    if (isRu) "Основная" else "Main"
                }
                else -> {
                    backNormalIdx++
                    if (isRu) "Тыловая #$backNormalIdx" else "Rear #$backNormalIdx"
                }
            }

            val idTag = when {
                cam.parentLogicalId != null -> "ID ${cam.parentLogicalId}→${cam.id}"
                !cam.isListed -> "Direct ID ${cam.id}"
                else -> "ID ${cam.id}"
            }

            val optionKey = when {
                cam.parentLogicalId != null -> "PHYS_${cam.parentLogicalId}_${cam.id}"
                !cam.isListed -> "PROBE_${cam.id}"
                else -> "ID_${cam.id}"
            }

            result.add(
                DeviceCameraOption(
                    optionKey = optionKey,
                    cameraId = cam.parentLogicalId ?: cam.id,
                    physicalCameraId = if (cam.parentLogicalId != null) cam.id else null,
                    isDirectCamera2Only = !cam.isListed,
                    label = label,
                    subtitle = formatMeta(idTag, cam.focal, cam.fovDeg, isRu),
                    lensOption = if (isUltrawide) CameraLensOption.ULTRAWIDE else CameraLensOption.BACK,
                    focalLengthMm = cam.focal,
                    horizontalFovDeg = cam.fovDeg
                )
            )
        }

        // If no standalone ultrawide CameraX ID exists, expose optical/zoom-out ultrawide (e.g. 0.6x) when supported
        if (!hasCameraXUltrawide) {
            val zoomWideCam = listedBack.firstOrNull { it.minZoom < 0.98f }
            if (zoomWideCam != null) {
                val zoomLabel = String.format(Locale.US, "%.1f×", zoomWideCam.minZoom)
                result.add(
                    DeviceCameraOption(
                        optionKey = "ZOOM_WIDE_${zoomWideCam.id}",
                        cameraId = zoomWideCam.id,
                        label = if (isRu) "Широкоугольная ($zoomLabel)" else "Ultrawide ($zoomLabel)",
                        subtitle = if (isRu) "ID ${zoomWideCam.id} • Мин. зум $zoomLabel" else "ID ${zoomWideCam.id} • Min zoom $zoomLabel",
                        lensOption = CameraLensOption.ULTRAWIDE,
                        focalLengthMm = zoomWideCam.focal,
                        horizontalFovDeg = 120,
                        useMinZoomRatio = true,
                        minZoomRatio = zoomWideCam.minZoom
                    )
                )
            }
        }

        return result.ifEmpty { fallbackOptions(isRu) }
    }

    private fun formatMeta(idTag: String, focal: Float?, fovDeg: Int?, isRu: Boolean): String {
        val focalUnit = if (isRu) "мм" else "mm"
        val focalStr = focal?.let { String.format(Locale.US, "%.1f%s", it, focalUnit) }
        val fovStr = fovDeg?.let { "$it°" }
        return listOfNotNull(idTag, focalStr, fovStr).joinToString(" • ")
    }

    private fun fallbackOptions(isRu: Boolean): List<DeviceCameraOption> = listOf(
        DeviceCameraOption(
            optionKey = "AUTO_FRONT",
            cameraId = "AUTO_FRONT",
            label = if (isRu) "Фронтальная" else "Front",
            subtitle = if (isRu) "По умолчанию" else "Default",
            lensOption = CameraLensOption.FRONT,
            focalLengthMm = null,
            horizontalFovDeg = null
        ),
        DeviceCameraOption(
            optionKey = "AUTO_BACK",
            cameraId = "AUTO_BACK",
            label = if (isRu) "Основная" else "Main",
            subtitle = if (isRu) "Тыловая камера" else "Rear camera",
            lensOption = CameraLensOption.BACK,
            focalLengthMm = null,
            horizontalFovDeg = null
        )
    )
}
