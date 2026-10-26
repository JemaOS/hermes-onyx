package com.onyx.app.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * C1 de l'audit des routes: `navTo` appelé **pendant la composition** quand l'état d'une route
 * est vide.
 *
 * Reproduit à l'appareil (`qa/20261026-media-capture/c1.py`, `results-c1.jsonl`): la feuille
 * « Sélectionner du texte » ouverte, le processus tué (`am kill`), l'activité recréée avec son état
 * sauvegardé → la feuille disparaît, et **trois appuis sur retour ne font rien** (l'app reste au
 * premier plan, l'écran ne change pas), là où le même écran atteint sans restauration se ferme au
 * premier retour.
 *
 * Ce fichier tient les deux moitiés de la règle: le repli se pose **hors de la composition**, et
 * la route vide **n'entre pas dans la pile**.
 */
class EmptyStateReturnTest {

    private val app = File("src/main/java/com/onyx/app/ui/OnyxApp.kt").readText()

    private fun branch(name: String): String =
        app.substringAfter("Route.$name ->").substringBefore("\n            Route.")

    /** Les routes dont l'état peut être vide: le repli, et la destination qu'il prend. */
    private val emptyStateBranches = mapOf(
        "SUBAGENT" to "CHAT",
        "PHOTO" to "CHAT",
        "PDF" to "CHAT",
        "VIDEO" to "CHAT",
        "SELECT_TEXT" to "CHAT",
        "PATCH" to "CHAT",
        "SESSION_USAGE" to "CONVERSATIONS",
        "PROJECT_SESSIONS" to "CONVERSATIONS",
    )

    @Test
    fun `le repli d etat vide se pose hors de la composition`() {
        emptyStateBranches.forEach { (route, target) ->
            val body = branch(route)
            assertTrue(
                "Route.$route : le repli est dans un LaunchedEffect",
                body.contains("LaunchedEffect(Unit) { navFromEmpty(Route.$target.name) }"),
            )
            assertFalse(
                "Route.$route : plus aucun navTo de repli écrit pendant la composition",
                body.contains("\n                    navTo(Route.$target.name)") ||
                    body.contains("\n                navTo(Route.$target.name)"),
            )
        }
    }

    @Test
    fun `le repli de l editeur de contexte se pose aussi hors de la composition`() {
        assertTrue(
            branch("CONTEXT_EDITOR").contains("LaunchedEffect(Unit) { navFromEmpty(Route.SETTINGS.name) }"),
        )
    }

    @Test
    fun `l etat vide est atteignable par la restauration d etat`() {
        // La route survit à une recréation de processus…
        assertTrue("la route est rememberSaveable", app.contains("var route by rememberSaveable { mutableStateOf(Route.CHAT.name) }"))
        // …alors que sa charge utile, non.
        listOf(
            "var subagentAgent by remember { mutableStateOf",
            "var photoSet by remember { mutableStateOf",
            "var pdfSource by remember { mutableStateOf",
            "var patchText by remember { mutableStateOf",
            "var selectedText by remember { mutableStateOf",
            "var contextEditor by remember { mutableStateOf",
        ).forEach { declaration ->
            assertTrue("« $declaration » : la charge utile ne survit pas", app.contains(declaration))
        }
        assertFalse(
            "la charge utile n'est pas sauvegardée",
            app.contains("var pdfSource by rememberSaveable") || app.contains("var photoSet by rememberSaveable"),
        )
    }

    @Test
    fun `l ancien repli avalait le retour`() {
        // Le défaut, tel qu'il se produisait: c'est ce que la règle nouvelle interdit.
        var route = "SELECT_TEXT"
        val backStack = mutableListOf<String>()
        // Le repli d'avant, écrit dans la composition: `navTo(CHAT)`, qui **empile** la route vide.
        route = navigateWithBackStack(route, "CHAT", backStack)
        assertEquals("CHAT", route)
        assertEquals(listOf("SELECT_TEXT"), backStack)
        // Le retour dépile et retombe dessus…
        route = popRoute(backStack)!!
        assertEquals("SELECT_TEXT", route)
        // …et l'état vide rebondit: même écran, même pile, geste avalé.
        route = navigateWithBackStack(route, "CHAT", backStack)
        assertEquals("CHAT", route)
        assertEquals(listOf("SELECT_TEXT"), backStack)
    }

    @Test
    fun `la nouvelle regle laisse le retour aboutir`() {
        // Le même chemin, avec le repli de maintenant: la route vide n'entre pas dans la pile.
        var route = "SELECT_TEXT"
        val backStack = mutableListOf<String>()
        route = navigateLeavingEmpty(route, "CHAT", backStack)
        assertEquals("CHAT", route)
        assertTrue("la route vide n'est pas une destination", backStack.isEmpty())
        // La pile est vide sur le fil: le retour rend la main au système, l'app se ferme.
        assertFalse("le retour n'est plus avalé", backStack.isNotEmpty() || route != "CHAT")
    }

    @Test
    fun `la route vide ne laisse pas non plus sa trace derriere elle`() {
        // Une pile qui contenait déjà la route vide (ancien passage) est nettoyée.
        val backStack = mutableListOf("CHAT", "SELECT_TEXT")
        val route = navigateLeavingEmpty("SELECT_TEXT", "CHAT", backStack)
        assertEquals("CHAT", route)
        assertFalse("aucune trace de l'écran vide", backStack.contains("SELECT_TEXT"))
        assertFalse("ni de la destination", backStack.contains("CHAT"))
    }

    @Test
    fun `un onglet de section reste une racine`() {
        val backStack = mutableListOf("CHAT", "SELECT_TEXT")
        val route = navigateLeavingEmpty("SELECT_TEXT", "CONVERSATIONS", backStack)
        assertEquals("CONVERSATIONS", route)
        assertTrue("un onglet de section vide la pile", backStack.isEmpty())
    }
}
