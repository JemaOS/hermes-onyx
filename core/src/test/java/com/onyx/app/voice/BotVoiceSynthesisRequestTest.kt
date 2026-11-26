package com.onyx.app.voice

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * **La requête de synthèse, interceptée.** Deux bots réglés sur deux voix différentes : chacune des
 * deux requêtes POST réellement envoyées à `/v1/audio/speech` porte **sa** voix — pas celle d'Onyx,
 * pas celle de l'autre bot. Et « Automatique » (aucune voix réglée) reprend la voix globale.
 *
 * La voix testée est **celle que la lecture envoie** : `BotVoices.kokoroFor`, la fonction que
 * `SpeechOutput.startRemote` appelle — la table n'est donc pas reconstruite ici, elle est exercée, et
 * le corps de la requête est lu sur le service (MockWebServer) qui l'a reçue.
 */
class BotVoiceSynthesisRequestTest {

    /** Le corps de la requête que `KokoroSpeechClient` vient de poster, tel que le service le voit. */
    private fun voiceOf(request: okhttp3.mockwebserver.RecordedRequest): String =
        Json.parseToJsonElement(request.body.readUtf8()).jsonObject.getValue("voice").jsonPrimitive.content

    private fun serverWithAudio(requests: Int): MockWebServer {
        val server = MockWebServer()
        repeat(requests) {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .addHeader("Content-Type", "audio/pcm")
                    .setBody(okio.Buffer().write(byteArrayOf(1, 0, 2, 0))),
            )
        }
        server.start()
        return server
    }

    @Test
    fun `deux bots, deux voix - chaque requete porte la sienne, et Automatique la voix globale`() = runBlocking {
        val server = serverWithAudio(requests = 3)
        try {
            val base = server.url("/").toString().trimEnd('/')
            val client = KokoroSpeechClient()

            // Ce qui est réglé : Quill lit de Pierre, Scout de Jessica, et Fix-it n'a rien réglé.
            val picked = mapOf(
                "quill" to BotVoice(kokoro = "fm_piper-pierre"),
                "scout" to BotVoice(kokoro = "ff_piper-jessica"),
            )
            val names = listOf("quill", "scout", "fixit")
            // La voix d'Onyx (Réglages → Voix → Kokoro) : ce qu'un bot sans réglage reçoit.
            // La lecture ne reçoit **pas** la liste du serveur (`OnyxApp` → `SpeechOutput.configure`
            // sans `kokoroVoices`) : « Automatique » garde donc la voix globale, comme demandé.
            val onyxVoice = "ff_siwis"

            fun speakAs(profile: String?): String =
                BotVoices.kokoroFor(profile, picked, onyxVoice, names = names)

            // 1. Quill — le fil de sa conversation.
            client.speak("Bonjour.", base, speakAs("quill"))
            // 2. Scout — le fil de sa conversation.
            client.speak("Bonjour.", base, speakAs("scout"))
            // 3. « Automatique » : aucune voix réglée — la voix globale.
            client.speak("Bonjour.", base, speakAs("fixit"))

            assertEquals("Quill parle de sa voix", "fm_piper-pierre", voiceOf(server.takeRequest()))
            assertEquals("Scout parle de sa voix", "ff_piper-jessica", voiceOf(server.takeRequest()))
            assertEquals("Automatique reprend la voix globale", onyxVoice, voiceOf(server.takeRequest()))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `un bot regle sur l agent principal, et une conversation simple, gardent la voix globale`() = runBlocking {
        val server = serverWithAudio(requests = 3)
        try {
            val base = server.url("/").toString().trimEnd('/')
            val client = KokoroSpeechClient()
            val picked = mapOf(
                "quill" to BotVoice(kokoro = "fm_piper-pierre"),
                "scout" to BotVoice(sameAsMain = true),
            )
            val onyxVoice = "ff_siwis"

            fun speakAs(profile: String?): String =
                BotVoices.kokoroFor(profile, picked, onyxVoice, names = listOf("quill", "scout"))

            client.speak("Bonjour.", base, speakAs("scout"))
            client.speak("Bonjour.", base, speakAs("default"))
            client.speak("Bonjour.", base, speakAs(null))

            assertEquals("« Identique à l'agent principal »", onyxVoice, voiceOf(server.takeRequest()))
            assertEquals("le profil principal (default)", onyxVoice, voiceOf(server.takeRequest()))
            assertEquals("une conversation simple (aucun bot)", onyxVoice, voiceOf(server.takeRequest()))
        } finally {
            server.shutdown()
        }
    }
}
