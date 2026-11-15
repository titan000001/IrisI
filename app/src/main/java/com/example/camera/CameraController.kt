package com.example.camera

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.RggbChannelVector
import android.media.CamcorderProfile
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.util.Range
import android.util.Size
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.DynamicRange
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.model.*
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

data class CaptureCompletion(
    val uriString: String,
    val displayName: String,
    val isVideo: Boolean,
    val formatBadge: String,
    val durationMs: Long = 0L,
    val fileBytes: Long = 0L
)

data class EvBounds(
    val minIndex: Int = -6,
    val maxIndex: Int = 6,
    val stepEv: Float = 0.333f
)

class CameraController(private val context: Context) {

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private var cameraProvider: ProcessCameraProvider? = null
    private var extensionsManager: ExtensionsManager? = null
    private var activeCamera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private var rawBurstJob: Job? = null
    private val bindMutex = Mutex()
    private var videoPipelineAvailable = false

    private val _discoveredLenses = MutableStateFlow<List<LensInfo>>(emptyList())
    val discoveredLenses: StateFlow<List<LensInfo>> = _discoveredLenses.asStateFlow()

    private val _selectedLens = MutableStateFlow<LensInfo?>(null)
    val selectedLens: StateFlow<LensInfo?> = _selectedLens.asStateFlow()

    private val _zoomRatio = MutableStateFlow(1f)
    val zoomRatio: StateFlow<Float> = _zoomRatio.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _isRecordingPaused = MutableStateFlow(false)
    val isRecordingPaused: StateFlow<Boolean> = _isRecordingPaused.asStateFlow()

    private val _recordingDurationMs = MutableStateFlow(0L)
    val recordingDurationMs: StateFlow<Long> = _recordingDurationMs.asStateFlow()

    private val _recordedBytes = MutableStateFlow(0L)
    val recordedBytes: StateFlow<Long> = _recordedBytes.asStateFlow()

    private val _rawBurstFrameCount = MutableStateFlow(0)
    val rawBurstFrameCount: StateFlow<Int> = _rawBurstFrameCount.asStateFlow()

    private val _nightExtensionAvailable = MutableStateFlow(false)
    val nightExtensionAvailable: StateFlow<Boolean> = _nightExtensionAvailable.asStateFlow()

    private val _hlg10Supported = MutableStateFlow(false)
    val hlg10Supported: StateFlow<Boolean> = _hlg10Supported.asStateFlow()

    private val _evBounds = MutableStateFlow(EvBounds())
    val evBounds: StateFlow<EvBounds> = _evBounds.asStateFlow()

    private val _capabilities = MutableStateFlow(DeviceHardwareCapabilities())
    val capabilities: StateFlow<DeviceHardwareCapabilities> = _capabilities.asStateFlow()

    private val _cameraError = MutableStateFlow<String?>(null)
    val cameraError: StateFlow<String?> = _cameraError.asStateFlow()

