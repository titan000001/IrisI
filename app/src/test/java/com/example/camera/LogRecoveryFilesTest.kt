package com.example.camera

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LogRecoveryFilesTest {
    @get:Rule val temp = TemporaryFolder()
    private val first = "IRISI_20261116_223928.mp4.123.original.mp4"
    private val second = "IRISI_20261116_223935.mp4.124.original.mp4"

    @Test fun missingDirectoryIsAnEmptyRecoveryQueue() {
        assertTrue(LogRecoveryFiles.list(File(temp.root, "missing")).isEmpty())
    }

    @Test fun onlyCompleteNonemptyOriginalsAreListedNewestFirst() {
        val old = temp.newFile(first).apply { writeText("original1"); setLastModified(1000) }
        val recent = temp.newFile(second).apply { writeText("original2"); setLastModified(2000) }
        temp.newFile("$first.part").writeText("incomplete")
        temp.newFile("random.mp4").writeText("unrelated")
        temp.newFile("IRISI_20261116_223942.mp4.125.original.mp4")
        temp.newFolder("IRISI_20261116_223949.mp4.126.original.mp4")
        val entries = LogRecoveryFiles.list(temp.root)
        assertEquals(listOf(second, first), entries.map { it.fileName })
        assertEquals("IRISI_20261116_223935.mp4", entries.first().originalDisplayName)
        assertEquals(9L, entries.first().bytes)
        assertEquals("original1", old.readText())
        assertEquals("original2", recent.readText())
    }

    @Test fun traversalPartialEmptyAndMissingCandidatesAreRejected() {
        temp.newFile(first).writeText("original")
        temp.newFile(second)
        for (candidate in listOf("../$first", "folder/$first", "$first.part", second,
            "IRISI_20261116_223942.mp4.125.original.mp4", "file:///$first")) {
            assertThrows(candidate, IllegalArgumentException::class.java) {
                LogRecoveryFiles.requireCompleted(temp.root, candidate)
            }
        }
        assertEquals("original", LogRecoveryFiles.requireCompleted(temp.root, first).readText())
    }

    @Test fun failedProviderCommitBecomesDiscoverableWithoutLosingBytes() {
        val provider = temp.newFile("provider.mp4").apply { writeText("retained original") }
        val candidate = temp.newFile("candidate.mp4").apply { writeText("replacement") }
        val directory = File(temp.root, "log_recovery")
        assertThrows(IllegalStateException::class.java) {
            LogOutputCommit.commit(candidate, File(directory, first), File(temp.root, "sidecar/clip.json"), "grading",
                openSource = { provider.inputStream() },
                openDestination = { throw java.io.IOException("provider unavailable") })
        }
        val retained = LogRecoveryFiles.list(directory).single()
        assertEquals(first, retained.fileName)
        assertEquals("retained original", LogRecoveryFiles.requireCompleted(directory, retained.fileName).readText())
    }
}
