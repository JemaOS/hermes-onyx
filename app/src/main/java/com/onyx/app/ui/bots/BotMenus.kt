package com.onyx.app.ui.bots

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect
import com.onyx.app.data.model.serve.BotProfile
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.common.Mutes
import com.onyx.app.ui.common.muteSubmenuItems
import com.onyx.app.ui.conversations.GroupChat
import com.onyx.app.ui.design.OnyxMenu
import com.onyx.app.ui.design.LucideMenuIcons
import com.onyx.app.ui.design.MenuItem
import com.onyx.app.ui.design.Symbols

/**
 * Ce qu'une tuile de bot ou une salle propose quand on l'appuie longuement.
 *
 * **Deux blocs, dans cet ordre.** D'abord **les neuf entrées de la référence**, dans *son* ordre :
 * `BotsRoster.kt:444-454` (son menu d'une carte de bot, tel qu'il s'ouvre à l'appui long). Puis les
 * entrées **propres à Onyx**, celles de la source de référence de la maison
 * (`botMenu(_:unread:isOpen:)` de `ConversationsView:700`, le `contextMenu` du `groupRow`
 * à `:817`) : elles ne bougent pas, et aucune n'est retirée.
 */
internal enum class BotMenuAction {
    // ————— Les neuf entrées de la référence, dans son ordre (`BotsRoster.kt:444-454`) —————

    /** « Épingler en haut » / « Désépingler » (`Lucide.Pin` / `PinOff`) : le bot passe en tête. */
    Pin,

    /** « Masquer » / « Afficher » (`Lucide.EyeOff` / `Eye`), sans le profil principal. */
    Hide,

    /** « Ouvrir la dernière session » (`Lucide.History`) : la plus récente, hors son chat. */
    OpenRecent,

    /** « Nouvelle conversation avec <le nom du bot> » (`Lucide.MessageSquarePlus`). */
    NewChat,

    /** « Routines » (`Lucide.CalendarClock`) : les tâches planifiées du bot. */
    Routines,

    /** « Repartir à zéro » (`Lucide.RotateCcw`) : son chat archivé, une conversation neuve. */
    StartFresh,

    /** « Modifier » (`Lucide.Pencil`) : l'éditeur du bot. */
    EditBot,

    /** « Dupliquer » (`Lucide.Copy`) : un second bot, sa configuration copiée. */
    Duplicate,

    /** « Supprimer » (`Lucide.Trash2`, destructrice) : le profil s'en va, après confirmation. */
    Delete,

    // ————— Les entrées propres à Onyx, sous ce bloc, dans leur ordre —————

    /** « Mark as Read » (`envelope.open`), le bot ou la salle ayant quelque chose de neuf. */
    MarkRead,

    /** « Mark as Unread » (`envelope.badge`). */
    MarkUnread,

    /** « Voice… » : la voix d'un bot, sans le profil principal (`!bot.isDefault`). */
    Voice,

    /** Le silence (`MuteMenu`) : « Mute » ou « Unmute », avec l'échéance. */
    Mute,

    /** « Remove from Onyx » (`minus.circle`, destructrice), sur un bot ajouté à la main. */
    RemoveFromOnyx,

    /** « Change Face… » (`face.smiling`). */
    ChangeFace,

    /** « Reorder Bots » (`arrow.up.arrow.down`), en dernier, comme après le `Divider()` de la référence. */
    Reorder,

    /** « Sort Bots by Recent », dès qu'un ordre a été mis à la main. */
    SortByRecent,
}

/**
 * L'état d'une carte de bot au moment où son menu s'ouvre.
 *
 * C'est lui — jamais un écran — qui choisit le **mot** (« Pin to top » ou « Unpin », « Hide » ou
 * « Unhide ») et l'**icône** : la référence le fait au même endroit, avec le même `if`
 * (`BotsRoster.kt:445-446`). Le libellé du bot entre dans le quatrième mot, « Nouvelle conversation
 * avec <le nom> », que la référence compose de la même façon (`:448`).
 */
