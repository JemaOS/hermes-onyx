package com.onyx.app.ui.bots

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.onyx.app.data.model.AttachedFileNote
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.serve.BotProfile
import com.onyx.app.data.model.serve.GroupApproval
import com.onyx.app.data.model.serve.GroupMember
import com.onyx.app.data.model.serve.GroupRoom
import com.onyx.app.data.net.ServeClient
import com.onyx.app.data.net.ServeFilesApi
import com.onyx.app.data.net.ServeGroupsRpc
import com.onyx.app.data.net.ServeRpc
import com.onyx.app.data.net.ServerAttachment
import com.onyx.app.data.net.ServerAttachments
import com.onyx.app.ui.chat.PendingAttachment
import com.onyx.app.ui.conversations.GroupChat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Une salle du serveur ouverte : son fil (`GroupRoomFeed`), branché sur le Dashboard de la
 * connexion.
 *
 * Miroir de `GroupChatView` + `GroupFeed` de la référence (Echo/Views/GroupChatView:8) : la salle est
 * suivie tant que l'écran est là, ce qui est écrit part par `groups.send`, et ce qui s'affiche
 * vient du journal de la salle — jamais du fil local de l'application.
 */
class GroupRoomViewModel(
    application: Application,
    private val config: ServerConfig,
    initialRoom: GroupRoom,
    /** Le nom que Onyx donne au bot principal : les membres d'une salle Desktop en héritent. */
    mainName: String = "",
) : AndroidViewModel(application), GroupVoiceThread {

    private val rpc = ServeGroupsRpc(ServeRpc(config))
    private val attachments = ServerAttachments(ServeFilesApi(ServeClient(config)))

    /** Les visages des membres, tels que le fil et la scène vocale les dessinent. */
    private val faceStore = BotFaceStore(application)
    val face: (String) -> String? = { name -> faceStore.faceOf(name) }

    val feed = GroupRoomFeed(
        initialRoom = initialRoom,
        api = rpc,
        naming = { room -> room.naming(roster = null, mainName = mainName) },
    )
    val state = feed.state

    // — Ce qu'une scène vocale lit dans cette salle (`GroupVoiceThread`) —

    override val server: ServerConfig get() = config

    override val roomName: String get() = state.value.room.name

    /** Le round, tel que la scène vocale le lit : le journal, ce qui part, ce qui s'écrit. */
    private val _lines = state.map { it.voiceLines(face) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    override val lines: StateFlow<List<GroupVoiceLine>> = _lines

    override val isTalking: StateFlow<Boolean> =
        state.map { it.busy }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Un bot qui attend une réponse de la personne : une commande à approuver, ou une relance. */
    override val waitsOnPerson: StateFlow<Boolean> =
        state.map { it.status.waitsOnPerson }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Un message de la personne est encore en route (essai en cours, ou reprise espacée). */
    override val pendingSends: StateFlow<Boolean> = feed.pendingSends

    /**
     * Ce que la salle a refusé, tel quel : une question qui n'est pas partie, un journal illisible,
     * un envoi qui n'a pas atteint la salle. La scène vocale le montre — sans ça, une question
     * refusée disparaissait sans un mot. Ce que la **file d'envoi** dit prime (un message qui n'est
     * pas encore arrivé) ; la salle prend le relais (le journal illisible, la salle terminée).
     */
    override val problem: StateFlow<String?> =
        combine(feed.problem, state.map { it.problem }) { fromFeed, fromRoom -> fromFeed ?: fromRoom }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * La question reconnue part dans la salle (`groups.send`), **une seule fois** : son identifiant
     * est l'`event_id`, et la file d'envoi refuse de le reprendre.
     */
    override fun ask(question: String, askId: String) {
        viewModelScope.launch { feed.ask(question, askId) }
    }

    /** L'arrêt rouge arrête les bots de la salle (`groups.stop`). */
    override fun haltTalking() = stop()

    // Le fil est suivi par l'écran, tant qu'il est à l'écran et que l'application est devant
    // (`GroupRoomScreen`), comme le `task(id: scenePhase == .active)` de la référence.

    /**
     * Dit quelque chose dans la salle. Une salle ne prend que du texte : ce qui est joint part
     * d'abord sur le serveur (« Onyx attachments ») et le message les nomme, comme le fait
     * `GroupFeed.send` de la référence.
     */
    fun send(question: String, pendingAttachments: List<PendingAttachment> = emptyList()) {
        val clean = question.trim()
        if (clean.isEmpty() && pendingAttachments.isEmpty()) return
        val id = feed.beginSend(clean, pendingAttachments.map { it.name })
        viewModelScope.launch {
            try {
                var said = clean
                if (pendingAttachments.isNotEmpty()) {
                    val outgoing = withContext(Dispatchers.IO) {
                        pendingAttachments.map { attachment -> readServerAttachment(attachment) }
                    }
                    val placed = attachments.place(outgoing)
                    said = placed.fold(clean) { text, file ->
                        text + if (file.source.isImage) {
                            AttachedFileNote.picture(file.path)
                        } else {
                            AttachedFileNote.note(file.path)
                        }
                    }
                }
                feed.deliver(id, said)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                feed.failSend(id, error)
            }
        }
    }

    fun stop() {
        viewModelScope.launch { feed.stop() }
    }

    fun retry(task: String) {
        viewModelScope.launch { feed.retry(task) }
    }

    fun answer(approval: GroupApproval, choice: String) {
        viewModelScope.launch { feed.answer(approval, choice) }
    }

    fun rename(name: String, onDone: (String?) -> Unit = {}) {
        viewModelScope.launch {
            try {
                feed.rename(name)
                onDone(null)
            } catch (error: Exception) {
                onDone((error as? com.onyx.app.data.net.TransportError)?.userMessage() ?: error.message)
            }
        }
    }

    /**
     * Termine la salle pour de bon (`groups.disband`) : les bots s'arrêtent et la salle s'en va,
     * sur le serveur aussi. `onEnded` n'est appelé qu'une fois que le serveur a pris.
     */
    fun disband(onEnded: () -> Unit) {
        viewModelScope.launch {
            try {
                rpc.disband(state.value.room.id)
                onEnded()
            } catch (error: Exception) {
                // Le refus se lit à l'écran : la salle ne disparaît pas tant que le serveur ne l'a
                // pas prise, et rien n'est effacé ici.
                feed.report(error)
            }
        }
    }

    private fun readServerAttachment(attachment: PendingAttachment): ServerAttachment {
        val resolver = getApplication<Application>().contentResolver
        val bytes = resolver.openInputStream(attachment.uri)?.use { it.readBytes() }
            ?: throw IOException("Couldn't read ${attachment.name}")
        return ServerAttachment(name = attachment.name, bytes = bytes, isImage = attachment.isImage)
    }
}

/** Les membres d'une salle, comme les bots que le composer connaît (poignée `@`, nom, visage). */
internal fun GroupRoom.composerBots(face: (String) -> String? = { null }): List<BotProfile> =
    members.map { member ->
        BotProfile(
            name = member.profile,
            title = member.displayName,
            isDefault = member.profile == "default",
            faceCode = face(member.profile),
        )
    }

/**
 * La salle du serveur telle que la liste l'a donnée (`GroupChat` → `GroupRoom`) : le fil part de
 * là, puis se met à jour avec ce qu'Hermes en dit (`groups.state`, et sa copie pour une salle
 * qu'Hermes Desktop tient lui-même).
 */
internal fun GroupChat.toRoom(): GroupRoom = GroupRoom(
    id = id,
    name = name,
    members = members.map { bot ->
        GroupMember(id = bot.name, profile = bot.name, handle = bot.handle, displayName = bot.label())
    },
    updatedAtMillis = updatedAt,
    inDesktop = inDesktop,
)
