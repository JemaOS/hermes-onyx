package com.onyx.app.ui.bots

import com.onyx.app.data.model.AttachedFileNote
import com.onyx.app.data.model.serve.GroupApproval
import com.onyx.app.data.model.serve.GroupDraft
import com.onyx.app.data.model.serve.GroupEvent
import com.onyx.app.data.model.serve.GroupRoom
import com.onyx.app.data.model.serve.GroupState
import com.onyx.app.data.net.GroupRoomApi
import com.onyx.app.data.net.TransportError
import com.onyx.app.data.net.humanFailure
import com.onyx.app.data.net.strOrNull
import com.onyx.app.data.room.RoomHandoffLine
import com.onyx.app.data.room.roomHandoffs
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.voice.VoiceDiagnostics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Un message de la personne en route vers la salle : ce qui a été tapé, et les fichiers qui
 * l'accompagnent, par leur nom.
 */
data class GroupSending(
    /** Son identifiant d'événement : c'est aussi l'`event_id` du serveur, donc sa clé d'unicité. */
    val id: String,
    val text: String,
    val files: List<String> = emptyList(),
    /** Ce qui part dans la salle : le texte, et une note par fichier une fois sur le serveur. */
    val said: String? = null,
    /** Combien d'essais ont échoué : le message est **encore** en route, il n'est pas perdu. */
    val attempts: Int = 0,
    /** L'envoi a définitivement échoué : le message reste à l'écran, marqué, avec sa raison. */
    val failed: Boolean = false,
    val problem: String? = null,
) {
    /** Ce qui est vraiment dit : le texte envoyé, sinon celui qui a été tapé. */
    val delivered: String get() = said ?: text
}

/** Une salle suivie à l'écran : ce qui y a été dit, ce qu'elle attend, et ce qui s'écrit. */
data class GroupRoomFeedState(
    val room: GroupRoom,
    val events: List<GroupEvent> = emptyList(),
    val status: GroupState = GroupState(),
    /**
     * Le meneur que la **salle** annonce (`groups.state` → `context.lead_member_id`, routage
     * `lead`). `null` quand la salle n'en a pas — ou quand ce serveur ne connaît pas le routage.
     */
    val leadMemberId: String? = null,
    /** Les messages envoyés, pas encore dans le journal. */
    val sending: List<GroupSending> = emptyList(),
    /** Les réponses en train d'être écrites, chacune jusqu'à ce que la salle l'ait. */
    val drafts: List<GroupDraft> = emptyList(),
    /** Les approbations et les relances répondus ici et pas encore partis de l'état : leur carte se cache. */
    val answering: Set<String> = emptySet(),
    /** Ce que le serveur a refusé, dit tel quel : jamais un message qui disparaît en silence. */
    val problem: String? = null,
    /** Le dernier événement lu du journal. */
    val cursor: Int = 0,
    /** Vrai une fois le premier journal lu. */
    val loaded: Boolean = false,
) {
    /**
     * Les bots parlent : un message de la personne n'a pas encore de fin de discussion après lui.
     * (La copie d'une salle Desktop ne dit rien de cela.)
     */
    val discussing: Boolean
        get() {
            if (room.inDesktop) return false
            val lastAsk = events.lastOrNull { it.kind == "message.user" }?.seq ?: return sending.isNotEmpty()
            return sending.isNotEmpty() || events.none { it.seq > lastAsk && it.endsDiscussion }
        }

    /** Quelque chose tourne : une discussion est en cours, ou la salle travaille. */
    val busy: Boolean get() = discussing || status.working

    /** Le dernier événement vu ici, comme `GroupRoom.activityMark` le compte. */
    val seenMark: Double?
        get() = if (room.inDesktop) room.updatedAtMillis?.let { it / 1000.0 } else cursor.takeIf { it > 0 }?.toDouble()

    /** La réponse en train d'être écrite à cet instant, s'il y en a une. */
    val writing: GroupDraft? get() = drafts.lastOrNull { it.text.isNotEmpty() && it.finishedAtMillis == null }

    /**
     * Les brouillons qui se dessinent : ceux qui ont commencé à écrire, filtrés comme la vue de
     * référence (`feed.drafts.filter { !$0.text.isEmpty() }`) — une session ouverte dont rien
     * n'est encore venu n'ajoute pas de ligne.
     */
    val shownDrafts: List<GroupDraft> get() = drafts.filter { it.text.isNotEmpty() }

    /**
     * **Les passages de main du journal** — un par changement de destinataire, à la place de la
     * réponse qui le porte (`roomHandoffs`). Lus du journal, ils ne sont jamais inventés : deux
     * lectures du même journal rendent les mêmes lignes, et aucune ne peut donc s'afficher deux fois.
     *
     * Le dernier dit à qui la salle écrit par défaut pour la suite.
     */
    val handoffs: List<RoomHandoffLine> get() = roomHandoffs(events, room.members)

    /**
     * Les lignes du journal qui se dessinent : les messages, les notes que vaut un tour, et le
     * passage de main **juste après** la réponse qui le porte — même liste, donc même ordre : une
     * ligne de passage ne réordonne jamais les rangées existantes.
     */
    val rows: List<GroupEvent>
        get() {
            val kept = events.filter { it.isMessage || it.note(room) != null }
            val handoffs = handoffs
            if (handoffs.isEmpty()) return kept
            val after = handoffs.associateBy { it.seq }
            return kept.flatMap { event ->
                val handoff = after[event.seq]
                if (handoff == null) listOf(event) else listOf(event, handoff.asEvent())
            }
        }
}

