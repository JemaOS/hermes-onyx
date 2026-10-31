package com.onyx.app.ui.chat

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.onyx.app.data.model.ConversationFile
import com.onyx.app.data.model.FileFamily
import com.onyx.app.data.model.FileSort
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.fileDetailLine
import com.onyx.app.data.model.fileFamilyCounts
import com.onyx.app.data.model.fileFamilyLabel
import com.onyx.app.data.model.fileSortLabel
import com.onyx.app.data.model.visibleConversationFiles
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.design.FileThumbnail
import com.onyx.app.ui.design.FileThumbnailView
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxMenu
import com.onyx.app.ui.design.MenuItem
import com.onyx.app.ui.design.OnyxSocle
import com.onyx.app.ui.design.socleShadow
import com.onyx.app.ui.design.socleTitleStyle
import com.onyx.app.ui.design.socleMetaStyle
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.design.onyxTappable
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.theme.LocalOnyxTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * L'onglet **Fichiers** d'une conversation — **cartes à vignettes**, dans le langage « J ».
 *
 * Ce qu'il montre, de haut en bas : les **filtres** par famille **compacts, sur une seule rangée**
 * (Tout · Images · Vidéos · Audio · Documents · Archives) — le compte **sous le mot** —, le **tri**
 * dans un **contrôle compact** (une capsule qui ouvre les cinq ordres), puis une **grille paresseuse
 * de cartes à vignettes**. Chaque carte porte la **vignette déterministe** du fichier
 * ([FileThumbnail]) : l'**image** montre sa vraie vignette décodée (bornée par l'app), le
 * **document** une page stylisée avec son **type écrit**, le **son** une **forme d'onde** tirée des
 * premiers octets, l'**archive** un empilement — et la **pastille de type** est posée sur la vignette.
 *
 * **Un seul accent par écran** : la pastille du filtre actif. **Aucune pastille accentuée n'est
 * posée sur une carte** — l'état d'un fichier (à télécharger, en cours, disponible, en échec)
 * s'écrit **sous le nom**, dans l'encre de son état. **L'échec remplace le vide** : quand le serveur
 * n'a pas répondu, le panneau de problème tient la place de la grille et de l'état vide, avec
 * « Réessayer ». La liste est **paresseuse** ([LazyVerticalGrid]).
 *
 * Un appui **ouvre toujours quelque chose** ; un appui long ouvre le menu.
 */
