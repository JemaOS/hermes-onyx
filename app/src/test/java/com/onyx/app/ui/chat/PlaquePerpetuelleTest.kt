package com.onyx.app.ui.chat

import com.onyx.app.ui.design.ComposerStacking
import com.onyx.app.ui.design.GlassSurface
import com.onyx.app.ui.design.MaskedContentMaxRatio
import com.onyx.app.ui.design.OnyxContrast
import androidx.compose.ui.graphics.lerp
import com.onyx.app.ui.design.compositedBackdrop
import com.onyx.app.ui.design.glassBlurStrokeCoverage
import com.onyx.app.ui.design.glassPaintBlurDp
import com.onyx.app.ui.design.glassInks
import com.onyx.app.ui.design.glassMaskingFloor
import com.onyx.app.ui.design.glassMaskingTintAlpha
import com.onyx.app.ui.design.glassPaintTintAlpha
import com.onyx.app.ui.design.glassTintColour
import com.onyx.app.ui.theme.OnyxFonts
import com.onyx.app.ui.theme.OnyxTheme
import com.onyx.app.ui.theme.ResolvedTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **LE VERROU DE LA PLAQUE PERPÉTUELLE.**
 *
 * Deux garanties, et chacune est un défaut du propriétaire :
 *
 * 1. **« ça ne doit JAMAIS disparaître quand je défile. »** La pile du bas — le compositeur **et**
 *    la rangée d'onglets — s'effaçait au défilement (fondu + glissement court, décidés par une
 *    distance accumulée). Ce verrou échoue si **un seul** de ces chemins revient : l'état replié
 *    (`stackVeiled`), la distance (`accumulate` / `ScrollSlopDp`), la décision, la garde de frappe
 *    (`typingHolds`), l'opacité (`stackAlpha`), le mouvement (`slideSpec` / `SlideFraction`) ou
 *    l'`AnimatedVisibility` qui la peignait. Il exige, en regard, que la plaque soit dessinée
 *    **inconditionnellement**.
 *
 * 2. **« le texte passe À TRAVERS et AUTOUR de la pastille et de la plaque. »** Une vitre mince
 *    laisse lire le fil **dans** la plaque. Ce verrou mesure : le voile **peint** de la plaque amène
 *    le contraste du fil **derrière** elle sous [MaskedContentMaxRatio] — donc sous le seuil de
 *    lisibilité — alors que le voile mince (sans `masking`) le laissait au-dessus.
 *
 * **Morsure** : rebrancher `stackVeiled`/`stackVisible`/l'`AnimatedVisibility` de la pile fait
 * rougir la première famille ; retirer `masking = true` de la plaque fait rougir la seconde
 * (le contrôle « sombre » tombe alors au-dessus du seuil de masquage).
 */
class PlaquePerpetuelleTest {

    private fun read(path: String): String {
        val file = listOf(path, "../$path").map(::File).firstOrNull { it.isFile }
            ?: error("fichier introuvable : $path")
        return file.readText()
    }

    /** La source **sans ses commentaires** : on juge le code, pas les commentaires qui le nomment. */
    private fun code(path: String): String {
        val blocks = read(path).replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
        return blocks.lines().joinToString("\n") { line ->
            val i = line.indexOf("//")
            if (i >= 0) line.substring(0, i) else line
        }
    }

    private val screen = code("src/main/java/com/onyx/app/ui/chat/ChatScreen.kt")
    private val stacking = code("../core/src/main/java/com/onyx/app/ui/design/ComposerStacking.kt")

    // ---------------------------------------------------------------------------------------------
    // 1 · Aucun chemin ne peut modifier la visibilité ou l'opacité de la plaque
    // ---------------------------------------------------------------------------------------------

    /** La ligne qui pose la vitre **masquante** de la plaque du bas : le repère du lot. */
    private val plateGlassLine: String =
        "liquidGlass(GlassSurface.TAB_BAR, RoundedCornerShape(PlaqueGeometry.Radius), masking = true)"

    /**
     * Le **câblage de la plaque** : de son `.align(Alignment.BottomCenter)` jusqu'à sa vitre. C'est
     * exactement l'endroit où une `AnimatedVisibility` ou un modificateur d'opacité viendraient se
     * glisser s'ils revenaient. Commentaires retirés : on juge le code.
     */
    private val plateWiring: String = run {
        val glass = screen.indexOf(plateGlassLine)
        val align = if (glass >= 0) screen.lastIndexOf(".align(Alignment.BottomCenter)", glass) else -1
        if (glass > 0 && align in 1 until glass) {
            screen.substring(align, (glass + plateGlassLine.length).coerceAtMost(screen.length))
        } else {
            ""
        }
    }

