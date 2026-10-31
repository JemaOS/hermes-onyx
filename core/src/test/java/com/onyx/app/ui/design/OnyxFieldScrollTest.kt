package com.onyx.app.ui.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le champ d'une rangée, au banc : une valeur plus longue que le champ.
 *
 * Le propriétaire déplaçait le curseur de gauche à droite sans jamais voir toutes les lettres — le
 * repère restait posé au milieu d'un texte tronqué. Ces tests mesurent la position du texte pendant
 * ce déplacement : le curseur doit rester dans la fenêtre visible à chaque pas, et la valeur entière
 * doit pouvoir être lue, du premier caractère au dernier.
 */
class OnyxFieldScrollTest {

    /** Une colonne étroite de formulaire : le libellé prend la moitié, la valeur le reste. */
    private val viewport = 260

    /** Le champ secret du pont : `https://give-overnight-reveal-na….trycloudflare.com`. */
    private val longValue = "https://give-overnight-reveal-na7x.trycloudflare.com"

    /** La largeur du texte, comme la mise en page la mesure : chaque caractère avance de [advance]. */
    private fun width(advance: Int) = longValue.length * advance

    /** L'abscisse du curseur posé devant le [index]-ième caractère, bornée à la fin du texte. */
    private fun caretX(index: Int, advance: Int) = minOf(index * advance, width(advance))

    private fun assertVisible(origin: Int, caret: Int, step: Int) {
        assertTrue("curseur $step coupé à gauche : $origin + $caret", origin + caret >= 0)
        assertTrue("curseur $step coupé à droite : $origin + $caret", origin + caret <= viewport)
    }

    @Test
    fun `au repos la valeur alignee a droite montre sa fin`() {
        val content = width(advance = 9)
        val rest = OnyxFieldScroll.rest(viewport = viewport, content = content, alignEnd = true)
        assertTrue("le début est coupé à gauche", rest < 0)
        assertEquals("la fin tombe sur le bord droit", viewport, rest + content)
    }

    @Test
    fun `au repos une valeur alignee a gauche montre son debut`() {
        assertEquals(0, OnyxFieldScroll.rest(viewport = viewport, content = width(9), alignEnd = false))
    }

    @Test
    fun `en deplacant le curseur du debut a la fin la valeur entiere est lisible`() {
        val advance = 9
        val content = width(advance)
        val margin = 8
        var origin = OnyxFieldScroll.rest(viewport, content, alignEnd = true)

        for (step in 0..longValue.length) {
            val caret = caretX(step, advance)
            origin = OnyxFieldScroll.origin(
                viewport = viewport,
                content = content,
                caretLeft = caret,
                caretRight = caret,
                previous = origin,
                alignEnd = true,
                margin = margin,
            )
            assertVisible(origin, caret, step)
        }
        // Arrivé au dernier caractère, c'est bien la queue de la valeur qu'on lit.
        assertEquals(viewport - content, origin)
        assertEquals(viewport, origin + content)
    }

    @Test
    fun `le curseur recule aussi loin qu il avance`() {
        val advance = 9
        val content = width(advance)
        var origin = viewport - content // on part de la fin, comme au repos

        for (step in longValue.length downTo 0) {
            val caret = caretX(step, advance)
            origin = OnyxFieldScroll.origin(viewport, content, caret, caret, origin, alignEnd = true)
            assertVisible(origin, caret, step)
        }
        assertEquals("revenu au premier caractère, sa tête est visible", 0, origin)
    }

    @Test
    fun `un collage en fin de texte ramene la queue dans la fenetre`() {
        val advance = 9
        val content = width(advance)
        // Le curseur était au début du champ, on colle : il saute à la fin d'un coup.
        val origin = OnyxFieldScroll.origin(
            viewport = viewport,
            content = content,
            caretLeft = content,
            caretRight = content,
            previous = 0,
            alignEnd = true,
        )
        assertEquals(viewport - content, origin)
        assertVisible(origin, content, longValue.length)
    }

    @Test
    fun `une police agrandie ne perd pas le curseur`() {
        // 1,3× : chaque caractère avance plus large, la valeur déborde davantage du champ.
        val advance = 12
        val content = width(advance)
        var origin = OnyxFieldScroll.rest(viewport, content, alignEnd = true)

        for (step in 0..longValue.length) {
            val caret = caretX(step, advance)
            origin = OnyxFieldScroll.origin(viewport, content, caret, caret, origin, alignEnd = true, margin = 10)
            assertVisible(origin, caret, step)
        }
        assertEquals(viewport - content, origin)
    }

    @Test
    fun `une valeur qui tient dans le champ ne bouge jamais`() {
        val content = 120
        val origin = OnyxFieldScroll.origin(
            viewport = viewport,
            content = content,
            caretLeft = 60,
            caretRight = 62,
            previous = OnyxFieldScroll.rest(viewport, content, alignEnd = true),
            alignEnd = true,
        )
        assertEquals(viewport - content, origin)
    }

    @Test
    fun `une selection multiple garde sa fin visible`() {
        // Le curseur actif d'une sélection est à sa fin : c'est lui qui doit rester dans la fenêtre.
        val advance = 9
        val content = width(advance)
        val selectionEnd = caretX(40, advance)
        val origin = OnyxFieldScroll.origin(
            viewport = viewport,
            content = content,
            caretLeft = selectionEnd,
            caretRight = selectionEnd,
            previous = viewport - content,
            alignEnd = true,
        )
        assertVisible(origin, selectionEnd, 40)
    }

    @Test
    fun `un champ aligne a gauche ne recule pas avant son premier caractere`() {
        val advance = 9
        val content = width(advance)
        var origin = OnyxFieldScroll.rest(viewport, content, alignEnd = false)

        for (step in 0..longValue.length) {
            val caret = caretX(step, advance)
            origin = OnyxFieldScroll.origin(viewport, content, caret, caret, origin, alignEnd = false)
            assertVisible(origin, caret, step)
            assertTrue("l'origine reste négative ou nulle", origin <= 0)
        }
    }
}
