package com.onyx.app.ui.conversations

import com.onyx.app.data.model.ChatMessage
import com.onyx.app.data.model.Conversation
import com.onyx.app.data.model.Role
import com.onyx.app.data.model.serve.BotProfile
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.bots.roomMembersLabel
import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La section Bots des conversations: des **lignes**, comme le `botRow` — le point du
 * non-lu dans la marge, le visage, le nom en gras, l'heure de son dernier mot, et le dernier mot de
 * son chat sous lui —, les salles du serveur (`groupRow`), puis les trois lignes qui font agir.
 * La grille de vignettes (`botGrid`) reste à l'écran de gestion des bots (`BotsScreen`), pas ici.
 */
class ConversationsBotListLayoutTest {
    private val source = File("src/main/java/com/onyx/app/ui/conversations/ConversationsScreen.kt").readText()
    private val section = source.substringAfter("private fun BotsSection(").substringBefore("private fun BotRowSeparator()")
    private val row = source.substringAfter("private fun BotRow(").substringBefore("/**\n * « Needs you »")
    private val group = source.substringAfter("private fun GroupRow(").substringBefore("/** « 1 member »")

    @Test
    fun `la section des bots est une liste de lignes`() {
        assertTrue("la section dessine les lignes des bots", section.contains("BotRow("))
        assertTrue(source.contains("private fun BotRow("))
        assertTrue(source.contains("private fun RowUnreadDot("))
        // Plus de grille de vignettes dans la liste des conversations.
        assertFalse(source.contains("BotGrid("))
        assertFalse(source.contains("private fun BotTile("))
        assertFalse(source.contains("private fun GridActionTile("))
        assertFalse(source.contains("GRID_COLUMNS"))
        assertFalse(source.contains("BotGridCell"))
    }

    @Test
    fun `la ligne porte le point du non lu, le visage, le nom en gras et l heure`() {
        // Le point (`unreadDot`), dans la marge: muet, il n'y en a pas.
        assertTrue(row.contains("RowUnreadDot(shown = activity.unread && !muted)"))
        // Le visage de 40 points, jamais une initiale — et son humeur dit ce qu'il fait.
        assertTrue(row.contains("com.onyx.app.ui.design.BotLook("))
        assertTrue(row.contains("size = BOT_ROW_FACE,"))
        assertTrue(row.contains("activity.working -> BotMood.THINKING"))
        assertTrue(source.contains("private val BOT_ROW_FACE = 40.dp"))
        // Le nom en gras, et l'heure à droite (ou le rond qui tourne).
        assertTrue(row.contains("fontWeight = FontWeight.SemiBold"))
        assertTrue(row.contains("bot.label(mainName)"))
        assertTrue(row.contains("BasicText(text = rowTime(it)"))
        assertTrue(row.contains("if (activity.working) {"))
        assertTrue(row.contains("RowSpinner()"))
        // Le chat ouvert ici: le fond teinté du `listRowBackground`.
        assertTrue(row.contains("if (isOpen) theme.accent.copy(alpha = 0.08f) else theme.pageColor"))
        // Le tap ouvre le chat du bot, l'appui long son menu: rien de perdu.
        assertTrue(section.contains("onClick = { onOpenBotChat(bot) }"))
        assertTrue(section.contains("onLongPress = { menuBot = bot }"))
        assertTrue(section.contains("BotTileMenu("))
        assertTrue(section.contains("badges.forBot("))
        assertTrue(section.contains("BotsArrangeList("))
    }

    @Test
    fun `la ligne dit Needs you devant le dernier mot du chat`() {
        assertTrue(row.contains("if (activity.needsYou) NeedsYouTag()"))
        assertTrue(source.contains("private fun NeedsYouTag()"))
        // Le tag: le texte orange sur sa capsule d'orange.
        val tag = source.substringAfter("private fun NeedsYouTag()").substringBefore("private fun botRowPreview")
        assertTrue(tag.contains("theme.caption2.copy(color = theme.warning"))
        assertTrue(tag.contains("theme.warning.copy(alpha = 0.15f)"))
        assertTrue(tag.contains("text = \"Needs you\""))
    }

