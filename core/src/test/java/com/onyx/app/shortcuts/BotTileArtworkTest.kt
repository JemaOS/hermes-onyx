package com.onyx.app.shortcuts

import com.onyx.app.ui.theme.ResolvedTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les tuiles du menu d'appui long : la tuile d'un bot et l'encre qui s'y lit.
 *
 * Le dessin lui-même se voit à l'émulateur (un `Bitmap` ne se peint pas dans un essai JVM) ; ici
 * on tient ce qui décide de la tuile : son initiale, sa couleur — celle du visage du bot, la même
 * que partout dans l'app — et l'encre qui reste lisible dessus.
 */
class BotTileArtworkTest {

    @Test
    fun `l initiale est la premiere lettre ou le premier chiffre, en capitale`() {
        assertEquals("K", BotTileArtwork.initialFor("Kilo"))
        assertEquals("H", BotTileArtwork.initialFor("hermes"))
        assertEquals("3", BotTileArtwork.initialFor("3d-printer"))
        assertEquals("Ø", BotTileArtwork.initialFor("ø-conseil"))
    }

    @Test
    fun `un libelle sans lettre garde un repere`() {
        // Le premier caractère **utile** : une lettre ou un chiffre prime sur la ponctuation.
        assertEquals("Q", BotTileArtwork.initialFor("@qa"))
        assertEquals("S", BotTileArtwork.initialFor("- scout"))
        assertEquals("*", BotTileArtwork.initialFor("***"))
        assertEquals("•", BotTileArtwork.initialFor("   "))
        assertEquals("•", BotTileArtwork.initialFor(""))
    }

    @Test
    fun `la tuile ne reste jamais nue`() {
        assertEquals("Q", BotTileArtwork.initialFor("  qa tester  "))
        assertEquals("É", BotTileArtwork.initialFor("élan"))
    }

    @Test
    fun `l encre d une tuile profonde est claire et celle d une tuile claire est sombre`() {
        assertEquals(BotTileArtwork.LIGHT_INK, BotTileArtwork.inkOn(0xFF2F6BF5.toInt()))
        assertEquals(BotTileArtwork.DARK_INK, BotTileArtwork.inkOn(0xFFDBDBDB.toInt()))
        assertEquals(BotTileArtwork.DARK_INK, BotTileArtwork.inkOn(0xFFFFFFFF.toInt()))
        assertEquals(BotTileArtwork.LIGHT_INK, BotTileArtwork.inkOn(0xFF101014.toInt()))
    }

    @Test
    fun `un visage blanc recoit une encre sombre, un visage profond une encre claire`() {
        // Un bot blanc (`BotFace.white`) et un bot noir : les deux cas qui décident de l'encre.
        assertEquals(BotTileArtwork.LIGHT_INK, BotTileArtwork.inkOn(0xFF070707.toInt()))
        assertEquals(BotTileArtwork.DARK_INK, BotTileArtwork.inkOn(0xFFF0F0F0.toInt()))
    }

    @Test
    fun `la luminance est celle des canaux du sRGB`() {
        assertEquals(0f, BotTileArtwork.luminance(0xFF000000.toInt()), 0.0001f)
        assertEquals(1f, BotTileArtwork.luminance(0xFFFFFFFF.toInt()), 0.0001f)
        assertTrue(BotTileArtwork.luminance(0xFF7F7F7F.toInt()) in 0.2f..0.25f)
    }

    @Test
    fun `la tuile d un bot prend la couleur de son visage`() {
        val theme = ResolvedTheme.fallback()
        val kilo = BotTileArtwork.botColour("kilo", null, theme)
        val coder = BotTileArtwork.botColour("coder", null, theme)
        assertNotEquals(kilo, coder)
        // La même entrée donne toujours la même tuile.
        assertEquals(kilo, BotTileArtwork.botColour("kilo", null, theme))
    }

    @Test
    fun `un code de visage enregistre decide de la couleur, et une tuile blanche prend une encre sombre`() {
        val theme = ResolvedTheme.fallback()
        val own = BotTileArtwork.botColour("kilo", null, theme)
        // Le visage **blanc** de la palette : sa tuile est claire, l'initiale doit y être sombre.
        val whiteFace = com.onyx.app.ui.design.BotFace(colour = com.onyx.app.ui.design.BotFace.white).code()
        val white = BotTileArtwork.botColour("kilo", whiteFace, theme)
        assertNotEquals("un code de visage enregistré décide", own, white)
        assertEquals(BotTileArtwork.DARK_INK, BotTileArtwork.inkOn(white))
    }

    @Test
    fun `le degre clair et le profond d une meme tuile different`() {
        val theme = ResolvedTheme.fallback()
        val light = BotTileArtwork.botLightColour("kilo", null, theme)
        val deep = BotTileArtwork.botColour("kilo", null, theme)
        assertNotEquals(light, deep)
        assertTrue(
            "le haut du dégradé est plus lumineux que le bas",
            BotTileArtwork.luminance(light) > BotTileArtwork.luminance(deep),
        )
    }

    @Test
    fun `la forme est celle des tuiles d action`() {
        assertEquals(0.22f, BotTileArtwork.CORNER_FRACTION, 0.0001f)
        assertTrue("le glyphe tient dans le disque de sûreté", BotTileArtwork.SAFE_RADIUS_FRACTION <= 0.75f)
    }
}
