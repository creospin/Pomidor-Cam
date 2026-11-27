package com.example.hardware

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.Surface
import android.view.WindowManager

object CameraRotationHelper {

    fun surfaceRotationToDegrees(surfaceRotation: Int): Int = when (surfaceRotation) {
        Surface.ROTATION_90 -> 90
        Surface.ROTATION_180 -> 180
        Surface.ROTATION_270 -> 270
        else -> 0
    }

    /**
     * Computes the clockwise rotation (0, 90, 180, or 270 degrees) needed to make a
     * raw Camera2 sensor frame upright for the current display rotation.
     */
    fun computeUprightRotationDegrees(
        sensorOrientationDeg: Int,
        isFrontFacing: Boolean,
        surfaceRotation: Int
    ): Int {
        val displayDeg = surfaceRotationToDegrees(surfaceRotation)
        val sensor = ((sensorOrientationDeg % 360) + 360) % 360
        return if (isFrontFacing) {
            (sensor + displayDeg) % 360
        } else {
            (sensor - displayDeg + 360) % 360
        }
    }

    fun getDisplayRotation(context: Context): Int {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        val display = dm?.getDisplay(Display.DEFAULT_DISPLAY)
        if (display != null) {
            return display.rotation
        }
        @Suppress("DEPRECATION")
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        @Suppress("DEPRECATION")
        return wm?.defaultDisplay?.rotation ?: Surface.ROTATION_0
    }
}
