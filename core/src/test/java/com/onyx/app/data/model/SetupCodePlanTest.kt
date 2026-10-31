package com.onyx.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupCodePlanTest {
    private val active = ServerConfig(
        id = "active",
        name = "Actif",
        baseUrl = "https://old-model.example",
        apiKey = "old-secret",
        transport = Transport.CHAT_COMPLETIONS,
    )

    @Test
    fun `un code serveur remplit seulement un serveur inutilise`() {
        val code = SetupCode(dashboardURL = "https://hermes.example")

        val plan = code.plan(active, listOf(active), activeServerIsUnused = true)

        assertEquals(SetupServerAction.FILL, plan.server)
        assertEquals(Transport.HERMES_SESSIONS, plan.transport)
    }

    @Test
    fun `un code serveur ajoute un serveur quand celui-ci est deja utilise`() {
        val code = SetupCode(
            name = "Nouveau",
            dashboardURL = "https://hermes.example",
            apiURL = "https://gateway.example",
        )
        val same = active.copy(
            id = "same",
            name = "Déjà là",
            serveBaseUrl = "https://hermes.example/",
            baseUrl = "https://gateway.example/",
        )

        val plan = code.plan(active, listOf(active, same), activeServerIsUnused = false)

        assertEquals(SetupServerAction.ADD, plan.server)
        assertEquals("Déjà là", plan.sameAs)
    }

    @Test
    fun `un nouveau endpoint ne recupere jamais la cle de lancien`() {
        val code = SetupCode(modelURL = "https://new-model.example", model = "hermes")

        val plan = code.plan(active, listOf(active), activeServerIsUnused = false)
        val installed = code.toServerConfig(active.id)

        assertEquals(SetupServerAction.NONE, plan.server)
        assertEquals("https://old-model.example", plan.replacesModelURL)
        assertEquals("", installed.apiKey)
        assertEquals("https://new-model.example", installed.baseUrl)
    }

    @Test
    fun `le meme endpoint ne signale pas de remplacement`() {
        val code = SetupCode(modelURL = "https://old-model.example/")

        val plan = code.plan(active, listOf(active), activeServerIsUnused = false)

        assertNull(plan.replacesModelURL)
    }

    @Test
    fun `une installation model seule preserve le serveur et la cle du meme endpoint`() {
        val configured = active.copy(
            serveBaseUrl = "https://hermes.example",
            serveUsername = "alice",
            servePassword = "secret",
        )
        val code = SetupCode(modelURL = "https://old-model.example/", model = "new-name")
        val plan = code.plan(configured, listOf(configured), activeServerIsUnused = false)

        val installed = code.configurationForInstall(plan, configured, "unused")

        assertEquals(configured.id, installed.id)
        assertEquals(configured.serveBaseUrl, installed.serveBaseUrl)
        assertEquals(configured.servePassword, installed.servePassword)
        assertEquals("old-secret", installed.apiKey)
        assertEquals("new-name", installed.model)
    }

    @Test
    fun `une installation ajoutee utilise le nouvel identifiant`() {
        val code = SetupCode(name = "Nouveau", apiURL = "https://gateway.example", apiKey = "new-key")
        val plan = code.plan(active, listOf(active), activeServerIsUnused = false)

        val installed = code.configurationForInstall(plan, active, "new-id")

        assertEquals("new-id", installed.id)
        assertEquals("new-key", installed.apiKey)
        assertEquals(Transport.HERMES_SESSIONS, installed.transport)
    }

    @Test
    fun `un code Dashboard reste sur le transport reellement supporte par Android`() {
        val code = SetupCode(dashboardURL = "https://hermes.example", use = Transport.HERMES_SERVE)

        val installed = code.toServerConfig("id")

        assertEquals(Transport.HERMES_SESSIONS, installed.transport)
        assertEquals("https://hermes.example", installed.serveBaseUrl)
    }

    @Test
    fun `un lien a une seule cle fait vivre le Dashboard a la meme adresse`() {
        // Le compagnon « une clé » : `server` + `key`, aucun identifiant de Dashboard. Le même
        // lien doit donner un seul serveur qui porte le chat **et** les bots, sans rien redemander.
        val code = SetupCode(
            name = "Compagnon",
            apiURL = "https://companion.example",
            apiKey = "install-key",
            use = Transport.HERMES_SESSIONS,
        )

        val installed = code.toServerConfig("id")

        assertEquals("https://companion.example", installed.serveBaseUrl)
        assertEquals(Transport.HERMES_SESSIONS, installed.transport)
        assertTrue("le Dashboard est joignable", installed.hasServeCredentials)
        assertTrue("par la clé, sans mot de passe", installed.servesWithKey)
        assertFalse("aucun identifiant à demander", installed.hasServePassword)
    }

    @Test
    fun `un lien sans cle n'invente pas de Dashboard`() {
        val code = SetupCode(apiURL = "https://bare.example")

        val installed = code.toServerConfig("id")

        assertEquals("", installed.serveBaseUrl)
    }
}
