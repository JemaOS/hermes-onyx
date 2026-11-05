package com.onyx.app.ui.chat

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.onyx.app.data.local.BotRosterCache
import com.onyx.app.data.model.ChatMessage
import com.onyx.app.data.model.Attachment
import com.onyx.app.data.model.ChatRequest
import com.onyx.app.data.model.ChatTurn
import com.onyx.app.data.model.Conversation
import com.onyx.app.data.model.Role
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.SubagentActivity
import com.onyx.app.data.model.ToolActivity
import com.onyx.app.data.model.OutboxItem
import com.onyx.app.data.model.OutboxState
import com.onyx.app.data.model.ReplyCards
import com.onyx.app.data.model.ToolStatus
import com.onyx.app.data.model.serve.BotProfile
import com.onyx.app.data.local.DraftStore
import com.onyx.app.data.model.Transport
import com.onyx.app.data.model.TurnMetrics
import com.onyx.app.data.net.ChatCompletionsClient
import com.onyx.app.data.net.GatewayApprovalClient
import com.onyx.app.data.net.HermesServeChat
import com.onyx.app.data.net.ServeRpc
import com.onyx.app.data.net.ServeClient
import com.onyx.app.data.net.ServeSessionsApi
import com.onyx.app.data.net.ServeSkillsApi
import com.onyx.app.data.net.ServerSessions
import com.onyx.app.data.net.TransportError
import com.onyx.app.data.net.TurnEvent
import com.onyx.app.data.net.asChatChannel
import com.onyx.app.data.net.asObject
import com.onyx.app.data.net.array
import com.onyx.app.data.net.humanFailure
import com.onyx.app.data.net.firstString
import com.onyx.app.data.repo.ConversationRepository
import com.onyx.app.data.repo.ServerRepository
import com.onyx.app.data.repo.SettingsRepository
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.shortcuts.BotShortcutInbox
import com.onyx.app.shortcuts.BotShortcutRequest
import com.onyx.app.ui.bots.botChatToOpen
import com.onyx.app.ui.bots.botOfConversation
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Pilote une conversation : envoie le tour au serveur, applique les événements du flux, expose
 * l'état à l'écran et enregistre le fil.
 *
 * Miroir partiel de `Conversation` côté référence : `startTurn` devient [send], `apply(_:to:)`
 * devient [applyEvent], et `ConversationStore` est ici [ConversationRepository].
 */
@OptIn(ExperimentalCoroutinesApi::class)
/** Le calme avant d'interroger le serveur pour le menu « / » (ms). */
private const val SlashCompleteDebounceMs = 120L

/** L'attente la plus longue d'un tour avant qu'une commande « / » ne renonce (10 minutes). */
private const val SlashWaitTimeoutMs = 10 * 60 * 1000L

/**
 * Les deux temps de la reprise d'import après une réinstallation.
 *
 * Premier essai rapide (le serveur répond souvent dès la deuxième frame), puis on double jusqu'à
 * une minute : au pire une lecture par minute tant que ça échoue, et plus aucune dès que c'est fait.
 * Sans ce plafond, un serveur injoignable ferait tourner une boucle serrée.
 */
private const val IMPORT_RETRY_FIRST_MS = 3_000L
private const val IMPORT_RETRY_CEILING_MS = 60_000L

