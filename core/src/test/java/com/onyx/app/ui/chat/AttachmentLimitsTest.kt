package com.onyx.app.ui.chat

import com.onyx.app.localization.OnyxLanguage
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les limites d'une pièce jointe, **telles qu'Onyx les applique**.
 *
 * Ce qui est éprouvé ici : les quatre chiffres du code (10 pièces jointes, 25 Mo au total, 25 Mo
 * par fichier, 128 Ko pour un fichier texte), le sous-titre français qui les affiche, et le verdict
 * d'un ajout — un fichier au-dessus du plafond est refusé **avec son poids et la limite**, en
 * français, au lieu de partir entier vers le serveur.
 */
class AttachmentLimitsTest {

    @After
    fun remettreLaLangue() {
        OnyxLanguage.apply(OnyxLanguage.SYSTEM)
    }

    private fun enFrancais(block: () -> Unit) {
        OnyxLanguage.apply(OnyxLanguage.FRENCH)
        block()
    }

    private val unMo = 1024L * 1024L

    // ---- 1. les quatre chiffres, et le sous-titre qui les montre --------------------------------

    @Test
    fun `les limites sont celles du code, pas celles de la reference`() {
        assertEquals("pièces jointes au plus", 10, AttachmentLimits.MAX_COUNT)
        assertEquals("poids total", 25L * 1000L * 1000L, AttachmentLimits.MAX_TOTAL_BYTES)
        assertEquals("poids d'un fichier", 25L * 1000L * 1000L, AttachmentLimits.MAX_FILE_BYTES)
        assertEquals("poids d'un fichier texte", 128L * 1000L, AttachmentLimits.MAX_TEXT_BYTES)
    }

    @Test
    fun `le sous titre de la feuille affiche les vrais chiffres, en francais`() {
        enFrancais {
            val sousTitre = AttachmentLimits.subtitle()
            assertEquals(
                "Jusqu’à 10 pièces jointes · 25 Mo au total · 25 Mo par fichier · " +
                    "128 Ko pour un fichier texte",
                sousTitre,
            )
            // Les quatre nombres du code sont là, aucun n'est recopié à la main.
            listOf("10", "25 Mo", "128 Ko").forEach { chiffre ->
                assertTrue("« $chiffre » manque au sous-titre", sousTitre.contains(chiffre))
            }
        }
    }

    @Test
    fun `le poids d une piece jointe suit la langue de l application`() {
        OnyxLanguage.apply(OnyxLanguage.FRENCH)
        assertEquals("1,5 Mo", AttachmentLimits.sizeLabel(1_480_000))
        OnyxLanguage.apply(OnyxLanguage.ENGLISH)
        assertEquals("1.5 Mo", AttachmentLimits.sizeLabel(1_480_000))
        OnyxLanguage.apply(OnyxLanguage.FRENCH)
        assertEquals("312 o", AttachmentLimits.sizeLabel(312))
    }

    // ---- 2. ce qui passe, et ce qui ne passe pas ------------------------------------------------

    @Test
    fun `un fichier plus gros que le plafond est refuse avec son poids et la limite`() {
        enFrancais {
            val refus = AttachmentLimits.refusal(
                name = "cours.pdf",
                bytes = 32 * unMo,
                isText = false,
                attached = 0,
                attachedBytes = 0L,
            )
            assertEquals(
                "« cours.pdf » fait 34 Mo, au-delà de la limite de 25 Mo : il n’a pas été joint.",
                refus,
            )
        }
    }

    @Test
    fun `un fichier texte au dela de 128 Ko est refuse, avec le chiffre`() {
        enFrancais {
            val refus = AttachmentLimits.refusal(
                name = "notes.txt",
                bytes = 300L * 1024L,
                isText = true,
                attached = 0,
                attachedBytes = 0L,
            )
            assertTrue("le nom", refus!!.contains("notes.txt"))
            assertTrue("le poids mesuré", refus.contains("307 Ko"))
            assertTrue("la limite", refus.contains("128 Ko"))
        }
    }

    @Test
    fun `le onzieme fichier est refuse, et le message le dit`() {
        enFrancais {
            val refus = AttachmentLimits.refusal(
                name = "onzieme.txt",
                bytes = 100L,
                isText = true,
                attached = AttachmentLimits.MAX_COUNT,
                attachedBytes = 0L,
            )
            assertEquals("Vous pouvez joindre jusqu’à 10 fichiers.", refus)
        }
    }

    @Test
    fun `un ajout qui ferait depasser le total est refuse au total`() {
        enFrancais {
            val refus = AttachmentLimits.refusal(
                name = "film.mov",
                bytes = 10 * unMo,
                isText = false,
                attached = 2,
                attachedBytes = 20 * unMo,
            )
            assertTrue("le poids du fichier", refus!!.contains("10 Mo"))
            assertTrue("la limite du message", refus.contains("25 Mo"))
            assertTrue("le refus parle du message entier", refus.contains("au total"))
        }
    }

    @Test
    fun `juste sous les plafonds, on passe`() {
        assertNull(
            "un fichier à la limite",
            AttachmentLimits.refusal(
                "pile.bin",
                AttachmentLimits.MAX_FILE_BYTES,
                isText = false,
                attached = 0,
                attachedBytes = 0L,
            ),
        )
        assertNull(
            "le dixième fichier",
            AttachmentLimits.refusal("dix.bin", 100L, isText = false, attached = 9, attachedBytes = 0L),
        )
        assertNull(
            "un texte à la limite",
            AttachmentLimits.refusal(
                "pile.txt",
                AttachmentLimits.MAX_TEXT_BYTES,
                isText = true,
                attached = 0,
                attachedBytes = 0L,
            ),
        )
    }

    @Test
    fun `une taille inconnue ne fait pas refuser sur un poids suppose`() {
        // Le fournisseur ne donne pas toujours `OpenableColumns.SIZE` : on ne refuse alors que sur
        // le nombre, jamais sur un poids inventé.
        assertNull(
            AttachmentLimits.refusal("inconnu.bin", null, isText = false, attached = 3, attachedBytes = 24 * unMo),
        )
    }

    @Test
    fun `le refus est en francais et jamais en anglais`() {
        enFrancais {
            val refus = AttachmentLimits.refusal("gros.bin", 30 * unMo, isText = false, attached = 0, attachedBytes = 0L)
            assertFalse("encore de l'anglais : $refus", refus!!.contains("over the"))
            assertFalse(refus.contains("was not attached"))
        }
    }

    @Test
    fun `en anglais, les memes refus passent par l anglais`() {
        OnyxLanguage.apply(OnyxLanguage.ENGLISH)
        val refus = AttachmentLimits.refusal("big.bin", 30 * unMo, isText = false, attached = 0, attachedBytes = 0L)
        assertEquals(
            "big.bin is 31 Mo, over the 25 Mo limit: it was not attached.",
            refus,
        )
        assertTrue(
            "le sous-titre anglais",
            AttachmentLimits.subtitle().contains("Up to 10 attachments"),
        )
    }

    @Test
    fun `les poids se lisent a la francaise`() {
        assertEquals("25 Mo", AttachmentLimits.sizeLabel(AttachmentLimits.MAX_TOTAL_BYTES))
        assertEquals("128 Ko", AttachmentLimits.sizeLabel(AttachmentLimits.MAX_TEXT_BYTES))
        assertEquals("2,5 Mo", AttachmentLimits.sizeLabel(2_500_000))
        assertEquals("12 Mo", AttachmentLimits.sizeLabel(12_000_000))
        assertEquals(Locale.FRENCH.language, OnyxLanguage.preferred.language)
    }
}
