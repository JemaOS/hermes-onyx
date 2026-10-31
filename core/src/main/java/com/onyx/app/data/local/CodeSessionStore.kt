package com.onyx.app.data.local

import com.onyx.app.data.model.CodeSession
import com.onyx.app.data.model.CodeStatus
import com.onyx.app.data.net.asArray
import com.onyx.app.data.net.asObject
import com.onyx.app.data.net.long
import com.onyx.app.data.net.strOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Le peu qu'un magasin clé-valeur doit offrir pour garder les sessions de code : les préférences
 * d'Android, ou une carte en mémoire quand on éprouve sans appareil.
 */
interface CodeSessionStorage {
    fun get(key: String): String?
    fun put(key: String, value: String)
}

/** [CodeSessionStorage] en mémoire : les tests, et les fenêtres sans dépôt. */
class InMemoryCodeSessionStorage(
    private var value: String? = null,
) : CodeSessionStorage {
    override fun get(key: String): String? = value
    override fun put(key: String, value: String) {
        this.value = value
    }
}

/**
 * Les sessions de code gardées **sur ce téléphone**, par connexion.
 *
 * Le serveur n'a pas de ressource « session de code » (voir [com.onyx.app.data.model.CodeSession]) :
 * la fiche — titre, branche dérivée du titre, dépôt et copie de travail, horodatage — vit donc ici
 * et **survit à un redémarrage de l'app**. Le statut gardé est le **dernier fait observé** : une
 * session laissée « en cours » par une fin de processus est reprise telle quelle au lancement
 * suivant, puis réconciliée avec le serveur (voir le ViewModel), jamais promue par défaut.
 *
 * Tout tient sous une seule clé, un objet JSON `{"<connexion>": [ session, … ]}`.
 */
class CodeSessionStore(private val storage: CodeSessionStorage) {

    /** Les sessions gardées pour cette connexion, la plus récente d'abord. */
    fun sessions(server: String): List<CodeSession> =
        all()[server].orEmpty().sortedByDescending { it.createdAtMillis }

    /** Écrit (ou réécrit) une fiche. Même identifiant : la fiche est remplacée. */
    fun save(server: String, session: CodeSession) {
        val kept = all()
        val here = kept[server].orEmpty().filterNot { it.id == session.id }
        kept[server] = here + session
        write(kept)
    }

    fun remove(server: String, id: String) {
        val kept = all()
        val here = kept[server].orEmpty().filterNot { it.id == id }
        if (here.isEmpty()) kept.remove(server) else kept[server] = here
        write(kept)
    }

    /** « Erase everything » : plus aucune session gardée ici. */
    fun clearAll() {
        storage.put(KEY, EMPTY)
    }

    // — Ce qui est écrit sur le disque —

    private fun all(): MutableMap<String, List<CodeSession>> = decode(storage.get(KEY)).toMutableMap()

    private fun write(sessions: Map<String, List<CodeSession>>) {
        val json = buildJsonObject {
            for ((server, list) in sessions) {
                put(server, buildJsonArray { list.forEach { add(encode(it)) } })
            }
        }
        storage.put(KEY, json.toString())
    }

    private fun encode(session: CodeSession): JsonObject = buildJsonObject {
        put("id", session.id)
        put("title", session.title)
        put("branch", session.branch)
        session.repoPath?.let { put("repo", it) }
        session.worktreePath?.let { put("worktree", it) }
        put("created", session.createdAtMillis)
        put("status", session.status.key)
        put("status_at", session.statusAtMillis)
        session.sessionKey?.let { put("session_key", it) }
        session.prompt?.let { put("prompt", it) }
        put("local", session.local)
    }

    private fun decode(text: String?): Map<String, List<CodeSession>> {
        if (text.isNullOrBlank()) return emptyMap()
        val root = runCatching { Json.parseToJsonElement(text).asObject() }.getOrNull() ?: return emptyMap()
        val kept = LinkedHashMap<String, List<CodeSession>>()
        for ((server, value) in root) {
            val decoded = value.asArray().orEmpty().mapNotNull { entry -> entry.asObject()?.let(::decodeOne) }
            if (decoded.isNotEmpty()) kept[server] = decoded
        }
        return kept
    }

    private fun decodeOne(json: JsonObject): CodeSession? {
        val id = json.strOrNull("id") ?: return null
        val created = json.long("created") ?: 0L
        return CodeSession(
            id = id,
            title = json.strOrNull("title") ?: id,
            branch = json.strOrNull("branch") ?: "code/session",
            repoPath = json.strOrNull("repo"),
            worktreePath = json.strOrNull("worktree"),
            createdAtMillis = created,
            status = CodeStatus.fromKey(json.strOrNull("status")) ?: CodeStatus.RUNNING,
            statusAtMillis = json.long("status_at") ?: created,
            sessionKey = json.strOrNull("session_key"),
            // La demande : absente des fiches écrites avant qu'elle ne soit gardée, et c'est
            // exactement ce que `null` veut dire ici — la relance retombe alors sur le titre.
            prompt = json.strOrNull("prompt"),
            local = true,
        )
    }

    private companion object {
        /** La clé unique sous laquelle tout est écrit, et sa valeur quand il n'y a rien. */
        const val KEY = "code_sessions"
        const val EMPTY = "{}"
    }
}
