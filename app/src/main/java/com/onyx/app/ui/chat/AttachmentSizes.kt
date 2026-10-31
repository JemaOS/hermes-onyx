package com.onyx.app.ui.chat

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

/**
 * Le poids d'une pièce jointe, et l'examen des limites **avant** qu'elle n'entre dans le message.
 *
 * C'est ce qui manquait : le compositeur ne vérifiait que ce qu'on lui **déposait** (le glisser-
 * déposer d'un bureau), et il le vérifiait après coup — la bande des pièces jointes affichait alors
 * un refus pendant que la pièce était déjà là. Choisi par la photothèque, l'appareil photo, les
 * fichiers ou le presse-papiers, un fichier de 300 Mo traversait l'app : lu entier en mémoire,
 * encodé en data URL, envoyé. Il est refusé ici, tout de suite, avec son poids et le plafond.
 *
 * Depuis le lot des pièces durables, le poids se lit **sur la copie** (`filesDir`) : c'est la
 * seule mesure qui reste vraie une fois l'app fermée.
 */
internal class AttachmentAdmission(
    val accepted: List<PendingAttachment>,
    val problem: String?,
    /** Ce qui n'a **pas pu être copié** (source disparue) — le dit aussi, jamais en silence. */
    val refused: Int = 0,
)

/** Le poids d'un fichier du stockage privé, lu sur la copie — exact, et sans fournisseur. */
fun durableFileLength(uri: Uri): Long? =
    if (uri.scheme == "file") runCatching { File(uri.path ?: return null).length().takeIf { it > 0L } }.getOrNull()
    else null

/** Le poids annoncé par le fournisseur (`OpenableColumns.SIZE`), quand il le donne. */
fun resolveAttachmentBytes(resolver: ContentResolver, uri: Uri): Long? = runCatching {
    resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        val column = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (cursor.moveToFirst() && column >= 0) cursor.getLong(column).takeIf { it >= 0 } else null
    }
}.getOrNull()

/**
 * Le poids mesuré quand le fournisseur ne le dit pas : on compte les octets, et on **s'arrête au
 * plafond** — la réponse est « trop gros » sans lire les 300 Mo du fichier.
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

/** Le poids d'une pièce jointe : la copie durable d'abord, sinon la taille annoncée, sinon mesurée. */
fun attachmentBytes(resolver: ContentResolver, uri: Uri): Long? =
    durableFileLength(uri) ?: resolveAttachmentBytes(resolver, uri) ?: countAttachmentBytes(resolver, uri)

/**
 * Passe les pièces jointes choisies à l'examen : celles qui tiennent entrent, les autres sont
 * refusées avec la phrase qui le dit (le premier refus se dit, la suite se tait — on n'empile pas
 * quatre messages pour quatre fichiers).
 *
 * [existing] : ce qui est déjà au message, pour le nombre et le poids cumulé.
 *
 * C'est ici que la copie durable a lieu ([pendingAttachmentsFromUris]) : une source qui ne répond
 * plus ne rend pas de pièce jointe, et sa disparition se **dit** ([AttachmentAdmission.refused]).
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
    // Une pièce qui n'a pas pu être copiée ne se tait pas : c'est le cas « l'autorisation de la
    // galerie a expiré », et le geste qui peut aboutir est de la renvoyer depuis l'appareil.
    val lost = uris.size - candidates.size
    if (lost > 0 && problem == null) {
        problem = com.onyx.app.localization.OnyxLocalization[
            "This file is no longer on this device. Send it again from the device."
        ]
    }
    return AttachmentAdmission(accepted, problem, refused = lost)
}
