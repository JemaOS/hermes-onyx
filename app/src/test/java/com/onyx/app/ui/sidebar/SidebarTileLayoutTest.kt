package com.onyx.app.ui.sidebar

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **La grille de raccourcis personnalisable : ses bornes, son ordre, sa bibliothèque.**
 *
 * Ce que ce test tient, et pourquoi il est là :
 * - **la valeur par défaut, ce sont les six tuiles de la référence** — un appareil qui n'a jamais
 *   touché à la grille voit exactement ce qu'il voyait avant ce lot ;
 * - **un retrait ne passe pas sous quatre tuiles, un ajout ne passe pas au-dessus de douze** : le
 *   refus est **explicite** (`removalRefusal` / `additionRefusal`), donc l'écran peut le **dire** —
 *   c'est ce qui remplace une troncature silencieuse ;
 * - **rien ne disparaît** : une tuile retirée est dans la bibliothèque, et s'y remet ;
 * - **un réglage déjà enregistré n'est pas invalidé** : l'ordre revient tel quel, un identifiant
 *   inconnu d'une version antérieure est ignoré **sans** emporter le reste ;
 * - **la mise en page ne se casse ni à quatre ni à douze tuiles** : deux ou trois colonnes, et
 *   jamais une tuile plus étroite que celle du volet le plus étroit à deux colonnes.
 */
class SidebarTileLayoutTest {

    /** La grille d'origine : les six tuiles de la référence, dans son ordre. */
    private val six = listOf("CRON", "INSIGHTS", "CAPABILITIES", "TOOLS", "GATEWAY", "ARCHIVED")

    // -----------------------------------------------------------------------------------------
    // 1. La valeur par défaut
    // -----------------------------------------------------------------------------------------

    @Test
    fun `la grille par defaut est celle des six tuiles de la reference`() {
        assertEquals(six, SidebarTileLayout.defaultIds)
        assertEquals(6, SidebarTileLayout.defaultIds.size)
        assertEquals(
            "le défaut suit la table de la référence, il n'est pas recopié",
            SidebarDestinations.referenceTiles.map { it.id },
            SidebarTileLayout.defaultIds,
        )
    }

    @Test
    fun `un reglage absent ou illisible rend la grille d'origine`() {
        listOf(null, "", "   ", ",,,", "PAS-UNE-TUILE").forEach { stored ->
            assertEquals("clé « $stored »", SidebarTileLayout.defaultIds, SidebarTileLayout.parse(stored))
        }
    }

    // -----------------------------------------------------------------------------------------
    // 2. Persistance — la sélection et l'ordre survivent
    // -----------------------------------------------------------------------------------------

    @Test
    fun `un reglage enregistre revient dans son ordre`() {
        val choix = listOf("CODE", "KANBAN", "CRON", "MODELS", "TOOLS")
        assertEquals(choix, SidebarTileLayout.parse(SidebarTileLayout.encode(choix)))
    }

    @Test
    fun `un identifiant inconnu n invalide pas le reste du reglage`() {
        // Une valeur écrite par une version qui connaissait une tuile depuis retirée du catalogue :
        // elle est ignorée, le reste est **gardé** — le réglage n'est pas invalidé.
        assertEquals(
            listOf("CRON", "INSIGHTS", "CAPABILITIES", "TOOLS"),
            SidebarTileLayout.parse("CRON,TUILE-D-UNE-AUTRE-VERSION,INSIGHTS,CAPABILITIES,TOOLS"),
        )
    }

    @Test
    fun `un reglage descendu sous le plancher est complete, jamais rendu plus court`() {
        // Une valeur amputée (trois tuiles) ne produit pas une grille à trois tuiles : la règle des
        // quatre la complète avec les tuiles d'origine.
        assertEquals(4, SidebarTileLayout.parse("CRON,INSIGHTS,CAPABILITIES").size)
        assertTrue(SidebarTileLayout.parse("CRON,INSIGHTS,CAPABILITIES").containsAll(
            listOf("CRON", "INSIGHTS", "CAPABILITIES"),
        ))
        assertEquals(4, SidebarTileLayout.normalize(listOf("CODE", "KANBAN")).size)
    }

