package com.onyx.app.ui.design

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le **combiné d'appel**, relevé dans les deux vectoriels — pas dans une capture.
 *
 * Le défaut d'origine : `ic_sym_phone_fill.xml`, le dessin qui porte `phone.fill` et que la rangée
 * « Appels » de Réglages affichait dans sa vignette, était un **rectangle arrondi plein** — le corps
 * d'un appareil, pas un combiné. Peint sur toute sa surface dans une vignette de 32 dp, au milieu
 * d'une colonne de glyphes au trait fin, il se lisait comme un bouton d'arrêt égaré : un carré noir.
 *
 * Les deux dessins partagent maintenant **une seule silhouette** — le combiné de la référence — et
 * la règle de la maison : boîte de **24**, **trait de 2** (celui de la famille, `MenuIconFamilyGeometryTest`),
 * bouts et jointures **ronds**, rien de peint pour le contour, l'encre du contour dans la zone de
 * vingt (**2 à 22**) et centrée sur **(12, 12)**. Le plein est le **contour du tracé**, poussé d'un
 * demi-trait vers l'extérieur (échelle 10/9 autour du centre : l'encre du tracé va de 3 à 21, celle
 * du plein de 2 à 22), pour que les deux partagent le **même bord extérieur** — la règle des
 * dossiers (`FolderIconGeometryTest`).
 */
class PhoneGlyphGeometryTest {

    private fun drawable(name: String): String = listOf(
        File("src/main/res/drawable/$name.xml"),
        File("../core/src/main/res/drawable/$name.xml"),
        File("core/src/main/res/drawable/$name.xml"),
    ).first { it.isFile }.readText()

    private val outline = "ic_sym_phone"
    private val filled = "ic_sym_phone_fill"

    private val outlinePath =
        "M6.62,10.79 c1.44,2.83 3.76,5.14 6.59,6.59 l2.2,-2.2 c0.27,-0.27 0.67,-0.36 1.02,-0.24 " +
            "c1.12,0.37 2.33,0.57 3.57,0.57 c0.55,0 1,0.45 1,1 V20 c0,0.55 -0.45,1 -1,1 " +
            "c-9.39,0 -17,-7.61 -17,-17 c0,-0.55 0.45,-1 1,-1 h3.5 c0.55,0 1,0.45 1,1 " +
            "c0,1.25 0.2,2.45 0.57,3.57 c0.11,0.35 0.03,0.74 -0.25,1.02 Z"

    private fun attribute(xml: String, name: String): Double? =
        Regex("android:$name=\"([^\"]*)\"").find(xml)?.groupValues?.get(1)?.toDouble()

    /** L'échelle du `<group>` du plein : la poussée d'un demi-trait vers l'extérieur. */
    private val scale = 10.0 / 9.0

    private fun paintedInk(xml: String): List<Double> {
        // Le tracé, mis à l'échelle du groupe autour du centre de la boîte.
        val (x0, y0, x1, y1) = Drawings.ink(Drawings.of(xml)).toList()
        val pivotX = attribute(xml, "pivotX") ?: 12.0
        val pivotY = attribute(xml, "pivotY") ?: 12.0
        val sx = attribute(xml, "scaleX") ?: 1.0
        val sy = attribute(xml, "scaleY") ?: 1.0
        fun sx1(v: Double) = pivotX + (v - pivotX) * sx
        fun sy1(v: Double) = pivotY + (v - pivotY) * sy
        return listOf(sx1(x0), sy1(y0), sx1(x1), sy1(y1))
    }

    // ---- 1. le contour -------------------------------------------------------------------------

    @Test
    fun `le combine au trait est sur la grille de la famille, au trait de deux`() {
        val xml = drawable(outline)
        val d = Drawings.of(xml)
        assertEquals("un seul tracé", 1, d.elements.size)
        val path = d.elements[0]
        assertEquals("son trait", 2.0, path.stroke!!, 0.001)
        assertEquals("les bouts", "round", path.cap)
        assertEquals("les jointures", "round", path.join)
        assertTrue("rien de peint", path.fill == "@android:color/transparent")
        assertEquals("la boîte", 24.0, Drawings.viewport(xml, "width"), 0.001)
        assertEquals("la boîte", 24.0, Drawings.viewport(xml, "height"), 0.001)
        assertEquals("la boîte", 24.0, Drawings.viewport(xml, "viewportWidth"), 0.001)
        assertEquals("la boîte", 24.0, Drawings.viewport(xml, "viewportHeight"), 0.001)
    }

