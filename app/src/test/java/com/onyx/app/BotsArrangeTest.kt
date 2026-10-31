package com.onyx.app

import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.bots.ARRANGE_ROW_HEIGHT
import com.onyx.app.ui.bots.arrangeTarget
import com.onyx.app.ui.bots.botGridCells
import com.onyx.app.ui.bots.BotGridCell
import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * « Reorder Bots » et la tuile « Hidden » de la section Bots, comme `ConversationsView`
 * (`arrangingBots`, `onMove`, `gridAction("Hidden", systemImage: "eye.slash")`) et comme
 * `HiddenBotsView` (Echo/Views/BotEditor:575).
 */
class BotsArrangeTest {
    private val botsScreen = File("src/main/java/com/onyx/app/ui/bots/BotsScreen.kt").readText()
    private val arrange = File("src/main/java/com/onyx/app/ui/bots/BotsArrange.kt").readText()
    private val hidden = File("src/main/java/com/onyx/app/ui/bots/HiddenBotsScreen.kt").readText()
    private val viewModel = File("src/main/java/com/onyx/app/ui/bots/BotsViewModel.kt").readText()
    private val app = File("src/main/java/com/onyx/app/ui/OnyxApp.kt").readText()

    // MARK: - Le geste

    @Test
    fun `un glissement deplace la ligne d autant de rangs qu il descend`() {
        val row = 58f
        assertEquals(2, arrangeTarget(from = 0, dy = 2 * row + 4f, rowHeight = row, count = 5))
        assertEquals(0, arrangeTarget(from = 2, dy = -2 * row - 4f, rowHeight = row, count = 5))
        assertEquals("un petit glissement ne change rien", 1, arrangeTarget(from = 1, dy = row / 3f, rowHeight = row, count = 5))
        assertEquals("la ligne ne sort pas de la section", 4, arrangeTarget(from = 4, dy = 10 * row, rowHeight = row, count = 5))
        assertEquals(0, arrangeTarget(from = 1, dy = -10 * row, rowHeight = row, count = 5))
        assertTrue("la hauteur d'une ligne est celle de la référence", ARRANGE_ROW_HEIGHT.value > 0f)
    }

    // MARK: - L'écran

    @Test
    fun `l ecran des bots a son mode reordonnancement`() {
        assertTrue("le mode existe", botsScreen.contains("var arranging by remember { mutableStateOf(false) }"))
        assertTrue("la section seule s'affiche", botsScreen.contains("BotsArrangeList("))
        assertTrue("la barre dit New room", botsScreen.contains("action = if (arranging) \"Done\" else \"New room\""))
        assertTrue("le geste appartient au ViewModel", botsScreen.contains("onMove = viewModel::move"))
        assertTrue("l'ordre se remet au plus récent", botsScreen.contains("viewModel::sortByRecent"))
        // Une ligne qu'on met en ordre ne s'ouvre pas : elle ne porte que sa poignée.
        assertTrue("la poignée de la référence", arrange.contains("Symbols.Line3Horizontal"))
        assertTrue("le pied de section de la référence", arrange.contains("Drag bots and group chats by their handles into the order you want."))
    }

    @Test
    fun `l ordre et les lignes cachees sont gardes par connexion`() {
        assertTrue(viewModel.contains("activity.order(serverKey)"))
        assertTrue(viewModel.contains("activity.setOrder(serverKey, order)"))
        assertTrue(viewModel.contains("BotListItem.arranged("))
        assertTrue(viewModel.contains("BotListItem.orderAfter("))
        // Une ligne vue pour la première fois compte comme lue, comme `ChatActivity.listed`.
        assertTrue(viewModel.contains("activity.listed(serverKey"))
    }

    // MARK: - La tuile « Hidden »

