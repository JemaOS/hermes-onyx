package com.onyx.app.ui.sidebar

import androidx.compose.ui.graphics.Color
import com.onyx.app.ui.design.OnyxContrast
import com.onyx.app.ui.design.OnyxSpace
import com.onyx.app.ui.design.flatActivePillColor
import com.onyx.app.ui.design.flatActivePillInk
import com.onyx.app.ui.design.flatWellColor
import com.onyx.app.ui.design.sidebarPaneColor
import com.onyx.app.ui.theme.OnyxFonts
import com.onyx.app.ui.theme.OnyxTheme
import com.onyx.app.ui.theme.ResolvedTheme
import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le volet du thème clair, tel qu'il est habillé** : les points du lot, chacun mesuré ou verrouillé
 * dans le source.
 *
 * Ce fichier tient quatre choses, et aucune n'est une impression :
 *
 * 1. **Une seule colonne d'alignement** — l'arithmétique est faite sur les jetons de l'échelle, pas
 *    sur des nombres recopiés : la marge de grille des tuiles plus leur rembourrage interne doit
 *    **valoir** la marge de la liste plus le rembourrage d'une rangée, et toutes deux doivent valoir
 *    le retrait de la tête.
 * 2. **Une hauteur de tuile constante** — un seul jeton, une seule hauteur, et le libellé sur une
 *    ligne.
 * 3. **La ligne d'état hors ligne** — la phrase est **pure** et vérifiable, elle ne contient aucun
 *    code d'erreur, et c'est bien elle (et non un rond dans la tête) qui porte le réessai.
 * 4. **Aucune fonction retirée** — les tuiles, les puces, les filtres, la liste et le pied sont
 *    toujours là, et chaque tuile garde son écran.
 */
class SidebarFlatLightTest {

    private val sidebar = read("src/main/java/com/onyx/app/ui/sidebar/ChatSidebar.kt")
    private val bots = read("src/main/java/com/onyx/app/ui/sidebar/ChatSidebarBots.kt")
    private val model = read("src/main/java/com/onyx/app/ui/sidebar/ChatSidebarModel.kt")
    private val scaffold = read("src/main/java/com/onyx/app/ui/sidebar/SidebarScaffold.kt")

    private fun read(path: String): String =
        listOf(File(path), File("../$path")).firstOrNull { it.isFile }?.readText() ?: ""

    private fun theme(theme: OnyxTheme, dark: Boolean) =
        ResolvedTheme.resolve(theme, dark = dark, fonts = OnyxFonts.System)

    // -----------------------------------------------------------------------------------------
    // 1. Une seule colonne à gauche
    // -----------------------------------------------------------------------------------------

    @Test
    fun `les icones de tuile, les cercles de liste et la tete partagent la meme colonne`() {
        // L'arithmétique du lot, sur les jetons de l'échelle — **et sans marge de liste** : c'est
        // chaque bloc qui porte la sienne, sinon elle s'ajouterait à celles des blocs qui ont déjà
        // leur rembourrage (mesuré sur la capture : les tuiles glissaient de 8 dp).
        //   tuile  : marge de grille (Snug, 12) + rembourrage interne (Hair, 4) = 16
        //   rangée : rembourrage de rangée (Base, 16)                             = 16
        //   puce   : marge de rangée (Hair, 4) + rembourrage de puce (Snug, 12)   = 16
        //   tête   : retrait de barre (BarInset, 16)                              = 16
        val tileIcon = OnyxSpace.Snug + OnyxSpace.Hair
        val rowDisc = OnyxSpace.Base
        val headText = OnyxSpace.BarInset
        assertEquals("l'icône d'une tuile n'est pas dans la colonne de la tête", OnyxSpace.BarInset, tileIcon)
        assertEquals("le cercle d'une rangée n'est pas dans la colonne de la tête", OnyxSpace.BarInset, rowDisc)
        assertEquals("les trois colonnes doivent être la même", OnyxSpace.BarInset, headText)
        assertEquals(
            "le libellé d'une puce n'est pas dans la colonne",
            OnyxSpace.BarInset,
            OnyxSpace.Hair + OnyxSpace.Snug,
        )
        // Le source **porte** bien ces valeurs aux endroits dits — sinon le calcul serait un vœu.
        assertTrue("la tuile ne prend pas le rembourrage de la colonne", sidebar.contains(".padding(horizontal = OnyxSpace.Hair, vertical = OnyxSpace.Half),"))
        assertTrue("la liste porte encore une marge horizontale", sidebar.contains("contentPadding = PaddingValues(bottom = OnyxSpace.Snug),"))
        assertTrue("la rangée n'a pas le rembourrage de la colonne", sidebar.contains(".padding(horizontal = OnyxSpace.Base, vertical = 6.dp),"))
        assertTrue("la tête a perdu son retrait de barre", sidebar.contains("start = OnyxSpace.BarInset,"))
        assertTrue("les filtres n'ont pas la marge de la colonne", sidebar.contains(".padding(start = OnyxSpace.Hair, end = OnyxSpace.Hair, top = 2.dp, bottom = 6.dp),"))
        // Le côté *Bots* suit la même colonne : 4 de grille + 12 de rangée.
        assertTrue("la grille des bots n'aligne pas ses rangées", bots.contains("start = OnyxSpace.Hair,"))
    }

