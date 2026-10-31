package com.onyx.app.ui.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Une surface en verre a toujours une forme arrondie — et son rayon est celui de la table.**
 *
 * Le défaut signalé par le propriétaire (« les bords ne sont pas arrondis ») est un défaut de
 * *forme* : la barre du haut peignait un rectangle franc, parce que l'appelant ne passait aucune
 * forme et que le défaut du modificateur était `RectangleShape`. Le correctif ne tient donc pas à
 * l'appel — il tient à la **table** : la forme est portée par `GlassSurface`, et `liquidGlass` s'en
 * sert **par défaut**. Ce test **échoue** dès qu'une surface n'a plus de forme arrondie, dès qu'un
 * rayon se décale, ou dès qu'un rectangle redevient le défaut du modificateur.
 *
 * Les valeurs sont celles de la famille : **22 dp** pour les barres à rayon de coin (barre du haut,
 * carte du compositeur — le rayon du lot « composition »), **50 %** pour les **capsules** : la
 * capsule d'en-tête, le contrôle segmenté, et la **barre d'onglets** — devenue une pilule flottante
 * au lot « verre liquide ».
 */
class GlassShapeTest {

    /** La table attendue : surface → la forme que le verre doit peindre. */
    private val expected: Map<GlassSurface, Shape> = mapOf(
        GlassSurface.THREAD_TOP_BAR to RoundedCornerShape(22.dp),
        // La barre d'onglets est une **capsule** : 50 %, comme l'en-tête et le contrôle segmenté.
        // Sur une pilule détachée des quatre côtés, un rayon de 22 dp se lit encore comme un
        // rectangle rogné (relevé sur la capture du propriétaire).
        GlassSurface.TAB_BAR to RoundedCornerShape(50),
        GlassSurface.COMPOSER to RoundedCornerShape(22.dp),
        GlassSurface.HEADER_CAPSULE to RoundedCornerShape(50),
        // Les panneaux du bas et le menu : une seule famille, un seul rayon — celui des panneaux.
        GlassSurface.SHEET to RoundedCornerShape(OnyxSpace.PanelCorner),
        GlassSurface.ALERT to RoundedCornerShape(OnyxSpace.PanelCorner),
        GlassSurface.MENU to RoundedCornerShape(OnyxSpace.PanelCorner),
        // Le volet : seul son bord extérieur s'arrondit (l'autre longe le bord de l'écran).
        GlassSurface.SIDEBAR_PANEL to RoundedCornerShape(topEnd = 22.dp, bottomEnd = 22.dp),
        GlassSurface.SIDEBAR_TILE to RoundedCornerShape(OnyxSpace.TileCorner),
        GlassSurface.BOT_CARD to RoundedCornerShape(OnyxSpace.CardCorner),
        GlassSurface.LIST_ROW to RoundedCornerShape(OnyxSpace.CardCorner),
        GlassSurface.RESPONSE_BLOCK to RoundedCornerShape(OnyxSpace.CardCorner),
        GlassSurface.SEGMENTED to RoundedCornerShape(50),
        // La grande carte de matière (fil vide, carte d'un écran de contenu) : le rayon des
        // cartes — 30 dp, la fourchette de la référence du propriétaire.
        GlassSurface.MATTER_CARD to RoundedCornerShape(GlassCardCorner),
        // La plaque d'un onglet de contenu (celle qui glisse sous le doigt) : le rayon des
        // panneaux, comme la plaque du bas qu'elle double d'un cran plus haut.
        GlassSurface.PANE to RoundedCornerShape(PlaqueGeometry.Radius),
    )

    @Test
    fun `aucune surface en verre n a une forme carree`() {
        GlassSurface.entries.forEach { surface ->
            assertTrue(
                "${surface.name} : sa forme est un rectangle à coins carrés — c'est le défaut " +
                    "« les bords ne sont pas arrondis »",
                surface.shape !== RectangleShape,
            )
        }
    }

    @Test
    fun `le rayon de chaque surface est celui de la table`() {
        assertEquals(
            "une surface n'a pas son rayon déclaré dans ce test : sa forme ne serait plus vérifiée",
            GlassSurface.entries.toSet(),
            expected.keys,
        )
        expected.forEach { (surface, shape) ->
            assertEquals("${surface.name} : forme attendue", shape, surface.shape)
        }
    }

    @Test
    fun `les barres a coin partagent le meme rayon, les capsules la meme forme`() {
        // La barre du haut et la carte du compositeur : **une seule** valeur, pour qu'aucune des deux
        // ne puisse se décaler toute seule. C'est ainsi qu'un coin carré revient : une surface qui
        // prend sa propre valeur au lieu de la valeur de la famille.
        listOf(GlassSurface.THREAD_TOP_BAR, GlassSurface.COMPOSER).forEach { surface ->
            assertEquals("${surface.name} : rayon de la famille", GlassBarCorner, 22.dp)
            assertEquals(
                "${surface.name} : la forme doit porter le rayon de la famille",
                RoundedCornerShape(GlassBarCorner),
                surface.shape,
            )
        }
        // Les **capsules** : une barre flottante détachée des quatre côtés se lit comme un objet posé
        // quand elle est entièrement arrondie. La barre d'onglets, l'en-tête et le contrôle segmenté
        // partagent donc la même forme — 50 %, jamais un rayon de coin.
        listOf(GlassSurface.TAB_BAR, GlassSurface.HEADER_CAPSULE, GlassSurface.SEGMENTED)
            .forEach { surface ->
                assertEquals(
                    "${surface.name} : une capsule est entièrement arrondie",
                    RoundedCornerShape(50),
                    surface.shape,
                )
            }
    }

    @Test
    fun `le defaut du modificateur est la forme de la surface`() {
        val source = source("src/main/java/com/onyx/app/ui/design/LiquidGlass.kt")
        assertTrue(
            "liquidGlass doit prendre la forme de la surface par défaut",
            source.contains("shape: Shape = surface.shape"),
        )
        assertTrue(
            "un rectangle ne doit plus être le défaut de liquidGlass",
            !source.contains("shape: Shape = RectangleShape"),
        )
    }

    @Test
    fun `le rayon de la famille des panneaux est celui de la table`() {
        // Les trois panneaux de verre (feuille, alerte, menu) partagent **un seul** rayon, exposé
        // sous le nom [GlassPanelCorner]. C'est ce nom que les écrans citent : un rayon recopié à la
        // main finit toujours par diverger d'un panneau à l'autre — c'était le cas avant ce lot
        // (14 dp ici, 14 dp là, 10 dp pour la marge).
        listOf(GlassSurface.SHEET, GlassSurface.ALERT, GlassSurface.MENU).forEach { surface ->
            assertEquals(
                "${surface.name} : le rayon doit être celui de la famille des panneaux",
                RoundedCornerShape(GlassPanelCorner),
                surface.shape,
            )
        }
        assertEquals(
            "le coin de la famille des panneaux",
            OnyxSpace.PanelCorner,
            GlassPanelCorner,
        )
    }

    private fun source(path: String): String =
        listOf(File(path), File("core/$path")).firstOrNull { it.isFile }?.readText()
            ?: error("fichier introuvable : $path")
}
