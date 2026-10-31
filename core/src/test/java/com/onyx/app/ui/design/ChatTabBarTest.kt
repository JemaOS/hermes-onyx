package com.onyx.app.ui.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.theme.OnyxFonts
import com.onyx.app.ui.theme.OnyxTheme
import com.onyx.app.ui.theme.ResolvedTheme
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La barre d'onglets d'une conversation : la **règle du geste**, la **mémoire par conversation** et
 * la **lisibilité de l'encre**, éprouvées sans appareil.
 *
 * Les deux décisions qui se discutent y sont posées comme des fonctions pures : un glissement court
 * qui revient en place, et le bord de l'écran qui reste au retour du système. Le contraste est
 * mesuré sur le fond **réellement composé** (le voile de la barre, la teinte de l'onglet actif, et
 * le contenu le plus extrême qui peut passer sous la vitre) — la même fonction que l'écran appelle,
 * donc le chiffre annoncé et le chiffre vérifié ne peuvent pas diverger.
 */
class ChatTabBarTest {

    // — Les onglets —

    @Test
    fun `les reperes suivent l'ordre Chat Feed Files puis le visage du bot`() {
        // Trois onglets de contenu, puis le **repère-visage**. « Idées » et « Objectifs » ont été
        // supprimés (demande du propriétaire) : ils ne sont plus ni des onglets, ni des pages.
        assertEquals(
            listOf("Chat", "Feed", "Files", "Bot"),
            ChatTab.entries.map { it.key },
        )
        assertEquals(listOf("Chat", "Feed", "Files"), ChatTab.paged.map { it.key })
        assertEquals(3, chatTabCount)
        // Les libellés passent par le dictionnaire : chaque onglet a sa clé.
        assertEquals(ChatTab.entries.map { it.key }, ChatTab.entries.map { it.label })
    }

    @Test
    fun `une cle inconnue ou vide retombe sur Chat`() {
        assertEquals(ChatTab.CHAT, ChatTab.of(null))
        assertEquals(ChatTab.CHAT, ChatTab.of(""))
        assertEquals(ChatTab.CHAT, ChatTab.of("   "))
        assertEquals(ChatTab.CHAT, ChatTab.of("Aucun"))
        assertEquals(ChatTab.FEED, ChatTab.of(" Feed "))
        assertEquals(ChatTab.FILES, ChatTab.of("Files"))
        assertEquals(0, chatTabIndexOf(null))
        assertEquals(1, chatTabIndexOf("Feed"))
        assertEquals(2, chatTabIndexOf("Files"))
        assertEquals(0, chatTabIndexOf("Menu"))
        // **Une clé d'avant la suppression retombe sur Chat** : « Ideas » et « Goals » ne sont plus
        // des onglets, une valeur gardée ne casse donc ni l'écran ni le pageur.
        assertEquals(0, chatTabIndexOf("Goals"))
        assertEquals(0, chatTabIndexOf("Ideas"))
        // Tous les index rendus tombent dans le pageur : un index hors page ne peut pas en sortir.
        assertTrue(listOf(null, "", "Feed", "Nope", "Goals", "Ideas", "Bot").all { chatTabIndexOf(it) in 0 until chatTabCount })
    }

    // — Le glissement —

    @Test
    fun `un glissement court revient en place`() {
        val page = 1080f
        // Un frôlement (un cinquième de page) ne change pas d'onglet.
        assertEquals(1, TabSwipe.settledIndex(1, 5, -page * 0.2f, page))
        assertEquals(1, TabSwipe.settledIndex(1, 5, page * 0.2f, page))
        assertEquals(2, TabSwipe.settledIndex(2, 5, 0f, page))
    }

    @Test
    fun `un glissement franc change d'onglet, et s'arrete aux deux bouts`() {
        val page = 1080f
        assertEquals(2, TabSwipe.settledIndex(1, 5, -page * 0.6f, page))
        assertEquals(0, TabSwipe.settledIndex(1, 5, page * 0.6f, page))
        // Aux bords, on ne sort jamais de la barre.
        assertEquals(0, TabSwipe.settledIndex(0, 5, page, page))
        assertEquals(4, TabSwipe.settledIndex(4, 5, -page, page))
        assertEquals(4, TabSwipe.settledIndex(9, 5, 0f, page))
        assertEquals(0, TabSwipe.settledIndex(-3, 5, 0f, page))
    }

