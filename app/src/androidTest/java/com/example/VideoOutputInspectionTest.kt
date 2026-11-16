package com.example

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.camera.HevcSequenceInfo
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeNotNull
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in inspection of a named, already recorded clip. Never captures or deletes user media. */
@RunWith(AndroidJUnit4::class)
class VideoOutputInspectionTest {
    @Test fun inspectRecordedTracks() {
        val args = InstrumentationRegistry.getArguments()
        val name = args.getString("inspectVideoName")
        assumeNotNull(name)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val id = context.contentResolver.query(collection, arrayOf(MediaStore.Video.Media._ID),
            "${MediaStore.Video.Media.DISPLAY_NAME} = ?", arrayOf(name!!), null)!!.use {
            assertTrue("Recorded clip must exist", it.moveToFirst())
            it.getLong(0)
        }
        val uri = android.content.ContentUris.withAppendedId(collection, id)
        val tracks = JSONArray()
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            var videoFound = false
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                val track = JSONObject().put("format", format.toString())
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                track.put("mime", mime)
                if (mime.startsWith("video/")) {
                    videoFound = true
                    val width = format.getInteger(MediaFormat.KEY_WIDTH)
                    val height = format.getInteger(MediaFormat.KEY_HEIGHT)
                    assertTrue(width > 0 && height > 0)
                    track.put("width", width).put("height", height)
                    // Missing tags stay unknown; do not infer LOG, HDR, or depth from a menu label.
                    for (key in listOf(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.KEY_COLOR_TRANSFER,
                        MediaFormat.KEY_COLOR_RANGE, MediaFormat.KEY_PROFILE, MediaFormat.KEY_LEVEL)) {
                        track.put(key, if (format.containsKey(key)) format.getInteger(key) else JSONObject.NULL)
                    }
                    args.getString("expectedVideoMime")?.let { expected ->
                        assertEquals("Encoded MIME must match the requested verification contract", expected, mime)
                    }
                    for ((argument, key) in mapOf(
                        "expectedVideoTransfer" to MediaFormat.KEY_COLOR_TRANSFER,
                        "expectedVideoStandard" to MediaFormat.KEY_COLOR_STANDARD,
                        "expectedVideoRange" to MediaFormat.KEY_COLOR_RANGE,
                        "expectedVideoProfile" to MediaFormat.KEY_PROFILE,
                        "expectedVideoWidth" to MediaFormat.KEY_WIDTH,
                        "expectedVideoHeight" to MediaFormat.KEY_HEIGHT
                    )) {
                        args.getString(argument)?.let { expected ->
                            assertTrue("Required encoded metadata is missing: $key", format.containsKey(key))
                            assertEquals("Encoded $key must match", expected.toInt(), format.getInteger(key))
                        }
                    }
                    if (mime == "video/hevc") {
                        val csd = requireNotNull(format.getByteBuffer("csd-0")).duplicate()
                        val sequence = HevcSequenceInfo.parseAnnexB(ByteArray(csd.remaining()).also { csd.get(it) })
                        track.put("spsLumaBits", sequence.lumaBits).put("spsChromaBits", sequence.chromaBits)
                        track.put("spsWidth", sequence.width).put("spsHeight", sequence.height)
                        assertEquals(width, sequence.width)
                        assertEquals(height, sequence.height)
                        args.getString("expectedVideoDepth")?.toInt()?.let { depth ->
                            assertEquals(depth, sequence.lumaBits)
                            assertEquals(depth, sequence.chromaBits)
                        }
                    } else {
                        assertNull("Depth assertion requires an implemented bitstream parser for $mime",
                            args.getString("expectedVideoDepth"))
                    }
                    extractor.selectTrack(index)
                    var samples = 0
                    var first = -1L
                    var last = -1L
                    while (extractor.sampleTrackIndex >= 0) {
                        val time = extractor.sampleTime
                        if (first < 0) first = time
                        last = time
                        samples++
                        if (!extractor.advance()) break
                    }
                    extractor.unselectTrack(index)
                    assertTrue("Video must contain samples", samples > 1)
                    track.put("samples", samples).put("firstTimestampUs", first)
                        .put("lastTimestampUs", last)
                    if (last > first) track.put("measuredFps", (samples - 1) * 1_000_000.0 / (last - first))
                    extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                }
                tracks.put(track)
            }
            assertTrue("Video track must exist", videoFound)
        } finally { extractor.release() }
        val decoded = JSONArray()
        if (args.getString("inspectDecodedFrames") == "true") {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                for (timeUs in listOf(500_000L, 2_000_000L, 4_000_000L)) {
                    // Some Pixel HDR decoder builds only expose sync frames through the retriever.
                    // Try the requested point first, then nearby sync-friendly points.
                    val frame = listOf(
                        MediaMetadataRetriever.OPTION_CLOSEST,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                    ).asSequence().mapNotNull { option ->
                        runCatching { retriever.getFrameAtTime(timeUs, option) }.getOrNull()
                    }.firstOrNull()
                    assertNotNull("Frame must decode at $timeUs (closest or sync)", frame)
                    requireNotNull(frame)
                    try {
                        var low = 255; var high = 0; var sum = 0L; var count = 0
                        for (y in 0 until frame.height step maxOf(1, frame.height / 64)) {
                            for (x in 0 until frame.width step maxOf(1, frame.width / 64)) {
                                val pixel = frame.getPixel(x, y)
                                val luma = (54 * ((pixel shr 16) and 255) + 183 * ((pixel shr 8) and 255) +
                                    19 * (pixel and 255)) / 256
                                low = minOf(low, luma); high = maxOf(high, luma); sum += luma; count++
                            }
                        }
                        decoded.put(JSONObject().put("timeUs", timeUs).put("width", frame.width)
                            .put("height", frame.height).put("minLuma8", low).put("maxLuma8", high)
                            .put("meanLuma8", sum.toDouble() / count))
                        // Scene-dependent; only require contrast for an explicitly requested visible-scene contract.
                        if (args.getString("requireNonblankFrames") == "true") {
                            assertTrue("Decoded scene must not be uniform/black", high - low >= 8 && high > 8)
                        }
                    } finally { frame.recycle() }
                }
            } finally { retriever.release() }
        }
        File(context.filesDir, "video-inspection.json").writeText(
            JSONObject().put("displayName", name).put("tracks", tracks).put("decodedFrames", decoded)
                .put("decodeNote", "Retriever RGB may be display-tone-mapped; not HDR colorimetry or source-precision proof.")
                .toString(2))
    }
}
