package com.onyx.app.data.net

import com.onyx.app.data.model.ServerConfig
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Vérifie les deux comportements du Dashboard dont l'éditeur de contexte dépend. */
class ServeFilesApiTest {
    @Test
    fun `la maison demandee est celle du profil choisi sans tenir compte de la casse`() = runBlocking {
        val api = filesApi { request ->
            request.respond(
                200,
                """{"profiles":[{"name":"default","path":"/opt/default","is_default":true},{"name":"Scout","path":"/opt/scout","is_default":false}]}""",
            )
        }

        assertEquals("/opt/scout", api.home("sCoUt"))
        assertNull(api.home("absent"))
        assertEquals("/opt/default", api.home(null))
    }

    @Test
    fun `un 404 de lecture signifie que le fichier est a creer`() = runBlocking {
        val missing = filesApi { it.respond(404, "not found") }
        val present = filesApi { it.respond(200, """{"text":"contenu","truncated":false,"binary":false}""") }

        assertNull(missing.readTextOrNull("~/.hermes/MEMORY.md"))
        assertEquals("contenu", present.readTextOrNull("~/.hermes/MEMORY.md"))
    }
}

private fun filesApi(respond: (Request) -> Response): ServeFilesApi {
    val http = OkHttpClient.Builder().addInterceptor { chain ->
        val request = chain.request()
        if (request.url.encodedPath.endsWith("/api/sessions")) {
            request.respond(200, "{}")
        } else {
            respond(request)
        }
    }.build()
    val config = ServerConfig(
        id = "test",
        serveBaseUrl = "https://hermes.test",
        serveUsername = "user",
        servePassword = "x",
    )
    return ServeFilesApi(ServeClient(config, http))
}

private fun Request.respond(code: Int, body: String): Response = Response.Builder()
    .request(this)
    .protocol(Protocol.HTTP_1_1)
    .code(code)
    .message("test")
    .body(body.toResponseBody("application/json; charset=utf-8".toMediaType()))
    .build()