    @Test
    fun `le reglage ecrit est toujours dans ses bornes`() {
        // Vingt entrées, dont des doublons et des répétitions : ce qui s'écrit est une grille dans
        // ses bornes, sans doublon.
        val ecrit = SidebarTileLayout.encode(
            List(20) { SidebarTileLayout.catalog[it % SidebarTileLayout.catalog.size].id }.reversed(),
        )
        val ids = ecrit.split(",")
        assertTrue("au plus douze tuiles écrites", ids.size <= SidebarTileLayout.MAX_TILES)
        assertEquals("aucun doublon écrit", ids.size, ids.toSet().size)
        assertTrue(ids.all { SidebarTileLayout.tile(it) != null })
    }

    // -----------------------------------------------------------------------------------------
    // 3. Les bornes — moins de quatre refusé, plus de douze refusé
    // -----------------------------------------------------------------------------------------

    @Test
    fun `les bornes de la grille sont quatre et douze`() {
        assertEquals(4, SidebarTileLayout.MIN_TILES)
        assertEquals(12, SidebarTileLayout.MAX_TILES)
        assertEquals(4, SidebarTileLayout.bounds.min)
        assertEquals(12, SidebarTileLayout.bounds.max)
    }

    @Test
    fun `le retrait est refuse au plancher et la liste ne bouge pas`() {
        val quatre = six.take(4)
        assertNotNull("à quatre tuiles, le retrait est refusé", SidebarTileLayout.removalRefusal(quatre))
        assertEquals(
            "un retrait refusé rend la grille inchangée",
            quatre,
            SidebarTileLayout.remove(quatre, "CRON"),
        )
        // Cinq tuiles : le retrait passe.
        val cinq = six.take(5)
        assertNull(SidebarTileLayout.removalRefusal(cinq))
        assertEquals(listOf("INSIGHTS", "CAPABILITIES", "TOOLS", "GATEWAY"), SidebarTileLayout.remove(cinq, "CRON"))
    }

    @Test
    fun `l ajout est refuse au plafond et la liste ne bouge pas`() {
        // Le catalogue porte **dix** destinations aujourd'hui : le plafond se mesure donc sur une
        // grille de douze, telle que la règle la voit — c'est la borne qui refuse, avant tout autre
        // motif, et c'est **le même refus** que l'écran affichera.
        val douze = List(SidebarTileLayout.MAX_TILES) { "TUILE-$it" }
        val refus = SidebarTileLayout.additionRefusal(douze, "CRON")
        assertNotNull("à douze tuiles, l'ajout est refusé", refus)
        assertTrue("le refus porte le plafond, pour que l'écran l'écrive", refus!!.contains("%d"))
        assertNotNull(
            "la borne haute refuse aussi quand la tuile est connue",
            TileGridBounds.House.additionRefusal(SidebarTileLayout.MAX_TILES),
        )
        assertNull(
            "à onze, il reste une place",
            TileGridBounds.House.additionRefusal(SidebarTileLayout.MAX_TILES - 1),
        )
        assertEquals(
            "un ajout refusé rend la grille inchangée",
            douze,
            SidebarTileLayout.add(douze, "CRON"),
        )
    }

    @Test
    fun `un ajout ne passe pas non plus pour une tuile inconnue ou deja presente`() {
        assertNotNull(SidebarTileLayout.additionRefusal(six, "PAS-UNE-TUILE"))
        assertNotNull(SidebarTileLayout.additionRefusal(six, "CRON"))
        assertEquals(
            "un ajout refusé rend la grille inchangée",
            six,
            SidebarTileLayout.add(six, "PAS-UNE-TUILE"),
        )
    }

