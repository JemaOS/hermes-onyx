package com.onyx.app.ui.sidebar

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le montage du mode édition, dans le source** — les six règles du propriétaire, une par test.
 *
 * Les règles de **calcul** sont tenues par `SidebarTileLayoutTest` ; ce fichier tient celles qui ne
 * se mesurent que dans le **montage** : qu'un appui ne retire pas par accident, que le rond inactif
 * dise pourquoi, que « Terminé » soit **à la place du crayon** et dans l'accent, que la grille ne se
 * réordonne **pas** pendant le glissement, que l'appui long d'une tuile soit **tranché** une fois, et
 * que l'état vide de la bibliothèque ait une phrase. Plus la persistance (clé dédiée) et les
 * traductions.
 */
class SidebarTileEditorWiringTest {

    private val sidebar: String = read("src/main/java/com/onyx/app/ui/sidebar/ChatSidebar.kt")
    private val host: String = read("src/main/java/com/onyx/app/ui/sidebar/ChatSidebarHost.kt")
    private val app: String = read("src/main/java/com/onyx/app/ui/OnyxApp.kt")
    private val repo: String = read("src/main/java/com/onyx/app/data/repo/SettingsRepository.kt")
    private val keys: String = read("src/main/java/com/onyx/app/data/local/OnyxDataStore.kt")
    private val french: String =
        read("../core/src/main/java/com/onyx/app/localization/OnyxFrenchSidebarTiles.kt")

    /** Le source de l'arbre, lu depuis le module `app` (le dossier de travail des tests unitaires). */
    private fun read(path: String): String =
        listOf(File(path), File("../$path")).firstOrNull { it.isFile }?.readText() ?: ""

    /** La section des tuiles du volet : du titre de section à celle des projets. */
    private val tiles: String = sidebar
        .substringAfter("// 4. Les tuiles")
        .substringBefore("// 5. Les projets")

    // -----------------------------------------------------------------------------------------
    // Règle 1 — aucun retrait accidentel
    // -----------------------------------------------------------------------------------------

    @Test
    fun `le rond de retrait exige un appui franc et jamais un glissement`() {
        assertTrue("le rond écoute l'appui sur lui-même", tiles.contains("detectTapGestures("))
        assertTrue(
            "l'appui doit aller jusqu'au bout sur le rond (un frôlement est annulé)",
            tiles.contains("tryAwaitRelease()"),
        )
        assertFalse(
            "aucun retrait ne doit être branché sur les gestes de glissement des rangées",
            tiles.contains("onSwipe") || tiles.contains("SwipeableConversationRow"),
        )
        // Le geste de réordonnancement et le rond sont deux zones distinctes : le rond a son
        // `pointerInput` à lui, dans la bande du haut.
        assertTrue(tiles.contains("TileCellStrip("))
        assertTrue(
            "la bande du rond est au-dessus du contenu (jamais sur le libellé)",
            tiles.contains("TileBadgeStrip") && tiles.contains("Alignment.CenterEnd"),
        )
    }

    @Test
    fun `le retrait s annule d un geste discret au bas de la grille`() {
        // On retire vite, on rattrape vite : un « Annuler » sous la grille, sans confirmation.
        assertTrue(tiles.contains("SidebarTileUndoMillis"))
        assertTrue(tiles.contains("\"Shortcut removed\""))
        assertTrue(tiles.contains("OnyxLocalization[\"Undo\"]"))
        assertTrue(
            "l'annulation repose la grille d'avant le retrait",
            tiles.contains("undo?.let(setOrder)"),
        )
        // **Et la raison passe avant le rattrapage** : quand le rond vient d'être touché alors que la
        // grille est au plancher, c'est la réponse à ce geste-là qu'on attend — pas le souvenir du
        // retrait précédent. Les deux ne s'écrivent jamais ensemble.
        assertTrue(
            "la raison du refus est écrite là où le rattrapage s'écrirait",
            tiles.contains("text = refusal ?: OnyxLocalization[\"Shortcut removed\"]"),
        )
        assertTrue(
            "toucher un rond inactif remplace le rattrapage en cours",
            tiles.contains("undo = null\n                                refusal = removalReason"),
        )
        // Aucune alerte de confirmation sur le retrait d'une tuile.
        assertFalse(
            "retirer une tuile ne demande pas de confirmation",
            tiles.contains("OnyxAlert("),
        )
    }

    // -----------------------------------------------------------------------------------------
    // Règle 2 — au plancher, le rond est inactif et dit pourquoi
    // -----------------------------------------------------------------------------------------

