package com.onyx.app.data.net

import com.onyx.app.data.model.ServerConfig
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * « Repartir à zéro » : le chat du bot est **archivé**, pas supprimé.
 *
 * Miroir du `sessions.setArchived` de la référence (`BotsViewModel.startFresh`) : la route est le
 * `PATCH api/sessions/{id}` du registre, avec `{"archived": true}` — la même que « Archiver » d'une
 * conversation. C'est ce qui fait que l'ancien fil **reste dans l'historique**, entier : sans cette
 * route (une hypothèse, pas une réponse), l'entrée ne serait pas dessinée du tout.
 */
class BotArchiveRouteTest {

    @Test
    fun `repartir a zero archive le chat du bot par la route du registre`() = runBlocking {
        val calls = mutableListOf<Recorded>()
        val config = config()
        val client = ServeClient(config, client(calls))
        ChatSessionActions(ServeRpc(config), client).archive("bot-chat-scout")

        val call = calls.single { it.method == "PATCH" }
        assertEquals("/api/sessions/bot-chat-scout", call.path)
        assertEquals("""{"archived":true}""", call.body)
        assertTrue("aucun profil en plus : la route ne prend que l'identifiant", call.query.isEmpty())
    }

    @Test
    fun `desarchiver la meme route, avec archived false`() = runBlocking {
        val calls = mutableListOf<Recorded>()
        val config = config()
        ChatSessionActions(ServeRpc(config), ServeClient(config, client(calls))).archive("s-1", archived = false)

        val call = calls.single { it.method == "PATCH" }
        assertEquals("/api/sessions/s-1", call.path)
        assertEquals("""{"archived":false}""", call.body)
    }

    // MARK: - Outils

    private fun config() = ServerConfig(
        id = "test",
        serveBaseUrl = "https://hermes.test",
        serveUsername = "user",
        servePassword = "x",
    )

    private fun client(calls: MutableList<Recorded>): OkHttpClient = OkHttpClient.Builder().addInterceptor { chain ->
        val request = chain.request()
        val sent = request.body?.let { payload -> Buffer().also { payload.writeTo(it) }.readUtf8() }
        val query = request.url.queryParameterNames.associateWith { key -> request.url.queryParameter(key).orEmpty() }
        calls += Recorded(request.method, request.url.encodedPath, sent, query)
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("test")
            .body("{}".toResponseBody("application/json; charset=utf-8".toMediaType()))
            .build()
    }.build()

    private data class Recorded(
        val method: String,
        val path: String,
        val body: String?,
        val query: Map<String, String>,
    )
}
