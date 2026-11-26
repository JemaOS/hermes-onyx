package com.onyx.app.data.net

import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.Transport
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Les routes du coffre, **contre un vrai serveur HTTP** (MockWebServer, la convention du dépôt).
 *
 * Ce que ce fichier tient, et qui n'est pas décoratif :
 *
 * 1. **les chemins et les corps sont ceux du greffon** — `api/plugins/onyx-vault/vault.list`
 *    avec son `?profile=`, `vault.put` et `vault.revoke` en `POST` JSON ;
 * 2. **le profil part toujours** : le greffon refuse une requête sans profil, donc `default` est
 *    envoyé quand l'app n'en nomme pas d'autre ;
 * 3. **la valeur ne ressort par aucun chemin** : la réponse de `vault.list` porte une valeur dans
 *    ce test, et le modèle de lecture ne doit ni la garder, ni la rendre dans son `toString()`, ni
 *    avoir un champ pour elle.
 */
class VaultApiTest {

    private lateinit var server: MockWebServer

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun stop() {
        server.shutdown()
    }

    @Test
    fun `la liste lit les metadonnees du profil et jamais la valeur`() = runBlocking {
        answering(
            """
            {"profile":"scout","count":2,"secrets":[
              {"name":"openai","fingerprint":"a1b2","updated_at":"2026-11-03T09:12:00Z",
               "used_by":["recherche","resume"],"value":"sk-la-valeur-interdite"},
              {"name":"smtp","fingerprint":"****","updated_at":"2026-10-30T18:40:00Z","used_by":[]}
            ]}
            """.trimIndent(),
        )

        val secrets = VaultApi(client(profile = "scout")).list()

        assertEquals(listOf("openai", "smtp"), secrets.map { it.name })
        assertEquals("a1b2", secrets[0].fingerprint)
        assertEquals("2026-11-03T09:12:00Z", secrets[0].updatedAt)
        assertEquals(listOf("recherche", "resume"), secrets[0].usedBy)
        assertTrue("un secret sans usage reste lisible", secrets[1].usedBy.isEmpty())

        // La valeur était dans le corps : elle ne doit apparaître nulle part dans ce qu'on garde.
        val dump = secrets.joinToString("\n") { it.toString() }
        assertFalse("le toString() d'un secret ne porte jamais une valeur", dump.contains("sk-la-valeur-interdite"))
        assertFalse("le modèle de lecture n'a aucun champ `value`", "value" in VaultSecret::class.java.declaredFields.map { it.name })
        VaultSecret::class.java.declaredFields.forEach { field ->
            assertFalse(
                "le champ « ${field.name} » ne doit pas transporter la valeur",
                field.name.equals("value", ignoreCase = true),
            )
        }

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/${VaultApi.LIST}", request.requestUrl?.encodedPath)
        assertEquals("scout", request.requestUrl?.queryParameter("profile"))
    }

    @Test
    fun `l ecriture envoie le corps du greffon et rend l empreinte sans la valeur`() = runBlocking {
        answering(
            """
            {"profile":"scout","name":"openai","fingerprint":"a1b2","updated_at":"2026-11-03T10:00:00Z",
             "used_by":["recherche"],"created":true}
            """.trimIndent(),
        )

        val written = VaultApi(client(profile = "scout"))
            .put(name = "openai", value = "sk-la-valeur-interdite", usedBy = listOf("recherche"))

        assertEquals("openai", written.name)
        assertEquals("a1b2", written.fingerprint)
        assertEquals(listOf("recherche"), written.usedBy)
        assertTrue(written.created)
        assertFalse(
            "le résultat d'une écriture ne porte pas la valeur",
            written.toString().contains("sk-la-valeur-interdite"),
        )
        assertFalse("aucun champ `value` dans le résultat", "value" in VaultWrite::class.java.declaredFields.map { it.name })

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/${VaultApi.PUT}", request.requestUrl?.encodedPath)
        val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("scout", body["profile"]?.jsonPrimitive?.content)
        assertEquals("openai", body["name"]?.jsonPrimitive?.content)
        assertEquals("sk-la-valeur-interdite", body["value"]?.jsonPrimitive?.content)
        assertEquals(listOf("recherche"), (body["used_by"] as kotlinx.serialization.json.JsonArray).map { it.jsonPrimitive.content })
    }

    @Test
    fun `une ecriture sans usage n envoie pas de used_by et ne l efface pas`() = runBlocking {
        answering("""{"profile":"scout","name":"smtp","fingerprint":"zz99","created":false}""")

        VaultApi(client(profile = "scout")).put(name = "smtp", value = "mot-de-passe")

        val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertFalse(
            "sans usage déclaré, la clé `used_by` ne part pas : le serveur garde la liste existante",
            body.containsKey("used_by"),
        )
    }

    @Test
    fun `la revocation est un POST idempotent qui dit si une ligne est tombee`() = runBlocking {
        answering("""{"profile":"scout","name":"smtp","revoked":false}""")

        val revoked = VaultApi(client(profile = "scout")).revoke("smtp")

        assertEquals("smtp", revoked.name)
        assertFalse("un secret déjà absent n'est pas un succès", revoked.revoked)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/${VaultApi.REVOKE}", request.requestUrl?.encodedPath)
        val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("scout", body["profile"]?.jsonPrimitive?.content)
        assertEquals("smtp", body["name"]?.jsonPrimitive?.content)
    }

    @Test
    fun `le profil par defaut part quand l app n en nomme aucun`() = runBlocking {
        answering("""{"profile":"default","count":0,"secrets":[]}""")

        val secrets = VaultApi(client(profile = null)).list()

        assertTrue(secrets.isEmpty())
        assertEquals("default", server.takeRequest().requestUrl?.queryParameter("profile"))
    }

    @Test
    fun `la sante du coffre se lit sans profil`() = runBlocking {
        answering("""{"ok":false,"code":"vault_unavailable","detail":"cle absente"}""")

        val health = VaultApi(client(profile = "scout")).health()

        assertFalse(health.ok)
        assertEquals("vault_unavailable", health.code)
        assertEquals("cle absente", health.detail)
        val request = server.takeRequest()
        assertEquals("/${VaultApi.HEALTH}", request.requestUrl?.encodedPath)
        assertNull("la route de santé ne prend pas de profil", request.requestUrl?.queryParameter("profile"))
    }

    // MARK: - Outillage

    private fun config(profile: String?) = ServerConfig(
        id = "coffre",
        serveBaseUrl = server.url("/").toString().trimEnd('/'),
        apiKey = "cle-1",
        transport = Transport.HERMES_SERVE,
        hermesProfile = profile.orEmpty(),
    )

    private fun client(profile: String?) = ServeClient(config(profile))

    private fun answering(body: String, code: Int = 200) {
        server.enqueue(MockResponse().setResponseCode(code).setBody(body))
    }
}
