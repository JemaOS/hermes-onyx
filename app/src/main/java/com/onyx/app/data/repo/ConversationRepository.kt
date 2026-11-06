package com.onyx.app.data.repo

import androidx.datastore.core.DataStore
import com.onyx.app.data.local.DeletedConversations
import com.onyx.app.data.model.ChatMessage
import com.onyx.app.data.model.Conversation
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * L'historique des conversations, sur disque.
 *
 * Miroir de `ConversationStore` côté référence : les conversations sont triées de la plus
 * récente à la plus ancienne, et l'écran de chat enregistre le fil à la fin de chaque tour.
 *
 * **Une suppression est définitive.** Le fichier ne suffit pas à le garantir : d'autres chemins
 * réécrivent une conversation sans savoir qu'elle a été supprimée (la fin d'un tour qui enregistre
 * son fil, un rattachement de bot, une migration). Chaque suppression pose donc une **marque**
 * ([DeletedConversations]) — durable, hors du fichier — et [save] refuse d'écrire un identifiant
 * marqué : une conversation supprimée ne peut plus revenir par aucun chemin, ni au redémarrage.
 * Seul [restore] (le « Annuler » du bandeau) retire la marque.
 */
class ConversationRepository(
    private val store: DataStore<List<Conversation>>,
    private val deleted: DeletedConversations = DeletedConversations.InMemory,
) {

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())

    /** Le verrou d'écriture du fichier : un seul écrivain à la fois. */
    private val writeLock = Mutex()
    /**
     * Les demandes d'écriture, **fusionnées** : vingt publications pendant un tour ne font qu'une
     * entrée en attente, et c'est l'état le plus frais qui part.
     */
    private val writeRequests = Channel<Unit>(Channel.CONFLATED)
    /** Vrai quand le fichier ne porte pas encore l'état courant. */
    private val dirty = AtomicBoolean(false)
    /**
     * Le scope du seul écrivain. Interne et vivant aussi longtemps que le dépôt (un singleton de
     * l'app) : aucune écriture n'a besoin d'être attendue par l'appelant, et une rafale ne peut plus
     * bloquer le fil qui la produit.
     */
    private val writer = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        writer.launch {
            // **L'écrivain unique.** Chaque demande est suivie d'une courte fenêtre qui laisse la
            // rafale se tasser ; le canal étant fusionné, tout ce qui arrive pendant ce temps ne
            // fait qu'une entrée — et l'écriture prend l'état **courant**, pas celui de la demande.
            // **La fenêtre suit le prix de l'écriture.** Le magasin tient toutes les conversations
            // dans un seul JSON : une sauvegarde resérialise et réécrit **tout**. Mesuré
            // (`ConversationWriteCostTest`) : une conversation de 60 outils pèse 256 Kio et coûte
            // 7 ms ; vingt-cinq de ces conversations pèsent 6,4 Mio et coûtent 33 ms. À fenêtre fixe
            // (250 ms), un tour qui lance 60 outils en déclenchait quatre par seconde — 130 ms de CPU
            // et 26 Mio de disque par seconde, pendant que la personne lit. La fenêtre s'allonge donc
            // avec le nombre de messages à écrire : on sauvegarde moins souvent quand sauvegarder
            // coûte plus cher.
            for (request in writeRequests) {
                delay(writeSettleMillis(_conversations.value.sumOf { it.messages.size }))
                writeLock.withLock { writeFile() }
            }
        }
    }

    /** Les conversations, la plus récente d'abord. */
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    /** Lit le fichier au démarrage. */
    suspend fun load() {
        val list = withContext(Dispatchers.IO) {
            runCatching { store.data.first() }.getOrDefault(emptyList())
        }
        // Une conversation marquée supprimée est écartée **aussi** à la lecture : un fichier écrit
        // par une version antérieure (ou par une synchronisation) ne la ramène pas non plus.
        _conversations.value = list.filterNot { it.id in deleted.marked() }.sortedByDescending { it.updatedAt }
    }

    fun find(id: String): Conversation? = _conversations.value.firstOrNull { it.id == id }

    /** Épingle ou désépingle une conversation, et réécrit le fichier. */
    suspend fun setPinned(id: String, pinned: Boolean) {
        val existing = find(id) ?: return
        save(existing.copy(pinned = pinned, updatedAt = System.currentTimeMillis()))
    }

    /** Range une conversation dans les archives, ou l'en sort. */
    suspend fun setArchived(id: String, archived: Boolean) {
        val existing = find(id) ?: return
        save(existing.copy(archived = archived, updatedAt = System.currentTimeMillis()))
    }

    /** Renomme une conversation. */
    suspend fun rename(id: String, title: String) {
        val existing = find(id) ?: return
        save(existing.copy(title = title, updatedAt = System.currentTimeMillis()))
    }

    /**
     * « Créer un fil » : une **branche** de cette conversation — un fil neuf qui porte la **copie**
     * du transcript, et s'ouvre à part. La copie vit **sur ce téléphone** : le serveur d'Onyx n'a
     * pas de route pour bifurquer une session (`POST api/sessions/{id}/fork` n'existe pas côté
     * serveur), il n'y a donc rien à lui demander — la branche est locale, comme le reste du fil.
     *
     * Rend la conversation créée (l'appelant l'ouvre), ou `null` si l'originale a disparu.
     */
    suspend fun fork(id: String, now: Long = System.currentTimeMillis()): Conversation? {
        val existing = find(id) ?: return null
        val branch = forkedConversation(existing, java.util.UUID.randomUUID().toString(), now)
        save(branch)
        return branch
    }

    /**
     * « Brancher à partir d'ici » : une **branche** de [conversation] qui ne garde que ses messages
     * **jusqu'à [messageId] inclus** — le message visé et tout ce qui le précède, dans l'ordre.
     *
     * C'est la **même** règle de coupe que la restauration (`messagesThroughRestore`) ; ce qui
     * change est le sens : la restauration **retire** de la conversation, la branche **copie**
     * ailleurs et laisse l'originale intacte. La règle elle-même vit dans [branchedConversation],
     * pure, un peu plus bas.
     *
     * Enregistre la branche et la rend (l'appelant l'ouvre) ; rien n'est envoyé nulle part, et
     * l'originale n'est ni modifiée ni réécrite. Rend `null` quand le message visé a disparu.
     */
    suspend fun branchFrom(conversation: Conversation, messageId: String, now: Long = System.currentTimeMillis()): Conversation? {
        val branch = branchedConversation(conversation, java.util.UUID.randomUUID().toString(), messageId, now) ?: return null
        save(branch)
        return branch
    }

    /**
     * Épingle plusieurs conversations d'un coup (la sélection de la référence).
     *
     * **Une écriture, pas N.** Chaque `save` réécrit le fichier **entier** — toutes les
     * conversations, tous les messages. Épingler douze conversations en réécrivait douze fois le
     * même fichier, sur une action qui n'a qu'un résultat : la liste change, puis le disque la
     * reçoit une fois.
     */
    suspend fun pinAll(ids: Set<String>, pinned: Boolean) {
        if (ids.isEmpty()) return
        val touched = _conversations.value.map { conversation ->
            if (conversation.id in ids) conversation.copy(pinned = pinned) else conversation
        }
        if (touched == _conversations.value) return
        _conversations.value = touched
        persist()
    }

    /** Range plusieurs conversations dans les archives — même règle : une seule écriture. */
    suspend fun archiveAll(ids: Set<String>) {
        if (ids.isEmpty()) return
        val touched = _conversations.value.map { conversation ->
            if (conversation.id in ids) conversation.copy(archived = true) else conversation
        }
        if (touched == _conversations.value) return
        _conversations.value = touched
        persist()
    }

    /**
     * **Renommer un bot renomme ses conversations.**
     *
     * Le titre d'une conversation de bot **est** le nom du bot : c'est lui qui la rattache à lui
     * quand le `canonical_session` ne dit rien (`botOfConversation` lit `botName()`). Renommer le
     * bot sans retitrer ses fils les laissait porter l'ancien nom dans la liste et, pire, les
     * **détachait** du bot — qui perdait alors son visage et son sous-titre.
     *
     * [profile] est le nom du profil (l'identité, immuable) ; [fromLabel] l'ancien libellé, tel que
     * la liste le montrait ; [canonicalSessionId] la session que le serveur donne à ce bot — la
     * seule chose qui rattache une conversation écrite avant que `botProfile` existe.
     *
     * **Le titre seul ne suffit pas à reconnaître un bot.** L'ancienne règle renommait toute
     * conversation sans `botProfile` dont le titre valait l'ancien nom : deux bots appelés de la
     * même façon — ou un bot et les fils d'un autre, créés sous le même libellé — se
     * renommaient ensemble. Renommer un bot changeait le nom des autres, ce qui n'est pas
     * cohérent : on ne touche ici qu'au bot visé et à ce qui lui appartient vraiment.
     *
     * **Et seulement aux titres qui n'étaient que son nom.** Un fil que le propriétaire a nommé
     * lui-même (« Refonte du parser ») garde son nom : le renommage du bot ne réécrit pas ce qu'il
     * a écrit. Ne suivent que les fils muets ou encore à l'ancien libellé — ceux dont le titre
     * n'était rien d'autre que le nom du bot.
     *
     * Une seule écriture, comme les autres actions de masse.
     */
    suspend fun retitleBot(
        profile: String,
        fromLabel: String,
        toLabel: String,
        canonicalSessionId: String? = null,
    ) {
        if (toLabel.isBlank() || profile.isBlank()) return
        val old = fromLabel.trim()
        val canonique = canonicalSessionId?.takeIf { it.isNotBlank() }
        val touched = _conversations.value.map { conversation ->
            // **Le fil d'un bot se reconnaît à deux choses, et à rien d'autre.**
            //
            // 1. sa session canonique — ce que le serveur donne au profil, et qui ne se confond
            //    avec aucun autre bot ;
            // 2. son profil **et** un titre qui n'était que son nom.
            //
            // Le profil seul ne suffit pas : deux bots peuvent le partager (même libellé par
            // défaut, profil recopié à l'import), et renommer l'un renommait alors les fils de
            // l'autre — « j'ai changé le nom de ce bot, ça change tous les bots ». Et un titre
            // écrit par quelqu'un ne suit jamais un renommage : il n'appartient plus au bot.
            val parSession = canonique != null && conversation.serverSessionId == canonique
            // **Une session étrangère tranche : ce fil n'est pas à ce bot.**
            // Un fil qui porte la session d'un autre bot ne se renomme jamais par son titre,
            // même si son profil dit « coder » — c'est ainsi que renommer un bot renommait les
            // fils des autres.
            val sessionEtrangere = !conversation.serverSessionId.isNullOrBlank() && !parSession
            val parNom = !sessionEtrangere &&
                conversation.botProfile == profile &&
                conversation.title.trim() == old
            val mine = parSession || parNom
            when {
                !mine -> conversation
                conversation.title.trim().isEmpty() -> conversation.copy(title = toLabel)
                conversation.title.trim() == toLabel -> conversation
                // Un titre qui n'était **que** l'ancien nom suit le bot ; tout autre est gardé.
                parNom || conversation.title.trim() == old -> conversation.copy(title = toLabel)
                else -> conversation
            }
        }
        if (touched == _conversations.value) return
        _conversations.value = touched
        persist()
    }

    suspend fun deleteAll(ids: Set<String>) {
        ids.forEach { deleted.mark(it) }
        _conversations.update { current -> current.filterNot { it.id in ids } }
        // **Une suppression attend son écriture.** Les publications du fil peuvent se tasser, une
        // suppression non : si le processus meurt dans la fenêtre de tassement, la conversation
        // reviendrait au prochain démarrage. C'est le seul chemin où l'attente est due au disque.
        flush()
    }

    /**
     * Efface **toutes** les conversations de ce téléphone, comme `Conversation.eraseAll()` de la référence.
     * Rien n'est touché sur le serveur — mais chaque conversation effacée est **marquée** : ce qui a
     * été effacé ne revient pas, même par un chemin qui ne lit pas le fichier.
     */
    suspend fun eraseAll() {
        _conversations.value.forEach { deleted.mark(it.id) }
        _conversations.update { emptyList() }
        flush()
    }

    /**
     * Enregistre (ou remplace) une conversation et réécrit le fichier.
     *
     * Refuse un identifiant **marqué supprimé** : c'est la garantie qu'aucun chemin ne ramène une
     * conversation effacée (voir la note de classe). Le « Annuler » passe par [restore], qui défait
     * la marque d'abord.
     */
    suspend fun save(conversation: Conversation) {
        if (conversation.id in deleted.marked()) return
        // **Rien n'a changé : rien à réécrire.** Un tour réémet souvent la même forme (une carte
        // répondue, un secret donné) et chaque écriture réécrit *toutes* les conversations du
        // fichier : avec beaucoup de conversations, c'est la liste entière qui repart sur le disque
        // pour un champ identique.
        // **La lecture-écriture doit être atomique.** `_conversations.value = (value - lui + lui)`
        // lit puis réécrit : deux `save()` simultanés (le viewModel publie, un rappel de tour
        // publie) repartaient chacun de la même liste et le second **effaçait la conversation de
        // l'autre** — douze sauvegardes concurrentes n'en laissaient qu'une sur le disque. C'est un
        // défaut de perte de données, pas de performance, trouvé par le test de coalescence.
        var changed = false
        _conversations.update { current ->
            val previous = current.firstOrNull { it.id == conversation.id }
            // **Le cas courant d'abord, sans comparer le texte.** `previous == conversation` est une
            // égalité de `data class` : elle compare **tous** les messages, tout leur texte et toutes
            // leurs sorties d'outil — des mégaoctets, à chaque sauvegarde. Quand la liste est la
            // **même instance** (le viewModel ne la copie plus s'il n'y a rien à dépouiller) et que
            // les champs de surface sont égaux, la réponse est connue sans lire un caractère.
            if (previous === conversation) return@update current
            if (previous != null && previous.messages === conversation.messages &&
                previous.copy(messages = emptyList()) == conversation.copy(messages = emptyList())
            ) {
                return@update current
            }
            if (previous == conversation) return@update current
            changed = true
            (current.filterNot { it.id == conversation.id } + conversation).sortedByDescending { it.updatedAt }
        }
        if (!changed) return
        persist()
    }

    /** « Annuler » du bandeau : défait la marque de suppression, puis réenregistre la conversation. */
    suspend fun restore(conversation: Conversation) {
        deleted.unmark(conversation.id)
        save(conversation)
    }

    /**
     * **Retient le bot d'une conversation** (`Conversation.botProfile`) pour que l'écran le
     * connaisse dès le redémarrage, sans attendre le roster.
     *
     * Même valeur que celle que l'envoi porte au tour : `""` pour le profil principal, sinon le nom
     * du profil. Ne touche ni la date ni le titre : la liste ne bouge pas parce qu'un bot a été
     * reconnu.
     */
    suspend fun bindBot(conversationId: String, profile: String?) {
        val existing = find(conversationId) ?: return
        if (existing.botProfile == profile || existing.remote) return
        save(existing.copy(botProfile = profile))
    }

    /** Ajoute un message au fil enregistré, en créant la conversation au besoin. */
    suspend fun append(conversationId: String, serverId: String, message: ChatMessage, now: Long = System.currentTimeMillis()) {
        val existing = find(conversationId)
        val updated = (existing ?: Conversation.start(id = conversationId, now = now).copy(serverId = serverId))
            .withMessage(message, now)
        save(updated)
    }

    /**
     * Écrit (ou remplace) **un** message du fil enregistré.
     *
     * C'est ce qui fait survivre la réponse d'un tour dont l'écran a disparu : le magasin des
     * tours l'écrit au fil de son avancement, sans personne pour appeler `save` depuis un écran.
     */
    suspend fun upsertMessage(conversationId: String, message: ChatMessage) {
        val existing = find(conversationId) ?: return
        val messages = (existing.messages.filterNot { it.id == message.id } + message)
            .sortedBy { it.createdAt }
        save(
            existing.copy(
                messages = messages,
                updatedAt = maxOf(existing.updatedAt, message.createdAt),
                title = if (existing.title == Conversation.DefaultTitle && message.role == com.onyx.app.data.model.Role.USER) {
                    Conversation.titleFrom(message.text)
                } else {
                    existing.title
                },
            ),
        )
    }

    /**
     * Supprime une conversation : elle quitte le fichier, et son identifiant est **marqué** — elle
     * ne peut plus revenir par un `save` (l'enregistrement d'un tour qui se terminait, un
     * rattachement de bot, une migration de profil), ni au redémarrage.
     */
    suspend fun delete(id: String) {
        deleted.mark(id)
        _conversations.update { current -> current.filterNot { it.id == id } }
        // Voir [deleteAll] : une suppression attend son écriture, elle ne se tasse pas.
        flush()
    }

    /** La conversation la plus récente à laquelle on peut revenir depuis l'écran de démarrage. */
    fun mostRecent(excluding: String? = null): Conversation? =
        _conversations.value.firstOrNull { it.id != excluding && it.turnCount > 0 }

    private suspend fun persist() {
        // **Demander, jamais écrire soi-même.** `store.updateData` réécrit le fichier **entier**
        // (toutes les conversations locales, avec leur historique complet) et `persistCurrent()` est
        // appelé à seize endroits, plusieurs fois par tour : c'était une réécriture complète par
        // étape d'outil. La demande est maintenant *fusionnée* (une entrée en attente, jamais N) et
        // l'écrivain unique y répond avec l'état courant après une courte fenêtre.
        dirty.set(true)
        writeRequests.trySend(Unit)
    }

    /** L'écriture elle-même : l'état courant, sans les conversations du serveur. */
    private suspend fun writeFile() {
        // Les conversations **du serveur** ne sont pas persistées : elles ne sont qu'une
        // projection du registre du serveur, relue à chaque connexion. Les écrire ici les
        // transformerait en copies locales — et une conversation supprimée reviendrait.
        val snapshot = _conversations.value.filterNot { it.remote }
        withContext(Dispatchers.IO) { store.updateData { snapshot } }
        // Le fichier porte l'état lu **avant** l'écriture ; une demande arrivée entre-temps a remis
        // le drapeau à vrai et sera servie par la boucle de l'écrivain.
        dirty.set(false)
    }

    /**
     * Attend que le fichier soit à jour. Le rendu n'en a pas besoin ; un chemin qui **doit** savoir
     * que l'écriture est posée (avant de rendre la main au système) l'utilise.
     */
    suspend fun flush() {
        // Prendre le verrou, c'est attendre l'écrivain en cours. S'il reste une écriture en attente
        // (le fichier ne porte pas l'état courant), on l'écrit soi-même ; sinon on ne touche à rien —
        // un `flush` sur un état déjà écrit ne doit pas coûter une réécriture de tout le fichier.
        writeLock.withLock { if (dirty.get()) writeFile() }
    }
}
/**
 * La fenêtre pendant laquelle les demandes d'écriture se fondent en une.
 *
 * Assez courte pour qu'une fermeture de l'app juste après un envoi retrouve le tour écrit, assez
 * longue pour qu'une rafale de publications (un tour, dix étapes d'outil) ne coûte qu'une écriture.
 */