    @Test
    fun `l encre du combine tient exactement dans la zone de vingt, centree`() {
        val (x0, y0, x1, y1) = Drawings.ink(Drawings.of(drawable(outline))).toList()
        // Le combiné porte la boîte **pleine** de la famille : 2 à 22 des deux côtés.
        assertEquals("bord gauche", 2.0, x0, 0.01)
        assertEquals("bord haut", 2.0, y0, 0.01)
        assertEquals("bord droit", 22.0, x1, 0.01)
        assertEquals("bord bas", 22.0, y1, 0.01)
        assertEquals("centre d'encre x", 12.0, (x0 + x1) / 2, 0.01)
        assertEquals("centre d'encre y", 12.0, (y0 + y1) / 2, 0.01)
    }

    @Test
    fun `le combine est celui de la reference, aux memes nombres`() {
        // Le tracé de la référence, relevé ligne à ligne : le combiné de biais, ses deux oreilles
        // arrondies et son arc de gorge. Rien n'a été redessiné « à la main ».
        assertEquals(
            outlinePath,
            Drawings.of(drawable(filled)).elements[0].data,
        )
    }

    // ---- 2. le plein ---------------------------------------------------------------------------

    @Test
    fun `le plein est le contour du trace, pousse d un demi-trait`() {
        val xml = drawable(filled)
        val d = Drawings.of(xml)
        assertEquals("un seul tracé", 1, d.elements.size)
        assertTrue("aucun trait", d.elements[0].stroke == null)
        assertTrue("une surface peinte", d.elements[0].fill != null)
        // L'échelle déclarée, et le centre de la poussée.
        assertEquals("l'échelle x", scale, attribute(xml, "scaleX")!!, 1e-4)
        assertEquals("l'échelle y", scale, attribute(xml, "scaleY")!!, 1e-4)
        assertEquals("le centre de la poussée", 12.0, attribute(xml, "pivotX")!!, 0.001)
        assertEquals("le centre de la poussée", 12.0, attribute(xml, "pivotY")!!, 0.001)
        // L'encre du tracé non poussé, puis la poussée : 3 -> 2 et 21 -> 22 des deux côtés.
        val (bx0, by0, bx1, by1) = Drawings.ink(d).toList()
        assertEquals("le tracé avant poussée", listOf(3.0, 3.0, 21.0, 21.0), listOf(bx0, by0, bx1, by1))
        val (x0, y0, x1, y1) = paintedInk(xml).toList()
        assertEquals("bord gauche du plein", 2.0, x0, 0.01)
        assertEquals("bord haut du plein", 2.0, y0, 0.01)
        assertEquals("bord droit du plein", 22.0, x1, 0.01)
        assertEquals("bord bas du plein", 22.0, y1, 0.01)
        assertEquals("centre x", 12.0, (x0 + x1) / 2, 0.01)
        assertEquals("centre y", 12.0, (y0 + y1) / 2, 0.01)
    }

    // ---- 3. le rectangle plein n'est plus là ---------------------------------------------------

    @Test
    fun `l ancien rectangle plein n est plus dans le fichier`() {
        val xml = drawable(filled)
        // Le corps d'appareil d'avant : un cadre arrondi plein, et son encoche (deux sous-chemins
        // de **même sens**, donc pas de creux — la surface était peinte en entier : le carré noir.
        listOf(
            "M6.6,2.8 H17.4",
            "A2.2,2.2 0 0 1 19.6,5.0",
            "M9.4,6.2 H14.6 V7.8 H9.4 Z",
            "android:fillType",
        ).forEach { artefact ->
            assertFalse("le plein porte encore « $artefact »", xml.contains(artefact))
        }
        // Et son nom dit maintenant ce qu'il dessine : le combiné.
        assertEquals(outlinePath, Drawings.of(xml).elements[0].data)
    }
}
