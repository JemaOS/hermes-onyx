package com.onyx.app.ui.design

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La coupure d'un libellé : **jamais au milieu d'un mot**, jamais sans ellipse.
 *
 * Le défaut relevé à police 1,3× sur un téléphone de 411 dp : la feuille de compte rognait le
 * dernier mot de deux rangées — « Réessayer la connexion **maint…** » (« maintenant ») et
 * « Changer ou ajouter une **passer…** » (« passerelle ») —, et son motif d'état finissait sur
 * « … du Dashboard **dan…** » (« dans »). L'ellipse du moteur coupe au dernier caractère qui tient ;
 * [fitWords] retire des **mots entiers** avant de la poser.
 *
 * La mesure est simulée par une règle simple (une largeur par caractère) : ce qui se teste ici,
 * c'est la **décision** — quelles lignes, quel mot de fin —, pas la police.
 */
class SheetLabelFitTest {

    /** Un caractère = 10 unités : la largeur d'un texte est dix fois sa longueur. */
    private val measure: (String) -> Float = { it.length * 10f }

    @Test
    fun `un libelle qui tient n est pas touche — aucune ellipse n est posee`() {
        assertEquals("Réglages", fitWords("Réglages", measure, 200f, maxLines = 1))
        // Deux mots qui tiennent ensemble restent intacts (pas d'ellipse inutile).
        assertEquals("Changer ou ajouter", fitWords("Changer ou ajouter", measure, 200f, maxLines = 1))
    }

    @Test
    fun `un libelle trop long est coupe a une frontiere de mot, avec l ellipse de la maison`() {
        val long = "Réessayer la connexion maintenant"
        // 220 px (22 caractères) : « Réessayer la » fait 13 caractères (130) + « … » ; on garde des
        // mots entiers.
        val out = fitWords(long, measure, 220f, maxLines = 1)
        assertTrue("une ellipse est posée", out.endsWith(ONYX_ELLIPSIS))
        assertTrue("l'ellipse suit un mot entier, jamais un morceau", out.endsWith(" $ONYX_ELLIPSIS"))
        val kept = out.removeSuffix(" $ONYX_ELLIPSIS")
        assertTrue("le texte gardé tient dans la largeur", measure(kept) <= 220f)
        // Chaque mot gardé est un mot **entier** du libellé d'origine, dans l'ordre.
        val words = kept.split(" ")
        assertEquals(listOf("Réessayer", "la").take(words.size), words)
        assertFalse("aucun mot n'est rogné", kept.endsWith("maint"))
    }

    @Test
    fun `le dernier mot d une rangee longue ne se coupe jamais au milieu`() {
        val out = fitWords("Changer ou ajouter une passerelle", measure, 240f, maxLines = 1)
        val kept = out.removeSuffix(" $ONYX_ELLIPSIS")
        // « passer » (un morceau de « passerelle ») ne doit jamais apparaître.
        assertFalse("« passerelle » n'est pas rogné en « passer »", kept.contains("passer"))
        assertTrue(out.endsWith(" $ONYX_ELLIPSIS"))
    }

    @Test
    fun `sur plusieurs lignes, seule la derniere porte l ellipse et elle finit sur un mot entier`() {
        // Trois lignes de 20 caractères : « Le Dashboard Hermes » / « 0.22.3 est » / « accessible, »…
        // en portant quatre lignes, on coupe la troisième à un mot entier.
        val motif = "Le Dashboard Hermes est accessible mais la connexion a echoue dans les reglages"
        val out = fitWords(motif, measure, 200f, maxLines = 3)
        val lines = out.split("\n")
        assertEquals("on garde le nombre de lignes accordé", 3, lines.size)
        assertTrue("seule la dernière ligne porte l'ellipse", lines.last().endsWith(ONYX_ELLIPSIS))
        assertTrue("et elle finit sur un mot entier", lines.last().endsWith(" $ONYX_ELLIPSIS"))
        lines.dropLast(1).forEach { assertFalse("les lignes pleines n'ont pas d'ellipse", it.contains(ONYX_ELLIPSIS)) }
    }

    @Test
    fun `un mot plus large que la place reste entier — on ne le casse jamais`() {
        val out = fitWords("Anticonstitutionnellement", measure, 60f, maxLines = 1)
        assertEquals("le mot est rendu tel quel", "Anticonstitutionnellement", out)
    }

    @Test
    fun `le texte vide ou sans place ne bouge pas`() {
        assertEquals("", fitWords("", measure, 100f, maxLines = 1))
        assertEquals("x", fitWords("x", measure, 0f, maxLines = 1))
    }

    @Test
    fun `la ligne de la famille coupe par mots, pas au caractere`() {
        val sheet = File("src/main/java/com/onyx/app/ui/design/AttachmentSheet.kt").readText()
        assertTrue(
            "la ligne de la feuille passe par le coupeur par mots",
            sheet.contains("fitWords("),
        )
        assertTrue("et garde l'ellipse de la maison pour le dernier recours", sheet.contains("TextOverflow.Ellipsis"))
    }
}
