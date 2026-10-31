package com.onyx.app.ui.design

import androidx.compose.ui.unit.dp
import com.onyx.app.ui.theme.OnyxFonts
import com.onyx.app.ui.theme.OnyxTheme
import com.onyx.app.ui.theme.ResolvedTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le verrou du **champ de saisie** et de l'**empilement** du bas.
 *
 * Le défaut mesuré : clavier ouvert, une **bande claire** se lisait derrière le texte du champ sur
 * un thème sombre, et le compositeur recouvrait à moitié la barre à cinq onglets. Aucune de ces
 * deux choses ne pouvait casser un test — ce fichier est ce test.
 *
 * Tout est calculé par les mêmes fonctions pures que l'écran appelle : le chiffre annoncé et le
 * chiffre vérifié sont le même.
 */
class ComposerFieldTest {

    private val themes = OnyxTheme.entries.toList()
    private fun light(theme: OnyxTheme) = ResolvedTheme.resolve(theme, dark = false, fonts = OnyxFonts.System)
    private fun dark(theme: OnyxTheme) = ResolvedTheme.resolve(theme, dark = true, fonts = OnyxFonts.System)

    // — Le fond du champ ne doit jamais être un blanc d'aplat en sombre —

    @Test
    fun `aucun theme sombre ne pose un fond de champ clair`() {
        themes.forEach { theme ->
            assertTrue("le fond du champ de ${theme.id} est clair en sombre", fieldSurfaceIsSafe(dark(theme)))
            assertFalse("${theme.id}: $FIELD_BAND", isNearWhite(fieldSurfaceColour(dark(theme))))
        }
    }

    @Test
    fun `le voile du champ ne vaut jamais du blanc pur`() {
        themes.forEach { theme ->
            // La teinte vient de la page, pas d'une couleur nouvelle : elle suit la page de près.
            val page = dark(theme).pageColor
            val veil = fieldSurfaceColour(dark(theme))
            assertTrue("${theme.id}: le voile du champ est un blanc d'aplat", !isNearWhite(veil))
            assertTrue("${theme.id}: le voile ne suit pas la page", veil.red <= page.red + 0.06f)
        }
    }

    // — La sélection : teinte d'accent translucide, encre lisible, jamais du blanc —

    @Test
    fun `la selection prend la teinte de l accent et reste lisible`() {
        themes.forEach { theme ->
            listOf(light(theme), dark(theme)).forEach { resolved ->
                val fill = fieldSelectionColours(resolved).backgroundColor
                assertFalse("${resolved.id}/${if (resolved.dark) "sombre" else "clair"}: sélection blanche", isNearWhite(fill))
                assertTrue("la sélection n'est pas translucide", fill.alpha < 1f)
                assertTrue(
                    "${resolved.id}: encre de sélection ${selectionInkContrast(resolved)}",
                    selectionInkContrast(resolved) >= TEXT_CONTRAST_MIN,
                )
            }
        }
    }

    @Test
    fun `les poignees et le curseur prennent l accent`() {
        themes.forEach { theme ->
            listOf(light(theme), dark(theme)).forEach { resolved ->
                assertEquals(resolved.accent, fieldSelectionColours(resolved).handleColor)
                assertEquals(resolved.accent, fieldCursorColour(resolved))
            }
        }
    }

    @Test
    fun `l opacite de la selection recule jusqu au seuil, jamais en dessous`() {
        themes.forEach { theme ->
            listOf(light(theme), dark(theme)).forEach { resolved ->
                val alpha = selectionAlphaFor(resolved)
                assertTrue("la sélection disparaît (alpha $alpha)", alpha >= 0.12f)
                assertTrue(selectionInkContrast(resolved) >= TEXT_CONTRAST_MIN)
            }
        }
    }

    // — L'empilement : les deux marges ne s'additionnent pas —

    @Test
    fun `la marge basse ne cumule jamais le clavier et la navigation`() {
        val cases = listOf(
            0 to 24,    // clavier fermé, navigation par gestes
            0 to 48,    // clavier fermé, trois boutons
            900 to 24,  // clavier ouvert
            900 to 48,
            1180 to 0,  // pliable déplié : la zone de navigation disparaît sous le clavier
        )
        cases.forEach { (ime, nav) ->
            assertTrue("ime=$ime nav=$nav cumule", ComposerStacking.marginsDoNotCumulate(ime, nav))
            assertEquals(
                "ime=$ime nav=$nav",
                maxOf(ime, nav) + SafeBarSpacing.GapDp,
                ComposerStacking.stackBottomMarginDp(ime, nav),
            )
        }
    }

    @Test
    fun `la marge basse prend le plus grand des deux encarts`() {
        assertEquals(900 + SafeBarSpacing.GapDp, ComposerStacking.stackBottomMarginDp(imeDp = 900, navigationBarsDp = 24))
        assertEquals(48 + SafeBarSpacing.GapDp, ComposerStacking.stackBottomMarginDp(imeDp = 0, navigationBarsDp = 48))
        // Un encart négatif n'existe pas : il ne rabote pas la marge.
        assertEquals(SafeBarSpacing.GapDp, ComposerStacking.stackBottomMarginDp(imeDp = -4, navigationBarsDp = -4))
    }

