package com.example.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.media.ImageReader
import android.hardware.camera2.DngCreator
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Experimental sequential RAW_SENSOR capture. This is DNG frames, not encoded RAW video. */
class RawSequenceRecorder(private val context: Context) : AutoCloseable {
    data class Config(val width: Int, val height: Int, val durationMs: Long = 5_000, val fps: Int = 5, val maxFrames: Int = 60)
    data class Result(val directory: File, val frames: Int, val error: String? = null)

    private val thread = HandlerThread("IrisIRawSequence").apply { start() }
    private val handler = Handler(thread.looper)
    private val writer: ExecutorService = Executors.newSingleThreadExecutor()
    private val stopping = AtomicBoolean(false)
    private var camera: CameraDevice? = null
    private var session: android.hardware.camera2.CameraCaptureSession? = null
    private var reader: ImageReader? = null
    private var directory: File? = null
    private var frameCount = 0
    private val frameMetadata = JSONArray()
    private var startedAt = 0L
    private val lifecycle = RawCaptureLifecycle()
    private val used = AtomicBoolean(false)
    private val captureTimeout = Runnable { end("RAW frame timed out waiting for matching sensor data") }
    private val setupTimeout = Runnable { end("RAW camera setup timed out") }
    private var lastResult: TotalCaptureResult? = null
    private var lastImage: android.media.Image? = null
    private var imageTimestamp: Long? = null
    private var resultTimestamp: Long? = null
    private var finish: ((Result) -> Unit)? = null

