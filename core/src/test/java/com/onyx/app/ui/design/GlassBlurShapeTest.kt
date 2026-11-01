package com.onyx.app.ui.design

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **La vitre se découpe à sa forme, jamais à un rectangle.**
 *
 * Le voile de Haze — le fond de page plus le flou — était découpé au **rectangle** des limites de
 * la surface. Sur une plaque à grand rayon (le compositeur, dont le rayon est celui de la plaque),
 * ce voile débordait donc des coins arrondis, et le propriétaire le lisait pour ce qu'il est : « un
 * rectangle transparent superposé sur la partie prompt, ça fait moche et pas unifié ».
 *
 * La réfraction, elle, posait déjà la forme sur son style ; c'est cette **symétrie** que le verrou
 * exige — les deux chemins de peinture du verre ne peuvent pas décrire deux formes différentes.
 */
class GlassBlurShapeTest {

    private val source = File("src/main/java/com/onyx/app/ui/design/LiquidGlass.kt").readText()

    @Test
    fun `le voile du flou suit la forme de la surface`() {
        assertTrue(
            "le style de flou reçoit la forme de la surface",
            source.contains("private fun glassBlurStyle(\n    surfaceShape: Shape,"),
        )
        assertTrue(
            "et c'est elle qu'il découpe",
            source.contains("blurredEdgeTreatment(BlurredEdgeTreatment(surfaceShape))"),
        )
        assertFalse(
            "plus de découpe rectangulaire : c'était le rectangle posé sur la plaque",
            source.contains("blurredEdgeTreatment(BlurredEdgeTreatment.Rectangle)"),
        )
    }

    @Test
    fun `le flou est appele avec la forme de la vitre`() {
        assertTrue(
            "l'appel transmet la forme de la surface",
            source.contains("style = glassBlurStyle(shape, blurDp, tint, tintAlpha, scrim, page, level),"),
        )
    }
}
