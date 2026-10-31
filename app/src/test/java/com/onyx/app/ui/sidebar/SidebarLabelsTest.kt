package com.onyx.app.ui.sidebar

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onyx.app.ui.design.OnyxSocle
import com.onyx.app.ui.design.OnyxSpace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Une tuile ne coupe jamais un mot**, et les puces ne se chevauchent pas — la géométrie comparée.
 *
 * Le défaut du propriétaire, sur un téléphone étroit et à la police du système 1,3× : la tuile
 * « Compétences » s'écrivait « Compétence » puis « s » ; « Fichiers du serveur » se repliait en deux
 * lignes bancales ; la puce de projet se faisait couper par le rond du « + » ; la dernière pastille
 * de filtre sortait par le bord de l'écran.
 *
 * Les largeurs de mots sont **relevées sur l'appareil** (arbre d'accessibilité, volet de 340 dp à
 * 420 dpi, libellés d'une seule ligne à la police 1,0×) : « Compétences » 210 px, « Planifiés »
 * 133, « Outils » 89, « Modèles » 131, « Kanban » 114, « Code » 78, « Aperçus » 127 — soit 80,0 /
 * 50,7 / 33,9 / 49,9 / 43,4 / 29,7 / 48,4 **dp** à 13 sp. Les mots d'un libellé de plusieurs mots
 * (« Fichiers du serveur ») ne se lisent pas dans l'arbre — un libellé replié publie la largeur qui
 * lui est donnée, pas celle de ses lignes —, ils prennent donc le rapport **le plus large constaté**
 * (7,4 dp par caractère), et l'espace vaut 7 dp : le modèle recompose chaque texte mot à mot, il ne
 * mesure jamais une chaîne entière « au jugé ».
 */
class SidebarLabelsTest {

    /**
     * Les largeurs de mots **relevées sur l'appareil**, en dp, au **corps du libellé de tuile
     * (12 sp)** et à la police 1,0×. Les six tuiles dessinées viennent de l'arbre d'accessibilité
     * (volet de 340 dp, 420 dpi, build `1.0.0+700`, police 1,3× → largeur relevée ÷ 1,3) ; les mots
     * du vocabulaire qui n'est plus dessiné (groupe ONYX) viennent de l'ancien relevé à 13 sp,
     * ramené par 12/13. Un mot non relevé prend le rapport le plus large constaté (**6,8 dp par
     * caractère**, à 12 sp) : le modèle recompose chaque texte mot à mot, il ne mesure jamais une
     * chaîne entière au jugé.
     */
    private val ink = mapOf(
        // Relevés du build 1.0.0+700, 12 sp, 1,0×.
        "Planifiés" to 45.4f, "Aperçus" to 43.4f, "Capacités" to 52.2f,
        "Greffons" to 45.7f, "Configuration" to 71.2f, "Archives" to 45.7f,
        // Ancien relevé à 13 sp, ramené à 12 sp (× 12/13).
        "Compétences" to 73.8f, "Outils" to 31.3f, "Modèles" to 46.1f,
        "Kanban" to 40.1f, "Code" to 27.4f,
        // Modèle conservateur, à 12 sp (6,8 dp par caractère).
        "Fichiers" to 54.6f, "du" to 13.7f, "serveur" to 47.8f,
    )

    private val space = 7f

    /**
     * Le corps du libellé de tuile : **12 sp** (`.mt`, la méta du socle). Les largeurs de [ink] sont
     * relevées sur l'appareil à ce corps — voir la table plus haut.
     */
    private val baseSp = 12f

    /** La largeur d'un texte, **mot à mot**, au corps de la tuile et à l'échelle de police demandée. */
    private fun measure(scale: Float): (String) -> Float = { text ->
        val words = text.trim().split(" ").filter { it.isNotEmpty() }
        (words.sumOf { (ink[it] ?: (it.length * 6.8f)).toDouble() } * scale +
            (words.size - 1) * space * scale).toFloat()
    }

