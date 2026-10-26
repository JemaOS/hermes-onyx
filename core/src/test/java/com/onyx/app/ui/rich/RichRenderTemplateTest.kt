package com.onyx.app.ui.rich

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le modèle de rendu des blocs riches, tel qu'il est embarqué dans les assets.
 *
 * Deux choses s'y vérifient, parce qu'elles se cassent sans bruit: tout marqueur `__…__` a un
 * remplacement côté Kotlin (un marqueur oublié s'afficherait tel quel dans le fil), et le dessin
 * plus large que le bloc se fait glisser au lieu d'être coupé.
 */
class RichRenderTemplateTest {

    private val template = File("src/main/assets/rich/render.html").readText()
    private val renderer = File("src/main/java/com/onyx/app/ui/rich/RichWebView.kt").readText()

    @Test
    fun `chaque marqueur du modele est remplace par Kotlin`() {
        val markers = Regex("__[A-Z_]+__").findAll(template).map { it.value }.toSet()
        assertTrue("le modèle porte des marqueurs", markers.isNotEmpty())
        markers.forEach { marker ->
            assertTrue("$marker est remplacé", renderer.contains("\"$marker\""))
        }
    }

    @Test
    fun `un dessin large est ramene a la largeur du bloc`() {
        assertTrue("le corps defile en largeur", template.contains("overflow-x: auto"))
        assertTrue("une mise a l'echelle existe", template.contains("function fitDiagram()"))
        assertTrue("elle retire la largeur imposee par mermaid", template.contains("svg.removeAttribute('width')"))
        assertTrue("elle laisse le viewBox tenir l'echelle", template.contains("viewBox"))
        assertTrue("elle sert le rendu du fil", template.contains("out.innerHTML = svg;\n                fitDiagram();"))
    }

    @Test
    fun `une formule trop large est mise a l echelle`() {
        assertTrue("la mise à l'échelle existe", template.contains("function fitMath()"))
        assertTrue("elle mesure la largeur de KaTeX", template.contains("out.querySelector('.katex .base')"))
        assertTrue("elle a une borne basse lisible", template.contains("Math.max(0.4"))
        assertTrue("elle sert le bloc du fil", template.contains("if (!zoomable) fitMath();"))
        assertFalse("aucune ressource distante dans le modèle", template.contains("http://") || template.contains("https://"))
    }

    @Test
    fun `les deux bibliotheques sont bien dans les assets`() {
        assertTrue(File("src/main/assets/rich/mermaid.min.js").length() > 1_000_000)
        assertTrue(File("src/main/assets/rich/katex.min.js").length() > 100_000)
        assertTrue(File("src/main/assets/rich/katex.min.css").length() > 10_000)
        assertTrue("les polices de KaTeX sont embarquées", File("src/main/assets/rich/fonts").listFiles().size >= 20)
    }
}
