package com.onyx.app.ui.design

import androidx.compose.ui.unit.dp
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Une seule échelle, et le source la porte.**
 *
 * Le défaut que ce test ferme n'est pas « il manque une constante » : c'est qu'un écart juste
 * **aujourd'hui** redevienne faux **demain**, dans le fichier voisin. Le relevé de ce lot a trouvé
 * quatorze valeurs d'espacement pour six intentions dans les seuls écrans de la capture ; la cause
 * n'était pas l'ignorance, c'était l'absence de **nom** — une valeur en dur ne se relit pas, ne se
 * cherche pas, ne se compte pas.
 *
 * Trois verrous :
 *  1. **l'échelle** — cinq marches, multiples de 4, déclarées dans l'ordre ; aucun jeton nommé ne
 *     peut être hors échelle ;
 *  2. **l'optique** — ce qui est sous 4 dp (traits, recalage de glyphe) est déclaré **à part**, et
 *     aucun jeton d'espacement ne peut y tomber : sans cette séparation, « une seule échelle »
 *     finirait par vouloir dire « on met ce qu'on veut » ;
 *  3. **le source** — chaque ligne du tableau des écarts ([OnyxSpace.Audit]) est vérifiée **dans le
 *     fichier qu'elle cite** : l'ancienne valeur ne doit plus s'y trouver, la nouvelle doit y être.
 *     Le tableau du rapport ne peut donc pas se contenter d'être plausible.
 */
class OnyxSpaceScaleTest {

    @Test
    fun `l echelle est de cinq marches, toutes multiples du pas`() {
        assertEquals(listOf(4.dp, 8.dp, 12.dp, 16.dp, 24.dp), OnyxSpace.Scale)
        OnyxSpace.Scale.forEach { step ->
            assertEquals(
                "la marche ${step.value} n'est pas un multiple de ${OnyxSpace.Step}",
                0f,
                step.value % OnyxSpace.Step,
                0.001f,
            )
        }
        // Les marches sont déclarées dans l'ordre, sans trou : c'est ce qui permet de dire « la
        // marche du dessus » sans tableau.
        assertEquals(OnyxSpace.Scale.sortedBy { it.value }, OnyxSpace.Scale)
    }

    @Test
    fun `aucun jeton nomme n est hors echelle`() {
        OnyxSpace.tokens.forEach { (name, value) ->
            assertTrue(
                "le jeton $name vaut ${value.value} dp : hors échelle",
                OnyxSpace.isOnScale(value),
            )
            assertNotNull("le jeton $name doit avoir un nom de marche", OnyxSpace.nameOf(value))
        }
        // Et les douze jetons d'espacement sont bien là : un nom qui disparaît, c'est un site qui
        // reviendra à une valeur en dur.
        assertEquals(12, OnyxSpace.tokens.size)
    }

    @Test
    fun `l optique reste sous l echelle et ne sert jamais d espacement`() {
        OnyxSpace.Optical.forEach { value ->
            assertTrue("${value.value} dp n'est pas une correction optique", value.value < OnyxSpace.Hair.value)
            assertFalse(
                "une valeur optique (${value.value}) ne peut pas être sur l'échelle",
                OnyxSpace.isOnScale(value),
            )
        }
        // Les jetons d'usage ne tombent jamais dans la zone optique.
        OnyxSpace.tokens.forEach { (name, value) ->
            assertTrue(
                "le jeton $name (${value.value}) est dans la zone optique : ce n'est pas de l'espacement",
                value.value >= OnyxSpace.Hair.value,
            )
        }
    }

    @Test
    fun `la regle d arrondi est celle de la marche la plus proche`() {
        assertEquals(OnyxSpace.Hair, OnyxSpace.nearest(2.dp))
        assertEquals(OnyxSpace.Hair, OnyxSpace.nearest(4.dp))
        assertEquals(OnyxSpace.Half, OnyxSpace.nearest(7.dp))
        assertEquals(OnyxSpace.Snug, OnyxSpace.nearest(11.dp))
        assertEquals(OnyxSpace.Snug, OnyxSpace.nearest(14.dp))
        assertEquals(OnyxSpace.Base, OnyxSpace.nearest(15.dp))
        assertEquals(OnyxSpace.Loose, OnyxSpace.nearest(22.dp))
        // Au-delà de la dernière marche, on ne monte pas indéfiniment.
        assertEquals(OnyxSpace.Loose, OnyxSpace.nearest(80.dp))
        // `nearestAtLeast` ne descend jamais sous la valeur demandée : c'est la règle des rangées,
        // dont l'écart ne doit pas devenir plus serré que ce qui était demandé.
        assertEquals(OnyxSpace.Base, OnyxSpace.nearestAtLeast(14.dp))
        assertEquals(OnyxSpace.Snug, OnyxSpace.nearestAtLeast(11.dp))
        assertEquals("Hair", OnyxSpace.nameOf(OnyxSpace.Hair))
        assertNull("14 n'est sur aucune marche", OnyxSpace.nameOf(14.dp))
    }

