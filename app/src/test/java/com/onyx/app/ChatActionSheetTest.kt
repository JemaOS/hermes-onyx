package com.onyx.app

import com.onyx.app.ui.chat.components.ChatActionBadge
import com.onyx.app.ui.chat.components.ChatActionTint
import com.onyx.app.ui.chat.components.ChatSheetAction
import com.onyx.app.ui.chat.components.ChatSheetRow
import com.onyx.app.ui.chat.components.chatSessionIdForCopy
import com.onyx.app.ui.chat.components.chatSheetOrder
import com.onyx.app.ui.chat.components.chatSheetRows
import com.onyx.app.ui.chat.components.chatTintFor
import com.onyx.app.ui.design.MenuItem
import com.onyx.app.ui.design.OnyxContrast
import com.onyx.app.ui.design.Symbols
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La feuille des actions d'une conversation : sa forme, ses lignes, et ce qu'elle ne doit pas
 * perdre.
 *
 * Trois contrats y sont vérifiés :
 *
 *  1. **une ligne fait une chose** — chaque libellé porte sa propre action, aucune n'est avalée
 *     par sa voisine ;
 *  2. **rien n'est perdu** — toutes les entrées que le menu flottant proposait existent encore
 *     dans la feuille, et l'ordre de la feuille les couvre toutes ;
 *  3. **les trois fermetures existent** — appui à l'extérieur, glissement vers le bas, retour
 *     matériel — et l'identifiant de session se copie **entier**.
 */
class ChatActionSheetTest {

    private val sheetSource = File("src/main/java/com/onyx/app/ui/chat/components/ChatActionSheet.kt").readText()
    private val screenSource = File("src/main/java/com/onyx/app/ui/chat/ChatScreen.kt").readText()
    private val appSource = File("src/main/java/com/onyx/app/ui/OnyxApp.kt").readText()

    /**
     * Les entrées que l'app **déclare** (des `MenuItem` d'`OnyxApp`), y compris celle du nouveau
     * départ : ce sont elles que `ChatScreen` remet à la feuille.
     */
    private val declaredEntries = listOf(
        "Find in conversation",
        "Rename",
        "Export as Markdown",
        "New Conversation",
        "Start fresh",
        "Take a note",
        "Computer",
        "Terminal",
        "Settings",
    )

    /** Les entrées que le menu flottant proposait, et qui doivent survivre à la refonte. */
    private val existingEntries = declaredEntries.filter { it != "Start fresh" }

    /** Les quatre lignes que la feuille possède en propre (elle les construit elle-même). */
    private val sheetOwnedEntries = listOf(
        "Usage & cost",
        "Background processes",
        "Restore points",
        "Copy session id",
    )

    // -- Ce que la feuille contient ----------------------------------------------------------

    @Test
    fun `la feuille porte toutes les entrees, anciennes et nouvelles`() {
        val declared = declaredEntries.map { item -> MenuItem(item, Symbols.Ellipsis) {} }
        val labels = chatSheetRows(declared).map { it.label }.toSet()
        (existingEntries + sheetOwnedEntries + "Start fresh").forEach { label ->
            assertTrue("« $label » manque dans la feuille", label in labels)
        }
    }

    @Test
    fun `chaque entree declairee garde exactement son action`() {
        // Chaque ligne rappelle **sa** lambda, une fois : aucun appui ne tombe sur l'action du
        // voisin. La preuve est un compteur par ligne, pas un libellé relu.
        val calls = existingEntries.associateWith { 0 }.toMutableMap()
        val items = existingEntries.map { label -> MenuItem(label, Symbols.Ellipsis) { calls[label] = calls[label]!! + 1 } }
        val rows = chatSheetRows(items)
        rows.filter { it.action == ChatSheetAction.SIMPLE }.forEach { it.onSelect() }
        calls.forEach { (label, count) ->
            assertEquals("« $label » doit agir une fois et une seule", 1, count)
        }
        assertEquals(existingEntries.size, calls.values.sum())
    }

    @Test
    fun `les cinq lignes de la feuille ont chacune leur action`() {
        val rows = chatSheetRows(emptyList())
        val byLabel = rows.associateBy { it.label }
        assertEquals(ChatSheetAction.USAGE, byLabel.getValue("Usage & cost").action)
        assertEquals(ChatSheetAction.PROCESSES, byLabel.getValue("Background processes").action)
        assertEquals(ChatSheetAction.RESTORES, byLabel.getValue("Restore points").action)
        assertEquals(ChatSheetAction.COPY_ID, byLabel.getValue("Copy session id").action)
        // Les quatre ouvrent un panneau ou copient : aucune n'agit « en silence » par une lambda
        // vide — sauf le départ à neuf, qui vient de l'app et porte la sienne.
        assertTrue(rows.filter { it.action != ChatSheetAction.SIMPLE }.none { it.label == "Start fresh" })
    }

