package com.onyx.app.ui.conversations

import android.content.ClipData
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.onyx.app.ui.localization.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onyx.app.ui.design.glassSurface
import com.onyx.app.data.model.Conversation
import com.onyx.app.data.model.Role
import com.onyx.app.data.model.serve.BotListItem
import com.onyx.app.data.model.serve.BotProfile
import com.onyx.app.data.net.ServerProject
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.localization.resolveOnyxText
import com.onyx.app.ui.bots.BotsArrangeList
import com.onyx.app.ui.bots.BotBadges
import com.onyx.app.ui.bots.BotTileMenu
import com.onyx.app.ui.bots.CLOCK_SLACK
import com.onyx.app.ui.bots.RoomMenu
import com.onyx.app.ui.bots.roomMembersLabel
import com.onyx.app.ui.design.AlertAction
import com.onyx.app.ui.design.BotMood
import com.onyx.app.ui.design.OnyxAlert
import com.onyx.app.ui.design.OnyxFormSection
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxRow
import com.onyx.app.ui.design.OnyxRowDivider
import com.onyx.app.ui.design.OnyxSegmented
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.design.onyxTappable
import com.onyx.app.ui.theme.LocalOnyxTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** Une ligne du pied de liste: son titre, son icône et ce qu'elle fait. */
data class FooterAction(val title: String, val symbol: Int, val onClick: () -> Unit)

/** Les trois sections de la liste, comme le `Picker`. */
enum class ListSection(val title: String) {
    CHATS("Chats"),
    CRON("Cron"),
    KANBAN("Kanban"),
}

/**
 * Un groupe de bots tel que le serveur le connaît (comme `GroupRoom`).
 *
 * Ses membres sont des [BotProfile] déjà chargés par l'écran: la rangée d'un groupe ne demande
 * donc rien de plus au serveur pour dessiner les visages.
 */
data class GroupChat(
    val id: String,
    val name: String,
    val members: List<BotProfile> = emptyList(),
    val updatedAt: Long? = null,
    val lastMessage: String? = null,
    /**
     * Combien de membres sont en train de répondre dans la salle, en ce moment.
     *
     * C'est le `answering` de `GroupFeed`: tant qu'il reste des réponses,
     * la rangée le dit (« N answering ») à la place de la date, comme y met le rond qui
     * tourne à la place de l'heure. Zéro, rien ne répond.
     */
    val answering: Int = 0,
    /**
     * Où la salle en est (`GroupRoom.activityMark`): le numéro de son dernier événement, ou
     * l'heure de la copie d'une salle qu'Hermes Desktop tient. Sert au badge « pas encore lu ».
     */
    val activityMark: Double? = null,
    /** Une salle qu'Hermes Desktop tient lui-même: la rangée en porte la marque. */
    val inDesktop: Boolean = false,
    /**
     * Une salle que ce téléphone garde lui-même, faute de Dashboard pour la tenir: elle s'ouvre
     * sur son fil local, et non sur le journal d'une salle du serveur.
     */
    val local: Boolean = false,
)

/**
 * Ce que devient un « New Group Chat »: la salle faite — sur le serveur, ou gardée ici —, ou ce
 * qui l'a empêchée. Un échec n'est jamais un succès muet: l'écran le montre et rien n'est gardé.
 */
sealed interface GroupCreation {
    /** La salle est là: sur le serveur, ou dans le magasin des salles gardées ici. */
    data class Made(val room: GroupChat) : GroupCreation

    /** La salle n'a pas été faite: ce qui l'a empêchée, prêt à être montré tel quel. */
    data class Failed(val problem: String) : GroupCreation
}

/**
 * L'écran des conversations.
 *
 * Comme: le grand titre, la barre à quatre boutons (réglages,
 * titre, sélection, écriture), le champ « Search conversations », les segments Chats / Cron /
 * Kanban, la section des bots avec leurs orbes, et les lignes qui font agir (nouveau groupe,
 * nouveau bot, laisser les bots se parler).
 *
 * Les paramètres de la fin (groupes, chargement, erreur, pied de liste) sont optionnels: l'écran
 * se dessine sans eux comme avant, et l'appelant les renseigne quand il a la donnée.
 */
