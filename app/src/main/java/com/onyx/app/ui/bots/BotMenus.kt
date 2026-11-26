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
 * What a bot tile or a room offers when long-pressed.
 *
 * **Two blocks, in this order.** First **the reference's nine entries**, in *its* order:
 * `BotsRoster.kt:444-454` (its menu for a bot card, as it opens on long press). Then the
 * entries **specific to Onyx**, those of the house reference source
 * (`botMenu(_:unread:isOpen:)` of `ConversationsView:700`, the `contextMenu` of the `groupRow`
 * at `:817`): they do not move, and none is removed.
 */
internal enum class BotMenuAction {
    // ————— The reference's nine entries, in its order (`BotsRoster.kt:444-454`) —————

    /** « Épingler en haut » (Pin to top) / « Désépingler » (Unpin) (`Lucide.Pin` / `PinOff`): the bot moves to the top. */
    Pin,

    /** « Masquer » (Hide) / « Afficher » (Unhide) (`Lucide.EyeOff` / `Eye`), without the main profile. */
    Hide,

    /** « Ouvrir la dernière session » (Open recent session) (`Lucide.History`): the most recent one, outside its chat. */
    OpenRecent,

    /** « Nouvelle conversation avec <le nom du bot> » (New chat with the bot) (`Lucide.MessageSquarePlus`). */
    NewChat,

    /** « Routines » (`Lucide.CalendarClock`): the bot's scheduled tasks. */
    Routines,

    /** « Repartir à zéro » (Start fresh) (`Lucide.RotateCcw`): its chat archived, a fresh conversation. */
    StartFresh,

    /** « Modifier » (Edit) (`Lucide.Pencil`): the bot's editor. */
    EditBot,

    /** « Dupliquer » (Duplicate) (`Lucide.Copy`): a second bot, with its configuration copied. */
    Duplicate,

    /** « Supprimer » (Delete) (`Lucide.Trash2`, destructive): the profile goes away, after confirmation. */
    Delete,

    // ————— The entries specific to Onyx, below this block, in their order —————

    /** « Mark as Read » (`envelope.open`), the bot or the room having something new. */
    MarkRead,

    /** « Mark as Unread » (`envelope.badge`). */
    MarkUnread,

    /** « Voice… » : a bot's voice, without the main profile (`!bot.isDefault`). */
    Voice,

    /** Muting (`MuteMenu`): « Mute » or « Unmute », with the deadline. */
    Mute,

    /** « Remove from Onyx » (`minus.circle`, destructive), on a bot added by hand. */
    RemoveFromOnyx,

    /** « Change Face… » (`face.smiling`). */
    ChangeFace,

    /** « Reorder Bots » (`arrow.up.arrow.down`), last, as after the `Divider()` of the reference. */
    Reorder,

    /** « Sort Bots by Recent », as soon as an order has been set by hand. */
    SortByRecent,

    /**
     * « Partager comme modèle » (Share as Template) (`Symbols.Share`): the bot's `onyx://bot?v=1&…` link, its QR, and
     * nothing that goes to the server before the recipient has seen the preview.
     *
     * It comes **after** all the house ones: it is the last thing a bot offers,
     * as « Supprimer » (Delete) closes the reference block.
     */
    ShareTemplate,
}

/**
 * The state of a bot card at the moment its menu opens.
 *
 * It — never a screen — chooses the **word** (« Pin to top » or « Unpin », « Hide » or
 * « Unhide ») and the **icon**: the reference does it in the same place, with the same `if`
 * (`BotsRoster.kt:445-446`). The bot's label enters the fourth word, « Nouvelle conversation
 * avec <le nom> » (New chat with the name), which the reference composes the same way (`:448`).
 */
internal data class BotMenuFrame(
    val label: String = "",
    val pinned: Boolean = false,
    val hidden: Boolean = false,
)

/**
 * The menu entries of a bot tile: the reference's nine in its order, then the house
 * ones.
 *
 * An entry is there only if it **can act**: those that go through the server are shown
 * only on a bot that the Dashboard holds (`!bot.viaAPI`), « Supprimer » (Delete) never on the main
 * profile, « Repartir à zéro » (Start fresh) only when the bot has a named chat to archive, « Ouvrir la
 * dernière session » (Open recent session) only when there is one other than that chat. The gesture itself
 * ([handlers][BotMenuAction.handler]) also removes the entry when the screen does not provide it — a
 * displayed entry always acts.
 */
