package com.onyx.app.ui.rich

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La garantie du widget HTML tient à ces quelques lignes : **aucun script de la page ne s'exécute**,
 * et aucune adresse active ne subsiste après [AgentHtml.sanitize].
 *
 * Le contrôle n'est pas une intention mais une mesure sur la chaîne : ce qui est dangereux est
 * détecté avant, détecté absent après — et ce qui est utile (titres, listes, tableaux, code, liens,
 * styles) est bien resté.
 */
class AgentHtmlTest {

    @Test
    fun `un script est retiré en entier`() {
        val page = "<h1>Titre</h1><script>alert(1)</script><p>Suite</p>"
        assertTrue("le brut est dangereux", AgentHtml.stillExecutable(page))
        val propre = AgentHtml.sanitize(page)
        assertFalse("le propre ne l'est plus", AgentHtml.stillExecutable(propre))
        assertFalse(propre.contains("alert(1)"))
        assertTrue(propre.contains("<h1>Titre</h1>"))
        assertTrue(propre.contains("<p>Suite</p>"))
    }

    @Test
    fun `un script non fermé est retiré jusqu'au bout`() {
        val page = "<p>Avant</p><script src=\"https://exemple.test/x.js\">"
        val propre = AgentHtml.sanitize(page)
        assertFalse(AgentHtml.stillExecutable(propre))
        assertTrue(propre.contains("<p>Avant</p>"))
        assertFalse(propre.contains("exemple.test"))
    }

    @Test
    fun `les gestionnaires d'événements sont retirés`() {
        val page = "<img src=\"data:image/png;base64,AAAA\" onerror=\"alert(1)\" onclick='alert(2)'>"
        assertTrue(AgentHtml.stillExecutable(page))
        val propre = AgentHtml.sanitize(page)
        assertFalse(AgentHtml.stillExecutable(propre))
        assertFalse(propre.contains("onerror"))
        assertFalse(propre.contains("onclick"))
        // L'image en ligne, elle, n'a pas bougé : elle ne sort pas du téléphone.
        assertTrue(propre.contains("data:image/png;base64,AAAA"))
    }

    @Test
    fun `les adresses actives sont neutralisées, les liens normaux restent`() {
        val page = "<a href=\"javascript:alert(1)\">X</a><a href=\"https://exemple.test/a\">Y</a>"
        assertTrue(AgentHtml.stillExecutable(page))
        val propre = AgentHtml.sanitize(page)
        assertFalse(AgentHtml.stillExecutable(propre))
        assertFalse(propre.contains("javascript:"))
        assertTrue("le lien ordinaire reste lisible", propre.contains("https://exemple.test/a"))
    }

    @Test
    fun `cadres objets et rafraîchissement partent aussi`() {
        val page = "<iframe src=\"https://exemple.test\"></iframe><object data=\"x.swf\"></object>" +
            "<embed src=\"y\"><meta http-equiv=\"refresh\" content=\"0;url=https://ailleurs.test\">" +
            "<base href=\"https://exemple.test/\">"
        val propre = AgentHtml.sanitize(page)
        assertFalse(AgentHtml.stillExecutable(propre))
        assertFalse(propre.contains("<iframe"))
        assertFalse(propre.contains("<object"))
        assertFalse(propre.contains("<embed"))
        assertFalse(propre.contains("<base"))
        assertFalse(propre.contains("refresh"))
    }

    @Test
    fun `le sous-ensemble utile survit`() {
        val page = """
            <title>Rapport</title>
            <style>.x{color:red}</style>
            <h2>Bilan</h2>
            <ul><li>un</li><li>deux</li></ul>
            <table><tr><td>a</td><td>b</td></tr></table>
            <pre><code>val x = 1</code></pre>
            <a href="https://exemple.test">lien</a>
        """.trimIndent()
        val propre = AgentHtml.sanitize(page)
        listOf("<title>Rapport</title>", "<style>", "Bilan", "<ul>", "<table>", "<pre>", "val x = 1", "lien").forEach {
            assertTrue("« $it » doit survivre", propre.contains(it))
        }
        assertFalse(AgentHtml.stillExecutable(propre))
    }

    @Test
    fun `un commentaire ne cache rien`() {
        val page = "<p>a</p><!-- <script>alert(1)</script> --><p>b</p>"
        val propre = AgentHtml.sanitize(page)
        assertFalse(propre.contains("<!--"))
        assertFalse(AgentHtml.stillExecutable(propre))
        assertTrue(propre.contains("<p>a</p>") && propre.contains("<p>b</p>"))
    }

    @Test
    fun `une page vide ou du texte nu ne lèvent pas`() {
        assertTrue(AgentHtml.sanitize("").isEmpty())
        assertFalse(AgentHtml.stillExecutable(AgentHtml.sanitize("du texte")))
    }

    @Test
    fun `une page démesurée est tronquée`() {
        val page = "<p>" + "a".repeat(AgentHtml.MAX_CHARS) + "</p>"
        val propre = AgentHtml.sanitize(page)
        assertTrue(propre.length <= AgentHtml.MAX_CHARS)
    }
}