    @Test
    fun `la tuile Hidden suit les bots caches`() {
        // **Changement assumé par le lot « états manquants »** (audit des 66 routes, §4) : la tuile
        // ne suit plus le seul compte des bots cachés mais la **disponibilité de l'entrée**. Avec un
        // Dashboard, elle est là même sans bot caché — l'écran dit alors qu'il n'y a rien à
        // réafficher. Sans entrée fournie, elle n'est pas dessinée.
        assertEquals(
            listOf(BotGridCell.Face, BotGridCell.Face, BotGridCell.NewBot, BotGridCell.Hidden),
            botGridCells(2, expanded = false, hasDashboard = true, hidden = 0, hiddenEntry = true),
        )
        assertEquals(
            listOf(BotGridCell.Face, BotGridCell.Face, BotGridCell.NewBot, BotGridCell.Hidden),
            botGridCells(2, expanded = false, hasDashboard = true, hidden = 3, hiddenEntry = true),
        )
        assertEquals(
            listOf(BotGridCell.Face, BotGridCell.Face, BotGridCell.NewBot),
            botGridCells(2, expanded = false, hasDashboard = true, hidden = 3, hiddenEntry = false),
        )
        // Repliée, la grille dit « N more » et la tuile des cachés attend qu'on la déplie.
        assertEquals(
            listOf(BotGridCell.Face, BotGridCell.Face, BotGridCell.Face, BotGridCell.Face, BotGridCell.More),
            botGridCells(9, expanded = false, hasDashboard = true, hidden = 3, hiddenEntry = true),
        )
        assertTrue(botsScreen.contains("symbol = Symbols.EyeSlash"))
        assertTrue(botsScreen.contains("hiddenCount = state.hiddenBots.size"))
        assertTrue("l'entrée est fournie au site d'appel", botsScreen.contains("hiddenEntry = onOpenHiddenBots != null,"))
        assertTrue("la tuile des cachés compte ce qu'elle cache", botsScreen.contains("hiddenCount = state.hiddenBots.size"))
    }

    @Test
    fun `la liste des bots caches les remontre, comme HiddenBotsView`() {
        assertTrue("son titre", hidden.contains("OnyxPageHeader(title = \"Hidden Bots\""))
        assertTrue("le bouton qui remet le bot dans la liste", hidden.contains("text = \"Show\""))
        assertTrue("le pied de section de la référence", hidden.contains("Hidden here and in Hermes Desktop alike."))
        assertTrue("le bot reparaît", viewModel.contains("fun showBot(bot: BotProfile)"))
        assertTrue(
            "et le serveur l'apprend, comme `changeBot(hidden: false)` de la référence",
            viewModel.contains("writeHidden(bot, hidden = false)"),
        )
        assertTrue("masquer dans l'éditeur le masque aussi sur le serveur", viewModel.contains("writeHidden(bot, hidden = !shown)"))
        assertTrue("l'écriture dit la révision lue", viewModel.contains("setHidden(bot.name, hidden = hidden, revision = bot.setUpRevision)"))
        assertTrue("sa route existe", File("src/main/java/com/onyx/app/ui/OnyxApp.kt").readText().contains("Route.HIDDEN_BOTS -> HiddenBotsScreen("))
    }

    @Test
    fun `la section montre les salles du serveur avec leurs pastilles`() {
        assertTrue("les salles viennent de l'état du ViewModel", app.contains("groups = botsState.groups"))
        assertTrue("elles portent leurs pastilles", botsScreen.contains("badges.forRoom("))
        assertTrue("la salle ouverte est vue", app.contains("onRoomSeen = { room -> botsViewModel.markRoomSeen(room) }"))
        assertTrue("le ViewModel lit `groups.list`", viewModel.contains("ServeGroupsRpc(rpc)") && viewModel.contains("groups.rooms()"))
        assertTrue("et la copie d'Hermes Desktop", viewModel.contains("groups.desktopRooms()"))
        assertTrue("et ses capacités", viewModel.contains("groups.support()"))
        assertTrue("l'activité vient de `session.active_list`", viewModel.contains("ServeActivityRpc(rpc).activeKinds()"))
    }

    // MARK: - La langue

    @Test
    fun `les nouveaux libelles sont dits en francais`() {
        assertEquals("Réordonner les bots", OnyxLocalization.translate("Reorder Bots", Locale.FRENCH))
        assertEquals("Trier les bots par récent", OnyxLocalization.translate("Sort Bots by Recent", Locale.FRENCH))
        assertEquals("Bots masqués", OnyxLocalization.translate("Hidden Bots", Locale.FRENCH))
        assertEquals("Masqué", OnyxLocalization.translate("Hidden", Locale.FRENCH))
        assertEquals("Afficher", OnyxLocalization.translate("Show", Locale.FRENCH))
        assertEquals("S’exécute dans Hermes Desktop", OnyxLocalization.translate("Runs in Hermes Desktop", Locale.FRENCH))
        val drag = OnyxLocalization.translate("Drag bots and group chats by their handles into the order you want.", Locale.FRENCH)
        assertFalse("la phrase du geste reste anglaise", drag == "Drag bots and group chats by their handles into the order you want.")
        assertTrue(drag.contains("poignée"))
        // En anglais, les libellés restent ceux de la référence.
        assertEquals("Reorder Bots", OnyxLocalization.translate("Reorder Bots", Locale.ENGLISH))
        assertEquals("Hidden Bots", OnyxLocalization.translate("Hidden Bots", Locale.ENGLISH))
    }
}
