package com.example.model

import androidx.compose.ui.graphics.Color

enum class CaptureMode(val label: String) {
    PHOTO("PHOTO"),
    VIDEO("VIDEO"),
    NIGHT("NIGHT"),
    RAW_VIDEO("DNG SEQ")
}

enum class ExposureProgram(val shortCode: String, val label: String) {
    AUTO("AUTO", "Auto Exposure"),
    PROGRAM("P", "Program AE + EV"),
    SHUTTER_PRIORITY("S", "Shutter Priority"),
    ISO_PRIORITY("ISO", "ISO Priority"),
    MANUAL("M", "Full Manual")
}

enum class FlashMode(val label: String, val shortCode: String) {
    OFF("Off", "OFF"),
    AUTO("Auto", "AUTO"),
    ON("Fill Flash", "ON"),
    TORCH("LED Torch", "TORCH")
}

enum class SelfTimerOption(val seconds: Int, val label: String) {
    OFF(0, "Off"),
    SEC_3(3, "3s"),
    SEC_10(10, "10s")
}

enum class PhotoAspectRatio(val label: String, val ratioPortrait: Float, val ratioLandscape: Float) {
    RATIO_4_3("4:3", 3f / 4f, 4f / 3f),
    RATIO_3_2("3:2", 2f / 3f, 3f / 2f),
    RATIO_16_9("16:9", 9f / 16f, 16f / 9f),
    RATIO_1_1("1:1", 1f, 1f)
}

enum class PhotoSequenceMode(val label: String, val shortCode: String) {
    SINGLE("Single", "1x"),
    BURST("Burst 5x", "BURST"),
    AEB_HDR("AEB HDR (3EV)", "AEB"),
    FOCUS_STACK("Focus Stack", "F-STK"),
    INTERVAL("Intervalometer", "INTVL")
}

enum class NightStackStyle(val label: String, val frames: Int, val description: String) {
    LOW_LIGHT("Night Stack", 4, "4-frame multi-exposure noise reduction"),
    ASTRO_STACK("Astro / Long Exp", 6, "6-frame deep sky shadow recovery"),
    LIGHT_TRAILS("Light Trails", 6, "6-frame lighten blend for traffic & star trails")
}

enum class MonitorScopeMode(val label: String, val shortCode: String) {
    OFF("Off", "OFF"),
    HISTOGRAM("RGB Histogram", "HIST"),
    WAVEFORM("Luma Waveform", "WFM"),
    RGB_PARADE("RGB Parade", "PRDE"),
    VECTORSCOPE("Vectorscope", "VECT")
}

enum class PhotoFormat(val label: String, val badge: String) {
    JPEG("JPEG 8-bit", "JPG"),
    RAW_DNG("RAW 12-bit DNG", "DNG"),
    RAW_PLUS_JPEG("RAW + JPEG", "DNG+JPG")
}

enum class VideoCodecFormat(val label: String, val shortLabel: String, val description: String) {
    H264_STANDARD("SDR Video (MP4)", "SDR MP4", "Hardware MP4 video encoding via CameraX Recorder"),
    HEVC_LOG("Log Monitor MP4", "LOG MON", "Tone-mapped preview only; MP4 does not contain the monitoring LOG curve. Codec is device-selected."),
    HLG_10BIT("HLG 10-bit HDR", "HLG10", "Hardware 10-bit Hybrid Log-Gamma HEVC where supported"),
    IRISI_LOG_8("IrisI LOG 8-bit (Experimental)", "LOG8", "Recorded GPU LOG from ISP SDR, AVC with grading sidecar. Does not recover clipped highlights; 8-bit banding risk."),
    RAW_CINEMA_DNG("Experimental DNG Sequence", "DNG SEQ", "Sequential RAW_SENSOR DNG frames; cadence is hardware-limited and not encoded video")
}

enum class LogProfile(
    val displayName: String,
    val shortName: String,
    val dynamicRangeStops: String,
    val blackLift: Float,
    val contrastScale: Float,
    val saturationScale: Float,
    val highlightCompression: Float
) {
    REC_709("Rec.709 Standard", "709", "8.5 Stops", 0f, 1.0f, 1.0f, 1.0f),
    SLOG3("CineLog-S (S-Log3)", "S-LOG3", "14+ Stops", 26f, 0.56f, 0.48f, 0.78f),
    CLOG2("CineLog-C (C-Log2)", "C-LOG2", "15 Stops", 22f, 0.62f, 0.54f, 0.82f),
    VLOG("CineLog-V (V-Log)", "V-LOG", "14 Stops", 24f, 0.58f, 0.50f, 0.80f),
    RAW_FLAT("Linear Sensor Flat", "FLAT", "16-bit", 14f, 0.68f, 0.62f, 0.88f)
}

