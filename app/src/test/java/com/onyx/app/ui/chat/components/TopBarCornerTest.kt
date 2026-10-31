package com.onyx.app.ui.chat.components

import java.io.File
import com.onyx.app.ui.design.SurfaceRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **La barre du haut a des coins arrondis — parce qu'elle est détachée des bords et qu'elle prend la
 * forme de sa surface.**
 *
 * Le défaut « les bords ne sont pas arrondis » a deux moitiés, et une seule ne suffit pas :
 *
 * - **la forme** : la barre ne passe plus de forme à `liquidGlass` — elle prend celle de
 *   `GlassSurface.THREAD_TOP_BAR` (22 dp, la famille des barres). Une forme arrondie est donc peinte
 *   *et* le liseré la suit (`drawGlassEdge`) ;
 * - **la détache** : une barre collée aux deux bords de l'écran aurait ses coins arrondis **sur** le
 *   bord, et se lirait comme un rectangle rogné. La marge latérale ([TopBarSideMargin], 12 dp — la
 *   même que la barre d'onglets) la décolle, exactement comme la carte du compositeur et la barre
 *   d'onglets.
 *
 * Le test échoue si la marge disparaît, si elle est posée **après** la vitre (elle ne servirait
 * alors à rien), ou si la barre se remet à peindre un rectangle franc.
 */
class TopBarCornerTest {

    private fun read(path: String): String =
        listOf(File(path), File("../$path")).firstOrNull { it.isFile }?.readText()
            ?: error("fichier introuvable : $path")

    private val topBar = read("src/main/java/com/onyx/app/ui/chat/components/OnyxTopBar.kt")
    private val glass = read("core/src/main/java/com/onyx/app/ui/design/LiquidGlass.kt")

    @Test
    fun `la barre du haut est detachee des bords`() {
        assertTrue(
            "la marge latérale doit exister et nommer sa valeur",
            topBar.contains("internal val TopBarSideMargin = 12.dp"),
        )
        val margin = topBar.indexOf(".padding(horizontal = TopBarSideMargin)")
        val vitre = topBar.indexOf(".liquidGlass(GlassSurface.THREAD_TOP_BAR, SurfaceRole.Bar.shape)")
        assertTrue("la marge doit être posée", margin > 0)
        assertTrue("la vitre doit être posée", vitre > 0)
        assertTrue(
            "la marge doit venir AVANT la vitre (marge=$margin, vitre=$vitre) : après, la vitre " +
                "couvre toujours le bord et les coins arrondis tombent dessus",
            margin < vitre,
        )
    }

    @Test
    fun `la barre du haut prend la forme de son role, pas d un nombre`() {
        // La barre ne se fabrique **aucune** forme : elle prend celle de son **rôle**
        // ([SurfaceRole.Bar]) — une capsule, la même que la barre d'onglets posée juste en dessous.
        // L'interdit d'origine tient : un appel ne peut pas repasser un rectangle ici, parce que la
        // forme vient de la table des rôles et non d'un `RoundedCornerShape` écrit sur place.
        assertTrue(
            "la barre prend la forme de son rôle",
            topBar.contains(".liquidGlass(GlassSurface.THREAD_TOP_BAR, SurfaceRole.Bar.shape)"),
        )
        assertFalse(
            "la barre ne doit pas se fabriquer une forme à part",
            topBar.contains("liquidGlass(GlassSurface.THREAD_TOP_BAR, RoundedCornerShape"),
        )
        assertEquals(
            "et le rôle « barre » est une capsule",
            androidx.compose.foundation.shape.RoundedCornerShape(50),
            SurfaceRole.Bar.shape,
        )
        assertTrue(
            "la table doit porter une forme arrondie pour la barre du haut",
            glass.contains("THREAD_TOP_BAR(GlassBlurDefaultDp, GlassInk.TEXT, 4f, RoundedCornerShape(GlassBarCorner), lit = false)"),
        )
    }

    @Test
    fun `aucun RectangleShape ne revient dans la vitre`() {
        assertFalse(
            "la vitre ne doit plus importer RectangleShape",
            glass.contains("import androidx.compose.ui.graphics.RectangleShape"),
        )
    }
}