internal data class BotMenuFrame(
    val label: String = "",
    val pinned: Boolean = false,
    val hidden: Boolean = false,
)

/**
 * Les entrées du menu d'une tuile de bot : les neuf de la référence dans son ordre, puis celles de
 * la maison.
 *
 * Une entrée n'est là que si elle **peut agir** : celles qui passent par le serveur ne s'affichent
 * que sur un bot que le Dashboard tient (`!bot.viaAPI`), « Supprimer » jamais sur le profil
 * principal, « Repartir à zéro » seulement quand le bot a un chat nommé à archiver, « Ouvrir la
 * dernière session » seulement quand il y en a une autre que ce chat. Le geste lui-même
 * ([handlers][BotMenuAction.handler]) retire aussi l'entrée quand l'écran ne le fournit pas — une
 * entrée affichée agit toujours.
 */
internal fun botMenuActions(
    bot: BotProfile,
    isOpen: Boolean,
    unread: Boolean,
    hasOrder: Boolean,
    /** Une conversation du bot autre que son chat : « Ouvrir la dernière session ». */
    hasRecent: Boolean = false,
): List<BotMenuAction> = buildList {
    // ————— Les neuf entrées de la référence, dans son ordre (`BotsRoster.kt:444-454`) —————
    add(BotMenuAction.Pin)
    if (!bot.isDefault) add(BotMenuAction.Hide)
    if (hasRecent) add(BotMenuAction.OpenRecent)
    add(BotMenuAction.NewChat)
    // Ses routines sont tenues par le serveur, par le Dashboard (`api/cron/jobs`) ; sur l'API, il
    // n'y en a pas — c'est le même garde que l'entrée « Schedules… » qu'elle remplace.
    if (!bot.viaAPI) add(BotMenuAction.Routines)
    // « Repartir à zéro » archive le chat du bot : il en faut un.
    if (bot.chatId != null) add(BotMenuAction.StartFresh)
    if (!bot.viaAPI) add(BotMenuAction.EditBot)
    // La copie d'un profil passe par `profiles.create` du Dashboard (`clone_from`).
    if (!bot.viaAPI) add(BotMenuAction.Duplicate)
    // Le profil s'en va du serveur (`DELETE api/profiles/{name}`), jamais le principal.
    if (!bot.isDefault && !bot.viaAPI) add(BotMenuAction.Delete)

    // ————— Les entrées propres à Onyx, sous ce bloc, dans leur ordre —————
    if (bot.chatId != null && !isOpen) {
        add(if (unread) BotMenuAction.MarkRead else BotMenuAction.MarkUnread)
    }
    if (!bot.isDefault) add(BotMenuAction.Voice)
    add(BotMenuAction.Mute)
    if (bot.viaAPI) {
        // Ajouté ici pour l'API Hermes : ses réglages et son visage sont ceux du Dashboard.
        if (!bot.isDefault) add(BotMenuAction.RemoveFromOnyx)
    } else {
        add(BotMenuAction.ChangeFace)
    }
    add(BotMenuAction.Reorder)
    if (hasOrder) add(BotMenuAction.SortByRecent)
}

/**
 * Les entrées du menu d'une salle, comme le `contextMenu` du `groupRow` : marquée lue ou non lue
 * quand elle n'est pas ouverte et que le serveur lui connaît une activité, le silence (sauf une
 * salle d'Hermes Desktop, qui ne notifie pas), puis la mise en ordre de la section.
 */
internal fun roomMenuActions(
    canMark: Boolean,
    unread: Boolean,
    inDesktop: Boolean,
    hasOrder: Boolean,
): List<BotMenuAction> = buildList {
    if (canMark) add(if (unread) BotMenuAction.MarkRead else BotMenuAction.MarkUnread)
    if (!inDesktop) add(BotMenuAction.Mute)
    add(BotMenuAction.Reorder)
    if (hasOrder) add(BotMenuAction.SortByRecent)
}

