package com.onyx.app.ui.chat

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

/**
 * The weight of an attachment, and the checking of limits **before** it enters the message.
 *
 * This is what was missing: the composer only checked what was **dropped onto** it (dragging
 * and dropping from a desktop), and it checked it afterwards — the attachment strip showed a
 * refusal while the item was already there. Chosen via the photo library, the camera, the files
 * or the clipboard, a 300 MB file went through the app: read whole into memory, encoded as a
 * data URL, sent. It is refused here, at once, with its weight and the cap.
 *
 * Since the durable batch, the weight is read **on the copy** (`filesDir`): the only measure
 * that stays true once the app is closed.
 */
internal class AttachmentAdmission(
    val accepted: List<PendingAttachment>,
    val problem: String?,
    /** What **could not be copied** (source gone) — also reported, never silently. */
    val refused: Int = 0,
)

/** The weight of a file in private storage, read on the copy — exact, and without a provider. */
fun durableFileLength(uri: Uri): Long? =
    if (uri.scheme == "file") runCatching { File(uri.path ?: return null).length().takeIf { it > 0L } }.getOrNull()
    else null

/** The weight announced by the provider (`OpenableColumns.SIZE`), when it gives it. */
fun resolveAttachmentBytes(resolver: ContentResolver, uri: Uri): Long? = runCatching {
    resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        val column = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (cursor.moveToFirst() && column >= 0) cursor.getLong(column).takeIf { it >= 0 } else null
    }
}.getOrNull()

/**
 * The weight measured when the provider does not give it: we count the bytes, and **stop at the
 * cap** — the answer is "too big" without reading the 300 MB of the file.
 */
fun countAttachmentBytes(
    resolver: ContentResolver,
    uri: Uri,
    cap: Long = AttachmentLimits.MAX_FILE_BYTES,
): Long? = runCatching {
    resolver.openInputStream(uri)?.use { input ->
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            total += read
            if (total > cap) return@runCatching total
        }
        total
    }
}.getOrNull()

/** The weight of an attachment: the durable copy first, else the announced size, else measured. */
fun attachmentBytes(resolver: ContentResolver, uri: Uri): Long? =
    durableFileLength(uri) ?: resolveAttachmentBytes(resolver, uri) ?: countAttachmentBytes(resolver, uri)

/**
 * Passes the chosen attachments to the check: those that fit go in, the others are refused with
 * the sentence that says so (the first refusal is reported, the rest stay quiet — we do not stack
 * four messages for four files).
 *
 * [existing]: what is already in the message, for the count and the cumulative weight.
 *
 * This is where the durable copy takes place ([pendingAttachmentsFromUris]): a source that no
 * longer answers yields no attachment, and its disappearance is **reported**
 * ([AttachmentAdmission.refused]).
 */
internal fun admitAttachments(
    context: Context,
    existing: List<PendingAttachment>,
    uris: List<Uri>,
): AttachmentAdmission {
    val resolver = context.contentResolver
    val candidates = pendingAttachmentsFromUris(context, uris)
    val accepted = mutableListOf<PendingAttachment>()
    var problem: String? = null
    var count = existing.size
    var bytes = existing.sumOf { attachmentBytes(resolver, it.uri) ?: 0L }
    candidates.forEach { candidate ->
        val size = attachmentBytes(resolver, candidate.uri)
        val refusal = AttachmentLimits.refusal(
            name = candidate.name,
            bytes = size,
            isText = !candidate.isImage,
            attached = count,
            attachedBytes = bytes,
        )
        if (refusal != null) {
            if (problem == null) problem = refusal
        } else {
            accepted += candidate
            count += 1
            bytes += size ?: 0L
        }
    }
    // An item that could not be copied is not kept quiet: this is the "the gallery permission
    // has expired" case, and the gesture that can succeed is to send it again from the device.
    val lost = uris.size - candidates.size
    if (lost > 0 && problem == null) {
        problem = com.onyx.app.localization.OnyxLocalization[
            "This file is no longer on this device. Send it again from the device."
        ]
    }
    return AttachmentAdmission(accepted, problem, refused = lost)
}
