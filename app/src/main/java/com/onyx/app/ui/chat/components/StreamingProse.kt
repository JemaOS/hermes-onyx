package com.onyx.app.ui.chat.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import com.onyx.app.ui.design.SelectableText
import com.onyx.app.ui.design.inlineCodeInk
import com.onyx.app.ui.design.inlineCodeSurface
import com.onyx.app.ui.theme.LocalOnyxTheme
import com.onyx.app.ui.theme.ResolvedTheme

/**
 * **La prose qui s'écrit, en texte de Compose — jamais de `TextView` sous le doigt.**
 *
 * Mesuré sur l'émulateur (GPU hôte, réponse de douze mille caractères, un jeton toutes les 33 ms) :
 *
 * | chemin du bloc vivant | thread UI (p50) | dont enregistrement du dessin |
 * |---|---|---|
 * | Markwon dans un `AndroidView` | **17,5 ms** | **15,7 ms** |
 * | texte de Compose (ce fichier) | **1,4 ms** | 0,85 ms |
 *
 * L'analyse Markwon, elle, ne pèse que 0,5 à 1,3 ms par jeton (journal instrumenté du binaire de
 * mesure) : ce n'est pas le découpage qui coûte, c'est **le dessin d'une vue du système dont le
 * contenu change à chaque trame**, dans un arbre Compose qui doit alors réenregistrer son calque. La
 * même vue, **finie**, ne coûte rien (0,4 ms mesurés au défilement) : son *display list* est gardée.
 * Le défaut n'existe donc que pendant l'écriture — et c'est là qu'il faut un autre chemin.
 *
 * À la fin de la réponse, `MarkdownText` repasse le bloc à Markwon : son rendu complet (titres,
 * listes, cases, tableaux, liens cliquables) reste **la référence**, et il ne s'exécute alors qu'une
 * fois, mis en cache par `RowCaches.rendered`.
 *
 * Ce que ce rendu couvre — exactement ce qu'on voit arriver : **gras**, *italique*, `code en ligne`,
 * ~~barré~~, liens (soulignés, non cliquables pendant l'écriture), titres (`#`, à l'échelle du
 * thème), puces et cases à cocher. Un marqueur **non fermé** (un `**` qui attend sa fermeture)
 * s'affiche littéralement : c'est ce que fait Markwon, et le texte ne doit pas mentir sur son
 * contenu.
 */
@Composable
fun StreamingProse(text: String, tint: Color?) {
    val theme = LocalOnyxTheme.current
    val style = theme.messageFont.copy(color = tint ?: theme.textColor)
    // **Le découpage ne se refait que si le texte change.**
    //
    // `streamingTokens` relit la réponse entière, ligne par ligne, à chaque appel : sur une réponse
    // de 20 000 caractères, c'est un parcours complet. Or la rangée qui porte ce bloc se recompose
    // aussi pour d'autres raisons — l'horloge du tour qui bat, un compteur d'usage, le défilement,
    // un changement d'état voisin — et chacune repayait cette lecture pour un texte **identique**.
    // Mesuré sur l'émulateur : jusqu'à 14 ms sur le thread UI dans une trame de flux (p99 7,8 ms),
    // alors que le travail utile de la trame est sous 2 ms.
    val annotated = remember(text, style, theme) { streamingAnnotated(text, style, theme) }
    // La surcharge `AnnotatedString` de `SelectableText` ne traduit rien : elle ne reçoit que des
    // intervalles déjà posés, et le contenu d'une réponse n'est pas un libellé (voir `MarkdownText`).
    SelectableText(
        text = annotated,
        style = style,
    )
}

/** Les marques reconnues dans un bloc vivant. */
internal enum class MarkKind { BOLD, ITALIC, CODE, STRIKE, LINK, HEADING, TASK_DONE, TASK_TODO }

/** Une marque : un intervalle du texte rendu, et son genre (niveau de titre pour [MarkKind.HEADING]). */
internal data class Mark(val kind: MarkKind, val start: Int, val end: Int, val level: Int = 0)

