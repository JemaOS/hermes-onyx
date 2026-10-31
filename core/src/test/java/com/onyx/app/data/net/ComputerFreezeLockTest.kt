package com.onyx.app.data.net

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le gel de l'écran, verrouillé par des tests — c'est le défaut qui a coûté le plus cher.
 *
 * Trois vérités y sont tenues, chacune pour une des trois causes possibles :
 *
 * 1. **un pong ne prouve pas que l'image arrive** : le pont répondait aux pings pendant que sa
 *    capture était morte, donc la session paraissait vivante et l'écran restait figé pour toujours.
 *    Aucune image pendant N secondes doit déclencher une reprise, **même** quand les pongs passent ;
 * 2. **un flux sain ne se coupe pas** : recevoir des images doit suffire à ne jamais déclencher la
 *    reprise, sinon on remplacerait un gel par une reconnexion permanente ;
 * 3. **la demande de flux se réaffirme à chaque ouverture** : après une coupure, l'app redemandait
 *    rien et le pont s'abonnait à un flux mort. Elle doit redemander **la même taille**, sans quoi
 *    le pont ne relance pas sa capture.
 *
 * Le pont est une `MockWebServer` : le WebSocket est réel, ce qui part sur le fil est lu, et rien
 * n'est inventé.
 */
class ComputerFreezeLockTest {

    private val token = "246d2yiWnd3RjeqSRZqV"

