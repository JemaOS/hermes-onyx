package com.onyx.app.ui.chat.components

import com.onyx.app.ui.design.OnyxSpace

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.net.Uri
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ForegroundColorSpan
import android.text.style.ReplacementSpan
import android.text.style.StrikethroughSpan
import android.text.util.Linkify
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.onyx.app.ui.design.SelectableText
import com.onyx.app.ui.localization.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.onyx.app.ui.rich.CodeBlock
import com.onyx.app.ui.rich.MarkdownBlock
import com.onyx.app.ui.rich.MarkdownRenderer
import com.onyx.app.ui.rich.MarkdownTable
import com.onyx.app.ui.rich.MathBlock
import com.onyx.app.ui.rich.MermaidBlock
import com.onyx.app.ui.rich.TableAlignment
import com.onyx.app.ui.rich.renderer
import com.onyx.app.ui.rich.splitMarkdown
import com.onyx.app.ui.theme.LocalOnyxTheme
import com.onyx.app.ui.theme.OnyxFonts
import com.onyx.app.ui.theme.ResolvedTheme
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.LinkResolver
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.linkify.LinkifyPlugin

/**
 * Le rendu Markdown des réponses (`MarkdownView`).
 *
 * La réponse est découpée à chaque révision, y compris pendant le streaming : les blocs déjà
 * terminés gardent leur vrai rendu. Seul le dernier diagramme, formule ou code reste temporairement
 * dans une fenêtre de code monochrome, comme `MarkdownBlockView.trailing` sur la référence. La prose passe
 * par Markwon (titres, listes, cases, liens), le code par [CodeBlock], les citations et tableaux
 * par Compose, et les diagrammes/formules par [MermaidBlock] et [MathBlock].
 */
@Composable
fun MarkdownText(
    markdown: String,
    isStreaming: Boolean,
    modifier: Modifier = Modifier,
    /**
     * Couleur forcée de la prose. Les citations s'en servent pour rendre leur contenu en
     * secondaire ; partout ailleurs la couleur du thème s'applique.
     */
    tint: Color? = null,
) {
    // Un échec du découpage ne doit jamais laisser la réponse vide : on retombe sur le texte brut.
    val blocks = remember(markdown) {
        runCatching { splitMarkdown(markdown) }
            .getOrElse { listOf(MarkdownBlock(prose = markdown)) }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        blocks.forEachIndexed { index, block ->
            val trailing = isStreaming && index == blocks.lastIndex
            when (block.renderer(trailing)) {
                MarkdownRenderer.MATH -> MathBlock(source = block.math.orEmpty())
                MarkdownRenderer.AGENT_PAGE -> com.onyx.app.ui.rich.AgentPageBlock(html = block.code.orEmpty())
                MarkdownRenderer.MERMAID -> MermaidBlock(source = block.code.orEmpty())
                MarkdownRenderer.CODE -> CodeBlock(code = block.code.orEmpty(), language = block.language)
                MarkdownRenderer.LIVE_CODE -> CodeBlock(
                    code = block.code ?: block.math.orEmpty(),
                    language = block.language ?: "math",
                    live = true,
                )
                MarkdownRenderer.QUOTE -> MarkdownQuote(markdown = block.quote.orEmpty())
                MarkdownRenderer.TABLE -> block.table?.let { MarkdownTableBlock(table = it) }
                MarkdownRenderer.PROSE -> MarkdownProse(markdown = block.prose.orEmpty(), tint = tint, streaming = trailing)
            }
        }
    }
}

