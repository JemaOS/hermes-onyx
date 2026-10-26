package com.onyx.app

import com.onyx.app.localization.OnyxLocalization
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Les mots de la barre de sélection multiple, en français.
 *
 * Le panneau parle français jusqu'à la confirmation: le titre de la suppression groupée était
 * resté **en anglais** (« Delete 4 conversations? ») sous un message français, parce que le motif
 * de traduction attendait la phrase sans le « ? » que l'écran ajoute. Aucun mot du parcours ne doit
 * rester anglais sur un téléphone français.
 */
class SelectionBarFrenchTest {
    private fun fr(key: String) = OnyxLocalization.translate(key, Locale.FRANCE)

    @Test
    fun `le compte et les actions de la barre sont en francais`() {
        assertEquals("4 éléments sélectionnés", fr("4 selected"))
        assertEquals("1 élément sélectionné", fr("1 selected"))
        assertEquals("Tout sélectionner", fr("Select all"))
        assertEquals("Tout désélectionner", fr("Deselect all"))
        assertEquals("Annuler", fr("Cancel"))
        assertEquals("Archiver 4", fr("Archive 4"))
        assertEquals("Supprimer 4", fr("Delete 4"))
    }

    @Test
    fun `le titre de la confirmation de suppression est en francais`() {
        assertEquals("Supprimer 4 conversations ?", fr("Delete 4 conversations?"))
        assertEquals("Supprimer 1 conversation ?", fr("Delete 1 conversation?"))
        // Le motif d'origine reste servi: les deux écritures se traduisent pareil.
        assertEquals("Supprimer 4 conversations ?", fr("Delete 4 conversations"))
    }

    @Test
    fun `aucun mot du parcours ne reste en anglais`() {
        val parcours = listOf(
            "4 selected", "1 selected", "Select all", "Deselect all", "Cancel",
            "Archive 4", "Delete 4", "Delete 4 conversations?", "Delete 1 conversation?",
        )
        for (key in parcours) {
            val mot = fr(key)
            assertFalse("« $key » reste en anglais : $mot", mot == key)
        }
    }
}
