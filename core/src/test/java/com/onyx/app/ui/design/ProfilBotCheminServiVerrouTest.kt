package com.onyx.app.ui.design

import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.Transport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * **Le chemin réellement servi** par le compagnon « une clé » (127.0.0.1:8890).
 *
 * Le compagnon ne sert que deux familles de chemins (`onyx_one.py`, `_target()`) :
 *
 *  * `/v1` et ses sous-chemins → le serveur API OpenAI de la passerelle (127.0.0.1:8642) ;
 *  * `/api` et ses sous-chemins → le tableau de bord Hermes (127.0.0.1:9119), session entretenue
 *    côté serveur.
 *
 * Tout le reste — dont `/p/<profil>/…` — part au **tableau de bord**, qui répond
 * `405 Method Not Allowed`. Le préfixe `/p/<profil>` n'existe que sur le serveur API de la
 * passerelle **pris directement**, et seulement avec la clé **propre au profil** ; le compagnon
 * ne le relaie pas. Un tour de bot sur profil distant ne doit donc **jamais** partir sur ce chemin.
 *
 * Ce verrou échoue si une requête portant un profil de bot part sur un chemin que le serveur ne
 * sert pas — il **nomme** alors le chemin fautif et le `405` — ou si le profil du bot n'est pas
 * porté par le canal qui le sert (le RPC du tableau de bord, `profile` sur `session.create`).
 *
 * Morsure (source d'avant) : le tour de `coder` part sur
 * `POST /p/coder/v1/chat/completions → 405 Method Not Allowed`.
 */
class ProfilBotCheminServiVerrouTest {

    private fun serveur(profil: String = "") = ServerConfig(
        id = "default",
        name = "Onyx",
        baseUrl = "http://10.0.2.2:8890",
        serveBaseUrl = "http://10.0.2.2:8890",
        hermesProfile = profil,
        transport = Transport.HERMES_SESSIONS,
    )

    /** Le chemin du tour, **exactement** tel qu'il part : base d'appel + endpoint du chat. */
    private fun cheminDuTour(bot: String?): String {
        val config = ProfileInForce.appliedTo(serveur(), bot)
        val prefixe = config.apiBaseUrl.removePrefix("http://10.0.2.2:8890")
        return prefixe + "/v1/chat/completions"
    }

    @Test
    fun `le tour d un bot sur profil ne part pas sur un chemin non servi`() {
        for (bot in listOf("coder", "qa-tester", "scout", "kilo")) {
            val chemin = cheminDuTour(bot)
            assertFalse(
                "$bot : le tour part sur `POST $chemin` — chemin NON SERVI par le compagnon " +
                    "(il ne sert que `/v1/*` et `/api/*`) : le tableau de bord répond " +
                    "`405 Method Not Allowed`. Le profil doit passer par le RPC `profile`.",
                chemin.contains("/p/"),
            )
        }
    }

    @Test
    fun `le profil du bot est porte par le canal qui le sert`() {
        val config = ProfileInForce.appliedTo(serveur(), "coder")
        // Le RPC du tableau de bord est le **seul** canal qui porte `profile` à travers le
        // compagnon ; le transport `/v1` (OpenAI) n'a aucun sélecteur de profil.
        assertEquals(
            "coder : le tour doit passer par le canal qui porte `profile` (RPC du tableau de bord)",
            Transport.HERMES_SERVE,
            config.transport,
        )
        assertEquals("coder", config.effectiveProfile)
    }

    @Test
    fun `le bot principal ne change pas le transport et ne prefixe rien`() {
        val config = ProfileInForce.appliedTo(serveur(), "")
        assertEquals(null, config.effectiveProfile)
        assertEquals(Transport.HERMES_SESSIONS, config.transport)
    }
}
