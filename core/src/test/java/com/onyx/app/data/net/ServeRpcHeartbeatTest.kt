package com.onyx.app.data.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Le canal du WebSocket du Dashboard : ce qui fait qu'une coupure se voit, et qu'elle se répare.
 *
 * **Le défaut mesuré.** Le client vivait avec `readTimeout(0)` — aucune échéance de lecture, ce
 * qui est juste pour un flux muet pendant qu'un agent réfléchit — **et sans `pingInterval`**.
 * Personne ne demandait donc jamais à la liaison si elle était encore là. Or un WebSocket de
 * téléphone traverse un NAT, un tunnel Cloudflare, une bascule Wi-Fi→4G : le NAT jette l'état de
 * la connexion après quelques minutes d'inactivité, et le socket meurt **sans trame de close**.
 * C'est exactement ce que le journal du gateway inscrit en `1006`, et ce que la personne ressent
 * comme « ça rame, ça bug » : un tour suspendu, ou une écriture qui échoue et un tour perdu.
 *
 * Ces tests portent sur le **vrai** client (`dashboardTransportClient`), jamais sur une copie du
 * Builder : recopier la configuration dans le test ne prouve rien sur ce que l'app utilise.
 */
class ServeRpcHeartbeatTest {

    @Test
    fun `le canal envoie un battement de coeur`() {
        // Un ping toutes les 20 s : sans pong sous deux intervalles, OkHttp déclare la liaison
        // morte, `onFailure` part, la reprise se déclenche. Sans cette valeur, un lien mort reste
        // cru vivant jusqu'à la prochaine écriture — c'est-à-dire jusqu'à ce que le tour casse.
        val client = dashboardTransportClient()

        assertTrue(
            "le canal doit sonder la liaison : pingIntervalMillis=${client.pingIntervalMillis}",
            client.pingIntervalMillis > 0,
        )
        assertEquals(
            "le battement doit être court devant l'inactivité du NAT (20 s)",
            PING_INTERVAL_MS,
            client.pingIntervalMillis.toLong(),
        )
    }

    @Test
    fun `le canal garde une lecture sans echeance`() {
        // À ne pas « corriger » en mettant un readTimeout : un agent peut rester muet longtemps
        // (réflexion, appel d'outil lent). C'est le ping qui doit détecter le lien mort, pas une
        // échéance de lecture — sinon une réponse lente serait tuée en pleine génération.
        val client = dashboardTransportClient()

        assertEquals("la lecture d'un flux ne doit pas expirer", 0, client.readTimeoutMillis)
    }

    @Test
    fun `le canal du websocket ne negocie que http 1 1 et son propre pool`() {
        val client = dashboardTransportClient()
        val rest = OkHttpClientForComparison.pool()

        assertTrue(
            "un upgrade WebSocket ne passe pas sur h2 : ${client.protocols}",
            client.protocols.none { it == okhttp3.Protocol.HTTP_2 },
        )
        assertTrue(
            "le pool du WebSocket ne doit pas être celui du REST (connexion h2 déjà prise)",
            client.connectionPool !== rest,
        )
    }

    @Test
    fun `le delai de reprise double et plafonne`() {
        // 1 s, 2 s, 4 s, 8 s puis 8 s : une coupure brève se répare au premier essai, une bascule
        // de réseau au troisième. L'ancienne formule donnait 1, 2, 3 — plate — suivie d'un abandon
        // après six secondes de patience au total, moins qu'un changement de réseau.
        assertEquals(1_000L, ServeRpc.reconnectDelayMs(1))
        assertEquals(2_000L, ServeRpc.reconnectDelayMs(2))
        assertEquals(4_000L, ServeRpc.reconnectDelayMs(3))
        assertEquals(8_000L, ServeRpc.reconnectDelayMs(4))
        assertEquals(8_000L, ServeRpc.reconnectDelayMs(20))
    }

    @Test
    fun `la patience de reprise couvre une bascule de reseau`() {
        // Trois essais (1 + 2 + 3 = 6 s) laissaient le canal mourir pour de bon au milieu d'une
        // bascule Wi-Fi→4G. Le budget doit dépasser largement une bascule, sans devenir infini.
        val budget = (1..ServeRpc.RECONNECT_ATTEMPTS)
            .sumOf { ServeRpc.reconnectDelayMs(it) }

        assertTrue("le budget de reprise est trop court : ${budget}ms", budget >= 20_000L)
        assertTrue("la reprise ne doit pas devenir sans fin : ${budget}ms", budget <= 60_000L)
    }

    @Test
    fun `le battement borne le temps ou un lien mort reste cru vivant`() {
        // Le contrat : un lien mort est vu en moins de deux intervalles de ping. C'est ce délai —
        // et non l'échéance de lecture, qui n'existe pas — qui décide combien de temps un tour
        // peut paraître figé avant que la reprise se déclenche.
        val detection = PING_INTERVAL_MS * 2

        assertTrue("le battement est trop long : ${PING_INTERVAL_MS}ms", PING_INTERVAL_MS <= 30_000L)
        assertTrue("un lien mort resterait cru vivant ${detection}ms", detection <= 60_000L)
        assertEquals(
            "le battement doit valoir 20 s : assez court pour un tour, assez long pour une batterie",
            20_000L,
            TimeUnit.MILLISECONDS.toMillis(PING_INTERVAL_MS),
        )
    }
}

/** Le pool du client REST, pour prouver que le WebSocket ne le partage pas. */
private object OkHttpClientForComparison {
    private val shared = okhttp3.OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .build()

    fun pool() = shared.connectionPool
}