package com.onyx.app

import com.onyx.app.data.model.ServerConfig
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.bots.GroupVoice
import com.onyx.app.ui.bots.GroupVoiceLine
import com.onyx.app.ui.bots.GroupVoiceThread
import com.onyx.app.ui.bots.groupRoundIsOver
import com.onyx.app.ui.bots.groupVoicePhaseTitle
import com.onyx.app.voice.EarOutput
import com.onyx.app.voice.EarRouteController
import com.onyx.app.voice.FaceDownSensing
import com.onyx.app.voice.HeardFrame
import com.onyx.app.voice.ProximityEarRouter
import com.onyx.app.voice.ProximityWatcher
import com.onyx.app.voice.SpeechInput
import com.onyx.app.voice.SpeechReply
import com.onyx.app.voice.SpeechState
import com.onyx.app.voice.VoiceDiagnostics
import com.onyx.app.voice.VoicePhase
import com.onyx.app.voice.VoiceSession
import com.onyx.app.voice.scenarioFromWav
import java.io.File
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **La boucle vocale d'une salle, rejouée sans micro.**
 *
 * Le défaut rapporté : une salle s'affichait (« À L'ÉCOUTE », les visages, la ligne d'aide) mais la
 * conversation **ne se déroulait pas** — la question partait, et rien ne revenait. Ici, la chaîne
 * complète tourne pour de vrai : le moteur d'écoute est alimenté par le **WAV français** des
 * ressources, le lecteur est un faux, et la salle est scriptée — elle répond, bot après bot.
 *
 * Ce qui est verrouillé, dans l'ordre du tour :
 *  1. l'écoute s'ouvre et la question reconnue **part dans la salle**, une fois ;
 *  2. **pendant que les bots travaillent, l'écoute ne se rouvre pas** (c'est le défaut nommé : la
 *     reprise jetait les réponses qui attendaient d'être lues) ;
 *  3. les réponses sont lues **une par une, dans l'ordre de la file** — le tour passe au bot
 *     suivant, chacune **avec son profil** (donc sa voix) ;
 *  4. le micro **revient** quand la file est vidée et la salle retombée au repos ;
 *  5. un tour suivant repart, et la sortie laisse tout au repos.
 *
 * Le test **casse** si le tour ne passe plus au bot suivant, si l'écoute ne reprend pas, ou si deux
 * réponses se chevauchent.
 */
class GroupVoiceLoopTest {

    private fun wav(): ByteArray = File("src/main/assets/voice/phrase-fr.wav").readBytes()

