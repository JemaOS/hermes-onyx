package com.onyx.app.data.net

import com.onyx.app.data.model.SubagentActivity
import com.onyx.app.data.model.SubagentUpdate
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Les sous-agents d'une session, par le RPC du Dashboard (`hermes serve`).
 *
 * Comme `subagentSteer` / `subagentInterrupt` de `HermesServeClient` , **plus** les
 * deux routes que le gateway expose par ailleurs (`subagent.list`, `subagent.tail`) et qui sont la
 * seule façon de voir et de lire un enfant **sans tour en cours**:
 *
 * - `subagent.list { session_id }` → le roster des enfants vivants (`subagent_id`, but, profondeur,
 * modèle, date de départ, état, nombre d'outils, dernier outil, `accepting_steer`);
 * - `subagent.tail { session_id, subagent_id }` → les derniers ko de la transcription vivante de
 * l'enfant (`available`, `text`, `truncated`), ou « rien » quand il n'en a pas;
 * - `subagent.steer { subagent_id, text }` → « queued » / « rejected »;
 * - `subagent.interrupt { subagent_id }` → « found » (faux quand l'enfant a déjà fini).
 *
 * L'autorité de pilotage vient du **transport** attaché à la session, pas du seul identifiant:
 * une session inconnue du socket répond `4001` et rien n'est piloté en silence.
 */
class ServeSubagentRpc(
    private val rpc: RpcChannel,
    private val closer: (() -> Unit)? = null,
) {

    constructor(rpc: ServeRpc) : this(rpc.asChannel(), { rpc.close() })

    /** Le roster des sous-agents vivants de cette session, tels que le gateway les listes. */
    suspend fun list(sessionId: String): List<SubagentActivity> {
        if (sessionId.isBlank()) return emptyList()
        val result = rpc.call(
            "subagent.list",
            buildJsonObject { put("session_id", sessionId) },
            timeoutMillis = 15_000,
        ).asObject() ?: return emptyList()
        return (result["subagents"] as? JsonArray)
            ?.mapNotNull { (it as? JsonObject)?.let(SubagentUpdate::fromRoster) }
            .orEmpty()
    }

    /**
     * La transcription vivante d'un enfant, ou `null` quand le gateway n'a rien à en dire.
     *
     * `available: false` est une réponse normale (l'enfant démarre, ou il n'a rien écrit): la
     * transcription reste vide et l'écran le dit, au lieu de tourner en boucle sur un chargement.
     */
    suspend fun tail(sessionId: String, subagentId: String): SubagentTail? {
        if (subagentId.isBlank()) return null
        val params = buildJsonObject {
            if (sessionId.isNotBlank()) put("session_id", sessionId)
            put("subagent_id", subagentId)
        }
        val result = rpc.call("subagent.tail", params, timeoutMillis = 20_000).asObject() ?: return null
        if (result.bool("available") != true) {
            return SubagentTail(text = "", available = false, truncated = false)
        }
        return SubagentTail(
            text = result.str("text"),
            available = true,
            truncated = result.bool("truncated") == true,
        )
    }

    /**
     * Envoie une consigne au sous-agent; `false` s'il ne l'accepte pas pour le moment.
     *
     * [sessionId] est l'identifiant **runtime** du parent: sans lui le gateway refuse (`4001`),
     * l'autorité de pilotage n'étant pas déduite du seul `subagent_id`.
     */
    suspend fun steer(sessionId: String, id: String, text: String): Boolean {
        val result = rpc.call(
            "subagent.steer",
            buildJsonObject {
                if (sessionId.isNotBlank()) put("session_id", sessionId)
                put("subagent_id", id)
                put("text", text)
            },
            timeoutMillis = 30_000,
        ).asObject()
        return result?.firstString("status") == "queued"
    }

    /** Interrompt le sous-agent; `false` s'il avait déjà fini. */
    suspend fun interrupt(sessionId: String, id: String): Boolean {
        val result = rpc.call(
            "subagent.interrupt",
            buildJsonObject {
                if (sessionId.isNotBlank()) put("session_id", sessionId)
                put("subagent_id", id)
            },
            timeoutMillis = 30_000,
        ).asObject()
        return result?.bool("found") ?: (result?.firstString("found") == "true")
    }

    fun close() {
        closer?.invoke()
    }
}

/** Ce que `subagent.tail` a rendu: la fin de la transcription vivante de l'enfant. */
data class SubagentTail(
    val text: String,
    val available: Boolean,
    val truncated: Boolean,
)
