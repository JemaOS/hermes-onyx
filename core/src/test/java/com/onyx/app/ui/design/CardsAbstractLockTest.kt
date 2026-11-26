package com.onyx.app.ui.design

import androidx.compose.ui.graphics.Color
import com.onyx.app.ui.theme.OnyxFonts
import com.onyx.app.ui.theme.OnyxTheme
import com.onyx.app.ui.theme.ResolvedTheme
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le verrou des compositions abstraites de couverture.**
 *
 * Quatre dents, toutes mesurées, et chacune dit **ce qu'elle mord** :
 *
 *  1. **la variation par conversation** — deux cartes ne doivent pas se ressembler. La dent est
 *     pointée **sur le matériau de la planche** dans le même test, et elle y **mord** (c'est la
 *     morsure rouge, avec la valeur nommée) : l'ancienne couverture variait de 1,87 niveau quand le
 *     nouveau dessin varie de 6,08 à 15,78 ;
 *  2. **le titre blanc** — jamais sous 4,5:1, sur la **pire image du cycle** (la lumière respire) et
 *     sur les quatre conversations, dans les **deux apparences** ;
 *  3. **la propreté** — aucun bruit dans le programme : le grain procédural (le pas de trois pixels,
 *     `h11`) et le champ de bruit (`fbm`) de l'ancienne couverture ne doivent **pas** être dans le
 *     programme des compositions ;
 *  4. **la déclaration unique** — le défaut du 31/10 (un `float c` déclaré deux fois, AGSL REFUSE)
 *     ne doit pas revenir : aucun nom local n'est déclaré deux fois dans un programme.
 *
 * **Re-pointé par le lot « blocs-verre » :** aucune carte ne peint plus ce matériau (le propriétaire a
 * refusé les trois compositions et demandé une plaque de verre). Les quatre dents mesurent donc le
 * **matériau lui-même**, et la dent de la carte exige en plus que **plus aucune carte ne le peigne** —
 * sans être désarmée pour autant.
 */
class CardsAbstractLockTest {

    private val conversations = listOf(
        "Réunion de chantier" to "conv-1",
        "Devis toiture" to "conv-2",
        "Planning équipe" to "conv-3",
        "Notes de terrain" to "conv-4",
    )

    /** **Le seuil de la dent de variation**, en niveaux 8 bits moyens par canal. */
    private val VariationMin = 3.0f

    private fun theme(dark: Boolean) =
        ResolvedTheme.resolve(OnyxTheme.STANDARD, dark = dark, fonts = OnyxFonts.System)

    /**
     * **Le voile de pied de la carte** — la copie des trois arrêts de `ConversationCoverCard`
     * (`Color.Transparent` → `0,44` → `0,94`). C'est ce voile qui décide du contraste du titre ; il
     * est recopié ici parce qu'un test ne compose pas un `Box`.
     */
    private fun voile(v: Float): Float =
        if (v <= 0.5f) 0.44f * (v / 0.5f) else 0.44f + 0.50f * ((v - 0.5f) / 0.5f)

    private val NU = 24
    private val NV = 12

    /** La carte d'une conversation, **sur la pire image du cycle**, sous le voile, en 24 × 12 points. */
    private fun carte(choix: ChoixCouverture, titre: String, id: String, dark: Boolean): FloatArray {
        val plan = Fluid.plan(titre, id, dark, choix)
        val out = FloatArray(NU * NV * 3)
        var i = 0
        for (j in 0 until NV) {
            for (k in 0 until NU) {
                val u = (k + 0.5f) / NU
                val v = (j + 0.5f) / NV
                val c = Fluid.worstSample(plan, u, v)
                val a = voile(v)
                out[i++] = c[0] * (1f - a)
                out[i++] = c[1] * (1f - a)
                out[i++] = c[2] * (1f - a)
            }
        }
        return out
    }

    /** L'écart moyen le plus faible entre deux conversations, en niveaux 8 bits. */
    private fun variationMin(choix: ChoixCouverture, dark: Boolean): Float {
        val cartes = conversations.map { carte(choix, it.first, it.second, dark) }
        var mini = Float.MAX_VALUE
        for (i in cartes.indices) {
            for (j in i + 1 until cartes.size) {
                var s = 0f
                for (k in cartes[i].indices) s += kotlin.math.abs(cartes[i][k] - cartes[j][k])
                mini = minOf(mini, s / cartes[i].size * 255f)
            }
        }
        return mini
    }

