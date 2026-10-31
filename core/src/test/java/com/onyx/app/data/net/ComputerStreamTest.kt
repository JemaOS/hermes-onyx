package com.onyx.app.data.net

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ce que l'app demande au pont : la taille d'image, sa qualité, et où sont les volets.
 *
 * Le poids du réseau se décide ici — une largeur demandée de trop, et chaque image transporte des
 * pixels que l'écran ne montrera jamais, sur le forfait du propriétaire.
 */
class ComputerStreamTest {

    @Test
    fun `la largeur demandee suit le volet, bornee des deux cotes`() {
        // Un volet de téléphone : on demande exactement ce qu'on affiche.
        assertEquals(1080, ComputerStream.viewWidthFor(1080, screenWidth = 3840))
        // Un grand volet : on demande plus, sans dépasser le plafond du pont.
        assertEquals(ComputerStream.MAX_WIDTH, ComputerStream.viewWidthFor(6000, screenWidth = 3840))
        // Un tout petit volet : plancher de lisibilité.
        assertEquals(ComputerStream.MIN_WIDTH, ComputerStream.viewWidthFor(120, screenWidth = 3840))
        // Jamais plus que l'écran du PC lui-même.
        assertEquals(1920, ComputerStream.viewWidthFor(4000, screenWidth = 1920))
    }

    @Test
    fun `la largeur demandee est toujours paire`() {
        // Les encodeurs vidéo n'aiment pas les tailles impaires : 1081 deviendrait 1080.
        assertEquals(1080, ComputerStream.viewWidthFor(1081, screenWidth = 3840))
        assertEquals(1080, ComputerStream.viewWidthFor(1080, screenWidth = 3840))
        assertEquals(0, ComputerStream.viewWidthFor(481, screenWidth = 3840) % 2)
    }

    @Test
    fun `une largeur absente ne demande rien d'absurde`() {
        assertEquals(ComputerStream.MIN_WIDTH, ComputerStream.viewWidthFor(0, screenWidth = 3840))
        assertEquals(ComputerStream.MIN_WIDTH, ComputerStream.viewWidthFor(-40, screenWidth = 3840))
        // Un écran inconnu ne doit pas faire tomber le plancher sous le plafond.
        assertTrue(ComputerStream.viewWidthFor(100, screenWidth = 0) >= ComputerStream.MIN_WIDTH)
    }

    @Test
    fun `la qualite suit la finesse affichee`() {
        assertEquals(ComputerStream.QUALITY_WIDE, ComputerStream.qualityFor(1920))
        assertEquals(ComputerStream.QUALITY_WIDE, ComputerStream.qualityFor(1440))
        assertTrue(ComputerStream.qualityFor(1080) > ComputerStream.QUALITY_WIDE)
        assertTrue(ComputerStream.qualityFor(600) >= ComputerStream.qualityFor(1080))
    }

    @Test
    fun `la cadence d'un geste ne descend jamais sous celle du repos`() {
        assertEquals(6, ComputerStream.fpsFor(6, 12, moving = false))
        assertEquals(12, ComputerStream.fpsFor(6, 12, moving = true))
        // Un pont mal configuré (turbo plus bas que le repos) ne doit pas ralentir l'aperçu.
        assertEquals(6, ComputerStream.fpsFor(6, 2, moving = true))
        assertTrue(ComputerStream.TURBO_HOLD_MS >= 1_000L)
    }
}

/** Où sont les volets, fermé et déplié. */
class ComputerLayoutTest {

    /** Le Honor Magic V3 fermé : 411 dp de large. */
    private val closed = 411.dp

    /** Le même déplié : 862 dp. */
    private val unfolded = 862.dp

    @Test
    fun `ferme, une seule colonne`() {
        assertFalse(ComputerLayout.twoPanes(closed))
        assertEquals(closed, ComputerLayout.previewWidth(closed))
        assertEquals(closed, ComputerLayout.controlsWidth(closed))
    }

    @Test
    fun `deplie, deux volets et l'apercu prend le sien en entier`() {
        assertTrue(ComputerLayout.twoPanes(unfolded))
        val controls = ComputerLayout.controlsWidth(unfolded)
        val preview = ComputerLayout.previewWidth(unfolded)
        // Les deux volets font exactement la fenêtre : aucune largeur perdue, aucune étirée.
        assertEquals(unfolded.value, (controls + preview).value, 0.01f)
        // Le volet des commandes est borné, jamais la moitié de l'écran.
        assertTrue(controls >= ComputerLayout.CONTROLS_MIN_WIDTH)
        assertTrue(controls <= ComputerLayout.CONTROLS_MAX_WIDTH)
        // L'aperçu garde une place utilisable : un 16:9 s'y affiche plus grand qu'au milieu du vide.
        assertTrue(preview >= ComputerLayout.PREVIEW_MIN_WIDTH)
    }

    @Test
    fun `entre les deux, la fenetre choisit selon la place`() {
        assertFalse(ComputerLayout.twoPanes(600.dp))
        assertFalse(ComputerLayout.twoPanes(719.dp))
        assertTrue(ComputerLayout.twoPanes(720.dp))
        assertTrue(ComputerLayout.twoPanes(2000.dp))
    }

    @Test
    fun `la largeur du volet de l'apercu se donne en pixels reels`() {
        // C'est cette valeur que l'app annonce au pont : elle décide du poids de chaque image.
        assertEquals(1760, ComputerLayout.previewWidthPx(640.dp, density = 2.75f))
        assertEquals(1078, ComputerLayout.previewWidthPx(392.dp, density = 2.75f))
        // Un volet vide ne doit pas rendre zéro : le pont refuserait la demande.
        assertEquals(1, ComputerLayout.previewWidthPx(0.dp, density = 2.75f))
    }
}
