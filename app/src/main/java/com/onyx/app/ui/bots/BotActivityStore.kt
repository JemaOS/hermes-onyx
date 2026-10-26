package com.onyx.app.ui.bots

import android.content.Context
import com.onyx.app.data.model.serve.BotProfile
import com.onyx.app.data.model.serve.GroupRoom
import com.onyx.app.data.net.ActivityKind
import com.onyx.app.ui.conversations.BotActivity

/**
 * L'activité des bots et des salles telle que **ce téléphone** l'a vue, et l'ordre de la section
 * « Bots ».
 *
 * Comme deux états , gardés de la même façon (par connexion, puis par ligne):
 * `ChatActivity.seen` — que le serveur ne donne pas: Hermes
 * ne tient pas de marque de lecture pour le chat d'un bot ni pour une salle — et
 * `Settings.botOrderHere` , l'ordre où la personne a mis les
 * lignes de la section.
 *
 * Les identifiants de ligne sont ceux de `BotListItem`: `bot:<profil>` et `room:<salle>`.
 */
class BotActivityStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("onyx_bot_activity", Context.MODE_PRIVATE)

    /** Ce qui a été vu de cette ligne: l'heure de sa dernière activité, ou 0 si marquée non lue. */
    fun seen(server: String, item: String): Double? = prefs.getString(key(server, item), null)?.toDoubleOrNull()

    /** La ligne a été vue jusqu'à `upTo` (l'heure de sa dernière activité). */
    fun markSeen(server: String, item: String, upTo: Double) {
        prefs.edit().putString(key(server, item), upTo.toString()).apply()
    }

    /** « Mark as Unread »: la ligne s'allume, quoi qu'elle ait fait depuis. */
    fun markUnread(server: String, item: String) = markSeen(server, item, 0.0)

    /**
     * Les lignes lues pour la première fois comptent comme vues jusqu'à leur dernière activité:
     * le premier regard sur la liste ne l'allume pas tout entière (`ChatActivity.listed`).
     * Une salle sans marque d'activité (jamais servie) n'est pas enregistrée.
     */
    fun listed(server: String, items: List<Pair<String, Double?>>, marksWithoutActivity: Boolean = false) {
        val editor = prefs.edit()
        var changed = false
        for ((item, mark) in items) {
            if (prefs.contains(key(server, item))) continue
            val seen = mark ?: if (marksWithoutActivity) nowSeconds() else continue
            editor.putString(key(server, item), seen.toString())
            changed = true
        }
        if (changed) editor.apply()
    }

    /** L'ordre de la section, vide s'il n'a jamais été mis. */
    fun order(server: String): List<String> =
        prefs.getString(orderKey(server), "").orEmpty().split(SEPARATOR).filter { it.isNotBlank() }

    fun setOrder(server: String, ids: List<String>) {
        prefs.edit().putString(orderKey(server), ids.joinToString(SEPARATOR)).apply()
    }

    private fun key(server: String, item: String) = "seen|$server|$item"

    private fun orderKey(server: String) = "order|$server"

    private fun nowSeconds() = System.currentTimeMillis() / 1000.0

    private companion object {
        const val SEPARATOR = "\u001F"
    }
}

/** L'écart d'horloge toléré entre deux appareils (`ChatActivity.clockSlack`). */
internal const val CLOCK_SLACK = 5.0

/**
 * Un bot dont un worker (une carte kanban qu'on lui a donnée) s'est fait entendre depuis moins de
 * ça est « au travail » (`ChatActivity.workerLive`).
 */
internal const val WORKER_LIVE_MILLIS = 150_000L

/**
 * Le non-lu d'une ligne, comme `ChatActivity.unread`:
 * ni la ligne ouverte, ni une ligne où quelque chose se passe (l'activité d'un bot pendant qu'il
 * travaille n'est pas encore une réponse), et une activité venue après la dernière lecture — ou une
 * ligne marquée non lue à la main (`seen == 0`).
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
 * Ce qu'un bot fait, comme `ChatActivity.kind(of:open:settings:)` (254): son chat ouvert ici et
 * en train d'écrire, la session que le serveur dit travailler dans son chat
 * (`session.active_list`, par l'id de sa conversation), ou un de ses workers entendu récemment.
 */
internal fun botKind(
    bot: BotProfile,
    live: Map<String, ActivityKind>,
    openStreaming: Boolean,
    nowMillis: Long,
): ActivityKind? {
    if (openStreaming) return ActivityKind.WORKING
    bot.chatId?.let { chat -> live[chat]?.let { return it } }
    val worker = bot.workerActiveMillis
    if (worker != null && nowMillis - worker < WORKER_LIVE_MILLIS) return ActivityKind.WORKING
    return null
}

/** Ce qu'une salle fait, comme `ChatActivity.kind(of room:)` (305): ce que dit `liveRooms`. */
internal fun roomKind(room: GroupRoom, rooms: Map<String, ActivityKind>): ActivityKind? = rooms[room.id]

/**
 * La pastille d'un bot: au travail, en attente de vous, ou quelque chose de pas encore vu.
 *
 * Le non-lu vient de ce que ce téléphone a vu (le serveur ne le dit pas pour le chat d'un bot);
 * le travail et l'attente, de la session que le serveur dit vivante.
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

/** La pastille d'une salle, même règle (`ChatActivity.isUnread(_:isOpen:settings:)`,:315). */
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
