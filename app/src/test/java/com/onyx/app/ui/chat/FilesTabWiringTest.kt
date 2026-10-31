package com.onyx.app.ui.chat

import com.onyx.app.data.model.Attachment
import com.onyx.app.data.model.ChatMessage
import com.onyx.app.data.model.FileFamily
import com.onyx.app.data.model.Role
import com.onyx.app.data.model.ToolActivity
import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L'onglet **Fichiers** est vraiment branché : un appui ouvre quelque chose, chaque famille a sa
 * destination, les filtres et les tris sont dessinés, et l'appui long ouvre un menu.
 *
 * Un écran qui liste sans ouvrir, ou un filtre qui ne filtre rien, serait une fonction factice :
 * c'était exactement le défaut signalé — une ligne, une icône, un nom, et rien au bout du doigt.
 */
class FilesTabWiringTest {

    private val app = File("src/main/java/com/onyx/app/ui/OnyxApp.kt").readText()
    private val chatScreen = File("src/main/java/com/onyx/app/ui/chat/ChatScreen.kt").readText()
    private val tab = File("src/main/java/com/onyx/app/ui/chat/FilesTab.kt").readText()
    private val panels = File("src/main/java/com/onyx/app/ui/chat/ChatPanels.kt").readText()
    private val controller = File("src/main/java/com/onyx/app/ui/chat/ConversationFilesController.kt").readText()

    /**
     * Le corps d'une branche du **routeur** (celui de `when (route)`), jamais celui de la table
     * d'ouverture : les deux portent « Route.AUDIO -> » (« FileOpenRoute.AUDIO -> » pour l'autre),
     * et confondre les deux ferait passer un test de route sur le mauvais code.
     */
    private fun branch(name: String): String =
        app.substringAfter("\n            Route.$name ->").substringBefore("\n            Route.")

    // — L'onglet est branché sur les fichiers de la conversation —

    @Test
    fun `l onglet des fichiers montre les fichiers echanges, avec leur type et leur date`() {
        assertTrue(
            "le fil fournit les fichiers de l'onglet",
            chatScreen.contains("ChatFiles.conversationFiles(state.messages)"),
        )
        assertTrue("le panneau reçoit la liste", chatScreen.contains("files = exchangedFiles"))
        assertTrue("le panneau reçoit le serveur actif", chatScreen.contains("config = state.server"))
        assertTrue("la couleur du bot porte l'onglet", chatScreen.contains("accent = tabAccent"))
        // La liste garde le type annoncé par la pièce jointe et la date du message qui l'a amenée.
        assertTrue("les pièces jointes entrent dans la liste", panels.contains("message.attachments.forEach"))
        assertTrue("le type de la pièce jointe est gardé", panels.contains("mime = attachment.mimeType"))
        assertTrue("la date du message est gardée", panels.contains("appearedAtMillis = message.createdAt"))
        // Les anciennes pastilles du fil gardent leur contrat (une seule source de vérité).
        assertTrue(panels.contains("fun exchanged(messages: List<ChatMessage>)"))
    }

    @Test
    fun `les fichiers ecanges portent leur type et leur date`() {
        val message = ChatMessage(
            id = "a1",
            role = Role.ASSISTANT,
            text = "Voici le contrat /home/onyx/Documents/contrat.pdf",
            createdAt = 1_700_000_000_000L,
            tools = listOf(
                ToolActivity(id = "t1", name = "write_file", output = "/home/onyx/Images/IMG-20261026-WA0001.jpg"),
            ),
            attachments = listOf(
                Attachment(id = "at1", mimeType = "audio/mpeg", uri = "content://x/note.mp3", name = null),
            ),
        )
        val files = ChatFiles.conversationFiles(listOf(message))
        // Les fichiers nommés par la réponse d'abord, dans l'ordre où elle les nomme, puis ce que
        // la personne a joint.
        assertEquals(
            listOf("IMG-20261026-WA0001.jpg", "contrat.pdf", "note.mp3"),
            files.map { it.name },
        )
        assertEquals(FileFamily.IMAGE, files[0].family)
        assertEquals(FileFamily.DOCUMENT, files[1].family)
        // Le type vient de la pièce jointe, et le nom sans nom prend son dernier segment.
        assertEquals(FileFamily.AUDIO, files[2].family)
        assertEquals("audio/mpeg", files[2].mime)
        assertTrue("la date du message est celle du fichier", files.all { it.appearedAtMillis == 1_700_000_000_000L })
        assertTrue("aucun fichier sans date", files.none { it.appearedAtMillis == 0L })
    }

