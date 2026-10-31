package com.onyx.app.ui.chat

import com.onyx.app.ui.design.ComposerStacking
import com.onyx.app.ui.design.GlassSurface
import com.onyx.app.ui.design.MaskedContentMaxRatio
import com.onyx.app.ui.design.OnyxContrast
import com.onyx.app.ui.design.compositedBackdrop
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

    /** Le contraste, **à travers un voile d'opacité α**, entre l'encre du fil et son fond. */
    private fun contrastThrough(theme: ResolvedTheme, alpha: Float): Float {
        val tint = glassTintColour(theme.dark)
        val veil = tint.copy(alpha = alpha)
        val text = compositedBackdrop(veil, theme.textColor)
        val page = compositedBackdrop(veil, theme.pageColor)
        return OnyxContrast.ratio(text, page)
    }

    private fun nominalVeil(theme: ResolvedTheme): Float =
        glassPaintTintAlpha(GlassSurface.TAB_BAR, theme.dark, glassInks(GlassSurface.TAB_BAR, theme))

    private fun maskingVeil(theme: ResolvedTheme): Float = glassMaskingTintAlpha(
        tint = glassTintColour(theme.dark),
        nominal = nominalVeil(theme),
        ink = theme.textColor,
        page = theme.pageColor,
    )

    @Test
    fun `le fil derriere la plaque tombe sous le seuil de lisibilite`() {
        var checked = 0
        var changed = 0
        themes().forEach { theme ->
            // Le fil, **sans la plaque** (le cas de l'effacement retiré), est franchement lisible.
            assertTrue(
                "${theme.id} (sombre=${theme.dark}) : sans plaque, le fil doit être lisible",
                OnyxContrast.ratio(theme.textColor, theme.pageColor) >= OnyxContrast.TEXT_MIN,
            )
            // Le voile **mince** (avant la correction) laissait le fil au-dessus du seuil de masquage.
            assertTrue(
                "${theme.id} (sombre=${theme.dark}) : le voile mince laissait lire le fil " +
                    "(${contrastThrough(theme, nominalVeil(theme))}:1)",
                contrastThrough(theme, nominalVeil(theme)) > MaskedContentMaxRatio,
            )
            // Le voile **masquant** (après) l'amène sous le seuil : on ne lit plus à travers.
            assertTrue(
                "${theme.id} (sombre=${theme.dark}) : le fil doit tomber sous le seuil de lisibilité, " +
                    "contraste mesuré ${contrastThrough(theme, maskingVeil(theme))}:1",
                contrastThrough(theme, maskingVeil(theme)) <= MaskedContentMaxRatio,
            )
            if (maskingVeil(theme) > nominalVeil(theme) + 0.0001f) changed += 1
            checked += 1
        }
        assertTrue("le tableau des thèmes a été parcouru", checked > 0)
        assertTrue(
            "le masquage doit réellement remonter le voile sur au moins un thème, sinon la dent ne " +
                "prouve rien",
            changed > 0,
        )
    }

    @Test
    fun `le plancher de masquage est deterministe et borne`() {
        themes().forEach { theme ->
            val tint = glassTintColour(theme.dark)
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
