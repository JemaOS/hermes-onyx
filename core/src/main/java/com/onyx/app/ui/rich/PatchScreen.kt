package com.onyx.app.ui.rich

import com.onyx.app.ui.design.onyxPage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import com.onyx.app.ui.localization.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.onyx.app.ui.design.OnyxPageHeader
import com.onyx.app.ui.theme.LocalOnyxTheme

/** Une ligne d'un fichier de diff (`PatchFile.Line`). */
data class PatchLine(val kind: Kind, val text: String) {
    enum class Kind { CONTEXT, ADDED, REMOVED, HUNK }
}

/** Un fichier d'un diff unifié (`PatchFile`). */
data class PatchFile(
    val id: Int,
    val path: String,
    val added: Int,
    val removed: Int,
    val lines: List<PatchLine>,
)

/**
 * Lit un diff unifié (`git diff`, ou celui de Python) en fichiers.
 *
 * Miroir de `Patch.files` : les chemins `a/` et `b/` s'effacent, les en-têtes de difflib sont
 * coupés à la tabulation, et les lignes d'index ne disent rien au lecteur.
 */
fun patchFiles(text: String): List<PatchFile> {
    val files = mutableListOf<PatchFile>()
    var oldPath: String? = null

    fun clean(raw: String): String {
        var path = raw.trim()
        if (path.contains('\t')) path = path.substringBefore('\t')
        if (path.startsWith("a/") || path.startsWith("b/")) path = path.drop(2)
        return path
    }

    fun start(path: String) {
        files += PatchFile(id = files.size, path = path, added = 0, removed = 0, lines = emptyList())
    }

    fun append(line: PatchLine, added: Int = 0, removed: Int = 0) {
        val index = files.lastIndex
        if (index < 0) return
        val file = files[index]
        files[index] = file.copy(
            added = file.added + added,
            removed = file.removed + removed,
            lines = file.lines + line,
        )
    }

    text.split("\n").forEach { line ->
        when {
            line.startsWith("diff --git ") -> {
                val parts = line.split(" ")
                start(if (parts.size >= 4) clean(parts[3]) else "")
                oldPath = null
            }

            line.startsWith("--- ") -> oldPath = clean(line.drop(4))

            line.startsWith("+++ ") -> {
                val new = clean(line.drop(4))
                val path = if (new == "/dev/null") (oldPath ?: new) else new
                val last = files.lastOrNull()
                if (last != null && last.lines.isEmpty()) {
                    files[files.lastIndex] = last.copy(path = path)
                } else {
                    start(path)
                }
            }

            files.isNotEmpty() -> when {
                line.startsWith("@@") -> append(PatchLine(PatchLine.Kind.HUNK, line))
                line.startsWith("+") -> append(PatchLine(PatchLine.Kind.ADDED, line), added = 1)
                line.startsWith("-") -> append(PatchLine(PatchLine.Kind.REMOVED, line), removed = 1)
                line.startsWith(" ") -> append(PatchLine(PatchLine.Kind.CONTEXT, line))
                else -> Unit
            }
        }
    }
    return files.filter { it.lines.isNotEmpty() || it.path.isNotEmpty() }
}

/** « 2 files · +12 −3 ». */
fun patchSummary(files: List<PatchFile>): String {
    val added = files.sumOf { it.added }
    val removed = files.sumOf { it.removed }
    val count = if (files.size == 1) "1 file" else "${files.size} files"
    return "$count · +$added −$removed"
}

/** Vrai si ce texte porte un diff à montrer. */
fun looksLikePatch(text: String): Boolean =
    text.contains("diff --git ") || (text.contains("+++ ") && text.contains("@@"))

/**
 * Un changement à lire (`PatchView`).
 *
 * Chaque fichier avec ce qu'il ajoute et retire, ses lignes en monospace, les ajouts en vert et
 * les retraits en rouge ; une longue ligne passe à la ligne au lieu de défiler.
 */
@Composable
fun PatchScreen(
    title: String,
    patch: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    val files = remember(patch) { patchFiles(patch) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .onyxPage(theme),
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            OnyxPageHeader(title = title, onBack = onBack)
        }

        if (files.isEmpty()) {
            BasicText(
                text = "The change has no lines to show.",
                style = theme.body.copy(color = theme.secondaryText),
                modifier = Modifier.padding(20.dp),
            )
            return@Column
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            files.forEach { file ->
                item(key = "header-${file.id}") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(theme.surfaceColor)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BasicText(
                            text = file.path,
                            localize = false,
                            style = theme.footnote.copy(color = theme.textColor, fontFamily = FontFamily.Monospace),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        BasicText(
                            text = "+${file.added}",
                            style = theme.footnote.copy(color = theme.success, fontFamily = FontFamily.Monospace),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                        BasicText(
                            text = "−${file.removed}",
                            style = theme.footnote.copy(color = theme.error, fontFamily = FontFamily.Monospace),
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
                items(
                    count = minOf(file.lines.size, LINE_CAP),
                    key = { index -> "${file.id}-$index" },
                ) { index ->
                    val line = file.lines[index]
                    BasicText(
                        text = line.text.ifEmpty { " " },
                        localize = false,
                        style = theme.caption.copy(
                            color = if (line.kind == PatchLine.Kind.HUNK) theme.secondaryText else theme.textColor,
                            fontFamily = FontFamily.Monospace,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(backgroundOf(line.kind))
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
                if (file.lines.size > LINE_CAP) {
                    item(key = "more-${file.id}") {
                        BasicText(
                            text = "… ${file.lines.size - LINE_CAP} more lines",
                            style = theme.caption.copy(color = theme.secondaryText),
                            modifier = Modifier.padding(6.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun backgroundOf(kind: PatchLine.Kind): Color {
    val theme = LocalOnyxTheme.current
    return when (kind) {
        PatchLine.Kind.ADDED -> theme.success.copy(alpha = 0.16f)
        PatchLine.Kind.REMOVED -> theme.error.copy(alpha = 0.16f)
        else -> Color.Transparent
    }
}

/** Un fichier de cette longueur montre ses premières lignes, et dit combien il en reste. */
private const val LINE_CAP = 1500
