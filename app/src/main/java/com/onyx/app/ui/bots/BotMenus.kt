package com.onyx.app.ui.bots

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect
import com.onyx.app.data.model.serve.BotProfile
import com.onyx.app.ui.common.Mutes
import com.onyx.app.ui.common.muteSubmenuItems
import com.onyx.app.ui.conversations.GroupChat
import com.onyx.app.ui.design.OnyxMenu
import com.onyx.app.ui.design.MenuItem
import com.onyx.app.ui.design.Symbols

/**
 * Ce qu'une tuile de bot ou une salle propose quand on l'appuie longuement: le menu contextuel
 * , qui n'existait pas ici (« Reorder Bots » et « Sort Bots by Recent » étaient des lignes de
 * section, et « Mark as Unread » d'une salle n'était atteignable nulle part).
 *
 * Une action par entrée, dans l'ordre: [BotTileMenu] rend le `botMenu(_:unread:isOpen:)`
 * de , [RoomMenu] le `contextMenu` du `groupRow` (`:817`).
 */
internal enum class BotMenuAction {
    /** « Mark as Read » (`envelope.open`), le bot ou la salle ayant quelque chose de neuf. */
    MarkRead,

    /** « Mark as Unread » (`envelope.badge`). */
    MarkUnread,

    /** « Voice… »: la voix d'un bot, sans le profil principal (`!bot.isDefault`). */
    Voice,

    /** Le silence (`MuteMenu`): « Mute » ou « Unmute », avec l'échéance. */
    Mute,

    /** « Schedules… » (`clock`), sur un bot du Dashboard (`!bot.viaAPI`). */
    Schedules,

    /** « Remove from Onyx » (`minus.circle`, destructrice), sur un bot ajouté à la main. */
    RemoveFromOnyx,

    /** « Edit Bot… » (`slider.horizontal.3`). */
    EditBot,

    /** « Change Face… » (`face.smiling`). */
    ChangeFace,

    /** « Hide » (`eye.slash`), sans le profil principal. */
    Hide,

    /** « Reorder Bots » (`arrow.up.arrow.down`), en dernier, comme après le `Divider`. */
    Reorder,

    /** « Sort Bots by Recent », dès qu'un ordre a été mis à la main. */
    SortByRecent,
}

/**
 * Les entrées du menu d'une tuile de bot, dans l'ordre du `botMenu`: marqué lu ou non lu
 * quand le bot a un chat nommé et qu'il n'est pas celui qu'on regarde, « Voice… » sur un bot autre
 * que le principal, le silence, « Schedules… » sur un bot du Dashboard — sur un bot ajouté à la
 * main, c'est « Remove from Onyx » à la place —, puis « Edit Bot… », « Change Face… » et
 * « Hide », et enfin la mise en ordre de la section.
 */
internal fun botMenuActions(
    bot: BotProfile,
    isOpen: Boolean,
    unread: Boolean,
    hasOrder: Boolean,
): List<BotMenuAction> = buildList {
    if (bot.chatId != null && !isOpen) {
        add(if (unread) BotMenuAction.MarkRead else BotMenuAction.MarkUnread)
    }
    if (!bot.isDefault) add(BotMenuAction.Voice)
    add(BotMenuAction.Mute)
    // Ses horaires sont tenus par le serveur, par le Dashboard; sur l'API, il n'y en a pas.
    if (!bot.viaAPI) add(BotMenuAction.Schedules)
    if (bot.viaAPI) {
        // Ajouté ici pour l'API Hermes: ses réglages et son visage sont ceux du Dashboard.
        if (!bot.isDefault) add(BotMenuAction.RemoveFromOnyx)
    } else {
        add(BotMenuAction.EditBot)
        add(BotMenuAction.ChangeFace)
        if (!bot.isDefault) add(BotMenuAction.Hide)
    }
    add(BotMenuAction.Reorder)
    if (hasOrder) add(BotMenuAction.SortByRecent)
}