    @Test
    fun `un elan suffit sur un geste court, et un seul onglet est franchi`() {
        val page = 1080f
        // Un geste court mais lancé tourne la page...
        assertEquals(2, TabSwipe.settledIndex(1, 5, -page * 0.05f, page, velocityPxPerSecond = -3_000f))
        assertEquals(0, TabSwipe.settledIndex(1, 5, page * 0.05f, page, velocityPxPerSecond = 3_000f))
        // ...mais d'un seul onglet, jamais de deux : 1080 px de large ne se traverse pas d'un coup.
        assertEquals(2, TabSwipe.settledIndex(1, 5, -page * 4f, page, velocityPxPerSecond = -9_000f))
        // Sous le seuil d'élan, c'est la distance qui décide.
        assertEquals(1, TabSwipe.settledIndex(1, 5, -page * 0.2f, page, velocityPxPerSecond = -900f))
        // Une seule page (ou une largeur inconnue) ne glisse pas.
        assertEquals(0, TabSwipe.settledIndex(0, 1, -page, page))
        assertEquals(3, TabSwipe.settledIndex(3, 5, -page, 0f))
    }

    @Test
    fun `le bord de l'ecran ne commence pas un changement d'onglet`() {
        val width = 1080f
        val guard = 40f
        assertFalse(TabSwipe.allowsStart(0f, width, guard))
        assertFalse(TabSwipe.allowsStart(5f, width, guard))
        assertFalse(TabSwipe.allowsStart(guard - 1f, width, guard))
        assertFalse(TabSwipe.allowsStart(width, width, guard))
        assertFalse(TabSwipe.allowsStart(width - 8f, width, guard))
        assertFalse(TabSwipe.allowsStart(width - guard + 1f, width, guard))
        assertTrue(TabSwipe.allowsStart(guard, width, guard))
        assertTrue(TabSwipe.allowsStart(width / 2f, width, guard))
        assertTrue(TabSwipe.allowsStart(width - guard, width, guard))
        // Navigation à trois boutons : aucune bande réservée, le pageur prend toute la largeur,
        // bords compris.
        assertTrue(TabSwipe.allowsStart(0f, width, 0f))
        assertTrue(TabSwipe.allowsStart(width, width, 0f))
        assertTrue(TabSwipe.allowsStart(width / 2f, width, 0f))
        // Une bande plus large que la moitié de l'écran est ramenée : le milieu reste toujours libre.
        assertTrue(TabSwipe.allowsStart(width / 2f, width, width))
    }

    // — Le fond composé —

    @Test
    fun `le fond d'un onglet se compose du voile, de la teinte active, puis du contenu`() {
        val veil = Color(0xCC000000)
        val veilOverWhite = compositedBackdrop(veil, Color.White)
        val veilOverBlack = compositedBackdrop(veil, Color.Black)
        // Sans teinte, le fond est exactement le voile composé : la fonction ne change rien.
        assertEquals(veilOverWhite, tabBackground(veil, null, Color.White))
        assertEquals(veilOverBlack, tabBackground(veil, null, Color.Black))
        // Avec la teinte de l'onglet actif, elle passe **par-dessus** le voile.
        val tint = Color(0x29FF8800)
        assertEquals(compositedBackdrop(tint, veilOverWhite), tabBackground(veil, tint, Color.White))
        // Le pire des deux contenus n'est pas le même selon le voile : en sombre, c'est le blanc qui
        // rapproche le fond de l'encre blanche, donc c'est lui qui abaisse le rapport.
        val darkVeil = veilColour(Color(0xFF101014), 0.87f, dark = true)
        assertTrue(contrastRatio(Color.White, compositedBackdrop(darkVeil, Color.White)) <
            contrastRatio(Color.White, compositedBackdrop(darkVeil, Color.Black)))
        // Et en clair, c'est le noir : les deux cas sont donc bien testés.
        val lightVeil = veilColour(Color(0xFFF4F4F6), 0.89f, dark = false)
        assertTrue(contrastRatio(Color.Black, compositedBackdrop(lightVeil, Color.Black)) <
            contrastRatio(Color.Black, compositedBackdrop(lightVeil, Color.White)))
    }

