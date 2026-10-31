package com.onyx.app

import androidx.compose.ui.graphics.Color
import com.onyx.app.ui.design.GlassBlurMinDp
import com.onyx.app.ui.design.GlassInk
import com.onyx.app.ui.design.GlassSurface
import com.onyx.app.ui.design.GlassPlateTintCandidates
import com.onyx.app.ui.design.GlassPlateTintLevelRetained
import com.onyx.app.ui.design.GlassPlateTintLight
import com.onyx.app.ui.design.glassLegibilityFloorOver
import com.onyx.app.ui.design.glassNominalTint
import com.onyx.app.ui.design.glassPlateBackdrops
import com.onyx.app.ui.design.glassPlateTraversal
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
import com.onyx.app.ui.design.glassPlateGrainUsed
import com.onyx.app.ui.design.glassTintColour
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
 * **Le verrou du lot « verre-teinte » — quatre morsures, et chacune mord AVANT comme APRÈS.**
 *
 * Le lot a reçu **trois consignes du propriétaire**, et chacune a changé une exigence que ce fichier
 * tenait :
 *
 *  1. **Le verre des plaques doit être FRANCHEMENT TEINTÉ** (capture à l'appui : « trop subtil »),
 *     **même si cela l'écarte de sa planche** — la teinte du verre clair des plaques est donc plus
 *     dense que la planche, et c'est une décision assumée, pas un écart accidentel (voir le
 *     commentaire d'écart dans `LiquidGlass.kt`, au-dessus de `GlassPlateTintCandidates`).
 *  2. **Le GRAIN est refusé** (« tu as mis une espèce de grain, supprime ce grain ») : les plaques
 *     sont **lisses**, et la teinte est le seul signe qui les fait lire comme du verre.
 *  3. **La plaque de CONTENU est refusée** (« un encadré gris transparent, je veux pas ça ») : le
 *     contenu repose **directement sur la page**. Le **glissement** des onglets reste (c'est le
 *     pageur), c'est la plaque qui s'en va.
 *
 * **Aucun cas n'a été supprimé ni désarmé** : les cas 2 et 3 sont **re-pointés** sur la nouvelle
 * exigence (le grain doit être ABSENT ; aucune plaque ne doit être posée derrière le contenu), avec
 * le motif écrit ici même.
 *
 * Les quatre morsures :
 *
 *  1. **le verre des plaques** — flou nul, voile opaque ou arête absente sur l'une des surfaces
 *     flottantes → échec ; et **grain présent** → échec (exigence du propriétaire) ;
 *  2. **la teinte franche** — le détachement corps/page du verre **clair** redescend sous le niveau
 *     retenu → échec, en nommant la valeur mesurée ;
 *  3. **la lisibilité** — une encre posée sur la vitre d'une de ces plaques qui passe sous 4,5:1
 *     dans **l'une des deux apparences** ;
 *  4. **le contenu repose sur la page** — la plaque de contenu (`panePlate` / `PANE`) est revenue
 *     derrière le contenu, ou le pageur a disparu avec elle.
 */
class VerrePlaquesVerrouTest {

    /**
     * **Les trois plaques flottantes de l'écran de conversation** — la famille qui porte le verre
     * teinté. La plaque de contenu ([GlassSurface.PANE]) n'y est plus : le propriétaire l'a refusée,
     * elle n'est peinte par aucun écran.
     */
    private val plaques = listOf(
        GlassSurface.THREAD_TOP_BAR,
        GlassSurface.TAB_BAR,
        GlassSurface.COMPOSER,
    )

    /** La page de l'apparence claire, **relevée à l'appareil** au repos (`sonde.py`, lot verre-fond). */
    private val pageRelevee = Color(0xFFD3DCD7)

    /**
     * Le détachement corps/page visé par le niveau retenu.
     *
     * **RE-POINTÉ par le lot « liquid glass » : de 1,40 à 1,25, et c'est un arbitrage écrit.** Les deux
     * décisions du propriétaire tirent en sens inverse : « le verre doit être franchement teinté »
     * (1,40, lot « verre-teinte ») et « il doit y avoir l'effet liquid glass » (ce lot). La densité est
     * le seul levier qui sert l'une en tuant l'autre : à 1,40 il fallait **0,82** de teinte, soit
     * **18 %** de traversée — la plaque était une carte. Avec le fond **réellement** derrière la plaque
     * (mesuré), la borne d'encre donne **0,55** de densité : **45 %** de traversée et **1,275:1** de
     * détachement. Le seuil est donc posé **sous** la valeur mesurée (1,25) et reste **au-dessus du
     * verre de la planche** (1,238:1) que le propriétaire avait refusé. Le chiffre, la mesure et la
     * raison sont aussi dans `VerreTeinte.kt`.
     */
    private val detachementRetenu = 1.25f

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
    // 1 · Le verre des plaques — flou, voile, arête, et PAS de grain
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `les plaques gardent leur verre — flou, voile, arete, sans grain`() {
        val report = StringBuilder("\nVERRE DES PLAQUES — flou peint, voile peint, arête, grain\n")
        report.append(
            "surface               flou(dp)  voile clair  voile sombre  arête(dp)  arête(α clair)  arête(α sombre)  grain\n",
        )
        plaques.forEach { surface ->
            val blur = glassPaintBlurDp(surface)
            assertTrue(
                "$surface : le flou peint est NUL ($blur dp) — la plaque n'est plus une vitre, " +
                    "c'est un aplat. Un flou de 36 dp au moins est exigé.",
                blur >= GlassBlurMinDp,
            )
            listOf(false, true).forEach { dark ->
                val veil = glassPaintTintAlpha(surface, dark, glassInks(surface, themeFor(dark)))
                assertTrue(
                    "$surface (sombre=$dark) : le voile peint vaut $veil — une vitre OPAQUE n'est " +
                        "plus une vitre.",
                    veil < 1f,
                )
            }
            assertTrue(
                "$surface : l'arête de verre n'est plus peinte (glassPlateEdgeUsed = false) — " +
                    "c'est l'arête qui pose la plaque ; sans elle, elle se lit comme un aplat découpé.",
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
                        "pour se lire sur le corps de la plaque.",
                    alpha >= minOf(
                        PlaqueGeometry.TopEdgeAlphaDark,
                        PlaqueGeometry.TopEdgeAlphaLight,
                    ),
                )
            }
            // **EXIGENCE DU PROPRIÉTAIRE (capture à l'appui) : la plaque est LISSE, SANS GRAIN.**
            // Ce cas exigeait le CONTRAIRE avant ce lot (« la plaque porte son grain ») : il est
            // re-pointé, pas supprimé. Le grain des plaques (« une espèce de grain, supprime ce
            // grain ») est retiré de la peinture ; c'est la TEINTE qui fait lire le verre.
            assertEquals(
                "$surface : la plaque porte ENCORE un grain (glassPlateGrainUsed = " +
                    "${glassPlateGrainUsed(surface)}) — le propriétaire a REFUSÉ ce grain " +
                    "(« tu as mis une espèce de grain, supprime ce grain »). Le verre des plaques " +
                    "est LISSE ; ce qui le fait lire comme du verre, c'est sa teinte, pas un motif.",
                0f,
                glassPlateGrainUsed(surface),
                0f,
            )
            report.append(
                "%-21s %-9.1f %-12.3f %-13.3f %-10.2f %-15.2f %-15.2f %.3f\n".format(
                    Locale.ROOT,
                    surface.name,
                    blur,
                    glassPaintTintAlpha(surface, false, glassInks(surface, themeFor(false))),
                    glassPaintTintAlpha(surface, true, glassInks(surface, themeFor(true))),
                    glassPlateEdgeDp(surface),
                    glassPlateEdgeAlpha(surface, false),
                    glassPlateEdgeAlpha(surface, true),
                    glassPlateGrainUsed(surface),
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
    // 2 · LA TEINTE FRANCHE — le détachement du verre clair, mesuré
    // ---------------------------------------------------------------------------------------------

    /**
     * **LE DÉTACHEMENT DU VERRE CLAIR NE REDESCEND PAS SOUS LE NIVEAU RETENU.**
     *
     * C'est la décision du propriétaire, chiffrée : il a vu le verre de la planche (détachement
     * **1,238:1**, relevé du lot « verre-fond ») et l'a jugé *trop subtil*. Le verre clair des
     * plaques porte donc une **teinte plus dense** ([GlassPlateTintLight]), et ce cas **mord** si on
     * la ramène au verre clair de la planche : à l'état d'avant, le corps valait `#EDF2F0` sur la
     * page `#D3DCD7`, soit **1,238:1** — sous le plancher de 1,40:1 exigé ici, en nommant la valeur.
     *
     * Les trois plaques doivent porter **le même verre** : même teinte, même densité.
     */
    @Test
    fun `le verre clair des plaques ne redescend pas sous le niveau retenu`() {
        val theme = themeFor(dark = false)
        val report = StringBuilder("\nLE VERRE CLAIR DES PLAQUES — teinte franche (décision du propriétaire)\n")
        report.append("surface                teinte      densité  corps/page  détachement  seuil\n")

        // L'échelle entière, pour que le rapport porte les SIX candidats mesurés (les deux plus
        // clairs sont ceux que le propriétaire a refusés).
        //
        // **Re-pointé par le lot « liquid glass »** : la densité d'un candidat est celle de son
        // plancher d'encre, et ce plancher se mesure maintenant contre **le fond réellement derrière
        // la plaque** ([glassPlateBackdrops]) — la même fonction que celle que le dessin lit
        // ([glassTintAlpha]). La colonne « traversée » (1 − α) est ajoutée : c'est elle qui manquait
        // au lot précédent, et c'est elle que le propriétaire voit (« pas d'effet liquid glass »).
        report.append("\n--- l'échelle des candidats (apparence claire, page relevée $pageRelevee) ---\n")
        report.append("teinte      densité  traversée  corps/page  détachement  pire encre  verdict\n")
        GlassPlateTintCandidates.forEachIndexed { index, candidate ->
            val inks = glassInks(GlassSurface.THREAD_TOP_BAR, theme)
            val density = glassLegibilityFloorOver(
                candidate,
                inks,
                glassPlateBackdrops(dark = false),
                GlassSurface.THREAD_TOP_BAR.ink.minRatio,
                glassNominalTint(dark = false),
            )
            val body = compositedBackdrop(candidate.copy(alpha = density), pageRelevee)
            val detach = OnyxContrast.ratio(body, pageRelevee)
            report.append(
                "%-11s %-8.2f %-10.0f %-11s %-12.3f %-11.2f %s\n".format(
                    Locale.ROOT,
                    hex(candidate),
                    density,
                    100f * glassPlateTraversal(density),
                    hex(body),
                    detach,
                    plateInkWorst(candidate, density, inks),
                    if (index == GlassPlateTintLevelRetained) "← RETENU" else "",
                ),
            )
        }

        report.append("\n--- ce que les trois plaques portent réellement ---\n")
        plaques.forEach { surface ->
            val tint = glassTintColour(surface, theme.dark)
            val density = glassPaintTintAlpha(surface, theme.dark, glassInks(surface, theme))
            val body = compositedBackdrop(tint.copy(alpha = density), pageRelevee)
            val detach = OnyxContrast.ratio(body, pageRelevee)
            report.append(
                "%-22s %-11s %-8.2f %-11s %-12.3f %.2f\n".format(
                    Locale.ROOT,
                    surface.name,
                    hex(tint),
                    density,
                    hex(body),
                    detach,
                    detachementRetenu,
                ),
            )
            assertTrue(
                "$surface : le détachement corps/page du verre clair vaut $detach — SOUS le niveau " +
                    "retenu ($detachementRetenu). C'est la décision du propriétaire : il a refusé le " +
                    "verre clair de sa planche (« trop subtil », mesuré 1,238:1). Revenir au verre de " +
                    "la planche annule cette décision — si c'est voulu, il faut changer la décision, " +
                    "pas effacer le chiffre.",
                detach >= detachementRetenu,
            )
        }
        print(report)
    }

    private fun hex(c: Color): String = String.format(
        Locale.ROOT,
        "#%02X%02X%02X",
        (c.red * 255f).toInt().coerceIn(0, 255),
        (c.green * 255f).toInt().coerceIn(0, 255),
        (c.blue * 255f).toInt().coerceIn(0, 255),
    )

    /**
     * Le pire rapport d'encre obtenu par une teinte à une densité donnée, **contre le fond
     * réellement derrière la plaque** ([glassPlateBackdrops]) — la borne re-dérivée de ce lot.
     */
    private fun plateInkWorst(tint: Color, density: Float, inks: List<Color>): Float =
        glassPlateBackdrops(dark = false).minOf { ground ->
            val backdrop = compositedBackdrop(tint.copy(alpha = density), ground)
            inks.minOf { OnyxContrast.ratio(it, backdrop) }
        }

    /** Le pire rapport d'encre contre les deux aplats extrêmes — la borne conservatrice de la maison. */
    private fun inkWorst(tint: Color, density: Float, inks: List<Color>): Float =
        listOf(Color.White, Color.Black).minOf { under ->
            val backdrop = compositedBackdrop(tint.copy(alpha = density), under)
            inks.minOf { OnyxContrast.ratio(it, backdrop) }
        }

    // ---------------------------------------------------------------------------------------------
    // 3 · La lisibilité — l'encre de la plaque tient son seuil, dans les DEUX apparences
    // ---------------------------------------------------------------------------------------------

    /**
     * **Le verrou de lisibilité de la maison**, tenu sur les plaques : pour chaque thème et chaque
     * apparence, la pire encre posée sur la vitre **réellement peinte** (teinte retenue comprise)
     * doit tenir le seuil de sa famille — 4,5:1 pour du texte, 3:1 pour une icône.
     *
     * **RE-POINTÉ par le lot « liquid glass » — la règle n'est pas abaissée, le fond de mesure est
     * remplacé.** Ce cas mesurait l'encre contre les **deux aplats extrêmes** (blanc pur et noir pur),
     * « le pire cas honnête de ce qui peut passer derrière la plaque ». C'est cette borne qui fixait
     * la densité à **0,82** — 19 % de traversée — et qui a produit le défaut du propriétaire : « la
     * c'est pas le cas [d'effet liquid glass] … les deux plaques sont quasi opaques ». Le **noir pur
     * n'est jamais** derrière ces plaques : le fond est la **page**, le **fil** et ses **images**,
     * échantillonnés à la sonde ([glassPlateBackdrops]). Le seuil reste **4,5:1**
     * ([OnyxContrast.TEXT_MIN]) et la mesure porte sur le pire fond **réel** de l'apparence.
     */
    @Test
    fun `l encre des plaques tient son seuil dans les deux apparences`() {
        val report = StringBuilder("\nLISIBILITÉ DES PLAQUES — pire encre sur la vitre peinte\n")
        report.append("surface                thème/apparence                     pire fond réel  pire ratio  seuil  verdict\n")
        var checked = 0
        themes().forEach { theme ->
            val grounds = glassPlateBackdrops(theme.dark)
            plaques.forEach { surface ->
                val inks = glassInks(surface, theme)
                val tint = glassTintColour(surface, theme.dark)
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
                var worstGround = grounds.first()
                var worst = Float.MAX_VALUE
                grounds.forEach { ground ->
                    val backdrop = compositedBackdrop(veil, ground)
                    val here = when (surface.ink) {
                        GlassInk.DERIVED -> maxOf(
                            OnyxContrast.ratio(Color.Black, backdrop),
                            OnyxContrast.ratio(Color.White, backdrop),
                        )

                        else -> inks.minOf { OnyxContrast.ratio(it, backdrop) }
                    }
                    if (here < worst) {
                        worst = here
                        worstGround = ground
                    }
                }
                report.append(
                    "%-22s %-35s %-15s %-11.2f %-6.1f %s\n".format(
                        Locale.ROOT,
                        surface.name,
                        "${theme.id}/${if (theme.dark) "sombre" else "clair"}",
                        hex(worstGround),
                        worst,
                        surface.ink.minRatio,
                        if (worst >= surface.ink.minRatio) "ok" else "ÉCHEC",
                    ),
                )
                assertTrue(
                    "$surface ${theme.id}/${if (theme.dark) "sombre" else "clair"} : le pire rapport " +
                        "d'encre sur la vitre peinte vaut $worst (fond ${hex(worstGround)}) — sous le " +
                        "seuil de la maison (${surface.ink.minRatio}:1). Le verre traversant ne peut " +
                        "pas coûter la lisibilité : c'est un levier à changer (densité, encre, fond " +
                        "protégé), pas un seuil à baisser.",
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

    /**
     * **Le plancher de masquage d'une plaque se mesure sur le contenu FLUTÉ.** Mesuré avant le lot
     * « verre-plaques » : le plancher du texte net montait le voile jusqu'à l'aplat — plus rien ne se
     * voyait au travers, donc plus de verre. Le modèle du flou est vérifié ici, chiffre par chiffre.
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

    // ---------------------------------------------------------------------------------------------
    // 4 · Le contenu repose sur la page — et le GESTE reste
    // ---------------------------------------------------------------------------------------------

    /**
     * **LE CONTENU D'UN ONGLET REPOSE SUR LA PAGE, ET IL GLISSE TOUJOURS.**
     *
     * Ce cas exigeait avant que « le contenu d'un onglet soit porté par une plaque » (`panePlate`,
     * lot « verre-plaques »). Le propriétaire a **refusé** cette plaque, capture à l'appui : *« tu as
     * foutu en arrière-plan un encadré gris transparent, je veux pas ça »* — il a entouré toute la
     * zone en rouge. Le cas est donc **re-pointé**, pas supprimé : il mord maintenant si la plaque
     * revient.
     *
     * **Ce qui n'est pas perdu** : le **glissement**. Le contenu des onglets (Chat / Flux / Fichiers)
     * suit toujours le doigt — c'est le `HorizontalPager` qui le translate. Le geste demandé était le
     * défilement latéral du contenu ; la plaque qui le portait n'en était que l'emballage.
     */
    @Test
    fun `le contenu d un onglet repose sur la page et glisse sous le doigt`() {
        val pager = code("src/main/java/com/onyx/app/ui/chat/ChatScreen.kt")
        val plateCalls = Regex("panePlate\\(").findAll(pager).count()
        assertEquals(
            "ChatScreen repose encore la plaque de verre DERRIÈRE le contenu : `panePlate(` est " +
                "appelé $plateCalls fois. Le propriétaire a REFUSÉ cette plaque (« un encadré gris " +
                "transparent, je veux pas ça ») : le contenu doit reposer sur la page, sans encadré.",
            0,
            plateCalls,
        )
        assertFalse(
            "ChatScreen pose encore la surface PANE sur le contenu (`liquidGlass(GlassSurface.PANE`) — " +
                "c'est l'encadré refusé, par un autre chemin d'appel.",
            pager.contains("liquidGlass(GlassSurface.PANE"),
        )
        assertTrue(
            "le pageur doit rester la source du glissement (`HorizontalPager`) : c'est le GESTE du " +
                "propriétaire (« que je peux faire glisser de gauche a droite ») — la plaque s'en va, " +
                "le défilement latéral reste.",
            pager.contains("HorizontalPager("),
        )
        val glass = code("../core/src/main/java/com/onyx/app/ui/design/LiquidGlass.kt")
        assertFalse(
            "la fonction `panePlate` doit avoir disparu de la couche de dessin : si elle reste " +
                "appelable, la plaque refusée peut revenir sans que rien ne morde.",
            glass.contains("fun Modifier.panePlate(theme: ResolvedTheme): Modifier"),
        )
    }


    /**
     * **Le verre du repos est celui du doigt** — le défaut signalé : « l'effet de transparence verre
     * s'active qu'au clic ; il doit etre de base ». Le chemin de peinture ne doit donc lire **aucun
     * état d'interaction** : ni l'enfoncement, ni le défilement, ni la mesure de fluidité ne peuvent
     * décider du flou, de la teinte ou de l'arête.
     */
    @Test
    fun `le verre du repos est celui du doigt`() {
        val glassCode = code("../core/src/main/java/com/onyx/app/ui/design/LiquidGlass.kt")
        assertTrue(
            "le palier de peinture doit rester constant (glassPaintLevel) : c'est lui qui rend " +
                "l'apparence indépendante du temps et du geste",
            glassCode.contains("val level = glassPaintLevel"),
        )
        assertTrue(
            "le flou peint doit venir de la constante de la surface, pas d'un état de geste",
            glassCode.contains("val blurDp = glassPaintBlurDp(surface)"),
        )
        assertTrue(
            "le voile peint doit venir de la mesure de lisibilité",
            glassCode.contains("val nominalTintAlpha = glassPaintTintAlpha(surface, dark, inks)"),
        )
        plaques.forEach { surface ->
            assertEquals(
                "${surface.name} : au repos le flou peint vaut ${glassPaintBlurDp(surface)} dp ; " +
                    "sous le doigt ce doit être LE MÊME — il n'y a qu'une fonction de peinture.",
                surface.blurDp,
                glassPaintBlurDp(surface),
                0f,
            )
        }
    }

    private companion object {
        /** La densité de l'appareil de recette : onyxglass, 420 dpi = 2,625. */
        const val DENSITY = 2.625f
    }
}
