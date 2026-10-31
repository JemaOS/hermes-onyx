package com.onyx.app.voice

import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * **Le locuteur de la réplique, tel que la session le passe au lecteur.**
 *
 * C'est la moitié amont du défaut : le fil, le mode vocal et la relance demandaient une lecture sans
 * jamais dire **de qui** étaient les mots, si bien que `SpeechOutput` retombait sur la voix d'Onyx —
 * le réglage « Voix » du bot n'avait aucun effet. La session, elle, sait porter ce profil
 * (`speak(text, listenAfterSpeech, speaker)` → `reply.speak(text, langage, speaker)`) : ce test
 * l'exerce pour de vrai, avec la vraie [VoiceSession] et un lecteur qui enregistre ce qu'il reçoit.
 */
class BotVoiceTurnTest {

    /** Le lecteur : il retient le texte **et** le locuteur qu'on lui donne, et finit tout de suite. */
    private class RecordingReply : SpeechReply {
        private val _isSpeaking = MutableStateFlow(false)
        override val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

        private val _level = MutableStateFlow(0f)
        override val replyLevel: StateFlow<Float> = _level.asStateFlow()

        override var onFinished: (() -> Unit)? = null
        override val earRouting: Boolean = false

        /** (texte, locuteur) de chaque réplique confiée au lecteur. */
        val spoken = CopyOnWriteArrayList<Pair<String, String?>>()

        override fun setLanguage(tag: String) = Unit
        override fun setEarRouting(atEar: Boolean) = Unit

        override fun speak(text: String, languageTag: String?, speaker: String?) {
            spoken += text to speaker
        }

        override fun pause() = Unit
        override fun resume(): Boolean = false
        override fun stop() = Unit
        override fun shutdown() = Unit
    }

    private class SilentEngine : SpeechInput {
        private val _state = MutableStateFlow(SpeechState())
        override val state: StateFlow<SpeechState> = _state.asStateFlow()
        override var offlineWanted: Boolean = false
        override val answered: Boolean = false
        override val frameCount: Int = 0
        override val canRetry: Boolean = true
        override val attempts: Int = 0
        override val isAvailable: Boolean = true
        override val prefersOffline: Boolean = false
        override val requestedLocale: String = "fr-FR"
        override var onFinished: (() -> Unit)? = null
        override fun start(fresh: Boolean) = Unit
        override fun stop() = Unit
        override fun cancel() = Unit
        override fun release() = Unit
    }

    private class NoRoute : EarRouteController {
        override fun apply(output: EarOutput) = Unit
        override fun restore() = Unit
        override fun hasExternalRoute(): Boolean = false
        override fun release() = Unit
    }

    private class NeverFaceDown : FaceDownSensing {
        override fun start() = Unit
        override fun stop() = Unit
        override fun isFaceDown(): Boolean = false
    }

    private class NoProximity : ProximityWatcher {
        override fun start() = Unit
        override fun stop() = Unit
        override val isAvailable: Boolean = false
    }

    private fun session(scope: CoroutineScope, reply: SpeechReply) = VoiceSession(
        engine = SilentEngine(),
        reply = reply,
        audioRoute = NoRoute(),
        faceDown = NeverFaceDown(),
        // Sans contexte Android, la session **exige** son routeur (« une session sans Android reçoit
        // son routeur ») : un routeur inerte, comme le chemin du haut-parleur de la capture.
        earRouter = ProximityEarRouter(watcher = NoProximity(), route = NoRoute(), enabled = { false }),
        scope = scope,
        localeTag = "fr-FR",
    )

    @Test
    fun `la fin du tour lit la reponse de la voix de son bot`() {
        val scope = CoroutineScope(Dispatchers.Default)
        try {
            val reply = RecordingReply()
            val session = session(scope, reply)

            // Le fil d'une conversation de bot : le profil part avec le texte.
            session.finishTurn("Bonjour, c'est Quill.", speaker = "quill")
            assertEquals(listOf("Bonjour, c'est Quill." to "quill"), reply.spoken.toList())
            session.stop()

            // La relance du mode vocal : la même voix, celle du bot.
            session.speak("Je le relis.", listenAfterSpeech = false, speaker = "quill")
            assertEquals("la relance garde la voix du bot", "quill", reply.spoken.last().second)
            session.stop()

            // Une conversation simple (aucun bot) : la voix d'Onyx — « Automatique » d'avant.
            reply.spoken.clear()
            session.finishTurn("Bonjour, c'est Onyx.")
            assertEquals(1, reply.spoken.size)
            assertNull("sans bot, c'est la voix d'Onyx", reply.spoken.last().second)
        } finally {
            scope.cancel()
        }
    }
}
