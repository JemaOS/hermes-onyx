package com.onyx.app.localization

import java.io.File
import java.util.Locale
import org.junit.Test

/**
 * Balayage **de diagnostic** : liste les littéraux d'interface anglaise que le dictionnaire
 * français ne traduit pas. Écrit `build/missing-french.txt`, ne fait échouer aucun build.
 */
class LocalizationSweepTest {

    private val roots = listOf(
        "../app/src/main/java",
        "../core/src/main/java",
        "../wear/src/main/java",
        "../auto/src/main/java",
    )

    private val literal = Regex("\"((?:[^\"\\\\\\n]|\\\\.){3,400})\"")

    // Un littéral qui ressemble à de la prose anglaise d'interface.
    private val looksLikeLabel = Regex("^[A-Z][A-Za-z0-9 ,.'’‘:;!?()\\-/&%+…·\"«»]{2,}$")

    private val notALabel = listOf(
        "com.", "org.", "android.", "java.", "kotlin.", "http", "https", "www.",
        "content://", "file://", "image/", "text/", "audio/", "video/", "application/",
        "yyyy", "HH:mm", "MM", "dd", "EEEE", "Debug", "Release", "Bearer", "POST",
        "GET ", "PUT ", "DELETE", "application/json",
    )

    @Test
    fun sweep() {
        val out = mutableListOf<String>()
        val seen = HashSet<String>()
        roots.forEach { root ->
            val dir = File(root)
            if (!dir.isDirectory) return@forEach
            dir.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .sortedBy { it.path }
                .forEach { file ->
                    val text = file.readText()
                    literal.findAll(text).forEachIndexed { _, match ->
                        val value = match.groupValues[1]
                            .replace("\\\"", "\"")
                            .replace("\\n", "\n")
                            .replace("\\\\", "\\")
                        if (value.contains('\n')) return@forEachIndexed
                        if (!looksLikeLabel.matches(value)) return@forEachIndexed
                        if (!value.contains(' ')) return@forEachIndexed
                        if (notALabel.any { value.contains(it) }) return@forEachIndexed
                        if (value.any { it.isDigit() } && value.split(' ').size <= 2) return@forEachIndexed
                        if (OnyxLocalization.translate(value, Locale.FRANCE) != value) return@forEachIndexed
                        val key = "$value"
                        if (seen.add(key)) {
                            val line = text.substring(0, match.range.first).count { it == '\n' } + 1
                            out += "${file.path}:$line\t$value"
                        }
                    }
                }
        }
        val target = File("build/missing-french.txt")
        target.parentFile?.mkdirs()
        target.writeText(out.joinToString("\n"))
        println("SWEEP: ${out.size} littéraux non traduits -> ${target.absolutePath}")
        out.forEach { println("MISS\t$it") }
    }
}
