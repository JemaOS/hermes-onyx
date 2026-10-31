package com.onyx.app

import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.Transport
import com.onyx.app.ui.chat.ChatUiState
import com.onyx.app.ui.design.ProfileInForce
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * **Verrou de la liaison bot → profil, sur l'état réel du chat.**
 *
 * Il échoue si le profil **affiché** par l'en-tête ([ChatUiState.profileInForce]) diffère du profil
 * que l'envoi porte au tour (`ProfileInForce.appliedTo(state.server, state.botProfile)`). Les deux
 * valeurs lisent la même décision : ce qui est montré est ce qui sert.
 *
 * La morsure (source d'avant) : « coder : attendu coder, obtenu default ».
 */
class BotProfileBindingEtatVerrouTest {

    private fun etat(bot: String?) = ChatUiState(
        server = ServerConfig(
            id = "default",
            name = "Onyx",
            baseUrl = "http://10.0.2.2:8890",
            serveBaseUrl = "http://10.0.2.2:8890",
            transport = Transport.HERMES_SERVE,
        ),
        botProfile = bot,
    )

    private fun profilDeSession(bot: String?): String =
        ProfileInForce.appliedTo(etat(bot).server, etat(bot).botProfile).effectiveProfile
            ?: ProfileInForce.Default

    @Test
    fun `le profil affiche est le profil de la session`() {
        for (bot in listOf("coder", "kilo", "kilo-2", "qa-tester", "scout", "writer", "")) {
            val affiche = etat(bot).profileInForce
            val session = profilDeSession(bot)
            assertEquals("$bot : affiché $affiche, en force $session", session, affiche)
        }
    }

    @Test
    fun `la conversation de coder affiche et sert coder`() {
        val state = etat("coder")
        assertEquals("coder", state.profileInForce)
        assertEquals("coder", profilDeSession("coder"))
    }

    @Test
    fun `une conversation simple affiche et sert default quand le serveur est sur default`() {
        val state = etat(null)
        assertEquals("default", state.profileInForce)
        assertEquals("default", profilDeSession(null))
    }

    @Test
    fun `le profil du bot ne fuit pas d une conversation a l autre`() {
        // Le changement de conversation remet le profil à zéro (`switchToConversation`) : une
        // conversation qui suit une conversation de bot n'hérite pas de son profil.
        val suivante = etat("coder").copy(botProfile = null)
        assertEquals("default", suivante.profileInForce)
    }
}