    // -----------------------------------------------------------------------------------------
    // 2. Hauteur de tuile constante
    // -----------------------------------------------------------------------------------------

    @Test
    fun `toutes les tuiles ont la meme hauteur plancher, et le libelle qui se replie n est pas coupe`() {
        assertTrue("la tuile n'a pas de hauteur nommée", sidebar.contains("internal val SidebarTileHeight: Dp = OnyxSpace.ThumbnailSize"))
        // **La hauteur est un plancher, pas un plafond.** Un libellé qui se replie sur deux lignes à
        // police 1,3× (« Fichiers du serveur ») doit pouvoir grandir : une hauteur fixe de 44 dp
        // **coupait** la seconde ligne au bas de la tuile (mesuré sur l'appareil). `heightIn(min = …)`
        // garde la cible tactile (44 dp) et laisse la tuile prendre la hauteur que son libellé demande.
        assertTrue("la tuile pose une hauteur fixe qui couperait un repli", sidebar.contains(".heightIn(min = SidebarTileHeight)"))
        assertFalse("la tuile ne doit plus figer sa hauteur", sidebar.contains(".height(SidebarTileHeight)"))
        // La hauteur plancher est la **cible tactile** de la maison : une tuile se touche.
        assertTrue("une tuile est plus basse que la cible tactile", SidebarTileHeight.value >= MinTarget.value)
        // Et les deux tuiles d'une rangée grandissent **ensemble** : la rangée prend la hauteur de sa
        // plus haute tuile (`IntrinsicSize.Min`) et chaque tuile la remplit — sinon l'une dépasserait
        // l'autre et la grille perdrait son alignement de rangée.
        assertTrue(
            "la rangée ne prend pas la hauteur de sa plus haute tuile",
            sidebar.contains("Modifier.height(IntrinsicSize.Min)"),
        )
        assertTrue(
            "la tuile ne remplit pas la hauteur de sa rangée",
            sidebar.contains("Modifier.weight(1f).fillMaxHeight()"),
        )
        // Le libellé se range sur **une ligne par bloc** avec ellipse : jamais de coupure sèche.
        val cell = sidebar.substringAfter("private fun SidebarTileCell(").substringBefore("// ------")
        assertTrue("le libellé d'une tuile se replie encore", cell.contains("maxLines = 1,"))
        assertFalse("le libellé d'une tuile se replie encore", cell.contains("maxLines = 2,"))
    }

    // -----------------------------------------------------------------------------------------
    // 3. La ligne d'état hors ligne
    // -----------------------------------------------------------------------------------------

