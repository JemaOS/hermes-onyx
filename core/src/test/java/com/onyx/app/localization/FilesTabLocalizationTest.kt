package com.onyx.app.localization

import com.onyx.app.data.model.FileFamily
import com.onyx.app.data.model.FileSort
import com.onyx.app.data.model.fileFamilyLabel
import com.onyx.app.data.model.fileSortLabel
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * L'onglet **Fichiers** parle français par le dictionnaire central : les six filtres avec leur
 * compteur, les cinq ordres de tri, l'état de chaque fichier sur le téléphone, et les états vides.
 *
 * Les libellés sont **assemblés** (un mot traduit, puis le compte) : c'est pourquoi ils sortent du
 * dictionnaire déjà formés, et que l'écran ne les repasse pas une seconde fois.
 */
class FilesTabLocalizationTest {

    private val fr = Locale.FRANCE

    @Test
    fun `les six filtres sont en francais, compteur compris`() {
        assertEquals("Tout 8", fileFamilyLabel(FileFamily.ALL, 8, fr))
        assertEquals("Images 2", fileFamilyLabel(FileFamily.IMAGE, 2, fr))
        assertEquals("Vidéos 1", fileFamilyLabel(FileFamily.VIDEO, 1, fr))
        assertEquals("Audio 1", fileFamilyLabel(FileFamily.AUDIO, 1, fr))
        assertEquals("Documents 3", fileFamilyLabel(FileFamily.DOCUMENT, 3, fr))
        assertEquals("Archives 1", fileFamilyLabel(FileFamily.ARCHIVE, 1, fr))
    }

    @Test
    fun `les cinq tris sont en francais`() {
        assertEquals("Récents d’abord", fileSortLabel(FileSort.RECENT, fr))
        assertEquals("Anciens d’abord", fileSortLabel(FileSort.OLDEST, fr))
        assertEquals("Nom", fileSortLabel(FileSort.NAME, fr))
        assertEquals("Taille", fileSortLabel(FileSort.SIZE, fr))
        assertEquals("Type", fileSortLabel(FileSort.TYPE, fr))
        // Aucun libellé ne reste en anglais.
        FileSort.entries.forEach { order ->
            assertNotEquals("tri non traduit : ${order.key}", order.key, fileSortLabel(order, fr))
        }
    }

    @Test
    fun `l etat d un fichier et les gestes du menu sont en francais`() {
        val keys = linkedMapOf(
            "To download" to "À télécharger",
            "Downloading…" to "Téléchargement…",
            "Available" to "Disponible",
            "Save to the phone" to "Enregistrer sur le téléphone",
            "Saved to the phone" to "Enregistré sur le téléphone",
            "Copy path" to "Copier le chemin",
            "Path copied" to "Chemin copié",
            "Open with" to "Ouvrir avec",
            "Sort" to "Tri",
            "This sound can't be played." to "Ce son ne peut pas être lu.",
            "This archive can't be read here." to "Cette archive ne peut pas être lue ici.",
            "This archive is empty." to "Cette archive est vide.",
        )
        keys.forEach { (english, french) ->
            assertEquals(french, OnyxLocalization.translate(english, fr))
            assertEquals(english, OnyxLocalization.translate(english, Locale.ENGLISH))
        }
        // Les deux libellés qui existaient déjà gardent leur traduction.
        assertEquals("Ouvrir", OnyxLocalization.translate("Open", fr))
        assertEquals("Réessayer", OnyxLocalization.translate("Retry", fr))
    }

    @Test
    fun `les etats vides nomment ce qui manque, filtre par filtre`() {
        assertEquals(
            "Aucune image dans cette conversation.",
            OnyxLocalization.translate("No picture in this conversation.", fr),
        )
        assertEquals(
            "Aucune vidéo dans cette conversation.",
            OnyxLocalization.translate("No video in this conversation.", fr),
        )
        assertEquals(
            "Aucun son dans cette conversation.",
            OnyxLocalization.translate("No sound in this conversation.", fr),
        )
        assertEquals(
            "Aucun document dans cette conversation.",
            OnyxLocalization.translate("No document in this conversation.", fr),
        )
        assertEquals(
            "Aucune archive dans cette conversation.",
            OnyxLocalization.translate("No archive in this conversation.", fr),
        )
    }

    @Test
    fun `chaque libelle traduit garde son repli anglais`() {
        val pairs = listOf(
            "Videos" to "Vidéos",
            "Size" to "Taille",
            "Recent first" to "Récents d’abord",
            "Oldest first" to "Anciens d’abord",
            "Available" to "Disponible",
            "To download" to "À télécharger",
            "Open with" to "Ouvrir avec",
        )
        pairs.forEach { (english, french) ->
            assertEquals(french, OnyxLocalization.translate(english, fr))
            assertEquals(english, OnyxLocalization.translate(english, Locale.ENGLISH))
        }
    }
}
