package com.onyx.app.data.net

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Les réponses aux interruptions, par le RPC du Dashboard.
 *
 * Comme `respondClarify` / `respondSudo` / `respondSecret`: ce sont des appels
 * `clarify.lock`, `sudo.respond` et `secret.respond`, que les routes REST ne portent pas.
 */
class ServeInterruptRpc(private val rpc: ServeRpc) {

    /** Répond à une question; la dernière réponse d'une série règle la demande. */
    suspend fun clarify(requestId: String, questionId: String?, answer: String) {
        val params = buildJsonObject {
            put("request_id", requestId)
            if (!questionId.isNullOrEmpty()) put("question_id", questionId)
            put("answer", answer)
        }
        rpc.call("clarify.lock", params, timeoutMillis = 30_000)
    }

    suspend fun sudo(sessionId: String, requestId: String, password: String) {
        rpc.call(
            "sudo.respond",
            buildJsonObject {
                put("session_id", sessionId)
                put("request_id", requestId)
                put("password", password)
            },
            timeoutMillis = 30_000,
        )
    }

    /** Une valeur vide veut dire « passé »: le gateway note que rien n'a été fourni. */
    suspend fun secret(sessionId: String, requestId: String, value: String) {
        rpc.call(
            "secret.respond",
            buildJsonObject {
                put("session_id", sessionId)
                put("request_id", requestId)
                put("value", value)
            },
            timeoutMillis = 30_000,
        )
    }
}