    @Test
    fun `la ligne d etat dit ce qu on a et ce qui manque, sans aucun code d erreur`() {
        // La phrase de la **référence**, mot pour mot, dans les deux langues de l'app.
        assertEquals(
            "Saved copy · 9 h ago. The server is unreachable for now.",
            savedCopyNotice(9 * 3600.0, Locale.ENGLISH),
        )
        assertEquals(
            "Copie enregistrée · il y a 9 h. Le serveur est injoignable pour le moment.",
            savedCopyNotice(9 * 3600.0, Locale.FRANCE),
        )
        assertEquals("just now", savedCopyAgeLabel(30.0, Locale.ENGLISH))
        assertEquals("à l'instant", savedCopyAgeLabel(30.0, Locale.FRANCE))
        assertEquals("12 min ago", savedCopyAgeLabel(12 * 60.0, Locale.ENGLISH))
        assertEquals("il y a 12 min", savedCopyAgeLabel(12 * 60.0, Locale.FRANCE))
        assertEquals("9 h ago", savedCopyAgeLabel(9 * 3600.0, Locale.ENGLISH))
        assertEquals("il y a 9 h", savedCopyAgeLabel(9 * 3600.0, Locale.FRANCE))
        assertEquals("3 d ago", savedCopyAgeLabel(3 * 86_400.0, Locale.ENGLISH))
        assertEquals("2 w ago", savedCopyAgeLabel(15 * 86_400.0, Locale.ENGLISH))
        // Sans copie : la phrase ne prétend pas avoir une date.
        assertEquals("", savedCopyAgeLabel(null, Locale.ENGLISH))
        assertEquals(
            "Saved copy on this phone. The server is unreachable for now.",
            savedCopyNotice(null, Locale.ENGLISH),
        )
        assertEquals(
            "Copie enregistrée sur ce téléphone. Le serveur est injoignable pour le moment.",
            savedCopyNotice(null, Locale.FRANCE),
        )
        // **Aucun code d'erreur**, aucune exception, aucune adresse : c'est la demande du
        // propriétaire, et le seul endroit où la phrase est écrite est cette fonction pure.
        val locales = listOf(Locale.ENGLISH, Locale.FRANCE)
        locales.forEach { locale ->
            listOf(null, 0.0, 9 * 3600.0).forEach { age ->
                val notice = savedCopyNotice(age, locale)
                listOf("http", "HTTP", "Exception", "502", "500", "Errno", "127.0.0.1", "stack", "null").forEach { banned ->
                    assertFalse("la ligne d'état contient « $banned » : $notice", notice.contains(banned))
                }
            }
        }
        assertEquals("Try again", SAVED_COPY_RETRY_LABEL)
        // La ligne existe, elle est **conditionnée** par l'échec de la passerelle, et c'est elle qui
        // porte le réessai.
        assertTrue("la ligne d'état n'est pas dessinée", sidebar.contains("SidebarStatusLine("))
        assertTrue("la ligne d'état n'est pas conditionnée", sidebar.contains("if (reachable == false) {"))
        val line = sidebar.substringAfter("private fun SidebarStatusLine(").substringBefore("// ------")
        assertTrue("le réessai n'est plus porté par la ligne d'état", line.contains("SAVED_COPY_RETRY_LABEL"))
        assertTrue("la ligne d'état n'a plus sa bordure fine", line.contains("border(FlatHairlineDp, theme.separator, CircleShape)"))
        // Et la **tête** ne porte plus de second chemin de réessai : un doublon d'action.
        val head = sidebar.substringAfter("private fun SidebarHeader(").substringBefore("private fun SidebarStatusLine(")
        assertFalse("la tête porte encore le rond de réessai", head.contains("Symbols.ArrowClockwise"))
        assertFalse("la tête porte encore un réessai", head.contains("onRetry"))
    }

    @Test
    fun `la phrase de la ligne d etat se mesure, sur tous les themes clairs`() {
        var failures = 0
        val report = StringBuilder()
        for (theme in OnyxTheme.entries) {
            val t = theme(theme, dark = false)
            val pane = sidebarPaneColor(t)
            val well = flatWellColor(t, pane)
            val pill = flatActivePillColor(t)
            val pairs = listOf(
                InkPair("ligne d'état · panneau", mutedInkFor(t, pane), pane, OnyxContrast.TEXT_MIN),
                InkPair("« Try again » · panneau", sidebarInk(t.accent, pane, OnyxContrast.TEXT_MIN), pane, OnyxContrast.TEXT_MIN),
                InkPair("puce au repos · gris des creux", sidebarInk(t.textColor, well, OnyxContrast.TEXT_MIN), well, OnyxContrast.TEXT_MIN),
                InkPair("puce choisie · pastille pleine", flatActivePillInk(t), pill, OnyxContrast.TEXT_MIN),
                InkPair("titre d'une rangée · panneau", t.textColor, pane, OnyxContrast.TEXT_MIN),
                InkPair("âge d'une rangée · panneau", mutedInkFor(t, pane), pane, OnyxContrast.TEXT_MIN),
            )
            for (pair in pairs) {
                val ratio = OnyxContrast.ratioOver(pair.ink, pair.ground)
                val ok = ratio + 1e-4f >= pair.min
                if (!ok) failures++
                report.append(
                    "${theme.id} · ${pair.what} · ${"%.1f".format(pair.min)} · ${"%.2f".format(ratio)} " +
                        "${if (ok) "ok" else "SOUS LE SEUIL"}\n",
                )
            }
        }
        println(report)
        assertTrue(
            "des encres du volet clair passent sous leur seuil :\n" +
                report.lineSequence().filter { it.contains("SOUS LE SEUIL") }.joinToString("\n"),
            failures == 0,
        )
    }

    /** `mutedInk` du volet, recopié ici pour que la mesure du test soit la même que celle du source. */
    private fun mutedInkFor(t: ResolvedTheme, ground: Color): Color =
        sidebarInk(t.textColor, ground, OnyxContrast.TEXT_MIN)

    private data class InkPair(val what: String, val ink: Color, val ground: Color, val min: Float)

    // -----------------------------------------------------------------------------------------
    // 4. Aucune fonction retirée
    // -----------------------------------------------------------------------------------------

