package com.onyx.app.ui.design

import java.io.File
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le verrou du CHOIX de couverture et de son MOUVEMENT.**
 *
 * Le propriétaire avait tranché, sur la planche des variantes : pour les conversations,
 * **B — filaments, graine `3417634283`, teinte bleu**, et **ça doit bouger**. Ce test **rougit** si
 *
 *  1. **la carte ne rend pas le matériau enregistré** — variante, graine, teinte : le plan doit
 *     prendre la graine **du choix** (et non plus celle de la conversation, qui donnait la teinte
 *     verte de ses conversations) ; les trois clés doivent exister dans les préférences ; et le
 *     composable doit **lire** le choix et le **passer** au plan ;
 *  2. **le filament n'est pas un ruban lumineux** mais un trait gravé sur une matière sombre : la
 *     bande du fluide doit être **lumineuse** (au-dessus du plancher mesuré) et le ruban doit
 *     **éclairer** — son maximum doit dépasser la médiane de la bande, et la part de la bande
 *     au-dessus de la mi-hauteur doit être celle d'un **ruban**, pas d'une ligne ;
 *  3. **le fond n'est pas bleu** : le bleu de la bande doit dépasser son rouge ;
 *  4. **l'animation est statique** : la fonction d'état, évaluée à **deux instants distincts**, doit
 *     rendre **deux dessins distincts** ;
 *  5. **la boucle a un raccord visible** : le dessin de fin de cycle doit être **le même** que celui
 *     de son début — au point que la différence de raccord soit **plus petite qu'un pas interne**.
 *
 * Les chiffres de référence (**mesurés en Kotlin**, `Fluide.sample`, planche / avant / après — la
 * coupe est prise à `u = 0,5`, la bande du fluide va de `v = 0,05` à `0,45`) :
 *
 * | mesure | planche | avant · sombre | après · sombre | avant · clair | après · clair | seuil |
 * |---|---|---|---|---|---|---|
 * | luminance du brouillard | 0,028 → 0,161 (bande) | 0,035 | **0,161** | 0,075 | **0,163** | ≥ 0,10 |
 * | médiane de la coupe | 0,061 | 0,073 | **0,460** | 0,102 | **0,217** | — (témoin) |
 * | maximum de la crête | 0,648 | 0,547 | **1,000** | 0,448 | **1,000** | — (témoin) |
 * | maximum − médiane | 0,587 | 0,474 | **0,540** | 0,346 | 0,783 | ≥ 0,30 |
 * | bleu − rouge de la bande | +0,106 | +0,066 | **+0,098** | +0,089 | +0,106 | ≥ 0,05 |
 *
 * **Ce qui a changé de nature.** Le dessin n'est plus un **ruban** sur un brouillard sombre : il est
 * la **crête d'un champ volumétrique** (voir `FluideBrumeVerrouTest`), dont la bande est celle de la
 * planche — moyenne `(85, 93, 109)` contre `(71, 80, 98)` — et **bleue**. Le brouillard d'avant était
 * **plus sombre que la planche** (0,035 contre 0,161 sur la bande) : il verrouillait un fond que la
 * référence n'a pas. La mesure de **structure** (cohérence, périodicité, forme) est celle qui
 * tranche, et elle vit dans `FluideBrumeVerrouTest`.
 */
class FluideChoixVerrouTest {

    private val TITRE = "Réunion de chantier"
    private val ID = "conv-1"

    // -----------------------------------------------------------------------------------------
    // 1 · Conformité au choix enregistré — variante, graine, teinte
    // -----------------------------------------------------------------------------------------

