package com.onyx.app.ui.sidebar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le libellé d'une tuile du volet **ne se coupe jamais au milieu d'un mot**.
 *
 * Le défaut relevé sur la vidéo du propriétaire : dans le volet (Honor replié), « Compétences » se
 * dessinait « Compétence / s ». La règle qui l'empêche est mesurable sans écran, donc éprouvée ici :
 * la taille descend juste assez pour que le mot le plus large de la grille tienne, et jamais sous le
 * plancher.
 */
class TileLabelFitTest {

    private val baseSp = 16.9f

    @Test
    fun `un mot qui tient garde la taille nominale`() {
        assertEquals(baseSp, TileLabelFit.fontSize(baseSp, labelWidthDp = 96f, widestWordDp = 80f), 0.001f)
        // Exactement la largeur : il tient, on ne réduit pas.
        assertEquals(baseSp, TileLabelFit.fontSize(baseSp, labelWidthDp = 80f, widestWordDp = 80f), 0.001f)
    }

    @Test
    fun `un mot trop large fait descendre la taille, proportionnellement`() {
        // 80 dp demandés dans 72 dp : 16,9 × 72/80 = 15,21 sp, au-dessus du plancher.
        val size = TileLabelFit.fontSize(baseSp, labelWidthDp = 72f, widestWordDp = 80f)
        assertEquals(baseSp * 72f / 80f, size, 0.01f)
        assertTrue("le mot tient à cette taille", TileLabelFit.wordWidthAt(baseSp, 80f, size) <= 72f)
        assertTrue("on ne descend pas sous le plancher", size >= baseSp * TileLabelFit.FLOOR_FRACTION)
    }

    @Test
    fun `jamais sous le plancher, meme si le mot ne tient pas`() {
        // Un mot démesuré : la règle s'arrête au plancher (plutôt que de rendre un libellé illisible).
        val size = TileLabelFit.fontSize(baseSp, labelWidthDp = 20f, widestWordDp = 200f)
        assertEquals(baseSp * TileLabelFit.FLOOR_FRACTION, size, 0.001f)
    }

    @Test
    fun `une mesure absente rend la taille nominale, jamais zero`() {
        assertEquals(baseSp, TileLabelFit.fontSize(baseSp, labelWidthDp = 0f, widestWordDp = 80f), 0.001f)
        assertEquals(baseSp, TileLabelFit.fontSize(baseSp, labelWidthDp = 80f, widestWordDp = 0f), 0.001f)
    }

    /**
     * La largeur du libellé telle qu'elle est mesurée dans le volet : la case moins l'icône (16 dp),
     * son écart (8 dp) et les marges de la tuile (2 × 12 dp). Le volet étroit du pliable replié
     * (340 dp de panneau) offrait **108 dp** — « Compétences » (~80 dp à police 1,3×) y tenait à
     * peine, et une tuile un peu plus étroite le coupait (« Compétence / s » sur la vidéo). Le volet
     * qui prend la fenêtre entière (411 dp, la largeur du Honor replié) lui en offre **143,7 dp**.
     */
    @Test
    fun `la largeur offerte au libelle d une tuile suit la largeur du volet`() {
        val narrowPanel = 340f
        val phonePanel = 411.43f
        fun labelWidth(panel: Float): Float {
            val cell = TileLabelFit.cellWidthDp(gridWidthDp = panel, paddingDp = 12f, gapDp = 4f)
            return TileLabelFit.labelWidthDp(cellWidthDp = cell, iconDp = 16f, iconGapDp = 8f, paddingDp = 12f)
        }
        val narrow = labelWidth(narrowPanel)
        val phone = labelWidth(phonePanel)
        assertEquals(108f, narrow, 0.5f)
        assertEquals(143.7f, phone, 0.5f)
        assertTrue("le volet qui prend la fenêtre offre plus de place au libellé", phone > narrow)
        // « Compétences » mesure ~80 dp à police 1,3× : la taille nominale suffit donc, sur les deux.
        assertEquals(16.9f, TileLabelFit.fontSize(16.9f, phone, 80f), 0.001f)
        assertEquals(16.9f, TileLabelFit.fontSize(16.9f, narrow, 80f), 0.001f)
    }
}
