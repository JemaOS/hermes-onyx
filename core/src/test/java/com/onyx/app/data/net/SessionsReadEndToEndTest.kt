package com.onyx.app.data.net

import com.onyx.app.data.model.ServerConfig
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress

/**
 * **La lecture complète, contre un vrai serveur local qui répond la forme réelle.**
 *
 * `{"object": "list", "data": [ … ]}` — celle du gateway. Le chemin entier est exercé : le client,
 * l'authentification par clé, la requête paginée, le choix de la clé (`data`), le décodage des
 * lignes. Si ce test passe et que l'application lit zéro, alors la différence est dans la
 * configuration du client, pas dans le lecteur.
 */
class SessionsReadEndToEndTest {

    private lateinit var serveur: HttpServer
    private var derniereQuery: String = ""

    private val corps = """
        {"object": "list", "data": [
          {"id": "api-9ff07d177ac60f52", "source": "api_server", "user_id": "api",
           "model": "deepseek/deepseek-v4.1-flash-1017", "title": "Continue previous task",
           "started_at": 1793844644.792967, "ended_at": null, "message_count": 1310,
           "tool_call_count": 752, "input_tokens": 1488560, "output_tokens": 5203,
           "cwd": "/home/john/Bureau/onyx"},
          {"id": "api-22614007784b1ad4", "source": "api_server", "title": "T'es la",
           "started_at": 1793844500.5, "message_count": 3}
        ]}
    """.trimIndent()

    @Before
    fun ouvrir() {
        serveur = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        serveur.createContext("/api/sessions") { echange ->
            derniereQuery = echange.requestURI.query.orEmpty()
            val octets = corps.toByteArray()
            echange.responseHeaders.add("Content-Type", "application/json")
            echange.sendResponseHeaders(200, octets.size.toLong())
            echange.responseBody.use { it.write(octets) }
        }
        serveur.start()
    }

    @After
    fun fermer() = serveur.stop(0)

    private fun config(): ServerConfig = ServerConfig.blank("test").copy(
        serveBaseUrl = "http://127.0.0.1:${serveur.address.port}",
        apiKey = "cle-de-test",
    )

    @Test
    fun `la lecture simple ramene les deux sessions`() = runBlocking {
        val lues = ServeSessionsApi(ServeClient(config())).allSummaries()
        assertEquals("deux lignes decodables", 2, lues.size)
        assertEquals("api-9ff07d177ac60f52", lues.first().id)
        assertEquals("Continue previous task", lues.first().title)
        assertEquals(1310, lues.first().messageCount)
    }

    @Test
    fun `la lecture par profil du serveur ramene les memes`() = runBlocking {
        val lues = ServeSessionsApi(ServeClient(config())).summariesByProfile(listOf(""))
        assertEquals("une liste vide de profils veut dire « le profil du serveur »", 2, lues.size)
        // Et la requête ne porte alors **aucun** paramètre de profil.
        assertEquals("limit=100&offset=0&order=created", derniereQuery)
    }

    @Test
    fun `le profil nomme part bien dans la requete`() = runBlocking {
        ServeSessionsApi(ServeClient(config())).summariesByProfile(listOf("coder"))
        assertEquals("limit=100&offset=0&order=created&profile=coder", derniereQuery)
    }
}
