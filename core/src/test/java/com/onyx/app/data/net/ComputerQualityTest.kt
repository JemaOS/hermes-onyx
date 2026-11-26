package com.onyx.app.data.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L'échelle de qualité et son gouverneur — verrouillés sans réseau.
 *
 * Le gouverneur est volontairement **pur** : il reçoit des mesures, il rend une décision. C'est ce
 * qui permet de prouver l'hystérésis sans attendre un vrai lien qui se dégrade — et c'est aussi ce
 * qui garantit qu'il n'y a qu'un seul décideur, nourri par les mêmes chiffres que le panneau.
 */
class ComputerQualityTest {

    private fun stats(rtt: Int, fps: Float = 6f, dropped: Int = 0) =
        ComputerStreamStats(rttMs = rtt, fps = fps, dropped = dropped, width = 1080, quality = 9)

    @Test
    fun `la largeur demandee ne descend jamias sous le plancher de lisibilite`() {
        // Le cran le plus léger, sur un grand volet : on reste au-dessus du plancher.
        val lightest = ComputerQuality.LADDER[ComputerQuality.LIGHTEST]
        assertEquals(
            ComputerStream.READABLE_WIDTH,
            ComputerQuality.widthFor(lightest, paneWidthPx = 3840),
        )
        // Un volet plus petit que le plancher : on demande le volet, jamais plus.
        assertEquals(600, ComputerQuality.widthFor(lightest, paneWidthPx = 600))
        // Toujours pair.
        assertEquals(0, ComputerQuality.widthFor(lightest, paneWidthPx = 1081) % 2)
        // Jamais plus que le volet, même au cran le plus lourd.
        assertTrue(ComputerQuality.widthFor(ComputerQuality.LADDER[0], 1080) <= 1080)
    }

    @Test
    fun `la descente est immediate, la remontee attend d'etre stable`() {
        val governor = ComputerQualityGovernor(steadySamples = 4)
        assertEquals(ComputerQuality.HEAVIEST, governor.step)
        // **Une seule** mauvaise mesure fait descendre : un lien qui souffre le dit tout de suite.
        assertEquals(1, governor.observe(stats(rtt = 900), missRatio = 0f, dropped = 0))
        // Une bonne mesure ne fait **pas** remonter tout de suite.
        repeat(3) { assertNull(governor.observe(stats(rtt = 90), missRatio = 0f, dropped = 0)) }
        assertEquals(1, governor.step)
        // La quatrième bonne mesure, elle, remonte.
        assertEquals(0, governor.observe(stats(rtt = 90), missRatio = 0f, dropped = 0))
        assertEquals(ComputerQuality.HEAVIEST, governor.step)
    }

    @Test
    fun `une remontee ne redescend pas betement a la premiere oscillation`() {
        val governor = ComputerQualityGovernor(steadySamples = 3)
        governor.select(2)
        // Trois bonnes mesures : on remonte d'un cran.
        governor.observe(stats(rtt = 90), 0f, 0)
        governor.observe(stats(rtt = 90), 0f, 0)
        assertEquals(1, governor.observe(stats(rtt = 90), 0f, 0))
        assertEquals(1, governor.step)
        // Une mesure moyenne (ni bonne ni mauvaise) ne redescend pas et remet le compteur à zéro :
        // l'échelle ne danse pas entre deux crans.
        assertNull(governor.observe(stats(rtt = 300), 0f, 0))
        assertEquals(1, governor.step)
        // Il faut une vraie dégradation pour redescendre.
        assertEquals(2, governor.observe(stats(rtt = 900), 0f, 0))
    }

    @Test
    fun `trames manquees et images jetees font descendre l'echelle`() {
        val governor = ComputerQualityGovernor()
        assertEquals(1, governor.observe(stats(rtt = 60), missRatio = 0.4f, dropped = 0))
        assertEquals(2, governor.observe(stats(rtt = 60), missRatio = 0f, dropped = 3))
    }

    @Test
    fun `l'echelle reste dans ses bornes`() {
        val governor = ComputerQualityGovernor()
        repeat(20) { governor.observe(stats(rtt = 5_000), missRatio = 1f, dropped = 9) }
        assertEquals(ComputerQuality.LIGHTEST, governor.step)
        val narrow = ComputerQualityGovernor(steadySamples = 1)
        repeat(20) { narrow.observe(stats(rtt = 10), missRatio = 0f, dropped = 0) }
        assertEquals(ComputerQuality.HEAVIEST, narrow.step)
    }

    @Test
    fun `la liaison econome force le cran le plus leger et ne bouge plus`() {
        val governor = ComputerQualityGovernor()
        assertEquals(ComputerQuality.LIGHTEST, governor.forceEconomy())
        assertTrue(governor.economical)
        // Toute mesure est ignorée : l'échelle ne reprend pas la main sur un choix explicite.
        assertNull(governor.observe(stats(rtt = 90), 0f, 0))
        assertNull(governor.observe(stats(rtt = 90), 0f, 0))
        assertEquals(ComputerQuality.LIGHTEST, governor.step)
        // Rendre la main remet le cran le plus lourd, puis l'échelle repart.
        assertEquals(ComputerQuality.HEAVIEST, governor.automatic())
        assertTrue(!governor.economical)
    }

    @Test
    fun `le cran le plus leger demande moins d'images que le plus lourd`() {
        val heavy = ComputerQuality.LADDER[ComputerQuality.HEAVIEST]
        val light = ComputerQuality.LADDER[ComputerQuality.LIGHTEST]
        assertTrue("la liaison économe doit demander moins d'images", light.fps < heavy.fps)
        assertTrue("la liaison économe doit compresser plus", light.quality > heavy.quality)
        assertTrue("l'échelle doit descendre la largeur", light.width < heavy.width)
    }
}
