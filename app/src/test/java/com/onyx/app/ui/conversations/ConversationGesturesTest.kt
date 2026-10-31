package com.onyx.app.ui.conversations

import com.onyx.app.data.model.ChatMessage
import com.onyx.app.data.model.Conversation
import com.onyx.app.data.model.Role
import com.onyx.app.data.repo.forkTitle
import com.onyx.app.data.repo.forkedConversation
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les **deux gestes** d'une rangée de conversation, portés sur le **volet** — l'écran des
 * conversations a été retiré, et ses gestes ne doivent pas disparaître avec lui.
 *
 * 1. **Le glissement** prend les **deux directions** : vers la **droite** il découvre la pilule
 *    « Épingler » à gauche, vers la **gauche** la pilule « Archiver » à droite — deux pilules
 *    claires, sans couleur saturée, et la rangée qui devient une carte à coins arrondis pendant le
 *    geste. Ce qui cohabite avec le **geste de bord** du volet — le geste principal du
 *    propriétaire — n'est plus une direction interdite mais une **zone morte** au bord : un doigt
 *    posé dans la bande réservée par le système n'est jamais revendiqué par la rangée, donc le
 *    volet s'ouvre toujours depuis le bord. C'est le test de la zone morte qui le tient.
 * 2. **L'appui long** ouvre la **feuille du bas** : le nom de la conversation, sa dernière activité,
 *    puis une ligne par action — renommer, créer un fil, épingler/détacher, archiver/désarchiver,
 *    supprimer. Les lignes se désactivent quand l'action n'a pas de sens.
 *
 * Et deux garde-fous du propriétaire : **l'archivage ne rend rien inatteignable** (une conversation
 * archivée reste une rangée et se **désarchive**), et **rien ne disparaît** (les actions du menu
 * flottant d'avant survivent dans la feuille).
 */
class ConversationGesturesTest {

    private val sidebar = File("src/main/java/com/onyx/app/ui/sidebar/ChatSidebar.kt").readText()
    private val sheet = File("src/main/java/com/onyx/app/ui/conversations/ConversationActionSheet.kt").readText()
    private val app = File("src/main/java/com/onyx/app/ui/OnyxApp.kt").readText()
    private val repo = File("src/main/java/com/onyx/app/data/repo/ConversationRepository.kt").readText()

    private fun conversation(
        pinned: Boolean = false,
        archived: Boolean = false,
        hasMessages: Boolean = true,
    ) = Conversation(
        id = "conv-1",
        title = "Where is the key?",
        createdAt = 0L,
        updatedAt = 7_200_000L,
        pinned = pinned,
        archived = archived,
        messages = if (hasMessages) {
            listOf(ChatMessage(id = "m1", role = Role.USER, text = "Where is the key?", createdAt = 1L))
        } else {
            emptyList()
        },
    )

    private fun rowsOf(
        conversation: Conversation,
        canMarkUnread: Boolean = true,
        onToggleUnread: (() -> Unit)? = { },
        muteRows: List<ConversationSheetRow> = emptyList(),
    ): List<ConversationSheetRow> = conversationSheetRows(
        conversation = conversation,
        canMarkUnread = canMarkUnread,
        muted = false,
        onRename = {},
        onCreateThread = {},
        onTogglePin = {},
        onToggleArchive = {},
        onDelete = {},
        onToggleUnread = onToggleUnread,
        // Les compléments que la rangée offrait déjà : ils doivent survivre dans la feuille.
        onUsage = {},
        onCopyTitle = {},
        onOpenElsewhere = {},
        muteRows = muteRows,
    )

    // -- La feuille : ses lignes ---------------------------------------------------------------

    @Test
    fun `les cinq actions demandees sont dans la feuille`() {
        val labels = rowsOf(conversation()).map { it.label }
        listOf("Rename", "Create thread", "Pin", "Archive", "Delete").forEach { label ->
            assertTrue("« $label » manque dans la feuille", label in labels)
        }
    }

    @Test
    fun `la feuille met supprimer en dernier, et en rouge`() {
        // Le destructeur est la dernière ligne, comme dans la référence — même quand des lignes du
        // menu flottant s'ajoutent après les cinq actions.
        val rows = rowsOf(
            conversation(),
            muteRows = listOf(
                ConversationSheetRow("Mute", 0, ConversationActionTint.MUTE),
                ConversationSheetRow("Mute for 1 hour", 0, ConversationActionTint.MUTE),
            ),
        )
        assertEquals("Delete", rows.last().label)
        assertTrue("la ligne de suppression est destructrice", rows.last().destructive)
        assertEquals(ConversationActionTint.DANGER, rows.last().tint)
    }

    @Test
    fun `chaque ligne porte son icone et sa famille de teinte`() {
        val rows = rowsOf(conversation()).associateBy { it.label }
        assertEquals(ConversationActionTint.CONVERSATION, rows.getValue("Rename").tint)
        assertEquals(ConversationActionTint.THREAD, rows.getValue("Create thread").tint)
        assertEquals(ConversationActionTint.PIN, rows.getValue("Pin").tint)
        assertEquals(ConversationActionTint.ARCHIVE, rows.getValue("Archive").tint)
        assertEquals(ConversationActionTint.DANGER, rows.getValue("Delete").tint)
        // Chaque ligne a une icône (aucune n'est muette).
        rows.values.forEach { assertNotEquals("une ligne sans icône", 0, it.symbol) }
    }

    @Test
    fun `la ligne epingler devient detacher, et son action suit`() {
        var toggled = 0
        val pinned = conversationSheetRows(
            conversation = conversation(pinned = true),
            canMarkUnread = false,
            muted = false,
            onRename = {},
            onCreateThread = {},
            onTogglePin = { toggled++ },
            onToggleArchive = {},
            onDelete = {},
        )
        val row = pinned.first { it.tint == ConversationActionTint.PIN }
        assertEquals("Unpin", row.label)
        assertTrue("une conversation épinglée garde une ligne active", row.enabled)
        row.onSelect()
        assertEquals("l'appui appelle bien l'action de la ligne", 1, toggled)

        // Non épinglée : « Épingler ».
        val notPinned = rowsOf(conversation()).first { it.tint == ConversationActionTint.PIN }
        assertEquals("Pin", notPinned.label)
    }

    @Test
    fun `la ligne archiver devient desarchiver`() {
        val archived = conversationSheetRows(
            conversation = conversation(archived = true),
            canMarkUnread = false,
            muted = false,
            onRename = {},
            onCreateThread = {},
            onTogglePin = {},
            onToggleArchive = {},
            onDelete = {},
        )
        assertEquals("Unarchive", archived.first { it.tint == ConversationActionTint.ARCHIVE }.label)
        assertEquals("Archive", rowsOf(conversation()).first { it.tint == ConversationActionTint.ARCHIVE }.label)
    }

    @Test
    fun `creer un fil se desactive sur une conversation vide`() {
        // Il n'y a rien à recopier : la ligne reste visible mais inerte.
        val empty = conversationSheetRows(
            conversation = conversation(hasMessages = false),
            canMarkUnread = false,
            muted = false,
            onRename = {},
            onCreateThread = {},
            onTogglePin = {},
            onToggleArchive = {},
            onDelete = {},
        ).first { it.label == "Create thread" }
        assertFalse("une conversation vide n'a rien à recopier", empty.enabled)

        val full = rowsOf(conversation()).first { it.label == "Create thread" }
        assertTrue("une conversation avec un fil peut être recopiée", full.enabled)
    }

    @Test
    fun `les actions du menu flottant survivent dans la feuille`() {
        // Rien ne devient inatteignable : ce que la rangée offrait en plus des cinq actions y est —
        // marquer non lu, usage, copier le titre, se taire, ouvrir ailleurs.
        val rows = rowsOf(conversation()).map { it.label }
        assertTrue("marquer non lu", "Mark as Unread" in rows)
        assertTrue("usage", "Usage & cost" in rows)
        assertTrue("copier le titre", "Copy title" in rows)
        assertTrue("ouvrir ailleurs", "Open in New Window" in rows)
        // La conversation ouverte ne se marque pas : la ligne n'apparaît pas.
        assertFalse(
            "la conversation ouverte ne propose pas de se marquer non lue",
            "Mark as Unread" in rowsOf(conversation(), canMarkUnread = false).map { it.label },
        )
        // « Se taire » : les lignes du silence la rejoignent, jamais une seconde feuille.
        val muted = conversationSheetRows(
            conversation = conversation(),
            canMarkUnread = false,
            muted = true,
            onRename = {},
            onCreateThread = {},
            onTogglePin = {},
            onToggleArchive = {},
            onDelete = {},
            muteRows = listOf(ConversationSheetRow("Unmute", 0, ConversationActionTint.MUTE)),
        ).map { it.label }
        assertTrue("se taire / se rallumer", "Unmute" in muted)
    }

    @Test
    fun `le sous-titre dit la derniere activite, jamais un horodatage brut`() {
        val subtitle = conversationActivitySubtitle(updatedAt = 0L, now = 7L * 3600L * 1000L)
        assertTrue("le sous-titre dit l'âge en heures", subtitle.contains("7"))
        assertTrue(
            "le sous-titre dit l'âge (« ago » ou « il y a »), pas un nombre de millisecondes",
            subtitle.contains("ago") || subtitle.contains("il y a"),
        )
        assertFalse("jamais l'horodatage brut", subtitle.contains("25920000"))
    }

    // -- Le glissement : les deux directions, et le conflit avec le geste de bord ---------------

    /** Les jetons du glissement : ils vivent dans **un seul** fichier, `SidebarSwipe.kt`. */
    private val swipeStyle = File("src/main/java/com/onyx/app/ui/sidebar/SidebarSwipe.kt").readText()

    private fun swipeBody(): String = sidebar
        .substringAfter("private fun SwipeableConversationRow(")
        .substringBefore("private fun SidebarSwipeAction(")

    @Test
    fun `le glissement de la rangee prend les deux directions`() {
        val swipe = swipeBody()
        // Le geste lit la **passe Initial** : il voit le doigt avant le clic de la rangée, sinon le
        // clic le prend et le glissement n'obtient qu'un doigt déjà levé.
        assertTrue("le geste lit le doigt avant le clic", swipe.contains("PointerEventPass.Initial"))
        assertTrue("vers la droite, l'action est l'épingle", swipe.contains("val pin = total >= threshold.value"))
        assertTrue("vers la gauche, l'action est l'archivage", swipe.contains("val archive = total <= -threshold.value"))
        assertTrue("la pilule de gauche est découverte par la droite", swipe.contains("slide.value > 0.5f"))
        assertTrue("la pilule de droite par la gauche", swipe.contains("slide.value < -0.5f"))
        assertTrue("c'est alors que la rangée consomme", swipe.contains("change.consume()"))
        // Le détecteur qui consommait **tout** horizontal (dont le geste du bord) a disparu.
        assertFalse(
            "le détecteur qui avalait le geste de bord ne doit plus être utilisé",
            swipe.contains("detectHorizontalDragGestures"),
        )
        assertFalse("aucun import du détecteur ne subsiste", sidebar.contains("import androidx.compose.foundation.gestures.detectHorizontalDragGestures"))
    }

    @Test
    fun `la zone morte du bord garde le geste de bord au systeme`() {
        val swipe = swipeBody()
        // Un doigt posé dans la bande que le système réserve au retour de bord n'est **jamais**
        // revendiqué par la rangée : c'est ce qui laisse le geste du bord ouvrir le volet.
        assertTrue(
            "vers la droite, la zone morte n'est pas revendiquée",
            swipe.contains("if (total > 0f && down.position.x <= deadZone.value) return@awaitEachGesture"),
        )
        assertTrue("la zone morte vient de la liste", sidebar.contains("rememberSidebarSwipeDeadZone()"))
        assertTrue("et elle est la bande du système plus la marge", swipeStyle.contains("WindowInsets.systemGestures"))
        assertTrue("le chiffre est journalisé à l'appareil", swipeStyle.contains("Log.i("))
    }

    @Test
    fun `le glissement garde son seuil, son retour en place et son geste franc`() {
        val swipe = swipeBody()
        assertTrue("la rangée devient une carte pendant le geste", swipe.contains("Modifier.sidebarSwipeCard()"))
        assertTrue("l'icône de l'épingle", swipe.contains("symbol = Symbols.Pin"))
        assertTrue("l'icône de l'archivage", swipe.contains("symbol = Symbols.ArchiveBox"))
        assertTrue("sous le seuil comme au-delà, la rangée revient en place", swipe.contains("slide.animateTo(0f, settle)"))
        assertTrue("un geste franc déclenche l'épingle", swipe.contains("pin -> onPin()"))
        assertTrue("et l'autre, l'archivage", swipe.contains("archive -> onArchive()"))
        assertFalse("aucune durée écrite à la main", swipe.contains("tween("))
    }

    // -- Le bandeau d'annulation --------------------------------------------------------------

    @Test
    fun `une action appliquee tout de suite laisse un retour en arriere`() {
        assertTrue("le bandeau existe", sheet.contains("fun ConversationUndoBar("))
        assertTrue("il porte « Undo »", sheet.contains("\"Undo\""))
        assertTrue("sa cible fait 48 dp", sheet.contains("UNDO_TARGET = 48.dp"))
        assertTrue("il s'efface tout seul", sheet.contains("UNDO_MILLIS"))
        // Il est posé par la liste du volet, et il défait l'action.
        assertTrue("le volet pose le bandeau", sidebar.contains("ConversationUndoBar("))
        assertTrue("le bandeau défait l'action", sidebar.contains("state.undo()"))
    }

    @Test
    fun `chaque action du glissement et de la feuille arme son annulation`() {
        // Épingler, archiver, supprimer : chacune passe par le geste « appliquer puis noter le
        // retour en arrière », jamais par une action sans filet.
        assertTrue(sidebar.contains("fun applyPin("))
        assertTrue(sidebar.contains("fun applyArchive("))
        assertTrue(sidebar.contains("fun applyDelete("))
        assertTrue("l'annulation d'un épinglage reprend l'état d'avant", sidebar.contains("actions.onTogglePin(!pinned)"))
        assertTrue("l'annulation d'un archivage reprend l'état d'avant", sidebar.contains("actions.onToggleArchive(!archived)"))
        assertTrue("l'annulation d'une suppression la remet", sidebar.contains("actions.onRestore()"))
    }

    // -- La suppression : confirmation explicite et phrase vraie -------------------------------

    @Test
    fun `la suppression demande confirmation et dit que la conversation quitte aussi le serveur`() {
        assertTrue("une confirmation explicite est posée", sidebar.contains("confirmDelete?.let"))
        assertTrue(
            "la phrase dit la vérité : elle quitte ce téléphone ET le serveur, définitivement",
            sidebar.contains("It leaves this phone and your server. This can't be undone."),
        )
        assertTrue("la confirmation est posée par le volet", sidebar.contains("OnyxConfirm("))
    }

    // -- L'archivage ne rend rien inatteignable -----------------------------------------------

    @Test
    fun `les conversations archivees restent atteignables et se retablissent`() {
        // Le volet ne cache pas les archives : sa liste ne filtre que la recherche et l'attention,
        // jamais l'état d'archive — aucune règle ne les retire. Une conversation archivée reste donc
        // une rangée, et son appui long propose de la désarchiver.
        val model = File("src/main/java/com/onyx/app/ui/sidebar/ChatSidebarModel.kt").readText()
        assertFalse("aucune règle de liste ne retire les archives", model.contains("archived"))
        assertTrue(
            "la ligne d'archive propose de désarchiver",
            sheet.contains("label = if (conversation.archived) \"Unarchive\" else \"Archive\""),
        )
        assertTrue("le glissement porte la même porte de sortie", sidebar.contains("\"Unarchive\" else \"Archive\""))
    }

    // -- « Créer un fil » : pas de route serveur, une branche locale ---------------------------

    @Test
    fun `creer un fil fait une branche locale, sans route serveur`() {
        val original = conversation()
        val branch = forkedConversation(original, id = "conv-2", now = 9_999L)
        assertNotEquals("la branche a son propre identifiant", original.id, branch.id)
        assertEquals("le titre dit que c'est une branche", "Where is the key? (fork)", branch.title)
        assertEquals("le transcript est recopié", original.messages, branch.messages)
        assertFalse("une branche neuve n'est pas épinglée", branch.pinned)
        assertFalse("une branche neuve n'est pas archivée", branch.archived)
        assertEquals("la branche est récente", 9_999L, branch.updatedAt)
        assertEquals("forkTitle ajoute « (fork) »", "T (fork)", forkTitle("T"))
        // La branche se fait localement : aucune route de fork serveur n'est appelée (le serveur
        // d'Onyx n'en a pas — `POST api/sessions/{id}/fork` n'existe pas).
        assertTrue("le dépôt offre bien la bifurcation", repo.contains("suspend fun fork(id: String"))
        assertFalse("aucun appel de fork serveur n'est fait", repo.contains("forkSession"))
    }

    @Test
    fun `creer un fil est relie a l'app, et ouvre la branche`() {
        assertTrue("le volet accepte la création d'un fil", sidebar.contains("onCreateThread()"))
        assertTrue("l'app branche la création d'un fil", app.contains("onCreateThreadConversation = { conversation ->"))
        assertTrue("la branche s'ouvre", app.contains("conversationRepo.fork(conversation.id)"))
        assertTrue("l'annulation d'une suppression est reliée", app.contains("onRestoreConversation = { conversation ->"))
    }

    // -- Le garde-fou : le volet ne peut pas perdre ses gestes ---------------------------------

    @Test
    fun `le volet ne peut pas perdre ses gestes`() {
        // Les deux gestes vivent dans les rangées du volet : le glissement vers la gauche et
        // l'appui long qui ouvre la feuille. Si l'un disparaît du fichier, la fonction demandée par
        // le propriétaire disparaît avec l'écran retiré — et ce test tombe.
        assertTrue("le glissement est branché sur les rangées", sidebar.contains("SwipeableConversationRow("))
        assertTrue("la liste reçoit de quoi glisser", sidebar.contains("rowActions = rowActions,"))
        assertTrue("l'appui long ouvre la feuille du bas", sidebar.contains("ConversationActionSheet("))
        assertTrue("le volet construit la feuille de la rangée", sidebar.contains("conversationSheetRows("))
        assertTrue(
            "le geste de bord garde son doigt dans la zone morte du bord",
            sidebar.contains("if (total > 0f && down.position.x <= deadZone.value) return@awaitEachGesture"),
        )
        assertFalse("le détecteur qui avalait le geste de bord est banni", sidebar.contains("detectHorizontalDragGestures"))
        // Une seule feuille maison : le volet réemploie le composant partagé, il n'en invente pas un
        // troisième.
        assertTrue("le volet emploie la feuille partagée", sheet.contains("fun ConversationActionSheet("))
        assertFalse("le volet ne définit pas sa propre feuille", sidebar.contains("fun ConversationActionSheet("))
    }
}
