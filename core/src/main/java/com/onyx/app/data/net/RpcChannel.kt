package com.onyx.app.data.net

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Le canal d'appel JSON-RPC du Dashboard.
 *
 * C'est `ServeRpc` en vrai (WebSocket + ticket) ; les classes qui listent les salles et lisent
 * l'activité du serveur s'en servent par ce trait, pour se laisser éprouver sans socket.
 */
fun interface RpcChannel {
    suspend fun call(method: String, params: JsonObject, timeoutMillis: Long): JsonElement
}

/** Le canal d'un `ServeRpc` déjà construit. */
fun ServeRpc.asChannel(): RpcChannel = RpcChannel { method, params, timeoutMillis -> call(method, params, timeoutMillis) }
