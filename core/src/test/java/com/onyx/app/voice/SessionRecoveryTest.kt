package com.onyx.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * L'écoute qui ne capte rien, et l'écoute qui s'arrête toute seule.
 *
 * Deux défauts rapportés par le propriétaire, deux vérités à tenir :
 *
 *  - « ça marche pas, rien ne se passe quand je parle » : l'écran restait sur « À L'ÉCOUTE » sans
 *    qu'aucun son ne rentre. Au bout de cinq secondes, l'app doit **le dire** — et proposer de
 *    réessayer — au lieu de laisser parler dans le vide.
 *  - « des fois ça s'arrête brusquement » : un moteur de reconnaissance Android termine sa session
 *    **tout seul** (silence, durée maximale). Ce n'est pas une panne : en mains libres, l'écoute
 *    doit se rouvrir. L'app ne s'arrête que sur un arrêt explicite, les mots d'invocation d'arrêt,
 *    ou des échecs **répétés**.
 *
 * Les décisions sont pures (donc éprouvées ici) ; l'articulation de la session est vérifiée sur le
 * texte, comme les autres lots — l'émulateur n'a pas de micro pour la jouer.
 */
class SessionRecoveryTest {

    private val session = File("src/main/java/com/onyx/app/voice/VoiceSession.kt").readText()
    private val failurePolicy = File("src/main/java/com/onyx/app/voice/EngineFailure.kt").readText()

    // MARK: - « On n'entend rien »

    @Test
    fun `aucun son pendant cinq secondes se dit`() {
        assertFalse(
            "au bout de 4,9 s, rien ne se dit encore",
            noSoundWhileListening(VoicePhase.LISTENING, quietForMs = 4_999, heard = ""),
        )
        assertTrue(
            "à 5 s sans le moindre son, l'écran doit le dire",
            noSoundWhileListening(VoicePhase.LISTENING, quietForMs = 5_000, heard = ""),
        )
        assertEquals("le seuil est bien de cinq secondes", 5_000L, NO_SOUND_MS)
    }

    @Test
    fun `une phrase entendue ecarte l alerte, et le repos aussi`() {
        // Des mots sont arrivés : le micro fonctionne, il n'y a plus rien à signaler.
        assertFalse(noSoundWhileListening(VoicePhase.LISTENING, quietForMs = 30_000, heard = "bonjour"))
        // L'alerte n'existe qu'à l'écoute : en réflexion ou en lecture, le micro n'est pas en jeu.
        assertFalse(noSoundWhileListening(VoicePhase.IDLE, quietForMs = 30_000, heard = ""))
        assertFalse(noSoundWhileListening(VoicePhase.THINKING, quietForMs = 30_000, heard = ""))
        assertFalse(noSoundWhileListening(VoicePhase.SPEAKING, quietForMs = 30_000, heard = ""))
    }

    @Test
    fun `l horloge du silence survit aux reprises automatiques`() {
        // Le repère est porté par la **session**, pas par la boucle d'endpointing (recréée à chaque
        // reprise) : sinon un moteur qui rouvre sa session au silence toutes les quelques secondes
        // effacerait l'alerte — et le micro muet ne serait jamais signalé.
        assertTrue("le repère du dernier son", session.contains("private var lastSoundAt: Long = 0L"))
        assertTrue(session.contains("if (level > ENDPOINT_MINIMUM_LEVEL || _heard.value.isNotBlank()) lastSoundAt = now"))
        assertTrue(session.contains("noSoundWhileListening(_phase.value, now - lastSoundAt, _heard.value)"))
        // Et il n'est remis à zéro que sur un geste de la personne.
        assertTrue(session.contains("if (resetSilence) {"))
        assertTrue(session.contains("fun startListening(fresh: Boolean = true) = beginListening(fresh = fresh, resetSilence = true)"))
    }

    // MARK: - La fin de session, la vraie panne

