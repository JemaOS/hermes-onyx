package com.onyx.app.ui.design

import androidx.compose.ui.graphics.Color
import com.onyx.app.ui.theme.OnyxFonts
import com.onyx.app.ui.theme.OnyxTheme
import com.onyx.app.ui.theme.ResolvedTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 * **LE VERROU DU VERRE TRAVERSANT — quatre morsures, et chacune mord AVANT comme APRÈS.**
 *
 * Le propriétaire, capture à l'appui, en encadrant la plaque de l'en-tête et celle du compositeur +
 * barre d'onglets :
 *
 * > « normalement il doit y avoir l'effet liquid glass, la c'est pas le cas »
 *
 * **Ce qu'il voyait, mesuré.** Les deux plaques étaient **quasi opaques** : la densité de la teinte
 * (0,82) ne laissait passer que **18 %** du fond, et l'en-tête portait en plus, **sous** sa vitre, un
 * aplat OPAQUE ([OnyxSurfaces.surfaceHigh]) posé par `surfaceRole(SurfaceRole.Bar)` — une vitre posée
 * sur un aplat n'a rien à révéler, elle transporte l'aplat. Les deux causes sont ici, chacune avec sa
 * morsure.
 *
 * Les quatre morsures, toutes **chiffrées** :
 *
 *  1. **plaquE OPAQUE** — la traversée peinte d'une plaque descend sous [GlassPlateTraversalMin] ; ou
 *     un aplat opaque revient sous la vitre de l'en-tête ;
 *  2. **AUCUN FLOU DU FOND** — le flou peint descend sous le plancher de la famille ;
 *  3. **AUCUNE RÉFRACTION DE BORD** — le déplacement d'arête d'une plaque retombe au sub-pixel (la
 *     lentille disparaît), ou le reflet de tête n'est plus peint ;
 *  4. **ENCRE < 4,5:1 CONTRE LE FOND RÉEL** — le pire rapport d'encre d'une plaque passe sous
 *     [OnyxContrast.TEXT_MIN] **contre le fond réellement derrière elle** ([glassPlateBackdrops]).
 *
 * **Ce qui n'est pas fait à ce verrou** : il ne remplace aucune règle. L'ancienne borne (encre contre
 * blanc pur et noir pur) est **toujours dans le code** ([glassLegibilityFloor], et le test 5 le
 * vérifie) ; c'est le **fond de mesure** des plaques qui a changé, avec la raison écrite et le chiffre.
 */
class PlateTraversingLockTest {

    /** Les **trois** plaques flottantes de l'écran de conversation. */
    private val plaques = listOf(
        GlassSurface.THREAD_TOP_BAR,
        GlassSurface.TAB_BAR,
        GlassSurface.COMPOSER,
    )

    private fun theme(dark: Boolean): ResolvedTheme = ResolvedTheme(
        theme = OnyxTheme.entries.first(),
        palette = OnyxTheme.entries.first().palette,
        dark = dark,
        fonts = OnyxFonts.System,
    )