    /** Le rapport de contraste du titre blanc, le plus bas de la zone du titre, sur les 4 cartes. */
    private fun titreMin(choix: ChoixCouverture, dark: Boolean): Double {
        var pire = Double.MAX_VALUE
        for ((titre, id) in conversations) {
            val plan = Fluid.plan(titre, id, dark, choix)
            for (j in 12..23) {
                val v = (j + 0.5f) / NV
                for (k in 0 until NU) {
                    val u = (k + 0.5f) / NU
                    val c = Fluid.worstSample(plan, u, v)
                    val a = voile(v)
                    val bg = floatArrayOf(c[0] * (1f - a), c[1] * (1f - a), c[2] * (1f - a))
                    pire = minOf(pire, Fluid.ratio(1f, Fluid.lumOf(bg)))
                }
            }
        }
        return pire
    }

    private val cover = java.io.File("src/main/java/com/onyx/app/ui/design/FluidCover.kt")
        .takeIf { it.isFile }?.readText()
        ?: java.io.File("../core/src/main/java/com/onyx/app/ui/design/FluidCover.kt").readText()

    // =============================================================================================
    // 1 · La variation par conversation — la dent MORD sur l'ancien matériau
    // =============================================================================================

    @Test
    fun `deux conversations ne rendent pas le meme dessin — et l ancien materiau, lui, mordait`() {
        val rapport = StringBuilder()
        var ancien = Float.MAX_VALUE
        var nouveau = Float.MAX_VALUE
        for (dark in listOf(true, false)) {
            val a = variationMin(ChoixCouverture.MATERIAU_PLANCHE, dark)
            val n = variationMin(ChoixCouverture.DEFAUT, dark)
            ancien = minOf(ancien, a)
            nouveau = minOf(nouveau, n)
            rapport.append(
                "  %-6s · ancien matériau (filaments) : %.2f · nouveau (plans) : %.2f · seuil %.1f%n"
                    .format(if (dark) "sombre" else "clair", a, n, VariationMin),
            )
        }
        println("variation entre deux conversations (écart moyen, niveaux 8 bits) :\n$rapport")
        // **LA MORSURe ROUGE** : pointée sur l'ancien matériau, la dent échoue — elle nomme la
        // valeur. C'est ce qui prouve que le seuil n'est pas choisi pour faire passer le nouveau.
        assertTrue(
            "la dent de variation ne mord PAS sur le matériau de la planche (min %.2f ≥ seuil %.1f) : " +
                "elle ne prouve donc rien sur la nouvelle composition.\n%s"
                    .format(ancien, VariationMin, rapport),
            ancien < VariationMin,
        )
        // **VERT APRÈS** : la composition retenue passe.
        assertTrue(
            "deux conversations se ressemblent dans la composition retenue : min %.2f < seuil %.1f\n%s"
                .format(nouveau, VariationMin, rapport),
            nouveau >= VariationMin,
        )
    }

    // =============================================================================================
    // 2 · Le titre blanc posé sur la couverture — 4,5:1, sur la pire image, les deux apparences
    // =============================================================================================

    @Test
    fun `le titre blanc tient 4,5 sur la couverture, sur la pire image du cycle, dans les deux apparences`() {
        val rapport = StringBuilder()
        var fautes = 0
        for (dark in listOf(true, false)) {
            for ((nom, choix) in listOf(
                "planche (filaments)" to ChoixCouverture.MATERIAU_PLANCHE,
                "plans" to ChoixCouverture.VARIANTE_PLANS.let { ChoixCouverture(variante = it) },
                "volume" to ChoixCouverture.VARIANTE_VOLUME.let { ChoixCouverture(variante = it) },
                "trame" to ChoixCouverture.VARIANTE_TRAME.let { ChoixCouverture(variante = it) },
            )) {
                val r = titreMin(choix, dark)
                rapport.append(
                    "  %-8s %-20s titre blanc %.2f:1 (min 4,5)%n"
                        .format(if (dark) "sombre" else "clair", nom, r),
                )
                if (r < OnyxContrast.TEXT_MIN) fautes++
            }
        }
        println("contraste du titre sur la couverture (« pire image » du cycle) :\n$rapport")
        assertTrue(
            "le titre blanc passe sous 4,5:1 sur une couverture :\n" +
                rapport.lineSequence().filter { line ->
                    val v = Regex("(\\d+\\.\\d+):1").find(line)?.groupValues?.get(1)?.toDoubleOrNull()
                    v != null && v < OnyxContrast.TEXT_MIN
                }.joinToString("\n"),
            fautes == 0,
        )
    }

    // =============================================================================================
    // 3 · La propreté — aucun bruit, aucun grain, dans le programme comme dans le calcul
    // =============================================================================================

