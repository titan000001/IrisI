package com.example.camera

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer

/** Remuxes without re-encoding. Keeps a private recovery original if provider replacement fails. */
object LogVideoFinalizer {
    fun finalize(context: Context, source: Uri, name: String): File {
        val temporary = File.createTempFile("irisi-log-", ".mp4", context.cacheDir)
        val backup = File(context.filesDir, "log_recovery/${name}.${System.nanoTime()}.original.mp4")
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            extractor.setDataSource(context, source, null)
            muxer = MediaMuxer(temporary.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val tracks = IntArray(extractor.trackCount)
            var videoFound = false
            for (index in tracks.indices) {
                val format = extractor.getTrackFormat(index)
                if (format.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true) {
                    check(format.getString(MediaFormat.KEY_MIME) == "video/avc") {
                        "Custom LOG signaling currently requires AVC; device selected another codec"
                    }
                    val csd = requireNotNull(format.getByteBuffer("csd-0")).duplicate()
                    val patched = AvcLogSignaling.unspecifiedTransfer(ByteArray(csd.remaining()).also { csd.get(it) })
                    format.setByteBuffer("csd-0", ByteBuffer.wrap(patched))
                    format.setInteger(MediaFormat.KEY_COLOR_TRANSFER, 0)
                    if (format.containsKey(MediaFormat.KEY_ROTATION)) muxer.setOrientationHint(format.getInteger(MediaFormat.KEY_ROTATION))
                    videoFound = true
                }
                tracks[index] = muxer.addTrack(format)
                extractor.selectTrack(index)
            }
            check(videoFound)
            muxer.start()
            val buffer = ByteBuffer.allocateDirect(16 * 1024 * 1024)
            val info = android.media.MediaCodec.BufferInfo()
            while (extractor.sampleTrackIndex >= 0) {
                val index = extractor.sampleTrackIndex
                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                check(size in 0..buffer.capacity()) { "Encoded sample exceeds bounded remux buffer" }
                info.set(0, size, extractor.sampleTime, RemuxSampleFlags.toCodecFlags(extractor.sampleFlags))
                muxer.writeSampleData(tracks[index], buffer, info)
                if (!extractor.advance()) break
            }
            muxer.stop(); muxer.release(); muxer = null
            // Read back the remuxed SPS before replacing the source. Transfer value 2
            // is unspecified in the bitstream, not MediaFormat's unrelated integer enum.
            val checkExtractor = MediaExtractor()
            try {
                checkExtractor.setDataSource(temporary.absolutePath)
                val video = (0 until checkExtractor.trackCount).map { checkExtractor.getTrackFormat(it) }
                    .single { it.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true }
                val csd = requireNotNull(video.getByteBuffer("csd-0")).duplicate()
                val bytes = ByteArray(csd.remaining()).also { csd.get(it) }
                check(AvcLogSignaling.unspecifiedTransfer(bytes).contentEquals(bytes)) { "Muxer changed custom LOG signaling" }
            } finally { checkExtractor.release() }
            val sidecars = File(context.filesDir, "log_sidecars").apply { check(isDirectory || mkdirs()) }
            val sidecar = File(sidecars, "$name.log.json")
            val sidecarText = JSONObject().put("curve", IrisLogTransfer.ID)
                .put("encode", "ln(1+31*x)/ln(32)").put("decode", "(exp(y*ln(32))-1)/31")
                .put("source", "ISP SDR RGB, inverse Rec.709 then IrisI LOG; no restored clipped highlights")
                .put("bits", 8).put("gamut", "BT.709").put("transferSignaling", "unspecified")
                .put("clip", name).put("warning", "8-bit LOG has reduced grading latitude and banding risk").toString(2)
            LogOutputCommit.commit(temporary, backup, sidecar, sidecarText,
                openSource = { requireNotNull(context.contentResolver.openInputStream(source)) },
                openDestination = { requireNotNull(context.contentResolver.openOutputStream(source, "wt")) })
            return sidecar
        } finally {
            extractor.release()
            runCatching { muxer?.release() }
            temporary.delete()
        }
    }
}
