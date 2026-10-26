package com.onyx.app.data.net

import com.onyx.app.data.model.serve.FileEntry
import com.onyx.app.localization.OnyxLocalization
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Les fichiers du serveur, par le Dashboard.
 *
 * Comme: les mêmes routes (`api/files`, `api/fs/read-text`,
 * `api/fs/write-text`), la même lecture souple des entrées, et les noms qui commencent par un
 * point cachés par défaut.
 */
class ServeFilesApi(private val client: ServeClient) {

    /** Le dossier résolu par `GET api/files`, notamment la racine pour un nouveau projet. */
    suspend fun folderPath(path: String?): String {
        val query = if (path.isNullOrEmpty()) emptyMap() else mapOf("path" to path)
        val payload = client.request("api/files", query = query).asObject()
        return payload?.strOrNull("path")
            ?: throw TransportError.Malformed(OnyxLocalization["the folder has no path"])
    }

    /** Le dossier du profil choisi, quand le serveur sait le dire. */
    suspend fun home(profile: String? = null): String? {
        val profiles = runCatching { client.request("api/profiles").asObject() }.getOrNull()
            ?: return null
        val list = profiles.array("profiles").mapNotNull { it.asObject() }
        val chosen = if (profile.isNullOrBlank()) {
            list.firstOrNull { it["is_default"].asBool() == true } ?: list.firstOrNull()
        } else {
            list.firstOrNull { it.firstString("name").equals(profile, ignoreCase = true) }
        }
        return chosen?.firstString("path")?.takeIf { it.isNotEmpty() }
    }

    /** `GET api/files?path=` — les entrées d'un dossier, dossiers d'abord. */
    suspend fun list(path: String?): List<FileEntry> {
        val query = if (path.isNullOrEmpty()) emptyMap() else mapOf("path" to path)
        val payload = client.request("api/files", query = query)
        val entries = payload.asObject()?.array("entries")
            ?: payload.asArray()
            ?: payload.asObject()?.array("files")
        return entries.orEmpty().mapNotNull { element ->
            val obj = element.asObject() ?: return@mapNotNull null
            val name = obj.firstString("name") ?: return@mapNotNull null
            val isDirectory = obj["is_directory"].asBool() ?: obj["isDirectory"].asBool() ?: false
            FileEntry(
                name = name,
                path = obj.firstString("path") ?: buildPath(path, name),
                isDirectory = isDirectory,
                size = obj.long("size") ?: 0L,
                modifiedAt = obj.firstString("modified", "modified_at", "mtime"),
                isText = obj["is_text"].asBool() ?: isTextName(name),
            )
        }.sortedWith(compareBy({ if (it.isDirectory) 0 else 1 }, { it.name.lowercase() }))
    }

    /** Les noms commençant par un point sont cachés, comme. */
    fun visible(entries: List<FileEntry>, showHidden: Boolean): List<FileEntry> =
        if (showHidden) entries else entries.filterNot { it.name.startsWith(".") }

    /** `GET api/fs/read-text?path=` — le texte d'un fichier, et s'il a été tronqué. */
    suspend fun readText(path: String): TextFile {
        val payload = client.request("api/fs/read-text", query = mapOf("path" to path)).asObject()
        return TextFile(
            text = payload?.str("text") ?: "",
            truncated = payload?.get("truncated").asBool() ?: false,
            binary = payload?.get("binary").asBool() ?: false,
        )
    }

    /** Comme , seul un 404 devient `null`; les autres erreurs restent visibles. */
    suspend fun readTextOrNull(path: String): String? = try {
        readText(path).text
    } catch (error: TransportError.Http) {
        if (error.status == 404) null else throw error
    }

    /** `POST api/fs/write-text` avec `{path, content}`. */
    suspend fun writeText(path: String, content: String) {
        client.request(
            path = "api/fs/write-text",
            method = "POST",
            body = buildJsonObject {
                put("path", path)
                put("content", content)
            },
        )
    }

    /** `POST api/files/mkdir`. */
    suspend fun makeFolder(path: String) {
        client.request(
            path = "api/files/mkdir",
            method = "POST",
            body = buildJsonObject { put("path", path) },
        )
    }

    /** `DELETE api/files` avec `{path, recursive}`. */
    suspend fun delete(entry: FileEntry) {
        client.request(
            path = "api/files",
            method = "DELETE",
            body = buildJsonObject {
                put("path", entry.path)
                put("recursive", entry.isDirectory)
            },
        )
    }

    /** `GET api/files/download?path=` — les octets d'un fichier. */
    suspend fun bytes(path: String): ByteArray = client.bytes("api/files/download", mapOf("path" to path))

    /** `POST api/files/upload-stream`, un fichier à la fois, avec l'autorisation d'écraser. */
    suspend fun upload(destinationFolder: String, name: String, bytes: ByteArray, overwrite: Boolean = false) {
        client.multipart(
            path = "api/files/upload-stream",
            fields = mapOf(
                "path" to destinationFolder,
                "overwrite" to overwrite.toString(),
            ),
            fileName = name,
            bytes = bytes,
        )
    }

    /** Le contenu du SOUL.md d'un profil, ou `null` s'il n'existe pas encore. */
    suspend fun soul(profile: String): String? {
        val payload = client.request("api/profiles/$profile/soul").asObject() ?: return null
        val exists = payload["exists"].asBool() ?: true
        return payload.str("content").takeIf { exists }
    }

    /** Écrit le SOUL.md d'un profil. */
    suspend fun setSoul(profile: String, content: String) {
        client.request(
            path = "api/profiles/$profile/soul",
            method = "PUT",
            body = buildJsonObject { put("content", content) },
        )
    }

    private fun buildPath(folder: String?, name: String): String =
        if (folder.isNullOrEmpty()) "/$name" else folder.trimEnd('/') + "/" + name

    private fun isTextName(name: String): Boolean {
        val extension = name.substringAfterLast('.', "").lowercase()
        return extension in TEXT_EXTENSIONS
    }

    data class TextFile(val text: String, val truncated: Boolean, val binary: Boolean)

    private companion object {
        val TEXT_EXTENSIONS = setOf(
            "md", "markdown", "txt", "json", "yaml", "yml", "toml", "ini", "conf", "cfg",
            "sh", "bash", "zsh", "py", "js", "ts", "tsx", "jsx", "kt", "kts", "java", "go",
            "rs", "c", "h", "cpp", "hpp", "swift", "rb", "php", "sql", "csv", "html", "css",
            "xml", "env", "gitignore", "log",
        )
    }
}
