package com.onyx.app.ui.chat

import com.onyx.app.data.model.serve.DeliveredFile
import com.onyx.app.data.net.TransportError
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.localization.OnyxLanguage
import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ce que dit le bandeau quand une pièce jointe ne passe pas, et ce qu'il ne laisse jamais passer.
 *
 * Le défaut signalé : « Impossible de le récupérer : http_400 » — un code nu, sans geste à faire.
 * Le refus d'un fichier joint doit maintenant dire **en français quoi faire**, et une pièce jointe
 * de l'appareil ne doit **jamais** être réclamée au serveur (c'est ce qui fabriquait le 400).
 *
 * Ces tests tiennent aussi le câblage : si l'envoi d'une image redevient silencieux (une bannière
 * muette) ou si l'image repart dans le texte du message (un message fantôme), ils cassent.
 */
class AttachmentProblemTextTest {

    private val french = Locale.FRANCE

    private fun fr(key: String): String = OnyxLocalization.translate(key, french)

    @Test
    fun `un 400 dit quoi faire et ne montre jamais le code nu`() {
        val text = DeliveredFiles.failureText(TransportError.Http(400, "{\"detail\":\"Path must be absolute\"}"))

        assertFalse("le code nu ne doit plus s'afficher", text.contains("http_400"))
        assertTrue("le texte est en français", text.contains("Le serveur a refusé la demande (400)"))
        assertTrue("il dit le geste à faire", text.contains("Retirez la pièce") || text.contains("trop lourd"))
    }

    @Test
    fun `un 413 parle du poids du fichier`() {
        val text = DeliveredFiles.failureText(TransportError.Http(413, "File is too large"))
        assertFalse(text.contains("http_413"))
        assertTrue(text.contains("trop lourd"))
        assertTrue(text.contains("plus petit"))
    }

    @Test
    fun `un 409 dit qu'un fichier de ce nom est deja la`() {
        val text = DeliveredFiles.failureText(TransportError.Http(409, "File already exists"))
        assertFalse(text.contains("http_409"))
        assertTrue(text.contains("409"))
        assertTrue(text.contains("Réessayez"))
    }

    @Test
    fun `un serveur injoignable parle de la connexion, pas d'un code`() {
        val unreachable = DeliveredFiles.failureText(TransportError.Unreachable("Connection refused"))
        assertFalse(unreachable.contains("http_"))
        assertTrue(unreachable.contains("Serveur injoignable"))

        val timeout = DeliveredFiles.failureText(TransportError.Timeout("timeout"))
        assertTrue(timeout.contains("Vérifiez l’adresse et réessayez"))
    }

    @Test
    fun `un 401 renvoie aux identifiants du Dashboard`() {
        val text = DeliveredFiles.failureText(TransportError.Http(401, "no_cookie"))
        assertTrue(text.contains("Dashboard"))
        assertTrue(text.contains("Réglages"))
    }

    @Test
    fun `un 404 reste precis sur l emplacement`() {
        val absolute = DeliveredFiles.failureText(
            TransportError.Http(404, "not found"),
            DeliveredFile(name = "image.png", path = "/home/john/image.png"),
        )
        assertTrue(absolute.contains("introuvable"))
        assertFalse(absolute.contains("http_404"))

        val relative = DeliveredFiles.failureText(
            TransportError.Http(404, "not found"),
            DeliveredFile(name = "image.png", path = "image.png"),
        )
        assertTrue("un chemin relatif dit que la discussion n'est pas encore sur le serveur", relative.contains("discussion"))
    }

    @Test
    fun `une piece jointe de l appareil n est jamais demandee au serveur`() {
        assertTrue(DeliveredFiles.isLocalPath("content://media/external/images/media/1234"))
        assertTrue(DeliveredFiles.isLocalPath("content://com.android.providers.media.documents/document/image%3A12"))
        assertTrue(DeliveredFiles.isLocalPath("file:///storage/emulated/0/DCIM/Camera/IMG_0001.jpg"))
        assertTrue("un chemin relatif n'est pas un chemin du serveur", DeliveredFiles.isLocalPath("image.png"))

        assertFalse("un chemin absolu reste un fichier du serveur", DeliveredFiles.isLocalPath("/home/john/file.png"))
        assertFalse(DeliveredFiles.isLocalPath("~/docs/file.pdf"))
    }

    @Test
    fun `un essai perdu se rejoue, un refus net non`() {
        assertTrue("rien n'a été écrit", DeliveredFiles.isRetryable(TransportError.Unreachable("refused")))
        assertTrue(DeliveredFiles.isRetryable(TransportError.Timeout("timeout")))
        assertTrue(DeliveredFiles.isRetryable(TransportError.Http(500, "boom")))
        assertTrue(DeliveredFiles.isRetryable(TransportError.Http(429, "slow down")))

        assertFalse("un 400 demande de changer la pièce, pas de réessayer", DeliveredFiles.isRetryable(TransportError.Http(400, "invalid")))
        assertFalse(DeliveredFiles.isRetryable(TransportError.Http(413, "too large")))
    }

