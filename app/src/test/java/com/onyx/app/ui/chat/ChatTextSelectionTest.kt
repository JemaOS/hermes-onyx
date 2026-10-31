package com.onyx.app.ui.chat

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le texte d'une conversation se prend au doigt : appui long → un mot, poignées pour étendre,
 * barre du système (Couper / Copier / Coller / Tout sélectionner).
 *
 * Ce fichier fixe le **périmètre** autant que le mécanisme : ce qui doit être sélectionnable, et
 * surtout ce qui ne doit pas l'être — le wrapper de texte de l'app habille aussi les réglages, et
 * les lignes de liste portent un menu ancré que la sélection volerait.
 */
class ChatTextSelectionTest {

    private fun app(path: String) = File("src/main/java/com/onyx/app", path).readText()

    private fun core(path: String) = File("../core/src/main/java/com/onyx/app", path).readText()

    @Test
    fun `la selection passe par le mecanisme de Compose et jamais par une barre maison`() {
        val wrapper = core("ui/design/SelectableText.kt")

        assertTrue(wrapper.contains("SelectionContainer("))
        // Aucune barre dessinée par l'app : ni surlignage maison, ni menu de copie.
        assertFalse(wrapper.contains("FloatingToolbar"))
        assertFalse(wrapper.contains("TextToolbar"))
        assertTrue(wrapper.contains("fun SelectableText("))
    }

    @Test
    fun `le wrapper de texte de l app n active pas la selection globalement`() {
        val basic = core("ui/localization/OnyxBasicText.kt")

        // `BasicText` habille toute l'app, réglages compris : la sélection s'y demande, elle ne
        // s'impose pas.
        assertFalse(basic.contains("SelectionContainer"))
        assertFalse(basic.contains("isTextSelectable"))
    }

    @Test
    fun `les messages du fil sont selectionnables`() {
        val row = app("ui/chat/components/MessageRow.kt")

        // Votre message, dans sa bulle.
        assertTrue(row.contains("SelectableText(text = text, localize = false, style = theme.userFont.copy(color = theme.userBubbleText))"))
        // Le raisonnement déplié.
        assertTrue(row.contains("val full = if (isLive) tail(message.reasoning, 360) else message.reasoning"))
        // La ligne d'invite des thèmes CRT, le texte de pilotage et l'erreur de tour.
        assertTrue(row.contains("text = promptLineText(prefix, text)"))
        assertTrue(row.contains("text = message.text,\n                    localize = false,\n                    style = theme.footnote.copy(color = theme.secondaryText),"))
        assertTrue(row.contains("SelectableText(text = error, localize = false"))
    }

    @Test
    fun `les sorties d outils sont selectionnables`() {
        val row = app("ui/chat/components/MessageRow.kt")
        val gabarit = app("ui/chat/components/ToolCallDetail.kt")

        // Le nom de l'outil, son aperçu, et le bloc « Input » / « Output » d'un pas ouvert.
        // LOT organisation-fil-competences : le nom reste **sélectionnable**, et c'est le verbe au
        // passé qui le remplace **une fois l'outil fini** (le passé serait faux tant qu'il tourne).
        // LOT outils-detail : la rangée écrit ce que le gabarit lui donne (`toolRowLabel`) — le nom
        // de repli et le verbe sont passés dans le gabarit, où le test du lot les vérifie.
        // Le verrou garde ce qu'il gardait : la surface (`SelectableText`), le nom, et l'aperçu —
        // il les suit là où ils vivent maintenant (le titre et la commande de la carte de contexte,
        // puis la surface de sélection du composant), au lieu de les relâcher.
        assertTrue(row.contains("title = rowLabel.text,"))
        assertTrue(row.contains("detail = tool.preview?.takeIf { it.isNotBlank() },"))
        assertTrue(row.contains("SelectableText("))
        assertTrue(row.contains("text = head(text, lines),"))
        assertTrue(app("ui/chat/components/ContextCard.kt").contains("SelectableText("))
        assertTrue(app("ui/chat/components/ContextCard.kt").contains("text = line,"))
        assertTrue(gabarit.contains("tool.name.ifBlank { OnyxLocalization[\"a tool\"] }"))
    }

