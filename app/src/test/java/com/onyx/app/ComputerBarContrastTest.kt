package com.onyx.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La barre du bas de l'écran « Ordinateur », en clair **et** en sombre, et le glyphe du plein écran.
 *
 * Deux défauts signalés par le propriétaire, et ce qui les tient fermés :
 *
 * 1. **les quatre touches ne se lisaient pas** : leur encre venait de `tertiaryLabel` (un libellé
 *    effacé du système) et leur fond de `fill` — deux teintes translucides qui se composaient sur
 *    la page ou sur la scène du plein écran à 1,1:1 … 2,5:1. La barre tire maintenant **toutes** ses
 *    couleurs d'un `BarInk` mesuré (`BarInkContrastTest`, dans `:core`, exige les seuils WCAG sur
 *    les sept thèmes, les deux apparences et les deux fonds).
 * 2. **le symbole du plein écran ne disait pas l'action** : le bouton montrait les deux flèches en
 *    diagonale (`Symbols.Expand`, qui sert à déplier un bloc de réponse) dans les deux états. Il
 *    porte maintenant deux glyphes maison — `FullScreenEnter` / `FullScreenExit` — dont la
 *    géométrie est relevée par `FullScreenGlyphTest`.
 *
 * Ce test-ci est celui du **câblage** : ce qui ne peut pas revenir sans qu'on s'en aperçoive.
 */
class ComputerBarContrastTest {

    private fun read(path: String) = File(path).readText()

    private val screen = read("src/main/java/com/onyx/app/ui/computer/ComputerScreen.kt")
    private val symbols = read("../core/src/main/java/com/onyx/app/ui/design/Symbols.kt")
    private val interactions = read("../core/src/main/java/com/onyx/app/ui/design/Interactions.kt")

    private val status = screen.substringAfter("private fun ComputerStatusLine(")
        .substringBefore("private fun ComputerNotes(")
    private val notes = screen.substringAfter("private fun ComputerNotes(")
        .substringBefore("private fun ComputerOutput(")
    private val note = screen.substringAfter("private fun ComputerNote(")
        .substringBefore("private fun ComputerOutput(")
    private val controls = screen.substringAfter("private fun ComputerControls(")
        .substringBefore("private fun ComputerHandButton(")
    private val hand = screen.substringAfter("private fun ComputerHandButton(")
        .substringBefore("private fun ComputerKeyButton(")
    private val key = screen.substringAfter("private fun ComputerKeyButton(")
        .substringBefore("private fun computerBarInk(")

    @Test
    fun `la barre tire son encre du fond sur lequel elle se pose, scene comprise`() {
        // Le plein écran pose la barre sur la **scène**, toujours sombre : une encre d'apparence y
        // écrirait du noir sur du noir dès que le thème est clair. Une seule règle, ici.
        assertTrue(
            screen.contains(
                """
                private fun computerBarInk(theme: ResolvedTheme, fullscreen: Boolean): BarInk = BarInk(
                    surface = if (fullscreen) theme.stageColor else theme.pageColor,
                    ink = if (fullscreen) theme.stageText else theme.textColor,
                    accent = theme.accent,
                )
                """.trimIndent(),
            ),
        )
        // Les trois composables de la barre la reçoivent ; aucun ne la recalcule à sa façon.
        assertTrue(screen.contains("ComputerControls("))
        assertTrue(status.contains("bar: BarInk"))
        assertTrue(notes.contains("bar: BarInk"))
        assertTrue(note.contains("bar: BarInk"))
        assertTrue(controls.contains("bar: BarInk"))
        assertTrue(hand.contains("bar: BarInk"))
        assertTrue(key.contains("bar: BarInk"))
    }

    @Test
    fun `plus une seule encre figee ou effacee dans la barre`() {
        // Ce qui donnait 1,1:1 en plein écran clair et 2,5:1 en sombre : un libellé du système
        // (tertiary/quaternary) sur une teinte `fill`, et l'accent du thème posé sur la scène.
        for ((nom, corps) in listOf(
            "les commandes" to controls,
            "le bouton de la main" to hand,
            "les touches" to key,
            "la ligne d'état" to status,
            "les notes" to notes,
            "une note" to note,
        )) {
            listOf("theme.fill", "theme.tertiaryText", "theme.quaternaryText").forEach { couleur ->
                assertFalse("$nom : $couleur revient dans la barre", corps.contains(couleur))
            }
        }
        // Aucune ne consulte le système de la référence directement : tout passe par l'encre mesurée.
        assertFalse("le fond du système ne doit plus dessiner la barre", key.contains("OnyxSystem"))
        assertFalse("un libellé effacé du système ne doit plus dessiner la barre", key.contains("tertiaryLabel"))
    }