/**
 * Le libellé d'une entrée.
 *
 * Les neuf mots des entrées reprises sont ceux de la référence (`BotsRoster.kt:444-454`) : quatre
 * d'entre eux demandent l'état par [frame] (« Pin to top »/« Unpin », « Hide »/« Unhide »), et
 * « Nouvelle conversation avec … » **porte le nom du bot**, composé avec le gabarit de la clé. Les
 * entrées de la maison gardent leurs mots de la référence, tels quels.
 */
internal fun botMenuLabel(action: BotMenuAction, frame: BotMenuFrame = BotMenuFrame()): String = when (action) {
    BotMenuAction.Pin -> if (frame.pinned) "Unpin" else "Pin to top"
    BotMenuAction.Hide -> if (frame.hidden) "Unhide" else "Hide"
    BotMenuAction.OpenRecent -> "Open recent session"
    // Le nom du bot fait partie du libellé : la clé est un gabarit, composé ici une seule fois.
    BotMenuAction.NewChat -> OnyxLocalization.format("New chat with %1\$s", frame.label)
    BotMenuAction.Routines -> "Routines"
    BotMenuAction.StartFresh -> "Start fresh"
    BotMenuAction.EditBot -> "Edit"
    BotMenuAction.Duplicate -> "Duplicate"
    BotMenuAction.Delete -> "Delete"
    BotMenuAction.MarkRead -> "Mark as Read"
    BotMenuAction.MarkUnread -> "Mark as Unread"
    BotMenuAction.Voice -> "Voice…"
    BotMenuAction.Mute -> "Mute"
    BotMenuAction.RemoveFromOnyx -> "Remove from Onyx"
    BotMenuAction.ChangeFace -> "Change Face…"
    BotMenuAction.Reorder -> "Reorder Bots"
    BotMenuAction.SortByRecent -> "Sort Bots by Recent"
}

/**
 * L'icône d'une entrée.
 *
 * **Les neuf entrées reprises portent les icônes de la référence** : le jeu Lucide, généré depuis
 * la géométrie de la source ([LucideMenuIcons] porte, par icône, la ligne de `BotsRoster.kt` où
 * elle sert), et les deux qui dépendent de l'état prennent la variante barrée quand l'état est posé
 * (`:445-446`). Les entrées de la maison gardent leurs symboles SF.
 */
internal fun botMenuSymbol(action: BotMenuAction, frame: BotMenuFrame = BotMenuFrame()): Int? = when (action) {
    BotMenuAction.Pin -> if (frame.pinned) LucideMenuIcons.PinOff else LucideMenuIcons.Pin
    BotMenuAction.Hide -> if (frame.hidden) LucideMenuIcons.Eye else LucideMenuIcons.EyeOff
    BotMenuAction.OpenRecent -> LucideMenuIcons.History
    BotMenuAction.NewChat -> LucideMenuIcons.MessageSquarePlus
    BotMenuAction.Routines -> LucideMenuIcons.CalendarClock
    BotMenuAction.StartFresh -> LucideMenuIcons.RotateCcw
    BotMenuAction.EditBot -> LucideMenuIcons.Pencil
    BotMenuAction.Duplicate -> LucideMenuIcons.Copy
    BotMenuAction.Delete -> LucideMenuIcons.Trash2
    BotMenuAction.MarkRead -> Symbols.EnvelopeOpen
    BotMenuAction.MarkUnread -> Symbols.EnvelopeBadge
    BotMenuAction.Voice -> Symbols.Waveform
    BotMenuAction.Mute -> Symbols.BellSlash
    BotMenuAction.RemoveFromOnyx -> Symbols.MinusCircle
    BotMenuAction.ChangeFace -> Symbols.FaceSmiling
    BotMenuAction.Reorder -> Symbols.ArrowUpArrowDown
    BotMenuAction.SortByRecent -> Symbols.Clock
}

