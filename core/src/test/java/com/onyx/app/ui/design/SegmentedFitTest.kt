package com.onyx.app.ui.design

import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La règle qui empêche un libellé de contrôle segmenté d'être **coupé**.
 *
 * Le défaut vu deux fois sur l'écran d'accueil : à police 1,3× sur 360 dp, la dernière case était
 * découpée (un fragment de bordure lu comme un trait parasite). La taille descend donc jusqu'à ce
 * que le plus large libellé tienne, jamais au-dessous du plancher.
 */
class SegmentedFitTest {

    @Test
    fun `la taille nominale est gardee quand tout tient`() {
        // 360 dp, quatre cases : environ 74 dp de texte par case, quatre libellés courts.
        val size = SegmentedFit.fontSize(74f) { fontSize -> fontSize * 3.3f }
        assertEquals(SegmentedFit.BASE_SP, size, 0.001f)
    }

    @Test
    fun `la taille descend juste assez pour faire tenir le plus large`() {
        // Un libellé de 5,2 fois la taille : 16 → 83,2 dp (trop), 15 → 78 (trop), 12 → 62,4 (tient).
        // Les marches sont les **corps du socle** : 16 → 15 → 12 → 11, jamais un demi-point.
        val size = SegmentedFit.fontSize(74f) { fontSize -> fontSize * 5.2f }
        assertEquals(12f, size, 0.001f)
        assertTrue("le libellé doit tenir", 5.2f * size <= 74f)
        listOf(40f, 52f, 60f, 74f, 90f, 120f, 200f).forEach { available ->
            val s = SegmentedFit.fontSize(available) { it * 4.7f }
            assertTrue(s.toString() + " sp hors échelle", OnyxSocle.isOnScale(s.sp))
            assertTrue(s.toString() + " sp sous le plancher", s >= SegmentedFit.FLOOR_SP)
        }
    }

    @Test
    fun `une case plus etroite donne une taille plus petite ou egale`() {
        // Le libellé fait 3,3 fois la taille : entre 74 dp et 52 dp par case, la seconde doit
        // réduire la taille, et le libellé doit encore tenir.
        val wide = SegmentedFit.fontSize(74f) { it * 3.3f }
        val narrow = SegmentedFit.fontSize(52f) { it * 3.3f }
        assertTrue(narrow <= wide)
        assertTrue(3.3f * narrow <= 52f)
        assertEquals(SegmentedFit.BASE_SP, wide, 0.001f)
    }

    @Test
    fun `le plancher tient meme si rien ne peut tenir`() {
        assertEquals(SegmentedFit.FLOOR_SP, SegmentedFit.fontSize(20f) { 10_000f }, 0.001f)
    }

    @Test
    fun `le degagement laisse de la place au texte et jamais zero`() {
        assertEquals(74f, SegmentedFit.availableWidthDp(80f), 0.001f)
        assertTrue(SegmentedFit.availableWidthDp(2f) > 0f)
    }
}