/**
 * Les entrées du menu d'une salle, comme le `contextMenu` du `groupRow`: marquée lue ou non lue
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

/** Le libellé d'une entrée, mot pour mot. */
internal fun botMenuLabel(action: BotMenuAction): String = when (action) {
    BotMenuAction.MarkRead -> "Mark as Read"
    BotMenuAction.MarkUnread -> "Mark as Unread"
    BotMenuAction.Voice -> "Voice…"
    BotMenuAction.Mute -> "Mute"
    BotMenuAction.Schedules -> "Schedules…"
    BotMenuAction.RemoveFromOnyx -> "Remove from Onyx"
    BotMenuAction.EditBot -> "Edit Bot…"
    BotMenuAction.ChangeFace -> "Change Face…"
    BotMenuAction.Hide -> "Hide"
    BotMenuAction.Reorder -> "Reorder Bots"
    BotMenuAction.SortByRecent -> "Sort Bots by Recent"
}

/** Le dessin d'une entrée (`envelope.open`, `slider.horizontal.3`…). */
internal fun botMenuSymbol(action: BotMenuAction): Int? = when (action) {
    BotMenuAction.MarkRead -> Symbols.EnvelopeOpen
    BotMenuAction.MarkUnread -> Symbols.EnvelopeBadge
    BotMenuAction.Voice -> Symbols.Waveform
    BotMenuAction.Mute -> Symbols.BellSlash
    BotMenuAction.Schedules -> Symbols.Clock
    BotMenuAction.RemoveFromOnyx -> Symbols.MinusCircle
    BotMenuAction.EditBot -> Symbols.SliderHorizontal3
    BotMenuAction.ChangeFace -> Symbols.FaceSmiling
    BotMenuAction.Hide -> Symbols.EyeSlash
    BotMenuAction.Reorder -> Symbols.ArrowUpArrowDown
    BotMenuAction.SortByRecent -> Symbols.Clock
}

/**
 * Le menu d'une tuile de bot, tel qu'il s'ouvre à l'appui long — le panneau , ses entrées dans
 * l'ordre , et chacune son action: aucune entrée ne s'ouvre sans agir. Une action dont
 * l'écran ne fournit pas le geste (la voix d'un bot, par exemple) ne s'affiche pas du tout.
 */
@Composable
internal fun BotTileMenu(
    bot: BotProfile,
    isOpen: Boolean,
    unread: Boolean,
    mutes: Mutes,
    order: List<String>,
    /** La rangée touchée (ses limites dans la fenêtre): le panneau s'ouvre près d'elle. */
    anchor: Rect? = null,
    onDismiss: () -> Unit,
    onMarkRead: (() -> Unit)? = null,
    onMarkUnread: (() -> Unit)? = null,
    onVoice: (() -> Unit)? = null,
    onSchedules: (() -> Unit)? = null,
    onRemoveFromOnyx: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onChangeFace: (() -> Unit)? = null,
    onHide: (() -> Unit)? = null,
    onReorder: (() -> Unit)? = null,
    onSortByRecent: (() -> Unit)? = null,
) {
    val actions = botMenuActions(bot, isOpen = isOpen, unread = unread, hasOrder = order.isNotEmpty())
    OnyxMenu(
        items = menuItems(
            actions = actions,
            muteLines = { muteLines(mutes, "bot:" + bot.name, onDismiss) },
            onDismiss = onDismiss,
            onMarkRead = onMarkRead,
            onMarkUnread = onMarkUnread,
            onVoice = onVoice,
            onSchedules = onSchedules,
            onRemoveFromOnyx = onRemoveFromOnyx,
            onEdit = onEdit,
            onChangeFace = onChangeFace,
            onHide = onHide,
            onReorder = onReorder,
            onSortByRecent = onSortByRecent,
        ),
        onDismiss = onDismiss,
        anchor = anchor,
    )
}