/** Un morceau de prose, rendu par Markwon avec les couleurs et les polices du thème. */
@Composable
private fun MarkdownProse(markdown: String, tint: Color? = null, streaming: Boolean = false) {
    val theme = LocalOnyxTheme.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val bodyTypeface = remember(theme.id, theme.dark) { theme.bodyTypeface(context) }
    val textColor = (tint ?: theme.textColor).toArgb()
    // Le code **en ligne** et les blocs : une teinte **dérivée du thème** (surface surélevée) et une
    // encre **mesurée** contre elle. Jamais un gris clair hérité, jamais une pastille claire sur un
    // thème sombre (`InlineSurfaces`).
    val codeText = com.onyx.app.ui.design.inlineCodeInk(theme).toArgb()
    val codeBackground = com.onyx.app.ui.design.inlineCodeSurface(theme).toArgb()
    val linkColor = theme.linkColor.toArgb()
    val quoteColor = theme.quoteBarColor.toArgb()
    val checkboxChecked = theme.checkboxColor.toArgb()
    val checkboxOutline = theme.secondaryText.toArgb()
    val secondaryColor = theme.secondaryText.toArgb()
    val tickColor = android.graphics.Color.WHITE
    val streamingDotColor = theme.accent.toArgb()

    // Les tailles relatives des titres, pour que Markwon les rende aux tailles de `headingFont`
    // du thème — et surtout pour que le titre de niveau 1 ne s'affiche pas énorme.
    val headingMultipliers = remember(theme.id, theme.dark) { headingSizeMultipliers(theme) }
    // La barre de citation et le corps, en pixels, comme Markwon les attend.
    val blockQuoteWidth = with(density) { 3.dp.roundToPx() }
    val baseTextSize = with(density) { theme.messageFont.fontSize.toPx() }

    // `getOrNull` : si Markwon refuse de se construire, la prose repart en texte brut (voir plus bas).
    val markwon = remember(
        textColor,
        codeText,
        codeBackground,
        linkColor,
        quoteColor,
        bodyTypeface,
        checkboxChecked,
        checkboxOutline,
        blockQuoteWidth,
        baseTextSize,
        headingMultipliers,
    ) {
        runCatching {
            Markwon.builder(context)
                .usePlugin(StrikethroughPlugin.create())
                .usePlugin(TablePlugin.create(context))
                .usePlugin(LinkifyPlugin.create(Linkify.WEB_URLS))
                .usePlugin(object : AbstractMarkwonPlugin() {
                    override fun configureTheme(builder: MarkwonTheme.Builder) {
                        builder
                            .codeTextColor(codeText)
                            .codeBackgroundColor(codeBackground)
                            .codeTypeface(Typeface.MONOSPACE)
                            .linkColor(linkColor)
                            // Les liens sont soulignés, comme `underlineStyle = .single` côté référence.
                            .isLinkUnderlined(true)
                            .blockQuoteColor(quoteColor)
                            .blockQuoteWidth(blockQuoteWidth)
                            // Pas de trait sous les titres : la référence n'en dessine pas non plus.
                            .headingBreakHeight(0)
                            .headingTextSizeMultipliers(headingMultipliers)
                    }

                    override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                        // Un tap sur un lien ouvre le navigateur, comme le fait référence.
                        builder.linkResolver(LinkResolver { view, link -> openLink(view, link) })
                    }

                    override fun beforeSetText(textView: TextView, markdown: Spanned) {
                        // `- [ ]` / `- [x]` : Markwon n'a pas d'extension de liste de tâches, on
                        // remplace les crochets qu'il laisse dans le texte par de vraies cases, et
                        // une tâche cochée prend le libellé barré en secondaire.
                        applyTaskCheckboxes(markdown, checkboxChecked, checkboxOutline, tickColor, secondaryColor)
                    }
                })
                .build()
        }.getOrNull()
    }

    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { ctx ->
            TextView(ctx).apply {
                setTextColor(textColor)
                typeface = bodyTypeface
                setTextSize(TypedValue.COMPLEX_UNIT_PX, baseTextSize)
                setLineSpacing(0f, 1.18f)
                // La prose se prend au doigt comme n'importe quel texte Android : appui long → un
                // mot, deux poignées pour étendre, barre du système (Couper / Copier / Coller /
                // Tout sélectionner). L'ordre compte : `setTextIsSelectable` repose un
                // `MovementMethod` de clavier, on remet donc `LinkMovementMethod` après pour que
                // les liens restent cliquables.
                // Le texte prend le focus au toucher : c'est l'exigence du système pour montrer la
                // sélection et sa barre sur un texte non modifiable. C'est aussi ce que fait la
                // référence, où toucher un texte le rend premier répondant.
                setTextIsSelectable(true)
                movementMethod = LinkMovementMethod.getInstance()
                isVerticalScrollBarEnabled = false
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
        },
        update = { view ->
            view.setTextColor(textColor)
            view.typeface = bodyTypeface
            view.setTextSize(TypedValue.COMPLEX_UNIT_PX, baseTextSize)
            val renderer = markwon
            if (renderer == null) {
                // Repli : le texte brut plutôt que rien.
                view.text = markdown
            } else {
                runCatching {
                    // Une case remplace le marqueur de sa liste : Markwon n'a pas de liste de
                    // tâches, et sa puce n'existe pas sur la référence.
                    renderer.setMarkdown(view, demoteTaskListMarkers(markdown))
                    if (streaming) {
                        // Markwon garde le gras, les listes et les cases pendant l'arrivée ; le
                        // point d'accent remplace le curseur de `streamingTail` sans afficher le
                        // Markdown brut à la place du contenu mis en forme.
                        val content = SpannableStringBuilder(view.text)
                        val dot = content.length + 1
                        content.append(" •")
                        content.setSpan(
                            ForegroundColorSpan(streamingDotColor),
                            dot,
                            content.length,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                        )
                        view.text = content
                    }
                }.onFailure { view.text = markdown }
            }
        },
    )
}

