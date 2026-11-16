package com.example

import android.content.Context
import android.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.local.MediaShareUris
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipFile
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeNotNull
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in read-only validation of an existing sequence; never captures or deletes media. */
@RunWith(AndroidJUnit4::class)
class RawSequenceOutputInspectionTest {
    @Test fun inspectCompletedSequence() {
        val name = InstrumentationRegistry.getArguments().getString("inspectRawSequence")
        assumeNotNull(name)
        require(name!!.matches(Regex("IRISI_RAW_[0-9]+")))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = File(context.filesDir, "raw_sequences")
        val directory = File(root, name)
        val archive = File(root, "$name.zip")
        assertTrue("Completed archive must exist", archive.isFile)
        assertFalse("Archive must not still be finalizing", File(root, "$name.zip.part").exists())
        val manifestFile = File(directory, "manifest.json")
        val manifest = JSONObject(manifestFile.readText())
        assertEquals("DNG_SEQUENCE", manifest.getString("format"))
        assertTrue("Capture must have no recorder error", manifest.isNull("error"))
        val frames = manifest.getInt("frames")
        assertTrue(frames in 1..60)
        val metadata = manifest.getJSONArray("frameMetadata")
        assertEquals(frames, metadata.length())
        val expected = mutableSetOf("$name/manifest.json")
        val geometry = JSONArray()
        var previousTimestamp = -1L
        for (index in 0 until frames) {
            val frame = metadata.getJSONObject(index)
            val filename = frame.getString("file")
            assertEquals("frame_%04d.dng".format(java.util.Locale.US, index), filename)
            val file = File(directory, filename)
            assertTrue("DNG must contain data", file.length() > 0)
            val timestamp = frame.getLong("sensorTimestampNs")
            assertTrue("Sensor timestamps must increase", timestamp > previousTimestamp)
            previousTimestamp = timestamp
            val exif = ExifInterface(file.absolutePath)
            val width = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0)
            val height = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0)
            assertTrue("DNG geometry must be readable", width > 0 && height > 0)
            geometry.put(JSONObject().put("file", filename).put("width", width).put("height", height))
            expected.add("$name/$filename")
        }
        ZipFile(archive).use { zip ->
            val entries = zip.entries().toList()
            assertEquals("Archive must contain exactly its manifest and completed frames", expected, entries.map { it.name }.toSet())
            assertEquals(expected.size, entries.size)
            val buffer = ByteArray(64 * 1024)
            for (entry in entries) {
                val crc = CRC32()
                var bytes = 0L
                zip.getInputStream(entry).use { input ->
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        crc.update(buffer, 0, count)
                        bytes += count
                    }
                }
                assertEquals("ZIP entry length: ${entry.name}", entry.size, bytes)
                assertEquals("ZIP entry CRC: ${entry.name}", entry.crc, crc.value)
            }
            assertEquals(manifestFile.readText(), zip.getInputStream(zip.getEntry("$name/manifest.json")).bufferedReader().use { it.readText() })
        }
        val shareUri = MediaShareUris.resolve(context, archive.toURI().toString())
        assertEquals("content", shareUri.scheme)
        context.contentResolver.openInputStream(shareUri)!!.use {
            assertEquals('P'.code, it.read())
            assertEquals('K'.code, it.read())
        }
        val report = JSONObject().put("sequence", name).put("manifest", manifest)
            .put("geometry", geometry).put("archiveBytes", archive.length()).put("crcVerified", true)
            .put("shareUri", shareUri.toString())
        File(context.filesDir, "raw-sequence-inspection.json").writeText(report.toString(2))
    }
}
