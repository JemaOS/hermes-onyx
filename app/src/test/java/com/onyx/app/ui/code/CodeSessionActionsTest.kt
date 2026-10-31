package com.onyx.app.ui.code

import com.onyx.app.localization.OnyxLocalization
import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les actions d'une fiche de code : renommer, modifier, relancer, supprimer — **atteignables**.
 *
 * Le défaut du propriétaire : une session listée n'était « ni modifiable, ni renommable, ni
 * supprimable ». Ce test lit le câblage réel et exige les trois choses qui font qu'un geste existe :
 * un **appui long** sur la carte, un bouton **⋮** visible, et un menu **ancré à la carte** — le
 * panneau maison, celui des bots et des conversations, jamais un panneau au centre de l'écran.
 */
class CodeSessionActionsTest {

    private val ui = File("src/main/java/com/onyx/app/ui")
    private val screen = File(ui, "code/CodeScreen.kt").readText()
    private val actions = File(ui, "code/CodeSessionActions.kt").readText()
    private val viewModel = File(ui, "code/CodeViewModel.kt").readText()
    private val menu = File("../core/src/main/java/com/onyx/app/ui/design/OnyxMenu.kt").readText()

    // MARK: - Les deux gestes, et le menu ancré maison

    @Test
    fun `la carte s ouvre par appui long ET par un bouton visible`() {
        // L'appui long de la carte : le geste qu'on essaie par habitude.
        assertTrue("la carte a l'appui long", screen.contains("onyxTappable(onLongPress = onLongPress, onClick = onClick)"))
        // Et le bouton ⋮, à la vue de tous, avec sa description pour l'accessibilité.
        assertTrue("le bouton ⋮ existe", screen.contains("Symbols.Ellipsis"))
        assertTrue("le bouton ⋮ s'annonce", screen.contains("contentDescription = \"Card menu\""))
        assertTrue("le bouton ⋮ agit", screen.contains("onyxTappable(onLongPress = onMore) { onMore() }"))
        // Les deux chemins ouvrent le **même** menu, ancré à ce qu'on a touché.
        assertTrue("l'appui long ancre au carte", screen.contains("menuAnchor = bounds[session.id]"))
        assertTrue("le ⋮ ancre au bouton", screen.contains("menuAnchor = bounds[moreKey(session.id)]"))
        assertTrue("la carte publie ses limites", screen.contains("onBounds = { bounds[session.id] = it }"))
        assertTrue("le bouton aussi", screen.contains("onMoreBounds = { bounds[moreKey(session.id)] = it }"))
    }

    @Test
    fun `le menu est celui de la maison, ancre a la carte`() {
        // Le panneau est `OnyxMenu` — celui des bots et des conversations, pas un composant neuf.
        assertTrue("le menu maison", actions.contains("OnyxMenu("))
        assertTrue("il prend une ancre", actions.contains("anchor = anchor"))
        assertTrue("l'appel le lui passe", screen.contains("anchor = menuAnchor,"))
        // Et ce panneau se pose contre la rangée touchée, jamais au centre, jamais hors de l'écran.
        assertTrue(menu.contains("anchor: Rect? = null"))
        assertTrue(menu.contains("boundsInWindow()"))
        assertTrue(menu.contains("coerceIn(marginPx"))
    }

    @Test
    fun `le menu se ferme au clic exterieur et au retour`() {
        // Le voile porte la fermeture, le panneau avale l'appui (un appui entre deux lignes ne
        // ferme pas la feuille par accident), et Échap / le retour aboutissent au même état.
        assertTrue(menu.contains(".onyxTappable(onClick = onDismiss)"))
        assertTrue(menu.contains(".onyxTappable(onClick = {})"))
        assertTrue(menu.contains("Key.Escape"))
        assertTrue(menu.contains("Dialog(onDismissRequest = onDismiss"))
        // Le menu de la fiche est bien refermé par son propre rappel.
        assertTrue(screen.contains("onDismiss = { menuSession = null }"))
    }

    // MARK: - Les quatre actions

