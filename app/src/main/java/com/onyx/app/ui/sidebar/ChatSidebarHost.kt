package com.onyx.app.ui.sidebar

import android.content.ClipData
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import com.onyx.app.data.model.Conversation
import com.onyx.app.data.model.serve.BotProfile
import com.onyx.app.data.local.savedCopyAtMs
import com.onyx.app.data.net.ActivityKind
import com.onyx.app.data.net.ServerProject
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.bots.BotCardMenu
import com.onyx.app.ui.bots.BotMenuHandlers
import com.onyx.app.ui.common.Mutes
import com.onyx.app.ui.common.muteMenuItems
import com.onyx.app.ui.conversations.ConversationActionTint
import com.onyx.app.ui.conversations.ConversationSheetRow
import com.onyx.app.ui.conversations.GroupChat
import com.onyx.app.ui.design.OnyxConfirm
import com.onyx.app.ui.design.Symbols
import kotlinx.coroutines.launch

/**
 * Le volet des conversations **branchement** : il traduit l'état de l'app (conversations, bots,
 * salons, projets, activité du serveur) en ce que [ChatSidebar] dessine, et rien de plus.
 *
 * Il tient aussi l'état **propre au volet** — la position Chats/Bots, la recherche, le filtre et le
 * projet choisi —, si bien que l'appelant n'a qu'à lui passer ses données et ses effets. C'est ce
 * qui garde la mise en page de l'app (`OnyxApp`) lisible, et le volet mesurable à part.
 */
