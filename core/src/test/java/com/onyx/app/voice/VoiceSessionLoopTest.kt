package com.onyx.app.voice

import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **La boucle vocale complète, rejouée sans micro.**
 *
 * C'est ce que l'émulateur ne pouvait pas prouver : un émulateur n'a pas de micro, donc la seule
 * chose observable était un moteur qui **démarre**. Ici, le moteur d'écoute est
 * [ScenarioSpeechInput] — alimenté par le **WAV français** des ressources — et le lecteur est un
 * faux : la session tourne donc **pour de vrai**, avec ses propres coroutines, sa propre
 * endpointing et ses propres décisions.
 *
 * Ce qui est prouvé, dans l'ordre du tour :
 *  1. l'écoute s'ouvre (mains libres) ;
 *  2. **le niveau bouge** — les points ne sont pas plats ;
 *  3. la fin de parole est détectée **toute seule** (endpointing), et la question part **une fois** ;
 *  4. la phase passe en RÉFLEXION, la réponse se lit (PARLE) ;
 *  5. l'écoute **reprend** ;
 *  6. un mot d'arrêt français ferme l'écoute **sans rien envoyer** ;
 *  7. le plafond d'échecs du moteur arrête l'écoute **en le disant** (message, pas un code) ;
 *  8. et un moteur qui tourne sans rien recevoir **le dit** (« on n'entend rien »).
 *
 * Aucune valeur n'est simulée à la place de la session : on l'observe par ses flux publics.
 */
class VoiceSessionLoopTest {

    private fun wav(): ByteArray = requireNotNull(
        VoiceSessionLoopTest::class.java.getResourceAsStream("/voice/phrase-fr.wav"),
    ).readBytes()

    private fun await(what: String, timeoutMs: Long = 20_000, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (condition()) return
            Thread.sleep(20)
        }
        throw AssertionError("attendu (${timeoutMs} ms) : $what")
    }

    /** Le vrai moteur d'écoute, celui du WAV — la phrase de la maquette. */
    private fun phraseEngine(scope: CoroutineScope, stepMs: Long = 40) = ScenarioSpeechInput(
        scenarioFromWav(wav(), transcript = QUESTION, leadInMs = 300, tailMs = 1_700, stepMs = stepMs),
        scope,
        stepMs = stepMs,
    )

    private fun session(
        scope: CoroutineScope,
        engine: SpeechInput,
        reply: SpeechReply,
    ) = VoiceSession(
        engine = engine,
        reply = reply,
        audioRoute = NoRoute(),
        faceDown = NeverFaceDown(),
        // Un routeur « à l'oreille » inerte : le capteur n'existe pas dans un test, et le réglage
        // est éteint — c'est le chemin du haut-parleur, celui de la capture.
        earRouter = ProximityEarRouter(
            watcher = NoProximity(),
            route = NoRoute(),
            enabled = { false },
        ),
        scope = scope,
        localeTag = "fr-FR",
    )

    // MARK: - 1 à 5. Le tour complet

    @Test
    fun `silence, phrase, question, reflexion, reponse, reprise - sans micro`() {
        val scope = CoroutineScope(Dispatchers.Default)
        try {
            val reply = FakeReply(scope)
            val session = session(scope, phraseEngine(scope), reply)
            val asked = CopyOnWriteArrayList<String>()
            session.onQuestion = { asked += it }
            session.autoRelisten = true

            // 1. L'écoute s'ouvre sur le geste (mains libres).
            session.toggleHandsFree()
            await("l'écoute s'ouvre") { session.phase.value == VoicePhase.LISTENING }

            // 2. Le niveau bouge : c'est le WAV qui le donne, et les points le suivront.
            await("le niveau monte") { session.listenLevel.value > 0.2f }

            // 3. La fin de parole est détectée par l'endpointing, et la question part **une fois**.
            await("la question part") { asked.isNotEmpty() }
            assertEquals(listOf(QUESTION), asked.toList())
            assertEquals("on réfléchit au tour", VoicePhase.THINKING, session.phase.value)
            assertEquals("ce qui a été entendu est à l'écran", QUESTION, session.heard.value)

            // 4. La réponse se lit.
            session.finishTurn(REPLY)
            await("la réponse se lit") { reply.spoken.isNotEmpty() }
            assertEquals(listOf(REPLY), reply.spoken.toList())

            // 5. L'écoute reprend toute seule (mains libres).
            await("l'écoute reprend") { session.phase.value == VoicePhase.LISTENING }

            session.stop()
            assertEquals(VoicePhase.IDLE, session.phase.value)
            assertEquals("les points retombent au repos", 0f, session.listenLevel.value, 0.001f)
        } finally {
            scope.cancel()
        }
    }

    // MARK: - 6. Un mot d'arrêt français

    @Test
    fun `un mot d arret francais ferme l ecoute sans rien envoyer`() {
        val scope = CoroutineScope(Dispatchers.Default)
        try {
            val frames = (0 until 6).map {
                HeardFrame(atMs = it * 40L, level = 0.4f, transcript = "arrête d'écouter")
            } + HeardFrame(atMs = 240L, level = 0f, transcript = "arrête d'écouter", isFinal = true)
            val session = session(scope, ScenarioSpeechInput(frames, scope, stepMs = 40), FakeReply(scope))
            val asked = CopyOnWriteArrayList<String>()
            session.onQuestion = { asked += it }

            session.startListening()
            await("l'écoute s'ouvre") { session.phase.value == VoicePhase.LISTENING }
            await("l'écoute se ferme sur le mot d'arrêt") { session.phase.value == VoicePhase.IDLE }

            assertTrue("rien n'est envoyé à l'agent", asked.isEmpty())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `les phrases d arret par defaut sont francaises et reconnues`() {
        assertTrue("arrête d'écouter", "arrête d'écouter" in BUILT_IN_STOP_PHRASES)
        assertTrue("c'est tout — avec l'apostrophe du clavier", "c'est tout" in BUILT_IN_STOP_PHRASES)
        assertTrue(
            "arrête d'écouter — et sa forme comparable, celle que le moteur écrit",
            comparableStopPhrase("arrête d'écouter") in BUILT_IN_STOP_PHRASES,
        )
        assertTrue(
            "c'est tout — la forme comparable, ponctuation retirée",
            comparableStopPhrase("C’est tout.") in BUILT_IN_STOP_PHRASES,
        )
        assertTrue("les phrases anglaises de la référence restent", "stop listening" in BUILT_IN_STOP_PHRASES)
        // L'apostrophe droite et l'apostrophe typographique mènent à la **même** phrase : c'est ce
        // qui rend « c'est tout » reconnaissable quel que soit le caractère écrit par le moteur.
        assertEquals(normalizeStopPhrase("c'est tout"), normalizeStopPhrase("C’est tout."))
        assertEquals("c est tout", normalizeStopPhrase("C’est tout."))
    }

    // MARK: - 7. Le plafond d'échecs

    @Test
    fun `le plafond d echecs arrete l ecoute en le disant`() {
        val scope = CoroutineScope(Dispatchers.Default)
        try {
            // Le moteur refuse trois fois d'affilée : une session, puis deux reprises.
            val frames = listOf(
                HeardFrame(
                    atMs = 0L,
                    level = 0f,
                    failed = true,
                    stopDecision = EngineStopDecision.RETRY,
                    problem = "The engine's recogniser is busy. Tap the orb and try again.",
                ),
            )
            val engine = ScenarioSpeechInput(frames, scope, stepMs = 40)
            val session = session(scope, engine, FakeReply(scope))
            val asked = CopyOnWriteArrayList<String>()
            session.onQuestion = { asked += it }

            session.startListening()
            await("le moteur a refusé trois fois", timeoutMs = 25_000) { session.failure.value != null }

            assertNotNull("un message est porté à l'écran", session.failure.value)
            assertTrue("ce n'est pas un code brut", (session.failure.value ?: "").contains("Tap the orb"))
            assertEquals("l'écoute est close, pas laissée à mentir", VoicePhase.IDLE, session.phase.value)
            assertTrue("rien n'a été envoyé", asked.isEmpty())
            assertTrue("le moteur a bien été relancé jusqu'au plafond", engine.attempts >= MAX_ENGINE_ATTEMPTS)
        } finally {
            scope.cancel()
        }
    }

    // MARK: - 8. Le moteur qui tourne sans rien recevoir

    @Test
    fun `un moteur qui tourne sans rien recevoir le dit`() {
        val scope = CoroutineScope(Dispatchers.Default)
        try {
            val frames = (0 until 300).map { HeardFrame(atMs = it * 60L, level = 0f) }
            val session = session(scope, ScenarioSpeechInput(frames, scope, stepMs = 60), FakeReply(scope))

            session.startListening()
            await("l'écoute s'ouvre") { session.phase.value == VoicePhase.LISTENING }
            // Le défaut rapporté : les points ne bougent pas et l'écran ne dit rien. Les deux
            // doivent désormais être vrais ensemble — points plats **et** alerte affichée.
            await("l'alerte « on n'entend rien » s'affiche", timeoutMs = 12_000) { session.noSound.value }
            assertEquals("les points restent plats — c'est la vérité", 0f, session.listenLevel.value, 0.001f)
            assertTrue("et le journal en garde une ligne courte", VoiceDiagnostics.lines().any { it.contains("on n'entend rien") })
        } finally {
            scope.cancel()
        }
    }

    private companion object {
        const val QUESTION = "Quel temps fait-il demain à Lyon ?"
        const val REPLY = "Demain à Lyon, ciel dégagé et dix-huit degrés l'après-midi."
    }
}

/** La sortie audio d'un test : elle ne touche à rien. */
private class NoRoute : EarRouteController {
    override fun apply(output: EarOutput) = Unit
    override fun restore() = Unit
    override fun hasExternalRoute(): Boolean = false
    override fun release() = Unit
}

/** Aucun capteur de proximité. */
private class NoProximity : ProximityWatcher {
    override fun start() = Unit
    override fun stop() = Unit
    override val isAvailable: Boolean = false
}

/** Le téléphone n'est jamais posé face contre la table. */
private class NeverFaceDown : FaceDownSensing {
    override fun start() = Unit
    override fun stop() = Unit
    override fun isFaceDown(): Boolean = false
}

/**
 * Le lecteur de test : il « dit » la réplique pendant [durationMs], puis rend la main — c'est ce
 * qui fait passer la phase de PARLE à PRÊT et rouvre l'écoute.
 */
private class FakeReply(
    private val scope: CoroutineScope,
    private val durationMs: Long = 300,
) : SpeechReply {

    private val _isSpeaking = MutableStateFlow(false)
    override val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _level = MutableStateFlow(0f)
    override val replyLevel: StateFlow<Float> = _level.asStateFlow()

    override var onFinished: (() -> Unit)? = null

    private var atEar = false
    override val earRouting: Boolean get() = atEar

    /** Ce qui a été réellement « dit » : c'est la preuve que la réponse est passée par là. */
    val spoken = CopyOnWriteArrayList<String>()

    override fun setLanguage(tag: String) = Unit

    override fun setEarRouting(atEar: Boolean) {
        this.atEar = atEar
    }

    override fun speak(text: String, languageTag: String?, speaker: String?) {
        spoken += text
        _isSpeaking.value = true
        scope.launch {
            val steps = (durationMs / 60).toInt().coerceAtLeast(1)
            repeat(steps) { i ->
                _level.value = 0.2f + 0.4f * (i % 3) / 3f
                delay(60)
            }
            _level.value = 0f
            _isSpeaking.value = false
            onFinished?.invoke()
        }
    }

    override fun pause() = Unit

    override fun resume(): Boolean = _isSpeaking.value

    override fun stop() {
        _level.value = 0f
        _isSpeaking.value = false
    }

    override fun shutdown() = stop()
}
