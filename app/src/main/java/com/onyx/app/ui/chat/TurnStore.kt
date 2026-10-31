package com.onyx.app.ui.chat

import com.onyx.app.data.model.ChatMessage
import com.onyx.app.data.net.TurnEvent
import com.onyx.app.data.net.TransportError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Ce qui survit à l'écran : ce qu'un tour écrit, même quand personne ne regarde.
 *
 * Sans écran, il n'y a plus personne pour appeler `persistCurrent()` : c'est le magasin qui écrit
 * la réponse au fil de son avancement, sinon la réponse d'un tour fini sans écran serait perdue.
 */
interface TurnJournal {
    /** La réponse d'un tour, au fil de son avancement. */
    fun noteAssistant(conversationId: String, message: ChatMessage)

    /** L'issue d'un tour qui n'est plus en cours. */
    fun turnEnded(conversationId: String, session: TurnSession)

    companion object {
        /** Un magasin sans disque : les tests, et les fenêtres sans dépôt. */
        val NONE: TurnJournal = object : TurnJournal {
            override fun noteAssistant(conversationId: String, message: ChatMessage) = Unit
            override fun turnEnded(conversationId: String, session: TurnSession) = Unit
        }
    }
}

/**
 * Les tours en cours du processus, par conversation.
 *
 * C'est **ici** que vit un tour, pas dans le ViewModel d'un écran : quitter la conversation,
 * changer de route, détruire l'activité ou éteindre l'écran ne l'interrompt donc pas — la
 * coroutine tourne dans la portée du processus, et la réponse s'accumule dans sa session.
 * Au retour, l'écran se rattache à la session et le fil reprend là où il en est.
 *
 * Miroir de la référence : `Conversation` et son `TurnDeparture`, tenus par le magasin de
 * conversations (`ConversationStore`) et non par la vue. Comme là-bas, un tour **arrêté** est
 * arrêté chez l'hôte, et un tour dont l'app s'est seulement **détachée** y continue.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TurnStore(
    private val scope: CoroutineScope,
    private val journal: TurnJournal = TurnJournal.NONE,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _sessions = MutableStateFlow<Map<String, TurnSession>>(emptyMap())

    /** Les tours en cours ou finis récemment, par conversation. */
    val sessions: StateFlow<Map<String, TurnSession>> = _sessions.asStateFlow()

    fun session(conversationId: String): TurnSession? = _sessions.value[conversationId]

    /** Ce qu'un tour en cours écrit pour cette conversation — `null` quand il n'y en a pas. */
    fun live(conversationId: String): Flow<TurnLive?> =
        _sessions
            .map { it[conversationId] }
            .distinctUntilChanged()
            .flatMapLatest { session -> session?.live ?: flowOf(null) }

    /**
     * Démarre un tour. [stream] construit le flux du transport pour ce tour : il reçoit son
     * [com.onyx.app.data.net.TurnDeparture] pour savoir s'il faut interrompre l'hôte en partant.
     */
    fun start(
        conversationId: String,
        assistantId: String,
        userText: String,
        serverSessionId: String? = null,
        stream: suspend (com.onyx.app.data.net.TurnDeparture) -> Flow<TurnEvent>,
    ): TurnSession {
        // Un tour remplacé dans la même conversation est arrêté, pas abandonné : l'hôte est
        // prévenu qu'il ne sert plus à rien.
        stop(conversationId)
        val session = TurnSession(
            conversationId = conversationId,
            assistantId = assistantId,
            userText = userText,
            startedAt = clock(),
            serverSessionId = serverSessionId,
        )
        _sessions.update { it + (conversationId to session) }
        scope.launch {
            try {
                stream(session.departure).collect { event -> note(session, event) }
                session.succeed(clock())
            } catch (error: CancellationException) {
                // Arrêté ou abandonné : la session le sait déjà (stop/leave sont passés avant).
                throw error
            } catch (error: TransportError) {
                if (error is TransportError.Cancelled) session.succeed(clock()) else session.fail(error, clock())
            } catch (error: Exception) {
                session.fail(TransportError.Malformed(error.message ?: error.javaClass.simpleName), clock())
            } finally {
                journal.turnEnded(conversationId, session)
            }
        }
        return session
    }

    /** L'utilisateur arrête le tour : il est interrompu chez l'hôte. */
    fun stop(conversationId: String) {
        val session = _sessions.value[conversationId] ?: return
        session.stop(clock())
        session.job?.cancel()
        session.job = null
        journal.turnEnded(conversationId, session)
    }

    /**
     * L'app se détache du tour : l'hôte le finit, et la réponse est là au retour.
     *
     * Utilisé là où le tour ne peut plus être suivi du tout (fin de processus, changement de
     * serveur) : on préfère une réponse finie côté hôte à un tour tué.
     */
    fun leave(conversationId: String) {
        val session = _sessions.value[conversationId] ?: return
        if (session.isFinished) return
        session.departure.leave()
        session.job?.cancel()
        session.job = null
        _sessions.update { it - conversationId }
    }

    /** La conversation est supprimée : son tour est arrêté, jamais laissé tourner. */
    fun forget(conversationId: String) {
        stop(conversationId)
        _sessions.update { it - conversationId }
    }

    /** Tout arrêter (changement de serveur, effacement) : chaque tour est interrompu. */
    fun stopAll() {
        _sessions.value.keys.toList().forEach { stop(it) }
    }

    /** Un événement écrit dans la session, et sur disque quand sa forme a changé. */
    private fun note(session: TurnSession, event: TurnEvent) {
        session.apply(event, clock())
        if (session.shouldPersist()) {
            journal.noteAssistant(session.conversationId, session.message)
        }
    }

    /** Une modification de la réponse en cours (une carte répondue, un secret donné). */
    fun update(conversationId: String, messageId: String, transform: (ChatMessage) -> ChatMessage): Boolean {
        val session = _sessions.value[conversationId] ?: return false
        if (session.assistantId != messageId) return false
        session.edit(transform)
        if (session.shouldPersist()) journal.noteAssistant(conversationId, session.message)
        return true
    }

    /** Le modèle que ce tour emploie vraiment : la barre du haut le nomme. */
    fun noteModel(conversationId: String, messageId: String, model: String): Boolean {
        val session = _sessions.value[conversationId] ?: return false
        if (session.assistantId != messageId) return false
        session.noteModel(model)
        return true
    }

    /** Les tours encore ouverts, arrêtés ou abandonnés à la fin du processus. */
    fun openSessions(): List<TurnSession> = _sessions.value.values.filter { !it.isFinished }
}
