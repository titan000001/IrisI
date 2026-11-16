package com.example.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.camera.view.PreviewView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.example.camera.AudioMonitorManager
import com.example.camera.CameraController
import com.example.camera.ColorEngine
import com.example.camera.EvBounds
import com.example.camera.FrameAnalyzer
import com.example.camera.HorizonLevelSensor
import com.example.camera.RawSequenceRecorder
import com.example.data.local.CameraPresetEntity
import com.example.data.local.CapturedMediaEntity
import com.example.data.local.CineRigDatabase
import com.example.data.local.CineRigRepository
import com.example.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

enum class ActiveSheet {
    NONE,
    COLOR_GRADE,
    VIDEO_STAB,
    AUDIO_MONITOR,
    UI_CUSTOMIZER,
    PRESETS,
    VAULT,
    DEVELOP,
    HARDWARE_INFO
}

enum class ManualControlTab {
    EV,
    ISO,
    SHUTTER,
    FOCUS,
    WB
}

data class CineRigUiState(
    val cameraGranted: Boolean = false,
    val audioGranted: Boolean = false,
    val captureMode: CaptureMode = CaptureMode.PHOTO,
    val photoFormat: PhotoFormat = PhotoFormat.RAW_PLUS_JPEG,
    val photoAspectRatio: PhotoAspectRatio = PhotoAspectRatio.RATIO_4_3,
    val flashMode: FlashMode = FlashMode.OFF,
    val selfTimer: SelfTimerOption = SelfTimerOption.OFF,
    val timerCountdownSec: Int = 0,
    val photoSequenceMode: PhotoSequenceMode = PhotoSequenceMode.SINGLE,
    val intervalActive: Boolean = false,
    val intervalShotCount: Int = 0,
    val nightStackStyle: NightStackStyle = NightStackStyle.LOW_LIGHT,
    val captureProgressLabel: String? = null,
    val videoCodecFormat: VideoCodecFormat = VideoCodecFormat.HEVC_LOG,
    val logProfile: LogProfile = LogProfile.REC_709,
    val filmPreset: FilmPreset = FilmPreset.PORTRA_400,
    val colorGrading: ColorGradingSettings = ColorGradingSettings(grainAmount = FilmPreset.PORTRA_400.defaultGrain),
    val videoResolution: VideoResolution = VideoResolution.UHD_4K,
    val videoFps: VideoFps = VideoFps.FPS_24,
    val videoBitrate: VideoBitrate = VideoBitrate.MBPS_100,
    val stabilizationMode: StabilizationMode = StabilizationMode.STANDARD,
    val exposure: ExposureSettings = ExposureSettings(),
    val evBounds: EvBounds = EvBounds(),
    val scopeMode: MonitorScopeMode = MonitorScopeMode.OFF,
    val histogramEnabled: Boolean = false,
    val zebraEnabled: Boolean = false,
    val zebraThreshold: Float = 0.92f,
    val falseColorEnabled: Boolean = false,
    val horizonLevelEnabled: Boolean = false,
    val horizonRollDeg: Float = 0f,
    val focusPeakingEnabled: Boolean = false,
    val peakingColor: PeakingColor = PeakingColor.AMBER,
    val peakingSensitivity: Float = 0.55f,
    val focusMagnifierEnabled: Boolean = false,
    val loupeZoomFactor: Float = 4f,
    val focusPointNormalized: Pair<Float, Float> = 0.5f to 0.5f,
    val frameGuide: FrameGuide = FrameGuide.NONE,
    val nightBoostEnabled: Boolean = false,
    val manualDeckExpanded: Boolean = false,
    val zoomSliderExpanded: Boolean = false,
    val shutterFlashTick: Int = 0,
    val activeManualTab: ManualControlTab = ManualControlTab.EV,
    val activeSheet: ActiveSheet = ActiveSheet.NONE,
    val isLayoutEditMode: Boolean = false,
    val lenses: List<LensInfo> = emptyList(),
    val selectedLens: LensInfo? = null,
    val zoomRatio: Float = 1f,
    val isRecording: Boolean = false,
    val isCapturing: Boolean = false,
    val isRecordingPaused: Boolean = false,
    val recordingDurationMs: Long = 0L,
    val recordedBytes: Long = 0L,
    val rawBurstFrameCount: Int = 0,
    val nightExtensionAvailable: Boolean = false,
    val hlg10Supported: Boolean = false,
    val deviceCapabilities: DeviceHardwareCapabilities = DeviceHardwareCapabilities(),
    val cameraError: String? = null,
    val cameraNotice: String? = null,
    val liveAnalysisAvailable: Boolean = false,
    val histogramData: HistogramData = HistogramData(),
    val peakingBitmap: Bitmap? = null,
    val loupeBitmap: Bitmap? = null,
    val zebraBitmap: Bitmap? = null,
    val falseColorBitmap: Bitmap? = null,
    val audioDevices: List<AudioInputDeviceInfo> = emptyList(),
    val selectedAudioDeviceId: Int? = null,
    val headphoneConnected: Boolean = false,
    val audioMonitoringEnabled: Boolean = false,
    val inputGainDb: Float = 0f,
    val audioLevels: AudioLevels = AudioLevels(),
    val layouts: List<CustomLayoutState> = emptyList(),
    val activeLayout: CustomLayoutState = CustomLayoutState(),
    val presets: List<CameraPresetEntity> = emptyList(),
    val mediaLogs: List<CapturedMediaEntity> = emptyList(),
    val developTarget: CapturedMediaEntity? = null,
    val developSettings: DevelopSettings = DevelopSettings(),
    val statusMessage: String? = null,
    val lastCapturedThumbUri: String? = null
)

class CineRigViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = CineRigDatabase.getInstance(application).cineRigDao()
    private val repository = CineRigRepository(application, dao)
    val cameraController = CameraController(application)
    val audioManager = AudioMonitorManager(application)
    private val horizonSensor = HorizonLevelSensor(application)

    private val _uiState = MutableStateFlow(CineRigUiState())
    val uiState: StateFlow<CineRigUiState> = _uiState.asStateFlow()
    private val discoveredMedia = MutableStateFlow<List<CapturedMediaEntity>>(emptyList())

    private var boundLifecycleOwner: LifecycleOwner? = null
    private var boundPreviewView: PreviewView? = null
    private var bindJob: Job? = null
    private var timerJob: Job? = null
    private var intervalJob: Job? = null
    private var lastPriorityBalanceMs = 0L

    val frameAnalyzer = FrameAnalyzer(
        scopeMode = { _uiState.value.scopeMode },
        isHistogramEnabled = {
            _uiState.value.histogramEnabled ||
                _uiState.value.scopeMode != MonitorScopeMode.OFF ||
                _uiState.value.activeLayout.placements.any { it.widgetId == HudWidgetId.HISTOGRAM && it.zone != HudSlotZone.HIDDEN }
        },
        isPeakingEnabled = { _uiState.value.focusPeakingEnabled },
        peakingColor = { _uiState.value.peakingColor },
        peakingSensitivity = { _uiState.value.peakingSensitivity },
        isLoupeEnabled = { _uiState.value.focusMagnifierEnabled },
        loupeZoomFactor = { _uiState.value.loupeZoomFactor },
        focusPointNormalized = { _uiState.value.focusPointNormalized },
        isZebraEnabled = { _uiState.value.zebraEnabled },
        zebraThresholdPct = { _uiState.value.zebraThreshold },
        isFalseColorEnabled = { _uiState.value.falseColorEnabled },
        isPriorityMeteringNeeded = {
            val prog = _uiState.value.exposure.program
            prog == ExposureProgram.SHUTTER_PRIORITY || prog == ExposureProgram.ISO_PRIORITY
        },
        onResult = { result ->
            _uiState.update {
                it.copy(
                    histogramData = result.histogram,
                    peakingBitmap = result.peakingBitmap,
                    loupeBitmap = result.loupeBitmap,
                    zebraBitmap = result.zebraBitmap,
                    falseColorBitmap = result.falseColorBitmap
                )
            }
            val exp = _uiState.value.exposure
            val now = System.currentTimeMillis()
            if ((exp.program == ExposureProgram.SHUTTER_PRIORITY || exp.program == ExposureProgram.ISO_PRIORITY) &&
                !exp.isAeAfLocked &&
                now - lastPriorityBalanceMs > 700L &&
                kotlin.math.abs(result.histogram.avgLumaEv - 0.5f) > 0.08f
            ) {
                lastPriorityBalanceMs = now
                val balanced = applyPriorityAutoBalance(exp)
                if (balanced != exp) {
                    _uiState.update { it.copy(exposure = balanced) }
                    cameraController.applyCamera2ManualControls(balanced, _uiState.value.stabilizationMode)
                }
            }
        }
    )

    init {
        viewModelScope.launch {
            repository.ensureSeedData()
        }
        observeStreams()
    }

    private fun observeStreams() {
        viewModelScope.launch {
            combine(cameraController.cameraNotice, cameraController.liveAnalysisAvailable) { notice, available ->
                notice to available
            }.collect { (notice, available) ->
                _uiState.update {
                    it.copy(cameraNotice = notice, liveAnalysisAvailable = available)
                }
            }
        }
        viewModelScope.launch {
            combine(
                cameraController.discoveredLenses,
                cameraController.selectedLens,
                cameraController.zoomRatio,
                cameraController.isRecording,
                cameraController.recordingDurationMs
            ) { lenses, selected, zoom, rec, dur ->
                Quint(lenses, selected, zoom, rec, dur)
            }.collect { (lenses, selected, zoom, rec, dur) ->
                _uiState.update {
                    it.copy(
                        lenses = lenses,
                        selectedLens = selected,
                        zoomRatio = zoom,
                        isRecording = rec,
                        recordingDurationMs = dur
                    )
                }
            }
        }

        viewModelScope.launch {
            combine(
                cameraController.isRecordingPaused,
                cameraController.recordedBytes,
                cameraController.evBounds,
                cameraController.capabilities
            ) { paused, bytes, evB, caps ->
                Quad(paused, bytes, evB, caps)
            }.collect { (paused, bytes, evB, caps) ->
                _uiState.update {
                    it.copy(
                        isRecordingPaused = paused,
                        recordedBytes = bytes,
                        evBounds = evB,
                        exposure = it.exposure.copy(
                            evMinIndex = evB.minIndex,
                            evMaxIndex = evB.maxIndex,
                            evStep = evB.stepEv
                        ),
                        deviceCapabilities = caps
                    )
                }
            }
        }

        viewModelScope.launch {
            combine(
                cameraController.rawBurstFrameCount,
                cameraController.nightExtensionAvailable,
                cameraController.hlg10Supported,
                cameraController.cameraError
            ) { burst, nightAvail, hlg, err ->
                Quad(burst, nightAvail, hlg, err)
            }.collect { (burst, nightAvail, hlg, err) ->
                _uiState.update {
                    it.copy(
                        rawBurstFrameCount = burst,
                        nightExtensionAvailable = nightAvail,
                        hlg10Supported = hlg,
                        cameraError = err
                    )
                }
            }
        }

        viewModelScope.launch {
            horizonSensor.rollDegrees.collect { deg ->
                if (_uiState.value.horizonLevelEnabled) {
                    _uiState.update { it.copy(horizonRollDeg = deg) }
                }
            }
        }

        viewModelScope.launch {
            combine(
                audioManager.inputDevices,
                audioManager.selectedDeviceId,
                audioManager.headphoneConnected,
                audioManager.monitoringEnabled,
                audioManager.inputGainDb
            ) { devices, selId, hp, mon, gain ->
                Quint(devices, selId, hp, mon, gain)
            }.collect { (devices, selId, hp, mon, gain) ->
                _uiState.update {
                    it.copy(
                        audioDevices = devices,
                        selectedAudioDeviceId = selId,
                        headphoneConnected = hp,
                        audioMonitoringEnabled = mon,
                        inputGainDb = gain
                    )
                }
            }
        }

        viewModelScope.launch {
            audioManager.audioLevels.collect { levels ->
                _uiState.update { it.copy(audioLevels = levels) }
            }
        }

        viewModelScope.launch {
            combine(
                repository.layouts,
                repository.activeLayoutId,
                repository.presets,
                repository.mediaLogs
            ) { layouts, activeId, presets, media ->
                val active = layouts.firstOrNull { it.id == activeId } ?: layouts.firstOrNull() ?: CustomLayoutState()
                Quad(layouts, active, presets, media)
            }.collect { (layouts, active, presets, media) ->
                _uiState.update {
                    it.copy(
                        layouts = layouts,
                        activeLayout = active,
                        presets = presets,
                        mediaLogs = mergeMediaLibrary(media),
                        lastCapturedThumbUri = mergeMediaLibrary(media).firstOrNull()?.uriString
                    )
                }
            }
        }
    }

    fun onPermissionsUpdated(cameraGranted: Boolean, audioGranted: Boolean) {
        val wasCamGranted = _uiState.value.cameraGranted
        _uiState.update { it.copy(cameraGranted = cameraGranted, audioGranted = audioGranted) }
        if (cameraGranted && (!wasCamGranted || _uiState.value.lenses.isEmpty())) {
            cameraController.discoverLenses()
            rebindSession()
        }
        audioManager.start(viewModelScope, audioGranted)
    }

    fun attachPreview(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        boundLifecycleOwner = lifecycleOwner
        boundPreviewView = previewView
        if (_uiState.value.cameraGranted) {
            if (_uiState.value.lenses.isEmpty()) {
                cameraController.discoverLenses()
            }
            rebindSession()
        }
    }

    private fun rebindSession() {
        if (_uiState.value.isRecording || _uiState.value.isCapturing) return
        val owner = boundLifecycleOwner ?: return
        val pv = boundPreviewView ?: return
        if (!_uiState.value.cameraGranted) return

        bindJob?.cancel()
        bindJob = viewModelScope.launch {
            delay(35L)
            val state = _uiState.value
            if (state.isRecording || state.isCapturing) return@launch
            cameraController.bindCameraSession(
                lifecycleOwner = owner,
                previewView = pv,
                frameAnalyzer = frameAnalyzer,
                captureMode = state.captureMode,
                photoFormat = state.photoFormat,
                photoAspectRatio = state.photoAspectRatio,
                flashMode = state.flashMode,
                videoCodecFormat = state.videoCodecFormat,
                videoResolution = state.videoResolution,
                videoFps = state.videoFps,
                videoBitrate = state.videoBitrate,
                stabilizationMode = state.stabilizationMode,
                exposure = state.exposure,
                nightBoostEnabled = state.nightBoostEnabled
            )
        }
    }

    fun setCaptureMode(mode: CaptureMode) {
        if (_uiState.value.isRecording || _uiState.value.isCapturing) return
        stopIntervalometerIfRunning()
        val nextCodec = if (mode == CaptureMode.RAW_VIDEO) VideoCodecFormat.RAW_CINEMA_DNG
            else if (_uiState.value.videoCodecFormat == VideoCodecFormat.RAW_CINEMA_DNG) VideoCodecFormat.HEVC_LOG
            else _uiState.value.videoCodecFormat
        val nextNight = mode == CaptureMode.NIGHT
        _uiState.update {
            it.copy(
                captureMode = mode,
                videoCodecFormat = nextCodec,
                nightBoostEnabled = nextNight
            )
        }
        rebindSession()
    }

    fun setExposureProgram(program: ExposureProgram) {
        val manual = program == ExposureProgram.MANUAL ||
            program == ExposureProgram.SHUTTER_PRIORITY ||
            program == ExposureProgram.ISO_PRIORITY
        val nextTab = when (program) {
            ExposureProgram.AUTO, ExposureProgram.PROGRAM -> ManualControlTab.EV
            ExposureProgram.SHUTTER_PRIORITY -> ManualControlTab.SHUTTER
            ExposureProgram.ISO_PRIORITY -> ManualControlTab.ISO
            ExposureProgram.MANUAL -> ManualControlTab.ISO
        }
        updateExposure {
            val adjusted = applyPriorityAutoBalance(it.copy(program = program, isManualExposure = manual))
            adjusted
        }
        _uiState.update { it.copy(activeManualTab = nextTab, manualDeckExpanded = program != ExposureProgram.AUTO) }
    }

    private fun applyPriorityAutoBalance(exp: ExposureSettings): ExposureSettings {
        val lens = _uiState.value.selectedLens
        val luma = _uiState.value.histogramData.avgLumaEv.coerceIn(0.08f, 0.92f)
        val lumaScale = (0.5f / luma).coerceIn(0.35f, 3.0f)
        return when (exp.program) {
            ExposureProgram.SHUTTER_PRIORITY -> {
                val baseIso = ((20_833_333.0 / exp.shutterSpeedNs.coerceAtLeast(100_000L)) * 200.0 * lumaScale)
                    .roundToInt()
                    .coerceIn(lens?.minIso ?: 50, lens?.maxIso ?: 6400)
                val snapped = (baseIso / 25).coerceAtLeast(2) * 25
                exp.copy(isManualExposure = true, iso = snapped)
            }
            ExposureProgram.ISO_PRIORITY -> {
                val targetNs = ((200.0 / exp.iso.coerceAtLeast(25)) * 20_833_333.0 * lumaScale)
                    .toLong()
                    .coerceIn(lens?.minExposureNs ?: 100_000L, lens?.maxExposureNs ?: 500_000_000L)
                exp.copy(isManualExposure = true, shutterSpeedNs = targetNs)
            }
            else -> exp
        }
    }

    fun setPhotoFormat(format: PhotoFormat) {
        if (_uiState.value.isRecording || _uiState.value.isCapturing) return
        _uiState.update { it.copy(photoFormat = format) }
        rebindSession()
    }

    fun cyclePhotoAspectRatio() {
        if (_uiState.value.isRecording || _uiState.value.isCapturing) return
        val all = PhotoAspectRatio.entries
        val next = all[(all.indexOf(_uiState.value.photoAspectRatio) + 1) % all.size]
        _uiState.update { it.copy(photoAspectRatio = next, statusMessage = "Aspect Ratio: ${next.label}") }
        rebindSession()
    }

    fun cycleFlashMode() {
        val all = FlashMode.entries
        val next = all[(all.indexOf(_uiState.value.flashMode) + 1) % all.size]
        _uiState.update { it.copy(flashMode = next, statusMessage = "Flash: ${next.label}") }
        cameraController.applyFlashMode(next)
    }

    fun cycleSelfTimer() {
        val all = SelfTimerOption.entries
        val next = all[(all.indexOf(_uiState.value.selfTimer) + 1) % all.size]
        _uiState.update { it.copy(selfTimer = next, statusMessage = "Self-Timer: ${next.label}") }
    }

    fun setPhotoSequenceMode(seq: PhotoSequenceMode) {
        stopIntervalometerIfRunning()
        _uiState.update {
            it.copy(
                photoSequenceMode = seq,
                statusMessage = "Sequence: ${seq.label}"
            )
        }
    }

    fun setNightStackStyle(style: NightStackStyle) {
        _uiState.update { it.copy(nightStackStyle = style, statusMessage = "${style.label} (${style.frames} frames)") }
    }

    fun setVideoCodecFormat(codec: VideoCodecFormat) {
        if (_uiState.value.isRecording || _uiState.value.isCapturing) return
        val mode = if (codec == VideoCodecFormat.RAW_CINEMA_DNG) CaptureMode.RAW_VIDEO else CaptureMode.VIDEO
        _uiState.update { it.copy(videoCodecFormat = codec, captureMode = mode) }
        rebindSession()
    }

    fun setVideoResolution(res: VideoResolution) {
        if (_uiState.value.isRecording || _uiState.value.isCapturing) return
        _uiState.update { it.copy(videoResolution = res) }
        rebindSession()
    }

    fun setVideoFps(fps: VideoFps) {
        if (_uiState.value.isRecording || _uiState.value.isCapturing) return
        _uiState.update { it.copy(videoFps = fps) }
        rebindSession()
    }

    fun setVideoBitrate(bitrate: VideoBitrate) {
        if (_uiState.value.isRecording || _uiState.value.isCapturing) return
        _uiState.update { it.copy(videoBitrate = bitrate) }
        rebindSession()
    }

    fun setStabilizationMode(mode: StabilizationMode) {
        if (_uiState.value.isRecording || _uiState.value.isCapturing) return
        val updatedExposure = if (mode == StabilizationMode.ACTION && _uiState.value.exposure.isManualExposure) {
            val fastFloor = 8_333_333L
            if (_uiState.value.exposure.shutterSpeedNs > fastFloor) {
                _uiState.value.exposure.copy(shutterSpeedNs = fastFloor)
            } else {
                _uiState.value.exposure
            }
        } else {
            _uiState.value.exposure
        }
        _uiState.update {
            it.copy(
                stabilizationMode = mode,
                exposure = updatedExposure,
                statusMessage = "Stabilization: ${mode.displayName} (${mode.cropFactor})"
            )
        }
        rebindSession()
    }

    fun selectLens(lens: LensInfo) {
        if (_uiState.value.isRecording || _uiState.value.isCapturing) return
        cameraController.selectLens(lens)
        rebindSession()
    }

    fun setZoomRatio(ratio: Float) {
        cameraController.applyZoom(ratio)
    }

    fun tapToFocus(nx: Float, ny: Float, w: Float, h: Float) {
        _uiState.update { it.copy(focusPointNormalized = nx to ny) }
        cameraController.tapToFocus(nx, ny, w, h)
    }

    fun updateExposure(transform: (ExposureSettings) -> ExposureSettings) {
        val raw = transform(_uiState.value.exposure)
        val balanced = if (raw.program == ExposureProgram.SHUTTER_PRIORITY || raw.program == ExposureProgram.ISO_PRIORITY) {
            applyPriorityAutoBalance(raw)
        } else {
            raw
        }
        _uiState.update { it.copy(exposure = balanced) }
        cameraController.applyCamera2ManualControls(balanced, _uiState.value.stabilizationMode)
    }

    fun setLogProfile(profile: LogProfile) {
        _uiState.update { it.copy(logProfile = profile) }
    }

    fun setFilmPreset(preset: FilmPreset) {
        _uiState.update {
            it.copy(
                filmPreset = preset,
                colorGrading = it.colorGrading.copy(grainAmount = preset.defaultGrain)
            )
        }
    }

    fun updateColorGrading(transform: (ColorGradingSettings) -> ColorGradingSettings) {
        _uiState.update { it.copy(colorGrading = transform(it.colorGrading)) }
    }

    fun cycleScopeMode() {
        val modes = MonitorScopeMode.entries
        val next = modes[(modes.indexOf(_uiState.value.scopeMode) + 1) % modes.size]
        _uiState.update {
            it.copy(
                scopeMode = next,
                histogramEnabled = next != MonitorScopeMode.OFF,
                statusMessage = "Scope: ${next.label}"
            )
        }
    }

    fun setScopeMode(mode: MonitorScopeMode) {
        _uiState.update {
            it.copy(
                scopeMode = mode,
                histogramEnabled = mode != MonitorScopeMode.OFF
            )
        }
    }

    fun toggleHistogram() {
        val next = if (_uiState.value.scopeMode == MonitorScopeMode.OFF) MonitorScopeMode.HISTOGRAM else MonitorScopeMode.OFF
        setScopeMode(next)
    }

    fun toggleZebra() {
        _uiState.update {
            val next = !it.zebraEnabled
            it.copy(zebraEnabled = next, zebraBitmap = if (next) it.zebraBitmap else null)
        }
    }

    fun toggleFalseColor() {
        _uiState.update {
            val next = !it.falseColorEnabled
            it.copy(falseColorEnabled = next, falseColorBitmap = if (next) it.falseColorBitmap else null)
        }
    }

    fun toggleHorizonLevel() {
        val next = !_uiState.value.horizonLevelEnabled
        horizonSensor.setEnabled(next)
        _uiState.update { it.copy(horizonLevelEnabled = next) }
    }

    fun toggleFocusPeaking() {
        _uiState.update {
            val next = !it.focusPeakingEnabled
            it.copy(
                focusPeakingEnabled = next,
                peakingBitmap = if (next) it.peakingBitmap else null
            )
        }
    }

    fun setPeakingColor(color: PeakingColor) {
        _uiState.update { it.copy(peakingColor = color) }
    }

    fun setPeakingSensitivity(value: Float) {
        _uiState.update { it.copy(peakingSensitivity = value.coerceIn(0.1f, 0.95f)) }
    }

    fun toggleFocusMagnifier() {
        _uiState.update {
            val next = !it.focusMagnifierEnabled
            it.copy(
                focusMagnifierEnabled = next,
                loupeBitmap = if (next) it.loupeBitmap else null
            )
        }
    }

    fun cycleLoupeZoom() {
        _uiState.update {
            val next = when (it.loupeZoomFactor.roundToInt()) {
                2 -> 4f
                4 -> 6f
                else -> 2f
            }
            it.copy(loupeZoomFactor = next)
        }
    }

    fun cycleFrameGuide() {
        val entries = FrameGuide.entries
        val next = entries[(entries.indexOf(_uiState.value.frameGuide) + 1) % entries.size]
        _uiState.update { it.copy(frameGuide = next) }
    }

    fun toggleNightBoost() {
        if (_uiState.value.isRecording || _uiState.value.isCapturing) return
        _uiState.update { it.copy(nightBoostEnabled = !it.nightBoostEnabled) }
        rebindSession()
    }

    fun toggleManualDeck() {
        _uiState.update { it.copy(manualDeckExpanded = !it.manualDeckExpanded) }
    }

    fun toggleZoomSlider() {
        _uiState.update { it.copy(zoomSliderExpanded = !it.zoomSliderExpanded) }
    }

    fun cyclePhotoFormat() {
        val formats = PhotoFormat.entries
        val next = formats[(formats.indexOf(_uiState.value.photoFormat) + 1) % formats.size]
        setPhotoFormat(next)
        _uiState.update { it.copy(statusMessage = "Format: ${next.label}") }
    }

    fun selectManualTab(tab: ManualControlTab) {
        _uiState.update { it.copy(activeManualTab = tab, manualDeckExpanded = true) }
    }

    fun openSheet(sheet: ActiveSheet) {
        if (sheet == ActiveSheet.HARDWARE_INFO || sheet == ActiveSheet.VAULT) {
            cameraController.refreshStorageAndCapabilities()
        }
        _uiState.update { it.copy(activeSheet = sheet) }
        if (sheet == ActiveSheet.VAULT) {
            viewModelScope.launch {
                try {
                    discoveredMedia.value = repository.discoverOwnedMedia()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    _uiState.update { state -> state.copy(statusMessage = "Could not refresh local media") }
                }
                val current = repository.mediaLogs.firstOrNull()
                if (current != null) _uiState.update {
                    it.copy(mediaLogs = mergeMediaLibrary(current))
                }
            }
        }
    }

    private fun mergeMediaLibrary(logs: List<CapturedMediaEntity>): List<CapturedMediaEntity> {
        val loggedUris = logs.mapTo(HashSet()) { it.uriString }
        return (logs + discoveredMedia.value.filter { it.uriString !in loggedUris })
            .sortedByDescending { it.timestamp }
    }

    fun closeSheet() {
        _uiState.update { it.copy(activeSheet = ActiveSheet.NONE) }
    }

    fun toggleLayoutEditMode() {
        _uiState.update { it.copy(isLayoutEditMode = !it.isLayoutEditMode) }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    fun pauseOrResumeRecording() {
        cameraController.pauseOrResumeRecording()
    }

    private fun stopIntervalometerIfRunning() {
        intervalJob?.cancel()
        intervalJob = null
        if (_uiState.value.intervalActive) {
            _uiState.update { it.copy(intervalActive = false, intervalShotCount = 0) }
        }
    }

    fun triggerCapture() {
        val state = _uiState.value
        if (!state.cameraGranted) {
            _uiState.update { it.copy(statusMessage = "Camera permission required") }
            return
        }

        // Cancel active self-timer or intervalometer on second tap
        if (state.timerCountdownSec > 0) {
            timerJob?.cancel()
            timerJob = null
            _uiState.update { it.copy(timerCountdownSec = 0) }
            return
        }
        if (state.intervalActive) {
            stopIntervalometerIfRunning()
            _uiState.update { it.copy(statusMessage = "Intervalometer stopped") }
            return
        }

        if (state.captureMode == CaptureMode.RAW_VIDEO) {
            bindJob?.cancel()
            frameAnalyzer.clear()
            _uiState.update { it.copy(isCapturing = true, statusMessage = if (!state.isCapturing) "Preparing DNG sequence…" else "Stopping DNG sequence…") }
            cameraController.toggleRawSequenceRecording(
                config = RawSequenceRecorder.Config(
                    width = state.videoResolution.width,
                    height = state.videoResolution.height,
                    fps = state.videoFps.fps.coerceIn(1, 10),
                    durationMs = 10_000,
                    maxFrames = 60
                ),
                onStarted = { _uiState.update { it.copy(statusMessage = "Capturing sequential DNG frames…") } },
                onComplete = { completion -> logMediaToVault(completion) },
                onError = { err -> _uiState.update { it.copy(statusMessage = err) } },
                onFinished = {
                    _uiState.update { it.copy(isCapturing = false) }
                    rebindSession()
                }
            )
            return
        }
        if (state.isCapturing) return

        if (state.captureMode == CaptureMode.VIDEO || state.captureMode == CaptureMode.RAW_VIDEO) {
            cameraController.toggleVideoRecording(
                scope = viewModelScope,
                audioGranted = state.audioGranted,
                videoCodecFormat = state.videoCodecFormat,
                videoBitrate = state.videoBitrate,
                onRecordingStateChanged = audioManager::setPausedForVideoRecording,
                onAudioAmplitude = audioManager::updateLevelsFromRecordingAmplitude,
                onComplete = { completion -> logMediaToVault(completion) },
                onError = { err -> _uiState.update { it.copy(statusMessage = err) } }
            )
            return
        }

        val delaySec = state.selfTimer.seconds
        if (delaySec > 0) {
            timerJob?.cancel()
            timerJob = viewModelScope.launch {
                for (rem in delaySec downTo 1) {
                    _uiState.update { it.copy(timerCountdownSec = rem) }
                    delay(1000L)
                }
                _uiState.update { it.copy(timerCountdownSec = 0) }
                executePhotoCapture()
            }
        } else if (state.captureMode == CaptureMode.PHOTO && state.photoSequenceMode == PhotoSequenceMode.INTERVAL) {
            intervalJob?.cancel()
            intervalJob = viewModelScope.launch {
                _uiState.update { it.copy(intervalActive = true, intervalShotCount = 0) }
                while (isActive) {
                    _uiState.update { it.copy(intervalShotCount = it.intervalShotCount + 1) }
                    val shotDone = CompletableDeferred<Unit>()
                    executePhotoCapture { shotDone.complete(Unit) }
                    shotDone.await()
                    if (!isActive) break
                    delay(3000L)
                }
            }
        } else {
            executePhotoCapture()
        }
    }

    private fun executePhotoCapture(onFinished: () -> Unit = {}) {
        val state = _uiState.value
        _uiState.update { it.copy(shutterFlashTick = it.shutterFlashTick + 1, isCapturing = true) }
        cameraController.capturePhoto(
            scope = viewModelScope,
            photoFormat = state.photoFormat,
            photoAspectRatio = state.photoAspectRatio,
            sequenceMode = if (state.captureMode == CaptureMode.NIGHT) PhotoSequenceMode.SINGLE else state.photoSequenceMode,
            captureMode = state.captureMode,
            nightStackStyle = state.nightStackStyle,
            exposure = state.exposure,
            stabilizationMode = state.stabilizationMode,
            // The previous code always passed Rec.709, so selecting a profile in the
            // color workbench changed the monitor but silently left processed stills unchanged.
            // Pass the selected profile through the complete still-image processing path.
            logProfile = state.logProfile,
            filmPreset = state.filmPreset,
            grading = state.colorGrading,
            onProgress = { _, _, label ->
                _uiState.update { it.copy(captureProgressLabel = label.ifBlank { null }) }
            },
            onComplete = { completion ->
                _uiState.update { it.copy(captureProgressLabel = null, isCapturing = false) }
                logMediaToVault(completion)
            },
            onError = { err ->
                _uiState.update { it.copy(captureProgressLabel = null, isCapturing = false, statusMessage = err) }
            },
            onFinished = onFinished
        )
    }

    private fun logMediaToVault(completion: com.example.camera.CaptureCompletion) {
        val state = _uiState.value
        val entity = CapturedMediaEntity(
            uriString = completion.uriString,
            displayName = completion.displayName,
            isVideo = completion.isVideo,
            formatBadge = completion.formatBadge,
            resolutionLabel = if (completion.isVideo) state.videoResolution.shortLabel else state.photoAspectRatio.label,
            lensLabel = state.selectedLens?.label ?: "1x",
            iso = state.exposure.iso,
            shutterLabel = formatShutterSpeed(state.exposure.shutterSpeedNs),
            logProfile = if (completion.isVideo) state.logProfile.shortName else "Rec.709",
            filmPreset = state.filmPreset.shortCode,
            stabilizationMode = state.stabilizationMode.shortCode,
            durationMs = completion.durationMs,
            fileBytes = completion.fileBytes
        )
        viewModelScope.launch {
            repository.logCapturedMedia(entity)
        }
        _uiState.update {
            it.copy(
                statusMessage = "Saved ${completion.displayName}",
                lastCapturedThumbUri = completion.uriString
            )
        }
    }

    // Media Library & Non-Destructive RAW/Photo Develop Lab
    fun toggleMediaFavorite(item: CapturedMediaEntity) {
        if (item.id < 0L) return
        viewModelScope.launch {
            repository.setMediaFavorite(item.id, !item.isFavorite)
        }
    }

    fun openDevelopForMedia(item: CapturedMediaEntity) {
        val preset = enumOrDefault(item.filmPreset, FilmPreset.NONE)
        _uiState.update {
            it.copy(
                developTarget = item,
                developSettings = DevelopSettings(filmPreset = preset),
                activeSheet = ActiveSheet.DEVELOP
            )
        }
    }

    fun updateDevelopSettings(transform: (DevelopSettings) -> DevelopSettings) {
        _uiState.update { it.copy(developSettings = transform(it.developSettings)) }
    }

    fun exportDevelopedMedia() {
        val target = _uiState.value.developTarget ?: return
        val dev = _uiState.value.developSettings
        if (target.uriString.isBlank()) {
            _uiState.update { it.copy(statusMessage = "Source URI unavailable") }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                val uri = Uri.parse(target.uriString)
                val decoded = ColorEngine.decodeBitmapForDevelop(
                    getApplication<Application>().contentResolver,
                    uri,
                    sampleSize = 1
                ) ?: throw IllegalStateException("Cannot decode source image/DNG")
                val developed = ColorEngine.applyDevelopToBitmap(decoded, dev)
                decoded.recycle()
                val outName = target.displayName.substringBeforeLast('.') + "_DEV.jpg"
                val saved = cameraController.saveBitmapToMediaStore(developed, outName)
                    ?: throw IllegalStateException("MediaStore export failed")
                developed.recycle()
                outName to saved
            }
            withContext(Dispatchers.Main) {
                result.onSuccess { (outName, saved) ->
                    val newLog = target.copy(
                        id = 0,
                        uriString = saved.first.toString(),
                        displayName = outName,
                        formatBadge = "DEV JPG",
                        pairedUriString = target.uriString,
                        fileBytes = saved.second,
                        timestamp = System.currentTimeMillis()
                    )
                    viewModelScope.launch { repository.logCapturedMedia(newLog) }
                    _uiState.update {
                        it.copy(
                            statusMessage = "Exported non-destructive copy: $outName",
                            activeSheet = ActiveSheet.VAULT
                        )
                    }
                }.onFailure { err ->
                    _uiState.update { it.copy(statusMessage = err.localizedMessage ?: "Develop export failed") }
                }
            }
        }
    }

    fun dispatchAssignableAction(action: AssignableAction) {
        when (action) {
            AssignableAction.NONE -> Unit
            AssignableAction.TOGGLE_HISTOGRAM -> cycleScopeMode()
            AssignableAction.TOGGLE_PEAKING -> toggleFocusPeaking()
            AssignableAction.TOGGLE_MAGNIFIER -> toggleFocusMagnifier()
            AssignableAction.CYCLE_STABILIZATION -> {
                val modes = StabilizationMode.entries
                val next = modes[(modes.indexOf(_uiState.value.stabilizationMode) + 1) % modes.size]
                setStabilizationMode(next)
            }
            AssignableAction.TOGGLE_MONITOR_LUT -> {
                updateColorGrading { it.copy(monitoringLutEnabled = !it.monitoringLutEnabled) }
            }
            AssignableAction.CYCLE_LOG_PROFILE -> {
                val profiles = LogProfile.entries
                val next = profiles[(profiles.indexOf(_uiState.value.logProfile) + 1) % profiles.size]
                setLogProfile(next)
            }
            AssignableAction.TOGGLE_NIGHT_BOOST -> toggleNightBoost()
            AssignableAction.LOCK_AE_AF -> updateExposure { it.copy(isAeAfLocked = !it.isAeAfLocked) }
            AssignableAction.SWITCH_PHOTO_VIDEO -> {
                val next = if (_uiState.value.captureMode == CaptureMode.PHOTO) CaptureMode.VIDEO else CaptureMode.PHOTO
                setCaptureMode(next)
            }
            AssignableAction.TOGGLE_OPEN_GATE -> {
                val next = when (_uiState.value.videoResolution) {
                    VideoResolution.OPEN_GATE_4_3 -> VideoResolution.OPEN_GATE_HALF
                    VideoResolution.OPEN_GATE_HALF -> VideoResolution.UHD_4K
                    else -> VideoResolution.OPEN_GATE_4_3
                }
                setVideoResolution(next)
                _uiState.update { it.copy(statusMessage = next.label) }
            }
            AssignableAction.TOGGLE_FRAME_GUIDE -> cycleFrameGuide()
            AssignableAction.OPEN_COLOR_GRADE -> openSheet(ActiveSheet.COLOR_GRADE)
            AssignableAction.OPEN_AUDIO_PANEL -> openSheet(ActiveSheet.AUDIO_MONITOR)
            AssignableAction.CAPTURE_TRIGGER -> triggerCapture()
        }
    }

    fun handleVolumeKey(isVolumeUp: Boolean): Boolean {
        val layout = _uiState.value.activeLayout
        val action = if (isVolumeUp) layout.volumeUpAction else layout.volumeDownAction
        if (action == AssignableAction.NONE) return false
        dispatchAssignableAction(action)
        return true
    }

    fun moveWidgetToZone(widgetId: HudWidgetId, targetZone: HudSlotZone) {
        val current = _uiState.value.activeLayout
        val maxOrder = current.placements.filter { it.zone == targetZone }.maxOfOrNull { it.order } ?: -1
        val updated = current.placements.map {
            if (it.widgetId == widgetId) it.copy(zone = targetZone, order = maxOrder + 1) else it
        }
        saveUpdatedActiveLayout(current.copy(placements = updated))
    }

    fun reorderWidgetInZone(widgetId: HudWidgetId, moveEarlier: Boolean) {
        val current = _uiState.value.activeLayout
        val target = current.placements.firstOrNull { it.widgetId == widgetId } ?: return
        val siblings = current.placements.filter { it.zone == target.zone }.sortedBy { it.order }.toMutableList()
        val idx = siblings.indexOfFirst { it.widgetId == widgetId }
        val swapIdx = if (moveEarlier) idx - 1 else idx + 1
        if (idx !in siblings.indices || swapIdx !in siblings.indices) return
        val temp = siblings[idx]
        siblings[idx] = siblings[swapIdx]
        siblings[swapIdx] = temp
        val reindexed = siblings.mapIndexed { i, item -> item.copy(order = i) }
        val merged = current.placements.map { old ->
            reindexed.firstOrNull { it.widgetId == old.widgetId } ?: old
        }
        saveUpdatedActiveLayout(current.copy(placements = merged))
    }

    fun updateLayoutButtonBindings(
        fn1: AssignableAction? = null,
        fn2: AssignableAction? = null,
        fn3: AssignableAction? = null,
        volUp: AssignableAction? = null,
        volDown: AssignableAction? = null
    ) {
        val current = _uiState.value.activeLayout
        saveUpdatedActiveLayout(
            current.copy(
                fn1Action = fn1 ?: current.fn1Action,
                fn2Action = fn2 ?: current.fn2Action,
                fn3Action = fn3 ?: current.fn3Action,
                volumeUpAction = volUp ?: current.volumeUpAction,
                volumeDownAction = volDown ?: current.volumeDownAction
            )
        )
    }

    private fun saveUpdatedActiveLayout(layout: CustomLayoutState) {
        _uiState.update { it.copy(activeLayout = layout) }
        viewModelScope.launch {
            repository.saveLayout(layout)
        }
    }

    fun selectLayout(layoutId: Long) {
        viewModelScope.launch {
            repository.setActiveLayoutId(layoutId)
        }
    }

    fun saveAsNewLayout(name: String) {
        val trimmed = name.trim().ifBlank { "Custom Rig" }
        val newLayout = _uiState.value.activeLayout.copy(id = 0L, name = trimmed)
        viewModelScope.launch {
            repository.saveLayout(newLayout)
        }
        _uiState.update { it.copy(statusMessage = "Saved layout \"$trimmed\"") }
    }

    fun deleteLayout(id: Long) {
        if (_uiState.value.layouts.size <= 1) return
        viewModelScope.launch {
            repository.deleteLayout(id)
        }
    }

    fun saveCurrentAsPreset(name: String, description: String) {
        val s = _uiState.value
        val entity = CameraPresetEntity(
            name = name.trim().ifBlank { "Custom Look" },
            description = description.trim().ifBlank { "${s.logProfile.shortName} • ${s.filmPreset.displayName}" },
            captureMode = s.captureMode.name,
            photoFormat = s.photoFormat.name,
            videoCodecFormat = s.videoCodecFormat.name,
            logProfile = s.logProfile.name,
            filmPreset = s.filmPreset.name,
            videoResolution = s.videoResolution.name,
            videoFps = s.videoFps.name,
            videoBitrate = s.videoBitrate.name,
            stabilizationMode = s.stabilizationMode.name,
            isManualExposure = s.exposure.isManualExposure,
            iso = s.exposure.iso,
            shutterSpeedNs = s.exposure.shutterSpeedNs,
            isAutoWb = s.exposure.isAutoWhiteBalance,
            wbKelvin = s.exposure.whiteBalanceKelvin,
            lift = s.colorGrading.lift,
            gamma = s.colorGrading.gamma,
            gain = s.colorGrading.gain,
            contrast = s.colorGrading.contrast,
            saturation = s.colorGrading.saturation,
            temperatureOffset = s.colorGrading.temperatureOffset,
            tintOffset = s.colorGrading.tintOffset,
            vignette = s.colorGrading.vignette,
            grainAmount = s.colorGrading.grainAmount,
            grainSize = s.colorGrading.grainSize,
            monitoringLutEnabled = s.colorGrading.monitoringLutEnabled
        )
        viewModelScope.launch {
            repository.savePreset(entity)
        }
        _uiState.update { it.copy(statusMessage = "Saved preset \"${entity.name}\"") }
    }

    fun applyPreset(preset: CameraPresetEntity) {
        if (_uiState.value.isRecording || _uiState.value.isCapturing) return
        _uiState.update { state ->
            state.copy(
                captureMode = enumOrDefault(preset.captureMode, state.captureMode),
                photoFormat = enumOrDefault(preset.photoFormat, state.photoFormat),
                videoCodecFormat = enumOrDefault(preset.videoCodecFormat, state.videoCodecFormat),
                logProfile = enumOrDefault(preset.logProfile, state.logProfile),
                filmPreset = enumOrDefault(preset.filmPreset, state.filmPreset),
                videoResolution = enumOrDefault(preset.videoResolution, state.videoResolution),
                videoFps = enumOrDefault(preset.videoFps, state.videoFps),
                videoBitrate = enumOrDefault(preset.videoBitrate, state.videoBitrate),
                stabilizationMode = enumOrDefault(preset.stabilizationMode, state.stabilizationMode),
                exposure = state.exposure.copy(
                    isManualExposure = preset.isManualExposure,
                    iso = preset.iso,
                    shutterSpeedNs = preset.shutterSpeedNs,
                    isAutoWhiteBalance = preset.isAutoWb,
                    whiteBalanceKelvin = preset.wbKelvin
                ),
                colorGrading = ColorGradingSettings(
                    lift = preset.lift,
                    gamma = preset.gamma,
                    gain = preset.gain,
                    contrast = preset.contrast,
                    saturation = preset.saturation,
                    temperatureOffset = preset.temperatureOffset,
                    tintOffset = preset.tintOffset,
                    vignette = preset.vignette,
                    grainAmount = preset.grainAmount,
                    grainSize = preset.grainSize,
                    monitoringLutEnabled = preset.monitoringLutEnabled
                ),
                statusMessage = "Loaded preset \"${preset.name}\""
            )
        }
        rebindSession()
    }

    fun deletePreset(id: Long) {
        viewModelScope.launch { repository.deletePreset(id) }
    }

    fun deleteMediaLog(id: Long) {
        viewModelScope.launch {
            if (id < 0L) {
                val item = discoveredMedia.value.firstOrNull { it.id == id } ?: return@launch
                if (repository.deleteOwnedMedia(item.uriString)) {
                    discoveredMedia.value = discoveredMedia.value.filterNot { it.id == id }
                    val logs = repository.mediaLogs.firstOrNull().orEmpty()
                    _uiState.update { it.copy(mediaLogs = mergeMediaLibrary(logs), statusMessage = "Deleted ${item.displayName}") }
                } else {
                    _uiState.update { it.copy(statusMessage = "Could not delete ${item.displayName}") }
                }
            } else {
                val item = _uiState.value.mediaLogs.firstOrNull { it.id == id }
                val deleted = item?.let { repository.deleteOwnedMedia(it.uriString) } == true
                if (deleted) {
                    repository.deleteMediaLog(id)
                    discoveredMedia.value = discoveredMedia.value.filterNot { it.uriString == item?.uriString }
                    val logs = repository.mediaLogs.firstOrNull().orEmpty()
                    _uiState.update { it.copy(mediaLogs = mergeMediaLibrary(logs), statusMessage = "Deleted ${item?.displayName}") }
                } else {
                    // Explicitly retain the file if its ownership/provider identity cannot be
                    // verified; merely hiding an item would make it reappear on the next scan.
                    _uiState.update { it.copy(statusMessage = "File could not be verified for deletion") }
                }
            }
        }
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String, default: T): T =
        runCatching { enumValueOf<T>(name) }.getOrDefault(default)

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        intervalJob?.cancel()
        horizonSensor.release()
        cameraController.release()
        audioManager.release()
    }

    companion object {
        fun formatShutterSpeed(ns: Long): String {
            if (ns <= 0L) return "AUTO"
            val sec = ns / 1_000_000_000.0
            return if (sec >= 0.4) {
                String.format(Locale.US, "%.1f\"", sec)
            } else {
                val denom = (1.0 / sec).roundToInt().coerceAtLeast(1)
                "1/$denom"
            }
        }

        fun formatDuration(ms: Long): String {
            val totalSec = ms / 1000L
            val h = totalSec / 3600L
            val m = (totalSec % 3600L) / 60L
            val s = totalSec % 60L
            return if (h > 0) {
                String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
            } else {
                String.format(Locale.US, "%02d:%02d", m, s)
            }
        }

        fun formatBytes(bytes: Long): String {
            return when {
                bytes >= 1_073_741_824L -> String.format(Locale.US, "%.1f GB", bytes / 1_073_741_824.0)
                bytes >= 1_048_576L -> String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)
                bytes >= 1024L -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
                else -> "$bytes B"
            }
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
private data class Quint<A, B, C, D, E>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E)
