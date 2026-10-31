package com.onyx.app.ui.rich

import androidx.compose.ui.graphics.Color
import com.onyx.app.ui.theme.SyntaxPalette

/** La nature d'un morceau de code, pour lui donner sa couleur. */
enum class TokenKind { PLAIN, KEYWORD, STRING, COMMENT, NUMBER, TYPE, ATTRIBUTE, KEY }

data class CodeToken(val text: String, val kind: TokenKind)

/**
 * Le surligneur de syntaxe des blocs de code.
 *
 * Équivalent Android de `SyntaxHighlighter` : mêmes familles de langages, mêmes couleurs —
 * celles que le thème porte déjà (`SyntaxPalette`). Assez simple pour rester juste : commentaires,
 * chaînes, nombres, mots-clés, types.
 */
object SyntaxHighlighter {

    private val kotlinKeywords = setOf(
        "package", "import", "class", "interface", "object", "fun", "val", "var", "if", "else",
        "when", "for", "while", "do", "return", "break", "continue", "try", "catch", "finally",
        "throw", "is", "in", "as", "null", "true", "false", "this", "super", "override", "private",
        "public", "internal", "protected", "data", "sealed", "enum", "companion", "suspend", "lateinit",
        "const", "typealias", "by", "where", "init", "constructor", "operator", "inline", "reified",
    )

    private val pythonKeywords = setOf(
        "def", "class", "return", "if", "elif", "else", "for", "while", "import", "from", "as",
        "with", "try", "except", "finally", "raise", "pass", "None", "True", "False", "and", "or",
        "not", "in", "is", "lambda", "yield", "global", "nonlocal", "assert", "del", "async", "await",
    )

    private val jsKeywords = setOf(
        "function", "const", "let", "var", "return", "if", "else", "for", "while", "do", "switch",
        "case", "break", "continue", "new", "class", "extends", "import", "export", "from", "default",
        "async", "await", "try", "catch", "finally", "throw", "null", "undefined", "true", "false",
        "this", "typeof", "instanceof", "in", "of", "yield",
    )

    private val shellKeywords = setOf(
        "if", "then", "else", "elif", "fi", "for", "in", "do", "done", "while", "case", "esac",
        "function", "return", "export", "local", "echo", "cd", "set", "source", "exit",
    )

    private val sqlKeywords = setOf(
        "select", "from", "where", "insert", "into", "values", "update", "set", "delete", "create",
        "table", "drop", "alter", "join", "left", "right", "inner", "outer", "on", "group", "by",
        "order", "having", "limit", "offset", "as", "and", "or", "not", "null", "primary", "key",
        "foreign", "references", "index", "distinct", "union", "case", "when", "then", "else", "end",
    )

    private val jsonKeywords = setOf("true", "false", "null")

    private val rustKeywords = setOf(
        "fn", "let", "mut", "pub", "use", "mod", "struct", "enum", "impl", "trait", "for", "in",
        "while", "loop", "if", "else", "match", "return", "self", "Self", "super", "crate", "as",
        "ref", "where", "async", "await", "move", "dyn", "const", "static", "unsafe", "type", "true",
        "false", "Some", "None", "Ok", "Err", "break", "continue",
    )

    private val goKeywords = setOf(
        "package", "import", "func", "return", "var", "const", "type", "struct", "interface", "map",
        "chan", "go", "defer", "if", "else", "for", "range", "switch", "case", "default", "break",
        "continue", "select", "fallthrough", "goto", "nil", "true", "false", "make", "new", "len",
        "cap", "append", "error", "string", "int", "int64", "bool", "byte", "float64",
    )

    private val markupLanguages = setOf("html", "xml", "svg", "vue", "svelte")

    private fun keywordsFor(language: String?): Set<String>? = when (language?.lowercase()) {
        "kotlin", "kt", "kts" -> kotlinKeywords
        "python", "py" -> pythonKeywords
        "javascript", "js", "typescript", "ts", "tsx", "jsx" -> jsKeywords
        "json", "jsonc" -> jsonKeywords
        "sh", "bash", "zsh", "shell", "console" -> shellKeywords
        "sql", "psql", "mysql", "sqlite" -> sqlKeywords
        "rust", "rs" -> rustKeywords
        "go", "golang" -> goKeywords
        in markupLanguages -> emptySet()
        else -> null
    }

    private fun isMarkup(language: String?): Boolean = language?.lowercase() in markupLanguages

    /** La couleur d'un morceau, dans la palette du thème. */
    fun colourOf(kind: TokenKind, palette: SyntaxPalette, plain: Color): Color = when (kind) {
        TokenKind.KEYWORD -> palette.keyword
        TokenKind.STRING -> palette.string
        TokenKind.COMMENT -> palette.comment
        TokenKind.NUMBER -> palette.number
        TokenKind.TYPE -> palette.type
        TokenKind.ATTRIBUTE -> palette.attribute
        TokenKind.KEY -> palette.key
        TokenKind.PLAIN -> plain
    }

