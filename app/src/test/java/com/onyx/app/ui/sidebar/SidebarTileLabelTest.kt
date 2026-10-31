package com.onyx.app.ui.sidebar

import com.onyx.app.localization.OnyxLocalization
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Aucun libellé de tuile ne se coupe au milieu d'un mot — et c'est `tileLabelPlan` qui le tient.**
 *
 * Le volet montre ses écrans en tuiles, deux par rangée. Le libellé d'une tuile se replie **entre
 * ses mots** ; quand un mot est plus long que la place, la ligne se casse au milieu (« Compétence »
 * / « s ») — c'est le défaut relevé sur la capture, et ce n'est pas un détail typographique : le mot
 * ne se lit plus.
 *
 * La fusion tient ce défaut par **deux mécanismes qui se complètent** (voir `ChatSidebar.kt`) :
 *
 * 1. **`TileLabelFit`** retient **une seule** taille pour toute la grille — celle qui fait tenir le
 *    mot le plus large du volet ([TileLabelWordLimit] est le budget de la référence, à `caption` et
 *    police 1,3×, avant que la taille ne cède) ; deux tuiles voisines dont les libellés changent de
 *    taille se liraient comme un défaut.
 * 2. **`tileLabelPlan`** range ensuite le libellé en lignes coupées **aux espaces seulement** — un
 *    mot est un groupe indivisible, jamais coupé.
 *
 * Ce test vérifie la règle sur **tous** les libellés de tuiles, dans les deux langues que l'app
 * sert, avec la mesure de caractères comme jauge (elle suffit à comparer des mots) : la somme des
 * lignes redonne **exactement** le libellé, donc aucun mot n'a été coupé, et le ratio de taille
 * cède avant le mot.
 */
class SidebarTileLabelTest {

    /** La jauge du libellé : sa longueur, en caractères. Assez fidèle pour comparer des mots. */
    private fun measure(text: String): Float = text.length.toFloat()

    /** La place réellement laissée au libellé d'une tuile, en « caractères » — la marge étroite. */
    private val available = 12f

    private fun widestWord(label: String, locale: Locale): Int =
        OnyxLocalization.translate(label, locale)
            .split(' ', '·', '-', '/')
            .filter { it.isNotEmpty() }
            .maxOf { it.length }

    @Test
    fun `aucun mot d aucun libelle de tuile n est coupe, en francais comme en anglais`() {
        for (locale in listOf(Locale.FRANCE, Locale.ENGLISH)) {
            SidebarDestinations.tiles.forEach { tile ->
                val label = OnyxLocalization.translate(tile.label, locale)
                val plan = tileLabelPlan(label = label, measure = ::measure, available = available)
                // Chaque ligne est un groupe de **mots entiers** : remise bout à bout avec un espace,
                // elle redonne le libellé exact — donc aucun mot n'a été coupé au milieu.
                assertEquals(
                    "« $label » ($locale) : une ligne coupe un mot",
                    label,
                    plan.lines.joinToString(" "),
                )
                // Et la taille n'augmente jamais au-dessus de la nominale.
                assertTrue("« $label » : le ratio dépasse 1", plan.fontRatio <= 1f)
            }
        }
    }

    @Test
    fun `le cas reel qui a produit le defaut tient, et la taille cede avant le mot`() {
        // « Compétences » (11 caractères) sur un téléphone à police 1,3× : c'est lui qui fixe le
        // budget de la référence ([TileLabelWordLimit]). Sous la place étroite d'une tuile, la taille
        // cède — mais **le mot reste d'un bloc**.
        val narrow = 10f
        val plan = tileLabelPlan(label = "Compétences", measure = ::measure, available = narrow)
        assertEquals("« Compétences » doit tenir sur une ligne entière", listOf("Compétences"), plan.lines)
        assertTrue("la taille n'a pas cédé alors que le mot débordait (11 > $narrow)", plan.fontRatio < 1f)

        // « Fichiers du serveur » est long **en mots**, pas en mot : il se replie entre eux, et aucun
        // de ses mots n'approche le budget.
        val files = SidebarDestinations.tiles.first { it.id == "FILES" }
        assertEquals("Fichiers du serveur", OnyxLocalization.translate(files.label, Locale.FRANCE))
        assertEquals("aucun de ses mots ne doit s'approcher du budget", 8, widestWord(files.label, Locale.FRANCE))

        // Le budget de la référence est toujours celui du cas le plus large du volet.
        val widestFrench = SidebarDestinations.tiles.maxOf { widestWord(it.label, Locale.FRANCE) }
        assertTrue(
            "le mot le plus large du volet ($widestFrench) dépasse le budget de la référence ($TileLabelWordLimit) " +
                "sans que la taille puisse céder",
            widestFrench <= TileLabelWordLimit + 2,
        )
    }
}
