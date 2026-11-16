package com.example.camera

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/** Provider replacement is not atomic: retain a complete original until read-back and sidecar commit. */
internal object LogOutputCommit {
    fun commit(
        candidate: File,
        backup: File,
        sidecar: File,
        sidecarText: String,
        openSource: () -> InputStream,
        openDestination: () -> OutputStream
    ) {
        require(candidate.isFile && candidate.length() > 0) { "Empty LOG candidate" }
        require(!backup.exists() && !sidecar.exists()) { "LOG recovery/sidecar already exists" }
        check(backup.parentFile!!.isDirectory || backup.parentFile!!.mkdirs())
        check(sidecar.parentFile!!.isDirectory || sidecar.parentFile!!.mkdirs())
        val backupPart = File(backup.parentFile, "${backup.name}.part")
        val sidecarPart = File(sidecar.parentFile, "${sidecar.name}.part")
        require(!backupPart.exists() && !sidecarPart.exists()) { "LOG commit already in progress" }
        try {
            // Fail before touching the provider if sidecar or recovery storage is unavailable.
            sidecarPart.writeText(sidecarText)
            openSource().use { input -> backupPart.outputStream().use { input.copyTo(it) } }
            check(backupPart.length() > 0 && backupPart.renameTo(backup)) { "Could not retain LOG original" }
            val expected = candidate.inputStream().use(::digest)
            openDestination().use { output -> candidate.inputStream().use { it.copyTo(output) } }
            val actual = openSource().use(::digest)
            check(expected.contentEquals(actual)) { "LOG replacement content verification failed" }
            check(sidecarPart.renameTo(sidecar)) { "Could not commit LOG grading sidecar" }
            backup.delete() // Only our original, after verified replacement and sidecar publication.
        } catch (error: Exception) {
            val recovery = if (backup.isFile) "Original retained at ${backup.absolutePath}"
                else "Original provider clip was not replaced"
            throw IllegalStateException("$recovery: ${error.message}", error)
        } finally {
            backupPart.delete()
            sidecarPart.delete()
        }
    }

    private fun digest(input: InputStream): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val size = input.read(buffer)
            if (size < 0) break
            digest.update(buffer, 0, size)
        }
        return digest.digest()
    }
}
