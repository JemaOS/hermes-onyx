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
import org.junit.Test

class ServeProfilesApiTest {
    @Test
    fun `l aperçu et la date de la session du bot alimentent sa ligne`() = runBlocking {
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val body = if (request.url.encodedPath.endsWith("/api/sessions")) {
                "{}"
            } else {
                """{"profiles":[{"name":"kilo","ui_meta":{"onyx":{"face":"1:round:round:none:none:4:0:0"}},"canonical_session":{"resolved_id":"bot-chat","preview":"Latest answer","last_active":1761324000.6719236}}]}"""
            }
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("test")
                .body(body.toResponseBody("application/json; charset=utf-8".toMediaType()))
                .build()
        }.build()
        val config = ServerConfig(
            id = "test",
            serveBaseUrl = "https://hermes.test",
            serveUsername = "user",
            servePassword = "x",
        )

        val bot = ServeProfilesApi(ServeClient(config, http)).profiles().single()

        assertEquals("bot-chat", bot.chatId)
        assertEquals("Latest answer", bot.preview)
        assertEquals(1_761_324_000_671L, bot.lastActive)
        assertEquals("1:round:round:none:none:4:0:0", bot.faceCode)
    }
}