    // — La barre d'onglets : plus aucun masquage —
    //
    // Il n'y a **plus rien à éprouver ici**, et c'est le résultat : les règles de masquage à la main
    // ([ComposerStacking.tabBarVisible], `tabBarAlpha`, `toggled`, `handleLabelKey`) et la géométrie
    // de la poignée (`TabBarHandle`) ont été **retirées** avec le bouton qu'elles commandaient, sur
    // l'ordre du propriétaire (« supprime le bouton pour la possibilité de cacher »). La garantie
    // n'est donc plus un comportement mais une **absence**, et elle se lit dans les sources : c'est
    // ce que verrouille `RetraitPoigneeChevronTest` (module `app`, où les fichiers sont lisibles).
    //
    // Ce qui restait du masquage était l'effacement **au défilement** de la pile entière. Il a été
    // **retiré** sur l'ordre du propriétaire (« ça ne doit JAMAIS disparaître quand je défile ») :
    // il n'y a donc plus de règle de pile à éprouver ici, et la garantie est une **absence** —
    // verrouillée par `PlaquePerpetuelleTest` (module `app`). Ce qui survit du mouvement, c'est la
    // seule décision du bouton flottant ([ComposerStacking.fadeSpec]).

    @Test
    fun `la seule decision de mouvement restante est le fondu du bouton flottant`() {
        assertTrue(ComposerStacking.fadeSpec<Float>(reduceMotion = false) !is androidx.compose.animation.core.SnapSpec<*>)
        assertTrue(ComposerStacking.fadeSpec<Float>(reduceMotion = true) is androidx.compose.animation.core.SnapSpec<*>)
    }

    // — Le voile : jamais un bandeau plus blanc que la page, en clair comme en sombre —

    @Test
    fun `aucun voile n est plus clair que la page, dans les deux apparences`() {
        val surfaces = FrostSurface.entries.toList()
        val levels = FrostLevel.entries.toList()
        themes.forEach { theme ->
            listOf(light(theme), dark(theme)).forEach { resolved ->
                surfaces.forEach { surface ->
                    levels.forEach { level ->
                        assertTrue(
                            "${resolved.id}/${if (resolved.dark) "sombre" else "clair"} ${surface.name} $level : " +
                                "le voile est plus clair que la page",
                            veilIsNeverLighterThanPage(resolved, surface, level),
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `en clair la vitre garde exactement la page`() {
        // C'est la règle qui a coûté un aller-retour : la vitre était tirée de 5 % vers le blanc,
        // donc le compositeur peignait un bandeau plus clair que la page (mesuré sur « Ardoise »
        // clair : page #DADFE7, fond du compositeur #DCE0E8).
        themes.forEach { theme ->
            val resolved = light(theme)
            val veil = veilColour(resolved.pageColor, frostVeilAlpha(FrostSurface.COMMAND_BAR, FrostLevel.FULL, dark = false), dark = false)
            assertEquals("${theme.id}: le voile clair ne suit plus la page", resolved.pageColor.red, veil.red, 0.0005f)
            assertEquals("${theme.id}: le voile clair ne suit plus la page", resolved.pageColor.green, veil.green, 0.0005f)
            assertEquals("${theme.id}: le voile clair ne suit plus la page", resolved.pageColor.blue, veil.blue, 0.0005f)
        }
    }

    // — Les surfaces « en ligne » : code, citation, mention, lien —

    @Test
    fun `le code en ligne n est jamais une pastille claire sur un theme sombre`() {
        themes.forEach { theme ->
            val resolved = dark(theme)
            assertTrue("${theme.id}: le code en ligne est clair en sombre", inlineCodeRuleHolds(resolved))
            assertFalse(isNearWhite(inlineCodeSurface(resolved)))
            assertTrue(
                "${theme.id}: encre de code ${inlineCodeContrast(resolved)}",
                inlineCodeContrast(resolved) >= INLINE_INK_MIN,
            )
        }
    }

    @Test
    fun `le code en ligne garde une encre lisible en clair aussi`() {
        themes.forEach { theme ->
            val resolved = light(theme)
            assertTrue(inlineCodeRuleHolds(resolved))
            assertTrue(inlineCodeContrast(resolved) >= INLINE_INK_MIN)
        }
    }

    @Test
    fun `le bloc de code suit la surface du theme`() {
        themes.forEach { theme ->
            assertFalse("${theme.id}: bloc de code clair en sombre", isNearWhite(codeBlockSurface(dark(theme))))
        }
    }

    @Test
    fun `la citation, la mention et le lien restent des teintes du theme`() {
        themes.forEach { theme ->
            val resolved = dark(theme)
            assertFalse(isNearWhite(quoteRuleColour(resolved)))
            assertFalse(isNearWhite(mentionSurface(resolved)))
            assertEquals(resolved.linkColor, linkInk(resolved))
        }
    }

    private companion object {
        const val FIELD_BAND = "le fond du champ est un blanc d'aplat"
    }
}
