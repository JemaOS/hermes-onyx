package com.onyx.app.ui.chat

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le verrou des pièces jointes durables.** Il mord sur la source, et il nomme le fichier et la
 * ligne fautifs — c'est la preuve rouge d'avant.
 *
 * Le défaut mesuré, sur les captures du propriétaire :
 *
 *  * dans le fil, deux bulles d'image portaient « Illisible » après fermeture de l'app : la photo
 *    prise par l'appareil était écrite dans `cacheDir`, que le système purge, et l'affichage se
 *    décodait depuis cette URI ;
 *  * dans l'onglet Fichiers, `1000187506.jpg` portait « Indisponible » et un refus 400 ;
 *  * une pièce jointe choisie n'était gardée que par une **URI temporaire** du sélecteur
 *    (`OpenMultipleDocuments`), dont l'autorisation meurt avec le processus.
 *
 * Le remède que ce verrou tient : **toute pièce jointe est copiée dans le stockage privé durable
 * de l'app** (`filesDir`, jamais `cacheDir`) à l'instant où elle est choisie ou créée, et c'est
 * cette copie — un `file://` — qui est portée par la pièce jointe, avec son nom, sa taille et son
 * type MIME. Le serveur devient une synchronisation, pas la source de vérité.
 */
class DurableAttachmentsLockTest {

    // -----------------------------------------------------------------------------------------
    // Les sources, lues telles qu'elles sont : app/ et core/
    // -----------------------------------------------------------------------------------------

    private val appRoot = File("src/main")
    private val coreRoot = File("../core/src/main")