internal fun botMenuActions(
    bot: BotProfile,
    isOpen: Boolean,
    unread: Boolean,
    hasOrder: Boolean,
    /** A conversation of the bot other than its chat: « Ouvrir la dernière session » (Open recent session). */
    hasRecent: Boolean = false,
): List<BotMenuAction> = buildList {
    // ————— The reference's nine entries, in its order (`BotsRoster.kt:444-454`) —————
    add(BotMenuAction.Pin)
    if (!bot.isDefault) add(BotMenuAction.Hide)
    if (hasRecent) add(BotMenuAction.OpenRecent)
    add(BotMenuAction.NewChat)
    // Its routines are held by the server, by the Dashboard (`api/cron/jobs`); on the API, there
    // are none — it is the same guard as the « Schedules… » entry it replaces.
    if (!bot.viaAPI) add(BotMenuAction.Routines)
    // « Repartir à zéro » (Start fresh) archives the bot's chat: one is needed.
    if (bot.chatId != null) add(BotMenuAction.StartFresh)
    if (!bot.viaAPI) add(BotMenuAction.EditBot)
    // Copying a profile goes through the Dashboard's `profiles.create` (`clone_from`).
    if (!bot.viaAPI) add(BotMenuAction.Duplicate)
    // The profile leaves the server (`DELETE api/profiles/{name}`), never the main one.
    if (!bot.isDefault && !bot.viaAPI) add(BotMenuAction.Delete)

    // ————— The entries specific to Onyx, below this block, in their order —————
    if (bot.chatId != null && !isOpen) {
        add(if (unread) BotMenuAction.MarkRead else BotMenuAction.MarkUnread)
    }
    if (!bot.isDefault) add(BotMenuAction.Voice)
    add(BotMenuAction.Mute)
    if (bot.viaAPI) {
        // Added here for the Hermes API: its settings and its face are the Dashboard's.
        if (!bot.isDefault) add(BotMenuAction.RemoveFromOnyx)
    } else {
        add(BotMenuAction.ChangeFace)
    }
    add(BotMenuAction.Reorder)
    if (hasOrder) add(BotMenuAction.SortByRecent)

    // ————— « Partager comme modèle » (Share as Template), the last —————
    // The link is read from the server: its `SOUL.md` (`api/profiles/{nom}/soul`), its toolsets
    // and its skills. A bot added by hand has no profile on the Dashboard, and the main
    // profile is the person's agent — neither is shared as a template.
    if (!bot.viaAPI && !bot.isDefault) add(BotMenuAction.ShareTemplate)
}

/**
 * The menu entries of a room, like the `contextMenu` of the `groupRow`: marked read or unread
 * when it is not open and the server knows of an activity for it, muting (except a
 * Hermes Desktop room, which does not notify), then the ordering of the section.
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
 * The label of an entry.
 *
 * The nine words of the adopted entries are those of the reference (`BotsRoster.kt:444-454`): four
 * of them ask for the state through [frame] (« Pin to top »/« Unpin », « Hide »/« Unhide »), and
 * « Nouvelle conversation avec … » (New chat with …) **carries the bot's name**, composed with the key's template. The
 * house entries keep their words from the reference, as is.
 */
internal fun botMenuLabel(action: BotMenuAction, frame: BotMenuFrame = BotMenuFrame()): String = when (action) {
    BotMenuAction.Pin -> if (frame.pinned) "Unpin" else "Pin to top"
    BotMenuAction.Hide -> if (frame.hidden) "Unhide" else "Hide"
    BotMenuAction.OpenRecent -> "Open recent session"
    // The bot's name is part of the label: the key is a template, composed here only once.
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
    BotMenuAction.ShareTemplate -> "Share as Template…"
}

