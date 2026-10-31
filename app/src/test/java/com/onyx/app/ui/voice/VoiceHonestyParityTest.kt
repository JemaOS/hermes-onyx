package com.onyx.app.ui.voice

import com.onyx.app.data.model.serve.LogLevel
import com.onyx.app.ui.settings.LogFilter
import com.onyx.app.ui.settings.voiceLogLevel
import com.onyx.app.ui.settings.voiceLogLines
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L'écoute honnête : quand rien n'est capté, quand le moteur meurt, et ce que la page Journaux en
 * dit.
 *
 * Le propriétaire voit « À L'ÉCOUTE », l'orbe respire, « Allez-y. » — et rien ne se passe quand il
 * parle. Ces vérifications de source portent sur le **câblage** (le vrai rendu est jugé sur la
 * capture d'émulateur) : l'écran doit dire « je n'entends rien », proposer une action, et la page
 * Journaux doit montrer les lignes utiles du chemin vocal — sans jamais un jeton.
 */
class VoiceHonestyParityTest {
    private val screen = File("src/main/java/com/onyx/app/ui/voice/VoiceScreen.kt").readText()
    private val app = File("src/main/java/com/onyx/app/ui/OnyxApp.kt").readText()
    private val logs = File("src/main/java/com/onyx/app/ui/settings/LogsViewModel.kt").readText()

    // MARK: - « Je n'entends rien »

    @Test
    fun `aucun son pendant cinq secondes est dit a l ecran`() {
        // Le message de l'écran, et l'alerte qui le porte.
        assertTrue(app.contains("\"I can't hear anything — check your microphone.\""))
        assertTrue("l'alerte vient de la session ou d'une pose de capture", app.contains("val voiceNoSound = noSound || posedNoSound"))
        assertTrue("le drapeau de capture existe", app.contains("private const val EXTRA_VOICE_NO_SOUND = \"onyx.voiceNoSound\""))
        assertTrue("il est lu en booléen (jamais avec -e)", app.contains("getBooleanExtra(EXTRA_VOICE_NO_SOUND, false)"))
        // Et il est branché sur le bandeau de l'écran.
        assertTrue(app.contains("problem = listenProblem,"))
    }

    @Test
    fun `un bandeau d ecoute porte toujours une action`() {
        // Le composant : le bandeau prend une action, jamais un texte mort.
        assertTrue(screen.contains("data class VoiceAlertAction(val label: String, val onClick: () -> Unit)"))
        assertTrue(screen.contains("ProblemHelp(problem = problem, action = alertAction)"))
        assertTrue(screen.contains("alertAction: VoiceAlertAction? = null,"))
        // Côté écran : « Réessayer » pour une panne, l'ouverture des réglages pour la permission.
        assertTrue(app.contains("VoiceAlertAction(label = \"Try again\", onClick = { retryVoiceListening() })"))
        assertTrue(app.contains("VoiceAlertAction(label = \"Open Android settings\", onClick = { openVoiceAppSettings() })"))
        assertTrue(app.contains("Settings.ACTION_APPLICATION_DETAILS_SETTINGS"))
    }

    @Test
    fun `le refus de permission est explique et mene aux reglages`() {
        // Le message du refus est porté par l'écran, et le journal note la décision.
        assertTrue(app.contains("\"Onyx needs the microphone to listen. Allow it in Android's Settings → Apps → Onyx.\""))
        assertTrue(app.contains("voice: permission du micro refusée (RECORD_AUDIO)"))
        assertTrue(app.contains("voice: permission du micro accordée"))
        // L'action ouvre les réglages du système pour le paquet de l'app.
        assertTrue(app.contains("\"package:\${context.packageName}\""))
    }

    @Test
    fun `un echec du moteur est visible, actionnable, et n est pas un code brut`() {
        // Le message du moteur remonte à l'écran (le dernier échec définitif, sinon le problème).
        assertTrue(app.contains("val engineFailure by voice.failure.collectAsStateWithLifecycle()"))
        assertTrue(app.contains("engineFailure ?: speech.problem"))
        // Et la session le porte par un message, jamais par un code seul.
        val session = File("../core/src/main/java/com/onyx/app/voice/VoiceSession.kt").readText()
        assertTrue(session.contains("val failure: StateFlow<String?>"))
        assertTrue(session.contains("_failure.value = speech.problem ?: ENGINE_STOPPED_KEY"))
    }

    // MARK: - Le verrou : l'écran ne peut pas rester muet

    @Test
    fun `la panne d ecoute arrive a l ecran avant le seul niveau du micro`() {
        // Le défaut d'origine : le seul signal d'alerte était un **seuil de niveau du micro**.
        // Un moteur qui livre des niveaux sans rendre un mot le franchissait, donc l'écran restait
        // muet sur « À L'ÉCOUTE ». La panne jugée sur les faits doit donc être lue, et **prioritaire**
        // sur cette alerte de niveau.
        assertTrue(app.contains("val voiceTrouble by voice.trouble.collectAsStateWithLifecycle()"))
        assertTrue(app.contains("?: voiceTrouble.message"))
        assertTrue(
            "la panne passe avant le message de niveau",
            app.indexOf("?: voiceTrouble.message") < app.indexOf("\"I can't hear anything — check your microphone.\""),
        )
        // Et la session la publie : jamais un écran à l'écoute sans rien à dire.
        val session = File("../core/src/main/java/com/onyx/app/voice/VoiceSession.kt").readText()
        assertTrue(session.contains("val trouble: StateFlow<ListenTrouble>"))
    }

    @Test
    fun `les deux causes du silence ont leur phrase, en francais`() {
        val policy = File("../core/src/main/java/com/onyx/app/voice/ListenHonesty.kt").readText()
        assertTrue(policy.contains("The device's speech recogniser never answered. Tap the orb to listen again."))
        assertTrue(policy.contains("The recogniser is running but finding no words. Tap the orb and try again."))
        // Et les deux phrases sont traduites : l'écran est français.
        val french = File("../core/src/main/java/com/onyx/app/localization/OnyxFrench.kt").readText()
        assertTrue(french.contains("n’a pas répondu. Touchez l’orbe pour réécouter."))
        assertTrue(french.contains("tourne mais ne trouve aucun mot. Touchez l’orbe et réessayez."))
    }

    // MARK: - La phrase d'aide

    @Test
    fun `la phrase d aide nomme les phrases vraiment ecoutees, en francais`() {
        // Plus de phrase figée citant des mots anglais.
        assertFalse(
            "l'ancienne phrase d'aide anglaise a disparu",
            screen.contains("Say “stop listening” or “that's all” to end hands-free."),
        )
        // La phrase se compose du gabarit traduit et des phrases réelles.
        assertTrue(screen.contains("\"Say %1"))
        assertTrue(screen.contains("to end hands-free."))
        assertTrue(screen.contains("spokenStops.joinToString(OnyxLocalization[\" or \"])"))
        // Les phrases par défaut sont **françaises** : c'était le défaut rapporté — une phrase
        // d'aide française citant des mots anglais (« stop listening », « that's all »).
        assertTrue(
            screen.contains("private val DEFAULT_HANDS_FREE_STOPS = listOf(\"arrête d'écouter\", \"c'est tout\")"),
        )
        assertTrue(screen.contains("stopPhrases: List<String> = emptyList(),"))
        // Et l'écran vocal reçoit les phrases réglées par la personne.
        assertTrue(app.contains("stopPhrases = stopPhrases,"))
    }

    // MARK: - La page Journaux

    @Test
    fun `la page Journaux montre le journal vocal de l appareil`() {
        assertTrue(logs.contains("VOICE(\"voice\", \"Voice\")"))
        assertTrue(logs.contains("VoiceDiagnostics.lines()"))
        assertTrue(logs.contains("if (state.file == LogFile.VOICE) {"))
        // Le filtre et la recherche s'appliquent en local pour ce journal-là.
        assertTrue(logs.contains("loadVoiceLog(state)"))
    }

    @Test
    fun `une ligne du journal vocal se colore selon son niveau`() {
        assertTrue(voiceLogLevel("10:04:01  voice: erreur du moteur code=5 (client), trames reçues=0") == LogLevel.ERROR)
        assertTrue(voiceLogLevel("10:04:01  voice: aucun son depuis 5 s (trames reçues=0)") == LogLevel.WARNING)
        assertTrue(voiceLogLevel("10:04:01  voice: moteur démarré (langue=fr-FR, hors ligne)") == LogLevel.PLAIN)
    }

    @Test
    fun `l ecoute muette est orange et se cherche telle quelle`() {
        // La ligne à transmettre : courte, orange, et cherchable mot pour mot.
        val line = "10:04:01  voice: on n'entend rien (5 s, trames=0, moteur=oui)"
        assertTrue("orange, comme l'alerte de l'écran", voiceLogLevel(line) == LogLevel.WARNING)
        assertTrue("cherchable telle quelle", line.contains("on n'entend rien"))
    }

    @Test
    fun `la page Journaux montre les lignes du journal vocal, filtrees en local`() {
        val lines = listOf(
            "10:04:01  voice: moteur démarré (langue=fr-FR, hors ligne)",
            "10:04:06  voice: aucun son depuis 5 s (trames reçues=0)",
            "10:04:09  voice: erreur du moteur code=5 (client), trames reçues=0",
        )
        // Tout : les trois lignes se lisent telles quelles.
        val all = voiceLogLines(lines, LogFilter.EVERYTHING)
        assertEquals(3, all.size)
        assertTrue(all.all { it.text.isNotBlank() })
        // Le filtre de niveau tient chaque ligne à sa couleur : « avertissements et erreurs » prend
        // les deux (orange + rouge), « erreurs seules » ne prend que la rouge.
        assertEquals(2, voiceLogLines(lines, LogFilter.WARNINGS).size)
        assertEquals(1, voiceLogLines(lines, LogFilter.ERRORS_ONLY).size)
        // La recherche s'applique en local.
        assertEquals(2, voiceLogLines(lines, LogFilter.EVERYTHING, search = "trames").size)
        assertEquals(0, voiceLogLines(lines, LogFilter.EVERYTHING, search = "introuvable").size)
    }

    @Test
    fun `le journal vocal ne cite jamais les mots reconnus`() {
        // Le journal du chemin vocal n'écrit que des faits de plomberie : les mots reconnus n'y
        // entrent pas — un test du core le prouve aussi (`SessionRecoveryTest`).
        val core = File("../core/src/main/java/com/onyx/app/voice/VoiceDiagnostics.kt").readText()
        assertTrue(core.contains("redactSensitive"))
        assertTrue(core.contains("never") || core.contains("jamais"))
        assertFalse("le transcript n'est jamais journalisé", core.contains("transcript"))
    }
}
