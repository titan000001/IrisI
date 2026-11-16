package com.example.data.local

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri

/** Resolve every private archive before sharing; grants must cover every selected URI. */
internal object MediaShareIntents {
    fun create(context: Context, items: List<CapturedMediaEntity>): Intent {
        require(items.isNotEmpty()) { "Select media to share" }
        val streams = items.map { item ->
            require(item.uriString.isNotBlank()) { "Selected media has no accessible output" }
            MediaShareUris.resolve(context, item.uriString)
        }.distinct()
        val types = items.map { mimeType(it) }.distinct()
        val mime = when {
            types.size == 1 -> types.single()
            types.all { it.startsWith("image/") } -> "image/*"
            types.all { it.startsWith("video/") } -> "video/*"
            else -> "*/*"
        }
        return Intent(if (streams.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
            type = mime
            if (streams.size == 1) putExtra(Intent.EXTRA_STREAM, streams.single())
            else putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(streams))
            clipData = ClipData.newUri(context.contentResolver, "IrisI media", streams.first()).apply {
                streams.drop(1).forEach { addItem(ClipData.Item(it)) }
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun mimeType(item: CapturedMediaEntity): String = when {
        item.formatBadge.startsWith("DNG SEQ") -> "application/zip"
        item.isVideo -> "video/mp4"
        Uri.parse(item.uriString).lastPathSegment?.endsWith(".dng", ignoreCase = true) == true ||
            item.formatBadge.equals("DNG", ignoreCase = true) -> "image/x-adobe-dng"
        else -> "image/jpeg"
    }
}
