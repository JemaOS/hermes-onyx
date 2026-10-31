package com.onyx.app.ui.bots

import android.content.Context
import com.onyx.app.data.local.ActivityKeys
import com.onyx.app.data.model.Conversation
import com.onyx.app.data.model.serve.BotProfile
import com.onyx.app.data.model.serve.GroupRoom
import com.onyx.app.data.net.ActivityKind
import com.onyx.app.ui.conversations.BotActivity

/**
 * L'activité des bots et des salles telle que **ce téléphone** l'a vue, et l'ordre de la section
 * « Bots ».
 *
 * Miroir de deux états de la référence, gardés de la même façon (par connexion, puis par ligne) :
 * `ChatActivity.seen` (Echo/Services/ChatActivity:52) — que le serveur ne donne pas : Hermes
 * ne tient pas de marque de lecture pour le chat d'un bot ni pour une salle — et
 * `Settings.botOrderHere` (Echo/Services/Settings:254), l'ordre où la personne a mis les
 * lignes de la section.
 *
 * Les identifiants de ligne sont ceux de `BotListItem` : `bot:<profil>` et `room:<salle>`.
 */
class BotActivityStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(ActivityKeys.FILE, Context.MODE_PRIVATE)

    /** Ce qui a été vu de cette ligne : l'heure de sa dernière activité, ou 0 si marquée non lue. */
    fun seen(server: String, item: String): Double? = prefs.getString(ActivityKeys.seen(server, item), null)?.toDoubleOrNull()

    /** La ligne a été vue jusqu'à `upTo` (l'heure de sa dernière activité). */
    fun markSeen(server: String, item: String, upTo: Double) {
        prefs.edit().putString(ActivityKeys.seen(server, item), upTo.toString()).apply()
    }

    /** « Mark as Unread » : la ligne s'allume, quoi qu'elle ait fait depuis. */
    fun markUnread(server: String, item: String) = markSeen(server, item, 0.0)

    /**
     * Les lignes lues pour la première fois comptent comme vues jusqu'à leur dernière activité :
     * le premier regard sur la liste ne l'allume pas tout entière (`ChatActivity.listed`).
     * Une salle sans marque d'activité (jamais servie) n'est pas enregistrée.
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

    /** L'ordre de la section, vide s'il n'a jamais été mis. */
    fun order(server: String): List<String> =
        prefs.getString(ActivityKeys.order(server), "").orEmpty().split(ActivityKeys.SEPARATOR).filter { it.isNotBlank() }

    fun setOrder(server: String, ids: List<String>) {
        prefs.edit().putString(ActivityKeys.order(server), ids.joinToString(ActivityKeys.SEPARATOR)).apply()
    }

    private fun nowSeconds() = System.currentTimeMillis() / 1000.0
}

/** L'écart d'horloge toléré entre deux appareils (`ChatActivity.clockSlack`). */
internal const val CLOCK_SLACK = 5.0

/**
 * Un bot dont un worker (une carte kanban qu'on lui a donnée) s'est fait entendre depuis moins de
 * ça est « au travail » (`ChatActivity.workerLive`).
 */
internal const val WORKER_LIVE_MILLIS = 150_000L

/**
 * Le non-lu d'une ligne, comme `ChatActivity.unread` (Echo/Services/ChatActivity:97) :
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
 * Ce qu'un bot fait, comme `ChatActivity.kind(of:open:settings:)` (:254) : son chat ouvert ici et
 * en train d'écrire, la session que le serveur dit travailler dans son chat
 * (`session.active_list`, par l'id de sa conversation), ou un de ses workers entendu récemment.
 */
internal fun botKind(
    bot: BotProfile,
    live: Map<String, ActivityKind>,
    openStreaming: Boolean,
    nowMillis: Long,
): ActivityKind? {
    // Le chat de **ce** bot s'écrit ici : il faut qu'il ait un chat. Sans identifiant de chat, la
    // comparaison qui amène `openStreaming` ne peut dire que « n'importe quelle conversation »
    // (`ChatActivity.kind(of:)` : `guard let chat = bot.chatID`).
    if (openStreaming && !bot.chatId.isNullOrBlank()) return ActivityKind.WORKING
    bot.chatId?.let { chat -> live[chat]?.let { return it } }
    val worker = bot.workerActiveMillis
    if (worker != null && nowMillis - worker < WORKER_LIVE_MILLIS) return ActivityKind.WORKING
    return null
}

/**
 * Le chat d'un bot s'écrit-il **ici** ? (`ChatActivity.kind(of:)` : « la conversation ouverte ici,
 * pour **ce** bot, écrit »).
 *
 * Sans identifiant de chat connu, rien ne peut s'écrire pour lui : le garde est le même qu'à la
 * source (`guard let chat = bot.chatID`). Sans lui, `conversation.serverSessionId == bot.chatId`
 * compare **deux `null`** — les profils du serveur du propriétaire n'ont tous qu'un
 * `canonical_session: null` — et **toute** conversation en cours d'écriture allumait alors le rond
 * de chargement de **tous** les bots, sur une liste pourtant déjà chargée.
 */
internal fun botChatStreaming(chatId: String?, conversations: List<Conversation>): Boolean {
    val chat = chatId?.takeIf { it.isNotBlank() } ?: return false
    return conversations.any { conversation ->
        conversation.serverSessionId == chat && conversation.messages.any { it.isStreaming }
    }
}

/** Ce qu'une salle fait, comme `ChatActivity.kind(of room:)` (:305) : ce que dit `liveRooms`. */
internal fun roomKind(room: GroupRoom, rooms: Map<String, ActivityKind>): ActivityKind? = rooms[room.id]

/**
 * La pastille d'un bot : au travail, en attente de vous, ou quelque chose de pas encore vu.
 *
 * Le non-lu vient de ce que ce téléphone a vu (le serveur ne le dit pas pour le chat d'un bot) ;
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

/** La pastille d'une salle, même règle (`ChatActivity.isUnread(_:isOpen:settings:)`, :315). */
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
