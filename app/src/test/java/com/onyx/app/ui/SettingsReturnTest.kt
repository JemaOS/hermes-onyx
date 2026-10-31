package com.onyx.app.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le **retour du bord** d'un écran atteint depuis les Réglages doit ramener **aux Réglages**, la
 * page d'où l'on vient — exactement ce que fait le bouton retour maison : les deux appellent
 * `navBack()`. C'est le défaut signalé — Réglages → Cron (ou Kanban) → geste du bord ne rendait pas
 * les Réglages, mais la liste des conversations.
 *
 * La cause tient dans `navigateWithBackStack` : la pile était vidée pour **tout** onglet de section
 * (`SectionTabRoutes`), et Cron / Kanban en sont. La route quittée (les Réglages) sortait donc de la
 * pile, `navBack()` rendait faux, et le repli partagé (`rootRouteBehind`) renvoyait la liste des
 * conversations — « remonte à un niveau supérieur » au lieu de la page d'où l'on vient.
 *
 * La règle ne vide la pile que **dans le rail** : d'un onglet de section à un autre. Atteint
 * **depuis l'extérieur** du rail (les Réglages, une tuile, un menu), un onglet de section est une
 * page **poussée** comme une autre ; son retour rend donc la page quittée. La liste des
 * conversations n'est plus une route du tout (elle vit dans le volet) : le repli est le fil.
 */
class SettingsReturnTest {

    private val appSource = File("src/main/java/com/onyx/app/ui/OnyxApp.kt").readText()

    /**
     * Chaque destination ouverte par une ligne ou un contrôle des Réglages (les `onOpen…` d'`OnyxApp`),
     * auditées ensemble : aucune ne doit se singulariser.
     */
    private val settingsChildren = listOf(
        "APPEARANCE", "MODELS", "VOICE_SETTINGS", "GATEWAY", "LOGS", "FILES", "CRON", "KANBAN",
        "PHONE_TOOLS", "BOTS", "SERVERS", "PROFILES", "TOOLS", "WHATS_NEW", "TIP_JAR",
        "LOCK", "CONNECT", "TERMINAL_SETTINGS", "ACKNOWLEDGEMENTS", "CONTEXT", "MEMORY",
        "NOTIFICATIONS", "CONNECTION_DETAILS",
    )

    /** Le parcours Réglages → enfant, qui laisse la pile prête pour un retour. */
    private fun openFromSettings(child: String): MutableList<String> {
        val backStack = mutableListOf<String>()
        // Le fil ouvre les Réglages ; les Réglages ouvrent l'enfant.
        assertEquals("les Réglages s'ouvrent sur le fil", "SETTINGS", navigateWithBackStack("CHAT", "SETTINGS", backStack))
        assertEquals(child, navigateWithBackStack("SETTINGS", child, backStack))
        return backStack
    }

    @Test
    fun `chaque ecran fils des Reglages revient aux Reglages par le retour`() {
        settingsChildren.forEach { child ->
            val backStack = openFromSettings(child)
            // Le geste du bord comme le bouton maison : `navBack()` rend les Réglages.
            assertEquals("$child devrait rendre les Réglages", "SETTINGS", popRoute(backStack))
        }
    }

    @Test
    fun `les onglets de section ouverts des Reglages ne retombent pas sur la liste`() {
        // Le défaut exact : Cron et Kanban sont les seuls onglets de section atteints des Réglages.
        listOf("CRON", "KANBAN").forEach { tab ->
            val backStack = openFromSettings(tab)
            // La page quittée est bien dans la pile (la feuille n'est pas refermée)…
            assertTrue("$tab : les Réglages restent dans la pile", "SETTINGS" in backStack)
            // …donc le repli partagé ne s'applique pas : c'est les Réglages qui reviennent, pas la liste.
            assertEquals("SETTINGS", popRoute(backStack))
            assertEquals("$tab : la pile ne porte que le fil et les Réglages", listOf("CHAT"), backStack)
        }
    }