/**
 * Le fil tel que la scène vocale le lit (`GroupVoiceThread.lines`) : la question de la personne —
 * celle qui part, et celles du journal —, les réponses des bots, celles qui s'écrivent
 * (`drafts`) et celles en route (`sending`), dans l'ordre où le fil les dessine.
 *
 * C'est la matière du `VoiceView.groupExchange` de la référence : les événements du journal deviennent les
 * lignes d'une réponse (`message.member`) ou d'une question (`message.user`), un brouillon une
 * ligne vivante sous son bot, et un message en route la bulle de la personne qui attend encore.
 */
internal fun GroupRoomFeedState.voiceLines(face: (String) -> String? = { null }): List<GroupVoiceLine> {
    val said = events.filter { it.isMessage }.map { event ->
        val member = room.member(event.actorID)
        val text = AttachedFileNote.split(event.text)
        GroupVoiceLine(
            id = "voice-seq-" + event.seq,
            // Le profil du bot (`room.member(id)?.profile`), pas l'identifiant du membre : c'est
            // lui qui choisit sa voix (`BotVoices`) et son visage dans l'orbe. L'identifiant, lui,
            // est gardé à part — le fil le dessine et le protocole le nomme.
            profile = if (event.kind == "message.user") null else member?.profile ?: event.actorID,
            memberID = if (event.kind == "message.user") null else event.actorID,
            name = member?.label ?: OnyxLocalization["A bot"],
            faceCode = face(member?.profile.orEmpty()),
            text = text.text,
            paths = if (event.kind == "message.user") text.paths else emptyList(),
            atMillis = event.createdAtMillis,
            seq = event.seq,
            after = event.seq - 1,
        )
    }
    val pending = sending.map { message ->
        val text = AttachedFileNote.split(message.text)
        GroupVoiceLine(
            id = "voice-sending-" + message.id,
            profile = null,
            name = "",
            text = text.text,
            paths = text.paths,
            files = message.files,
            // Ce qui n'est pas arrivé dans la salle se **voit** : la bulle reste, marquée de son
            // échec s'il est définitif. Un message qui disparaît en silence est un message perdu.
            waiting = !message.failed,
            failed = message.failed,
        )
    }
    val writing = shownDrafts.map { draft ->
        val member = room.member(draft.memberID)
        GroupVoiceLine(
            id = "voice-draft-" + draft.id,
            profile = member?.profile ?: draft.memberID,
            memberID = draft.memberID,
            name = member?.label ?: OnyxLocalization["A bot"],
            faceCode = face(member?.profile.orEmpty()),
            text = draft.text,
            live = draft.finishedAtMillis == null,
            atMillis = draft.beganMillis,
            draftId = draft.id,
            after = draft.after,
        )
    }
    return said + pending + writing
}

