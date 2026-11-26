package com.onyx.app.ui.models

import androidx.compose.ui.graphics.Color
import com.onyx.app.ui.design.ICON_CONTRAST_MIN
import com.onyx.app.ui.design.ModelContrast
import com.onyx.app.ui.design.TEXT_CONTRAST_MIN
import com.onyx.app.ui.design.contrastRatio
import com.onyx.app.ui.design.readableInk
import com.onyx.app.ui.theme.OnyxFonts
import com.onyx.app.ui.theme.OnyxTheme
import com.onyx.app.ui.theme.ResolvedTheme
import java.io.File
import java.util.Locale
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les **seuils de contraste** de la carte de composition et du sélecteur de modèle, **mesurés** sur
 * toutes les apparences, en clair comme en sombre — 4,5:1 pour un texte, 3:1 pour une icône.
 *
 * Les fonds viennent de [ModelContrast], **la même fonction que les écrans appellent** : le chiffre
 * mesuré et le chiffre affiché sont le même. Là où l'app dérive son encre ([readableInk], comme la
 * barre d'onglets), le test mesure **l'encre réellement dérivée** — pas la couleur du thème.
 *
 * La table complète est écrite dans `build/model-contrast.csv` : c'est la preuve du rapport.
 */
class ModelContrastTest {

    private val rows = mutableListOf<String>()

    private fun report(surface: String, theme: ResolvedTheme, ink: String, ratio: Double, min: Double) {
        rows += listOf(
            surface,
            theme.id,
            if (theme.dark) "dark" else "light",
            ink,
            String.format(Locale.US, "%.2f", ratio),
            String.format(Locale.US, "%.1f", min),
            if (ratio >= min) "OK" else "FAIL",
        ).joinToString(",")
    }

    private fun check(surface: String, theme: ResolvedTheme, ink: String, inkColour: Color, background: Color, min: Double) {
        val ratio = contrastRatio(inkColour, background)
        report(surface, theme, ink, ratio, min)
        assertTrue(
            "$surface · ${theme.id}/${if (theme.dark) "dark" else "light"} · $ink : %.2f < %.1f".format(ratio, min),
            ratio >= min,
        )
    }

    @Test
    fun `la carte de composition et la feuille tiennent les seuils, en clair comme en sombre`() {
        OnyxTheme.entries.toList().forEach { name ->
            listOf(false, true).forEach { dark ->
                val theme = ResolvedTheme.resolve(name, dark = dark, fonts = OnyxFonts.System)

                // 1. La carte (la vitre de commande) : le texte du champ, et l'encre discrète.
                val card = ModelContrast.card(theme)
                check("carte", theme, "texte", theme.textColor, card, TEXT_CONTRAST_MIN)
                check("carte", theme, "encre discrète", theme.secondaryText, card, ICON_CONTRAST_MIN)

                // 2. La puce du modèle : son encre est **dérivée** de son fond composé.
                val chip = ModelContrast.chip(theme)
                check("puce", theme, "texte", readableInk(theme.textColor, chip), chip, TEXT_CONTRAST_MIN)
                check("puce", theme, "chevron", readableInk(theme.secondaryText, chip, ICON_CONTRAST_MIN), chip, ICON_CONTRAST_MIN)

                // 3. La feuille (la vitre de feuille, teintée de la surface).
                val sheet = ModelContrast.sheet(theme)
                check("feuille", theme, "texte", theme.textColor, sheet, TEXT_CONTRAST_MIN)
                check("feuille", theme, "texte secondaire", theme.secondaryText, sheet, TEXT_CONTRAST_MIN)
                check("feuille", theme, "icône discrète", theme.tertiaryText, sheet, ICON_CONTRAST_MIN)

                // 4. La vignette : son encre est mesurée contre le carré **gris** composé.
                val monogram = ModelContrast.monogram(theme)
                check("vignette", theme, "encre", readableInk(theme.textColor, monogram), monogram, TEXT_CONTRAST_MIN)

                // 5. Les pastilles de choix : la **pleine** (le motif de la référence — noire en
                //    clair, blanche en sombre) et la claire. Leur encre est dérivée de la pastille
                //    réellement peinte, jamais de la couleur du thème.
                val active = ModelContrast.activeChip(theme)
                val idle = ModelContrast.idleChip(theme)
                check("pastille-active", theme, "encre", readableInk(ModelContrast.activeChipInk(theme), active), active, TEXT_CONTRAST_MIN)
                check("pastille-claire", theme, "encre", readableInk(theme.textColor, idle), idle, TEXT_CONTRAST_MIN)

                // 5 bis. La pastille d'information d'une rangée (fournisseur, et son prix).
                val offer = ModelContrast.offerChip(theme)
                check("pastille-offre", theme, "encre", readableInk(theme.textColor, offer), offer, TEXT_CONTRAST_MIN)

                // 5 ter. L'ancien vocabulaire reste mesuré : la puce du compositeur et le bouton
                //    d'envoi gardent l'encre dérivée sur l'accent.
                check("puce-retention", theme, "encre", readableInk(theme.userText, theme.accent), theme.accent, TEXT_CONTRAST_MIN)
                check("envoi", theme, "encre", readableInk(theme.userText, theme.accent), theme.accent, TEXT_CONTRAST_MIN)

                // 6. La rangée du modèle retenu : le texte sur la teinte d'accent posée sur la feuille,
                // encre **dérivée** — c'est ce que la rangée appelle.
                check(
                    "rangée-retenue", theme, "texte",
                    readableInk(theme.textColor, ModelContrast.chosenRow(theme)),
                    ModelContrast.chosenRow(theme), TEXT_CONTRAST_MIN,
                )
            }
        }

        val out = File("build/model-contrast.csv")
        out.parentFile?.mkdirs()
        out.writeText((listOf("surface,theme,mode,ink,ratio,min,verdict") + rows).joinToString("\n") + "\n")
    }
}