    @Test
    fun `les quatre actions, dans l ordre, la suppression a part`() {
        val labels = CodeSessionAction.entries.map { codeSessionActionLabel(it) }
        assertEquals(listOf("Rename", "Edit…", "Run again", "Delete"), labels)
        // Chacune son icône maison (le style du dépôt : la correspondance se lit dans la source).
        assertTrue(actions.contains("CodeSessionAction.Rename -> Symbols.Pencil"))
        assertTrue(actions.contains("CodeSessionAction.Edit -> Symbols.SquareAndPencil"))
        assertTrue(actions.contains("CodeSessionAction.RunAgain -> Symbols.ArrowClockwise"))
        assertTrue(actions.contains("CodeSessionAction.Delete -> Symbols.Trash"))
        // La suppression est destructrice, et séparée des trois autres par un filet.
        assertTrue("la suppression est en rouge", actions.contains("destructive = action == CodeSessionAction.Delete"))
        assertTrue("et elle a son filet", actions.contains("groupBreak = action == CodeSessionAction.Delete"))
    }

    @Test
    fun `chaque action agit, aucune ligne ne reste inerte`() {
        assertTrue("renommer ouvre l'invite", actions.contains("CodeSessionAction.Rename -> onRename(session)"))
        assertTrue("modifier ouvre la feuille", actions.contains("CodeSessionAction.Edit -> onEdit(session)"))
        assertTrue("relancer relance", actions.contains("CodeSessionAction.RunAgain -> onRunAgain(session)"))
        assertTrue("supprimer demande confirmation", actions.contains("CodeSessionAction.Delete -> onDelete(session)"))
        // Et l'écran fournit les quatre gestes, dans l'ordre.
        assertTrue(screen.contains("onRename = { pick ->"))
        assertTrue(screen.contains("onEdit = viewModel::beginEdit"))
        assertTrue(screen.contains("onRunAgain = viewModel::rerun"))
        assertTrue(screen.contains("onDelete = { pick -> deleting = pick }"))
    }

    // MARK: - Renommer

    @Test
    fun `renommer valide, et enregistre tout de suite`() {
        assertTrue("l'invite maison", actions.contains("OnyxPrompt("))
        assertTrue("un titre vide ne valide pas", actions.contains("confirmEnabled = value.trim().isNotEmpty()"))
        assertTrue("rien à enregistrer, rien qui s'écrit", actions.contains("value.trim() != session.title"))
        assertTrue("on enregistre", actions.contains("confirmLabel = \"Save\""))
        // Le geste écrit la fiche immédiatement, sans passer par un bouton « Enregistrer » ailleurs.
        assertTrue(screen.contains("viewModel.rename(session, renameText)"))
        assertTrue(viewModel.contains("fun rename(session: CodeSession, title: String)"))
        assertTrue("écrit tout de suite", viewModel.contains("store.save(serverKey(), next)"))
    }

    // MARK: - Modifier

    @Test
    fun `modifier donne le depot, la copie, la branche et la demande`() {
        assertTrue("la feuille existe", actions.contains("internal fun CodeEditSheet("))
        // Les deux puces du serveur, avec le même menu ancré que le compositeur.
        assertTrue("la puce dépôt", actions.contains("onClick = { picker = \"repo\" }"))
        assertTrue("la puce copie de travail", actions.contains("onClick = { picker = \"worktree\" }"))
        assertTrue("le menu du dépôt est ancré", actions.contains("anchor = repoBounds,"))
        assertTrue("celui de la copie aussi", actions.contains("anchor = worktreeBounds,"))
        // Et les champs que la personne peut corriger.
        assertTrue("le titre", actions.contains("EditField(label = \"Title\""))
        assertTrue("la branche", actions.contains("EditField(label = \"Branch\""))
        assertTrue("la demande, sur plusieurs lignes", actions.contains("text = \"Request\""))
        assertTrue("et son champ suit", actions.contains("onValueChange = onPrompt"))
        // Les deux puces disent la même chose que le compositeur : la clé passe au dictionnaire.
        assertTrue("dépôt traduit", actions.contains("OnyxLocalization[\"Repository\"]"))
        assertTrue("copie de travail traduite", actions.contains("OnyxLocalization[\"New worktree\"]"))
        // Rien ne s'écrit avant « Enregistrer » : la feuille travaille sur une copie.
        assertTrue("la fiche gardée à part", screen.contains("state.editing?.let { session ->"))
        assertTrue("on n'écrit qu'à l'enregistrement", viewModel.contains("fun saveEdit()"))
    }

    // MARK: - Relancer