    @Test
    fun `la prose Markwon se selectionne sans perdre les liens`() {
        val source = app("ui/chat/components/MarkdownText.kt")

        assertTrue(source.contains("setTextIsSelectable(true)"))
        // `setTextIsSelectable` repose un movement method de clavier : le lien se remet APRÈS,
        // sinon plus aucun lien ne s'ouvre.
        val selectable = source.indexOf("setTextIsSelectable(true)")
        val links = source.indexOf("movementMethod = LinkMovementMethod.getInstance()")
        assertTrue("le movement method suit la sélection", selectable in 0..<links)
        // Le texte prend le focus au toucher : le système ne montre la sélection et sa barre sur un
        // texte non modifiable que si la vue peut être focalisée.
        assertFalse(source.contains("isFocusableInTouchMode = false"))
    }

    @Test
    fun `un bloc de code et une cellule de tableau se selectionnent`() {
        assertTrue(core("ui/rich/CodeBlock.kt").contains("SelectableText(\n                text = highlight,"))
        assertTrue(app("ui/chat/components/MarkdownText.kt").contains("SelectableText(\n            text = inlinePlain(text),"))
    }

    @Test
    fun `les notes de salle de groupe se selectionnent`() {
        val room = app("ui/bots/GroupRoomScreen.kt")

        assertTrue(room.contains("else -> event.note(room)?.let { note ->\n            SelectableText("))
        assertTrue(room.contains("text = \"Earlier messages are in Hermes Desktop.\""))
        assertTrue(room.contains("SelectableText(\n            text = \"This group chat runs in Hermes Desktop."))
    }

    @Test
    fun `le texte de la carte d approbation se selectionne comme dans la reference`() {
        assertTrue(
            app("ui/chat/components/ApprovalCard.kt").contains("SelectableText(") &&
                app("ui/chat/components/ApprovalCard.kt").contains("text = request.command,"),
        )
    }

    @Test
    fun `l apercu d une piece jointe texte se selectionne aussi`() {
        // Le fil d'une pièce jointe texte : ses premières lignes s'affichent sous la pastille.
        val delivered = app("ui/chat/DeliveredFiles.kt")

        assertTrue(delivered.contains("SelectableText(\n                    text = preview.text,"))
    }

    @Test
    fun `les lignes de liste gardent leur menu ancre`() {
        // Une ligne dont l'appui long ouvre un menu ne doit pas prendre la sélection : le geste
        // sert déjà la ligne (liste des conversations, brouillons en file).
        // Le volet est la liste des conversations : sa rangée porte l'appui long du menu, pas la
        // sélection de texte.
        assertFalse(app("ui/sidebar/ChatSidebar.kt").contains("SelectableText("))
        assertFalse(app("ui/chat/QueuedMessageRow.kt").contains("SelectableText("))
        assertTrue(app("ui/chat/QueuedMessageRow.kt").contains("detectTapGestures(onLongPress = { showMenu = true })"))
    }

    @Test
    fun `les reglages restent sans selection`() {
        val settings = File("src/main/java/com/onyx/app/ui/settings").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()

        assertTrue(settings.size > 10)
        settings.forEach { file ->
            assertFalse(file.path, file.readText().contains("SelectableText("))
        }
    }

    @Test
    fun `les poignees et le surlignage suivent le theme`() {
        val theme = core("ui/theme/Theme.kt")

        assertTrue(theme.contains("LocalTextSelectionColors provides selection"))
        assertTrue(theme.contains("handleColor = resolved.accent"))
        // La sélection des textes natifs (la prose d'un message) prend ses couleurs du thème
        // Android : sans cet accent, ses poignées restaient au vert par défaut du système.
        assertTrue(File("src/main/res/values/themes.xml").readText().contains("android:colorControlActivated\">@color/onyx_accent"))
        assertTrue(File("src/main/res/values/colors.xml").readText().contains("<color name=\"onyx_accent\">#FF2F6BF5</color>"))
    }

    @Test
    fun `le fil de sous-agent et le texte copiable restent selectionnables`() {
        // Les écrans qui montraient déjà du texte copiable ne perdent pas leur sélection.
        assertEquals(1, Regex("SelectionContainer \\{").findAll(app("ui/chat/SelectableTextScreen.kt")).count())
        assertEquals(1, Regex("SelectionContainer \\{").findAll(app("ui/chat/SubagentTranscriptScreen.kt")).count())
    }
}
