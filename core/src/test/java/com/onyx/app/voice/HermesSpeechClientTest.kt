package com.onyx.app.voice

import com.onyx.app.data.model.ServerConfig
import java.util.Base64
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test

class HermesSpeechClientTest {
    @Test
    fun `le test Hermes ouvre la session puis lit le data URL et le provider`() = runBlocking {
        val bytes = byteArrayOf(9, 8, 7)
        val dataUrl = "data:audio/mpeg;base64," + Base64.getEncoder().encodeToString(bytes)
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""{"data_url":"$dataUrl","mime_type":"audio/mpeg","provider":"elevenlabs"}"""),
        )
        server.start()
        try {
            val config = ServerConfig(
                id = "test",
                serveBaseUrl = server.url("/").toString(),
                serveUsername = "john",
                servePassword = "secret",
                hermesProfile = "work",
            )

            val audio = HermesSpeechClient(config).speak("Read this")

            assertEquals("audio/mpeg", audio.mimeType)
            assertEquals("elevenlabs", audio.provider)
            assertEquals(bytes.toList(), audio.bytes.toList())
            assertEquals("/api/sessions?limit=1", server.takeRequest().path)
            val login = server.takeRequest()
            assertEquals("/auth/password-login", login.path)
            val loginBody = Json.parseToJsonElement(login.body.readUtf8()).jsonObject
            assertEquals("basic", loginBody.getValue("provider").jsonPrimitive.content)
            assertEquals("john", loginBody.getValue("username").jsonPrimitive.content)
            val speech = server.takeRequest()
            assertEquals("/api/audio/speak?profile=work", speech.path)
            val speechBody = Json.parseToJsonElement(speech.body.readUtf8()).jsonObject
            assertEquals("Read this", speechBody.getValue("text").jsonPrimitive.content)
        } finally {
            server.shutdown()
        }
    }
}
