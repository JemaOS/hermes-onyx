package com.onyx.app.data.link

import android.content.Context
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Les pages des fiches, lues par le téléphone lui-même, une fois chacune : gardées en mémoire et
 * dans le cache par adresse.
 *
 * Port de `LinkPreviews` (LinkPreviews:23). Éteint jusqu'à ce qu'on l'allume : le site voit la
 * visite, là où Onyx ne parle sinon qu'aux serveurs qu'on lui a donnés. Une page qu'on n'a pas pu
 * lire n'est pas redemandée pendant un moment. Les requêtes en cours sont dédoublonnées (une seule
 * lecture par adresse), et l'horodatage de l'échec suit le `failedAt` de la référence.
 */
class LinkPreviews(
    /** Le dossier du cache ; `null` = rien sur disque (la démo, et les tests qui le veulent). */
    internal val folder: File?,
    /** Comment une page est lue. Les tests et la démo en fournissent une autre. */
    private var fetch: suspend (String) -> LinkPreview? = { LinkPreviewFetcher.fromSite(it) },
) {

    /** Ce qui a été lu, au fur et à mesure : les fiches se dessinent à partir de ça. */
    private val known = mutableMapOf<String, LinkPreview>()
    private val failedAt = mutableMapOf<String, Long>()
    private val fetching = mutableSetOf<String>()
    private val mutex = Mutex()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    /** La fiche de [url] si elle est connue, en mémoire ou sur disque ; `null` sinon. */
    fun preview(url: String): LinkPreview? = known[url]

    /** Remplace la lecture d'une page (les tests s'en servent). */
    fun fetchWith(reader: suspend (String) -> LinkPreview?) {
        fetch = reader
    }

    /**
     * Lit la page [url] sauf si elle est connue, déjà en cours, ou en échec récemment. Une fiche
     * gardée sur disque revient aussitôt.
     */
    suspend fun request(url: String, now: Long = System.currentTimeMillis()) {
        mutex.withLock {
            if (known.containsKey(url)) return
            if (fetching.contains(url)) return
            fromDisk(url)?.let { known[url] = it; return }
            failedAt[url]?.let { failed -> if (now - failed < RETRY_AFTER_MS) return }
            fetching.add(url)
        }
        val preview = try {
            fetch(url)
        } catch (cancelled: CancellationException) {
            mutex.withLock { fetching.remove(url) }
            throw cancelled
        } catch (failure: Throwable) {
            null
        }
        mutex.withLock {
            fetching.remove(url)
            if (preview == null) failedAt[url] = now else known[url] = preview
        }
        if (preview != null) toDisk(preview)
    }

    /** Fait partie de « Erase everything ». */
    suspend fun clear() {
        mutex.withLock { known.clear(); failedAt.clear() }
        withContext(Dispatchers.IO) { runCatching { folder?.deleteRecursively() } }
    }

    // MARK: Gardé sur disque

    private fun fileFor(url: String): File? {
        val folder = folder ?: return null
        // FNV-1a 64 bits, comme `LinkPreviews.file(for:)` : la même adresse donne le même nom.
        var hash = 0xcbf2_9ce4_8422_2325uL
        for (byte in url.toByteArray(Charsets.UTF_8)) {
            hash = hash xor byte.toUByte().toULong()
            hash *= 0x0000_0100_0000_01b3uL
        }
        return File(folder, hash.toString(36) + ".json")
    }

    private suspend fun fromDisk(url: String): LinkPreview? = withContext(Dispatchers.IO) {
        val file = fileFor(url) ?: return@withContext null
        runCatching {
            val preview = json.decodeFromString(LinkPreview.serializer(), file.readText())
            preview.takeIf { it.url == url }
        }.getOrNull()
    }

    private suspend fun toDisk(preview: LinkPreview) = withContext(Dispatchers.IO) {
        val file = fileFor(preview.url) ?: return@withContext
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(LinkPreview.serializer(), preview))
        }
    }

    companion object {
        /** Le temps pendant lequel une page illisible est laissée tranquille : 15 minutes. */
        const val RETRY_AFTER_MS = 15L * 60 * 1000

        /** La même instance pour toute l'app, comme `LinkPreviews.shared` de la référence. */
        @Volatile
        private var instance: LinkPreviews = LinkPreviews(folder = null)

        val shared: LinkPreviews get() = instance

        /** À appeler une fois, depuis `Application.onCreate` (ou le conteneur). */
        fun install(context: Context) {
            val folder = File(context.applicationContext.cacheDir, "link-previews")
            if (instance.folder == null) instance = LinkPreviews(folder = folder)
        }

        /** « www.example.com » se lit « example.com ». */
        fun site(host: String): String = if (host.startsWith("www.")) host.drop(4) else host
    }
}