    @Test
    fun `les cinq familles d un livrable apparaissent dans l onglet`() {
        val reply = listOf(
            "/home/onyx/Images/i.jpg",
            "/home/onyx/Videos/v.mp4",
            "/home/onyx/Audio/s.mp3",
            "/home/onyx/Documents/d.pdf",
            "/home/onyx/Archives/z.zip",
            "/home/onyx/Documents/t.txt",
            "/home/onyx/Documents/x.docx",
            "/home/onyx/Data/b.bin",
        ).joinToString(" ")
        val names = DeliveredFiles.of(listOf(reply), limit = 20).map { it.name }
        assertEquals(listOf("i.jpg", "v.mp4", "s.mp3", "d.pdf", "z.zip", "t.txt", "x.docx", "b.bin"), names)
    }

    @Test
    fun `une extension longue garde son nom entier`() {
        // « rapport.docx » se coupait en « rapport.doc » : le nom perdait sa fin, et le poids du
        // fichier n'était plus retrouvé auprès du serveur — deux défauts d'un seul caractère.
        val files = DeliveredFiles.of(listOf("Le rapport /home/onyx/Documents/rapport.docx est prêt."), limit = 10)
        assertEquals(listOf("rapport.docx"), files.map { it.name })
        assertEquals("/home/onyx/Documents/rapport.docx", files.first().path)
        assertEquals(
            listOf("feuille.xlsx"),
            DeliveredFiles.of(listOf("/home/onyx/Documents/feuille.xlsx"), limit = 10).map { it.name },
        )
    }

    // — Aucun appui sans effet —

    /**
     * **Le verrou de résolution, re-pointé ici.** Cette classe certifiait qu'un appui mène quelque
     * part — jamais que la cible existe. Or le propriétaire a vu deux cartes menant à un
     * « Il est introuvable… » suivi d'un « Réessayer » qui rejouait la même demande morte : la
     * promesse tenait sur le papier et tombait à l'appui. La liste ne suffit donc plus ; il faut que
     * la cible soit **résolue avant** que la carte promette quoi que ce soit.
     */
    @Test
    fun `une carte ne promet rien sans cible resolvable`() {
        assertTrue(
            "la lecture du dossier qui porte le fichier dit si la cible est servie",
            controller.contains("val size = served[file.name]"),
        )
        assertTrue(
            "son silence ne conclut rien",
            controller.contains("val served = servedFolder(path) ?: return"),
        )
        assertTrue(
            "et l'absence devient une carte qui dit ce qui a changé, sans geste",
            controller.contains("DeliveredFiles.goneText("),
        )
        assertTrue(
            "l'état se décide par la règle pure",
            controller.contains("FileCardTargets.state("),
        )
        assertTrue(
            "le geste n'est offert que si rejouer peut aboutir",
            tab.contains("action = if (availability.retryable) onRetry else null"),
        )
        assertTrue(
            "et l'étiquette le dit « Indisponible »",
            tab.contains("label = if (availability.retryable) OnyxLocalization[\"Retry\"] else OnyxLocalization[\"Unavailable\"]"),
        )
    }

    @Test
    fun `un appui ouvre le fichier ou lance son telechargement, jamais rien`() {
        assertTrue(
            "l'appui ouvre le fichier local",
            tab.contains("openFromTab(file, availability.local)"),
        )
        assertTrue(
            "le geste d'ouverture est écrit une fois pour la ligne et le menu",
            tab.contains("fun openFromTab(file: ConversationFile, local: File)"),
        )
        assertTrue(
            "sinon il le télécharge **puis l'ouvre** (un appui ne se contente jamais de télécharger)",
            tab.contains("openFromTab(file, controller.local(file))"),
        )
        // Chaque destination de la famille est bien écrite à l'écran.
        assertTrue("photo", app.contains("com.onyx.app.data.model.FileOpenRoute.PHOTO,"))
        assertTrue("vidéo", app.contains("com.onyx.app.data.model.FileOpenRoute.VIDEO,"))
        assertTrue("PDF", app.contains("com.onyx.app.data.model.FileOpenRoute.PDF,"))
        assertTrue("système", app.contains("com.onyx.app.data.model.FileOpenRoute.EXTERNAL,"))
        assertTrue("son", app.contains("com.onyx.app.data.model.FileOpenRoute.AUDIO ->"))
        assertTrue("texte", app.contains("com.onyx.app.data.model.FileOpenRoute.TEXT ->"))
        assertTrue("archive", app.contains("com.onyx.app.data.model.FileOpenRoute.ARCHIVE ->"))
        // Aucune branche ne se contente d'un `Unit` : chacune navigue.
        assertTrue(app.contains("fun openConversationFile(uri: android.net.Uri, name: String, mime: String)"))
        val dispatchBody = app.substringAfter("fun openConversationFile(").substringBefore("\n        LaunchedEffect(state.server)")
        assertFalse("aucune branche muette", dispatchBody.contains("-> Unit"))
    }

