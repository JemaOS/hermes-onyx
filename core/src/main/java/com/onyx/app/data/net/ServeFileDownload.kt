package com.onyx.app.data.net

import com.onyx.app.data.model.ServerConfig
import com.onyx.app.localization.OnyxLocalization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.io.File
import java.io.IOException

/**
 * Le téléchargement d'un fichier du Dashboard, **en flux**.
 *
 * `ServeFilesApi.bytes()` rend les octets d'un coup : parfait pour une vignette, inutilisable pour
 * montrer une **progression** sur un fichier de plusieurs mégaoctets. Ici le corps est lu par
 * morceaux, le poids annoncé par le serveur (`Content-Length`) sert de repère, et l'écriture se fait
 * dans un fichier `.part` renommé **à la fin seulement** : une coupure de réseau ne laisse donc
 * jamais un fichier tronqué qu'on croirait complet.
 *
 * Deux cibles, et c'est la distinction qui compte : un **chemin du serveur** se demande au
 * Dashboard ([toFile]), une **adresse absolue** nommée par une réponse se demande **à elle-même**
 * ([toUrl]) — c'est ce qui fait qu'une adresse est enfin servie.
 *
 * Rien n'est écrit ici dans les fichiers du service : le client est celui du Dashboard, avec son
 * bocal à cookies et sa clé déjà en place ([ServeClient.clientFor]).
 */
class ServeFileDownload(private val config: ServerConfig) {

    /**
     * Télécharge **l'adresse** [remoteUrl] dans [destination] et rend le fichier écrit.
     *
     * L'adresse est demandée telle quelle : elle n'appartient pas au Dashboard, donc aucune session
     * de Dashboard n'est ouverte ici, et son échec se lit comme celui de n'importe quelle requête.
     */
    suspend fun toUrl(
        remoteUrl: String,
        destination: File,
        onProgress: (received: Long, total: Long?) -> Unit = { _, _ -> },
    ): File = withContext(Dispatchers.IO) {
        val url = remoteUrl.toHttpUrlOrNull()
            ?: throw TransportError.BadUrl(OnyxLocalization["empty Dashboard address"])
        val request = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", OnyxHttp.USER_AGENT)
            .build()
        stream(request, destination, onProgress)
    }

    /**
     * Télécharge [remotePath] dans [destination] et rend le fichier écrit.
     *
     * [onProgress] est appelé à chaque morceau lu, avec le nombre d'octets reçus et le poids total
     * quand le serveur l'annonce (`null` s'il ne le dit pas — on ne l'invente jamais).
     */
    suspend fun toFile(
        remotePath: String,
        destination: File,
        onProgress: (received: Long, total: Long?) -> Unit = { _, _ -> },
    ): File = withContext(Dispatchers.IO) {
        // La session d'abord : sans elle, la route répond 401 et l'erreur ne dirait pas pourquoi.
        ServeClient(config).login()

        val base = config.serveBase.toHttpUrlOrNull()
            ?: throw TransportError.BadUrl(OnyxLocalization["empty Dashboard address"])
        val url = base.newBuilder()
            .addPathSegments("api/files/download")
            .addQueryParameter("path", remotePath)
            .build()
        val request = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", OnyxHttp.USER_AGENT)
            .build()

        stream(request, destination, onProgress)
    }

    /** Le corps d'une requête déjà construite, écrit en `.part` puis renommé **à la fin**. */
    private fun stream(
        request: Request,
        destination: File,
        onProgress: (received: Long, total: Long?) -> Unit,
    ): File {
        val parent = destination.parentFile
        if (parent != null && !parent.exists()) parent.mkdirs()
        val partial = File(parent, destination.name + ".part")
        if (partial.exists()) partial.delete()

        try {
            ServeClient.clientFor(config).newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw TransportError.Http(response.code, "")
                val body = response.body ?: throw TransportError.Malformed(OnyxLocalization["empty body"])
                val total = body.contentLength().takeIf { it >= 0L }
                var received = 0L
                body.byteStream().use { input ->
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            received += read
                            onProgress(received, total)
                        }
                    }
                }
                if (total != null && received < total) {
                    // Un corps plus court que ce qui était annoncé est une coupure, pas un fichier.
                    throw TransportError.from(IOException("short body"), afterHeaders = true)
                }
            }
        } catch (error: IOException) {
            partial.delete()
            throw TransportError.from(error, afterHeaders = true)
        }

        if (destination.exists()) destination.delete()
        if (!partial.renameTo(destination)) {
            partial.copyTo(destination, overwrite = true)
            partial.delete()
        }
        return destination
    }
}