/**
 * Le fil d'une salle de bots : ce qui y a été dit, lu de son journal, et ce que la personne y dit.
 *
 * Miroir de `GroupFeed` (Echo/Services/Transport/ServerGroups:647) : le journal est lu après
 * un curseur (`groups.log`), l'état de la salle à chaque tour (`groups.state`), et les réponses des
 * bots là où Hermes les écrit d'abord — dans la session cachée « Group: <salle> » de chaque bot
 * (`session.events.since`). Un message envoyé reste à l'écran tant que la salle ne l'a pas, et un
 * refus du serveur se lit au lieu de faire disparaître ce qui a été écrit.
 *
 * Le fil d'une salle ne touche jamais au fil local de l'application (`GroupChatViewModel`) : ce qui
 * est écrit ici part dans la salle, et ce qui s'affiche vient de la salle.
 */
class GroupRoomFeed(
    initialRoom: GroupRoom,
    private val api: GroupRoomApi,
    /** Le temps, injectable pour éprouver ce que devient une réponse finie. */
    private val clock: () -> Long = System::currentTimeMillis,
    /** Les membres d'une salle Desktop appelés comme le roster les appelle (`GroupRoom.naming`). */
    private val naming: (GroupRoom) -> GroupRoom = { it },
    /** Les poses entre deux essais d'envoi — raccourcies dans une preuve. */
    private val sendBackoffMs: List<Long> = GroupSendOutbox.SEND_BACKOFF_MS,
    /** La pose elle-même, remplaçable pour ne pas attendre dans une preuve. */
    private val waitSend: suspend (Long) -> Unit = { delay(it) },
) {
    private val _state = MutableStateFlow(GroupRoomFeedState(room = initialRoom))
    val state: StateFlow<GroupRoomFeedState> = _state.asStateFlow()

    /**
     * La file d'envoi de la salle : ce qui n'est **pas encore** arrivé au serveur. Le même
     * identifiant n'y entre qu'une fois (`event_id`), et rien n'en sort sans que le serveur l'ait
     * pris ou sans qu'on l'ait dit à la personne.
     */
    private val outbox = GroupSendOutbox(backoffMs = sendBackoffMs)

    /** Les envois partent **dans l'ordre** : une reprise ne double jamais le message suivant. */
    private val sendLock = Mutex()

    private val _pendingSends = MutableStateFlow(false)

    /** Un message de la personne est encore en route : le micro d'une scène vocale reste fermé. */
    val pendingSends: StateFlow<Boolean> = _pendingSends.asStateFlow()

    private val _problem = MutableStateFlow<String?>(null)

    /** Ce que la salle a refusé, tel qu'il se lit (déjà dans la langue de l'app). */
    val problem: StateFlow<String?> = _problem.asStateFlow()

    /** Chaque session vivante et son dernier événement lu. */
    private val draftSeen = mutableMapOf<String, Int>()

    /** Les sessions des bots de cette salle, par identifiant gardé : à qui est une session vivante. */
    private val memberSessions = mutableMapOf<String, String>()
    private var lookedForMembers: Long? = null

    /** Le numéro de chaque message d'une salle Desktop ici, par sa clé : le même message garde sa ligne. */
    private val desktopSeqs = mutableMapOf<String, Int>()

    /** Suit la salle jusqu'à l'annulation de la tâche (l'écran s'en va). */
    suspend fun follow() {
        var tick = 0
        while (currentCoroutineContext().isActive) {
            val busy = state.value.busy
            // Pendant que les bots parlent : le journal une fois par seconde, les réponses deux fois.
            if (!busy || tick % 2 == 0) look()
            lookAtDrafts()
            tick += 1
            delay(if (busy) BUSY_TICK else IDLE_TICK)
        }
    }

    /** Lit le journal de la salle et son état (`groups.log`, `groups.state`). */
    suspend fun look() {
        if (state.value.room.inDesktop) return lookInDesktop()
        try {
            // **Le journal et l'état sont demandés ensemble.** Ce sont deux lectures
            // indépendantes du même canal ; en série, le tick payait deux allers-retours
            // (le tunnel en ajoute ~60 ms) alors qu'en parallèle il en paie un.
            val room = state.value.room.id
            val cursor = state.value.cursor
            val (fresh, roomStatus) = coroutineScope {
                val freshCall = async { api.log(room, cursor) }
                val stateCall = async { api.roomState(room) }
                freshCall.await() to stateCall.await()
            }
            if (fresh.isNotEmpty()) {
                _state.update { current ->
                    val renamed = fresh.lastOrNull { it.kind == "room.renamed" }?.payload?.strOrNull("name")
                    current.copy(
                        // **Le journal affiché est borné.** La salle relit son journal deux fois
                        // par seconde quand elle travaille, et `events + fresh` recopiait tout :
                        // une longue session de bots faisait O(n) allocations par tick, donc O(n²)
                        // sur la durée. Cinq cents lignes couvrent largement ce que l'écran
                        // dessine (les passages de main et le meneur ne lisent que les dernières).
                        events = (current.events + fresh).takeLast(MAX_EVENTS),
                        cursor = fresh.last().seq,
                        // Un message de la personne maintenant dans le journal n'attend plus (Hermes
                        // rogne ce qu'on lui donne : c'est le texte dit qui les font se rejoindre).
                        sending = current.sending.filterNot { pending ->
                            fresh.any { it.kind == "message.user" && it.text.trim() == pending.delivered.trim() }
                        },
                        room = renamed?.let { name -> current.room.copy(name = name) } ?: current.room,
                    )
                }
            }
            val (current, status) = roomStatus
            _state.update { state ->
                val members = current?.members?.takeIf { it.isNotEmpty() && it != state.room.members }
                state.copy(
                    room = if (members != null) state.room.copy(members = members) else state.room,
                    status = status,
                    leadMemberId = roomStatus.leadMemberId,
                    problem = null,
                    loaded = true,
                )
            }
        } catch (error: Exception) {
            fail(error)
        }
    }

    /** Les réponses des bots telles qu'elles s'écrivent (`GroupDraft`), tant que la salle travaille. */
    suspend fun lookAtDrafts() {
        if (state.value.room.inDesktop) return
        val current = state.value
        val busy = current.busy
        // Une réponse part une fois que la salle l'a ; une finie que la salle n'a jamais écrite (un bot
        // qui passe), après un moment ; une en cours d'écriture, quand la salle s'arrête.
        val kept = current.drafts.filter { draft ->
            val landed = current.events.any { event ->
                event.kind == "message.member" && event.seq > draft.after &&
                    (draft.memberID == null || event.actorID == draft.memberID)
            }
            when {
                landed -> false
                draft.finishedAtMillis != null -> clock() - draft.finishedAtMillis!! < KEEP_FINISHED
                else -> busy
            }
        }
        if (kept != current.drafts) _state.update { it.copy(drafts = kept) }
        if (!busy) return
        val live = try {
            api.liveSessions(current.room.id)
        } catch (_: Exception) {
            return
        }
        var next = _state.value.drafts
        // Le rattachement des sessions aux membres se résout **une fois**, avant les lectures :
        // chercher la session de chaque membre à chaque bot, à chaque tick, était un voyage de
        // plus par membre.
        if (live.any { memberSessions[it.key] == null }) findMemberSessions()
        // **Les sessions vivantes se lisent en parallèle.** Une à une, chaque bot rajoutait
        // un aller-retour complet (le tunnel en ajoute ~60 ms) avant que le suivant ne parte :
        // avec quatre bots, la réponse du quatrième attendait trois voyages. En parallèle,
        // tous les brouillons arrivent ensemble.
        val slices = coroutineScope {
            live.map { session ->
                async {
                    val index = next.indexOfFirst { it.id == session.id }
                    // Une session au travail, ou une dont la réponse est encore suivie.
                    val following = index >= 0 && next[index].finishedAtMillis == null
                    if (session.status !in BUSY_STATUSES && !following) return@async null
                    val since = draftSeen[session.id] ?: 0
                    val slice = try {
                        api.sessionEvents(session.id, since)
                    } catch (_: Exception) {
                        null
                    }
                    session to slice
                }
            }.mapNotNull { it.await() }
        }
        for ((session, slice) in slices) {
            if (slice == null) continue
            val index = next.indexOfFirst { it.id == session.id }
            val first = draftSeen[session.id] == null
            draftSeen[session.id] = slice.latest
            var draft = if (index >= 0) next[index] else GroupDraft(id = session.id, after = _state.value.cursor)
            draft = draft.copy(memberID = memberSessions[session.key])
            val read = draft.read(slice.events)
            draft = read.draft
            // Lu d'abord après que son début a été lâché (Hermes garde les 512 derniers) : le reste.
            if (!read.started && first && slice.truncated && draft.text.isNotEmpty()) {
                draft = draft.copy(text = "…" + draft.text)
            }
            if (index >= 0) {
                next = next.toMutableList().also { it[index] = draft }
            } else if (draft.finishedAtMillis == null || draft.text.isNotEmpty()) {
                next = next + draft
            }
        }
        if (next != _state.value.drafts) _state.update { it.copy(drafts = next) }
    }

    /** À qui est chacune des sessions vivantes de la salle, par le titre de chaque bot chez Hermes. */
    private suspend fun findMemberSessions() {
        val last = lookedForMembers
        if (last != null && clock() - last < MEMBER_LOOK) return
        lookedForMembers = clock()
        val room = state.value.room.id
        val members = state.value.room.members
        // **Les membres se cherchent en parallèle.** Un par un, chaque membre rajoutait un
        // aller-retour avant que le suivant ne parte ; en parallèle, une seule attente.
        val found = coroutineScope {
            members.map { member ->
                async {
                    val key = try {
                        api.memberSession(room, member.profile)
                    } catch (_: Exception) {
                        null
                    }
                    member to key
                }
            }.mapNotNull { it.await() }
        }
        for ((member, key) in found) {
            if (key != null) memberSessions[key] = member.id
        }
    }

    /** Une salle qu'Hermes Desktop tient : sa copie est relue (Desktop l'écrit un instant après). */
    private suspend fun lookInDesktop() {
        try {
            val current = api.desktopRooms().firstOrNull { it.id == state.value.room.id }
            if (current == null) {
                _state.update {
                    it.copy(problem = OnyxLocalization["This group chat was ended in Hermes Desktop."])
                }
                return
            }
            val named = naming(current)
            val fresh = desktopEvents(named, desktopSeqs)
            _state.update { state ->
                state.copy(room = named, events = fresh, loaded = true, problem = null)
            }
        } catch (error: Exception) {
            fail(error)
        }
    }

    /**
     * Ajoute un message de la personne, en route vers la salle : il s'affiche tout de suite, et
     * reste là tant que le journal ne l'a pas. Rend son identifiant d'événement.
     *
     * L'identifiant peut être **imposé** : une question reconnue par la voix arrive avec le sien
     * (`GroupAskLedger`), et c'est lui l'`event_id` — une question rejouée par l'écran ne peut donc
     * pas entrer deux fois dans la salle.
     */
    fun beginSend(
        text: String,
        files: List<String> = emptyList(),
        id: String = java.util.UUID.randomUUID().toString(),
    ): String {
        if (_state.value.sending.any { it.id == id }) return id
        _state.update { it.copy(sending = it.sending + GroupSending(id = id, text = text, files = files)) }
        outbox.offer(id, text, clock())
        _pendingSends.value = outbox.isNotEmpty
        return id
    }

    /**
     * **Nomme le meneur de la salle côté serveur** (`groups.lead`), ou le retire (`memberId = null`).
     *
     * Rend `true` quand le serveur a accepté — il écrit alors un `room.lead_changed` dans le journal,
     * et la salle interroge ce membre en premier quand personne n'est nommé. Rend `false` quand le
     * serveur ne connaît pas la route : le réglage reste sur le téléphone, et le journal n'a rien à
     * relire.
     */
    suspend fun nameLead(memberId: String?): Boolean {
        // Trois âges de serveur, dans l'ordre du plus récent au plus ancien :
        //   1. `groups.context` + `routing: "lead"` — la forme de l'amont (le meneur distribue) ;
        //   2. `groups.lead` — la route du lot 5 (le meneur répond en premier) ;
        //   3. aucune des deux — le réglage reste sur ce téléphone.
        val named = api.leadRouting(state.value.room.id, memberId) ||
            api.nameLead(state.value.room.id, memberId)
        if (named) look()
        return named
    }

    /**
     * **Enregistre un passage de main dans la salle** (`groups.handoff`) : le meneur qui part a
     * nommé son successeur, la salle le note, et les autres appareils le voient.
     *
     * Rend `false` quand ce serveur ne connaît pas la route — la ligne de journal locale reste
     * alors la seule trace, exactement comme avant.
     */
    suspend fun handOver(from: String, to: String): Boolean {
        if (from == to) return false
        val done = api.handoff(state.value.room.id, from, to)
        if (done) look()
        return done
    }

    /**
     * Dit le message dans la salle (`groups.send`) et relit le journal.
     *
     * **Un message ne se perd pas.** Un envoi qui échoue pour une raison passagère (réseau, 5xx,
     * délai dépassé, erreur interne du serveur) **reste en file** et repart avec des poses
     * croissantes — toujours sous le **même** `event_id`, si bien qu'un serveur qui avait déjà pris
     * le message ne le garde qu'une fois. Épuisé, l'échec se **dit** : la bulle reste à l'écran,
     * marquée, et le problème s'affiche dans la langue de l'app.
     *
     * Refusé pour de bon par le serveur (une question qui n'attend plus), le message quitte
     * l'écran et le refus se lit — jamais un message qui disparaît en silence.
     */
    suspend fun deliver(id: String, said: String) {
        _state.update { state ->
            state.copy(sending = state.sending.map { if (it.id == id) it.copy(said = said) else it })
        }
        sendLock.withLock {
            var attempt = 0
            while (true) {
                try {
                    api.send(state.value.room.id, said, id)
                    outbox.sent(id)
                    _pendingSends.value = outbox.isNotEmpty
                    look()
                    return
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    if (!sendIsWorthRetrying(error) || attempt >= sendBackoffMs.size) {
                        failSend(id, error)
                        return
                    }
                    attempt += 1
                    val wait = sendBackoffMs[(attempt - 1).coerceAtMost(sendBackoffMs.lastIndex)]
                    VoiceDiagnostics.note(
                        "salle (${state.value.room.name}): envoi en échec ($attempt/${sendBackoffMs.size}), " +
                            "reprise dans ${wait} ms — $id",
                    )
                    // Le message reste dans la file **et** à l'écran : il n'a pas disparu.
                    _state.update { current ->
                        current.copy(
                            sending = current.sending.map { pending ->
                                if (pending.id == id) pending.copy(attempts = attempt) else pending
                            },
                        )
                    }
                    waitSend(wait)
                }
            }
        }
    }

    /**
     * Un message qui n'a pas pu partir (pièce jointe refusée, réseau mort, refus du serveur).
     *
     * Un refus **définitif** garde le message à l'écran, marqué : il ne s'évapore pas. Un refus de
     * « question qui n'attend plus » (5118/5119) le retire, comme le fil du chat, parce que la
     * salle l'a déjà traité ailleurs.
     */
    fun failSend(id: String, error: Exception) {
        val reason = reason(error)
        val stale = (error as? TransportError.Rpc)?.code in GroupSendOutbox.STALE_CODES
        outbox.giveUp(id)
        _pendingSends.value = outbox.isNotEmpty
        val message = if (stale) {
            OnyxLocalization.format("Couldn't send: %1\$s", reason)
        } else {
            OnyxLocalization.format("Your message did not reach the room: %1\$s", reason)
        }
        _problem.value = message
        _state.update { state ->
            state.copy(
                sending = if (stale) {
                    state.sending.filterNot { it.id == id }
                } else {
                    state.sending.map { pending ->
                        if (pending.id == id) pending.copy(failed = true, problem = reason) else pending
                    }
                },
                problem = message,
            )
        }
        VoiceDiagnostics.note("salle (${state.value.room.name}): envoi perdu — $reason")
    }

    /** Ce que le serveur a refusé, à l'écran, tel quel (un renommage, une suppression de salle). */
    fun report(error: Exception) = fail(error)

    /**
     * Une question reconnue entre dans la salle, **une seule fois**.
     *
     * C'est le chemin de la voix : `askId` est l'identifiant que lui a donné `GroupAskLedger`, et
     * il sert d'`event_id`. Une question rejouée (l'écran se recompose, la phase repasse, le moteur
     * rend sa transcription une fois de plus) porte le même identifiant et n'entre donc pas deux
     * fois : la file d'envoi la refuse, en le journalisant.
     */
    suspend fun ask(question: String, askId: String) {
        val clean = question.trim()
        if (clean.isEmpty()) return
        if (outbox.knows(askId) || _state.value.sending.any { it.id == askId }) {
            VoiceDiagnostics.note("salle (${state.value.room.name}): question déjà posée — elle ne repart pas ($askId)")
            return
        }
        beginSend(clean, emptyList(), askId)
        deliver(askId, clean)
    }

    /** Arrête les bots de la salle (`groups.stop`). */
    suspend fun stop() {
        try {
            api.stop(state.value.room.id)
            look()
        } catch (error: Exception) {
            fail(error)
        }
    }

    /** Relance une réponse arrêtée à mi-chemin (`groups.retry`). */
    suspend fun retry(task: String) {
        settle(task) { api.retry(state.value.room.id, task) }
    }

    /** Répond à la commande d'un bot (« once » ou « deny »). */
    suspend fun answer(approval: GroupApproval, choice: String) {
        settle(approval.id) { api.approve(state.value.room.id, approval, choice) }
    }

    /**
     * Répond à une des questions de la salle, sa carte cachée entre-temps. Refusée comme n'étant
     * plus en attente (répondue ailleurs — 5119 —, ou expirée), la salle est relue et la carte s'en va.
     */
    private suspend fun settle(id: String, call: suspend () -> Unit) {
        if (state.value.answering.contains(id)) return
        _state.update { it.copy(answering = it.answering + id) }
        try {
            call()
            look()
        } catch (error: TransportError) {
            if (error is TransportError.Rpc && error.code in STALE_CODES) look() else fail(error)
        } catch (error: Exception) {
            fail(error)
        }
        _state.update { it.copy(answering = it.answering - id) }
    }

    /** Renomme la salle sur le serveur (`groups.rename`). */
    suspend fun rename(to: String) {
        api.rename(state.value.room.id, to)
        _state.update { it.copy(room = it.room.copy(name = to)) }
        look()
    }

    /** Le bot dont c'est la tâche, par le journal de la salle (la fin d'un tour le nomme). */
    fun memberForTask(task: String): String? =
        state.value.events.lastOrNull { it.payload["task_id"] != null && it.actorID != null }
            ?.let { state.value.room.member(it.actorID)?.label }

    private fun fail(error: Exception) {
        _state.update { it.copy(problem = reason(error), loaded = true) }
    }

    private fun reason(error: Exception): String =
        humanFailure(error, OnyxLocalization["unknown error"])

    companion object {
        const val BUSY_TICK = 500L
        const val IDLE_TICK = 5_000L
        const val KEEP_FINISHED = 20_000L

        /** Le nombre de lignes de journal gardées en mémoire pour l'affichage. */
        const val MAX_EVENTS = 500
        const val MEMBER_LOOK = 5_000L

        /** Une session qui écrit, ou qui vient de commencer. */
        val BUSY_STATUSES = setOf("working", "starting")

        /** Les codes d'un refus qui n'est pas une panne : la question n'attend plus de réponse. */
        val STALE_CODES = setOf(5118, 5119)

        /** Le journal d'une salle Desktop, comme les entrées que l'écran dessine. */
        fun desktopEvents(room: GroupRoom, seqs: MutableMap<String, Int>): List<GroupEvent> =
            room.desktopMessages.map { message ->
                val seq = seqs[message.key] ?: ((seqs.values.maxOrNull() ?: 0) + 1)
                seqs[message.key] = seq
                GroupEvent(
                    seq = seq,
                    kind = if (message.fromPerson) "message.user" else "message.member",
                    actorKind = if (message.fromPerson) "user" else "member",
                    actorID = message.profile,
                    payload = buildJsonObject { put("text", message.text) },
                    createdAtMillis = message.atMillis,
                )
            }
    }
}