    @Test
    fun `au plancher le rond devient inactif et affiche la raison`() {
        assertTrue(tiles.contains("removable = removalRefusal == null"))
        assertTrue("le rond inactif n'exécute pas le retrait", tiles.contains("onRefused"))
        assertTrue(
            "la raison est écrite dans le nom accessible du rond",
            tiles.contains("removeReason.orEmpty()"),
        )
        // La raison vient des bornes, avec leur nombre : « Une grille garde au moins 4 tuiles. »
        assertTrue(
            "la raison est remplie avec le plancher du code",
            tiles.contains("SidebarTileLayout.MIN_TILES"),
        )
    }

    // -----------------------------------------------------------------------------------------
    // Règle 3 — « Terminé » à la place du crayon, dans l'accent
    // -----------------------------------------------------------------------------------------

    @Test
    fun `on sort de l edition a la meme place que le crayon et dans l accent`() {
        val titre = sidebar.substringAfter("private fun SidebarTileTitleRow(")
            .substringBefore("private fun SidebarTileSmallButton(")
        assertTrue("le crayon est le chemin d'entrée", titre.contains("symbol = Symbols.Pencil"))
        assertTrue("« Terminé » est le chemin de sortie", titre.contains("OnyxLocalization[\"Done\"]"))
        assertTrue("« Réinitialiser » est là", titre.contains("OnyxLocalization[\"Reset\"]"))
        // Les deux sont dans la **même** rangée, au bout de la ligne : une seule rangée, trois états.
        assertTrue(titre.contains("SidebarTileSmallButton("))
        assertTrue(
            "« Terminé » est plein de l'accent",
            sidebar.substringAfter("private fun SidebarTileSmallButton(")
                .substringBefore("@Composable\nprivate fun SidebarTileLibrary")
                .contains("OnyxContrast.filled(theme.accent)"),
        )
        // Le crayon **est** l'action de section de la maison : un seul composant, une seule taille
        // (44 dp), la même que le « + » de BOTS et celui de SALONS.
        assertTrue(
            "le crayon passe par SectionActionButton",
            titre.contains("SectionActionButton(") && titre.contains("symbol = Symbols.Pencil"),
        )
        assertTrue(
            "« Terminé » a la même cible que l'action de section",
            sidebar.substringAfter("private fun SidebarTileSmallButton(")
                .contains("defaultMinSize(minHeight = SectionAction.Size)"),
        )
        // **Sortir laisse l'écran dans son état normal** : la sortie ne touche qu'à l'état d'édition.
        assertTrue(
            "la sortie ne fait que refermer l'édition",
            host.contains("onExit = { editingTiles = false }"),
        )
    }

    // -----------------------------------------------------------------------------------------
    // Règle 4 — le glissement : ancre stable, une seule cible
    // -----------------------------------------------------------------------------------------

    @Test
    fun `pendant le glissement la grille ne se reordonne pas et les voisines ne bougent pas`() {
        val geste = sidebar.substringAfter("private fun Modifier.reorderGesture(")
            .substringBefore("/**\n * **La ligne de titre de la section des tuiles**")
        assertFalse(
            "aucun réordonnancement ne part pendant le geste : il n'a lieu qu'au lâcher",
            geste.contains("setOrder("),
        )
        assertTrue("la tuile prise est décalée par une translation", tiles.contains(".offset {"))
        assertTrue("elle passe au-dessus des autres", tiles.contains(".zIndex("))
        assertTrue(
            "le décalage est borné aux rangées de la grille",
            tiles.contains("fun clampDy("),
        )
        assertTrue(
            "une seule cible : la tuile la plus proche",
            tiles.contains("fun targetFor(") && tiles.contains("minByOrNull"),
        )
        // L'axe est celui de la maison : la même classification que les gestes de rangée.
        assertTrue(
            "l'axe du geste est tranché par la règle de la maison",
            geste.contains("sidebarSwipeAxis("),
        )
        // **Un lâcher qui ne déplace rien n'écrit rien.** Sans cette garde, un appui long qui ne
        // bouge pas — et un appui **posé** sur un rond de retrait, que la même détection termine en
        // « lâcher sans déplacement » — rendait la liste **d'avant** le geste, et le retrait se
        // défaisait tout seul (mesuré sur l'appareil : le réglage revenait aux six tuiles).
        assertTrue(
            "le lâcher sans déplacement ne réécrit pas la grille",
            tiles.contains("if (to != from) {"),
        )
    }

