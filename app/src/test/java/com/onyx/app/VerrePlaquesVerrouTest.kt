package com.onyx.app

import com.onyx.app.ui.design.GlassBlurMinDp
import com.onyx.app.ui.design.GlassInk
import com.onyx.app.ui.design.GlassSurface
import com.onyx.app.ui.design.MaskedContentMaxRatio
import com.onyx.app.ui.design.OnyxContrast
import com.onyx.app.ui.design.PlaqueGeometry
import com.onyx.app.ui.design.compositedBackdrop
import com.onyx.app.ui.design.glassBlurStrokeCoverage
import com.onyx.app.ui.design.glassInks
import com.onyx.app.ui.design.glassMaskingFloorBlurred
import com.onyx.app.ui.design.glassMaskingTintAlphaBlurred
import com.onyx.app.ui.design.glassPaintBlurDp
import com.onyx.app.ui.design.glassPaintTintAlpha
import com.onyx.app.ui.design.glassPlateEdgeAlpha
import com.onyx.app.ui.design.glassPlateEdgeDp
import com.onyx.app.ui.design.glassPlateEdgeUsed
import com.onyx.app.ui.design.glassTintColour
import com.onyx.app.ui.theme.OnyxFonts
import com.onyx.app.ui.theme.OnyxTheme
import com.onyx.app.ui.theme.ResolvedTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **Le verrou du lot « verre-plaques » — trois morsures, et il mord AVANT comme APRÈS.**
 *
 * La demande du propriétaire, sur capture d'appareil, entourait deux zones en rouge : la **plaque de
 * l'en-tête** et la **plaque du compositeur / barre d'onglets**, avec un seul reproche — « il manque
 * du liquid glass, c'est pas assez prononcé ». Relevé avant ce lot, au pixel, sur l'émulateur :
 *
 * | plaque | corps (clair) | arête (clair) | voile |
 * |---|---|---|---|
 * | en-tête | `#FFFFFF` (L 1,0000) | une seule ligne sombre de filet, **aucune arête claire** | opaque |
 * | compositeur / onglets | `#FFFFFF` (L 1,0000) | idem | opaque (plancher de masquage du **texte net**) |
 *
 * Trois pièces sont donc verrouillées ici, et chacune **nomme la valeur mesurée** quand elle tombe :
 *
 *  1. **le verre des plaques** — flou nul, voile opaque ou arête absente sur l'une des surfaces de la
 *     famille flottante (en-tête, plaque du bas, carte du compositeur, plaque d'onglet) → échec ;
 *  2. **la lisibilité** — une encre posée sur la vitre d'une de ces plaques qui passe sous le seuil de
 *     la maison (4,5:1 pour du texte, 3:1 pour une icône), **dans l'une des deux apparences** ;
 *  3. **le masquage par le flou** — le plancher de masquage doit être calculé sur le contenu **flouté**
 *     (sinon le voile remonte à l'aplat et la plaque cesse d'être une vitre), et la plaque d'onglet
 *     doit rester **portée par le pageur** dans `ChatScreen` (le contenu d'un onglet qui n'est plus sur
 *     une plaque ne glisse plus « comme une plaque de verre »).
 */
class VerrePlaquesVerrouTest {

    /** Les plaques flottantes de l'écran de conversation, dans l'ordre où elles se lisent. */
    private val plaques = listOf(
        GlassSurface.THREAD_TOP_BAR,
        GlassSurface.TAB_BAR,
        GlassSurface.COMPOSER,
        GlassSurface.PANE,
    )

    private fun themes(): List<ResolvedTheme> = buildList {
        for (name in OnyxTheme.entries) {
            for (dark in listOf(false, true)) {
                add(ResolvedTheme(theme = name, palette = name.palette, dark = dark, fonts = OnyxFonts.System))
            }
        }
    }

    private fun code(relative: String): String {
        val candidates = listOf(File(relative), File("../$relative"), File("core/$relative"))
        val file = candidates.firstOrNull { it.exists() }
            ?: error("source introuvable : $relative (depuis ${File(".").absolutePath})")
        return file.readText()
    }

    // ---------------------------------------------------------------------------------------------
    // 1 · Le verre des plaques — flou, voile, arête
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `les deux plaques gardent leur verre — flou, voile mince, arete`() {
        val report = StringBuilder("\nVERRE DES PLAQUES — flou peint, voile peint, arête\n")
        report.append("surface               flou(dp)  voile clair  voile sombre  arête(dp)  arête(α clair)  arête(α sombre)\n")
        plaques.forEach { surface ->
            val blur = glassPaintBlurDp(surface)
            assertTrue(
                "$surface : le flou peint est NUL ($blur dp) — la plaque n'est plus une vitre, " +
                    "c'est un aplat. Un flou de 36 dp au moins est exigé (relevé avant le lot : 36 dp, " +
                    "donc présent ; c'est le lot qui l'a relevé à 44 dp pour la plaque du bas).",
                blur >= GlassBlurMinDp,
            )
            listOf(false, true).forEach { dark ->
                val veil = glassPaintTintAlpha(surface, dark, glassInks(surface, themeFor(dark)))
                assertTrue(
                    "$surface (sombre=$dark) : le voile peint vaut $veil — une vitre OPAQUE n'est plus " +
                        "une vitre (le plancher est un plafond de 1,0 et la maison exige une vitre mince).",
                    veil < 1f,
                )
            }
            assertTrue(
                "$surface : l'arête de verre n'est plus peinte (glassPlateEdgeUsed = false) — c'est " +
                    "exactement le défaut relevé sur la capture du propriétaire : les deux plaques " +
                    "étaient peintes SANS arête (le jeton de la planche, OnyxEdge.topEdge, n'était " +
                    "appelé par personne).",
                glassPlateEdgeUsed(surface),
            )
            assertTrue(
                "$surface : l'épaisseur peinte de l'arête vaut ${glassPlateEdgeDp(surface)} dp — " +
                    "la planche la donne à ${PlaqueGeometry.TopEdge.value} dp.",
                glassPlateEdgeDp(surface) > 0f,
            )
            listOf(false, true).forEach { dark ->
                val alpha = glassPlateEdgeAlpha(surface, dark)
                assertTrue(
                    "$surface (sombre=$dark) : l'arête est peinte à $alpha d'opacité — trop faible " +
                        "pour se lire sur le corps de la plaque (planche : 0,98 en clair, 0,62 en sombre).",
                    alpha >= minOf(
                        PlaqueGeometry.TopEdgeAlphaDark,
                        PlaqueGeometry.TopEdgeAlphaLight,
                    ),
                )
            }
            report.append(
                "%-21s %-9.1f %-12.3f %-13.3f %-10.2f %-15.2f %.2f\n".format(
                    surface.name,
                    blur,
                    glassPaintTintAlpha(surface, false, glassInks(surface, themeFor(false))),
                    glassPaintTintAlpha(surface, true, glassInks(surface, themeFor(true))),
                    glassPlateEdgeDp(surface),
                    glassPlateEdgeAlpha(surface, false),
                    glassPlateEdgeAlpha(surface, true),
                ),
            )
        }
        print(report)
    }

    /** Le thème de l'apparence demandée, dans la famille par défaut (`OnyxTheme.entries`). */
    private fun themeFor(dark: Boolean): ResolvedTheme =
        ResolvedTheme(
            theme = OnyxTheme.entries.first(),
            palette = OnyxTheme.entries.first().palette,
            dark = dark,
            fonts = OnyxFonts.System,
        )

    // ---------------------------------------------------------------------------------------------
    // 2 · La lisibilité — l'encre de la plaque tient son seuil, dans les DEUX apparences
    // ---------------------------------------------------------------------------------------------

    /**
     * **Le verrou de lisibilité de la maison**, tenu sur les plaques : pour chaque thème et chaque
     * apparence, la pire encre posée sur la vitre **réellement peinte** (voile compris, masquage
     * compris) doit tenir le seuil de sa famille — 4,5:1 pour du texte, 3:1 pour une icône, et pour
     * une surface à encre dérivée l'encre qu'elle **choisit** (le noir ou le blanc).
     */
    @Test
    fun `l encre des plaques tient son seuil dans les deux apparences`() {
        val report = StringBuilder("\nLISIBILITÉ DES PLAQUES — pire encre sur la vitre peinte\n")
        report.append("surface                thème/apparence                     pire ratio  seuil  verdict\n")
        var checked = 0
        themes().forEach { theme ->
            plaques.forEach { surface ->
                val inks = glassInks(surface, theme)
                val tint = glassTintColour(theme.dark)
                val blurred = sourceMaskedAndBlurred(surface)
                val painted = if (blurred) {
                    glassMaskingTintAlphaBlurred(
                        tint = tint,
                        nominal = glassPaintTintAlpha(surface, theme.dark, inks),
                        ink = theme.textColor,
                        page = theme.pageColor,
                        blurPx = glassPaintBlurDp(surface) * DENSITY,
                    )
                } else {
                    glassPaintTintAlpha(surface, theme.dark, inks)
                }
                val veil = tint.copy(alpha = painted)
                val worst = listOf(androidx.compose.ui.graphics.Color.White, androidx.compose.ui.graphics.Color.Black)
                    .minOf { under ->
                        val backdrop = compositedBackdrop(veil, under)
                        when (surface.ink) {
                            GlassInk.DERIVED -> maxOf(
                                OnyxContrast.ratio(androidx.compose.ui.graphics.Color.Black, backdrop),
                                OnyxContrast.ratio(androidx.compose.ui.graphics.Color.White, backdrop),
                            )

                            else -> inks.minOf { OnyxContrast.ratio(it, backdrop) }
                        }
                    }
                report.append(
                    "%-22s %-35s %-11.2f %-6.1f %s\n".format(
                        surface.name,
                        "${theme.id}/${if (theme.dark) "sombre" else "clair"}",
                        worst,
                        surface.ink.minRatio,
                        if (worst >= surface.ink.minRatio) "ok" else "ÉCHEC",
                    ),
                )
                assertTrue(
                    "$surface ${theme.id}/${if (theme.dark) "sombre" else "clair"} : le pire rapport " +
                        "d'encre sur la vitre peinte vaut $worst — sous le seuil de la maison " +
                        "(${surface.ink.minRatio}:1). Un verre plus prononcé ne peut pas coûter la " +
                        "lisibilité : c'est un levier à changer, pas un seuil à baisser.",
                    worst >= surface.ink.minRatio,
                )
                checked++
            }
        }
        report.append("couples vérifiés : $checked\n")
        print(report)
        assertEquals("tous les couples thème × apparence × plaque", themes().size * plaques.size, checked)
    }

    /** La surface masque-t-elle son contenu ? — la même porte que `liquidGlass` (source-lock). */
    private fun sourceMaskedAndBlurred(surface: GlassSurface): Boolean = surface == GlassSurface.TAB_BAR

    // ---------------------------------------------------------------------------------------------
    // 3 · Le masquage par le flou, et la plaque qui glisse
    // ---------------------------------------------------------------------------------------------

    /**
     * **Le plancher de masquage d'une plaque se mesure sur le contenu FLUTÉ.** Mesuré avant ce lot :
     * le plancher du texte net montait le voile jusqu'à l'aplat — plus rien ne se voyait au travers,
     * donc plus de verre. Le modèle du flou est vérifié ici, chiffre par chiffre.
     */
    @Test
    fun `le plancher de masquage tient compte du flou`() {
        val theme = themeFor(dark = false)
        val tint = glassTintColour(theme.dark)
        // Un fût de 2 px dans un flou de 36 dp à 2,625× (94,5 px) : la part occupée.
        val coverage = glassBlurStrokeCoverage(2f, 36f * DENSITY)
        assertEquals(
            "la part d'un disque de flou qu'occupe un fût de 2 px dans 36 dp à 2,625×",
            2f / (2f * 36f * DENSITY),
            coverage,
            1e-6f,
        )
        assertTrue("la dilution du fût doit être écrasante (relevé $coverage)", coverage < 0.02f)
        val floor = glassMaskingFloorBlurred(tint, theme.textColor, theme.pageColor, 36f * DENSITY)
        assertEquals(
            "le contenu flouté est DÉJÀ sous le seuil de masquage : le plancher doit valoir 0 " +
                "(aucun voile n'est nécessaire), relevé $floor",
            0f,
            floor,
            1e-6f,
        )
        val through = run {
            val blurred = androidx.compose.ui.graphics.lerp(theme.pageColor, theme.textColor, coverage)
            OnyxContrast.ratio(blurred, theme.pageColor)
        }
        assertTrue(
            "le contraste du contenu flouté derrière la plaque doit tomber sous le seuil de " +
                "masquage ($MaskedContentMaxRatio:1) — relevé $through:1",
            through <= MaskedContentMaxRatio,
        )
    }

    /**
     * **Le contenu d'un onglet est porté par une plaque, et il glisse avec elle.** Le pageur ne
     * translate que ce qu'on lui donne : si une page de contenu n'est plus habillée par
     * `panePlate`, l'onglet redevient du contenu nu — le défaut relevé sur les images prises à
     * mi-course (aucune arête ne suivait le doigt).
     */
    @Test
    fun `le contenu d un onglet est porte par une plaque qui glisse`() {
        val pager = code("src/main/java/com/onyx/app/ui/chat/ChatScreen.kt")
        val plateCalls = Regex("panePlate\\(").findAll(pager).count()
        assertTrue(
            "ChatScreen ne pose plus la plaque de verre des onglets : `panePlate(` est appelé " +
                "$plateCalls fois — les pages de contenu du pageur (Flux, Fichiers) doivent " +
                "chacune le porter, sinon le contenu glisse SANS plaque (défaut du propriétaire).",
            plateCalls >= 2,
        )
        assertTrue(
            "le pageur doit rester la source du glissement (HorizontalPager) : la plaque n'a pas " +
                "de geste à elle, c'est le pageur qui la translate sous le doigt.",
            pager.contains("HorizontalPager("),
        )
        val plate = code("../core/src/main/java/com/onyx/app/ui/design/LiquidGlass.kt")
        assertTrue(
            "la plaque d'onglet doit être posée par `panePlate` sur la surface PANE",
            plate.contains("fun Modifier.panePlate(theme: ResolvedTheme): Modifier") &&
                plate.contains(".liquidGlass(GlassSurface.PANE, shape)"),
        )
        assertTrue(
            "la surface PANE doit rester une vitre échantillonnable à l'écran (IN_SCREEN) — sinon " +
                "elle ne floute rien",
            GlassSurface.PANE.sampling == com.onyx.app.ui.design.GlassSampling.IN_SCREEN,
        )
    }

    private companion object {
        /** La densité de l'appareil de recette : onyxglass, 420 dpi = 2,625. */
        const val DENSITY = 2.625f
    }
}
