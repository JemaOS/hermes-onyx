package com.onyx.app

import com.onyx.app.data.model.Role
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.Transport
import com.onyx.app.data.model.serve.BotProfile
import com.onyx.app.ui.bots.GroupMessage
import com.onyx.app.ui.bots.groupBotConfig
import com.onyx.app.ui.bots.groupHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class GroupChatFlowTest {
    private val connection = ServerConfig(
        id = "server",
        baseUrl = "http://10.0.2.2:8650",
        model = "hermes-agent",
        transport = Transport.CHAT_COMPLETIONS,
    )

    @Test
    fun `un endpoint non scope garde l alias de routage de la connexion`() {
        val main = BotProfile(name = "default", isDefault = true, model = "sage-a1.5")

        val resolved = groupBotConfig(connection, main)

        assertEquals("hermes-agent", resolved.model)
        assertEquals("", resolved.hermesProfile)
        assertEquals(Transport.CHAT_COMPLETIONS, resolved.transport)
    }

    @Test
    fun `un gateway scope applique le profil et le modele du bot`() {
        val gateway = connection.copy(transport = Transport.HERMES_SESSIONS)
        val kilo = BotProfile(name = "kilo", model = "moonshotai/kimi-k3.1-pro")

        val resolved = groupBotConfig(gateway, kilo)

        assertEquals("kilo", resolved.hermesProfile)
        assertEquals("moonshotai/kimi-k3.1-pro", resolved.model)
        assertEquals(Transport.HERMES_SESSIONS, resolved.transport)
    }

    @Test
    fun `la question reste derniere et les erreurs hors historique`() {
        val onyx = BotProfile(name = "default", title = "Onyx", isDefault = true)
        val kilo = BotProfile(name = "kilo", title = "kilo")
        val messages = listOf(
            GroupMessage(id = "old-q", bot = null, text = "Ancienne question"),
            GroupMessage(id = "old-a", bot = onyx, text = "Ancienne réponse"),
            GroupMessage(id = "q", bot = null, text = "Question courante"),
            GroupMessage(id = "failed", bot = onyx, text = "timeout", failed = true),
            GroupMessage(id = "other", bot = onyx, text = "Réponse de Onyx"),
            GroupMessage(id = "draft", bot = kilo, text = "", isStreaming = true),
        )

        val history = groupHistory(messages, kilo)

        assertEquals("Question courante", history.last().text)
        assertEquals(Role.USER, history.last().role)
        assertFalse(history.any { it.text.contains("timeout") })
        assertEquals(
            listOf("Ancienne question", "Onyx: Ancienne réponse", "Onyx: Réponse de Onyx", "Question courante"),
            history.map { it.text },
        )
    }
}
