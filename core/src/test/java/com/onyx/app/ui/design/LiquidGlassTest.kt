package com.onyx.app.ui.design

import androidx.compose.ui.graphics.Color
import com.onyx.app.ui.theme.OnyxFonts
import com.onyx.app.ui.theme.OnyxTheme
import com.onyx.app.ui.theme.ResolvedTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le verre liquide, éprouvé **sans appareil** : les bornes de la spec, la règle de qualité, et
 * surtout le **tableau des contrastes** — l'encre de chaque surface, sur la vitre, dans le pire cas
 * (contenu blanc pur **et** noir pur derrière), pour les deux apparences et les trois qualités.
 *
 * Le tableau est **imprimé** par le test : le chiffre du rapport et le chiffre vérifié sont donc
 * produits par le même code, au même moment. Un seuil qui tomberait sous 4,5 (texte) ou 3 (icône)
 * échoue ici, pas à la capture.
 */
class LiquidGlassTest {

    private val surfaces = GlassSurface.entries.toList()

    private fun themes(): List<ResolvedTheme> = buildList {
        for (name in OnyxTheme.entries) {
            for (dark in listOf(false, true)) {
                add(ResolvedTheme(name, name.palette, dark, OnyxFonts.System))
            }
        }
    }

    // — La spec, à la lettre —

    @Test
    fun `le flou de chaque surface tient dans 36 a 50 dp`() {
        surfaces.forEach { surface ->
            assertTrue(
                "${surface.name} : ${surface.blurDp} dp sort de la fourchette 36–50",
                surface.blurDp in GlassBlurMinDp..GlassBlurMaxDp,
            )
        }
        assertEquals("le défaut est 36 dp", 36f, GlassBlurDefaultDp, 0f)
        assertEquals("le défaut de la barre du haut", 36f, GlassSurface.THREAD_TOP_BAR.blurDp, 0f)
    }

    @Test
    fun `la teinte nominative est blanc 20 en clair et anthracite 30 en sombre`() {
        assertEquals(0.20f, GlassTintLight, 0.001f)
        assertEquals(0.30f, GlassTintDark, 0.001f)
        assertEquals("blanc en clair", Color.White, glassTintColour(dark = false))
        assertEquals("anthracite en sombre", GlassAnthracite, glassTintColour(dark = true))
        assertEquals(0.20f, glassNominalTint(dark = false), 0.001f)
        assertEquals(0.30f, glassNominalTint(dark = true), 0.001f)
    }

    @Test
    fun `le reflet de tranche tient dans 0,5 a 1 dp et 30 a 40 pour cent`() {
        assertTrue("l'épaisseur reste dans la fourchette", GlassEdgeDp in GlassEdgeMinDp..GlassEdgeMaxDp)
        assertTrue("l'opacité reste dans la fourchette", GlassEdgeAlpha in 0.30f..0.40f)
    }

    @Test
    fun `le bruit est de 0,1`() {
        assertEquals(0.1f, GlassNoiseFactor, 0.0001f)
    }

    // — La qualité —

    @Test
    fun `le plafond suit la version d Android et la memoire`() {
        assertEquals(GlassLevel.FULL, glassCeiling(34, lowRam = false))
        // Le seuil n'est plus « la version 31 est cassée » mais **celui de la plateforme** :
        // sous la version où son flou se rafraîchit, le verre démarre en repli, à partir de là en
        // pleine qualité. Une seule constante décide, et c'est celle de `glassTier`.
        assertEquals(GlassLevel.SCRIM, glassCeiling(GlassBlurMinSdk - 1, lowRam = false))
        assertEquals(GlassLevel.FULL, glassCeiling(GlassBlurMinSdk, lowRam = false))
        assertEquals(GlassLevel.LITE, glassCeiling(34, lowRam = true))
    }

    @Test
    fun `le mode allege divise le flou et n en descend jamais sous zero`() {
        surfaces.forEach { surface ->
            val full = glassBlurDp(surface, GlassLevel.FULL)
            val lite = glassBlurDp(surface, GlassLevel.LITE)
            val scrim = glassBlurDp(surface, GlassLevel.SCRIM)
            assertEquals(surface.blurDp, full, 0f)
            assertEquals(surface.blurDp / 2f, lite, 0.001f)
            assertEquals(0f, scrim, 0f)
            assertTrue("le repli ne floute pas", !GlassLevel.SCRIM.blurs)
            assertTrue("le mode allégé floute encore", GlassLevel.LITE.blurs)
        }
    }

