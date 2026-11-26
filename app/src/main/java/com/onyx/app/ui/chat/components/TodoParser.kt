package com.onyx.app.ui.chat.components

import com.onyx.app.data.model.ToolActivity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Lit la liste de tâches dans les appels d'outils d'une réponse (`TodoList` côté référence).
 *
 * Deux formes : un JSON de l'outil `todo` (`[{content, status}]`), et les cases à cocher en
 * Markdown qu'un agent écrit parfois à la place.
 */
fun todosFrom(tools: List<ToolActivity>): List<TodoItem> {
    val tool = tools.lastOrNull { it.name.contains("todo", ignoreCase = true) } ?: return emptyList()
    val text = tool.output ?: tool.args ?: return emptyList()

    val marks = MARKDOWN_ENTRY.findAll(text).toList()
    if (marks.isNotEmpty()) {
        return marks.map { match ->
            TodoItem(
                text = match.groupValues[2].trim(),
                status = if (match.groupValues[1].isNotBlank()) TodoItem.Status.DONE else TodoItem.Status.PENDING,
            )
        }
    }

    val array = runCatching {
        when (val root = Json.parseToJsonElement(text)) {
            is JsonArray -> root
            is JsonObject -> root["todos"] as? JsonArray
            else -> null
        }
    }.getOrNull() ?: return emptyList()

    return array.mapNotNull { element ->
        val item = element as? JsonObject ?: return@mapNotNull null
        val content = ((item["content"] ?: item["task"]) as? JsonPrimitive)?.contentOrNull
            ?: return@mapNotNull null
        val status = (item["status"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        TodoItem(
            text = content,
            status = when (status) {
                "completed", "done" -> TodoItem.Status.DONE
                "in_progress", "active" -> TodoItem.Status.IN_PROGRESS
                "cancelled", "dropped" -> TodoItem.Status.DROPPED
                else -> TodoItem.Status.PENDING
            },
        )
    }
}

/** « - [ ] chose » / « - [x] chose ». */
private val MARKDOWN_ENTRY = Regex("""[-*]\s*\[( |x|X)]\s*(.+)""")