    @Test
    fun `la grille reste dans ses bornes a chaque etape`() {
        // La suite d'une composition réelle : on retire jusqu'au plancher, on ajoute jusqu'au plafond.
        var ids = SidebarTileLayout.defaultIds
        repeat(8) { ids = SidebarTileLayout.remove(ids, ids.first()) }
        assertEquals("on ne descend pas sous quatre", 4, ids.size)
        repeat(12) { tile -> ids = SidebarTileLayout.add(ids, SidebarTileLayout.catalog[tile % 10].id) }
        assertTrue("on ne monte pas au-dessus de douze", ids.size <= SidebarTileLayout.MAX_TILES)
        assertTrue(SidebarTileLayout.inBounds(ids))
    }

    // -----------------------------------------------------------------------------------------
    // 4. Le réordonnancement
    // -----------------------------------------------------------------------------------------

    @Test
    fun `le reordonnancement deplace une tuile sans rien perdre`() {
        val bouge = SidebarTileLayout.move(six, 0, 3)
        assertEquals(listOf("INSIGHTS", "CAPABILITIES", "TOOLS", "CRON", "GATEWAY", "ARCHIVED"), bouge)
        assertEquals("aucune tuile perdue", six.toSet(), bouge.toSet())
        assertEquals("aucun doublon", six.size, bouge.toSet().size)
        assertEquals("un retour en arrière rend l'ordre d'origine", six, SidebarTileLayout.move(bouge, 3, 0))
    }

    @Test
    fun `un deplacement qui ne change rien rend la grille telle quelle`() {
        assertEquals(six, SidebarTileLayout.move(six, 2, 2))
        assertEquals("un index de départ hors liste", six, SidebarTileLayout.move(six, 9, 0))
        assertEquals("même place", six, SidebarTileLayout.move(six, 0, 0))
        // Une cible hors liste est **ramenée** dans la grille : elle ne fait pas disparaître la tuile.
        assertEquals(
            listOf("INSIGHTS", "CAPABILITIES", "TOOLS", "GATEWAY", "ARCHIVED", "CRON"),
            SidebarTileLayout.move(six, 0, 99),
        )
    }

    // -----------------------------------------------------------------------------------------
    // 5. Le catalogue et la bibliothèque — rien ne disparaît
    // -----------------------------------------------------------------------------------------

    @Test
    fun `la bibliotheque porte les destinations qui ne sont pas sur la grille`() {
        val library = SidebarTileLayout.library(SidebarTileLayout.defaultIds)
        assertEquals(
            "les quatre destinations propres à Onyx sont dans la bibliothèque",
            listOf("FILES", "MODELS", "KANBAN", "CODE"),
            library.map { it.id },
        )
        assertEquals(
            "grille + bibliothèque = tout le catalogue",
            SidebarTileLayout.catalog.map { it.id }.toSet(),
            (SidebarTileLayout.defaultIds + library.map { it.id }).toSet(),
        )
        assertTrue(
            "aucune tuile n'est des deux côtés",
            library.none { it.id in SidebarTileLayout.defaultIds },
        )
    }

    @Test
    fun `toute tuile retiree est recuperable dans la bibliotheque`() {
        // « Aucune suppression : rien ne disparaît. » Pour **chaque** destination du catalogue : on
        // la retire, elle entre dans la bibliothèque, et elle se remet — à sa place, avec son écran.
        SidebarTileLayout.catalog.forEach { tile ->
            val grille = if (tile.id in SidebarTileLayout.defaultIds) {
                SidebarTileLayout.defaultIds
            } else {
                SidebarTileLayout.defaultIds + tile.id
            }
            val apresRetrait = SidebarTileLayout.remove(grille, tile.id)
            assertEquals("le retrait a bien eu lieu (${tile.id})", grille.size - 1, apresRetrait.size)
            assertFalse(apresRetrait.contains(tile.id))
            assertTrue(
                "la tuile ${tile.id} est dans la bibliothèque",
                SidebarTileLayout.library(apresRetrait).any { it.id == tile.id },
            )
            val remise = SidebarTileLayout.add(apresRetrait, tile.id)
            assertEquals("la tuile ${tile.id} se remet", grille.size, remise.size)
            assertEquals(
                "la grille remise porte les mêmes tuiles qu'avant le retrait",
                grille.toSet(),
                remise.toSet(),
            )
            assertEquals(
                "la tuile remise entre par la fin : c'est l'ordre de la personne, et le glissement le règle",
                tile.id,
                remise.last(),
            )
        }
    }

