package com.onyx.app.ui.models

import androidx.compose.ui.unit.dp
import com.onyx.app.ui.design.OnyxSocle
import com.onyx.app.ui.design.OnyxSpace
import com.onyx.app.ui.design.ModelContrast
import com.onyx.app.ui.design.ReasoningLevels
import com.onyx.app.ui.design.contrastRatio
import com.onyx.app.ui.theme.OnyxFonts
import com.onyx.app.ui.theme.OnyxTheme
import com.onyx.app.ui.theme.ResolvedTheme
import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le verrou de la marge de la feuille de modèle.**
 *
 * Il mord sur trois choses, et seulement trois — celles que la capture du propriétaire montrait
 * cassées :
 *
 * 1. **Une pastille sort de la marge** : la rangée de pastilles doit être dans la marge de la
 *    feuille ([OnyxSocle.CardPadding] de chaque côté), se **replier** (`FlowRow`) et ne plus
 *    défiler horizontalement — un défilement qui n'est pas découvert tranche la dernière pastille
 *    au bord de la feuille.
 * 2. **Une rangée n'a pas sa tuile** : chaque rangée porte une tuile arrondie de 44 à 48 dp, du
 *    gris de la maison, dont le contraste sur la feuille tient **1,25:1** — en dessous, la tuile est
 *    peinte mais invisible, ce qui se lit comme « une lettre nue ».
 * 3. **Un filet est pleine largeur** : tout `OnyxRowDivider` de la feuille est en retrait des deux
 *    bords, et celui d'une rangée commence **après la tuile**, donc sur le nom.
 *
 * Les valeurs sont celles que l'écran **appelle** (`ModelRowDividerInset`, `ModelContrast.tile`,
 * `OnyxSpace.ThumbnailSize`) : le chiffre mesuré et le chiffre affiché sont le même.
 */
class ModelSheetMarginLockTest {

    private val sheet = File("src/main/java/com/onyx/app/ui/models/ModelSheet.kt").readText()

    // — 1. Les pastilles ne sortent jamais de la marge —

    @Test
    fun `les rangees de pastilles sont fluides et dans la marge`() {
        assertTrue(
            "la feuille n'a plus qu'un dessin de pastille, et il se replie",
            sheet.contains("FlowRow("),
        )
        assertFalse(
            "plus de défilement horizontal : c'est lui qui tranchait la dernière pastille",
            sheet.contains("horizontalScroll"),
        )
        assertFalse(
            "les pastilles ne se mesurent plus pour se rétrécir jusqu'à un plancher",
            sheet.contains("ChipRowFit"),
        )
        // La marge de la rangée est celle de la feuille, des deux côtés.
        val flowRow = sheet.substringAfter("private fun ChipFlow(", "").substringBefore("labels.forEach")
        assertTrue(
            "la rangée de pastilles est dans la marge de la feuille",
            flowRow.contains("padding(horizontal = OnyxSocle.CardPadding)"),
        )
        assertTrue("la marge est de chaque côté", OnyxSocle.CardPadding.value > 0f)
    }

    // — 2. Chaque rangée a sa tuile, et elle se voit —

    @Test
    fun `la tuile d une rangee mesure 44 a 48 dp et se detache de la feuille`() {
        val side = OnyxSpace.ThumbnailSize.value
        assertTrue("la tuile doit faire au moins 44 dp (elle en fait $side)", side >= 44f)
        assertTrue("et pas plus de 48 dp (elle en fait $side)", side <= 48f)

        OnyxTheme.entries.toList().forEach { name ->
            listOf(false, true).forEach { dark ->
                val theme = ResolvedTheme.resolve(name, dark = dark, fonts = OnyxFonts.System)
                val surface = ModelContrast.sheetSurface(theme)
                val tile = ModelContrast.tile(theme)
                val ratio = contrastRatio(tile, surface)
                assertTrue(
                    "tuile · ${theme.id}/${if (dark) "sombre" else "clair"} : %.2f < %.2f".format(ratio, ModelContrast.TILE_MIN),
                    ratio >= ModelContrast.TILE_MIN,
                )
                val ink = contrastRatio(ModelContrast.tileInk(theme), tile)
                assertTrue(
                    "monogramme · ${theme.id}/${if (dark) "sombre" else "clair"} : %.2f < 4.5".format(ink),
                    ink >= 4.5f,
                )
            }
        }
    }

    @Test
    fun `la rangee dessine la tuile, la lettre dedans, le nom, la sous-ligne et le prix`() {
        val row = sheet.substringAfter("private fun ModelRow(", "").substringBefore("OnyxRowDivider(inset =")
        assertTrue("la tuile prend le côté de la famille", row.contains(".size(OnyxSpace.ThumbnailSize)"))
        assertTrue("elle est arrondie", row.contains("RoundedCornerShape(OnyxSpace.ThumbnailCorner)"))
        assertTrue("elle est peinte du gris mesuré", row.contains("background(tile)"))
        assertTrue("la lettre est DANS la tuile", row.contains("text = entry.monogram"))
        // Le nom est un **identifiant** : une ligne, abrégée au milieu, jamais coupée au bord.
        assertTrue("le nom s'abrège au milieu", row.contains("IdentifierText("))
        assertTrue("le fournisseur est la sous-ligne", row.contains("modelRowProviders(entry)"))
        assertTrue("le prix est en monospace", row.contains("theme.monospacedFootnote"))
        assertTrue("la coche du modèle retenu", row.contains("Symbols.Checkmark"))
        assertTrue("l'étoile de fin de rangée", row.contains("Symbols.StarFill"))
    }

