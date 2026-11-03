package com.onyx.app.data.net

import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.Transport
import java.io.File
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Ce que la sonde répond, et ce qu'elle refuse de conclure.
 *
 * La brique existe pour qu'un serveur plus ancien **se dégrade** au lieu de produire un écran vide :
 * une route qui répond vaut `YES`, une route absente vaut `NO`, et tout ce qui ne prouve rien
 * (délai dépassé, 5xx) ne vaut jamais un oui. C'est ce que ces tests tiennent, avec le
 * MockWebServer de la maison — aucun écran, aucune UI.
 */
class ServerProbeTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private val paths = java.util.Collections.synchronizedList(mutableListOf<String>())

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun stop() {
        server.shutdown()
    }

    @Test
    fun `une route qui repond vaut YES`() = runBlocking {
        answering(200, """{"ok":true}""", route = VAULT_PATH)
        val probe = probe()

        assertEquals(Support.YES, probe.supports(ServerCapability.VAULT))
        assertTrue(probe.allows(ServerCapability.VAULT))
        assertTrue("la sonde est un GET sur la route déclarée", paths.contains(VAULT_PATH))
        assertEquals("GET", server.takeRequest().method)
    }

    @Test
    fun `une route absente vaut NO`() = runBlocking {
        answering(404, route = "/api/vault")

        val probe = probe()

        assertEquals(Support.NO, probe.supports(ServerCapability.VAULT))
        assertFalse(probe.allows(ServerCapability.VAULT))
    }

    @Test
    fun `un delai depasse ne vaut pas un oui`() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)
        }
        val probe = probe(timeoutSeconds = 1L)

        assertEquals(Support.NO, probe.supports(ServerCapability.VAULT))
    }

    @Test
    fun `deux appels de suite ne font qu'une requete`() = runBlocking {
        answering(200, """{"ok":true}""", route = VAULT_PATH)
        val probe = probe()

        assertEquals(Support.YES, probe.supports(ServerCapability.VAULT))
        assertEquals(Support.YES, probe.supports(ServerCapability.VAULT))

        assertEquals("le cache évite la seconde sonde", 1, server.requestCount)
    }

    @Test
    fun `une signature de configuration differente redemande la sonde`() = runBlocking {
        answering(200, """{"ok":true}""", route = VAULT_PATH)
        val cache = ProbeCache()
        val first = probe(cache = cache, apiKey = "cle-1")
        val second = probe(cache = cache, apiKey = "cle-2")

        first.supports(ServerCapability.VAULT)
        second.supports(ServerCapability.VAULT)

        assertEquals("corriger la clé, c'est un autre serveur : on resonde", 2, server.requestCount)
        assertFalse(
            "les deux couples ne partagent pas la même clé",
            ServerProbe.keyFor(config(apiKey = "cle-1"), ServerCapability.VAULT) ==
                ServerProbe.keyFor(config(apiKey = "cle-2"), ServerCapability.VAULT),
        )
        assertEquals(
            "même couple, même clé",
            ServerProbe.keyFor(config(apiKey = "cle-1"), ServerCapability.VAULT),
            ServerProbe.keyFor(config(apiKey = "cle-1"), ServerCapability.VAULT),
        )
    }

    @Test
    fun `UNKNOWN se comporte comme NO`() = runBlocking {
        answering(503, route = "/api/handoff")
        val probe = probe()

        val answer = probe.supports(ServerCapability.HANDOFF)

        assertEquals("un serveur en peine ne prouve pas la route", Support.UNKNOWN, answer)
        assertFalse("UNKNOWN n'ouvre rien", answer.isUsable)
        assertFalse(probe.allows(ServerCapability.HANDOFF))
        assertFalse(Support.NO.isUsable)
        assertTrue(Support.YES.isUsable)
    }

    @Test
    fun `une sonde sans identifiants ne part pas sur le reseau`() = runBlocking {
        val probe = ServerProbe(
            ServerConfig(id = "sonde", serveBaseUrl = server.url("/").toString().trimEnd('/')),
            cache = ProbeCache(),
        )

        assertEquals(Support.UNKNOWN, probe.supports(ServerCapability.VAULT))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `toutes les capacites declarees sont interrogeables`() = runBlocking {
        answering(200, """{"ok":true}""")
        val probe = probe()

        val all = probe.snapshot()

        assertEquals(ServerCapability.entries, probe.declared())
        assertEquals(ServerCapability.entries.toSet(), all.keys)
        assertTrue(all.values.all { it == Support.YES })
        assertTrue(
            "trois capacités, trois routes",
            paths.containsAll(listOf("/api/vault", "/api/record", "/api/handoff")),
        )
    }

    @Test
    fun `une reponse gravee sur disque evite une seconde sonde`() = runBlocking {
        answering(200, """{"ok":true}""", route = VAULT_PATH)
        val file = File(folder.newFolder(), "sonde.json")

        val first = probe(cache = ProbeCache(file))
        assertEquals(Support.YES, first.supports(ServerCapability.VAULT))
        assertEquals(1, server.requestCount)

        val second = probe(cache = ProbeCache(file))
        assertEquals("le disque évite la sonde de la nouvelle instance", Support.YES, second.supports(ServerCapability.VAULT))
        assertEquals(1, server.requestCount)
        assertTrue("le cache a bien été écrit", file.isFile)
    }

    @Test
    fun `une reponse perimee est redemandee`() {
        var now = 1_000L
        val cache = ProbeCache(maxAgeMillis = 60_000L, clock = { now })

        cache.write("couple|VAULT", Support.YES)
        assertEquals(Support.YES, cache.read("couple|VAULT"))

        now += 61_000L
        assertNull("au-delà de l'expiration, on redemande", cache.read("couple|VAULT"))
    }

    @Test
    fun `une reponse indecise ne se grave pas`() {
        val cache = ProbeCache()

        cache.write("couple|VAULT", Support.UNKNOWN)
        assertNull(cache.read("couple|VAULT"))
    }

    @Test
    fun `le cache disque survit a un contenu corrompu`() {
        val file = File(folder.newFolder(), "sonde.json")
        file.writeText("{ceci n'est pas du json")

        val cache = ProbeCache(file)

        assertNull("un fichier illisible vaut cache vide, pas une panne", cache.read("couple|VAULT"))
        cache.write("couple|VAULT", Support.YES)
        assertEquals(Support.YES, cache.read("couple|VAULT"))
    }

    // MARK: - Outillage

    private fun config(apiKey: String = "cle-1", id: String = "sonde") = ServerConfig(
        id = id,
        serveBaseUrl = server.url("/").toString().trimEnd('/'),
        apiKey = apiKey,
        transport = Transport.HERMES_SERVE,
    )

    private fun probe(
        cache: ProbeCache = ProbeCache(),
        apiKey: String = "cle-1",
        timeoutSeconds: Long = ServerProbe.DEFAULT_TIMEOUT_SECONDS,
    ) = ServerProbe(config(apiKey), cache = cache, timeoutSeconds = timeoutSeconds)

    /** Un serveur qui répond [code] sur [route] et 404 partout ailleurs. */
    private fun answering(code: Int, body: String = "{}", route: String? = null) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl?.encodedPath ?: request.path.orEmpty()
                paths += path
                val matched = route == null || path == route
                return if (matched) {
                    MockResponse().setResponseCode(code).setBody(body)
                } else {
                    MockResponse().setResponseCode(404).setBody("""{"error":"not found"}""")
                }
            }
        }
    }

    private companion object {
        const val VAULT_PATH = "/api/vault"
    }
}