    @SuppressLint("MissingPermission")
    fun start(cameraId: String, config: Config, onStarted: () -> Unit = {}, onFinished: (Result) -> Unit) {
        require(config.width > 0 && config.height > 0 && config.fps > 0 && config.durationMs > 0 && config.maxFrames > 0)
        check(!stopping.get()) { "RAW recorder is closed" }
        check(used.compareAndSet(false, true)) { "RAW recorder already used" }
        finish = onFinished
        activeConfig = config
        configFps = config.fps
        handler.post {
            if (stopping.get()) { closeCamera(); return@post }
            handler.postDelayed(setupTimeout, 10_000L)
            try {
                val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                val chars = manager.getCameraCharacteristics(cameraId)
                val caps = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
                check(caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)) { "Camera does not support RAW_SENSOR" }
                val map = requireNotNull(chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP))
                // DngCreator cannot serialize arbitrary cropped RAW output, even when the
                // stream map advertises it. Its dimensions must match a sensor array.
                val nativeSizes = buildList {
                    chars.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)?.let { add(it.width to it.height) }
                    chars.get(CameraCharacteristics.SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE)?.let { add(it.width() to it.height()) }
                }
                val dimensions = RawSensorGeometry.select(
                    map.getOutputSizes(ImageFormat.RAW_SENSOR).orEmpty().map { it.width to it.height },
                    nativeSizes, config.width to config.height
                ) ?: error("No DNG-compatible full sensor RAW output size reported")
                val size = android.util.Size(dimensions.first, dimensions.second)
                directory = File(context.filesDir, "raw_sequences/IRISI_RAW_${System.currentTimeMillis()}").apply {
                    check(mkdirs()) { "Could not create RAW sequence directory" }
                }
                reader = ImageReader.newInstance(size.width, size.height, ImageFormat.RAW_SENSOR, 2).also { output ->
                    output.setOnImageAvailableListener({ source ->
                        val image = runCatching { source.acquireNextImage() }.getOrNull() ?: return@setOnImageAvailableListener
                        if (stopping.get() || !lifecycle.capturing || lastImage != null) image.close() else {
                            lastImage = image
                            imageTimestamp = image.timestamp
                            writeIfPaired(chars)
                        }
                    }, handler)
                }
                manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                    override fun onOpened(value: CameraDevice) {
                        if (stopping.get()) { value.close(); closeCamera(); return }
                        camera = value
                        try {
                            value.createCaptureSession(listOf(requireNotNull(reader).surface), object : android.hardware.camera2.CameraCaptureSession.StateCallback() {
                                override fun onConfigured(value: android.hardware.camera2.CameraCaptureSession) {
                                    if (stopping.get()) { value.close(); closeCamera(); return }
                                    handler.removeCallbacks(setupTimeout)
                                    session = value
                                    startedAt = SystemClock.elapsedRealtime()
                                    onStarted()
                                    captureNext(chars, config)
                                }
                                override fun onConfigureFailed(value: android.hardware.camera2.CameraCaptureSession) {
                                    value.close()
                                    end("RAW session configuration failed")
                                }
                            }, handler)
                        } catch (t: Throwable) { end(t.message ?: "Could not configure RAW session") }
                    }
                    override fun onDisconnected(value: CameraDevice) {
                        value.close()
                        end("RAW camera disconnected")
                    }
                    override fun onError(value: CameraDevice, error: Int) {
                        value.close()
                        end("RAW camera error $error")
                    }
                }, handler)
            } catch (t: Throwable) { end(t.message ?: "RAW capture unavailable") }
        }
    }

    fun stop() { if (!stopping.getAndSet(true)) handler.post { closeCamera() } }

    private fun captureNext(chars: CameraCharacteristics, config: Config) {
        if (stopping.get() || lifecycle.capturing || lifecycle.writing) return
        if (frameCount >= config.maxFrames || SystemClock.elapsedRealtime() - startedAt >= config.durationMs) {
            stop(); return
        }
        try {
            val request = requireNotNull(camera).createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(requireNotNull(reader).surface)
                set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
            }.build()
            if (!lifecycle.beginCapture()) return
            handler.postDelayed(captureTimeout, 5_000L)
            requireNotNull(session).capture(request, object : android.hardware.camera2.CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(s: android.hardware.camera2.CameraCaptureSession, r: CaptureRequest, result: TotalCaptureResult) {
                    if (stopping.get() || !lifecycle.capturing) return
                    lastResult = result
                    resultTimestamp = result.get(android.hardware.camera2.CaptureResult.SENSOR_TIMESTAMP)
                    writeIfPaired(chars)
                }
                override fun onCaptureFailed(s: android.hardware.camera2.CameraCaptureSession, r: CaptureRequest, failure: android.hardware.camera2.CaptureFailure) = end("RAW frame failed")
                override fun onCaptureSequenceAborted(s: android.hardware.camera2.CameraCaptureSession, sequenceId: Int) = end("RAW capture aborted")
            }, handler)
        } catch (t: Throwable) { end(t.message ?: "RAW request failed") }
    }

    private fun writeIfPaired(chars: CameraCharacteristics) {
        val image = lastImage ?: return
        val result = lastResult ?: return
        if (!lifecycle.beginWrite(imageTimestamp, resultTimestamp)) return
        handler.removeCallbacks(captureTimeout)
        lastImage = null
        lastResult = null
        imageTimestamp = null
        resultTimestamp = null
        val index = frameCount
        val dir = requireNotNull(directory)
        writer.execute {
            var error: String? = null
            try {
                val part = File(dir, "frame_%04d.dng.part".format(index))
                val output = File(dir, "frame_%04d.dng".format(index))
                FileOutputStream(part).use { stream ->
                    DngCreator(chars, result).use { it.setOrientation(1); it.writeImage(stream, image) }
                    stream.fd.sync()
                }
                check(part.renameTo(output)) { "Could not finalize RAW frame" }
            } catch (t: Throwable) { error = t.message ?: "RAW frame write failed" } finally { image.close() }
            handler.post {
                lifecycle.finishWrite()
                if (error != null) end(error) else {
                    frameMetadata.put(JSONObject()
                        .put("file", "frame_%04d.dng".format(index))
                        .put("sensorTimestampNs", result.get(android.hardware.camera2.CaptureResult.SENSOR_TIMESTAMP))
                        .put("exposureTimeNs", result.get(android.hardware.camera2.CaptureResult.SENSOR_EXPOSURE_TIME))
                        .put("iso", result.get(android.hardware.camera2.CaptureResult.SENSOR_SENSITIVITY)))
                    frameCount++
                    if (stopping.get()) closeCamera() else {
                        val delay = (1000L / configFps.coerceAtLeast(1)).coerceAtLeast(1L)
                        handler.postDelayed({ captureNext(chars, activeConfig) }, delay)
                    }
                }
            }
        }
    }

    private var activeConfig = Config(1, 1)
    private var configFps = 5
    private fun end(error: String?) {
        if (lifecycle.completed) return
        stopping.set(true)
        if (this.error == null) this.error = error
        handler.post { closeCamera() }
    }
    private var error: String? = null
    private fun closeCamera() {
        lifecycle.stop()
        handler.removeCallbacks(captureTimeout)
        handler.removeCallbacks(setupTimeout)
        lastImage?.close()
        lastImage = null
        lastResult = null
        imageTimestamp = null
        resultTimestamp = null
        runCatching { session?.stopRepeating() }
        runCatching { session?.close() }
        runCatching { camera?.close() }
        session = null; camera = null
        // The writer owns its acquired Image until its finally block. Keep the reader alive.
        if (lifecycle.writing) return
        runCatching { reader?.close() }
        reader = null
        if (!lifecycle.complete()) return
        val target = directory ?: run {
            val callback = finish
            finish = null
            try { callback?.invoke(Result(context.filesDir, 0, error ?: "RAW sequence did not start")) }
            finally { releaseWorkers() }
            return
        }
        runCatching {
            val manifest = File(target, "manifest.json.part")
            manifest.writeText(
                JSONObject()
                    .put("schema", 1)
                    .put("format", "DNG_SEQUENCE")
                    .put("frames", frameCount)
                    .put("durationMs", activeConfig.durationMs)
                    .put("requestedFps", activeConfig.fps)
                    .put("elapsedMs", if (startedAt == 0L) 0L else SystemClock.elapsedRealtime() - startedAt)
                    .put("frameMetadata", frameMetadata)
                    .put("error", error ?: JSONObject.NULL)
                    .toString(2)
            )
            check(manifest.renameTo(File(target, "manifest.json"))) { "Could not finalize RAW manifest" }
        }.onFailure { if (error == null) error = it.message ?: "Could not write RAW manifest" }
        val result = Result(target, frameCount, error)
        val callback = finish
        finish = null
        try { callback?.invoke(result) } finally { releaseWorkers() }
    }

    private fun releaseWorkers() {
        writer.shutdown()
        thread.quitSafely()
    }

    override fun close() { stop() }
}