    @Test
    fun `le programme des compositions n a ni grain ni champ de bruit`() {
        val debut = cover.indexOf("FLUIDE_AGSL_ABSTRAIT = \"\"\"")
        assertTrue("le programme des compositions a disparu du fichier", debut > 0)
        val source = cover.substring(debut, cover.indexOf("\n\"\"\"", debut))
        val fautes = listOf("h11(", "fbm(", "GRAIN").filter { source.contains(it) }
        println("netteté : le programme abstrait mesure ${source.length} caractères, fautes=$fautes")
        assertTrue(
            "le programme des compositions porte encore du bruit (un nuage, un grain) : $fautes — " +
                "c'est exactement la matière que le propriétaire a jugée sale",
            fautes.isEmpty(),
        )
    }

    // =============================================================================================
    // 4 · Le défaut du 31/10 (AGSL REFUSE) ne revient pas : un nom local déclaré une seule fois
    // =============================================================================================

    @Test
    fun `aucun nom local n est declare deux fois dans un programme AGSL`() {
        val rapport = StringBuilder()
        var fautes = 0
        for (marqueur in listOf("FLUIDE_AGSL_ABSTRAIT = \"\"\"")) {
            val debut = cover.indexOf(marqueur)
            if (debut < 0) continue
            val source = cover.substring(debut, cover.indexOf("\n\"\"\"", debut))
            val noms = Regex("(?m)^\\s*(?:float|float2|float3|float4|half4|int)\\s+([A-Za-z_]\\w*)\\s*=")
                .findAll(source).map { it.groupValues[1] }.toList()
            val doubles = noms.groupingBy { it }.eachCount().filter { it.value > 1 }.keys
            rapport.append("  %-24s %d déclarations, doublons %s%n".format(marqueur, noms.size, doubles))
            if (doubles.isNotEmpty()) fautes++
        }
        println("déclarations uniques par programme :\n$rapport")
        assertTrue(
            "un programme AGSL déclare deux fois le même nom (le défaut du 31/10 — `float c` en " +
                "double, AGSL REFUSE) :\n$rapport",
            fautes == 0,
        )
    }

    @Test
    fun `la composition n est plus peinte par une carte, et le materiau reste entier`() {
        // =========================================================================================
        // **VERROU RE-POINTÉ — lot « blocs-verre ».**
        //
        // Cette dent exigeait que la CARTE montre le dessin abstrait (le chemin `FluideConversationCover`
        // → `Fluid.plan` → programme AGSL). Le propriétaire a refusé les trois compositions et
        // demandé une plaque de verre : ce qui reste exigé est donc que le **matériau** soit entier
        // (les douze réglages, le programme sondé) **et** qu'aucune carte ne le peigne. La dent ne se
        // désarme pas : elle change de cible, et elle mesure les deux bouts.
        // =========================================================================================
        val plan = Fluid.plan("Réunion de chantier", "conv-1", dark = true, choix = ChoixCouverture.DEFAUT)
        assertTrue(
            "le plan du défaut ne porte pas de composition : le matériau de la planche est amputé",
            plan.composition == Fluid.Composition.PLANS,
        )
        assertTrue("les douze réglages de la composition manquent", plan.reglages.size == 12)
        // Deux conversations, deux réglages différents : la graine du champ est bien celle de la
        // conversation, et non celle de la planche.
        val autre = Fluid.plan("Devis toiture", "conv-2", dark = true, choix = ChoixCouverture.DEFAUT)
        assertTrue(
            "deux conversations portent les mêmes réglages : le matériau ne diffère plus",
            plan.reglages.indices.any { kotlin.math.abs(plan.reglages[it] - autre.reglages[it]) > 1e-4f },
        )
        assertTrue(
            "les deux programmes AGSL ne sont pas sondés à la compilation (AGSL REFUSE resterait muet)",
            cover.contains("RuntimeShader(FLUIDE_AGSL)") && cover.contains("RuntimeShader(FLUIDE_AGSL_ABSTRAIT)"),
        )
        // **Et plus AUCUNE carte ne le peint** : le fichier de la carte ne cite plus la couverture.
        val galerie = java.io.File("src/main/java/com/onyx/app/ui/design/Gallery.kt")
            .takeIf { it.isFile } ?: java.io.File("../core/src/main/java/com/onyx/app/ui/design/Gallery.kt")
        val carte = galerie.readText()
        val appels = listOf("FluideConversationCover(", "ConversationCover(", "ConversationCoverCard(")
            .filter { carte.contains(it) }
        assertTrue(
            "la carte peint encore la couverture générative : $appels — elle doit être une plaque de verre",
            appels.isEmpty(),
        )
    }
}
