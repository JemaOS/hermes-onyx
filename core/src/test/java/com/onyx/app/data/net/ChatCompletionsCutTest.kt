package com.onyx.app.data.net

import com.onyx.app.data.model.ChatRequest
import com.onyx.app.data.model.ChatTurn
import com.onyx.app.data.model.Role
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.Transport
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * **CE QUI DISTINGUE UNE RÉPONSE FINIE D'UNE RÉPONSE ARRACHÉE.**
 *
 * Un flux SSE peut se terminer de trois façons, et deux seulement veulent dire « c'est fini » :
 *
 *  - le serveur envoie `[DONE]` — la sentinelle OpenAI ;
 *  - le serveur nomme sa fin (`choices[].finish_reason` = `stop`, `length`, `tool_calls`) — tous
 *    les serveurs ne passent pas par `[DONE]` ;
 *  - **le socket tombe sans rien dire.** Un NAT jette l'état d'une connexion inactive, un tunnel
 *    expire, une bascule Wi-Fi→4G coupe : le corps s'arrête au milieu d'une phrase, sans erreur,
 *    sans trame de close.
 *
 * Le troisième cas finissait en `Done` : l'app affichait une phrase amputée comme une réponse
 * complète, sans erreur et sans rien à relancer. C'est le « ça se coupe » du propriétaire.
 *
 * Les deux derniers tests gardent l'autre bord : une réponse **entière** ne doit jamais être
 * rejetée comme une coupure. Un correctif de troncature qui casse les réponses complètes est
 * pire que le défaut.
 */
class ChatCompletionsCutTest {

    private fun enqueue(sse: String): Pair<MockWebServer, ServerConfig> {
        val server = MockWebServer()
        server.start()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(sse),
        )
        val config = ServerConfig(
            id = "cut-test",
            name = "Cut test",
            baseUrl = server.url("/").toString().trimEnd('/'),
            transport = Transport.CHAT_COMPLETIONS,
        )
        return server to config
    }

    private fun delta(texte: String, finish: String? = null): String {
        val fin = if (finish == null) "null" else "\"$finish\""
        return """data: {"choices":[{"index":0,"delta":{"content":"$texte"},"finish_reason":$fin}]}"""
    }

    private fun events(sse: String): List<TurnEvent> {
        val (server, config) = enqueue(sse)
        return try {
            runBlocking {
                ChatCompletionsClient(config, OkHttpClient())
                    .stream(ChatRequest(history = listOf(ChatTurn(role = Role.USER, text = "salut"))))
                    .toList()
            }
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `un flux arrache sans sentinelle ni finish_reason est une troncature`() {
        // Deux morceaux de phrase, puis plus rien : pas de [DONE], pas de finish_reason.
        val sse = listOf(delta("La réponse est "), delta("à moitié écrite")).joinToString("\n\n", postfix = "\n\n")

        try {
            events(sse)
            fail("un flux coupé ne doit jamais passer pour une réponse complète")
        } catch (erreur: TransportError.Truncated) {
            // Exactement ce qu'on veut : le tour part en échec récupérable, et l'app va relire
            // la réponse chez l'hôte (voir la reprise de ChatViewModel).
        }
    }

    @Test
    fun `un flux qui nomme sa fin sans sentinelle est complet`() {
        // Certains serveurs n'envoient jamais [DONE] et se contentent de `finish_reason: stop`.
        // Les refuser couperait des réponses parfaitement entières : le test verrouille ce cas.
        val sse = listOf(
            delta("Voilà le résultat"),
            """data: {"choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}""",
        ).joinToString("\n\n", postfix = "\n\n")

        val evenements = events(sse)
        val texte = evenements.filterIsInstance<TurnEvent.TextDelta>().joinToString("") { it.text }
        assertEquals("Voilà le résultat", texte)
        assertTrue("le tour doit se clore sans erreur", evenements.last() is TurnEvent.Done)
    }

    @Test
    fun `un flux clos par la sentinelle est complet`() {
        val sse = listOf(delta("Bonjour"), "data: [DONE]").joinToString("\n\n", postfix = "\n\n")

        val evenements = events(sse)
        assertEquals("Bonjour", evenements.filterIsInstance<TurnEvent.TextDelta>().joinToString("") { it.text })
        assertTrue(evenements.last() is TurnEvent.Done)
    }

    @Test
    fun `une sentinelle sans retour a la ligne final clot le flux`() {
        // `data: [DONE]` collé en fin de corps, sans le `\n\n` habituel : le parseur le rend au
        // `finish()`, et ce verdict doit compter. Le jeter faisait passer une réponse **entière**
        // pour une réponse arrachée — l'inverse du défaut qu'on corrige, aussi grave.
        val sse = listOf(delta("Terminé"), "data: [DONE]").joinToString("\n\n")

        val evenements = events(sse)
        assertEquals("Terminé", evenements.filterIsInstance<TurnEvent.TextDelta>().joinToString("") { it.text })
        assertTrue("la sentinelle sans saut de ligne doit clore le tour", evenements.last() is TurnEvent.Done)
    }

    @Test
    fun `une reponse non streamee est complete sans finish_reason`() {
        // Un serveur qui ignore `stream` renvoie le texte entier dans `message.content`, sans
        // `delta` et sans `finish_reason`. C'est une réponse finie : la refuser couperait des
        // réponses parfaitement entières.
        val sse = """data: {"choices":[{"index":0,"message":{"content":"Réponse entière"}}]}""" + "\n\n"

        val evenements = events(sse)
        // Une réponse non streamée arrive d'un bloc : `TextFinal`, pas une suite de `TextDelta`.
        val texte = evenements.joinToString("") {
            when (it) {
                is TurnEvent.TextDelta -> it.text
                is TurnEvent.TextFinal -> it.text
                else -> ""
            }
        }
        assertEquals("Réponse entière", texte)
        assertTrue("une réponse non streamée est complète", evenements.last() is TurnEvent.Done)
    }
}
