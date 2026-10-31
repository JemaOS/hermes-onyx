package com.onyx.app.ui.chat

import com.onyx.app.ui.design.OnyxSpace

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import com.onyx.app.ui.design.SelectableText
import com.onyx.app.ui.localization.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.serve.DeliveredFile
import com.onyx.app.data.net.ServeClient
import com.onyx.app.data.net.ServeFileDownload
import com.onyx.app.data.net.ServeFilesApi
import com.onyx.app.data.net.TransportError
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.LiveMark
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.theme.LocalOnyxTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Les fichiers que l'agent a livrés dans sa réponse.
 *
 * C'est ce que `DeliveredFiles` fait : les chemins du serveur qu'une réponse nomme, et, sur
 * un tap, le fichier récupéré par le Dashboard puis ouvert. On ne prend que les chemins absolus
 * avec une extension connue, pour ne pas transformer une phrase en bouton.
 *
 * S'y ajoutent ce que le fichier lui-même a de montrable avant de l'ouvrir : une vignette pour une
 * image (décodée depuis le cache), un aperçu pour un fichier texte, et, quand ça échoue, la raison
 * en mots sous sa pastille (`DeliveredFileChip`).
 */
object DeliveredFiles {

    /**
     * Les extensions qu'une réponse peut nommer et que l'onglet **Fichiers** sait montrer.
     *
     * La liste couvre **toutes** les familles — image, vidéo, son, document, archive — parce que
     * c'est d'elle que dépend l'apparition d'un fichier dans l'onglet : une réponse qui livre un
     * son (`.mp3`) ou une archive (`.tar.gz`) le nomme aussi clairement qu'une image, et l'oublier
     * ici ferait disparaître ces familles de l'onglet et des pastilles du fil.
     */
    private val extensions = listOf(
        // Images.
        "png", "jpg", "jpeg", "gif", "webp", "bmp", "heic", "heif", "tif", "tiff", "avif", "svg",
        // Vidéos.
        "mp4", "m4v", "mov", "webm", "mkv", "avi", "3gp", "3g2", "mpg", "mpeg", "ogv",
        // Sons.
        "mp3", "m4a", "m4b", "aac", "wav", "ogg", "oga", "opus", "flac", "amr", "aiff", "aif", "wma",
        // Documents.
        "pdf", "md", "markdown", "csv", "tsv", "json", "yaml", "yml", "xml", "txt", "text", "log",
        "ini", "toml", "html", "htm", "rtf", "doc", "docx", "odt", "ppt", "pptx", "odp", "xls",
        "xlsx", "ods", "epub",
        // Archives.
        "zip", "jar", "apk", "gz", "tgz", "tar", "bz2", "xz", "zst", "7z", "rar",
        // Ce qui n'a pas de famille connue : l'onglet le montre quand même et le passe au système.
        "bin", "dat",
    )

    private val pattern = Regex(
        // Deux formes, et **une seule prise** sur chacune :
        //
        //  * une **adresse absolue** (`https://hôte/chemin/fichier.json`) est gardée **entière** :
        //    c'est elle qui se demande, telle quelle. Avant, le motif ne partait que du premier
        //    `/` et rendait `//hôte/chemin/fichier.json` — ni l'adresse, ni un chemin du serveur :
        //    la carte nommait bien « me.json », mais pointait un chemin que personne n'a jamais
        //    servi, et la réponse était toujours « il est introuvable à l'emplacement indiqué ».
        //  * un **chemin du serveur** (`/home/onyx/…/fichier.pdf`) reste ce qu'il est.
        //
        // Les extensions les plus longues d'abord (« docx » avant « doc »), et une **frontière**
        // après l'extension : sans elle, « rapport.docx » se coupait en « rapport.doc » — le nom
        // perdait sa fin, et le poids du fichier n'était plus retrouvé auprès du serveur.
        """(?:https?://[^\s"'<>()\[\]]+|/[^\s"'<>()\[\]]+)\.(""" +
            extensions.sortedByDescending { it.length }.joinToString("|") +
            """)(?![A-Za-z0-9])""",
        RegexOption.IGNORE_CASE,
    )

