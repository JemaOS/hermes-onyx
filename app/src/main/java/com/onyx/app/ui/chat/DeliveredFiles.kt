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
 * The files the agent delivered in its reply.
 *
 * That is what `DeliveredFiles` does: the server paths a reply names, and, on
 * a tap, the file fetched through the Dashboard then opened. We only take absolute paths
 * with a known extension, so as not to turn a sentence into a button.
 *
 * Added to that is what the file itself can show before being opened: a thumbnail for an
 * image (decoded from the cache), a preview for a text file, and, when it fails, the reason
 * in words under its chip (`DeliveredFileChip`).
 */
object DeliveredFiles {

    /**
     * The extensions a reply can name and that the **Files** tab knows how to show.
     *
     * The list covers **all** families — image, video, sound, document, archive — because
     * it is on it that a file's appearance in the tab depends: a reply that delivers a
     * sound (`.mp3`) or an archive (`.tar.gz`) names it as clearly as an image, and forgetting it
     * here would make those families disappear from the tab and from the thread's chips.
     */
    private val extensions = listOf(
        // Images.
        "png", "jpg", "jpeg", "gif", "webp", "bmp", "heic", "heif", "tif", "tiff", "avif", "svg",
        // Videos.
        "mp4", "m4v", "mov", "webm", "mkv", "avi", "3gp", "3g2", "mpg", "mpeg", "ogv",
        // Sounds.
        "mp3", "m4a", "m4b", "aac", "wav", "ogg", "oga", "opus", "flac", "amr", "aiff", "aif", "wma",
        // Documents.
        "pdf", "md", "markdown", "csv", "tsv", "json", "yaml", "yml", "xml", "txt", "text", "log",
        "ini", "toml", "html", "htm", "rtf", "doc", "docx", "odt", "ppt", "pptx", "odp", "xls",
        "xlsx", "ods", "epub",
        // Archives.
        "zip", "jar", "apk", "gz", "tgz", "tar", "bz2", "xz", "zst", "7z", "rar",
        // What has no known family: the tab still shows it and hands it to the system.
        "bin", "dat",
    )

    private val pattern = Regex(
        // Two shapes, and **a single capture** on each:
        //
        //  * an **absolute address** (`https://hôte/chemin/fichier.json` / `https://host/path/file.json`) is kept **whole**:
        //    it is what gets requested, as is. Before, the pattern only started from the first
        //    `/` and returned `//hôte/chemin/fichier.json` (`//host/path/file.json`) — neither the address nor a server path:
        //    the card did name "me.json", but pointed at a path that no one has ever
        //    served, and the answer was always "it cannot be found at the indicated location".
        //  * a **server path** (`/home/onyx/…/fichier.pdf` / `…/file.pdf`) stays what it is.
        //
        // Longest extensions first ("docx" before "doc"), and a **boundary**
        // after the extension: without it, "rapport.docx" ("report.docx") was cut into "rapport.doc" — the name
        // lost its ending, and the file's weight was no longer found on the server.
        """(?:https?://[^\s"'<>()\[\]]+|/[^\s"'<>()\[\]]+)\.(""" +
            extensions.sortedByDescending { it.length }.joinToString("|") +
            """)(?![A-Za-z0-9])""",
        RegexOption.IGNORE_CASE,
    )

    /** What shows of a file without opening it. */
    sealed interface Preview {
        /** Nothing to show: the chip is enough (PDF, archive, video…). */
        data object None : Preview

        /** Being fetched. */
        data object Loading : Preview

        /** A thumbnail, for an image. */
        data class Thumbnail(val bitmap: Bitmap) : Preview

        /** A preview of the first lines, for a text file. */
        data class Text(val text: String) : Preview

        /** The reason in words, when the file cannot be shown. */
        data class Failure(val text: String) : Preview
    }

