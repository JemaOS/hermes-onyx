package com.onyx.app.ui.models

import androidx.compose.ui.graphics.Color
import com.onyx.app.ui.design.ModelContrast
import com.onyx.app.ui.design.OnyxSurfaces
import com.onyx.app.ui.design.contrastRatio
import com.onyx.app.ui.design.readableInk
import com.onyx.app.ui.theme.OnyxFonts
import com.onyx.app.ui.theme.OnyxTheme
import java.io.File
import java.util.Locale
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **LE verrou du contraste de la feuille de modèle — il mord, dans les deux apparences.**
 *
 * Le propriétaire a montré la feuille **en apparence sombre** avec un seul mot : « on ne voit rien
 * ici, corrige ». Le défaut n'était pas une impression, il se mesure sur la feuille **opaque
 * réellement peinte** ([ModelContrast.sheetSurface]) :
 *
 * | ce qui se peint | avant | seuil | dans les deux apparences |
 * |---|---|---|---|
 * | la **forme** de la pastille inactive contre la feuille | 1,20:1 (sombre) · 1,21:1 (clair) | **1,25:1** | sous le seuil **partout** |
 * | l'**encre** de l'en-tête de section (« PAR DÉFAUT »), du sous-titre, de l'**étoile** | 4,41:1 (sombre) | **4,5:1** pour un mot, **3:1** pour un tracé | sous le seuil **en sombre** |
 *
 * Les trois seuils du brief y sont tenus et **mesurés sur toutes les apparences** :
 *
 *  1. **un libellé** (mot) de la feuille : **≥ 4,5:1** — l'en-tête de section, le sous-titre, le nom
 *     de rangée, la sous-ligne du fournisseur, le libellé des deux pastilles ;
 *  2. **la pastille inactive contre la feuille : ≥ 1,25:1** — sous cette marche, on lit ses mots sans
 *     voir **qu'il y a une pastille** : c'est exactement ce que la capture montrait ;
 *  3. **l'étoile (un tracé) : ≥ 3:1** — et les autres icônes de la feuille (chevron, croix).
 *
 * Les valeurs viennent des **fonctions que l'écran appelle** ([ModelContrast.idleChip],
 * [ModelContrast.sheetTertiaryInk], [ModelContrast.tileInk]) : le chiffre annoncé et le chiffre
 * mesuré sont le même. La ligne qui échoue est **nommée** — avec le thème et l'apparence.
 *
 * Enfin il **prouve qu'il mord** : les encres **d'avant** (le gris de la famille tel quel, le
 * tertiaire du jeton partagé tel quel) doivent **échouer** aux mêmes seuils, sinon le verrou ne
 * garderait rien.
 */
class ContrasteFeuilleVerrouTest {

    private val rows = mutableListOf<String>()

    private fun hex(c: Color): String = String.format(
        Locale.US, "#%02X%02X%02X",
        (c.red * 255f).toInt().coerceIn(0, 255),
        (c.green * 255f).toInt().coerceIn(0, 255),
        (c.blue * 255f).toInt().coerceIn(0, 255),
    )

    /** Mesure une ligne, l'écrit dans la table, et **échoue en la nommant** si elle passe sous son seuil. */
    private fun check(
        ligne: String,
        theme: OnyxTheme,
        dark: Boolean,
        fond: Color,
        encre: Color,
        seuil: Double,
        sens: String,
    ) {
        val r = contrastRatio(encre, fond)
        val verdict = if (r >= seuil) "OK" else "FAIL"
        rows += listOf(
            ligne, theme.id, if (dark) "sombre" else "clair",
            hex(fond), hex(encre), String.format(Locale.US, "%.3f", r),
            String.format(Locale.US, "%.2f", seuil), sens, verdict,
        ).joinToString(",")
        assertTrue(
            "$ligne · ${theme.id}/${if (dark) "sombre" else "clair"} : " +
                "%.3f < %.2f ($sens, fond %s, encre %s)".format(r, seuil, hex(fond), hex(encre)),
            r >= seuil,
        )
    }