class ChatViewModel(
    application: Application,
    private val servers: ServerRepository,
    private val conversations: ConversationRepository,
    private val settings: SettingsRepository,
    private val drafts: DraftStore,
    /**
     * Les tours en cours du processus. Le tour vit là, **au-dessus de l'écran** : quitter la
     * conversation ou détruire l'activité ne l'arrête pas, et le fil le retrouve au retour.
     */
    private val turns: TurnStore,
) : AndroidViewModel(application) {

    private var conversationId: String = UUID.randomUUID().toString()

    /**
     * Le roster gardé sur ce téléphone, lu par l'import des sessions du serveur pour savoir
     * quelles sessions sont le **chat d'un bot** — et non des conversations à importer.
     */
    private val roster = BotRosterCache(getApplication<Application>())

    private val _state = MutableStateFlow(
        ChatUiState(server = servers.activeServer.value, conversationId = conversationId),
    )
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    /** La liste affichée par l'écran des conversations, la plus récente d'abord. */
    val history: StateFlow<List<Conversation>> = conversations.conversations

    private var lastRequest: ChatRequest? = null
    private val runControls = RunChatControlsStore(application)

    /** Les tours dont l'issue a déjà été arbitrée (une seule fois chacun). */
    private val settledTurns = mutableSetOf<String>()

    /** Identifiant de session côté serveur, quand le transport en tient une. */
    private var serverSessionId: String? = null

    /** Le nom donné à la conversation (« Renommer »), ou `null` tant qu'elle n'a que sa question. */
    private var conversationName: String? = null

    /** Les messages écrits pendant une réponse : ils partent l'un après l'autre à la fin. */
    private val queued = OutboxQueue()
    private var retryJob: Job? = null
    /** Le rattrapage d'un tour fini sans nous (voir [resumeDetachedTurn]) : un seul à la fois. */
    private var recoveryJob: Job? = null
    private var retryAttempt = 0
    private val retryDelaysMs = longArrayOf(2_000, 5_000, 15_000, 30_000, 60_000)
    /** Ancienneté initiale des tours sortis de la file, par bulle de réponse. */
    private val heldSince = mutableMapOf<String, Long>()

    /** Le canal JSON-RPC du Dashboard : c'est lui qui porte les commandes « / ». */
    private var rpc: com.onyx.app.data.net.ServeRpc? = null

    /**
     * L'identifiant **runtime** de la session ouverte sur le Dashboard, celui sous lequel le
     * gateway l'enregistre. `subagent.list` / `.tail` / `.steer` / `.interrupt` le cherchent ;
     * l'identifiant conservé côté app (`serverSessionId`) n'est qu'une clé de reprise.
     */
    private var runtimeSessionId: String? = null
    private var slashJob: kotlinx.coroutines.Job? = null

    /**
     * **La liste que le menu et l'exécution lisent**, et rien d'autre : les six commandes fixes,
     * puis les compétences du profil ([refreshSlashCommands]).
     *
     * Elle démarre sans compétence : avant la première lecture, l'app se comporte exactement comme
     * avant ce lot.
     */
    private var slashCommands: List<SlashCommand> = SlashCommands.commands()

    /** La lecture des compétences en cours (`api/skills`). */
    private var skillsJob: Job? = null

    /** Quand `api/skills` a été lu pour la dernière fois : une relecture par minute au plus. */
    private var skillsLoadedAt = 0L

    /**
     * Vrai une fois la conversation la plus récente rétablie (ou une nouvelle commencée).
     *
     * Sans ce verrou, le fil vide d'une nouvelle conversation ressemblerait au démarrage de
     * l'app, et le premier enregistrement du dépôt rouvrirait celle qu'on vient de quitter.
     */
    private var restoredInitial = false

    init {
        // Un changement de serveur (Réglages) vide le fil : l'historique n'a plus de sens.
        // En revanche une ÉDITION de la configuration (même id) doit être adoptée : sinon
        // « Done » sur l'écran de connexion enregistre bien le serveur mais l'état garde
        // l'ancienne config, l'app se croit toujours sans serveur et réaffiche le formulaire
        // (symptôme : le bouton Done semble ne rien faire).
        viewModelScope.launch {
            servers.activeServer.collect { server ->
                val current = _state.value.server
                if (server.id != current.id) {
                    persistCurrent()
                    // Les tours de l'ancien serveur n'ont plus personne à qui répondre : ils sont
                    // arrêtés (l'hôte est prévenu), pas abandonnés.
                    turns.stopAll()
                    settledTurns.clear()
                    queued.clear()
                    retryJob?.cancel()
                    retryJob = null
                    heldSince.clear()
                    serverSessionId = null
                    conversationId = UUID.randomUUID().toString()
                    conversationName = null
                    restoredInitial = false
                    _state.update {
                        it.copy(
                            server = server,
                            conversationId = conversationId,
                            serverSessionId = null,
                            conversationName = null,
                            messages = emptyList(),
                            queued = emptyList(),
                            attachments = emptyList(),
                            error = null,
                            canRetry = false,
                        )
                    }
                } else if (server != current) {
                    // Une édition de la configuration du **même** serveur est adoptée. Quand c'est le
                    // **modèle** qui a changé (la feuille de modèle écrit `ServerConfig.model` pour une
                    // conversation simple), l'écho du serveur ([ChatUiState.modelInUse]) devient
                    // périmé : on le baisse pour que le nouveau choix prenne à l'affichage **et** à
                    // l'envoi. Sans ça, l'étage « conversation » gardait l'ancien modèle servi et le
                    // choix ne s'appliquait nulle part.
                    val modelChanged = server.model != current.model
                    _state.update { it.copy(server = server, modelInUse = if (modelChanged) null else it.modelInUse) }
                }
                // Les compétences vivent dans le profil : un autre serveur, un autre profil ou
                // d'autres identifiants changent la liste des commandes « / ».
                refreshSlashCommands()
            }
        }
        // La conversation la plus récente pour ce serveur, une seule fois, s'il y en a une.
        viewModelScope.launch {
            conversations.conversations.collect { list ->
                // Ne JAMAIS restaurer pendant un tour en cours : l'enregistrement de la
                // conversation (déclenché par l'envoi) fait émettre cette liste, et la
                // restauration écraserait le message assistant en train de streamer — la
                // réponse du serveur n'aurait plus de bulle où s'écrire (chat muet : le
                // serveur répond en 1,6 s mais rien ne s'affiche).
                if (restoredInitial || _state.value.isStreaming || _state.value.messages.isNotEmpty()) {
                    return@collect
                }
                val latest = list.firstOrNull { it.serverId == _state.value.server.id && !it.isEmpty }
                    ?: return@collect
                restoredInitial = true
                open(latest)
            }
        }
        // Le tour s'écrit dans le magasin, pas dans cet écran : on s'y rattache. Un tour lancé
        // avant la recréation de l'écran (retour dans l'app) se retrouve donc entier, en cours.
        viewModelScope.launch {
            _state.map { it.conversationId }.distinctUntilChanged()
                .flatMapLatest { id -> if (id == null) flowOf(null) else turns.live(id) }
                .collect { live -> follow(live) }
        }
        // Les conversations du **serveur** : le registre local ne montre que ce que l'app a
        // écrit sur ce téléphone. Les sessions du serveur — d'autres appareils, la CLI, les
        // anciennes — s'importent ici, une fois par serveur : sans cela, le volet restait vide
        // alors que le serveur en comptait des dizaines.
        viewModelScope.launch {
            // **Un import par serveur, pas par émission.** `activeServer` réémet à chaque réglage
            // touché (thème, police, profil, modèle) : l'import relançait alors une lecture de tous
            // les profils et une écriture complète de l'historique **par changement**, pour un
            // résultat identique. La clé est la signature de configuration : elle change quand le
            // serveur change, pas quand un réglage d'affichage bouge.
            var importedFor: String? = null
            servers.activeServer.collect { server ->
                if (!server.hasServeCredentials) return@collect
                if (importedFor == server.configSignature) return@collect
                // **Un import qui échoue ne se marque pas comme fait.**
                //
                // C'était le défaut de la réinstallation : la marque était posée même quand la
                // lecture avait échoué — serveur pas encore joignable, tunnel qui démarre, réseau
                // pas prêt sur les premières frames. La condition « déjà importé pour ce serveur »
                // était donc vraie pour toujours, et l'app restait vide **sans jamais réessayer**.
                // C'est exactement ce que le propriétaire a vu : « j'ai réinstallé, mes
                // conversations ne reviennent que dans Home ».
                //
                // On réessaie donc jusqu'à ce que ça passe, à intervalle croissant, plafonné :
                // une lecture par minute au pire, et plus rien dès que c'est fait.
                var attente = IMPORT_RETRY_FIRST_MS
                while (importedFor != server.configSignature) {
                    if (importServerSessions(server)) {
                        importedFor = server.configSignature
                    } else {
                        kotlinx.coroutines.delay(attente)
                        attente = (attente * 2).coerceAtMost(IMPORT_RETRY_CEILING_MS)
                    }
                }
            }
        }
        // Le menu qui s'ouvre sur un appui long sur l'icône : un raccourci de **bot** ouvre la
        // conversation de ce bot. La demande a été déposée par l'activité ([BotShortcutInbox]) ;
        // elle s'ouvre ici, une fois, puis se consomme — reprendre le même raccourci deux fois
        // ouvre deux fois, mais un retour dans l'app ne rouvre rien.
        viewModelScope.launch {
            BotShortcutInbox.requested.collect { request ->
                if (request == null) return@collect
                BotShortcutInbox.consume()
                openBotShortcut(request)
            }
        }
    }

    /**
     * Importe les sessions du serveur dans le registre local, pour que le volet les montre.
     *
     * Le registre local ne connaît que les conversations **écrites sur ce téléphone**. Les
     * sessions du serveur — celles d'autres appareils, celles de la CLI, les anciennes —
     * n'y sont pas : le volet paraissait alors vide alors que le serveur en comptait des
     * dizaines. Chaque session devient une conversation sans message : le fil se lit à
     * l'ouverture, jamais d'avance.
     *
     * Un échec de lecture ne casse rien : le registre local reste, et l'app continue.
     */
    private suspend fun importServerSessions(server: ServerConfig): Boolean {
        if (conversations.conversations.value.isEmpty()) conversations.load()
        // **Les profils à interroger** : ceux des bots retenus sur ce téléphone, plus celui du
        // serveur. C'est ce qui donne à chaque session le profil qui la porte — sans lui, une
        // conversation réimportée après une réinstallation perd son bot et prend le visage d'Onyx.
        val cached = runCatching { roster.load(server.rosterScope) }.getOrNull()
        val profiles = (cached?.bots.orEmpty().map { it.name } + listOfNotNull(server.effectiveProfile))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        val api = ServeSessionsApi(ServeClient(server))
        val tagged = runCatching { api.summariesByProfile(profiles) }.getOrElse { return false }
        // Une lecture qui a répondu « aucune session » est un succès : il n'y a rien à restaurer.
        if (tagged.isEmpty()) return true
        val summaries = tagged.map { (_, summary) -> summary }
        val profileById = tagged.associate { (profile, summary) -> summary.id to profile }
        val known = conversations.conversations.value
        // **Les chats des bots du roster ne sont pas des conversations.**
        //
        // Le roster gardé sur ce téléphone (`BotRosterCache`) nomme le `canonical_session` de chaque
        // bot. Sans cette liste, l'import les prenait pour des conversations ordinaires : une ligne
        // **vide** par bot, titrée de son nom et sans aperçu, apparaissait tout seule dans la liste
        // dès qu'on créait une conversation avec l'un d'eux (« j'ai 3 bots, je devrais avoir que le
        // bot que j'ai choisi »). Un chat de bot s'ouvre par sa tuile — il n'a rien à faire ici.
        val botChats = cached?.bots.orEmpty().mapNotNull { it.chatId }.toSet()

        // **Et les lignes déjà écrites par l'ancien import s'effacent.** Jusqu'ici le chat de
        // chaque bot était importé comme une conversation : les lignes vides sont dans le registre
        // du téléphone, et un correctif qui ne ferait que bloquer les suivantes les laisserait
        // là pour toujours. On ne touche qu'aux conversations **distantes** (`remote`) dont la
        // session est un chat de bot — jamais à une conversation écrite ici, qui peut légitimement
        // être le chat d'un bot.
        if (botChats.isNotEmpty()) {
            val stale = ServerSessions.staleBotChats(known, botChats)
            if (stale.isNotEmpty()) conversations.deleteAll(stale.toSet())
        }

        // **Les conversations déjà importées retrouvent leur profil.** Celles qu'une version
        // précédente a écrites n'ont pas de `botProfile` : sans cette reprise, le correctif ne
        // vaudrait que pour les imports suivants, et la liste resterait au visage d'Onyx jusqu'au
        // prochain redémarrage.
        known.filter { it.remote && it.botProfile == null }.forEach { conversation ->
            val profile = profileById[conversation.serverSessionId] ?: return@forEach
            conversations.save(conversation.copy(botProfile = profile))
        }

        val imported = ServerSessions.conversations(
            server = server,
            summaries = summaries,
            known = known,
            botChats = botChats,
            profileOf = { summary -> profileById[summary.id] },
        )
        imported.forEach { conversation -> conversations.save(conversation) }
        // Vrai dès que la lecture a abouti — même sans rien de neuf à écrire : c'est la **lecture**
        // qui doit être marquée comme faite, pas l'écriture.
        return true
    }

    /**
     * Ouvre la conversation du bot demandé par son raccourci.
     *
     * C'est le chemin d'une **tuile de bot** (« `openBot` » de la référence) : la conversation que
     * le roster nomme pour ce bot (`BotProfile.chatId`, son `canonical_session`) est retrouvée dans
     * l'historique ; à défaut, une conversation neuve est ouverte à son nom, et c'est ce titre qui
     * la rattache au bot. L'historique est relu **avant** de résoudre, sinon une conversation déjà
     * gardée mais pas encore chargée du disque serait dupliquée au premier appui.
     */
    private suspend fun openBotShortcut(request: BotShortcutRequest) {
        val server = _state.value.server
        if (conversations.conversations.value.isEmpty()) conversations.load()
        val known = conversations.conversations.value
        val bot = BotProfile(
            name = request.bot,
            title = request.label,
            isDefault = request.isDefault,
            chatId = request.chatId,
        )
        // Sans `canonical_session` (aucun Dashboard pour la nommer), `botChatToOpen` ouvre une
        // conversation **neuve** à chaque appui : le même raccourci pris deux fois laisserait deux
        // fils vides du même bot. On reprend donc celui qui est **vide** et déjà à ce bot, comme le
        // crayon du compositeur (`freshConversationForPencil`).
        val empty = known.firstOrNull { conversation ->
            conversation.isEmpty && botOfConversation(conversation, listOf(bot), server.displayName) != null
        }
        val chat = empty ?: botChatToOpen(
            bot = bot,
            conversations = known,
            serverId = server.id,
            mainName = server.displayName,
        )
        if (known.none { it.id == chat.id }) conversations.save(chat)
        open(chat)
    }

    /**
     * Le fil suit le tour en cours de cette conversation : la réponse qui s'écrit, l'étape connue
     * et l'issue. C'est le seul endroit qui arbitre une fin de tour.
     */
    private fun follow(live: TurnLive?) {
        val state = _state.value
        if (live == null) {
            if (state.isStreaming || state.live != null) {
                _state.update { it.copy(isStreaming = false, prefill = null, providerStatus = null, live = null) }
            }
            return
        }
        val phase = live.phase
        if (live.serverSessionId != null && live.serverSessionId != serverSessionId) {
            serverSessionId = live.serverSessionId
            // LOT appel-agent : la session vivante du gateway, pour que le pont d'appel puisse
            // parler au plugin (`command.dispatch` exige une session ouverte).
            if (com.onyx.app.di.AppContainer.isInstalled) {
                com.onyx.app.di.AppContainer.agentCalls.sessionId = live.serverSessionId
            }
        }
        _state.update {
            it.copy(
                messages = upsertMessage(it.messages, live.message),
                isStreaming = phase.isStreaming,
                prefill = phase.prefill,
                providerStatus = phase.providerStatus,
                serverSessionId = live.serverSessionId ?: it.serverSessionId,
                modelInUse = live.modelInUse ?: it.modelInUse,
                fallback = live.fallback ?: it.fallback,
                live = phase,
            )
        }
        if (!phase.isStreaming) settle(live)
    }

    /** Pose le message d'un tour en cours à sa place (ou l'ajoute, au retour dans la
     *  conversation) : l'écran n'écrit jamais dans la réponse, il la lit. */
    private fun upsertMessage(messages: List<ChatMessage>, message: ChatMessage): List<ChatMessage> =
        if (messages.any { it.id == message.id }) {
            messages.map { if (it.id == message.id) message else it }
        } else {
            messages + message
        }

    /**
     * Un tour qui vient de finir. Réussi : on enregistre et on reprend la file. Échoué : un tour
     * coupé par la connexion retourne dans la file au lieu d'être perdu, comme avant.
     */
    private fun settle(live: TurnLive) {
        if (!settledTurns.add(live.assistantId)) return
        val error = turns.session(live.conversationId)?.failure
        heldSince.remove(live.assistantId)
        if (error == null) {
            retryAttempt = 0
            persistCurrent()
            retryOutbox()
            if (settings.suggestFollowUps.value) requestFollowUps()
            return
        }

        val snapshot = _state.value
        val assistantIndex = snapshot.messages.indexOfFirst { it.id == live.assistantId }
        val user = snapshot.messages.take(assistantIndex.coerceAtLeast(0)).lastOrNull { it.role == Role.USER }
        val assistant = snapshot.messages.firstOrNull { it.id == live.assistantId }
        val blankReply = assistant != null && assistant.text.isEmpty() && assistant.reasoning.isEmpty() &&
            assistant.tools.isEmpty() && assistant.approvals.isEmpty() && assistant.clarify == null &&
            assistant.secret == null && assistant.sudoRequestId == null

        // Aucune réponse n'est arrivée : le tour courant retourne dans l'outbox au lieu d'être perdu.
        if (error.isConnectivity && user != null && blankReply) {
            val held = OutboxItem(
                id = user.id,
                text = user.text,
                attachments = user.attachments,
                queuedAt = heldSince.remove(live.assistantId) ?: now(),
                state = OutboxState.WAITING_FOR_CONNECTION,
            )
            queued.hold(held)
            _state.update {
                it.copy(
                    isStreaming = false,
                    prefill = null,
                    steerStatus = null,
                    messages = it.messages.filterNot { row -> row.id == user.id || row.id == live.assistantId },
                    queued = queued.items,
                    error = null,
                    canRetry = false,
                    isConnectionProblem = false,
                    live = null,
                )
            }
            persistCurrent()
            scheduleRetry()
            return
        }

        queued.pause()
        _state.update {
            it.copy(
                isStreaming = false,
                prefill = null,
                providerStatus = null,
                queued = queued.items,
                error = error.userMessage(),
                canRetry = lastRequest != null,
                isConnectionProblem = error.isConnectivity,
                live = null,
            )
        }
        persistCurrent()
    }

    fun onInputChange(value: String) {
        _state.update { it.copy(input = value) }
        updateSlashMenu(value)
        // Le brouillon suit la conversation : on le retrouve en la rouvrant.
        drafts.put(conversationId, value)
    }

    /**
     * Une ligne du menu « / » a été **touchée** : son texte entre dans le brouillon, et le menu se
     * referme.
     *
     * Sans cette fermeture, `onInputChange` relançait `updateSlashMenu` sur le texte choisi : le
     * menu se redessinait avec cette seule ligne, au-dessus du champ qu'on venait d'utiliser — et
     * une compétence, qu'il faut pouvoir compléter d'une instruction puis envoyer, restait sous un
     * panneau. C'était le second versant du défaut « les compétences sont inutilisables ».
     */
    fun pickSlash(text: String) {
        slashJob?.cancel()
        _state.update { it.copy(input = text, slashItems = emptyList()) }
        drafts.put(conversationId, text)
    }

    /** Joint des fichiers choisis : images ou textes, selon leur type. */
    fun addAttachments(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val app = getApplication<Application>()
        viewModelScope.launch {
            // Les limites sont vérifiées **avant** l'ajout (hors du fil principal : la copie
            // durable lit le fichier, puis on lit une taille) : ce qui dépasse est refusé ici,
            // avec son poids et le plafond, et ne part jamais vers le serveur.
            val admission = withContext(Dispatchers.IO) {
                // L'autorisation persistable d'abord — quand le fournisseur en accorde une —, la
                // copie ensuite : c'est elle la garantie, la première n'est que le confort.
                takeDurablePermissions(app, uris)
                admitAttachments(app, _state.value.attachments, uris)
            }
            _state.update {
                it.copy(
                    attachments = it.attachments + admission.accepted,
                    attachmentProblem = admission.problem,
                )
            }
        }
    }

    /**
     * Écarte les pièces jointes du **brouillon** — un démarrage de secours ne rejoue pas celle
     * qui a pu tuer le lancement précédent. Le fil garde la sienne : rien n'est supprimé d'un
     * message déjà envoyé.
     */
    fun discardDraftAttachments() {
        if (_state.value.attachments.isEmpty()) return
        _state.update { it.copy(attachments = emptyList()) }
    }

    fun removeAttachment(id: String) {
        _state.update { state ->
            state.copy(
                attachments = state.attachments.filterNot { it.id == id },
                // Le refus s'efface avec la pièce jointe qu'on retire : il parlait de l'ajout.
                attachmentProblem = null,
            )
        }
    }

    /** Répond à une question de l'agent, par le canal du Dashboard. */
    fun respondClarify(messageId: String, answers: Map<String, String>) {
        val message = _state.value.messages.firstOrNull { it.id == messageId } ?: return
        val request = message.clarify ?: return
        updateMessage(messageId) { it.copy(clarify = null) }

        val server = _state.value.server
        if (!server.hasServeCredentials) return
        viewModelScope.launch {
            runCatching {
                val rpc = com.onyx.app.data.net.ServeRpc(server)
                val client = com.onyx.app.data.net.ServeInterruptRpc(rpc)
                if (!request.isBatch) {
                    client.clarify(request.id, null, answers.values.firstOrNull().orEmpty())
                } else {
                    answers.forEach { (questionId, answer) -> client.clarify(request.id, questionId, answer) }
                }
                rpc.close()
            }.onFailure { error ->
                _state.update { it.copy(error = humanFailure(error)) }
            }
        }
    }

    /** Donne le mot de passe sudo demandé. */
    fun respondSudo(messageId: String, password: String) {
        val message = _state.value.messages.firstOrNull { it.id == messageId } ?: return
        val requestId = message.sudoRequestId ?: return
        updateMessage(messageId) { it.copy(sudoRequestId = null) }

        val server = _state.value.server
        if (!server.hasServeCredentials) return
        viewModelScope.launch {
            runCatching {
                val rpc = com.onyx.app.data.net.ServeRpc(server)
                com.onyx.app.data.net.ServeInterruptRpc(rpc).sudo(serverSessionId.orEmpty(), requestId, password)
                rpc.close()
            }.onFailure { error ->
                _state.update { it.copy(error = humanFailure(error)) }
            }
        }
    }

    /** Donne le secret demandé (une valeur vide le passe). */
    fun respondSecret(messageId: String, value: String) {
        val message = _state.value.messages.firstOrNull { it.id == messageId } ?: return
        val request = message.secret ?: return
        updateMessage(messageId) { it.copy(secret = null) }

        val server = _state.value.server
        if (!server.hasServeCredentials) return
        viewModelScope.launch {
            runCatching {
                val rpc = com.onyx.app.data.net.ServeRpc(server)
                com.onyx.app.data.net.ServeInterruptRpc(rpc).secret(serverSessionId.orEmpty(), request.id, value)
                rpc.close()
            }.onFailure { error ->
                _state.update { it.copy(error = humanFailure(error)) }
            }
        }
    }

    /**
     * Oriente un sous-agent en cours (`subagent.steer`), depuis sa transcription.
     *
     * `false` quand le gateway n'accepte pas la consigne pour le moment, ce que l'écran dit.
     *
     * Le canal est celui de la conversation, **pas** un socket neuf : le gateway n'accorde le
     * pilotage qu'au transport attaché à la session, et refuse un second socket en `4001` même
     * avec le bon identifiant.
     */
    suspend fun steerSubagent(id: String, text: String): Boolean {
        val server = _state.value.server
        if (!server.hasServeCredentials) return false
        return runCatching {
            val channel = rpc ?: com.onyx.app.data.net.ServeRpc(server).also { rpc = it }
            com.onyx.app.data.net.ServeSubagentRpc(channel).steer(parentSessionId(), id, text)
        }.getOrDefault(false)
    }

    /** Arrête un sous-agent en cours (`subagent.interrupt`) ; `false` s'il avait déjà fini. */
    suspend fun stopSubagent(id: String): Boolean {
        val server = _state.value.server
        if (!server.hasServeCredentials) return false
        return runCatching {
            val channel = rpc ?: com.onyx.app.data.net.ServeRpc(server).also { rpc = it }
            com.onyx.app.data.net.ServeSubagentRpc(channel).interrupt(parentSessionId(), id)
        }.getOrDefault(false)
    }

    /**
     * Relit le roster des sous-agents (`subagent.list`) et le garde pour l'écran des tâches.
     *
     * Sans tour en cours, c'est la seule source : les trames `subagent.*` ne viennent que pendant
     * un tour. Rendu vide (jamais une exception) quand le transport n'est pas le Dashboard — la
     * liste vit alors sur ce que le fil a déjà vu.
     */
    suspend fun refreshSubagentRoster() {
        val server = _state.value.server
        if (server.transport != com.onyx.app.data.model.Transport.HERMES_SERVE) return
        if (!server.hasServeCredentials) return
        runCatching {
            val channel = rpc ?: com.onyx.app.data.net.ServeRpc(server).also { rpc = it }
            com.onyx.app.data.net.ServeSubagentRpc(channel).list(parentSessionId())
        }.getOrNull()?.let { rows ->
            // Lu, même vide : c'est ce qui autorise l'écran à clore les tâches que le serveur ne
            // liste plus (sinon une liste vide ne distinguerait pas « rien ne tourne » de « pas
            // encore demandé »).
            _state.update { it.copy(subagentRoster = rows, subagentRosterRead = true) }
        }
    }

    /**
     * La transcription vivante d'un sous-agent (`subagent.tail`), ou `null` quand le transport ne
     * la porte pas — l'écran le dit alors au lieu d'attendre indéfiniment.
     */
    suspend fun subagentTranscript(agent: com.onyx.app.data.model.SubagentActivity): SubagentTailView? {
        val server = _state.value.server
        if (server.transport != com.onyx.app.data.model.Transport.HERMES_SERVE) return null
        if (!server.hasServeCredentials) return null
        val result = runCatching {
            val channel = rpc ?: com.onyx.app.data.net.ServeRpc(server).also { rpc = it }
            com.onyx.app.data.net.ServeSubagentRpc(channel).tail(parentSessionId(), agent.id)
        }.getOrNull() ?: return null
        return SubagentTailView(
            text = result.text,
            available = result.available,
            truncated = result.truncated,
        )
    }

    /** La session à nommer au gateway : la runtime quand on l'a vue, sinon celle conservée. */
    private fun parentSessionId(): String = runtimeSessionId ?: serverSessionId.orEmpty()

    /**
     * Pose un fil **injecté** de démonstration (drapeau `onyx.subagentDemo`), pour les captures :
     * l'émulateur n'a ni Dashboard ni agent, donc aucun sous-agent réel ne peut naître.
     *
     * Écrit l'état affiché seulement — aucune conversation n'est ouverte, rien n'est persisté sous
     * un identifiant : sans le drapeau, cette fonction n'est jamais appelée.
     */
    fun seedSubagentDemo(messages: List<com.onyx.app.data.model.ChatMessage>) {
        _state.update { it.copy(messages = messages, isStreaming = false, error = null) }
    }

    /** Renomme la conversation ; un titre vide revient à la première question. */
    fun rename(title: String) {
        val clean = title.trim()
        val id = conversationId
        val messages = _state.value.messages
        // L'en-tête suit le nom immédiatement, sans rouvrir la conversation.
        conversationName = clean.ifEmpty { null }
        _state.update { it.copy(conversationName = conversationName) }
        viewModelScope.launch {
            val existing = conversations.find(id) ?: return@launch
            conversations.save(
                existing.copy(
                    title = clean.ifEmpty {
                        messages.firstOrNull { it.isQuestion }?.let { Conversation.titleFrom(it.text) }
                            ?: Conversation.DefaultTitle
                    },
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    /** Le fil en Markdown — ce que « Export as Markdown » partage (`TranscriptExporter`). */
    fun exportMarkdown(assistantName: String = _state.value.server.displayName): String {
        val state = _state.value
        val title = conversations.find(conversationId)?.title ?: Conversation.DefaultTitle
        return buildString {
            appendLine("# $title")
            appendLine()
            state.messages.forEach { message ->
                if (message.text.isBlank() && message.tools.isEmpty()) return@forEach
                appendLine(if (message.isUser) "## You" else "## $assistantName")
                appendLine()
                message.reasoning.takeIf { it.isNotBlank() }?.let {
                    appendLine("<details><summary>Reasoning</summary>")
                    appendLine()
                    appendLine(it)
                    appendLine()
                    appendLine("</details>")
                    appendLine()
                }
                message.tools.forEach { tool ->
                    appendLine("> **${tool.name}**${tool.preview?.let { preview -> " · $preview" }.orEmpty()}")
                    appendLine()
                }
                if (message.text.isNotBlank()) {
                    appendLine(message.text)
                    appendLine()
                }
            }
        }
    }

    /** Les identifiants des messages qui contiennent le texte cherché (`Find in conversation`). */
    fun search(query: String): List<String> {
        val needle = query.trim()
        if (needle.isEmpty()) return emptyList()
        return _state.value.messages
            .filter { message ->
                message.text.contains(needle, ignoreCase = true) ||
                    message.reasoning.contains(needle, ignoreCase = true) ||
                    message.tools.any { tool ->
                        tool.name.contains(needle, ignoreCase = true) ||
                            tool.output.orEmpty().contains(needle, ignoreCase = true)
                    }
            }
            .map { it.id }
    }

    /**
     * Demande au gateway les commandes qui commencent par ce que l'on tape après « / ».
     * Le menu n'existe que sur une connexion Dashboard, comme sur la référence.
     */
    private fun updateSlashMenu(value: String) {
        slashJob?.cancel()
        if (!value.startsWith("/") || value.contains("\n") || _state.value.isStreaming) {
            _state.update { it.copy(slashItems = emptyList()) }
            return
        }
        // Les compétences du profil peuvent avoir changé (une installation dans « Compétences »,
        // un autre bot) : on les relit, au plus une fois par minute.
        if (now() - skillsLoadedAt > SKILLS_TTL_MS) refreshSlashCommands()
        // Sans connexion Dashboard, il n'y a pas de `complete.slash` : le menu montre les
        // commandes que l'app sait exécuter elle-même, plutôt que rien.
        if (!_state.value.server.hasServeCredentials) {
            val local = SlashCommands.matches(value, slashCommands).map { command ->
                SlashItem(text = command.name, display = command.display, detail = command.shownDetail)
            }
            _state.update { it.copy(slashItems = local) }
            return
        }
        slashJob = viewModelScope.launch {
            // **Une frappe, une requête — au plus.** Chaque caractère tapé après « / » lançait un
            // `complete.slash` : quatre-vingts appels pour un mot de vingt lettres, dont aucun ne
            // servait puisque le suivant annulait le précédent. Le petit délai n'ajoute rien à la
            // sensation (le menu apparaît quand le doigt s'arrête) et supprime la rafale.
            delay(SlashCompleteDebounceMs)
            val server = _state.value.server
            val channel = rpc ?: com.onyx.app.data.net.ServeRpc(server).also { rpc = it }
            val result = runCatching {
                channel.call(
                    method = "complete.slash",
                    params = kotlinx.serialization.json.buildJsonObject {
                        put("text", value)
                    },
                    timeoutMillis = 4_000,
                )
            }.getOrNull()
            val items = result?.asObject()?.array("items").orEmpty().mapNotNull { element ->
                val obj = element.asObject() ?: return@mapNotNull null
                val text = obj.firstString("text") ?: return@mapNotNull null
                SlashItem(
                    text = text,
                    display = obj.firstString("display") ?: text,
                    // La réponse porte `meta` pour ce qui s'affiche à droite du nom.
                    detail = obj.firstString("meta", "detail", "description").orEmpty(),
                )
            }.take(8)
            // Le menu du gateway, complété de ce que l'app sait déjà exécuter — `/model` et les
            // compétences du profil (`withLocalCommands`) : la même liste que l'exécution.
            val merged = withModelCommand(items, value)
            _state.update { it.copy(slashItems = withLocalCommands(merged, value, slashCommands)) }
        }
    }

    /** Ouvre ou ferme « Find in conversation ». */
    fun toggleSearch() {
        _state.update {
            it.copy(searchOpen = !it.searchOpen, searchQuery = "", searchHits = emptyList(), searchCurrent = 0)
        }
    }

    fun setSearchQuery(query: String) {
        val hits = search(query)
        _state.update { it.copy(searchQuery = query, searchHits = hits, searchCurrent = 0) }
    }

    /** Passe au résultat suivant (ou précédent) de la recherche. */
    fun stepSearch(delta: Int) {
        val state = _state.value
        if (state.searchHits.isEmpty()) return
        val next = Math.floorMod(state.searchCurrent + delta, state.searchHits.size)
        _state.update { it.copy(searchCurrent = next) }
    }

    fun clearError() {
        _state.update { it.copy(error = null, canRetry = false, isConnectionProblem = false) }
    }

    /**
     * Un fichier joint qui ne s'ouvre pas le dit dans la bande d'erreur du fil, en mots : la raison
     * du serveur ou du système, jamais un silence qui laisserait croire à une réussite.
     */
    fun showProblem(text: String) {
        _state.update { it.copy(error = text, canRetry = false, isConnectionProblem = false) }
    }

    /**
     * Nouvelle conversation : vide le fil et l'identifiant de session serveur.
     *
     * Le tour de la conversation qu'on quitte n'est **pas** arrêté : il vit dans le magasin, il
     * continue, et sa réponse est là quand on la rouvre.
     */
    fun newConversation() {
        persistCurrent()
        serverSessionId = null
        conversationName = null
        conversationId = UUID.randomUUID().toString()
        restoredInitial = true
        queued.clear()
        retryJob?.cancel()
        retryJob = null
        heldSince.clear()
        _state.update {
            it.switchToConversation(
                conversationId = conversationId,
                serverSessionId = null,
                messages = emptyList(),
                input = drafts.get(conversationId),
                queued = emptyList(),
                conversationName = null,
            )
        }
    }

    /**
     * Rouvre une conversation enregistrée. Un tour encore en cours dans une autre conversation
     * continue d'écrire : on le retrouve entier au retour.
     */
    fun open(conversation: Conversation) {
        conversationId = conversation.id
        serverSessionId = conversation.serverSessionId
        // Un titre qui n'est pas celui qu'aurait donné la première question est un nom choisi :
        // l'en-tête doit le montrer plutôt que la question.
        conversationName = conversation.customName()
        queued.replace(conversation.outbox)
        heldSince.clear()
        restoredInitial = true
        _state.update {
            it.switchToConversation(
                conversationId = conversation.id,
                serverSessionId = conversation.serverSessionId,
                // Les cartes d'une réponse relue sont retrouvées depuis ses appels d'outil quand
                // elles n'ont pas été enregistrées (`ReplyCards.resolve`, comme référence).
                messages = ReplyCards.resolve(conversation.messages).map { message -> message.copy(isStreaming = false) },
                input = drafts.get(conversation.id),
                queued = queued.items,
                conversationName = conversationName,
            )
        }
        scheduleRetry()
        resumeDetachedTurn(conversation)
        loadRemoteThread(conversation)
    }

    /**
     * Le fil d'une conversation **du serveur** : la projection qu'on a dans le registre n'a que
     * son titre, pas ses messages. Le fil se lit ici, à l'ouverture — le serveur en garde la
     * source, et rien n'est écrit sur ce téléphone.
     *
     * Un fil déjà chargé (une conversation locale qui a aussi une session) n'est pas relu :
     * ce qu'on a ici est plus frais que ce que le serveur en sait.
     */
    private fun loadRemoteThread(conversation: com.onyx.app.data.model.Conversation) {
        if (!conversation.remote) return
        if (conversation.messages.isNotEmpty()) return
        val sessionId = conversation.serverSessionId?.takeIf { it.isNotBlank() } ?: return
        val server = com.onyx.app.ui.design.ProfileInForce.appliedTo(_state.value.server, _state.value.botProfile)
        viewModelScope.launch {
            val rows = runCatching {
                com.onyx.app.data.net.ServeSessionMessages(server).rows(sessionId)
            }.getOrElse { return@launch }
            if (rows.isEmpty()) return@launch
            val loaded = rows.mapIndexed { index, row ->
                com.onyx.app.data.model.ChatMessage(
                    id = java.util.UUID.randomUUID().toString(),
                    role = if (row.role == "user") com.onyx.app.data.model.Role.USER
                    else com.onyx.app.data.model.Role.ASSISTANT,
                    text = row.text,
                    createdAt = conversation.createdAt + index,
                )
            }
            if (conversationId != conversation.id) return@launch
            _state.update { state ->
                state.copy(messages = state.messages + loaded.map { it.copy(isStreaming = false) })
            }
        }
    }

    /**
     * **Le tour que l'hôte a fini sans nous.**
     *
     * Fermer l'app pendant qu'un bot travaille ne l'arrête plus : le serveur finit le run et écrit
     * la réponse dans la session (le client annonce `X-Hermes-Keep-Running`). Au retour sur la
     * conversation, la bulle laissée en suspens se remplit donc avec ce que l'hôte a écrit —
     * pendant que l'app était fermée, ou pendant qu'elle finissait d'arriver.
     *
     * Trois refus explicites, dans cet ordre :
     *  - un tour **vit encore** dans le processus : c'est lui qui écrit, on ne double pas ;
     *  - le transport ne tient **pas** de session côté serveur : il n'y a rien à relire ;
     *  - pas d'identifiant de session conservé : la même raison.
     *
     * Et une règle de fond : sans réponse d'assistant **après** le dernier message de l'utilisateur,
     * on ne touche à rien (`ServeSessionMessages.trailingReply`) — une vieille réponse n'est jamais
     * la nouvelle.
     */
    private fun resumeDetachedTurn(conversation: com.onyx.app.data.model.Conversation) {
        recoveryJob?.cancel()
        recoveryJob = null
        val pending = conversation.messages.lastOrNull()?.takeIf { message ->
            message.role == Role.ASSISTANT && (message.isStreaming || message.text.isBlank())
        } ?: return
        if (turns.session(conversation.id) != null) return
        val state = _state.value
        val server = com.onyx.app.ui.design.ProfileInForce.appliedTo(state.server, state.botProfile)
        if (server.transport == Transport.CHAT_COMPLETIONS && server.apiBaseUrl.isBlank()) return
        if (server.transport != Transport.CHAT_COMPLETIONS && server.transport != Transport.HERMES_SESSIONS) return
        val sessionId = conversation.serverSessionId?.takeIf { it.isNotBlank() } ?: return
        recoveryJob = viewModelScope.launch {
            val client = com.onyx.app.data.net.ServeSessionMessages(server)
            repeat(RECOVERY_ATTEMPTS) { attempt ->
                val fromHost = runCatching { client.reply(sessionId) }.getOrNull()
                val merged = com.onyx.app.data.net.ServeSessionMessages.merge(pending.text, fromHost)
                if (merged != null) {
                    updateMessage(pending.id) { message -> message.copy(text = merged, isStreaming = false) }
                    persistCurrent()
                    return@launch
                }
                // Le run peut encore tourner chez l'hôte : on redemande, sans rien affirmer entre-temps.
                if (attempt < RECOVERY_ATTEMPTS - 1) delay(RECOVERY_DELAY_MS)
            }
        }
    }

    fun delete(conversation: Conversation) {
        // Une conversation supprimée n'a plus de tour qui tourne : il est arrêté chez l'hôte,
        // jamais laissé en arrière-plan (miroir de `Conversation.delete` de la référence).
        turns.forget(conversation.id)
        settledTurns.removeAll { turns.session(conversation.id)?.assistantId == it }
        viewModelScope.launch {
            conversations.delete(conversation.id)
            if (conversation.id == conversationId) newConversation()
        }
    }

    /** Les cartes de l'écran de démarrage : reprendre la dernière conversation. */
    fun startCards(): List<com.onyx.app.ui.chat.components.StartCard> {
        val recent = conversations.mostRecent(excluding = conversationId) ?: return emptyList()
        return listOf(
            com.onyx.app.ui.chat.components.StartCard(
                id = recent.id,
                symbol = com.onyx.app.ui.design.Symbols.Bubbles,
                title = OnyxLocalization["Continue: "] + recent.title,
                subtitle = OnyxLocalization["Opens your most recent conversation"],
                isContinue = true,
            ),
        )
    }

    /** Envoie le contenu du composer. */
    fun send() {
        val current = _state.value
        val text = current.input.trim()
        if (text.isEmpty() && current.attachments.isEmpty()) return
        // « /model » nu ouvre le sélecteur de modèle (mêmes écran et effet que par l'en-tête),
        // au lieu de partir au serveur — comme `ComposerView.send` côté référence.
        if (current.attachments.isEmpty() && isModelCommand(text)) {
            _state.update { it.copy(input = "", modelPickerRequested = true, slashItems = emptyList()) }
            drafts.put(conversationId, "")
            return
        }
        sendText(text, allowEmpty = current.attachments.isNotEmpty())
    }

    /** L'écran du modèle s'est ouvert : on abaisse la demande (l'effet ne se rejoue pas). */
    fun consumeModelPickerRequest() {
        _state.update { it.copy(modelPickerRequested = false) }
    }

    /**
     * Le modèle **du bot** de la conversation ouverte — le même souvenir que celui qu'écrit la
     * feuille de modèle ([com.onyx.app.ui.bots.BotsViewModel.setModel] → `FaceKeys.model`).
     *
     * L'écran le pousse dès que la conversation (ou le roster du bot) change : c'est ce qui fait que
     * la pilule du haut nomme **immédiatement** le modèle choisi, et que l'envoi le porte
     * ([ChatUiState.modelInForce] : conversation → bot → serveur → défaut). `null` pour une
     * conversation simple.
     *
     * Un nouveau choix **invalide l'écho du serveur** ([ChatUiState.modelInUse]) : sans cela, l'étage
     * « conversation » garderait l'ancien modèle servi et le modèle du bot ne prendrait pas — l'app
     * afficherait et enverrait le précédent, en ignorant le choix qui vient d'être fait.
     */
    fun setBotModel(model: String?) {
        val clean = model?.trim()?.takeIf { it.isNotEmpty() }
        _state.update { if (it.botModel == clean) it else it.copy(botModel = clean, modelInUse = null) }
    }

    /**
     * Le **profil du bot** de la conversation ouverte — la liaison bot → profil.
     *
     * L'écran le pousse dès que la conversation (ou le roster du bot) change, comme [setBotModel] le
     * fait pour le modèle. C'est ce qui fait que le tour suivant part **dans le profil de ce bot**
     * (`profile` sur `session.create`, adresse `/p/<profil>`), au lieu du profil global du serveur
     * — le défaut que le propriétaire a vu (« coder » annoncé, `default` servi).
     *
     * `null` pour une conversation simple (le profil du serveur décide) ; `""` pour le profil
     * principal (le bot `isDefault`, qui laisse la passerelle sur son profil par défaut).
     */
    fun setBotProfile(profile: String?) {
        val clean = profile?.trim()
        val changed = _state.value.botProfile != clean
        _state.update { if (it.botProfile == clean) it else it.copy(botProfile = clean) }
        // Les compétences sont celles **du profil** : changer de bot change la liste des
        // commandes « / » (celles d'un profil ne sont pas celles d'un autre).
        if (changed) refreshSlashCommands()
        // **Le bot se retient avec la conversation.** Sans cette ligne, l'app ne sait plus, au
        // redémarrage, que cette conversation est celle de « coder » : le roster arrive après
        // l'écran, et l'en-tête montrait Onyx — visage bleu, « @default » — le temps de deux
        // frames avant de sauter sur le vrai bot.
        val id = _state.value.conversationId?.takeIf { it.isNotBlank() }
        if (id != null) viewModelScope.launch { conversations.bindBot(id, clean) }
    }

    /**
     * Envoie un texte venu d'ailleurs que du composer — la dictée, l'écran vocal, une tuile de
     * montre, un partage entrant.
     */
    fun sendText(
        text: String,
        clearInput: Boolean = true,
        modelOverride: String? = null,
        allowEmpty: Boolean = false,
    ) {
        val current = _state.value
        val clean = text.trim()
        if (clean.isEmpty() && !(allowEmpty && current.attachments.isNotEmpty())) return

        // Une commande « / » ne part jamais comme un message du fil : `/model` nu ouvre le
        // sélecteur, les commandes de l'app agissent, et le reste va au gateway (`slash.exec`).
        if (clean.startsWith("/") && !clean.contains('\n') && current.attachments.isEmpty()) {
            submitSlash(clean, clearInput)
            return
        }

        // Pendant une réponse, ou derrière un message déjà retenu, on garde texte et pièces jointes.
        if (current.isStreaming || queued.items.isNotEmpty()) {
            val item = OutboxItem(
                id = UUID.randomUUID().toString(),
                text = clean,
                attachments = current.attachments.map { it.toAttachment() },
                queuedAt = now(),
            )
            queued.enqueue(item, waiting = false)
            drafts.put(conversationId, "")
            _state.update {
                it.copy(
                    input = if (clearInput) "" else it.input,
                    attachments = emptyList(),
                    queued = queued.items,
                )
            }
            persistCurrent()
            if (!current.isStreaming) retryOutbox()
            return
        }

        val history = current.messages
            .filter { it.error == null && it.text.isNotBlank() }
            .map { ChatTurn(role = it.role, text = it.text) }
        appendTurn(text = clean, history = history, clearInput = clearInput, modelOverride = modelOverride)
    }

    /**
     * Une commande « / » : ce que l'app exécute, elle le fait ; le reste part au gateway.
     *
     * Miroir de `ComposerView.send()` (le `/model` nu) et de `HermesServeTransport.runSlash`
     * (`slash.exec`, puis `command.dispatch` sur l'erreur 4018).
     */
    private fun submitSlash(text: String, clearInput: Boolean) {
        if (clearInput) clearDraft()
        when (SlashCommands.action(text, slashCommands)) {
            SlashAction.OPEN_MODEL_PICKER,
            SlashAction.NEW_CONVERSATION,
            SlashAction.FIND,
            SlashAction.SETTINGS,
            SlashAction.VOICE,
            -> _state.update {
                it.copy(pendingSlash = SlashCommands.action(text, slashCommands), slashItems = emptyList())
            }

            SlashAction.HELP -> {
                _state.update { it.copy(slashItems = emptyList()) }
                appendLocalReply(SlashCommands.helpText(slashCommands))
            }

            // Une compétence du profil n'est pas une commande du worker : `slash.exec` la refuse
            // exprès (« skill command: use command.dispatch »).
            SlashAction.INVOKE_SKILL -> invokeSkill(text)

            SlashAction.GATEWAY -> runSlashOnGateway(text)
        }
    }

    /**
     * Invoque une compétence du profil, et **envoie** le message qui la charge.
     *
     * `command.dispatch` rend le message destiné au modèle (`type: skill`) : c'est lui qui part
     * comme le tour suivant, exactement le contrat de la référence (`gateway_chat_commands.forward`
     * appelle `view.submit(reply["message"])`). Le fil, lui, garde la ligne tapée — le corps de la
     * compétence n'est pas une parole de l'utilisateur.
     */
    private fun invokeSkill(text: String) {
        val server = _state.value.server
        if (!server.hasServeCredentials) {
            appendLocalReply(OnyxLocalization["Slash commands need a connection to the Dashboard."])
            return
        }
        val line = text.trim().removePrefix("/")
        val name = line.substringBefore(' ').trim()
        val argument = line.substringAfter(' ', "").trim()
        _state.update { it.copy(slashItems = emptyList()) }
        viewModelScope.launch {
            // Un tour est déjà en cours : la compétence attend son tour au lieu d'ouvrir un second
            // flux sur la même conversation.
            //
            // **On attend le changement, on ne le scrute pas.** La boucle réveillait le processus
            // cinq fois par seconde sans limite ; un flux qui dure dix minutes, c'est trois mille
            // réveils pour rien — et chacun republie une lecture d'état dans un tour déjà chargé.
            // `first { }` se réveille au changement, et `withTimeout` rend la main plutôt que
            // d'attendre indéfiniment un tour qui ne finira jamais.
            runCatching {
                withTimeoutOrNull(SlashWaitTimeoutMs) {
                    state.first { !it.isStreaming }
                }
            }
            val channel = rpc ?: com.onyx.app.data.net.ServeRpc(server).also { rpc = it }
            val result = runCatching {
                channel.call(
                    method = "command.dispatch",
                    params = buildJsonObject {
                        // La session vivante, quand elle existe : `command.dispatch` s'en sert pour
                        // nommer la tâche de la compétence, et accepte une valeur vide.
                        put("session_id", parentSessionId())
                        put("name", name)
                        put("arg", argument)
                    },
                    timeoutMillis = 30_000,
                )
            }
            val payload = result.getOrNull()?.asObject()
            val message = payload?.firstString("message")
            if (message != null) {
                val history = _state.value.messages
                    .filter { it.error == null && it.text.isNotBlank() }
                    .map { ChatTurn(role = it.role, text = it.text) }
                appendTurn(text = message, history = history, displayText = text.trim())
                return@launch
            }
            val error = result.exceptionOrNull()
            val reason = (error as? TransportError)?.userMessage() ?: error?.message.orEmpty()
            appendLocalReply(
                OnyxLocalization["The command did not run."] + " " + reason,
            )
        }
    }

    /**
     * Relit les compétences du profil : `api/skills`, la route que l'écran « Compétences » utilise
     * déjà. C'est **la** source de la liste que le menu et l'exécution partagent.
     *
     * Sans connexion Dashboard, il n'y a pas de route : la liste reste celle des six commandes
     * fixes. Une lecture qui échoue ne casse rien non plus — un profil sans compétence donne
     * exactement la liste d'avant.
     */
    private fun refreshSlashCommands() {
        skillsJob?.cancel()
        skillsLoadedAt = now()
        val server = _state.value.server
        if (!server.hasServeCredentials) {
            slashCommands = SlashCommands.commands()
            return
        }
        // Les compétences sont celles du profil du bot quand la conversation en porte un ; sinon
        // celles du profil du serveur (`ServeClient` s'en charge quand on ne nomme personne).
        val profile = _state.value.botProfile?.takeIf { it.isNotBlank() }
        skillsJob = viewModelScope.launch {
            val skills = runCatching { ServeSkillsApi(ServeClient(server)).skills(profile) }
                .getOrDefault(emptyList())
            slashCommands = SlashCommands.commands(skills)
            // Le menu déjà ouvert se relit avec la liste fraîche : les compétences y entrent.
            val draft = _state.value.input
            if (draft.startsWith("/")) updateSlashMenu(draft)
        }
    }

    /** L'écran a exécuté la commande : elle ne doit pas se rejouer à la recomposition suivante. */
    fun consumeSlash() {
        _state.update { it.copy(pendingSlash = null) }
    }

    /**
     * Envoie la commande au gateway : `slash.exec` d'abord, `command.dispatch` si le gateway
     * répond 4018 (« ce n'est pas une commande du worker »). Sans connexion Dashboard, il n'y a
     * personne pour l'exécuter : la réponse le dit plutôt que d'envoyer la ligne comme un message.
     */
    private fun runSlashOnGateway(text: String) {
        val server = _state.value.server
        if (!server.hasServeCredentials) {
            appendLocalReply(OnyxLocalization["Slash commands need a connection to the Dashboard."])
            return
        }
        val command = text.trim().removePrefix("/").trim()
        viewModelScope.launch {
            val channel = rpc ?: com.onyx.app.data.net.ServeRpc(server).also { rpc = it }
            val result = runCatching {
                channel.call(
                    method = "slash.exec",
                    params = buildJsonObject {
                        // `session_id` n'est utile qu'à une session ouverte ; le worker accepte vide.
                        put("session_id", serverSessionId.orEmpty())
                        put("command", command)
                    },
                    timeoutMillis = 30_000,
                )
            }
            val output = result.getOrNull()?.asObject()?.firstString("output")
            if (output != null) {
                appendLocalReply(output.ifBlank { "(no output)" })
                return@launch
            }
            val error = result.exceptionOrNull()
            if (error is TransportError.Rpc && error.code == SLASH_NOT_A_COMMAND) {
                dispatchCustomCommand(channel, command)
                return@launch
            }
            appendLocalReply(
                OnyxLocalization["The command did not run."] + " " +
                    ((error as? TransportError)?.userMessage() ?: error?.message.orEmpty()),
            )
        }
    }

    /** `command.dispatch` : les commandes propres à l'utilisateur, celles que `slash.exec` ignore. */
    private suspend fun dispatchCustomCommand(
        channel: com.onyx.app.data.net.ServeRpc,
        command: String,
    ) {
        val name = command.substringBefore(' ').trim()
        val argument = command.substringAfter(' ', "").trim()
        val result = runCatching {
            channel.call(
                method = "command.dispatch",
                params = buildJsonObject {
                    put("session_id", serverSessionId.orEmpty())
                    put("name", name)
                    put("arg", argument)
                },
                timeoutMillis = 30_000,
            )
        }
        val reply = result.getOrNull()?.asObject()
        // Une compétence (ou une commande qui compose un tour, `/plan`…) : le gateway rend le
        // message **destiné au modèle**, et c'est lui qui part — jamais dans une bulle. C'est le
        // repli d'une compétence que la liste connue ne portait pas encore.
        val message = reply?.firstString("message")
        val type = reply?.firstString("type")
        if (message != null && (type == "skill" || type == "send")) {
            val history = _state.value.messages
                .filter { it.error == null && it.text.isNotBlank() }
                .map { ChatTurn(role = it.role, text = it.text) }
            val typed = "/" + name + if (argument.isEmpty()) "" else " " + argument
            appendTurn(text = message, history = history, displayText = typed)
            return
        }
        val shown = reply
            ?.let { it.firstString("display") ?: it.firstString("output") }
            ?: humanFailure(result.exceptionOrNull(), "(done)")
        appendLocalReply(shown)
    }

    /** Ce qu'une commande a répondu : une bulle de l'agent, comme `TurnEvent.textDelta` de la référence. */
    private fun appendLocalReply(text: String) {
        val reply = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = Role.ASSISTANT,
            text = text,
            createdAt = now(),
        )
        _state.update { it.copy(messages = it.messages + reply) }
        persistCurrent()
    }

    /**
     * Pilote la réponse en cours sans l'interrompre (`Conversation.steer`).
     *
     * La note s'affiche comme un message de l'utilisateur, placée avant la réponse qui streame
     * pour qu'elle reste la dernière ligne du fil. Seul un transport à registre (`hasLedger`)
     * accepte un pilotage : ailleurs le composeur n'offre que la file.
     */
    fun steer(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val snapshot = _state.value
        if (!snapshot.canSteer) return
        val note = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = Role.USER,
            text = clean,
            createdAt = now(),
            isSteer = true,
        )
        _state.update { state ->
            val index = steerInsertIndex(state.messages)
            state.copy(
                messages = state.messages.toMutableList().apply { add(index, note) },
                input = "",
                slashItems = emptyList(),
            )
        }
        clearDraft()
        persistCurrent()
        deliverSteer(clean)
    }

    /** Le canal qui porte `session.steer` : celui du Dashboard, le seul qui sache piloter. */
    private fun deliverSteer(text: String) {
        val state = _state.value
        val session = serverSessionId
        if (state.server.transport != Transport.HERMES_SERVE ||
            !state.server.hasServeCredentials ||
            session.isNullOrEmpty()
        ) {
            // Aucun canal pour piloter : référence rend la main avec « steer rejected » et garde la
            // note dans le fil. On ne prétend pas que le tour a été piloté.
            _state.update { it.copy(steerStatus = OnyxLocalization["steer rejected (turn already finishing)"]) }
            return
        }
        viewModelScope.launch {
            val channel = rpc ?: com.onyx.app.data.net.ServeRpc(state.server).also { rpc = it }
            val result = runCatching {
                channel.call(
                    method = "session.steer",
                    params = buildJsonObject {
                        put("session_id", session)
                        put("text", text)
                    },
                    timeoutMillis = 8_000,
                )
            }
            val status = result.getOrNull()?.asObject()?.firstString("status")
            val queued = status == "queued"
            _state.update {
                it.copy(
                    steerStatus = if (queued) {
                        OnyxLocalization["steer queued"]
                    } else {
                        OnyxLocalization["steer rejected (turn already finishing)"]
                    },
                )
            }
        }
    }

    private fun clearDraft() {
        drafts.put(conversationId, "")
        _state.update { it.copy(input = "", slashItems = emptyList()) }
    }

    /** Rejoue le dernier tour (bouton « Retry » ou « Regenerate »). */
    fun retry() {
        val current = _state.value
        if (current.isStreaming) return
        val lastUser = current.messages.lastOrNull { it.role == Role.USER } ?: return
        val history = current.messages
            .filter { it.error == null && it.text.isNotBlank() && it.id != lastUser.id }
            .map { ChatTurn(role = it.role, text = it.text) }
        // Le message fautif et la réponse vide sont retirés avant de rejouer le tour.
        _state.update { it.copy(messages = it.messages.filterNot { m -> m.error != null || m.isStreaming }) }
        appendTurn(text = lastUser.text, history = history, clearInput = false)
    }

    /**
     * Demande la restauration d'une réponse **à ce message** : rien n'est retiré avant la
     * confirmation. On relève seulement ce qui serait défait.
     */
    fun requestRestore(messageId: String) {
        val current = _state.value
        if (current.isStreaming) return
        // Rien n'est retiré ici : on relève seulement ce qui **serait** défait, et on le soumet.
        val plan = restorePlan(current.messages, messageId) ?: return
        _state.update { it.copy(restorePrompt = plan) }
    }

    /** La personne refuse : **rien** n'est défait. */
    fun cancelRestore() {
        _state.update { it.copy(restorePrompt = null) }
    }

    /**
     * La personne confirme : le fil est ramené **à** ce message, et la session du gateway est
     * rembobinée du même nombre de tours quand le transport sait le faire (`session.undo`, le
     * Dashboard). Sur l'API partagée, qui n'a pas d'annulation, seule notre copie du fil est
     * coupée : l'avis le dit franchement.
     */
    fun confirmRestore() {
        val prompt = _state.value.restorePrompt ?: return
        _state.update { it.copy(restorePrompt = null) }
        val current = _state.value
        if (current.messages.none { it.id == prompt.messageId }) return
        val kept = messagesThroughRestore(current.messages, prompt.messageId)

        turns.stop(conversationId)
        retryJob?.cancel()
        retryJob = null
        queued.clear()
        settledTurns.clear()
        _state.update {
            it.copy(
                messages = kept,
                queued = emptyList(),
                isStreaming = false,
                live = null,
                prefill = null,
                providerStatus = null,
                steerStatus = null,
                error = null,
                canRetry = false,
                followUps = emptyList(),
            )
        }
        persistCurrent()

        val session = serverSessionId
        val server = _state.value.server
        val undoable = prompt.removedTurns > 0 && !session.isNullOrEmpty() &&
            server.transport == Transport.HERMES_SERVE && server.hasServeCredentials
        viewModelScope.launch {
            val rewound = if (undoable) rewindGateway(session.orEmpty(), prompt.removedTurns) else null
            _state.update { it.copy(restoreNotice = restoreNotice(prompt, rewound)) }
        }
    }

    /** L'avis de restauration a été affiché : il ne se rejoue pas. */
    fun consumeRestoreNotice() {
        if (_state.value.restoreNotice != null) _state.update { it.copy(restoreNotice = null) }
    }

    /**
     * « Brancher à partir d'ici » : demande confirmation **avant** de créer quoi que ce soit.
     *
     * Comme la restauration, la demande ne touche à rien : elle relève seulement ce qui **serait**
     * copié (le message visé et tout ce qui le précède) et le soumet à la confirmation.
     */
    fun requestBranch(messageId: String) {
        val current = _state.value
        if (current.isStreaming) return
        val plan = branchPlan(current.messages, messageId) ?: return
        _state.update { it.copy(branchPrompt = plan) }
    }

    /** La personne refuse : **rien** n'est créé, et le fil ne bouge pas d'un pouce. */
    fun cancelBranch() {
        _state.update { it.copy(branchPrompt = null) }
    }

    /**
     * La personne confirme : une **nouvelle conversation** qui porte la copie du fil jusqu'à ce
     * message inclus, et elle **s'ouvre**. L'originale n'est ni réécrite, ni amputée : elle garde
     * ses messages, son titre et son identifiant.
     *
     * **Aucun tour ne démarre ici, et rien ne part au serveur** : le branchement est une copie
     * locale (`ConversationRepository.branchFrom`) suivie d'une ouverture. La branche naît sans
     * session serveur et sans file d'attente, donc aucun envoi ne peut s'y déclencher tout seul ;
     * c'est le premier message écrit dans la nouvelle conversation qui ouvrira sa session.
     *
     * Le serveur, lui, n'est pas branlable : le gateway n'expose pas de route pour bifurquer une
     * session (`POST api/sessions/{id}/branch` n'existe pas). La copie vit donc sur ce téléphone —
     * et c'est dit dans la confirmation, en toutes lettres.
     */
    fun confirmBranch() {
        val prompt = _state.value.branchPrompt ?: return
        _state.update { it.copy(branchPrompt = null) }
        val current = _state.value
        if (current.messages.none { it.id == prompt.messageId }) return
        // L'originale telle qu'elle est **maintenant** : ses messages en mémoire (le fil vivant),
        // son titre et sa session d'origine. Rien de tout cela n'est modifié par ce qui suit.
        val source = (conversations.find(conversationId) ?: Conversation.start(id = conversationId)).copy(
            serverId = current.server.id,
            serverSessionId = serverSessionId,
            messages = current.messages.map { it.copy(isStreaming = false) },
        )
        viewModelScope.launch {
            conversations.branchFrom(source, prompt.messageId)?.let { branch ->
                // La nouvelle conversation s'ouvre ici même ; l'écran du chat suit son état.
                open(branch)
            }
        }
    }

    /**
     * `session.undo` retire **le dernier tour** ; pour revenir à un message plus haut, on l'appelle
     * une fois par tour utilisateur retiré (c'est ce que fait la référence). Rend `true` quand tous
     * les tours sont passés, `false` dès qu'un appel échoue ou que le serveur en refuse un.
     */
    private suspend fun rewindGateway(session: String, turns: Int): Boolean {
        val channel = rpc ?: com.onyx.app.data.net.ServeRpc(_state.value.server).also { rpc = it }
        repeat(turns) {
            val result = runCatching {
                channel.call(
                    method = "session.undo",
                    params = buildJsonObject { put("session_id", session) },
                    timeoutMillis = 15_000,
                )
            }.getOrNull()?.asObject() ?: return false
            if ((result.firstString("removed")?.toIntOrNull() ?: 0) <= 0) return false
        }
        return true
    }

    /**
     * L'avis de fin de restauration : ce qui a été défait, et ce que le serveur a pu suivre.
     *
     * Le **vrai** compte y passe comme dans la confirmation, et le zéro y est dit autrement : « rien
     * n'a été retiré » plutôt que « retiré : 0 messages ».
     */
    private fun restoreNotice(prompt: RestorePrompt, rewound: Boolean?): String {
        val base = if (prompt.removedMessages == 0) {
            OnyxLocalization["Restored here — nothing was removed."]
        } else {
            String.format(
                OnyxLocalization["Restored here — %1\$s removed."],
                restoreCountPhrase(prompt.removedMessages),
            )
        }
        return when (rewound) {
            true -> base + " " + OnyxLocalization["The session was rewound to the same point."]
            false -> base + " " + OnyxLocalization["The server could not rewind its own copy; it still holds them."]
            null -> base
        }
    }

    /**
     * Le bouton d'arrêt : le tour en cours est **vraiment** arrêté, chez l'hôte aussi.
     * Le texte déjà reçu reste dans le fil.
     */
    fun stop() {
        // L'ordre part **avant** la coupure locale : depuis que le tour survit à la fermeture de
        // l'app, annuler l'écoute ne suffit plus — sans `POST /v1/runs/{id}/stop`, le bot
        // continuerait de travailler dans le dos de l'utilisateur.
        val stateBefore = _state.value
        val runId = turns.session(conversationId)?.live?.value?.runId
        val server = com.onyx.app.ui.design.ProfileInForce.appliedTo(
            stateBefore.server, stateBefore.botProfile,
        )
        if (!runId.isNullOrBlank()) {
            viewModelScope.launch {
                runCatching { com.onyx.app.data.net.ServeRunStop(server).stop(runId) }
            }
        }
        turns.stop(conversationId)
        retryJob?.cancel()
        retryJob = null
        queued.pause()
        _state.update { state ->
            state.copy(
                isStreaming = false,
                prefill = null,
                steerStatus = null,
                live = null,
                queued = queued.items,
                messages = state.messages.map { if (it.isStreaming) it.copy(isStreaming = false) else it },
            )
        }
        persistCurrent()
    }

    /** Répond à une demande d'approbation, dans les mots du gateway (`once`… `deny`). */
    fun respondApproval(messageId: String, approvalId: String, choice: String) {
        val message = _state.value.messages.firstOrNull { it.id == messageId } ?: return
        val approval = message.approvals.firstOrNull { it.id == approvalId } ?: return
        if (!approval.isPending) return

        updateMessage(messageId) { current ->
            current.copy(
                approvals = current.approvals.map {
                    if (it.id == approvalId) it.copy(decision = choice) else it
                },
            )
        }

        val runId = approval.runId
        if (runId.isNullOrEmpty() || _state.value.server.transport != Transport.HERMES_SESSIONS) return

        viewModelScope.launch {
            try {
                GatewayApprovalClient(_state.value.server).respond(runId, approvalId, choice)
            } catch (error: CancellationException) {
                throw error
            } catch (error: TransportError) {
                _state.update {
                    it.copy(error = OnyxLocalization["The approval did not reach the server."] + " " + error.userMessage())
                }
            }
        }
    }

    private fun appendTurn(
        text: String,
        history: List<ChatTurn>,
        clearInput: Boolean = true,
        modelOverride: String? = null,
        outgoing: OutboxItem? = null,
        /**
         * Ce que le fil montre comme parole de l'utilisateur, quand le texte **envoyé** n'est pas le
         * sien : l'invocation d'une compétence envoie le corps de la compétence, et la bulle garde
         * la ligne tapée (`/resume ce fil`).
         */
        displayText: String? = null,
    ) {
        // L'état **et** le serveur : le modèle en vigueur se lit sur l'état — la **même** résolution
        // que la pilule du haut, pour que ce qui est nommé soit exactement ce qui part. Et le
        // **profil** : la conversation d'un bot porte **son** profil (adresse, `profile` de session,
        // modèle et fournisseur du profil), pas celui du serveur — la liaison bot → profil.
        val state = _state.value
        val server = com.onyx.app.ui.design.ProfileInForce.appliedTo(state.server, state.botProfile)
        val attachments = outgoing?.attachments?.map { it.toPendingAttachment() } ?: _state.value.attachments
        val userMessage = ChatMessage(
            id = outgoing?.id ?: UUID.randomUUID().toString(),
            role = Role.USER,
            text = displayText ?: text,
            createdAt = now(),
            attachments = attachments.map { it.toAttachment() },
        )
        val assistantId = UUID.randomUUID().toString()
        val assistantMessage = ChatMessage(
            id = assistantId,
            role = Role.ASSISTANT,
            createdAt = now(),
            metrics = TurnMetrics(startedAt = now()),
            isStreaming = true,
        )
        if (outgoing != null) heldSince[assistantId] = outgoing.queuedAt

        drafts.clear(conversationId)
        _state.update { state ->
            state.copy(
                messages = state.messages + userMessage + assistantMessage,
                input = if (clearInput) "" else state.input,
                isStreaming = true,
                prefill = null,
                error = null,
                canRetry = false,
                isConnectionProblem = false,
            )
        }

        // Sauvegarde immédiate du tour et de l'outbox : une fermeture en cours de stream ne peut
        // pas ressusciter le message retiré de la file à côté de sa bulle déjà envoyée.
        restoredInitial = true
        persistCurrent()

        // Les images partent en data URL ; un texte joint est ajouté au message, comme la note
        // que l'app de référence écrit pour un fichier.
        val images = attachments.filter { it.isImage }.mapNotNull { encodeImage(it.uri) }
        val fileNotes = attachments.filterNot { it.isImage }.mapNotNull { readTextAttachment(it.uri, it.name) }
        val fullText = if (fileNotes.isEmpty()) text else text + "\n\n" + fileNotes.joinToString("\n\n")

        if (outgoing == null) _state.update { it.copy(attachments = emptyList()) }

        val request = ChatRequest(
            history = history + ChatTurn(role = Role.USER, text = fullText, images = images),
            instructions = SettingsRepository.replyInstruction(settings.replyLanguage.value),
            model = modelOverride ?: state.modelInForce,
            provider = server.effectiveProvider,
            reasoningEffort = settings.reasoningEffort.value.takeIf { it.isNotEmpty() },
            fast = server.transport == Transport.HERMES_SESSIONS &&
                serverSessionId?.let { runControls.isFast(server.configSignature, it) } == true,
        )
        lastRequest = request
        startStream(server, request, assistantId, prompt = fullText)
    }

    private fun startStream(
        server: ServerConfig,
        request: ChatRequest,
        assistantId: String,
        prompt: String,
    ) {
        val conversation = conversationId
        // Le tour part dans le magasin du processus : l'écran qui l'a lancé peut disparaître
        // (retour, autre conversation, activité détruite) sans l'arrêter.
        turns.start(
            conversationId = conversation,
            assistantId = assistantId,
            userText = prompt,
            serverSessionId = serverSessionId,
        ) { departure ->
            // **La requête porte exactement le modèle en vigueur** — le même nom que la pilule du haut
            // écrit (`ChatUiState.modelInForce`). Aucune substitution ici : `withResolvedModel`
            // réécrivait le modèle quand le champ était vide ou inconnu (`available.first()`, souvent
            // `sage-a1.5`) alors que la pilule disait « Modèle par défaut » — l'app nommait une chose
            // et la requête en portait une autre. Si rien n'est en vigueur, **rien n'est envoyé** : le
            // serveur choisit, et c'est exactement ce que « Modèle par défaut » annonce.
            lastRequest = request
            val effectiveRequest = lastRequest ?: request
            // Ce qui part vraiment : la barre du haut le montre (c'est la même valeur).
            effectiveRequest.model?.trim()?.takeIf { it.isNotEmpty() }?.let { resolved ->
                turns.noteModel(conversation, assistantId, resolved)
            }
            when (server.transport) {
                // Le tour passe par le canal JSON-RPC du Dashboard (WebSocket), comme référence — et
                // par le **même socket** que les commandes de sous-agent : le gateway n'accorde
                // le pilotage qu'au transport attaché à la session.
                Transport.HERMES_SERVE -> {
                    val channel = rpc ?: com.onyx.app.data.net.ServeRpc(server).also { rpc = it }
                    HermesServeChat(server, channel.asChatChannel(), ownsLink = false)
                        .stream(effectiveRequest, serverSessionId, departure) { runtimeSessionId = it }
                }
                else -> ChatCompletionsClient(server).stream(effectiveRequest)
            }
        }
    }

    /**
     * Demande trois questions de relance au modèle, comme `FollowUpChips` les reçoit côté référence.
     * Un petit tour à part, une fois la réponse finie.
     */
    private fun requestFollowUps() {
        val state = _state.value
        val server = state.server
        if (state.messages.count { it.role == Role.USER } < 1) return
        val history = state.messages
            .filter { it.text.isNotBlank() }
            .takeLast(6)
            .map { ChatTurn(role = it.role, text = it.text) }
        viewModelScope.launch {
            val builder = StringBuilder()
            runCatching {
                ChatCompletionsClient(server).stream(
                    ChatRequest(
                        history = history + ChatTurn(
                            role = Role.USER,
                            text = "Suggest three short follow-up questions I might ask next. " +
                                "One per line, no numbering, no quotes, at most 60 characters each.",
                        ),
                        model = state.modelInForce,
                    ),
                ).collect { event ->
                    when (event) {
                        is TurnEvent.TextDelta -> builder.append(event.text)
                        is TurnEvent.TextFinal -> {
                            builder.clear()
                            builder.append(event.text)
                        }
                        else -> Unit
                    }
                }
                val lines = builder.toString()
                    .lineSequence()
                    .map { it.trim().trimStart('-', '*', '•').trim().trim('"') }
                    .filter { it.isNotEmpty() && it.length <= 80 }
                    .take(3)
                    .toList()
                _state.update { it.copy(followUps = lines) }
            }
        }
    }

    /** Le lecteur confirme la tête de file ; les suivants reprennent ensuite dans le même ordre. */
    fun sendQueuedNow(id: String) {
        if (!queued.confirmFirst(id, now())) return
        retryAttempt = 0
        _state.update { it.copy(queued = queued.items) }
        retryOutbox()
    }

    /** « Edit » retire le message de la file et le rend au composer avec ses pièces jointes. */
    fun editQueued(id: String) {
        val item = queued.remove(id) ?: return
        drafts.put(conversationId, item.text)
        _state.update {
            it.copy(
                input = item.text,
                attachments = item.attachments.map { attachment -> attachment.toPendingAttachment() },
                queued = queued.items,
            )
        }
        persistCurrent()
    }

    fun deleteQueued(id: String) {
        queued.remove(id) ?: return
        if (queued.items.isEmpty()) {
            retryJob?.cancel()
            retryJob = null
        }
        _state.update { it.copy(queued = queued.items) }
        persistCurrent()
    }

    /** Envoie la tête si elle est autorisée à partir, jamais un message en pause ou périmé. */
    private fun retryOutbox() {
        if (_state.value.isStreaming) return
        val next = queued.takeNext(now())
        if (next == null) {
            _state.update { it.copy(queued = queued.items) }
            persistCurrent()
            return
        }
        _state.update { it.copy(queued = queued.items) }
        val history = _state.value.messages
            .filter { it.error == null && it.text.isNotBlank() }
            .map { ChatTurn(role = it.role, text = it.text) }
        appendTurn(text = next.text, history = history, clearInput = false, outgoing = next)
    }

    private fun scheduleRetry() {
        val first = queued.items.firstOrNull()
        if (_state.value.isStreaming || first?.state != OutboxState.WAITING_FOR_CONNECTION) return
        retryJob?.cancel()
        val delayMs = retryDelaysMs[minOf(retryAttempt, retryDelaysMs.lastIndex)]
        retryAttempt += 1
        retryJob = viewModelScope.launch {
            kotlinx.coroutines.delay(delayMs)
            retryOutbox()
            scheduleRetry()
        }
    }

    /** Enregistre le fil tel qu'il est : appelé à la fin d'un tour et au changement de serveur. */
    private fun persistCurrent() {
        val state = _state.value
        if (state.messages.isEmpty() && queued.items.isEmpty()) return
        val id = conversationId
        val serverId = state.server.id
        val messages = state.messages.map { it.copy(isStreaming = false) }
        val session = serverSessionId
        viewModelScope.launch {
            val existing = conversations.find(id)
            val base = existing ?: Conversation.start(id = id)
            conversations.save(
                base.copy(
                    serverId = serverId,
                    serverSessionId = session,
                    messages = messages,
                    outbox = queued.items,
                    updatedAt = maxOf(
                        base.updatedAt,
                        messages.maxOfOrNull { it.createdAt } ?: base.updatedAt,
                        queued.items.maxOfOrNull { it.queuedAt } ?: base.updatedAt,
                    ),
                    title = if (base.title == Conversation.DefaultTitle) {
                        messages.firstOrNull { it.isQuestion }?.let { Conversation.titleFrom(it.text) }
                            ?: base.title
                    } else {
                        base.title
                    },
                ),
            )
        }
    }

    /**
     * Une image en data URL — l'API n'accepte pas d'URI locale.
     *
     * Deux chemins, et une borne sur les deux : une image ordinaire part telle quelle, mais une
     * image plus lourde que [MAX_INLINE_IMAGE] est **réduite** avant l'envoi. Lire entière une
     * photo de 40 Mo pour la convertir en base64 tenait son poids plusieurs fois en mémoire (les
     * octets, la chaîne, sa copie), et une image de 8000×6000 décodée en pleine résolution pèse
     * 192 Mo — de quoi tuer le processus au pire moment. Le décodage passe par [BoundedImages] :
     * borné en pixels et en octets, et `null` plutôt qu'une exception.
     */
    private fun encodeImage(uri: Uri): String? {
        val resolver = getApplication<Application>().contentResolver
        return runCatching {
            val mime = resolver.getType(uri) ?: "image/jpeg"
            val bytes = resolver.openInputStream(uri)?.use { input ->
                val buffer = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(64 * 1024)
                while (true) {
                    val step = input.read(chunk)
                    if (step <= 0) break
                    buffer.write(chunk, 0, step)
                    if (buffer.size() > MAX_INLINE_IMAGE) break
                }
                buffer.toByteArray()
            } ?: return null
            if (bytes.size <= MAX_INLINE_IMAGE) {
                return "data:$mime;base64," + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            }
            // Trop lourde pour partir telle quelle : on relit la source **réduite** (le flux est
            // rouvert, les octets tronqués ne servent pas à décoder), et le type envoyé est celui
            // du ré-encodage.
            val bitmap = BoundedImages.decodeUri(getApplication(), uri, SEND_PX, SEND_BYTES) ?: return null
            val reduced = java.io.ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, reduced)
            bitmap.recycle()
            "data:image/jpeg;base64," + android.util.Base64.encodeToString(reduced.toByteArray(), android.util.Base64.NO_WRAP)
        }.getOrNull()
    }

    /** Le texte d'un fichier joint, borné, avec son nom — la note de la référence pour un fichier. */
    private fun readTextAttachment(uri: Uri, name: String): String? {
        val resolver = getApplication<Application>().contentResolver
        return runCatching {
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
            if (bytes.size > MAX_TEXT_ATTACHMENT) return null
            val text = bytes.decodeToString()
            "[Attached file: $name]\n$text"
        }.getOrNull()
    }

    /**
     * La pièce jointe du message porte **la copie durable** ([PendingAttachment.uri]), son nom, sa
     * taille et son type : c'est ce qui reste dans le fil après la fermeture de l'app.
     */
    private fun PendingAttachment.toAttachment(): Attachment =
        Attachment(id = id, mimeType = mimeType, uri = uri.toString(), name = name, size = size)

    private fun Attachment.toPendingAttachment(): PendingAttachment = PendingAttachment(
        id = id,
        uri = Uri.parse(uri),
        name = name ?: uri.substringAfterLast('/'),
        mimeType = mimeType,
        isImage = mimeType.startsWith("image/"),
        size = size.takeIf { it > 0L } ?: durableFileLength(Uri.parse(uri)) ?: 0L,
    )

    /**
     * Modifie un message du fil. La réponse d'un tour en cours appartient au magasin : c'est lui
     * qui l'écrit, sinon la modification serait écrasée par le fragment suivant du serveur.
     */
    private fun updateMessage(id: String, transform: (ChatMessage) -> ChatMessage) {
        if (turns.update(conversationId, id, transform)) return
        _state.update { state ->
            state.copy(messages = state.messages.map { if (it.id == id) transform(it) else it })
        }
    }

    private fun now(): Long = System.currentTimeMillis()

    private companion object {
        /**
         * Effort de raisonnement demandé aux modèles qui le supportent.
         * Aucune consigne système par défaut, comme sur la référence : `instructions` reste vide tant que
         * l'utilisateur n'a pas réglé de langue de réponse.
         */
        const val REASONING_EFFORT = "medium"

        /** Un fichier joint plus gros n'est pas ajouté au message. */
        const val MAX_TEXT_ATTACHMENT = AttachmentLimits.MAX_TEXT_BYTES

        /**
         * Le poids au-delà duquel une image est réduite avant l'envoi : une photo ordinaire
         * (2 à 6 Mo) part intacte, une image « pour analyse » de plusieurs dizaines de mégaoctets
         * est ramenée à [SEND_PX] sur son plus grand côté.
         */
        const val MAX_INLINE_IMAGE = 16 * 1024 * 1024

        /** Le plus grand côté d'une image réduite pour l'envoi : ce qu'un modèle regarde encore. */
        const val SEND_PX = 2048

        /** Le budget mémoire de cette réduction, sous le plafond de dessin. */
        const val SEND_BYTES = 32 * 1024 * 1024

        /** Le gateway rend 4018 quand la ligne n'est pas une commande de son worker. */
        const val SLASH_NOT_A_COMMAND = 4018

        /**
         * Combien de temps la liste des compétences reste fraîche : `api/skills` est relu au plus
         * une fois par minute, quand le menu « / » s'ouvre.
         */
        const val SKILLS_TTL_MS = 60_000L

        /**
         * Le rattrapage d'un tour fini sans client : combien de relectures, et à quel rythme.
         *
         * Douze relectures espacées de cinq secondes couvrent une minute — le temps qu'un run
         * laissé tourner chez l'hôte finisse d'écrire. Au-delà, la bulle reste telle quelle : rien
         * n'est inventé, et le prochain retour sur la conversation retentera.
         */
        const val RECOVERY_ATTEMPTS = 12
        const val RECOVERY_DELAY_MS = 5_000L
    }
}

/**
 * Où poser une note de pilotage : juste avant la réponse en cours, pour qu'elle reste la dernière
 * ligne du fil et que la réponse vivante ne cesse pas de l'être.
 *
 * Miroir de `Conversation.steer` : `messages.lastIndex(where: { $0.role == .assistant })`,
 * la fin du fil quand aucune réponse n'existe encore.
 */
internal fun steerInsertIndex(messages: List<ChatMessage>): Int {
    val last = messages.indexOfLast { it.role == Role.ASSISTANT }
    return if (last < 0) messages.size else last
}