/**
 * Les tailles relatives des titres : le rapport entre chaque taille de `headingFont` et celle du
 * corps. Markwon multiplie la taille du texte par ces nombres, si bien que les titres suivent le
 * thème au lieu des tailles HTML par défaut (2× pour un titre 1, ce qui était trop gros).
 */
private fun headingSizeMultipliers(theme: ResolvedTheme): FloatArray {
    val base = theme.messageFont.fontSize.value.coerceAtLeast(1f)
    return FloatArray(6) { index -> theme.headingFont(index + 1).fontSize.value / base }
}

/** Un tap sur un lien ouvre le navigateur : `Intent.ACTION_VIEW`, comme `OpenURL` côté référence. */
private fun openLink(view: View, link: String) {
    runCatching {
        val uri = Uri.parse(link)
        val data = if (uri.scheme.isNullOrEmpty()) Uri.parse("https://$link") else uri
        view.context.startActivity(Intent(Intent.ACTION_VIEW, data))
    }
}

/** L'état d'une case `[ ]` / `[x]` à cet index, ou `null` si ce n'est pas une case. */
internal fun taskMarkerAt(text: String, index: Int): Boolean? {
    if (index < 0 || index + 2 >= text.length || text[index] != '[' || text[index + 2] != ']') return null
    return when (text[index + 1]) {
        ' ' -> false
        'x', 'X' -> true
        else -> null
    }
}

/**
 * La fin de la ligne qui porte le marqueur à cet index : le saut de ligne, ou la fin du texte.
 *
 * Sert à barrer le libellé d'une tâche cochée et à lui donner la couleur secondaire, comme
 * `MarkdownBlockView` le fait côté référence sur tout le texte de l'élément.
 */
internal fun taskLineEnd(text: String, index: Int): Int {
    val end = text.indexOf('\n', index + 1)
    return if (end < 0) text.length else end
}

/**
 * Sort les éléments de liste de tâches de leur liste.
 *
 * Markwon dessine une puce devant chaque élément de liste et référence n'en a pas : `MarkdownBlockView`
 * montre la case *à la place* du marqueur (`MarkdownView:186`). Une case reste donc un
 * paragraphe dont le premier caractère est « [ ] » — la case la remplace —, terminé par une
 * coupure dure (deux espaces) pour que chaque tâche garde sa ligne.
 *
 * L'indentation éventuelle devient des espaces insécables : gardée en espaces ordinaires, quatre
 * d'entre eux feraient de l'élément imbriqué un bloc de code.
 */
internal fun demoteTaskListMarkers(markdown: String): String =
    markdown.lineSequence().joinToString("\n") { line ->
        val match = TASK_LIST_ITEM.matchEntire(line)
        if (match == null) {
            line
        } else {
            val indent = match.groupValues[1].map { space -> if (space == '\t') "\t" else "\u00A0" }.joinToString("")
            indent + match.groupValues[2] + HARD_BREAK
        }
    }

/** Une coupure dure en Markdown : la ligne suivante reste une ligne, sans nouveau paragraphe. */
private const val HARD_BREAK = "  "

/** `- [x] …`, `* [ ] …`, `+ [X] …` : un élément de liste dont le contenu est une tâche. */
private val TASK_LIST_ITEM = Regex("""^([ \t]*)[-*+] +(\[[ xX]\](?:[ \t].*)?)$""")

/**
 * Transforme les « [ ] » et « [x] » que laisse une liste de tâches en cases dessinées, dans la
 * couleur du thème. Elles ne se cochent pas : l'état vient du Markdown, comme sur la référence.
 *
 * Une tâche cochée voit son libellé barré et mis en secondaire — `strikethrough(checked)` et
 * `foregroundStyle(.secondary)` de `MarkdownBlockView` (Echo/Views/MarkdownView:186).
 */