@Composable
fun ChatFilesPane(
    files: List<ConversationFile>,
    accent: Color,
    config: ServerConfig,
    onOpen: (ConversationFile, File) -> Unit,
    modifier: Modifier = Modifier,
    problem: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    val theme = LocalOnyxTheme.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    val controller = remember(config.id) { ConversationFilesController(context, config) }

    var filterKey by rememberSaveable { mutableStateOf(FileFamily.ALL.key) }
    var sortKey by rememberSaveable { mutableStateOf(FileSort.RECENT.key) }
    var menuFor by remember { mutableStateOf<ConversationFile?>(null) }
    var sortMenu by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    val headBytes = remember { mutableStateMapOf<String, ByteArray>() }

    val filter = FileFamily.entries.firstOrNull { it.key == filterKey } ?: FileFamily.ALL
    val sort = FileSort.entries.firstOrNull { it.key == sortKey } ?: FileSort.RECENT
    val counts = remember(files) { fileFamilyCounts(files) }
    val visible = remember(files, filter, sort) { visibleConversationFiles(files, filter, sort) }

    LaunchedEffect(files, config.id) {
        files.forEach { file -> controller.prepare(file, scope) }
    }

    fun openFromTab(file: ConversationFile, local: File) {
        val mime = file.mime.ifBlank { FileViewers.mimeFor(file.name) }
        if (com.onyx.app.data.model.fileOpenRoute(file.name, mime) ==
            com.onyx.app.data.model.FileOpenRoute.EXTERNAL
        ) {
            openWith(context, local, file.name, mime) { message -> notice = message }
        } else {
            onOpen(file, local)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // L'échec **remplace** le vide : le panneau de problème tient la place de la grille.
        if (problem != null) FilesTrouble(problem = problem, onRetry = onRetry)

        FilesFilterRow(counts = counts, selected = filter, onSelect = { filterKey = it.key })
        FilesSortBar(
            shown = visible.size,
            total = files.size,
            sort = sort,
            onOpen = { sortMenu = true },
        )

        when {
            problem != null -> Unit

            files.isEmpty() -> FilesEmpty(
                title = OnyxLocalization["Nothing has been exchanged in this conversation yet."],
                detail = OnyxLocalization["Files the bot delivers and files you attach land here."],
                modifier = Modifier.weight(1f),
            )

            visible.isEmpty() -> FilesEmpty(
                title = emptyTitle(filter),
                detail = OnyxLocalization["Files the bot delivers and files you attach land here."],
                modifier = Modifier.weight(1f),
            )

            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(
                    start = OnyxSocle.ScreenMargin,
                    end = OnyxSocle.ScreenMargin,
                    top = OnyxSocle.Tight,
                    bottom = OnyxSocle.Loose,
                ),
                horizontalArrangement = Arrangement.spacedBy(OnyxSocle.Loose),
                verticalArrangement = Arrangement.spacedBy(OnyxSocle.Loose),
            ) {
                items(items = visible, key = { it.path }) { file ->
                    val availability = controller.state(file.path)
                    val available = availability as? FileAvailability.Available
                    // La forme d'onde vient des **premiers octets** dès que le fichier est là.
                    if (file.family == FileFamily.AUDIO && available != null && headBytes[file.path] == null) {
                        LaunchedEffect(file.path) {
                            val bytes = withContext(Dispatchers.IO) {
                                runCatching {
                                    available.local.inputStream().use { stream ->
                                        val buffer = ByteArray(FileThumbnail.WaveBytes)
                                        val read = stream.read(buffer)
                                        if (read <= 0) ByteArray(0) else buffer.copyOf(read)
                                    }
                                }.getOrDefault(ByteArray(0))
                            }
                            headBytes[file.path] = bytes
                        }
                    }
                    val plan = FileThumbnail.plan(file, headBytes[file.path])
                    ConversationFileCard(
                        file = file,
                        plan = plan,
                        availability = availability,
                        sizeLabel = VideoMeta.sizeLabel(controller.size(file.path) ?: 0L),
                        poster = controller.poster(file.path),
                        accent = accent,
                        onOpen = {
                            if (availability is FileAvailability.Available) {
                                openFromTab(file, availability.local)
                            } else {
                                scope.launch {
                                    try {
                                        openFromTab(file, controller.local(file))
                                    } catch (error: CancellationException) {
                                        throw error
                                    } catch (_: Throwable) {
                                        // L'échec est déjà écrit dans la carte, en mots.
                                    }
                                }
                            }
                        },
                        onLongPress = { menuFor = file },
                        onDownload = { controller.fetch(file, scope) },
                        onRetry = { controller.retry(file, scope) },
                    )
                    if (available != null) {
                        LaunchedEffect(available.local, file.path) { controller.loadPoster(file) }
                    }
                    if (file.family == FileFamily.IMAGE && availability is FileAvailability.Remote) {
                        LaunchedEffect(file.path) { controller.fetch(file, scope) }
                    }
                }
            }
        }

        notice?.let { message ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = OnyxSocle.ScreenMargin, vertical = OnyxSocle.Tight)
                    .background(theme.fill, RoundedCornerShape(50))
                    .padding(horizontal = OnyxSocle.Loose, vertical = OnyxSocle.Tight),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(OnyxSocle.Tight),
            ) {
                BasicText(
                    text = message,
                    localize = false,
                    style = theme.caption.copy(color = theme.textColor),
                    modifier = Modifier.weight(1f),
                )
                BasicText(
                    text = "Done",
                    style = theme.caption.copy(color = theme.accent, fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.onyxClickable { notice = null },
                )
            }
        }
    }

    if (sortMenu) {
        val sortItems = mutableListOf<MenuItem>()
        FileSort.entries.forEach { order ->
            sortItems += MenuItem(fileSortLabel(order), sortSymbol(order), checked = order == sort) {
                sortMenu = false
                sortKey = order.key
            }
        }
        OnyxMenu(items = sortItems, onDismiss = { sortMenu = false })
    }

    menuFor?.let { file ->
        val available = (controller.state(file.path) as? FileAvailability.Available)?.local
        OnyxMenu(
            items = listOfNotNull(
                MenuItem("Open", Symbols.PlayCircle) {
                    menuFor = null
                    if (available != null) {
                        openFromTab(file, available)
                    } else {
                        scope.launch {
                            try {
                                openFromTab(file, controller.local(file))
                            } catch (error: CancellationException) {
                                throw error
                            } catch (_: Throwable) {
                                // La carte dit déjà pourquoi.
                            }
                        }
                    }
                },
                available?.let { local ->
                    MenuItem("Share", Symbols.Share) {
                        menuFor = null
                        shareFile(context, local, file.name) { message -> notice = message }
                    }
                },
                available?.let { local ->
                    MenuItem("Save to the phone", Symbols.Folder) {
                        menuFor = null
                        scope.launch { notice = saveToPhone(context, local, file.name) }
                    }
                },
                MenuItem("Copy path", Symbols.DocOnClipboard) {
                    menuFor = null
                    scope.launch {
                        runCatching {
                            clipboard.setClipEntry(
                                ClipEntry(android.content.ClipData.newPlainText("Path", file.path)),
                            )
                        }
                        notice = OnyxLocalization["Path copied"]
                    }
                },
                available?.let { local ->
                    MenuItem("Open with", Symbols.ArrowRightSquare) {
                        menuFor = null
                        openWith(context, local, file.name, file.mime) { message -> notice = message }
                    }
                },
            ),
            onDismiss = { menuFor = null },
        )
    }
}