internal const val WRITE_SETTLE_MILLIS = 250L

/**
 * **Combien de temps les demandes d'écriture se fondent, selon ce qu'il y a à écrire.**
 *
 * Le magasin n'a qu'un fichier : une sauvegarde resérialise et réécrit **toutes** les
 * conversations. Son prix suit donc le nombre de messages, pas la taille du changement. À fenêtre
 * fixe, une grosse installation payait le même rythme qu'une petite — quatre écritures par seconde
 * pendant un tour qui lance soixante outils.
 *
 * La fenêtre part de [WRITE_SETTLE_MILLIS] et s'allonge de 4 ms par message, plafonnée à
 * [WRITE_SETTLE_CEILING_MILLIS] : sur un fichier de 300 messages (6,4 Mio, 33 ms d'écriture), deux
 * écritures par seconde au plus. Le tour finit par `flush()`, et la mise en arrière-plan aussi —
 * rien de ce qui doit être sur le disque n'attend cette fenêtre.
 */
internal fun writeSettleMillis(messageCount: Int): Long =
    (WRITE_SETTLE_MILLIS + messageCount.coerceAtLeast(0) * 4L).coerceAtMost(WRITE_SETTLE_CEILING_MILLIS)

/** Le plafond de la fenêtre : au-delà, on n'attend plus, on écrit. */
internal const val WRITE_SETTLE_CEILING_MILLIS = 1_200L

