package com.onyx.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.onyx.app.ui.design.Halo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * « Noir pur » et « Halo », mesurés — pas seulement déclarés.
 *
 * Le noir pur n'agit **qu'en apparence sombre** : en clair, la page garde la couleur du thème. Le
 * halo, éteint, **aplatit vraiment tout** (aucun arrêt de dégradé, alpha nul) ; allumé, il pose une
 * lumière d'accent dont on mesure l'écart de couleur avec la page.
 */
class PureBlackAndHaloTest {

    private fun theme(
        dark: Boolean,
        pureBlack: Boolean = false,
        halo: Boolean = true,
        base: OnyxTheme = OnyxTheme.SLATE,
    ): ResolvedTheme =
        ResolvedTheme.resolve(
            theme = base,
            dark = dark,
            fonts = OnyxFonts.System,
            pureBlack = pureBlack,
            halo = halo,
        )

    @Test
    fun `noir pur, la page passe au noir absolu en sombre seulement`() {
        val dark = theme(dark = true, pureBlack = true)
        assertEquals("la page sombre", Color.Black, dark.pageColor)
        assertEquals("la page de formulaire sombre", Color.Black, dark.formPageColor)
        assertEquals("luminance nulle", 0f, dark.pageColor.luminance(), 0.0001f)
        assertTrue("le drapeau se lit", dark.oledBlack)

        // En clair, le réglage est sans effet : la page garde la couleur du thème.
        val light = theme(dark = false, pureBlack = true)
        assertFalse("pas d'effet en clair", light.oledBlack)
        assertTrue("la page claire n'est pas noire", light.pageColor.luminance() > 0.5f)
    }

    @Test
    fun `sans noir pur, la page sombre du theme n'est pas le noir absolu`() {
        // Sur un thème dont la page sombre est teintée (et non déjà #000), « noir pur » change
        // vraiment quelque chose — et son absence laisse la teinte du thème.
        val dark = theme(dark = true, pureBlack = false)
        assertFalse(dark.oledBlack)
        assertTrue("la page sombre du thème n'est pas #000", dark.pageColor.luminance() > 0f)
        assertTrue("et elle est plus sombre que la page claire", dark.pageColor.luminance() < theme(dark = false).pageColor.luminance())
        // Avec le réglage, cette même page tombe au noir absolu.
        assertTrue(theme(dark = true, pureBlack = true).pageColor.luminance() < dark.pageColor.luminance())
    }

    @Test
    fun `halo eteint, aucun arret de degrade, la page est plate`() {
        val off = theme(dark = true, halo = false)
        assertTrue("aucun arrêt", Halo.topStops(off).isEmpty())
        assertEquals(0f, Halo.topAlpha(off), 0f)
        assertEquals(0f, Halo.underAlpha(off), 0f)
    }

    @Test
    fun `halo allume, la lumiere d accent se mesure sur la page`() {
        val on = theme(dark = true, halo = true)
        val stops = Halo.topStops(on)
        assertEquals("un dégradé à deux arrêts", 2, stops.size)
        assertEquals("le premier est l'accent translucide", Halo.topAlpha(on), stops.first().alpha, 0.01f)
        assertTrue("l'accent est teinté", stops.first().alpha > 0f)
        assertEquals("le second est transparent", 0f, stops.last().alpha, 0f)

        // L'écart se mesure : la couleur de l'accent posée sur la page en change la luminance.
        val washed = on.accent.copy(alpha = Halo.topAlpha(on)).compositeOver(on.pageColor)
        val delta = kotlin.math.abs(washed.luminance() - on.pageColor.luminance())
        assertTrue("le halo change la page (mesuré $delta)", delta > 0.01f)

        // Éteint, l'écart est nul : la page est plate.
        val offTheme = theme(dark = true, halo = false)
        assertEquals(0f, kotlin.math.abs(offTheme.pageColor.luminance() - on.pageColor.luminance()), 0f)

        // En clair, la lumière est plus ténue qu'en sombre — mais pas nulle.
        val lightOn = theme(dark = false, halo = true)
        assertTrue(Halo.topAlpha(lightOn) > 0f)
        assertTrue(Halo.topAlpha(lightOn) < Halo.topAlpha(on))
    }

    @Test
    fun `les deux reglages traversent withFontScale, withColors et withDisplay`() {
        val base = theme(dark = true, pureBlack = true, halo = false)
        assertTrue(base.withFontScale(1.3f).pureBlack)
        assertFalse(base.withFontScale(1.3f).halo)
        assertTrue(base.withColors(ThemeColors()).pureBlack)
        assertFalse(base.withColors(ThemeColors()).halo)

        val flipped = base.withDisplay(pureBlack = false, halo = true)
        assertFalse(flipped.pureBlack)
        assertTrue(flipped.halo)
        assertEquals(base, base.withDisplay())
    }
}
