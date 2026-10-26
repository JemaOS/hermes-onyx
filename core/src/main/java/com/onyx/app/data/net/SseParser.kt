package com.onyx.app.data.net

/**
 * Un événement Server-Sent Events décodé.
 *
 * Comme `SSEEvent`.
 */
data class SseEvent(
    val event: String?,
    val data: String,
    val id: String?,
)

/**
 * Parseur SSE incrémental, ligne par ligne.
 *
 * Règles respectées (spec SSE + comportement ):
 * - une ligne vide déclenche l'événement en attente;
 * - les lignes commençant par `:` sont des commentaires (souvent des *heartbeats*);
 * - `champ: valeur` coupe au premier `:` et retire au plus un espace de tête;
 * - seuls `event`, `data` et `id` sont reconnus; `retry` et l'inconnu sont ignorés;
 * - plusieurs lignes `data:` sont concaténées avec `\n`.
 *
 * Comme `SSEParser`.
 */
class SseParser {
    private val dataLines = mutableListOf<String>()
    private var pendingEvent: String? = null
    private var pendingId: String? = null

    /** Injecte une ligne (sans le `\n`) et renvoie l'événement dispatché, le cas échéant. */
    fun feed(line: String): SseEvent? = consume(line)

    /** Termine le flux: dispatch un éventuel événement en attente non terminé par une ligne vide. */
    fun finish(): SseEvent? = dispatch()

    private fun consume(line: String): SseEvent? {
        if (line.isEmpty()) return dispatch()
        if (line.startsWith(":")) return null

        val colon = line.indexOf(':')
        val field = if (colon >= 0) line.substring(0, colon) else line
        var value = if (colon >= 0) line.substring(colon + 1) else ""
        if (value.startsWith(" ")) value = value.drop(1)

        return when (field) {
            "event" -> {
                pendingEvent = value
                null
            }
            "data" -> {
                dataLines += value
                null
            }
            "id" -> {
                pendingId = value
                null
            }
            else -> null
        }
    }

    private fun dispatch(): SseEvent? {
        if (dataLines.isEmpty() && pendingEvent == null && pendingId == null) return null
        val event = SseEvent(
            event = pendingEvent,
            data = dataLines.joinToString("\n"),
            id = pendingId,
        )
        dataLines.clear()
        pendingEvent = null
        pendingId = null
        return event
    }
}