    /** Découpe un texte en morceaux colorés ; un langage inconnu reste d'une seule couleur. */
    fun tokenize(code: String, language: String?): List<CodeToken> {
        val keywords = keywordsFor(language) ?: return listOf(CodeToken(code, TokenKind.PLAIN))
        val tokens = mutableListOf<CodeToken>()
        var i = 0
        val buffer = StringBuilder()

        fun flush() {
            if (buffer.isNotEmpty()) {
                tokens += CodeToken(buffer.toString(), TokenKind.PLAIN)
                buffer.setLength(0)
            }
        }

        while (i < code.length) {
            val rest = code.substring(i)
            val hashComment = rest.startsWith("#") && keywords !== sqlKeywords &&
                (i == 0 || !(code[i - 1].isLetterOrDigit() || code[i - 1] == '_'))
            when {
                rest.startsWith("//") || hashComment -> {
                    val end = code.indexOf('\n', i).let { if (it < 0) code.length else it }
                    flush()
                    tokens += CodeToken(code.substring(i, end), TokenKind.COMMENT)
                    i = end
                }

                rest.startsWith("<!--") && isMarkup(language) -> {
                    val end = code.indexOf("-->", i).let { if (it < 0) code.length else it + 3 }
                    flush()
                    tokens += CodeToken(code.substring(i, end), TokenKind.COMMENT)
                    i = end
                }

                rest.startsWith("/*") -> {
                    val end = code.indexOf("*/", i).let { if (it < 0) code.length else it + 2 }
                    flush()
                    tokens += CodeToken(code.substring(i, end), TokenKind.COMMENT)
                    i = end
                }

                isMarkup(language) && code[i] == '<' && i + 1 < code.length &&
                    (code[i + 1].isLetter() || code[i + 1] == '/') -> {
                    var j = i + 1
                    if (code[j] == '/') j++
                    while (j < code.length &&
                        (code[j].isLetterOrDigit() || code[j] == '_' || code[j] == '-' || code[j] == ':')
                    ) j++
                    flush()
                    tokens += CodeToken(code.substring(i, j), TokenKind.KEYWORD)
                    i = j
                }

                rest.startsWith("\"\"\"") -> {
                    val end = code.indexOf("\"\"\"", i + 3).let { if (it < 0) code.length else it + 3 }
                    flush()
                    tokens += CodeToken(code.substring(i, end), TokenKind.STRING)
                    i = end
                }

                code[i] == '"' || code[i] == '\'' || code[i] == '`' -> {
                    val quote = code[i]
                    var j = i + 1
                    while (j < code.length && code[j] != quote) {
                        if (code[j] == '\\') j++
                        j++
                    }
                    j = minOf(j + 1, code.length)
                    flush()
                    var after = j
                    while (after < code.length && code[after] == ' ') after++
                    val isJsonKey = language?.lowercase() in setOf("json", "jsonc") &&
                        after < code.length && code[after] == ':'
                    tokens += CodeToken(
                        code.substring(i, j),
                        if (isJsonKey) TokenKind.KEY else TokenKind.STRING,
                    )
                    i = j
                }

                code[i].isDigit() -> {
                    var j = i
                    while (j < code.length && (code[j].isLetterOrDigit() || code[j] == '.' || code[j] == '_')) j++
                    flush()
                    tokens += CodeToken(code.substring(i, j), TokenKind.NUMBER)
                    i = j
                }

                code[i].isLetter() || code[i] == '_' -> {
                    var j = i
                    while (j < code.length && (code[j].isLetterOrDigit() || code[j] == '_')) j++
                    val word = code.substring(i, j)
                    var afterWord = j
                    while (afterWord < code.length && code[afterWord] == ' ') afterWord++
                    val markupAttribute = isMarkup(language) && i > 0 &&
                        (code[i - 1] == ' ' || code[i - 1] == '\n') &&
                        afterWord < code.length && code[afterWord] == '='
                    when {
                        word in keywords -> {
                            flush()
                            tokens += CodeToken(word, TokenKind.KEYWORD)
                        }

                        markupAttribute -> {
                            flush()
                            tokens += CodeToken(word, TokenKind.ATTRIBUTE)
                        }

                        word.first().isUpperCase() && !isMarkup(language) &&
                            language?.lowercase() !in setOf("sh", "bash", "zsh", "sql") -> {
                            flush()
                            tokens += CodeToken(word, TokenKind.TYPE)
                        }

                        else -> buffer.append(word)
                    }
                    i = j
                }

                else -> {
                    buffer.append(code[i])
                    i++
                }
            }
        }
        flush()
        return tokens
    }
}
