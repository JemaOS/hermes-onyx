package com.onyx.app.voice

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * **La requête de synthèse réelle, contre le vrai service vocal.**
 *
 * Même code que la lecture (`KokoroSpeechClient`, tel quel) et même résolution de voix
 * (`BotVoices.kokoroFor`, celle qu'appelle `SpeechOutput.startRemote`) — mais l'adresse est celle du
 * **service vocal de la machine**, et le journal du service dit alors, pour chaque synthèse, la voix
 * qu'elle a réellement reçue.
 *
 * Hors ligne (par défaut, sans `ONYX_LIVE_VOICE_BASE`), l'épreuve est **sautée** : la suite reste
 * hermétique, aucun test ne dépend du réseau.
 *
 *     ONYX_LIVE_VOICE_BASE=http://127.0.0.1:8880 \
 *     ONYX_LIVE_VOICE_TOKEN="$(cat /home/john/kokoro-tts/auth-proxy/token)" \
 *     gradle :core:testDebugUnitTest --tests '*BotVoiceGatewayLiveTest'
 *
 * Le jeton n'est **jamais** écrit ni affiché : il ne voyage que dans l'en-tête `Authorization`.
 */
class BotVoiceGatewayLiveTest {

    private fun voiceOf(profile: String?): String = BotVoices.kokoroFor(
        forProfile = profile,
        picked = mapOf(
            "quill" to BotVoice(kokoro = "fm_piper-pierre"),
            "scout" to BotVoice(kokoro = "ff_piper-jessica"),
        ),
        main = "ff_siwis",
        names = listOf("quill", "scout", "fixit"),
    )

    @Test
    fun `chaque bot envoie sa voix au service vocal, et Automatique la voix globale`() = runBlocking {
        val base = System.getenv("ONYX_LIVE_VOICE_BASE").orEmpty()
        assumeTrue("service vocal absent (ONYX_LIVE_VOICE_BASE non posé)", base.isNotEmpty())
        val token = System.getenv("ONYX_LIVE_VOICE_TOKEN").orEmpty()

        val client = KokoroSpeechClient(token = token)
        val cases = listOf("quill" to "fm_piper-pierre", "scout" to "ff_piper-jessica", "fixit" to "ff_siwis")
        for ((profile, expected) in cases) {
            val voice = voiceOf(profile)
            assertEquals("la voix visée pour $profile", expected, voice)
            val audio = client.speak("Bonjour, c'est $profile.", base, voice, speed = 1.0f)
            assertTrue("$profile : le service a rendu de l'audio (${audio.bytes.size} octets)", audio.bytes.isNotEmpty())
            println("SYNTHESE $profile -> voice=$voice ${audio.bytes.size} octets ${audio.mimeType}")
        }
    }
}
