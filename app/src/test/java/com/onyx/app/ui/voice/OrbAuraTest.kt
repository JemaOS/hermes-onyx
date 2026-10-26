package com.onyx.app.ui.voice

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L'aura de l'orbe est un **cercle**, jamais un pavé.
 *
 * Le défaut relevé sur la capture: derrière l'orbe, l'aura à bords droits et coins carrés. Elle
 * venait du calque carré du « nebula » (taches décalées qui débordaient de leur boîte, dégradé
 * encore vif au bord) et de l'anneau des halos qui sortait de la sienne. Ces tests tiennent les
 * deux: découpe au cercle — avant la rotation — et extinction du bord, halos qui restent dedans.
 */
class OrbAuraTest {

    private val blobs = File("src/main/java/com/onyx/app/ui/voice/DriftingBlobs.kt").readText()
    private val motion = File("src/main/java/com/onyx/app/ui/voice/VoiceLiveMotion.kt").readText()
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
    fun `les halos s ouvrent au-dela de l orbe, sans arete droite`() {
        val halos = motion.substringAfter("internal fun VoiceHalos(")
            .substringBefore("internal fun VoiceThinkingArc(")
        // Découpés au cercle, mesurés **sans contrainte**: les anneaux débordent donc du carré de
        // l'orbe — c'est ce que montre le modèle, deux cercles bien plus larges que le disque.
        // Aucun ne touche le bord du cadre, où son opacité est nulle: jamais d'arête droite.
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
    fun `l orbe solo porte ses trois anneaux`() {
        val orb = screen.substringAfter("private fun VoiceOrb(").substringBefore("private fun phaseTitle(")
        // Le premier: l'anneau de phase collé au bord du disque, `lineWidth: 3`, éteint au repos.
        assertTrue(orb.contains(".size(OrbRingDiameter.dp)"))
        assertTrue(
            orb.contains("color = phaseColor.copy(alpha = if (phase == VoicePhase.IDLE) 0f else 0.8f)"),
        )
        // Les deux autres: les halos, à la base `GlassOrb.haloDiameter`.
        assertTrue(orb.contains("diameter = OrbHaloDiameter.dp"))
        // Le halo du visage-image est découpé au cercle, comme l'aura de la salle.
        assertTrue(screen.contains("CircleShape,"))
    }

    @Test
    fun `l orbe solo porte le halo diffus de la reference`() {
        // `OrbFace`: l'aura passe **avant** le choix de la face — un seul halo diffus, dans la
        // couleur de la phase, large et vif à l'écoute (297 pt + la voix), ténu au repos (216 pt).
        // La voix solo ne l'avait pas: il ne lui restait que ses cercles nets sur le noir.
        val drawn = screen.substringAfter("private fun VoiceOrb(").substringBefore("private fun phaseTitle(")
        val picture = screen.substringAfter("private fun OrbPictureFace(")
            .substringBefore("private fun OrbChooserRow(")
        for ((nom, bloc) in listOf("l'orbe dessiné" to drawn, "une face-image" to picture)) {
            assertTrue("$nom porte l'aura", bloc.contains("Brush.radialGradient("))
            assertTrue("$nom la découpe au cercle", bloc.contains("CircleShape,"))
            assertTrue(
                "$nom reprend les mesures de la référence",
                bloc.contains("297f + amplitude * 55f else 216f") &&
                    bloc.contains("143f + amplitude * 44f else 110f"),
            )
            assertTrue(
                "$nom la laisse ténue hors écoute",
                bloc.contains("if (listening) 0.45f else 0.16f"),
            )
            // `startRadius: 66`: la lueur tient jusqu'à 66 points avant de s'éteindre — sans ce
            // plateau elle s'éteignait avant le bord du disque et ne laissait qu'un liseré.
            assertTrue(
                "$nom garde le plateau de la référence",
                bloc.contains("VoiceAuraPlateau / auraRadius to"),
            )
        }
    }
}
