package com.example.camera

import java.io.File
import java.io.IOException

data class LogRecoveryClip(
    val fileName: String,
    val originalDisplayName: String,
    val bytes: Long,
    val modifiedAt: Long
)

/** Read-only discovery of complete retained originals. Never promotes .part files or removes media. */
internal object LogRecoveryFiles {
    private val namePattern = Regex("^(IRISI_[0-9]{8}_[0-9]{6}\\.mp4)\\.-?[0-9]+\\.original\\.mp4$")

    fun list(directory: File): List<LogRecoveryClip> {
        if (!directory.exists()) return emptyList()
        val files = directory.listFiles() ?: throw IOException("Could not read retained LOG originals")
        return files.mapNotNull { file ->
            try {
                val original = requireCompleted(directory, file.name)
                LogRecoveryClip(file.name, namePattern.matchEntire(file.name)!!.groupValues[1],
                    original.length(), original.lastModified())
            } catch (_: IllegalArgumentException) { null }
        }.sortedWith(compareByDescending<LogRecoveryClip> { it.modifiedAt }.thenBy { it.fileName })
    }

    fun requireCompleted(directory: File, fileName: String): File {
        require(namePattern.matches(fileName)) { "Not a retained LOG original" }
        val root = directory.canonicalFile
        val file = File(root, fileName).canonicalFile
        require(file.parentFile == root && file.name == fileName && file.isFile && file.length() > 0) {
            "Recovery original is missing, empty or outside its directory"
        }
        return file
    }
}
