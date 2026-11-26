package com.onyx.app.ui.rich

import com.onyx.app.ui.design.OnyxSpace

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.onyx.app.ui.localization.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.onyx.app.ui.design.SelectableText
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.theme.LocalOnyxTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Un morceau de réponse : du texte Markdown, ou un bloc de code. */
data class MarkdownBlock(
    val prose: String? = null,
    val code: String? = null,
    val language: String? = null,
    /** Une formule, à rendre par KaTeX (le contenu est du TeX, sans les `$$`). */
    val math: String? = null,
    /** Une citation ; son contenu est du Markdown, rendu récursivement. */
    val quote: String? = null,
    /** Un tableau GFM ; Markwon n'offre ni teinte d'en-tête ni défilement, Compose s'en charge. */
    val table: MarkdownTable? = null,
) {
    val isMermaid: Boolean get() = language?.lowercase() == "mermaid"
}

/** Le composant qui doit réellement dessiner un bloc, y compris pendant le streaming. */
enum class MarkdownRenderer { PROSE, CODE, LIVE_CODE, MERMAID, MATH, QUOTE, TABLE, AGENT_PAGE }

/**
 * Le routage de `MarkdownBlockView` : un diagramme, une formule ou une page encore en train
 * d'arriver reste dans une fenêtre de code ; les blocs précédents gardent leur vrai rendu.
 */
fun MarkdownBlock.renderer(trailing: Boolean): MarkdownRenderer = when {
    trailing && (code != null || math != null) -> MarkdownRenderer.LIVE_CODE
    math != null -> MarkdownRenderer.MATH
    code != null && language?.equals("html", ignoreCase = true) == true -> MarkdownRenderer.AGENT_PAGE
    code != null && isMermaid -> MarkdownRenderer.MERMAID
    code != null -> MarkdownRenderer.CODE
    quote != null -> MarkdownRenderer.QUOTE
    table != null -> MarkdownRenderer.TABLE
    else -> MarkdownRenderer.PROSE
}

/** L'alignement d'une colonne de tableau, comme `MarkdownBlock.ColumnAlignment` côté référence. */
enum class TableAlignment { LEADING, CENTER, TRAILING }

/** Un tableau GFM : son en-tête, ses lignes, et l'alignement de chaque colonne. */
data class MarkdownTable(
    val header: List<String>,
    val rows: List<List<String>> = emptyList(),
    val alignments: List<TableAlignment> = emptyList(),
)

/**
 * Découpe une réponse en texte et en blocs de code, sur les clôtures de code (```).
 *
 * Markwon rend la prose ; les blocs de code demandent davantage — un surligneur, des numéros de
 * ligne et un bouton « copier » —, et les citations et les tableaux sont détachés ici pour que
 * Compose les rende (barre de citation, en-tête teinté, défilement horizontal), comme
 * `MarkdownView` le fait.
 */
fun splitMarkdown(markdown: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val lines = markdown.split("\n")
    val prose = StringBuilder()
    var index = 0

    fun flushProse() {
        val text = prose.toString().trim('\n')
        if (text.isNotBlank()) blocks += MarkdownBlock(prose = text)
        prose.setLength(0)
    }

    while (index < lines.size) {
        val line = lines[index]
        val fence = line.trimStart()

        // Une formule détachée : `$$ … $$` ou `\[ … \]`, sur une ligne ou plusieurs.
        val mathDelimiters = when {
            fence.startsWith("$$") -> "$$" to "$$"
            fence.startsWith("\\[") -> "\\[" to "\\]"
            else -> null
        }
        if (mathDelimiters != null) {
            val (opener, closer) = mathDelimiters
            val rest = fence.removePrefix(opener)
            val closesInline = rest.isNotEmpty() && rest.endsWith(closer)
            val sameLine = rest.removeSuffix(closer).trim()
            if (closesInline && sameLine.isEmpty()) {
                // Un délimiteur vide (`$$$$`) est de la prose, comme dans le parseur la référence.
                prose.appendLine(line)
                index++
                continue
            }
            flushProse()
            if (closesInline) {
                blocks += MarkdownBlock(math = sameLine)
                index++
                continue
            }
            val tex = StringBuilder()
            val inline = rest.trim()
            if (inline.isNotEmpty()) tex.append(inline)
            index++
            while (index < lines.size && !lines[index].trimStart().startsWith(closer)) {
                if (tex.isNotEmpty()) tex.append('\n')
                tex.append(lines[index])
                index++
            }
            index++ // la fermeture, ou la fin d'un bloc encore en streaming
            blocks += MarkdownBlock(math = tex.toString().trim())
            continue
        }

        if (fence.startsWith("```")) {
            flushProse()
            val language = fence.removePrefix("```").trim().ifEmpty { null }
            val code = StringBuilder()
            index++
            while (index < lines.size && !lines[index].trimStart().startsWith("```")) {
                code.appendLine(lines[index])
                index++
            }
            index++ // la clôture
            blocks += MarkdownBlock(code = code.toString().trimEnd('\n'), language = language)
            continue
        }

        // Un tableau GFM : une ligne d'en-tête suivie de sa ligne de séparation `|---|:--:|`.
        if (isTableRow(line) && index + 1 < lines.size && isTableDelimiter(lines[index + 1])) {
            flushProse()
            val header = splitTableRow(line)
            val alignments = splitTableRow(lines[index + 1]).map { alignmentOf(it) }
            index += 2
            val rows = mutableListOf<List<String>>()
            while (index < lines.size && isTableRow(lines[index])) {
                rows += splitTableRow(lines[index])
                index++
            }
            blocks += MarkdownBlock(table = MarkdownTable(header = header, rows = rows, alignments = alignments))
            continue
        }

        // Une citation : une suite de lignes préfixées par « > ». Le contenu reste du Markdown,
        // si bien qu'un tableau ou une liste dans la citation se rendent comme ailleurs.
        if (fence.startsWith(">")) {
            flushProse()
            val quoted = StringBuilder()
            while (index < lines.size && lines[index].trimStart().startsWith(">")) {
                val stripped = lines[index].trimStart().removePrefix(">").removePrefix(" ")
                if (quoted.isNotEmpty()) quoted.append('\n')
                quoted.append(stripped)
                index++
            }
            blocks += MarkdownBlock(quote = quoted.toString().trim('\n'))
            continue
        }

        prose.appendLine(line)
        index++
    }
    flushProse()
    return blocks
}

