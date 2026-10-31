package com.onyx.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les trois garde-fous du mode vocal : demi-duplex, garde anti-écho, endpointing.
 *
 * Toutes les décisions sont pures, donc éprouvées ici ; l'émulateur n'a pas de micro, et c'est
 * justement pour cela que la boucle (effet Larsen) se teste par injection et non au téléphone.
 */
class HalfDuplexTest {

    // MARK: - La garde anti-écho

    @Test
    fun `la reponse lue qui revient par le micro est reconnue pour un écho`() {
        // Le cas exact de la boucle observée : la réponse transcrite à la ponctuation près.
        assertTrue(
            isEchoOfReply(
                "bonjour comment puis-je t'aider aujourd'hui",
                "Bonjour ! Comment puis-je t'aider aujourd'hui ?",
            ),
        )
        assertTrue(isEchoOfReply("Bonjour ! Comment", "Bonjour ! Comment puis-je t'aider aujourd'hui ?"))
        assertTrue(isEchoOfReply("puis-je t'aider aujourd'hui", "Bonjour ! Comment puis-je t'aider aujourd'hui ?"))
        assertTrue(
            isEchoOfReply(
                "je peux t aider a trouver des idees de recettes simples",
                "Je peux t'aider à trouver des idées de recettes simples, dis-moi ce que tu as sous la main.",
            ),
        )
    }

    @Test
    fun `un fragment de la reponse entendu par le haut parleur est un echo`() {
        // La capture du propriétaire : le micro reprend trois mots de la réponse en cours, avec un
        // mot que le moteur a écrit autrement (« plus » pour « plutôt »). Sans la comparaison par
        // préfixe, rien de tout cela n'était un écho — la réponse était coupée en plein milieu et
        // le fragment devenait la question suivante.
        val reply = "Bien sûr ! Je suis plutôt à l'aise pour discuter de tout : " +
            "tech, musique, ciné, bouffe, philo de comp"
        assertTrue(isEchoOfReply("je suis plus", reply))
        // Le moteur réordonne et abrège : « Ma question, c'était juste : tu veux parler de quoi ? »
        assertTrue(isEchoOfReply("tu veux parler de quoi", "Ma question, c'était juste : tu veux parler de quoi ?"))
        // Et une vraie question garde son chemin.
        assertFalse(isEchoOfReply("tu peux me faire une liste de courses", reply))
    }

    @Test
    fun `les mots se comparent par leur prefixe, pas au hasard`() {
        assertTrue("plus" .matchesEchoToken("plus"))
        assertTrue("plutot".matchesEchoToken("plus"))
        assertTrue("questions".matchesEchoToken("question"))
        assertFalse("chat".matchesEchoToken("chien"))
        // Deux lettres ne suffisent pas : « tu » et « tout » ne sont pas le même mot.
        assertFalse("tu".matchesEchoToken("tout"))
    }

    @Test
    fun `une vraie question n est pas prise pour un écho`() {
        val reply = "Je peux t'aider à trouver des idées de recettes simples avec ce que tu as chez toi."
        assertFalse(isEchoOfReply("tu peux me faire une liste de courses", reply))
        assertFalse(isEchoOfReply("et sinon, quel temps fera-t-il demain ?", reply))
        assertFalse(isEchoOfReply("", reply))
        assertFalse(isEchoOfReply("bonjour", ""))
    }

    @Test
    fun `un mot isolé de la réponse ne suffit pas à la prendre pour un écho`() {
        val reply = "Bonjour ! Comment puis-je t'aider aujourd'hui ? Je suis là pour ça."
        // « bonjour » est un mot de la réponse, mais ce n'est pas la réponse qui revient.
        assertFalse(isEchoOfReply("bonjour", reply))
    }

    @Test
    fun `la ressemblance compte les mots, pas les caractères`() {
        assertEquals(1f, transcriptSimilarity("un deux trois", "trois deux un"), 0.0001f)
        assertEquals(0f, transcriptSimilarity("un deux", "trois quatre"), 0.0001f)
        assertEquals(0f, transcriptSimilarity("", "trois"), 0.0001f)
        // 2 mots communs sur 4 distincts : 0,5 (Jaccard).
        assertEquals(0.5f, transcriptSimilarity("un deux trois", "deux trois quatre"), 0.0001f)
    }

    @Test
    fun `la phrase n est envoyée qu une fois et jamais si c est un écho`() {
        val reply = "Bonjour ! Comment puis-je t'aider aujourd'hui ?"
        assertTrue(shouldSendHeardTranscript("quelle heure est-il", reply, alreadySent = false))
        assertFalse("déjà envoyée", shouldSendHeardTranscript("quelle heure est-il", reply, alreadySent = true))
        assertFalse("écho", shouldSendHeardTranscript("bonjour comment puis-je t'aider aujourd'hui", reply, false))
        assertFalse("vide", shouldSendHeardTranscript("   ", reply, false))
    }

    // MARK: - Le demi-duplex

