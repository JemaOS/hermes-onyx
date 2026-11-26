package com.onyx.app.data.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le décodage d'image reste **borné**, quelle que soit l'image.
 *
 * Le défaut reproduit : une photo de 8000×6000 jointe à une conversation de bot tuait l'app —
 * `RuntimeException: Canvas: trying to draw too large(192000000bytes) bitmap` sur le fil
 * principal — et l'app se relançait sur la même bulle pour remourir. La cause n'est pas la
 * taille du fichier mais celle du `Bitmap` : 8000×6000×4 octets, décodé en pleine résolution
 * pour une vignette de 64 dp.
 *
 * Ces tests tiennent l'invariant qui empêche la classe entière de revenir : pour n'importe
 * quelles dimensions annoncées, le plan de décodage tient dans le budget, et ce qu'il produirait
 * peut être dessiné.
 */
class BoundedImageTest {

    private val thumb = 192
    private val viewer = 2048

    @Test
    fun `une photo de 8000x6000 est reduite pour une vignette`() {
        val plan = BoundedImage.plan(8000, 6000, thumb)
        assertNotNull("un plan doit exister pour 8000×6000", plan)
        requireNotNull(plan)

        assertTrue("le facteur de réduction est réel", plan.sampleSize > 1)
        assertTrue("la vignette tient largement sous le budget", plan.bytes < 2L * 1024 * 1024)
        assertTrue("elle peut être dessinée", plan.drawable)
        assertTrue("elle ne garde pas la résolution d'origine", plan.width < 8000 && plan.height < 6000)
    }

    @Test
    fun `la source du plantage ne peut plus produire un bitmap non dessinable`() {
        // 192 000 000 octets : exactement ce que le moteur de rendu a refusé.
        assertFalse(BoundedImage.canDraw(8000L * 6000L * 4))
        assertFalse(BoundedImage.canDraw(192_000_000L))
        assertTrue(BoundedImage.canDraw(1920L * 1080L * 4))
    }

    @Test
    fun `aucune dimension, meme absurde, ne sort du budget`() {
        val sources = listOf(
            8000 to 6000,      // la photo du défaut
            4000 to 3000,      // une photo de téléphone ordinaire
            12_000 to 9_000,   // un scan
            40_000 to 30_000,  // une image « pour analyse »
            100_000 to 100_000,
            1000 to 20_000,    // très haute et étroite
            20_000 to 1000,    // très large et plate
        )
        for ((width, height) in sources) {
            for (target in listOf(96, 192, 512, 2048)) {
                val plan = BoundedImage.plan(width, height, target) ?: continue
                assertTrue(
                    "$width×$height à $target px : ${plan.bytes} octets dépassent le budget",
                    plan.bytes <= BoundedImage.MAX_BYTES,
                )
                assertTrue(
                    "$width×$height à $target px : le plan n'est pas dessinable (${plan.bytes} octets)",
                    plan.drawable,
                )
                assertTrue("un plan garde au moins un pixel", plan.width >= 1 && plan.height >= 1)
            }
        }
    }

    @Test
    fun `le lecteur d'image reste sous son budget et sous le plafond de dessin`() {
        val budget = 24 * 1024 * 1024
        val plan = BoundedImage.plan(8000, 6000, viewer, budget)
        assertNotNull(plan)
        requireNotNull(plan)
        assertTrue("le lecteur ne garde pas 192 Mo", plan.bytes <= budget)
        assertTrue(plan.bytes < BoundedImage.MAX_DRAW_BYTES)
    }

    @Test
    fun `un carre qui depasserait le budget descend d'un cran`() {
        // 2049×2049 = 16,79 Mo : au-dessus du budget d'une vignette de 16 Mo.
        val plan = BoundedImage.plan(2049, 2049, 2049, BoundedImage.MAX_BYTES)
        assertNotNull(plan)
        requireNotNull(plan)
        assertTrue(plan.bytes <= BoundedImage.MAX_BYTES)
        assertTrue("il a fallu réduire encore", plan.sampleSize >= 2)
    }

    @Test
    fun `une dimension nulle est refusee au lieu de decoder`() {
        assertNull(BoundedImage.plan(0, 6000, thumb))
        assertNull(BoundedImage.plan(8000, 0, thumb))
        assertNull(BoundedImage.plan(-1, 6000, thumb))
        assertEquals(1, BoundedImage.sampleSize(0, 0, thumb))
    }

    @Test
    fun `un jpeg coupe est reconnu comme tronque`() {
        // 8000×6000 réduit à 500×375 pour une vignette : un fichier coupé aux deux tiers ne rend
        // que les premières bandes, par exemple 500×180.
        assertTrue(BoundedImage.isTruncated(500, 375, 500, 180))
        assertTrue("une largeur écourtée aussi", BoundedImage.isTruncated(500, 375, 200, 375))
        assertFalse("une image entière n'est pas tronquée", BoundedImage.isTruncated(500, 375, 500, 375))
        assertFalse("un arrondi d'une ligne n'est pas une troncature", BoundedImage.isTruncated(500, 375, 500, 374))
        assertFalse("des dimensions inconnues ne condamnent pas", BoundedImage.isTruncated(0, 0, 500, 375))
    }

    @Test
    fun `une petite image garde sa resolution`() {
        val plan = BoundedImage.plan(120, 90, thumb)
        assertNotNull(plan)
        requireNotNull(plan)
        assertEquals(1, plan.sampleSize)
        assertEquals(120, plan.width)
        assertEquals(90, plan.height)
    }
}
