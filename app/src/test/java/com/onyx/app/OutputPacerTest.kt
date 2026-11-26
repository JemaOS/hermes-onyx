package com.onyx.app

import com.onyx.app.ui.terminal.OutputPacer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Un terminal bavard ne repeint plus l'écran à chaque fragment.**
 *
 * `onOutput` faisait `revision++` **par fragment** ; le rendu (`TerminalScreen`) reconstruit alors
 * les quarante `AnnotatedString` de l'écran, style par cellule. Une compilation, un `npm install`,
 * un `tail -f` envoient des centaines de fragments par seconde : autant de repeints complets, et des
 * figements qui n'ont rien à voir avec le réseau — d'autant plus sensibles avec trente terminaux
 * ouverts.
 *
 * Le tampon, lui, reste écrit immédiatement (grille bornée, 40×120) : seule la **publication** est
 * rythmée. Rien ne peut donc être perdu — l'écran montre l'état complet une fois par trame.
 *
 * Aucune horloge n'est lue ici : les tests passent `now` à la main, donc ils ne dépendent ni de la
 * machine ni d'une attente réelle.
 */
class OutputPacerTest {

    @Test
    fun `la premiere sortie part tout de suite`() {
        val pacer = OutputPacer(frameMillis = 16)
        assertTrue("rien à faire attendre sur la première trame", pacer.shouldPublishNow(1_000L))
    }

    @Test
    fun `mille fragments en une seconde ne font pas mille publications`() {
        val pacer = OutputPacer(frameMillis = 16)
        var published = 0
        // Un fragment par milliseconde pendant une seconde : le pire cas d'une compilation.
        for (millis in 0 until 1_000) {
            if (pacer.shouldPublishNow(millis.toLong())) published++
        }

        assertTrue("au plus une publication par trame (mesuré : $published)", published <= 63)
        assertTrue("et l'écran suit quand même ($published publications)", published >= 60)
    }

    @Test
    fun `deux fragments dans la meme trame donnent une seule publication`() {
        val pacer = OutputPacer(frameMillis = 16)
        assertTrue(pacer.shouldPublishNow(0L))
        assertFalse("le second est couvert par le rattrapage", pacer.shouldPublishNow(1L))
        assertFalse(pacer.shouldPublishNow(15L))
        assertTrue("la trame suivante publie", pacer.shouldPublishNow(16L))
    }

    @Test
    fun `le rattrapage dit combien de temps attendre, et il est court`() {
        val pacer = OutputPacer(frameMillis = 16)
        pacer.shouldPublishNow(100L)
        assertEquals("il reste la fin de la trame", 16L, pacer.trailingDelayMillis(100L))
        assertEquals("à mi-trame", 8L, pacer.trailingDelayMillis(108L))
        // Jamais zéro : un `delay(0)` en boucle ferait tourner le rattrapage à vide.
        assertEquals("plancher d'une milliseconde", 1L, pacer.trailingDelayMillis(120L))
    }

    @Test
    fun `une nouvelle session ne reprend pas la fenetre de la precedente`() {
        val pacer = OutputPacer(frameMillis = 16)
        pacer.shouldPublishNow(0L)
        assertFalse("dans la même fenêtre", pacer.shouldPublishNow(5L))
        pacer.reset()
        assertTrue("après reconnexion, la première sortie doit paraître", pacer.shouldPublishNow(6L))
    }

    @Test
    fun `une sortie rare n attend jamais`() {
        val pacer = OutputPacer(frameMillis = 16)
        // Un shell qui répond une fois toutes les deux secondes : chaque fragment publie.
        var published = 0
        for (step in 0 until 5) {
            if (pacer.shouldPublishNow(step * 2_000L)) published++
        }
        assertEquals("aucun délai ajouté sur une sortie lente", 5, published)
    }
}