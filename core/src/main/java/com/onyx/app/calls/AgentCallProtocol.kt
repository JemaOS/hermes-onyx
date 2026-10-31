package com.onyx.app.calls

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * L'outil « appelle-moi », **côté téléphone** — la partie pure, sans Android, donc éprouvable sur
 * JVM.
 *
 * Reprise de `core/phone/PhoneTool.kt` (l'identifiant de l'outil), de `androidApp/.../phone/CallMeTool.kt`
 * (la demande, la raison, l'issue rendue) et de `core/push/PushApi.kt` (le corps des requêtes) de
 * la référence.
 *
 * **Ce que fait la référence, tel quel** : l'outil `phone_call_me` ne vit **pas** sur le serveur
 * comme un outil du gateway — c'est le **plugin** installé sur le gateway (`herald-push`) qui le
 * déclare à l'agent, et l'app lui parle par des commandes (`phone tools`, `phone pending`,
 * `phone answer`). Toute la mécanique est donc : *l'app déclare son outil, on sonne, et l'issue
 * remonte*. Ce fichier tient exactement ça, sans Android.
 */
object AgentCallTool {

    /** L'identifiant de l'outil, tel que la référence le nomme (`PhoneTool.CallMe`). */
    const val CALL_ME = "phone_call_me"

    /** Le préfixe des outils du téléphone, côté référence (`PhoneToolsBridge.TOOL_PREFIX`). */
    const val PREFIX = "phone_"

    /** La commande sous laquelle le plugin du gateway écoute (`herald-push` chez la référence). */
    const val PLUGIN_COMMAND = "onyx-calls"
}

/**
 * Une demande d'appel placée par l'agent, telle que le plugin la rend (`phone pending`).
 *
 * [session] : la conversation d'où l'agent a appelé — c'est **là** que Hermes se met en ligne quand
 * on décroche. [expires] : l'instant (epoch secondes du gateway) au-delà duquel l'agent n'attend
 * plus ; une demande déjà périmée ne sonne pas.
 */
data class AgentCallRequest(
    val id: String,
    val tool: String = AgentCallTool.CALL_ME,
    val reason: String,
    val details: String? = null,
    val session: String = "",
    val expires: Long = 0,
)

/** L'issue d'un appel, telle qu'elle **remonte à l'agent** (`status`, et `until` si silence). */
data class AgentCallAnswer(val status: CallOutcome, val until: String? = null) {

    /** Le corps renvoyé par `phone answer` (`buildJsonObject` de `CallMeTool.kt:28-32`). */
    fun toJson(): JsonObject = buildJsonObject {
        put("status", status.id)
        // Quand il pourra resonner, pour que l'agent place un rappel à ce moment-là.
        if (until != null) put("until", until)
    }
}

/**
 * Le protocole pur : lire la réponse du plugin, et décider ce que l'app fait.
 *
 * Rien ici ne touche Android : ni notification, ni écran, ni réseau. C'est « quand sonner »,
 * « pourquoi », et « quoi répondre » — le reste vit côté plateforme.
 */
object AgentCallProtocol {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Les demandes en attente, lues dans la réponse de `phone pending` du plugin
     * (`{"ok":true,"requests":[…],"now":…}`). Illisible ou refusée : **la liste vide**, jamais une
     * exception — un pont de fond ne doit pas mourir sur une trame qu'il ne comprend pas.
     */
    fun parsePending(reply: String, now: Long = 0): List<AgentCallRequest> {
        val root = runCatching { json.parseToJsonElement(reply) as? JsonObject }.getOrNull() ?: return emptyList()
        if (root.boolean("ok") == false) return emptyList()
        val requests = root["requests"] as? JsonArray ?: return emptyList()
        return requests.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val id = obj.string("id")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val tool = obj.string("tool")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            // Les arguments portent la raison et les détails (l'outil `phone_call_me`).
            val args = obj["args"] as? JsonObject
            val reason = args?.string("reason")?.trim()?.takeIf { it.isNotEmpty() }
                ?: obj.string("reason")?.trim()?.takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null
            val details = args?.string("details")?.trim()?.takeIf { it.isNotEmpty() }
                ?: obj.string("details")?.trim()?.takeIf { it.isNotEmpty() }
            val expires = obj.long("expires") ?: 0L
            // Périmée : l'agent n'attend plus, inutile de sonner.
            if (expires > 0 && now > 0 && expires <= now) return@mapNotNull null
            AgentCallRequest(
                id = id,
                tool = tool,
                reason = reason,
                details = details,
                session = obj.string("session").orEmpty(),
                expires = expires,
            )
        }
    }

    /** La raison d'appel pour l'écran et la voix, dérivée de la demande (`CallMeTool.kt:20-26`). */
    fun reasonOf(request: AgentCallRequest): CallReason = CallReason(request.reason, request.details)

    /**
     * Ce que l'app répond à l'agent **sans sonner** : les heures de tranquillité, ou une demande qui
     * n'est pas un appel. Rend `null` quand il faut vraiment sonner.
     *
     * Les heures de tranquillité portent `Quiet` **et** l'heure à laquelle il pourra resonner
     * (`CallMeTool.kt:31`) : l'agent doit savoir **quand** rappeler, pas seulement que c'était
     * silencieux.
     */
    fun silentAnswer(
        request: AgentCallRequest,
        quietNow: Boolean,
        quietUntilMinute: Int,
    ): AgentCallAnswer? = when {
        request.tool != AgentCallTool.CALL_ME -> AgentCallAnswer(CallOutcome.BUSY)
        quietNow -> AgentCallAnswer(CallOutcome.QUIET, until = QuietHours.clock(quietUntilMinute))
        else -> null
    }

    /**
     * Le corps de `phone tools` : l'app **déclare** au plugin les outils qu'elle peut servir
     * (`PushApi.phoneTools`, `PushApi.kt:347-359`).
     */
    fun toolsReport(deviceId: String, key: String, name: String, tools: List<String>): String =
        buildJsonObject {
            put("device_id", deviceId)
            put("key", key)
            put("name", name)
            putJsonArray("tools") { tools.forEach { add(JsonPrimitive(it)) } }
        }.toString()

    /** Le corps de `phone pending` : « qu'est-ce qui m'attend ? ». */
    fun pendingQuery(deviceId: String, key: String): String =
        buildJsonObject {
            put("device_id", deviceId)
            put("key", key)
        }.toString()

    /**
     * Le corps de `phone answer` : l'issue rendue à l'agent. `result` quand ça a abouti (`status`,
     * et `until` si silence), `error` quand non (`PhoneToolsBridge.answer`).
     */
    fun answerBody(deviceId: String, key: String, requestId: String, answer: AgentCallAnswer): String =
        buildJsonObject {
            put("device_id", deviceId)
            put("key", key)
            put("id", requestId)
            put("result", answer.toJson())
        }.toString()

    /** Le corps de `phone answer` quand l'appel a échoué : l'agent lit [error]. */
    fun errorBody(deviceId: String, key: String, requestId: String, error: String): String =
        buildJsonObject {
            put("device_id", deviceId)
            put("key", key)
            put("id", requestId)
            put("error", error)
        }.toString()

    /** L'identifiant est-il bien formé (32 hexadécimaux, `PhoneToolsBridge.REQUEST_ID`) ? */
    fun isWellFormedId(id: String): Boolean = id.length == 32 && id.all { it in "0123456789abcdef" }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull

    private fun JsonObject.boolean(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()
}
