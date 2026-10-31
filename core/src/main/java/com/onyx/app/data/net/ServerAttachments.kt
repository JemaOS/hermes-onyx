package com.onyx.app.data.net

import com.onyx.app.localization.OnyxLocalization
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Une pièce jointe lue sur le téléphone, prête à être déposée sur le serveur. */
data class ServerAttachment(val name: String, val bytes: ByteArray, val isImage: Boolean)
data class PlacedServerAttachment(val source: ServerAttachment, val path: String)

fun serverAttachmentUploadName(stamp: String, copy: Int, filename: String): String =
    if (copy == 1) "$stamp $filename" else "$stamp $copy $filename"

/** `ServerFiles.place` : dépôt daté dans « Onyx attachments », sans écraser un fichier. */
class ServerAttachments(private val files: ServeFilesApi) {
    suspend fun place(attachments: List<ServerAttachment>, now: Date = Date()): List<PlacedServerAttachment> {
        if (attachments.isEmpty()) return emptyList()
        val home = files.folderPath(null)
        val folder = serverPathJoin(home, "Onyx attachments")
        try {
            files.makeFolder(folder)
        } catch (_: Exception) {
            // Le dossier existe déjà : la lecture résout aussi une éventuelle racine canonicalisée.
            files.folderPath(folder)
        }
        val stamp = SimpleDateFormat("yyyy-MM-dd HHmmss", Locale.US).format(now)
        return attachments.map { attachment ->
            var placed: PlacedServerAttachment? = null
            for (copy in 1..9) {
                val name = serverAttachmentUploadName(stamp, copy, attachment.name)
                try {
                    files.upload(destinationFolder = folder, name = name, bytes = attachment.bytes, overwrite = false)
                    placed = PlacedServerAttachment(attachment, serverPathJoin(folder, name))
                    break
                } catch (error: TransportError.Http) {
                    if (error.status != 409 || copy == 9) throw error
                }
            }
            placed ?: throw TransportError.Malformed(OnyxLocalization["The attachment couldn't be uploaded."])
        }
    }
}
