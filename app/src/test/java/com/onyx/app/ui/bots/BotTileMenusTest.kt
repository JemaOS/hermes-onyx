package com.onyx.app.ui.bots

import com.onyx.app.data.model.Conversation
import com.onyx.app.data.model.serve.BotProfile
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.conversations.conversationUnread
import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le tap d'une tuile de bot ouvre son **chat** (`openBot`), l'appui long son **menu** — les
 * entrées du `botMenu` et du `contextMenu` du `groupRow`,
 * dans leur ordre, chacune câblée à une action. Et « Mark as Unread », d'une salle comme d'une
 * conversation, est réellement atteignable et agit sur ce qui s'affiche.
 */
class BotTileMenusTest {
    private val root = File("src/main/java/com/onyx/app/ui")
    private val botsScreen = File(root, "bots/BotsScreen.kt").readText()
    private val menus = File(root, "bots/BotMenus.kt").readText()
    private val botChatFile = File(root, "bots/BotChat.kt").readText()
    private val viewModel = File(root, "bots/BotsViewModel.kt").readText()
    private val conversations = File(root, "conversations/ConversationsScreen.kt").readText()
    private val app = File(root, "OnyxApp.kt").readText()

    // MARK: - Le tap: le chat, jamais l'éditeur

    @Test
    fun `le tap d une tuile ouvre le chat du bot`() {
        assertTrue("la tuile a son geste", botsScreen.contains("onClick = { onOpenBot(bot) }"))
        assertTrue("le tap mène au chat", botsScreen.contains("onOpenBot = onOpenBotChat"))
        assertTrue("dans la liste des conversations aussi", conversations.contains("onOpenBotChat(bot)"))
        assertTrue("le rappel part bien vers le chat", app.contains("onOpenBotChat = openBotChat"))
        // L'éditeur n'est plus ce que le tap ouvre: c'est « Edit Bot… » du menu.
        assertFalse("plus de tuile vers l'éditeur", app.contains("onOpenBot = { bot ->"))
        assertTrue("l'éditeur est une entrée du menu", app.contains("onEditBot = { bot ->"))

        // Le chat ouvert est celui du bot: la conversation que son `canonical_session` désigne.
        val his = Conversation(id = "c1", createdAt = 0, updatedAt = 0, serverSessionId = "bot-chat")
        val other = Conversation(id = "c2", createdAt = 0, updatedAt = 0, serverSessionId = "other")
        val bot = BotProfile(name = "kilo", chatId = "bot-chat")
        assertEquals(his, botChat(bot, listOf(other, his)))
        assertNull("un autre chat n'est pas le sien", botChat(bot, listOf(other)))
    }

    @Test
    fun `le chat d un bot sans chat nomme ouvre une conversation liee`() {
        val bot = BotProfile(name = "kilo", title = "Kilo", chatId = "bot-chat")
        // Rien de gardé: la conversation est faite, liée à son chat serveur, et titrée de son nom
        // — celui que la conversation d'un bot porte chez , pas « Bot Chat ».
        val made = botChatToOpen(bot, conversations = emptyList(), serverId = "srv", mainName = "Onyx", now = 42L)
        assertEquals("bot-chat", made.serverSessionId)
        assertEquals("srv", made.serverId)
        assertEquals("Kilo", made.title)
        assertTrue(made.messages.isEmpty())
        // Gardée, c'est elle qu'on rouvre — pas une deuxième.
        assertEquals(made, botChatToOpen(bot, listOf(made), serverId = "srv", mainName = "Onyx", now = 99L))
        // Un bot sans `canonical_session` ouvre une conversation neuve, sans session serveur.
        val nameless = botChatToOpen(BotProfile(name = "api", title = "API"), emptyList(), mainName = "Onyx")
        assertNull(nameless.serverSessionId)
        assertEquals("API", nameless.title)
        assertTrue("le chemin est celui  : `openBot` puis `botChat`", botChatFile.contains("fun botChatToOpen("))
    }

    // MARK: - Le menu d'une tuile, dans l'ordre 