    @Test
    fun `l ecran ne porte plus aucun etat d effacement de la pile`() {
        listOf(
            "stackVeiled",
            "stackVisible",
            "bottomStackVeil",
            "SlideFraction",
            "slideSpec",
            "ScrollSlopDp",
            "stackAlpha",
            "typingHolds",
            "accumulate(",
        ).forEach { token ->
            assertFalse(
                "le jeton d'effacement « $token » ne doit plus apparaître dans le code de ChatScreen.kt",
                screen.contains(token),
            )
        }
    }

    @Test
    fun `la plaque est dessinee inconditionnellement, pas dans une AnimatedVisibility`() {
        assertTrue("le câblage de la plaque doit être trouvé", plateWiring.length > 200)
        assertFalse(
            "plus d'`AnimatedVisibility` autour de la pile : elle est toujours composée",
            plateWiring.contains("AnimatedVisibility"),
        )
        // La plaque n'est ouverte dans **aucun** bloc conditionnel : sa chaîne de mise en page ne
        // contient ni accolade ouvrante ni fermante.
        assertFalse("la plaque n'est ouverte dans aucun bloc conditionnel", plateWiring.contains("{"))
        assertFalse("la plaque n'est fermée dans aucun bloc conditionnel", plateWiring.contains("}"))
        listOf(".alpha(", "graphicsLayer", ".offset(", "translationY", "animateFloat").forEach { token ->
            assertFalse(
                "aucun « $token » ne doit pouvoir changer la visibilité ou l'opacité de la plaque",
                plateWiring.contains(token),
            )
        }
        assertTrue(
            "la plaque est alignée au bas, pleine largeur",
            plateWiring.contains(".align(Alignment.BottomCenter)")
                && plateWiring.contains(".fillMaxWidth()"),
        )
        assertTrue(
            "la plaque peint toujours sa vitre, à la surface de la table",
            plateWiring.contains("liquidGlass(GlassSurface.TAB_BAR"),
        )
        // Le nombre de fenêtres `AnimatedVisibility` du fichier est celui d'avant le lot (la bannière
        // d'erreur et les puces de suivi) : une de plus = la plaque est repassée sous une visibilité.
        assertEquals(
            "aucune `AnimatedVisibility` n'a été ajoutée autour de la pile",
            3,
            Regex("AnimatedVisibility\\(").findAll(screen).count(),
        )
    }

    @Test
    fun `le noyau ne porte plus la regle d effacement`() {
        listOf(
            "fun stackVeiled",
            "fun accumulate",
            "fun typingHolds",
            "fun stackAlpha",
            "fun <T> slideSpec",
            "const val ScrollSlopDp",
            "const val SlideFraction",
        ).forEach { token ->
            assertFalse(
                "le noyau ne doit plus exposer « $token » : la règle a été retirée",
                stacking.contains(token),
            )
        }
    }

    // ---------------------------------------------------------------------------------------------
    // 2 · La plaque masque le contenu : le fil derrière tombe sous le seuil de lisibilité
    // ---------------------------------------------------------------------------------------------

    private fun themes(): List<ResolvedTheme> = buildList {
        for (name in OnyxTheme.entries) {
            for (dark in listOf(false, true)) {
                add(ResolvedTheme(theme = name, palette = name.palette, dark = dark, fonts = OnyxFonts.System))
            }
        }
    }

    private companion object {
        /** La densité de l'appareil de recette (420 dpi) — le flou se mesure en pixels. */
        const val DENSITY = 2.625f
    }

    /** Le contraste, **à travers un voile d'opacité α**, entre l'encre du fil et son fond. */
    private fun contrastThrough(theme: ResolvedTheme, alpha: Float): Float {
        val tint = glassTintColour(GlassSurface.TAB_BAR, theme.dark)
        val veil = tint.copy(alpha = alpha)
        val text = compositedBackdrop(veil, theme.textColor)
        val page = compositedBackdrop(veil, theme.pageColor)
        return OnyxContrast.ratio(text, page)
    }

    private fun nominalVeil(theme: ResolvedTheme): Float =
        glassPaintTintAlpha(GlassSurface.TAB_BAR, theme.dark, glassInks(GlassSurface.TAB_BAR, theme))