    @Test
    fun `chaque ligne du tableau des ecarts est portee par le source`() {
        assertTrue("le tableau doit avoir des lignes", OnyxSpace.Audit.size >= 12)
        OnyxSpace.Audit.forEach { fix ->
            val file = read(fix.file)
            assertTrue(
                "${fix.where} : le fichier « ${fix.file} » est introuvable",
                file.isNotEmpty(),
            )
            assertTrue(
                "${fix.where} : le source ne porte pas la valeur retenue —\nattendu : ${fix.after}",
                file.contains(fix.after),
            )
            assertFalse(
                "${fix.where} : l'ancienne valeur est encore là —\nà retirer : ${fix.before}",
                file.contains(fix.before),
            )
            // Le jeton cité doit exister dans la table (espacement **ou** rayon), et sa valeur être
            // sur l'échelle quand c'est un espacement.
            val spacing = (OnyxSpace.tokens + OnyxSpace.steps).map { it.first }
            val others = (OnyxSpace.sizes + OnyxSpace.radii).map { it.first }
            val citedSpacing = spacing.filter { fix.token.contains(it) }
            val citedOther = others.filter { fix.token.contains(it) }
            assertTrue(
                "${fix.where} : le jeton « ${fix.token} » n'est ni un jeton d'usage, ni une marche, " +
                    "ni une taille, ni un rayon déclarés",
                citedSpacing.isNotEmpty() || citedOther.isNotEmpty(),
            )
            if (citedSpacing.isNotEmpty()) {
                Regex("\\d+").findAll(fix.token).forEach { m ->
                    val value = m.value.toFloat()
                    assertTrue(
                        "${fix.where} : la valeur $value du jeton « ${fix.token} » n'est pas sur l'échelle",
                        OnyxSpace.Scale.any { it.value == value },
                    )
                }
            }
        }
    }

    @Test
    fun `les tailles, rayons et marches sont declares, et aucun n est un espacement`() {
        assertTrue("le lot nomme au moins une taille", OnyxSpace.sizes.isNotEmpty())
        assertTrue("le lot nomme au moins un rayon", OnyxSpace.radii.isNotEmpty())
        assertEquals("les cinq marches de l'échelle", 5, OnyxSpace.steps.size)
        OnyxSpace.steps.forEach { (name, value) ->
            assertEquals(
                "la marche $name (${value.value}) doit être celle de l'échelle",
                value,
                OnyxSpace.Scale.first { it.value == value.value },
            )
        }
        OnyxSpace.sizes.forEach { (name, value) ->
            assertTrue("la taille $name vaut ${value.value} : elle doit exister", value.value > 0f)
            assertEquals(
                "la taille $name (${value.value}) n'est pas un multiple du pas de ${OnyxSpace.Step}",
                0f,
                value.value % OnyxSpace.Step,
                0.001f,
            )
        }
        OnyxSpace.radii.forEach { (name, value) ->
            assertTrue("le rayon $name vaut ${value.value} : il doit exister", value.value > 0f)
        }
        val spacingNames = (OnyxSpace.tokens + OnyxSpace.steps).map { it.first }
        (OnyxSpace.sizes + OnyxSpace.radii).forEach { (name, _) ->
            assertFalse(
                "$name ne doit pas être déclaré comme espacement (c'est une taille ou un rayon)",
                spacingNames.contains(name),
            )
        }
    }

    @Test
    fun `la meme famille d elements a le meme ecart partout`() {
        // Le point qui se voit à l'œil : l'écart icône-libellé d'un onglet, d'une tuile et d'une
        // ligne de menu était de 2, 12 et 14 dp. Il est maintenant **un seul** jeton, et les trois
        // sites le citent. La feuille cite son alias [OnyxSpace.ThumbnailGap], qui **vaut**
        // `IconLabelGap` : une vignette et une icône nue s'écartent pareillement de leur mot.
        val sites = listOf(
            "core/src/main/java/com/onyx/app/ui/design/ChatTabBar.kt" to "OnyxSpace.IconLabelGap",
            "core/src/main/java/com/onyx/app/ui/design/OnyxMenu.kt" to "OnyxSpace.IconLabelGap",
            "core/src/main/java/com/onyx/app/ui/design/AttachmentSheet.kt" to "OnyxSpace.ThumbnailGap",
        )
        assertEquals(
            "l'alias de la vignette est l'écart icône-libellé de la maison",
            OnyxSpace.IconLabelGap,
            OnyxSpace.ThumbnailGap,
        )
        sites.forEach { (path, token) ->
            assertTrue(
                "$path doit citer l'écart icône-libellé de la maison",
                read(path).contains(token),
            )
        }
    }

    private fun read(path: String): String =
        listOf(File(path), File("../$path")).firstOrNull { it.isFile }?.readText() ?: ""
}