/**
 * Le bloc vivant, découpé en **texte rendu** et **marques** — sans thème, sans Compose, sans Android.
 *
 * Pure et testée (`StreamingProseTest`) : c'est ici que se joue la fidélité du rendu, et elle se
 * vérifie sans appareil.
 */
internal fun streamingTokens(markdown: String): Pair<String, List<Mark>> {
    val text = StringBuilder(markdown.length)
    val marks = mutableListOf<Mark>()
    val lines = markdown.split('\n')
    for ((index, line) in lines.withIndex()) {
        if (index > 0) text.append('\n')
        emitLine(line, text, marks)
    }
    return text.toString() to marks
}

private fun emitLine(line: String, text: StringBuilder, marks: MutableList<Mark>) {
    val trimmed = line.trimStart()
    val indent = line.length - trimmed.length
    // ---- Les préfixes de bloc : titre, case à cocher, puce. Un par ligne, en tête. ------------
    if (trimmed.startsWith("#")) {
        val hashes = trimmed.takeWhile { it == '#' }.length
        if (hashes in 1..6 && trimmed.length > hashes && trimmed[hashes] == ' ') {
            repeat(indent) { text.append(' ') }
            val start = text.length
            emitInline(line, indent + hashes + 1, line.length, text, marks)
            marks += Mark(MarkKind.HEADING, start, text.length, hashes)
            return
        }
    }
    for ((marker, kind, glyph) in BLOCK_MARKERS) {
        if (!trimmed.startsWith(marker)) continue
        repeat(indent) { text.append(' ') }
        val start = text.length
        text.append(glyph)
        emitInline(line, indent + marker.length, line.length, text, marks)
        if (kind != null) marks += Mark(kind, start, start + glyph.length)
        return
    }
    emitInline(line, 0, line.length, text, marks)
}

/** Les préfixes de bloc, du plus long au plus court (un `- [x] ` commence par `- `). */
private val BLOCK_MARKERS: List<Triple<String, MarkKind?, String>> = listOf(
    Triple("- [ ] ", MarkKind.TASK_TODO, "☐ "),
    Triple("* [ ] ", MarkKind.TASK_TODO, "☐ "),
    Triple("- [x] ", MarkKind.TASK_DONE, "☑ "),
    Triple("* [x] ", MarkKind.TASK_DONE, "☑ "),
    Triple("- [X] ", MarkKind.TASK_DONE, "☑ "),
    Triple("* [X] ", MarkKind.TASK_DONE, "☑ "),
    Triple("- ", null, "• "),
    Triple("* ", null, "• "),
    Triple("+ ", null, "• "),
)

/**
 * Le balisage **en ligne** d'un intervalle : gras, italique, code, barré, lien.
 *
 * Un marqueur sans fermeture est écrit **tel quel** — on ne devine pas ce que l'auteur voulait dire,
 * et Markwon fait de même. Un appariement ne franchit jamais la fin de l'intervalle.
 */
private fun emitInline(
    source: String,
    from: Int,
    to: Int,
    text: StringBuilder,
    marks: MutableList<Mark>,
) {
    var index = from
    while (index < to) {
        val rest = to - index
        if (rest >= 4 && (source.startsWith("**", index) || source.startsWith("__", index))) {
            val delimiter = source.substring(index, index + 2)
            val close = source.indexOf(delimiter, index + 2)
            if (close in (index + 2) until to) {
                val start = text.length
                emitInline(source, index + 2, close, text, marks)
                marks += Mark(MarkKind.BOLD, start, text.length)
                index = close + 2
                continue
            }
        }
        if (rest >= 3 && source.startsWith("~~", index)) {
            val close = source.indexOf("~~", index + 2)
            if (close in (index + 2) until to) {
                val start = text.length
                emitInline(source, index + 2, close, text, marks)
                marks += Mark(MarkKind.STRIKE, start, text.length)
                index = close + 2
                continue
            }
        }
        if (source[index] == '`') {
            val close = source.indexOf('`', index + 1)
            if (close in (index + 1) until to) {
                val start = text.length
                // Le code ne se re-analyse pas : ce qui est entre les accents graves est littéral.
                text.append(source, index + 1, close)
                marks += Mark(MarkKind.CODE, start, text.length)
                index = close + 1
                continue
            }
        }
        if (source[index] == '[') {
            val close = source.indexOf(']', index + 1)
            if (close in (index + 1) until to && close + 1 < to && source[close + 1] == '(') {
                val end = source.indexOf(')', close + 2)
                if (end in (close + 2) until to) {
                    val start = text.length
                    emitInline(source, index + 1, close, text, marks)
                    marks += Mark(MarkKind.LINK, start, text.length)
                    index = end + 1
                    continue
                }
            }
        }
        val char = source[index]
        if ((char == '*' || char == '_') && opensEmphasis(source, index, from, to)) {
            val close = closingEmphasis(source, index, to)
            if (close > index) {
                val start = text.length
                emitInline(source, index + 1, close, text, marks)
                marks += Mark(MarkKind.ITALIC, start, text.length)
                index = close + 1
                continue
            }
        }
        text.append(char)
        index++
    }
}