    @Test
    fun `le micro reste fermé pendant la lecture tant que le barge-in n est pas demandé`() {
        // Demi-duplex : rien ne rouvre le micro tant que « Talk over replies » est éteint.
        assertFalse(
            shouldReopenMicForSpeech(
                VoicePhase.SPEAKING, isListening = false, listenAfterSpeech = true, micBehindReply = false,
            ),
        )
        assertTrue(
            "barge-in demandé : le micro peut rouvrir derrière la réponse",
            shouldReopenMicForSpeech(
                VoicePhase.SPEAKING, isListening = false, listenAfterSpeech = true, micBehindReply = true,
            ),
        )
        assertFalse(
            "une lecture demandée depuis le fil ne rouvre jamais le micro",
            shouldReopenMicForSpeech(VoicePhase.SPEAKING, false, listenAfterSpeech = false, micBehindReply = true),
        )
        assertFalse("hors lecture", shouldReopenMicForSpeech(VoicePhase.IDLE, false, true, true))
    }

    @Test
    fun `il faut 400 ms de parole tenue pour couper la réponse`() {
        val detector = BargeInDetector.echoCancelled(hold = BARGE_IN_HOLD_MS / 1000.0, settle = 0.0)
        detector.feed(-95f, 0.05)                 // la pièce se déclare
        var tripped = false
        repeat(3) { if (detector.feed(-20f, 0.1)) tripped = true }   // 300 ms : pas encore
        assertFalse("300 ms ne suffisent pas", tripped)
        repeat(2) { if (detector.feed(-20f, 0.1)) tripped = true }   // 500 ms : oui
        assertTrue("500 ms coupent", tripped)
    }

    // MARK: - L'endpointing

    @Test
    fun `le silence termine l écoute après le temps réglé`() {
        assertTrue(shouldEndBySilence("bonjour", sinceLastChangeMs = 1400, sinceFirstSpeechMs = 2000))
        assertFalse(
            "pas encore",
            shouldEndBySilence("bonjour", sinceLastChangeMs = 900, sinceFirstSpeechMs = 2000),
        )
        assertFalse(
            "rien n'a été entendu : un silence ne termine pas une écoute qui n'a pas commencé",
            shouldEndBySilence("", sinceLastChangeMs = 5000, sinceFirstSpeechMs = 0),
        )
        assertFalse(
            "on vient de commencer à parler",
            shouldEndBySilence("bon", sinceLastChangeMs = 1400, sinceFirstSpeechMs = 100),
        )
    }

    @Test
    fun `le silence qui termine l écoute est réglable`() {
        assertFalse(shouldEndBySilence("bonjour", 1200, 2000, silenceMs = 1400))
        assertTrue(shouldEndBySilence("bonjour", 1200, 2000, silenceMs = 900))
        // **1,2 s : la valeur de la référence** (`SpeechRecognizer.silenceTimeout`). À 1,1 s, une
        // hésitation au milieu d'une phrase la faisait couper (« ça se bloque toujours en plein
        // milieu d'une phrase ») ; la référence demande aussi que la pièce soit calme depuis
        // 0,6 × ce temps, ce que porte `EndpointWatcher`.
        assertEquals(1200L, DEFAULT_ENDPOINT_SILENCE_MS)
    }

    // MARK: - La boucle, de bout en bout

    @Test
    fun `la reponse lue qui revient telle quelle est jetee et la boucle ne part pas`() {
        // La séquence exacte de la capture : « bonjour » part, la réponse est lue, puis le micro
        // rend la réponse mot pour mot. Elle doit être jetée — sinon « bonjour comment puis-je
        // t'aider aujourd'hui » devient la question suivante, et la boucle Larsen s'installe.
        val reply = "Bonjour ! Comment puis-je t'aider aujourd'hui ?"
        val heardEcho = "bonjour comment puis-je t'aider aujourd'hui"

        // 1. La personne dit « bonjour » : ça part, une fois.
        assertTrue(shouldSendHeardTranscript("bonjour", lastReply = "", alreadySent = false))

        // 2. La réponse est lue (`VoiceSession.speak` pose ce texte comme `lastReplyText`).
        // 3. Le micro rend la réponse telle quelle : jetée.
        assertFalse("la réponse lue qui revient est un écho", shouldSendHeardTranscript(heardEcho, reply, false))
        assertFalse("la réponse entière aussi", shouldSendHeardTranscript(reply, reply, false))

        // 4. Une phrase qui n'est pas la réponse part, elle.
        assertTrue(shouldSendHeardTranscript("quelle heure est-il", reply, false))

        // 5. Et une phrase déjà envoyée ne repart jamais (l'ancien défaut en mains-libres).
        assertFalse(shouldSendHeardTranscript("bonjour", reply, alreadySent = true))
    }

    @Test
    fun `la garde anti-echo vaut aussi sur la parole par-dessus la lecture`() {
        val reply = "Bonjour ! Comment puis-je t'aider aujourd'hui ?"

        // Le micro rouvert pour le barge-in rend un morceau de la réponse : ce n'est pas une prise
        // de parole, la réponse n'est donc pas coupée.
        assertFalse(shouldSendHeardTranscript("comment puis-je t'aider", reply, alreadySent = false))
        assertFalse(shouldSendHeardTranscript(reply, reply, alreadySent = false))

        // Une vraie prise de parole, elle, passe — c'est le barge-in.
        assertTrue(shouldSendHeardTranscript("non attends", reply, alreadySent = false))
        // Et un mot isolé reste une parole : « bonjour » n'est pas la réponse qui revient (c'est la
        // règle voulue — un mot de la réponse ne suffit jamais à la prendre pour un écho).
        assertTrue(shouldSendHeardTranscript("bonjour", reply, alreadySent = false))
    }
}
