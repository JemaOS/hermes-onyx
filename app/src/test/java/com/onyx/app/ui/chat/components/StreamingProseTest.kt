package com.onyx.app.ui.chat.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le rendu de la prose qui s'écrit — la règle, et ses garde-fous.**
 *
 * `streamingTokens` est la partie pure de [StreamingProse] : le texte tel qu'il s'affiche, et les
 * marques qui l'habillent. Ce que ces tests tiennent, c'est que le rendu **ne perd aucun caractère**
 * (hors marqueurs remplacés), qu'il ne devine rien (un marqueur non fermé reste littéral) et qu'il ne
 * mange pas les identifiants (`snake_case` n'est pas de l'italique).
 */
class StreamingProseTest {

    private fun text(markdown: String) = streamingTokens(markdown).first

    private fun marks(markdown: String, kind: MarkKind) =
        streamingTokens(markdown).second.filter { it.kind == kind }

    @Test
    fun `le gras s'applique et les etoiles disparaissent`() {
        val (rendered, marks) = streamingTokens("Un mot **gras** ici.")
        assertEquals("Un mot gras ici.", rendered)
        val bold = marks.filter { it.kind == MarkKind.BOLD }.single()
        assertEquals("gras", rendered.substring(bold.start, bold.end))
    }

    @Test
    fun `un marqueur non ferme reste litteral`() {
        // Ce que fait Markwon, et ce que le flux montre : le texte ne ment pas sur son contenu.
        assertEquals("Un **gras qui arrive", text("Un **gras qui arrive"))
        assertTrue(marks("Un **gras qui arrive", MarkKind.BOLD).isEmpty())
    }

    @Test
    fun `le code en ligne et le barre sont reconnus`() {
        val rendered = text("Le `cache` et ~~l'ancien~~.")
        assertEquals("Le cache et l'ancien.", rendered)
        val code = marks("Le `cache` et ~~l'ancien~~.", MarkKind.CODE).single()
        assertEquals("cache", text("Le `cache` et ~~l'ancien~~.").substring(code.start, code.end))
        assertEquals(1, marks("Le `cache` et ~~l'ancien~~.", MarkKind.STRIKE).size)
    }

    @Test
    fun `le code ne se re-analyse pas`() {
        // `**x**` dans des accents graves est du code littéral, pas du gras.
        assertEquals("val a = **x**", text("val a = `**x**`"))
        assertTrue(marks("val a = `**x**`", MarkKind.BOLD).isEmpty())
    }

    @Test
    fun `un identifiant n'est pas de l'italique`() {
        assertEquals("snake_case et two_words", text("snake_case et two_words"))
        assertTrue(marks("snake_case et two_words", MarkKind.ITALIC).isEmpty())
    }

    @Test
    fun `l'italique entoure de blancs est reconnu`() {
        val rendered = text("C'est *très* juste")
        assertEquals("C'est très juste", rendered)
        val italic = marks("C'est *très* juste", MarkKind.ITALIC).single()
        assertEquals("très", rendered.substring(italic.start, italic.end))
    }

    @Test
    fun `un lien garde son libelle et le perd de son adresse`() {
        val rendered = text("Voir [la page](https://exemple.fr/x) ici.")
        assertEquals("Voir la page ici.", rendered)
        val link = marks("Voir [la page](https://exemple.fr/x) ici.", MarkKind.LINK).single()
        assertEquals("la page", rendered.substring(link.start, link.end))
    }

    @Test
    fun `les puces et les cases deviennent des glyphes`() {
        val rendered = text("- un\n- [ ] deux\n- [x] trois\n1. quatre")
        assertEquals("• un\n☐ deux\n☑ trois\n1. quatre", rendered)
        assertEquals(1, marks("- [ ] deux", MarkKind.TASK_TODO).size)
        assertEquals(1, marks("- [x] trois", MarkKind.TASK_DONE).size)
    }

    @Test
    fun `un titre est une marque de ligne entiere`() {
        val source = "## Les trois postes"
        val (rendered, tokens) = streamingTokens(source)
        assertEquals("Les trois postes", rendered)
        val heading = tokens.single { it.kind == MarkKind.HEADING }
        assertEquals(2, heading.level)
        assertEquals(0, heading.start)
        assertEquals(rendered.length, heading.end)
    }

    @Test
    fun `le texte est rendu caractere pour caractere`() {
        // Le garde-fou général : hors marqueurs remplacés, rien ne se perd et rien ne s'ajoute.
        val source = "Une réponse **courte** avec `du code`, un [lien](https://x.y) et *de l'italique*."
        val rendered = text(source)
        assertEquals(
            "Une réponse courte avec du code, un lien et de l'italique.",
            rendered,
        )
    }
}