    private fun await(what: String, timeoutMs: Long = 20_000, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (condition()) return
            Thread.sleep(10)
        }
        throw AssertionError("attendu (${timeoutMs} ms) : $what")
    }

    private fun session(scope: CoroutineScope, engine: SpeechInput, reply: SpeechReply) = VoiceSession(
        engine = engine,
        reply = reply,
        audioRoute = NoRoute(),
        faceDown = NeverFaceDown(),
        earRouter = ProximityEarRouter(watcher = NoProximity(), route = NoRoute(), enabled = { false }),
        scope = scope,
        localeTag = "fr-FR",
    )

    private fun scenario(text: String, leadInMs: Long = 300L) =
        scenarioFromWav(wav(), transcript = text, leadInMs = leadInMs, tailMs = 1_700)

    @Test
    fun `la file fait parler les bots un par un, puis rend le micro`() {
        val scope = CoroutineScope(Dispatchers.Default)
        try {
            VoiceDiagnostics.clear()
            val reply = FakeReply(scope, durationMs = 400)
            val engine = ScriptedEngine(
                listOf(scenario(QUESTION_A), scenario(QUESTION_B), scenario(QUESTION_C)),
                scope,
            )
            val session = session(scope, engine, reply)
            val room = ScriptedRoom(scope)
            val group = GroupVoice(room, session, scope)

            // La trace de ce qui s'est réellement passé, dans l'ordre.
            val reading = CopyOnWriteArrayList<String>()
            val listeningDuring = CopyOnWriteArrayList<String>()
            reply.onSpeak = { text, speaker ->
                reading += speaker + ":" + text.take(11)
                // Le tour de qui parle : à cet instant, une salle ne lit jamais deux voix.
                assertTrue("une seule voix à la fois", reply.speakingNow.get() == 1)
            }
            val watcher = scope.launch {
                while (true) {
                    if (session.phase.value == VoicePhase.LISTENING && room.asked.isNotEmpty() &&
                        reading.size < 2
                    ) {
                        // Le micro ne peut pas être rouvert alors qu'il reste des réponses à lire.
                        listeningDuring += "n=${reading.size}"
                    }
                    delay(10)
                }
            }

            group.start()
            // Le câblage de l'app : la question reconnue part dans la salle.
            session.onQuestion = group::ask
            group.listen()

            await("la question A part dans la salle") { room.asked.isNotEmpty() }
            assertEquals(listOf(QUESTION_A), room.asked.toList())

            await("le bot A parle") { reading.size >= 1 }
            assertEquals("le tour est au premier bot de la file", "scout", group.speakerId.value)

            await("le bot B parle à son tour") { reading.size >= 2 }
            assertEquals("le tour passe au bot suivant", "sage", group.speakerId.value)

            await("le bot C parle à son tour") { reading.size >= 3 }
            assertEquals("le tour passe au troisième bot", "quill", group.speakerId.value)

            // 3. La file : chacun son tour, dans l'ordre, et jamais deux à la fois.
            assertEquals(
                listOf(
                    "scout:" + ANSWER_A.take(11),
                    "sage:" + ANSWER_B.take(11),
                    "quill:" + ANSWER_C.take(11),
                ),
                reading.toList(),
            )
            assertTrue("aucun chevauchement", reply.overlaps.get() == 0)

            // 2. La preuve du défaut nommé : l'écoute ne s'est **pas** rouverte pendant le tour.
            assertEquals(
                "l'écoute ne se rouvre pas pendant que les bots répondent",
                emptyList<String>(),
                listeningDuring.toList(),
            )

            // 4. Le micro revient une fois la file vidée et la salle au repos.
            await("l'écoute reprend après le tour") { session.phase.value == VoicePhase.LISTENING }

            // 5. Un tour suivant repart : question B, et la file reprend.
            await("la question B part") { room.asked.size >= 2 }
            assertEquals(listOf(QUESTION_A, QUESTION_B), room.asked.toList())
            await("la file du deuxième tour est lue") { reading.size >= 6 }

            // Le journal de la salle dit **chaque étape**, et ne garde **jamais** les mots.
            val journal = VoiceDiagnostics.lines()
            assertTrue(
                "la question qui part",
                journal.any { it.contains("question reconnue envoyée dans la salle") },
            )
            assertTrue("le premier bot prend la parole", journal.any { it.contains("scout prend la parole") })
            assertTrue("puis le suivant", journal.any { it.contains("sage prend la parole") })
            assertTrue(
                "et l'écoute qui reprend",
                journal.any { it.contains("le tour est fini — l'écoute reprend") },
            )
            assertFalse(
                "aucun mot reconnu ni lu n'entre dans le journal",
                journal.any { it.contains(QUESTION_A) || it.contains(ANSWER_A) || it.contains(ANSWER_B) },
            )

            // Sortie propre : plus de lecture, plus de micro, phase au repos.
            group.stop()
            watcher.cancel()
            assertEquals(VoicePhase.IDLE, session.phase.value)
            assertFalse("plus rien ne se lit", reply.isSpeaking.value)
            await("plus de lecture après la sortie") { reply.levelNow.value == 0f }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `reparler pendant la file coupe la lecture en cours et repart`() {
        val scope = CoroutineScope(Dispatchers.Default)
        try {
            val reply = FakeReply(scope, durationMs = 1_200)
            val engine = ScriptedEngine(listOf(scenario(QUESTION_A), scenario(QUESTION_B)), scope)
            val session = session(scope, engine, reply)
            val room = ScriptedRoom(scope)
            val group = GroupVoice(room, session, scope)

            group.start()
            session.onQuestion = group::ask
            group.listen()

            await("la question A part") { room.asked.isNotEmpty() }
            await("le bot A commence à parler") { reply.spoken.isNotEmpty() }
            // La personne touche l'orbe en pleine file : la réplique en cours est **coupée**, la
            // suite de la file est lâchée, et l'écoute repart.
            group.listen()

            assertEquals("la lecture en cours est coupée", false, reply.isSpeaking.value)
            assertEquals(VoicePhase.LISTENING, session.phase.value)

            await("la nouvelle question part") { room.asked.size >= 2 }
            assertEquals(listOf(QUESTION_A, QUESTION_B), room.asked.toList())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `un tour est fini quand la salle se tait, et le micro ne s enferme jamais`() {
        assertTrue("salle au repos depuis la pose : le tour est fini", groupRoundIsOver(false, false, 500))
        assertFalse("salle qui travaille : le tour n'est pas fini", groupRoundIsOver(true, false, 500))
        assertFalse("un bot attend la personne", groupRoundIsOver(false, true, 5_000))
        assertFalse("même après une longue attente", groupRoundIsOver(true, true, 60_000))
        // Le garde-fou : une salle annoncée au travail qui ne produit plus rien rend le micro.
        assertTrue("la salle se tait depuis trop longtemps", groupRoundIsOver(true, false, 12_000))
        assertFalse("juste avant le délai", groupRoundIsOver(true, false, 11_999))
        // La **pose** : la salle vient d'écrire sa dernière ligne, une réponse peut encore arriver.
        assertFalse("le micro ne se rouvre pas dans la foulée", groupRoundIsOver(false, false, 0))
        assertFalse("ni 300 ms après", groupRoundIsOver(false, false, 300))
    }

    @Test
    fun `une reponse qui arrive juste apres la fin du tour n est pas perdue`() {
        val scope = CoroutineScope(Dispatchers.Default)
        try {
            val reply = FakeReply(scope, durationMs = 400)
            val engine = ScriptedEngine(listOf(scenario(QUESTION_A), scenario(QUESTION_B)), scope)
            val session = session(scope, engine, reply)
            // La salle se déclare **au repos**, puis écrit encore une réponse : c'est le tour sauté
            // vu une fois sur un appareil lent. L'écoute ne doit pas s'être rouverte entre-temps —
            // rouverte, elle jette ce qui attend d'être lu, et ce bot n'aurait jamais parlé.
            val room = LateReplyRoom(scope)
            val group = GroupVoice(room, session, scope)

            group.start()
            session.onQuestion = group::ask
            group.listen()

            await("la première réponse est lue") { reply.spoken.isNotEmpty() }
            await("la réponse en retard est lue elle aussi") { reply.spoken.size >= 2 }
            assertEquals(listOf(ANSWER_A, ANSWER_B), reply.spoken.toList())
        } finally {
            scope.cancel()
        }
    }


    @Test
    fun `le tour est juge sur la salle a l instant present, pas sur un instantane`() {
        // La salle ecrit sa derniere reponse **et** se declare au repos dans le meme souffle : une
        // ligne ecrite juste avant le reveil de la tache de pose doit etre vue. Le tour juge sur un
        // instantane dejoue faisait declarer le tour fini avec une file vide — la reponse etait
        // ensuited envoyee dans une file d'ecoute et lachee : un bot muet, au journal
        // « ecrit quill (seq=4) » et « le tour est fini » dans la meme seconde.
        val scope = CoroutineScope(Dispatchers.Default)
        try {
            val reply = FakeReply(scope, durationMs = 300)
            val engine = ScriptedEngine(listOf(scenario(QUESTION_A), scenario(QUESTION_B)), scope)
            val session = session(scope, engine, reply)
            val room = SameBreathRoom(scope)
            val group = GroupVoice(room, session, scope)

            group.start()
            session.onQuestion = group::ask
            group.listen()

            await("les trois bots parlent, un par un") { reply.spoken.size >= 3 }
            assertEquals(listOf(ANSWER_A, ANSWER_B, ANSWER_C), reply.spoken.toList())

            // Et la relecture de la salle est bien dans le chemin (`GroupVoice.next`) : sans elle,
            // une ligne ecrite juste avant le reveil de la tache de pose passe inapercue.
            val voice = java.io.File("src/main/java/com/onyx/app/ui/bots/GroupVoice.kt").readText()
            assertTrue(voice.contains("if (replies.update(thread.lines.value, held = _isHeld.value))"))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `une reponse qui arrive apres le garde-fou du tour n est pas perdue`() {
        // Le défaut relevé sur un vrai serveur (journal du 27 oct. 02:04) :
        //
        //   02:04:31  voice (salle): question reconnue envoyée dans la salle
        //   02:04:31  voice (salle): le tour des bots n'est pas fini — l'écoute reste fermée
        //   02:04:38  voice (salle): le tour est fini — l'écoute reprend      ← 7 s après la question
        //   02:04:50  voice (salle): scout prend la parole (0 en attente)
        //
        // Le garde-fou « la salle travaille sans rien produire » (douze secondes) se comptait depuis
        // l'**ouverture de la scène**, pas depuis la question : il rendait le micro avant que les
        // bots aient répondu, et la reprise d'écoute **jetait** la réponse qui arrivait ensuite — la
        // première réponse du tour n'était lue par personne.
        //
        // Ici la salle répond dix secondes et demie après la question : le compte partait de
        // l'ouverture (le micro se rouvrait donc neuf secondes après la question, et la réponse
        // était jetée), il part maintenant de la question.
        val scope = CoroutineScope(Dispatchers.Default)
        try {
            VoiceDiagnostics.clear()
            val reply = FakeReply(scope, durationMs = 300)
            val engine = ScriptedEngine(listOf(scenario(QUESTION_A), scenario(QUESTION_B)), scope)
            val session = session(scope, engine, reply)
            val room = SlowReplyRoom(scope, delayMs = 10_500)
            val group = GroupVoice(room, session, scope)

            group.start()
            session.onQuestion = group::ask
            group.listen()
            await("la question part dans la salle", timeoutMs = 15_000) { room.asked.isNotEmpty() }
            // La salle se déclare au travail puis se tait dix secondes : sa réponse doit être lue.
            await("la réponse, arrivée après le garde-fou, est quand même lue", timeoutMs = 25_000) {
                reply.spoken.isNotEmpty()
            }
            assertEquals(listOf(ANSWER_A), reply.spoken.toList())
            assertTrue(
                "le bot qui répond est nommé au journal",
                VoiceDiagnostics.lines().any { it.contains("scout prend la parole") },
            )
        } finally {
            scope.cancel()
        }
    }

    // — Une question reconnue n'entre qu'une fois, et vingt tours font vingt messages ------------

    /**
     * Une phrase courte, sans WAV : de quoi rejouer **vingt tours** dans une seule preuve, avec la
     * même chaîne que l'app (le vrai `VoiceSession`, le vrai `GroupVoice`, la salle qui répond).
     *
     * La transcription grandit comme celle d'un moteur qui hésite, et la dernière trame est la
     * **finale** — c'est elle qui pose la question.
     */
    private fun quickScenario(text: String): List<HeardFrame> = listOf<HeardFrame>(
        HeardFrame(atMs = 0, level = 0f),
        HeardFrame(atMs = 40, level = 0.3f, transcript = text.take(2)),
        HeardFrame(atMs = 80, level = 0.3f, transcript = text),
        HeardFrame(atMs = 120, level = 0f, transcript = text),
        HeardFrame(atMs = 160, level = 0f, transcript = text, isFinal = true),
    )

    @Test
    fun `vingt tours d affilee font vingt envois et vingt reponses, jamais vingt et un`() {
        val scope = CoroutineScope(Dispatchers.Default)
        try {
            VoiceDiagnostics.clear()
            val questions = (1..20).map { turn -> "Tour $turn : quel temps fera-t-il demain à Lyon ?" }
            val reply = FakeReply(scope, durationMs = 120)
            // Vingt tours, puis **la même question qui grandit** — « … à Lyon, et mardi ? » —, telle
            // que le moteur la rend quand son écoute se rouvre. C'est exactement la boucle vue par
            // les bots ; elle ne doit pas faire un vingt-et-unième message.
            val engine = ScriptedEngine(
                questions.map { quickScenario(it) } +
                    listOf(quickScenario("Tour 20 : quel temps fera-t-il demain à Lyon, et mardi ?")),
                scope,
            )
            val session = session(scope, engine, reply)
            val room = SteadyRoom(scope)
            val group = GroupVoice(room, session, scope)

            group.start()
            session.onQuestion = group::ask
            group.listen()
            await("les vingt questions partent dans la salle", timeoutMs = 90_000) { room.asked.size >= 20 }
            await("les vingt réponses sont lues", timeoutMs = 60_000) { reply.spoken.size >= 20 }
            // Le moteur rejoue la question, plus complète : le guichet doit la reconnaître.
            await("le rejeu du moteur est vu et arrêté", timeoutMs = 10_000) {
                VoiceDiagnostics.lines().any { it.contains("question déjà posée") }
            }
            Thread.sleep(500)

            assertEquals("vingt tours, vingt messages — jamais vingt et un", 20, room.asked.size)
            assertEquals("et ce sont les questions de la personne", questions, room.asked.toList())
            assertEquals("vingt réponses affichées (lues)", 20, reply.spoken.size)
            assertEquals("chaque question porte un identifiant unique", 20, room.askedIds.toSet().size)
            // La même phrase dite deux fois ne compte pas deux fois : c'est le tour qui repart.
            assertEquals("aucune question en double", room.asked.size, room.asked.toSet().size)
            assertTrue(
                "le journal dit l'envoi unique de chaque question",
                VoiceDiagnostics.lines().count { it.contains("question reconnue envoyée dans la salle") } >= 20,
            )
            // La trace du tour, telle qu'on la relit pour la preuve : un envoi, une réponse.
            for (turn in 0 until 20) {
                println(
                    "TOUR ${turn + 1} | id=${room.askedIds[turn]} | envoyé=« ${room.asked[turn]} » " +
                        "| 1 envoi | lu=« ${reply.spoken[turn].take(30)}… »",
                )
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `une transcription qui grandit ne part qu une fois dans la salle`() {
        val scope = CoroutineScope(Dispatchers.Default)
        try {
            VoiceDiagnostics.clear()
            // Le moteur rend la même question trois fois, de plus en plus complète : c'est ce que
            // les bots ont vu (« vous êtes là » en boucle). **Un** message doit partir.
            val reply = FakeReply(scope, durationMs = 150)
            val engine = ScriptedEngine(
                listOf(
                    quickScenario("vous êtes là"),
                    quickScenario("vous êtes là ?"),
                    quickScenario("Vous êtes là, les gars ?"),
                ),
                scope,
            )
            val session = session(scope, engine, reply)
            val room = SteadyRoom(scope)
            val group = GroupVoice(room, session, scope)

            group.start()
            session.onQuestion = group::ask
            group.listen()

            await("la question part", timeoutMs = 20_000) { room.asked.isNotEmpty() }
            // Laisse le temps aux deux reprises du moteur d'arriver (l'écoute est déjà rouverte).
            Thread.sleep(2_500)

            assertEquals("une seule question dans la salle", 1, room.asked.size)
            assertEquals("et une seule réponse lue", 1, reply.spoken.size)
            assertTrue(
                "le journal dit que la question ne repart pas",
                VoiceDiagnostics.lines().any { it.contains("question déjà posée") },
            )
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `une salle qui ne repond pas le dit, et le micro revient`() {
        // La salle accusait réception, se déclarait au travail, et ne produisait **rien** : l'écran
        // restait sur « les bots discutent » sans un mot. L'app doit le dire, et rendre le micro.
        val scope = CoroutineScope(Dispatchers.Default)
        try {
            val reply = FakeReply(scope, durationMs = 300)
            val engine = ScriptedEngine(listOf(scenario(QUESTION_A)), scope)
            val session = session(scope, engine, reply)
            val room = SilentRoom(scope)
            val group = GroupVoice(room, session, scope)

            group.start()
            session.onQuestion = group::ask
            group.listen()
            await("la question part", timeoutMs = 10_000) { room.asked.isNotEmpty() }
            await("la salle est déclarée ne pas répondre", timeoutMs = 25_000) { group.stalled.value }
            // Les deux lignes du jugement s'écrivent juste après le drapeau : on les attend aussi.
            await("le journal dit la salle qui ne répond pas", timeoutMs = 5_000) {
                VoiceDiagnostics.lines().any { it.contains("rien n'est venu de la salle") }
            }
            await("et le tour est déclaré fini", timeoutMs = 5_000) {
                VoiceDiagnostics.lines().any { it.contains("le tour est fini — l'écoute reprend") }
            }
            assertEquals("rien n'a été lu", emptyList<String>(), reply.spoken.toList())
            // Le micro est revenu : la personne n'est jamais enfermée.
            await("le micro revient", timeoutMs = 10_000) {
                session.phase.value == VoicePhase.LISTENING
            }
            // Et la légende ne dit plus « les bots discutent » : elle dit que personne ne répond.
            assertEquals(
                "Nobody is answering",
                groupVoicePhaseTitle(
                    VoicePhase.IDLE, speaker = null, writer = null, isTalking = true, stalled = true,
                ),
            )
        } finally {
            scope.cancel()
        }
    }

    fun `le micro reste ferme tant qu un envoi attend sa reprise`() {
        val scope = CoroutineScope(Dispatchers.Default)
        try {
            val reply = FakeReply(scope, durationMs = 150)
            val engine = ScriptedEngine(listOf(quickScenario(QUESTION_A)), scope)
            val session = session(scope, engine, reply)
            // Le message est encore en route (essai en cours, ou reprise espacée) : la salle ne peut
            // pas avoir répondu, et le micro ne doit pas s'ouvrir avant que le message soit passé.
            val room = SendingRoom(scope)
            val group = GroupVoice(room, session, scope)

            group.start()
            session.onQuestion = group::ask
            group.listen()
            await("la question part", timeoutMs = 20_000) { room.asked.isNotEmpty() }
            Thread.sleep(1_200)
            assertEquals(
                "l'écoute ne se rouvre pas tant que l'envoi n'est pas passé",
                VoicePhase.THINKING,
                session.phase.value,
            )
            assertEquals(
                "l'état le dit : les bots discutent",
                "The bots are talking",
                groupTitle(session, room, group),
            )

            // Le message est passé (le serveur l'a pris) : l'écoute revient.
            room.sending.value = false
            await("l'écoute revient une fois le message passé", timeoutMs = 15_000) {
                session.phase.value == VoicePhase.LISTENING
            }
        } finally {
            scope.cancel()
        }
    }

    /** Le titre que l'écran affiche, calculé comme `VoiceScreen` le fait. */
    private fun groupTitle(session: VoiceSession, room: GroupVoiceThread, group: GroupVoice): String =
        groupVoicePhaseTitle(
            session.phase.value,
            group.speakerId.value,
            null,
            room.isTalking.value,
            false,
            group.queued.value,
            room.pendingSends.value,
        )

    @Test
    fun `l etat affiche ne dit pas pret pendant une reponse, et le dit a la fin`() {
        val scope = CoroutineScope(Dispatchers.Default)
        try {
            val reply = FakeReply(scope, durationMs = 600)
            val engine = ScriptedEngine(listOf(quickScenario(QUESTION_A), quickScenario(QUESTION_B)), scope)
            val session = session(scope, engine, reply)
            val room = SteadyRoom(scope)
            val group = GroupVoice(room, session, scope)

            group.start()
            session.onQuestion = group::ask
            group.listen()

            await("la question part", timeoutMs = 20_000) { room.asked.isNotEmpty() }
            // Pendant la réponse : « <NOM> PARLE », jamais « PRÊT ».
            await("une réponse se lit", timeoutMs = 15_000) { session.phase.value == VoicePhase.SPEAKING }
            assertTrue(
                "la légende nomme le bot qui parle",
                groupTitle(session, room, group).endsWith(" is speaking"),
            )
            // En français, c'est ce que la personne lit, en capitales.
            assertEquals(
                "SCOUT PARLE",
                OnyxLocalization.translate(groupTitle(session, room, group), Locale.FRENCH).uppercase(),
            )

            // Le tour fini : le micro revient, et l'écran le dit — « À L'ÉCOUTE », pas « PRÊT ».
            await("le tour est fini, le micro revient", timeoutMs = 20_000) {
                session.phase.value == VoicePhase.LISTENING && group.queued.value == 0
            }
            assertEquals("À L’ÉCOUTE pendant que le micro écoute", "Listening", groupTitle(session, room, group))
            assertNotEquals(
                "jamais PRÊT tant que quelque chose tourne",
                "PRÊT",
                OnyxLocalization.translate(groupTitle(session, room, group), Locale.FRENCH).uppercase(),
            )

            // Plus rien ne tourne et plus rien n'attend (le Stop rouge) : là, PRÊT.
            group.halt()
            assertEquals("PRÊT quand le tour est terminé", "Ready", groupTitle(session, room, group))
            assertEquals(
                "PRÊT",
                OnyxLocalization.translate(groupTitle(session, room, group), Locale.FRENCH).uppercase(),
            )
        } finally {
            scope.cancel()
        }
    }

    private companion object {
        /** Le temps que met la salle entre deux réponses (plus long que la lecture). */
        const val ROOM_GAP_MS = 700L
        const val QUESTION_A = "Quel temps fait-il demain à Lyon ?"
        const val QUESTION_B = "Et à Paris, mardi matin ?"
        const val QUESTION_C = "Et le vent, il souffle ?"
        const val ANSWER_A = "Demain à Lyon, ciel dégagé et dix-huit degrés l'après-midi."
        const val ANSWER_B = "À Paris mardi, pluie fine le matin, quatorze degrés."
        const val ANSWER_C = "Et le vent souffle à trente kilomètres heure."
    }

    /** Le moteur de test : il rejoue le scénario suivant à chaque écoute neuve. */
    private class ScriptedEngine(
        private val scenarios: List<List<HeardFrame>>,
        private val scope: CoroutineScope,
        private val stepMs: Long = 40,
    ) : SpeechInput {
        private val _state = MutableStateFlow(SpeechState())
        override val state: StateFlow<SpeechState> = _state.asStateFlow()
        override var offlineWanted: Boolean = false
        override var answered: Boolean = false
            private set
        override var frameCount: Int = 0
            private set
        override val canRetry: Boolean = true
        override val attempts: Int = 0
        override val isAvailable: Boolean = true
        override val prefersOffline: Boolean = false
        override val requestedLocale: String = "fr-FR"
        override var onFinished: (() -> Unit)? = null

        private var job: Job? = null
        private var next = 0

        override fun start(fresh: Boolean) {
            job?.cancel()
            val frames = scenarios[next.coerceAtMost(scenarios.lastIndex)]
            next += 1
            _state.value = SpeechState(isListening = true)
            job = scope.launch {
                frames.forEach { frame ->
                    answered = true
                    frameCount += 1
                    _state.update {
                        it.copy(
                            isListening = !frame.isFinal,
                            isFinal = frame.isFinal,
                            transcript = frame.transcript,
                            level = frame.level,
                        )
                    }
                    delay(stepMs)
                }
                _state.update { it.copy(isListening = false, level = 0f) }
                onFinished?.invoke()
            }
        }

        override fun stop() {
            job?.cancel()
            job = null
            _state.update { it.copy(isListening = false, level = 0f, isFinal = true) }
        }

        override fun cancel() {
            job?.cancel()
            job = null
            _state.value = SpeechState()
        }

        override fun release() = cancel()
    }

    /**
     * La salle scriptée : elle reçoit la question, puis **un bot après l'autre** écrit sa réponse —
     * la première finit avant que la deuxième ne commence.
     */
    private class ScriptedRoom(private val scope: CoroutineScope) : GroupVoiceThread {
        override val roomName: String = "Weekend hike"
        override val server: ServerConfig = ServerConfig(id = "demo")

        private val _lines = MutableStateFlow<List<GroupVoiceLine>>(emptyList())
        override val lines: StateFlow<List<GroupVoiceLine>> = _lines.asStateFlow()

        private val _talking = MutableStateFlow(false)
        override val isTalking: StateFlow<Boolean> = _talking.asStateFlow()

        override val waitsOnPerson: StateFlow<Boolean> = MutableStateFlow(false)

        val asked = CopyOnWriteArrayList<String>()
        private var seq = 0

        override fun ask(question: String, askId: String) {
            asked += question
            seq += 1
            _lines.value = _lines.value + GroupVoiceLine(
                id = "q$seq", profile = null, name = "", text = question, seq = seq, after = seq - 1,
            )
            _talking.value = true
            scope.launch {
                // **Trois** bots, un après l'autre : c'est la file d'une salle.
                // La salle écrit **plus lentement** que la voix ne lit (comme un vrai serveur :
                // 2,2 s entre deux réponses pour 1,4 s de lecture) : la file se vide donc entre
                // deux bots, et c'est là que l'écoute ne doit **pas** se rouvrir.
                delay(ROOM_GAP_MS)
                say("scout", ANSWER_A)
                delay(ROOM_GAP_MS)
                say("sage", ANSWER_B)
                delay(ROOM_GAP_MS)
                say("quill", ANSWER_C)
                _talking.value = false
            }
        }

        private fun say(who: String, text: String) {
            seq += 1
            _lines.value = _lines.value + GroupVoiceLine(
                id = "a$seq", profile = who, name = who.replaceFirstChar { it.uppercase() },
                text = text, seq = seq, after = seq - 1,
            )
        }

        override fun haltTalking() {
            _talking.value = false
        }
    }
}

/**
 * La salle qui **se déclare au repos puis écrit encore** : c'est le cas où une réponse arrive juste
 * après la fin du tour (un serveur qui rogne, une file qui se vide une seconde après son état).
 */
private class LateReplyRoom(private val scope: CoroutineScope) : GroupVoiceThread {
    override val roomName: String = "Weekend hike"
    override val server: ServerConfig = ServerConfig(id = "demo")

    private val _lines = MutableStateFlow<List<GroupVoiceLine>>(emptyList())
    override val lines: StateFlow<List<GroupVoiceLine>> = _lines.asStateFlow()

    private val _talking = MutableStateFlow(false)
    override val isTalking: StateFlow<Boolean> = _talking.asStateFlow()

    override val waitsOnPerson: StateFlow<Boolean> = MutableStateFlow(false)

    private var seq = 0

    override fun ask(question: String, askId: String) {
        seq += 1
        add(null, "", question, seq)
        _talking.value = true
        scope.launch {
            delay(150)
            seq += 1
            add("scout", "Scout", ANSWER_A, seq)
            // La salle se tait… et écrit pourtant encore, 300 ms plus tard.
            _talking.value = false
            delay(300)
            seq += 1
            add("sage", "Sage", ANSWER_B, seq)
        }
    }

    private fun add(profile: String?, name: String, text: String, seq: Int) {
        _lines.value = _lines.value + GroupVoiceLine(
            id = "late-$seq", profile = profile, name = name, text = text, seq = seq, after = seq - 1,
        )
    }

    override fun haltTalking() {
        _talking.value = false
    }

    private companion object {
        const val ANSWER_A = "Demain à Lyon, ciel dégagé et dix-huit degrés l'après-midi."
        const val ANSWER_B = "À Paris mardi, pluie fine le matin, quatorze degrés."
    }
}


/**
 * La salle qui ecrit sa **derniere** reponse et se declare au repos **dans le meme souffle** : la
 * ligne est publiee avant que le collecteur ne la voie, et un tour juge sur un instantane dejoue la
 * manquait — la reponse partait alors dans une file d'ecoute, et etait lachee.
 */
private class SameBreathRoom(private val scope: CoroutineScope) : GroupVoiceThread {
    override val roomName: String = "Weekend hike"
    override val server: ServerConfig = ServerConfig(id = "demo")

    private val _lines = MutableStateFlow<List<GroupVoiceLine>>(emptyList())
    override val lines: StateFlow<List<GroupVoiceLine>> = _lines.asStateFlow()

    private val _talking = MutableStateFlow(false)
    override val isTalking: StateFlow<Boolean> = _talking.asStateFlow()

    override val waitsOnPerson: StateFlow<Boolean> = MutableStateFlow(false)

    private var seq = 0

    override fun ask(question: String, askId: String) {
        seq += 1
        add(null, "", question, seq)
        _talking.value = true
        scope.launch {
            delay(200)
            seq += 1
            add("scout", "Scout", ANSWER_A, seq)
            delay(400)
            seq += 1
            add("sage", "Sage", ANSWER_B, seq)
            delay(600)
            // Les deux gestes du meme souffle : ecrire, et se declarer au repos.
            seq += 1
            add("quill", "Quill", ANSWER_C, seq)
            _talking.value = false
        }
    }

    private fun add(profile: String?, name: String, text: String, seq: Int) {
        _lines.value = _lines.value + GroupVoiceLine(
            id = "breath-$seq", profile = profile, name = name, text = text, seq = seq, after = seq - 1,
        )
    }

    override fun haltTalking() {
        _talking.value = false
    }

    private companion object {
        const val ANSWER_A = "Demain à Lyon, ciel dégagé et dix-huit degrés l'après-midi."
        const val ANSWER_B = "À Paris mardi, pluie fine le matin, quatorze degrés."
        const val ANSWER_C = "Et le vent souffle à trente kilomètres heure."
    }
}

/**
 * La salle qui **repond tard** : elle prend la question, se declare au travail, et n'ecrit sa
 * reponse qu'au bout de [delayMs] — le temps qu'un vrai modele met a repondre. C'est ce qui
 * manquait au tour : le garde-fou se comptait depuis l'ouverture de la scene, et le micro se
 * rouvrait avant la reponse (qui etait alors jetee).
 */
private class SlowReplyRoom(private val scope: CoroutineScope, private val delayMs: Long) : GroupVoiceThread {
    override val roomName: String = "Weekend hike"
    override val server: ServerConfig = ServerConfig(id = "demo")

    private val _lines = MutableStateFlow<List<GroupVoiceLine>>(emptyList())
    override val lines: StateFlow<List<GroupVoiceLine>> = _lines.asStateFlow()

    private val _talking = MutableStateFlow(false)
    override val isTalking: StateFlow<Boolean> = _talking.asStateFlow()

    override val waitsOnPerson: StateFlow<Boolean> = MutableStateFlow(false)

    val asked = CopyOnWriteArrayList<String>()
    val askedIds = CopyOnWriteArrayList<String>()
    private var seq = 0

    override fun ask(question: String, askId: String) {
        asked += question
        askedIds += askId
        seq += 1
        add(null, "", question, seq)
        _talking.value = true
        // Elle ne repond qu'a la **premiere** question : une reponse jetee se voit donc (un
        // deuxieme tour ne vient pas la masquer en repondant la meme phrase).
        if (asked.size > 1) return
        scope.launch {
            delay(delayMs)
            seq += 1
            add("scout", "Scout", ANSWER_A, seq)
            _talking.value = false
        }
    }

    private fun add(profile: String?, name: String, text: String, seq: Int) {
        _lines.value = _lines.value + GroupVoiceLine(
            id = "slow-$seq", profile = profile, name = name, text = text, seq = seq, after = seq - 1,
        )
    }

    override fun haltTalking() {
        _talking.value = false
    }

    private companion object {
        const val ANSWER_A = "Demain à Lyon, ciel dégagé et dix-huit degrés l'après-midi."
    }
}

/**
 * La salle qui **ne repond jamais** : elle accuse reception, se declare au travail, et rien ne
 * vient — c'est la salle du proprietaire. L'app doit le dire (et rendre le micro), pas laisser
 * « les bots discutent » tourner dans le vide.
 */
private class SilentRoom(private val scope: CoroutineScope) : GroupVoiceThread {
    override val roomName: String = "Weekend hike"
    override val server: ServerConfig = ServerConfig(id = "demo")

    private val _lines = MutableStateFlow<List<GroupVoiceLine>>(emptyList())
    override val lines: StateFlow<List<GroupVoiceLine>> = _lines.asStateFlow()

    private val _talking = MutableStateFlow(false)
    override val isTalking: StateFlow<Boolean> = _talking.asStateFlow()

    override val waitsOnPerson: StateFlow<Boolean> = MutableStateFlow(false)

    val asked = CopyOnWriteArrayList<String>()
    val askedIds = CopyOnWriteArrayList<String>()
    private var seq = 0

    override fun ask(question: String, askId: String) {
        asked += question
        askedIds += askId
        seq += 1
        _lines.value = _lines.value + GroupVoiceLine(
            id = "silent-$seq", profile = null, name = "", text = question, seq = seq, after = seq - 1,
        )
        // La salle travaille… et rien ne vient jamais.
        _talking.value = true
    }

    override fun haltTalking() {
        _talking.value = false
    }
}

private class NoRoute : EarRouteController {
    override fun apply(output: EarOutput) = Unit
    override fun restore() = Unit
    override fun hasExternalRoute(): Boolean = false
    override fun release() = Unit
}

private class NoProximity : ProximityWatcher {
    override fun start() = Unit
    override fun stop() = Unit
    override val isAvailable: Boolean = false
}

private class NeverFaceDown : FaceDownSensing {
    override fun start() = Unit
    override fun stop() = Unit
    override fun isFaceDown(): Boolean = false
}

/**
 * Le lecteur de test : il « dit » la réplique pendant [durationMs], puis rend la main. Il compte ce
 * qui se lirait **en même temps** — deux voix superposées seraient un tour volé.
 */
private class FakeReply(
    private val scope: CoroutineScope,
    private val durationMs: Long = 500,
) : SpeechReply {
    private val _isSpeaking = MutableStateFlow(false)
    override val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()
    private val _level = MutableStateFlow(0f)
    override val replyLevel: StateFlow<Float> = _level.asStateFlow()
    override var onFinished: (() -> Unit)? = null
    private var atEar = false
    override val earRouting: Boolean get() = atEar

    val spoken = CopyOnWriteArrayList<String>()
    val speakingNow = java.util.concurrent.atomic.AtomicInteger(0)
    val levelNow get() = _level
    val overlaps = java.util.concurrent.atomic.AtomicInteger(0)
    var onSpeak: ((String, String?) -> Unit)? = null

    override fun setLanguage(tag: String) = Unit
    override fun setEarRouting(atEar: Boolean) {
        this.atEar = atEar
    }

    override fun speak(text: String, languageTag: String?, speaker: String?) {
        if (speakingNow.incrementAndGet() > 1) overlaps.incrementAndGet()
        spoken += text
        onSpeak?.invoke(text, speaker)
        _isSpeaking.value = true
        scope.launch {
            repeat((durationMs / 50).toInt().coerceAtLeast(1)) {
                _level.value = 0.4f
                delay(50)
            }
            _level.value = 0f
            _isSpeaking.value = false
            speakingNow.decrementAndGet()
            onFinished?.invoke()
        }
    }

    override fun pause() = Unit
    override fun resume(): Boolean = _isSpeaking.value

    override fun stop() {
        if (_isSpeaking.value) speakingNow.decrementAndGet()
        _level.value = 0f
        _isSpeaking.value = false
    }

    override fun shutdown() = stop()
}

/**
 * La salle qui répond **tout de suite**, un bot par tour : de quoi rejouer vingt tours sans attendre.
 */
private class SteadyRoom(private val scope: CoroutineScope) : GroupVoiceThread {
    override val roomName: String = "Weekend hike"
    override val server: ServerConfig = ServerConfig(id = "demo")

    private val _lines = MutableStateFlow<List<GroupVoiceLine>>(emptyList())
    override val lines: StateFlow<List<GroupVoiceLine>> = _lines.asStateFlow()

    private val _talking = MutableStateFlow(false)
    override val isTalking: StateFlow<Boolean> = _talking.asStateFlow()

    override val waitsOnPerson: StateFlow<Boolean> = MutableStateFlow(false)

    val asked = CopyOnWriteArrayList<String>()
    val askedIds = CopyOnWriteArrayList<String>()
    private var seq = 0

    override fun ask(question: String, askId: String) {
        asked += question
        askedIds += askId
        seq += 1
        add(null, "", question, seq)
        _talking.value = true
        scope.launch {
            delay(60)
            seq += 1
            // La réponse ne répète pas la question : la garde anti-écho la prendrait pour un écho.
            add("scout", "Scout", "Bien reçu. Le ciel sera dégagé et doux, sans vent.", seq)
            _talking.value = false
        }
    }

    private fun add(profile: String?, name: String, text: String, at: Int) {
        _lines.value = _lines.value + GroupVoiceLine(
            id = "steady-$at", profile = profile, name = name, text = text, seq = at, after = at - 1,
        )
    }

    override fun haltTalking() {
        _talking.value = false
    }
}

/**
 * La salle dont un message est **encore en route** : le serveur n'a rien reçu, donc rien ne peut
 * être répondu, et le micro ne doit pas se rouvrir pour autant.
 */
private class SendingRoom(private val scope: CoroutineScope) : GroupVoiceThread {
    override val roomName: String = "Weekend hike"
    override val server: ServerConfig = ServerConfig(id = "demo")

    private val _lines = MutableStateFlow<List<GroupVoiceLine>>(emptyList())
    override val lines: StateFlow<List<GroupVoiceLine>> = _lines.asStateFlow()

    private val _talking = MutableStateFlow(false)
    override val isTalking: StateFlow<Boolean> = _talking.asStateFlow()

    override val waitsOnPerson: StateFlow<Boolean> = MutableStateFlow(false)

    /** Vrai tant que le message n'est pas arrivé au serveur. */
    val sending = MutableStateFlow(true)

    override val pendingSends: StateFlow<Boolean> = sending

    val asked = CopyOnWriteArrayList<String>()
    private var seq = 0

    override fun ask(question: String, askId: String) {
        asked += question
        seq += 1
        _lines.value = _lines.value + GroupVoiceLine(
            id = "sending-$seq", profile = null, name = "", text = question, seq = seq, after = seq - 1,
        )
        // L'écoute : le message part une fois la reprise passée (`sending` repasse à faux).
        scope.launch {
            while (sending.value) delay(50)
            delay(80)
            seq += 1
            _lines.value = _lines.value + GroupVoiceLine(
                id = "sending-$seq",
                profile = "scout",
                name = "Scout",
                text = ANSWER,
                seq = seq,
                after = seq - 1,
            )
        }
    }

    override fun haltTalking() {
        _talking.value = false
    }

    private companion object {
        const val ANSWER = "Oui, je suis là — ton message vient d'arriver."
    }
}
