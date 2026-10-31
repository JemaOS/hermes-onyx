package com.onyx.app.ui.rich

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les deux garanties du widget HTML, telles qu'elles sont **branchées** dans le rendu.
 *
 * La chaîne compte autant que la fonction : un assainissement écrit mais jamais appelé, ou un
 * réseau fermé dans un `settings` qu'on remplace plus loin, ne garantirait rien. Ces contrôles
 * lisent donc le rendu lui-même.
 */
class AgentPageGuardTest {

    private val block = File("src/main/java/com/onyx/app/ui/rich/AgentPageBlock.kt").readText()
    private val view = File("src/main/java/com/onyx/app/ui/rich/RichWebView.kt").readText()

    @Test
    fun `la page est assainie avant d entrer dans la vue`() {
        assertTrue("l'assainissement est appelé", view.contains("AgentHtml.sanitize(source)"))
        assertTrue("c'est le texte assaini qui est chargé", view.contains("framed(safe, prelude)"))
        assertFalse("la source brute n'est plus chargée telle quelle", view.contains("framed(source, prelude)"))
    }

    @Test
    fun `rien ne sort du rendu`() {
        assertTrue("le réseau est coupé par la vue", view.contains("settings.blockNetworkLoads = true"))
        assertTrue("la politique coupe tout par défaut", view.contains("default-src 'none'"))
        assertTrue("elle est posée dans le document", view.contains("PAGE_CSP +"))
        // La politique elle-même, extraite du fichier : elle n'ouvre aucune origine distante.
        val csp = Regex("""content=\\?"([^"\\]*default-src[^"\\]*)""").find(view)?.groupValues?.get(1).orEmpty()
        assertTrue("la politique existe", csp.contains("default-src 'none'"))
        assertFalse("aucune origine distante dans la politique", csp.contains("http"))
        assertTrue("les images en ligne restent permises", csp.contains("img-src data:"))
    }

    @Test
    fun `le pont et la mesure sont bien des scripts`() {
        // Le défaut d'avant : ces deux morceaux sont du JavaScript **nu**, et concaténés tels quels
        // ils s'affichaient en clair dans la page au lieu de tourner. Les balises sont désormais
        // posées à l'endroit où ils entrent dans le document.
        assertTrue("le pont est enveloppé", view.contains("\"<script>\" + PAGE_BRIDGE + \"</script>\""))
        assertTrue("la mesure aussi", view.contains("\"<script>\" + PAGE_MEASURE + \"</script>\""))
    }

    @Test
    fun `le bloc est pliable`() {
        assertTrue("le pli existe", block.contains("if (open) {"))
        assertTrue("il part déplié d'un état", block.contains("var open by remember(html)"))
        assertTrue("la barre le commande", block.contains("onToggle = { open = !open }"))
        assertTrue("le titre aussi", block.contains("Modifier.weight(1f).onyxClickable(onClick = onToggle)"))
        assertTrue("le chevron dit l'état", block.contains("if (open) 90f else 0f"))
        assertTrue("il a un libellé d'accessibilité", block.contains("\"Collapse page\"") && block.contains("\"Expand page\""))
    }

    @Test
    fun `le pli n anime pas la disparition de la vue`() {
        // Le crash constaté sur l'appareil : `SIGSEGV` dans `libhwui` (`RenderNodeDrawable::forceDraw`
        // récursif) quand le repli enlevait la `WebView` au milieu d'une transition de mise en page.
        // Le pli est donc immédiat — et rien ne doit réintroduire un `AnimatedVisibility` autour du
        // rendu de la page.
        assertFalse("aucune transition autour du rendu", block.contains("AnimatedVisibility"))
        assertTrue("la raison est écrite", block.contains("SIGSEGV"))
        assertTrue("la page quitte la composition", block.contains("if (open) {") && block.contains("RichWebView("))
    }

    @Test
    fun `la largeur d une page ne se replie pas sur elle-meme`() {
        val natural = view.substringAfter("val naturalIsUsable").substringBefore("val blockWidth")
        assertTrue("la mesure est bornée en bas", view.contains("naturalWidth >= maxWidth * 0.6f"))
        assertTrue("elle sert au choix de largeur", view.contains("isPage && naturalIsUsable -> naturalWidth"))
        assertTrue(natural.isNotEmpty())
    }

    @Test
    fun `les replis et les commandes de la page sont intacts`() {
        assertTrue("le repli sur le code source reste", block.contains("PageSource(html"))
        assertTrue("l'échec de chargement reste dit", block.contains("PageLoadFailure"))
        assertTrue("le zoom reste", block.contains("PageZoomFactors"))
        assertTrue("le partage reste", block.contains("\"Share page\""))
        assertTrue("la copie du source reste", block.contains("\"Copy source\""))
        assertTrue("la hauteur reste bornée", view.contains("PageMaxHeight"))
        assertTrue("elle défile au-delà", view.contains("pageScrolls"))
    }
}