    @Test
    fun `aucune cible de la feuille ne descend sous 48 dp`() {
        assertTrue(
            "la cible de la feuille (${ModelControlTarget.value} dp)",
            ModelControlTarget.value >= 48f,
        )
        assertTrue(
            "la hauteur d'une rangée (${ModelRowMinHeight.value} dp)",
            ModelRowMinHeight.value >= 48f,
        )
        // Les cibles qui portent une action sont à cette taille dans le source.
        assertTrue("l'étoile et la croix", sheet.contains(".size(ModelControlTarget)"))
        assertTrue("le champ de recherche", sheet.contains("heightIn(min = ModelControlTarget)"))
        assertTrue("une pastille", sheet.contains("heightIn(min = ModelControlTarget)"))
    }

    // — 3. Aucun filet de bord à bord —

    @Test
    fun `tout filet de la feuille est en retrait des deux bords`() {
        // Le filet d'une rangée commence après la tuile : marge + rembourrage + tuile + écart.
        val expected = ModelRowSideMargin + ModelRowPadding + OnyxSpace.ThumbnailSize + ModelTileGap
        assertEquals("le filet commence sur le nom", expected.value, ModelRowDividerInset.value, 0.01f)
        assertTrue("le filet ne touche pas le bord gauche", ModelRowDividerInset.value > 0f)
        assertTrue("le filet ne touche pas le bord droit", ModelRowDividerEndInset.value > 0f)
        assertTrue(
            "le filet laisse la tuile entière hors de lui",
            ModelRowDividerInset.value >= ModelRowSideMargin.value + OnyxSpace.ThumbnailSize.value,
        )

        // Aucun appel de filet de la feuille ne se contente du bord par défaut : chacun dit ses
        // deux retraits. C'est la règle « jamais de bord à bord » tenue par le texte, pas par l'œil.
        val calls = Regex("OnyxRowDivider\\(([^)]*)\\)").findAll(sheet).map { it.groupValues[1] }.toList()
        assertTrue("la feuille pose au moins un filet", calls.isNotEmpty())
        calls.forEach { args ->
            assertTrue("un filet sans retrait de départ : $args", args.contains("inset ="))
            assertTrue("un filet sans retrait de fin : $args", args.contains("endInset ="))
            assertFalse("un filet au bord : $args", args.contains("inset = 0.dp") || args.contains("endInset = 0.dp"))
        }
    }

    @Test
    fun `la feuille ne depasse pas l ecran, et l arête du haut se voit`() {
        assertTrue(
            "la feuille laisse voir le voile en tête (${ModelSheetHeightFraction})",
            ModelSheetHeightFraction in 0.5f..0.99f,
        )
        assertTrue("elle est bornée à cette part", sheet.contains("heightIn(max = sheetCap)"))
        assertTrue("la liste défile dedans", sheet.contains("weight(1f, fill = false)"))
        assertTrue("elle porte l'arête du filet de la maison", sheet.contains("OnyxSurfaces.hairline(theme)"))
        assertTrue("et la poignée", sheet.contains("ModelSheetHandle()"))
    }

    // — Les noms et les comptes, en français —

    @Test
    fun `les en-tetes de section sont en francais`() {
        assertEquals("PAR DÉFAUT", sectionTitle("default", Locale.FRENCH))
        assertEquals("FAVORIS", sectionTitle("Favourites", Locale.FRENCH))
        assertEquals("RÉCENTS", sectionTitle("Recents", Locale.FRENCH))
        // Un nom propre de profil reste tel quel, en capitales comme les autres en-têtes.
        assertEquals("KILO", sectionTitle("kilo", Locale.FRENCH))
        // En anglais, la feuille garde ses mots.
        assertEquals("DEFAULT", sectionTitle("default", Locale.ENGLISH))
    }

    @Test
    fun `la ligne des crans dit combien, pas lequel manque`() {
        // Le serveur complet : les six crans pilotables sont là, la ligne **disparaît** — même si
        // « Ultra » de la référence n'a pas de valeur côté serveur (ce n'est pas son absence à lui).
        assertNull("tout est là : pas de note", reasoningCountLine(ReasoningLevels.plan(), Locale.FRENCH))

        // Un serveur sans « Très élevé » (xhigh) : la feuille le **compte**.
        val withoutXhigh = ReasoningLevels.server.filterNot { it.first == "xhigh" }
        val line = reasoningCountLine(ReasoningLevels.plan(withoutXhigh), Locale.FRENCH)
        assertNotNull("un cran manque : la feuille le compte", line)
        assertEquals("5 crans de réflexion sur 6 proposés par ce serveur.", line)
        assertFalse("elle ne nomme plus le cran absent", line!!.contains("Ultra"))
        assertFalse("ni une note de développement", line.contains("ne propose pas tous les crans"))
    }
}