@Composable
fun ConversationsScreen(
    conversations: List<Conversation>,
    bots: List<BotProfile>,
    currentId: String?,
    onOpen: (Conversation, Rect?) -> Unit,
    onDelete: (Conversation) -> Unit,
    onPin: (Conversation, Boolean) -> Unit = { _, _ -> },
    onArchive: (Conversation, Boolean) -> Unit = { _, _ -> },
    onRename: ((Conversation, String) -> Unit)? = null,
    onBulkDelete: (Set<String>) -> Unit = {},
    onBulkArchive: (Set<String>) -> Unit = {},
    onBulkPin: (Set<String>) -> Unit = {},
    onNew: () -> Unit,
    /** Le tap d'une tuile de bot: son **chat** (`openBot`), jamais son éditeur. */
    onOpenBotChat: (BotProfile) -> Unit,
    /** « Edit Bot… » du menu d'une tuile: l'éditeur du bot. */
    onEditBot: ((BotProfile) -> Unit)? = null,
    /** « Change Face… » du menu d'une tuile; sans lui, l'entrée n'apparaît pas. */
    onChangeFace: ((BotProfile) -> Unit)? = null,
    /** « Schedules… » du menu d'une tuile; sans lui, l'entrée n'apparaît pas. */
    onBotSchedules: ((BotProfile) -> Unit)? = null,
    /** « Voice… » du menu d'une tuile (`BotVoicePicker`); sans lui, l'entrée n'apparaît pas. */
    onBotVoice: ((BotProfile) -> Unit)? = null,
    /** « Hide » du menu d'une tuile. */
    onHideBot: ((BotProfile) -> Unit)? = null,
    /** « Remove from Onyx » du menu d'une tuile, sur un bot ajouté à la main. */
    onRemoveAPIBot: ((BotProfile) -> Unit)? = null,
    /** « Mark as Unread » du menu d'une tuile. */
    onMarkBotUnread: ((BotProfile) -> Unit)? = null,
    /** « Mark as Read » du menu d'une tuile. */
    onMarkBotSeen: ((BotProfile) -> Unit)? = null,
    /** « Mark as Unread » du menu d'une salle. */
    onMarkRoomUnread: ((GroupChat) -> Unit)? = null,
    /**
     * Les marques de lecture des conversations, par identifiant: `0.0` pour une conversation
     * marquée non lue à la main, sinon l'heure jusqu'à laquelle elle a été vue. Absente, la rangée
     * garde son non-lu d'après le fil (le dernier mot est celui de l'agent).
     */
    unreadMarks: Map<String, Double?> = emptyMap(),
    /** « Mark as Read » / « Mark as Unread » d'une conversation, comme le `contextMenu`. */
    onSetUnread: ((Conversation, Boolean) -> Unit)? = null,
    onNewBot: () -> Unit,
    /**
     * « New Group Chat »: la salle est faite ici — sur le serveur quand la connexion a des
     * identifiants de Dashboard, gardée sur ce téléphone sinon. Rend la salle faite, ou l'échec,
     * que la feuille montre en restant ouverte.
     */
    onNewGroupChat: suspend (Set<String>, String) -> GroupCreation,
    onLetBotsMessage: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCron: () -> Unit,
    onOpenKanban: () -> Unit,
    onDone: () -> Unit,
    /** Les groupes de bots du serveur (Hermes Bot Mode), sous les lignes de bots. */
    groups: List<GroupChat> = emptyList(),
    /** Ouvre un groupe; sans lui, la rangée reste visible mais inerte. */
    onOpenGroup: ((GroupChat) -> Unit)? = null,
    /** Les sources des pastilles de la section « Bots »: l'activité que le Dashboard signale
     * (`session.active_list`), et ce que ce téléphone a vu de chaque ligne. */
    badges: BotBadges = BotBadges(),
    /** Le nom du profil principal, comme `Settings.headerTitle`: le nom du bot « default ». */
    mainName: String = "Onyx",
    /** Les lignes de la section dans l'ordre mis à la main (« Reorder Bots »). */
    arranged: List<BotListItem> = emptyList(),
    /** L'ordre enregistré; non vide, « Sort Bots by Recent » apparaît dans la section. */
    botOrder: List<String> = emptyList(),
    /** Combien de bots sont cachés: la tuile « Hidden » de la grille n'apparaît qu'au-delà de zéro. */
    hiddenBotCount: Int = 0,
    /** Ouvre la liste des bots cachés (`HiddenBotsView`). */
    onOpenHiddenBots: (() -> Unit)? = null,
    /** Une ligne glissée pendant « Reorder Bots »: `(de, vers)`. */
    onMoveRow: ((Int, Int) -> Unit)? = null,
    /** « Sort Bots by Recent », quand un ordre a été mis à la main. */
    onSortBotsByRecent: (() -> Unit)? = null,
    /** Une salle ouverte: elle est vue jusqu'à son dernier événement. */
    onRoomSeen: ((GroupChat) -> Unit)? = null,
    /** Le serveur répond: la liste attend. */
    loading: Boolean = false,
    /** Ce qui a empêché la liste de se charger (`ledgerError` ). */
    error: String? = null,
    /** Réessaie le chargement, appuyé depuis la carte de problème. */
    onRetry: () -> Unit = {},
    /** Les lignes de pied de liste (`footer` d'une `Section` ), petites et grises. */
    footers: List<String> = emptyList(),
    /** Ouvre Réglages depuis le pied de liste, sans passer par le bouton de la barre. */
    onQuickSettings: (() -> Unit)? = null,
    /** Importe ou exporte les transcriptions, depuis le pied de liste. */
    onImportExport: (() -> Unit)? = null,
    /** « Open in New Window »: rouvre la conversation ailleurs sans la quitter ici. */
    onOpenInNewWindow: ((Conversation) -> Unit)? = null,
    /** « Usage & cost » du menu d'une rangée: la fiche d'usage de cette conversation. */
    onOpenUsage: ((Conversation) -> Unit)? = null,
    /** Les projets du serveur (dossiers de travail), quand le Dashboard est joignable. */
    serverProjects: List<ServerProject> = emptyList(),
    /** Ouvre les sessions d'un projet, comme le `NavigationLink` du `projectsSection`. */
    onOpenProject: ((ServerProject) -> Unit)? = null,
    /** D'autres lignes de pied de liste à icône, en plus des trois habituelles. */
    footerActions: List<FooterAction> = emptyList(),
    /** téléphone / écran étendu: la liste reste visible et les segments pilotent le panneau de détail. */
    wideDetail: Boolean = false,
    /** La route visible pilote le segment sur grand écran, notamment après une rotation. */
    selectedSection: ListSection? = null,
    /** Retour au chat depuis le sélecteur de section en mode deux panneaux. */
    onOpenChats: (() -> Unit)? = null,
    /** Les serveurs enregistrés: le menu « tous les serveurs » apparaît dès qu'il y en a deux. */
    servers: List<ServerTag> = emptyList(),
    /** Le serveur où l'app est (ses conversations ne portent jamais d'étiquette). */
    activeServerId: String = "",
    /** Bascule sur un serveur, depuis le menu (`ServerSwitcher` ). */
    onSwitchServer: ((String) -> Unit)? = null,
    /** Ouvre la conversation d'un autre serveur: Onyx bascule et l'ouvre là-bas. */
    onOpenRemote: ((AllServerRow) -> Unit)? = null,
    /** Les serveurs qui n'ont pas répondu, nommés au-dessus de la liste (`serversUnreachable`). */
    unreachable: List<ServerTag> = emptyList(),
    modifier: Modifier = Modifier,
    /**
     * Le contrôle du volet (`sidebar.left`) quand cette liste **est** le volet des deux panneaux:
     * la barre le porte à la place d'un retour, avant le bouton des Réglages.
     */
    sidebarControl: (@Composable () -> Unit)? = null,
) {
    val theme = LocalOnyxTheme.current
    var section by rememberSaveable { mutableStateOf(ListSection.CHATS.name) }
    var query by rememberSaveable { mutableStateOf("") }
    // Le tri et la portée de la recherche, comme le menu « … » de la liste.
    var sort by rememberSaveable { mutableStateOf(Sort.RECENT.name) }
    var searchScope by rememberSaveable { mutableStateOf(SearchScope.MESSAGES.name) }
    var infoShown by remember { mutableStateOf<Conversation?>(null) }
    var creatingGroup by remember { mutableStateOf(false) }
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    // La suppression groupée demande confirmation, comme `confirmBulkDelete`.
    var confirmBulkDelete by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val mutes = remember { com.onyx.app.ui.common.Mutes(context) }
    var muting by remember { mutableStateOf<Conversation?>(null) }
    var showArchived by remember { mutableStateOf(false) }
    // Une conversation dont le titre se change à la main (« Rename session » ).
    var renaming by remember { mutableStateOf<Conversation?>(null) }
    var renameText by remember { mutableStateOf("") }
    // Les sections repliables: les lignes de bots, et les projets.
    var botsOpen by rememberSaveable { mutableStateOf(true) }
    var projectsOpen by rememberSaveable { mutableStateOf(true) }
    // « Reorder Bots »: la section ne montre que ses lignes, avec leurs poignées, et n'ouvre rien.
    var arrangingBots by remember { mutableStateOf(false) }
    // La seule rangée dont les actions de balayage sont ouvertes, comme le `swipeActions`:
    // une par écran. Elle se referme au tap à côté, au défilement, au changement de géométrie et
    // en quittant la liste — jamais ne reste peinte.
    var openRow by remember { mutableStateOf<String?>(null) }
    val listScroll = rememberScrollState()
    LaunchedEffect(listScroll.isScrollInProgress) {
        if (listScroll.isScrollInProgress) openRow = null
    }
    // Une rotation ou un dépliage ne laisse pas un volet ouvert sur un écran qui a changé de forme.
    val configuration = LocalConfiguration.current
    LaunchedEffect(configuration.screenWidthDp, configuration.screenHeightDp, configuration.fontScale) {
        openRow = null
    }
    // Pinned et Chats se replient aussi, comme tout en-tête de section.
    var pinnedOpen by rememberSaveable { mutableStateOf(true) }
    var chatsOpen by rememberSaveable { mutableStateOf(true) }
    // « All Servers » (`AllServers`): le menu du serveur, au-dessus de la liste, choisit entre
    // tous les serveurs et un seul. Le serveur où l'app est reste celui où les conversations
    // neuves commencent — le menu le dit.
    var allServers by rememberSaveable { mutableStateOf(false) }
    var serverMenuOpen by remember { mutableStateOf(false) }
    // « Copy title »: le presse-papiers de la plateforme, comme le reste de l'app.
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    val needle = query.trim()
    val scopeNow = SearchScope.valueOf(searchScope)
    val presentedSection = selectedSection ?: ListSection.valueOf(section)
    // L'écran change (une conversation s'ouvre, un segment passe au Cron): volet refermé.
    LaunchedEffect(currentId, presentedSection) { openRow = null }
    // Le serveur de chaque conversation: celui avec qui elle a été écrite, ou celui où l'app est
    // quand elle est plus ancienne que les serveurs multiples ([Conversation.serverId] vide).
    val serverOf: (Conversation) -> String = { conversation -> conversation.serverId.ifBlank { activeServerId } }
    // Le filtre de la recherche, partagé par les nôtres et celles des autres serveurs.
    val matches: (Conversation) -> Boolean = { conversation ->
        if (needle.isEmpty()) {
            true
        } else {
            val inTitle = conversation.title.contains(needle, ignoreCase = true)
            val inMessages = conversation.messages.any { it.text.contains(needle, ignoreCase = true) }
            when (scopeNow) {
                SearchScope.TITLES -> inTitle
                SearchScope.MESSAGES -> inMessages
                SearchScope.BOTH -> inTitle || inMessages
            }
        }
    }
    // Le serveur où l'app est: ses conversations s'affichent, et seulement les siennes. Les autres
    // serveurs ont les leurs, montrées à part quand « All Servers » est allumé (`AllServers`).
    val own = conversations.filter { it.serverId.isBlank() || it.serverId == activeServerId }
    val visible = when (Sort.valueOf(sort)) {
        Sort.RECENT -> own.filter(matches).sortedByDescending { it.updatedAt }
        Sort.OLDEST -> own.filter(matches).sortedBy { it.updatedAt }
        Sort.TITLE -> own.filter(matches).sortedBy { it.title.lowercase() }
    }
    // « All Servers »: chaque autre serveur enregistré, ses conversations étiquetées du sien,
    // réunies aux nôtres et triées de la plus récente à la plus ancienne — le tri.
    val allServersOn = allServers && servers.size > 1
    val unreachableIds = unreachable.map { it.id }.toSet()
    val remoteRows: List<AllServerRow> = if (!allServersOn) {
        emptyList()
    } else {
        AllServers.merge(
            own = emptyList(),
            ownServerId = activeServerId,
            // Un serveur qui n'a pas répondu ne prête pas ses conversations: il est nommé
            // au-dessus de la liste au lieu de laisser un vide (`serversUnreachable` ).
            others = AllServers.others(conversations, activeServerId, servers)
                .filterNot { (tag, _) -> tag.id in unreachableIds }
                .map { (tag, list) -> tag to list.filter(matches) },
        )
    }
    // La clé composite de la conversation ouverte: deux serveurs peuvent nommer deux
    // conversations de la même façon, un seul identifiant ne suffit donc pas à les distinguer.
    val openKey = currentId?.let { AllServers.key(activeServerId, it) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.pageColor)
            // Sur grand écran la colonne reste active pendant que le clavier est ouvert: ses
            // actions basses et la dernière rangée ne passent ni sous l'IME ni sous le geste Android.
            .then(if (wideDetail) Modifier.imePadding().navigationBarsPadding() else Modifier),
    ) {
        // La barre de 03-bots.png: le gear dans un grand cercle, le titre à gauche, et la
        // capsule de verre qui porte la sélection et l'écriture.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // « Reorder Bots » prend la barre: n'y laisse que « Done » (le bouton Réglages part).
            if (!arrangingBots) {
                sidebarControl?.let {
                    it()
                    Spacer(Modifier.size(8.dp))
                }
                BigCircleButton(
                    symbol = Symbols.GearshapeSingle,
                    label = "Settings",
                    onClick = onOpenSettings,
                )
            }
            BasicText(
                text = when {
                    selecting -> "${selected.size} selected"
                    wideDetail -> "Chats"
                    else -> "Conversations"
                },
                style = androidx.compose.ui.text.TextStyle(
                    color = theme.textColor,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 14.dp),
            )
            if (arrangingBots) {
                BasicText(
                    text = "Done",
                    style = theme.body.copy(color = theme.accent, fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.onyxClickable { arrangingBots = false },
                )
            } else if (!selecting) {
                // Les deux actions dans une seule capsule, comme sur la capture.
                Row(
                    modifier = Modifier
                        .glassSurface(RoundedCornerShape(50))
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    CapsuleAction(Symbols.CheckmarkCircleOutline, "Select") { selecting = true }
                    CapsuleAction(Symbols.SquareAndPencil, "New conversation") { onNew() }
                }
            } else {
                BasicText(
                    text = "Cancel",
                    style = theme.body.copy(color = theme.accent),
                    modifier = Modifier.onyxClickable {
                        selecting = false
                        selected = emptySet()
                    },
                )
            }
        }

        // « Reorder Bots »: le champ de recherche et les segments partent avec le reste, comme le
        // `topRow` (`if arrangingBots { botsSection } else { topRow … }`).
        if (!arrangingBots) {
        // Le menu du serveur, au-dessus de la liste (`serverMenu` ): « All Servers » et
        // chaque serveur enregistré. Il n'apparaît qu'avec deux serveurs et plus.
        if (servers.size >= 2) {
            ServerMenuBar(
                allServers = allServers,
                activeName = servers.firstOrNull { it.id == activeServerId }?.name.orEmpty(),
                onOpenMenu = { serverMenuOpen = true },
            )
        }
        // « Search conversations »: une capsule haute, comme le champ.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .background(theme.fill, RoundedCornerShape(26.dp))
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OnyxIcon(Symbols.MagnifyingGlass, tint = theme.secondaryText, size = 20.dp)
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    BasicText(
                        text = "Search conversations",
                        style = androidx.compose.ui.text.TextStyle(
                            color = theme.tertiaryText,
                            fontSize = 17.sp,
                        ),
                    )
                }
                androidx.compose.foundation.text.BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    textStyle = androidx.compose.ui.text.TextStyle(color = theme.textColor, fontSize = 17.sp),
                    singleLine = true,
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(theme.accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) {
                OnyxIcon(
                    symbol = Symbols.XmarkCircle,
                    tint = theme.tertiaryText,
                    size = 18.dp,
                    modifier = Modifier.onyxClickable { query = "" },
                )
            }
        }

        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            OnyxSegmented(
                options = ListSection.entries.map { it.title },
                selectedIndex = ListSection.entries.indexOf(presentedSection),
                onSelect = { index ->
                    // Un onglet **ouvre son écran** tout de suite, y compris sur téléphone: avant,
                    // il ne faisait qu'afficher une carte « Ouvrir Cron » avec un second bouton à
                    // toucher. Le segment revient à « Discussions » pour que le retour retombe sur
                    // la liste et non sur une carte.
                    when (val next = ListSection.entries[index]) {
                        ListSection.CHATS -> {
                            section = next.name
                            if (wideDetail) onOpenChats?.invoke() ?: onDone()
                        }
                        ListSection.CRON -> {
                            section = ListSection.CHATS.name
                            onOpenCron()
                        }
                        ListSection.KANBAN -> {
                            section = ListSection.CHATS.name
                            onOpenKanban()
                        }
                    }
                },
            )
        }
        }

        // En largeur étendue, la colonne garde les sessions: le segment choisit le détail à droite.
        when (if (wideDetail) ListSection.CHATS else presentedSection) {
            ListSection.CHATS -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // La liste prend la place qui reste: la barre de sélection garde la sienne.
                    .weight(1f)
                    .verticalScroll(listScroll)
                    // Un tap à côté d'une rangée referme son volet d'actions (les rangées, elles,
                    // gardent le leur: un enfant touché consomme le tap avant ce filet).
                    .pointerInput(Unit) { detectTapGestures(onTap = { openRow = null }) }
                    .padding(bottom = 30.dp),
            ) {
                // L'erreur du serveur: la carte de problème quand rien ne s'affiche, une ligne
                // discrète quand la liste est déjà là (un renommage ou un épinglage raté).
                error?.let { message ->
                    if (visible.isEmpty()) {
                        com.onyx.app.ui.common.ConnectionProblemCard(
                            message = message,
                            onRetry = onRetry,
                            onOpenSettings = onOpenSettings,
                        )
                    } else {
                        InlineError(message = message, onRetry = onRetry)
                    }
                }

                // L'attente: le rond qui tourne , quand la liste est vide et rien n'a échoué.
                if (loading && visible.isEmpty() && error == null) {
                    ListSpinner()
                }

                // Un serveur qui n'a pas répondu est nommé au-dessus de la liste, comme le
                // `serversUnreachable`: plutôt qu'une liste vide, on dit pourquoi. Rien
                // tant que « All Servers » est éteint — on ne parle alors que d'un serveur.
                if (allServersOn) AllServers.unreachableLine(unreachable.map { it.name })?.let { line ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 26.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OnyxIcon(Symbols.WifiExclamation, tint = theme.secondaryText, size = 14.dp)
                        BasicText(
                            text = line,
                            localize = false,
                            style = theme.footnote.copy(color = theme.secondaryText),
                        )
                    }
                }

                val pinnedRows = visible.filter { it.pinned && !it.archived }
                val chats = visible.filter { !it.pinned && !it.archived }
                val archived = visible.filter { it.archived }
                // Les rangées de la section « Chats »: les nôtres et celles des autres serveurs
                // (« All Servers »), chacune à sa place dans le temps — la liste plate.
                val chatRows = (chats.map { AllServerRow(it, serverOf(it)) } + remoteRows)
                    .sortedByDescending { it.conversation.updatedAt }
                // Les conversations réunies par serveur d'origine: chaque serveur est un projet,
                // comme `hermes serve` range ses sessions par dossier de travail. Le groupe porte
                // le nom du serveur quand on le connaît, jamais son identifiant brut.
                val projects = remember(visible, servers) {
                    visible.filter { it.serverId.isNotBlank() }
                        .groupBy { conversation ->
                            servers.firstOrNull { it.id == conversation.serverId }?.name ?: conversation.serverId
                        }
                }

                BotsSection(
                    mainName = mainName,
                    bots = bots,
                    currentId = currentId,
                    // Toutes les conversations, pas seulement celles que la recherche garde: la
                    // ligne d'un bot montre son dernier mot même quand on cherche autre chose.
                    conversations = conversations,
                    groups = groups,
                    mutes = mutes,
                    onOpenBotChat = onOpenBotChat,
                    onOpenGroup = onOpenGroup,
                    onNewBot = onNewBot,
                    onNewGroupChat = { creatingGroup = true },
                    onLetBotsMessage = onLetBotsMessage,
                    open = botsOpen,
                    onToggleOpen = { botsOpen = !botsOpen },
                    badges = badges,
                    arranged = arranged,
                    hiddenCount = hiddenBotCount,
                    arranging = arrangingBots,
                    order = botOrder,
                    onOpenHidden = onOpenHiddenBots,
                    onMove = onMoveRow,
                    onSortByRecent = onSortBotsByRecent,
                    onArrange = { arrangingBots = true },
                    onDoneArrange = { arrangingBots = false },
                    onRoomSeen = onRoomSeen,
                    onEditBot = onEditBot,
                    onChangeFace = onChangeFace,
                    onBotSchedules = onBotSchedules,
                    onBotVoice = onBotVoice,
                    onHideBot = onHideBot,
                    onRemoveAPIBot = onRemoveAPIBot,
                    onMarkBotUnread = onMarkBotUnread,
                    onMarkBotSeen = onMarkBotSeen,
                    onMarkRoomUnread = onMarkRoomUnread,
                )

                // Pendant « Reorder Bots », la liste ne montre que la section ( n'y laisse
                // qu'elle: `if arrangingBots { botsSection } else { … }`).
                if (!arrangingBots) {

                // Les projets du serveur, quand le Dashboard les connaît (`projectsSection`:
                // un dossier, son libellé, son compte, sa dernière activité); sinon le
                // regroupement local par serveur, qui reste utile sans Dashboard.
                if (serverProjects.isNotEmpty() && onOpenProject != null) {
                    ServerProjectsSection(
                        projects = serverProjects,
                        open = projectsOpen,
                        onToggleOpen = { projectsOpen = !projectsOpen },
                        onOpen = onOpenProject,
                    )
                } else if (projects.isNotEmpty()) {
                    ProjectsSection(
                        projects = projects,
                        open = projectsOpen,
                        onToggleOpen = { projectsOpen = !projectsOpen },
                        currentId = currentId,
                        onOpen = onOpen,
                    )
                }

                if (pinnedRows.isNotEmpty()) {
                    FoldingHeader(
                        title = "Pinned",
                        count = pinnedRows.size,
                        open = pinnedOpen,
                        onToggle = { pinnedOpen = !pinnedOpen },
                    )
                    if (pinnedOpen) pinnedRows.forEach { conversation ->
                        ConversationRow(
                            conversation = conversation,
                            isOpen = AllServers.key(serverOf(conversation), conversation.id) == openKey,
                            selecting = selecting,
                            checked = conversation.id in selected,
                            onToggle = {
                                selected = if (conversation.id in selected) selected - conversation.id else selected + conversation.id
                            },
                            onClick = { rect ->
                                if (selecting) {
                                    selected = if (conversation.id in selected) selected - conversation.id else selected + conversation.id
                                } else {
                                    onOpen(conversation, rect)
                                }
                            },
                            onDelete = { onDelete(conversation) },
                            muted = mutes.isMuted(conversation.id),
                            onMenu = { muting = conversation },
                            mark = unreadMarks[conversation.id],
                        )
                    }
                }

                // « Chats » se replie comme Pinned, Projects et Archived: même en-tête que le
                // `foldingHeader` , son compte et son chevron.
                FoldingHeader(
                    title = "Chats",
                    count = chatRows.size,
                    open = chatsOpen,
                    onToggle = { chatsOpen = !chatsOpen },
                )

                if (chatsOpen) {
                if (chatRows.isEmpty() && pinnedRows.isEmpty()) {
                    if (needle.isEmpty()) {
                        EmptyState(
                            symbol = Symbols.Bubbles,
                            title = "No conversations yet",
                            description = "Conversations from every platform appear here.",
                        )
                    } else {
                        EmptyState(
                            symbol = Symbols.MagnifyingGlass,
                            title = "Nothing here by that name.",
                        )
                    }
                } else {
                    chatRows.forEach { row ->
                        if (row.remote) {
                            // La rangée d'un autre serveur: étiquetée, sans glissement ni
                            // sélection, elle s'ouvre sur son serveur (le `remoteButton` ).
                            RemoteConversationRow(
                                row = row,
                                open = row.key == openKey,
                                onClick = { onOpenRemote?.invoke(row) },
                            )
                        } else {
                            val conversation = row.conversation
                            SwipeableRowActions(
                            conversation = conversation,
                            open = conversation.id == openRow,
                            onOpen = { openRow = conversation.id },
                            onClose = { if (openRow == conversation.id) openRow = null },
                            onPin = onPin,
                            onArchive = onArchive,
                            onDelete = onDelete,
                        ) {
    ConversationRow(
                                conversation = conversation,
                                isOpen = row.key == openKey,
                                selecting = selecting,
                                checked = conversation.id in selected,
                                onToggle = {
                                    selected = if (conversation.id in selected) selected - conversation.id else selected + conversation.id
                                },
                                onClick = { rect ->
                                    if (selecting) {
                                        selected = if (conversation.id in selected) selected - conversation.id else selected + conversation.id
                                    } else {
                                        onOpen(conversation, rect)
                                    }
                                },
                                muted = mutes.isMuted(conversation.id),
                                onMenu = { muting = conversation },
                                onDelete = { onDelete(conversation) },
                                mark = unreadMarks[conversation.id],
                            )
                        }
                    }
                    }
                }
                }

                // Les archivées: hors de la liste tant qu'on ne les ouvre pas, comme.
                if (archived.isNotEmpty()) {
                    FoldingHeader(
                        title = "Archived",
                        count = archived.size,
                        open = showArchived,
                        onToggle = { showArchived = !showArchived },
                    )
                    CollectionSummary(
                        total = visible.size,
                        pinned = visible.count { it.pinned },
                        archived = visible.count { it.archived },
                    )

                    if (showArchived) {
                        archived.forEach { conversation ->
                            ConversationRow(
                                conversation = conversation,
                                isOpen = AllServers.key(serverOf(conversation), conversation.id) == openKey,
                                selecting = selecting,
                                checked = conversation.id in selected,
                                onToggle = {
                                    selected = if (conversation.id in selected) selected - conversation.id else selected + conversation.id
                                },
                                onClick = { rect ->
                                    if (selecting) {
                                        selected = if (conversation.id in selected) selected - conversation.id else selected + conversation.id
                                    } else {
                                        onOpen(conversation, rect)
                                    }
                                },
                                muted = mutes.isMuted(conversation.id),
                                onMenu = { muting = conversation },
                                onDelete = { onDelete(conversation) },
                                mark = unreadMarks[conversation.id],
                            )
                        }
                    }
                }

                // Le pied de liste: sous la dernière section, les lignes de service —
                // réglages rapides, import/export, « Show archived » — puis les `footer` bruts.
                val quickSettings = onQuickSettings ?: onOpenSettings
                val importExport = onImportExport
                val serviceFooter = buildList {
                    add(FooterAction("Quick Settings", Symbols.GearshapeSingle) { quickSettings.invoke() })
                    importExport?.let { work ->
                        add(FooterAction("Import / Export", Symbols.Share, work))
                    }
                    add(
                        FooterAction(
                            if (showArchived) "Hide archived" else "Show archived",
                            if (showArchived) Symbols.EyeSlash else Symbols.Folder,
                        ) { showArchived = !showArchived },
                    )
                    addAll(footerActions)
                }

                if (footers.isNotEmpty()) {
                    ListFooter(lines = footers)
                }
                }
            }
    // Cron et Kanban s'ouvrent depuis les onglets ci-dessus: plus de carte intermédiaire.
            ListSection.CRON, ListSection.KANBAN -> Unit
        }

        // La barre de sélection, sous la liste, comme la `bottomBar`: le compte, puis les
        // actions groupées. La suppression demande confirmation.
        if (selecting) {
            SelectionBar(
                count = selected.size,
                allSelected = visible.isNotEmpty() && selected.size == visible.size,
                onSelectAll = {
                    val every = visible.map { it.id }.toSet()
                    selected = if (selected.size == every.size && selected.containsAll(every)) emptySet() else every
                },
                onCancel = {
                    selecting = false
                    selected = emptySet()
                },
                onArchive = {
                    val ids = selected
                    selecting = false
                    selected = emptySet()
                    onBulkArchive(ids)
                },
                onDelete = { confirmBulkDelete = true },
            )
        }
    }
    // Le menu du serveur (`serverMenu` ): « All Servers » au-dessus des serveurs, celui où
    // l'app est coché quand on n'est pas sur « All Servers ». Ne rien changer ne change rien.
    if (serverMenuOpen) {
        com.onyx.app.ui.design.OnyxMenu(
            items = buildList {
                add(
                    com.onyx.app.ui.design.MenuItem(
                        label = "All Servers",
                        symbol = Symbols.SquareStack3dUp,
                        checked = allServers,
                    ) {
                        allServers = true
                        serverMenuOpen = false
                    },
                )
                servers.forEach { server ->
                    add(
                        com.onyx.app.ui.design.MenuItem(
                            label = server.name,
                            localize = false,
                            checked = !allServers && server.id == activeServerId,
                        ) {
                            allServers = false
                            serverMenuOpen = false
                            onSwitchServer?.invoke(server.id)
                        },
                    )
                }
            },
            onDismiss = { serverMenuOpen = false },
        )
    }

    // Le menu d'une rangée, comme le `contextMenu` , dans son ordre: marquer lu ou non lu,
    // ouvrir ailleurs, renommer, épingler, copier le titre, archiver, se taire, puis supprimer — le
    // destructeur en dernier, comme dans.
    muting?.let { conversation ->
        val items = buildList {
            // « Mark as Read » / « Mark as Unread » , en tête du menu. La conversation
            // ouverte ne se marque pas: la quitter la marque lue de nouveau (le `canMark` du
            // , `session.id != conversation.serverSessionID`).
            onSetUnread?.takeIf { conversation.id != currentId }?.let { setUnread ->
                val unread = conversationUnread(
                    lastSpokenAt = conversation.messages.lastOrNull { it.role == Role.ASSISTANT }?.createdAt,
                    seen = unreadMarks[conversation.id],
                    isOpen = false,
                    busy = conversation.messages.lastOrNull()?.isStreaming == true,
                    muted = mutes.isMuted(conversation.id),
                    fallback = conversation.messages.lastOrNull()?.role == Role.ASSISTANT,
                )
                add(
                    com.onyx.app.ui.design.MenuItem(
                        if (unread) "Mark as Read" else "Mark as Unread",
                        if (unread) Symbols.CheckmarkMessage else Symbols.MessageBadge,
                    ) {
                        setUnread(conversation, !unread)
                        muting = null
                    },
                )
            }
            // « Open in New Window »: la même conversation ouverte dans une autre fenêtre.
            // Le pendant Android est d'ouvrir la conversation ailleurs sans quitter celle-ci; on ne
            // le montre que si l'écran sait le faire.
            onOpenInNewWindow?.let { openElsewhere ->
                add(
                    com.onyx.app.ui.design.MenuItem("Open in New Window", Symbols.SquareAndPencil) {
                        muting = null
                        openElsewhere(conversation)
                    },
                )
            }
            // « Usage & cost »: placé avant « Rename », comme le `contextMenu`.
            onOpenUsage?.let { openUsage ->
                add(
                    com.onyx.app.ui.design.MenuItem("Usage & cost", Symbols.ChartBar) {
                        muting = null
                        openUsage(conversation)
                    },
                )
            }
            add(
                com.onyx.app.ui.design.MenuItem("Rename", Symbols.Pencil) {
                    renameText = conversation.title
                    renaming = conversation
                    muting = null
                },
            )
            add(
                com.onyx.app.ui.design.MenuItem(
                    if (conversation.pinned) "Unpin" else "Pin",
                    Symbols.Pin,
                ) {
                    onPin(conversation, !conversation.pinned)
                    muting = null
                },
            )
            add(
                com.onyx.app.ui.design.MenuItem("Copy title", Symbols.DocOnDoc) {
                    scope.launch {
                        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Onyx", conversation.title)))
                    }
                    muting = null
                },
            )
            add(
                com.onyx.app.ui.design.MenuItem(
                    if (conversation.archived) "Unarchive" else "Archive",
                    Symbols.Folder,
                ) {
                    onArchive(conversation, !conversation.archived)
                    muting = null
                },
            )
            addAll(
                com.onyx.app.ui.common.muteMenuItems(mutes, conversation.id).map { item ->
                    com.onyx.app.ui.design.MenuItem(item.label, item.symbol) {
                        item.onSelect()
                        muting = null
                    }
                },
            )
            add(
                com.onyx.app.ui.design.MenuItem("Delete", Symbols.Trash, destructive = true) {
                    muting = null
                    onDelete(conversation)
                },
            )
        }
        com.onyx.app.ui.design.OnyxMenu(items = items, onDismiss = { muting = null })
    }

    // « Rename session »: la même invite qu', un champ, Save ou Cancel.
    renaming?.let { conversation ->
        com.onyx.app.ui.design.OnyxPrompt(
            title = "Rename session",
            message = null,
            placeholder = "Title",
            value = renameText,
            onValueChange = { renameText = it },
            confirmLabel = "Save",
            onConfirm = {
                val title = renameText.trim()
                renaming = null
                if (title.isNotEmpty()) onRename?.invoke(conversation, title)
            },
            onDismiss = { renaming = null },
        )
    }

    // La confirmation de la suppression groupée, comme le `confirmationDialog`.
    if (confirmBulkDelete) {
        val count = selected.size
        com.onyx.app.ui.design.OnyxAlert(
            title = "Delete $count conversation${if (count == 1) "" else "s"}?",
            message = "This only removes them from this phone.",
            actions = listOf(
                com.onyx.app.ui.design.AlertAction("Delete", destructive = true) {
                    val ids = selected
                    confirmBulkDelete = false
                    selecting = false
                    selected = emptySet()
                    onBulkDelete(ids)
                },
                com.onyx.app.ui.design.AlertAction("Cancel") { confirmBulkDelete = false },
            ),
            onDismiss = { confirmBulkDelete = false },
        )
    }

    // La barre « … » de la liste: ce que la conversation choisie contient, comme la feuille
    // d'informations.
    infoShown?.let { conversation ->
        ConversationInfoSheet(conversation = conversation, onClose = { infoShown = null })
    }

    // « New Group Chat »: choisir qui répond (deux à six bots), puis Create. La salle est faite
    // sur le serveur — gardée ici quand il n'y a pas de Dashboard —, puis s'ouvre avec ses membres,
    // comme la feuille `NewGroupChatView` du modèle. Un échec reste dans la feuille.
    if (creatingGroup) {
        NewGroupSheet(
            bots = bots,
            onCreate = { picked, name -> onNewGroupChat(picked, name) },
            onCreated = { room ->
                creatingGroup = false
                onOpenGroup?.invoke(room)
            },
            onClose = { creatingGroup = false },
        )
    }
}