/**
 * Le menu d'une tuile de bot, tel qu'il s'ouvre à l'appui long : le panneau de la maison, les neuf
 * entrées de la référence dans son ordre puis celles de la maison, et chacune son action. Aucune
 * entrée ne s'ouvre sans agir : une action dont l'écran ne fournit pas le geste (la voix d'un bot,
 * par exemple) ne s'affiche pas du tout.
 */
@Composable
internal fun BotTileMenu(
    bot: BotProfile,
    isOpen: Boolean,
    unread: Boolean,
    mutes: Mutes,
    order: List<String>,
    /** Une conversation du bot autre que son chat : « Ouvrir la dernière session ». */
    hasRecent: Boolean = false,
    /** Le libellé du bot : il entre dans « Nouvelle conversation avec <le nom> ». */
    mainName: String = "Onyx",
    /** La rangée touchée (ses limites dans la fenêtre) : le panneau s'ouvre près d'elle. */
    anchor: Rect? = null,
    onDismiss: () -> Unit,
    onMarkRead: (() -> Unit)? = null,
    onMarkUnread: (() -> Unit)? = null,
    onVoice: (() -> Unit)? = null,
    /**
     * Les **routines** du bot : l'écran de ses tâches planifiées. C'est le geste de « Routines » de
     * la référence — celui de « Schedules… » d'hier, la même cible.
     */
    onRoutines: (() -> Unit)? = null,
    onRemoveFromOnyx: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onChangeFace: (() -> Unit)? = null,
    onHide: (() -> Unit)? = null,
    /** « Épingler en haut » / « Désépingler » : l'état **visé** (déjà retourné). */
    onSetPinned: ((Boolean) -> Unit)? = null,
    /** « Ouvrir la dernière session » : la plus récente du bot, hors son chat. */
    onOpenRecent: (() -> Unit)? = null,
    /** « Nouvelle conversation avec <le nom> ». */
    onNewChat: (() -> Unit)? = null,
    /** « Repartir à zéro » : son chat archivé, une conversation neuve ouverte. */
    onStartFresh: (() -> Unit)? = null,
    /** « Dupliquer » : un second bot, sa configuration copiée. */
    onDuplicate: (() -> Unit)? = null,
    /** « Supprimer » : l'écran demande confirmation avant. */
    onDelete: (() -> Unit)? = null,
    onReorder: (() -> Unit)? = null,
    onSortByRecent: (() -> Unit)? = null,
) {
    val actions = botMenuActions(
        bot,
        isOpen = isOpen,
        unread = unread,
        hasOrder = order.isNotEmpty(),
        hasRecent = hasRecent,
    )
    val frame = BotMenuFrame(label = bot.label(mainName), pinned = bot.pinned, hidden = bot.isHidden)
    OnyxMenu(
        items = menuItems(
            actions = actions,
            frame = frame,
            muteLines = { muteLines(mutes, "bot:" + bot.name, onDismiss) },
            onDismiss = onDismiss,
            onMarkRead = onMarkRead,
            onMarkUnread = onMarkUnread,
            onVoice = onVoice,
            onRoutines = onRoutines,
            onRemoveFromOnyx = onRemoveFromOnyx,
            onEdit = onEdit,
            onChangeFace = onChangeFace,
            onHide = onHide,
            onSetPinned = onSetPinned,
            onOpenRecent = onOpenRecent,
            onNewChat = onNewChat,
            onStartFresh = onStartFresh,
            onDuplicate = onDuplicate,
            onDelete = onDelete,
            onReorder = onReorder,
            onSortByRecent = onSortByRecent,
        ),
        onDismiss = onDismiss,
        anchor = anchor,
    )
}

