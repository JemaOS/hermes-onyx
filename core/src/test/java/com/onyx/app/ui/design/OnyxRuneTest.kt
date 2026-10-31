package com.onyx.app.ui.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **La rune de la maison : un seul signe, et lisible à toutes les tailles.**
 *
 * Le signe du lanceur — le cercle et la rune Othalan **ouverte** — est celui que l'application porte
 * partout où elle dit qui elle est. Ce fichier tient trois choses :
 *
 *  1. **Le tracé est ouvert**, et il vient de l'icône : le glyphe de l'app le **refermait**
 *     (deux jambes jointes par une barre en bas), ce qui faisait deux signes voisins dans la même
 *     application. La mesure est le **recouvrement de silhouette (IoU)** : 1,0 après correction, et
 *     la valeur de l'ancien tracé est **imprimée** — c'est la divergence, chiffrée ;
 *  2. **La planche des tailles** (20 / 24 / 28 / 44 / 56 dp) et le **test qui décide** : le signe
 *     réduit à **20 dp, à un seul bit**, garde sa signature — deux jambes séparées en bas et une
 *     pointe unique en haut. Un signe refermé n'en a qu'une en bas ;
 *  3. **Le contraste de la marque**, au seuil d'icône (3:1) — mesuré, jamais supposé. La marque est
 *     **la plaque sombre, le cercle et la rune** de [OnyxBrand], aux teintes de l'icône : elle ne
 *     dépend **d'aucun thème**, et sa mesure ne change donc pas d'une apparence à l'autre. (Avant,
 *     ce fichier mesurait une encre d'accent sur un disque teinté — la règle d'interface qui
 *     produisait une rune bleue. Un logo ne se dérive pas du thème.)
 */
class OnyxRuneTest {

    /** L'ancien glyphe de l'application : le tracé **fermé**, tel qu'il était écrit. */
    private val ancienGlyphe = "M39.75,75.81L68.25,47.89L54.00,32.20L39.75,47.89L68.25,75.81Z"

    /** Les rangées de pixels d'un rendu à un seul bit. */
    private fun rowsOf(bits: List<Boolean>, side: Int): List<List<Boolean>> =
        (0 until side).map { y -> (0 until side).map { x -> bits[y * side + x] } }

    /** Le nombre de plages de pixels peints d'une rangée : 2 = deux jambes séparées. */
    private fun runs(row: List<Boolean>): Int {
        var count = 0
        var previous = false
        for (painted in row) {
            if (painted && !previous) count++
            previous = painted
        }
        return count
    }

    // ---------------------------------------------------------------------------------------------
    // 1. Un seul signe
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `la rune de la maison est ouverte et vient de l icone`() {
        val trace = OnyxRune.trace
        assertEquals("la rune Othalan a cinq sommets", 5, trace.vertices.size)
        assertTrue(
            "la rune de la maison est OUVERTE : un trace referme en fait un second signe " +
                "dans la meme application",
            !trace.closed,
        )
        assertEquals(
            "le trace de la maison doit etre mot pour mot celui de la couche de premier plan " +
                "de l icone adaptative",
            "M47.15,64.73 L61.08,50.80 L54,43.27 L46.92,50.80 L60.85,64.73",
            OnyxRune.PATH_DATA,
        )
        assertEquals(3.88f, OnyxRune.STROKE_WIDTH, 0.0005f)
    }

    @Test
    fun `le recouvrement de silhouette avec la reference vaut un`() {
        val apres = OnyxRune.iouAgainstReference(OnyxRune.PATH_DATA, OnyxRune.STROKE_WIDTH)
        val avant = OnyxRune.iouAgainstReference(ancienGlyphe, 8.39f)
        println(
            "IoU — rune de l'app contre rune de l'icone : " +
                "AVANT (glyphe ferme de l'app) = " + "%.4f".format(avant) +
                " · APRES (trace de l'icone) = " + "%.4f".format(apres),
        )
        assertEquals("la rune de l'app EST celle de l'icone : recouvrement 1,0", 1.0f, apres, 0.0005f)
        assertTrue(
            "la mesure doit DISTINGUER l'ancien glyphe ferme — sinon elle ne mesure rien",
            avant < 0.999f,
        )
    }

    // ---------------------------------------------------------------------------------------------
    // 2. La planche des tailles, et le test à un bit
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `la planche des tailles est celle que l application emploie`() {
        assertEquals(
            "les tailles du signe : 20 / 24 / 28 / 44 / 56 dp",
            listOf(20.dp, 24.dp, 28.dp, 44.dp, 56.dp),
            OnyxRune.Sizes,
        )
        listOf(
            "Smallest" to OnyxRune.Smallest,
            "Small" to OnyxRune.Small,
            "Medium" to OnyxRune.Medium,
            "Mark" to OnyxRune.Mark,
            "Large" to OnyxRune.Large,
        ).forEach { (nom, taille) ->
            assertTrue(
                "la taille « " + nom + " » (" + taille.value + " dp) n'est pas sur la planche",
                OnyxRune.Sizes.any { it.value == taille.value },
            )
        }
        assertEquals("le signe des vignettes est celui du socle (44 dp)", 44f, OnyxRune.Mark.value, 0.001f)
    }

