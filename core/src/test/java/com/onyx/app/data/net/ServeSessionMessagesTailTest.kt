package com.onyx.app.data.net

import com.onyx.app.data.model.ServerConfig
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * **Le sondage d'une réponse ne relit plus le fil entier.**
 *
 * Mesuré sur une vraie conversation (journal du 2026-11-05) : la route
 * `/api/sessions/<id>/messages` rend sa page par défaut de 500 messages — **1,17 Mo** — et l'app
 * la redemandait toutes les 5 s tant qu'un tour se taisait, puis à chaque tentative de rattrapage.
 * `reply()` lit désormais les derniers [ServeSessionMessages.REPLY_TAIL] messages (97 Ko mesurés),
 * ce que `trailingReply` peut seul consommer.
 *
 * Deux pièges verrouillés ici :
 * - `order=latest` doit accompagner `limit`, sinon le serveur pagine depuis le plus **ancien** ;
 * - l'ouverture d'une conversation et le veilleur de notifications gardent la lecture complète
 *   (`isNewReply` compare des **comptes** : une fenêtre bornée les figerait et les alertes
 *   cesseraient en silence).
 */
class ServeSessionMessagesTailTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun config() = ServerConfig(
        id = "s",
        baseUrl = server.url("/").toString(),
        apiKey = "cle-du-gateway-0123456789",
    )

    @Test
    fun `le sondage d une reponse demande les derniers messages, pas la page de 500`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":[]}"""))
        ServeSessionMessages(config()).reply("abc", "coder")
        val path = server.takeRequest().path.orEmpty()
        assertTrue("limit demandé : $path", path.contains("limit=${ServeSessionMessages.REPLY_TAIL}"))
        assertTrue("pagination depuis la fin : $path", path.contains("order=latest"))
        assertTrue("le profil reste annoncé : $path", path.contains("profile=coder"))
    }

    @Test
    fun `la fenetre courte suffit a lire la reponse qui suit le dernier mot`() {
        // Ce que le serveur rend pour limit=40&order=latest : la fin du fil, en ordre chronologique.
        val tail = listOf(
            ServeSessionMessages.Row("tool", """{"output": "…"}"""),
            ServeSessionMessages.Row("assistant", "je cherche"),
            ServeSessionMessages.Row("user", "corrige ça"),
            ServeSessionMessages.Row("assistant", ""),
            ServeSessionMessages.Row("tool", """{"output": "…"}"""),
            ServeSessionMessages.Row("assistant", "C'est corrigé."),
        )
        assertEquals("C'est corrigé.", ServeSessionMessages.trailingReply(tail))
    }

    @Test
    fun `sans limit, la lecture reste complete pour le fil`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":[]}"""))
        ServeSessionMessages(config()).rows("abc", "coder")
        val path = server.takeRequest().path.orEmpty()
        assertTrue("aucune pagination sur la lecture du fil : $path", !path.contains("limit="))
        assertTrue("ni order : $path", !path.contains("order="))
    }

    @Test
    fun `le veilleur de notifications garde la lecture complete`() {
        // Garde de source : borner cette lecture figerait son compte et `isNewReply`
        // (`current.count <= previous.count` -> false) n'annoncerait plus aucune réponse.
        // Ce test vit dans `:core`, le veilleur dans `:app` : chemins relatifs au module.
        val source = File("../app/src/main/java/com/onyx/app/notifications/ReplyWatcher.kt").readText()
        assertTrue(
            "ReplyWatcher lit le fil complet (son instantané compte les messages)",
            source.contains("sessions.rows(sessionId, conversation.botProfile)"),
        )
        assertTrue(
            "et la règle du compte est bien celle qui interdit de borner",
            File("src/main/java/com/onyx/app/data/net/ReplyWatch.kt").readText()
                .contains("current.count <= previous.count"),
        )
    }
}