/** Un bouton rond de la barre, comme ceux. */
@Composable
private fun RoundButton(
    symbol: Int,
    label: String,
    onClick: () -> Unit,
    tint: Color? = null,
) {
    val theme = LocalOnyxTheme.current
    Box(
        modifier = Modifier
            .size(34.dp)
            .background(theme.fill, CircleShape)
            .onyxClickable(pressedScale = 0.9f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        OnyxIcon(symbol = symbol, tint = tint ?: theme.secondaryText, size = 17.dp, contentDescription = label)
    }
}

/**
 * Le rond qui tourne, comme le `ProgressView`: un arc tournant, dessiné à la main — aucun
 * composant Material n'a sa place ici.
 */
@Composable
private fun OnyxSpinner(size: Dp = 20.dp, tint: Color? = null) {
    val theme = LocalOnyxTheme.current
    val color = tint ?: theme.secondaryText
    val transition = rememberInfiniteTransition(label = "spinner")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 900, easing = LinearEasing)),
        label = "angle",
    )
    Canvas(modifier = Modifier.size(size).rotate(angle)) {
        val stroke = this.size.minDimension * 0.18f
        drawArc(
            color = color,
            startAngle = 0f,
            sweepAngle = 285f,
            useCenter = false,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}

/** L'attente de la liste: le rond et ce qu'on attend. */
@Composable
private fun ListSpinner(label: String = "Loading…") {
    val theme = LocalOnyxTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 44.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OnyxSpinner(size = 24.dp)
        BasicText(text = label, style = theme.footnote.copy(color = theme.secondaryText))
    }
}

