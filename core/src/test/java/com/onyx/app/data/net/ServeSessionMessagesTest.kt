package com.onyx.app.data.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * **Le bot ne s'arrête plus quand l'app se ferme — et l'app le relit.**
 *
 * Le correctif a deux moitiés : le client annonce `X-Hermes-Keep-Running` (le serveur finit le run
 * sans personne à l'écoute), et au retour sur la conversation la bulle se remplit avec ce que
 * l'hôte a écrit. Ce sont les règles de cette seconde moitié qui se vérifient ici, sans réseau ni
 * Android : quelles lignes de la session comptent comme « la réponse », et quand une réponse
 * relue remplace celle qu'on avait sous les yeux.
 */
class ServeSessionMessagesTest {

    private fun row(role: String, text: String) = ServeSessionMessages.Row(role = role, text = text)

    // — 1 · la réponse se lit APRÈS le dernier mot de l'utilisateur, et jamais avant —

    @Test
    fun `la reponse relue est celle qui suit le dernier message de l utilisateur`() {
        val rows = listOf(
            row("user", "première question"),
            row("assistant", "première réponse"),
            row("user", "deuxième question"),
            row("assistant", "deuxième réponse"),
        )
        assertEquals("deuxième réponse", ServeSessionMessages.trailingReply(rows))
    }

    @Test
    fun `les lignes de service ne se lisent jamais comme une reponse`() {
        // La forme réelle de la route : le dernier assistant est suivi d'une ligne `session_meta`,
        // et les appels d'outil sont des lignes `tool` à contenu JSON — aucune n'est la réponse.
        val rows = listOf(
            row("user", "lance ceci"),
            row("assistant", ""),
            row("tool", "{\"output\": \"\", \"exit_code\": 0}"),
            row("assistant", "TERMINE"),
            row("session_meta", ""),
        )
        assertEquals("TERMINE", ServeSessionMessages.trailingReply(rows))
    }

    @Test
    fun `un tour encore en cours ne se relit pas`() {
        // Le dernier message est celui de l'utilisateur : l'hôte n'a rien écrit encore. On rend
        // `null` — surtout pas la réponse du tour précédent, qui n'a rien à voir.
        val rows = listOf(
            row("user", "ancienne question"),
            row("assistant", "ancienne réponse"),
            row("user", "nouvelle question"),
        )
        assertNull(ServeSessionMessages.trailingReply(rows))
    }

    @Test
    fun `un assistant vide ne compte pas comme reponse`() {
        val rows = listOf(row("user", "question"), row("assistant", "   "))
        assertNull(ServeSessionMessages.trailingReply(rows))
    }

    // — 2 · ce que la relecture remplace, et ce qu'elle ne touche pas —

    @Test
    fun `une bulle vide prend la reponse de l hote`() {
        assertEquals("TERMINE", ServeSessionMessages.merge("", "TERMINE"))
        assertEquals("TERMINE", ServeSessionMessages.merge("  ", "TERMINE"))
    }

    @Test
    fun `une reponse coupee au milieu est remplacee par la version complete`() {
        // Le flux a lâché sur « TER » : l'hôte en sait plus, il prolonge ce qu'on a lu.
        assertEquals("TERMINE", ServeSessionMessages.merge("TER", "TERMINE"))
    }

    @Test
    fun `une reponse divergente n ecrase pas ce que l utilisateur lit`() {
        assertNull("deux réponses différentes : on ne choisit pas", ServeSessionMessages.merge("bonjour", "autre chose"))
        assertNull("l'hôte en sait moins que l'écran", ServeSessionMessages.merge("TERMINE et plus", "TERMINE"))
        assertNull("rien à relire", ServeSessionMessages.merge("TER", null))
    }

    // — 3 · la lecture du JSON de la route —

    @Test
    fun `la route se lit en messages avec un contenu en blocs`() {
        val body = """
            {"messages": [
              {"role": "user", "content": "question"},
              {"role": "assistant", "content": [{"type": "text", "text": "ré"}, {"type": "text", "text": "ponse"}]}
            ]}
        """.trimIndent()
        val rows = ServeSessionMessages.parseRows(body)
        assertEquals(2, rows.size)
        assertEquals("assistant", rows[1].role)
        assertEquals("réponse", rows[1].text)
    }

    @Test
    fun `une route illisible ne rend aucun message`() {
        assertEquals(0, ServeSessionMessages.parseRows("").size)
        assertEquals(0, ServeSessionMessages.parseRows("pas du json").size)
        assertEquals(0, ServeSessionMessages.parseRows("[]").size)
    }
}