/** Le menu d'une salle, le même panneau, avec ses seules entrées (`groupRow` de la référence). */
@Composable
internal fun RoomMenu(
    room: GroupChat,
    canMark: Boolean,
    unread: Boolean,
    mutes: Mutes,
    order: List<String>,
    /** La rangée touchée (ses limites dans la fenêtre) : le panneau s'ouvre près d'elle. */
    anchor: Rect? = null,
    onDismiss: () -> Unit,
    onMarkRead: (() -> Unit)? = null,
    onMarkUnread: (() -> Unit)? = null,
    onReorder: (() -> Unit)? = null,
    onSortByRecent: (() -> Unit)? = null,
) {
    val actions = roomMenuActions(
        canMark = canMark,
        unread = unread,
        inDesktop = room.inDesktop,
        hasOrder = order.isNotEmpty(),
    )
    OnyxMenu(
        items = menuItems(
            actions = actions,
            muteLines = { muteLines(mutes, "room:" + room.id, onDismiss) },
            onDismiss = onDismiss,
            onMarkRead = onMarkRead,
            onMarkUnread = onMarkUnread,
            onReorder = onReorder,
            onSortByRecent = onSortByRecent,
        ),
        onDismiss = onDismiss,
        anchor = anchor,
    )
}

/**
 * Une entrée au panneau : son action, et rien d'autre. Le `when` est exhaustif — ajouter une action
 * sans lui donner d'effet ne compilerait pas — et une action sans geste ne produit aucune ligne,
 * si bien qu'une entrée affichée agit toujours.
 */
internal fun menuItems(
    actions: List<BotMenuAction>,
    frame: BotMenuFrame = BotMenuFrame(),
    /** Les lignes du sous-menu du silence (`MuteMenu`) : ce que « Mute › » ouvre. */
    muteLines: () -> List<MenuItem>,
    onDismiss: () -> Unit,
    onMarkRead: (() -> Unit)? = null,
    onMarkUnread: (() -> Unit)? = null,
    onVoice: (() -> Unit)? = null,
    onRoutines: (() -> Unit)? = null,
    onRemoveFromOnyx: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onChangeFace: (() -> Unit)? = null,
    onHide: (() -> Unit)? = null,
    onSetPinned: ((Boolean) -> Unit)? = null,
    onOpenRecent: (() -> Unit)? = null,
    onNewChat: (() -> Unit)? = null,
    onStartFresh: (() -> Unit)? = null,
    onDuplicate: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onReorder: (() -> Unit)? = null,
    onSortByRecent: (() -> Unit)? = null,
): List<MenuItem> = actions.flatMap { action ->
    // Le silence est un sous-menu (« Mute › »), comme celui du modèle : ses durées ne sont pas
    // quatre lignes du panneau, et la ligne ouvre les quatre — plus « Unmute » s'il est déjà tu.
    if (action == BotMenuAction.Mute) {
        return@flatMap listOf(
            MenuItem(
                label = botMenuLabel(action, frame),
                symbol = botMenuSymbol(action, frame),
                submenu = muteLines(),
            ) { }
        )
    }
    val run = action.handler(
        frame = frame,
        onMarkRead = onMarkRead,
        onMarkUnread = onMarkUnread,
        onVoice = onVoice,
        onRoutines = onRoutines,
        onRemoveFromOnyx = onRemoveFromOnyx,
        onEdit = onEdit,
        onChangeFace = onChangeFace,
        onHide = onHide,
        onSetPinned = onSetPinned,
        onOpenRecent = onOpenRecent,
        onNewChat = onNewChat,
        onStartFresh = onStartFresh,
        onDuplicate = onDuplicate,
        onDelete = onDelete,
        onReorder = onReorder,
        onSortByRecent = onSortByRecent,
    )
    run?.let {
        listOf(
            MenuItem(
                label = botMenuLabel(action, frame),
                symbol = botMenuSymbol(action, frame),
                destructive = action == BotMenuAction.RemoveFromOnyx || action == BotMenuAction.Delete,
                // « Nouvelle conversation avec … » est **composée** (le nom du bot y entre) : elle
                // sort déjà traduite, le dictionnaire ne la reprend pas une deuxième fois.
                localize = action != BotMenuAction.NewChat,
                // Un filet devant le dernier groupe, celui de la mise en ordre — et un seul,
                // comme le `Divider()` du modèle.
                groupBreak = action == BotMenuAction.Reorder,
            ) {
                onDismiss()
                it()
            },
        )
    }.orEmpty()
}