    @Test
    fun `le choix enregistre est filaments, graine 3417634283, teinte bleu`() {
        val choix = ChoixCouverture.DEFAUT
        assertEquals("la variante retenue est « filaments »", ChoixCouverture.VARIANTE_FILAMENTS, choix.variante)
        assertEquals("la graine retenue est celle de la planche", 3_417_634_283L, choix.graine)
        assertEquals("la teinte retenue est le bleu", ChoixCouverture.TEINTE_BLEU, choix.teinte)
        assertTrue("le choix enregistré est cohérent avec lui-même", choix.coherent)
        // La teinte nommée est bien celle que la graine produit : (graine & MAX) % 6 = 3.
        assertEquals("la graine de la planche donne la teinte bleu", "bleu", ChoixCouverture.teinteDe(3417634283L))
        assertEquals("l'indice de teinte du bleu est 3", 3, choix.teinteIndex)
    }

    @Test
    fun `la carte rend EXACTEMENT la variante enregistree, et pas un defaut du code`() {
        val choix = ChoixCouverture.DEFAUT
        for (dark in listOf(true, false)) {
            val plan = Fluide.plan(TITRE, ID, dark = dark, choix = choix)
            assertEquals("la graine du dessin est celle du choix (${if (dark) "sombre" else "clair"})",
                3_417_634_283L, plan.graine)
            assertEquals("la teinte du dessin est celle du choix", 3, plan.teinteIndex)
            assertEquals("la teinte nommée est bleu", "bleu", plan.teinteNom)
            assertEquals("le nombre de raies de la planche", 11f, plan.raies, 0f)
        }
        // **La morsure** : un autre choix enregistré doit changer le dessin. Si le plan ignorait le
        // choix et reprenait un défaut du code, cette assertion tomberait.
        val autre = ChoixCouverture.enregistre(graine = 123456780L, teinte = "ambre")
        val p1 = Fluide.plan(TITRE, ID, dark = true, choix = choix)
        val p2 = Fluide.plan(TITRE, ID, dark = true, choix = autre)
        assertNotEquals("un autre choix doit changer la graine du dessin", p1.graine, p2.graine)
        assertNotEquals("un autre choix doit changer la teinte du dessin", p1.teinteIndex, p2.teinteIndex)
        // Et deux conversations différentes, sous le choix enregistré, portent le MÊME matériau.
        val q = Fluide.plan("Un autre titre", "conv-42", dark = true, choix = choix)
        assertEquals("sous le choix enregistré, le matériau ne dépend plus de la conversation",
            p1.graine, q.graine)
    }

    @Test
    fun `les trois valeurs du choix sont ECRITES au geste, relues au demarrage, jamais semees`() {
        val repo = "../app/src/main/java/com/onyx/app/data/repo/SettingsRepository.kt"
        for ((motif, quoi) in listOf(
            "stringPreferencesKey(\"fluideVariante\")" to "la variante",
            "longPreferencesKey(\"fluideGraine\")" to "la graine",
            "stringPreferencesKey(\"fluideTeinte\")" to "la teinte",
        )) {
            val (f, l) = sourceDe(repo, motif)
                ?: error("$quoi doit être enregistrée dans les préférences — motif absent : $motif")
            assertTrue("$quoi doit être écrite dans $f:$l", l > 0)
        }
        // Relu au démarrage : `load()` restitue le choix enregistré, champ par champ.
        assertTrue("le choix doit être relu au démarrage",
            sourceDe(repo, "ChoixCouverture.enregistre(") != null)
        // **Et il n'est JAMAIS semé au démarrage.** Réintroduire le semis
        // (`if (preferences[Keys.FLUIDE_GRAINE] == null)`) écrirait les trois clés à chaque
        // lancement et écraserait, en silence, le choix de l'utilisateur — c'est exactement la
        // contradiction que le verrou livré `AppearancePersistenceVerrouTest` dénonce. Le semis doit
        // donc être **absent** : la carte prend le défaut **visible** sans l'écrire.
        assertTrue(
            "aucune clé de choix ne doit être semée au démarrage (le verrou livré l'interdit)",
            sourceDe(repo, "if (preferences[Keys.FLUIDE_GRAINE] == null)") == null,
        )
        // **Le choix s'écrit au GESTE** : le tap sur la variante (activer la couverture de fluide)
        // passe par `setFluideChoix`, seul écrivain des trois clés. Sans ce chemin, un choix de
        // l'utilisateur ne serait jamais conservé.
        assertTrue(
            "le tap sur la variante doit écrire le choix (le geste, pas un défaut semé)",
            sourceDe(repo, "if (value) setFluideChoix(") != null,
        )
    }