/**
 * La rangée de filtres : **une seule rangée**, six capsules compactes. Chaque capsule porte le nom
 * de la famille et son **compte sous le mot**. La capsule active est **le seul accent** de la
 * rangée ; les autres sont sur la gorge.
 */
@Composable
private fun FilesFilterRow(
    counts: Map<FileFamily, Int>,
    selected: FileFamily,
    onSelect: (FileFamily) -> Unit,
) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = OnyxSocle.ScreenMargin, vertical = OnyxSocle.Tight),
        horizontalArrangement = Arrangement.spacedBy(OnyxSocle.Tight / 2),
    ) {
        FileFamily.entries.filter { it.isFilter }.forEach { family ->
            val active = family == selected
            val count = counts[family] ?: 0
            // Le libellé assemblé du modèle (nom + compte) est la description accessible.
            val full = fileFamilyLabel(family, count)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(OnyxSocle.RadiusField))
                    .background(if (active) theme.accent else Color.Transparent)
                    .semantics { contentDescription = full }
                    .onyxTappable { onSelect(family) }
                    .padding(vertical = OnyxSocle.Tight),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                BasicText(
                    text = family.shortLabel(),
                    localize = false,
                    style = theme.caption.copy(
                        color = if (active) Color.White else theme.textColor,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 2.dp),
                )
                BasicText(
                    text = count.toString(),
                    localize = false,
                    style = theme.caption2.copy(
                        color = if (active) Color.White else theme.secondaryText,
                        fontWeight = FontWeight.Medium,
                    ),
                    maxLines = 1,
                )
            }
        }
    }
}