private fun applyTaskCheckboxes(text: Spanned, checked: Int, outline: Int, tick: Int, secondary: Int) {
    if (text !is Spannable) return
    val raw = text.toString()
    var i = 0
    while (i <= raw.length - 3) {
        val isChecked = taskMarkerAt(raw, i)
        if (isChecked != null) {
            text.setSpan(
                TaskCheckboxSpan(checked = isChecked, checkedColor = checked, outlineColor = outline, tickColor = tick),
                i, i + 3, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            if (isChecked) {
                val lineEnd = taskLineEnd(raw, i + 3)
                if (lineEnd > i + 3) {
                    text.setSpan(StrikethroughSpan(), i + 3, lineEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    text.setSpan(
                        ForegroundColorSpan(secondary),
                        i + 3,
                        lineEnd,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                }
            }
            i += 3
            continue
        }
        i++
    }
}

/**
 * Une case de tâche dessinée à la main : vide et contourée, ou pleine dans la couleur du thème
 * avec un crochet. C'est un [ReplacementSpan], donc les caractères « [x] » ne sont pas dessinés —
 * la case prend leur place et l'alignement de la liste est conservé.
 */
private class TaskCheckboxSpan(
    private val checked: Boolean,
    private val checkedColor: Int,
    private val outlineColor: Int,
    private val tickColor: Int,
) : ReplacementSpan() {
    private val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val checkPath = Path()

    /** Le côté de la case, proportionnel à la taille du texte pour rester aligné sur la ligne. */
    private fun side(textSize: Float): Float = (textSize * 1.02f).coerceAtLeast(9f)

    override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        val box = side(paint.textSize)
        return (box + paint.textSize * 0.4f).toInt()
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence?,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint,
    ) {
        val box = side(paint.textSize)
        val left = x
        val boxTop = y - box
        val radius = box * 0.24f
        if (checked) {
            shapePaint.style = Paint.Style.FILL
            shapePaint.color = checkedColor
            canvas.drawRoundRect(left, boxTop, left + box, boxTop + box, radius, radius, shapePaint)

            shapePaint.style = Paint.Style.STROKE
            shapePaint.color = tickColor
            shapePaint.strokeWidth = (box * 0.16f).coerceAtLeast(1.2f)
            shapePaint.strokeCap = Paint.Cap.ROUND
            shapePaint.strokeJoin = Paint.Join.ROUND
            checkPath.reset()
            checkPath.moveTo(left + box * 0.24f, boxTop + box * 0.52f)
            checkPath.lineTo(left + box * 0.43f, boxTop + box * 0.71f)
            checkPath.lineTo(left + box * 0.77f, boxTop + box * 0.30f)
            canvas.drawPath(checkPath, shapePaint)
        } else {
            shapePaint.style = Paint.Style.STROKE
            shapePaint.color = outlineColor
            shapePaint.strokeWidth = (box * 0.1f).coerceAtLeast(1.2f)
            canvas.drawRoundRect(left, boxTop, left + box, boxTop + box, radius, radius, shapePaint)
        }
    }
}

/**
 * Une citation : une barre de 3 dp à gauche dans `quoteBarColor`, le contenu en secondaire.
 * Le contenu est re-rendu par [MarkdownText], donc les citations imbriquées et les blocs de code
 * qu'elles contiennent suivent.
 */
@Composable
private fun MarkdownQuote(markdown: String, modifier: Modifier = Modifier) {
    val theme = LocalOnyxTheme.current
    val barWidth = 3.dp
    val barColor = theme.quoteBarColor
    Box(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                val width = barWidth.toPx()
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset.Zero,
                    size = Size(width, size.height),
                    cornerRadius = CornerRadius(width / 2f, width / 2f),
                )
            }
            .padding(start = barWidth + 10.dp),
    ) {
        MarkdownText(markdown = markdown, isStreaming = false, tint = theme.secondaryText)
    }
}

/**
 * Un tableau GFM : en-tête teinté, bordures fines, cellules en monospace quand le thème le
 * demande, et défilement horizontal au-delà de quatre colonnes — comme `MarkdownTable` sur la référence.
 */
@Composable
private fun MarkdownTableBlock(table: MarkdownTable, modifier: Modifier = Modifier) {
    val theme = LocalOnyxTheme.current
    val columns = table.header.size
    if (columns <= 0) return
    val scrolls = columns > 4
    val shape = RoundedCornerShape(OnyxSpace.ControlCorner)
    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(theme.textColor.copy(alpha = 0.04f))
            .border(0.5.dp, theme.separator, shape),
    ) {
        Column(
            modifier = if (scrolls) Modifier.horizontalScroll(scrollState) else Modifier.fillMaxWidth(),
        ) {
            MarkdownTableRow(
                cells = table.header,
                alignments = table.alignments,
                columns = columns,
                scrolls = scrolls,
                header = true,
                lastRow = table.rows.isEmpty(),
                theme = theme,
            )
            table.rows.forEachIndexed { index, cells ->
                MarkdownTableRow(
                    cells = cells,
                    alignments = table.alignments,
                    columns = columns,
                    scrolls = scrolls,
                    header = false,
                    lastRow = index == table.rows.lastIndex,
                    striped = index % 2 == 1,
                    theme = theme,
                )
            }
        }
    }
}

