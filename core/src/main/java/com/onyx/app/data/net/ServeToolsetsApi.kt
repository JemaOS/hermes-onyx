package com.onyx.app.data.net

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Un jeu d'outils que le gateway peut activer (`Toolset`). */
data class GatewayToolset(
    val name: String,
    val label: String? = null,
    val description: String? = null,
    val enabled: Boolean = false,
    val tools: List<String> = emptyList(),
) {
    val title: String get() = label?.takeIf { it.isNotBlank() } ?: name
}

/**
 * Les jeux d'outils du gateway.
 *
 * Comme `HermesServeClient.toolsets`: `GET api/tools/toolsets`, et
 * `PUT api/tools/toolsets/{name}` pour la bascule.
 */
class ServeToolsetsApi(private val client: ServeClient) {

    suspend fun toolsets(): List<GatewayToolset> {
        val payload = client.request(
            "api/tools/toolsets",
            query = client.profile?.let { mapOf("profile" to it) }.orEmpty(),
        )
        val rows = payload.asObject()?.array("toolsets") ?: payload.asArray()
        return rows.orEmpty().mapNotNull { element ->
            val obj = element.asObject() ?: return@mapNotNull null
            val name = obj.firstString("name") ?: return@mapNotNull null
            GatewayToolset(
                name = name,
                label = obj.strOrNull("label"),
                description = obj.strOrNull("description"),
                enabled = obj["enabled"].asBool() ?: false,
                tools = obj.stringList("tools"),
            )
        }
    }

    suspend fun setEnabled(name: String, enabled: Boolean) {
        client.request(
            path = "api/tools/toolsets/$name",
            method = "PUT",
            body = buildJsonObject { put("enabled", enabled) },
        )
    }
}