/**
 * Un `*` ou un `_` ouvre-t-il un italique ? Il faut être en début de mot logique : sinon
 * `snake_case` deviendrait `snakecase` en italique au milieu d'un identifiant.
 */
private fun opensEmphasis(source: String, index: Int, from: Int, to: Int): Boolean {
    if (index + 1 >= to || source[index + 1].isWhitespace()) return false
    if (index == from) return true
    val previous = source[index - 1]
    return previous.isWhitespace() || previous in "([{\"'«“-—"
}

/** La fermeture d'un italique : le même caractère, non suivi d'un caractère de mot. */
private fun closingEmphasis(source: String, index: Int, to: Int): Int {
    val char = source[index]
    var at = source.indexOf(char, index + 1)
    while (at in (index + 1) until to) {
        val after = at + 1
        if (after >= to || !source[after].isLetterOrDigit()) return at
        at = source.indexOf(char, at + 1)
    }
    return -1
}

/** Le texte rendu, avec les styles du thème appliqués aux marques. */
internal fun streamingAnnotated(
    markdown: String,
    style: TextStyle,
    theme: ResolvedTheme,
): AnnotatedString {
    val (text, marks) = streamingTokens(markdown)
    val ink = theme.textColor
    val linkColor = theme.linkColor
    val codeInk = inlineCodeInk(theme)
    val codeSurface = inlineCodeSurface(theme)
    val baseSize = style.fontSize.value
    return buildAnnotatedString {
        append(text)
        // **Le point qui dit que ça écrit encore.** Même marque que le texte fini (` •`), mais en
        // accent : c'est ce qui distingue une réponse qui arrive d'une réponse coupée net.
        var tailStart = -1
        if (text.isNotBlank()) {
            tailStart = length
            append(" •")
        }
        marks.forEach { mark ->
            val span = when (mark.kind) {
                MarkKind.BOLD -> SpanStyle(fontWeight = FontWeight.SemiBold, color = ink)
                MarkKind.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic, color = ink)
                MarkKind.STRIKE -> SpanStyle(textDecoration = TextDecoration.LineThrough, color = ink)
                MarkKind.CODE -> SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    color = codeInk,
                    background = codeSurface,
                )
                MarkKind.LINK -> SpanStyle(
                    color = linkColor,
                    textDecoration = TextDecoration.Underline,
                )
                MarkKind.HEADING -> {
                    // Le titre garde le rapport de taille du thème, sans dépasser le corps de plus
                    // du double : en flux, un `#` doit se voir, pas sauter.
                    val factor = when (mark.level) {
                        1 -> 1.45f
                        2 -> 1.3f
                        3 -> 1.18f
                        else -> 1.08f
                    }
                    SpanStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = (baseSize * factor).sp,
                        color = ink,
                    )
                }
                MarkKind.TASK_DONE, MarkKind.TASK_TODO -> SpanStyle(color = theme.accent)
            }
            addStyle(span, mark.start, mark.end)
        }
        if (tailStart >= 0) addStyle(SpanStyle(color = theme.accent), tailStart, length)
    }
}