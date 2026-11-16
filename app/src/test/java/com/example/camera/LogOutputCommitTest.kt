package com.example.camera

import java.io.File
import java.io.IOException
import java.io.OutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LogOutputCommitTest {
    @get:Rule val temp = TemporaryFolder()
    private val original = byteArrayOf(1, 2, 3, 4)
    private val replacement = byteArrayOf(5, 6, 7, 8)

    private fun exercise(write: (File) -> OutputStream = { it.outputStream() }, readBackFails: Boolean = false) {
        val provider = temp.newFile("provider.mp4").apply { writeBytes(original) }
        val candidate = temp.newFile("candidate.mp4").apply { writeBytes(replacement) }
        val backup = File(temp.root, "recovery/original.mp4")
        val sidecar = File(temp.root, "sidecars/clip.json")
        var reads = 0
        try {
            LogOutputCommit.commit(candidate, backup, sidecar, "grading",
                openSource = {
                    reads++
                    if (readBackFails && reads == 2) throw IOException("read-back unavailable")
                    provider.inputStream()
                },
                openDestination = { write(provider) })
            assertArrayEquals(replacement, provider.readBytes())
            assertEquals("grading", sidecar.readText())
            assertFalse(backup.exists())
        } catch (error: IllegalStateException) {
            assertArrayEquals(original, backup.readBytes())
            assertFalse("Failure must not publish a success sidecar", sidecar.exists())
            assertTrue(error.message!!.contains(backup.absolutePath))
            throw error
        } finally {
            assertFalse(File(backup.parentFile, "${backup.name}.part").exists())
            assertFalse(File(sidecar.parentFile, "${sidecar.name}.part").exists())
            assertArrayEquals(replacement, candidate.readBytes())
        }
    }

    @Test fun successVerifiesReplacementBeforePublishingSidecar() = exercise()

    @Test fun sameLengthCorruptionKeepsOriginalAndNoSidecar() {
        assertThrows(IllegalStateException::class.java) {
            exercise(write = { file -> object : OutputStream() {
                override fun write(value: Int) { file.appendBytes(byteArrayOf(value.toByte())) }
                override fun write(bytes: ByteArray, offset: Int, length: Int) {
                    file.writeBytes(ByteArray(length) { 9 })
                }
            } })
        }
    }

    @Test fun interruptedProviderWriteKeepsOriginalAndNoSidecar() {
        assertThrows(IllegalStateException::class.java) {
            exercise(write = { file ->
                file.writeBytes(byteArrayOf())
                object : OutputStream() {
                    override fun write(value: Int) = throw IOException("disk full")
                }
            })
        }
    }

    @Test fun readBackFailureKeepsOriginalAndNoSidecar() {
        assertThrows(IllegalStateException::class.java) { exercise(readBackFails = true) }
    }

    @Test fun recoveryFailureDoesNotOpenDestination() {
        val candidate = temp.newFile().apply { writeBytes(replacement) }
        val backup = File(temp.root, "recovery/original.mp4")
        val sidecar = File(temp.root, "sidecars/clip.json")
        var openedDestination = false
        assertThrows(IllegalStateException::class.java) {
            LogOutputCommit.commit(candidate, backup, sidecar, "grading",
                openSource = { throw IOException("source unavailable") },
                openDestination = { openedDestination = true; candidate.outputStream() })
        }
        assertFalse(openedDestination)
        assertFalse(sidecar.exists())
        assertFalse(backup.exists())
    }
}
