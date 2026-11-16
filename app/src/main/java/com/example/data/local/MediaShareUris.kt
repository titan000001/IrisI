package com.example.data.local

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.camera.LogRecoveryFiles
import java.io.File

/** Convert only completed private DNG archives to temporary grantable content URIs. */
internal object MediaShareUris {
    /** A separate, narrow entry point: only complete retained LOG originals, never arbitrary files. */
    fun recoveryOriginal(context: Context, fileName: String): Uri {
        val original = LogRecoveryFiles.requireCompleted(File(context.filesDir, "log_recovery"), fileName)
        return FileProvider.getUriForFile(context, "${context.packageName}.media", original)
    }

    fun resolve(context: Context, value: String): Uri {
        val uri = Uri.parse(value)
        if (uri.scheme == "content") return uri
        require(uri.scheme == "file") { "Unsupported media URI" }
        val file = File(requireNotNull(uri.path)).canonicalFile
        val root = File(context.filesDir, "raw_sequences").canonicalFile
        require(file.path.startsWith(root.path + File.separator) && file.isFile && file.extension == "zip") {
            "Only completed DNG archives can be shared"
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.media", file)
    }
}
