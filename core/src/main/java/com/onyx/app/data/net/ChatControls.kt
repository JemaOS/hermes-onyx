package com.onyx.app.data.net

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Les deux réglages d'une conversation sur Hermes (`ChatControls`). */
data class ChatControls(
    val autoApprove: Boolean = false,
    val approvalMode: String = "",
    val fast: Boolean = false,
) {
    val serverNeverAsks: Boolean get() = approvalMode == "off"

    /** Fusionne une `session.info`, y compris courte, sans effacer ce qu'elle ne répète pas. */
    fun merged(info: JsonObject?): ChatControls {
        if (info == null) return this
        val fastValue = info["fast"]?.asBool()
        val tier = info["service_tier"]?.asString()
        return copy(
            autoApprove = info["yolo"]?.asBool() ?: autoApprove,
            approvalMode = info["approval_mode"]?.asString() ?: approvalMode,
            fast = fastValue ?: tier?.let { it == "priority" } ?: fast,
        )
    }

    companion object {
        /** Traduit les deux refus Hermes attendus, sans réécrire les autres erreurs du serveur. */
        fun fastRefusal(message: String): String = when {
            message.contains("not available for this model", ignoreCase = true) ->
                "This model has no fast mode."
            message.contains("without a selected model", ignoreCase = true) ->
                "Pick a model first: fast mode belongs to a model."
            else -> message
        }

        const val fastFooter = "Fast mode asks the provider for its faster tier, which usually costs more; only some models have one."
        const val serverNeverAsksFooter = "This server never asks before a command: approvals are off in Hermes's config, for every conversation."
        const val conversationAutoApproveFooter = "Without asking, Hermes runs every command it wants to in this conversation, until you switch it back or Hermes restarts."
        const val apiFastFooter = "It is asked for on each of this conversation's turns; a model without one answers as usual."
    }
}

/** Ce que la reprise d'une session apprend au menu et au menu Projet. */
data class ChatSessionInfo(
    val controls: ChatControls,
    val runtimeSessionId: String,
    val projectName: String? = null,
)

/** Les appels Dashboard de `ChatControlsSection`, sur la session et jamais sur la config globale. */
class ChatControlsApi(private val rpc: ServeRpc) {
    suspend fun read(stored: String): ChatSessionInfo {
        val resumed = rpc.call(
            "session.resume",
            buildJsonObject {
                put("session_id", stored)
                put("omit_messages", true)
            },
        ).asObject()
        val info = resumed?.obj("info")
        return ChatSessionInfo(
            controls = ChatControls().merged(info),
            runtimeSessionId = resumed?.strOrNull("session_id") ?: stored,
            projectName = info?.obj("project")?.strOrNull("name"),
        )
    }

    suspend fun setFast(on: Boolean, stored: String): ChatControls {
        val current = read(stored)
        val answer = setControl(current.runtimeSessionId, "fast", if (on) "fast" else "normal")
        return current.controls.copy(fast = answer == "fast")
    }

    suspend fun setAutoApprove(on: Boolean, stored: String): ChatControls {
        val current = read(stored)
        val answer = setControl(current.runtimeSessionId, "yolo", if (on) "on" else "off")
        return current.controls.copy(
            autoApprove = answer == "1" || answer.equals("true", ignoreCase = true) ||
                answer.equals("on", ignoreCase = true) || current.controls.serverNeverAsks,
        )
    }

    private suspend fun setControl(runtime: String, key: String, value: String): String =
        rpc.call(
            "config.set",
            buildJsonObject {
                put("session_id", runtime)
                put("key", key)
                put("value", value)
            },
        ).asObject()?.firstString("value").orEmpty()
}