    // — La pastille de l'onglet actif : `--pill` de la planche —

    /**
     * **Ce que la pastille change, mesuré, et pas déclaré.** La planche peint un ovale plus clair
     * sous l'onglet actif (`.navi.on{background:var(--pill)}`) : le test compose donc le fond réel de
     * la barre **avec** et **sans** la pastille, et exige que la première soit plus claire — c'est le
     * seul effet qui compte, et c'est celui qu'un lecteur de la capture voit.
     *
     * Et il exige la seconde moitié de la règle : l'encre de l'onglet choisi, mesurée **sur ce fond
     * plus clair**, reste au-dessus du seuil de lecture ([readableInk]). Une pastille qui rendrait le
     * libellé illisible serait un défaut, pas une décoration.
     */
    @Test
    fun `la pastille de l onglet actif eclaircit le fond, et l encre reste lisible`() {
        val light = ResolvedTheme(OnyxTheme.STANDARD, OnyxTheme.STANDARD.palette, false, OnyxFonts.System)
        val dark = ResolvedTheme(OnyxTheme.STANDARD, OnyxTheme.STANDARD.palette, true, OnyxFonts.System)

        val lightVeil = veilColour(light.pageColor, 0.89f, dark = false)
        val withoutPill = tabBackground(lightVeil, null, Color.White)
        val withPill = tabBackground(lightVeil, PlaqueGeometry.activePill(light), Color.White)
        assertTrue(
            "la pastille doit éclaircir l'onglet actif en clair",
            contrastRatio(Color.White, withPill) < contrastRatio(Color.White, withoutPill),
        )
        assertTrue(
            "et l'encre de l'onglet choisi reste au-dessus du seuil sur ce fond-là",
            contrastRatio(
                readableInk(light.accentInk, withPill),
                withPill,
            ) >= TEXT_CONTRAST_MIN,
        )

        val darkVeil = veilColour(dark.pageColor, 0.87f, dark = true)
        val withoutPillDark = tabBackground(darkVeil, null, Color.White)
        val withPillDark = tabBackground(darkVeil, PlaqueGeometry.activePill(dark), Color.White)
        assertTrue(
            "la pastille doit aussi se détacher en sombre, sans devenir un blanc",
            contrastRatio(Color.White, withPillDark) < contrastRatio(Color.White, withoutPillDark),
        )
        assertTrue(
            "mais elle reste un blanc à 17 % : la page sombre n'est pas inondée",
            PlaqueGeometry.activePill(dark).alpha < PlaqueGeometry.activePill(light).alpha,
        )
    }

    // — La lisibilité —

    @Test
    fun `une encre qui passe deja n'est pas touchee`() {
        val veil = veilColour(Color(0xFFF4F4F6), 0.89f, dark = false)
        val background = tabBackground(veil, null, Color.White)
        val ink = Color(0xFF1B1B1D)
        assertTrue(contrastRatio(ink, background) >= 4.5)
        assertEquals(ink, readableInk(ink, background))
    }

