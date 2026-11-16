package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.MediaShareUris
import com.example.camera.LogOutputCommit
import com.example.camera.LogRecoveryFiles
import java.io.File
import java.io.IOException
import java.io.OutputStream
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

// FileProvider canonical-path containment uses Android/Linux separators. Exercise the real
// provider on Android rather than substituting a fake path strategy on a Windows host.
@RunWith(AndroidJUnit4::class)
class MediaShareUrisTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun mediaStoreContentUrisAreUnchanged() {
        val value = "content://media/external/video/media/7"
        assertEquals(value, MediaShareUris.resolve(context, value).toString())
    }

    @Test fun completedArchiveUsesRestrictedProvider() {
        val archive = File(context.filesDir, "raw_sequences/share_test.zip")
        archive.parentFile!!.mkdirs()
        archive.writeText("fixture")
        try {
            val uri = MediaShareUris.resolve(context, archive.toURI().toString())
            assertEquals("content", uri.scheme)
            assertEquals("${context.packageName}.media", uri.authority)
            assertTrue(uri.path!!.startsWith("/raw_sequences/"))
        } finally { archive.delete() }
    }

    @Test fun outsideAndIncompleteFilesCannotBeShared() {
        // Linux must traverse the intermediate directory even when the next segment is '..'.
        File(context.filesDir, "raw_sequences").mkdirs()
        for (name in listOf("private.zip", "raw_sequences/../escape.zip", "raw_sequences/draft.zip.part")) {
            val file = File(context.filesDir, name)
            file.parentFile!!.mkdirs()
            file.writeText("fixture")
            try {
                assertThrows(IllegalArgumentException::class.java) {
                    MediaShareUris.resolve(context, file.toURI().toString())
                }
            } finally { file.delete() }
        }
    }

    @Test fun recoveryOriginalUsesReadOnlyProviderWithoutRemovingTheSource() {
        val name = "IRISI_20261116_223928.mp4.999999.original.mp4"
        val original = File(context.filesDir, "log_recovery/$name")
        original.parentFile!!.mkdirs()
        check(!original.exists()) { "Refusing to overwrite an existing recovery original" }
        original.writeText("recovery fixture")
        try {
            val uri = MediaShareUris.recoveryOriginal(context, name)
            assertTrue(uri.path!!.startsWith("/log_recovery/"))
            assertEquals("${context.packageName}.media", uri.authority)
            assertEquals("recovery fixture", context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() })
            assertEquals("recovery fixture", original.readText())
            assertThrows(IllegalArgumentException::class.java) { MediaShareUris.recoveryOriginal(context, "../$name") }
            assertThrows(IllegalArgumentException::class.java) { MediaShareUris.recoveryOriginal(context, "$name.part") }
        } finally { original.delete() }
    }

    @Test fun interruptedReplacementRemainsDiscoverableAndReadableThroughTheProvider() {
        val provider = File.createTempFile("recovery-provider-", ".mp4", context.cacheDir)
        val candidate = File.createTempFile("recovery-candidate-", ".mp4", context.cacheDir)
        val sidecar = File(context.cacheDir, "${candidate.name}.json")
        val name = "IRISI_20261116_223928.mp4.${System.nanoTime()}.original.mp4"
        val directory = File(context.filesDir, "log_recovery")
        val backup = File(directory, name)
        val original = "retained bytes before simulated provider interruption".toByteArray()
        check(!backup.exists())
        provider.writeBytes(original)
        candidate.writeText("replacement")
        try {
            assertThrows(IllegalStateException::class.java) {
                LogOutputCommit.commit(candidate, backup, sidecar, "grading",
                    openSource = { provider.inputStream() },
                    openDestination = {
                        provider.writeBytes(byteArrayOf())
                        object : OutputStream() {
                            override fun write(value: Int) = throw IOException("Simulated provider interruption")
                        }
                    })
            }
            assertTrue(LogRecoveryFiles.list(directory).any { it.fileName == name })
            assertFalse(sidecar.exists())
            val uri = MediaShareUris.recoveryOriginal(context, name)
            val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
            assertArrayEquals(original, bytes)
            assertArrayEquals(original, backup.readBytes())
        } finally {
            // Only fixtures created by this test, never other recoveries or camera media.
            provider.delete(); candidate.delete(); backup.delete(); sidecar.delete()
        }
    }
}
