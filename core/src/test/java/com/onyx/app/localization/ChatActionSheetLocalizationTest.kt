package com.onyx.app.localization

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les libellés de la feuille des actions d'une conversation passent tous par le dictionnaire
 * central — y compris ceux qui ne sont **pas** déclarés dans un `MenuItem` (les quatre lignes que
 * la feuille possède en propre, les titres de section, et les phrases d'état vide).
 *
 * C'est le trou que ce test ferme : `MenuAndSheetLocalizationTest` balaie les `MenuItem(…)` et les
 * `AlertAction(…)` de l'arbre, mais une ligne construite par la feuille elle-même (ou une note
 * d'état vide) lui échappe — et resterait en anglais sans que rien ne rougisse.
 */
class ChatActionSheetLocalizationTest {

    private val fr = Locale.FRANCE

    /** Les libellés de la feuille et de ses trois panneaux. */
    private val labels = linkedMapOf(
        "Start fresh" to "Repartir de zéro",
        "Start fresh?" to "Repartir de zéro ?",
        "Usage & cost" to "Consommation et coût",
        "Background processes" to "Processus en arrière-plan",
        "Restore points" to "Points de restauration",
        "Copy session id" to "Copier l'identifiant de session",
        "Cache share" to "Part du cache",
        "Context used" to "Contexte utilisé",
        "What is filling the window" to "Ce qui remplit la fenêtre",
        "Stop" to "Arrêter",
        "Restore" to "Restaurer",
        "Restored" to "Restauré",
        "Restore points" to "Points de restauration",
        "Process stopped" to "Processus arrêté",
    )

    /** Les phrases d'état des trois listes du serveur. */
    private val emptyStates = listOf(
        "This server would not give the counters of this conversation.",
        "This server gives no token count for this conversation yet.",
        "This server gives no price for this conversation; the counters above are all it has.",
        "The window is estimated from the text; the server did not give the exact count.",
        "Everything said here is read again with each message: this is what is filling the window.",
        "This server would not give the background processes of this conversation.",
        "Nothing is running in the background for this conversation.",
        "Running for %1\$s",
        "Failed (exit code %1\$d)",
        "This server would not give the restore points of this conversation.",
        "This server does not keep restore points for this folder.",
        "No restore point for this conversation yet.",
        "this conversation has no session id yet",
        "Session id copied (%1\$d characters)",
        "Couldn't copy it: %1\$s",
        "the server answered nothing",
        "this process is no longer running",
        "the server refused to stop it",
        "the server refused to restore it",
    )

    @Test
    fun `chaque libelle de la feuille a sa traduction francaise`() {
        labels.forEach { (english, french) ->
            assertEquals("français : $english", french, OnyxLocalization.translate(english, fr))
            // En anglais, rien ne bouge.
            assertEquals("anglais : $english", english, OnyxLocalization.translate(english, Locale.ENGLISH))
        }
    }

    @Test
    fun `aucune phrase d etat vide ne reste en anglais`() {
        val untranslated = emptyStates.filter { OnyxLocalization.translate(it, fr) == it }
        assertTrue("phrases d'état encore en anglais : $untranslated", untranslated.isEmpty())
    }

    @Test
    fun `les phrases de format gardent leurs arguments`() {
        assertEquals("En cours depuis 3 min", OnyxLocalization.format("Running for %1\$s", "3 min", locale = fr))
        assertEquals("Échoué (code 2)", OnyxLocalization.format("Failed (exit code %1\$d)", 2, locale = fr))
        assertEquals(
            "Identifiant de session copié (20 caractères)",
            OnyxLocalization.format("Session id copied (%1\$d characters)", 20, locale = fr),
        )
        assertEquals("Impossible de le copier : refus", OnyxLocalization.format("Couldn't copy it: %1\$s", "refus", locale = fr))
    }

    @Test
    fun `les postes du contexte que le serveur nomme en anglais sont traduits`() {
        // Ces mots viennent du serveur (`session.context_breakdown.label`), pas du code : ils
        // sont traduits pour que la fenêtre se lise en français.
        assertEquals("Invite système", OnyxLocalization.translate("System prompt", fr))
        assertEquals("Définitions des outils", OnyxLocalization.translate("Tool definitions", fr))
        assertEquals("Définitions des sous-agents", OnyxLocalization.translate("Subagent definitions", fr))
        assertEquals("Mémoire", OnyxLocalization.translate("Memory", fr))
        // Un poste que le dictionnaire ne connaît pas garde son nom : jamais une clé nue effacée.
        assertEquals("Nouveau poste", OnyxLocalization.translate("Nouveau poste", fr))
    }

    @Test
    fun `la feuille n a pas herite du vocabulaire de l autre plateforme`() {
        val forbidden = listOf("ap" + "ple", "i" + "os", "i" + "phone", "i" + "pad", "macos", "s" + "iri")
        (labels.values + emptyStates.map { OnyxLocalization.translate(it, fr) }).forEach { text ->
            forbidden.forEach { word ->
                assertFalse("« $word » dans : $text", text.lowercase().contains(word))
            }
        }
        // Et la confirmation ne dit pas « utilisateurs », elle parle de la personne.
        assertNotEquals("", OnyxLocalization.translate("Start fresh?", fr))
    }
}