    @Test
    fun `relancer rejoue la demande dans une session neuve`() {
        assertTrue("la demande est gardée avec la fiche", viewModel.contains("prompt = text,"))
        assertTrue("la relance la reprend", viewModel.contains("session.prompt?.takeIf { it.isNotBlank() } ?: session.title"))
        assertTrue("une vraie session est ouverte", viewModel.contains("starter(text, cwd)"))
        assertTrue("la fiche est neuve", viewModel.contains("session.relaunched(conversationId, now)"))
        // Une fiche neuve : identifiant neuf, en cours, sans clé — jamais la même fiche réécrite.
        val model = File("../core/src/main/java/com/onyx/app/data/model/CodeSession.kt").readText()
        assertTrue(model.contains("fun CodeSession.relaunched(id: String, atMillis: Long)"))
        assertTrue(model.contains("sessionKey = null,"))
    }

    // MARK: - Supprimer

    @Test
    fun `supprimer demande confirmation, et dit que la conversation reste`() {
        assertTrue("la confirmation", actions.contains("internal fun CodeDeleteAlert("))
        assertTrue("le titre", actions.contains("title = \"Delete this session?\""))
        assertTrue(
            "la conséquence est dite",
            actions.contains("message = \"The card is removed from this phone. The conversation stays in your history.\""),
        )
        assertTrue("deux issues : annuler, puis supprimer", actions.contains("AlertAction(label = \"Cancel\""))
        assertTrue("la suppression est destructrice", actions.contains("AlertAction(label = \"Delete\", destructive = true"))
        // L'écran confirme avant d'effacer : le menu ne supprime jamais d'un coup.
        assertTrue(screen.contains("deleting?.let { session ->"))
        assertTrue(screen.contains("viewModel.delete(session)"))
    }

    @Test
    fun `supprimer une fiche ne touche pas la conversation`() {
        val delete = viewModel.substringAfter("fun delete(session: CodeSession)")
            .substringBefore("fun rerun(")
        assertTrue("le magasin local est réécrit", delete.contains("store.remove(serverKey(), session.id)"))
        assertTrue("la liste vient du disque", delete.contains("store.sessions(serverKey())"))
        // Aucun appel au serveur, aucun tour arrêté, aucune conversation touchée : la fiche
        // seulement.
        assertFalse("aucun démarrage de session", delete.contains("starter("))
        assertFalse("aucun appel git", delete.contains("git."))
        assertFalse("aucun tour touché", delete.contains("turns."))
    }

    @Test
    fun `la fiche est tenue sur le téléphone, et y survit`() {
        assertTrue(
            "le magasin local des sessions de code",
            File(ui, "code/CodeViewModel.kt").readText().contains("\"onyx_code_sessions\""),
        )
        val store = File("../core/src/main/java/com/onyx/app/data/local/CodeSessionStore.kt").readText()
        assertTrue("les fiches sont écrites sous une clé stable", store.contains("const val KEY = \"code_sessions\""))
        assertTrue("et relues au lancement", viewModel.contains("store.sessions(serverKey())"))
    }

    // MARK: - En français, par le dictionnaire

    @Test
    fun `les actions et la confirmation sont en francais`() {
        val fr = Locale.FRANCE
        assertEquals("Renommer", OnyxLocalization.translate("Rename", fr))
        assertEquals("Modifier…", OnyxLocalization.translate("Edit…", fr))
        assertEquals("Relancer", OnyxLocalization.translate("Run again", fr))
        assertEquals("Supprimer", OnyxLocalization.translate("Delete", fr))
        assertEquals("Supprimer cette session ?", OnyxLocalization.translate("Delete this session?", fr))
        assertEquals(
            "La fiche est retirée de ce téléphone. La conversation reste dans l’historique.",
            OnyxLocalization.translate(
                "The card is removed from this phone. The conversation stays in your history.",
                fr,
            ),
        )
        assertEquals("Modifier la session", OnyxLocalization.translate("Edit session", fr))
        assertEquals("Titre", OnyxLocalization.translate("Title", fr))
        assertEquals("Demande", OnyxLocalization.translate("Request", fr))
        assertEquals("Annuler", OnyxLocalization.translate("Cancel", fr))
        assertEquals("Enregistrer", OnyxLocalization.translate("Save", fr))
    }
}