/**
 * L'erreur d'une action, quand la liste a déjà de quoi s'afficher: une ligne discrète, comme le
 * `Label` orange — un échec ne doit pas blanchir la liste déjà chargée. Une ligne
 * « Try again » laisse réessayer sans quitter ce qu'on lisait.
 */
@Composable
private fun InlineError(message: String, onRetry: (() -> Unit)? = null) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        OnyxIcon(Symbols.ExclamationTriangle, tint = theme.warning, size = 14.dp)
        BasicText(
            text = message,
            localize = false,
            style = theme.footnote.copy(color = theme.warning),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        onRetry?.let { retry ->
            BasicText(
                text = "Try again",
                style = theme.footnote.copy(color = theme.accent, fontWeight = FontWeight.SemiBold),
                modifier = Modifier.onyxClickable(onClick = retry),
            )
        }
    }
}

/** Les lignes de pied de liste, comme les `footer` d'une `Section`: petites et grises. */
@Composable
private fun ListFooter(lines: List<String>) {
    val theme = LocalOnyxTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        lines.forEach { line ->
            BasicText(
                text = line,
                style = theme.footnote.copy(color = theme.secondaryText),
            )
        }
    }
}

/**
 * Les lignes de service du pied de liste, comme les `Button` d'un `footer`: une icône
 * teintée, puis le libellé — réglages rapides, import/export, « Show archived ».
 */
@Composable
private fun ListFooterActions(actions: List<FooterAction>) {
    val theme = LocalOnyxTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        actions.forEach { action ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .onyxTappable(onClick = action.onClick)
                    .padding(horizontal = 8.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                    OnyxIcon(symbol = action.symbol, tint = theme.secondaryText, size = 15.dp)
                }
                BasicText(
                    text = action.title,
                    style = theme.footnote.copy(color = theme.secondaryText),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * La barre de sélection, sous la liste, comme la `bottomBar` du modèle: « Archiver N » au
 * bord gauche, « Supprimer N » au bord droit — une icône et le compte, l'écart du modèle
 * entre les deux —, et au-dessus le compte des rangées cochées avec ses deux actions de liste
 * (« Tout sélectionner » / « Tout désélectionner », Annuler). Rien de coché, les deux actions
 * groupées s'éteignent.
 *
 * Le compte ne se casse **jamais** au milieu d'un mot: sa largeur est mesurée sous la police du
 * système ([selectionBarRows]) et la barre se replace au lieu de couper — le compte entier à côté
 * des actions, sinon sur sa propre ligne, les actions dessous, et le nombre seul en dernier recours.
 * Rien ne touche la barre de gestes: son inset est pris ici, une seule fois.
 */
@Composable
private fun SelectionBar(
    count: Int,
    allSelected: Boolean,
    onSelectAll: () -> Unit,
    onCancel: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    val enabled = count > 0
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val spacing = 16.dp
    val countStyle = theme.subheadline.copy(color = theme.secondaryText)
    val actionStyle = theme.body.copy(color = theme.accent)
    val countFull = "$count selected"
    val countShort = "$count"
    val selectAllText = if (allSelected) "Deselect all" else "Select all"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .background(theme.surfaceColor),
    ) {
        // Le filet du haut: la liste défile dessous, comme sous les barres du modèle.
        OnyxRowDivider(inset = 0.dp)
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            // La mesure porte sur le texte **localisé** et sur la densité courante: la police
            // d'accessibilité agrandie change les largeurs, pas seulement le dessin.
            val rows = selectionBarRows(
                available = constraints.maxWidth.toFloat(),
                fullCount = measurer.measure(resolveOnyxText(countFull), countStyle).size.width.toFloat(),
                shortCount = measurer.measure(resolveOnyxText(countShort), countStyle).size.width.toFloat(),
                selectAll = measurer.measure(resolveOnyxText(selectAllText), actionStyle).size.width.toFloat(),
                cancel = measurer.measure(resolveOnyxText("Cancel"), actionStyle).size.width.toFloat(),
                spacing = with(density) { spacing.toPx() },
            )
            val countLabel = if (rows.shortCount) countShort else countFull

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (rows.actionsBelow) {
                    // Le compte garde sa ligne entière; ses actions descendent dessous.
                    SelectionBarCount(countLabel, countStyle, Modifier.fillMaxWidth())
                    if (rows.actionsStacked) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            SelectionBarText(selectAllText, theme.accent, onSelectAll)
                            SelectionBarText("Cancel", theme.accent, onCancel)
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(spacing, Alignment.End),
                        ) {
                            SelectionBarText(selectAllText, theme.accent, onSelectAll)
                            SelectionBarText("Cancel", theme.accent, onCancel)
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing),
                    ) {
                        // Le compte cède la place, jamais un mot: une seule ligne, ellipse au pire.
                        SelectionBarCount(countLabel, countStyle, Modifier.weight(1f))
                        SelectionBarText(selectAllText, theme.accent, onSelectAll)
                        SelectionBarText("Cancel", theme.accent, onCancel)
                    }
                }

                // Les deux actions groupées, aux deux bords: l'ordre et l'écart du modèle.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    SelectionBarAction(
                        symbol = Symbols.ArchiveBox,
                        label = "Archive $count",
                        tint = if (enabled) theme.accent else theme.tertiaryText,
                        enabled = enabled,
                        onClick = onArchive,
                    )
                    SelectionBarAction(
                        symbol = Symbols.Trash,
                        label = "Delete $count",
                        tint = if (enabled) theme.error else theme.tertiaryText,
                        enabled = enabled,
                        semibold = true,
                        onClick = onDelete,
                    )
                }
            }
        }
    }
}

/**
 * Ce que la barre de sélection met sur la ligne du compte, décidé en **mesurant** la police du
 * système plutôt qu'en pariant sur une largeur en dp.
 *
 * La capture du propriétaire montrait le compte cassé mot par mot — « 4 / élément / s / sélectio /
 * nnés » — parce qu'il était le seul élément souple d'une ligne serrée entre deux actions de largeur
 * fixe. Trois replis, dans cet ordre, et le texte reste entier dans tous les cas: le compte en
 * entier s'il tient à côté des actions; le seul nombre s'il ne reste que lui; puis les actions
 * descendent — ensemble, ou chacune sur sa ligne s'il le faut.
 */
internal data class SelectionBarRows(
    /** Le compte s'écrit en abrégé (« 4 »): le libellé entier ne tient pas à côté des actions. */
    val shortCount: Boolean = false,
    /** Les deux actions ne tiennent pas sur la ligne du compte: elles passent en dessous. */
    val actionsBelow: Boolean = false,
    /** Même en dessous, elles ne tiennent pas côte à côte: chacune prend sa ligne. */
    val actionsStacked: Boolean = false,
)

/**
 * La forme de la barre pour une largeur utile [available] (en pixels, police du système comprise),
 * les largeurs mesurées du compte ([fullCount], [shortCount]) et des deux actions ([selectAll],
 * [cancel]), et leur écart [spacing].
 */
internal fun selectionBarRows(
    available: Float,
    fullCount: Float,
    shortCount: Float,
    selectAll: Float,
    cancel: Float,
    spacing: Float,
): SelectionBarRows {
    val beside = 2f * spacing + selectAll + cancel
    // Le compte en entier reste-t-il sur sa ligne, à côté des deux actions ? C'est la forme la plus
    // courte, et la seule qui garde la barre à deux lignes.
    if (fullCount + beside <= available) return SelectionBarRows()
    val stacked = selectAll + cancel + spacing > available
    // Sinon le compte prend **sa** ligne — en entier: un compte abrégé se lit mal, et la phrase
    // n'est pas un mot qu'on coupe, c'est une information qui change de place.
    if (fullCount <= available) return SelectionBarRows(actionsBelow = true, actionsStacked = stacked)
    // Même seul sur sa ligne, le libellé entier ne tient pas (police très agrandie, volet très
    // étroit): le nombre seul, qui lui tient toujours.
    return SelectionBarRows(shortCount = true, actionsBelow = true, actionsStacked = stacked)
}