    @Test
    fun `le rapport d encre est le meme a toutes les tailles de la planche`() {
        val report = StringBuilder()
        val ratios = mutableListOf<Float>()
        OnyxRune.Sizes.forEach { taille ->
            val ratio = OnyxRune.inkRatio(taille.value * 4f, samples = 260)
            ratios += ratio
            report.append("  " + taille.value + " dp -> rapport d'encre " + "%.4f".format(ratio) + "\n")
            // Un signe au trait : ni un aplat (> 0,5) ni une trace invisible (< 0,03).
            assertTrue(
                "à " + taille.value + " dp le rapport d'encre vaut " + "%.4f".format(ratio) +
                    " : ce n'est plus un signe au trait",
                ratio > 0.03f && ratio < 0.5f,
            )
        }
        println("Rapport d'encre du signe, par taille :\n" + report)
        val ecart = ratios.max() - ratios.min()
        assertTrue(
            "le rapport d'encre doit etre le meme a toutes les tailles (il varie de " +
                "%.4f".format(ecart) + " — le signe se deforme en changeant de taille)",
            ecart < 0.02f,
        )
    }

    @Test
    fun `a vingt dp et a un seul bit le signe garde sa signature`() {
        val side = 20
        val bits = OnyxRune.oneBit(side)
        assertEquals(side * side, bits.size)
        val painted = bits.count { it }
        println(
            "Le signe a 20 dp, a un seul bit : " + painted + " pixels peints sur " + (side * side) +
                " (" + "%.1f".format(100f * painted / (side * side)) + " %)",
        )
        assertTrue("a 20 dp le signe doit encore porter de l'encre (il en porte $painted)", painted >= 20)

        val rows = rowsOf(bits, side)
        val derniere = rows.indexOfLast { row -> row.any { it } }
        val premiere = rows.indexOfFirst { row -> row.any { it } }
        val runsBas = runs(rows[derniere])
        val runsHaut = runs(rows[premiere])
        println(
            "Signature a 20 dp : rangée du haut = $runsHaut plage(s), rangée du bas = " +
                "$runsBas plage(s) (dernière rangée peinte : $derniere, première : $premiere)",
        )
        assertEquals(
            "en bas, la rune de la maison a DEUX jambes séparées — un signe refermé n'en a qu'une, " +
                "c'est exactement la divergence que ce verrou interdit",
            2,
            runsBas,
        )
        assertEquals("en haut, la pointe est unique", 1, runsHaut)

        // La morsure : sur l'ancien glyphe fermé, la mesure doit tomber à une seule plage.
        val ancien = OnyxRune.oneBit(OnyxRune.parse(ancienGlyphe, 8.39f), side)
        val runsAncien = runs(rowsOf(ancien, side).let { r -> r[r.indexOfLast { row -> row.any { it } }] })
        println("Morsure : sur le glyphe fermé de l'app, la rangée du bas a $runsAncien plage(s)")
        assertEquals(
            "la mesure doit VOIR le signe fermé (une seule plage en bas) — sinon elle ne mesure rien",
            1,
            runsAncien,
        )
    }

    // ---------------------------------------------------------------------------------------------
    // 3. Le contraste de la marque (seuil d'icône), invariant au thème
    // ---------------------------------------------------------------------------------------------

    /**
     * **La marque ne se dérive pas du thème : son contraste est le même en clair et en sombre.**
     *
     * On mesure l'encre de la rune contre les fonds réels du dessin — le haut et le bas du **cercle**,
     * et la **plaque** — et le **contour** contre la plaque et le cercle, au seuil d'icône (3:1). Les
     * valeurs ne dépendent d'aucun thème : c'est le point du verrou retourné (voir `OnyxBrand`).
     */
    @Test
    fun `l encre de la marque tient le seuil d icone dans les deux apparences`() {
        val paires = listOf(
            "rune / cercle haut" to (OnyxBrand.RuneInk to OnyxBrand.CircleTop),
            "rune / cercle bas" to (OnyxBrand.RuneInk to OnyxBrand.CircleBottom),
            "rune / plaque" to (OnyxBrand.RuneInk to OnyxBrand.Plate),
            "contour / plaque" to (OnyxBrand.Outline to OnyxBrand.Plate),
            "contour / cercle bas" to (OnyxBrand.Outline to OnyxBrand.CircleBottom),
        )
        val report = StringBuilder()
        val sousLeSeuil = mutableListOf<String>()
        paires.forEach { (nom, couleurs) ->
            val (encre, fond) = couleurs
            val ratio = OnyxContrast.ratio(encre, fond)
            val ok = ratio + 1e-4f >= OnyxContrast.ICON_MIN
            if (!ok) sousLeSeuil += nom + " : " + "%.2f".format(ratio) + ":1"
            report.append(
                "  " + nom + " : " + "%.2f".format(ratio) + ":1" +
                    (if (ok) "  ok" else "  SOUS LE SEUIL") + "\n",
            )
        }
        println("Contraste de la marque (seuil d'icône 3:1, aucune dépendance au thème) :\n" + report)
        assertTrue(
            "des traits de la marque passent sous le seuil d'icône :\n" + sousLeSeuil.joinToString("\n"),
            sousLeSeuil.isEmpty(),
        )

        // **Les teintes sont celles de l'icône de lanceur, et elles ne varient pas** : c'est ce qui
        // rend la marque identique en clair et en sombre (le fichier de l'app les compare aux
        // ressources de l'icône ; ici on verrouille les valeurs, sans thème).
        assertEquals("la plaque est celle de l'icône (#1A202A)", Color(0xFF1A202A), OnyxBrand.Plate)
        assertEquals("le haut du cercle est celui de l'icône (#36404C)", Color(0xFF36404C), OnyxBrand.CircleTop)
        assertEquals("le bas du cercle est celui de l'icône (#222937)", Color(0xFF222937), OnyxBrand.CircleBottom)
        assertEquals("le contour est celui de l'icône (#A9B3C2)", Color(0xFFA9B3C2), OnyxBrand.Outline)
        assertEquals("l'encre de la rune est celle de l'icône (#DADFE8)", Color(0xFFDADFE8), OnyxBrand.RuneInk)
    }
}
