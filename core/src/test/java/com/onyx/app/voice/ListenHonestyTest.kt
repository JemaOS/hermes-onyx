package com.onyx.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **Le verrou** : « l'écran peut-il rester muet alors que le moteur n'a jamais démarré ? »
 *
 * Le défaut rapporté au propriétaire : l'écran dit « À L'ÉCOUTE », les points de l'orbe ne bougent
 * pas, il parle — et rien ne se passe, sans le moindre message. La cause tenait en une ligne : le
 * seul signal d'alerte était un **seuil de niveau du micro** (`ENDPOINT_MINIMUM_LEVEL`). Un moteur
 * qui livre des mesures de niveau sans jamais rendre un mot gardait ce niveau au-dessus du seuil,
 * donc l'alerte ne s'allumait **jamais** — et rien ne vérifiait que « À L'ÉCOUTE » voulait dire
 * « le moteur a démarré ».
 *
 * Ces vérifications cassent si la décision revient au seul niveau, ou si l'écran peut rester sans
 * message pendant qu'un moteur muet tourne. Les décisions sont pures (donc jouées ici) ; le câblage
 * de la session est vérifié sur le texte, comme les autres lots.
 */
class ListenHonestyTest {

    private val session = File("src/main/java/com/onyx/app/voice/VoiceSession.kt").readText()
    private val engine = File("src/main/java/com/onyx/app/voice/SpeechEngine.kt").readText()
    private val policy = File("src/main/java/com/onyx/app/voice/ListenHonesty.kt").readText()

    // MARK: - Le verrou : jamais muet quand le moteur n'a pas démarré

    @Test
    fun `un moteur qui n a jamais demarre ne peut pas laisser l ecran muet`() {
        // À l'écoute, aucun mot, aucune trame, et **le moteur n'a jamais répondu** : l'app doit
        // avoir quelque chose à dire, et ce n'est pas le silence.
        val trouble = listenTrouble(
            phase = VoicePhase.LISTENING,
            heard = "",
            frames = 0,
            engineStarted = false,
            listeningForMs = ENGINE_START_GRACE_MS,
        )
        assertEquals(ListenTrouble.ENGINE_NEVER_STARTED, trouble)
        assertTrue("la panne porte un message, jamais un code seul", !trouble.message.isNullOrBlank())
        // Un instant plus tôt, on laisse au moteur le temps de répondre — l'alerte ne clignote pas.
        assertEquals(
            ListenTrouble.NONE,
            listenTrouble(VoicePhase.LISTENING, "", 0, engineStarted = false, listeningForMs = ENGINE_START_GRACE_MS - 1),
        )
    }

    @Test
    fun `des trames qui ne rendent aucun mot se disent aussi`() {
        // **Le cas rapporté** : le moteur tourne (il a répondu), le micro livre des mesures de
        // niveau, donc le seuil de niveau est franchi — et pourtant aucun mot n'arrive. L'ancienne
        // règle (niveau seul) ne disait rien ; celle-ci doit dire.
        assertEquals(
            ListenTrouble.NO_WORDS,
            listenTrouble(
                phase = VoicePhase.LISTENING,
                heard = "",
                frames = 240,
                engineStarted = true,
                listeningForMs = NO_WORDS_MS,
            ),
        )
        // Des trames mais aucun mot avant le délai : on laisse le moteur décoder.
        assertEquals(
            ListenTrouble.NONE,
            listenTrouble(VoicePhase.LISTENING, "", 240, engineStarted = true, listeningForMs = NO_WORDS_MS - 1),
        )
        // Le moteur a répondu mais **aucune trame** : c'est le micro qui ne donne rien.
        assertEquals(
            ListenTrouble.NO_FRAMES,
            listenTrouble(VoicePhase.LISTENING, "", 0, engineStarted = true, listeningForMs = NO_FRAMES_MS),
        )
    }