enum class FilmPreset(
    val displayName: String,
    val shortCode: String,
    val tagline: String,
    val defaultGrain: Float,
    val warmth: Float,
    val tint: Float,
    val contrast: Float,
    val saturation: Float,
    val redGain: Float,
    val greenGain: Float,
    val blueGain: Float,
    val swatchColor: Color
) {
    NONE("Natural", "NONE", "Calibrated sensor response", 0f, 0f, 0f, 1.0f, 1.0f, 1f, 1f, 1f, Color(0xFF90A4AE)),
    PORTRA_400("Portra 400", "P400", "Warm skin tones & smooth roll-off", 0.22f, 0.14f, 0.04f, 0.94f, 0.96f, 1.06f, 1.01f, 0.92f, Color(0xFFD7A57E)),
    CINESTILL_800T("CineStill 800T", "800T", "Tungsten night look & cool shadows", 0.34f, -0.18f, -0.06f, 1.12f, 1.06f, 0.95f, 1.02f, 1.14f, Color(0xFF5D9EAE)),
    VELVIA_50("Velvia 50", "RVP50", "Vivid slide contrast & rich greens", 0.12f, 0.05f, 0.08f, 1.24f, 1.32f, 1.08f, 0.98f, 1.04f, Color(0xFFC86D7C)),
    TRIX_400("Tri-X 400 B&W", "400TX", "Silver gelatin monochrome", 0.48f, 0f, 0f, 1.26f, 0f, 1.0f, 1.0f, 1.0f, Color(0xFFB0BEC5)),
    TEAL_ORANGE("Split Teal & Amber", "T&O", "Dual-tone cinema separation", 0.16f, 0.08f, -0.04f, 1.15f, 1.08f, 1.10f, 0.99f, 1.05f, Color(0xFF4F9D96)),
    BLEACH_BYPASS("Bleach Bypass", "ENR", "High-contrast silver retention", 0.40f, -0.04f, 0f, 1.34f, 0.46f, 1.01f, 1.01f, 0.98f, Color(0xFF78909C)),
    KODACHROME_64("Kodachrome 64", "KR64", "Classic warm reds & golden tones", 0.18f, 0.10f, -0.02f, 1.14f, 1.10f, 1.09f, 0.99f, 0.90f, Color(0xFFC89B54))
}

data class ColorGradingSettings(
    val lift: Float = 0f,
    val gamma: Float = 0f,
    val gain: Float = 1.0f,
    val contrast: Float = 1.0f,
    val saturation: Float = 1.0f,
    val temperatureOffset: Float = 0f,
    val tintOffset: Float = 0f,
    val vignette: Float = 0f,
    val grainAmount: Float = 0.15f,
    val grainSize: Float = 1.2f,
    val monitoringLutEnabled: Boolean = true
)

data class DevelopSettings(
    val exposureEv: Float = 0f,       // -2.0f..+2.0f
    val contrast: Float = 1.0f,       // 0.5f..1.6f
    val highlights: Float = 0f,       // -1.0f..+1.0f
    val shadows: Float = 0f,          // -1.0f..+1.0f
    val whites: Float = 0f,           // -0.5f..+0.5f
    val blacks: Float = 0f,           // -0.5f..+0.5f
    val temperature: Float = 0f,      // -0.8f..+0.8f
    val tint: Float = 0f,             // -0.8f..+0.8f
    val saturation: Float = 1.0f,     // 0f..1.6f
    val vibrance: Float = 0f,         // -0.5f..+0.5f
    val sharpening: Float = 0.15f,    // 0f..1.0f
    val vignette: Float = 0f,         // 0f..0.8f
    val rotationDeg: Int = 0,         // 0, 90, 180, 270
    val filmPreset: FilmPreset = FilmPreset.NONE
)

enum class VideoResolution(
    val label: String,
    val shortLabel: String,
    val width: Int,
    val height: Int,
    val isOpenGate: Boolean = false,
    val isBinned: Boolean = false
) {
    OPEN_GATE_4_3("Open Gate 4:3 Full", "OG FULL", 4032, 3024, isOpenGate = true, isBinned = false),
    OPEN_GATE_HALF("Open Gate 4:3 (1/2 Binned)", "OG 1/2", 2016, 1512, isOpenGate = true, isBinned = true),
    UHD_4K("4K UHD 16:9", "4K UHD", 3840, 2160),
    FHD_1080P("1080p Full HD", "1080P", 1920, 1080),
    HD_720P("720p Proxy", "720P", 1280, 720)
}

enum class VideoFps(val fps: Int, val label: String, val isHighSpeed: Boolean = false) {
    FPS_24(24, "24p"),
    FPS_25(25, "25p"),
    FPS_30(30, "30p"),
    FPS_60(60, "60p", isHighSpeed = true),
    FPS_120(120, "120p Slow", isHighSpeed = true)
}

