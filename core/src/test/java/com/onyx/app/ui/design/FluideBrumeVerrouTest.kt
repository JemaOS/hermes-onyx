package com.onyx.app.ui.design

import java.io.File
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le verrou STRUCTUREL de la couverture — il mesure la NATURE du dessin, pas ses moyennes.**
 *
 * Les deux passes précédentes sont passées à côté parce qu'elles ne mesuraient que des
 * **moyennes sur toute la carte** (occupation, gradient moyen, luminance moyenne, largeur moyenne).
 * Or **un champ de lignes régulières et une brume irrégulière ont des moyennes voisines** : le rendu
 * était déclaré « proche » alors qu'il était **d'une autre nature**. Ce qu'il faut mesurer, c'est la
 * **structure** :
 *
 *  - **la cohérence d'orientation** (tenseur de structure global sur la bande `v` 0,05 → 0,45) :
 *    `0` = isotrope (les volutes s'enroulent dans tous les sens), `1` = tout parallèle. Mesuré :
 *    **planche 0,188**, app d'avant **0,789**, app d'après **0,035**. C'est LA mesure qui tranche ;
 *  - **la source** : le programme porte la **crête d'un champ volumétrique** ([Fluide.champ]) et
 *    **plus aucune** fonction de potentiel de raies — c'est le mécanisme du défaut.
 *
 * **La morsure est prouvée par mutation** : remettre `potentiel`/`fil` à la place du champ fait
 * rougir le test (la cohérence remonte vers 0,79). Un verrou qui n'a jamais échoué ne protège rien.
 */
class FluideBrumeVerrouTest {

    private val TITRE = "Réunion de chantier"
    private val ID = "conv-1"

    /** La bande du fluide : la même que celle des mesures du rapport (`v` 0,05 → 0,45). */
    private fun bande(plan: Fluide.Plan, W: Int = 379, H: Int = 192): FloatArray {
        val y0 = (0.05f * H).toInt()
        val y1 = (0.45f * H).toInt()
        val l = FloatArray(W * (y1 - y0))
        for (y in y0 until y1) {
            for (x in 0 until W) {
                val c = Fluide.sample(plan, (x + 0.5f) / W, (y + 0.5f) / H, 0f)
                l[(y - y0) * W + x] = 0.2126f * c[0] + 0.7152f * c[1] + 0.0722f * c[2]
            }
        }
        return l
    }

    /**
     * **La cohérence d'orientation** : le tenseur de structure global
     * `J = Σ |∇l|²·(∇l ⊗ ∇l)`; on rend `(λ1 − λ2)/(λ1 + λ2)`.
     */
    private fun coherence(l: FloatArray, W: Int, H: Int): Float {
        var jxx = 0.0; var jyy = 0.0; var jxy = 0.0
        for (y in 1 until H - 1) {
            for (x in 1 until W - 1) {
                val i = y * W + x
                val gx = (l[i + 1] - l[i - 1]) * 0.5f
                val gy = (l[i + W] - l[i - W]) * 0.5f
                val w = (gx * gx + gy * gy).toDouble()
                jxx += w * gx * gx; jyy += w * gy * gy; jxy += w * gx * gy
            }
        }
        val tr = jxx + jyy
        val det = jxx * jyy - jxy * jxy
        val d = sqrt((tr * tr / 4.0 - det).coerceAtLeast(0.0))
        val l1 = tr / 2.0 + d; val l2 = tr / 2.0 - d
        return ((l1 - l2) / (l1 + l2 + 1e-12)).toFloat()
    }

    /** **Le taux de volute** : part des crêtes dont les deux courbures sont du même ordre. */
    private fun tauxVolute(l: FloatArray, W: Int, H: Int): Float {
        var volutes = 0; var total = 0
        for (y in 2 until H - 2) {
            for (x in 2 until W - 2) {
                val i = y * W + x
                val gxx = l[i + 2] - 2 * l[i] + l[i - 2]
                val gyy = l[i + 2 * W] - 2 * l[i] + l[i - 2 * W]
                val gxy = (l[i + W + 1] - l[i + W - 1] - l[i - W + 1] + l[i - W - 1]) * 0.25f
                val tr = gxx + gyy
                val det = gxx * gyy - gxy * gxy
                val d = sqrt((tr * tr / 4f - det).coerceAtLeast(0f))
                val e1 = tr / 2f + d; val e2 = tr / 2f - d
                if (e1 < -1e-5f) {
                    val mx = maxOf(abs(e1), abs(e2)); val mn = minOf(abs(e1), abs(e2))
                    total++
                    if (mn / (mx + 1e-9f) > 0.35f) volutes++
                }
            }
        }
        return if (total == 0) 0f else volutes.toFloat() / total
    }

    @Test
    fun `le dessin est ISOTROPE (des volutes), plus un champ de lignes paralleles`() {
        listOf(true, false).forEach { dark ->
            val plan = Fluide.plan(TITRE, ID, dark = dark, choix = ChoixCouverture.DEFAUT)
            val W = 379; val H = 192
            val l = bande(plan, W, H)
            val y0 = (0.05f * H).toInt(); val y1 = (0.45f * H).toInt()
            val coh = coherence(l, W, y1 - y0)
            val vol = tauxVolute(l, W, y1 - y0)
            println(
                (
                    "MESURE structure (%s) : cohérence d'orientation %.3f (planche 0,188 · app d'avant 0,789) | " +
                        "taux de volute %.2f (planche 0,44 · app d'avant 0,12)"
                    ).format(if (dark) "sombre" else "clair", coh, vol),
            )
            assertTrue(
                "le dessin doit être ISOTROPE — des volutes, pas un champ de lignes " +
                    "(${if (dark) "sombre" else "clair"} : cohérence %.3f, seuil 0,35 ; l'app d'avant valait 0,789)".format(coh),
                coh < 0.35f,
            )
            assertTrue(
                "le dessin doit être majoritairement en volute — des crêtes dont les deux courbures " +
                    "sont du même ordre (${if (dark) "sombre" else "clair"} : %.2f, seuil 0,35)".format(vol),
                vol > 0.35f,
            )
        }
    }

    @Test
    fun `le dessin n a plus de direction dominante — la crete du champ, pas un potentiel de raies`() {
        // L'orientation globale ne doit PAS être celle d'une famille unique. On la relève sur les
        // gradients : la direction dominante ne doit pas porter la majorité de l'énergie.
        listOf(true, false).forEach { dark ->
            val plan = Fluide.plan(TITRE, ID, dark = dark, choix = ChoixCouverture.DEFAUT)
            val W = 379; val H = 192
            val l = bande(plan, W, H)
            val y0 = (0.05f * H).toInt(); val y1 = (0.45f * H).toInt()
            val h = y1 - y0
            // La rose des orientations, pondérée par l'énergie du gradient.
            val bins = FloatArray(12)
            for (y in 1 until h - 1) {
                for (x in 1 until W - 1) {
                    val i = y * W + x
                    val gx = (l[i + 1] - l[i - 1]) * 0.5f
                    val gy = (l[i + W] - l[i - W]) * 0.5f
                    val e = gx * gx + gy * gy
                    if (e < 1e-8f) continue
                    var a = atan2(gy, gx)
                    if (a < 0) a += Math.PI.toFloat()
                    val b = ((a / Math.PI) * 12).toInt().coerceIn(0, 11)
                    bins[b] += e
                }
            }
            val tot = bins.sum()
            var dir = 0f
            for (k in 0 until 6) {
                val s = bins[k] + bins[k + 6]
                if (s > dir) dir = s
            }
            val directivite = dir / tot
            println(
                "MESURE directivité (%s) : la direction dominante porte %.1f%% de l'énergie du gradient".format(
                    if (dark) "sombre" else "clair", directivite * 100,
                ),
            )
            assertTrue(
                "aucune direction ne doit dominer — mesuré %.1f%% (seuil 45%% ; un champ de lignes régulières en porte plus de 90%%)".format(
                    directivite * 100,
                ),
                directivite < 0.45f,
            )
            assertTrue("la rose d'orientation doit être peuplée", tot > 0f)
        }
    }

    @Test
    fun `le programme porte la crete du champ et plus aucun potentiel de raies`() {
        val cover = "src/main/java/com/onyx/app/ui/design/FluideCover.kt"
        assertTrue(
            "le programme doit lire les crêtes d'un champ volumétrique — `float champ(float2 p)` absent de $cover",
            sourceDe(cover, "float champ(float2 p)") != null,
        )
        assertTrue(
            "le programme ne doit plus porter le potentiel de raies — `float potentiel(` encore présent dans $cover",
            sourceDe(cover, "float potentiel(") == null,
        )
        assertTrue(
            "le programme ne doit plus prendre le niveau d'une rampe — `abs(fract(L)-0.5)` encore présent dans $cover",
            sourceDe(cover, "abs(fract(L)-0.5)") == null,
        )
        assertTrue(
            "la crête doit garder son épaisseur VARIABLE — `BAND_MIN + (BAND_MAX-BAND_MIN)` absent de $cover",
            sourceDe(cover, "BAND_MIN + (BAND_MAX-BAND_MIN)") != null,
        )
        assertTrue(
            "le halo doit déborder — `smoothstep(HALO_W, 0.0, uc)` absent de $cover",
            sourceDe(cover, "smoothstep(HALO_W, 0.0, uc)") != null,
        )
        // Le grain de la planche et le halo solidaire de la tête sont conservés tels quels.
        assertTrue(
            "le grain de pas de trois pixels doit rester — absent de $cover",
            sourceDe(cover, "floor(fragCoord.x/3.0)+floor(fragCoord.y/3.0)*57.0") != null,
        )
        assertTrue(
            "le halo doit rester SOLIDAIRE de la tête — absent de $cover",
            sourceDe(cover, "float halo = exp(-dd*dd/(2.0*0.26*0.26));") != null,
        )
    }

    private fun sourceDe(chemin: String, motif: String): Pair<String, Int>? {
        val cible = File(chemin)
        if (!cible.isFile) return null
        cible.readLines().forEachIndexed { index, ligne ->
            if (ligne.contains(motif)) return cible.path to (index + 1)
        }
        return null
    }
}