    @Test
    fun `l ordre de la feuille couvre toutes les entrees et les range par famille`() {
        val ordered = chatSheetRows(declaredEntries.map { MenuItem(it, Symbols.Ellipsis) {} })
            .map { it.label }
            .filter { it in declaredEntries }
        // Conversation, puis nouveau départ, puis analyse, restauration, identifiant, écrans.
        assertEquals(
            listOf(
                "Find in conversation",
                "Rename",
                "Export as Markdown",
                "Take a note",
                "Start fresh",
                "New Conversation",
                "Computer",
                "Terminal",
                "Settings",
            ),
            ordered,
        )
        // Une entrée imprévue (la ligne de projet) n'est jamais écartée : elle se pose en dernier.
        assertTrue(chatSheetOrder("Add to Project") < chatSheetOrder("Une ligne inconnue"))
        assertTrue(chatSheetOrder("Add to Project") > chatSheetOrder("Export as Markdown"))
    }

    @Test
    fun `une couleur par famille d action`() {
        assertEquals(ChatActionTint.CONVERSATION, chatTintFor("Find in conversation"))
        assertEquals(ChatActionTint.CONVERSATION, chatTintFor("Rename"))
        assertEquals(ChatActionTint.NOTE, chatTintFor("Take a note"))
        assertEquals(ChatActionTint.EXPORT, chatTintFor("Export as Markdown"))
        assertEquals(ChatActionTint.INSIGHT, chatTintFor("Usage & cost"))
        assertEquals(ChatActionTint.BACKGROUND, chatTintFor("Background processes"))
        assertEquals(ChatActionTint.RESTORE, chatTintFor("Restore points"))
        assertEquals(ChatActionTint.IDENTITY, chatTintFor("Copy session id"))
        assertEquals(ChatActionTint.SCREEN, chatTintFor("Settings"))
        assertEquals(ChatActionTint.DANGER, chatTintFor("Delete", destructive = true))
        // Une même famille pour ce qui se ressemble : les écrans entre eux, les actions de
        // conversation entre elles.
        assertEquals(chatTintFor("Computer"), chatTintFor("Terminal"))
        assertNotEquals(chatTintFor("Usage & cost"), chatTintFor("Settings"))
    }

    // -- La copie de l'identifiant -----------------------------------------------------------

    @Test
    fun `l identifiant de session se copie entier, sur ses 20 caracteres`() {
        // Le défaut déjà corrigé dans l'app : un identifiant de 20 caractères collé en arrivait à
        // 6. La copie ne rogne rien — la longueur se vérifie sur la valeur rendue.
        val id = "api-6796f7daf00648a4"
        val copied = chatSessionIdForCopy(id)
        assertEquals(20, copied!!.length)
        assertEquals(id, copied)
        // Une clé de session Hermes (22 caractères) passe entière elle aussi.
        val ledger = "20261025_120509_3e8f42"
        assertEquals(ledger.length, chatSessionIdForCopy(ledger)!!.length)
        // Rien à copier : une valeur vide ou blanche n'est pas un identifiant.
        assertNull(chatSessionIdForCopy(null))
        assertNull(chatSessionIdForCopy(""))
        assertNull(chatSessionIdForCopy("   "))
    }

    @Test
    fun `la copie met l identifiant entier dans le presse-papiers et le dit`() {
        // Le texte posé dans le presse-papiers est l'identifiant lui-même, pas une coupe.
        assertTrue(
            "le presse-papiers doit recevoir l'identifiant entier",
            sheetSource.contains("ClipData.newPlainText(\"session\", id)"),
        )
        // La confirmation est visible **dans la feuille**, avec la longueur copiée.
        assertTrue(sheetSource.contains("Session id copied (%1\\\$d characters)"))
        assertTrue(sheetSource.contains("id.length"))
        assertTrue(
            "la confirmation doit s'afficher dans la feuille, pas seulement dans un toast",
            sheetSource.contains("notice?.let"),
        )
    }

    // -- Les trois fermetures ----------------------------------------------------------------

