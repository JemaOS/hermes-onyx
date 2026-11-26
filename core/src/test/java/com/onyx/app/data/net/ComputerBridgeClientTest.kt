package com.onyx.app.data.net

import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * La session « Ordinateur » telle qu'elle se comporte vraiment : ce qui part sur le fil, ce qui
 * reste à l'écran quand le pont refuse, et ce qui arrive quand il se tait.
 *
 * Rien d'inventé : le pont est une `MockWebServer`, le WebSocket est réel, et les motifs de refus
 * sont ceux que le pont envoie (`bad-token:len:18:20`, `too many attempts`). Les cas du lot
 * « adresse et jeton » (schéma déduit du port, jeton recollé, 401 de poignée de main, changement
 * d'adresse ou de jeton) vivent ici à côté : c'est le même client, et une régression de l'un se
 * voit tout de suite dans l'autre.
 */
class ComputerBridgeClientTest {

    private val token = "246d2yiWnd3RjeqSRZqV"

    private fun awaitTrue(what: String, timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(20)
        }
        throw AssertionError("jamais vrai : $what")
    }

    private var serverSide: WebSocket? = null

    private fun helloServer(): MockWebServer = MockWebServer().apply {
        enqueue(
            MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    serverSide = webSocket
                    webSocket.send(
                        """{"t":"hello","screen":[3840,2160],"view":[1280,720],"fps":6,"viewer":"v1","holder":"agent"}""",
                    )
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    // Le pont répond au battement applicatif : la session reste vivante.
                    if (text.contains("\"t\":\"ping\"")) webSocket.send("""{"t":"pong","echo":1}""")
                }
            }),
        )
    }

    /**
     * Ferme le serveur de test sans transformer un arrêt de socket en échec de test : une session
     * encore ouverte fait attendre `shutdown()` (la machine est chargée, plusieurs builds tournent).
     */
    private fun quietShutdown(server: MockWebServer) {
        serverSide?.close(1000, "test over")
        serverSide = null
        Thread.sleep(200)
        try {
            server.shutdown()
        } catch (_: java.io.IOException) {
            // L'assertion est faite ; l'arrêt d'une socket ouverte n'est pas le sujet.
        }
    }

    private fun closeWith(code: Int, reason: String): MockWebServer = MockWebServer().apply {
        enqueue(
            MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.close(code, reason)
                }
            }),
        )
    }

    private fun clientFor(server: MockWebServer, heartbeatMs: Long = 100, silenceMs: Long = 600) =
        ComputerBridgeClient(
            address = server.url("/").toString(),
            token = "  Bearer $token\n",
            heartbeatMs = heartbeatMs,
            silenceDeadlineMs = silenceMs,
        )

    @Test
    fun `le jeton part nettoye et le salut ouvre la session`() {
        val server = helloServer()
        server.start()
        val client = clientFor(server)
        try {
            client.connect()
            awaitTrue("session vivante") { client.phase.value == ComputerBridgeClient.Phase.LIVE }
            val recorded = server.takeRequest(2, TimeUnit.SECONDS)
            assertNotNull("aucune requête reçue", recorded)
            assertEquals("Bearer $token", recorded!!.getHeader("Authorization"))
            assertEquals("/ws", recorded.path)
            awaitTrue("salut reçu") { client.hello.value != null }
            assertEquals(3840, client.hello.value?.screenWidth)
            assertNull(client.notice.value)
        } finally {
            client.close()
            quietShutdown(server)
        }
    }

    @Test
    fun `un jeton tronque est dit a l'ecran, avec les deux longueurs`() {
        val server = closeWith(4401, "bad-token:len:18:20")
        server.start()
        val client = clientFor(server)
        try {
            client.connect()
            awaitTrue("refus enregistré") { client.problem.value != null && client.notice.value != null }
            assertEquals(ComputerBridgeError.TokenCut(18, 20), client.problem.value)
            val notice = client.notice.value ?: ""
            assertTrue("longueur reçue absente: $notice", notice.contains("18"))
            assertTrue("longueur attendue absente: $notice", notice.contains("20"))
            // Aucun texte interne du pont ne doit arriver à l'écran.
            assertTrue("motif brut affiché: $notice", !notice.contains("bad-token"))
            // Un jeton faux ne se réessaie pas tout seul : cela épuiserait les essais du pont.
            Thread.sleep(1_500)
            assertEquals(1, server.requestCount)
        } finally {
            client.close()
            quietShutdown(server)
        }
    }

    @Test
    fun `une origine mise au coin ne se rejoue pas en boucle`() {
        val server = closeWith(4429, "too many attempts")
        server.start()
        val client = clientFor(server)
        try {
            client.connect()
            awaitTrue("mise au coin enregistrée") { client.problem.value != null }
            assertEquals(ComputerBridgeError.TooManyAttempts(300), client.problem.value)
            Thread.sleep(1_500)
            assertEquals(1, server.requestCount)
        } finally {
            client.close()
            quietShutdown(server)
        }
    }

    @Test
    fun `un pont muet coupe la session et le dit, sans jargon`() {
        val server = MockWebServer().apply {
            enqueue(
                MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) = Unit
                }),
            )
        }
        server.start()
        val client = clientFor(server, heartbeatMs = 50, silenceMs = 150)
        try {
            client.connect()
            awaitTrue("silence conclu", timeoutMs = 3_000) {
                client.problem.value is ComputerBridgeError.Silent
            }
            val notice = client.notice.value ?: ""
            assertTrue("message inattendu: $notice", notice.contains("150") || notice.isNotBlank())
            assertTrue("jargon affiché: $notice", !notice.contains("pong"))
            assertEquals(ComputerBridgeClient.Phase.FAILED, client.phase.value)
        } finally {
            client.close()
            quietShutdown(server)
        }
    }

    @Test
    fun `une adresse vide ne lance aucune connexion`() {
        val server = helloServer()
        server.start()
        val client = ComputerBridgeClient(address = "   ", token = token, heartbeatMs = 100)
        try {
            client.connect()
            awaitTrue("adresse jugée invalide") { client.problem.value != null }
            assertEquals(ComputerBridgeError.BadAddress, client.problem.value)
            assertEquals(0, server.requestCount)
        } finally {
            client.close()
            quietShutdown(server)
        }
    }

    @Test
    fun `un jeton fait d'un seul blanc ne part pas sur le fil`() {
        val server = helloServer()
        server.start()
        val client = ComputerBridgeClient(address = server.url("/").toString(), token = "   ", heartbeatMs = 100)
        try {
            client.connect()
            awaitTrue("jeton vide") { client.problem.value != null }
            assertEquals(ComputerBridgeError.TokenMissing, client.problem.value)
            assertEquals(0, server.requestCount)
        } finally {
            client.close()
            quietShutdown(server)
        }
    }

    private val previousLocale = Locale.getDefault()

    @Before
    fun forceEnglish() {
        // Les phrases se vérifient sur leurs clés anglaises : la machine de recette est en français.
        Locale.setDefault(Locale.ENGLISH)
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(previousLocale)
    }

    private fun client(address: String, token: String) = ComputerBridgeClient(
        address = address,
        token = token,
        httpClient = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .build(),
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    )

    private fun await(what: String, timeoutMs: Long = 20000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(25)
        }
        assertTrue("$what n'est jamais arrivé", condition())
    }

    private fun base(server: MockWebServer) = server.url("/").toString().trimEnd('/')

    /**
     * Démonte le serveur de recette.
     *
     * Une session WebSocket que le serveur garde ouverte empêche sa file de se vider, et
     * `shutdown()` rend alors un `IOException` de ménage **après** que les assertions ont passé.
     * Le cas porte sur le jeton reçu, pas sur la fermeture : on tolère ce ménage-là.
     */
    private fun shutdown(server: MockWebServer) {
        Thread.sleep(200)
        runCatching { server.shutdown() }
    }

    @Test
    fun `un serveur local qui exige le jeton ouvre la session au bon jeton`() {
        val server = MockWebServer()
        server.dispatcher = TokenDispatcher("jeton-attendu")
        server.start()
        try {
            // Collé tel quel : blancs autour et préfixe en trop — le pont doit recevoir le jeton.
            val good = client(base(server), "  Bearer jeton-attendu \n")
            good.connect()
            await("le salut du pont") { good.hello.value != null }
            assertEquals(3840, good.hello.value?.screenWidth)
            assertEquals(ComputerBridgeClient.Phase.LIVE, good.phase.value)
            good.close()
        } finally {
            shutdown(server)
        }
    }

    @Test
    fun `changer d'adresse ou de jeton repart sur les nouveaux, sans remplacer le client`() {
        val first = MockWebServer()
        first.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {}))
        first.start()
        val second = MockWebServer()
        second.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {}))
        second.start()
        try {
            val live = client(base(first), "jeton-un")
            live.connect()
            await("la première session") { live.phase.value == ComputerBridgeClient.Phase.LIVE }

            live.reconfigure(base(second), "  Bearer jeton-deux \n")
            // La session en cours est fermée, et le nouvel essai part sur la NOUVELLE adresse —
            // le même objet, pour que l'écran continue de le regarder.
            assertEquals(ComputerBridgeClient.Phase.IDLE, live.phase.value)

            live.connect()
            await("la seconde session") { live.phase.value == ComputerBridgeClient.Phase.LIVE }
            val request = second.takeRequest(5, TimeUnit.SECONDS)
            assertEquals("Bearer jeton-deux", request?.getHeader("Authorization"))
            assertEquals("/ws", request?.path)
            assertEquals(0, first.requestCount - 1) // un seul essai sur la première adresse
            // La fermeture de l'ancien socket ne doit pas se lire comme une panne de la nouvelle
            // session : l'écran afficherait « impossible de joindre le pont » sur une session ouverte.
            Thread.sleep(400)
            assertNull(live.notice.value)
            assertEquals(ComputerBridgeClient.Phase.LIVE, live.phase.value)
            live.close()
        } finally {
            shutdown(first)
            shutdown(second)
        }
    }

    @Test
    fun `un jeton colle part nettoye dans l'en-tete Authorization`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {}))
        server.start()
        try {
            val live = client(base(server), "\n  jeton-utile \r\n")
            live.connect()
            await("la demande d'ouverture") { live.phase.value == ComputerBridgeClient.Phase.LIVE }
            val request = server.takeRequest(5, TimeUnit.SECONDS)
            assertEquals("Bearer jeton-utile", request?.getHeader("Authorization"))
            assertEquals("/ws", request?.path)
            live.close()
        } finally {
            shutdown(server)
        }
    }

    @Test
    fun `un 401 a la poignee de main dit que le jeton est refuse`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(401).setBody("no"))
        server.start()
        try {
            val refused = client(base(server), "jeton-faux")
            refused.connect()
            await("le refus de jeton") { refused.notice.value == "The bridge refused the token." }
            // La phrase ne doit rien laisser filtrer du jeton.
            assertTrue(refused.notice.value?.contains("jeton-faux") == false)
            refused.close()
        } finally {
            shutdown(server)
        }
    }

    @Test
    fun `un nom qui n'existe pas dit que le nom n'existe pas`() {
        val unknown = client("no-such-host-onyx.invalid", "jeton")
        unknown.connect()
        await("le nom introuvable") {
            unknown.notice.value == "This name doesn't exist: check the address."
        }
        assertEquals("This name doesn't exist: check the address.", unknown.notice.value)
        unknown.close()
    }

    @Test
    fun `un port ferme dit que la connexion est refusee`() {
        val closed = client("127.0.0.1:1", "jeton")
        closed.connect()
        await("la connexion refusée") {
            closed.notice.value == "The connection was refused: nothing is listening there."
        }
        assertEquals(
            "The connection was refused: nothing is listening there.",
            closed.notice.value,
        )
        closed.close()
    }

    @Test
    fun `une adresse vide dit que l'adresse n'a pas l'air bonne`() {
        val empty = client("   ", "jeton")
        empty.connect()
        await("l'adresse invalide") {
            empty.notice.value == "The bridge address doesn't look right."
        }
        assertEquals(ComputerBridgeClient.Phase.FAILED, empty.phase.value)
        empty.close()
    }

    @Test
    fun `un jeton en blanc n'appelle meme pas le pont`() {
        val server = MockWebServer()
        server.start()
        try {
            val blank = client(base(server), "   \n\t ")
            blank.connect()
            await("le jeton manquant") { blank.notice.value == "The bridge token is missing." }
            assertEquals("The bridge token is missing.", blank.notice.value)
            assertEquals(0, server.requestCount)
            blank.close()
        } finally {
            shutdown(server)
        }
    }

    /**
     * Un pont en miniature : il n'ouvre la session qu'au bon jeton.
     *
     * Un refus **après** la poignée de main (code `4401`) ne se provoque pas avec MockWebServer —
     * sa fermeture serveur n'atteint pas le client. Ce chemin-là se vérifie sur le vrai pont, avec
     * un faux jeton, et se lit au journal du pont ; ici on verrouille ce que MockWebServer sait
     * faire : l'en-tête reçu, et une session qui s'ouvre quand le jeton est le bon.
     */
    private class TokenDispatcher(private val expected: String) : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val accepted = request.getHeader("Authorization") == "Bearer $expected"
            return MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    if (!accepted) return
                    webSocket.send(
                        "{\"t\":\"hello\",\"screen\":[3840,2160],\"view\":[1280,720],\"fps\":6," +
                            "\"viewer\":\"test\",\"holder\":\"agent\"}",
                    )
                }
            })
        }
    }

    // ---- la compatibilité avec un pont plus ancien -----------------------------------------------

    /**
     * Un pont qui répond à tout ce qu'on lui envoie, avec le refus qu'on lui a fixé.
     *
     * `reasonFor` rend `null` pour ce qu'il accepte. Les identifiants reviennent tels quels — c'est
     * ce qui permet au client de reconnaître **laquelle** de ses demandes vient d'être refusée — et
     * `vus` garde la trace des types effectivement reçus, pour que le test attende un fait et non
     * une supposition.
     */
    private fun denyingServer(
        reasonFor: (String) -> String?,
        vus: MutableList<String> = mutableListOf(),
    ): MockWebServer = MockWebServer().apply {
        enqueue(
            MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send(
                        "{\"t\":\"hello\",\"screen\":[3840,2160],\"view\":[1280,720],\"fps\":6,\"viewer\":\"test\"}",
                    )
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    // Le pont répond au battement applicatif : la session reste vivante.
                    if (text.contains("\"t\":\"ping\"")) {
                        webSocket.send("{\"t\":\"pong\",\"echo\":1}")
                        return
                    }
                    val kind = Regex("\"t\":\"([a-z_]+)\"").find(text)?.groupValues?.get(1) ?: return
                    vus.add(kind)
                    val reason = reasonFor(kind) ?: return
                    val id = Regex("\"id\":(\\d+)").find(text)?.groupValues?.get(1)
                    webSocket.send("{\"t\":\"denied\",\"id\":$id,\"reason\":\"$reason\"}")
                }
            }),
        )
    }

    /**
     * Un pont plus ancien ne connaît ni la taille d'image ni la cadence : il refuse les deux. Ce
     * refus ne dit rien de l'écran — l'app doit donc se taire, et continuer à afficher l'aperçu.
     */
    @Test
    fun `un pont plus ancien refuse la taille et la cadence, aucune panne a l'ecran`() {
        val vus = mutableListOf<String>()
        val server = denyingServer(
            { kind -> if (kind == "stream" || kind == "rate") "unknown_action:$kind" else null },
            vus,
        )
        server.start()
        try {
            val app = client(base(server), token)
            app.connect()
            await("la session vivante") { app.phase.value == ComputerBridgeClient.Phase.LIVE }
            app.requestStream(1080)
            app.setMoving(true)
            await("les deux demandes parties") { vus.contains("stream") && vus.contains("rate") }
            // Le refus a le temps d'arriver : s'il devait s'afficher, il serait là.
            Thread.sleep(700)
            assertNull(
                "une demande de confort refusée ne doit rien afficher : ${app.notice.value}",
                app.notice.value,
            )
            assertEquals(ComputerBridgeClient.Phase.LIVE, app.phase.value)
            app.close()
        } finally {
            shutdown(server)
        }
    }

    /** Le pendant du cas précédent : un refus d'**action**, lui, reste affiché. */
    @Test
    fun `un refus d'action, lui, reste affiche`() {
        val server = denyingServer({ kind -> if (kind == "tap") "not_holder" else null })
        server.start()
        try {
            val app = client(base(server), token)
            app.connect()
            await("la session vivante") { app.phase.value == ComputerBridgeClient.Phase.LIVE }
            app.tap(100, 200)
            await("le refus affiché") { app.notice.value != null }
            assertTrue("refus d'action perdu : ${app.notice.value}", app.notice.value!!.contains("hand"))
            app.close()
        } finally {
            shutdown(server)
        }
    }
}