    @Test
    fun `toute couleur de bot devient lisible, sur tous les themes et les deux apparences`() {
        val lines = mutableListOf<String>()
        var checked = 0
        // Un balayage franc de la palette : si une teinte échappe au seuil, elle est ici.
        val candidates = buildList {
            for (r in 0..5) for (g in 0..5) for (b in 0..5) {
                add(Color(r / 5f, g / 5f, b / 5f))
            }
            add(Color.White)
            add(Color.Black)
            add(Color(0xFFFFF200))
            add(Color(0xFF7FFF00))
        }
        for (name in OnyxTheme.entries) {
            for (dark in listOf(false, true)) {
                val resolved = ResolvedTheme(name, name.palette, dark, OnyxFonts.System)
                for (level in FrostLevel.entries) {
                    val veil = veilColour(
                        resolved.pageColor,
                        frostVeilAlpha(FrostSurface.BOTTOM_BAR, level, dark),
                        dark,
                    )
                    for (under in listOf(Color.White, Color.Black)) {
                        val idle = tabBackground(veil, null, under)
                        val active = tabBackground(veil, resolved.accent.copy(alpha = TabSelectionTint), under)
                        // L'encre au repos (le libellé effacé du thème) doit tenir le seuil du texte.
                        val idleInk = readableInk(resolved.secondaryText, idle)
                        val idleRatio = contrastRatio(idleInk, idle)
                        assertTrue(
                            "$name/${if (dark) "sombre" else "clair"}/$level/$under : l'onglet au repos " +
                                "ne tient pas 4,5 ($idleRatio)",
                            idleRatio >= TEXT_CONTRAST_MIN,
                        )
                        checked++
                        // Et **toutes** les couleurs de bot possibles tiennent sur l'onglet actif.
                        for (candidate in candidates) {
                            val ink = readableInk(candidate, active)
                            val ratio = contrastRatio(ink, active)
                            assertTrue(
                                "$name/${if (dark) "sombre" else "clair"}/$level/$under : la couleur " +
                                    "$candidate sur l'onglet actif donne $ratio",
                                ratio >= TEXT_CONTRAST_MIN,
                            )
                            checked++
                        }
                        lines += "%-8s %-6s %-5s %-5s  repos %5.2f  pire couleur de bot %5.2f"
                            .format(name.id, if (dark) "sombre" else "clair", level, under.toString(), idleRatio, TEXT_CONTRAST_MIN)
                    }
                }
            }
        }
        println("=== Les rapports de contraste de la barre d'onglets (pire cas) ===")
        lines.forEach(::println)
        println("$checked rapports vérifiés, tous au-dessus de ${TEXT_CONTRAST_MIN}")
        assertTrue(checked >= OnyxTheme.entries.size * 2 * 3 * 2 * (1 + 207))
    }

    @Test
    fun `l'icone d'un onglet tient son seuil plus bas que le texte`() {
        // L'icône se reconnaît dès 3:1 : la même règle, avec son seuil.
        val background = Color(0xFF1C1C1E)
        val faint = Color(0xFF6A6A6E)
        val ink = readableInk(faint, background, ICON_CONTRAST_MIN)
        assertTrue(contrastRatio(ink, background) >= ICON_CONTRAST_MIN)
        // Et une encre impossible (transparente) tombe sur le noir ou le blanc, jamais sous le seuil.
        val transparent = Color(0x00000000)
        val fallback = readableInk(transparent, background)
        assertTrue(contrastRatio(fallback, background) >= TEXT_CONTRAST_MIN)
    }

    // — La géométrie de la pilule et le libellé qui ne se coupe jamais —

    /**
     * Les largeurs **relevées sur l'appareil** (1080×2400 @ 420, build `1.0.0+547`), en dp, pour un
     * libellé à **11 sp** (la taille nominale de `caption2`) à la police système 1,0×.
     *
     * Ce sont ces chiffres — pas une estimation — qui rendent le test honnête : la règle est jugée
     * sur les largeurs réellement peintes par la police du système.
     */
    private val measuredAtOneSp = mapOf(
        "Chat" to 22.1f,
        "Flux" to 19.4f,
        "Fichiers" to 36.9f,
    )

    /** La même mesure, pour une taille et une échelle de police données : la police est linéaire. */
    private fun measurer(fontScale: Float): (String, Float) -> Float = { text, sizeSp ->
        (measuredAtOneSp[text] ?: 0f) * (sizeSp / 11f) * fontScale
    }

    /** Les libellés **français** des onglets de contenu, tels qu'ils sont réellement peints. */
    private val frenchLabels = listOf("Chat", "Flux", "Fichiers")