enum class VideoBitrate(val mbps: Int, val label: String) {
    MBPS_25(25, "25 Mbps"),
    MBPS_50(50, "50 Mbps"),
    MBPS_100(100, "100 Mbps"),
    MBPS_150(150, "150 Mbps"),
    MBPS_200(200, "200 Mbps Pro")
}

enum class StabilizationMode(
    val displayName: String,
    val shortCode: String,
    val description: String,
    val cropFactor: String
) {
    OFF("Off", "OFF", "Native sensor FOV for gimbals & tripods", "1.00x"),
    STANDARD("Standard", "STD", "OIS + balanced electronic stabilization", "1.06x"),
    ENHANCED("Enhanced", "ENH", "Predictive horizon smoothing for walking shots", "1.12x"),
    ACTION("Action", "ACT", "High-frequency EIS + fast shutter priority for motion", "1.22x")
}

enum class PeakingColor(val label: String, val color: Color) {
    AMBER("Amber", Color(0xFFE5A952)),
    RED("Crimson", Color(0xFFE05A5A)),
    CYAN("Blue", Color(0xFF64B5F6)),
    GREEN("Green", Color(0xFF66BB6A))
}

enum class FrameGuide(val label: String, val aspectRatio: Float?) {
    NONE("Off", null),
    RULE_OF_THIRDS("3x3 Grid", null),
    OPEN_GATE_4_3("4:3 Open Gate", 4f / 3f),
    CINEMA_239("2.39:1 Scope", 2.39f),
    DCI_185("1.85:1 Flat", 1.85f),
    VERTICAL_9_16("9:16 Vertical", 9f / 16f)
}

data class LensInfo(
    val cameraId: String,
    val label: String,
    val focalLengthMm: Float,
    val equivalentMm: Int,
    val isFront: Boolean,
    val minZoom: Float,
    val maxZoom: Float,
    val supportsRaw: Boolean,
    val supportsOis: Boolean,
    val minIso: Int,
    val maxIso: Int,
    val minExposureNs: Long,
    val maxExposureNs: Long,
    val minFocusDistance: Float,
    val sensorDimensions: String,
    val hardwareLevel: String = "LIMITED",
    val supportsManualSensor: Boolean = false,
    val supportsTenBit: Boolean = false,
    val supportedStabModes: List<String> = emptyList(),
    val physicalModuleIds: List<String> = emptyList()
)

data class DeviceHardwareCapabilities(
    val lenses: List<LensInfo> = emptyList(),
    val nightExtensionAvailable: Boolean = false,
    val hdrExtensionAvailable: Boolean = false,
    val bokehExtensionAvailable: Boolean = false,
    val supportedVideoQualities: List<String> = emptyList(),
    val hlg10Supported: Boolean = false,
    val availableStorageBytes: Long = 0L,
    val totalStorageBytes: Long = 0L
)

data class AudioInputDeviceInfo(
    val id: Int,
    val name: String,
    val typeLabel: String,
    val isExternal: Boolean
)

data class AudioLevels(
    val peakDb: Float = -60f,
    val rmsDb: Float = -60f,
    val leftNormalized: Float = 0f,
    val rightNormalized: Float = 0f,
    val clipping: Boolean = false
)

data class HistogramData(
    val lumaBins: FloatArray = FloatArray(48),
    val redBins: FloatArray = FloatArray(48),
    val greenBins: FloatArray = FloatArray(48),
    val blueBins: FloatArray = FloatArray(48),
    val waveformColumns: FloatArray = FloatArray(48),
    val vectorPointsX: FloatArray = FloatArray(0),
    val vectorPointsY: FloatArray = FloatArray(0),
    val highlightClipPct: Float = 0f,
    val shadowCrushPct: Float = 0f,
    val avgLumaEv: Float = 0.5f
)

data class ExposureSettings(
    val program: ExposureProgram = ExposureProgram.AUTO,
    val isManualExposure: Boolean = false,
    val iso: Int = 200,
    val shutterSpeedNs: Long = 20_833_333L,
    val evCompensation: Int = 0,
    val evStep: Float = 0.333f,
    val evMinIndex: Int = -6,
    val evMaxIndex: Int = 6,
    val isAeAfLocked: Boolean = false,
    val isManualFocus: Boolean = false,
    val focusDistance: Float = 0f,
    val isAutoWhiteBalance: Boolean = true,
    val whiteBalanceKelvin: Int = 5600
)