    @Test
    fun `chaque touche porte la case, sa bordure et son encre d'etat`() {
        // Le fond de la case vient de l'encre mesurée ; le cran « précision » du lot tactile y
        // pose son surlignage d'accent — jamais une teinte de `fill`.
        assertTrue(
            "le fond de la case, surlignage d'accent compris",
            key.contains("if (highlight && enabled) theme.accent.copy(alpha = 0.20f) else bar.swatch"),
        )
        assertTrue("la bordure fine", key.contains(".border(BorderStroke(1.dp, bar.edge), RoundedCornerShape(OnyxSpace.ControlCorner))"))
        assertTrue("l'icône active", key.contains("tint = if (enabled) bar.icon else bar.iconIdle"))
        // Trois cases dans la rangée : Retour, Échap, Viser. Les quatre autres — Haut, Bas,
        // Ajuster, Plein écran — sont repliées dans « Plus » : c'est ce qui fait tenir la rangée.
        assertEquals("trois cases dessinées", 3, Regex("ComputerKeyButton\\(").findAll(controls).count())
        // Quatre envois d'encre : les trois cases, et la pastille de la main.
        assertEquals("chacune reçoit l'encre de la barre", 4, Regex("bar = bar").findAll(controls).count())
        // La pastille de la main aussi : la seule pleine, et son encre est choisie par mesure.
        assertTrue(hand.contains("if (active) bar.accentFill else bar.swatch"))
        assertTrue(hand.contains("active -> bar.onAccent"))
        assertTrue(hand.contains("else -> bar.mutedOnSwatch"))
        assertTrue(hand.contains(".border(BorderStroke(1.dp, bar.edge), RoundedCornerShape(OnyxSpace.ControlCorner))"))
    }

    @Test
    fun `l'opacite d'un controle desactive ne vient pas s'ajouter a une encre mesuree`() {
        // Le deuxième coupable, trouvé en mesurant sur les pixels : `onyxClickable(enabled = false)`
        // posait une opacité de 0,35 sur **tout** le contenu. Une encre calculée pour tenir 3:1
        // redescendait donc à 1,5:1 sur l'écran (relevé : #777777 voulu, #474747 affiché, dans une
        // case #2E2E2E). Les deux contrôles de la barre portent leur propre encre de repos et
        // écartent cette couche ; le reste de l'app garde l'atténuation habituelle.
        assertTrue("la couche est paramétrable", interactions.contains("disabledAlpha: Float = 0.35f"))
        assertTrue("et elle sert vraiment", interactions.contains(".alpha(if (enabled) alpha else disabledAlpha)"))
        assertEquals(
            "les deux contrôles de la barre l'écartent",
            2,
            Regex("onyxClickable\\(enabled = enabled, disabledAlpha = 1f").findAll(screen).count(),
        )
        assertTrue("le fond et la bordure restent sous cette couche", key.contains("disabledAlpha = 1f, onClick = onClick)"))
    }

    @Test
    fun `l'invite, l'envoi et la ligne d'etat suivent la meme encre`() {
        assertTrue(controls.contains("style = theme.caption.copy(color = bar.muted)"))
        assertTrue(controls.contains("tint = if (typed.isNotEmpty()) bar.ink else bar.iconIdleOnSurface"))
        assertTrue(status.contains("if (state.humanHolds) bar.signal else bar.muted"))
        assertTrue(status.contains("style = theme.caption.copy(color = bar.ink)"))
        // Le détail et la résolution : l'encre atténuée, jamais au-dessous de 4,5:1.
        assertEquals("deux textes atténués", 2, Regex("color = bar.muted,").findAll(status).count())
        assertTrue(notes.contains("tone = bar.mutedOnSwatch"))
        assertTrue(note.contains("tint = bar.iconIdle"))
    }

    @Test
    fun `le bouton de plein ecran porte deux glyphes distincts, pas les fleches d'un bloc`() {
        // Le plein écran a quitté la rangée du bas pour « Plus » : c'est le **même** couple de
        // glyphes, posé sur une ligne nommée (« Plein écran » / « Quitter le plein écran »).
        val menu = screen.substringAfter("items = buildList {").substringBefore("onDismiss = { showMenu = false }")
        assertTrue(
            "l'état commande le glyphe",
            menu.contains("symbol = if (fullscreen) Symbols.FullScreenExit else Symbols.FullScreenEnter"),
        )
        // Le glyphe des deux flèches en diagonale dit « agrandir un bloc » : il reste aux blocs de
        // réponse, il ne commande plus le plein écran.
        assertFalse("les flèches en diagonale ne commandent plus le plein écran", menu.contains("Symbols.Expand"))
        assertFalse("ni son ancien inverse", menu.contains("Symbols.ExitFullscreen"))
        // Le bandeau, lui, ne propose que la sortie.
        val banner = screen.substringAfter("private fun FullscreenBanner(")
            .substringBefore("private fun ComputerWaiting(")
        assertTrue(banner.contains("OnyxIcon(Symbols.FullScreenExit,"))
        assertFalse(banner.contains("Symbols.Expand"))
        // Les deux entrées existent dans le jeu d'icônes, et pointent deux fichiers différents.
        assertTrue(symbols.contains("val FullScreenEnter = R.drawable.ic_sym_fullscreen_enter"))
        assertTrue(symbols.contains("val FullScreenExit = R.drawable.ic_sym_fullscreen_exit"))
        assertFalse("l'ancien symbole n'a plus de raison d'être", symbols.contains("ExitFullscreen"))
        assertFalse("et son dessin non plus", File("../core/src/main/res/drawable/ic_sym_arrow_down_right_and_arrow_up_left.xml").exists())
    }

    @Test
    fun `les deux glyphes du plein ecran sont bien deux fichiers, sur la grille du jeu`() {
        for (nom in listOf("ic_sym_fullscreen_enter.xml", "ic_sym_fullscreen_exit.xml")) {
            val xml = read("../core/src/main/res/drawable/$nom")
            assertTrue("$nom : 24 dp", xml.contains("android:width=\"24dp\""))
            assertTrue("$nom : trait de 2", xml.contains("android:strokeWidth=\"2\""))
            assertTrue("$nom : quatre coins", Regex("android:pathData").findAll(xml).count() == 4)
        }
    }
}
