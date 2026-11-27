package com.example

import com.example.data.CameraLensOption
import com.example.hardware.CameraCatalog
import com.example.hardware.DeviceCameraOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import kotlin.math.roundToInt

class CameraCatalogAndTorchTest {

    @Test
    fun findMatch_distinguishesZoomWideFromMainCameraWithSameCameraId() {
        val options = listOf(
            DeviceCameraOption(
                optionKey = "ID_1",
                cameraId = "1",
                label = "Фронтальная",
                subtitle = "ID 1",
                lensOption = CameraLensOption.FRONT,
                focalLengthMm = 3.2f,
                horizontalFovDeg = 76
            ),
            DeviceCameraOption(
                optionKey = "ID_0",
                cameraId = "0",
                label = "Основная",
                subtitle = "ID 0",
                lensOption = CameraLensOption.BACK,
                focalLengthMm = 4.7f,
                horizontalFovDeg = 69
            ),
            DeviceCameraOption(
                optionKey = "ZOOM_WIDE_0",
                cameraId = "0",
                label = "Широкоугольная (0.6×)",
                subtitle = "ID 0 • Мин. зум 0.6×",
                lensOption = CameraLensOption.ULTRAWIDE,
                focalLengthMm = 4.7f,
                horizontalFovDeg = 120,
                useMinZoomRatio = true,
                minZoomRatio = 0.6f
            )
        )

        val matchedWide = CameraCatalog.findMatch(options, "ZOOM_WIDE_0", CameraLensOption.ULTRAWIDE)
        assertNotNull(matchedWide)
        assertEquals("ZOOM_WIDE_0", matchedWide?.optionKey)
        assertEquals(true, matchedWide?.useMinZoomRatio)

        val matchedMain = CameraCatalog.findMatch(options, "ID_0", CameraLensOption.BACK)
        assertEquals("ID_0", matchedMain?.optionKey)
        assertEquals(false, matchedMain?.useMinZoomRatio)
    }

    @Test
    fun findMatch_matchesPhysicalAndProbedUltrawideOptions() {
        val options = listOf(
            DeviceCameraOption(
                optionKey = "ID_0",
                cameraId = "0",
                label = "Основная",
                subtitle = "ID 0",
                lensOption = CameraLensOption.BACK,
                focalLengthMm = 4.7f,
                horizontalFovDeg = 69
            ),
            DeviceCameraOption(
                optionKey = "PHYS_0_2",
                cameraId = "0",
                physicalCameraId = "2",
                isDirectCamera2Only = true,
                label = "Широкоугольная (модуль 2)",
                subtitle = "ID 0→2 • 1.8мм • 118°",
                lensOption = CameraLensOption.ULTRAWIDE,
                focalLengthMm = 1.8f,
                horizontalFovDeg = 118
            )
        )

        val matched = CameraCatalog.findMatch(options, "PHYS_0_2", CameraLensOption.ULTRAWIDE)
        assertEquals("PHYS_0_2", matched?.optionKey)
        assertEquals("2", matched?.physicalCameraId)
        assertEquals(true, matched?.isDirectCamera2Only)
    }

    @Test
    fun brightnessMapping_spansFullRangeMonotonically() {
        val pulse10 = (55L + ((10 - 10) * 445L) / 90L).coerceIn(55L, 500L)
        val pulse100 = (55L + ((100 - 10) * 445L) / 90L).coerceIn(55L, 500L)
        assertEquals(55L, pulse10)
        assertEquals(500L, pulse100)

        val maxLevel = 45
        val level10 = (1.0 + ((10 - 10) / 90.0) * (maxLevel - 1)).roundToInt().coerceIn(1, maxLevel)
        val level100 = (1.0 + ((100 - 10) / 90.0) * (maxLevel - 1)).roundToInt().coerceIn(1, maxLevel)
        assertEquals(1, level10)
        assertEquals(45, level100)
    }
}
