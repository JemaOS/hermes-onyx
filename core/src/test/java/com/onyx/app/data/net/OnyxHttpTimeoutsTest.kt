package com.onyx.app.data.net

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le budget d'une sonde de connexion, et la place du délai de connexion **dedans**.
 *
 * Le défaut observé : la sonde partait sur un client neuf dont le budget global était de 8 s,
 * alors que le délai de connexion hérité en valait 15. Un réseau où une seule des familles
 * d'adresses ne répond pas — IPv6 noire en 4G/5G, ou un résolveur qui tarde sur l'AAAA —
 * consommait alors **tout** le budget, et OkHttp n'essayait jamais l'adresse suivante : la
 * requête expirait « pendant la connexion », donc n'atteignait jamais le serveur, et l'écran
 * affichait « le serveur n'a pas répondu à temps » sur un serveur parfaitement joignable.
 *
 * La règle tenue par ces tests : **le délai de connexion est toujours plus court que le budget
 * global**, pour qu'une adresse morte soit abandonnée à temps et que la suivante soit tentée.
 */
class OnyxHttpTimeoutsTest {

    @Test
    fun `le delai de connexion est plus court que le budget global`() {
        listOf(4L, 6L, 8L, 15L, 30L, 60L).forEach { budget ->
            assertTrue(
                "budget ${budget}s : le delai de connexion (${OnyxHttp.connectTimeoutFor(budget)}s) " +
                    "doit etre plus court que le budget",
                OnyxHttp.connectTimeoutFor(budget) < budget,
            )
        }
    }

    @Test
    fun `un budget minuscule ne laisse pas de delai de connexion nul`() {
        // 2 s est le plus petit budget possible : il doit rester un delai de connexion, meme
        // d'une seconde, sinon la connexion serait declaree morte avant d'etre tentee.
        assertTrue(OnyxHttp.connectTimeoutFor(2L) >= 1L)
        assertTrue(OnyxHttp.connectTimeoutFor(1L) >= 1L)
    }

    @Test
    fun `le delai de connexion reste dans des bornes raisonnables`() {
        // Jamais si court qu'une connexion normale serait déclarée morte…
        assertTrue(OnyxHttp.connectTimeoutFor(4L) >= 2L)
        // …ni si long qu'il mangerait le budget d'une sonde courte.
        assertTrue(OnyxHttp.connectTimeoutFor(120L) <= 15L)
    }

    @Test
    fun `le client court des sondes laisse une place au repli sur une autre adresse`() {
        val client = OnyxHttp.shortClient()
        assertTrue(
            "callTimeout=${client.callTimeoutMillis}ms doit dépasser connectTimeout=${client.connectTimeoutMillis}ms",
            client.connectTimeoutMillis < client.callTimeoutMillis,
        )
    }

    @Test
    fun `chaque client court laisse une place au repli`() {
        listOf(4L, 8L, 12L, 60L).forEach { budget ->
            val client = OnyxHttp.shortClient(budget)
            assertTrue(
                "budget ${budget}s : connectTimeout=${client.connectTimeoutMillis}ms " +
                    "doit être < callTimeout=${client.callTimeoutMillis}ms",
                client.connectTimeoutMillis < client.callTimeoutMillis,
            )
        }
    }

    @Test
    fun `le budget d'une sonde est assez large pour un aller-retour par tunnel mobile`() {
        // 8 s ne suffisaient pas : la sonde expirait avant la fin du handshake TLS sur un
        // réseau mobile, alors que le serveur répondait en quelques dizaines de ms.
        assertTrue(OnyxHttp.PROBE_TIMEOUT_SECONDS >= 12L)
        val client = OnyxHttp.shortClient()
        assertTrue(client.callTimeoutMillis >= 12_000L)
    }
}