enum class HudWidgetId(val title: String, val iconName: String) {
    HISTOGRAM("Scopes (Hist / WFM / Vect)", "bar_chart"),
    AUDIO_METER("Audio dBFS Meter", "graphic_eq"),
    STABILIZATION_CHIP("Stabilization Pill", "video_stable"),
    LOG_PROFILE_CHIP("Log / LUT Pill", "palette"),
    PEAKING_CHIP("Focus Peaking Pill", "center_focus_strong"),
    MAGNIFIER_CHIP("Focus Loupe Pill", "zoom_in"),
    FORMAT_BADGE("Codec / RAW Badge", "high_quality"),
    NIGHT_BOOST_CHIP("Night Boost Pill", "nights_stay"),
    GRID_GUIDE_CHIP("Frame Guides Pill", "grid_on"),
    FN_BUTTON_1("Fn1 Programmable", "looks_one"),
    FN_BUTTON_2("Fn2 Programmable", "looks_two"),
    FN_BUTTON_3("Fn3 Programmable", "looks_3")
}

enum class HudSlotZone(val label: String) {
    TOP_BAR("Top HUD Strip"),
    LEFT_DOCK("Left Edge Dock"),
    RIGHT_DOCK("Right Edge Dock"),
    HIDDEN("Hidden Drawer")
}

enum class AssignableAction(val label: String, val shortCode: String) {
    NONE("Unassigned", "---"),
    TOGGLE_HISTOGRAM("Cycle Exposure Scopes", "SCOPE"),
    TOGGLE_PEAKING("Toggle Focus Peaking", "PEAK"),
    TOGGLE_MAGNIFIER("Toggle 4x Loupe", "LOUPE"),
    CYCLE_STABILIZATION("Cycle Stabilization", "STAB"),
    TOGGLE_MONITOR_LUT("Toggle Monitor LUT", "LUT"),
    CYCLE_LOG_PROFILE("Cycle Log Profile", "LOG"),
    TOGGLE_NIGHT_BOOST("Toggle Night Boost", "NIGHT"),
    LOCK_AE_AF("Lock AE/AF", "LOCK"),
    SWITCH_PHOTO_VIDEO("Switch Photo / Video", "MODE"),
    TOGGLE_OPEN_GATE("Cycle Open Gate 4:3", "GATE"),
    TOGGLE_FRAME_GUIDE("Cycle Frame Guide", "GRID"),
    OPEN_COLOR_GRADE("Open Color Grading", "GRADE"),
    OPEN_AUDIO_PANEL("Open Audio Monitor", "AUDIO"),
    CAPTURE_TRIGGER("Trigger Capture / Rec", "REC")
}

data class WidgetPlacement(
    val widgetId: HudWidgetId,
    val zone: HudSlotZone,
    val order: Int
)

data class CustomLayoutState(
    val id: Long = 0L,
    val name: String = "Cinema Rig",
    val placements: List<WidgetPlacement> = defaultPlacements(),
    val fn1Action: AssignableAction = AssignableAction.TOGGLE_PEAKING,
    val fn2Action: AssignableAction = AssignableAction.TOGGLE_MONITOR_LUT,
    val fn3Action: AssignableAction = AssignableAction.CYCLE_STABILIZATION,
    val volumeUpAction: AssignableAction = AssignableAction.CAPTURE_TRIGGER,
    val volumeDownAction: AssignableAction = AssignableAction.TOGGLE_MAGNIFIER
) {
    companion object {
        fun defaultPlacements(): List<WidgetPlacement> = listOf(
            WidgetPlacement(HudWidgetId.FORMAT_BADGE, HudSlotZone.TOP_BAR, 0),
            WidgetPlacement(HudWidgetId.LOG_PROFILE_CHIP, HudSlotZone.TOP_BAR, 1),
            WidgetPlacement(HudWidgetId.STABILIZATION_CHIP, HudSlotZone.TOP_BAR, 2),
            WidgetPlacement(HudWidgetId.AUDIO_METER, HudSlotZone.TOP_BAR, 3),
            WidgetPlacement(HudWidgetId.HISTOGRAM, HudSlotZone.LEFT_DOCK, 0),
            WidgetPlacement(HudWidgetId.FN_BUTTON_1, HudSlotZone.RIGHT_DOCK, 0),
            WidgetPlacement(HudWidgetId.FN_BUTTON_2, HudSlotZone.RIGHT_DOCK, 1),
            WidgetPlacement(HudWidgetId.PEAKING_CHIP, HudSlotZone.HIDDEN, 0),
            WidgetPlacement(HudWidgetId.MAGNIFIER_CHIP, HudSlotZone.HIDDEN, 1),
            WidgetPlacement(HudWidgetId.NIGHT_BOOST_CHIP, HudSlotZone.HIDDEN, 2),
            WidgetPlacement(HudWidgetId.GRID_GUIDE_CHIP, HudSlotZone.HIDDEN, 3),
            WidgetPlacement(HudWidgetId.FN_BUTTON_3, HudSlotZone.HIDDEN, 4)
        )
    }
}
