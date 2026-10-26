package com.onyx.app.ui.files

import android.content.ClipData
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.onyx.app.ui.localization.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onyx.app.data.model.serve.FileEntry
import com.onyx.app.ui.design.AlertAction
import com.onyx.app.ui.design.OnyxAlert
import com.onyx.app.ui.design.OnyxFormSection
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxMenu
import com.onyx.app.ui.design.OnyxPageHeader
import com.onyx.app.ui.design.OnyxPrompt
import com.onyx.app.ui.design.OnyxRow
import com.onyx.app.ui.design.OnyxRowDivider
import com.onyx.app.ui.design.MenuItem
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.design.onyxTappable
import com.onyx.app.ui.theme.LocalOnyxTheme
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.ClipEntry

/**
 * Réglages → Fichiers.
 *
 * Comme: la racine « Places », les dossiers qu'on empile, les fichiers
 * texte qu'on édite, les autres qu'on ouvre, et le menu de dossier (téléverser, nouveau dossier,
 * fichiers cachés, aller à un chemin).
 */
@Composable
fun FilesScreen(
    viewModel: FilesViewModel,
    onBack: () -> Unit,
    onOpenFile: (FileEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current

    var showMenu by remember { mutableStateOf(false) }
    var showNewFolder by remember { mutableStateOf(false) }
    var showGoTo by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var goToPath by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<FileEntry?>(null) }

    val entries = remember(state.entries, state.showHidden) {
        if (state.showHidden) state.entries else state.entries.filterNot { it.name.startsWith(".") }
    }

    // Le geste retour d'Android remonte d'un dossier, comme le bouton.
    BackHandler(enabled = !state.atRoot) { viewModel.back() }

    val uploader = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri -> readAndUpload(context, uri, viewModel) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.pageColor),
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            OnyxPageHeader(
                title = state.title,
                onBack = { if (!viewModel.back()) onBack() },
                action = if (state.atRoot) null else "…",
                onAction = { showMenu = true },
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 40.dp),
        ) {
            if (state.path != null) {
                BasicText(
                    text = state.path.orEmpty(),
                    localize = false,
                    style = theme.caption.copy(color = theme.secondaryText, fontFamily = FontFamily.Monospace),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
            }

            state.working?.let { working ->
                OnyxFormSection {
                    OnyxRow {
                        BasicText(text = working, style = theme.footnote.copy(color = theme.secondaryText))
                    }
                }
            }

            state.problem?.let { problem ->
                OnyxFormSection {
                    OnyxRow {
                        OnyxIcon(Symbols.ExclamationTriangle, tint = theme.warning, size = 16.dp)
                        BasicText(
                            text = problem,
                            localize = false,
                            style = theme.footnote.copy(color = theme.secondaryText),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    OnyxRowDivider()
                    OnyxRow(onClick = viewModel::reload) {
                        BasicText("Try Again", style = theme.body.copy(color = theme.accent))
                    }
                }
            }

            when {
                state.loading -> OnyxFormSection {
                    OnyxRow { BasicText("Loading from Onyx…", style = theme.body.copy(color = theme.secondaryText)) }
                }

                state.atRoot -> OnyxFormSection(header = "Places") {
                    OnyxRow(
                        onClick = { state.home?.let { viewModel.open(it) } ?: viewModel.open("~") },
                    ) {
                        OnyxIcon(Symbols.Brain, tint = theme.accent, size = 18.dp)
                        BasicText(
                            text = "Hermes",
                            style = theme.body.copy(color = theme.textColor),
                            modifier = Modifier.weight(1f),
                        )
                        OnyxIcon(Symbols.ChevronRight, tint = theme.tertiaryText, size = 12.dp)
                    }
                }

                else -> OnyxFormSection(
                    footer = "Names that start with a dot are hidden.",
                ) {
                    if (entries.isEmpty()) {
                        OnyxRow {
                            BasicText("This folder is empty.", style = theme.body.copy(color = theme.secondaryText))
                        }
                        return@OnyxFormSection
                    }
                    entries.forEachIndexed { index, entry ->
                        if (index > 0) OnyxRowDivider()
                        FileRow(
                            entry = entry,
                            onClick = {
                                if (entry.isDirectory) {
                                    viewModel.open(entry)
                                } else if (entry.isText) {
                                    onOpenFile(entry)
                                } else {
                                    // Un fichier binaire n'a pas d'éditeur: on le télécharge.
                                    scope.launch {
                                        val bytes = viewModel.bytes(entry)
                                        if (bytes != null) downloadToCache(context, entry, bytes)
                                    }
                                }
                            },
                            onLongPress = { pendingDelete = entry },
                        )
                    }
                }
            }
        }
    }

    if (showMenu) {
        OnyxMenu(
            items = listOf(
                MenuItem("Upload Files…", Symbols.FolderBadgePlus) {
                    showMenu = false
                    uploader.launch(arrayOf("*/*"))
                },
                MenuItem("New Folder…", Symbols.Folder) {
                    showMenu = false
                    newFolderName = ""
                    showNewFolder = true
                },
                MenuItem("Show Hidden Files", Symbols.EyeSlash, checked = state.showHidden) {
                    showMenu = false
                    viewModel.toggleHidden()
                },
                MenuItem("Go to Folder…", Symbols.ArrowTurnDownRight) {
                    showMenu = false
                    goToPath = ""
                    showGoTo = true
                },
                MenuItem("Copy Path", Symbols.DocOnDoc) {
                    showMenu = false
                    state.path?.let { path ->
                        scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Path", path))) }
                    }
                },
            ),
            onDismiss = { showMenu = false },
        )
    }

    if (showNewFolder) {
        OnyxPrompt(
            title = "New Folder",
            placeholder = "Name",
            value = newFolderName,
            onValueChange = { newFolderName = it },
            confirmLabel = "Create",
            onConfirm = {
                showNewFolder = false
                viewModel.makeFolder(newFolderName)
            },
            onDismiss = { showNewFolder = false },
        )
    }

    if (showGoTo) {
        OnyxPrompt(
            title = "Go to Folder",
            message = "A path on the Hermes host. ~ is its user's home.",
            placeholder = "/path/on/the/server",
            value = goToPath,
            onValueChange = { goToPath = it },
            confirmLabel = "Go",
            onConfirm = {
                showGoTo = false
                if (goToPath.isNotBlank()) viewModel.open(goToPath.trim())
            },
            onDismiss = { showGoTo = false },
        )
    }

    pendingDelete?.let { entry ->
        OnyxAlert(
            title = if (entry.isDirectory) "Delete the folder “${entry.name}” and everything in it?" else "Delete “${entry.name}”?",
            message = "It is removed from the Hermes host. This can't be undone.",
            actions = listOf(
                AlertAction("Cancel") { pendingDelete = null },
                AlertAction(if (entry.isDirectory) "Delete Folder and Its Contents" else "Delete File", destructive = true) {
                    pendingDelete = null
                    viewModel.delete(entry)
                },
            ),
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun FileRow(entry: FileEntry, onClick: () -> Unit, onLongPress: () -> Unit) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onyxTappable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OnyxIcon(
            symbol = if (entry.isDirectory) Symbols.FolderFill else Symbols.Doc,
            tint = if (entry.isDirectory) theme.accent else theme.secondaryText,
            size = 18.dp,
        )
        Column(modifier = Modifier.weight(1f)) {
            BasicText(
                text = entry.name,
                localize = false,
                style = theme.body.copy(color = theme.textColor),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val detail = listOfNotNull(
                entry.displaySize.takeIf { it.isNotEmpty() },
                entry.modifiedAt,
            ).joinToString(" · ")
            if (detail.isNotBlank()) {
                BasicText(text = detail, localize = false, style = theme.footnote.copy(color = theme.secondaryText))
            }
        }
        BasicText(
            text = "Delete",
            style = theme.footnote.copy(color = theme.error),
            modifier = Modifier.onyxClickable(onClick = onLongPress),
        )
    }
}

/** Lit un document choisi et le téléverse sous son nom d'origine. */
private fun readAndUpload(context: android.content.Context, uri: Uri, viewModel: FilesViewModel) {
    val name = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
    } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "file"
    val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull() ?: return
    viewModel.upload(name, bytes)
}

/** Écrit un fichier binaire dans le cache et le confie au système, faute d'éditeur. */
private fun downloadToCache(context: android.content.Context, entry: FileEntry, bytes: ByteArray) {
    runCatching {
        val file = java.io.File(context.cacheDir, entry.name)
        file.writeBytes(bytes)
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/octet-stream")
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(intent, entry.name))
    }
}

/**
 * L'éditeur d'un fichier texte (`ServerTextFileView`).
 *
 * Le chemin en en-tête, un éditeur monospace qui remplit la page, et « Save » à droite.
 */
@Composable
fun FileEditorScreen(
    viewModel: FileEditorViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    purpose: String? = null,
) {
    val theme = LocalOnyxTheme.current
    val text by viewModel.text.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.pageColor),
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            OnyxPageHeader(
                title = title ?: viewModel.filePath.substringAfterLast('/'),
                onBack = onBack,
                action = when {
                    state.saving -> "Saving…"
                    else -> "Save"
                },
                onAction = viewModel::save,
                localizeTitle = false,
            )
        }

        BasicText(
            text = viewModel.filePath,
            localize = false,
            style = theme.caption.copy(color = theme.secondaryText, fontFamily = FontFamily.Monospace),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )

        purpose?.let {
            BasicText(
                text = it,
                style = theme.footnote.copy(color = theme.secondaryText),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
        }

        state.problem?.let {
            BasicText(
                text = it,
                localize = false,
                style = theme.footnote.copy(color = theme.error),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        when {
            state.loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                BasicText("Loading…", style = theme.body.copy(color = theme.secondaryText))
            }

            state.binary -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                BasicText(
                    "This file isn't text, so it can't be edited here.",
                    style = theme.body.copy(color = theme.secondaryText),
                )
            }

            else -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .background(theme.surfaceColor, RoundedCornerShape(12.dp))
                    .padding(10.dp),
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = viewModel::onTextChange,
                    textStyle = theme.footnote.copy(color = theme.textColor, fontFamily = FontFamily.Monospace),
                    cursorBrush = SolidColor(theme.accent),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        if (state.truncated) {
            BasicText(
                text = "The file is longer than the editor shows; saving keeps what you see.",
                style = theme.caption.copy(color = theme.warning),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
    }
}