    private val imageExtensions = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "heic", "heif", "avif", "tif", "tiff", "svg")

    private val textExtensions = setOf(
        "md", "markdown", "txt", "json", "yaml", "yml", "csv", "log", "html", "htm",
        "css", "js", "ts", "kt", "py", "sh", "xml", "toml", "ini",
    )

    /**
     * The files named by a message's tools, without duplicates.
     *
     * [limit] bounds what is kept. The thread keeps the first few (one chip per file,
     * not a list); the **Files** tab, however, asks for the whole list — that is its subject.
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

    /** True if the name has an image extension (`UTType.conforms(to: .image)`). */
    fun isImage(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in imageExtensions

    /** True if the name has the extension of a file that can be read as text. */
    fun isText(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in textExtensions

    /**
     * A file's symbol according to its type, like `DeliveredFileChip.symbol`: photo, film,
     * sound, PDF, page, archive, otherwise document.
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
     * Downloads a delivered file and hands it to the system, as `DeliveredFileChip` does:
     * the agent's files go through the Dashboard.
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
     * The delivered file, fetched into the phone's cache: what an app reader — the
     * PDF reader, the image viewer — needs to show it itself.
     */
    suspend fun local(context: Context, config: ServerConfig, file: DeliveredFile): Result<File> =
        runCatching { cached(context, config, file) }

    /**
     * What can be shown of a file without opening it: its thumbnail if it is an image, its first
     * lines if it is text, otherwise nothing — and the reason in words when the fetch failed.
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
     * True for an attachment from **this device**: it is not a server path. A
     * `content://` (gallery, picker, share) or a `file://` is never requested from the Dashboard:
     * it opens on the spot. The server, for its part, answers `400 Path must be absolute` to such a path.
     *
     * An **absolute address** (`http://`, `https://`) is neither of the two: it is requested
     * **from itself**, and calling it "local" would open a `content://` that does not exist.
     */
    fun isLocalPath(path: String): Boolean =
        !isRemoteUrl(path) &&
            (
                path.startsWith("content://") || path.startsWith("file://") ||
                    path.startsWith("android.resource://") ||
                    (!path.startsWith("/") && !path.startsWith("~"))
                )

    /**
     * True for an **absolute address** named by a reply: `http://…` or `https://…`.
     *
     * It is the Files tab's "remote" target: it does not have to go through the Dashboard,
     * and requesting it from `api/files/download` could **never** succeed — the address was
     * then never served.
     */
    fun isRemoteUrl(path: String): Boolean =
        path.startsWith("http://", ignoreCase = true) || path.startsWith("https://", ignoreCase = true)

    /** Why a file named by a reply does not open, in words (the reference's `Problem`). */
    fun failureText(error: Throwable, file: DeliveredFile? = null): String = when {
        error is TransportError.MissingApiKey || error is TransportError.SignInRequired ||
            error is TransportError.Http && error.status == 401 ->
            OnyxLocalization["Files your agent sends come through the Hermes Dashboard. Add its login in Settings → Connection details."]

        // A bare code ("http_400") tells no one anything: every refusal says the gesture to make.
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
                // An **address** named by the reply: it is what should have answered, and it
                // returned nothing. Say it that way, rather than "not on the server", which sends
                // you looking in the wrong place for a file that was never there.
                file != null && isRemoteUrl(file.path) ->
                    OnyxLocalization["The address the reply named answers nothing (404): the file is gone from there, or it was never served."]

                relative -> OnyxLocalization["It isn't on the server where the reply says, or the chat isn't on the server yet."]
                else -> OnyxLocalization["It isn't on the server where the reply says."]
            }
        }

        // Server never reached: nothing was written, a new attempt makes sense — and `userMessage()`
        // already says it in words, in the app's language.
        error is TransportError -> error.userMessage()

        else -> OnyxLocalization["Couldn't fetch it:"] + " " +
            com.onyx.app.data.net.humanFailure(error, OnyxLocalization["unknown error"])
    }

    /** True when replaying the same request has a chance to succeed (the "Réessayer" button, i.e. "Retry"). */
    fun isRetryable(error: Throwable): Boolean =
        error is TransportError &&
            (error.isConnectivity || error is TransportError.StreamLost || error.isTransient)

    /**
     * True when what the server returned is an **acknowledgement** (`{"ok": true}`, twelve bytes) and not
     * the file.
     *
     * The route may answer 200 with a receipt: the Files tab's row then announced
     * "Available, 12 B" and the reader opened entirely black. A text or JSON file may
     * legitimately start with a brace: the check therefore only applies to
     * binary families, and only under a few hundred bytes.
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

    /** Why a device attachment does not open: it is read on the spot, not from the server. */
    fun localProblemText(error: Throwable): String =
        OnyxLocalization["The file is still on this device, not on the server: it opens from here."] +
            " " + (error as? TransportError)?.userMessage().orEmpty()

    /**
     * What **changed**, for a target that no one serves any more.
     *
     * It is the screen's sentence that does not lie: the name and date are already on the card, and
     * it says the rest — the file was removed, or it was written in a working folder
     * that has been cleaned up. No gesture is offered with it, because no gesture can bring it back.
     */
    fun goneText(file: DeliveredFile): String =
        OnyxLocalization.format(
            "No one serves this file at %1\$s any more: it was removed, or it was written in a working folder that has since been cleaned up.",
            file.path,
        )

    /** The reason for a failure, in words, and whether a new attempt is worth it. */
    fun problem(error: Throwable, file: DeliveredFile? = null): Problem =
        Problem(text = failureText(error, file), retryable = isRetryable(error))

    /** A delivered-file failure: what is read, and whether to offer "Réessayer" ("Retry"). */
    data class Problem(val text: String, val retryable: Boolean)

    /** The delivered file, in the app's files, downloaded only once. */
    private suspend fun cached(context: Context, config: ServerConfig, file: DeliveredFile): File =
        withContext(Dispatchers.IO) {
            // **`filesDir`, not `cacheDir`**: the cache is purged, and a file seen once was no longer
            // seen after. The agent's files go through the Dashboard; an **address**
            // named by the reply is requested from itself. Without its credentials, the Dashboard returns
            // nothing, and `failureText` says what to do.
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
     * Decodes a bounded thumbnail, never the whole image: a deliverable can be huge.
     *
     * The computation is that of [BoundedImages] — a single place decides the shrink, and it
     * re-checks the resulting `Bitmap`. `null` (missing, truncated, unreadable file) becomes a named
     * failure card at the caller, never an exception.
     */
    private suspend fun decodeThumbnail(local: File, maxPx: Int = BoundedImages.PREVIEW_PX): Bitmap? =
        // Off the main thread: a TIFF (the only format the system does not decode) is read in
        // full before being shrunk, and no one should wait for that on the image.
        withContext(Dispatchers.IO) { BoundedImages.decodeFile(local, maxPx) }

    /** A text file's first lines, like a `MarkdownDocument` preview. */
    private fun readPreview(local: File, maxLines: Int = 12): String =
        local.useLines { lines -> lines.take(maxLines).joinToString("\n") }

    /**
     * Loads, outside recomposition, what can be shown of a delivered file. A file that is neither image nor
     * text stays in the [Preview.None] state: its chip is enough.
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
 * A delivered file's chip (`DeliveredFileChip`): its symbol — or its thumbnail, or its
 * failure reason — then its name; on tap, it is fetched and opened. A text preview is shown
 * below, and the failure, in words, under the chip.
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

        // The text preview: the first lines, as `MarkdownDocument` renders them. Selectable
        // like the rest of the thread: it is text to read, therefore to reuse.
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

        // The failure state: why it does not open, under the chip.
        if (preview is DeliveredFiles.Preview.Failure) {
            SelectableText(text = preview.text, localize = false, style = theme.caption.copy(color = theme.secondaryText))
        }
    }
}