    @Test
    fun `la feuille se ferme par le dehors, par le glissement et par le retour`() {
        // 1. Un appui à l'extérieur : le voile porte la fermeture.
        assertTrue(sheetSource.contains(".onyxTappable(onClick = onDismiss)"))
        // 2. Le glissement vers le bas : un seuil, et la feuille repart au repos sinon.
        assertTrue(sheetSource.contains("DismissThreshold"))
        assertTrue(sheetSource.contains("if (drag > thresholdPx) onDismiss() else drag = 0f"))
        // 3. Le retour matériel : la fenêtre de dialogue ferme sur son `onDismissRequest`, et le
        //    panneau ouvert le prend explicitement pour rendre la feuille des actions — un seul
        //    gestionnaire, puisque la feuille et le panneau vivent dans la même fenêtre.
        assertTrue(sheetSource.contains("onDismissRequest = onDismiss"))
        assertTrue(sheetSource.contains("BackHandler(enabled = panel != null)"))
    }

    // -- L'empilement feuille → panneau --------------------------------------------------------

    @Test
    fun `le panneau remplace la feuille, aucune ligne n est recouverte`() {
        // Le défaut signalé : le panneau montait **par-dessus** la feuille, dans une seconde fenêtre,
        // et « Copier l'identifiant de session » — la dernière ligne — était coupée en deux. La
        // feuille n'est donc plus dessinée quand un panneau est ouvert.
        assertTrue("la feuille ne s'affiche que sans panneau", sheetSource.contains("if (panel == null) {"))
        assertTrue("le panneau est une surface de la maison", sheetSource.contains("ChatSheetPanel("))
        assertFalse(
            "plus de seconde fenêtre par-dessus la feuille",
            sheetSource.contains("ChatSheetDetail("),
        )
        // Le panneau a son **propre** défilement, plus haut que la feuille (il ne partage plus sa place).
        assertTrue(sheetSource.contains(".heightIn(max = 560.dp)"))
    }

    @Test
    fun `le panneau porte un retour visible, pas seulement le geste du systeme`() {
        assertTrue(
            "la ligne de retour est écrite dans la feuille",
            sheetSource.contains("\"Back to the actions\""),
        )
        assertTrue(
            "et elle ramène à la feuille",
            sheetSource.contains(".onyxTappable(onClick = onBack)"),
        )
        // Le titre de la feuille elle-même se coupe à une frontière de mot, comme la barre du haut.
        assertTrue(
            "le titre de la feuille suit la même règle de coupe",
            sheetSource.contains("measurer.headerLine(title, titleStyle, budget)"),
        )
    }

    @Test
    fun `un echec de lecture se dit en francais et propose de relire`() {
        // Le code brut du serveur (« session not found ») ne s'affiche plus tel quel : il passe par
        // la traduction des refus connus.
        assertTrue(sheetSource.contains("ServeChatActions.failureReason"))
        assertTrue(sheetSource.contains("internal fun readingFailure("))
        assertFalse(
            "la raison brute du serveur n'est plus peinte telle quelle",
            sheetSource.contains("DetailNote(state.reason)"),
        )
        // L'échec explique ce qui se passe et offre une action utile : relire.
        assertTrue(sheetSource.contains("ReadingFailure(state.reason, onRetry)"))
        assertTrue(sheetSource.contains("\"Retry the reading\""))
        assertTrue(
            "l'état dit aussi que la lecture n'existe que pour une conversation en cours",
            sheetSource.contains("Usage and its lists are only available while the conversation is running."),
        )
        assertTrue("relire relance vraiment la lecture", sheetSource.contains("LaunchedEffect(open, reload)"))
    }

    @Test
    fun `la feuille est ancree en bas, arrondie en haut, avec une poignee`() {
        assertTrue(sheetSource.contains("RoundedCornerShape(topStart = SheetCorner, topEnd = SheetCorner)"))
        assertTrue(sheetSource.contains("Modifier.align(Alignment.BottomCenter)"))
        assertTrue(sheetSource.contains("SheetHandleWidth"))
    }

    @Test
    fun `sous reduce motion la feuille est figee`() {
        // Aucune animation d'entrée : la feuille apparaît à sa place, sans glissement.
        assertTrue(sheetSource.contains("EnterTransition.None"))
        assertTrue(sheetSource.contains("val reduceMotion = LocalReduceMotion.current"))
    }

    @Test
    fun `chaque ligne offre une cible de 48 dp et separe par l air, sans trait`() {
        // 52 dp de haut : au-dessus du plancher tactile, et la ligne se tape d'un doigt.
        assertTrue(sheetSource.contains("SheetRowMinHeight = 52.dp"))
        // Aucun filet de séparation dans la feuille (le modèle n'en a pas).
        assertFalse("la feuille ne doit pas tracer de filet", sheetSource.contains("OnyxRowDivider"))
        assertTrue(sheetSource.contains(".heightIn(min = SheetRowMinHeight)"))
    }

