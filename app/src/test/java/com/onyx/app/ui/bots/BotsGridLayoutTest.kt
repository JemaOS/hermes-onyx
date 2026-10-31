package com.onyx.app.ui.bots

import com.onyx.app.localization.OnyxLocalization
import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L'écran Bots doit dessiner la section « Bots » de la référence telle qu'elle est **par défaut** :
 * `botGrid` (`ConversationsView`), des vignettes de visage cinq par rangée, leurs marques,
 * puis la tuile qui en fait un nouveau — et non les lignes d'aperçu de `botRow`, que la référence ne
 * montre que pendant « Reorder Bots » (`arrangingBots`).
 */
class BotsGridLayoutTest {
    private val source = File("src/main/java/com/onyx/app/ui/bots/BotsScreen.kt").readText()

    // MARK: - La grille, et non la liste

    @Test
    fun `la grille des bots est celle du botGrid de la référence`() {
        assertTrue("cinq visages à la rangée", source.contains("BOT_GRID_COLUMNS = 5"))
        assertTrue("plus de six bots la replient", source.contains("BOT_GRID_FOLDS_AFTER = 6"))
        assertTrue("un visage de 52 pt", source.contains("BOT_TILE_FACE: Dp = 52.dp"))
        assertTrue("les cases se rangent par cinq", source.contains("cells.chunked(BOT_GRID_COLUMNS)"))
    }

    @Test
    fun `chaque vignette porte le visage du bot et son nom dessous`() {
        val tile = source.substringAfter("private fun BotTile(").substringBefore("/** Ce qui suit les visages")
        assertTrue("le visage passe par BotLook", tile.contains("BotLook("))
        assertTrue("son nom est sous le visage", tile.contains("text = bot.label(mainName)"))
        assertTrue("le nom est une légende", tile.contains("theme.caption"))
    }

    @Test
    fun `les marques du bot sont celles du botTile de la référence`() {
        val tile = source.substringAfter("private fun BotTile(").substringBefore("/** Ce qui suit les visages")
        // Au travail : le rond qui tourne, en haut à droite.
        assertTrue(tile.contains("activity.working ->"))
        assertTrue(tile.contains("TileSpinner()"))
        // En attente de vous : le point d'exclamation orange.
        assertTrue(tile.contains("activity.needsYou ->"))
        assertTrue(tile.contains("symbol = Symbols.ExclamationCircle"))
        assertTrue(tile.contains("tint = theme.warning"))
        // Pas encore lu : la pastille d'accent de 9 pt.
        assertTrue(tile.contains("activity.unread ->"))
        assertTrue(tile.contains(".size(9.dp)"))
        // Tu : la cloche barrée, en bas à droite.
        assertTrue(tile.contains("symbol = mutedMark"))
        assertTrue(tile.contains("contentDescription = \"Muted\""))
        // Ouvert : le fond teinté du listRowBackground et le nom en accent.
        assertTrue(tile.contains("theme.accent.copy(alpha = 0.1f)"))
    }

    @Test
    fun `le repli et la tuile d'action suivent la référence`() {
        val grid = source.substringAfter("private fun BotsGrid(").substringBefore("private fun BotTile(")
        assertTrue("une tuile d'action suit les visages", grid.contains("GridAction("))
        assertTrue("la tuile dit New Bot", grid.contains("\"New Bot\""))
        assertTrue("ou Add Bot sans Dashboard", grid.contains("\"Add Bot\""))
        assertTrue("le chevron de plus", grid.contains("moreLabel("))
        assertTrue("et le mot pour refermer", source.contains("text = \"Fewer\""))
        assertTrue("le symbole du repli", source.contains("symbol = Symbols.ChevronDown"))
    }

    @Test
    fun `les discussions de groupe et la ligne d'action restent sous la grille`() {
        val screen = source.substringAfter("fun BotsScreen(").substringBefore("/** Bots à la rangée")
        assertTrue("les groupes passent par GroupRow", screen.contains("GroupRow("))
        assertTrue("leurs visages s'empilent", source.contains("MemberFaces("))
        assertTrue(screen.contains("title = \"Let bots message each other\""))
        assertTrue(screen.contains("symbol = Symbols.Bubbles"))

        // Group chats et ligne d'action viennent après la grille, comme dans la référence.
        val grid = screen.indexOf("BotsGrid(")
        val groupRow = screen.indexOf("GroupRow(")
        val otherRow = screen.indexOf("title = \"Let bots message each other\"")
        assertTrue("les groupes suivent la grille", groupRow > grid)
        assertTrue("la ligne d'action suit les groupes", otherRow > groupRow)
    }

