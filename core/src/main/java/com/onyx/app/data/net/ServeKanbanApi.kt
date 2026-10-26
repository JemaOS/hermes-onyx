package com.onyx.app.data.net

import com.onyx.app.data.model.serve.KanbanCard
import com.onyx.app.data.model.serve.KanbanComment
import com.onyx.app.data.model.serve.KanbanStatus
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Le tableau tel que le plugin le rend: ses cartes, ses assignés et son curseur d'événements. */
data class KanbanBoard(
    val cards: List<KanbanCard>,
    val assignees: List<String> = emptyList(),
    /**
     * `latest_event_id`: de là que le socket d'événements reprend (`since=`), comme le `Board`
     * de `KanbanClient`.
     */
    val latestEventId: Int = 0,
)

/**
 * Le tableau kanban du Dashboard.
 *
 * Comme `KanbanClient`: même base
 * (`api/plugins/kanban`), mêmes colonnes, mêmes routes, et les mêmes champs facultatifs.
 */
class ServeKanbanApi(private val client: ServeClient) {

    /** `GET api/plugins/kanban/board`. */
    suspend fun board(): KanbanBoard {
        val root = client.request("$BASE/board")
        val latestEventId = root.asObject()?.int("latest_event_id") ?: 0
        val assignees = root.asObject()?.array("assignees")
            .orEmpty()
            .mapNotNull { it.asString() ?: it.asObject()?.firstString("name", "profile") }
        // Le plugin kanban de Hermes répond `columns: [{name, tasks: [...]}]`: le statut d'une
        // carte est le **nom de sa colonne**, et les tâches imbriquées ne portent pas toujours
        // `status`. Sans ce cas, le tableau s'affichait vide (« Nothing in Triage ») alors que
        // le serveur répondait 200 avec les colonnes.
        val columns = root.asObject()?.array("columns")
        if (columns != null) {
            val cards = columns.flatMap { element ->
                val column = element.asObject() ?: return@flatMap emptyList()
                val name = column.firstString("name", "id", "column").orEmpty()
                column.array("tasks").mapNotNull { task ->
                    val obj = task.asObject() ?: return@mapNotNull null
                    val card = card(obj)
                    if (name.isEmpty()) card else card.copy(status = KanbanStatus.fromId(name).id)
                }
            }
            return KanbanBoard(cards, assignees, latestEventId)
        }

        val rows = root.asObject()?.array("tasks")
            ?: root.asObject()?.array("cards")
            ?: root.asArray()
        val cards = rows.orEmpty().mapNotNull { element ->
            val obj = element.asObject() ?: return@mapNotNull null
            card(obj)
        }
        return KanbanBoard(cards, assignees, latestEventId)
    }

    /** `GET api/plugins/kanban/tasks/{id}` — la carte avec ses commentaires. */
    suspend fun task(id: String): KanbanCard? {
        val payload = client.request("$BASE/tasks/$id").asObject() ?: return null
        val obj = payload.obj("task") ?: payload
        val comments = obj.array("comments").mapNotNull { element ->
            val comment = element.asObject() ?: return@mapNotNull null
            KanbanComment(
                id = comment.firstString("id").orEmpty(),
                author = comment.firstString("author", "by").orEmpty(),
                body = comment.firstString("body", "text").orEmpty(),
                at = comment.firstString("created_at", "at"),
            )
        }
        return card(obj).copy(comments = comments)
    }

    /** `POST api/plugins/kanban/tasks`. */
    suspend fun create(
        title: String,
        body: String?,
        priority: Int,
        assignee: String?,
        startReady: Boolean,
    ): KanbanCard? {
        val payload = client.request(
            path = "$BASE/tasks",
            method = "POST",
            body = buildJsonObject {
                put("title", title)
                if (!body.isNullOrBlank()) put("body", body)
                put("priority", priority)
                if (!assignee.isNullOrBlank()) put("assignee", assignee)
                // Le board attend un drapeau `triage`, pas une colonne.
                put("triage", !startReady)
            },
        ).asObject()
        return payload?.let { card(it.obj("task") ?: it) }
    }

    /**
     * `PATCH api/plugins/kanban/tasks/{id}` — le déplacement porte la raison demandée
     * (`block_reason` ou `summary` selon la colonne).
     */
    suspend fun update(
        id: String,
        status: KanbanStatus? = null,
        title: String? = null,
        body: String? = null,
        priority: Int? = null,
        assignee: String? = null,
        note: String? = null,
    ) {
        val payload = buildJsonObject {
            status?.let { put("status", it.id) }
            title?.let { put("title", it) }
            body?.let { put("body", it) }
            priority?.let { put("priority", it) }
            assignee?.let { put("assignee", it) }
            if (!note.isNullOrBlank()) {
                when (status) {
                    KanbanStatus.BLOCKED, KanbanStatus.SCHEDULED -> put("block_reason", note)
                    else -> put("summary", note)
                }
            }
        }
        client.request(path = "$BASE/tasks/$id", method = "PATCH", body = payload)
    }

    /** `DELETE api/plugins/kanban/tasks/{id}`. */
    suspend fun delete(id: String) {
        client.request("$BASE/tasks/$id", method = "DELETE", body = client.emptyBody())
    }

    /** `POST api/plugins/kanban/tasks/{id}/comments`, signé `onyx` comme. */
    suspend fun comment(id: String, body: String) {
        client.request(
            path = "$BASE/tasks/$id/comments",
            method = "POST",
            body = buildJsonObject {
                put("author", "onyx")
                put("body", body)
            },
        )
    }

    /** `GET api/plugins/kanban/assignees`. */
    suspend fun assignees(): List<String> =
        runCatching {
            client.request("$BASE/assignees")
                .asObject()
                ?.array("assignees")
                .orEmpty()
                .mapNotNull { it.asString() ?: it.asObject()?.firstString("name", "profile") }
        }.getOrDefault(emptyList())

    private fun card(obj: kotlinx.serialization.json.JsonObject): KanbanCard = KanbanCard(
        id = obj.firstString("id", "task_id").orEmpty(),
        title = obj.firstString("title", "name").orEmpty(),
        status = KanbanStatus.fromId(obj.firstString("status", "column")).id,
        priority = obj.int("priority") ?: 0,
        // `latest_summary` d'abord: la dernière chose que le worker a dite de la carte, comme
        // `task.latestSummary ?? task.body` sur la rangée.
        summary = obj.strOrNull("latest_summary") ?: obj.strOrNull("summary"),
        body = obj.strOrNull("body"),
        assignee = obj.strOrNull("assignee"),
        createdAt = obj.firstString("created_at", "created"),
        // `created_at` arrive en secondes d'époque (`Date(timeIntervalSince1970:)` ):
        // la rangée en montre l'âge (« 1 hr. ago »), pas le nombre.
        createdAtMillis = obj.long("created_at")?.let { if (it < MILLIS_FLOOR) it * 1000 else it },
        completedAt = obj.firstString("completed_at", "completed"),
        commentCount = obj.int("comment_count") ?: obj.array("comments").size,
        failed = obj["failed"].asBool() ?: (obj.strOrNull("last_failure_error") != null) ||
            (obj.strOrNull("last_failure") != null),
        result = obj.strOrNull("result"),
        lastFailure = obj.strOrNull("last_failure_error") ?: obj.strOrNull("last_failure"),
    )

    private companion object {
        const val BASE = "api/plugins/kanban"

        /** Au-delà, l'estampille est déjà en millisecondes (`1e11` s ≈ l'an 5138). */
        const val MILLIS_FLOOR = 100_000_000_000L
    }
}