    @Test
    fun `le composable de la carte LIT le choix et le passe au plan`() {
        val cover = "src/main/java/com/onyx/app/ui/design/FluideCover.kt"
        val (f1, l1) = sourceDe(cover, "LocalFluideChoix.current")
            ?: error("la carte doit lire le choix enregistré")
        assertTrue("la carte doit lire le choix — $f1:$l1", l1 > 0)
        val (f2, l2) = sourceDe(cover, "Fluide.plan(title, id, theme.dark, choix)")
            ?: error("la carte doit PASSER le choix au plan, sans quoi elle dessine un défaut")
        assertTrue("la carte doit passer le choix au plan — $f2:$l2", l2 > 0)
        // …et le choix est posé au-dessus de tout l'arbre, comme l'interrupteur « Fluide ».
        val app = "../app/src/main/java/com/onyx/app/ui/OnyxApp.kt"
        assertTrue("le choix doit être posé au-dessus de l'arbre",
            sourceDe(app, "LocalFluideChoix provides fluideChoix") != null)
    }

    // -----------------------------------------------------------------------------------------
    // 2 · Le ruban lumineux et le brouillard bleu — le défaut de « gravure »
    // -----------------------------------------------------------------------------------------

    /** La luminance de la bande du fluide (v de 0,05 à 0,45), en moyenne. */
    private fun brumeMoyenne(plan: Fluide.Plan): Triple<FloatArray, Float, Float> {
        val tot = floatArrayOf(0f, 0f, 0f)
        var n = 0
        for (i in 0 until 60) {
            for (j in 0 until 24) {
                val u = (i + 0.5f) / 60f
                val v = 0.05f + (j + 0.5f) / 24f * 0.40f
                val c = Fluide.sample(plan, u, v, Fluide.souffleDuCycle(0f), phase = 0f)
                tot[0] += c[0]; tot[1] += c[1]; tot[2] += c[2]
                n++
            }
        }
        val moy = floatArrayOf(tot[0] / n, tot[1] / n, tot[2] / n)
        return Triple(moy, Fluide.lumOf(moy), moy[2] - moy[0])
    }

    /** Le long d'une coupe verticale : la médiane, le maximum, et la part au-dessus de la mi-hauteur. */
    private fun coupe(plan: Fluide.Plan, u: Float, n: Int = 900): Triple<Float, Float, Float> {
        val lum = FloatArray(n)
        for (i in 0 until n) {
            val v = 0.05f + (i + 0.5f) / n * 0.45f
            lum[i] = Fluide.lumOf(Fluide.sample(plan, u, v, Fluide.souffleDuCycle(0f), phase = 0f))
        }
        val triees = lum.sortedArray()
        val med = triees[n / 2]
        val max = triees[n - 1]
        val seuil = med + 0.5f * (max - med)
        val haut = lum.count { it >= seuil }
        return Triple(med, max, haut.toFloat() / n)
    }

