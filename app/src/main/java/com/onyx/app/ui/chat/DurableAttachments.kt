package com.onyx.app.ui.chat

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID

/**
 * **Le stockage privé durable des pièces jointes** — le pendant du « dossier de l'app » de
 * WhatsApp.
 *
 * À l'instant où une pièce jointe est **choisie** (photothèque, fichiers, presse-papiers) ou
 * **créée** (appareil photo), ses octets sont copiés ici, sous `filesDir` — jamais `cacheDir`.
 * C'est cette copie qui est portée par la pièce jointe, sous la forme d'un `file://` : elle
 * survit à la fermeture de l'app (`am force-stop`), au redémarrage du téléphone et au nettoyage du
 * cache par le système.
 *
 * Trois raisons, mesurées sur les captures du propriétaire :
 *
 *  * la photo prise par l'appareil était écrite dans `cacheDir/photos` : après nettoyage, la bulle
 *    disait « Illisible » (`MessageRow` décode `attachment.uri`) ;
 *  * une pièce choisie ne gardait que l'URI du sélecteur (`OpenMultipleDocuments`) : son
 *    autorisation de lecture est **temporaire**, liée au processus, et l'onglet Fichiers finissait
 *    par afficher « Indisponible » ;
 *  * le serveur était la source de vérité : il ne doit plus l'être. Il est une synchronisation ;
 *    ce que l'app a, elle le garde.
 *
 * Une source déjà dans le stockage privé n'est pas recopiée (une prise de vue de l'appareil, un
 * fichier déjà adopté) : la copie n'a lieu que quand elle est nécessaire.
 */
object DurableAttachments {

    /** Le dossier des copies durables, sous `filesDir`. */
    const val FOLDER = "attachments"

    fun folder(context: Context): File = File(context.filesDir, FOLDER).apply { mkdirs() }

    /**
     * Copie le contenu de [uri] dans le stockage privé durable, sous son nom (préfixé d'un
     * identifiant pour qu'un même nom venu de deux sources ne s'écrase pas).
     *
     * Rend le fichier copié, ou `null` quand la source ne répond plus (autorisation expirée,
     * fichier retiré de l'appareil) : l'appelant le dit, jamais ne l'invente.
     */
    fun copyIn(context: Context, uri: Uri, name: String): File? = runCatching {
        // Déjà chez nous : ni recopie, ni doublon (une prise de vue de l'appareil, un fichier
        // adopté lors d'une ouverture précédente).
        ownFile(context, uri)?.let { existing -> if (existing.length() > 0L) return existing }

        val target = File(folder(context), "${UUID.randomUUID()}-${safeName(name)}")
        val copied = context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } != null
        // Une copie vide n'est pas un fichier : on ne garde pas un lecteur noir.
        if (!copied || !target.exists() || target.length() <= 0L) {
            target.delete()
            return null
        }
        target
    }.getOrNull()

    /**
     * L'URI durable d'une copie : un `file://`, la seule forme qu'une pièce jointe porte.
     *
     * Elle ne dépend d'aucune autorisation, d'aucun fournisseur, et d'aucun cache.
     */
    fun durableUri(file: File): Uri = Uri.fromFile(file)

    /** Vrai quand le fichier est bien dans le stockage privé durable — la garantie de la maison. */
    fun isDurable(file: File): Boolean = AttachmentPathPolicy.isDurable(Uri.fromFile(file).toString())

    /**
     * Le fichier du stockage privé que [uri] désigne déjà, ou `null`.
     *
     * Deux formes : un `file://` (la prise de vue rendue par le FileProvider après copie) et un
     * `content://<pkg>.files/…` du FileProvider de l'app — dont le chemin, sous l'autorité de
     * l'app, est le chemin relatif sous `filesDir`.
     */
    private fun ownFile(context: Context, uri: Uri): File? {
        val path = when {
            uri.scheme == "file" -> uri.path
            uri.scheme == "content" && uri.authority == "${context.packageName}.files" ->
                uri.path?.trimStart('/')?.let { relative -> File(context.filesDir, relative).absolutePath }
            else -> null
        } ?: return null
        val file = File(path)
        return file.takeIf { it.exists() && !it.absolutePath.contains("/cache/") }
    }

    /** Le nom du fichier, sans ce qui ouvrirait un autre dossier, et jamais vide. */
    private fun safeName(name: String): String =
        name.replace('/', '_').replace('\\', '_').takeIf { it.isNotBlank() } ?: "file"
}

/**
 * L'URI à confier à une **autre app** (« ouvrir avec », partage).
 *
 * Une copie durable est un `file://` : depuis Android 7, la confier telle quelle à une autre app
 * lève `FileUriExposedException` et l'appui ne fait rien. Elle passe donc par le FileProvider de
 * l'app, qui a le droit de la partager (`file_paths.xml` déclare `files-path`).
 */
fun shareableUri(context: Context, uri: Uri): Uri =
    if (uri.scheme == "file") {
        runCatching {
            androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.files",
                File(uri.path ?: return@runCatching uri),
            )
        }.getOrDefault(uri)
    } else {
        uri
    }
