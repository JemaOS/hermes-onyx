package com.onyx.app.ui.chat

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import java.util.UUID

/**
 * Une pièce jointe choisie mais pas encore envoyée.
 *
 * **Sa [uri] est toujours la copie durable** ([DurableAttachments]) : un `file://` sous `filesDir`,
 * avec son [name], sa [size] en octets et son [mimeType]. Jamais le `content://` du sélecteur ni
 * un chemin du cache — c'est ce qui fait qu'une bulle reste lisible après fermeture de l'app.
 */
data class PendingAttachment(
    val id: String,
    val uri: Uri,
    val name: String,
    val mimeType: String,
    val isImage: Boolean,
    val size: Long = 0L,
)

/**
 * Résolution commune aux composers simple et groupe : nom, type et poids réels du fournisseur —
 * **puis la copie dans le stockage privé durable**, faite à cet instant précis.
 *
 * Une source qui ne répond plus (autorisation de galerie expirée, fichier retiré) ne produit pas
 * de pièce jointe : elle est écartée ici, et l'examen des limites le dit ([admitAttachments]).
 */
fun pendingAttachmentsFromUris(context: Context, uris: List<Uri>): List<PendingAttachment> =
    uris.mapNotNull { uri ->
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        val name = runCatching {
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "file"
        // **La copie d'abord.** Tant qu'elle n'existe pas, la pièce jointe n'existe pas : une URI
        // transitoire ne deviendra jamais un fichier durable, et la promettre serait le défaut.
        val durable = DurableAttachments.copyIn(context, uri, name) ?: return@mapNotNull null
        val durableUri = DurableAttachments.durableUri(durable)
        if (!AttachmentPathPolicy.isDurable(durableUri.toString())) return@mapNotNull null
        PendingAttachment(
            id = UUID.randomUUID().toString(),
            uri = durableUri,
            name = name,
            mimeType = mime,
            isImage = mime.startsWith("image/"),
            size = durable.length(),
        )
    }

/**
 * Prend l'autorisation **durable** quand le fournisseur en accorde une.
 *
 * `OpenMultipleDocuments` (comme `OpenDocument`) accorde une autorisation *persistable* : la
 * prendre fait qu'un `content://` continue de répondre après la mort du processus. Elle n'est
 * **pas** la garantie — la copie l'est — mais elle évite une relecture inutile, et elle est prise
 * ici, à l'instant où le sélecteur rend la main.
 */
fun takeDurablePermissions(context: Context, uris: List<Uri>) {
    uris.forEach { uri ->
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }
}