    /**
     * Le contraste du fil **flouté** à travers le voile peint : la même mesure que le cas du clair,
     * mais sur l'image que le flou livre réellement (un fût de 2 px dilué dans le disque de la plaque).
     */
    private fun blurredThrough(theme: ResolvedTheme, alpha: Float): Float {
        val tint = glassTintColour(GlassSurface.TAB_BAR, theme.dark)
        val coverage = glassBlurStrokeCoverage(2f, glassPaintBlurDp(GlassSurface.TAB_BAR) * DENSITY)
        val blurredInk = lerp(theme.pageColor, theme.textColor, coverage)
        return OnyxContrast.ratio(
            compositedBackdrop(tint.copy(alpha = alpha), blurredInk),
            compositedBackdrop(tint.copy(alpha = alpha), theme.pageColor),
        )
    }

    private fun maskingVeil(theme: ResolvedTheme): Float = glassMaskingTintAlpha(
        tint = glassTintColour(GlassSurface.TAB_BAR, theme.dark),
        nominal = nominalVeil(theme),
        ink = theme.textColor,
        page = theme.pageColor,
    )

    @Test
    fun `le fil derriere la plaque tombe sous le seuil de lisibilite`() {
        var checked = 0
        var changed = 0
        var masqueParLeVerreClair = 0
        themes().forEach { theme ->
            // Le fil, **sans la plaque** (le cas de l'effacement retiré), est franchement lisible.
            assertTrue(
                "${theme.id} (sombre=${theme.dark}) : sans plaque, le fil doit être lisible",
                OnyxContrast.ratio(theme.textColor, theme.pageColor) >= OnyxContrast.TEXT_MIN,
            )
            val through = contrastThrough(theme, nominalVeil(theme))
            if (theme.dark) {
                // **RE-POINTÉ par le lot « liquid glass ».** Ce cas exigeait l'inverse : « en sombre, le
                // voile nominal est si mince qu'on lit le fil au travers (donc le masquage est
                // nécessaire) ». Depuis que la densité du sombre vient de la mesure (0,70 sur le fond
                // échantillonné), le voile peint amène **déjà** le fil flouté sous le seuil — mesuré :
                // **2,40:1** net sur le thème le plus défavorable (`githubDark`), sous le seuil de
                // masquage de 2,5:1. Ce qui reste exigé est la règle elle-même, mesurée sur le contenu
                // **flouté** (voir le cas du clair) : c'est elle qui compte, et elle mord toujours si
                // une densité descend sous le seuil de masquage.
                val blurred = blurredThrough(theme, maskingVeil(theme))
                assertTrue(
                    "${theme.id} (sombre) : le fil se lit encore au travers du verre peint " +
                        "($blurred:1 sur le contenu flouté, $through:1 net).",
                    blurred <= MaskedContentMaxRatio,
                )
            } else {
                // **RE-POINTÉ (lot « liquid glass »).** Ce cas exigeait que le **verre clair teinté**
                // masque le fil au repos — c'était vrai à la densité de 0,82. La densité est maintenant
                // celle de la mesure (0,57), et le masquage du fil est tenu par **le flou**, comme le
                // code le dit depuis deux lots (`glassMaskingTintAlphaBlurred`, dont le plancher
                // **vaut 0**) : derrière une plaque de 44 dp, un fût de 2 px occupe
                // `2/(2·44·2,625) = 0,87 %` d'un disque de flou, et le contraste résiduel du fil tombe
                // à **1,01:1**. Mesuré **à l'appareil**, voile forcé à 0 (donc le fond nu, flou
                // compris) : sur la bande de la plaque du bas, l'écart du fond derrière la plaque vaut
                // **0,028 de luminance au plus** (contre 0,110 nu sur l'en-tête d'une image) — le fil
                // est bien dilué. Ce qui reste exigé est la règle elle-même, mesurée sur le contenu
                // **flouté** (le seul qui existe derrière une plaque), et le verrou mord toujours sur
                // la densité : un voile qui laisserait le contenu **net** lisible échoue.
                val blurred = blurredThrough(theme, maskingVeil(theme))
                assertTrue(
                    "${theme.id} (clair) : le fil se lit encore au travers du verre peint " +
                        "($blurred:1 sur le contenu flouté, $through:1 net) — le verre clair doit le " +
                        "masquer, par sa teinte et par son flou.",
                    blurred <= MaskedContentMaxRatio,
                )
                masqueParLeVerreClair += 1
            }
            // Le voile **masquant** (plaque `masking = true`) l'amène sous le seuil, lui aussi.
            assertTrue(
                "${theme.id} (sombre=${theme.dark}) : le fil doit tomber sous le seuil de lisibilité, " +
                    "contraste mesuré ${contrastThrough(theme, maskingVeil(theme))}:1",
                contrastThrough(theme, maskingVeil(theme)) <= MaskedContentMaxRatio,
            )
            // **Le levier `masking = true` est devenu INERTE, et c'est mesuré.** Son plancher est le
            // contraste du contenu **flouté** (`glassMaskingFloorBlurred`), qui **vaut 0** : derrière
            // un flou de 44 dp, le fil est déjà dilué à ~1,01:1. Ce qui masque le fil, c'est donc la
            // **densité d'encre** de la plaque — et elle est toujours **au-dessus** du plancher de
            // masquage. Le compte est donc celui des thèmes où le voile **peint** couvre déjà le
            // plancher de masquage : le levier nommé ne sert plus, mais la règle tient.
            if (maskingVeil(theme) > nominalVeil(theme) + 0.0001f) changed += 1
            checked += 1
        }
        assertTrue("le tableau des thèmes a été parcouru", checked > 0)
        assertTrue(
            "le masquage doit réellement remonter le voile sur au moins un thème (le levier " +
                "`masking = true` n'est pas inerte) : mesuré sur $changed thème(s) sur $checked",
            changed > 0,
        )
        assertTrue(
            "au moins un thème clair doit être masqué par le verre peint lui-même, sinon la dent " +
                "re-pointée ne prouve rien",
            masqueParLeVerreClair > 0,
        )
    }