    /** Les libellés du volet, tels qu'ils s'écrivent en français. */
    private val labels = listOf(
        "Planifiés", "Compétences", "Outils", "Fichiers du serveur",
        "Modèles", "Kanban", "Code", "Aperçus",
    )

    private fun panel(narrow: Boolean): Float = if (narrow) 309.6f else 340f

    private fun available(narrow: Boolean): Float = tileLabelWidth(panel(narrow).dp).value

    @Test
    fun `la largeur du libelle est celle que la tuile a vraiment`() {
        // Volet de 340 dp (téléphone de 411 dp) → libellé de 102 dp, ce que l'arbre rend à 266 px
        // à 420 dpi (2,625 px/dp) : 102 × 2,625 = 267,75 px, à 2 px près.
        assertEquals(102f, tileLabelWidth(340.dp).value, 0.01f)
        assertEquals(266f, tileLabelWidth(340.dp).value * 2.625f, 2f)
        // Volet de 309,6 dp (téléphone de 360 dp) : la place manque de 15 dp.
        assertEquals(86.8f, tileLabelWidth(309.6.dp).value, 0.05f)
        // Un compteur mange la place du libellé : elle est comptée, pas ignorée. La réserve est
        // **calculée pour le pire cas réel** (« 999 », trois chiffres), et non plus une largeur
        // écrite à la main : 150 dp de tuile − 24 (rembourrages) − 16 (icône) − 8 (écart) − 8 (écart
        // icône-libellé) − **29,8** (la pastille de trois chiffres : 3 × 0,60 × 11 sp + 2 × 5 dp) =
        // **64,2 dp**. Le chiffre est celui du code (`BadgeGeometry.reserveDp`), pas un relevé.
        assertEquals(64.2f, tileLabelWidth(340.dp, badgeDigits = 3).value, 0.05f)
        // Un volet minuscule ne rend pas une largeur négative.
        assertEquals(0f, tileLabelWidth(40.dp).value, 0.01f)
    }

    @Test
    fun `aucun libelle de tuile ne coupe un mot, de 1,0x a 1,3x`() {
        val report = StringBuilder("format · police · libellé · lignes · taille\n")
        var failures = 0
        for (narrow in listOf(false, true)) {
            val width = available(narrow)
            for (scale in listOf(1.0f, 1.3f)) {
                labels.forEach { label ->
                    val plan = tileLabelPlan(label, measure(scale), width)
                    val whole = plan.lines.joinToString(" ") == label
                    val fits = plan.lines.all { measure(scale)(it) * plan.fontRatio <= width + 0.01f }
                    val two = plan.lines.size <= 2
                    val ratio = plan.fontRatio in TILE_LABEL_MIN_RATIO..1f
                    val ok = whole && fits && two && ratio && plan.lines.isNotEmpty()
                    if (!ok) failures++
                    report.append(
                        "${if (narrow) "360 dp" else "411 dp"} · ${scale}× · $label · " +
                            "${plan.lines.joinToString(" / ")} · ${"%.2f".format(plan.fontRatio)} " +
                            "${if (ok) "ok" else "DÉFAUT"}\n",
                    )
                }
            }
        }
        println(report)
        assertTrue(
            "des libellés de tuile se coupent ou débordent :\n" +
                report.lineSequence().filter { it.contains("DÉFAUT") }.joinToString("\n"),
            failures == 0,
        )
    }