    @Test
    fun `le dernier mot vient du chat du bot, jamais d une description inventee`() {
        // L'aperçu du serveur passe par `BotMessages.rowPreview`, la description ne venant qu'à
        // défaut — et la poignée après elle, comme `botRow`.
        assertTrue(row.contains("botRowPreview(lastWord, roster, mainName)"))
        assertTrue(row.contains("bot.description.takeIf { it.isNotBlank() }"))
        assertTrue(row.contains("\"@\" + bot.handle"))
        assertTrue(source.contains("internal fun botRowPreview("))
        // La langue par défaut du poste n'entre pas dans ces vérifications.
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            assertEquals(
                "un dernier mot venu d'un autre bot se dit avec son nom",
                "Scout: three trails under two hours",
                botRowPreview("Message from 🤖 Scout (@scout): three trails under two hours", roster(), "Onyx"),
            )
            assertEquals(
                "la forme ancienne aussi",
                "Scout: on it",
                botRowPreview("[Message from agent 'scout'] on it", roster(), "Onyx"),
            )
            assertEquals(
                "un mot ordinaire reste tel quel",
                "Done: the reminder is set for 4 pm.",
                botRowPreview("Done: the reminder is set for 4 pm.", roster(), "Onyx"),
            )
            assertEquals(
                "une tâche de fond se dit pour ce qu'elle est",
                "A background task finished",
                botRowPreview("[IMPORTANT: Background process proc_1 exited (exit code 0).", roster(), "Onyx"),
            )
        } finally {
            Locale.setDefault(previous)
        }
        assertNull("rien à dire, rien", botRowPreview("   ", roster(), "Onyx"))
        assertNull(botRowPreview(null, roster(), "Onyx"))
        // La description du bot n'est jamais présentée comme son dernier mot.
        assertFalse(row.contains("botRowPreview(bot.description"))
    }

    @Test
    fun `le dernier mot et l heure viennent du chat du bot, sinon du chat d ici`() {
        val bot = BotProfile(name = "scout", chatId = "bot-chat")
        val his = Conversation(
            id = "c1",
            createdAt = 0L,
            updatedAt = 999L,
            serverSessionId = "bot-chat",
            messages = listOf(
                ChatMessage(id = "m1", role = Role.ASSISTANT, text = "Three trails under two hours", createdAt = 999L),
            ),
        )
        // Le serveur donne le dernier mot de son chat et son heure: c'est ce qu'on montre.
        val served = bot.copy(preview = "Done: the reminder is set for 4 pm.", lastActive = 111L)
        assertEquals("Done: the reminder is set for 4 pm.", botRowLastWord(served, listOf(his)))
        assertEquals(111L, botRowLastActive(served, listOf(his)))
        // Sans mot du serveur: celui du chat que ce téléphone connaît, et son heure.
        assertEquals("Three trails under two hours", botRowLastWord(bot, listOf(his)))
        assertEquals(999L, botRowLastActive(bot, listOf(his)))
        // Ni l'un ni l'autre: rien à dire, et aucune heure à montrer.
        assertNull(botRowLastWord(bot, emptyList()))
        assertNull(botRowLastActive(bot, emptyList()))
        // Le chat d'un autre n'est pas le sien.
        assertNull(botRowLastWord(bot, listOf(his.copy(serverSessionId = "other"))))
        // La ligne s'en sert: la sienne, et non celle du modèle du roster.
        assertTrue(section.contains("lastWord = botRowLastWord(bot, conversations)"))
        assertTrue(section.contains("lastActive = botRowLastActive(bot, conversations)"))
    }

    @Test
    fun `les salles empilent leurs visages et nomment qui en fait partie`() {
        assertTrue(section.contains("GroupRow("))
        assertTrue(group.contains("MemberFaces(members = group.members)"))
        val stack = source.substringAfter("private fun MemberFaces(").substringBefore("/**\n * Une rangée de salle")
        assertTrue("deux visages, comme `prefix(2)`", stack.contains("members.take(2)"))
        assertTrue("posés sur le fond de la page", stack.contains("background(theme.pageColor, CircleShape)"))
        assertTrue(group.contains("roomMembersLabel(group.members.map { it.label() })"))
        assertTrue("la salle d'Hermes Desktop se dit à sa ligne", group.contains("Symbols.DesktopComputer"))
        assertTrue("le fond teinté de la salle ouverte", group.contains("theme.accent.copy(alpha = 0.08f)"))
        // « Onyx, Scout, and Quill »: la formulation , `formatted(.list(type: .and))`.
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            assertEquals("Onyx, Scout, and Quill", roomMembersLabel(listOf("Onyx", "Scout", "Quill")))
            assertEquals("Fix-it and Scout", roomMembersLabel(listOf("Fix-it", "Scout")))
            Locale.setDefault(Locale.FRANCE)
            assertEquals("Onyx, Scout et Quill", roomMembersLabel(listOf("Onyx", "Scout", "Quill")))
        } finally {
            Locale.setDefault(previous)
        }
        assertEquals("Onyx", roomMembersLabel(listOf("Onyx")))
    }

    @Test
    fun `les trois actions suivent les lignes avec les icones `() {
        assertTrue(section.contains("ActionRow(title = \"New Group Chat\""))
        assertTrue(section.contains("symbol = Symbols.Person3"))
        assertTrue(section.contains("title = if (hasDashboard) \"New Bot\" else \"Add Bot\""))
        assertTrue(section.contains("symbol = Symbols.PlusBubble"))
        assertTrue(section.contains("ActionRow(title = \"Let bots message each other\""))
        assertTrue(section.contains("symbol = Symbols.Bubbles"))
        // L'ordre: New Group Chat, New Bot, puis laisser les bots se parler.
        val groupChat = section.indexOf("ActionRow(title = \"New Group Chat\"")
        val newBot = section.indexOf("title = if (hasDashboard) \"New Bot\" else \"Add Bot\"")
        val letThem = section.indexOf("Let bots message each other")
        assertTrue("New Bot suit New Group Chat", newBot > groupChat)
        assertTrue("et Let bots vient en dernier", letThem > newBot)
        // La tuile « Hidden » de la grille reste atteignable, en ligne, quand des bots sont cachés.
        assertTrue(section.contains("symbol = Symbols.EyeSlash"))
        assertTrue(section.contains("hint = hiddenHint(hiddenCount)"))
        assertTrue(section.contains("if (hiddenCount > 0 && onOpenHidden != null)"))
        assertTrue(source.contains("private fun ActionRow("))
    }

    @Test
    fun `chaque ligne est separee par le filet rentre jusqu au texte`() {
        assertTrue("le filet suit chaque ligne", section.contains("BotRowSeparator()"))
        assertTrue(source.contains(".padding(start = BOT_ROW_TEXT, end = 20.dp)"))
        assertTrue(source.contains("private val BOT_ROW_TEXT = BOT_ROW_START + BOT_ROW_DOT + BOT_ROW_GAP + BOT_ROW_FACE + BOT_ROW_GAP"))
        // Le symbole d'une action tient la place du visage: les titres tombent où les noms tombent.
        val action = source.substringAfter("private fun ActionRow(").substringBefore("/** Une conversation : titre")
        assertTrue(action.contains("Spacer(modifier = Modifier.size(width = BOT_ROW_DOT, height = BOT_ROW_FACE))"))
        assertTrue(action.contains("Modifier.size(BOT_ROW_FACE)"))
    }

    @Test
    fun `les libelles de la section sont dits en francais`() {
        assertEquals("Nouvelle discussion de groupe", OnyxLocalization.translate("New Group Chat", Locale.FRENCH))
        assertEquals("Nouveau bot", OnyxLocalization.translate("New Bot", Locale.FRENCH))
        assertEquals("Ajouter un bot", OnyxLocalization.translate("Add Bot", Locale.FRENCH))
        assertEquals(
            "Laisser les bots discuter entre eux",
            OnyxLocalization.translate("Let bots message each other", Locale.FRENCH),
        )
        assertEquals("Action requise", OnyxLocalization.translate("Needs you", Locale.FRENCH))
        assertEquals("Masqué", OnyxLocalization.translate("Hidden", Locale.FRENCH))
        assertFalse(
            "la tâche de fond se dit aussi en français",
            OnyxLocalization.translate("A background task finished", Locale.FRENCH) == "A background task finished",
        )
        // En anglais, les libellés restent ceux.
        assertEquals("New Group Chat", OnyxLocalization.translate("New Group Chat", Locale.ENGLISH))
        assertEquals("Needs you", OnyxLocalization.translate("Needs you", Locale.ENGLISH))
    }

    /** Le roster des captures: Onyx (le profil principal), Scout, Quill, Fix-it. */
    private fun roster(): List<BotProfile> = listOf(
        BotProfile(name = "default", displayName = "Onyx", isDefault = true),
        BotProfile(name = "scout", title = "Scout"),
        BotProfile(name = "writer", displayName = "Quill"),
        BotProfile(name = "fixit", title = "Fix-it"),
    )
}