/**
 * The icon of an entry.
 *
 * **The nine adopted entries carry the reference's icons**: the Lucide set, generated from
 * the source's geometry ([LucideMenuIcons] carries, per icon, the line of `BotsRoster.kt` where
 * it is used), and the two that depend on the state take the struck-through variant when the state is set
 * (`:445-446`). The house entries keep their SF symbols.
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
    BotMenuAction.ShareTemplate -> Symbols.Share
}

/**
 * The menu of a bot tile, as it opens on long press: the house panel, the nine
 * entries of the reference in its order then the house ones, and each with its action. No
 * entry opens without acting: an action whose gesture the screen does not provide (a bot's voice,
 * for example) is not shown at all.
 */
@Composable
internal fun BotTileMenu(
    bot: BotProfile,
    isOpen: Boolean,
    unread: Boolean,
    mutes: Mutes,
    order: List<String>,
    /** A conversation of the bot other than its chat: « Ouvrir la dernière session » (Open recent session). */
    hasRecent: Boolean = false,
    /** The bot's label: it enters « Nouvelle conversation avec <le nom> » (New chat with the name). */
    mainName: String = "Onyx",
    /** The touched row (its bounds in the window): the panel opens near it. */
    anchor: Rect? = null,
    onDismiss: () -> Unit,
    onMarkRead: (() -> Unit)? = null,
    onMarkUnread: (() -> Unit)? = null,
    onVoice: (() -> Unit)? = null,
    /**
     * The bot's **routines**: the screen of its scheduled tasks. It is the gesture of « Routines » in
     * the reference — that of yesterday's « Schedules… », the same target.
     */
    onRoutines: (() -> Unit)? = null,
    onRemoveFromOnyx: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onChangeFace: (() -> Unit)? = null,
    onHide: (() -> Unit)? = null,
    /** « Épingler en haut » (Pin to top) / « Désépingler » (Unpin): the **target** state (already inverted). */
    onSetPinned: ((Boolean) -> Unit)? = null,
    /** « Ouvrir la dernière session » (Open recent session): the bot's most recent one, outside its chat. */
    onOpenRecent: (() -> Unit)? = null,
    /** « Nouvelle conversation avec <le nom> » (New chat with the name). */
    onNewChat: (() -> Unit)? = null,
    /** « Repartir à zéro » (Start fresh): its chat archived, a fresh conversation opened. */
    onStartFresh: (() -> Unit)? = null,
    /** « Dupliquer » (Duplicate): a second bot, with its configuration copied. */
    onDuplicate: (() -> Unit)? = null,
    /** « Supprimer » (Delete): the screen asks for confirmation first. */
    onDelete: (() -> Unit)? = null,
    onReorder: (() -> Unit)? = null,
    onSortByRecent: (() -> Unit)? = null,
    /** « Partager comme modèle » (Share as Template): the sheet of the link and its QR. */
    onShareTemplate: (() -> Unit)? = null,
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
            onShareTemplate = onShareTemplate,
        ),
        onDismiss = onDismiss,
        anchor = anchor,
    )
}

/** The menu of a room, the same panel, with its own entries only (`groupRow` of the reference). */
@Composable
internal fun RoomMenu(
    room: GroupChat,
    canMark: Boolean,
    unread: Boolean,
    mutes: Mutes,
    order: List<String>,
    /** The touched row (its bounds in the window): the panel opens near it. */
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
 * An entry in the panel: its action, and nothing else. The `when` is exhaustive — adding an action
 * without giving it an effect would not compile — and an action without a gesture produces no line,
 * so a displayed entry always acts.
 */
internal fun menuItems(
    actions: List<BotMenuAction>,
    frame: BotMenuFrame = BotMenuFrame(),
    /** The lines of the mute submenu (`MuteMenu`): what « Mute › » opens. */
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
    onShareTemplate: (() -> Unit)? = null,
): List<MenuItem> = actions.flatMap { action ->
    // Muting is a submenu (« Mute › »), like the template's: its durations are not
    // four lines of the panel, and the line opens the four — plus « Unmute » if it is already muted.
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
        onShareTemplate = onShareTemplate,
    )
    run?.let {
        listOf(
            MenuItem(
                label = botMenuLabel(action, frame),
                symbol = botMenuSymbol(action, frame),
                destructive = action == BotMenuAction.RemoveFromOnyx || action == BotMenuAction.Delete,
                // « Nouvelle conversation avec … » (New chat with …) is **composed** (the bot's name enters it): it
                // comes out already translated, the dictionary does not take it a second time.
                localize = action != BotMenuAction.NewChat,
                // A rule before the last group, the one for ordering — and only one,
                // like the template's `Divider()`.
                groupBreak = action == BotMenuAction.Reorder,
            ) {
                onDismiss()
                it()
            },
        )
    }.orEmpty()
}

