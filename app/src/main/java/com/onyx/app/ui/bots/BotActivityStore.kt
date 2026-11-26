package com.onyx.app.ui.bots

import android.content.Context
import com.onyx.app.data.local.ActivityKeys
import com.onyx.app.data.model.Conversation
import com.onyx.app.data.model.serve.BotProfile
import com.onyx.app.data.model.serve.GroupRoom
import com.onyx.app.data.net.ActivityKind
import com.onyx.app.ui.conversations.BotActivity

/**
 * The activity of bots and rooms as **this phone** has seen it, and the order of the "Bots"
 * section.
 *
 * Mirror of two reference states, kept the same way (by connection, then by row):
 * `ChatActivity.seen` (Echo/Services/ChatActivity:52) — which the server does not give: Hermes
 * does not keep a read mark for a bot's chat nor for a room — and
 * `Settings.botOrderHere` (Echo/Services/Settings:254), the order in which the person put the
 * rows of the section.
 *
 * The row identifiers are those of `BotListItem`: `bot:<profil>` and `room:<salle>`.
 */
class BotActivityStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(ActivityKeys.FILE, Context.MODE_PRIVATE)

    /** What has been seen of this row: the time of its last activity, or 0 if marked unread. */
    fun seen(server: String, item: String): Double? = prefs.getString(ActivityKeys.seen(server, item), null)?.toDoubleOrNull()

    /** The row has been seen up to `upTo` (the time of its last activity). */
    fun markSeen(server: String, item: String, upTo: Double) {
        prefs.edit().putString(ActivityKeys.seen(server, item), upTo.toString()).apply()
    }

    /** "Mark as Unread": the row lights up, whatever it has done since. */
    fun markUnread(server: String, item: String) = markSeen(server, item, 0.0)

    /**
     * Rows read for the first time count as seen up to their last activity:
     * the first look at the list does not light it up entirely (`ChatActivity.listed`).
     * A room without an activity mark (never served) is not recorded.
     */
    fun listed(server: String, items: List<Pair<String, Double?>>, marksWithoutActivity: Boolean = false) {
        val editor = prefs.edit()
        var changed = false
        for ((item, mark) in items) {
            if (prefs.contains(ActivityKeys.seen(server, item))) continue
            val seen = mark ?: if (marksWithoutActivity) nowSeconds() else continue
            editor.putString(ActivityKeys.seen(server, item), seen.toString())
            changed = true
        }
        if (changed) editor.apply()
    }

    /** The order of the section, empty if it has never been set. */
    fun order(server: String): List<String> =
        prefs.getString(ActivityKeys.order(server), "").orEmpty().split(ActivityKeys.SEPARATOR).filter { it.isNotBlank() }

    fun setOrder(server: String, ids: List<String>) {
        prefs.edit().putString(ActivityKeys.order(server), ids.joinToString(ActivityKeys.SEPARATOR)).apply()
    }

    private fun nowSeconds() = System.currentTimeMillis() / 1000.0
}

/** The clock offset tolerated between two devices (`ChatActivity.clockSlack`). */
internal const val CLOCK_SLACK = 5.0

/**
 * A bot from which a worker (a kanban card it was given) has made itself heard for less than
 * this is "at work" (`ChatActivity.workerLive`).
 */
internal const val WORKER_LIVE_MILLIS = 150_000L

/**
 * The unread state of a row, like `ChatActivity.unread` (Echo/Services/ChatActivity:97):
 * neither the open row, nor a row where something is happening (a bot's activity while it
 * is working is not yet an answer), and an activity that came after the last read — or a
 * row marked unread by hand (`seen == 0`).
 */
internal fun isUnread(
    markSeconds: Double?,
    seen: Double?,
    isOpen: Boolean,
    busy: Boolean,
    slack: Double = CLOCK_SLACK,
): Boolean {
    if (isOpen || busy) return false
    if (markSeconds == null || seen == null) return false
    return seen == 0.0 || markSeconds > seen + slack
}

/**
 * What a bot is doing, like `ChatActivity.kind(of:open:settings:)` (:254): its chat open here and
 * writing, the session the server says is working in its chat
 * (`session.active_list`, by its conversation's id), or one of its workers heard recently.
 */
internal fun botKind(
    bot: BotProfile,
    live: Map<String, ActivityKind>,
    openStreaming: Boolean,
    nowMillis: Long,
): ActivityKind? {
    // **This** bot's chat is being written here: it must have a chat. Without a chat identifier, the
    // comparison that brings `openStreaming` can only say "any conversation"
    // (`ChatActivity.kind(of:)`: `guard let chat = bot.chatID`).
    if (openStreaming && !bot.chatId.isNullOrBlank()) return ActivityKind.WORKING
    bot.chatId?.let { chat -> live[chat]?.let { return it } }
    val worker = bot.workerActiveMillis
    if (worker != null && nowMillis - worker < WORKER_LIVE_MILLIS) return ActivityKind.WORKING
    return null
}

/**
 * Is a bot's chat being written **here**? (`ChatActivity.kind(of:)`: "the conversation open here,
 * for **this** bot, writing").
 *
 * Without a known chat identifier, nothing can be written for it: the guard is the same as at the
 * source (`guard let chat = bot.chatID`). Without it, `conversation.serverSessionId == bot.chatId`
 * compares **two `null`s** — the owner's server profiles all have only a
 * `canonical_session: null` — and **every** conversation being written then lit up the loading
 * circle of **all** bots, on a list that was already loaded.
 */
internal fun botChatStreaming(chatId: String?, conversations: List<Conversation>): Boolean {
    val chat = chatId?.takeIf { it.isNotBlank() } ?: return false
    return conversations.any { conversation ->
        conversation.serverSessionId == chat && conversation.messages.any { it.isStreaming }
    }
}

/** What a room is doing, like `ChatActivity.kind(of room:)` (:305): what `liveRooms` says. */
internal fun roomKind(room: GroupRoom, rooms: Map<String, ActivityKind>): ActivityKind? = rooms[room.id]

/**
 * A bot's badge: at work, waiting for you, or something not yet seen.
 *
 * Unread comes from what this phone has seen (the server does not say it for a bot's chat);
 * working and waiting, from the session the server says is alive.
 */
internal fun botActivity(
    bot: BotProfile,
    isOpen: Boolean,
    seen: Double?,
    kind: ActivityKind?,
    muted: Boolean,
): BotActivity = BotActivity(
    working = kind == ActivityKind.WORKING,
    needsYou = kind == ActivityKind.NEEDS_YOU,
    unread = !muted && isUnread(
        markSeconds = bot.lastActive?.let { it / 1000.0 },
        seen = seen,
        isOpen = isOpen,
        busy = kind != null,
    ),
)

/** The badge of a room, same rule (`ChatActivity.isUnread(_:isOpen:settings:)`, :315). */
internal fun roomActivity(
    markSeconds: Double?,
    isOpen: Boolean,
    seen: Double?,
    kind: ActivityKind?,
    muted: Boolean,
): BotActivity = BotActivity(
    working = kind == ActivityKind.WORKING,
    needsYou = kind == ActivityKind.NEEDS_YOU,
    unread = !muted && isUnread(
        markSeconds = markSeconds,
        seen = seen,
        isOpen = isOpen,
        busy = kind != null,
    ),
)
