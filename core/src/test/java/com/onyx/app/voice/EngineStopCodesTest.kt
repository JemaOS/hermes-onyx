package com.onyx.app.voice

import android.speech.SpeechRecognizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Les **codes d'arrêt du moteur de reconnaissance**, un par un, et la cause du `code=13`.
 *
 * Le propriétaire a montré l'écran de la vérité — « Le moteur de reconnaissance s'est arrêté et
 * l'écoute est coupée. Touchez l'orbe pour réécouter. » + « Réessayer » — avec le journal de son
 * téléphone :
 *
 * ```
 * voice: moteur démarré (langue=fr-FR, hors ligne)
 * voice: erreur du moteur code=13 (language-unavailable)
 * voice: erreur du moteur code=11 (server-disconnected)
 * ```
 *
 * La cause était dans ces deux lignes : l'app demandait une reconnaissance **hors ligne** alors
 * que le téléphone n'avait pas le pack de langue. Le moteur démarrait, ne trouvait pas sa langue,
 * s'arrêtait ; l'app réessayait hors ligne (deux fois le même aveu), puis coupait l'écoute.
 *
 * Trois vérités à tenir, éprouvées ici sans micro ni Android :
 *  1. le hors ligne n'est **pas imposé** — il est **demandé** (Réglages → Voix), faux par défaut ;
 *  2. quand le moteur hors ligne avoue ne pas servir la langue, on **bascule en ligne** — une fois,
 *     sans rien redemander ;
 *  3. chaque code d'arrêt a **sa** décision : reprendre, basculer, ou s'arrêter en le disant — et
 *     rien ne boucle.
 */
class EngineStopCodesTest {

    private val engine = File("src/main/java/com/onyx/app/voice/SpeechEngine.kt").readText()
    private val policy = File("src/main/java/com/onyx/app/voice/EngineFailure.kt").readText()
    private val session = File("src/main/java/com/onyx/app/voice/VoiceSession.kt").readText()

    private fun decision(error: Int, offline: Boolean, attempts: Int = 1) =
        engineStopDecision(error = error, preferOffline = offline, attempts = attempts)

    // MARK: - La table, code par code

    @Test
    fun `7 et 6 sont des fins de session normales, pas des pannes`() {
        assertEquals(EngineStopDecision.RESUME, decision(SpeechRecognizer.ERROR_NO_MATCH, offline = true))
        assertEquals(EngineStopDecision.RESUME, decision(SpeechRecognizer.ERROR_SPEECH_TIMEOUT, offline = false))
        assertTrue(isEngineSilence(SpeechRecognizer.ERROR_NO_MATCH))
        assertTrue(isEngineSilence(SpeechRecognizer.ERROR_SPEECH_TIMEOUT))
        assertFalse("13 n'est pas un silence", isEngineSilence(SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE))
    }

    @Test
    fun `13 language-unavailable bascule en ligne quand le hors ligne etait demande`() {
        assertEquals(
            "hors ligne demandé : on bascule",
            EngineStopDecision.GO_ONLINE,
            decision(SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE, offline = true),
        )
        assertEquals(
            "déjà en ligne : la langue manque vraiment, on le dit",
            EngineStopDecision.STOP,
            decision(SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE, offline = false),
        )
        assertTrue(offlineEngineExplainsFailure(SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE))
    }

    @Test
    fun `12 language-not-supported suit la meme regle`() {
        assertEquals(EngineStopDecision.GO_ONLINE, decision(SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, offline = true))
        assertEquals(EngineStopDecision.STOP, decision(SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, offline = false))
        assertTrue(offlineEngineExplainsFailure(SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED))
    }

    @Test
    fun `11 server-disconnected bascule hors ligne, reprend en ligne`() {
        assertEquals(EngineStopDecision.GO_ONLINE, decision(SpeechRecognizer.ERROR_SERVER_DISCONNECTED, offline = true))
        assertEquals(EngineStopDecision.RETRY, decision(SpeechRecognizer.ERROR_SERVER_DISCONNECTED, offline = false))
    }

    @Test
    fun `5 client bascule hors ligne, reprend espace en ligne`() {
        assertEquals(EngineStopDecision.GO_ONLINE, decision(SpeechRecognizer.ERROR_CLIENT, offline = true))
        assertEquals(EngineStopDecision.RETRY, decision(SpeechRecognizer.ERROR_CLIENT, offline = false))
    }

    @Test
    fun `9 insufficient-permissions s arrete et le dit, sans reprendre`() {
        assertEquals(EngineStopDecision.STOP, decision(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS, offline = true))
        assertEquals(EngineStopDecision.STOP, decision(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS, offline = false))
        assertFalse(
            "la permission ne se répare pas en réessayant",
            isTransientEngineFailure(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS),
        )
    }

    @Test
    fun `1 2 4 basculent hors ligne et reprennent en ligne`() {
        listOf(
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_SERVER,
        ).forEach { code ->
            assertEquals("code $code hors ligne", EngineStopDecision.GO_ONLINE, decision(code, offline = true))
            assertEquals("code $code en ligne", EngineStopDecision.RETRY, decision(code, offline = false))
        }
    }