    @Test
    fun `le menu d une tuile a les entrees du botMenu dans son ordre`() {
        val bot = BotProfile(name = "kilo", chatId = "bot-chat")
        assertEquals(
            "marqué lu ou non lu, la voix, le silence, les horaires, puis l'édition et l'ordre",
            listOf(
                BotMenuAction.MarkUnread,
                BotMenuAction.Voice,
                BotMenuAction.Mute,
                BotMenuAction.Schedules,
                BotMenuAction.EditBot,
                BotMenuAction.ChangeFace,
                BotMenuAction.Hide,
                BotMenuAction.Reorder,
            ),
            botMenuActions(bot, isOpen = false, unread = false, hasOrder = false),
        )
        // Ce qui a été vu: l'entrée dit « Mark as Read ».
        assertEquals(BotMenuAction.MarkRead, botMenuActions(bot, isOpen = false, unread = true, hasOrder = false).first())
        // Le chat du bot est celui qu'on regarde, ou le roster ne lui en nomme pas: rien à marquer.
        assertFalse(
            botMenuActions(bot, isOpen = true, unread = true, hasOrder = false).contains(BotMenuAction.MarkRead),
        )
        assertFalse(
            botMenuActions(BotProfile(name = "api"), isOpen = false, unread = true, hasOrder = false)
                .any { it == BotMenuAction.MarkRead || it == BotMenuAction.MarkUnread },
        )
        // Le profil principal n'a ni voix, ni retrait, ni masquage (comme `!bot.isDefault`).
        val main = BotProfile(name = "default", isDefault = true, chatId = "main-chat")
        val mainMenu = botMenuActions(main, isOpen = false, unread = false, hasOrder = true)
        assertFalse(mainMenu.contains(BotMenuAction.Voice))
        assertFalse(mainMenu.contains(BotMenuAction.Hide))
        assertTrue(mainMenu.contains(BotMenuAction.EditBot))
        // Un ordre mis à la main: « Sort Bots by Recent » revient fermer la marche.
        assertEquals(BotMenuAction.SortByRecent, mainMenu.last())
    }

    @Test
    fun `un bot ajoute a la main se retire de Onyx au lieu de s editer`() {
        val api = BotProfile(name = "api-bot", viaAPI = true)
        val menu = botMenuActions(api, isOpen = false, unread = false, hasOrder = false)
        assertEquals(
            // La voix reste (`!bot.isDefault`), puis le silence, le retrait, et l'ordre: sur un
            // bot de l'API, « Edit Bot… », « Change Face… » et « Hide » sont ceux du Dashboard.
            listOf(BotMenuAction.Voice, BotMenuAction.Mute, BotMenuAction.RemoveFromOnyx, BotMenuAction.Reorder),
            menu,
        )
        assertFalse(menu.contains(BotMenuAction.EditBot))
        assertFalse(menu.contains(BotMenuAction.Schedules))
        assertEquals("Remove from Onyx", botMenuLabel(BotMenuAction.RemoveFromOnyx))
        assertTrue("la ligne est destructrice", menus.contains("destructive = action == BotMenuAction.RemoveFromOnyx"))
        assertTrue("et elle retire le bot pour de bon", viewModel.contains("faces.forget(bot.name)"))
    }

    @Test
    fun `les libelles du menu sont ceux `() {
        assertEquals("Mark as Read", botMenuLabel(BotMenuAction.MarkRead))
        assertEquals("Mark as Unread", botMenuLabel(BotMenuAction.MarkUnread))
        assertEquals("Voice…", botMenuLabel(BotMenuAction.Voice))
        assertEquals("Schedules…", botMenuLabel(BotMenuAction.Schedules))
        assertEquals("Edit Bot…", botMenuLabel(BotMenuAction.EditBot))
        assertEquals("Change Face…", botMenuLabel(BotMenuAction.ChangeFace))
        assertEquals("Hide", botMenuLabel(BotMenuAction.Hide))
        assertEquals("Reorder Bots", botMenuLabel(BotMenuAction.Reorder))
        assertEquals("Sort Bots by Recent", botMenuLabel(BotMenuAction.SortByRecent))
    }