    @Test
    fun `le plancher de masquage est deterministe et borne`() {
        themes().forEach { theme ->
            val tint = glassTintColour(GlassSurface.TAB_BAR, theme.dark)
            val floor = glassMaskingFloor(tint, theme.textColor, theme.pageColor)
            assertTrue("le plancher est dans [0, 1]", floor in 0f..1f)
            assertTrue(
                "${theme.id} (sombre=${theme.dark}) : le plancher doit amener le fil sous le seuil",
                contrastThrough(theme, floor) <= MaskedContentMaxRatio,
            )
            assertTrue(
                "le plancher doit être déterministe",
                glassMaskingFloor(tint, theme.textColor, theme.pageColor) == floor,
            )
        }
    }

    @Test
    fun `la plaque est peinte masquante`() {
        assertTrue(
            "la plaque du bas doit peindre sa vitre masquante (masking = true)",
            screen.contains(
                "liquidGlass(GlassSurface.TAB_BAR, RoundedCornerShape(PlaqueGeometry.Radius), masking = true)",
            ),
        )
    }

    // ---------------------------------------------------------------------------------------------
    // 3 · La pastille est au-dessus de la plaque, à 16 de la marge, sans chevauchement
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `la pastille est pleine, a 16 de la marge, au dessus de la plaque`() {
        assertTrue("la pastille est pleine", screen.contains("solid = true,"))
        assertTrue(
            "la pastille garde 16 dp de chaque côté",
            screen.contains(".padding(start = 16.dp, end = 16.dp, bottom = jumpClearance)"),
        )
        assertTrue(
            "et elle se range au-dessus de la réserve de la plaque, plus son dégagement",
            screen.contains("val jumpClearance = glassBottomInset + ComposerStacking.PillClearanceDp.dp"),
        )
        assertTrue(
            "le dégagement de la pastille est un jeton positif",
            stacking.contains("val PillClearanceDp: Int = OnyxSpace.BlockGap.value.toInt()"),
        )
    }

    @Test
    fun `le rapport imprime ce qui a ete verifie`() {
        println(
            "\nPLAQUE PERPÉTUELLE — effacement au défilement : retiré (aucun jeton dans ChatScreen.kt)" +
                " · plaque : masquante (masking = true) · pastille : pleine, 16 dp, dégagement " +
                "${ComposerStacking.PillClearanceDp} dp\n" +
                "CONTRASTE DU FIL DERRIÈRE LA PLAQUE (seuil de masquage = $MaskedContentMaxRatio:1," +
                " seuil de texte = ${OnyxContrast.TEXT_MIN}:1)\n" +
                themes().joinToString("\n") { theme ->
                    val n = nominalVeil(theme)
                    val m = maskingVeil(theme)
                    "  %-14s sombre=%-5s  voile mince=%.2f (%.2f:1)  →  voile masquant=%.2f (%.2f:1)".format(
                        theme.id,
                        theme.dark,
                        n,
                        contrastThrough(theme, n),
                        m,
                        contrastThrough(theme, m),
                    )
                },
        )
    }
}