    @Test
    fun `8 et 10 reprennent, puis s arretent au plafond`() {
        listOf(
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
            SpeechRecognizer.ERROR_TOO_MANY_REQUESTS,
        ).forEach { code ->
            assertEquals("code $code", EngineStopDecision.RETRY, decision(code, offline = false, attempts = 1))
            assertEquals("code $code", EngineStopDecision.RETRY, decision(code, offline = false, attempts = 2))
            assertEquals(
                "code $code au plafond",
                EngineStopDecision.STOP,
                decision(code, offline = false, attempts = MAX_ENGINE_ATTEMPTS),
            )
        }
    }

    @Test
    fun `les codes sans reprise s arretent en le disant`() {
        listOf(
            SpeechRecognizer.ERROR_AUDIO,
            SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT,
            99, // un code inconnu du moteur : jamais avalé en silence
        ).forEach { code ->
            assertEquals("code $code", EngineStopDecision.STOP, decision(code, offline = false, attempts = 1))
        }
    }

    @Test
    fun `aucune ligne de la table ne boucle`() {
        // Le plafond vaut pour **tous** les codes : au-delà, plus une seule reprise — et une
        // décision qui n'est pas `RETRY` mène toujours à un message (jamais le silence).
        val codes = listOf(
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
            SpeechRecognizer.ERROR_CLIENT,
            SpeechRecognizer.ERROR_SERVER,
            SpeechRecognizer.ERROR_SERVER_DISCONNECTED,
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
            SpeechRecognizer.ERROR_AUDIO,
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS,
            SpeechRecognizer.ERROR_TOO_MANY_REQUESTS,
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
            SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT,
        )
        codes.forEach { code ->
            listOf(true, false).forEach { offline ->
                val decision = decision(code, offline = offline, attempts = MAX_ENGINE_ATTEMPTS)
                assertTrue(
                    "code $code (hors ligne=$offline) ne doit plus reprendre au plafond",
                    decision != EngineStopDecision.RETRY,
                )
            }
        }
    }

    @Test
    fun `le message suit la decision, et un arret definitif ne promet jamais une reprise`() {
        // Rien à dire quand le moteur se répare tout seul : fin normale, reprise espacée, bascule
        // hors ligne → en ligne.
        assertNull(engineMessage(SpeechRecognizer.ERROR_NO_MATCH, EngineStopDecision.RESUME))
        assertNull(engineMessage(SpeechRecognizer.ERROR_CLIENT, EngineStopDecision.RETRY))
        assertNull(engineMessage(SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE, EngineStopDecision.GO_ONLINE))
        // Un arrêt définitif, lui, porte **toujours** un message…
        val codes = listOf(
            SpeechRecognizer.ERROR_CLIENT,
            SpeechRecognizer.ERROR_SERVER_DISCONNECTED,
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS,
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
            SpeechRecognizer.ERROR_AUDIO,
            99,
        )
        codes.forEach { code ->
            val message = engineMessage(code, EngineStopDecision.STOP)
            assertNotNull("code $code : un arrêt muet", message)
            assertTrue("code $code", message!!.isNotBlank())
            // …et **jamais** la promesse d'une reprise qui n'aura pas lieu, ni un code brut seul.
            assertFalse("code $code promet une reprise", message.contains("retry", ignoreCase = true))
            assertFalse("code $code promet une tentative", message.contains("try again, Onyx"))
            assertFalse("code $code : message vide de sens", message.trim() == "code $code")
        }
        // Le refus passager au plafond perd la formulation « Onyx réessaiera » : elle serait fausse.
        val atCeiling = engineMessage(SpeechRecognizer.ERROR_CLIENT, EngineStopDecision.STOP)!!
        assertTrue(atCeiling.contains("code 5"))
        assertTrue(atCeiling.contains("Tap the orb"))
    }

    // MARK: - Le verrou : le hors ligne n'est plus imposé

    @Test
    fun `le hors ligne n est plus impose — il est demande`() {
        // Le défaut d'origine, mot pour mot : `preferOffline = true` à la construction.
        assertFalse(
            "le hors ligne imposé ne doit pas revenir",
            engine.contains("preferOffline = true"),
        )
        assertTrue(
            "le souhait de la personne est faux par défaut",
            engine.contains("var offlineWanted: Boolean = false"),
        )
        // Et c'est **lui seul** que l'intention lit, moins une bascule déjà faite : l'option
        // `EXTRA_PREFER_OFFLINE` n'est donc posée que si elle a été demandée.
        assertTrue(engine.contains("private val preferOffline: Boolean get() = offlineWanted && !fellBackOnline"))
        assertTrue(engine.contains("if (preferOffline) putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)"))
        // Le journal dit lequel des deux modes tourne — c'est la première ligne qu'il faut lire.
        assertTrue(engine.contains("(if (preferOffline) \"hors ligne\" else \"en ligne\")"))
    }