    @Test
    fun `Competences tient sur une ligne a la police du systeme 1,3x sur un telephone etroit`() {
        // Le cas exact du propriétaire : 80 dp à 1,0× → 104 dp à 1,3×, pour 86,8 dp de place. Sans
        // la mesure, le moteur de texte coupe le mot (« Compétenc » puis « es » — relevé au pixel sur
        // l'appareil) ; avec le plan, la taille cède et le mot reste entier.
        val plan = tileLabelPlan("Compétences", measure(1.3f), available(narrow = true))
        assertEquals(1, plan.lines.size)
        assertEquals("Compétences", plan.lines.single())
        assertTrue("la taille ne doit pas s'effondrer", plan.fontRatio >= TILE_LABEL_MIN_RATIO)
        assertTrue("la taille doit céder un peu", plan.fontRatio < 1f)
        assertTrue(measure(1.3f)("Compétences") * plan.fontRatio <= available(narrow = true) + 0.01f)
        // Et le même libellé, sur un volet large à 1,0×, ne rétrécit pas d'un pouce.
        val large = tileLabelPlan("Compétences", measure(1.0f), available(false))
        assertEquals(1f, large.fontRatio, 0.0001f)
        assertEquals(listOf("Compétences"), large.lines)
    }

    @Test
    fun `Fichiers du serveur se coupe aux espaces, et ses deux lignes tiennent`() {
        val width = available(narrow = false)
        val plan = tileLabelPlan("Fichiers du serveur", measure(1.0f), width)
        assertEquals(listOf("Fichiers", "du serveur"), plan.lines)
        assertTrue(plan.lines.all { measure(1.0f)(it) * plan.fontRatio <= width + 0.01f })
        // La coupe la plus équilibrée possible : les deux lignes se ressemblent plus que sous la
        // coupe gloutonne (« Fichiers du » / « serveur », 105,8 contre 51,8).
        val left = measure(1.0f)(plan.lines[0])
        val right = measure(1.0f)(plan.lines[1])
        assertTrue("les deux lignes sont déséquilibrées ($left / $right)", kotlin.math.abs(left - right) < 20f)
    }

    @Test
    fun `un libelle long perd de la taille plutot que de casser un mot`() {
        // Le même libellé sur le volet étroit à 1,3× : deux lignes, et la taille cède juste ce qu'il
        // faut pour que « du serveur » tienne — 95,6 dp pour 86,8 dp de place.
        val width = available(narrow = true)
        val plan = tileLabelPlan("Fichiers du serveur", measure(1.3f), width)
        assertEquals(listOf("Fichiers", "du serveur"), plan.lines)
        assertTrue(plan.fontRatio in TILE_LABEL_MIN_RATIO..1f)
        assertTrue(measure(1.3f)("du serveur") > width)
        assertTrue(measure(1.3f)("du serveur") * plan.fontRatio <= width + 0.01f)
    }

    @Test
    fun `le plan ne perd jamais un mot, quelle que soit la largeur`() {
        // Un invariant, pas un exemple : pour toute largeur, les lignes recomposent le libellé
        // exactement — aucun mot perdu, aucun mot coupé, et jamais plus de lignes que de mots.
        for (width in listOf(10f, 20f, 40f, 60f, 80f, 100f, 140f, 200f, 320f, 480f)) {
            for (scale in listOf(0.8f, 1f, 1.3f, 2f)) {
                labels.forEach { label ->
                    val plan = tileLabelPlan(label, measure(scale), width)
                    assertEquals(
                        "« $label » perd un mot à $width dp (×$scale)",
                        label,
                        plan.lines.joinToString(" "),
                    )
                    assertEquals(
                        "« $label » lâche un mot d'un bloc à $width dp",
                        label.split(" ").sorted(),
                        plan.lines.flatMap { it.split(" ") }.sorted(),
                    )
                    assertTrue(
                        "« $label » dépasse ses mots en lignes à $width dp",
                        plan.lines.size <= label.split(" ").size,
                    )
                    assertTrue(
                        "« $label » descend sous le plancher de taille à $width dp",
                        plan.fontRatio >= TILE_LABEL_MIN_RATIO,
                    )
                }
            }
        }
    }