    private fun codeFile(relative: String): File {
        val candidates = listOf(
            File(relative),
            File("../$relative"),
            File("app/$relative"),
            File("../app/$relative"),
            File("core/$relative"),
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("source introuvable : $relative (depuis ${File(".").absolutePath})")
    }

    private fun code(relative: String): String = codeFile(relative).readText()

    private fun hex(c: Color): String = String.format(
        Locale.ROOT,
        "#%02X%02X%02X",
        (c.red * 255f).toInt().coerceIn(0, 255),
        (c.green * 255f).toInt().coerceIn(0, 255),
        (c.blue * 255f).toInt().coerceIn(0, 255),
    )

    // ---------------------------------------------------------------------------------------------
    // 1 · LA PLAQUE EST TRAVERSANTE — la part du fond qui passe au travers
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `la plaque laisse voir le fond derriere elle`() {
        val report = StringBuilder("\nTRAVERSÉE DES PLAQUES — la part du fond qui passe au travers\n")
        report.append("surface                apparence  teinte      densité  traversée  seuil  verdict\n")
        listOf(false, true).forEach { dark ->
            val t = theme(dark)
            plaques.forEach { surface ->
                val alpha = glassPaintTintAlpha(surface, dark, glassInks(surface, t))
                val traversal = glassPlatePaintedTraversal(surface, dark, glassInks(surface, t))
                report.append(
                    "%-22s %-10s %-11s %-8.2f %-10s %-6s %s\n".format(
                        Locale.ROOT,
                        surface.name,
                        if (dark) "sombre" else "clair",
                        hex(glassTintColour(surface, dark)),
                        alpha,
                        "${"%.0f".format(Locale.ROOT, 100f * traversal)} %",
                        "${"%.0f".format(Locale.ROOT, 100f * GlassPlateTraversalMin)} %",
                        if (traversal >= GlassPlateTraversalMin) "ok" else "ÉCHEC",
                    ),
                )
                assertTrue(
                    "${surface.name}/${if (dark) "sombre" else "clair"} : la traversée peinte vaut " +
                        "$traversal — SOUS le seuil mesuré ($GlassPlateTraversalMin). La plaque se lit " +
                        "comme une carte posée sur la page, et c'est exactement le défaut du " +
                        "propriétaire (« normalement il doit y avoir l'effet liquid glass, la c'est pas " +
                        "le cas »). Le levier est la DENSITÉ de la teinte (et, si la lisibilité " +
                        "l'exige, le fond sous l'encre) — jamais le seuil, jamais un aplat.",
                    traversal >= GlassPlateTraversalMin,
                )
            }
        }
        print(report)
    }

    /**
     * **L'aplat opaque sous la vitre est le premier défaut, et il ne revient pas.**
     *
     * L'en-tête appelait `surfaceRole(SurfaceRole.Bar)`, qui pose [OnyxSurfaces.surfaceHigh]
     * (`#FFFFFF` en clair) **sous** `.liquidGlass(...)` : la vitre n'avait rien à révéler. Le
     * remplacement ([glassBarShell]) garde la forme, le filet et l'ombre — tout ce qui détache une
     * barre — et **pas le fond**.
     */
    @Test
    fun `l en-tete ne peint pas d aplat opaque sous sa vitre`() {
        val topFile = codeFile("src/main/java/com/onyx/app/ui/chat/components/OnyxTopBar.kt")
        val top = topFile.readText()
        assertTrue(
            "fichier relu : ${topFile.absolutePath} (depuis ${File(".").absolutePath}) ; " +
                "occurrences de `surfaceRole(SurfaceRole.Bar)` = " +
                Regex("surfaceRole\\(SurfaceRole.Bar\\)").findAll(top).count(),
            !top.contains("surfaceRole(SurfaceRole.Bar)"),
        )
        assertTrue(
            "`OnyxTopBar` doit prendre la coque de barre (`glassBarShell(SurfaceRole.Bar)`) : " +
                "la forme, l'ombre et le filet de la maison, et le fond par la VITRE.",
            top.contains("glassBarShell(SurfaceRole.Bar)"),
        )
        val style = code("core/src/main/java/com/onyx/app/ui/design/OnyxSurfaceStyle.kt")
        assertTrue(
            "`glassBarShell` doit exister dans la couche de dessin : c'est la seule façon d'avoir " +
                "la coque d'une barre SANS son fond opaque.",
            style.contains("fun Modifier.glassBarShell(role: SurfaceRole"),
        )
        assertTrue(
            "la coque de barre ne doit peindre AUCUN fond : ni `background(`, ni le barreau du rôle.",
            !style.substringAfter("fun Modifier.glassBarShell(role: SurfaceRole")
                .substringBefore("\n}\n")
                .contains("background("),
        )
    }

    // ---------------------------------------------------------------------------------------------
    // 2 · LE FLOU DU FOND — sans lui, il n'y a rien à voir au travers
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `aucune plaque n a perdu son flou`() {
        plaques.forEach { surface ->
            val blur = glassPaintBlurDp(surface)
            assertTrue(
                "${surface.name} : le flou peint vaut $blur dp — sous le plancher de la famille " +
                    "($GlassBlurMinDp dp). Une plaque sans flou n'a pas de fond à montrer : c'est un " +
                    "aplat translucide.",
                blur >= GlassBlurMinDp,
            )
        }
    }

    // ---------------------------------------------------------------------------------------------
    // 3 · LE LIQUIDE — la lentille d'arête et le reflet de tête
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `la lentille d arete des plaques est visible, bornee, et le reflet de tete est peint`() {
        val report = StringBuilder("\nLE LIQUIDE DES PLAQUES — lentille d'arête et reflet de tête\n")
        report.append("surface                déplacement(dp)  bande(dp)  reflet(α clair)  reflet(α sombre)\n")
        val density = 2.625f
        plaques.forEach { surface ->
            val disp = glassRefractionDisplacementDp(surface)
            report.append(
                "%-22s %-16.2f %-10.1f %-16.2f %-16.2f\n".format(
                    Locale.ROOT,
                    surface.name,
                    disp,
                    GlassRefractionEdgeDp,
                    glassPlateGlossUsed(surface, false),
                    glassPlateGlossUsed(surface, true),
                ),
            )
            assertTrue(
                "${surface.name} : le déplacement d'arête vaut $disp dp — sub-pixel, donc AUCUNE " +
                    "réfraction visible. C'est le défaut relevé : un verre qui ne déforme pas son " +
                    "arête se lit comme une vitre plate. Les plaques prennent " +
                    "$GlassPlateRefractionDisplacementDp dp (mesuré à l'appareil) ; les petites " +
                    "surfaces gardent le sub-pixel, parce que c'est LEUR défaut qui a été signalé.",
                disp >= GlassPlateRefractionDisplacementDp,
            )
            assertTrue(
                "${surface.name} : le déplacement réel ($disp dp, soit " +
                    "${disp * density} px à 2,625×) doit rester borné — au-delà de la bande " +
                    "d'arête (${GlassRefractionEdgeDp} dp), la lentille tirerait l'étiquette voisine.",
                disp <= GlassRefractionEdgeDp / 2f,
            )
            listOf(false, true).forEach { dark ->
                assertTrue(
                    "${surface.name} (sombre=$dark) : le reflet de tête vaut " +
                        "${glassPlateGlossUsed(surface, dark)} — une plaque sans reflet n'est " +
                        "qu'un aplat bordé d'un liseré.",
                    glassPlateGlossUsed(surface, dark) > 0f,
                )
            }
        }
        print(report)
    }

    /**
     * **Les petites surfaces gardent le déplacement sub-pixel.** C'est la morsure du lot précédent,
     * et elle tient : le refus du propriétaire portait sur la tuile « Capacités » du volet
     * (« réfraction et déformation de ce qui se passe derrière »), pas sur les plaques.
     */
    @Test
    fun `les petites surfaces gardent un deplacement sous le pixel`() {
        GlassSurface.entries.filter { !it.plateEdge }.forEach { surface ->
            assertEquals(
                "${surface.name} : le déplacement doit rester sub-pixel ($GlassRefractionDisplacementDp dp)",
                GlassRefractionDisplacementDp,
                glassRefractionDisplacementDp(surface),
                0f,
            )
        }
    }

    // ---------------------------------------------------------------------------------------------
    // 4 · L'ENCRE CONTRE LE FOND RÉEL
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `l encre des plaques tient 4,5 a 1 contre le fond reellement derriere`() {
        val report = StringBuilder("\nENCRE DES PLAQUES — contre le fond réel (échantillonné)\n")
        report.append("surface                apparence  teinte      pire fond réel  pire encre  seuil  verdict\n")
        listOf(false, true).forEach { dark ->
            val t = theme(dark)
            val grounds = glassPlateBackdrops(dark)
            plaques.forEach { surface ->
                val alpha = glassPaintTintAlpha(surface, dark, glassInks(surface, t))
                val veil = glassTintColour(surface, dark).copy(alpha = alpha)
                val worst = glassWorstInkRatio(surface, glassPaintLevel, t)
                var ground = grounds.first()
                var best = Float.MAX_VALUE
                grounds.forEach { g ->
                    val backdrop = compositedBackdrop(veil, g)
                    val here = glassInks(surface, t).minOf { OnyxContrast.ratio(it, backdrop) }
                    if (here < best) {
                        best = here
                        ground = g
                    }
                }
                report.append(
                    "%-22s %-10s %-11s %-15s %-11.2f %-6.1f %s\n".format(
                        Locale.ROOT,
                        surface.name,
                        if (dark) "sombre" else "clair",
                        hex(glassTintColour(surface, dark)),
                        hex(ground),
                        worst,
                        surface.ink.minRatio,
                        if (worst >= surface.ink.minRatio) "ok" else "ÉCHEC",
                    ),
                )
                assertTrue(
                    "${surface.name}/${if (dark) "sombre" else "clair"} : le pire rapport d'encre " +
                        "contre le fond RÉELLEMENT derrière la plaque vaut $worst — sous 4,5:1. Le " +
                        "verre traversant ne peut pas coûter la lisibilité : le levier est la densité, " +
                        "l'encre, ou un fond protégé sous le texte — jamais le seuil.",
                    worst >= surface.ink.minRatio,
                )
            }
        }
        print(report)
    }

    // ---------------------------------------------------------------------------------------------
    // 5 · LA BORNE DE LA MAISON — l'ancienne ET la nouvelle, écrites dans le code
    // ---------------------------------------------------------------------------------------------

    /**
     * **Un verrou de contraste re-pointe n'est pas efface.** L'ancienne règle (encre contre **blanc
     * pur et noir pur**) et la nouvelle (encre contre **le fond échantillonné derrière la plaque**)
     * doivent **toutes les deux** être dans le code, avec la raison et la mesure qui les sépare.
     * Un lot qui effacerait l'ancienne ferait échouer ce cas en la nommant.
     */
    @Test
    fun `l ancienne borne et la nouvelle sont ecrites dans le code`() {
        val glass = code("core/src/main/java/com/onyx/app/ui/design/LiquidGlass.kt")
        assertTrue(
            "l'ANCIENNE borne doit rester dans le code (`glassLegibilityFloor`, encre contre blanc " +
                "pur ET noir pur) : c'est la borne des surfaces qu'on ne sait pas où poser, et elle " +
                "explique le 0,82 de la densité d'avant.",
            glass.contains("fun glassLegibilityFloor(") &&
                glass.contains("compositedBackdrop(veil, Color.White)") &&
                glass.contains("compositedBackdrop(veil, Color.Black)"),
        )
        assertTrue(
            "la NOUVELLE borne doit exister (`glassLegibilityFloorOver` : encre contre les fonds " +
                "échantillonnés derrière la plaque).",
            glass.contains("fun glassLegibilityFloorOver("),
        )
        assertTrue(
            "les plaques doivent passer par la NOUVELLE borne — c'est elle qui décide de leur " +
                "densité, et le chiffre du dessin et celui du verrou sont le même appel.",
            glass.contains("glassLegibilityFloorOver(tint, inks, glassPlateBackdrops(dark)"),
        )
        assertTrue(
            "la raison du changement doit être écrite : le noir pur n'est JAMAIS derrière une plaque, " +
                "et c'est lui qui imposait 0,82.",
            glass.contains("le noir pur n'est jamais") || glass.contains("LE NOIR PUR N'EST JAMAIS") ||
                glass.contains("jamais derrière ces plaques") || glass.contains("n'y passe jamais"),
        )
        val teinte = code("core/src/main/java/com/onyx/app/ui/design/GlassTint.kt")
        assertTrue(
            "les fonds échantillonnés des plaques doivent porter leur provenance dans le code.",
            teinte.contains("val GlassPlateBackdropsLight: List<Color>") &&
                teinte.contains("val GlassPlateBackdropsDark: List<Color>"),
        )
    }
}