/** Une ligne de tableau : elle porte des « | » et n'ouvre pas un autre bloc. */
private fun isTableRow(line: String): Boolean {
    val text = line.trim()
    return text.isNotEmpty() &&
        text.contains('|') &&
        !text.startsWith("```") &&
        !text.startsWith("$$")
}

/**
 * La ligne de séparation d'un tableau (`|---|---|:--:|`) : uniquement des « | », « - », « : » et
 * des espaces, et au moins un tiret. C'est elle qui distingue un tableau d'une simple ligne de
 * prose contenant un « | ».
 */
private fun isTableDelimiter(line: String): Boolean {
    val text = line.trim()
    if (text.isEmpty() || !text.contains('|') || !text.contains('-')) return false
    return text.all { it == '|' || it == '-' || it == ':' || it == ' ' }
}

/** Les cellules d'une ligne de tableau, un « \| » échappé restant dans sa cellule. */
private fun splitTableRow(line: String): List<String> {
    var text = line.trim()
    if (text.startsWith("|")) text = text.substring(1)
    if (text.endsWith("|") && !text.endsWith("\\|")) text = text.dropLast(1)

    val cells = mutableListOf<String>()
    val current = StringBuilder()
    var index = 0
    while (index < text.length) {
        val char = text[index]
        if (char == '\\' && index + 1 < text.length && text[index + 1] == '|') {
            current.append('|')
            index += 2
            continue
        }
        if (char == '|') {
            cells += current.toString().trim()
            current.setLength(0)
            index++
            continue
        }
        current.append(char)
        index++
    }
    cells += current.toString().trim()
    return cells
}

/** `:---` à gauche, `:---:` centré, `---:` à droite, `---` par défaut. */
private fun alignmentOf(spec: String): TableAlignment {
    val text = spec.trim()
    val leading = text.startsWith(":")
    val trailing = text.endsWith(":")
    return when {
        leading && trailing -> TableAlignment.CENTER
        trailing -> TableAlignment.TRAILING
        else -> TableAlignment.LEADING
    }
}

/** Le libellé d'un bloc : son langage, en capitales, ou « code » quand la clôture n'en donne pas. */
fun codeBlockLabel(language: String?): String = (language?.takeIf { it.isNotBlank() } ?: "code").uppercase()

/**
 * Un bloc de code : son langage, « Copy », et la coloration du thème.
 *
 * Port de la partie « code » de `MarkdownView` : même fond que le `Color.primary.opacity(0.06)`
 * de la référence, même monospace (Source Code Pro), les couleurs de `SyntaxPalette` du thème actif, et
 * les mêmes marges — 10 de chaque côté, 6 au-dessus du libellé, 10 sous le code. Pas de numéros de
 * ligne : référence n'en dessine pas, et la capture de référence n'en montre pas.
 */
@Composable
fun CodeBlock(
    code: String,
    language: String?,
    modifier: Modifier = Modifier,
    /** Le dernier bloc pendant le streaming reste monochrome, comme `CodeBlock.live` sur la référence. */
    live: Boolean = false,
) {
    val theme = LocalOnyxTheme.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val palette = theme.syntaxPalette()
    var copied by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }

    val highlight = remember(code, language, palette, theme.textColor, live) {
        buildAnnotatedString {
            if (live) {
                append(code)
            } else {
                SyntaxHighlighter.tokenize(code, language).forEach { token ->
                    withStyle(SpanStyle(color = SyntaxHighlighter.colourOf(token.kind, palette, theme.textColor))) {
                        append(token.text)
                    }
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(theme.textColor.copy(alpha = 0.06f), RoundedCornerShape(OnyxSpace.ControlCorner)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                text = codeBlockLabel(language),
                style = theme.caption.copy(color = theme.secondaryText, fontFamily = FontFamily.Monospace),
                modifier = Modifier.weight(1f),
            )
            // Le bouton est nu, comme `.buttonStyle(.plain)` côté référence : pas de pastille derrière.
            Row(
                modifier = Modifier
                    .onyxClickable(pressedScale = 0.9f) {
                        scope.launch {
                            // La copie est réelle : elle va dans le presse-papiers du système.
                            clipboard.setClipEntry(ClipEntry(android.content.ClipData.newPlainText("code", code)))
                        }
                        copied = true
                    }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                OnyxIcon(
                    symbol = if (copied) Symbols.Checkmark else Symbols.DocOnDoc,
                    tint = theme.secondaryText,
                    size = 11.dp,
                )
                BasicText(
                    text = if (copied) "Copied" else "Copy",
                    style = theme.caption.copy(color = theme.secondaryText),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
        ) {
            // Le code se prend au doigt comme le reste du texte du fil : appui long → un mot, puis
            // les poignées et la barre du système. Le bouton « Copy » ci-dessus, lui, emporte tout
            // le bloc.
            SelectableText(
                text = highlight,
                style = theme.codeFont,
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
            )
        }
    }
}