    /** Ce qui se montre d'un fichier sans l'ouvrir. */
    sealed interface Preview {
        /** Rien à montrer : la pastille suffit (PDF, archive, vidéo…). */
        data object None : Preview

        /** En cours de récupération. */
        data object Loading : Preview

        /** Une vignette, pour une image. */
        data class Thumbnail(val bitmap: Bitmap) : Preview

        /** Un aperçu des premières lignes, pour un fichier texte. */
        data class Text(val text: String) : Preview

        /** La raison en mots, quand le fichier ne peut pas être montré. */
        data class Failure(val text: String) : Preview
    }

    private val imageExtensions = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "heic", "heif", "avif", "tif", "tiff", "svg")

    private val textExtensions = setOf(
        "md", "markdown", "txt", "json", "yaml", "yml", "csv", "log", "html", "htm",
        "css", "js", "ts", "kt", "py", "sh", "xml", "toml", "ini",
    )

    /**
     * Les fichiers nommés par les outils d'un message, sans doublon.
     *
     * [limit] borne ce qu'on garde. Le fil garde les quelques premiers (une pastille par fichier,
     * pas une liste) ; l'onglet **Fichiers**, lui, demande la liste entière — c'est son sujet.
     */
    fun of(texts: List<String?>, limit: Int = 6): List<DeliveredFile> =
        texts.filterNotNull()
            .flatMap { pattern.findAll(it).map { match -> match.value } }
            .distinct()
            .take(limit)
            .map { path ->
                DeliveredFile(
                    name = path.substringAfterLast('/'),
                    path = path,
                )
            }

    /** Vrai si le nom a l'extension d'une image (`UTType.conforms(to: .image)`). */
    fun isImage(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in imageExtensions

    /** Vrai si le nom a l'extension d'un fichier qu'on sait lire en texte. */
    fun isText(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in textExtensions

    /**
     * Le symbole d'un fichier selon son type, comme `DeliveredFileChip.symbol` : photo, film,
     * son, PDF, page, archive, sinon document.
     */
    fun symbolFor(name: String): Int = when (name.substringAfterLast('.', "").lowercase()) {
        "png", "jpg", "jpeg", "gif", "webp", "bmp", "heic", "heif", "avif", "tif", "tiff" -> Symbols.Photo
        "svg" -> Symbols.Photo
        "mp4", "mov", "m4v", "avi", "mkv" -> Symbols.Play
        "mp3", "wav", "m4a", "aac", "ogg", "flac" -> Symbols.SpeakerWave
        "pdf" -> Symbols.DocOnDoc
        "html", "htm" -> Symbols.Globe
        "zip", "gz", "tar", "tgz", "7z" -> Symbols.Doc
        else -> Symbols.Doc
    }

    /**
     * Télécharge un fichier livré et le confie au système, comme le fait `DeliveredFileChip` :
     * les fichiers de l'agent passent par le Dashboard.
     */
    suspend fun open(context: Context, config: ServerConfig, file: DeliveredFile): Result<Unit> =
        runCatching {
            val local = cached(context, config, file)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", local)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, FileViewers.mimeFor(file.name))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, file.name))
        }

    /**
     * Le fichier livré, récupéré dans le cache du téléphone : ce qu'un lecteur de l'app — le
     * lecteur de PDF, le visualiseur d'images — a besoin pour le montrer lui-même.
     */
    suspend fun local(context: Context, config: ServerConfig, file: DeliveredFile): Result<File> =
        runCatching { cached(context, config, file) }

    /**
     * Ce qu'on peut montrer d'un fichier sans l'ouvrir : sa vignette s'il est image, ses premières
     * lignes s'il est texte, sinon rien — et la raison en mots quand la récupération a échoué.
     */
    suspend fun preview(context: Context, config: ServerConfig, file: DeliveredFile): Preview =
        try {
            val local = cached(context, config, file)
            when {
                isImage(file.name) ->
                    decodeThumbnail(local)?.let { Preview.Thumbnail(it) }
                        ?: Preview.Failure(OnyxLocalization["This image can't be read."])

                isText(file.name) -> Preview.Text(readPreview(local))

                else -> Preview.None
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Preview.Failure(failureText(error, file))
        }

    /**
     * Vrai pour une pièce jointe de **cet appareil** : ce n'est pas un chemin du serveur. Un
     * `content://` (galerie, sélecteur, partage) ou un `file://` ne se demande jamais au Dashboard :
     * il s'ouvre sur place. Le serveur, lui, répond `400 Path must be absolute` à un tel chemin.
     *
     * Une **adresse absolue** (`http://`, `https://`) n'est ni l'un ni l'autre : elle se demande
     * **à elle-même**, et la dire « locale » ferait ouvrir un `content://` qui n'existe pas.
     */
    fun isLocalPath(path: String): Boolean =
        !isRemoteUrl(path) &&
            (
                path.startsWith("content://") || path.startsWith("file://") ||
                    path.startsWith("android.resource://") ||
                    (!path.startsWith("/") && !path.startsWith("~"))
                )

    /**
     * Vrai pour une **adresse absolue** nommée par une réponse : `http://…` ou `https://…`.
     *
     * C'est la cible « distante » de l'onglet Fichiers : elle n'a pas à passer par le Dashboard,
     * et la demander à `api/files/download` ne pouvait **jamais** aboutir — l'adresse n'était
     * alors jamais servie.
     */
    fun isRemoteUrl(path: String): Boolean =
        path.startsWith("http://", ignoreCase = true) || path.startsWith("https://", ignoreCase = true)

    /** Pourquoi un fichier nommé par une réponse ne s'ouvre pas, en mots (`Problem` de la référence). */
    fun failureText(error: Throwable, file: DeliveredFile? = null): String = when {
        error is TransportError.MissingApiKey || error is TransportError.SignInRequired ||
            error is TransportError.Http && error.status == 401 ->
            OnyxLocalization["Files your agent sends come through the Hermes Dashboard. Add its login in Settings → Connection details."]

        // Un code nu (« http_400 ») ne dit rien à personne : chaque refus dit le geste à faire.
        error is TransportError.Http && error.status == 400 ->
            OnyxLocalization["The server refused this request (400). It may be too heavy, or in a shape it doesn't take."]

        error is TransportError.Http && error.status == 413 ->
            OnyxLocalization["The server says that file is too heavy (413). Choose a smaller one."]

        error is TransportError.Http && error.status == 409 ->
            OnyxLocalization["The server has no room for a file of that name (409). Try again."]

        error is TransportError.Http && error.status == 403 ->
            OnyxLocalization["The server refused this file (403). It isn't allowed to write there."]

        error is TransportError.Http && error.status == 404 -> {
            val relative = file != null && !file.path.startsWith("/") && !file.path.startsWith("~")
            when {
                // Une **adresse** nommée par la réponse : c'est elle qui devait répondre, et elle
                // n'a rien rendu. Le dire ainsi, plutôt que « pas sur le serveur », qui envoie
                // chercher au mauvais endroit un fichier qui n'y a jamais été.
                file != null && isRemoteUrl(file.path) ->
                    OnyxLocalization["The address the reply named answers nothing (404): the file is gone from there, or it was never served."]

                relative -> OnyxLocalization["It isn't on the server where the reply says, or the chat isn't on the server yet."]
                else -> OnyxLocalization["It isn't on the server where the reply says."]
            }
        }

        // Serveur jamais joint : rien n'a été écrit, un nouvel essai a du sens — et `userMessage()`
        // le dit déjà en mots, dans la langue de l'app.
        error is TransportError -> error.userMessage()

        else -> OnyxLocalization["Couldn't fetch it:"] + " " + (error.message ?: OnyxLocalization["unknown error"])
    }

    /** Vrai quand rejouer la même demande a une chance d'aboutir (le bouton « Réessayer »). */
    fun isRetryable(error: Throwable): Boolean =
        error is TransportError &&
            (error.isConnectivity || error is TransportError.StreamLost || error.isTransient)

    /**
     * Vrai quand ce que le serveur a rendu est un **accusé** (`{"ok": true}`, douze octets) et non
     * le fichier.
     *
     * La route peut répondre 200 avec un reçu : la ligne de l'onglet Fichiers annonçait alors
     * « Disponible, 12 o » et le lecteur s'ouvrait tout noir. Un fichier texte ou JSON peut
     * légitimement commencer par une accolade : la vérification ne s'applique donc qu'aux
     * familles binaires, et seulement sous quelques centaines d'octets.
     */
    fun looksLikeAck(local: File, name: String): Boolean = runCatching {
        val length = local.length()
        if (length <= 0L || length > 512L) return false
        val extension = name.substringAfterLast('.', "").lowercase()
        if (extension in setOf("json", "txt", "md", "markdown", "csv", "yaml", "yml", "xml", "html", "htm")) {
            return false
        }
        val head = ByteArray(minOf(length, 96L).toInt())
        var read = 0
        local.inputStream().use { input ->
            while (read < head.size) {
                val step = input.read(head, read, head.size - read)
                if (step <= 0) break
                read += step
            }
        }
        if (read <= 0) return false
        val text = head.decodeToString(0, read).trimStart('\uFEFF').trim()
        text.startsWith("{") && text.contains("\"ok\"")
    }.getOrDefault(false)

    /** Pourquoi une pièce jointe de l'appareil ne s'ouvre pas : elle se lit sur place, pas au serveur. */
    fun localProblemText(error: Throwable): String =
        OnyxLocalization["The file is still on this device, not on the server: it opens from here."] +
            " " + (error as? TransportError)?.userMessage().orEmpty()

    /**
     * Ce qui a **changé**, pour une cible que personne ne sert plus.
     *
     * C'est la phrase de l'écran qui ne ment pas : le nom et la date sont déjà sur la carte, et
     * celle-ci dit le reste — le fichier a été retiré, ou il a été écrit dans un dossier de travail
     * qui a été nettoyé. Aucun geste n'est proposé avec, parce qu'aucun geste ne peut le rapporter.
     */
    fun goneText(file: DeliveredFile): String =
        OnyxLocalization.format(
            "No one serves this file at %1\$s any more: it was removed, or it was written in a working folder that has since been cleaned up.",
            file.path,
        )

    /** La raison d'un échec, en mots, et si un nouvel essai vaut le coup. */
    fun problem(error: Throwable, file: DeliveredFile? = null): Problem =
        Problem(text = failureText(error, file), retryable = isRetryable(error))

    /** Un échec de fichier livré : ce qu'on lit, et s'il faut proposer « Réessayer ». */
    data class Problem(val text: String, val retryable: Boolean)

    /** Le fichier livré, dans les fichiers de l'app, téléchargé une seule fois. */
    private suspend fun cached(context: Context, config: ServerConfig, file: DeliveredFile): File =
        withContext(Dispatchers.IO) {
            // **`filesDir`, pas `cacheDir`** : le cache se purge, et un fichier revu une fois ne
            // l'était plus après. Les fichiers de l'agent passent par le Dashboard ; une **adresse**
            // nommée par la réponse se demande à elle-même. Sans ses identifiants, le Dashboard ne
            // rend rien, et `failureText` dit quoi faire.
            val target = File(context.filesDir, "delivered").apply { mkdirs() }
            val local = File(target, file.name)
            if (local.exists() && local.length() > 0L) return@withContext local
            if (isRemoteUrl(file.path)) {
                ServeFileDownload(config).toUrl(file.path, local)
            } else {
                local.writeBytes(ServeFilesApi(ServeClient(config)).bytes(file.path))
            }
            local
        }

    /**
     * Décode une vignette bornée, jamais l'image entière : un livrable peut être énorme.
     *
     * Le calcul est celui de [BoundedImages] — un seul endroit décide de la réduction, et il
     * revérifie le `Bitmap` obtenu. `null` (fichier absent, coupé, illisible) devient une carte
     * d'échec nommée chez l'appelant, jamais une exception.
     */
    private suspend fun decodeThumbnail(local: File, maxPx: Int = BoundedImages.PREVIEW_PX): Bitmap? =
        // Hors du fil principal : un TIFF (le seul format que le système ne décode pas) se lit en
        // entier avant d'être réduit, et personne ne doit attendre ça à l'image.
        withContext(Dispatchers.IO) { BoundedImages.decodeFile(local, maxPx) }

    /** Les premières lignes d'un fichier texte, comme l'aperçu d'un `MarkdownDocument`. */
    private fun readPreview(local: File, maxLines: Int = 12): String =
        local.useLines { lines -> lines.take(maxLines).joinToString("\n") }

    /**
     * Charge, hors recomposition, ce qu'on peut montrer d'un fichier livré. Un fichier ni image ni
     * texte reste à l'état [Preview.None] : sa pastille suffit.
     */
    @Composable
    fun rememberPreview(
        context: Context,
        config: ServerConfig,
        file: DeliveredFile,
        enabled: Boolean = true,
    ): Preview {
        var state by remember(file.path, config.id) { mutableStateOf<Preview>(Preview.None) }
        LaunchedEffect(file.path, config.id, enabled) {
            if (!enabled) return@LaunchedEffect
            if (!isImage(file.name) && !isText(file.name)) return@LaunchedEffect
            state = Preview.Loading
            state = preview(context, config, file)
        }
        return state
    }
}

/**
 * La pastille d'un fichier livré (`DeliveredFileChip`) : son symbole — ou sa vignette, ou sa
 * raison d'échec — puis son nom ; au tap, il est récupéré et ouvert. Un aperçu texte s'affiche en
 * dessous, et l'échec, en mots, sous la pastille.
 */
@Composable
fun DeliveredFileChip(
    file: DeliveredFile,
    preview: DeliveredFiles.Preview,
    fetching: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier
                .background(theme.fill, RoundedCornerShape(50))
                .onyxClickable(enabled = !fetching, pressedScale = 0.95f, onClick = onOpen)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            when {
                fetching -> LiveMark(color = theme.secondaryText)
                preview is DeliveredFiles.Preview.Failure ->
                    OnyxIcon(Symbols.ExclamationTriangle, tint = theme.warning, size = 13.dp)

                preview is DeliveredFiles.Preview.Thumbnail -> Image(
                    bitmap = preview.bitmap.asImageBitmap(),
                    contentDescription = file.name,
                    modifier = Modifier.size(18.dp).clip(RoundedCornerShape(OnyxSpace.ControlCorner)),
                )

                else -> OnyxIcon(symbol = DeliveredFiles.symbolFor(file.name), tint = theme.textColor, size = 13.dp)
            }
            BasicText(
                text = file.name,
                localize = false,
                style = theme.caption.copy(color = theme.textColor),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // L'aperçu texte : les premières lignes, comme le rend `MarkdownDocument`. Sélectionnable
        // comme le reste du fil : c'est du texte à lire, donc à reprendre.
        if (preview is DeliveredFiles.Preview.Text && preview.text.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(theme.surfaceColor, RoundedCornerShape(OnyxSpace.ControlCorner))
                    .padding(10.dp),
            ) {
                SelectableText(
                    text = preview.text,
                    localize = false,
                    style = theme.caption.copy(color = theme.secondaryText, fontFamily = FontFamily.Monospace),
                    maxLines = 12,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // L'état d'échec : pourquoi il ne s'ouvre pas, sous la pastille.
        if (preview is DeliveredFiles.Preview.Failure) {
            SelectableText(text = preview.text, localize = false, style = theme.caption.copy(color = theme.secondaryText))
        }
    }
}