/** Le contrôle de tri **compact** : le compte à gauche, une capsule qui ouvre les cinq ordres. */
@Composable
private fun FilesSortBar(shown: Int, total: Int, sort: FileSort, onOpen: () -> Unit) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = OnyxSocle.ScreenMargin, end = OnyxSocle.ScreenMargin, bottom = OnyxSocle.Tight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        BasicText(
            text = if (shown == total) "$total ${OnyxLocalization["Files"].lowercase()}" else "$shown / $total",
            localize = false,
            style = theme.caption.copy(color = theme.secondaryText),
            maxLines = 1,
        )
        Row(
            modifier = Modifier
                .height(36.dp)
                .clip(RoundedCornerShape(50))
                .background(theme.fill)
                .onyxTappable { onOpen() }
                .padding(horizontal = OnyxSocle.Loose),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OnyxSocle.Tight),
        ) {
            OnyxIcon(symbol = Symbols.ArrowUpArrowDown, tint = theme.secondaryText, size = 13.dp, contentDescription = "Sort")
            BasicText(
                text = fileSortLabel(sort),
                localize = false,
                style = theme.caption.copy(color = theme.textColor),
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/**
 * **Une carte de fichier** : la vignette, le nom, le poids · la date, et l'état écrit en mots.
 * La vignette est la **vraie** pour une image, **dessinée** pour un document, un son, une archive.
 */
@Composable
private fun ConversationFileCard(
    file: ConversationFile,
    plan: FileThumbnail.Plan,
    availability: FileAvailability,
    sizeLabel: String,
    poster: android.graphics.Bitmap?,
    accent: Color,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onDownload: () -> Unit,
    onRetry: () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    // **La carte EST une carte.** La planche pose le fichier sur `.card`
    // (`hlib.py:612-617` : `box(…, R_CARD, 'solid fcard', …)` + `sh='sh1'`) — surface `.solid`,
    // rayon 20, ombre en deux couches — et **non** une vignette posée à même la page. C'est la
    // même famille que la carte de bot et la carte de conversation : une seule surface de carte
    // dans tout l'écran, avec son ombre **rendue** (`socleShadow`).
    val shape = RoundedCornerShape(OnyxSocle.RadiusCard)
    val ground = theme.formSurfaceColor
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .socleShadow(OnyxSocle.ShadowNear, OnyxSocle.RadiusCard, theme.dark)
            .clip(shape)
            .background(ground)
            .onyxTappable(onLongPress = onLongPress, onClick = onOpen),
    ) {
        // La vignette **plein cadre dans le haut de la carte** (`.fthumb`, `hcss.py:214-217`) :
        // c'est la carte qui découpe ses coins, la vignette ne redessine pas un second arrondi.
        FileThumbnailView(
            plan = plan,
            dark = theme.dark,
            image = poster?.asImageBitmap(),
            corner = 0.dp,
            modifier = Modifier.fillMaxWidth().height(100.dp),
        )
        Column(
            // `.fbody{padding:12px 14px 0}` — le corps de la carte (hcss.py:218).
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(OnyxSocle.Tight / 2),
        ) {
            BasicText(
                text = file.name,
                localize = false,
                // `.ttl.fname` : 16 sp, Bold (hcss.py:38, 219).
                style = socleTitleStyle(theme).copy(color = theme.textColor),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val detail = fileDetailLine(sizeLabel, file.appearedAtMillis)
            if (detail.isNotEmpty()) {
                BasicText(
                    // La donnée libre (le poids · la date) se replie sur deux lignes : elle n'a aucun
                    // mot clé à perdre, et le plafond des coupures ne remonte pas pour elle.
                    // `.fmeta` est **monospace** (`.mt.mono`, hcss.py:615) : le poids et la date
                    // sont des données chiffrées, à l'encre d'identifiant.
                    text = detail,
                    localize = false,
                    style = socleMetaStyle(theme).copy(
                        color = theme.secondaryText,
                        fontFamily = FontFamily.Monospace,
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            FileStateLine(availability = availability, accent = accent, onDownload = onDownload, onRetry = onRetry)
        }
    }
}

/** L'état du fichier sur le téléphone, dit en un mot — et son geste quand il y en a un. */
@Composable
private fun FileStateLine(
    availability: FileAvailability,
    accent: Color,
    onDownload: () -> Unit,
    onRetry: () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    val label: String
    val tint: Color
    val action: (() -> Unit)?
    when (availability) {
        is FileAvailability.Remote -> {
            label = OnyxLocalization["To download"]
            tint = theme.secondaryText
            action = onDownload
        }

        is FileAvailability.Fetching -> {
            val percent = availability.fraction?.let { "${(it * 100).toInt()} %" }
            label = percent ?: OnyxLocalization["Downloading…"]
            tint = accent
            action = null
        }

        is FileAvailability.Available -> {
            label = OnyxLocalization["Available"]
            tint = theme.success
            action = null
        }

        is FileAvailability.Failed -> {
            label = if (availability.retryable) OnyxLocalization["Retry"] else OnyxLocalization["Unavailable"]
            tint = theme.error
            action = if (availability.retryable) onRetry else null
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.then(if (action != null) Modifier.onyxClickable(onClick = action) else Modifier),
    ) {
        BasicText(
            text = label,
            localize = false,
            style = theme.caption2.copy(color = tint, fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            softWrap = false,
        )
        if (availability is FileAvailability.Failed) {
            BasicText(
                text = availability.reason,
                localize = false,
                style = theme.caption2.copy(color = theme.secondaryText),
                // **Ce qui a changé doit se lire.** Une phrase coupée à deux lignes ne disait plus
                // si le fichier avait été retiré, ou s'il n'avait jamais été écrit là.
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
    if (availability is FileAvailability.Fetching) {
        val fraction = availability.fraction
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(50))
                .background(theme.fill),
        ) {
            if (fraction != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(3.dp)
                        .background(accent),
                )
            }
        }
    }
}

/** Le panneau de problème — avec « Réessayer » — **à la place** du vide, jamais au-dessus. */
@Composable
private fun FilesTrouble(problem: String, onRetry: (() -> Unit)?) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = OnyxSocle.ScreenMargin, vertical = OnyxSocle.Tight)
            .background(theme.surfaceColor, RoundedCornerShape(OnyxSocle.RadiusCard))
            .padding(horizontal = OnyxSocle.Loose, vertical = OnyxSocle.Tight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OnyxSocle.Tight),
    ) {
        OnyxIcon(symbol = Symbols.ExclamationTriangle, tint = theme.warning, size = 16.dp, contentDescription = null)
        BasicText(
            text = problem,
            localize = false,
            style = theme.caption.copy(color = theme.secondaryText),
            modifier = Modifier.weight(1f),
        )
        if (onRetry != null) {
            BasicText(
                text = "Retry",
                style = theme.caption.copy(color = theme.accent, fontWeight = FontWeight.SemiBold),
                modifier = Modifier.onyxClickable(onClick = onRetry),
            )
        }
    }
}

/** L'état vide d'un filtre : il nomme ce qui manque, et jamais un écran blanc. */
private fun emptyTitle(family: FileFamily): String = when (family) {
    FileFamily.IMAGE -> OnyxLocalization["No picture in this conversation."]
    FileFamily.VIDEO -> OnyxLocalization["No video in this conversation."]
    FileFamily.AUDIO -> OnyxLocalization["No sound in this conversation."]
    FileFamily.DOCUMENT -> OnyxLocalization["No document in this conversation."]
    FileFamily.ARCHIVE -> OnyxLocalization["No archive in this conversation."]
    FileFamily.ALL, FileFamily.OTHER -> OnyxLocalization["Nothing has been exchanged in this conversation yet."]
}

/** Le nom court d'une famille, posé au-dessus de son compte. */
private fun FileFamily.shortLabel(): String = when (this) {
    FileFamily.ALL -> OnyxLocalization["All"]
    FileFamily.IMAGE -> OnyxLocalization["Images"]
    FileFamily.VIDEO -> OnyxLocalization["Videos"]
    FileFamily.AUDIO -> OnyxLocalization["Audio"]
    FileFamily.DOCUMENT -> OnyxLocalization["Documents"]
    FileFamily.ARCHIVE -> OnyxLocalization["Archives"]
    FileFamily.OTHER -> OnyxLocalization["Other"]
}

private fun sortSymbol(sort: FileSort): Int = when (sort) {
    FileSort.RECENT, FileSort.OLDEST -> Symbols.ArrowUpArrowDown
    FileSort.NAME -> Symbols.Doc
    FileSort.SIZE -> Symbols.ArrowUpArrowDown
    FileSort.TYPE -> Symbols.DocOnDoc
}

@Composable
private fun FilesEmpty(title: String, detail: String?, modifier: Modifier = Modifier) {
    val theme = LocalOnyxTheme.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = OnyxSocle.Loose + OnyxSocle.Tight, vertical = OnyxSocle.Loose),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(OnyxSocle.Tight),
    ) {
        BasicText(
            text = title,
            style = theme.subheadline.copy(color = theme.textColor, textAlign = TextAlign.Center),
        )
        if (detail != null) {
            BasicText(
                text = detail,
                style = theme.caption.copy(color = theme.secondaryText, textAlign = TextAlign.Center),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Les gestes du menu : partager, enregistrer, ouvrir avec
// ---------------------------------------------------------------------------------------------

/** L'adresse partageable d'un fichier du cache. */
private fun shareable(context: Context, local: File): Uri =
    FileProvider.getUriForFile(context, "${context.packageName}.files", local)

/** Confie le fichier à une autre application, par le partage du système. */
private fun shareFile(context: Context, local: File, name: String, onMessage: (String) -> Unit) {
    runCatching {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = FileViewers.mimeFor(name)
            putExtra(Intent.EXTRA_STREAM, shareable(context, local))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newUri(context.contentResolver, name, shareable(context, local))
        }
        context.startActivity(Intent.createChooser(intent, name))
    }.onFailure { error -> onMessage(error.message ?: OnyxLocalization["Sharing isn't possible for this file."]) }
}

/** Ouvre le fichier avec une application du téléphone, en le disant si rien ne sait le lire. */
private fun openWith(
    context: Context,
    local: File,
    name: String,
    mime: String,
    onMessage: (String) -> Unit,
) {
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(shareable(context, local), mime.ifBlank { FileViewers.mimeFor(name) })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, name))
    }.onFailure { error -> onMessage(error.message ?: OnyxLocalization["Couldn't open it."]) }
}

/**
 * Enregistre le fichier dans les téléchargements du téléphone.
 *
 * Deux chemins selon la version d'Android : le magasin `Downloads` à partir d'Android 10 (aucune
 * permission à demander), et le dossier public en dessous — où un refus de permission se **dit** au
 * lieu de laisser croire que le fichier est enregistré. Rend la phrase à afficher.
 */
private suspend fun saveToPhone(context: Context, local: File, name: String): String = withContext(Dispatchers.IO) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, FileViewers.mimeFor(name))
            }
            val target = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return@withContext OnyxLocalization["Couldn't save it."]
            context.contentResolver.openOutputStream(target)?.use { output ->
                local.inputStream().use { input -> input.copyTo(output) }
            } ?: return@withContext OnyxLocalization["Couldn't save it."]
        } else {
            val directory = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "",
            )
            if (!directory.exists()) directory.mkdirs()
            local.copyTo(File(directory, name), overwrite = true)
        }
        OnyxLocalization["Saved to the phone"]
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        error.message ?: OnyxLocalization["Couldn't save it."]
    }
}
