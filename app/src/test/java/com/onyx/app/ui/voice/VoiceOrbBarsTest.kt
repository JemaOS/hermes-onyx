package com.onyx.app.ui.voice

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La vague de l'orbe : **sept** points, qui réagissent au niveau réel du micro, avec une
 * **ondulation qui se propage**, des hauteurs différentes à un même instant, des bouts arrondis et
 * un lissage. Au repos : une petite respiration. Sous Reduce Motion : figée.
 *
 * Ce qui était cassé (capture du propriétaire : « sept points parfaitement statiques de taille
 * identique ») :
 *
 *  - le disque était une **image** dont il fallait découper les sept barres figées, et le découpage
 *    laissait des restes (« deux mini barres verticales » aux extrémités) : trouvé dans l'image,
 *    pas supposé — au prix d'un balayage de pixels au chargement. Le disque étant maintenant
 *    **calculé**, il n'y a plus rien à découper : le balayage et ses pièges disparaissent ;
 *  - l'ondulation **ne tournait pas** pendant la parole (`drive == nil` la gardait) : les sept
 *    points montaient ensemble, à la même hauteur ;
 *  - au repos, la seule ondulation faisait 3 pt : sept points identiques et immobiles.
 *
 * Les tests ci-dessous portent sur la **géométrie pure** (`liveWaveHeight`, `liveWaveRipple`), que
 * la composition appelle telle quelle : c'est elle qui décide les hauteurs, et une capture juge le
 * reste.
 */
class VoiceOrbBarsTest {

    private val motion = File("src/main/java/com/onyx/app/ui/voice/VoiceLiveMotion.kt").readText()

    private fun heights(drive: Float, rippleTime: Float, breatheTime: Float): FloatArray =
        FloatArray(LiveWaveWeights.size) { liveWaveHeight(it, drive, rippleTime, breatheTime) }

    @Test
    fun `la vague garde ses sept points, comme la reference`() {
        // Sept poids, comme `LiveWaveform.weights` de la référence : retirer les deux extrêmes avait
        // raccourci la vague (7 → 5) sans faire partir les deux traits, qui ne venaient pas d'elle.
        assertEquals("sept points", 7, LiveWaveWeights.size)
        assertTrue(motion.contains("floatArrayOf(0.13f, 0.27f, 0.51f, 1f, 0.51f, 0.27f, 0.13f)"))
        assertTrue("plus de poids retirés", !motion.contains("drop(1).dropLast(1)"))
    }

    @Test
    fun `avec la voix les sept points ne sont pas a la meme hauteur`() {
        val drive = 0.8f
        val heights = heights(drive, 1.0f, 0f)
        assertEquals("le centre est le plus haut", heights.max(), heights[3], 0.01f)
        for (index in 0 until 3) {
            assertTrue(
                "le point ${index + 1} est plus bas que le suivant",
                heights[index] < heights[index + 1] - 0.5f,
            )
        }
        // Le **profil** est symétrique (les poids se répondent)…
        for (index in 0 until 3) {
            assertEquals(LiveWaveWeights[index], LiveWaveWeights[6 - index], 0.0001f)
        }
        // …mais à un instant donné les hauteurs **ne le sont pas** : c'est l'ondulation qui passe.
        // Deux points de même poids (1 et 5) sont donc à des hauteurs différentes.
        assertNotEquals(
            "l'ondulation casse la symétrie à l'instant",
            heights[1],
            heights[5],
            0.5f,
        )
        assertTrue("le centre monte vraiment", heights[3] > 40f)
    }

