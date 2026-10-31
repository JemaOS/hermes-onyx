package com.onyx.app

import com.onyx.app.data.model.ChatMessage
import com.onyx.app.data.model.Conversation
import com.onyx.app.data.model.Role
import com.onyx.app.data.model.serve.BotProfile
import com.onyx.app.data.net.ActivityKind
import com.onyx.app.ui.bots.botActivity
import com.onyx.app.ui.bots.botKind
import com.onyx.app.ui.conversations.rowTime
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les marques d'une tuile de bot : l'activité se lit sur **le chat du bot** — la session que le
 * serveur dit vivante (`session.active_list`) —, et le non-lu sur ce que ce téléphone a vu de ce
 * chat, jamais sur une conversation qui n'a rien à voir.
 */
class ConversationsBotTilesTest {
    @Test
    fun `les badges suivent le chat du bot et pas un autre`() {
        val bot = BotProfile(name = "kilo", chatId = "bot-chat")

        // Le serveur travaille dans le chat de ce bot : lui seul l'apprend.
        assertEquals(ActivityKind.WORKING, botKind(bot, mapOf("bot-chat" to ActivityKind.WORKING), openStreaming = false, nowMillis = 0))
        assertNull(botKind(bot, mapOf("other-chat" to ActivityKind.WORKING), openStreaming = false, nowMillis = 0))

        // Le serveur attend une réponse dans son chat : « Needs you ».
        val waiting = botActivity(
            bot = bot,
            isOpen = false,
            seen = 0.0,
            kind = botKind(bot, mapOf("bot-chat" to ActivityKind.NEEDS_YOU), false, 0),
            muted = false,
        )
        assertTrue(waiting.needsYou)
        assertFalse("elle attend quelqu'un : rien de nouveau à lire", waiting.unread)
    }

    @Test
    fun `un bot travaille quand sa derniere reponse s ecrit encore`() {
        val bot = BotProfile(name = "kilo", chatId = "bot-chat")
        val streaming = conversation(id = "local", serverSessionId = "bot-chat", role = Role.ASSISTANT, streaming = true)

        // Le chat du bot est ouvert ici et sa réponse s'écrit encore : c'est le seul cas local.
        assertTrue(openStreaming(listOf(streaming), bot.chatId.orEmpty()))
        assertFalse(openStreaming(listOf(streaming), "other-chat"))
        assertNull("sans chat ouvert ni session vivante, rien", botKind(bot, emptyMap(), openStreaming = false, nowMillis = 0))
    }

    @Test
    fun `un bot sans chat n a aucune activite`() {
        val activity = botActivity(BotProfile(name = "kilo"), isOpen = false, seen = 0.0, kind = null, muted = false)

        assertFalse(activity.working)
        assertFalse(activity.needsYou)
        assertFalse(activity.unread)
    }

    @Test
    fun `l heure du jour est une heure courte comme DateGroup rowTime`() {
        val now = System.currentTimeMillis()
        val expected = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault()).format(Date(now))
        assertEquals(expected, rowTime(now))
    }

    private fun openStreaming(chats: List<Conversation>, chatId: String): Boolean =
        chats.any { it.serverSessionId == chatId && it.messages.any { message -> message.isStreaming } }

    private fun conversation(
        id: String,
        serverSessionId: String,
        text: String = "Message",
        updatedAt: Long = 1000L,
        role: Role = Role.ASSISTANT,
        streaming: Boolean = false,
    ) = Conversation(
        id = id,
        createdAt = 0L,
        updatedAt = updatedAt,
        serverSessionId = serverSessionId,
        messages = listOf(
            ChatMessage(
                id = "$id-message",
                role = role,
                text = text,
                createdAt = updatedAt,
                isStreaming = streaming,
            ),
        ),
    )
}
