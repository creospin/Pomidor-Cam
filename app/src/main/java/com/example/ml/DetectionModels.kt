package com.example.ml

data class DetectionBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val confidence: Float,
    val label: String = "person"
)

data class DetectionResult(
    val personDetected: Boolean,
    val confidence: Float,
    val boxes: List<DetectionBox>,
    val inferenceMs: Long,
    val engineLabel: String,
    val errorMessage: String? = null
)
