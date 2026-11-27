package com.example.ml

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Decodes Ultralytics YOLO26 outputs for COCO class 0 ("person").
 *
 * Supports:
 * 1) Native YOLO26 end-to-end NMS-free shape: [1, N, 6] or [N, 6]
 *    where each row is [x1, y1, x2, y2, confidence, classId].
 * 2) Standard one-to-many YOLO shape: [1, 4+nc, numBoxes] (e.g. [1, 84, 8400])
 *    or transposed [1, numBoxes, 4+nc] (e.g. [1, 8400, 84]).
 */
object Yolo26Decoder {

    private const val PERSON_CLASS_ID = 0

    fun decodeNmsFreeRows(
        rows: Array<FloatArray>,
        threshold: Float,
        letterbox: LetterboxInput
    ): List<DetectionBox> {
        val boxes = mutableListOf<DetectionBox>()
        for (row in rows) {
            if (row.size < 6) continue
            val score = row[4]
            val classId = row[5].roundToInt()
            if (classId != PERSON_CLASS_ID || score < threshold) continue

            val mapped = mapBoxToNormalized(
                x1Raw = row[0],
                y1Raw = row[1],
                x2Raw = row[2],
                y2Raw = row[3],
                letterbox = letterbox
            )
            if (mapped != null) {
                boxes.add(mapped.copy(confidence = score))
            }
        }
        return boxes.sortedByDescending { it.confidence }
    }

    /**
     * Decodes [1, features, anchors] (channelsFirst = true, e.g. [1, 84, 8400])
     * or [1, anchors, features] (channelsFirst = false, e.g. [1, 8400, 84]).
     */
    fun decodeClassicHead(
        tensor: Array<FloatArray>,
        channelsFirst: Boolean,
        threshold: Float,
        letterbox: LetterboxInput,
        iouThreshold: Float = 0.45f
    ): List<DetectionBox> {
        val candidates = mutableListOf<DetectionBox>()
        if (channelsFirst) {
            if (tensor.size < 5) return emptyList()
            val numAnchors = tensor[0].size
            for (a in 0 until numAnchors) {
                val score = tensor[4 + PERSON_CLASS_ID][a]
                if (score < threshold) continue
                val cx = tensor[0][a]
                val cy = tensor[1][a]
                val w = tensor[2][a]
                val h = tensor[3][a]
                val mapped = mapCenterWhToNormalized(cx, cy, w, h, letterbox)
                if (mapped != null) {
                    candidates.add(mapped.copy(confidence = score))
                }
            }
        } else {
            for (row in tensor) {
                if (row.size < 5) continue
                val score = row[4 + PERSON_CLASS_ID]
                if (score < threshold) continue
                val mapped = mapCenterWhToNormalized(row[0], row[1], row[2], row[3], letterbox)
                if (mapped != null) {
                    candidates.add(mapped.copy(confidence = score))
                }
            }
        }
        return applyNms(candidates, iouThreshold)
    }

    private fun mapCenterWhToNormalized(
        cxRaw: Float,
        cyRaw: Float,
        wRaw: Float,
        hRaw: Float,
        letterbox: LetterboxInput
    ): DetectionBox? {
        val scaleToInput = if (cxRaw <= 1.5f && cyRaw <= 1.5f && wRaw <= 1.5f && hRaw <= 1.5f) {
            letterbox.inputWidth.toFloat()
        } else {
            1.0f
        }
        val cx = cxRaw * scaleToInput
        val cy = cyRaw * scaleToInput
        val w = wRaw * scaleToInput
        val h = hRaw * scaleToInput
        return mapBoxToNormalized(
            x1Raw = cx - w / 2f,
            y1Raw = cy - h / 2f,
            x2Raw = cx + w / 2f,
            y2Raw = cy + h / 2f,
            letterbox = letterbox
        )
    }

    private fun mapBoxToNormalized(
        x1Raw: Float,
        y1Raw: Float,
        x2Raw: Float,
        y2Raw: Float,
        letterbox: LetterboxInput
    ): DetectionBox? {
        val isNormalized = x2Raw <= 1.5f && y2Raw <= 1.5f && x1Raw >= -0.5f && y1Raw >= -0.5f
        val x1In = if (isNormalized) x1Raw * letterbox.inputWidth else x1Raw
        val y1In = if (isNormalized) y1Raw * letterbox.inputHeight else y1Raw
        val x2In = if (isNormalized) x2Raw * letterbox.inputWidth else x2Raw
        val y2In = if (isNormalized) y2Raw * letterbox.inputHeight else y2Raw

        val x1Orig = ((x1In - letterbox.padX) / letterbox.scale) / letterbox.srcWidth.toFloat()
        val y1Orig = ((y1In - letterbox.padY) / letterbox.scale) / letterbox.srcHeight.toFloat()
        val x2Orig = ((x2In - letterbox.padX) / letterbox.scale) / letterbox.srcWidth.toFloat()
        val y2Orig = ((y2In - letterbox.padY) / letterbox.scale) / letterbox.srcHeight.toFloat()

        val l = x1Orig.coerceIn(0f, 1f)
        val t = y1Orig.coerceIn(0f, 1f)
        val r = x2Orig.coerceIn(0f, 1f)
        val b = y2Orig.coerceIn(0f, 1f)
        if (r <= l || b <= t) return null
        return DetectionBox(left = l, top = t, right = r, bottom = b, confidence = 0f, label = "person")
    }

    private fun applyNms(boxes: List<DetectionBox>, iouThreshold: Float): List<DetectionBox> {
        if (boxes.size <= 1) return boxes
        val sorted = boxes.sortedByDescending { it.confidence }
        val selected = mutableListOf<DetectionBox>()
        val suppressed = BooleanArray(sorted.size)
        for (i in sorted.indices) {
            if (suppressed[i]) continue
            val current = sorted[i]
            selected.add(current)
            for (j in i + 1 until sorted.size) {
                if (!suppressed[j] && iou(current, sorted[j]) > iouThreshold) {
                    suppressed[j] = true
                }
            }
        }
        return selected
    }

    private fun iou(a: DetectionBox, b: DetectionBox): Float {
        val interLeft = max(a.left, b.left)
        val interTop = max(a.top, b.top)
        val interRight = min(a.right, b.right)
        val interBottom = min(a.bottom, b.bottom)
        val interArea = max(0f, interRight - interLeft) * max(0f, interBottom - interTop)
        val areaA = (a.right - a.left) * (a.bottom - a.top)
        val areaB = (b.right - b.left) * (b.bottom - b.top)
        val union = areaA + areaB - interArea
        return if (union <= 0f) 0f else interArea / union
    }
}
