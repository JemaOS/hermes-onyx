package com.onyx.app.ui.design

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Tous les boutons ronds sont des cercles — et un seul gabarit les décrit.**
 *
 * Le défaut signalé par le propriétaire sur son téléphone : « la forme des boutons octogonale ».
 * C'est un défaut de *forme*, et il se ferme donc par un test de forme :
 *
 *  1. **[RoundButtonShape] est un vrai cercle**, pas un `Path` polygonal. Un cercle tracé à la main
 *     en huit ou douze segments *est* un octogone (ou un dodécagone) : on vérifie donc que le
 *     contour rendu par la forme est un **arrondi analytique** ([Outline.Rounded], rayon = moitié de
 *     la plus petite dimension) et **jamais** un `Outline.Generic` — le seul type que produirait un
 *     `Path` dessiné segment par segment.
 *  2. **Un seul gabarit** : les deux boutons ronds de `OnyxControls` (la pastille du compositeur et
 *     la pastille en verre) citent [RoundButtonShape], et le mot `CircleShape` n'apparaît plus
 *     qu'une fois — à sa définition. Un bouton qui prend sa propre forme est exactement comment un
 *     octogone revient.
 *  3. **L'ombre de la plateforme ne revient pas** : `glassSurface` peint son ombre avec
 *     [glassDiscShadow]. `Modifier.shadow` (l'ombre d'élévation de `hwui`) approxime la silhouette
 *     d'un petit disque par un **polygone inscrit** — c'est la cause mesurée du défaut, et la vitre
 *     translucide laisse voir cet octogone *à travers* elle.
 *  4. **Aucune forme circulaire dessinée à la main** dans le paquet du design : pas de `Path()` avec
 *     `cubicTo`/`arcTo` qui referait un cercle par morceaux.
 */
class RoundButtonShapeTest {

    private val density = Density(2.75f)

    private fun outlineOf(shape: Shape, width: Float, height: Float): Outline =
        shape.createOutline(Size(width, height), LayoutDirection.Ltr, density)

    @Test
    fun `le gabarit des boutons ronds est un vrai cercle`() {
        // 38 dp à 2,75× = 104,5 px — la taille réelle du contrôle du volet.
        val outline = outlineOf(RoundButtonShape, 104f, 104f)
        assertTrue(
            "le contour doit être un arrondi analytique, pas un Path (Outline.Generic = polygone)",
            outline is Outline.Rounded,
        )
        val round = (outline as Outline.Rounded).roundRect
        assertEquals(
            "un carré parfait doit donner un rayon de la moitié du côté (donc un cercle)",
            52f,
            round.topLeftCornerRadius.x,
            0.01f,
        )
        assertEquals(
            "les quatre coins doivent avoir le même rayon : c'est UN cercle, pas un squircle",
            listOf(
                round.topLeftCornerRadius.x,
                round.topRightCornerRadius.x,
                round.bottomLeftCornerRadius.x,
                round.bottomRightCornerRadius.x,
            ).distinct().size,
            1,
        )
    }

    @Test
    fun `sur une boite non carree le gabarit reste un arrondi, jamais une marche`() {
        // Une pilule (capsule) : c'est le second usage de la même forme. Un arrondi en escalier
        // (coins carrés sur une boîte plus large que haute) serait le défaut « bords non arrondis ».
        val outline = outlineOf(RoundButtonShape, 104f, 40f)
        assertTrue("une capsule reste un arrondi analytique", outline is Outline.Rounded)
        val round = (outline as Outline.Rounded).roundRect
        assertEquals("le rayon est la moitié de la plus petite dimension", 20f, round.topLeftCornerRadius.x, 0.01f)
    }

