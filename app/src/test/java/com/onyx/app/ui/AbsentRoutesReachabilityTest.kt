package com.onyx.app.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Aucun écran orphelin: chacune des destinations portées est déclarée dans l'énum `Route`,
 * dessinée par la branche `when` du routeur, **et** ouverte par une entrée réelle de l'interface —
 * un réglage, une tuile de la grille, une ligne de menu, une rangée de projet.
 */
class AbsentRoutesReachabilityTest {

    private val app = File("src/main/java/com/onyx/app/ui/OnyxApp.kt").readText()
    private val settings = File("src/main/java/com/onyx/app/ui/settings/SettingsScreen.kt").readText()
    private val bots = File("src/main/java/com/onyx/app/ui/bots/BotsScreen.kt").readText()
    private val conversations = File("src/main/java/com/onyx/app/ui/conversations/ConversationsScreen.kt").readText()

    /** La branche complète d'une route dans le `when (presentedRoute)`. */
    private fun branch(name: String): String =
        app.substringAfter("Route.$name ->").substringBefore("\n            Route.")

    @Test
    fun `les quatre routes sont declarees`() {
        val routes = app.substringAfter("private enum class Route {").substringBefore("\n}")
        listOf("CONNECTION_DETAILS", "HIDDEN_BOTS", "SESSION_USAGE", "PROJECT_SESSIONS").forEach { name ->
            assertTrue("Route.$name est déclarée", routes.contains(name))
        }
    }

    @Test
    fun `chaque route est dessinee par le routeur`() {
        assertTrue(branch("CONNECTION_DETAILS").contains("ConnectionDetailsScreen("))
        assertTrue(branch("HIDDEN_BOTS").contains("HiddenBotsScreen("))
        assertTrue(branch("SESSION_USAGE").contains("SessionUsageScreen("))
        assertTrue(branch("PROJECT_SESSIONS").contains("ProjectSessionsScreen("))
    }

    @Test
    fun `chaque route a un chemin de retour`() {
        assertTrue(branch("CONNECTION_DETAILS").contains("navTo(Route.SETTINGS.name)"))
        assertTrue(branch("HIDDEN_BOTS").contains("navTo(Route.BOTS.name)"))
        assertTrue(branch("SESSION_USAGE").contains("navTo(Route.CONVERSATIONS.name)"))
        assertTrue(branch("PROJECT_SESSIONS").contains("navTo(Route.CONVERSATIONS.name)"))
    }

    @Test
    fun `Connection details s ouvre depuis les Reglages`() {
        assertTrue(settings.contains("label = \"Connection details\""))
        assertTrue(app.contains("onOpenConnectionDetails = { navTo(Route.CONNECTION_DETAILS.name) }"))
    }

    @Test
    fun `Hidden Bots s ouvre depuis la grille des bots`() {
        assertTrue(bots.contains("title = \"Hidden\""))
        assertTrue(app.contains("hiddenCount = botsState.hidden.size"))
        assertTrue(app.contains("onOpenHiddenBots = { navTo(Route.HIDDEN_BOTS.name) }"))
    }

    @Test
    fun `Usage et cout s ouvre depuis le menu d une conversation`() {
        assertTrue(conversations.contains("\"Usage & cost\""))
        assertTrue(app.contains("onOpenUsage = { conversation ->"))
        assertTrue(app.contains("navTo(Route.SESSION_USAGE.name)"))
    }

    @Test
    fun `le menu d une rangee s ouvre vraiment, par un appui long`() {
        // Sans ce geste, le menu — et « Usage & cost » avec lui — serait un écran orphelin.
        assertTrue(conversations.contains(".onyxTappable(onLongPress = onMenu) { onClick(bounds) }"))
        val interactions = File("../core/src/main/java/com/onyx/app/ui/design/Interactions.kt").readText()
        assertTrue(interactions.contains("onLongClick = onLongPress"))
        assertTrue(interactions.contains("combinedClickable("))
    }

    @Test
    fun `les sessions d un projet s ouvrent depuis la section Projects`() {
        assertTrue(conversations.contains("private fun ServerProjectsSection("))
        assertTrue(app.contains("onOpenProject = { project ->"))
        assertTrue(app.contains("navTo(Route.PROJECT_SESSIONS.name)"))
    }

    @Test
    fun `New Group Chat ouvre la salle avec les bots choisis`() {
        assertTrue(app.contains("onNewGroupChat = { picked, name ->"))
        assertTrue(app.contains("groupPicked = picked"))
        assertTrue(app.contains("groupViewModel.setSelected(picked)"))
        assertTrue(app.contains("title = groupTitle"))
    }

    @Test
    fun `Erase everything part des Reglages et efface avant de revenir`() {
        assertTrue(settings.contains("text = \"Erase everything…\""))
        assertTrue(app.contains("onEraseEverything = {"))
        assertTrue(app.contains("AppContainer.eraseEverything()"))
        assertTrue(app.contains("chatViewModel.newConversation()"))
    }

    @Test
    fun `le retour du systeme n est jamais avale`() {
        // Sur Réglages en deux panneaux, la pile vide faisait rendre faux à `navBack` et le geste
        // du bord ne faisait rien. Le retour remonte désormais toujours quelque part.
        val source = File("src/main/java/com/onyx/app/ui/OnyxApp.kt").readText()
        assertTrue("une condition de retour explicite", source.contains("val backHandled = if (layout.twoPanes)"))
        assertTrue(
            "le retour remonte au lieu de ne rien faire",
            source.contains("if (!navBack()) {") &&
                Regex("navTo\\(\\s*\\n?\\s*rootRouteBehind\\(").containsMatchIn(source),
        )
        assertTrue(
            "l'écran de repli vient de la règle partagée",
            source.contains("rootRouteBehind(\n                            currentRoute = route,"),
        )
    }
}