@Composable
internal fun ChatSidebarHost(
    /** Le nom de la passerelle. */
    serverName: String,
    /** Son adresse, telle qu'elle est écrite dans les réglages. */
    serverAddress: String,
    /** `true` joignable, `false` non, `null` pas encore su. */
    reachable: Boolean?,
    agentName: String,
    /**
     * **L'identité d'une conversation**, par identifiant de serveur : le **nom de profil de son
     * bot**, la clé sur laquelle se résout la marque (`IdentityMark`). Absent, toutes les lignes
     * portent celle du bot principal — jamais une marque muette.
     */
    identityOf: (String) -> String = { agentName },
    conversations: List<Conversation>,
    /** L'activité que le serveur signale, par id de session (`session.active_list`). */
    liveKinds: Map<String, ActivityKind>,
    /** Ce que ce téléphone a vu de chaque conversation, en secondes. */
    seenMarks: Map<String, Double?>,
    /** Les bots du roster, cachés compris. */
    bots: List<BotProfile>,
    mainName: String,
    /** Les salles du serveur et celles qu'Hermes Desktop tient. */
    groups: List<GroupChat>,
    /** Ce que ce téléphone a vu de chaque salle, en secondes. */
    roomMarks: Map<String, Double?>,
    /** L'activité que le serveur signale dans une salle, par id de salle. */
    roomKinds: Map<String, ActivityKind>,
    /** Les projets du Dashboard. */
    projects: List<ServerProject>,
    /** Le visage de chaque bot, par nom de profil. */
    faceCodeOf: (String) -> String?,
    /** Les tuiles vers les écrans de l'app ; l'appelant les construit (voir `OnyxApp`). */
    tiles: List<SidebarTile>,
    onOpenTile: (String) -> Unit,
    /**
     * **La bibliothèque de la grille** : les destinations que l'app porte et que la grille ne montre
     * pas (voir `SidebarTileLayout.library`). Elles n'apparaissent qu'en mode édition.
     */
    tileLibrary: List<SidebarTile> = emptyList(),
    /**
     * **La grille vient de changer** : on rend toute la liste des identifiants, dans l'ordre. Absent,
     * la grille n'est pas personnalisable (le crayon et l'appui long n'existent pas).
     */
    onSetTileLayout: ((List<String>) -> Unit)? = null,
    onOpenSettings: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenConversation: (Conversation, Rect?) -> Unit,
    onNewConversation: () -> Unit,
    /** « Mark as Read » / « Mark as Unread » d'une rangée, comme le menu d'une conversation. */
    onSetConversationUnread: (Conversation, Boolean) -> Unit,
    /** « Unpin » / « Pin » d'une rangée. */
    onPinConversation: (Conversation, Boolean) -> Unit,
    /** « Archive » / « Désarchive » d'une rangée : l'état visé, si bien qu'un retour puisse le défaire. */
    onSetConversationArchived: (Conversation, Boolean) -> Unit,
    /** « Renommer » d'une rangée : le libellé définitif, déjà rogné. */
    onRenameConversation: (Conversation, String) -> Unit,
    /** « Créer un fil » d'une rangée : une branche locale (copie du transcript), ouverte à part. */
    onCreateThreadConversation: (Conversation) -> Unit,
    /** Le retour du bandeau d'annulation : une conversation supprimée est réenregistrée. */
    onRestoreConversation: (Conversation) -> Unit,
    /** « Delete » d'une rangée : la confirmation vit chez l'appelant. */
    onDeleteConversation: (Conversation) -> Unit,
    /** « Usage & cost » d'une rangée : la fiche d'usage de cette conversation. */
    onOpenUsageConversation: (Conversation) -> Unit,
    /** « Archiver N » de la barre de sélection. */
    onArchiveConversations: (Set<String>) -> Unit,
    /** « Supprimer N » : ici la confirmation est posée par le volet lui-même. */
    onDeleteConversations: (Set<String>) -> Unit,
    /** « Réessayer » : relit la liste des conversations et le roster (la carte de problème). */
    onRetry: () -> Unit,
    onOpenBot: (BotProfile) -> Unit,
    onNewBot: () -> Unit,
    onOpenRoom: (GroupChat) -> Unit,
    onNewRoom: (() -> Unit)?,
    onNewProject: (() -> Unit)?,
    /**
     * Le **menu d'une carte de bot** (les neuf entrées de la référence, puis celles de la maison) :
     * la table des gestes, fournie par l'appelant. Absente, l'appui long d'une carte n'ouvre rien.
     */
    botMenu: BotMenuHandlers? = null,
    /** Un mot sur ce qui vient d'échouer sur un bot (dupliquer, repartir à zéro…), ou `null`. */
    botNotice: String? = null,
    onDismissBotNotice: (() -> Unit)? = null,
    /** Ouvre les sessions d'un projet, au tap de sa puce. */
    onOpenProject: (ServerProject) -> Unit,
    /**
     * **L'écran que le volet porte** (une tuile ouverte dans le volet), ou `null` pour sa liste.
     * Voir [ChatSidebar.destination] — l'appelant le tient de son état de navigation
     * (`com.onyx.app.ui.sidebar.OnyxSidebarState.destination`).
     */
    destination: String? = null,
    /** Le contenu de [destination], construit par l'appelant. */
    destinationContent: (@Composable () -> Unit)? = null,
    /**
     * Le retour **dans le volet** : dépile un niveau. Appelé par le retour système tant que le
     * volet porte un écran ([destination]) — c'est ce qui fait qu'un retour matériel revient à la
     * liste **avant** de refermer le volet, un niveau à la fois.
     */
    onPaneBack: () -> Unit = {},
    /**
     * L'état du volet (n° 7) — c'est lui qui porte le **mode recherche**, donc aussi le retour : le
     * geste de bord quitte la recherche avant de refermer le volet.
     */
    sidebarState: OnyxSidebarState,
    modifier: Modifier = Modifier,
) {
    // Le volet porte un écran : le retour système lui appartient, et il **dépile** — jamais il ne
    // saute une marche. Posé ici (dans le volet lui-même), ce gestionnaire passe après ceux des
    // routes : tant qu'un écran est ouvert dans le volet, c'est lui qui répond.
    BackHandler(enabled = destination != null) { onPaneBack() }
    var tabName by rememberSaveable { mutableStateOf(SidebarTab.CHATS.name) }
    var query by rememberSaveable { mutableStateOf("") }
    var filterName by rememberSaveable { mutableStateOf(SidebarFilter.ALL.name) }
    var projectId by rememberSaveable { mutableStateOf<String?>(null) }

    // **Le mode édition de la grille de raccourcis.** Il vit ici, avec les autres états du volet (la
    // position, la recherche, le filtre), et il est **mémorisé** : un repliage ne le perd pas.
    var editingTiles by rememberSaveable { mutableStateOf(false) }

    // La multi-sélection de la liste : son état vit ici (le volet est son écran), ses deux lots
    // passent par les actions de l'appelant. La suppression demande confirmation, comme l'écran
    // qu'elle remplace — et la sélection reste tant qu'on n'a pas répondu.
    val selection = remember { SidebarSelectionState() }
    var pendingDelete by remember { mutableStateOf<Set<String>?>(null) }
    // Le bot dont le menu est ouvert (son appui long), la carte touchée pour l'ancrer, et celui dont
    // on demande la **suppression** — le menu ne supprime jamais de lui-même.
    var menuBot by remember { mutableStateOf<Pair<BotProfile, Rect>?>(null) }
    var askDeleteBot by remember { mutableStateOf<BotProfile?>(null) }

    // Le silence et le presse-papiers sont des gestes de **l'appareil** : le volet les tient ici,
    // comme l'écran des conversations les tenait avant lui.
    val context = LocalContext.current
    val mutes = remember { Mutes(context) }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    val tab = SidebarTab.valueOf(tabName)
    val filter = SidebarFilter.valueOf(filterName)
    val searching = sidebarState.searching

    // La recherche remplace la grille : on **sort** de l'édition en y entrant, sinon on reviendrait à
    // une grille restée en cours de composition sous un écran qui n'en montrait rien.
    LaunchedEffect(searching) {
        if (searching) editingTiles = false
    }

    /**
     * **Le mode édition de la grille**, tel qu'il se dessine : le crayon entre, « Terminé » sort, et
     * chaque geste rend la **grille entière** dans son ordre — c'est l'appelant qui l'enregistre.
     */
    val tileEditor = onSetTileLayout?.let { setLayout ->
        SidebarTileEditor(
            active = editingTiles,
            library = tileLibrary,
            onSetOrder = setLayout,
            onEnter = { editingTiles = true },
            onExit = { editingTiles = false },
        )
    }

    /**
     * La requête **appliquée**. La frappe, elle, n'y touche qu'une fois posée : à chaque caractère
     * l'effet est relancé et l'attente recommence ([debouncedSearchQuery]), donc une rafale de frappe
     * ne construit **qu'une** liste.
     */
    var settledQuery by remember { mutableStateOf("") }
    LaunchedEffect(query, searching) {
        if (!searching) {
            settledQuery = ""
            return@LaunchedEffect
        }
        debouncedSearchQuery(query) { settledQuery = it }
    }
    // La requête ne vaut **que** pour la recherche : la liste des conversations n'est jamais filtrée
    // par un texte qu'on a quitté sans l'effacer.
    val activeQuery = if (searching) settledQuery else ""

    /**
     * **Sortir de la recherche** : le mode d'abord, la requête ensuite.
     *
     * Décision, et elle est visible : sortir de la recherche **efface la requête**. Sans cela, la
     * liste reviendrait filtrée par un texte que plus rien n'affiche — le défaut d'origine —, ou
     * reviendrait avec une requête fantôme qu'on retrouverait en rentrant. Les filtres et le projet
     * choisi, eux, ne bougent pas : la liste se retrouve là où elle était.
     */
    val exitSearch = {
        sidebarState.exitSearch()
        query = ""
        settledQuery = ""
    }
    // **Le retour quitte la recherche avant de refermer le volet.** Ce gestionnaire est composé
    // *dans* le volet, donc après celui de l'enveloppe ([com.onyx.app.ui.sidebar.SidebarScaffold]) :
    // tant que la recherche est ouverte, c'est lui qui prend le geste ; une fois la recherche
    // quittée, le geste suivant referme le volet. Il n'est armé que si le volet est **à l'écran** —
    // un geste qui ne se voit pas serait un geste avalé.
    BackHandler(enabled = searching && sidebarState.isOpen) { exitSearch() }

    // L'horloge n'est relue que quand les données changent : les âges du volet ne font pas
    // recomposer la liste chaque seconde pour un « 12m » qui passe à « 13m ».
    val now = remember(conversations, bots, groups) { System.currentTimeMillis() / 1000.0 }

    // **La copie enregistrée.** Hors ligne, le volet dit depuis quand il l'a : la date de **dernière
    // écriture du fichier** des conversations, relue quand la joignabilité change (une seule lecture
    // de fichier par changement, jamais par recomposition). Tant que la passerelle répond, `null` —
    // la ligne d'état n'existe pas, et rien n'est mesuré.
    val savedCopyAgeSeconds = remember(reachable) {
        if (reachable != false) {
            null
        } else {
            savedCopyAtMs(context)?.let { at -> (System.currentTimeMillis() - at) / 1000.0 }
        }
    }

    // La recherche regarde les messages aussi : c'est ce que fait la liste de l'app, et ce qu'on
    // attend d'un champ « Search chats ». C'est **exactement** ce que l'état vide annonce
    // (`SearchScopeSentence`) : les titres, et le texte des messages.
    val rows = remember(conversations, liveKinds, seenMarks, activeQuery, filter, now, identityOf, faceCodeOf) {
        sidebarRows(
            conversations = conversations,
            liveKinds = liveKinds,
            seenMarks = seenMarks,
            now = now,
            query = activeQuery.trim(),
            includeMessages = true,
            filter = filter,
            agentName = agentName,
            // La marque d'identité d'une ligne se résout sur le **nom de profil de son bot**, la
            // même clé que la liste des bots, la fiche, l'en-tête et le volet (`BotLook`).
            identityOf = identityOf,
            faceCodeOf = faceCodeOf,
        )
    }
    val allTones = remember(conversations, liveKinds, seenMarks) {
        conversations.map { sidebarToneOf(it, it.serverSessionId?.let { id -> liveKinds[id] }, seenMarks[it.id]) }
    }
    val counts = remember(allTones) { sidebarFilterCounts(allTones) }

    val chips = remember(projects) { sidebarProjectChips(projects) }

    val botCards = remember(bots, mainName, liveKinds, seenMarks, now) {
        bots.map { bot ->
            val kind = bot.chatId?.let { liveKinds[it] }
            SidebarBotCard(
                name = bot.name,
                label = bot.label(mainName),
                // **Le sous-titre de la référence** : ce que le bot vient de dire (l'aperçu de son
                // chat), sinon sa phrase, sinon « Say hello » — la carte ne reste jamais vide sous
                // son nom (`BotsRoster.kt:426-437`). Le texte est déjà traduit ici : la carte le
                // dessine verbatim, comme une phrase du serveur.
                description = bot.preview?.takeIf { it.isNotBlank() }
                    ?: bot.description.takeIf { it.isNotBlank() }
                    ?: OnyxLocalization["Say hello"],
                // Sans activité connue, **pas d'âge** : une date absente ne se dit pas « 2964w »
                // (le zéro d'époque comparé à l'horloge). Le bot montre alors son nom et sa ligne.
                ageLabel = bot.lastActive
                    ?.let { last -> sidebarAgeLabel(now - last / 1000.0) }
                    ?.takeIf { it.isNotEmpty() },
                tone = when (kind) {
                    ActivityKind.WORKING -> SidebarRowTone.RUNNING
                    ActivityKind.NEEDS_YOU -> SidebarRowTone.WAITING
                    else -> SidebarRowTone.IDLE
                },
                // « Marquée non lue à la main » : la marque à zéro. Une absence de marque n'est pas
                // un non-lu — un bot qu'on n'a jamais ouvert n'a rien annoncé.
                unread = seenMarks["bot:${bot.name}"]?.let { it <= 0.0 } == true,
                // « Pin to top » : le bot marque sa carte (l'icône devant son nom) et passe en tête
                // de la section — c'est `BotListItem.arranged` qui le met là.
                pinned = bot.pinned,
            )
        }
    }
    val waitingBots = botCards.count { it.tone == SidebarRowTone.WAITING }

    val roomRows = remember(groups, roomMarks, roomKinds, now) {
        sidebarRoomRows(
            rooms = groups.map { room ->
                val last = room.updatedAt ?: 0L
                SidebarRoomSource(
                    id = room.id,
                    name = room.name,
                    // Le texte de la ligne garde les **libellés** (« Onyx, Scout ») ; le visage se
                    // résout sur les **noms de profil** (`it.name`), sinon le membre principal
                    // portait le soleil ambre de son libellé au lieu de la bulle bleue de son profil.
                    members = room.members.map { it.label(mainName) },
                    memberProfiles = room.members.map { it.name },
                    updatedAt = last / 1000.0,
                    ageSeconds = now - last / 1000.0,
                )
            },
            unreadRoomIds = groups.mapNotNull { room ->
                room.id.takeIf { roomMarks["room:${room.id}"]?.let { mark -> mark <= 0.0 } == true }
            }.toSet(),
        )
    }

    ChatSidebar(
        gatewayName = serverName,
        gatewayAddress = serverAddress,
        reachable = reachable,
        onOpenSettings = onOpenSettings,
        searchQuery = query,
        onSearchQuery = { query = it },
        searching = searching,
        onEnterSearch = { sidebarState.enterSearch() },
        onExitSearch = exitSearch,
        tab = tab,
        onTab = { tabName = it.name },
        botsWaiting = waitingBots,
        tiles = tiles,
        onOpenTile = { tile -> onOpenTile(tile.id) },
        tileEditor = tileEditor,
        projects = chips,
        selectedProjectId = projectId,
        onSelectProject = { id ->
            projectId = id
            if (id != null && id != ALL_PROJECTS_ID) {
                projects.firstOrNull { it.id == id }?.let(onOpenProject)
            }
        },
        onNewProject = onNewProject,
        filter = filter,
        onFilter = { filterName = it.name },
        filterCounts = counts,
        rows = rows,
        onOpenRow = { row, rect ->
            conversations.firstOrNull { it.id == row.id }?.let { conversation -> onOpenConversation(conversation, rect) }
        },
        selection = selection,
        onArchiveSelected = { ids ->
            selection.cancel()
            onArchiveConversations(ids)
        },
        onDeleteSelected = { ids -> pendingDelete = ids },
        rowActions = { row ->
            conversations.firstOrNull { it.id == row.id }?.let { conversation ->
                val seen = seenMarks[conversation.id]
                SidebarRowActions(
                    conversation = conversation,
                    // « Non lue » : la dernière réponse de l'agent n'a pas encore été vue, ou la
                    // conversation a été marquée non lue **à la main** (la marque à zéro).
                    unread = sidebarHasUnreadReply(conversation, seen) || seen?.let { it <= 0.0 } == true,
                    muted = mutes.isMuted(conversation.id),
                    onMarkRead = { onSetConversationUnread(conversation, false) },
                    onMarkUnread = { onSetConversationUnread(conversation, true) },
                    onTogglePin = { pinned -> onPinConversation(conversation, pinned) },
                    onToggleArchive = { archived -> onSetConversationArchived(conversation, archived) },
                    onRename = { title -> onRenameConversation(conversation, title) },
                    onCreateThread = { onCreateThreadConversation(conversation) },
                    onOpenUsage = { onOpenUsageConversation(conversation) },
                    // « Copier le titre » : le titre de la conversation dans le presse-papiers.
                    onCopyTitle = {
                        scope.launch {
                            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Onyx", conversation.title)))
                        }
                    },
                    // « Ouvrir ailleurs » : sur ce téléphone, le pendant de la fenêtre séparée est
                    // d'ouvrir la conversation dans le fil, sans la quitter d'ici.
                    onOpenElsewhere = { onOpenConversation(conversation, null) },
                    onDelete = { onDeleteConversation(conversation) },
                    onRestore = { onRestoreConversation(conversation) },
                    // Le silence : « Mute », « Mute for 1 hour »… — les lignes du menu maison.
                    muteRows = muteMenuItems(mutes, conversation.id).map { item ->
                        ConversationSheetRow(
                            label = item.label,
                            symbol = item.symbol ?: Symbols.Ellipsis,
                            tint = ConversationActionTint.MUTE,
                            localize = item.localize,
                        ) { item.onSelect() }
                    },
                )
            }
        },
        onNewConversation = onNewConversation,
        onOpenAccount = onOpenAccount,
        bots = botCards,
        onOpenBot = { card -> bots.firstOrNull { it.name == card.name }?.let(onOpenBot) },
        onNewBot = onNewBot,
        // L'appui long d'une carte : le bot visé et la carte touchée (le panneau s'y ancre).
        onMenuBot = botMenu?.let { table ->
            { card: SidebarBotCard, rect: Rect ->
                bots.firstOrNull { it.name == card.name }?.let { bot -> menuBot = bot to rect }
            }
        },
        botNotice = botNotice,
        onDismissBotNotice = onDismissBotNotice,
        rooms = roomRows,
        onOpenRoom = { row -> groups.firstOrNull { it.id == row.id }?.let(onOpenRoom) },
        onNewRoom = onNewRoom,
        faceCodeOf = faceCodeOf,
        onRetry = onRetry,
        savedCopyAgeSeconds = savedCopyAgeSeconds,
        destination = destination,
        destinationContent = destinationContent,
        modifier = modifier,
    )
    // **Le menu d'une carte de bot** : les neuf entrées de la référence dans son ordre, puis celles
    // de la maison, la table venant de l'appelant (une seule liste de gestes). Le panneau s'ouvre
    // contre la carte touchée.
    menuBot?.let { (bot, bounds) ->
        botMenu?.let { table ->
            BotCardMenu(
                bot = bot,
                // Le volet ne sait pas quelle conversation est ouverte **dans le fil** (il ne la
                // garde pas) : les deux entrées de lecture s'en tiennent donc au non-lu de la carte.
                isOpen = false,
                unread = seenMarks["bot:${bot.name}"]?.let { it <= 0.0 } == true,
                mutes = mutes,
                // « Reorder Bots » et « Sort Bots by Recent » vivent sur la page « Bots », où la
                // mise en ordre a son écran : la table du volet ne les porte pas.
                order = emptyList(),
                mainName = mainName,
                anchor = bounds,
                handlers = table,
                onDismiss = { menuBot = null },
                // « Supprimer » demande confirmation : c'est ici, et pas dans le menu.
                onAskDelete = table.onDelete?.let { act -> { target -> askDeleteBot = target } },
            )
        }
    }

    // La confirmation de « Supprimer » : le bot part du **serveur**, les conversations de ce
    // téléphone restent dans l'historique — la règle de la maison, dite à l'écran. La confirmation
    // est une **surface de la maison** ([OnyxConfirm]), pas une boîte du système.
    askDeleteBot?.let { bot ->
        OnyxConfirm(
            title = OnyxLocalization.format("Delete %1\$s?", bot.label(mainName)),
            message = "This removes the bot from your server. Its conversations stay in your history on this phone.",
            destructiveLabel = "Delete",
            cancelLabel = "Cancel",
            onDestructive = {
                val act = botMenu?.onDelete
                askDeleteBot = null
                act?.invoke(bot)
            },
            onCancel = { askDeleteBot = null },
        )
    }

    // La suppression groupée demande confirmation, comme le `confirmationDialog` qu'elle remplace :
    // la sélection reste tant qu'on n'a pas répondu. Même surface de la maison.
    pendingDelete?.let { lot ->
        val count = lot.size
        OnyxConfirm(
            title = "Delete $count conversation${if (count == 1) "" else "s"}?",
            message = "They leave this phone and your server. This can't be undone.",
            destructiveLabel = "Delete",
            cancelLabel = "Cancel",
            onDestructive = {
                pendingDelete = null
                selection.cancel()
                onDeleteConversations(lot)
            },
            onCancel = { pendingDelete = null },
        )
    }
}

/** Le libellé du sélecteur, avec le compte de ce qui attend — comme le volet de la référence. */
internal fun sidebarTabLabel(tab: SidebarTab, waitingBots: Int): String =
    if (tab == SidebarTab.BOTS && waitingBots > 0) "${tab.label} · $waitingBots" else tab.label