    @Test
    fun `les seuils de la feuille tiennent dans les deux apparences`() {
        OnyxTheme.entries.toList().forEach { name ->
            listOf(false, true).forEach { dark ->
                val theme = com.onyx.app.ui.theme.ResolvedTheme.resolve(name, dark = dark, fonts = OnyxFonts.System)
                val feuille = ModelContrast.sheetSurface(theme)
                val idle = ModelContrast.idleChip(theme)
                val active = ModelContrast.activeChip(theme)
                val tuile = ModelContrast.tile(theme)

                // — 1. Les libellés (des mots) : 4,5:1 —
                check("libellé · en-tête de section « PAR DÉFAUT »", name, dark, feuille,
                    ModelContrast.sheetTertiaryInk(theme), 4.5, "mot")
                check("libellé · sous-titre d'état d'un profil", name, dark, feuille,
                    ModelContrast.sheetTertiaryInk(theme), 4.5, "mot")
                check("libellé · nom de rangée", name, dark, feuille,
                    readableInk(theme.textColor, feuille), 4.5, "mot")
                check("libellé · sous-ligne du fournisseur", name, dark, feuille,
                    readableInk(theme.secondaryText, feuille), 4.5, "mot")
                check("libellé · libellé de la pastille inactive", name, dark, idle,
                    readableInk(theme.textColor, idle), 4.5, "mot")
                check("libellé · libellé de la pastille active", name, dark, active,
                    ModelContrast.activeChipInk(theme), 4.5, "mot")

                // — 2. La FORME de la pastille inactive contre la feuille : 1,25:1 —
                check("pastille inactive · forme contre la feuille", name, dark, feuille,
                    idle, ModelContrast.CHIP_STEP_MIN.toDouble(), "surface qui se détache")
                check("pastille active · forme contre la feuille", name, dark, feuille,
                    active, ModelContrast.CHIP_STEP_MIN.toDouble(), "surface qui se détache")
                // …et l'active ne se confond pas avec les inactives (le contrôle de fournisseur).
                check("pastille active · écart avec la pastille inactive", name, dark, idle,
                    active, ModelContrast.CHIP_STEP_MIN.toDouble(), "surface qui se détache")

                // — 3. Les tracés (icônes, étoile) : 3:1 —
                check("étoile · tracé au repos", name, dark, feuille,
                    ModelContrast.sheetTertiaryInk(theme), 3.0, "icône")
                check("étoile · tracé posé (favori)", name, dark, feuille,
                    readableInk(theme.accent, feuille, 3.0), 3.0, "icône")
                check("chevron « Tous les modèles »", name, dark, feuille,
                    ModelContrast.sheetTertiaryInk(theme), 3.0, "icône")
                check("croix de fermeture", name, dark, feuille,
                    readableInk(theme.secondaryText, feuille, 3.0), 3.0, "icône")

                // — Et l'encre de la tuile, sur la tuile : 4,5:1 —
                check("tuile · monogramme sur la tuile", name, dark, tuile,
                    ModelContrast.tileInk(theme), 4.5, "mot")
            }
        }

        val out = File("build/contraste-feuille.csv")
        out.parentFile?.mkdirs()
        out.writeText((listOf("ligne,theme,mode,fond,encre,ratio,seuil,sens,verdict") + rows).joinToString("\n") + "\n")
    }

    /**
     * **La preuve de morsure** — les encres **d'avant** échouent aux seuils du verrou.
     *
     * Un verrou qui ne planterait pas sur le défaut livré ne garderait rien. On rejoue donc les deux
     * valeurs exactes de la capture : le **gris de la famille tel quel** posé sur la feuille, et le
     * **tertiaire du jeton partagé** posé sur la feuille. Elles doivent être **sous** leur seuil —
     * c'est ce qui a fait écrire ce test.
     */
    @Test
    fun `les encres d avant echouent aux seuils du verrou`() {
        OnyxTheme.entries.toList().forEach { name ->
            listOf(false, true).forEach { dark ->
                val theme = com.onyx.app.ui.theme.ResolvedTheme.resolve(name, dark = dark, fonts = OnyxFonts.System)
                val feuille = ModelContrast.sheetSurface(theme)

                // Le défaut livré : au moins une apparence passe **sous** chaque seuil.
                val formeSous = contrastRatio(OnyxSurfaces.capsule(theme), feuille) < ModelContrast.CHIP_STEP_MIN
                val motSous = contrastRatio(theme.tertiaryText, feuille) < 4.5
                assertTrue(
                    "aucun défaut rejoué pour $name/${if (dark) "sombre" else "clair"} : " +
                        "le verrou ne garde rien de cette apparence",
                    formeSous || motSous,
                )
            }
        }
        // Sur le thème des captures du propriétaire (Messages, le défaut), les deux défauts sont là.
        val standard = com.onyx.app.ui.theme.ResolvedTheme.resolve(OnyxTheme.STANDARD, dark = true, fonts = OnyxFonts.System)
        val feuille = ModelContrast.sheetSurface(standard)
        assertTrue(
            "la morsure : la pastille d'avant devait être sous 1,25:1 sur la feuille sombre",
            contrastRatio(OnyxSurfaces.capsule(standard), feuille) < ModelContrast.CHIP_STEP_MIN,
        )
        assertTrue(
            "la morsure : le tertiaire d'avant devait être sous 4,5:1 sur la feuille sombre",
            contrastRatio(standard.tertiaryText, feuille) < 4.5,
        )
    }