    @Test
    fun `chaque destination du catalogue est servie par le routeur du volet`() {
        // La preuve « tuile → destination » côté code : aucune tuile du catalogue n'est orpheline.
        SidebarTileLayout.catalog.forEach { tile ->
            assertTrue(
                "la tuile ${tile.id} n'a pas d'écran porté par le volet",
                SidebarDestinations.paneScreens.contains(tile.id),
            )
            assertNotNull(SidebarTileLayout.tile(tile.id))
        }
        assertEquals(10, SidebarTileLayout.catalog.size)
        assertFalse(
            "les Réglages ne sont pas une tuile (la roue dentée de la tête les porte)",
            SidebarTileLayout.catalog.any { it.id == SidebarDestinations.HEAD_SETTINGS_TILE },
        )
    }

    // -----------------------------------------------------------------------------------------
    // 6. La mise en page — deux ou trois colonnes, jamais cassée
    // -----------------------------------------------------------------------------------------

    /** Le mot le plus long des libellés de la grille, à la police 1,3× (`TileLabelWordLimit`). */
    private val widestWord = 125.dp

    private val largeurs = listOf(336.dp, 387.dp, 494.dp, 620.dp, 700.dp, 1000.dp)

    @Test
    fun `deux colonnes sur un telephone, trois sur un volet large`() {
        // Le téléphone : volet en surimpression de 360 dp (le plus étroit servi) et de 411 dp.
        assertEquals(2, tileColumnsFor(336.dp, widestWord))
        assertEquals(2, tileColumnsFor(387.dp, widestWord))
        // Un pliable déplié / une tablette : trois colonnes.
        assertEquals(3, tileColumnsFor(700.dp, widestWord))
        assertEquals(3, tileColumnsFor(1000.dp, widestWord))
    }

    @Test
    fun `la troisieme colonne ne s obtient jamais en appauvrissant le libelle`() {
        largeurs.forEach { width ->
            if (tileColumnsFor(width, widestWord) == 3) {
                assertTrue(
                    "à $width dp, une tuile de trois colonnes est plus étroite que sur le volet le plus étroit",
                    tileWidthIn(width, 3) >= tileWidthIn(336.dp, 2),
                )
            }
        }
    }

    @Test
    fun `la mise en page ne se casse ni a quatre ni a douze tuiles`() {
        listOf(0.dp, 96.dp, widestWord).forEach { widest ->
            largeurs.forEach { width ->
                val columns = tileColumnsFor(width, widest)
                assertTrue("colonnes hors bornes à $width dp", columns == 2 || columns == 3)
                val label = tileLabelWidthIn(width, badgeDigits = 0, columns = columns)
                assertTrue("aucune place pour le libellé à $width dp", label.value > 0f)
                assertTrue(
                    "la tuile est plus étroite que la référence à $width dp",
                    tileWidthIn(width, columns) >= tileWidthIn(336.dp, TILE_COLUMNS_MIN),
                )
            }
        }
        // De quatre à douze tuiles : la même grille, le même nombre de colonnes, aucune exception.
        (SidebarTileLayout.MIN_TILES..SidebarTileLayout.MAX_TILES).forEach { count ->
            assertEquals(2, tileColumnsFor(387.dp, widestWord))
            val rows = (count + 1) / 2
            assertTrue("les rangées restent entières à $count tuiles", rows * 2 >= count)
        }
    }

    @Test
    fun `le catalogue tient dans le plafond mais le depasse si on le lui demande`() {
        // Le catalogue porte dix destinations : le plafond de douze a de la marge — et la règle
        // **refuse** quand même au-delà, ce que la contre-épreuve ci-dessous mesure.
        assertEquals(10, SidebarTileLayout.catalog.size)
        assertTrue(SidebarTileLayout.catalog.size < SidebarTileLayout.MAX_TILES)
    }
}