    @Test
    fun `chaque entree ouvre sur une action`() {
        // Le `when` du menu est exhaustif: une action sans effet ne compilerait pas.
        assertTrue(menus.contains("BotMenuAction.MarkRead -> onMarkRead"))
        assertTrue(menus.contains("BotMenuAction.Hide -> onHide"))
        assertTrue("le panneau est celui ", menus.contains("OnyxMenu("))
        assertTrue("chaque entrée ferme le menu en agissant", menus.contains("onDismiss()\n                it()"))
        // La voix d'un bot: l'entrée ouvre sa feuille (`BotVoicePicker`) depuis les deux écrans.
        assertTrue(menus.contains("BotMenuAction.Voice -> onVoice"))
        assertTrue("la tuile des Réglages la branche", botsScreen.contains("onVoice = onVoice?.let { open -> { open(bot) } }"))
        assertTrue("celle de la liste aussi", conversations.contains("onVoice = onBotVoice?.let { open -> { open(bot) } }"))

        // Une entrée sans geste ne s'affiche pas du tout: le menu qui s'ouvre agit toujours.
        val shown = menuItems(
            actions = listOf(BotMenuAction.MarkUnread, BotMenuAction.Voice, BotMenuAction.EditBot, BotMenuAction.Hide),
            muteLines = { emptyList() },
            onDismiss = {},
            onMarkUnread = {},
            onEdit = {},
        )
        assertEquals(
            "la voix et le masquage n'ont pas de geste ici : ils ne sont pas montrés",
            listOf("Mark as Unread", "Edit Bot…"),
            shown.map { it.label },
        )
    }
    @Test
    fun `les deux ecrans portent l appui long et le menu`() {
        assertTrue("la tuile des Réglages", botsScreen.contains(".onyxTappable(onLongPress = onLongPress, onClick = onClick)"))
        assertTrue("celle de la liste", conversations.contains(".onyxTappable(onLongPress = onLongPress, onClick = onClick)"))
        assertTrue(botsScreen.contains("onLongPress = { onOpenMenu(bot) }"))
        assertTrue(conversations.contains("onLongPress = { menuBot = bot }"))
        assertTrue(botsScreen.contains("BotTileMenu("))
        assertTrue(conversations.contains("BotTileMenu("))
        // « Reorder Bots » et « Sort Bots by Recent » ne sont plus des lignes de la section.
        assertFalse(
            "plus de ligne de section dans les Réglages",
            botsScreen.contains("ActionRow(title = \"Reorder Bots\""),
        )
        assertFalse(
            "ni dans la liste",
            conversations.contains("ActionRow(title = \"Sort Bots by Recent\""),
        )
        assertTrue("le menu seul les porte", menus.contains("BotMenuAction.Reorder -> onReorder"))
    }

    // MARK: - Mark as Unread: une salle, une conversation

    @Test
    fun `une salle se marque non lue depuis son menu`() {
        // Comme le `contextMenu` du `groupRow`: marquée lue ou non lue quand elle n'est pas
        // ouverte et que le serveur lui connaît une activité, le silence sinon.
        assertEquals(
            listOf(BotMenuAction.MarkUnread, BotMenuAction.Mute, BotMenuAction.Reorder),
            roomMenuActions(canMark = true, unread = false, inDesktop = false, hasOrder = false),
        )
        assertEquals(
            BotMenuAction.MarkRead,
            roomMenuActions(canMark = true, unread = true, inDesktop = false, hasOrder = false).first(),
        )
        // Une salle d'Hermes Desktop ne notifie pas: rien à taire.
        assertFalse(
            roomMenuActions(canMark = false, unread = false, inDesktop = true, hasOrder = true)
                .contains(BotMenuAction.Mute),
        )
        assertTrue("la rangée des Réglages l'ouvre", botsScreen.contains("onMarkUnread = { viewModel.markRoomUnread(room) }"))
        assertTrue("celle de la liste aussi", conversations.contains("onMarkUnread = onMarkRoomUnread?.let { unreadMark -> { unreadMark(room) } },"))
        assertTrue("elle est câblée à l'écriture", app.contains("onMarkRoomUnread = { room -> botsViewModel.markRoomUnread(room) }"))
        assertTrue("le ViewModel la garde par connexion", viewModel.contains("activity.markUnread(serverKey, \"room:\" + room.id)"))
        assertTrue("et la tuile d'un bot aussi", viewModel.contains("fun markBotUnread(bot: BotProfile)"))
    }

