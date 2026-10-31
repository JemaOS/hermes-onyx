package com.onyx.app.ui.design

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le verrou des jetons du socle — les valeurs de la vague 1, recoupées.**
 *
 * Le piège de la journée : deux lots qui retapent les mêmes jetons et **divergent** ; la fusion
 * donnerait alors trois designs. Ce test fige les six corps, les deux espacements, la famille de
 * rayons, la famille d'ombres et l'épaisseur de trait **sur les valeurs exactes** lues dans le
 * générateur du jeu corrigé (`maquettes-hermes-v2/sources/`), telles que la vague 1 les a posées.
 *
 * Une dérive d'un seul dixième de dp ou d'un demi-point fait échouer le test — c'est son but.
 */
class OnyxSocleTest {

    // — 1 · Les six corps —

    @Test
    fun `l echelle typographique est exactement 30 26 16 15 12 11`() {
        assertEquals(
            listOf(30.sp, 26.sp, 16.sp, 15.sp, 12.sp, 11.sp),
            OnyxSocle.TypeScale,
        )
        assertEquals(30.sp, OnyxSocle.Display)
        assertEquals(26.sp, OnyxSocle.Heading)
        assertEquals(16.sp, OnyxSocle.Title)
        assertEquals(15.sp, OnyxSocle.Body)
        assertEquals(12.sp, OnyxSocle.Meta)
        assertEquals(11.sp, OnyxSocle.Micro)
    }

    @Test
    fun `l interlettrage des grands corps est negatif et celui de section ouvert`() {
        assertEquals((-0.032f), OnyxSocle.TrackingDisplay.value, 1e-6f)
        assertEquals((-0.030f), OnyxSocle.TrackingHeading.value, 1e-6f)
        assertEquals((-0.018f), OnyxSocle.TrackingTitle.value, 1e-6f)
        assertEquals(0.16f, OnyxSocle.TrackingMicro.value, 1e-6f)
    }

    // — 2 · Les deux espacements —

    @Test
    fun `l echelle d espacement vaut 8 et 16, et rien d autre`() {
        assertEquals(listOf(8.dp, 16.dp), OnyxSocle.SpacingScale)
        assertEquals(8, OnyxSocle.SpacingStep)
        assertEquals(8.dp, OnyxSocle.Tight)
        assertEquals(16.dp, OnyxSocle.Loose)
    }

    @Test
    fun `tous les jetons d espacement nommes sont sur l echelle`() {
        OnyxSocle.spacingTokens.forEach { (name, value) ->
            assertTrue("le jeton $name ($value) sort de l'échelle 8/16", OnyxSocle.isOnScale(value))
        }
    }

    // — 3 · La famille de rayons —

    @Test
    fun `la famille de rayons vaut 14 20 28 et la capsule`() {
        assertEquals(listOf(14.dp, 20.dp, 28.dp, 50.dp), OnyxSocle.Radii)
        assertEquals(14.dp, OnyxSocle.RadiusField)
        assertEquals(20.dp, OnyxSocle.RadiusCard)
        assertEquals(28.dp, OnyxSocle.RadiusPanel)
        assertEquals(50.dp, OnyxSocle.RadiusCapsule)
    }

    // — 4 · La famille d'ombres, trois profondeurs enchaînées —

    @Test
    fun `les ombres sont trois profondeurs, toutes rendues`() {
        assertEquals(3, OnyxSocle.Shadows.size)
        assertEquals(listOf("Near", "Mid", "Far"), OnyxSocle.Shadows.map { it.name })
        // Chaque profondeur chaîne au moins deux couches : jamais deux ombres dans une seule
        // déclaration (le défaut de la version livrée — 42 ombres jamais rendues).
        assertEquals(2, OnyxSocle.ShadowNear.layers.size)
        assertEquals(3, OnyxSocle.ShadowMid.layers.size)
        assertEquals(3, OnyxSocle.ShadowFar.layers.size)
    }

    @Test
    fun `aucune couche d ombre n est sans flou ni sans encre`() {
        OnyxSocle.Shadows.flatMap { it.layers }.forEach { layer ->
            assertTrue("flou > 0", layer.blur.value > 0f)
            assertTrue("opacité > 0", layer.alpha > 0f)
        }
    }

    // — 5 · L'épaisseur de trait —

    @Test
    fun `le trait rendu vaut 1,55 partout`() {
        assertEquals(1.55.dp, OnyxSocle.Stroke)
        // Sur une boîte de 24 unités à la taille 24, le trait de boîte rend 1,55.
        assertEquals(1.55f, OnyxSocle.renderedStroke(size = 24.dp, width = OnyxSocle.iconStrokeWidth(24.dp)), 1e-4f)
        // À une autre taille, la largeur de boîte compense, le rendu reste 1,55.
        val w18 = OnyxSocle.iconStrokeWidth(18.dp)
        assertEquals(1.55f, OnyxSocle.renderedStroke(size = 18.dp, width = w18), 1e-4f)
    }

    // — 6 · Les tailles de cible —

    @Test
    fun `les cibles sont au plancher de 48`() {
        assertEquals(48.dp, OnyxSocle.TouchTarget)
        assertTrue("une rangée de liste fait au moins 48", OnyxSocle.RowHeight.value >= OnyxSocle.TouchTarget.value)
    }

    // — 7 · Le centrage optique —

    @Test
    fun `le centrage optique ramene la masse au centre`() {
        // Une masse de 12 dans une boîte de 24 se décale de 6.
        assertEquals(6.dp, OnyxSocle.opticalCenterOffset(box = 24.dp, ink = 12.dp))
        // Et l'emboîtement ne descend jamais sous zéro.
        assertEquals(0.dp, OnyxSocle.interior(outer = 14.dp, inset = 20.dp))
        assertEquals(6.dp, OnyxSocle.interior(outer = 20.dp, inset = 14.dp))
    }

    // — 8 · La table de provenance cite les valeurs du générateur —

    @Test
    fun `la provenance porte les sources exactes du generateur`() {
        val tokens = OnyxSocle.provenance.associate { it.token to it.value }
        assertEquals("30 sp, −0,032 em", tokens["Display"])
        assertEquals("26 sp, −0,030 em", tokens["Heading"])
        assertEquals("16 sp, −0,018 em", tokens["Title"])
        assertEquals("8 dp", tokens["Tight"])
        assertEquals("16 dp", tokens["Loose"])
        assertEquals("1,55 dp", tokens["Stroke"])
        OnyxSocle.provenance.forEach { assertTrue("source citée pour ${it.token}", it.source.isNotBlank()) }
    }

    // — 9 · Les exceptions sont déclarées, pas improvisées —

    @Test
    fun `le monospace a 13 est une exception retiree, plus toleree`() {
        // Elle a été **assumée**, puis **retirée** : 13 sp est un corps entre 12 et 15, c'est-à-dire
        // ce que l'échelle interdit. L'identifiant se lit désormais à 12 sp, en monospace.
        assertFalse(
            OnyxSocle.isDeclaredException(
                where = "Échelle typographique — identifiant monospace",
                value = "13.sp (JetBrains Mono)",
            ),
        )
        assertTrue(
            "le registre garde la trace du retrait",
            OnyxSocle.retiredExceptions.any { it.value.startsWith("13.sp") },
        )
        assertFalse(
            OnyxSocle.isDeclaredException(where = "inconnu", value = "13.sp (JetBrains Mono)"),
        )
    }
}
