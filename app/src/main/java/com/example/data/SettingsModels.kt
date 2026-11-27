package com.example.data

import com.example.i18n.AppLanguage

enum class CameraLensOption(val labelEn: String, val labelRu: String) {
    FRONT("Front", "Фронтальная"),
    BACK("Main", "Основная"),
    ULTRAWIDE("Ultrawide", "Широкоугольная");

    fun label(isRu: Boolean): String = if (isRu) labelRu else labelEn
}

enum class DeviceProfile(
    val labelEn: String,
    val labelRu: String,
    val subtitleEn: String,
    val subtitleRu: String
) {
    INFINIX_NOTE_10_PRO(
        labelEn = "Infinix Note 10 Pro",
        labelRu = "Infinix Note 10 Pro",
        subtitleEn = "Helio G95 • 8 GB • 64MP + 120° Ultrawide + 16MP Front",
        subtitleRu = "Helio G95 • 8 ГБ • 64МП + 120° ширик + 16МП фронт"
    ),
    STANDARD(
        labelEn = "Universal",
        labelRu = "Универсальный",
        subtitleEn = "Standard profile for any Android device",
        subtitleRu = "Стандартный профиль для любого Android"
    );

    fun label(isRu: Boolean): String = if (isRu) labelRu else labelEn
    fun subtitle(isRu: Boolean): String = if (isRu) subtitleRu else subtitleEn
}

enum class DetectorMode(val labelEn: String, val labelRu: String) {
    AUTO("Auto (YOLO26 → Built-in)", "Авто (YOLO26 → встроенный)"),
    YOLO26_ONNX("YOLO26 ONNX", "YOLO26 ONNX"),
    YOLO26_TFLITE("YOLO26 TFLite", "YOLO26 TFLite"),
    BUILTIN_MLKIT("Built-in Detector", "Встроенный детектор");

    fun label(isRu: Boolean): String = if (isRu) labelRu else labelEn
}

enum class LanProtocol(val labelEn: String, val labelRu: String) {
    HTTP_AND_UDP("HTTP + UDP", "HTTP + UDP"),
    HTTP_ONLY("HTTP POST only", "Только HTTP POST"),
    UDP_UNICAST("UDP to PC IP", "UDP на IP ПК"),
    UDP_BROADCAST("UDP Broadcast (LAN)", "UDP Broadcast (вся сеть)");

    fun label(isRu: Boolean): String = if (isRu) labelRu else labelEn
}

data class PomidorSettings(
    val appLanguage: AppLanguage = AppLanguage.EN,
    val cameraLens: CameraLensOption = CameraLensOption.FRONT,
    val selectedCameraId: String = "AUTO_FRONT",
    val selectedCameraLabel: String = "",
    val useUltrawideZoomFallback: Boolean = false,
    val deviceProfile: DeviceProfile = DeviceProfile.INFINIX_NOTE_10_PRO,
    val checkIntervalSeconds: Int = 10,
    val focusDurationMinutes: Int = 20,
    val breakDurationMinutes: Int = 5,
    val maxMissedChecks: Int = 2,
    val detectorMode: DetectorMode = DetectorMode.AUTO,
    val customModelPath: String = "",
    val customModelName: String = "",
    val confidenceThreshold: Float = 0.45f,
    val torchBlinkEnabled: Boolean = true,
    val torchBlinkCount: Int = 3,
    val torchBrightnessPercent: Int = 80,
    val pcHost: String = "192.168.1.100",
    val pcPort: Int = 8765,
    val lanProtocol: LanProtocol = LanProtocol.HTTP_AND_UDP,
    val workdayStartHour: Int = 6,
    val workdayEndHour: Int = 4,
    val showLivePreview: Boolean = true,
    val autoMorningExportEnabled: Boolean = true,
    val autoMorningExportHour: Int = 8,
    val lastAutoExportedWorkday: String = "",
    val lastAutoExportStatus: String = ""
)
