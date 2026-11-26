package com.onyx.app

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le switch de bot ne repaie plus le handshake.**
 *
 * `ServeRpc.connect()` coûte un login, un ticket (`POST /api/auth/ws-ticket`) et une montée de
 * WebSocket. Le canal fige son profil, donc jeter le canal à chaque changement de bot repayait ces
 * trois allers-retours **à chaque passage** — et revenir sur un bot qu'on venait de quitter coûtait
 * autant que l'aller.
 *
 * Ce verrou tient la forme du correctif, que le test unitaire de la clé ne peut pas voir : un canal
 * par (serveur, profil), gardé ouvert, borné, et aucun `close()` sur un canal partagé.
 */
class ServeChannelCacheTest {

    private val viewModel = File("src/main/java/com/onyx/app/ui/chat/ChatViewModel.kt").readText()

    @Test
    fun `le canal est resolu par la cle du serveur et du profil, pas par un champ unique`() {
        assertTrue(
            "un canal par (serveur, profil)",
            viewModel.contains("private val serveChannels ="),
        )
        assertTrue(
            "la résolution passe par la clé",
            viewModel.contains("serveChannelKey(server)"),
        )
        assertFalse(
            "le champ unique repayait un handshake à chaque changement de bot",
            viewModel.contains("private var rpc: com.onyx.app.data.net.ServeRpc? = null"),
        )
    }

    @Test
    fun `changer de bot prechauffe le nouveau sans fermer les anciens`() {
        val setBotProfile = viewModel.substringAfter("fun setBotProfile(").substringBefore("fun bindBot(")
        assertTrue(
            "le profil choisi est préchauffé",
            setBotProfile.contains("preheatServe(com.onyx.app.ui.design.ProfileInForce.appliedTo("),
        )
        assertFalse(
            "le canal du bot quitté reste ouvert : c'est tout le gain du retour",
            setBotProfile.contains("dropServeChannel()") || setBotProfile.contains("retainServeChannels("),
        )
    }

    @Test
    fun `changer de serveur ne garde que ses propres canaux`() {
        assertTrue(
            "au changement de serveur, les canaux d'une autre adresse ou d'autres identifiants s'en vont",
            viewModel.contains("retainServeChannels(com.onyx.app.data.net.serveChannelKey(server))"),
        )
    }

    @Test
    fun `un canal partage n est jamais referme par un appelant`() {
        // Trois appels ponctuels (répondre à une question, un mot de passe sudo, un secret) créaient
        // leur propre canal pour le refermer aussitôt : deux handshakes pour une trame. Ils passent
        // désormais par le canal du bot, sans le fermer — il appartient au cache.
        val clarify = viewModel.substringAfter("fun respondClarify(").substringBefore("fun respondSudo(")
        val sudo = viewModel.substringAfter("fun respondSudo(").substringBefore("fun respondSecret(")
        val secret = viewModel.substringAfter("fun respondSecret(").substringBefore("/** Donne le")
        listOf("clarify" to clarify, "sudo" to sudo, "secret" to secret).forEach { (name, body) ->
            assertTrue("$name passe par le canal du bot", body.contains("serveChannel(server)"))
            assertFalse("$name ne referme pas un canal partagé", body.contains("rpc.close()"))
        }
    }

    @Test
    fun `le cache est borne, et le canal evince se referme`() {
        assertTrue(
            "un plafond existe",
            viewModel.contains("private const val ServeChannelLimit"),
        )
        assertTrue(
            "l'éviction referme le socket au lieu de l'oublier",
            viewModel.contains("runCatching { eldest.value.close() }"),
        )
    }
}