    /**
     * **L'encre passée à `IdentifierText` est bien celle qui se peint.**
     *
     * Le vrai coupable, relevé au pixel : la feuille annonçait des encres **mesurées** et en peignait
     * d'**autres** — du noir, la couleur par défaut du socle. `IdentifierText` recevait sa `color`,
     * s'en servait pour **mesurer** la coupe au milieu, puis dessinait le **style nu**. Tout ce qui
     * passe par lui sur la feuille — les libellés de pastille, le nom d'un modèle, sa sous-ligne de
     * fournisseur — tombait donc à **1,68:1** et **1,70:1** sur la feuille sombre.
     *
     * Le verrou tient la ligne du dessin : c'est `drawn` (le style **avec** sa couleur) qui se
     * peint. Le fichier et la ligne sont nommés.
     */
    @Test
    fun `l encre passee a IdentifierText est bien celle qui se peint`() {
        val file = File("../core/src/main/java/com/onyx/app/ui/design/OnyxTextPolicy.kt")
        assertTrue("la politique de texte est introuvable", file.isFile)
        val body = file.readText().substringAfter("fun IdentifierText(").substringBefore("\n/**")
        val line = file.readText().lines().indexOfFirst { it.trim().startsWith("style =") }
        assertTrue(
            "IdentifierText doit peindre la couleur qu'on lui donne (`style = drawn`, " +
                "OnyxTextPolicy.kt:${line + 1}) — sinon la feuille peint du noir là où elle " +
                "annonce une encre mesurée",
            body.contains("style = drawn"),
        )
        assertTrue(
            "IdentifierText ne doit pas peindre le style nu (le noir du socle, OnyxTextPolicy.kt:${line + 1})",
            !body.contains("style = style"),
        )
    }

    /**
     * **La feuille peint l'encre qu'elle a mesurée, pas le jeton partagé.**
     *
     * Les chiffres du test précédent mesurent les fonctions de la maison ([ModelContrast]) ; celui-ci
     * tient le **branchement** — sans lui, une feuille pourrait revenir au tertiaire du jeton partagé
     * (4,41:1) sans qu'aucun chiffre ne bouge. Chaque constat **nomme le fichier et l'endroit**.
     */
    @Test
    fun `la feuille peint l encre qu elle a mesuree sur elle`() {
        val sheet = File("src/main/java/com/onyx/app/ui/models/ModelSheet.kt").readText()

        val header = sheet.substringAfter("private fun SectionHeader(").substringBefore("private fun AllModelsRow")
        assertTrue(
            "l'en-tête de section doit mesurer son encre sur la feuille " +
                "(ModelSheet.kt, SectionHeader — `ModelContrast.sheetTertiaryInk(theme)`)",
            header.contains("ModelContrast.sheetTertiaryInk(theme)"),
        )
        assertTrue(
            "l'en-tête de section ne doit pas peindre le tertiaire du jeton partagé " +
                "(ModelSheet.kt, SectionHeader — 4,41:1 sur la feuille sombre)",
            !header.contains("theme.tertiaryText"),
        )

        val row = sheet.substringAfter("private fun ModelRow(").substringBefore("OnyxRowDivider(inset =")
        assertTrue(
            "l'étoile au repos doit mesurer son encre sur la feuille " +
                "(ModelSheet.kt, ModelRow — `else ModelContrast.sheetTertiaryInk(theme)`)",
            row.contains("else ModelContrast.sheetTertiaryInk(theme)"),
        )

        val chip = sheet.substringAfter("private fun ChipFlow(").substringBefore("labels.forEach")
        assertTrue(
            "la pastille inactive doit venir de ModelContrast.idleChip (ModelSheet.kt, ChipFlow)",
            chip.contains("ModelContrast.idleChip(theme)"),
        )
    }
}