    // -----------------------------------------------------------------------------------------
    // Règle 5 — l'appui long d'une tuile : tranché, et dit
    // -----------------------------------------------------------------------------------------

    @Test
    fun `l appui long d une tuile entre en edition et rien d autre`() {
        assertTrue(
            "la décision est écrite dans le source",
            tiles.contains("L'appui long d'une tuile : tranché, et dit."),
        )
        assertTrue("l'appui long entre en édition", tiles.contains("onLongPress = { gridEditor.onEnter() }"))
        assertFalse(
            "une tuile n'ouvre pas de feuille : l'appui long des rangées est un autre objet",
            tiles.contains("onMenuRow"),
        )
    }

    // -----------------------------------------------------------------------------------------
    // Règle 6 — l'état vide de la bibliothèque a une phrase
    // -----------------------------------------------------------------------------------------

    @Test
    fun `la bibliotheque vide a une phrase et non un blanc`() {
        assertTrue(tiles.contains("SidebarTileLibrary("))
        assertTrue(tiles.contains("OnyxLocalization[\"Everything is already on the grid.\"]"))
        assertTrue(tiles.contains("library.isEmpty()"))
        assertTrue("l'intitulé est en petites capitales", tiles.contains("GalerieSectionLabel("))
        assertTrue(
            "la bibliothèque n'existe qu'en édition",
            tiles.contains("if (editing) {"),
        )
    }

    // -----------------------------------------------------------------------------------------
    // La persistance — clé dédiée, défaut = les six
    // -----------------------------------------------------------------------------------------

    @Test
    fun `la grille est enregistree sous sa propre cle et relue au demarrage`() {
        assertTrue(
            "une clé dédiée, et son nom",
            keys.contains("val SIDEBAR_TILES = stringPreferencesKey(\"sidebar_tiles\")"),
        )
        assertTrue("le dépôt la relit", repo.contains("parse(preferences[SettingsKeys.SIDEBAR_TILES])"))
        assertTrue("le dépôt l'écrit", repo.contains("it[SettingsKeys.SIDEBAR_TILES] = "))
        assertTrue(
            "la valeur par défaut est la grille d'origine",
            repo.contains("MutableStateFlow(com.onyx.app.ui.sidebar.SidebarTileLayout.defaultIds)"),
        )
        assertTrue(
            "l'app branche la grille sur le réglage",
            app.contains("settingsRepo.sidebarTiles.collectAsStateWithLifecycle()"),
        )
        assertTrue(
            "chaque geste écrit la grille entière",
            app.contains("onSetTileLayout = { ids -> scope.launch { settingsRepo.setSidebarTiles(ids) } }"),
        )
        assertTrue("« Réinitialiser » repose les six", tiles.contains("SidebarTileLayout.defaultIds"))
        assertEquals("la grille d'origine fait six tuiles", 6, SidebarTileLayout.defaultIds.size)
    }

    @Test
    fun `la grille dessinee est celle du reglage et la bibliotheque le reste du catalogue`() {
        assertTrue(app.contains("SidebarTileLayout.resolve(tileIds)"))
        assertTrue(app.contains("SidebarTileLayout.library(tileIds)"))
        assertTrue(app.contains("tiles = shownTiles,"))
        assertTrue(app.contains("tileLibrary = tileLibrary,"))
        // Le volet reçoit les deux, et le mode édition.
        assertTrue(host.contains("tileLibrary: List<SidebarTile> = emptyList(),"))
        assertTrue(host.contains("onSetTileLayout: ((List<String>) -> Unit)? = null,"))
        assertTrue(host.contains("tileEditor = tileEditor,"))
    }

    // -----------------------------------------------------------------------------------------
    // Les mots — tout est dit en français
    // -----------------------------------------------------------------------------------------

    @Test
    fun `toutes les phrases du mode edition sont traduites`() {
        listOf(
            "Shortcuts", "Edit shortcuts", "Remove %1\$s", "Shortcut removed", "Reset", "Library",
            "Add %1\$s", "Everything is already on the grid.", "A grid keeps at least %d tiles.",
            "A grid holds %d tiles at most.", "This screen has no shortcut.",
            "This shortcut is already on the grid.",
        ).forEach { key ->
            val escaped = key.replace("$", "\\$")
            assertTrue(
                "la phrase « $key » n'a pas de français",
                french.contains("\"$escaped\" to "),
            )
        }
        assertFalse("le fichier de traduction est bien là", french.isEmpty())
    }
}
