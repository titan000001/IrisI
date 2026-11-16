package com.example

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.camera.IrisLogTransfer
import com.example.camera.LogSurfaceRenderer
import com.example.camera.AvcLogSignaling
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in original GPU transfer -> hardware encoder proof, not a camera/audio workflow. */
class EncodedLogRampTest {
    @Test fun encodeEightBitLogRamps() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("recordLogRamp") == "true")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = requireNotNull(context.getExternalFilesDir("log-experiments"))
        val reports = JSONArray()
        for (mime in listOf("video/avc", "video/hevc")) {
            val encoderInfo = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.firstOrNull {
                it.isEncoder && it.isHardwareAccelerated && it.supportedTypes.contains(mime)
            }
            assertNotNull("An explicit hardware encoder is required for $mime", encoderInfo)
            val file = File(directory, "IRISI_LOG_RAMP_${System.currentTimeMillis()}.mp4")
            val codec = MediaCodec.createByCodecName(requireNotNull(encoderInfo).name)
            val muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var renderer: LogSurfaceRenderer? = null
            var surface: android.view.Surface? = null
            var codecStarted = false
            var muxerStarted = false
            var track = -1
            var samples = 0
            var previousPts = -1L
            var eos = false
            val report = JSONObject().put("mime", mime).put("encoder", encoderInfo.name)
                .put("path", file.absolutePath).put("source", "synthetic normalized linear RGB ramp")
                .put("curve", IrisLogTransfer.ID).put("requestedBits", 8)
                .put("audio", false).put("cameraRecording", false)
            try {
                val format = MediaFormat.createVideoFormat(mime, 640, 360).apply {
                    setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                    setInteger(MediaFormat.KEY_BIT_RATE, 12_000_000)
                    setInteger(MediaFormat.KEY_FRAME_RATE, 24)
                    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                    setInteger(MediaFormat.KEY_PROFILE, if (mime == "video/avc")
                        MediaCodecInfo.CodecProfileLevel.AVCProfileHigh else MediaCodecInfo.CodecProfileLevel.HEVCProfileMain)
                    setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT709)
                    setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_FULL)
                    // Custom LOG is neither Rec.709 transfer nor HLG/PQ. Verify effective signaling later.
                    setInteger(MediaFormat.KEY_COLOR_TRANSFER, 0)
                }
                codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                surface = codec.createInputSurface()
                renderer = LogSurfaceRenderer(surface, 640, 360)
                codec.start(); codecStarted = true
                fun drain(timeoutUs: Long) {
                    val info = MediaCodec.BufferInfo()
                    while (true) {
                        val index = codec.dequeueOutputBuffer(info, timeoutUs)
                        when {
                            index == MediaCodec.INFO_TRY_AGAIN_LATER -> return
                            index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                                check(!muxerStarted)
                                val effective = codec.outputFormat
                                report.put("effectiveFormat", effective.toString())
                                val effectiveTransfer = if (effective.containsKey(MediaFormat.KEY_COLOR_TRANSFER))
                                    effective.getInteger(MediaFormat.KEY_COLOR_TRANSFER) else 0
                                report.put("effectiveTransfer", effectiveTransfer)
                                report.put("metadataHonestForCustomLog", effectiveTransfer == 0)
                                // The platform encoder currently forces SDR metadata. Do not
                                // mistake this for a conformant LOG signaling implementation.
                                assertEquals(MediaFormat.COLOR_TRANSFER_SDR_VIDEO, effectiveTransfer)
                                if (mime == "video/avc") {
                                    val csd = requireNotNull(effective.getByteBuffer("csd-0")).duplicate()
                                    val original = ByteArray(csd.remaining()).also { csd.get(it) }
                                    val patched = AvcLogSignaling.unspecifiedTransfer(original)
                                    assertFalse(original.contentEquals(patched))
                                    assertArrayEquals(patched, AvcLogSignaling.unspecifiedTransfer(patched))
                                    effective.setByteBuffer("csd-0", java.nio.ByteBuffer.wrap(patched))
                                    effective.setInteger(MediaFormat.KEY_COLOR_TRANSFER, 0)
                                    report.put("avcSpsTransferPatchedToUnspecified", true)
                                }
                                track = muxer.addTrack(effective)
                                muxer.start(); muxerStarted = true
                            }
                            index >= 0 -> {
                                try {
                                    if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                                        check(muxerStarted)
                                        assertTrue("Strictly increasing encoder timestamps", info.presentationTimeUs > previousPts)
                                        previousPts = info.presentationTimeUs
                                        val buffer = requireNotNull(codec.getOutputBuffer(index))
                                        buffer.position(info.offset); buffer.limit(info.offset + info.size)
                                        muxer.writeSampleData(track, buffer, info)
                                        samples++
                                    }
                                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) eos = true
                                } finally { codec.releaseOutputBuffer(index, false) }
                            }
                        }
                    }
                }
                repeat(48) { frame ->
                    drain(0)
                    renderer.drawLinearRamp(frame * 1_000_000_000L / 24)
                    drain(10_000)
                }
                codec.signalEndOfInputStream()
                val deadline = android.os.SystemClock.elapsedRealtime() + 10_000
                while (!eos && android.os.SystemClock.elapsedRealtime() < deadline) drain(10_000)
                assertTrue("Encoder must emit EOS", eos)
                assertEquals(48, samples)
                report.put("samples", samples).put("eos", eos)
                renderer.close(); renderer = null
                codec.stop(); codecStarted = false
                muxer.stop(); muxerStarted = false
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(file.absolutePath)
                    val frame = requireNotNull(retriever.getFrameAtTime(1_000_000,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC))
                    try {
                        val patches = JSONArray()
                        for (fraction in listOf(0.01, 0.05, 0.18, 0.5, 0.9)) {
                            val x = (fraction * frame.width).toInt()
                            val pixel = frame.getPixel(x, frame.height / 2)
                            val measured = ((pixel shr 16) and 255) / 255.0
                            val expected = IrisLogTransfer.encode((x + 0.5) / frame.width)
                            patches.put(JSONObject().put("x", x).put("expected", expected).put("redNormalized", measured))
                            assertEquals("Encoded/decoded patch at $fraction for $mime", expected, measured, 0.035)
                        }
                        report.put("decodedPatches", patches)
                    } finally { frame.recycle() }
                } finally { retriever.release() }
                reports.put(report)
                File(context.filesDir, "encoded-log-ramp.json").writeText(JSONObject()
                    .put("experiments", reports).put("limitations",
                        "8-bit synthetic GPU/encoder proof only. No camera, audio, 10-bit, sustained recording or proven custom-transfer bitstream/container signaling.")
                    .toString(2))
            } finally {
                renderer?.close()
                if (codecStarted) runCatching { codec.stop() }
                codec.release()
                surface?.release()
                if (muxerStarted) runCatching { muxer.stop() }
                muxer.release()
            }
        }
    }
}