/** Le titre d'une branche : celui de son originale plus « (fork) », comme la référence. */
internal fun forkTitle(title: String): String = "$title${Conversation.ForkSuffix}"

/**
 * La conversation d'une **branche à partir d'ici** : la copie des messages jusqu'à [messageId]
 * **inclus**, sous un titre de branche, dans un fil neuf.
 *
 * Ce que la copie emporte, et ce qu'elle n'emporte pas — c'est ici que tout se décide :
 *
 *  - les **messages** tels qu'ils sont tenus en mémoire : texte, raisonnement, étapes d'outils avec
 *    leurs entrées et sorties, **pièces jointes** (leurs références aux fichiers de ce téléphone),
 *    cartes et mesures ;
 *  - **pas** l'**outbox** : un message encore non envoyé dans l'originale ne doit jamais partir tout
 *    seul depuis la branche. C'est ce qui garantit qu'un branchement ne déclenche aucun tour ;
 *  - **pas** l'identifiant de session serveur : la branche n'a pas de session chez le gateway, donc
 *    rien de ce qu'on y enverra plus tard ne s'écrira dans l'originale. Le serveur n'a aucune route
 *    pour bifurquer une session ; la copie vit sur ce téléphone, et c'est le **premier envoi** dans
 *    la branche qui ouvrira sa session (le fil copié part alors comme historique, comme toujours).
 *
 * Le titre suit la convention de « Créer un fil » ([forkTitle]) ; un titre encore au défaut prend
 * celui de la première question, sans quoi la branche d'une conversation jamais renommée
 * s'appellerait « New Conversation (fork) ».
 *
 * **Pure**, donc vérifiable sans stockage ni appareil ; `null` quand le message n'est pas dans
 * l'originale (auquel cas il n'y a rien à brancher).
 */
internal fun branchedConversation(
    existing: Conversation,
    id: String,
    messageId: String,
    now: Long,
): Conversation? {
    val index = existing.messages.indexOfFirst { it.id == messageId }
    if (index < 0) return null
    val titled = if (existing.title == Conversation.DefaultTitle) {
        existing.messages.firstOrNull { it.role == com.onyx.app.data.model.Role.USER }
            ?.let { existing.copy(title = Conversation.titleFrom(it.text)) }
            ?: existing
    } else {
        existing
    }
    return forkedConversation(titled, id, now).copy(
        messages = existing.messages.take(index + 1),
        outbox = emptyList(),
        serverSessionId = null,
    )
}

/**
 * La conversation d'une branche : la **copie** du transcript sous un nouveau titre, qui n'hérite
 * ni de l'épingle ni de l'archive de son originale (une branche neuve arrive dans les Chats, en
 * tête, jamais dans les archives). Pure, donc vérifiable sans stockage.
 */
internal fun forkedConversation(existing: Conversation, id: String, now: Long): Conversation =
    existing.copy(
        id = id,
        title = forkTitle(existing.title),
        createdAt = now,
        updatedAt = now,
        pinned = false,
        archived = false,
    )