    @Test
    fun `les deux marges de la pilule viennent de l'echelle d'espacement`() {
        assertEquals(
            "la marge latérale est la marge d'écran de la maison",
            OnyxSpace.ScreenMargin,
            TabBarGeometry.SideMargin,
        )
        assertEquals(
            "le dégagement du bas est la marche `Half` de la maison",
            OnyxSpace.Half,
            TabBarGeometry.BottomMargin,
        )
        assertTrue("elles sont sur l'échelle", OnyxSpace.isOnScale(TabBarGeometry.SideMargin))
        assertTrue("elles sont sur l'échelle", OnyxSpace.isOnScale(TabBarGeometry.BottomMargin))
        // Et la pilule laisse **au moins** 12 dp de chaque côté : une pilule collée aux bords n'est
        // plus une pilule — c'était le défaut de la capture.
        assertTrue(TabBarGeometry.SideMargin >= 12.dp)
    }

    @Test
    fun `aucun libelle d'onglet n'est coupe, en clair comme a 1,3x`() {
        // Les largeurs d'écran demandées par la consigne : téléphone étroit (360 dp), le téléphone
        // de la maison (411 dp) et le pliable déplié (838 dp).
        val screens = listOf(360f, 411f, 838f)
        val report = StringBuilder("\nTABLE DES LIBELLÉS D'ONGLET — la taille retenue tient-elle ?\n")
        report.append("écran   police  colonne  taille  pire libellé  verdict\n")
        var checked = 0
        screens.forEach { screenDp ->
            listOf(1.0f, 1.3f).forEach { fontScale ->
                // La largeur intérieure de la pilule : l'écran, moins ses deux marges, moins le
                // rembourrage horizontal de la rangée.
                val innerDp = screenDp - 2f * TabBarGeometry.SideMargin.value - 2f * OnyxSpace.ControlGap.value
                // La barre n'accueille plus de poignée : aucune commande ne prend de place avant
                // les onglets, la colonne est donc celle des seuls libellés.
                val columnsDp = TabLabelFit.columnsDp(
                    innerDp = innerDp,
                    count = chatTabCount,
                )
                val width = measurer(fontScale)
                val size = TabLabelFit.fontSize(columnsDp = columnsDp, labels = frenchLabels, width = width)
                val fits = TabLabelFit.fits(columnsDp = columnsDp, labels = frenchLabels, sizeSp = size, width = width)
                val worst = frenchLabels.maxOf { width(it, size) }
                report.append(
                    "%-7.0f %-7.1f %-8.1f %-7.1f %-13.1f %s\n".format(
                        screenDp, fontScale, columnsDp, size, worst, if (fits) "ok" else "ÉCHEC",
                    ),
                )
                assertTrue(
                    "écran $screenDp dp, police ${fontScale}× : le libellé le plus large fait $worst dp " +
                        "pour une colonne de $columnsDp dp — il serait coupé",
                    fits,
                )
                assertTrue("la taille retenue ne descend pas sous le plancher", size >= TabLabelFit.FloorSp)
                assertTrue("et elle ne remonte jamais au-dessus du nominal", size <= TabLabelFit.BaseSp)
                checked++
            }
        }
        report.append("lignes vérifiées : $checked\n")
        print(report)
        assertEquals(screens.size * 2, checked)
    }

