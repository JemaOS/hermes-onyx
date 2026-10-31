package com.onyx.app.ui.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * **Le modèle en vigueur** : conversation → bot → serveur → défaut, l'alias de passerelle écarté.
 *
 * La seule valeur que lisent l'affichage **et** l'envoi (`PiluleModeleEnvoiVerrouTest` tient le fait
 * qu'ils ne peuvent pas diverger). Chaque étage a son cas, un étage vide passe la main, et un alias
 * n'est jamais un modèle.
 */
class ModelInForceTest {

    @Test
    fun `la conversation prime sur le bot et le serveur`() {
        assertEquals(
            "le modèle de la conversation l'emporte",
            "conversation-modele",
            ModelInForce.inForce("conversation-modele", "bot-modele", "serveur-modele"),
        )
    }

    @Test
    fun `le bot prime sur le serveur, et sert toutes ses conversations`() {
        assertEquals(
            "sans modèle de conversation, le bot décide",
            "bot-modele",
            ModelInForce.inForce(null, "bot-modele", "serveur-modele"),
        )
        assertEquals(
            "le même bot donne le même modèle à chacune de ses conversations",
            ModelInForce.inForce(null, "bot-modele", ""),
            ModelInForce.inForce(null, "bot-modele", ""),
        )
    }

    @Test
    fun `le serveur sert quand rien de plus precis n est pose`() {
        assertEquals("serveur-modele", ModelInForce.inForce(null, null, "serveur-modele"))
    }

    @Test
    fun `le defaut est le silence, jamais une valeur inventee`() {
        assertNull("rien posé : le défaut", ModelInForce.inForce(null, null, null))
        assertNull("des blancs ne comptent pas", ModelInForce.inForce("", "  ", ""))
    }

    @Test
    fun `un etage blanc passe la main au suivant`() {
        assertEquals("bot-modele", ModelInForce.inForce("", "bot-modele", "serveur-modele"))
        assertEquals("serveur-modele", ModelInForce.inForce(null, "  ", "serveur-modele"))
    }

    @Test
    fun `un alias de passerelle n est jamais un modele en vigueur`() {
        // `hermes-agent` / `default` désignent le tuyau, pas un modèle : ils passent la main, et s'il
        // n'y a rien derrière, le résultat est `null` (« Modèle par défaut ») — donc rien n'est envoyé.
        assertNull(ModelInForce.inForce(null, null, "hermes-agent"))
        assertNull(ModelInForce.inForce(" HERMES-AGENT ", "default", "gateway"))
        assertNull(ModelInForce.inForce(null, null, ""))
    }

    @Test
    fun `un vrai nom derriere l alias reprend la main`() {
        assertEquals(
            "le modèle de la conversation passe devant l'alias du serveur",
            "claude-sonnet-4.5",
            ModelInForce.inForce("claude-sonnet-4.5", null, "hermes-agent"),
        )
        assertEquals(
            "un vrai nom configuré sur le serveur s'emploie",
            "gpt-5-codex",
            ModelInForce.inForce(null, null, "gpt-5-codex"),
        )
    }
}