    @Test
    fun `les etats vides du serveur sont dits, pas seulement dessines`() {
        assertTrue(sheetSource.contains("This server would not give the background processes of this conversation."))
        assertTrue(sheetSource.contains("Nothing is running in the background for this conversation."))
        assertTrue(sheetSource.contains("This server would not give the restore points of this conversation."))
        assertTrue(sheetSource.contains("No restore point for this conversation yet."))
        // Un échec de lecture n'est pas une liste vide : la branche insiste.
        assertTrue(sheetSource.contains("is LoadState.Failed ->"))
        assertTrue(sheetSource.contains("is LoadState.Ready ->"))
    }

    // -- La parité de contraste des pastilles ------------------------------------------------

    @Test
    fun `l encre d une pastille d icone tient le seuil de 3 pour 1`() {
        // La teinte des familles, sur une surface claire et sur une surface sombre : l'icône
        // doit se lire dans les deux cas, sinon la pastille ne dit rien.
        val lightSurface = androidx.compose.ui.graphics.Color(0xFFF2F2F7)
        val darkSurface = androidx.compose.ui.graphics.Color(0xFF1C1C1E)
        val hues = listOf(
            androidx.compose.ui.graphics.Color(0xFF0A84FF),
            androidx.compose.ui.graphics.Color(0xFFFF9F0A),
            androidx.compose.ui.graphics.Color(0xFF30D158),
            androidx.compose.ui.graphics.Color(0xFFFF453A),
            androidx.compose.ui.graphics.Color(0xFF8E8E93),
        )
        hues.forEach { hue ->
            listOf(lightSurface, darkSurface).forEach { surface ->
                val fill = ChatActionBadge.fill(hue, surface)
                val ink = ChatActionBadge.ink(hue, surface)
                val ratio = OnyxContrast.ratio(ink, fill)
                assertTrue(
                    "icône illisible sur sa pastille (rapport $ratio)",
                    // Le seuil, plus la marge d'antialias que la maison exige.
                    ratio >= OnyxContrast.ICON_MIN + ChatActionBadge.INK_MARGIN,
                )
                // L'encre **garde la famille** : elle est la teinte approfondie, jamais un gris.
                // Une teinte bleue doit rester plus bleue que rouge, une orange plus rouge que bleue.
                assertEquals(
                    "la famille de la teinte est perdue à l'encrage",
                    hue.blue > hue.red,
                    ink.blue > ink.red,
                )
            }
        }
        // Le fond d'une pastille reste une teinte diluée : jamais la surface elle-même.
        assertNotEquals(lightSurface, ChatActionBadge.fill(hues.first(), lightSurface))
    }

    // -- La refonte n a pas touché à ce qu elle ne devait pas ---------------------------------

    @Test
    fun `le menu flottant ne sert plus a la conversation, mais reste a la piece jointe`() {
        // Le « + » du composeur garde son menu : seul le « … » de la conversation passe à la
        // feuille.
        assertFalse(
            "la conversation ne doit plus ouvrir OnyxMenu",
            screenSource.contains("OnyxMenu(\n                items = items,"),
        )
        assertTrue(screenSource.contains("ChatActionSheet("))
        assertTrue(screenSource.contains("showMoreMenu = false },"))
        assertTrue("le menu du « + » reste en place", screenSource.contains("if (showAddMenu) {"))
    }

    @Test
    fun `le titre de la feuille vient de la conversation ou du bot`() {
        // Le nom donné à la conversation d'abord ; sinon le libellé de qui parle (le bot).
        assertTrue(screenSource.contains("title = state.conversationName?.takeIf { it.isNotBlank() } ?: threadSpeaker.label"))
    }

    @Test
    fun `repartir de zero demande confirmation et garde l ancienne conversation`() {
        // La question existe, et sa phrase dit que l'ancienne reste dans l'historique.
        assertTrue(appSource.contains("if (confirmingFresh) {"))
        assertTrue(appSource.contains("message = \"A new conversation starts with the same bot"))
        assertTrue(appSource.contains("stays in your history, whole."))
        // Le corps est écrit une fois, et la conversation repart avec **le même bot**.
        assertTrue(appSource.contains("fun startFreshConversation() {"))
        assertTrue(appSource.contains("bot = openConversationBot,"))
        assertTrue(appSource.contains("onNewConversation = { startFreshConversation() }"))
    }
}