/** Le menu d'une salle, le même panneau, avec ses seules entrées (`groupRow` ). */
@Composable
internal fun RoomMenu(
    room: GroupChat,
    canMark: Boolean,
    unread: Boolean,
    mutes: Mutes,
    order: List<String>,
    /** La rangée touchée (ses limites dans la fenêtre): le panneau s'ouvre près d'elle. */
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
 * Une entrée au panneau: son action, et rien d'autre. Le `when` est exhaustif — ajouter une action
 * sans lui donner d'effet ne compilerait pas — et une action sans geste ne produit aucune ligne,
 * si bien qu'une entrée affichée agit toujours.
 */
internal fun menuItems(
    actions: List<BotMenuAction>,
    /** Les lignes du sous-menu du silence (`MuteMenu`): ce que « Mute › » ouvre. */
    muteLines: () -> List<MenuItem>,
    onDismiss: () -> Unit,
    onMarkRead: (() -> Unit)? = null,
    onMarkUnread: (() -> Unit)? = null,
    onVoice: (() -> Unit)? = null,
    onSchedules: (() -> Unit)? = null,
    onRemoveFromOnyx: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onChangeFace: (() -> Unit)? = null,
    onHide: (() -> Unit)? = null,
    onReorder: (() -> Unit)? = null,
    onSortByRecent: (() -> Unit)? = null,
): List<MenuItem> = actions.flatMap { action ->
    // Le silence est un sous-menu (« Mute › »), comme celui du modèle: ses durées ne sont pas
    // quatre lignes du panneau, et la ligne ouvre les quatre — plus « Unmute » s'il est déjà tu.
    if (action == BotMenuAction.Mute) {
        return@flatMap listOf(
            MenuItem(
                label = botMenuLabel(action),
                symbol = botMenuSymbol(action),
                submenu = muteLines(),
            ) { },
        )
    }
    val run = action.handler(
        onMarkRead = onMarkRead,
        onMarkUnread = onMarkUnread,
        onVoice = onVoice,
        onSchedules = onSchedules,
        onRemoveFromOnyx = onRemoveFromOnyx,
        onEdit = onEdit,
        onChangeFace = onChangeFace,
        onHide = onHide,
        onReorder = onReorder,
        onSortByRecent = onSortByRecent,
    )
    run?.let {
        listOf(
            MenuItem(
                label = botMenuLabel(action),
                symbol = botMenuSymbol(action),
                destructive = action == BotMenuAction.RemoveFromOnyx,
                // Un filet devant le dernier groupe, celui de la mise en ordre — et un seul,
                // comme le `Divider` du modèle.
                groupBreak = action == BotMenuAction.Reorder,
            ) {
                onDismiss()
                it()
            },
        )
    }.orEmpty()
}

/**
 * Les lignes du silence au menu (`MuteMenu`), telles que son sous-menu les ouvre: « Unmute » et
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

/** Le geste d'une entrée: celui que l'écran a fourni, ou rien — l'entrée ne s'affiche alors pas. */
private fun BotMenuAction.handler(
    onMarkRead: (() -> Unit)?,
    onMarkUnread: (() -> Unit)?,
    onVoice: (() -> Unit)?,
    onSchedules: (() -> Unit)?,
    onRemoveFromOnyx: (() -> Unit)?,
    onEdit: (() -> Unit)?,
    onChangeFace: (() -> Unit)?,
    onHide: (() -> Unit)?,
    onReorder: (() -> Unit)?,
    onSortByRecent: (() -> Unit)?,
): (() -> Unit)? = when (this) {
    BotMenuAction.MarkRead -> onMarkRead
    BotMenuAction.MarkUnread -> onMarkUnread
    BotMenuAction.Voice -> onVoice
    BotMenuAction.Mute -> null
    BotMenuAction.Schedules -> onSchedules
    BotMenuAction.RemoveFromOnyx -> onRemoveFromOnyx
    BotMenuAction.EditBot -> onEdit
    BotMenuAction.ChangeFace -> onChangeFace
    BotMenuAction.Hide -> onHide
    BotMenuAction.Reorder -> onReorder
    BotMenuAction.SortByRecent -> onSortByRecent
}
