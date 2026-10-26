package com.onyx.app.ui.chat

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
 * C'est ce que fait: les chemins du serveur qu'une réponse nomme, et, sur
 * un tap, le fichier récupéré par le Dashboard puis ouvert. On ne prend que les chemins absolus
 * avec une extension connue, pour ne pas transformer une phrase en bouton.
 *
 * S'y ajoutent ce que le fichier lui-même a de montrable avant de l'ouvrir: une vignette pour une
 * image (décodée depuis le cache), un aperçu pour un fichier texte, et, quand ça échoue, la raison
 * en mots sous sa pastille (`DeliveredFileChip`).
 */
object DeliveredFiles {

    private val pattern = Regex(
        """/[^\s"'<>()\[\]]+\.(png|jpg|jpeg|gif|webp|pdf|md|csv|json|txt|zip|html|svg|mp4|mov)""",
        RegexOption.IGNORE_CASE,
    )

    /** Ce qui se montre d'un fichier sans l'ouvrir. */
    sealed interface Preview {
        /** Rien à montrer: la pastille suffit (PDF, archive, vidéo…). */
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

    private val imageExtensions = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "heic", "svg")

    private val textExtensions = setOf(
        "md", "markdown", "txt", "json", "yaml", "yml", "csv", "log", "html", "htm",
        "css", "js", "ts", "kt", "py", "sh", "xml", "toml", "ini",
    )

    /** Les fichiers nommés par les outils d'un message, sans doublon. */
    fun of(texts: List<String?>): List<DeliveredFile> =
        texts.filterNotNull()
            .flatMap { pattern.findAll(it).map { match -> match.value } }
            .distinct()
            .take(6)
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
     * Le symbole d'un fichier selon son type, comme `DeliveredFileChip.symbol`: photo, film,
     * son, PDF, page, archive, sinon document.
     */
    fun symbolFor(name: String): Int = when (name.substringAfterLast('.', "").lowercase()) {
        "png", "jpg", "jpeg", "gif", "webp", "bmp", "heic" -> Symbols.Photo
        "svg" -> Symbols.Photo
        "mp4", "mov", "m4v", "avi", "mkv" -> Symbols.Play
        "mp3", "wav", "m4a", "aac", "ogg", "flac" -> Symbols.SpeakerWave
        "pdf" -> Symbols.DocOnDoc
        "html", "htm" -> Symbols.Globe
        "zip", "gz", "tar", "tgz", "7z" -> Symbols.Doc
        else -> Symbols.Doc
    }

    /**
     * Télécharge un fichier livré et le confie au système, comme le fait `DeliveredFileChip`:
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
     * Le fichier livré, récupéré dans le cache du téléphone: ce qu'un lecteur de l'app — le
     * lecteur de PDF, le visualiseur d'images — a besoin pour le montrer lui-même.
     */
    suspend fun local(context: Context, config: ServerConfig, file: DeliveredFile): Result<File> =
        runCatching { cached(context, config, file) }

    /**
     * Ce qu'on peut montrer d'un fichier sans l'ouvrir: sa vignette s'il est image, ses premières
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

    /** Pourquoi un fichier nommé par une réponse ne s'ouvre pas, en mots (`Problem` ). */
    fun failureText(error: Throwable, file: DeliveredFile? = null): String = when {
        error is TransportError.MissingApiKey || error is TransportError.Http && error.status == 401 ->
            OnyxLocalization["Files your agent sends come through the Hermes Dashboard. Add its login in Settings → Connection details."]

        error is TransportError.Http && error.status == 404 -> {
            val relative = file != null && !file.path.startsWith("/") && !file.path.startsWith("~")
            if (relative) OnyxLocalization["It isn't on the server where the reply says, or the chat isn't on the server yet."]
            else OnyxLocalization["It isn't on the server where the reply says."]
        }

        error is TransportError.Http && error.status == 403 -> OnyxLocalization["The server won't share this file."]

        else -> OnyxLocalization["Couldn't fetch it:"] + " " + (error.message ?: OnyxLocalization["unknown error"])
    }

    /** Le fichier livré, dans le cache du téléphone, téléchargé une seule fois. */
    private suspend fun cached(context: Context, config: ServerConfig, file: DeliveredFile): File =
        withContext(Dispatchers.IO) {
            val target = File(context.cacheDir, "delivered").apply { mkdirs() }
            val local = File(target, file.name)
            if (local.exists() && local.length() > 0L) return@withContext local
            // Les fichiers de l'agent passent par le Dashboard; sans ses identifiants, rien n'est
            // récupérable, et `failureText` dit quoi faire.
            val bytes = ServeFilesApi(ServeClient(config)).bytes(file.path)
            local.writeBytes(bytes)
            local
        }

    /** Décode une vignette bornée, jamais l'image entière: un livrable peut être énorme. */
    private fun decodeThumbnail(local: File, maxPx: Int = 512): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(local.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxPx || bounds.outHeight / (sample * 2) >= maxPx) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(local.absolutePath, options)
    }

    /** Les premières lignes d'un fichier texte, comme l'aperçu d'un `MarkdownDocument`. */
    private fun readPreview(local: File, maxLines: Int = 12): String =
        local.useLines { lines -> lines.take(maxLines).joinToString("\n") }

    /**
     * Charge, hors recomposition, ce qu'on peut montrer d'un fichier livré. Un fichier ni image ni
     * texte reste à l'état [Preview.None]: sa pastille suffit.
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
 * La pastille d'un fichier livré (`DeliveredFileChip`): son symbole — ou sa vignette, ou sa
 * raison d'échec — puis son nom; au tap, il est récupéré et ouvert. Un aperçu texte s'affiche en
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
                    modifier = Modifier.size(18.dp).clip(RoundedCornerShape(4.dp)),
                )

                else -> OnyxIcon(symbol = DeliveredFiles.symbolFor(file.name), tint = theme.accent, size = 13.dp)
            }
            BasicText(
                text = file.name,
                localize = false,
                style = theme.caption.copy(color = theme.textColor),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // L'aperçu texte: les premières lignes, comme le rend `MarkdownDocument`. Sélectionnable
        // comme le reste du fil: c'est du texte à lire, donc à reprendre.
        if (preview is DeliveredFiles.Preview.Text && preview.text.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(theme.surfaceColor, RoundedCornerShape(10.dp))
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

        // L'état d'échec: pourquoi il ne s'ouvre pas, sous la pastille.
        if (preview is DeliveredFiles.Preview.Failure) {
            SelectableText(text = preview.text, localize = false, style = theme.caption.copy(color = theme.secondaryText))
        }
    }
}