    private fun awaitTrue(what: String, timeoutMs: Long = 8_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(20)
        }
        throw AssertionError("jamais vrai : $what")
    }

    private fun clientFor(
        server: MockWebServer,
        heartbeatMs: Long = 150,
        silenceMs: Long = 10_000,
        imageMs: Long = 300,
    ) = ComputerBridgeClient(
        address = server.url("/").toString(),
        token = token,
        heartbeatMs = heartbeatMs,
        silenceDeadlineMs = silenceMs,
        imageDeadlineMs = imageMs,
    )

    private fun quietShutdown(server: MockWebServer) {
        try {
            server.shutdown()
        } catch (_: java.io.IOException) {
            // L'arrêt d'une socket encore ouverte n'est pas le sujet du test.
        }
    }

    /**
     * Le pont est **vivant** (il répond aux pings) mais sa capture est morte : plus une image.
     * C'est le cas exact du défaut. La reprise doit partir du constat « aucune image ».
     */
    @Test
    fun `un flux fige declenche une reprise meme si le pont repond aux pings`() {
        val pongs = AtomicInteger()
        val server = MockWebServer().apply {
            enqueue(
                MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        webSocket.send(
                            """{"t":"hello","screen":[3840,2160],"view":[1280,720],"fps":6,"viewer":"v1","holder":"agent"}""",
                        )
                    }

                    // Le pont répond **toujours** : c'est ce qui rendait le gel invisible.
                    override fun onMessage(webSocket: WebSocket, text: String) {
                        if (text.contains("\"t\":\"ping\"")) {
                            pongs.incrementAndGet()
                            webSocket.send("""{"t":"pong","echo":1}""")
                        }
                    }
                }),
            )
        }
        server.start()
        val client = clientFor(server, imageMs = 300)
        try {
            client.connect()
            awaitTrue("session ouverte") { client.phase.value == ComputerBridgeClient.Phase.LIVE }
            // Aucune image ne viendra : la reprise doit être conclue sans attendre la fin des temps.
            awaitTrue("reprise conclue sur l'inactivité de l'image") {
                client.phase.value == ComputerBridgeClient.Phase.RECONNECTING
            }
            assertTrue("le pont a bien répondu aux pings", pongs.get() >= 1)
            // Le message peut être posé quelques millisecondes après le changement de phase : on
            // l'attend, mais il doit **venir** — un gel muet était tout le défaut.
            awaitTrue("message de reprise affiché", timeoutMs = 2_000) {
                !client.notice.value.isNullOrBlank()
            }
            val notice = client.notice.value ?: ""
            assertTrue("aucun message affiché : le gel serait resté muet", notice.isNotBlank())
            // Le texte de la bibliothèque ne doit jamais atteindre l'écran.
            assertFalse("jargon affiché: $notice", notice.contains("pong"))
            assertFalse("jargon affiché: $notice", notice.contains("ms ("))
        } finally {
            client.close()
            quietShutdown(server)
        }
    }

    /** Un flux qui arrive vraiment ne doit **jamais** être pris pour un gel. */
    @Test
    fun `des images qui arrivent ne declenchent aucune reprise`() {
        val server = MockWebServer().apply {
            enqueue(
                MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        webSocket.send(
                            """{"t":"hello","screen":[3840,2160],"view":[1280,720],"fps":6,"viewer":"v1","holder":"agent"}""",
                        )
                        val bytes = ByteArray(64) { 7 }
                        val forward = webSocket.send(okio.ByteString.of(*bytes))
                        assertTrue(forward)
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        if (text.contains("\"t\":\"ping\"")) {
                            webSocket.send("""{"t":"pong","echo":1}""")
                            val bytes = ByteArray(64) { 9 }
                            webSocket.send(okio.ByteString.of(*bytes))
                        }
                    }
                }),
            )
        }
        server.start()
        val client = clientFor(server, heartbeatMs = 200, imageMs = 1_500)
        try {
            client.connect()
            awaitTrue("session ouverte") { client.phase.value == ComputerBridgeClient.Phase.LIVE }
            Thread.sleep(1_800)
            assertEquals(ComputerBridgeClient.Phase.LIVE, client.phase.value)
            assertNull("une panne a été inventée sur un flux sain", client.problem.value)
        } finally {
            client.close()
            quietShutdown(server)
        }
    }

    /** Ferme une session de test **après** un délai : une coupure ne précède pas la demande. */
    private fun closeLater(webSocket: WebSocket, delayMs: Long) {
        Thread {
            try {
                Thread.sleep(delayMs)
            } catch (_: InterruptedException) {
                // Le test se termine ; rien à signaler.
            }
            webSocket.close(1000, "tunnel cut")
        }.start()
    }

    /**
     * La demande de flux se **réaffirme** à chaque ouverture, à la même taille.
     *
     * C'est la moitié « app » du défaut : après une coupure, l'app se reconnectait sans rien
     * redemander, et le pont n'avait donc aucune raison de relancer sa capture — image figée.
     */
    @Test
    fun `le flux est redemande a la meme taille a chaque ouverture`() {
        val opens = AtomicInteger()
        val streams = java.util.Collections.synchronizedList(mutableListOf<String>())
        val server = MockWebServer().apply {
            repeat(2) {
                enqueue(
                    MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                        override fun onOpen(webSocket: WebSocket, response: Response) {
                            val which = opens.incrementAndGet()
                            webSocket.send(
                                """{"t":"hello","screen":[3840,2160],"view":[1080,608],"fps":6,"viewer":"v$which","holder":"agent"}""",
                            )
                            // La première session « lâche » un peu après : c'est la coupure du
                            // tunnel. Un peu après, sinon la demande de flux n'a pas le temps de
                            // partir et le test jugerait une course, pas le client.
                            if (which == 1) closeLater(webSocket, 800)
                        }

                        override fun onMessage(webSocket: WebSocket, text: String) {
                            if (text.contains("\"t\":\"stream\"")) streams.add(text)
                            if (text.contains("\"t\":\"ping\"")) {
                                webSocket.send("""{"t":"pong","echo":1}""")
                            }
                        }
                    }),
                )
            }
        }
        server.start()
        val client = clientFor(server, silenceMs = 30_000, imageMs = 30_000)
        try {
            client.connect()
            awaitTrue("première session ouverte") {
                client.phase.value == ComputerBridgeClient.Phase.LIVE
            }
            // L'écran mesure son volet une fois — comme le fait la composition.
            client.requestStream(1080)
            awaitTrue("demande envoyée sur la première session") { streams.isNotEmpty() }

            // La reconnexion part toute seule ; elle redemande **la même taille** sans que l'écran
            // ne reparle.
            awaitTrue("seconde session ouverte", timeoutMs = 12_000) { opens.get() >= 2 }
            awaitTrue("demande réaffirmée sur la seconde session", timeoutMs = 8_000) {
                streams.size >= 2
            }
            assertTrue("largeur absente: ${streams[1]}", streams[1].contains("\"width\":1080"))
            assertEquals(2, streams.size)
        } finally {
            client.close()
            quietShutdown(server)
        }
    }

    /** La reprise ne rend ni le jeton ni la session : on repart, on ne recommence pas. */
    @Test
    fun `une reprise garde le jeton et ne repasse pas par l'accueil`() {
        val opens = AtomicInteger()
        val authorizations = java.util.Collections.synchronizedList(mutableListOf<String>())
        val server = MockWebServer().apply {
            repeat(2) {
                enqueue(
                    MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                        override fun onOpen(webSocket: WebSocket, response: Response) {
                            val which = opens.incrementAndGet()
                            webSocket.send(
                                """{"t":"hello","screen":[3840,2160],"view":[1080,608],"fps":6,"viewer":"v$which","holder":"agent"}""",
                            )
                            if (which == 1) closeLater(webSocket, 800)
                        }
                    }),
                )
            }
        }
        server.start()
        val client = clientFor(server, silenceMs = 30_000, imageMs = 30_000)
        try {
            client.connect()
            awaitTrue("première session") { client.phase.value == ComputerBridgeClient.Phase.LIVE }
            client.requestStream(1080)
            awaitTrue("seconde session", timeoutMs = 12_000) { opens.get() >= 2 }
            // Le jeton part **sur les deux** connexions : rien n'a été redemandé à l'utilisateur.
            repeat(2) {
                val recorded = server.takeRequest(5, TimeUnit.SECONDS)
                if (recorded != null) authorizations.add(recorded.getHeader("Authorization") ?: "")
            }
            assertEquals(2, authorizations.size)
            assertTrue(
                "jeton perdu en route: $authorizations",
                authorizations.all { it.startsWith("Bearer ") },
            )
        } finally {
            client.close()
            quietShutdown(server)
        }
    }
}