/** Le compte des rangées cochées: une seule ligne, jamais coupé au milieu d'un mot. */
@Composable
private fun SelectionBarCount(label: String, style: TextStyle, modifier: Modifier = Modifier) {
    BasicText(
        text = label,
        style = style,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** Une action de liste de la barre (« Tout sélectionner », « Annuler »). */
@Composable
private fun SelectionBarText(label: String, color: Color, onClick: () -> Unit) {
    BasicText(
        text = label,
        style = LocalOnyxTheme.current.body.copy(color = color),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.onyxClickable(onClick = onClick),
    )
}

/** Une action groupée de la barre: son icône et son libellé, comme les boutons du modèle. */
@Composable
private fun SelectionBarAction(
    @DrawableRes symbol: Int,
    label: String,
    tint: Color,
    enabled: Boolean,
    semibold: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.onyxClickable(enabled = enabled, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        OnyxIcon(symbol = symbol, tint = tint, size = 17.dp)
        BasicText(
            text = label,
            style = LocalOnyxTheme.current.body.copy(
                color = tint,
                fontWeight = if (semibold) FontWeight.SemiBold else FontWeight.Normal,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * La section des bots, comme `botsSection`: des **lignes**, une par bot (`botRow`) — le
 * point du non-lu dans la marge, son visage, son nom en gras, l'heure de son dernier mot, puis le
 * dernier mot de son chat sous lui —, les **salles** du serveur (`groupRow`, leurs visages empilés
 * et qui en fait partie), puis les lignes qui font agir (New Group Chat, New Bot, laisser les bots
 * se parler), séparées par le filet rentré d'une liste.
 *
 * Pendant « Reorder Bots », la section seule s'affiche, ses lignes à poignées (`BotsArrangeList`),
 * comme `arrangingBots`. « Reorder Bots » et « Sort Bots by Recent » ne sont pas des lignes
 * de la section: elles sont dans le menu contextuel d'une ligne et d'une salle (`orderingBots`),
 * comme.
 */
@Composable
private fun BotsSection(
    hasDashboard: Boolean = true,
    mainName: String = "Onyx",
    bots: List<BotProfile>,
    currentId: String?,
    conversations: List<Conversation>,
    groups: List<GroupChat>,
    mutes: com.onyx.app.ui.common.Mutes,
    /** Le tap d'une tuile: le chat du bot (`openBot`), jamais son éditeur. */
    onOpenBotChat: (BotProfile) -> Unit,
    onOpenGroup: ((GroupChat) -> Unit)?,
    onNewBot: () -> Unit,
    onNewGroupChat: () -> Unit,
    onLetBotsMessage: () -> Unit,
    open: Boolean,
    onToggleOpen: () -> Unit,
    badges: BotBadges = BotBadges(),
    arranged: List<BotListItem> = emptyList(),
    hiddenCount: Int = 0,
    arranging: Boolean = false,
    order: List<String> = emptyList(),
    onOpenHidden: (() -> Unit)? = null,
    onMove: ((Int, Int) -> Unit)? = null,
    onSortByRecent: (() -> Unit)? = null,
    onArrange: (() -> Unit)? = null,
    onDoneArrange: (() -> Unit)? = null,
    onRoomSeen: ((GroupChat) -> Unit)? = null,
    /** « Edit Bot… » du menu d'une tuile. */
    onEditBot: ((BotProfile) -> Unit)? = null,
    /** « Change Face… » du menu d'une tuile; sans lui, l'entrée n'apparaît pas. */
    onChangeFace: ((BotProfile) -> Unit)? = null,
    /** « Schedules… » du menu d'une tuile; sans lui, l'entrée n'apparaît pas. */
    onBotSchedules: ((BotProfile) -> Unit)? = null,
    /** « Voice… » du menu d'une tuile (`BotVoicePicker`); sans lui, l'entrée n'apparaît pas. */
    onBotVoice: ((BotProfile) -> Unit)? = null,
    /** « Hide » du menu d'une tuile. */
    onHideBot: ((BotProfile) -> Unit)? = null,
    /** « Remove from Onyx » du menu d'une tuile, sur un bot ajouté à la main. */
    onRemoveAPIBot: ((BotProfile) -> Unit)? = null,
    /** « Mark as Unread » du menu d'une tuile. */
    onMarkBotUnread: ((BotProfile) -> Unit)? = null,
    /** « Mark as Read » du menu d'une tuile. */
    onMarkBotSeen: ((BotProfile) -> Unit)? = null,
    /** « Mark as Unread » du menu d'une salle. */
    onMarkRoomUnread: ((GroupChat) -> Unit)? = null,
) {
    val theme = LocalOnyxTheme.current

    // L'en-tête de section: « Bots » tout court pendant la mise en ordre, le compte sinon.
    FoldingHeader(
        title = "Bots",
        count = if (arranging) null else bots.size,
        open = open,
        onToggle = { if (arranging) onDoneArrange?.invoke() else onToggleOpen() },
    )

    if (arranging) {
        BotsArrangeList(
            items = arranged,
            bots = bots,
            badges = badges,
            mutes = mutes,
            openedChatId = conversations
                .firstOrNull { it.id == currentId || it.serverSessionId == currentId }
                ?.serverSessionId,
            mainName = mainName,
            onMove = { from, to -> onMove?.invoke(from, to) },
            onOpenBot = onOpenBotChat,
            onOpenRoom = { room -> groups.firstOrNull { it.id == room.id }?.let { onOpenGroup?.invoke(it) } },
        )
        return
    }

    if (!open) return

    if (bots.isEmpty() && groups.isEmpty()) {
        BasicText(
            text = "Bots are your server's other profiles. With the Dashboard login in Settings, Onyx lists them.",
            style = theme.footnote.copy(color = theme.secondaryText),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
    }

    // Le chat du bot ouvert: sa ligne garde le fond teinté du `listRowBackground`.
    val openedChatId = conversations
        .firstOrNull { it.id == currentId || it.serverSessionId == currentId }
        ?.serverSessionId

    // La ligne de bot ou la salle dont le menu est ouvert (l'appui long), comme le `contextMenu`.
    var menuBot by remember { mutableStateOf<BotProfile?>(null) }
    var menuRoom by remember { mutableStateOf<GroupChat?>(null) }

    // Où chaque rangée est à l'écran (dans la fenêtre): le menu s'ouvre **près d'elle**, pas au
    // centre. La table se remplit aux mises en page et ne recompose rien.
    val rowBounds = remember { mutableMapOf<String, Rect>() }

    // Les bots, un à un (`botRow`): leur visage, leur nom, l'heure de leur dernier mot — ou le rond
    // qui tourne —, puis le dernier mot de leur chat, la marque « Needs you » devant lui.
    bots.forEach { bot ->
        val muted = mutes.isMuted("bot:" + bot.name)
        val isOpen = bot.chatId != null && bot.chatId == openedChatId
        Box(
            modifier = Modifier.onGloballyPositioned {
                rowBounds["bot:" + bot.name] = it.boundsInWindow()
            },
        ) {
            BotRow(
                bot = bot,
                roster = bots,
                mainName = mainName,
                lastWord = botRowLastWord(bot, conversations),
                lastActive = botRowLastActive(bot, conversations),
                activity = badges.forBot(
                    bot = bot,
                    isOpen = isOpen,
                    openStreaming = conversations.any { conversation ->
                        conversation.serverSessionId == bot.chatId &&
                            conversation.messages.any { it.isStreaming }
                    },
                    muted = muted,
                ),
                muted = muted,
                isOpen = isOpen,
                onClick = { onOpenBotChat(bot) },
                onLongPress = { menuBot = bot },
            )
        }
        BotRowSeparator()
    }

    // Les salles du serveur sous eux (`groupRow`), puis ce qui fait agir.
    groups.forEach { group ->
        val muted = mutes.isMuted("room:" + group.id)
        Box(
            modifier = Modifier.onGloballyPositioned {
                rowBounds["room:" + group.id] = it.boundsInWindow()
            },
        ) {
            GroupRow(
                group = group,
                muted = muted,
                activity = badges.forRoom(
                    id = group.id,
                    activityMark = group.activityMark,
                    isOpen = group.id == currentId,
                    muted = muted,
                ),
                isOpen = group.id == currentId,
                onClick = {
                    onRoomSeen?.invoke(group)
                    onOpenGroup?.invoke(group)
                },
                // L'appui long ouvre le menu de la salle, comme le `contextMenu` du `groupRow`.
                onMenu = { menuRoom = group },
            )
        }
        BotRowSeparator()
    }

    // Les actions de la section, dans l'ordre: le symbole teinté tient la place du visage,
    // et leur titre s'aligne sur les noms des lignes.
    ActionRow(title = "New Group Chat", symbol = Symbols.Person3, onClick = onNewGroupChat)
    BotRowSeparator()
    ActionRow(
        title = if (hasDashboard) "New Bot" else "Add Bot",
        symbol = Symbols.PlusBubble,
        onClick = onNewBot,
    )
    BotRowSeparator()
    ActionRow(title = "Let bots message each other", symbol = Symbols.Bubbles, onClick = onLetBotsMessage)
    // La tuile des bots cachés de la grille (`gridAction("Hidden", systemImage: "eye.slash")`),
    // en ligne: elle dit combien de bots elle cache.
    if (hiddenCount > 0 && onOpenHidden != null) {
        BotRowSeparator()
        ActionRow(
            title = "Hidden",
            symbol = Symbols.EyeSlash,
            hint = hiddenHint(hiddenCount),
            onClick = { onOpenHidden() },
        )
    }
    BotRowSeparator()
    // « Reorder Bots » et « Sort Bots by Recent » ne sont plus des lignes de la section: les
    // met dans le menu d'une tuile (et d'une salle), dernier groupe après le filet (`orderingBots`).

    // Le menu d'une tuile, ouvert à l'appui long: les entrées du `botMenu` , dans son
    // ordre, et chacune son action.
    menuBot?.let { bot ->
        val isOpen = bot.chatId != null && bot.chatId == openedChatId
        val muted = mutes.isMuted("bot:" + bot.name)
        BotTileMenu(
            bot = bot,
            isOpen = isOpen,
            unread = badges.forBot(
                bot = bot,
                isOpen = isOpen,
                openStreaming = conversations.any { it.serverSessionId == bot.chatId && it.messages.any { message -> message.isStreaming } },
                muted = muted,
            ).unread,
            mutes = mutes,
            order = order,
            // Le panneau s'ouvre contre la rangée touchée, comme le `Menu` du modèle.
            anchor = rowBounds["bot:" + bot.name],
            onDismiss = { menuBot = null },
            onMarkRead = onMarkBotSeen?.let { seen -> { seen(bot) } },
            onMarkUnread = onMarkBotUnread?.let { unreadMark -> { unreadMark(bot) } },
            // « Voice… »: la voix du bot, choisie pour lui seul (`BotVoicePicker`).
            onVoice = onBotVoice?.let { open -> { open(bot) } },
            onSchedules = onBotSchedules?.let { open -> { open(bot) } },
            onRemoveFromOnyx = onRemoveAPIBot?.let { remove -> { remove(bot) } },
            onEdit = onEditBot?.let { edit -> { edit(bot) } },
            onChangeFace = onChangeFace?.let { open -> { open(bot) } },
            onHide = onHideBot?.let { hide -> { hide(bot) } },
            onReorder = onArrange,
            onSortByRecent = onSortByRecent,
        )
    }

    // Le menu d'une salle: marquée lue ou non lue quand le serveur lui connaît une activité, le
    // silence — sauf une salle d'Hermes Desktop, qui ne notifie pas —, puis l'ordre.
    menuRoom?.let { room ->
        val muted = mutes.isMuted("room:" + room.id)
        RoomMenu(
            room = room,
            canMark = room.activityMark != null,
            unread = badges.forRoom(
                id = room.id,
                activityMark = room.activityMark,
                isOpen = room.id == currentId,
                muted = muted,
            ).unread,
            mutes = mutes,
            order = order,
            // Le panneau s'ouvre contre la rangée de la salle, comme le `Menu` du modèle.
            anchor = rowBounds["room:" + room.id],
            onDismiss = { menuRoom = null },
            onMarkRead = onRoomSeen?.let { seen -> { seen(room) } },
            onMarkUnread = onMarkRoomUnread?.let { unreadMark -> { unreadMark(room) } },
            onReorder = onArrange,
            onSortByRecent = onSortByRecent,
        )
    }
}

/** Le filet d'une rangée de liste , rentré jusqu'au texte (les noms et les titres). */
@Composable
private fun BotRowSeparator() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = BOT_ROW_TEXT, end = 20.dp)
            .height(0.5.dp)
            .background(LocalOnyxTheme.current.separator),
    )
}

/** La marge gauche d'une ligne de la section des bots, comme celle d'une conversation. */
private val BOT_ROW_START = 20.dp

/** La colonne du point du non-lu, réservée même sans point, pour que rien ne se décale. */
private val BOT_ROW_DOT = 10.dp

/** La place du visage — et du symbole d'une ligne d'action. */
private val BOT_ROW_FACE = 40.dp

/** L'écart entre deux morceaux d'une ligne, comme le `HStack(spacing: 12)`. */
private val BOT_ROW_GAP = 12.dp

/** Où le texte d'une ligne commence: les noms, les titres, et le filet qui les sépare. */
private val BOT_ROW_TEXT = BOT_ROW_START + BOT_ROW_DOT + BOT_ROW_GAP + BOT_ROW_FACE + BOT_ROW_GAP

/**
 * Le point « pas encore lu » (`unreadDot`): dans la marge, à la place réservée pour lui, si
 * bien que les visages des lignes lues et des lignes non lues s'alignent.
 */
@Composable
private fun RowUnreadDot(shown: Boolean) {
    Box(
        modifier = Modifier.size(width = BOT_ROW_DOT, height = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (shown) {
            Box(modifier = Modifier.size(9.dp).background(LocalOnyxTheme.current.accent, CircleShape))
        }
    }
}

/**
 * Une ligne de bot, comme le `botRow`: le point du non-lu dans la marge, son visage, son
 * nom en gras, l'heure de son dernier mot — ou le rond qui tourne tant qu'il travaille —, puis le
 * dernier mot de son chat sous lui, la marque « Needs you » devant lui quand il attend une réponse.
 *
 * Le tap ouvre son **chat** (`onOpenBotChat`), l'appui long son menu, comme le `contextMenu`;
 * son chat ouvert ici, la ligne garde le fond teinté du `listRowBackground`.
 */
@Composable
private fun BotRow(
    bot: BotProfile,
    /** Le reste du roster: un dernier mot venu d'un autre bot se dit avec son nom à lui. */
    roster: List<BotProfile>,
    mainName: String,
    /** Le dernier mot de son chat, du serveur ou d'ici (`botRowLastWord`). */
    lastWord: String?,
    /** L'heure de ce mot (`botRowLastActive`); rien à dire, pas d'heure. */
    lastActive: Long?,
    activity: BotActivity,
    muted: Boolean,
    isOpen: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isOpen) theme.accent.copy(alpha = 0.08f) else theme.pageColor)
            .onyxTappable(onLongPress = onLongPress, onClick = onClick)
            .padding(start = BOT_ROW_START, end = 20.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BOT_ROW_GAP),
    ) {
        // Tu, la ligne ne porte pas de point (`muted`): ni au travail, ni une réponse non lue.
        RowUnreadDot(shown = activity.unread && !muted)
        // Son visage dit ce qu'il fait: il compose (réflexion), il attend une réponse, ou il se
        // tient tranquille. La transition d'une humeur à l'autre est fondue (dans `BotAvatar`).
        com.onyx.app.ui.design.BotLook(
            name = bot.name,
            faceCode = bot.faceCode,
            size = BOT_ROW_FACE,
            mood = when {
                activity.working -> BotMood.THINKING
                activity.needsYou -> BotMood.WAITING
                else -> BotMood.IDLE
            },
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BasicText(
                    text = bot.label(mainName),
                    localize = false,
                    style = theme.body.copy(color = theme.textColor, fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (muted) {
                    OnyxIcon(
                        symbol = com.onyx.app.ui.common.mutedMark,
                        tint = theme.secondaryText,
                        size = 13.dp,
                        contentDescription = "Muted",
                    )
                }
                // Au travail: le rond qui tourne tient la place de l'heure, comme `ProgressView`.
                if (activity.working) {
                    RowSpinner()
                } else {
                    lastActive?.let {
                        BasicText(text = rowTime(it), style = theme.caption.copy(color = theme.secondaryText))
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (activity.needsYou) NeedsYouTag()
                BasicText(
                    text = botRowPreview(lastWord, roster, mainName)
                        ?: bot.description.takeIf { it.isNotBlank() }
                        ?: "@" + bot.handle,
                    localize = false,
                    style = theme.subheadline.copy(color = theme.secondaryText),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * « Needs you », devant la dernière ligne d'une ligne: l'agent attend une réponse, une approbation
 * ou un mot de passe — le `NeedsYouTag` , son texte orange sur sa capsule d'orange.
 */
@Composable
private fun NeedsYouTag() {
    val theme = LocalOnyxTheme.current
    BasicText(
        text = "Needs you",
        style = theme.caption2.copy(color = theme.warning, fontWeight = FontWeight.SemiBold),
        maxLines = 1,
        modifier = Modifier
            .background(theme.warning.copy(alpha = 0.15f), RoundedCornerShape(50))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

/**
 * Le dernier mot du chat d'un bot, comme `BotMessages.rowPreview`: un message venu d'un
 * autre bot se dit « Scout: … », le compte rendu d'une tâche de fond se dit pour ce qu'il est, et
 * tout le reste se dit tel que le serveur le donne. Rien à dire: rien — la ligne retombe alors sur
 * la description du bot, puis sur sa poignée, comme le fait `botRow`.
 */
internal fun botRowPreview(preview: String?, roster: List<BotProfile>, mainName: String): String? {
    val text = preview?.takeIf { it.isNotBlank() } ?: return null
    if (text.startsWith("[IMPORTANT: Background process")) {
        return OnyxLocalization["A background task finished"]
    }
    val inbound = botInbound(text) ?: return text
    val bot = roster.firstOrNull { candidate ->
        candidate.name.equals(inbound.handle, ignoreCase = true) ||
            candidate.handle.equals(inbound.handle, ignoreCase = true) ||
            candidate.label(mainName).equals(inbound.name, ignoreCase = true)
    }
    return (bot?.label(mainName) ?: inbound.name) + ": " + inbound.body
}

/**
 * L'heure d'une ligne de bot: celle que le serveur donne pour son chat, sinon celle du chat que ce
 * téléphone connaît déjà — une réponse en cours ne la compte pas, elle, puisque le rond qui tourne
 * prend sa place.
 */
internal fun botRowLastActive(bot: BotProfile, chats: List<Conversation>): Long? =
    bot.lastActive ?: botRowChat(bot, chats)?.updatedAt

/**
 * Le dernier mot d'une ligne de bot: celui que le serveur donne pour son chat (`preview`), sinon
 * celui du chat que ce téléphone connaît — jamais la description du bot, qui n'est pas un message.
 */
internal fun botRowLastWord(bot: BotProfile, chats: List<Conversation>): String? =
    bot.preview?.takeIf { it.isNotBlank() } ?: botRowChat(bot, chats)?.messages?.lastOrNull()?.text

/** Le chat d'un bot parmi les conversations chargées ici, quand il y est. */
private fun botRowChat(bot: BotProfile, chats: List<Conversation>): Conversation? {
    val chatId = bot.chatId ?: return null
    return chats.firstOrNull { it.serverSessionId == chatId || it.id == chatId }
}

/** Un message venu d'un autre bot, comme l'`Inbound` de `BotMessages`: qui parle, et ce qu'il dit. */
internal data class BotInbound(val name: String, val handle: String?, val body: String)

/** `Message from 🤖 Scout (@scout): …` — et `Message from Scout (@scout@server): …`. */
private val BOT_MESSAGE = Regex(
    "^Message from (?:\uD83E\uDD16\\s*)?([^:\n(]{1,64}?)(?:\\s*\\(@([a-z0-9][a-z0-9_-]{0,63})" +
        "(?:@[A-Za-z0-9][A-Za-z0-9_-]{0,63})?\\))?:\\s*([\\s\\S]*)$",
)

/** La forme plus ancienne: `[Message from agent 'scout'] …`. */
private val BOT_AGENT_MESSAGE = Regex("^\\[Message from agent '([^']{1,64})'\\]\\s*([\\s\\S]*)$")

/** Ce qu'un message d'un bot nomme: son nom, sa poignée — quand il la donne —, et son corps. */
internal fun botInbound(text: String): BotInbound? {
    BOT_MESSAGE.matchEntire(text)?.let { match ->
        return BotInbound(
            name = match.groupValues[1].trim(),
            handle = match.groupValues[2].takeIf { it.isNotEmpty() },
            body = match.groupValues[3],
        )
    }
    BOT_AGENT_MESSAGE.matchEntire(text)?.let { match ->
        return BotInbound(name = match.groupValues[1], handle = null, body = match.groupValues[2])
    }
    return null
}

/** Ce que la tuile « Hidden » dit à VoiceOver (`hint: "Shows the N bots hidden from this list"`). */
internal fun hiddenHint(count: Int): String = "Shows the $count bots hidden from this list"

/**
 * Les visages superposés des membres d'un groupe (`FaceStack` ): les deux premiers, chacun
 * posé sur le fond de la page pour que le recouvrement reste net, dans la place du visage — les
 * noms des salles s'alignent donc sur ceux des bots.
 *
 * Chaque visage passe par [com.onyx.app.ui.design.BotLook]: le visage dessiné du bot quand il en
 * a un, sinon l'un des orbes que son nom donne — comme le `BotAvatar(face:)`.
 */
@Composable
private fun MemberFaces(members: List<BotProfile>, size: Dp = 24.dp) {
    val theme = LocalOnyxTheme.current
    Box(
        modifier = Modifier.size(width = BOT_ROW_FACE, height = BOT_ROW_FACE),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(-8.dp),
        ) {
            members.take(2).forEach { bot ->
                Box(
                    modifier = Modifier
                        .size(size)
                        .background(theme.pageColor, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    com.onyx.app.ui.design.BotLook(
                        name = bot.label(),
                        faceCode = bot.faceCode,
                        size = size - 2.dp,
                    )
                }
            }
        }
    }
}

/**
 * Une rangée de salle, comme le `groupRow`: les visages empilés de ses membres, son nom et
 * sa date — ou le rond qui tourne tant qu'un bot y travaille —, puis qui en fait partie, joint
 * comme `formatted(.list(type: .and))` (« Onyx, Scout, and Quill »), la marque « Needs you »
 * devant, celle d'Hermes Desktop pour une salle que le Bureau tient lui-même, et le point « pas
 * encore lu » dans la marge.
 */
@Composable
private fun GroupRow(
    group: GroupChat,
    muted: Boolean,
    activity: BotActivity = BotActivity(),
    /** La salle ouverte ici: sa rangée garde le fond teinté du `listRowBackground`. */
    isOpen: Boolean = false,
    onClick: () -> Unit,
    /** L'appui long de la rangée: son menu (`contextMenu` du `groupRow`). */
    onMenu: (() -> Unit)? = null,
) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isOpen) theme.accent.copy(alpha = 0.08f) else theme.pageColor)
            .onyxTappable(onLongPress = onMenu, onClick = onClick)
            .padding(start = BOT_ROW_START, end = 20.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BOT_ROW_GAP),
    ) {
        RowUnreadDot(shown = activity.unread)
        MemberFaces(members = group.members)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BasicText(
                    text = group.name,
                    localize = false,
                    style = theme.body.copy(color = theme.textColor, fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (muted) {
                    OnyxIcon(
                        symbol = com.onyx.app.ui.common.mutedMark,
                        tint = theme.secondaryText,
                        size = 13.dp,
                        contentDescription = "Muted",
                    )
                }
                // Un bot travaille dans la salle: le rond qui tourne à la place de la date.
                if (activity.working) {
                    RowSpinner()
                } else if (group.updatedAt != null) {
                    BasicText(text = rowTime(group.updatedAt), style = theme.caption.copy(color = theme.secondaryText))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                if (activity.needsYou) NeedsYouTag()
                // Une salle qu'Hermes Desktop tient lui-même.
                if (group.inDesktop) {
                    OnyxIcon(
                        symbol = Symbols.DesktopComputer,
                        tint = theme.secondaryText,
                        size = 13.dp,
                        contentDescription = "Runs in Hermes Desktop",
                    )
                }
                BasicText(
                    text = roomMembersLabel(group.members.map { it.label() }).ifEmpty {
                        memberCount(group.members.size)
                    },
                    localize = false,
                    style = theme.subheadline.copy(color = theme.secondaryText),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** « 1 member », « 3 members »: le compte des membres d'un groupe qui n'en nomme aucun. */
private fun memberCount(count: Int): String =
    if (count == 1) "1 member" else "$count members"

/**
 * Une ligne qui fait agir (New Group Chat, New Bot, Hidden): le symbole teinté tient la place du
 * visage, si bien que son titre s'aligne sur les noms des lignes — la géométrie du `sectionAction`
 * , dessinée comme une ligne.
 */
@Composable
private fun ActionRow(title: String, symbol: Int, hint: String? = null, onClick: () -> Unit) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onyxTappable(onClick = onClick)
            // Le libellé français est plus long que l'anglais — « Laisser les bots discuter
            // entre eux » mesure 255 dp —, et les 20 dp de marge droite le faisaient passer sur
            // deux lignes dans la colonne de 340 dp, où la rangée grandissait alors au milieu
            // de la liste. La marge droite lui rend la place de tenir sur une ligne.
            .padding(start = BOT_ROW_START, end = 8.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BOT_ROW_GAP),
    ) {
        // La place du point du non-lu, puis celle du visage: le titre tombe où les noms tombent.
        Spacer(modifier = Modifier.size(width = BOT_ROW_DOT, height = BOT_ROW_FACE))
        Box(modifier = Modifier.size(BOT_ROW_FACE), contentAlignment = Alignment.Center) {
            OnyxIcon(symbol = symbol, tint = theme.accent, size = 20.dp, contentDescription = hint)
        }
        BasicText(
            text = title,
            style = theme.body.copy(color = theme.textColor),
            // Une ligne, comme les autres rangées de service: la hauteur de la rangée reste
            // celle de ses voisines.
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Une conversation: titre, dernier mot, heure, et « Needs you » s'il attend une réponse. */
@Composable
private fun ConversationRow(
    conversation: Conversation,
    isOpen: Boolean,
    selecting: Boolean,
    checked: Boolean,
    onToggle: () -> Unit,
    onClick: (Rect?) -> Unit,
    onDelete: () -> Unit,
    muted: Boolean,
    onMenu: () -> Unit,
    /**
     * La marque de lecture de la conversation (`unreadMarks`): `0.0` marquée non lue à la main,
     * sinon l'heure jusqu'à laquelle elle a été vue; `null`, jamais marquée.
     */
    mark: Double? = null,
) {
    val theme = LocalOnyxTheme.current
    var bounds by remember { mutableStateOf<Rect?>(null) }
    val last = conversation.messages.lastOrNull()
    val working = last?.isStreaming == true
    // « Non lu »: la conversation ouverte et celle où un tour s'écrit ne le sont jamais; une
    // ligne marquée non lue à la main l'est quoi qu'elle ait fait depuis, et une ligne marquée lue
    // ne se rallume qu'à une réponse venue après (`ChatActivity.unread`).
    val unread = conversationUnread(
        lastSpokenAt = conversation.messages.lastOrNull { it.role == Role.ASSISTANT }?.createdAt,
        seen = mark,
        isOpen = isOpen,
        busy = working,
        muted = muted,
        fallback = last?.role == Role.ASSISTANT,
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isOpen) theme.accent.copy(alpha = 0.08f) else theme.pageColor)
            .onGloballyPositioned { bounds = it.boundsInRoot() }
            // Un appui long ouvre le menu de la rangée, comme le `contextMenu`: c'est
            // aussi par là qu'on regarde « Usage & cost ».
            .onyxTappable(onLongPress = onMenu) { onClick(bounds) }
            .padding(start = 26.dp, end = 20.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Le point « non lu », dans la marge gauche, comme `unreadDot`.
        if (unread && !selecting) {
            Box(
                modifier = Modifier
                    .padding(end = 9.dp)
                    .size(9.dp)
                    .background(theme.accent, androidx.compose.foundation.shape.CircleShape),
            )
        }
        if (selecting) {
            OnyxIcon(
                symbol = if (checked) Symbols.CheckmarkCircle else Symbols.Circle,
                tint = if (checked) theme.accent else theme.tertiaryText,
                size = 20.dp,
                modifier = Modifier.padding(end = 10.dp).onyxClickable(onClick = onToggle),
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // L'épingle devant le titre, comme `pin.fill`.
                if (conversation.pinned) {
                    OnyxIcon(Symbols.Pin, tint = theme.warning, size = 11.dp)
                }
                BasicText(
                    text = conversation.title,
                    localize = false,
                    style = theme.body.copy(color = theme.textColor, fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (muted) {
                    OnyxIcon(
                        symbol = com.onyx.app.ui.common.mutedMark,
                        tint = theme.secondaryText,
                        size = 11.dp,
                        contentDescription = "Muted",
                    )
                }
                // Au travail: le rond qui tourne tient la place de l'heure.
                if (working) {
                    RowSpinner()
                } else {
                    BasicText(
                        text = rowTime(conversation.updatedAt),
                        style = theme.caption.copy(color = theme.secondaryText),
                    )
                }
            }
            BasicText(
                // L'aperçu du serveur, sinon le dernier mot du fil, sinon rien.
                text = last?.text?.replace('\n', ' ')?.take(110).orEmpty(),
                localize = false,
                style = theme.subheadline.copy(color = theme.secondaryText),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Le menu du serveur, au-dessus de la liste (`serverMenu` ): l'icône et le nom du serveur où
 * l'app est — ou « All Servers », avec le rappel de où les conversations neuves commencent.
 * L'appui l'ouvre ([OnyxMenu]); l'entrée est mise en avant par `active` (le motif « … » est
 * dessiné par l'appelant).
 */
@Composable
private fun ServerMenuBar(
    allServers: Boolean,
    activeName: String,
    onOpenMenu: () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .onyxClickable(onClick = onOpenMenu)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        OnyxIcon(
            symbol = if (allServers) Symbols.SquareStack3dUp else Symbols.ServerRack,
            tint = theme.secondaryText,
            size = 15.dp,
        )
        BasicText(
            text = if (allServers) "All Servers" else activeName,
            localize = !allServers,
            style = theme.subheadline.copy(color = theme.textColor, fontWeight = FontWeight.Medium),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (allServers && activeName.isNotEmpty()) {
            // « · new chats on <serveur> »: le nom du serveur s'insère, jamais traduit.
            BasicText(
                text = OnyxLocalization.format("· new chats on %1\$s", activeName),
                localize = false,
                style = theme.subheadline.copy(color = theme.secondaryText),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }
        OnyxIcon(Symbols.ChevronUpDown, tint = theme.tertiaryText, size = 11.dp)
    }
}

/**
 * La rangée d'un autre serveur, avec « All Servers »: elle porte le nom de son serveur, ne se
 * glisse pas et ne se sélectionne pas, et s'ouvre **sur son serveur** — le `remoteButton`.
 * Rien de ce qu'elle offre ne touche donc le serveur où l'app est.
 */
@Composable
private fun RemoteConversationRow(
    row: AllServerRow,
    open: Boolean,
    onClick: () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    val conversation = row.conversation
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (open) theme.accent.copy(alpha = 0.08f) else theme.pageColor)
            .onyxClickable(onClick = onClick)
            .padding(start = 26.dp, end = 20.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (conversation.pinned) {
                    OnyxIcon(Symbols.Pin, tint = theme.warning, size = 11.dp)
                }
                BasicText(
                    text = conversation.title,
                    localize = false,
                    style = theme.body.copy(color = theme.textColor, fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                // L'étiquette du serveur: le `Label(server.name, systemImage: "server.rack")`.
                ServerTagCapsule(name = row.serverName.orEmpty())
                BasicText(
                    text = rowTime(conversation.updatedAt),
                    style = theme.caption.copy(color = theme.secondaryText),
                )
            }
            BasicText(
                text = conversation.messages.lastOrNull()?.text?.replace('\n', ' ')?.take(110).orEmpty(),
                localize = false,
                style = theme.subheadline.copy(color = theme.secondaryText),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** L'étiquette d'un serveur sur une rangée: un gris léger, une capsule, le nom jamais traduit. */
@Composable
private fun ServerTagCapsule(name: String) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = Modifier
            .background(theme.fill, RoundedCornerShape(50))
            .padding(horizontal = 6.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        OnyxIcon(Symbols.ServerRack, tint = theme.secondaryText, size = 10.dp)
        BasicText(
            text = name,
            localize = false,
            style = theme.caption2.copy(color = theme.secondaryText, fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Un rond qui tourne, discret, à la place de l'heure d'une session au travail. */
@Composable
private fun RowSpinner() {
    val theme = LocalOnyxTheme.current
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "rowSpinner")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(900),
        ),
        label = "rowSpinnerAngle",
    )
    androidx.compose.foundation.Canvas(modifier = Modifier.size(13.dp).rotate(angle)) {
        drawArc(
            color = theme.secondaryText.copy(alpha = 0.25f),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f),
        )
        drawArc(
            color = theme.secondaryText,
            startAngle = 0f,
            sweepAngle = 90f,
            useCenter = false,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f),
        )
    }
}

/**
 * L'état vide de la liste, comme le `ContentUnavailableView`: un symbole, un titre, et
 * une ligne qui dit ce qui y apparaîtra.
 */
@Composable
private fun EmptyState(symbol: Int, title: String, description: String? = null) {
    val theme = LocalOnyxTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        OnyxIcon(symbol = symbol, tint = theme.tertiaryText, size = 44.dp)
        BasicText(
            text = title,
            style = theme.headline.copy(color = theme.textColor, textAlign = TextAlign.Center),
        )
        description?.let {
            BasicText(
                text = it,
                style = theme.subheadline.copy(color = theme.secondaryText, textAlign = TextAlign.Center),
            )
        }
    }
}

/**
 * L'en-tête repliable (`foldingHeader`): son titre, le compte de ce qu'elle contient, et
 * un chevron qui plie ou déplie la section. Une action à droite (Edit) garde sa place.
 */
@Composable
private fun FoldingHeader(
    title: String,
    count: Int?,
    open: Boolean,
    onToggle: () -> Unit,
    trailing: String? = null,
    onTrailing: (() -> Unit)? = null,
) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onyxTappable(onClick = onToggle)
            .padding(start = 20.dp, end = 16.dp, top = 18.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BasicText(
            text = title,
            style = androidx.compose.ui.text.TextStyle(
                color = theme.textColor,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
        Box(modifier = Modifier.weight(1f))
        if (trailing != null && onTrailing != null) {
            BasicText(
                text = trailing,
                style = theme.footnote.copy(color = theme.accent),
                modifier = Modifier.onyxClickable(onClick = onTrailing),
            )
        }
        OnyxIcon(
            symbol = if (open) Symbols.ChevronDown else Symbols.ChevronRight,
            tint = theme.tertiaryText,
            size = 12.dp,
        )
    }
}

/** Une pastille d'activité: « Working » ou « Needs you », sous le nom d'un bot. */
@Composable
private fun ActivityBadge(text: String, color: Color) {
    BasicText(
        text = text,
        style = LocalOnyxTheme.current.caption2.copy(color = color, fontWeight = FontWeight.SemiBold),
        modifier = Modifier
            .background(color.copy(alpha = 0.16f), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

/**
 * La section « Projects »: les dossiers de travail du serveur, chacun avec son icône de
 * dossier (orange pour le dossier personnel), son compte de conversations et sa dernière activité.
 * Une rangée ouvre les sessions du projet.
 */
@Composable
private fun ServerProjectsSection(
    projects: List<ServerProject>,
    open: Boolean,
    onToggleOpen: () -> Unit,
    onOpen: (ServerProject) -> Unit,
) {
    val theme = LocalOnyxTheme.current
    FoldingHeader(title = "Projects", count = projects.size, open = open, onToggle = onToggleOpen)
    if (!open) return
    OnyxFormSection {
        projects.forEachIndexed { index, project ->
            if (index > 0) OnyxRowDivider()
            OnyxRow(onClick = { onOpen(project) }) {
                OnyxIcon(
                    symbol = if (project.isHome) Symbols.HouseFill else Symbols.FolderFill,
                    tint = if (project.isHome) theme.warning else theme.accent,
                    size = 22.dp,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    BasicText(
                        text = project.label,
                        localize = false,
                        style = theme.body.copy(color = theme.textColor),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    BasicText(
                        text = listOfNotNull(
                            if (project.sessionCount == 1) "1 conversation" else "${project.sessionCount} conversations",
                            project.lastActive?.let { rowTime(it) },
                        ).joinToString(" · "),
                        localize = false,
                        style = theme.caption.copy(color = theme.secondaryText),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * La section « Projects »: les conversations réunies par serveur d'origine (`serverId`), avec un
 * en-tête par projet — son nom, son nombre de conversations, sa ligne de résumé, et le chevron qui
 * le replie — la façon dont `hermes serve` range ses sessions par dossier de travail.
 */
@Composable
private fun ProjectsSection(
    projects: Map<String, List<Conversation>>,
    open: Boolean,
    onToggleOpen: () -> Unit,
    currentId: String?,
    onOpen: (Conversation, Rect?) -> Unit,
) {
    FoldingHeader(title = "Projects", count = projects.size, open = open, onToggle = onToggleOpen)
    if (!open) return
    // Chaque projet se replie pour lui-même, comme un dossier; le premier est ouvert.
    var collapsed by remember { mutableStateOf(setOf<String>()) }
    projects.forEach { (serverId, rows) ->
        val isOpen = serverId !in collapsed
        // Le résumé du projet: la conversation la plus récente et son dernier message.
        val latest = rows.maxByOrNull { it.updatedAt }
        ProjectHeader(
            name = serverId,
            count = rows.size,
            lastMessage = latest?.messages?.lastOrNull()?.text,
            lastTimestamp = latest?.updatedAt,
            open = isOpen,
            onToggle = {
                collapsed = if (isOpen) collapsed + serverId else collapsed - serverId
            },
        )
        if (isOpen) {
            // La clé composite, comme partout: le groupe porte un serveur, la rangée le reprend.
            val openHere = currentId?.let { AllServers.key(serverId, it) }
            rows.forEach { conversation ->
                ProjectChatRow(
                    conversation = conversation,
                    isOpen = AllServers.key(serverId, conversation.id) == openHere,
                    onClick = { onOpen(conversation, null) },
                )
            }
        }
    }
}

/**
 * L'en-tête d'un projet: son nom (le serveur d'origine), son nombre de conversations, la ligne de
 * résumé (dernier message et date relative), et le chevron qui replie ses conversations.
 */
@Composable
private fun ProjectHeader(
    name: String,
    count: Int,
    lastMessage: String?,
    lastTimestamp: Long?,
    open: Boolean,
    onToggle: () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    // La ligne de résumé: le dernier mot dit, puis la date relative; rien à dire s'il n'y a rien.
    val summary = lastMessage?.replace('\n', ' ')?.trim().orEmpty()
    val summaryLine = listOfNotNull(
        summary.takeIf { it.isNotEmpty() }?.take(80),
        lastTimestamp?.let { rowTime(it) },
    ).joinToString(" · ").ifEmpty { "No conversations yet" }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onyxTappable(onClick = onToggle)
            .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OnyxIcon(Symbols.Folder, tint = theme.secondaryText, size = 14.dp)
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicText(
                    text = name,
                    localize = false,
                    style = theme.subheadline.copy(color = theme.textColor, fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                BasicText(text = conversationCount(count), style = theme.caption.copy(color = theme.secondaryText))
            }
            BasicText(
                text = summaryLine,
                localize = summaryLine == "No conversations yet",
                style = theme.caption.copy(color = theme.secondaryText),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        OnyxIcon(
            symbol = if (open) Symbols.ChevronDown else Symbols.ChevronRight,
            tint = theme.tertiaryText,
            size = 12.dp,
        )
    }
}

/** Une conversation dans un projet: son titre, son heure, et « Needs you » s'il attend. */
@Composable
private fun ProjectChatRow(conversation: Conversation, isOpen: Boolean, onClick: () -> Unit) {
    val theme = LocalOnyxTheme.current
    val last = conversation.messages.lastOrNull()
    val needsYou = last != null && last.role == Role.USER && !last.isStreaming
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isOpen) theme.accent.copy(alpha = 0.08f) else theme.pageColor)
            .onyxTappable(onClick = onClick)
            .padding(start = 36.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (needsYou) ActivityBadge(text = "Needs you", color = theme.warning)
        BasicText(
            text = conversation.title,
            localize = false,
            style = theme.body.copy(color = theme.textColor),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        BasicText(
            text = rowTime(conversation.updatedAt),
            style = theme.caption.copy(color = theme.secondaryText),
        )
    }
}

/** « 1 conversation », « 3 conversations »: le compte d'un projet. */
private fun conversationCount(count: Int): String =
    if (count == 1) "1 conversation" else "$count conversations"

/**
 * L'activité d'un bot, déduite des conversations déjà chargées: une réponse en cours
 * (« Working »), ou un dernier mot de l'utilisateur resté sans réponse (« Needs you »).
 *
 * Le seul lien que le modèle expose entre un bot et ses conversations est l'identifiant de son
 * chat (`BotProfile.chatId`, le `canonical_session` du serveur); sans correspondance, le bot
 * reste au repos — aucune donnée n'est demandée au serveur pour ces pastilles.
 */
internal fun botActivity(bot: BotProfile, conversations: List<Conversation>): BotActivity {
    val chatId = bot.chatId ?: return BotActivity()
    val mine = conversations.filter { it.serverSessionId == chatId || it.id == chatId }
    val working = mine.any { it.messages.lastOrNull()?.isStreaming == true }
    val needsYou = mine.any {
        val last = it.messages.lastOrNull()
        last != null && last.role == Role.USER && !last.isStreaming
    }
    val unread = !working && mine.any { it.messages.lastOrNull()?.role == Role.ASSISTANT }
    return BotActivity(working = working, needsYou = needsYou, unread = unread)
}

/** Ce qu'un bot fait, tel que sa pastille et son visage le montrent. */
data class BotActivity(
    val working: Boolean = false,
    val needsYou: Boolean = false,
    val unread: Boolean = false,
)

/**
 * Le non-lu d'une conversation, comme `ChatActivity.unread`:
 * ni celle qu'on regarde, ni celle où un tour s'écrit, ni une conversation tue. Marquée non lue à
 * la main (`seen == 0`), elle l'est quoi qu'elle ait fait depuis; vue jusqu'à [seen], une réponse
 * venue après la rallume (l'écart d'horloge , [CLOCK_SLACK], toléré). Sans marque — le premier
 * regard sur la liste, ou un fil venu d'ailleurs —, c'est le fil qui parle ([fallback]: le dernier
 * mot est celui de l'agent).
 */
internal fun conversationUnread(
    lastSpokenAt: Long?,
    seen: Double?,
    isOpen: Boolean,
    busy: Boolean,
    muted: Boolean,
    fallback: Boolean,
    slack: Double = CLOCK_SLACK,
): Boolean {
    if (isOpen || busy || muted) return false
    if (seen == null) return fallback
    if (seen == 0.0) return true
    return lastSpokenAt != null && lastSpokenAt / 1000.0 > seen + slack
}

/**
 * « Today, 9:41 », « Yesterday », « Monday », « 12 Mar »: l'heure d'une rangée.
 *
 * Comme `DateGroup.rowTime`: l'heure du jour, « Yesterday » pour la veille, le nom du jour pour
 * la semaine qui vient de passer, puis le jour et le mois — et l'année seulement quand elle change.
 */
internal fun rowTime(timestamp: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = timestamp }
    val sameYear = now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
    val sameDay = sameYear && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    val yesterday = sameYear && now.get(Calendar.DAY_OF_YEAR) - 1 == then.get(Calendar.DAY_OF_YEAR)
    // Les jours 2 à 6 en arrière, hors hier déjà nommé: on dit le jour de la semaine (« Monday »).
    val withinWeek = sameYear && now.get(Calendar.DAY_OF_YEAR) - then.get(Calendar.DAY_OF_YEAR) in 2..6
    return when {
        sameDay -> java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT, Locale.getDefault()).format(Date(timestamp))
        yesterday -> OnyxLocalization["Yesterday"]
        withinWeek -> SimpleDateFormat("EEE", Locale.getDefault()).format(Date(timestamp))
        sameYear -> SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(timestamp))
        else -> SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(timestamp))
    }
}

/** L'ordre de la liste, comme le menu de tri. */
private enum class Sort { RECENT, OLDEST, TITLE }

/** Où la recherche regarde. */
private enum class SearchScope { BOTH, TITLES, MESSAGES }

/**
 * Ce qu'une conversation contient, en un coup d'œil (`ConversationInfo`).
 *
 * Le titre, les dates, le serveur, les tours, les tokens et les outils utilisés: de quoi savoir
 * ce qu'on va rouvrir avant de le rouvrir.
 */
@Composable
private fun ConversationInfoSheet(
    conversation: Conversation,
    onClose: () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    val userTurns = conversation.messages.count { it.role == Role.USER }
    val assistantTurns = conversation.messages.count { it.role == Role.ASSISTANT }
    val tools = conversation.messages.flatMap { it.tools }.map { it.name }.distinct()
    val tokens = conversation.messages.mapNotNull { it.usage?.total }.sum()

    androidx.compose.ui.window.Dialog(onDismissRequest = onClose) {
        Column(
            modifier = Modifier
                .background(theme.surfaceColor, RoundedCornerShape(14.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BasicText(
                text = conversation.title,
                localize = false,
                style = theme.headline.copy(color = theme.textColor),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            InfoLine("Updated", rowTime(conversation.updatedAt))
            InfoLine("Created", rowTime(conversation.createdAt))
            if (conversation.serverId.isNotEmpty()) InfoLine("Server", conversation.serverId, verbatimValue = true)
            InfoLine("Your turns", userTurns.toString())
            InfoLine("Replies", assistantTurns.toString())
            if (tools.isNotEmpty()) InfoLine("Tools", tools.joinToString(", "), verbatimValue = true)
            if (tokens > 0) InfoLine("Tokens", tokens.toString())
            if (conversation.pinned) InfoLine("Pinned", "Yes")
            if (conversation.archived) InfoLine("Archived", "Yes")
            conversation.messages.lastOrNull()?.text?.takeIf { it.isNotBlank() }?.let { last ->
                Spacer(Modifier.height(8.dp))
                BasicText(
                    text = last.take(160),
                    localize = false,
                    style = theme.footnote.copy(color = theme.secondaryText),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(10.dp))
            BasicText(
                text = "Close",
                style = theme.body.copy(color = theme.accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .onyxClickable(onClick = onClose)
                    .padding(vertical = 4.dp),
            )
        }
    }
}

/**
 * La feuille « New Group Chat », comme `NewGroupChatView`.
 *
 * Les bots de la liste, cochés un à un — deux à six —, le nom de la salle, puis « Create »: la
 * salle est faite (sur le serveur, ou gardée ici), et s'ouvre avec ces membres et ce nom. Un échec
 * reste dans la feuille, sous les lignes, au lieu de fermer sur une salle qui n'existe pas.
 */
@Composable
private fun NewGroupSheet(
    bots: List<BotProfile>,
    onCreate: suspend (Set<String>, String) -> GroupCreation,
    onCreated: (GroupChat) -> Unit,
    onClose: () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    val scope = rememberCoroutineScope()
    var picked by remember { mutableStateOf<Set<String>>(emptySet()) }
    var name by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }

    androidx.compose.ui.window.Dialog(onDismissRequest = { if (!creating) onClose() }) {
        Column(
            modifier = Modifier
                .background(theme.surfaceColor, RoundedCornerShape(14.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            BasicText(
                text = "New Group Chat",
                style = theme.headline.copy(color = theme.textColor),
            )
            BasicText(
                text = "Two to six. A message goes to all of them, or with an @tag to one; they " +
                    "answer one after another, and may ask each other.",
                style = theme.footnote.copy(color = theme.secondaryText),
            )
            Spacer(Modifier.height(6.dp))
            bots.forEach { bot ->
                val checked = bot.name in picked
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onyxTappable {
                            // Six au plus, comme le `picked.count < 6`.
                            picked = when {
                                checked -> picked - bot.name
                                picked.size < 6 -> picked + bot.name
                                else -> picked
                            }
                        }
                        .padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    com.onyx.app.ui.design.BotLook(name = bot.name, faceCode = bot.faceCode, size = 30.dp)
                    BasicText(
                        text = bot.label(),
                        localize = false,
                        style = theme.body.copy(color = theme.textColor),
                        modifier = Modifier.weight(1f),
                    )
                    if (checked) {
                        OnyxIcon(symbol = Symbols.Checkmark, tint = theme.accent, size = 15.dp)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            // `Section("Name")`: la place du nom, la liste des bots choisie par défaut.
            BasicText(
                text = "Name",
                style = theme.footnote.copy(color = theme.secondaryText),
            )
            androidx.compose.foundation.text.BasicTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = theme.textColor, fontSize = 17.sp),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(theme.accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            )
            // Ce qui a empêché la salle: montré ici, au-dessus des boutons, comme le `problem`
            // — la feuille reste ouverte et rien n'a été gardé.
            problem?.let { message ->
                Row(
                    modifier = Modifier.padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    OnyxIcon(symbol = Symbols.ExclamationTriangle, tint = theme.warning, size = 13.dp)
                    BasicText(
                        text = message,
                        style = theme.footnote.copy(color = theme.warning),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                BasicText(
                    text = "Cancel",
                    style = theme.body.copy(color = theme.secondaryText),
                    modifier = Modifier.onyxClickable(enabled = !creating, onClick = onClose),
                )
                BasicText(
                    text = "Create",
                    style = theme.body.copy(
                        color = if (picked.size < 2 || creating) theme.tertiaryText else theme.accent,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    ),
                    modifier = Modifier.onyxClickable(enabled = picked.size >= 2 && !creating) {
                        // Une seule création à la fois: la salle faite ferme la feuille, l'échec
                        // reste ici avec ce qui l'a dit.
                        creating = true
                        problem = null
                        scope.launch {
                            when (val made = onCreate(picked, name.trim())) {
                                is GroupCreation.Made -> onCreated(made.room)
                                is GroupCreation.Failed -> {
                                    problem = made.problem
                                    creating = false
                                }
                            }
                        }
                    },
                )
            }
        }
    }
}

/** Une ligne « libellé → valeur » de la feuille d'informations. */
@Composable
private fun InfoLine(label: String, value: String, verbatimValue: Boolean = false) {
    val theme = LocalOnyxTheme.current
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        BasicText(
            text = label,
            style = theme.footnote.copy(color = theme.secondaryText),
            modifier = Modifier.weight(1f),
        )
        BasicText(
            text = value,
            localize = !verbatimValue,
            style = theme.footnote.copy(color = theme.textColor),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Le pied de la liste: combien de conversations, combien d'épinglées, combien d'archivées.
 *
 * le montre sous la dernière section; c'est le même compte, en une ligne discrète.
 */
@Composable
private fun CollectionSummary(total: Int, pinned: Int, archived: Int) {
    if (total == 0) return
    val theme = LocalOnyxTheme.current
    val parts = buildList {
        add(if (total == 1) "1 conversation" else "$total conversations")
        if (pinned > 0) add(if (pinned == 1) "1 pinned" else "$pinned pinned")
        if (archived > 0) add(if (archived == 1) "1 archived" else "$archived archived")
    }
    BasicText(
        text = parts.joinToString(" · "),
        style = theme.caption.copy(color = theme.tertiaryText, textAlign = TextAlign.Center),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 4.dp),
    )
}

/**
 * Les actions d'une rangée, révélées en la faisant glisser vers la gauche.
 *
 * les montre ainsi: épingler, archiver, supprimer — chacune un pavé **arrondi**, large de
 * 84 dp, à 8 dp l'une de l'autre, à 12 dp des bords, et hautes de la rangée moins 12 dp (6 dp en
 * haut et en bas). Le symbole se pose **au-dessus** du libellé, l'un et l'autre en blanc sur un
 * fond de couleur pleine: l'accent pour épingler, un gris système pour archiver, le rouge
 * destructif pour supprimer.
 *
 * La rangée suit le doigt, se découpe au même arrondi dès qu'elle s'écarte (aucun coin carré ne
 * dépasse des pavés) et garde son fond par-dessus les actions: fermée, elle les cache
 * entièrement. Un tap à côté ou un défilement la referme; un glissement franc — au-delà du volet
 * — déclenche la première action, comme l'`allowsFullSwipe`.
 */
@Composable
private fun SwipeableRowActions(
    conversation: Conversation,
    /** Vrai pour la seule rangée dont le volet est ouvert; l'état appartient à la liste. */
    open: Boolean,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    onPin: (Conversation, Boolean) -> Unit,
    onArchive: (Conversation, Boolean) -> Unit,
    onDelete: (Conversation) -> Unit,
    content: @Composable () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    val scope = rememberCoroutineScope()
    // La largeur du volet, mesurée une fois: le contenu s'écarte d'autant, ni plus ni moins.
    var actionsWidth by remember { mutableIntStateOf(0) }
    // La pose du contenu: 0 fermé, négatif à mesure que le volet se découvre. Elle suit le doigt
    // pendant le glissement, puis rejoint sa pose.
    val offset = remember { Animatable(0f) }
    var dragging by remember { mutableStateOf(false) }

    // La liste a ouvert ou refermé la rangée (une autre s'ouvre, un tap à côté, un défilement):
    // celle-ci glisse jusqu'à sa pose.
    LaunchedEffect(open, actionsWidth) {
        if (!dragging && actionsWidth > 0) {
            offset.animateTo(if (open) -actionsWidth.toFloat() else 0f, tween(durationMillis = 220))
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        // Les actions vivent derrière le contenu: alignées au bord droit, 12 dp de marge autour du
        // groupe, 8 dp entre elles, et 6 dp en haut et en bas.
        Box(
            modifier = Modifier.matchParentSize(),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Row(
                modifier = Modifier
                    .onSizeChanged { actionsWidth = it.width }
                    .padding(horizontal = SwipeActionMargin, vertical = 6.dp)
                    .alpha(if (open || dragging) 1f else 0f),
                horizontalArrangement = Arrangement.spacedBy(SwipeActionGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SwipeAction(
                    label = if (conversation.pinned) "Unpin" else "Pin",
                    symbol = Symbols.Pin,
                    tint = theme.accent,
                ) { onPin(conversation, !conversation.pinned); onClose() }
                SwipeAction(
                    label = if (conversation.archived) "Unarchive" else "Archive",
                    symbol = Symbols.ArchiveBox,
                    tint = SwipeActionGray,
                ) { onArchive(conversation, !conversation.archived); onClose() }
                SwipeAction(label = "Delete", symbol = Symbols.Trash, tint = theme.error) {
                    onDelete(conversation)
                    onClose()
                }
            }
        }
        Box(
            modifier = Modifier
                // Le contenu quitte le volet exactement: son horodatage reste dans la rangée,
                // jamais par-dessus une action. Dès qu'il s'écarte, il se découpe au même arrondi
                // que les pavés — aucun coin carré ne dépasse.
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .graphicsLayer {
                    clip = offset.value < -0.5f
                    shape = RoundedCornerShape(SwipeActionCorner)
                }
                .pointerInput(conversation.id) {
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragStart = {
                            dragging = true
                            total = 0f
                        },
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            total += amount
                            val next = (offset.value + amount)
                                .coerceIn(-(actionsWidth.toFloat() + SwipeFullSlack.toPx()), 0f)
                            scope.launch { offset.snapTo(next) }
                        },
                        onDragEnd = {
                            dragging = false
                            // Un glissement franc, plus loin que le volet d'un cran, déclenche la
                            // première action (`allowsFullSwipe`), arrondis conservés.
                            val full = actionsWidth.toFloat() + SwipeFullGesture.toPx()
                            val threshold = SwipeOpenThreshold.toPx()
                            val settleOpen = total <= -threshold ||
                                (total > -threshold && offset.value <= -actionsWidth / 2f)
                            when {
                                total <= -full -> {
                                    scope.launch { offset.animateTo(0f, tween(durationMillis = 200)) }
                                    onPin(conversation, !conversation.pinned)
                                    onClose()
                                }
                                settleOpen -> onOpen()
                                else -> onClose()
                            }
                        },
                        onDragCancel = {
                            dragging = false
                            onClose()
                        },
                    )
                },
        ) {
            content()
        }
    }
}

/** Une action du volet: une icône au-dessus de son libellé, en blanc, sur un pavé arrondi plein. */
@Composable
private fun SwipeAction(
    label: String,
    symbol: Int,
    tint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    Column(
        modifier = Modifier
            .width(SwipeActionWidth)
            .fillMaxHeight()
            .clip(RoundedCornerShape(SwipeActionCorner))
            .background(tint)
            .onyxClickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        OnyxIcon(symbol = symbol, tint = Color.White, size = 18.dp, contentDescription = label)
        Spacer(Modifier.height(4.dp))
        BasicText(
            text = label,
            style = theme.caption.copy(color = Color.White, fontWeight = FontWeight.Medium),
            maxLines = 1,
        )
    }
}

/** La largeur d'un pavé d'action: 76–88 dp chez , 84 ici. */
private val SwipeActionWidth = 84.dp
/** L'espace entre deux pavés. */
private val SwipeActionGap = 8.dp
/** La marge du groupe avec les bords: aucun pavé ne touche l'écran ni la rangée voisine. */
private val SwipeActionMargin = 12.dp
/** Le rayon continu des pavés — et de la rangée qui s'écarte. */
private val SwipeActionCorner = 16.dp
/** Le seuil d'un glissement qui ouvre le volet. */
private val SwipeOpenThreshold = 30.dp
/** Ce qu'un glissement doit dépasser le volet pour déclencher la première action. */
private val SwipeFullGesture = 60.dp
/** Jusqu'où le volet peut suivre le doigt au-delà de sa largeur. */
private val SwipeFullSlack = 120.dp
/** Le gris d'« Archiver »: le `.gray` , plein, pour un libellé blanc lisible. */
private val SwipeActionGray = Color(0xFF8E8E93)

/** Le grand cercle de verre de la barre: le gear de « Conversations ». */
@Composable
private fun BigCircleButton(symbol: Int, label: String, onClick: () -> Unit) {
    val theme = LocalOnyxTheme.current
    Box(
        modifier = Modifier
            .size(52.dp)
            .glassSurface(androidx.compose.foundation.shape.CircleShape)
            .onyxClickable(pressedScale = 0.94f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        OnyxIcon(symbol = symbol, tint = theme.textColor, size = 24.dp, contentDescription = label)
    }
}

/** Une action de la capsule de verre: son icône seule, dans une zone de 44. */
@Composable
private fun CapsuleAction(symbol: Int, label: String, onClick: () -> Unit) {
    val theme = LocalOnyxTheme.current
    Box(
        modifier = Modifier
            .size(44.dp)
            .onyxClickable(pressedScale = 0.92f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        OnyxIcon(symbol = symbol, tint = theme.textColor, size = 22.dp, contentDescription = label)
    }
}