    /**
     * **À 1,3× de police : combien de tuiles étaient illisibles ou tronquées, et combien le sont.**
     *
     * Deux règles s'affrontent, et c'est la seconde qui gagne :
     *
     *  * **avant** — corps 13 sp, rapport plancher 0,78, aucune borne basse : « Compétences » se
     *    rangeait à **10,1 sp**, sous le plancher de lisibilité de l'app, et en deux colonnes ;
     *  * **après** — corps 12 sp, même rapport, mais la taille est **ramenée sur l'échelle** et
     *    jamais sous **11 sp** (`OnyxSocle.snap`) ; et si le mot ne tient plus à 11 sp, c'est la
     *    **grille** qui cède : une colonne (`tileColumns`), pas le corps.
     *
     * Le compte est imprimé : c'est lui que le rapport cite.
     */
    @Test
    fun `a 1,3x, aucune tuile ne descend sous 11 sp ni ne tronque un mot`() {
        val report = StringBuilder("volet · police · colonnes · libellé · avant · après · verdict\n")
        var avantIllisible = 0
        var avantCoupe = 0
        var apresIllisible = 0
        var apresCoupe = 0
        for (panel in listOf(340f, 309.6f)) {
            val grid = (panel - TileGridInset.value * 2).dp
            for (scale in listOf(1.0f, 1.3f)) {
                // AVANT : deux colonnes, corps 13 sp, plancher 0,78 — sans borne de lisibilité.
                val widthBefore = tileLabelWidthIn(grid, columns = 2).value
                // APRÈS : le nombre de colonnes suit la police ; le corps est celui du socle.
                val columns = tileColumns(scale)
                val widthAfter = tileLabelWidthIn(grid, columns = columns).value
                labels.forEach { label ->
                    val w13: (String) -> Float = { t -> measure(scale)(t) * 13f / 12f }
                    val planBefore = tileLabelPlan(label, w13, widthBefore)
                    val sizeBefore = 13f * planBefore.fontRatio
                    val fitBefore = planBefore.lines.all { w13(it) * planBefore.fontRatio <= widthBefore + 0.01f }
                    if (sizeBefore < 11f) avantIllisible++
                    if (!fitBefore) avantCoupe++

                    val planAfter = tileLabelPlan(label, measure(scale), widthAfter)
                    val sizeAfter = OnyxSocle.snap((baseSp * planAfter.fontRatio).sp).value
                    val fitAfter = planAfter.lines.all {
                        measure(scale)(it) * (sizeAfter / baseSp) <= widthAfter + 0.01f
                    }
                    if (sizeAfter < 11f) apresIllisible++
                    if (!fitAfter) apresCoupe++
                    report.append(
                        "%.0f · %.1f× · %d · %s · %.1f sp%s · %.1f sp%s · %s\n".format(
                            panel, scale, columns, label,
                            sizeBefore, if (fitBefore) "" else " (coupé)",
                            sizeAfter, if (fitAfter) "" else " (coupé)",
                            when {
                                !fitAfter -> "DÉFAUT — coupé"
                                sizeAfter < 11f -> "DÉFAUT — illisible"
                                !fitBefore || sizeBefore < 11f -> "corrigé"
                                else -> "ok"
                            },
                        ),
                    )
                }
            }
        }
        println(report)
        println("COMPTE — illisible (< 11 sp) : avant $avantIllisible → après $apresIllisible ; " +
            "coupé : avant $avantCoupe → après $apresCoupe")
        assertEquals("des tuiles descendent sous 11 sp après correction :\n" + report, 0, apresIllisible)
        assertEquals("des tuiles tronquent un mot après correction :\n" + report, 0, apresCoupe)
        // Et la correction n'est pas un tour de passe-passe : l'ancienne règle, elle, fautait.
        assertTrue("l'ancienne règle devait faillir — sinon ce test ne prouve rien", avantIllisible + avantCoupe > 0)
    }


    /** Les six tuiles **dessinées** dans le volet (groupe REFERENCE) — ce que l'écran montre. */
    private val drawn = listOf("Planifiés", "Aperçus", "Capacités", "Greffons", "Configuration", "Archives")