    @Test
    fun `une conversation se marque non lue et la rangee le montre`() {
        assertTrue("l'entrée est en tête du menu d'une rangée", conversations.contains("\"Mark as Read\" else \"Mark as Unread\""))
        assertTrue("avec les symboles ", conversations.contains("Symbols.CheckmarkMessage else Symbols.MessageBadge"))
        assertTrue("elle n'est pas proposée pour la conversation ouverte", conversations.contains("onSetUnread?.takeIf { conversation.id != currentId }"))
        assertTrue("l'écriture passe par l'écran", app.contains("onSetUnread = setConversationUnread"))
        assertTrue("qui marque non lue", app.contains("conversationMarks.markUnread(state.server.configSignature, key)"))
        assertTrue("et relit la marque tout de suite", app.contains("unreadMarks = unreadMarks + (conversation.id to"))
        assertTrue("la rangée porte la marque", conversations.contains("mark = unreadMarks[conversation.id]"))

        // La règle du non-lu, comme `ChatActivity.unread`: marquée (0) elle s'allume, lue elle se
        // rallume à une réponse venue après, et la conversation ouverte n'est jamais non lue.
        assertTrue(conversationUnread(lastSpokenAt = 1_000L, seen = 0.0, isOpen = false, busy = false, muted = false, fallback = false))
        assertFalse(conversationUnread(lastSpokenAt = 1_000L, seen = 2.0, isOpen = false, busy = false, muted = false, fallback = true))
        assertTrue("une réponse après la lecture", conversationUnread(lastSpokenAt = 9_000L, seen = 2.0, isOpen = false, busy = false, muted = false, fallback = false))
        assertFalse("celle qu'on regarde", conversationUnread(lastSpokenAt = 9_000L, seen = 0.0, isOpen = true, busy = false, muted = false, fallback = true))
        assertFalse("celle où un tour s'écrit", conversationUnread(lastSpokenAt = 9_000L, seen = 0.0, isOpen = false, busy = true, muted = false, fallback = true))
        assertFalse("une conversation tue", conversationUnread(lastSpokenAt = 9_000L, seen = 0.0, isOpen = false, busy = false, muted = true, fallback = true))
        assertTrue("sans marque, c'est le fil qui parle", conversationUnread(lastSpokenAt = null, seen = null, isOpen = false, busy = false, muted = false, fallback = true))
    }

    // MARK: - Le mode « Reorder Bots » tient

    @Test
    fun `reorder bots passe par le menu et glisse toujours par sa poignee`() {
        assertTrue("le menu ouvre le mode", botsScreen.contains("onReorder = { arranging = true }"))
        assertTrue("dans la liste aussi", conversations.contains("onReorder = onArrange,"))
        assertTrue("le tri par récent est dans le menu", botsScreen.contains("onSortByRecent = viewModel::sortByRecent"))
        assertTrue("et il remonte au ViewModel", app.contains("onSortBotsByRecent = { botsViewModel.sortByRecent() }"))
        // Le mode lui-même: les lignes à poignées, le geste rendu au ViewModel.
        assertTrue(botsScreen.contains("BotsArrangeList("))
        assertTrue(botsScreen.contains("onMove = viewModel::move"))
        assertTrue(app.contains("onMoveRow = { from, to -> botsViewModel.move(from, to) }"))
        assertTrue("« Hide » est écrit là où la liste le relit", viewModel.contains("fun hideBot(bot: BotProfile)"))
    }

    // MARK: - La langue

    @Test
    fun `les libelles du menu sont dits en francais`() {
        assertEquals("Marquer comme lu", OnyxLocalization.translate("Mark as Read", Locale.FRENCH))
        assertEquals("Marquer comme non lu", OnyxLocalization.translate("Mark as Unread", Locale.FRENCH))
        assertEquals("Voix…", OnyxLocalization.translate("Voice…", Locale.FRENCH))
        assertEquals("Horaires…", OnyxLocalization.translate("Schedules…", Locale.FRENCH))
        assertEquals("Modifier le bot…", OnyxLocalization.translate("Edit Bot…", Locale.FRENCH))
        assertEquals("Changer de visage…", OnyxLocalization.translate("Change Face…", Locale.FRENCH))
        assertEquals("Retirer de Onyx", OnyxLocalization.translate("Remove from Onyx", Locale.FRENCH))
        // En anglais, les libellés restent ceux.
        assertEquals("Mark as Unread", OnyxLocalization.translate("Mark as Unread", Locale.ENGLISH))
        assertEquals("Edit Bot…", OnyxLocalization.translate("Edit Bot…", Locale.ENGLISH))
    }
}
