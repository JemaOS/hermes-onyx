package com.onyx.app.voice

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KokoroSpeechClientTest {
    @Test
    fun `les voix et la synthese utilisent les routes et le corps OpenAI de Kokoro`() = runBlocking {
        val pcm = byteArrayOf(1, 0, 2, 0)
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""{"voices":[{"id":"bm_george"},{"id":"af_heart"}]}"""),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "audio/pcm")
                .setBody(okio.Buffer().write(pcm)),
        )
        server.start()
        try {
            val base = server.url("/").toString().trimEnd('/')
            val client = KokoroSpeechClient()

            assertEquals(listOf("af_heart", "bm_george"), client.voiceIds(base))
            val voicesRequest = server.takeRequest()
            assertEquals("GET", voicesRequest.method)
            assertEquals("/v1/audio/voices", voicesRequest.path)

            val audio = client.speak("Hello", base, "af_heart", speed = 1.25f)

            assertEquals("audio/pcm", audio.mimeType)
            assertEquals(pcm.toList(), audio.bytes.toList())
            val speechRequest = server.takeRequest()
            assertEquals("POST", speechRequest.method)
            assertEquals("/v1/audio/speech", speechRequest.path)
            val sent = Json.parseToJsonElement(speechRequest.body.readUtf8()).jsonObject
            assertEquals("kokoro", sent.getValue("model").jsonPrimitive.content)
            assertEquals("Hello", sent.getValue("input").jsonPrimitive.content)
            assertEquals("af_heart", sent.getValue("voice").jsonPrimitive.content)
            assertEquals(1.25, sent.getValue("speed").jsonPrimitive.double, 0.0001)
            assertEquals("pcm", sent.getValue("response_format").jsonPrimitive.content)
            assertTrue(sent.getValue("stream").jsonPrimitive.boolean)
        } finally {
            server.shutdown()
        }
    }
}
