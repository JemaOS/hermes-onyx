package com.onyx.app.ui.chat

import com.onyx.app.ui.design.OnyxSpace

import com.onyx.app.ui.design.onyxPage

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxPageHeader
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.theme.LocalOnyxTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

/** Le document à montrer : son adresse (le cache du téléphone) et son nom. */
data class DocumentSource(val uri: Uri, val name: String)

/** Ce qu'un lecteur de texte a le droit de charger : au-delà, on le dit au lieu de mentir. */
private const val MaxTextBytes = 512 * 1024

/** Ce qu'un lecteur a pu lire d'un fichier texte. */
data class DocumentText(val text: String, val truncated: Boolean, val problem: String? = null)

/**
 * Le lecteur d'un document **texte** (`md`, `txt`, `csv`, `json`, `html`…).
 *
 * C'est le lecteur de document de la maison : le nom en tête, et le contenu en monospace,
 * sélectionnable et défilable — la mise en forme d'un fichier de données n'a pas à être devinée.
 * Un fichier plus gros que [MaxTextBytes] s'affiche en entier mais **le dit** : jamais une
 * troncature silencieuse.
 */
@Composable
fun DocumentScreen(
    source: DocumentSource,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val document by produceState<DocumentText?>(initialValue = null, source.uri) {
        value = withContext(Dispatchers.IO) { readDocument(context, source.uri) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .onyxPage(theme),
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            OnyxPageHeader(
                title = source.name,
                onBack = onClose,
                localizeTitle = false,
            )
        }

        val loaded = document
        when {
            loaded == null -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                BasicText("Loading…", style = theme.body.copy(color = theme.secondaryText))
            }

            loaded.problem != null -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = loaded.problem.orEmpty(),
                    localize = false,
                    style = theme.footnote.copy(color = theme.secondaryText),
                    modifier = Modifier.padding(24.dp),
                )
            }

            else -> Column(modifier = Modifier.fillMaxSize()) {
                if (loaded.truncated) {
                    BasicText(
                        text = OnyxLocalization.format(
                            "Only the first %1\$s of this file is shown.",
                            VideoMeta.sizeLabel(MaxTextBytes.toLong()),
                        ),
                        style = theme.caption.copy(color = theme.warning),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
                SelectionContainer(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    BasicText(
                        text = loaded.text,
                        localize = false,
                        style = theme.footnote.copy(
                            color = theme.textColor,
                            fontFamily = FontFamily.Monospace,
                        ),
                    )
                }
            }
        }
    }
}

/** Lit un fichier texte, borné : la fin au-delà de la borne est annoncée, jamais perdue en silence. */
private fun readDocument(context: Context, uri: Uri): DocumentText {
    return try {
        val stream = when (uri.scheme) {
            "file" -> File(uri.path.orEmpty()).inputStream()
            else -> context.contentResolver.openInputStream(uri)
        }
        if (stream == null) {
            DocumentText("", false, OnyxLocalization["Couldn't open it."])
        } else {
            stream.use { input ->
                val buffer = ByteArray(MaxTextBytes + 1)
                var read = 0
                while (read < buffer.size) {
                    val step = input.read(buffer, read, buffer.size - read)
                    if (step <= 0) break
                    read += step
                }
                val full = read > MaxTextBytes
                DocumentText(
                    text = String(buffer, 0, minOf(read, MaxTextBytes), Charsets.UTF_8),
                    truncated = full,
                )
            }
        }
    } catch (error: Throwable) {
        DocumentText("", false, error.message ?: OnyxLocalization["Couldn't open it."])
    }
}

/** Une entrée d'une archive, telle qu'on la montre. */
data class ArchiveEntry(val name: String, val size: Long, val directory: Boolean)

/** Ce qu'on a pu lire d'une archive : ses entrées, ou pourquoi on n'a pas pu. */
data class ArchiveListing(val entries: List<ArchiveEntry>, val problem: String? = null)

/**
 * Le contenu d'une **archive** (`zip`, `jar`, `apk`), quand c'est une archive d'alignement de
 * fichiers — la seule qu'on puisse lire sur place, sans rien extraire : on n'ouvre pas le contenu,
 * on en montre la liste. Une archive qu'on ne sait pas lire (`tar`, `7z`, `rar`, `gz`) n'est pas un
 * échec : l'écran le dit et propose **l'ouverture par le système**, jamais un appui sans effet.
 */
@Composable
fun ArchiveScreen(
    source: DocumentSource,
    onClose: () -> Unit,
    onOpenExternally: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    val listing by produceState<ArchiveListing?>(initialValue = null, source.uri) {
        value = withContext(Dispatchers.IO) { readArchive(source.uri) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .onyxPage(theme),
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            OnyxPageHeader(
                title = source.name,
                onBack = onClose,
                localizeTitle = false,
            )
        }

        val read = listing
        when {
            read == null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                BasicText("Loading…", style = theme.body.copy(color = theme.secondaryText))
            }

            read.problem != null -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                BasicText(
                    text = read.problem.orEmpty(),
                    localize = false,
                    style = theme.footnote.copy(color = theme.secondaryText),
                )
                Row(
                    modifier = Modifier
                        .background(theme.accent, RoundedCornerShape(50))
                        .onyxClickable(onClick = onOpenExternally)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    OnyxIcon(Symbols.Share, tint = theme.pageColor, size = 14.dp)
                    BasicText("Open with", style = theme.subheadline.copy(color = theme.pageColor))
                }
            }

            read.entries.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = OnyxLocalization["This archive is empty."],
                    style = theme.body.copy(color = theme.secondaryText),
                )
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                itemsIndexed(items = read.entries, key = { index, entry -> "$index-${entry.name}" }) { _, entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(theme.surfaceColor, RoundedCornerShape(OnyxSpace.ControlCorner))
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        androidx.compose.foundation.Image(
                            painter = painterResource(
                                if (entry.directory) Symbols.FolderFill else Symbols.Doc,
                            ),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(if (entry.directory) theme.accent else theme.secondaryText),
                            modifier = Modifier.size(15.dp),
                        )
                        BasicText(
                            text = entry.name,
                            localize = false,
                            style = theme.footnote.copy(color = theme.textColor),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (!entry.directory) {
                            BasicText(
                                text = VideoMeta.sizeLabel(entry.size),
                                localize = false,
                                style = theme.caption.copy(color = theme.secondaryText),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Ouvre l'archive comme un alignement de fichiers ; sinon, dit pourquoi et laisse faire le système. */
private fun readArchive(uri: Uri): ArchiveListing {
    val path = uri.path
    if (uri.scheme != "file" || path.isNullOrEmpty()) {
        return ArchiveListing(emptyList(), OnyxLocalization["This archive can't be read here."])
    }
    return try {
        ZipFile(File(path)).use { archive ->
            val entries = archive.entries().toList()
                .map { entry -> ArchiveEntry(entry.name, entry.size.coerceAtLeast(0L), entry.isDirectory) }
                .sortedWith(compareBy({ !it.directory }, { it.name.lowercase() }))
            ArchiveListing(entries)
        }
    } catch (error: Throwable) {
        ArchiveListing(emptyList(), OnyxLocalization["This archive can't be read here."])
    }
}
