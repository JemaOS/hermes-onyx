package com.onyx.app.ui.design

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **La soudure de deux formes : la géométrie, sans appareil.**
 *
 * Le pont de matière est la troisième pièce du verre liquide, et la seule qu'aucune bibliothèque ne
 * donnait. Sa géométrie est donc écrite comme des **fonctions pures** — [glassWeldProgress] et
 * [glassWeldShape] — pour être éprouvée sans processeur d'ombrage, sans émulateur et sans capture :
 * ce qu'un rendu ne montrerait qu'à l'œil, un test le chiffre.
 *
 * Les tracés ([glassWeldPath], [glassWeldRimPath]) ne sont **pas** éprouvés ici, et c'est
 * volontaire : ils construisent un `Path`, que la machine virtuelle de test ne sait pas instancier
 * hors appareil. Tout ce qui décide de la forme est donc dans [glassWeldShape], et c'est là que les
 * assertions mordent — un tracé juste avec des chiffres faux resterait un tracé faux.
 *
 * Ce que le test tient :
 *  1. **le profil de soudure** — nulle quand les formes sont loin, pleine quand elles se touchent, et
 *     **lissée** au milieu (le point milieu vaut exactement 0,5) ;
 *  2. **la forme du pont** — absente quand il n'y a rien à souder (formes disjointes, jointives,
 *     soudure nulle), présente sinon, et d'autant plus **large au col** que la soudure est avancée ;
 *  3. **l'emprise** — le pont ne déborde jamais des deux formes, et il entre d'un `inset` dans
 *     chacune, jamais plus.
 */
class GlassWeldTest {

    private val upper = Rect(left = 20f, top = 100f, right = 300f, bottom = 200f)
    private val lower = Rect(left = 40f, top = 240f, right = 280f, bottom = 300f)

    // — 1. Le profil de soudure —

    @Test
    fun `la soudure est nulle quand les formes sont loin`() {
        assertEquals(0f, glassWeldProgress(gapPx = 100f, weldPx = 40f), 0.0001f)
        assertEquals(0f, glassWeldProgress(gapPx = 40f, weldPx = 40f), 0.0001f)
    }

    @Test
    fun `la soudure est pleine quand les formes se touchent ou se recouvrent`() {
        assertEquals(1f, glassWeldProgress(gapPx = 0f, weldPx = 40f), 0.0001f)
        assertEquals(1f, glassWeldProgress(gapPx = -12f, weldPx = 40f), 0.0001f)
    }

    @Test
    fun `la soudure est lissee au milieu du rapprochement`() {
        // Au milieu géométrique, un lissage cubique vaut exactement un demi — et c'est ce qui
        // distingue une langue de matière d'un rectangle qu'on étire.
        assertEquals(0.5f, glassWeldProgress(gapPx = 20f, weldPx = 40f), 0.0001f)
        // Et la croissance est monotone : plus l'écart se réduit, plus on soude.
        val samples = (0..8).map { glassWeldProgress(gapPx = it * 5f, weldPx = 40f) }
        assertTrue(
            "le profil croît quand l'écart se réduit ($samples)",
            samples.zipWithNext().all { it.first >= it.second },
        )
    }

    @Test
    fun `une distance de soudure absurde ne soude rien`() {
        assertEquals(0f, glassWeldProgress(gapPx = 10f, weldPx = 0f), 0.0001f)
        assertEquals(0f, glassWeldProgress(gapPx = 10f, weldPx = -5f), 0.0001f)
        assertEquals(0f, glassWeldProgress(gapPx = Float.NaN, weldPx = 40f), 0.0001f)
    }

    // — 2. La forme du pont —

    @Test
    fun `le pont est absent quand il n y a rien a souder`() {
        val jointives = upper.copy(top = 200f, bottom = 260f)
        assertNull("formes jointives", glassWeldShape(upper, jointives, 1f, 5f))
        assertNull("formes recouvrantes", glassWeldShape(upper, upper.copy(top = 180f), 1f, 5f))
        assertNull(
            "formes disjointes horizontalement",
            glassWeldShape(upper, lower.copy(left = 400f, right = 600f), 1f, 5f),
        )
        assertNull("soudure nulle", glassWeldShape(upper, lower, 0f, 5f))
    }

    @Test
    fun `le pont existe et son col s elargit avec la soudure`() {
        val debut = glassWeldShape(upper, lower, weld = 0.2f, insetPx = 5f)
        assertNotNull("le pont doit exister dès que la soudure commence", debut)
        val d = debut!!
        val f = glassWeldShape(upper, lower, weld = 1f, insetPx = 5f)!!

        assertTrue(
            "la soudure pleine a un col plus large que la soudure qui commence " +
                "(${d.neckWidth} -> ${f.neckWidth})",
            f.neckWidth > d.neckWidth,
        )
        assertEquals("à soudure pleine, le col rejoint la largeur des formes", f.halfWidth, f.neckHalfWidth, 0.001f)
        assertTrue("la soudure qui commence garde un filet visible", d.neckWidth > 0f)
        assertTrue("mais pas la largeur pleine", d.neckWidth < d.halfWidth)
    }

    // — 3. L'emprise —

    @Test
    fun `le pont ne deborde jamais des deux formes`() {
        val shape = glassWeldShape(upper, lower, weld = 1f, insetPx = 5f)!!
        // Il est centré sur le recouvrement horizontal des deux formes, et pas ailleurs.
        val left = maxOf(upper.left, lower.left)
        val right = minOf(upper.right, lower.right)
        assertEquals((left + right) / 2f, shape.centreX, 0.001f)
        assertEquals("le pont ne sort pas du recouvrement", right - left, shape.halfWidth * 2f, 0.001f)
        // Il entre d'un `inset` dans chaque forme — c'est ce qui le rend continu — et jamais plus.
        assertEquals(upper.bottom - 5f, shape.top, 0.001f)
        assertEquals(lower.top + 5f, shape.bottom, 0.001f)
        assertTrue("la courbure reste dans la hauteur du pont", shape.bend > 0f && shape.bend < shape.span)
    }

    @Test
    fun `les bornes de la soudure sont celles du produit`() {
        // La distance de soudure est du même ordre que le dégagement de la pile du bas : ni
        // invisible (quelques dp), ni permanente (trois fois la hauteur du compositeur).
        assertTrue("la soudure commence à une distance visible", GlassWeldDistanceDp >= 16f)
        assertTrue("mais pas de loin", GlassWeldDistanceDp <= 48f)
        assertTrue("le col garde une largeur non nulle", GlassWeldNeckMin > 0f)
        assertTrue("et ne naît pas à pleine largeur", GlassWeldNeckMin < 1f)
        assertTrue("le pont entre réellement dans les formes", GlassWeldInsetDp > 0f)
    }
}
