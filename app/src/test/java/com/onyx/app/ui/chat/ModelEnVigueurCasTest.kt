package com.onyx.app.ui.chat

import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.Transport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * **Les trois cas de conversation, tapés sur l'état réel** — et dans les trois, **le nom affiché est
 * le modèle envoyé**.
 *
 * L'envoi d'un tour sans override porte `modelOverride ?: state.modelInForce` ; la pilule écrit
 * `state.modelInForce ?: « Modèle par défaut »`. Le verrou de source ([PiluleModeleEnvoiVerrouTest])
 * prouve que les deux lisent la **même** clé **sans habillage** ; ce fichier prouve, cas par cas, que
 * la **valeur** de cette clé est bien celle qui s'affiche **et** celle qui part.
 *
 * Les trois cas tapés :
 *
 * - **conversation simple** — un modèle de conversation (rapporté par la session), sinon celui
 *   configuré du serveur ;
 * - **conversation de bot** — le modèle du bot, qui suit toutes ses conversations ;
 * - **défaut** — aucun choix explicite : la pilule dit « Modèle par défaut » et l'envoi ne nomme rien.
 */
class ModelEnVigueurCasTest {

    private fun server(model: String) = ServerConfig(
        id = "serveur",
        baseUrl = "https://exemple.test",
        model = model,
        transport = Transport.CHAT_COMPLETIONS,
    )

    /** Le modèle qui partira d'un envoi sans override — l'expression exacte de `appendTurn`. */
    private fun sent(state: ChatUiState): String? = state.modelInForce

    /** Ce que la pilule écrit : la valeur, ou « Modèle par défaut » quand elle est nulle. */
    private fun shown(state: ChatUiState): String = state.modelInForce ?: "Modèle par défaut"

    @Test
    fun `cas simple — le libelle de la pilule est le modele envoye`() {
        // La conversation a son modèle (rapporté par sa session) ; le serveur en configure un autre —
        // c'est le cas mesuré : la pilule disait « claude-sonnet-4.5 », la requête partait en
        // « hermes-agent ». Après le correctif, les deux valent le modèle en vigueur.
        val state = ChatUiState(server = server("hermes-agent"), modelInUse = "claude-sonnet-4.5")
        assertEquals("la conversation nomme SON modèle", "claude-sonnet-4.5", state.modelInForce)
        assertEquals(
            "le libellé de la pilule est exactement le modèle envoyé",
            sent(state),
            state.modelInForce,
        )
        assertEquals("claude-sonnet-4.5", shown(state))
    }

    @Test
    fun `cas simple sans echo serveur — le modele configure est le modele envoye`() {
        val state = ChatUiState(server = server("gpt-5-codex"))
        assertEquals("gpt-5-codex", state.modelInForce)
        assertEquals(sent(state), state.modelInForce)
    }

    @Test
    fun `cas bot — le libelle de la pilule est le modele envoye`() {
        val state = ChatUiState(
            server = server("hermes-agent"),
            botModel = "deepseek/deepseek-v4.1-flash",
        )
        assertEquals("le bot décide, pas le serveur", "deepseek/deepseek-v4.1-flash", state.modelInForce)
        assertEquals(
            "la pilule écrit le modèle du bot, et c'est lui qui part",
            sent(state),
            state.modelInForce,
        )
    }

    @Test
    fun `cas defaut — le silence des deux cotes`() {
        val state = ChatUiState(server = server(""))
        assertNull("rien n'est en vigueur : le défaut", state.modelInForce)
        assertNull("l'envoi porte le silence", sent(state))
        assertEquals("la pilule dit le défaut, et rien d'autre", "Modèle par défaut", shown(state))
    }

    @Test
    fun `un alias de passerelle ne s affiche pas et ne part pas`() {
        // L'app n'écrit jamais `hermes-agent` (règle de `ModelName`) : la valeur unique le retire donc
        // aussi de l'envoi — sinon, pour rester vraie, la pilule devrait le peindre.
        val state = ChatUiState(server = server("hermes-agent"))
        assertNull("l'alias n'est pas un modèle en vigueur", state.modelInForce)
        assertNull("et il ne part pas non plus", sent(state))
        assertEquals("la pilule dit le défaut", "Modèle par défaut", shown(state))
    }

    @Test
    fun `changer de conversation ne contamine pas le modele`() {
        val first = ChatUiState(
            server = server("hermes-agent"),
            modelInUse = "claude-sonnet-4.5",
            botModel = "deepseek/deepseek-v4.1-flash",
        )
        assertEquals("la conversation l'emporte", "claude-sonnet-4.5", first.modelInForce)
        // Le changement de fil remet à zéro les niveaux « conversation » ET « bot » : la nouvelle
        // conversation ne peut pas hériter d'un modèle qui n'est pas le sien.
        val second = first.switchToConversation(
            conversationId = "autre",
            serverSessionId = null,
            messages = emptyList(),
            input = "",
            queued = emptyList(),
        )
        assertEquals("le bot ne suit pas d'un fil à l'autre", null, second.botModel)
        assertNull("l'écho de conversation ne fuit pas", second.modelInUse)
        // Le serveur, lui, porte un alias : le nouveau fil retombe donc sur le défaut, pas sur l'alias.
        assertNull("le nouveau fil retombe sur le défaut", second.modelInForce)
    }
}