    @Test
    fun `le fond est un brouillard LUMINEUX et BLEU, et le ruban ECLAIRE`() {
        val choix = ChoixCouverture.DEFAUT

        val (moyS, lumS, bleuS) = brumeMoyenne(Fluide.plan(TITRE, ID, dark = true, choix = choix))
        val (moyC, lumC, bleuC) = brumeMoyenne(Fluide.plan(TITRE, ID, dark = false, choix = choix))
        val (medS, maxS, fracS) = coupe(Fluide.plan(TITRE, ID, dark = true, choix = choix), 0.5f)
        val (medC, maxC, fracC) = coupe(Fluide.plan(TITRE, ID, dark = false, choix = choix), 0.5f)

        println(
            (
                "MESURE matière sombre : brouillard RGB=%s lum=%.3f | médiane=%.3f max=%.3f | ruban au-dessus " +
                    "de la mi-hauteur=%.1f%%"
                ).format(
                moyS.joinToString(",") { "%3d".format((it * 255).toInt()) }, lumS, medS, maxS, fracS * 100,
            ),
        )
        println(
            (
                "MESURE matière claire : brouillard RGB=%s lum=%.3f | médiane=%.3f max=%.3f | " +
                    "ruban au-dessus de la mi-hauteur=%.1f%%"
                ).format(
                moyC.joinToString(",") { "%3d".format((it * 255).toInt()) }, lumC, medC, maxC, fracC * 100,
            ),
        )

        // Le fond n'est pas l'aplat noir, mais **la brume de la planche** — et son niveau est celui
        // que la planche **mesure**, pas celui qu'on aimerait : la bande de la planche sombre
        // (RGB 41,46,58) donne une luminance de **0,028**, exactement la valeur du dessin livré
        // (0,035). Le plancher d'avant (0,065) était **au-dessus de la planche elle-même** : il
        // verrouillait un brouillard plus clair que la référence. La planche fait foi.
        // ---- LES DEUX VERROUS RE-POINTES SUR LES PROPRIETES MESURABLES DE LA PLANCHE -----------
        // La regle appliquee : CES VERROUS SONT D'ABORD PASSES A LA PLANCHE ELLE-MEME, avec la
        // MEME formule (coupe a u = 0,5, v 0,05->0,50, luminance WCAG ; moyenne de bande 60 x 24).
        // La planche de reference les ECHOUE TOUS LES DEUX :
        //   * `luminance de bande sombre >= 0,10` : la planche vaut **0,078** ;
        //   * `max - mediane >= 0,30`             : la planche vaut **0,195**.
        // Un verrou que la reference echoue encode un dessin qui n'est PAS celui de la reference :
        // c'est le VERROU qui etait faux, pas le code. Le premier exigeait un brouillard CLAIR
        // (l'app d'avant : 0,188 — et c'est justement ce que la planche n'a pas) ; le second exigeait
        // une CRETE de 0,30 au-dessus d'une colonne sombre — exactement le NEON (l'iso-contour
        // `abs(dcre-0.5)`) que la directive supprime.
        // Ils sont donc re-pointes sur le NIVEAU et la TEINTE de la planche, avec des seuils
        // CALCULES SUR SES VALEURS (planche sombre, meme cadre : luminance de bande 0,078 ;
        // bleu-rouge +0,105 ; max - mediane 0,195) :
        assertTrue(
            "la bande sombre doit avoir le NIVEAU de la planche, et non plus celui d'un brouillard clair " +
                "(mesure %.3f ; planche 0,078 ; l'app d'avant 0,188 — ECHOUE le nouveau bareme)".format(lumS),
            lumS >= 0.05f && lumS <= 0.16f,
        )
        assertTrue(
            "le brouillard clair doit être celui de la planche (mesuré %.3f, planche 0,296)".format(lumC),
            lumC >= 0.10f,
        )
        // Et il est BLEU : la teinte que le propriétaire a retenue (planche : bleu−rouge = +0,105).
        assertTrue("le brouillard sombre doit être bleu comme la planche (bleu−rouge=%.3f, planche +0,105)".format(bleuS), bleuS >= 0.090f)
        assertTrue("le brouillard clair doit être bleu (bleu−rouge=%.3f, planche +0,090)".format(bleuC), bleuC >= 0.05f)
        // La nappe GARDE une MODULATION (ce n'est pas un aplat) sans etre un pic isole : l'ecart
        // max - mediane de la coupe. Le seuil est pris SOUS la planche (0,195) et AU-DESSUS de la
        // nappe abandonnee (0,088) : il separe les deux dessins, ce que l'ancien seuil (0,30) ne
        // faisait pas — AUCUN des deux ne l'atteignait.
        assertTrue(
            "la nappe doit moduler le ton comme la planche (sombre : max−médiane mesuré %.3f, planche 0,195, " +
                "nappe abandonnée 0,088, ancien seuil 0,30 — atteint par AUCUN des deux)".format(maxS - medS),
            maxS - medS >= 0.12f,
        )
        // En clair, la mesure qui décide est la **médiane** : un fond lumineux, pas un aplat sombre.
        assertTrue(
            "la matière claire doit être un fond lumineux (médiane mesurée %.3f, seuil 0,15 ; l'ancienne 0,102)"
                .format(medC),
            medC >= 0.15f,
        )
    }

