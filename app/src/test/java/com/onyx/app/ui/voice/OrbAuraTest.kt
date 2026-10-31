package com.onyx.app.ui.voice

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * L'orbe de la voix solo : **un seul halo doux**, jamais des cercles concentriques tracés.
 *
 * Le défaut relevé sur la capture du propriétaire : des anneaux empilés visibles, avec leurs
 * **raccords** et des **marches de couleur**, un **bord sombre franc** en bas à droite du disque
 * intérieur et un **liseré double** dur autour du disque. Trois sources :
 *
 *  - `ic_dark_glass`, une **image** posée par-dessus la lumière : son dégradé porte sa propre
 *    couture (le croissant bleu nuit en bas à droite), qu'aucun réglage d'alpha ne rattrape ;
 *  - un `border(3.dp)` **collé au bord du disque**, doublé par les `VoiceHalos` (des
 *    `drawCircle(style = Stroke)` empilés à faible opacité) : deux traits à quelques pixels l'un de
 *    l'autre se lisent comme un liseré double ;
 *  - l'**aura à plateau** (`startRadius: 66`), dont la rupture de pente se voit comme un bord.
 *
 * Ces tests tiennent les trois corrections : l'orbe est **dessiné** (aucune image), il n'y a plus
 * qu'**un** dégradé radial à arrêts progressifs, et sa courbe est **monotone** — donc sans marche.
 * Ce qui reste de l'ancien monde (les blobs de l'aura, les anneaux des faces-image) garde ses
 * garde-fous.
 */
class OrbAuraTest {

    private val blobs = File("src/main/java/com/onyx/app/ui/voice/DriftingBlobs.kt").readText()
    private val motion = File("src/main/java/com/onyx/app/ui/voice/VoiceLiveMotion.kt").readText()
    private val glass = File("src/main/java/com/onyx/app/ui/voice/VoiceOrbGlass.kt").readText()
    private val screen = File("src/main/java/com/onyx/app/ui/voice/VoiceScreen.kt").readText()

    @Test
    fun `l'aura de l'orbe est decoupee au cercle, avant la rotation`() {
        val clip = blobs.indexOf(".clip(CircleShape)")
        val rotate = blobs.indexOf(".graphicsLayer { rotationZ = rotation }")
        assertTrue("le calque de l'aura est découpé au cercle", clip >= 0)
        assertTrue("la rotation vient après la découpe", rotate > clip)
    }

    @Test
    fun `le bord de l'aura s'eteint avant le bord de la boite`() {
        assertTrue("le bord est fondu par un masque radial", blobs.contains("android.graphics.RadialGradient("))
        assertTrue("le masque s'applique en DST_IN", blobs.contains("PorterDuff.Mode.DST_IN"))
        assertTrue(
            "la dernière couleur du masque est transparente",
            blobs.contains("intArrayOf(android.graphics.Color.WHITE, android.graphics.Color.WHITE, 0)"),
        )
    }

    @Test
    fun `les halos des faces-image s ouvrent au-dela de l orbe, sans arete droite`() {
        val halos = motion.substringAfter("internal fun VoiceHalos(")
            .substringBefore("internal fun VoiceThinkingArc(")
        // Découpés au cercle, mesurés **sans contrainte** : les anneaux débordent donc du carré de
        // l'orbe — c'est ce que montre la référence, deux cercles bien plus larges que le disque.
        // Aucun ne touche le bord du cadre, où son opacité est nulle : jamais d'arête droite.
        assertTrue("les halos sont découpés au cercle", halos.contains(".clip(CircleShape)"))
        assertTrue(
            "le cadre se mesure sans contrainte",
            halos.contains(".wrapContentSize(align = Alignment.Center, unbounded = true)"),
        )
        assertTrue(
            "le cadre porte l'anneau le plus large",
            halos.contains(".requiredSize(diameter * HaloMaxScale)"),
        )
        assertTrue("l'anneau part à 0,95 du diamètre de base", halos.contains("HaloRestScale + travel * progress"))
    }

    @Test
    fun `l orbe solo ne porte plus de cercles concentriques traces`() {
        val orb = screen.substringAfter("private fun VoiceOrb(").substringBefore("private fun phaseTitle(")
        // Le `border(3.dp)` dur et les `VoiceHalos` empilés : ce qui faisait le liseré double.
        assertFalse("plus de liseré dur collé au disque", orb.contains(".border("))
        assertFalse("plus d'anneaux tracés dans l'orbe dessiné", orb.contains("VoiceHalos("))
        assertFalse("plus d'anneau de phase", orb.contains("OrbRingDiameter"))
        // À la place : un unique halo doux, une ombre douce, le disque de verre.
        assertTrue("l'orbe porte son halo unique", orb.contains("VoiceOrbHalo("))
        assertTrue("l'orbe porte l'ombre douce de la sphère", orb.contains("OrbSoftShadow("))
        assertTrue("l'orbe porte le disque de verre dessiné", orb.contains("LiquidOrbBody("))
        // Les faces-image gardent leurs anneaux (`Halos` de la référence) : elles ne sont pas touchées.
        val picture = screen.substringAfter("private fun OrbPictureFace(")
            .substringBefore("private fun OrbChooserRow(")
        assertTrue("la face-image garde ses anneaux", picture.contains("VoiceHalos("))
    }

