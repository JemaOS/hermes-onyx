package com.onyx.app.ui.design

import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.Transport
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * **Verrou de la liaison bot → profil.**
 *
 * Il échoue si la conversation d'un bot ne tourne pas dans le profil de ce bot — c'est-à-dire si la
 * configuration employée pour le tour ne porte pas le `profile_name` attendu — **ou** si le profil
 * affiché diffère du profil en force.
 *
 * La morsure : sur la source d'avant, le profil du bot n'était écrit nulle part, et un bot `coder`
 * obtenait `default` (« coder : attendu coder, obtenu default »).
 */
class BotProfileBindingVerrouTest {

    private fun serveur(profil: String = "") = ServerConfig(
        id = "default",
        name = "Onyx",
        baseUrl = "http://10.0.2.2:8890",
        serveBaseUrl = "http://10.0.2.2:8890",
        hermesProfile = profil,
        transport = Transport.HERMES_SERVE,
    )

    /** Le profil que la configuration du tour fait **réellement** porter à la session. */
    private fun profilDuTour(botProfil: String?, profilServeur: String = ""): String =
        ProfileInForce.appliedTo(serveur(profilServeur), botProfil).effectiveProfile ?: ProfileInForce.Default

    @Test
    fun `une conversation ouverte depuis un bot tourne dans le profil de ce bot`() {
        val obtenu = profilDuTour("coder")
        assertEquals("coder : attendu coder, obtenu $obtenu", "coder", obtenu)
    }

    @Test
    fun `les sept profils servent chacun leur conversation`() {
        val bots = listOf("default", "coder", "kilo", "kilo-2", "qa-tester", "scout", "writer")
        for (bot in bots) {
            val attendu = if (bot == "default") "default" else bot
            val obtenu = profilDuTour(if (bot == "default") "" else bot)
            assertEquals("$bot : attendu $attendu, obtenu $obtenu", attendu, obtenu)
        }
    }

    @Test
    fun `le profil affiche est le profil en force`() {
        for (bot in listOf("coder", "kilo", "kilo-2", "qa-tester", "scout", "writer")) {
            val affiche = ProfileInForce.inForce(bot, "")
            val enForce = profilDuTour(bot)
            assertEquals("$bot : affiché $affiche, en force $enForce", enForce, affiche)
        }
    }

    @Test
    fun `le profil du bot prime sur le profil global du serveur`() {
        // Le réglage global de l'app vaut « kilo » ; la conversation du bot « coder » doit rester
        // « coder », pas « kilo » : c'est la liaison bot → profil qui décide.
        val obtenu = profilDuTour("coder", profilServeur = "kilo")
        assertEquals("coder : attendu coder, obtenu $obtenu", "coder", obtenu)
    }

    @Test
    fun `le bot principal laisse la passerelle sur son profil par defaut`() {
        // Un bot `isDefault` vaut « default » : aucun `/p/default/` explicite, `hermesProfile` vide.
        val config = ProfileInForce.appliedTo(serveur("kilo"), "")
        assertEquals("default", ProfileInForce.inForce("", "kilo"))
        assertEquals(null, config.effectiveProfile)
    }

    @Test
    fun `une conversation simple garde le profil du serveur`() {
        val config = ProfileInForce.appliedTo(serveur("kilo"), null)
        assertEquals("kilo", config.effectiveProfile)
        assertEquals("kilo", ProfileInForce.inForce(null, "kilo"))
    }

    @Test
    fun `l adresse du tour ne porte pas de prefixe de profil non servi`() {
        // Le profil n'est **jamais** porté par l'adresse : `/p/<profil>` partirait au tableau de
        // bord (405). Il est porté par le canal qui le sert (RPC du tableau de bord, `profile`).
        val config = ProfileInForce.appliedTo(serveur(""), "coder")
        assertEquals("http://10.0.2.2:8890", config.gatewayBaseUrl)
        assertEquals("http://10.0.2.2:8890", config.apiBaseUrl)
        assertEquals(Transport.HERMES_SERVE, config.transport)
        assertEquals("coder", config.effectiveProfile)
    }
}