    @Test
    fun `le texte de langue n a pas de trou`() {
        // Le dictionnaire doit porter chaque clé ajoutée : une clé manquante rendrait l'anglais.
        val keys = listOf(
            "The server refused this request (400). It may be too heavy, or in a shape it doesn't take.",
            "The server says that file is too heavy (413). Choose a smaller one.",
            "The server has no room for a file of that name (409). Try again.",
            "The server refused this file (403). It isn't allowed to write there.",
            "The server doesn't know that route (404). Check the Dashboard address in Settings.",
            "The file is still on this device, not on the server: it opens from here.",
            "The picture didn't reach the server.",
            "the server refused the image",
        )
        keys.forEach { key ->
            val value = fr(key)
            assertTrue("clé non traduite : $key", value != key)
            assertTrue("traduction vide pour $key", value.isNotBlank())
        }
    }

    // --------------------------------------------------------------------------------------------
    // Le câblage : ce qui doit rester vrai pour qu'un envoi d'image refusé ne soit jamais muet.
    // --------------------------------------------------------------------------------------------

    private val root = File("..")
    private val coreServeFiles = File("../core/src/main/java/com/onyx/app/data/net/ServeFilesApi.kt")
    private val coreServeChat = File("../core/src/main/java/com/onyx/app/data/net/HermesServeChat.kt")
    private val appSources = File("src/main/java/com/onyx/app")

    private fun read(file: File): String = file.readText().replace("\r\n", "\n")

    @Test
    fun `l envoi multipart vise le fichier cible, jamais le seul dossier`() {
        val text = read(coreServeFiles).substringAfter("suspend fun upload(")
            .substringBefore("fun soul(")
        assertTrue(
            "le champ path doit joindre dossier et nom : envoyer le dossier fait répondre 409",
            text.contains("serverPathJoin(destinationFolder, name)"),
        )
        assertFalse("envoyer le dossier seul est le défaut d'origine", text.contains("\"path\" to destinationFolder"))
    }

    @Test
    fun `une image jointe est deposee sur la session avant le prompt`() {
        val text = read(coreServeChat)
        assertTrue("l'appel existe", text.contains("\"image.attach_bytes\""))
        assertTrue("le base64 voyage", text.contains("\"content_base64\""))
        assertTrue("le nom aussi", text.contains("\"filename\""))
        assertTrue(
            "le dépôt précède le prompt",
            text.indexOf("attachImages(request, session.runtime)") < text.indexOf("put(\"text\", promptText(request))"),
        )
    }

    @Test
    fun `un refus d image laisse un mot, pas un silence`() {
        val text = read(coreServeChat)
        assertTrue(
            "le refus est dit, et le tour continue",
            text.contains("attachImages(request, session.runtime)?.let { send(TurnEvent.Status(it)) }"),
        )
        assertFalse(
            "l'image ne doit pas revenir dans le texte du message (message fantôme)",
            text.contains("images.joinToString"),
        )
    }

    @Test
    fun `la piece jointe de l appareil s ouvre sur place`() {
        val app = read(File("src/main/java/com/onyx/app/ui/OnyxApp.kt"))
        val guard = app.substringAfter("onOpenDelivered = { file ->").substringBefore("},")
        assertTrue(
            "une pièce jointe de l'appareil est ouverte directement, jamais réclamée au serveur",
            guard.contains("DeliveredFiles.isLocalPath(file.path)"),
        )
        assertTrue("elle s'ouvre avec son adresse locale", guard.contains("android.net.Uri.parse(file.path)"))
    }

    @Test
    fun `le bandeau reste ignorable et le composeur garde de quoi retirer la piece`() {
        val screen = read(File("src/main/java/com/onyx/app/ui/chat/ChatScreen.kt"))
        assertTrue("le bandeau a un « Ignorer »", screen.contains("text = \"Dismiss\""))
        assertTrue("et un « Réessayer » quand le tour peut être rejoué", screen.contains("text = \"Retry\""))
        assertTrue("la pièce en attente peut être retirée", screen.contains("onRemoveAttachment"))

        val state = read(File("src/main/java/com/onyx/app/ui/chat/ChatViewModel.kt"))
        assertTrue("un message joint qu'on retire ne bloque pas l'envoi", state.contains("fun removeAttachment("))
        assertTrue("et le texte part sans la pièce", state.contains("sendText(text, allowEmpty = current.attachments.isNotEmpty())"))
    }

    @Test
    fun `aucun mot de la plateforme d origine dans ce qui a ete ajoute`() {
        // On ne cherche que les mots propres de la marque : le préfixe de la maison de composants
        // commune est le vocabulaire du dépôt entier, présent partout, et n'est pas visé ici.
        val added = read(File("src/main/java/com/onyx/app/ui/chat/DeliveredFiles.kt")) +
            read(coreServeFiles) + read(coreServeChat) +
            read(File("src/main/java/com/onyx/app/ui/OnyxApp.kt"))
        // Les mots sont assemblés morceau par morceau : ce garde-fou ne doit pas se déclencher
        // lui-même (le balayage est insensible à la casse).
        listOf("ap" + "ple", "iph" + "one", "i" + "pad", "mac" + "os", "si" + "ri").forEach { word ->
            assertFalse("le mot « $word » ne doit pas apparaître", added.lowercase().contains(word))
        }
        assertTrue(root.isDirectory)
        assertTrue(appSources.isDirectory)
        assertTrue("la langue préférée est lisible", OnyxLanguage.preferred.language.isNotEmpty())
    }
}
