package com.onyx.app.data.net

import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.Transport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * **Ce qui fait deux canaux, et ce qui n'en fait qu'un.**
 *
 * Un `ServeRpc` fige sa configuration : le profil part dans les méthodes qui l'acceptent, l'adresse
 * vient du serveur. La clé du cache doit donc séparer ce qui change de socket et **réunir** ce qui
 * n'y touche pas — sans quoi on repaie un handshake pour un réglage de modèle, ou on sert le tour
 * d'un bot par le socket d'un autre.
 */
class ServeChannelKeyTest {

    private fun server(
        base: String = "https://onyx.example",
        profile: String = "",
        model: String = "",
        provider: String = "",
        password: String = "secret",
    ) = ServerConfig(
        id = "s",
        name = "Dashboard",
        serveBaseUrl = base,
        serveUsername = "alice",
        servePassword = password,
        hermesProfile = profile,
        model = model,
        provider = provider,
        transport = Transport.HERMES_SERVE,
    )

    @Test
    fun `deux profils de bot ne partagent jamais un canal`() {
        assertNotEquals(
            "le canal d'un bot ne peut pas servir le tour du suivant",
            serveChannelKey(server(profile = "coder")),
            serveChannelKey(server(profile = "writer")),
        )
        assertEquals(
            "le même profil, lu deux fois, est le même canal",
            serveChannelKey(server(profile = "coder")),
            serveChannelKey(server(profile = "coder")),
        )
    }

    @Test
    fun `le profil par defaut et le profil vide sont le meme canal`() {
        assertEquals(
            "« default » et « pas de profil » désignent la passerelle elle-même",
            serveChannelKey(server(profile = "")),
            serveChannelKey(server(profile = "default")),
        )
    }

    @Test
    fun `choisir un modele ne jette pas le canal`() {
        assertEquals(
            "le modèle n'est pas porté par le socket : le jeter coûtait un login et un ticket",
            serveChannelKey(server(model = "hermes-kilo")),
            serveChannelKey(server(model = "hermes-local")),
        )
        assertEquals(
            "ni le provider",
            serveChannelKey(server(provider = "openrouter")),
            serveChannelKey(server(provider = "")),
        )
    }

    @Test
    fun `une autre adresse ou un autre mot de passe est un autre canal`() {
        assertNotEquals(
            "deux serveurs ne partagent pas un socket",
            serveChannelKey(server(base = "https://onyx.example")),
            serveChannelKey(server(base = "https://autre.example")),
        )
        assertNotEquals(
            "des identifiants changés rouvrent une session",
            serveChannelKey(server(password = "secret")),
            serveChannelKey(server(password = "autre-secret")),
        )
    }

    @Test
    fun `la cle ne porte aucun secret en clair`() {
        val key = serveChannelKey(server(password = "mot-de-passe-tres-reconnaissable"))
        assertEquals(
            "le mot de passe n'apparaît pas dans une clé qui peut finir dans un journal",
            false,
            key.contains("mot-de-passe-tres-reconnaissable"),
        )
    }
}