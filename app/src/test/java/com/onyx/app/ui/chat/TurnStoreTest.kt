package com.onyx.app.ui.chat

import com.onyx.app.data.model.ChatMessage
import com.onyx.app.data.net.TurnEvent
import com.onyx.app.data.net.TransportError
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Un tour vit dans le magasin, pas dans l'écran.
 *
 * Le propriétaire: « si j'ai le malheur de faire retour, il ne continue pas à travailler en
 * arrière-plan ». Ici, on quitte la conversation (l'écran se détache, puis il est détruit) et le
 * tour doit continuer, finir sa réponse, l'écrire sur disque, et la rendre au retour.
 *
 * Ce qui est vérifié ici porte sur la **portée du travail** (le magasin) et sur ce qui arrive à la
 * réponse quand plus personne ne regarde; l'écran lui-même est vérifié par capture.
 */
class TurnStoreTest {

    /** Les réponses écrites sur disque, sans écran. */
    private class JournalSpy : TurnJournal {
        val written = CopyOnWriteArrayList<ChatMessage>()
        val ended = CopyOnWriteArrayList<TurnSession>()

        override fun noteAssistant(conversationId: String, message: ChatMessage) {
            written += message
        }

        override fun turnEnded(conversationId: String, session: TurnSession) {
            ended += session
        }
    }

    /** Un flux qui ne dit rien tant qu'on ne lui demande pas: un serveur lent, ou en file. */
    private class SlowStream {
        private val events = Channel<TurnEvent>(Channel.UNLIMITED)
        val departures = CopyOnWriteArrayList<com.onyx.app.data.net.TurnDeparture>()
        val closed = java.util.concurrent.atomic.AtomicBoolean(false)

        fun flow(): Flow<TurnEvent> {
            val departure = com.onyx.app.data.net.TurnDeparture()
            departures += departure
            return kotlinx.coroutines.flow.flow {
                try {
                    events.receiveAsFlow().collect { emit(it) }
                } finally {
                    closed.set(true)
                }
            }
        }

        suspend fun say(event: TurnEvent) {
            events.send(event)
        }

        fun finish() {
            events.close()
        }
    }

    private fun store(journal: TurnJournal = TurnJournal.NONE, scope: CoroutineScope) = TurnStore(scope, journal)

    @Test
    fun `quitter la conversation ne tue pas le tour, et sa reponse est là au retour`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val journal = JournalSpy()
        val turns = store(journal, scope)
        val stream = SlowStream()

        val session = turns.start("fil", "answer", "Compte les routes.", null) { stream.flow() }
        // L'écran s'en va: plus personne n'observe la session.
        assertTrue(session.phase.isStreaming)

        // Le serveur travaille et répond pendant ce temps.
        stream.say(TurnEvent.ReasoningDelta("je cherche"))
        stream.say(TurnEvent.ToolStarted(name = "terminal", preview = null))
        stream.say(TurnEvent.ToolFinished(name = "terminal", failed = false))
        stream.say(TurnEvent.TextDelta("54 routes, "));
        stream.say(TurnEvent.TextDelta("toutes cohérentes."))
        stream.finish()

        withTimeout(5_000) {
            while (!session.isFinished) kotlinx.coroutines.delay(10)
        }

        // La réponse est complète et enregistrée, alors qu'aucun écran ne l'a jamais vue entière.
        assertEquals("54 routes, toutes cohérentes.", session.message.text)
        assertEquals(1, session.message.tools.size)
        assertTrue(session.message.reasoning.isNotEmpty())
        assertFalse(session.message.isStreaming)
        assertTrue("la fin du tour est écrite", journal.ended.isNotEmpty())
        assertEquals("54 routes, toutes cohérentes.", journal.written.last().text)
        assertTrue("rien de la réponse n'est peint comme le message de l'utilisateur",
            session.message.role == com.onyx.app.data.model.Role.ASSISTANT &&
                session.message.text != session.userText)
    }

    @Test
    fun `le tour continue quand on ouvre une autre conversation, et se retrouve en revenant`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val turns = store(scope = scope)
        val stream = SlowStream()

        val session = turns.start("fil-a", "answer-a", "Question longue et précise.", null) { stream.flow() }

        // On ouvre une autre conversation: le magasin garde le tour, il continue d'écrire.
        stream.say(TurnEvent.TextDelta("Réponse du premier fil."))
        withTimeout(5_000) {
            while (session.message.text.isEmpty()) kotlinx.coroutines.delay(10)
        }
        // Le tour est toujours vivant et son état est consultable par conversation.
        val live = session.live.value
        assertEquals("fil-a", live.conversationId)
        assertEquals("Réponse du premier fil.", live.message.text)
        assertEquals(TurnStep.WRITING, live.phase.step)
        assertTrue(live.phase.isStreaming)
        stream.finish()
    }

    @Test
    fun `l'arret arrete vraiment, l'abandon laisse l'hote finir`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val turns = store(scope = scope)

        val stoppedStream = SlowStream()
        val stopped = turns.start("arretee", "a1", "Un message de l'utilisateur assez long.", null) { stoppedStream.flow() }
        turns.stop("arretee")
        assertTrue("l'arrêt est un arrêt, pas un abandon", !stopped.departure.hasLeft)
        assertEquals(TurnStep.STOPPED, stopped.phase.step)
        assertTrue("le tour arrêté n'est plus en cours", !turns.session("arretee")!!.phase.isStreaming)

        val leftStream = SlowStream()
        val left = turns.start("abandonnee", "a2", "Un autre message de l'utilisateur long.", null) { leftStream.flow() }
        turns.leave("abandonnee")
        assertTrue("l'abandon laisse l'hôte finir", left.departure.hasLeft)
        assertNull("la session abandonnée est oubliée ici", turns.session("abandonnee"))
    }

    @Test
    fun `supprimer une conversation arrete son tour au lieu de le laisser tourner`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val turns = store(scope = scope)
        val stream = SlowStream()

        val session = turns.start("supprimee", "a1", "Une question qui n'aura plus de fil.", null) { stream.flow() }
        turns.forget("supprimee")

        assertFalse("un tour sans conversation ne tourne plus", session.phase.isStreaming)
        assertNull(turns.session("supprimee"))
    }

    @Test
    fun `un tour remplace dans la meme conversation est arrete, pas abandonne`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val turns = store(scope = scope)
        val first = SlowStream()
        val second = SlowStream()

        val replaced = turns.start("fil", "a1", "Premier message utilisateur assez long.", null) { first.flow() }
        val current = turns.start("fil", "a2", "Second message utilisateur assez long.", null) { second.flow() }

        assertFalse("le remplacé est arrêté chez l'hôte", replaced.departure.hasLeft)
        assertEquals(TurnStep.STOPPED, replaced.phase.step)
        assertEquals("a2", turns.session("fil")?.assistantId)
        assertTrue(current.phase.isStreaming)
    }

    @Test
    fun `une connexion coupee dit l'echec, et le tour est fini`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val turns = store(scope = scope)
        val stream = kotlinx.coroutines.flow.flow<TurnEvent> {
            emit(TurnEvent.TextDelta("Bonjour"))
            throw TransportError.StreamLost("flux coupé")
        }

        val session = turns.start("fil", "answer", "Bonjour, une question assez longue.", null) { stream }
        withTimeout(5_000) {
            while (!session.isFinished) kotlinx.coroutines.delay(10)
        }

        assertEquals(TurnStep.FAILED, session.phase.step)
        assertFalse("une connexion coupée n'est pas une injoignabilité", session.phase.connectivity)
        assertEquals("flux coupé", session.failure?.message)
        assertEquals("Bonjour", session.message.text)
    }
}