    /**
     * **À 1,3× de police : combien de libellés de tuile étaient sous le plancher ou coupés.**
     *
     * Deux règles s'affrontent, et c'est la seconde qui gagne :
     *
     *  * **avant** — corps 13 sp, rapport plancher 0,78, aucune borne basse : un libellé étroit
     *    descendait à **10,1 sp**, sous le plancher de lisibilité de l'app ;
     *  * **après** — corps 12 sp, même rapport, mais la taille est **ramenée sur l'échelle** et
     *    jamais sous **11 sp** (`OnyxSocle.snap`). Si un mot ne tient pas à 11 sp, il est **tronqué
     *    avec une ellipse** — indiqué — plutôt que peint à 10 sp : une taille ne se négocie pas.
     *
     * Le compte est imprimé, et c'est lui que le rapport cite.
     */
    @Test
    fun `a 1,3x, aucun libelle de tuile ne descend sous 11 sp`() {
        val report = StringBuilder("volet · police · libellé · avant · après · verdict\n")
        var avantIllisible = 0
        var avantCoupe = 0
        var apresIllisible = 0
        var apresCoupe = 0
        for (panel in listOf(340f, 309.6f)) {
            val grid = (panel - TileGridInset.value * 2).dp
            val width = tileLabelWidthIn(grid, columns = 2).value
            for (scale in listOf(1.0f, 1.3f)) {
                drawn.forEach { label ->
                    // AVANT : corps 13 sp, aucune borne de lisibilité.
                    val w13: (String) -> Float = { t -> measure(scale)(t) * 13f / 12f }
                    val planBefore = tileLabelPlan(label, w13, width)
                    val sizeBefore = 13f * planBefore.fontRatio
                    val fitBefore = planBefore.lines.all { w13(it) * planBefore.fontRatio <= width + 0.01f }
                    if (sizeBefore < 11f) avantIllisible++
                    if (!fitBefore) avantCoupe++

                    // APRÈS : corps 12 sp, taille ramenée sur l'échelle, jamais sous 11 sp.
                    val planAfter = tileLabelPlan(label, measure(scale), width)
                    val sizeAfter = OnyxSocle.snap((baseSp * planAfter.fontRatio).sp).value
                    val fitAfter = planAfter.lines.all {
                        measure(scale)(it) * (sizeAfter / baseSp) <= width + 0.01f
                    }
                    if (sizeAfter < 11f) apresIllisible++
                    if (!fitAfter) apresCoupe++
                    report.append(
                        "%.0f · %.1f× · %s · %.1f sp%s · %.1f sp%s · %s\n".format(
                            panel, scale, label,
                            sizeBefore, if (fitBefore) "" else " (coupé)",
                            sizeAfter, if (fitAfter) "" else " (ellipse)",
                            when {
                                !fitAfter -> "tronqué, indiqué"
                                sizeAfter < 11f -> "DÉFAUT — illisible"
                                !fitBefore || sizeBefore < 11f -> "corrigé"
                                else -> "ok"
                            },
                        ),
                    )
                }
            }
        }
        println(report)
        println(
            "COMPTE — sous 11 sp : avant $avantIllisible → après $apresIllisible ; " +
                "coupé sans indication : avant $avantCoupe → après $apresCoupe",
        )
        assertEquals("des libellés de tuile descendent sous 11 sp :\n" + report, 0, apresIllisible)
    }