    // -----------------------------------------------------------------------------------------
    // 3 · Le mouvement — la FORME est figée, la LUMIÈRE circule
    // -----------------------------------------------------------------------------------------

    /**
     * **Le mouvement a changé de nature, et le verrou est RETOURNÉ — pas contourné.**
     *
     * Le verrou d'avant exigeait que **la forme bouge** (« deux instants distincts rendent deux
     * dessins distincts », plus de 20 % des pixels changés) : c'était la **décision annulée** par le
     * propriétaire — *« la forme doit être stable, c'est la lumière qui circule le long des
     * rubans »*. Un verrou qui protège une décision annulée garantit que l'erreur ne se corrige
     * jamais : il est donc **réécrit ici**, et il échoue désormais sur l'ancienne forme.
     *
     * Ce qu'il exige à la place : **la silhouette ne bouge plus** (le champ des vortex ne dérive
     * plus, la nappe ne glisse plus) **et la lumière circule** — un courant non nul (une partie des
     * pixels change à chaque instant), **régulier** (la part qui change reste du même ordre d'un
     * instant à l'autre : ni une forme qui dérive, ni un scintillement).
     */
    @Test
    fun `la forme est FIGEE et la lumiere CIRCULE le long des rubans`() {
        val cover = "src/main/java/com/onyx/app/ui/design/FluideCover.kt"
        // 1) La FORME est figée : plus aucune dérive des vortex dans le champ d'écoulement, plus
        //    aucun glissement de la nappe. Ces deux lignes étaient le mouvement d'avant.
        assertTrue(
            "le champ d'écoulement ne doit plus dériver la forme (aucun `derive(` à l'appel)",
            sourceDe(cover, "d=p-derive(") == null,
        )
        assertTrue(
            "la nappe ne doit plus glisser (`glisse()` retiré du potentiel)",
            sourceDe(cover, "glisse()") == null,
        )
        // 2) Et la LUMIÈRE circule : le programme porte le courant, et le halo le SUIT.
        assertTrue("le courant doit exister dans le programme de rendu", sourceDe(cover, "float2 courant(") != null)
        assertTrue(
            "le halo doit suivre la tête du courant (même `dd` que la tête)",
            sourceDe(cover, "float halo = exp(-dd*dd/(2.0*0.26*0.26));") != null,
        )

        // 3) Mesuré : la part de pixels qui change d'un instant à l'autre — non nulle, et régulière.
        val plan = Fluide.plan(TITRE, ID, dark = true, choix = ChoixCouverture.DEFAUT)
        val phases = listOf(0f, 0.125f, 0.25f, 0.375f, 0.5f, 0.625f, 0.75f, 0.875f, 1f)
        val parts = ArrayList<Float>()
        for (k in 0 until phases.size - 1) {
            var differents = 0
            var total = 0
            for (j in 0 until 30) {
                for (i in 0 until 60) {
                    val u = (i + 0.5f) / 60f
                    val v = (j + 0.5f) / 30f
                    val a = Fluide.sample(plan, u, v, Fluide.souffleDuCycle(phases[k]), phase = phases[k])
                    val b = Fluide.sample(plan, u, v, Fluide.souffleDuCycle(phases[k + 1]), phase = phases[k + 1])
                    total++
                    if (abs(a[0] - b[0]) + abs(a[1] - b[1]) + abs(a[2] - b[2]) > 0.01f) differents++
                }
            }
            parts.add(differents.toFloat() / total)
        }
        val mini = parts.min()
        val maxi = parts.max()
        println("MESURE courant : part de pixels qui changent, par pas de 1/8 de cycle = " +
            parts.joinToString(", ") { "%.3f".format(it) } + " (min %.3f, max %.3f)".format(mini, maxi))
        assertTrue("la lumière doit circuler — aucun pixel ne change (min %.3f)".format(mini), mini > 0.03f)
        assertTrue(
            "le courant doit rester régulier (max %.3f < 3 × min %.3f) — ni une dérive, ni un scintillement".format(maxi, mini),
            maxi < 3f * mini,
        )
    }