/**
 * The mute lines in the menu (`MuteMenu`), as its submenu opens them: « Unmute » and
 * its deadline when it is already muted, then the four durations — each with its action, taken from
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
 * The gestures of a bot card's menu, **gathered**.
 *
 * The two places that show this menu — the drawer (*Bots* position) and the « Bots » page — pass
 * the same table: a single list says what each entry does, and an absent gesture removes its
 * entry (it is not shown). [hasRecent] answers for « Ouvrir la dernière session » (Open recent session): it
 * exists only if the bot has a conversation **other than its chat**.
 */
data class BotMenuHandlers(
    /** « Épingler en haut » (Pin to top) / « Désépingler » (Unpin): the **target** state is already inverted by the menu. */
    val onSetPinned: ((BotProfile, Boolean) -> Unit)? = null,
    /** « Masquer » (Hide) / « Afficher » (Unhide). */
    val onHide: ((BotProfile) -> Unit)? = null,
    /** « Ouvrir la dernière session » (Open recent session). */
    val onOpenRecent: ((BotProfile) -> Unit)? = null,
    /** « Nouvelle conversation avec <le nom> » (New chat with the name). */
    val onNewChat: ((BotProfile) -> Unit)? = null,
    /** « Routines » : the screen of the bot's scheduled tasks. */
    val onRoutines: ((BotProfile) -> Unit)? = null,
    /** « Repartir à zéro » (Start fresh): its chat archived, a fresh conversation opened. */
    val onStartFresh: ((BotProfile) -> Unit)? = null,
    /** « Modifier » (Edit): the bot's editor. */
    val onEdit: ((BotProfile) -> Unit)? = null,
    /** « Dupliquer » (Duplicate). */
    val onDuplicate: ((BotProfile) -> Unit)? = null,
    /** « Supprimer » (Delete): it is the screen that asks for confirmation before acting. */
    val onDelete: ((BotProfile) -> Unit)? = null,
    /** « Partager comme modèle » (Share as Template): the sheet of the link and its QR. */
    val onShareTemplate: ((BotProfile) -> Unit)? = null,
    // ————— The entries specific to Onyx, the earlier ones —————
    val onMarkSeen: ((BotProfile) -> Unit)? = null,
    val onMarkUnread: ((BotProfile) -> Unit)? = null,
    val onVoice: ((BotProfile) -> Unit)? = null,
    val onChangeFace: ((BotProfile) -> Unit)? = null,
    val onRemoveFromOnyx: ((BotProfile) -> Unit)? = null,
    val onReorder: (() -> Unit)? = null,
    val onSortByRecent: (() -> Unit)? = null,
    /** Does the bot have a conversation other than its chat? (« Ouvrir la dernière session », Open recent session). */
    val hasRecent: (BotProfile) -> Boolean = { false },
)

/**
 * The menu of a bot card, mounted from [handlers]: the **same** panel for the drawer and for the
 * « Bots » page, so that neither can forget an entry that the other has.
 *
 * « Supprimer » (Delete) goes through [onAskDelete]: the menu never deletes itself — it is the screen that
 * asks for confirmation first, like the reference.
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
    /** « Supprimer » (Delete): the screen asks the question, the menu merely requests it. */
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
        onShareTemplate = handlers.onShareTemplate?.let { act -> { act(bot) } },
    )
}

/**
 * The gesture of an entry: the one the screen provided, or nothing — the entry is then not shown.
 *
 * [frame] only enters for pinning: its gesture says the **target** state (« épingler » (pin) or
 * « désépingler » (unpin)), and it is the only place that inverts it — the screen, for its part, receives a decision.
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
    onShareTemplate: (() -> Unit)?,
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
    BotMenuAction.ShareTemplate -> onShareTemplate
}