    @Test
    fun `le halo unique est un seul degrade radial a arrets calcules et lisses`() {
        val halo = glass.substringAfter("internal fun VoiceOrbHalo(")
            .substringBefore("internal fun OrbSoftShadow(")
        assertTrue("un seul dégradé radial", halo.contains("Brush.radialGradient("))
        // Les arrêts sont **calculés** en 32 pas d'une courbe lisse, pas écrits à la main : une
        // poignée d'arrêts fait des **bandes de Mach** — l'œil y voit un anneau à chaque rupture de
        // pente alors que la couleur est continue. C'était le dernier défaut visible, et il se
        // mesurait : une marche de luminance en pleine courbe.
        assertTrue("32 pas", glass.contains("private const val HaloSteps = 32"))
        assertTrue("les arrêts sont calculés", halo.contains("orbHaloAlpha(t) * peak"))
        assertTrue("échantillonnés sur le rayon", halo.contains("t = index.toFloat() / HaloSteps"))
        assertTrue("la courbe s'annule sans pente", orbHaloAlpha(1f) == 0f)
        // Le dégradé est **mémorisé** : la voix ne le reconstruit pas à chaque image, c'est le
        // calque qui s'ouvre et s'éclaircit.
        assertTrue("le dégradé est mémorisé", halo.contains("remember(stops)"))
        assertTrue("c'est le calque qui suit la voix", halo.contains("graphicsLayer {"))
        assertFalse("plus de `drawArc` d'anneau", halo.contains("drawArc"))
    }

    @Test
    fun `la courbe du halo decroit sans marche`() {
        // Une extinctions linéaire entre deux arrêts fait une **marche** perceptible ; on vérifie
        // la propriété qui compte : décroissante, jamais de remontée, et pas d'à-coup supérieur à
        // ce que l'œil tolère sur une ligne radiale.
        var previous = Float.MAX_VALUE
        var biggestStep = 0f
        var t = 0f
        while (t <= 1.0001f) {
            val alpha = orbHaloAlpha(t)
            assertTrue("le halo ne remonte jamais (t=$t)", alpha <= previous + 1e-4f)
            if (previous != Float.MAX_VALUE) biggestStep = maxOf(biggestStep, previous - alpha)
            previous = alpha
            t += 0.002f
        }
        assertTrue("le halo s'éteint complètement", abs(orbHaloAlpha(1f)) < 1e-4f)
        assertTrue("le halo est plein au centre", orbHaloAlpha(0f) > 0.99f)
        assertTrue("aucune marche dans la courbe (pas > 0,06 par pas de 0,2 %)", biggestStep < 0.06f)
    }

    @Test
    fun `l orbe dessine n est plus une image`() {
        // L'image portait la couture (son propre croissant sombre) : il n'y a plus de bitmap du
        // tout dans l'orbe dessiné.
        assertFalse("plus de décodage d'image", glass.contains("BitmapFactory"))
        assertFalse("plus de dessin d'image", glass.contains("drawImage"))
        assertFalse("plus de PNG de verre", motion.contains("ic_dark_glass"))
        assertFalse("plus de découpe de barres figées", motion.contains("stripFrozenBars"))
        assertFalse("plus de colonnes de barres figées", motion.contains("frozenBarColumns"))
        // La face du disque suit la lumière allumée dedans (`GlassOrb.face`).
        assertTrue("la face dépend de la lumière", glass.contains("internal fun orbFaceColour(light: OrbLight)"))
    }

    @Test
    fun `le verre a sa finition, vignette, reflet speculaire et un seul lisere`() {
        val finishing = glass.substringAfter("internal fun OrbGlassFinishing(")
            .substringBefore("internal fun LiquidOrbBody(")
        assertTrue("la vignette assombrit le bord", finishing.contains("Color.Black.copy(alpha = 0.48f)"))
        assertTrue("le reflet spéculaire est en haut", finishing.contains("radius * 0.58f"))
        assertTrue("un reflet ténu", finishing.contains("Color.White.copy(alpha = 0.13f)"))
        // **Un seul** `drawCircle` en `Stroke` : un second à quelques pixels ferait le liseré double.
        val strokes = Regex("style = Stroke\\(").findAll(finishing).count()
        assertEquals("un seul trait de liseré", 1, strokes)
    }
}
