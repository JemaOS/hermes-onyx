package com.onyx.app.ui.design

import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La **bifurcation** de la rangée d'actions : le dessin de la référence, relevé dans le vectoriel.
 *
 * Provenance, vérifiable : la référence dessine ce bouton avec le jeu `com.composables:icons-lucide`
 * en version **1.1.0** (`gradle/libs.versions.toml:6` et `:54`), et la source de cet artefact —
 * `commonMain/com/composables/icons/lucide/GitBranch.kt`, dans `icons-lucide-android-1.1.0-sources.jar`
 * — donne la géométrie exacte :
 *
 *  - un trait de (6,3) à (6,15) ;
 *  - un rond de rayon 3 sur (18,6) ;
 *  - un rond de rayon 3 sur (6,18) ;
 *  - l'arc de rayon 9 allant de (18,9) à (9,18) ;
 *
 * le tout sur une grille de 24, au trait de **2**, bouts et jointures **ronds**, sans surface
 * peinte. Ces quatre tracés sont ceux du fichier, aux mêmes nombres. La couleur et le remplissage de
 * la source ne sont pas repris : la maison tinte ses icônes ([OnyxIcon]) et ne peint rien.
 *
 * Et l'encre tombe **dans la zone** de la famille (2 à 22 sur les deux axes), centrée sur (12, 12) :
 * c'est la règle que tiennent les glyphes de la rangée (`ActionIconGeometryTest`).
 */
class BranchGlyphGeometryTest {

    private val source = File("src/main/res/drawable/ic_lucide_git_branch.xml").readText()

    private fun pathDatas(): List<String> =
        Regex("android:pathData=\"([^\"]*)\"").findAll(source).map { it.groupValues[1] }.toList()

    private fun count(pattern: String): Int = Regex(pattern).findAll(source).count()

    /** La boîte d'encre : les points des tracés, arcs compris, élargis d'un demi-trait. */
    private fun ink(): List<Double> {
        val points = pathDatas().flatMap { Drawings.flatten(it) }
        assertEquals("les tracés se lisent", true, points.isNotEmpty())
        return listOf(
            points.minOf { it.first } - 1.0, points.minOf { it.second } - 1.0,
            points.maxOf { it.first } + 1.0, points.maxOf { it.second } + 1.0,
        )
    }

    @Test
    fun `les quatre traces de la reference sont la, aux memes nombres`() {
        val datas = pathDatas()
        assertEquals("le trait, les deux ronds et l'arc", 4, datas.size)
        // Le trait vertical, de (6,3) à (6,15).
        assertEquals("le trait", "M6,3 L6,15", datas[0])
        // Le rond du haut : centre (18,6), rayon 3 → de (15,6) à (21,6) et retour.
        assertEquals("le rond du haut", "M15,6 A3,3 0 1 1 21,6 A3,3 0 1 1 15,6 Z", datas[1])
        // Le rond du bas : centre (6,18), rayon 3 → de (3,18) à (9,18) et retour.
        assertEquals("le rond du bas", "M3,18 A3,3 0 1 1 9,18 A3,3 0 1 1 3,18 Z", datas[2])
        // La branche : l'arc de 9, du bas du rond du haut à la droite du rond du bas.
        assertEquals("l'arc de la référence", "M18,9 A9,9 0 0 1 9,18", datas[3])
    }

    @Test
    fun `le trait fait deux, les bouts et les jointures sont ronds, rien n est peint`() {
        assertEquals("un trait de 2 par tracé", 4, count("android:strokeWidth=\"2\""))
        assertEquals("bouts ronds", 4, count("android:strokeLineCap=\"round\""))
        assertEquals("jointures rondes", 4, count("android:strokeLineJoin=\"round\""))
        assertEquals("quatre tracés, aucun aplat", 4, count("@android:color/transparent"))
        assertTrue("aucune surface peinte", !source.contains("fillColor=\"#"))
    }

    @Test
    fun `l encre tient dans la zone de vingt, centree sur douze`() {
        val (x0, y0, x1, y1) = ink()
        assertTrue("bord gauche $x0", x0 >= 1.95)
        assertTrue("bord haut $y0", y0 >= 1.95)
        assertTrue("bord droit $x1", x1 <= 22.05)
        assertTrue("bord bas $y1", y1 <= 22.05)
        assertEquals("centre d'encre x", 12.0, (x0 + x1) / 2, 0.05)
        assertEquals("centre d'encre y", 12.0, (y0 + y1) / 2, 0.05)
        // Et la boîte est **exactement** celle de la famille : 2 à 22 des deux côtés.
        assertEquals("bord gauche exact", 2.0, x0, 0.05)
        assertEquals("bord haut exact", 2.0, y0, 0.05)
        assertEquals("bord droit exact", 22.0, x1, 0.05)
        assertEquals("bord bas exact", 22.0, y1, 0.05)
    }

    @Test
    fun `le dessin ne porte que des nombres entiers de la grille`() {
        // Aucune valeur étrangère au dessin de la référence : tout nombre lu est entier.
        pathDatas().forEach { data ->
            Regex("-?(?:\\d+\\.?\\d*|\\.\\d+)").findAll(data).forEach { found ->
                val value = found.value.toDouble()
                assertTrue("valeur hors grille : $value", abs(value - value.roundToInt()) < 1e-9)
            }
        }
        assertTrue("l'arc porte son rayon", pathDatas()[3].contains("A9,9"))
    }
}
