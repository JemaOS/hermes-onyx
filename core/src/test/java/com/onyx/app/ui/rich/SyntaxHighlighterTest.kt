package com.onyx.app.ui.rich

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyntaxHighlighterTest {
    @Test
    fun `swift colore mots cles chaines commentaires et types`() {
        val tokens = SyntaxHighlighter.tokenize("let x = \"hi\" // note\nstruct Foo {}", "swift")

        assertTrue(tokens.contains(CodeToken("let", TokenKind.KEYWORD)))
        assertTrue(tokens.contains(CodeToken("\"hi\"", TokenKind.STRING)))
        assertTrue(tokens.contains(CodeToken("// note", TokenKind.COMMENT)))
        assertTrue(tokens.contains(CodeToken("Foo", TokenKind.TYPE)))
    }

    @Test
    fun `python colore les chaines triples sur plusieurs lignes`() {
        val code = "s = \"\"\"a\nb\"\"\"\nprint(3)"
        val tokens = SyntaxHighlighter.tokenize(code, "python")

        assertTrue(tokens.contains(CodeToken("\"\"\"a\nb\"\"\"", TokenKind.STRING)))
        assertTrue(tokens.contains(CodeToken("3", TokenKind.NUMBER)))
        assertEquals(code, tokens.joinToString("") { it.text })
    }

    @Test
    fun `json distingue les cles des valeurs`() {
        val tokens = SyntaxHighlighter.tokenize("{\"name\": \"x\", \"n\": 2, \"ok\": true}", "json")

        assertTrue(tokens.contains(CodeToken("\"name\"", TokenKind.KEY)))
        assertTrue(tokens.contains(CodeToken("\"x\"", TokenKind.STRING)))
        assertTrue(tokens.contains(CodeToken("true", TokenKind.KEYWORD)))
    }

    @Test
    fun `commentaire shell ne commence pas au milieu dun mot`() {
        val tokens = SyntaxHighlighter.tokenize("echo a#b # real", "bash")

        assertFalse(tokens.contains(CodeToken("#b # real", TokenKind.COMMENT)))
        assertTrue(tokens.contains(CodeToken("# real", TokenKind.COMMENT)))
        assertTrue(tokens.contains(CodeToken("echo", TokenKind.KEYWORD)))
    }

    @Test
    fun `rust conserve le texte et colore sa syntaxe`() {
        val code = "fn main() { let s = \"x\"; /* c */ 42 }"
        val tokens = SyntaxHighlighter.tokenize(code, "rust")

        assertEquals(code, tokens.joinToString("") { it.text })
        assertTrue(tokens.contains(CodeToken("fn", TokenKind.KEYWORD)))
        assertTrue(tokens.contains(CodeToken("/* c */", TokenKind.COMMENT)))
    }

    @Test
    fun `html colore balises attributs et chaines`() {
        val tokens = SyntaxHighlighter.tokenize("<div class=\"a\">hi</div>", "html")

        assertTrue(tokens.contains(CodeToken("<div", TokenKind.KEYWORD)))
        assertTrue(tokens.contains(CodeToken("class", TokenKind.ATTRIBUTE)))
        assertTrue(tokens.contains(CodeToken("\"a\"", TokenKind.STRING)))
    }
}