@Composable
private fun MarkdownTableRow(
    cells: List<String>,
    alignments: List<TableAlignment>,
    columns: Int,
    scrolls: Boolean,
    header: Boolean,
    lastRow: Boolean,
    theme: ResolvedTheme,
    striped: Boolean = false,
) {
    val rowBackground = when {
        header -> theme.tableHeaderTint
        striped -> theme.textColor.copy(alpha = 0.03f)
        else -> Color.Transparent
    }
    Row(
        modifier = Modifier
            .then(if (scrolls) Modifier else Modifier.fillMaxWidth())
            .background(rowBackground),
        verticalAlignment = Alignment.Top,
    ) {
        for (index in 0 until columns) {
            MarkdownTableCell(
                text = cells.getOrElse(index) { "" },
                header = header,
                alignment = alignments.getOrElse(index) { TableAlignment.LEADING },
                scrolls = scrolls,
                lastColumn = index == columns - 1,
                lastRow = lastRow,
                theme = theme,
            )
        }
    }
}

/** Une cellule : elle partage la largeur, ou se fixe à 150 dp quand le tableau défile. */
@Composable
private fun RowScope.MarkdownTableCell(
    text: String,
    header: Boolean,
    alignment: TableAlignment,
    scrolls: Boolean,
    lastColumn: Boolean,
    lastRow: Boolean,
    theme: ResolvedTheme,
) {
    val hairline = 0.5.dp
    val borderColor = theme.separator
    Box(
        modifier = Modifier
            .then(if (scrolls) Modifier.width(150.dp) else Modifier.weight(1f))
            .drawBehind {
                val stroke = hairline.toPx()
                if (!lastColumn) {
                    drawLine(borderColor, Offset(size.width - stroke, 0f), Offset(size.width - stroke, size.height), stroke)
                }
                if (!lastRow) {
                    drawLine(borderColor, Offset(0f, size.height - stroke), Offset(size.width, size.height - stroke), stroke)
                }
            }
            .padding(horizontal = 10.dp, vertical = if (header) 7.dp else 6.dp),
    ) {
        SelectableText(
            text = inlinePlain(text),
            localize = false,
            // `tableFont` est déjà monospace quand le thème le demande (`monoTables`).
            style = theme.tableFont.copy(
                color = theme.textColor,
                fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = alignment.toTextAlign(),
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun TableAlignment.toTextAlign(): TextAlign = when (this) {
    TableAlignment.LEADING -> TextAlign.Start
    TableAlignment.CENTER -> TextAlign.Center
    TableAlignment.TRAILING -> TextAlign.End
}

/**
 * Le texte d'une cellule, débarrassé de ses marques inline. la référence stylise la cellule inline ; ici
 * on l'allège — la cellule reste du texte, mais lisible (« **a** » devient « a »).
 */
private fun inlinePlain(text: String): String = text
    .replace(Regex("\\[([^\\]]+)\\]\\([^)]*\\)"), "\$1")
    .replace("`", "")
    .replace("**", "")
    .replace("__", "")
    .replace("~~", "")
    .replace(Regex("(?<![\\w*])\\*(?!\\s)([^*]+)\\*(?![\\w*])"), "\$1")
    .replace(Regex("(?<![\\w_])_(?!\\s)([^_]+)_(?![\\w_])"), "\$1")
    .trim()

/** La police de corps du thème, telle qu'Android la connaît. */
fun com.onyx.app.ui.theme.ResolvedTheme.bodyTypeface(context: android.content.Context): Typeface {
    val name = palette.monoFontName ?: palette.bodyFontName
    return when {
        name != null -> runCatching {
            Typeface.createFromAsset(
                context.assets,
                if (name.contains("SourceCode")) OnyxFonts.SOURCE_CODE_MEDIUM else OnyxFonts.INTER_REGULAR,
            )
        }.getOrElse { Typeface.SANS_SERIF }

        palette.replyDesign == com.onyx.app.ui.theme.FontDesign.MONOSPACED -> Typeface.MONOSPACE
        palette.replyDesign == com.onyx.app.ui.theme.FontDesign.SERIF -> Typeface.SERIF
        else -> Typeface.SANS_SERIF
    }
}