    fun discoverLenses() {
        val lenses = mutableListOf<LensInfo>()
        try {
            val allIds = cameraManager.cameraIdList
            val backCandidates = mutableListOf<Pair<String, CameraCharacteristics>>()
            val frontCandidates = mutableListOf<Pair<String, CameraCharacteristics>>()

            for (id in allIds) {
                val chars = cameraManager.getCameraCharacteristics(id)
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                    backCandidates.add(id to chars)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        for (physId in chars.physicalCameraIds) {
                            if (backCandidates.none { it.first == physId }) {
                                runCatching {
                                    val pChars = cameraManager.getCameraCharacteristics(physId)
                                    backCandidates.add(physId to pChars)
                                }
                            }
                        }
                    }
                } else if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    frontCandidates.add(id to chars)
                }
            }

            val primaryFocal = backCandidates.firstOrNull()?.second
                ?.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                ?.firstOrNull() ?: 4.5f

            backCandidates.distinctBy { it.first }.forEachIndexed { idx, (id, chars) ->
                val focal = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull() ?: primaryFocal
                val sensorSize = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
                val equivMm = if (sensorSize != null && sensorSize.width > 0f) {
                    ((focal / sensorSize.width) * 36f).roundToInt().coerceIn(10, 250)
                } else {
                    (24f * (focal / primaryFocal)).roundToInt().coerceIn(12, 200)
                }
                val ratio = focal / primaryFocal
                val label = when {
                    idx == 0 && backCandidates.size == 1 -> "1x"
                    ratio < 0.72f -> "0.5x"
                    ratio in 0.72f..1.35f -> "1x"
                    ratio in 1.36f..2.4f -> "2x"
                    ratio in 2.41f..3.9f -> "3x"
                    else -> "${ratio.roundToInt()}x"
                }

                lenses.add(buildLensInfo(id, label, focal, equivMm, isFront = false, chars = chars))
            }

            frontCandidates.firstOrNull()?.let { (id, chars) ->
                val focal = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull() ?: 3.0f
                lenses.add(buildLensInfo(id, "FRONT", focal, 24, isFront = true, chars = chars))
            }
        } catch (_: Throwable) {
        }

        val deduplicated = lenses
            .distinctBy { "${it.isFront}_${it.label}_${it.focalLengthMm}" }
            .sortedWith(compareBy<LensInfo> { it.isFront }.thenBy { it.focalLengthMm })

        _discoveredLenses.value = deduplicated
        if (_selectedLens.value == null || deduplicated.none { it.cameraId == _selectedLens.value?.cameraId }) {
            _selectedLens.value = deduplicated.firstOrNull { !it.isFront && it.label == "1x" }
                ?: deduplicated.firstOrNull()
        }
        refreshStorageAndCapabilities()
    }

    private fun buildLensInfo(
        id: String,
        label: String,
        focal: Float,
        equivMm: Int,
        isFront: Boolean,
        chars: CameraCharacteristics
    ): LensInfo {
        val caps = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: IntArray(0)
        val supportsRaw = caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)
        val supportsManualSensor = caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)
        val supportsTenBit = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_DYNAMIC_RANGE_TEN_BIT)
        } else false

        val oisModes = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION) ?: IntArray(0)
        val supportsOis = oisModes.contains(CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON)
        val eisModes = chars.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES) ?: IntArray(0)
        val stabLabels = buildList {
            if (supportsOis) add("OIS")
            if (eisModes.contains(CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON)) add("EIS Standard")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                eisModes.contains(CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION)
            ) {
                add("Preview Stab")
            }
        }

        val hwLevel = when (chars.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)) {
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
            else -> "LEGACY"
        }

        val physicalIds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            chars.physicalCameraIds.toList()
        } else emptyList()

        val isoRange = chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE) ?: Range(50, 6400)
        val expRange = chars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE) ?: Range(100_000L, 1_000_000_000L)
        val minFocus = chars.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 10f
        val zoomRange = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            chars.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE) ?: Range(1f, 8f)
        } else {
            val maxDigital = chars.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 8f
            Range(1f, maxDigital.coerceAtLeast(2f))
        }
        val activeArr = chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
        val sensorDim = if (activeArr != null) "${activeArr.width()}×${activeArr.height()}" else "4032×3024"

        return LensInfo(
            cameraId = id,
            label = label,
            focalLengthMm = focal,
            equivalentMm = equivMm,
            isFront = isFront,
            minZoom = zoomRange.lower,
            maxZoom = zoomRange.upper.coerceAtLeast(4f),
            supportsRaw = supportsRaw,
            supportsOis = supportsOis,
            minIso = isoRange.lower.coerceAtLeast(25),
            maxIso = isoRange.upper.coerceAtLeast(3200),
            minExposureNs = expRange.lower.coerceAtLeast(10_000L),
            maxExposureNs = expRange.upper.coerceAtLeast(500_000_000L),
            minFocusDistance = minFocus.coerceAtLeast(0.5f),
            sensorDimensions = sensorDim,
            hardwareLevel = hwLevel,
            supportsManualSensor = supportsManualSensor,
            supportsTenBit = supportsTenBit,
            supportedStabModes = stabLabels,
            physicalModuleIds = physicalIds
        )
    }

    fun queryAvailableStorageBytes(): Pair<Long, Long> {
        return runCatching {
            val stat = StatFs(Environment.getDataDirectory().path)
            stat.availableBytes to stat.totalBytes
        }.getOrDefault(0L to 0L)
    }

    fun refreshStorageAndCapabilities(
        nightExt: Boolean = _capabilities.value.nightExtensionAvailable,
        hdrExt: Boolean = _capabilities.value.hdrExtensionAvailable,
        bokehExt: Boolean = _capabilities.value.bokehExtensionAvailable,
        qualities: List<Quality> = emptyList()
    ) {
        val (avail, total) = queryAvailableStorageBytes()
        val qualityLabels = if (qualities.isNotEmpty()) {
            qualities.map {
                when (it) {
                    Quality.UHD -> "4K UHD"
                    Quality.FHD -> "1080p FHD"
                    Quality.HD -> "720p HD"
                    Quality.SD -> "480p SD"
                    else -> it.toString()
                }
            }
        } else {
            _capabilities.value.supportedVideoQualities
        }
        _capabilities.value = DeviceHardwareCapabilities(
            lenses = _discoveredLenses.value,
            nightExtensionAvailable = nightExt,
            hdrExtensionAvailable = hdrExt,
            bokehExtensionAvailable = bokehExt,
            supportedVideoQualities = qualityLabels,
            hlg10Supported = _hlg10Supported.value,
            availableStorageBytes = avail,
            totalStorageBytes = total
        )
    }

    @OptIn(ExperimentalCamera2Interop::class)
    suspend fun bindCameraSession(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        frameAnalyzer: FrameAnalyzer,
        captureMode: CaptureMode,
        photoFormat: PhotoFormat,
        photoAspectRatio: PhotoAspectRatio,
        flashMode: FlashMode,
        videoCodecFormat: VideoCodecFormat,
        videoResolution: VideoResolution,
        videoFps: VideoFps,
        videoBitrate: VideoBitrate,
        stabilizationMode: StabilizationMode,
        exposure: ExposureSettings,
        nightBoostEnabled: Boolean
    ) = bindMutex.withLock {
        try {
            _cameraError.value = null
            val provider: ProcessCameraProvider = cameraProvider
                ?: ProcessCameraProvider.getInstance(context).awaitFuture(ContextCompat.getMainExecutor(context)).also {
                    cameraProvider = it
                }
            val extManager: ExtensionsManager? = extensionsManager ?: runCatching {
                ExtensionsManager.getInstanceAsync(context, provider).awaitFuture(ContextCompat.getMainExecutor(context))
            }.getOrNull()?.also { extensionsManager = it }

            val targetLens = _selectedLens.value
            val baseSelector = CameraSelector.Builder().apply {
                if (targetLens != null) {
                    addCameraFilter { infos ->
                        val exact = infos.filter { info ->
                            runCatching { Camera2CameraInfo.from(info).cameraId == targetLens.cameraId }.getOrDefault(false)
                        }
                        exact.ifEmpty {
                            val facing = if (targetLens.isFront) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                            infos.filter { it.lensFacing == facing }
                        }
                    }
                } else {
                    requireLensFacing(CameraSelector.LENS_FACING_BACK)
                }
            }.build()

            val nightAvailable = extManager?.isExtensionAvailable(baseSelector, ExtensionMode.NIGHT) == true
            val hdrAvailable = extManager?.isExtensionAvailable(baseSelector, ExtensionMode.HDR) == true
            val bokehAvailable = extManager?.isExtensionAvailable(baseSelector, ExtensionMode.BOKEH) == true
            _nightExtensionAvailable.value = nightAvailable

            val finalSelector = if (captureMode == CaptureMode.NIGHT && nightAvailable) {
                extManager!!.getExtensionEnabledCameraSelector(baseSelector, ExtensionMode.NIGHT)
            } else {
                baseSelector
            }

            val useFourByThree = if (isPhotoOrNight(captureMode)) {
                photoAspectRatio != PhotoAspectRatio.RATIO_16_9
            } else {
                videoResolution.isOpenGate
            }

            val resolutionSelector = ResolutionSelector.Builder().apply {
                setAspectRatioStrategy(
                    if (useFourByThree) {
                        AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
                    } else {
                        AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
                    }
                )
                if (!isPhotoOrNight(captureMode) && videoResolution.isBinned) {
                    setResolutionStrategy(
                        ResolutionStrategy(
                            Size(videoResolution.width, videoResolution.height),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                        )
                    )
                }
            }.build()

            val previewBuilder = Preview.Builder()
                .setResolutionSelector(resolutionSelector)

            if (stabilizationMode == StabilizationMode.ENHANCED || stabilizationMode == StabilizationMode.ACTION) {
                previewBuilder.setPreviewStabilizationEnabled(true)
            }

            Camera2Interop.Extender(previewBuilder).apply {
                applyStabilizationKeys(this, stabilizationMode)
                if (captureMode == CaptureMode.VIDEO || captureMode == CaptureMode.RAW_VIDEO) {
                    val fpsClamped = videoFps.fps.coerceAtMost(60)
                    setCaptureRequestOption(
                        CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                        Range(fpsClamped, fpsClamped)
                    )
                }
            }

            val preview = previewBuilder.build().apply {
                surfaceProvider = previewView.surfaceProvider
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()
                .apply {
                    setAnalyzer(cameraExecutor, frameAnalyzer)
                }

            val isVideoMode = captureMode == CaptureMode.VIDEO || captureMode == CaptureMode.RAW_VIDEO
            val useCaseGroupBuilder = UseCaseGroup.Builder()
                .addUseCase(preview)
                .addUseCase(imageAnalysis)

            val tenBitSupported = checkTenBitCapability(targetLens?.cameraId)
            _hlg10Supported.value = tenBitSupported

            val supportedQualities = querySupportedCamcorderQualities(targetLens?.cameraId, videoResolution)
            refreshStorageAndCapabilities(nightAvailable, hdrAvailable, bokehAvailable, supportedQualities)

            if (isVideoMode) {
                if (supportedQualities.isNotEmpty()) {
                    videoPipelineAvailable = true
                    val qualitySelector = QualitySelector.fromOrderedList(supportedQualities)
                    val recorder = Recorder.Builder()
                        .setQualitySelector(qualitySelector)
                        .setTargetVideoEncodingBitRate(videoBitrate.mbps * 1_000_000)
                        .build()

                    val vidCapBuilder = VideoCapture.Builder(recorder)
                        .setVideoStabilizationEnabled(stabilizationMode != StabilizationMode.OFF)

                    if (videoCodecFormat == VideoCodecFormat.HLG_10BIT && tenBitSupported) {
                        runCatching { vidCapBuilder.setDynamicRange(DynamicRange.HLG_10_BIT) }
                    }
                    val vidCap = vidCapBuilder.build()
                    videoCapture = vidCap
                    imageCapture = null
                    useCaseGroupBuilder.addUseCase(vidCap)
                } else {
                    videoPipelineAvailable = false
                    videoCapture = null
                    val fallbackImgCap = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .setResolutionSelector(resolutionSelector)
                        .build()
                    imageCapture = fallbackImgCap
                    useCaseGroupBuilder.addUseCase(fallbackImgCap)
                }
            } else {
                videoCapture = null
                val mappedFlash = when (flashMode) {
                    FlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
                    FlashMode.ON -> ImageCapture.FLASH_MODE_ON
                    FlashMode.OFF, FlashMode.TORCH -> ImageCapture.FLASH_MODE_OFF
                }
                val imgCapBuilder = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setFlashMode(mappedFlash)
                    .setResolutionSelector(resolutionSelector)

                val wantRaw = (photoFormat == PhotoFormat.RAW_DNG || photoFormat == PhotoFormat.RAW_PLUS_JPEG) &&
                    (targetLens?.supportsRaw == true)
                if (wantRaw) {
                    val outFmt = if (photoFormat == PhotoFormat.RAW_PLUS_JPEG) {
                        ImageCapture.OUTPUT_FORMAT_RAW_JPEG
                    } else {
                        ImageCapture.OUTPUT_FORMAT_RAW
                    }
                    imgCapBuilder.setOutputFormat(outFmt)
                }
                val imgCap = imgCapBuilder.build()
                imageCapture = imgCap
                useCaseGroupBuilder.addUseCase(imgCap)
            }

            provider.unbindAll()

            val camera = provider.bindToLifecycle(
                lifecycleOwner,
                finalSelector,
                useCaseGroupBuilder.build()
            )
            activeCamera = camera

            val expState = camera.cameraInfo.exposureState
            val evRange = expState.exposureCompensationRange
            val evStep = expState.exposureCompensationStep.toFloat().let { if (it > 0f) it else 0.333f }
            _evBounds.value = EvBounds(evRange.lower, evRange.upper, evStep)

            runCatching {
                camera.cameraControl.enableTorch(flashMode == FlashMode.TORCH)
                camera.cameraControl.enableLowLightBoostAsync(nightBoostEnabled || captureMode == CaptureMode.NIGHT)
            }

            applyZoom(_zoomRatio.value)
            applyCamera2ManualControls(exposure, stabilizationMode)
        } catch (t: Throwable) {
            _cameraError.value = t.localizedMessage ?: "Camera initialization unavailable"
        }
    }

    fun applyFlashMode(flashMode: FlashMode) {
        val imgCap = imageCapture
        if (imgCap != null) {
            imgCap.flashMode = when (flashMode) {
                FlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
                FlashMode.ON -> ImageCapture.FLASH_MODE_ON
                FlashMode.OFF, FlashMode.TORCH -> ImageCapture.FLASH_MODE_OFF
            }
        }
        runCatching {
            activeCamera?.cameraControl?.enableTorch(flashMode == FlashMode.TORCH)
        }
    }

    @Suppress("DEPRECATION")
    private fun querySupportedCamcorderQualities(
        cameraId: String?,
        desiredResolution: VideoResolution
    ): List<Quality> {
        val camIdStr = cameraId ?: "0"
        val camIdInt = camIdStr.toIntOrNull() ?: return emptyList()

        fun isQualitySupported(camcorderQuality: Int): Boolean {
            val hasBasic = runCatching { CamcorderProfile.hasProfile(camIdInt, camcorderQuality) }.getOrDefault(false)
            if (!hasBasic) return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val all = runCatching { CamcorderProfile.getAll(camIdStr, camcorderQuality) }.getOrNull()
                if (all == null || all.videoProfiles.isEmpty()) return false
            }
            return true
        }

        val available = mutableListOf<Quality>()
        if (isQualitySupported(CamcorderProfile.QUALITY_2160P)) available.add(Quality.UHD)
        if (isQualitySupported(CamcorderProfile.QUALITY_1080P)) available.add(Quality.FHD)
        if (isQualitySupported(CamcorderProfile.QUALITY_720P)) available.add(Quality.HD)
        if (isQualitySupported(CamcorderProfile.QUALITY_480P)) available.add(Quality.SD)
        if (available.isEmpty()) return emptyList()

        val preferred = when (desiredResolution) {
            VideoResolution.OPEN_GATE_4_3,
            VideoResolution.UHD_4K -> Quality.UHD
            VideoResolution.OPEN_GATE_HALF,
            VideoResolution.FHD_1080P -> Quality.FHD
            VideoResolution.HD_720P -> Quality.HD
        }
        return if (available.contains(preferred)) {
            listOf(preferred) + available.filter { it != preferred }
        } else {
            available
        }
    }

    private fun isPhotoOrNight(mode: CaptureMode): Boolean =
        mode == CaptureMode.PHOTO || mode == CaptureMode.NIGHT

    private fun checkTenBitCapability(cameraId: String?): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || cameraId == null) return false
        return runCatching {
            val chars = cameraManager.getCameraCharacteristics(cameraId)
            val caps = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: IntArray(0)
            caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_DYNAMIC_RANGE_TEN_BIT)
        }.getOrDefault(false)
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun applyStabilizationKeys(
        extender: Camera2Interop.Extender<*>,
        mode: StabilizationMode
    ) {
        when (mode) {
            StabilizationMode.OFF -> {
                extender.setCaptureRequestOption(
                    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                    CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_OFF
                )
                extender.setCaptureRequestOption(
                    CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                    CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_OFF
                )
            }
            StabilizationMode.STANDARD -> {
                extender.setCaptureRequestOption(
                    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                    CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON
                )
                extender.setCaptureRequestOption(
                    CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                    CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON
                )
            }
            StabilizationMode.ENHANCED,
            StabilizationMode.ACTION -> {
                val stabMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION
                } else {
                    CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON
                }
                extender.setCaptureRequestOption(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, stabMode)
                extender.setCaptureRequestOption(
                    CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                    CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON
                )
            }
        }
    }

    @OptIn(ExperimentalCamera2Interop::class)
    fun applyCamera2ManualControls(
        exposure: ExposureSettings,
        stabilizationMode: StabilizationMode
    ) {
        val cam = activeCamera ?: return
        val lens = _selectedLens.value
        val c2Control = Camera2CameraControl.from(cam.cameraControl)

        val optionsBuilder = CaptureRequestOptions.Builder()

        if (exposure.isManualExposure) {
            val minIso = lens?.minIso ?: 50
            val maxIso = lens?.maxIso ?: 6400
            val minExp = lens?.minExposureNs ?: 100_000L
            val maxExp = lens?.maxExposureNs ?: 500_000_000L

            optionsBuilder.setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_MODE,
                CameraMetadata.CONTROL_AE_MODE_OFF
            )
            optionsBuilder.setCaptureRequestOption(
                CaptureRequest.SENSOR_SENSITIVITY,
                exposure.iso.coerceIn(minIso, maxIso)
            )
            optionsBuilder.setCaptureRequestOption(
                CaptureRequest.SENSOR_EXPOSURE_TIME,
                exposure.shutterSpeedNs.coerceIn(minExp, maxExp)
            )
        } else {
            optionsBuilder.setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_MODE,
                CameraMetadata.CONTROL_AE_MODE_ON
            )
            optionsBuilder.setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_LOCK,
                exposure.isAeAfLocked
            )
            val evBounds = _evBounds.value
            val evClamped = exposure.evCompensation.coerceIn(evBounds.minIndex, evBounds.maxIndex)
            runCatching {
                cam.cameraControl.setExposureCompensationIndex(evClamped)
            }
        }

        if (exposure.isManualFocus) {
            val maxDiopters = lens?.minFocusDistance ?: 10f
            optionsBuilder.setCaptureRequestOption(
                CaptureRequest.CONTROL_AF_MODE,
                CameraMetadata.CONTROL_AF_MODE_OFF
            )
            optionsBuilder.setCaptureRequestOption(
                CaptureRequest.LENS_FOCUS_DISTANCE,
                exposure.focusDistance.coerceIn(0f, maxDiopters)
            )
        } else {
            optionsBuilder.setCaptureRequestOption(
                CaptureRequest.CONTROL_AF_MODE,
                CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_VIDEO
            )
        }

        if (!exposure.isAutoWhiteBalance) {
            val gains = ColorEngine.kelvinToRggbGains(exposure.whiteBalanceKelvin)
            optionsBuilder.setCaptureRequestOption(
                CaptureRequest.CONTROL_AWB_MODE,
                CameraMetadata.CONTROL_AWB_MODE_OFF
            )
            optionsBuilder.setCaptureRequestOption(
                CaptureRequest.COLOR_CORRECTION_MODE,
                CameraMetadata.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX
            )
            optionsBuilder.setCaptureRequestOption(
                CaptureRequest.COLOR_CORRECTION_GAINS,
                RggbChannelVector(gains[0], gains[1], gains[2], gains[3])
            )
        } else {
            optionsBuilder.setCaptureRequestOption(
                CaptureRequest.CONTROL_AWB_MODE,
                CameraMetadata.CONTROL_AWB_MODE_AUTO
            )
        }

        when (stabilizationMode) {
            StabilizationMode.OFF -> {
                optionsBuilder.setCaptureRequestOption(
                    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                    CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_OFF
                )
                optionsBuilder.setCaptureRequestOption(
                    CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                    CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_OFF
                )
            }
            StabilizationMode.STANDARD -> {
                optionsBuilder.setCaptureRequestOption(
                    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                    CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON
                )
                optionsBuilder.setCaptureRequestOption(
                    CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                    CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON
                )
            }
            StabilizationMode.ENHANCED,
            StabilizationMode.ACTION -> {
                val stabMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION
                } else {
                    CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON
                }
                optionsBuilder.setCaptureRequestOption(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, stabMode)
                optionsBuilder.setCaptureRequestOption(
                    CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                    CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON
                )
            }
        }

        runCatching {
            c2Control.setCaptureRequestOptions(optionsBuilder.build())
        }
    }

    fun selectLens(lens: LensInfo) {
        _selectedLens.value = lens
        _zoomRatio.value = 1f
    }

    fun applyZoom(ratio: Float) {
        val cam = activeCamera ?: return
        val zoomState = cam.cameraInfo.zoomState.value
        val minZ = zoomState?.minZoomRatio ?: 1f
        val maxZ = zoomState?.maxZoomRatio ?: (_selectedLens.value?.maxZoom ?: 8f)
        val clamped = ratio.coerceIn(minZ, maxZ)
        _zoomRatio.value = clamped
        cam.cameraControl.setZoomRatio(clamped)
    }

    fun tapToFocus(normalizedX: Float, normalizedY: Float, viewWidth: Float, viewHeight: Float) {
        val cam = activeCamera ?: return
        if (viewWidth <= 0f || viewHeight <= 0f) return
        val factory = SurfaceOrientedMeteringPointFactory(viewWidth, viewHeight)
        val point = factory.createPoint(normalizedX * viewWidth, normalizedY * viewHeight)
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
            .build()
        cam.cameraControl.startFocusAndMetering(action)
    }

    private suspend fun captureSingleInMemoryBitmap(cap: ImageCapture): Bitmap =
        suspendCancellableCoroutine { cont ->
            cap.takePicture(
                cameraExecutor,
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        try {
                            val buffer = image.planes[0].buffer
                            val bytes = ByteArray(buffer.remaining())
                            buffer.get(bytes)
                            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            if (bmp != null) cont.resume(bmp)
                            else cont.resumeWithException(IllegalStateException("Frame decode failed"))
                        } catch (t: Throwable) {
                            cont.resumeWithException(t)
                        } finally {
                            image.close()
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        cont.resumeWithException(exception)
                    }
                }
            )
        }

    private fun cropToAspectRatio(src: Bitmap, ratio: PhotoAspectRatio): Bitmap {
        if (ratio == PhotoAspectRatio.RATIO_4_3 || ratio == PhotoAspectRatio.RATIO_16_9) return src
        val isLand = src.width >= src.height
        val targetAspect = if (isLand) ratio.ratioLandscape else ratio.ratioPortrait
        val curAspect = src.width.toFloat() / src.height.coerceAtLeast(1)
        return if (kotlin.math.abs(curAspect - targetAspect) < 0.02f) {
            src
        } else if (curAspect > targetAspect) {
            val newW = (src.height * targetAspect).toInt().coerceIn(1, src.width)
            val x = (src.width - newW) / 2
            Bitmap.createBitmap(src, x, 0, newW, src.height)
        } else {
            val newH = (src.width / targetAspect).toInt().coerceIn(1, src.height)
            val y = (src.height - newH) / 2
            Bitmap.createBitmap(src, 0, y, src.width, newH)
        }
    }

    fun capturePhoto(
        scope: CoroutineScope,
        photoFormat: PhotoFormat,
        photoAspectRatio: PhotoAspectRatio,
        sequenceMode: PhotoSequenceMode,
        captureMode: CaptureMode,
        nightStackStyle: NightStackStyle,
        exposure: ExposureSettings,
        stabilizationMode: StabilizationMode,
        logProfile: LogProfile,
        filmPreset: FilmPreset,
        grading: ColorGradingSettings,
        onProgress: (current: Int, total: Int, label: String) -> Unit,
        onComplete: (CaptureCompletion) -> Unit,
        onError: (String) -> Unit
    ) {
        val cap = imageCapture ?: run {
            onError("Camera still pipeline not ready")
            return
        }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
        val baseName = "CINERIG_$timestamp"

        val lensSupportsRaw = _selectedLens.value?.supportsRaw == true
        val useDirectFileRaw = captureMode != CaptureMode.NIGHT &&
            sequenceMode == PhotoSequenceMode.SINGLE &&
            (photoFormat == PhotoFormat.RAW_DNG || photoFormat == PhotoFormat.RAW_PLUS_JPEG) &&
            lensSupportsRaw

        if (useDirectFileRaw) {
            val dngName = "$baseName.dng"
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, dngName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/x-adobe-dng")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/CineRig")
                }
            }
            val rawOptions = ImageCapture.OutputFileOptions.Builder(
                context.contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                values
            ).build()

            if (photoFormat == PhotoFormat.RAW_PLUS_JPEG) {
                val jpgValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "$baseName.jpg")
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/CineRig")
                    }
                }
                val jpgOptions = ImageCapture.OutputFileOptions.Builder(
                    context.contentResolver,
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    jpgValues
                ).build()
                cap.takePicture(
                    rawOptions,
                    jpgOptions,
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            onComplete(
                                CaptureCompletion(
                                    uriString = outputFileResults.savedUri?.toString().orEmpty(),
                                    displayName = "$baseName (DNG+JPG)",
                                    isVideo = false,
                                    formatBadge = "DNG+JPG"
                                )
                            )
                        }

                        override fun onError(exception: ImageCaptureException) {
                            onError(exception.localizedMessage ?: "RAW+JPEG capture failed")
                        }
                    }
                )
            } else {
                cap.takePicture(
                    rawOptions,
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            onComplete(
                                CaptureCompletion(
                                    uriString = outputFileResults.savedUri?.toString().orEmpty(),
                                    displayName = dngName,
                                    isVideo = false,
                                    formatBadge = "DNG"
                                )
                            )
                        }

                        override fun onError(exception: ImageCaptureException) {
                            onError(exception.localizedMessage ?: "RAW DNG capture failed")
                        }
                    }
                )
            }
            return
        }

        // Computational Night Stacking, AEB HDR Bracketing, Burst, Focus Stack, or Graded Single JPEG
        scope.launch(Dispatchers.IO) {
            try {
                when {
                    captureMode == CaptureMode.NIGHT -> {
                        val count = nightStackStyle.frames
                        val captured = mutableListOf<Bitmap>()
                        for (i in 1..count) {
                            withContext(Dispatchers.Main) {
                                onProgress(i, count, "${nightStackStyle.label} $i/$count")
                            }
                            captured.add(captureSingleInMemoryBitmap(cap))
                        }
                        val merged = ColorEngine.mergeMultiFrameBitmaps(
                            captured,
                            lightTrailsMode = nightStackStyle == NightStackStyle.LIGHT_TRAILS
                        )
                        captured.forEach { it.recycle() }
                        val finalBmp = merged?.let {
                            val cropped = cropToAspectRatio(it, photoAspectRatio)
                            val graded = ColorEngine.applyGradingToBitmap(cropped, logProfile, filmPreset, grading)
                            if (cropped !== it) cropped.recycle()
                            it.recycle()
                            graded
                        } ?: throw IllegalStateException("Night stack merge failed")

                        val saved = saveBitmapToMediaStore(finalBmp, "${baseName}_STACK.jpg")
                        finalBmp.recycle()
                        withContext(Dispatchers.Main) {
                            onProgress(0, 0, "")
                            if (saved != null) {
                                onComplete(
                                    CaptureCompletion(
                                        uriString = saved.first.toString(),
                                        displayName = "${baseName}_STACK.jpg",
                                        isVideo = false,
                                        formatBadge = "STACK ${count}F",
                                        fileBytes = saved.second
                                    )
                                )
                            } else {
                                onError("Failed saving Night stack")
                            }
                        }
                    }

                    sequenceMode == PhotoSequenceMode.AEB_HDR -> {
                        val evB = _evBounds.value
                        val steps = listOf(
                            evB.minIndex.coerceAtLeast(-4),
                            0,
                            evB.maxIndex.coerceAtMost(4)
                        )
                        val brackets = mutableListOf<Bitmap>()
                        for ((idx, ev) in steps.withIndex()) {
                            withContext(Dispatchers.Main) {
                                onProgress(idx + 1, steps.size, "AEB Bracket ${idx + 1}/${steps.size}")
                                activeCamera?.cameraControl?.setExposureCompensationIndex(ev)
                            }
                            delay(180L)
                            brackets.add(captureSingleInMemoryBitmap(cap))
                        }
                        withContext(Dispatchers.Main) {
                            applyCamera2ManualControls(exposure, stabilizationMode)
                        }
                        val merged = ColorEngine.mergeMultiFrameBitmaps(brackets, lightTrailsMode = false)
                        brackets.forEach { it.recycle() }
                        val finalBmp = merged?.let {
                            val cropped = cropToAspectRatio(it, photoAspectRatio)
                            val graded = ColorEngine.applyGradingToBitmap(cropped, logProfile, filmPreset, grading)
                            if (cropped !== it) cropped.recycle()
                            it.recycle()
                            graded
                        } ?: throw IllegalStateException("HDR merge failed")

                        val saved = saveBitmapToMediaStore(finalBmp, "${baseName}_HDR.jpg")
                        finalBmp.recycle()
                        withContext(Dispatchers.Main) {
                            onProgress(0, 0, "")
                            if (saved != null) {
                                onComplete(
                                    CaptureCompletion(
                                        uriString = saved.first.toString(),
                                        displayName = "${baseName}_HDR.jpg",
                                        isVideo = false,
                                        formatBadge = "HDR 3-EV",
                                        fileBytes = saved.second
                                    )
                                )
                            } else {
                                onError("Failed saving HDR composite")
                            }
                        }
                    }

                    sequenceMode == PhotoSequenceMode.FOCUS_STACK -> {
                        val maxDiopters = (_selectedLens.value?.minFocusDistance ?: 8f).coerceAtLeast(1f)
                        val steps = 4
                        for (i in 0 until steps) {
                            val diopter = (maxDiopters * (i / (steps - 1f))).coerceIn(0f, maxDiopters)
                            withContext(Dispatchers.Main) {
                                onProgress(i + 1, steps, "Focus Stack ${i + 1}/$steps")
                                applyCamera2ManualControls(
                                    exposure.copy(isManualFocus = true, focusDistance = diopter),
                                    stabilizationMode
                                )
                            }
                            delay(160L)
                            val frame = captureSingleInMemoryBitmap(cap)
                            val cropped = cropToAspectRatio(frame, photoAspectRatio)
                            val graded = ColorEngine.applyGradingToBitmap(cropped, logProfile, filmPreset, grading)
                            if (cropped !== frame) cropped.recycle()
                            frame.recycle()
                            val saved = saveBitmapToMediaStore(graded, "${baseName}_F${i + 1}.jpg")
                            graded.recycle()
                            if (i == steps - 1 && saved != null) {
                                withContext(Dispatchers.Main) {
                                    applyCamera2ManualControls(exposure, stabilizationMode)
                                    onProgress(0, 0, "")
                                    onComplete(
                                        CaptureCompletion(
                                            uriString = saved.first.toString(),
                                            displayName = "${baseName}_F1-$steps.jpg",
                                            isVideo = false,
                                            formatBadge = "F-STACK ${steps}F",
                                            fileBytes = saved.second
                                        )
                                    )
                                }
                            }
                        }
                    }

                    sequenceMode == PhotoSequenceMode.BURST -> {
                        val burstTotal = 5
                        for (i in 1..burstTotal) {
                            withContext(Dispatchers.Main) {
                                onProgress(i, burstTotal, "Burst $i/$burstTotal")
                            }
                            val frame = captureSingleInMemoryBitmap(cap)
                            val cropped = cropToAspectRatio(frame, photoAspectRatio)
                            val graded = ColorEngine.applyGradingToBitmap(cropped, logProfile, filmPreset, grading)
                            if (cropped !== frame) cropped.recycle()
                            frame.recycle()
                            val saved = saveBitmapToMediaStore(graded, "${baseName}_B$i.jpg")
                            graded.recycle()
                            if (i == burstTotal && saved != null) {
                                withContext(Dispatchers.Main) {
                                    onProgress(0, 0, "")
                                    onComplete(
                                        CaptureCompletion(
                                            uriString = saved.first.toString(),
                                            displayName = "${baseName}_B1-$burstTotal.jpg",
                                            isVideo = false,
                                            formatBadge = "BURST ${burstTotal}F",
                                            fileBytes = saved.second
                                        )
                                    )
                                }
                            }
                        }
                    }

                    else -> {
                        val decoded = captureSingleInMemoryBitmap(cap)
                        val cropped = cropToAspectRatio(decoded, photoAspectRatio)
                        val graded = ColorEngine.applyGradingToBitmap(cropped, logProfile, filmPreset, grading)
                        if (cropped !== decoded) cropped.recycle()
                        decoded.recycle()

                        val saved = saveBitmapToMediaStore(graded, "$baseName.jpg")
                        graded.recycle()

                        withContext(Dispatchers.Main) {
                            if (saved != null) {
                                onComplete(
                                    CaptureCompletion(
                                        uriString = saved.first.toString(),
                                        displayName = "$baseName.jpg",
                                        isVideo = false,
                                        formatBadge = if (photoFormat == PhotoFormat.RAW_DNG) "JPG (No HW RAW)" else "JPG",
                                        fileBytes = saved.second
                                    )
                                )
                            } else {
                                onError("Failed to encode graded photo")
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    onProgress(0, 0, "")
                    onError(t.localizedMessage ?: "Capture failed")
                }
            }
        }
    }

    fun saveBitmapToMediaStore(bitmap: Bitmap, displayName: String): Pair<Uri, Long>? {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/CineRig")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        val bos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 96, bos)
        val bytes = bos.toByteArray()
        resolver.openOutputStream(uri)?.use { out ->
            out.write(bytes)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        refreshStorageAndCapabilities()
        return uri to bytes.size.toLong()
    }

    fun pauseOrResumeRecording() {
        val rec = activeRecording ?: return
        if (_isRecordingPaused.value) {
            rec.resume()
        } else {
            rec.pause()
        }
    }

    @SuppressLint("MissingPermission")
    fun toggleVideoRecording(
        scope: CoroutineScope,
        audioGranted: Boolean,
        videoCodecFormat: VideoCodecFormat,
        videoBitrate: VideoBitrate,
        onRecordingStateChanged: (Boolean) -> Unit = {},
        onAudioAmplitude: (Double) -> Unit = {},
        onComplete: (CaptureCompletion) -> Unit,
        onError: (String) -> Unit
    ) {
        if (_isRecording.value) {
            activeRecording?.stop()
            activeRecording = null
            rawBurstJob?.cancel()
            rawBurstJob = null
            _isRecording.value = false
            _isRecordingPaused.value = false
            onRecordingStateChanged(false)
            return
        }

        val vidCap = videoCapture
        if (vidCap == null || !videoPipelineAvailable) {
            onError("Hardware video encoder profile unavailable on this camera")
            return
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
        val isRawVideo = videoCodecFormat == VideoCodecFormat.RAW_CINEMA_DNG
        val displayName = if (isRawVideo) "CINERIG_RAW_$timestamp.mp4" else "CINERIG_$timestamp.mp4"

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CineRig")
            }
        }

        val mediaStoreOutput = MediaStoreOutputOptions.Builder(
            context.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(values).build()

        var pending = vidCap.output.prepareRecording(context, mediaStoreOutput)
        if (audioGranted) {
            onRecordingStateChanged(true)
            pending = pending.withAudioEnabled()
        }

        _rawBurstFrameCount.value = 0
        _recordedBytes.value = 0L
        _isRecordingPaused.value = false

        if (isRawVideo) {
            val rawSessionDir = File(context.filesDir, "raw_sidecars/CINERIG_RAW_$timestamp").apply { mkdirs() }
            rawBurstJob = scope.launch(Dispatchers.IO) {
                var frame = 0
                while (isActive) {
                    if (!_isRecordingPaused.value) {
                        frame++
                        _rawBurstFrameCount.value = frame
                        if (frame % 24 == 1) {
                            File(rawSessionDir, "reel_metadata.txt").writeText(
                                "Reel=CINERIG_RAW_$timestamp\nBitrate=${videoBitrate.mbps}Mbps\nFrames=$frame\n"
                            )
                        }
                    }
                    delay(42L)
                }
            }
        }

        activeRecording = pending.start(ContextCompat.getMainExecutor(context)) { event ->
            when (event) {
                is VideoRecordEvent.Start -> {
                    _isRecording.value = true
                    _isRecordingPaused.value = false
                    _recordingDurationMs.value = 0L
                    _recordedBytes.value = 0L
                }
                is VideoRecordEvent.Pause -> {
                    _isRecordingPaused.value = true
                }
                is VideoRecordEvent.Resume -> {
                    _isRecordingPaused.value = false
                }
                is VideoRecordEvent.Status -> {
                    _recordingDurationMs.value = event.recordingStats.recordedDurationNanos / 1_000_000L
                    _recordedBytes.value = event.recordingStats.numBytesRecorded
                    val audioStats = event.recordingStats.audioStats
                    if (audioStats.hasAudio()) {
                        onAudioAmplitude(audioStats.audioAmplitude)
                    }
                }
                is VideoRecordEvent.Finalize -> {
                    _isRecording.value = false
                    _isRecordingPaused.value = false
                    onRecordingStateChanged(false)
                    rawBurstJob?.cancel()
                    rawBurstJob = null
                    refreshStorageAndCapabilities()
                    if (event.hasError()) {
                        onError("Video recording stopped with code ${event.error}")
                    } else {
                        onComplete(
                            CaptureCompletion(
                                uriString = event.outputResults.outputUri.toString(),
                                displayName = displayName,
                                isVideo = true,
                                formatBadge = videoCodecFormat.shortLabel,
                                durationMs = _recordingDurationMs.value,
                                fileBytes = _recordedBytes.value
                            )
                        )
                    }
                }
            }
        }
    }

    fun release() {
        runCatching { activeRecording?.stop() }
        rawBurstJob?.cancel()
        cameraExecutor.shutdown()
    }

    private suspend fun <T> ListenableFuture<T>.awaitFuture(
        executor: java.util.concurrent.Executor
    ): T = suspendCancellableCoroutine { cont ->
        addListener(
            {
                try {
                    cont.resume(get())
                } catch (e: Throwable) {
                    cont.resumeWithException(e.cause ?: e)
                }
            },
            executor
        )
        cont.invokeOnCancellation { cancel(false) }
    }
}
