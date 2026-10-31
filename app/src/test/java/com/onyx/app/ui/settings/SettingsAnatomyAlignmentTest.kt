package com.onyx.app.ui.settings

import com.onyx.app.localization.OnyxLocalization
import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **L'anatomie des Réglages**, alignée sur celle de la référence : l'en-tête à grande flèche et
 * titre très grand, la carte de la passerelle à coins très arrondis et pilule à bordure, les
 * sections en petites capitales grises, les rangées à **vignette d'icône grise** + libellé +
 * description + **un seul contrôle à droite**, les **filets en retrait** (ils commencent après la
 * vignette et s'arrêtent avant le bord droit), l'interrupteur **bleu allumé / gris éteint**, le
 * segmenté à pastille blanche et icônes, les pastilles d'accent cerclées, le **curseur de taille de
 * texte** avec ses « A » et sa mention « Défaut », et le **pied de version / d'état**.
 *
 * Ce fichier ne décrit pas des intentions : il lit le code écrit et vérifie les nombres du gabarit.
 */
class SettingsAnatomyAlignmentTest {

    private fun appSource(path: String): String = File("src/main/java/com/onyx/app/$path").readText()
    private fun coreSource(path: String): String = File("../core/src/main/java/com/onyx/app/$path").readText()

    private val anatomy = coreSource("ui/design/OnyxAnatomy.kt")
    private val form = coreSource("ui/design/OnyxForm.kt")
    private val screen = appSource("ui/settings/SettingsScreen.kt")
    private val card = appSource("ui/settings/GatewayCard.kt")
    private val rows = appSource("ui/settings/AppearanceRows.kt")

    // MARK: - Le gabarit : une seule source pour les mesures

    @Test
    fun `le gabarit porte les mesures de la reference`() {
        listOf(
            "val RowPaddingH: Dp = 14.dp",
            "val RowPaddingV: Dp = 13.dp",
            "val RowGap: Dp = 14.dp",
            "val TitleDetailGap: Dp = 1.dp",
            "val NavRowMinHeight: Dp = 56.dp",
            "val TileSize: Dp = 32.dp",
            "val TileCorner: Dp = 10.dp",
            "val TileIcon: Dp = 15.dp",
            "val Chevron: Dp = 16.dp",
            "val PillMinHeight: Dp = 32.dp",
            "const val DetailLines: Int = 2",
        ).forEach { assertTrue("$it doit être dans le gabarit", anatomy.contains(it)) }
        // Le filet part après la vignette, et s'arrête avant le bord droit.
        assertTrue(anatomy.contains("val DividerInsetWithTile: Dp = RowPaddingH + TileSize + RowGap"))
        assertTrue(anatomy.contains("val DividerEndInset: Dp = RowPaddingH"))
    }

    // MARK: - La vignette d'icône grise, et la rangée

    @Test
    fun `la vignette de rangee est grise et non une pastille vive`() {
        // Le fond est le gris des creux (`fill`), le rayon et le glyphe ceux du gabarit, et l'encre
        // est **mesurée** sur ce fond (3:1) — jamais un gris posé au hasard.
        assertTrue(anatomy.contains("fun OnyxIconTile("))
        assertTrue(anatomy.contains(".background(theme.fill, RoundedCornerShape(Anatomy.TileCorner))"))
        assertTrue(anatomy.contains("OnyxInkOn(theme.secondaryText, theme.fill, OnyxContrast.ICON_MIN)"))
        assertTrue(anatomy.contains("size = Anatomy.TileIcon"))
    }

    @Test
    fun `la rangee porte vignette libelle description et le controle a droite`() {
        // La rangée qui ouvre une page : hauteur minimale de la référence, libellé puis description
        // sur deux lignes au plus, chevron au bout.
        assertTrue(anatomy.contains("fun OnyxDetailRow("))
        assertTrue(anatomy.contains(".heightIn(min = Anatomy.NavRowMinHeight)"))
        assertTrue(anatomy.contains("maxLines = Anatomy.DetailLines"))
        // La rangée interrupteur : la même anatomie, l'interrupteur à la place du chevron.
        assertTrue(anatomy.contains("fun OnyxSwitchDetailRow("))
        assertTrue(anatomy.contains("OnyxSwitch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)"))
        // La rangée d'information du pied : le titre, la valeur dessous.
        assertTrue(anatomy.contains("fun OnyxInfoRow("))
        // Et la rangée de formulaire (celle des sous-pages) prend la vignette grise et le gabarit.
        assertTrue(form.contains("symbol?.let { OnyxIconTile(symbol = it, tint = tint) }"))
        assertTrue(form.contains(".heightIn(min = Anatomy.NavRowMinHeight)"))
    }

    @Test
    fun `le filet commence apres la vignette et s arrete avant le bord droit`() {
        assertTrue(anatomy.contains("fun OnyxAnatomyDivider()"))
        assertTrue(anatomy.contains("inset = Anatomy.DividerInsetWithTile"))
        assertTrue(anatomy.contains("endInset = Anatomy.DividerEndInset"))
        // Les rangées des Réglages s'en servent, pas du filet bord à bord.
        assertTrue("les Réglages prennent le filet en retrait", screen.contains("OnyxAnatomyDivider()"))
        assertFalse("plus de filet bord à bord dans le corps des Réglages",
            screen.substringBefore("fun AppearanceScreen(").contains("OnyxRowDivider()"))
    }

    // MARK: - Les contrôles

    @Test
    fun `l interrupteur est bleu allume et gris eteint, aux dimensions de la reference`() {
        assertTrue(form.contains("val track = if (checked) OnyxContrast.distinctOn(theme.accent, theme.formPageColor) else theme.fill"))
        assertTrue(form.contains(".size(width = 46.dp, height = 28.dp)"))
        assertTrue(form.contains(".size(22.dp)"))
    }

    @Test
    fun `les pastilles d accent ont l anneau de la reference`() {
        // Une pastille de 38 dp dans une cible de 48 dp, un anneau de 2 dp sur la retenue.
        assertTrue(rows.contains(".size(48.dp)"))
        assertTrue(rows.contains(".size(38.dp)"))
        assertTrue(rows.contains("width = if (selected) 2.dp else 0.dp"))
        assertTrue(rows.contains("color = if (selected) color"))
    }

    @Test
    fun `le curseur de taille de texte a ses A, ses crans et sa mention`() {
        assertTrue(anatomy.contains("fun OnyxTextSizeSlider("))
        assertTrue("un « A » à chaque bout", anatomy.contains("text = \"A\""))
        assertTrue("les crans intermédiaires", anatomy.contains("horizontalArrangement = Arrangement.SpaceBetween"))
        assertTrue("le nom du cran courant sous la piste", anatomy.contains("text = labels[index]"))
        assertTrue("le curseur se lit comme un curseur", anatomy.contains("progressBarRangeInfo = ProgressBarRangeInfo("))
        // Les cinq crans du réglage, et le cran du milieu qui se dit « Défaut ».
        assertTrue(rows.contains("fun ChatTextSizeRow("))
        assertTrue(rows.contains("ResolvedTheme.chatTextSizes.map { chatTextStopLabel(it) }"))
        assertTrue(rows.contains("steps == 0 -> \"Défaut\""))
        assertTrue(screen.contains("ChatTextSizeRow(steps = chatTextSize, onPick = onChatTextSize)"))
    }

    @Test
    fun `les boutons d action sont des pilules a bordure`() {
        assertTrue(anatomy.contains("fun OnyxPillButton("))
        // LOT traits-plat : le contour est **le filet de la maquette** (12 %, `OnyxEdge.fil`) — il
        // était une encre de la référence mesurée à 3:1, donc pleinement opaque, doublée d'un
        // `theme.quaternaryText` tout aussi opaque : deux encadrements gris prononcés.
        assertTrue(
            "le filet de la maquette, aucun encadrement opaque",
            anatomy.contains(".border(OnyxSpace.Stroke, OnyxEdge.fil(theme), CircleShape)"),
        )
        assertFalse(
            "plus d'encre opaque en contour de pilule",
            anatomy.contains("if (busy) theme.quaternaryText else stroke"),
        )
        // L'encre passe par la **composition** avant la mesure : un gris de système translucide ne
        // se lit pas sur sa couleur nue.
        assertTrue(anatomy.contains("fun OnyxInkOn(ink: Color, background: Color, minRatio: Float = TextInkTarget): Color"))
        assertTrue(anatomy.contains("const val TextInkTarget: Float = 4.75f"))
        assertTrue(anatomy.contains("OnyxContrast.distinctOn(OnyxContrast.over(ink, background), background, minRatio)"))
        // La carte de la passerelle s'en sert pour « Vérifier », avec sa petite icône d'onde.
        assertTrue(card.contains("OnyxPillButton("))
        assertTrue(card.contains("symbol = Symbols.Waveform"))
        assertTrue(card.contains("VerifyButton(label = OnyxLocalization[\"Check\"]"))
    }

    @Test
    fun `le theme se choisit par un segmente a icones`() {
        assertTrue(screen.contains("AppearanceMode.SYSTEM -> Symbols.Phone"))
        assertTrue(screen.contains("AppearanceMode.LIGHT -> Symbols.SunMax"))
        assertTrue(screen.contains("AppearanceMode.DARK -> Symbols.MoonFill"))
        assertTrue(form.contains("icons: List<Int?> = emptyList()"))
    }

    // MARK: - L'en-tête, la carte, le pied

    @Test
    fun `l en tete est la grande fleche puis le titre en tres grand`() {
        assertTrue(anatomy.contains("fun OnyxLargePageHeader("))
        assertTrue("une grande flèche de retour", anatomy.contains(".size(44.dp)") && anatomy.contains("modifier = Modifier.rotate(180f)"))
        assertTrue("le titre en très grand, aligné à gauche", anatomy.contains("style = theme.largeTitle.copy("))
        assertTrue("le titre est un titre pour l'accessibilité", anatomy.contains("heading()"))
        // Les Réglages l'utilisent, et gardent le contrôle du volet dans leur barre.
        // (Vague 2 : l'en-tête passe au grand titre du socle — 30 sp, −0,032 em, ExtraBold —
        // par `SocleLargeHeader`, qui garde la flèche de retour et le contrôle du volet.)
        assertTrue(screen.contains("SocleLargeHeader("))
        assertTrue(screen.contains("title = \"Settings\""))
        assertTrue(screen.contains("onBack = onDone"))
        assertTrue(screen.contains("actions = sidebarControl"))
        // La page Apparence, atteinte depuis les Réglages, prend le même en-tête.
        assertTrue(screen.contains("OnyxLargePageHeader(title = \"Appearance\", onBack = onBack)"))
    }

    @Test
    fun `la carte de la passerelle prend les coins de la reference et la marque de la maison`() {
        assertTrue(card.contains(".background(theme.formSurfaceColor, RoundedCornerShape(20.dp))"))
        assertFalse("aucune bordure sur la carte", card.contains(".border(1.dp, theme"))
        // **Le logo de l'app dans la vignette**, et non plus l'initiale du serveur : le rond bleu à
        // lettre « O » devient la **marque** — la plaque sombre, le cercle et la rune, aux couleurs
        // de l'icône de lanceur. Le verrou suit la décision — un test qui exige encore l'initiale
        // passerait au vert en contredisant la demande.
        assertTrue("la marque de la maison dans la vignette", card.contains("OnyxBrandMark("))
        assertFalse(
            "l'initiale du serveur n'est plus dessinée",
            card.contains("firstOrNull()?.uppercaseChar()"),
        )
        // **La vignette n'est plus teintée de l'accent.** C'était la faute : un logo dérivé du
        // thème. La marque a ses propres teintes, prises sur l'icône, les mêmes dans les deux
        // apparences.
        assertFalse(
            "la vignette n'est plus teintée de l'accent — le logo ne se dérive pas du thème",
            card.contains("theme.accent.copy(alpha = 0.16f)"),
        )
    }

    @Test
    fun `le pied montre la version, l etat de la passerelle et une rangee d action`() {
        assertTrue(screen.contains("OnyxInfoRow("))
        assertTrue(screen.contains("title = \"App version\""))
        assertTrue(screen.contains("title = \"Gateway\""))
        assertTrue("l'état pendant la vérification", screen.contains("OnyxLocalization[\"Verifying…\"]"))
        // Une rangée d'action du pied : la vignette grise et le chevron.
        assertTrue(screen.contains("OnyxIconTile(symbol = symbol"))
    }

    @Test
    fun `la carte du formulaire a les coins de la maison`() {
        assertTrue(form.contains(".background(theme.formSurfaceColor, RoundedCornerShape(OnyxSpace.CardCorner))"))
        assertTrue("le rayon de carte existe bien", coreSource("ui/design/Spacing.kt").contains("val CardCorner: Dp = 20.dp"))
    }

    // MARK: - L'inventaire : rien n'a disparu

    @Test
    fun `tous les reglages d Onyx restent atteignables`() {
        listOf(
            "\"Appearance\"", "\"Chat\"", "\"Voice\"", "\"Notifications\"", "\"Phone\"", "\"Privacy\"",
            "\"Account\"", "\"About\"",
        ).forEach { assertTrue("la section $it", screen.contains("header = $it")) }
        // L'entrée « Skills » des Réglages a été **retirée** par le lot du doublon (l'éditeur verse
        // dans « Capacités ») : elle n'est plus de cet inventaire, et son absence est verrouillée par
        // `CapabilitiesDoublonRetireTest`. Tout le reste de la liste reste exigé.
        listOf(
            "AgentEntry(\"Capabilities\"", "AgentEntry(\"Insights\"",
            "AgentEntry(\"Tools\"", "AgentEntry(\"Bots\"", "AgentEntry(\"Profile\"",
            "AgentEntry(\"Context files\"", "AgentEntry(\"Memory\"", "AgentEntry(\"Files\"",
            "AgentEntry(\"Gateway\"", "AgentEntry(\"Cron\"", "AgentEntry(\"Kanban\"",
        ).forEach { assertTrue("l'entrée $it", screen.contains(it)) }
        listOf(
            "ChatTextSizeRow(", "BlurbToggleRow(", "AccentSwatchRow(", "AppIconSettings(embedded = true)",
            "VoiceOrbSettingsRows(", "Connection details", "Context window", "Terminal", "Lock",
            "Set up another device", "What's New", "Support Onyx", "Erase everything…",
            "title = \"Pure black\"", "title = \"Halo\"", "\"Language\"",
        ).forEach { assertTrue("$it doit rester", screen.contains(it)) }
    }

    // MARK: - Le français

    @Test
    fun `les libelles du gabarit sont dits en francais`() {
        assertEquals("Version de l’app", OnyxLocalization.translate("App version", Locale.FRENCH))
        assertEquals("Taille du texte", OnyxLocalization.translate("Text size", Locale.FRENCH))
        assertEquals("Vérifier la connexion", OnyxLocalization.translate("Check connection", Locale.FRENCH))
        assertEquals("Vérification…", OnyxLocalization.translate("Verifying…", Locale.FRENCH))
        assertEquals("Défaut", OnyxLocalization.translate("Défaut", Locale.FRENCH))
        assertEquals("La plus petite", OnyxLocalization.translate("Smallest", Locale.FRENCH))
        assertEquals("Petite", OnyxLocalization.translate("Small", Locale.FRENCH))
        assertEquals("Grande", OnyxLocalization.translate("Large", Locale.FRENCH))
        assertEquals("La plus grande", OnyxLocalization.translate("Largest", Locale.FRENCH))
        // En anglais, le cran du milieu se dit dans sa langue ; « Default » garde son sens ailleurs.
        assertEquals("Default", OnyxLocalization.translate("Défaut", Locale.ENGLISH))
        assertEquals("Par défaut", OnyxLocalization.translate("Default", Locale.FRENCH))
    }
}