    @Test
    fun `une fin de session pendant l ecoute rouvre l ecoute, sans retomber sur pret`() {
        // Fin normale : le silence laisse la phase à l'écoute, et l'écoute se rouvre.
        assertTrue(
            "à l'écoute, une fin de session rouvre",
            shouldReopenListeningAfterSessionEnd(handsFree = true, phase = VoicePhase.LISTENING),
        )
        // Fin d'une lecture : la phase est au repos, et l'écoute repart là aussi.
        assertTrue(
            "au repos, la reprise d'après réponse rouvre",
            shouldReopenListeningAfterSessionEnd(handsFree = true, phase = VoicePhase.IDLE),
        )
        // **Pendant l'écoute, le hors-mains-libres rouvre aussi** : une fin de session n'est pas une
        // raison de couper l'écoute. Elle ne se coupe que sur un geste (■), les mots d'arrêt, ou un
        // échec définitif après plafond — jamais parce que le moteur a fini sa session. (Avant, cet
        // état retombait sur PRÊT : c'est la « fin de session prématurée » signalée.)
        assertTrue(
            "hors mains libres, mais toujours à l'écoute : rouvre",
            shouldReopenListeningAfterSessionEnd(handsFree = false, phase = VoicePhase.LISTENING),
        )
        // Au repos hors mains libres, en revanche, on ne rouvre pas : c'est la fin d'une lecture,
        // et rouvrir le micro là ferait s'auto-écouter l'agent.
        assertFalse(shouldReopenListeningAfterSessionEnd(handsFree = false, phase = VoicePhase.IDLE))
        // Pendant la réflexion ou la lecture, le micro est tenu par le tour : rien ne rouvre.
        assertFalse(shouldReopenListeningAfterSessionEnd(handsFree = true, phase = VoicePhase.THINKING))
        assertFalse(shouldReopenListeningAfterSessionEnd(handsFree = true, phase = VoicePhase.SPEAKING))
    }

    @Test
    fun `la session rouvre l ecoute au lieu de s arreter`() {
        // L'ancien garde (`if (_phase.value == VoicePhase.IDLE)`) rendait la reprise inerte quand
        // on était encore à l'écoute : le silence terminait la session et l'app restait figée.
        assertFalse(
            "l'ancien garde a disparu",
            session.contains("if (_phase.value == VoicePhase.IDLE) startListening()"),
        )
        assertTrue(session.contains("if (shouldReopenListeningAfterSessionEnd(_handsFree.value, _phase.value)) {"))
        assertTrue(session.contains("beginListening(fresh = false, resetSilence = resetSilence)"))
        // Et la branche du silence (fin normale, `isFinal` vrai et `failed` faux) reprend l'écoute.
        assertTrue(session.contains("} else if (!speech.isListening && speech.isFinal) {"))
        assertTrue(session.contains("// Un silence : en mains-libres on se remet à écouter."))
    }

    @Test
    fun `un echec repete seul arrete l ecoute, avec un message et une action`() {
        // Le repli de la référence : une reprise espacée, l'attente qui s'allonge, puis l'arrêt.
        assertTrue(session.contains("delay(engineRetryDelayMs(engine.attempts))"))
        assertTrue(failurePolicy.contains("ENGINE_RETRY_DELAY_MS * attempts.coerceAtLeast(1)"))
        // L'échec définitif **close** l'écoute (jamais un écran qui prétend écouter) et porte un
        // message lisible, pas un code brut seul.
        assertTrue(session.contains("_failure.value = speech.problem ?: ENGINE_STOPPED_KEY"))
        assertTrue(session.contains("val failure: StateFlow<String?>"))
        assertFalse(
            "le message n'est pas un code seul",
            session.substringAfter("_failure.value =").substringBefore("\n").contains("\"code "),
        )
    }

    // MARK: - Le journal du chemin vocal

    @Test
    fun `le journal vocal ne garde aucun secret`() {
        // Un jeton, une clé, une autorisation : jamais dans une ligne.
        assertFalse(redactSensitive("voice: token=abc123def").contains("abc123def"))
        assertFalse(redactSensitive("Authorization: Bearer xyz789").contains("xyz789"))
        assertFalse(redactSensitive("api_key: s3cr3t").contains("s3cr3t"))
        // Une ligne technique ordinaire passe telle quelle.
        assertTrue(redactSensitive("voice: moteur démarré (langue=fr-FR, hors ligne)").contains("moteur démarré"))
    }

    @Test
    fun `le journal vocal est borne et horodate`() {
        VoiceDiagnostics.clear()
        assertTrue(VoiceDiagnostics.lines().isEmpty())
        repeat(VoiceDiagnostics.MAX_LINES + 50) { i -> VoiceDiagnostics.note("voice: ligne $i") }
        val lines = VoiceDiagnostics.lines()
        assertEquals(VoiceDiagnostics.MAX_LINES, lines.size)
        // Les plus anciennes sont tombées.
        assertFalse(lines.any { it.endsWith("ligne 0") })
        assertTrue(lines.any { it.endsWith("ligne ${VoiceDiagnostics.MAX_LINES + 49}") })
        VoiceDiagnostics.clear()
    }
}
