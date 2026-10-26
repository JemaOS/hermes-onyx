package com.onyx.app.ui.chat

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import java.util.UUID

/** Une pièce jointe choisie mais pas encore envoyée. */
data class PendingAttachment(
    val id: String,
    val uri: Uri,
    val name: String,
    val mimeType: String,
    val isImage: Boolean,
)

/** Résolution commune aux composers simple et groupe: nom et type réels du fournisseur. */
fun pendingAttachmentsFromUris(resolver: ContentResolver, uris: List<Uri>): List<PendingAttachment> =
    uris.map { uri ->
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        val name = runCatching {
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "file"
        PendingAttachment(
            id = UUID.randomUUID().toString(),
            uri = uri,
            name = name,
            mimeType = mime,
            isImage = mime.startsWith("image/"),
        )
    }