/**
 * Les lignes du silence au menu (`MuteMenu`), telles que son sous-menu les ouvre : « Unmute » et
 * son échéance quand il est déjà tu, puis les quatre durées — chacune son action, prises à
 * `muteSubmenuItems`.
 */
internal fun muteLines(mutes: Mutes, item: String, onDismiss: () -> Unit): List<MenuItem> =
    muteSubmenuItems(mutes, item).map { entry ->
        MenuItem(
            label = entry.label,
            symbol = entry.symbol,
            destructive = entry.destructive,
            checked = entry.checked,
            localize = entry.localize,
            submenu = entry.submenu,
            groupBreak = entry.groupBreak,
        ) {
            onDismiss()
            entry.onSelect()
        }
    }

/**
 * Les gestes du menu d'une carte de bot, **rassemblés**.
 *
 * Les deux endroits qui montrent ce menu — le volet (position *Bots*) et la page « Bots » — passent
 * la même table : une seule liste dit ce que chaque entrée fait, et un geste absent retire son
 * entrée (elle ne s'affiche pas). [hasRecent] répond pour « Ouvrir la dernière session » : elle
 * n'existe que si le bot a une conversation **autre que son chat**.
 */
data class BotMenuHandlers(
    /** « Épingler en haut » / « Désépingler » : l'état **visé** est déjà retourné par le menu. */
    val onSetPinned: ((BotProfile, Boolean) -> Unit)? = null,
    /** « Masquer » / « Afficher ». */
    val onHide: ((BotProfile) -> Unit)? = null,
    /** « Ouvrir la dernière session ». */
    val onOpenRecent: ((BotProfile) -> Unit)? = null,
    /** « Nouvelle conversation avec <le nom> ». */
    val onNewChat: ((BotProfile) -> Unit)? = null,
    /** « Routines » : l'écran des tâches planifiées du bot. */
    val onRoutines: ((BotProfile) -> Unit)? = null,
    /** « Repartir à zéro » : son chat archivé, une conversation neuve ouverte. */
    val onStartFresh: ((BotProfile) -> Unit)? = null,
    /** « Modifier » : l'éditeur du bot. */
    val onEdit: ((BotProfile) -> Unit)? = null,
    /** « Dupliquer ». */
    val onDuplicate: ((BotProfile) -> Unit)? = null,
    /** « Supprimer » : c'est l'écran qui demande confirmation avant d'agir. */
    val onDelete: ((BotProfile) -> Unit)? = null,
    // ————— Les entrées propres à Onyx, celles d'avant —————
    val onMarkSeen: ((BotProfile) -> Unit)? = null,
    val onMarkUnread: ((BotProfile) -> Unit)? = null,
    val onVoice: ((BotProfile) -> Unit)? = null,
    val onChangeFace: ((BotProfile) -> Unit)? = null,
    val onRemoveFromOnyx: ((BotProfile) -> Unit)? = null,
    val onReorder: (() -> Unit)? = null,
    val onSortByRecent: (() -> Unit)? = null,
    /** Le bot a-t-il une conversation autre que son chat ? (« Ouvrir la dernière session »). */
    val hasRecent: (BotProfile) -> Boolean = { false },
)

/**
 * Le menu d'une carte de bot, monté depuis [handlers] : le **même** panneau pour le volet et pour la
 * page « Bots », si bien qu'aucune des deux ne peut oublier une entrée que l'autre a.
 *
 * « Supprimer » passe par [onAskDelete] : le menu ne supprime jamais lui-même — c'est l'écran qui
 * demande confirmation d'abord, comme la référence.
 */