    @Test
    fun `chaque famille a sa route, son lecteur et son chemin de retour`() {
        val routes = app.substringAfter("private enum class Route {").substringBefore("\n}")
        assertTrue("Route.AUDIO est déclarée", routes.contains("AUDIO, DOCUMENT, ARCHIVE"))
        assertTrue("le son se lit dans l'app", branch("AUDIO").contains("AudioPlayerScreen("))
        assertTrue("le document texte aussi", branch("DOCUMENT").contains("DocumentScreen("))
        assertTrue("l'archive montre son contenu", branch("ARCHIVE").contains("ArchiveScreen("))
        listOf("AUDIO", "DOCUMENT", "ARCHIVE").forEach { name ->
            assertTrue("$name revient au fil", branch(name).contains("navTo(Route.CHAT.name)"))
            assertTrue("$name se replie hors de la composition", branch(name).contains("navFromEmpty(Route.CHAT.name)"))
        }
        assertTrue("les trois se présentent comme des feuilles", app.contains("Route.AUDIO, Route.DOCUMENT, Route.ARCHIVE,"))
        assertTrue(
            "le fil branche l'ouverture sur la famille",
            app.contains("onOpenLocalFile = { uri, name, mime -> openConversationFile(uri, name, mime) }"),
        )
        assertTrue(
            "le fil garde les lecteurs déjà en place",
            app.contains("openFile(uri = uri, name = name, mime = mime)"),
        )
    }

    @Test
    fun `les lecteurs de l app sont reutilises, pas reecrits`() {
        // Le lecteur d'image vient d'être corrigé par un autre lot : ce lot ne le réécrit pas, il
        // s'y branche. La route de l'image dessine toujours le visualiseur existant.
        assertTrue(branch("PHOTO").contains("PhotoViewerScreen("))
        assertTrue(branch("VIDEO").contains("VideoPlayerScreen("))
        assertTrue(branch("PDF").contains("PdfViewerScreen("))
        assertFalse("l'onglet ne redéfinit pas le visualiseur", tab.contains("fun PhotoViewerScreen"))
        assertFalse("l'onglet ne redéfinit pas le lecteur de vidéo", tab.contains("fun VideoPlayerScreen"))
        assertFalse("l'onglet ne redéfinit pas le lecteur de PDF", tab.contains("fun PdfViewerScreen"))
    }

    // — Les filtres, les tris, le menu —

    @Test
    fun `les filtres sont compacts sur une rangée, et le tri est un contrôle compact`() {
        assertTrue("les six puces de famille", tab.contains("FileFamily.entries.filter { it.isFilter }"))
        assertTrue("chaque puce porte son compte sous le mot", tab.contains("fileFamilyLabel(family, count)"))
        // Le tri, dans un contrôle compact : les cinq ordres restent construits (dans son menu),
        // pas perdus derrière un mot.
        assertTrue("les cinq ordres de tri", tab.contains("FileSort.entries.forEach { order ->"))
        assertTrue("l'ordre courant est nommé dans le contrôle", tab.contains("fileSortLabel(sort)"))
        assertTrue("et les cinq ordres dans le menu", tab.contains("fileSortLabel(order)"))
        assertTrue("la liste passe par les fonctions pures", tab.contains("visibleConversationFiles(files, filter, sort)"))
        // Les puces **tiennent sur une seule rangée** : plus de FlowRow qui passe à la ligne, plus de
        // rangée qui défile (« Archives » n'était plus atteignable).
        assertFalse("aucune rangée de puces à faire glisser", tab.contains("horizontalScroll"))
        assertEquals("les filtres sont sur une seule rangée", 0, Regex("FlowRow\\(").findAll(tab).count())
    }

    @Test
    fun `les fichiers sont des cartes a vignettes deterministes, dans une grille paresseuse`() {
        assertTrue("une grille paresseuse", tab.contains("LazyVerticalGrid("))
        assertTrue("deux colonnes de cartes", tab.contains("GridCells.Fixed(2)"))
        assertTrue("la vignette vient du générateur du socle", tab.contains("FileThumbnail.plan("))
        assertTrue("et elle est dessinée par la maison", tab.contains("FileThumbnailView("))
        // Une image montre sa **vraie** vignette décodée (bornée par la l'app), pas un pictogramme.
        assertTrue("l'image passe son bitmap borné", tab.contains("image = poster?.asImageBitmap()"))
        // La forme d'onde d'un son vient des **premiers octets** du fichier.
        assertTrue("les premiers octets du son sont lus", tab.contains("FileThumbnail.WaveBytes"))
    }