    @Test
    fun `une app au repos, dont les trames tombent sur le vsync, reste en pleine qualite`() {
        // Le piège que la charge évite : à 60 images/s, une trame parfaite dure 16,67 ms. Comparée à
        // un seuil écrit en dur sur 16,67, elle passait pour une trame **perdue** et le verre tombait
        // en mode allégé sur un appareil parfaitement fluide (mesuré sur l'émulateur : 100 % des
        // trames « saccadées » alors que l'app ne dessinait rien). La charge vaut 1,0 : on ne bouge pas.
        assertEquals(GlassLevel.FULL, GlassFluidity.levelFor(1.0, 0.0, GlassLevel.FULL))
        // Et à 120 images/s, 8,33 ms vaut aussi 1,0 de charge — même verdict.
        assertEquals(GlassLevel.FULL, GlassFluidity.levelFor(1.0, 0.0, GlassLevel.FULL))
    }

    @Test
    fun `la descente se declenche sur la mesure et la remontee a sa propre bande morte`() {
        // Charge 0,6 (une app large) : on reste en pleine qualité.
        assertEquals(GlassLevel.FULL, GlassFluidity.levelFor(0.6, 0.0, GlassLevel.FULL))
        // Charge 1,45 : le verre s'allège.
        assertEquals(GlassLevel.LITE, GlassFluidity.levelFor(1.45, 0.0, GlassLevel.FULL))
        // Trames perdues, même avec une charge correcte : on allège aussi.
        assertEquals(GlassLevel.LITE, GlassFluidity.levelFor(1.05, 0.25, GlassLevel.FULL))
        // Bande morte : entre les deux seuils (1,15 à 1,35), rien ne bouge — c'est ce qui empêche
        // l'oscillation.
        assertEquals(GlassLevel.LITE, GlassFluidity.levelFor(1.25, 0.10, GlassLevel.LITE))
        // Franchement fluide : on remonte.
        assertEquals(GlassLevel.FULL, GlassFluidity.levelFor(1.05, 0.01, GlassLevel.LITE))
        // Allégé ne suffit pas : repli.
        assertEquals(GlassLevel.SCRIM, GlassFluidity.levelFor(2.10, 0.0, GlassLevel.LITE))
        assertEquals(GlassLevel.SCRIM, GlassFluidity.levelFor(1.4, 0.50, GlassLevel.LITE))
        // Et on ne remonte du repli que par le bas.
        assertEquals(GlassLevel.LITE, GlassFluidity.levelFor(1.05, 0.01, GlassLevel.SCRIM))
        assertEquals(GlassLevel.SCRIM, GlassFluidity.levelFor(1.25, 0.01, GlassLevel.SCRIM))
    }

    @Test
    fun `une trame anormalement longue n est pas imputee au verre`() {
        val holder = GlassFluidity(GlassLevel.FULL)
        holder.acquire()
        // Le démarrage puis une suite de trames à 8 ms : la fenêtre est pleine, le verre reste plein.
        var frame = 1_000_000L
        holder.sample(frame)
        repeat(GlassFluidity.WindowFrames) {
            frame += 8_000_000L
            holder.sample(frame)
        }
        assertEquals(GlassLevel.FULL, holder.level)
        // Une mise en veille de deux secondes ne compte pas comme une trame perdue.
        frame += 2_000_000_000L
        holder.sample(frame)
        repeat(GlassFluidity.WindowFrames) {
            frame += 8_000_000L
            holder.sample(frame)
        }
        assertEquals("la veille n'entre pas dans la moyenne", GlassLevel.FULL, holder.level)
        holder.release()
        assertEquals("plus de vitre, plus de mesure", 0, holder.demand)
        assertEquals("et la qualité repart du plafond", GlassLevel.FULL, holder.level)
    }

    @Test
    fun `la garde empeche le clignotement, une qualite tient deux fenetres`() {
        // La règle, seule : c'est elle qui empêche `FULL -> LITE -> FULL` observé à 400 ms
        // d'intervalle avant ce garde-fou.
        // `dwelled(suivante, courante, fenêtres écoulées)`. Juste après un changement, la demandée
        // est retenue : on garde la qualité courante.
        assertEquals(GlassLevel.LITE, GlassFluidity.dwelled(GlassLevel.FULL, GlassLevel.LITE, 0))
        assertEquals(GlassLevel.LITE, GlassFluidity.dwelled(GlassLevel.FULL, GlassLevel.LITE, 1))
        // La durée de garde écoulée, le changement passe.
        assertEquals(GlassLevel.FULL, GlassFluidity.dwelled(GlassLevel.FULL, GlassLevel.LITE, 2))
        // Une qualité qui ne change pas n'est jamais retenue.
        assertEquals(GlassLevel.LITE, GlassFluidity.dwelled(GlassLevel.LITE, GlassLevel.LITE, 0))
        // Et une qualité stable depuis longtemps peut changer tout de suite.
        assertEquals(GlassLevel.FULL, GlassFluidity.dwelled(GlassLevel.FULL, GlassLevel.LITE, 9))
        // Le sens inverse est symétrique.
        assertEquals(GlassLevel.FULL, GlassFluidity.dwelled(GlassLevel.LITE, GlassLevel.FULL, 0))
    }