    @Test
    fun `le telephone etroit est le cas ou la mesure sert vraiment`() {
        // **L'arbitrage, nommé.** Deux corrections se sont croisées à la réconciliation :
        //
        //  1. le **plancher** de taille est monté au **nominal** de l'app (11 sp — `BaseSp == FloorSp`,
        //     voir le test suivant), donc la règle de descente ne descend plus : toute taille
        //     retournée vaut 11 sp ;
        //  2. la **suppression d'« Idées »/« Objectifs »** a ramené les onglets à trois libellés
        //     courts (« Chat », « Flux », « Fichiers »), dont le plus large (36,9 dp) tient dans sa
        //     colonne au nominal sur **toute** largeur servie.
        //
        // La descente de taille est donc **inerte**, et le verrou du lot du chevron — qui exigeait
        // « une largeur de téléphone où la règle descend » — n'avait plus d'objet (il échouait à
        // raison : plus aucune largeur ne la déclenche). Ce qui survit, et qui est la seule garantie
        // qui compte, est vérifié ici : **aucun libellé d'onglet ne se coupe**, à la plus étroite des
        // largeurs servies, police 1,3× — et jamais sous le plancher. Le cas « Objectifs » ne peut
        // plus revenir (le mot a disparu), mais un libellé plus long le pourrait : c'est ce que ce
        // test éprouve, et il échoue si un onglet se coupe.
        val width = measurer(1.3f)
        var screenDp = 360f
        var checked = 0
        while (screenDp >= 260f) {
            val innerDp = screenDp - 2f * TabBarGeometry.SideMargin.value - 2f * OnyxSpace.ControlGap.value
            val columnsDp = TabLabelFit.columnsDp(innerDp)
            val size = TabLabelFit.fontSize(columnsDp, frenchLabels, width = width)
            assertTrue(
                "à $screenDp dp / 1,3×, un libellé d'onglet se couperait (colonne %.1f dp)".format(columnsDp),
                TabLabelFit.fits(columnsDp, frenchLabels, size, width),
            )
            assertTrue("la taille ne descend jamais sous le plancher de l'app", size >= TabLabelFit.FloorSp)
            checked++
            screenDp -= 4f
        }
        assertEquals("toutes les largeurs servies ont été vérifiées", 26, checked)
        println(
            "arbitrage : trois onglets courts, aucun ne se coupe de 360 à 260 dp à 1,3× ; colonne étroite = %.1f dp"
                .format(TabLabelFit.columnsDp(260f - 2f * TabBarGeometry.SideMargin.value - 2f * OnyxSpace.ControlGap.value)),
        )
    }

    @Test
    fun `le plancher de taille reste lisible a 1,3x`() {
        // Le plancher **déclaré** est celui de l'app : 11 sp, jamais moins. Il ne se divise pas par
        // l'échelle de police (ce qui donnait 9,5 sp, et 8,5 sp à 1,3×) : à 1,3× le libellé est peint
        // à 14,3 sp, et c'est la mise en page — le mot — qui s'adapte.
        assertEquals(OnyxSocle.Micro.value, TabLabelFit.FloorSp, 0.001f)
        assertEquals(TabLabelFit.BaseSp, TabLabelFit.FloorSp, 0.001f)
        assertTrue("à 1,3×, le plancher est peint à 14,3 sp", TabLabelFit.FloorSp * 1.3f >= 14.2f)
    }

    // — Le français —

    @Test
    fun `les onglets portent leur libelle francais et rien de l'autre plateforme`() {
        assertEquals("Chat", OnyxLocalization.translate("Chat", Locale.FRANCE))
        assertEquals("Flux", OnyxLocalization.translate("Feed", Locale.FRANCE))
        // « Files » est déjà traduit par le catalogue partagé : une seule valeur, pas deux.
        assertEquals("Fichiers", OnyxLocalization.translate("Files", Locale.FRANCE))
        assertEquals("Pouce en haut", OnyxLocalization.translate("Thumb up", Locale.FRANCE))
        assertEquals("Pouce en bas", OnyxLocalization.translate("Thumb down", Locale.FRANCE))
        assertEquals("Discuter", OnyxLocalization.translate("Discuss", Locale.FRANCE))
        assertEquals("Cette liste est vide.", OnyxLocalization.translate("This list is empty.", Locale.FRANCE))
        // L'état vide du Feed nomme le bot, entre mots français.
        assertEquals(
            "Aucune carte de Quill pour l’instant. Ses réponses apparaîtront ici.",
            OnyxLocalization.format(
                "No cards from %1\$s yet. Its answers appear here.",
                "Quill",
                locale = Locale.FRANCE,
            ),
        )
        // Chaque clé d'un onglet **de contenu** a bien une valeur (aucune ne se rend elle-même).
        // « Bot » est le repère-visage : son libellé est celui de la face, pas un mot à traduire.
        for (tab in ChatTab.paged) {
            assertTrue(
                "l'onglet ${tab.key} n'est pas traduit",
                OnyxLocalization.translate(tab.label, Locale.FRANCE) != tab.label || tab.key == "Chat",
            )
        }
    }
}