    @Test
    fun `un mot reconnu, ou une autre phase, ecarte toute alerte`() {
        // Des mots sont arrivés : le micro et le moteur fonctionnent, plus rien à signaler.
        assertEquals(
            ListenTrouble.NONE,
            listenTrouble(VoicePhase.LISTENING, "bonjour", 400, engineStarted = true, listeningForMs = 60_000),
        )
        // L'alerte n'existe qu'à l'écoute : en réflexion ou en lecture, le micro n'est pas en jeu.
        for (phase in listOf(VoicePhase.IDLE, VoicePhase.THINKING, VoicePhase.SPEAKING)) {
            assertEquals(
                "aucune alerte hors écoute ($phase)",
                ListenTrouble.NONE,
                listenTrouble(phase, "", 0, engineStarted = false, listeningForMs = 60_000),
            )
        }
    }

    @Test
    fun `la cause exacte du silence est nommee dans le journal`() {
        // Chaque panne a sa ligne, et elle dit les faits — jamais les mots reconnus, et jamais un
        // « code » nu.
        val neverStarted = listenTroubleLogLine(ListenTrouble.ENGINE_NEVER_STARTED, 2, 0, started = false)
        assertTrue(neverStarted!!.contains("le moteur n'a jamais démarré"))
        assertTrue(neverStarted.contains("démarré=false"))
        val noFrames = listenTroubleLogLine(ListenTrouble.NO_FRAMES, 5, 0, started = true)
        assertTrue(noFrames!!.contains("aucune trame du micro"))
        val noWords = listenTroubleLogLine(ListenTrouble.NO_WORDS, 9, 240, started = true)
        assertTrue(noWords!!.contains("aucun mot"))
        assertTrue(noWords.contains("trames reçues=240"))
        // Rien à dire : aucune ligne.
        assertNull(listenTroubleLogLine(ListenTrouble.NONE, 3, 0, started = true))
        // Et le motif d'un moteur muet nomme ce qu'on peut vérifier (moteur déclaré, langue, mode).
        val reason = engineStartReason(
            recognitionAvailable = false,
            preferOffline = true,
            localeTag = "fr-FR",
            frames = 0,
        )
        assertTrue(reason.contains("aucun moteur déclaré"))
        assertTrue(reason.contains("langue=fr-FR"))
        assertTrue(reason.contains("hors ligne"))
    }

    // MARK: - Le câblage : le moteur prouve qu'il a répondu, la session publie la panne

    @Test
    fun `le moteur dit s il a repondu, et jamais en silence`() {
        // « À L'ÉCOUTE » est posé au départ, avant que le moteur n'ait dit quoi que ce soit : c'est
        // une intention, pas une preuve. La preuve, c'est le premier rappel du moteur.
        assertTrue(engine.contains("var answered = false"))
        assertTrue(engine.contains("override fun onReadyForSpeech(params: Bundle?) {"))
        assertTrue(engine.contains("answered = true"))
        // Une demande d'écoute qui tombe sur une session déjà ouverte ne peut plus être ignorée
        // sans un mot : c'était le chemin muet du « rien ne se passe ».
        assertTrue(engine.contains("voice: départ ignoré — une session est déjà ouverte"))
        assertTrue(engine.contains("moteur a répondu=\$answered"))
    }

    @Test
    fun `la session publie la panne d ecoute, et l efface quand les mots arrivent`() {
        assertTrue(session.contains("val trouble: StateFlow<ListenTrouble>"))
        assertTrue(session.contains("listenTrouble("))
        assertTrue(session.contains("engineStarted = engine.answered"))
        assertTrue(session.contains("listeningForMs = now - listenStartedAt"))
        // L'horloge du jugement survit aux reprises automatiques : sinon un moteur qui rouvre sa
        // session au silence effacerait la panne à chaque reprise (le défaut d'origine, encore).
        assertTrue(session.contains("private var listenStartedAt: Long = 0L"))
        assertTrue(session.contains("listenStartedAt = lastSoundAt"))
        // Elle est écrite au journal une seule fois par changement.
        assertTrue(session.contains("if (trouble != notedTrouble) {"))
        assertTrue(policy.contains("val message: String?"))
    }
}
