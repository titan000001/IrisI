package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.local.CapturedMediaEntity
import com.example.data.local.MediaShareIntents
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class MediaShareIntentsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun batchMixingPrivateDngArchiveAndMediaStoreGrantsBothStreams() {
        val root = File(context.filesDir, "raw_sequences").apply { mkdirs() }
        val archive = File.createTempFile("share-intent-", ".zip", root).apply { writeText("fixture") }
        try {
            val video = "content://media/external/video/media/7"
            val intent = MediaShareIntents.create(context, listOf(item(archive.toURI().toString(), "DNG SEQ 2F"), item(video, "MP4")))
            assertEquals(Intent.ACTION_SEND_MULTIPLE, intent.action)
            assertEquals("*/*", intent.type)
            val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)!!
            assertEquals(2, uris.size)
            assertTrue(uris.all { it.scheme == "content" })
            assertEquals("${context.packageName}.media", uris.first().authority)
            assertEquals(video, uris.last().toString())
            assertEquals(2, intent.clipData!!.itemCount)
            assertEquals(uris, (0 until intent.clipData!!.itemCount).map { intent.clipData!!.getItemAt(it).uri })
            assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            assertEquals(0, intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            assertEquals("fixture", context.contentResolver.openInputStream(uris.first())!!.bufferedReader().use { it.readText() })
            assertTrue(archive.exists())
        } finally { archive.delete() }
    }

    @Test fun repeatedSelectionsBecomeASingleStreamAndPrivateFilesFailClosed() {
        val video = item("content://media/external/video/media/7", "MP4")
        val intent = MediaShareIntents.create(context, listOf(video, video))
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("video/mp4", intent.type)
        assertEquals(1, intent.clipData!!.itemCount)
        assertThrows(IllegalArgumentException::class.java) {
            MediaShareIntents.create(context, listOf(video, item("file:///data/private.zip", "DNG SEQ")))
        }
        assertThrows(IllegalArgumentException::class.java) { MediaShareIntents.create(context, emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { MediaShareIntents.create(context, listOf(item("", "MP4"))) }
    }

    private fun item(uri: String, badge: String) = CapturedMediaEntity(
        uriString = uri, displayName = "fixture", isVideo = true, formatBadge = badge,
        resolutionLabel = "", lensLabel = "", iso = 100, shutterLabel = "",
        logProfile = "", filmPreset = "", stabilizationMode = ""
    )
}