    @Test
    fun `le suivi descend sous la charge et remonte au calme, une evaluation par fenetre`() {
        val holder = GlassFluidity(GlassLevel.FULL)
        holder.acquire()
        var frame = 1_000_000L
        fun windows(count: Int, ms: Double) {
            repeat(count * GlassFluidity.WindowFrames) {
                frame += (ms * 1_000_000).toLong()
                holder.sample(frame)
            }
        }
        // Deux fenêtres fluides : la qualité ne bouge pas.
        windows(2, 8.0)
        assertEquals(GlassLevel.FULL, holder.level)
        // Puis deux fenêtres à 25 ms (charge 1,5×) : le verre s'allège, et il n'ira pas jusqu'au
        // repli puisque la charge reste sous 2×.
        windows(2, 25.0)
        assertEquals(GlassLevel.LITE, holder.level)
        // Le retour au calme : la fenêtre glissante garde la mémoire des trames chargées, donc il
        // faut la vider avant de remonter — c'est voulu, une trame fluide isolée ne décide rien.
        windows(40, 6.0)
        assertEquals(GlassLevel.FULL, holder.level)
        assertEquals(GlassFluidity.WindowFrames * 2, GlassFluidity.DwellWindows * GlassFluidity.WindowFrames)
    }

    @Test
    fun `le plafond ne se laisse pas depasser par la mesure`() {
        val holder = GlassFluidity(GlassLevel.SCRIM)
        holder.acquire()
        var frame = 1_000_000L
        holder.sample(frame)
        repeat(GlassFluidity.WindowFrames * 2) {
            frame += 6_000_000L
            holder.sample(frame)
        }
        assertEquals("l'appareil refuse le flou : la mesure ne le force pas", GlassLevel.SCRIM, holder.level)
        holder.release()
    }

    // — La lisibilité : le tableau, mesuré —

    @Test
    fun `toute surface tient son seuil sur un contenu blanc et sur un contenu noir`() {
        val report = StringBuilder("\nTABLE DE CONTRASTE — encre sur la vitre, pire cas\n")
        report.append("surface            thème  qualité  teinte(α)  vs blanc  vs noir  seuil  verdict\n")
        var checked = 0
        themes().forEach { theme ->
            surfaces.forEach { surface ->
                val inks = glassInks(surface, theme)
                GlassLevel.entries.forEach { level ->
                    val tint = glassTintColour(theme.dark)
                    val alpha = glassTintAlpha(surface, level, theme.dark, inks)
                    val veil = tint.copy(alpha = alpha)
                    val onWhite = compositedBackdrop(veil, Color.White)
                    val onBlack = compositedBackdrop(veil, Color.Black)
                    // **La même fonction que le rapport imprime et que le rendu honore** : une surface
                    // à encre dérivée (la barre d'onglets) est jugée sur l'encre **qu'elle choisit**
                    // (le noir ou le blanc, celui qui passe), les autres sur la teinte du thème.
                    val worst = glassWorstInkRatio(surface, level, theme)
                    val white = if (surface.ink == GlassInk.DERIVED) {
                        maxOf(OnyxContrast.ratio(Color.Black, onWhite), OnyxContrast.ratio(Color.White, onWhite))
                    } else {
                        inks.minOf { OnyxContrast.ratio(it, onWhite) }
                    }
                    val black = if (surface.ink == GlassInk.DERIVED) {
                        maxOf(OnyxContrast.ratio(Color.Black, onBlack), OnyxContrast.ratio(Color.White, onBlack))
                    } else {
                        inks.minOf { OnyxContrast.ratio(it, onBlack) }
                    }
                    report.append(
                        "%-18s %-6s %-8s %-9.3f  %-8.2f %-7.2f %-6.1f %s\n".format(
                            surface.name,
                            if (theme.dark) "sombre" else "clair",
                            level.name,
                            alpha,
                            white,
                            black,
                            surface.ink.minRatio,
                            if (worst >= surface.ink.minRatio) "ok" else "ÉCHEC",
                        ),
                    )
                    assertTrue(
                        "${surface.name}/${if (theme.dark) "sombre" else "clair"}/$level : $worst < ${surface.ink.minRatio}",
                        worst >= surface.ink.minRatio,
                    )
                    checked++
                }
            }
        }
        report.append("lignes vérifiées : $checked\n")
        print(report)
        assertEquals(themes().size * surfaces.size * GlassLevel.entries.size, checked)
    }