    @Test
    fun `un onglet de section garde le rail quand on y vient par un onglet`() {
        // D'un onglet de section à un autre, la pile reste au fond (comportement conservé).
        val backStack = mutableListOf("CHAT", "TOOLS")
        assertEquals("KANBAN", navigateWithBackStack("CRON", "KANBAN", backStack))
        assertTrue("le rail remet la pile au fond", backStack.isEmpty())
        // Un onglet de section à pile vide retombe sur le fil : la liste est un panneau, pas une route.
        assertEquals("CHAT", rootRouteBehind("CRON", chatRoute = "CHAT"))
    }

    @Test
    fun `une conversation ouverte depuis une page revient a cette page`() {
        val backStack = mutableListOf<String>()
        assertEquals("CRON", navigateWithBackStack("TOOLS", "CRON", backStack))
        assertEquals("CHAT", navigateWithBackStack("CRON", "CHAT", backStack))
        assertEquals(listOf("TOOLS", "CRON"), backStack)
        assertEquals("CRON", popRoute(backStack))
    }

    @Test
    fun `un sous-ecran d une conversation revient a la conversation`() {
        val backStack = mutableListOf<String>()
        assertEquals("CHAT", navigateWithBackStack("CRON", "CHAT", backStack))
        // Un sous-écran du fil (les sous-agents), poussé depuis la conversation.
        assertEquals("SUBAGENTS", navigateWithBackStack("CHAT", "SUBAGENTS", backStack))
        assertEquals("CHAT", popRoute(backStack))
    }

    @Test
    fun `la racine d un onglet de section ne ferme pas l app`() {
        // Un onglet de section atteint directement (état restauré) a la pile vide : le repli est un
        // vrai écran — le fil —, donc le geste du bord ne rend pas la main au lanceur.
        listOf("CRON", "KANBAN").forEach { tab ->
            assertEquals(
                "$tab : le repli est le fil, pas la fermeture",
                "CHAT",
                rootRouteBehind(tab, chatRoute = "CHAT"),
            )
        }
        // La règle du retour vit dans **une seule fonction** ([routeBackHandled]) : elle garde le
        // retour vivant partout hors du fil, qui est la racine dans les deux formats. La liste des
        // conversations n'est plus une route (elle vit dans le volet), donc `twoPanes` ne change
        // plus la réponse — la règle le dit explicitement.
        val rule = File("src/main/java/com/onyx/app/ui/ResponsiveLayout.kt").readText()
        assertTrue(rule.contains("backStack.isNotEmpty() -> true"))
        assertTrue(rule.contains("twoPanes -> currentRoute != chatRoute"))
        assertTrue(rule.contains("else -> currentRoute != chatRoute"))
        assertTrue(appSource.contains("val backHandled = routeBackHandled("))
        assertTrue(appSource.contains("if (!navBack()) {"))
        assertEquals("CHAT", rootRouteBehind("CHAT", chatRoute = "CHAT"))
    }

    @Test
    fun `le bouton maison et le retour du bord passent par la meme fonction`() {
        // Une seule règle : le bouton des onglets de section dépile d'abord (`navBack()`), et le
        // geste du bord, dans la mise en page, fait exactement la même chose avant de se rabattre.
        assertTrue(appSource.contains("val inApp: () -> Unit = ({ if (!navBack()) navTo(Route.CHAT.name) })"))
        // La décision : dépiler le volet seulement s'il porte **cet** écran (et non « un écran
        // quelconque est ouvert là-dedans », qui était vrai même volet fermé).
        assertTrue(appSource.contains("if (sidebarState.destination == Route.CRON.name) paneBack else inApp"))
        assertTrue(appSource.contains("onDone = { if (!navBack()) navTo(Route.CHAT.name) }"))
        assertTrue(appSource.contains("BackHandler(enabled = backHandled) {"))
    }
}
