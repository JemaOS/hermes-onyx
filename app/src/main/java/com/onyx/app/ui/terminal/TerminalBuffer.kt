package com.onyx.app.ui.terminal

/** Une cellule de l'écran : son caractère et son style. */
data class TerminalCell(val char: Char = ' ', val style: TerminalStyle = TerminalStyle())

/** Le peu de style qu'un terminal utilise au quotidien. */
data class TerminalStyle(
    val bold: Boolean = false,
    val dim: Boolean = false,
    val inverse: Boolean = false,
    val foreground: Int = -1,
    val background: Int = -1,
)

/**
 * Un écran de terminal : une grille de cellules que les séquences ANSI déplacent.
 *
 * SwiftTerm en implémente bien davantage (souris, presse-papiers, sixel, modes privés). Ce tampon
 * couvre ce qui se voit à l'usage : retour chariot, saut de ligne, effacement, déplacement du
 * curseur, et les couleurs SGR que `git`, `ls` et `systemctl` emploient.
 */
class TerminalBuffer(
    val rows: Int = 40,
    val columns: Int = 120,
) {
    private val grid = Array(rows) { Array(columns) { TerminalCell() } }

    var cursorRow: Int = 0
        private set
    var cursorColumn: Int = 0
        private set

    private var style = TerminalStyle()
    private var savedRow = 0
    private var savedColumn = 0

    /** L'état du parseur : texte ordinaire, ou séquence en cours. */
    private val pending = StringBuilder()
    private var inEscape = false

    /** La version du contenu, pour que Compose redessine quand quelque chose change. */
    var version: Int = 0
        private set

    fun cell(row: Int, column: Int): TerminalCell =
        grid.getOrNull(row)?.getOrNull(column) ?: TerminalCell()

    /** Les lignes visibles, du haut vers le bas, espaces de fin retirés. */
    fun lines(): List<String> =
        grid.map { row -> row.map { it.char }.joinToString("").trimEnd() }

    /** Le texte complet, pour « Copier ». */
    fun text(): String = lines().joinToString("\n").trimEnd()

    /**
     * Ajoute la sortie reçue. Les octets arrivent en morceaux au milieu d'une séquence : on garde
     * l'état d'un appel à l'autre.
     */
    fun write(chunk: String) {
        var changed = false
        for (char in chunk) {
            changed = true
            when {
                inEscape -> {
                    pending.append(char)
                    // Une séquence CSI finit par une lettre ; les autres par n'importe quoi.
                    if (pending.length >= 2 && char.isLetter()) {
                        applySequence(pending.toString())
                        pending.setLength(0)
                        inEscape = false
                    } else if (pending.length > 32) {
                        pending.setLength(0)
                        inEscape = false
                    }
                }

                char == '\u001B' -> {
                    inEscape = true
                    pending.setLength(0)
                    pending.append(char)
                }

                char == '\r' -> cursorColumn = 0
                char == '\n' -> lineFeed()
                char == '\b' -> cursorColumn = (cursorColumn - 1).coerceAtLeast(0)
                char == '\t' -> cursorColumn = ((cursorColumn / 8) + 1) * 8
                char.code < 32 -> Unit
                else -> {
                    if (cursorColumn >= columns) {
                        cursorColumn = 0
                        lineFeed()
                    }
                    grid[cursorRow][cursorColumn] = TerminalCell(char, style)
                    cursorColumn++
                }
            }
        }
        if (changed) version++
    }

    fun clear() {
        for (row in 0 until rows) for (column in 0 until columns) grid[row][column] = TerminalCell()
        cursorRow = 0
        cursorColumn = 0
        version++
    }

    private fun lineFeed() {
        if (cursorRow == rows - 1) {
            // Une ligne de plus : on remonte tout d'un cran, comme un terminal qui défile.
            for (row in 1 until rows) grid[row - 1] = grid[row]
            grid[rows - 1] = Array(columns) { TerminalCell() }
        } else {
            cursorRow++
        }
    }

    private fun applySequence(sequence: String) {
        // `ESC[` : contrôle ; `ESC]` : chaîne du système d'exploitation (titre), ignorée.
        if (!sequence.startsWith("\u001B[")) return
        val body = sequence.removePrefix("\u001B[").dropLast(1)
        val final = sequence.last()
        val isPrivate = body.startsWith("?") || body.startsWith(">")
        val numbers = body.trimStart('?', '>', '<', '=')
            .split(';')
            .map { it.toIntOrNull() ?: 0 }

        fun count(default: Int): Int = numbers.firstOrNull()?.takeIf { it > 0 } ?: default

        if (isPrivate) return
        when (final) {
            'A' -> cursorRow = (cursorRow - count(1)).coerceAtLeast(0)
            'B' -> cursorRow = (cursorRow + count(1)).coerceAtMost(rows - 1)
            'C' -> cursorColumn = (cursorColumn + count(1)).coerceAtMost(columns - 1)
            'D' -> cursorColumn = (cursorColumn - count(1)).coerceAtLeast(0)
            'H', 'f' -> {
                cursorRow = ((numbers.getOrNull(0)?.takeIf { it > 0 } ?: 1) - 1).coerceIn(0, rows - 1)
                cursorColumn = ((numbers.getOrNull(1)?.takeIf { it > 0 } ?: 1) - 1).coerceIn(0, columns - 1)
            }
            'J' -> clearFrom(numbers.firstOrNull() ?: 0)
            'K' -> eraseLine(numbers.firstOrNull() ?: 0)
            'm' -> applySgr(numbers)
            's' -> {
                savedRow = cursorRow
                savedColumn = cursorColumn
            }
            'u' -> {
                cursorRow = savedRow.coerceIn(0, rows - 1)
                cursorColumn = savedColumn.coerceIn(0, columns - 1)
            }
            else -> Unit
        }
    }

    private fun clearFrom(mode: Int) {
        when (mode) {
            0 -> {
                eraseLine(0)
                for (row in cursorRow + 1 until rows) grid[row] = Array(columns) { TerminalCell() }
            }
            1 -> {
                for (row in 0 until cursorRow) grid[row] = Array(columns) { TerminalCell() }
                eraseLine(1)
            }
            else -> for (row in 0 until rows) grid[row] = Array(columns) { TerminalCell() }
        }
    }

    private fun eraseLine(mode: Int) {
        when (mode) {
            0 -> for (column in cursorColumn until columns) grid[cursorRow][column] = TerminalCell()
            1 -> for (column in 0..cursorColumn.coerceAtMost(columns - 1)) grid[cursorRow][column] = TerminalCell()
            else -> grid[cursorRow] = Array(columns) { TerminalCell() }
        }
    }

    private fun applySgr(numbers: List<Int>) {
        if (numbers.isEmpty()) {
            style = TerminalStyle()
            return
        }
        var index = 0
        while (index < numbers.size) {
            when (val code = numbers[index]) {
                0 -> style = TerminalStyle()
                1 -> style = style.copy(bold = true)
                2 -> style = style.copy(dim = true)
                7 -> style = style.copy(inverse = true)
                22 -> style = style.copy(bold = false, dim = false)
                27 -> style = style.copy(inverse = false)
                in 30..37 -> style = style.copy(foreground = code - 30)
                in 40..47 -> style = style.copy(background = code - 40)
                39 -> style = style.copy(foreground = -1)
                49 -> style = style.copy(background = -1)
                90 -> style = style.copy(foreground = 8)
                38, 48 -> {
                    // `38;5;n` et `38;2;r;g;b` : on garde le premier indice, sans le décoder.
                    val target = if (code == 38) "foreground" else "background"
                    val kind = numbers.getOrNull(index + 1)
                    val value = when (kind) {
                        5 -> numbers.getOrNull(index + 2)
                        2 -> numbers.getOrNull(index + 2)
                        else -> null
                    }
                    if (value != null) {
                        style = if (target == "foreground") {
                            style.copy(foreground = 16 + value)
                        } else {
                            style.copy(background = 16 + value)
                        }
                    }
                    index += if (kind == 5) 2 else 4
                }
            }
            index++
        }
    }
}

/** Les couleurs ANSI courantes, dans l'ordre des codes 0 à 15. */
val AnsiColours = listOf(
    0xFF1B1B22, 0xFFE06C75, 0xFF98C379, 0xFFE5C07B,
    0xFF61AFEF, 0xFFC678DD, 0xFF56B6C2, 0xFFABB2BF,
    0xFF5C6370, 0xFFFF7B86, 0xFFA9D48A, 0xFFF0CE7F,
    0xFF7CC0F5, 0xFFD79BE8, 0xFF6FD3DE, 0xFFFFFFFF,
)