    @Test
    fun `un mot trop long est tronque avec une ellipse, jamais rapetisse`() {
        // « Compétences » n'est pas dessiné dans le volet d'aujourd'hui (groupe REFERENCE) : il est
        // le plus long mot du vocabulaire des tuiles. Sur un volet de 360 dp à 1,3×, il demande 96 dp
        // à 11 sp pour 86,8 dp de place — aucune taille licite ne le fait tenir, et on ne descend
        // pas sous le plancher : la ligne porte alors une **ellipse** (`overflow = TextOverflow
        // .Ellipsis` dans `SidebarTileCell`), c'est-à-dire une troncature **indiquée**, pas un mot
        // coupé au milieu.
        val width = tileLabelWidthIn((309.6f - TileGridInset.value * 2).dp, columns = 2).value
        val plan = tileLabelPlan("Compétences", measure(1.3f), width)
        val size = OnyxSocle.snap((baseSp * plan.fontRatio).sp).value
        assertTrue("le mot garde au moins le plancher de 11 sp", size >= 11f)
        assertTrue("et il ne se plie pas en deux : c'est un mot", plan.lines.size == 1)
        val short = tileLabelPlan("Archives", measure(1.3f), width)
        assertEquals(
            "le mot court, lui, tient entier",
            12f,
            OnyxSocle.snap((baseSp * short.fontRatio).sp).value,
            0.001f,
        )
    }

    @Test
    fun `la puce de projet et le bouton d ajout ne se recouvrent pas`() {
        // 44 dp de bouton (la cible de la maison) + le filet d'air de la maison, sur les quatre
        // largeurs de volet que l'app produit.
        val button = 44f
        val gutter = ProjectChipsGutter.value
        for (panel in listOf(280f, 309.6f, 340f, 345f)) {
            val viewport = projectChipsWidth(panel, button, gutter)
            assertTrue(
                "les puces touchent le bouton (volet $panel dp)",
                viewport + gutter + button <= panel + 0.001f,
            )
            assertTrue("la puce peut encore passer sous le bouton", viewport < panel - button)
            assertTrue("le filet d'air doit exister", gutter >= OnyxSpace.Hair.value)
        }
        assertEquals(340f - 44f - 8f, projectChipsWidth(340f, button, gutter), 0.001f)
        // Un volet plus étroit que le bouton ne rend pas une largeur négative.
        assertEquals(0f, projectChipsWidth(40f, button, gutter), 0.001f)
    }

    @Test
    fun `les pastilles de filtre passent a la ligne au lieu de sortir de l'ecran`() {
        // Les trois pastilles, à la police 1,0×, d'après leurs libellés relevés : « Tout » 25,9 dp,
        // « En cours » 50,3 dp, « Vous attend » 69,7 dp ; une pastille vaut 12 + point (7 + 6) +
        // libellé + 6 + compte (8) + 12.
        fun chip(label: Float) = 12f + 13f + label + 6f + 8f + 12f
        val widths = listOf(chip(25.9f), chip(50.3f), chip(69.7f))
        // La place réelle de la rangée : 340 dp de volet, moins les 6 dp de la liste par côté,
        // moins les 10 dp de la rangée par côté.
        val available = 340f - 12f - 20f

        // Avant : tout sur une rangée ne tient pas — la dernière sortait de l'écran.
        assertTrue("le défaut doit être reproductible", widths.sum() + 12f > available)

        // Après : deux rangées, chaque pastille entière, aucune perdue ni coupée.
        val rows = chipRows(widths, available, gap = 6f)
        assertEquals(listOf(listOf(0, 1), listOf(2)), rows)
        assertEquals("aucune pastille perdue", 3, rows.flatten().size)
        assertEquals("aucune pastille en double", listOf(0, 1, 2), rows.flatten().sorted())
        rows.forEach { row ->
            val total = row.map { widths[it] }.sum() + (row.size - 1) * 6f
            assertTrue("une rangée dépasse la place disponible : $total > $available", total <= available)
        }
        // Et aucune pastille n'est plus large que la rangée : elle tient toujours **entière**.
        assertTrue(widths.all { it <= available })
    }

    @Test
    fun `une rangee de filtres large ne se replie pas sans raison`() {
        val widths = listOf(70f, 90f, 110f)
        assertEquals(listOf(listOf(0, 1, 2)), chipRows(widths, available = 400f, gap = 6f))
        assertEquals(listOf(listOf(0), listOf(1), listOf(2)), chipRows(widths, available = 74f, gap = 6f))
        assertTrue(chipRows(emptyList(), available = 400f, gap = 6f).isEmpty())
    }
}