    @Test
    fun `l ondulation se propage d un point au suivant`() {
        // Le premier point reçoit la crête à t = 0,3 s, le dernier à t = 1,02 s.
        assertTrue("le premier point est haut avant le dernier", liveWaveRipple(0, 0.30f) > liveWaveRipple(6, 0.30f) + 0.4f)
        assertTrue("le dernier point est haut après le premier", liveWaveRipple(6, 1.02f) > liveWaveRipple(0, 1.02f) + 0.4f)
        // Le sommet de chacun arrive bien plus tard que celui du précédent.
        for (index in 0 until 6) {
            val here = (0..40).maxBy { liveWaveRipple(index, it * 0.05f) } * 0.05f
            val next = (0..40).maxBy { liveWaveRipple(index + 1, it * 0.05f) } * 0.05f
            assertTrue("le point ${index + 2} suit le point ${index + 1}", next > here + 0.05f)
        }
        // L'ondulation retombe à zéro avant la fin de la période : au redémarrage il reste moins de
        // 0,03 pt, invisible sur un disque de 190.
        assertTrue("rien au redémarrage", liveWaveRipple(0, 5f) < 0.05f && liveWaveRipple(6, 5f) < 0.05f)
        assertTrue("rien juste avant", liveWaveRipple(0, 4.99f) < 0.01f)
        assertTrue("le pas est invisible", liveWaveRipple(0, 5f) - liveWaveRipple(0, 0f) < 0.01f)
    }

    @Test
    fun `l ondulation grandit avec la voix`() {
        // Au repos 3 pt, avec la voix 10 pt : c'est elle qui distingue les hauteurs quand le niveau
        // du micro est stable.
        val atRest = heights(0f, 0.30f, 0f)[0] - heights(0f, 2.5f, 0f)[0]
        val loud = heights(1f, 0.30f, 0f)[0] - heights(1f, 2.5f, 0f)[0]
        assertTrue("l'ondulation du repos est petite mais non nulle", atRest > 1f && atRest < 5f)
        assertTrue("l'ondulation grandit avec la voix", loud > atRest * 2f)
    }

    @Test
    fun `au repos la vague respire, et chaque point a son temps`() {
        // Sans voix, la houle lente fait vivre les sept points — ce que la capture montrait figé.
        val a = heights(0f, 0f, 0f)
        val b = heights(0f, 0f, 1.2f)
        assertNotEquals("la vague change entre deux instants du repos", a[0], b[0], 0.2f)
        // Et les points ne montent pas tous ensemble : le décalage de phase les sépare.
        val spread = heights(0f, 0f, 0.9f)
        assertTrue("les points du repos ne sont pas à la même hauteur", spread.max() - spread.min() > 0.5f)
        // La respiration reste petite : la vague ne devient pas une barre.
        assertTrue("respiration discrète", spread.max() < 10f)
    }

    @Test
    fun `sous reduce motion la vague est figee`() {
        // Aucune horloge ne tourne et le niveau n'est pas lu : les sept points restent au repos, à
        // la même hauteur — comme le `TimelineView(paused:)` de la référence.
        val frozen = FloatArray(LiveWaveWeights.size) {
            liveWaveHeight(it, 0f, 0f, 0f, animate = false)
        }
        for (height in frozen) assertEquals(6f, height, 0.001f)
        assertEquals("la vague figée est bien sept points", 7, frozen.size)
    }

    @Test
    fun `le niveau du micro est lisse, pas suivi trame a trame`() {
        assertTrue("le lissage existe", motion.contains("rememberSmoothedLevel("))
        // Montée vive (~45 ms) et retombée douce (~160 ms) : le contraire fait claquer la vague.
        assertTrue("montée vive", motion.contains("if (target > current) 0.045f else 0.16f"))
        assertTrue("lissage exponentiel", motion.contains("1f - exp(-dt / time)"))
        // Le niveau lissé est lu dans le **dessin** : une trame n'invalide que la vague.
        assertTrue("le niveau est lu au dessin", motion.contains("val rawLevel = if (live && !reduceMotion) smoothed.value else 0f"))
    }

    @Test
    fun `les points ont des bouts arrondis et un fond legerement fondu`() {
        assertTrue(
            "des capsules (rayon = demi-largeur)",
            motion.contains("cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)"),
        )
        assertTrue("un dégradé qui suit le disque", motion.contains("Brush.verticalGradient"))
        assertTrue("le bas se fond", motion.contains("LiveWaveColor.copy(alpha = LiveWaveBottomAlpha)"))
        // Aucune découpe `Clear` hors écran : plus de carrés ni de points colorés autour des barres.
        assertTrue("plus de découpe au Clear", !motion.contains("BlendMode.Clear"))
        assertTrue("plus de composition hors écran", !motion.contains("CompositingStrategy"))
    }
}