@Composable
internal fun BotCardMenu(
    bot: BotProfile,
    isOpen: Boolean,
    unread: Boolean,
    mutes: Mutes,
    order: List<String>,
    mainName: String,
    anchor: Rect?,
    handlers: BotMenuHandlers,
    onDismiss: () -> Unit,
    /** « Supprimer » : l'écran pose la question, le menu ne fait que la demander. */
    onAskDelete: ((BotProfile) -> Unit)? = null,
) {
    BotTileMenu(
        bot = bot,
        isOpen = isOpen,
        unread = unread,
        mutes = mutes,
        order = order,
        hasRecent = handlers.hasRecent(bot),
        mainName = mainName,
        anchor = anchor,
        onDismiss = onDismiss,
        onMarkRead = handlers.onMarkSeen?.let { act -> { act(bot) } },
        onMarkUnread = handlers.onMarkUnread?.let { act -> { act(bot) } },
        onVoice = handlers.onVoice?.let { act -> { act(bot) } },
        onRoutines = handlers.onRoutines?.let { act -> { act(bot) } },
        onRemoveFromOnyx = handlers.onRemoveFromOnyx?.let { act -> { act(bot) } },
        onEdit = handlers.onEdit?.let { act -> { act(bot) } },
        onChangeFace = handlers.onChangeFace?.let { act -> { act(bot) } },
        onHide = handlers.onHide?.let { act -> { act(bot) } },
        onSetPinned = handlers.onSetPinned?.let { act -> { pinned -> act(bot, pinned) } },
        onOpenRecent = handlers.onOpenRecent?.let { act -> { act(bot) } },
        onNewChat = handlers.onNewChat?.let { act -> { act(bot) } },
        onStartFresh = handlers.onStartFresh?.let { act -> { act(bot) } },
        onDuplicate = handlers.onDuplicate?.let { act -> { act(bot) } },
        onDelete = onAskDelete?.let { ask -> { ask(bot) } },
        onReorder = handlers.onReorder,
        onSortByRecent = handlers.onSortByRecent,
    )
}

/**
 * Le geste d'une entrée : celui que l'écran a fourni, ou rien — l'entrée ne s'affiche alors pas.
 *
 * [frame] n'entre que pour l'épinglage : son geste dit l'état **visé** (« épingler » ou
 * « désépingler »), et c'est le seul endroit qui le retourne — l'écran, lui, reçoit une décision.
 */
private fun BotMenuAction.handler(
    frame: BotMenuFrame,
    onMarkRead: (() -> Unit)?,
    onMarkUnread: (() -> Unit)?,
    onVoice: (() -> Unit)?,
    onRoutines: (() -> Unit)?,
    onRemoveFromOnyx: (() -> Unit)?,
    onEdit: (() -> Unit)?,
    onChangeFace: (() -> Unit)?,
    onHide: (() -> Unit)?,
    onSetPinned: ((Boolean) -> Unit)?,
    onOpenRecent: (() -> Unit)?,
    onNewChat: (() -> Unit)?,
    onStartFresh: (() -> Unit)?,
    onDuplicate: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    onReorder: (() -> Unit)?,
    onSortByRecent: (() -> Unit)?,
): (() -> Unit)? = when (this) {
    BotMenuAction.MarkRead -> onMarkRead
    BotMenuAction.MarkUnread -> onMarkUnread
    BotMenuAction.Voice -> onVoice
    BotMenuAction.Mute -> null
    BotMenuAction.Routines -> onRoutines
    BotMenuAction.RemoveFromOnyx -> onRemoveFromOnyx
    BotMenuAction.EditBot -> onEdit
    BotMenuAction.ChangeFace -> onChangeFace
    BotMenuAction.Hide -> onHide
    BotMenuAction.Pin -> onSetPinned?.let { set -> { set(!frame.pinned) } }
    BotMenuAction.OpenRecent -> onOpenRecent
    BotMenuAction.NewChat -> onNewChat
    BotMenuAction.StartFresh -> onStartFresh
    BotMenuAction.Duplicate -> onDuplicate
    BotMenuAction.Delete -> onDelete
    BotMenuAction.Reorder -> onReorder
    BotMenuAction.SortByRecent -> onSortByRecent
}