    @Test
    fun `aucune fonction du volet n a ete retiree en re-habillant le clair`() {
        val kept = listOf(
            "private fun SidebarHeader(",
            "private fun SidebarSearch(",
            "private fun SidebarSegment(",
            "private fun SidebarTileGrid(",
            "private fun SidebarTileCell(",
            "private fun SidebarProjects(",
            "private fun SidebarFilters(",
            "private fun ConversationList(",
            "private fun ConversationRow(",
            "private fun SwipeableConversationRow(",
            "private fun SidebarSwipeAction(",
            "private fun SelectionMark(",
            "private fun ToneDisc(",
            "private fun SidebarFooter(",
            "private fun SidebarChip(",
            "private fun SidebarStatusLine(",
            "internal fun BadgeCount(",
            "internal fun mutedInk(",
        )
        kept.forEach { assertTrue("$it a disparu du volet", sidebar.contains(it)) }
        // Les tuiles : toutes, avec leur écran. C'est la promesse « rien n'est retiré » — le groupe
        // de la référence (6, Inbox écarté) puis le groupe « Onyx » (4), donc **10** au total (n° 5).
        // Le passage de 11 à 10 est le **retrait du doublon** « Compétences » : « Capacités » porte
        // déjà cette famille, et l'éditeur y a été versé (`CapabilitiesDoublonRetireTest`).
        assertEquals("le nombre de tuiles a changé", 10, SidebarDestinations.tiles.size)
        SidebarDestinations.tiles.forEach { tile ->
            assertTrue(
                "la tuile ${tile.id} n'a plus d'écran porté par le volet",
                SidebarDestinations.paneScreens.contains(tile.id),
            )
        }
        // Les feuilles du bas et les gestes de rangée sont intacts.
        assertTrue("la feuille d'une rangée a disparu", sidebar.contains("ConversationActionSheet("))
        assertTrue("le bandeau d'annulation a disparu", sidebar.contains("ConversationUndoBar("))
        assertTrue("la barre de sélection a disparu", sidebar.contains("SelectionBar("))
        // Le modèle de la ligne d'état est dans le modèle, pas dans l'écran.
        assertTrue("la phrase de la ligne d'état n'est plus dans le modèle", model.contains("internal fun savedCopyNotice("))
    }

    // -----------------------------------------------------------------------------------------
    // 5. Le verre : retiré au clair, intact en sombre
    // -----------------------------------------------------------------------------------------

    @Test
    fun `le verre est retire au clair et intact en sombre`() {
        // Le panneau : le verre **seulement** quand l'apparence est sombre.
        assertTrue(
            "le panneau ne passe plus par le point de décision du verre",
            sidebar.contains(".sidebarPaneSurface()"),
        )
        assertTrue(
            "la tuile ne passe plus par le point de décision du verre",
            sidebar.contains(".sidebarTileSurface(GlassSurface.SIDEBAR_TILE, shape)"),
        )
        assertTrue(
            "la rangée de liste ne passe plus par le point de décision",
            bots.contains(".sidebarRowSurface(shape)"),
        )
        // Le panneau **garde** sa vitre en sombre : la fonction de la table du verre est toujours
        // appelée, avec la même surface qu'avant.
        val flat = read("core/src/main/java/com/onyx/app/ui/design/FlatLight.kt")
        assertTrue("la surface du verre a changé en sombre", flat.contains("this.liquidGlass(GlassSurface.SIDEBAR_PANEL)"))
        assertTrue("la table du verre n'est plus consultée", flat.contains("this.liquidGlass(surface, shape)"))
        assertTrue("la rangée ne consulte plus la table", flat.contains("this.liquidGlass(GlassSurface.LIST_ROW, shape)"))
        // L'ombre du panneau : nulle en sombre, jamais en plus du verre.
        assertTrue("l'ombre du panneau n'est pas conditionnée", flat.contains("if (theme.dark) 0.dp else FlatPanelElevation"))
        // Et le liseré de séparation du panneau ne se dessine plus au clair.
        assertTrue(
            "le liseré du panneau n'est plus conditionné",
            scaffold.contains("if (sidebarPaneElevation(theme) > 0.dp) return@drawBehind"),
        )
        assertTrue("l'ombre du panneau n'est pas posée par la coquille", scaffold.contains("sidebarPaneElevation(theme), drawerShape, clip = false"))
    }

    @Test
    fun `le selecteur plat ne s applique qu au clair`() {
        assertTrue("le volet ne demande plus la piste plate", sidebar.contains("flat = true,"))
        val form = read("core/src/main/java/com/onyx/app/ui/design/OnyxForm.kt")
        assertTrue("le drapeau du sélecteur n'est plus neutralisé en sombre", form.contains("val flatTrack = flat && !theme.dark"))
        assertTrue("la piste plate n'a plus la couleur des creux", form.contains("flatTrackColor(theme)"))
        assertTrue("la pastille n'est plus blanche", form.contains("flatTrackPillColor(theme)"))
    }
}
