package com.onyx.app.localization

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le **français** des gestes d'une conversation : la feuille du bas, ses lignes, le bandeau
 * d'annulation, et la confirmation de suppression.
 *
 * Les libellés de la feuille naissent d'une fonction pure (`conversationSheetRows`) et non d'un
 * `MenuItem` : un balayage qui ne regarde que les `MenuItem` les manquerait, et une feuille
 * française resterait en anglais sans que rien ne rougisse. C'est ce trou que ce fichier ferme.
 */
class ConversationGesturesLocalizationTest {

    private val fr = Locale.FRANCE

    /** Les cinq actions de la feuille, dans leur ordre. */
    private val actions = linkedMapOf(
        "Rename" to "Renommer",
        "Create thread" to "Créer un fil",
        "Pin" to "Épingler",
        "Unpin" to "Désépingler",
        "Archive" to "Archiver",
        "Unarchive" to "Désarchiver",
        "Delete" to "Supprimer",
    )

    /** Les compléments que la rangée offrait déjà : ils survivent dans la feuille, en français. */
    private val complements = linkedMapOf(
        "Mark as Read" to "Marquer comme lu",
        "Mark as Unread" to "Marquer comme non lu",
        "Usage & cost" to "Consommation et coût",
        "Copy title" to "Copier le titre",
        "Open in New Window" to "Ouvrir dans une nouvelle fenêtre",
    )

    /** Le bandeau, la confirmation de suppression, et le renommage. */
    private val barAndDialogs = linkedMapOf(
        "Undo" to "Annuler",
        "Pinned" to "Épinglés",
        "Unpinned" to "Détachée",
        "Archived" to "Archivé",
        "Unarchived" to "Désarchivée",
        "Deleted" to "Supprimée",
        "Delete this conversation?" to "Supprimer cette conversation ?",
        "It leaves this phone and your server. This can't be undone."
            to "Elle quitte ce téléphone et le serveur. C’est définitif.",
        "Rename conversation" to "Renommer la conversation",
        "Name" to "Nom",
        "Save" to "Enregistrer",
    )

    /** Les lignes du silence, telles que la feuille les accueille. */
    private val mutes = linkedMapOf(
        "Unmute" to "Réactiver",
        "For 1 Hour" to "Pendant 1 heure",
        "For 8 Hours" to "Pendant 8 heures",
        "Until Tomorrow" to "Jusqu’à demain",
        "Until I Turn It Back On" to "Jusqu’à réactivation",
        "Muted" to "Silencieux",
        "Unmuted" to "Son rétabli",
    )

    private fun assertTranslated(entries: Map<String, String>) {
        entries.forEach { (english, french) ->
            assertEquals("français : $english", french, OnyxLocalization.translate(english, fr))
            // En anglais, rien ne bouge.
            assertEquals("anglais : $english", english, OnyxLocalization.translate(english, Locale.ENGLISH))
        }
    }

    @Test
    fun `les cinq actions de la feuille sont dites en francais`() = assertTranslated(actions)

    @Test
    fun `les complements du menu flottant sont dits en francais`() = assertTranslated(complements)

    @Test
    fun `le bandeau et les dialogues sont dits en francais`() = assertTranslated(barAndDialogs)

    @Test
    fun `les lignes du silence sont dites en francais`() = assertTranslated(mutes)

    @Test
    fun `le sous-titre « Active » se met en forme`() {
        assertEquals("Actif 7h", OnyxLocalization.format("Active %1\$s", "7h", locale = fr))
        // En anglais, le mot reste celui du modèle.
        assertEquals("Active 7h", OnyxLocalization.format("Active %1\$s", "7h", locale = Locale.ENGLISH))
    }

    @Test
    fun `le sous-titre garde son argument de format`() {
        val rendered = OnyxLocalization.format("Active %1\$s", "3 days", locale = fr)
        assertTrue("l'argument est bien placé : $rendered", rendered.contains("3 days"))
        assertTrue("le mot d'activité est traduit : $rendered", rendered.startsWith("Actif"))
    }

    @Test
    fun `aucun libelle de la feuille ne reste en anglais`() {
        val untranslated = (actions.keys + complements.keys + barAndDialogs.keys + mutes.keys)
            .filter { OnyxLocalization.translate(it, fr) == it }
        assertTrue("libellés encore en anglais : $untranslated", untranslated.isEmpty())
    }

    @Test
    fun `epingler et detacher ne disent pas la meme chose`() {
        assertNotEquals(
            OnyxLocalization.translate("Pin", fr),
            OnyxLocalization.translate("Unpin", fr),
        )
    }

    @Test
    fun `archiver et desarchiver ne disent pas la meme chose`() {
        assertNotEquals(
            OnyxLocalization.translate("Archive", fr),
            OnyxLocalization.translate("Unarchive", fr),
        )
    }

    @Test
    fun `supprimer et supprimee ne disent pas la meme chose`() {
        // L'action (« Supprimer ») et l'état du bandeau (« Supprimée ») ne se confondent pas.
        assertNotEquals(
            OnyxLocalization.translate("Delete", fr),
            OnyxLocalization.translate("Deleted", fr),
        )
    }

    @Test
    fun `la phrase de suppression parle de la personne, pas d utilisateurs`() {
        val phrase = OnyxLocalization.translate("It leaves this phone and your server. This can't be undone.", fr)
        assertTrue("elle dit que ça quitte le téléphone", phrase.contains("téléphone"))
        assertTrue("et que ça quitte le serveur", phrase.contains("serveur"))
        assertTrue("et que c'est définitif", phrase.contains("définitif"))
    }

    @Test
    fun `le sous-titre n a jamais d horodatage brut en francais`() {
        val rendered = OnyxLocalization.format("Active %1\$s", "2h", locale = fr)
        assertFalse(rendered.contains("25920000"))
        assertFalse(rendered.contains("millisecondes"))
    }

    @Test
    fun `aucun libelle de la feuille ne nomme l autre plateforme`() {
        val forbidden = listOf("ap" + "ple", "i" + "os", "i" + "phone", "i" + "pad", "macos", "s" + "iri")
        (actions.values + complements.values + barAndDialogs.values + mutes.values).forEach { text ->
            forbidden.forEach { word ->
                assertFalse("« $word » dans : $text", text.lowercase().contains(word))
            }
        }
    }

    @Test
    fun `les libelles anglais de la feuille sont ceux de la reference`() {
        actions.keys.forEach { assertEquals(it, OnyxLocalization.translate(it, Locale.ENGLISH)) }
    }

    @Test
    fun `« Creer un fil » ne dit pas « fil de discussion »`() {
        // C'est un fil, pas une « discussion » : le mot est celui de la maison.
        assertEquals("Créer un fil", OnyxLocalization.translate("Create thread", fr))
    }

    @Test
    fun `les quatre durees de silence sont toutes la`() {
        listOf("For 1 Hour", "For 8 Hours", "Until Tomorrow", "Until I Turn It Back On").forEach { span ->
            assertNotEquals("durée sans traduction : $span", span, OnyxLocalization.translate(span, fr))
        }
    }
}
