package com.example

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.view.WindowManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.camera.CaptureCompletion
import com.example.camera.AvcLogSignaling
import com.example.camera.HevcSequenceInfo
import com.example.model.StabilizationMode
import com.example.model.ExposureProgram
import com.example.model.VideoBitrate
import com.example.model.VideoCodecFormat
import com.example.model.VideoFps
import com.example.model.VideoResolution
import com.example.viewmodel.CineRigViewModel
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit opt-in: creates two short LOG/HLG clips; keeps outputs, never deletes media. */
@RunWith(AndroidJUnit4::class)
class EncodedVideoRecordingSmokeTest {
    private fun matchesGeometry(requestWidth: Int, requestHeight: Int, actualWidth: Int, actualHeight: Int) =
        (requestWidth == actualWidth && requestHeight == actualHeight) ||
            (requestWidth == actualHeight && requestHeight == actualWidth)

    @Test fun recordRepeatedHlgClips() {
        val args = InstrumentationRegistry.getArguments()
        val mode = args.getString("recordEncodedSmoke")
        assumeTrue("Capture requires explicit recordEncodedSmoke opt-in", mode != null)
        assertTrue("Unknown recordEncodedSmoke mode: $mode", mode in listOf("HLG", "SDR_HLG_PAIR", "LOG"))
        val paired = mode == "SDR_HLG_PAIR"
        var selectedCodec = if (mode == "LOG") VideoCodecFormat.IRISI_LOG_8 else VideoCodecFormat.HLG_10BIT
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertTrue("Explicit capture test requires camera permission",
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
        assertFalse("Unlock is required; the test never bypasses keyguard",
            (context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val reports = JSONArray()
        val reportFile = File(context.filesDir, when {
            paired -> "encoded-sdr-hlg-pair.json"
            mode == "LOG" -> "encoded-log-smoke.json"
            else -> "encoded-hlg-smoke.json"
        })
        val runReport = JSONObject().put("runId", System.currentTimeMillis()).put("requestedMode", mode)
            .put("complete", false).put("clips", reports)
            .put("limitations", "Short recordings, signaling, track timestamp alignment and sampled decoded contrast only; not colorimetry, audible/visual AV sync, source precision, lifecycle or thermal proof.")
        reportFile.writeText(runReport.toString(2))
        val completion = AtomicReference<CaptureCompletion?>()
        val failure = AtomicReference<String?>()
        lateinit var vm: CineRigViewModel
        var initializedVm: CineRigViewModel? = null
        fun onMain(action: () -> Unit) = instrumentation.runOnMainSync(action)
        fun waitFor(label: String, timeoutMs: Long = 20_000, condition: () -> Boolean) {
            val deadline = android.os.SystemClock.elapsedRealtime() + timeoutMs
            while (!condition() && android.os.SystemClock.elapsedRealtime() < deadline) {
                failure.get()?.let { throw AssertionError("$label: $it") }
                Thread.sleep(100)
            }
            assertTrue("Timed out: $label (${vm.cameraController.cameraError.value})", condition())
        }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    vm = ViewModelProvider(activity)[CineRigViewModel::class.java]
                    initializedVm = vm
                }
                waitFor("camera permission and lens discovery") { vm.uiState.value.cameraGranted && vm.uiState.value.lenses.isNotEmpty() }
                onMain {
                    if (paired) {
                        vm.uiState.value.lenses.firstOrNull { it.cameraId == "0" }?.let { vm.selectLens(it) }
                        vm.updateExposure { it.copy(program = ExposureProgram.MANUAL, isManualExposure = true,
                            iso = 800, shutterSpeedNs = 20_833_333L, isAutoWhiteBalance = true) }
                    }
                    vm.setVideoResolution(VideoResolution.FHD_1080P)
                    vm.setVideoFps(VideoFps.FPS_24)
                    vm.setStabilizationMode(StabilizationMode.OFF)
                    vm.setVideoCodecFormat(selectedCodec)
                }
                // Allow the coalesced rebind to invalidate the prior session first.
                Thread.sleep(1500)
                waitFor("$mode binding") {
                    vm.cameraController.cameraError.value == null &&
                        (vm.cameraController.liveAnalysisAvailable.value || vm.cameraController.cameraNotice.value != null)
                }
                if (mode != "LOG") assertTrue("HLG must be advertised on the selected camera", vm.cameraController.hlg10Supported.value)
                repeat(2) {
                    if (paired) {
                        selectedCodec = if (it == 0) VideoCodecFormat.H264_STANDARD else VideoCodecFormat.HLG_10BIT
                        onMain { vm.setVideoCodecFormat(selectedCodec) }
                        Thread.sleep(1500)
                        waitFor("paired ${selectedCodec.name} binding") {
                            vm.cameraController.cameraError.value == null &&
                                (vm.cameraController.liveAnalysisAvailable.value || vm.cameraController.cameraNotice.value != null)
                        }
                    }
                    completion.set(null)
                    failure.set(null)
                    val audioGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                    fun toggle() = vm.cameraController.toggleVideoRecording(
                        scope = scope,
                        audioGranted = audioGranted,
                        videoCodecFormat = selectedCodec,
                        videoBitrate = VideoBitrate.MBPS_100,
                        onRecordingStateChanged = vm.audioManager::setPausedForVideoRecording,
                        onComplete = { completion.set(it) },
                        onError = { failure.set(it) }
                    )
                    onMain { toggle() }
                    waitFor("recording start") { vm.cameraController.isRecording.value }
                    waitFor("five seconds of encoded output", 15_000) {
                        vm.cameraController.recordedBytes.value > 0 && vm.cameraController.recordingDurationMs.value >= 5000
                    }
                    onMain { toggle() }
                    waitFor("recording finalization") { completion.get() != null }
                    waitFor("idle after finalization") { !vm.cameraController.isRecording.value }
                    val clip = requireNotNull(completion.get())
                    val uri = Uri.parse(clip.uriString)
                        val extractor = MediaExtractor()
                    val report = JSONObject().put("displayName", clip.displayName).put("uri", uri)
                        .put("durationMs", clip.durationMs).put("fileBytes", clip.fileBytes)
                        .put("requestedMode", selectedCodec.name)
                        .put("requestedExposure", vm.uiState.value.exposure.toString())
                        .put("cameraId", vm.uiState.value.selectedLens?.cameraId)
                    try {
                        extractor.setDataSource(context, uri, null)
                        val video = (0 until extractor.trackCount).map { extractor.getTrackFormat(it) }
                            .single { it.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true }
                        report.put("format", video.toString())
                        val encodedWidth = video.getInteger(MediaFormat.KEY_WIDTH)
                        val encodedHeight = video.getInteger(MediaFormat.KEY_HEIGHT)
                        // CameraX may encode the sensor-oriented dimensions and express
                        // display orientation separately. Require exact FHD geometry while
                        // accepting either 1920x1080 or its rotated representation.
                        assertTrue(
                            "FHD output must be 1920x1080 in either orientation, got ${encodedWidth}x${encodedHeight}",
                            matchesGeometry(1920, 1080, encodedWidth, encodedHeight)
                        )
                        if (selectedCodec == VideoCodecFormat.HLG_10BIT) {
                        assertEquals("video/hevc", video.getString(MediaFormat.KEY_MIME))
                        assertEquals(MediaFormat.COLOR_STANDARD_BT2020, video.getInteger(MediaFormat.KEY_COLOR_STANDARD))
                        assertEquals(MediaFormat.COLOR_TRANSFER_HLG, video.getInteger(MediaFormat.KEY_COLOR_TRANSFER))
                        val csd = requireNotNull(video.getByteBuffer("csd-0")).duplicate()
                        val bytes = ByteArray(csd.remaining()).also { csd.get(it) }
                        val sequence = HevcSequenceInfo.parseAnnexB(bytes)
                        report.put("spsLumaBits", sequence.lumaBits).put("spsChromaBits", sequence.chromaBits)
                        assertEquals(10, sequence.lumaBits)
                        assertEquals(10, sequence.chromaBits)
                        assertEquals(encodedWidth, sequence.width)
                        assertEquals(encodedHeight, sequence.height)
                        } else if (selectedCodec == VideoCodecFormat.H264_STANDARD) {
                            assertEquals(MediaFormat.COLOR_TRANSFER_SDR_VIDEO,
                                video.getInteger(MediaFormat.KEY_COLOR_TRANSFER))
                        } else {
                            assertEquals("video/avc", video.getString(MediaFormat.KEY_MIME))
                            assertTrue("Custom LOG must not retain SDR transfer tags",
                                !video.containsKey(MediaFormat.KEY_COLOR_TRANSFER) || video.getInteger(MediaFormat.KEY_COLOR_TRANSFER) == 0)
                            val csd = requireNotNull(video.getByteBuffer("csd-0")).duplicate()
                            val sps = ByteArray(csd.remaining()).also { csd.get(it) }
                            assertArrayEquals("AVC SPS transfer must already be unspecified", sps,
                                AvcLogSignaling.unspecifiedTransfer(sps))
                            val sidecar = JSONObject(File(context.filesDir, "log_sidecars/${clip.displayName}.log.json").readText())
                            assertEquals("irisi-log-v1", sidecar.getString("curve"))
                            assertEquals(8, sidecar.getInt("bits"))
                            assertEquals(clip.displayName, sidecar.getString("clip"))
                        }
                        val timing = JSONArray()
                        var videoStart = -1L; var videoEnd = -1L
                        var audioStart = -1L; var audioEnd = -1L
                        for (index in 0 until extractor.trackCount) {
                            val format = extractor.getTrackFormat(index)
                            val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                            extractor.selectTrack(index)
                            extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                            var first = Long.MAX_VALUE; var last = -1L; var samples = 0
                            while (extractor.sampleTrackIndex >= 0) {
                                first = minOf(first, extractor.sampleTime)
                                last = maxOf(last, extractor.sampleTime)
                                samples++
                                if (!extractor.advance()) break
                            }
                            extractor.unselectTrack(index)
                            assertTrue("$mime must contain timed samples", samples > 1 && last > first)
                            val track = JSONObject().put("mime", mime).put("samples", samples)
                                .put("firstTimestampUs", first).put("lastTimestampUs", last)
                            if (mime.startsWith("video/")) {
                                videoStart = first; videoEnd = last
                                val fps = (samples - 1) * 1_000_000.0 / (last - first)
                                track.put("measuredFps", fps)
                                assertEquals("Measured cadence", 24.0, fps, 1.0)
                            } else if (mime.startsWith("audio/")) {
                                audioStart = first; audioEnd = last
                            }
                            timing.put(track)
                        }
                        report.put("tracks", timing).put("audioRequested", audioGranted)
                        if (audioGranted) {
                            assertTrue("Audio permission must produce an audio track", audioStart >= 0)
                            report.put("avStartDeltaUs", audioStart - videoStart).put("avEndDeltaUs", audioEnd - videoEnd)
                            assertTrue("Track starts must align within 250ms", kotlin.math.abs(audioStart - videoStart) < 250_000)
                            assertTrue("Track ends must align within 250ms", kotlin.math.abs(audioEnd - videoEnd) < 250_000)
                        }
                    } finally { extractor.release() }
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(context, uri)
                        val frame = retriever.getFrameAtTime(2_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        assertNotNull("$selectedCodec clip must decode a frame", frame)
                        report.put("decodedWidth", frame!!.width).put("decodedHeight", frame.height)
                        var low = 255; var high = 0
                        for (y in 0 until frame.height step maxOf(1, frame.height / 64)) {
                            for (x in 0 until frame.width step maxOf(1, frame.width / 64)) {
                                val pixel = frame.getPixel(x, y)
                                val luma = (54 * ((pixel shr 16) and 255) + 183 * ((pixel shr 8) and 255) + 19 * (pixel and 255)) / 256
                                low = minOf(low, luma); high = maxOf(high, luma)
                            }
                        }
                        report.put("minLuma8", low).put("maxLuma8", high)
                        if (args.getString("requireNonblankFrames") == "true") {
                            assertTrue("Visible scene must not decode to uniform/black", high - low >= 8 && high > 8)
                        }
                        frame.recycle()
                    } finally { retriever.release() }
                    reports.put(report)
                    reportFile.writeText(runReport.toString(2))
                }
                reportFile.writeText(runReport.put("complete", true).toString(2))
            }
        } catch (error: Throwable) {
            reportFile.writeText(runReport.put("error", error.toString()).toString(2))
            throw error
        } finally {
            initializedVm?.let { current -> if (current.cameraController.isRecording.value) {
                onMain { current.cameraController.toggleVideoRecording(scope, false, selectedCodec,
                    VideoBitrate.MBPS_100, onComplete = {}, onError = {}) }
            } }
            scope.cancel()
        }
    }
}
