package com.onyx.app.data.net

import com.onyx.app.data.model.serve.McpServerDraft
import com.onyx.app.localization.OnyxLocalization
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray

/**
 * Les refus réels de l'ajout d'un serveur MCP, traduits en phrases claires.
 *
 * Deux familles, tenues séparées parce qu'elles n'ont pas la même cause :
 *
 * 1. **Ce qui se voit avant l'envoi** ([mcpDraftProblem]) : un nom manquant, deux transports à la
 *    fois, une variable d'environnement sans « = ». Le formulaire le dit et n'envoie rien — inutile
 *    de faire un aller-retour pour un champ vide.
 * 2. **Ce que le serveur répond** ([mcpFailureMessage]) : le Dashboard valide lui aussi, et ses
 *    refus arrivent en **400/409/422** avec un `detail` en anglais (`Provide exactly one of URL…`).
 *    Le lire est ce qui permet de dire *pourquoi* plutôt qu'un code HTTP.
 *
 * Aucune de ces fonctions n'invente un succès : elles ne rendent que ce que la réponse contient.
 */

/**
 * Le premier empêchement de [draft], ou `null` si la demande peut partir.
 *
 * Rend une **clé du dictionnaire** (une phrase anglaise, comme partout dans l'app) : l'écran
 * l'affiche telle quelle et la traduction se fait au rendu.
 */
fun mcpDraftProblem(draft: McpServerDraft): String? = when {
    draft.name.isBlank() -> "Give the server a name."
    draft.transport == "http" && draft.url.isBlank() -> "Give the server's address."
    draft.transport != "http" && draft.command.isBlank() -> "Give the command that starts the server."
    draft.malformedEnvironment.isNotEmpty() -> "Each environment variable needs a name and a value, like KEY=value."
    draft.transport == "http" && draft.auth == "header" && draft.bearerToken.isBlank() ->
        "A token is required for header authentication."
    else -> null
}

/**
 * Le message d'un échec d'ajout, de suppression ou de test.
 *
 * Quand la réponse porte un `detail` en anglais, il est reconnu et dit en français ; sinon le
 * message générique du transport est rendu, **avec** le corps du serveur (jamais un code seul).
 */
fun mcpFailureMessage(error: Throwable, serverName: String? = null): String {
    val http = error as? TransportError ?: return error.message.orEmpty()
    if (http !is TransportError.Http) return http.userMessage()

    val detail = detailOf(http.body)
    // Un refus de champ ou de contenu : ce sont les seuls codes dont le `detail` porte une cause
    // qu'on sait dire. Un 401, un 403, un 429 ou un 5xx gardent le message du transport, qui
    // **nomme le code** et garde le corps du serveur — mieux qu'une phrase choisie pour lui.
    if (http.status !in setOf(400, 404, 409, 422)) return http.userMessage()
    // Le serveur a refusé la requête elle-même : un champ obligatoire manque (Pydantic, 422).
    if (http.status == 422) {
        return OnyxLocalization["The server needs every required field. Check the form and send again."]
    }
    if (detail == null) return http.userMessage()

    val french = when {
        detail.startsWith("Server name is required") ->
            OnyxLocalization["Give the server a name."]

        detail.startsWith("Provide exactly one of URL") ->
            OnyxLocalization["Give one transport only: an address, or a command — not both."]

        detail.startsWith("Unsupported auth mode:") ->
            OnyxLocalization.format("Unsupported authentication: %1\$s.", detail.substringAfter(':').trim())

        detail.startsWith("Arguments are only supported") ->
            OnyxLocalization["Arguments belong to a command, not to an address."]

        detail.startsWith("Environment variables are only supported") ->
            OnyxLocalization["Environment variables belong to a command, not to an address."]

        detail.startsWith("Bearer token is required") ->
            OnyxLocalization["A token is required for header authentication."]

        detail.startsWith("Bearer token requires header authentication") ->
            OnyxLocalization["A token goes with header authentication only."]

        detail.startsWith("HTTP authentication is not supported") ->
            OnyxLocalization["A command-launched server has no HTTP authentication."]

        detail.contains("already exists") ->
            OnyxLocalization.format("A server named %1\$s already exists.", serverName ?: quoted(detail).orEmpty())

        detail.contains("is provided by plugin") ->
            OnyxLocalization.format(
                "%1\$s is supplied by the plugin %2\$s; it can't be changed here.",
                serverName ?: quoted(detail).orEmpty(),
                lastQuoted(detail).orEmpty(),
            )

        detail.endsWith("not found") ->
            OnyxLocalization.format("The server %1\$s wasn't found.", serverName ?: quoted(detail).orEmpty())

        detail.contains("rejected:") ->
            OnyxLocalization.format(
                "%1\$s was refused: %2\$s",
                serverName ?: quoted(detail).orEmpty(),
                detail.substringAfter("rejected:").trim(),
            )

        else -> null
    }
    // Rien de reconnu : le corps du serveur, tel qu'il l'a écrit — pas un code HTTP nu.
    return french ?: OnyxLocalization.format("The server refused: %1\$s", detail)
}

/**
 * Le `detail` d'un corps d'erreur, quand il y en a un.
 *
 * `detail` est une chaîne pour nos refus, et un **tableau** pour ceux de FastAPI (un champ
 * obligatoire manquant : `[{"loc":["body","name"],"msg":"Field required"}]`) — les deux formes se
 * lisent, parce que les deux arrivent pour la même raison : une demande mal formée.
 */
fun detailOf(body: String): String? {
    if (body.isBlank()) return null
    val element = runCatching { kotlinx.serialization.json.Json.parseToJsonElement(body) }.getOrNull() ?: return null
    val obj = element as? JsonObject ?: return null
    val detail = obj["detail"] ?: return null
    (detail as? JsonPrimitive)?.takeIf { it.isString }?.let { return it.content }
    val first = runCatching { detail.jsonArray.firstOrNull() }.getOrNull()?.let { it as? JsonObject } ?: return null
    return first["msg"]?.let { (it as? JsonPrimitive)?.content }
}

/** Le premier mot entre guillemets simples d'un message du serveur (`Server 'fs' …`). */
private fun quoted(text: String): String? = quotes(text).firstOrNull()

/** Le **dernier** mot entre guillemets (`… plugin 'kanban' …`). */
private fun lastQuoted(text: String): String? = quotes(text).lastOrNull()

/** Tous les mots entre guillemets simples, dans l'ordre où le serveur les a écrits. */
private fun quotes(text: String): List<String> =
    Regex("'([^']*)'").findAll(text).mapNotNull { it.groupValues.getOrNull(1)?.takeIf { word -> word.isNotEmpty() } }.toList()
