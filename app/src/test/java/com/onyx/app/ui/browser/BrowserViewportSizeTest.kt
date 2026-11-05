package com.onyx.app.ui.browser

import com.onyx.app.data.net.BrowserScreenApi
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **La page prend la forme de l'écran, pas d'un gabarit.**
 *
 * Le gabarit fixe (390×844) rendait la page minuscule au milieu d'une grande dalle : sur un
 * pliable déplié — Honor Magic V3 — elle occupait une bande étroite cernée de noir, illisible et
 * intouchable. Et plier ou déplier change la largeur sans changer de page.
 */
class BrowserViewportSizeTest {

    @Test
    fun `la taille de l ecran part avec la prise en main`() {
        val taille = BrowserScreenApi.screenSize(1112, 1666, 2.75f)
        assertNotNull("une taille plausible est transmise", taille)
        assertEquals(1112, taille!!["width"]!!.jsonPrimitive.int)
        assertEquals(1666, taille["height"]!!.jsonPrimitive.int)
        val corps = BrowserScreenApi.leaseBody("acquire", size = taille)
        assertEquals("acquire", corps["action"]!!.jsonPrimitive.content)
        assertEquals(1112, corps["width"]!!.jsonPrimitive.int)
    }

    @Test
    fun `une taille absurde ne part pas`() {
        assertNull("pas de largeur", BrowserScreenApi.screenSize(null, 1666, 2.75f))
        assertNull("largeur de dix pixels", BrowserScreenApi.screenSize(10, 1666, 2.75f))
        assertNull("hauteur de dix pixels", BrowserScreenApi.screenSize(1112, 10, 2.75f))
    }

    @Test
    fun `l ecran mesure sa zone et la redit quand elle change`() {
        val ecran = File("src/main/java/com/onyx/app/ui/browser/BrowserScreen.kt").readText()
        assertTrue(
            "la zone de la page est mesurée",
            ecran.contains("onSizeChanged { taille -> viewModel.screenSized("),
        )
        assertTrue(
            "la prise en main annonce la forme mesurée",
            ecran.contains("BrowserScreenApi.acquire(server, forme?.first, forme?.second, forme?.third)"),
        )
        assertTrue(
            "un changement de forme est redit au serveur",
            ecran.contains("BrowserScreenApi.viewport(server, largeur, hauteur, density)"),
        )
    }

    @Test
    fun `l ecran reserve la barre d etat`() {
        // **Le titre se dessinait sous la barre d'état.** « Bouygues », l'heure et les icônes
        // passaient par-dessus le titre et la pastille « VOUS CONDUISEZ ». Tous les écrans pleine
        // page réservent les barres ; celui-ci ne le faisait pas.
        val ecran = File("src/main/java/com/onyx/app/ui/browser/BrowserScreen.kt").readText()
        assertTrue("barre d'état réservée", ecran.contains(".statusBarsPadding()"))
        assertTrue("barre de navigation réservée", ecran.contains(".navigationBarsPadding()"))
    }
}
