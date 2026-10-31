package com.onyx.app.data.link

import com.onyx.app.data.net.OnyxHttp
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Lit une page et en tire de quoi dessiner sa fiche.
 *
 * C'est le `LinkPreviews.fromSite` de la référence (LinkPreviews:94) : le **téléphone** ouvre la page
 * lui-même — d'où l'interrupteur éteint par défaut, le site voyant la visite. référence s'appuie sur
 * `LPMetadataProvider` ; Android n'en a pas, donc ce port fait lui-même la requête et lit les
 * balises Open Graph ([LinkPreviewMeta]).
 *
 * Les limites reprennent celles de la référence ou, à défaut, en posent une : délai de 10 s (le
 * `provider.timeout` de la référence), image ramenée à [MAX_IMAGE_SIDE] points ; une page ou une image plus
 * grosse que les plafonds n'est pas lue (référence s'en remet à LinkPresentation pour ça).
 */
object LinkPreviewFetcher {

    /** Le délai de la référence : `provider.timeout = 10`. */
    const val TIMEOUT_SECONDS = 10L

    /** Le plus grand côté de la vignette, comme les 240 points de la référence. */
    const val MAX_IMAGE_SIDE = 240

    /** Une page plus grosse que 1 Mio n'est pas lue. */
    const val MAX_HTML_BYTES = 1 shl 20

    /** Une image plus grosse que 4 Mio n'est pas téléchargée. */
    const val MAX_IMAGE_BYTES = 4 shl 20

    /** Comment une page est lue ; les tests et la démo en fournissent une autre. */
    @Volatile
    var client: OkHttpClient = defaultClient(TIMEOUT_SECONDS)

    /** Comment l'image est ramenée à 240 px ; les tests en fournissent une sans `android.graphics`. */
    @Volatile
    var compress: (ByteArray) -> ByteArray? = { AndroidImageThumbnail.jpeg(it, MAX_IMAGE_SIDE) }

    /** Le délai d'une lecture, en secondes ; réglable dans les tests. */
    fun defaultClient(timeoutSeconds: Long): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .callTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /** La fiche de [url], ou `null` si la page n'a pas pu être lue. */
    suspend fun fromSite(url: String): LinkPreview? {
        val page = fetchPage(url) ?: return null
        val meta = LinkPreviewMeta.parse(page.html)
        val image = meta.imageUrl
            ?.let { resolve(it, page.finalUrl) }
            ?.let { fetchThumbnail(it) }
        // Le site vient de l'adresse **finale** : une redirection suit vraiment, comme le
        // `metadata.url` de LinkPresentation.
        val host = LinkPreviewLinks.host(page.finalUrl) ?: LinkPreviewLinks.host(url) ?: return null
        return LinkPreview(
            url = url,
            title = meta.title,
            site = LinkPreviews.site(host),
            image = image,
        )
    }

    private data class Page(val finalUrl: String, val html: String)

    private suspend fun fetchPage(url: String): Page? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", OnyxHttp.USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val type = response.header("Content-Type").orEmpty()
                if (type.isNotEmpty() && !type.contains("html", ignoreCase = true)) return@use null
                val body = response.body ?: return@use null
                val source = body.source()
                source.request(MAX_HTML_BYTES + 1L)
                if (source.buffer.size > MAX_HTML_BYTES) return@use null
                Page(finalUrl = response.request.url.toString(), html = source.readUtf8())
            }
        }.getOrNull()
    }

    private suspend fun fetchThumbnail(imageUrl: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(imageUrl)
                .header("User-Agent", OnyxHttp.USER_AGENT)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body ?: return@use null
                val source = body.source()
                source.request(MAX_IMAGE_BYTES + 1L)
                if (source.buffer.size > MAX_IMAGE_BYTES) return@use null
                val thumb = compress(source.readByteArray()) ?: return@use null
                java.util.Base64.getEncoder().encodeToString(thumb)
            }
        }.getOrNull()
    }

    /** Une image relative (« /a.png ») se résout contre l'adresse de la page. */
    private fun resolve(imageUrl: String, pageUrl: String): String? {
        if (HttpUrlRegex.hasScheme(imageUrl)) return imageUrl
        return pageUrl.toHttpUrlOrNull()?.resolve(imageUrl)?.toString()
    }

    private object HttpUrlRegex {
        private val scheme = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")
        fun hasScheme(url: String): Boolean = scheme.containsMatchIn(url)
    }
}
