package com.onyx.app.ui.design

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le verrou des compteurs de l'app** — le balayage, fait mordre.
 *
 * Le défaut du propriétaire était dans la barre d'onglets, mais la **famille** de pastilles de
 * nombres est plus large : le volet, l'en-tête de section, le kanban, la liste des bots, les
 * capacités. Tous portaient le même motif écrit à la main — un rembourrage vertical de **1 dp** (la
 * boîte du texte au contact de la boîte peinte) et, pour deux d'entre eux, une **largeur fixe**
 * (18 dp) qui ne vaut que pour un chiffre.
 *
 * Ce test relit les **sources** de ces sites, comme les autres verrous du dépôt : il nomme
 * `fichier:ligne` quand il refuse. Il mord sur :
 *
 * - un rembourrage vertical retombé à 1 dp sur la famille des pastilles ;
 * - une largeur écrite à la main sur un compteur ;
 * - une réserve de libellé de tuile qui ne serait plus **calculée** sur le nombre de chiffres ;
 * - un compteur d'app qui n'emploierait plus le compteur de la maison ([CounterBadge]).
 */
class CounterSitesGuardTest {

    private val counts =
        File("src/main/java/com/onyx/app/ui/sidebar/ChatSidebar.kt")
    private val labels =
        File("src/main/java/com/onyx/app/ui/sidebar/SidebarLabels.kt")
    private val panels =
        File("src/main/java/com/onyx/app/ui/chat/ChatPanels.kt")

    private val family = listOf(
        counts,
        labels,
        panels,
        File("src/main/java/com/onyx/app/ui/capabilities/CapabilityParts.kt"),
        File("src/main/java/com/onyx/app/ui/kanban/KanbanScreen.kt"),
        File("src/main/java/com/onyx/app/ui/bots/BotsArrange.kt"),
    )

    private fun lineOf(file: File, needle: String): Int {
        val index = file.readLines().indexOfFirst { it.contains(needle) }
        return if (index < 0) 0 else index + 1
    }

    private fun where(file: File, needle: String): String = "${file.path}:${lineOf(file, needle)}"

    /** Les lignes d'un fichier qui portent un motif, avec leur numéro — pour nommer le défaut. */
    private fun hits(file: File, pattern: Regex): List<String> =
        file.readLines().withIndex()
            .filter { pattern.containsMatchIn(it.value) }
            .map { "${file.path}:${it.index + 1} : ${it.value.trim()}" }

    @Test
    fun `aucune pastille de la famille ne retombe a un rembourrage vertical de 1 dp`() {
        // À 1 dp, la boîte du texte touche la boîte peinte : c'est la moitié du défaut relevé
        // (« le chiffre est tranché »). Le seuil de la maison est 2 dp (BadgeGeometry.PadVertical).
        val offenders = family.flatMap { hits(it, Regex("""vertical\s*=\s*1\.dp""")) }
        val geometry = File("../core/src/main/java/com/onyx/app/ui/design/BadgeGeometry.kt")
        assertTrue(
            "un rembourrage vertical de 1 dp est revenu sur une pastille : $offenders — " +
                "voir ${where(geometry, "val PadVertical")}",
            offenders.isEmpty(),
        )
    }

    /** Le corps d'une fonction, de sa signature à la première accolade fermante en colonne zéro. */
    private fun body(file: File, signature: String): String =
        file.readText().substringAfter(signature).substringBefore("\n}\n")

    @Test
    fun `aucun compteur ne porte une largeur ou une hauteur ecrite a la main`() {
        // Une largeur littérale sur un compteur ne vaut que pour le nombre de chiffres sur lequel
        // elle a été calculée : c'est le défaut « largeur fixe » (18 dp pour « 9 », faux pour « 99 »).
        // Le balayage porte sur **les corps de compteur**, pas sur les rangées : une rangée a le
        // droit d'avoir une hauteur de cible tactile écrite en dur, un compteur non.
        val literal = Regex("""(minWidth|minHeight|width|height|size)\s*=\s*\d+(\.\d+)?\.dp""")
        val blocks = listOf(
            counts to "internal fun BadgeCount(",
            labels to "internal fun tileBadgeReserveDp(",
            panels to "private fun CountTag(",
            File("src/main/java/com/onyx/app/ui/capabilities/CapabilityParts.kt") to
                "internal fun ProvenanceBadge(",
        )
        val offenders = blocks.flatMap { (file, signature) ->
            val text = body(file, signature)
            assertTrue(
                "le corps de compteur « $signature » est introuvable — " + where(file, signature),
                text.isNotBlank(),
            )
            text.lines().withIndex()
                .filter { literal.containsMatchIn(it.value) }
                .map { "${file.path} (${signature.removeSuffix("(")}+${it.index + 1}) : ${it.value.trim()}" }
        }
        assertTrue("largeur/hauteur écrite à la main sur un compteur : $offenders", offenders.isEmpty())
    }

    @Test
    fun `les pastilles de nombre des ecrans emploient le rembourrage de la maison`() {
        // Les deux pastilles de comptage de corps d'écran (le compte d'une colonne de kanban, le
        // « Needs you » d'une ligne de bot) portaient `vertical = 1.dp` : elles prennent le jeton.
        val kanban = File("src/main/java/com/onyx/app/ui/kanban/KanbanScreen.kt")
        val bots = File("src/main/java/com/onyx/app/ui/bots/BotsArrange.kt")
        listOf(kanban, bots).forEach { file ->
            val hits = file.readText().lines().count { it.contains("vertical = BadgeGeometry.PadVertical") }
            assertTrue(
                "la pastille de ${file.name} n'emploie plus le rembourrage de la maison — " +
                    where(file, "BadgeGeometry.PadVertical"),
                hits > 0,
            )
        }
    }

    @Test
    fun `le compteur du volet emploie le compteur de la maison`() {
        val text = counts.readText()
        assertTrue(
            "le compteur d'une tuile n'emploie plus le compteur de la maison — " + where(counts, "CounterBadge("),
            text.contains("CounterBadge(") && text.contains("count = value"),
        )
        assertFalse(
            "la largeur fixe de 18 dp est revenue dans le compteur du volet — " + where(counts, "minWidth = 18.dp"),
            text.contains("minWidth = 18.dp"),
        )
    }

    @Test
    fun `la reserve du compteur de tuile est calculee sur le nombre de chiffres`() {
        val text = labels.readText()
        assertTrue(
            "la réserve n'est plus calculée — " + where(labels, "tileBadgeReserveDp"),
            text.contains("tileBadgeReserveDp") && text.contains("BadgeGeometry.reserveDp"),
        )
        assertTrue(
            "la réserve ne couvre plus le pire cas réel (« 999 ») — " + where(labels, "TILE_BADGE_DIGITS"),
            text.contains("TILE_BADGE_DIGITS: Int = 3"),
        )
        assertFalse(
            "la réserve booléenne (un seul chiffre) est revenue — " + where(labels, "tileLabelWidthIn"),
            text.contains("hasBadge"),
        )
    }

    @Test
    fun `le compteur d'en-tete de section emploie le compteur de la maison`() {
        val text = panels.readText()
        val countTag = text.substringAfter("private fun CountTag(count: Int, accent: Color)")
            .substringBefore("private fun")
        assertTrue(
            "l'étiquette de compte d'un en-tête de section n'emploie plus le compteur de la maison — " +
                where(panels, "private fun CountTag"),
            countTag.contains("CounterBadge("),
        )
        assertFalse(
            "un rembourrage écrit à la main est revenu dans l'étiquette de compte — " +
                where(panels, "private fun CountTag"),
            countTag.contains(".padding("),
        )
    }
}