    @Test
    fun `le reglage est le seul chemin vers le hors ligne`() {
        val repo = File("../app/src/main/java/com/onyx/app/data/repo/SettingsRepository.kt").readText()
        val viewModel = File("../app/src/main/java/com/onyx/app/ui/settings/SettingsViewModel.kt").readText()
        val screen = File("../app/src/main/java/com/onyx/app/ui/settings/VoiceSettingsScreen.kt").readText()
        val app = File("../app/src/main/java/com/onyx/app/ui/OnyxApp.kt").readText()

        assertTrue("le réglage est faux par défaut", repo.contains("private val _offlineRecognition = MutableStateFlow(false)"))
        assertTrue(repo.contains("val OFFLINE_RECOGNITION = booleanPreferencesKey(\"offline_recognition\")"))
        assertTrue(repo.contains("suspend fun setOfflineRecognition(value: Boolean)"))
        assertTrue(viewModel.contains("fun setOfflineRecognition(value: Boolean)"))
        assertTrue(screen.contains("label = \"Recognise offline\""))
        assertTrue(screen.contains("onCheckedChange = viewModel::setOfflineRecognition"))
        // Et il est réellement poussé au moteur de la session.
        assertTrue(app.contains("val offlineRecognition by settingsRepo.offlineRecognition.collectAsStateWithLifecycle()"))
        assertTrue(app.contains("voice.offlineRecognition = offlineRecognition"))
        assertTrue(session.contains("var offlineRecognition: Boolean"))
        assertTrue(session.contains("engine.offlineWanted = value"))
    }

    // MARK: - La bascule hors ligne → en ligne

    @Test
    fun `la bascule est cablee, definitive pour l ecoute, et journalisee`() {
        val onError = engine.substringAfter("override fun onError(error: Int)")
            .substringBefore("override fun onResults")

        // Elle est décidée par la politique, pas par une liste d'erreurs recopiée dans le moteur.
        assertTrue(onError.contains("val decision = engineStopDecision("))
        assertTrue(onError.contains("preferOffline = preferOffline"))
        // Elle est définitive : un seul sens, jamais de va-et-vient.
        assertTrue(onError.contains("fellBackOnline = true"))
        assertTrue(engine.contains("private var fellBackOnline = false"))
        // Elle ne consomme pas le budget de reprises : l'essai en ligne part avec son compteur à
        // zéro, sinon la bascule serait le troisième échec et l'écoute se fermerait quand même.
        val decisionIndex = onError.indexOf("val decision = engineStopDecision(")
        val resetIndex = onError.indexOf("consecutiveErrors = 0", startIndex = decisionIndex)
        assertTrue("la remise à zéro vient après la décision", resetIndex > decisionIndex)
        // Et elle se lit au journal, en clair : c'est la ligne à transmettre.
        assertTrue(engine.contains("voice: bascule hors ligne → en ligne (code=\$error"))
        // À l'écran, rien ne s'annonce : c'est une réparation, pas une panne. Le message est décidé
        // par la politique (`engineMessage`), où `GO_ONLINE` ne rend rien.
        assertTrue(onError.contains("val message = engineMessage(error, decision)"))
        assertNull(engineMessage(30, EngineStopDecision.GO_ONLINE))
    }

    @Test
    fun `la session repart en ligne sans rien redemander, et ne coupe pas l ecoute`() {
        val listening = session.substringAfter("VoicePhase.LISTENING -> {")
            .substringBefore("VoicePhase.SPEAKING -> handleSpeaking")
        assertTrue(
            "la décision de bascule est lue avant tout autre chemin",
            listening.contains("if (speech.stopDecision == EngineStopDecision.GO_ONLINE) {"),
        )
        assertTrue(listening.contains("relistenAfterOfflineFallback()"))
        assertTrue(session.contains("private fun relistenAfterOfflineFallback()"))
        assertTrue("elle passe `fresh = false` : rien n'est redemandé", session.contains("OFFLINE_FALLBACK_DELAY_MS"))
        // Un `STOP` ferme tout de suite, sans passer par le compteur de reprises : un code qui ne se
        // répare pas ne se réessaie pas.
        assertTrue(listening.contains("if (speech.stopDecision == EngineStopDecision.RETRY) {"))
        assertTrue(session.contains("private fun closeAfterEngineFailure(speech: SpeechState)"))
    }

    @Test
    fun `une fin de session ne coupe plus l ecoute`() {
        // C'est la règle du lot : l'écoute ne se coupe que sur un geste (■), les mots d'arrêt, ou un
        // échec définitif après plafond.
        assertTrue(
            "pendant l'écoute, une fin de session rouvre — même hors mains libres",
            shouldReopenListeningAfterSessionEnd(handsFree = false, phase = VoicePhase.LISTENING),
        )
        assertTrue(shouldReopenListeningAfterSessionEnd(handsFree = true, phase = VoicePhase.LISTENING))
        assertFalse(
            "au repos hors mains libres, non : c'est la fin d'une lecture (écho)",
            shouldReopenListeningAfterSessionEnd(handsFree = false, phase = VoicePhase.IDLE),
        )
        assertTrue(policy.contains("isEngineSilence(error) -> EngineStopDecision.RESUME"))
    }
}