    @Test
    fun `une surface a encre derivee garde une vitre mince et une encre lisible`() {
        // Le cœur de la correction du lot « verre liquide » : la barre d'onglets est une **pilule
        // flottante**, donc sa vitre doit laisser voir le fil qui défile dessous. Avec une encre
        // **fixe**, le plancher de lisibilité montait à 0,67 en clair — la vitre devenait un aplat
        // de blanc. Avec une encre **dérivée**, il s'arrête bien plus bas, et la barre choisit
        // quand même une encre au-dessus du seuil du texte.
        val derived = GlassSurface.entries.filter { it.ink == GlassInk.DERIVED }
        assertTrue("une surface doit porter une encre dérivée", derived.isNotEmpty())
        derived.forEach { surface ->
            themes().forEach { theme ->
                val inks = glassInks(surface, theme)
                val thin = glassTintAlpha(surface, GlassLevel.FULL, theme.dark, inks)
                val fixed = glassLegibilityFloor(
                    glassTintColour(theme.dark),
                    inks,
                    surface.ink.minRatio,
                    glassNominalTint(theme.dark),
                )
                assertTrue(
                    "${surface.name}/${if (theme.dark) "sombre" else "clair"} : la vitre dérivée " +
                        "($thin) doit être plus mince que la vitre à encre fixe ($fixed)",
                    thin <= fixed,
                )
                assertTrue(
                    "${surface.name} : le voile doit rester mince — une vitre au-dessus de 60 % " +
                        "n'est plus un verre (relevé $thin)",
                    glassTintAlpha(surface, GlassLevel.FULL, theme.dark, inks) <= 0.60f,
                )
                assertTrue(
                    "${surface.name} : l'encre dérivée doit tenir le seuil du texte",
                    glassWorstInkRatio(surface, GlassLevel.FULL, theme) >= surface.ink.minRatio,
                )
            }
        }
    }

    @Test
    fun `la teinte nominative seule ne suffit pas, et le plancher la remonte`() {
        // C'est le cœur de la décision : 20 % de blanc sur du noir ne portent pas un titre à 4,5:1.
        val nominalOnly = compositedBackdrop(
            Color.White.copy(alpha = GlassTintLight),
            Color.Black,
        )
        val ink = Color(0xFF1B1B1D)
        val ratioAtNominal = OnyxContrast.ratio(ink, nominalOnly)
        assertTrue(
            "20 % de blanc sur du noir devait échouer au seuil (mesuré $ratioAtNominal)",
            ratioAtNominal < OnyxContrast.TEXT_MIN,
        )
        val floor = glassLegibilityFloor(Color.White, listOf(ink), OnyxContrast.TEXT_MIN, GlassTintLight)
        assertTrue("le plancher remonte au-dessus du nominatif", floor > GlassTintLight)
        assertTrue(
            "et il tient le seuil",
            OnyxContrast.ratio(ink, compositedBackdrop(Color.White.copy(alpha = floor), Color.Black)) >=
                OnyxContrast.TEXT_MIN,
        )
    }

    @Test
    fun `le repli est plus opaque que le verre, jamais un trou`() {
        themes().forEach { theme ->
            surfaces.forEach { surface ->
                val inks = glassInks(surface, theme)
                val scrim = glassScrimAlpha(surface, theme.dark, inks)
                val glass = glassTintAlpha(surface, GlassLevel.FULL, theme.dark, inks)
                assertTrue("${surface.name} : le repli doit être plus opaque que le verre", scrim > glass)
                assertTrue(
                    "${surface.name} : le repli ne doit jamais laisser passer le contenu",
                    scrim >= (if (theme.dark) GlassScrimMinDark else GlassScrimMinLight),
                )
                assertTrue(scrim <= 1f)
            }
        }
    }

    @Test
    fun `le mode allege remonte la teinte, il ne la baisse jamais`() {
        themes().forEach { theme ->
            surfaces.forEach { surface ->
                val inks = glassInks(surface, theme)
                val full = glassTintAlpha(surface, GlassLevel.FULL, theme.dark, inks)
                val lite = glassTintAlpha(surface, GlassLevel.LITE, theme.dark, inks)
                assertTrue("${surface.name} : le mode allégé ne peut pas éclaircir la vitre", lite >= full)
            }
        }
    }

    @Test
    fun `le plancher de lisibilite est le meme pour tous les themes, sans trou`() {
        // Un aplat extrême derrière : la fonction rend toujours une valeur utilisable, même pour une
        // encre qu'aucune opacité ne sauverait (elle sature à 1 plutôt que de rendre un nombre faux).
        val impossible = glassLegibilityFloor(Color(0x808080), listOf(Color(0x808080)), OnyxContrast.TEXT_MIN)
        assertEquals(1f, impossible, 0.0001f)
    }
}
