package com.onyx.app.data.net

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

/**
 * Ce que le serveur dit de son activité, par conversation et par salle.
 *
 * Comme `ChatActivity.kinds` / `ChatActivity.roomKinds` (
 * et:188), lus sur `session.active_list`, qui liste **toutes** les sessions vivantes du processus
 * du Dashboard — les cachées aussi (c'est ce qui laisse voir les salles travailler).
 */

/** L'état d'une conversation ou d'une salle: un tour en cours, ou une question posée à la personne. */
enum class ActivityKind { WORKING, NEEDS_YOU }

/** Ce que `session.active_list` dit, par clé de session et par id de salle. */
data class ActivityKinds(
    val sessions: Map<String, ActivityKind> = emptyMap(),
    val rooms: Map<String, ActivityKind> = emptyMap(),
) {
    /** Vrai si rien n'est signalé: la liste montre alors la date à la place du rond. */
    val isEmpty: Boolean get() = sessions.isEmpty() && rooms.isEmpty()
}

/**
 * L'activité du Dashboard, par le RPC (`session.active_list`).
 *
 * Une seule lecture donne les deux: la conversation où le bot écrit, et la salle où l'un de ses
 * bots écrit. C'est la source des pastilles « au travail » / « en attente » de la liste.
 */
class ServeActivityRpc(private val rpc: RpcChannel) {

    constructor(rpc: ServeRpc) : this(rpc.asChannel())

    suspend fun activeKinds(): ActivityKinds {
        val list = rpc.call("session.active_list", buildJsonObject { }, ACTIVE_LIST_TIMEOUT)
        return kindsOf(list)
    }

    companion object {
        const val ACTIVE_LIST_TIMEOUT = 15_000L

        /** Le titre qu'Hermes donne à la session d'un bot dans une salle (`GroupDraft.sessionTitle`). */
        const val GROUP_TITLE_PREFIX = "Group: "

        /** Les deux tables d'un même `session.active_list`. */
        fun kindsOf(activeList: JsonElement): ActivityKinds =
            ActivityKinds(sessions = kinds(activeList), rooms = roomKinds(activeList))

        /**
         * Les conversations où un tour est en cours, par identifiant de session stocké.
         * « starting » est un tour qui s'en va; les sessions cachées des salles n'en sont pas
         * (elles comptent pour leur salle), et la fin d'un bot n'est pas la fin d'une conversation.
         */
        fun kinds(activeList: JsonElement): Map<String, ActivityKind> {
            val found = mutableMapOf<String, ActivityKind>()
            for (item in rows(activeList)) {
                val title = item.strOrNull("title")
                if (title != null && title.startsWith(GROUP_TITLE_PREFIX)) continue
                val key = item.strOrNull("session_key") ?: item.strOrNull("id") ?: continue
                val kind = when (item.strOrNull("status")) {
                    "working", "starting" -> ActivityKind.WORKING
                    "waiting" -> ActivityKind.NEEDS_YOU
                    else -> null
                }
                if (kind != null) found[key] = kind
            }
            return found
        }

        /**
         * Les salles où des bots travaillent ou attendent, par identifiant de salle. Une salle
         * hébergée y est sous son propre id; une salle d'Hermes Desktop sous « desktop:id:<salle> ».
         * Une salle qui attend quelqu'un l'emporte sur une salle qui travaille.
         */
        fun roomKinds(activeList: JsonElement): Map<String, ActivityKind> {
            val found = mutableMapOf<String, ActivityKind>()
            for (item in rows(activeList)) {
                val title = item.strOrNull("title") ?: continue
                if (!title.startsWith(GROUP_TITLE_PREFIX)) continue
                val rest = title.removePrefix(GROUP_TITLE_PREFIX)
                val kind = when (item.strOrNull("status")) {
                    "working", "starting" -> ActivityKind.WORKING
                    "waiting" -> ActivityKind.NEEDS_YOU
                    else -> continue
                }
                val id = if (rest.contains(" · ")) "desktop:id:" + rest.substringBefore(" · ") else rest
                if (found[id] != ActivityKind.NEEDS_YOU) found[id] = kind
            }
            return found
        }

        private fun rows(activeList: JsonElement): List<JsonObject> =
            activeList.asObject()?.array("sessions")?.mapNotNull { it.asObject() }.orEmpty()
    }
}