    @Test
    fun `la creation d'un salon n'a plus qu'une entree, dans l'en-tete`() {
        // **Le verrou déplacé.** Il comptait la rangée « New room » sous la grille (icône `person.3`).
        // Sur ordre explicite du propriétaire, la création n'a plus qu'**une** entrée — l'action de
        // l'en-tête (en accent, en haut à droite) — car l'écran portait « Nouveau salon » **deux
        // fois** : l'en-tête **et** la rangée du bas. Ce test mord si la rangée revient, ou si
        // l'en-tête perd son action.
        val screen = source.substringAfter("fun BotsScreen(").substringBefore("/** Bots à la rangée")
        assertFalse(
            "plus de rangée « New room » sous la grille",
            screen.contains("ActionRow(title = \"New room\""),
        )
        assertFalse(
            "plus d'icône de grille (person.3) portant la création",
            screen.contains("symbol = Symbols.Person3"),
        )
        assertTrue(
            "l'en-tête est le seul porteur de la création",
            screen.contains("action = if (arranging) \"Done\" else \"New room\""),
        )
    }

    @Test
    fun `la ligne d'aperçu d'une conversation ne dessine plus les bots`() {
        // L'écran des bots ne portait que des lignes « visage + profil + modèle + chevron ».
        assertFalse("plus de ligne d'éditeur dans l'écran des bots", source.contains("BotRow("))
        assertFalse("plus de nom de modèle sous le visage", source.contains("bot.model?.let"))
        assertFalse("plus de chevron de ligne", source.contains("symbol = Symbols.ChevronRight"))
    }

    // MARK: - L'en-tête de section (foldingHeader)

    @Test
    fun `l'en-tete de section est le foldingHeader de la référence`() {
        assertTrue(source.contains("SectionLabel("))
        assertTrue(
            "le compte n'apparait que repliee",
            source.contains("count = if (open && !arranging) null else state.bots.size"),
        )
        assertTrue(
            "le chevron suit l'etat",
            source.contains("symbol = if (open) Symbols.ChevronDown else Symbols.ChevronRight"),
        )
        assertTrue("repliee, la section cache son contenu", source.contains("if (!open) return@Column"))
        assertEquals("Masquer les bots", OnyxLocalization.translate("Hide bots", Locale.FRENCH))
        assertEquals("Afficher les bots", OnyxLocalization.translate("Show bots", Locale.FRENCH))
    }

    // MARK: - Les cases de la grille (fonctions pures)

    @Test
    fun `sous six bots la grille montre tous les visages puis New Bot`() {
        assertEquals(listOf(BotGridCell.Face, BotGridCell.Face, BotGridCell.NewBot), botGridCells(2, expanded = false, hasDashboard = true))
        assertEquals(
            listOf(BotGridCell.Face, BotGridCell.Face, BotGridCell.AddBot),
            botGridCells(2, expanded = false, hasDashboard = false),
        )
    }

    @Test
    fun `au dela de six bots la grille se replie sur quatre visages et N more`() {
        val folded = botGridCells(7, expanded = false, hasDashboard = true)
        assertEquals(5, folded.size)
        assertEquals(4, folded.count { it == BotGridCell.Face })
        assertEquals(BotGridCell.More, folded.last())
        assertTrue(botGridFolds(7))
        assertFalse(botGridFolds(6))
        assertEquals(4, botGridShown(7, expanded = false))
        assertEquals(7, botGridShown(7, expanded = true))
    }

    @Test
    fun `depliee la grille montre tous les visages puis New Bot`() {
        val opened = botGridCells(7, expanded = true, hasDashboard = true)
        assertEquals(8, opened.size)
        assertEquals(7, opened.count { it == BotGridCell.Face })
        assertEquals(BotGridCell.NewBot, opened.last())
    }

    @Test
    fun `la tuile de repli dit combien de bots elle cache`() {
        assertEquals("3 more", moreLabel(3))
        assertEquals("1 more", moreLabel(1))
    }

    // MARK: - La langue

    @Test
    fun `les libelles de la grille sont dits en francais`() {
        assertEquals("Nouveau bot", OnyxLocalization.translate("New Bot", Locale.FRENCH))
        assertEquals("Ajouter un bot", OnyxLocalization.translate("Add Bot", Locale.FRENCH))
        assertEquals("Nouveau salon", OnyxLocalization.translate("New Group Chat", Locale.FRENCH))
        assertEquals("Action requise", OnyxLocalization.translate("Needs you", Locale.FRENCH))
        assertEquals("Réduire", OnyxLocalization.translate("Fewer", Locale.FRENCH))
        assertEquals("3 de plus", OnyxLocalization.translate("3 more", Locale.FRENCH))
        // En anglais, la tuile garde son mot.
        assertEquals("3 more", OnyxLocalization.translate("3 more", Locale.ENGLISH))
    }

    @Test
    fun `la phrase de l'etat vide sans Dashboard est traduite`() {
        val key = "Bots are your server's other profiles. On the Hermes API, add each by its name and its own API key; with the Dashboard login saved in Settings, Onyx lists them and has group chats too."
        val french = OnyxLocalization.translate(key, Locale.FRENCH)
        assertFalse("la phrase reste anglaise", french == key)
        assertTrue(french.contains("profils"))
    }
}