    private fun sources(): List<File> =
        (listOf(appRoot, coreRoot)).filter { it.isDirectory }
            .flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }

    /** Les `chemin:ligne` où une expression apparaît — pour nommer le défaut, jamais le décrire. */
    private fun hits(expression: Regex): List<String> = sources().flatMap { file ->
        file.readLines().withIndex().mapNotNull { (index, line) ->
            if (expression.containsMatchIn(line)) "${file.path}:${index + 1}: ${line.trim()}" else null
        }
    }

    private fun text(path: String): String =
        File(path).readText().replace("\r\n", "\n")

    private val pendingAttachment = text("src/main/java/com/onyx/app/ui/chat/PendingAttachment.kt")
    private val chatScreen = text("src/main/java/com/onyx/app/ui/chat/ChatScreen.kt")
    private val cameraScreen = text("src/main/java/com/onyx/app/ui/chat/CameraScreen.kt")
    private val groupComposer = text("src/main/java/com/onyx/app/ui/bots/GroupComposer.kt")
    private val filesScreen = text("src/main/java/com/onyx/app/ui/files/FilesScreen.kt")
    private val filePaths = text("src/main/res/xml/file_paths.xml")

    // -----------------------------------------------------------------------------------------
    // (a) + (b) — rien de ce qui doit survivre n'est écrit dans le cache
    // -----------------------------------------------------------------------------------------

    @Test
    fun `aucune prise de vue n est ecrite dans le cache du systeme`() {
        val offenders = hits(Regex("""cacheDir\s*,\s*"photos""""))
        assertTrue(
            "une photo prise par l'appareil écrite dans `cacheDir` disparaît au nettoyage — " +
                "c'est l'« Illisible » du fil. Trouvé :\n" + offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    @Test
    fun `aucun fichier du serveur n est ecrit dans le cache du systeme`() {
        assertTrue(
            "FilesScreen.kt écrit le fichier téléchargé dans `context.cacheDir` : le lecteur " +
                "s'ouvre, puis le système purge et le fichier n'est plus là",
            !filesScreen.contains("File(context.cacheDir"),
        )
    }

    @Test
    fun `chaque prise de vue vise le stockage prive durable`() {
        val durable = Regex("""File\((context|applicationContext)\.filesDir,\s*"(photos|attachments)"""")
        listOf(
            "ChatScreen.kt" to chatScreen,
            "CameraScreen.kt" to cameraScreen,
            "GroupComposer.kt" to groupComposer,
        ).forEach { (name, source) ->
            assertTrue(
                "$name doit écrire sa prise sous `filesDir` (jamais le cache)",
                durable.containsMatchIn(source),
            )
        }
    }

    // -----------------------------------------------------------------------------------------
    // (c) — la pièce jointe porte une copie durable, pas une URI transitoire
    // -----------------------------------------------------------------------------------------

    @Test
    fun `la piece jointe est copiee dans le stockage prive a l instant du choix`() {
        assertTrue(
            "`pendingAttachmentsFromUris` garde l'URI du sélecteur telle quelle : son autorisation " +
                "meurt avec le processus, et la bulle devient « Illisible »",
            pendingAttachment.contains("DurableAttachments.copyIn"),
        )
        assertTrue(
            "et la pièce jointe doit porter le `file://` de la copie, jamais le `content://` d'origine",
            pendingAttachment.contains("DurableAttachments.durableUri"),
        )
    }

    @Test
    fun `la piece jointe enregistre son nom, sa taille et son type`() {
        assertTrue(
            "`PendingAttachment` doit porter `size` (le poids de la copie durable)",
            Regex("""val size:\s*Long""").containsMatchIn(pendingAttachment),
        )
        assertTrue(
            "`PendingAttachment` doit porter `mimeType` (le type annoncé par le fournisseur)",
            Regex("""val mimeType:\s*String""").containsMatchIn(pendingAttachment),
        )
        assertTrue(
            "`PendingAttachment` doit porter `name`",
            Regex("""val name:\s*String""").containsMatchIn(pendingAttachment),
        )
    }

    @Test
    fun `le stockage durable est sous filesDir, jamais sous cacheDir`() {
        val store = File("src/main/java/com/onyx/app/ui/chat/DurableAttachments.kt")
        assertTrue("le stockage durable existe", store.isFile)
        val source = store.readText()
        assertTrue(
            "la racine du stockage est `File(context.filesDir, \"attachments\")`",
            Regex("""File\(context\.filesDir,\s*(FOLDER|"attachments")\)""").containsMatchIn(source),
        )
        assertTrue(
            "et son code ne doit jamais lire `cacheDir` (le commentaire peut le nommer, pas le code)",
            !source.contains(".cacheDir") && !source.contains("cacheDir,"),
        )
    }

    // -----------------------------------------------------------------------------------------
    // L'autorisation durable du sélecteur, prise quand elle existe
    // -----------------------------------------------------------------------------------------

    @Test
    fun `le selecteur de fichiers donne son autorisation durable`() {
        assertTrue(
            "`OpenMultipleDocuments` accorde une autorisation persistable : la prendre en plus de " +
                "la copie (la copie reste la garantie, l'autorisation le confort)",
            pendingAttachment.contains("takePersistableUriPermission"),
        )
        val model = text("src/main/java/com/onyx/app/ui/chat/ChatViewModel.kt")
        assertTrue(
            "et le chat la prend à l'ajout",
            model.contains("takeDurablePermissions(app, uris)"),
        )
        assertTrue(
            "la salle de groupe aussi",
            groupComposer.contains("takeDurablePermissions(context, uris)"),
        )
    }

    // -----------------------------------------------------------------------------------------
    // La règle, pure — ce qu'une pièce jointe peut nommer
    // -----------------------------------------------------------------------------------------

    @Test
    fun `une uri transitoire ou un chemin du cache ne sont pas durables`() {
        val filesDir = "/data/user/0/com.onyx.app/files"
        assertTrue(
            "le `content://` du sélecteur n'est pas durable : son autorisation meurt avec le processus",
            AttachmentPathPolicy.isTransient("content://com.android.providers.media.documents/document/image%3A42"),
        )
        assertTrue(
            "un chemin sous `cacheDir` non plus : le système le purge",
            AttachmentPathPolicy.isTransient("$filesDir/../cache/photos/photo-1.jpg"),
        )
        assertFalse(
            "mais la copie sous `filesDir` l'est",
            AttachmentPathPolicy.isTransient("file://$filesDir/attachments/p.jpg"),
        )
        assertTrue(
            "et elle est reconnue comme telle",
            AttachmentPathPolicy.isDurable("file://$filesDir/attachments/p.jpg"),
        )
        assertFalse(
            "un `content://` n'est jamais durable, même chez nous",
            AttachmentPathPolicy.isDurable("content://com.onyx.app.files/photos/p.jpg"),
        )
    }

    // -----------------------------------------------------------------------------------------
    // Le message enregistre la piece avec sa taille et son type, pas seulement son URI
    // -----------------------------------------------------------------------------------------

    @Test
    fun `le message enregistre la piece par sa conversion durable`() {
        val model = text("src/main/java/com/onyx/app/ui/chat/ChatViewModel.kt")
        assertTrue(
            "le tour doit passer par `toAttachment()` : une construction à la main laissait " +
                "`size` à 0 et la taille n'était pas enregistrée avec la pièce",
            model.contains("attachments = attachments.map { it.toAttachment() }"),
        )
    }

    // -----------------------------------------------------------------------------------------
    // Une copie durable se confie à une autre app par le FileProvider, jamais en `file://`
    // -----------------------------------------------------------------------------------------

    @Test
    fun `une copie durable se partage par le FileProvider`() {
        val app = text("src/main/java/com/onyx/app/ui/OnyxApp.kt")
        assertTrue(
            "depuis Android 7, une `file://` confiée à une autre app lève `FileUriExposedException` " +
                "et l'appui ne fait rien : les pièces durables doivent passer par le FileProvider",
            app.contains("shareableUri(context, uri)"),
        )
        assertTrue(
            "et la conversion vit à un seul endroit",
            text("src/main/java/com/onyx/app/ui/chat/DurableAttachments.kt")
                .contains("fun shareableUri(context: Context, uri: Uri): Uri"),
        )
    }

    // -----------------------------------------------------------------------------------------
    // Le partage depuis le stockage privé doit être déclaré au FileProvider
    // -----------------------------------------------------------------------------------------

    @Test
    fun `le FileProvider sait partager le stockage prive`() {
        assertTrue(
            "`file_paths.xml` ne déclare que `cache-path` : `getUriForFile` échouerait sur un " +
                "fichier de `filesDir` — un lecteur ne pourrait plus l'ouvrir",
            filePaths.contains("files-path"),
        )
    }
}
