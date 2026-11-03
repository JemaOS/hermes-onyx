package com.onyx.app.ui.bots

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.onyx.app.data.local.RoomLeadStore
import com.onyx.app.data.model.AttachedFileNote
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.serve.BotProfile
import com.onyx.app.data.model.serve.GroupApproval
import com.onyx.app.data.model.serve.GroupMember
import com.onyx.app.data.model.serve.GroupRoom
import com.onyx.app.data.model.serve.GroupRoomDraft
import com.onyx.app.data.net.ServeClient
import com.onyx.app.data.net.ServeFilesApi
import com.onyx.app.data.net.ServeGroupsRpc
import com.onyx.app.data.net.ServeRpc
import com.onyx.app.data.net.ServerAttachment
import com.onyx.app.data.net.ServerAttachments
import com.onyx.app.data.net.humanFailure
import com.onyx.app.data.room.RoomHandoffLine
import com.onyx.app.data.room.roomAddressed
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.chat.PendingAttachment
import com.onyx.app.ui.conversations.GroupChat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val mainName: String = "",
) : AndroidViewModel(application), GroupVoiceThread {

    private val rpc = ServeGroupsRpc(ServeRpc(config))
    private val attachments = ServerAttachments(ServeFilesApi(ServeClient(config)))

    /**
     * Le choix gardé sur ce téléphone, **derrière la résolution unique**.
     *
     * La salle ne relit plus le magasin pour dessiner : elle lit ce que [BotFaceResolver] rend.
     * Quand l'écran n'a pas encore posé sa table, le repli reste le choix local — mais il est
     * servi par le crochet `local` du résolveur, la même fonction que partout ailleurs, au lieu
     * d'un second chemin de résolution qui pouvait donner un autre visage.
     */
    private val faceStore: BotFaceResolver =
        BotFaceResolver.of(emptyMap(), BotFaceStore(application)::faceOf)

    /** Le nom que la salle donne au visage d'un profil : ce que la résolution unique rend. */
    private fun BotFaceResolver.faceOf(profile: String): String? = codeOf(profile)

    /**
     * **Le meneur de cette salle.** Il vit d'abord ici (`RoomLeadStore`), comme l'onglet ouvert
     * d'une conversation — c'est ce qui le garde d'une session à l'autre, même hors ligne. Quand le
     * serveur connaît `groups.lead`, [setLead] l'écrit **aussi dans la salle** : le meneur devient
     * alors un fait du salon, que les bots lisent et que la salle interroge en premier.
     */
    private val leadStore = RoomLeadStore(application)

    /**
     * Le destinataire par défaut de la salle : le membre à qui elle écrit quand la personne ne
     * nomme personne. `null` — « No lead » — et la salle se comporte exactement comme avant.
     */
    private val _lead = MutableStateFlow(leadStore.lead(initialRoom.id))
    val lead: StateFlow<String?> = _lead.asStateFlow()

    /**
     * Jusqu'où le journal a été lu pour le meneur : un passage ne change le destinataire **qu'une
     * fois**. Posé au dernier événement que la liste connaissait à l'ouverture (le journal d'hier
     * ne défait donc pas le choix d'aujourd'hui) ; `null` quand la liste n'en savait rien — le
     * premier journal lu sert alors de point de départ.
     */
    private var handoffThrough: Int? = initialRoom.latestSeq

    /**
     * La table de visages **de l'écran** (`BotFaceResolver`), posée par la salle : elle réunit le
     * choix du téléphone et `ui_meta.onyx.face`. Sans elle, la scène vocale ne lisait que le choix
     * local — un membre dont le visage vient du serveur y apparaissait sans code, donc avec la
     * créature de repli de `BotLook`, déduite de son nom : deux visages pour un même bot.
     */
    private val faces = MutableStateFlow<((String) -> String?)?>(null)

    /** La salle pose sa résolution de visages ; le fil et la scène vocale la suivent. */
    fun useFaces(resolve: (String) -> String?) {
        faces.value = resolve
    }

    val face: (String) -> String? = { name -> faces.value?.invoke(name) ?: faceStore.faceOf(name) }

    val feed = GroupRoomFeed(
        initialRoom = initialRoom,
        api = rpc,
        naming = { room -> room.naming(roster = null, mainName = mainName) },
    )
    val state = feed.state

    init {
        // **Un passage de main se lit dans le journal**, à chaque réponse lue : le premier journal
        // venu est l'histoire de la salle (il ne défait pas le choix d'aujourd'hui), et les
        // passages **suivants** changent le destinataire par défaut de la suite — une seule fois
        // chacun.
        viewModelScope.launch { state.collect { current -> applyHandoffs(current) } }
    }

    // — Ce qu'une scène vocale lit dans cette salle (`GroupVoiceThread`) —

    override val server: ServerConfig get() = config

    override val roomName: String get() = state.value.room.name

    /** Le round, tel que la scène vocale le lit : le journal, ce qui part, ce qui s'écrit. */
    private val _lines = combine(state, faces) { room, _ -> room.voiceLines(face) }
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
     *
     * **Le destinataire est un paramètre, pas un protocole.** La salle a un meneur ? Le message
     * part **à lui par défaut** (`roomAddressed`) : c'est la même mention que la personne aurait
     * tapée. La mention `@X` du composeur prime toujours, et sans meneur rien ne change.
     * [to] nomme un destinataire pour cet envoi seulement ; absent, c'est le meneur de la salle.
     */
    fun send(
        question: String,
        pendingAttachments: List<PendingAttachment> = emptyList(),
        to: String? = null,
    ) {
        val clean = question.trim()
        if (clean.isEmpty() && pendingAttachments.isEmpty()) return
        // Le destinataire est choisi **ici**, une fois : le meneur de la salle, ou celui qu'on nomme.
        val recipient = to ?: _lead.value
        val id = feed.beginSend(clean, pendingAttachments.map { it.name })
        viewModelScope.launch {
            try {
                var said = roomAddressed(clean, recipient, state.value.room.members)
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

    /**
     * **Choisit le meneur de la salle** — ou l'enlève (`null`, « No lead »).
     *
     * Deux endroits, dans cet ordre : le téléphone (`RoomLeadStore`, toujours), puis la salle
     * elle-même quand ce serveur connaît `groups.lead`. Écrire au salon est ce qui rend le choix
     * **visible aux bots** : ils lisent le `room.lead_changed` du journal, et la salle interroge le
     * meneur en premier quand personne n'est nommé. Un serveur sans cette route laisse simplement
     * le réglage ici — et [leadIsRooms] le dit à l'écran.
     */
    fun setLead(profile: String?, memberId: String? = profile) {
        val clean = profile?.takeIf { it.isNotBlank() }
        _lead.value = clean
        leadStore.set(state.value.room.id, clean)
        // Le salon d'abord quand il sait le recevoir : c'est ce qui rend le meneur lisible aux bots
        // (un `room.lead_changed` dans le journal) et fait interroger ce membre en premier. Un
        // serveur sans la route rend `false` — le réglage local reste, exactement comme avant.
        viewModelScope.launch { feed.nameLead(memberId?.takeIf { it.isNotBlank() }) }
    }

    /**
     * Ce qu'un journal **vient de dire** du meneur : le dernier passage plus récent que
     * [handoffThrough] l'emporte, une seule fois, et se garde pour la suite.
     *
     * Le premier journal venu ne compte pas : c'est l'histoire de la salle, et elle ne doit pas
     * défaire le choix que la personne a fait aujourd'hui.
     */
    private fun applyHandoffs(current: GroupRoomFeedState) {
        val through = handoffThrough
        if (through == null) {
            if (current.loaded) handoffThrough = current.handoffs.lastOrNull()?.seq ?: 0
            return
        }
        val read = roomLeadRead(_lead.value, current.handoffs, through)
        handoffThrough = read.through
        if (read.lead != _lead.value) {
            _lead.value = read.lead
            leadStore.set(current.room.id, read.lead)
        }
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
                onDone(humanFailure(error))
            }
        }
    }

    /**
     * Le crayon de la salle : une **conversation neuve de la même salle**.
     *
     * Une salle d'Hermes **est** une conversation : sa neuve se demande donc au serveur
     * (`groups.create`), avec les mêmes membres et le même nom, sous un identifiant neuf. Le
     * journal repart à vide, et la salle d'hier reste dans la liste avec son fil — rien n'est
     * effacé, rien n'est réécrit.
     *
     * `onOpened` reçoit la salle telle que le serveur l'a faite : l'appelant l'ouvre **dans le
     * même écran** (`GroupRoomScreen`), donc avec la même plaque, les mêmes onglets et les mêmes
     * visages. Un refus se lit dans la salle et l'écran ne bouge pas.
     */
    fun startNewConversation(onOpened: (GroupRoom) -> Unit) {
        val room = state.value.room
        viewModelScope.launch {
            try {
                // Les membres passent **tels quels** : mêmes profils, mêmes poignées, mêmes noms
                // de salle — la conversation neuve est celle de cette salle, pas une autre.
                val draft = GroupRoomDraft(
                    id = GroupRoomDraft.newID(),
                    name = room.name,
                    members = room.members,
                    createdAtMillis = System.currentTimeMillis(),
                )
                val made = rpc.create(draft.id, draft.name, draft.members)
                    ?: throw IOException(OnyxLocalization["The server made no group."])
                onOpened(made)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                feed.report(error)
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

/**
 * **Ce qu'un journal fait au meneur** : le dernier passage plus récent que [through] l'emporte, et
 * [through] avance jusqu'à lui.
 *
 * C'est ce qui fait qu'un passage n'agit **qu'une fois** : relire le journal (chaque seconde, et à
 * chaque reconnexion) ne change pas deux fois le destinataire, et ne réécrit pas le réglage.
 */
internal data class RoomLeadRead(val lead: String?, val through: Int)

internal fun roomLeadRead(lead: String?, lines: List<RoomHandoffLine>, through: Int): RoomLeadRead {
    val fresh = lines.lastOrNull { it.seq > through } ?: return RoomLeadRead(lead, through)
    return RoomLeadRead(fresh.to, fresh.seq)
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
