package com.onyx.app.ui.design

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Le placeholder des états vides, et l'intégrité `Symbols` → `drawable`. */
class EmptyStateLayoutTest {
    private fun file(path: String): File =
        listOf(File(path), File("core/$path")).firstOrNull { it.exists() }
            ?: error("introuvable : $path")

    @Test
    fun `le placeholder reprend l encombrement de ContentUnavailableView`() {
        val form = file("src/main/java/com/onyx/app/ui/design/OnyxForm.kt").readText()
        assertTrue(form.contains("fun OnyxEmptyState("))
        assertTrue(form.contains("size = 38.dp"))
        assertTrue(form.contains("verticalArrangement = Arrangement.spacedBy(12.dp)"))
        assertTrue(form.contains("fontWeight = FontWeight.SemiBold"))
        assertTrue(form.contains("theme.footnote.copy(color = theme.secondaryText, textAlign = TextAlign.Center)"))
    }

    @Test
    fun `chaque symbole declare un drawable present`() {
        val symbols = file("src/main/java/com/onyx/app/ui/design/Symbols.kt").readText()
        val drawables = file("src/main/res/drawable")
        val declared = Regex("val (\\w+) = R\\.drawable\\.(\\w+)").findAll(symbols).toList()
        assertTrue("aucun symbole lu", declared.size > 90)
        val missing = declared.filter { match ->
            !File(drawables, "${match.groupValues[2]}.xml").exists()
        }
        assertTrue(
            "drawables manquants : ${missing.map { it.groupValues[2] }}",
            missing.isEmpty(),
        )
    }
}
