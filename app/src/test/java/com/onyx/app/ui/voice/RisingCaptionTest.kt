package com.onyx.app.ui.voice

import androidx.compose.ui.unit.dp
import com.onyx.app.ui.design.OnyxMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La légende mot à mot (`RisingCaption`, ).
 *
 * Les mesures du flux centré et le découpage des mots sont les deux parties qui se testent sans
 * écran; le reste (l'apparition d'un mot) est une courbe de [OnyxMotion], vérifiée par
 * `MotionTableTest`.
 */
class RisingCaptionTest {

    @Test
    fun `les mots se decoupent sur les espaces seuls, comme Text(split(separator -))`() {
        assertEquals(listOf("Go", "ahead.", "I'm", "listening"), captionWords("Go ahead. I'm listening"))
        // Les vides sont écartés, comme `split(separator: " ")`.
        assertEquals(listOf("two", "words"), captionWords("  two   words  "))
        assertEquals(emptyList<String>(), captionWords("   "))
        // La ponctuation colle au mot: rien n'est redistribué.
        assertEquals(listOf("a,b", "c"), captionWords("a,b c"))
    }

    @Test
    fun `le flux remplit la ligne tant que le mot tient, puis en ouvre une autre`() {
        // 6 pt d'écart: des mots de 100, 100 puis 100 sur 250 de large.
        assertEquals(
            listOf(listOf(0, 1), listOf(2)),
            captionRows(widths = listOf(100, 100, 100), available = 250, spacing = 12),
        )
        // Un seul mot trop large occupe sa ligne, seul.
        assertEquals(
            listOf(listOf(0), listOf(1)),
            captionRows(widths = listOf(400, 50), available = 250, spacing = 12),
        )
        // Tout tient: une seule ligne.
        assertEquals(
            listOf(listOf(0, 1, 2)),
            captionRows(widths = listOf(20, 20, 20), available = 250, spacing = 12),
        )
        assertEquals(listOf(listOf(0)), captionRows(widths = listOf(10), available = 250, spacing = 12))
    }

    @Test
    fun `le flux garde les mesures `() {
        assertEquals(6.dp, CaptionWordSpacing)
        assertEquals(2.dp, CaptionLineSpacing)
        assertEquals(8.dp, CaptionWordRise)
    }

    @Test
    fun `le mot monte en easeOut 0,25 comme la valeur de words count`() {
        // `OnyxMotion.Sites.CaptionWord` est un `tween`; on vérifie la durée posée dans.
        assertTrue(
            "la légende doit être un tween de 250 ms",
            OnyxMotion.Sites.CaptionWord is androidx.compose.animation.core.TweenSpec<*>,
        )
        val spec = OnyxMotion.Sites.CaptionWord as androidx.compose.animation.core.TweenSpec<*>
        assertEquals(250, spec.durationMillis)
    }
}
