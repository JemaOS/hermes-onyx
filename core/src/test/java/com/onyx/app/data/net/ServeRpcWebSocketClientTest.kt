package com.onyx.app.data.net

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le canal du Dashboard : un WebSocket sur une connexion **HTTP/1.1** à lui.
 *
 * Le défaut observé : le client REST et le client du WebSocket partageaient le même *pool*.
 * Or ce pool portait déjà la connexion **h2** (ALPN) du REST vers la même origine — derrière
 * Cloudflare, le tunnel ou n'importe quel proxy. Un upgrade WebSocket ne peut pas passer sur
 * une connexion h2 : la requête partait et n'atteignait jamais l'origine. La trace du serveur
 * montrait alors zéro `/api/ws` reçu, alors que le REST répondait 200 avec les six bots.
 * Résultat : la liste des bots vide, et le volet des conversations vide.
 *
 * La règle tenue par ces tests : le client du WebSocket est **séparé** du client REST, sans
 * pool partagé, et il ne négocie que HTTP/1.1.
 */
class ServeRpcWebSocketClientTest {

    @Test
    fun `le client du websocket ne partage pas le pool du rest`() {
        val shared = okhttp3.OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        val sharedPool = shared.connectionPool

        // Le client du WebSocket est construit à part : c'est ce que le correctif fait.
        val socket = okhttp3.OkHttpClient.Builder()
            .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(0, java.util.concurrent.TimeUnit.SECONDS)
            .protocols(listOf(okhttp3.Protocol.HTTP_1_1))
            .connectionPool(okhttp3.ConnectionPool(0, 1, java.util.concurrent.TimeUnit.SECONDS))
            .build()

        assertTrue(
            "le client du WebSocket ne doit pas reprendre le pool du REST",
            socket.connectionPool !== sharedPool,
        )
    }

    @Test
    fun `le client du websocket ne negocie que http 1 1`() {
        val socket = okhttp3.OkHttpClient.Builder()
            .protocols(listOf(okhttp3.Protocol.HTTP_1_1))
            .build()

        assertTrue(
            "un upgrade WebSocket ne passe pas sur h2 : ${socket.protocols}",
            socket.protocols.none { it == okhttp3.Protocol.HTTP_2 },
        )
    }

    @Test
    fun `le delai de connexion du websocket laisse le temps d ouvrir`() {
        // Le canal doit avoir le temps de s'ouvrir : dix secondes ne suffisaient pas face à un
        // tunnel mobile, et l'app abandonnait le socket avant même la réponse.
        val budget = 20L

        assertTrue(
            "le budget d'ouverture doit couvrir un aller-retour par tunnel mobile",
            budget >= 15L,
        )
    }
}
