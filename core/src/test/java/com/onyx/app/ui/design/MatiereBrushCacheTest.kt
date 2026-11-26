package com.onyx.app.ui.design

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **La matière et le halo ne se reconstruisent pas à chaque image.**
 *
 * La matière de la page est un **fond fixe** : elle ne dépend que de l'apparence et de la taille.
 * La reconstruire à chaque trame — un dégradé, quatre taches radiales, un vignettage et un
 * `ImageShader` **natif** — alors que la seule chose qui bouge est le fil qui défile au-dessus,
 * est du travail jeté : mesuré au défilement d'un fil dense, le thread UI passait de **0,9 ms** en
 * médiane avec les brosses gardées à **5,7 ms** sans.
 *
 * Un `ImageShader` est un `SkShader` : sa construction passe par la couche native (JNI) et
 * l'enveloppe native qui l'accompagne est allouée à chaque construction. À 120 images par seconde,
 * cette seule couche fait des milliers d'allocations natives par seconde — de la pression sur le
 * GC qui se paie en saccades, exactement comme le défaut des quarante mille rectangles du grain
 * avant lui.
 *
 * Ce test verrouille **le mécanisme** : les brosses sont construites dans un `drawWithCache`, donc
 * une fois par taille, et non dans le corps de dessin. Il verrouille aussi que la matière
 * **continue de se dessiner** — un cache mal branché donnerait une page vide, ce qui serait pire
 * qu'un page lente.
 */
class MatiereBrushCacheTest {

    private fun source(path: String): String =
        listOf(File(path), File("../$path")).firstOrNull { it.isFile }?.readText() ?: ""

    private val material = source("core/src/main/java/com/onyx/app/ui/design/Material.kt")
    private val halo = source("core/src/main/java/com/onyx/app/ui/design/Halo.kt")

    @Test
    fun `la matiere garde ses brosses au lieu de les reconstruire a chaque image`() {
        assertTrue(
            "Material.kt ne dessine plus la matière : le fond de page a perdu sa matière, " +
                "et le verre n'a plus rien à montrer.",
            material.contains("fun DrawScope.drawMatiere(brushes: MatiereBrushes)"),
        )
        assertTrue(
            "La matière n'est plus posée par `drawWithCache` : ses brosses sont donc " +
                "reconstruites à chaque trame — c'est le défaut mesuré (thread UI à 5,7 ms " +
                "au lieu de 0,9 ms).",
            material.contains("): Modifier = drawWithCache {"),
        )
        assertTrue(
            "La matière doit être dessinée **derrière** le contenu de la surface : c'est ce qui " +
                "laisse le verre l'échantillonner.",
            material.contains("onDrawBehind {"),
        )
    }

    @Test
    fun `le grain n est plus un shader natif reconstruit a chaque image`() {
        // L'ImageShader doit être construit **une fois**, dans les brosses gardées — pas dans le
        // corps du dessin, où il serait recréé à chaque trame.
        val grainIndex = material.indexOf("val grain: androidx.compose.ui.graphics.ShaderBrush")
        assertTrue(
            "Les brosses gardées ne contiennent pas le grain : le carreau de grain n'est plus " +
                "posé une fois pour toutes.",
            grainIndex >= 0,
        )
        // Le corps de dessin ne doit plus construire de shader : il ne fait que s'en servir.
        val drawBody = material.substringAfter("private fun DrawScope.drawMatiere(brushes: MatiereBrushes)")
        assertFalse(
            "Le corps de dessin reconstruit encore un `ImageShader` : l'allocation native " +
                "par image est revenue.",
            drawBody.contains("ImageShader("),
        )
        assertTrue(
            "Le corps de dessin doit se servir du grain gardé.",
            drawBody.contains("brushes.grain"),
        )
    }

    @Test
    fun `le halo garde son degrade`() {
        assertTrue(
            "Halo.kt ne pose plus le halo par `drawWithCache` : son dégradé est reconstruit à " +
                "chaque trame.",
            halo.contains("): Modifier = drawWithCache {"),
        )
        assertTrue(
            "Le halo doit être dessiné derrière le contenu.",
            halo.contains("onDrawBehind {"),
        )
    }

    @Test
    fun `la matiere est toujours dessinee, couche par couche`() {
        // Un cache mal branché (une taille nulle, une brosse absente) donnerait une page vide :
        // quatre couches doivent rester dessinées, et dans le même ordre.
        val body = material.substringAfter("private fun DrawScope.drawMatiere(brushes: MatiereBrushes)")
        val fond = body.indexOf("brushes.fond")
        val blobs = body.indexOf("brushes.blobs")
        val grain = body.indexOf("brushes.grain")
        val vignette = body.indexOf("brushes.vignette")
        assertTrue("La couche du fond n'est plus dessinée.", fond >= 0)
        assertTrue("Les formes floues ne sont plus dessinées.", blobs >= 0)
        assertTrue("Le grain n'est plus dessiné.", grain >= 0)
        assertTrue("Le vignettage n'est plus dessiné.", vignette >= 0)
        assertTrue(
            "Les quatre couches de la matière ne sont plus dans l'ordre (fond, formes, grain, " +
                "vignettage).",
            fond < blobs && blobs < grain && grain < vignette,
        )
    }
}