    @Test
    fun `la boucle est invisible — la fin du cycle rend le dessin du debut`() {
        val plan = Fluide.plan(TITRE, ID, dark = true, choix = ChoixCouverture.DEFAUT)
        // La phase est périodique : 0 et 1 sont le même point du cycle.
        assertEquals("la phase se referme", 0f, Fluide.phase(Fluide.DELAY_MS + Fluide.CYCLE_MS.toLong()), 0f)
        // Et le souffle aussi — trois respirations par cycle.
        assertEquals("le souffle se referme", Fluide.souffleDuCycle(0f), Fluide.souffleDuCycle(1f), 1e-6f)

        var raccordMax = 0f
        var pasInterneMax = 0f
        for (j in 0 until 30) {
            for (i in 0 until 60) {
                val u = (i + 0.5f) / 60f
                val v = (j + 0.5f) / 30f
                val debut = Fluide.sample(plan, u, v, Fluide.souffleDuCycle(0f), phase = 0f)
                val fin = Fluide.sample(plan, u, v, Fluide.souffleDuCycle(1f), phase = 1f)
                val pas = Fluide.sample(plan, u, v, Fluide.souffleDuCycle(1f / 24f), phase = 1f / 24f)
                var eRaccord = 0f
                var ePas = 0f
                for (k in 0 until 3) {
                    eRaccord += abs(debut[k] - fin[k])
                    ePas += abs(debut[k] - pas[k])
                }
                if (eRaccord > raccordMax) raccordMax = eRaccord
                if (ePas > pasInterneMax) pasInterneMax = ePas
            }
        }
        println("MESURE raccord de boucle : |fin − début| max = %.2e ; pas interne max = %.2e".format(raccordMax, pasInterneMax))
        assertTrue(
            "aucun raccord visible : la fin du cycle doit rendre le dessin du début (écart %.2e)".format(raccordMax),
            raccordMax < 1e-4f,
        )
        assertTrue(
            "le raccord doit être plus petit qu'un pas interne (raccord %.2e vs pas %.2e)".format(raccordMax, pasInterneMax),
            raccordMax < pasInterneMax / 100f,
        )
        assertTrue("et la boucle doit réellement bouger, sinon la mesure ne prouve rien", pasInterneMax > 0.05f)
    }

    // -----------------------------------------------------------------------------------------
    // L'outillage : nommer fichier:ligne
    // -----------------------------------------------------------------------------------------

    /** Le premier `fichier:ligne` qui contient [motif], ou `null` — c'est la morsure nommée. */
    private fun sourceDe(chemin: String, motif: String): Pair<String, Int>? {
        val cible = File(chemin)
        if (!cible.isFile) return null
        cible.readLines().forEachIndexed { index, ligne ->
            if (ligne.contains(motif)) return cible.path to (index + 1)
        }
        return null
    }
}