    @Test
    fun `un seul gabarit pour tous les boutons ronds`() {
        val controls = source("src/main/java/com/onyx/app/ui/design/OnyxControls.kt")
        assertEquals(
            "les deux boutons ronds (pastille du compositeur, pastille en verre) doivent citer le " +
                "gabarit unique RoundButtonShape",
            2,
            Regex("RoundButtonShape").findAll(controls).count(),
        )
        assertFalse(
            "un bouton rond ne doit plus citer CircleShape en direct : le gabarit unique est le seul chemin",
            controls.contains("CircleShape"),
        )
        // Les deux boutons ronds, isolés : chacun doit se servir du gabarit, et rien d'autre.
        listOf("ComposerCircleButton", "GlassCircleButton").forEach { name ->
            val body = functionBody(controls, name)
            assertTrue("$name doit citer RoundButtonShape", body.contains("RoundButtonShape"))
            assertFalse(
                "$name ne doit pas se fabriquer une forme à part",
                body.contains("RoundedCornerShape("),
            )
            assertFalse(
                "$name ne doit pas dessiner son cercle à la main (Path + segments = polygone)",
                body.contains("Path("),
            )
        }
    }

    @Test
    fun `la pastille en verre a la forme du gabarit par defaut`() {
        val glass = source("src/main/java/com/onyx/app/ui/design/Glass.kt")
        assertTrue(
            "glassSurface doit prendre la forme du gabarit unique par défaut",
            glass.contains("shape: Shape = RoundButtonShape"),
        )
        assertTrue(
            "le gabarit unique est le cercle",
            glass.contains("val RoundButtonShape: Shape = CircleShape"),
        )
    }

    @Test
    fun `l ombre des pastilles est dessinee, pas confiee a la plateforme`() {
        val glass = source("src/main/java/com/onyx/app/ui/design/Glass.kt")
        assertTrue(
            "glassSurface doit peindre son ombre avec glassDiscShadow",
            glass.contains(".glassDiscShadow("),
        )
        assertFalse(
            "Modifier.shadow (ombre d'élévation de hwui) approxime la silhouette d'un petit " +
                "disque par un polygone inscrit : c'est la cause mesurée de l'octogone",
            glass.contains(".shadow(elevation ="),
        )
        // L'ombre dessinée empile les contours **élargis** de la forme : chaque anneau suit la forme
        // exactement (un cercle pour un disque), donc rien n'est tessellé.
        assertTrue(
            "l'ombre doit élargir le contour de la forme pour chaque anneau",
            glass.contains("shape.createOutline(grown, layoutDirection, this)"),
        )
    }

    @Test
    fun `aucun cercle n est dessine a la main dans le paquet du design`() {
        // Un cercle « fait main » se reconnaît à trois signes : un Path, et des courbes par morceaux.
        // S'il en existait un avec un nombre de segments insuffisant, ce serait l'octogone lui-même.
        val files = listOf(
            "src/main/java/com/onyx/app/ui/design/Glass.kt",
            "src/main/java/com/onyx/app/ui/design/OnyxControls.kt",
            "src/main/java/com/onyx/app/ui/design/LiquidGlass.kt",
            "src/main/java/com/onyx/app/ui/design/GlassTouch.kt",
        )
        files.forEach { path ->
            val text = source(path)
            assertFalse(
                "$path : une forme circulaire ne doit pas être dessinée par un Path avec des segments",
                text.contains("arcTo(") || text.contains("quadraticBezierTo("),
            )
        }
    }

    /**
     * Le corps d'une composable, du mot `fun` à la documentation suivante : c'est ce qui permet de
     * vérifier **ce bouton-là**, sans confondre sa forme avec celle d'un autre composant du fichier.
     */
    private fun functionBody(source: String, name: String): String {
        val start = source.indexOf("fun $name(")
        check(start >= 0) { "fonction introuvable : $name" }
        val next = source.indexOf("\n/**", start)
        return source.substring(start, if (next >= 0) next else source.length)
    }

    private fun source(path: String): String =
        listOf(File(path), File("core/$path")).firstOrNull { it.isFile }?.readText()
            ?: error("fichier introuvable : $path")
}