    @Test
    fun `un seul accent par ecran, la pastille du filtre actif`() {
        // Le filtre actif porte l'accent ; toute autre pastille de la carte est neutre.
        assertTrue("la capsule active prend l'accent", tab.contains("background(if (active) theme.accent else Color.Transparent)"))
        // Aucune pastille accentuée posée sur une carte : l'état s'écrit en mots sous le nom.
        assertTrue("l'état d'un fichier s'écrit en mots", tab.contains("FileStateLine("))
        assertTrue("l'échec a un panneau à part", tab.contains("private fun FilesTrouble("))
    }

    @Test
    fun `l appui long ouvre le menu, et ne propose que ce qui peut aboutir`() {
        assertTrue("l'appui long est branché", tab.contains("onLongPress = onLongPress"))
        assertTrue("le menu s'ouvre sur la ligne touchée", tab.contains("menuFor = file"))
        listOf("Open", "Share", "Save to the phone", "Copy path", "Open with").forEach { entry ->
            assertTrue("entrée « $entry »", tab.contains("MenuItem(\"$entry\""))
        }
        // Le Dashboard n'a aucune route pour détacher un fichier d'un message : l'entrée ne se
        // propose donc pas, plutôt que de promettre ce qui ne peut pas aboutir.
        assertFalse("pas de suppression impossible", tab.contains("Remove from thread"))
    }

    @Test
    fun `l etat de chaque fichier est visible, avec sa progression et son reessai`() {
        assertTrue("quatre états", controller.contains("sealed interface FileAvailability"))
        listOf("Remote", "Fetching", "Available", "Failed").forEach { state ->
            assertTrue("état $state", controller.contains("data class $state") || controller.contains("data object $state"))
        }
        assertTrue("la progression vient du poids annoncé", controller.contains("val fraction: Float?"))
        // **Re-pointé** (lot « fichiers résolus ») : le téléchargement passe désormais par une
        // variable, parce qu'il a **deux cibles** — un chemin du serveur (`toFile`, streamé par le
        // Dashboard) et une adresse nommée par la réponse (`toUrl`, demandée telle quelle). Le
        // contrat reste : c'est `ServeFileDownload` qui streame, jamais une lecture d'un bloc.
        assertTrue("le téléchargement passe par le flux", controller.contains("ServeFileDownload(config)"))
        assertTrue("un chemin du serveur est streamé", controller.contains("download.toFile("))
        assertTrue("une adresse aussi", controller.contains("download.toUrl("))
        assertTrue("le réessai existe", controller.contains("fun retry(file: ConversationFile, scope: CoroutineScope)"))
        // Re-pointé de même : l'échec dit pourquoi **et** s'il vaut la peine d'être rejoué.
        assertTrue("l'échec dit pourquoi", controller.contains("DeliveredFiles.problem(error,"))
        assertTrue("un fichier local s'ouvre sans réseau", controller.contains("FileAvailability.Available(local)"))
    }

    @Test
    fun `les etats vides suivent le filtre choisi`() {
        assertTrue(tab.contains("private fun emptyTitle(family: FileFamily): String"))
        FileFamily.entries.filter { it.isFilter && it.key != "all" }.forEach { family ->
            assertTrue("état vide pour ${family.key}", tab.contains("FileFamily.${family.name} ->"))
        }
        assertTrue("l'erreur du serveur a son réessai", tab.contains("text = \"Retry\""))
    }

    @Test
    fun `le panneau ne dessine aucun composant substitue`() {
        // Barres maison et icônes de la maison, comme le reste de l'app.
        assertTrue("pastilles rondes dessinées ici", tab.contains("RoundedCornerShape(50)"))
        assertTrue("icônes de la maison", tab.contains("OnyxIcon("))
        assertTrue("texte par le dictionnaire", tab.contains("OnyxLocalization["))
        listOf("androidx.compose.material", "material3", "Scaffold", "TopAppBar").forEach { forbidden ->
            assertFalse("pas de $forbidden", tab.contains(forbidden))
        }
    }

    @Test
    fun `les libelles de la ligne sont traduits par le dictionnaire`() {
        val french = File("../core/src/main/java/com/onyx/app/localization/OnyxFrenchTabs.kt").readText()
        listOf(
            "\"Images\"", "\"Videos\"", "\"Audio\"", "\"Documents\"", "\"Archives\"",
            "\"Recent first\"", "\"Oldest first\"", "\"Size\"", "\"Type\"",
            "\"To download\"", "\"Available\"", "\"Save to the phone\"", "\"Copy path\"", "\"Open with\"",
        ).forEach { key ->
            assertTrue("clé française absente : $key", french.contains("$key to "))
        }
        assertTrue(
            "les vidéos se disent en français",
            com.onyx.app.localization.OnyxLocalization.translate("Videos", Locale.FRANCE) == "Vidéos",
        )
    }
}
